package br.com.managerfoot.domain.engine

import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Sorteio da fase de grupos da CONMEBOL Libertadores. Ver regras_libertadores.md.
//
// 32 clubes em 8 grupos de 4. Os potes saem do coeficiente: o pote 1 reúne os 8
// melhores, e cada grupo recebe exatamente um clube de cada pote.
//
// Diferente do sorteio da Champions, aqui a separação por país é um OBJETIVO, não
// uma trava: o enunciado não impõe nenhuma restrição geográfica, e com os 3 países
// que o jogo tem hoje separar compatriotas num grupo de 4 é impossível por
// construção — um grupo de 4 precisaria de 4 países distintos. Então o sorteio
// minimiza compatriotas em vez de proibi-los, e nunca falha.
// ─────────────────────────────────────────────────────────────────────────────

/** Um grupo da Libertadores: rótulo e os seus 4 clubes, um de cada pote. */
data class GrupoLibertadores(
    val rotulo: String,
    val clubes: List<ClubeChampions>
)

data class ResultadoSorteioLibertadores(
    val potes: List<List<ClubeChampions>>,
    val grupos: List<GrupoLibertadores>
)

object SorteioLibertadores {

    const val GRUPOS = ListaDeAcessoLibertadores.GRUPOS                    // 8
    const val CLUBES_POR_GRUPO = ListaDeAcessoLibertadores.CLUBES_POR_GRUPO // 4
    const val CLUBES = GRUPOS * CLUBES_POR_GRUPO                           // 32

    /** Rótulos dos grupos: A..H. */
    val ROTULOS = ('A' until 'A' + GRUPOS).map { it.toString() }

    /** Divide os 32 clubes em 4 potes de 8, por coeficiente decrescente. */
    fun montarPotes(clubes: List<ClubeChampions>): List<List<ClubeChampions>> =
        clubes.sortedWith(compareByDescending<ClubeChampions> { it.coeficiente }.thenBy { it.nome })
            .chunked(GRUPOS)

    /**
     * Sorteia a fase de grupos.
     *
     * Cada grupo recebe um clube de cada pote. Dentro de um pote, a escolha de qual
     * grupo recebe qual clube é aleatória, mas preferindo o grupo que **ainda não
     * tem** compatriota — é o que espalha os países sem transformar a separação numa
     * condição que poderia não ter solução.
     *
     * Devolve null só quando o número de clubes não é 32.
     */
    fun sortear(
        clubes: List<ClubeChampions>,
        random: Random = Random.Default
    ): ResultadoSorteioLibertadores? {
        if (clubes.size != CLUBES) return null
        val potes = montarPotes(clubes)
        val grupos = List(GRUPOS) { mutableListOf<ClubeChampions>() }

        for (pote in potes) {
            // Um clube de cada pote por grupo: os grupos disponíveis são todos os
            // que ainda não receberam alguém deste pote.
            val disponiveis = grupos.indices.toMutableList()
            for (clube in pote.shuffled(random)) {
                val escolhido = disponiveis
                    .sortedBy { idx -> grupos[idx].count { it.pais == clube.pais } }
                    .let { ordenados ->
                        // Entre os grupos com o MENOR número de compatriotas,
                        // escolhe ao acaso — senão o sorteio seria determinístico.
                        val menor = grupos[ordenados.first()].count { it.pais == clube.pais }
                        ordenados.filter { grupos[it].count { c -> c.pais == clube.pais } == menor }
                            .random(random)
                    }
                grupos[escolhido] += clube
                disponiveis -= escolhido
            }
        }

        return ResultadoSorteioLibertadores(
            potes = potes,
            grupos = grupos.mapIndexed { i, clubes -> GrupoLibertadores(ROTULOS[i], clubes) }
        )
    }

    /** Confere se um sorteio respeita a estrutura. Usado em teste. */
    fun validar(resultado: ResultadoSorteioLibertadores): List<String> {
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
            // Um de cada pote, exatamente.
            val potes = grupo.clubes.mapNotNull { potePorId[it.timeId] }
            if (potes.distinct().size != potes.size) {
                erros += "grupo ${grupo.rotulo}: mais de um clube do mesmo pote"
            }
        }
        return erros
    }

    /**
     * Quantos pares de compatriotas o sorteio deixou dentro de grupos. Serve de
     * métrica de qualidade: com as 10 associações o esperado é zero, e com os 3
     * países do seed atual o mínimo possível é maior que zero — um grupo de 4 não
     * cabe em 3 países.
     */
    fun paresDeCompatriotas(resultado: ResultadoSorteioLibertadores): Int =
        resultado.grupos.sumOf { grupo ->
            grupo.clubes.groupingBy { it.pais }.eachCount().values
                .sumOf { n -> n * (n - 1) / 2 }
        }
}
