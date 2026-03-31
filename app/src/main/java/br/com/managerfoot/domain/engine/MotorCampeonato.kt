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

    // ── Fases da Copa (em ordem) ──────────────────────────────────
    val COPA_FASES = listOf(
        "Primeira Fase",
        "Segunda Fase",
        "Oitavas",
        "Quartas",
        "Semi",
        "Final"
    )

    // Retorna rodadaIda de uma fase (0-indexed faseIndex)
    fun rodadaIdaDeFase(faseIndex: Int) = faseIndex * 2 + 1

    // ordemGlobal de cada jogo da Copa no calendário multi-competição.
    // Copa Fase 0 ida fica após Brasileirão R3 (30), e assim por diante.
    // Brasileirão usa ordemGlobal = rodada * 10 (10, 20, 30 ...) → Copa encaixa em N*10+5.
    // Índices: 0=F0-ida, 1=F0-volta, 2=F1-ida, 3=F1-volta, ... 11=Final-volta
    // Copa encaixa entre as rodadas do Brasileirão (ordemGlobal = rodada*10):
    //   Primeira Fase ─ Fev:  antes de R1 (10)
    //   Segunda Fase  ─ Mar:  entre R1 (10) e R4 (40)
    //   Oitavas       ─ Mai:  entre R9 (90) e R12 (120)
    //   Quartas       ─ Jul:  entre R17 (170) e R19 (190)
    //   Semifinal     ─ Set:  entre R24 (240) e R26 (260)
    //   Final         ─ Nov:  entre R33 (330) e R35 (350)
    val COPA_ORDEM_GLOBAL = intArrayOf(2, 7, 15, 35, 95, 115, 175, 195, 245, 265, 335, 355)

    fun proximaFaseCopa(faseAtual: String): String? {
        val idx = COPA_FASES.indexOf(faseAtual)
        return if (idx >= 0 && idx < COPA_FASES.size - 1) COPA_FASES[idx + 1] else null
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
}
