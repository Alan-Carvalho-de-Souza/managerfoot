# UEFA Europa League — Regras de Vagas e Formato

> Companheira de [regras_championsleague.md](regras_championsleague.md): mesmo formato de
> liga única de 36 clubes, mesma estrutura de classificação, lista de acesso diferente.
>
> ✅ **O pré-requisito dos clubes está resolvido.** O pool europeu subiu de 30 para 52
> clubes e as duas competições cabem na mesma temporada — ver
> [Pré-requisito](#pré-requisito-os-clubes-para-as-duas-competições), com a conta nova, e
> `SeedEuropaTest`, que monta as duas a partir do seed real e sorteia as duas.

Esta competição pontua nos dois conjuntos de ranking que já estavam prontos antes dela:
no [Coeficiente de Clubes da UEFA](ranking_europa.md) — onde o topo da Fase de Liga vale 6
pontos, caindo 0,25 por posição, e cada fase do mata-mata soma 1,0 — e no
[Ranking Mundial da IFFHS](ranking_mundial.md), onde uma vitória vale 12 pontos.

---

## 1. Os pilares do formato

| Regra | Detalhe |
|---|---|
| **Fase de Liga única** | A fase de grupos foi extinta. Os 36 clubes competem numa tabela geral única. |
| **8 jogos por clube** | Cada equipe enfrenta 8 adversários diferentes: 4 em casa e 4 fora. |
| **Classificação direta** | Do **1º ao 8º**: avançam direto às oitavas de final. |
| **Play-off de mata-mata** | Do **9º ao 24º**: disputam uma fase prévia de ida e volta. |
| **Eliminação sem repescagem** | Do **25º ao 36º**: estão fora, **sem** direito a migrar para a Conference League. |
| **Sem gol fora** | O gol como visitante não é mais critério de desempate no mata-mata. |

Essa estrutura é **idêntica à da Champions**, o que importa muito na prática: as constantes
e os geradores já existem em `MotorCampeonato` (`CHAMPIONS_RODADAS_LIGA = 8`,
`CHAMPIONS_CLASSIFICADOS_DIRETO = 8`, `CHAMPIONS_ULTIMO_DO_PLAYOFF = 24`, mais
`distribuirRodadasFaseLiga`, `gerarPlayoffChampions` e `gerarOitavasChampions`). A Europa
League é, em grande parte, uma segunda instância da mesma máquina — o que muda é a lista de
acesso e os pesos de coeficiente.

> **Potes e travas geográficas: o enunciado não fala deles.** Ele diz "4 em casa e 4 fora",
> mas não menciona a divisão em 4 potes de 9 nem as duas travas (nunca um compatriota, no
> máximo 2 rivais de um mesmo país). Na Europa League real as duas valem, iguais às da
> Champions, e adotei que valem — é o que torna necessária a conta do pré-requisito abaixo.
> Sem travas, a Europa League cabe no seed atual; se preferir assim, é só dizer.

---

## 2. Distribuição de vagas por país

A UEFA atribui as vagas pela força da liga no ranking de coeficientes das federações. Cada
país indica os seus clubes melhor posicionados — tipicamente o **campeão da copa nacional**
ou os **colocados logo abaixo da zona da Champions**.

| Posição no ranking da UEFA | Países | Vagas por país | Subtotal |
|---|---|---:|---:|
| 1º ao 5º | Inglaterra, Itália, Espanha, Alemanha, França | 2 | **10** |
| 6º ao 15º | Países Baixos, Portugal, Bélgica, Escócia, Áustria, Sérvia, Turquia, Suíça, Ucrânia, República Checa | 1 | **10** |
| Exceção | Liechtenstein (só o campeão da copa local) | 1 | **1** |
| 16º ao 55º | Demais associações | via qualificatórias | — |

**Aritmética, que o enunciado não fecha:** as vagas diretas somam **21** (10 + 10 + 1).
Faltam **15** para os 36, e vêm das duas origens que o enunciado cita de passagem — as
fases qualificatórias e os eliminados que caem da Champions League.

| Origem | Vagas |
|---|---:|
| Acesso direto por país | 21 |
| Qualificatórias + eliminados da Champions | 15 |
| **Total** | **36** |

> **Liechtenstein é um caso real, não um erro de digitação.** O país não tem liga nacional
> própria — seus clubes jogam no campeonato suíço —, então a copa local é a única porta de
> entrada. Vale deixar a exceção explícita, para ninguém "corrigir" isso depois.

### Como as vagas mapeiam neste jogo

Aqui a Europa League tem uma vantagem sobre a Champions: **os três países com liga completa
no jogo também têm copa nacional implementada**. Então as 2 vagas de cada um saem de onde a
regra manda, sem arbítrio:

| País | Vaga 1 | Vaga 2 |
|---|---|---|
| Inglaterra | Campeão da Copa da Inglaterra | Melhor da Premier fora da zona da Champions |
| Espanha | Campeão da Copa do Rei | Melhor da LaLiga fora da zona da Champions |
| Itália | Campeão da Coppa Italia | Melhor da Serie A fora da zona da Champions |

Como a Champions leva 5 ingleses, 5 espanhóis e 4 italianos pela colocação na liga, "fora
da zona" é o 6º da Premier, o 6º da LaLiga e o 5º da Serie A.

E se o campeão da copa já estiver classificado à Champions pela liga, a vaga **desce para o
colocado seguinte**. Isso precisa ser explícito no código: sem essa regra o clube ocuparia
duas vagas e o país entraria com um time a menos.

> **Conferido com a fonte oficial da Premier League.** Ela descreve exatamente este
> mapeamento — campeão da FA Cup mais o melhor da liga abaixo da zona da Champions, com a
> vaga de copa descendo para o próximo não classificado, o que pode levar o 7º à Europa
> League. A fonte menciona também uma vaga de **Conference League** para o campeão da EFL
> Cup; nenhuma das duas existe no jogo, e a decisão de deixar as duas de fora está
> registrada em [regras_championsleague.md](regras_championsleague.md), na seção
> "Conferência com a fonte oficial da Premier League".

Os demais países são clubes de pool, sem liga nem copa. Para eles decide o coeficiente da
UEFA com a reputação desempatando, igual ao que a Champions já faz.

---

## Pré-requisito: os clubes para as duas competições

> **Resolvido.** O pool europeu foi ampliado de 30 para 52 clubes. O que segue é a conta
> que motivou a ampliacão, mantida porque ela explica *por que* o pool tem o tamanho que
> tem — e porque encolher o pool quebra a Europa League sem mudar nada na Champions.

### A conta que não fechava

O seed tinha **90 clubes europeus elegíveis**: 60 de elite (Inglaterra, Espanha e Itália,
20 cada) e 30 no pool, espalhados por 19 associações. Parece bastante para dois torneios
de 36 — mas a trava de **no máximo 7 clubes por país** muda a conta.

A Champions consome, pela sua lista de acesso, 14 clubes de elite (5 + 5 + 4) e 22 do pool
(14 diretos das 8 associações com vaga, mais 8 da qualificação). Sobravam:

| Origem | Total | Champions | Sobra |
|---|---:|---:|---:|
| Elite (ING + ESP + ITA) | 60 | 14 | 46 |
| Pool (19 associações) | 30 | 22 | 8 |
| **Total** | **90** | **36** | **54** |

Eram 54 clubes para 36 vagas, o que parecia confortável. Não era:

```
máximo de elite na Europa League = 7 por país × 3 países = 21
sobra de pool disponível                                 =  8
máximo montável                                          = 29
necessário                                               = 36
```

E o gargalo não era o número de associações — era que o pool tinha 30 clubes para 19
associações, cerca de 1,5 cada. A elite não compensava, porque o teto de 7 por país a
limitava a 21.

### O que foi feito

O caminho escolhido foi o recomendado: **ampliar o pool**, de 30 para **52** clubes, em 20
associações — 2 a 3 por associação em vez de 1,5. Os 22 clubes novos saem do mesmo
`scripts/gen_outros.py` dos 30 primeiros, com 20 jogadores cada (2 goleiros) e um técnico
real. Divisão 91, ids 632–653.

A distribuição não é uniforme, porque a demanda não é: cada país precisa de tantos clubes
quantas vagas diretas tem **nas duas competições somadas**, mais o que a qualificação puxar.

| Associação | Pool | Champions (diretas) | Europa (diretas) |
|---|---:|---:|---:|
| Alemanha | 6 | 4 | 2 |
| França | 5 | 3 | 2 |
| Holanda | 3 | 2 | 1 |
| Escócia, Áustria, Suíça, Grécia, Dinamarca | 3 cada | — | 1 (as três primeiras) |
| Portugal, Bélgica, Tchéquia, Turquia, Ucrânia, Sérvia | 2 cada | 1 (menos Sérvia) | 1 |
| Noruega, Croácia, Polônia, Suécia, Romênia | 2 cada | — | — |
| Liechtenstein | 1 | — | 1 (só pela copa) |

Com isso as duas fases de liga fecham os 36 e passam pelas travas: a Champions sai com 17
países e no máximo 5 clubes de um, a Europa League com 22 países e no máximo 5.

### As reputações não são decorativas

Dentro de cada país as reputações são **escalonadas de propósito**: o primeiro clube vai à
Champions, o segundo é o que sobra para a vaga direta da Europa League, o terceiro alimenta
a qualificação. Subir um clube acima do titular do seu país troca as duas competições de
lugar — e, pior, pode estourar o limite menos óbvio das travas: **no máximo 4 clubes de um
mesmo país por pote**. Os potes saem do coeficiente, que na 1ª temporada é a reputação
sozinha, então uma reputação nova pode empilhar cinco clubes de um país no topo sem mudar
contagem nenhuma. `SeedEuropaTest` e `SeedChampionsTest` são a rede de segurança disso.

### Liechtenstein e as duas temporadas

Liechtenstein só tem a porta da copa local. Na **1ª temporada** nenhuma copa encerrou, então
essa porta está fechada e as vagas diretas fecham em **20**, não 21 — o FC Vaduz entra pela
qualificação e o torneio fecha os 36 de qualquer forma. Da **2ª em diante**, com o campeão da
copa conhecido, são as 21. Não é defeito; é a exceção do enunciado vista de frente, e os dois
casos têm teste.

### Caminhos que não foram seguidos

| Caminho | Por que não |
|---|---|
| **Segundas divisões europeias** | Habilitar a Championship (divisão 12) e criar segundas divisões para Espanha e Itália **não resolve**: o teto de 7 é por país, não por divisão. |
| **Europa League sem travas** | Cabia de imediato no seed antigo, mas um clube inglês enfrentaria 8 rivais entre espanhóis e italianos, e o torneio perderia o sentido continental. |

## Estado da implementação

### Pronto

**O formato continental generalizado** — `MotorCampeonato.FormatoContinental`, com as
instâncias `FORMATO_CHAMPIONS` e `FORMATO_EUROPA_LEAGUE`. As constantes `CHAMPIONS_*`
antigas continuam existindo e apontando para os mesmos valores, então a Champions não mudou
de comportamento. A Europa League joga um dia depois, com cada casa de `ordemGlobal` igual
à da Champions + 1, e base de rodada do mata-mata em 40 (contra 20): um teste garante que
as duas competições **nunca colidem** no calendário, nem entre si nem com as rodadas das
ligas nacionais, que usam múltiplos de 10.

Os geradores do motor também passaram a receber o formato: `distribuirRodadasFaseLiga`,
`gerarFaseLigaChampions`, `paresPlayoffChampions` e `paresOitavasChampions` têm um
parâmetro `formato` com `FORMATO_CHAMPIONS` por padrão, então nenhuma chamada antiga mudou.

**Os clubes** — o pool europeu tem 52 clubes em 20 associações (divisão 91, ids 602–653),
o suficiente para as duas competições na mesma temporada. `SeedEuropaTest` monta as duas a
partir do `seed_data.json` real, na ordem em que o jogo as monta — a Champions primeiro, a
Europa League depois recebendo os 36 já ocupados — e sorteia as duas com várias sementes.

**A lista de acesso** — `ListaDeAcessoEuropa.kt`, com 15 testes. As 21 vagas diretas, as
duas portas por país (campeão da copa e melhor da liga fora da zona da Champions), a queda
da vaga da copa para o colocado seguinte, a exceção de Liechtenstein, a exclusão mútua com
a Champions e a fila de qualificação com o teto por país.

**O repositório** — `determinarParticipantesEuropa`, `criarEuropaLeague` e
`criarEuropaDaTemporada`, no modelo das funções da Champions. A base de candidatos europeus
(coeficiente da UEFA + colocação final das ligas) virou o helper comum `candidatosEuropeus`,
e os campeões das copas nacionais europeias da temporada que encerra saem de
`campeoesDasCopasEuropeias`, que alimenta a porta da copa.

A máquina de fases foi **generalizada**, não duplicada: `verificarEAvancarFaseContinental`
recebe o `FormatoContinental`, o código do Hall da Fama, a `CompeticaoUefa` e os prêmios;
`verificarEAvancarFaseChampions` e `verificarEAvancarFaseEuropa` são casca em cima dela.
O mesmo vale para `gerarPlayoffContinental`, `gerarOitavasContinental`,
`registrarCampanhasEuropeias` (que agora recebe a competição) e
`simularProximaRodadaContinentalSeNecessario`. No rollover, `encerrarECriarEuropa` roda
**depois** de `encerrarECriarChampions` e recebe o id da Champions nova: os participantes
dela são os `ocupados` da lista de acesso.

O Hall da Fama da Europa League é a **divisão 31** (a 30 é a Champions, a 32 fica para a
Conference League), e ao encerrar a competição cada campanha é registrada em
`CompeticaoUefa.EUROPA_LEAGUE`, com os pesos que já estavam em `RankingEuropa`.

**O save** — `europaLeagueId` no `GameDataStore`, no padrão do `championsId`: campo,
chave, leitura e as duas funções de gravação. Não precisou de migração de banco: nada
mudou no Room, e uma chave de DataStore ausente num save antigo lê como -1, que é o mesmo
que "não existe essa competição".

**As telas** — Rodadas (32 = Fase de Liga, 33 = mata-mata), Classificação (31, com as
mesmas zonas e legenda da 30), Artilharia (36) e Chaveamento (aba "Europa League"), todas
no recorte de país "Europa". O chaveamento mantém o filtro de `observeCopaPartidas`
(`fase IS NOT NULL AND confrontoId IS NOT NULL`), que é o que impede as 144 partidas da
Fase de Liga de tentarem entrar num `CopaPartidaDto` de campos não-nulos.

**Os testes** — `EuropaLeagueTest` cobre a criação (montagem depois da Champions, sem
interseção de clubes; sorteio viável; 144 partidas com 8 por clube; casas do calendário) e
o avanço de fases (play-off do 9º ao 24º, cruzamento das oitavas, e o torneio de 8 para 1
confronto até sobrar um campeão).

### Falta

Nada do que esta seção listava: a competição está completa e jogável, com os clubes do
seed real. O que sobrou não é da Europa League em si:

- **Escudos dos 22 clubes novos do pool.** Ficam com `""` (escudo genérico) até a imagem
  existir em `res/drawable`; o mapa `ESCUDOS` do `gen_outros.py` está pronto para recebê-la.
- **Os eliminados da Champions que caem na Europa League.** O enunciado cita a mecânica mas
  não a detalha, e ela criaria dependência de ordem dentro da temporada: parte do campo só
  seria conhecida depois das qualificatórias da Champions. Hoje as 15 vagas de qualificação
  saem todas da fila de acesso, o que fecha os 36 sem essa dependência.
- **Conference League.** A divisão 32 do Hall da Fama e `CompeticaoUefa.CONFERENCE_LEAGUE`
  já estão reservadas e testadas. Com a máquina de fases parametrizada pelo
  `FormatoContinental`, uma terceira competição é uma terceira instância do formato mais uma
  lista de acesso — sem mexer no repositório.
