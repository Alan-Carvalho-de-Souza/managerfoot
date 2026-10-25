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

As copas nacionais pontuam em **todas as fases**. Os pontos por vitória seguem o **nível da
liga do país** — uma vitória na Copa do Brasil vale os mesmos 4 pontos de uma vitória no
Brasileirão, inclusive na Primeira Fase.

| Competição no jogo | Fases que pontuam | Vitória |
|---|---|---:|
| Copa do Brasil | todas | 4 |
| Copa da Inglaterra | todas | 4 |
| Copa do Rei (Espanha) | todas | 4 |
| Coppa Italia | todas | 4 |
| Copa Argentina | todas | 3 |

Nas fases de ida e volta, **cada jogo conta separadamente** — uma eliminatória rende dois
resultados, não um.

> **Isto é um desvio consciente do enunciado**, que manda contar a copa só das oitavas em
> diante. O corte deixava o ranking cego para boa parte do calendário: o clube eliminado na
> Primeira Fase da Copa do Brasil não somava nada por aqueles jogos, e um clube de Série C
> ou D, que raramente passa das primeiras fases, não tinha como aparecer. A cobertura
> ganhou do literalismo aqui — e só aqui.

## 4. O que é ignorado

- **Supercopas e Troféu dos Campeões.** Jogo único entre campeões: exatamente o "torneio
  secundário" que o enunciado manda ignorar, como a Copa da Liga. Vale para a Supercopa
  Rei, as três europeias, a Supercopa da UEFA e o Troféu dos Campeões argentino.
- **Campeonatos estaduais**, excluídos pelo enunciado.
- **Amistosos**, que não são competição oficial.
- **Jogos de seleções**, que não são futebol de clubes.

> **Por que as copas foram abertas e estas não.** Abrir as fases de copa amplia a cobertura
> de competições que o ranking já contava — é mais do mesmo calendário. Incluir supercopa,
> Troféu dos Campeões e estadual seria incluir **torneios que o enunciado exclui por
> natureza**, não por fase. Um teste fixa a lista inteira de tipos que não pontuam, para
> que nenhuma competição nova entre ou saia sem aviso.

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
| Copa do Brasil (todas as fases) | 6 V, 3 E, 1 D | 6×4 + 3×2 | 30 |
| | | **Total** | **134** |

Clube inglês, no mesmo período:

| Competição | Campanha | Cálculo | Pontos |
|---|---|---|---:|
| Premier League | 24 V, 7 E, 7 D | 24×4 + 7×2 | 110 |
| Copa da Inglaterra (todas as fases) | 4 V, 1 E | 4×4 + 1×2 | 18 |
| | | **Total** | **128** |

## Por que os brasileiros dominam o ranking real — e por que não vão dominar aqui

No ranking real, clubes brasileiros ocupam o Top 10 com frequência, por dois motivos
matemáticos: o Brasileirão tem peso máximo, e o calendário sul-americano tem muito mais
jogos oficiais do que o europeu, porque o clube soma Libertadores e Brasileirão ao longo
dos 12 meses.

**No jogo esse fator agora existe**, desde que a Libertadores, a Champions e a Europa
League foram implementadas: quem avança nelas soma jogos de peso 12 e 14 por cima da liga.
Entre dois clubes que ficam só no âmbito nacional, porém, a distância é pequena:
Brasileirão, Premier, LaLiga e Serie A têm 20 clubes e 38 rodadas cada, todas de Nível 4, e
o exemplo acima dá 134 contra 128.

> **Ponto de calibragem a observar:** a Argentina tem 30 clubes na primeira divisão e
> disputa Apertura, Clausura e mata-mata na mesma temporada. Mesmo valendo 3 por vitória
> em vez de 4, o volume de jogos pode levar clubes argentinos à frente dos de Nível 4.
> Vale conferir com o jogo rodando, antes de tratar os níveis como definitivos.

---

## Como está aplicado no jogo

Implementado e funcionando, com as três decisões abaixo tomadas.

- Entrada: aba **Colocação** da tela Ranking de Clubes, recorte **Mundo**.
- Regras em `RankingMundial.kt`, cobertas por testes unitários.
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
  em Kotlin, então o banco não precisa conhecer níveis de liga.

### Decisões tomadas

| Questão | Decisão |
|---|---|
| Divisões inferiores | **Cascata**: o nível cai um degrau por divisão abaixo da elite do país, com piso no nível 1. No Brasil, Série A 4 / B 3 / C 2 / D 1; na Inglaterra, Premier 4 / Championship 3; na Argentina, Primera 3 / segunda 2. |
| Fases de copa | **Todas pontuam**, com o nível do país. Revisto: antes só valiam das oitavas em diante. |
| Supercopas e Troféu dos Campeões | **Não pontuam** — torneio secundário, pelo enunciado. |
| Estaduais | **Não pontuam**, pelo enunciado. |
| Pênaltis | Vale o resultado dos 90 minutos ou da prorrogação; a disputa conta como empate para os dois clubes. |

## O que ainda falta

1. **O Mundial de Clubes da FIFA**, a linha de peso 21 da tabela 1. O peso já está
   implementado e testado, mas fica dormente: `TipoCampeonato.MUNDIAL_CLUBES` existe no
   enum e nenhum campeonato é criado com ele. No dia em que existir, pontua sozinho.

   A Libertadores, a Champions e a Europa League **já pontuam**: são criadas como
   `CONTINENTAL` e o nome de cada uma é o que seleciona o peso (14, 14 e 12).
2. **Nível de liga variável.** Hoje é fixo no código; nas regras reais ele muda a cada ano
   conforme o desempenho internacional dos clubes do país. Agora que as competições
   continentais existem, já há de onde medir esse desempenho.
