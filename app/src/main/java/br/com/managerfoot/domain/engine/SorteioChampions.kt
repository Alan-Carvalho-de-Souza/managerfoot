package br.com.managerfoot.domain.engine

import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Sorteio da Fase de Liga da UEFA Champions League. Ver regras_championsleague.md.
//
// 36 clubes em 4 potes de 9, ordenados pelo coeficiente da UEFA. Cada clube
// enfrenta 8 adversários — 2 de cada pote, um em casa e um fora — respeitando
// duas travas geográficas: nunca um rival do próprio país, e no máximo 2 rivais
// de um mesmo país.
//
// O sorteio é um problema de satisfação de restrições, e sortear ingenuamente
// leva a becos sem saída: os últimos clubes ficam sem par válido. Daí o
// retrocesso (backtracking) com heurística de "slot mais restrito primeiro".
// ─────────────────────────────────────────────────────────────────────────────

/** Clube apto à Fase de Liga, com o coeficiente que define a ordem dos potes. */
data class ClubeChampions(
    val timeId: Int,
    val nome: String,
    val pais: String,
    val coeficiente: Double
)

/** Uma partida da Fase de Liga, já com mando definido. */
data class ConfrontoChampions(
    val casaId: Int,
    val foraId: Int
)

/** Resultado do sorteio: os potes e as 144 partidas da Fase de Liga. */
data class ResultadoSorteioChampions(
    val potes: List<List<ClubeChampions>>,
    val confrontos: List<ConfrontoChampions>
)

/** Por que um conjunto de clubes não pode ser sorteado sob as travas. */
sealed class InviabilidadeSorteio {
    data class QuantidadeDeClubes(val encontrados: Int) : InviabilidadeSorteio()
    data class PaisesInsuficientes(val encontrados: Int, val minimo: Int) : InviabilidadeSorteio()
    data class PaisComClubesDemais(val pais: String, val clubes: Int, val maximo: Int) : InviabilidadeSorteio()
    /** Um pote concentrado num país só: ver MAX_CLUBES_POR_PAIS_NO_POTE. */
    data class PoteConcentrado(
        val pote: Int,
        val pais: String,
        val clubes: Int,
        val maximo: Int
    ) : InviabilidadeSorteio()
}

object SorteioChampions {

    const val CLUBES = 36
    const val POTES = 4
    const val CLUBES_POR_POTE = 9
    const val ADVERSARIOS_POR_POTE = 2
    const val ADVERSARIOS = POTES * ADVERSARIOS_POR_POTE          // 8
    const val MAX_ADVERSARIOS_MESMO_PAIS = 2
    const val TOTAL_CONFRONTOS = CLUBES * ADVERSARIOS / 2         // 144

    /**
     * Mínimo de países para as travas serem satisfazíveis. Cada clube precisa de 8
     * adversários com no máximo 2 por país, logo precisa de 4 países além do seu:
     * 5 no total. É esta conta que torna a Champions impossível com os 3 países
     * europeus do seed — ver regras_championsleague.md.
     */
    const val MINIMO_DE_PAISES = 1 + ADVERSARIOS / MAX_ADVERSARIOS_MESMO_PAIS   // 5

    /**
     * Máximo de clubes que um país pode ter na Fase de Liga. Cada clube de um país
     * com K clubes precisa de 8 rivais de fora, e cada um dos 36−K clubes de fora
     * aceita no máximo 2 daquele país:
     *
     *     8K ≤ 2(36 − K)  →  10K ≤ 72  →  K ≤ 7
     */
    const val MAX_CLUBES_POR_PAIS =
        (MAX_ADVERSARIOS_MESMO_PAIS * CLUBES) / (ADVERSARIOS + MAX_ADVERSARIOS_MESMO_PAIS)  // 7

    /**
     * Máximo de clubes de um mesmo país DENTRO de um pote. Cada clube enfrenta 2
     * adversários do próprio pote, e nenhum deles pode ser compatriota. Num pote de
     * 9 com K clubes de um país, esses K demandam 2K vagas dos 9−K restantes, que
     * oferecem 2(9−K):
     *
     *     2K ≤ 2(9 − K)  →  K ≤ 4
     *
     * É uma restrição por pote, invisível na contagem global: um país com 5 clubes
     * cabe no torneio (≤ 7), mas não cabe num pote só.
     */
    const val MAX_CLUBES_POR_PAIS_NO_POTE = (CLUBES_POR_POTE - 1) / 2   // 4

    /**
     * Verifica se as travas podem ser satisfeitas, ANTES de tentar sortear. Devolve
     * a lista de impedimentos, vazia quando o sorteio é viável. Serve para falhar
     * com uma mensagem clara em vez de esgotar o retrocesso sem explicação.
     */
    fun verificarViabilidade(clubes: List<ClubeChampions>): List<InviabilidadeSorteio> {
        val problemas = mutableListOf<InviabilidadeSorteio>()
        if (clubes.size != CLUBES) {
            problemas += InviabilidadeSorteio.QuantidadeDeClubes(clubes.size)
        }
        val porPais = clubes.groupingBy { it.pais }.eachCount()
        if (porPais.size < MINIMO_DE_PAISES) {
            problemas += InviabilidadeSorteio.PaisesInsuficientes(porPais.size, MINIMO_DE_PAISES)
        }
        porPais.filter { it.value > MAX_CLUBES_POR_PAIS }.forEach { (pais, qtd) ->
            problemas += InviabilidadeSorteio.PaisComClubesDemais(pais, qtd, MAX_CLUBES_POR_PAIS)
        }
        // Concentração por pote, que a contagem global não revela.
        if (clubes.size == CLUBES) {
            montarPotes(clubes).forEachIndexed { indice, pote ->
                pote.groupingBy { it.pais }.eachCount()
                    .filter { it.value > MAX_CLUBES_POR_PAIS_NO_POTE }
                    .forEach { (pais, qtd) ->
                        problemas += InviabilidadeSorteio.PoteConcentrado(
                            pote = indice + 1, pais = pais,
                            clubes = qtd, maximo = MAX_CLUBES_POR_PAIS_NO_POTE
                        )
                    }
            }
        }
        return problemas
    }

    /** Divide os clubes em 4 potes de 9, estritamente por coeficiente decrescente. */
    fun montarPotes(clubes: List<ClubeChampions>): List<List<ClubeChampions>> =
        clubes.sortedWith(compareByDescending<ClubeChampions> { it.coeficiente }.thenBy { it.nome })
            .chunked(CLUBES_POR_POTE)

    /**
     * Sorteia a Fase de Liga. Devolve null quando não encontra solução dentro de
     * [tentativas] reinícios — o que, com a viabilidade verificada, é raro.
     *
     * O mando sai junto: ao emparelhar A e B, A consome um dos seus dois slots
     * contra o pote de B (um de casa, um de fora) e B consome o slot de mando
     * oposto contra o pote de A. Isso garante, de graça, 4 jogos em casa e 4 fora,
     * com um de cada mando por pote.
     */
    fun sortear(
        clubes: List<ClubeChampions>,
        random: Random = Random.Default,
        tentativas: Int = 200
    ): ResultadoSorteioChampions? {
        if (verificarViabilidade(clubes).isNotEmpty()) return null
        val potes = montarPotes(clubes)
        val poteDoClube = HashMap<Int, Int>()
        potes.forEachIndexed { indice, pote -> pote.forEach { poteDoClube[it.timeId] = indice } }

        repeat(tentativas) {
            val confrontos = tentarSortear(clubes, poteDoClube, random)
            if (confrontos != null) return ResultadoSorteioChampions(potes, confrontos)
        }
        return null
    }

    // ── Estado de um clube durante o sorteio ─────────────────────────────
    private class Estado(val clube: ClubeChampions) {
        /** Slots livres por pote e mando: [pote][0] = casa, [pote][1] = fora. */
        val slotLivre = Array(POTES) { booleanArrayOf(true, true) }
        val adversarios = HashSet<Int>()
        val porPais = HashMap<String, Int>()

        fun temSlot(pote: Int, casa: Boolean) = slotLivre[pote][if (casa) 0 else 1]

        fun podeEnfrentar(outro: Estado): Boolean {
            if (clube.pais == outro.clube.pais) return false              // trava 1
            if (outro.clube.timeId in adversarios) return false
            val jaDoPais = porPais[outro.clube.pais] ?: 0
            return jaDoPais < MAX_ADVERSARIOS_MESMO_PAIS                  // trava 2
        }

        fun ocupar(outro: Estado, pote: Int, casa: Boolean) {
            slotLivre[pote][if (casa) 0 else 1] = false
            adversarios += outro.clube.timeId
            porPais[outro.clube.pais] = (porPais[outro.clube.pais] ?: 0) + 1
        }

        fun liberar(outro: Estado, pote: Int, casa: Boolean) {
            slotLivre[pote][if (casa) 0 else 1] = true
            adversarios -= outro.clube.timeId
            val restante = (porPais[outro.clube.pais] ?: 1) - 1
            if (restante <= 0) porPais.remove(outro.clube.pais) else porPais[outro.clube.pais] = restante
        }

        fun slotsAbertos(): Int = slotLivre.sumOf { mandos -> mandos.count { it } }
    }

    private fun tentarSortear(
        clubes: List<ClubeChampions>,
        poteDoClube: Map<Int, Int>,
        random: Random
    ): List<ConfrontoChampions>? {
        val estados = clubes.associate { it.timeId to Estado(it) }
        val confrontos = mutableListOf<ConfrontoChampions>()
        return if (preencher(estados, poteDoClube, confrontos, random)) confrontos else null
    }

    /**
     * Retrocesso sobre os slots. A cada passo escolhe o slot (clube, pote, mando)
     * ainda aberto com MENOS candidatos válidos — a heurística que evita descobrir
     * o beco sem saída só no fim — e tenta cada candidato em ordem aleatória.
     */
    private fun preencher(
        estados: Map<Int, Estado>,
        poteDoClube: Map<Int, Int>,
        confrontos: MutableList<ConfrontoChampions>,
        random: Random
    ): Boolean {
        if (confrontos.size == TOTAL_CONFRONTOS) return true

        var melhorSlot: Triple<Estado, Int, Boolean>? = null
        var melhoresCandidatos: List<Estado> = emptyList()

        for (estado in estados.values) {
            for (pote in 0 until POTES) {
                for (casa in booleanArrayOf(true, false)) {
                    if (!estado.temSlot(pote, casa)) continue
                    val candidatos = candidatosPara(estado, pote, casa, estados, poteDoClube)
                    if (candidatos.isEmpty()) return false     // beco sem saída: retrocede já
                    if (melhorSlot == null || candidatos.size < melhoresCandidatos.size) {
                        melhorSlot = Triple(estado, pote, casa)
                        melhoresCandidatos = candidatos
                        if (candidatos.size == 1) break        // não há slot mais restrito
                    }
                }
                if (melhoresCandidatos.size == 1 && melhorSlot != null) break
            }
            if (melhoresCandidatos.size == 1 && melhorSlot != null) break
        }

        val (estado, pote, casa) = melhorSlot ?: return false
        val poteDoEstado = poteDoClube.getValue(estado.clube.timeId)

        for (candidato in melhoresCandidatos.shuffled(random)) {
            estado.ocupar(candidato, pote, casa)
            candidato.ocupar(estado, poteDoEstado, !casa)
            confrontos += if (casa) ConfrontoChampions(estado.clube.timeId, candidato.clube.timeId)
                          else ConfrontoChampions(candidato.clube.timeId, estado.clube.timeId)

            if (preencher(estados, poteDoClube, confrontos, random)) return true

            confrontos.removeAt(confrontos.lastIndex)
            candidato.liberar(estado, poteDoEstado, !casa)
            estado.liberar(candidato, pote, casa)
        }
        return false
    }

    /** Clubes do [pote] que podem ocupar o slot de mando [casa] deste [estado]. */
    private fun candidatosPara(
        estado: Estado,
        pote: Int,
        casa: Boolean,
        estados: Map<Int, Estado>,
        poteDoClube: Map<Int, Int>
    ): List<Estado> {
        val poteDoEstado = poteDoClube.getValue(estado.clube.timeId)
        return estados.values.filter { outro ->
            poteDoClube.getValue(outro.clube.timeId) == pote &&
                estado.podeEnfrentar(outro) &&
                outro.podeEnfrentar(estado) &&
                outro.temSlot(poteDoEstado, !casa)
        }
    }

    // ── Conferência do resultado ─────────────────────────────────────────
    /**
     * Confere se um sorteio respeita todas as regras. Usado em teste e disponível
     * para validar um sorteio vindo de save antigo.
     */
    fun validar(resultado: ResultadoSorteioChampions): List<String> {
        val erros = mutableListOf<String>()
        val clubes = resultado.potes.flatten()
        val poteDoClube = HashMap<Int, Int>()
        resultado.potes.forEachIndexed { i, pote -> pote.forEach { poteDoClube[it.timeId] = i } }
        val paisDoClube = clubes.associate { it.timeId to it.pais }

        if (resultado.confrontos.size != TOTAL_CONFRONTOS) {
            erros += "esperados $TOTAL_CONFRONTOS confrontos, obtidos ${resultado.confrontos.size}"
        }

        for (clube in clubes) {
            val comoCasa = resultado.confrontos.filter { it.casaId == clube.timeId }
            val comoFora = resultado.confrontos.filter { it.foraId == clube.timeId }
            val adversarios = comoCasa.map { it.foraId } + comoFora.map { it.casaId }

            if (adversarios.size != ADVERSARIOS) {
                erros += "${clube.nome}: ${adversarios.size} adversários, esperados $ADVERSARIOS"
            }
            if (comoCasa.size != ADVERSARIOS / 2 || comoFora.size != ADVERSARIOS / 2) {
                erros += "${clube.nome}: ${comoCasa.size} em casa e ${comoFora.size} fora"
            }
            if (adversarios.distinct().size != adversarios.size) {
                erros += "${clube.nome}: adversário repetido"
            }
            if (clube.timeId in adversarios) {
                erros += "${clube.nome}: enfrenta a si mesmo"
            }
            // 2 adversários de cada pote, um em casa e um fora
            for (pote in 0 until POTES) {
                val doPote = adversarios.count { poteDoClube[it] == pote }
                if (doPote != ADVERSARIOS_POR_POTE) {
                    erros += "${clube.nome}: $doPote adversários do pote ${pote + 1}"
                }
                val casaDoPote = comoCasa.count { poteDoClube[it.foraId] == pote }
                if (casaDoPote != 1) {
                    erros += "${clube.nome}: $casaDoPote jogos em casa contra o pote ${pote + 1}"
                }
            }
            // Travas geográficas
            val porPais = adversarios.groupingBy { paisDoClube[it] ?: "" }.eachCount()
            if ((porPais[clube.pais] ?: 0) > 0) {
                erros += "${clube.nome}: enfrenta rival do próprio país (${clube.pais})"
            }
            porPais.filter { it.value > MAX_ADVERSARIOS_MESMO_PAIS }.forEach { (pais, qtd) ->
                erros += "${clube.nome}: $qtd adversários de $pais, máximo $MAX_ADVERSARIOS_MESMO_PAIS"
            }
        }
        return erros
    }
}
