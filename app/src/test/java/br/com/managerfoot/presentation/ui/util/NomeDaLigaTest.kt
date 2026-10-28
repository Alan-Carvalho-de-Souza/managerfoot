package br.com.managerfoot.presentation.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O nome da liga a partir da divisão do clube.
 *
 * Esta tabela estava copiada em três telas e as cópias divergiram: a do header
 * do Dashboard ainda tratava 11..16 como divisões uruguaias, de antes das ligas
 * europeias existirem.
 */
class NomeDaLigaTest {

    @Test
    fun `as divisoes europeias nao sao mais uruguaias`() {
        // O bug: o Real Madrid (divisão 13) aparecia como "Segunda Div. (URU)".
        assertEquals("Premier League", NomeDaLiga.deDivisao(11))
        assertEquals("EFL Championship", NomeDaLiga.deDivisao(12))
        assertEquals("La Liga", NomeDaLiga.deDivisao(13))
        assertEquals("Serie A", NomeDaLiga.deDivisao(14))
    }

    @Test
    fun `a versao curta tambem`() {
        assertEquals("Premier League", NomeDaLiga.curto(11))
        assertEquals("La Liga", NomeDaLiga.curto(13))
        assertEquals("Serie A", NomeDaLiga.curto(14))
    }

    @Test
    fun `as divisoes uruguaias sao a 9 e a 10`() {
        assertEquals("Primera Div. (URU)", NomeDaLiga.curto(9))
        assertEquals("Segunda Div. (URU)", NomeDaLiga.curto(10))
    }

    @Test
    fun `brasileiras e argentinas seguem como eram`() {
        assertEquals("Série A", NomeDaLiga.deDivisao(1))
        assertEquals("Série D", NomeDaLiga.deDivisao(4))
        assertEquals("Primera División", NomeDaLiga.deDivisao(5))
        assertEquals("Primera Nacional", NomeDaLiga.deDivisao(6))
    }

    @Test
    fun `o pool sem liga aparece como outros times`() {
        for (divisao in listOf(90, 91, 92)) {
            assertEquals("Outros times", NomeDaLiga.deDivisao(divisao))
        }
    }

    @Test
    fun `divisao desconhecida nao inventa nome`() {
        assertEquals("Divisão 77", NomeDaLiga.deDivisao(77))
        assertEquals("Divisão 77", NomeDaLiga.curto(77))
    }

    @Test
    fun `nenhuma divisao conhecida cai no generico`() {
        val conhecidas = listOf(1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14, 90, 91, 92)
        for (divisao in conhecidas) {
            val nome = NomeDaLiga.deDivisao(divisao)
            assertEquals("$divisao: $nome", false, nome.startsWith("Divisão "))
        }
    }
}
