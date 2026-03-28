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
            rodadaJogos.forEach { (casa, fora) ->
                partidas.add(PartidaEntity(
                    campeonatoId = campeonatoId,
                    rodada = idx + 1,
                    timeCasaId = casa,
                    timeForaId = fora
                ))
            }
        }

        // Returno (inverte mandante/visitante)
        rodadaTurno.forEachIndexed { idx, rodadaJogos ->
            rodadaJogos.forEach { (casa, fora) ->
                partidas.add(PartidaEntity(
                    campeonatoId = campeonatoId,
                    rodada = idx + 1 + (totalRodadas / 2),
                    timeCasaId = fora,   // invertido
                    timeForaId = casa
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
