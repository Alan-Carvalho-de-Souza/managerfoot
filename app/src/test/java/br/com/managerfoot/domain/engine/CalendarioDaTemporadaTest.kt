package br.com.managerfoot.domain.engine

import br.com.managerfoot.domain.engine.CompeticaoNoCalendario.Companion.INTERNACIONAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O calendário da temporada como dado, e o validador que o julga.
 *
 * O validador é a peça que importa: é ele que transforma "acho que esse clube
 * joga dois dias seguidos" numa falha de teste.
 */
class CalendarioDaTemporadaTest {

    private fun comp(chave: String, dias: List<Int>, pais: String = INTERNACIONAL) =
        CompeticaoNoCalendario(chave, pais, dias)

    // ── A janela ─────────────────────────────────────────────────────────

    @Test
    fun `uma liga comeca em 8 de fevereiro e termina em 30 de novembro`() {
        val dias = CalendarioDaTemporada.diasDeLiga(38)
        assertEquals(38, dias.size)
        assertEquals(CalendarioDaTemporada.PRIMEIRO_DIA, dias.first())
        assertEquals(CalendarioDaTemporada.ULTIMO_DIA_DE_LIGA, dias.last())
    }

    @Test
    fun `a liga de 38 rodadas mantem as datas que o jogo ja usa`() {
        // A fórmula antiga era 39 + (rodada*10 - 10) * 295 / 370, e o aparelho
        // confirmou as datas dela. Esta tabela não pode mudá-las.
        val dias = CalendarioDaTemporada.diasDeLiga(38)
        for (rodada in 1..38) {
            val antiga = 39 + (rodada * 10 - 10) * 295 / 370
            assertEquals("rodada $rodada", antiga, dias[rodada - 1])
        }
    }

    @Test
    fun `qualquer numero de rodadas cabe na janela`() {
        // O bug que isto conserta: a EFL Championship tem 46 rodadas e usava a
        // curva calibrada para 38. Da 39ª em diante caía depois de 30 de
        // novembro, e as rodadas 44, 45 e 46 colapsavam as três em 31 de
        // dezembro — três rodadas no mesmo dia.
        for (rodadas in listOf(20, 30, 38, 46, 58)) {
            val dias = CalendarioDaTemporada.diasDeLiga(rodadas)
            assertEquals("$rodadas rodadas", rodadas, dias.size)
            assertEquals("$rodadas rodadas", rodadas, dias.distinct().size)
            assertTrue("$rodadas rodadas: ${dias.last()}", dias.last() <= CalendarioDaTemporada.ULTIMO_DIA_DE_LIGA)
            assertTrue("$rodadas rodadas: ${dias.first()}", dias.first() >= CalendarioDaTemporada.PRIMEIRO_DIA)
            assertEquals("$rodadas rodadas", dias.sorted(), dias)
        }
    }

    @Test
    fun `a championship de 46 rodadas nao repete mais nenhum dia`() {
        val dias = CalendarioDaTemporada.diasDeLiga(46)
        assertEquals(46, dias.distinct().size)
        assertEquals(emptyList<Int>(), CalendarioDaTemporada.diasForaDaJanela(comp("EFL", dias)))
    }

    @Test
    fun `casos degenerados nao estouram`() {
        assertEquals(emptyList<Int>(), CalendarioDaTemporada.diasDeLiga(0))
        assertEquals(emptyList<Int>(), CalendarioDaTemporada.diasDeLiga(-3))
        assertEquals(listOf(CalendarioDaTemporada.PRIMEIRO_DIA), CalendarioDaTemporada.diasDeLiga(1))
    }

    // ── Dia do ano ───────────────────────────────────────────────────────

    @Test
    fun `dia do ano e o inverso da conversao de exibicao`() {
        for (diaDoAno in 1..365) {
            val (mes, dia) = CalendarioDoJogo.diaDoAnoParaMesDia(diaDoAno)
            assertEquals("dia $diaDoAno", diaDoAno, CalendarioDaTemporada.diaDoAno(mes, dia))
        }
    }

    @Test
    fun `mes invalido nao vira dia`() {
        assertEquals(0, CalendarioDaTemporada.diaDoAno(0, 10))
        assertEquals(0, CalendarioDaTemporada.diaDoAno(13, 1))
    }

    @Test
    fun `a casa de ordemGlobal vira o dia que a tela mostra`() {
        // Validado no aparelho: Champions R2 (casa 67) cai em 25 de março.
        assertEquals(
            CalendarioDaTemporada.diaDoAno(3, 25),
            CalendarioDaTemporada.diaDaCasa(67, "Champions League 2027")
        )
    }

    // ── O validador ──────────────────────────────────────────────────────

    @Test
    fun `calendario sem conflito nao acusa nada`() {
        val perfil = listOf(
            comp("Liga", listOf(39, 47, 55)),
            comp("Copa", listOf(43, 51))
        )
        assertEquals(emptyList<ConflitoDeCalendario>(), CalendarioDaTemporada.conflitosDoPerfil(perfil))
    }

    @Test
    fun `dois jogos no mesmo dia sao acusados`() {
        val perfil = listOf(comp("Liga", listOf(39, 47)), comp("Copa", listOf(47)))
        val conflitos = CalendarioDaTemporada.conflitosDoPerfil(perfil)
        assertEquals(1, conflitos.size)
        assertEquals(ConflitoDeCalendario.Tipo.MESMO_DIA, conflitos[0].tipo)
        assertEquals(47, conflitos[0].dia)
    }

    @Test
    fun `uma competicao que repete o proprio dia tambem e acusada`() {
        // É o caso da Championship colapsando rodadas em 31 de dezembro: o
        // conflito é da competição com ela mesma.
        val perfil = listOf(comp("Liga", listOf(39, 365, 365)))
        val conflitos = CalendarioDaTemporada.conflitosDoPerfil(perfil)
            .filter { it.tipo == ConflitoDeCalendario.Tipo.MESMO_DIA }
        assertEquals(1, conflitos.size)
        assertEquals("Liga", conflitos[0].competicaoA)
        assertEquals("Liga", conflitos[0].competicaoB)
    }

    @Test
    fun `dias consecutivos sao acusados`() {
        val perfil = listOf(comp("Liga", listOf(100)), comp("Copa", listOf(101)))
        val conflitos = CalendarioDaTemporada.conflitosDoPerfil(perfil)
        assertEquals(1, conflitos.size)
        assertEquals(ConflitoDeCalendario.Tipo.DIAS_CONSECUTIVOS, conflitos[0].tipo)
        assertEquals(100, conflitos[0].dia)
    }

    @Test
    fun `dois dias de folga nao sao conflito`() {
        // O ritmo observado no aparelho tem 2 dias entre a continental e a
        // rodada seguinte da liga. Isso é o calendário real do jogo, não um
        // defeito — exigir mais obrigaria a reconstruir tudo.
        val perfil = listOf(comp("Liga", listOf(100)), comp("Champions", listOf(102)))
        assertEquals(emptyList<ConflitoDeCalendario>(), CalendarioDaTemporada.conflitosDoPerfil(perfil))
    }

    @Test
    fun `o validador acha todos os conflitos, nao so o primeiro`() {
        val perfil = listOf(
            comp("Liga", listOf(50, 60, 70)),
            comp("Copa", listOf(50, 61)),
            comp("Continental", listOf(50))
        )
        val conflitos = CalendarioDaTemporada.conflitosDoPerfil(perfil)
        val mesmoDia = conflitos.filter { it.tipo == ConflitoDeCalendario.Tipo.MESMO_DIA }
        val consecutivos = conflitos.filter { it.tipo == ConflitoDeCalendario.Tipo.DIAS_CONSECUTIVOS }
        assertEquals(3, mesmoDia.size)        // Liga×Copa, Liga×Continental, Copa×Continental
        assertEquals(1, consecutivos.size)    // 60 → 61
    }

    // ── Fora da janela ───────────────────────────────────────────────────

    @Test
    fun `rodada depois de 30 de novembro e apontada`() {
        val fora = CalendarioDaTemporada.diasForaDaJanela(comp("Liga", listOf(39, 334, 340, 365)))
        assertEquals(listOf(340, 365), fora)
    }

    @Test
    fun `uma final em dezembro e esperada, e nao conta como liga`() {
        // Finais de copa e continental jogam depois do fim da liga de propósito;
        // quem é julgado pela janela é a competição de pontos corridos.
        val liga = CalendarioDaTemporada.diasDeLiga(38)
        assertEquals(emptyList<Int>(), CalendarioDaTemporada.diasForaDaJanela(comp("Liga", liga)))
    }
}
