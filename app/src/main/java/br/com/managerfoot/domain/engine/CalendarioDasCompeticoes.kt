package br.com.managerfoot.domain.engine

import br.com.managerfoot.domain.engine.CalendarioDaTemporada.DIAS_AMISTOSOS
import br.com.managerfoot.domain.engine.CalendarioDaTemporada.DIAS_RECOPA
import br.com.managerfoot.domain.engine.CalendarioDaTemporada.DIA_INTERCONTINENTAL
import br.com.managerfoot.domain.engine.CalendarioDaTemporada.DIA_SUPERCOPA_NACIONAL
import br.com.managerfoot.domain.engine.CalendarioDaTemporada.DIA_SUPERCOPA_UEFA
import br.com.managerfoot.domain.engine.CalendarioDaTemporada.DIA_TROFEU
import br.com.managerfoot.domain.engine.CalendarioDaTemporada.MEIO_DE_SEMANA_1
import br.com.managerfoot.domain.engine.CalendarioDaTemporada.MEIO_DE_SEMANA_2
import br.com.managerfoot.domain.engine.CalendarioDaTemporada.diasDeFaixa
import br.com.managerfoot.domain.engine.CalendarioDaTemporada.diasDeLiga
import br.com.managerfoot.domain.engine.CalendarioDaTemporada.diasDeMeioDeSemana
import br.com.managerfoot.domain.engine.CalendarioDaTemporada.semanasEscolhidas
import br.com.managerfoot.domain.engine.CompeticaoNoCalendario.Companion.INTERNACIONAL

// ─────────────────────────────────────────────────────────────────────────────
// O calendário de cada competição do jogo, na grade de CalendarioDaTemporada.
//
// É a tabela que faltava: hoje ninguém consegue perguntar "que dia é a 3ª
// rodada da Libertadores?" sem refazer a conversão de `ordemGlobal`, e por isso
// ninguém conseguia descobrir que 22 pares de jogos caíam no mesmo dia.
//
// Aqui cada competição diz os seus dias, e [PERFIS] diz quais delas um mesmo
// clube pode disputar. O teste cruza as duas coisas e exige zero conflito — é
// esse cruzamento que torna o calendário uma afirmação verificável, e não uma
// intenção escrita num comentário.
//
// A etapa 2 (ver docs/calendario-da-temporada.md) é o que liga esta tabela aos
// geradores de partida. Até lá ela é só verdade declarada e testada.
// ─────────────────────────────────────────────────────────────────────────────
object CalendarioDasCompeticoes {

    // ── Pontos corridos ──────────────────────────────────────────────────

    /** Ligas de 38 rodadas: Brasileirão, Premier League, La Liga, Serie A. */
    const val RODADAS_LIGA_PADRAO = 38

    /** EFL Championship: 24 clubes, 46 rodadas. */
    const val RODADAS_CHAMPIONSHIP = 46

    /** Rodadas de um torneio argentino (Apertura ou Clausura). */
    const val RODADAS_TORNEIO_ARGENTINO = 20

    /** Primeira semana do Clausura argentino: deixa a semana 20 de intervalo. */
    const val PRIMEIRA_SEMANA_CLAUSURA_ARG = 21

    fun ligaPadrao(): List<Int> = diasDeLiga(RODADAS_LIGA_PADRAO)

    fun championship(): List<Int> = diasDeLiga(RODADAS_CHAMPIONSHIP)

    fun aperturaArgentino(): List<Int> = diasDeFaixa(0, RODADAS_TORNEIO_ARGENTINO)

    fun clausuraArgentino(): List<Int> =
        diasDeFaixa(PRIMEIRA_SEMANA_CLAUSURA_ARG, RODADAS_TORNEIO_ARGENTINO)

    /**
     * O calendário uruguaio, em quatro trechos encadeados: Apertura (15),
     * Intermediário (7 de grupo + a final), Clausura (15) e o playoff do
     * Campeonato (semi e final). São 40 fins de semana das 43 da grade.
     */
    fun aperturaUruguaio(): List<Int> = diasDeFaixa(0, 15)
    fun intermediarioUruguaio(): List<Int> = diasDeFaixa(15, 8)
    fun clausuraUruguaio(): List<Int> = diasDeFaixa(23, 15)
    fun playoffUruguaio(): List<Int> = diasDeFaixa(38, 2)

    // ── Copas nacionais ──────────────────────────────────────────────────

    /** Copa do Brasil: 6 fases ida e volta, 12 jogos. */
    fun copaDoBrasil(): List<Int> =
        diasDeMeioDeSemana(CalendarioDaTemporada.SEMANAS_COPA_DO_BRASIL, MEIO_DE_SEMANA_1)

    /** Copa Argentina: 5 fases ida e volta, 10 jogos. */
    fun copaArgentina(): List<Int> =
        diasDeMeioDeSemana(CalendarioDaTemporada.SEMANAS_COPA_ARGENTINA, MEIO_DE_SEMANA_1)

    /** Copa da Inglaterra: 5 fases de jogo único. */
    fun copaDaInglaterra(): List<Int> = diasDeMeioDeSemana(
        semanasEscolhidas(CalendarioDaTemporada.SEMANAS_COPA_EUROPEIA, 5), MEIO_DE_SEMANA_2
    )

    /**
     * Copa do Rei e Coppa Italia: 4 fases ida e volta, 8 jogos.
     *
     * As duas dividem os mesmos dias de propósito — um clube disputa a copa do
     * seu próprio país, então Espanha e Itália nunca se encontram num perfil.
     */
    fun copaDeDezesseis(): List<Int> = diasDeMeioDeSemana(
        semanasEscolhidas(CalendarioDaTemporada.SEMANAS_COPA_EUROPEIA, 8), MEIO_DE_SEMANA_2
    )

    // ── Continentais ─────────────────────────────────────────────────────

    /** Champions League: 8 da Fase de Liga + 2 do play-off + 8 do mata-mata. */
    fun champions(): List<Int> =
        diasDeMeioDeSemana(CalendarioDaTemporada.SEMANAS_EUROPEIAS, MEIO_DE_SEMANA_1)

    /** Europa League: as mesmas semanas da Champions, na outra casa. */
    fun europaLeague(): List<Int> =
        diasDeMeioDeSemana(CalendarioDaTemporada.SEMANAS_EUROPEIAS, MEIO_DE_SEMANA_2)

    /** Libertadores: 6 da Pré + 6 dos grupos + 7 do mata-mata. */
    fun libertadores(): List<Int> =
        diasDeMeioDeSemana(CalendarioDaTemporada.SEMANAS_LIBERTADORES, MEIO_DE_SEMANA_2)

    /** Copa Sul-Americana: 1 da Primeira Fase + 6 dos grupos + 9 do mata-mata. */
    fun sulAmericana(): List<Int> =
        diasDeMeioDeSemana(CalendarioDaTemporada.SEMANAS_SULAMERICANA, MEIO_DE_SEMANA_2)

    // ── Jogos avulsos ────────────────────────────────────────────────────

    fun supercopaNacional(): List<Int> = listOf(DIA_SUPERCOPA_NACIONAL)
    fun supercopaDaUefa(): List<Int> = listOf(DIA_SUPERCOPA_UEFA)
    fun recopa(): List<Int> = DIAS_RECOPA
    fun intercontinental(): List<Int> = listOf(DIA_INTERCONTINENTAL)
    fun trofeuDosCampeoes(): List<Int> = listOf(DIA_TROFEU)
    fun amistosos(): List<Int> = DIAS_AMISTOSOS

    // ── As competições, por chave ────────────────────────────────────────

    const val BRASILEIRAO = "Brasileirão"
    const val COPA_DO_BRASIL = "Copa do Brasil"
    const val PREMIER = "Premier League"
    const val CHAMPIONSHIP = "EFL Championship"
    const val COPA_DA_INGLATERRA = "Copa da Inglaterra"
    const val LA_LIGA = "La Liga"
    const val COPA_DO_REI = "Copa do Rei"
    const val SERIE_A_ITALIANA = "Serie A"
    const val COPPA_ITALIA = "Coppa Italia"
    const val APERTURA_ARG = "Apertura (ARG)"
    const val CLAUSURA_ARG = "Clausura (ARG)"
    const val COPA_ARGENTINA = "Copa Argentina"
    const val APERTURA_URU = "Apertura (URU)"
    const val INTERMEDIARIO_URU = "Intermediário (URU)"
    const val CLAUSURA_URU = "Clausura (URU)"
    const val PLAYOFF_URU = "Playoff (URU)"
    const val SUPERCOPA_NACIONAL = "Supercopa nacional"
    const val SUPERCOPA_UEFA = "Supercopa da UEFA"
    const val CHAMPIONS = "Champions League"
    const val EUROPA_LEAGUE = "Europa League"
    const val LIBERTADORES = "Libertadores"
    const val SULAMERICANA = "Copa Sul-Americana"
    const val RECOPA = "Recopa Sul-Americana"
    const val INTERCONTINENTAL = "Copa Intercontinental"
    const val TROFEU = "Troféu dos Campeões"
    const val AMISTOSOS = "Amistosos"

    const val BRASIL = "Brasil"
    const val INGLATERRA = "Inglaterra"
    const val ESPANHA = "Espanha"
    const val ITALIA = "Italia"
    const val ARGENTINA = "Argentina"
    const val URUGUAI = "Uruguai"

    /** Toda competição do jogo e os dias que ela ocupa. */
    val TODAS: Map<String, CompeticaoNoCalendario> = listOf(
        CompeticaoNoCalendario(BRASILEIRAO, BRASIL, ligaPadrao()),
        CompeticaoNoCalendario(COPA_DO_BRASIL, BRASIL, copaDoBrasil()),
        CompeticaoNoCalendario(PREMIER, INGLATERRA, ligaPadrao()),
        CompeticaoNoCalendario(CHAMPIONSHIP, INGLATERRA, championship()),
        CompeticaoNoCalendario(COPA_DA_INGLATERRA, INGLATERRA, copaDaInglaterra()),
        CompeticaoNoCalendario(LA_LIGA, ESPANHA, ligaPadrao()),
        CompeticaoNoCalendario(COPA_DO_REI, ESPANHA, copaDeDezesseis()),
        CompeticaoNoCalendario(SERIE_A_ITALIANA, ITALIA, ligaPadrao()),
        CompeticaoNoCalendario(COPPA_ITALIA, ITALIA, copaDeDezesseis()),
        CompeticaoNoCalendario(APERTURA_ARG, ARGENTINA, aperturaArgentino()),
        CompeticaoNoCalendario(CLAUSURA_ARG, ARGENTINA, clausuraArgentino()),
        CompeticaoNoCalendario(COPA_ARGENTINA, ARGENTINA, copaArgentina()),
        CompeticaoNoCalendario(APERTURA_URU, URUGUAI, aperturaUruguaio()),
        CompeticaoNoCalendario(INTERMEDIARIO_URU, URUGUAI, intermediarioUruguaio()),
        CompeticaoNoCalendario(CLAUSURA_URU, URUGUAI, clausuraUruguaio()),
        CompeticaoNoCalendario(PLAYOFF_URU, URUGUAI, playoffUruguaio()),
        // A supercopa nacional é uma por país, mas todas no mesmo dia: um clube
        // só disputa a do seu país, então uma entrada só basta para a validação.
        CompeticaoNoCalendario(SUPERCOPA_NACIONAL, INTERNACIONAL, supercopaNacional()),
        CompeticaoNoCalendario(SUPERCOPA_UEFA, INTERNACIONAL, supercopaDaUefa()),
        CompeticaoNoCalendario(CHAMPIONS, INTERNACIONAL, champions()),
        CompeticaoNoCalendario(EUROPA_LEAGUE, INTERNACIONAL, europaLeague()),
        CompeticaoNoCalendario(LIBERTADORES, INTERNACIONAL, libertadores()),
        CompeticaoNoCalendario(SULAMERICANA, INTERNACIONAL, sulAmericana()),
        CompeticaoNoCalendario(RECOPA, INTERNACIONAL, recopa()),
        CompeticaoNoCalendario(INTERCONTINENTAL, INTERNACIONAL, intercontinental()),
        CompeticaoNoCalendario(TROFEU, INTERNACIONAL, trofeuDosCampeoes()),
        CompeticaoNoCalendario(AMISTOSOS, INTERNACIONAL, amistosos())
    ).associateBy { it.chave }

    /**
     * Os perfis de clube: os conjuntos de competições que um MESMO clube pode
     * disputar na mesma temporada.
     *
     * É a unidade de validação, porque o calendário não precisa ser livre de
     * colisões no mundo todo — ele precisa ser livre de colisões para cada
     * elenco. Um jogo do Brasileirão e um da Premier no mesmo dia não incomodam
     * ninguém; um da Série A e um da Libertadores incomodam o Palmeiras.
     *
     * Os amistosos entram em todos porque o clube do usuário sempre os joga.
     */
    val PERFIS: Map<String, List<String>> = linkedMapOf(
        "Brasil + Libertadores" to listOf(
            BRASILEIRAO, COPA_DO_BRASIL, SUPERCOPA_NACIONAL, LIBERTADORES,
            RECOPA, INTERCONTINENTAL, TROFEU, AMISTOSOS
        ),
        "Brasil + Sul-Americana" to listOf(
            BRASILEIRAO, COPA_DO_BRASIL, SUPERCOPA_NACIONAL, SULAMERICANA, RECOPA, AMISTOSOS
        ),
        // O eliminado da Fase 3 da Pré-Libertadores entra nos grupos da
        // Sul-Americana: é o perfil que joga as duas na mesma temporada.
        "Brasil + Pré-Libertadores + Sul-Americana" to listOf(
            BRASILEIRAO, COPA_DO_BRASIL, LIBERTADORES, SULAMERICANA, AMISTOSOS
        ),
        "Inglaterra + Champions" to listOf(
            PREMIER, COPA_DA_INGLATERRA, SUPERCOPA_NACIONAL, CHAMPIONS,
            SUPERCOPA_UEFA, INTERCONTINENTAL, TROFEU, AMISTOSOS
        ),
        "Inglaterra + Europa League" to listOf(
            PREMIER, COPA_DA_INGLATERRA, SUPERCOPA_NACIONAL, EUROPA_LEAGUE,
            SUPERCOPA_UEFA, AMISTOSOS
        ),
        "Inglaterra (Championship)" to listOf(CHAMPIONSHIP, COPA_DA_INGLATERRA, AMISTOSOS),
        "Espanha + Champions" to listOf(
            LA_LIGA, COPA_DO_REI, SUPERCOPA_NACIONAL, CHAMPIONS,
            SUPERCOPA_UEFA, INTERCONTINENTAL, AMISTOSOS
        ),
        "Espanha + Europa League" to listOf(
            LA_LIGA, COPA_DO_REI, SUPERCOPA_NACIONAL, EUROPA_LEAGUE, SUPERCOPA_UEFA, AMISTOSOS
        ),
        "Itália + Champions" to listOf(
            SERIE_A_ITALIANA, COPPA_ITALIA, SUPERCOPA_NACIONAL, CHAMPIONS,
            SUPERCOPA_UEFA, INTERCONTINENTAL, AMISTOSOS
        ),
        "Itália + Europa League" to listOf(
            SERIE_A_ITALIANA, COPPA_ITALIA, SUPERCOPA_NACIONAL, EUROPA_LEAGUE,
            SUPERCOPA_UEFA, AMISTOSOS
        ),
        "Argentina + Libertadores" to listOf(
            APERTURA_ARG, CLAUSURA_ARG, COPA_ARGENTINA, LIBERTADORES, RECOPA, AMISTOSOS
        ),
        "Argentina + Sul-Americana" to listOf(
            APERTURA_ARG, CLAUSURA_ARG, COPA_ARGENTINA, SULAMERICANA, AMISTOSOS
        ),
        "Uruguai + Libertadores" to listOf(
            APERTURA_URU, INTERMEDIARIO_URU, CLAUSURA_URU, PLAYOFF_URU, LIBERTADORES, AMISTOSOS
        ),
        "Uruguai + Sul-Americana" to listOf(
            APERTURA_URU, INTERMEDIARIO_URU, CLAUSURA_URU, PLAYOFF_URU, SULAMERICANA, AMISTOSOS
        )
    )

    /** As competições de um perfil, prontas para [CalendarioDaTemporada.conflitosDoPerfil]. */
    fun competicoesDoPerfil(perfil: String): List<CompeticaoNoCalendario> =
        (PERFIS[perfil] ?: emptyList()).mapNotNull { TODAS[it] }
}
