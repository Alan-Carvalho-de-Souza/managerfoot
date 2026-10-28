package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O calendário real do jogo, julgado pelo validador.
 *
 * Este é o teste que dá sentido à etapa 3. Enumerar o calendário pela curva
 * antiga acusava 22 colisões de mesmo dia e 41 de dias seguidos nos perfis
 * abaixo; a grade semanal as tira, e é aqui que isso deixa de ser uma afirmação
 * e passa a ser uma verificação.
 */
class CalendarioDasCompeticoesTest {

    // ── A afirmação central ──────────────────────────────────────────────

    @Test
    fun `nenhum perfil de clube tem conflito de calendario`() {
        val acusacoes = mutableListOf<String>()
        for (perfil in CalendarioDasCompeticoes.PERFIS.keys) {
            val conflitos = CalendarioDaTemporada.conflitosDoPerfil(
                CalendarioDasCompeticoes.competicoesDoPerfil(perfil)
            )
            for (c in conflitos) {
                acusacoes += "$perfil: dia ${c.dia} ${c.tipo} ${c.competicaoA} × ${c.competicaoB}"
            }
        }
        assertEquals(emptyList<String>(), acusacoes)
    }

    @Test
    fun `os conflitos que existiam ficaram todos resolvidos`() {
        // Os casos concretos que a enumeração achou, cada um nomeado para que a
        // regressão diga QUAL defeito voltou, e não só que algo voltou.
        val conflitosConhecidos = listOf(
            // As cinco supercopas nasciam todas em ordemGlobal = 1, e o campeão
            // da Champions quase sempre é campeão da liga: o mesmo clube jogava
            // a supercopa nacional e a da UEFA no mesmo dia.
            Triple(
                "Espanha + Champions",
                CalendarioDasCompeticoes.SUPERCOPA_NACIONAL,
                CalendarioDasCompeticoes.SUPERCOPA_UEFA
            ),
            // A final da Copa do Brasil e a da Libertadores caíam ambas em
            // dezembro, colando uma na outra.
            Triple(
                "Brasil + Libertadores",
                CalendarioDasCompeticoes.COPA_DO_BRASIL,
                CalendarioDasCompeticoes.LIBERTADORES
            ),
            // A Europa League é a Champions + 1 em ordemGlobal, e 1 casa valia
            // menos de um dia na curva: ela colidia com a liga nacional.
            Triple(
                "Espanha + Europa League",
                CalendarioDasCompeticoes.EUROPA_LEAGUE,
                CalendarioDasCompeticoes.LA_LIGA
            ),
            Triple(
                "Espanha + Europa League",
                CalendarioDasCompeticoes.EUROPA_LEAGUE,
                CalendarioDasCompeticoes.COPA_DO_REI
            ),
            // O Brasileirão e a Libertadores dividiam três dias em fevereiro.
            Triple(
                "Brasil + Libertadores",
                CalendarioDasCompeticoes.BRASILEIRAO,
                CalendarioDasCompeticoes.LIBERTADORES
            ),
            // O argentino era o pior perfil: 5 colisões de mesmo dia.
            Triple(
                "Argentina + Libertadores",
                CalendarioDasCompeticoes.APERTURA_ARG,
                CalendarioDasCompeticoes.LIBERTADORES
            ),
            Triple(
                "Argentina + Libertadores",
                CalendarioDasCompeticoes.COPA_ARGENTINA,
                CalendarioDasCompeticoes.LIBERTADORES
            ),
            // A Recopa (casas 6 e 9) caía na véspera da 1ª rodada da liga.
            Triple(
                "Brasil + Libertadores",
                CalendarioDasCompeticoes.RECOPA,
                CalendarioDasCompeticoes.BRASILEIRAO
            )
        )
        for ((perfil, a, b) in conflitosConhecidos) {
            val conflitos = CalendarioDaTemporada.conflitosDoPerfil(
                CalendarioDasCompeticoes.competicoesDoPerfil(perfil)
            ).filter {
                (it.competicaoA == a && it.competicaoB == b) ||
                    (it.competicaoA == b && it.competicaoB == a)
            }
            assertEquals("$perfil: $a × $b", emptyList<ConflitoDeCalendario>(), conflitos)
        }
    }

    @Test
    fun `os quatro amistosos caem em quatro dias diferentes`() {
        // Pela curva antiga os dois de dezembro caíam os dois em 20 de dezembro
        // e os dois de janeiro em 15 de janeiro.
        val dias = CalendarioDasCompeticoes.amistosos()
        assertEquals(4, dias.size)
        assertEquals(4, dias.distinct().size)
        for (i in 0 until dias.size - 1) {
            assertTrue("${dias[i]} e ${dias[i + 1]}", dias[i + 1] - dias[i] >= 2)
        }
    }

    // ── Os tamanhos, que são o que a alocação consome ────────────────────

    @Test
    fun `cada competicao tem o numero de jogos que o formato pede`() {
        val esperado = mapOf(
            CalendarioDasCompeticoes.BRASILEIRAO to 38,
            CalendarioDasCompeticoes.PREMIER to 38,
            CalendarioDasCompeticoes.LA_LIGA to 38,
            CalendarioDasCompeticoes.SERIE_A_ITALIANA to 38,
            CalendarioDasCompeticoes.CHAMPIONSHIP to 46,
            // 6 fases ida e volta.
            CalendarioDasCompeticoes.COPA_DO_BRASIL to 12,
            // 5 fases ida e volta.
            CalendarioDasCompeticoes.COPA_ARGENTINA to 10,
            // 5 fases de jogo único.
            CalendarioDasCompeticoes.COPA_DA_INGLATERRA to 5,
            // 4 fases ida e volta, das Oitavas em diante.
            CalendarioDasCompeticoes.COPA_DO_REI to 8,
            CalendarioDasCompeticoes.COPPA_ITALIA to 8,
            // 8 da Fase de Liga + 2 do play-off + 8 do mata-mata.
            CalendarioDasCompeticoes.CHAMPIONS to 18,
            CalendarioDasCompeticoes.EUROPA_LEAGUE to 18,
            // 6 da Pré + 6 dos grupos + 7 do mata-mata (final de jogo único).
            CalendarioDasCompeticoes.LIBERTADORES to 19,
            // 1 da Primeira Fase + 6 dos grupos + 9 do mata-mata.
            CalendarioDasCompeticoes.SULAMERICANA to 16,
            CalendarioDasCompeticoes.APERTURA_ARG to 20,
            CalendarioDasCompeticoes.CLAUSURA_ARG to 20,
            CalendarioDasCompeticoes.APERTURA_URU to 15,
            CalendarioDasCompeticoes.INTERMEDIARIO_URU to 8,
            CalendarioDasCompeticoes.CLAUSURA_URU to 15,
            CalendarioDasCompeticoes.PLAYOFF_URU to 2,
            CalendarioDasCompeticoes.RECOPA to 2,
            CalendarioDasCompeticoes.SUPERCOPA_NACIONAL to 1,
            CalendarioDasCompeticoes.SUPERCOPA_UEFA to 1,
            CalendarioDasCompeticoes.INTERCONTINENTAL to 1,
            CalendarioDasCompeticoes.TROFEU to 1,
            CalendarioDasCompeticoes.AMISTOSOS to 4
        )
        for ((chave, jogos) in esperado) {
            val comp = CalendarioDasCompeticoes.TODAS[chave]
            assertEquals("$chave não está na tabela", true, comp != null)
            assertEquals(chave, jogos, comp!!.dias.size)
        }
        assertEquals(esperado.size, CalendarioDasCompeticoes.TODAS.size)
    }

    @Test
    fun `nenhuma competicao repete um dia`() {
        for ((chave, comp) in CalendarioDasCompeticoes.TODAS) {
            assertEquals(chave, comp.dias.size, comp.dias.distinct().size)
            assertEquals(chave, comp.dias.sorted(), comp.dias)
        }
    }

    @Test
    fun `os turnos argentinos e uruguaios nao se sobrepoem`() {
        // Apertura e Clausura são torneios distintos no mesmo ano: as faixas de
        // semana são o que os separa, e uma sobreposição aqui significaria dois
        // jogos de pontos corridos no mesmo fim de semana.
        val arg = CalendarioDasCompeticoes.aperturaArgentino()
            .intersect(CalendarioDasCompeticoes.clausuraArgentino().toSet())
        assertEquals(emptySet<Int>(), arg)

        val uru = listOf(
            CalendarioDasCompeticoes.aperturaUruguaio(),
            CalendarioDasCompeticoes.intermediarioUruguaio(),
            CalendarioDasCompeticoes.clausuraUruguaio(),
            CalendarioDasCompeticoes.playoffUruguaio()
        )
        val todos = uru.flatten()
        assertEquals(todos.size, todos.distinct().size)
        // E cabem nas 43 semanas da grade.
        assertTrue(
            "o uruguaio termina em ${todos.max()}",
            todos.max() <= CalendarioDaTemporada.ULTIMO_DIA_DE_LIGA
        )
    }

    @Test
    fun `todo perfil aponta para competicoes que existem`() {
        for ((perfil, chaves) in CalendarioDasCompeticoes.PERFIS) {
            for (chave in chaves) {
                assertEquals(
                    "$perfil aponta para \"$chave\", que não está na tabela",
                    true,
                    CalendarioDasCompeticoes.TODAS.containsKey(chave)
                )
            }
            assertEquals(
                chaves.size,
                CalendarioDasCompeticoes.competicoesDoPerfil(perfil).size
            )
        }
    }

    @Test
    fun `um perfil desconhecido nao estoura`() {
        assertEquals(
            emptyList<CompeticaoNoCalendario>(),
            CalendarioDasCompeticoes.competicoesDoPerfil("Marte + Champions")
        )
    }

    // ── A janela ─────────────────────────────────────────────────────────

    /** As competições de pontos corridos, que a janela da liga julga. */
    private val pontosCorridos = setOf(
        CalendarioDasCompeticoes.BRASILEIRAO,
        CalendarioDasCompeticoes.PREMIER,
        CalendarioDasCompeticoes.CHAMPIONSHIP,
        CalendarioDasCompeticoes.LA_LIGA,
        CalendarioDasCompeticoes.SERIE_A_ITALIANA,
        CalendarioDasCompeticoes.APERTURA_ARG,
        CalendarioDasCompeticoes.CLAUSURA_ARG,
        CalendarioDasCompeticoes.APERTURA_URU,
        CalendarioDasCompeticoes.INTERMEDIARIO_URU,
        CalendarioDasCompeticoes.CLAUSURA_URU,
        CalendarioDasCompeticoes.PLAYOFF_URU
    )

    /** As competições de entressafra, que jogam fora da temporada de propósito. */
    private val entressafra = setOf(
        CalendarioDasCompeticoes.SUPERCOPA_NACIONAL,
        CalendarioDasCompeticoes.SUPERCOPA_UEFA,
        CalendarioDasCompeticoes.RECOPA,
        CalendarioDasCompeticoes.INTERCONTINENTAL,
        CalendarioDasCompeticoes.TROFEU,
        CalendarioDasCompeticoes.AMISTOSOS
    )

    @Test
    fun `nenhum ponto corrido joga depois do fim da liga`() {
        // É a regra que a Championship violava: as rodadas 44, 45 e 46 caíam
        // depois do fim da janela, as três em 31 de dezembro. E é por isso que o
        // excedente de uma liga longa para na penúltima semana — o meio de
        // semana da última já está do lado de fora.
        for (chave in pontosCorridos) {
            val comp = CalendarioDasCompeticoes.TODAS.getValue(chave)
            assertEquals(
                "$chave vazou da janela",
                emptyList<Int>(),
                CalendarioDaTemporada.diasForaDaJanela(comp)
            )
        }
    }

    @Test
    fun `os jogos de entressafra ficam inteiramente fora da janela`() {
        for (chave in entressafra) {
            val comp = CalendarioDasCompeticoes.TODAS.getValue(chave)
            assertEquals(
                "$chave deveria estar fora da janela",
                comp.dias,
                CalendarioDaTemporada.diasForaDaJanela(comp)
            )
        }
    }

    @Test
    fun `as copas e continentais cabem na grade, e podem decidir em dezembro`() {
        // Elas NÃO são julgadas pela janela da liga: a final da Libertadores e a
        // da Copa do Brasil jogam depois do último ponto corrido, de propósito.
        // O que elas devem respeitar é a grade.
        val deMeioDeSemana = CalendarioDasCompeticoes.TODAS.keys - pontosCorridos - entressafra
        assertTrue("nenhuma competição de meio de semana na tabela", deMeioDeSemana.isNotEmpty())
        var algumaDecideEmDezembro = false
        for (chave in deMeioDeSemana) {
            val dias = CalendarioDasCompeticoes.TODAS.getValue(chave).dias
            assertTrue("$chave começa em ${dias.first()}", dias.first() >= CalendarioDaTemporada.PRIMEIRO_DIA)
            assertTrue(
                "$chave termina em ${dias.last()}, depois da grade",
                dias.last() <= CalendarioDaTemporada.ULTIMO_DIA_DA_GRADE
            )
            if (dias.last() > CalendarioDaTemporada.ULTIMO_DIA_DE_LIGA) algumaDecideEmDezembro = true
        }
        assertTrue("nenhuma final caiu depois do fim da liga", algumaDecideEmDezembro)
    }
}
