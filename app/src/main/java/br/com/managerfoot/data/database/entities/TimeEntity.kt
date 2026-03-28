package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "times")
data class TimeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val nome: String,
    val cidade: String,
    val estado: String,
    val pais: String = "Brasil",
    val divisao: Int,                   // 1 = Série A, 2 = Série B, etc.
    val nivel: Int,                     // força geral do clube (1-10), afeta AI e contratações
    val saldo: Long,                    // em reais (centavos)
    val estadioNome: String,
    val estadioCapacidade: Int,
    val precoIngresso: Long,            // em centavos
    val taticaFormacao: String,         // "4-4-2", "4-3-3", "3-5-2"
    val estiloJogo: EstiloJogo,
    val escudoRes: String = "",         // nome do drawable resource
    val controladoPorJogador: Boolean = false,
    val reputacao: Int = 50,            // 0-100, afeta contratações e patrocínio
)

enum class EstiloJogo {
    OFENSIVO,
    EQUILIBRADO,
    DEFENSIVO,
    CONTRA_ATAQUE
}
