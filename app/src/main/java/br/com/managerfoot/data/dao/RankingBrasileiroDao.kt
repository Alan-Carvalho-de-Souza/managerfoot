package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.RankingBrasileiroEntity
import kotlinx.coroutines.flow.Flow

/**
 * Linha consolidada do Ranking Nacional de Clubes (CBF), já com o peso do ano
 * aplicado a cada lançamento da janela de 5 anos.
 */
data class RankingBrasileiroLinha(
    val timeId: Int,
    val nomeTime: String,
    val escudoRes: String,
    val pais: String,
    val pontos: Int,
    val pontosLiga: Int,
    val pontosCopa: Int,
    // Último lançamento de liga da janela, para a linha de apoio na UI
    // ("Série A · 3º"). Vazio enquanto o clube não tiver disputado uma série.
    val ultimaCampanha: String
)

@Dao
interface RankingBrasileiroDao {

    @Insert
    suspend fun inserir(entry: RankingBrasileiroEntity)

    @Insert
    suspend fun inserirTodos(entries: List<RankingBrasileiroEntity>)

    /**
     * Ranking consolidado. O peso é calculado em SQL: `5 - (anoAtual - ano)`, o que
     * dá 5 para o ano mais recente e 1 para o 5º ano da janela. Diferente do
     * CONMEBOL, aqui não há depreciação fracionária — a temporada que sai da janela
     * simplesmente deixa de contar, e o WHERE já a descarta.
     */
    @Query("""
        SELECT r.timeId AS timeId,
               COALESCE(t.nome, '')      AS nomeTime,
               COALESCE(t.escudoRes, '') AS escudoRes,
               COALESCE(t.pais, '')      AS pais,
               SUM(r.pontos * (5 - (:anoAtual - r.ano))) AS pontos,
               SUM(CASE WHEN r.competicao = 'COPA_DO_BRASIL' THEN 0
                        ELSE r.pontos * (5 - (:anoAtual - r.ano)) END) AS pontosLiga,
               SUM(CASE WHEN r.competicao = 'COPA_DO_BRASIL'
                        THEN r.pontos * (5 - (:anoAtual - r.ano)) ELSE 0 END) AS pontosCopa,
               COALESCE((
                   SELECT u.motivo FROM ranking_brasileiro u
                   WHERE u.timeId = r.timeId AND u.competicao <> 'COPA_DO_BRASIL'
                         AND u.ano <= :anoAtual AND u.ano > :anoAtual - 5
                   ORDER BY u.ano DESC, u.id DESC LIMIT 1
               ), '') AS ultimaCampanha
        FROM ranking_brasileiro r
        INNER JOIN times t ON t.id = r.timeId
        WHERE r.ano <= :anoAtual AND r.ano > :anoAtual - 5
        GROUP BY r.timeId
        HAVING pontos > 0
        ORDER BY pontos DESC, nomeTime ASC
    """)
    fun observeRanking(anoAtual: Int): Flow<List<RankingBrasileiroLinha>>

    /** Lançamentos de um clube, do mais recente para o mais antigo. */
    @Query("SELECT * FROM ranking_brasileiro WHERE timeId = :timeId ORDER BY ano DESC, id DESC")
    fun observeLancamentosDoTime(timeId: Int): Flow<List<RankingBrasileiroEntity>>

    /**
     * Apaga os lançamentos de uma competição num ano. Chamado antes de registrar,
     * para que reencerrar a mesma temporada não duplique a pontuação.
     */
    @Query("DELETE FROM ranking_brasileiro WHERE ano = :ano AND competicao = :competicao")
    suspend fun apagarLancamentos(ano: Int, competicao: String)

    @Query("DELETE FROM ranking_brasileiro")
    suspend fun deleteAll()
}
