package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras do Coeficiente de Clubes da UEFA (ver ranking_europa.md).
 *
 * As âncoras do documento são o contrato: 12 no 1º da Champions, 6,25 no 24º e 6 do
 * 25º ao 36º; 6 e 0,25 na Europa League; 4 e 0,125 na Conference.
 */
class RankingEuropaTest {

    private val delta = 0.0001

    // ── Bônus por posição na Fase de Liga ────────────────────────────────

    @Test
    fun `champions usa passo de 0,25 e fecha exatamente no 24o`() {
        assertEquals(0.25, RankingEuropa.passoDaEscada(CompeticaoUefa.CHAMPIONS_LEAGUE), delta)
        assertEquals(12.0, RankingEuropa.bonusPorPosicao(CompeticaoUefa.CHAMPIONS_LEAGUE, 1), delta)
        assertEquals(11.75, RankingEuropa.bonusPorPosicao(CompeticaoUefa.CHAMPIONS_LEAGUE, 2), delta)
        assertEquals(6.25, RankingEuropa.bonusPorPosicao(CompeticaoUefa.CHAMPIONS_LEAGUE, 24), delta)
    }

    @Test
    fun `champions da 6 pontos fixos do 25o ao 36o`() {
        for (posicao in 25..RankingEuropa.CLUBES_FASE_LIGA) {
            assertEquals(
                "posição $posicao",
                6.0,
                RankingEuropa.bonusPorPosicao(CompeticaoUefa.CHAMPIONS_LEAGUE, posicao),
                delta
            )
        }
    }

    @Test
    fun `europa league tambem fecha com passo de 0,25`() {
        assertEquals(0.25, RankingEuropa.passoDaEscada(CompeticaoUefa.EUROPA_LEAGUE), delta)
        assertEquals(6.0, RankingEuropa.bonusPorPosicao(CompeticaoUefa.EUROPA_LEAGUE, 1), delta)
        assertEquals(3.0, RankingEuropa.bonusPorPosicao(CompeticaoUefa.EUROPA_LEAGUE, 13), delta)
        assertEquals(0.25, RankingEuropa.bonusPorPosicao(CompeticaoUefa.EUROPA_LEAGUE, 24), delta)
    }

    @Test
    fun `conference respeita as ancoras ainda que o passo nao seja redondo`() {
        assertEquals(4.0, RankingEuropa.bonusPorPosicao(CompeticaoUefa.CONFERENCE_LEAGUE, 1), delta)
        assertEquals(0.125, RankingEuropa.bonusPorPosicao(CompeticaoUefa.CONFERENCE_LEAGUE, 24), delta)
        // O passo da Conference não é 0,25 nem 0,125: é 3,875/23 ≈ 0,1685.
        assertEquals(0.168478, RankingEuropa.passoDaEscada(CompeticaoUefa.CONFERENCE_LEAGUE), delta)
    }

    @Test
    fun `posicao invalida nao gera bonus`() {
        assertEquals(0.0, RankingEuropa.bonusPorPosicao(CompeticaoUefa.CHAMPIONS_LEAGUE, 0), delta)
        assertEquals(0.0, RankingEuropa.bonusPorPosicao(CompeticaoUefa.CHAMPIONS_LEAGUE, -3), delta)
    }

    // ── Pontos por partida ───────────────────────────────────────────────

    @Test
    fun `vitoria vale 2 e empate vale 1`() {
        assertEquals(12.0, RankingEuropa.pontosPorPartidas(vitorias = 5, empates = 2), delta)
        assertEquals(0.0, RankingEuropa.pontosPorPartidas(vitorias = 0, empates = 0), delta)
    }

    // ── Bônus por avanço no mata-mata ────────────────────────────────────

    @Test
    fun `bonus de avanco e acumulativo e comeca nas oitavas`() {
        val ucl = CompeticaoUefa.CHAMPIONS_LEAGUE
        assertEquals(1.5, RankingEuropa.bonusPorAvanco(ucl, FaseUefa.OITAVAS), delta)
        assertEquals(3.0, RankingEuropa.bonusPorAvanco(ucl, FaseUefa.QUARTAS), delta)
        assertEquals(4.5, RankingEuropa.bonusPorAvanco(ucl, FaseUefa.SEMIFINAL), delta)
        assertEquals(6.0, RankingEuropa.bonusPorAvanco(ucl, FaseUefa.FINAL), delta)
        // Quem não chegou às oitavas não tem bônus de avanço.
        assertEquals(0.0, RankingEuropa.bonusPorAvanco(ucl, null), delta)
    }

    @Test
    fun `cada competicao tem o seu passo de mata-mata`() {
        assertEquals(4.0, RankingEuropa.bonusPorAvanco(CompeticaoUefa.EUROPA_LEAGUE, FaseUefa.FINAL), delta)
        assertEquals(2.0, RankingEuropa.bonusPorAvanco(CompeticaoUefa.CONFERENCE_LEAGUE, FaseUefa.FINAL), delta)
    }

    // ── Total de uma campanha ────────────────────────────────────────────

    @Test
    fun `campanha soma bonus de posicao, partidas e avanco`() {
        // Exemplo do ranking_europa.md: 5º na Fase de Liga, 5V 2E 1D, cai nas quartas
        // com 2 vitórias e 2 derrotas. 11 + (10+2) + 4 + 3 = 30.
        val total = RankingEuropa.pontosDaCampanha(
            competicao = CompeticaoUefa.CHAMPIONS_LEAGUE,
            posicaoFaseLiga = 5,
            vitorias = 7,
            empates = 2,
            faseAlcancada = FaseUefa.QUARTAS
        )
        assertEquals(30.0, total, delta)
    }

    @Test
    fun `garantia de participacao e piso do total, nao do bonus`() {
        // 24º da Europa League, sem pontuar nenhuma partida: o bônus é 0,25, mas a
        // garantia de participação de 3 pontos eleva o total.
        val total = RankingEuropa.pontosDaCampanha(
            competicao = CompeticaoUefa.EUROPA_LEAGUE,
            posicaoFaseLiga = 24,
            vitorias = 0,
            empates = 0
        )
        assertEquals(3.0, total, delta)
    }

    @Test
    fun `garantia nao reduz quem ja pontuou acima dela`() {
        val total = RankingEuropa.pontosDaCampanha(
            competicao = CompeticaoUefa.CONFERENCE_LEAGUE,
            posicaoFaseLiga = 1,
            vitorias = 6,
            empates = 0,
            faseAlcancada = FaseUefa.FINAL
        )
        // 4 + 12 + 2 = 18, bem acima da garantia de 2,5.
        assertEquals(18.0, total, delta)
        assertTrue(total > CompeticaoUefa.CONFERENCE_LEAGUE.garantiaParticipacao)
    }

    // ── Qualificação ─────────────────────────────────────────────────────

    @Test
    fun `qualificacao rende so a consolacao fixa da rodada`() {
        assertEquals(1.0, RankingEuropa.pontosDaQualificacao(FaseQualificatoriaUefa.PLAY_OFF), delta)
        assertEquals(0.25, RankingEuropa.pontosDaQualificacao(FaseQualificatoriaUefa.PRIMEIRA_PRE), delta)
    }

    // ── Janela de 5 temporadas, sem pesos ───────────────────────────────

    @Test
    fun `as cinco temporadas da janela valem igual`() {
        val anoAtual = 2030
        for (distancia in 0 until RankingEuropa.JANELA_ANOS) {
            assertEquals(
                "distância $distancia",
                1.0,
                RankingEuropa.peso(anoAtual - distancia, anoAtual),
                delta
            )
        }
    }

    @Test
    fun `temporada fora da janela sai da conta de uma vez`() {
        assertEquals(0.0, RankingEuropa.peso(2025, 2030), delta)
        // Ano futuro também não pontua, por segurança.
        assertEquals(0.0, RankingEuropa.peso(2031, 2030), delta)
    }

    // ── Piso de 20% do país ─────────────────────────────────────────────

    @Test
    fun `coeficiente do pais e a media por clube, nao a soma`() {
        val pontos = listOf(40.0, 30.0, 20.0, 10.0)
        assertEquals(25.0, RankingEuropa.coeficienteDoPais(pontos), delta)
    }

    @Test
    fun `piso socorre o clube que nao se classificou`() {
        val coeficientePais = 50.0   // piso = 10
        assertEquals(10.0, RankingEuropa.aplicarPisoDoPais(0.0, coeficientePais), delta)
        assertEquals(10.0, RankingEuropa.aplicarPisoDoPais(7.5, coeficientePais), delta)
    }

    @Test
    fun `piso nunca reduz quem pontuou acima dele`() {
        assertEquals(42.0, RankingEuropa.aplicarPisoDoPais(42.0, 50.0), delta)
    }

    @Test
    fun `pais sem clubes nao gera piso`() {
        assertEquals(0.0, RankingEuropa.coeficienteDoPais(emptyList()), delta)
        assertEquals(0.0, RankingEuropa.aplicarPisoDoPais(0.0, 0.0), delta)
    }

    // ── Elegibilidade ───────────────────────────────────────────────────

    @Test
    fun `so divisoes de elite entram, a championship fica fora`() {
        assertTrue(RankingEuropa.ehDivisaoElite(11))   // Premier League
        assertTrue(RankingEuropa.ehDivisaoElite(13))   // La Liga
        assertTrue(RankingEuropa.ehDivisaoElite(14))   // Serie A
        assertTrue(!RankingEuropa.ehDivisaoElite(12))  // Championship
        assertTrue(!RankingEuropa.ehDivisaoElite(1))   // Brasileirão Série A
    }
}
