package br.com.managerfoot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.managerfoot.data.database.entities.EscalacaoEntity
import br.com.managerfoot.data.database.entities.EventoPartidaEntity
import br.com.managerfoot.data.database.entities.PartidaEntity
import br.com.managerfoot.data.database.entities.TipoCampeonato
import kotlinx.coroutines.flow.Flow

/**
 * Campanha agregada de um clube numa competição, para o Ranking Mundial (IFFHS).
 * Uma linha por clube/competição/fase, com vitórias e empates contados; o peso em
 * pontos é aplicado em Kotlin.
 */
data class CampanhaMundialDto(
    val timeId: Int,
    val nomeTime: String,
    val escudoRes: String,
    val pais: String,
    val tipo: TipoCampeonato,
    val paisCampeonato: String,
    val nomeCampeonato: String,
    val fase: String?,
    val vitorias: Int,
    val empates: Int
)

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

    /**
     * Migração: corrige os ordemGlobal de jogos da Copa do Brasil criados com os valores
     * antigos (2, 7, 15, 35, ...) para os novos valores que se intercalam corretamente
     * entre as rodadas do Brasileirão.
     * Filtra apenas campeonatos do tipo COPA_NACIONAL para não afetar a Copa Argentina.
     */
    @Query("""
        UPDATE partidas SET ordemGlobal = CASE ordemGlobal
            WHEN 2   THEN 13
            WHEN 7   THEN 33
            WHEN 15  THEN 53
            WHEN 35  THEN 83
            WHEN 95  THEN 133
            WHEN 115 THEN 163
            WHEN 175 THEN 207
            WHEN 195 THEN 233
            WHEN 245 THEN 278
            WHEN 265 THEN 313
            WHEN 335 THEN 357
            WHEN 355 THEN 383
            ELSE ordemGlobal
        END
        WHERE ordemGlobal IN (2, 7, 15, 35, 95, 115, 175, 195, 245, 265, 335, 355)
          AND campeonatoId IN (
              SELECT id FROM campeonatos WHERE tipo = 'COPA_NACIONAL' AND (pais IS NULL OR pais != 'Argentina')
          )
    """)
    suspend fun migrarOrdemGlobalCopaBrasil()

    @Query("""
        SELECT * FROM partidas
        WHERE (timeCasaId = :timeId OR timeForaId = :timeId) AND jogada = 0
          AND campeonatoId NOT IN (SELECT id FROM campeonatos WHERE tipo = 'AMISTOSO')
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

    /**
     * Últimas partidas jogadas por um time em qualquer competição (cálculo de moral).
     *
     * A ordenação precisa da TEMPORADA na frente, e esta é a razão: `ordemGlobal`
     * é o relógio de UMA temporada e zera na virada. Ordenando só por ele, as 5
     * partidas de maior `ordemGlobal` de um clube não são as 5 mais recentes —
     * são as rodadas finais das temporadas PASSADAS, que têm os números altos. As
     * partidas antigas não são apagadas do banco, então a moral de um elenco
     * ficava presa no fim da temporada anterior por quase toda a temporada nova.
     */
    @Query("""
        SELECT p.* FROM partidas p
        INNER JOIN campeonatos c ON c.id = p.campeonatoId
        WHERE (p.timeCasaId = :timeId OR p.timeForaId = :timeId) AND p.jogada = 1
        ORDER BY c.temporadaId DESC, p.ordemGlobal DESC
        LIMIT :limite
    """)
    suspend fun buscarUltimasPartidasDoTime(timeId: Int, limite: Int = 5): List<PartidaEntity>

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

    /**
     * Atualiza SO o placar, sem tocar em torcedores nem na receita de bilheteria.
     * E o que a prorrogacao precisa: os gols dos 30 minutos extra entram no placar
     * de uma partida que ja foi registrada, e o publico nao muda por isso.
     */
    @Query("UPDATE partidas SET golsCasa = :golsCasa, golsFora = :golsFora WHERE id = :partidaId")
    suspend fun atualizarPlacar(partidaId: Int, golsCasa: Int, golsFora: Int)

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

    /**
     * Campanha de um clube numa competição, dentro da janela do Ranking Mundial:
     * vitórias e empates agrupados por competição e fase. O peso de cada linha é
     * aplicado em Kotlin (ver RankingMundial), então o SQL não precisa conhecer
     * níveis de liga.
     *
     * A janela é identificada por `campeonatos.temporadaId`, e NÃO pela tabela
     * `temporadas`: essa tabela nunca é populada — nada no app insere um
     * TemporadaEntity — e o `INNER JOIN temporadas` que existia aqui não casava
     * nenhuma linha, o que deixava o ranking mundial permanentemente vazio.
     * `temporadaId` é um inteiro que cresce de 1 em 1 por temporada, então
     * `:temporadaAtual - 1` é a temporada anterior, exatamente como `ano - 1` era.
     */
    @Query("""
        SELECT x.timeId        AS timeId,
               tm.nome         AS nomeTime,
               tm.escudoRes    AS escudoRes,
               tm.pais         AS pais,
               x.tipo          AS tipo,
               x.paisCampeonato AS paisCampeonato,
               x.nomeCampeonato AS nomeCampeonato,
               x.fase          AS fase,
               SUM(x.venceu)   AS vitorias,
               SUM(x.empatou)  AS empates
        FROM (
            SELECT p.timeCasaId AS timeId, c.tipo AS tipo, c.pais AS paisCampeonato,
                   c.nome AS nomeCampeonato, p.fase AS fase,
                   CASE WHEN p.golsCasa >  p.golsFora THEN 1 ELSE 0 END AS venceu,
                   CASE WHEN p.golsCasa =  p.golsFora THEN 1 ELSE 0 END AS empatou
            FROM partidas p
            INNER JOIN campeonatos c ON c.id = p.campeonatoId
            WHERE p.jogada = 1 AND p.golsCasa IS NOT NULL AND p.golsFora IS NOT NULL
              AND (  (c.temporadaId = :temporadaAtual     AND p.ordemGlobal <= :ordemGlobalAtual)
                  OR (c.temporadaId = :temporadaAtual - 1 AND p.ordemGlobal >  :ordemGlobalAtual) )
            UNION ALL
            SELECT p.timeForaId AS timeId, c.tipo AS tipo, c.pais AS paisCampeonato,
                   c.nome AS nomeCampeonato, p.fase AS fase,
                   CASE WHEN p.golsFora >  p.golsCasa THEN 1 ELSE 0 END AS venceu,
                   CASE WHEN p.golsFora =  p.golsCasa THEN 1 ELSE 0 END AS empatou
            FROM partidas p
            INNER JOIN campeonatos c ON c.id = p.campeonatoId
            WHERE p.jogada = 1 AND p.golsCasa IS NOT NULL AND p.golsFora IS NOT NULL
              AND (  (c.temporadaId = :temporadaAtual     AND p.ordemGlobal <= :ordemGlobalAtual)
                  OR (c.temporadaId = :temporadaAtual - 1 AND p.ordemGlobal >  :ordemGlobalAtual) )
        ) x
        INNER JOIN times tm ON tm.id = x.timeId
        GROUP BY x.timeId, x.tipo, x.paisCampeonato, x.nomeCampeonato, x.fase
    """)
    fun observeCampanhasMundial(temporadaAtual: Int, ordemGlobalAtual: Int): Flow<List<CampanhaMundialDto>>

    /**
     * Relógio da janela: o maior ordemGlobal já jogado na temporada corrente. Como
     * ordemGlobal é monotônico dentro da temporada, ele faz o papel de "agora" e
     * dispensa data nas partidas. Zero quando a temporada ainda não teve jogos.
     */
    @Query("""
        SELECT COALESCE(MAX(p.ordemGlobal), 0)
        FROM partidas p
        INNER JOIN campeonatos c ON c.id = p.campeonatoId
        WHERE p.jogada = 1 AND c.temporadaId = :temporadaAtual
    """)
    fun observeOrdemGlobalAtual(temporadaAtual: Int): Flow<Int>

    /** O mesmo relógio, para quem precisa carimbar o "agora" numa gravação. */
    @Query("""
        SELECT COALESCE(MAX(p.ordemGlobal), 0)
        FROM partidas p
        INNER JOIN campeonatos c ON c.id = p.campeonatoId
        WHERE p.jogada = 1 AND c.temporadaId = :temporadaAtual
    """)
    suspend fun ordemGlobalAtual(temporadaAtual: Int): Int

    @Query("DELETE FROM partidas")
    suspend fun deleteAll()

    @Query("DELETE FROM partidas WHERE campeonatoId = :campeonatoId")
    suspend fun deletePorCampeonato(campeonatoId: Int)

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

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        LEFT JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId IN (:campeonatoIds) AND ep.tipo = 'GOL'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT 1
    """)
    suspend fun buscarArtilheiroTop1Multi(campeonatoIds: List<Int>): ArtilheiroDto?

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p ON ep.partidaId = p.id
        INNER JOIN jogadores j ON ep.jogadorId = j.id
        LEFT JOIN times t ON j.timeId = t.id
        WHERE p.campeonatoId IN (:campeonatoIds) AND ep.tipo = 'ASSISTENCIA'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT 1
    """)
    suspend fun buscarAssisteTop1Multi(campeonatoIds: List<Int>): ArtilheiroDto?

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

    // ── Histórico ciente do PAÍS ──
    // Vários países compartilham o mesmo `tipo` (ex.: NACIONAL_DIVISAO1 = Brasil A,
    // Premier, La Liga, Serie A). Filtrar só por tipo mistura países no histórico.
    // Estas variantes casam também o país do campeonato.
    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p  ON ep.partidaId   = p.id
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        INNER JOIN jogadores j ON ep.jogadorId   = j.id
        LEFT JOIN times t     ON j.timeId       = t.id
        WHERE ep.tipo = 'GOL' AND c.pais = :pais AND c.tipo IN (:tipos)
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeArtilheirosHistoricoPaisTipo(pais: String, tipos: List<String>, limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p  ON ep.partidaId   = p.id
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        INNER JOIN jogadores j ON ep.jogadorId   = j.id
        LEFT JOIN times t     ON j.timeId       = t.id
        WHERE ep.tipo = 'ASSISTENCIA' AND c.pais = :pais AND c.tipo IN (:tipos)
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeAssistentesHistoricoPaisTipo(pais: String, tipos: List<String>, limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p  ON ep.partidaId   = p.id
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        INNER JOIN jogadores j ON ep.jogadorId   = j.id
        LEFT JOIN times t     ON j.timeId       = t.id
        WHERE ep.tipo = 'GOL' AND c.tipo = 'COPA_NACIONAL' AND c.pais = 'Argentina'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeArtilheirosHistoricoCopaArgentina(limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p  ON ep.partidaId   = p.id
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        INNER JOIN jogadores j ON ep.jogadorId   = j.id
        LEFT JOIN times t     ON j.timeId       = t.id
        WHERE ep.tipo = 'ASSISTENCIA' AND c.tipo = 'COPA_NACIONAL' AND c.pais = 'Argentina'
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeAssistentesHistoricoCopaArgentina(limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p  ON ep.partidaId   = p.id
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        INNER JOIN jogadores j ON ep.jogadorId   = j.id
        LEFT JOIN times t     ON j.timeId       = t.id
        WHERE ep.tipo = 'GOL' AND c.tipo = 'COPA_NACIONAL' AND (c.pais IS NULL OR c.pais != 'Argentina')
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeArtilheirosHistoricoCopaBrasil(limite: Int = 40): Flow<List<ArtilheiroDto>>

    @Query("""
        SELECT ep.jogadorId AS jogadorId, j.nome AS nomeJogador, j.nomeAbreviado AS nomeAbrev,
               COALESCE(t.nome, 'Aposentado') AS nomeTime, COALESCE(t.escudoRes, '') AS escudoRes, COUNT(*) AS total
        FROM eventos_partida ep
        INNER JOIN partidas p  ON ep.partidaId   = p.id
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        INNER JOIN jogadores j ON ep.jogadorId   = j.id
        LEFT JOIN times t     ON j.timeId       = t.id
        WHERE ep.tipo = 'ASSISTENCIA' AND c.tipo = 'COPA_NACIONAL' AND (c.pais IS NULL OR c.pais != 'Argentina')
        GROUP BY ep.jogadorId
        ORDER BY total DESC
        LIMIT :limite
    """)
    fun observeAssistentesHistoricoCopaBrasil(limite: Int = 40): Flow<List<ArtilheiroDto>>

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
               c.calendarioVersao AS calendarioVersao,
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
               c.calendarioVersao AS calendarioVersao,
               p.timeCasaId AS timeCasaId, tc.nome AS nomeCasa, tc.escudoRes AS escudoCasa,
               p.timeForaId AS timeForaId, tf.nome AS nomeFora, tf.escudoRes AS escudoFora,
               p.golsCasa AS golsCasa, p.golsFora AS golsFora, p.jogada AS jogada,
               p.torcedores AS torcedores, p.receitaPartida AS receitaPartida
        FROM partidas p
        INNER JOIN campeonatos c  ON p.campeonatoId = c.id
        INNER JOIN times tc ON p.timeCasaId = tc.id
        INNER JOIN times tf ON p.timeForaId = tf.id
        WHERE p.timeCasaId = :timeId AND p.jogada = 1 AND p.receitaPartida IS NOT NULL
        -- Temporada na frente: ordenar só por ordemGlobal intercala as temporadas,
        -- porque o relógio zera na virada. Ver buscarUltimasPartidasDoTime.
        ORDER BY c.temporadaId DESC, p.ordemGlobal DESC
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
               c.calendarioVersao AS calendarioVersao,
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

    /**
     * A mesma rodada, SEM o filtro de fase.
     *
     * Existe para a Libertadores, onde a Pré e a fase de grupos dividem a
     * numeração de rodadas do mesmo campeonato: as partidas da Pré têm `fase`
     * preenchida, e [observeRodada] as esconderia do calendário por rodada.
     */
    @Query("""
        SELECT p.id AS partidaId, p.campeonatoId AS campeonatoId,
               c.nome AS nomeCampeonato, p.rodada AS rodada,
               p.fase AS fase, p.ordemGlobal AS ordemGlobal,
               c.calendarioVersao AS calendarioVersao,
               p.timeCasaId AS timeCasaId, tc.nome AS nomeCasa, tc.escudoRes AS escudoCasa,
               p.timeForaId AS timeForaId, tf.nome AS nomeFora, tf.escudoRes AS escudoFora,
               p.golsCasa AS golsCasa, p.golsFora AS golsFora, p.jogada AS jogada,
               p.torcedores AS torcedores, p.receitaPartida AS receitaPartida
        FROM partidas p
        INNER JOIN campeonatos c  ON p.campeonatoId = c.id
        INNER JOIN times tc ON p.timeCasaId = tc.id
        INNER JOIN times tf ON p.timeForaId = tf.id
        WHERE p.campeonatoId = :campeonatoId AND p.rodada = :rodada
        ORDER BY p.id
    """)
    fun observeRodadaComFases(campeonatoId: Int, rodada: Int): Flow<List<CalendarioPartidaDto>>

    @Query("""
        SELECT p.id AS partidaId, p.campeonatoId AS campeonatoId,
               c.nome AS nomeCampeonato, p.rodada AS rodada,
               p.fase AS fase, p.ordemGlobal AS ordemGlobal,
               c.calendarioVersao AS calendarioVersao,
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

    /**
     * Partidas de mata-mata de uma copa, para o chaveamento.
     *
     * O filtro `fase IS NOT NULL AND confrontoId IS NOT NULL` não é cosmético:
     * [CopaPartidaDto] declara `fase` e `confrontoId` como NÃO-NULOS, e a Fase de
     * Liga da Champions grava as suas 144 partidas justamente com os dois nulos
     * — é o que as faz alimentar a tabela de 36 clubes em vez do chaveamento.
     * Sem o filtro, o Room tenta escrever null nesses campos e a tela de Copas
     * quebra ao abrir, assim que passa a existir uma Champions. Para as demais
     * copas o filtro é inócuo: toda partida delas tem fase e confronto.
     */
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
          AND p.fase IS NOT NULL
          AND p.confrontoId IS NOT NULL
        ORDER BY p.fase, p.confrontoId, p.rodada
    """)
    fun observeCopaPartidas(copaId: Int): Flow<List<CopaPartidaDto>>

    /**
     * Partidas de uma copa de JOGO ÚNICO (ex.: Copa Intercontinental), cuja
     * decisão não tem `confrontoId` (não é um confronto ida/volta). Reusa o
     * [CopaPartidaDto] com COALESCE nos campos não-nulos, para que o chaveamento
     * consiga exibir a final mesmo sem confronto — ao contrário de
     * [observeCopaPartidas], que exige `confrontoId IS NOT NULL`.
     */
    @Query("""
        SELECT p.id AS partidaId, COALESCE(p.confrontoId, 0) AS confrontoId,
               COALESCE(p.fase, 'Final') AS fase,
               p.rodada AS rodada, p.timeCasaId AS timeCasaId,
               tc.nome AS nomeCasa, tc.escudoRes AS escudoCasa,
               p.timeForaId AS timeForaId, tf.nome AS nomeFora, tf.escudoRes AS escudoFora,
               p.golsCasa AS golsCasa, p.golsFora AS golsFora, p.jogada AS jogada,
               p.penaltisCasa AS penaltisCasa, p.penaltisForaId AS penaltisForaId
        FROM partidas p
        INNER JOIN times tc ON p.timeCasaId = tc.id
        INNER JOIN times tf ON p.timeForaId = tf.id
        WHERE p.campeonatoId = :copaId
        ORDER BY p.rodada, p.id
    """)
    fun observeCopaPartidasJogoUnico(copaId: Int): Flow<List<CopaPartidaDto>>

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

    /**
     * Histórico de mata-mata do clube: copas nacionais E competições continentais
     * (Libertadores, Sul-Americana, Champions, Europa League, Intercontinental e
     * Recopa, todas gravadas como CONTINENTAL). As continentais ficavam de fora
     * quando o filtro era só `COPA_NACIONAL`, então nenhuma delas aparecia no
     * histórico de desempenho — justamente as que têm fase eliminatória.
     */
    @Query("""
        SELECT p.campeonatoId   AS campeonatoId,
               c.nome           AS nomeCampeonato,
               c.temporadaId    AS temporadaId,
               c.tipo           AS tipo,
               c.formato        AS formato,
               p.timeCasaId     AS timeCasaId,
               p.timeForaId     AS timeForaId,
               p.golsCasa       AS golsCasa,
               p.golsFora       AS golsFora,
               p.penaltisCasa   AS penaltisCasa,
               p.penaltisForaId AS penaltisForaId,
               p.fase           AS fase
        FROM partidas p
        INNER JOIN campeonatos c ON p.campeonatoId = c.id
        WHERE c.tipo IN ('COPA_NACIONAL', 'CONTINENTAL')
        AND (p.timeCasaId = :timeId OR p.timeForaId = :timeId)
        AND p.jogada = 1
        ORDER BY c.temporadaId ASC, p.rodada ASC
    """)
    suspend fun buscarHistoricoCopaDoTime(timeId: Int): List<PartidaCopaHistoricoDto>

    @Query("""
        SELECT p.campeonatoId   AS campeonatoId,
               c.nome           AS nomeCampeonato,
               c.temporadaId    AS temporadaId,
               c.tipo           AS tipo,
               c.formato        AS formato,
               p.timeCasaId     AS timeCasaId,
               p.timeForaId     AS timeForaId,
               p.golsCasa       AS golsCasa,
               p.golsFora       AS golsFora,
               p.penaltisCasa   AS penaltisCasa,
               p.penaltisForaId AS penaltisForaId,
               p.fase           AS fase
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

    /**
     * Busca todos os eventos (GOL, ASSISTENCIA, PARTICIPOU, etc) de UM jogador.
     * Usa LEFT JOIN para campeonatos/temporadas para sobreviver a saves antigos
     * onde o vínculo da partida com a temporada possa estar quebrado — nesse
     * caso `ano` vem como 0 e o consumidor usa fallback.
     */
    @Query("""
        SELECT
            ep.partidaId   AS partidaId,
            ep.tipo        AS tipo,
            p.timeCasaId   AS timeCasaId,
            p.timeForaId   AS timeForaId,
            p.campeonatoId AS campeonatoId,
            p.ordemGlobal  AS ordemGlobal,
            COALESCE(c.temporadaId, 0) AS temporadaId,
            COALESCE(t.ano, 0)         AS ano
        FROM eventos_partida ep
        INNER JOIN partidas p     ON p.id = ep.partidaId
        LEFT JOIN campeonatos c   ON c.id = p.campeonatoId
        LEFT JOIN temporadas t    ON t.id = c.temporadaId
        WHERE ep.jogadorId = :jogadorId
        ORDER BY t.ano ASC, p.ordemGlobal ASC
    """)
    suspend fun buscarEventosCarreiraJogador(jogadorId: Int): List<EventoCarreiraDto>
}

/** DTO retornado por `buscarEventosCarreiraJogador`. Cada evento sabe em qual
 *  partida ocorreu (com os 2 times) e em qual temporada/ano. O time do jogador
 *  no momento é derivado depois cruzando com TransferenciaEntity. */
data class EventoCarreiraDto(
    val partidaId: Int,
    val tipo: String,         // valores do enum TipoEvento (GOL, ASSISTENCIA, PARTICIPOU, etc.)
    val timeCasaId: Int,
    val timeForaId: Int,
    val campeonatoId: Int,
    val ordemGlobal: Int,
    val temporadaId: Int,
    val ano: Int
)

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

    /**
     * A escala de `ordemGlobal` desta partida. Vem do campeonato, no mesmo JOIN
     * de [nomeCampeonato].
     *
     * Sem ela a tela do calendário leria o valor gravado pela curva antiga: numa
     * temporada da grade, onde `ordemGlobal` já É o dia do ano, a data sairia
     * completamente errada.
     */
    val calendarioVersao: Int = br.com.managerfoot.data.database.entities.CalendarioVersao.CURVA,
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
    // Nome do enum TipoCampeonato, para a UI distinguir copa nacional de
    // continental sem adivinhar pelo nome do campeonato.
    val tipo: String,
    // Nome do enum FormatoCampeonato. É o que diz se as partidas sem fase são de
    // fase de GRUPOS (Libertadores, Sul-Americana) ou de fase de LIGA (Champions,
    // Europa League) — as duas gravam `fase = null`.
    val formato: String,
    val timeCasaId: Int,
    val timeForaId: Int,
    val golsCasa: Int?,
    val golsFora: Int?,
    // Necessários para saber quem levantou a taça numa final decidida nos
    // pênaltis — sem eles o agregado empatado não diz campeão nem vice.
    val penaltisCasa: Int?,
    val penaltisForaId: Int?,
    val fase: String?
)
