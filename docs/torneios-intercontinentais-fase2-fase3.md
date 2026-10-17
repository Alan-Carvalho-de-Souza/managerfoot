# Torneios Intercontinentais — Plano de Implementação (Fase 2 e Fase 3)

> Documento de design. Base: Libertadores, Champions League e Mundial de Clubes.
> Fase 1 (pool "Outros times") já implementada — ver `memory/outros-times-pool.md`.
> Data: 2026-09-18.

## Contexto (estado atual verificado)

- `TipoCampeonato.CONTINENTAL` e `TipoCampeonato.MUNDIAL_CLUBES` existem no enum
  (`CampeonatoEntity.kt`) mas são **valores mortos**: nunca são criados nem
  simulados; sem telas, sem seleção de participantes, sem partidas.
- O `SaveState`/`GameDataStore` **não tem** campos para esses torneios.
- **As copas já resolvem ~90% do problema.** Os torneios intercontinentais são,
  na prática, "copas com um seletor de participantes diferente".
- Pool "Outros times" (Fase 1): clubes sem liga nacional via `divisao`
  **90** (CONMEBOL) / **91** (UEFA) / **92** (resto). Hoje só o Inter Miami.

### Peças reaproveitáveis (já existem)

| Peça | Onde |
|---|---|
| Gerador mata-mata ida/volta | `MotorCampeonato.gerarFaseIdaVolta` |
| Gerador mata-mata jogo único | `MotorCampeonato.gerarFaseJogoUnico` |
| Avanço de fase ida/volta (agregado) | `GameRepository.verificarEAvancarFaseCopa` |
| Avanço de fase jogo único | `GameRepository.verificarEAvancarFaseCopaInglaterra` |
| Continuação IA quando jogador não participa | `simularProximaFaseCopa*SeJogadorEliminado` |
| Pênaltis IA×IA | `GameRepository.simularEPersistirPenaltisIA` |
| Pênaltis interativos (jogador) | `simularEPersistirPenaltisJogador*` |
| Partida interativa | `iniciarPartidaComJogador` / `finalizarPartidaComJogador` |
| Criação de copa + participantes | `criarCopaInglaterra` / `determinarParticipantesCopaInglaterra` / `criarSupercopa` |
| Registro de título + premiação | `registrarCampeaoCopaInglaterra` / `inserirNoHallDaFamaComTecnico` |
| Chaveamento (UI) | `CopaChaveamentoScreen` |
| Painel "Placares ao vivo" (copas) | `PartidaSimulacaoScreen` (já cobre ida/volta e jogo único) |
| Slots de calendário | arrays `*_ORDEM_GLOBAL` em `MotorCampeonato` (liga usa `rodada*10`) |

---

## Fundação comum (pré-requisito das duas fases)

**A. Mapa de confederação.** Helper `confederacaoDe(pais): Confed` ligando país →
confederação e ao pool:
- Libertadores (CONMEBOL) = Brasil/Argentina/Uruguai + pool `divisao 90`.
- Champions (UEFA) = Inglaterra/Espanha/Itália + pool `divisao 91`.
- Mundial = campeões continentais + convidados (pool `92` / reputação).

**B. Seleção de participantes** (`determinarParticipantesLibertadores`, etc.) —
espelha `determinarParticipantesCopaInglaterra`. Usa o **top-N da tabela final**
de cada liga (`classificacaoDao.buscarTabelaOrdenada` do ano encerrado) + o pool
da confederação. Roda no rollover (posições finais já existem).

**C. Persistência dos IDs.** Novos campos no `SaveState`/`GameDataStore`
(é **DataStore, não Room → sem migração**): `libertadoresId`, `championsId`,
`mundialId`. Fiar em `salvarNovoJogo` e `salvarNovaTemporada` e no load — copiar
exatamente o padrão de `copaInglaterraId`.

**D. Criação no rollover.** Em `encerrarTemporadaComHallDaFama`, ao lado de onde
hoje se cria Copa BR/ARG/ING e Supercopa: `criarLibertadores(...)` etc., e
propagar via `NovaTemporadaInfo` (novos campos), como já se faz com
`novoCopaInglaterraId`.

**E. Calendário (`ordemGlobal`).** Definir `LIBERTADORES_ORDEM_GLOBAL` /
`CHAMPIONS_ORDEM_GLOBAL` com slots que **não colidam** com liga (`rodada*10`) nem
com as copas (`COPA_ORDEM_GLOBAL`, `COPA_ARG_ORDEM_GLOBAL`, `COPA_ING_ORDEM_GLOBAL`).

**F. Hall / Conquistas / Troféus.** Códigos de `divisao` novos para os títulos
(sugestão: **50**=Libertadores, **51**=Champions, **52**=Mundial), reusando
`inserirNoHallDaFamaComTecnico`. Adicionar rótulos/filtros em
`ArtilheirosScreen`, `HallDaFamaScreen`, `TabelaScreen`, `ConquistasScreen`,
`Trofeus.kt`.

**G. Profundidade do pool.** Povoar `gen_outros.py` com mais clubes por
confederação (ex.: 6–10 europeus, 6–10 sul-americanos) para um chaveamento
crível. Hoje só há o Inter Miami.

---

## Fase 2 — Torneio continental **simulado (IA-only)**, ponta a ponta

**Objetivo:** ao fim de cada temporada, um campeão da **Libertadores** é coroado,
aparece no Hall/Conquistas, tudo automático, **sem o usuário jogar**. Valida a
fundação com risco baixo.

**Passos:**

1. **`criarLibertadores(campeonato, participantes)`** — clone de
   `criarCopaInglaterra`. Cria `CampeonatoEntity` (`tipo = CONTINENTAL`) e gera a
   1ª fase com `MotorCampeonato.gerarFaseIdaVolta`.
2. **Formato recomendado: mata-mata ida/volta puro** (16 ou 32 clubes). O motor
   já tem geração + avanço por agregado + pênaltis. **Evitar fase de grupos
   nesta fase**: o `GRUPOS_E_MATA_MATA` atual é o modelo argentino de *duas zonas
   em pontos corridos* (`MotorCampeonato.kt:352`), não um group-stage estilo UCL.
   Grupos ficam como enhancement posterior.
3. **Avanço de fase**: reusar `verificarEAvancarFaseCopa` (ida/volta genérico)
   com as fases/ORDEM_GLOBAL da Libertadores. Empate no agregado →
   `simularEPersistirPenaltisIA` (já existe).
4. **Simulação automática**: sem jogador, roda como
   `simularProximaFaseCopa*SeJogadorEliminado` — a cada avanço de calendário,
   simula os confrontos pendentes e gera a próxima fase.
5. **Coroação**: `registrarCampeaoLibertadores` (clone de
   `registrarCampeaoCopaInglaterra`) grava no Hall (divisao 50), bônus de
   reputação e premiação.

**Entregável:** Libertadores nascendo no rollover, simulada sozinha, campeão no
Hall/Conquistas. **Risco: médio** (maior parte é clonar; novo = seletor de
participantes e slots de calendário).

---

## Fase 3 — **Jogável** + Champions + Mundial + UI

**Objetivo:** quando o clube do usuário se classifica, ele **joga** (como já joga
a Copa da Inglaterra), mais Champions e Mundial, com telas e calendário.

1. **Tornar jogável.** `iniciarPartidaComJogador` / `finalizarPartidaComJogador`
   já tratam `fase`/`confrontoId`, agregado ida/volta e pênaltis do jogador.
   Basta o `DashboardViewModel.finalizarPartidaSimulada` reconhecer o
   `campeonatoId` da Libertadores/Champions (mesmos blocos
   `if (ctx.campeonatoId == save.copaInglaterraId ...)`, com guard
   `!precisaPenaltis`). **Bônus:** o painel "Placares ao vivo" já cobre estes
   torneios (ida/volta / jogo único) automaticamente.
2. **Interleaving do calendário.** Fazer o jogo continental aparecer como
   "próxima partida" nos slots certos entre rodadas de liga — mesmo mecanismo de
   `ordemGlobal` das copas; o trabalho é calibrar os arrays para coexistirem sem
   sobrepor datas.
3. **Champions League (UEFA).** Reusa 100% do motor da Fase 2; muda só
   `determinarParticipantesChampions` (ENG/ESP/ITA + pool 91) e os slots de
   calendário. Custo incremental baixo.
4. **Mundial de Clubes (`MUNDIAL_CLUBES`).** Participantes = campeões
   continentais do ano + convidados (reputação/pool 92). Formato: mata-mata curto
   (`gerarFaseJogoUnico` + pênaltis). **Depende dos campeões continentais já
   definidos** → roda numa janela específica (fim de temporada ou início da
   seguinte).
5. **UI.**
   - Chaveamento: estender `CopaChaveamentoScreen` (já renderiza jogo único e
     ida/volta).
   - Tabela/artilharia/hall: adicionar os novos códigos de `divisao`/campId nos
     `when` dos ViewModels (Tabela/Artilheiros/HallDaFama) e telas.
   - Troféus/Conquistas: mapear títulos em `Trofeus.kt` (cor/ícone) e filtros de
     `ConquistasScreen`.
   - Classificação/seeding, premiação e bônus de reputação por competição.

**Risco: alto** — não pela lógica de jogo (reaproveitada), mas pela quantidade de
pontos de UI e pela calibragem do calendário para 2–3 competições novas
coexistirem com liga + copas sem colisão.

---

## Esforço e sequência sugerida

| Etapa | Conteúdo | Reuso | Risco |
|---|---|---|---|
| Fundação | confederação, participantes, SaveState, rollover, Hall codes, pool | alto | baixo |
| **Fase 2** | Libertadores IA-only ponta a ponta | copas (ida/volta + pênaltis + Hall) | médio |
| **Fase 3a** | Libertadores jogável + calendário | fluxo interativo de copa + placares ao vivo | médio-alto |
| **Fase 3b** | Champions (clone) | motor da Fase 2 | baixo |
| **Fase 3c** | Mundial + UI (chaveamento, tabela, troféus, conquistas) | CopaChaveamentoScreen + `when()` de divisão | alto |

**Sem migração de Room** em nenhuma fase (competições/times/partidas já são
tabelas; só entram campos no DataStore). Vale para **jogos novos**; saves antigos
só veriam a partir de um rollover (ou de um backfill opcional).

---

## Decisões em aberto (necessárias antes da Fase 2)

1. **Formato da Libertadores**: mata-mata ida/volta puro (recomendado — pronto e
   realista) ou já com fase de grupos (mais trabalho/risco)?
2. **Nº de participantes** (16 ou 32) e **vagas por liga** (ex.: top-4 do
   Brasileirão, top-2 de Argentina/Uruguai) + quantos do pool.
3. **Povoar o pool** primeiro: quantos clubes sul-americanos/europeus reais
   adicionar (via `gen_outros.py`) para elenco crível.

---

## Checklist de arquivos a tocar (referência rápida)

- `domain/engine/MotorCampeonato.kt` — novos `*_FASES`, `*_ORDEM_GLOBAL`; reuso de `gerarFaseIdaVolta`/`gerarFaseJogoUnico`.
- `data/repository/GameRepository.kt` — `criarLibertadores`/`criarChampions`/`criarMundial`, `determinarParticipantes*`, `verificarEAvancar*`, `simularProximaFase*SeJogadorEliminado`, `registrarCampeao*`; criação no rollover (`encerrarTemporadaComHallDaFama`, `NovaTemporadaInfo`).
- `data/datasource/GameDataStore.kt` — campos `libertadoresId`/`championsId`/`mundialId` + KEYs + salvar/carregar.
- `data/dao/*` — queries auxiliares se necessário (participantes/hall).
- `presentation/viewmodel/ViewModels.kt` — `DashboardViewModel.finalizarPartidaSimulada` (blocos por competição), Tabela/Artilheiros/Hall (`when` de divisão/campId).
- `presentation/ui/screens/` — `CopaChaveamentoScreen`, `TabelaScreen`, `ArtilheirosScreen`, `HallDaFamaScreen`, `ConquistasScreen`.
- `presentation/ui/util/Trofeus.kt` — troféu/cor dos novos títulos.
- `scripts/gen_outros.py` — povoar o pool por confederação; rodar `merge_seeds.py` + `gen_tecnicos.py`.
