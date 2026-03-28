package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.*
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────
//  CampeonatoDao
// ─────────────────────────────────────────────
@Dao
interface CampeonatoDao {

    @Query("SELECT * FROM campeonatos WHERE temporadaId = :temporadaId ORDER BY tipo")
    fun observePorTemporada(temporadaId: Int): Flow<List<CampeonatoEntity>>

    @Query("SELECT * FROM campeonatos WHERE id = :id")
    suspend fun buscarPorId(id: Int): CampeonatoEntity?

    @Query("SELECT * FROM campeonatos WHERE encerrado = 0 ORDER BY tipo")
    suspend fun buscarAtivos(): List<CampeonatoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(campeonato: CampeonatoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(campeonatos: List<CampeonatoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirParticipantes(participantes: List<CampeonatoTimeEntity>)

    @Update
    suspend fun atualizar(campeonato: CampeonatoEntity)

    @Query("UPDATE campeonatos SET rodadaAtual = rodadaAtual + 1 WHERE id = :campeonatoId")
    suspend fun avancarRodada(campeonatoId: Int)

    @Query("UPDATE campeonatos SET encerrado = 1 WHERE id = :campeonatoId")
    suspend fun encerrar(campeonatoId: Int)

    @Query("SELECT timeId FROM campeonato_times WHERE campeonatoId = :campeonatoId")
    suspend fun buscarIdsParticipantes(campeonatoId: Int): List<Int>
}
