package br.com.managerfoot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.managerfoot.data.database.entities.EscalacaoEntity
import br.com.managerfoot.data.database.entities.EventoPartidaEntity
import br.com.managerfoot.data.database.entities.PartidaEntity
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────
//  PartidaDao
// ─────────────────────────────────────────────
@Dao
interface PartidaDao {

    @Query("""
        SELECT * FROM partidas
        WHERE campeonatoId = :campeonatoId AND rodada = :rodada
        ORDER BY id
    """)
    suspend fun buscarPorRodada(campeonatoId: Int, rodada: Int): List<PartidaEntity>

    @Query("""
        SELECT * FROM partidas
        WHERE (timeCasaId = :timeId OR timeForaId = :timeId) AND jogada = 0
        ORDER BY rodada
        LIMIT 1
    """)
    suspend fun buscarProximaPartida(timeId: Int): PartidaEntity?

    @Query("""
        SELECT * FROM partidas
        WHERE (timeCasaId = :timeId OR timeForaId = :timeId) AND jogada = 1
        ORDER BY rodada DESC
        LIMIT :limite
    """)
    fun observeUltimosResultados(timeId: Int, limite: Int = 5): Flow<List<PartidaEntity>>

    @Query("""
        SELECT * FROM partidas
        WHERE (timeCasaId = :timeId OR timeForaId = :timeId) AND jogada = 1
        ORDER BY rodada DESC
        LIMIT :limite
    """)
    suspend fun buscarUltimosResultados(timeId: Int, limite: Int = 5): List<PartidaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(partida: PartidaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(partidas: List<PartidaEntity>)

    @Query("""
        UPDATE partidas
        SET golsCasa = :golsCasa, golsFora = :golsFora, jogada = 1
        WHERE id = :partidaId
    """)
    suspend fun registrarResultado(partidaId: Int, golsCasa: Int, golsFora: Int)

    @Query("""
        UPDATE partidas
        SET penaltisCasa = :pCasa, penaltisForaId = :pFora
        WHERE id = :partidaId
    """)
    suspend fun registrarPenaltis(partidaId: Int, pCasa: Int, pFora: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirEscalacao(escalacoes: List<EscalacaoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirEventos(eventos: List<EventoPartidaEntity>)

    @Query("SELECT * FROM eventos_partida WHERE partidaId = :partidaId ORDER BY minuto")
    suspend fun buscarEventos(partidaId: Int): List<EventoPartidaEntity>

    @Query("SELECT campeonatoId FROM partidas WHERE id = :partidaId")
    suspend fun buscarCampeonatoId(partidaId: Int): Int?

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               t.nome AS nomeTime, t.escudoRes AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        INNER JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId = :campeonatoId AND ep.tipo = 'GOL'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeArtilheiros(campeonatoId: Int, limite: Int = 20): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               t.nome AS nomeTime, t.escudoRes AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        INNER JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId = :campeonatoId AND ep.tipo = 'ASSISTENCIA'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeAssistentes(campeonatoId: Int, limite: Int = 20): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               t.nome AS nomeTime, t.escudoRes AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        INNER JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId = :campeonatoId AND ep.tipo = 'GOL'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT 1
    """)
    suspend fun buscarArtilheiroTop1(campeonatoId: Int): ArtilheiroDto?

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               t.nome AS nomeTime, t.escudoRes AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        INNER JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId = :campeonatoId AND ep.tipo = 'ASSISTENCIA'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT 1
    """)
    suspend fun buscarAssisteTop1(campeonatoId: Int): ArtilheiroDto?

    // ── Consultas históricas (todas as temporadas) ──

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               t.nome AS nomeTime, t.escudoRes AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        INNER JOIN times t ON j.timeId = t.id
        WHERE ep.tipo = 'GOL'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeArtilheirosAllTime(limite: Int = 30): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               t.nome AS nomeTime, t.escudoRes AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        INNER JOIN times t ON j.timeId = t.id
        WHERE ep.tipo = 'ASSISTENCIA'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeAssistentesAllTime(limite: Int = 30): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT p.id AS partidaId, p.campeonatoId AS campeonatoId,
               c.nome AS nomeCampeonato, p.rodada AS rodada,
               p.timeCasaId AS timeCasaId, p.timeForaId AS timeForaId,
               p.golsCasa AS golsCasa, p.golsFora AS golsFora
        FROM partidas p
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        WHERE ((p.timeCasaId = :timeAId AND p.timeForaId = :timeBId)
            OR (p.timeCasaId = :timeBId AND p.timeForaId = :timeAId))
        AND p.jogada = 1
        ORDER BY c.temporadaId ASC, p.rodada ASC
    """)
    suspend fun buscarPartidasConfronto(timeAId: Int, timeBId: Int): List<ConfrontoPartidaDto>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               t.nome AS nomeTime, t.escudoRes AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        INNER JOIN times t ON j.timeId = t.id
        WHERE ((p.timeCasaId = :timeAId AND p.timeForaId = :timeBId)
            OR (p.timeCasaId = :timeBId AND p.timeForaId = :timeAId))
        AND ep.tipo = 'GOL'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    suspend fun buscarArtilheirosConfronto(timeAId: Int, timeBId: Int, limite: Int = 5): List<ArtilheiroDto>
}

data class ArtilheiroDto(
    val jogadorId: Int,
    val nomeJogador: String,
    val nomeAbrev: String,
    val nomeTime: String,
    val escudoRes: String = "",
    val total: Int
)

data class ConfrontoPartidaDto(
    val partidaId: Int,
    val campeonatoId: Int,
    val nomeCampeonato: String,
    val rodada: Int,
    val timeCasaId: Int,
    val timeForaId: Int,
    val golsCasa: Int,
    val golsFora: Int
)
