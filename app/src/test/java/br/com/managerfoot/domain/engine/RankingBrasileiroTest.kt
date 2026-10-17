package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes do Ranking Nacional de Clubes da CBF (ver ranking_brasileiro.md). Funções
 * puras — conferem a tabela de pontos do documento posição por posição, os pesos do
 * ciclo de 5 anos e a tabela de fases da Copa do Brasil.
 */
class RankingBrasileiroTest {

    // ─────────────────────────────────────────────────────────
    //  Pesos do ciclo (5 anos, 5/4/3/2/1)
    // ─────────────────────────────────────────────────────────

    @Test
    fun `peso cai de 5 a 1 dentro da janela`() {
        assertEquals(5, RankingBrasileiro.peso(2030, 2030))
        assertEquals(4, RankingBrasileiro.peso(2029, 2030))
        assertEquals(3, RankingBrasileiro.peso(2028, 2030))
        assertEquals(2, RankingBrasileiro.peso(2027, 2030))
        assertEquals(1, RankingBrasileiro.peso(2026, 2030))
    }

    @Test
    fun `temporada fora da janela nao conta`() {
        // Não decai: simplesmente sai da conta.
        assertEquals(0, RankingBrasileiro.peso(2025, 2030))
        assertEquals(0, RankingBrasileiro.peso(2000, 2030))
        // Ano futuro também não pontua.
        assertEquals(0, RankingBrasileiro.peso(2031, 2030))
    }

    // ─────────────────────────────────────────────────────────
    //  Campeonato Brasileiro: escala por posição final
    // ─────────────────────────────────────────────────────────

    // Coluna "Série A" da tabela do documento, do 1º ao 20º.
    private val SERIE_A_ESPERADO = listOf(
        800, 640, 600, 574, 547, 521, 495, 468, 442, 416,
        389, 363, 336, 310, 284, 257, 231, 205, 178, 152
    )

    @Test
    fun `serie A reproduz a tabela do documento`() {
        SERIE_A_ESPERADO.forEachIndexed { indice, esperado ->
            assertEquals(
                "posição ${indice + 1}",
                esperado,
                RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_A, indice + 1)
            )
        }
    }

    @Test
    fun `campeao de cada serie vale a base da competicao`() {
        assertEquals(800, RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_A, 1))
        assertEquals(400, RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_B, 1))
        assertEquals(200, RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_C, 1))
        assertEquals(100, RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_D, 1))
    }

    @Test
    fun `escala percentual e a mesma nas quatro series`() {
        // Linhas do 4º e do 20º colocado, nas quatro colunas do documento.
        assertEquals(574, RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_A, 4))
        assertEquals(287, RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_B, 4))
        assertEquals(143, RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_C, 4))
        assertEquals(72,  RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_D, 4))

        assertEquals(152, RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_A, 20))
        assertEquals(76,  RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_B, 20))
        assertEquals(38,  RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_C, 20))
        assertEquals(19,  RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_D, 20))
    }

    @Test
    fun `liga maior que 20 clubes nao gera pontuacao negativa`() {
        // Piso do 20º (19% da base) vale da 20ª posição em diante.
        assertEquals(152, RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_A, 24))
    }

    @Test
    fun `copa do brasil nao pontua pela escala de posicao`() {
        assertEquals(0, RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.COPA_DO_BRASIL, 1))
    }

    // ─────────────────────────────────────────────────────────
    //  Copa do Brasil: pontos por fase máxima
    // ─────────────────────────────────────────────────────────

    @Test
    fun `copa do brasil reproduz a tabela de fases`() {
        assertEquals(600, RankingBrasileiro.pontosCopaDoBrasil(FaseCopaDoBrasil.CAMPEAO))
        assertEquals(480, RankingBrasileiro.pontosCopaDoBrasil(FaseCopaDoBrasil.VICE))
        assertEquals(420, RankingBrasileiro.pontosCopaDoBrasil(FaseCopaDoBrasil.SEMIFINAL))
        assertEquals(330, RankingBrasileiro.pontosCopaDoBrasil(FaseCopaDoBrasil.QUARTAS))
        assertEquals(240, RankingBrasileiro.pontosCopaDoBrasil(FaseCopaDoBrasil.OITAVAS))
        assertEquals(150, RankingBrasileiro.pontosCopaDoBrasil(FaseCopaDoBrasil.SEGUNDA_FASE))
        assertEquals(90,  RankingBrasileiro.pontosCopaDoBrasil(FaseCopaDoBrasil.PRIMEIRA_FASE))
    }

    @Test
    fun `fases gravadas nas partidas mapeiam para o RNC`() {
        MotorCampeonato.COPA_FASES.forEach { nome ->
            assertEquals(
                "fase $nome deveria mapear",
                true,
                RankingBrasileiro.faseDaCopaPorNome(nome) != null
            )
        }
        // Quem chegou à Final vale pelo menos o vice; o campeão é promovido no
        // repositório, com o resultado da decisão em mãos.
        assertEquals(FaseCopaDoBrasil.VICE, RankingBrasileiro.faseDaCopaPorNome("Final"))
        assertEquals(null, RankingBrasileiro.faseDaCopaPorNome("Fase de Grupos"))
    }

    // ─────────────────────────────────────────────────────────
    //  Exemplo de cálculo do documento
    // ─────────────────────────────────────────────────────────

    @Test
    fun `exemplo do documento soma 10504 pontos`() {
        val anoAtual = 2030
        val temporadas = listOf(
            // ano, série, posição, fase na Copa do Brasil
            Quad(2030, CompeticaoRnc.SERIE_A,  3, FaseCopaDoBrasil.SEMIFINAL),
            Quad(2029, CompeticaoRnc.SERIE_A,  8, FaseCopaDoBrasil.OITAVAS),
            Quad(2028, CompeticaoRnc.SERIE_A, 15, FaseCopaDoBrasil.PRIMEIRA_FASE),
            Quad(2027, CompeticaoRnc.SERIE_B,  1, FaseCopaDoBrasil.SEGUNDA_FASE),
            Quad(2026, CompeticaoRnc.SERIE_B,  6, FaseCopaDoBrasil.PRIMEIRA_FASE)
        )
        val total = temporadas.sumOf { (ano, serie, posicao, fase) ->
            val pontosAno = RankingBrasileiro.pontosPorPosicao(serie, posicao) +
                            RankingBrasileiro.pontosCopaDoBrasil(fase)
            pontosAno * RankingBrasileiro.peso(ano, anoAtual)
        }
        assertEquals(10_504, total)
    }

    @Test
    fun `teto teorico e 21000 pontos`() {
        val anoAtual = 2030
        val total = (2026..2030).sumOf { ano ->
            val pontosAno = RankingBrasileiro.pontosPorPosicao(CompeticaoRnc.SERIE_A, 1) +
                            RankingBrasileiro.pontosCopaDoBrasil(FaseCopaDoBrasil.CAMPEAO)
            pontosAno * RankingBrasileiro.peso(ano, anoAtual)
        }
        assertEquals(21_000, total)
    }

    private data class Quad(
        val ano: Int,
        val serie: CompeticaoRnc,
        val posicao: Int,
        val fase: FaseCopaDoBrasil
    )
}
