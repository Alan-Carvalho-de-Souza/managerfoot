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
        ForeignKey(TimeEntity::class,    ["id"], ["timeId"],    onDelete = ForeignKey.CASCADE),
        ForeignKey(TemporadaEntity::class, ["id"], ["temporadaId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("timeId"), Index("temporadaId")]
)
data class FinancaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timeId: Int,
    val temporadaId: Int,
    val mes: Int,                       // 1-12
    val receitaBilheteria: Long = 0,
    val receitaPatrocinio: Long = 0,
    val receitaTransferencias: Long = 0,
    val receitaPremiacoes: Long = 0,
    val despesaSalarios: Long = 0,
    val despesaTransferencias: Long = 0,
    val despesaInfraestrutura: Long = 0,
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
    val tipo: TipoTransferencia
)

enum class TipoTransferencia {
    VENDA,
    COMPRA,
    EMPRESTIMO_SAIDA,
    EMPRESTIMO_RETORNO,
    FIM_CONTRATO,
    PROMOVIDO_BASE
}
