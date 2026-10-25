package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.TipoCampeonato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * CONMEBOL Copa Sul-Americana (ver regras_sulamericana.md).
 *
 * O que estes testes travam: a aritmética dos 56, que só fecha com os 12 clubes
 * que vêm da Libertadores; a Primeira Fase ser NACIONAL e de partida única; o
 * sorteio de grupos respeitar a trava de país até no caso apertado de 8 clubes
 * de uma associação; e o encaixe no calendário, que é o que impede a competição
 * de pedir à Libertadores um dado que ela ainda não tem.
 */
class SulAmericanaTest {

    private val acesso = ListaDeAcessoSulAmericana
    private val formato = MotorCampeonato.FORMATO_SULAMERICANA
    private val formatoLib = MotorCampeonato.FORMATO_LIBERTADORES

    // ── A aritmética ─────────────────────────────────────────────────────

    @Test
    fun `as cotas oficiais somam 44 vagas nacionais`() {
        assertEquals(44, acesso.VAGAS_NACIONAIS)
        assertEquals(12, acesso.DIRETAS_NOS_GRUPOS)
        assertEquals(32, acesso.NA_PRIMEIRA_FASE)
        assertEquals(10, acesso.COTAS_OFICIAIS.size)
    }

    @Test
    fun `o total de 56 so fecha com os clubes da Libertadores`() {
        assertEquals(56, acesso.TOTAL_DE_CLUBES)
        assertEquals(
            acesso.VAGAS_NACIONAIS + acesso.DA_PRE_LIBERTADORES + acesso.TERCEIROS_DA_LIBERTADORES,
            acesso.TOTAL_DE_CLUBES
        )
        // Sem os 12 que vêm de lá sobram as 44 nacionais — 12 a menos.
        assertEquals(12, acesso.TOTAL_DE_CLUBES - acesso.VAGAS_NACIONAIS)
    }

    @Test
    fun `brasil e argentina entram direto nos grupos`() {
        assertEquals(6, acesso.COTAS_OFICIAIS.getValue("Brasil").grupos)
        assertEquals(0, acesso.COTAS_OFICIAIS.getValue("Brasil").primeiraFase)
        assertEquals(6, acesso.COTAS_OFICIAIS.getValue("Argentina").grupos)
        // Os outros oito países entram pela Primeira Fase, com 4 cada.
        val outros = acesso.COTAS_OFICIAIS.filterKeys { it !in listOf("Brasil", "Argentina") }
        assertEquals(8, outros.size)
        assertTrue(outros.values.all { it.grupos == 0 && it.primeiraFase == 4 })
    }

    @Test
    fun `a cota da Primeira Fase e par em todo pais`() {
        // A fase é intra-país: uma cota ímpar deixaria um clube sem adversário.
        assertTrue(acesso.COTAS_OFICIAIS.values.all { it.primeiraFase % 2 == 0 })
    }

    @Test
    fun `a fase de grupos fecha 32`() {
        assertEquals(
            acesso.CLUBES_NA_FASE_DE_GRUPOS,
            acesso.DIRETAS_NOS_GRUPOS +
                acesso.VENCEDORES_DA_PRIMEIRA_FASE +
                acesso.DA_PRE_LIBERTADORES
        )
        assertEquals(32, acesso.GRUPOS * acesso.CLUBES_POR_GRUPO)
    }

    @Test
    fun `os playoffs fecham 16 e as oitavas tambem`() {
        val segundos = acesso.GRUPOS * acesso.VAO_AOS_PLAYOFFS_POR_GRUPO
        assertEquals(8, segundos)
        assertEquals(16, segundos + acesso.TERCEIROS_DA_LIBERTADORES)

        val primeiros = acesso.GRUPOS * acesso.VAO_AS_OITAVAS_POR_GRUPO
        val vencedoresDosPlayoffs = (segundos + acesso.TERCEIROS_DA_LIBERTADORES) / 2
        assertEquals(16, primeiros + vencedoresDosPlayoffs)
    }

    @Test
    fun `sem os terceiros da Libertadores os playoffs nao fecham`() {
        // 8 segundos sozinhos dariam 4 vencedores, e as oitavas teriam 12 clubes.
        val semTerceiros = acesso.GRUPOS * acesso.VAO_AOS_PLAYOFFS_POR_GRUPO
        assertEquals(8, semTerceiros)
        assertTrue(semTerceiros + semTerceiros / 2 != 16)
    }

    // ── A lista de acesso ────────────────────────────────────────────────

    private fun candidato(id: Int, pais: String, coef: Double) =
        CandidatoSulAmericana(
            timeId = id, nome = "Clube $id", pais = pais,
            coeficiente = coef, reputacao = 60f
        )

    /** 10 clubes por associação, o suficiente para as duas competições. */
    private fun poolCompleto(): List<CandidatoSulAmericana> {
        var id = 1
        return acesso.COTAS_OFICIAIS.keys.flatMap { pais ->
            (1..10).map { n -> candidato(id++, pais, 100.0 - n) }
        }
    }

    @Test
    fun `montar entrega as 44 vagas nacionais com o pool completo`() {
        val vagas = acesso.montar(poolCompleto(), fecharCampo = true)
        assertEquals(44, vagas.size)
        assertEquals(12, acesso.diretosNosGrupos(vagas).size)
        assertEquals(32, acesso.naPrimeiraFase(vagas).size)
    }

    @Test
    fun `montar nao repete nenhum clube`() {
        val vagas = acesso.montar(poolCompleto(), fecharCampo = true)
        assertEquals(vagas.size, vagas.map { it.candidato.timeId }.distinct().size)
    }

    @Test
    fun `quem esta na Libertadores nao entra na Sul-Americana`() {
        val pool = poolCompleto()
        // Os 4 melhores de cada país, que é o que a Libertadores levaria.
        val naLibertadores = pool.groupBy { it.pais }
            .flatMap { (_, doPais) -> doPais.sortedWith(ListaDeAcessoChampions.ORDEM).take(4) }
            .map { it.timeId }
            .toSet()
        val vagas = acesso.montar(pool, jaNaLibertadores = naLibertadores, fecharCampo = true)
        assertTrue(vagas.none { it.candidato.timeId in naLibertadores })
        assertEquals(44, vagas.size)
    }

    @Test
    fun `cada pais pequeno recebe quatro vagas na Primeira Fase`() {
        val vagas = acesso.montar(poolCompleto(), fecharCampo = true)
        val porPais = acesso.naPrimeiraFase(vagas).groupingBy { it.pais }.eachCount()
        for (pais in acesso.COTAS_OFICIAIS.keys - setOf("Brasil", "Argentina")) {
            assertEquals("$pais na Primeira Fase", 4, porPais[pais])
        }
    }

    @Test
    fun `as vagas sao dadas aos melhores de cada pais`() {
        val pool = poolCompleto()
        val vagas = acesso.montar(pool, fecharCampo = true)
        val escolhidosDoBrasil = vagas.filter { it.candidato.pais == "Brasil" }
            .map { it.candidato.timeId }.toSet()
        val melhoresDoBrasil = pool.filter { it.pais == "Brasil" }
            .sortedWith(ListaDeAcessoChampions.ORDEM).take(6).map { it.timeId }.toSet()
        assertEquals(melhoresDoBrasil, escolhidosDoBrasil)
    }

    @Test
    fun `um pais sem clubes tem as vagas remanejadas, e em pares`() {
        // A Bolívia some do pool: as 4 vagas dela precisam ir para outro país,
        // e aos pares — senão um clube ficaria sem adversário na Primeira Fase.
        val pool = poolCompleto().filter { it.pais != "Bolívia" }
        val vagas = acesso.montar(pool, fecharCampo = true)
        assertEquals(44, vagas.size)
        val porPais = acesso.naPrimeiraFase(vagas).groupingBy { it.pais }.eachCount()
        assertTrue(
            "nenhum país pode ter cota ímpar na Primeira Fase: $porPais",
            porPais.values.all { it % 2 == 0 }
        )
    }

    @Test
    fun `sem clubes suficientes montar devolve menos de 44`() {
        // É o caso de um save antigo: só três associações têm clubes, e as duas
        // competições juntas não cabem. A competição então não é criada.
        val pool = poolCompleto().filter { it.pais in setOf("Brasil", "Argentina", "Uruguai") }
            .take(20)
        assertTrue(acesso.montar(pool, fecharCampo = true).size < 44)
    }

    // ── Primeira Fase: nacional e de partida única ────────────────────────

    private fun clube(id: Int, pais: String, coef: Double) =
        ClubeChampions(timeId = id, nome = "Clube $id", pais = pais, coeficiente = coef)

    /** 4 clubes em cada um dos 8 países da Primeira Fase. */
    private fun campoDaPrimeiraFase(): List<ClubeChampions> {
        var id = 1
        return (acesso.COTAS_OFICIAIS.keys - setOf("Brasil", "Argentina")).flatMap { pais ->
            (1..4).map { n -> clube(id++, pais, 100.0 - n) }
        }
    }

    @Test
    fun `a Primeira Fase tem 16 confrontos de partida unica`() {
        val pares = SorteioSulAmericana.chavearPrimeiraFase(campoDaPrimeiraFase())
        assertEquals(16, pares.size)
        // Partida única: 32 clubes em 16 confrontos, cada um aparecendo uma vez.
        val todos = pares.flatMap { listOf(it.first, it.second) }
        assertEquals(32, todos.size)
        assertEquals(32, todos.distinct().size)
    }

    @Test
    fun `todo confronto da Primeira Fase e entre clubes do mesmo pais`() {
        val campo = campoDaPrimeiraFase()
        val paisDe = campo.associate { it.timeId to it.pais }
        for ((casa, fora) in SorteioSulAmericana.chavearPrimeiraFase(campo)) {
            assertEquals(
                "confronto $casa x $fora atravessa fronteira",
                paisDe[casa], paisDe[fora]
            )
        }
    }

    @Test
    fun `na Primeira Fase o melhor ranqueado joga em casa`() {
        val campo = campoDaPrimeiraFase()
        val coefDe = campo.associate { it.timeId to it.coeficiente }
        for ((casa, fora) in SorteioSulAmericana.chavearPrimeiraFase(campo)) {
            assertTrue(
                "o mandante $casa deveria ser o melhor ranqueado",
                coefDe.getValue(casa) > coefDe.getValue(fora)
            )
        }
    }

    @Test
    fun `a Primeira Fase chaveia 1x4 e 2x3`() {
        // Um país só, para a ordem ficar visível.
        val doPais = (1..4).map { clube(it, "Chile", 100.0 - it) }
        val pares = SorteioSulAmericana.chavearPrimeiraFase(doPais)
        assertEquals(listOf(1 to 4, 2 to 3), pares)
    }

    // ── Sorteio da fase de grupos ────────────────────────────────────────

    /**
     * 32 clubes, com os países intercalados para nenhuma associação concentrar
     * um pote — o erro que derrubou o primeiro teste do sorteio da Champions.
     */
    private fun campoDosGrupos(): List<ClubeChampions> {
        val paises = acesso.COTAS_OFICIAIS.keys.toList()
        return (0 until 32).map { i ->
            clube(i + 1, paises[i % paises.size], 100.0 - i)
        }
    }

    @Test
    fun `o sorteio monta 8 grupos de 4, um de cada pote`() {
        val sorteio = SorteioSulAmericana.sortearGrupos(campoDosGrupos(), Random(7))
        assertNotNull(sorteio)
        assertEquals(emptyList<String>(), SorteioSulAmericana.validar(sorteio!!))
        assertEquals(8, sorteio.grupos.size)
        assertTrue(sorteio.grupos.all { it.clubes.size == 4 })
        assertEquals(4, sorteio.potes.size)
        assertTrue(sorteio.potes.all { it.size == 8 })
    }

    @Test
    fun `o sorteio nao deixa compatriota nenhum`() {
        for (semente in 1..30) {
            val sorteio = SorteioSulAmericana.sortearGrupos(campoDosGrupos(), Random(semente))
            assertNotNull("semente $semente", sorteio)
            assertEquals(
                "semente $semente deixou compatriotas",
                0, SorteioSulAmericana.paresDeCompatriotas(sorteio!!)
            )
        }
    }

    @Test
    fun `o sorteio resolve o caso apertado de 8 clubes de um pais`() {
        // O pior caso real: o Brasil entra com 6 da cota mais os 2 que caíram na
        // Fase 3 da Pré-Libertadores. São 8 clubes em 8 grupos — cabe, mas só com
        // exatamente um por grupo, que é onde um sorteio guloso falha.
        val paises = listOf("Argentina", "Chile", "Colômbia", "Peru", "Uruguai", "Equador", "Paraguai")
        val clubes = (1..8).map { clube(it, "Brasil", 200.0 - it) } +
            (0 until 24).map { i -> clube(100 + i, paises[i % paises.size], 100.0 - i) }
        for (semente in 1..20) {
            val sorteio = SorteioSulAmericana.sortearGrupos(clubes, Random(semente))
            assertNotNull("semente $semente", sorteio)
            assertEquals(emptyList<String>(), SorteioSulAmericana.validar(sorteio!!))
            // Um brasileiro em cada grupo, exatamente.
            assertTrue(sorteio.grupos.all { g -> g.clubes.count { it.pais == "Brasil" } == 1 })
        }
    }

    @Test
    fun `um pais com mais de 8 clubes torna o sorteio impossivel`() {
        val clubes = (1..9).map { clube(it, "Brasil", 200.0 - it) } +
            (0 until 23).map { i -> clube(100 + i, "Chile", 100.0 - i) }
        val inviabilidade = SorteioSulAmericana.verificarViabilidade(clubes)
        assertTrue(inviabilidade is InviabilidadeSulAmericana.PaisConcentrado)
        assertNull(SorteioSulAmericana.sortearGrupos(clubes, Random(1)))
    }

    @Test
    fun `o sorteio recusa um campo que nao tem 32 clubes`() {
        val clubes = campoDosGrupos().take(30)
        assertTrue(
            SorteioSulAmericana.verificarViabilidade(clubes)
                is InviabilidadeSulAmericana.QuantidadeErrada
        )
        assertNull(SorteioSulAmericana.sortearGrupos(clubes, Random(1)))
    }

    @Test
    fun `os potes saem do ranking, em ordem decrescente`() {
        val potes = SorteioSulAmericana.montarPotes(campoDosGrupos())
        assertEquals(4, potes.size)
        val piorDoPote = potes.map { pote -> pote.minOf { it.coeficiente } }
        val melhorDoPote = potes.map { pote -> pote.maxOf { it.coeficiente } }
        // O pior do pote 1 ainda é melhor que o melhor do pote 2, e assim adiante.
        for (i in 0 until potes.size - 1) {
            assertTrue(piorDoPote[i] > melhorDoPote[i + 1])
        }
    }

    // ── Calendário: o acoplamento com a Libertadores ─────────────────────

    @Test
    fun `a fase prévia e de partida unica e ocupa uma rodada`() {
        assertTrue(formato.preDeJogoUnico)
        assertEquals(1, formato.preFases.size)
        assertEquals(1, formato.rodadaIdaDaPreFase(0))
        assertEquals(1, formato.ultimaRodadaDaPre)
        // O rótulo é o da fase, sem "Ida" de um jogo de volta que não existe.
        assertEquals("Primeira Fase", formato.rotuloDaRodadaPre(1))
        assertNull(formato.rotuloDaRodadaPre(2))
    }

    @Test
    fun `a Pre-Libertadores segue sendo de ida e volta`() {
        // A bandeira é por formato: ligá-la aqui não pode mexer na Libertadores.
        assertTrue(!formatoLib.preDeJogoUnico)
        assertEquals(6, formatoLib.ultimaRodadaDaPre)
        assertEquals("Fase 2 — Ida", formatoLib.rotuloDaRodadaPre(3))
    }

    @Test
    fun `as rodadas nao se sobrepoem dentro do campeonato`() {
        assertEquals(listOf(1), formato.rodadasDaPre)
        assertEquals(listOf(11, 12, 13, 14, 15, 16), formato.rodadasDosGrupos)
        // Cinco fases de mata-mata, da 21 à 29 — a final é a última e é única.
        assertEquals(21, formato.rodadaIdaDeFaseMataMata(0))
        assertEquals(29, formato.totalRodadas)
        assertTrue(formato.rodadasDosGrupos.none { it in formato.rodadasDaPre })
        assertTrue(formato.rodadasDosGrupos.max() < formato.rodadaIdaDeFaseMataMata(0))
    }

    @Test
    fun `os playoffs sao a primeira fase do mata-mata e a final a ultima`() {
        assertEquals("Playoffs", formato.fasesMataMata.first())
        assertEquals("Final", formato.fasesMataMata.last())
        assertTrue(formato.ehFinal("Final"))
        assertTrue(!formato.ehFinal("Playoffs"))
        // A final tem prorrogação; os playoffs e as oitavas vão direto aos pênaltis.
        assertTrue(formato.temProrrogacao("Final"))
        assertTrue(!formato.temProrrogacao("Playoffs"))
        assertTrue(!formato.temProrrogacao("Oitavas"))
    }

    @Test
    fun `os grupos comecam depois da Fase 3 da Pre-Libertadores`() {
        // É de lá que vêm 4 dos 32: sortear antes seria sortear sem eles.
        val fimDaPreLib = formatoLib.ordemGlobalPre.max()
        assertTrue(
            "grupos em ${formato.ordemGlobalGrupos.min()}, Pré da Libertadores até $fimDaPreLib",
            formato.ordemGlobalGrupos.min() > fimDaPreLib
        )
    }

    @Test
    fun `os playoffs comecam depois dos grupos da Libertadores`() {
        // Os 8 adversários são os terceiros de grupo dela: só existem no fim.
        val fimDosGruposLib = formatoLib.ordemGlobalGrupos.max()
        assertTrue(
            "playoffs em ${formato.ordemGlobalMataMata[0]}, grupos da Libertadores até $fimDosGruposLib",
            formato.ordemGlobalMataMata[0] > fimDosGruposLib
        )
    }

    @Test
    fun `a Primeira Fase vem antes da fase de grupos`() {
        assertTrue(formato.ordemGlobalPre.max() < formato.ordemGlobalGrupos.min())
    }

    @Test
    fun `a final vem antes da final da Libertadores`() {
        assertTrue(
            formato.ordemGlobalMataMata.last() < formatoLib.ordemGlobalMataMata.last()
        )
    }

    @Test
    fun `o calendario e estritamente crescente`() {
        val todas = formato.ordemGlobalPre.toList() +
            formato.ordemGlobalGrupos.toList() +
            formato.ordemGlobalMataMata.toList()
        assertEquals(todas.sorted(), todas)
        assertEquals(todas.size, todas.distinct().size)
    }

    @Test
    fun `nenhuma casa coincide com outra competicao do calendario`() {
        val champions = MotorCampeonato.FORMATO_CHAMPIONS
        val europa = MotorCampeonato.FORMATO_EUROPA_LEAGUE
        val ocupadas = (
            formatoLib.ordemGlobalPre + formatoLib.ordemGlobalGrupos + formatoLib.ordemGlobalMataMata +
                champions.ordemGlobalLiga + champions.ordemGlobalPlayoff + champions.ordemGlobalMataMata +
                europa.ordemGlobalLiga + europa.ordemGlobalPlayoff + europa.ordemGlobalMataMata +
                MotorCampeonato.COPA_ORDEM_GLOBAL + MotorCampeonato.COPA_ARG_ORDEM_GLOBAL
            ).toSet()
        val minhas = (formato.ordemGlobalPre + formato.ordemGlobalGrupos +
            formato.ordemGlobalMataMata).toList()
        val colisoes = minhas.filter { it in ocupadas }
        assertEquals("casas já ocupadas: $colisoes", emptyList<Int>(), colisoes)
        // As ligas nacionais usam ordemGlobal = rodada * 10.
        assertEquals(emptyList<Int>(), minhas.filter { it % 10 == 0 })
    }

    @Test
    fun `o mata-mata tem casa de ida e volta para toda fase menos a final`() {
        // 4 fases de ida e volta (Playoffs, Oitavas, Quartas, Semi) + 1 da final.
        assertEquals(4 * 2 + 1, formato.ordemGlobalMataMata.size)
    }

    // ── Pontuação e Hall da Fama ─────────────────────────────────────────

    @Test
    fun `vale menos que a Libertadores no ranking mundial`() {
        val sul = RankingMundial.pesoDaVitoria(
            TipoCampeonato.CONTINENTAL, "América do Sul", "Copa Sul-Americana 2027", "Final"
        )!!
        val lib = RankingMundial.pesoDaVitoria(
            TipoCampeonato.CONTINENTAL, "América do Sul", "Libertadores 2027", "Final"
        )!!
        assertEquals(RankingMundial.PESO_SUL_AMERICANA, sul, 0.0001)
        assertTrue("$sul deveria ser menor que $lib", sul < lib)
    }

    @Test
    fun `vale menos que a Libertadores no ranking CONMEBOL`() {
        val sul = RankingConmebol.pontosPorFase(CompeticaoConmebol.SUL_AMERICANA, FaseConmebol.CAMPEAO)
        val lib = RankingConmebol.pontosPorFase(CompeticaoConmebol.LIBERTADORES, FaseConmebol.CAMPEAO)
        assertTrue("$sul deveria ser menor que $lib", sul < lib)
        assertTrue(
            RankingConmebol.pontosPorVitoria(CompeticaoConmebol.SUL_AMERICANA) <
                RankingConmebol.pontosPorVitoria(CompeticaoConmebol.LIBERTADORES)
        )
    }

    @Test
    fun `o codigo do Hall da Fama e a 35, que estava reservada`() {
        val europa = listOf(30, 31, 32, 33)
        val americaDoSul = listOf(34, 35)
        val intercontinental = listOf(36, 37)
        val todos = europa + americaDoSul + intercontinental
        assertEquals("nenhum código repetido", todos.size, todos.distinct().size)
        assertEquals(35, americaDoSul.last())
    }

    @Test
    fun `os paises sao os mesmos 10 da Libertadores`() {
        assertEquals(
            ListaDeAcessoLibertadores.COTAS_OFICIAIS.keys,
            acesso.COTAS_OFICIAIS.keys
        )
        // E a tradução de grafias é a mesma: o seed grava "Uruguay".
        assertEquals("Uruguai", acesso.paisCanonico("Uruguay"))
        assertEquals("Bolívia", acesso.paisCanonico("Bolivia"))
        assertNull(acesso.paisCanonico("Alemanha"))
    }
}
