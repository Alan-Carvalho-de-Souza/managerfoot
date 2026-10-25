package br.com.managerfoot.domain.engine

import br.com.managerfoot.domain.model.Continentes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Prova que o SEED sustenta a Libertadores e a Copa Sul-Americana AO MESMO
// TEMPO, que é a condição que regras_sulamericana.md apontou como o único
// pré-requisito duro.
//
// A conta é por país, não por continente: cada associação pequena precisa de 8
// clubes — 4 que a Libertadores leva (2 nos grupos + 2 na Pré) e outros 4 para a
// Primeira Fase daqui. O pool sul-americano tinha 6 por país, e por isso faltavam
// 2 em cada um dos 7, 14 no total.
//
// Este teste roda as DUAS listas de acesso de produção na ordem real — a
// Libertadores primeiro, a Sul-Americana depois, excluindo quem já está lá — e
// sorteia os grupos de verdade. Quebra se um clube sair do seed.
// ─────────────────────────────────────────────────────────────────────────────
class SeedSulAmericanaTest {

    private data class ClubeSeed(
        val id: Int,
        val nome: String,
        val pais: String,
        val divisao: Int,
        val reputacao: Int
    )

    /** As mesmas de `GameRepository.DIVISOES_ELEGIVEIS_LIBERTADORES`. */
    private val DIVISOES_ELEGIVEIS = setOf(1, 5, 9, 90)

    private val libertadores = ListaDeAcessoLibertadores
    private val acesso = ListaDeAcessoSulAmericana

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

    /** A mesma tradução de `SeedDataSource.parseTimes`: no Brasil e na Argentina
     *  a divisão do seed é uma LETRA. */
    private fun divisaoDoSeed(letra: String, pais: String, numerica: Int): Int = when {
        letra.uppercase() == "A" && pais == "Argentina" -> 5
        letra.uppercase() == "B" && pais == "Argentina" -> 6
        letra.uppercase() == "A" -> 1
        letra.uppercase() == "B" -> 2
        letra.uppercase() == "C" -> 3
        letra.uppercase() == "D" -> 4
        else -> numerica
    }

    /** Os candidatos da 1ª temporada: nenhuma liga encerrou, então só a
     *  reputação do seed ordena — o cenário mais hostil. */
    private fun candidatos(): List<CandidatoSulAmericana> =
        lerClubes()
            .filter { it.divisao in DIVISOES_ELEGIVEIS }
            .filter { Continentes.ehAmericaDoSul(it.pais) }
            .mapNotNull { clube ->
                val pais = acesso.paisCanonico(clube.pais) ?: return@mapNotNull null
                CandidatoSulAmericana(
                    timeId = clube.id,
                    nome = clube.nome,
                    pais = pais,
                    coeficiente = 0.0,
                    reputacao = clube.reputacao.toFloat(),
                    posicaoLiga = null
                )
            }

    /** Os clubes que a Libertadores leva na mesma temporada. */
    private fun naLibertadores(): Set<Int> =
        libertadores.montar(candidatos(), fecharFaseDeGrupos = true)
            .map { it.candidato.timeId }
            .toSet()

    // ── O pré-requisito: 8 clubes por associação ─────────────────────────

    @Test
    fun `cada associacao tem clubes para as duas competicoes`() {
        val porPais = candidatos().groupBy { it.pais }
        assertEquals(
            "faltam associações: ${acesso.COTAS_OFICIAIS.keys - porPais.keys}",
            acesso.COTAS_OFICIAIS.keys,
            porPais.keys
        )
        for (pais in acesso.COTAS_OFICIAIS.keys) {
            val precisa = libertadores.COTAS_OFICIAIS.getValue(pais).total +
                acesso.COTAS_OFICIAIS.getValue(pais).total
            val tem = porPais.getValue(pais).size
            assertTrue(
                "$pais tem $tem clubes e as duas competições pedem $precisa",
                tem >= precisa
            )
        }
    }

    @Test
    fun `a Libertadores leva 47 e sobra campo para a Sul-Americana`() {
        val daLibertadores = naLibertadores()
        assertEquals(libertadores.TOTAL_DE_CLUBES, daLibertadores.size)
        val sobram = candidatos().count { it.timeId !in daLibertadores }
        assertTrue(
            "sobraram $sobram clubes para as ${acesso.VAGAS_NACIONAIS} vagas nacionais",
            sobram >= acesso.VAGAS_NACIONAIS
        )
    }

    // ── A distribuição real sobre o seed ─────────────────────────────────

    @Test
    fun `cada pais entra com a quantidade de vagas da tabela oficial`() {
        val vagas = acesso.montar(candidatos(), jaNaLibertadores = naLibertadores())
        val porPais = vagas.groupBy { it.candidato.pais }

        assertEquals(acesso.VAGAS_NACIONAIS, vagas.size)
        for ((pais, cota) in acesso.COTAS_OFICIAIS) {
            val doPais = porPais.getValue(pais)
            assertEquals("$pais: total de vagas", cota.total, doPais.size)
            assertEquals(
                "$pais: vagas diretas nos grupos",
                cota.grupos,
                doPais.count { it.porta == PortaDaSulAmericana.GRUPOS }
            )
            assertEquals(
                "$pais: vagas na Primeira Fase",
                cota.primeiraFase,
                doPais.count { it.porta == PortaDaSulAmericana.PRIMEIRA_FASE }
            )
        }
    }

    @Test
    fun `nenhum clube disputa as duas competicoes`() {
        val daLibertadores = naLibertadores()
        val vagas = acesso.montar(candidatos(), jaNaLibertadores = daLibertadores, fecharCampo = true)
        val daSulAmericana = vagas.map { it.candidato.timeId }.toSet()
        assertEquals(emptySet<Int>(), daLibertadores intersect daSulAmericana)
        assertEquals(vagas.size, daSulAmericana.size)
    }

    @Test
    fun `o campo nacional fecha 44, com 12 nos grupos e 32 na Primeira Fase`() {
        val vagas = acesso.montar(candidatos(), jaNaLibertadores = naLibertadores(), fecharCampo = true)
        assertEquals(acesso.VAGAS_NACIONAIS, vagas.size)
        assertEquals(acesso.DIRETAS_NOS_GRUPOS, acesso.diretosNosGrupos(vagas).size)
        assertEquals(acesso.NA_PRIMEIRA_FASE, acesso.naPrimeiraFase(vagas).size)
    }

    // ── A Primeira Fase sobre o seed ─────────────────────────────────────

    @Test
    fun `a Primeira Fase real fecha 16 confrontos nacionais`() {
        val vagas = acesso.montar(candidatos(), jaNaLibertadores = naLibertadores(), fecharCampo = true)
        val campo = acesso.naPrimeiraFase(vagas)
            .map { ClubeChampions(it.timeId, it.nome, it.pais, it.reputacao.toDouble()) }
        val pares = SorteioSulAmericana.chavearPrimeiraFase(campo)

        assertEquals(acesso.VENCEDORES_DA_PRIMEIRA_FASE, pares.size)
        val paisDe = campo.associate { it.timeId to it.pais }
        for ((casa, fora) in pares) {
            assertEquals("$casa x $fora atravessa fronteira", paisDe[casa], paisDe[fora])
        }
        // Todo clube da Primeira Fase entra em campo, e uma vez só.
        val todos = pares.flatMap { listOf(it.first, it.second) }
        assertEquals(campo.size, todos.size)
        assertEquals(campo.size, todos.distinct().size)
    }

    // ── O sorteio real dos grupos ────────────────────────────────────────

    @Test
    fun `o sorteio real sobre o seed nao deixa compatriota nenhum`() {
        val todos = candidatos()
        val porId = todos.associateBy { it.timeId }
        val vagasLib = libertadores.montar(todos, fecharFaseDeGrupos = true)
        val naPreLib = libertadores.naPreLibertadores(vagasLib)
        val daLibertadores = vagasLib.map { it.candidato.timeId }.toSet()

        val vagas = acesso.montar(todos, jaNaLibertadores = daLibertadores, fecharCampo = true)
        val diretos = acesso.diretosNosGrupos(vagas)
        val naPrimeira = acesso.naPrimeiraFase(vagas)
            .map { ClubeChampions(it.timeId, it.nome, it.pais, it.reputacao.toDouble()) }

        // Os 4 que caem na Fase 3 da Pré-Libertadores, no PIOR caso para a trava:
        // os brasileiros primeiro, porque é o Brasil que já chega com 6 da cota.
        // Como a cota de Pré do Brasil é 2, o teto real é 8 clubes em 8 grupos.
        val daPreLibertadores = naPreLib
            .sortedBy { if (it.pais == "Brasil") 0 else 1 }
            .take(acesso.DA_PRE_LIBERTADORES)
        assertEquals(acesso.DA_PRE_LIBERTADORES, daPreLibertadores.size)

        for (semente in 1..10) {
            val rng = Random(semente)
            // Qualquer um dos dois de cada confronto pode passar, e a trava tem de
            // valer em todas as combinações — daí o sorteio do vencedor.
            val vencedores = SorteioSulAmericana.chavearPrimeiraFase(naPrimeira)
                .map { (casa, fora) -> if (rng.nextBoolean()) casa else fora }

            val nosGrupos = diretos.map { it.timeId } + vencedores + daPreLibertadores.map { it.timeId }
            assertEquals(
                "o campo dos grupos tem de fechar 32",
                SorteioSulAmericana.CLUBES, nosGrupos.distinct().size
            )
            val clubes = nosGrupos.mapNotNull { id ->
                porId[id]?.let { ClubeChampions(it.timeId, it.nome, it.pais, it.reputacao.toDouble()) }
            }

            val inviabilidade = SorteioSulAmericana.verificarViabilidade(clubes)
            assertEquals("semente $semente: $inviabilidade", null, inviabilidade)
            val sorteio = SorteioSulAmericana.sortearGrupos(clubes, rng)
            assertNotNull("semente $semente não fechou o sorteio", sorteio)
            assertEquals(emptyList<String>(), SorteioSulAmericana.validar(sorteio!!))
            assertEquals(0, SorteioSulAmericana.paresDeCompatriotas(sorteio))
        }
    }

    // ── A vaga que a Sul-Americana devolve à Libertadores ────────────────

    @Test
    fun `com os dois campeoes vigentes as cotas voltam a tabela oficial`() {
        val todos = candidatos()
        val naLib = naLibertadores()
        // Os dois campeões vigentes, de países diferentes e nenhum deles entrando
        // pela vaga de título do Brasil — o caso em que as duas vagas estruturais
        // da fase de grupos têm titular.
        val campeaoLib = todos.first { it.pais == "Chile" }.timeId
        val campeaoSul = todos.first { it.timeId !in naLib && it.pais == "Peru" }.timeId

        val vagas = libertadores.montar(
            todos,
            campeaoLibertadores = campeaoLib,
            campeaoSulAmericana = campeaoSul,
            fecharFaseDeGrupos = true
        )

        // Com as duas ocupadas, nenhuma vaga é remanejada: é esse remanejamento
        // que dava ao Brasil e à Argentina uma vaga além da tabela oficial.
        assertEquals(
            0,
            vagas.count { it.porta == PortaDaLibertadores.VAGA_DE_CAMPEAO_REMANEJADA }
        )
        // E cada associação entra com exatamente a sua cota.
        val porPais = vagas.groupBy { it.candidato.pais }
        for ((pais, cota) in libertadores.COTAS_OFICIAIS) {
            val esperado = cota.total +
                listOf(campeaoLib, campeaoSul).count { id ->
                    todos.first { it.timeId == id }.pais == pais
                }
            assertEquals("$pais", esperado, porPais.getValue(pais).size)
        }
        assertEquals(libertadores.TOTAL_DE_CLUBES, vagas.size)
    }

    @Test
    fun `a vaga do campeao da Sul-Americana tira uma vaga remanejada`() {
        val todos = candidatos()
        val naLib = naLibertadores()
        val campeaoSul = todos.first { it.timeId !in naLib && it.pais == "Peru" }.timeId

        val semVaga = libertadores.montar(todos, fecharFaseDeGrupos = true)
        val comVaga = libertadores.montar(
            todos, campeaoSulAmericana = campeaoSul, fecharFaseDeGrupos = true
        )

        val remanejadasSem = semVaga.count { it.porta == PortaDaLibertadores.VAGA_DE_CAMPEAO_REMANEJADA }
        val remanejadasCom = comVaga.count { it.porta == PortaDaLibertadores.VAGA_DE_CAMPEAO_REMANEJADA }
        assertEquals(remanejadasSem - 1, remanejadasCom)

        // O campeão entra pela porta de campeão vigente, por fora da cota do país.
        assertTrue(
            comVaga.any {
                it.candidato.timeId == campeaoSul &&
                    it.porta == PortaDaLibertadores.CAMPEAO_VIGENTE
            }
        )

        // A remanejada que sobra é oferecida na ordem da cota, então quem devolve a
        // vaga a mais é a ÚLTIMA da fila — a Argentina, e não o Brasil. O Brasil só
        // volta às suas 7 quando as duas vagas estruturais têm titular.
        assertEquals(
            libertadores.COTAS_OFICIAIS.getValue("Argentina").total,
            comVaga.count { it.candidato.pais == "Argentina" }
        )
        assertEquals(libertadores.TOTAL_DE_CLUBES, comVaga.size)
    }

    @Test
    fun `nenhum pais passa de 8 clubes na fase de grupos`() {
        // É o teto de viabilidade: 8 clubes de um país em 8 grupos só cabem com
        // exatamente um por grupo, e 9 não cabem de jeito nenhum.
        val vagas = acesso.montar(candidatos(), jaNaLibertadores = naLibertadores(), fecharCampo = true)
        val porPais = acesso.diretosNosGrupos(vagas).groupingBy { it.pais }.eachCount()
        for ((pais, quantos) in porPais) {
            // 6 da cota + no máximo 2 que podem cair na Pré-Libertadores.
            val tetoDaPre = libertadores.COTAS_OFICIAIS.getValue(pais).pre
            assertTrue(
                "$pais entraria com até ${quantos + tetoDaPre} clubes nos grupos",
                quantos + tetoDaPre <= SorteioSulAmericana.GRUPOS
            )
        }
    }
}
