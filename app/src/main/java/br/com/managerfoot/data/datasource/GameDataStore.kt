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
    val campeonatoId: Int,           // ID do campeonato ativo
    val anoAtual: Int,
    val mesAtual: Int,               // 1–12
    val jogoInicializado: Boolean
)

@Singleton
class GameDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_TIME_ID        = intPreferencesKey("time_id_jogador")
        val KEY_TEMPORADA_ID   = intPreferencesKey("temporada_id")
        val KEY_CAMPEONATO_ID  = intPreferencesKey("campeonato_id")
        val KEY_ANO            = intPreferencesKey("ano_atual")
        val KEY_MES            = intPreferencesKey("mes_atual")
        val KEY_INICIALIZADO   = booleanPreferencesKey("jogo_inicializado")
    }

    val saveState: Flow<SaveState> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            SaveState(
                timeIdJogador     = prefs[KEY_TIME_ID]       ?: -1,
                temporadaId       = prefs[KEY_TEMPORADA_ID]  ?: -1,
                campeonatoId      = prefs[KEY_CAMPEONATO_ID] ?: -1,
                anoAtual          = prefs[KEY_ANO]           ?: 2026,
                mesAtual          = prefs[KEY_MES]           ?: 1,
                jogoInicializado  = prefs[KEY_INICIALIZADO]  ?: false
            )
        }

    suspend fun salvarNovoJogo(timeId: Int, temporadaId: Int, campeonatoId: Int, ano: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TIME_ID]       = timeId
            prefs[KEY_TEMPORADA_ID]  = temporadaId
            prefs[KEY_CAMPEONATO_ID] = campeonatoId
            prefs[KEY_ANO]           = ano
            prefs[KEY_MES]           = 1
            prefs[KEY_INICIALIZADO]  = true
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
        }
    }

    suspend fun salvarNovaTemporada(campeonatoId: Int, temporadaId: Int, ano: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CAMPEONATO_ID] = campeonatoId
            prefs[KEY_TEMPORADA_ID]  = temporadaId
            prefs[KEY_ANO]           = ano
            prefs[KEY_MES]           = 1
        }
    }

    suspend fun resetar() {
        context.dataStore.edit { it.clear() }
    }
}
