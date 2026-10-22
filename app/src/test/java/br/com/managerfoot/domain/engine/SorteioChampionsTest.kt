package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Sorteio da Fase de Liga da Champions (ver regras_championsleague.md).
 *
 * O teste central é [sorteio_respeita_todas_as_regras], que roda o sorteio várias
 * vezes com sementes diferentes e valida o resultado inteiro — é o que dá confiança
 * no retrocesso, já que um sorteio ingênuo passa muitas vezes e falha na enésima.
 */
class SorteioChampionsTest {

    /** Lista de acesso adotada: 28 vagas diretas + 8 da qualificação = 36. */
    private val listaDeAcesso = listOf(
        "Inglaterra" to 5,
        "Espanha" to 5,
        "Alemanha" to 4,
        "Italia" to 4,
        "França" to 3,
        "Holanda" to 2,
        "Belgica" to 1,
        "Tchequia" to 1,
        "Turquia" to 1,
        "Portugal" to 1,
        "Ucrania" to 1,
        // As 8 da qualificação, de associações menores
        "Escocia" to 1,
        "Austria" to 1,
        "Suica" to 1,
        "Grecia" to 1,
        "Dinamarca" to 1,
        "Noruega" to 1,
        "Croacia" to 1,
        "Servia" to 1
    )

    /**
     * Clubes da lista de acesso com os coeficientes INTERCALADOS entre países, como
     * acontece de verdade: o pote 1 da Champions reúne os melhores de várias ligas,
     * não os cinco ingleses.
     *
     * A diferença importa: se os coeficientes forem atribuídos em bloco por país, o
     * pote 1 sai com 5 ingleses e 4 espanhóis, e aí o sorteio é IMPOSSÍVEL — dentro
     * do pote os 5 ingleses demandam 10 vagas dos espanhóis, que só oferecem 8.
     * É a restrição de MAX_CLUBES_POR_PAIS_NO_POTE.
     */
    private fun clubesDaListaDeAcesso(): List<ClubeChampions> {
        val filas = listaDeAcesso.map { (pais, vagas) ->
            ArrayDeque((1..vagas).map { pais to it })
        }.toMutableList()

        val clubes = mutableListOf<ClubeChampions>()
        var id = 1
        // Rodízio entre países: um clube de cada vez, até esgotar as vagas.
        while (filas.any { it.isNotEmpty() }) {
            for (fila in filas) {
                val proximo = fila.removeFirstOrNull() ?: continue
                clubes += ClubeChampions(
                    timeId = id,
                    nome = "${proximo.first} ${proximo.second}",
                    pais = proximo.first,
                    coeficiente = 200.0 - id    // decrescente na ordem do rodízio
                )
                id++
            }
        }
        return clubes
    }

    @Test
    fun `a lista de acesso adotada soma 36 clubes`() {
        assertEquals(SorteioChampions.CLUBES, clubesDaListaDeAcesso().size)
    }

    // ── Viabilidade ──────────────────────────────────────────────────────

    @Test
    fun `cinco paises e o minimo para as travas serem satisfaziveis`() {
        // 8 adversários com no máximo 2 por país exigem 4 países além do próprio.
        assertEquals(5, SorteioChampions.MINIMO_DE_PAISES)
    }

    @Test
    fun `nenhum pais pode ter mais de sete clubes na fase de liga`() {
        // 8K ≤ 2(36 − K) → K ≤ 7
        assertEquals(7, SorteioChampions.MAX_CLUBES_POR_PAIS)
    }

    @Test
    fun `os tres paises europeus do seed nao conseguem sortear`() {
        // É o caso real do jogo: Inglaterra, Espanha e Itália com 12 clubes cada.
        var id = 1
        val clubes = listOf("Inglaterra", "Espanha", "Italia").flatMap { pais ->
            (1..12).map { ClubeChampions(id++, "$pais $it", pais, 100.0 - id) }
        }
        assertEquals(36, clubes.size)

        val problemas = SorteioChampions.verificarViabilidade(clubes)
        assertTrue("devia acusar países insuficientes", problemas.any {
            it is InviabilidadeSorteio.PaisesInsuficientes
        })
        assertTrue("devia acusar país com clubes demais", problemas.any {
            it is InviabilidadeSorteio.PaisComClubesDemais
        })
        // E o sorteio recusa, em vez de girar à toa.
        assertNull(SorteioChampions.sortear(clubes, Random(1), tentativas = 2))
    }

    @Test
    fun `lista de acesso adotada e viavel`() {
        assertTrue(SorteioChampions.verificarViabilidade(clubesDaListaDeAcesso()).isEmpty())
    }

    @Test
    fun `pote concentrado num pais e recusado mesmo com o total global ok`() {
        // Coeficientes em bloco por país: o pote 1 sai com 5 ingleses e 4 espanhóis.
        // Nenhum país passa de 5 clubes no torneio (limite global é 7), mas o pote 1
        // é insolúvel — a restrição só aparece pote a pote.
        var id = 1
        val clubes = listaDeAcesso.flatMap { (pais, vagas) ->
            (1..vagas).map { ClubeChampions(id, "$pais $it", pais, 200.0 - id++) }
        }
        val problemas = SorteioChampions.verificarViabilidade(clubes)
        val concentracao = problemas.filterIsInstance<InviabilidadeSorteio.PoteConcentrado>()
        assertTrue("devia acusar pote concentrado, obtido: $problemas", concentracao.isNotEmpty())
        assertEquals(1, concentracao.first().pote)
        // E nenhum país estoura o limite global, provando que a checagem global não basta.
        assertTrue(problemas.none { it is InviabilidadeSorteio.PaisComClubesDemais })
    }

    @Test
    fun `no maximo quatro clubes de um pais por pote`() {
        // 2K ≤ 2(9 − K) → K ≤ 4
        assertEquals(4, SorteioChampions.MAX_CLUBES_POR_PAIS_NO_POTE)
    }

    @Test
    fun `numero de clubes diferente de 36 e recusado`() {
        val clubes = clubesDaListaDeAcesso().drop(1)
        assertTrue(SorteioChampions.verificarViabilidade(clubes).any {
            it is InviabilidadeSorteio.QuantidadeDeClubes
        })
    }

    // ── Potes ────────────────────────────────────────────────────────────

    @Test
    fun `potes sao quatro de nove, por coeficiente decrescente`() {
        val potes = SorteioChampions.montarPotes(clubesDaListaDeAcesso())
        assertEquals(SorteioChampions.POTES, potes.size)
        potes.forEach { assertEquals(SorteioChampions.CLUBES_POR_POTE, it.size) }
        // O melhor coeficiente está no pote 1 e o pior no pote 4.
        val todos = potes.flatten()
        assertEquals(todos.maxOf { it.coeficiente }, potes[0].first().coeficiente, 0.0001)
        assertEquals(todos.minOf { it.coeficiente }, potes[3].last().coeficiente, 0.0001)
        // E cada pote é inteiramente melhor que o seguinte.
        for (i in 0 until SorteioChampions.POTES - 1) {
            assertTrue(
                "pote ${i + 1} deve superar o pote ${i + 2}",
                potes[i].minOf { it.coeficiente } >= potes[i + 1].maxOf { it.coeficiente }
            )
        }
    }

    // ── O sorteio ────────────────────────────────────────────────────────

    @Test
    fun `sorteio respeita todas as regras`() {
        val clubes = clubesDaListaDeAcesso()
        // Várias sementes: um sorteio sem retrocesso passa em algumas e falha noutras.
        for (semente in 1..15) {
            val resultado = SorteioChampions.sortear(clubes, Random(semente))
            assertNotNull("semente $semente não produziu sorteio", resultado)
            val erros = SorteioChampions.validar(resultado!!)
            assertTrue("semente $semente: ${erros.take(5)}", erros.isEmpty())
        }
    }

    @Test
    fun `sorteio produz 144 partidas`() {
        val resultado = SorteioChampions.sortear(clubesDaListaDeAcesso(), Random(7))!!
        assertEquals(144, resultado.confrontos.size)
        assertEquals(SorteioChampions.TOTAL_CONFRONTOS, resultado.confrontos.size)
    }

    @Test
    fun `cada clube joga quatro em casa e quatro fora`() {
        val resultado = SorteioChampions.sortear(clubesDaListaDeAcesso(), Random(11))!!
        for (clube in resultado.potes.flatten()) {
            val casa = resultado.confrontos.count { it.casaId == clube.timeId }
            val fora = resultado.confrontos.count { it.foraId == clube.timeId }
            assertEquals("${clube.nome} em casa", 4, casa)
            assertEquals("${clube.nome} fora", 4, fora)
        }
    }

    @Test
    fun `nenhum clube enfrenta rival do proprio pais`() {
        val resultado = SorteioChampions.sortear(clubesDaListaDeAcesso(), Random(23))!!
        val paisPorId = resultado.potes.flatten().associate { it.timeId to it.pais }
        for (c in resultado.confrontos) {
            assertTrue(
                "confronto entre clubes de ${paisPorId[c.casaId]}",
                paisPorId[c.casaId] != paisPorId[c.foraId]
            )
        }
    }

    @Test
    fun `nenhum clube enfrenta mais de dois rivais do mesmo pais`() {
        val resultado = SorteioChampions.sortear(clubesDaListaDeAcesso(), Random(31))!!
        val paisPorId = resultado.potes.flatten().associate { it.timeId to it.pais }
        for (clube in resultado.potes.flatten()) {
            val adversarios = resultado.confrontos.filter { it.casaId == clube.timeId }.map { it.foraId } +
                resultado.confrontos.filter { it.foraId == clube.timeId }.map { it.casaId }
            val porPais = adversarios.groupingBy { paisPorId[it] }.eachCount()
            porPais.forEach { (pais, qtd) ->
                assertTrue("${clube.nome} tem $qtd rivais de $pais", qtd <= 2)
            }
        }
    }

    @Test
    fun `cada clube enfrenta dois de cada pote, um em casa e um fora`() {
        val resultado = SorteioChampions.sortear(clubesDaListaDeAcesso(), Random(43))!!
        val potePorId = HashMap<Int, Int>()
        resultado.potes.forEachIndexed { i, pote -> pote.forEach { potePorId[it.timeId] = i } }

        for (clube in resultado.potes.flatten()) {
            val casa = resultado.confrontos.filter { it.casaId == clube.timeId }.map { it.foraId }
            val fora = resultado.confrontos.filter { it.foraId == clube.timeId }.map { it.casaId }
            for (pote in 0 until SorteioChampions.POTES) {
                assertEquals("${clube.nome}: casa vs pote ${pote + 1}", 1, casa.count { potePorId[it] == pote })
                assertEquals("${clube.nome}: fora vs pote ${pote + 1}", 1, fora.count { potePorId[it] == pote })
            }
        }
    }

    @Test
    fun `sementes diferentes geram sorteios diferentes`() {
        val clubes = clubesDaListaDeAcesso()
        val a = SorteioChampions.sortear(clubes, Random(2))!!.confrontos.toSet()
        val b = SorteioChampions.sortear(clubes, Random(99))!!.confrontos.toSet()
        assertTrue("o sorteio não deveria ser determinístico", a != b)
    }

    @Test
    fun `validar acusa sorteio adulterado`() {
        val resultado = SorteioChampions.sortear(clubesDaListaDeAcesso(), Random(5))!!
        // Remove uma partida: a validação deve reclamar da contagem e dos clubes afetados.
        val adulterado = resultado.copy(confrontos = resultado.confrontos.drop(1))
        assertTrue(SorteioChampions.validar(adulterado).isNotEmpty())
    }
}
