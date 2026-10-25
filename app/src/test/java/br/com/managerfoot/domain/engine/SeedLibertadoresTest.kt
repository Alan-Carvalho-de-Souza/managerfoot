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
    fun `o sorteio real sobre o seed respeita o bloqueio de paises`() {
        val vagas = acesso.montar(candidatos(), fecharFaseDeGrupos = true)
        val diretos = acesso.diretosNosGrupos(vagas)
        // Os 4 sobreviventes da Pré. O teste não simula as fases prévias, então
        // toma os 4 melhores dela — e eles vão ao Pote 4 por esse caminho, não
        // pelo ranking, que é justamente a regra a exercitar.
        val sobreviventes = acesso.naPreLibertadores(vagas)
            .sortedWith(ListaDeAcessoChampions.ORDEM)
            .take(acesso.SOBREVIVENTES_DA_PRE)
        val idsDaPre = sobreviventes.map { it.timeId }.toSet()
        val clubes = (diretos + sobreviventes).map { c ->
            ClubeChampions(c.timeId, c.nome, c.pais, c.coeficiente)
        }
        assertEquals(SorteioLibertadores.CLUBES, clubes.size)
        // O campeão vigente: o melhor ranqueado entre os diretos.
        val campeao = diretos.maxByOrNull { it.coeficiente }!!.timeId

        var piorBloqueavel = 0
        for (semente in 1..30) {
            val r = SorteioLibertadores.sortearGrupos(clubes, idsDaPre, campeao, Random(semente))!!
            assertTrue(
                "sorteio invalido na semente $semente: ${SorteioLibertadores.validar(r)}",
                SorteioLibertadores.validar(r).isEmpty()
            )
            // A posicao A1 e do campeao vigente, em toda semente.
            assertTrue(
                "o campeao devia estar no Grupo A (semente $semente)",
                r.grupos[0].clubes.any { it.timeId == campeao }
            )
            piorBloqueavel = maxOf(
                piorBloqueavel,
                SorteioLibertadores.paresDeCompatriotasBloqueaveis(r, idsDaPre)
            )
        }

        // Com 3 associacoes sobravam 13 pares e a separacao era impossivel por
        // contagem. Com as 10 e o bloqueio de paises sobra no maximo 1 par — e o
        // que sobra e do sorteio ser SEQUENCIAL: a bolinha anda para o proximo
        // grupo disponivel e nunca volta atras, entao uma escolha cedo pode
        // encurralar a ultima. A troca de desfazerCompatriotas desfaz a maior
        // parte dos nos, mas so permuta clubes do MESMO pote, para cada grupo
        // continuar com um de cada — e esse limite e o que deixa o residuo.
        assertTrue(
            "o sorteio deixou $piorBloqueavel pares que a regra proibe; esperado no maximo 1",
            piorBloqueavel <= 1
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
     * O sorteio BLOQUEIA compatriotas — a bolinha anda para o proximo grupo
     * disponivel —, mas nunca falha: esgotados os grupos ele aceita o conflito em
     * vez de travar a criacao do torneio. Com um pais acima de 8 clubes esse e o
     * unico desfecho possivel.
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

    /**
     * Os clubes do pool nao sao DIRIGIVEIS, e isto trava a definicao disso.
     *
     * Eles existem so para as competicoes continentais e nao tem liga nacional.
     * Dirigir um deles poria o jogador num campeonato do qual o clube nem
     * participa — o roteamento de divisao de `iniciarNovoJogo` cairia no `else`.
     *
     * Duas portas levavam a esse estado e as duas foram fechadas pela mesma
     * constante: a tela de selecao (InicioViewModel.timesSelecionaveis) e as
     * propostas de emprego de clubes (GameRepository, candidatosOutrasLigas).
     */
    @Test
    fun `as divisoes de pool cobrem os clubes sem liga nacional`() {
        // A constante e a fonte unica das duas filtragens.
        assertEquals(setOf(90, 91, 92), RankingEuropa.DIVISOES_POOL)

        val porDivisao = lerClubes().groupBy { it.divisao }
        // Todo clube do pool esta numa divisao de pool, e nenhuma liga jogavel
        // aparece ali por acidente.
        val noPool = porDivisao.filterKeys { it in RankingEuropa.DIVISOES_POOL }
            .values.flatten()
        assertTrue("o pool nao devia estar vazio", noPool.isNotEmpty())

        val DIVISOES_JOGAVEIS = setOf(1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14)
        assertTrue(
            "divisao nao pode ser de pool e jogavel ao mesmo tempo",
            RankingEuropa.DIVISOES_POOL.none { it in DIVISOES_JOGAVEIS }
        )
        // Os 56 sul-americanos do pool estao todos na 90, logo todos filtrados.
        // Eram 42 (6 por pais) ate a Copa Sul-Americana existir: ela precisa de 4
        // clubes por associacao pequena, alem dos 4 que a Libertadores leva.
        assertEquals(56, porDivisao[90]?.size)
        assertTrue(porDivisao.getValue(90).all { it.divisao in RankingEuropa.DIVISOES_POOL })
    }

    // ── O pool em si ─────────────────────────────────────────────────────

    @Test
    fun `o pool sul-americano tem oito clubes para cada um dos sete paises`() {
        val pool = lerClubes().filter { it.divisao == 90 }
        val porPais = pool.groupBy { acesso.paisCanonico(it.pais) }

        // Oito, e nao seis: a Libertadores consome 4 de cada associacao pequena
        // (2 nos grupos + 2 na Pre) e a Copa Sul-Americana precisa de outros 4.
        // Ver regras_sulamericana.md.
        assertEquals(56, pool.size)
        assertEquals(
            setOf("Bolívia", "Chile", "Colômbia", "Equador", "Paraguai", "Peru", "Venezuela"),
            porPais.keys.filterNotNull().toSet()
        )
        porPais.forEach { (pais, clubes) ->
            assertEquals("$pais no pool", 8, clubes.size)
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
