package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.CalendarioVersao

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
        assertEquals(1 to 25, CalendarioDoJogo.naCurva(1, "Supercopa Rei 2027"))
    }

    @Test
    fun `os amistosos caem na entressafra de dezembro ou janeiro`() {
        assertEquals(12 to 20, CalendarioDoJogo.naCurva(450, "Amistoso"))
        assertEquals(1 to 15, CalendarioDoJogo.naCurva(5, "Amistoso"))
    }

    @Test
    fun `a intercontinental vem antes do ramo generico de copa`() {
        // O nome dela contém "copa", então a ordem dos ramos importa: se o ramo
        // genérico viesse primeiro, ela seria datada pela curva das copas.
        assertEquals(
            12 to 18,
            CalendarioDoJogo.naCurva(385, "Copa Intercontinental 2027")
        )
    }

    @Test
    fun `o trofeu dos campeoes e em meados de dezembro`() {
        assertEquals(12 to 15, CalendarioDoJogo.naCurva(620, ""))
        assertEquals(
            12 to 15,
            CalendarioDoJogo.naCurva(500, "Troféu dos Campeões 2027")
        )
    }

    @Test
    fun `as casas fixas da copa do brasil sao respeitadas`() {
        assertEquals(2 to 10, CalendarioDoJogo.naCurva(13, "Copa do Brasil 2027"))
        assertEquals(12 to 3, CalendarioDoJogo.naCurva(383, "Copa do Brasil 2027"))
    }

    // ── Curvas das ligas ─────────────────────────────────────────────────

    @Test
    fun `a liga comeca em 8 de fevereiro e termina em 30 de novembro`() {
        assertEquals(2 to 8, CalendarioDoJogo.naCurva(10, "Brasileiro Série A 2027"))
        assertEquals(11 to 30, CalendarioDoJogo.naCurva(380, "Brasileiro Série A 2027"))
    }

    @Test
    fun `o calendario argentino estica o mesmo periodo sobre mais rodadas`() {
        // 58 rodadas no mesmo intervalo que o brasileiro cobre com 38: na mesma
        // ordemGlobal o argentino ainda está mais atrás no ano.
        val brasil = CalendarioDoJogo.naCurva(300, "Brasileiro Série A 2027")
        val argentina = CalendarioDoJogo.naCurva(300, "Primera División Argentina 2027")
        assertTrue(
            "$argentina deveria vir antes de $brasil",
            argentina.first * 100 + argentina.second < brasil.first * 100 + brasil.second
        )
    }

    @Test
    fun `a data avanca junto com o relogio`() {
        val datas = (10..380 step 10).map { CalendarioDoJogo.naCurva(it, "") }
        val numeros = datas.map { it.first * 100 + it.second }
        assertEquals(numeros.sorted(), numeros)
    }

    @Test
    fun `sem nome de campeonato vale a curva generica das ligas`() {
        // É o caso de uma transferência, que não pertence a competição nenhuma.
        assertEquals(
            CalendarioDoJogo.naCurva(200, "Brasileiro Série A 2027"),
            CalendarioDoJogo.naCurva(200, "")
        )
    }

    // ── DataDoJogo ───────────────────────────────────────────────────────

    @Test
    fun `a data curta traz dia, mes e ano`() {
        val data = CalendarioDoJogo.ordemGlobalParaData(10, 2027, "", CalendarioVersao.CURVA)
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

    // ── Regressão: as fórmulas divergentes que existiam ──────────────────

    private fun mes(og: Int, nome: String = "") =
        CalendarioDoJogo.naCurva(og, nome).first

    @Test
    fun `o mes de uma rodada e o mesmo por qualquer caminho`() {
        // Havia três conversores: /370 no ViewModel (fecha salários), /380 no
        // repositório (atribui partidas a clubes na carreira) e /370+/410 aqui.
        // Nestas cinco rodadas da Série A o /380 dava um mês A MENOS que o /370,
        // e era perto de uma transferência que a atribuição errava o clube.
        val divergiam = mapOf(190 to 7, 230 to 8, 270 to 9, 310 to 10, 350 to 11)
        for ((og, mesEsperado) in divergiam) {
            assertEquals("OG $og", mesEsperado, mes(og, "Brasileiro Série A 2027"))
        }
    }

    @Test
    fun `o calendario argentino tem o proprio mes, e nao o generico`() {
        // O conversor do ViewModel era fixo em /370 e cego ao país, e é ele que
        // decide quando salários e patrocínio são cobrados. Em 21 das 58 rodadas
        // argentinas ele discordava da data exibida.
        val arg = "Primera División Argentina Apertura 2027"
        assertEquals(3, mes(80, arg))   // genérico daria 4
        assertEquals(4, mes(120, arg))  // genérico daria 5
        assertEquals(5, mes(160, arg))  // genérico daria 6
        assertEquals(6, mes(190, arg))  // genérico daria 7

        // E o genérico segue como era, para quem não é argentino.
        assertEquals(4, mes(80))
        assertEquals(7, mes(190))
    }

    @Test
    fun `o nome do torneio argentino e o que seleciona a curva`() {
        // Apertura e Clausura são criados com "Primera División Argentina" no
        // nome. Se isso mudar, a curva deixa de ser escolhida e o mês volta a
        // ser o genérico, em silêncio.
        for (nome in listOf(
            "Primera División Argentina Apertura 2027",
            "Primera División Argentina Clausura 2027",
            "Segunda División Argentina 2027"
        )) {
            assertEquals(nome, 3, mes(80, nome))
        }
    }

    // ── Ano no nome do campeonato ────────────────────────────────────────

    @Test
    fun `o ano sai do nome do campeonato`() {
        assertEquals(2027, CalendarioDoJogo.anoDoNomeDoCampeonato("Brasileiro Série A 2027"))
        assertEquals(2030, CalendarioDoJogo.anoDoNomeDoCampeonato("Copa do Rei 2030"))
        assertEquals(0, CalendarioDoJogo.anoDoNomeDoCampeonato("Sem ano"))
        assertEquals(0, CalendarioDoJogo.anoDoNomeDoCampeonato(""))
    }
    // ── As duas escalas ──────────────────────────────────────────────────

    @Test
    fun `numa temporada da grade o ordemGlobal ja e o dia do ano`() {
        // Não há curva nem nome de campeonato: o valor gravado é a resposta.
        for (diaDoAno in listOf(1, 39, 180, 333, 365)) {
            assertEquals(
                "dia $diaDoAno",
                CalendarioDoJogo.diaDoAnoParaMesDia(diaDoAno),
                CalendarioDoJogo.ordemGlobalParaMesDia(diaDoAno, "", CalendarioVersao.GRADE)
            )
        }
        assertEquals(2 to 8, CalendarioDoJogo.ordemGlobalParaMesDia(39, "", CalendarioVersao.GRADE))
        assertEquals(11 to 29, CalendarioDoJogo.ordemGlobalParaMesDia(333, "", CalendarioVersao.GRADE))
    }

    @Test
    fun `na grade o nome do campeonato nao muda mais a data`() {
        // É o ganho da etapa 2: a casa especial, a curva da copa e a curva
        // argentina deixam de existir, porque o dia está gravado.
        val nomes = listOf(
            "", "Brasileiro Série A 2027", "Copa do Brasil 2027",
            "Primera División Argentina Clausura 2027", "Amistoso", "Copa Intercontinental 2027"
        )
        val datas = nomes.map { CalendarioDoJogo.ordemGlobalParaMesDia(200, it, CalendarioVersao.GRADE) }
        assertEquals(1, datas.distinct().size)
    }

    @Test
    fun `a mesma casa da datas diferentes nas duas escalas`() {
        // Ler uma temporada pela escala da outra mostra data errada, e é por isso
        // que a versão viaja junto com a linha em vez de ter um valor padrão.
        val naCurva = CalendarioDoJogo.ordemGlobalParaMesDia(200, "Brasileiro Série A 2027", CalendarioVersao.CURVA)
        val naGrade = CalendarioDoJogo.ordemGlobalParaMesDia(200, "Brasileiro Série A 2027", CalendarioVersao.GRADE)
        assertEquals(7 to 9, naCurva)
        assertEquals(7 to 19, naGrade)
    }

    @Test
    fun `a data completa respeita a escala`() {
        assertEquals(
            DataDoJogo(2027, 2, 8),
            CalendarioDoJogo.ordemGlobalParaData(39, 2027, "", CalendarioVersao.GRADE)
        )
        assertEquals(
            DataDoJogo(2027, 2, 8),
            CalendarioDoJogo.ordemGlobalParaData(10, 2027, "", CalendarioVersao.CURVA)
        )
    }
}
