package br.com.managerfoot.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.managerfoot.data.dao.ArtilheiroDto
import br.com.managerfoot.data.dao.CalendarioPartidaDto
import br.com.managerfoot.data.dao.ConfrontoPartidaDto
import br.com.managerfoot.data.dao.CopaPartidaDto
import br.com.managerfoot.data.dao.PartidaDao
import br.com.managerfoot.data.database.entities.*
import br.com.managerfoot.data.datasource.GameDataStore
import br.com.managerfoot.data.datasource.SeedDataSource
import br.com.managerfoot.data.repository.*
import br.com.managerfoot.domain.engine.*
import br.com.managerfoot.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════
//  InicioViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class InicioViewModel @Inject constructor(
    private val gameDataStore: GameDataStore,
    private val timeRepository: TimeRepository,
    private val jogadorRepository: JogadorRepository,
    private val gameRepository: GameRepository,
    private val seedDataSource: SeedDataSource,
    private val timeDao: br.com.managerfoot.data.dao.TimeDao,
    private val jogadorDao: br.com.managerfoot.data.dao.JogadorDao
) : ViewModel() {

    val saveState = gameDataStore.saveState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
    )

    val timesDisponiveis: StateFlow<List<Time>> = timeRepository.observeTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow<InicioUiState>(InicioUiState.Carregando)
    val uiState: StateFlow<InicioUiState> = _uiState.asStateFlow()

    init { verificarSave() }

    private fun verificarSave() = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        _uiState.value = if (save.jogoInicializado) InicioUiState.TemSave
        else InicioUiState.SemSave
    }

    // Carrega os times no banco (se necessário) e abre a tela de seleção de clube
    fun iniciarSelecionarTime() = viewModelScope.launch {
        _uiState.value = InicioUiState.Carregando
        try {
            // Só insere o seed quando o banco está vazio (nenhum time cadastrado).
            // Se já houver times (jogo em andamento), preserva todos os dados para não
            // corromper campeonatos, partidas e progresso do jogo atual caso o usuário
            // feche o app antes de confirmar a seleção do novo time.
            val timesExistentes = timeRepository.observeTodos().first()
            if (timesExistentes.isEmpty()) {
                val seed = seedDataSource.carregar()
                timeDao.inserirTodos(seed.times)
                jogadorDao.inserirTodos(seed.jogadores)
            }
            _uiState.value = InicioUiState.SelecionandoTime
        } catch (e: Exception) {
            _uiState.value = InicioUiState.Erro(e.message ?: "Erro ao carregar clubes")
        }
    }

    fun iniciarNovoJogo(timeId: Int) = viewModelScope.launch {
        _uiState.value = InicioUiState.Carregando
        try {
            // Limpa TODOS os dados do jogo atual (campeonatos, partidas, times, jogadores, etc.)
            gameDataStore.resetar()
            gameRepository.limparTodosDados()
            timeDao.deleteAll()
            jogadorDao.deleteAll()

            // Insere dados frescos do seed
            val seed = seedDataSource.carregar()
            timeDao.inserirTodos(seed.times)
            jogadorDao.inserirTodos(seed.jogadores)

            // Marca apenas o time escolhido como controlado pelo jogador
            val timeEntity = timeDao.buscarPorId(timeId) ?: run {
                _uiState.value = InicioUiState.Erro("Time não encontrado")
                return@launch
            }
            timeDao.atualizar(timeEntity.copy(controladoPorJogador = true))

            val temporadaId = 1
            // Usa o campo divisao do seed — não assume IDs fixos por divisão
            val participantesA = timeDao.observePorDivisao(1).first().map { it.id }
            val participantesB = timeDao.observePorDivisao(2).first().map { it.id }
            val participantesC = timeDao.observePorDivisao(3).first().map { it.id }
            val participantesD = timeDao.observePorDivisao(4).first().map { it.id }

            val campeonatoAId = gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId = temporadaId,
                    nome = "Brasileiro Série A 2026",
                    tipo = TipoCampeonato.NACIONAL_DIVISAO1,
                    formato = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = 38
                ),
                participantesA
            )
            val campeonatoBId = gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId = temporadaId,
                    nome = "Brasileiro Série B 2026",
                    tipo = TipoCampeonato.NACIONAL_DIVISAO2,
                    formato = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = 38
                ),
                participantesB
            )
            val campeonatoCId = if (participantesC.isNotEmpty()) gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId = temporadaId,
                    nome = "Brasileiro Série C 2026",
                    tipo = TipoCampeonato.NACIONAL_DIVISAO3,
                    formato = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = (participantesC.size - 1) * 2
                ),
                participantesC
            ) else -1
            val campeonatoDId = if (participantesD.isNotEmpty()) gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId = temporadaId,
                    nome = "Brasileiro Série D 2026",
                    tipo = TipoCampeonato.NACIONAL_DIVISAO4,
                    formato = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = (participantesD.size - 1) * 2
                ),
                participantesD
            ) else -1

            // Determina em qual divisão o time escolhido está
            val timeEntity2 = timeDao.buscarPorId(timeId)
            val divJogador = timeEntity2?.divisao ?: 1
            val campeonatoIdJogador = when (divJogador) {
                1 -> campeonatoAId; 2 -> campeonatoBId; 3 -> campeonatoCId; else -> campeonatoDId
            }

            // Cria Copa do Brasil: top 64 por reputação
            val todos = timeDao.observeTodos().first()
            val copaParticipantes = todos.sortedByDescending { it.reputacao }.take(64).map { it.id }
            val copaId = gameRepository.criarCopa(
                CampeonatoEntity(
                    temporadaId  = temporadaId,
                    nome         = "Copa do Brasil 2026",
                    tipo         = TipoCampeonato.COPA_NACIONAL,
                    formato      = FormatoCampeonato.MATA_MATA_IDA_VOLTA,
                    totalRodadas = 12
                ),
                copaParticipantes
            )

            gameDataStore.salvarNovoJogo(
                timeId = timeId,
                temporadaId = temporadaId,
                campeonatoId = campeonatoIdJogador,
                campeonatoAId = campeonatoAId,
                campeonatoBId = campeonatoBId,
                campeonatoCId = campeonatoCId,
                campeonatoDId = campeonatoDId,
                copaId = copaId,
                ano = 2026
            )
            _uiState.value = InicioUiState.JogoIniciado(timeId)
        } catch (e: Exception) {
            _uiState.value = InicioUiState.Erro(e.message ?: "Erro desconhecido")
        }
    }

    fun continuarJogo() = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        if (save.jogoInicializado) {
            _uiState.value = InicioUiState.JogoIniciado(save.timeIdJogador)
        }
    }
}

sealed class InicioUiState {
    object Carregando       : InicioUiState()
    object SemSave          : InicioUiState()
    object TemSave          : InicioUiState()
    object SelecionandoTime : InicioUiState()
    data class JogoIniciado(val timeId: Int) : InicioUiState()
    data class Erro(val mensagem: String) : InicioUiState()
}

// ═══════════════════════════════════════════════════════════
//  DashboardViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val gameDataStore: GameDataStore,
    private val timeRepository: TimeRepository,
    private val jogadorRepository: JogadorRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    val saveState = gameDataStore.saveState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _timeJogador = MutableStateFlow<Time?>(null)
    val timeJogador: StateFlow<Time?> = _timeJogador.asStateFlow()

    // Todos os times para resolver nomes nas telas
    private val _todosOsTimes = MutableStateFlow<List<Time>>(emptyList())
    val todosOsTimes: StateFlow<List<Time>> = _todosOsTimes.asStateFlow()

    private val _proximaPartida = MutableStateFlow<PartidaEntity?>(null)
    val proximaPartida: StateFlow<PartidaEntity?> = _proximaPartida.asStateFlow()

    private val _ultimosResultados = MutableStateFlow<List<PartidaEntity>>(emptyList())
    val ultimosResultados: StateFlow<List<PartidaEntity>> = _ultimosResultados.asStateFlow()

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Carregando)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Resultado da última simulação — quando não nulo, abre a tela de simulação
    private val _resultadoSimulado = MutableStateFlow<ResultadoPartida?>(null)
    val resultadoSimulado: StateFlow<ResultadoPartida?> = _resultadoSimulado.asStateFlow()

    // Contexto entre metades - armazena estado do 1o tempo para continuar no 2o
    private var _contextoSimulacao: ContextoSimulacaoMetade? = null

    // Escalação em uso durante a simulação (para o painel do intervalo)
    private val _escalacaoSimulacao = MutableStateFlow<Escalacao?>(null)
    val escalacaoSimulacao: StateFlow<Escalacao?> = _escalacaoSimulacao.asStateFlow()

    // Resultado dos pênaltis (Copa): não nulo quando disputa de pênaltis foi simulada
    private val _penaltisResultado = MutableStateFlow<ResultadoPenaltis?>(null)
    val penaltisResultado: StateFlow<ResultadoPenaltis?> = _penaltisResultado.asStateFlow()

    // Dados do adversário para a disputa interativa de pênaltis
    private val _dadosPenaltisAdversario = MutableStateFlow<DadosPenaltiAdversario?>(null)
    val dadosPenaltisAdversario: StateFlow<DadosPenaltiAdversario?> = _dadosPenaltisAdversario.asStateFlow()

    // Indica que a disputa interativa já foi concluída (evita re-animação)
    private val _penaltisInterativoConcluido = MutableStateFlow(false)
    val penaltisInterativoConcluido: StateFlow<Boolean> = _penaltisInterativoConcluido.asStateFlow()

    private val _posicaoNaTabela = MutableStateFlow(0)
    val posicaoNaTabela: StateFlow<Int> = _posicaoNaTabela.asStateFlow()

    fun carregar(timeId: Int) = viewModelScope.launch {
        // Carrega todos os times para resolver nomes
        launch {
            timeRepository.observeTodos().collect { lista ->
                _todosOsTimes.value = lista
                _timeJogador.value = lista.find { it.id == timeId }
            }
        }
        // Obtém o campeonato atual do save state
        val save = gameDataStore.saveState.first()
        val campeonatoId = save.campeonatoId
        
        // Últimos resultados: busca pontual — não reage a mudanças do banco
        // durante a simulação para evitar exibir o resultado antes da animação finalizar
        val copaIdInicial = save.copaId
        _ultimosResultados.value = gameRepository.buscarUltimosResultados(
            timeId, listOf(campeonatoId, copaIdInicial).filter { it > 0 }
        )
        _proximaPartida.value = gameRepository.buscarProximaPartida(timeId)
        _posicaoNaTabela.value = gameRepository.buscarPosicaoNaTabela(campeonatoId, timeId)
        _uiState.value = DashboardUiState.Pronto
    }

    // Simula a partida do jogador e abre a tela de animação
    fun simularProximaPartida(campeonatoId: Int, rodada: Int) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        _proximaPartida.value ?: return@launch

        _uiState.value = DashboardUiState.Simulando

        try {
            // Carrega escalacao salva pelo jogador (ou cai no IA se nao houver)
            val escalacaoJogador = gameRepository.gerarEscalacaoJogador(save.timeIdJogador)
            _escalacaoSimulacao.value = escalacaoJogador

            // Simula apenas o 1o tempo; o 2o sera simulado apos o intervalo
            val (contexto, resultadoParcial) = gameRepository.iniciarPartidaComJogador(
                campeonatoId     = campeonatoId,
                rodada           = rodada,
                timeJogadorId    = save.timeIdJogador,
                escalacaoJogador = escalacaoJogador
            )
            _contextoSimulacao = contexto
            _resultadoSimulado.value = resultadoParcial
        } catch (e: Exception) {
            // Ignora erro e volta para o estado normal
        }

        _uiState.value = DashboardUiState.Pronto
    }

    // Chamado pela tela de simulacao para obter eventos do 2o tempo (ou re-simulacao)
    suspend fun obterEventosSegundoTempo(
        titularesJogador: List<JogadorNaEscalacao>,
        reservasJogador: List<JogadorNaEscalacao>,
        substituicoes: List<InfoSubstituicao>,
        formacao: String,
        estilo: EstiloJogo,
        minInicio: Int,
        minFim: Int
    ): List<EventoSimulado> {
        val ctx = _contextoSimulacao ?: return emptyList()
        val (ctxAtualizado, eventos) = gameRepository.simularPeriodoComJogador(
            ctx              = ctx,
            titularesJogador = titularesJogador,
            reservasJogador  = reservasJogador,
            substituicoes    = substituicoes,
            formacao         = formacao,
            estilo           = estilo,
            minInicio        = minInicio,
            minFim           = minFim
        )
        _contextoSimulacao = ctxAtualizado
        return eventos
    }

    // Chamado pela tela de simulacao apos animacao do 2o tempo para finalizar e persistir
    suspend fun finalizarPartidaSimulada(golsCasa: Int, golsFora: Int) {
        val ctx  = _contextoSimulacao ?: return
        val save = gameDataStore.saveState.first()
        try {
            val resultadoFinal = gameRepository.finalizarPartidaComJogador(
                ctx           = ctx,
                golsCasaFinal = golsCasa,
                golsForaFinal = golsFora
            )
            _resultadoSimulado.value = resultadoFinal

            if (save.copaId > 0 && ctx.campeonatoId == save.copaId && !resultadoFinal.precisaPenaltis) {
                gameRepository.verificarEAvancarFaseCopa(save.copaId, save.anoAtual)
            }
            if (save.copaId > 0 && ctx.campeonatoId == save.copaId && resultadoFinal.precisaPenaltis) {
                _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                    copaId         = save.copaId,
                    voltaPartidaId = resultadoFinal.partidaId,
                    timeJogadorId  = save.timeIdJogador
                )
            }
            if (ctx.campeonatoId != save.copaId && save.copaId > 0) {
                gameRepository.simularProximaFaseCopaSeJogadorEliminado(
                    copaId        = save.copaId,
                    timeJogadorId = save.timeIdJogador,
                    anoAtual      = save.anoAtual
                )
            }
        } catch (e: Exception) { /* ignora */ }
        _contextoSimulacao = null
        _proximaPartida.value = gameRepository.buscarProximaPartida(save.timeIdJogador)
    }

    // Chamado quando o jogador fecha a tela de simulação
    fun fecharSimulacao() = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        // Atualiza últimos resultados só agora, após a animação ter sido exibida
        _ultimosResultados.value = gameRepository.buscarUltimosResultados(
            save.timeIdJogador, listOf(save.campeonatoId, save.copaId).filter { it > 0 }
        )
        _posicaoNaTabela.value = gameRepository.buscarPosicaoNaTabela(save.campeonatoId, save.timeIdJogador)
        _resultadoSimulado.value = null
        _escalacaoSimulacao.value = null
        _penaltisResultado.value = null
        _dadosPenaltisAdversario.value = null
        _penaltisInterativoConcluido.value = false
    }

    // Chamado quando o jogador conclui a disputa interativa de pênaltis
    fun finalizarPenaltisJogador(resultado: ResultadoPenaltis) = viewModelScope.launch {
        val save          = gameDataStore.saveState.first()
        val resultadoPartida = _resultadoSimulado.value ?: return@launch
        try {
            gameRepository.persistirResultadoPenaltisJogador(
                resultado       = resultado,
                copaId          = save.copaId,
                anoAtual        = save.anoAtual,
                voltaPartidaId  = resultadoPartida.partidaId
            )
            _penaltisInterativoConcluido.value = true
        } catch (_: Exception) { }
    }

    // Chamado quando o jogador confirma os cobradores de pênalti
    fun simularPenaltisJogador(
        cobradores: List<JogadorNaEscalacao>,
        goleiroDefesa: Int
    ) = viewModelScope.launch {
        val save    = gameDataStore.saveState.first()
        val resultado = _resultadoSimulado.value ?: return@launch
        try {
            val penaltis = gameRepository.simularEPersistirPenaltisJogador(
                copaId               = save.copaId,
                anoAtual             = save.anoAtual,
                voltaPartidaId       = resultado.partidaId,
                cobradoresJogador    = cobradores,
                timeJogadorId        = save.timeIdJogador,
                goleiroJogadorDefesa = goleiroDefesa
            )
            _penaltisResultado.value = penaltis
        } catch (_: Exception) { }
    }

    fun fecharMes() = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        gameRepository.fecharMes(save.timeIdJogador, save.temporadaId, save.mesAtual)
        gameDataStore.avancarMes()
    }

    fun encerrarTemporada() = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        _uiState.value = DashboardUiState.Simulando
        try {
            val novaInfo = gameRepository.encerrarTemporadaComHallDaFama(
                campeonatoAId = save.campeonatoAId,
                campeonatoBId = save.campeonatoBId,
                campeonatoCId = save.campeonatoCId,
                campeonatoDId = save.campeonatoDId,
                temporadaId   = save.temporadaId,
                ano           = save.anoAtual
            )
            // Determina em qual divisão o jogador estará na próxima temporada
            val divJogador = timeRepository.buscarPorId(save.timeIdJogador)?.divisao ?: 1
            val novoCampJogador = when (divJogador) {
                1 -> novaInfo.campeonatoAId; 2 -> novaInfo.campeonatoBId
                3 -> novaInfo.campeonatoCId; else -> novaInfo.campeonatoDId
            }
            gameDataStore.salvarNovaTemporada(
                campeonatoId  = novoCampJogador,
                campeonatoAId = novaInfo.campeonatoAId,
                campeonatoBId = novaInfo.campeonatoBId,
                campeonatoCId = novaInfo.campeonatoCId,
                campeonatoDId = novaInfo.campeonatoDId,
                copaId        = novaInfo.copaId,
                temporadaId   = novaInfo.temporadaId,
                ano           = novaInfo.ano
            )
            val saveAtualizado = gameDataStore.saveState.first()
            _proximaPartida.value    = gameRepository.buscarProximaPartida(saveAtualizado.timeIdJogador)
            _ultimosResultados.value = gameRepository.buscarUltimosResultados(
                saveAtualizado.timeIdJogador,
                listOf(saveAtualizado.campeonatoId, saveAtualizado.copaId).filter { it > 0 }
            )
        } catch (e: Exception) {
            // Registrar erro sem travar a UI
        }
        _uiState.value = DashboardUiState.Pronto
    }
}

sealed class DashboardUiState {
    object Carregando : DashboardUiState()
    object Pronto     : DashboardUiState()
    object Simulando  : DashboardUiState()
}

// ═══════════════════════════════════════════════════════════
//  EscalacaoViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class EscalacaoViewModel @Inject constructor(
    private val gameDataStore: GameDataStore,
    private val jogadorRepository: JogadorRepository,
    private val timeRepository: TimeRepository
) : ViewModel() {

    private val _elenco = MutableStateFlow<List<Jogador>>(emptyList())
    val elenco: StateFlow<List<Jogador>> = _elenco.asStateFlow()

    private val _escalacao = MutableStateFlow<Escalacao?>(null)
    val escalacao: StateFlow<Escalacao?> = _escalacao.asStateFlow()

    private val _jogadorSelecionado = MutableStateFlow<Jogador?>(null)
    val jogadorSelecionado: StateFlow<Jogador?> = _jogadorSelecionado.asStateFlow()

    private val _adversario = MutableStateFlow<Time?>(null)
    val adversario: StateFlow<Time?> = _adversario.asStateFlow()

    private var timeIdCarregado: Int = -1

    fun carregar(timeId: Int) = viewModelScope.launch {
        timeIdCarregado = timeId
        jogadorRepository.observeElenco(timeId).collect { lista ->
            _elenco.value = lista
            if (_escalacao.value == null) {
                val time = timeRepository.buscarPorId(timeId) ?: return@collect
                val titularesSalvos = jogadorRepository.buscarTitularesSalvos(timeId)
                if (titularesSalvos.isNotEmpty()) {
                    val reservasSalvas = jogadorRepository.buscarReservasSalvas(timeId)
                    val titulares = titularesSalvos.map { j -> JogadorNaEscalacao(j, j.posicaoEscalado ?: j.posicao) }
                    val reservas  = reservasSalvas.map  { j -> JogadorNaEscalacao(j, j.posicaoEscalado ?: j.posicao) }
                    _escalacao.value = Escalacao(time = time, titulares = titulares, reservas = reservas)
                } else {
                    val gerada = IATimeRival.gerarEscalacao(time, lista)
                    // Persiste os titulares gerados pela IA para que futuras aberturas
                    // da tela carreguem do banco (e as reservas adicionadas pelo usuário persistam).
                    gerada.titulares.forEach { jne ->
                        val pos = if (jne.posicaoUsada != jne.jogador.posicao) jne.posicaoUsada else null
                        jogadorRepository.atualizarEscalacao(jne.jogador.id, 1, pos)
                    }
                    // Carrega reservas que o usuário possa ter salvo anteriormente
                    val reservasSalvas = jogadorRepository.buscarReservasSalvas(timeId)
                    val reservas = reservasSalvas.map { j -> JogadorNaEscalacao(j, j.posicaoEscalado ?: j.posicao) }
                    _escalacao.value = gerada.copy(reservas = reservas)
                }
            }
        }
    }

    fun selecionarJogador(jogador: Jogador?) { _jogadorSelecionado.value = jogador }

    fun aposentarJogador(jogadorId: Int) = viewModelScope.launch {
        jogadorRepository.aposentarJogador(jogadorId)
    }

    fun carregarAdversario(timeId: Int) = viewModelScope.launch {
        _adversario.value = timeRepository.buscarPorId(timeId)
    }

    fun moverParaTitular(jogador: Jogador, posicao: Posicao) {
        val atual = _escalacao.value ?: return
        // Guarda: não faz nada se já está nos titulares ou se está cheio
        if (atual.titulares.size >= 11) return
        if (atual.titulares.any { it.jogador.id == jogador.id }) return
        val novosTitulares = atual.titulares + JogadorNaEscalacao(jogador, posicao)
        val novasReservas  = atual.reservas.filter { it.jogador.id != jogador.id }
        _escalacao.value = atual.copy(titulares = novosTitulares, reservas = novasReservas)
        val posToSave = if (posicao != jogador.posicao) posicao else null
        persistirEscalacao(jogador.id, 1, posToSave)
    }

    fun moverParaReserva(jogador: Jogador) {
        val atual = _escalacao.value ?: return
        val novosTitulares = atual.titulares.filter { it.jogador.id != jogador.id }
        // Não adiciona se já é reserva ou se o banco está cheio (11 jogadores)
        val jaEhReserva = atual.reservas.any { it.jogador.id == jogador.id }
        val novasReservas = if (jaEhReserva || atual.reservas.size >= 11) {
            atual.reservas
        } else {
            atual.reservas + JogadorNaEscalacao(jogador, jogador.posicao)
        }
        _escalacao.value = atual.copy(titulares = novosTitulares, reservas = novasReservas)
        if (!jaEhReserva && atual.reservas.size < 11) persistirEscalacao(jogador.id, 2)
    }

    fun trocarPosicoes(a: JogadorNaEscalacao, b: JogadorNaEscalacao) {
        val atual = _escalacao.value ?: return
        val novosTitulares = atual.titulares.map { jne ->
            when (jne.jogador.id) {
                a.jogador.id -> jne.copy(posicaoUsada = b.posicaoUsada)
                b.jogador.id -> jne.copy(posicaoUsada = a.posicaoUsada)
                else         -> jne
            }
        }
        _escalacao.value = atual.copy(titulares = novosTitulares)
        viewModelScope.launch {
            jogadorRepository.atualizarEscalacao(a.jogador.id, 1, b.posicaoUsada)
            jogadorRepository.atualizarEscalacao(b.jogador.id, 1, a.posicaoUsada)
        }
    }

    fun adicionarComoReserva(jogador: Jogador) {
        val atual = _escalacao.value ?: return
        if (atual.reservas.any { it.jogador.id == jogador.id }) return
        if (atual.titulares.any { it.jogador.id == jogador.id }) return
        if (atual.reservas.size >= 11) return
        val novasReservas = atual.reservas + JogadorNaEscalacao(jogador, jogador.posicao)
        _escalacao.value = atual.copy(reservas = novasReservas)
        persistirEscalacao(jogador.id, 2)
    }

    fun removerDaEscalacao(jogador: Jogador) {
        val atual = _escalacao.value ?: return
        val novasReservas = atual.reservas.filter { it.jogador.id != jogador.id }
        _escalacao.value = atual.copy(reservas = novasReservas)
        persistirEscalacao(jogador.id, 0)
    }

    private fun persistirEscalacao(jogadorId: Int, status: Int, posicao: Posicao? = null) {
        viewModelScope.launch { jogadorRepository.atualizarEscalacao(jogadorId, status, posicao) }
    }

    // Persiste a formação no banco, atualiza o Time em memória
    // e re-escala automaticamente os melhores jogadores disponíveis.
    fun mudarFormacao(formacao: String) = viewModelScope.launch {
        val atual = _escalacao.value ?: return@launch
        val novoTime = atual.time.copy(taticaFormacao = formacao)
        salvarTaticaNoBanco(novoTime)

        // Auto-escalação: gera o melhor 11 + 7 reservas para a nova formação
        val todos        = _elenco.value
        val disponiveis  = todos.filter { !it.lesionado && !it.suspenso }
        val novaEscalacao = IATimeRival.gerarEscalacao(novoTime, disponiveis)

        // Limpa todos os status de escalação no DB
        todos.forEach { j -> jogadorRepository.atualizarEscalacao(j.id, 0, null) }
        // Persiste novos titulares
        novaEscalacao.titulares.forEach { jne ->
            val pos = if (jne.posicaoUsada != jne.jogador.posicao) jne.posicaoUsada else null
            jogadorRepository.atualizarEscalacao(jne.jogador.id, 1, pos)
        }
        // Persiste reservas (melhores disponíveis restantes)
        novaEscalacao.reservas.forEach { jne ->
            jogadorRepository.atualizarEscalacao(jne.jogador.id, 2, null)
        }

        _escalacao.value = novaEscalacao
    }

    // Persiste o estilo de jogo no banco e atualiza o Time em memória
    fun mudarEstilo(estilo: br.com.managerfoot.data.database.entities.EstiloJogo) = viewModelScope.launch {
        val atual = _escalacao.value ?: return@launch
        val novoTime = atual.time.copy(estiloJogo = estilo)
        _escalacao.value = atual.copy(time = novoTime)
        salvarTaticaNoBanco(novoTime)
    }

    private suspend fun salvarTaticaNoBanco(time: Time) {
        val entity = timeRepository.buscarEntityPorId(time.id) ?: return
        timeRepository.atualizar(
            entity.copy(taticaFormacao = time.taticaFormacao, estiloJogo = time.estiloJogo)
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  MercadoViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class MercadoViewModel @Inject constructor(
    private val gameDataStore: GameDataStore,
    private val jogadorRepository: JogadorRepository,
    private val timeRepository: TimeRepository
) : ViewModel() {

    private val _jogadoresLivres = MutableStateFlow<List<Jogador>>(emptyList())
    val jogadoresLivres: StateFlow<List<Jogador>> = _jogadoresLivres.asStateFlow()

    private val _elencoAtual = MutableStateFlow<List<Jogador>>(emptyList())
    val elencoAtual: StateFlow<List<Jogador>> = _elencoAtual.asStateFlow()

    private val _saldo = MutableStateFlow(0L)
    val saldo: StateFlow<Long> = _saldo.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    private var timeId = -1

    fun carregar(tId: Int) {
        timeId = tId
        viewModelScope.launch {
            launch { jogadorRepository.observeLivres().collect { _jogadoresLivres.value = it } }
            launch { jogadorRepository.observeElenco(tId).collect { _elencoAtual.value = it } }
            launch {
                timeRepository.observeTodos().collect { times ->
                    _saldo.value = times.find { it.id == tId }?.saldo ?: 0L
                }
            }
        }
    }

    fun contratarJogador(jogador: Jogador) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        val time = timeRepository.buscarPorId(timeId) ?: return@launch
        if (time.saldo < jogador.valorMercado) {
            _mensagem.value = "Saldo insuficiente para contratar ${jogador.nomeAbreviado}"
            return@launch
        }
        jogadorRepository.realizarTransferencia(
            oferta = OfertaTransferencia(
                jogadorId = jogador.id,
                timeCompradorId = timeId,
                timeVendedorId = null,
                valor = jogador.valorMercado,
                salarioProposto = jogador.salario,
                contratoAnos = 2
            ),
            temporadaId = save.temporadaId,
            mes = save.mesAtual
        )
        _mensagem.value = "${jogador.nomeAbreviado} contratado com sucesso!"
    }

    fun venderJogador(jogador: Jogador) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        jogadorRepository.realizarTransferencia(
            oferta = OfertaTransferencia(
                jogadorId = jogador.id,
                timeCompradorId = -1,
                timeVendedorId = timeId,
                valor = jogador.valorMercado,
                salarioProposto = 0L,
                contratoAnos = 0
            ),
            temporadaId = save.temporadaId,
            mes = save.mesAtual
        )
        _mensagem.value = "${jogador.nomeAbreviado} vendido por ${formatarValor(jogador.valorMercado)}"
    }

    fun limparMensagem() { _mensagem.value = null }

    private fun formatarValor(centavos: Long): String {
        val reais = centavos / 100.0
        return when {
            reais >= 1_000_000 -> "R$ %.1f M".format(reais / 1_000_000)
            reais >= 1_000     -> "R$ %.0f mil".format(reais / 1_000)
            else               -> "R$ %.0f".format(reais)
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  TabelaViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class TabelaViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val timeRepository: TimeRepository
) : ViewModel() {

    private val _tabela = MutableStateFlow<List<ClassificacaoEntity>>(emptyList())
    val tabela: StateFlow<List<ClassificacaoEntity>> = _tabela.asStateFlow()

    private val _times = MutableStateFlow<List<Time>>(emptyList())
    val times: StateFlow<List<Time>> = _times.asStateFlow()

    // IDs das quatro divisões
    private var campeonatoAId: Int = -1
    private var campeonatoBId: Int = -1
    private var campeonatoCId: Int = -1
    private var campeonatoDId: Int = -1

    // Divisão selecionada em exibição (1 = Série A, 2 = Série B, 3 = Série C, 4 = Série D)
    private val _divisaoSelecionada = MutableStateFlow(1)
    val divisaoSelecionada: StateFlow<Int> = _divisaoSelecionada.asStateFlow()

    fun carregar(campAId: Int, campBId: Int, campCId: Int = -1, campDId: Int = -1, divisaoInicial: Int = 1) = viewModelScope.launch {
        campeonatoAId = campAId
        campeonatoBId = campBId
        campeonatoCId = campCId
        campeonatoDId = campDId
        _divisaoSelecionada.value = divisaoInicial
        launch { timeRepository.observeTodos().collect { _times.value = it } }
        coletarTabela(campIdParaDivisao(divisaoInicial))
    }

    fun selecionarDivisao(divisao: Int) = viewModelScope.launch {
        if (_divisaoSelecionada.value == divisao) return@launch
        _divisaoSelecionada.value = divisao
        coletarTabela(campIdParaDivisao(divisao))
    }

    private fun campIdParaDivisao(div: Int) = when (div) {
        1 -> campeonatoAId; 2 -> campeonatoBId; 3 -> campeonatoCId; else -> campeonatoDId
    }

    private fun coletarTabela(campId: Int) = viewModelScope.launch {
        if (campId <= 0) { _tabela.value = emptyList(); return@launch }
        gameRepository.observarTabela(campId).collect { lista ->
            _tabela.value = lista.sortedWith(
                compareByDescending<ClassificacaoEntity> { it.pontos }
                    .thenByDescending { it.vitorias }
                    .thenByDescending { it.saldoGols }
                    .thenByDescending { it.golsPro }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  ArtilheirosViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class ArtilheirosViewModel @Inject constructor(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _artilheiros = MutableStateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>>(emptyList())
    val artilheiros: StateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>> = _artilheiros.asStateFlow()

    private val _assistentes = MutableStateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>>(emptyList())
    val assistentes: StateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>> = _assistentes.asStateFlow()

    private val _artilheirosAllTime = MutableStateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>>(emptyList())
    val artilheirosAllTime: StateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>> = _artilheirosAllTime.asStateFlow()

    private val _assistentesAllTime = MutableStateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>>(emptyList())
    val assistentesAllTime: StateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>> = _assistentesAllTime.asStateFlow()

    private val _artilheirosHistorico = MutableStateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>>(emptyList())
    val artilheirosHistorico: StateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>> = _artilheirosHistorico.asStateFlow()

    private val _assistentesHistorico = MutableStateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>>(emptyList())
    val assistentesHistorico: StateFlow<List<br.com.managerfoot.data.dao.ArtilheiroDto>> = _assistentesHistorico.asStateFlow()

    // IDs das quatro divisões
    private var campeonatoAId: Int = -1
    private var campeonatoBId: Int = -1
    private var campeonatoCId: Int = -1
    private var campeonatoDId: Int = -1
    private var copaId: Int = -1

    private val _divisaoSelecionada = MutableStateFlow(1)
    val divisaoSelecionada: StateFlow<Int> = _divisaoSelecionada.asStateFlow()

    private val _divisaoHistoricoSelecionada = MutableStateFlow(0)
    val divisaoHistoricoSelecionada: StateFlow<Int> = _divisaoHistoricoSelecionada.asStateFlow()

    fun carregar(campAId: Int, campBId: Int, campCId: Int = -1, campDId: Int = -1, copaId: Int = -1, divisaoInicial: Int = 1) = viewModelScope.launch {
        campeonatoAId = campAId
        campeonatoBId = campBId
        campeonatoCId = campCId
        campeonatoDId = campDId
        this@ArtilheirosViewModel.copaId = copaId
        _divisaoSelecionada.value = divisaoInicial
        coletarArtilheiros(divisaoInicial)
        launch { gameRepository.observarArtilheirosAllTime().collect { _artilheirosAllTime.value = it } }
        launch { gameRepository.observarAssistentesAllTime().collect { _assistentesAllTime.value = it } }
        // histórico filtrado começa em "Todas" (flows de AllTime já populam _artilheirosAllTime)
    }

    fun selecionarDivisao(divisao: Int) = viewModelScope.launch {
        if (_divisaoSelecionada.value == divisao) return@launch
        _divisaoSelecionada.value = divisao
        coletarArtilheiros(divisao)
    }

    fun selecionarDivisaoHistorico(divisao: Int) = viewModelScope.launch {
        if (_divisaoHistoricoSelecionada.value == divisao) return@launch
        _divisaoHistoricoSelecionada.value = divisao
        coletarHistoricoFiltrado(divisao)
    }

    private fun campIdParaDivisao(div: Int) = when (div) {
        1 -> campeonatoAId; 2 -> campeonatoBId; 3 -> campeonatoCId; 4 -> campeonatoDId; else -> copaId
    }

    /** Mapeia divisão → tipo(s) de campeonato para filtrar o histórico */
    private fun tiposDaDiv(div: Int): List<String> = when (div) {
        1 -> listOf("NACIONAL_DIVISAO1")
        2 -> listOf("NACIONAL_DIVISAO2")
        3 -> listOf("NACIONAL_DIVISAO3")
        4 -> listOf("NACIONAL_DIVISAO4")
        5 -> listOf("COPA_NACIONAL")
        else -> emptyList()
    }

    /** div == 0 → todas as competições da temporada (multi-query) */
    private fun coletarArtilheiros(div: Int) = viewModelScope.launch {
        if (div == 0) {
            val ids = listOf(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, copaId)
                .filter { it > 0 }
            launch { gameRepository.observarArtilheirosMulti(ids).collect { _artilheiros.value = it } }
            launch { gameRepository.observarAssistentesMulti(ids).collect { _assistentes.value = it } }
        } else {
            val campId = campIdParaDivisao(div)
            launch { gameRepository.observarArtilheiros(campId).collect { _artilheiros.value = it } }
            launch { gameRepository.observarAssistentes(campId).collect { _assistentes.value = it } }
        }
    }

    /** div == 0 → usa AllTime sem filtro (já populado no carregar); caso contrário filtra por tipo */
    private fun coletarHistoricoFiltrado(div: Int) = viewModelScope.launch {
        if (div == 0) {
            _artilheirosHistorico.value = emptyList()
            _assistentesHistorico.value = emptyList()
            return@launch
        }
        val tipos = tiposDaDiv(div)
        launch { gameRepository.observarArtilheirosHistoricoFiltrado(tipos).collect { _artilheirosHistorico.value = it } }
        launch { gameRepository.observarAssistentesHistoricoFiltrado(tipos).collect { _assistentesHistorico.value = it } }
    }
}

// ═══════════════════════════════════════════════════════════
//  HallDaFamaViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class HallDaFamaViewModel @Inject constructor(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _todos: StateFlow<List<br.com.managerfoot.data.database.entities.HallDaFamaEntity>> =
        gameRepository.observarHallDaFama()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _divisaoSelecionada = MutableStateFlow(0)  // 0=todas, 1=A, 2=B, 3=C, 4=D
    val divisaoSelecionada: StateFlow<Int> = _divisaoSelecionada.asStateFlow()

    val hallDaFama: StateFlow<List<br.com.managerfoot.data.database.entities.HallDaFamaEntity>> =
        combine(_todos, _divisaoSelecionada) { lista, div ->
            if (div == 0) lista else lista.filter { it.divisao == div }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selecionarDivisao(div: Int) { _divisaoSelecionada.value = div }
}

// ═══════════════════════════════════════════════════════════
//  ConfrontoViewModel
// ═══════════════════════════════════════════════════════════
data class ConfrontoStats(
    val partidas: List<ConfrontoPartidaDto>,
    val vitoriasA: Int,
    val empates: Int,
    val vitoriasB: Int,
    val golsA: Int,
    val golsB: Int,
    val maiorSequenciaVitoriasA: Int,
    val maiorSequenciaVitoriasB: Int,
    val maiorSequenciaInvicA: Int,
    val maiorSequenciaInvicB: Int,
    val artilheiros: List<ArtilheiroDto>
)

@HiltViewModel
class ConfrontoViewModel @Inject constructor(
    private val timeRepository: TimeRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _todos = MutableStateFlow<List<Time>>(emptyList())
    val todos: StateFlow<List<Time>> = _todos.asStateFlow()

    private val _timeA = MutableStateFlow<Time?>(null)
    val timeASelecionado: StateFlow<Time?> = _timeA.asStateFlow()

    private val _timeB = MutableStateFlow<Time?>(null)
    val timeBSelecionado: StateFlow<Time?> = _timeB.asStateFlow()

    private val _stats = MutableStateFlow<ConfrontoStats?>(null)
    val stats: StateFlow<ConfrontoStats?> = _stats.asStateFlow()

    fun carregar(preTimeId: Int) = viewModelScope.launch {
        timeRepository.observeTodos().collect { lista ->
            _todos.value = lista
            if (_timeA.value == null && preTimeId > 0) {
                _timeA.value = lista.find { it.id == preTimeId }
            }
        }
    }

    fun selecionarTimeA(time: Time) {
        _timeA.value = time
        _stats.value = null
        val b = _timeB.value
        if (b != null) calcularStats(time, b)
    }

    fun selecionarTimeB(time: Time) {
        _timeB.value = time
        _stats.value = null
        val a = _timeA.value
        if (a != null) calcularStats(a, time)
    }

    private fun calcularStats(timeA: Time, timeB: Time) = viewModelScope.launch {
        val partidas = gameRepository.buscarPartidasConfronto(timeA.id, timeB.id)
        val artilheiros = gameRepository.buscarArtilheirosConfronto(timeA.id, timeB.id)

        var vitoriasA = 0; var vitoriasB = 0; var empates = 0
        var golsA = 0; var golsB = 0

        // Resultados da perspectiva de A: 'W'=A venceu, 'L'=B venceu, 'D'=empate
        val resultados = partidas.map { p ->
            val aEhCasa = p.timeCasaId == timeA.id
            val gA = if (aEhCasa) p.golsCasa else p.golsFora
            val gB = if (aEhCasa) p.golsFora else p.golsCasa
            golsA += gA; golsB += gB
            when {
                gA > gB -> { vitoriasA++; 'W' }
                gA < gB -> { vitoriasB++; 'L' }
                else    -> { empates++;   'D' }
            }
        }

        _stats.value = ConfrontoStats(
            partidas                = partidas,
            vitoriasA               = vitoriasA,
            empates                 = empates,
            vitoriasB               = vitoriasB,
            golsA                   = golsA,
            golsB                   = golsB,
            maiorSequenciaVitoriasA = maiorSequencia(resultados, 'W'),
            maiorSequenciaVitoriasB = maiorSequencia(resultados, 'L'),
            maiorSequenciaInvicA    = maiorSequencia(resultados, 'W', 'D'),
            maiorSequenciaInvicB    = maiorSequencia(resultados, 'L', 'D'),
            artilheiros             = artilheiros
        )
    }

    private fun maiorSequencia(resultados: List<Char>, vararg tipos: Char): Int {
        var max = 0; var atual = 0
        for (r in resultados) {
            if (r in tipos) { if (++atual > max) max = atual } else atual = 0
        }
        return max
    }
}

// ═══════════════════════════════════════════════════════════
//  CalendarioViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class CalendarioViewModel @Inject constructor(
    private val partidaDao: PartidaDao
) : ViewModel() {

    private val _partidas = MutableStateFlow<List<CalendarioPartidaDto>>(emptyList())
    val partidas: StateFlow<List<CalendarioPartidaDto>> = _partidas.asStateFlow()

    fun carregar(timeId: Int) = viewModelScope.launch {
        partidaDao.observeCalendario(timeId).collect { _partidas.value = it }
    }
}

// ═══════════════════════════════════════════════════════════
//  CopaChaveamentoViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class CopaChaveamentoViewModel @Inject constructor(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _partidas = MutableStateFlow<List<CopaPartidaDto>>(emptyList())
    val partidas: StateFlow<List<CopaPartidaDto>> = _partidas.asStateFlow()

    fun carregar(copaId: Int) = viewModelScope.launch {
        if (copaId <= 0) return@launch
        gameRepository.observarCopaPartidas(copaId).collect { _partidas.value = it }
    }
}

// ═══════════════════════════════════════════════════════════
//  RankingGeralViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class RankingGeralViewModel @Inject constructor(
    private val gameRepository: GameRepository
) : ViewModel() {

    val ranking: StateFlow<List<RankingGeralEntity>> =
        gameRepository.observarRankingGeral()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

// ═══════════════════════════════════════════════════════════
//  EstatisticasTimeViewModel
// ═══════════════════════════════════════════════════════════
data class TemporadaCompeticao(
    val campeonatoId: Int,
    val nome: String,
    val vitorias: Int,
    val empates: Int,
    val derrotas: Int,
    val golsPro: Int,
    val golsContra: Int,
    val pontos: Int,
    val jogos: Int,
    val jogadores: List<br.com.managerfoot.data.dao.EstatisticaJogadorDto>,
    val ehCopa: Boolean = false,
    val faseAtingida: String? = null
)

data class HistoricoTemporada(
    val campeonatoId: Int,
    val nomeCampeonato: String,
    val temporadaId: Int,
    val tipo: String,
    val posicao: Int,
    val vitorias: Int,
    val empates: Int,
    val derrotas: Int,
    val golsPro: Int,
    val golsContra: Int,
    val pontos: Int,
    val jogos: Int,
    val faseAtingida: String? = null
)

@HiltViewModel
class EstatisticasTimeViewModel @Inject constructor(
    private val gameDataStore: GameDataStore,
    private val gameRepository: GameRepository,
    private val jogadorRepository: br.com.managerfoot.data.repository.JogadorRepository
) : ViewModel() {

    private val _temporadaStats = MutableStateFlow<List<TemporadaCompeticao>>(emptyList())
    val temporadaStats: StateFlow<List<TemporadaCompeticao>> = _temporadaStats.asStateFlow()

    private val _historicoStats = MutableStateFlow<List<HistoricoTemporada>>(emptyList())
    val historicoStats: StateFlow<List<HistoricoTemporada>> = _historicoStats.asStateFlow()

    private val _jogadoresHistorico = MutableStateFlow<List<br.com.managerfoot.data.dao.EstatisticaJogadorDto>>(emptyList())
    val jogadoresHistorico: StateFlow<List<br.com.managerfoot.data.dao.EstatisticaJogadorDto>> = _jogadoresHistorico.asStateFlow()

    private val _notasElenco = MutableStateFlow<List<br.com.managerfoot.domain.model.Jogador>>(emptyList())
    val notasElenco: StateFlow<List<br.com.managerfoot.domain.model.Jogador>> = _notasElenco.asStateFlow()

    fun carregar(timeId: Int) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()

        // Nomeia o campeonato da liga do jogador
        val nomeLiga = when (save.campeonatoId) {
            save.campeonatoAId -> "Série A"
            save.campeonatoBId -> "Série B"
            save.campeonatoCId -> "Série C"
            save.campeonatoDId -> "Série D"
            else -> "Liga"
        }
        val temporadaList = mutableListOf<TemporadaCompeticao>()

        // Liga do jogador na temporada corrente
        if (save.campeonatoId > 0) {
            val classif = gameRepository.buscarClassificacaoDoTime(save.campeonatoId, timeId)
            if (classif != null) {
                val jogadores = gameRepository.buscarEstatisticasJogadoresPorCampeonato(save.campeonatoId, timeId)
                temporadaList.add(
                    TemporadaCompeticao(
                        campeonatoId = save.campeonatoId,
                        nome         = nomeLiga,
                        vitorias     = classif.vitorias,
                        empates      = classif.empates,
                        derrotas     = classif.derrotas,
                        golsPro      = classif.golsPro,
                        golsContra   = classif.golsContra,
                        pontos       = classif.pontos,
                        jogos        = classif.jogos,
                        jogadores    = jogadores
                    )
                )
            }
        }

        // Copa do Brasil da temporada corrente (sem clasificaçao — mata-mata)
        if (save.copaId > 0) {
            val partidas = gameRepository.buscarPartidasDaEquipe(save.copaId, timeId)
            if (partidas.isNotEmpty()) {
                val jogadores = gameRepository.buscarEstatisticasJogadoresPorCampeonato(save.copaId, timeId)
                var v = 0; var e = 0; var d = 0; var gp = 0; var gc = 0
                var faseAtingida: String? = null
                for (p in partidas) {
                    val ehCasa = p.timeCasaId == timeId
                    val gf = if (ehCasa) p.golsCasa ?: 0 else p.golsFora ?: 0
                    val ga = if (ehCasa) p.golsFora ?: 0 else p.golsCasa ?: 0
                    gp += gf; gc += ga
                    when { gf > ga -> v++; gf < ga -> d++; else -> e++ }
                    if (p.fase != null) faseAtingida = p.fase
                }
                temporadaList.add(
                    TemporadaCompeticao(
                        campeonatoId = save.copaId,
                        nome         = "Copa do Brasil",
                        vitorias     = v,
                        empates      = e,
                        derrotas     = d,
                        golsPro      = gp,
                        golsContra   = gc,
                        pontos       = 0,
                        jogos        = partidas.size,
                        jogadores    = jogadores,
                        ehCopa       = true,
                        faseAtingida = faseAtingida
                    )
                )
            }
        }

        _temporadaStats.value = temporadaList

        // Histórico de ligas
        val historico = gameRepository.buscarHistoricoDoTime(timeId)
        val ligaEntries = historico.map { h ->
            HistoricoTemporada(
                campeonatoId   = h.campeonatoId,
                nomeCampeonato = h.nomeCampeonato,
                temporadaId    = h.temporadaId,
                tipo           = h.tipo,
                posicao        = h.posicao,
                vitorias       = h.vitorias,
                empates        = h.empates,
                derrotas       = h.derrotas,
                golsPro        = h.golsPro,
                golsContra     = h.golsContra,
                pontos         = h.pontos,
                jogos          = h.jogos
            )
        }

        // Histórico de Copas (grupo por campeonatoId, calcula W/D/L da sequência)
        val copaHistoricoPartidas = gameRepository.buscarHistoricoCopaDoTime(timeId)
        val copaEntries = copaHistoricoPartidas
            .groupBy { it.campeonatoId }
            .map { (campId, lista) ->
                var v = 0; var e = 0; var d = 0; var gp = 0; var gc = 0
                var faseAtingida: String? = null
                for (p in lista) {
                    val ehCasa = p.timeCasaId == timeId
                    val gf = if (ehCasa) p.golsCasa ?: 0 else p.golsFora ?: 0
                    val ga = if (ehCasa) p.golsFora ?: 0 else p.golsCasa ?: 0
                    gp += gf; gc += ga
                    when { gf > ga -> v++; gf < ga -> d++; else -> e++ }
                    if (p.fase != null) faseAtingida = p.fase
                }
                val first = lista.first()
                HistoricoTemporada(
                    campeonatoId   = campId,
                    nomeCampeonato = first.nomeCampeonato,
                    temporadaId    = first.temporadaId,
                    tipo           = "COPA_NACIONAL",
                    posicao        = 0,
                    vitorias       = v,
                    empates        = e,
                    derrotas       = d,
                    golsPro        = gp,
                    golsContra     = gc,
                    pontos         = 0,
                    jogos          = lista.size,
                    faseAtingida   = faseAtingida
                )
            }

        _historicoStats.value = (ligaEntries + copaEntries).sortedByDescending { it.temporadaId }

        _jogadoresHistorico.value = gameRepository.buscarEstatisticasJogadoresAllTime(timeId)

        // Notas médias do elenco na temporada atual
        jogadorRepository.observeElenco(timeId)
            .collect { lista ->
                _notasElenco.value = lista
                    .filter { it.partidasTemporada > 0 }
                    .sortedByDescending { it.notaMedia }
            }
    }
}

// ══════════════════════════════════════════════════════
//  FinancasViewModel
// ══════════════════════════════════════════════════════
@HiltViewModel
class FinancasViewModel @Inject constructor(
    private val gameDataStore: GameDataStore,
    private val jogadorRepository: JogadorRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    val saveState = gameDataStore.saveState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _elenco = MutableStateFlow<List<Jogador>>(emptyList())
    val elenco: StateFlow<List<Jogador>> = _elenco.asStateFlow()

    // Mês e ano derivados da progressão real do calendário de partidas,
    // igual à lógica do Dashboard (baseado em ordemGlobal da próxima partida).
    private val _mesAtual = MutableStateFlow(1)
    val mesAtual: StateFlow<Int> = _mesAtual.asStateFlow()

    private val _anoAtual = MutableStateFlow(2026)
    val anoAtual: StateFlow<Int> = _anoAtual.asStateFlow()

    fun carregar(timeId: Int) = viewModelScope.launch {
        // Carrega elenco de forma reativa
        launch {
            jogadorRepository.observeElenco(timeId)
                .collect { lista -> _elenco.value = lista.sortedByDescending { it.salario } }
        }
        // Carrega mês/ano a partir do calendário de partidas
        val save = gameDataStore.saveState.first()
        val proximaPartida = gameRepository.buscarProximaPartida(save.timeIdJogador)
        val ultimosResultados = gameRepository.buscarUltimosResultados(
            save.timeIdJogador,
            listOf(save.campeonatoId, save.copaId).filter { it > 0 }
        )
        val ordemRef = (proximaPartida ?: ultimosResultados.firstOrNull())?.ordemGlobal ?: 0
        _mesAtual.value = if (ordemRef > 0)
            (1 + (ordemRef.coerceAtLeast(1) - 1) * 11 / 379).coerceIn(1, 12)
        else save.mesAtual
        _anoAtual.value = save.anoAtual
    }
}

// ══════════════════════════════════════════════════════
//  EstadioViewModel
// ══════════════════════════════════════════════════════
@HiltViewModel
class EstadioViewModel @Inject constructor(
    private val estadioRepository: br.com.managerfoot.data.repository.EstadioRepository,
    private val timeRepository: TimeRepository
) : ViewModel() {

    private val _estadio = MutableStateFlow<br.com.managerfoot.data.database.entities.EstadioEntity?>(null)
    val estadio: StateFlow<br.com.managerfoot.data.database.entities.EstadioEntity?> = _estadio.asStateFlow()

    private val _time = MutableStateFlow<Time?>(null)
    val time: StateFlow<Time?> = _time.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    fun carregar(timeId: Int) = viewModelScope.launch {
        _estadio.value = estadioRepository.buscarOuCriar(timeId)
        _time.value = timeRepository.buscarPorId(timeId)
    }

    fun upgradeSetor(timeId: Int, setor: Int) = viewModelScope.launch {
        val ok = estadioRepository.upgradeSetor(timeId, setor)
        if (ok) {
            _estadio.value = estadioRepository.buscarOuCriar(timeId)
            _time.value = timeRepository.buscarPorId(timeId)
            _mensagem.value = "Upgrade realizado com sucesso!"
        } else {
            _mensagem.value = "Saldo insuficiente ou nível máximo atingido."
        }
    }

    fun limparMensagem() { _mensagem.value = null }
}

// ══════════════════════════════════════════════════════
//  JunioresViewModel
// ══════════════════════════════════════════════════════
@HiltViewModel
class JunioresViewModel @Inject constructor(
    private val jogadorRepository: br.com.managerfoot.data.repository.JogadorRepository
) : ViewModel() {

    private val _juniores = MutableStateFlow<List<br.com.managerfoot.domain.model.Jogador>>(emptyList())
    val juniores: StateFlow<List<br.com.managerfoot.domain.model.Jogador>> = _juniores.asStateFlow()

    private val _jogadorSelecionado = MutableStateFlow<br.com.managerfoot.domain.model.Jogador?>(null)
    val jogadorSelecionado: StateFlow<br.com.managerfoot.domain.model.Jogador?> = _jogadorSelecionado.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    fun carregar(timeId: Int) = viewModelScope.launch {
        jogadorRepository.observeJuniores(timeId).collect { _juniores.value = it }
    }

    fun selecionarJogador(jogador: br.com.managerfoot.domain.model.Jogador?) {
        _jogadorSelecionado.value = jogador
    }

    fun promoverJunior(jogadorId: Int, nomeAbrev: String) = viewModelScope.launch {
        jogadorRepository.promoverJunior(jogadorId)
        _mensagem.value = "$nomeAbrev promovido ao elenco principal!"
    }

    fun limparMensagem() { _mensagem.value = null }
}
