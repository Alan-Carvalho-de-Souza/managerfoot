# Ranking Nacional de Clubes (CBF) — Regras de Pontuação

> **Escopo:** conjunto de regras do ranking por colocação dos clubes do **Brasil**.
> É independente do [ranking_conmebol.md](ranking_conmebol.md), que é o conjunto
> continental da América do Sul — os dois usam ciclos, pesos e competições diferentes.

## Objetivo

Classificar os clubes pelo rendimento recente, para definir vagas em competições
nacionais (Copa do Brasil e Série D) e a divisão de potes nos sorteios.

## Ciclo e pesos

O ranking considera **exclusivamente os últimos 5 anos**. Pontuação de temporada anterior
a essa janela é descartada por completo — não decai, simplesmente sai da conta.

Os anos recentes pesam mais, por multiplicadores inteiros:

| Temporada avaliada | Peso |
|---|---:|
| Ano atual / mais recente | × 5 |
| 1º ano anterior | × 4 |
| 2º ano anterior | × 3 |
| 3º ano anterior | × 2 |
| 4º ano anterior | × 1 |

```
Pontuação Total = Σ (pontos da temporada × peso do ano)
                  para cada um dos 5 anos da janela
```

Os pontos de uma temporada são a soma do que o clube fez **no Campeonato Brasileiro**
(na série em que jogou) **e na Copa do Brasil**. Nada mais pontua: torneios
internacionais (Libertadores, Sul-Americana) e campeonatos estaduais valem zero aqui.

Como um clube disputa uma única série por ano, ele recebe pontos de apenas uma das
colunas de liga na tabela abaixo.

---

## 1. Campeonato Brasileiro — pontos por posição final

O campeão de cada série vale a pontuação-base da competição, e a pontuação cai
proporcionalmente conforme a posição final:

| Competição | Base (campeão) |
|---|---:|
| Série A | 800 |
| Série B | 400 |
| Série C | 200 |
| Série D | 100 |

A escala percentual é a mesma nas quatro séries: 100% para o 1º colocado, 80% para o 2º,
75% para o 3º e queda linear de 3,294 pontos percentuais por posição até o 20º, que fica
com 19%.

| Pos. | % da base | Série A | Série B | Série C | Série D |
|---:|---:|---:|---:|---:|---:|
| 1º | 100,0% | 800 | 400 | 200 | 100 |
| 2º | 80,0% | 640 | 320 | 160 | 80 |
| 3º | 75,0% | 600 | 300 | 150 | 75 |
| 4º | 71,7% | 574 | 287 | 143 | 72 |
| 5º | 68,4% | 547 | 274 | 137 | 68 |
| 6º | 65,1% | 521 | 260 | 130 | 65 |
| 7º | 61,8% | 495 | 247 | 124 | 62 |
| 8º | 58,5% | 468 | 234 | 117 | 59 |
| 9º | 55,2% | 442 | 221 | 110 | 55 |
| 10º | 51,9% | 416 | 208 | 104 | 52 |
| 11º | 48,6% | 389 | 195 | 97 | 49 |
| 12º | 45,4% | 363 | 181 | 91 | 45 |
| 13º | 42,1% | 336 | 168 | 84 | 42 |
| 14º | 38,8% | 310 | 155 | 78 | 39 |
| 15º | 35,5% | 284 | 142 | 71 | 35 |
| 16º | 32,2% | 257 | 129 | 64 | 32 |
| 17º | 28,9% | 231 | 116 | 58 | 29 |
| 18º | 25,6% | 205 | 102 | 51 | 26 |
| 19º | 22,3% | 178 | 89 | 45 | 22 |
| 20º | 19,0% | 152 | 76 | 38 | 19 |

Fórmula da escala, para gerar a tabela em código:

```
percentual(1) = 100%
percentual(2) =  80%
percentual(p) = 75% - 3,294 × (p - 3)     para p de 3 até 20
pontos(p)     = arredondar(base × percentual(p))
```

> **Sobre a Série D:** nas regras da CBF a Série D é pontuada por fase atingida, porque é
> um torneio de grupos e mata-mata. Neste jogo a Série D é uma liga de 20 clubes em pontos
> corridos, então ela usa a escala de posição acima, sobre a base 100. Se a Série D virar
> mata-mata, trocar para uma tabela de fases como a da Copa do Brasil.

---

## 2. Copa do Brasil — pontos por fase atingida

Aqui a pontuação é computada **estritamente pela fase máxima alcançada**: todos os clubes
eliminados na mesma fase recebem exatamente a mesma pontuação, sem desempate por
campanha, saldo ou número de vitórias.

Base do campeão: **600 pontos**. As fases abaixo são as da Copa do Brasil deste jogo
(64 clubes, 6 fases).

| Fase máxima atingida | % da base | Pontos |
|---|---:|---:|
| Campeão | 100% | 600 |
| Vice-campeão | 80% | 480 |
| Eliminado na Semifinal | 70% | 420 |
| Eliminado nas Quartas de final | 55% | 330 |
| Eliminado nas Oitavas de final | 40% | 240 |
| Eliminado na Segunda Fase | 25% | 150 |
| Eliminado na Primeira Fase | 15% | 90 |

> As porcentagens de campeão e vice vêm das regras; as das fases intermediárias são uma
> escala proposta, coerente com a base de 600 e com a convenção de que a eliminação na
> primeira fase vale uma pontuação mínima padrão. Ajustar se houver valores oficiais.

---

## Exemplo de cálculo

Clube X, nos cinco anos da janela:

| Ano | Brasileirão | Copa do Brasil | Pontos do ano | Peso | Subtotal |
|---|---|---|---:|---:|---:|
| Atual | Série A, 3º | Semifinal | 600 + 420 = 1.020 | × 5 | 5.100 |
| 1º anterior | Série A, 8º | Oitavas | 468 + 240 = 708 | × 4 | 2.832 |
| 2º anterior | Série A, 15º | Primeira Fase | 284 + 90 = 374 | × 3 | 1.122 |
| 3º anterior | Série B, 1º | Segunda Fase | 400 + 150 = 550 | × 2 | 1.100 |
| 4º anterior | Série B, 6º | Primeira Fase | 260 + 90 = 350 | × 1 | 350 |
| | | | | **Total** | **10.504** |

O teto teórico é 21.000 pontos: campeão da Série A e da Copa do Brasil nos cinco anos
seguidos — (800 + 600) × (5 + 4 + 3 + 2 + 1).
