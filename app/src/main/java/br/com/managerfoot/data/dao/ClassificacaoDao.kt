package br.com.managerfoot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.managerfoot.data.database.entities.ClassificacaoEntity
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────
//  ClassificacaoDao
// ─────────────────────────────────────────────
@Dao
interface ClassificacaoDao {

    @Query("""
        SELECT * FROM classificacoes
        WHERE campeonatoId = :campeonatoId AND (:grupo IS NULL OR grupo = :grupo)
        ORDER BY pontos DESC, vitorias DESC, saldoGols DESC, golsPro DESC
    """)
    fun observeTabelaPorCampeonato(campeonatoId: Int, grupo: String? = null): Flow<List<ClassificacaoEntity>>

    @Query("SELECT * FROM classificacoes WHERE campeonatoId = :campeonatoId AND timeId = :timeId")
    suspend fun buscarPosicao(campeonatoId: Int, timeId: Int): ClassificacaoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(classificacoes: List<ClassificacaoEntity>)

    @Query("DELETE FROM classificacoes")
    suspend fun deleteAll()

    // Atualiza estatísticas após uma partida (casa)
    @Query("""
        UPDATE classificacoes SET
            jogos     = jogos + 1,
            vitorias  = vitorias  + :v,
            empates   = empates   + :e,
            derrotas  = derrotas  + :d,
            pontos    = pontos    + (:v * 3) + :e,
            golsPro   = golsPro   + :gp,
            golsContra= golsContra+ :gc,
            saldoGols = saldoGols + :gp - :gc,
            aproveitamento = CAST((vitorias + :v) AS FLOAT) / (jogos + 1)
        WHERE campeonatoId = :campeonatoId AND timeId = :timeId
    """)
    suspend fun atualizarEstatisticas(
        campeonatoId: Int, timeId: Int,
        v: Int, e: Int, d: Int, gp: Int, gc: Int
    )

    @Query("""
        SELECT * FROM classificacoes
        WHERE campeonatoId = :campeonatoId
        ORDER BY pontos DESC, vitorias DESC, saldoGols DESC, golsPro DESC
        LIMIT 2
    """)
    suspend fun buscarTop2(campeonatoId: Int): List<ClassificacaoEntity>

    @Query("""
        SELECT * FROM classificacoes
        WHERE campeonatoId = :campeonatoId
        ORDER BY pontos DESC, vitorias DESC, saldoGols DESC, golsPro DESC
    """)
    suspend fun buscarTabelaOrdenada(campeonatoId: Int): List<ClassificacaoEntity>
}
