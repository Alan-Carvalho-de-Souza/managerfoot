package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A distribuição das vagas continentais do Brasil sobre a tabela da Série A.
 *
 * O que estes testes travam é a tabela da distribuição padrão — 1º ao 4º nos
 * grupos, campeão da Copa do Brasil nos grupos, 5º na Pré, vice da Copa na Pré —
 * e o EFEITO CASCATA: vaga que sobra desce para o próximo colocado mantendo o seu
 * destino, o que estica o G-5 para G-6 e além.
 */
class VagasDoBrasileiraoTest {

    private val G = ZonaBrasileirao.LIBERTADORES_GRUPOS
    private val P = ZonaBrasileirao.LIBERTADORES_PRE
    private val S = ZonaBrasileirao.SUL_AMERICANA
    private val Z = ZonaBrasileirao.REBAIXAMENTO
    private val NADA = ZonaBrasileirao.NENHUMA

    private fun posicoes(zona: ZonaBrasileirao, z: ZonasDoBrasileirao) =
        (1..VagasDoBrasileirao.CLUBES).filter { z.zona(it) == zona }

    // ── A distribuição padrão ────────────────────────────────────────────

    @Test
    fun `as quantidades saem das cotas das duas competicoes`() {
        assertEquals(7, VagasDoBrasileirao.VAGAS_LIBERTADORES)
        assertEquals(6, VagasDoBrasileirao.VAGAS_SUL_AMERICANA)
        // O G4 e o 5º: é o que a liga distribui quando as duas vagas de título da
        // Copa do Brasil têm titular.
        assertEquals(4, VagasDoBrasileirao.VAGAS_DE_GRUPO_DA_LIGA)
        assertEquals(1, VagasDoBrasileirao.VAGAS_DE_PRE_DA_LIGA)
        assertEquals(17, VagasDoBrasileirao.PRIMEIRA_REBAIXADA)
    }

    @Test
    fun `a distribuicao padrao e 1o ao 4o nos grupos e o 5o na pre`() {
        // Campeão da Copa em 10º e vice em 12º: fora do G5, então a liga entrega
        // exatamente a tabela da distribuição padrão.
        val z = VagasDoBrasileirao.distribuir(
            posicaoDoCampeaoDaCopa = 10,
            posicaoDoViceDaCopa = 12
        )
        assertEquals(listOf(1, 2, 3, 4, 10), posicoes(G, z))
        assertEquals(listOf(5, 12), posicoes(P, z))
        // Sul-Americana: os 6 seguintes, pulando quem já está dentro.
        assertEquals(listOf(6, 7, 8, 9, 11, 13), posicoes(S, z))
        assertEquals(listOf(17, 18, 19, 20), posicoes(Z, z))
        assertEquals(listOf(14, 15, 16), posicoes(NADA, z))
    }

    @Test
    fun `a conta por destino nunca muda`() {
        // 7 na Libertadores (5 grupos + 2 Pré) e 6 na Sul-Americana, em qualquer
        // arranjo de detentores elegíveis.
        for (campeao in listOf(-1, 1, 5, 9)) {
            for (vice in listOf(-1, 2, 6, 13)) {
                if (campeao == vice && campeao > 0) continue
                val z = VagasDoBrasileirao.distribuir(campeao, vice)
                assertEquals("campeão $campeao, vice $vice: grupos", 5, z.quantas(G))
                assertEquals("campeão $campeao, vice $vice: Pré", 2, z.quantas(P))
                assertEquals("campeão $campeao, vice $vice: Sul", 6, z.quantas(S))
                assertEquals(4, z.quantas(Z))
            }
        }
    }

    // ── Queda: sem detentor, a vaga volta para a liga ────────────────────

    @Test
    fun `sem finalistas elegiveis a liga entrega o G5 e o 6o na pre`() {
        // Nenhum dos dois na Série A — campeão da Série B, por exemplo.
        val z = VagasDoBrasileirao.distribuir()
        assertEquals(listOf(1, 2, 3, 4, 5), posicoes(G, z))
        assertEquals(listOf(6, 7), posicoes(P, z))
        assertEquals(listOf(8, 9, 10, 11, 12, 13), posicoes(S, z))
    }

    @Test
    fun `so o campeao elegivel deixa a liga com G4 e dois na pre`() {
        val z = VagasDoBrasileirao.distribuir(posicaoDoCampeaoDaCopa = 11)
        assertEquals(listOf(1, 2, 3, 4, 11), posicoes(G, z))
        assertEquals(listOf(5, 6), posicoes(P, z))
    }

    @Test
    fun `um finalista rebaixado perde a vaga, que volta para a liga`() {
        // Regulamento da CONMEBOL: rebaixado não disputa a Libertadores.
        val z = VagasDoBrasileirao.distribuir(
            posicaoDoCampeaoDaCopa = 18,
            posicaoDoViceDaCopa = 19
        )
        assertEquals(Z, z.zona(18))
        assertEquals(Z, z.zona(19))
        // As duas vagas voltaram: G5 e dois na Pré, como se não houvesse copa.
        assertEquals(listOf(1, 2, 3, 4, 5), posicoes(G, z))
        assertEquals(listOf(6, 7), posicoes(P, z))
    }

    // ── Acúmulo: finalista já classificado pela liga ─────────────────────

    @Test
    fun `campeao da Copa dentro do G4 nao ocupa duas vagas`() {
        // Ele fica com a vaga de título, e a da liga cascateia: o 5º passa a ter
        // vaga de grupo e o 6º a de Pré.
        val z = VagasDoBrasileirao.distribuir(
            posicaoDoCampeaoDaCopa = 2,
            posicaoDoViceDaCopa = 14
        )
        assertEquals(listOf(1, 2, 3, 4, 5), posicoes(G, z))
        assertEquals(listOf(6, 14), posicoes(P, z))
        assertEquals(listOf(7, 8, 9, 10, 11, 12), posicoes(S, z))
    }

    @Test
    fun `os dois finalistas no topo esticam a liga`() {
        val z = VagasDoBrasileirao.distribuir(
            posicaoDoCampeaoDaCopa = 1,
            posicaoDoViceDaCopa = 3
        )
        // O 1º fica com a vaga de grupo do campeão e o 3º com a de PRÉ do vice —
        // o destino é da vaga, não da colocação. A liga completa os grupos com
        // 2º, 4º, 5º e 6º, e a Pré com o 7º. Do 1º ao 7º, todos com vaga.
        assertEquals(listOf(1, 2, 4, 5, 6), posicoes(G, z))
        assertEquals(listOf(3, 7), posicoes(P, z))
        assertTrue((1..7).all { z.zona(it) == G || z.zona(it) == P })
    }

    // ── O efeito cascata dos campeões continentais ───────────────────────

    @Test
    fun `campeao continental ja classificado estica o G5 para o G6`() {
        // É o exemplo do enunciado: o campeão da Libertadores terminou 3º, entra
        // pela vaga estrutural, e a vaga que ele teria pela liga desce.
        val z = VagasDoBrasileirao.distribuir(
            posicoesDosCampeoesContinentais = listOf(3)
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6), posicoes(G, z))
        assertEquals(listOf(7, 8), posicoes(P, z))
        // Seis clubes nos grupos: a vaga estrutural é por fora da cota.
        assertEquals(6, z.quantas(G))
        assertEquals(listOf(9, 10, 11, 12, 13, 14), posicoes(S, z))
    }

    @Test
    fun `os dois campeoes continentais esticam ainda mais`() {
        val z = VagasDoBrasileirao.distribuir(
            posicoesDosCampeoesContinentais = listOf(2, 5)
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), posicoes(G, z))
        assertEquals(listOf(8, 9), posicoes(P, z))
        assertEquals(7, z.quantas(G))
    }

    @Test
    fun `acumulo maximo chega ao G9`() {
        // Dois campeões continentais e os dois finalistas da Copa, todos no topo:
        // nove clubes com vaga de Libertadores, do 1º ao 9º.
        val z = VagasDoBrasileirao.distribuir(
            posicaoDoCampeaoDaCopa = 4,
            posicaoDoViceDaCopa = 6,
            posicoesDosCampeoesContinentais = listOf(1, 2)
        )
        val comVaga = (1..9).filter { z.zona(it) == G || z.zona(it) == P }
        assertEquals((1..9).toList(), comVaga)
        assertEquals(7, z.quantas(G))
        assertEquals(2, z.quantas(P))
        // E a Sul-Americana segue com 6, logo abaixo.
        assertEquals(listOf(10, 11, 12, 13, 14, 15), posicoes(S, z))
    }

    @Test
    fun `campeao continental fora da Serie A nao estica nada`() {
        // Campeão vigente de outro país, ou brasileiro já rebaixado: a lista de
        // posições chega com -1 ou com uma posição de rebaixado.
        val z = VagasDoBrasileirao.distribuir(
            posicaoDoCampeaoDaCopa = 10,
            posicaoDoViceDaCopa = 12,
            posicoesDosCampeoesContinentais = listOf(-1, 18)
        )
        assertEquals(listOf(1, 2, 3, 4, 10), posicoes(G, z))
        assertEquals(Z, z.zona(18))
    }

    // ── A vaga remanejada ────────────────────────────────────────────────

    @Test
    fun `a vaga remanejada vira uma vaga de grupo a mais`() {
        // Primeira temporada: nenhum campeão continental existe, as duas vagas
        // estruturais ficam órfãs e o Brasil, primeiro na ordem da cota, leva uma.
        val z = VagasDoBrasileirao.distribuir(
            posicaoDoCampeaoDaCopa = 10,
            posicaoDoViceDaCopa = 12,
            vagasRemanejadas = 1
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 10), posicoes(G, z))
        assertEquals(listOf(6, 12), posicoes(P, z))
        assertEquals(VagasDoBrasileirao.VAGAS_LIBERTADORES + 1, z.quantas(G) + z.quantas(P))
    }

    // ── Invariantes ──────────────────────────────────────────────────────

    @Test
    fun `nenhuma vaga invade o rebaixamento`() {
        for (campeao in listOf(-1, 3, 17, 20)) {
            val z = VagasDoBrasileirao.distribuir(
                posicaoDoCampeaoDaCopa = campeao,
                posicoesDosCampeoesContinentais = listOf(19)
            )
            assertTrue("campeão $campeao", (17..20).all { z.zona(it) == Z })
        }
    }

    @Test
    fun `a tabela tem 20 posicoes e cada uma em uma zona so`() {
        val z = VagasDoBrasileirao.distribuir(
            posicaoDoCampeaoDaCopa = 7,
            posicaoDoViceDaCopa = 9,
            posicoesDosCampeoesContinentais = listOf(2)
        )
        assertEquals(VagasDoBrasileirao.CLUBES, z.contagem.values.sum())
        for (zona in ZonaBrasileirao.entries) {
            assertEquals(posicoes(zona, z).size, z.quantas(zona))
        }
    }

    @Test
    fun `posicao fora da tabela nao tem zona`() {
        val z = VagasDoBrasileirao.distribuir()
        assertEquals(NADA, z.zona(0))
        assertEquals(NADA, z.zona(-1))
        assertEquals(NADA, z.zona(VagasDoBrasileirao.CLUBES + 1))
    }

    @Test
    fun `um detentor repetido nao consome duas vagas`() {
        // Campeão da Copa que também é campeão da Libertadores: entra uma vez, e a
        // vaga que sobra desce para a liga.
        val z = VagasDoBrasileirao.distribuir(
            posicaoDoCampeaoDaCopa = 3,
            posicoesDosCampeoesContinentais = listOf(3)
        )
        assertEquals(G, z.zona(3))
        assertEquals(6, z.quantas(G))
        assertEquals(2, z.quantas(P))
    }
}
