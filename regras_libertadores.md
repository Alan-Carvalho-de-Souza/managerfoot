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

### Como as 7 vagas do Brasil são distribuídas

| Forma de classificação | Vagas | Destino |
|---|---:|---|
| Campeonato Brasileiro, 1º ao 4º | 4 | Fase de grupos |
| Copa do Brasil, **campeão** | 1 | Fase de grupos |
| Campeonato Brasileiro, 5º | 1 | **Pré-Libertadores** |
| Copa do Brasil, **vice-campeão** | 1 | **Pré-Libertadores** |
| **Total** | **7** | |

A Argentina reserva uma das suas ao campeão da **Copa Argentina**, na fase de
grupos. O Uruguai não reserva nenhuma. São vagas de título **dentro** da cota do
país, e não extras.

### O efeito cascata

Nenhum clube ocupa duas vagas, e vaga que sobra não se perde: ela **desce para o
próximo colocado** da Série A, mantendo o seu destino — uma vaga de grupo continua
de grupo, uma de Pré continua de Pré. É daí que o G-5 vira G-6, G-7 e, no acúmulo
máximo, G-9:

- **Campeão continental brasileiro.** O campeão vigente da Libertadores ou da
  Sul-Americana entra pela vaga **estrutural**, por fora da cota do país. Se ele
  também se classificaria pela liga, a vaga da liga cascateia e um colocado a mais
  entra — e isso vale inclusive quando ele é brasileiro, porque a vaga de título
  do Brasil é da Copa do Brasil.
- **Finalista da Copa já classificado.** Campeão ou vice que termina dentro do
  G-4/G-5 ocupa a vaga de título, e a que ele teria pela colocação desce. A conta
  por destino não muda: 5 nos grupos e 2 na Pré, sempre.
- **Rebaixado não joga.** É regulamento da CONMEBOL. Um finalista de copa que cai
  para a Série B perde a vaga, que volta para o Brasileirão. No jogo isso sai de
  graça: o rollover atualiza as divisões **antes** de criar a Libertadores, então o
  clube deixa de ser candidato e a regra de queda age sozinha.
- **Detentor que não existe.** Primeira temporada, ou um campeão da Copa que não
  está na Série A: a vaga desce para a liga, e o Brasileirão volta a entregar o
  G-5 mais o 6º na Pré.

Na tela de Classificação essa distribuição é aplicada por
`VagasDoBrasileirao.distribuir`, que recebe as posições dos detentores e devolve a
zona de cada linha — as cores da tabela e os números da legenda saem da mesma
conta, então não há como uma dizer 5 e a outra mostrar 6.

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

## 4. O sorteio

O sorteio tem **dois momentos**, e o critério de divisão em ambos é um só: o
**Ranking de Clubes da CONMEBOL**. No jogo esse ranking é o
[Ranking CONMEBOL](ranking_conmebol.md), somado à reputação como desempate dos
clubes que ainda não acumularam campanha.

### 4.1 As fases prévias

**Fase 1 — 6 clubes.** Divididos em dois potes de 3 pelo ranking. Os 3 do Pote 1
enfrentam os 3 do Pote 2; qual pega qual é sorteado, e o do Pote 1 decide em
casa. Os três vencedores avançam.

**Fase 2 — 16 clubes:** os 13 pré-classificados mais os 3 sobreviventes da Fase 1.

| Pote | Quem | Mando |
|---|---|---|
| **A** | os 8 melhores ranqueados **entre os 13 pré-classificados** | decidem o jogo de volta em casa |
| **B** | os outros 5 pré-classificados + os 3 vindos da Fase 1 | — |

As bolinhas do Pote B são sorteadas para enfrentar as do Pote A. Note que os 3 da
Fase 1 vão ao Pote B **pelo caminho que percorreram, e não pelo ranking**: por
isso `sortearFase2` recebe quem veio da Fase 1 em separado, em vez de deduzir
pela ordenação.

**Fase 3 — 8 clubes. Não há sorteio.** Os 8 vencedores da Fase 2 se enfrentam em
chaveamento pré-definido: **C1×C8, C2×C7, C3×C6, C4×C5**, onde C1..C8 são os
confrontos da Fase 2 na ordem do Pote A. O C1 decide em casa. Os 4 vencedores vão
aos grupos.

### 4.2 A fase de grupos

Com os 32 definidos — 28 diretos e 4 da Pré —, **4 potes de 8** pelo ranking:

| Pote | Quem |
|---|---|
| **1** | o campeão vigente da Libertadores, **automaticamente na posição A1**, e as outras 7 vagas com os melhores ranqueados |
| **2** | os 8 seguintes |
| **3** | os 8 seguintes |
| **4** | os **4 de menor ranking** + os **4 sobreviventes da Pré**, independentemente do ranking deles |

**O sorteio é sequencial:** todo o Pote 1 primeiro, definindo os cabeças de chave
do Grupo A ao H, depois o Pote 2, o Pote 3 e o Pote 4.

**Bloqueio de países.** Dois clubes do mesmo país não podem ficar no mesmo grupo.
A bolinha cai num grupo ao acaso e, se aquele grupo já tem um compatriota, **é
movida para o próximo grupo disponível**.

**A exceção do Pote 4.** Os 4 que vieram da Pré **podem** enfrentar clubes do
mesmo país: eles não estavam definidos quando os potes fecharam, então o bloqueio
não se aplica a eles. É regra da competição, não concessão do algoritmo — e é por
isso que existem duas métricas, `paresDeCompatriotas` (todos) e
`paresDeCompatriotasBloqueaveis` (só os que a regra proíbe). A segunda é a medida
de qualidade; a primeira mistura defeito com regra.

### 4.3 O que o sorteio garante, e o que não garante

Garante: 8 grupos de 4, um clube de cada pote em cada grupo, o campeão vigente no
Grupo A em **toda** semente, e **nunca falha** — esgotados os grupos, aceita o
conflito em vez de travar a criação do torneio.

Não garante zero compatriotas. Medido sobre o seed, sobra **no máximo 1 par** que
a regra proibiria, e a razão é o sorteio ser **sequencial**: a bolinha anda para o
próximo grupo disponível e nunca volta atrás, então uma escolha cedo pode
encurralar a última. Uma troca (`desfazerCompatriotas`) desfaz a maior parte dos
nós, mas só permuta clubes do **mesmo pote**, para cada grupo continuar com um de
cada — e é esse limite que deixa o resíduo. O sorteio real da CONMEBOL tem a
mesma propriedade, e é por isso que a mesa às vezes precisa reordenar bolinhas.

Há também um limite de contagem que nenhum algoritmo vence: são 8 grupos, então um
país com mais de 8 clubes tem de repetir em algum deles.

---

## Pré-requisito resolvido: as 10 associações

O seed tinha **170 clubes sul-americanos de apenas 3 associações** — Brasil (80),
Argentina (60) e Uruguai (30) —, e com isso só 17 das 45 vagas nacionais tinham de
onde sair. As outras 28 eram redistribuídas entre os três países presentes.

Foi pelo caminho recomendado: um **pool sul-americano** na divisão 90, no mesmo
modelo do pool europeu (`scripts/gen_outros.py`), com **6 clubes para cada uma das
7 associações que faltavam** — Bolívia, Chile, Colômbia, Equador, Paraguai, Peru e
Venezuela. São 42 clubes (ids 654–695) e 840 jogadores.

Seis por país, e não os 4 da cota, para que as vagas tenham concorrência e haja
rotação entre temporadas: dentro do pool a fila é ordenada pelo coeficiente do
Ranking CONMEBOL e, no desempate, pela reputação. As reputações ficam na faixa
61–78 — abaixo dos gigantes brasileiros (até 89) e argentinos (até 97) —, o que
mantém Brasil e Argentina nos potes 1 e 2 do sorteio sem tornar os outros oito
países figurantes.

**O efeito no domínio é o que `cotasEfetivas` foi escrita para ter:** com as 10
associações presentes ela devolve as **cotas oficiais**, sem redistribuir nada e
sem uma linha de código alterada. `SeedLibertadoresTest` trava isso lendo o
`seed_data.json` de verdade, e confere vaga por vaga que cada país entra com o que
a tabela da seção 2 manda: Brasil 5 + 2, Argentina 5 + 1, os outros oito 2 + 2.

> **Os elencos do pool são SINTÉTICOS.** Os 53 clubes do pool europeu têm elenco
> real escrito à mão; estes 42 não. Seriam 840 jogadores, e nomes reais nessa
> quantidade, para sete ligas, sairiam errados — jogador no clube trocado, idade e
> posição chutadas. Nome gerado não finge ser quem não é, e o elenco cumpre o papel
> de dar a cada clube um plantel coerente com a sua reputação. Para trocar um clube
> por um elenco real, basta acrescentar a entrada dele em `ROSTERS`: o gerador só
> age quando o clube não tem roster escrito. Os **escudos** continuam manuais —
> `escudo_res` sai vazio e o app cai no escudo genérico até o PNG existir em
> `res/drawable`.

### O bloqueio de países passou a caber

Com 3 associações a separação por país era **impossível por construção**: um grupo
de 4 precisa de 4 países distintos, e sobravam 13 pares de compatriotas. É por isso
que o bloqueio não existia — ele não teria solução.

Com as 10 associações ele passa a caber, e virou a regra que a seção 4.2 descreve.
O que sobra, e por quê, está em **4.3**: no máximo 1 par que a regra proibiria,
resíduo do sorteio ser sequencial.

Resta o limite de contagem que nenhum algoritmo vence: são 8 grupos, então um país
com mais de 8 clubes tem de repetir em algum deles. O Brasil pode chegar a 8 — 5 da
cota, 1 remanejada e os 2 da Pré que sobrevivam —, o que ainda cabe; passando disso,
o conflito é aritmético.

## Estado da implementação

### Pronto

**A lista de acesso** — `ListaDeAcessoLibertadores.kt`, com a aritmética das 47 vagas
travada em teste: as cotas oficiais dos 10 países, as 26 diretas e 19 na Pré, as duas vagas
de campeão vigente com a regra de queda quando o campeão já se classificaria pela liga, e a
estrutura das três fases prévias.

**A redistribuição de vagas** é genérica de propósito: ela recebe as associações que têm
clubes e reparte as 45 vagas proporcionalmente às cotas oficiais. Hoje ela **não age**: com
o pool sul-americano o jogo tem as 10 associações, e a função devolve as cotas oficiais
intactas, sem alteração de código — era exatamente a propriedade para a qual foi escrita.
Ela continua valendo como rede de proteção, para o caso de uma associação ficar sem clubes
elegíveis. Antes do pool, com 3 países, repartia as 28 vagas órfãs preservando a hierarquia
(Brasil ≥ Argentina ≥ Uruguai).

**O sorteio** — `SorteioLibertadores.kt`, nos dois momentos que a competição tem: as
fases prévias e a fase de grupos, com o Ranking de Clubes da CONMEBOL como critério único
de divisão. Potes, mandos, bloqueio de países, a posição A1 do campeão vigente e a exceção
do Pote 4 estão todos na **seção 4**, acima.

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

**Quem concede as vagas são os campeonatos nacionais.** A classificação sai da
**tabela anual** de cada país, somando os pontos de todos os torneios de primeira
divisão daquela associação na temporada que encerrou: no Brasil é a tabela do
Brasileirão Série A; na Argentina, Apertura + Clausura, como a Tabla Anual
distribui as vagas de verdade; no Uruguai, Apertura + Intermediário + Clausura.
Somar é melhor que pegar a melhor colocação entre os torneios, porque é a
regularidade no ano que a classificação anual existe para medir.

Duas associações reservam **uma das suas vagas a um título**, e não à colocação:
o **Brasil** aos finalistas da **Copa do Brasil** e a **Argentina** ao campeão da
**Copa Argentina**. O Uruguai não reserva nenhuma. A vaga sai da cota de grupos
daquele país — não é uma vaga extra —, e quando o detentor não existe, não é clube
daquele país ou já entrou por outra porta, ela **desce para o colocado seguinte**
da liga. É a porta `VAGA_DE_TITULO`, no mesmo molde da vaga de copa da Europa
League.

O Brasil usa **duas**: a do campeão da Copa do Brasil, que sai da cota de grupos,
e a do vice, que sai da cota de **Pré** — é a porta `VAGA_DE_TITULO_PRE`, separada
justamente porque o destino faz parte da vaga. Daí a conta: 1 + **G4** nos grupos,
1 + o **5º** na Pré. Ver a tabela da seção 2 e o efeito cascata.

O campeão vigente da **Libertadores** não entra por essa porta: a vaga dele é a
estrutural de campeão vigente, por fora da cota de qualquer país, e vale também
quando ele é brasileiro. Como a vaga de título do Brasil é da Copa do Brasil, não
há como um clube ocupar as duas.

Na **primeira temporada** nenhum campeonato encerrou e nenhum título foi decidido,
então não há tabela anual nem detentores: a lista de acesso cai no coeficiente e na
reputação, e as vagas de título descem todas para a liga. Da segunda em diante são
os campeonatos nacionais que mandam.

**As duas vagas de campeão vigente**, que o documento chama de estruturais,
ganharam a regra de queda que faltava: quando não têm titular — na primeira
temporada não houve Libertadores, e a Copa Sul-Americana não existe no jogo — elas
**descem para os próximos colocados**, pelo parâmetro `fecharFaseDeGrupos` de
`montar`. Sem isso a fase de grupos nasceria com 30 clubes e a competição nunca
seria criada.

**O desempate:** toda fase eliminatória é de **ida e volta** — as três da Pré e as
oitavas, quartas e semis —, e o empate no agregado vai **direto aos pênaltis**, sem
prorrogação e sem gol fora de casa. Só a **final** é de jogo único, e nela o empate no
tempo normal leva a **30 minutos de prorrogação em dois tempos de 15**; persistindo o
empate, pênaltis.

A prorrogação foi implementada: `SimuladorPartida.simularProrrogacao` joga os minutos
91–105 e 106–120 como dois períodos de verdade, e não um bloco de 30, porque o estado
atravessa o intervalo — um vermelho no primeiro tempo da prorrogação pesa no segundo.
Os gols entram no placar da própria partida, como na súmula, e contam para a
artilharia; só o placar é atualizado, porque o público e a receita já foram creditados
no apito dos 90 minutos.

Quem decide se uma fase tem prorrogação é `FormatoLibertadores.temProrrogacao`, e o
gatilho exige **confronto de jogo único**: é o que separa a final das oitavas, que
usam a mesma competição e o mesmo formato de banco mas são de ida e volta. Pela mesma
razão foi preciso corrigir a regra de pênaltis de `GRUPOS_E_MATA_MATA`, que decidia no
empate de qualquer partida com fase e levaria a **ida** das oitavas aos pênaltis.

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

### O fechamento dos grupos respeita a hierarquia das cotas

As duas vagas de campeão vigente nunca têm titular — a Copa Sul-Americana não
existe no jogo e na primeira temporada não houve Libertadores —, então o
fechamento precisa remanejá-las. **Elas descem respeitando a hierarquia das
cotas:** são oferecidas às associações na ordem da cota (Brasil 7, Argentina 6, os
outros 4), **uma por país**, e cada uma leva o próximo clube da sua fila.

Medido sobre o seed: Brasil e Argentina entram com **6** vagas diretas cada (5 da
cota + 1 remanejada) e os outros oito com 2, fechando os 28. A vaga remanejada tem
porta própria, `VAGA_DE_CAMPEAO_REMANEJADA`, e não se confunde com a cota de liga
— sem isso era impossível ler quantas vagas um país recebeu de verdade pela liga.

> **O que havia antes.** O fechamento ordenava os candidatos restantes pela
> `posicaoLiga`, comparando-a entre países como se fosse a mesma escala. Com ligas
> de tamanhos diferentes o resultado era arbitrário: as duas sobras iam para o 11º
> e o 12º do Uruguai, clubes que não entravam nem na cota uruguaia, enquanto o 12º
> do Brasileirão ia para a Pré. Depois do pool sul-americano as duas passaram a ir
> ao Brasil, que entrava com 7 vagas diretas em vez das 5 da tabela.

### Aproximações registradas na distribuição de vagas

1. **A fase de mata-mata dos torneios argentino e uruguaio não entra na tabela
   anual.** O campeão deles não sobe por ter vencido a final: vale o que somou de
   pontos na fase de grupos.
2. **A soma mistura torneios de tamanhos diferentes**, o que dá mais peso aos mais
   longos — no Uruguai, Apertura e Clausura pesam mais que o Intermediário.

## O que ainda falta

Mantido como registro do que continua valendo como evolução.

1. **Um sorteio com retrocesso**, para a separação por país fechar em zero pares
   quando a composição permite. O sequencial atual deixa no máximo 1 — ver 4.3.
2. **Elencos reais para o pool sul-americano**, clube a clube, em `ROSTERS`.
3. **Escudos do pool** em `res/drawable` (`escudo_res` está vazio nos 42 clubes).
4. **A Copa Sul-Americana**, para onde vão os terceiros de cada grupo. A lista está
   pronta e derivada; falta a competição que a consome — e, com ela, a segunda vaga
   de campeão vigente, que hoje desce para o próximo colocado.
5. **A prorrogação como segmento interativo.** Os 30 minutos são simulados de uma vez
   quando a final do jogador termina empatada; a tela da partida não oferece um
   período de prorrogação para ele mexer no time, como faz no intervalo. O placar
   exibido é o dos 120 minutos, com os gols nos minutos 91 a 120.
