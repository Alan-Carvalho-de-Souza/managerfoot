package br.com.managerfoot.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class StatusPropostaClube {
    /** Aguardando decisão do treinador */
    PENDENTE,
    /** Treinador aceitou a proposta e trocou de clube */
    ACEITA,
    /** Treinador recusou a proposta */
    RECUSADA
}

/**
 * Proposta de trabalho recebida de outro clube ao final de uma temporada.
 * Gerada automaticamente com base nos resultados do treinador na temporada.
 */
@Entity(tableName = "propostas_clube")
data class PropostaClubeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** Clube que está oferecendo o cargo de treinador */
    val timeOfertanteId: Int,
    /** Temporada em que a proposta foi gerada */
    val temporadaId: Int,
    val status: StatusPropostaClube = StatusPropostaClube.PENDENTE,
    /** Indica se o usuário já visualizou esta notificação */
    val lida: Boolean = false
)
