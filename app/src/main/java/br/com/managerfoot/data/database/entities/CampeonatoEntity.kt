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

/**
 * Qual calendário uma temporada segue.
 *
 * O jogo não guarda data: guarda `ordemGlobal`. Até a [CURVA] esse valor era um
 * contador de slots (1..620) e o dia saía de uma curva que o espalhava sobre o
 * ano; na [GRADE] ele passa a ser o próprio dia do ano. São duas escalas
 * diferentes para a mesma coluna, e é por isso que a versão existe.
 *
 * A versão fica no CAMPEONATO, e não numa configuração global, porque toda
 * competição de uma temporada nasce no rollover: uma temporada ou é inteira de
 * uma escala, ou é inteira da outra. Isso dispensa migrar as partidas gravadas
 * (seriam milhares de linhas reescritas no startup) e torna impossível, por
 * construção, uma temporada misturar as duas — inclusive nas fases que só são
 * geradas meses depois, porque elas herdam a versão do campeonato que já existe.
 *
 * O preço é que a curva antiga continua no código como LEITORA do histórico.
 */
object CalendarioVersao {

    /** `ordemGlobal` é um contador de slots, e o dia sai de uma curva. */
    const val CURVA = 1

    /** `ordemGlobal` é o dia do ano. */
    const val GRADE = 2

    /** A versão que as temporadas novas recebem. */
    const val ATUAL = GRADE
}

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
    val pais: String = "Brasil",

    /**
     * Qual escala de `ordemGlobal` as partidas deste campeonato usam. Ver
     * [CalendarioVersao].
     *
     * Um campeonato novo nasce em [CalendarioVersao.ATUAL]; os que já estão
     * gravados ficam em [CalendarioVersao.CURVA], que é o `DEFAULT 1` da
     * migração 36 → 37.
     */
    val calendarioVersao: Int = CalendarioVersao.ATUAL
)

enum class TipoCampeonato {
    NACIONAL_DIVISAO1,
    NACIONAL_DIVISAO2,
    NACIONAL_DIVISAO3,
    NACIONAL_DIVISAO4,
    ESTADUAL,
    COPA_NACIONAL,          // Copa do Brasil
    SUPERCOPA,              // Supercopa Rei (campeão brasileiro x campeão da copa)
    CONTINENTAL,            // Libertadores / Copa Sul-Americana
    MUNDIAL_CLUBES,
    // Copa Intercontinental: jogo único entre o campeão da Champions League e o
    // campeão da Libertadores, último jogo oficial da temporada. Separada de
    // MUNDIAL_CLUBES, que segue reservada ao Mundial de Clubes da FIFA.
    INTERCONTINENTAL,
    SELECOES,
    EXTRANGEIRO_DIVISAO1,   // Primera División Argentina (e futuros)
    EXTRANGEIRO_DIVISAO2,   // Segunda División Argentina (Primera Nacional)
    TROFEU_CAMPEOES,        // Troféu dos Campeões — campeão Apertura ARG x campeão Clausura ARG (mesma temporada)
    AMISTOSO                // Amistosos de entressafra — não contam para tabela/artilheiros/hall
}

enum class FormatoCampeonato {
    PONTOS_CORRIDOS,        // todos contra todos
    GRUPOS_E_MATA_MATA,     // fase de grupos + eliminatórias
    MATA_MATA_SIMPLES,      // só eliminatórias, jogo único
    MATA_MATA_IDA_VOLTA,    // eliminatórias, ida e volta
    /**
     * Fase de Liga única + mata-mata — o formato novo da Champions League.
     *
     * Não é PONTOS_CORRIDOS: os 36 clubes estão numa tabela só, mas cada um
     * enfrenta apenas 8 adversários DIFERENTES (2 de cada pote, um em casa e um
     * fora), de modo que ninguém joga contra todos e o confronto direto não
     * existe entre a maioria dos pares. Também não é GRUPOS_E_MATA_MATA, porque
     * não há grupos: a classificação vai de 1 a 36 numa única tabela.
     *
     * Ver regras_championsleague.md e SorteioChampions.
     */
    FASE_LIGA_E_MATA_MATA
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
