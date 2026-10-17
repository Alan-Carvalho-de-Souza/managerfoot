# Ranking CONMEBOL — Regras de Pontuação

> **Escopo:** este é o conjunto de regras do ranking por colocação da **América do Sul**.
> O ranking por colocação é definido por continente; Europa e os demais continentes terão
> seus próprios conjuntos de regras, que não seguem nada deste documento.

O ranking de clubes da CONMEBOL soma três blocos:

```
Pontuação Total = Desempenho (últimos 10 anos, com depreciação)
                + Coeficiente Histórico (1960–2014, sem depreciação)
                + Bônus por Campeonatos Locais (últimos 10 anos, com depreciação)
```

> Torneios menores, como a **Recopa Sul-Americana** e a antiga **Copa Suruga**, **não** distribuem pontos para este ranking.

---

## 1. Pontuação por Desempenho (últimos 10 anos)

Pontos **fixos** pela fase final atingida + pontos **dinâmicos** por vitória ou empate.

### Copa Libertadores da América

| Resultado | Pontos |
|---|---:|
| Campeão | 1.000 |
| Vice-campeão | 400 |
| Eliminado na Semifinal | 240 |
| Eliminado nas Quartas de final | 160 |
| Eliminado nas Oitavas de final | 80 |
| Eliminado na Fase de Grupos | 20 |
| Eliminado na Fase 3 (Preliminar) | 12 |
| Eliminado na Fase 2 (Preliminar) | 8 |
| Eliminado na Fase 1 (Preliminar) | 4 |
| **Vitória** (da Fase de Grupos em diante) | 80 por partida |
| **Empate** (da Fase de Grupos em diante) | 40 por partida |

### Copa Sul-Americana

| Resultado | Pontos |
|---|---:|
| Campeão | 300 |
| Vice-campeão | 120 |
| Eliminado na Semifinal | 72 |
| Eliminado nas Quartas de final | 48 |
| Eliminado nas Oitavas de final | 24 |
| Eliminado na Fase de Grupos | 6 |
| Eliminado na Fase Preliminar / Fase 1 | 4 |
| **Vitória** (da Fase de Grupos em diante) | 24 por partida |
| **Empate** (da Fase de Grupos em diante) | 12 por partida |

### Regra de depreciação anual

O ano mais recente vale 100%; cada ano anterior perde 10%.

| Ano | Peso |
|---|---:|
| Ano atual (1º) | 100% |
| 2º | 90% |
| 3º | 80% |
| 4º | 70% |
| 5º | 60% |
| 6º | 50% |
| 7º | 40% |
| 8º | 30% |
| 9º | 20% |
| 10º | 10% |

Fórmula: `peso(n) = 1.0 − 0.1 × (n − 1)`, onde `n` = 1 para o ano mais recente.

---

## 2. Coeficiente Histórico (1960 a 2014)

Premia a tradição do clube antes da última década. Valores **fixos, sem depreciação**.

### Copa Libertadores

| Resultado | Pontos |
|---|---:|
| Campeão | 100 por título |
| Vice-campeão | 40 |
| Semifinalista | 24 |
| Vitória (da Fase de Grupos em diante) | 8 |
| Empate (da Fase de Grupos em diante) | 4 ⚠️ |

> ⚠️ O texto de origem indica **40** pontos por empate, o que parece erro de digitação: seria igual ao vice-campeonato e 5× a vitória. Pela proporção com a Sul-Americana (que vale 30% da Libertadores: 1,2 → 4), o valor coerente é **4**.

### Copa Sul-Americana

| Resultado | Pontos |
|---|---:|
| Campeão | 30 por título |
| Vice-campeão | 12 |
| Semifinalista | 7,2 |
| Vitória (da Fase de Grupos em diante) | 2,4 |
| Empate (da Fase de Grupos em diante) | 1,2 |

---

## 3. Bônus por Campeonatos Locais

- O campeão de cada liga nacional de uma associação membro recebe **50 pontos**.
- Se houver mais de um campeão no ano (ex.: **Apertura** e **Clausura**), os 50 pontos são divididos igualmente (**25** para cada).
- Esses pontos sofrem a mesma **depreciação de 10% ao ano** ao longo da última década.

---

## Como está aplicado no jogo

O Ranking CONMEBOL é um **segundo ranking**, separado do Ranking Geral. O Geral ficou
sendo puramente de desempenho em campo (3 pontos por vitória, 1 por empate) — todos os
bônus de título e de vice-campeonato foram retirados dele. Conquistas pontuam só aqui.

- Entrada: aba **Colocação** na tela Ranking de Clubes (a aba **Geral** é o ranking de desempenho).
- Os recortes Mundo / Continente / País valem para os dois rankings; as opções de chip
  saem do ranking ativo, então em Colocação aparecem só a América do Sul e seus países.
- O escopo é o continente, não uma lista de países: `Continentes.ehAmericaDoSul()` aceita
  as dez associações da CONMEBOL, então um clube chileno ou colombiano adicionado ao seed
  entra no ranking sem nenhuma outra alteração. Hoje o seed tem Brasil, Argentina e Uruguai.
- Cada conquista é um lançamento com o ano em que aconteceu (tabela `ranking_conmebol`),
  e a depreciação é calculada na leitura, a partir do ano corrente do save.

| Bloco | Situação |
|---|---|
| 1. Desempenho continental | Tabelas e motor prontos (`RankingConmebol.kt`), **dormentes** — Libertadores e Sul-Americana ainda não existem no jogo. Passam a pontuar sozinhas quando forem implementadas. |
| 2. Coeficiente histórico | Semeado a partir da reputação do clube, já que o jogo não tem passado anterior a 2026. Fixo, sem depreciação. Roda ao iniciar um jogo novo e também ao continuar um save antigo — é idempotente, age só na primeira vez. |
| 3. Campeonatos locais | Ativo: Brasileirão Série A e Campeonato Uruguaio dão 50 ao campeão; Apertura e Clausura argentinos dão 25 cada. Apenas 1ª divisão. |

## Exemplo de cálculo

Clube X, temporada mais recente (peso 100%):

- Libertadores: vice-campeão (400) + 7 vitórias (7 × 80 = 560) + 3 empates (3 × 40 = 120) = **1.080**
- Campeão nacional (liga com campeão único): **50**

Mesmo desempenho há 4 anos (peso 70%): `(1.080 + 50) × 0,7 = 791`.
