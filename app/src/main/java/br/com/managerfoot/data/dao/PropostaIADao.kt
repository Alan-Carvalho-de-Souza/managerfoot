package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.PropostaIAEntity
import br.com.managerfoot.data.database.entities.StatusProposta
import br.com.managerfoot.data.database.entities.TipoProposta
import kotlinx.coroutines.flow.Flow

@Dao
interface PropostaIADao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(proposta: PropostaIAEntity): Long

    @Update
    suspend fun atualizar(proposta: PropostaIAEntity)

    /** Fluxo reativo de propostas ativas (visíveis na aba Propostas do Mercado). */
    @Query("SELECT * FROM propostas_ia WHERE status = 'PENDENTE' OR status = 'AGUARDANDO_RESPOSTA_IA' ORDER BY id DESC")
    fun observeAtivas(): Flow<List<PropostaIAEntity>>

    @Query("SELECT * FROM propostas_ia WHERE jogadorId = :jogadorId AND (status = 'PENDENTE' OR status = 'AGUARDANDO_RESPOSTA_IA')")
    suspend fun buscarAtivasPorJogador(jogadorId: Int): List<PropostaIAEntity>

    @Query("SELECT * FROM propostas_ia WHERE jogadorId = :jogadorId AND tipoProposta = :tipo AND (status = 'PENDENTE' OR status = 'AGUARDANDO_RESPOSTA_IA')")
    suspend fun buscarAtivasPorJogadorETipo(jogadorId: Int, tipo: TipoProposta): List<PropostaIAEntity>

    @Query("SELECT * FROM propostas_ia WHERE status = 'AGUARDANDO_RESPOSTA_IA'")
    suspend fun buscarAguardandoRespostaIA(): List<PropostaIAEntity>

    @Query("SELECT * FROM propostas_ia WHERE id = :id")
    suspend fun buscarPorId(id: Int): PropostaIAEntity?

    @Query("UPDATE propostas_ia SET status = :status WHERE id = :id")
    suspend fun atualizarStatus(id: Int, status: StatusProposta)

    @Query("UPDATE propostas_ia SET status = :status, tentativasNegociacao = :tentativas, valorSolicitadoJogador = :valorSolicitado WHERE id = :id")
    suspend fun atualizarNegociacao(id: Int, status: StatusProposta, tentativas: Int, valorSolicitado: Long)

    @Query("DELETE FROM propostas_ia WHERE status IN ('ACEITA', 'RECUSADA')")
    suspend fun limparEncerradas()

    @Query("DELETE FROM propostas_ia")
    suspend fun limparTodas()

    // ── Notificações ──────────────────────────────────────────────────────────

    /** Fluxo de todas as entradas relevantes para a tela de notificações:
     *  propostas pendentes + aceites/recusas ainda não lidas. */
    @Query("""
        SELECT * FROM propostas_ia
        WHERE status = 'PENDENTE'
           OR status = 'AGUARDANDO_RESPOSTA_IA'
           OR (status IN ('ACEITA', 'RECUSADA') AND lida = 0)
        ORDER BY id DESC
    """)
    fun observeNotificacoes(): Flow<List<PropostaIAEntity>>

    /** Contagem de notificações não lidas (badge da bottom bar). */
    @Query("""
        SELECT COUNT(*) FROM propostas_ia
        WHERE status = 'PENDENTE'
           OR status = 'AGUARDANDO_RESPOSTA_IA'
           OR (status IN ('ACEITA', 'RECUSADA') AND lida = 0)
    """)
    fun observeContadorNaoLidas(): Flow<Int>

    /** Marca uma proposta como lida. */
    @Query("UPDATE propostas_ia SET lida = 1 WHERE id = :id")
    suspend fun marcarLida(id: Int)

    /** Marca todas as notificações encerradas (aceitas/recusadas) como lidas. */
    @Query("UPDATE propostas_ia SET lida = 1 WHERE status IN ('ACEITA', 'RECUSADA')")
    suspend fun marcarTodasEncerradasLidas()
}
