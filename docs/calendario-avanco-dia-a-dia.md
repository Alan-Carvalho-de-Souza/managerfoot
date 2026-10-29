# Avanço dia a dia — o que falta do calendário

Este é o pedaço que ficou de fora da etapa 5. O calendário está pronto e
correto — ver [calendario-da-temporada.md](calendario-da-temporada.md) —, e o
dia já governa a simulação **entre** duas partidas do jogador. O que falta é o
dia aparecer para ele.

Não é correção de defeito: é mecânica de jogo. Só vale a pena se o avanço dia a
dia for algo que você queira que o jogador sinta.

---

## 1. Onde o jogo está hoje

O ciclo é "jogar a minha próxima partida":

```
buscarProximaPartida(timeId)   →  ORDER BY ordemGlobal LIMIT 1
   ↓  o jogador escala e joga
finalizarPartidaComJogador
   ↓
simularRodadaEmOutrosCampeonatos  →  as ligas nacionais ficam em dia
porEmDiaAsContinentais            →  Champions, Europa, Libertadores, Sula
   ↓
verificarEAvancarFase*            →  os mata-matas avançam de fase
```

`AvancoPorDia` já responde a pergunta certa — *o que estava marcado até hoje?* —
e tem as três propriedades que o avanço dia a dia vai precisar: é idempotente,
recupera atraso sozinha e não depende da ordem das competições.

**Os dias entre duas partidas do jogador são atravessados em silêncio.** Ele
nunca vê 12 de março; vê "a próxima partida é dia 15".

---

## 2. O que mudaria

### 2.1 Um "avançar dia" na tela

A unidade de interação deixa de ser a partida e passa a ser o dia:

- **dia com jogo do clube dele** → o fluxo de hoje (escalar, apitar, jogar);
- **dia com jogos de outros** → avança, e os resultados aparecem;
- **dia sem jogo nenhum** → avança, e o que aparece é o que acontece fora de
  campo (treino, mercado, recuperação de lesão, contrato vencendo).

Vale agrupar dias vazios: "avançar até o próximo jogo" tem de continuar sendo um
botão, ou o jogador vai tocar 6 vezes para atravessar uma semana.

### 2.2 Um relógio guardado no save

Hoje o "agora" é derivado: `ordemGlobalAtualDoTime` dá o dia da última partida
do clube. Isso basta para carimbar uma transferência, mas **não** para um jogo
que avança dias vazios — num dia sem jogos o relógio não anda.

Então o `SaveState` ganha um `diaAtual`, e ele passa a ser a fonte:

- ele é quem anda quando o jogador toca "avançar dia";
- `AvancoPorDia` passa a receber ele em vez do dia da partida;
- o carimbo de transferência passa a ler ele, e aí fica certo também na janela
  de janeiro, antes do primeiro jogo (hoje fica em zero e o histórico mostra só
  o mês).

**Para saves em andamento**, o valor inicial é o maior `ordemGlobal` já jogado da
temporada. Não precisa de migração de dados: é derivável na primeira carga.

### 2.3 Os placares ao vivo por dia

`observeRodada(campeonatoId, rodada)` vira algo como
`observeJogosDoDia(temporadaId, dia)`: hoje o painel mostra "os outros jogos da
mesma rodada" e passaria a mostrar "os outros jogos de hoje", incluindo outros
países que jogam no mesmo fim de semana. É o que uma rodada real parece.

### 2.4 O que o dia passa a poder decidir

É aqui que está o ganho de jogabilidade, e é o motivo de a etapa 3 ter garantido
que ninguém jogue em dias seguidos:

| Hoje conta | Passaria a contar |
|---|---|
| partidas disputadas | dias de descanso entre partidas |
| "N jogos de suspensão" | "volta no dia X" |
| lesão em partidas | lesão em dias |
| janela de transferências por mês | janela por data de abertura e fechamento |

---

## 3. O que vai doer

### 3.1 O mata-mata não entra pelo dia

`AvancoPorDia` ignora `fase != null` de propósito: as fases nascem de
`verificarEAvancarFase*` quando a anterior termina, e são **15 funções**. Jogar
uma fase por data atropelaria o encadeamento — a fase seguinte precisa existir
antes de ter data.

A ordem correta continua sendo: a fase termina → o gatilho gera a seguinte com
os dias dela → o avanço por dia joga quando chegar o dia. **Não troque isso por
"joga tudo que tem data"** sem antes separar *gerar* de *jogar* nessas 15
funções; é o trabalho de verdade desta etapa.

### 3.2 Reconciliar competições atrasadas

No primeiro avanço de um save existente, tudo que estava marcado até hoje é
jogado de uma vez. Isso é correto, mas pode significar uma rajada de dezenas de
partidas — e o jogador vai ver um salto. Vale um aviso na tela, ou espalhar a
rajada pelos primeiros avanços.

### 3.3 As temporadas da CURVA

Um save anterior à grade guarda slots, não dias, e **não tem como avançar por
dia**. O avanço dia a dia só pode ser oferecido numa temporada
`CalendarioVersao.GRADE`. Nas outras a tela tem de continuar oferecendo "jogar a
próxima partida" — ou seja, as duas interações convivem por pelo menos uma
temporada de cada save.

### 3.4 Os dias vazios podem ser muitos

Entre 29 de novembro e a Intercontinental de 15 de dezembro há duas semanas sem
quase nada. Sem um "avançar até o próximo jogo", a entressafra vira tédio de
toque.

---

## 4. Uma ordem sugerida

1. **`diaAtual` no `SaveState`**, derivado na primeira carga, ainda sem mudar
   nada na tela. Dá para conferir que ele bate com o dia da última partida.
2. **Trocar a fonte do avanço**: `AvancoPorDia` passa a receber `diaAtual` em
   vez do dia da partida do jogador. Comportamento idêntico, fonte diferente.
3. **O carimbo de transferência** passa a ler `diaAtual` — e aí a janela de
   janeiro ganha dia exato.
4. **Separar gerar de jogar** nas 15 `verificarEAvancarFase*`: gerar quando a
   fase anterior termina, jogar quando chegar o dia. É o passo caro.
5. **Os placares ao vivo por dia.**
6. **O botão "avançar dia"** e o "avançar até o próximo jogo", só numa temporada
   da grade.
7. **Descanso, suspensão e lesão em dias**, que é o que justifica tudo isto.

Os passos 1 a 3 são baratos e não mudam nada do que o jogador vê — dá para
pará-los ali e colher o ganho do carimbo em janeiro. O passo 4 é a fronteira: a
partir dele a mudança é de mecânica, não de encanamento.

---

## 5. O que NÃO precisa mudar

- **O calendário.** Os dias das 26 competições estão corretos e testados.
- **A escala gravada.** `ordemGlobal` já é o dia; não há migração nova.
- **A tradução.** `CalendarioDeSlots` continua fazendo o que faz.
- **A grade.** As colisões já são impossíveis por construção.
