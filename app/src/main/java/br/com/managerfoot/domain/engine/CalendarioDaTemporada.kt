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
// Aqui o calendário vira uma tabela que se pode ler, comparar e VALIDAR. Esta
// peça ainda não é usada pelos geradores de partida — ver docs/calendario-da-
// temporada.md, etapa 2.
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

    /** Último dia das competições de pontos corridos: 30 de novembro. */
    const val ULTIMO_DIA_DE_LIGA = 334

    /**
     * Os dias de uma liga de pontos corridos, espalhados pela janela.
     *
     * A fórmula antiga era `39 + (rodada*10 - 10) * 295 / 370`, que é esta com
     * 38 rodadas embutidas no 370. Por isso a **EFL Championship**, que tem 46
     * rodadas (24 clubes), estourava a temporada: da 39ª em diante ela caía
     * depois de 30 de novembro, e as rodadas 44, 45 e 46 colapsavam as três no
     * dia 31 de dezembro. Com o número de rodadas como parâmetro, qualquer liga
     * cabe na janela, de 20 rodadas ou de 46.
     */
    fun diasDeLiga(rodadas: Int): List<Int> {
        if (rodadas <= 0) return emptyList()
        if (rodadas == 1) return listOf(PRIMEIRO_DIA)
        val amplitude = ULTIMO_DIA_DE_LIGA - PRIMEIRO_DIA
        return (1..rodadas).map { rodada ->
            PRIMEIRO_DIA + (rodada - 1) * amplitude / (rodadas - 1)
        }
    }

    /**
     * Converte uma casa de `ordemGlobal` no dia do ano, pela curva de hoje.
     *
     * É a ponte entre o calendário implícito e este: enquanto a etapa 2 não
     * troca a gravação, as competições que não são de pontos corridos têm os
     * dias herdados daqui, para que o calendário desta tabela seja o MESMO que
     * o jogo mostra hoje.
     */
    fun diaDaCasa(ordemGlobal: Int, nomeCampeonato: String = ""): Int {
        val (mes, dia) = CalendarioDoJogo.ordemGlobalParaMesDia(ordemGlobal, nomeCampeonato)
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
