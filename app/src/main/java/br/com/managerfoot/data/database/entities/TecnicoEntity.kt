package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Técnico de futebol — pode estar contratado por um time (timeId != null)
 * ou livre no mercado (timeId = null). Stats acumulam ao longo da carreira;
 * pontuação no ranking = vitorias * 5 + empates * 1 + derrotas * 0.
 *
 * O técnico controlado pelo jogador tem `controladoPorJogador = true`.
 * Idade é fixa nesta versão (sem aposentadoria).
 *
 * Sem FK para TimeEntity (timeId é nullable e SET_NULL no padrão dos outros
 * relacionamentos do app).
 */
@Entity(
    tableName = "tecnicos",
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
data class TecnicoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val nome: String,
    val nomeAbreviado: String = "",
    val idade: Int = 50,
    val nacionalidade: String = "Brasil",   // Brasil | Argentina | Uruguai | ...
    val timeId: Int? = null,                // null = livre no mercado
    val salario: Long = 200_000_00L,        // centavos
    val contratoAnos: Int = 0,
    val reputacao: Float = 50f,             // 0..100
    val controladoPorJogador: Boolean = false,

    // ── Stats acumulados de carreira (todos os jogos) ────────
    val vitorias: Int = 0,
    val empates: Int = 0,
    val derrotas: Int = 0,
    val titulos: Int = 0,                   // total de títulos conquistados

    // ── Stats da temporada atual (resetam ao final da temporada) ──
    val vitoriasTemporada: Int = 0,
    val empatesTemporada: Int = 0,
    val derrotasTemporada: Int = 0
) {
    /** Pontos no ranking: V=5, E=1, D=0 */
    val pontos: Int get() = vitorias * 5 + empates

    val pontosTemporada: Int get() = vitoriasTemporada * 5 + empatesTemporada

    val jogos: Int get() = vitorias + empates + derrotas
    val jogosTemporada: Int get() = vitoriasTemporada + empatesTemporada + derrotasTemporada

    val aproveitamento: Float get() = if (jogos > 0)
        (vitorias * 3 + empates).toFloat() / (jogos * 3) * 100f
    else 0f
}

/**
 * Passagem do técnico por um clube — registra o período + stats agregados
 * naquela passagem. Persistida explicitamente (não derivada como o histórico
 * do jogador) porque técnicos têm transferências menos frequentes e a
 * agregação de partidas seria mais cara.
 */
@Entity(
    tableName = "passagens_tecnicos",
    foreignKeys = [
        ForeignKey(
            entity = TecnicoEntity::class,
            parentColumns = ["id"],
            childColumns = ["tecnicoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("tecnicoId"),
        Index("timeId")
    ]
)
data class PassagemTecnicoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val tecnicoId: Int,
    val timeId: Int,
    val timeNome: String = "",      // snapshot — sobrevive a apagar o time
    val timeEscudo: String = "",    // snapshot
    val anoInicio: Int,
    val anoFim: Int,                // = anoInicio se ainda ativa
    val ativa: Boolean = true,      // true = técnico ainda dirige este time

    val vitorias: Int = 0,
    val empates: Int = 0,
    val derrotas: Int = 0,
    val titulos: Int = 0
) {
    val pontos: Int get() = vitorias * 5 + empates
    val jogos: Int get() = vitorias + empates + derrotas
}
