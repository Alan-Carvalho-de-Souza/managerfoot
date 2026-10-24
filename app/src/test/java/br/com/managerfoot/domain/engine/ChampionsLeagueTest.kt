package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A Champions do sorteio ao campeão, do lado que dá para testar sem banco:
 * a lista de acesso, o calendário da Fase de Liga e o cruzamento do play-off e
 * das oitavas. Ver regras_championsleague.md.
 *
 * O avanço de fase propriamente dito mora no GameRepository, que depende do Room;
 * o que ele faz de decisão está aqui, nas funções puras que ele chama.
 */
class ChampionsLeagueTest {

    // ─────────────────────────────────────────────────────────────────────
    //  Participantes sintéticos
    //
    //  O pool de clubes europeus (divisões 90/91/92) ainda não existe no seed,
    //  que só tem Inglaterra, Espanha e Itália — e com 3 países o sorteio é
    //  impossível por construção. Então os candidatos aqui são sintéticos, no
    //  mesmo espírito de SorteioChampionsTest.
    // ─────────────────────────────────────────────────────────────────────

    /** Países da lista de acesso, mais alguns de ligas menores para a qualificação. */
    private val paisesMenores = listOf(
        "Escócia", "Áustria", "Suíça", "Grécia", "Dinamarca", "Noruega", "Croácia", "Sérvia"
    )

    /**
     * Candidatos com reputação intercalada entre países, como é de verdade: o
     * pote 1 reúne os melhores de várias ligas, não os cinco ingleses. Em bloco
     * por país o sorteio seria impossível — é a trava por pote.
     *
     * Cada país da lista de acesso oferece o dobro das suas vagas diretas, para
     * que haja de fato uma disputa; os menores oferecem 3 clubes cada.
     */
    private fun candidatos(): List<CandidatoChampions> {
        val filas = buildList {
            ListaDeAcessoChampions.VAGAS_DIRETAS.forEach { (pais, vagas) ->
                add(ArrayDeque((1..vagas * 2).map { pais to it }))
            }
            paisesMenores.forEach { pais ->
                add(ArrayDeque((1..3).map { pais to it }))
            }
        }.toMutableList()

        val lista = mutableListOf<CandidatoChampions>()
        var id = 1
        while (filas.any { it.isNotEmpty() }) {
            for (fila in filas) {
                val proximo = fila.removeFirstOrNull() ?: continue
                lista += CandidatoChampions(
                    timeId      = id,
                    nome        = "${proximo.first} ${proximo.second}",
                    pais        = proximo.first,
                    coeficiente = 0.0,                   // 1ª temporada: tudo zero
                    reputacao   = 200f - id              // reputação decide
                )
                id++
            }
        }
        return lista
    }

    private fun faseDeLiga(): List<ClubeChampions> =
        ListaDeAcessoChampions.paraSorteio(ListaDeAcessoChampions.montar(candidatos()))

    // ── Lista de acesso ──────────────────────────────────────────────────

    @Test
    fun `a lista de acesso tem 28 vagas diretas e 8 da qualificacao`() {
        assertEquals(28, ListaDeAcessoChampions.TOTAL_DIRETAS)
        assertEquals(8, ListaDeAcessoChampions.VAGAS_QUALIFICACAO)
        assertEquals(
            SorteioChampions.CLUBES,
            ListaDeAcessoChampions.TOTAL_DIRETAS + ListaDeAcessoChampions.VAGAS_QUALIFICACAO
        )
    }

    @Test
    fun `montar fecha 36 vagas e respeita as cotas por pais`() {
        val vagas = ListaDeAcessoChampions.montar(candidatos())
        assertEquals(SorteioChampions.CLUBES, vagas.size)

        val diretasPorPais = vagas
            .filter { it.porta == PortaDeEntrada.DIRETA }
            .groupingBy { ListaDeAcessoChampions.paisCanonico(it.candidato.pais) }
            .eachCount()
        ListaDeAcessoChampions.VAGAS_DIRETAS.forEach { (pais, cota) ->
            assertEquals("vagas diretas de $pais", cota, diretasPorPais[pais] ?: 0)
        }
        assertEquals(
            ListaDeAcessoChampions.VAGAS_QUALIFICACAO,
            vagas.count { it.porta == PortaDeEntrada.QUALIFICACAO }
        )
    }

    @Test
    fun `as vagas diretas vao para os melhores de cada pais`() {
        val candidatos = candidatos()
        val vagas = ListaDeAcessoChampions.montar(candidatos)
        val escolhidosIngleses = vagas
            .filter { it.candidato.pais == "Inglaterra" && it.porta == PortaDeEntrada.DIRETA }
            .map { it.candidato.timeId }.toSet()
        val melhoresIngleses = candidatos
            .filter { it.pais == "Inglaterra" }
            .sortedWith(ListaDeAcessoChampions.ORDEM)
            .take(5).map { it.timeId }.toSet()
        assertEquals(melhoresIngleses, escolhidosIngleses)
    }

    @Test
    fun `a qualificacao e das ligas menores, nao dos reservas dos grandes`() {
        // Os reservas de Inglaterra, Espanha e Itália têm as maiores reputações do
        // jogo. Se a qualificação fosse só "os melhores que sobraram", as 8 vagas
        // iriam todas para eles e a Champions viraria um torneio de três países.
        val vagas = ListaDeAcessoChampions.montar(candidatos())
        val qualificados = vagas.filter { it.porta == PortaDeEntrada.QUALIFICACAO }
        assertEquals(8, qualificados.size)
        qualificados.forEach { vaga ->
            assertTrue(
                "${vaga.candidato.nome} tem vaga direta no país, não devia vir pela qualificação",
                ListaDeAcessoChampions.paisCanonico(vaga.candidato.pais) == null
            )
        }
        // E o torneio passa a ter bem mais de 5 países, o mínimo das travas.
        val paises = vagas.map { ListaDeAcessoChampions.paisCanonico(it.candidato.pais) ?: it.candidato.pais }
        assertTrue(
            "${paises.distinct().size} países no torneio",
            paises.distinct().size >= ListaDeAcessoChampions.VAGAS_DIRETAS.size
        )
    }

    @Test
    fun `sem ligas menores a qualificacao cai nos melhores restantes`() {
        // Fallback: se o jogo não tiver clubes de associações fora da tabela, as 8
        // vagas voltam a ser dos melhores restantes — melhor que não ter torneio.
        val semMenores = candidatos().filter { ListaDeAcessoChampions.paisCanonico(it.pais) != null }
        val vagas = ListaDeAcessoChampions.montar(semMenores)
        assertEquals(SorteioChampions.CLUBES, vagas.size)
        assertEquals(8, vagas.count { it.porta == PortaDeEntrada.QUALIFICACAO })
    }

    @Test
    fun `a qualificacao nao estoura o teto de clubes por pais`() {
        // Um país com 8 clubes torna o sorteio impossível (8K ≤ 2(36−K) → K ≤ 7).
        // Aqui a Inglaterra concorre com 30 clubes, muito mais que as suas 5 vagas.
        val inflado = candidatos() + (1..30).map {
            CandidatoChampions(
                timeId = 10_000 + it, nome = "Inglaterra extra $it", pais = "Inglaterra",
                coeficiente = 999.0, reputacao = 99f    // melhores que todo mundo
            )
        }
        val vagas = ListaDeAcessoChampions.montar(inflado)
        val ingleses = vagas.count { it.candidato.pais == "Inglaterra" }
        assertTrue(
            "Inglaterra ficou com $ingleses clubes, acima do teto",
            ingleses <= SorteioChampions.MAX_CLUBES_POR_PAIS
        )
    }

    @Test
    fun `grafias diferentes do mesmo pais nao burlam as travas geograficas`() {
        // "Paises Baixos" e "Holanda" são o mesmo país; se passassem como dois,
        // um clube holandês poderia enfrentar compatriotas na Fase de Liga.
        assertEquals("Holanda", ListaDeAcessoChampions.paisCanonico("Países Baixos"))
        assertEquals("Holanda", ListaDeAcessoChampions.paisCanonico("paises baixos"))
        assertEquals("Italia", ListaDeAcessoChampions.paisCanonico("Itália"))
        assertEquals("Tchéquia", ListaDeAcessoChampions.paisCanonico("República Tcheca"))

        val vaga = VagaChampions(
            CandidatoChampions(1, "Ajax", "Países Baixos", 0.0, 80f),
            PortaDeEntrada.DIRETA
        )
        assertEquals("Holanda", ListaDeAcessoChampions.paraSorteio(listOf(vaga)).single().pais)
    }

    @Test
    fun `montar devolve menos de 36 quando nao ha clubes suficientes`() {
        val poucos = candidatos().take(20)
        assertTrue(ListaDeAcessoChampions.montar(poucos).size < SorteioChampions.CLUBES)
    }

    // ── Classificação pela liga nacional ────────────────────────────────

    /**
     * Candidatos de um país com liga completa: todos têm colocação final, e a
     * reputação é INVERSA à colocação de propósito — o lanterna é o mais
     * reputado. Assim, se a vaga sair pela reputação em vez da tabela, o teste
     * falha de forma evidente.
     */
    private fun candidatosComLiga(pais: String, clubes: Int, idBase: Int): List<CandidatoChampions> =
        (1..clubes).map { posicao ->
            CandidatoChampions(
                timeId      = idBase + posicao,
                nome        = "$pais ${posicao}o",
                pais        = pais,
                coeficiente = 0.0,
                reputacao   = posicao.toFloat(),   // inversa: o 1o tem a pior
                posicaoLiga = posicao
            )
        }

    /** Candidatos do pool, sem liga, dos países que não são o informado. */
    private fun candidatosSemLiga(exceto: List<String>): List<CandidatoChampions> =
        candidatos().filter { ListaDeAcessoChampions.paisCanonico(it.pais) !in exceto }

    @Test
    fun `as vagas diretas vao para os primeiros colocados da liga nacional`() {
        val comLiga = candidatosComLiga("Inglaterra", 20, 10_000) +
            candidatosComLiga("Espanha", 20, 20_000) +
            candidatosComLiga("Italia", 20, 30_000)
        val vagas = ListaDeAcessoChampions.montar(
            comLiga + candidatosSemLiga(listOf("Inglaterra", "Espanha", "Italia"))
        )

        for ((pais, cota) in listOf("Inglaterra" to 5, "Espanha" to 5, "Italia" to 4)) {
            val diretas = vagas
                .filter { ListaDeAcessoChampions.paisCanonico(it.candidato.pais) == pais }
                .filter { it.porta == PortaDeEntrada.DIRETA }
            assertEquals("vagas diretas de $pais", cota, diretas.size)
            // Exatamente as colocações 1..cota, e nada mais.
            assertEquals(
                "classificados de $pais",
                (1..cota).toList(),
                diretas.mapNotNull { it.candidato.posicaoLiga }.sorted()
            )
        }
    }

    @Test
    fun `a reputacao nao fura a fila quando existe colocacao na liga`() {
        val comLiga = candidatosComLiga("Inglaterra", 20, 10_000)
        val maisReputado = comLiga.maxByOrNull { it.reputacao }!!
        assertEquals("o mais reputado devia ser o lanterna", 20, maisReputado.posicaoLiga)

        val vagas = ListaDeAcessoChampions.montar(
            comLiga + candidatosSemLiga(listOf("Inglaterra"))
        )
        val diretasInglesas = vagas
            .filter { ListaDeAcessoChampions.paisCanonico(it.candidato.pais) == "Inglaterra" }
            .filter { it.porta == PortaDeEntrada.DIRETA }
            .map { it.candidato.timeId }
        assertTrue(
            "o 20o colocado nao devia ter vaga direta",
            maisReputado.timeId !in diretasInglesas
        )
    }

    @Test
    fun `a cota por pais e respeitada mesmo com liga completa`() {
        val vagas = ListaDeAcessoChampions.montar(
            candidatosComLiga("Inglaterra", 20, 10_000) + candidatosSemLiga(listOf("Inglaterra"))
        )
        val diretas = vagas.count {
            ListaDeAcessoChampions.paisCanonico(it.candidato.pais) == "Inglaterra" &&
                it.porta == PortaDeEntrada.DIRETA
        }
        assertEquals("20 clubes com colocacao nao viram 20 vagas", 5, diretas)

        val total = vagas.count { ListaDeAcessoChampions.paisCanonico(it.candidato.pais) == "Inglaterra" }
        assertTrue(
            "nenhum pais passa do teto de ${SorteioChampions.MAX_CLUBES_POR_PAIS}",
            total <= SorteioChampions.MAX_CLUBES_POR_PAIS
        )
    }

    @Test
    fun `clube com colocacao vem antes de clube sem colocacao`() {
        val comLiga = CandidatoChampions(1, "Com liga", "Alemanha", 0.0, 10f, posicaoLiga = 9)
        val semLiga = CandidatoChampions(2, "Sem liga", "Alemanha", 0.0, 99f, posicaoLiga = null)
        val ordenado = listOf(semLiga, comLiga).sortedWith(ListaDeAcessoChampions.ORDEM)
        assertEquals(comLiga.timeId, ordenado.first().timeId)
    }

    @Test
    fun `sem liga a ordem continua sendo coeficiente e reputacao`() {
        // Caso do pool estrangeiro e da 1a temporada: ninguem tem colocacao.
        val a = CandidatoChampions(1, "A", "Alemanha", coeficiente = 5.0, reputacao = 50f)
        val b = CandidatoChampions(2, "B", "Alemanha", coeficiente = 9.0, reputacao = 10f)
        val c = CandidatoChampions(3, "C", "Alemanha", coeficiente = 0.0, reputacao = 99f)
        val ordenado = listOf(a, b, c).sortedWith(ListaDeAcessoChampions.ORDEM)
        assertEquals(listOf(2, 1, 3), ordenado.map { it.timeId })
    }

    @Test
    fun `a lista de acesso ainda fecha 36 vagas com as ligas completas`() {
        val vagas = ListaDeAcessoChampions.montar(
            candidatosComLiga("Inglaterra", 20, 10_000) +
                candidatosComLiga("Espanha", 20, 20_000) +
                candidatosComLiga("Italia", 20, 30_000) +
                candidatosSemLiga(listOf("Inglaterra", "Espanha", "Italia"))
        )
        assertEquals(SorteioChampions.CLUBES, vagas.size)
    }

    // ── Geração da competição: o sorteio em cima da lista de acesso ──────

    @Test
    fun `a lista de acesso produz uma fase de liga sortevel`() {
        val clubes = faseDeLiga()
        assertEquals(SorteioChampions.CLUBES, clubes.size)
        assertEquals(emptyList<InviabilidadeSorteio>(), SorteioChampions.verificarViabilidade(clubes))

        repeat(5) { semente ->
            val sorteio = SorteioChampions.sortear(clubes, Random(semente))
            assertTrue("sorteio falhou na semente $semente", sorteio != null)
            assertEquals(
                "erros de validação na semente $semente",
                emptyList<String>(), SorteioChampions.validar(sorteio!!)
            )
        }
    }

    @Test
    fun `na primeira temporada os potes saem pela reputacao, nao em ordem alfabetica`() {
        // Com todos os coeficientes em zero, montarPotes cairia no desempate por
        // NOME: os 5 ingleses viriam juntos num pote e o sorteio seria recusado
        // por concentração. É para isso que a reputação entra como desempate.
        val clubes = faseDeLiga()
        assertTrue("1ª temporada: nenhum coeficiente registrado", candidatos().all { it.coeficiente == 0.0 })

        SorteioChampions.montarPotes(clubes).forEachIndexed { indice, pote ->
            pote.groupingBy { it.pais }.eachCount().forEach { (pais, qtd) ->
                assertTrue(
                    "pote ${indice + 1} tem $qtd clubes de $pais",
                    qtd <= SorteioChampions.MAX_CLUBES_POR_PAIS_NO_POTE
                )
            }
        }
    }

    @Test
    fun `o coeficiente da UEFA manda, e a reputacao so desempata`() {
        val tradicionalEmBaixa = CandidatoChampions(1, "A", "Espanha", coeficiente = 10.0, reputacao = 50f)
        val novoRicoSemHistoria = CandidatoChampions(2, "B", "Espanha", coeficiente = 9.0, reputacao = 100f)
        assertTrue(
            ListaDeAcessoChampions.coeficienteDeSorteio(tradicionalEmBaixa) >
                ListaDeAcessoChampions.coeficienteDeSorteio(novoRicoSemHistoria)
        )
    }

    // ── Calendário da Fase de Liga ───────────────────────────────────────

    @Test
    fun `a fase de liga sai com 144 partidas e 8 por clube`() {
        val clubes = faseDeLiga()
        val sorteio = SorteioChampions.sortear(clubes, Random(7))!!
        val partidas = MotorCampeonato.gerarFaseLigaChampions(1, sorteio.confrontos, Random(7))

        assertEquals(SorteioChampions.TOTAL_CONFRONTOS, partidas.size)
        assertEquals(144, partidas.size)
        clubes.forEach { clube ->
            val jogos = partidas.filter { it.timeCasaId == clube.timeId || it.timeForaId == clube.timeId }
            assertEquals("${clube.nome}: jogos na Fase de Liga", 8, jogos.size)
            assertEquals("${clube.nome}: jogos em casa", 4, jogos.count { it.timeCasaId == clube.timeId })
        }
    }

    @Test
    fun `as partidas da fase de liga tem fase nula, para alimentar a tabela`() {
        // recalcularClassificacao só conta partidas de fase nula: é assim que a
        // tabela de 1 a 36 é alimentada sem nenhum caminho especial.
        val sorteio = SorteioChampions.sortear(faseDeLiga(), Random(3))!!
        val partidas = MotorCampeonato.gerarFaseLigaChampions(1, sorteio.confrontos, Random(3))
        assertTrue(partidas.all { it.fase == null })
    }

    @Test
    fun `ninguem joga duas vezes na mesma rodada da fase de liga`() {
        repeat(5) { semente ->
            val sorteio = SorteioChampions.sortear(faseDeLiga(), Random(semente))!!
            val partidas = MotorCampeonato.gerarFaseLigaChampions(1, sorteio.confrontos, Random(semente))
            partidas.groupBy { it.rodada }.forEach { (rodada, jogos) ->
                val clubes = jogos.flatMap { listOf(it.timeCasaId, it.timeForaId) }
                assertEquals(
                    "rodada $rodada da semente $semente tem clube repetido",
                    clubes.size, clubes.distinct().size
                )
            }
            // 8 rodadas é o mínimo possível e o formato real: cada clube joga
            // uma vez por rodada. A heurística de confronto mais restrito as
            // alcança; a folga de rodadas extras fica só como rede de proteção
            // para o pior caso de Vizing, que não deve aparecer na prática.
            val rodadas = partidas.map { it.rodada }.distinct()
            assertEquals(
                "semente $semente precisou de ${rodadas.size} rodadas",
                MotorCampeonato.CHAMPIONS_RODADAS_LIGA, rodadas.size
            )
        }
    }

    @Test
    fun `cada rodada da fase de liga tem seu lugar no calendario`() {
        val sorteio = SorteioChampions.sortear(faseDeLiga(), Random(11))!!
        val partidas = MotorCampeonato.gerarFaseLigaChampions(1, sorteio.confrontos, Random(11))
        // ordemGlobal é constante dentro da rodada e crescente entre rodadas
        val porRodada = partidas.groupBy { it.rodada }
            .mapValues { (_, jogos) -> jogos.map { it.ordemGlobal }.distinct() }
        porRodada.forEach { (rodada, ogs) ->
            assertEquals("rodada $rodada devia ter um ordemGlobal só", 1, ogs.size)
        }
        val ordenadas = porRodada.toSortedMap().values.map { it.single() }
        assertEquals(ordenadas.sorted(), ordenadas)
        // e o mata-mata vem depois de toda a Fase de Liga
        assertTrue(
            MotorCampeonato.CHAMPIONS_ORDEM_GLOBAL_PLAYOFF.first() > ordenadas.last()
        )
    }

    @Test
    fun `o mata-mata nao colide com as rodadas da fase de liga`() {
        val maiorRodadaDeLiga = MotorCampeonato.CHAMPIONS_RODADAS_LIGA + 4   // teto do fallback
        assertTrue(MotorCampeonato.CHAMPIONS_RODADA_IDA_PLAYOFF > maiorRodadaDeLiga)
        assertTrue(
            MotorCampeonato.rodadaIdaDeFaseChampions(0) >
                MotorCampeonato.CHAMPIONS_RODADA_IDA_PLAYOFF + 1
        )
        // E as rodadas das fases crescem de duas em duas, sem se sobrepor.
        val idas = MotorCampeonato.CHAMPIONS_FASES_MATA_MATA.indices
            .map { MotorCampeonato.rodadaIdaDeFaseChampions(it) }
        assertEquals(listOf(21, 23, 25, 27), idas)
        assertEquals(MotorCampeonato.CHAMPIONS_TOTAL_RODADAS, idas.last() + 1)
    }

    // ── Play-off ─────────────────────────────────────────────────────────

    /** Tabela final fictícia: o clube de id N terminou na posição N. */
    private val classificados = (1..36).toList()

    @Test
    fun `o play-off reune do 9o ao 24o em oito confrontos`() {
        val pares = MotorCampeonato.paresPlayoffChampions(classificados)
        assertEquals(8, pares.size)
        val envolvidos = pares.flatMap { listOf(it.first, it.second) }.sorted()
        assertEquals((9..24).toList(), envolvidos)
        // 1º–8º passam direto; 25º–36º estão eliminados
        assertTrue(envolvidos.none { it <= 8 || it >= 25 })
    }

    @Test
    fun `no play-off o melhor colocado decide em casa`() {
        val pares = MotorCampeonato.paresPlayoffChampions(classificados)
        // par = (mandante da ida, visitante da ida); gerarFaseIdaVolta inverte na
        // volta, então o visitante da ida é quem decide em casa.
        pares.forEach { (mandanteIda, visitanteIda) ->
            assertTrue(
                "o pior colocado ($mandanteIda) devia receber a ida, não $visitanteIda",
                visitanteIda < mandanteIda
            )
        }
        assertEquals(24 to 9, pares.first())
        assertEquals(17 to 16, pares.last())
    }

    @Test
    fun `as partidas do play-off saem com ida e volta e mando invertido`() {
        val pares = MotorCampeonato.paresPlayoffChampions(classificados)
        val partidas = MotorCampeonato.gerarFaseIdaVolta(
            campeonatoId      = 1,
            pares             = pares,
            fase              = MotorCampeonato.CHAMPIONS_FASE_PLAYOFF,
            rodadaIda         = MotorCampeonato.CHAMPIONS_RODADA_IDA_PLAYOFF,
            confrontoIdInicio = 1,
            ordemGlobalIda    = MotorCampeonato.CHAMPIONS_ORDEM_GLOBAL_PLAYOFF[0],
            ordemGlobalVolta  = MotorCampeonato.CHAMPIONS_ORDEM_GLOBAL_PLAYOFF[1]
        )
        assertEquals(16, partidas.size)
        assertTrue(partidas.all { it.fase == MotorCampeonato.CHAMPIONS_FASE_PLAYOFF })
        partidas.groupBy { it.confrontoId }.forEach { (confronto, jogos) ->
            assertEquals("confronto $confronto", 2, jogos.size)
            val ida = jogos.minBy { it.rodada }
            val volta = jogos.maxBy { it.rodada }
            assertEquals(ida.timeCasaId, volta.timeForaId)
            assertEquals(ida.timeForaId, volta.timeCasaId)
        }
    }

    // ── Oitavas: cruzamento com os cabeças de chave ──────────────────────

    /** Vencedores do play-off na ordem dos pares: sempre o melhor colocado. */
    private fun vencedoresPlayoff(): List<Int> =
        MotorCampeonato.paresPlayoffChampions(classificados).map { minOf(it.first, it.second) }

    @Test
    fun `as oitavas cruzam os oito primeiros com os vencedores do play-off`() {
        val vencedores = vencedoresPlayoff()
        assertEquals(listOf(9, 10, 11, 12, 13, 14, 15, 16), vencedores)

        val pares = MotorCampeonato.paresOitavasChampions(classificados, vencedores)
        assertEquals(8, pares.size)

        // Cada cabeça de chave aparece uma vez, e cada vencedor também.
        val cabecas = pares.map { it.second }.sorted()
        assertEquals((1..8).toList(), cabecas)
        assertEquals(vencedores.sorted(), pares.map { it.first }.sorted())

        // 1º contra o vencedor do último par, 8º contra o do primeiro.
        assertEquals(16 to 1, pares.first())
        assertEquals(9 to 8, pares.last())
    }

    @Test
    fun `nas oitavas o cabeca de chave decide em casa`() {
        val pares = MotorCampeonato.paresOitavasChampions(classificados, vencedoresPlayoff())
        val partidas = MotorCampeonato.gerarFaseIdaVolta(
            campeonatoId      = 1,
            pares             = pares,
            fase              = MotorCampeonato.CHAMPIONS_FASES_MATA_MATA.first(),
            rodadaIda         = MotorCampeonato.rodadaIdaDeFaseChampions(0),
            confrontoIdInicio = 9,
            ordemGlobalIda    = MotorCampeonato.CHAMPIONS_ORDEM_GLOBAL_MATA_MATA[0],
            ordemGlobalVolta  = MotorCampeonato.CHAMPIONS_ORDEM_GLOBAL_MATA_MATA[1]
        )
        assertEquals(16, partidas.size)
        partidas.groupBy { it.confrontoId }.forEach { (_, jogos) ->
            val volta = jogos.maxBy { it.rodada }
            // o mandante da volta é o cabeça de chave (posição 1..8)
            assertTrue("volta em casa de ${volta.timeCasaId}", volta.timeCasaId <= 8)
        }
        // confrontoId continua de onde o play-off parou, sem recomeçar
        assertEquals(9, partidas.minOf { it.confrontoId ?: 0 })
    }

    // ── Pontuação no coeficiente da UEFA ────────────────────────────────

    @Test
    fun `o play-off nao da bonus de avanco, mas as oitavas dao`() {
        val champions = CompeticaoUefa.CHAMPIONS_LEAGUE
        // Quem caiu no play-off não alcançou fase pontuável: faseAlcancada = null.
        assertEquals(0.0, RankingEuropa.bonusPorAvanco(champions, null), 0.0001)
        assertEquals(1.5, RankingEuropa.bonusPorAvanco(champions, FaseUefa.OITAVAS), 0.0001)
        assertEquals(6.0, RankingEuropa.bonusPorAvanco(champions, FaseUefa.FINAL), 0.0001)
    }

    @Test
    fun `o bonus de posicao vai de 12 no primeiro a 6 fixo do 25o ao 36o`() {
        val champions = CompeticaoUefa.CHAMPIONS_LEAGUE
        assertEquals(12.0, RankingEuropa.bonusPorPosicao(champions, 1), 0.0001)
        assertEquals(0.25, RankingEuropa.passoDaEscada(champions), 0.0001)
        assertEquals(11.75, RankingEuropa.bonusPorPosicao(champions, 2), 0.0001)
        assertEquals(6.25, RankingEuropa.bonusPorPosicao(champions, 24), 0.0001)
        for (posicao in 25..36) {
            assertEquals(
                "posição $posicao", 6.0,
                RankingEuropa.bonusPorPosicao(champions, posicao), 0.0001
            )
        }
    }

    @Test
    fun `o campeao pontua mais que quem caiu na fase de liga`() {
        val champions = CompeticaoUefa.CHAMPIONS_LEAGUE
        // Campeão: 1º na Fase de Liga, 8 vitórias lá e 8 no mata-mata.
        val campeao = RankingEuropa.pontosDaCampanha(champions, 1, 16, 0, FaseUefa.FINAL)
        // Último: 36º, nenhuma vitória, eliminado na Fase de Liga.
        val ultimo = RankingEuropa.pontosDaCampanha(champions, 36, 0, 0, null)
        assertEquals(12.0 + 32.0 + 6.0, campeao, 0.0001)
        assertEquals(6.0, ultimo, 0.0001)
        assertTrue(campeao > ultimo)
    }

    // ── Elegibilidade ────────────────────────────────────────────────────

    @Test
    fun `os clubes do pool sao elegiveis junto com as divisoes de elite`() {
        // O pool (90/91/92) existe para disputar os torneios continentais: sem ele
        // o jogo tem só 3 países europeus e o sorteio é impossível.
        assertTrue(RankingEuropa.DIVISOES_ELEGIVEIS_CHAMPIONS.containsAll(setOf(11, 13, 14)))
        assertTrue(RankingEuropa.DIVISOES_ELEGIVEIS_CHAMPIONS.containsAll(setOf(90, 91, 92)))
        // A Championship (12) não disputa a Europa.
        assertTrue(12 !in RankingEuropa.DIVISOES_ELEGIVEIS_CHAMPIONS)
    }

    /**
     * Grafias de país exatamente como o seed as escreve — ligas de elite e pool de
     * clubes estrangeiros (divisão 91), conferidas no seed real.
     *
     * O seed escreve sem acento ("Franca", "Belgica", "Tchequia", "Ucrania"), e um
     * alias que não casasse falharia em silêncio: o país perderia TODAS as suas
     * vagas diretas, a lista de acesso não fecharia os 36 e a Champions
     * simplesmente não seria criada, sem nenhum erro. Daí o teste.
     */
    private val grafiasDoSeed = listOf(
        "Inglaterra", "Espanha", "Italia",
        "Alemanha", "Franca", "Holanda", "Belgica", "Tchequia", "Turquia",
        "Portugal", "Ucrania",
        "Escocia", "Grecia", "Austria", "Suica", "Dinamarca", "Noruega",
        "Croacia", "Servia", "Polonia", "Suecia", "Romenia"
    )

    @Test
    fun `as grafias do seed casam com os paises da lista de acesso`() {
        val canonicos = ListaDeAcessoChampions.VAGAS_DIRETAS.keys
        val casados = grafiasDoSeed.mapNotNull { ListaDeAcessoChampions.paisCanonico(it) }.toSet()
        assertEquals(
            "todo pais com vaga direta tem de casar com alguma grafia do seed",
            canonicos, casados
        )
    }

    @Test
    fun `as grafias do seed sao todas reconhecidas como Europa`() {
        // Um país fora de Continentes.PAISES_EUROPA nem chega a ser candidato:
        // determinarParticipantesChampions filtra por ehEuropa antes de tudo.
        grafiasDoSeed.forEach { pais ->
            assertTrue(
                "$pais devia ser Europa",
                br.com.managerfoot.domain.model.Continentes.ehEuropa(pais)
            )
        }
    }

    @Test
    fun `a divisao do pool do seed e elegivel`() {
        // Os clubes estrangeiros do seed estão na divisão 91.
        assertTrue(91 in RankingEuropa.DIVISOES_ELEGIVEIS_CHAMPIONS)
    }

    @Test
    fun `os paises da lista de acesso contam como Europa`() {
        ListaDeAcessoChampions.VAGAS_DIRETAS.keys.forEach { pais ->
            assertTrue("$pais devia ser Europa", br.com.managerfoot.domain.model.Continentes.ehEuropa(pais))
        }
        paisesMenores.forEach { pais ->
            assertTrue("$pais devia ser Europa", br.com.managerfoot.domain.model.Continentes.ehEuropa(pais))
        }
        // E um clube de fora não entra: o pool também abriga o Inter Miami.
        assertNotEquals(
            br.com.managerfoot.domain.model.Continentes.EUROPA,
            br.com.managerfoot.domain.model.Continentes.dePais("Estados Unidos")
        )
    }
}
