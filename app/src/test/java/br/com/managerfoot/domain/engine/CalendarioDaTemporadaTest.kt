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

    // ── A grade ──────────────────────────────────────────────────────────

    @Test
    fun `a temporada abre em 8 de fevereiro e a ultima semana e 29 de novembro`() {
        assertEquals(39, CalendarioDaTemporada.dia(0, CalendarioDaTemporada.FIM_DE_SEMANA))
        assertEquals(333, CalendarioDaTemporada.ULTIMO_DIA_DE_LIGA)
        assertEquals(
            CalendarioDaTemporada.ULTIMO_DIA_DE_LIGA,
            CalendarioDaTemporada.dia(
                CalendarioDaTemporada.SEMANAS - 1, CalendarioDaTemporada.FIM_DE_SEMANA
            )
        )
        // 29 de novembro.
        assertEquals(11 to 29, CalendarioDoJogo.diaDoAnoParaMesDia(333))
    }

    @Test
    fun `duas casas distintas da grade nunca sao o mesmo dia nem dias seguidos`() {
        // É A garantia da grade, e é dela que todo o resto depende: se duas
        // casas distintas podem ficar a menos de 2 dias, nenhuma alocação de
        // competições salva o calendário.
        val todas = (0 until CalendarioDaTemporada.SEMANAS).flatMap { semana ->
            CalendarioDaTemporada.CASAS.map { casa -> CalendarioDaTemporada.dia(semana, casa) }
        }
        assertEquals(todas.size, todas.distinct().size)
        val ordenados = todas.sorted()
        for (i in 0 until ordenados.size - 1) {
            assertTrue(
                "${ordenados[i]} e ${ordenados[i + 1]} distam menos de 2 dias",
                ordenados[i + 1] - ordenados[i] >= 2
            )
        }
    }

    @Test
    fun `a ultima casa de uma semana dista 2 dias da primeira da seguinte`() {
        // É o ponto mais apertado da grade: 2 dias exatos. Encurtar a semana ou
        // empurrar MEIO_DE_SEMANA_2 para +6 quebraria aqui, e em nenhum outro
        // lugar — por isso este teste existe separado do anterior.
        val fim = CalendarioDaTemporada.dia(0, CalendarioDaTemporada.MEIO_DE_SEMANA_2)
        val inicioDaSeguinte = CalendarioDaTemporada.dia(1, CalendarioDaTemporada.FIM_DE_SEMANA)
        assertEquals(2, inicioDaSeguinte - fim)
    }

    @Test
    fun `semanas espacadas pegam as duas pontas`() {
        val semanas = CalendarioDaTemporada.semanasEspacadas(18)
        assertEquals(18, semanas.size)
        assertEquals(0, semanas.first())
        assertEquals(CalendarioDaTemporada.SEMANAS - 1, semanas.last())
        assertEquals(semanas.sorted(), semanas)
        assertEquals(18, semanas.distinct().size)
    }

    @Test
    fun `os conjuntos deslocados de uma semana exigem que o original tenha folga`() {
        // SEMANAS_SULAMERICANA e SEMANAS_COPA_EUROPEIA são "a semana seguinte" de
        // outro conjunto, e isso só os torna disjuntos se o conjunto de origem
        // não tiver duas semanas seguidas. Se algum dia couberem mais rodadas
        // numa dessas competições, é aqui que o calendário avisa.
        for (origem in listOf(
            CalendarioDaTemporada.SEMANAS_LIBERTADORES,
            CalendarioDaTemporada.SEMANAS_EUROPEIAS
        )) {
            for (i in 0 until origem.size - 1) {
                assertTrue(
                    "semanas ${origem[i]} e ${origem[i + 1]} são seguidas",
                    origem[i + 1] - origem[i] >= 2
                )
            }
        }
        assertEquals(
            emptyList<Int>(),
            CalendarioDaTemporada.SEMANAS_LIBERTADORES
                .intersect(CalendarioDaTemporada.SEMANAS_SULAMERICANA.toSet()).toList()
        )
        assertEquals(
            emptyList<Int>(),
            CalendarioDaTemporada.SEMANAS_EUROPEIAS
                .intersect(CalendarioDaTemporada.SEMANAS_COPA_EUROPEIA.toSet()).toList()
        )
    }

    // ── Pontos corridos ──────────────────────────────────────────────────

    @Test
    fun `uma liga comeca em 8 de fevereiro e termina na ultima semana`() {
        val dias = CalendarioDaTemporada.diasDeLiga(38)
        assertEquals(38, dias.size)
        assertEquals(CalendarioDaTemporada.PRIMEIRO_DIA, dias.first())
        assertEquals(CalendarioDaTemporada.ULTIMO_DIA_DE_LIGA, dias.last())
    }

    @Test
    fun `toda rodada de liga cai num fim de semana da grade`() {
        // A fórmula antiga era 39 + (rodada*10 - 10) * 295 / 370, de resolução
        // 0,8 dia por casa de ordemGlobal — e era essa resolução que colava dois
        // jogos no mesmo dia. A troca pela grade é o conserto, então a tabela
        // antiga não vale mais como referência: o que vale é a casa.
        for (rodadas in listOf(15, 20, 38, 43)) {
            val dias = CalendarioDaTemporada.diasDeLiga(rodadas)
            for (dia in dias) {
                assertEquals(
                    "$rodadas rodadas: o dia $dia não é um fim de semana",
                    CalendarioDaTemporada.FIM_DE_SEMANA,
                    (dia - CalendarioDaTemporada.PRIMEIRO_DIA) % 7
                )
            }
        }
    }

    @Test
    fun `qualquer numero de rodadas cabe na janela`() {
        // O bug que isto conserta: a EFL Championship tem 46 rodadas e usava a
        // curva calibrada para 38. Da 39ª em diante caía depois do fim da
        // janela, e as rodadas 44, 45 e 46 colapsavam as três em 31 de
        // dezembro — três rodadas no mesmo dia.
        for (rodadas in listOf(20, 30, 38, 43, 46, 58)) {
            val dias = CalendarioDaTemporada.diasDeLiga(rodadas)
            assertEquals("$rodadas rodadas", rodadas, dias.size)
            assertEquals("$rodadas rodadas", rodadas, dias.distinct().size)
            assertTrue(
                "$rodadas rodadas: ${dias.last()}",
                dias.last() <= CalendarioDaTemporada.ULTIMO_DIA_DE_LIGA
            )
            assertTrue(
                "$rodadas rodadas: ${dias.first()}",
                dias.first() >= CalendarioDaTemporada.PRIMEIRO_DIA
            )
            assertEquals("$rodadas rodadas", dias.sorted(), dias)
        }
    }

    @Test
    fun `a championship de 46 rodadas nao repete mais nenhum dia`() {
        val dias = CalendarioDasCompeticoes.championship()
        assertEquals(46, dias.distinct().size)
        assertEquals(emptyList<Int>(), CalendarioDaTemporada.diasForaDaJanela(comp("EFL", dias)))
    }

    @Test
    fun `um torneio de faixa anda semana a semana`() {
        val apertura = CalendarioDaTemporada.diasDeFaixa(0, 20)
        assertEquals(20, apertura.size)
        assertEquals(CalendarioDaTemporada.PRIMEIRO_DIA, apertura.first())
        for (i in 0 until apertura.size - 1) {
            assertEquals(7, apertura[i + 1] - apertura[i])
        }
    }

    @Test
    fun `casos degenerados nao estouram`() {
        assertEquals(emptyList<Int>(), CalendarioDaTemporada.diasDeLiga(0))
        assertEquals(emptyList<Int>(), CalendarioDaTemporada.diasDeLiga(-3))
        assertEquals(listOf(CalendarioDaTemporada.PRIMEIRO_DIA), CalendarioDaTemporada.diasDeLiga(1))
        assertEquals(emptyList<Int>(), CalendarioDaTemporada.semanasEspacadas(0))
        assertEquals(emptyList<Int>(), CalendarioDaTemporada.semanasEscolhidas(listOf(1, 2), 0))
        assertEquals(emptyList<Int>(), CalendarioDaTemporada.semanasEscolhidas(emptyList(), 3))
        assertEquals(emptyList<Int>(), CalendarioDaTemporada.diasDeFaixa(0, 0))
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
    fun `a casa de ordemGlobal continua lendo o historico`() {
        // diaDaCasa sobrevive como LEITOR das temporadas gravadas antes da
        // etapa 2. Validado no aparelho: Champions R2 (casa 67) cai em 25 de
        // março.
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
        // 2 dias é exatamente a folga mínima da grade (a última casa de uma
        // semana para a primeira da seguinte). Exigir 3 invalidaria a grade
        // inteira, então o validador tem de aceitar 2.
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
    fun `rodada depois do fim da janela e apontada`() {
        val fora = CalendarioDaTemporada.diasForaDaJanela(
            comp("Liga", listOf(39, 333, 340, 365))
        )
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

