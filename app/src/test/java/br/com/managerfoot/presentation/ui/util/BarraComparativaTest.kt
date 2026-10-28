package br.com.managerfoot.presentation.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A fração da barra comparativa.
 *
 * O que estes testes protegem é um crash: o Compose lança
 * `IllegalArgumentException("invalid weight 0.0")` para `Modifier.weight(0f)`, e
 * os dois lados podem valer zero de verdade. Nenhuma fração daqui pode ser 0 nem 1.
 */
class BarraComparativaTest {

    private val delta = 0.0001f

    private fun frac(a: Int, b: Int) = BarraComparativa.fracaoDoPrimeiro(a, b)

    // ── O crash ──────────────────────────────────────────────────────────

    @Test
    fun `nenhuma fracao e zero, nem o complemento dela`() {
        // Varre o zero contra tudo, e o inverso, que é o que derrubava o app: um
        // setor sem titular contra um adversário escalado.
        val casos = listOf(0, 1, 5, 50, 99, 1000)
        for (a in casos) for (b in casos) {
            val f = frac(a, b)
            assertTrue("frac($a,$b) = $f deveria ser > 0", f > 0f)
            assertTrue("1 - frac($a,$b) = ${1f - f} deveria ser > 0", (1f - f) > 0f)
        }
    }

    @Test
    fun `setor zerado contra adversario escalado mantem a fatia minima`() {
        assertEquals(BarraComparativa.FATIA_MINIMA, frac(0, 80), delta)
        assertEquals(1f - BarraComparativa.FATIA_MINIMA, frac(80, 0), delta)
    }

    @Test
    fun `escalacao nao montada nos dois lados da meio a meio`() {
        // Jogo novo: titulares vazio zera os quatro setores dos dois times. Isso é
        // um empate, não uma barra torta.
        assertEquals(0.5f, frac(0, 0), delta)
    }

    // ── A proporção ──────────────────────────────────────────────────────

    @Test
    fun `valores iguais dividem a barra ao meio`() {
        assertEquals(0.5f, frac(70, 70), delta)
    }

    @Test
    fun `a fracao e proporcional dentro da faixa`() {
        assertEquals(0.75f, frac(75, 25), delta)
        assertEquals(0.25f, frac(25, 75), delta)
        assertEquals(0.6f, frac(30, 20), delta)
    }

    @Test
    fun `as duas fracoes sempre somam um`() {
        for (a in 0..100 step 7) for (b in 0..100 step 11) {
            val f = frac(a, b)
            assertEquals("frac($a,$b)", 1f, f + (1f - f), delta)
        }
    }

    // ── Entradas degeneradas ─────────────────────────────────────────────

    @Test
    fun `negativo e tratado como zero, sem sair da faixa`() {
        assertEquals(BarraComparativa.FATIA_MINIMA, frac(-10, 50), delta)
        assertEquals(0.5f, frac(-1, -1), delta)
        val f = frac(50, -10)
        assertTrue("$f deveria caber na faixa", f > 0f && f < 1f)
    }
}
