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

    /** Retorna todas as partidas da liga (fase IS NULL) já jogadas — usada para recalcular a classificação. */
    @Query("SELECT * FROM partidas WHERE campeonatoId = :campeonatoId AND jogada = 1 AND fase IS NULL")
    suspend fun buscarPartidasJogadasDeLiga(campeonatoId: Int): List<PartidaEntity>

    @Query("""
        SELECT * FROM partidas
        WHERE (timeCasaId = :timeId OR timeForaId = :timeId) AND jogada = 0
        ORDER BY ordemGlobal
        LIMIT 1
    """)
    suspend fun buscarProximaPartida(timeId: Int): PartidaEntity?

    @Query("""
        SELECT * FROM partidas
        WHERE (timeCasaId = :timeId OR timeForaId = :timeId) AND jogada = 1 AND campeonatoId = :campeonatoId
        ORDER BY rodada DESC
        LIMIT :limite
    """)
    fun observeUltimosResultados(timeId: Int, campeonatoId: Int, limite: Int = 5): Flow<List<PartidaEntity>>

    @Query("""
        SELECT * FROM partidas
        WHERE (timeCasaId = :timeId OR timeForaId = :timeId) AND jogada = 1 AND campeonatoId = :campeonatoId
        ORDER BY ordemGlobal DESC
        LIMIT :limite
    """)
    suspend fun buscarUltimosResultados(timeId: Int, campeonatoId: Int, limite: Int = 5): List<PartidaEntity>

    @Query("""
        SELECT * FROM partidas
        WHERE (timeCasaId = :timeId OR timeForaId = :timeId) AND jogada = 1 AND campeonatoId IN (:campeonatoIds)
        ORDER BY ordemGlobal DESC
        LIMIT :limite
    """)
    suspend fun buscarUltimosResultadosMultiCamp(timeId: Int, campeonatoIds: List<Int>, limite: Int = 5): List<PartidaEntity>

    @Query("SELECT * FROM partidas WHERE id = :id")
    suspend fun buscarPorId(id: Int): PartidaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(partida: PartidaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(partidas: List<PartidaEntity>)

    @Query("""
        UPDATE partidas
        SET golsCasa = :golsCasa, golsFora = :golsFora, jogada = 1,
            torcedores = :torcedores, receitaPartida = :receitaPartida
        WHERE id = :partidaId
    """)
    suspend fun registrarResultado(partidaId: Int, golsCasa: Int, golsFora: Int, torcedores: Int = 0, receitaPartida: Long = 0L)

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

    @Query("DELETE FROM partidas")
    suspend fun deleteAll()

    @Query("DELETE FROM escalacoes")
    suspend fun deleteAllEscalacoes()

    @Query("DELETE FROM eventos_partida")
    suspend fun deleteAllEventos()

    @Query("SELECT * FROM eventos_partida WHERE partidaId = :partidaId ORDER BY minuto")
    suspend fun buscarEventos(partidaId: Int): List<EventoPartidaEntity>

    @Query("SELECT campeonatoId FROM partidas WHERE id = :partidaId")
    suspend fun buscarCampeonatoId(partidaId: Int): Int?

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        LEFT JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId = :campeonatoId AND ep.tipo = 'GOL'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeArtilheiros(campeonatoId: Int, limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        LEFT JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId = :campeonatoId AND ep.tipo = 'ASSISTENCIA'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeAssistentes(campeonatoId: Int, limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, SUM(1) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        LEFT JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId IN (:campeonatoIds) AND ep.tipo = 'GOL'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeArtilheirosMulti(campeonatoIds: List<Int>, limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, SUM(1) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        LEFT JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId IN (:campeonatoIds) AND ep.tipo = 'ASSISTENCIA'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeAssistentesMulti(campeonatoIds: List<Int>, limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        LEFT JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId = :campeonatoId AND ep.tipo = 'GOL'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT 1
    """)
    suspend fun buscarArtilheiroTop1(campeonatoId: Int): ArtilheiroDto?

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        LEFT JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId = :campeonatoId AND ep.tipo = 'ASSISTENCIA'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT 1
    """)
    suspend fun buscarAssisteTop1(campeonatoId: Int): ArtilheiroDto?

    // ── Consultas históricas (todas as temporadas) ──

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        LEFT JOIN times t ON j.timeId = t.id
        WHERE ep.tipo = 'GOL'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeArtilheirosAllTime(limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        LEFT JOIN times t ON j.timeId = t.id
        WHERE ep.tipo = 'ASSISTENCIA'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeAssistentesAllTime(limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p  ON ep.partidaId   = p.id
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        INNER JOIN jogadores j ON ep.jogadorId   = j.id
        LEFT JOIN times t     ON j.timeId       = t.id
        WHERE ep.tipo = 'GOL' AND c.tipo IN (:tipos)
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeArtilheirosHistoricoFiltrado(tipos: List<String>, limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p  ON ep.partidaId   = p.id
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        INNER JOIN jogadores j ON ep.jogadorId   = j.id
        LEFT JOIN times t     ON j.timeId       = t.id
        WHERE ep.tipo = 'ASSISTENCIA' AND c.tipo IN (:tipos)
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeAssistentesHistoricoFiltrado(tipos: List<String>, limite: Int = 40): Flow<List<ArtilheiroDto>>

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
        SELECT p.id AS partidaId, p.campeonatoId AS campeonatoId,
               c.nome AS nomeCampeonato, p.rodada AS rodada,
               p.fase AS fase, p.ordemGlobal AS ordemGlobal,
               p.timeCasaId AS timeCasaId, tc.nome AS nomeCasa, tc.escudoRes AS escudoCasa,
               p.timeForaId AS timeForaId, tf.nome AS nomeFora, tf.escudoRes AS escudoFora,
               p.golsCasa AS golsCasa, p.golsFora AS golsFora, p.jogada AS jogada,
               p.torcedores AS torcedores, p.receitaPartida AS receitaPartida
        FROM partidas p
        INNER JOIN campeonatos c  ON p.campeonatoId = c.id
        INNER JOIN times tc ON p.timeCasaId = tc.id
        INNER JOIN times tf ON p.timeForaId = tf.id
        WHERE p.timeCasaId = :timeId OR p.timeForaId = :timeId
        ORDER BY p.ordemGlobal ASC
    """)
    fun observeCalendario(timeId: Int): Flow<List<CalendarioPartidaDto>>

    @Query("""
        SELECT p.id AS partidaId, p.campeonatoId AS campeonatoId,
               c.nome AS nomeCampeonato, p.rodada AS rodada,
               p.fase AS fase, p.ordemGlobal AS ordemGlobal,
               p.timeCasaId AS timeCasaId, tc.nome AS nomeCasa, tc.escudoRes AS escudoCasa,
               p.timeForaId AS timeForaId, tf.nome AS nomeFora, tf.escudoRes AS escudoFora,
               p.golsCasa AS golsCasa, p.golsFora AS golsFora, p.jogada AS jogada,
               p.torcedores AS torcedores, p.receitaPartida AS receitaPartida
        FROM partidas p
        INNER JOIN campeonatos c  ON p.campeonatoId = c.id
        INNER JOIN times tc ON p.timeCasaId = tc.id
        INNER JOIN times tf ON p.timeForaId = tf.id
        WHERE p.timeCasaId = :timeId AND p.jogada = 1 AND p.receitaPartida IS NOT NULL
        ORDER BY p.ordemGlobal DESC
    """)
    fun observeReceitasPartidas(timeId: Int): Flow<List<CalendarioPartidaDto>>

    @Query("SELECT * FROM partidas WHERE campeonatoId = :campeonatoId ORDER BY confrontoId, rodada")
    suspend fun buscarTodasPorCampeonato(campeonatoId: Int): List<PartidaEntity>

    @Query("SELECT COALESCE(MAX(rodada), 0) FROM partidas WHERE campeonatoId = :campeonatoId AND fase IS NULL")
    suspend fun maxRodada(campeonatoId: Int): Int

    @Query("""
        SELECT p.id AS partidaId, p.campeonatoId AS campeonatoId,
               c.nome AS nomeCampeonato, p.rodada AS rodada,
               p.fase AS fase, p.ordemGlobal AS ordemGlobal,
               p.timeCasaId AS timeCasaId, tc.nome AS nomeCasa, tc.escudoRes AS escudoCasa,
               p.timeForaId AS timeForaId, tf.nome AS nomeFora, tf.escudoRes AS escudoFora,
               p.golsCasa AS golsCasa, p.golsFora AS golsFora, p.jogada AS jogada,
               p.torcedores AS torcedores, p.receitaPartida AS receitaPartida
        FROM partidas p
        INNER JOIN campeonatos c  ON p.campeonatoId = c.id
        INNER JOIN times tc ON p.timeCasaId = tc.id
        INNER JOIN times tf ON p.timeForaId = tf.id
        WHERE p.campeonatoId = :campeonatoId AND p.rodada = :rodada AND p.fase IS NULL
        ORDER BY p.id
    """)
    fun observeRodada(campeonatoId: Int, rodada: Int): Flow<List<CalendarioPartidaDto>>

    @Query("""
        SELECT p.id AS partidaId, p.campeonatoId AS campeonatoId,
               c.nome AS nomeCampeonato, p.rodada AS rodada,
               p.fase AS fase, p.ordemGlobal AS ordemGlobal,
               p.timeCasaId AS timeCasaId, tc.nome AS nomeCasa, tc.escudoRes AS escudoCasa,
               p.timeForaId AS timeForaId, tf.nome AS nomeFora, tf.escudoRes AS escudoFora,
               p.golsCasa AS golsCasa, p.golsFora AS golsFora, p.jogada AS jogada,
               p.torcedores AS torcedores, p.receitaPartida AS receitaPartida,
               p.penaltisCasa AS penaltisCasa, p.penaltisForaId AS penaltisForaId
        FROM partidas p
        INNER JOIN campeonatos c  ON p.campeonatoId = c.id
        INNER JOIN times tc ON p.timeCasaId = tc.id
        INNER JOIN times tf ON p.timeForaId = tf.id
        WHERE p.campeonatoId = :campeonatoId AND p.fase IS NOT NULL
        ORDER BY p.rodada, p.id
    """)
    fun observeArgKnockout(campeonatoId: Int): Flow<List<CalendarioPartidaDto>>

    @Query("""
        SELECT p.id AS partidaId, p.confrontoId AS confrontoId, p.fase AS fase,
               p.rodada AS rodada, p.timeCasaId AS timeCasaId,
               tc.nome AS nomeCasa, tc.escudoRes AS escudoCasa,
               p.timeForaId AS timeForaId, tf.nome AS nomeFora, tf.escudoRes AS escudoFora,
               p.golsCasa AS golsCasa, p.golsFora AS golsFora, p.jogada AS jogada,
               p.penaltisCasa AS penaltisCasa, p.penaltisForaId AS penaltisForaId
        FROM partidas p
        INNER JOIN times tc ON p.timeCasaId = tc.id
        INNER JOIN times tf ON p.timeForaId = tf.id
        WHERE p.campeonatoId = :copaId
        ORDER BY p.fase, p.confrontoId, p.rodada
    """)
    fun observeCopaPartidas(copaId: Int): Flow<List<CopaPartidaDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        LEFT JOIN times t ON j.timeId = t.id
        WHERE ((p.timeCasaId = :timeAId AND p.timeForaId = :timeBId)
            OR (p.timeCasaId = :timeBId AND p.timeForaId = :timeAId))
        AND ep.tipo = 'GOL'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    suspend fun buscarArtilheirosConfronto(timeAId: Int, timeBId: Int, limite: Int = 5): List<ArtilheiroDto>

    // ── Partidas de uma equipe em um campeonato específico ──

    @Query("""
        SELECT * FROM partidas
        WHERE campeonatoId = :campeonatoId
        AND (timeCasaId = :timeId OR timeForaId = :timeId)
        AND jogada = 1
        ORDER BY rodada ASC
    """)
    suspend fun buscarPartidasDaEquipe(campeonatoId: Int, timeId: Int): List<PartidaEntity>

    @Query("""
        SELECT p.campeonatoId AS campeonatoId,
               c.nome        AS nomeCampeonato,
               c.temporadaId AS temporadaId,
               p.timeCasaId  AS timeCasaId,
               p.timeForaId  AS timeForaId,
               p.golsCasa    AS golsCasa,
               p.golsFora    AS golsFora,
               p.fase        AS fase
        FROM partidas p
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        WHERE c.tipo = 'COPA_NACIONAL'
        AND (p.timeCasaId = :timeId OR p.timeForaId = :timeId)
        AND p.jogada = 1
        ORDER BY c.temporadaId ASC, p.rodada ASC
    """)
    suspend fun buscarHistoricoCopaDoTime(timeId: Int): List<PartidaCopaHistoricoDto>

    @Query("""
        SELECT p.campeonatoId AS campeonatoId,
               c.nome        AS nomeCampeonato,
               c.temporadaId AS temporadaId,
               p.timeCasaId  AS timeCasaId,
               p.timeForaId  AS timeForaId,
               p.golsCasa    AS golsCasa,
               p.golsFora    AS golsFora,
               p.fase        AS fase
        FROM partidas p
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        WHERE c.tipo = 'SUPERCOPA'
        AND (p.timeCasaId = :timeId OR p.timeForaId = :timeId)
        AND p.jogada = 1
        ORDER BY c.temporadaId ASC, p.rodada ASC
    """)
    suspend fun buscarHistoricoSupercopaDoTime(timeId: Int): List<PartidaCopaHistoricoDto>

    // ── Estatísticas de jogadores por equipe ──

    @Query("""
        SELECT j.id AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COUNT(DISTINCT ep.partidaId) AS partidas,
               SUM(CASE WHEN ep.tipo = 'GOL' THEN 1 ELSE 0 END) AS gols,
               SUM(CASE WHEN ep.tipo = 'ASSISTENCIA' THEN 1 ELSE 0 END) AS assistencias
        FROM eventos_partida ep
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        INNER JOIN partidas p ON ep.partidaId = p.id
        WHERE p.campeonatoId = :campeonatoId AND j.timeId = :timeId
        GROUP BY j.id
        ORDER BY gols DESC, assistencias DESC
    """)
    suspend fun buscarEstatisticasJogadoresDaEquipe(
        campeonatoId: Int, timeId: Int
    ): List<EstatisticaJogadorDto>

    @Query("""
        SELECT j.id AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COUNT(DISTINCT ep.partidaId) AS partidas,
               SUM(CASE WHEN ep.tipo = 'GOL' THEN 1 ELSE 0 END) AS gols,
               SUM(CASE WHEN ep.tipo = 'ASSISTENCIA' THEN 1 ELSE 0 END) AS assistencias
        FROM eventos_partida ep
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        WHERE j.timeId = :timeId
        GROUP BY j.id
        ORDER BY gols DESC, assistencias DESC
    """)
    suspend fun buscarEstatisticasJogadoresAllTime(timeId: Int): List<EstatisticaJogadorDto>
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

data class CalendarioPartidaDto(
    val partidaId: Int,
    val campeonatoId: Int,
    val nomeCampeonato: String,
    val rodada: Int,
    val fase: String?,
    val ordemGlobal: Int,
    val timeCasaId: Int,
    val nomeCasa: String,
    val escudoCasa: String,
    val timeForaId: Int,
    val nomeFora: String,
    val escudoFora: String,
    val golsCasa: Int?,
    val golsFora: Int?,
    val jogada: Boolean,
    val torcedores: Int? = null,
    val receitaPartida: Long? = null,
    val penaltisCasa: Int? = null,
    val penaltisForaId: Int? = null
)

data class CopaPartidaDto(
    val partidaId: Int,
    val confrontoId: Int,
    val fase: String,
    val rodada: Int,
    val timeCasaId: Int,
    val nomeCasa: String,
    val escudoCasa: String,
    val timeForaId: Int,
    val nomeFora: String,
    val escudoFora: String,
    val golsCasa: Int?,
    val golsFora: Int?,
    val jogada: Boolean,
    val penaltisCasa: Int? = null,
    val penaltisForaId: Int? = null
)

data class EstatisticaJogadorDto(
    val jogadorId: Int,
    val nomeJogador: String,
    val nomeAbrev: String,
    val partidas: Int,
    val gols: Int,
    val assistencias: Int
)

data class PartidaCopaHistoricoDto(
    val campeonatoId: Int,
    val nomeCampeonato: String,
    val temporadaId: Int,
    val timeCasaId: Int,
    val timeForaId: Int,
    val golsCasa: Int?,
    val golsFora: Int?,
    val fase: String?
)
