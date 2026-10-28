package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testes do resumo de campanha de mata-mata: a fase alcançada e o desfecho da
 * decisão (campeão, vice, ou nada quando a final não terminou).
 */
class CampanhaMataMataTest {

    private fun jogo(
        fase: String?, pro: Int, contra: Int,
        penPro: Int? = null, penContra: Int? = null
    ) = CampanhaMataMata.Jogo(fase, pro, contra, penPro, penContra)

    // ── Fase alcançada ───────────────────────────────────────────────────

    @Test
    fun `fase atingida e a do ultimo jogo da campanha`() {
        val campanha = listOf(
            jogo("Primeira Fase", 2, 0),
            jogo("Primeira Fase", 1, 1),
            jogo("Oitavas", 0, 3)
        )
        assertEquals("Oitavas", CampanhaMataMata.faseAtingida(campanha))
    }

    @Test
    fun `campanha sem fase nao tem fase atingida`() {
        // Liga de pontos corridos: nenhuma partida tem fase.
        val campanha = listOf(jogo(null, 1, 0), jogo(null, 2, 2))
        assertNull(CampanhaMataMata.faseAtingida(campanha))
    }

    // ── Decisão: campeão e vice ──────────────────────────────────────────

    @Test
    fun `final de jogo unico define campeao e vice`() {
        assertEquals(
            ResultadoFinal.CAMPEAO,
            CampanhaMataMata.resultadoDaFinal(listOf(jogo("Final", 2, 1)))
        )
        assertEquals(
            ResultadoFinal.VICE,
            CampanhaMataMata.resultadoDaFinal(listOf(jogo("Final", 0, 1)))
        )
    }

    @Test
    fun `final de ida e volta soma o agregado`() {
        // Perde a ida por 1x2 e vence a volta por 3x0: agregado 4x2.
        val campanha = listOf(jogo("Final", 1, 2), jogo("Final", 3, 0))
        assertEquals(ResultadoFinal.CAMPEAO, CampanhaMataMata.resultadoDaFinal(campanha))
    }

    @Test
    fun `agregado nao usa gol fora como criterio`() {
        // Mesmo agregado 2x2; sem pênaltis gravados, não há desfecho — e o gol
        // fora NÃO decide, igual a MotorCampeonato.determinarVencedorTie.
        val campanha = listOf(jogo("Final", 0, 2), jogo("Final", 2, 0))
        assertNull(CampanhaMataMata.resultadoDaFinal(campanha))
    }

    @Test
    fun `agregado empatado decide nos penaltis`() {
        val campeao = listOf(jogo("Final", 1, 1), jogo("Final", 0, 0, penPro = 5, penContra = 4))
        assertEquals(ResultadoFinal.CAMPEAO, CampanhaMataMata.resultadoDaFinal(campeao))

        val vice = listOf(jogo("Final", 1, 1), jogo("Final", 0, 0, penPro = 3, penContra = 4))
        assertEquals(ResultadoFinal.VICE, CampanhaMataMata.resultadoDaFinal(vice))
    }

    // ── Sem desfecho ─────────────────────────────────────────────────────

    @Test
    fun `quem nao chegou a final nao tem desfecho`() {
        val campanha = listOf(jogo("Quartas", 1, 0), jogo("Semi", 0, 2))
        assertNull(CampanhaMataMata.resultadoDaFinal(campanha))
    }

    @Test
    fun `final ainda em andamento nao vira titulo`() {
        // Só a ida jogada, empatada: o agregado provisório não decide nada.
        assertNull(CampanhaMataMata.resultadoDaFinal(listOf(jogo("Final", 1, 1))))
    }

    @Test
    fun `fases anteriores nao entram no agregado da final`() {
        // Goleada nas semis não pode compensar a derrota na decisão.
        val campanha = listOf(
            jogo("Semi", 5, 0),
            jogo("Final", 0, 1)
        )
        assertEquals(ResultadoFinal.VICE, CampanhaMataMata.resultadoDaFinal(campanha))
    }
}
