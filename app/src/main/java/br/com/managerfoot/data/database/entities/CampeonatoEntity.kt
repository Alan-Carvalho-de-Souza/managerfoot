package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "temporadas")
data class TemporadaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ano: Int,
    val encerrada: Boolean = false
)

@Entity(tableName = "campeonatos")
data class CampeonatoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val temporadaId: Int,
    val nome: String,                   // "Brasileirão Série A 2025"
    val tipo: TipoCampeonato,
    val formato: FormatoCampeonato,
    val rodadaAtual: Int = 0,
    val totalRodadas: Int,
    val encerrado: Boolean = false,
    val pais: String = "Brasil"
)

enum class TipoCampeonato {
    NACIONAL_DIVISAO1,
    NACIONAL_DIVISAO2,
    NACIONAL_DIVISAO3,
    NACIONAL_DIVISAO4,
    ESTADUAL,
    COPA_NACIONAL,          // Copa do Brasil
    CONTINENTAL,            // Libertadores / Copa Sul-Americana
    MUNDIAL_CLUBES,
    SELECOES
}

enum class FormatoCampeonato {
    PONTOS_CORRIDOS,        // todos contra todos
    GRUPOS_E_MATA_MATA,     // fase de grupos + eliminatórias
    MATA_MATA_SIMPLES,      // só eliminatórias, jogo único
    MATA_MATA_IDA_VOLTA     // eliminatórias, ida e volta
}

// Vínculo many-to-many entre Campeonato e Times participantes
@Entity(
    tableName = "campeonato_times",
    primaryKeys = ["campeonatoId", "timeId"],
    foreignKeys = [
        ForeignKey(CampeonatoEntity::class, ["id"], ["campeonatoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TimeEntity::class,        ["id"], ["timeId"],       onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("timeId")]
)
data class CampeonatoTimeEntity(
    val campeonatoId: Int,
    val timeId: Int,
    val grupo: String? = null           // "A", "B" etc. (só para GRUPOS_E_MATA_MATA)
)
