package br.com.managerfoot.data.repository

import br.com.managerfoot.data.dao.CampeonatoDao
import br.com.managerfoot.data.dao.ClassificacaoDao
import br.com.managerfoot.data.dao.HallDaFamaDao
import br.com.managerfoot.data.dao.PartidaDao
import br.com.managerfoot.data.database.entities.*
import br.com.managerfoot.domain.engine.*
import br.com.managerfoot.domain.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val campeonatoDao: CampeonatoDao,
    private val partidaDao: PartidaDao,
    private val classificacaoDao: ClassificacaoDao,
    private val timeRepository: TimeRepository,
    private val jogadorRepository: JogadorRepository,
    private val financaDao: br.com.managerfoot.data.dao.FinancaDao,
    private val hallDaFamaDao: HallDaFamaDao
) {
    private val simulador = SimuladorPartida()

    suspend fun criarCampeonato(
        campeonato: CampeonatoEntity,
        participantesIds: List<Int>
    ): Int {
        val campeonatoId = campeonatoDao.inserir(campeonato).toInt()

        campeonatoDao.inserirParticipantes(
            participantesIds.map { CampeonatoTimeEntity(campeonatoId, it) }
        )

        val partidas = MotorCampeonato.gerarCalendarioPontosCorridos(campeonatoId, participantesIds)
        partidaDao.inserirTodas(partidas)

        val tabela = MotorCampeonato.gerarClassificacaoInicial(campeonatoId, participantesIds)
        classificacaoDao.inserirTodos(tabela)

        return campeonatoId
    }

    fun observarTabela(campeonatoId: Int): Flow<List<ClassificacaoEntity>> =
        classificacaoDao.observeTabelaPorCampeonato(campeonatoId)

    fun observarArtilheiros(campeonatoId: Int) = partidaDao.observeArtilheiros(campeonatoId)
    fun observarAssistentes(campeonatoId: Int) = partidaDao.observeAssistentes(campeonatoId)
    fun observarArtilheirosAllTime() = partidaDao.observeArtilheirosAllTime()
    fun observarAssistentesAllTime() = partidaDao.observeAssistentesAllTime()

    suspend fun buscarPartidasConfronto(timeAId: Int, timeBId: Int) =
        partidaDao.buscarPartidasConfronto(timeAId, timeBId)

    suspend fun buscarArtilheirosConfronto(timeAId: Int, timeBId: Int) =
        partidaDao.buscarArtilheirosConfronto(timeAId, timeBId)

    suspend fun simularRodada(campeonatoId: Int, rodada: Int) {
        val partidas = partidaDao.buscarPorRodada(campeonatoId, rodada)
            .filter { !it.jogada }

        for (partida in partidas) {
            simularPartidaInterna(partida)
        }

        campeonatoDao.avancarRodada(campeonatoId)
    }

    /**
     * Simula a rodada inteira de forma atômica:
     * – A partida do jogador é simulada uma única vez (com escalação opcional).
     * – As demais partidas da rodada são simuladas com IA.
     * – O mesmo ResultadoPartida persistido no banco é retornado para a UI,
     *   eliminando qualquer divergência de placar entre animação e últimos resultados.
     */
    suspend fun simularRodadaComJogador(
        campeonatoId: Int,
        rodada: Int,
        timeJogadorId: Int,
        escalacaoCasa: Escalacao? = null
    ): ResultadoPartida {
        val partidas = partidaDao.buscarPorRodada(campeonatoId, rodada)
            .filter { !it.jogada }

        val partidaDoJogador = partidas.firstOrNull {
            it.timeCasaId == timeJogadorId || it.timeForaId == timeJogadorId
        } ?: throw IllegalStateException("Partida do jogador não encontrada na rodada $rodada")

        // Determina escalações da partida do jogador
        val ehMandante = partidaDoJogador.timeCasaId == timeJogadorId
        val escalacaoJogador = escalacaoCasa ?: gerarEscalacaoIA(timeJogadorId)
        val escalacaoAdversario = gerarEscalacaoIA(
            if (ehMandante) partidaDoJogador.timeForaId else partidaDoJogador.timeCasaId
        )
        val escalacaoFinalCasa = if (ehMandante) escalacaoJogador else escalacaoAdversario
        val escalacaoFinalFora  = if (ehMandante) escalacaoAdversario else escalacaoJogador

        // Simula e persiste a partida do jogador — resultado único
        val resultadoJogador = simulador.simular(
            partidaId = partidaDoJogador.id,
            casa = escalacaoFinalCasa,
            fora = escalacaoFinalFora
        )
        persistirResultado(resultadoJogador)

        // Simula as demais partidas da rodada com IA
        for (partida in partidas) {
            if (partida.id != partidaDoJogador.id) {
                simularPartidaInterna(partida)
            }
        }

        campeonatoDao.avancarRodada(campeonatoId)
        return resultadoJogador
    }

    suspend fun simularPartida(
        partida: PartidaEntity,
        escalacaoCasa: Escalacao? = null,
        escalacaoFora: Escalacao? = null
    ): ResultadoPartida {
        val escalacaoFinalCasa = escalacaoCasa ?: gerarEscalacaoIA(partida.timeCasaId)
        val escalacaoFinalFora = escalacaoFora ?: gerarEscalacaoIA(partida.timeForaId)

        val resultado = simulador.simular(
            partidaId = partida.id,
            casa = escalacaoFinalCasa,
            fora = escalacaoFinalFora
        )

        persistirResultado(resultado)
        return resultado
    }

    suspend fun buscarProximaPartida(timeId: Int): PartidaEntity? =
        partidaDao.buscarProximaPartida(timeId)

    fun observarUltimosResultados(timeId: Int): Flow<List<PartidaEntity>> =
        partidaDao.observeUltimosResultados(timeId)

    suspend fun buscarUltimosResultados(timeId: Int): List<PartidaEntity> =
        partidaDao.buscarUltimosResultados(timeId)

    suspend fun processarFimDeTemporada(temporadaId: Int) {
        val campeonatos = campeonatoDao.buscarAtivos()
        for (campeonato in campeonatos) {
            campeonatoDao.encerrar(campeonato.id)
        }
        jogadorRepository.processarDesenvolvimentoAnual()
    }

    // Retorna informações da nova temporada criada
    data class NovaTemporadaInfo(val campeonatoId: Int, val temporadaId: Int, val ano: Int)

    suspend fun encerrarTemporadaComHallDaFama(
        campeonatoId: Int,
        temporadaId: Int,
        ano: Int
    ): NovaTemporadaInfo {
        // Ler participantes antes de encerrar (FK cascade não apaga, mas boa prática)
        val participantes = campeonatoDao.buscarIdsParticipantes(campeonatoId)

        // Ler classificação final (top 2)
        val top2 = classificacaoDao.buscarTop2(campeonatoId)
        val campeao = top2.getOrNull(0)
        val vice    = top2.getOrNull(1)

        // Ler artilheiro e garçom
        val artilheiro = partidaDao.buscarArtilheiroTop1(campeonatoId)
        val assistente = partidaDao.buscarAssisteTop1(campeonatoId)

        // Resolver nomes dos times
        val campeonato   = campeonatoDao.buscarPorId(campeonatoId)
        val nomeCampeao  = campeao?.let { timeRepository.buscarPorId(it.timeId)?.nome } ?: "Desconhecido"
        val nomeVice     = vice?.let { timeRepository.buscarPorId(it.timeId)?.nome } ?: ""

        // Salvar no Hall da Fama
        if (campeao != null) {
            hallDaFamaDao.inserir(
                HallDaFamaEntity(
                    ano               = ano,
                    nomeCampeonato    = campeonato?.nome ?: "Brasileirão Série A $ano",
                    campeaoTimeId     = campeao.timeId,
                    campeaoNome       = nomeCampeao,
                    viceTimeId        = vice?.timeId ?: -1,
                    viceNome          = nomeVice,
                    artilheiroId      = artilheiro?.jogadorId ?: -1,
                    artilheiroNome    = artilheiro?.nomeJogador ?: "",
                    artilheiroNomeAbrev = artilheiro?.nomeAbrev ?: "",
                    artilheiroGols    = artilheiro?.total ?: 0,
                    artilheiroNomeTime = artilheiro?.nomeTime ?: "",
                    assistenteId      = assistente?.jogadorId ?: -1,
                    assistenteNome    = assistente?.nomeJogador ?: "",
                    assistenteNomeAbrev = assistente?.nomeAbrev ?: "",
                    assistenciasTotais = assistente?.total ?: 0,
                    assistenteNomeTime = assistente?.nomeTime ?: ""
                )
            )
        }

        // Encerrar campeonato atual
        campeonatoDao.encerrar(campeonatoId)

        // Desenvolvimento anual dos jogadores
        jogadorRepository.processarDesenvolvimentoAnual()

        // Criar o novo campeonato da próxima temporada
        val novoAno          = ano + 1
        val novoTemporadaId  = temporadaId + 1
        val novoCampeonatoId = criarCampeonato(
            CampeonatoEntity(
                temporadaId  = novoTemporadaId,
                nome         = "Brasileirão Série A $novoAno",
                tipo         = TipoCampeonato.NACIONAL_DIVISAO1,
                formato      = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = (participantes.size - 1) * 2
            ),
            participantes
        )

        return NovaTemporadaInfo(novoCampeonatoId, novoTemporadaId, novoAno)
    }

    fun observarHallDaFama(): Flow<List<HallDaFamaEntity>> = hallDaFamaDao.observeTodos()

    suspend fun fecharMes(timeId: Int, temporadaId: Int, mes: Int) {
        val time = timeRepository.buscarPorId(timeId) ?: return
        val elenco = jogadorRepository.buscarDisponiveis(timeId)

        val fechamento = MotorFinanceiro.processarFechamentoMensal(
            time = time,
            elenco = elenco,
            partidasEmCasa = 2
        )

        // Tenta inserir o registro financeiro
        // Ignora erro de FK caso a temporada ainda não exista no banco
        try {
            financaDao.inserir(
                FinancaEntity(
                    timeId = timeId,
                    temporadaId = temporadaId,
                    mes = mes,
                    receitaBilheteria = fechamento.receitaBilheteria,
                    receitaPatrocinio = fechamento.receitaPatrocinio,
                    despesaSalarios = fechamento.despesaSalarios,
                    despesaInfraestrutura = fechamento.despesaInfraestrutura,
                    saldoFinal = fechamento.saldoFinal
                )
            )
        } catch (e: Exception) {
            // FK da temporada ainda não persistida — ignora o registro
            // mas continua atualizando o saldo do clube normalmente
        }

        if (fechamento.lucroOuPrejuizo >= 0) {
            timeRepository.creditarSaldo(timeId, fechamento.lucroOuPrejuizo)
        } else {
            timeRepository.debitarSaldo(timeId, -fechamento.lucroOuPrejuizo)
        }
    }

    // ─── Helpers privados ───

    private suspend fun simularPartidaInterna(partida: PartidaEntity) {
        val escalacaoCasa = gerarEscalacaoIA(partida.timeCasaId)
        val escalacaoFora = gerarEscalacaoIA(partida.timeForaId)

        val resultado = simulador.simular(
            partidaId = partida.id,
            casa = escalacaoCasa,
            fora = escalacaoFora
        )
        persistirResultado(resultado)
    }

    private suspend fun gerarEscalacaoIA(timeId: Int): Escalacao {
        val time = timeRepository.buscarPorId(timeId)
            ?: throw IllegalStateException("Time $timeId não encontrado")
        val elenco = jogadorRepository.buscarDisponiveis(timeId)
        return IATimeRival.gerarEscalacao(time, elenco)
    }

    private suspend fun persistirResultado(resultado: ResultadoPartida) {
        partidaDao.registrarResultado(
            resultado.partidaId,
            resultado.golsCasa,
            resultado.golsFora
        )

        partidaDao.inserirEventos(resultado.eventos.map { ev ->
            EventoPartidaEntity(
                partidaId = resultado.partidaId,
                jogadorId = ev.jogadorId,
                minuto = ev.minuto,
                tipo = ev.tipo,
                descricao = ev.descricao
            )
        })

        val (deltaCasa, deltaFora) = MotorCampeonato.calcularDelta(resultado)
        val campeonatoId = buscarCampeonatoIdDaPartida(resultado.partidaId)

        if (campeonatoId != null) {
            classificacaoDao.atualizarEstatisticas(
                campeonatoId, deltaCasa.timeId,
                deltaCasa.v, deltaCasa.e, deltaCasa.d, deltaCasa.gp, deltaCasa.gc
            )
            classificacaoDao.atualizarEstatisticas(
                campeonatoId, deltaFora.timeId,
                deltaFora.v, deltaFora.e, deltaFora.d, deltaFora.gp, deltaFora.gc
            )
        }

        resultado.eventos
            .filter { it.tipo == TipoEvento.LESAO }
            .forEach { _ -> }
    }

    private suspend fun buscarCampeonatoIdDaPartida(partidaId: Int): Int? =
        partidaDao.buscarCampeonatoId(partidaId)
}