# CONMEBOL Copa Sul-Americana — Regras de Vagas e Formato

> A irmã menor de [regras_libertadores.md](regras_libertadores.md), e **acoplada a ela**:
> parte do campo da Sul-Americana vem da Libertadores, em dois momentos diferentes. Os dois
> documentos se encaixam exatamente — ver a seção de aritmética.

Quando existir, pontua no [Ranking CONMEBOL](ranking_conmebol.md) — campeão 300, vice 120,
semifinal 72, quartas 48, oitavas 24, mais 24 por vitória e 12 por empate da fase de grupos
em diante — e no [Ranking Mundial da IFFHS](ranking_mundial.md), onde uma vitória vale 12
pontos, contra os 14 da Libertadores.

---

## 1. Participantes e vagas

**56 equipes no total.**

| País | Vagas | Entrada |
|---|---:|---|
| Brasil | 6 | direto na fase de grupos |
| Argentina | 6 | direto na fase de grupos |
| Bolívia, Chile, Colômbia, Equador, Paraguai, Peru, Uruguai, Venezuela | 4 cada | Primeira Fase |

As vagas nacionais somam **44**: 12 do Brasil e da Argentina, mais 32 dos outros oito
países.

---

## 2. O formato, fase por fase

### Primeira Fase — preliminar nacional

- **32 clubes**, dos oito países que não são Brasil nem Argentina.
- Eliminatórias de **partida única**, e **dentro do mesmo país**: um clube boliviano
  enfrenta outro boliviano. É o que faz desta fase uma preliminar *nacional*.
- Empate vai direto aos **pênaltis**.
- **16 vencedores** avançam, dois por país.

### Fase de grupos

**32 clubes**, de três origens:

| Origem | Clubes |
|---|---:|
| Brasil e Argentina, direto | 12 |
| Vencedores da Primeira Fase | 16 |
| Vindos de fases preliminares da **Libertadores** | 4 |

- **8 grupos de 4**, em turno e returno — 6 jogos por clube.
- **Clubes do mesmo país não podem cair no mesmo grupo.**
- **1º de cada grupo** → direto às oitavas.
- **2º de cada grupo** → playoffs.

### Playoffs (repescagem)

- Jogos de **ida e volta**.
- Os **8 segundos colocados** da Sul-Americana enfrentam os **8 terceiros colocados da fase
  de grupos da Libertadores**.

### Mata-mata

- **Oitavas, quartas e semifinais** em ida e volta.
- **Final em jogo único**, em sede pré-definida pela CONMEBOL.

---

## 3. Critérios de desempate na fase de grupos

Na ordem oficial:

1. **Confronto direto** — pontos, saldo e gols marcados nos jogos entre os empatados
2. Saldo de gols geral
3. Gols marcados geral
4. Menos cartões vermelhos
5. Menos cartões amarelos
6. Sorteio

> **Atenção, isto é diferente do resto do jogo.** As competições existentes desempatam por
> saldo de gols direto. Aqui o **confronto direto vem primeiro**, e os critérios 4 e 5
> exigem contar cartões por clube na competição — algo que o jogo registra por partida, em
> `eventos_partida`, mas nunca agregou para desempate. É a parte do regulamento que dá mais
> trabalho, e a que menos aparece: só decide quando dois clubes empatam em tudo.

---

## 4. A aritmética fecha — e só com a Libertadores

As 44 vagas nacionais não chegam a 56. A diferença são justamente os clubes que **vêm da
Libertadores**, em dois momentos:

```
vagas nacionais                                        = 44
+ eliminados nas preliminares da Libertadores          =  4   (entram nos grupos)
+ terceiros colocados dos grupos da Libertadores       =  8   (entram nos playoffs)
                                              total    = 56  ✔ confere
```

E as fases encaixam:

```
grupos   = 12 (BRA + ARG) + 16 (Primeira Fase) + 4 (preliminares da Libertadores) = 32  ✔
playoffs = 8 segundos de grupo + 8 terceiros da Libertadores                      = 16  ✔
oitavas  = 8 primeiros de grupo + 8 vencedores dos playoffs                        = 16  ✔
```

Os 8 terceiros da Libertadores não são um detalhe: **sem eles os playoffs teriam 8 clubes
em vez de 16**, e as oitavas não fechariam. O `regras_libertadores.md` já previa essa
transferência do seu lado — os dois documentos descrevem a mesma ponte, de pontas opostas.

---

## 5. Estado do jogo: o que ajuda e o que falta

### A boa notícia: o seed agora tem as 10 associações

Quando o `regras_libertadores.md` foi escrito, só havia clubes de 3 países sul-americanos.
Isso mudou: a implementação da Libertadores trouxe os 7 que faltavam.

| País | Clubes no seed |
|---|---:|
| Brasil | 80 |
| Argentina | 60 |
| Uruguai | 30 |
| Bolívia, Chile, Colômbia, Equador, Paraguai, Peru, Venezuela | 6 cada |

Com 10 países, a trava **"clubes do mesmo país não podem cair no mesmo grupo" é
satisfazível** — um grupo de 4 encontra 4 países distintos sem esforço. Esta competição não
tem o impedimento matemático que travou a Champions e a Europa League.

### O que falta: clubes nos países pequenos

A conta que impede a Sul-Americana de rodar hoje não é de países, é de **clubes por país**:

```
Libertadores consome, de cada país pequeno         = 4  (2 nos grupos + 2 na Pré)
Sul-Americana precisa, do mesmo país               = 4
                                        necessário = 8
                                       disponível  = 6
```

Faltam **2 clubes em cada um dos 7 países pequenos — 14 no total**. Um clube não disputa as
duas competições na mesma temporada, então as vagas não podem se sobrepor.

O Uruguai não tem esse problema: 30 clubes cobrem as 4 da Libertadores e as 4 daqui com
folga. Brasil e Argentina também não.

### A ponte com a Libertadores ainda não existe

A Libertadores está implementada, mas **não registra os terceiros colocados de grupo** em
lugar nenhum — eles simplesmente não avançam. O `GameRepository` tem o ponto de extensão
documentado para o campeão vigente da Sul-Americana (a lista de acesso da Libertadores já
trata a vaga dele, inclusive a regra de queda), mas a transferência dos terceiros precisa
ser construída.

O mesmo vale para os 4 eliminados das preliminares da Libertadores, que hoje saem do
torneio sem destino.

---

## 6. O que a implementação vai precisar

1. **2 a 3 clubes a mais em cada um dos 7 países pequenos**, para as duas competições
   coexistirem. É o único pré-requisito duro.
2. **A Primeira Fase nacional**, que é um formato novo: eliminatória de partida única
   **entre clubes do mesmo país**, 4 por país virando 2. O `MotorCampeonato` já tem
   `gerarFaseJogoUnico` e `sortearPares`; o que muda é sortear **dentro** de cada país em
   vez de no conjunto todo.
3. **A fase de grupos com a trava de país**, diferente da Libertadores, cujo sorteio apenas
   *minimiza* compatriotas porque o regulamento dela não proíbe. Aqui a proibição é
   explícita, então o sorteio precisa recusar quando não há solução — o modelo é o
   `SorteioChampions`, que faz exatamente isso por retrocesso.
4. **Os playoffs**, que são a parte mais acoplada: dependem dos 8 terceiros da Libertadores,
   e portanto da fase de grupos dela ter terminado.
5. **Os critérios de desempate**, com confronto direto na frente e contagem de cartões —
   ver a ressalva da seção 3.
6. **A final em jogo único**, que sai de graça: `simularPartidaInterna` já liga campo neutro
   quando a fase é "Final".
7. **Hall da Fama na divisão 35**, já reservada a esta competição, e a pontuação nos dois
   rankings, que também já está implementada e testada — basta chamar.

---

## As faixas de rodada

Como na Libertadores, as três etapas dividem a numeração de rodadas dentro do
mesmo campeonato: a Primeira Fase ocupa a 1, os grupos as 11..16 e o mata-mata
da 21 em diante.

Essa faixa é **slot de armazenamento, não numeração de tela**. A fase de grupos
tem seis rodadas e a primeira delas é a primeira: `rodadaDosGruposVisivel`
traduz 11..16 para 1..6, e é por ela que o seletor de rodadas e o calendário
passam. A Primeira Fase e o mata-mata não precisam disso — as partidas deles têm
`fase`, e quem nomeia é a fase.
