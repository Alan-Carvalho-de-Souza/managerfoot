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
     * Fase mais avançada alcançada: a fase do ÚLTIMO jogo da campanha, seja ela
     * qual for. Funciona porque as competições avançam junto com a rodada e as
     * consultas devolvem os jogos em ordem (ORDER BY rodada).
     *
     * **Devolver null significa "caiu na fase de grupos ou na fase de liga"**, não
     * "sem fase": os geradores gravam as partidas de grupo (Libertadores,
     * Sul-Americana) e de fase de liga (Champions, Europa League) com `fase = null`.
     * Qual das duas foi, só o formato do campeonato diz — quem exibe decide o
     * rótulo. Para uma liga de pontos corridos, que não passa por aqui, também
     * seria null.
     *
     * Antes isto era `lastOrNull { it.fase != null }`, que pulava justamente as
     * partidas de grupo e produzia o erro oposto: um clube que passou pela
     * Pré-Libertadores ("Fase 1".."Fase 3", gravadas DENTRO do mesmo campeonato) e
     * caiu na fase de grupos aparecia como eliminado na "Fase 3" — como se nem
     * tivesse chegado aos grupos. Mesmo problema na Sul-Americana, cuja fase
     * prévia se chama "Primeira Fase".
     */
    fun faseAtingida(jogos: List<Jogo>): String? =
        jogos.lastOrNull()?.fase

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
