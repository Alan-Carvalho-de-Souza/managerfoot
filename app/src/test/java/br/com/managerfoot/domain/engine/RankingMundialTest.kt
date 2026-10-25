package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.TipoCampeonato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regras do Ranking Mundial de Clubes da IFFHS (ver ranking_mundial.md).
 *
 * Contrato do enunciado: pesos 21/14/12/9 nas internacionais, níveis de liga
 * 4/3/2/1, copas nacionais só das oitavas em diante e pelo nível do país, empate
 * sempre metade da vitória e derrota nunca somando nem subtraindo.
 */
class RankingMundialTest {

    private val delta = 0.0001

    // ── A regra universal: empate vale metade ────────────────────────────

    @Test
    fun `empate vale metade da vitoria em qualquer peso`() {
        assertEquals(10.5, RankingMundial.pontosPorEmpate(21.0), delta)
        assertEquals(7.0, RankingMundial.pontosPorEmpate(14.0), delta)
        assertEquals(6.0, RankingMundial.pontosPorEmpate(12.0), delta)
        assertEquals(4.5, RankingMundial.pontosPorEmpate(9.0), delta)
        assertEquals(2.0, RankingMundial.pontosPorEmpate(4.0), delta)
        assertEquals(1.5, RankingMundial.pontosPorEmpate(3.0), delta)
        assertEquals(0.5, RankingMundial.pontosPorEmpate(1.0), delta)
    }

    @Test
    fun `derrota nao soma nem subtrai`() {
        // 3 vitórias e 2 empates num peso 4 = 12 + 4. As derrotas não aparecem na
        // fórmula: o modelo é puramente de acumulação.
        assertEquals(16.0, RankingMundial.pontos(4.0, vitorias = 3, empates = 2), delta)
        assertEquals(0.0, RankingMundial.pontos(4.0, vitorias = 0, empates = 0), delta)
    }

    // ── Níveis das ligas nacionais ───────────────────────────────────────

    @Test
    fun `as quatro ligas citadas no enunciado sao nivel 4`() {
        assertEquals(NivelLiga.NIVEL_4, RankingMundial.nivelDoPais("Brasil"))
        assertEquals(NivelLiga.NIVEL_4, RankingMundial.nivelDoPais("Inglaterra"))
        assertEquals(NivelLiga.NIVEL_4, RankingMundial.nivelDoPais("Espanha"))
        assertEquals(NivelLiga.NIVEL_4, RankingMundial.nivelDoPais("Italia"))
    }

    @Test
    fun `argentina e nivel 3 e uruguai nivel 2`() {
        assertEquals(NivelLiga.NIVEL_3, RankingMundial.nivelDoPais("Argentina"))
        assertEquals(NivelLiga.NIVEL_2, RankingMundial.nivelDoPais("Uruguay"))
    }

    @Test
    fun `pais desconhecido cai no nivel 1`() {
        assertEquals(NivelLiga.NIVEL_1, RankingMundial.nivelDoPais("Estados Unidos"))
        assertEquals(NivelLiga.NIVEL_1, RankingMundial.nivelDoPais(""))
    }

    @Test
    fun `grafias alternativas do pais sao aceitas`() {
        assertEquals(NivelLiga.NIVEL_4, RankingMundial.nivelDoPais(" itália "))
        assertEquals(NivelLiga.NIVEL_4, RankingMundial.nivelDoPais("BRASIL"))
    }

    // ── A cascata por divisão ────────────────────────────────────────────

    @Test
    fun `no brasil o nivel cai um degrau por serie`() {
        assertEquals(NivelLiga.NIVEL_4, RankingMundial.nivelDaLiga("Brasil", TipoCampeonato.NACIONAL_DIVISAO1))
        assertEquals(NivelLiga.NIVEL_3, RankingMundial.nivelDaLiga("Brasil", TipoCampeonato.NACIONAL_DIVISAO2))
        assertEquals(NivelLiga.NIVEL_2, RankingMundial.nivelDaLiga("Brasil", TipoCampeonato.NACIONAL_DIVISAO3))
        assertEquals(NivelLiga.NIVEL_1, RankingMundial.nivelDaLiga("Brasil", TipoCampeonato.NACIONAL_DIVISAO4))
    }

    @Test
    fun `championship e um degrau abaixo da premier`() {
        // A Premier é NACIONAL_DIVISAO1 com país Inglaterra; a Championship é a DIVISAO2.
        assertEquals(NivelLiga.NIVEL_4, RankingMundial.nivelDaLiga("Inglaterra", TipoCampeonato.NACIONAL_DIVISAO1))
        assertEquals(NivelLiga.NIVEL_3, RankingMundial.nivelDaLiga("Inglaterra", TipoCampeonato.NACIONAL_DIVISAO2))
    }

    @Test
    fun `cascata respeita o piso do nivel 1`() {
        // Uruguai é nível 2: a segunda divisão cai para 1 e não passa disso.
        assertEquals(NivelLiga.NIVEL_2, RankingMundial.nivelDaLiga("Uruguay", TipoCampeonato.EXTRANGEIRO_DIVISAO1))
        assertEquals(NivelLiga.NIVEL_1, RankingMundial.nivelDaLiga("Uruguay", TipoCampeonato.EXTRANGEIRO_DIVISAO2))
        // País de nível 1 na terceira divisão continua em 1, nunca em zero.
        assertEquals(NivelLiga.NIVEL_1, RankingMundial.nivelDaLiga("Portugal", TipoCampeonato.NACIONAL_DIVISAO3))
    }

    @Test
    fun `o que nao e liga nao tem profundidade de divisao`() {
        assertNull(RankingMundial.profundidadeDaDivisao(TipoCampeonato.COPA_NACIONAL))
        assertNull(RankingMundial.profundidadeDaDivisao(TipoCampeonato.SUPERCOPA))
        assertNull(RankingMundial.profundidadeDaDivisao(TipoCampeonato.CONTINENTAL))
    }

    // ── Peso de uma partida de liga ──────────────────────────────────────

    @Test
    fun `vitoria no brasileirao serie A vale 4`() {
        assertEquals(
            4.0,
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.NACIONAL_DIVISAO1, "Brasil", "Brasileiro Série A 2026", null
            )!!,
            delta
        )
    }

    @Test
    fun `vitoria na primera argentina vale 3`() {
        assertEquals(
            3.0,
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.EXTRANGEIRO_DIVISAO1, "Argentina", "Primera División Apertura", null
            )!!,
            delta
        )
    }

    // ── Copas nacionais ──────────────────────────────────────────────────

    @Test
    fun `copa nacional pontua em todas as fases`() {
        // Todo jogo oficial conta: a Primeira Fase vale o mesmo que a Final, que é
        // o que põe no ranking o clube de divisão inferior eliminado cedo.
        for (fase in MotorCampeonato.COPA_FASES) {
            assertEquals(
                "fase $fase",
                4.0,
                RankingMundial.pesoDaVitoria(
                    TipoCampeonato.COPA_NACIONAL, "Brasil", "Copa do Brasil 2026", fase
                )!!,
                delta
            )
        }
    }

    @Test
    fun `copa pontua mesmo sem fase gravada`() {
        assertEquals(
            4.0,
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.COPA_NACIONAL, "Brasil", "Copa do Brasil 2026", null
            )!!,
            delta
        )
    }

    @Test
    fun `copa segue o nivel do pais, nao a divisao do clube`() {
        // Uma vitória na Copa Argentina vale 3, o nível do país, mesmo que o clube
        // seja da segunda divisão.
        assertEquals(
            3.0,
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.COPA_NACIONAL, "Argentina", "Copa Argentina 2026", "Oitavas"
            )!!,
            delta
        )
    }

    // ── Supercopas, Troféu dos Campeões e estaduais: não pontuam ─────────

    @Test
    fun `supercopas e trofeu dos campeoes nao pontuam`() {
        // Jogo unico entre campeoes: o "torneio secundario" que o enunciado da
        // IFFHS manda ignorar, como a Copa da Liga. Decisao registrada em
        // ranking_mundial.md.
        assertNull(
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.SUPERCOPA, "Brasil", "Supercopa Rei 2026", "Final"
            )
        )
        assertNull(
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.SUPERCOPA, "Europa", "Supercopa da UEFA 2027", "Final"
            )
        )
        assertNull(
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.TROFEU_CAMPEOES, "Argentina", "Trofeo de Campeones", "Final"
            )
        )
    }

    @Test
    fun `estaduais nao pontuam`() {
        assertNull(RankingMundial.pesoDaVitoria(TipoCampeonato.ESTADUAL, "Brasil", "Paulistão", null))
    }

    // ── O que é ignorado ─────────────────────────────────────────────────

    @Test
    fun `so amistoso e selecoes nao pontuam`() {
        assertNull(RankingMundial.pesoDaVitoria(TipoCampeonato.AMISTOSO, "Brasil", "Amistoso", null))
        assertNull(RankingMundial.pesoDaVitoria(TipoCampeonato.SELECOES, "Brasil", "Eliminatórias", null))
    }

    @Test
    fun `so cinco tipos de campeonato nao pontuam`() {
        // A lista inteira, de uma vez, para uma competicao nova nunca entrar ou
        // sair do ranking sem que este teste aponte.
        val semPontuar = TipoCampeonato.values().filter {
            RankingMundial.pesoDaVitoria(it, "Brasil", "X", "Final") == null
        }.toSet()
        assertEquals(
            setOf(
                TipoCampeonato.ESTADUAL,        // excluido pelo enunciado
                TipoCampeonato.SUPERCOPA,       // jogo unico entre campeoes
                TipoCampeonato.TROFEU_CAMPEOES, // idem
                TipoCampeonato.SELECOES,        // nao e clube
                TipoCampeonato.AMISTOSO         // nao e competicao oficial
            ),
            semPontuar
        )
    }

    // ── Competições internacionais (dormentes) ──────────────────────────

    @Test
    fun `mundial de clubes vale 21 por vitoria`() {
        assertEquals(
            21.0,
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.MUNDIAL_CLUBES, "Brasil", "Mundial de Clubes FIFA", "Semi"
            )!!,
            delta
        )
    }

    @Test
    fun `libertadores e champions valem 14, sul-americana e europa league 12`() {
        assertEquals(
            14.0,
            RankingMundial.pesoDaVitoria(TipoCampeonato.CONTINENTAL, "Brasil", "Copa Libertadores 2026", "Semi")!!,
            delta
        )
        assertEquals(
            14.0,
            RankingMundial.pesoDaVitoria(TipoCampeonato.CONTINENTAL, "Espanha", "UEFA Champions League", "Semi")!!,
            delta
        )
        assertEquals(
            12.0,
            RankingMundial.pesoDaVitoria(TipoCampeonato.CONTINENTAL, "Brasil", "Copa Sul-Americana 2026", "Semi")!!,
            delta
        )
        assertEquals(
            12.0,
            RankingMundial.pesoDaVitoria(TipoCampeonato.CONTINENTAL, "Italia", "UEFA Europa League", "Semi")!!,
            delta
        )
    }

    @Test
    fun `continental nao reconhecida cai no peso de 9`() {
        assertEquals(
            9.0,
            RankingMundial.pesoDaVitoria(TipoCampeonato.CONTINENTAL, "Egito", "CAF Champions League", "Semi")!!,
            delta
        )
    }

    // ── Exemplo completo do documento ───────────────────────────────────

    @Test
    fun `exemplo do documento - clube brasileiro soma 126`() {
        // Brasileirão: 22 V, 8 E → 104. Copa do Brasil das oitavas: 4 V, 3 E → 22.
        val liga = RankingMundial.pontos(
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.NACIONAL_DIVISAO1, "Brasil", "Brasileiro Série A", null
            )!!,
            vitorias = 22, empates = 8
        )
        val copa = RankingMundial.pontos(
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.COPA_NACIONAL, "Brasil", "Copa do Brasil", "Oitavas"
            )!!,
            vitorias = 4, empates = 3
        )
        assertEquals(104.0, liga, delta)
        assertEquals(22.0, copa, delta)
        assertEquals(126.0, liga + copa, delta)
    }

    @Test
    fun `exemplo do documento - clube ingles soma 124`() {
        val liga = RankingMundial.pontos(
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.NACIONAL_DIVISAO1, "Inglaterra", "Premier League", null
            )!!,
            vitorias = 24, empates = 7
        )
        val copa = RankingMundial.pontos(
            RankingMundial.pesoDaVitoria(
                TipoCampeonato.COPA_NACIONAL, "Inglaterra", "Copa da Inglaterra", "Oitavas"
            )!!,
            vitorias = 3, empates = 1
        )
        assertEquals(110.0, liga, delta)
        assertEquals(14.0, copa, delta)
        assertEquals(124.0, liga + copa, delta)
    }
}
