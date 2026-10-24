# CONMEBOL Libertadores — Regras de Vagas e Formato

> A contraparte sul-americana de [regras_championsleague.md](regras_championsleague.md).
> E uma contraparte de verdade, não uma variação: a Libertadores **manteve** o formato
> tradicional de grupos e mata-mata, enquanto a Europa migrou para a liga única de 36.

Quando existir, esta competição acorda o bloco que está dormente desde o começo no
[Ranking CONMEBOL](ranking_conmebol.md) — campeão 1.000 pontos, vice 400, semifinal 240,
quartas 160, oitavas 80, mais 80 por vitória e 40 por empate da fase de grupos em diante —
e também pontua no [Ranking Mundial da IFFHS](ranking_mundial.md), onde uma vitória na
Libertadores vale 14 pontos, o mesmo peso da Champions.

---

## 1. Os pilares do formato

47 clubes das 10 associações sul-americanas.

| Etapa | Clubes | Como funciona |
|---|---:|---|
| **Pré-Libertadores** (Fases 1, 2 e 3) | 19 | Mata-mata eliminatório. Só **4** sobreviventes chegam aos grupos. |
| **Fase de grupos** | 32 | 8 grupos de 4, em **turno e returno** — 6 partidas por clube. |
| **Oitavas em diante** | 16 | Os **2 primeiros** de cada grupo avançam. |

- **Os terceiros colocados** de cada grupo não são eliminados: são transferidos para os
  playoffs da **Copa Sul-Americana**.
- **Sem gol fora.** O gol de visitante como critério de desempate foi extinto.
- **Empate no agregado, das oitavas às semifinais:** vai **direto aos pênaltis**, sem
  prorrogação.
- **Final única**, em estádio neutro pré-definido. Aí sim há prorrogação de 30 minutos
  antes dos pênaltis.

---

## 2. Distribuição de vagas por país

| País | Total | Direto nos grupos | Na Pré-Libertadores |
|---|---:|---:|---:|
| Brasil | 7 | 5 | 2 |
| Argentina | 6 | 5 | 1 |
| Bolívia | 4 | 2 | 2 |
| Chile | 4 | 2 | 2 |
| Colômbia | 4 | 2 | 2 |
| Equador | 4 | 2 | 2 |
| Paraguai | 4 | 2 | 2 |
| Peru | 4 | 2 | 2 |
| Uruguai | 4 | 2 | 2 |
| Venezuela | 4 | 2 | 2 |
| **Soma** | **45** | **26** | **19** |

**Vagas extras:** os campeões vigentes da **Libertadores** e da **Copa Sul-Americana**
entram direto na fase de grupos da edição seguinte, independentemente do que fizeram na
liga local. Isso pode aumentar temporariamente a representação do país deles.

### A aritmética fecha — e as duas vagas extras são indispensáveis

Este é o primeiro conjunto de regras desta série em que as contas fecham sozinhas, sem
precisar de correção. Vale mostrar, porque o papel das duas vagas extras só aparece aqui:

```
vagas nacionais            = 7 + 6 + (8 países × 4)   = 45
campeões vigentes (2)                                 =  2
                                              total   = 47  ✔ confere

grupos: diretos            = 5 + 5 + (8 × 2)          = 26
        + sobreviventes da Pré-Libertadores           =  4
        + campeões vigentes                           =  2
                                              total   = 32  ✔ confere

Pré-Libertadores           = 2 + 1 + (8 × 2)          = 19  ✔ confere
```

Sem as duas vagas de campeão vigente a fase de grupos ficaria com **30** clubes, e não 32 —
ou seja, elas não são um detalhe de prestígio, são estruturais. Quem implementar não pode
tratá-las como opcionais.

> **Decisão pendente.** E se o campeão vigente **já se classificou** pela liga local? Aí a
> sua vaga de campeão sobra, e os 32 não fecham. Na competição real a vaga passa ao próximo
> colocado da liga daquele país. A proposta é fazer o mesmo — é a mesma regra de "queda da
> vaga" que a Europa League já usa para o campeão da copa, em `ListaDeAcessoEuropa`.

---

## 3. O que muda em relação à Champions

Vale listar, porque a tentação de reaproveitar a máquina europeia inteira é grande e em
três pontos ela não serve:

| Aspecto | Champions | Libertadores |
|---|---|---|
| Primeira fase | Liga única de 36, 8 jogos | 8 grupos de 4, turno e returno, 6 jogos |
| Classificação | Tabela única de 1 a 36 | 8 tabelas de 4 |
| Quem avança | 1º–8º direto, 9º–24º no play-off | 2 primeiros de cada grupo |
| Quem cai fora | 25º–36º, sem repescagem | 3º de cada grupo vai à Sul-Americana |
| Empate no mata-mata | Prorrogação e pênaltis | **Pênaltis direto**, sem prorrogação |
| Final | Ida e volta | **Jogo único em campo neutro**, com prorrogação |

O `FormatoContinental` criado para Champions e Europa League **não** descreve a
Libertadores: ele assume liga única, rodadas de 1 a 8 e os cortes 8/24. A Libertadores pede
o formato `GRUPOS_E_MATA_MATA`, que já existe no jogo.

---

## Pré-requisito: faltam 7 das 10 associações

O seed tem **170 clubes sul-americanos**, de sobra para os 47 — mas de apenas **3 das 10
associações**: Brasil (80), Argentina (60) e Uruguai (30). Não há clubes da Bolívia, Chile,
Colômbia, Equador, Paraguai, Peru nem Venezuela.

Com isso, apenas **17 das 45 vagas nacionais** têm de onde sair: as 7 do Brasil, as 6 da
Argentina e as 4 do Uruguai.

**A boa notícia, diferente do caso europeu:** o enunciado **não impõe travas geográficas**.
A Champions proíbe compatriotas na mesma chave e limita a 2 rivais por país, o que tornava
o torneio matematicamente impossível com 3 países. Aqui não há nada disso no texto, então a
Libertadores **é implementável já**, redistribuindo as 28 vagas órfãs entre os três países
existentes.

> ⚠️ **Mas se você quiser a trava real, ela também não cabe.** Na Libertadores de verdade o
> sorteio evita clubes do mesmo país num grupo. Um grupo de 4 precisaria de 4 países
> distintos, e temos 3 — impossível por construção, exatamente como na Champions. Com as 10
> associações, cabe com folga.

### Caminhos

| Caminho | O que implica |
|---|---|
| **Redistribuir as vagas (mais rápido)** | Dar as 28 vagas órfãs a Brasil, Argentina e Uruguai, proporcionalmente. Roda já, sem tocar no seed, e o torneio fica com 3 países — aceitável porque não há trava geográfica no enunciado. |
| **Pool sul-americano (recomendado)** | Adicionar clubes dos 7 países que faltam, no mesmo modelo do pool europeu (divisões 90/91/92). O domínio já está pronto: `Continentes.PAISES_AMERICA_DO_SUL` **já reconhece as 10 associações da CONMEBOL**, incluindo Chile, Colômbia, Paraguai, Peru, Equador, Bolívia e Venezuela. Com ~30 clubes o torneio ganha os 10 países, a trava geográfica passa a ser possível e a Sul-Americana depois reaproveita os mesmos clubes. |
| **Ligas completas** | Criar campeonatos nacionais para os 7 países. O mais fiel e o mais caro; fica como evolução do pool. |

---

## Estado da implementação

### Pronto

**A lista de acesso** — `ListaDeAcessoLibertadores.kt`, com a aritmética das 47 vagas
travada em teste: as cotas oficiais dos 10 países, as 26 diretas e 19 na Pré, as duas vagas
de campeão vigente com a regra de queda quando o campeão já se classificaria pela liga, e a
estrutura das três fases prévias.

**A redistribuição de vagas** é genérica de propósito: ela recebe as associações que têm
clubes e reparte as 45 vagas proporcionalmente às cotas oficiais. Com os 3 países do seed
atual ela redistribui as 28 vagas órfãs sem perder nenhuma e preservando a hierarquia
(Brasil ≥ Argentina ≥ Uruguai); quando os 7 países que faltam ganharem clubes, ela devolve
as cotas oficiais **sem alteração de código**.

**O sorteio dos grupos** — `SorteioLibertadores.kt`: 4 potes de 8 por coeficiente, 8 grupos
de 4 com um clube de cada pote. A separação por país é um objetivo, não uma trava: o
sorteio escolhe sempre o grupo com menos compatriotas, então **nunca falha**, e um teste
confirma que com as 10 associações ele separa todos os compatriotas, enquanto com 3 países
ele apenas minimiza — um grupo de 4 não cabe em 3 países.

### Resolvido pelo caminho

O documento levantava quem receberia **bye** na Pré-Libertadores, já que 19 é ímpar.
Não há bye: a entrada escalonada absorve a sobra. Derivando de trás para frente, a Fase 3
precisa de 8 clubes para entregar 4, a Fase 2 precisa de 16 para entregar 8, e desses 16
apenas 3 vêm da Fase 1 — os outros 13 entram direto. Logo a Fase 1 tem 6 clubes, e **toda
fase tem número par**.

### Falta

Nada do núcleo nem da fiação. A competição está jogável de ponta a ponta.

### A fiação

**O motor** — `MotorCampeonato.FORMATO_LIBERTADORES`, um `FormatoLibertadores`
próprio em vez do `FormatoContinental` das europeias, porque as duas máquinas não
têm as mesmas peças: aqui há fases prévias, grupos em vez de tabela única e uma
final de jogo único. Mais `gerarGruposTurnoReturno`, que aplica o round-robin
grupo a grupo e alinha as rodadas dos oito — 6 rodadas, 12 partidas por grupo, 96
no total, 6 por clube —, e `paresOitavasLibertadores`, que cruza o 1º do grupo i
com o 2º do grupo (i+4)%8: um deslocamento de meia volta que por construção nunca
repete um adversário de chave, sem depender de um sorteio que pudesse falhar.

**As faixas de rodada**, separadas entre si: a Pré ocupa 1..6, os grupos 11..16 e
o mata-mata 21..27, com a final única na 27. **As casas de `ordemGlobal`** são
15/19/25/29/35/39 na Pré, 45/75/105/135/165/195 nos grupos e 265/275, 295/305,
335/345 e 385 no mata-mata — nenhuma colide com a Champions, com a Europa League
ou com os múltiplos de 10 das ligas nacionais, e um teste trava isso.

**O repositório** cria a competição com a Pré-Libertadores e sorteia os grupos
quando a Fase 3 termina — tem de ser nessa ordem, porque 4 dos 32 vêm da Pré. É o
mesmo padrão do play-off → oitavas da Champions. O rótulo de
`CampeonatoTimeEntity.grupo` guarda a porta de entrada ("G" direto, "P" na Pré)
até o sorteio, e depois dele passa a ser o grupo A..H; é o que distingue, na Fase
2, os 13 que entram direto dos 3 que vêm da Fase 1, sem tabela nova nem migração.

**As duas vagas de campeão vigente**, que o documento chama de estruturais,
ganharam a regra de queda que faltava: quando não têm titular — na primeira
temporada não houve Libertadores, e a Copa Sul-Americana não existe no jogo — elas
**descem para os próximos colocados**, pelo parâmetro `fecharFaseDeGrupos` de
`montar`. Sem isso a fase de grupos nasceria com 30 clubes e a competição nunca
seria criada.

**O desempate:** das oitavas às semis o empate no agregado vai direto aos
pênaltis. Isso já era o comportamento de todo o jogo, que não simula prorrogação
em competição nenhuma; o que a Libertadores acrescenta é a regra registrada em
`FormatoLibertadores.temProrrogacao`, onde a prorrogação se encaixa quando a
engine a tiver, e a **final de jogo único**, que é mesmo nova. Um confronto com
uma partida só é o que faz o chaveamento, o desempate e o campo neutro a tratarem
como jogo único em vez de agregado — e foi preciso corrigir a regra de pênaltis de
`GRUPOS_E_MATA_MATA`, que decidia no empate de qualquer partida com fase e levaria
a ida das oitavas aos pênaltis.

**A ponte com a Copa Sul-Americana** não inventou a competição. Os terceiros de
cada grupo são **derivados** das tabelas por
`GameRepository.buscarClassificadosSulAmericana(libertadoresId)`: quem implementar
a Sul-Americana chama com o id da Libertadores da temporada e recebe os 8 clubes,
sem que a Libertadores tenha gravado nada em lugar nenhum.

**Hall da Fama:** código 34, com a 35 reservada à Sul-Americana e o grupo novo 800
("América do Sul") no filtro "Todas". **Rankings:** o bloco continental do
`RankingConmebol` passou a pontuar sozinho, e o peso 14 da IFFHS sai do nome
"Libertadores". **Telas:** Rodadas (grupos e mata-mata), Classificação (8 tabelas
de 4, pelo filtro de grupo que o `ClassificacaoDao` já tinha), Artilharia e
Chaveamento. **Migração de banco: nenhuma.**

## O que ainda falta

Mantido como registro do que continua valendo como evolução.

1. **Os clubes dos 7 países que faltam**, se você quiser as 10 associações — ver
   acima. É a única coisa que falta para a trava geográfica real do sorteio passar
   a ser possível. Enquanto isso a redistribuição reparte as 28 vagas órfãs entre
   Brasil, Argentina e Uruguai, e o sorteio **minimiza** compatriotas por grupo em
   vez de proibi-los.
2. **A Copa Sul-Americana**, para onde vão os terceiros de cada grupo. A lista está
   pronta e derivada; falta a competição que a consome — e, com ela, a segunda vaga
   de campeão vigente, que hoje desce para o próximo colocado.
3. **A prorrogação**, que a engine não tem em competição nenhuma. A regra da
   Libertadores já está registrada por fase; falta o motor simular os 30 minutos.
