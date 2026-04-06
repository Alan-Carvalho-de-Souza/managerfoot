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

    @Query("SELECT * FROM jogadores WHERE timeId = :timeId AND categoriaBase = 0 ORDER BY posicao, forca DESC")
    fun observeElenco(timeId: Int): Flow<List<JogadorEntity>>

    @Query("SELECT * FROM jogadores WHERE timeId IS NULL AND aposentado = 0 AND categoriaBase = 0 ORDER BY forca DESC")
    fun observeLivres(): Flow<List<JogadorEntity>>

    @Query("SELECT * FROM jogadores WHERE aposentado = 0 AND categoriaBase = 0 ORDER BY forca DESC")
    fun observeTodosJogadoresAtivos(): Flow<List<JogadorEntity>>

    @Query("SELECT * FROM jogadores WHERE id = :id")
    suspend fun buscarPorId(id: Int): JogadorEntity?

    @Query("""
        SELECT * FROM jogadores
        WHERE timeId = :timeId
          AND categoriaBase = 0
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

    /** Aplica lesão programada: marca lesionado e define quantas partidas o jogador ficará de fora. */
    @Query("UPDATE jogadores SET lesionado = 1, partidasSemJogar = :partidas WHERE id = :jogadorId")
    suspend fun aplicarLesao(jogadorId: Int, partidas: Int)

    @Query("UPDATE jogadores SET contratoAnos = contratoAnos - 1 WHERE timeId IS NOT NULL AND contratoAnos > 0")
    suspend fun decrementarContratos()

    @Query("SELECT * FROM jogadores WHERE timeId IS NOT NULL AND contratoAnos = 0")
    suspend fun buscarComContratoExpirado(): List<JogadorEntity>

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

    // ─── Progressão de habilidades ─────────────────────────────────

    /** Retorna todos os jogadores (para processamento de desenvolvimento anual em Kotlin). */
    @Query("SELECT * FROM jogadores")
    suspend fun buscarTodos(): List<JogadorEntity>

    /** Persiste em lote todos os jogadores com atributos atualizados. */
    @Update
    suspend fun atualizarTodos(jogadores: List<JogadorEntity>)

    /**
     * Atualiza a nota média acumulada na temporada e o contador de partidas.
     * Deve ser chamado após cada partida para cada jogador participante.
     */
    @Query("UPDATE jogadores SET notaMedia = :nota, partidasTemporada = :partidas WHERE id = :jogadorId")
    suspend fun atualizarNota(jogadorId: Int, nota: Float, partidas: Int)

    /**
     * Aposenta o jogador: remove do time e marca como aposentado.
     * Disponível para jogadores com idade entre 33 e 44 anos.
     */
    @Query("UPDATE jogadores SET aposentado = 1, timeId = NULL WHERE id = :jogadorId")
    suspend fun aposentarJogador(jogadorId: Int)

    /** Retorna os jogadores da base de juniores de um clube. */
    @Query("SELECT * FROM jogadores WHERE timeId = :timeId AND categoriaBase = 1 ORDER BY posicao, forca DESC")
    fun observeJuniores(timeId: Int): Flow<List<JogadorEntity>>

    /** Promove um jogador da base para o elenco principal. */
    @Query("UPDATE jogadores SET categoriaBase = 0 WHERE id = :jogadorId")
    suspend fun promoverJunior(jogadorId: Int)

    /** Dispensa um jogador da base: remove do clube e sai da categoria base (vai para mercado livre). */
    @Query("UPDATE jogadores SET timeId = NULL, categoriaBase = 0 WHERE id = :jogadorId")
    suspend fun dispensarJuniorDb(jogadorId: Int)

    /** Conta jogadores (sênior + base) de um time — usado para verificar o limite de elenco. */
    @Query("SELECT COUNT(*) FROM jogadores WHERE timeId = :timeId AND aposentado = 0")
    suspend fun contarJogadoresPorTime(timeId: Int): Int

    /** Retorna todos os jogadores sênior ativos (sem base) de um time, incluindo lesionados/suspensos. */
    @Query("SELECT * FROM jogadores WHERE timeId = :timeId AND aposentado = 0 AND categoriaBase = 0")
    suspend fun buscarSenioresDoTime(timeId: Int): List<JogadorEntity>

    /** Retorna todos os jogadores ativos (sênior + base) de um time. */
    @Query("SELECT * FROM jogadores WHERE timeId = :timeId AND aposentado = 0")
    suspend fun buscarElencoCompletoDoTime(timeId: Int): List<JogadorEntity>
}
