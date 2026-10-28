package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.CampeonatoEntity
import br.com.managerfoot.data.database.entities.TipoCampeonato

// ─────────────────────────────────────────────────────────────────────────────
// A ponte entre as duas escalas de `ordemGlobal`.
//
// Os geradores de partida continuam produzindo os mesmos valores de sempre — as
// casas de `MotorCampeonato`, que são slots num contador de 1 a 620. Esta peça
// traduz cada slot para o DIA DO ANO da grade, e é aplicada num ponto só, na
// hora de gravar a partida.
//
// Foi de propósito: trocar o valor dentro dos ~109 lugares que montam calendário
// seria um refactor largo e arriscado para um ganho nenhum, porque a informação
// é a mesma. O que importa é que o valor GRAVADO seja o dia — é dele que saem a
// ordenação cronológica e a data exata de uma transferência, que não pertence a
// competição nenhuma e por isso não tem como consultar tabela de slots.
//
// A tradução é um índice: dentro de uma competição os slots são uma lista
// ordenada e os dias são outra, do mesmo tamanho. O slot na posição i vira o dia
// na posição i.
// ─────────────────────────────────────────────────────────────────────────────
object CalendarioDeSlots {

    /** Continente usado como "país" das competições europeias. */
    private const val EUROPA = "Europa"

    /**
     * O dia do ano de uma partida, dado o campeonato e o slot que o gerador
     * produziu.
     *
     * Quando a competição não é reconhecida, cai na curva antiga
     * ([CalendarioDaTemporada.diaDaCasa]): a data sai plausível em vez de o jogo
     * quebrar. Mas o silêncio é o risco real aqui — uma competição nova que
     * ninguém ligou a esta tabela seguiria funcionando, mal. Por isso
     * [reconhece] existe, e um teste exige que toda competição do jogo passe
     * por ele.
     */
    fun dia(campeonato: CampeonatoEntity, slot: Int, rodada: Int): Int =
        diaReconhecido(campeonato, slot, rodada)
            ?: CalendarioDaTemporada.diaDaCasa(slot, campeonato.nome)

    /** Se esta competição tem tradução própria, e não cai na curva antiga. */
    fun reconhece(campeonato: CampeonatoEntity, slot: Int, rodada: Int): Boolean =
        diaReconhecido(campeonato, slot, rodada) != null

    private fun diaReconhecido(campeonato: CampeonatoEntity, slot: Int, rodada: Int): Int? {
        val nome = campeonato.nome
        return when (campeonato.tipo) {
            TipoCampeonato.AMISTOSO -> diaDeAmistoso(slot)

            TipoCampeonato.INTERCONTINENTAL -> CalendarioDaTemporada.DIA_INTERCONTINENTAL

            TipoCampeonato.TROFEU_CAMPEOES -> CalendarioDaTemporada.DIA_TROFEU

            TipoCampeonato.SUPERCOPA ->
                if (campeonato.pais == EUROPA) CalendarioDaTemporada.DIA_SUPERCOPA_UEFA
                else CalendarioDaTemporada.DIA_SUPERCOPA_NACIONAL

            TipoCampeonato.COPA_NACIONAL -> diaDeCopaNacional(nome, slot)

            TipoCampeonato.CONTINENTAL -> diaDeContinental(nome, slot)

            TipoCampeonato.NACIONAL_DIVISAO1,
            TipoCampeonato.NACIONAL_DIVISAO2,
            TipoCampeonato.NACIONAL_DIVISAO3,
            TipoCampeonato.NACIONAL_DIVISAO4,
            TipoCampeonato.EXTRANGEIRO_DIVISAO1,
            TipoCampeonato.EXTRANGEIRO_DIVISAO2 -> diaDePontosCorridos(campeonato, rodada)

            else -> null
        }
    }

    // ── Amistosos ────────────────────────────────────────────────────────

    /** Base de `ordemGlobal` dos amistosos de janeiro. */
    const val BASE_AMISTOSO_JANEIRO = 5

    /** Base de `ordemGlobal` dos amistosos de dezembro. */
    const val BASE_AMISTOSO_DEZEMBRO = 450

    /** Acima disto o amistoso é de dezembro; abaixo, de janeiro. */
    const val LIMITE_AMISTOSO_DEZEMBRO = 400

    /**
     * São quatro amistosos por temporada: dois em janeiro e dois em dezembro.
     *
     * A rodada NÃO serve de índice aqui, e foi o que eu errei na primeira
     * versão: o container de amistosos só é limpo no encerramento da temporada,
     * então os dois de dezembro nascem com rodada 3 e 4, depois dos dois de
     * janeiro. Pior, se o jogador ignorar os de janeiro, os de dezembro voltam a
     * ser rodada 1 e 2.
     *
     * Então o que decide é o SLOT: a base diz a metade do ano, e a distância até
     * ela diz qual dos dois dias daquela metade — em módulo 2, para que o
     * contador de rodada ter andado não mude nada.
     */
    private fun diaDeAmistoso(slot: Int): Int? {
        val deDezembro = slot >= LIMITE_AMISTOSO_DEZEMBRO
        val base = if (deDezembro) BASE_AMISTOSO_DEZEMBRO else BASE_AMISTOSO_JANEIRO
        val ordem = slot - base
        if (ordem < 1) return null
        val dias = CalendarioDaTemporada.DIAS_AMISTOSOS
        // DIAS_AMISTOSOS é [jan, jan, dez, dez].
        val primeiroDaMetade = if (deDezembro) dias.size / 2 else 0
        return porIndice(dias, primeiroDaMetade + (ordem - 1) % 2)
    }

    // ── Pontos corridos ──────────────────────────────────────────────────

    /**
     * A rodada de uma liga é o índice direto: a rodada N joga no N-ésimo dia da
     * competição. Não há busca de slot aqui, e é o que torna este caminho imune
     * aos vários offsets que as ligas em turnos usam (o Clausura argentino soma
     * 210, o uruguaio 250, o Competencia 450).
     */
    private fun diaDePontosCorridos(campeonato: CampeonatoEntity, rodada: Int): Int? {
        val nome = campeonato.nome
        return when {
            ehArgentinaApertura(nome) ->
                porIndice(CalendarioDasCompeticoes.aperturaArgentino(), rodada - 1)

            ehArgentinaClausura(nome) ->
                porIndice(CalendarioDasCompeticoes.clausuraArgentino(), rodada - 1)

            ehUruguaiApertura(nome) ->
                porIndice(CalendarioDasCompeticoes.aperturaUruguaio(), rodada - 1)

            ehUruguaiIntermediario(nome) ->
                porIndice(CalendarioDasCompeticoes.intermediarioUruguaio(), rodada - 1)

            // O Clausura uruguaio guarda o playoff do Campeonato nas suas duas
            // rodadas seguintes: a semi na 16 e a final na 17.
            ehUruguaiClausura(nome) -> {
                val playoff = CalendarioDasCompeticoes.playoffUruguaio()
                val regulares = CalendarioDasCompeticoes.clausuraUruguaio()
                if (rodada > regulares.size) porIndice(playoff, rodada - regulares.size - 1)
                else porIndice(regulares, rodada - 1)
            }

            else -> porIndice(CalendarioDaTemporada.diasDeLiga(campeonato.totalRodadas), rodada - 1)
        }
    }

    // ── Copas nacionais ──────────────────────────────────────────────────

    private fun diaDeCopaNacional(nome: String, slot: Int): Int? = when {
        nome.contains("Copa do Brasil", ignoreCase = true) ->
            traduzir(MotorCampeonato.COPA_ORDEM_GLOBAL, CalendarioDasCompeticoes.copaDoBrasil(), slot)

        nome.contains("Copa Argentina", ignoreCase = true) ->
            traduzir(MotorCampeonato.COPA_ARG_ORDEM_GLOBAL, CalendarioDasCompeticoes.copaArgentina(), slot)

        nome.contains("Copa da Inglaterra", ignoreCase = true) ->
            traduzir(MotorCampeonato.COPA_ING_ORDEM_GLOBAL, CalendarioDasCompeticoes.copaDaInglaterra(), slot)

        // Copa do Rei e Coppa Italia dividem a mesma tabela de slots e a mesma
        // de dias: um clube só disputa a copa do seu país.
        nome.contains("Copa Espanhola", ignoreCase = true) ||
            nome.contains("Copa Italiana", ignoreCase = true) ->
            traduzir(MotorCampeonato.COPA16_ORDEM_GLOBAL, CalendarioDasCompeticoes.copaDeDezesseis(), slot)

        else -> null
    }

    // ── Continentais ─────────────────────────────────────────────────────

    private fun diaDeContinental(nome: String, slot: Int): Int? = when {
        // ANTES do ramo da Sul-Americana: "Recopa Sul-Americana" contém o nome
        // dela, e a ordem dos ramos é o que decide. Invertê-los faz a Recopa cair
        // na tabela da Sul-Americana, não achar os seus slots e voltar em
        // silêncio para a curva antiga — foi exatamente o que o teste pegou.
        nome.contains("Recopa", ignoreCase = true) ->
            traduzir(SLOTS_RECOPA, CalendarioDaTemporada.DIAS_RECOPA, slot)

        nome.contains("Champions", ignoreCase = true) -> traduzir(
            slotsDe(MotorCampeonato.FORMATO_CHAMPIONS),
            CalendarioDasCompeticoes.champions(),
            slot
        )

        nome.contains("Europa League", ignoreCase = true) -> traduzir(
            slotsDe(MotorCampeonato.FORMATO_EUROPA_LEAGUE),
            CalendarioDasCompeticoes.europaLeague(),
            slot
        )

        nome.contains("Libertadores", ignoreCase = true) -> traduzir(
            slotsDe(MotorCampeonato.FORMATO_LIBERTADORES),
            CalendarioDasCompeticoes.libertadores(),
            slot
        )

        nome.contains("Sul-Americana", ignoreCase = true) -> traduzir(
            slotsDe(MotorCampeonato.FORMATO_SULAMERICANA),
            CalendarioDasCompeticoes.sulAmericana(),
            slot
        )

        else -> null
    }

    /** As duas casas da Recopa, que vivem em `GameRepository` como privadas. */
    private val SLOTS_RECOPA = listOf(6, 9)

    // ── Reconhecimento de nome ───────────────────────────────────────────
    //
    // O nome é o que seleciona a tradução, e isso é frágil de propósito
    // reconhecido: `CalendarioDoJogo` já escolhia a curva argentina assim. O
    // risco é o silêncio — se um nome mudar, a competição cai na curva antiga e
    // continua "funcionando". Os testes pinam cada nome exatamente como o
    // rollover o escreve.

    fun ehArgentinaApertura(nome: String) =
        nome.contains("Argentina", ignoreCase = true) && nome.contains("Apertura", ignoreCase = true)

    fun ehArgentinaClausura(nome: String) =
        nome.contains("Argentina", ignoreCase = true) && nome.contains("Clausura", ignoreCase = true)

    fun ehUruguaiApertura(nome: String) =
        nome.contains("Uruguai", ignoreCase = true) && nome.contains("Apertura", ignoreCase = true)

    fun ehUruguaiClausura(nome: String) =
        nome.contains("Uruguai", ignoreCase = true) && nome.contains("Clausura", ignoreCase = true)

    fun ehUruguaiIntermediario(nome: String) =
        nome.contains("Uruguai", ignoreCase = true) && nome.contains("Intermedi", ignoreCase = true)

    // ── Mecânica ─────────────────────────────────────────────────────────

    /** Os slots de uma continental, na ordem em que o torneio os usa. */
    fun slotsDe(formato: MotorCampeonato.FormatoContinental): List<Int> =
        formato.ordemGlobalLiga.toList() +
            formato.ordemGlobalPlayoff.toList() +
            formato.ordemGlobalMataMata.toList()

    /** Os slots de uma competição no modelo da Libertadores. */
    fun slotsDe(formato: MotorCampeonato.FormatoLibertadores): List<Int> =
        formato.ordemGlobalPre.toList() +
            formato.ordemGlobalGrupos.toList() +
            formato.ordemGlobalMataMata.toList()

    private fun traduzir(slots: IntArray, dias: List<Int>, slot: Int): Int? =
        traduzir(slots.toList(), dias, slot)

    private fun traduzir(slots: List<Int>, dias: List<Int>, slot: Int): Int? {
        val i = slots.indexOf(slot)
        return if (i >= 0) porIndice(dias, i) else null
    }

    private fun porIndice(dias: List<Int>, i: Int): Int? = dias.getOrNull(i)
}
