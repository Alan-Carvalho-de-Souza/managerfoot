package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "jogadores",
    foreignKeys = [
        ForeignKey(
            entity = TimeEntity::class,
            parentColumns = ["id"],
            childColumns = ["timeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("timeId")]
)
data class JogadorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timeId: Int?,                   // null = jogador livre (sem clube)
    val nome: String,
    val nomeAbreviado: String,          // ex: "Rodrigo S." para exibição na lista
    val nacionalidade: String = "Brasileiro",
    val idade: Int,
    val posicao: Posicao,
    val posicaoSecundaria: Posicao? = null, // permite improvisar

    // Atributos técnicos (1-99)
    val forca: Int,                     // atributo geral (média ponderada)
    val tecnica: Int,
    val passe: Int,
    val velocidade: Int,
    val finalizacao: Int,
    val defesa: Int,
    val fisico: Int,

    // Contrato
    val salario: Long,                  // por mês, em centavos
    val contratoAnos: Int,              // anos restantes de contrato
    val valorMercado: Long,             // estimativa de mercado, em centavos

    // Estado
    val lesionado: Boolean = false,
    val suspensoCicloAmarelos: Boolean = false,
    val moraleEstado: MoraleEstado = MoraleEstado.NORMAL,
    val categoriaBase: Boolean = false  // true = sub-21
)

enum class Posicao(val abreviacao: String, val setor: Setor) {
    GOLEIRO("GL", Setor.GOLEIRO),
    ZAGUEIRO("ZG", Setor.DEFESA),
    LATERAL_DIREITO("LD", Setor.DEFESA),
    LATERAL_ESQUERDO("LE", Setor.DEFESA),
    VOLANTE("VOL", Setor.MEIO),
    MEIA_CENTRAL("MC", Setor.MEIO),
    MEIA_ATACANTE("MA", Setor.MEIO),
    PONTA_DIREITA("PD", Setor.ATAQUE),
    PONTA_ESQUERDA("PE", Setor.ATAQUE),
    CENTROAVANTE("CA", Setor.ATAQUE),
    SEGUNDA_ATACANTE("SA", Setor.ATAQUE)
}

enum class Setor { GOLEIRO, DEFESA, MEIO, ATAQUE }

enum class MoraleEstado {
    EXCELENTE,
    BOM,
    NORMAL,
    INSATISFEITO,
    REVOLTADO
}
