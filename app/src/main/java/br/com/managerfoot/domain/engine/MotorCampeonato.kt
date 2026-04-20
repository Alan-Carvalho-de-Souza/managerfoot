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
}
