package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.TimeEntity
import br.com.managerfoot.data.database.entities.JogadorEntity
import br.com.managerfoot.data.database.entities.Posicao
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────

//  JogadorDao
// ─────────────────────────────────────────────
@Dao
interface JogadorDao {

    @Query("SELECT * FROM jogadores WHERE timeId = :timeId ORDER BY posicao, forca DESC")
    fun observeElenco(timeId: Int): Flow<List<JogadorEntity>>

    @Query("SELECT * FROM jogadores WHERE timeId IS NULL ORDER BY forca DESC")
    fun observeLivres(): Flow<List<JogadorEntity>>

    @Query("SELECT * FROM jogadores WHERE id = :id")
    suspend fun buscarPorId(id: Int): JogadorEntity?

    @Query("""
        SELECT * FROM jogadores
        WHERE timeId = :timeId
          AND lesionado = 0
          AND suspensoCicloAmarelos = 0
        ORDER BY forca DESC
    """)
    suspend fun buscarDisponiveisPorTime(timeId: Int): List<JogadorEntity>

    @Query("""
        SELECT * FROM jogadores
        WHERE timeId IS NULL
          AND (:posicao IS NULL OR posicao = :posicao)
          AND forca BETWEEN :forcaMin AND :forcaMax
        ORDER BY forca DESC
        LIMIT :limite
    """)
    suspend fun buscarLivresParaTransferencia(
        posicao: String?,
        forcaMin: Int,
        forcaMax: Int,
        limite: Int = 20
    ): List<JogadorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(jogador: JogadorEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(jogadores: List<JogadorEntity>)

    @Update
    suspend fun atualizar(jogador: JogadorEntity)

    @Query("UPDATE jogadores SET timeId = :timeId WHERE id = :jogadorId")
    suspend fun transferirJogador(jogadorId: Int, timeId: Int?)

    @Query("UPDATE jogadores SET lesionado = :lesionado WHERE id = :jogadorId")
    suspend fun atualizarLesao(jogadorId: Int, lesionado: Boolean)

    @Query("UPDATE jogadores SET contratoAnos = contratoAnos - 1 WHERE timeId IS NOT NULL AND contratoAnos > 0")
    suspend fun decrementarContratos()

    @Query("SELECT * FROM jogadores WHERE timeId IS NOT NULL AND contratoAnos = 0")
    suspend fun buscarComContratoExpirado(): List<JogadorEntity>

    // Desenvolvimento anual: jogadores jovens crescem, veteranos decaem
    @Query("""
        UPDATE jogadores
        SET forca = MIN(99, forca + CASE
            WHEN idade <= 22 THEN 2
            WHEN idade <= 25 THEN 1
            ELSE 0
        END),
        forca = MAX(1, forca + CASE
            WHEN idade >= 33 THEN -2
            WHEN idade >= 30 THEN -1
            ELSE 0
        END),
        idade = idade + 1
    """)
    suspend fun processarDesenvolvimentoAnual()

    @Delete
    suspend fun deletar(jogador: JogadorEntity)

    @Query("DELETE FROM jogadores")
    suspend fun deleteAll()

    // ─── Escalação pré-definida pelo jogador ───────────────────────────────────

    @Query("UPDATE jogadores SET escalarStatus = :status, posicaoEscalado = :posicao WHERE id = :jogadorId")
    suspend fun atualizarEscalacaoComPosicao(jogadorId: Int, status: Int, posicao: Posicao)

    @Query("UPDATE jogadores SET escalarStatus = :status, posicaoEscalado = NULL WHERE id = :jogadorId")
    suspend fun atualizarEscalacaoSemPosicao(jogadorId: Int, status: Int)

    @Query("SELECT * FROM jogadores WHERE timeId = :timeId AND escalarStatus = 1 ORDER BY id")
    suspend fun buscarTitularesSalvos(timeId: Int): List<JogadorEntity>

    @Query("SELECT * FROM jogadores WHERE timeId = :timeId AND escalarStatus = 2 ORDER BY id")
    suspend fun buscarReservasSalvas(timeId: Int): List<JogadorEntity>

    @Query("UPDATE jogadores SET escalarStatus = 0, posicaoEscalado = NULL WHERE timeId = :timeId")
    suspend fun limparEscalacaoTime(timeId: Int)
}
