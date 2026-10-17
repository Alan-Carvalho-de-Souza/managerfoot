package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Lançamentos de pontos do Ranking CONMEBOL (ver ranking_conmebol.md).
// Diferente do ranking_geral, aqui cada conquista/resultado é uma linha com o ano em
// que aconteceu, porque a pontuação por desempenho sofre depreciação de 10% ao ano.
// Sem foreign key para TimeEntity: os lançamentos são históricos e sobrevivem ao clube.
@Entity(
    tableName = "ranking_conmebol",
    indices = [Index("timeId"), Index("ano")]
)
data class RankingConmebolEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timeId: Int,
    // Ano do lançamento. Para o coeficiente histórico (1960-2014) usamos ANO_HISTORICO.
    val ano: Int,
    // Nome do enum CompeticaoConmebol.
    val competicao: String,
    // Descrição do que gerou os pontos ("Campeão", "Vitória", "Coeficiente histórico"...).
    val motivo: String = "",
    val pontos: Double = 0.0,
    // false para o coeficiente histórico, que é fixo e não perde valor com o tempo.
    val deprecia: Boolean = true
)
