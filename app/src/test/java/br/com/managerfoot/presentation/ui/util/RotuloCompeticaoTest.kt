package br.com.managerfoot.presentation.ui.util

import br.com.managerfoot.data.datasource.SaveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O rótulo da competição de uma partida.
 *
 * O bug que originou isto: um clube da La Liga via "BRASILEIRÃO · RODADA 4" no
 * card de próxima partida, porque a cadeia de `when` da tela só conhecia os
 * campeonatos brasileiros e argentinos e terminava num `else` que chutava o
 * Brasileirão. Daí o teste mais importante aqui ser o do campeonato
 * DESCONHECIDO: ele não pode receber nome de competição nenhuma.
 */
class RotuloCompeticaoTest {

    /**
     * Um save em que todo campeonato existe, cada um com um id distinto, para
     * que nenhum rótulo possa casar por acidente com o id de outro.
     */
    private val save = SaveState(
        timeIdJogador = 100,
        temporadaId = 1,
        campeonatoId = 13,               // o clube do jogador está na La Liga
        campeonatoAId = 1,
        campeonatoBId = 2,
        campeonatoCId = 3,
        campeonatoDId = 4,
        campeonatoArgAId = 5,
        campeonatoArgBId = 6,
        campeonatoArgClausuraId = 7,
        campeonatoUruAperturaId = 8,
        campeonatoUruClausuraId = 9,
        campeonatoUruIntermedId = 10,
        campeonatoUruBId = 11,
        campeonatoUruBCompetId = 12,
        campeonatoPremierId = 20,
        campeonatoChampionshipId = 21,
        campeonatoLaLigaId = 13,
        campeonatoSerieAId = 14,
        copaId = 30,
        copaArgId = 31,
        copaInglaterraId = 32,
        copaEspanholaId = 33,
        copaItalianaId = 34,
        championsId = 40,
        europaLeagueId = 41,
        libertadoresId = 42,
        sulAmericanaId = 43,
        supercopaId = 50,
        supercopaInglaterraId = 51,
        supercopaEspanholaId = 52,
        supercopaItalianaId = 53,
        supercopaUefaId = 54,
        intercontinentalId = 60,
        recopaId = 61,
        anoAtual = 2027,
        mesAtual = 3,
        jogoInicializado = true,
        patrocinadorValorAnual = 0,
        patrocinadorTipo = 0,
        patrocinadorPreCreditado = 0,
        campeonatoAmistososId = 70
    )

    private val mapa = MapaDeCompeticoes.de(save)

    // ── O bug original ───────────────────────────────────────────────────

    @Test
    fun `a liga do clube do jogador e nomeada, nao o brasileirao`() {
        assertEquals("La Liga", mapa.daProximaPartida(13, fase = null, rodada = 4))
        assertEquals("Premier League", mapa.daProximaPartida(20, fase = null, rodada = 4))
        assertEquals("Serie A", mapa.daProximaPartida(14, fase = null, rodada = 4))
        assertEquals("EFL Championship", mapa.daProximaPartida(21, fase = null, rodada = 4))
    }

    @Test
    fun `campeonato desconhecido nao vira brasileirao`() {
        // O coração do bug: na falta de resposta, a tela mostra a rodada.
        assertEquals("Rodada 7", mapa.daProximaPartida(999, fase = null, rodada = 7))
        assertEquals("Partida", mapa.daProximaPartida(999, fase = null, rodada = 0))
        assertNull(mapa.de(999))
    }

    @Test
    fun `campeonato ausente do save nao casa com o id negativo de outro`() {
        // Sem copa argentina nem supercopas, todos eles valem -1 ao mesmo tempo.
        val semCopas = save.copy(
            copaArgId = -1,
            supercopaId = -1,
            supercopaInglaterraId = -1,
            intercontinentalId = -1
        )
        val m = MapaDeCompeticoes.de(semCopas)
        assertNull(m.de(-1))
        assertEquals("Rodada 3", m.daProximaPartida(-1, fase = null, rodada = 3))
        // E os que continuam no save seguem nomeados.
        assertEquals("Copa do Brasil — Oitavas", m.daProximaPartida(30, "Oitavas", 0))
    }

    // ── Copas e supercopas ───────────────────────────────────────────────

    @Test
    fun `copas nacionais mostram a fase, inclusive as europeias`() {
        assertEquals("Copa do Rei — Oitavas", mapa.daProximaPartida(33, "Oitavas", 0))
        assertEquals("Copa da Inglaterra — Final", mapa.daProximaPartida(32, "Final", 0))
        assertEquals("Coppa Italia — Quartas", mapa.daProximaPartida(34, "Quartas", 0))
        assertEquals("Copa da Argentina — Semi", mapa.daProximaPartida(31, "Semi", 0))
    }

    @Test
    fun `supercopa e jogo unico, sem fase e sem rodada`() {
        assertEquals("Supercopa da Espanha", mapa.daProximaPartida(52, fase = null, rodada = 1))
        assertEquals("Supercopa da UEFA", mapa.doResultado(54, fase = null, rodada = 1))
        assertEquals("Supercopa Rei", mapa.daProximaPartida(50, fase = null, rodada = 0))
    }

    // ── Continentais: partida sem fase ───────────────────────────────────

    @Test
    fun `sem fase, a continental cai na fase padrao do formato dela`() {
        // Champions e Europa League gravam fase = null na fase de liga; a
        // Libertadores e a Sul-Americana, na fase de grupos.
        assertEquals("Champions League — Fase de Liga", mapa.daProximaPartida(40, null, 3))
        assertEquals("Europa League — Fase de Liga", mapa.daProximaPartida(41, null, 3))
        assertEquals("Libertadores — Fase de Grupos", mapa.daProximaPartida(42, null, 3))
        assertEquals("Copa Sul-Americana — Fase de Grupos", mapa.daProximaPartida(43, null, 3))
    }

    @Test
    fun `com fase, a continental mostra a fase que veio na partida`() {
        assertEquals("Champions League — Play-off", mapa.daProximaPartida(40, "Play-off", 0))
        assertEquals("Libertadores — Fase 2", mapa.daProximaPartida(42, "Fase 2", 0))
    }

    @Test
    fun `as finais de jogo unico ou ida e volta ja vem como final`() {
        assertEquals("Copa Intercontinental — Final", mapa.daProximaPartida(60, null, 0))
        assertEquals("Recopa Sul-Americana — Final", mapa.daProximaPartida(61, null, 0))
    }

    // ── Rótulo curto dos últimos resultados ──────────────────────────────

    @Test
    fun `no historico a liga leva a rodada junto`() {
        assertEquals("La Liga · R4", mapa.doResultado(13, fase = null, rodada = 4))
        assertEquals("Série A · R12", mapa.doResultado(1, fase = null, rodada = 12))
    }

    @Test
    fun `no historico a copa leva a fase, nao a rodada`() {
        assertEquals("Copa do Rei — Oitavas", mapa.doResultado(33, "Oitavas", 5))
    }

    @Test
    fun `amistoso nao tem rodada nem fase`() {
        assertEquals("Amistoso", mapa.daProximaPartida(70, fase = null, rodada = 1))
        assertEquals("Amistoso", mapa.doResultado(70, fase = null, rodada = 1))
    }

    // ── Convenção do travessão ───────────────────────────────────────────

    @Test
    fun `so copa e continental usam travessao`() {
        // MatchCard só acrescenta "Rodada N" quando o rótulo NÃO tem travessão.
        // Uma liga com travessão ficaria sem a rodada na tela.
        for (id in listOf(1, 13, 14, 20, 21, 50, 54, 70)) {
            val rotulo = mapa.daProximaPartida(id, fase = null, rodada = 4)
            assertEquals("$id: $rotulo", false, rotulo.contains("—"))
        }
        for (id in listOf(30, 33, 40, 42, 60)) {
            val rotulo = mapa.daProximaPartida(id, fase = null, rodada = 4)
            assertEquals("$id: $rotulo", true, rotulo.contains("—"))
        }
    }

    // ── Save ausente ─────────────────────────────────────────────────────

    @Test
    fun `sem save nao ha rotulo nenhum`() {
        val vazio = MapaDeCompeticoes.de(null)
        assertNull(vazio.de(13))
        assertEquals("Rodada 4", vazio.daProximaPartida(13, fase = null, rodada = 4))
    }
}
