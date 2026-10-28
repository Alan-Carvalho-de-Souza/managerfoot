package br.com.managerfoot.domain.engine

import br.com.managerfoot.domain.engine.MercadoDeTecnicos.ClubeNoMercado
import br.com.managerfoot.domain.engine.MercadoDeTecnicos.Contratacao
import br.com.managerfoot.domain.engine.MercadoDeTecnicos.TecnicoNoMercado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A janela de fim de temporada do mercado de técnicos.
 *
 * O bug de origem: o mundo tem 363 técnicos para 363 clubes e um único agente
 * livre, e toda contratação filtrava `timeId == null`. A janela existia, mas
 * não tinha de quem contratar. Por isso o teste que mais importa aqui é o da
 * CASCATA: um clube tira de outro, e a vaga desce.
 */
class MercadoDeTecnicosTest {

    private fun clube(id: Int, rep: Float, pais: String = "Brasil") =
        ClubeNoMercado(id, rep, pais)

    private fun tecnico(
        id: Int,
        rep: Float,
        timeId: Int?,
        pais: String = "Brasil",
        jogador: Boolean = false
    ) = TecnicoNoMercado(id, rep, pais, timeId, jogador)

    /** Sorteio que sempre aprova a troca. */
    private val sempre: () -> Float = { 0f }

    /** Sorteio que sempre recusa a troca por upgrade. */
    private val nunca: () -> Float = { 1f }

    // ── A cascata ────────────────────────────────────────────────────────

    @Test
    fun `clube grande tira tecnico de clube menor e a vaga desce`() {
        // Grande (90) está vago. Medio (70) tem um bom técnico. Pequeno (50)
        // tem um razoável. O grande leva o do médio; o médio, agora vago, leva
        // o do pequeno. Duas contratações a partir de UMA vaga.
        val clubes = listOf(clube(1, 90f), clube(2, 70f), clube(3, 50f))
        val tecnicos = listOf(
            tecnico(20, 68f, timeId = 2),
            tecnico(30, 48f, timeId = 3)
        )

        val movimentos = MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sempre)

        assertEquals(
            listOf(
                Contratacao(tecnicoId = 20, paraTimeId = 1, deTimeId = 2),
                Contratacao(tecnicoId = 30, paraTimeId = 2, deTimeId = 3)
            ),
            movimentos
        )
    }

    @Test
    fun `a cascata nunca sobe, entao nao ha ciclo`() {
        // Nenhum movimento pode tirar de um clube de reputação igual ou maior.
        val clubes = (1..12).map { clube(it, 90f - it * 4f) }
        val tecnicos = (1..12).map { tecnico(100 + it, 85f - it * 4f, timeId = it) }

        val movimentos = MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sempre)

        val repPorClube = clubes.associate { it.timeId to it.reputacao }
        for (m in movimentos) {
            val origem = m.deTimeId ?: continue
            assertTrue(
                "assédio de $origem para ${m.paraTimeId} não desceu",
                repPorClube.getValue(origem) <= repPorClube.getValue(m.paraTimeId) -
                    MercadoDeTecnicos.MARGEM_ASSEDIO
            )
        }
        // E cada clube é servido no máximo uma vez.
        assertEquals(movimentos.map { it.paraTimeId }.distinct().size, movimentos.size)
    }

    @Test
    fun `o numero de clubes sem tecnico nunca cresce`() {
        val clubes = (1..10).map { clube(it, 95f - it * 6f) }
        // Clube 1 vago; todos os demais empregados.
        val tecnicos = (2..10).map { tecnico(100 + it, 88f - it * 6f, timeId = it) }

        val movimentos = MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sempre)

        val ocupacao = clubes.associate { it.timeId to (it.timeId != 1) }.toMutableMap()
        for (m in movimentos) {
            m.deTimeId?.let { ocupacao[it] = false }
            ocupacao[m.paraTimeId] = true
        }
        assertEquals(1, ocupacao.count { !it.value })
    }

    // ── Quem pode ser assediado ──────────────────────────────────────────

    @Test
    fun `clube de reputacao parecida esta protegido do assedio`() {
        // Diferença de 5 é menor que MARGEM_ASSEDIO (8): ninguém se mexe.
        val clubes = listOf(clube(1, 80f), clube(2, 75f))
        val tecnicos = listOf(tecnico(20, 70f, timeId = 2))

        assertEquals(
            emptyList<Contratacao>(),
            MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sempre)
        )
    }

    @Test
    fun `tecnico livre pode ser levado por qualquer clube compativel`() {
        val clubes = listOf(clube(1, 60f))
        val tecnicos = listOf(tecnico(20, 55f, timeId = null))

        assertEquals(
            listOf(Contratacao(20, 1, null)),
            MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sempre)
        )
    }

    @Test
    fun `tecnico grande demais nao desce para clube pequeno`() {
        // Teto é reputação do clube + 10.
        val clubes = listOf(clube(1, 40f))
        val tecnicos = listOf(tecnico(20, 80f, timeId = null))

        assertEquals(
            emptyList<Contratacao>(),
            MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sempre)
        )
    }

    // ── O time do usuário ────────────────────────────────────────────────

    @Test
    fun `o clube do usuario nunca e mexido`() {
        val clubes = listOf(clube(1, 90f), clube(2, 60f))
        val tecnicos = listOf(
            tecnico(10, 20f, timeId = 1, jogador = true),   // péssimo, mas é o usuário
            tecnico(20, 55f, timeId = 2)
        )

        assertEquals(
            emptyList<Contratacao>(),
            MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sempre)
        )
    }

    @Test
    fun `o tecnico do usuario nunca e contratado por outro clube`() {
        val clubes = listOf(clube(1, 95f), clube(2, 50f))
        val tecnicos = listOf(tecnico(20, 55f, timeId = 2, jogador = true))

        assertEquals(
            emptyList<Contratacao>(),
            MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sempre)
        )
    }

    // ── Upgrade e sorteio ────────────────────────────────────────────────

    @Test
    fun `clube so troca o tecnico que ja tem por um upgrade claro`() {
        // Alvo é apenas 3 melhor que o atual; a margem exigida é 8.
        val clubes = listOf(clube(1, 80f), clube(2, 60f))
        val tecnicos = listOf(
            tecnico(10, 65f, timeId = 1),
            tecnico(20, 68f, timeId = 2)
        )

        assertEquals(
            emptyList<Contratacao>(),
            MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sempre)
        )
    }

    @Test
    fun `o sorteio segura a troca por upgrade, mas nunca a vaga aberta`() {
        val clubes = listOf(clube(1, 85f), clube(2, 60f))

        // Clube 1 com técnico fraco: é upgrade, e o sorteio recusa.
        val comTecnico = listOf(tecnico(10, 40f, timeId = 1), tecnico(20, 70f, timeId = 2))
        assertEquals(
            emptyList<Contratacao>(),
            MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, comTecnico, nunca)
        )

        // Mesmo cenário, clube 1 vago: preenche mesmo com o sorteio recusando.
        val vago = listOf(tecnico(20, 70f, timeId = 2))
        assertEquals(
            listOf(Contratacao(20, 1, 2)),
            MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, vago, nunca)
        )
    }

    // ── Preferência por nacionalidade ────────────────────────────────────

    @Test
    fun `entre candidatos o clube prefere o compatriota`() {
        val clubes = listOf(clube(1, 80f, pais = "Italia"))
        val tecnicos = listOf(
            tecnico(20, 72f, timeId = null, pais = "Brasil"),
            tecnico(21, 70f, timeId = null, pais = "Italia")
        )

        assertEquals(
            listOf(Contratacao(21, 1, null)),
            MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sempre)
        )
    }

    // ── Determinismo ─────────────────────────────────────────────────────

    @Test
    fun `a mesma entrada produz sempre a mesma janela`() {
        val clubes = (1..8).map { clube(it, 92f - it * 7f) }
        val tecnicos = (1..8).map { tecnico(100 + it, 85f - it * 7f, timeId = it) }

        val primeira = MercadoDeTecnicos.janelaDeFimDeTemporada(clubes, tecnicos, sempre)
        val segunda = MercadoDeTecnicos.janelaDeFimDeTemporada(clubes.reversed(), tecnicos, sempre)
        assertEquals(primeira, segunda)
    }

    @Test
    fun `mundo sem clubes ou sem tecnicos nao quebra`() {
        assertEquals(
            emptyList<Contratacao>(),
            MercadoDeTecnicos.janelaDeFimDeTemporada(emptyList(), emptyList(), sempre)
        )
        assertEquals(
            emptyList<Contratacao>(),
            MercadoDeTecnicos.janelaDeFimDeTemporada(listOf(clube(1, 70f)), emptyList(), sempre)
        )
    }

    // ── Reputação de fim de temporada ────────────────────────────────────

    @Test
    fun `temporada boa sobe a reputacao e temporada ruim derruba`() {
        assertEquals(3.0f, MercadoDeTecnicos.deltaDeReputacao(0.75f, false, false), 0.001f)
        assertEquals(1.5f, MercadoDeTecnicos.deltaDeReputacao(0.60f, false, false), 0.001f)
        assertEquals(0.0f, MercadoDeTecnicos.deltaDeReputacao(0.45f, false, false), 0.001f)
        assertEquals(-1.5f, MercadoDeTecnicos.deltaDeReputacao(0.33f, false, false), 0.001f)
        assertEquals(-3.0f, MercadoDeTecnicos.deltaDeReputacao(0.10f, false, false), 0.001f)
    }

    @Test
    fun `rebaixamento e demissao somam ao delta de desempenho`() {
        // O caso que não existia antes: a reputação do técnico da IA só subia.
        assertEquals(-10.0f, MercadoDeTecnicos.deltaDeReputacao(0.10f, true, true), 0.001f)
        // Campanha boa e demissão mesmo assim ainda deixa saldo negativo pequeno.
        assertEquals(0.0f, MercadoDeTecnicos.deltaDeReputacao(0.75f, true, false), 0.001f)
    }

    // ── Fim de carreira ──────────────────────────────────────────────────

    @Test
    fun `tecnico jovem nunca se aposenta`() {
        for (idade in 30..63) {
            assertEquals(idade.toString(), 0f, MercadoDeTecnicos.probabilidadeDeAposentar(idade), 0.001f)
            assertEquals(idade.toString(), false, MercadoDeTecnicos.deveAposentar(idade) { 0f })
        }
    }

    @Test
    fun `a chance de aposentar sobe com a idade`() {
        val chances = (64..71).map { MercadoDeTecnicos.probabilidadeDeAposentar(it) }
        assertEquals(chances.sortedBy { it }, chances)
        assertTrue(chances.first() > 0f && chances.first() < 0.2f)
    }

    @Test
    fun `acima do teto a aposentadoria e compulsoria`() {
        for (idade in 72..85) {
            assertEquals(1f, MercadoDeTecnicos.probabilidadeDeAposentar(idade), 0.001f)
            // Nem o sorteio mais favorável segura.
            assertEquals(idade.toString(), true, MercadoDeTecnicos.deveAposentar(idade) { 1f })
        }
    }

    @Test
    fun `o sorteio decide na faixa intermediaria`() {
        assertEquals(true, MercadoDeTecnicos.deveAposentar(70) { 0f })
        assertEquals(false, MercadoDeTecnicos.deveAposentar(70) { 0.99f })
    }
}
