package br.com.managerfoot.domain.engine

import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Sorteios da CONMEBOL Copa Sul-Americana. Ver regras_sulamericana.md.
//
// Duas coisas distinguem estes sorteios dos da Libertadores:
//
//  1. A Primeira Fase é NACIONAL — os 4 clubes de um país se enfrentam entre si.
//     É o oposto de toda outra fase prévia do jogo, que embaralha países.
//
//  2. A trava de país na fase de grupos é uma PROIBIÇÃO, não um objetivo. O
//     enunciado da Libertadores não impõe restrição geográfica, e lá o sorteio
//     minimiza compatriotas sem nunca falhar; aqui o enunciado proíbe, então o
//     sorteio é por retrocesso — ele encontra um arranjo sem compatriota nenhum
//     ou devolve null, como o da Champions.
//
// E a trava é APERTADA, não folgada: o Brasil pode chegar com 8 clubes aos
// grupos (6 da cota + 2 que caíram na Fase 3 da Pré-Libertadores), e 8 clubes em
// 8 grupos só cabem se cada grupo receber exatamente um. É por isso que um
// sorteio guloso não serve.
// ─────────────────────────────────────────────────────────────────────────────

/** Um grupo da Sul-Americana: a mesma forma de um grupo da Libertadores. */
typealias GrupoSulAmericana = GrupoLibertadores

data class ResultadoSorteioSulAmericana(
    val potes: List<List<ClubeChampions>>,
    val grupos: List<GrupoSulAmericana>
)

/** Por que um sorteio de grupos não tem solução. */
sealed interface InviabilidadeSulAmericana {
    /** Não são 32 clubes. */
    data class QuantidadeErrada(val clubes: Int) : InviabilidadeSulAmericana
    /**
     * Um país tem mais clubes do que grupos: com 9 clubes de um país em 8 grupos,
     * dois caem juntos por contagem, e nenhum algoritmo resolve.
     */
    data class PaisConcentrado(val pais: String, val clubes: Int) : InviabilidadeSulAmericana
}

object SorteioSulAmericana {

    const val GRUPOS = ListaDeAcessoSulAmericana.GRUPOS                     // 8
    const val CLUBES_POR_GRUPO = ListaDeAcessoSulAmericana.CLUBES_POR_GRUPO // 4
    const val CLUBES = GRUPOS * CLUBES_POR_GRUPO                            // 32

    /** Rótulos dos grupos: A..H. */
    val ROTULOS = ('A' until 'A' + GRUPOS).map { it.toString() }

    /** O mesmo critério de divisão da Libertadores: o Ranking de Clubes da CONMEBOL. */
    val POR_RANKING: Comparator<ClubeChampions> = SorteioLibertadores.POR_RANKING

    /** Teto de passos do retrocesso, para um caso sem solução não virar laço. */
    private const val PASSOS_MAXIMOS = 200_000

    // ── Primeira Fase: preliminar dentro de cada país ────────────────────────
    /**
     * Chaveia a Primeira Fase. Os 4 clubes de cada associação se enfrentam entre
     * si — **1º×4º e 2º×3º pelo ranking** —, em partida única, e o melhor
     * ranqueado joga em casa.
     *
     * O enunciado diz que a fase é nacional e de partida única com pênaltis, mas
     * não diz quem pega quem; 1×4 e 2×3 é o chaveamento que o resto do jogo usa
     * (ver [SorteioLibertadores.chavearFase3]) e premia o ranking, em vez de
     * sortear dois favoritos um contra o outro na estreia.
     *
     * Devolve os pares na convenção de [MotorCampeonato.gerarFaseJogoUnico]:
     * (mandante, visitante). Um país com um número ímpar de clubes tem o último
     * ignorado — a lista de acesso garante que isso não aconteça, movendo as
     * vagas da Primeira Fase em pares.
     */
    fun chavearPrimeiraFase(clubes: List<ClubeChampions>): List<Pair<Int, Int>> =
        clubes.groupBy { it.pais }
            .toSortedMap()
            .flatMap { (_, doPais) ->
                val ordenados = doPais.sortedWith(POR_RANKING)
                val n = ordenados.size / 2
                (0 until n).map { i ->
                    // O melhor ranqueado recebe o jogo em casa.
                    ordenados[i].timeId to ordenados[ordenados.size - 1 - i].timeId
                }
            }

    // ── Fase de grupos: 4 potes de 8, pelo ranking ───────────────────────────
    /** Divide os 32 clubes em 4 potes de 8, por coeficiente decrescente. */
    fun montarPotes(clubes: List<ClubeChampions>): List<List<ClubeChampions>> =
        clubes.sortedWith(POR_RANKING).chunked(GRUPOS)

    /**
     * Por que um sorteio não tem solução, ou null quando ele é viável.
     *
     * Só há uma forma de inviabilidade: um país com mais de 8 clubes. Dentro de
     * um pote a trava nunca aperta — cada grupo recebe um clube de cada pote, e
     * dois clubes do mesmo pote nunca se encontram.
     */
    fun verificarViabilidade(clubes: List<ClubeChampions>): InviabilidadeSulAmericana? {
        if (clubes.size != CLUBES) return InviabilidadeSulAmericana.QuantidadeErrada(clubes.size)
        val porPais = clubes.groupingBy { it.pais }.eachCount()
        val pior = porPais.maxByOrNull { it.value }
        if (pior != null && pior.value > GRUPOS) {
            return InviabilidadeSulAmericana.PaisConcentrado(pior.key, pior.value)
        }
        return null
    }

    /**
     * Sorteia a fase de grupos: 8 grupos de 4, um clube de cada pote por grupo e
     * **nenhum par de compatriotas**.
     *
     * O retrocesso é o que garante a trava. Um sorteio sequencial que só anda
     * para o próximo grupo livre — o da Libertadores — encurrala a última
     * bolinha quando a trava é apertada; aqui, quando um clube não cabe em
     * nenhum grupo, o algoritmo desfaz a escolha anterior em vez de aceitar o
     * compatriota.
     *
     * Devolve null quando não são 32 clubes, quando um país tem mais de 8 (ver
     * [verificarViabilidade]) ou quando o teto de passos é atingido.
     */
    fun sortearGrupos(
        clubes: List<ClubeChampions>,
        random: Random = Random.Default
    ): ResultadoSorteioSulAmericana? {
        if (verificarViabilidade(clubes) != null) return null
        val potes = montarPotes(clubes)
        val grupos = Array(GRUPOS) { mutableListOf<ClubeChampions>() }
        // A fila é embaralhada DENTRO de cada pote: a estrutura dos potes é
        // ranking puro, e o acaso decide só qual grupo cada bolinha pega.
        val fila = potes.flatMap { it.shuffled(random) }
        val passos = intArrayOf(PASSOS_MAXIMOS)
        val colocou = colocar(fila, 0, grupos, (0 until GRUPOS).toList(), random, passos)
        if (!colocou) return null
        return ResultadoSorteioSulAmericana(
            potes = potes,
            grupos = grupos.mapIndexed { i, doGrupo -> GrupoSulAmericana(ROTULOS[i], doGrupo) }
        )
    }

    /**
     * Coloca a bolinha [i] da fila e segue adiante, voltando atrás quando o
     * caminho não leva a lugar nenhum.
     *
     * [livres] são os grupos que ainda não receberam ninguém DESTE pote. Quando
     * a lista esvazia, o pote acabou e o seguinte começa com os 8 grupos livres
     * de novo — é isso que mantém um clube de cada pote por grupo.
     */
    private fun colocar(
        fila: List<ClubeChampions>,
        i: Int,
        grupos: Array<MutableList<ClubeChampions>>,
        livres: List<Int>,
        random: Random,
        passos: IntArray
    ): Boolean {
        if (i == fila.size) return true
        if (passos[0]-- <= 0) return false
        val clube = fila[i]
        val candidatos = livres
            .filter { g -> grupos[g].none { it.pais == clube.pais } }
            .shuffled(random)
        for (g in candidatos) {
            grupos[g] += clube
            val restantes = livres - g
            val proximos = if (restantes.isEmpty()) (0 until GRUPOS).toList() else restantes
            if (colocar(fila, i + 1, grupos, proximos, random, passos)) return true
            grupos[g].removeAt(grupos[g].lastIndex)
        }
        return false
    }

    /** Confere se um sorteio respeita a estrutura e a trava. Usado em teste. */
    fun validar(resultado: ResultadoSorteioSulAmericana): List<String> {
        val erros = mutableListOf<String>()
        if (resultado.grupos.size != GRUPOS) {
            erros += "esperados $GRUPOS grupos, obtidos ${resultado.grupos.size}"
        }
        val potePorId = HashMap<Int, Int>()
        resultado.potes.forEachIndexed { i, pote -> pote.forEach { potePorId[it.timeId] = i } }

        val todos = resultado.grupos.flatMap { it.clubes }
        if (todos.size != CLUBES) erros += "esperados $CLUBES clubes, obtidos ${todos.size}"
        if (todos.map { it.timeId }.distinct().size != todos.size) {
            erros += "clube repetido entre grupos"
        }

        for (grupo in resultado.grupos) {
            if (grupo.clubes.size != CLUBES_POR_GRUPO) {
                erros += "grupo ${grupo.rotulo}: ${grupo.clubes.size} clubes"
            }
            val potes = grupo.clubes.mapNotNull { potePorId[it.timeId] }
            if (potes.distinct().size != potes.size) {
                erros += "grupo ${grupo.rotulo}: mais de um clube do mesmo pote"
            }
            val repetidos = grupo.clubes.groupingBy { it.pais }.eachCount().filterValues { it > 1 }
            if (repetidos.isNotEmpty()) {
                erros += "grupo ${grupo.rotulo}: compatriotas de ${repetidos.keys.joinToString()}"
            }
        }
        return erros
    }

    /** Quantos pares de compatriotas o sorteio deixou. Com a trava, tem de ser 0. */
    fun paresDeCompatriotas(resultado: ResultadoSorteioSulAmericana): Int =
        resultado.grupos.sumOf { grupo ->
            grupo.clubes.groupingBy { it.pais }.eachCount().values
                .sumOf { n -> n * (n - 1) / 2 }
        }
}
