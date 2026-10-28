# UEFA Champions League — Regras de Vagas e Sorteio

> **Leia primeiro a seção [Pré-requisito](#pré-requisito-o-jogo-ainda-não-tem-países-para-isso).**
> As travas geográficas do sorteio são **matematicamente impossíveis** com os 3 países
> europeus que o jogo tem hoje. Isso precisa ser resolvido antes de implementar.

Quando existir, esta competição acorda os dois conjuntos de ranking que estão dormentes:
o [Coeficiente de Clubes da UEFA](ranking_europa.md), que pontua por partida e por posição
na Fase de Liga, e o [Ranking Mundial da IFFHS](ranking_mundial.md), onde uma vitória na
Champions vale 14 pontos. O coeficiente da UEFA é também o que **ordena os potes** do
sorteio, então os dois se alimentam.

---

## 1. Lista de acesso — 36 clubes

### Vagas diretas na Fase de Liga

| Países / Ligas | Vagas | Observação |
|---|---:|---|
| Inglaterra, Espanha | 5 cada | 4 pela liga + 1 vaga extra pelo desempenho geral da temporada anterior |
| Alemanha, Itália | 4 cada | topo do ranking de associações |
| França | 3 | 5º lugar no ranking de associações |
| Holanda | 2 | 6º lugar |
| Bélgica, Tchéquia, Turquia, Portugal | 1 cada | só o campeão nacional entra direto |
| Ucrânia | 1 | vaga realocada, porque o campeão vigente já se classificou pela liga |

As vagas restantes vêm das **fases qualificatórias**, um mata-mata que reúne mais de 50
clubes de ligas menores.

> ⚠️ **A aritmética do enunciado não fecha.** Somando a tabela: 5+5+4+4+3+2+1+1+1+1+1 =
> **28 vagas diretas**, não 29. Com as 7 da qualificação dá **35**, e não os 36 da Fase de
> Liga. **Decidido:** a tabela de países fica intacta, com 28 diretas, e a qualificação
> entrega **8** clubes em vez de 7.

### Lista de acesso adotada

| País | Vagas diretas |
|---|---:|
| Inglaterra | 5 |
| Espanha | 5 |
| Alemanha | 4 |
| Itália | 4 |
| França | 3 |
| Holanda | 2 |
| Bélgica | 1 |
| Tchéquia | 1 |
| Turquia | 1 |
| Portugal | 1 |
| Ucrânia | 1 |
| **Direto** | **28** |
| Via qualificação | **8** |
| **Total** | **36** |

### Quem pega as vagas de cada país

**A colocação na liga nacional manda.** Para os países que têm liga completa no jogo —
Inglaterra, Espanha e Itália — as vagas vão para os primeiros colocados do campeonato da
temporada que encerra: os 5 melhores da Premier, os 5 da LaLiga e os 4 da Serie A. É a
tabela final que classifica, não reputação nem coeficiente.

Os clubes do **pool estrangeiro** (Alemanha, França, Holanda, Portugal, Bélgica, Tchéquia,
Turquia, Ucrânia) não disputam liga própria, então para eles não existe colocação: decide o
coeficiente da UEFA, com a reputação desempatando. Vale o mesmo para a **primeira
temporada** de qualquer país, quando ainda não houve campeonato encerrado — aí a reputação
do seed decide sozinha.

Um clube com colocação sempre vem antes de um sem. Dentro de um país isso nunca mistura os
dois; na fila da qualificação, significa que um 6º colocado da Premier passa à frente de um
clube de pool que não tem campanha nenhuma.

> **Simplificação.** O enunciado dá 4 das 5 vagas inglesas "pela liga" e 1 "pelo desempenho
> geral do país na temporada anterior". Aqui as 5 saem da tabela, porque o jogo não tem o
> ranking de associações que distribui essa vaga extra.

### Conferência com a fonte oficial da Premier League

As regras de classificação europeia publicadas pela Premier League foram conferidas contra
o que está implementado. O que elas **confirmam**:

| Regra da fonte | Estado no jogo |
|---|---|
| Os 4 primeiros vão direto à Champions, e o 5º também quando a Inglaterra fica entre as melhores federações do coeficiente (bônus EPS) | **Implementado como 5 vagas fixas.** É o cenário COM o bônus, e é a simplificação registrada acima: o jogo não tem ranking de associações para decidir ano a ano. |
| Europa League: campeão da FA Cup + melhor da liga logo abaixo da zona da Champions | **Implementado.** Ver a tabela de mapeamento em [europa_league.md](europa_league.md). |
| Vaga de copa cujo campeão já se classificou pela liga desce para o próximo não classificado, podendo chegar ao 7º | **Implementado**, na lista de acesso e na zona da tabela (`VagasEuropeias`). Com uma única copa no jogo, o limite é o 7º; o 8º da fonte depende da Copa da Liga. |

O que ficou **de fora, de propósito**:

- **UEFA Conference League** e a vaga do campeão da **EFL Cup**. Nenhuma das duas existe no
  jogo: a Conference tem só o código de Hall reservado (32), e a Copa da Liga é justamente o
  "torneio secundário" que [ranking_mundial.md](ranking_mundial.md) manda ignorar. São duas
  competições novas para construir, não um ajuste de cota.
- **A 5ª vaga condicional.** Tornar o bônus EPS dependente do desempenho do país exigiria um
  ranking de **associações**, que o jogo não tem — o `RankingEuropa` é de clubes — e mudaria
  a soma de 28 vagas diretas, que é o que fecha os 36 da Fase de Liga.

E o que falta de verdade é a **vaga do campeão vigente** — item 4 de [Falta](#falta), que é
de onde sai o cenário de representação expandida que a fonte descreve.

---

## 2. O sorteio — modelo híbrido

Cada clube enfrenta **8 adversários diferentes**, não um grupo. Um sorteio manual com
bolinhas exigiria mais de mil combinações e levaria horas, então é automatizado.

1. **Divisão em potes.** Os 36 clubes são divididos em **4 potes de 9**, ordenados
   estritamente pelo **ranking individual de coeficientes da UEFA** — o mesmo coeficiente
   de [ranking_europa.md](ranking_europa.md). Pote 1 são os 9 de maior coeficiente, e assim
   por diante.
2. **Início manual.** Uma bolinha física define qual clube terá os rivais sorteados
   primeiro.
3. **Ação do computador.** Escolhido o clube, o software sorteia na hora os seus 8
   adversários — **2 de cada pote** — e define quais **4 jogos são em casa e 4 fora**.
4. Repete até os 36 estarem completos.

### Travas geográficas

| Trava | Regra |
|---|---|
| Mesmo país | Um clube **nunca** enfrenta rivais do próprio país na Fase de Liga |
| Concentração | Um clube enfrenta **no máximo 2** clubes de um mesmo país |

> **Sobre o mando:** o enunciado pede "2 de cada pote" e "4 em casa e 4 fora". A
> implementação usa a forma mais específica da regra real — **1 em casa e 1 fora contra
> cada pote** — que satisfaz as duas exigências de uma vez.

### Três limites que as travas impõem

As travas não são apenas filtros no sorteio: elas restringem quem pode estar no torneio.
Todos os três são verificados antes do sorteio começar, para falhar com uma causa clara em
vez de esgotar o retrocesso sem explicação.

| Limite | Conta | Valor |
|---|---|---:|
| Países no torneio | 8 adversários, no máximo 2 por país → 4 países além do seu | **≥ 5** |
| Clubes de um país | `8K ≤ 2(36 − K)` → `10K ≤ 72` | **≤ 7** |
| Clubes de um país **num pote** | `2K ≤ 2(9 − K)` | **≤ 4** |

O terceiro é o menos óbvio e **não aparece na contagem global**: um país com 5 clubes cabe
folgadamente no torneio (limite 7), mas não cabe num pote só. Se os 5 ingleses tivessem os
9 maiores coeficientes e caíssem todos no pote 1, o sorteio seria impossível — dentro do
pote, os 5 ingleses demandariam 10 vagas dos 4 espanhóis, que oferecem 8. Na prática os
coeficientes se intercalam entre as ligas e o pote 1 reúne os melhores de vários países,
mas o caso degenerado é detectado e recusado.

---

## Pré-requisito: o jogo ainda não tem países para isso

O seed tem **3 países europeus**: Inglaterra (20 na Premier + 24 na Championship), Espanha
(20) e Itália (20) — 60 clubes de elite. Não existem clubes da Alemanha, França, Holanda,
Portugal, Bélgica, Tchéquia, Turquia ou Ucrânia.

Isso gera dois problemas de tamanhos muito diferentes.

**O menor:** a lista de acesso pede 11 países para distribuir 29 vagas diretas. Com 3
países o jogo oferece no máximo 5 + 5 + 4 = 14.

**O que realmente trava:** as duas travas geográficas tornam o sorteio impossível. Um clube
inglês não pode enfrentar ingleses, e pode pegar no máximo 2 espanhóis e 2 italianos:

```
máximo de adversários = 2 × (número de países disponíveis − 1)
com 3 países:  2 × 2 = 4 adversários
necessário:                8 adversários
```

Não é questão de calibragem nem de sorteio azarado — **nenhuma combinação existe**. Para as
travas funcionarem como escritas, um clube precisa de adversários de **pelo menos 4 outros
países**, ou seja o torneio precisa de **5 países no mínimo**, e isso ainda deixaria o
sorteio muito apertado. Com os 11 países da lista de acesso, ele respira.

### Decidido: pool de clubes estrangeiros, sem ligas nacionais

Os clubes que faltam entram num **pool sem liga nacional**, no modelo que já existe para
torneios intercontinentais — divisões 90/91/92, onde hoje está o Inter Miami. Eles existem
para disputar a Champions: não têm campeonato próprio, não aparecem nos rankings nacionais
e não entram em promoção/rebaixamento. Se as ligas desses países forem criadas depois, os
clubes migram para elas sem alterar o torneio.

O formato de um clube de pool, como no seed atual:

```json
{"id": 601, "nome": "Inter Miami", "cidade": "Miami", "estado": "FL",
 "divisao": 92, "pais": "Estados Unidos", "nivel": 7, "saldo": 4000000000,
 "estadio_nome": "Chase Stadium", "estadio_capacidade": 21550,
 "preco_ingresso": 5000, "tatica": "4-3-3", "estilo": "OFENSIVO",
 "reputacao": 72, "escudo_res": ""}
```

Quantos clubes: as 8 associações que faltam somam **17 vagas diretas** (Alemanha 4, França
3, Holanda 2, Bélgica 1, Tchéquia 1, Turquia 1, Portugal 1, Ucrânia 1), mais os 8 da
qualificação. Como a qualificação também precisa de clubes para eliminar, o pool deve ser
maior que as vagas — a proposta é **~30 clubes**, distribuídos entre as 8 associações mais
algumas ligas menores, o que dá margem para a qualificação ter alguma disputa.

### Os outros caminhos considerados

| Caminho | Por que não foi escolhido |
|---|---|
| **Expandir o seed com as ligas** | Criar as primeiras divisões dos 8 países é o caminho mais fiel e daria 8 ligas jogáveis, mas é de longe o maior trabalho — clubes, jogadores, escudos e a fiação de seed descrita em `seed-pipeline-quirk`. Fica como evolução possível do pool. |
| **Relaxar as travas** | Manter os 3 países e abandonar a trava de concentração funcionaria sem tocar no seed, mas descaracteriza o torneio: um clube inglês pegaria 8 rivais entre espanhóis e italianos, e a Champions viraria um torneio de três países. |
| **Fase de Liga menor** | Reduzir a Fase de Liga não resolve nada: com 3 países o teto de adversários é 4, qualquer que seja o tamanho do torneio. |

---

## Estado da implementação

### Pronto

**O sorteio** — `SorteioChampions.kt`, com 13 testes. Monta os 4 potes de 9 por
coeficiente, sorteia os 8 adversários de cada clube (2 por pote, 1 em casa e 1 fora) por
retrocesso com heurística de slot mais restrito, e produz as 144 partidas. Verifica os três
limites acima antes de começar e devolve a causa quando o conjunto é inviável, inclusive o
caso real dos 3 países do seed. Tem também `validar()`, que confere um sorteio inteiro
contra todas as regras — usado nos testes e aproveitável para checar save antigo.

**A lista de acesso** — `ListaDeAcessoChampions.kt`. Distribui as 28 vagas diretas pela
tabela de países e abre a qualificação para os melhores clubes europeus restantes, com o
teto de 7 clubes por país que as travas impõem. Casa grafias diferentes do mesmo país
("Países Baixos" = "Holanda"), para que duas escritas não burlem a trava de compatriotas.

> **Ordem dos potes na 1ª temporada.** O coeficiente da UEFA ordena os potes, mas no
> primeiro ano ele é zero para todos — e `montarPotes` desempata por NOME. Os potes sairiam
> em ordem alfabética, os 5 ingleses cairiam no mesmo pote e o sorteio seria recusado por
> concentração. Por isso o peso do sorteio é `coeficiente × 1000 + reputação`: o
> coeficiente manda sempre, e a reputação só desempata.

**O formato** — `FormatoCampeonato.FASE_LIGA_E_MATA_MATA`. As partidas da Fase de Liga têm
`fase = null`, como a fase de grupos dos torneios argentino e uruguaio: é isso que faz a
classificação de 1 a 36 ser alimentada pelo caminho normal, sem nenhum caso especial.

**A competição** — `GameRepository.criarChampionsDaTemporada`, com
`TipoCampeonato.CONTINENTAL` e nome "Champions League &lt;ano&gt;" (é o nome que o peso 14 da
IFFHS reconhece). As 144 partidas são distribuídas em 8 rodadas por coloração de arestas
(`MotorCampeonato.distribuirRodadasFaseLiga`), com `ordemGlobal` encaixado entre as rodadas
das ligas nacionais; o mata-mata usa as rodadas 19–28, longe da Fase de Liga.

**Play-off e mata-mata** — 9º–24º no play-off, melhor colocado decidindo em casa; os 8
primeiros entram nas oitavas contra os vencedores, o 1º contra o vencedor do último par.
Das oitavas à final o avanço é o do motor de copa ida/volta, com pênaltis no empate.

**Os rankings** — ao encerrar, cada participante tem a campanha registrada no coeficiente
da UEFA, com a posição na Fase de Liga, vitórias, empates e a fase máxima do mata-mata (o
play-off não conta para o bônus de avanço, mas as suas partidas pontuam por jogo). O
ranking mundial da IFFHS deriva das partidas sozinho.

**Hall da Fama** — divisão 30, no recorte "Europa" (31 e 32 ficam para a Europa League e a
Conference League). **Telas** — a Fase de Liga na tela de Classificação, no recorte Europa,
com as faixas de oitavas e play-off; o mata-mata na aba "Champions" do chaveamento.

### Falta

1. **Os clubes do pool** — ~30 clubes das 8 associações que faltam, no modelo de divisão
   90/91/92. Enquanto não existirem, `criarChampionsDaTemporada` devolve -1 e a competição
   simplesmente não é criada: o sorteio recusa 3 países, e é o único bloqueio restante.
   O pool foi escrito em paralelo e **já confere**: com ele, a 1ª temporada monta 36 clubes
   de 15 países e nenhum pote passa de 4 clubes do mesmo país (o teto: Espanha 4 no pote 1,
   Itália 4 no pote 2). O caso degenerado que recusaria o sorteio não acontece — mas ele
   depende das reputações do seed, então vale reconferir se elas mudarem muito.
2. **As fases qualificatórias de verdade.** As 8 vagas da qualificação já saem das ligas
   menores, como manda o enunciado, mas vão direto para as melhores delas — o mata-mata
   entre mais de 50 clubes não existe. `registrarQualificacaoEuropeia` já está pronto para
   pontuar quem cair nele.
3. **Chaveamento fixo das oitavas em diante.** Das quartas à final os confrontos são
   sorteados a cada fase (`sortearPares`), como nas outras copas do jogo, em vez de seguirem
   um quadro fechado a partir das oitavas como na Champions de verdade.
4. **A vaga estrutural do campeão vigente.** O campeão da Champions e o da Europa League
   deveriam entrar na edição seguinte **por fora da cota do país**, com a vaga da liga
   cascateando quando eles também se classificariam por ela. É disso que vem a
   "representação expandida" que a fonte da Premier League descreve — até 11 clubes ingleses
   em cenários excepcionais. Hoje `ListaDeAcessoChampions.montar` distribui apenas as vagas
   diretas e a qualificação, então um campeão que termina fora da zona simplesmente não se
   classifica para defender o título.

   O caminho está pronto dos dois lados: o Hall da Fama já guarda os dois campeões (divisões
   30 e 31), e o Brasil já faz exatamente isso com a Libertadores — ver
   `VagasDoBrasileirao` e o `campeoesContinentaisTimeIds` de
   `buscarVagasContinentaisDoBrasil`. Falta portar para a Europa.
