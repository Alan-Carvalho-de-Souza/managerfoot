package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.PropostaClubeEntity
import br.com.managerfoot.data.database.entities.StatusPropostaClube
import kotlinx.coroutines.flow.Flow

@Dao
interface PropostaClubeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(proposta: PropostaClubeEntity): Long

    @Update
    suspend fun atualizar(proposta: PropostaClubeEntity)

    /** Fluxo de todas as entradas relevantes para a tela de notificações:
     *  propostas pendentes + aceites/recusas ainda não lidas. */
    @Query("""
        SELECT * FROM propostas_clube
        WHERE status = 'PENDENTE'
           OR (status IN ('ACEITA', 'RECUSADA') AND lida = 0)
        ORDER BY id DESC
    """)
    fun observeNotificacoes(): Flow<List<PropostaClubeEntity>>

    /** Contagem de notificações de proposta de clube não lidas (badge da bottom bar). */
    @Query("""
        SELECT COUNT(*) FROM propostas_clube
        WHERE status = 'PENDENTE'
           OR (status IN ('ACEITA', 'RECUSADA') AND lida = 0)
    """)
    fun observeContadorNaoLidas(): Flow<Int>

    @Query("SELECT * FROM propostas_clube WHERE id = :id")
    suspend fun buscarPorId(id: Int): PropostaClubeEntity?

    @Query("SELECT * FROM propostas_clube WHERE status = 'PENDENTE'")
    suspend fun buscarPendentes(): List<PropostaClubeEntity>

    @Query("UPDATE propostas_clube SET status = :status WHERE id = :id")
    suspend fun atualizarStatus(id: Int, status: StatusPropostaClube)

    @Query("UPDATE propostas_clube SET lida = 1 WHERE id = :id")
    suspend fun marcarLida(id: Int)

    @Query("UPDATE propostas_clube SET lida = 1 WHERE status IN ('ACEITA', 'RECUSADA')")
    suspend fun marcarTodasEncerradasLidas()

    @Query("DELETE FROM propostas_clube")
    suspend fun limparTodas()
}
