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

    private fun De(posicao: Int) = VagaDeTitulo.De(posicao)
    private val SemDetentor = VagaDeTitulo.SemDetentor
    private val Indefinida = VagaDeTitulo.Indefinida

    /** -1 vira "decidida e sem detentor", que é o que os cenários antigos diziam. */
    private fun vagaDe(posicao: Int): VagaDeTitulo =
        if (posicao > 0) VagaDeTitulo.De(posicao) else VagaDeTitulo.SemDetentor

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
            campeaoDaCopa = De(10),
            viceDaCopa = De(12)
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
                val z = VagasDoBrasileirao.distribuir(vagaDe(campeao), vagaDe(vice))
                assertEquals("campeão $campeao, vice $vice: grupos", 5, z.quantas(G))
                assertEquals("campeão $campeao, vice $vice: Pré", 2, z.quantas(P))
                assertEquals("campeão $campeao, vice $vice: Sul", 6, z.quantas(S))
                assertEquals(4, z.quantas(Z))
            }
        }
    }

    // ── Copa em andamento: a vaga fica reservada ─────────────────────────

    @Test
    fun `com a Copa em andamento a liga entrega so o G4 e o 5o`() {
        // O caso que a tabela errava: no meio da temporada a Copa do Brasil ainda
        // não tem campeão, e as duas vagas dela NÃO podem descer para a liga —
        // senão o 5º e o 6º aparecem com uma vaga que a copa vai levar.
        val z = VagasDoBrasileirao.distribuir(Indefinida, Indefinida)
        assertEquals(listOf(1, 2, 3, 4), posicoes(G, z))
        assertEquals(listOf(5), posicoes(P, z))
        assertEquals(listOf(6, 7, 8, 9, 10, 11), posicoes(S, z))
        // Só 5 vagas pintadas: as outras 2 estão reservadas à Copa do Brasil.
        assertEquals(5, z.quantas(G) + z.quantas(P))
    }

    @Test
    fun `indefinida e diferente de sem detentor`() {
        val emAndamento = VagasDoBrasileirao.distribuir(Indefinida, Indefinida)
        val decididaSemDono = VagasDoBrasileirao.distribuir(SemDetentor, SemDetentor)
        assertEquals(4, emAndamento.quantas(G))
        assertEquals(5, decididaSemDono.quantas(G))
        assertEquals(1, emAndamento.quantas(P))
        assertEquals(2, decididaSemDono.quantas(P))
    }

    @Test
    fun `a Copa decidida no meio da temporada ja pinta os dois finalistas`() {
        val z = VagasDoBrasileirao.distribuir(De(9), De(11))
        assertEquals(listOf(1, 2, 3, 4, 9), posicoes(G, z))
        assertEquals(listOf(5, 11), posicoes(P, z))
    }

    // ── Queda: sem detentor, a vaga volta para a liga ────────────────────

    @Test
    fun `sem finalistas elegiveis a liga entrega o G5 e o 6o na pre`() {
        // Copa JÁ decidida, mas nenhum dos dois na Série A — campeão da Série B,
        // por exemplo. Só aí as duas vagas descem para a liga.
        val z = VagasDoBrasileirao.distribuir(SemDetentor, SemDetentor)
        assertEquals(listOf(1, 2, 3, 4, 5), posicoes(G, z))
        assertEquals(listOf(6, 7), posicoes(P, z))
        assertEquals(listOf(8, 9, 10, 11, 12, 13), posicoes(S, z))
    }

    @Test
    fun `so o campeao elegivel deixa a liga com G4 e dois na pre`() {
        val z = VagasDoBrasileirao.distribuir(
            campeaoDaCopa = De(11),
            viceDaCopa = SemDetentor
        )
        assertEquals(listOf(1, 2, 3, 4, 11), posicoes(G, z))
        assertEquals(listOf(5, 6), posicoes(P, z))
    }

    @Test
    fun `um finalista rebaixado perde a vaga, que volta para a liga`() {
        // Regulamento da CONMEBOL: rebaixado não disputa a Libertadores.
        val z = VagasDoBrasileirao.distribuir(
            campeaoDaCopa = De(18),
            viceDaCopa = De(19)
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
            campeaoDaCopa = De(2),
            viceDaCopa = De(14)
        )
        assertEquals(listOf(1, 2, 3, 4, 5), posicoes(G, z))
        assertEquals(listOf(6, 14), posicoes(P, z))
        assertEquals(listOf(7, 8, 9, 10, 11, 12), posicoes(S, z))
    }

    @Test
    fun `os dois finalistas no topo esticam a liga`() {
        val z = VagasDoBrasileirao.distribuir(
            campeaoDaCopa = De(1),
            viceDaCopa = De(3)
        )
        // Vaga mais benéfica (Art. 5): o 1º (campeão) e o 3º (vice) já têm vaga de
        // grupo pela colocação, então vão aos grupos; a vaga de Pré do vice
        // cascateia. Grupos: 1º ao 5º; Pré: 6º e 7º. Do 1º ao 7º, todos com vaga.
        assertEquals(listOf(1, 2, 3, 4, 5), posicoes(G, z))
        assertEquals(listOf(6, 7), posicoes(P, z))
        assertTrue((1..7).all { z.zona(it) == G || z.zona(it) == P })
    }

    @Test
    fun `o vice no G5 vai aos grupos e a Pre cascateia`() {
        // Art. 5: o vice da Copa do Brasil detém uma vaga de PRÉ, mas terminando na
        // zona de grupos fica com a vaga de grupo da colocação (a mais benéfica), e
        // a vaga de Pré desce para o próximo colocado sem dono de vaga.
        val z = VagasDoBrasileirao.distribuir(
            campeaoDaCopa = SemDetentor,  // campeão sem detentor elegível
            viceDaCopa = De(2)            // vice no G5
        )
        assertEquals(G, z.zona(2))        // subiu aos grupos, não ficou na Pré
        assertEquals(listOf(1, 2, 3, 4, 5), posicoes(G, z))
        assertEquals(listOf(6, 7), posicoes(P, z))
        // A conta por destino não muda: 5 grupos + 2 Pré.
        assertEquals(5, z.quantas(G))
        assertEquals(2, z.quantas(P))
    }

    // ── O efeito cascata dos campeões continentais ───────────────────────

    @Test
    fun `campeao continental ja classificado estica o G5 para o G6`() {
        // É o exemplo do enunciado: o campeão da Libertadores terminou 3º, entra
        // pela vaga estrutural, e a vaga que ele teria pela liga desce.
        val z = VagasDoBrasileirao.distribuir(
            campeaoDaCopa = SemDetentor,
            viceDaCopa = SemDetentor,
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
            campeaoDaCopa = SemDetentor,
            viceDaCopa = SemDetentor,
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
            campeaoDaCopa = De(4),
            viceDaCopa = De(6),
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
            campeaoDaCopa = De(10),
            viceDaCopa = De(12),
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
            campeaoDaCopa = De(10),
            viceDaCopa = De(12),
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
                campeaoDaCopa = vagaDe(campeao),
                viceDaCopa = SemDetentor,
                posicoesDosCampeoesContinentais = listOf(19)
            )
            assertTrue("campeão $campeao", (17..20).all { z.zona(it) == Z })
        }
    }

    @Test
    fun `a tabela tem 20 posicoes e cada uma em uma zona so`() {
        val z = VagasDoBrasileirao.distribuir(
            campeaoDaCopa = De(7),
            viceDaCopa = De(9),
            posicoesDosCampeoesContinentais = listOf(2)
        )
        assertEquals(VagasDoBrasileirao.CLUBES, z.contagem.values.sum())
        for (zona in ZonaBrasileirao.entries) {
            assertEquals(posicoes(zona, z).size, z.quantas(zona))
        }
    }

    @Test
    fun `posicao fora da tabela nao tem zona`() {
        val z = VagasDoBrasileirao.distribuir(SemDetentor, SemDetentor)
        assertEquals(NADA, z.zona(0))
        assertEquals(NADA, z.zona(-1))
        assertEquals(NADA, z.zona(VagasDoBrasileirao.CLUBES + 1))
    }

    @Test
    fun `um detentor repetido nao consome duas vagas`() {
        // Campeão da Copa que também é campeão da Libertadores: entra uma vez, e a
        // vaga que sobra desce para a liga.
        val z = VagasDoBrasileirao.distribuir(
            campeaoDaCopa = De(3),
            viceDaCopa = SemDetentor,
            posicoesDosCampeoesContinentais = listOf(3)
        )
        assertEquals(G, z.zona(3))
        assertEquals(6, z.quantas(G))
        assertEquals(2, z.quantas(P))
    }
}
