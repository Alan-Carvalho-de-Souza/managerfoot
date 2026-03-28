package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.RankingGeralEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RankingGeralDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirOuAtualizar(entry: RankingGeralEntity)

    @Query("SELECT * FROM ranking_geral ORDER BY pontosAcumulados DESC, vitorias DESC")
    fun observeRanking(): Flow<List<RankingGeralEntity>>

    @Query("SELECT * FROM ranking_geral ORDER BY pontosAcumulados DESC LIMIT :n")
    suspend fun buscarTopN(n: Int): List<RankingGeralEntity>

    @Query("SELECT * FROM ranking_geral WHERE timeId = :timeId")
    suspend fun buscarPorTime(timeId: Int): RankingGeralEntity?

    @Query("DELETE FROM ranking_geral")
    suspend fun deleteAll()
}
