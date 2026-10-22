package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Lançamentos de pontos do Coeficiente de Clubes da UEFA (ver ranking_europa.md).
//
// Tabela própria, seguindo a mesma decisão tomada em RankingBrasileiroEntity: os três
// regimes de pontuação só compartilham a ideia de "um lançamento por ano". A janela da
// UEFA é de 5 temporadas SEM pesos, enquanto o RNC usa pesos 5/4/3/2/1 e o CONMEBOL
// deprecia 10% ao ano em 10 anos. Juntar exigiria uma coluna de regime e um CASE em
// cada consulta; separadas, cada regra evolui sem tocar nas outras.
//
// Uma linha por clube/ano/competição. Um clube disputa uma competição da UEFA por
// temporada, então o normal é um lançamento por ano.
// Sem foreign key para TimeEntity: os lançamentos são históricos e sobrevivem ao clube.
@Entity(
    tableName = "ranking_europa",
    indices = [Index("timeId"), Index("ano")]
)
data class RankingEuropaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timeId: Int,
    // Temporada avaliada. Entra inteira na soma enquanto estiver na janela de 5 anos.
    val ano: Int,
    // Nome do enum CompeticaoUefa.
    val competicao: String,
    // Como os pontos foram obtidos ("5º na Fase de Liga, quartas de final"...).
    val motivo: String = "",
    // Pontos da temporada. Fracionários por definição: a escada de bônus da
    // Conference League chega a 0,125.
    val pontos: Double = 0.0
)
