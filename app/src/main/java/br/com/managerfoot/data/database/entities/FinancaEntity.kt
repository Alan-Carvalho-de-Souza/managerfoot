package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Tabela de classificação por campeonato
@Entity(
    tableName = "classificacoes",
    primaryKeys = ["campeonatoId", "timeId"],
    foreignKeys = [
        ForeignKey(CampeonatoEntity::class, ["id"], ["campeonatoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TimeEntity::class,        ["id"], ["timeId"],       onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("timeId")]
)
data class ClassificacaoEntity(
    val campeonatoId: Int,
    val timeId: Int,
    val grupo: String? = null,
    val pontos: Int = 0,
    val jogos: Int = 0,
    val vitorias: Int = 0,
    val empates: Int = 0,
    val derrotas: Int = 0,
    val golsPro: Int = 0,
    val golsContra: Int = 0,
    val saldoGols: Int = 0,
    val aproveitamento: Float = 0f      // 0.0 - 1.0
)

// Registro financeiro mensal do clube
@Entity(
    tableName = "financas",
    foreignKeys = [
        ForeignKey(TimeEntity::class, ["id"], ["timeId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("timeId")]
)
data class FinancaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timeId: Int,
    val temporadaId: Int,               // referência à temporada (sem FK para evitar falhas de integridade)
    val mes: Int,                       // 1-12
    val receitaBilheteria: Long = 0,
    val receitaPatrocinio: Long = 0,
    val receitaTransferencias: Long = 0,
    val receitaPremiacoes: Long = 0,
    val descricaoPremio: String? = null,   // ex: "Série A — Campeão" ou "Copa do Brasil — Vice"
    val despesaSalarios: Long = 0,
    val despesaTransferencias: Long = 0,
    val despesaInfraestrutura: Long = 0,
    val despesaAmpliacaoEstadio: Long = 0, // custo de expansão do estádio
    val despesaBase: Long = 0,             // custo de formação de jogadores na base de juniores
    val saldoFinal: Long = 0            // calculado no fechamento do mês
)

// Histórico de transferências
@Entity(
    tableName = "transferencias",
    foreignKeys = [
        ForeignKey(JogadorEntity::class, ["id"], ["jogadorId"],  onDelete = ForeignKey.CASCADE),
        ForeignKey(TimeEntity::class,    ["id"], ["timeOrigemId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(TimeEntity::class,    ["id"], ["timeDestinoId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("jogadorId"), Index("timeOrigemId"), Index("timeDestinoId")]
)
data class TransferenciaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val jogadorId: Int,
    val timeOrigemId: Int?,
    val timeDestinoId: Int?,
    val valor: Long,                    // em centavos (0 = livre)
    val temporadaId: Int,
    val mes: Int,
    val tipo: TipoTransferencia,

    /**
     * Ano-calendário da transferência. `temporadaId` é um índice que começa em 1
     * em 2026, então o ano daria para derivar — mas derivar amarra a exibição a
     * essa origem, e um save que comece noutro ano passaria a mentir. 0 = antigo.
     */
    val ano: Int = 0,

    /**
     * O relógio do jogo no momento da transferência: o maior `ordemGlobal` já
     * jogado na temporada. É dele que sai o DIA exato da negociação, pela mesma
     * conversão que data as partidas (CalendarioDoJogo), e é por ele que o
     * histórico é ordenado — `ordemGlobal` é monotônico dentro da temporada, o
     * que torna a ordenação exatamente cronológica.
     *
     * 0 quando a temporada ainda não teve jogo nenhum (janela de janeiro) ou em
     * registros anteriores a esta coluna.
     */
    val ordemGlobal: Int = 0
)

enum class TipoTransferencia {
    VENDA,
    COMPRA,
    EMPRESTIMO_SAIDA,
    EMPRESTIMO_RETORNO,
    FIM_CONTRATO,
    PROMOVIDO_BASE,
    DISPENSADO_BASE
}
