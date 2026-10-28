package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// O mercado de técnicos da IA: quem assedia quem, quanto a reputação sobe ou
// desce no fim da temporada, e quando um técnico pendura a prancheta.
//
// ── O problema que isto resolve ───────────────────────────────────────────
//
// O seed traz 363 técnicos para 363 clubes, nenhum livre. O único agente livre
// do mundo é o técnico que o jogador desalojou ao começar o jogo. Como TODA a
// contratação filtrava `timeId == null`, o mercado inteiro era uma cadeira
// musical com uma cadeira sobrando: o demitido da vez era o único disponível, e
// a "cascata de fim de temporada" fazia no máximo um movimento por ano.
//
// ── Como o assédio funciona aqui ──────────────────────────────────────────
//
// Clube não tira técnico de um pool; tira de OUTRO CLUBE, e a vaga desce. Os
// clubes são percorridos do mais forte para o mais fraco, e um clube só pode
// assediar quem está num clube pelo menos [MARGEM_ASSEDIO] abaixo dele. Duas
// consequências caem de graça dessa regra:
//
//  - **a cascata se arma sozinha.** O clube assediado tem reputação menor, logo
//    vem DEPOIS na ordem; quando a iteração chega nele, ele está vago e vai
//    buscar em alguém menor ainda. Chelsea leva o do Brighton, Brighton leva um
//    da Championship. Não é preciso fila nenhuma.
//  - **ela termina.** Como o assédio só anda para baixo e cada clube é visitado
//    uma vez, não há ciclo: ninguém rouba de volta de quem já se serviu.
//
// E o número de clubes sem técnico nunca cresce: cada assédio move a vaga para
// baixo, e o técnico substituído volta ao mercado para quem vier depois.
// ─────────────────────────────────────────────────────────────────────────────
object MercadoDeTecnicos {

    /** Quanto o clube precisa superar o clube assediado para poder tirar o técnico de lá. */
    const val MARGEM_ASSEDIO = 8f

    /** Quanto o alvo precisa superar o técnico atual para o clube se mexer. */
    const val MARGEM_UPGRADE = 8f

    /** Teto de reputação do técnico em relação à do clube — evita um nome grande na Série D. */
    const val TETO_ACIMA_DO_CLUBE = 10f

    /** Nem todo clube se mexe todo ano, mesmo com um upgrade disponível. */
    const val PROB_TROCA_POR_UPGRADE = 0.35f

    data class ClubeNoMercado(
        val timeId: Int,
        val reputacao: Float,
        /** País já normalizado, para comparar com a nacionalidade do técnico. */
        val pais: String
    )

    data class TecnicoNoMercado(
        val tecnicoId: Int,
        val reputacao: Float,
        val nacionalidade: String,
        /** Clube atual, ou null para quem está livre. */
        val timeId: Int?,
        val controladoPorJogador: Boolean = false
    )

    /** Um movimento decidido: [deTimeId] é null quando o técnico estava livre. */
    data class Contratacao(
        val tecnicoId: Int,
        val paraTimeId: Int,
        val deTimeId: Int?
    )

    /**
     * Decide os movimentos da janela de fim de temporada, na ordem em que devem
     * ser aplicados. É uma função pura: não lê nem grava nada, e [sorteio] é o
     * único ponto de aleatoriedade, injetável para os testes.
     *
     * Um clube VAGO contrata sempre que houver candidato — é a vaga descendo a
     * cascata. Um clube que já tem técnico só troca por um upgrade claro, e
     * ainda assim passa pelo sorteio.
     */
    fun janelaDeFimDeTemporada(
        clubes: List<ClubeNoMercado>,
        tecnicos: List<TecnicoNoMercado>,
        sorteio: () -> Float = { kotlin.random.Random.nextFloat() }
    ): List<Contratacao> {
        val porId = tecnicos.associateBy { it.tecnicoId }
        val reputacaoDoClube = clubes.associate { it.timeId to it.reputacao }

        // Estado do mercado enquanto a janela corre, nos dois sentidos.
        val clubeDoTecnico: MutableMap<Int, Int?> =
            tecnicos.associate { it.tecnicoId to it.timeId }.toMutableMap()
        val tecnicoDoClube: MutableMap<Int, Int?> =
            clubes.associate { clube ->
                clube.timeId to tecnicos.firstOrNull { it.timeId == clube.timeId }?.tecnicoId
            }.toMutableMap()

        val movimentos = mutableListOf<Contratacao>()

        // Do mais forte para o mais fraco. O desempate por id mantém a janela
        // reproduzível quando dois clubes têm a mesma reputação.
        val ordem = clubes.sortedWith(
            compareByDescending<ClubeNoMercado> { it.reputacao }.thenBy { it.timeId }
        )

        for (clube in ordem) {
            val atualId = tecnicoDoClube[clube.timeId]
            val atual = atualId?.let { porId[it] }
            if (atual?.controladoPorJogador == true) continue   // o usuário decide

            val candidatos = tecnicos.filter { candidato ->
                if (candidato.controladoPorJogador) return@filter false
                if (candidato.tecnicoId == atualId) return@filter false
                if (candidato.reputacao > clube.reputacao + TETO_ACIMA_DO_CLUBE) return@filter false
                // Upgrade claro é exigido só de quem já tem técnico; clube vago pega o que der.
                if (atual != null && candidato.reputacao <= atual.reputacao + MARGEM_UPGRADE) {
                    return@filter false
                }
                when (val empregador = clubeDoTecnico[candidato.tecnicoId]) {
                    null -> true   // livre: qualquer um pode levar
                    else -> {
                        // Assédio: só de um clube claramente menor. É esta linha que
                        // faz a cascata andar sempre para baixo, e por isso terminar.
                        val repEmpregador = reputacaoDoClube[empregador] ?: return@filter false
                        repEmpregador <= clube.reputacao - MARGEM_ASSEDIO
                    }
                }
            }
            if (candidatos.isEmpty()) continue

            val melhor = compareByDescending<TecnicoNoMercado> { it.reputacao }.thenBy { it.tecnicoId }
            val alvo = candidatos.filter { it.nacionalidade == clube.pais }.minWithOrNull(melhor)
                ?: candidatos.minWithOrNull(melhor)
                ?: continue

            // O sorteio só é consumido quando há de fato uma decisão a tomar —
            // assim um teste controla exatamente quais trocas acontecem.
            if (atual != null && sorteio() > PROB_TROCA_POR_UPGRADE) continue

            val de = clubeDoTecnico[alvo.tecnicoId]
            if (de != null) tecnicoDoClube[de] = null          // abre a vaga lá embaixo
            if (atualId != null) clubeDoTecnico[atualId] = null // o substituído volta ao mercado
            tecnicoDoClube[clube.timeId] = alvo.tecnicoId
            clubeDoTecnico[alvo.tecnicoId] = clube.timeId

            movimentos += Contratacao(alvo.tecnicoId, clube.timeId, de)
        }

        return movimentos
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Reputação de fim de temporada
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Quanto a reputação do técnico muda ao fim da temporada.
     *
     * Antes disto a reputação dos técnicos da IA só SUBIA: os únicos ajustes
     * eram o bônus de título e o delta de meta, e esse delta só se aplicava ao
     * técnico do jogador. Em poucas temporadas todo mundo convergia para o topo
     * e os filtros de compatibilidade deixavam de separar qualquer coisa.
     *
     * @param aproveitamento 0..1, da temporada que está encerrando.
     */
    fun deltaDeReputacao(
        aproveitamento: Float,
        foiDemitido: Boolean,
        foiRebaixado: Boolean
    ): Float {
        val porDesempenho = when {
            aproveitamento >= 0.70f -> 3.0f
            aproveitamento >= 0.55f -> 1.5f
            aproveitamento >= 0.40f -> 0.0f
            aproveitamento >= 0.30f -> -1.5f
            else                    -> -3.0f
        }
        val porQueda = if (foiRebaixado) -4.0f else 0.0f
        val porDemissao = if (foiDemitido) -3.0f else 0.0f
        return porDesempenho + porQueda + porDemissao
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Fim de carreira
    // ─────────────────────────────────────────────────────────────────────

    /** Acima desta idade o técnico sempre se aposenta. */
    const val IDADE_APOSENTADORIA_COMPULSORIA = 72

    /** Abaixo desta idade ninguém se aposenta. */
    const val IDADE_APOSENTADORIA_MINIMA = 64

    /**
     * Chance de o técnico encerrar a carreira nesta virada de temporada.
     *
     * Sobe com a idade em vez de ser um corte seco, para que os clubes não
     * percam o comando todos no mesmo ano. O seed tem técnicos de até 71 anos,
     * então as primeiras aposentadorias acontecem já na primeira virada.
     */
    fun probabilidadeDeAposentar(idade: Int): Float = when {
        idade >= IDADE_APOSENTADORIA_COMPULSORIA -> 1.0f
        idade >= IDADE_APOSENTADORIA_MINIMA ->
            (0.08f * (idade - IDADE_APOSENTADORIA_MINIMA + 1)).coerceAtMost(1.0f)
        else -> 0.0f
    }

    fun deveAposentar(idade: Int, sorteio: () -> Float): Boolean {
        val prob = probabilidadeDeAposentar(idade)
        if (prob <= 0f) return false
        if (prob >= 1f) return true
        return sorteio() < prob
    }
}
