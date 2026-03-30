package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// ─────────────────────────────────────────────────
//  EstadioEntity
//  Armazena os níveis de cada setor do estádio.
//  Cada setor possui 10 níveis de desenvolvimento
//  (0 = base, 10 = máximo).
//
//  Capacidade por nível:
//  Nível | Arquibancada | Cadeira | Camarote
//    0   |       500    |    200  |       0
//    1   |     2.000    |    800  |     200
//    2   |     4.000    |  2.000  |     500
//    3   |     6.500    |  4.000  |   1.000
//    4   |     9.000    |  6.000  |   2.000
//    5   |    12.000    |  8.500  |   3.500
//    6   |    16.000    | 11.000  |   5.500
//    7   |    20.000    | 14.000  |   8.000
//    8   |    25.000    | 17.000  |  12.000
//    9   |    30.000    | 21.000  |  18.000
//   10   |    40.000    | 25.000  |  35.000
//
//  Capacidade total máxima: 40k + 25k + 35k = 100.000
// ─────────────────────────────────────────────────
@Entity(
    tableName = "estadio",
    foreignKeys = [
        ForeignKey(
            entity = TimeEntity::class,
            parentColumns = ["id"],
            childColumns = ["timeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EstadioEntity(
    @PrimaryKey
    val timeId: Int,
    val nivelArquibancada: Int = 0,   // 0–10
    val nivelCadeira: Int = 0,        // 0–10
    val nivelCamarote: Int = 0,       // 0–10
) {
    companion object {

        // Capacidade de cada setor conforme nível (índices 0–10)
        val CAPACIDADE_ARQUIBANCADA = intArrayOf(
              500,  2_000,  4_000,  6_500,  9_000,
           12_000, 16_000, 20_000, 25_000, 30_000, 40_000
        )
        val CAPACIDADE_CADEIRA = intArrayOf(
              200,    800,  2_000,  4_000,  6_000,
            8_500, 11_000, 14_000, 17_000, 21_000, 25_000
        )
        val CAPACIDADE_CAMAROTE = intArrayOf(
                0,    200,    500,  1_000,  2_000,
            3_500,  5_500,  8_000, 12_000, 18_000, 35_000
        )

        // Custo de upgrade (9 entradas: nível N → N+1, para N = 0..9), em centavos
        // Arquibancada — dificuldade baixa, custo barato
        val CUSTO_ARQUIBANCADA = longArrayOf(
             5_000_000L,  // 0→1  R$50k
            10_000_000L,  // 1→2  R$100k
            20_000_000L,  // 2→3  R$200k
            35_000_000L,  // 3→4  R$350k
            50_000_000L,  // 4→5  R$500k
            75_000_000L,  // 5→6  R$750k
           120_000_000L,  // 6→7  R$1.2M
           200_000_000L,  // 7→8  R$2M
           350_000_000L,  // 8→9  R$3.5M
           600_000_000L   // 9→10 R$6M
        )
        // Cadeiras — dificuldade e custo médios
        val CUSTO_CADEIRA = longArrayOf(
            20_000_000L,  // 0→1  R$200k
            40_000_000L,  // 1→2  R$400k
            70_000_000L,  // 2→3  R$700k
           120_000_000L,  // 3→4  R$1.2M
           200_000_000L,  // 4→5  R$2M
           300_000_000L,  // 5→6  R$3M
           500_000_000L,  // 6→7  R$5M
           800_000_000L,  // 7→8  R$8M
         1_500_000_000L,  // 8→9  R$15M
         2_500_000_000L   // 9→10 R$25M
        )
        // Camarote — dificuldade alta, custo caro
        val CUSTO_CAMAROTE = longArrayOf(
            50_000_000L,  // 0→1  R$500k
           150_000_000L,  // 1→2  R$1.5M
           350_000_000L,  // 2→3  R$3.5M
           700_000_000L,  // 3→4  R$7M
         1_500_000_000L,  // 4→5  R$15M
         3_000_000_000L,  // 5→6  R$30M
         5_000_000_000L,  // 6→7  R$50M
         8_000_000_000L,  // 7→8  R$80M
        12_000_000_000L,  // 8→9  R$120M
        20_000_000_000L   // 9→10 R$200M
        )

        /**
         * Cria um EstadioEntity distribuindo a capacidade existente nos 3 setores.
         * Proporção: ~55% arquibancada, ~30% cadeiras, ~15% camarote.
         * Cada setor recebe o nível cujo valor não excede a fatia alocada.
         */
        fun inicializar(timeId: Int, capacidadeExistente: Int): EstadioEntity {
            val targetArch = (capacidadeExistente * 0.55).toInt()
            val targetCad  = (capacidadeExistente * 0.30).toInt()
            val targetCam  = (capacidadeExistente * 0.15).toInt()

            val nivelArch = CAPACIDADE_ARQUIBANCADA.indexOfLast { it <= targetArch }.coerceAtLeast(0)
            val nivelCad  = CAPACIDADE_CADEIRA.indexOfLast { it <= targetCad }.coerceAtLeast(0)
            val nivelCam  = CAPACIDADE_CAMAROTE.indexOfLast { it <= targetCam }.coerceAtLeast(0)

            return EstadioEntity(timeId, nivelArch, nivelCad, nivelCam)
        }
    }

    /** Capacidade total calculada a partir dos níveis de setor. */
    val capacidadeTotal: Int
        get() = CAPACIDADE_ARQUIBANCADA[nivelArquibancada] +
                CAPACIDADE_CADEIRA[nivelCadeira] +
                CAPACIDADE_CAMAROTE[nivelCamarote]
}
