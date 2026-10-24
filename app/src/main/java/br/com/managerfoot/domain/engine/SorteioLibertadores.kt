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
    /**
     * Sorteia a fase de grupos sem campeão vigente nem sobreviventes da Pré — a
     * forma curta de [sortearGrupos], que é onde a regra de verdade vive.
     */
    fun sortear(
        clubes: List<ClubeChampions>,
        random: Random = Random.Default
    ): ResultadoSorteioLibertadores? = sortearGrupos(clubes, random = random)

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
    /**
     * Os pares de compatriotas que a regra REALMENTE proíbe.
     *
     * Diferente de [paresDeCompatriotas], que conta todos, este desconta os pares
     * em que entra um sobrevivente da Pré: a exceção do Pote 4 permite que eles
     * enfrentem clubes do mesmo país, porque não estavam definidos quando os potes
     * fecharam. É esta a medida de qualidade do sorteio — a outra mistura o que é
     * defeito com o que é regra.
     */
    fun paresDeCompatriotasBloqueaveis(
        resultado: ResultadoSorteioLibertadores,
        sobreviventesDaPre: Set<Int>
    ): Int = resultado.grupos.sumOf { grupo ->
        val bloqueaveis = grupo.clubes.filter { it.timeId !in sobreviventesDaPre }
        bloqueaveis.groupingBy { it.pais }.eachCount().values.sumOf { n -> n * (n - 1) / 2 }
    }

    fun paresDeCompatriotas(resultado: ResultadoSorteioLibertadores): Int =
        resultado.grupos.sumOf { grupo ->
            grupo.clubes.groupingBy { it.pais }.eachCount().values
                .sumOf { n -> n * (n - 1) / 2 }
        }

    // ═════════════════════════════════════════════════════════════════════════
    //  O sorteio de verdade — dois momentos, o Ranking de Clubes da CONMEBOL
    //  como critério único de divisão.
    //
    //  Convenção dos pares: todas as funções devolvem (mandante da IDA, mandante
    //  da VOLTA), que é a forma que MotorCampeonato.gerarFaseIdaVolta consome. O
    //  clube melhor ranqueado fica SEMPRE na segunda posição, porque é ele que
    //  "decide o jogo de volta em casa".
    // ═════════════════════════════════════════════════════════════════════════

    /** Ordem do Ranking de Clubes da CONMEBOL: coeficiente, nome como desempate. */
    val POR_RANKING: Comparator<ClubeChampions> =
        compareByDescending<ClubeChampions> { it.coeficiente }.thenBy { it.nome }

    // ── Fase 1 da Pré: 6 clubes, dois potes de 3 ─────────────────────────────
    /**
     * Sorteia a Fase 1: os 6 clubes são divididos em dois potes pelo ranking, e os
     * 3 do Pote 1 enfrentam os 3 do Pote 2.
     *
     * Qual clube do Pote 2 pega qual do Pote 1 é sorteado; o do Pote 1, melhor
     * ranqueado, decide em casa.
     */
    fun sortearFase1(
        clubes: List<ClubeChampions>,
        random: Random = Random.Default
    ): List<Pair<Int, Int>> {
        val ordenados = clubes.sortedWith(POR_RANKING)
        val metade = ordenados.size / 2
        val pote1 = ordenados.take(metade)
        val pote2 = ordenados.drop(metade).shuffled(random)
        return pote1.indices.map { i -> pote2[i].timeId to pote1[i].timeId }
    }

    // ── Fase 2 da Pré: 16 clubes, Pote A e Pote B ────────────────────────────
    /**
     * Sorteia a Fase 2: entram os 13 pré-classificados e os 3 sobreviventes da
     * Fase 1.
     *
     *  - **Pote A** — os 8 melhores ranqueados ENTRE OS 13 pré-classificados. Eles
     *    decidem o jogo de volta em casa.
     *  - **Pote B** — os outros 5 pré-classificados e os 3 que vieram da Fase 1.
     *
     * As bolinhas do Pote B são sorteadas para enfrentar as do Pote A. Os 3 da
     * Fase 1 vão ao Pote B pelo caminho que percorreram, e não pelo ranking: é por
     * isso que [vindosDaFase1] é separado do resto em vez de sair da ordenação.
     */
    fun sortearFase2(
        clubes: List<ClubeChampions>,
        vindosDaFase1: Set<Int>,
        random: Random = Random.Default
    ): List<Pair<Int, Int>> {
        val daFase1 = clubes.filter { it.timeId in vindosDaFase1 }
        val preClassificados = clubes.filter { it.timeId !in vindosDaFase1 }
            .sortedWith(POR_RANKING)
        val confrontos = clubes.size / 2
        val poteA = preClassificados.take(confrontos)
        val poteB = (preClassificados.drop(confrontos) + daFase1).shuffled(random)
        return poteA.indices.map { i -> poteB[i].timeId to poteA[i].timeId }
    }

    // ── Fase 3 da Pré: sem sorteio ───────────────────────────────────────────
    /**
     * Chaveia a Fase 3. **Não há sorteio**: os 8 vencedores da Fase 2 se enfrentam
     * em confrontos pré-definidos, o 1º contra o último — C1×C8, C2×C7, C3×C6,
     * C4×C5.
     *
     * [vencedoresDaFase2] vem na ordem dos confrontos da Fase 2, que é a ordem do
     * Pote A: C1 saiu do confronto do melhor cabeça de chave, então é ele que
     * decide em casa.
     */
    fun chavearFase3(vencedoresDaFase2: List<Int>): List<Pair<Int, Int>> {
        val n = vencedoresDaFase2.size / 2
        return (0 until n).map { i ->
            vencedoresDaFase2[vencedoresDaFase2.size - 1 - i] to vencedoresDaFase2[i]
        }
    }

    // ── Fase de grupos: 4 potes de 8, pelo ranking ───────────────────────────
    /**
     * Sorteia a fase de grupos dos 32 — o sorteio principal.
     *
     * **Os potes**, estritamente pelo Ranking de Clubes da CONMEBOL:
     *  - **Pote 1** — o campeão vigente da Libertadores entra automaticamente na
     *    posição **A1**; as outras 7 vagas ficam com os melhores ranqueados.
     *  - **Pote 2** e **Pote 3** — os 8 seguintes, e os 8 seguintes.
     *  - **Pote 4** — os 4 de menor ranking mais os **4 sobreviventes da Pré**,
     *    independentemente do ranking deles.
     *
     * **O sorteio é sequencial**: todo o Pote 1 primeiro, definindo os cabeças de
     * chave do Grupo A ao H, depois o Pote 2, o Pote 3 e o Pote 4.
     *
     * **Bloqueio de países:** dois clubes do mesmo país não podem ficar no mesmo
     * grupo. A bolinha cai num grupo ao acaso e, se aquele grupo já tem um
     * compatriota, ela é movida para o **próximo grupo disponível**.
     *
     * **A exceção do Pote 4:** os 4 que vieram da Pré podem enfrentar clubes do
     * mesmo país. Eles não estavam definidos quando os potes foram fechados, então
     * o bloqueio não se aplica a eles — é a regra da competição, e não uma
     * concessão do algoritmo.
     *
     * Devolve null só quando o número de clubes não é 32.
     */
    fun sortearGrupos(
        clubes: List<ClubeChampions>,
        sobreviventesDaPre: Set<Int> = emptySet(),
        campeaoVigente: Int? = null,
        random: Random = Random.Default
    ): ResultadoSorteioLibertadores? {
        if (clubes.size != CLUBES) return null
        val potes = montarPotesPeloRanking(clubes, sobreviventesDaPre, campeaoVigente)
        val grupos = List(GRUPOS) { mutableListOf<ClubeChampions>() }

        potes.forEachIndexed { indiceDoPote, pote ->
            // Um clube de cada pote por grupo: os grupos disponíveis são os que
            // ainda não receberam alguém DESTE pote.
            val disponiveis = grupos.indices.toMutableList()

            // O campeão vigente é o primeiro do Pote 1 e vai direto para o A1.
            val cabecaFixa = if (indiceDoPote == 0) pote.firstOrNull { it.timeId == campeaoVigente } else null
            if (cabecaFixa != null) {
                grupos[0] += cabecaFixa
                disponiveis -= 0
            }

            for (clube in pote.filter { it !== cabecaFixa }.shuffled(random)) {
                // A bolinha cai num grupo ao acaso...
                val sorteado = disponiveis.random(random)
                val partida = disponiveis.indexOf(sorteado)
                // ...e, se houver compatriota lá, anda para o próximo disponível.
                val isento = clube.timeId in sobreviventesDaPre
                var escolhido = sorteado
                for (passo in disponiveis.indices) {
                    val candidato = disponiveis[(partida + passo) % disponiveis.size]
                    if (isento || grupos[candidato].none { it.pais == clube.pais }) {
                        escolhido = candidato
                        break
                    }
                }
                grupos[escolhido] += clube
                disponiveis -= escolhido
            }
        }

        // O "andar para o próximo grupo" é o MEIO; a regra é que dois
        // compatriotas não fiquem juntos. Quando a bolinha anda a volta inteira e
        // nenhum grupo serve — o que acontece quando as escolhas anteriores
        // encurralaram a última —, uma troca desfaz o nó. Ver [desfazerCompatriotas].
        desfazerCompatriotas(grupos, potes, sobreviventesDaPre, campeaoVigente)

        return ResultadoSorteioLibertadores(
            potes = potes,
            grupos = grupos.mapIndexed { i, doGrupo -> GrupoLibertadores(ROTULOS[i], doGrupo) }
        )
    }

    /**
     * Desfaz compatriotas que sobraram, trocando clubes DO MESMO POTE entre grupos.
     *
     * O sorteio sequencial escolhe grupo a grupo sem voltar atrás, então uma
     * escolha cedo pode encurralar a última bolinha num grupo que já tem
     * compatriota — mesmo quando existe arranjo sem nenhum. A troca é entre clubes
     * do mesmo pote justamente para preservar a estrutura do sorteio: cada grupo
     * continua com exatamente um clube de cada pote.
     *
     * Os sobreviventes da Pré ficam de fora: a regra da competição permite que
     * eles enfrentem compatriotas, então um par que envolva um deles não é um nó a
     * desfazer.
     *
     * Só age quando há o que consertar, e para quando não consegue mais melhorar.
     */
    private fun desfazerCompatriotas(
        grupos: List<MutableList<ClubeChampions>>,
        potes: List<List<ClubeChampions>>,
        sobreviventesDaPre: Set<Int>,
        campeaoVigente: Int?
    ) {
        fun ehNo(clube: ClubeChampions, grupo: List<ClubeChampions>) =
            clube.timeId !in sobreviventesDaPre &&
                grupo.any { it !== clube && it.pais == clube.pais && it.timeId !in sobreviventesDaPre }

        val poteDe = HashMap<Int, Int>()
        potes.forEachIndexed { i, pote -> pote.forEach { poteDe[it.timeId] = i } }

        repeat(GRUPOS * CLUBES_POR_GRUPO) {
            // Acha um clube em conflito e um parceiro de troca que resolva os dois.
            var trocou = false
            for (i in grupos.indices) {
                val problema = grupos[i].firstOrNull { ehNo(it, grupos[i]) } ?: continue
                val pote = poteDe[problema.timeId] ?: continue
                // A posição A1 é do campeão vigente: ele nunca é trocado de grupo.
                if (problema.timeId == campeaoVigente) continue
                for (j in grupos.indices) {
                    if (j == i) continue
                    val candidato = grupos[j]
                        .firstOrNull { poteDe[it.timeId] == pote && it.timeId != campeaoVigente }
                        ?: continue
                    // A troca só vale se nenhum dos dois ficar com compatriota.
                    val restoDeI = grupos[i].filter { it !== problema }
                    val restoDeJ = grupos[j].filter { it !== candidato }
                    val problemaFicaBem = restoDeJ.none { it.pais == problema.pais && it.timeId !in sobreviventesDaPre }
                    val candidatoFicaBem = candidato.timeId in sobreviventesDaPre ||
                        restoDeI.none { it.pais == candidato.pais && it.timeId !in sobreviventesDaPre }
                    if (problemaFicaBem && candidatoFicaBem) {
                        grupos[i].remove(problema); grupos[i].add(candidato)
                        grupos[j].remove(candidato); grupos[j].add(problema)
                        trocou = true
                        break
                    }
                }
                if (trocou) break
            }
            if (!trocou) return
        }
    }

    /**
     * Os 4 potes de 8, pelo Ranking de Clubes da CONMEBOL.
     *
     * Os sobreviventes da Pré vão ao Pote 4 por onde entraram, e não pelo ranking:
     * eles são retirados da ordenação antes de ela acontecer. O campeão vigente é
     * posto na frente do Pote 1, que é o que garante a ele a posição A1.
     */
    fun montarPotesPeloRanking(
        clubes: List<ClubeChampions>,
        sobreviventesDaPre: Set<Int> = emptySet(),
        campeaoVigente: Int? = null
    ): List<List<ClubeChampions>> {
        val daPre = clubes.filter { it.timeId in sobreviventesDaPre }
        val resto = clubes.filter { it.timeId !in sobreviventesDaPre }.sortedWith(POR_RANKING)

        // Pote 4: os de menor ranking completam as vagas que a Pré não ocupa.
        val dosMenores = (CLUBES_POR_GRUPO * 2 - daPre.size).coerceIn(0, resto.size)
        val pote4 = resto.takeLast(dosMenores) + daPre
        val acima = resto.dropLast(dosMenores)

        // Pote 1: o campeão vigente na frente, depois os melhores ranqueados.
        val campeao = acima.firstOrNull { it.timeId == campeaoVigente }
        val semCampeao = acima.filter { it !== campeao }
        val vagasAbertasNoPote1 = GRUPOS - (if (campeao != null) 1 else 0)
        val pote1 = listOfNotNull(campeao) + semCampeao.take(vagasAbertasNoPote1)
        val sobraram = semCampeao.drop(vagasAbertasNoPote1)

        return listOf(pote1, sobraram.take(GRUPOS), sobraram.drop(GRUPOS).take(GRUPOS), pote4)
    }
}
