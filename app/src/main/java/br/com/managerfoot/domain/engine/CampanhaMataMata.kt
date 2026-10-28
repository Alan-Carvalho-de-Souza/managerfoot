package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// Como a campanha de um clube num torneio de mata-mata é resumida para a UI:
// a fase mais avançada que ele alcançou e, se chegou à decisão, se levantou a
// taça ou ficou com o vice.
//
// Vale para tudo que tem eliminatória: copas nacionais, supercopas de jogo
// único e as continentais (Libertadores, Sul-Americana, Champions, Europa
// League, Intercontinental e Recopa).
//
// Função pura, separada da UI e do banco, porque a regra da decisão tem três
// casos (agregado, pênaltis e final ainda em aberto) e vale testar os três.
// ─────────────────────────────────────────────────────────────────────────────

/** Desfecho de quem chegou à decisão. */
enum class ResultadoFinal { CAMPEAO, VICE }

object CampanhaMataMata {

    /** Nome da fase decisiva, como gravado em PartidaEntity.fase. */
    const val FASE_FINAL = "Final"

    /**
     * Um jogo da campanha, já do ponto de vista do clube analisado: `golsPro` são
     * os dele, independente de ter jogado em casa ou fora.
     */
    data class Jogo(
        val fase: String?,
        val golsPro: Int,
        val golsContra: Int,
        val penaltisPro: Int? = null,
        val penaltisContra: Int? = null
    )

    /**
     * Fase mais avançada alcançada: a fase do último jogo da campanha. Depende dos
     * jogos virem em ordem cronológica, que é como as consultas os devolvem
     * (ORDER BY rodada). Null quando nenhum jogo tem fase — caso das ligas de
     * pontos corridos, que não têm mata-mata.
     */
    fun faseAtingida(jogos: List<Jogo>): String? =
        jogos.lastOrNull { it.fase != null }?.fase

    /**
     * Campeão ou vice, para quem disputou a decisão; null para quem não chegou
     * lá, e também para a final que ainda não terminou — a ida já jogada e a
     * volta pendente dá agregado provisório, que não deve virar troféu na tela.
     *
     * Soma o agregado dos jogos da final (uma partida nas de jogo único, duas nas
     * de ida e volta), que é o mesmo critério de [MotorCampeonato.determinarVencedorTie]
     * — agregado puro, sem peso de gol fora. Empate no agregado vai para os
     * pênaltis do último jogo que os tiver.
     */
    fun resultadoDaFinal(jogos: List<Jogo>): ResultadoFinal? {
        val finais = jogos.filter { it.fase == FASE_FINAL }
        if (finais.isEmpty()) return null

        val pro    = finais.sumOf { it.golsPro }
        val contra = finais.sumOf { it.golsContra }
        if (pro != contra) {
            return if (pro > contra) ResultadoFinal.CAMPEAO else ResultadoFinal.VICE
        }

        // Agregado empatado: decide nos pênaltis. Se não houver disputa gravada, a
        // final ainda não foi decidida.
        val decisao = finais.lastOrNull {
            it.penaltisPro != null && it.penaltisContra != null
        } ?: return null
        val pp = decisao.penaltisPro ?: return null
        val pc = decisao.penaltisContra ?: return null
        return when {
            pp > pc -> ResultadoFinal.CAMPEAO
            pc > pp -> ResultadoFinal.VICE
            else    -> null
        }
    }
}
