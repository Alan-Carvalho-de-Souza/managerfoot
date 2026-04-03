package br.com.managerfoot.domain.model

import br.com.managerfoot.data.database.entities.*

// ─────────────────────────────────────────────
//  Modelos de domínio (desacoplados do Room)
//  A game engine opera sobre esses objetos.
// ─────────────────────────────────────────────

data class Time(
    val id: Int,
    val nome: String,
    val cidade: String,
    val estado: String,
    val nivel: Int,
    val divisao: Int,
    val saldo: Long,
    val estadioCapacidade: Int,
    val precoIngresso: Long,
    val taticaFormacao: String,
    val estiloJogo: EstiloJogo,
    val reputacao: Int,
    val controladoPorJogador: Boolean,
    val escudoRes: String = ""
)

data class Jogador(
    val id: Int,
    val timeId: Int?,
    val nome: String,
    val nomeAbreviado: String,
    val idade: Int,
    val posicao: Posicao,
    val posicaoSecundaria: Posicao?,
    val forca: Int,
    val tecnica: Int,
    val passe: Int,
    val velocidade: Int,
    val finalizacao: Int,
    val defesa: Int,
    val fisico: Int,
    val salario: Long,
    val contratoAnos: Int,
    val valorMercado: Long,
    val lesionado: Boolean,
    val suspenso: Boolean,
    val moraleEstado: MoraleEstado,
    val escalarStatus: Int = 0,          // 0=não escalado, 1=titular, 2=reserva
    val posicaoEscalado: Posicao? = null, // posição salva na escalação manual
    val notaMedia: Float = 6.0f,         // média das notas na temporada (1.0–10.0)
    val partidasTemporada: Int = 0,      // partidas jogadas na temporada atual
    val aposentado: Boolean = false      // true = encerrou carreira
) {
    // Força efetiva: penaliza improvisos e considera morale
    fun forcaEfetiva(posicaoUsada: Posicao = posicao): Int {
        val penaltyImproviso = when {
            posicao == posicaoUsada -> 0
            posicaoSecundaria == posicaoUsada -> 5
            posicao.setor == posicaoUsada.setor -> 10
            else -> 20
        }
        val bonusMorale = when (moraleEstado) {
            MoraleEstado.EXCELENTE    ->  5
            MoraleEstado.BOM          ->  2
            MoraleEstado.NORMAL       ->  0
            MoraleEstado.INSATISFEITO -> -5
            MoraleEstado.REVOLTADO    -> -10
        }
        return (forca - penaltyImproviso + bonusMorale).coerceIn(1, 99)
    }
}

data class Escalacao(
    val time: Time,
    val titulares: List<JogadorNaEscalacao>,   // exatamente 11
    val reservas: List<JogadorNaEscalacao>      // até 11
) {
    val formacaoEfetiva: String get() = calcularFormacao(titulares)

    private fun calcularFormacao(jogadores: List<JogadorNaEscalacao>): String {
        val sem = jogadores.drop(1) // remove goleiro
        val def = sem.count { it.posicaoUsada.setor == Setor.DEFESA }
        val meio = sem.count { it.posicaoUsada.setor == Setor.MEIO }
        val atq = sem.count { it.posicaoUsada.setor == Setor.ATAQUE }
        return "$def-$meio-$atq"
    }
}

data class JogadorNaEscalacao(
    val jogador: Jogador,
    val posicaoUsada: Posicao
)

data class EventoPenalti(
    val jogadorId: Int,
    val nomeAbrev: String,
    val convertido: Boolean
)

data class ResultadoPenaltis(
    val cobrancasCasa: List<EventoPenalti>,
    val cobrancasFora: List<EventoPenalti>,
    val golsCasa: Int,
    val golsFora: Int,
    val vencedorId: Int,
    val timeCasaId: Int,
    val timeForaId: Int
)

/** Dados do adversário para a disputa interativa de pênaltis no lado da UI. */
data class DadosPenaltiAdversario(
    val gkDefesa: Int,
    val cobradores: List<Pair<Int, String>>,   // (jogadorId, nomeAbrev) ordenados por finalizacao desc
    val finalizacoes: List<Int>
)

data class ResultadoPartida(
    val partidaId: Int,
    val timeCasaId: Int,
    val timeForaId: Int,
    val golsCasa: Int,
    val golsFora: Int,
    val eventos: List<EventoSimulado>,
    val estatisticasCasa: EstatisticasTime,
    val estatisticasFora: EstatisticasTime,
    val precisaPenaltis: Boolean = false,
    val golsAgregadoCasa: Int = 0,
    val golsAgregadoFora: Int = 0,
    val notasJogadores: Map<Int, Float> = emptyMap(),  // jogadorId → nota da partida (1–10)
    val torcedores: Int = 0,          // público presente na partida
    val receitaPartida: Long = 0L     // receita de bilheteria (centavos)
)

data class EventoSimulado(
    val minuto: Int,
    val tipo: TipoEvento,
    val jogadorId: Int,
    val timeId: Int,
    val descricao: String
)

data class EstatisticasTime(
    val chutes: Int,
    val chutesNoGol: Int,
    val posse: Int,         // percentual 0-100
    val faltas: Int,
    val cartaoAmarelo: Int,
    val cartaoVermelho: Int
)

/**
 * Estado do jogo ao final de um período (1º ou 2º tempo).
 * Captura o squad atualizado e as penalizações acumuladas para que o
 * período seguinte seja simulado a partir do estado real do momento.
 */
data class EstadoMetade(
    val titsCasa: List<JogadorNaEscalacao>,
    val titsFora: List<JogadorNaEscalacao>,
    val resCasa: List<JogadorNaEscalacao>,
    val resFora: List<JogadorNaEscalacao>,
    val penalCasa: Double = 1.0,        // multiplicador de penalização acumulada (expulsões/lesões)
    val penalFora: Double = 1.0,
    val entryMinutes: Map<Int, Int> = emptyMap(),   // jogadorId → minuto de entrada em campo
    val exitMinutes: Map<Int, Int> = emptyMap()     // jogadorId → minuto de saída do campo
)

/** Contexto que o ViewModel mantém entre o fim do 1º tempo e o início do 2º. */
data class ContextoSimulacaoMetade(
    val campeonatoId: Int,
    val rodada: Int,
    val timeJogadorId: Int,
    val ehMandante: Boolean,
    val partidaDoJogadorId: Int,
    val escalacaoJogadorOriginal: Escalacao,    // escalação inicial do jogador (para Time info)
    val escalacaoAdversario: Escalacao,          // escalação inicial do adversário
    val estadoMetade1: EstadoMetade,             // squad + penas após 1º tempo
    val golsCasaMetade1: Int,
    val golsForaMetade1: Int,
    val eventosAcumulados: List<EventoSimulado>  // eventos canônicos acumulados para persistência
)

/** Substituição passada da UI para o motor — sem dependência de Compose. */
data class InfoSubstituicao(
    val saiId: Int,
    val entrouId: Int,
    val minuto: Int,
    val timeId: Int
)

data class OfertaTransferencia(
    val jogadorId: Int,
    val timeCompradorId: Int,
    val timeVendedorId: Int?,
    val valor: Long,
    val salarioProposto: Long,
    val contratoAnos: Int
)

data class SaldoFinanceiro(
    val timeId: Int,
    val saldoAtual: Long,
    val folhaMensal: Long,           // soma dos salários
    val receitaMensalEstimada: Long  // bilheteria + patrocínio médios
) {
    val saldoProjetadoProximoMes: Long get() = saldoAtual + receitaMensalEstimada - folhaMensal
}
