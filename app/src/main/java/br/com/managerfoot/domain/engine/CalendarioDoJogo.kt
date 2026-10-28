package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.CalendarioVersao

// ─────────────────────────────────────────────────────────────────────────────
// A data exata de um momento do jogo.
//
// O jogo não guarda data: guarda `ordemGlobal`, um relógio monotônico dentro da
// temporada. Toda data que o jogador lê — a do card de próxima partida, a do
// calendário, e agora a de uma transferência — sai da conversão desse relógio
// em (mês, dia).
//
// Esta conversão estava copiada na tela do calendário e na do dashboard, e as
// cópias já divergiam: a do dashboard não conhecia amistosos, Intercontinental
// nem o Troféu dos Campeões, e datava os três pela curva genérica das ligas.
// Como as transferências seriam a terceira cópia, ela virou uma peça só.
// ─────────────────────────────────────────────────────────────────────────────

/** Um dia do calendário do jogo. [ano] vale 0 quando é desconhecido. */
data class DataDoJogo(val ano: Int, val mes: Int, val dia: Int) {

    /** Tem dia exato. */
    val conhecida: Boolean get() = temMes && dia >= 1

    /**
     * Tem pelo menos o mês.
     *
     * É o que sobra de uma transferência gravada antes de o relógio passar a
     * ser carimbado: o mês dela é real, o dia é que não foi registrado. Melhor
     * mostrar "mar 2027" do que nada — e muito melhor do que inventar um dia.
     */
    val temMes: Boolean get() = mes in 1..12

    /** "12 mar 2027", "mar 2027" sem dia, "12 mar" sem ano, "" sem mês. */
    fun curta(): String {
        if (!temMes) return ""
        val mesNome = CalendarioDoJogo.MESES_ABREV[mes]
        val comDia = if (dia >= 1) "$dia $mesNome" else mesNome
        return if (ano > 0) "$comDia $ano" else comDia
    }
}

object CalendarioDoJogo {

    val MESES_ABREV = listOf(
        "", "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"
    )

    val MESES = listOf(
        "", "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    /** As 12 casas fixas da Copa do Brasil, que não seguem a curva das ligas. */
    private val COPA_DATAS: Map<Int, Pair<Int, Int>> = mapOf(
        13 to (2 to 10), 33 to (2 to 25),
        53 to (3 to 15), 83 to (4 to 5),
        133 to (5 to 15), 163 to (6 to 10),
        207 to (7 to 15), 233 to (8 to 5),
        278 to (9 to 10), 313 to (10 to 8),
        357 to (11 to 12), 383 to (12 to 3)
    )

    private val DIAS_MES = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    /** Converte dia-do-ano (1..365) em (mês, dia). */
    fun diaDoAnoParaMesDia(diaDoAno: Int): Pair<Int, Int> {
        var restante = diaDoAno.coerceIn(1, 365)
        for (mes in 1..12) {
            if (restante <= DIAS_MES[mes]) return mes to restante
            restante -= DIAS_MES[mes]
        }
        return 12 to 31
    }

    /**
     * (mês, dia) de um momento do jogo.
     *
     * [nomeCampeonato] existe porque competições diferentes esticam o mesmo
     * intervalo de `ordemGlobal` sobre o ano de formas diferentes — o calendário
     * argentino tem 58 rodadas no mesmo período em que o brasileiro tem 38. Para
     * um momento que não pertence a competição nenhuma (uma transferência, por
     * exemplo), passe a string vazia e a curva genérica das ligas é usada.
     */
    /**
     * (mês, dia) de um momento de uma temporada da **grade**, onde
     * `ordemGlobal` já É o dia do ano. Não há curva nem nome de campeonato: o
     * valor gravado é a resposta.
     */
    fun diaParaMesDia(diaDoAno: Int): Pair<Int, Int> = diaDoAnoParaMesDia(diaDoAno)

    /**
     * (mês, dia) de um momento do jogo, na escala da temporada dele.
     *
     * [versao] não tem valor padrão de propósito. Um default faria um ponto de
     * leitura esquecido continuar compilando e passar a mostrar datas erradas em
     * silêncio nas temporadas novas; sem ele, o compilador lista todos.
     */
    fun ordemGlobalParaMesDia(
        ordemGlobal: Int,
        nomeCampeonato: String,
        versao: Int
    ): Pair<Int, Int> =
        if (versao == CalendarioVersao.CURVA) naCurva(ordemGlobal, nomeCampeonato)
        else diaParaMesDia(ordemGlobal)

    /**
     * A curva antiga: `ordemGlobal` é um contador de slots, e o dia sai de
     * espalhá-lo sobre o ano. É a escala de [CalendarioVersao.CURVA], e segue
     * aqui para ler as temporadas gravadas antes da grade.
     */
    fun naCurva(ordemGlobal: Int, nomeCampeonato: String = ""): Pair<Int, Int> = when {
        // Amistosos de entressafra: dezembro (~450) ou janeiro (~5).
        nomeCampeonato.contains("Amistoso", ignoreCase = true) ->
            if (ordemGlobal >= 400) 12 to 20 else 1 to 15
        ordemGlobal == 1 -> 1 to 25   // Supercopa Rei
        // Antes do ramo genérico de "Copa", que também casaria com o nome dela.
        nomeCampeonato.contains("Intercontinental", ignoreCase = true) -> 12 to 18
        ordemGlobal == 620 ||
            (nomeCampeonato.contains("Troféu", ignoreCase = true) &&
                nomeCampeonato.contains("Campe", ignoreCase = true)) -> 12 to 15
        nomeCampeonato.contains("Copa", ignoreCase = true) ->
            COPA_DATAS[ordemGlobal] ?: curvaDeLiga(ordemGlobal, 370)
        nomeCampeonato.contains("Argentina", ignoreCase = true) ->
            curvaDeLiga(ordemGlobal, 410)
        else -> curvaDeLiga(ordemGlobal, 370)
    }

    /** O mesmo, já com o ano. */
    fun ordemGlobalParaData(
        ordemGlobal: Int,
        ano: Int,
        nomeCampeonato: String,
        versao: Int
    ): DataDoJogo {
        val (mes, dia) = ordemGlobalParaMesDia(ordemGlobal, nomeCampeonato, versao)
        return DataDoJogo(ano, mes, dia)
    }

    /**
     * A curva das ligas: `ordemGlobal` de 10 a [amplitude] espalhado sobre os
     * 295 dias entre 8 de fevereiro (dia 39) e 30 de novembro.
     */
    private fun curvaDeLiga(ordemGlobal: Int, amplitude: Int): Pair<Int, Int> =
        diaDoAnoParaMesDia((39 + (ordemGlobal - 10) * 295 / amplitude).coerceIn(1, 365))

    /** O ano que aparece no nome do campeonato ("Brasileiro Série A 2027"), ou 0. */
    fun anoDoNomeDoCampeonato(nomeCampeonato: String): Int =
        Regex("\\d{4}").find(nomeCampeonato)?.value?.toIntOrNull() ?: 0
}
