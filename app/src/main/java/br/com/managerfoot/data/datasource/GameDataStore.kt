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
    val copaId: Int,                  // ID da Copa do Brasil ativa
    val copaArgId: Int,               // ID da Copa Argentina ativa (-1 se não houver)
    val supercopaId: Int,            // ID da Supercopa Rei ativa (-1 se não houver)
    val anoAtual: Int,
    val mesAtual: Int,               // 1–12
    val jogoInicializado: Boolean,
    val patrocinadorValorAnual: Long, // 0 = patrocinador não escolhido na temporada
    val patrocinadorTipo: Int,        // 0 = nenhum, 1-3 = tier escolhido
    val patrocinadorPreCreditado: Long // valor já adiantado no saldo do mês corrente
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
        val KEY_COPA_ID         = intPreferencesKey("copa_id")
        val KEY_COPA_ARG_ID     = intPreferencesKey("copa_arg_id")
        val KEY_SUPERCOPA_ID    = intPreferencesKey("supercopa_id")
        val KEY_ANO            = intPreferencesKey("ano_atual")
        val KEY_MES            = intPreferencesKey("mes_atual")
        val KEY_INICIALIZADO   = booleanPreferencesKey("jogo_inicializado")
        val KEY_PATROCINADOR_VALOR        = longPreferencesKey("patrocinador_valor_anual")
        val KEY_PATROCINADOR_TIPO         = intPreferencesKey("patrocinador_tipo")
        val KEY_PATROCINADOR_PRE_CREDITADO = longPreferencesKey("patrocinador_pre_creditado")
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
                copaId            = prefs[KEY_COPA_ID]          ?: -1,
                copaArgId         = prefs[KEY_COPA_ARG_ID]      ?: -1,
                supercopaId       = prefs[KEY_SUPERCOPA_ID]    ?: -1,
                anoAtual          = prefs[KEY_ANO]             ?: 2026,
                mesAtual          = prefs[KEY_MES]             ?: 1,
                jogoInicializado  = prefs[KEY_INICIALIZADO]    ?: false,
                patrocinadorValorAnual    = prefs[KEY_PATROCINADOR_VALOR]         ?: 0L,
                patrocinadorTipo          = prefs[KEY_PATROCINADOR_TIPO]          ?: 0,
                patrocinadorPreCreditado  = prefs[KEY_PATROCINADOR_PRE_CREDITADO] ?: 0L
            )
        }

    suspend fun salvarNovoJogo(
        timeId: Int, temporadaId: Int, campeonatoId: Int,
        campeonatoAId: Int, campeonatoBId: Int, campeonatoCId: Int, campeonatoDId: Int,
        campeonatoArgAId: Int, campeonatoArgBId: Int = -1,
        campeonatoArgClausuraId: Int = -1,
        campeonatoUruAperturaId: Int = -1, campeonatoUruClausuraId: Int = -1,
        campeonatoUruIntermedId: Int = -1, campeonatoUruBId: Int = -1,
        campeonatoUruBCompetId: Int = -1,
        copaId: Int, copaArgId: Int = -1, supercopaId: Int = -1, ano: Int
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
            prefs[KEY_COPA_ID]         = copaId
            prefs[KEY_COPA_ARG_ID]     = copaArgId
            prefs[KEY_SUPERCOPA_ID]    = supercopaId
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
        campeonatoUruBCompetId: Int = -1,
        copaId: Int, copaArgId: Int = -1, supercopaId: Int = -1, temporadaId: Int, ano: Int
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
            prefs[KEY_COPA_ID]         = copaId
            prefs[KEY_COPA_ARG_ID]     = copaArgId
            prefs[KEY_SUPERCOPA_ID]    = supercopaId
            prefs[KEY_TEMPORADA_ID]    = temporadaId
            prefs[KEY_ANO]             = ano
            prefs[KEY_MES]             = 1
            // Reseta patrocinador — usuário deve escolher um novo para cada temporada
            prefs[KEY_PATROCINADOR_VALOR]         = 0L
            prefs[KEY_PATROCINADOR_TIPO]          = 0
            prefs[KEY_PATROCINADOR_PRE_CREDITADO] = 0L
        }
    }

    suspend fun salvarPatrocinador(tipo: Int, valorAnual: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PATROCINADOR_TIPO]  = tipo
            prefs[KEY_PATROCINADOR_VALOR] = valorAnual
        }
    }

    suspend fun resetar() {
        context.dataStore.edit { it.clear() }
    }
}
