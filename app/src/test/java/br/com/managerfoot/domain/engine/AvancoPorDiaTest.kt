package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.PartidaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O avanço por dia.
 *
 * A regra é uma frase: depois que o jogador joga no dia D, tudo que estava
 * marcado até D já aconteceu. O que estes testes protegem são as três
 * propriedades que fazem ela funcionar sem uma rotina de reconciliação —
 * idempotência, recuperação de atraso e independência de ordem.
 */
class AvancoPorDiaTest {

    private var proximoId = 1

    private fun partida(
        dia: Int,
        rodada: Int,
        jogada: Boolean = false,
        fase: String? = null,
        campeonatoId: Int = 1
    ) = PartidaEntity(
        id = proximoId++,
        campeonatoId = campeonatoId,
        rodada = rodada,
        timeCasaId = 100 + rodada,
        timeForaId = 200 + rodada,
        fase = fase,
        jogada = jogada,
        ordemGlobal = dia
    )

    /** Uma liga de [rodadas] rodadas na grade, nenhuma jogada. */
    private fun liga(rodadas: Int): List<PartidaEntity> =
        CalendarioDaTemporada.diasDeLiga(rodadas)
            .mapIndexed { i, dia -> partida(dia, i + 1) }

    // ── A regra ──────────────────────────────────────────────────────────

    @Test
    fun `joga tudo que estava marcado ate hoje`() {
        val partidas = liga(38)
        val dias = CalendarioDaTemporada.diasDeLiga(38)
        // Até o dia da 5ª rodada: as cinco primeiras.
        val aJogar = AvancoPorDia.partidasAtePorDia(partidas, dias[4])
        assertEquals(5, aJogar.size)
        assertEquals(listOf(1, 2, 3, 4, 5), aJogar.map { it.rodada })
    }

    @Test
    fun `o dia da partida conta, o seguinte nao`() {
        val dias = CalendarioDaTemporada.diasDeLiga(38)
        val partidas = liga(38)
        assertEquals(1, AvancoPorDia.partidasAtePorDia(partidas, dias[0]).size)
        assertEquals(1, AvancoPorDia.partidasAtePorDia(partidas, dias[1] - 1).size)
        assertEquals(2, AvancoPorDia.partidasAtePorDia(partidas, dias[1]).size)
    }

    @Test
    fun `a ordem e a do calendario, nao a da lista`() {
        val partidas = liga(38).reversed()
        val aJogar = AvancoPorDia.partidasAtePorDia(partidas, 365)
        assertEquals(aJogar.map { it.ordemGlobal }.sorted(), aJogar.map { it.ordemGlobal })
    }

    // ── As três propriedades ─────────────────────────────────────────────

    @Test
    fun `chamar duas vezes no mesmo dia nao joga nada de novo`() {
        val dias = CalendarioDaTemporada.diasDeLiga(38)
        val partidas = liga(38)
        val primeira = AvancoPorDia.partidasAtePorDia(partidas, dias[4])
        // Simula o efeito: o que saiu da primeira chamada passa a estar jogado.
        val idsJogados = primeira.map { it.id }.toSet()
        val depois = partidas.map { if (it.id in idsJogados) it.copy(jogada = true) else it }
        assertEquals(emptyList<PartidaEntity>(), AvancoPorDia.partidasAtePorDia(depois, dias[4]))
    }

    @Test
    fun `uma competicao parada ha semanas volta a estar em dia de uma vez`() {
        // É o que dispensa uma rotina de reconciliação: um save em que a liga
        // ficou para trás se acerta na primeira chamada.
        val dias = CalendarioDaTemporada.diasDeLiga(38)
        val partidas = liga(38)
        val aJogar = AvancoPorDia.partidasAtePorDia(partidas, dias[19])
        assertEquals(20, aJogar.size)
        assertEquals(20, AvancoPorDia.rodadaDepoisDoAvanco(partidas, dias[19]))
    }

    @Test
    fun `ligas de tamanhos diferentes andam no proprio ritmo`() {
        // O defeito que isto conserta: com uma rodada por partida do jogador, a
        // Championship (46 rodadas) e a Série A (38) andavam no mesmo passo, e a
        // de 46 terminava a temporada com 8 rodadas por jogar.
        val dia = CalendarioDaTemporada.diasDeLiga(38)[18]   // metade da de 38
        val curta = liga(38)
        proximoId = 1000
        val longa = liga(46)
        val rodadaCurta = AvancoPorDia.rodadaDepoisDoAvanco(curta, dia)
        val rodadaLonga = AvancoPorDia.rodadaDepoisDoAvanco(longa, dia)
        assertEquals(19, rodadaCurta)
        assertTrue(
            "a de 46 rodadas deveria estar mais adiante, e está em $rodadaLonga",
            rodadaLonga > rodadaCurta
        )
    }

    // ── O que fica de fora ───────────────────────────────────────────────

    @Test
    fun `mata-mata nao entra, porque tem gatilho proprio`() {
        // As fases nascem de verificarEAvancarFase* quando a anterior termina.
        // Jogá-las por data atropelaria esse encadeamento.
        val partidas = listOf(
            partida(dia = 50, rodada = 1),
            partida(dia = 60, rodada = 3, fase = "Oitavas"),
            partida(dia = 70, rodada = 2)
        )
        val aJogar = AvancoPorDia.partidasAtePorDia(partidas, 365)
        assertEquals(listOf(1, 2), aJogar.map { it.rodada })
    }

    @Test
    fun `partida sem posicao no calendario nao entra`() {
        val partidas = listOf(
            partida(dia = 0, rodada = 1),
            partida(dia = -5, rodada = 2),
            partida(dia = 50, rodada = 3)
        )
        assertEquals(listOf(3), AvancoPorDia.partidasAtePorDia(partidas, 365).map { it.rodada })
    }

    @Test
    fun `partida ja jogada nao entra`() {
        val partidas = listOf(
            partida(dia = 50, rodada = 1, jogada = true),
            partida(dia = 60, rodada = 2)
        )
        assertEquals(listOf(2), AvancoPorDia.partidasAtePorDia(partidas, 365).map { it.rodada })
    }

    @Test
    fun `dia zero ou negativo nao joga nada`() {
        val partidas = liga(38)
        assertEquals(emptyList<PartidaEntity>(), AvancoPorDia.partidasAtePorDia(partidas, 0))
        assertEquals(emptyList<PartidaEntity>(), AvancoPorDia.partidasAtePorDia(partidas, -10))
        assertEquals(0, AvancoPorDia.rodadaDepoisDoAvanco(partidas, 0))
    }

    @Test
    fun `lista vazia nao estoura`() {
        assertEquals(emptyList<PartidaEntity>(), AvancoPorDia.partidasAtePorDia(emptyList(), 100))
        assertEquals(0, AvancoPorDia.rodadaDepoisDoAvanco(emptyList(), 100))
    }

    // ── A rodada resultante ──────────────────────────────────────────────

    @Test
    fun `a rodada leva em conta o que ja estava jogado`() {
        // Uma competição pode ter partidas jogadas fora da janela deste avanço;
        // o contador não pode retroceder por causa disso.
        val partidas = listOf(
            partida(dia = 50, rodada = 7, jogada = true),
            partida(dia = 60, rodada = 1)
        )
        assertEquals(7, AvancoPorDia.rodadaDepoisDoAvanco(partidas, 60))
    }

    @Test
    fun `sem nada jogado nem a jogar a rodada e zero`() {
        val partidas = listOf(partida(dia = 200, rodada = 5))
        assertEquals(0, AvancoPorDia.rodadaDepoisDoAvanco(partidas, 100))
    }
    // ── As continentais ──────────────────────────────────────────────────

    @Test
    fun `a Fase de Liga da Champions anda no ritmo dela, nao no da liga nacional`() {
        // Era o descompasso mais visível: a Champions tem 8 rodadas espalhadas
        // por meia temporada, e com uma rodada por partida do jogador ela
        // acabava a Fase de Liga enquanto a liga nacional ia na 8ª. Pelo dia,
        // ela joga quando está marcada.
        proximoId = 2000
        val faseDeLiga = CalendarioDasCompeticoes.champions().take(8)
            .mapIndexed { i, dia -> partida(dia, i + 1) }
        val diaDaOitavaRodadaNacional = CalendarioDaTemporada.diasDeLiga(38)[7]

        val jogadas = AvancoPorDia.partidasAtePorDia(faseDeLiga, diaDaOitavaRodadaNacional)
        assertTrue(
            "a Champions não pode ter 8 rodadas jogadas aqui, e tem ${jogadas.size}",
            jogadas.size < 8
        )
        assertEquals(
            faseDeLiga.count { it.ordemGlobal <= diaDaOitavaRodadaNacional },
            jogadas.size
        )
    }

    @Test
    fun `o mata-mata continental fica para o gatilho de fase`() {
        // A Fase de Liga tem fase = null e entra; play-off e mata-mata têm fase
        // e ficam de fora, porque quem sabe montar o chaveamento é
        // verificarEAvancarFaseContinental.
        proximoId = 3000
        val dias = CalendarioDasCompeticoes.champions()
        val todas = dias.take(8).mapIndexed { i, dia -> partida(dia, i + 1) } +
            partida(dias[8], 19, fase = "Play-off") +
            partida(dias[10], 21, fase = "Oitavas")
        val aJogar = AvancoPorDia.partidasAtePorDia(todas, 365)
        assertEquals(8, aJogar.size)
        assertTrue(aJogar.all { it.fase == null })
    }

    @Test
    fun `Champions e Europa League andam no mesmo passo, em dias diferentes`() {
        // Elas dividem as 18 semanas, em casas diferentes. Nenhum clube joga as
        // duas, mas o ritmo tem de ser o mesmo — é a mesma competição europeia
        // de meio de semana.
        proximoId = 4000
        val champions = CalendarioDasCompeticoes.champions().take(8)
            .mapIndexed { i, dia -> partida(dia, i + 1) }
        proximoId = 5000
        val europa = CalendarioDasCompeticoes.europaLeague().take(8)
            .mapIndexed { i, dia -> partida(dia, i + 1) }
        for (dia in listOf(60, 100, 150, 200)) {
            assertEquals(
                "dia $dia",
                AvancoPorDia.rodadaDepoisDoAvanco(champions, dia),
                AvancoPorDia.rodadaDepoisDoAvanco(europa, dia)
            )
        }
    }
}
