package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A conversão do relógio do jogo (`ordemGlobal`) em data.
 *
 * Esta peça nasceu de duas cópias divergentes — a do calendário conhecia
 * amistosos, Intercontinental e Troféu dos Campeões, e a do dashboard não — e
 * ganhou um terceiro consumidor: a data exata de uma transferência.
 */
class CalendarioDoJogoTest {

    // ── Dia do ano ───────────────────────────────────────────────────────

    @Test
    fun `o dia do ano vira mes e dia`() {
        assertEquals(1 to 1, CalendarioDoJogo.diaDoAnoParaMesDia(1))
        assertEquals(1 to 31, CalendarioDoJogo.diaDoAnoParaMesDia(31))
        assertEquals(2 to 1, CalendarioDoJogo.diaDoAnoParaMesDia(32))
        assertEquals(12 to 31, CalendarioDoJogo.diaDoAnoParaMesDia(365))
    }

    @Test
    fun `dia do ano fora do intervalo e preso nas bordas`() {
        assertEquals(1 to 1, CalendarioDoJogo.diaDoAnoParaMesDia(0))
        assertEquals(1 to 1, CalendarioDoJogo.diaDoAnoParaMesDia(-40))
        assertEquals(12 to 31, CalendarioDoJogo.diaDoAnoParaMesDia(900))
    }

    @Test
    fun `todo dia do ano produz um mes e um dia validos`() {
        val diasPorMes = listOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        for (dia in 1..365) {
            val (mes, d) = CalendarioDoJogo.diaDoAnoParaMesDia(dia)
            assertTrue("dia $dia → mês $mes", mes in 1..12)
            assertTrue("dia $dia → $d/$mes", d in 1..diasPorMes[mes])
        }
    }

    // ── Casas especiais ──────────────────────────────────────────────────

    @Test
    fun `a supercopa rei abre a temporada em 25 de janeiro`() {
        assertEquals(1 to 25, CalendarioDoJogo.ordemGlobalParaMesDia(1, "Supercopa Rei 2027"))
    }

    @Test
    fun `os amistosos caem na entressafra de dezembro ou janeiro`() {
        assertEquals(12 to 20, CalendarioDoJogo.ordemGlobalParaMesDia(450, "Amistoso"))
        assertEquals(1 to 15, CalendarioDoJogo.ordemGlobalParaMesDia(5, "Amistoso"))
    }

    @Test
    fun `a intercontinental vem antes do ramo generico de copa`() {
        // O nome dela contém "copa", então a ordem dos ramos importa: se o ramo
        // genérico viesse primeiro, ela seria datada pela curva das copas.
        assertEquals(
            12 to 18,
            CalendarioDoJogo.ordemGlobalParaMesDia(385, "Copa Intercontinental 2027")
        )
    }

    @Test
    fun `o trofeu dos campeoes e em meados de dezembro`() {
        assertEquals(12 to 15, CalendarioDoJogo.ordemGlobalParaMesDia(620, ""))
        assertEquals(
            12 to 15,
            CalendarioDoJogo.ordemGlobalParaMesDia(500, "Troféu dos Campeões 2027")
        )
    }

    @Test
    fun `as casas fixas da copa do brasil sao respeitadas`() {
        assertEquals(2 to 10, CalendarioDoJogo.ordemGlobalParaMesDia(13, "Copa do Brasil 2027"))
        assertEquals(12 to 3, CalendarioDoJogo.ordemGlobalParaMesDia(383, "Copa do Brasil 2027"))
    }

    // ── Curvas das ligas ─────────────────────────────────────────────────

    @Test
    fun `a liga comeca em 8 de fevereiro e termina em 30 de novembro`() {
        assertEquals(2 to 8, CalendarioDoJogo.ordemGlobalParaMesDia(10, "Brasileiro Série A 2027"))
        assertEquals(11 to 30, CalendarioDoJogo.ordemGlobalParaMesDia(380, "Brasileiro Série A 2027"))
    }

    @Test
    fun `o calendario argentino estica o mesmo periodo sobre mais rodadas`() {
        // 58 rodadas no mesmo intervalo que o brasileiro cobre com 38: na mesma
        // ordemGlobal o argentino ainda está mais atrás no ano.
        val brasil = CalendarioDoJogo.ordemGlobalParaMesDia(300, "Brasileiro Série A 2027")
        val argentina = CalendarioDoJogo.ordemGlobalParaMesDia(300, "Primera División Argentina 2027")
        assertTrue(
            "$argentina deveria vir antes de $brasil",
            argentina.first * 100 + argentina.second < brasil.first * 100 + brasil.second
        )
    }

    @Test
    fun `a data avanca junto com o relogio`() {
        val datas = (10..380 step 10).map { CalendarioDoJogo.ordemGlobalParaMesDia(it, "") }
        val numeros = datas.map { it.first * 100 + it.second }
        assertEquals(numeros.sorted(), numeros)
    }

    @Test
    fun `sem nome de campeonato vale a curva generica das ligas`() {
        // É o caso de uma transferência, que não pertence a competição nenhuma.
        assertEquals(
            CalendarioDoJogo.ordemGlobalParaMesDia(200, "Brasileiro Série A 2027"),
            CalendarioDoJogo.ordemGlobalParaMesDia(200, "")
        )
    }

    // ── DataDoJogo ───────────────────────────────────────────────────────

    @Test
    fun `a data curta traz dia, mes e ano`() {
        val data = CalendarioDoJogo.ordemGlobalParaData(10, 2027)
        assertEquals(DataDoJogo(2027, 2, 8), data)
        assertEquals("8 fev 2027", data.curta())
    }

    @Test
    fun `sem ano a data curta omite o ano`() {
        assertEquals("8 fev", DataDoJogo(0, 2, 8).curta())
    }

    @Test
    fun `data sem dia ainda se formata pelo mes`() {
        // É o que sobra de uma transferência gravada antes de o relógio passar
        // a ser carimbado: o mês é real, o dia é que não foi registrado.
        val semDia = DataDoJogo(2027, 3, 0)
        assertEquals(false, semDia.conhecida)   // não tem dia
        assertEquals(true, semDia.temMes)       // mas tem mês
        assertEquals("mar 2027", semDia.curta())
    }

    @Test
    fun `data sem mes nenhum nao se formata`() {
        for (data in listOf(DataDoJogo(2027, 0, 0), DataDoJogo(0, 0, 0), DataDoJogo(2027, 13, 4))) {
            assertEquals(data.toString(), false, data.temMes)
            assertEquals(data.toString(), "", data.curta())
        }
    }

    @Test
    fun `o dia so aparece quando existe`() {
        assertEquals("12 mar 2027", DataDoJogo(2027, 3, 12).curta())
        assertEquals("mar 2027", DataDoJogo(2027, 3, 0).curta())
        assertEquals("12 mar", DataDoJogo(0, 3, 12).curta())
        assertEquals("mar", DataDoJogo(0, 3, 0).curta())
    }

    @Test
    fun `a chave numerica ordena cronologicamente`() {
        val datas = listOf(
            DataDoJogo(2027, 3, 12),
            DataDoJogo(2026, 11, 30),
            DataDoJogo(2027, 3, 1),
            DataDoJogo(2028, 1, 5)
        )
        assertEquals(
            listOf(
                DataDoJogo(2026, 11, 30),
                DataDoJogo(2027, 3, 1),
                DataDoJogo(2027, 3, 12),
                DataDoJogo(2028, 1, 5)
            ),
            datas.sortedBy { it.comoNumero() }
        )
    }

    // ── Ano no nome do campeonato ────────────────────────────────────────

    @Test
    fun `o ano sai do nome do campeonato`() {
        assertEquals(2027, CalendarioDoJogo.anoDoNomeDoCampeonato("Brasileiro Série A 2027"))
        assertEquals(2030, CalendarioDoJogo.anoDoNomeDoCampeonato("Copa do Rei 2030"))
        assertEquals(0, CalendarioDoJogo.anoDoNomeDoCampeonato("Sem ano"))
        assertEquals(0, CalendarioDoJogo.anoDoNomeDoCampeonato(""))
    }
}
