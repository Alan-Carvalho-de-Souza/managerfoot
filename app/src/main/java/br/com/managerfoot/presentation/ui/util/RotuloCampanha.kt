package br.com.managerfoot.presentation.ui.util

import br.com.managerfoot.domain.engine.ResultadoFinal

// ─────────────────────────────────────────────────────────────────────────────
// Como a campanha de um clube num torneio é resumida em uma palavra na tela:
// "Campeão", "Vice", a fase em que caiu, ou a fase de grupos/liga.
//
// Compartilhado entre a tela de estatísticas e a aba de campanhas do clube, que
// mostram a mesma informação e vinham divergindo: uma sabia nomear só três
// competições e a outra exibia a fase crua e tratava as continentais como liga,
// chegando a mostrar "0º" para a Libertadores.
// ─────────────────────────────────────────────────────────────────────────────
object RotuloCampanha {

    /**
     * Tipos de campeonato com fase eliminatória, como o enum TipoCampeonato os
     * grava: copas nacionais, supercopas de jogo único e as continentais
     * (Libertadores, Sul-Americana, Recopa, Champions, Europa League e
     * Intercontinental, todas CONTINENTAL).
     */
    val TIPOS_MATA_MATA = setOf("COPA_NACIONAL", "CONTINENTAL", "SUPERCOPA")

    fun ehMataMata(tipo: String): Boolean = tipo in TIPOS_MATA_MATA

    /** Nome curto de uma fase, como ela é gravada em PartidaEntity.fase. */
    fun daFase(fase: String): String = when (fase) {
        "Primeira Fase" -> "1ª Fase"
        "Segunda Fase"  -> "2ª Fase"
        "Terceira Fase" -> "3ª Fase"
        "Quarta Fase"   -> "4ª Fase"
        // As três fases prévias da Libertadores são gravadas como "Fase 1".."Fase 3"
        // DENTRO do próprio campeonato. Sem o prefixo, "Fase 1" não diz que é a
        // Pré-Libertadores e se confunde com a 1ª fase de uma copa nacional.
        "Fase 1"        -> "Pré · 1ª Fase"
        "Fase 2"        -> "Pré · 2ª Fase"
        "Fase 3"        -> "Pré · 3ª Fase"
        "Play-off"      -> "Play-off"   // Champions e Europa League
        "Playoffs"      -> "Playoffs"   // Sul-Americana, entre os grupos e as oitavas
        "Oitavas"       -> "Oitavas"
        "Quartas"       -> "Quartas"
        "Semi"          -> "Semifinal"
        "Final"         -> "Final"
        else            -> fase
    }

    /**
     * Fase em que o clube parou, ou null quando não há o que dizer.
     *
     * Quem cai na fase de grupos (Libertadores, Sul-Americana) ou na fase de liga
     * (Champions, Europa League) não tem fase nas partidas: os geradores gravam
     * `fase = null` nelas. Então `faseAtingida == null` significa "eliminado
     * nessa fase", e o FORMATO do campeonato é o que diz qual das duas foi — não
     * o nome da competição.
     */
    fun daCampanha(faseAtingida: String?, formato: String): String? = when {
        faseAtingida != null               -> daFase(faseAtingida)
        formato == "GRUPOS_E_MATA_MATA"    -> "Fase de Grupos"
        formato == "FASE_LIGA_E_MATA_MATA" -> "Fase de Liga"
        else                               -> null
    }

    /**
     * O resumo completo: quem decidiu o título aparece como campeão ou vice, e os
     * demais pela fase em que caíram.
     */
    fun resumo(
        resultadoFinal: ResultadoFinal?,
        faseAtingida: String?,
        formato: String
    ): String? = when (resultadoFinal) {
        ResultadoFinal.CAMPEAO -> "Campeão"
        ResultadoFinal.VICE    -> "Vice"
        null                   -> daCampanha(faseAtingida, formato)
    }
}
