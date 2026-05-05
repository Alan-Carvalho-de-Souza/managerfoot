package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.HallDaFamaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HallDaFamaDao {

    @Query("SELECT * FROM hall_da_fama ORDER BY ano DESC")
    fun observeTodos(): Flow<List<HallDaFamaEntity>>

    /** Conquistas (títulos) de um time específico — ordenadas pelo mais recente. */
    @Query("SELECT * FROM hall_da_fama WHERE campeaoTimeId = :timeId ORDER BY ano DESC")
    fun observeConquistasDoTime(timeId: Int): Flow<List<HallDaFamaEntity>>

    /** Vice-campeonatos de um time específico (opcional, pode ser usado em "quase-conquistas"). */
    @Query("SELECT * FROM hall_da_fama WHERE viceTimeId = :timeId ORDER BY ano DESC")
    fun observeVicesDoTime(timeId: Int): Flow<List<HallDaFamaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(entry: HallDaFamaEntity)

    @Query("SELECT * FROM hall_da_fama WHERE ano = :ano AND divisao = :divisao LIMIT 1")
    suspend fun buscarCampeaoPorAnoEDivisao(ano: Int, divisao: Int): HallDaFamaEntity?

    @Query("DELETE FROM hall_da_fama")
    suspend fun deleteAll()
}
