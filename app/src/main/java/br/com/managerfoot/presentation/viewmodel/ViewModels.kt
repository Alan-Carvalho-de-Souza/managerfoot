package br.com.managerfoot.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.managerfoot.data.dao.ArtilheiroDto
import br.com.managerfoot.data.dao.ConfrontoPartidaDto
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
            // Garante que o seed está no banco para popular a lista de seleção
            val seed = seedDataSource.carregar()
            timeDao.inserirTodos(seed.times)
            jogadorDao.inserirTodos(seed.jogadores)
            _uiState.value = InicioUiState.SelecionandoTime
        } catch (e: Exception) {
            _uiState.value = InicioUiState.Erro(e.message ?: "Erro ao carregar clubes")
        }
    }

    fun iniciarNovoJogo(timeId: Int) = viewModelScope.launch {
        _uiState.value = InicioUiState.Carregando
        try {
            // Limpa save anterior e recria com os dados do seed
            gameDataStore.resetar()
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
            val participantesA = (1..20).toList()
            val participantesB = (21..40).toList()

            val campeonatoAId = gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId = temporadaId,
                    nome = "Brasileirão Série A 2026",
                    tipo = TipoCampeonato.NACIONAL_DIVISAO1,
                    formato = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = 38
                ),
                participantesA
            )

            val campeonatoBId = gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId = temporadaId,
                    nome = "Brasileirão Série B 2026",
                    tipo = TipoCampeonato.NACIONAL_DIVISAO2,
                    formato = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = 38
                ),
                participantesB
            )

            // Determina em qual divisão o time escolhido está
            val timeEntity2 = timeDao.buscarPorId(timeId)
            val divJogador = timeEntity2?.divisao ?: 1
            val campeonatoIdJogador = if (divJogador == 1) campeonatoAId else campeonatoBId
            val campeonatoBIdParaSalvar = if (divJogador == 1) campeonatoBId else campeonatoAId

            gameDataStore.salvarNovoJogo(
                timeId = timeId,
                temporadaId = temporadaId,
                campeonatoId = campeonatoIdJogador,
                campeonatoBId = campeonatoBIdParaSalvar,
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

    // Escalação em uso durante a simulação (para o painel do intervalo)
    private val _escalacaoSimulacao = MutableStateFlow<Escalacao?>(null)
    val escalacaoSimulacao: StateFlow<Escalacao?> = _escalacaoSimulacao.asStateFlow()

    fun carregar(timeId: Int) = viewModelScope.launch {
        // Carrega todos os times para resolver nomes
        launch {
            timeRepository.observeTodos().collect { lista ->
                _todosOsTimes.value = lista
                _timeJogador.value = lista.find { it.id == timeId }
            }
        }
        // Últimos resultados: busca pontual — não reage a mudanças do banco
        // durante a simulação para evitar exibir o resultado antes da animação finalizar
        _ultimosResultados.value = gameRepository.buscarUltimosResultados(timeId)
        _proximaPartida.value = gameRepository.buscarProximaPartida(timeId)
        _uiState.value = DashboardUiState.Pronto
    }

    // Simula a partida do jogador e abre a tela de animação
    fun simularProximaPartida(campeonatoId: Int, rodada: Int) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        _proximaPartida.value ?: return@launch

        _uiState.value = DashboardUiState.Simulando

        try {
            // Carrega escalação para o painel do intervalo
            val elenco = jogadorRepository.buscarDisponiveis(save.timeIdJogador)
            val time = timeRepository.buscarPorId(save.timeIdJogador)
            if (time != null) {
                _escalacaoSimulacao.value = IATimeRival.gerarEscalacao(time, elenco)
            }

            // Simula a rodada inteira uma única vez: o mesmo resultado é persistido
            // no banco e exibido na animação, eliminando divergência de placar.
            val resultado = gameRepository.simularRodadaComJogador(
                campeonatoId = campeonatoId,
                rodada = rodada,
                timeJogadorId = save.timeIdJogador
            )
            _resultadoSimulado.value = resultado

            // Simula a mesma rodada na outra divisão (pura IA, sem jogador)
            val campeonatoBId = save.campeonatoBId
            if (campeonatoBId > 0) {
                gameRepository.simularRodada(campeonatoBId, rodada)
            }
        } catch (e: Exception) {
            // Ignora erro e volta para o estado normal
        }

        _proximaPartida.value = gameRepository.buscarProximaPartida(save.timeIdJogador)
        _uiState.value = DashboardUiState.Pronto
    }

    // Chamado quando o jogador fecha a tela de simulação
    fun fecharSimulacao() = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        // Atualiza últimos resultados só agora, após a animação ter sido exibida
        _ultimosResultados.value = gameRepository.buscarUltimosResultados(save.timeIdJogador)
        _resultadoSimulado.value = null
        _escalacaoSimulacao.value = null
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
                campeonatoAId = if (save.campeonatoId != -1) save.campeonatoId else save.campeonatoBId,
                campeonatoBId = if (save.campeonatoId != -1) save.campeonatoBId else save.campeonatoId,
                temporadaId  = save.temporadaId,
                ano          = save.anoAtual
            )
            // Determina em qual divisão o time do jogador estará na próxima temporada
            val time = timeRepository.buscarPorId(save.timeIdJogador)
            val divJogador = time?.divisao ?: 1
            val novoAId = novaInfo.campeonatoAId
            val novoBId = novaInfo.campeonatoBId
            val novoCampJogador = if (divJogador == 1) novoAId else novoBId
            val novoCampOutro   = if (divJogador == 1) novoBId else novoAId
            gameDataStore.salvarNovaTemporada(novoCampJogador, novoCampOutro, novaInfo.temporadaId, novaInfo.ano)
            val saveAtualizado = gameDataStore.saveState.first()
            _proximaPartida.value  = gameRepository.buscarProximaPartida(saveAtualizado.timeIdJogador)
            _ultimosResultados.value = gameRepository.buscarUltimosResultados(saveAtualizado.timeIdJogador)
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

    private var timeIdCarregado: Int = -1

    fun carregar(timeId: Int) = viewModelScope.launch {
        timeIdCarregado = timeId
        jogadorRepository.observeElenco(timeId).collect { lista ->
            _elenco.value = lista
            if (_escalacao.value == null) {
                val time = timeRepository.buscarPorId(timeId) ?: return@collect
                val escalacaoIA = IATimeRival.gerarEscalacao(time, lista)
                _escalacao.value = escalacaoIA
            }
        }
    }

    fun selecionarJogador(jogador: Jogador?) { _jogadorSelecionado.value = jogador }

    fun moverParaTitular(jogador: Jogador, posicao: Posicao) {
        val atual = _escalacao.value ?: return
        val novoTitular = JogadorNaEscalacao(jogador, posicao)
        val novasReservas = atual.reservas.filter { it.jogador.id != jogador.id }.toMutableList()
        val novosTitulares = atual.titulares.toMutableList()
        if (novosTitulares.size < 11) novosTitulares.add(novoTitular)
        _escalacao.value = atual.copy(titulares = novosTitulares, reservas = novasReservas)
    }

    fun moverParaReserva(jogador: Jogador) {
        val atual = _escalacao.value ?: return
        val novosTitulares = atual.titulares.filter { it.jogador.id != jogador.id }
        val novaReserva = JogadorNaEscalacao(jogador, jogador.posicao)
        val novasReservas = (atual.reservas + novaReserva).take(7)
        _escalacao.value = atual.copy(titulares = novosTitulares, reservas = novasReservas)
    }

    // Persiste a formação no banco e atualiza o Time em memória
    fun mudarFormacao(formacao: String) = viewModelScope.launch {
        val atual = _escalacao.value ?: return@launch
        val novoTime = atual.time.copy(taticaFormacao = formacao)
        _escalacao.value = atual.copy(time = novoTime)
        salvarTaticaNoBanco(novoTime)
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

    // IDs das duas divisões
    private var campeonatoAId: Int = -1
    private var campeonatoBId: Int = -1

    // Divisão selecionada em exibição (1 = Série A, 2 = Série B)
    private val _divisaoSelecionada = MutableStateFlow(1)
    val divisaoSelecionada: StateFlow<Int> = _divisaoSelecionada.asStateFlow()

    fun carregar(campAId: Int, campBId: Int, divisaoInicial: Int = 1) = viewModelScope.launch {
        campeonatoAId = campAId
        campeonatoBId = campBId
        _divisaoSelecionada.value = divisaoInicial
        launch { timeRepository.observeTodos().collect { _times.value = it } }
        coletarTabela(if (divisaoInicial == 1) campAId else campBId)
    }

    fun selecionarDivisao(divisao: Int) = viewModelScope.launch {
        if (_divisaoSelecionada.value == divisao) return@launch
        _divisaoSelecionada.value = divisao
        coletarTabela(if (divisao == 1) campeonatoAId else campeonatoBId)
    }

    private fun coletarTabela(campId: Int) = viewModelScope.launch {
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

    // IDs das duas divisões
    private var campeonatoAId: Int = -1
    private var campeonatoBId: Int = -1

    private val _divisaoSelecionada = MutableStateFlow(1)
    val divisaoSelecionada: StateFlow<Int> = _divisaoSelecionada.asStateFlow()

    fun carregar(campAId: Int, campBId: Int, divisaoInicial: Int = 1) = viewModelScope.launch {
        campeonatoAId = campAId
        campeonatoBId = campBId
        _divisaoSelecionada.value = divisaoInicial
        val campId = if (divisaoInicial == 1) campAId else campBId
        coletarArtilheiros(campId)
        launch { gameRepository.observarArtilheirosAllTime().collect { _artilheirosAllTime.value = it } }
        launch { gameRepository.observarAssistentesAllTime().collect { _assistentesAllTime.value = it } }
    }

    fun selecionarDivisao(divisao: Int) = viewModelScope.launch {
        if (_divisaoSelecionada.value == divisao) return@launch
        _divisaoSelecionada.value = divisao
        coletarArtilheiros(if (divisao == 1) campeonatoAId else campeonatoBId)
    }

    private fun coletarArtilheiros(campId: Int) = viewModelScope.launch {
        launch { gameRepository.observarArtilheiros(campId).collect { _artilheiros.value = it } }
        launch { gameRepository.observarAssistentes(campId).collect { _assistentes.value = it } }
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

    private val _divisaoSelecionada = MutableStateFlow(0)  // 0=todas, 1=Série A, 2=Série B
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