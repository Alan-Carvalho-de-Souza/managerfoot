package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.RankingGeralEntity
import kotlinx.coroutines.flow.Flow

/** Linha do ranking enriquecida com o país do clube (join com `times`). */
data class RankingGeralComPais(
    @Embedded val ranking: RankingGeralEntity,
    val pais: String
)

@Dao
interface RankingGeralDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirOuAtualizar(entry: RankingGeralEntity)

    @Query("SELECT * FROM ranking_geral ORDER BY pontosAcumulados DESC, vitorias DESC")
    fun observeRanking(): Flow<List<RankingGeralEntity>>

    /** Ranking com país via LEFT JOIN em `times` — base dos recortes por país/continente. */
    @Query("""
        SELECT r.*, COALESCE(t.pais, '') AS pais
        FROM ranking_geral r
        LEFT JOIN times t ON t.id = r.timeId
        ORDER BY r.pontosAcumulados DESC, r.vitorias DESC
    """)
    fun observeRankingComPais(): Flow<List<RankingGeralComPais>>

    @Query("SELECT * FROM ranking_geral ORDER BY pontosAcumulados DESC LIMIT :n")
    suspend fun buscarTopN(n: Int): List<RankingGeralEntity>

    @Query("SELECT * FROM ranking_geral WHERE timeId = :timeId")
    suspend fun buscarPorTime(timeId: Int): RankingGeralEntity?

    @Query("DELETE FROM ranking_geral")
    suspend fun deleteAll()
}
