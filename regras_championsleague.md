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

### Falta

1. **Os clubes do pool** — ~30 clubes das 8 associações que faltam, no modelo de divisão
   90/91/92. Sem isso nada roda, porque o sorteio recusa 3 países.
2. **Um formato novo de Fase de Liga.** `FormatoCampeonato` tem `PONTOS_CORRIDOS`,
   `GRUPOS_E_MATA_MATA` e `MATA_MATA_SIMPLES`. A liga única de 36 com 8 jogos por clube não
   é nenhum dos três: é uma tabela única em que os clubes têm adversários diferentes.
3. **Criar a competição** com `TipoCampeonato.CONTINENTAL`, que existe no enum e nunca foi
   usado, distribuir as vagas pela lista de acesso e persistir as 144 partidas com
   `ordemGlobal` encaixado entre as rodadas das ligas nacionais.
4. **Classificação de 1 a 36** numa tabela só, que alimenta o bônus de posição do
   coeficiente da UEFA (12 pontos para o 1º, caindo 0,25 por posição).
5. **O play-off e o mata-mata** das oitavas em diante, que dão o bônus de avanço de 1,5 por
   fase. O `MotorCampeonato` já tem `gerarFaseIdaVolta`, `gerarFaseJogoUnico`,
   `sortearPares` e `determinarVencedorTie` reaproveitáveis.
6. **A fiação com os rankings**, que é automática: `registrarCampanhaEuropeia` já existe e
   o peso 14 da IFFHS já está implementado. Os dois conjuntos dormentes acordam sozinhos.
7. **A tela** da Fase de Liga e do mata-mata.
