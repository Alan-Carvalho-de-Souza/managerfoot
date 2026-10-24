package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * CONMEBOL Libertadores (ver regras_libertadores.md).
 *
 * O que estes testes travam: a aritmética das 47 vagas, o papel estrutural das
 * duas vagas de campeão vigente, a redistribuição quando faltam associações, a
 * estrutura das três fases prévias e o sorteio dos 8 grupos de 4.
 */
class LibertadoresTest {

    private val acesso = ListaDeAcessoLibertadores

    // ── A aritmética ─────────────────────────────────────────────────────

    @Test
    fun `as cotas oficiais somam 45 vagas nacionais`() {
        assertEquals(45, acesso.VAGAS_NACIONAIS)
        assertEquals(26, acesso.DIRETAS_NOS_GRUPOS)
        assertEquals(19, acesso.NA_PRE_LIBERTADORES)
        assertEquals(10, acesso.COTAS_OFICIAIS.size)
    }

    @Test
    fun `o total de clubes e 47`() {
        assertEquals(47, acesso.TOTAL_DE_CLUBES)
        assertEquals(acesso.VAGAS_NACIONAIS + acesso.VAGAS_CAMPEOES_VIGENTES, acesso.TOTAL_DE_CLUBES)
    }

    @Test
    fun `as vagas de campeao vigente sao estruturais`() {
        // Sem elas a fase de grupos teria 30 clubes, nao 32.
        val semCampeoes = acesso.DIRETAS_NOS_GRUPOS + acesso.SOBREVIVENTES_DA_PRE
        assertEquals(30, semCampeoes)
        assertEquals(
            acesso.CLUBES_NA_FASE_DE_GRUPOS,
            semCampeoes + acesso.VAGAS_CAMPEOES_VIGENTES
        )
    }

    @Test
    fun `brasil e argentina tem cinco vagas diretas`() {
        assertEquals(5, acesso.COTAS_OFICIAIS.getValue("Brasil").grupos)
        assertEquals(2, acesso.COTAS_OFICIAIS.getValue("Brasil").pre)
        assertEquals(7, acesso.COTAS_OFICIAIS.getValue("Brasil").total)
        assertEquals(5, acesso.COTAS_OFICIAIS.getValue("Argentina").grupos)
        assertEquals(6, acesso.COTAS_OFICIAIS.getValue("Argentina").total)
        // Os outros oito paises tem 4 cada.
        val outros = acesso.COTAS_OFICIAIS.filterKeys { it !in listOf("Brasil", "Argentina") }
        assertEquals(8, outros.size)
        assertTrue(outros.values.all { it.total == 4 && it.grupos == 2 })
    }

    // ── A Pré-Libertadores ───────────────────────────────────────────────

    @Test
    fun `as tres fases previas entregam exatamente quatro sobreviventes`() {
        // Fase 1: 6 → 3 vencedores. Fase 2: 3 + 13 = 16 → 8. Fase 3: 8 → 4.
        assertEquals(6, acesso.PRE_FASE1_CLUBES)
        val vencedoresFase1 = acesso.PRE_FASE1_CLUBES / 2
        assertEquals(3, vencedoresFase1)
        assertEquals(
            acesso.PRE_FASE2_CLUBES,
            vencedoresFase1 + acesso.PRE_FASE2_ENTRAM_DIRETO
        )
        val vencedoresFase2 = acesso.PRE_FASE2_CLUBES / 2
        assertEquals(acesso.PRE_FASE3_CLUBES, vencedoresFase2)
        assertEquals(acesso.SOBREVIVENTES_DA_PRE, acesso.PRE_FASE3_CLUBES / 2)
    }

    @Test
    fun `os 19 clubes da pre cabem sem ninguem receber bye`() {
        // 19 e impar, mas a entrada escalonada absorve a sobra: so 6 comecam na
        // Fase 1, e os outros 13 entram na Fase 2.
        assertEquals(
            acesso.NA_PRE_LIBERTADORES,
            acesso.PRE_FASE1_CLUBES + acesso.PRE_FASE2_ENTRAM_DIRETO
        )
        // Toda fase tem numero par de clubes, logo nenhum bye.
        assertEquals(0, acesso.PRE_FASE1_CLUBES % 2)
        assertEquals(0, acesso.PRE_FASE2_CLUBES % 2)
        assertEquals(0, acesso.PRE_FASE3_CLUBES % 2)
    }

    @Test
    fun `sao tres fases previas`() {
        assertEquals(3, acesso.PRE_FASES.size)
    }

    // ── Redistribuição de vagas ──────────────────────────────────────────

    @Test
    fun `com as dez associacoes as cotas sao as oficiais`() {
        assertEquals(acesso.COTAS_OFICIAIS, acesso.cotasEfetivas(acesso.COTAS_OFICIAIS.keys))
    }

    @Test
    fun `com tres associacoes as vagas sao redistribuidas sem perder nenhuma`() {
        // O caso real do jogo: so Brasil, Argentina e Uruguai tem clubes.
        val cotas = acesso.cotasEfetivas(setOf("Brasil", "Argentina", "Uruguai"))
        assertEquals(3, cotas.size)
        assertEquals("nenhuma vaga de grupo perdida", acesso.DIRETAS_NOS_GRUPOS, cotas.values.sumOf { it.grupos })
        assertEquals("nenhuma vaga de pre perdida", acesso.NA_PRE_LIBERTADORES, cotas.values.sumOf { it.pre })
        assertEquals("o total continua 45", acesso.VAGAS_NACIONAIS, cotas.values.sumOf { it.total })
    }

    @Test
    fun `a redistribuicao respeita a hierarquia das cotas oficiais`() {
        val cotas = acesso.cotasEfetivas(setOf("Brasil", "Argentina", "Uruguai"))
        // Brasil (cota 7) >= Argentina (6) >= Uruguai (4)
        assertTrue(cotas.getValue("Brasil").total >= cotas.getValue("Argentina").total)
        assertTrue(cotas.getValue("Argentina").total >= cotas.getValue("Uruguai").total)
    }

    @Test
    fun `pais sem clube nenhum nao recebe vaga`() {
        val cotas = acesso.cotasEfetivas(setOf("Brasil", "Argentina"))
        assertTrue("Chile" !in cotas)
        assertEquals(acesso.VAGAS_NACIONAIS, cotas.values.sumOf { it.total })
    }

    @Test
    fun `sem nenhuma associacao nao ha cotas`() {
        assertTrue(acesso.cotasEfetivas(emptySet()).isEmpty())
    }

    // ── Montagem do campo ────────────────────────────────────────────────

    private fun clubes(pais: String, quantos: Int, idBase: Int) =
        (1..quantos).map { pos ->
            CandidatoLibertadores(
                timeId = idBase + pos, nome = "$pais $pos", pais = pais,
                coeficiente = 0.0, reputacao = (100 - pos).toFloat(), posicaoLiga = pos
            )
        }

    /** O caso real do jogo: 3 associações, com elenco de clubes de sobra. */
    private fun candidatos() =
        clubes("Brasil", 40, 10_000) +
            clubes("Argentina", 30, 20_000) +
            clubes("Uruguai", 20, 30_000)

    @Test
    fun `montar entrega 45 vagas nacionais`() {
        val vagas = acesso.montar(candidatos())
        assertEquals(acesso.VAGAS_NACIONAIS, vagas.size)
    }

    @Test
    fun `montar separa diretos e pre nas quantidades certas`() {
        val vagas = acesso.montar(candidatos())
        assertEquals(acesso.DIRETAS_NOS_GRUPOS, acesso.diretosNosGrupos(vagas).size)
        assertEquals(acesso.NA_PRE_LIBERTADORES, acesso.naPreLibertadores(vagas).size)
    }

    @Test
    fun `com os campeoes vigentes a fase de grupos fecha em 32`() {
        val todos = candidatos()
        // Dois campeões vigentes que NÃO estariam entre os melhores das suas ligas.
        val campLib = todos.first { it.pais == "Uruguai" && it.posicaoLiga == 18 }.timeId
        val campSul = todos.first { it.pais == "Brasil" && it.posicaoLiga == 35 }.timeId

        val vagas = acesso.montar(todos, campeaoLibertadores = campLib, campeaoSulAmericana = campSul)
        val diretos = acesso.diretosNosGrupos(vagas)

        assertEquals(
            "26 diretos + 2 campeoes = 28; com os 4 da Pre fecha 32",
            acesso.DIRETAS_NOS_GRUPOS + acesso.VAGAS_CAMPEOES_VIGENTES,
            diretos.size
        )
        assertEquals(
            acesso.CLUBES_NA_FASE_DE_GRUPOS,
            diretos.size + acesso.SOBREVIVENTES_DA_PRE
        )
        // E os dois campeões estão entre eles, pela porta de campeão vigente.
        val porCampeao = vagas.filter { it.porta == PortaDaLibertadores.CAMPEAO_VIGENTE }
        assertEquals(2, porCampeao.size)
        assertEquals(setOf(campLib, campSul), porCampeao.map { it.candidato.timeId }.toSet())
    }

    @Test
    fun `campeao vigente nao ocupa duas vagas`() {
        val todos = candidatos()
        // Campeão vigente que TAMBÉM seria o 1º do Brasil, logo já teria vaga.
        val campLib = todos.first { it.pais == "Brasil" && it.posicaoLiga == 1 }.timeId
        val vagas = acesso.montar(todos, campeaoLibertadores = campLib)

        val ids = vagas.map { it.candidato.timeId }
        assertEquals("nenhum clube repetido", ids.size, ids.distinct().size)
        // A vaga nacional do Brasil desceu para o colocado seguinte: o país segue
        // com a sua cota cheia, e o campeão entra por fora.
        val brasileiros = vagas.count { it.candidato.pais == "Brasil" }
        val cotaBrasil = acesso.cotasEfetivas(setOf("Brasil", "Argentina", "Uruguai"))
            .getValue("Brasil").total
        assertEquals(cotaBrasil + 1, brasileiros)
    }

    @Test
    fun `o mesmo clube campeao das duas competicoes ocupa uma vaga so`() {
        val todos = candidatos()
        val id = todos.first().timeId
        val vagas = acesso.montar(todos, campeaoLibertadores = id, campeaoSulAmericana = id)
        assertEquals(1, vagas.count { it.porta == PortaDaLibertadores.CAMPEAO_VIGENTE })
    }

    // ── Sorteio dos grupos ───────────────────────────────────────────────

    private fun trintaEDois(): List<ClubeChampions> {
        var id = 1
        // 3 países, como o seed real — compatriotas em grupo são inevitáveis.
        return listOf("Brasil" to 14, "Argentina" to 11, "Uruguai" to 7).flatMap { (pais, n) ->
            (1..n).map { ClubeChampions(id, "$pais $it", pais, 200.0 - id++) }
        }
    }

    @Test
    fun `o sorteio monta 8 grupos de 4, um clube de cada pote`() {
        for (semente in 1..10) {
            val r = SorteioLibertadores.sortear(trintaEDois(), Random(semente))
            assertNotNull("semente $semente", r)
            val erros = SorteioLibertadores.validar(r!!)
            assertTrue("semente $semente: $erros", erros.isEmpty())
            assertEquals(8, r.grupos.size)
            assertTrue(r.grupos.all { it.clubes.size == 4 })
        }
    }

    @Test
    fun `os potes sao quatro de oito, por coeficiente`() {
        val potes = SorteioLibertadores.montarPotes(trintaEDois())
        assertEquals(4, potes.size)
        assertTrue(potes.all { it.size == 8 })
        for (i in 0 until 3) {
            assertTrue(
                "pote ${i + 1} deve superar o pote ${i + 2}",
                potes[i].minOf { it.coeficiente } >= potes[i + 1].maxOf { it.coeficiente }
            )
        }
    }

    @Test
    fun `numero de clubes diferente de 32 e recusado`() {
        assertNull(SorteioLibertadores.sortear(trintaEDois().drop(1), Random(1)))
    }

    @Test
    fun `o sorteio minimiza compatriotas em vez de proibir`() {
        // Com 3 paises, um grupo de 4 nao cabe em paises distintos: o minimo
        // teorico de pares de compatriotas e maior que zero. O sorteio nao falha,
        // e fica perto desse minimo.
        val r = SorteioLibertadores.sortear(trintaEDois(), Random(7))!!
        val pares = SorteioLibertadores.paresDeCompatriotas(r)
        assertTrue("deveria haver compatriotas com so 3 paises", pares > 0)
        // 8 grupos de 4 com 3 paises: cada grupo tem ao menos 1 par repetido.
        assertTrue("pares demais: $pares", pares <= 16)
    }

    @Test
    fun `com dez associacoes o sorteio separa todos os compatriotas`() {
        var id = 1
        val dezPaises = acesso.COTAS_OFICIAIS.keys.toList()
        // 32 clubes bem espalhados: no maximo 4 por pais.
        val clubes = (0 until 32).map {
            val pais = dezPaises[it % dezPaises.size]
            ClubeChampions(id, "$pais $it", pais, 200.0 - id++)
        }
        val r = SorteioLibertadores.sortear(clubes, Random(3))!!
        assertEquals(
            "com 10 paises nao deveria sobrar compatriota em grupo",
            0, SorteioLibertadores.paresDeCompatriotas(r)
        )
    }

    // ── A fiação: calendário, avanço de fases e final única ──────────────
    //
    // Daqui para baixo os testes cobrem o que a fiação acrescentou: a geração
    // dos grupos em turno e returno, as casas do calendário, o cruzamento das
    // oitavas e a final de jogo único.

    private val formato = MotorCampeonato.FORMATO_LIBERTADORES

    /** Oito grupos de 4, com ids 1..32 na ordem A..H. */
    private fun oitoGruposDeQuatro(): List<List<Int>> =
        (0 until 8).map { g -> (1..4).map { g * 4 + it } }

    @Test
    fun `a fase de grupos e turno e returno`() {
        val partidas = MotorCampeonato.gerarGruposTurnoReturno(
            campeonatoId         = 1,
            grupos               = oitoGruposDeQuatro(),
            rodadaBase           = formato.rodadaBaseGrupos,
            ordemGlobalPorRodada = formato.ordemGlobalGrupos
        )
        // 8 grupos x 12 partidas = 96, em 6 rodadas.
        assertEquals(96, partidas.size)
        assertEquals(6, formato.rodadasGrupos)
        val rodadas = partidas.map { it.rodada }.distinct().sorted()
        assertEquals((11..16).toList(), rodadas)

        // 6 partidas por clube, e 16 por rodada (uma para cada dois dos 32).
        for (timeId in 1..32) {
            val doClube = partidas.count { it.timeCasaId == timeId || it.timeForaId == timeId }
            assertEquals("clube $timeId devia jogar 6 partidas", 6, doClube)
        }
        for (rodada in 11..16) {
            assertEquals(16, partidas.count { it.rodada == rodada })
        }
    }

    @Test
    fun `no turno e returno cada par se enfrenta duas vezes, uma em cada casa`() {
        val partidas = MotorCampeonato.gerarGruposTurnoReturno(
            campeonatoId = 1,
            grupos       = oitoGruposDeQuatro(),
            rodadaBase   = formato.rodadaBaseGrupos
        )
        // Dentro de um grupo de 4 há 6 pares, e cada par joga 2 vezes.
        for (grupo in oitoGruposDeQuatro()) {
            val doGrupo = partidas.filter { it.timeCasaId in grupo }
            assertEquals(12, doGrupo.size)
            val mandos = doGrupo.map { it.timeCasaId to it.timeForaId }
            // Cada mando acontece exatamente uma vez: A×B no turno, B×A no returno.
            assertEquals(12, mandos.distinct().size)
            for ((casa, fora) in mandos) {
                assertTrue("falta o jogo de volta de $casa x $fora", (fora to casa) in mandos)
            }
        }
        // Ninguém joga duas vezes na mesma rodada.
        for (rodada in partidas.map { it.rodada }.distinct()) {
            val clubes = partidas.filter { it.rodada == rodada }
                .flatMap { listOf(it.timeCasaId, it.timeForaId) }
            assertEquals("clube repetido na rodada $rodada", clubes.size, clubes.distinct().size)
        }
    }

    @Test
    fun `as partidas de grupo tem fase nula, para alimentar a classificacao`() {
        val partidas = MotorCampeonato.gerarGruposTurnoReturno(
            campeonatoId = 1,
            grupos       = oitoGruposDeQuatro(),
            rodadaBase   = formato.rodadaBaseGrupos
        )
        assertTrue(partidas.all { it.fase == null })
    }

    @Test
    fun `as casas do calendario nao colidem com as europeias nem com as ligas`() {
        val ucl = MotorCampeonato.FORMATO_CHAMPIONS
        val uel = MotorCampeonato.FORMATO_EUROPA_LEAGUE
        val casasEuropeias = (
            ucl.ordemGlobalLiga + ucl.ordemGlobalPlayoff + ucl.ordemGlobalMataMata +
                uel.ordemGlobalLiga + uel.ordemGlobalPlayoff + uel.ordemGlobalMataMata
            ).toSet()
        val casasLibertadores = (
            formato.ordemGlobalPre + formato.ordemGlobalGrupos + formato.ordemGlobalMataMata
            ).toList()

        val colisao = casasLibertadores.filter { it in casasEuropeias }
        assertTrue("ordemGlobal colidindo com a Europa: $colisao", colisao.isEmpty())

        // As rodadas das ligas nacionais usam ordemGlobal = rodada * 10.
        val multiplosDeDez = casasLibertadores.filter { it % 10 == 0 }
        assertTrue("ordemGlobal colidindo com liga nacional: $multiplosDeDez", multiplosDeDez.isEmpty())

        // E as casas da própria Libertadores não se repetem entre si.
        assertEquals(casasLibertadores.size, casasLibertadores.distinct().size)
    }

    @Test
    fun `as faixas de rodada da pre, dos grupos e do mata-mata nao se sobrepoem`() {
        val pre = formato.preFases.indices.flatMap { i ->
            val ida = formato.rodadaIdaDaPreFase(i)
            listOf(ida, ida + 1)
        }
        val grupos = (formato.rodadaBaseGrupos until formato.rodadaBaseGrupos + formato.rodadasGrupos).toList()
        val mataMata = formato.fasesMataMata.indices.flatMap { i ->
            val ida = formato.rodadaIdaDeFaseMataMata(i)
            // A final é jogo único: ocupa uma rodada só.
            if (formato.ehFinal(formato.fasesMataMata[i])) listOf(ida) else listOf(ida, ida + 1)
        }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), pre)
        assertEquals((11..16).toList(), grupos)
        assertEquals(listOf(21, 22, 23, 24, 25, 26, 27), mataMata)

        val todas = pre + grupos + mataMata
        assertEquals("faixas de rodada se sobrepondo", todas.size, todas.distinct().size)
        assertEquals(27, formato.totalRodadas)
        assertEquals(formato.totalRodadas, mataMata.max())
    }

    @Test
    fun `a pre-libertadores cabe em 19 clubes sem bye nenhum`() {
        // Fase 1: 6 clubes -> 3 vencedores. Fase 2: 3 + 13 = 16 -> 8. Fase 3: 8 -> 4.
        assertEquals(6, acesso.PRE_FASE1_CLUBES)
        assertEquals(13, acesso.PRE_FASE2_ENTRAM_DIRETO)
        assertEquals(16, acesso.PRE_FASE2_CLUBES)
        assertEquals(8, acesso.PRE_FASE3_CLUBES)
        assertEquals(
            acesso.NA_PRE_LIBERTADORES,
            acesso.PRE_FASE1_CLUBES + acesso.PRE_FASE2_ENTRAM_DIRETO
        )
        assertEquals(acesso.PRE_FASE2_CLUBES, acesso.PRE_FASE1_CLUBES / 2 + acesso.PRE_FASE2_ENTRAM_DIRETO)
        assertEquals(acesso.PRE_FASE3_CLUBES, acesso.PRE_FASE2_CLUBES / 2)
        assertEquals(acesso.SOBREVIVENTES_DA_PRE, acesso.PRE_FASE3_CLUBES / 2)
        // Toda fase tem número par: é o que dispensa o bye.
        assertTrue(
            listOf(acesso.PRE_FASE1_CLUBES, acesso.PRE_FASE2_CLUBES, acesso.PRE_FASE3_CLUBES)
                .all { it % 2 == 0 }
        )
        assertEquals(3, formato.preFases.size)
    }

    @Test
    fun `as oitavas cruzam primeiro de um grupo com segundo de outro`() {
        val primeiros = (1..8).toList()          // 1º dos grupos A..H
        val segundos  = (11..18).toList()        // 2º dos grupos A..H
        val pares = MotorCampeonato.paresOitavasLibertadores(primeiros, segundos)

        assertEquals(8, pares.size)
        // Os 16 classificados aparecem uma vez cada.
        val todos = pares.flatMap { listOf(it.first, it.second) }
        assertEquals(16, todos.distinct().size)

        pares.forEachIndexed { i, (casaIda, foraIda) ->
            // O 1º colocado decide em casa, então é o VISITANTE da ida.
            assertEquals(primeiros[i], foraIda)
            // O 2º adversário nunca é do mesmo grupo: o deslocamento é de meia volta.
            assertEquals(segundos[(i + 4) % 8], casaIda)
            assertTrue("grupo $i se reencontrou", segundos.indexOf(casaIda) != i)
        }
    }

    @Test
    fun `o avanco de fase do mata-mata vai das oitavas a final`() {
        val fases = formato.fasesMataMata
        assertEquals(listOf("Oitavas", "Quartas", "Semi", "Final"), fases)
        // 16 nas oitavas, 8 nas quartas, 4 na semi, 2 na final.
        var vivos = ListaDeAcessoLibertadores.GRUPOS * ListaDeAcessoLibertadores.AVANCAM_POR_GRUPO
        assertEquals(16, vivos)
        for (fase in fases) {
            val confrontos = vivos / 2
            assertTrue("fase $fase sem confronto", confrontos >= 1)
            vivos = confrontos
        }
        // Sobra exatamente um: o campeão.
        assertEquals(1, vivos)
    }

    @Test
    fun `a final e jogo unico e as fases anteriores sao ida e volta`() {
        val vencedoresDaSemi = listOf(7, 9)
        val finalUnica = MotorCampeonato.gerarFaseJogoUnico(
            campeonatoId      = 1,
            pares             = listOf(vencedoresDaSemi[0] to vencedoresDaSemi[1]),
            fase              = formato.fasesMataMata.last(),
            rodada            = formato.rodadaIdaDeFaseMataMata(formato.fasesMataMata.lastIndex),
            confrontoIdInicio = 50,
            ordemGlobal       = formato.ordemGlobalMataMata.last()
        )
        // Uma partida só, no confronto só — é o que o chaveamento e o desempate
        // usam para tratar a final como jogo único em vez de agregado.
        assertEquals(1, finalUnica.size)
        assertEquals("Final", finalUnica[0].fase)
        assertEquals(27, finalUnica[0].rodada)
        assertEquals(385, finalUnica[0].ordemGlobal)
        assertEquals(1, finalUnica.map { it.confrontoId }.distinct().size)

        // As semis, por contraste, são ida e volta: 2 partidas por confronto.
        val semi = MotorCampeonato.gerarFaseIdaVolta(
            campeonatoId      = 1,
            pares             = listOf(1 to 2, 3 to 4),
            fase              = "Semi",
            rodadaIda         = formato.rodadaIdaDeFaseMataMata(2),
            confrontoIdInicio = 40,
            ordemGlobalIda    = formato.ordemGlobalMataMata[4],
            ordemGlobalVolta  = formato.ordemGlobalMataMata[5]
        )
        assertEquals(4, semi.size)
        assertTrue(semi.groupBy { it.confrontoId }.values.all { it.size == 2 })
    }

    @Test
    fun `so a final tem prorrogacao antes dos penaltis`() {
        // Das oitavas às semis o empate no agregado vai direto aos pênaltis.
        for (fase in listOf("Oitavas", "Quartas", "Semi")) {
            assertTrue("$fase nao devia ter prorrogacao", !formato.temProrrogacao(fase))
            assertTrue("$fase nao e a final", !formato.ehFinal(fase))
        }
        assertTrue(formato.temProrrogacao("Final"))
        assertTrue(formato.ehFinal("Final"))
    }

    @Test
    fun `o agregado empatado das oitavas vai aos penaltis, sem gol fora`() {
        // 1x2 na ida e 2x1 na volta: 3x3 no agregado. Sem gol de visitante como
        // critério, o confronto não tem vencedor direto — vai aos pênaltis.
        val vencedor = MotorCampeonato.determinarVencedorTie(
            timeCasaIdaId = 1, timeForaIdaId = 2,
            golsCasaIda = 1, golsForaIda = 2,
            golsCasaVolta = 1, golsForaVolta = 2
        )
        assertNull("empate no agregado deve ir aos penaltis", vencedor)
    }

    @Test
    fun `o chaveamento da libertadores comeca na pre e termina na final`() {
        // É esta lista que a tela de Chaveamento usa para montar os chips de fase.
        assertEquals(
            listOf("Fase 1", "Fase 2", "Fase 3", "Oitavas", "Quartas", "Semi", "Final"),
            formato.fases
        )
    }

    @Test
    fun `o nome da competicao da o peso 14 do ranking mundial`() {
        // É o nome que identifica a competição para a IFFHS — ver RankingMundial.
        val peso = RankingMundial.pesoDaVitoria(
            tipo           = br.com.managerfoot.data.database.entities.TipoCampeonato.CONTINENTAL,
            paisCampeonato = br.com.managerfoot.domain.model.Continentes.AMERICA_DO_SUL,
            nomeCampeonato = "Libertadores 2026",
            fase           = null
        )
        assertEquals(14.0, peso!!, 0.001)
    }

    @Test
    fun `o bloco continental do ranking conmebol pontua a libertadores`() {
        val lib = CompeticaoConmebol.LIBERTADORES
        assertEquals(1000.0, RankingConmebol.pontosPorFase(lib, FaseConmebol.CAMPEAO), 0.001)
        assertEquals(400.0, RankingConmebol.pontosPorFase(lib, FaseConmebol.VICE), 0.001)
        assertEquals(240.0, RankingConmebol.pontosPorFase(lib, FaseConmebol.SEMIFINAL), 0.001)
        assertEquals(160.0, RankingConmebol.pontosPorFase(lib, FaseConmebol.QUARTAS), 0.001)
        assertEquals(80.0, RankingConmebol.pontosPorFase(lib, FaseConmebol.OITAVAS), 0.001)
        assertEquals(20.0, RankingConmebol.pontosPorFase(lib, FaseConmebol.FASE_DE_GRUPOS), 0.001)
        assertEquals(80.0, RankingConmebol.pontosPorVitoria(lib), 0.001)
        assertEquals(40.0, RankingConmebol.pontosPorEmpate(lib), 0.001)
    }

    @Test
    fun `o pais do clube e canonizado antes de casar com as cotas`() {
        // O seed grava "Uruguay"; as cotas sao indexadas por "Uruguai". Sem a
        // traducao as quatro vagas uruguaias ficariam orfas.
        assertEquals("Uruguai", acesso.paisCanonico("Uruguay"))
        assertEquals("Uruguai", acesso.paisCanonico(" uruguai "))
        assertEquals("Brasil", acesso.paisCanonico("Brazil"))
        assertEquals("Colômbia", acesso.paisCanonico("Colombia"))
        assertNull(acesso.paisCanonico("Inglaterra"))
        // Todas as chaves oficiais são reconhecidas por elas mesmas.
        for (pais in acesso.COTAS_OFICIAIS.keys) {
            assertEquals(pais, acesso.paisCanonico(pais))
        }
    }

    @Test
    fun `com tres associacoes o campo inteiro sai do seed atual`() {
        // Brasil, Argentina e Uruguai sao os tres paises do seed. A redistribuicao
        // tem de entregar os 47 clubes, senao a competicao nao e criada.
        var id = 1
        val candidatos = listOf("Brasil" to 20, "Argentina" to 30, "Uruguai" to 32)
            .flatMap { (pais, quantos) ->
                (1..quantos).map { pos ->
                    CandidatoLibertadores(
                        timeId = id++, nome = "$pais $pos", pais = pais,
                        coeficiente = (quantos - pos).toDouble(), reputacao = 80f,
                        posicaoLiga = pos
                    )
                }
            }
        val vagas = acesso.montar(
            candidatos,
            campeaoLibertadores = 1,
            fecharFaseDeGrupos  = true
        )
        val diretos = acesso.diretosNosGrupos(vagas)
        val naPre = acesso.naPreLibertadores(vagas)

        // 45 nacionais + o campeao vigente + a vaga do campeao da Sul-Americana,
        // que nao existe no jogo e por isso desceu para o proximo colocado.
        assertEquals(acesso.TOTAL_DE_CLUBES, vagas.size)
        assertEquals(acesso.NA_PRE_LIBERTADORES, naPre.size)
        // Os diretos têm de fechar os 32 dos grupos junto com os 4 da Pré.
        assertEquals(
            acesso.CLUBES_NA_FASE_DE_GRUPOS - acesso.SOBREVIVENTES_DA_PRE,
            diretos.size
        )
        // Ninguém aparece duas vezes.
        val todos = (diretos + naPre).map { it.timeId }
        assertEquals(todos.size, todos.distinct().size)
    }

    @Test
    fun `sem campeao vigente as vagas estruturais descem e os grupos fecham em 32`() {
        // Na primeira temporada nao houve Libertadores ainda, e a Copa Sul-Americana
        // nem existe no jogo: as DUAS vagas de campeao vigente ficam sem titular.
        // Elas sao estruturais, entao descem para os proximos colocados — senao a
        // fase de grupos teria 30 clubes e a competicao nao poderia ser criada.
        val vagas = acesso.montar(candidatos(), fecharFaseDeGrupos = true)
        val diretos = acesso.diretosNosGrupos(vagas)

        assertEquals(
            "os diretos tem de fechar 32 junto com os 4 da Pre",
            acesso.CLUBES_NA_FASE_DE_GRUPOS - acesso.SOBREVIVENTES_DA_PRE,
            diretos.size
        )
        assertEquals(acesso.NA_PRE_LIBERTADORES, acesso.naPreLibertadores(vagas).size)
        assertEquals(acesso.TOTAL_DE_CLUBES, vagas.size)
        // Ninguem entra duas vezes.
        val ids = vagas.map { it.candidato.timeId }
        assertEquals(ids.size, ids.distinct().size)
        // Sem titular, nenhuma vaga fica marcada como de campeao vigente.
        assertTrue(vagas.none { it.porta == PortaDaLibertadores.CAMPEAO_VIGENTE })
    }

    @Test
    fun `com um campeao vigente so a outra vaga estrutural desce`() {
        val todos = candidatos()
        val campLib = todos.first { it.pais == "Uruguai" && it.posicaoLiga == 18 }.timeId
        val vagas = acesso.montar(todos, campeaoLibertadores = campLib, fecharFaseDeGrupos = true)

        assertEquals(1, vagas.count { it.porta == PortaDaLibertadores.CAMPEAO_VIGENTE })
        assertEquals(
            acesso.CLUBES_NA_FASE_DE_GRUPOS - acesso.SOBREVIVENTES_DA_PRE,
            acesso.diretosNosGrupos(vagas).size
        )
        assertEquals(acesso.TOTAL_DE_CLUBES, vagas.size)
    }

    @Test
    fun `com os dois campeoes vigentes nada desce`() {
        val todos = candidatos()
        val campLib = todos.first { it.pais == "Uruguai" && it.posicaoLiga == 18 }.timeId
        val campSul = todos.first { it.pais == "Brasil" && it.posicaoLiga == 35 }.timeId
        val comFechamento = acesso.montar(todos, campLib, campSul, fecharFaseDeGrupos = true)
        val semFechamento = acesso.montar(todos, campLib, campSul)
        // As duas vagas tem titular, entao o fechamento nao muda nada.
        assertEquals(semFechamento.size, comFechamento.size)
        assertEquals(acesso.TOTAL_DE_CLUBES, comFechamento.size)
    }

    // ── Ida e volta, pênaltis e a prorrogação da final ───────────────────
    //
    // O que estes testes travam: toda fase eliminatória é de ida e volta, com o
    // empate no agregado indo aos pênaltis; e só a final é de jogo único, com 30
    // minutos de prorrogação em dois tempos de 15 antes dos pênaltis.

    @Test
    fun `as tres fases da pre-libertadores sao de ida e volta`() {
        // A Fase 1 tem 6 clubes (3 confrontos), a Fase 2 tem 16 (8) e a Fase 3
        // tem 8 (4). Cada confronto, em qualquer delas, é de duas partidas.
        val tamanhos = listOf(
            acesso.PRE_FASE1_CLUBES,
            acesso.PRE_FASE2_CLUBES,
            acesso.PRE_FASE3_CLUBES
        )
        formato.preFases.forEachIndexed { indice, fase ->
            val clubes = (1..tamanhos[indice]).toList()
            val pares = (0 until clubes.size / 2).map { i -> clubes[clubes.size - 1 - i] to clubes[i] }
            val partidas = MotorCampeonato.gerarFaseIdaVolta(
                campeonatoId      = 1,
                pares             = pares,
                fase              = fase,
                rodadaIda         = formato.rodadaIdaDaPreFase(indice),
                confrontoIdInicio = 1,
                ordemGlobalIda    = formato.ordemGlobalPre[indice * 2],
                ordemGlobalVolta  = formato.ordemGlobalPre[indice * 2 + 1]
            )
            // Duas partidas por confronto, em rodadas consecutivas.
            val porConfronto = partidas.groupBy { it.confrontoId }
            assertEquals("$fase: confrontos", clubes.size / 2, porConfronto.size)
            assertTrue("$fase: devia ser ida e volta", porConfronto.values.all { it.size == 2 })
            for (jogos in porConfronto.values) {
                val ida = jogos.minByOrNull { it.rodada }!!
                val volta = jogos.maxByOrNull { it.rodada }!!
                assertEquals("$fase: volta na rodada seguinte", ida.rodada + 1, volta.rodada)
                // O mando se inverte: quem recebeu a ida visita na volta.
                assertEquals(ida.timeCasaId, volta.timeForaId)
                assertEquals(ida.timeForaId, volta.timeCasaId)
            }
            assertTrue("$fase: nenhuma partida devia ser de jogo unico",
                porConfronto.values.none { it.size == 1 })
        }
    }

    @Test
    fun `das oitavas as semis e ida e volta, e so a final e jogo unico`() {
        val idaVolta = formato.fasesMataMata.dropLast(1)   // Oitavas, Quartas, Semi
        assertEquals(listOf("Oitavas", "Quartas", "Semi"), idaVolta)

        idaVolta.forEachIndexed { indice, fase ->
            val partidas = MotorCampeonato.gerarFaseIdaVolta(
                campeonatoId      = 1,
                pares             = listOf(1 to 2, 3 to 4),
                fase              = fase,
                rodadaIda         = formato.rodadaIdaDeFaseMataMata(indice),
                confrontoIdInicio = 1,
                ordemGlobalIda    = formato.ordemGlobalMataMata[indice * 2],
                ordemGlobalVolta  = formato.ordemGlobalMataMata[indice * 2 + 1]
            )
            assertTrue("$fase: devia ser ida e volta",
                partidas.groupBy { it.confrontoId }.values.all { it.size == 2 })
            assertTrue("$fase nao e a final", !formato.ehFinal(fase))
        }

        // A final: um confronto, uma partida.
        val finalUnica = MotorCampeonato.gerarFaseJogoUnico(
            campeonatoId      = 1,
            pares             = listOf(1 to 2),
            fase              = formato.fasesMataMata.last(),
            rodada            = formato.rodadaIdaDeFaseMataMata(formato.fasesMataMata.lastIndex),
            confrontoIdInicio = 1,
            ordemGlobal       = formato.ordemGlobalMataMata.last()
        )
        assertEquals(1, finalUnica.groupBy { it.confrontoId }.size)
        assertTrue(finalUnica.groupBy { it.confrontoId }.values.all { it.size == 1 })
        assertTrue(formato.ehFinal(finalUnica[0].fase!!))
    }

    @Test
    fun `o empate no agregado vai aos penaltis em toda fase de ida e volta`() {
        // determinarVencedorTie devolve null exatamente quando o agregado empata,
        // e null e o sinal de "decide nos penaltis". Nao ha gol fora de casa.
        //
        // Lembrando o mando: na volta o time A (casa na ida) e o VISITANTE, entao
        // o agregado de A e golsCasaIda + golsForaVolta.
        val empates = listOf(
            // ida 0x0, volta 0x0 -> 0x0
            intArrayOf(0, 0, 0, 0),
            // ida 1x2, volta 1x2 -> A: 1+2=3, B: 2+1=3
            intArrayOf(1, 2, 1, 2),
            // ida 3x1, volta 2x0 -> A: 3+0=3, B: 1+2=3
            intArrayOf(3, 1, 2, 0)
        )
        for (e in empates) {
            assertNull(
                "agregado empatado (${e[0]}x${e[1]} / ${e[2]}x${e[3]}) devia ir aos penaltis",
                MotorCampeonato.determinarVencedorTie(1, 2, e[0], e[1], e[2], e[3])
            )
        }
        // E quando nao empata, ha vencedor direto: ida 2x0, volta 1x0
        // -> A: 2+0 = 2, B: 0+1 = 1.
        assertEquals(
            1, MotorCampeonato.determinarVencedorTie(1, 2, 2, 0, 1, 0)
        )
        // ida 0x2, volta 0x1 -> A: 0+1 = 1, B: 2+0 = 2.
        assertEquals(
            2, MotorCampeonato.determinarVencedorTie(1, 2, 0, 2, 0, 1)
        )
    }

    @Test
    fun `a prorrogacao e de 30 minutos em dois tempos de 15`() {
        assertEquals(15, SimuladorPartida.MINUTOS_POR_TEMPO_EXTRA)
        assertEquals(30, SimuladorPartida.MINUTOS_DE_PRORROGACAO)
        // 1º tempo: 91 a 105. 2º tempo: 106 a 120.
        assertEquals(91, SimuladorPartida.PRORROGACAO_1T_INICIO)
        assertEquals(105, SimuladorPartida.PRORROGACAO_1T_FIM)
        assertEquals(106, SimuladorPartida.PRORROGACAO_2T_INICIO)
        assertEquals(120, SimuladorPartida.PRORROGACAO_2T_FIM)

        // Os dois tempos têm 15 minutos cada e são contíguos, sem sobreposição.
        val primeiro = SimuladorPartida.PRORROGACAO_1T_INICIO..SimuladorPartida.PRORROGACAO_1T_FIM
        val segundo = SimuladorPartida.PRORROGACAO_2T_INICIO..SimuladorPartida.PRORROGACAO_2T_FIM
        assertEquals(SimuladorPartida.MINUTOS_POR_TEMPO_EXTRA, primeiro.count())
        assertEquals(SimuladorPartida.MINUTOS_POR_TEMPO_EXTRA, segundo.count())
        assertEquals(SimuladorPartida.MINUTOS_DE_PRORROGACAO, primeiro.count() + segundo.count())
        assertTrue("os tempos nao podem se sobrepor", primeiro.last < segundo.first)
        assertEquals("o 2o tempo comeca logo depois do 1o", primeiro.last + 1, segundo.first)
        // E começam depois dos 90 minutos do tempo normal.
        assertTrue(primeiro.first > 90)
    }

    @Test
    fun `so a final tem prorrogacao, e as outras fases vao direto aos penaltis`() {
        // Das oitavas às semis, e em toda a Pré, o empate vai DIRETO aos pênaltis.
        for (fase in formato.preFases + formato.fasesMataMata.dropLast(1)) {
            assertTrue(
                "$fase nao devia ter prorrogacao",
                !formato.temProrrogacao(fase)
            )
        }
        // Só a final.
        assertTrue(formato.temProrrogacao("Final"))
        assertEquals("Final", formato.fasesMataMata.last())
        // E a fase de grupos nem passa por aqui: ela não tem nome de fase.
        assertTrue(formato.fasesMataMata.none { it == "" })
    }

    // ── As vagas de título dentro da cota de cada país ───────────────────
    //
    // Os campeonatos nacionais concedem as vagas, mas duas associações reservam
    // uma das suas a um título em vez de à colocação na liga: o Brasil ao campeão
    // da Libertadores e a Argentina ao campeão da Copa Argentina. A vaga sai da
    // cota do país — não é uma vaga extra — e desce para o colocado seguinte da
    // liga quando o detentor não existe ou já entrou por outra porta.

    /** Quantos clubes de [pais] entraram, e por qual porta. */
    private fun porPortaDoPais(vagas: List<VagaLibertadores>, pais: String) =
        vagas.filter { it.candidato.pais == pais }.groupBy { it.porta }

    @Test
    fun `a vaga de titulo do brasil vai ao campeao da libertadores`() {
        val todos = candidatos()
        // Um brasileiro que NÃO se classificaria pela liga: 30º do Brasileirão.
        val campeao = todos.first { it.pais == "Brasil" && it.posicaoLiga == 30 }

        val vagas = acesso.montar(
            todos,
            vagasDeTitulo      = mapOf("Brasil" to campeao.timeId),
            fecharFaseDeGrupos = true
        )
        val doBrasil = porPortaDoPais(vagas, "Brasil")

        // Ele entrou, e entrou pela porta do título.
        assertEquals(
            listOf(campeao.timeId),
            doBrasil[PortaDaLibertadores.VAGA_DE_TITULO]?.map { it.candidato.timeId }
        )
        // E direto na fase de grupos, não na Pré.
        assertTrue(acesso.diretosNosGrupos(vagas).any { it.timeId == campeao.timeId })
        assertTrue(acesso.naPreLibertadores(vagas).none { it.timeId == campeao.timeId })
    }

    @Test
    fun `a vaga de titulo sai da cota do pais, nao e uma vaga extra`() {
        val todos = candidatos()
        val campeao = todos.first { it.pais == "Brasil" && it.posicaoLiga == 30 }

        val semTitulo = acesso.montar(todos, fecharFaseDeGrupos = true)
        val comTitulo = acesso.montar(
            todos,
            vagasDeTitulo      = mapOf("Brasil" to campeao.timeId),
            fecharFaseDeGrupos = true
        )

        // O Brasil entra com o MESMO número de clubes nos grupos nas duas
        // montagens: a vaga do título ocupou o lugar de um classificado da liga.
        fun brasileirosNosGrupos(vagas: List<VagaLibertadores>) =
            acesso.diretosNosGrupos(vagas).count { it.pais == "Brasil" }
        assertEquals(brasileirosNosGrupos(semTitulo), brasileirosNosGrupos(comTitulo))

        // E o total continua fechando os 32.
        assertEquals(
            acesso.CLUBES_NA_FASE_DE_GRUPOS - acesso.SOBREVIVENTES_DA_PRE,
            acesso.diretosNosGrupos(comTitulo).size
        )

        // Quem perdeu a vaga foi o ÚLTIMO classificado de grupo do Brasil, e não
        // o primeiro: a cota encurta por baixo.
        val gruposSemTitulo = semTitulo
            .filter { it.candidato.pais == "Brasil" && it.porta == PortaDaLibertadores.GRUPOS }
            .map { it.candidato.posicaoLiga }
        val gruposComTitulo = comTitulo
            .filter { it.candidato.pais == "Brasil" && it.porta == PortaDaLibertadores.GRUPOS }
            .map { it.candidato.posicaoLiga }
        assertEquals(gruposSemTitulo.size - 1, gruposComTitulo.size)
        assertEquals(gruposSemTitulo.dropLast(1), gruposComTitulo)
    }

    @Test
    fun `a argentina reserva uma vaga ao campeao da copa argentina`() {
        val todos = candidatos()
        val campeaoCopa = todos.first { it.pais == "Argentina" && it.posicaoLiga == 25 }

        val vagas = acesso.montar(
            todos,
            vagasDeTitulo      = mapOf("Argentina" to campeaoCopa.timeId),
            fecharFaseDeGrupos = true
        )
        assertEquals(
            listOf(campeaoCopa.timeId),
            porPortaDoPais(vagas, "Argentina")[PortaDaLibertadores.VAGA_DE_TITULO]
                ?.map { it.candidato.timeId }
        )
        // A vaga é da Argentina: nenhum outro país ganhou vaga de título.
        assertEquals(1, vagas.count { it.porta == PortaDaLibertadores.VAGA_DE_TITULO })
    }

    @Test
    fun `o uruguai nao reserva vaga de titulo, tudo sai da liga`() {
        val vagas = acesso.montar(
            candidatos(),
            vagasDeTitulo      = mapOf("Brasil" to 1, "Argentina" to 41),
            fecharFaseDeGrupos = true
        )
        assertTrue(
            "o Uruguai nao devia ter vaga de titulo",
            porPortaDoPais(vagas, "Uruguai")[PortaDaLibertadores.VAGA_DE_TITULO] == null
        )
    }

    @Test
    fun `as duas vagas de titulo convivem, uma por pais`() {
        val todos = candidatos()
        val campeaoLib = todos.first { it.pais == "Brasil" && it.posicaoLiga == 30 }
        val campeaoCopa = todos.first { it.pais == "Argentina" && it.posicaoLiga == 25 }

        val vagas = acesso.montar(
            todos,
            vagasDeTitulo = mapOf(
                "Brasil" to campeaoLib.timeId,
                "Argentina" to campeaoCopa.timeId
            ),
            fecharFaseDeGrupos = true
        )
        val titulos = vagas.filter { it.porta == PortaDaLibertadores.VAGA_DE_TITULO }
        assertEquals(2, titulos.size)
        assertEquals(
            setOf(campeaoLib.timeId, campeaoCopa.timeId),
            titulos.map { it.candidato.timeId }.toSet()
        )
        // Um por país.
        assertEquals(2, titulos.map { it.candidato.pais }.distinct().size)
        // E os grupos continuam fechando.
        assertEquals(
            acesso.CLUBES_NA_FASE_DE_GRUPOS - acesso.SOBREVIVENTES_DA_PRE,
            acesso.diretosNosGrupos(vagas).size
        )
    }

    @Test
    fun `detentor que ja se classificou pela liga nao ocupa duas vagas`() {
        val todos = candidatos()
        // O campeão é o 1º do Brasileirão: entraria pela liga de qualquer jeito.
        val campeao = todos.first { it.pais == "Brasil" && it.posicaoLiga == 1 }

        val vagas = acesso.montar(
            todos,
            vagasDeTitulo      = mapOf("Brasil" to campeao.timeId),
            fecharFaseDeGrupos = true
        )
        // Aparece uma vez só.
        assertEquals(1, vagas.count { it.candidato.timeId == campeao.timeId })
        // Pela porta do título, que é avaliada antes da liga.
        assertEquals(
            PortaDaLibertadores.VAGA_DE_TITULO,
            vagas.first { it.candidato.timeId == campeao.timeId }.porta
        )
        // E a vaga de liga que ele deixou de ocupar desceu: o Brasil entra com o
        // mesmo número de clubes de uma montagem sem vaga de título.
        val semTitulo = acesso.montar(todos, fecharFaseDeGrupos = true)
        assertEquals(
            acesso.diretosNosGrupos(semTitulo).count { it.pais == "Brasil" },
            acesso.diretosNosGrupos(vagas).count { it.pais == "Brasil" }
        )
    }

    @Test
    fun `sem detentor a vaga de titulo desce para a liga`() {
        val todos = candidatos()
        // Primeira temporada: nenhum título decidido ainda.
        val semDetentor = acesso.montar(todos, fecharFaseDeGrupos = true)
        // Um detentor que não existe entre os candidatos não muda nada.
        val comDetentorInexistente = acesso.montar(
            todos,
            vagasDeTitulo      = mapOf("Brasil" to 999_999),
            fecharFaseDeGrupos = true
        )
        assertTrue(comDetentorInexistente.none { it.porta == PortaDaLibertadores.VAGA_DE_TITULO })
        assertEquals(
            semDetentor.map { it.candidato.timeId to it.porta },
            comDetentorInexistente.map { it.candidato.timeId to it.porta }
        )
    }

    @Test
    fun `a vaga de titulo do brasil nao vai para um clube de outro pais`() {
        val todos = candidatos()
        val argentino = todos.first { it.pais == "Argentina" && it.posicaoLiga == 25 }
        // montar recebe o detentor sob a chave "Brasil", mas o clube e argentino:
        // a vaga so vale para clube do proprio pais, entao ela nao e concedida.
        val vagas = acesso.montar(
            todos,
            vagasDeTitulo      = mapOf("Brasil" to argentino.timeId),
            fecharFaseDeGrupos = true
        )
        assertTrue(
            "a vaga do Brasil nao podia ir a um argentino",
            porPortaDoPais(vagas, "Brasil")[PortaDaLibertadores.VAGA_DE_TITULO] == null
        )
    }
}
