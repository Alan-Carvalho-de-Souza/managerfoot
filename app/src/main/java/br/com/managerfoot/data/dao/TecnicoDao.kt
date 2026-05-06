package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.PassagemTecnicoEntity
import br.com.managerfoot.data.database.entities.TecnicoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TecnicoDao {

    // ── Leitura ───────────────────────────────────────────────
    @Query("SELECT * FROM tecnicos ORDER BY (vitorias * 5 + empates) DESC, vitorias DESC, nome")
    fun observeTodos(): Flow<List<TecnicoEntity>>

    @Query("SELECT * FROM tecnicos WHERE timeId IS NULL ORDER BY reputacao DESC, nome")
    fun observeLivres(): Flow<List<TecnicoEntity>>

    @Query("SELECT * FROM tecnicos WHERE timeId = :timeId LIMIT 1")
    suspend fun buscarPorTime(timeId: Int): TecnicoEntity?

    @Query("SELECT * FROM tecnicos WHERE timeId = :timeId LIMIT 1")
    fun observePorTime(timeId: Int): Flow<TecnicoEntity?>

    @Query("SELECT * FROM tecnicos WHERE id = :id LIMIT 1")
    suspend fun buscarPorId(id: Int): TecnicoEntity?

    @Query("SELECT * FROM tecnicos WHERE controladoPorJogador = 1 LIMIT 1")
    suspend fun buscarTecnicoDoJogador(): TecnicoEntity?

    /**
     * Ranking ordenado por pontos (V*5+E) desc; desempates: vitórias, títulos.
     */
    @Query("""
        SELECT * FROM tecnicos
        ORDER BY (vitorias * 5 + empates) DESC,
                 vitorias DESC,
                 titulos DESC,
                 nome ASC
    """)
    fun observeRanking(): Flow<List<TecnicoEntity>>

    @Query("SELECT * FROM tecnicos")
    suspend fun buscarTodos(): List<TecnicoEntity>

    // ── Escrita ───────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(tecnico: TecnicoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(tecnicos: List<TecnicoEntity>)

    @Update
    suspend fun atualizar(tecnico: TecnicoEntity)

    @Query("UPDATE tecnicos SET timeId = NULL WHERE id = :tecnicoId")
    suspend fun liberarDoTime(tecnicoId: Int)

    @Query("""
        UPDATE tecnicos
        SET timeId = :novoTimeId, contratoAnos = :anos, salario = :salario
        WHERE id = :tecnicoId
    """)
    suspend fun contratar(tecnicoId: Int, novoTimeId: Int, salario: Long, anos: Int)

    /** Adiciona +1 ao contador correspondente. */
    @Query("""
        UPDATE tecnicos
        SET vitorias = vitorias + :v,
            empates  = empates  + :e,
            derrotas = derrotas + :d,
            vitoriasTemporada = vitoriasTemporada + :v,
            empatesTemporada  = empatesTemporada  + :e,
            derrotasTemporada = derrotasTemporada + :d
        WHERE id = :tecnicoId
    """)
    suspend fun acrescentarResultado(tecnicoId: Int, v: Int, e: Int, d: Int)

    @Query("UPDATE tecnicos SET titulos = titulos + 1 WHERE id = :tecnicoId")
    suspend fun acrescentarTitulo(tecnicoId: Int)

    @Query("UPDATE tecnicos SET reputacao = :reputacao WHERE id = :tecnicoId")
    suspend fun atualizarReputacao(tecnicoId: Int, reputacao: Float)

    @Query("""
        UPDATE tecnicos
        SET vitoriasTemporada = 0, empatesTemporada = 0, derrotasTemporada = 0
    """)
    suspend fun resetarStatsTemporada()

    @Query("DELETE FROM tecnicos")
    suspend fun deleteAll()

    // ─────────────────────────────────────────────────────────
    //  Passagens (histórico por clube)
    // ─────────────────────────────────────────────────────────
    @Query("SELECT * FROM passagens_tecnicos WHERE tecnicoId = :tecnicoId ORDER BY anoInicio ASC, anoFim ASC")
    suspend fun buscarPassagens(tecnicoId: Int): List<PassagemTecnicoEntity>

    @Query("SELECT * FROM passagens_tecnicos WHERE tecnicoId = :tecnicoId AND ativa = 1 LIMIT 1")
    suspend fun buscarPassagemAtiva(tecnicoId: Int): PassagemTecnicoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirPassagem(passagem: PassagemTecnicoEntity): Long

    @Update
    suspend fun atualizarPassagem(passagem: PassagemTecnicoEntity)

    @Query("UPDATE passagens_tecnicos SET ativa = 0, anoFim = :anoFim WHERE id = :passagemId")
    suspend fun encerrarPassagem(passagemId: Int, anoFim: Int)

    /** Acrescenta resultado à passagem ativa do técnico. */
    @Query("""
        UPDATE passagens_tecnicos
        SET vitorias = vitorias + :v,
            empates  = empates  + :e,
            derrotas = derrotas + :d
        WHERE id = :passagemId
    """)
    suspend fun acrescentarResultadoPassagem(passagemId: Int, v: Int, e: Int, d: Int)

    @Query("UPDATE passagens_tecnicos SET titulos = titulos + 1 WHERE id = :passagemId")
    suspend fun acrescentarTituloPassagem(passagemId: Int)

    @Query("DELETE FROM passagens_tecnicos")
    suspend fun deleteAllPassagens()
}
