package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.PartidaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A Europa League do zero ao campeão, do lado que dá para testar sem banco:
 * a criação da competição (lista de acesso → sorteio → calendário da Fase de
 * Liga) e o avanço de fases (play-off → oitavas → final). Ver europa_league.md.
 *
 * Companheiro de [ChampionsLeagueTest], com duas diferenças que são o ponto:
 *
 *  1. **A ordem.** A Europa League é montada DEPOIS da Champions e recebe os
 *     clubes dela como ocupados, porque um clube não disputa as duas. Os testes
 *     daqui montam as duas na ordem real, e checam que não há interseção.
 *  2. **As casas do calendário.** Cada `ordemGlobal` da Europa League é a da
 *     Champions + 1, e as rodadas do mata-mata partem de 40 contra 20 — um
 *     teste garante que nada colide.
 *
 * Os participantes são **sintéticos**, como em [ListaDeAcessoEuropaTest]: o seed
 * ainda não tem clubes europeus para as duas competições ao mesmo tempo (faltam
 * cerca de 6 — ver o pré-requisito de europa_league.md), e com o seed atual o
 * sorteio da Europa League recusaria por inviabilidade, que é o comportamento
 * correto. O que se testa aqui é a máquina, que já está pronta para os clubes.
 */
class EuropaLeagueTest {

    private val formato = MotorCampeonato.FORMATO_EUROPA_LEAGUE

    // ─────────────────────────────────────────────────────────────────────
    //  Participantes sintéticos: 3 ligas completas + pool largo
    // ─────────────────────────────────────────────────────────────────────

    /** Clube de um país com liga completa: a reputação é inversa à colocação,
     *  então uma vaga dada pela reputação em vez da tabela falha de forma óbvia. */
    private fun comLiga(pais: String, posicao: Int, idBase: Int) = CandidatoChampions(
        timeId      = idBase + posicao,
        nome        = "$pais ${posicao}o",
        pais        = pais,
        coeficiente = 0.0,
        reputacao   = posicao.toFloat(),
        posicaoLiga = posicao
    )

    private fun dePool(pais: String, n: Int, id: Int) = CandidatoChampions(
        timeId      = id,
        nome        = "$pais $n",
        pais        = pais,
        coeficiente = 0.0,
        reputacao   = (100 - n).toFloat()
    )

    /** Associações fora da tabela de vagas, que alimentam as qualificações. */
    private val associacoesMenores = listOf(
        "Grécia", "Dinamarca", "Noruega", "Croácia", "Polônia", "Romênia", "Suécia", "Hungria"
    )

    /**
     * O campo europeu inteiro: as três ligas completas do jogo e um pool com 5
     * clubes por associação — folga suficiente para as 72 vagas das duas
     * competições sem estourar o teto de 7 clubes por país.
     *
     * A reputação é intercalada entre associações de propósito: em bloco por país
     * os potes concentrariam compatriotas e o sorteio seria recusado pela trava
     * de pote, como [ChampionsLeagueTest] também observa.
     */
    private fun campoEuropeu(): List<CandidatoChampions> {
        val comLigas = (1..20).flatMap { posicao ->
            listOf(
                comLiga("Inglaterra", posicao, 10_000),
                comLiga("Espanha", posicao, 20_000),
                comLiga("Italia", posicao, 30_000)
            )
        }
        val associacoesDePool =
            ListaDeAcessoEuropa.VAGAS_DIRETAS.keys
                .filter { it !in setOf("Inglaterra", "Espanha", "Italia") } +
                associacoesMenores
        var id = 50_000
        val pool = (1..5).flatMap { n ->
            associacoesDePool.map { pais -> dePool(pais, n, id++) }
        }
        return comLigas + pool
    }

    /** Campeões das copas nacionais: o 7º de cada liga, fora da zona da Champions. */
    private fun campeoesDaCopa() = mapOf(
        "Inglaterra" to 10_007,
        "Espanha" to 20_007,
        "Italia" to 30_007
    )

    /** A Champions da temporada, montada primeiro, como no repositório. */
    private fun championsLeague(): List<VagaChampions> =
        ListaDeAcessoChampions.montar(campoEuropeu())

    /** A Europa League, montada depois e recebendo os clubes da Champions. */
    private fun europaLeague(): List<VagaEuropa> = ListaDeAcessoEuropa.montar(
        candidatos     = campoEuropeu(),
        campeoesDaCopa = campeoesDaCopa(),
        ocupados       = championsLeague().map { it.candidato.timeId }.toSet()
    )

    private fun faseDeLiga(): List<ClubeChampions> =
        ListaDeAcessoEuropa.paraSorteio(europaLeague())

    // ─────────────────────────────────────────────────────────────────────
    //  Criação da competição
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a competicao e criada depois da champions e sem nenhum clube dela`() {
        val naChampions = championsLeague().map { it.candidato.timeId }.toSet()
        val naEuropa = europaLeague().map { it.candidato.timeId }.toSet()

        assertEquals(SorteioChampions.CLUBES, naChampions.size)
        assertEquals(SorteioChampions.CLUBES, naEuropa.size)
        assertEquals(
            "nenhum clube pode disputar as duas competições",
            emptySet<Int>(), naChampions intersect naEuropa
        )
        // E as duas juntas consomem 72 clubes distintos do campo europeu.
        assertEquals(72, (naChampions + naEuropa).size)
    }

    @Test
    fun `a lista de acesso produz uma fase de liga sortevel`() {
        val clubes = faseDeLiga()
        assertEquals(SorteioChampions.CLUBES, clubes.size)
        assertEquals(emptyList<InviabilidadeSorteio>(), SorteioChampions.verificarViabilidade(clubes))

        repeat(5) { semente ->
            val sorteio = SorteioChampions.sortear(clubes, Random(semente))
            assertNotNull("sorteio falhou na semente $semente", sorteio)
            assertEquals(
                "erros de validação na semente $semente",
                emptyList<String>(), SorteioChampions.validar(sorteio!!)
            )
        }
    }

    @Test
    fun `a fase de liga sai com 144 partidas, 8 por clube, e alimenta a tabela`() {
        val clubes = faseDeLiga()
        val sorteio = SorteioChampions.sortear(clubes, Random(7))!!
        val partidas = MotorCampeonato.gerarFaseLigaChampions(1, sorteio.confrontos, Random(7), formato)

        assertEquals(144, partidas.size)
        clubes.forEach { clube ->
            val jogos = partidas.filter { it.timeCasaId == clube.timeId || it.timeForaId == clube.timeId }
            assertEquals("${clube.nome}: jogos na Fase de Liga", 8, jogos.size)
            assertEquals("${clube.nome}: jogos em casa", 4, jogos.count { it.timeCasaId == clube.timeId })
        }
        // fase = null é o que faz a tabela de 1 a 36 ser alimentada pelo caminho
        // normal de recalcularClassificacao, sem nenhum ramo especial.
        assertTrue(partidas.all { it.fase == null })
        assertEquals(formato.rodadasLiga, partidas.map { it.rodada }.distinct().size)
    }

    @Test
    fun `cada rodada da fase de liga ocupa a sua casa do calendario da europa league`() {
        val sorteio = SorteioChampions.sortear(faseDeLiga(), Random(11))!!
        val partidas = MotorCampeonato.gerarFaseLigaChampions(1, sorteio.confrontos, Random(11), formato)

        val porRodada = partidas.groupBy { it.rodada }
            .mapValues { (_, jogos) -> jogos.map { it.ordemGlobal }.distinct() }
        porRodada.forEach { (rodada, ogs) ->
            assertEquals("rodada $rodada devia ter um ordemGlobal só", 1, ogs.size)
        }
        val ordenadas = porRodada.toSortedMap().values.map { it.single() }
        assertEquals(formato.ordemGlobalLiga.toList(), ordenadas)
        // E o mata-mata vem depois de toda a Fase de Liga.
        assertTrue(formato.ordemGlobalPlayoff.first() > ordenadas.last())
    }

    @Test
    fun `ninguem joga duas vezes na mesma rodada da fase de liga`() {
        repeat(5) { semente ->
            val sorteio = SorteioChampions.sortear(faseDeLiga(), Random(semente))!!
            val partidas =
                MotorCampeonato.gerarFaseLigaChampions(1, sorteio.confrontos, Random(semente), formato)
            partidas.groupBy { it.rodada }.forEach { (rodada, jogos) ->
                val clubes = jogos.flatMap { listOf(it.timeCasaId, it.timeForaId) }
                assertEquals(
                    "rodada $rodada da semente $semente tem clube repetido",
                    clubes.size, clubes.distinct().size
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Avanço de fases: play-off → oitavas → final
    //
    //  A máquina de fases vive no GameRepository, que depende do Room; o que
    //  ela decide são estas funções puras, chamadas aqui na mesma ordem e com
    //  o mesmo FormatoContinental que o repositório passa.
    // ─────────────────────────────────────────────────────────────────────

    /** Tabela final fictícia: o clube de id N terminou na posição N. */
    private val classificados = (1..36).toList()

    @Test
    fun `o play-off reune do 9o ao 24o, com o melhor colocado decidindo em casa`() {
        val pares = MotorCampeonato.paresPlayoffChampions(classificados, formato)
        assertEquals(8, pares.size)
        assertEquals((9..24).toList(), pares.flatMap { listOf(it.first, it.second) }.sorted())
        // par = (mandante da ida, visitante da ida): o melhor colocado recebe a volta.
        pares.forEach { (mandanteIda, visitanteIda) ->
            assertTrue(
                "o pior colocado ($mandanteIda) devia receber a ida, não $visitanteIda",
                visitanteIda < mandanteIda
            )
        }
    }

    @Test
    fun `o play-off e as oitavas entram nas rodadas e casas da europa league`() {
        val playoff = partidasDoPlayoff()
        assertEquals(16, playoff.size)
        assertTrue(playoff.all { it.fase == formato.fasePlayoff })
        assertEquals(setOf(39, 40), playoff.map { it.rodada }.toSet())
        assertEquals(
            formato.ordemGlobalPlayoff.toList(),
            playoff.map { it.ordemGlobal }.distinct().sorted()
        )

        val oitavas = partidasDasOitavas(playoff)
        assertEquals(16, oitavas.size)
        assertEquals(setOf(41, 42), oitavas.map { it.rodada }.toSet())
        // confrontoId continua de onde o play-off parou, sem recomeçar.
        assertEquals(9, oitavas.minOf { it.confrontoId ?: 0 })
        // E o cabeça de chave decide em casa, na volta.
        oitavas.groupBy { it.confrontoId }.forEach { (_, jogos) ->
            val volta = jogos.maxBy { it.rodada }
            assertTrue("volta em casa de ${volta.timeCasaId}", volta.timeCasaId <= 8)
        }
    }

    @Test
    fun `do play-off a final sobra um campeao, em fases de oito a um`() {
        // Percorre o torneio como o repositório: play-off, oitavas e, daí em
        // diante, uma fase por vez com os vencedores da anterior. O "resultado"
        // é determinístico — vence sempre o de menor id, o melhor colocado —
        // porque o que se testa é o encadeamento, não o placar.
        val playoff = partidasDoPlayoff()
        var fase = formato.fasesMataMata.first()
        var partidas = partidasDasOitavas(playoff)
        val tamanhos = mutableListOf(partidas.size / 2)
        var confrontoId = (partidas.mapNotNull { it.confrontoId }.maxOrNull() ?: 0) + 1

        while (true) {
            val vencedores = vencedoresPorConfronto(partidas)
            val proxima = MotorCampeonato.proximaFaseCopa(fase, formato.fasesMataMata) ?: break
            val indice = formato.fasesMataMata.indexOf(proxima)
            partidas = MotorCampeonato.gerarFaseIdaVolta(
                campeonatoId      = 1,
                pares             = vencedores.chunked(2).map { it[0] to it[1] },
                fase              = proxima,
                rodadaIda         = formato.rodadaIdaDeFaseMataMata(indice),
                confrontoIdInicio = confrontoId,
                ordemGlobalIda    = formato.ordemGlobalMataMata[indice * 2],
                ordemGlobalVolta  = formato.ordemGlobalMataMata[indice * 2 + 1]
            )
            confrontoId += partidas.size / 2
            tamanhos += partidas.size / 2
            fase = proxima
        }

        assertEquals("oitavas, quartas, semi e final", listOf(8, 4, 2, 1), tamanhos)
        assertEquals("Final", fase)
        assertEquals(1, vencedoresPorConfronto(partidas).size)
        // A última rodada do torneio é a volta da final, e fecha o totalRodadas.
        assertEquals(formato.totalRodadas, partidas.maxOf { it.rodada })
    }

    private fun partidasDoPlayoff() = MotorCampeonato.gerarFaseIdaVolta(
        campeonatoId      = 1,
        pares             = MotorCampeonato.paresPlayoffChampions(classificados, formato),
        fase              = formato.fasePlayoff,
        rodadaIda         = formato.rodadaIdaPlayoff,
        confrontoIdInicio = 1,
        ordemGlobalIda    = formato.ordemGlobalPlayoff[0],
        ordemGlobalVolta  = formato.ordemGlobalPlayoff[1]
    )

    private fun partidasDasOitavas(playoff: List<PartidaEntity>) = MotorCampeonato.gerarFaseIdaVolta(
        campeonatoId      = 1,
        pares             = MotorCampeonato.paresOitavasChampions(
            classificados, vencedoresPorConfronto(playoff), formato
        ),
        fase              = formato.fasesMataMata.first(),
        rodadaIda         = formato.rodadaIdaDeFaseMataMata(0),
        confrontoIdInicio = (playoff.mapNotNull { it.confrontoId }.maxOrNull() ?: 0) + 1,
        ordemGlobalIda    = formato.ordemGlobalMataMata[0],
        ordemGlobalVolta  = formato.ordemGlobalMataMata[1]
    )

    /** Vence o de menor id — o melhor colocado da Fase de Liga. */
    private fun vencedoresPorConfronto(partidas: List<PartidaEntity>): List<Int> =
        partidas.groupBy { it.confrontoId }
            .toSortedMap(compareBy { it ?: 0 })
            .map { (_, jogos) -> minOf(jogos.first().timeCasaId, jogos.first().timeForaId) }

    // ─────────────────────────────────────────────────────────────────────
    //  Calendário: as duas competições continentais nunca se cruzam
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a europa league joga um dia depois da champions, sem colidir com nada`() {
        val champions = MotorCampeonato.FORMATO_CHAMPIONS
        val casasChampions = (champions.ordemGlobalLiga + champions.ordemGlobalPlayoff +
            champions.ordemGlobalMataMata).toList()
        val casasEuropa = (formato.ordemGlobalLiga + formato.ordemGlobalPlayoff +
            formato.ordemGlobalMataMata).toList()

        // Cada casa da Europa League é a da Champions + 1, como no calendário real.
        assertEquals(casasChampions.map { it + 1 }, casasEuropa)
        assertEquals(emptySet<Int>(), casasChampions.toSet() intersect casasEuropa.toSet())
        // E nenhuma delas cai numa rodada de liga nacional, que usa múltiplos de 10.
        assertTrue(casasEuropa.none { it % 10 == 0 })
    }

    @Test
    fun `as rodadas do mata-mata nao colidem com a fase de liga nem com a champions`() {
        val maiorRodadaDeLiga = formato.rodadasLiga + 4          // teto do fallback
        assertTrue(formato.rodadaIdaPlayoff > maiorRodadaDeLiga)
        assertTrue(formato.rodadaIdaDeFaseMataMata(0) > formato.rodadaIdaPlayoff + 1)

        val idas = formato.fasesMataMata.indices.map { formato.rodadaIdaDeFaseMataMata(it) }
        assertEquals(listOf(41, 43, 45, 47), idas)
        assertEquals(formato.totalRodadas, idas.last() + 1)

        // Faixas de rodada separadas da Champions: depurar duas competições na
        // mesma temporada fica sem ambiguidade.
        val champions = MotorCampeonato.FORMATO_CHAMPIONS
        val rodadasChampions = (champions.rodadaIdaPlayoff..champions.totalRodadas).toSet()
        val rodadasEuropa = (formato.rodadaIdaPlayoff..formato.totalRodadas).toSet()
        assertEquals(emptySet<Int>(), rodadasChampions intersect rodadasEuropa)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Coeficiente da UEFA: a Europa League pontua com os seus pesos
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `os pesos da europa league sao os dela, nao os da champions`() {
        val europa = CompeticaoUefa.EUROPA_LEAGUE
        // Escada do 1º (6,0) ao 24º (0,25) — ver ranking_europa.md.
        assertEquals(6.0, RankingEuropa.bonusPorPosicao(europa, 1), 0.0001)
        assertEquals(0.25, RankingEuropa.bonusPorPosicao(europa, 24), 0.0001)
        // O play-off não dá bônus de avanço; a contagem começa nas oitavas, 1,0 cada.
        assertEquals(0.0, RankingEuropa.bonusPorAvanco(europa, null), 0.0001)
        assertEquals(1.0, RankingEuropa.bonusPorAvanco(europa, FaseUefa.OITAVAS), 0.0001)
        assertEquals(4.0, RankingEuropa.bonusPorAvanco(europa, FaseUefa.FINAL), 0.0001)
    }
}
