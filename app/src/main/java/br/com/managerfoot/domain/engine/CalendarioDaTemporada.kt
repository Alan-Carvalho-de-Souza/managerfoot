package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// O calendário da temporada como DADO: que dia do ano cada rodada de cada
// competição ocupa.
//
// Hoje essa informação existe, mas espalhada e implícita: 16 `intArrayOf` de
// `ordemGlobal` em MotorCampeonato, mais uma dúzia de constantes soltas, e a
// data só aparece no fim, derivada por uma curva na hora de exibir. Nada
// consegue perguntar "que dia é a 3ª rodada da Libertadores?" sem refazer a
// conversão, e ninguém consegue perguntar "este clube joga duas vezes no mesmo
// dia?" de jeito nenhum.
//
// Enumerar o calendário real pela curva de hoje mostrou 22 colisões de mesmo
// dia e 41 de dias seguidos, em 14 perfis de clube. A causa não é o
// `ordemGlobal` — dentro de um perfil ele nunca repete, por construção. A causa
// é a RESOLUÇÃO da curva: ela espalha 370 casas sobre 295 dias, a 0,8 dia por
// casa, então duas casas vizinhas (a Champions em 303 e a Europa League em 304)
// caem no mesmo dia ou em dias seguidos.
//
// Por isso o conserto não é empurrar 63 jogos um a um: é trocar a curva por uma
// GRADE em que a colisão é impossível. Ver [CalendarioDaTemporada.dia].
//
// Esta peça ainda não é usada pelos geradores de partida — ver docs/calendario-
// da-temporada.md, etapa 2.
// ─────────────────────────────────────────────────────────────────────────────

/** Uma competição e os dias do ano que ela ocupa, na ordem das rodadas. */
data class CompeticaoNoCalendario(
    val chave: String,
    /** País da competição, ou [INTERNACIONAL] quando ela não pertence a um. */
    val pais: String,
    val dias: List<Int>
) {
    companion object { const val INTERNACIONAL = "—" }
}

/** Um problema no calendário de um clube que dispute as duas competições. */
data class ConflitoDeCalendario(
    val dia: Int,
    val competicaoA: String,
    val competicaoB: String,
    val tipo: Tipo
) {
    enum class Tipo {
        /** Dois jogos no mesmo dia. */
        MESMO_DIA,
        /** Jogos em dias seguidos. */
        DIAS_CONSECUTIVOS
    }
}

object CalendarioDaTemporada {

    /** Primeiro dia do ano em que a temporada joga: 8 de fevereiro. */
    const val PRIMEIRO_DIA = 39

    /**
     * Semanas da temporada. 43 semanas a partir de 8 de fevereiro terminam em 29
     * de novembro — um dia antes do fim de hoje, e ainda dentro do ano.
     */
    const val SEMANAS = 43

    /** Último dia das competições de pontos corridos: 29 de novembro. */
    const val ULTIMO_DIA_DE_LIGA = PRIMEIRO_DIA + (SEMANAS - 1) * 7

    // ─────────────────────────────────────────────────────────────────────
    //  A grade
    // ─────────────────────────────────────────────────────────────────────

    /** Casa do fim de semana: onde jogam as competições de pontos corridos. */
    const val FIM_DE_SEMANA = 0

    /** Primeira casa de meio de semana. */
    const val MEIO_DE_SEMANA_1 = 3

    /** Segunda casa de meio de semana. */
    const val MEIO_DE_SEMANA_2 = 5

    /**
     * Último dia que qualquer competição da grade pode ocupar: 4 de dezembro.
     *
     * É o meio de semana da última semana, e fica DEPOIS de
     * [ULTIMO_DIA_DE_LIGA] de propósito: a final da Libertadores e a da Copa do
     * Brasil jogam quando os pontos corridos já acabaram, como na vida real. As
     * duas pontas são coisas diferentes, e confundi-las foi o que fez a rodada
     * de meio de semana da Championship vazar da temporada.
     */
    const val ULTIMO_DIA_DA_GRADE = PRIMEIRO_DIA + (SEMANAS - 1) * 7 + MEIO_DE_SEMANA_2

    /** As três casas de uma semana, na ordem. */
    val CASAS = listOf(FIM_DE_SEMANA, MEIO_DE_SEMANA_1, MEIO_DE_SEMANA_2)

    /**
     * O dia do ano de uma casa da grade.
     *
     * É daqui que vem a garantia que a curva não dava. As três casas de uma
     * semana distam 3 e 2 dias entre si, e a última de uma semana dista 2 da
     * primeira da seguinte:
     *
     *     semana w:      +0 ... +3 ... +5      semana w+1: +0
     *     distâncias:       3      2        2
     *
     * Logo **duas casas distintas nunca são o mesmo dia nem dias seguidos**, e
     * um perfil de clube sem conflito é apenas um perfil cujas competições não
     * dividem casa. Isso troca um problema de coloração por um de alocação de
     * conjuntos, que se resolve lendo esta tabela.
     */
    fun dia(semana: Int, casa: Int): Int = PRIMEIRO_DIA + semana * 7 + casa

    /** [quantas] semanas espalhadas por igual no intervalo, com as duas pontas. */
    fun semanasEspacadas(quantas: Int, de: Int = 0, ate: Int = SEMANAS - 1): List<Int> {
        if (quantas <= 0) return emptyList()
        if (quantas == 1) return listOf(de)
        return (0 until quantas).map { de + it * (ate - de) / (quantas - 1) }
    }

    /** [quantas] semanas espalhadas por igual DENTRO de uma lista de semanas. */
    fun semanasEscolhidas(semanas: List<Int>, quantas: Int): List<Int> {
        if (quantas <= 0 || semanas.isEmpty()) return emptyList()
        if (quantas == 1) return listOf(semanas.first())
        return (0 until quantas).map { semanas[it * (semanas.size - 1) / (quantas - 1)] }
    }

    /** Os dias de uma competição de meio de semana, dadas as semanas e a casa. */
    fun diasDeMeioDeSemana(semanas: List<Int>, casa: Int): List<Int> =
        semanas.map { dia(it, casa) }

    // ─────────────────────────────────────────────────────────────────────
    //  Pontos corridos
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Os dias de uma liga de pontos corridos: um fim de semana por rodada.
     *
     * A fórmula antiga era `39 + (rodada*10 - 10) * 295 / 370`, que tinha 38
     * rodadas embutidas no 370. Por isso a **EFL Championship**, que tem 46
     * rodadas (24 clubes), estourava a temporada: da 39ª em diante ela caía
     * depois do fim da janela, e as rodadas 44, 45 e 46 colapsavam as três no
     * dia 31 de dezembro.
     *
     * Com [rodadas] como parâmetro qualquer liga cabe. Até [SEMANAS] rodadas
     * cada uma tem o seu fim de semana, e as semanas que sobram ficam vazias —
     * são as pausas de seleção. Acima disso o excedente vai para
     * [MEIO_DE_SEMANA_1], que é a rodada de meio de semana que as ligas longas
     * têm de verdade — mas só até a penúltima semana, porque o meio de semana da
     * última já cai depois de [ULTIMO_DIA_DE_LIGA], e uma rodada de pontos
     * corridos depois do fim da liga é exatamente o defeito que esta função
     * conserta.
     */
    fun diasDeLiga(rodadas: Int): List<Int> {
        if (rodadas <= 0) return emptyList()
        if (rodadas <= SEMANAS) {
            return semanasEspacadas(rodadas).map { dia(it, FIM_DE_SEMANA) }
        }
        val todosOsFinsDeSemana = (0 until SEMANAS).map { dia(it, FIM_DE_SEMANA) }
        val excedente = semanasEspacadas(rodadas - SEMANAS, 0, SEMANAS - 2)
            .map { dia(it, MEIO_DE_SEMANA_1) }
        return (todosOsFinsDeSemana + excedente).sorted()
    }

    /**
     * Os fins de semana de um torneio que ocupa uma FAIXA de semanas.
     *
     * É o caso dos calendários em turnos: o Apertura argentino nas semanas 0..19
     * e o Clausura nas 21..40, o uruguaio em quatro trechos. Aqui a rodada não
     * se espalha pela temporada inteira — ela anda semana a semana dentro do
     * trecho, e é o trecho que separa um torneio do outro.
     */
    fun diasDeFaixa(primeiraSemana: Int, rodadas: Int): List<Int> =
        (0 until rodadas).map { dia(primeiraSemana + it, FIM_DE_SEMANA) }

    // ─────────────────────────────────────────────────────────────────────
    //  As semanas de cada competição de meio de semana
    // ─────────────────────────────────────────────────────────────────────

    /**
     * As 18 semanas das europeias: 8 da Fase de Liga, 2 do play-off e 8 do
     * mata-mata. A Champions joga na casa [MEIO_DE_SEMANA_1] e a Europa League
     * na [MEIO_DE_SEMANA_2] das MESMAS semanas — nenhum clube disputa as duas na
     * mesma temporada, então as duas casas nunca se encontram num mesmo elenco.
     */
    val SEMANAS_EUROPEIAS: List<Int> = semanasEspacadas(18)

    /**
     * As 19 semanas da Libertadores: 6 da Pré, 6 dos grupos e 7 do mata-mata.
     * Casa [MEIO_DE_SEMANA_2] — a [MEIO_DE_SEMANA_1] fica para a copa nacional
     * do clube, e nenhum clube sul-americano joga as europeias.
     */
    val SEMANAS_LIBERTADORES: List<Int> = semanasEspacadas(19)

    /**
     * As 16 semanas da Sul-Americana: **uma semana depois da Libertadores**.
     *
     * Ela também joga na casa [MEIO_DE_SEMANA_2], e não bastaria dizer que um
     * clube disputa só uma das duas: os eliminados da Fase 3 da Pré-Libertadores
     * entram nos grupos da Sul-Americana, então existe elenco com jogo nas duas.
     * Deslocar de uma semana torna os dois conjuntos disjuntos por construção,
     * porque [SEMANAS_LIBERTADORES] nunca tem duas semanas seguidas.
     */
    val SEMANAS_SULAMERICANA: List<Int> =
        SEMANAS_LIBERTADORES.map { it + 1 }.filter { it <= SEMANAS - 1 }.take(16)

    /**
     * As semanas livres para as copas nacionais que jogam na casa
     * [MEIO_DE_SEMANA_2]: **uma semana depois das europeias**.
     *
     * São a Copa da Inglaterra, a Copa do Rei e a Coppa Italia. A casa
     * [MEIO_DE_SEMANA_1] já é da Champions e a [MEIO_DE_SEMANA_2] é da Europa
     * League, então um clube inglês na Europa League e na Copa da Inglaterra
     * exige que as duas não dividam semana. O deslocamento de uma garante isso,
     * pois [SEMANAS_EUROPEIAS] não tem duas semanas seguidas.
     */
    val SEMANAS_COPA_EUROPEIA: List<Int> =
        SEMANAS_EUROPEIAS.map { it + 1 }.filter { it <= SEMANAS - 1 }

    /** As 12 semanas da Copa do Brasil, na casa [MEIO_DE_SEMANA_1]. */
    val SEMANAS_COPA_DO_BRASIL: List<Int> = semanasEspacadas(12)

    /** As 10 semanas da Copa Argentina, na casa [MEIO_DE_SEMANA_1]. */
    val SEMANAS_COPA_ARGENTINA: List<Int> = semanasEspacadas(10)

    // ─────────────────────────────────────────────────────────────────────
    //  Fora da grade: antes e depois da temporada
    // ─────────────────────────────────────────────────────────────────────

    /** Supercopas nacionais: 25 de janeiro, antes de qualquer outro jogo. */
    const val DIA_SUPERCOPA_NACIONAL = 25

    /**
     * Supercopa da UEFA: 1º de fevereiro.
     *
     * Ela NÃO pode dividir o dia com as supercopas nacionais, embora as cinco
     * nasçam na mesma casa hoje: o campeão da Champions costuma ser campeão da
     * liga, e então o mesmo clube joga a nacional e a da UEFA.
     */
    const val DIA_SUPERCOPA_UEFA = 32

    /** Recopa Sul-Americana: ida em 29 de janeiro, volta em 3 de fevereiro. */
    val DIAS_RECOPA = listOf(29, 34)

    /** Troféu dos Campeões: 11 de dezembro. */
    const val DIA_TROFEU = 345

    /** Copa Intercontinental: 15 de dezembro, o último jogo oficial do ano. */
    const val DIA_INTERCONTINENTAL = 349

    /**
     * Os quatro amistosos de entressafra: dois em janeiro, dois em dezembro.
     *
     * Pela curva de hoje os dois de dezembro caíam os dois em 20 de dezembro, e
     * os dois de janeiro em 15 de janeiro — dois jogos no mesmo dia, no único
     * caso em que o conflito era de uma competição com ela mesma fora de uma
     * liga.
     */
    val DIAS_AMISTOSOS = listOf(15, 18, 354, 357)

    // ─────────────────────────────────────────────────────────────────────
    //  Conversão de dia
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Converte uma casa de `ordemGlobal` no dia do ano, pela curva ANTIGA.
     *
     * Continua aqui como LEITOR do histórico: as temporadas gravadas antes da
     * etapa 2 seguem na escala que entendem, e é esta função que as data.
     */
    fun diaDaCasa(ordemGlobal: Int, nomeCampeonato: String = ""): Int {
        val (mes, dia) = CalendarioDoJogo.naCurva(ordemGlobal, nomeCampeonato)
        return diaDoAno(mes, dia)
    }

    /** (mês, dia) → dia do ano, em ano não bissexto. */
    fun diaDoAno(mes: Int, dia: Int): Int {
        if (mes !in 1..12) return 0
        val diasAteOMes = intArrayOf(0, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        return diasAteOMes[mes] + dia
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Validação
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Os conflitos entre as competições de um perfil de clube.
     *
     * Um perfil é o conjunto de competições que um MESMO clube pode disputar na
     * mesma temporada — um brasileiro na Libertadores joga Série A, Copa do
     * Brasil e Libertadores, e pode jogar Supercopa, Recopa e Intercontinental.
     * Competições de países diferentes não entram no mesmo perfil, e por isso
     * podem dividir o mesmo dia sem problema.
     */
    fun conflitosDoPerfil(competicoes: List<CompeticaoNoCalendario>): List<ConflitoDeCalendario> {
        val porDia = sortedMapOf<Int, MutableList<String>>()
        for (competicao in competicoes) {
            for (dia in competicao.dias) {
                porDia.getOrPut(dia) { mutableListOf() }.add(competicao.chave)
            }
        }
        val conflitos = mutableListOf<ConflitoDeCalendario>()

        // Mesmo dia: inclui a mesma competição duas vezes no dia, que é como a
        // Championship colapsava três rodadas em 31 de dezembro.
        for ((dia, chaves) in porDia) {
            if (chaves.size < 2) continue
            val ordenadas = chaves.sorted()
            for (i in ordenadas.indices) {
                for (j in i + 1 until ordenadas.size) {
                    conflitos += ConflitoDeCalendario(
                        dia, ordenadas[i], ordenadas[j], ConflitoDeCalendario.Tipo.MESMO_DIA
                    )
                }
            }
        }

        // Dias consecutivos: nenhum clube joga em dois dias seguidos.
        val dias = porDia.keys.toList()
        for (i in 0 until dias.size - 1) {
            if (dias[i + 1] - dias[i] != 1) continue
            for (a in porDia.getValue(dias[i]).distinct().sorted()) {
                for (b in porDia.getValue(dias[i + 1]).distinct().sorted()) {
                    conflitos += ConflitoDeCalendario(
                        dias[i], a, b, ConflitoDeCalendario.Tipo.DIAS_CONSECUTIVOS
                    )
                }
            }
        }
        return conflitos
    }

    /** Rodadas que caem fora da janela de pontos corridos. */
    fun diasForaDaJanela(competicao: CompeticaoNoCalendario): List<Int> =
        competicao.dias.filter { it < PRIMEIRO_DIA || it > ULTIMO_DIA_DE_LIGA }
}
