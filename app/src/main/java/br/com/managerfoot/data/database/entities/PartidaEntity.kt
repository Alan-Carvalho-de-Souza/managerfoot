package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "partidas",
    foreignKeys = [
        ForeignKey(CampeonatoEntity::class, ["id"], ["campeonatoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TimeEntity::class,        ["id"], ["timeCasaId"],   onDelete = ForeignKey.CASCADE),
        ForeignKey(TimeEntity::class,        ["id"], ["timeForaId"],   onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("campeonatoId"), Index("timeCasaId"), Index("timeForaId")]
)
data class PartidaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val campeonatoId: Int,
    val rodada: Int,
    val timeCasaId: Int,
    val timeForaId: Int,

    // Resultado (null = partida ainda não jogada)
    val golsCasa: Int? = null,
    val golsFora: Int? = null,

    // Penáltis (copa, em caso de empate no mata-mata)
    val penaltisCasa: Int? = null,
    val penaltisForaId: Int? = null,

    val jogada: Boolean = false,
    val fase: String? = null,           // "Primeira Fase", "Segunda Fase", "Oitavas", "Quartas", "Semi", "Final"
    val confrontoId: Int? = null,       // agrupa jogo de ida + volta (Copa)
    val ordemGlobal: Int = 0            // posição no calendário multi-competição (usado por buscarProximaPartida)
)

// Escalação salva pelo jogador para uma determinada partida
@Entity(
    tableName = "escalacoes",
    foreignKeys = [
        ForeignKey(PartidaEntity::class,  ["id"], ["partidaId"],  onDelete = ForeignKey.CASCADE),
        ForeignKey(TimeEntity::class,      ["id"], ["timeId"],     onDelete = ForeignKey.CASCADE),
        ForeignKey(JogadorEntity::class,   ["id"], ["jogadorId"],  onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("partidaId"), Index("timeId"), Index("jogadorId")]
)
data class EscalacaoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val partidaId: Int,
    val timeId: Int,
    val jogadorId: Int,
    val posicaoImproviso: Posicao? = null,  // se nulo, joga na posição natural
    val titular: Boolean = true
)

// Eventos gerados na simulação da partida (gols, cartões, lesões)
@Entity(
    tableName = "eventos_partida",
    foreignKeys = [
        ForeignKey(PartidaEntity::class, ["id"], ["partidaId"],  onDelete = ForeignKey.CASCADE),
        ForeignKey(JogadorEntity::class, ["id"], ["jogadorId"],  onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("partidaId"), Index("jogadorId")]
)
data class EventoPartidaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val partidaId: Int,
    val jogadorId: Int,
    val minuto: Int,
    val tipo: TipoEvento,
    val descricao: String = ""
)

enum class TipoEvento {
    GOL,
    ASSISTENCIA,
    GOL_CONTRA,
    PENALTI_CONVERTIDO,
    PENALTI_PERDIDO,
    CARTAO_AMARELO,
    CARTAO_VERMELHO,
    LESAO,
    SUBSTITUICAO_ENTRA,
    SUBSTITUICAO_SAI,
    PARTICIPOU   // marca que o jogador entrou em campo (titular); não aparece na UI
}
