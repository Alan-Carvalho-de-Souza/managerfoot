# UEFA Europa League — Regras de Vagas e Formato

> Companheira de [regras_championsleague.md](regras_championsleague.md): mesmo formato de
> liga única de 36 clubes, mesma estrutura de classificação, lista de acesso diferente.
>
> ⚠️ **Leia o [Pré-requisito](#pré-requisito-faltam-clubes-para-as-duas-competições) antes
> de implementar.** Com o seed atual, Champions e Europa League **não cabem juntas** sob as
> travas geográficas. A conta está lá.

Quando existir, esta competição pontua nos dois conjuntos de ranking que já estão prontos:
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

Os demais países são clubes de pool, sem liga nem copa. Para eles decide o coeficiente da
UEFA com a reputação desempatando, igual ao que a Champions já faz.

---

## Pré-requisito: faltam clubes para as duas competições

O seed tem **91 clubes europeus elegíveis**: 60 de elite (Inglaterra, Espanha e Itália, 20
cada) e 31 no pool, espalhados por 20 associações. Parece bastante para dois torneios de
36 — mas a trava de **no máximo 7 clubes por país** muda a conta.

A Champions consome, pela sua lista de acesso, 14 clubes de elite (5 + 5 + 4) e 22 do pool
(14 diretos das 8 associações com vaga, mais 8 da qualificação). Sobram:

| Origem | Total | Champions | Sobra |
|---|---:|---:|---:|
| Elite (ING + ESP + ITA) | 60 | 14 | 46 |
| Pool (20 associações) | 31 | 22 | 9 |
| **Total** | **91** | **36** | **55** |

São 55 clubes para 36 vagas, o que parece confortável. Não é:

```
máximo de elite na Europa League = 7 por país × 3 países = 21
sobra de pool disponível                                 =  9
máximo montável                                          = 30
necessário                                               = 36
```

**Faltam 6 clubes.** E o gargalo não é o número de associações — é que o pool tem só 31
clubes para 20 associações, cerca de 1,5 cada. A elite não compensa, porque o teto de 7 por
país a limita a 21.

### Caminhos

| Caminho | O que implica |
|---|---|
| **Ampliar o pool (recomendado)** | Subir o pool de 31 para ~50 clubes, com 2 a 3 por associação. Resolve o gargalo com folga, dá disputa real às qualificatórias das duas competições, e é trabalho já trilhado: o `scripts/gen_outros.py` existe e gera clubes com elenco. |
| **Segundas divisões europeias** | Habilitar a Championship (divisão 12) e criar segundas divisões para Espanha e Itália. **Não resolve:** o teto de 7 é por país, não por divisão. |
| **Europa League sem travas** | Manter o seed e abandonar as travas geográficas só nesta competição. Cabe de imediato, mas um clube inglês enfrentaria 8 rivais entre espanhóis e italianos, e o torneio perde o sentido continental. |

---

## Estado da implementação

### Pronto

**O formato continental generalizado** — `MotorCampeonato.FormatoContinental`, com as
instâncias `FORMATO_CHAMPIONS` e `FORMATO_EUROPA_LEAGUE`. As constantes `CHAMPIONS_*`
antigas continuam existindo e apontando para os mesmos valores, então a Champions não mudou
de comportamento. A Europa League joga um dia depois, com cada casa de `ordemGlobal` igual
à da Champions + 1, e base de rodada do mata-mata em 40 (contra 20): um teste garante que
as duas competições **nunca colidem** no calendário, nem entre si nem com as rodadas das
ligas nacionais, que usam múltiplos de 10.

**A lista de acesso** — `ListaDeAcessoEuropa.kt`, com 15 testes. As 21 vagas diretas, as
duas portas por país (campeão da copa e melhor da liga fora da zona da Champions), a queda
da vaga da copa para o colocado seguinte, a exceção de Liechtenstein, a exclusão mútua com
a Champions e a fila de qualificação com o teto por país.

### Falta

1. **Os clubes** — ver o pré-requisito acima. É o que impede a competição de rodar.
2. **O repositório**: `determinarParticipantesEuropa`, `criarEuropaLeague`,
   `criarEuropaDaTemporada`, avanço de fases, Hall da Fama e a chamada de
   `registrarCampanhaEuropeia` ao encerrar. A máquina de fases da Champions
   (`verificarEAvancarFaseChampions`, `gerarPlayoffChampions`, `gerarOitavasChampions`)
   serve, mas hoje está escrita para uma competição só — use o `FormatoContinental`.
3. **O save**: um `europaLeagueId` no `GameDataStore`, no modelo do `championsId`.
4. **As telas**: Rodadas, Classificação, Artilharia e Chaveamento, todas no mesmo padrão já
   usado pela Champions (recorte "Europa", um código de divisão novo por tela).

## O que a implementação vai precisar

1. **Os clubes** — ver o pré-requisito. Sem eles o sorteio recusa, como já recusa o da
   Champions quando o conjunto é inviável.
2. **A lista de acesso da Europa League**, no modelo de `ListaDeAcessoChampions.kt`: as 21
   vagas diretas, a queda da vaga da copa para o colocado seguinte, e a fila de qualificação.
3. **Exclusão mútua com a Champions.** Um clube não disputa as duas, então a Europa League
   precisa ser montada **depois** da Champions, recebendo quem já está ocupado. Hoje
   `determinarParticipantesChampions` não devolve essa informação.
4. **Os eliminados da Champions que caem na Europa League.** O enunciado cita a mecânica mas
   não a detalha, e ela cria dependência de ordem: parte do campo da Europa League só é
   conhecida depois das qualificatórias da Champions.
5. **Generalizar a máquina da Champions.** As constantes e geradores de `MotorCampeonato` se
   chamam `CHAMPIONS_*` e assumem uma única competição continental por temporada. Vale
   extrair para algo parametrizável antes de duplicar.
6. **Pesos do coeficiente** — nada a fazer além de chamar. Já estão implementados e testados
   em `RankingEuropa.kt` (topo 6, passo 0,25, avanço 1,0, garantia de participação 3) e em
   `RankingMundial.kt` (12 por vitória).
