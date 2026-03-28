package br.com.managerfoot.data.repository

import br.com.managerfoot.data.dao.JogadorDao
import br.com.managerfoot.data.dao.TimeDao
import br.com.managerfoot.data.database.entities.*
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.domain.model.OfertaTransferencia
import br.com.managerfoot.domain.model.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────
//  TimeRepository
// ─────────────────────────────────────────────
@Singleton
class TimeRepository @Inject constructor(
    private val timeDao: TimeDao
) {
    fun observeTodos(): Flow<List<Time>> =
        timeDao.observeTodos().map { lista -> lista.map { it.toDomain() } }

    fun observeTimeDoJogador(): Flow<Time?> =
        timeDao.observePorId(-1).map { it?.toDomain() } // id resolvido abaixo

    suspend fun buscarTimeDoJogador(): Time? =
        timeDao.buscarTimeDoJogador()?.toDomain()

    suspend fun buscarPorId(id: Int): Time? =
        timeDao.buscarPorId(id)?.toDomain()

    suspend fun buscarEntityPorId(id: Int): TimeEntity? =
        timeDao.buscarPorId(id)

    suspend fun buscarTodosOrdenadosPorReputacao(): List<Time> =
        timeDao.observeTodos().first().sortedByDescending { it.reputacao }.map { it.toDomain() }

    suspend fun inserir(time: TimeEntity): Long = timeDao.inserir(time)

    suspend fun atualizar(time: TimeEntity) = timeDao.atualizar(time)

    suspend fun creditarSaldo(timeId: Int, valor: Long) =
        timeDao.creditarSaldo(timeId, valor)

    suspend fun debitarSaldo(timeId: Int, valor: Long) =
        timeDao.debitarSaldo(timeId, valor)

    suspend fun ampliarEstadio(timeId: Int, novaCapacidade: Int) =
        timeDao.ampliarEstadio(timeId, novaCapacidade)

    suspend fun atualizarReputacao(timeId: Int, rep: Int) =
        timeDao.atualizarReputacao(timeId, rep.coerceIn(0, 100))

    // Mapeamento Entity -> Domain
    private fun TimeEntity.toDomain() = Time(
        id = id,
        nome = nome,
        cidade = cidade,
        estado = estado,
        nivel = nivel,
        divisao = divisao,
        saldo = saldo,
        estadioCapacidade = estadioCapacidade,
        precoIngresso = precoIngresso,
        taticaFormacao = taticaFormacao,
        estiloJogo = estiloJogo,
        reputacao = reputacao,
        controladoPorJogador = controladoPorJogador,
        escudoRes = escudoRes
    )
}

// ─────────────────────────────────────────────
//  JogadorRepository
// ─────────────────────────────────────────────
@Singleton
class JogadorRepository @Inject constructor(
    private val jogadorDao: JogadorDao,
    private val financaDao: br.com.managerfoot.data.dao.FinancaDao
) {
    fun observeElenco(timeId: Int): Flow<List<Jogador>> =
        jogadorDao.observeElenco(timeId).map { lista -> lista.map { it.toDomain() } }

    fun observeLivres(): Flow<List<Jogador>> =
        jogadorDao.observeLivres().map { lista -> lista.map { it.toDomain() } }

    suspend fun buscarDisponiveis(timeId: Int): List<Jogador> =
        jogadorDao.buscarDisponiveisPorTime(timeId).map { it.toDomain() }

    suspend fun buscarMercado(
        posicao: Posicao? = null,
        forcaMin: Int = 1,
        forcaMax: Int = 99,
        limite: Int = 20
    ): List<Jogador> =
        jogadorDao.buscarLivresParaTransferencia(
            posicao?.name, forcaMin, forcaMax, limite
        ).map { it.toDomain() }

    suspend fun realizarTransferencia(
        oferta: OfertaTransferencia,
        temporadaId: Int,
        mes: Int
    ) {
        // Mudar dono do jogador
        jogadorDao.transferirJogador(oferta.jogadorId, oferta.timeCompradorId)

        // Registrar transação financeira
        financaDao.inserirTransferencia(
            TransferenciaEntity(
                jogadorId = oferta.jogadorId,
                timeOrigemId = oferta.timeVendedorId,
                timeDestinoId = oferta.timeCompradorId,
                valor = oferta.valor,
                temporadaId = temporadaId,
                mes = mes,
                tipo = if (oferta.timeVendedorId == null) TipoTransferencia.FIM_CONTRATO
                       else TipoTransferencia.COMPRA
            )
        )
    }

    suspend fun processarDesenvolvimentoAnual() =
        jogadorDao.processarDesenvolvimentoAnual()

    suspend fun atualizarEscalacao(jogadorId: Int, status: Int, posicao: Posicao? = null) {
        if (posicao != null) jogadorDao.atualizarEscalacaoComPosicao(jogadorId, status, posicao)
        else jogadorDao.atualizarEscalacaoSemPosicao(jogadorId, status)
    }

    suspend fun buscarTitularesSalvos(timeId: Int): List<Jogador> =
        jogadorDao.buscarTitularesSalvos(timeId).map { it.toDomain() }

    suspend fun buscarReservasSalvas(timeId: Int): List<Jogador> =
        jogadorDao.buscarReservasSalvas(timeId).map { it.toDomain() }

    suspend fun limparEscalacaoTime(timeId: Int) = jogadorDao.limparEscalacaoTime(timeId)

    suspend fun processarExpiracaoContratos(timeId: Int) {
        jogadorDao.decrementarContratos()
        val expirados = jogadorDao.buscarComContratoExpirado()
            .filter { it.timeId == timeId }
        expirados.forEach {
            jogadorDao.transferirJogador(it.id, null) // libera o jogador
        }
    }

    // Mapeamento Entity -> Domain
    private fun JogadorEntity.toDomain() = Jogador(
        id = id,
        timeId = timeId,
        nome = nome,
        nomeAbreviado = nomeAbreviado,
        idade = idade,
        posicao = posicao,
        posicaoSecundaria = posicaoSecundaria,
        forca = forca,
        tecnica = tecnica,
        passe = passe,
        velocidade = velocidade,
        finalizacao = finalizacao,
        defesa = defesa,
        fisico = fisico,
        salario = salario,
        contratoAnos = contratoAnos,
        valorMercado = valorMercado,
        lesionado = lesionado,
        suspenso = suspensoCicloAmarelos,
        moraleEstado = moraleEstado,
        escalarStatus = escalarStatus,
        posicaoEscalado = posicaoEscalado
    )
}
