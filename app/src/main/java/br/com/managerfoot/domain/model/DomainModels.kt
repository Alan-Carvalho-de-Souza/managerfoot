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
    val posicaoEscalado: Posicao? = null // posição salva na escalação manual
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
    val golsAgregadoFora: Int = 0
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
