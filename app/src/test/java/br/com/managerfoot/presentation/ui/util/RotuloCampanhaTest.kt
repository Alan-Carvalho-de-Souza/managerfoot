package br.com.managerfoot.presentation.ui.util

import br.com.managerfoot.domain.engine.ResultadoFinal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rótulo da campanha de um clube num torneio.
 *
 * O caso que exige atenção é a fase de grupos e a fase de liga: as partidas
 * delas são gravadas sem fase, então o rótulo depende do FORMATO do campeonato.
 */
class RotuloCampanhaTest {

    // ── Fase de grupos vs fase de liga ───────────────────────────────────

    @Test
    fun `sem fase, o formato diz se foi grupos ou fase de liga`() {
        // Libertadores e Sul-Americana
        assertEquals(
            "Fase de Grupos",
            RotuloCampanha.daCampanha(null, "GRUPOS_E_MATA_MATA")
        )
        // Champions e Europa League
        assertEquals(
            "Fase de Liga",
            RotuloCampanha.daCampanha(null, "FASE_LIGA_E_MATA_MATA")
        )
    }

    @Test
    fun `sem fase e sem grupos nao tem rotulo`() {
        // Copa de mata-mata puro não tem partida sem fase; se vier, não há o que
        // dizer — a tela mostra o travessão.
        assertNull(RotuloCampanha.daCampanha(null, "MATA_MATA_IDA_VOLTA"))
        assertNull(RotuloCampanha.daCampanha(null, "PONTOS_CORRIDOS"))
        assertNull(RotuloCampanha.daCampanha(null, ""))
    }

    // ── Fases da Pré-Libertadores ────────────────────────────────────────

    @Test
    fun `as fases da pre-libertadores sao identificadas como pre`() {
        assertEquals("Pré · 1ª Fase", RotuloCampanha.daFase("Fase 1"))
        assertEquals("Pré · 2ª Fase", RotuloCampanha.daFase("Fase 2"))
        assertEquals("Pré · 3ª Fase", RotuloCampanha.daFase("Fase 3"))
    }

    @Test
    fun `primeira fase de copa nacional nao e confundida com a pre`() {
        assertEquals("1ª Fase", RotuloCampanha.daFase("Primeira Fase"))
    }

    // ── Demais fases ─────────────────────────────────────────────────────

    @Test
    fun `fases eliminatorias tem nome curto`() {
        assertEquals("Oitavas", RotuloCampanha.daFase("Oitavas"))
        assertEquals("Quartas", RotuloCampanha.daFase("Quartas"))
        assertEquals("Semifinal", RotuloCampanha.daFase("Semi"))
        assertEquals("Final", RotuloCampanha.daFase("Final"))
        assertEquals("Play-off", RotuloCampanha.daFase("Play-off"))
        assertEquals("Playoffs", RotuloCampanha.daFase("Playoffs"))
    }

    @Test
    fun `fase desconhecida aparece como veio`() {
        assertEquals("Fase Nova", RotuloCampanha.daFase("Fase Nova"))
    }

    // ── Resumo ───────────────────────────────────────────────────────────

    @Test
    fun `campeao e vice vencem a fase`() {
        assertEquals(
            "Campeão",
            RotuloCampanha.resumo(ResultadoFinal.CAMPEAO, "Final", "MATA_MATA_IDA_VOLTA")
        )
        assertEquals(
            "Vice",
            RotuloCampanha.resumo(ResultadoFinal.VICE, "Final", "MATA_MATA_IDA_VOLTA")
        )
    }

    @Test
    fun `sem decisao, o resumo e a fase`() {
        assertEquals(
            "Quartas",
            RotuloCampanha.resumo(null, "Quartas", "GRUPOS_E_MATA_MATA")
        )
        assertEquals(
            "Fase de Grupos",
            RotuloCampanha.resumo(null, null, "GRUPOS_E_MATA_MATA")
        )
    }

    // ── Tipos de mata-mata ───────────────────────────────────────────────

    @Test
    fun `continentais e supercopas contam como mata-mata`() {
        for (tipo in listOf("COPA_NACIONAL", "CONTINENTAL", "SUPERCOPA")) {
            assertEquals(tipo, true, RotuloCampanha.ehMataMata(tipo))
        }
        for (tipo in listOf("NACIONAL_DIVISAO1", "EXTRANGEIRO_DIVISAO1", "LIGA_ARGENTINA")) {
            assertEquals(tipo, false, RotuloCampanha.ehMataMata(tipo))
        }
    }
}
