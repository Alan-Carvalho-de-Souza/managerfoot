package br.com.managerfoot.domain.engine

import br.com.managerfoot.domain.model.Continentes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Prova que o SEED sustenta a CONMEBOL Libertadores com as DEZ associações e as
// COTAS OFICIAIS de regras_libertadores.md.
//
// Era o pré-requisito que faltava. O seed tinha clubes de 3 das 10 associações,
// e por isso ListaDeAcessoLibertadores.cotasEfetivas redistribuía as 28 vagas
// órfãs entre Brasil, Argentina e Uruguai. Com o pool sul-americano (divisão 90,
// 6 clubes para cada um dos 7 países que faltavam) a redistribuição deixa de
// acontecer: a função devolve as cotas oficiais sem alteração de código, que é
// exatamente o que ela foi escrita para fazer.
//
// Este teste usa o CÓDIGO DE PRODUÇÃO — `cotasEfetivas`, `montar` e
// `SorteioLibertadores.sortear` —, então quebra se a regra mudar ou se um clube
// sair do seed.
// ─────────────────────────────────────────────────────────────────────────────
class SeedLibertadoresTest {

    private data class ClubeSeed(
        val id: Int,
        val nome: String,
        val pais: String,
        val divisao: Int,
        val reputacao: Int
    )

    /**
     * Divisões sul-americanas elegíveis, as mesmas de
     * `GameRepository.DIVISOES_ELEGIVEIS_LIBERTADORES`: 1 = Brasileirão Série A,
     * 5 = Primera argentina, 9 = Primera uruguaia, 90 = pool sul-americano.
     */
    private val DIVISOES_ELEGIVEIS = setOf(1, 5, 9, 90)

    private val acesso = ListaDeAcessoLibertadores

    // ── Leitura do seed ──────────────────────────────────────────────────
    private fun lerClubes(): List<ClubeSeed> {
        val arquivo = listOf(
            "src/main/assets/seed_data.json",
            "app/src/main/assets/seed_data.json"
        ).map(::File).firstOrNull { it.isFile }
            ?: throw AssertionError("seed_data.json não encontrado a partir de ${File("").absolutePath}")

        return MiniJson.objeto(arquivo.readText(Charsets.UTF_8)).lista("times").map { o ->
            val pais = o.texto("pais", "Brasil")
            ClubeSeed(
                id = o.inteiro("id"),
                nome = o.texto("nome"),
                pais = pais,
                divisao = divisaoDoSeed(o.texto("divisao", ""), pais, o.inteiro("divisao", -1)),
                reputacao = o.inteiro("reputacao")
            )
        }
    }

    /**
     * A mesma tradução que `SeedDataSource.parseTimes` faz: no Brasil e na
     * Argentina a divisão do seed é uma LETRA, e sem traduzi-la o Brasileirão e a
     * Primera argentina não entrariam na conta.
     */
    private fun divisaoDoSeed(letra: String, pais: String, numerica: Int): Int = when {
        letra.uppercase() == "A" && pais == "Argentina" -> 5
        letra.uppercase() == "B" && pais == "Argentina" -> 6
        letra.uppercase() == "A" -> 1
        letra.uppercase() == "B" -> 2
        letra.uppercase() == "C" -> 3
        letra.uppercase() == "D" -> 4
        else -> numerica
    }

    /**
     * Os candidatos da 1ª temporada: nenhuma liga encerrou, então `posicaoLiga` é
     * null em todos e só a reputação ordena. É o cenário mais hostil, porque é
     * quando a reputação do seed decide os potes sozinha.
     */
    private fun candidatos(): List<CandidatoLibertadores> =
        lerClubes()
            .filter { it.divisao in DIVISOES_ELEGIVEIS }
            .filter { Continentes.ehAmericaDoSul(it.pais) }
            .mapNotNull { clube ->
                val pais = acesso.paisCanonico(clube.pais) ?: return@mapNotNull null
                CandidatoLibertadores(
                    timeId = clube.id,
                    nome = clube.nome,
                    pais = pais,
                    coeficiente = 0.0,
                    reputacao = clube.reputacao.toFloat(),
                    posicaoLiga = null
                )
            }

    // ── As dez associações ───────────────────────────────────────────────

    @Test
    fun `o seed tem clubes das dez associacoes da conmebol`() {
        val porPais = candidatos().groupBy { it.pais }
        assertEquals(
            "faltam associações: ${acesso.COTAS_OFICIAIS.keys - porPais.keys}",
            acesso.COTAS_OFICIAIS.keys,
            porPais.keys
        )
        // Cada associação precisa de pelo menos as suas 4 vagas (2 grupos + 2 Pré).
        for ((pais, cota) in acesso.COTAS_OFICIAIS) {
            val quantos = porPais.getValue(pais).size
            assertTrue(
                "$pais tem $quantos clubes para uma cota de ${cota.total}",
                quantos >= cota.total
            )
        }
    }

    @Test
    fun `com as dez associacoes as cotas sao as OFICIAIS, sem redistribuicao`() {
        val paises = candidatos().map { it.pais }.toSet()
        val cotas = acesso.cotasEfetivas(paises)

        // É a propriedade que a redistribuição foi escrita para ter: completadas
        // as associações, ela devolve a tabela oficial sem alteração de código.
        assertEquals(acesso.COTAS_OFICIAIS, cotas)

        // E a tabela oficial, item por item, como está em regras_libertadores.md.
        assertEquals(CotaPais(grupos = 5, pre = 2), cotas.getValue("Brasil"))
        assertEquals(CotaPais(grupos = 5, pre = 1), cotas.getValue("Argentina"))
        for (pais in listOf(
            "Bolívia", "Chile", "Colômbia", "Equador",
            "Paraguai", "Peru", "Uruguai", "Venezuela"
        )) {
            assertEquals("$pais devia ter 2 + 2", CotaPais(grupos = 2, pre = 2), cotas.getValue(pais))
        }
        assertEquals(26, cotas.values.sumOf { it.grupos })
        assertEquals(19, cotas.values.sumOf { it.pre })
        assertEquals(45, cotas.values.sumOf { it.total })
    }

    // ── A distribuição real sobre o seed ─────────────────────────────────

    @Test
    fun `cada pais entra com a quantidade de vagas da tabela oficial`() {
        // Sem o fechamento dos grupos: aqui o que se mede são as 45 vagas
        // NACIONAIS, país por país, e não os 32 da fase de grupos.
        val vagas = acesso.montar(candidatos())
        val porPais = vagas.groupBy { it.candidato.pais }

        assertEquals(acesso.VAGAS_NACIONAIS, vagas.size)
        for ((pais, cota) in acesso.COTAS_OFICIAIS) {
            val doPais = porPais.getValue(pais)
            assertEquals("$pais: total de vagas", cota.total, doPais.size)
            assertEquals(
                "$pais: vagas diretas nos grupos",
                cota.grupos,
                doPais.count { it.porta == PortaDaLibertadores.GRUPOS }
            )
            assertEquals(
                "$pais: vagas na Pré-Libertadores",
                cota.pre,
                doPais.count { it.porta == PortaDaLibertadores.PRE_LIBERTADORES }
            )
        }
    }

    @Test
    fun `o campo fecha 47 clubes, com 32 nos grupos e 19 na pre`() {
        val vagas = acesso.montar(candidatos(), fecharFaseDeGrupos = true)
        val diretos = acesso.diretosNosGrupos(vagas)
        val naPre = acesso.naPreLibertadores(vagas)

        assertEquals(acesso.NA_PRE_LIBERTADORES, naPre.size)
        assertEquals(
            acesso.CLUBES_NA_FASE_DE_GRUPOS - acesso.SOBREVIVENTES_DA_PRE,
            diretos.size
        )
        assertEquals(acesso.TOTAL_DE_CLUBES, vagas.size)
        // Ninguém ocupa duas vagas.
        val ids = vagas.map { it.candidato.timeId }
        assertEquals(ids.size, ids.distinct().size)
    }

    // ── O sorteio com as dez associações ─────────────────────────────────

    @Test
    fun `com as dez associacoes o sorteio quase zera os compatriotas`() {
        val vagas = acesso.montar(candidatos(), fecharFaseDeGrupos = true)
        val diretos = acesso.diretosNosGrupos(vagas)

        // Os 4 sobreviventes da Pré-Libertadores. O teste não simula as fases
        // prévias, então escolhe os das associações MENOS representadas nos
        // grupos, o que mantém a composição dentro do limite em que a separação
        // por país é possível — ver o teste do limite, logo abaixo.
        val porPaisNosGrupos = diretos.groupingBy { it.pais }.eachCount()
        val sobreviventes = acesso.naPreLibertadores(vagas)
            .sortedBy { porPaisNosGrupos[it.pais] ?: 0 }
            .distinctBy { it.pais }
            .take(acesso.SOBREVIVENTES_DA_PRE)

        val clubes = (diretos + sobreviventes).map { c ->
            ClubeChampions(c.timeId, c.nome, c.pais, c.reputacao.toDouble())
        }
        assertEquals(SorteioLibertadores.CLUBES, clubes.size)
        assertTrue(
            "composicao com pais acima de 8 clubes nao seria separavel",
            clubes.groupingBy { it.pais }.eachCount().values.all { it <= SorteioLibertadores.GRUPOS }
        )

        // Com 3 associações a separação era IMPOSSÍVEL: um grupo de 4 precisa de
        // 4 países, e sobravam 13 pares de compatriotas. Com as 10 ela passa a
        // ser quase completa — mas não exatamente zero, e isso é do ALGORITMO,
        // não da contagem: SorteioLibertadores escolhe, pote a pote, o grupo com
        // menos compatriotas e nunca volta atrás, então uma escolha cedo pode
        // encurralar uma depois. Um sorteio com retrocesso zeraria; o guloso
        // existe para nunca falhar, que é o que importa para criar o torneio.
        var pior = 0
        for (semente in 1..20) {
            val resultado = SorteioLibertadores.sortear(clubes, Random(semente))!!
            assertTrue(
                "sorteio inválido na semente $semente: ${SorteioLibertadores.validar(resultado)}",
                SorteioLibertadores.validar(resultado).isEmpty()
            )
            pior = maxOf(pior, SorteioLibertadores.paresDeCompatriotas(resultado))
        }
        assertTrue(
            "o sorteio deixou $pior pares de compatriotas; com as 10 associacoes " +
                "o esperado e no maximo 2",
            pior <= 2
        )
    }

    /**
     * O limite da separacao por pais, registrado como teste porque e uma
     * propriedade da COMPOSICAO, nao do sorteio.
     *
     * Sao 8 grupos, entao um pais com mais de 8 clubes tem de repetir em algum
     * grupo, por contagem. A cota oficial do Brasil e 5 nos grupos, mas o campo
     * real pode passar disso: os 2 clubes brasileiros da Pre podem sobreviver e
     * as vagas de campeao vigente sem titular descem para os melhores restantes,
     * que tendem a ser brasileiros. No pior caso o Brasil chega a 9 e a trava
     * geografica deixa de caber.
     *
     * E por isso que SorteioLibertadores MINIMIZA compatriotas em vez de proibir:
     * uma trava rigida poderia nao ter solucao e travaria a criacao do torneio.
     */
    @Test
    fun `a separacao por pais depende de ninguem passar de 8 clubes`() {
        assertEquals(8, SorteioLibertadores.GRUPOS)

        // 9 clubes de um pais em 8 grupos: ao menos um par de compatriotas e
        // inevitavel, e o sorteio entrega o minimo em vez de falhar.
        val concentrado = (1..9).map { ClubeChampions(it, "Brasil $it", "Brasil", 90.0 - it) } +
            (10..32).map { ClubeChampions(it, "Outro $it", "Pais ${it % 9}", 70.0 - it * 0.1) }
        val resultado = SorteioLibertadores.sortear(concentrado, Random(7))!!
        assertTrue(SorteioLibertadores.validar(resultado).isEmpty())
        assertTrue(
            "com 9 clubes de um pais tem de sobrar compatriota",
            SorteioLibertadores.paresDeCompatriotas(resultado) >= 1
        )
    }

    /**
     * Por que a Pre-Libertadores tem de ser resolvida na criacao da competicao.
     *
     * A competicao nasce so com a Fase 1, entre os 6 clubes mais FRACOS da Pre. Na
     * primeira temporada nenhuma liga encerrou, entao a fila e ordenada por
     * coeficiente e reputacao — e os 6 ultimos sao sempre clubes do pool, de
     * reputacao 61-62. Nenhum clube de liga jogavel cai ali.
     *
     * Consequencia: o clube do jogador NUNCA tem partida da Libertadores no
     * momento em que o jogo comeca. Era por isso que o Calendario e a aba de
     * Rodadas apareciam sem Libertadores numa temporada nova — ela de fato ainda
     * nao tinha jogo dele. Ver GameRepository.criarLibertadores, que resolve a Pre
     * na criacao quando o jogador nao esta nela.
     */
    @Test
    fun `a fase 1 da pre nunca tem clube de liga jogavel na primeira temporada`() {
        val porId = lerClubes().associateBy { it.id }
        val vagas = acesso.montar(candidatos(), fecharFaseDeGrupos = true)
        val filaPre = acesso.naPreLibertadores(vagas).sortedWith(ListaDeAcessoChampions.ORDEM)
        val fase1 = filaPre.takeLast(acesso.PRE_FASE1_CLUBES)

        assertEquals(6, fase1.size)
        // Divisoes jogaveis sul-americanas: 1 Brasileirao, 5 Primera ARG, 9 URU.
        val deLigaJogavel = fase1.filter { porId.getValue(it.timeId).divisao in setOf(1, 5, 9) }
        assertTrue(
            "a Fase 1 nao deveria ter clube de liga jogavel: ${deLigaJogavel.map { it.nome }}",
            deLigaJogavel.isEmpty()
        )
        // Sao todos do pool, e e por isso que o jogador nunca esta na Fase 1.
        assertTrue(fase1.all { porId.getValue(it.timeId).divisao == 90 })
    }

    // ── O pool em si ─────────────────────────────────────────────────────

    @Test
    fun `o pool sul-americano tem seis clubes para cada um dos sete paises`() {
        val pool = lerClubes().filter { it.divisao == 90 }
        val porPais = pool.groupBy { acesso.paisCanonico(it.pais) }

        assertEquals(42, pool.size)
        assertEquals(
            setOf("Bolívia", "Chile", "Colômbia", "Equador", "Paraguai", "Peru", "Venezuela"),
            porPais.keys.filterNotNull().toSet()
        )
        porPais.forEach { (pais, clubes) ->
            assertEquals("$pais no pool", 6, clubes.size)
        }
        // Brasil, Argentina e Uruguai NÃO entram pelo pool: eles têm liga própria.
        assertTrue(porPais.keys.none { it in listOf("Brasil", "Argentina", "Uruguai") })

        // As reputações ficam abaixo dos gigantes das ligas jogáveis, para que o
        // pote 1 do sorteio continue sendo deles.
        val maiorDoPool = pool.maxOf { it.reputacao }
        val maiorDasLigas = lerClubes()
            .filter { it.divisao in setOf(1, 5, 9) }
            .maxOf { it.reputacao }
        assertTrue(
            "o pool ($maiorDoPool) não devia superar as ligas jogáveis ($maiorDasLigas)",
            maiorDoPool < maiorDasLigas
        )
    }
}
