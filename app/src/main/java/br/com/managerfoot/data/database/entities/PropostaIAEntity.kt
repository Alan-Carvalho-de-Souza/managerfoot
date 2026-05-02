package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class StatusProposta {
    /** Aguardando ação do usuário */
    PENDENTE,
    /** Usuário aceitou — transferência concluída */
    ACEITA,
    /** Recusada (por usuário ou pela IA após negociação) */
    RECUSADA,
    /** Usuário pediu valor maior — IA responde após a próxima rodada */
    AGUARDANDO_RESPOSTA_IA
}

enum class TipoProposta {
    /** Oferta de compra definitiva */
    VENDA,
    /** Oferta de empréstimo */
    EMPRESTIMO
}

@Entity(tableName = "propostas_ia")
data class PropostaIAEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** Jogador alvo da proposta (pertence ao time do jogador) */
    val jogadorId: Int,
    /** Time da IA que está fazendo a oferta */
    val timeCompradorId: Int,
    /** Valor ofertado pela IA */
    val valorOfertado: Long,
    val status: StatusProposta = StatusProposta.PENDENTE,
    /** Número de tentativas de contra-oferta realizadas (máx. 3) */
    val tentativasNegociacao: Int = 0,
    /** Valor solicitado pelo usuário na última contra-oferta */
    val valorSolicitadoJogador: Long = 0L,
    val temporadaId: Int,
    val mes: Int,
    /** Tipo da proposta (compra definitiva ou empréstimo) */
    val tipoProposta: TipoProposta = TipoProposta.VENDA,
    /** Indica se o usuário já visualizou esta notificação */
    val lida: Boolean = false
)
