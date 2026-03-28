package br.com.managerfoot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.managerfoot.data.database.entities.FinancaEntity
import br.com.managerfoot.data.database.entities.TransferenciaEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("""
        SELECT SUM(receitaBilheteria + receitaPatrocinio + receitaTransferencias + receitaPremiacoes
                   - despesaSalarios - despesaTransferencias - despesaInfraestrutura)
        FROM financas WHERE timeId = :timeId AND temporadaId = :temporadaId
    """)
    suspend fun calcularSaldoTemporada(timeId: Int, temporadaId: Int): Long?
}
