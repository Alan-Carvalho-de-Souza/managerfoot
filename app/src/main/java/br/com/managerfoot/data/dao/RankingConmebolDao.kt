package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.RankingConmebolEntity
import kotlinx.coroutines.flow.Flow

/** Linha consolidada do Ranking CONMEBOL, já com a depreciação anual aplicada. */
data class RankingConmebolLinha(
    val timeId: Int,
    val nomeTime: String,
    val escudoRes: String,
    val pais: String,
    val pontos: Double,
    val pontosDesempenho: Double,
    val pontosHistorico: Double,
    val pontosLiga: Double
)

@Dao
interface RankingConmebolDao {

    @Insert
    suspend fun inserir(entry: RankingConmebolEntity)

    @Insert
    suspend fun inserirTodos(entries: List<RankingConmebolEntity>)

    /**
     * Ranking consolidado. A depreciação é calculada em SQL: lançamentos com
     * `deprecia = 0` (coeficiente histórico) valem 100% sempre; os demais perdem
     * 10% por ano e saem da conta após 10 anos.
     *
     * O WHERE já descarta o que está fora da janela, para linhas de clubes cuja
     * pontuação inteira expirou não aparecerem com zero.
     */
    @Query("""
        SELECT r.timeId AS timeId,
               COALESCE(t.nome, '')      AS nomeTime,
               COALESCE(t.escudoRes, '') AS escudoRes,
               COALESCE(t.pais, '')      AS pais,
               SUM(r.pontos * CASE WHEN r.deprecia = 0 THEN 1.0
                                   ELSE MAX(0.0, 1.0 - 0.1 * (:anoAtual - r.ano)) END) AS pontos,
               SUM(CASE WHEN r.competicao IN ('LIBERTADORES', 'SUL_AMERICANA') AND r.deprecia = 1
                        THEN r.pontos * MAX(0.0, 1.0 - 0.1 * (:anoAtual - r.ano)) ELSE 0.0 END) AS pontosDesempenho,
               SUM(CASE WHEN r.deprecia = 0 THEN r.pontos ELSE 0.0 END) AS pontosHistorico,
               SUM(CASE WHEN r.competicao = 'LIGA_NACIONAL' AND r.deprecia = 1
                        THEN r.pontos * MAX(0.0, 1.0 - 0.1 * (:anoAtual - r.ano)) ELSE 0.0 END) AS pontosLiga
        FROM ranking_conmebol r
        INNER JOIN times t ON t.id = r.timeId
        WHERE r.deprecia = 0 OR (r.ano <= :anoAtual AND r.ano > :anoAtual - 10)
        GROUP BY r.timeId
        HAVING pontos > 0
        ORDER BY pontos DESC, nomeTime ASC
    """)
    fun observeRanking(anoAtual: Int): Flow<List<RankingConmebolLinha>>

    /** Lançamentos de um clube, do mais recente para o mais antigo. */
    @Query("SELECT * FROM ranking_conmebol WHERE timeId = :timeId ORDER BY ano DESC, id DESC")
    fun observeLancamentosDoTime(timeId: Int): Flow<List<RankingConmebolEntity>>

    @Query("SELECT COUNT(*) FROM ranking_conmebol WHERE deprecia = 0")
    suspend fun contarLancamentosHistoricos(): Int

    @Query("DELETE FROM ranking_conmebol")
    suspend fun deleteAll()
}
