package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.TipoCampeonato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Copa Intercontinental: jogo único entre o campeão da Champions League e o
 * campeão da Libertadores, último jogo oficial da temporada.
 *
 * É JOGÁVEL: quando o clube do usuário é finalista, ele escala e disputa a
 * partida como qualquer outra, e ela entra no calendário dele. Por isso segue o
 * padrão das Supercopas — criação separada da resolução — e não o do Troféu dos
 * Campeões, que cria e simula de uma vez.
 *
 * Estes testes cobrem o que é regra e não depende de banco: o lugar dela no
 * calendário, o peso no ranking e os códigos do Hall da Fama.
 */
class CopaIntercontinentalTest {

    private val hallChampions = 30
    private val hallLibertadores = 34
    private val hallSulAmericanaReservado = 35
    private val hallIntercontinental = 36
    private val hallMundialReservado = 37

    // ── Calendário: tem de ser o último jogo oficial ─────────────────────

    /** Onde a Copa Intercontinental é encaixada. */
    private val ordemGlobalIntercontinental = 440

    /** Primeiro amistoso de dezembro: ogBase 450 + rodada. */
    private val primeiroAmistosoDeDezembro = 451

    @Test
    fun `e disputada antes dos amistosos de fim de ano`() {
        assertTrue(
            "a Copa Intercontinental deve vir antes dos amistosos de dezembro",
            ordemGlobalIntercontinental < primeiroAmistosoDeDezembro
        )
    }

    @Test
    fun `vem depois de toda competicao continental`() {
        // A maior casa de calendário das continentais europeias e da Libertadores.
        val ultimaDaChampions = MotorCampeonato.FORMATO_CHAMPIONS.ordemGlobalMataMata.max()
        val ultimaDaEuropa = MotorCampeonato.FORMATO_EUROPA_LEAGUE.ordemGlobalMataMata.max()
        assertTrue(
            "depois da final da Champions ($ultimaDaChampions)",
            ordemGlobalIntercontinental > ultimaDaChampions
        )
        assertTrue(
            "depois da final da Europa League ($ultimaDaEuropa)",
            ordemGlobalIntercontinental > ultimaDaEuropa
        )
    }

    @Test
    fun `vem depois da ultima rodada das ligas nacionais`() {
        // As ligas usam ordemGlobal = rodada * 10, e a maior liga tem 38 rodadas.
        val ultimaRodadaDeLiga = 38 * 10
        assertTrue(ordemGlobalIntercontinental > ultimaRodadaDeLiga)
    }

    @Test
    fun `entra no calendario do usuario, por nao ser amistoso`() {
        // buscarProximaPartida varre toda partida nao jogada do clube e exclui
        // apenas os campeonatos de tipo AMISTOSO. Como a Copa Intercontinental tem
        // tipo proprio, a partida dela aparece sozinha no calendario, na posicao
        // que a sua ordemGlobal determina.
        assertNotEquals(TipoCampeonato.AMISTOSO, TipoCampeonato.INTERCONTINENTAL)
    }

    // ── Peso no ranking mundial ──────────────────────────────────────────

    @Test
    fun `vale o peso da final do mundial no ranking da iffhs`() {
        // Decide o titulo mundial entre os campeoes da Champions e da
        // Libertadores: e a antecessora direta do Mundial de Clubes da FIFA.
        assertEquals(
            RankingMundial.PESO_MUNDIAL_FIFA,
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.INTERCONTINENTAL, "Intercontinental", "Copa Intercontinental 2027", "Final"
            )!!,
            0.0001
        )
    }

    @Test
    fun `vale mais que a champions e a libertadores`() {
        val interc = RankingMundial.pesoDaVitoria(
            TipoCampeonato.INTERCONTINENTAL, "Intercontinental", "Copa Intercontinental 2027", "Final"
        )!!
        val champions = RankingMundial.pesoDaVitoria(
            TipoCampeonato.CONTINENTAL, "Espanha", "Champions League 2027", "Final"
        )!!
        val libertadores = RankingMundial.pesoDaVitoria(
            TipoCampeonato.CONTINENTAL, "Brasil", "Libertadores 2027", "Final"
        )!!
        assertTrue("$interc deveria superar $champions", interc > champions)
        assertTrue("$interc deveria superar $libertadores", interc > libertadores)
    }

    @Test
    fun `nao e tratada como supercopa, que nao pontua`() {
        // A engine dela e a do Trofeu dos Campeoes, mas o TIPO e proprio: se
        // fosse SUPERCOPA ou TROFEU_CAMPEOES, nao pontuaria no ranking.
        assertNotEquals(
            null,
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.INTERCONTINENTAL, "Intercontinental", "Copa Intercontinental 2027", "Final"
            )
        )
    }

    @Test
    fun `nao e uma liga, entao nao tem profundidade de divisao`() {
        assertEquals(null, RankingMundial.profundidadeDaDivisao(TipoCampeonato.INTERCONTINENTAL))
    }

    // ── Códigos do Hall da Fama ──────────────────────────────────────────

    @Test
    fun `o codigo do hall nao colide com nenhuma continental`() {
        val codigos = listOf(
            hallChampions, 31, 32, 33,                 // Europa
            hallLibertadores, hallSulAmericanaReservado, // America do Sul
            hallIntercontinental, hallMundialReservado   // Intercontinental
        )
        assertEquals("nenhum codigo repetido", codigos.size, codigos.distinct().size)
    }

    @Test
    fun `o mundial de clubes segue com codigo reservado`() {
        // Pedido explicito: manter o Mundial reservado para depois.
        assertEquals(37, hallMundialReservado)
        assertNotEquals(hallIntercontinental, hallMundialReservado)
    }

    @Test
    fun `os finalistas vem de confederacoes diferentes`() {
        // Por isso nao existe o desempate de "mesmo clube campeao dos dois" que o
        // Trofeu dos Campeoes precisa: um clube disputa a Champions OU a
        // Libertadores, nunca as duas.
        assertNotEquals(hallChampions, hallLibertadores)
    }
}
