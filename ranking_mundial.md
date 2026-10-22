# Ranking Mundial de Clubes (IFFHS) — Regras de Pontuação

> **Escopo:** conjunto de regras do ranking por colocação **mundial** — o recorte Mundo.
> É independente dos outros três: [ranking_conmebol.md](ranking_conmebol.md) (continente
> sul-americano), [ranking_brasileiro.md](ranking_brasileiro.md) (país Brasil) e
> [ranking_europa.md](ranking_europa.md) (continente Europa).

Este é o conjunto mais simples dos quatro, e o único que funciona por **soma de pontos por
partida**, sem nenhum bônus por colocação ou fase alcançada. Nada de Elo: a IFFHS não olha
a força do adversário, só o peso do torneio em que o jogo foi disputado.

## A diferença central: a janela

| Conjunto | Janela | Recência | O que pontua |
|---|---|---|---|
| **IFFHS (este)** | **últimos 12 meses** | nenhuma | partidas, por peso do torneio |
| UEFA / Europa | 5 temporadas | nenhuma | partidas + posição + fase |
| CBF / Brasil | 5 temporadas | pesos 5/4/3/2/1 | posição final e fase máxima |
| CONMEBOL / Am. do Sul | 10 anos | −10% ao ano | fase + partidas + liga + histórico |

A janela é **móvel por data**, não por temporada: conta os últimos 12 meses de competições
oficiais, então um jogo sai da conta no dia em que completa um ano.

## A regra universal: empate vale metade

Em **todas** as linhas de todas as tabelas abaixo, o empate vale exatamente metade da
vitória. Então basta guardar o valor da vitória por competição:

```
pontos = (vitórias × peso) + (empates × peso / 2)
```

**A derrota nunca soma nem subtrai pontos.** Não existe punição no modelo — só acumulação.

---

## 1. Competições internacionais

| Competição | Vitória | Empate |
|---|---:|---:|
| Mundial de Clubes da FIFA (fase final) | 21 | 10,5 |
| UEFA Champions League · Copa Libertadores | 14 | 7 |
| UEFA Europa League · Copa Sul-Americana | 12 | 6 |
| CAF Champions League (África) | 9 | 4,5 |
| AFC Champions League (Ásia) | 9 | 4,5 |
| Copa da CONCACAF | 9 | 4,5 |

---

## 2. Ligas nacionais — os quatro níveis

A IFFHS classifica todas as ligas do mundo em quatro níveis de força. O nível **muda
anualmente**, conforme o desempenho internacional dos clubes daquele país.

| Nível | Vitória | Empate |
|---|---:|---:|
| Nível 4 (ligas mais fortes) | 4 | 2 |
| Nível 3 | 3 | 1,5 |
| Nível 2 | 2 | 1 |
| Nível 1 | 1 | 0,5 |

### Níveis das ligas deste jogo

| Liga | Nível | Vitória | Origem |
|---|---:|---:|---|
| Brasileirão Série A | 4 | 4 | citada no enunciado |
| Premier League (Inglaterra) | 4 | 4 | citada no enunciado |
| LaLiga (Espanha) | 4 | 4 | citada no enunciado |
| Serie A (Itália) | 4 | 4 | citada no enunciado |
| Primera División Argentina | 3 | 3 | citada no enunciado |
| Campeonato Uruguaio | 2 | 2 | **proposto** — não citado |

As quatro ligas de Nível 4 nomeadas no enunciado são exatamente as quatro que existem no
jogo, o que torna esse mapeamento direto. A Argentina também vem citada, como Nível 3. Só
o Uruguai precisou de arbítrio.

> **Decisão pendente — divisões inferiores.** O enunciado classifica *ligas*, e a IFFHS
> considera apenas as primeiras divisões. Lido ao pé da letra, um clube da Série B, C ou D
> ou da Championship não pontua nada, e o ranking mundial passa a existir só para a elite.
> Como neste jogo o usuário pode dirigir um clube de divisão inferior, a alternativa é
> fazer o nível cair junto com a divisão:
>
> | Divisão | Nível proposto | Vitória |
> |---|---:|---:|
> | Série B / Championship | 3 | 3 |
> | Série C | 2 | 2 |
> | Série D | 1 | 1 |
> | Segunda Divisão ARG / URU | 1 | 1 |
>
> Recomendo essa cascata, para a tela não ficar vazia para quem está subindo de divisão.
> Confirme qual das duas leituras você quer.

---

## 3. Copas nacionais

As copas nacionais entram na conta, mas **apenas das oitavas de final em diante**. Os
pontos por vitória seguem o **nível da liga do país** — uma vitória nas oitavas da Copa do
Brasil vale os mesmos 4 pontos de uma vitória no Brasileirão.

| Copa no jogo | Fases que existem | Pontuam | Vitória |
|---|---|---|---:|
| Copa do Brasil | Primeira, Segunda, Oitavas, Quartas, Semi, Final | das Oitavas | 4 |
| Copa da Inglaterra | Primeira, Oitavas, Quartas, Semi, Final | das Oitavas | 4 |
| Copa do Rei (Espanha) | Oitavas, Quartas, Semi, Final | todas | 4 |
| Coppa Italia | Oitavas, Quartas, Semi, Final | todas | 4 |
| Copa Argentina | Primeira, Oitavas, Quartas, Semi, Final | das Oitavas | 3 |

As copas da Espanha e da Itália já começam nas oitavas, então pontuam integralmente. Nas
fases de ida e volta, **cada jogo conta separadamente** — uma eliminatória rende dois
resultados, não um.

## 4. O que é ignorado

- **Torneios secundários**, como a Copa da Liga, são completamente ignorados.
- **Fases de copa anteriores às oitavas** não pontuam, nem com valor reduzido.
- **Campeonatos estaduais** não pontuam.
- **Amistosos** não são competição oficial e não pontuam.

> **Decisão pendente — Supercopas e Troféu dos Campeões.** O jogo tem Supercopa Rei,
> Supercopa da Inglaterra, da Espanha, da Itália e o Troféu dos Campeões argentino, todos
> de jogo único entre dois campeões. São exatamente o tipo de "torneio secundário" que o
> enunciado manda ignorar, então a proposta é **não pontuarem**. Confirme.

> **Decisão pendente — pênaltis.** O enunciado não trata de decisão por pênaltis. A
> proposta é seguir o mesmo critério do coeficiente da UEFA: vale o resultado dos 90
> minutos (ou da prorrogação), e a disputa de pênaltis conta como **empate para os dois
> clubes**, independente de quem avançou.

---

## Exemplo de cálculo

Clube brasileiro, nos últimos 12 meses:

| Competição | Campanha | Cálculo | Pontos |
|---|---|---|---:|
| Brasileirão Série A | 22 V, 8 E, 8 D | 22×4 + 8×2 | 104 |
| Copa do Brasil (das oitavas) | 4 V, 3 E, 1 D | 4×4 + 3×2 | 22 |
| | | **Total** | **126** |

Clube inglês, no mesmo período:

| Competição | Campanha | Cálculo | Pontos |
|---|---|---|---:|
| Premier League | 24 V, 7 E, 7 D | 24×4 + 7×2 | 110 |
| Copa da Inglaterra (das oitavas) | 3 V, 1 E | 3×4 + 1×2 | 14 |
| | | **Total** | **124** |

## Por que os brasileiros dominam o ranking real — e por que não vão dominar aqui

No ranking real, clubes brasileiros ocupam o Top 10 com frequência, por dois motivos
matemáticos: o Brasileirão tem peso máximo, e o calendário sul-americano tem muito mais
jogos oficiais do que o europeu, porque o clube soma Libertadores e Brasileirão ao longo
dos 12 meses.

**No jogo, esse segundo fator não existe.** Não há Libertadores, e Brasileirão, Premier,
LaLiga e Serie A têm 20 clubes e 38 rodadas cada, todas de Nível 4. O exemplo acima mostra
o resultado: 126 contra 124, praticamente empatados. A vantagem brasileira só aparecerá
quando as competições continentais forem implementadas.

> **Ponto de calibragem a observar:** a Argentina tem 30 clubes na primeira divisão e
> disputa Apertura, Clausura e mata-mata na mesma temporada. Mesmo valendo 3 por vitória
> em vez de 4, o volume de jogos pode levar clubes argentinos à frente dos de Nível 4.
> Vale conferir com o jogo rodando, antes de tratar os níveis como definitivos.

---

## Como está aplicado no jogo

Implementado e funcionando, com as três decisões abaixo tomadas.

- Entrada: aba **Colocação** da tela Ranking de Clubes, recorte **Mundo**.
- Regras em `RankingMundial.kt`, cobertas por 22 testes unitários.
- **Sem tabela de lançamentos** — o único dos quatro conjuntos assim. A pontuação é
  derivada das partidas já gravadas, na leitura: `partidas` guarda toda partida oficial de
  todas as temporadas (só amistosos são apagados, e eles não pontuam) com resultado, fase e
  `ordemGlobal`, e `campeonatos` dá o tipo e o país. Isso evitou migração de banco, evitou
  escrita por partida dentro do laço de simulação, e faz o ranking recalcular sozinho se os
  níveis de liga mudarem — como as regras reais mandam que mudem.
- **A janela de 12 meses dispensou data.** Como `ordemGlobal` é monotônico dentro da
  temporada, o "agora" é o maior `ordemGlobal` já jogado no ano corrente, e a janela vira
  `(ano = atual E og ≤ agora) OU (ano = atual−1 E og > agora)`. Isso é exatamente um ciclo
  de 12 meses no calendário do próprio jogo, sem nenhuma conversão de mês.
- O SQL só agrega vitórias e empates por clube, competição e fase; os pesos são aplicados
  em Kotlin, então o banco não precisa conhecer níveis de liga nem o corte das oitavas.

### Decisões tomadas

| Questão | Decisão |
|---|---|
| Divisões inferiores | **Cascata**: o nível cai um degrau por divisão abaixo da elite do país, com piso no nível 1. No Brasil, Série A 4 / B 3 / C 2 / D 1; na Inglaterra, Premier 4 / Championship 3; na Argentina, Primera 3 / segunda 2. |
| Supercopas e Troféu dos Campeões | **Não pontuam** — jogo único entre campeões, o "torneio secundário" que o enunciado manda ignorar. |
| Pênaltis | Vale o resultado dos 90 minutos ou da prorrogação; a disputa conta como empate para os dois clubes. |

## O que ainda falta

1. **As competições internacionais**, que são as linhas de maior peso da tabela 1. Os pesos
   já estão implementados e testados, mas ficam dormentes: `TipoCampeonato.CONTINENTAL` e
   `MUNDIAL_CLUBES` existem no enum e nenhum campeonato é criado com eles. No dia em que
   uma Libertadores existir, ela pontua sozinha, sem alteração nenhuma aqui.
2. **Nível de liga variável.** Hoje é fixo no código; nas regras reais ele muda a cada ano
   conforme o desempenho internacional dos clubes do país — o que só faz sentido calcular
   quando o item 1 existir.
