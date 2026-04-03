package br.com.managerfoot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.managerfoot.data.database.entities.FinancaEntity
import br.com.managerfoot.data.database.entities.TipoTransferencia
import br.com.managerfoot.data.database.entities.TransferenciaEntity
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────
//  TransferenciaDetalhe — resultado de query JOIN
// ─────────────────────────────────────────────
data class TransferenciaDetalhe(
    val id: Int,
    val jogadorNome: String,
    val posicao: String,
    val origemNome: String?,
    val destinoNome: String?,
    val valor: Long,
    val tipo: TipoTransferencia,
    val temporadaId: Int,
    val mes: Int
)

// ─────────────────────────────────────────────
//  FinancaDao
// ─────────────────────────────────────────────
@Dao
interface FinancaDao {

    @Query("SELECT * FROM financas WHERE timeId = :timeId AND temporadaId = :temporadaId ORDER BY mes")
    fun observeExtratoAnual(timeId: Int, temporadaId: Int): Flow<List<FinancaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(financa: FinancaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTransferencia(transferencia: TransferenciaEntity): Long

    @Query("DELETE FROM financas")
    suspend fun deleteAll()

    @Query("DELETE FROM transferencias")
    suspend fun deleteAllTransferencias()

    @Query("""
        SELECT t.id, j.nomeAbreviado AS jogadorNome, j.posicao AS posicao,
               orig.nome AS origemNome, dest.nome AS destinoNome,
               t.valor, t.tipo, t.temporadaId, t.mes
        FROM transferencias t
        INNER JOIN jogadores j ON j.id = t.jogadorId
        LEFT JOIN times orig ON orig.id = t.timeOrigemId
        LEFT JOIN times dest ON dest.id = t.timeDestinoId
        ORDER BY t.temporadaId DESC, t.mes DESC
    """)
    fun observeTodasTransferencias(): Flow<List<TransferenciaDetalhe>>

    @Query("""
        SELECT t.id, j.nomeAbreviado AS jogadorNome, j.posicao AS posicao,
               orig.nome AS origemNome, dest.nome AS destinoNome,
               t.valor, t.tipo, t.temporadaId, t.mes
        FROM transferencias t
        INNER JOIN jogadores j ON j.id = t.jogadorId
        LEFT JOIN times orig ON orig.id = t.timeOrigemId
        LEFT JOIN times dest ON dest.id = t.timeDestinoId
        WHERE t.timeOrigemId = :timeId AND t.tipo = 'VENDA'
        ORDER BY t.temporadaId DESC, t.mes DESC
    """)
    fun observeVendasDoTime(timeId: Int): Flow<List<TransferenciaDetalhe>>

    @Query("SELECT * FROM financas WHERE timeId = :timeId ORDER BY temporadaId DESC, mes DESC")
    fun observeFinancasMensais(timeId: Int): Flow<List<FinancaEntity>>

    @Query("""
        SELECT t.id, j.nomeAbreviado AS jogadorNome, j.posicao AS posicao,
               orig.nome AS origemNome, dest.nome AS destinoNome,
               t.valor, t.tipo, t.temporadaId, t.mes
        FROM transferencias t
        INNER JOIN jogadores j ON j.id = t.jogadorId
        LEFT JOIN times orig ON orig.id = t.timeOrigemId
        LEFT JOIN times dest ON dest.id = t.timeDestinoId
        WHERE t.timeDestinoId = :timeId AND t.tipo = 'COMPRA'
        ORDER BY t.temporadaId DESC, t.mes DESC
    """)
    fun observeComprasDoTime(timeId: Int): Flow<List<TransferenciaDetalhe>>

    @Query("""
        SELECT SUM(receitaBilheteria + receitaPatrocinio + receitaTransferencias + receitaPremiacoes
                   - despesaSalarios - despesaTransferencias - despesaInfraestrutura)
        FROM financas WHERE timeId = :timeId AND temporadaId = :temporadaId
    """)
    suspend fun calcularSaldoTemporada(timeId: Int, temporadaId: Int): Long?
}
