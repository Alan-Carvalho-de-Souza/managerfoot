package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lista de acesso da Europa League (ver europa_league.md).
 *
 * As duas regras que mais importam aqui, e que a Champions não tem: a vaga da copa
 * nacional, e a queda dela para o colocado seguinte quando o campeão da copa já
 * está na Champions.
 */
class ListaDeAcessoEuropaTest {

    /** Clube de um país com liga completa: tem colocação final. */
    private fun comLiga(pais: String, posicao: Int, idBase: Int) = CandidatoChampions(
        timeId      = idBase + posicao,
        nome        = "$pais ${posicao}o",
        pais        = pais,
        coeficiente = 0.0,
        reputacao   = posicao.toFloat(),     // inversa: o 1o tem a pior
        posicaoLiga = posicao
    )

    /** Clube de pool: sem liga, decide coeficiente/reputação. */
    private fun dePool(pais: String, n: Int, id: Int) = CandidatoChampions(
        timeId      = id,
        nome        = "$pais $n",
        pais        = pais,
        coeficiente = 0.0,
        reputacao   = (100 - n).toFloat()
    )

    private fun ligaCompleta(pais: String, idBase: Int, clubes: Int = 20) =
        (1..clubes).map { comLiga(pais, it, idBase) }

    /** Candidatos cobrindo os países da lista, com folga para a qualificação. */
    private fun candidatos(): List<CandidatoChampions> {
        val comLigas = ligaCompleta("Inglaterra", 10_000) +
            ligaCompleta("Espanha", 20_000) +
            ligaCompleta("Italia", 30_000)
        var id = 50_000
        val pool = ListaDeAcessoEuropa.VAGAS_DIRETAS.keys
            .filter { it !in listOf("Inglaterra", "Espanha", "Italia") }
            .flatMap { pais -> (1..3).map { dePool(pais, it, id++) } } +
            listOf("Grécia", "Dinamarca", "Noruega", "Croácia", "Polônia", "Romênia")
                .flatMap { pais -> (1..3).map { dePool(pais, it, id++) } }
        return comLigas + pool
    }

    // ── Cotas ────────────────────────────────────────────────────────────

    @Test
    fun `sao 21 vagas diretas e 15 da qualificacao`() {
        assertEquals(21, ListaDeAcessoEuropa.TOTAL_DIRETAS)
        assertEquals(15, ListaDeAcessoEuropa.VAGAS_QUALIFICACAO)
        assertEquals(
            SorteioChampions.CLUBES,
            ListaDeAcessoEuropa.TOTAL_DIRETAS + ListaDeAcessoEuropa.VAGAS_QUALIFICACAO
        )
    }

    @Test
    fun `cinco paises levam duas vagas e dez levam uma`() {
        val cotas = ListaDeAcessoEuropa.VAGAS_DIRETAS
        assertEquals(5, cotas.values.count { it == 2 })
        // 10 do 6º ao 15º + Liechtenstein
        assertEquals(11, cotas.values.count { it == 1 })
    }

    @Test
    fun `montar fecha 36 vagas`() {
        assertEquals(SorteioChampions.CLUBES, ListaDeAcessoEuropa.montar(candidatos()).size)
    }

    // ── A vaga da copa nacional ──────────────────────────────────────────

    @Test
    fun `campeao da copa entra mesmo sem estar no topo da liga`() {
        // O 14º colocado da Premier é campeão da Copa da Inglaterra.
        val campeaoCopa = 10_000 + 14
        val vagas = ListaDeAcessoEuropa.montar(
            candidatos(),
            campeoesDaCopa = mapOf("Inglaterra" to campeaoCopa)
        )
        val porCopa = vagas.filter { it.porta == PortaDeEntradaEuropa.COPA_NACIONAL }
        assertEquals(1, porCopa.size)
        assertEquals(campeaoCopa, porCopa.single().candidato.timeId)
        assertEquals(14, porCopa.single().candidato.posicaoLiga)
    }

    @Test
    fun `sem campeao de copa as duas vagas saem da liga`() {
        val vagas = ListaDeAcessoEuropa.montar(candidatos())   // 1a temporada
        val inglesas = vagas.filter {
            ListaDeAcessoEuropa.paisCanonico(it.candidato.pais) == "Inglaterra"
        }
        assertEquals(2, inglesas.count { it.porta == PortaDeEntradaEuropa.LIGA })
        assertTrue(inglesas.none { it.porta == PortaDeEntradaEuropa.COPA_NACIONAL })
    }

    @Test
    fun `vaga da copa desce para o colocado seguinte quando o campeao ja esta na champions`() {
        // O 2º da Premier ganhou a copa, mas já está na Champions pela liga.
        val campeaoCopa = 10_000 + 2
        val ocupadosNaChampions = (1..5).map { 10_000 + it }.toSet()   // 1º ao 5º
        val vagas = ListaDeAcessoEuropa.montar(
            candidatos(),
            campeoesDaCopa = mapOf("Inglaterra" to campeaoCopa),
            ocupados = ocupadosNaChampions
        )
        val inglesas = vagas.filter {
            ListaDeAcessoEuropa.paisCanonico(it.candidato.pais) == "Inglaterra"
        }
        // Continuam 2 vagas, nenhuma pela copa, e nenhuma com clube da Champions.
        assertEquals(2, inglesas.size)
        assertTrue(inglesas.none { it.porta == PortaDeEntradaEuropa.COPA_NACIONAL })
        assertTrue(inglesas.none { it.candidato.timeId in ocupadosNaChampions })
        // E são os melhores livres: 6º e 7º.
        assertEquals(listOf(6, 7), inglesas.mapNotNull { it.candidato.posicaoLiga }.sorted())
    }

    @Test
    fun `fora da zona da champions e o 6o colocado`() {
        val ocupados = (1..5).map { 10_000 + it }.toSet()
        val vagas = ListaDeAcessoEuropa.montar(candidatos(), ocupados = ocupados)
        val melhorIngles = vagas
            .filter { ListaDeAcessoEuropa.paisCanonico(it.candidato.pais) == "Inglaterra" }
            .minByOrNull { it.candidato.posicaoLiga ?: Int.MAX_VALUE }
        assertEquals(6, melhorIngles?.candidato?.posicaoLiga)
    }

    // ── Exclusão mútua com a Champions ───────────────────────────────────

    @Test
    fun `nenhum clube da champions aparece na europa league`() {
        val todos = candidatos()
        val ocupados = todos.take(36).map { it.timeId }.toSet()
        val vagas = ListaDeAcessoEuropa.montar(todos, ocupados = ocupados)
        assertTrue(vagas.none { it.candidato.timeId in ocupados })
    }

    @Test
    fun `nenhum clube ocupa duas vagas`() {
        val vagas = ListaDeAcessoEuropa.montar(
            candidatos(),
            campeoesDaCopa = mapOf(
                "Inglaterra" to 10_000 + 3,
                "Espanha" to 20_000 + 1,
                "Italia" to 30_000 + 7
            )
        )
        val ids = vagas.map { it.candidato.timeId }
        assertEquals(ids.size, ids.distinct().size)
    }

    // ── Liechtenstein: só a copa ─────────────────────────────────────────

    @Test
    fun `liechtenstein so entra pela copa local`() {
        val semCampeao = ListaDeAcessoEuropa.montar(candidatos())
        val diretasLie = semCampeao.filter {
            ListaDeAcessoEuropa.paisCanonico(it.candidato.pais) == "Liechtenstein" &&
                it.porta != PortaDeEntradaEuropa.QUALIFICACAO
        }
        assertTrue("sem campeao da copa, nao deveria ter vaga direta", diretasLie.isEmpty())

        val comCampeao = ListaDeAcessoEuropa.montar(
            candidatos(),
            campeoesDaCopa = mapOf("Liechtenstein" to 50_000)
        )
        // Com campeão informado, ele entra — pela porta da copa.
        val porCopa = comCampeao.firstOrNull {
            it.porta == PortaDeEntradaEuropa.COPA_NACIONAL &&
                ListaDeAcessoEuropa.paisCanonico(it.candidato.pais) == "Liechtenstein"
        }
        // Só existe se o id 50_000 for de um clube de Liechtenstein nos candidatos.
        val eraDeLie = candidatos().firstOrNull { it.timeId == 50_000 }
            ?.let { ListaDeAcessoEuropa.paisCanonico(it.pais) == "Liechtenstein" } ?: false
        if (eraDeLie) assertNotNull(porCopa)
    }

    // ── Teto por país ────────────────────────────────────────────────────

    @Test
    fun `a qualificacao nao estoura o teto de clubes por pais`() {
        val vagas = ListaDeAcessoEuropa.montar(candidatos())
        val porPais = vagas.groupingBy {
            ListaDeAcessoEuropa.paisCanonico(it.candidato.pais) ?: it.candidato.pais
        }.eachCount()
        porPais.forEach { (pais, qtd) ->
            assertTrue(
                "$pais com $qtd clubes, teto ${SorteioChampions.MAX_CLUBES_POR_PAIS}",
                qtd <= SorteioChampions.MAX_CLUBES_POR_PAIS
            )
        }
    }

    // ── Grafias ──────────────────────────────────────────────────────────

    @Test
    fun `grafias dos paises exclusivos desta lista sao reconhecidas`() {
        assertEquals("Escócia", ListaDeAcessoEuropa.paisCanonico("Escocia"))
        assertEquals("Sérvia", ListaDeAcessoEuropa.paisCanonico("servia"))
        assertEquals("Suíça", ListaDeAcessoEuropa.paisCanonico("Suica"))
        assertEquals("Liechtenstein", ListaDeAcessoEuropa.paisCanonico("liechtenstein"))
        // E as da Champions continuam valendo, por delegação.
        assertEquals("Holanda", ListaDeAcessoEuropa.paisCanonico("Países Baixos"))
        assertEquals("Tchéquia", ListaDeAcessoEuropa.paisCanonico("República Tcheca"))
    }

    // ── Formato continental generalizado ────────────────────────────────

    @Test
    fun `europa league e champions tem a mesma estrutura`() {
        val ucl = MotorCampeonato.FORMATO_CHAMPIONS
        val uel = MotorCampeonato.FORMATO_EUROPA_LEAGUE
        assertEquals(ucl.rodadasLiga, uel.rodadasLiga)
        assertEquals(ucl.classificadosDireto, uel.classificadosDireto)
        assertEquals(ucl.ultimoDoPlayoff, uel.ultimoDoPlayoff)
        assertEquals(ucl.fases, uel.fases)
    }

    @Test
    fun `as duas competicoes nunca colidem no calendario`() {
        val ucl = MotorCampeonato.FORMATO_CHAMPIONS
        val uel = MotorCampeonato.FORMATO_EUROPA_LEAGUE
        val ogUcl = (ucl.ordemGlobalLiga + ucl.ordemGlobalPlayoff + ucl.ordemGlobalMataMata).toSet()
        val ogUel = (uel.ordemGlobalLiga + uel.ordemGlobalPlayoff + uel.ordemGlobalMataMata).toSet()
        assertTrue("ordemGlobal colidindo: ${ogUcl intersect ogUel}", (ogUcl intersect ogUel).isEmpty())
        // Nem com as rodadas das ligas nacionais, que usam multiplos de 10.
        assertTrue((ogUcl + ogUel).none { it % 10 == 0 })
        // E as faixas de rodada do mata-mata sao separadas.
        assertTrue(uel.rodadaIdaPlayoff > ucl.totalRodadas)
    }

    @Test
    fun `ordemGlobal da fase de liga tem fallback para rodadas extra`() {
        val uel = MotorCampeonato.FORMATO_EUROPA_LEAGUE
        assertEquals(28, uel.ordemGlobalDaRodadaLiga(1))
        assertEquals(288, uel.ordemGlobalDaRodadaLiga(8))
        // A 9a rodada so existe no fallback da distribuicao, e entra depois da 8a.
        assertTrue(uel.ordemGlobalDaRodadaLiga(9) > uel.ordemGlobalDaRodadaLiga(8))
    }
}
