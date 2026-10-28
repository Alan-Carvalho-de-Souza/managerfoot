package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A numeração das rodadas da fase de grupos da Libertadores e da Sul-Americana.
 *
 * A rodada GRAVADA é um slot dentro do campeonato: a Pré ocupa 1..6, os grupos
 * 11..16 e o mata-mata 20 em diante, para que as três fases convivam no mesmo
 * campeonato sem colidir. Esse 11 é detalhe de armazenamento — o jogador conta
 * as seis rodadas de grupo a partir de 1, e via "Rodada 11" na primeira delas.
 */
class RodadaDosGruposTest {

    private val libertadores = MotorCampeonato.FORMATO_LIBERTADORES
    private val sulAmericana = MotorCampeonato.FORMATO_SULAMERICANA

    @Test
    fun `a primeira rodada dos grupos e a primeira, nao a decima primeira`() {
        assertEquals(1, libertadores.rodadaDosGruposVisivel(11))
        assertEquals(1, sulAmericana.rodadaDosGruposVisivel(11))
    }

    @Test
    fun `as seis rodadas dos grupos sao numeradas de 1 a 6`() {
        for (formato in listOf(libertadores, sulAmericana)) {
            val visiveis = formato.rodadasDosGrupos.map { formato.rodadaDosGruposVisivel(it) }
            assertEquals(formato.nome, listOf(1, 2, 3, 4, 5, 6), visiveis)
        }
    }

    @Test
    fun `fora da faixa dos grupos nao ha numero de grupo`() {
        for (formato in listOf(libertadores, sulAmericana)) {
            // A Pré: quem nomeia essas rodadas é a fase.
            for (rodada in 1..formato.ultimaRodadaDaPre) {
                assertNull("${formato.nome} pré $rodada", formato.rodadaDosGruposVisivel(rodada))
            }
            // O intervalo entre a Pré e os grupos, e o mata-mata.
            assertNull(formato.rodadaDosGruposVisivel(10))
            assertNull(formato.rodadaDosGruposVisivel(formato.rodadaBaseMataMata))
            assertNull(formato.rodadaDosGruposVisivel(99))
        }
    }

    @Test
    fun `a faixa dos grupos nao encosta na da pre`() {
        // Se encostasse, a mesma rodada seria de duas fases ao mesmo tempo e a
        // tradução acima passaria a mentir.
        for (formato in listOf(libertadores, sulAmericana)) {
            assertEquals(
                formato.nome,
                true,
                formato.rodadaBaseGrupos > formato.ultimaRodadaDaPre
            )
            assertEquals(
                formato.nome,
                true,
                formato.rodadaBaseMataMata >= formato.rodadaBaseGrupos + formato.rodadasGrupos
            )
        }
    }

    @Test
    fun `a pre continua nomeada pela fase, e nao por numero`() {
        // Libertadores: três fases de ida e volta nas rodadas 1..6.
        assertEquals("Fase 1 — Ida", libertadores.rotuloDaRodadaPre(1))
        assertEquals("Fase 1 — Volta", libertadores.rotuloDaRodadaPre(2))
        assertEquals("Fase 3 — Volta", libertadores.rotuloDaRodadaPre(6))
        assertNull(libertadores.rotuloDaRodadaPre(11))

        // Sul-Americana: uma fase só, de jogo único, na rodada 1.
        assertEquals(
            ListaDeAcessoSulAmericana.PRIMEIRA_FASE,
            sulAmericana.rotuloDaRodadaPre(1)
        )
        assertNull(sulAmericana.rotuloDaRodadaPre(2))
    }
}
