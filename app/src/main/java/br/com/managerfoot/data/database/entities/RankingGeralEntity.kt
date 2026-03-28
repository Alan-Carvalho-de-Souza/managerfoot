package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Ranking acumulado de todos os campeonatos nacionais (Série A-D) de todas as temporadas.
// Usado para determinar os 44 classificados adicionais da Copa a partir da 2ª temporada.
@Entity(
    tableName = "ranking_geral",
    foreignKeys = [
        ForeignKey(TimeEntity::class, ["id"], ["timeId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("timeId")]
)
data class RankingGeralEntity(
    @PrimaryKey
    val timeId: Int,
    val nomeTime: String = "",
    val escudoRes: String = "",
    val divisaoAtual: Int = 1,
    val pontosAcumulados: Long = 0,
    val temporadasJogadas: Int = 0,
    val copasVencidas: Int = 0,
    val vitorias: Int = 0,
    val empates: Int = 0,
    val derrotas: Int = 0,
    val golsPro: Int = 0,
    val golsContra: Int = 0
)
