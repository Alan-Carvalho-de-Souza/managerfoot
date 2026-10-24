package br.com.managerfoot.domain.engine

import br.com.managerfoot.domain.model.Continentes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Prova que o SEED do jogo sustenta a Fase de Liga da Champions.
//
// SorteioChampionsTest exercita o algoritmo com clubes sintéticos. Este teste
// faz o contrário: parte do seed_data.json real, monta os 36 participantes pela
// lista de acesso de regras_championsleague.md e manda o sorteio rodar. É o que
// faltava antes do pool europeu existir — com os 3 países que o seed tinha
// (Inglaterra, Espanha, Itália) nenhuma combinação existe, porque cada clube
// precisa de 8 adversários e 3 países dão no máximo 4.
//
// Ele guarda, em particular, o limite menos óbvio das travas: no máximo 4 clubes
// de um mesmo país dentro de um pote. Os potes saem do coeficiente (aqui, da
// reputação do seed), então mexer nas reputações pode quebrá-lo sem que a
// contagem global de clubes por país mude.
// ─────────────────────────────────────────────────────────────────────────────
class SeedChampionsTest {

    private data class ClubeSeed(
        val id: Int,
        val nome: String,
        val pais: String,
        val divisao: Int,
        val reputacao: Int
    )

    /** Divisão do pool europeu sem liga nacional (ver gen_outros.py). */
    private val DIVISAO_POOL_EUROPA = 91

    /** Vagas das ligas jogáveis, por divisão: Premier, La Liga, Serie A. */
    private val VAGAS_POR_LIGA = listOf(11 to 5, 13 to 5, 14 to 4)

    /** Vagas diretas das associações que só existem no pool. */
    private val VAGAS_DIRETAS_DO_POOL = listOf(
        "Alemanha" to 4,
        "Franca" to 3,
        "Holanda" to 2,
        "Belgica" to 1,
        "Tchequia" to 1,
        "Turquia" to 1,
        "Portugal" to 1,
        "Ucrania" to 1
    )

    /** O resto da Fase de Liga sai da qualificação entre os clubes do pool. */
    private val VAGAS_DA_QUALIFICACAO = 8

    /** Piso de jogadores por clube, o mesmo que o rollover garante à IA. */
    private val MINIMO_DE_JOGADORES = 18

    // ── Leitura do seed ──────────────────────────────────────────────────
    private class Seed(
        val clubes: List<ClubeSeed>,
        val jogadoresPorTime: Map<Int, Int>,
        val timesComTecnico: Set<Int>
    )

    private fun lerSeed(): Seed {
        // O diretório de trabalho do teste é o módulo (app/) no Gradle, mas pode
        // ser a raiz do projeto quando rodado de outro lugar.
        val arquivo = listOf(
            "src/main/assets/seed_data.json",
            "app/src/main/assets/seed_data.json"
        ).map(::File).firstOrNull { it.isFile }
            ?: throw AssertionError("seed_data.json não encontrado a partir de ${File("").absolutePath}")

        val raiz = MiniJson.objeto(arquivo.readText(Charsets.UTF_8))

        val clubes = raiz.lista("times").map { o ->
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

        val porTime = HashMap<Int, Int>()
        for (o in raiz.lista("jogadores")) {
            val id = (o["time_id"] as? Number)?.toInt() ?: continue
            porTime[id] = (porTime[id] ?: 0) + 1
        }

        val comTecnico = HashSet<Int>()
        for (o in raiz.listaOpcional("tecnicos")) {
            (o["time_id"] as? Number)?.let { comTecnico += it.toInt() }
        }

        return Seed(clubes, porTime, comTecnico)
    }

    /** Ordem do coeficiente: reputação decrescente, nome como desempate. */
    private fun porCoeficiente(clubes: List<ClubeSeed>): List<ClubeSeed> =
        clubes.sortedWith(compareByDescending<ClubeSeed> { it.reputacao }.thenBy { it.nome })

    /** Os 36 da Fase de Liga, segundo a lista de acesso. */
    private fun montarParticipantes(seed: Seed): List<ClubeSeed> {
        val pool = seed.clubes.filter { it.divisao == DIVISAO_POOL_EUROPA }
        val participantes = mutableListOf<ClubeSeed>()

        for ((divisao, vagas) in VAGAS_POR_LIGA) {
            val daLiga = porCoeficiente(seed.clubes.filter { it.divisao == divisao })
            assertTrue(
                "divisão $divisao tem ${daLiga.size} clubes, precisa de $vagas",
                daLiga.size >= vagas
            )
            participantes += daLiga.take(vagas)
        }

        for ((pais, vagas) in VAGAS_DIRETAS_DO_POOL) {
            val doPais = porCoeficiente(pool.filter { it.pais == pais })
            assertTrue(
                "o pool tem ${doPais.size} clubes de $pais, precisa de $vagas",
                doPais.size >= vagas
            )
            participantes += doPais.take(vagas)
        }

        val classificados = participantes.map { it.id }.toSet()
        val qualificacao = porCoeficiente(pool.filterNot { it.id in classificados })
        assertTrue(
            "a qualificação precisa de mais clubes que vagas para ter disputa: " +
                "${qualificacao.size} candidatos para $VAGAS_DA_QUALIFICACAO vagas",
            qualificacao.size > VAGAS_DA_QUALIFICACAO
        )
        participantes += qualificacao.take(VAGAS_DA_QUALIFICACAO)

        return participantes
    }

    private fun comoClubesChampions(clubes: List<ClubeSeed>): List<ClubeChampions> =
        clubes.map {
            ClubeChampions(
                timeId = it.id,
                nome = it.nome,
                pais = it.pais,
                coeficiente = it.reputacao.toDouble()
            )
        }

    // ── O pool em si ─────────────────────────────────────────────────────
    @Test
    fun `pool europeu existe com paises suficientes e elencos completos`() {
        val seed = lerSeed()
        val pool = seed.clubes.filter { it.divisao == DIVISAO_POOL_EUROPA }

        assertTrue("pool europeu vazio (divisão $DIVISAO_POOL_EUROPA)", pool.isNotEmpty())

        val paises = pool.map { it.pais }.toSet()
        assertTrue(
            "pool tem ${paises.size} países; o sorteio exige ao menos " +
                "${SorteioChampions.MINIMO_DE_PAISES} no torneio",
            paises.size >= SorteioChampions.MINIMO_DE_PAISES
        )

        val foraDaEuropa = paises.filterNot { Continentes.dePais(it) == Continentes.EUROPA }
        assertEquals(
            "países do pool europeu não reconhecidos como Europa: $foraDaEuropa",
            emptyList<String>(), foraDaEuropa
        )

        val elencoCurto = pool.filter { (seed.jogadoresPorTime[it.id] ?: 0) < MINIMO_DE_JOGADORES }
            .map { "${it.nome}=${seed.jogadoresPorTime[it.id] ?: 0}" }
        assertEquals(
            "clubes do pool com menos de $MINIMO_DE_JOGADORES jogadores: $elencoCurto",
            emptyList<String>(), elencoCurto
        )

        val semTecnico = pool.filterNot { it.id in seed.timesComTecnico }.map { it.nome }
        assertEquals(
            "clubes do pool sem técnico no seed: $semTecnico",
            emptyList<String>(), semTecnico
        )
    }

    // ── A lista de acesso ────────────────────────────────────────────────
    @Test
    fun `lista de acesso monta 36 clubes distintos`() {
        val participantes = montarParticipantes(lerSeed())

        assertEquals(SorteioChampions.CLUBES, participantes.size)
        assertEquals(
            "clube repetido na lista de acesso",
            participantes.size, participantes.map { it.id }.distinct().size
        )

        val porPais = participantes.groupingBy { it.pais }.eachCount()
        val excesso = porPais.filter { it.value > SorteioChampions.MAX_CLUBES_POR_PAIS }
        assertEquals(
            "país com mais de ${SorteioChampions.MAX_CLUBES_POR_PAIS} clubes: $excesso",
            emptyMap<String, Int>(), excesso
        )
    }

    // ── As travas, antes de sortear ──────────────────────────────────────
    @Test
    fun `seed real passa na verificacao de viabilidade`() {
        val clubes = comoClubesChampions(montarParticipantes(lerSeed()))
        val problemas = SorteioChampions.verificarViabilidade(clubes)
        assertEquals(
            "o seed não sustenta o sorteio: $problemas",
            emptyList<InviabilidadeSorteio>(), problemas
        )
    }

    @Test
    fun `nenhum pote concentra mais de quatro clubes de um pais`() {
        val clubes = comoClubesChampions(montarParticipantes(lerSeed()))
        val potes = SorteioChampions.montarPotes(clubes)

        assertEquals(SorteioChampions.POTES, potes.size)
        potes.forEachIndexed { indice, pote ->
            assertEquals(SorteioChampions.CLUBES_POR_POTE, pote.size)
            val porPais = pote.groupingBy { it.pais }.eachCount()
            val excesso = porPais.filter { it.value > SorteioChampions.MAX_CLUBES_POR_PAIS_NO_POTE }
            assertEquals(
                "pote ${indice + 1} concentrado: $excesso (as reputações do seed " +
                    "empilharam clubes de um país no topo)",
                emptyMap<String, Int>(), excesso
            )
        }
    }

    // ── O sorteio completo ───────────────────────────────────────────────
    @Test
    fun `sorteio com os clubes do seed produz 144 partidas validas`() {
        val clubes = comoClubesChampions(montarParticipantes(lerSeed()))

        // Várias sementes: o retrocesso é aleatório, e um seed só correto "de
        // sorte" não serviria.
        for (semente in listOf(1, 7, 42, 2026)) {
            val resultado = SorteioChampions.sortear(clubes, Random(semente))
                ?: throw AssertionError("sorteio falhou com a semente $semente")

            assertEquals(SorteioChampions.TOTAL_CONFRONTOS, resultado.confrontos.size)
            val erros = SorteioChampions.validar(resultado)
            assertEquals("semente $semente: $erros", emptyList<String>(), erros)
        }
    }
}
