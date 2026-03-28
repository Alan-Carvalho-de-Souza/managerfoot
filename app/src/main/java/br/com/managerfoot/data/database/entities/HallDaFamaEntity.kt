package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// Sem FK constraints — o registro deve ser permanente,
// independentemente de alterações futuras em times ou jogadores.
@Entity(tableName = "hall_da_fama")
data class HallDaFamaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val ano: Int,
    val nomeCampeonato: String,

    // Campeão e vice-campeão
    val campeaoTimeId: Int,
    val campeaoNome: String,
    val viceTimeId: Int,
    val viceNome: String,

    // Artilheiro da temporada
    val artilheiroId: Int = -1,
    val artilheiroNome: String = "",
    val artilheiroNomeAbrev: String = "",
    val artilheiroGols: Int = 0,
    val artilheiroNomeTime: String = "",

    // Garçom (mais assistências) da temporada
    val assistenteId: Int = -1,
    val assistenteNome: String = "",
    val assistenteNomeAbrev: String = "",
    val assistenciasTotais: Int = 0,
    val assistenteNomeTime: String = ""
)
