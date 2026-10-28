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

    @Query("SELECT * FROM campeonatos WHERE temporadaId = :temporadaId ORDER BY tipo")
    suspend fun buscarPorTemporadaId(temporadaId: Int): List<CampeonatoEntity>

    @Query("SELECT * FROM campeonatos WHERE id = :id")
    suspend fun buscarPorId(id: Int): CampeonatoEntity?

    /**
     * A competição mais recente de um tipo, ou null se nunca houve nenhuma.
     *
     * Serve para competições que não guardam id no save porque são criadas e
     * resolvidas de uma vez no fim da temporada — a Copa Intercontinental é o
     * caso. Como ela pertence à temporada que ENCERROU, buscar pela temporada
     * corrente não a encontraria; o que a UI quer é sempre a última disputada.
     */
    @Query("SELECT * FROM campeonatos WHERE tipo = :tipo ORDER BY id DESC LIMIT 1")
    fun observeMaisRecentePorTipo(tipo: TipoCampeonato): Flow<CampeonatoEntity?>

    /** Versão suspensa de [observeMaisRecentePorTipo] — p/ resolver o id uma vez. */
    @Query("SELECT * FROM campeonatos WHERE tipo = :tipo ORDER BY id DESC LIMIT 1")
    suspend fun buscarMaisRecentePorTipo(tipo: TipoCampeonato): CampeonatoEntity?

    @Query("SELECT * FROM campeonatos WHERE encerrado = 0 ORDER BY tipo")
    suspend fun buscarAtivos(): List<CampeonatoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(campeonato: CampeonatoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(campeonatos: List<CampeonatoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirParticipantes(participantes: List<CampeonatoTimeEntity>)

    @Query("DELETE FROM campeonatos")
    suspend fun deleteAll()

    @Query("DELETE FROM campeonato_times")
    suspend fun deleteAllParticipantes()

    @Update
    suspend fun atualizar(campeonato: CampeonatoEntity)

    @Query("UPDATE campeonatos SET rodadaAtual = rodadaAtual + 1 WHERE id = :campeonatoId")
    suspend fun avancarRodada(campeonatoId: Int)

    @Query("UPDATE campeonatos SET encerrado = 1 WHERE id = :campeonatoId")
    suspend fun encerrar(campeonatoId: Int)

    @Query("SELECT timeId FROM campeonato_times WHERE campeonatoId = :campeonatoId")
    suspend fun buscarIdsParticipantes(campeonatoId: Int): List<Int>

    /**
     * Participantes COM o rotulo de grupo. A Libertadores usa esse rotulo em duas
     * etapas: antes do sorteio ele diz por qual porta o clube entrou ("G" direto
     * nos grupos, "P" na Pre-Libertadores) e, depois do sorteio, o grupo A..H.
     */
    @Query("SELECT * FROM campeonato_times WHERE campeonatoId = :campeonatoId")
    suspend fun buscarParticipantesComGrupo(campeonatoId: Int): List<CampeonatoTimeEntity>
}
