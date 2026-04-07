package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.TimeEntity
import kotlinx.coroutines.flow.Flow

//  TimeDao
// ─────────────────────────────────────────────
@Dao
interface TimeDao {

    @Query("SELECT * FROM times ORDER BY divisao, nome")
    fun observeTodos(): Flow<List<TimeEntity>>

    @Query("SELECT * FROM times WHERE divisao = :divisao ORDER BY nome")
    fun observePorDivisao(divisao: Int): Flow<List<TimeEntity>>

    @Query("SELECT * FROM times WHERE id = :id")
    fun observePorId(id: Int): Flow<TimeEntity?>

    @Query("SELECT * FROM times")
    suspend fun buscarTodos(): List<TimeEntity>

    @Query("SELECT * FROM times WHERE controladoPorJogador = 1 LIMIT 1")
    suspend fun buscarTimeDoJogador(): TimeEntity?

    @Query("SELECT * FROM times WHERE id = :id")
    suspend fun buscarPorId(id: Int): TimeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(time: TimeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(times: List<TimeEntity>)

    @Update
    suspend fun atualizar(time: TimeEntity)

    @Query("UPDATE times SET saldo = saldo + :valor WHERE id = :timeId")
    suspend fun creditarSaldo(timeId: Int, valor: Long)

    @Query("UPDATE times SET saldo = saldo - :valor WHERE id = :timeId")
    suspend fun debitarSaldo(timeId: Int, valor: Long)

    @Query("UPDATE times SET estadioCapacidade = :novaCapacidade WHERE id = :timeId")
    suspend fun ampliarEstadio(timeId: Int, novaCapacidade: Int)

    @Query("UPDATE times SET reputacao = :rep WHERE id = :timeId")
    suspend fun atualizarReputacao(timeId: Int, rep: Float)

    @Delete
    suspend fun deletar(time: TimeEntity)

    @Query("DELETE FROM times")
    suspend fun deleteAll()
}

// ─────────────────────────────────────────────