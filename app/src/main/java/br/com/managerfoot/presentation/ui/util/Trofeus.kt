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
 * **Como usar:** as competições sem imagem própria apontam para o placeholder
 * vetorial (`ic_trophy_placeholder`). Para dar imagem real a uma delas:
 *
 * 1. Adicione o arquivo `.png` em `res/drawable/` (ex: `ic_trophy_laliga.png`)
 * 2. Mude apenas a constante correspondente abaixo:
 *    `val LA_LIGA: Int = R.drawable.ic_trophy_laliga`
 * 3. Garanta que [resolver] tem um ramo que chega nela — o nome do campeonato
 *    é o que seleciona, e os nomes vêm do rollover em `GameRepository`.
 *
 * O resto do app (ConquistasScreen / TrofeuCard) detecta automaticamente: o
 * `tintable` da [TrofeuInfo] vira `false` sozinho quando o drawable deixa de
 * ser o placeholder, e a imagem passa a renderizar com as cores dela.
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

    // ── Inglaterra ────────────────────────────────────────────
    @DrawableRes val PREMIER_LEAGUE: Int   = R.drawable.ic_trophy_premierleague
    @DrawableRes val EFL_CHAMPIONSHIP: Int = R.drawable.ic_trophy_eflchampionship
    // A FA Cup se chama "Copa da Inglaterra" no rollover; a imagem é a do nome real.
    @DrawableRes val FA_CUP: Int           = R.drawable.ic_trophy_facup

    // ── Espanha ───────────────────────────────────────────────
    @DrawableRes val LA_LIGA: Int     = R.drawable.ic_trophy_laliga
    // Idem: o campeonato se chama "Copa Espanhola", o troféu é a Copa do Rei.
    @DrawableRes val COPA_DO_REI: Int = R.drawable.ic_trophy_copadorei

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

    // ── Continentais ──────────────────────────────────────────
    @DrawableRes val CHAMPIONS_LEAGUE: Int = R.drawable.ic_trophy_championsleague
    @DrawableRes val EUROPA_LEAGUE: Int    = R.drawable.ic_trophy_europaleague
    @DrawableRes val LIBERTADORES: Int     = R.drawable.ic_trophy_libertadores
    @DrawableRes val INTERCONTINENTAL: Int = R.drawable.ic_trophy_intercontinental
    @DrawableRes val SULAMERICANA: Int     = R.drawable.ic_trophy_copasulamericana
    // Sem imagem própria ainda: para usar PNG real, adicione
    // `ic_trophy_supercopauefa.png` em res/drawable/ e troque a referência.
    @DrawableRes val SUPERCOPA_UEFA: Int   = R.drawable.ic_trophy_placeholder

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

    enum class Pais { BRASIL, ARGENTINA, URUGUAI, INGLATERRA, ESPANHA, INTERNACIONAL }

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
            // ── Continentais, antes de tudo ───────────────────────────
            // Precisam vir primeiro porque os ramos abaixo casam por palavras
            // genéricas ("supercopa", "copa") e capturariam estas competições.
            //
            // "champions league" completo, e não só "champions": a EFL
            // Championship contém "champions" dentro de "championship" e seria
            // confundida com a Champions League.
            if (nome.contains("champions league")) {
                return@run TrofeuInfo(CHAMPIONS_LEAGUE, GoldChampion, "Champions League", Pais.INTERNACIONAL)
            }
            if (nome.contains("europa league")) {
                return@run TrofeuInfo(EUROPA_LEAGUE, AmberAccent, "Europa League", Pais.INTERNACIONAL)
            }
            // Cobre também a "Pré-Libertadores", que é a mesma competição.
            // Antes da Libertadores: "Copa Intercontinental" não contém
            // "libertadores", mas contém "copa", que é casado mais abaixo.
            if (nome.contains("intercontinental")) {
                return@run TrofeuInfo(INTERCONTINENTAL, GoldChampion, "Copa Intercontinental", Pais.INTERNACIONAL)
            }
            if (nome.contains("libertadores")) {
                return@run TrofeuInfo(LIBERTADORES, LibertadoresBlue, "Libertadores", Pais.INTERNACIONAL)
            }
            // A Recopa Sul-Americana vem ANTES da Copa Sul-Americana: o nome dela
            // contém "sul-americana" e, sem este ramo, ela era rotulada como "Copa
            // Sul-Americana" — que é outra competição. Mesma colisão de nome que
            // "championship"/"champions" e a das supercopas, logo abaixo.
            if (nome.contains("recopa")) {
                return@run TrofeuInfo(GENERICO, SulAmericanaTeal, "Recopa Sul-Americana", Pais.INTERNACIONAL)
            }
            // Antes do ramo genérico de "copa", que capturaria "Copa
            // Sul-Americana".
            if (nome.contains("sul-americana") || nome.contains("sul americana")) {
                return@run TrofeuInfo(SULAMERICANA, SulAmericanaTeal, "Copa Sul-Americana", Pais.INTERNACIONAL)
            }

            // ── Supercopas ────────────────────────────────────────────
            // O ramo antigo era só `contains("supercopa")` e devolvia SEMPRE
            // "Supercopa Rei" com o troféu brasileiro — então as supercopas da
            // UEFA, Inglaterra, Espanha e Itália todas apareciam como Supercopa
            // Rei. Agora cada uma se identifica antes do caso brasileiro.
            if (nome.contains("supercopa") || nome.contains("super copa")) {
                return@run when {
                    nome.contains("uefa") ->
                        TrofeuInfo(SUPERCOPA_UEFA, GreenElectric, "Supercopa da UEFA", Pais.INTERNACIONAL)
                    nome.contains("inglaterra") ->
                        TrofeuInfo(GENERICO, GreenElectric, "Supercopa da Inglaterra", Pais.INTERNACIONAL)
                    nome.contains("espanha") ->
                        TrofeuInfo(GENERICO, GreenElectric, "Supercopa da Espanha", Pais.INTERNACIONAL)
                    nome.contains("italia") || nome.contains("itália") ->
                        TrofeuInfo(GENERICO, GreenElectric, "Supercopa da Itália", Pais.INTERNACIONAL)
                    else ->
                        TrofeuInfo(SUPERCOPA_REI, GreenElectric, "Supercopa Rei", Pais.BRASIL)
                }
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

            // Copas da Inglaterra e da Espanha. Ficam aqui, depois das copas do
            // Brasil e da Argentina, porque todas casam por "copa" e a ordem é
            // o que separa uma da outra.
            if (nome.contains("copa da inglaterra")) {
                return@run TrofeuInfo(FA_CUP, GoldChampion, "FA Cup", Pais.INGLATERRA)
            }
            if (nome.contains("copa espanhola") || nome.contains("copa do rei")) {
                return@run TrofeuInfo(COPA_DO_REI, GoldChampion, "Copa do Rei", Pais.ESPANHA)
            }

            // Ligas europeias. A Championship vem ANTES da Premier só por
            // clareza — não há colisão entre as duas —, mas as duas precisam
            // vir antes do ramo por divisão, que as mandaria para o genérico.
            if (nome.contains("championship")) {
                return@run TrofeuInfo(EFL_CHAMPIONSHIP, SilverRunnerUp, "EFL Championship", Pais.INGLATERRA)
            }
            if (nome.contains("premier league")) {
                return@run TrofeuInfo(PREMIER_LEAGUE, GoldChampion, "Premier League", Pais.INGLATERRA)
            }
            if (nome.contains("la liga")) {
                return@run TrofeuInfo(LA_LIGA, GoldChampion, "La Liga", Pais.ESPANHA)
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
