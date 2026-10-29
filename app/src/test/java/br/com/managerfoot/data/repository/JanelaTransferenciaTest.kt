package br.com.managerfoot.data.repository

import br.com.managerfoot.data.repository.JogadorRepository.Companion.JANELA_FECHADA
import br.com.managerfoot.data.repository.JogadorRepository.Companion.janelaTransferencia
import br.com.managerfoot.data.repository.JogadorRepository.Companion.podeTrocarDeClube
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A janela de transferências.
 *
 * Duas por temporada — pré-temporada (meses 1 e 2) e fim de ano (10, 11 e 12) —
 * e um jogador troca de clube no máximo uma vez em cada.
 *
 * O que estes testes protegem é a diferença entre as duas proteções que o
 * mercado da IA tem. O conjunto de "já negociados" é recriado a cada mês e só
 * impede duas trocas no MESMO mês; quem fecha a janela inteira é o carimbo.
 * Sem o carimbo, um jogador movido no mês 1 voltaria a estar livre no mês 2.
 */
class JanelaTransferenciaTest {

    // ── Quais meses têm janela ───────────────────────────────────────────

    @Test
    fun `a pre-temporada e uma janela so, nos meses 1 e 2`() {
        assertEquals(janelaTransferencia(3, 1), janelaTransferencia(3, 2))
        assertTrue(janelaTransferencia(3, 1) != JANELA_FECHADA)
    }

    @Test
    fun `o fim de ano e uma janela so, nos meses 10, 11 e 12`() {
        val dez = janelaTransferencia(3, 10)
        assertEquals(dez, janelaTransferencia(3, 11))
        assertEquals(dez, janelaTransferencia(3, 12))
        assertTrue(dez != JANELA_FECHADA)
    }

    @Test
    fun `as duas janelas da temporada sao diferentes`() {
        assertTrue(janelaTransferencia(3, 2) != janelaTransferencia(3, 10))
    }

    @Test
    fun `do mes 3 ao 9 o mercado esta fechado`() {
        for (mes in 3..9) {
            assertEquals("mês $mes", JANELA_FECHADA, janelaTransferencia(3, mes))
        }
    }

    // ── A ordem entre temporadas ─────────────────────────────────────────

    @Test
    fun `a janela cresce com a temporada`() {
        val janelas = listOf(
            janelaTransferencia(1, 1), janelaTransferencia(1, 10),
            janelaTransferencia(2, 1), janelaTransferencia(2, 10),
            janelaTransferencia(3, 1), janelaTransferencia(3, 10)
        )
        assertEquals(janelas.sorted(), janelas)
        assertEquals(janelas.size, janelas.distinct().size)
    }

    @Test
    fun `nenhuma janela colide com o valor padrao da coluna`() {
        // `janelaUltimaTransferencia` nasce em 0 nos registros antigos. Se uma
        // janela real valesse 0, esses jogadores pareceriam ter sido negociados
        // nela e ficariam travados.
        for (temporadaId in 1..10) {
            for (mes in listOf(1, 2, 10, 11, 12)) {
                assertTrue(
                    "temporada $temporadaId, mês $mes",
                    janelaTransferencia(temporadaId, mes) > 0
                )
            }
        }
    }

    // ── A regra de troca ─────────────────────────────────────────────────

    @Test
    fun `quem ja trocou nesta janela nao troca de novo`() {
        val janela = janelaTransferencia(3, 1)
        assertFalse(podeTrocarDeClube(janela, janela))
    }

    @Test
    fun `o mes 2 nao reabre o que o mes 1 fechou`() {
        // É o caso que o conjunto mensal sozinho não pega: o set é recriado no
        // mês 2, e sem o carimbo o jogador voltaria ao mercado.
        val moveuNoMes1 = janelaTransferencia(3, 1)
        assertFalse(podeTrocarDeClube(moveuNoMes1, janelaTransferencia(3, 2)))
    }

    @Test
    fun `os meses 11 e 12 nao reabrem o que o 10 fechou`() {
        val moveuNoMes10 = janelaTransferencia(3, 10)
        assertFalse(podeTrocarDeClube(moveuNoMes10, janelaTransferencia(3, 11)))
        assertFalse(podeTrocarDeClube(moveuNoMes10, janelaTransferencia(3, 12)))
    }

    @Test
    fun `a janela seguinte libera`() {
        val moveuNaPreTemporada = janelaTransferencia(3, 1)
        assertTrue(podeTrocarDeClube(moveuNaPreTemporada, janelaTransferencia(3, 10)))
        assertTrue(podeTrocarDeClube(moveuNaPreTemporada, janelaTransferencia(4, 1)))
    }

    @Test
    fun `com o mercado fechado ninguem troca`() {
        val nuncaTrocou = 0
        for (mes in 3..9) {
            assertFalse("mês $mes", podeTrocarDeClube(nuncaTrocou, janelaTransferencia(3, mes)))
        }
    }

    @Test
    fun `quem nunca trocou pode trocar na janela aberta`() {
        val nuncaTrocou = 0
        assertTrue(podeTrocarDeClube(nuncaTrocou, janelaTransferencia(1, 1)))
        assertTrue(podeTrocarDeClube(nuncaTrocou, janelaTransferencia(1, 10)))
    }

    @Test
    fun `um carimbo de temporada passada nao trava o jogador`() {
        val moveuNaTemporadaPassada = janelaTransferencia(2, 10)
        assertTrue(podeTrocarDeClube(moveuNaTemporadaPassada, janelaTransferencia(3, 1)))
    }

    @Test
    fun `no maximo duas trocas por temporada`() {
        // Uma por janela. Simula um jogador percorrendo o ano inteiro.
        var carimbo = 0
        var trocas = 0
        for (mes in 1..12) {
            val janela = janelaTransferencia(3, mes)
            if (podeTrocarDeClube(carimbo, janela)) {
                trocas++
                carimbo = janela
            }
        }
        assertEquals(2, trocas)
    }
}
