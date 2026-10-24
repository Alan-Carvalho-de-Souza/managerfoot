package br.com.managerfoot.domain.engine

import br.com.managerfoot.domain.model.Continentes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Prova que o SEED sustenta a Champions League e a Europa League na MESMA
// temporada — o pré-requisito de europa_league.md, que era o que faltava.
//
// SeedChampionsTest prova a Champions sozinha, com a lista de acesso reescrita
// dentro do próprio teste. Aqui é diferente em dois pontos que importam:
//
//  1. usa o código de produção — `ListaDeAcessoChampions.montar` e
//     `ListaDeAcessoEuropa.montar` —, então o teste quebra se a regra mudar;
//  2. monta as duas na ORDEM REAL: a Champions primeiro, e a Europa League
//     depois, recebendo os 36 clubes já ocupados. Um clube não disputa as duas,
//     e é essa exclusão mútua que torna a conta apertada.
//
// A conta que o documento fecha: a Champions consome 14 clubes de elite e 22 do
// pool; a Europa League precisa de outros 36 sob o teto de 7 clubes por país.
// Com 30 clubes de pool isso era impossível (o documento calcula 30 montáveis
// para 36 necessários). Com 52 cabe, e este teste é o que garante que continua
// cabendo — mexer numa reputação do seed pode derrubá-lo sem mudar contagem
// nenhuma, porque as reputações é que ordenam os potes.
// ─────────────────────────────────────────────────────────────────────────────
class SeedEuropaTest {

    private data class ClubeSeed(
        val id: Int,
        val nome: String,
        val pais: String,
        val divisao: Int,
        val reputacao: Int
    )

    /** Divisões das ligas europeias jogáveis: Premier, La Liga, Serie A. */
    private val DIVISOES_DE_ELITE = setOf(11, 13, 14)

    /** Divisão do pool europeu sem liga nacional (ver gen_outros.py). */
    private val DIVISAO_POOL_EUROPA = 91

    // ── Leitura do seed ──────────────────────────────────────────────────
    private fun lerClubes(): List<ClubeSeed> {
        // O diretório de trabalho do teste é o módulo (app/) no Gradle, mas pode
        // ser a raiz do projeto quando rodado de outro lugar.
        val arquivo = listOf(
            "src/main/assets/seed_data.json",
            "app/src/main/assets/seed_data.json"
        ).map(::File).firstOrNull { it.isFile }
            ?: throw AssertionError("seed_data.json não encontrado a partir de ${File("").absolutePath}")

        return MiniJson.objeto(arquivo.readText(Charsets.UTF_8)).lista("times").map { o ->
            ClubeSeed(
                id = o.inteiro("id"),
                nome = o.texto("nome"),
                pais = o.texto("pais", "Brasil"),
                // As divisões do Brasil/Argentina são letras; só as numéricas
                // interessam aqui, e as letras caem no -1.
                divisao = o.inteiro("divisao", -1),
                reputacao = o.inteiro("reputacao")
            )
        }
    }

    /**
     * Os clubes elegíveis às competições continentais: os das três ligas
     * europeias jogáveis e os do pool sem liga própria.
     *
     * `posicaoLiga` fica null em todos de propósito: este é o cenário da 1ª
     * temporada, quando nenhuma liga encerrou ainda e só a reputação ordena —
     * o caso mais hostil às travas, porque é quando a reputação do seed decide
     * os potes sozinha. O coeficiente da UEFA também é zero para todos.
     */
    private fun candidatos(): List<CandidatoChampions> =
        lerClubes()
            .filter { it.divisao in DIVISOES_DE_ELITE || it.divisao == DIVISAO_POOL_EUROPA }
            .map {
                CandidatoChampions(
                    timeId = it.id,
                    nome = it.nome,
                    pais = it.pais,
                    coeficiente = 0.0,
                    reputacao = it.reputacao.toFloat(),
                    posicaoLiga = null
                )
            }

    /** As duas Fases de Liga da temporada, na ordem em que o jogo as monta. */
    private class Temporada(
        val champions: List<ClubeChampions>,
        val europa: List<ClubeChampions>,
        val vagasEuropa: List<VagaEuropa>
    )

    private fun montarTemporada(
        campeoesDaCopa: Map<String, Int> = emptyMap()
    ): Temporada {
        val candidatos = candidatos()

        val vagasChampions = ListaDeAcessoChampions.montar(candidatos)
        assertEquals(
            "a Champions não fechou a Fase de Liga com os clubes do seed",
            SorteioChampions.CLUBES, vagasChampions.size
        )

        // A exclusão mútua: a Europa League só vê quem a Champions não levou.
        val ocupados = vagasChampions.map { it.candidato.timeId }.toSet()
        val vagasEuropa = ListaDeAcessoEuropa.montar(candidatos, campeoesDaCopa, ocupados)
        assertEquals(
            "a Europa League montou ${vagasEuropa.size} de ${SorteioChampions.CLUBES} vagas " +
                "com os clubes que a Champions deixou — faltam clubes no pool (ver a seção " +
                "\"Pré-requisito\" de europa_league.md)",
            SorteioChampions.CLUBES, vagasEuropa.size
        )

        return Temporada(
            champions = ListaDeAcessoChampions.paraSorteio(vagasChampions),
            europa = ListaDeAcessoEuropa.paraSorteio(vagasEuropa),
            vagasEuropa = vagasEuropa
        )
    }

    // ── O pool ampliado ──────────────────────────────────────────────────
    @Test
    fun `pool europeu tem clubes para as duas competicoes`() {
        val pool = lerClubes().filter { it.divisao == DIVISAO_POOL_EUROPA }

        // A Champions consome 22 clubes de pool (14 diretos + 8 da qualificação)
        // e a Europa League 15 diretos; o resto alimenta a sua qualificação.
        assertTrue(
            "o pool tem ${pool.size} clubes; a conta de europa_league.md pede ~50 " +
                "para as duas competições caberem",
            pool.size >= 45
        )

        val porPais = pool.groupingBy { it.pais }.eachCount()
        val magros = porPais.filter { it.value < 2 }
        assertEquals(
            "associações com menos de 2 clubes no pool — o gargalo que impedia a " +
                "Europa League de fechar: $magros",
            // Liechtenstein é a exceção legítima: o país só entra pela copa local
            // e tem um clube só (ver europa_league.md).
            mapOf("Liechtenstein" to 1), magros
        )

        val foraDaEuropa = pool.map { it.pais }.distinct()
            .filterNot { Continentes.dePais(it) == Continentes.EUROPA }
        assertEquals(
            "países do pool não reconhecidos como Europa: $foraDaEuropa",
            emptyList<String>(), foraDaEuropa
        )
    }

    @Test
    fun `cada pais com vaga direta na Europa League tem clube para ela`() {
        val candidatos = candidatos()
        val semClube = ListaDeAcessoEuropa.VAGAS_DIRETAS.keys.filter { pais ->
            candidatos.none { ListaDeAcessoEuropa.paisCanonico(it.pais) == pais }
        }
        assertEquals(
            "países da lista de acesso sem nenhum clube no seed: $semClube",
            emptyList<String>(), semClube
        )
    }

    // ── As duas listas de acesso, juntas ─────────────────────────────────
    @Test
    fun `as duas competicoes fecham 36 clubes cada, sem repetir ninguem`() {
        val temporada = montarTemporada()

        assertEquals(SorteioChampions.CLUBES, temporada.champions.size)
        assertEquals(SorteioChampions.CLUBES, temporada.europa.size)

        val naChampions = temporada.champions.map { it.timeId }.toSet()
        val naEuropa = temporada.europa.map { it.timeId }.toSet()
        assertEquals("clube repetido dentro da Champions", temporada.champions.size, naChampions.size)
        assertEquals("clube repetido dentro da Europa League", temporada.europa.size, naEuropa.size)

        val nasDuas = naChampions intersect naEuropa
        assertEquals(
            "clube escalado nas duas competições da mesma temporada: $nasDuas",
            emptySet<Int>(), nasDuas
        )
    }

    @Test
    fun `nenhuma das duas passa de sete clubes de um pais`() {
        val temporada = montarTemporada()

        for ((rotulo, clubes) in listOf("Champions" to temporada.champions, "Europa" to temporada.europa)) {
            val excesso = clubes.groupingBy { it.pais }.eachCount()
                .filter { it.value > SorteioChampions.MAX_CLUBES_POR_PAIS }
            assertEquals(
                "$rotulo: país com mais de ${SorteioChampions.MAX_CLUBES_POR_PAIS} clubes, " +
                    "o que torna o sorteio impossível sob as travas: $excesso",
                emptyMap<String, Int>(), excesso
            )
        }
    }

    @Test
    fun `a Europa League preenche as vagas diretas de quem tem liga propria`() {
        val vagas = montarTemporada().vagasEuropa
        val diretas = vagas.filterNot { it.porta == PortaDeEntradaEuropa.QUALIFICACAO }

        // Na 1ª temporada nenhuma copa encerrou, então a ÚNICA porta de
        // Liechtenstein está fechada e o país não ocupa vaga direta: são 20 das
        // 21. Não é defeito — é a exceção do documento vista de frente. O clube
        // ainda entra, pela fila da qualificação, e o torneio fecha os 36.
        val semCopa = ListaDeAcessoEuropa.TOTAL_DIRETAS - ListaDeAcessoEuropa.SOMENTE_COPA.size
        assertEquals(
            "vagas diretas preenchidas: ${diretas.size} de $semCopa",
            semCopa, diretas.size
        )

        val faltando = ListaDeAcessoEuropa.VAGAS_DIRETAS
            .filterKeys { it !in ListaDeAcessoEuropa.SOMENTE_COPA }
            .filter { (pais, cota) ->
                diretas.count { ListaDeAcessoEuropa.paisCanonico(it.candidato.pais) == pais } < cota
            }
        assertEquals(
            "países que não preencheram a sua cota direta: $faltando",
            emptyMap<String, Int>(), faltando
        )
    }

    @Test
    fun `a vaga de Liechtenstein abre quando a copa local tem campeao`() {
        // A exceção do documento: o país não tem liga própria (seus clubes jogam
        // o campeonato suíço), então a copa local é a única porta. Sem um clube
        // de Liechtenstein no seed a vaga ficaria permanentemente vazia — este
        // teste é o que impede que ela volte a ficar.
        val vaduz = candidatos().single {
            ListaDeAcessoEuropa.paisCanonico(it.pais) == "Liechtenstein"
        }

        val vagas = montarTemporada(mapOf("Liechtenstein" to vaduz.timeId)).vagasEuropa

        assertEquals(
            "com a copa decidida, as ${ListaDeAcessoEuropa.TOTAL_DIRETAS} vagas diretas fecham",
            ListaDeAcessoEuropa.TOTAL_DIRETAS,
            vagas.count { it.porta != PortaDeEntradaEuropa.QUALIFICACAO }
        )

        val vaga = vagas.single { it.candidato.timeId == vaduz.timeId }
        assertEquals(PortaDeEntradaEuropa.COPA_NACIONAL, vaga.porta)
    }

    // ── As travas, antes de sortear ──────────────────────────────────────
    @Test
    fun `as duas competicoes passam na verificacao de viabilidade`() {
        val temporada = montarTemporada()

        assertEquals(
            "a Champions não sustenta o sorteio",
            emptyList<InviabilidadeSorteio>(),
            SorteioChampions.verificarViabilidade(temporada.champions)
        )
        assertEquals(
            "a Europa League não sustenta o sorteio com os clubes que a Champions deixou",
            emptyList<InviabilidadeSorteio>(),
            SorteioChampions.verificarViabilidade(temporada.europa)
        )
    }

    @Test
    fun `nenhum pote das duas concentra mais de quatro clubes de um pais`() {
        val temporada = montarTemporada()

        for ((rotulo, clubes) in listOf("Champions" to temporada.champions, "Europa" to temporada.europa)) {
            val potes = SorteioChampions.montarPotes(clubes)
            assertEquals(SorteioChampions.POTES, potes.size)
            potes.forEachIndexed { indice, pote ->
                assertEquals(SorteioChampions.CLUBES_POR_POTE, pote.size)
                val excesso = pote.groupingBy { it.pais }.eachCount()
                    .filter { it.value > SorteioChampions.MAX_CLUBES_POR_PAIS_NO_POTE }
                assertEquals(
                    "$rotulo, pote ${indice + 1} concentrado: $excesso (as reputações do " +
                        "seed empilharam clubes de um país no topo)",
                    emptyMap<String, Int>(), excesso
                )
            }
        }
    }

    // ── Os dois sorteios completos ───────────────────────────────────────
    @Test
    fun `as duas competicoes produzem sorteios validos na mesma temporada`() {
        val temporada = montarTemporada()

        // Várias sementes: o retrocesso do sorteio é aleatório, e um seed só
        // correto "de sorte" não serviria.
        for (semente in listOf(1, 7, 42, 2026)) {
            for ((rotulo, clubes) in listOf("Champions" to temporada.champions, "Europa" to temporada.europa)) {
                val resultado = SorteioChampions.sortear(clubes, Random(semente))
                    ?: throw AssertionError("$rotulo: sorteio falhou com a semente $semente")

                assertEquals(
                    "$rotulo, semente $semente",
                    SorteioChampions.TOTAL_CONFRONTOS, resultado.confrontos.size
                )
                val erros = SorteioChampions.validar(resultado)
                assertEquals("$rotulo, semente $semente: $erros", emptyList<String>(), erros)
            }
        }
    }

    // ── A 2ª temporada: com campeões de copa, a vaga da copa muda o campo ──
    @Test
    fun `as duas competicoes continuam viaveis quando as copas ja tem campeao`() {
        // A partir da 2ª temporada cada país com liga completa entrega a vaga da
        // copa nacional, e o campeão pode ser qualquer clube — inclusive um que a
        // Champions já levou, caso em que a vaga desce para o colocado seguinte.
        // Escolho de propósito um campeão que ESTÁ na Champions (o melhor de cada
        // liga) e outro que não está, para exercitar os dois caminhos.
        val porDivisao = lerClubes()
            .filter { it.divisao in DIVISOES_DE_ELITE }
            .groupBy { it.divisao }
            .mapValues { (_, clubes) -> clubes.sortedByDescending { it.reputacao } }

        val campeoesDaCopa = mapOf(
            // já classificado pela liga: a vaga da copa tem de descer
            "Inglaterra" to porDivisao.getValue(11).first().id,
            // fora da zona da Champions: entra pela porta da copa
            "Espanha" to porDivisao.getValue(13)[9].id,
            "Italia" to porDivisao.getValue(14)[8].id
        )

        val temporada = montarTemporada(campeoesDaCopa)

        assertEquals(SorteioChampions.CLUBES, temporada.europa.size)
        assertEquals(
            "a Europa League deixou de ser viável quando as copas entraram",
            emptyList<InviabilidadeSorteio>(),
            SorteioChampions.verificarViabilidade(temporada.europa)
        )

        val campeaoEspanhol = campeoesDaCopa.getValue("Espanha")
        val vagaDoCampeao = temporada.vagasEuropa.single { it.candidato.timeId == campeaoEspanhol }
        assertEquals(
            "o campeão da Copa do Rei devia entrar pela porta da copa",
            PortaDeEntradaEuropa.COPA_NACIONAL, vagaDoCampeao.porta
        )

        val resultado = SorteioChampions.sortear(temporada.europa, Random(2027))
            ?: throw AssertionError("sorteio da Europa League falhou com as copas decididas")
        assertEquals(emptyList<String>(), SorteioChampions.validar(resultado))
    }
}
