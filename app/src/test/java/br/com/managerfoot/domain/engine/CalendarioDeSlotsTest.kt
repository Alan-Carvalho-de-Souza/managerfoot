package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.CampeonatoEntity
import br.com.managerfoot.data.database.entities.FormatoCampeonato
import br.com.managerfoot.data.database.entities.TipoCampeonato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tradução de slot para dia do ano.
 *
 * O que este teste protege é o **silêncio**: quando uma competição não é
 * reconhecida, `CalendarioDeSlots.dia` cai na curva antiga e devolve uma data
 * plausível. O jogo não quebra, a tela não acusa nada, e o calendário volta a
 * ter as colisões que a etapa 3 tirou. Por isso quase tudo aqui passa por
 * `reconhece`, e não por `dia`.
 */
class CalendarioDeSlotsTest {

    private fun camp(
        nome: String,
        tipo: TipoCampeonato,
        totalRodadas: Int = 38,
        pais: String = "Brasil",
        formato: FormatoCampeonato = FormatoCampeonato.PONTOS_CORRIDOS
    ) = CampeonatoEntity(
        id = 1, temporadaId = 1, nome = nome, tipo = tipo,
        formato = formato, totalRodadas = totalRodadas, pais = pais
    )

    /** Uma competição, os slots que os geradores produzem e os dias esperados. */
    private data class Caso(
        val campeonato: CampeonatoEntity,
        /** (slot, rodada) de cada jogo, na ordem do torneio. */
        val jogos: List<Pair<Int, Int>>,
        val diasEsperados: List<Int>
    )

    private fun ligaDe(nome: String, tipo: TipoCampeonato, rodadas: Int, pais: String) = Caso(
        camp(nome, tipo, rodadas, pais),
        (1..rodadas).map { it * 10 to it },
        CalendarioDaTemporada.diasDeLiga(rodadas)
    )

    private fun casos(): List<Caso> = listOf(
        ligaDe("Brasileiro Série A 2027", TipoCampeonato.NACIONAL_DIVISAO1, 38, "Brasil"),
        ligaDe("Brasileiro Série B 2027", TipoCampeonato.NACIONAL_DIVISAO2, 38, "Brasil"),
        ligaDe("Premier League 2027/28", TipoCampeonato.NACIONAL_DIVISAO1, 38, "Inglaterra"),
        ligaDe("La Liga 2027/28", TipoCampeonato.NACIONAL_DIVISAO1, 38, "Espanha"),
        ligaDe("Serie A 2027/28", TipoCampeonato.NACIONAL_DIVISAO1, 38, "Italia"),
        ligaDe("EFL Championship 2027/28", TipoCampeonato.NACIONAL_DIVISAO2, 46, "Inglaterra"),

        // Turnos: o offset do slot não importa, porque quem indexa é a rodada.
        Caso(
            camp("Primera División Argentina Apertura 2027", TipoCampeonato.EXTRANGEIRO_DIVISAO1, 20, "Argentina"),
            (1..20).map { it * 10 to it },
            CalendarioDasCompeticoes.aperturaArgentino()
        ),
        Caso(
            camp("Primera División Argentina Clausura 2027", TipoCampeonato.EXTRANGEIRO_DIVISAO1, 20, "Argentina"),
            (1..20).map { it * 10 + MotorCampeonato.ARG_CLAUSURA_OG_OFFSET to it },
            CalendarioDasCompeticoes.clausuraArgentino()
        ),
        Caso(
            camp("Torneio Apertura Uruguai 2027", TipoCampeonato.EXTRANGEIRO_DIVISAO1, 15, "Uruguai"),
            (1..15).map { it * 10 to it },
            CalendarioDasCompeticoes.aperturaUruguaio()
        ),
        Caso(
            camp("Torneio Intermediário Uruguai 2027", TipoCampeonato.EXTRANGEIRO_DIVISAO1, 8, "Uruguai"),
            (1..8).map { it * 10 + MotorCampeonato.URU_INTERM_OG_OFFSET to it },
            CalendarioDasCompeticoes.intermediarioUruguaio()
        ),
        // O Clausura uruguaio carrega o playoff do Campeonato nas rodadas 16 e 17.
        Caso(
            camp("Torneio Clausura Uruguai 2027", TipoCampeonato.EXTRANGEIRO_DIVISAO1, 17, "Uruguai"),
            (1..15).map { it * 10 + MotorCampeonato.URU_CLAUSURA_OG_OFFSET to it } +
                listOf(
                    MotorCampeonato.URU_PLAYOFF_OG_SEMI to MotorCampeonato.URU_PLAYOFF_SEMI_RODADA,
                    MotorCampeonato.URU_PLAYOFF_OG_FINAL to MotorCampeonato.URU_PLAYOFF_FINAL_RODADA
                ),
            CalendarioDasCompeticoes.clausuraUruguaio() + CalendarioDasCompeticoes.playoffUruguaio()
        ),

        // Copas nacionais: aqui quem indexa é o slot, não a rodada.
        Caso(
            camp("Copa do Brasil 2027", TipoCampeonato.COPA_NACIONAL, 12, "Brasil", FormatoCampeonato.MATA_MATA_IDA_VOLTA),
            MotorCampeonato.COPA_ORDEM_GLOBAL.mapIndexed { i, og -> og to i + 1 },
            CalendarioDasCompeticoes.copaDoBrasil()
        ),
        Caso(
            camp("Copa Argentina 2027", TipoCampeonato.COPA_NACIONAL, 10, "Argentina", FormatoCampeonato.MATA_MATA_IDA_VOLTA),
            MotorCampeonato.COPA_ARG_ORDEM_GLOBAL.mapIndexed { i, og -> og to i + 1 },
            CalendarioDasCompeticoes.copaArgentina()
        ),
        Caso(
            camp("Copa da Inglaterra 2027", TipoCampeonato.COPA_NACIONAL, 5, "Inglaterra", FormatoCampeonato.MATA_MATA_SIMPLES),
            MotorCampeonato.COPA_ING_ORDEM_GLOBAL.mapIndexed { i, og -> og to i + 1 },
            CalendarioDasCompeticoes.copaDaInglaterra()
        ),
        Caso(
            camp("Copa Espanhola 2027", TipoCampeonato.COPA_NACIONAL, 8, "Espanha", FormatoCampeonato.MATA_MATA_IDA_VOLTA),
            MotorCampeonato.COPA16_ORDEM_GLOBAL.mapIndexed { i, og -> og to i + 1 },
            CalendarioDasCompeticoes.copaDeDezesseis()
        ),
        Caso(
            camp("Copa Italiana 2027", TipoCampeonato.COPA_NACIONAL, 8, "Italia", FormatoCampeonato.MATA_MATA_IDA_VOLTA),
            MotorCampeonato.COPA16_ORDEM_GLOBAL.mapIndexed { i, og -> og to i + 1 },
            CalendarioDasCompeticoes.copaDeDezesseis()
        ),

        // Continentais.
        Caso(
            camp("Champions League 2027", TipoCampeonato.CONTINENTAL, 28, "Europa", FormatoCampeonato.FASE_LIGA_E_MATA_MATA),
            CalendarioDeSlots.slotsDe(MotorCampeonato.FORMATO_CHAMPIONS).mapIndexed { i, og -> og to i + 1 },
            CalendarioDasCompeticoes.champions()
        ),
        Caso(
            camp("Europa League 2027", TipoCampeonato.CONTINENTAL, 48, "Europa", FormatoCampeonato.FASE_LIGA_E_MATA_MATA),
            CalendarioDeSlots.slotsDe(MotorCampeonato.FORMATO_EUROPA_LEAGUE).mapIndexed { i, og -> og to i + 1 },
            CalendarioDasCompeticoes.europaLeague()
        ),
        Caso(
            camp("Libertadores 2027", TipoCampeonato.CONTINENTAL, 27, "América do Sul", FormatoCampeonato.GRUPOS_E_MATA_MATA),
            CalendarioDeSlots.slotsDe(MotorCampeonato.FORMATO_LIBERTADORES).mapIndexed { i, og -> og to i + 1 },
            CalendarioDasCompeticoes.libertadores()
        ),
        Caso(
            camp("Copa Sul-Americana 2027", TipoCampeonato.CONTINENTAL, 29, "América do Sul", FormatoCampeonato.GRUPOS_E_MATA_MATA),
            CalendarioDeSlots.slotsDe(MotorCampeonato.FORMATO_SULAMERICANA).mapIndexed { i, og -> og to i + 1 },
            CalendarioDasCompeticoes.sulAmericana()
        ),
        Caso(
            camp("Recopa Sul-Americana 2027", TipoCampeonato.CONTINENTAL, 2, "América do Sul", FormatoCampeonato.MATA_MATA_IDA_VOLTA),
            listOf(6 to 1, 9 to 2),
            CalendarioDaTemporada.DIAS_RECOPA
        ),

        // Jogos avulsos.
        Caso(
            camp("Supercopa Rei 2027", TipoCampeonato.SUPERCOPA, 1, "Brasil", FormatoCampeonato.MATA_MATA_SIMPLES),
            listOf(1 to 1),
            listOf(CalendarioDaTemporada.DIA_SUPERCOPA_NACIONAL)
        ),
        Caso(
            camp("Supercopa da UEFA 2027", TipoCampeonato.SUPERCOPA, 1, "Europa", FormatoCampeonato.MATA_MATA_SIMPLES),
            listOf(1 to 1),
            listOf(CalendarioDaTemporada.DIA_SUPERCOPA_UEFA)
        ),
        Caso(
            camp("Copa Intercontinental 2027", TipoCampeonato.INTERCONTINENTAL, 1, "Mundo", FormatoCampeonato.MATA_MATA_SIMPLES),
            listOf(440 to 1),
            listOf(CalendarioDaTemporada.DIA_INTERCONTINENTAL)
        ),
        Caso(
            camp("Troféu dos Campeões 2027", TipoCampeonato.TROFEU_CAMPEOES, 1, "Argentina", FormatoCampeonato.MATA_MATA_SIMPLES),
            listOf(620 to 1),
            listOf(CalendarioDaTemporada.DIA_TROFEU)
        )
    )

    // ── A afirmação central ──────────────────────────────────────────────

    @Test
    fun `toda competicao traduz os seus slots para os dias da grade`() {
        for (caso in casos()) {
            val nome = caso.campeonato.nome
            assertEquals(
                "$nome: slots e dias de tamanhos diferentes",
                caso.jogos.size,
                caso.diasEsperados.size
            )
            caso.jogos.forEachIndexed { i, (slot, rodada) ->
                assertTrue(
                    "$nome: slot $slot não é reconhecido, cairia na curva antiga",
                    CalendarioDeSlots.reconhece(caso.campeonato, slot, rodada)
                )
                assertEquals(
                    "$nome: slot $slot (rodada $rodada)",
                    caso.diasEsperados[i],
                    CalendarioDeSlots.dia(caso.campeonato, slot, rodada)
                )
            }
        }
    }

    @Test
    fun `a traducao preserva a ordem do torneio`() {
        // É o que sustenta tudo que depende de ordemGlobal: buscarProximaPartida,
        // a moral pelos últimos resultados, a ordem do histórico de carreira.
        // Se a tradução embaralhasse, o jogo apontaria para uma partida que a
        // tela mostra no passado.
        for (caso in casos()) {
            val dias = caso.jogos.map { (slot, rodada) ->
                CalendarioDeSlots.dia(caso.campeonato, slot, rodada)
            }
            assertEquals("${caso.campeonato.nome} embaralhou", dias.sorted(), dias)
        }
    }

    @Test
    fun `nenhuma competicao coloca dois jogos no mesmo dia`() {
        for (caso in casos()) {
            val dias = caso.jogos.map { (slot, rodada) ->
                CalendarioDeSlots.dia(caso.campeonato, slot, rodada)
            }
            assertEquals("${caso.campeonato.nome}", dias.size, dias.distinct().size)
        }
    }

    @Test
    fun `todo dia traduzido cabe no ano`() {
        for (caso in casos()) {
            for ((slot, rodada) in caso.jogos) {
                val dia = CalendarioDeSlots.dia(caso.campeonato, slot, rodada)
                assertTrue("${caso.campeonato.nome}: slot $slot virou dia $dia", dia in 1..365)
            }
        }
    }

    // ── Amistosos ────────────────────────────────────────────────────────

    @Test
    fun `os amistosos sao indexados pelo slot, nao pela rodada`() {
        // O container de amistosos só é limpo no encerramento da temporada, então
        // os dois de dezembro nascem com rodada 3 e 4 — e com rodada 1 e 2 se o
        // jogador tiver ignorado os de janeiro. Em nenhum dos casos a rodada diz
        // qual é o dia.
        val amistosos = camp("Amistosos 2027", TipoCampeonato.AMISTOSO, 4, "Brasil", FormatoCampeonato.MATA_MATA_SIMPLES)
        val dias = CalendarioDaTemporada.DIAS_AMISTOSOS

        // Janeiro: base 5, rodadas 1 e 2.
        assertEquals(dias[0], CalendarioDeSlots.dia(amistosos, 6, 1))
        assertEquals(dias[1], CalendarioDeSlots.dia(amistosos, 7, 2))

        // Dezembro depois de janeiro: base 450, rodadas 3 e 4.
        assertEquals(dias[2], CalendarioDeSlots.dia(amistosos, 453, 3))
        assertEquals(dias[3], CalendarioDeSlots.dia(amistosos, 454, 4))

        // Dezembro sem janeiro: base 450, rodadas 1 e 2 — ainda dezembro.
        assertEquals(dias[2], CalendarioDeSlots.dia(amistosos, 451, 1))
        assertEquals(dias[3], CalendarioDeSlots.dia(amistosos, 452, 2))
    }

    @Test
    fun `os quatro amistosos de uma temporada caem em quatro dias`() {
        val amistosos = camp("Amistosos 2027", TipoCampeonato.AMISTOSO, 4, "Brasil", FormatoCampeonato.MATA_MATA_SIMPLES)
        val dias = listOf(6 to 1, 7 to 2, 453 to 3, 454 to 4).map { (slot, rodada) ->
            CalendarioDeSlots.dia(amistosos, slot, rodada)
        }
        assertEquals(4, dias.distinct().size)
        assertEquals(dias.sorted(), dias)
    }

    // ── Os nomes, que são o que seleciona a tradução ─────────────────────

    @Test
    fun `os nomes do rollover sao os que a traducao reconhece`() {
        // Se o rollover mudar um destes nomes, a competição deixa de ser
        // reconhecida e volta à curva antiga EM SILÊNCIO. Este teste é o alarme.
        assertTrue(CalendarioDeSlots.ehArgentinaApertura("Primera División Argentina Apertura 2027"))
        assertTrue(CalendarioDeSlots.ehArgentinaClausura("Primera División Argentina Clausura 2027"))
        assertTrue(CalendarioDeSlots.ehUruguaiApertura("Torneio Apertura Uruguai 2027"))
        assertTrue(CalendarioDeSlots.ehUruguaiClausura("Torneio Clausura Uruguai 2027"))
        assertTrue(CalendarioDeSlots.ehUruguaiIntermediario("Torneio Intermediário Uruguai 2027"))

        // E não se confundem entre si: o uruguaio tem Apertura e Clausura com os
        // mesmos nomes de fase que o argentino.
        assertTrue(!CalendarioDeSlots.ehArgentinaApertura("Torneio Apertura Uruguai 2027"))
        assertTrue(!CalendarioDeSlots.ehUruguaiApertura("Primera División Argentina Apertura 2027"))
        assertTrue(!CalendarioDeSlots.ehArgentinaApertura("Primera División Argentina Clausura 2027"))
    }

    @Test
    fun `uma competicao desconhecida cai na curva antiga, e isso e detectavel`() {
        val estranho = camp("Copa de Marte 2027", TipoCampeonato.COPA_NACIONAL, 4, "Marte", FormatoCampeonato.MATA_MATA_SIMPLES)
        assertTrue(!CalendarioDeSlots.reconhece(estranho, 100, 1))
        // Mas ainda devolve um dia utilizável, em vez de quebrar o jogo.
        assertEquals(CalendarioDaTemporada.diaDaCasa(100, estranho.nome), CalendarioDeSlots.dia(estranho, 100, 1))
    }

    @Test
    fun `um slot fora da tabela de uma copa nao e reconhecido`() {
        // Um slot que não está na tabela significa que o gerador e esta tradução
        // discordam — melhor acusar do que inventar um dia.
        val copa = camp("Copa do Brasil 2027", TipoCampeonato.COPA_NACIONAL, 12, "Brasil", FormatoCampeonato.MATA_MATA_IDA_VOLTA)
        assertTrue(!CalendarioDeSlots.reconhece(copa, 999, 1))
    }
}
