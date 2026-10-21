package br.com.managerfoot.data.dao

import androidx.room.*
import br.com.managerfoot.data.database.entities.RankingEuropaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Linha bruta do Coeficiente de Clubes da UEFA: a soma dos lançamentos da janela de
 * 5 temporadas, ANTES do piso de 20% do país.
 *
 * O piso não é aplicado em SQL de propósito: ele depende do coeficiente da associação,
 * que é a média dos clubes daquele país, e calcular média por grupo e comparar com cada
 * linha exigiria função de janela (SQLite 3.25+), cuja disponibilidade varia com a versão
 * do Android. O piso é aplicado em Kotlin, no repositório.
 */
data class RankingEuropaLinha(
    val timeId: Int,
    val nomeTime: String,
    val escudoRes: String,
    val pais: String,
    val pontosBrutos: Double,
    val pontosChampions: Double,
    val pontosEuropa: Double,
    val pontosConference: Double,
    // Último lançamento da janela, para a linha de apoio na UI.
    val ultimaCampanha: String
)

@Dao
interface RankingEuropaDao {

    @Insert
    suspend fun inserir(entry: RankingEuropaEntity)

    @Insert
    suspend fun inserirTodos(entries: List<RankingEuropaEntity>)

    /**
     * Soma bruta por clube na janela de 5 temporadas, sem pesos — as cinco valem igual.
     *
     * Parte de `times`, e não dos lançamentos, com LEFT JOIN: todo clube de divisão de
     * elite europeia aparece, mesmo sem nenhuma participação na janela. É isso que
     * permite ao piso de 20% cumprir a sua função, que é justamente socorrer o clube
     * tradicional que passou anos sem se classificar. Clubes que seguirem em zero depois
     * do piso são descartados no repositório.
     */
    @Query("""
        SELECT t.id AS timeId,
               t.nome AS nomeTime,
               t.escudoRes AS escudoRes,
               t.pais AS pais,
               COALESCE(SUM(r.pontos), 0.0) AS pontosBrutos,
               COALESCE(SUM(CASE WHEN r.competicao = 'CHAMPIONS_LEAGUE'
                                 THEN r.pontos ELSE 0.0 END), 0.0) AS pontosChampions,
               COALESCE(SUM(CASE WHEN r.competicao = 'EUROPA_LEAGUE'
                                 THEN r.pontos ELSE 0.0 END), 0.0) AS pontosEuropa,
               COALESCE(SUM(CASE WHEN r.competicao = 'CONFERENCE_LEAGUE'
                                 THEN r.pontos ELSE 0.0 END), 0.0) AS pontosConference,
               COALESCE((
                   SELECT u.motivo FROM ranking_europa u
                   WHERE u.timeId = t.id AND u.ano <= :anoAtual AND u.ano > :anoAtual - 5
                   ORDER BY u.ano DESC, u.id DESC LIMIT 1
               ), '') AS ultimaCampanha
        FROM times t
        LEFT JOIN ranking_europa r
               ON r.timeId = t.id AND r.ano <= :anoAtual AND r.ano > :anoAtual - 5
        WHERE t.divisao IN (:divisoesElite)
        GROUP BY t.id
        ORDER BY pontosBrutos DESC, nomeTime ASC
    """)
    fun observeRankingBruto(anoAtual: Int, divisoesElite: Set<Int>): Flow<List<RankingEuropaLinha>>

    /** Lançamentos de um clube, do mais recente para o mais antigo. */
    @Query("SELECT * FROM ranking_europa WHERE timeId = :timeId ORDER BY ano DESC, id DESC")
    fun observeLancamentosDoTime(timeId: Int): Flow<List<RankingEuropaEntity>>

    /**
     * Apaga os lançamentos de uma competição num ano. Chamado antes de registrar,
     * para que reencerrar a mesma temporada não duplique a pontuação.
     */
    @Query("DELETE FROM ranking_europa WHERE ano = :ano AND competicao = :competicao")
    suspend fun apagarLancamentos(ano: Int, competicao: String)

    @Query("DELETE FROM ranking_europa")
    suspend fun deleteAll()
}
