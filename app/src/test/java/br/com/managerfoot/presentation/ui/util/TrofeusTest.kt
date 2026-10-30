package br.com.managerfoot.presentation.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Resolução de troféu por nome de campeonato.
 *
 * A resolução casa por substring do nome, então a ORDEM dos ramos é a regra de
 * verdade. Estes testes travam as confusões que ela já causou ou causaria:
 * a EFL Championship virar Champions League, as supercopas continentais e
 * europeias virarem "Supercopa Rei", e a Recopa Sul-Americana virar "Copa
 * Sul-Americana".
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
    fun `a recopa nao e confundida com a copa sul-americana`() {
        // "Recopa Sul-Americana" contém "sul-americana": sem um ramo próprio antes,
        // ela era rotulada como "Copa Sul-Americana", que é outra competição.
        assertEquals("Recopa Sul-Americana", resolver("Recopa Sul-Americana 2027").nomeCurto)
        assertEquals("Copa Sul-Americana", resolver("Copa Sul-Americana 2027").nomeCurto)
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
    // ── As seis imagens novas ────────────────────────────────────────────
    //
    // Duas delas têm o nome do TROFÉU diferente do nome do CAMPEONATO: a FA Cup
    // se chama "Copa da Inglaterra" no rollover, e a Copa do Rei se chama "Copa
    // Espanhola". Casar pelo nome do troféu não acharia nada.

    @Test
    fun `as ligas europeias tem o trofeu delas`() {
        assertEquals(Trofeus.PREMIER_LEAGUE, resolver("Premier League 2027/28").drawable)
        assertEquals(Trofeus.EFL_CHAMPIONSHIP, resolver("EFL Championship 2027/28").drawable)
        assertEquals(Trofeus.LA_LIGA, resolver("La Liga 2027/28").drawable)
    }

    @Test
    fun `as copas europeias tem o trofeu delas, pelo nome do campeonato`() {
        assertEquals(Trofeus.FA_CUP, resolver("Copa da Inglaterra 2027").drawable)
        assertEquals(Trofeus.COPA_DO_REI, resolver("Copa Espanhola 2027").drawable)
    }

    @Test
    fun `a copa sul-americana deixou de usar o placeholder`() {
        val t = resolver("Copa Sul-Americana 2027")
        assertEquals(Trofeus.SULAMERICANA, t.drawable)
        assertNotEquals(Trofeus.GENERICO, t.drawable)
        assertEquals(false, t.tintable)
    }

    @Test
    fun `nenhuma das seis renderiza com tint`() {
        // tintable = false faz a imagem manter as cores dela. Se alguma voltasse
        // ao placeholder, o tint a pintaria de uma cor só e ninguém notaria.
        for (nome in listOf(
            "Premier League 2027/28", "EFL Championship 2027/28", "La Liga 2027/28",
            "Copa da Inglaterra 2027", "Copa Espanhola 2027", "Copa Sul-Americana 2027"
        )) {
            assertEquals(nome, false, resolver(nome).tintable)
        }
    }

    @Test
    fun `as seis sao imagens distintas entre si`() {
        val drawables = listOf(
            Trofeus.PREMIER_LEAGUE, Trofeus.EFL_CHAMPIONSHIP, Trofeus.LA_LIGA,
            Trofeus.FA_CUP, Trofeus.COPA_DO_REI, Trofeus.SULAMERICANA
        )
        assertEquals(drawables.size, drawables.distinct().size)
    }

    @Test
    fun `o pais de cada uma e o certo`() {
        assertEquals(Trofeus.Pais.INGLATERRA, resolver("Premier League 2027/28").pais)
        assertEquals(Trofeus.Pais.INGLATERRA, resolver("EFL Championship 2027/28").pais)
        assertEquals(Trofeus.Pais.INGLATERRA, resolver("Copa da Inglaterra 2027").pais)
        assertEquals(Trofeus.Pais.ESPANHA, resolver("La Liga 2027/28").pais)
        assertEquals(Trofeus.Pais.ESPANHA, resolver("Copa Espanhola 2027").pais)
    }

    // ── As colisões que os ramos novos poderiam criar ────────────────────

    @Test
    fun `a premier league nao e confundida com as europeias`() {
        // "Premier League" contém "league", como "Champions League" e "Europa
        // League". Os ramos continentais casam o nome inteiro, não só "league".
        val t = resolver("Premier League 2027/28")
        assertNotEquals(Trofeus.CHAMPIONS_LEAGUE, t.drawable)
        assertNotEquals(Trofeus.EUROPA_LEAGUE, t.drawable)
    }

    @Test
    fun `a championship continua fora da champions, agora com imagem propria`() {
        val t = resolver("EFL Championship 2027/28", divisao = 21)
        assertEquals(Trofeus.EFL_CHAMPIONSHIP, t.drawable)
        assertNotEquals(Trofeus.CHAMPIONS_LEAGUE, t.drawable)
    }

    @Test
    fun `as copas europeias nao capturam as outras copas`() {
        // Os ramos novos casam por "copa", como os do Brasil e da Argentina.
        assertEquals(Trofeus.COPA_BRASIL, resolver("Copa do Brasil 2027").drawable)
        assertEquals(Trofeus.COPA_ARGENTINA, resolver("Copa Argentina 2027").drawable)
        assertEquals(Trofeus.SULAMERICANA, resolver("Copa Sul-Americana 2027").drawable)
        assertEquals(Trofeus.INTERCONTINENTAL, resolver("Copa Intercontinental 2027").drawable)
    }

    @Test
    fun `a supercopa da espanha nao virou copa do rei`() {
        // "Supercopa da Espanha" contém "espanha", mas não "copa espanhola";
        // e o ramo das supercopas vem antes de qualquer jeito.
        assertEquals("Supercopa da Espanha", resolver("Supercopa da Espanha 2027").nomeCurto)
        assertNotEquals(Trofeus.COPA_DO_REI, resolver("Supercopa da Espanha 2027").drawable)
    }

    @Test
    fun `a coppa italia segue sem imagem propria`() {
        // Não entrou nesta leva: continua no genérico, e isto fixa o fato para
        // quem for adicionar a imagem dela depois.
        assertEquals(Trofeus.GENERICO, resolver("Copa Italiana 2027").drawable)
    }
}
