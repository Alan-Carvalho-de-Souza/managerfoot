# Calendário da temporada — análise e plano de ação

Levantamento do que existe hoje e o caminho para um calendário de 365 dias que
sirva de base para a simulação, as transferências e todo dado com data.

Tudo aqui foi medido no código, não estimado. O que não foi verificado está
marcado como tal.

---

## 1. O que já existe (e é mais do que parece)

### O jogo já é dirigido por um relógio

`ordemGlobal` é um inteiro por partida, monotônico dentro da temporada. O ponto
que mais importa e que não é óbvio: **o mês do save não é a fonte da verdade —
ele persegue o relógio.**

```kotlin
// ViewModels.kt, fechamento mensal
val ordemRef    = (proximaPartida ?: ultimoNaoAmistoso)?.ordemGlobal ?: 0
val mesDerived  = if (ordemRef > 0) ordemGlobalParaMes(ordemRef) else save.mesAtual
while (save.mesAtual <= mesDerived && iteracoesMes < 12) { … avancarMes() }
```

Ou seja: já há um único relógio, e ele é mais fino do que um calendário diário
precisa. A temporada ocupa 295 dias (8 fev – 30 nov) e o `ordemGlobal` usa a
faixa 1–620. **A estrutura para o calendário real já está lá.** O que falta é
ela deixar de ser derivada na exibição e passar a ser decidida no agendamento.

### O acoplamento, medido

| | Ocorrências | Concentração |
|---|---|---|
| `ordemGlobal` | 258 | `GameRepository` 71, `MotorCampeonato` 64 |
| — **escrita** (atribuição + tabelas) | ~46 | 35 atribuições + 16 `intArrayOf` |
| — **leitura semântica** (ordenar/comparar) | ~15 | 9 `ORDER BY`, 4 comparações, 2 `MAX` |
| Simulação por rodada | 32 | 26 em `GameRepository` |

A maior parte do acoplamento é **escrever** o calendário, não interpretá-lo.
Isso é o que torna a mudança viável: quase tudo que *lê* continua valendo se o
número seguir sendo monotônico.

### As tabelas de calendário que existem hoje

16 `intArrayOf` em `MotorCampeonato` mais ~12 constantes soltas:

| Competição | Casas de `ordemGlobal` |
|---|---|
| Ligas de 38 rodadas (BR A–D, Premier, Championship, La Liga, Serie A) | `rodada * 10` → 10…380 |
| Copa do Brasil | 13, 33, 53, 83, 133, 163, 207, 233, 278, 313, 357, 383 |
| Copa Argentina | 25, 45, 85, 115, 145, 175, 205, 215, 415, 425 |
| Copa da Inglaterra | 35, 95, 165, 255, 335 |
| Copa do Rei / Coppa Italia | 95, 115, 165, 185, 245, 265, 325, 345 |
| Champions | 27…287 (liga), 303/307 (play-off), 313…373 (mata-mata) |
| Europa League | as mesmas +1 |
| Libertadores | 15…39 (Pré), 45…195 (grupos), 265…385 (mata-mata) |
| Sul-Americana | 41 (1ª fase), 52…202 (grupos), 212…372 (mata-mata) |
| Argentina | Apertura `r*10`, Clausura `r*10 + 210` |
| Uruguai | Apertura `r*10`, Intermediário `+155`, Clausura `+250`, playoff 411/421, Competencia `+450` |
| Avulsos | Supercopa Rei 1 · Recopa 6/9 · Intercontinental 440 · Troféu ARG 620 · Amistosos ~5 / ~450 |

---

## 2. Os defeitos que a varredura encontrou

Estes são a lista de aceitação do trabalho: cada um precisa deixar de existir.

### 2.1 Quatro conversores de `ordemGlobal` → mês, com fórmulas diferentes

| Onde | Divisor | Conhece casos especiais? |
|---|---|---|
| `CalendarioDoJogo.ordemGlobalParaMesDia` | 370 (410 p/ Argentina) | sim |
| `ViewModels.ordemGlobalParaMes` | **370** | só Supercopa |
| `Repositories.ordemGlobalParaMesCarreira` | **380** | Supercopa + Troféu |

Consequências medidas:

- **`/370` vs `/380` discordam do mês em 5 das 38 rodadas** da Série A (OG 190,
  230, 270, 310, 350). O mês que fecha as finanças e o mês que atribui as
  partidas aos clubes no histórico de carreira divergem — e é perto de uma
  transferência que a atribuição erra o clube.
- **Para um clube argentino, o mês do relógio (`/370`) discorda do mês exibido
  (`/410`) em 21 das 58 rodadas.** `mesDerived` usa `/370` sem olhar o país, e é
  ele que dispara salários e patrocínio. Jogando na Argentina, o fechamento
  financeiro corre em descompasso com a data na tela por mais de um terço da
  temporada. *(Medido na fórmula; não reproduzido no app.)*

### 2.2 Colisões de data entre competições do mesmo clube

Por perfil de clube, convertendo as casas atuais pela curva de exibição:

| Perfil | Mesmo `ordemGlobal` | Mesma data |
|---|---|---|
| Brasileiro + Libertadores | 0 | **3** (Pré-Libertadores × Série A, em fev/mar) |
| Brasileiro + Sul-Americana | 0 | 0 |
| **Argentino + Libertadores** | **2** (casas 25 e 45: Copa Argentina × Libertadores) | **5** |
| Inglês + Champions | 0 | 0 |
| Espanhol + Champions | 0 | 0 |
| Italiano + Europa League | 0 | 0 |

A colisão argentina é a pior: **mesmo `ordemGlobal`**, ou seja, dois jogos no
mesmo instante do relógio para um clube que dispute as duas.

E mais três casos de **jogos em dias consecutivos** entre Copa do Brasil e Série
A (5/4 e 4/4, 15/5 e 14/5, 10/9 e 11/9).

### 2.3 Casas mágicas espalhadas

`ordemGlobal == 1` (Supercopa), `== 620` (Troféu), `>= 400` (amistoso de
dezembro), `> ARG_CLAUSURA_OG_OFFSET + 10` (detectar Clausura), `== 440`
(Intercontinental). São números que só significam algo porque alguém lembrou.

### 2.4 A tabela `temporadas` existe e nunca é populada

`TemporadaEntity(id, ano, encerrada)` está no schema, sem DAO e sem nenhum
insert. Já causou um bug real: o ranking mundial vinha vazio porque uma query
fazia `INNER JOIN temporadas`. **É a casa natural do calendário.**

### 2.5 `rodada` não é rodada

Já documentado em `regras_libertadores.md`: a rodada gravada é um slot dentro do
campeonato (Pré 1–6, grupos 11–16, mata-mata 20+). Qualquer coisa nova que leia
`rodada` como "a enésima rodada" vai errar.

---

## 3. O que `mes` e `ano` decidem hoje

Importa porque o calendário diário precisa continuar alimentando tudo isto:

| Regra | Granularidade | Onde |
|---|---|---|
| Fechamento mensal (salários, patrocínio, bilheteria) | mês | `fecharMes` |
| Janela de transferências (`mes <= 6` → 0/1) | semestre | `janelaTransferencia` |
| Propostas da IA ao time do jogador (meses 1,2,10,11,12) | mês | `GameRepository:5855` |
| Probabilidade de transferência IA↔IA (`mes <= 2` → 30%) | mês | `GameRepository:7398` |
| Retorno de empréstimo (`anoRetorno`, `mesRetorno`) | mês | `processarRetornosEmprestimo` |
| Amistosos de entressafra (dez / jan) | mês | `criarAmistoso` |
| Carimbo de transferência (`ano`, `ordemGlobal`) | **dia** | `carimboDeData` |

Nenhuma dessas regras é por dia. **O calendário diário entra por baixo delas sem
exigir que nenhuma mude** — o mês passa a sair do dia em vez da curva.

---

## 4. A proposta

### A ideia central: `ordemGlobal` passa a SER o dia da temporada

Em vez de uma escala abstrata de 1 a 620 convertida por curvas diferentes por
competição, o número vira o **dia absoluto da temporada (1–365)**.

O que isso resolve de uma vez:

- **a data deixa de ser derivada.** Os quatro conversores do §2.1 viram um só, e
  trivial — o mesmo para todo país, o que mata a divergência argentina;
- **tudo que ordena continua valendo.** `ORDER BY ordemGlobal`,
  `buscarProximaPartida`, as janelas de ranking (`ordemGlobal <= :atual`) e
  `MAX(ordemGlobal)` seguem corretos, porque o número segue monotônico;
- **`ordemGlobalAtual` vira literalmente "que dia é hoje"**, que é a peça que
  falta para qualquer coisa dirigida por data;
- **as casas mágicas desaparecem**: "Supercopa no dia 25" é legível.

O custo está concentrado nas ~46 escritas, que já estão em dois arquivos.

### Por que não é um problema de satisfação de restrições

Metade do calendário não existe no começo da temporada: os grupos da
Libertadores só são sorteados quando a Pré termina, o mata-mata quando os grupos
terminam. Um agendador global com restrições não teria o que agendar.

Mas as faixas de `ordemGlobal` já **reservam** espaço para o que ainda não foi
sorteado. Então o trabalho não é inventar um agendador: é **trocar números
abstratos por dias reais nas faixas que já existem**, e validar o resultado.

---

## 5. Plano de ação

### Estado atual

| Etapa | Estado | Onde |
|---|---|---|
| **0a** — ordenar por `(temporadaId, ordemGlobal)` | ✅ **feito** | `1e0af35` |
| **0b** — unificar os conversores de mês | ✅ **feito** | `b48e8d2`, e o resto abaixo |
| **1** — calendário como dado explícito | ✅ **feito** | `CalendarioDaTemporada` |
| **2** — `ordemGlobal` passa a ser o dia | ⬜ não começou | |
| **3** — restrições reais | 🟡 **validador pronto, conflitos abertos** | ver §5.3 |
| **4** — transferências e demais dados pelo dia | 🟡 **base pronta** | `b48e8d2`, `e1751f3` |
| **5** — simulação dirigida por data | ⬜ não começou (opcional) | |

**0b:** existe **uma única fórmula de data no projeto**, em `CalendarioDoJogo`.
As quatro cópias foram embora em dois passos — `CalendarioScreen` e
`DashboardScreen` em `b48e8d2`, e os dois divergentes depois.

O que mudou de comportamento ao unificar:

| Conversor removido | Era | Decidia |
|---|---|---|
| `ViewModels.ordemGlobalParaMes` | /370, cego ao país | `mesDerived` — quando salários e patrocínio são cobrados |
| `Repositories.ordemGlobalParaMesCarreira` | /380, cego ao país | a que clube cada partida pertence no histórico de carreira |

Os dois passaram a receber o **nome do campeonato**, que é o que seleciona a
curva. Três testes de regressão fixam o que divergia: as 5 rodadas da Série A em
que /370 e /380 discordavam, as rodadas argentinas em que a curva própria difere
da genérica, e o fato de a seleção depender de "Primera División Argentina"
aparecer no nome — se isso mudar, a curva volta a ser a genérica em silêncio.

**4, o que já foi:** a transferência carimba `ano` e `ordemGlobal` no momento em
que acontece, e o histórico já deriva a data daí. Falta só o `ordemGlobal` virar
o dia (etapa 2) para a data ficar exata sem conversão.



### Etapa 0 — o relógio que zera a cada temporada *(independente, barato, urgente)*

Duas coisas, nenhuma dependendo do resto do plano, e as duas consertam bug de hoje:

**0a. Ordenar por `(temporadaId, ordemGlobal)` onde a consulta cruza temporadas.**
`buscarUltimasPartidasDoTime` (moral) e `observeReceitasPartidas` (receitas)
ordenam por um número que zera todo ano, sem filtro de temporada — ver §7.3.
Duas linhas cada.

**0b. Unificar os conversores de mês.**

Um conversor só, o de `CalendarioDoJogo`, usado por `ViewModels` e por
`Repositories`. Elimina a divergência `/370` × `/380` × `/410` do §2.1.

- **Não depende de nada e pode ir sozinha.** Conserta um bug real hoje.
- **Saves:** nenhum impacto de dados. Muda o mês derivado em algumas rodadas,
  o que é a correção.
- **Teste:** o mês de uma partida é o mesmo por qualquer caminho, para toda casa
  de `ordemGlobal` de toda competição.

### Etapa 1 — o calendário como dado explícito ✅

`CalendarioDaTemporada` no domínio: os dias de uma liga por número de rodadas, a
ponte `ordemGlobal → dia` para as competições ainda não convertidas, e o
validador de conflitos. **Ainda não é usado pelos geradores** — isso é a etapa 2.

- `diasDeLiga(38)` reproduz exatamente as datas de hoje (há teste), e
  `diasDeLiga(46)` **conserta a Championship**, que colapsava rodadas em 31 dez.
- **Saves:** nada muda; é peça nova, não ligada.
- Persistir na tabela `temporadas` (hoje morta) fica para a etapa 2, quando o
  calendário passar a ser lido na geração.

### Etapa 2 — `ordemGlobal` passa a ser o dia ✅ *(feita: versionada por temporada)*

#### O que foi feito

**A versão do calendário mora no campeonato** (`campeonatos.calendarioVersao`,
migração 36 → 37 com `DEFAULT 1`). Uma temporada ou é inteira da `CURVA` — o
`ordemGlobal` é um slot de 1 a 620 e o dia sai de uma curva — ou é inteira da
`GRADE`, onde o `ordemGlobal` É o dia do ano. Nenhuma partida gravada foi
reescrita.

**Os geradores não mudaram.** Os ~109 lugares que montam calendário seguem
produzindo as casas de `MotorCampeonato`. A tradução de slot para dia acontece
num ponto só, `GameRepository.gravarPartida`/`gravarPartidas`, por onde passam
as 36 gravações de partida do projeto — todas no mesmo arquivo. `CalendarioDeSlots`
faz a conversão por índice: dentro de uma competição os slots são uma lista
ordenada e os dias são outra, do mesmo tamanho.

Isso foi de propósito. Trocar o valor na origem seria um refactor largo para um
ganho nenhum, porque a informação é a mesma; o que importa é que o valor
GRAVADO seja o dia, de onde saem a ordem cronológica e a data de uma
transferência.

**A versão viaja com a linha, e não tem valor padrão.** `ordemGlobalParaMesDia`
exige `versao`, e foi o compilador que listou os 8 pontos de leitura. Um default
faria um ponto esquecido continuar compilando e mostrar data errada em silêncio.
Onde a linha não sabe sozinha, a versão veio de onde ela mora:

| Leitor | De onde vem a versão |
|---|---|
| tela do Calendário | `CalendarioPartidaDto`, no mesmo JOIN do nome |
| histórico de transferências | subquery da temporada, ao lado de `nomeTemporada` |
| histórico de carreira | memo por campeonato e por temporada |
| chip de data do Dashboard | `StateFlow` do `DashboardViewModel` |
| fechamento de salários | o `CampeonatoEntity` que o ViewModel já buscava |
| `diaDaCasa` | fixo na `CURVA`: é o leitor do histórico |

**Uma armadilha que estava mapeada e se confirmou:** o `isClausura` argentino
decidia por `ordemGlobal > 220`. Numa temporada da grade qualquer jogo depois de
agosto passa de 220, e o torneio declararia Clausura no meio do Apertura,
registrando a divisão errada no Hall da Fama. Agora quem diz é o nome do
campeonato, e o ramo antigo ficou para as temporadas da `CURVA`.

**O que a grade pegou que eu não tinha previsto:** "Recopa Sul-Americana" contém
"Sul-Americana", então ela casava com o ramo da Sul-Americana, não achava os
seus slots e voltava em silêncio para a curva antiga. Foi o teste de
`reconhece` que acusou — é a mesma pegadinha de ordem de ramos que a
Intercontinental já tinha.

#### O que NÃO está coberto por teste

- **O `ALTER TABLE` de verdade.** Executá-lo pede SQLite real, e o projeto não
  tem teste instrumentado; um `androidTest` exigiria instalar no aparelho, o que
  apagaria o save do usuário. O que está testado é a corrente de migrações, que
  é o que destrói dado: com `fallbackToDestructiveMigration`, uma versão subida
  sem migração faz o Room apagar o banco em silêncio.
- **A fiação em si.** Os testes cobrem a tradução e as duas escalas, que são
  puras. Que cada tela leia a versão certa não foi verificado num save real.
- **Uma imperfeição de transição conhecida:** as duas consultas de janela de 12
  meses (`PartidaDao`, linhas 199–210) comparam `ordemGlobal` da temporada
  anterior com o da corrente. Na única temporada em que a anterior é `CURVA` e a
  corrente é `GRADE`, essa janela fica torta — um recorte de forma e estatística
  um pouco errado, por uma temporada, sem perda de dado.


> **Alternativa melhor do que migrar: versionar o calendário por temporada.**
>
> Verificado: **todas as competições de uma temporada são criadas no rollover**
> — ligas, copas, supercopas, Champions, Europa League, Libertadores,
> Sul-Americana, Recopa, Intercontinental e Troféu. As FASES de cada uma são
> geradas mais tarde, mas sempre dentro de um campeonato que já existe.
>
> Então o limite da temporada é uma fronteira limpa para criar competições, e
> dá para carimbar a versão do calendário no `campeonatos` (coluna nova, default
> 1). Cada gerador de fase já tem o campeonato em mãos (`buscarPorId`) e escolhe
> o calendário pela versão dele.
>
> O que isso elimina:
>
> - **a migração de `partidas` some inteira** — nada é reescrito, e some junto o
>   risco de converter ~6.300 linhas no startup;
> - **a mistura dentro da temporada fica impossível por construção** — uma
>   temporada v1 termina inteira em v1, inclusive as fases geradas depois;
> - **o `isClausura` e as casas mágicas continuam corretos** para as temporadas
>   v1, porque elas seguem na escala que entendem;
> - **não há exigência de o jogador atualizar num momento certo.**
>
> O custo: a curva antiga fica no código para sempre, como LEITOR de temporadas
> v1 no histórico. É um leitor, não um agendador — aceitável. E a exibição passa
> a escolher o leitor pela versão da temporada, o que é um ponto a mais para
> acertar (`observeCalendario` já reordena por data derivada em Kotlin).

#### Fim ou início de temporada: faz diferença?

| Momento em que o código novo chega ao save | Com versionamento | Sem versionamento (migração) |
|---|---|---|
| **Fim da temporada**, antes do rollover | a próxima temporada já nasce no calendário novo | **o único momento seguro**: não há nada por gerar na temporada velha |
| **Início**, logo depois do rollover | a temporada recém-criada é v1 e termina assim; o novo só entra daqui a um ano de jogo | a temporada inteira precisa ser migrada |
| **Meio** da temporada | igual ao início | pior caso: fases por gerar entram na escala nova dentro de uma temporada velha |

**Conclusão: o fim da temporada é melhor nos dois cenários, por razões
diferentes.** Sem versionamento é o único momento sem risco. Com versionamento
não há risco em momento nenhum — mas é o fim da temporada que faz o calendário
novo valer já na temporada seguinte, em vez de daqui a um ano de jogo.



Os geradores pedem o dia ao calendário em vez de ler `intArrayOf`. A conversão
de exibição vira identidade.

- **Saves:** exige migração. Converter o `ordemGlobal` pela derivação atual, que
  é função pura de `(ordemGlobal, nome do campeonato)` — ambos gravados. **Há
  precedente**: `migrarOrdemGlobalCopaBrasil` já reescreve `ordemGlobal` de
  saves antigos, chamada no startup (`ViewModels:730`).

**A migração NÃO pode ser preguiçosa, e não pode ser do save inteiro.**

Converter só na leitura (como foi feito com as transferências sem relógio) não
serve aqui: no meio de uma temporada, as partidas já geradas estariam na escala
velha e as geradas depois — os grupos da Libertadores, o mata-mata das copas —
na nova. Duas escalas dentro da MESMA temporada quebram todo `ORDER BY
ordemGlobal`, incluindo `buscarProximaPartida`. A conversão tem de ser ansiosa.

Mas o save inteiro é grande. Contando as competições do seed:

| | partidas |
|---|---|
| Ligas (BR A–D, Premier, Championship, La Liga, Serie A, ARG, URU) | ~5.500 |
| Copas nacionais | ~155 |
| Continentais | ~610 |
| **por temporada** | **~6.300** |
| após 10 temporadas | ~63.000 |

Ler 63 mil linhas com o nome do campeonato e reescrevê-las no `onCreate` do app
é lento o bastante para o usuário ver.

**A saída: migrar ansiosamente só a TEMPORADA CORRENTE (~6.300 linhas).** As
temporadas passadas só são lidas para histórico, e cada consulta de histórico já
ordena *dentro* da temporada (`ORDER BY temporadaId DESC, ordemGlobal DESC`) —
então escalas diferentes entre temporadas não se misturam. Converter as antigas
vira opcional: melhora as datas exibidas no histórico, e pode ser feito
preguiçosamente na leitura, onde a mistura de escalas é inofensiva.

- **Risco:** médio. É a etapa que exige mais cuidado e mais teste.
- **Teste:** a ordem relativa de todas as partidas da temporada corrente é
  preservada pela migração, e nenhuma partida da temporada corrente fica na
  escala velha.

### Etapa 3 — restrições reais ✅ *(feita: a grade semanal)*

A enumeração completa achou **mais conflitos do que o §2.2 tinha**, e três
classes novas que a análise por perfil não cobria:

| Defeito | Onde |
|---|---|
| **Todas as supercopas na casa 1** (25 jan) | um clube que ganhe a liga e a Champions joga a Supercopa nacional e a da UEFA no mesmo dia |
| **Championship colapsa 3 rodadas em 31 dez** | 46 rodadas na curva calibrada para 38 — **corrigido na etapa 1** |
| **Finais da Copa do Brasil e da Libertadores em 3 dez** | um clube pode estar nas duas |

E por perfil de clube, sobre o calendário de hoje:

| Perfil | Mesmo dia | Dias consecutivos |
|---|---|---|
| Brasileiro + Libertadores | 4 | 6 |
| Argentino + Libertadores | 5 | 6 |
| Inglês + Champions | 0 | 1 |
| Espanhol + Champions | 0 | 2 |

**E a correção é mais difícil do que "ajuste de tabela".** Eu tinha escrito que
não era um problema de satisfação de restrições; isso vale para a *reserva* de
faixas, não para o reparo dos conflitos. Exigir distinção e não-adjacência
globais é impossível: são ~200 jogos de calendário e cada um pediria 2 dias, o
que não cabe em 295. O reparo tem de ser **por perfil** — só competições que um
mesmo clube pode disputar juntas se excluem —, e aí vira um problema de
coloração com janelas estreitas.

#### O que foi feito

A enumeração do calendário real, agora completa (29 competições, **14 perfis**
de clube), achou **22 colisões de mesmo dia e 41 de dias seguidos**.

E mostrou que a causa não era o `ordemGlobal`: dentro de um perfil ele nunca
repete, por construção — os comentários de `MotorCampeonato` são verdadeiros. A
causa é a **resolução da curva**. Ela espalha 370 casas sobre 295 dias, a 0,8 dia
por casa, então duas casas vizinhas caem no mesmo dia ou em dias seguidos: a
Champions em 303 e a Europa League em 304 são o caso puro.

Por isso o conserto não foi empurrar 63 jogos um a um — seria coloração com
janelas estreitas, como esta seção previa. Foi **trocar a curva por uma grade em
que a colisão é impossível**:

```
semana w:   +0 ........ +3 ..... +5     semana w+1:  +0
distância:      3          2        2
```

43 semanas a partir de 8 de fevereiro, três casas por semana: o fim de semana
(pontos corridos) e dois meios de semana (copas e continentais). Duas casas
distintas **nunca** são o mesmo dia nem dias seguidos, qualquer que seja a
alocação. Isso troca o problema de coloração por um de **alocação de conjuntos**,
que se resolve lendo uma tabela:

| Casa | Quem joga |
|---|---|
| fim de semana | ligas, Apertura/Clausura, Intermediário, playoff uruguaio |
| meio de semana 1 | Champions, Copa do Brasil, Copa Argentina, excedente de liga longa |
| meio de semana 2 | Europa League, Libertadores, Sul-Americana, FA Cup, Copa do Rei, Coppa Italia |

Duas regras de disjunção fecham o resto, e as duas são **por construção**, não
por sorte:

- **A Sul-Americana joga uma semana depois da Libertadores.** Não basta dizer
  que um clube disputa só uma: o eliminado da Fase 3 da Pré-Libertadores entra
  nos grupos da Sul-Americana, então existe elenco com jogo nas duas.
- **As copas nacionais de meio de semana 2 jogam uma semana depois das
  europeias**, porque um inglês pode estar na Europa League e na FA Cup.

Ambas só funcionam porque o conjunto de origem não tem duas semanas seguidas —
e há um teste que exige exatamente isso, para o dia em que alguma dessas
competições ganhar rodadas.

#### O que mudou de visível

| | Antes | Depois |
|---|---|---|
| 1ª rodada da liga | 8 fev | 8 fev |
| última rodada da liga | 30 nov | 29 nov |
| Supercopa da UEFA | 25 jan (junto da nacional) | 1º fev |
| Recopa (ida/volta) | 7 e 10 fev | 29 jan e 3 fev |
| amistosos de dezembro | os dois em 20 dez | 20 e 23 dez |
| Championship, rodadas 44–46 | as três em 31 dez | dentro da janela |

#### Onde está

- `CalendarioDaTemporada` — a grade, as semanas de cada competição e o validador.
- `CalendarioDasCompeticoes` — os dias de cada uma das 29 competições e os 14
  perfis de clube. **É daqui que a etapa 2 vai ler.**
- `CalendarioDasCompeticoesTest` — exige zero conflito nos 14 perfis, e nomeia
  cada defeito antigo para que a regressão diga *qual* voltou.

`diaDaCasa` continua no lugar, agora como **leitor** do histórico: as temporadas
gravadas antes da etapa 2 seguem na escala que entendem.

#### Um defeito que a própria grade criou, e o teste pegou

A primeira versão mandava o excedente de uma liga longa (Championship, 46
rodadas em 43 semanas) para o meio de semana de qualquer semana — inclusive a
última, cujo meio de semana cai em 4 de dezembro, depois do fim da liga. Era o
mesmo defeito de antes com outra roupa. O excedente agora para na penúltima
semana, e a distinção entre `ULTIMO_DIA_DE_LIGA` (29 nov, pontos corridos) e
`ULTIMO_DIA_DA_GRADE` (4 dez, finais) ficou explícita — confundir as duas foi o
que deixou a rodada vazar.

#### Impacto em saves: faça a etapa 3 SEM tocar nas tabelas vivas

A etapa 3 pode ser feita de dois jeitos, e eles não têm o mesmo custo.

**(A) Confinada ao `CalendarioDaTemporada`** — decidir qual é o calendário
certo no objeto novo, que nenhum gerador lê ainda. **Impacto zero em saves.**

**(B) Mexendo nos `intArrayOf` vivos de `MotorCampeonato`** — aí há impacto, e
ele é específico:

1. **Nada se perde.** As tabelas são lidas na geração, então as partidas já
   gravadas ficam como estão. Nenhum histórico quebra.
2. **Mas a temporada corrente fica MISTA.** Há 14 funções `verificarEAvancarFase*`
   que geram partidas DURANTE a temporada — grupos da Libertadores, oitavas da
   Champions, fases da Copa do Rei, playoff uruguaio. Num save a meio de
   temporada, o que já existe usa os dias velhos e o que vier usa os novos:
   datas incoerentes dentro da mesma competição, e — pior — se um valor novo
   cair abaixo do de uma partida já jogada, `buscarProximaPartida` (que ordena
   por `ordemGlobal`) aponta para algo que o calendário mostra no passado. E os
   conflitos que a etapa 3 resolve **reaparecem** nesse save, porque os valores
   novos foram escolhidos contra os novos, não contra os velhos.
3. **Uma quebra de comportamento real:** `GameRepository:2221` identifica o
   Clausura argentino lendo o valor gravado —
   `any { it.ordemGlobal > ARG_CLAUSURA_OG_OFFSET + 10 }`. Mudar a faixa
   argentina faz um save existente deixar de reconhecer o Clausura, e o avanço
   de fase do torneio erra. Isso não é cosmético.
4. **Quatro casas mágicas** em `CalendarioDoJogo` leem o valor gravado: `== 1`
   (supercopa, 25 jan), `== 620` (Troféu), `>= 400` (amistoso de dezembro),
   `== 440` (Intercontinental). Tirar as supercopas da casa 1 — que é um dos
   consertos da etapa 3 — faz as novas caírem na curva genérica e exibirem data
   errada, enquanto as antigas seguem em 25 jan.

**Uma exceção:** competições geradas INTEIRAS no início da temporada (as ligas
de pontos corridos) só afetam a temporada seguinte, porque o save corrente já
tem o calendário completo delas. A correção da Championship é desta classe.

**Decisão: a etapa 3 ficou no (A).** A troca das tabelas vivas vai junto com a
etapa 2, que já precisa de migração — **uma ruptura só, com migração, em vez de
duas sem**. A migração da etapa 2 resolve os quatro pontos acima de uma vez:
converte a temporada corrente inteira, então não há mistura, o `isClausura` não
lê faixa errada e as casas mágicas desaparecem porque o dia é o dia.

### Etapa 3 — como era descrita

Um teste que varre a temporada inteira de cada perfil de clube e falha se:
nenhum clube joga duas vezes no mesmo dia; nenhum joga em dias consecutivos;
descanso mínimo respeitado; nenhuma partida fora da janela da temporada.

Corrigir o que o teste achar — já sei de **8 colisões e 3 jogos consecutivos**
(§2.2), e a colisão argentina de `ordemGlobal` é obrigatória.

- **Saves:** muda datas de partidas **futuras**; as já jogadas ficam.

### Etapa 4 — transferências e demais dados pelo dia ✅ *(saiu com a etapa 2)*

Ela não precisou de código próprio. A transferência já carimbava `ordemGlobal`,
e a etapa 2 levou a versão da temporada até o DTO dela — então numa temporada da
grade o `ordemGlobal` gravado já É o dia, e a data exata aparece sem conversão.
O mesmo carimbo serve para contratos, empréstimos e o que mais precisar de data.

Nas temporadas da `CURVA` o comportamento é o de antes: o mês é real e o dia sai
da curva.

- **Saves:** nenhum impacto; o fallback para registros sem relógio já existe.

### Etapa 5 — simulação dirigida por data *(opcional, e a cara)*

Trocar "o jogador joga uma rodada → todo mundo joga a próxima rodada dele"
(`simularRodadaEmOutrosCampeonatos`) por "avança para o próximo dia com jogos".

- Mexe nos 26 pontos de rodada do `GameRepository`, nos placares ao vivo (hoje
  "outros jogos da mesma rodada", passariam a ser "outros jogos de hoje") e na
  tela de Rodada.
- **Saves:** o risco não é de dados, é de **comportamento** — um save no meio da
  temporada tem cada competição numa `rodadaAtual` diferente, e um motor por
  data precisa reconciliar isso.
- **Só vale se o avanço dia a dia for uma mecânica desejada.** As etapas 0–4
  entregam o calendário real sem ela.

---

## 6. Resumo de impacto em saves existentes

| Etapa | Quebra save? | Por quê |
|---|---|---|
| 0 — relógio e conversores | não | só corrige ordenação e mês derivado |
| 1 — calendário como dado | não | tabela nova |
| 2 — `ordemGlobal` = dia | **não, com migração** | a data atual é função pura de dados já gravados; há precedente |
| 3 — restrições | não | muda só partidas futuras |
| 4 — transferências | não | fallback já existe |
| 5 — simulação por data | risco de comportamento | reconciliar `rodadaAtual` de cada competição |

---

## 7. Validação num save real *(aparelho conectado, Real Madrid, mar/2027)*

### 7.1 O modelo de datas confere — 7 de 7

Comparando o que a tela do Calendário desenha com a fórmula recalculada fora do
app:

| Partida | `ordemGlobal` | Modelo | Tela |
|---|---|---|---|
| Champions R2 | 67 | 25 mar | 25 mar ✅ |
| La Liga R7 | 70 | 27 mar | 27 mar ✅ |
| La Liga R8 | 80 | 4 abr | 4 abr ✅ |
| La Liga R9 | 90 | 12 abr | 12 abr ✅ |
| La Liga R10 | 100 | 20 abr | 20 abr ✅ |
| Champions R3 | 107 | 26 abr | 26 abr ✅ |
| La Liga R11 | 110 | 28 abr | 28 abr ✅ |

**A lista de colisões do §2.2 fica de pé**, e com ela a lista de aceitação da
etapa 3.

### 7.2 O ritmo real: o descanso mínimo de hoje é 2 dias

Intervalos observados entre jogos consecutivos do clube: **2, 8, 8, 8, 6, 2**.

As rodadas de liga vêm de 8 em 8 dias e a partida continental cai 2 dias antes
da rodada seguinte. **Isso muda a etapa 3:** a restrição não pode ser um
descanso agressivo (3+ dias), que obrigaria a reconstruir o calendário inteiro.
O alvo realista é **"nunca no mesmo dia" e "nunca em dias consecutivos"**, que o
calendário atual já quase cumpre — e aí a etapa 3 vira correção pontual das 8
colisões conhecidas, não um redesenho.

### 7.3 Um bug que o save real expôs: a moral usa a temporada passada

A tela mostrou **"Realizados (60)"** para um clube que jogou ~7 partidas nesta
temporada. As partidas das temporadas anteriores **permanecem no banco** — o que
já se sabia do código, mas agora está confirmado.

E `buscarUltimasPartidasDoTime`, que alimenta a moral do elenco depois de cada
partida (`atualizarMoraleTime`), é assim:

```sql
SELECT * FROM partidas
WHERE (timeCasaId = :timeId OR timeForaId = :timeId) AND jogada = 1
ORDER BY ordemGlobal DESC
LIMIT :limite
```

**Sem filtro de temporada, ordenando por um número que zera a cada temporada.**
As 5 partidas de maior `ordemGlobal` de um clube não são as 5 mais recentes: são
as rodadas finais das temporadas PASSADAS, que têm `ordemGlobal` alto. Para o
Real Madrid em 2027/28, a moral está sendo calculada sobre o fim de 2026/27, e
seguirá assim até a liga chegar perto da última rodada.

**A etapa 2 não conserta isto sozinha** — o dia da temporada também zera todo
ano. O conserto é ordenar por `(temporadaId, ordemGlobal)`, duas linhas, e deve
vir **antes** de tudo, junto com a etapa 0.

`observeReceitasPartidas` (receitas em Finanças) tem a mesma forma e o mesmo
problema. O Calendário escapa porque a tela reordena em Kotlin por
`(ano, data derivada)`.

### 7.4 Segundo achado de tela: o "R" do cabeçalho é de outra competição

O cabeçalho mostrou **"La Liga · 2º · R2 · 25 Mar 2027"** enquanto a próxima
partida da La Liga é a **R7** — o R2 é a rodada da *Champions*, porque
`rodadaAtual = proximaPartida?.rodada` pega a rodada da próxima partida seja de
que competição for, e a exibe sob o nome da liga do clube.

## 8. O que um aparelho conectado ainda mudaria

A análise acima saiu do código e de cálculo sobre as fórmulas. Três perguntas só
um save real responde, e duas delas mexem no plano:

1. ~~O modelo de datas confere?~~ **Respondido no §7.1: confere, 7 de 7.**
2. ~~Qual é o ritmo real?~~ **Respondido no §7.2: 2, 8, 8, 8, 6, 2 dias.**
3. **Qual o tamanho real deste save?** As ~6.300 partidas/temporada assumem
   todas as ligas ativas. Se o save tiver menos, a migração da etapa 2 é menor.
   Continua sem resposta: exige o banco.
4. **O descompasso financeiro em clube argentino (§2.1)** exige um save na
   Argentina; o deste aparelho é da La Liga, que usa a curva genérica.

O que um aparelho **não** dá: o banco. O APK instalado é de release, então
`run-as` não abre o diretório do app, e instalar um debug exigiria desinstalar —
o que apagaria o save. As checagens acima são todas por tela e por logcat.

## 9. O que NÃO foi verificado

- Nada disto foi executado no app nem num save real. As colisões e as
  divergências de mês foram calculadas sobre as fórmulas e as tabelas do código.
- O descompasso financeiro em clube argentino (§2.1) é o que a fórmula produz;
  não foi reproduzido jogando.
- O calendário uruguaio (`ordemGlobal` até 520) e os amistosos (~5 / ~450) não
  entraram na checagem de colisões por perfil.
