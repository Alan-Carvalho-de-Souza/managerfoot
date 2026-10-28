package br.com.managerfoot.data.dao

import br.com.managerfoot.data.database.entities.CalendarioVersao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.managerfoot.data.database.entities.FinancaEntity
import br.com.managerfoot.data.database.entities.TipoTransferencia
import br.com.managerfoot.data.database.entities.TransferenciaEntity
import br.com.managerfoot.domain.engine.CalendarioDoJogo
import br.com.managerfoot.domain.engine.DataDoJogo
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────
//  TransferenciaDetalhe — resultado de query JOIN
// ─────────────────────────────────────────────
data class TransferenciaDetalhe(
    val id: Int,
    val jogadorNome: String,
    val posicao: String,
    val origemNome: String?,
    val origemEscudo: String?,
    val destinoNome: String?,
    val destinoEscudo: String?,
    val valor: Long,
    val tipo: TipoTransferencia,
    val temporadaId: Int,
    val mes: Int,
    val ano: Int,
    val ordemGlobal: Int,
    /**
     * Nome de um campeonato da temporada, de onde sai o ano quando a própria
     * transferência não o traz. É o caso de todo registro anterior à coluna
     * `ano`: os campeonatos das temporadas passadas nunca são apagados do save,
     * então o ano de uma venda antiga continua recuperável.
     */
    val nomeTemporada: String? = null,

    /**
     * A escala de `ordemGlobal` da temporada da negociação. Uma transferência
     * não pertence a competição nenhuma, então ela não tem como saber sozinha se
     * o relógio dela é um slot ou um dia — quem sabe é a temporada, e é por isso
     * que isto vem da mesma subquery de [nomeTemporada].
     *
     * O padrão é [CalendarioVersao.CURVA] porque é o que todo registro gravado
     * antes da grade é.
     */
    val calendarioVersaoTemporada: Int = CalendarioVersao.CURVA
) {

    /** O ano da negociação, do carimbo dela ou, na falta dele, da temporada. */
    val anoEfetivo: Int
        get() = if (ano > 0) ano
                else CalendarioDoJogo.anoDoNomeDoCampeonato(nomeTemporada ?: "")

    /**
     * A data exata da negociação.
     *
     * O dia sai do relógio da temporada (`ordemGlobal`), pela mesma conversão
     * que data as partidas. Sem relógio — janela de janeiro, antes do primeiro
     * jogo, ou registro anterior a esta coluna — resta o mês, e o dia fica 0:
     * a tela mostra "mar 2027" em vez de inventar um dia.
     */
    val data: DataDoJogo
        get() = if (ordemGlobal > 0) CalendarioDoJogo.ordemGlobalParaData(
            ordemGlobal, anoEfetivo, nomeTemporada ?: "", calendarioVersaoTemporada
        )
                else DataDoJogo(anoEfetivo, mes, 0)
}

// ─────────────────────────────────────────────
//  FinancaDao
// ─────────────────────────────────────────────
@Dao
interface FinancaDao {

    @Query("SELECT * FROM financas WHERE timeId = :timeId AND temporadaId = :temporadaId AND mes = :mes LIMIT 1")
    suspend fun buscarPorMes(timeId: Int, temporadaId: Int, mes: Int): FinancaEntity?

    @Query("SELECT * FROM financas WHERE timeId = :timeId AND temporadaId = :temporadaId ORDER BY mes")
    fun observeExtratoAnual(timeId: Int, temporadaId: Int): Flow<List<FinancaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(financa: FinancaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTransferencia(transferencia: TransferenciaEntity): Long

    /** Todas as transferências de um jogador específico, ordenadas
     *  cronologicamente — usado para reconstruir o histórico de carreira. */
    @Query("""
        SELECT * FROM transferencias
        WHERE jogadorId = :jogadorId
        ORDER BY temporadaId ASC, ordemGlobal ASC, id ASC
    """)
    suspend fun buscarTransferenciasDoJogador(jogadorId: Int): List<TransferenciaEntity>

    @Query("DELETE FROM financas")
    suspend fun deleteAll()

    @Query("DELETE FROM transferencias")
    suspend fun deleteAllTransferencias()

    @Query("""
        SELECT t.id, j.nomeAbreviado AS jogadorNome, j.posicao AS posicao,
               orig.nome AS origemNome, orig.escudoRes AS origemEscudo,
               dest.nome AS destinoNome, dest.escudoRes AS destinoEscudo,
               t.valor, t.tipo, t.temporadaId, t.mes, t.ano, t.ordemGlobal,
               (SELECT c.nome FROM campeonatos c
                 WHERE c.temporadaId = t.temporadaId
                 ORDER BY c.id LIMIT 1) AS nomeTemporada,
               (SELECT c.calendarioVersao FROM campeonatos c
                 WHERE c.temporadaId = t.temporadaId
                 ORDER BY c.id LIMIT 1) AS calendarioVersaoTemporada
        FROM transferencias t
        INNER JOIN jogadores j ON j.id = t.jogadorId
        LEFT JOIN times orig ON orig.id = t.timeOrigemId
        LEFT JOIN times dest ON dest.id = t.timeDestinoId
        -- Cronológica exata, da venda mais recente para a mais antiga.
        -- ordemGlobal é o relógio da temporada e é monotônico dentro dela; o id
        -- desempata os registros de um mesmo instante (e ordena os antigos, sem
        -- ordemGlobal, pela ordem em que foram gravados).
        ORDER BY t.temporadaId DESC, t.ordemGlobal DESC, t.id DESC
    """)
    fun observeTodasTransferencias(): Flow<List<TransferenciaDetalhe>>

    @Query("""
        SELECT t.id, j.nomeAbreviado AS jogadorNome, j.posicao AS posicao,
               orig.nome AS origemNome, orig.escudoRes AS origemEscudo,
               dest.nome AS destinoNome, dest.escudoRes AS destinoEscudo,
               t.valor, t.tipo, t.temporadaId, t.mes, t.ano, t.ordemGlobal,
               (SELECT c.nome FROM campeonatos c
                 WHERE c.temporadaId = t.temporadaId
                 ORDER BY c.id LIMIT 1) AS nomeTemporada,
               (SELECT c.calendarioVersao FROM campeonatos c
                 WHERE c.temporadaId = t.temporadaId
                 ORDER BY c.id LIMIT 1) AS calendarioVersaoTemporada
        FROM transferencias t
        INNER JOIN jogadores j ON j.id = t.jogadorId
        LEFT JOIN times orig ON orig.id = t.timeOrigemId
        LEFT JOIN times dest ON dest.id = t.timeDestinoId
        WHERE t.timeOrigemId = :timeId AND t.tipo = 'VENDA'
        -- Cronológica exata, da venda mais recente para a mais antiga.
        -- ordemGlobal é o relógio da temporada e é monotônico dentro dela; o id
        -- desempata os registros de um mesmo instante (e ordena os antigos, sem
        -- ordemGlobal, pela ordem em que foram gravados).
        ORDER BY t.temporadaId DESC, t.ordemGlobal DESC, t.id DESC
    """)
    fun observeVendasDoTime(timeId: Int): Flow<List<TransferenciaDetalhe>>

    @Query("SELECT * FROM financas WHERE timeId = :timeId ORDER BY temporadaId DESC, mes DESC")
    fun observeFinancasMensais(timeId: Int): Flow<List<FinancaEntity>>

    @Query("""
        SELECT t.id, j.nomeAbreviado AS jogadorNome, j.posicao AS posicao,
               orig.nome AS origemNome, orig.escudoRes AS origemEscudo,
               dest.nome AS destinoNome, dest.escudoRes AS destinoEscudo,
               t.valor, t.tipo, t.temporadaId, t.mes, t.ano, t.ordemGlobal,
               (SELECT c.nome FROM campeonatos c
                 WHERE c.temporadaId = t.temporadaId
                 ORDER BY c.id LIMIT 1) AS nomeTemporada,
               (SELECT c.calendarioVersao FROM campeonatos c
                 WHERE c.temporadaId = t.temporadaId
                 ORDER BY c.id LIMIT 1) AS calendarioVersaoTemporada
        FROM transferencias t
        INNER JOIN jogadores j ON j.id = t.jogadorId
        LEFT JOIN times orig ON orig.id = t.timeOrigemId
        LEFT JOIN times dest ON dest.id = t.timeDestinoId
        WHERE t.timeDestinoId = :timeId AND t.tipo = 'COMPRA'
        -- Cronológica exata, da venda mais recente para a mais antiga.
        -- ordemGlobal é o relógio da temporada e é monotônico dentro dela; o id
        -- desempata os registros de um mesmo instante (e ordena os antigos, sem
        -- ordemGlobal, pela ordem em que foram gravados).
        ORDER BY t.temporadaId DESC, t.ordemGlobal DESC, t.id DESC
    """)
    fun observeComprasDoTime(timeId: Int): Flow<List<TransferenciaDetalhe>>

    @Query("""
        SELECT SUM(receitaBilheteria + receitaPatrocinio + receitaTransferencias + receitaPremiacoes
                   - despesaSalarios - despesaTransferencias - despesaInfraestrutura)
        FROM financas WHERE timeId = :timeId AND temporadaId = :temporadaId
    """)
    suspend fun calcularSaldoTemporada(timeId: Int, temporadaId: Int): Long?
}
