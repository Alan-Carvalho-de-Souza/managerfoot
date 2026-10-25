package br.com.managerfoot.data.datasource

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "managerfoot_prefs")

data class SaveState(
    val timeIdJogador: Int,          // -1 = sem save
    val temporadaId: Int,
    val campeonatoId: Int,           // ID do campeonato ativo do jogador
    val campeonatoAId: Int,          // ID do campeonato Série A
    val campeonatoBId: Int,          // ID do campeonato Série B
    val campeonatoCId: Int,          // ID do campeonato Série C
    val campeonatoDId: Int,          // ID do campeonato Série D
    val campeonatoArgAId: Int,        // ID da Primera División Argentina – Apertura
    val campeonatoArgBId: Int,        // ID da Segunda División Argentina
    val campeonatoArgClausuraId: Int, // ID da Primera División Argentina – Clausura
    val campeonatoUruAperturaId: Int, // ID do Torneio Apertura Uruguaio
    val campeonatoUruClausuraId: Int, // ID do Torneio Clausura Uruguaio
    val campeonatoUruIntermedId: Int, // ID do Torneio Intermediário Uruguaio
    val campeonatoUruBId: Int,        // ID da Segunda Divisão Uruguaia
    val campeonatoUruBCompetId: Int,  // ID do Torneo Competencia – Segunda División
    val campeonatoPremierId: Int,     // ID da Premier League inglesa (divisao 11)
    val campeonatoChampionshipId: Int, // ID da EFL Championship inglesa (divisao 12)
    val campeonatoLaLigaId: Int,       // ID da La Liga espanhola (divisao 13)
    val campeonatoSerieAId: Int,       // ID da Serie A italiana (divisao 14)
    val copaId: Int,                  // ID da Copa do Brasil ativa
    val copaArgId: Int,               // ID da Copa Argentina ativa (-1 se não houver)
    val copaInglaterraId: Int,        // ID da Copa da Inglaterra ativa (-1 se não houver)
    val copaEspanholaId: Int,         // ID da Copa Espanhola (Copa do Rei) ativa (-1 se não houver)
    val copaItalianaId: Int,          // ID da Copa Italiana (Coppa Italia) ativa (-1 se não houver)
    val championsId: Int,             // ID da UEFA Champions League ativa (-1 se não houver)
    val europaLeagueId: Int,          // ID da UEFA Europa League ativa (-1 se não houver)
    val libertadoresId: Int,          // ID da CONMEBOL Libertadores ativa (-1 se não houver)
    val supercopaId: Int,            // ID da Supercopa Rei ativa (-1 se não houver)
    val supercopaInglaterraId: Int,  // ID da Supercopa da Inglaterra ativa (-1 se não houver)
    val supercopaEspanholaId: Int,   // ID da Supercopa da Espanha ativa (-1 se não houver)
    val supercopaItalianaId: Int,    // ID da Supercopa da Itália ativa (-1 se não houver)
    val supercopaUefaId: Int,        // ID da Supercopa da UEFA ativa (-1 se não houver)
    val intercontinentalId: Int,     // ID da Copa Intercontinental ativa (-1 se não houver)
    val anoAtual: Int,
    val mesAtual: Int,               // 1–12
    val jogoInicializado: Boolean,
    val patrocinadorValorAnual: Long, // 0 = patrocinador não escolhido na temporada
    val patrocinadorTipo: Int,        // 0 = nenhum, 1-3 = tier escolhido
    val patrocinadorPreCreditado: Long, // valor já adiantado no saldo do mês corrente
    val onboardingConcluido: Boolean = false, // true depois que o usuário viu o tutorial inicial
    // ── Amistosos de entressafra ────────────────────────────────────────────
    val campeonatoAmistososId: Int = -1,   // container único das partidas de amistoso
    val amistososDez: Int = 0,             // amistosos jogados na janela de dezembro (0-2)
    val amistososJan: Int = 0,             // amistosos jogados na janela de janeiro (0-2)
    val amistosoJaneiroAtivo: Boolean = false // true logo após a virada, até jogar o 1º oficial
)

@Singleton
class GameDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_TIME_ID        = intPreferencesKey("time_id_jogador")
        val KEY_TEMPORADA_ID   = intPreferencesKey("temporada_id")
        val KEY_CAMPEONATO_ID  = intPreferencesKey("campeonato_id")
        val KEY_CAMPEONATO_A_ID = intPreferencesKey("campeonato_a_id")
        val KEY_CAMPEONATO_B_ID = intPreferencesKey("campeonato_b_id")
        val KEY_CAMPEONATO_C_ID = intPreferencesKey("campeonato_c_id")
        val KEY_CAMPEONATO_D_ID = intPreferencesKey("campeonato_d_id")
        val KEY_CAMPEONATO_ARG_A_ID       = intPreferencesKey("campeonato_arg_a_id")
        val KEY_CAMPEONATO_ARG_B_ID       = intPreferencesKey("campeonato_arg_b_id")
        val KEY_CAMPEONATO_ARG_CLAUSURA_ID  = intPreferencesKey("campeonato_arg_clausura_id")
        val KEY_CAMPEONATO_URU_APERTURA_ID  = intPreferencesKey("campeonato_uru_apertura_id")
        val KEY_CAMPEONATO_URU_CLAUSURA_ID  = intPreferencesKey("campeonato_uru_clausura_id")
        val KEY_CAMPEONATO_URU_INTERM_ID    = intPreferencesKey("campeonato_uru_interm_id")
        val KEY_CAMPEONATO_URU_B_ID         = intPreferencesKey("campeonato_uru_b_id")
        val KEY_CAMPEONATO_URU_B_COMPET_ID  = intPreferencesKey("campeonato_uru_b_compet_id")
        val KEY_CAMPEONATO_PREMIER_ID       = intPreferencesKey("campeonato_premier_id")
        val KEY_CAMPEONATO_CHAMPIONSHIP_ID  = intPreferencesKey("campeonato_championship_id")
        val KEY_CAMPEONATO_LA_LIGA_ID       = intPreferencesKey("campeonato_la_liga_id")
        val KEY_CAMPEONATO_SERIE_A_ID       = intPreferencesKey("campeonato_serie_a_id")
        val KEY_COPA_ID         = intPreferencesKey("copa_id")
        val KEY_COPA_ARG_ID     = intPreferencesKey("copa_arg_id")
        val KEY_COPA_ING_ID     = intPreferencesKey("copa_inglaterra_id")
        val KEY_COPA_ESP_ID     = intPreferencesKey("copa_espanhola_id")
        val KEY_COPA_ITA_ID     = intPreferencesKey("copa_italiana_id")
        val KEY_CHAMPIONS_ID    = intPreferencesKey("champions_id")
        val KEY_EUROPA_LEAGUE_ID = intPreferencesKey("europa_league_id")
        val KEY_LIBERTADORES_ID  = intPreferencesKey("libertadores_id")
        val KEY_SUPERCOPA_ID    = intPreferencesKey("supercopa_id")
        val KEY_SUPERCOPA_ING_ID = intPreferencesKey("supercopa_inglaterra_id")
        val KEY_SUPERCOPA_ESP_ID = intPreferencesKey("supercopa_espanhola_id")
        val KEY_SUPERCOPA_ITA_ID = intPreferencesKey("supercopa_italiana_id")
        val KEY_SUPERCOPA_UEFA_ID = intPreferencesKey("supercopa_uefa_id")
        val KEY_INTERCONTINENTAL_ID = intPreferencesKey("intercontinental_id")
        val KEY_ANO            = intPreferencesKey("ano_atual")
        val KEY_MES            = intPreferencesKey("mes_atual")
        val KEY_INICIALIZADO   = booleanPreferencesKey("jogo_inicializado")
        val KEY_PATROCINADOR_VALOR        = longPreferencesKey("patrocinador_valor_anual")
        val KEY_PATROCINADOR_TIPO         = intPreferencesKey("patrocinador_tipo")
        val KEY_PATROCINADOR_PRE_CREDITADO = longPreferencesKey("patrocinador_pre_creditado")
        val KEY_ONBOARDING_CONCLUIDO       = booleanPreferencesKey("onboarding_concluido")
        val KEY_AMISTOSO_CAMP_ID           = intPreferencesKey("amistoso_camp_id")
        val KEY_AMISTOSOS_DEZ              = intPreferencesKey("amistosos_dez")
        val KEY_AMISTOSOS_JAN             = intPreferencesKey("amistosos_jan")
        val KEY_AMISTOSO_JANEIRO_ATIVO     = booleanPreferencesKey("amistoso_janeiro_ativo")
    }

    val saveState: Flow<SaveState> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            SaveState(
                timeIdJogador     = prefs[KEY_TIME_ID]         ?: -1,
                temporadaId       = prefs[KEY_TEMPORADA_ID]    ?: -1,
                campeonatoId      = prefs[KEY_CAMPEONATO_ID]   ?: -1,
                campeonatoAId     = prefs[KEY_CAMPEONATO_A_ID] ?: -1,
                campeonatoBId     = prefs[KEY_CAMPEONATO_B_ID] ?: -1,
                campeonatoCId     = prefs[KEY_CAMPEONATO_C_ID] ?: -1,
                campeonatoDId     = prefs[KEY_CAMPEONATO_D_ID] ?: -1,
                campeonatoArgAId        = prefs[KEY_CAMPEONATO_ARG_A_ID]       ?: -1,
                campeonatoArgBId        = prefs[KEY_CAMPEONATO_ARG_B_ID]       ?: -1,
                campeonatoArgClausuraId = prefs[KEY_CAMPEONATO_ARG_CLAUSURA_ID] ?: -1,
                campeonatoUruAperturaId = prefs[KEY_CAMPEONATO_URU_APERTURA_ID] ?: -1,
                campeonatoUruClausuraId = prefs[KEY_CAMPEONATO_URU_CLAUSURA_ID] ?: -1,
                campeonatoUruIntermedId = prefs[KEY_CAMPEONATO_URU_INTERM_ID]   ?: -1,
                campeonatoUruBId        = prefs[KEY_CAMPEONATO_URU_B_ID]        ?: -1,
                campeonatoUruBCompetId  = prefs[KEY_CAMPEONATO_URU_B_COMPET_ID]  ?: -1,
                campeonatoPremierId     = prefs[KEY_CAMPEONATO_PREMIER_ID]       ?: -1,
                campeonatoChampionshipId = prefs[KEY_CAMPEONATO_CHAMPIONSHIP_ID]  ?: -1,
                campeonatoLaLigaId       = prefs[KEY_CAMPEONATO_LA_LIGA_ID]       ?: -1,
                campeonatoSerieAId       = prefs[KEY_CAMPEONATO_SERIE_A_ID]       ?: -1,
                copaId            = prefs[KEY_COPA_ID]          ?: -1,
                copaArgId         = prefs[KEY_COPA_ARG_ID]      ?: -1,
                copaInglaterraId  = prefs[KEY_COPA_ING_ID]      ?: -1,
                copaEspanholaId   = prefs[KEY_COPA_ESP_ID]      ?: -1,
                copaItalianaId    = prefs[KEY_COPA_ITA_ID]      ?: -1,
                championsId       = prefs[KEY_CHAMPIONS_ID]     ?: -1,
                europaLeagueId    = prefs[KEY_EUROPA_LEAGUE_ID] ?: -1,
                libertadoresId    = prefs[KEY_LIBERTADORES_ID]  ?: -1,
                supercopaId       = prefs[KEY_SUPERCOPA_ID]    ?: -1,
                supercopaInglaterraId = prefs[KEY_SUPERCOPA_ING_ID] ?: -1,
                supercopaEspanholaId  = prefs[KEY_SUPERCOPA_ESP_ID] ?: -1,
                supercopaItalianaId   = prefs[KEY_SUPERCOPA_ITA_ID] ?: -1,
                supercopaUefaId       = prefs[KEY_SUPERCOPA_UEFA_ID] ?: -1,
                intercontinentalId    = prefs[KEY_INTERCONTINENTAL_ID] ?: -1,
                anoAtual          = prefs[KEY_ANO]             ?: 2026,
                mesAtual          = prefs[KEY_MES]             ?: 1,
                jogoInicializado  = prefs[KEY_INICIALIZADO]    ?: false,
                patrocinadorValorAnual    = prefs[KEY_PATROCINADOR_VALOR]         ?: 0L,
                patrocinadorTipo          = prefs[KEY_PATROCINADOR_TIPO]          ?: 0,
                patrocinadorPreCreditado  = prefs[KEY_PATROCINADOR_PRE_CREDITADO] ?: 0L,
                onboardingConcluido       = prefs[KEY_ONBOARDING_CONCLUIDO]       ?: false,
                campeonatoAmistososId     = prefs[KEY_AMISTOSO_CAMP_ID]           ?: -1,
                amistososDez              = prefs[KEY_AMISTOSOS_DEZ]              ?: 0,
                amistososJan              = prefs[KEY_AMISTOSOS_JAN]              ?: 0,
                amistosoJaneiroAtivo      = prefs[KEY_AMISTOSO_JANEIRO_ATIVO]     ?: false
            )
        }

    suspend fun salvarNovoJogo(
        timeId: Int, temporadaId: Int, campeonatoId: Int,
        campeonatoAId: Int, campeonatoBId: Int, campeonatoCId: Int, campeonatoDId: Int,
        campeonatoArgAId: Int, campeonatoArgBId: Int = -1,
        campeonatoArgClausuraId: Int = -1,
        campeonatoUruAperturaId: Int = -1, campeonatoUruClausuraId: Int = -1,
        campeonatoUruIntermedId: Int = -1, campeonatoUruBId: Int = -1,
        campeonatoUruBCompetId: Int = -1, campeonatoPremierId: Int = -1,
        campeonatoChampionshipId: Int = -1,
        campeonatoLaLigaId: Int = -1,
        campeonatoSerieAId: Int = -1,
        copaId: Int, copaArgId: Int = -1, copaInglaterraId: Int = -1,
        copaEspanholaId: Int = -1, copaItalianaId: Int = -1,
        championsId: Int = -1,
        europaLeagueId: Int = -1,
        libertadoresId: Int = -1,
        supercopaId: Int = -1,
        supercopaInglaterraId: Int = -1, supercopaEspanholaId: Int = -1, supercopaItalianaId: Int = -1,
        supercopaUefaId: Int = -1,
        intercontinentalId: Int = -1,
        ano: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TIME_ID]         = timeId
            prefs[KEY_TEMPORADA_ID]    = temporadaId
            prefs[KEY_CAMPEONATO_ID]   = campeonatoId
            prefs[KEY_CAMPEONATO_A_ID] = campeonatoAId
            prefs[KEY_CAMPEONATO_B_ID] = campeonatoBId
            prefs[KEY_CAMPEONATO_C_ID] = campeonatoCId
            prefs[KEY_CAMPEONATO_D_ID] = campeonatoDId
            prefs[KEY_CAMPEONATO_ARG_A_ID]        = campeonatoArgAId
            prefs[KEY_CAMPEONATO_ARG_B_ID]        = campeonatoArgBId
            prefs[KEY_CAMPEONATO_ARG_CLAUSURA_ID] = campeonatoArgClausuraId
            prefs[KEY_CAMPEONATO_URU_APERTURA_ID] = campeonatoUruAperturaId
            prefs[KEY_CAMPEONATO_URU_CLAUSURA_ID] = campeonatoUruClausuraId
            prefs[KEY_CAMPEONATO_URU_INTERM_ID]   = campeonatoUruIntermedId
            prefs[KEY_CAMPEONATO_URU_B_ID]        = campeonatoUruBId
            prefs[KEY_CAMPEONATO_URU_B_COMPET_ID] = campeonatoUruBCompetId
            prefs[KEY_CAMPEONATO_PREMIER_ID]      = campeonatoPremierId
            prefs[KEY_CAMPEONATO_CHAMPIONSHIP_ID] = campeonatoChampionshipId
            prefs[KEY_CAMPEONATO_LA_LIGA_ID]      = campeonatoLaLigaId
            prefs[KEY_CAMPEONATO_SERIE_A_ID]      = campeonatoSerieAId
            prefs[KEY_COPA_ID]         = copaId
            prefs[KEY_COPA_ARG_ID]     = copaArgId
            prefs[KEY_COPA_ING_ID]     = copaInglaterraId
            prefs[KEY_COPA_ESP_ID]     = copaEspanholaId
            prefs[KEY_COPA_ITA_ID]     = copaItalianaId
            prefs[KEY_CHAMPIONS_ID]    = championsId
            prefs[KEY_EUROPA_LEAGUE_ID] = europaLeagueId
            prefs[KEY_LIBERTADORES_ID]  = libertadoresId
            prefs[KEY_SUPERCOPA_ID]    = supercopaId
            prefs[KEY_SUPERCOPA_ING_ID] = supercopaInglaterraId
            prefs[KEY_SUPERCOPA_ESP_ID] = supercopaEspanholaId
            prefs[KEY_SUPERCOPA_ITA_ID] = supercopaItalianaId
            prefs[KEY_SUPERCOPA_UEFA_ID] = supercopaUefaId
            prefs[KEY_INTERCONTINENTAL_ID] = intercontinentalId
            prefs[KEY_ANO]             = ano
            prefs[KEY_MES]             = 1
            prefs[KEY_INICIALIZADO]    = true
        }
    }

    suspend fun avancarMes() {
        context.dataStore.edit { prefs ->
            val mesAtual = prefs[KEY_MES] ?: 1
            val anoAtual = prefs[KEY_ANO] ?: 2025
            if (mesAtual == 12) {
                prefs[KEY_MES] = 1
                prefs[KEY_ANO] = anoAtual + 1
            } else {
                prefs[KEY_MES] = mesAtual + 1
            }
            // Reseta o adiantamento ao avançar para o próximo mês
            prefs[KEY_PATROCINADOR_PRE_CREDITADO] = 0L
        }
    }

    suspend fun marcarPatrocinioPreCreditado(valorMensal: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PATROCINADOR_PRE_CREDITADO] = valorMensal
        }
    }

    suspend fun salvarNovaTemporada(
        campeonatoId: Int, campeonatoAId: Int, campeonatoBId: Int,
        campeonatoCId: Int, campeonatoDId: Int, campeonatoArgAId: Int,
        campeonatoArgBId: Int = -1, campeonatoArgClausuraId: Int = -1,
        campeonatoUruAperturaId: Int = -1, campeonatoUruClausuraId: Int = -1,
        campeonatoUruIntermedId: Int = -1, campeonatoUruBId: Int = -1,
        campeonatoUruBCompetId: Int = -1, campeonatoPremierId: Int = -1,
        campeonatoChampionshipId: Int = -1,
        campeonatoLaLigaId: Int = -1,
        campeonatoSerieAId: Int = -1,
        copaId: Int, copaArgId: Int = -1, copaInglaterraId: Int = -1,
        copaEspanholaId: Int = -1, copaItalianaId: Int = -1,
        championsId: Int = -1,
        europaLeagueId: Int = -1,
        libertadoresId: Int = -1,
        supercopaId: Int = -1,
        supercopaInglaterraId: Int = -1, supercopaEspanholaId: Int = -1, supercopaItalianaId: Int = -1,
        supercopaUefaId: Int = -1,
        intercontinentalId: Int = -1,
        temporadaId: Int, ano: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CAMPEONATO_ID]   = campeonatoId
            prefs[KEY_CAMPEONATO_A_ID] = campeonatoAId
            prefs[KEY_CAMPEONATO_B_ID] = campeonatoBId
            prefs[KEY_CAMPEONATO_C_ID] = campeonatoCId
            prefs[KEY_CAMPEONATO_D_ID] = campeonatoDId
            prefs[KEY_CAMPEONATO_ARG_A_ID]        = campeonatoArgAId
            prefs[KEY_CAMPEONATO_ARG_B_ID]        = campeonatoArgBId
            prefs[KEY_CAMPEONATO_ARG_CLAUSURA_ID] = campeonatoArgClausuraId
            prefs[KEY_CAMPEONATO_URU_APERTURA_ID] = campeonatoUruAperturaId
            prefs[KEY_CAMPEONATO_URU_CLAUSURA_ID] = campeonatoUruClausuraId
            prefs[KEY_CAMPEONATO_URU_INTERM_ID]   = campeonatoUruIntermedId
            prefs[KEY_CAMPEONATO_URU_B_ID]        = campeonatoUruBId
            prefs[KEY_CAMPEONATO_URU_B_COMPET_ID] = campeonatoUruBCompetId
            prefs[KEY_CAMPEONATO_PREMIER_ID]      = campeonatoPremierId
            prefs[KEY_CAMPEONATO_CHAMPIONSHIP_ID] = campeonatoChampionshipId
            prefs[KEY_CAMPEONATO_LA_LIGA_ID]      = campeonatoLaLigaId
            prefs[KEY_CAMPEONATO_SERIE_A_ID]      = campeonatoSerieAId
            prefs[KEY_COPA_ID]         = copaId
            prefs[KEY_COPA_ARG_ID]     = copaArgId
            prefs[KEY_COPA_ING_ID]     = copaInglaterraId
            prefs[KEY_COPA_ESP_ID]     = copaEspanholaId
            prefs[KEY_COPA_ITA_ID]     = copaItalianaId
            prefs[KEY_CHAMPIONS_ID]    = championsId
            prefs[KEY_EUROPA_LEAGUE_ID] = europaLeagueId
            prefs[KEY_LIBERTADORES_ID]  = libertadoresId
            prefs[KEY_SUPERCOPA_ID]    = supercopaId
            prefs[KEY_SUPERCOPA_ING_ID] = supercopaInglaterraId
            prefs[KEY_SUPERCOPA_ESP_ID] = supercopaEspanholaId
            prefs[KEY_SUPERCOPA_ITA_ID] = supercopaItalianaId
            prefs[KEY_SUPERCOPA_UEFA_ID] = supercopaUefaId
            prefs[KEY_INTERCONTINENTAL_ID] = intercontinentalId
            prefs[KEY_TEMPORADA_ID]    = temporadaId
            prefs[KEY_ANO]             = ano
            prefs[KEY_MES]             = 1
            // Reseta patrocinador — usuário deve escolher um novo para cada temporada
            prefs[KEY_PATROCINADOR_VALOR]         = 0L
            prefs[KEY_PATROCINADOR_TIPO]          = 0
            prefs[KEY_PATROCINADOR_PRE_CREDITADO] = 0L
            // Amistosos: nova entressafra — container novo, zera contadores e abre janeiro
            prefs[KEY_AMISTOSO_CAMP_ID]           = -1
            prefs[KEY_AMISTOSOS_DEZ]              = 0
            prefs[KEY_AMISTOSOS_JAN]             = 0
            prefs[KEY_AMISTOSO_JANEIRO_ATIVO]     = true
        }
    }

    /** Cria/atualiza o id do container de amistosos (criado sob demanda). */
    /**
     * Guarda o id da Copa Intercontinental. Diferente das outras competições, ela
     * nasce NO MEIO da temporada — quando as finais da Champions e da Libertadores
     * já definiram os dois finalistas —, então não dá para gravá-la junto com as
     * demais no início da temporada.
     */
    suspend fun salvarIntercontinentalId(id: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_INTERCONTINENTAL_ID] = id }
    }

    suspend fun salvarAmistosoCampId(id: Int) {
        context.dataStore.edit { it[KEY_AMISTOSO_CAMP_ID] = id }
    }

    /** Registra um amistoso jogado na janela indicada (0=dezembro, 1=janeiro). */
    suspend fun registrarAmistosoJogado(janeiro: Boolean) {
        context.dataStore.edit { prefs ->
            if (janeiro) prefs[KEY_AMISTOSOS_JAN] = (prefs[KEY_AMISTOSOS_JAN] ?: 0) + 1
            else         prefs[KEY_AMISTOSOS_DEZ] = (prefs[KEY_AMISTOSOS_DEZ] ?: 0) + 1
        }
    }

    /** Fecha a janela de janeiro (chamado quando o jogador joga o 1º oficial da temporada). */
    suspend fun fecharJanelaAmistosoJaneiro() {
        context.dataStore.edit { it[KEY_AMISTOSO_JANEIRO_ATIVO] = false }
    }

    suspend fun salvarPatrocinador(tipo: Int, valorAnual: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PATROCINADOR_TIPO]  = tipo
            prefs[KEY_PATROCINADOR_VALOR] = valorAnual
        }
    }

    /** Atualiza apenas o time do jogador e seu campeonato ativo (ao trocar de clube). */
    suspend fun salvarTimeDoJogador(novoTimeId: Int, novoCampeonatoId: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TIME_ID]       = novoTimeId
            prefs[KEY_CAMPEONATO_ID] = novoCampeonatoId
        }
    }

    suspend fun resetar() {
        context.dataStore.edit { it.clear() }
    }

    /** Marca que o usuário concluiu (ou pulou) o tutorial inicial. */
    suspend fun marcarOnboardingConcluido() {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_CONCLUIDO] = true
        }
    }

    /** Reabilita o tutorial (usado pelo botão "Ver tutorial de novo" no menu Mais). */
    suspend fun resetarOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_CONCLUIDO] = false
        }
    }
}
