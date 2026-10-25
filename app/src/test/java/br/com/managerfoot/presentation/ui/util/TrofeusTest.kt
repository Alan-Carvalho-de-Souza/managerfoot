package br.com.managerfoot.presentation.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Resolução de troféu por nome de campeonato.
 *
 * A resolução casa por substring do nome, então a ORDEM dos ramos é a regra de
 * verdade. Estes testes travam as duas confusões que ela já causou ou causaria:
 * a EFL Championship virar Champions League, e as supercopas continentais e
 * europeias virarem "Supercopa Rei".
 */
class TrofeusTest {

    private fun resolver(nome: String, divisao: Int = 0) = Trofeus.resolver(nome, divisao)

    // ── Continentais ─────────────────────────────────────────────────────

    @Test
    fun `champions league tem o seu trofeu`() {
        val t = resolver("Champions League 2027")
        assertEquals(Trofeus.CHAMPIONS_LEAGUE, t.drawable)
        assertEquals("Champions League", t.nomeCurto)
        assertEquals(Trofeus.Pais.INTERNACIONAL, t.pais)
    }

    @Test
    fun `europa league tem o seu trofeu`() {
        val t = resolver("Europa League 2027")
        assertEquals(Trofeus.EUROPA_LEAGUE, t.drawable)
        assertEquals("Europa League", t.nomeCurto)
    }

    @Test
    fun `libertadores tem o seu trofeu`() {
        val t = resolver("Libertadores 2027")
        assertEquals(Trofeus.LIBERTADORES, t.drawable)
        assertEquals("Libertadores", t.nomeCurto)
    }

    @Test
    fun `a pre-libertadores usa o trofeu da libertadores`() {
        // E a mesma competicao, so uma fase anterior.
        assertEquals(Trofeus.LIBERTADORES, resolver("Pré-Libertadores 2027").drawable)
    }

    @Test
    fun `os tres trofeus continentais sao imagens distintas`() {
        val ids = listOf(Trofeus.CHAMPIONS_LEAGUE, Trofeus.EUROPA_LEAGUE, Trofeus.LIBERTADORES)
        assertEquals("nenhum deles deveria repetir imagem", ids.size, ids.distinct().size)
    }

    @Test
    fun `os continentais nao usam o placeholder`() {
        for (nome in listOf("Champions League 2027", "Europa League 2027", "Libertadores 2027")) {
            val t = resolver(nome)
            assertEquals("$nome deveria ter imagem propria, nao placeholder", false, t.tintable)
        }
    }

    // ── A armadilha da EFL Championship ──────────────────────────────────

    @Test
    fun `a efl championship nao e confundida com a champions league`() {
        // "championship" contem "champions": casar por "champions" sozinho
        // mandaria a segunda divisao inglesa para o trofeu da Champions.
        val t = resolver("EFL Championship 2027/28", divisao = 21)
        assertNotEquals(Trofeus.CHAMPIONS_LEAGUE, t.drawable)
        assertNotEquals("Champions League", t.nomeCurto)
    }

    // ── Supercopas ───────────────────────────────────────────────────────

    @Test
    fun `supercopa da uefa nao e mais supercopa rei`() {
        val t = resolver("Supercopa da UEFA 2027")
        assertEquals("Supercopa da UEFA", t.nomeCurto)
        assertEquals(Trofeus.Pais.INTERNACIONAL, t.pais)
        assertNotEquals(Trofeus.SUPERCOPA_REI, t.drawable)
    }

    @Test
    fun `as supercopas europeias nao sao mais supercopa rei`() {
        for ((nome, esperado) in listOf(
            "Supercopa da Inglaterra 2027" to "Supercopa da Inglaterra",
            "Supercopa da Espanha 2027" to "Supercopa da Espanha",
            "Supercopa da Italia 2027" to "Supercopa da Itália"
        )) {
            val t = resolver(nome)
            assertEquals(nome, esperado, t.nomeCurto)
            assertNotEquals(nome, Trofeus.Pais.BRASIL, t.pais)
        }
    }

    @Test
    fun `a supercopa rei brasileira segue intacta`() {
        val t = resolver("Supercopa Rei 2027")
        assertEquals(Trofeus.SUPERCOPA_REI, t.drawable)
        assertEquals("Supercopa Rei", t.nomeCurto)
        assertEquals(Trofeus.Pais.BRASIL, t.pais)
    }

    // ── Não quebrou o que já funcionava ──────────────────────────────────

    @Test
    fun `os campeonatos brasileiros seguem resolvendo`() {
        assertEquals(Trofeus.SERIE_A, resolver("Brasileiro Série A 2027", divisao = 1).drawable)
        assertEquals(Trofeus.SERIE_B, resolver("Brasileiro Série B 2027", divisao = 2).drawable)
        assertEquals(Trofeus.COPA_BRASIL, resolver("Copa do Brasil 2027").drawable)
    }

    @Test
    fun `copa argentina e trofeu dos campeoes seguem resolvendo`() {
        assertEquals(Trofeus.COPA_ARGENTINA, resolver("Copa Argentina 2027").drawable)
        assertEquals("Troféu dos Campeões", resolver("Troféu dos Campeões 2027").nomeCurto)
    }

    @Test
    fun `campeonato desconhecido cai no generico`() {
        val t = resolver("Torneio Inventado 2027")
        assertEquals(Trofeus.GENERICO, t.drawable)
        assertEquals(true, t.tintable)
    }
}
