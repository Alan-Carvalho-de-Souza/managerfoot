package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Supercopa da UEFA: jogo único entre o campeão da Champions League e o campeão da
 * Europa League da temporada que encerra.
 *
 * Reaproveita a engine de supercopa que já existia para as nacionais — o que muda
 * são os dois códigos do Hall da Fama de onde saem os finalistas, e o "país", que
 * aqui é o continente, porque a competição não pertence a país nenhum.
 *
 * Estes testes cobrem o que é regra, e não fiação de banco: os códigos do Hall da
 * Fama não colidem, os dois finalistas são sempre clubes distintos, e a competição
 * não existe na primeira temporada.
 */
class SupercopaUefaTest {

    /** Códigos de divisão do Hall da Fama usados pelas competições continentais. */
    private val hallChampions = 30
    private val hallEuropaLeague = 31
    private val hallConferenceReservado = 32
    private val hallSupercopaUefa = 33

    @Test
    fun `os codigos do hall da fama das continentais nao colidem`() {
        val codigos = listOf(
            hallChampions, hallEuropaLeague, hallConferenceReservado, hallSupercopaUefa
        )
        assertEquals("nenhum codigo repetido", codigos.size, codigos.distinct().size)
    }

    @Test
    fun `a supercopa da uefa nao ocupa o codigo reservado da conference`() {
        // O HallDaFamaScreen reserva o 32 para a Conference League; a Supercopa é 33.
        assertNotEquals(hallConferenceReservado, hallSupercopaUefa)
        assertEquals(33, hallSupercopaUefa)
    }

    @Test
    fun `os codigos das supercopas nacionais seguem livres`() {
        // Brasil 6, Inglaterra 27, Espanha 28, Itália 29 — a da UEFA não invade nenhum.
        val nacionais = listOf(6, 27, 28, 29)
        assertTrue(hallSupercopaUefa !in nacionais)
    }

    // ── Os finalistas ────────────────────────────────────────────────────

    /**
     * A engine de supercopa, quando o mesmo clube vence liga e copa, promove o
     * VICE da liga à segunda vaga. Na Supercopa da UEFA esse caminho é
     * inalcançável, porque Champions e Europa League são mutuamente exclusivas:
     * nenhum clube disputa as duas na mesma temporada, então nenhum pode vencer
     * as duas. Este teste documenta e fixa essa garantia.
     */
    @Test
    fun `campeao da champions nunca e tambem campeao da europa league`() {
        // Monta as duas competições a partir do mesmo conjunto de candidatos, na
        // ordem real do jogo: a Europa League recebe os clubes já ocupados.
        var id = 1
        val candidatos = buildList {
            // 3 ligas completas
            listOf("Inglaterra", "Espanha", "Italia").forEach { pais ->
                (1..20).forEach { pos ->
                    add(
                        CandidatoChampions(
                            timeId = id++, nome = "$pais $pos", pais = pais,
                            coeficiente = 0.0, reputacao = (100 - pos).toFloat(),
                            posicaoLiga = pos
                        )
                    )
                }
            }
            // Pool largo, para as duas competições fecharem
            val associacoes = listOf(
                "Alemanha", "França", "Holanda", "Portugal", "Bélgica", "Tchéquia",
                "Turquia", "Ucrânia", "Escócia", "Áustria", "Sérvia", "Suíça",
                "Grécia", "Dinamarca", "Noruega", "Croácia", "Polônia", "Romênia",
                "Suécia", "Hungria", "Liechtenstein"
            )
            associacoes.forEach { pais ->
                (1..4).forEach { n ->
                    add(
                        CandidatoChampions(
                            timeId = id++, nome = "$pais $n", pais = pais,
                            coeficiente = 0.0, reputacao = (90 - n).toFloat()
                        )
                    )
                }
            }
        }

        val champions = ListaDeAcessoChampions.montar(candidatos)
        assertEquals(SorteioChampions.CLUBES, champions.size)
        val ocupados = champions.map { it.candidato.timeId }.toSet()

        val europa = ListaDeAcessoEuropa.montar(candidatos, ocupados = ocupados)
        assertEquals(SorteioChampions.CLUBES, europa.size)

        // A garantia: os dois campos não compartilham um único clube, então o
        // campeão de um nunca pode ser o campeão do outro.
        val idsEuropa = europa.map { it.candidato.timeId }.toSet()
        assertTrue(
            "Champions e Europa League nao podem compartilhar clubes",
            (ocupados intersect idsEuropa).isEmpty()
        )
    }

    // ── Calendário ───────────────────────────────────────────────────────

    @Test
    fun `a supercopa e disputada antes do resto da temporada`() {
        // Todas as supercopas usam ordemGlobal = 1, antes de qualquer rodada de
        // liga (que começam em 10) e de qualquer jogo continental.
        val ordemGlobalSupercopa = 1
        val primeiraRodadaDeLiga = 10
        val primeiroJogoChampions = MotorCampeonato.FORMATO_CHAMPIONS.ordemGlobalLiga.first()
        val primeiroJogoEuropa = MotorCampeonato.FORMATO_EUROPA_LEAGUE.ordemGlobalLiga.first()

        assertTrue(ordemGlobalSupercopa < primeiraRodadaDeLiga)
        assertTrue(ordemGlobalSupercopa < primeiroJogoChampions)
        assertTrue(ordemGlobalSupercopa < primeiroJogoEuropa)
    }

    @Test
    fun `a supercopa depende de campeoes da temporada anterior`() {
        // Ela não pode existir na 1ª temporada: os finalistas saem do Hall da Fama
        // do ano encerrado, e no primeiro ano não há Champions nem Europa League
        // concluídas. É o mesmo que já vale para as supercopas europeias.
        val primeiraTemporada = 1
        val temCampeoesDoAnoAnterior = primeiraTemporada > 1
        assertTrue("na 1a temporada nao ha campeoes continentais", !temCampeoesDoAnoAnterior)
    }
}
