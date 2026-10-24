package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.CampeonatoEntity
import br.com.managerfoot.data.database.entities.ClassificacaoEntity
import br.com.managerfoot.data.database.entities.FormatoCampeonato
import br.com.managerfoot.data.database.entities.PartidaEntity
import br.com.managerfoot.domain.model.ResultadoPartida

// ─────────────────────────────────────────────
//  MotorCampeonato
//  Gera o calendário de partidas e processa
//  resultados para atualizar a classificação.
// ─────────────────────────────────────────────
object MotorCampeonato {

    // ── Fases da Copa do Brasil (em ordem) — 64 times ───────────────
    val COPA_FASES = listOf(
        "Primeira Fase",
        "Segunda Fase",
        "Oitavas",
        "Quartas",
        "Semi",
        "Final"
    )

    // ── Fases da Copa Argentina (em ordem) — 32 times ────────────────
    // Primeira Fase (32→16), Oitavas (16→8), Quartas (8→4), Semi (4→2), Final (2→1)
    val COPA_ARG_FASES = listOf(
        "Primeira Fase",
        "Oitavas",
        "Quartas",
        "Semi",
        "Final"
    )

    // Retorna rodadaIda de uma fase (0-indexed faseIndex)
    fun rodadaIdaDeFase(faseIndex: Int) = faseIndex * 2 + 1

    // ordemGlobal de cada jogo da Copa do Brasil no calendário multi-competição.
    // Brasileirão usa ordemGlobal = rodada * 10 (10, 20, 30 ...) → Copa encaixa em valores entre rodadas.
    // Índices: 0=F0-ida, 1=F0-volta, 2=F1-ida, 3=F1-volta, ... 11=Final-volta
    // Copa encaixa entre as rodadas do Brasileirão (ordemGlobal = rodada*10):
    //   Primeira Fase ─ Fev:  entre R1 (10) e R4 (40)   → 13, 33
    //   Segunda Fase  ─ Mar/Abr: entre R5 (50) e R9 (90)  → 53, 83
    //   Oitavas       ─ Mai/Jun: entre R13(130) e R17(170) → 133, 163
    //   Quartas       ─ Jul/Ago: entre R20(200) e R24(240) → 207, 233
    //   Semifinal     ─ Set/Out: entre R27(270) e R32(320) → 278, 313
    //   Final         ─ Nov/Dez: entre R35(350) e fim      → 357, 383
    val COPA_ORDEM_GLOBAL = intArrayOf(13, 33, 53, 83, 133, 163, 207, 233, 278, 313, 357, 383)

    // ordemGlobal de cada jogo da Copa Argentina no calendário Argentine.
    // Argentine calendar: Apertura OG 10–200 (rodada*10), Clausura OG 220–410 (rodada*10+210).
    // Índices: 0=PF-ida, 1=PF-volta, 2=Oitavas-ida, 3=Oitavas-volta,
    //          4=Quartas-ida, 5=Quartas-volta, 6=Semi-ida, 7=Semi-volta, 8=Final-ida, 9=Final-volta
    //   Primeira Fase ─ após Apertura R2  (OG 20)
    //   Oitavas       ─ após Apertura R8  (OG 80)
    //   Quartas       ─ após Apertura R14 (OG 140)
    //   Semifinal     ─ gap Apertura-Clausura (OG 200–220)
    //   Final         ─ após Clausura R20 (OG 420)
    val COPA_ARG_ORDEM_GLOBAL = intArrayOf(25, 45, 85, 115, 145, 175, 205, 215, 415, 425)

    // ── Fases da Copa da Inglaterra (FA Cup) — 32 times, JOGO ÚNICO ──
    // Primeira Fase (32→16), Oitavas (16→8), Quartas (8→4), Semi (4→2), Final (2→1)
    val COPA_ING_FASES = listOf(
        "Primeira Fase",
        "Oitavas",
        "Quartas",
        "Semi",
        "Final"
    )

    // ordemGlobal de cada fase (jogo único: 1 valor por fase). A Premier usa
    // ordemGlobal = rodada*10 (10..380); as fases da copa se encaixam entre as
    // rodadas do campeonato, distribuídas ao longo da temporada.
    //   Primeira Fase ─ após R3  (35)
    //   Oitavas       ─ após R9  (95)
    //   Quartas       ─ após R16 (165)
    //   Semifinal     ─ após R25 (255)
    //   Final         ─ após R33 (335)
    val COPA_ING_ORDEM_GLOBAL = intArrayOf(35, 95, 165, 255, 335)

    // ── Fases das copas de 16 times (Copa do Rei / Coppa Italia) — IDA/VOLTA ──
    // Começam nas Oitavas: Oitavas (16→8), Quartas (8→4), Semi (4→2), Final (2→1).
    // Mesmo modelo ida/volta da Copa do Brasil.
    val COPA16_FASES = listOf(
        "Oitavas",
        "Quartas",
        "Semi",
        "Final"
    )

    // ordemGlobal de cada jogo (4 fases × ida/volta = 8 valores). La Liga e Serie A
    // usam ordemGlobal = rodada*10 (10..380); as fases encaixam entre as rodadas.
    // Um clube só disputa a copa do seu país, então Espanha e Itália podem
    // compartilhar os mesmos slots (não há colisão entre times).
    //   Oitavas ─ ida após R9 (95),  volta após R11 (115)
    //   Quartas ─ ida após R16 (165), volta após R18 (185)
    //   Semi    ─ ida após R24 (245), volta após R26 (265)
    //   Final   ─ ida após R32 (325), volta após R34 (345)
    val COPA16_ORDEM_GLOBAL = intArrayOf(95, 115, 165, 185, 245, 265, 325, 345)

    fun proximaFaseCopa(faseAtual: String, fases: List<String> = COPA_FASES): String? {
        val idx = fases.indexOf(faseAtual)
        return if (idx >= 0 && idx < fases.size - 1) fases[idx + 1] else null
    }

    // ── Geração de fase mata-mata ida e volta ─────────────────────
    fun gerarFaseIdaVolta(
        campeonatoId: Int,
        pares: List<Pair<Int, Int>>,   // (timeMandanteIda, timeVisitanteIda)
        fase: String,
        rodadaIda: Int,
        confrontoIdInicio: Int,
        ordemGlobalIda: Int = 0,       // posição no calendário global (buscarProximaPartida)
        ordemGlobalVolta: Int = 0
    ): List<PartidaEntity> {
        val partidas = mutableListOf<PartidaEntity>()
        pares.forEachIndexed { i, (casa, fora) ->
            val confId = confrontoIdInicio + i
            // Jogo de ida: casa joga em casa
            partidas.add(
                PartidaEntity(
                    campeonatoId = campeonatoId,
                    rodada = rodadaIda,
                    timeCasaId = casa,
                    timeForaId = fora,
                    fase = fase,
                    confrontoId = confId,
                    ordemGlobal = ordemGlobalIda
                )
            )
            // Jogo de volta: fora joga em casa (mandante invertido)
            partidas.add(
                PartidaEntity(
                    campeonatoId = campeonatoId,
                    rodada = rodadaIda + 1,
                    timeCasaId = fora,
                    timeForaId = casa,
                    fase = fase,
                    confrontoId = confId,
                    ordemGlobal = ordemGlobalVolta
                )
            )
        }
        return partidas
    }

    // ── Geração de fase mata-mata JOGO ÚNICO ──────────────────────
    // Uma partida por confronto; empate é decidido nos pênaltis (fora da engine).
    fun gerarFaseJogoUnico(
        campeonatoId: Int,
        pares: List<Pair<Int, Int>>,
        fase: String,
        rodada: Int,
        confrontoIdInicio: Int,
        ordemGlobal: Int = 0
    ): List<PartidaEntity> = pares.mapIndexed { i, (casa, fora) ->
        PartidaEntity(
            campeonatoId = campeonatoId,
            rodada = rodada,
            timeCasaId = casa,
            timeForaId = fora,
            fase = fase,
            confrontoId = confrontoIdInicio + i,
            ordemGlobal = ordemGlobal
        )
    }

    // ── Sorteia pares para uma fase mata-mata ─────────────────────
    fun sortearPares(participantes: List<Int>): List<Pair<Int, Int>> {
        val shuffled = participantes.shuffled()
        return (shuffled.indices step 2).map { i ->
            Pair(shuffled[i], shuffled.getOrElse(i + 1) { shuffled[0] })
        }
    }

    // ── Determina vencedor de um confronto de ida+volta ───────────
    // timeCasaIdaId joga em casa no jogo de ida (= fora no jogo de volta)
    // timeForaIdaId joga fora no jogo de ida (= casa no jogo de volta)
    // Retorna null quando o agregado está empatado e pênaltis são necessários.
    fun determinarVencedorTie(
        timeCasaIdaId: Int,
        timeForaIdaId: Int,
        golsCasaIda: Int,
        golsForaIda: Int,
        golsCasaVolta: Int,
        golsForaVolta: Int
    ): Int? {
        // Gols do time A (casa na ida): golsCasaIda + golsForaVolta
        val golsA = golsCasaIda + golsForaVolta
        // Gols do time B (fora na ida): golsForaIda + golsCasaVolta
        val golsB = golsForaIda + golsCasaVolta

        if (golsA > golsB) return timeCasaIdaId
        if (golsB > golsA) return timeForaIdaId

        // Agregado empatado → pênaltis necessários
        return null
    }

    // ── Geração de calendário (pontos corridos - turno e returno) ──
    fun gerarCalendarioPontosCorridos(
        campeonatoId: Int,
        participantesIds: List<Int>
    ): List<PartidaEntity> {
        val times = participantesIds.toMutableList()
        val n = times.size
        val totalRodadas = (n - 1) * 2 // turno + returno
        val partidas = mutableListOf<PartidaEntity>()

        // Algoritmo round-robin (rotação de um time fixo)
        if (n % 2 != 0) times.add(-1) // "bye" se ímpar

        val metade = times.size / 2
        val rodadaTurno = mutableListOf<List<Pair<Int, Int>>>()

        repeat(times.size - 1) { rodada ->
            val jogos = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until metade) {
                val casa = times[i]
                val fora = times[times.size - 1 - i]
                if (casa != -1 && fora != -1) {
                    if (rodada % 2 == 0) jogos.add(Pair(casa, fora))
                    else jogos.add(Pair(fora, casa))
                }
            }
            rodadaTurno.add(jogos)
            // Rotacionar (mantém times[0] fixo)
            val ultimo = times.removeAt(times.size - 1)
            times.add(1, ultimo)
        }

        // Turno
        rodadaTurno.forEachIndexed { idx, rodadaJogos ->
            val rodada = idx + 1
            rodadaJogos.forEach { (casa, fora) ->
                partidas.add(PartidaEntity(
                    campeonatoId = campeonatoId,
                    rodada = rodada,
                    timeCasaId = casa,
                    timeForaId = fora,
                    ordemGlobal = rodada * 10
                ))
            }
        }

        // Returno (inverte mandante/visitante)
        rodadaTurno.forEachIndexed { idx, rodadaJogos ->
            val rodada = idx + 1 + (totalRodadas / 2)
            rodadaJogos.forEach { (casa, fora) ->
                partidas.add(PartidaEntity(
                    campeonatoId = campeonatoId,
                    rodada = rodada,
                    timeCasaId = fora,   // invertido
                    timeForaId = casa,
                    ordemGlobal = rodada * 10
                ))
            }
        }

        return partidas
    }

    // ── Classificação inicial zerada ──
    fun gerarClassificacaoInicial(
        campeonatoId: Int,
        participantesIds: List<Int>,
        grupo: String? = null
    ): List<ClassificacaoEntity> =
        participantesIds.map { timeId ->
            ClassificacaoEntity(
                campeonatoId = campeonatoId,
                timeId = timeId,
                grupo = grupo
            )
        }

    // ── Calcula delta para atualizar classificação após uma partida ──
    data class DeltaClassificacao(
        val timeId: Int,
        val v: Int, val e: Int, val d: Int,
        val gp: Int, val gc: Int
    )

    fun calcularDelta(resultado: ResultadoPartida): Pair<DeltaClassificacao, DeltaClassificacao> {
        val golsCasa = resultado.golsCasa
        val golsFora = resultado.golsFora

        val deltaCasa = DeltaClassificacao(
            timeId = resultado.timeCasaId,
            v = if (golsCasa > golsFora) 1 else 0,
            e = if (golsCasa == golsFora) 1 else 0,
            d = if (golsCasa < golsFora) 1 else 0,
            gp = golsCasa,
            gc = golsFora
        )
        val deltaFora = DeltaClassificacao(
            timeId = resultado.timeForaId,
            v = if (golsFora > golsCasa) 1 else 0,
            e = if (golsFora == golsCasa) 1 else 0,
            d = if (golsFora < golsCasa) 1 else 0,
            gp = golsFora,
            gc = golsCasa
        )
        return Pair(deltaCasa, deltaFora)
    }

    // ── Determina times promovidos/rebaixados ao fim do campeonato ──
    data class DesfechoCampeonato(
        val campeao: Int?,
        val promovidos: List<Int>,
        val rebaixados: List<Int>,
        val vagasContinental: List<Int>   // Libertadores / Sul-Americana
    )

    fun calcularDesfecho(
        campeonato: CampeonatoEntity,
        tabela: List<ClassificacaoEntity>
    ): DesfechoCampeonato {
        val ordenada = tabela.sortedWith(
            compareByDescending<ClassificacaoEntity> { it.pontos }
                .thenByDescending { it.vitorias }
                .thenByDescending { it.saldoGols }
                .thenByDescending { it.golsPro }
        )

        return when (campeonato.tipo) {
            br.com.managerfoot.data.database.entities.TipoCampeonato.NACIONAL_DIVISAO1 -> DesfechoCampeonato(
                campeao = ordenada.firstOrNull()?.timeId,
                promovidos = emptyList(),
                rebaixados = ordenada.takeLast(4).map { it.timeId },
                vagasContinental = ordenada.take(6).map { it.timeId }
            )
            br.com.managerfoot.data.database.entities.TipoCampeonato.NACIONAL_DIVISAO2 -> DesfechoCampeonato(
                campeao = ordenada.firstOrNull()?.timeId,
                promovidos = ordenada.take(4).map { it.timeId },
                rebaixados = ordenada.takeLast(4).map { it.timeId },
                vagasContinental = emptyList()
            )
            br.com.managerfoot.data.database.entities.TipoCampeonato.NACIONAL_DIVISAO3 -> DesfechoCampeonato(
                campeao = ordenada.firstOrNull()?.timeId,
                promovidos = ordenada.take(4).map { it.timeId },
                rebaixados = ordenada.takeLast(4).map { it.timeId },
                vagasContinental = emptyList()
            )
            br.com.managerfoot.data.database.entities.TipoCampeonato.NACIONAL_DIVISAO4 -> DesfechoCampeonato(
                campeao = ordenada.firstOrNull()?.timeId,
                promovidos = ordenada.take(4).map { it.timeId },
                rebaixados = emptyList(), // Série D não tem rebaixamento
                vagasContinental = emptyList()
            )
            else -> DesfechoCampeonato(
                campeao = ordenada.firstOrNull()?.timeId,
                promovidos = emptyList(),
                rebaixados = emptyList(),
                vagasContinental = emptyList()
            )
        }
    }

    // ── Texto descritivo do resultado ──
    fun descricaoResultado(resultado: ResultadoPartida): String {
        val g = "${resultado.golsCasa} x ${resultado.golsFora}"
        return when {
            resultado.golsCasa > resultado.golsFora -> "Vitória do mandante [$g]"
            resultado.golsCasa < resultado.golsFora -> "Vitória do visitante [$g]"
            else -> "Empate [$g]"
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Formato Argentine Apertura / Clausura  (GRUPOS_E_MATA_MATA)
    //  30 teams → Zona A (15) + Zona B (15)
    //  Fase de grupos: 14 rodadas intra-zona (turno único, cap) + 2 interzonais = 16 total
    //  Fase eliminatória: Oitavas (R17) → Quartas (R18) → Semi (R19) → Final (R20)
    //  Apertura: ordemGlobal = rodada * 10          (OG 10–200)
    //  Clausura:  ordemGlobal = rodada * 10 + 210   (OG 220–410)
    // ══════════════════════════════════════════════════════════════
    const val ARG_GRUPO_A              = "A"
    const val ARG_GRUPO_B              = "B"
    const val ARG_ROUNDS_ZONA          = 14   // rounds for turno único in each 15-team zone (capped)
    const val ARG_ROUNDS_GROUP_TOTAL   = 16   // 14 (zona) + 2 (interzonais)
    const val ARG_ROUND_KNOCKOUT_START = 17   // first knockout rodada
    const val ARG_TOTAL_ROUNDS         = 20   // total rodadas per tournament
    const val ARG_CLAUSURA_OG_OFFSET   = 210  // added to rodada*10 for Clausura

    val ARG_FASES_KNOCKOUT = listOf("Oitavas", "Quartas", "Semi", "Final")

    /**
     * Generates single round-robin (turno only) for one group of N teams.
     * For odd N a dummy bye (-1) is injected so every possible pair meets exactly once.
     * Matches for round [rodadaBase + 0 .. rodadaBase + min(N-1, maxRodadas)-1] are produced.
     * ordemGlobal = rodada * 10 + ogOffset
     */
    fun gerarTurnoUnicoGrupo(
        campeonatoId: Int,
        participantes: List<Int>,
        rodadaBase: Int = 1,
        ogOffset: Int = 0,
        maxRodadas: Int = Int.MAX_VALUE
    ): List<PartidaEntity> {
        val times = participantes.toMutableList()
        val partidas = mutableListOf<PartidaEntity>()
        if (times.size % 2 != 0) times.add(-1)          // bye for odd N

        val metade    = times.size / 2
        val numRodadas = minOf(times.size - 1, maxRodadas)

        repeat(numRodadas) { idx ->
            val rodada = rodadaBase + idx
            val og     = rodada * 10 + ogOffset
            for (i in 0 until metade) {
                val teamA = times[i]
                val teamB = times[times.size - 1 - i]
                if (teamA != -1 && teamB != -1) {
                    // Alternate home/away each round
                    val (casa, fora) = if (idx % 2 == 0) teamA to teamB else teamB to teamA
                    partidas.add(PartidaEntity(campeonatoId = campeonatoId, rodada = rodada,
                        timeCasaId = casa, timeForaId = fora, ordemGlobal = og))
                }
            }
            val ultimo = times.removeAt(times.size - 1)
            times.add(1, ultimo)
        }
        return partidas
    }

    /**
     * Generates 2 interzonal rounds starting at [rodadaBase]:
     *   Round +0: fixed rival pairings  (grupoA[i] vs grupoB[i])
     *   Round +1: shuffled secondary pairings
     */
    fun gerarInterzonais(
        campeonatoId: Int,
        grupoA: List<Int>,
        grupoB: List<Int>,
        rodadaBase: Int,
        ogOffset: Int = 0
    ): List<PartidaEntity> {
        require(grupoA.size == grupoB.size)
        val partidas = mutableListOf<PartidaEntity>()

        // Round 1 – rival (A[i] hosts B[i])
        val r1 = rodadaBase
        val og1 = r1 * 10 + ogOffset
        grupoA.forEachIndexed { i, teamA ->
            partidas.add(PartidaEntity(campeonatoId = campeonatoId, rodada = r1,
                timeCasaId = teamA, timeForaId = grupoB[i], ordemGlobal = og1))
        }

        // Round 2 – random secondary (B[j] hosts A[j], shuffled independently)
        val r2  = rodadaBase + 1
        val og2 = r2 * 10 + ogOffset
        val shA = grupoA.shuffled()
        val shB = grupoB.shuffled()
        shA.forEachIndexed { i, teamA ->
            partidas.add(PartidaEntity(campeonatoId = campeonatoId, rodada = r2,
                timeCasaId = shB[i], timeForaId = teamA, ordemGlobal = og2))
        }
        return partidas
    }

    /**
     * Generates Oitavas bracket for Argentine knockout.
     * Cross-seeding: A1-B8, A2-B7, A3-B6, A4-B5 (A side hosts);
     *                B1-A8, B2-A7, B3-A6, B4-A5 (B side hosts)
     * top8A sorted best→worst; top8B sorted best→worst.
     */
    fun gerarOitavasArgentina(
        campeonatoId: Int,
        top8A: List<Int>,
        top8B: List<Int>,
        rodada: Int,
        ogOffset: Int = 0
    ): List<PartidaEntity> {
        require(top8A.size == 8 && top8B.size == 8)
        val og = rodada * 10 + ogOffset
        return buildList {
            for (i in 0 until 4) {
                add(PartidaEntity(campeonatoId = campeonatoId, rodada = rodada,
                    timeCasaId = top8A[i], timeForaId = top8B[7 - i],
                    fase = "Oitavas", ordemGlobal = og))
            }
            for (i in 0 until 4) {
                add(PartidaEntity(campeonatoId = campeonatoId, rodada = rodada,
                    timeCasaId = top8B[i], timeForaId = top8A[7 - i],
                    fase = "Oitavas", ordemGlobal = og))
            }
        }
    }

    /**
     * Generates next knockout phase by pairing winners consecutively.
     * Winner[0] (home) vs Winner[1], Winner[2] (home) vs Winner[3], ...
     */
    fun gerarProximaFaseKnockoutArgentina(
        campeonatoId: Int,
        vencedores: List<Int>,
        rodada: Int,
        fase: String,
        ogOffset: Int = 0
    ): List<PartidaEntity> {
        val og = rodada * 10 + ogOffset
        return buildList {
            var i = 0
            while (i + 1 < vencedores.size) {
                add(PartidaEntity(campeonatoId = campeonatoId, rodada = rodada,
                    timeCasaId = vencedores[i], timeForaId = vencedores[i + 1],
                    fase = fase, ordemGlobal = og))
                i += 2
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Formato Uruguaio Apertura / Intermediário / Clausura
    //  16 times na Primera División
    //  Apertura:      turno único (15 rods)  OG = rodada*10           (10–150)
    //  Intermediário: 2 grupos de 8, turno único (7 rods) + Final(R8) OG = rodada*10+155 (165–235)
    //  Clausura:      turno único invertido  (15 rods)  OG = rodada*10+250 (260–400)
    //  Playoff:       Semi (R16 OG 411), Final (R17 OG 421) — armazenados no Clausura
    // ══════════════════════════════════════════════════════════════
    const val URU_ROUNDS_APERTURA       = 15   // 16 teams, turno único (N-1)
    const val URU_ROUNDS_INTERM_GROUP   = 7    // 8 teams per group, turno único
    const val URU_INTERM_FINAL_RODADA   = 8    // Intermediário Final round
    const val URU_INTERM_OG_OFFSET      = 155  // OG offset for Intermediário (R1→165)
    const val URU_INTERM_OG_FINAL       = 235  // Intermediário Final OG (8*10+155)
    const val URU_CLAUSURA_OG_OFFSET    = 250  // OG offset for Clausura (R1→260)
    const val URU_PLAYOFF_SEMI_RODADA   = 16   // Championship Semi round in Clausura
    const val URU_PLAYOFF_FINAL_RODADA  = 17   // Championship Final round in Clausura
    const val URU_PLAYOFF_OG_SEMI       = 411  // Championship Semi OG
    const val URU_PLAYOFF_OG_FINAL      = 421  // Championship Final OG
    // URU_GRUPO_A / URU_GRUPO_B reutilizam ARG_GRUPO_A / ARG_GRUPO_B ("A"/"B")

    //  Torneo Competencia – Segunda División Uruguaia
    //  14 teams → 2 groups of 7, turno único (6 rods) + Final (R7)
    //  OG = rodada*10 + URU_B_COMPET_OG_OFFSET (450..510); Final OG = 520
    const val URU_B_COMPET_ROUNDS_GROUP = 6    // 7 teams per group, turno único (N-1)
    const val URU_B_COMPET_FINAL_RODADA = 7    // Competencia Final round
    const val URU_B_COMPET_OG_OFFSET    = 450  // OG offset for Competencia groups (R1→460)
    const val URU_B_COMPET_OG_FINAL     = 520  // Competencia Final OG (7*10+450)

    // ══════════════════════════════════════════════════════════════
    //  UEFA Champions League  (FASE_LIGA_E_MATA_MATA)
    //  Ver regras_championsleague.md.
    //
    //  Fase de Liga: 36 clubes numa tabela só, 8 jogos cada contra
    //  adversários DIFERENTES (as 144 partidas vêm de SorteioChampions).
    //  As partidas da Fase de Liga têm fase = null, como a fase de grupos
    //  dos torneios argentino e uruguaio — é isso que faz a classificação
    //  ser alimentada pelo caminho normal (recalcularClassificacao só
    //  conta partidas de fase nula).
    //
    //  Depois dela: play-off (9º–24º), Oitavas, Quartas, Semi e Final,
    //  todos ida e volta. O play-off NÃO dá bônus de avanço no coeficiente
    //  da UEFA — a contagem começa nas oitavas (ver RankingEuropa) — mas
    //  as suas partidas pontuam por jogo como qualquer outra.
    // ══════════════════════════════════════════════════════════════

    /**
     * Parâmetros de uma competição continental de Fase de Liga + mata-mata.
     *
     * Extraído porque Champions e Europa League têm a MESMA estrutura — 36 clubes,
     * 8 jogos, 1º–8º direto às oitavas, 9º–24º no play-off, 25º–36º eliminados — e
     * só diferem na lista de acesso e nas casas do calendário. Ver
     * regras_championsleague.md e europa_league.md.
     */
    data class FormatoContinental(
        val nome: String,
        /** Rodadas da Fase de Liga: 8 jogos por clube. */
        val rodadasLiga: Int,
        /**
         * Deslocamento das rodadas do mata-mata. A Fase de Liga ocupa as rodadas
         * 1..8 (até 12 na folga do fallback de distribuição), então o play-off
         * começa em base−1 e as oitavas em base+1, longe de qualquer colisão.
         */
        val rodadaBaseMataMata: Int,
        val fasePlayoff: String,
        val fasesMataMata: List<String>,
        /** Clubes que vão direto às oitavas; os seguintes disputam o play-off. */
        val classificadosDireto: Int,
        val ultimoDoPlayoff: Int,
        val ordemGlobalLiga: IntArray,
        val ordemGlobalPlayoff: IntArray,
        val ordemGlobalMataMata: IntArray
    ) {
        /** Todas as fases nomeadas, na ordem — usada pelo chaveamento na UI. */
        val fases: List<String> get() = listOf(fasePlayoff) + fasesMataMata

        /** Última rodada do torneio: a volta da final. */
        val totalRodadas: Int get() = rodadaBaseMataMata + 8

        /** Rodada de ida do play-off, imediatamente antes das oitavas. */
        val rodadaIdaPlayoff: Int get() = rodadaBaseMataMata - 1

        /**
         * ordemGlobal de uma rodada da Fase de Liga. Rodadas além da última só
         * existem no fallback de [distribuirRodadasFaseLiga] e entram em seguida.
         */
        fun ordemGlobalDaRodadaLiga(rodada: Int): Int =
            ordemGlobalLiga.getOrElse(rodada - 1) {
                ordemGlobalLiga.last() + (rodada - rodadasLiga) * 2
            }

        /** rodadaIda de uma fase do mata-mata (0 = Oitavas). */
        fun rodadaIdaDeFaseMataMata(faseIndex: Int): Int =
            rodadaBaseMataMata + rodadaIdaDeFase(faseIndex)
    }

    /**
     * A Champions joga no meio de semana, encaixada entre as rodadas das ligas
     * nacionais (que usam ordemGlobal = rodada*10, de 10 a 380).
     */
    val FORMATO_CHAMPIONS = FormatoContinental(
        nome                = "Champions League",
        rodadasLiga         = 8,
        rodadaBaseMataMata  = 20,
        fasePlayoff         = "Play-off",
        fasesMataMata       = listOf("Oitavas", "Quartas", "Semi", "Final"),
        classificadosDireto = 8,
        ultimoDoPlayoff     = 24,
        //   R1 após liga R2 (27), R2 após R6 (67), R3 após R10 (107) ...
        ordemGlobalLiga     = intArrayOf(27, 67, 107, 147, 187, 227, 257, 287),
        ordemGlobalPlayoff  = intArrayOf(303, 307),
        //   Oitavas 313/317, Quartas 323/327, Semi 343/347, Final 363/373
        ordemGlobalMataMata = intArrayOf(313, 317, 323, 327, 343, 347, 363, 373)
    )

    /**
     * A Europa League joga um dia depois da Champions, como no calendário real:
     * cada casa de ordemGlobal é a da Champions + 1. Isso evita colisão com a
     * Champions e com as ligas nacionais, cujas rodadas são múltiplos de 10.
     *
     * A base de rodada do mata-mata é 40 (contra 20 da Champions) para que as duas
     * competições nunca compartilhem número de rodada — a tela de Rodadas e o
     * avanço de fase indexam por rodada dentro do campeonato, e manter as faixas
     * separadas evita confusão ao depurar dois torneios na mesma temporada.
     */
    val FORMATO_EUROPA_LEAGUE = FormatoContinental(
        nome                = "Europa League",
        rodadasLiga         = 8,
        rodadaBaseMataMata  = 40,
        fasePlayoff         = "Play-off",
        fasesMataMata       = listOf("Oitavas", "Quartas", "Semi", "Final"),
        classificadosDireto = 8,
        ultimoDoPlayoff     = 24,
        ordemGlobalLiga     = intArrayOf(28, 68, 108, 148, 188, 228, 258, 288),
        ordemGlobalPlayoff  = intArrayOf(304, 308),
        ordemGlobalMataMata = intArrayOf(314, 318, 324, 328, 344, 348, 364, 374)
    )

    /** Rodadas da Fase de Liga: 8 jogos por clube. */
    const val CHAMPIONS_RODADAS_LIGA = 8

    /**
     * Deslocamento das rodadas do mata-mata. A Fase de Liga ocupa as rodadas
     * 1..8 (até 12 na folga do fallback de distribuição), então o play-off
     * começa em 19 e as oitavas em 21, bem longe de qualquer colisão.
     */
    const val CHAMPIONS_RODADA_BASE_MATA_MATA = 20

    /** Última rodada do torneio: a volta da final. */
    const val CHAMPIONS_TOTAL_RODADAS = CHAMPIONS_RODADA_BASE_MATA_MATA + 8   // 28

    /** O play-off, que fica entre a Fase de Liga e as oitavas. */
    const val CHAMPIONS_FASE_PLAYOFF = "Play-off"

    /** Fases de mata-mata a partir das oitavas, na ordem. */
    val CHAMPIONS_FASES_MATA_MATA = listOf("Oitavas", "Quartas", "Semi", "Final")

    /** Todas as fases nomeadas, na ordem — usada pelo chaveamento na UI. */
    val CHAMPIONS_FASES = listOf(CHAMPIONS_FASE_PLAYOFF) + CHAMPIONS_FASES_MATA_MATA

    /** Clubes que vão direto às oitavas; os 16 seguintes disputam o play-off. */
    const val CHAMPIONS_CLASSIFICADOS_DIRETO = 8
    const val CHAMPIONS_ULTIMO_DO_PLAYOFF    = 24

    /**
     * ordemGlobal das 8 rodadas da Fase de Liga. As ligas nacionais usam
     * ordemGlobal = rodada*10 (10..380); a Fase de Liga encaixa entre elas, a
     * cada ~4 rodadas de liga, como as competições europeias de meio de semana.
     *   R1 após liga R2 (27), R2 após R6 (67), R3 após R10 (107) ...
     */
    val CHAMPIONS_ORDEM_GLOBAL_LIGA = intArrayOf(27, 67, 107, 147, 187, 227, 257, 287)

    /**
     * ordemGlobal de uma rodada da Fase de Liga. Rodadas além da 8ª só existem
     * no fallback de [distribuirRodadasFaseLiga] e entram logo após a 8ª.
     */
    fun ordemGlobalLigaChampions(rodada: Int): Int =
        CHAMPIONS_ORDEM_GLOBAL_LIGA.getOrElse(rodada - 1) {
            CHAMPIONS_ORDEM_GLOBAL_LIGA.last() + (rodada - CHAMPIONS_RODADAS_LIGA) * 2
        }

    /** ordemGlobal do play-off: ida e volta, no fim da fase de liga. */
    val CHAMPIONS_ORDEM_GLOBAL_PLAYOFF = intArrayOf(303, 307)

    /**
     * ordemGlobal do mata-mata (4 fases × ida/volta), encaixado na reta final da
     * temporada, entre as últimas rodadas das ligas nacionais.
     *   Oitavas 313/317, Quartas 323/327, Semi 343/347, Final 363/373
     */
    val CHAMPIONS_ORDEM_GLOBAL_MATA_MATA =
        intArrayOf(313, 317, 323, 327, 343, 347, 363, 373)

    /** rodadaIda de uma fase do mata-mata da Champions (0 = Oitavas). */
    fun rodadaIdaDeFaseChampions(faseIndex: Int) =
        CHAMPIONS_RODADA_BASE_MATA_MATA + rodadaIdaDeFase(faseIndex)

    /** rodada de ida do play-off, imediatamente antes das oitavas. */
    const val CHAMPIONS_RODADA_IDA_PLAYOFF = CHAMPIONS_RODADA_BASE_MATA_MATA - 1

    /**
     * Distribui as 144 partidas da Fase de Liga em rodadas em que nenhum clube
     * joga duas vezes.
     *
     * É uma coloração de arestas do grafo 8-regular dos confrontos: 8 rodadas
     * são o mínimo, e quase todo grafo desses admite as 8 (classe 1), mas isso
     * não é garantido — pelo teorema de Vizing o pior caso precisa de 9. Então
     * tentamos encaixar em 8 por guloso com reinícios aleatórios e, se nenhuma
     * tentativa fechar, abrimos uma rodada extra e repetimos. O calendário
     * aceita isso sem remendo: [ordemGlobalLigaChampions] cobre rodadas além da
     * 8ª, e a classificação não depende de todos terem jogado o mesmo número de
     * partidas na mesma rodada.
     *
     * Devolve, para cada confronto de [confrontos], a rodada (1-based) atribuída.
     */
    fun distribuirRodadasFaseLiga(
        confrontos: List<ConfrontoChampions>,
        random: kotlin.random.Random = kotlin.random.Random.Default,
        tentativasPorTamanho: Int = 200,
        formato: FormatoContinental = FORMATO_CHAMPIONS
    ): List<Int> {
        var rodadas = formato.rodadasLiga
        while (rodadas <= formato.rodadasLiga + 4) {
            repeat(tentativasPorTamanho) {
                val tentativa = tentarDistribuir(confrontos, rodadas, random)
                if (tentativa != null) return tentativa
            }
            rodadas++
        }
        // Inalcançável na prática: com 12 rodadas o guloso sempre fecha, já que
        // cada clube ocupa no máximo 8 delas.
        return tentarDistribuir(confrontos, confrontos.size, random)
            ?: List(confrontos.size) { it + 1 }
    }

    /**
     * Guloso de uma passada, com heurística de confronto mais restrito primeiro:
     * a cada passo pega o confronto que tem MENOS rodadas disponíveis e o põe na
     * primeira delas. É a mesma ideia que faz o retrocesso do sorteio funcionar —
     * decidir primeiro o que tem menos saída.
     *
     * A ordem importa muito aqui. Pegar os confrontos em ordem aleatória e
     * encaixar cada um na primeira rodada livre fecha as 8 rodadas raramente: o
     * sorteio real do seed precisava de 10. Com a heurística, as 8 saem.
     *
     * Devolve null quando algum confronto não cabe em [rodadas] rodadas.
     */
    private fun tentarDistribuir(
        confrontos: List<ConfrontoChampions>,
        rodadas: Int,
        random: kotlin.random.Random
    ): List<Int>? {
        val atribuicao = IntArray(confrontos.size)
        // ocupado[rodada][timeId]
        val ocupado = Array(rodadas) { HashSet<Int>() }
        val pendentes = confrontos.indices.shuffled(random).toMutableList()

        fun rodadasLivres(confronto: ConfrontoChampions): List<Int> =
            (0 until rodadas).filter { r ->
                confronto.casaId !in ocupado[r] && confronto.foraId !in ocupado[r]
            }

        while (pendentes.isNotEmpty()) {
            var escolhido = -1
            var melhores: List<Int> = emptyList()
            for ((posicao, indice) in pendentes.withIndex()) {
                val livres = rodadasLivres(confrontos[indice])
                if (livres.isEmpty()) return null          // beco sem saída
                if (escolhido < 0 || livres.size < melhores.size) {
                    escolhido = posicao
                    melhores = livres
                    if (livres.size == 1) break            // não há mais restrito
                }
            }
            val indice = pendentes.removeAt(escolhido)
            val confronto = confrontos[indice]
            val rodada = melhores.first()
            ocupado[rodada] += confronto.casaId
            ocupado[rodada] += confronto.foraId
            atribuicao[indice] = rodada + 1
        }
        return atribuicao.toList()
    }

    /**
     * Converte o sorteio na lista de partidas da Fase de Liga, já distribuídas
     * em rodadas e com o ordemGlobal do calendário. fase = null de propósito:
     * é o que faz a tabela de 1 a 36 ser alimentada pelo caminho normal.
     */
    fun gerarFaseLigaChampions(
        campeonatoId: Int,
        confrontos: List<ConfrontoChampions>,
        random: kotlin.random.Random = kotlin.random.Random.Default,
        formato: FormatoContinental = FORMATO_CHAMPIONS
    ): List<PartidaEntity> {
        val rodadas = distribuirRodadasFaseLiga(confrontos, random, formato = formato)
        return confrontos.mapIndexed { indice, confronto ->
            val rodada = rodadas[indice]
            PartidaEntity(
                campeonatoId = campeonatoId,
                rodada       = rodada,
                timeCasaId   = confronto.casaId,
                timeForaId   = confronto.foraId,
                fase         = null,
                ordemGlobal  = formato.ordemGlobalDaRodadaLiga(rodada)
            )
        }
    }

    /**
     * Pares do play-off: do 9º ao 24º da Fase de Liga, o melhor colocado contra
     * o pior (9º×24º, 10º×23º ... 16º×17º). O melhor colocado decide em casa, e
     * como [gerarFaseIdaVolta] inverte o mando na volta, o par entra com o pior
     * colocado como mandante da ida.
     *
     * [classificados] é a tabela ordenada do 1º ao 36º.
     */
    fun paresPlayoffChampions(
        classificados: List<Int>,
        formato: FormatoContinental = FORMATO_CHAMPIONS
    ): List<Pair<Int, Int>> {
        // Sem os 9 primeiros não existe play-off: a lista está incompleta e
        // insistir só produziria um chaveamento inventado.
        if (classificados.size <= formato.classificadosDireto) return emptyList()
        val faixa = classificados.subList(
            formato.classificadosDireto,
            minOf(formato.ultimoDoPlayoff, classificados.size)
        )
        val metade = faixa.size / 2
        return (0 until metade).map { i ->
            val melhor = faixa[i]
            val pior   = faixa[faixa.size - 1 - i]
            pior to melhor          // pior recebe a ida; melhor decide em casa
        }
    }

    /**
     * Pares das oitavas: os 8 primeiros da Fase de Liga, que passaram direto,
     * contra os 8 vencedores do play-off — o 1º contra o vencedor do último par,
     * o 2º contra o penúltimo, e assim por diante. O cabeça de chave decide em
     * casa, então o vencedor do play-off é o mandante da ida.
     *
     * [classificados] é a tabela ordenada; [vencedoresPlayoff] vem na ordem dos
     * pares de [paresPlayoffChampions].
     */
    fun paresOitavasChampions(
        classificados: List<Int>,
        vencedoresPlayoff: List<Int>,
        formato: FormatoContinental = FORMATO_CHAMPIONS
    ): List<Pair<Int, Int>> {
        val cabecas = classificados.take(formato.classificadosDireto)
        return cabecas.mapIndexed { i, cabeca ->
            val vencedor = vencedoresPlayoff.getOrNull(vencedoresPlayoff.size - 1 - i)
                ?: vencedoresPlayoff[i % vencedoresPlayoff.size]
            vencedor to cabeca      // vencedor recebe a ida; cabeça decide em casa
        }
    }
}
