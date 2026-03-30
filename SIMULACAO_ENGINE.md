# ManagerFoot — Como a Engine Decide Quem Vence

> Documento técnico detalhando todos os fatores que influenciam gols e resultados
> na simulação de partidas (`SimuladorPartida.kt` + `CalculadoraForca.kt`).

---

## Visão Geral do Fluxo

```
Escalação Casa + Escalação Fora
         ↓
  CalculadoraForca (força bruta de cada time)
         ↓
  Multiplicadores externos (mandante, cansaço, estilo)
         ↓
  Incidentes pré-jogo (cartões → expulsão, lesões)
         ↓
  Probabilidade de gols por time (proporção de forças)
         ↓
  Distribuição de Poisson → número de gols
         ↓
  Atribuição de gols/assistências por jogador (ponderada)
```

---

## 1. Força Base do Time (`CalculadoraForca`)

A força de um time é calculada como a **média ponderada da força efetiva dos titulares por setor**:

| Setor    | Peso | Jogadores típicos              |
|----------|------|-------------------------------|
| Goleiro  | 15%  | GL                             |
| Defesa   | 30%  | ZG, LD, LE                     |
| Meio     | 30%  | VOL, MC, MA                    |
| Ataque   | 25%  | PD, PE, CA, SA                 |

**Fórmula:**
```
ForcaTime = (médiaGL × 0.15) + (médiaDefesa × 0.30) + (médiaMeio × 0.30) + (médiaAtaque × 0.25)
```

### Implicações diretas
- **Defesa e meio-campo têm peso maior (30% cada)** do que o ataque (25%). Um time equilibrado defensivamente resiste melhor do que um time com atacantes brilhantes mas vulnerável atrás.
- **O goleiro vale 15%** — um goleiro excepcional eleva a força do time de forma mensurável.
- Se algum setor não tem jogadores (escalação incompleta), a média daquele setor vira **0**, colapsando a força total.

---

## 2. Força Efetiva do Jogador (`Jogador.forcaEfetiva`)

A força que cada jogador contribui para o cálculo não é simplesmente o atributo `forca`. Há dois ajustes:

### 2a. Penalidade de Improviso

| Situação                                    | Penalidade |
|---------------------------------------------|-----------|
| Jogando na posição natural                  | 0          |
| Jogando na posição secundária               | −5         |
| Jogando em outra posição do mesmo setor     | −10        |
| Jogando em setor completamente diferente    | −20        |

**Exemplo:** Um Volante (MEIO) jogando como Zagueiro (DEFESA) perde 20 pontos de força.

### 2b. Bônus/Penalidade de Morale

| Estado Morale   | Ajuste |
|-----------------|--------|
| EXCELENTE       | +5     |
| BOM             | +2     |
| NORMAL          | 0      |
| INSATISFEITO    | −5     |
| REVOLTADO       | −10    |

**O morale é o único atributo que pode ser melhorado sem transferências.** Um elenco com moral excelente pode superar adversários com força nominal superior.

---

## 3. Multiplicadores Externos (Pós-Força Base)

A força bruta é multiplicada por três fatores antes de entrar no cálculo de gols:

### 3a. Vantagem de Mandante
```
fatorMandante = 1.12   (+12% para o time da casa)
```
O time da casa recebe **12% de bônus** na sua força total. Um visitante precisa de força superior para anular essa vantagem, mas a margem é mais equilibrada do que antes.

### 3b. Fator Cansaço
```
3+ jogos nos últimos 7 dias → ×0.93  (−7% de força)
2  jogos nos últimos 7 dias → ×0.97  (−3% de força)
≤1 jogo                    → ×1.00  (sem penalidade)
```

### 3c. Fator de Estilo Tático (pedra-papel-tesoura)

| Atacante        | Defensor        | Multiplicador para o atacante |
|-----------------|-----------------|-------------------------------|
| CONTRA_ATAQUE   | OFENSIVO        | ×1.14 (+14%) — transições devastadoras |
| OFENSIVO        | CONTRA_ATAQUE   | ×0.92 (−8%)  — deixa espaços atrás    |
| OFENSIVO        | DEFENSIVO       | ×0.88 (−12%) — bloco compacto sufoca  |
| DEFENSIVO       | OFENSIVO        | ×1.10 (+10%) — sai em transição eficiente |
| Outros combos   | Qualquer        | ×1.00 (neutro)                |

Os dois duelos principais (CA × OF e OF × DEF) agora produzem um **efeito combinado de ~22%** por aplicarem bônus para um time e penalidade para o outro simultaneamente, tornando a vantagem tática capaz de causar goleadas quando a diferença de força for moderada.

---

## 4. Cálculo da Probabilidade de Gols

Após aplicar todos os multiplicadores, a força final de cada time é:

```
fCasa = ForcaBase(casa) × 1.20 × fatorCansaço(casa) × fatorEstilo(casa, fora)
fFora = ForcaBase(fora) × 1.00 × fatorCansaço(fora) × fatorEstilo(fora, casa)
```

A **proporção de forças** define a probabilidade de cada time marcar:

```
probCasa = fCasa / (fCasa + fFora)
probFora = fFora / (fCasa + fFora)
```

A **média esperada de gols** de cada time é:

```
mediaGolsCasa = 2.7 × probCasa × penalizações × chancesMultCasa
mediaGolsFora = 2.7 × probFora × penalizações × chancesMultFora
```

> A constante `2.7` é a média histórica de gols por jogo no futebol brasileiro.  
> Com times iguais e sem vantagens: probCasa = probFora = 0.5 → cada time espera **1.35 gols** → total = **2.7 ✓**  
> O multiplicador `1.8` que existia anteriormente inflava o total para 4.86 gols — **removido** para resultados realistas.

### Exemplo numérico
- Casa com força 70 (mandante × 1.12), Fora com força 60:
  - fCasa = 70 × 1.12 = **78.4**
  - fFora = 60 × 1.00 = **60.0**
  - probCasa = 78.4 / 138.4 = **56,6%**
  - dominance = 18.4 / 138.4 = 0.133 → chancesMultCasa = **×1.073**
  - mediaGolsCasa = 2.7 × 0.566 × 1.073 = **1.64 gols esperados**
  - mediaGolsFora = 2.7 × 0.434 = **1.17 gols esperados**
  - Total esperado: **~2.81 gols** (realista para mandante mais forte)

---

## 5. Fator de Dominância — Criação de Chances

Após calcular `probCasa` e `probFora`, a engine aplica um **multiplicador de dominância** à média de gols esperados de cada time. Isso traduz uma superioridade de força clara em mais oportunidades concretas:

```
dominance = (fCasa - fFora) / (fCasa + fFora)   ∈ [-1, +1]

chancesMultCasa = 1.0 + max(dominance, 0.0) × 0.35
chancesMultFora = 1.0 + max(-dominance, 0.0) × 0.35
```

| Cenário                        | chancesMultCasa | chancesMultFora |
|--------------------------------|-----------------|-----------------|
| Forças totalmente iguais       | ×1.00           | ×1.00           |
| Casa levemente superior (10%)  | ~×1.04          | ×1.00           |
| Casa muito superior (30%)      | ~×1.16          | ×1.00           |
| Casa dominante (50%+)          | ×1.35 (máx)     | ×1.00           |

O time mais fraco **não é penalizado diretamente** — ele simplesmente não recebe o multiplicador positivo. O efeito prático é que um time significativamente superior gera em média **até 35% mais gols** do que a proporção de forças isolada indicaria, refletindo o acúmulo de pressão e chances que o domínio tático produz.

## 6. Geração dos Gols — Distribuição de Poisson

Os gols são sorteados usando a **distribuição de Poisson** com a média calculada acima. Esta distribuição representa bem o futebol real:

- Uma média de 2.0 pode produzir 0, 1, 2, 3, 4... gols com probabilidades decrescentes.
- **Máximo de 9 gols por time** (cap de segurança).
- Há sempre variância — um time de força menor pode vencer por sorte, como ocorre no futebol real.

**Fórmula completa das lambdas de Poisson:**
```
mediaGolsCasa = 2.7 × probCasa × penalCasa × chancesMultCasa
mediaGolsFora = 2.7 × probFora × penalFora × chancesMultFora
```

## 7. Penalizações por Incidentes Durante o Jogo

Antes do cálculo de gols, a engine sorteia incidentes para cada titular:

| Incidente       | Probabilidade por jogador | Efeito na força |
|-----------------|---------------------------|-----------------|
| Cartão Amarelo  | 12% por titular           | Nenhum          |
| Cartão Vermelho | ~1% por titular (12% × 8%)| −13% na força do time (`×0.87`) |
| Lesão           | 3% por titular            | Veja abaixo     |

**Lesão:**
- Se há reserva disponível → substituição automática, **sem penalidade de força**.
- Se não há reserva → time joga com um a menos → **−13% (`×0.87`)**.

As penalizações se **multiplicam**: dois expulsos → ×0.87 × 0.87 = **×0.757 (−24%)**.

---

## 8. Quem Marca os Gols (Artilharia)

Após definir quantos gols cada time fez, a engine atribui autores e assistentes por **sorteio ponderado**. Os pesos são:

### Cantor (marcador de gol)
| Setor   | Fórmula do Peso                          |
|---------|------------------------------------------|
| Ataque  | `finalizacao × 3 + velocidade`           |
| Meio    | `finalizacao × 2 + passe`                |
| Defesa  | `finalizacao + fisico / 2`               |

### Assistente (72% de chance de haver assistência)
| Setor   | Fórmula do Peso                          |
|---------|------------------------------------------|
| Meio    | `passe × 3 + tecnica`                    |
| Ataque  | `passe × 2 + velocidade`                 |
| Defesa  | `passe × 2 + tecnica`                    |

**Atributos mais relevantes para artilharia:** `finalizacao` (peso 3× para atacantes), `velocidade`.  
**Atributos mais relevantes para assistência:** `passe` (peso 3× para meias), `tecnica`.

---

## 9. Ranking de Impacto dos Fatores

| Rank | Fator                                        | Magnitude estimada       |
|------|----------------------------------------------|--------------------------|
| 1    | **Força média dos titulares**                | Base de tudo (~60%)      |
| 2    | **Atributos `defesa` e `tecnica`**           | Até 45% do valor por setor |
| 3    | **Vantagem de mandante**                     | +12% força casa          |
| 4    | **Fator de dominância (criação de chances)** | Até +35% gols esperados  |
| 5    | **Improvisação fora da posição**             | −5 a −20 por jogador     |
| 6    | **Morale do elenco**                         | −10 a +5 por jogador     |
| 7    | **Penalidade de expulsão/lesão sem reserva** | −13% por ocorrência      |
| 8    | **Fator tático (CONTRA_ATAQUE vs OFENSIVO)** | ±5–6%                    |
| 9    | **Cansaço (3+ jogos em 7 dias)**             | −7% força                |
| 10   | **Distribuição de Poisson (sorte)**          | Variância inerente       |

---

## 10. O que o Jogador Pode Controlar

| Ação do jogador                        | Efeito na simulação              |
|----------------------------------------|----------------------------------|
| Comprar jogadores de maior `forca`     | Sobe a força de todos os setores |
| Contratar goleiro com alta `defesa`    | Goleiro: `defesa` vale 40% do seu valor no setor |
| Contratar goleiro com bom `passe`      | Goleiro: `passe` vale 10% (distribuição com os pés) |
| Contratar zagueiros com alta `defesa`  | `defesa` vale 25% do valor no setor defensivo |
| Contratar meias com alta `tecnica`     | `tecnica` vale 20% do valor no setor de meio |
| Contratar meias com alto `passe`       | `passe` vale 20% no meio — mesmo peso que `tecnica` |
| Contratar atacantes com alta `tecnica` | `tecnica` vale 15% no setor de ataque |
| Escalar jogadores nas posições naturais| Evita penalidades de improviso   |
| Manter jogadores na reserva            | Evita −13% por lesão sem reposição|
| Mudar para CONTRA_ATAQUE vs time ofensivo | +14% na força de ataque (e −8% para o adversário) |
| Não usar OFENSIVO contra time defensivo | Evita −12% no ataque (adversário ainda ganha +10%) |
| Usar DEFENSIVO contra time OFENSIVO | +10% na força defensiva / transições |
| Manter morale do elenco elevado        | Até +5 por jogador titularizado  |
| Gerenciar cansaço (poupar em datas duplas) | Evita −3% a −7%             |

---

## 11. Limitações e Possíveis Melhorias Futuras

| Limitação atual                        | Melhoria possível               |
|----------------------------------------|---------------------------------|
| Fator cansaço usa dias fixos sem verificar jogos reais do calendário | Contar jogos reais das últimas `X` rodadas |
| 4 combos táticos criam vantagem; EQUILIBRADO e outros pares são neutros | Adicionar combos envolvendo EQUILIBRADO |
| Morale não é atualizado após vitórias/derrotas | Atualizar morale pós-partida automaticamente |
| Variância de Poisson pode gerar resultados extremos em raros casos | Cap de 9 por time mantido; com o ×1.8 removido as médias são menores e goleadas absurdas são muito mais raras |

---

*Atualizado em: 29/03/2026 — reflete as mudanças em `SimuladorPartida.kt` / `CalculadoraForca`:*
- *Fator mandante reduzido de 1.20 → 1.12 (revisão anterior)*
- *Atributos `defesa` e `tecnica` incorporados na fórmula de valor por setor (revisão anterior)*
- *Multiplicador ×1.8 das lambdas de Poisson removido → total esperado de gols agora é ~2.7 por jogo (realista)*
- *Fator de dominância aumentado de ×0.35 para ×0.55 → times superiores vencem com mais consistência*
- *Matriz tática expandida: CA×OF (+14%/−8%) e OF×DEF (−12%/+10%) criam swing combinado de ~22%, possibilitando goleadas táticas*
