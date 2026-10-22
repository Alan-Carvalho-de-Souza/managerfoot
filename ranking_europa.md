# Coeficiente de Clubes da UEFA — Regras de Pontuação

> **Escopo:** conjunto de regras do ranking por colocação dos clubes da **Europa**.
> É independente de [ranking_conmebol.md](ranking_conmebol.md) (continente sul-americano)
> e de [ranking_brasileiro.md](ranking_brasileiro.md) (país Brasil) — os três usam janelas,
> pesos e competições diferentes.

> ⚠️ **Este conjunto está inteiramente dormente no jogo.** Ele pontua **somente**
> competições da UEFA (Champions, Europa e Conference League), e nenhuma delas existe
> hoje no ManagerFoot — não há nem a enum, nem calendário, nem participantes. Diferente
> do RNC brasileiro, que pontua liga e copa nacionais já implementadas, aqui não há
> nenhuma fonte de pontos: o ranking europeu nasce vazio e continua vazio até que essas
> competições sejam criadas. Ligas nacionais (Premier, La Liga, Serie A) e copas
> nacionais **não** pontuam neste ranking.

## Os cinco pilares

```
Coeficiente = Σ (pontos por partida + bônus de posição na Fase de Liga
                 + bônus por avanço no mata-mata)
              nas últimas 5 temporadas completas da UEFA

e depois:     Coeficiente = máximo(Coeficiente, piso de 20% do país)
```

---

## 1. Janela móvel de 5 temporadas

Soma o desempenho das **últimas 5 temporadas completas** da UEFA. A cada nova temporada,
os pontos da mais antiga expiram e saem da conta.

**Sem pesos por recência:** as cinco temporadas valem igual. Essa é a diferença central
em relação aos outros dois conjuntos já implementados no jogo:

| Conjunto | Janela | Recência |
|---|---|---|
| UEFA (este) | 5 temporadas | nenhuma — todas valem 100% |
| CBF / Brasil | 5 temporadas | pesos inteiros 5 / 4 / 3 / 2 / 1 |
| CONMEBOL / América do Sul | 10 anos | depreciação de 10% ao ano |

---

## 2. Pontos por partida (da Fase de Liga em diante)

| Resultado | Pontos |
|---|---:|
| Vitória | 2 |
| Empate | 1 |
| Derrota | 0 |

- A **prorrogação conta** para o resultado: quem vence na prorrogação leva os 2 pontos.
- A **disputa de pênaltis não conta**: para efeito de coeficiente o jogo é um empate, e
  ambos os clubes recebem 1 ponto, independente de quem avançou.
- Vale apenas da Fase de Liga em diante. Partidas de qualificação não dão pontos por
  jogo (ver a seção de exclusão).

---

## 3. Bônus por posição na Fase de Liga

A Fase de Liga é a liga única de 36 clubes que substituiu a fase de grupos. O bônus cai
0,25 por posição nas três competições, partindo de um topo diferente em cada uma.

```
bônus(p) = topo - 0,25 × (p - 1)
```

| Competição | Topo (1º) | 24º | Posições 25º–36º | Garantia de participação |
|---|---:|---:|---:|---:|
| Champions League | 12 | 6,25 | **6** (fixo) | — |
| Europa League | 6 | 0,25 | ver nota | 3 |
| Conference League | 4 | 0,125 | ver nota | 2,5 |

### Champions League — tabela completa

| Pos. | Bônus | Pos. | Bônus | Pos. | Bônus |
|---:|---:|---:|---:|---:|---:|
| 1º | 12,00 | 9º | 10,00 | 17º | 8,00 |
| 2º | 11,75 | 10º | 9,75 | 18º | 7,75 |
| 3º | 11,50 | 11º | 9,50 | 19º | 7,50 |
| 4º | 11,25 | 12º | 9,25 | 20º | 7,25 |
| 5º | 11,00 | 13º | 9,00 | 21º | 7,00 |
| 6º | 10,75 | 14º | 8,75 | 22º | 6,75 |
| 7º | 10,50 | 15º | 8,50 | 23º | 6,50 |
| 8º | 10,25 | 16º | 8,25 | 24º | 6,25 |
| | | | | 25º–36º | 6,00 |

A escada fecha perfeitamente: 12 − 0,25 × 23 = 6,25 no 24º, e o piso de 6 do 25º ao 36º
é o passo seguinte da mesma curva.

### Europa League — tabela completa

| Pos. | Bônus | Pos. | Bônus | Pos. | Bônus |
|---:|---:|---:|---:|---:|---:|
| 1º | 6,00 | 9º | 4,00 | 17º | 2,00 |
| 2º | 5,75 | 10º | 3,75 | 18º | 1,75 |
| 3º | 5,50 | 11º | 3,50 | 19º | 1,50 |
| 4º | 5,25 | 12º | 3,25 | 20º | 1,25 |
| 5º | 5,00 | 13º | 3,00 | 21º | 1,00 |
| 6º | 4,75 | 14º | 2,75 | 22º | 0,75 |
| 7º | 4,50 | 15º | 2,50 | 23º | 0,50 |
| 8º | 4,25 | 16º | 2,25 | 24º | 0,25 |

### Conference League — tabela completa

As âncoras dadas (4 no 1º, 0,125 no 24º) **não** produzem um passo redondo: o passo
linear exato é 3,875 ÷ 23 ≈ 0,1685, diferente do 0,25 das outras duas.

| Pos. | Bônus | Pos. | Bônus | Pos. | Bônus |
|---:|---:|---:|---:|---:|---:|
| 1º | 4,000 | 9º | 2,652 | 17º | 1,304 |
| 2º | 3,832 | 10º | 2,484 | 18º | 1,136 |
| 3º | 3,663 | 11º | 2,315 | 19º | 0,967 |
| 4º | 3,495 | 12º | 2,147 | 20º | 0,799 |
| 5º | 3,326 | 13º | 1,978 | 21º | 0,630 |
| 6º | 3,158 | 14º | 1,810 | 22º | 0,462 |
| 7º | 2,989 | 15º | 1,641 | 23º | 0,293 |
| 8º | 2,821 | 16º | 1,473 | 24º | 0,125 |

> **Decisão pendente.** Se preferir números redondos, use o mesmo passo de 0,125 a partir
> de 4, o que dá 1,125 no 24º em vez de 0,125 — provável leitura pretendida, já que 0,125
> é exatamente metade do passo de 0,25 da Europa League. Mantive as âncoras do enunciado.

> **Decisão pendente.** O enunciado diz o que o 25º–36º recebe só na Champions (6 pontos).
> Para Europa e Conference, a sugestão é repetir o valor do 24º (0,25 e 0,125), com a
> garantia de participação (3 e 2,5) agindo como piso do total da Fase de Liga — caso
> contrário a escada abaixo do 13º lugar da Europa League não teria efeito nenhum, porque
> a garantia de 3 pontos já cobriria todas essas posições.

---

## 4. Bônus por avanço no mata-mata

Pontos fixos **acumulativos**, a cada fase alcançada **a partir das oitavas de final**:

| Competição | Por fase alcançada |
|---|---:|
| Champions League | +1,5 |
| Europa League | +1,0 |
| Conference League | +0,5 |

Acumulado por fase mais distante alcançada:

| Fase alcançada | Champions | Europa | Conference |
|---|---:|---:|---:|
| Oitavas de final | 1,5 | 1,0 | 0,5 |
| Quartas de final | 3,0 | 2,0 | 1,0 |
| Semifinal | 4,5 | 3,0 | 1,5 |
| Final | 6,0 | 4,0 | 2,0 |

- O **play-off** entre a Fase de Liga e as oitavas **não** dá bônus de avanço, porque a
  contagem começa nas oitavas. As partidas dele rendem pontos por jogo normalmente, por
  serem posteriores à Fase de Liga.
- O **campeão não ganha bônus extra** além do da final; ele lucra apenas os 2 pontos da
  vitória na decisão.

---

## 5. Piso de proteção de 20% (fator país)

Rede de segurança para clubes tradicionais que passam temporadas sem se classificar: o
coeficiente individual nunca fica abaixo de **20% do coeficiente do país** no mesmo
período de 5 temporadas.

```
coeficiente final = máximo(pontos somados, 0,20 × coeficiente do país)
```

> **Atenção à calibragem.** O enunciado diz "20% do coeficiente **total** do país". Se
> "total" for a soma dos pontos de todos os clubes daquele país, o piso fica enorme — com
> 20 clubes somando, 20% da soma pode superar o que qualquer clube individual conquista,
> achatando o ranking e dando a todos os clubes do país a mesma pontuação. A UEFA, na
> prática, usa o **coeficiente da associação**, que é a soma dividida pelo número de
> clubes participantes, ou seja uma **média por clube** — e 20% de uma média é um piso
> modesto, que é o efeito pretendido. Recomendo a média.

> **Atenção ao escopo.** Aplicado cru, o piso daria pontos a clubes que nunca disputariam
> a Europa — no seed atual, as 24 equipes da Championship entrariam no ranking sem jogar
> nada. Recomendo aplicar o piso só a clubes da 1ª divisão, ou só a quem participou de
> alguma competição da UEFA dentro da janela.

---

## Exclusão: fases de qualificação

Diferente do ranking de países, as eliminatórias anteriores à Fase de Liga **não dão
pontos por partida** para o coeficiente de clubes. Quem é eliminado ali recebe apenas uma
pontuação fixa de consolação, definida estritamente pela rodada em que caiu.

> **Valores não especificados.** O enunciado não traz a tabela de consolação. Proposta,
> a confirmar:

| Eliminado em | Pontos |
|---|---:|
| Play-off de qualificação | 1,00 |
| 3ª pré-eliminatória | 0,75 |
| 2ª pré-eliminatória | 0,50 |
| 1ª pré-eliminatória | 0,25 |

---

## Exemplo de cálculo

Clube X na temporada mais recente, na Champions League: termina a Fase de Liga em **5º**,
com 5 vitórias, 2 empates e 1 derrota nos 8 jogos, e cai nas **quartas de final** (2
vitórias e 2 derrotas no mata-mata).

| Origem | Cálculo | Pontos |
|---|---|---:|
| Bônus de posição (5º) | tabela da Champions | 11,00 |
| Pontos por partida, Fase de Liga | 5 × 2 + 2 × 1 | 12,00 |
| Pontos por partida, mata-mata | 2 × 2 | 4,00 |
| Bônus de avanço | alcançou as quartas | 3,00 |
| | **Total da temporada** | **30,00** |

Como não há pesos por recência, esse valor entra inteiro na soma, e permanece inteiro
pelas cinco temporadas da janela, até expirar de uma vez.

O teto aproximado de uma temporada na Champions é cerca de **47 pontos**: 12 de bônus do
1º lugar, 16 de oito vitórias na Fase de Liga, 6 do bônus até a final e mais as vitórias
do mata-mata.

---

## Como está aplicado no jogo

As regras acima estão implementadas e o recorte existe na interface, mas **sem fonte de
pontos** — ver o aviso no topo.

- Entrada: aba **Colocação** da tela Ranking de Clubes, recorte **Continente → Europa**
  (ou País → Espanha / Itália / Inglaterra, que filtra o mesmo coeficiente por país).
  O chip "Europa" aparece mesmo sem clubes pontuados, ancorado no conjunto de regras;
  sem isso o recorte seria inalcançável.
- Regras em `RankingEuropa.kt`; lançamentos na tabela `ranking_europa` (DB v33).
- `registrarCampanhaEuropeia(...)` e `registrarQualificacaoEuropeia(...)` no
  `GameRepository` já pontuam uma campanha completa — basta chamá-las ao encerrar cada
  competição, quando elas existirem.
- O **piso de 20%** é aplicado em Kotlin, não em SQL, porque depende da média dos clubes
  do país e fazer isso em SQL exigiria função de janela, cuja disponibilidade varia com a
  versão do Android. A consulta parte de `times` com LEFT JOIN, para que o clube de elite
  sem nenhuma participação na janela apareça e o piso possa socorrê-lo.
- Elegibilidade: só divisões de elite (11 Premier, 13 La Liga, 14 Serie A). A Championship
  (12) fica fora, senão o piso daria pontos às suas 24 equipes sem elas jogarem nada.

## O que falta para isto funcionar no jogo

1. **As três competições da UEFA**, com Fase de Liga de 36 clubes e mata-mata — nada
   disso existe. O seed tem 84 clubes europeus, em 3 países (Inglaterra 44, Espanha 20,
   Itália 20), o que é pouco para uma Fase de Liga de 36 somada a Europa e Conference.
2. **Fase máxima alcançada por clube**, que o jogo não registra: o Hall da Fama guarda só
   campeão e vice. O RNC brasileiro resolveu isso derivando de `PartidaEntity.fase`.
3. **Coeficiente por país**, agregado na mesma janela, para o piso de 20%.
4. **Distinção entre qualificação e Fase de Liga** no modelo de fases, já que as duas
   pontuam de formas completamente diferentes.
