package br.com.managerfoot.data.dao

import androidx.room.ColumnInfo
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

    /** Zera todos os contadores de uma liga para recalcular do zero. */
    @Query("""
        UPDATE classificacoes SET
            jogos = 0, vitorias = 0, empates = 0, derrotas = 0,
            pontos = 0, golsPro = 0, golsContra = 0, saldoGols = 0, aproveitamento = 0
        WHERE campeonatoId = :campeonatoId
    """)
    suspend fun resetarEstatisticas(campeonatoId: Int)

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

    @Query("""
        SELECT cl.campeonatoId       AS campeonatoId,
               c.nome                AS nomeCampeonato,
               c.temporadaId         AS temporadaId,
               c.tipo                AS tipo,
               cl.jogos              AS jogos,
               cl.vitorias           AS vitorias,
               cl.empates            AS empates,
               cl.derrotas           AS derrotas,
               cl.golsPro            AS golsPro,
               cl.golsContra         AS golsContra,
               cl.pontos             AS pontos,
               (SELECT COUNT(*) + 1
                FROM classificacoes cl2
                WHERE cl2.campeonatoId = cl.campeonatoId
                  AND (   cl2.pontos    > cl.pontos
                       OR (cl2.pontos   = cl.pontos AND cl2.vitorias  > cl.vitorias)
                       OR (cl2.pontos   = cl.pontos AND cl2.vitorias  = cl.vitorias
                           AND cl2.saldoGols > cl.saldoGols))
               ) AS posicao
        FROM classificacoes cl
        INNER JOIN campeonatos c ON cl.campeonatoId = c.id
        WHERE cl.timeId = :timeId
        ORDER BY c.temporadaId DESC, c.tipo
    """)
    suspend fun buscarHistoricoDoTime(timeId: Int): List<ClassificacaoComCampeonatoDto>
}

data class ClassificacaoComCampeonatoDto(
    val campeonatoId: Int,
    @ColumnInfo(name = "nomeCampeonato") val nomeCampeonato: String,
    val temporadaId: Int,
    val tipo: String,
    val jogos: Int,
    val vitorias: Int,
    val empates: Int,
    val derrotas: Int,
    val golsPro: Int,
    val golsContra: Int,
    val pontos: Int,
    val posicao: Int
)
