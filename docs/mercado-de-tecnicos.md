# Técnicos: ciclo de vida e mercado

Referência de como os técnicos funcionam hoje no ManagerFoot — o que está
implementado, onde cada regra mora, e o que ainda falta. Serve para quem for
mexer nisto depois não ter de reconstruir o raciocínio.

---

## 1. O mundo: 363 técnicos para 363 clubes

`app/src/main/assets/seed_data.json` traz **363 técnicos, todos empregados,
nenhum livre** — um por clube, incluindo os clubes do pool sem liga (divisões
90/91/92). Idades de 30 a 71, reputação de 30 a 75,9.

Quando o jogador começa uma carreira, `cadastrarTecnicoUsuario` cria uma entrada
NOVA para ele e libera o técnico que estava no clube escolhido. O mundo fica com
**364 técnicos e 363 vagas: exatamente um agente livre.**

Esse número é a chave para entender quase tudo o que havia de errado antes, e
por que as regras de hoje são como são. **Se você mudar o seed, releia esta
seção**: várias decisões abaixo assumem um mercado sem folga.

---

## 2. Estado persistido

### `TecnicoEntity` (tabela `tecnicos`)

| Campo | Observação |
|---|---|
| `timeId` | `null` = livre no mercado |
| `idade` | incrementada na virada de temporada |
| `nacionalidade` | ~65 valores; usada para a preferência por compatriota |
| `reputacao` | 0..100; sobe e desce (ver §5) |
| `salario` | centavos; derivada só da reputação do CLUBE (ver §8, pendência) |
| `contratoAnos` | **gravado e nunca lido** (ver §8, pendência) |
| `controladoPorJogador` | o técnico do usuário; imune a demissão, assédio e aposentadoria |
| `aposentado` | carreira encerrada; fora de qualquer contratação |

**Por que o aposentado não é apagado:** o Hall da Fama (`buscarTitulosPorTecnico`)
e as passagens apontam para o `id`. Apagar a linha levaria os títulos junto. Ele
some das contratações mas continua no ranking e no histórico dos clubes.

### `PassagemTecnicoEntity` (tabela `passagens_tecnicos`)

Uma por período do técnico num clube, com `ativa = true` para a atual.

> **Armadilha:** `buscarPassagemAtiva` faz `LIMIT 1`. Um técnico com duas
> passagens ativas devolve qualquer uma das duas, e os resultados das partidas
> passam a ser creditados na passagem errada — silenciosamente. É por isso que
> `contratar()` encerra a passagem anterior do CONTRATADO, e não só a do técnico
> que estava no clube de destino. Qualquer caminho novo que mude um técnico de
> clube precisa fazer o mesmo.

### Migração

`aposentado` entrou na **v35** (`MIGRATION_34_35`, `AppDatabase.kt`).

---

## 3. Quem decide o quê

| Onde | Natureza | Responsabilidade |
|---|---|---|
| `domain/engine/MercadoDeTecnicos.kt` | pura, testada | assédio em cascata, delta de reputação, aposentadoria |
| `domain/engine/NomesDeTecnicos.kt` | pura, testada | nome dos técnicos novos |
| `data/repository/TecnicoRepository.kt` | I/O | aplica as decisões, contratos, passagens |
| `data/repository/GameRepository.kt` | orquestração | **a ordem** das etapas na virada |

A regra que vale manter: **decisão na engine, gravação no repository.** A janela
de mercado inteira é uma função pura que recebe clubes e técnicos e devolve uma
lista de contratações; o repositório só a aplica.

---

## 4. O assédio em cascata (a janela de fim de temporada)

`MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sorteio)`

Clube não tira técnico de um pool: tira de **outro clube**. Os clubes são
percorridos **do mais forte para o mais fraco**, e um clube só pode assediar
quem está num clube pelo menos `MARGEM_ASSEDIO` abaixo dele.

Duas propriedades caem de graça dessa regra, e os testes travam as duas:

- **A cascata se arma sozinha.** O clube assediado tem reputação menor, logo vem
  DEPOIS na ordem; quando a iteração chega nele, ele está vago e vai buscar em
  alguém menor ainda. Não é preciso fila.
- **Ela termina.** O assédio só anda para baixo e cada clube é visitado uma vez,
  então não há ciclo: ninguém rouba de volta de quem já se serviu.

E o número de clubes sem técnico **nunca cresce**: cada assédio move a vaga para
baixo, e o técnico substituído volta ao mercado para quem vier depois.

### Constantes

| Constante | Valor | Para quê |
|---|---|---|
| `MARGEM_ASSEDIO` | 8 | o quanto o clube precisa superar o clube assediado |
| `MARGEM_UPGRADE` | 8 | o quanto o alvo precisa superar o técnico atual |
| `TETO_ACIMA_DO_CLUBE` | 10 | evita um nome grande dirigindo a Série D |
| `PROB_TROCA_POR_UPGRADE` | 0,35 | nem todo clube se mexe todo ano |

### Regras de decisão

- Clube **vago** contrata sempre que houver candidato compatível — é a vaga
  descendo a cascata. Não passa pelo sorteio.
- Clube **com técnico** só troca por um upgrade de mais de `MARGEM_UPGRADE`, e
  ainda assim passa pelo sorteio.
- Entre candidatos de mesma qualidade, prefere o **compatriota**.
- O clube do usuário e o técnico do usuário ficam fora de tudo.

O `sorteio` só é consumido quando há de fato uma decisão a tomar, o que torna a
janela reproduzível nos testes. Empates de reputação são desempatados por id,
pelo mesmo motivo.

---

## 5. Reputação

| Evento | Delta | Onde |
|---|---|---|
| Título | `+5%` da reputação, mínimo 1 | `registrarTitulo` |
| Meta da temporada (só o técnico do usuário) | −5 a +6 | `GameRepository`, avaliação de meta |
| Aproveitamento ≥ 0,70 | +3,0 | `deltaDeReputacao` |
| Aproveitamento ≥ 0,55 | +1,5 | " |
| Aproveitamento ≥ 0,40 | 0 | " |
| Aproveitamento ≥ 0,30 | −1,5 | " |
| Aproveitamento < 0,30 | −3,0 | " |
| Rebaixado | −4,0 (soma) | " |
| Demitido | −3,0 (soma) | " |

**Por que o delta por desempenho existe:** antes dele, a reputação do técnico da
IA só SUBIA — os únicos ajustes eram o bônus de título e o delta de meta, e esse
delta só alcançava o técnico do jogador. Em poucas temporadas todo mundo
convergia para o topo e os filtros de compatibilidade (`+8`, `+10`) deixavam de
separar qualquer coisa. **Qualquer regra nova que dê reputação precisa vir com o
sumidouro correspondente.**

Aplicado em `demitirPorDesempenho`, que tem os dados da temporada à mão. Precisa
rodar **antes** de `resetarStatsTemporada`.

---

## 6. Fim de carreira e renovação

`envelhecerEAposentarTecnicos(anoFim)`:

1. `envelhecerUmAno()` — todos os não-aposentados ganham um ano, o do usuário
   inclusive.
2. Para cada técnico em atividade que não seja o do usuário,
   `MercadoDeTecnicos.deveAposentar(idade)` decide.
3. Quem se aposenta sai do clube (passagem encerrada), ganha `aposentado = 1`, e
   **gera um substituto da mesma nacionalidade**, livre, 36–48 anos, reputação
   30–42.

| Faixa | Chance de encerrar a carreira |
|---|---|
| < 64 | 0 |
| 64..71 | 8% por ano acima de 63 (64 → 8%, 71 → 64%) |
| ≥ 72 | compulsória |

**A reposição é 1 para 1 de propósito.** Nada nunca criava um técnico depois do
seed; sem ela, cada aposentadoria seria um clube a menos com comando até o mundo
esvaziar. Manter a nacionalidade mantém a distribuição por país estável — um
clube italiano continua achando técnicos italianos em 2050.

A rampa de probabilidade existe para os clubes não perderem o comando todos no
mesmo ano. O seed tem técnicos de até 71 anos, então as primeiras aposentadorias
acontecem já na primeira virada.

---

## 7. A ORDEM na virada de temporada

Em `GameRepository`, no encerramento. **Esta ordem é regra, não acaso:**

```
1. demitirPorDesempenho         (demite + aplica o delta de reputação)
2. envelhecerEAposentarTecnicos (abre vagas e cria os substitutos)
3. resetarStatsTemporada
4. movimentarMercadoTecnicosFimTemporada   ← assédio
5. preencherVagasComTecnicosLivres         ← rede de segurança
```

- **1 antes de 3** porque o delta de reputação lê as estatísticas da temporada.
- **2 depois de 1** para que quem se aposenta ainda seja avaliado pelo que fez, e
  **antes de 4** para que as vagas que ela abre entrem na mesma janela.
- **4 antes de 5** — este é o ponto todo. Antes era o contrário: o preenchimento
  de vagas esvaziava o pool de livres e a janela de mercado saía no primeiro
  `break` por não achar ninguém, quase todo ano. A função existia e não
  executava.

Se alguém reordenar isto, o mercado volta a congelar sem nenhum teste quebrar —
a ordem vive no `GameRepository`, fora do alcance dos testes de unidade.

---

## 8. O que ainda falta

### Contrato que vence (não implementado)

`contratoAnos` é gravado em toda contratação (sempre 2) e **nunca lido**. Não há
decremento nem expiração — o equivalente dos jogadores existe e está ligado
(`JogadorDao.decrementarContratos`, chamado no rollover), mas técnico não tem.

Na vida real, contrato vencendo é o principal canal de troca **sem demissão**.
Implementar: decrementar na virada; em 0, renovar (desempenho bom) ou liberar. É
isso que cria folga real no pool a cada temporada sem inventar gente. A engine
de assédio já tem onde encaixar o peso do contrato restante na escolha do alvo
(contrato curto = mais barato de tirar).

### Salário em função do técnico (não implementado)

`salarioPara(reputacaoDoClube)` = `150.000 + (repClube − 30) × 50.000` centavos.
**Depende só do clube:** dois técnicos de reputação 40 e 75 ganham igual no mesmo
lugar. Sem isso não há disputa salarial de verdade — um clube grande não "ganha"
um técnico, só espera que ele esteja disponível. O filtro `TETO_ACIMA_DO_CLUBE`
hoje faz o papel de barreira econômica, e é uma aproximação grosseira.

### Assédio no meio da temporada

`trocarTecnicoPorCrise` continua contratando **apenas de quem está livre** —
deliberadamente. Assédio no meio do ano deixaria o clube de origem sem comando
no meio da competição, e um clube sem técnico para de acumular estatísticas em
silêncio (`registrarResultadoPartida` retorna cedo quando `buscarPorTime` é
`null`). Para fazer isso, a troca teria de ser uma cadeia fechada: o clube em
crise leva o técnico do menor, e o menor leva o recém-demitido na mesma operação.

### Outras pontas soltas

- A reputação do clube influencia o salário, mas o salário do técnico não entra
  em nenhum cálculo financeiro do clube.
- `observeRanking` mostra aposentados junto dos ativos, sem distinção visual.
- `buscarTodos()` ainda existe e inclui aposentados; para qualquer caminho de
  CONTRATAÇÃO use `buscarEmAtividade()`.

---

## 9. Testes

| Arquivo | Cobre |
|---|---|
| `MercadoDeTecnicosTest` | cascata, não-subida, conservação de vagas, margens, imunidade do usuário, sorteio, determinismo, reputação, aposentadoria |
| `NomesDeTecnicosTest` | pools por nacionalidade, abreviação, nacionalidade desconhecida |

Os testes mais importantes são os **invariantes**, não os casos: a cascata nunca
sobe, cada clube é servido no máximo uma vez, e o número de clubes sem técnico
nunca cresce. São eles que seguram uma mudança futura nas constantes.

O que os testes **não** cobrem: a ordem do §7 e tudo o que passa pelo Room.
