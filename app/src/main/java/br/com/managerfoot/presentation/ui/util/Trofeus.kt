package br.com.managerfoot.presentation.ui.util

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import br.com.managerfoot.R
import br.com.managerfoot.presentation.ui.theme.AmberAccent
import br.com.managerfoot.presentation.ui.theme.BronzePlace
import br.com.managerfoot.presentation.ui.theme.GoldChampion
import br.com.managerfoot.presentation.ui.theme.GreenElectric
import br.com.managerfoot.presentation.ui.theme.LibertadoresBlue
import br.com.managerfoot.presentation.ui.theme.SilverRunnerUp
import br.com.managerfoot.presentation.ui.theme.SulAmericanaTeal

/**
 * Mapeamento de campeonato → drawable de troféu + tier (cor de destaque).
 *
 * **Como usar:** todas as constantes apontam atualmente para o mesmo
 * placeholder vetorial (`ic_trophy_placeholder`). Para substituir por
 * PNG real de um campeonato específico:
 *
 * 1. Adicione o arquivo `.png` em `res/drawable/` (ex: `trofeu_serie_a.png`)
 * 2. Mude apenas a constante correspondente abaixo:
 *    `val SERIE_A: Int = R.drawable.trofeu_serie_a`
 *
 * O resto do app (ConquistasScreen / TrofeuCard) detecta automaticamente.
 *
 * **Tier:** define a cor de destaque (gold para divisão de elite, prata
 * para segunda divisão, etc). Independente do drawable.
 */
object Trofeus {

    // ── Brasil ────────────────────────────────────────────────
    @DrawableRes val SERIE_A: Int      = R.drawable.ic_trophy_brasileirao
    @DrawableRes val SERIE_B: Int      = R.drawable.ic_trophy_brasileirao_serieb
    @DrawableRes val SERIE_C: Int      = R.drawable.ic_trophy_brasileirao_seriec
    @DrawableRes val SERIE_D: Int      = R.drawable.ic_trophy_brasileirao_seried
    @DrawableRes val COPA_BRASIL: Int  = R.drawable.ic_trophy_copadobrasil
    @DrawableRes val SUPERCOPA_REI: Int = R.drawable.ic_trophy_supercoparei

    // ── Argentina ─────────────────────────────────────────────
    @DrawableRes val PRIMERA_ARG: Int  = R.drawable.ic_trophy_placeholder
    @DrawableRes val SEGUNDA_ARG: Int  = R.drawable.ic_trophy_placeholder
    @DrawableRes val APERTURA_ARG: Int = R.drawable.ic_trophy_placeholder
    @DrawableRes val CLAUSURA_ARG: Int = R.drawable.ic_trophy_placeholder
    @DrawableRes val COPA_ARGENTINA: Int = R.drawable.ic_trophy_placeholder
    // Troféu dos Campeões — Apertura champion × Clausura champion (mesmo ano).
    // Para usar PNG real: adicione `trofeu_campeoes.png` em res/drawable/ e
    // troque a referência abaixo para `R.drawable.trofeu_campeoes`.
    @DrawableRes val TROFEU_CAMPEOES_ARG: Int = R.drawable.ic_trophy_placeholder

    // ── Uruguai ───────────────────────────────────────────────
    @DrawableRes val PRIMERA_URU: Int    = R.drawable.ic_trophy_placeholder
    @DrawableRes val SEGUNDA_URU: Int    = R.drawable.ic_trophy_placeholder
    @DrawableRes val APERTURA_URU: Int   = R.drawable.ic_trophy_placeholder
    @DrawableRes val INTERMEDIO_URU: Int = R.drawable.ic_trophy_placeholder
    @DrawableRes val CLAUSURA_URU: Int   = R.drawable.ic_trophy_placeholder
    @DrawableRes val COMPETENCIA_URU: Int = R.drawable.ic_trophy_placeholder

    // ── Genérico (fallback) ───────────────────────────────────
    @DrawableRes val GENERICO: Int = R.drawable.ic_trophy_placeholder

    // ──────────────────────────────────────────────────────────
    //  Resolução automática a partir do nome do campeonato + divisão
    //  Os campos `nomeCampeonato` e `divisao` vêm do HallDaFamaEntity.
    // ──────────────────────────────────────────────────────────

    data class TrofeuInfo(
        @DrawableRes val drawable: Int,
        val tier: Color,
        val nomeCurto: String,
        val pais: Pais,
        /** true quando o drawable é o placeholder vetorial (mono-cor, deve receber tint
         *  com a cor do tier). false para PNGs/vectors customizados que devem renderizar
         *  com suas cores originais. Calculado automaticamente em [resolver]. */
        val tintable: Boolean = true
    )

    enum class Pais { BRASIL, ARGENTINA, URUGUAI, INTERNACIONAL }

    /**
     * Identifica o troféu a partir do nome do campeonato e (opcionalmente)
     * divisão. A heurística cobre os campeonatos atualmente cadastrados.
     *
     * O campo `tintable` da [TrofeuInfo] é calculado automaticamente: vale
     * `true` quando o drawable retornado ainda é o placeholder vetorial
     * (mono-cor, precisa de tint), e `false` quando é uma imagem customizada
     * que deve preservar suas cores originais.
     */
    fun resolver(nomeCampeonato: String, divisao: Int = 0): TrofeuInfo {
        val nome = nomeCampeonato.lowercase()

        val raw: TrofeuInfo = run {
            // Supercopa (especial — verde elétrico)
            if (nome.contains("supercopa") || nome.contains("super copa")) {
                return@run TrofeuInfo(SUPERCOPA_REI, GreenElectric, "Supercopa Rei", Pais.BRASIL)
            }

            // Troféu dos Campeões (ARG) — campeão Apertura x campeão Clausura
            if (nome.contains("troféu") || nome.contains("trofeu")) {
                if (nome.contains("campe")) {
                    return@run TrofeuInfo(TROFEU_CAMPEOES_ARG, LibertadoresBlue, "Troféu dos Campeões", Pais.ARGENTINA)
                }
            }

            // Copas nacionais
            if (nome.contains("copa") && nome.contains("argentina")) {
                return@run TrofeuInfo(COPA_ARGENTINA, LibertadoresBlue, "Copa Argentina", Pais.ARGENTINA)
            }
            if (nome.contains("copa do brasil") || (nome.contains("copa") && nome.contains("brasil"))) {
                return@run TrofeuInfo(COPA_BRASIL, GoldChampion, "Copa do Brasil", Pais.BRASIL)
            }

            // Argentina — Apertura/Clausura específicos
            if (nome.contains("apertura") && nome.contains("argentina")) {
                return@run TrofeuInfo(APERTURA_ARG, GoldChampion, "Apertura ARG", Pais.ARGENTINA)
            }
            if (nome.contains("clausura") && nome.contains("argentina")) {
                return@run TrofeuInfo(CLAUSURA_ARG, GoldChampion, "Clausura ARG", Pais.ARGENTINA)
            }

            // Uruguai — fases específicas
            if (nome.contains("apertura")) {
                return@run TrofeuInfo(APERTURA_URU, GoldChampion, "Apertura URU", Pais.URUGUAI)
            }
            if (nome.contains("intermédio") || nome.contains("intermedio")) {
                return@run TrofeuInfo(INTERMEDIO_URU, AmberAccent, "Intermédio URU", Pais.URUGUAI)
            }
            if (nome.contains("clausura")) {
                return@run TrofeuInfo(CLAUSURA_URU, GoldChampion, "Clausura URU", Pais.URUGUAI)
            }
            if (nome.contains("competência") || nome.contains("competencia")) {
                return@run TrofeuInfo(COMPETENCIA_URU, SulAmericanaTeal, "Competência URU", Pais.URUGUAI)
            }

            // Por divisão
            when (divisao) {
                1 -> TrofeuInfo(SERIE_A, GoldChampion, "Série A", Pais.BRASIL)
                2 -> TrofeuInfo(SERIE_B, SilverRunnerUp, "Série B", Pais.BRASIL)
                3 -> TrofeuInfo(SERIE_C, BronzePlace, "Série C", Pais.BRASIL)
                4 -> TrofeuInfo(SERIE_D, AmberAccent, "Série D", Pais.BRASIL)
                5 -> TrofeuInfo(PRIMERA_ARG, GoldChampion, "Primera ARG", Pais.ARGENTINA)
                6 -> TrofeuInfo(SEGUNDA_ARG, SilverRunnerUp, "Segunda ARG", Pais.ARGENTINA)
                9 -> TrofeuInfo(PRIMERA_URU, GoldChampion, "Primera URU", Pais.URUGUAI)
                10 -> TrofeuInfo(SEGUNDA_URU, SilverRunnerUp, "Segunda URU", Pais.URUGUAI)
                else -> TrofeuInfo(GENERICO, GoldChampion, nomeCampeonato.take(20), Pais.INTERNACIONAL)
            }
        }

        // Auto-detecta tintable: só aplica tint se ainda for o placeholder vetorial.
        return raw.copy(tintable = raw.drawable == R.drawable.ic_trophy_placeholder)
    }
}
