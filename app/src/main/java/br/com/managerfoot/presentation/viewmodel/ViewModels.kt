package br.com.managerfoot.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.managerfoot.data.dao.ArtilheiroDto
import br.com.managerfoot.data.dao.CalendarioPartidaDto
import br.com.managerfoot.data.dao.ConfrontoPartidaDto
import br.com.managerfoot.data.dao.CopaPartidaDto
import br.com.managerfoot.data.dao.PartidaDao
import br.com.managerfoot.data.dao.RankingBrasileiroLinha
import br.com.managerfoot.data.dao.RankingConmebolLinha
import br.com.managerfoot.data.dao.RankingEuropaLinha
import br.com.managerfoot.data.dao.TransferenciaDetalhe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import br.com.managerfoot.data.database.entities.*
import br.com.managerfoot.data.datasource.GameDataStore
import br.com.managerfoot.data.datasource.SeedDataSource
import br.com.managerfoot.data.repository.*
import br.com.managerfoot.domain.engine.*
import br.com.managerfoot.domain.model.*
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Converte um ordemGlobal no mês do calendário (1=Jan … 12=Dez).
 * Usa a mesma fórmula canônica de CalendarioScreen:
 *   dia-do-ano = 39 + (OG-10)*295/370  (fev 8 = dia 39, nov 30 = dia 334)
 * Caso especial: OG=1 = Supercopa Rei em janeiro.
 */
private fun ordemGlobalParaMes(ordemGlobal: Int): Int {
    if (ordemGlobal <= 1) return 1
    val diasMes = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    val diaDoAno = (39 + (ordemGlobal - 10) * 295 / 370).coerceIn(1, 365)
    var rem = diaDoAno
    for (m in 1..12) { if (rem <= diasMes[m]) return m; rem -= diasMes[m] }
    return 12
}

// ═══════════════════════════════════════════════════════════
//  InicioViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class InicioViewModel @Inject constructor(
    private val gameDataStore: GameDataStore,
    private val timeRepository: TimeRepository,
    private val jogadorRepository: JogadorRepository,
    private val gameRepository: GameRepository,
    private val tecnicoRepository: TecnicoRepository,
    private val seedDataSource: SeedDataSource,
    private val timeDao: br.com.managerfoot.data.dao.TimeDao,
    private val jogadorDao: br.com.managerfoot.data.dao.JogadorDao
) : ViewModel() {

    val saveState = gameDataStore.saveState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
    )

    /**
     * Todos os clubes do banco. E uma fonte de CONSULTA por id — o clube do save
     * e o que o jogador acabou de escolher —, nao a lista de escolha. Para
     * escolher use [timesSelecionaveis].
     */
    val timesDisponiveis: StateFlow<List<Time>> = timeRepository.observeTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Os clubes que o jogador pode DIRIGIR: os das ligas modeladas no jogo.
     *
     * Fica de fora o pool (divisoes 90/91/92), que existe so para as competicoes
     * continentais e nao tem liga nacional. Esses clubes nao sao dirigiveis: sem
     * campeonato proprio, `iniciarNovoJogo` cairia no `else` do roteamento de
     * divisao e poria o jogador a dirigir, por exemplo, o Colo-Colo DENTRO do
     * Apertura argentino, um torneio do qual ele nao participa.
     *
     * A filtragem e so aqui, e nao em [timesDisponiveis], porque aquela lista e
     * usada para achar o clube do save pelo id: um save feito antes desta correcao
     * pode ter um clube do pool, e filtrar na fonte deixaria o card de continuar
     * sem clube nenhum.
     */
    val timesSelecionaveis: StateFlow<List<Time>> = timeRepository.observeTodos()
        .map { times -> times.filter { it.divisao !in RankingEuropa.DIVISOES_POOL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow<InicioUiState>(InicioUiState.Carregando)
    val uiState: StateFlow<InicioUiState> = _uiState.asStateFlow()

    init { verificarSave() }

    private fun verificarSave() = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        // Backfill de escudos para bancos populados por uma versão do seed
        // anterior aos escudos de uma liga (ex.: Premier). Idempotente e barato:
        // só preenche escudos vazios de times já existentes, sem tocar no jogo.
        try {
            val seed = seedDataSource.carregar()
            gameRepository.sincronizarEscudosDoSeed(seed.times)
        } catch (e: Exception) {
            Log.e("InicioViewModel", "backfill de escudos falhou", e)
        }
        _uiState.value = if (save.jogoInicializado) InicioUiState.TemSave
        else InicioUiState.SemSave
    }

    // Carrega os times no banco (se necessário) e abre a tela de seleção de clube
    fun iniciarSelecionarTime() = viewModelScope.launch {
        _uiState.value = InicioUiState.Carregando
        try {
            // Verifica se o seed já está íntegro (times E jogadores presentes).
            // Isso detecta o estado corrompido de crash entre as duas inserções.
            val seed = seedDataSource.carregar()
            if (!gameRepository.seedEstaIntegro()) {
                gameRepository.inserirSeedTransacional(seed.times, seed.jogadores)
            } else {
                // Banco já íntegro, mas pode ter sido populado por uma versão do seed
                // anterior a uma liga nova (ex.: Premier). Adiciona de forma aditiva
                // os times/jogadores que faltam, sem tocar em jogos em andamento.
                gameRepository.inserirSeedFaltante(seed.times, seed.jogadores)
            }
            // Preenche escudos que passaram a existir no seed para times já no banco.
            gameRepository.sincronizarEscudosDoSeed(seed.times)
            _uiState.value = InicioUiState.SelecionandoTime
        } catch (e: Exception) {
            Log.e("InicioViewModel", "iniciarSelecionarTime falhou", e)
            _uiState.value = InicioUiState.Erro("${e::class.simpleName}: ${e.message ?: "Erro ao carregar clubes"}")
        }
    }

    /** Após o usuário escolher o time, transita para o estado de prompt
     *  de nome do técnico (sem rodar a inicialização completa ainda). */
    fun timeEscolhido(timeId: Int) {
        _uiState.value = InicioUiState.PerguntandoNomeTecnico(timeId)
    }

    fun voltarParaSelecionarTime() {
        _uiState.value = InicioUiState.SelecionandoTime
    }

    fun iniciarNovoJogo(
        timeId: Int,
        nomeTecnico: String = "Técnico",
        nacionalidadeTecnico: String = "Brasil"
    ) = viewModelScope.launch {
        _uiState.value = InicioUiState.Carregando
        try {
            // Limpa TODOS os dados do jogo atual (campeonatos, partidas, times, jogadores, etc.)
            gameDataStore.resetar()
            gameRepository.limparTodosDados()
            timeDao.deleteAll()
            jogadorDao.deleteAll()

            // Insere dados frescos do seed em uma única transação atômica (times+jogadores+técnicos)
            val seed = seedDataSource.carregar()
            gameRepository.inserirSeedTransacional(seed.times, seed.jogadores, seed.tecnicos)

            // Marca apenas o time escolhido como controlado pelo jogador
            val timeEntity = timeDao.buscarPorId(timeId) ?: run {
                _uiState.value = InicioUiState.Erro("Time não encontrado")
                return@launch
            }
            timeDao.atualizar(timeEntity.copy(controladoPorJogador = true))

            // Cadastra o técnico do usuário no clube escolhido. O técnico que
            // estava nesse clube no seed é demitido (vira free agent).
            tecnicoRepository.cadastrarTecnicoUsuario(
                nome = nomeTecnico,
                nacionalidade = nacionalidadeTecnico,
                timeId = timeId,
                anoInicial = 2026
            )

            // Cria a passagem ativa inicial dos técnicos da IA já empregados, para
            // que o Ranking/Histórico exiba suas passagens desde os primeiros jogos
            // (o técnico do usuário já ganhou a sua acima; este passo o ignora).
            tecnicoRepository.criarPassagensIniciaisEmClubes(anoInicial = 2026)

            // Ranking CONMEBOL: semeia o coeficiente histórico (1960-2014) dos
            // clubes sul-americanos a partir da reputação do seed, para o ranking
            // já nascer com a hierarquia tradicional do continente.
            gameRepository.semearCoeficienteHistoricoConmebol()

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

            // Cria Copa do Brasil: top 64 por reputação (somente times brasileiros)
            val todos = timeDao.observeTodos().first()
            val copaParticipantes = todos.filter { it.pais == "Brasil" }
                .sortedByDescending { it.reputacao }.take(64).map { it.id }
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

            // Cria Supercopa Rei 2026: edição inaugural com Flamengo (campeão da Copa) vs
            // Corinthians (campeão do Brasileirão) — seeds fixos para a primeira temporada.
            val supercopaId = gameRepository.criarSupercopa(
                campeonatoBrasileiraoId = 5,  // Corinthians
                campeonatoCopaId        = 1,  // Flamengo
                ano                     = 2026,
                temporadaId             = temporadaId
            )

            val participantesArgA = timeDao.observePorDivisao(5).first().map { it.id }
            val campeonatoArgAId = if (participantesArgA.isNotEmpty()) gameRepository.criarArgentinaTorneio(
                participantesArgA, temporadaId,
                "Primera División Argentina Apertura 2026", ogOffset = 0
            ) else -1
            val campeonatoArgClausuraId = if (participantesArgA.isNotEmpty()) gameRepository.criarArgentinaTorneio(
                participantesArgA, temporadaId,
                "Primera División Argentina Clausura 2026",
                ogOffset = br.com.managerfoot.domain.engine.MotorCampeonato.ARG_CLAUSURA_OG_OFFSET
            ) else -1

            val participantesArgB = timeDao.observePorDivisao(6).first().map { it.id }
            val campeonatoArgBId = if (participantesArgB.isNotEmpty()) gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId = temporadaId,
                    nome = "Segunda División Argentina 2026",
                    tipo = TipoCampeonato.EXTRANGEIRO_DIVISAO2,
                    formato = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = (participantesArgB.size - 1) * 2,
                    pais = "Argentina"
                ),
                participantesArgB
            ) else -1

            // Cria Copa Argentina: todos da Primera División + top 2 da Segunda
            val participantesCopaArg = gameRepository.determinarParticipantesCopaArgentina(
                campeonatoArgAId, campeonatoArgBId
            )
            val copaArgId = if (participantesCopaArg.isNotEmpty()) gameRepository.criarCopaArgentina(
                CampeonatoEntity(
                    temporadaId  = temporadaId,
                    nome         = "Copa Argentina 2026",
                    tipo         = TipoCampeonato.COPA_NACIONAL,
                    formato      = FormatoCampeonato.MATA_MATA_IDA_VOLTA,
                    totalRodadas = 10,
                    pais         = "Argentina"
                ),
                participantesCopaArg
            ) else -1

            // ── Campeonato Uruguaio ──────────────────────────────────────────────
            val participantesUruA = timeDao.observePorDivisao(9).first().map { it.id }
            val campeonatoUruAperturaId = if (participantesUruA.isNotEmpty())
                gameRepository.criarUruguaiApertura(participantesUruA, temporadaId, 2026) else -1
            val campeonatoUruIntermedId = if (participantesUruA.isNotEmpty())
                gameRepository.criarUruguaiIntermediario(participantesUruA, temporadaId, 2026) else -1
            val campeonatoUruClausuraId = if (campeonatoUruAperturaId > 0)
                gameRepository.criarUruguaiClausura(campeonatoUruAperturaId, participantesUruA, temporadaId, 2026) else -1

            val participantesUruB = timeDao.observePorDivisao(10).first().map { it.id }
            val campeonatoUruBId = if (participantesUruB.isNotEmpty()) gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId  = temporadaId,
                    nome         = "Segunda División Uruguaia 2026",
                    tipo         = TipoCampeonato.EXTRANGEIRO_DIVISAO2,
                    formato      = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = (participantesUruB.size - 1) * 2,
                    pais         = "Uruguay"
                ), participantesUruB
            ) else -1
            val campeonatoUruBCompetId = if (participantesUruB.size >= 2)
                gameRepository.criarUruguaiSegundaDivCompetencia(participantesUruB, temporadaId, 2026) else -1

            // ── Premier League (Inglaterra, divisao 11) — pontos corridos ────────
            val participantesPremier = timeDao.observePorDivisao(11).first().map { it.id }
            val campeonatoPremierId = if (participantesPremier.isNotEmpty()) gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId  = temporadaId,
                    nome         = "Premier League 2026/27",
                    tipo         = TipoCampeonato.NACIONAL_DIVISAO1,
                    formato      = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = (participantesPremier.size - 1) * 2,
                    pais         = "Inglaterra"
                ),
                participantesPremier
            ) else -1

            // ── EFL Championship (Inglaterra, divisao 12) — pontos corridos ──────
            val participantesChampionship = timeDao.observePorDivisao(12).first().map { it.id }
            val campeonatoChampionshipId = if (participantesChampionship.isNotEmpty()) gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId  = temporadaId,
                    nome         = "EFL Championship 2026/27",
                    tipo         = TipoCampeonato.NACIONAL_DIVISAO2,
                    formato      = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = (participantesChampionship.size - 1) * 2,
                    pais         = "Inglaterra"
                ),
                participantesChampionship
            ) else -1

            // ── La Liga (Espanha, divisao 13) — pontos corridos ─────────────────
            val participantesLaLiga = timeDao.observePorDivisao(13).first().map { it.id }
            val campeonatoLaLigaId = if (participantesLaLiga.isNotEmpty()) gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId  = temporadaId,
                    nome         = "La Liga 2026/27",
                    tipo         = TipoCampeonato.NACIONAL_DIVISAO1,
                    formato      = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = (participantesLaLiga.size - 1) * 2,
                    pais         = "Espanha"
                ),
                participantesLaLiga
            ) else -1

            // ── Serie A (Italia, divisao 14) — pontos corridos ──────────────────
            val participantesSerieA = timeDao.observePorDivisao(14).first().map { it.id }
            val campeonatoSerieAId = if (participantesSerieA.isNotEmpty()) gameRepository.criarCampeonato(
                CampeonatoEntity(
                    temporadaId  = temporadaId,
                    nome         = "Serie A 2026/27",
                    tipo         = TipoCampeonato.NACIONAL_DIVISAO1,
                    formato      = FormatoCampeonato.PONTOS_CORRIDOS,
                    totalRodadas = (participantesSerieA.size - 1) * 2,
                    pais         = "Italia"
                ),
                participantesSerieA
            ) else -1

            // ── Copa da Inglaterra (FA Cup) — 32 times, jogo único + pênaltis ────
            val participantesCopaIng = gameRepository.determinarParticipantesCopaInglaterra(
                campeonatoPremierId, campeonatoChampionshipId
            )
            val copaInglaterraId = if (participantesCopaIng.size >= 2) gameRepository.criarCopaInglaterra(
                CampeonatoEntity(
                    temporadaId  = temporadaId,
                    nome         = "Copa da Inglaterra 2026",
                    tipo         = TipoCampeonato.COPA_NACIONAL,
                    formato      = FormatoCampeonato.MATA_MATA_SIMPLES,
                    totalRodadas = 5,
                    pais         = "Inglaterra"
                ),
                participantesCopaIng
            ) else -1

            // ── Copa Espanhola (Copa do Rei) e Copa Italiana (Coppa Italia) — 16 melhores,
            //    ida/volta a partir das Oitavas (modelo Copa do Brasil) ──
            val participantesCopaEsp = gameRepository.determinarParticipantesCopaEspanhola(campeonatoLaLigaId)
            val copaEspanholaId = if (participantesCopaEsp.size >= 2) gameRepository.criarCopa16(
                CampeonatoEntity(
                    temporadaId  = temporadaId,
                    nome         = "Copa Espanhola 2026",
                    tipo         = TipoCampeonato.COPA_NACIONAL,
                    formato      = FormatoCampeonato.MATA_MATA_IDA_VOLTA,
                    totalRodadas = 8,
                    pais         = "Espanha"
                ),
                participantesCopaEsp
            ) else -1

            val participantesCopaIta = gameRepository.determinarParticipantesCopaItaliana(campeonatoSerieAId)
            val copaItalianaId = if (participantesCopaIta.size >= 2) gameRepository.criarCopa16(
                CampeonatoEntity(
                    temporadaId  = temporadaId,
                    nome         = "Copa Italiana 2026",
                    tipo         = TipoCampeonato.COPA_NACIONAL,
                    formato      = FormatoCampeonato.MATA_MATA_IDA_VOLTA,
                    totalRodadas = 8,
                    pais         = "Italia"
                ),
                participantesCopaIta
            ) else -1

            // ── UEFA Champions League — Fase de Liga de 36 + mata-mata ──────────
            // Devolve -1 enquanto o jogo não tiver 36 clubes europeus elegíveis
            // (as travas geográficas do sorteio exigem no mínimo 5 países).
            val championsId = gameRepository.criarChampionsDaTemporada(temporadaId, 2026)

            // ── UEFA Europa League — montada DEPOIS da Champions ────────────────
            // Os clubes da Champions entram como ocupados: um clube não disputa as
            // duas competições (ver europa_league.md).
            val europaLeagueId = gameRepository.criarEuropaDaTemporada(
                temporadaId = temporadaId,
                ano         = 2026,
                championsId = championsId
            )

            // ── CONMEBOL Libertadores — Pré-Libertadores + 8 grupos de 4 ────────
            // Devolve -1 enquanto o jogo não tiver os 47 clubes sul-americanos
            // (19 na Pré, 26 diretos e as 2 vagas de campeão vigente).
            val libertadoresId = gameRepository.criarLibertadoresDaTemporada(
                temporadaId, 2026, timeJogadorId = timeId
            )

            // Determina em qual divisão o time escolhido está
            val timeEntity2 = timeDao.buscarPorId(timeId)
            val divJogador = timeEntity2?.divisao ?: 1
            val campeonatoIdJogador = when (divJogador) {
                1 -> campeonatoAId; 2 -> campeonatoBId; 3 -> campeonatoCId; 4 -> campeonatoDId
                5 -> campeonatoArgAId
                6 -> campeonatoArgBId
                9 -> campeonatoUruAperturaId
                10 -> campeonatoUruBId
                11 -> campeonatoPremierId
                12 -> campeonatoChampionshipId
                13 -> campeonatoLaLigaId
                14 -> campeonatoSerieAId
                else -> campeonatoArgAId
            }

            gameDataStore.salvarNovoJogo(
                timeId = timeId,
                temporadaId = temporadaId,
                campeonatoId = campeonatoIdJogador,
                campeonatoAId = campeonatoAId,
                campeonatoBId = campeonatoBId,
                campeonatoCId = campeonatoCId,
                campeonatoDId = campeonatoDId,
                campeonatoArgAId = campeonatoArgAId,
                campeonatoArgBId = campeonatoArgBId,
                campeonatoArgClausuraId = campeonatoArgClausuraId,
                campeonatoUruAperturaId = campeonatoUruAperturaId,
                campeonatoUruClausuraId = campeonatoUruClausuraId,
                campeonatoUruIntermedId = campeonatoUruIntermedId,
                campeonatoUruBId = campeonatoUruBId,
                campeonatoUruBCompetId = campeonatoUruBCompetId,
                campeonatoPremierId = campeonatoPremierId,
                campeonatoChampionshipId = campeonatoChampionshipId,
                campeonatoLaLigaId = campeonatoLaLigaId,
                campeonatoSerieAId = campeonatoSerieAId,
                copaId = copaId,
                copaArgId = copaArgId,
                copaInglaterraId = copaInglaterraId,
                copaEspanholaId = copaEspanholaId,
                copaItalianaId = copaItalianaId,
                championsId = championsId,
                europaLeagueId = europaLeagueId,
                libertadoresId = libertadoresId,
                supercopaId = supercopaId,
                ano = 2026
            )
            _uiState.value = InicioUiState.JogoIniciado(timeId)
        } catch (e: Exception) {
            Log.e("InicioViewModel", "iniciarNovoJogo falhou", e)
            _uiState.value = InicioUiState.Erro("${e::class.simpleName}: ${e.message ?: "Erro desconhecido"}")
        }
    }

    fun continuarJogo() = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        if (save.jogoInicializado) {
            // Saves criados antes do Ranking CONMEBOL não têm o coeficiente histórico,
            // o que deixaria a aba Colocação achatada para sempre. A semeadura é
            // idempotente, então aqui ela só age na primeira vez.
            gameRepository.semearCoeficienteHistoricoConmebol()
            _uiState.value = InicioUiState.JogoIniciado(save.timeIdJogador)
        }
    }
}

sealed class InicioUiState {
    object Carregando       : InicioUiState()
    object SemSave          : InicioUiState()
    object TemSave          : InicioUiState()
    object SelecionandoTime : InicioUiState()
    /** Após o usuário escolher o time, perguntar nome + nacionalidade do técnico. */
    data class PerguntandoNomeTecnico(val timeId: Int) : InicioUiState()
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
    private val gameRepository: GameRepository,
    private val tecnicoRepository: TecnicoRepository
) : ViewModel() {

    val saveState = gameDataStore.saveState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** true quando o jogo foi inicializado mas o patrocinador ainda não foi escolhido */
    val precisaEscolherPatrocinador: StateFlow<Boolean> = gameDataStore.saveState
        .map { save -> save.jogoInicializado && save.patrocinadorValorAnual == 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _timeJogador = MutableStateFlow<Time?>(null)
    val timeJogador: StateFlow<Time?> = _timeJogador.asStateFlow()

    /** Meta de temporada do clube do jogador, derivada de divisão + reputação. */
    val metaTemporada: StateFlow<br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada?> =
        _timeJogador
            .map { time -> time?.let { br.com.managerfoot.domain.engine.MotorFinanceiro.calcularMetaTemporada(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Técnico do clube do jogador (controlado pelo jogador). Atualiza quando o time muda. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val tecnicoJogador: StateFlow<br.com.managerfoot.data.database.entities.TecnicoEntity?> =
        gameDataStore.saveState
            .filter { it.timeIdJogador > 0 }
            .flatMapLatest { save -> tecnicoRepository.observePorTime(save.timeIdJogador) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Todos os times para resolver nomes nas telas
    private val _todosOsTimes = MutableStateFlow<List<Time>>(emptyList())
    val todosOsTimes: StateFlow<List<Time>> = _todosOsTimes.asStateFlow()

    private val _proximaPartida = MutableStateFlow<PartidaEntity?>(null)
    val proximaPartida: StateFlow<PartidaEntity?> = _proximaPartida.asStateFlow()

    private val _ultimosResultados = MutableStateFlow<List<PartidaEntity>>(emptyList())
    val ultimosResultados: StateFlow<List<PartidaEntity>> = _ultimosResultados.asStateFlow()

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Carregando)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Mensagens transitórias (erros/avisos) para exibição em Snackbar na Dashboard.
    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()
    fun limparMensagem() { _mensagem.value = null }

    // ── Amistosos de entressafra ─────────────────────────────────────────────
    // Flag: a partida em simulação é um amistoso (roteia a finalização leve).
    @Volatile private var _ehAmistoso = false
    @Volatile private var _amistosoEhJaneiro = false

    /** Estado da janela de amistosos (dez/jan) para a UI. */
    data class AmistosoJanela(val ativa: Boolean, val ehJaneiro: Boolean, val restantes: Int)
    val amistosoJanela: StateFlow<AmistosoJanela> =
        combine(gameDataStore.saveState, _proximaPartidaFlow()) { save, prox ->
            if (!save.jogoInicializado) AmistosoJanela(false, false, 0)
            else {
                val janeiro = save.amistosoJaneiroAtivo
                val dezembro = prox == null && !janeiro
                when {
                    janeiro && save.amistososJan < 2  -> AmistosoJanela(true, true, 2 - save.amistososJan)
                    dezembro && save.amistososDez < 2 -> AmistosoJanela(true, false, 2 - save.amistososDez)
                    else -> AmistosoJanela(false, janeiro, 0)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AmistosoJanela(false, false, 0))

    private fun _proximaPartidaFlow() = _proximaPartida.asStateFlow()

    /** Marca o onboarding como concluído (chamado ao fechar/pular o tutorial). */
    fun concluirOnboarding() = viewModelScope.launch {
        gameDataStore.marcarOnboardingConcluido()
    }

    /** Reabilita o tutorial (para o botão "Ver tutorial de novo" no menu Mais). */
    fun reiniciarOnboarding() = viewModelScope.launch {
        gameDataStore.resetarOnboarding()
    }

    // Resultado da última simulação — quando não nulo, abre a tela de simulação
    private val _resultadoSimulado = MutableStateFlow<ResultadoPartida?>(null)
    val resultadoSimulado: StateFlow<ResultadoPartida?> = _resultadoSimulado.asStateFlow()

    // Outros jogos da MESMA rodada (pré-simulados) — para o painel "Placares ao vivo".
    private val _outrosJogosRodada = MutableStateFlow<List<GameRepository.JogoRodadaAoVivo>>(emptyList())
    val outrosJogosRodada: StateFlow<List<GameRepository.JogoRodadaAoVivo>> = _outrosJogosRodada.asStateFlow()

    // Contexto entre metades - armazena estado do 1o tempo para continuar no 2o
    @Volatile private var _contextoSimulacao: ContextoSimulacaoMetade? = null

    // Escalação em uso durante a simulação (para o painel do intervalo)
    private val _escalacaoSimulacao = MutableStateFlow<Escalacao?>(null)
    val escalacaoSimulacao: StateFlow<Escalacao?> = _escalacaoSimulacao.asStateFlow()

    // Escalação do adversário durante a simulação (para visualização)
    private val _escalacaoAdversarioSimulacao = MutableStateFlow<Escalacao?>(null)
    val escalacaoAdversarioSimulacao: StateFlow<Escalacao?> = _escalacaoAdversarioSimulacao.asStateFlow()

    // Indica que os pênaltis pendentes são da Supercopa (não da Copa do Brasil)
    private var _penaltisParaSupercopa = false
    // Qual supercopa (Brasil/Inglaterra/Espanha/Itália) está em disputa de pênaltis
    private var _supercopaAtivaId = -1

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

    // ── Notificações ─────────────────────────────────────────────────────────
    // Contador combinado (propostas de transferência + propostas de clube)
    val notificacoesContador: StateFlow<Int> = combine(
        gameRepository.observarContadorNotificacoes(),
        gameRepository.observarContadorPropostasClube()
    ) { transf, clube -> transf + clube }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val notificacoes: StateFlow<List<br.com.managerfoot.data.database.entities.PropostaIAEntity>> =
        gameRepository.observarNotificacoes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val propostasClube: StateFlow<List<br.com.managerfoot.data.database.entities.PropostaClubeEntity>> =
        gameRepository.observarPropostasClube()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _infoPropostaClubeSelecionada = MutableStateFlow<InfoPropostaClube?>(null)
    val infoPropostaClubeSelecionada: StateFlow<InfoPropostaClube?> = _infoPropostaClubeSelecionada.asStateFlow()

    fun marcarNotificacaoLida(id: Int) = viewModelScope.launch {
        gameRepository.marcarNotificacaoLida(id)
    }

    fun marcarTodasNotificacoesLidas() = viewModelScope.launch {
        gameRepository.marcarTodasNotificacoesLidas()
        gameRepository.marcarTodasPropostasClubeLidas()
    }

    fun carregarInfoPropostaClube(timeOfertanteId: Int) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        _infoPropostaClubeSelecionada.value =
            gameRepository.buscarInfoPropostaClube(timeOfertanteId, save.temporadaId)
    }

    fun fecharInfoPropostaClube() {
        _infoPropostaClubeSelecionada.value = null
    }

    fun recusarPropostaClube(id: Int) = viewModelScope.launch {
        gameRepository.recusarPropostaClube(id)
        _infoPropostaClubeSelecionada.value = null
    }

    fun marcarPropostaClubeComoLida(id: Int) = viewModelScope.launch {
        gameRepository.marcarPropostaClubeComoLida(id)
    }

    fun aceitarPropostaClube(propostaId: Int, novoTimeId: Int) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        gameRepository.aceitarPropostaClube(propostaId, save.timeIdJogador, novoTimeId, save.anoAtual)
        val novaDiv = gameRepository.buscarInfoPropostaClube(novoTimeId, save.temporadaId)?.divisao ?: 1
        val novoCampId = when (novaDiv) {
            1 -> save.campeonatoAId; 2 -> save.campeonatoBId
            3 -> save.campeonatoCId; 4 -> save.campeonatoDId
            5 -> save.campeonatoArgAId
            6 -> save.campeonatoArgBId
            9 -> save.campeonatoUruAperturaId
            10 -> save.campeonatoUruBId
            11 -> save.campeonatoPremierId
            12 -> save.campeonatoChampionshipId
            13 -> save.campeonatoLaLigaId
            14 -> save.campeonatoSerieAId
            else -> save.campeonatoAId
        }
        gameDataStore.salvarTimeDoJogador(novoTimeId, novoCampId)
        _infoPropostaClubeSelecionada.value = null
    }

    fun carregar(timeId: Int) = viewModelScope.launch {
        // Migração única: corrige ordemGlobal de partidas da Copa do Brasil com valores legados
        gameRepository.migrarOrdemGlobalCopaBrasil()
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
            timeId, listOf(campeonatoId, copaIdInicial, save.copaArgId, save.copaInglaterraId, save.supercopaId, save.campeonatoArgClausuraId, save.championsId, save.europaLeagueId, save.libertadoresId, save.campeonatoAmistososId).filter { it > 0 }
        )
        _proximaPartida.value = gameRepository.buscarProximaPartida(timeId)
        _posicaoNaTabela.value = gameRepository.buscarPosicaoNaTabela(campeonatoId, timeId)
        _uiState.value = DashboardUiState.Pronto
    }

    // Simula a partida do jogador e abre a tela de animação
    fun simularProximaPartida(campeonatoId: Int, rodada: Int) = viewModelScope.launch(Dispatchers.Default) {
        val save = gameDataStore.saveState.first()
        _proximaPartida.value ?: return@launch

        _uiState.value = DashboardUiState.Simulando

        try {
            // Carrega escalacao salva pelo jogador (ou cai no IA se nao houver)
            val escalacaoJogador = gameRepository.gerarEscalacaoJogador(save.timeIdJogador)
            _escalacaoSimulacao.value = escalacaoJogador

            // Simula apenas o 1o tempo; o 2o sera simulado apos o intervalo
            val inicio = gameRepository.iniciarPartidaComJogador(
                campeonatoId     = campeonatoId,
                rodada           = rodada,
                timeJogadorId    = save.timeIdJogador,
                escalacaoJogador = escalacaoJogador
            )
            _contextoSimulacao = inicio.contexto
            _escalacaoAdversarioSimulacao.value = inicio.contexto.escalacaoAdversario
            _resultadoSimulado.value = inicio.resultadoParcial
            _outrosJogosRodada.value = inicio.outrosJogos
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "simularProximaPartida falhou", e)
            _mensagem.value = "Não foi possível simular a partida. Tente novamente."
        }

        _uiState.value = DashboardUiState.Pronto
    }

    /** Inicia um amistoso de entressafra (jogado como partida normal, interativo). */
    fun iniciarAmistoso(adversarioId: Int, ehCasa: Boolean) = viewModelScope.launch(Dispatchers.Default) {
        val save = gameDataStore.saveState.first()
        if (save.timeIdJogador <= 0) return@launch
        _uiState.value = DashboardUiState.Simulando
        try {
            val campId = gameRepository.criarOuObterCampeonatoAmistosos(save.campeonatoAmistososId, save.temporadaId, save.anoAtual)
            if (campId != save.campeonatoAmistososId) gameDataStore.salvarAmistosoCampId(campId)
            // ordemGlobal: janeiro ~5 (antes da 1ª rodada, envelhece cedo); dezembro ~450 (após todos os oficiais)
            val ogBase = if (save.amistosoJaneiroAtivo) 5 else 450
            val (_, rodada) = gameRepository.criarAmistoso(campId, save.timeIdJogador, adversarioId, ehCasa, ogBase)
            val escalacaoJogador = gameRepository.gerarEscalacaoJogador(save.timeIdJogador)
            _escalacaoSimulacao.value = escalacaoJogador
            val inicio = gameRepository.iniciarPartidaComJogador(
                campeonatoId     = campId,
                rodada           = rodada,
                timeJogadorId    = save.timeIdJogador,
                escalacaoJogador = escalacaoJogador
            )
            _contextoSimulacao = inicio.contexto
            _escalacaoAdversarioSimulacao.value = inicio.contexto.escalacaoAdversario
            _resultadoSimulado.value = inicio.resultadoParcial
            _outrosJogosRodada.value = inicio.outrosJogos   // amistoso: vazio (fase != null)
            _ehAmistoso = true
            _amistosoEhJaneiro = save.amistosoJaneiroAtivo
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "iniciarAmistoso falhou", e)
            _mensagem.value = "Não foi possível iniciar o amistoso."
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
        val (ctxAtualizado, eventos) = withContext(Dispatchers.Default) {
            gameRepository.simularPeriodoComJogador(
                ctx              = ctx,
                titularesJogador = titularesJogador,
                reservasJogador  = reservasJogador,
                substituicoes    = substituicoes,
                formacao         = formacao,
                estilo           = estilo,
                minInicio        = minInicio,
                minFim           = minFim
            )
        }
        _contextoSimulacao = ctxAtualizado
        return eventos
    }

    // Chamado pela tela de simulacao apos animacao do 2o tempo para finalizar e persistir.
    // Persiste o resultado core de forma síncrona (libera a tela imediatamente) e lança
    // as operações secundárias (Copa, Argentina, Uruguai) em background via viewModelScope.
    suspend fun finalizarPartidaSimulada(golsCasa: Int, golsFora: Int) {
        val ctx = _contextoSimulacao ?: return
        // Libera o contexto imediatamente — não é mais necessário após captura local
        _contextoSimulacao = null

        // ── Amistoso: finalização LEVE (sem tabela/copa/mês) ────────────────────
        if (_ehAmistoso) {
            try {
                val resultadoFinal = withContext(Dispatchers.Default) {
                    gameRepository.finalizarAmistosoComJogador(ctx, golsCasa, golsFora)
                }
                _resultadoSimulado.value = resultadoFinal
                gameDataStore.registrarAmistosoJogado(_amistosoEhJaneiro)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "finalizarAmistoso falhou", e)
                _mensagem.value = "Falha ao finalizar o amistoso."
            }
            return
        }

        try {
            val save = gameDataStore.saveState.first()
            // Ao jogar o 1º jogo OFICIAL da temporada, fecha a janela de amistosos de janeiro.
            if (save.amistosoJaneiroAtivo) {
                try { gameDataStore.fecharJanelaAmistosoJaneiro() } catch (_: Exception) {}
            }
            // B/C/D avançam APENAS quando o jogador joga na sua própria liga (nunca em Copa).
            // Isso garante sincronismo exato: 1 rodada da liga do jogador = 1 rodada nos outros campeonatos.
            val ehJogoDaLiga = ctx.campeonatoId == save.campeonatoId ||
                               ctx.campeonatoId == save.campeonatoArgClausuraId ||
                               ctx.campeonatoId == save.campeonatoUruIntermedId ||
                               ctx.campeonatoId == save.campeonatoUruBCompetId
            val outrosCampeonatos = if (ehJogoDaLiga) {
                listOf(
                    save.campeonatoAId,
                    save.campeonatoBId,
                    save.campeonatoCId,
                    save.campeonatoDId,
                    // campeonatoArgAId excluído: gerenciado via verificarEAvancarFaseArgentina
                    save.campeonatoArgBId,
                    save.campeonatoUruBId,
                    save.campeonatoPremierId,
                    save.campeonatoChampionshipId,
                    save.campeonatoLaLigaId,
                    save.campeonatoSerieAId
                ).filter { it > 0 && it != ctx.campeonatoId }
            } else {
                emptyList()
            }
            val resultadoFinal = withContext(Dispatchers.Default) {
                gameRepository.finalizarPartidaComJogador(
                    ctx                  = ctx,
                    golsCasaFinal        = golsCasa,
                    golsForaFinal        = golsFora,
                    outrosCampeonatoIds  = outrosCampeonatos
                )
            }
            _resultadoSimulado.value = resultadoFinal

            // ── Operações secundárias em background ────────────────────────────────
            // A tela já pode exibir o botão "Voltar ao painel" enquanto estas rodam.
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    // ── Copa do Brasil ──────────────────────────────────────────────
                    if (save.copaId > 0 && ctx.campeonatoId == save.copaId && !resultadoFinal.precisaPenaltis) {
                        gameRepository.verificarEAvancarFaseCopa(
                            copaId        = save.copaId,
                            anoAtual      = save.anoAtual,
                            timeJogadorId = save.timeIdJogador,
                            temporadaId   = save.temporadaId
                        )
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
                    // ── Copa Argentina ──────────────────────────────────────────────
                    if (save.copaArgId > 0 && ctx.campeonatoId == save.copaArgId && !resultadoFinal.precisaPenaltis) {
                        gameRepository.verificarEAvancarFaseCopaArgentina(
                            copaArgId     = save.copaArgId,
                            anoAtual      = save.anoAtual,
                            timeJogadorId = save.timeIdJogador,
                            temporadaId   = save.temporadaId
                        )
                    }
                    if (save.copaArgId > 0 && ctx.campeonatoId == save.copaArgId && resultadoFinal.precisaPenaltis) {
                        _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                            copaId         = save.copaArgId,
                            voltaPartidaId = resultadoFinal.partidaId,
                            timeJogadorId  = save.timeIdJogador
                        )
                    }
                    if (ctx.campeonatoId != save.copaArgId && save.copaArgId > 0) {
                        gameRepository.simularProximaFaseCopaArgentinaSeJogadorEliminado(
                            copaArgId     = save.copaArgId,
                            timeJogadorId = save.timeIdJogador,
                            anoAtual      = save.anoAtual
                        )
                    }
                    // ── Copa da Inglaterra (jogo único + pênaltis) ──────────────────
                    if (save.copaInglaterraId > 0 && ctx.campeonatoId == save.copaInglaterraId && !resultadoFinal.precisaPenaltis) {
                        gameRepository.verificarEAvancarFaseCopaInglaterra(
                            copaId        = save.copaInglaterraId,
                            anoAtual      = save.anoAtual,
                            timeJogadorId = save.timeIdJogador,
                            temporadaId   = save.temporadaId
                        )
                    }
                    if (save.copaInglaterraId > 0 && ctx.campeonatoId == save.copaInglaterraId && resultadoFinal.precisaPenaltis) {
                        _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                            copaId         = save.copaInglaterraId,
                            voltaPartidaId = resultadoFinal.partidaId,
                            timeJogadorId  = save.timeIdJogador
                        )
                    }
                    if (ctx.campeonatoId != save.copaInglaterraId && save.copaInglaterraId > 0) {
                        gameRepository.simularProximaFaseCopaInglaterraSeJogadorEliminado(
                            copaId        = save.copaInglaterraId,
                            timeJogadorId = save.timeIdJogador,
                            anoAtual      = save.anoAtual,
                            temporadaId   = save.temporadaId
                        )
                    }
                    // ── Copa Espanhola (Copa do Rei — ida/volta + pênaltis) ─────────
                    if (save.copaEspanholaId > 0 && ctx.campeonatoId == save.copaEspanholaId && !resultadoFinal.precisaPenaltis) {
                        gameRepository.verificarEAvancarFaseCopaEspanhola(
                            copaId = save.copaEspanholaId, anoAtual = save.anoAtual,
                            timeJogadorId = save.timeIdJogador, temporadaId = save.temporadaId
                        )
                    }
                    if (save.copaEspanholaId > 0 && ctx.campeonatoId == save.copaEspanholaId && resultadoFinal.precisaPenaltis) {
                        _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                            copaId = save.copaEspanholaId, voltaPartidaId = resultadoFinal.partidaId,
                            timeJogadorId = save.timeIdJogador
                        )
                    }
                    if (ctx.campeonatoId != save.copaEspanholaId && save.copaEspanholaId > 0) {
                        gameRepository.simularProximaFaseCopaEspanholaSeJogadorEliminado(
                            copaId = save.copaEspanholaId, timeJogadorId = save.timeIdJogador,
                            anoAtual = save.anoAtual, temporadaId = save.temporadaId
                        )
                    }
                    // ── Copa Italiana (Coppa Italia — ida/volta + pênaltis) ─────────
                    if (save.copaItalianaId > 0 && ctx.campeonatoId == save.copaItalianaId && !resultadoFinal.precisaPenaltis) {
                        gameRepository.verificarEAvancarFaseCopaItaliana(
                            copaId = save.copaItalianaId, anoAtual = save.anoAtual,
                            timeJogadorId = save.timeIdJogador, temporadaId = save.temporadaId
                        )
                    }
                    if (save.copaItalianaId > 0 && ctx.campeonatoId == save.copaItalianaId && resultadoFinal.precisaPenaltis) {
                        _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                            copaId = save.copaItalianaId, voltaPartidaId = resultadoFinal.partidaId,
                            timeJogadorId = save.timeIdJogador
                        )
                    }
                    if (ctx.campeonatoId != save.copaItalianaId && save.copaItalianaId > 0) {
                        gameRepository.simularProximaFaseCopaItalianaSeJogadorEliminado(
                            copaId = save.copaItalianaId, timeJogadorId = save.timeIdJogador,
                            anoAtual = save.anoAtual, temporadaId = save.temporadaId
                        )
                    }
                    // ── UEFA Champions League (Fase de Liga + mata-mata) ────────────
                    if (save.championsId > 0 && ctx.campeonatoId == save.championsId && !resultadoFinal.precisaPenaltis) {
                        gameRepository.verificarEAvancarFaseChampions(
                            championsId = save.championsId, anoAtual = save.anoAtual,
                            timeJogadorId = save.timeIdJogador, temporadaId = save.temporadaId
                        )
                    }
                    if (save.championsId > 0 && ctx.campeonatoId == save.championsId && resultadoFinal.precisaPenaltis) {
                        _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                            copaId = save.championsId, voltaPartidaId = resultadoFinal.partidaId,
                            timeJogadorId = save.timeIdJogador
                        )
                    }
                    // Fora da partida do jogador, a Champions anda sozinha: uma rodada
                    // da Fase de Liga por partida dele, como as ligas nacionais, e as
                    // fases pendentes do mata-mata quando a Fase de Liga termina.
                    if (ctx.campeonatoId != save.championsId && save.championsId > 0) {
                        gameRepository.simularProximaRodadaChampionsSeNecessario(
                            championsId = save.championsId, timeJogadorId = save.timeIdJogador,
                            anoAtual = save.anoAtual, temporadaId = save.temporadaId
                        )
                    }
                    // ── UEFA Europa League (mesma máquina da Champions) ─────────────
                    if (save.europaLeagueId > 0 && ctx.campeonatoId == save.europaLeagueId && !resultadoFinal.precisaPenaltis) {
                        gameRepository.verificarEAvancarFaseEuropa(
                            europaLeagueId = save.europaLeagueId, anoAtual = save.anoAtual,
                            timeJogadorId = save.timeIdJogador, temporadaId = save.temporadaId
                        )
                    }
                    if (save.europaLeagueId > 0 && ctx.campeonatoId == save.europaLeagueId && resultadoFinal.precisaPenaltis) {
                        _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                            copaId = save.europaLeagueId, voltaPartidaId = resultadoFinal.partidaId,
                            timeJogadorId = save.timeIdJogador
                        )
                    }
                    if (ctx.campeonatoId != save.europaLeagueId && save.europaLeagueId > 0) {
                        gameRepository.simularProximaRodadaEuropaSeNecessario(
                            europaLeagueId = save.europaLeagueId, timeJogadorId = save.timeIdJogador,
                            anoAtual = save.anoAtual, temporadaId = save.temporadaId
                        )
                    }
                    // ── CONMEBOL Libertadores (Pré + grupos + mata-mata) ────────────
                    if (save.libertadoresId > 0 && ctx.campeonatoId == save.libertadoresId && !resultadoFinal.precisaPenaltis) {
                        gameRepository.verificarEAvancarFaseLibertadores(
                            libertadoresId = save.libertadoresId, anoAtual = save.anoAtual,
                            timeJogadorId = save.timeIdJogador, temporadaId = save.temporadaId
                        )
                    }
                    if (save.libertadoresId > 0 && ctx.campeonatoId == save.libertadoresId && resultadoFinal.precisaPenaltis) {
                        _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                            copaId = save.libertadoresId, voltaPartidaId = resultadoFinal.partidaId,
                            timeJogadorId = save.timeIdJogador
                        )
                    }
                    // Fora da partida do jogador, a Libertadores anda sozinha: uma
                    // rodada de grupo por partida dele, como as ligas nacionais, e as
                    // fases eliminatórias pendentes de uma vez.
                    if (ctx.campeonatoId != save.libertadoresId && save.libertadoresId > 0) {
                        gameRepository.simularProximaRodadaLibertadoresSeNecessario(
                            libertadoresId = save.libertadoresId, timeJogadorId = save.timeIdJogador,
                            anoAtual = save.anoAtual, temporadaId = save.temporadaId
                        )
                    }
                    // ── Supercopas (Brasil / Inglaterra / Espanha / Itália) ─────────
                    val supercopasIds = listOf(
                        save.supercopaId, save.supercopaInglaterraId,
                        save.supercopaEspanholaId, save.supercopaItalianaId,
                        save.supercopaUefaId
                    ).filter { it > 0 }
                    val supercopaDoJogo = supercopasIds.firstOrNull { it == ctx.campeonatoId }
                    if (supercopaDoJogo != null) {
                        if (resultadoFinal.precisaPenaltis) {
                            _penaltisParaSupercopa = true
                            _supercopaAtivaId = supercopaDoJogo
                            _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                                copaId         = supercopaDoJogo,
                                voltaPartidaId = resultadoFinal.partidaId,
                                timeJogadorId  = save.timeIdJogador
                            )
                        } else {
                            gameRepository.verificarEEncerrarSupercopa(
                                supercopaId   = supercopaDoJogo,
                                anoAtual      = save.anoAtual,
                                timeJogadorId = save.timeIdJogador,
                                temporadaId   = save.temporadaId
                            )
                        }
                    }
                    // Supercopas que o jogador NÃO está disputando → simula passivamente
                    supercopasIds.filter { it != ctx.campeonatoId }.forEach { scId ->
                        gameRepository.simularSupercopaSeJogadorNaoParticipa(
                            supercopaId   = scId,
                            timeJogadorId = save.timeIdJogador,
                            anoAtual      = save.anoAtual,
                            temporadaId   = save.temporadaId
                        )
                    }
                    // ── Argentine Primera División (Apertura + Clausura) ────────────
                    // Avança fase do torneio ativo (se jogador acabou de jogar nele)
                    listOf(save.campeonatoArgAId, save.campeonatoArgClausuraId)
                        .filter { it > 0 && it == ctx.campeonatoId && !resultadoFinal.precisaPenaltis }
                        .forEach { argId ->
                            gameRepository.verificarEAvancarFaseArgentina(
                                campeonatoId  = argId,
                                anoAtual      = save.anoAtual,
                                timeJogadorId = save.timeIdJogador,
                                temporadaId   = save.temporadaId
                            )
                        }
                    // Carrega dados para pênaltis interativos no mata-mata Argentine
                    listOf(save.campeonatoArgAId, save.campeonatoArgClausuraId)
                        .firstOrNull { it > 0 && it == ctx.campeonatoId && resultadoFinal.precisaPenaltis }
                        ?.let { argId ->
                            _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                                copaId         = argId,
                                voltaPartidaId = resultadoFinal.partidaId,
                                timeJogadorId  = save.timeIdJogador
                            )
                        }
                    // Avança torneios Argentine onde jogador não está (ou já foi eliminado).
                    val isUserArgentine = save.campeonatoId == save.campeonatoArgAId ||
                                         save.campeonatoId == save.campeonatoArgClausuraId
                    if (isUserArgentine) {
                        listOf(save.campeonatoArgAId, save.campeonatoArgClausuraId)
                            .filter { it > 0 && it != ctx.campeonatoId }
                            .forEach { argId ->
                                gameRepository.simularProximaFaseArgentinaSeNecessario(
                                    campeonatoId  = argId,
                                    timeJogadorId = save.timeIdJogador,
                                    anoAtual      = save.anoAtual,
                                    temporadaId   = save.temporadaId
                                )
                            }
                    } else if (ehJogoDaLiga) {
                        val aperturaId = save.campeonatoArgAId
                        val clausuraId = save.campeonatoArgClausuraId
                        val aperturaEncerrado = aperturaId <= 0 ||
                            gameRepository.isCampeonatoEncerrado(aperturaId)
                        // Avança Apertura primeiro; Clausura só avança quando Apertura estiver encerrado
                        if (aperturaId > 0 && !aperturaEncerrado) {
                            gameRepository.simularUmaRodadaArgentina(
                                campeonatoId  = aperturaId,
                                timeJogadorId = save.timeIdJogador,
                                anoAtual      = save.anoAtual,
                                temporadaId   = save.temporadaId
                            )
                        } else if (clausuraId > 0) {
                            gameRepository.simularUmaRodadaArgentina(
                                campeonatoId  = clausuraId,
                                timeJogadorId = save.timeIdJogador,
                                anoAtual      = save.anoAtual,
                                temporadaId   = save.temporadaId
                            )
                        }
                    }
                    // ── Campeonato Uruguaio (Intermediário – grupos + final) ─────────
                    if (save.campeonatoUruIntermedId > 0 && ctx.campeonatoId == save.campeonatoUruIntermedId && !resultadoFinal.precisaPenaltis) {
                        gameRepository.verificarEAvancarFaseIntermediarioUruguai(
                            intermedidId  = save.campeonatoUruIntermedId,
                            anoAtual      = save.anoAtual,
                            timeJogadorId = save.timeIdJogador,
                            temporadaId   = save.temporadaId
                        )
                    }
                    if (save.campeonatoUruIntermedId > 0 && ctx.campeonatoId == save.campeonatoUruIntermedId && resultadoFinal.precisaPenaltis) {
                        _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                            copaId         = save.campeonatoUruIntermedId,
                            voltaPartidaId = resultadoFinal.partidaId,
                            timeJogadorId  = save.timeIdJogador
                        )
                    }
                    // ── Campeonato Uruguaio (Clausura + Playoff) ─────────────────────
                    if (save.campeonatoUruClausuraId > 0 && ctx.campeonatoId == save.campeonatoUruClausuraId && !resultadoFinal.precisaPenaltis) {
                        gameRepository.verificarEAvancarPlayoffUruguai(
                            clausuraId    = save.campeonatoUruClausuraId,
                            aperturaId    = save.campeonatoUruAperturaId,
                            intermedidId  = save.campeonatoUruIntermedId,
                            anoAtual      = save.anoAtual,
                            timeJogadorId = save.timeIdJogador,
                            temporadaId   = save.temporadaId
                        )
                    }
                    if (save.campeonatoUruClausuraId > 0 && ctx.campeonatoId == save.campeonatoUruClausuraId && resultadoFinal.precisaPenaltis) {
                        _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                            copaId         = save.campeonatoUruClausuraId,
                            voltaPartidaId = resultadoFinal.partidaId,
                            timeJogadorId  = save.timeIdJogador
                        )
                    }
                    // ── Torneo Competencia URU B (grupos + final) ───────────────────
                    if (save.campeonatoUruBCompetId > 0 && ctx.campeonatoId == save.campeonatoUruBCompetId && !resultadoFinal.precisaPenaltis) {
                        gameRepository.verificarEAvancarFaseCompetenciaUruguaiB(
                            competId      = save.campeonatoUruBCompetId,
                            anoAtual      = save.anoAtual,
                            timeJogadorId = save.timeIdJogador,
                            temporadaId   = save.temporadaId
                        )
                    }
                    if (save.campeonatoUruBCompetId > 0 && ctx.campeonatoId == save.campeonatoUruBCompetId && resultadoFinal.precisaPenaltis) {
                        _dadosPenaltisAdversario.value = gameRepository.buscarDadosPenaltisAdversario(
                            copaId         = save.campeonatoUruBCompetId,
                            voltaPartidaId = resultadoFinal.partidaId,
                            timeJogadorId  = save.timeIdJogador
                        )
                    }
                    // Simula Uruguay inteiro quando jogador não é uruguaio
                    val ehJogadorUruguai = listOf(save.campeonatoUruAperturaId, save.campeonatoUruClausuraId,
                        save.campeonatoUruIntermedId, save.campeonatoUruBId, save.campeonatoUruBCompetId)
                        .any { it > 0 && it == ctx.campeonatoId }
                    if (!ehJogadorUruguai && (save.campeonatoUruAperturaId > 0 || save.campeonatoUruClausuraId > 0 || save.campeonatoUruBId > 0)) {
                        gameRepository.simularUruguaiSeNecessario(
                            aperturaId    = save.campeonatoUruAperturaId,
                            clausuraId    = save.campeonatoUruClausuraId,
                            intermedidId  = save.campeonatoUruIntermedId,
                            competBId     = save.campeonatoUruBCompetId,
                            timeJogadorId = save.timeIdJogador,
                            anoAtual      = save.anoAtual,
                            temporadaId   = save.temporadaId
                        )
                    }
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "avanço de fase pós-partida falhou", e)
                } finally {
                    try {
                        if (save.timeIdJogador > 0) {
                            _proximaPartida.value = gameRepository.buscarProximaPartida(save.timeIdJogador)
                        }
                    } catch (e: Exception) {
                        Log.e("DashboardViewModel", "atualizar proximaPartida falhou", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "finalizarPartidaSimulada falhou", e)
            _mensagem.value = "Falha ao finalizar a partida. Verifique o resultado."
        }
    }

    // Chamado quando o jogador fecha a tela de simulação
    fun fecharSimulacao() = viewModelScope.launch(Dispatchers.Default) {
        // Limpa a tela de simulação imediatamente para não bloquear o usuário
        _resultadoSimulado.value = null
        _outrosJogosRodada.value = emptyList()
        _escalacaoSimulacao.value = null
        _escalacaoAdversarioSimulacao.value = null
        _penaltisResultado.value = null
        _dadosPenaltisAdversario.value = null
        _penaltisInterativoConcluido.value = false
        _penaltisParaSupercopa = false

        // Amistoso: não processa fechamento de mês/liga; atualiza próxima partida + últimos resultados.
        if (_ehAmistoso) {
            _ehAmistoso = false
            try {
                val save = gameDataStore.saveState.first()
                if (save.timeIdJogador > 0) {
                    _proximaPartida.value = gameRepository.buscarProximaPartida(save.timeIdJogador)
                    _ultimosResultados.value = gameRepository.buscarUltimosResultados(
                        save.timeIdJogador,
                        listOf(save.campeonatoId, save.copaId, save.copaArgId, save.copaInglaterraId, save.supercopaId,
                            save.campeonatoArgClausuraId, save.campeonatoUruClausuraId, save.campeonatoUruIntermedId,
                            save.championsId, save.europaLeagueId, save.libertadoresId, save.campeonatoAmistososId).filter { it > 0 }
                    )
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "fecharSimulacao (amistoso) falhou", e)
            }
            return@launch
        }

        // Processamento pós-partida em background (não bloqueia mais a navegação)
        try {
            var save = gameDataStore.saveState.first()
            // Atualiza últimos resultados só agora, após a animação ter sido exibida
            _ultimosResultados.value = gameRepository.buscarUltimosResultados(
                save.timeIdJogador, listOf(save.campeonatoId, save.copaId, save.copaArgId, save.copaInglaterraId, save.supercopaId,
                    save.campeonatoArgClausuraId, save.campeonatoUruClausuraId, save.campeonatoUruIntermedId,
                    save.championsId, save.europaLeagueId, save.libertadoresId, save.campeonatoAmistososId).filter { it > 0 }
            )
            _posicaoNaTabela.value = gameRepository.buscarPosicaoNaTabela(save.campeonatoId, save.timeIdJogador)

            // ── Fechamento salarial automático ──────────────────────────────────
            // Deriva o mês vigente a partir do ordemGlobal da próxima partida (ou
            // do último resultado se a temporada acabou).  Se o mês derivado for
            // maior que o mesAtual persistido, processa o fechamento mensal para
            // cada mês que passou (salários, patrocínio, bilheteria, IA contratações).
            // O mês vigente é derivado do calendário real (liga/copa). Amistosos têm
            // ordemGlobal artificial (~450 em dez, ~5 em jan) e NÃO representam o mês
            // corrente — se usados como referência, empurram mesDerived p/ dezembro no
            // meio da temporada e disparam avanço de ano indevido. Por isso, no fallback
            // (temporada de liga já encerrada), ignoramos os amistosos.
            val ultimoNaoAmistoso = _ultimosResultados.value
                .firstOrNull { it.campeonatoId != save.campeonatoAmistososId }
            val ordemRef = (_proximaPartida.value ?: ultimoNaoAmistoso)?.ordemGlobal ?: 0
            val mesDerived = if (ordemRef > 0) ordemGlobalParaMes(ordemRef) else save.mesAtual

            // Fecha todos os meses até o mês vigente inclusive (<=), garantindo que
            // patrocínio e salários do mês corrente sejam registrados ao entrar nele.
            // O limite de 12 iterações evita loop infinito; e ao cruzar dez→jan (mês 12
            // fechado → ano avança) o loop PARA — sem esse break, mesAtual voltava a 1
            // (≤ mesDerived) e o ciclo rodava um ano inteiro, saltando o ano +2/+3.
            var iteracoesMes = 0
            while (save.mesAtual <= mesDerived && iteracoesMes < 12) {
                iteracoesMes++
                val mesFechando           = save.mesAtual
                val patrocinioMensal      = save.patrocinadorValorAnual / 12L
                val patrocinioJaCreditado = save.patrocinadorPreCreditado
                gameRepository.fecharMes(save.timeIdJogador, save.temporadaId, mesFechando, patrocinioMensal, patrocinioJaCreditado, save.anoAtual)
                gameDataStore.avancarMes()  // também zera patrocinadorPreCreditado
                save = gameDataStore.saveState.first()
                if (mesFechando == 12) break  // cruzou dez→jan: fim do ano, não churna o próximo
            }
            // Após simular a rodada, a IA responde a eventuais contra-ofertas pendentes
            val savePos = gameDataStore.saveState.first()
            if (savePos.timeIdJogador > 0) {
                try {
                    gameRepository.processarRespostasIANegociacao(
                        playerTimeId = savePos.timeIdJogador,
                        temporadaId  = savePos.temporadaId,
                        mes          = savePos.mesAtual
                    )
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "processarRespostasIANegociacao falhou", e)
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "fecharSimulacao falhou", e)
            _mensagem.value = "Erro ao encerrar a partida. Estado pode ter ficado incompleto."
        }
    }

    // Chamado quando o jogador conclui a disputa interativa de pênaltis
    fun finalizarPenaltisJogador(resultado: ResultadoPenaltis) = viewModelScope.launch(Dispatchers.Default) {
        val save          = gameDataStore.saveState.first()
        val resultadoPartida = _resultadoSimulado.value ?: return@launch
        val argId = listOf(save.campeonatoArgAId, save.campeonatoArgClausuraId)
            .firstOrNull { it > 0 && it == _contextoSimulacao?.campeonatoId }
        val uruIntermedId = if (save.campeonatoUruIntermedId > 0 && _contextoSimulacao?.campeonatoId == save.campeonatoUruIntermedId) save.campeonatoUruIntermedId else -1
        val uruClausuraId = if (save.campeonatoUruClausuraId > 0 && _contextoSimulacao?.campeonatoId == save.campeonatoUruClausuraId) save.campeonatoUruClausuraId else -1
        val copaIngId = if (save.copaInglaterraId > 0 && _contextoSimulacao?.campeonatoId == save.copaInglaterraId) save.copaInglaterraId else -1
        val copaEspId = if (save.copaEspanholaId > 0 && _contextoSimulacao?.campeonatoId == save.copaEspanholaId) save.copaEspanholaId else -1
        val copaItaId = if (save.copaItalianaId > 0 && _contextoSimulacao?.campeonatoId == save.copaItalianaId) save.copaItalianaId else -1
        val championsId = if (save.championsId > 0 && _contextoSimulacao?.campeonatoId == save.championsId) save.championsId else -1
        val europaLeagueId = if (save.europaLeagueId > 0 && _contextoSimulacao?.campeonatoId == save.europaLeagueId) save.europaLeagueId else -1
        val libertadoresId = if (save.libertadoresId > 0 && _contextoSimulacao?.campeonatoId == save.libertadoresId) save.libertadoresId else -1
        try {
            when {
                _penaltisParaSupercopa -> {
                    gameRepository.persistirResultadoPenaltisJogadorSupercopa(
                        resultado     = resultado,
                        supercopaId   = _supercopaAtivaId,
                        anoAtual      = save.anoAtual,
                        partidaId     = resultadoPartida.partidaId,
                        timeJogadorId = save.timeIdJogador,
                        temporadaId   = save.temporadaId
                    )
                }
                argId != null -> {
                    gameRepository.persistirResultadoPenaltisJogadorArgentina(
                        resultado       = resultado,
                        argCampeonatoId = argId,
                        anoAtual        = save.anoAtual,
                        partidaId       = resultadoPartida.partidaId,
                        timeJogadorId   = save.timeIdJogador,
                        temporadaId     = save.temporadaId
                    )
                }
                uruIntermedId > 0 -> {
                    gameRepository.persistirResultadoPenaltisJogadorUruguaiIntermediario(
                        resultado       = resultado,
                        intermedidId    = uruIntermedId,
                        anoAtual        = save.anoAtual,
                        partidaId       = resultadoPartida.partidaId,
                        timeJogadorId   = save.timeIdJogador,
                        temporadaId     = save.temporadaId
                    )
                }
                uruClausuraId > 0 -> {
                    gameRepository.persistirResultadoPenaltisJogadorUruguaiPlayoff(
                        resultado       = resultado,
                        clausuraId      = uruClausuraId,
                        aperturaId      = save.campeonatoUruAperturaId,
                        intermedidId    = save.campeonatoUruIntermedId,
                        anoAtual        = save.anoAtual,
                        partidaId       = resultadoPartida.partidaId,
                        timeJogadorId   = save.timeIdJogador,
                        temporadaId     = save.temporadaId
                    )
                }
                copaIngId > 0 -> {
                    gameRepository.persistirResultadoPenaltisJogadorCopaInglaterra(
                        resultado     = resultado,
                        copaId        = copaIngId,
                        anoAtual      = save.anoAtual,
                        partidaId     = resultadoPartida.partidaId,
                        timeJogadorId = save.timeIdJogador,
                        temporadaId   = save.temporadaId
                    )
                }
                championsId > 0 -> {
                    gameRepository.persistirResultadoPenaltisJogadorChampions(
                        resultado      = resultado,
                        championsId    = championsId,
                        anoAtual       = save.anoAtual,
                        voltaPartidaId = resultadoPartida.partidaId,
                        timeJogadorId  = save.timeIdJogador,
                        temporadaId    = save.temporadaId
                    )
                }
                europaLeagueId > 0 -> {
                    gameRepository.persistirResultadoPenaltisJogadorEuropa(
                        resultado      = resultado,
                        europaLeagueId = europaLeagueId,
                        anoAtual       = save.anoAtual,
                        voltaPartidaId = resultadoPartida.partidaId,
                        timeJogadorId  = save.timeIdJogador,
                        temporadaId    = save.temporadaId
                    )
                }
                libertadoresId > 0 -> {
                    gameRepository.persistirResultadoPenaltisJogadorLibertadores(
                        resultado      = resultado,
                        libertadoresId = libertadoresId,
                        anoAtual       = save.anoAtual,
                        voltaPartidaId = resultadoPartida.partidaId,
                        timeJogadorId  = save.timeIdJogador,
                        temporadaId    = save.temporadaId
                    )
                }
                copaEspId > 0 || copaItaId > 0 -> {
                    val copaId = if (copaEspId > 0) copaEspId else copaItaId
                    gameRepository.persistirResultadoPenaltisJogador(
                        resultado         = resultado,
                        copaId            = copaId,
                        anoAtual          = save.anoAtual,
                        voltaPartidaId    = resultadoPartida.partidaId,
                        fases             = br.com.managerfoot.domain.engine.MotorCampeonato.COPA16_FASES,
                        ordemGlobal       = br.com.managerfoot.domain.engine.MotorCampeonato.COPA16_ORDEM_GLOBAL,
                        divisaoHallDaFama = if (copaEspId > 0) 25 else 26,
                        premioCampeao     = 15_000_000_00L,
                        premioVice        = 7_000_000_00L,
                        timeJogadorId     = save.timeIdJogador,
                        temporadaId       = save.temporadaId
                    )
                }
                else -> {
                    val copaIdAtivo = if (save.copaArgId > 0 && _contextoSimulacao?.campeonatoId == save.copaArgId) save.copaArgId else save.copaId
                    gameRepository.persistirResultadoPenaltisJogador(
                        resultado      = resultado,
                        copaId         = copaIdAtivo,
                        anoAtual       = save.anoAtual,
                        voltaPartidaId = resultadoPartida.partidaId
                    )
                }
            }
            _penaltisParaSupercopa = false
            _penaltisInterativoConcluido.value = true
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "finalizarPenaltisJogador falhou", e)
            _mensagem.value = "Falha ao registrar a disputa de pênaltis."
        }
    }

    // Chamado quando o jogador confirma os cobradores de pênalti
    fun simularPenaltisJogador(
        cobradores: List<JogadorNaEscalacao>,
        goleiroDefesa: Int
    ) = viewModelScope.launch {
        val save    = gameDataStore.saveState.first()
        val resultado = _resultadoSimulado.value ?: return@launch
        val argId = listOf(save.campeonatoArgAId, save.campeonatoArgClausuraId)
            .firstOrNull { it > 0 && it == _contextoSimulacao?.campeonatoId }
        val copaIngId = if (save.copaInglaterraId > 0 && _contextoSimulacao?.campeonatoId == save.copaInglaterraId) save.copaInglaterraId else -1
        val copaEspId = if (save.copaEspanholaId > 0 && _contextoSimulacao?.campeonatoId == save.copaEspanholaId) save.copaEspanholaId else -1
        val copaItaId = if (save.copaItalianaId > 0 && _contextoSimulacao?.campeonatoId == save.copaItalianaId) save.copaItalianaId else -1
        val championsId = if (save.championsId > 0 && _contextoSimulacao?.campeonatoId == save.championsId) save.championsId else -1
        val europaLeagueId = if (save.europaLeagueId > 0 && _contextoSimulacao?.campeonatoId == save.europaLeagueId) save.europaLeagueId else -1
        val libertadoresId = if (save.libertadoresId > 0 && _contextoSimulacao?.campeonatoId == save.libertadoresId) save.libertadoresId else -1
        try {
            val penaltis = when {
                _penaltisParaSupercopa -> {
                    gameRepository.simularEPersistirPenaltisJogadorSupercopa(
                        supercopaId          = _supercopaAtivaId,
                        anoAtual             = save.anoAtual,
                        partidaId            = resultado.partidaId,
                        cobradoresJogador    = cobradores,
                        timeJogadorId        = save.timeIdJogador,
                        goleiroJogadorDefesa = goleiroDefesa,
                        temporadaId          = save.temporadaId
                    )
                }
                argId != null -> {
                    gameRepository.simularEPersistirPenaltisJogadorArgentina(
                        argCampeonatoId      = argId,
                        anoAtual             = save.anoAtual,
                        partidaId            = resultado.partidaId,
                        cobradoresJogador    = cobradores,
                        timeJogadorId        = save.timeIdJogador,
                        goleiroJogadorDefesa = goleiroDefesa,
                        temporadaId          = save.temporadaId
                    )
                }
                copaIngId > 0 -> {
                    gameRepository.simularEPersistirPenaltisJogadorCopaInglaterra(
                        copaId               = copaIngId,
                        anoAtual             = save.anoAtual,
                        partidaId            = resultado.partidaId,
                        cobradoresJogador    = cobradores,
                        timeJogadorId        = save.timeIdJogador,
                        goleiroJogadorDefesa = goleiroDefesa,
                        temporadaId          = save.temporadaId
                    )
                }
                championsId > 0 -> {
                    gameRepository.simularEPersistirPenaltisJogador(
                        copaId               = championsId,
                        anoAtual             = save.anoAtual,
                        voltaPartidaId       = resultado.partidaId,
                        cobradoresJogador    = cobradores,
                        timeJogadorId        = save.timeIdJogador,
                        goleiroJogadorDefesa = goleiroDefesa,
                        temporadaId          = save.temporadaId,
                        avancoAlternativo    = {
                            gameRepository.verificarEAvancarFaseChampions(
                                championsId   = championsId,
                                anoAtual      = save.anoAtual,
                                timeJogadorId = save.timeIdJogador,
                                temporadaId   = save.temporadaId
                            )
                        }
                    )
                }
                europaLeagueId > 0 -> {
                    gameRepository.simularEPersistirPenaltisJogador(
                        copaId               = europaLeagueId,
                        anoAtual             = save.anoAtual,
                        voltaPartidaId       = resultado.partidaId,
                        cobradoresJogador    = cobradores,
                        timeJogadorId        = save.timeIdJogador,
                        goleiroJogadorDefesa = goleiroDefesa,
                        temporadaId          = save.temporadaId,
                        avancoAlternativo    = {
                            gameRepository.verificarEAvancarFaseEuropa(
                                europaLeagueId = europaLeagueId,
                                anoAtual       = save.anoAtual,
                                timeJogadorId  = save.timeIdJogador,
                                temporadaId    = save.temporadaId
                            )
                        }
                    )
                }
                libertadoresId > 0 -> {
                    gameRepository.simularEPersistirPenaltisJogador(
                        copaId               = libertadoresId,
                        anoAtual             = save.anoAtual,
                        voltaPartidaId       = resultado.partidaId,
                        cobradoresJogador    = cobradores,
                        timeJogadorId        = save.timeIdJogador,
                        goleiroJogadorDefesa = goleiroDefesa,
                        temporadaId          = save.temporadaId,
                        avancoAlternativo    = {
                            gameRepository.verificarEAvancarFaseLibertadores(
                                libertadoresId = libertadoresId,
                                anoAtual       = save.anoAtual,
                                timeJogadorId  = save.timeIdJogador,
                                temporadaId    = save.temporadaId
                            )
                        }
                    )
                }
                copaEspId > 0 || copaItaId > 0 -> {
                    val copaId = if (copaEspId > 0) copaEspId else copaItaId
                    gameRepository.simularEPersistirPenaltisJogador(
                        copaId               = copaId,
                        anoAtual             = save.anoAtual,
                        voltaPartidaId       = resultado.partidaId,
                        cobradoresJogador    = cobradores,
                        timeJogadorId        = save.timeIdJogador,
                        goleiroJogadorDefesa = goleiroDefesa,
                        fases                = br.com.managerfoot.domain.engine.MotorCampeonato.COPA16_FASES,
                        ordemGlobal          = br.com.managerfoot.domain.engine.MotorCampeonato.COPA16_ORDEM_GLOBAL,
                        divisaoHallDaFama    = if (copaEspId > 0) 25 else 26,
                        premioCampeao        = 15_000_000_00L,
                        premioVice           = 7_000_000_00L,
                        temporadaId          = save.temporadaId
                    )
                }
                else -> {
                    val copaIdAtivo = if (save.copaArgId > 0 && _contextoSimulacao?.campeonatoId == save.copaArgId) save.copaArgId else save.copaId
                    gameRepository.simularEPersistirPenaltisJogador(
                        copaId               = copaIdAtivo,
                        anoAtual             = save.anoAtual,
                        voltaPartidaId       = resultado.partidaId,
                        cobradoresJogador    = cobradores,
                        timeJogadorId        = save.timeIdJogador,
                        goleiroJogadorDefesa = goleiroDefesa
                    )
                }
            }
            _penaltisParaSupercopa = false
            _penaltisResultado.value = penaltis
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "simularPenaltisJogador falhou", e)
            _mensagem.value = "Falha na simulação da disputa de pênaltis."
        }
    }

    fun encerrarTemporada() = viewModelScope.launch(Dispatchers.Default) {
        val save = gameDataStore.saveState.first()
        _uiState.value = DashboardUiState.Simulando
        try {
            // Limpa os amistosos de dezembro antes da virada (não poluem a nova temporada)
            if (save.campeonatoAmistososId > 0) {
                try { gameRepository.limparAmistosos(save.campeonatoAmistososId) } catch (_: Exception) {}
            }
            // fecharSimulacao() chama avancarMes() para dezembro, fazendo o ano avançar de 12→1
            // antes de encerrarTemporada ser chamada. Detectamos isso: se mesAtual==1, o ano já
            // foi incrementado antecipadamente, então usamos anoAtual-1 como o ano da temporada.
            val anoTemporada = if (save.mesAtual == 1) save.anoAtual - 1 else save.anoAtual
            val novaInfo = gameRepository.encerrarTemporadaComHallDaFama(
                campeonatoAId            = save.campeonatoAId,
                campeonatoBId            = save.campeonatoBId,
                campeonatoCId            = save.campeonatoCId,
                campeonatoDId            = save.campeonatoDId,
                campeonatoArgAId         = save.campeonatoArgAId,
                campeonatoArgBId         = save.campeonatoArgBId,
                campeonatoArgClausuraId  = save.campeonatoArgClausuraId,
                campeonatoUruAperturaId  = save.campeonatoUruAperturaId,
                campeonatoUruClausuraId  = save.campeonatoUruClausuraId,
                campeonatoUruIntermedId  = save.campeonatoUruIntermedId,
                campeonatoUruBId         = save.campeonatoUruBId,
                campeonatoUruBCompetId    = save.campeonatoUruBCompetId,
                campeonatoPremierId       = save.campeonatoPremierId,
                campeonatoChampionshipId  = save.campeonatoChampionshipId,
                campeonatoLaLigaId        = save.campeonatoLaLigaId,
                campeonatoSerieAId        = save.campeonatoSerieAId,
                championsId               = save.championsId,
                europaLeagueId            = save.europaLeagueId,
                libertadoresId            = save.libertadoresId,
                temporadaId              = save.temporadaId,
                ano                      = anoTemporada,
                timeJogadorId            = save.timeIdJogador
            )
            // Determina em qual divisão o jogador estará na próxima temporada
            val divJogador = timeRepository.buscarPorId(save.timeIdJogador)?.divisao ?: 1
            val novoCampJogador = when (divJogador) {
                1 -> novaInfo.campeonatoAId; 2 -> novaInfo.campeonatoBId
                3 -> novaInfo.campeonatoCId; 4 -> novaInfo.campeonatoDId
                5 -> novaInfo.campeonatoArgAId
                6 -> novaInfo.campeonatoArgBId
                9 -> novaInfo.campeonatoUruAperturaId
                10 -> novaInfo.campeonatoUruBId
                11 -> novaInfo.campeonatoPremierId
                12 -> novaInfo.campeonatoChampionshipId
                13 -> novaInfo.campeonatoLaLigaId
                14 -> novaInfo.campeonatoSerieAId
                else -> novaInfo.campeonatoArgAId
            }
            gameDataStore.salvarNovaTemporada(
                campeonatoId     = novoCampJogador,
                campeonatoAId    = novaInfo.campeonatoAId,
                campeonatoBId    = novaInfo.campeonatoBId,
                campeonatoCId    = novaInfo.campeonatoCId,
                campeonatoDId    = novaInfo.campeonatoDId,
                campeonatoArgAId         = novaInfo.campeonatoArgAId,
                campeonatoArgBId         = novaInfo.campeonatoArgBId,
                campeonatoArgClausuraId  = novaInfo.campeonatoArgClausuraId,
                campeonatoUruAperturaId  = novaInfo.campeonatoUruAperturaId,
                campeonatoUruClausuraId  = novaInfo.campeonatoUruClausuraId,
                campeonatoUruIntermedId  = novaInfo.campeonatoUruIntermedId,
                campeonatoUruBId         = novaInfo.campeonatoUruBId,
                campeonatoUruBCompetId    = novaInfo.campeonatoUruBCompetId,
                campeonatoPremierId       = novaInfo.campeonatoPremierId,
                campeonatoChampionshipId  = novaInfo.campeonatoChampionshipId,
                campeonatoLaLigaId        = novaInfo.campeonatoLaLigaId,
                campeonatoSerieAId        = novaInfo.campeonatoSerieAId,
                copaId        = novaInfo.copaId,
                copaArgId     = novaInfo.copaArgId,
                copaInglaterraId = novaInfo.copaInglaterraId,
                copaEspanholaId = novaInfo.copaEspanholaId,
                copaItalianaId = novaInfo.copaItalianaId,
                championsId    = novaInfo.championsId,
                europaLeagueId = novaInfo.europaLeagueId,
                libertadoresId = novaInfo.libertadoresId,
                supercopaId   = novaInfo.supercopaId,
                supercopaInglaterraId = novaInfo.supercopaInglaterraId,
                supercopaEspanholaId  = novaInfo.supercopaEspanholaId,
                supercopaItalianaId   = novaInfo.supercopaItalianaId,
                supercopaUefaId       = novaInfo.supercopaUefaId,
                temporadaId   = novaInfo.temporadaId,
                ano           = novaInfo.ano
            )
            val saveAtualizado = gameDataStore.saveState.first()
            _proximaPartida.value    = gameRepository.buscarProximaPartida(saveAtualizado.timeIdJogador)
            _ultimosResultados.value = gameRepository.buscarUltimosResultados(
                saveAtualizado.timeIdJogador,
                listOf(saveAtualizado.campeonatoId, saveAtualizado.copaId, saveAtualizado.copaArgId, saveAtualizado.supercopaId, saveAtualizado.campeonatoArgClausuraId).filter { it > 0 }
            )
            // Gera propostas de trabalho de outros clubes com base nos resultados da temporada encerrada
            if (save.timeIdJogador > 0) {
                gameRepository.gerarPropostasDeClube(
                    campeonatoLigaId = save.campeonatoId,
                    copaId           = save.copaId,
                    timeJogadorId    = save.timeIdJogador,
                    temporadaId      = save.temporadaId
                )
            }
            // Exibe a mensagem de cumprimento/falha da meta em Snackbar
            novaInfo.resultadoMeta?.let { _mensagem.value = it }
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "encerrarTemporada falhou", e)
            _mensagem.value = "Não foi possível encerrar a temporada. Tente novamente."
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

    private val _escalacaoAdversario = MutableStateFlow<Escalacao?>(null)
    val escalacaoAdversario: StateFlow<Escalacao?> = _escalacaoAdversario.asStateFlow()

    private var timeIdCarregado: Int = -1

    fun carregar(timeId: Int) = viewModelScope.launch {
        timeIdCarregado = timeId
        jogadorRepository.observeElenco(timeId).collect { lista ->
            val listaOrdenada = lista.sortedWith(compareBy({ it.posicao.ordinal }, { -it.forca }))
            _elenco.value = listaOrdenada
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
                    // Persiste as reservas geradas automaticamente
                    gerada.reservas.forEach { jne ->
                        jogadorRepository.atualizarEscalacao(jne.jogador.id, 2, null)
                    }
                    _escalacao.value = gerada
                }
            } else {
                // Atualiza os atributos dos jogadores na escalação sempre que o elenco
                // mudar no banco (ex.: após evolução/regressão de atributos no jogo).
                // Preserva posicaoUsada e estrutura da escalação definida pelo usuário.
                val esc = _escalacao.value ?: return@collect
                val mapaJogadores = lista.associateBy { it.id }
                val novosTitulares = esc.titulares.map { jne ->
                    val atualizado = mapaJogadores[jne.jogador.id]
                    if (atualizado != null) jne.copy(jogador = atualizado) else jne
                }
                val novasReservas = esc.reservas.map { jne ->
                    val atualizado = mapaJogadores[jne.jogador.id]
                    if (atualizado != null) jne.copy(jogador = atualizado) else jne
                }
                if (novosTitulares != esc.titulares || novasReservas != esc.reservas) {
                    _escalacao.value = esc.copy(titulares = novosTitulares, reservas = novasReservas)
                }
            }
        }
    }

    fun selecionarJogador(jogador: Jogador?) { _jogadorSelecionado.value = jogador }

    fun aposentarJogador(jogadorId: Int) = viewModelScope.launch {
        jogadorRepository.aposentarJogador(jogadorId)
    }

    fun carregarAdversario(timeId: Int) = viewModelScope.launch {
        val time = timeRepository.buscarPorId(timeId) ?: return@launch
        _adversario.value = time
        val elencoAdversario = jogadorRepository.buscarDisponiveis(timeId)
        _escalacaoAdversario.value = IATimeRival.gerarEscalacao(time, elencoAdversario)
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

    /**
     * Troca um titular por um reserva:
     * - O reserva entra como titular na posição do titular sainte.
     * - O titular sainte passa a ser reserva (na sua posição natural).
     */
    fun trocarTitularComReserva(titular: JogadorNaEscalacao, reserva: JogadorNaEscalacao) {
        val atual = _escalacao.value ?: return
        val novosTitulares = atual.titulares
            .filter { it.jogador.id != titular.jogador.id } +
            JogadorNaEscalacao(reserva.jogador, titular.posicaoUsada)
        val novasReservas = atual.reservas
            .filter { it.jogador.id != reserva.jogador.id } +
            JogadorNaEscalacao(titular.jogador, titular.jogador.posicao)
        _escalacao.value = atual.copy(titulares = novosTitulares, reservas = novasReservas)
        viewModelScope.launch {
            jogadorRepository.atualizarEscalacao(reserva.jogador.id, 1, titular.posicaoUsada)
            jogadorRepository.atualizarEscalacao(titular.jogador.id, 2, null)
        }
    }

    /**
     * Substitui um titular por um jogador do elenco não escalado:
     * - O novo jogador entra como titular na posição do titular sainte.
     * - O titular sainte é removido da escalação (volta ao elenco).
     */
    fun substituirTitularPorElenco(titular: JogadorNaEscalacao, novoJogador: Jogador) {
        val atual = _escalacao.value ?: return
        if (atual.titulares.size >= 11 && atual.titulares.none { it.jogador.id == titular.jogador.id }) return
        val novosTitulares = atual.titulares
            .filter { it.jogador.id != titular.jogador.id } +
            JogadorNaEscalacao(novoJogador, titular.posicaoUsada)
        val novasReservas = atual.reservas.filter { it.jogador.id != novoJogador.id }
        _escalacao.value = atual.copy(titulares = novosTitulares, reservas = novasReservas)
        viewModelScope.launch {
            jogadorRepository.atualizarEscalacao(novoJogador.id, 1, titular.posicaoUsada)
            jogadorRepository.atualizarEscalacao(titular.jogador.id, 0, null)
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

    /**
     * Re-escala automaticamente os 11 melhores + reservas para a formação ATUAL,
     * descartando ajustes manuais. Usado pelo botão "Escalar os melhores".
     */
    fun escalarMelhores() = viewModelScope.launch {
        val atual = _escalacao.value ?: return@launch
        val todos       = _elenco.value
        val disponiveis = todos.filter { !it.lesionado && !it.suspenso }
        val nova = IATimeRival.gerarEscalacao(atual.time, disponiveis)

        // Limpa status de escalação e persiste a nova seleção ótima
        todos.forEach { j -> jogadorRepository.atualizarEscalacao(j.id, 0, null) }
        nova.titulares.forEach { jne ->
            val pos = if (jne.posicaoUsada != jne.jogador.posicao) jne.posicaoUsada else null
            jogadorRepository.atualizarEscalacao(jne.jogador.id, 1, pos)
        }
        nova.reservas.forEach { jne ->
            jogadorRepository.atualizarEscalacao(jne.jogador.id, 2, null)
        }
        _escalacao.value = nova
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
    private val timeRepository: TimeRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _jogadoresLivres = MutableStateFlow<List<Jogador>>(emptyList())
    val jogadoresLivres: StateFlow<List<Jogador>> = _jogadoresLivres.asStateFlow()

    private val _elencoAtual = MutableStateFlow<List<Jogador>>(emptyList())
    val elencoAtual: StateFlow<List<Jogador>> = _elencoAtual.asStateFlow()

    private val _saldo = MutableStateFlow(0L)
    val saldo: StateFlow<Long> = _saldo.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    private val _transferencias = MutableStateFlow<List<TransferenciaDetalhe>>(emptyList())
    val transferencias: StateFlow<List<TransferenciaDetalhe>> = _transferencias.asStateFlow()

    private val _jogadoresEmprestados = MutableStateFlow<List<Jogador>>(emptyList())
    val jogadoresEmprestados: StateFlow<List<Jogador>> = _jogadoresEmprestados.asStateFlow()

    private val _nomesTimesPorId = MutableStateFlow<Map<Int, String>>(emptyMap())
    val nomesTimesPorId: StateFlow<Map<Int, String>> = _nomesTimesPorId.asStateFlow()

    private val _propostas = MutableStateFlow<List<PropostaIATransferencia>>(emptyList())
    val propostas: StateFlow<List<PropostaIATransferencia>> = _propostas.asStateFlow()

    private var timeId = -1

    fun carregar(tId: Int) {
        timeId = tId
        viewModelScope.launch {
            launch { jogadorRepository.observeLivres().collect { _jogadoresLivres.value = it } }
            launch { jogadorRepository.observeElenco(tId).collect { _elencoAtual.value = it } }
            launch {
                timeRepository.observeTodos().collect { times ->
                    _saldo.value = times.find { it.id == tId }?.saldo ?: 0L
                    _nomesTimesPorId.value = times.associate { it.id to it.nome }
                }
            }
            launch { jogadorRepository.observeTodasTransferencias().collect { _transferencias.value = it } }
            launch { jogadorRepository.observeEmprestadosDoTime(tId).collect { _jogadoresEmprestados.value = it } }
            // Observa propostas ativas e enriquece com nomes de jogador e time
            launch {
                gameRepository.observarPropostasAtivas().collect { lista ->
                    _propostas.value = lista.mapNotNull { p ->
                        val jogador   = jogadorRepository.buscarPorId(p.jogadorId)   ?: return@mapNotNull null
                        val comprador = timeRepository.buscarPorId(p.timeCompradorId) ?: return@mapNotNull null
                        PropostaIATransferencia(
                            id                    = p.id,
                            jogadorId             = p.jogadorId,
                            jogadorNome           = jogador.nome,
                            jogadorNomeAbrev      = jogador.nomeAbreviado,
                            posicao               = jogador.posicao,
                            timeCompradorId       = p.timeCompradorId,
                            nomeTimeComprador     = comprador.nome,
                            escudoTimeComprador   = comprador.escudoRes,
                            valorOfertado         = p.valorOfertado,
                            valorMercadoJogador   = jogador.valorMercado,
                            status                = p.status,
                            tentativasNegociacao  = p.tentativasNegociacao,
                            valorSolicitadoJogador = p.valorSolicitadoJogador,
                            tipoProposta          = p.tipoProposta
                        )
                    }
                }
            }
        }
    }

    fun contratarJogador(jogador: Jogador) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        val time = timeRepository.buscarPorId(timeId) ?: return@launch
        val preco = CalculadoraMercado.precoCompraUsuario(jogador.valorMercado)
        if (time.saldo < preco) {
            _mensagem.value = "Saldo insuficiente para contratar ${jogador.nomeAbreviado}"
            return@launch
        }
        val qtdElenco = jogadorRepository.contarJogadores(timeId)
        if (qtdElenco >= 35) {
            _mensagem.value = "Elenco cheio (35/35). Venda ou dispense um jogador antes de contratar."
            return@launch
        }
        timeRepository.debitarSaldo(timeId, preco)
        jogadorRepository.realizarTransferencia(
            oferta = OfertaTransferencia(
                jogadorId = jogador.id,
                timeCompradorId = timeId,
                timeVendedorId = null,
                valor = preco,
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

        // Preço recebido pelo usuário: valor de mercado com deságio de liquidez.
        val preco = CalculadoraMercado.precoVendaUsuario(jogador.valorMercado)

        // Selecionar um time da IA com saldo suficiente como comprador.
        // Exclui o próprio time do jogador e prefere times com mais reputação.
        val comprador = timeRepository.buscarTodosOrdenadosPorReputacao()
            .filter { it.id != timeId && it.saldo >= preco }
            .randomOrNull()

        if (comprador == null) {
            _mensagem.value = "Nenhum clube interessado em ${jogador.nomeAbreviado} no momento. Jogador permanece no elenco."
            return@launch
        }

        timeRepository.creditarSaldo(timeId, preco)
        timeRepository.debitarSaldo(comprador.id, preco)

        jogadorRepository.realizarVenda(
            oferta = OfertaTransferencia(
                jogadorId = jogador.id,
                timeCompradorId = comprador.id,
                timeVendedorId = timeId,
                valor = preco,
                salarioProposto = jogador.salario,
                contratoAnos = 3
            ),
            temporadaId = save.temporadaId,
            mes = save.mesAtual
        )
        _mensagem.value = "${jogador.nomeAbreviado} vendido para ${comprador.nome} por ${formatarValor(preco)}"
    }

    // ─── Propostas recebidas de times da IA ──────────────────────────────────

    fun aceitarProposta(proposta: PropostaIATransferencia) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        gameRepository.aceitarProposta(
            proposta = br.com.managerfoot.data.database.entities.PropostaIAEntity(
                id              = proposta.id,
                jogadorId       = proposta.jogadorId,
                timeCompradorId = proposta.timeCompradorId,
                valorOfertado   = proposta.valorOfertado,
                status          = proposta.status,
                tentativasNegociacao   = proposta.tentativasNegociacao,
                valorSolicitadoJogador = proposta.valorSolicitadoJogador,
                temporadaId     = save.temporadaId,
                mes             = save.mesAtual,
                tipoProposta    = proposta.tipoProposta
            ),
            playerTimeId = timeId,
            temporadaId  = save.temporadaId,
            mes          = save.mesAtual,
            anoAtual     = save.anoAtual
        )
        val mesRetorno = save.mesAtual
        val anoRetorno = save.anoAtual + 1
        _mensagem.value = if (proposta.tipoProposta == TipoProposta.EMPRESTIMO) {
            "${proposta.jogadorNomeAbrev} emprestado para ${proposta.nomeTimeComprador}. Retorna em ${mesRetorno.toString().padStart(2,'0')}/${anoRetorno}."
        } else {
            "${proposta.jogadorNomeAbrev} vendido para ${proposta.nomeTimeComprador} por ${formatarValor(proposta.valorOfertado)}!"
        }
    }

    fun recusarProposta(propostaId: Int) = viewModelScope.launch {
        gameRepository.recusarProposta(propostaId)
        _mensagem.value = "Proposta recusada."
    }

    fun negociarProposta(propostaId: Int, valorSolicitado: Long) = viewModelScope.launch {
        gameRepository.negociarProposta(propostaId, valorSolicitado)
        _mensagem.value = "Contra-oferta enviada! O clube responderá após a próxima rodada."
    }

    /** Marca (ou desmarca) um jogador do elenco como disponível para venda. */
    fun marcarParaVenda(jogador: Jogador, flag: Boolean) = viewModelScope.launch {
        jogadorRepository.atualizarDisponibilidadeVenda(jogador.id, flag)
    }

    /** Marca (ou desmarca) um jogador do elenco como disponível para empréstimo. */
    fun marcarParaEmprestimo(jogador: Jogador, flag: Boolean) = viewModelScope.launch {
        jogadorRepository.atualizarDisponibilidadeEmprestimo(jogador.id, flag)
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
//  ClubesViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class ClubesViewModel @Inject constructor(
    private val gameDataStore: GameDataStore,
    private val gameRepository: GameRepository,
    private val timeRepository: TimeRepository,
    private val jogadorRepository: JogadorRepository
) : ViewModel() {

    private val _times = MutableStateFlow<List<Time>>(emptyList())
    val times: StateFlow<List<Time>> = _times.asStateFlow()

    private val _timeSelecionado = MutableStateFlow<Time?>(null)
    val timeSelecionado: StateFlow<Time?> = _timeSelecionado.asStateFlow()

    private val _elencoTimeSelecionado = MutableStateFlow<List<Jogador>>(emptyList())
    val elencoTimeSelecionado: StateFlow<List<Jogador>> = _elencoTimeSelecionado.asStateFlow()

    // Histórico de competições do clube selecionado (ligas + copas), por temporada.
    private val _historicoCompeticoes = MutableStateFlow<List<HistoricoTemporada>>(emptyList())
    val historicoCompeticoes: StateFlow<List<HistoricoTemporada>> = _historicoCompeticoes.asStateFlow()

    private val _saldo = MutableStateFlow(0L)
    val saldo: StateFlow<Long> = _saldo.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    private var timeJogadorId = -1
    private var elencoJob: Job? = null

    fun carregar(timeId: Int) {
        timeJogadorId = timeId
        viewModelScope.launch {
            launch {
                timeRepository.observeTodos().collect { todos ->
                    _times.value = todos.filter { it.id != timeId }
                    _saldo.value = todos.find { it.id == timeId }?.saldo ?: 0L
                }
            }
        }
    }

    fun selecionarTime(time: Time) {
        _timeSelecionado.value = time
        _historicoCompeticoes.value = emptyList()
        elencoJob?.cancel()
        elencoJob = viewModelScope.launch {
            jogadorRepository.observeElenco(time.id).collect { elenco ->
                _elencoTimeSelecionado.value = elenco.sortedWith(
                    compareBy({ it.posicao.ordinal }, { -it.forca })
                )
            }
        }
        viewModelScope.launch { carregarHistoricoCompeticoes(time.id) }
    }

    /** Campanhas do clube por competição/temporada (ligas + copas + supercopa). */
    private suspend fun carregarHistoricoCompeticoes(timeId: Int) {
        val ligas = gameRepository.buscarHistoricoDoTime(timeId).map { h ->
            HistoricoTemporada(
                campeonatoId = h.campeonatoId, nomeCampeonato = h.nomeCampeonato,
                temporadaId = h.temporadaId, tipo = h.tipo, posicao = h.posicao,
                vitorias = h.vitorias, empates = h.empates, derrotas = h.derrotas,
                golsPro = h.golsPro, golsContra = h.golsContra, pontos = h.pontos, jogos = h.jogos
            )
        }
        val copaPartidas = gameRepository.buscarHistoricoCopaDoTime(timeId) +
            gameRepository.buscarHistoricoSupercopaDoTime(timeId)
        val copas = copaPartidas.groupBy { it.campeonatoId }.map { (campId, lista) ->
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
                campeonatoId = campId, nomeCampeonato = first.nomeCampeonato,
                temporadaId = first.temporadaId, tipo = "COPA_NACIONAL", posicao = 0,
                vitorias = v, empates = e, derrotas = d, golsPro = gp, golsContra = gc,
                pontos = 0, jogos = lista.size, faseAtingida = faseAtingida
            )
        }
        _historicoCompeticoes.value = (ligas + copas)
            .sortedWith(compareByDescending<HistoricoTemporada> { it.temporadaId }.thenBy { it.nomeCampeonato })
    }

    fun limparTimeSelecionado() {
        _timeSelecionado.value = null
        elencoJob?.cancel()
        _elencoTimeSelecionado.value = emptyList()
        _historicoCompeticoes.value = emptyList()
    }

    fun fazerOferta(jogador: Jogador) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        val time = timeRepository.buscarPorId(timeJogadorId) ?: return@launch
        val preco = CalculadoraMercado.precoCompraUsuario(jogador.valorMercado)
        if (time.saldo < preco) {
            _mensagem.value = "Saldo insuficiente. Necessário: ${formatarValor(preco)}"
            return@launch
        }
        val qtdElenco = jogadorRepository.contarJogadores(timeJogadorId)
        if (qtdElenco >= 35) {
            _mensagem.value = "Elenco cheio (35/35). Venda ou dispense um jogador antes de contratar."
            return@launch
        }
        timeRepository.debitarSaldo(timeJogadorId, preco)
        jogador.timeId?.let { vendedorId ->
            timeRepository.creditarSaldo(vendedorId, preco)
        }
        jogadorRepository.realizarTransferencia(
            oferta = OfertaTransferencia(
                jogadorId = jogador.id,
                timeCompradorId = timeJogadorId,
                timeVendedorId = jogador.timeId,
                valor = preco,
                salarioProposto = jogador.salario,
                contratoAnos = 3
            ),
            temporadaId = save.temporadaId,
            mes = save.mesAtual
        )
        // Se o time vendedor ficou com elenco abaixo de 18, recompõe automaticamente
        if (jogador.timeId != null) {
            gameRepository.recompletarElencoEmergenciaIA(jogador.timeId, save.temporadaId, save.mesAtual)
        }
        _mensagem.value = "${jogador.nomeAbreviado} contratado por ${formatarValor(preco)}!"
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

    // IDs das quatro divisões brasileiras + Argentina A e B + Uruguai
    private var campeonatoAId: Int = -1
    private var campeonatoBId: Int = -1
    private var campeonatoCId: Int = -1
    private var campeonatoDId: Int = -1
    private var campeonatoArgAId: Int = -1
    private var campeonatoArgBId: Int = -1
    private var campeonatoArgClausuraId: Int = -1
    private var campeonatoUruAperturaId: Int = -1
    private var campeonatoUruBId: Int = -1
    private var campeonatoUruClausuraId: Int = -1
    private var campeonatoUruIntermedId: Int = -1
    private var campeonatoUruBCompetId: Int = -1
    private var campeonatoPremierId: Int = -1
    private var campeonatoChampionshipId: Int = -1
    private var campeonatoLaLigaId: Int = -1
    private var campeonatoSerieAId: Int = -1
    private var championsId: Int = -1
    private var europaLeagueId: Int = -1
    private var libertadoresId: Int = -1
    private var copaId: Int = -1

    // timeId do campeão da Copa do Brasil corrente (-1 enquanto não encerrada)
    private val _copaChampeaoTimeId = MutableStateFlow(-1)
    val copaChampeaoTimeId: StateFlow<Int> = _copaChampeaoTimeId.asStateFlow()

    // Divisão selecionada em exibição (1=Série A, 2=B, 3=C, 4=D, 5=Apertura, 6=Segunda Arg, 7=Clausura, 8=Geral Arg, 9=Apertura Uru, 10=Segunda Uru, 11=Intermédio Uru, 12=Clausura Uru, 13=Geral Uru)
    private val _divisaoSelecionada = MutableStateFlow(1)
    val divisaoSelecionada: StateFlow<Int> = _divisaoSelecionada.asStateFlow()

    // Job do collector ativo — cancelado sempre que a divisão muda
    private var coletaJob: kotlinx.coroutines.Job? = null

    fun carregar(campAId: Int, campBId: Int, campCId: Int = -1, campDId: Int = -1, campArgAId: Int = -1, campArgBId: Int = -1, campArgClausuraId: Int = -1, campUruAperturaId: Int = -1, campUruBId: Int = -1, campUruClausuraId: Int = -1, campUruIntermedId: Int = -1, campUruBCompetId: Int = -1, copaIdBrasil: Int = -1, campPremierId: Int = -1, campChampionshipId: Int = -1, campLaLigaId: Int = -1, campSerieAId: Int = -1, campChampionsId: Int = -1, campEuropaId: Int = -1, campLibertadoresId: Int = -1, timeJogadorId: Int = -1) = viewModelScope.launch {
        campeonatoAId = campAId
        campeonatoBId = campBId
        campeonatoCId = campCId
        campeonatoDId = campDId
        campeonatoArgAId = campArgAId
        campeonatoArgBId = campArgBId
        campeonatoArgClausuraId = campArgClausuraId
        campeonatoUruAperturaId = campUruAperturaId
        campeonatoUruBId = campUruBId
        campeonatoUruClausuraId = campUruClausuraId
        campeonatoUruIntermedId = campUruIntermedId
        campeonatoUruBCompetId = campUruBCompetId
        campeonatoPremierId = campPremierId
        campeonatoChampionshipId = campChampionshipId
        campeonatoLaLigaId = campLaLigaId
        campeonatoSerieAId = campSerieAId
        championsId = campChampionsId
        europaLeagueId = campEuropaId
        libertadoresId = campLibertadoresId
        copaId = copaIdBrasil
        // Busca o campeão da Copa do Brasil se já encerrada
        _copaChampeaoTimeId.value = gameRepository.buscarCampeaoCopaSeEncerrada(copaIdBrasil)
        launch { timeRepository.observeTodos().collect { _times.value = it } }
        // Recalcula todas as classificações do zero a partir das partidas reais
        listOf(campAId, campBId, campCId, campDId, campArgAId, campArgBId, campArgClausuraId,
               campUruAperturaId, campUruBId, campUruClausuraId, campUruIntermedId, campUruBCompetId, campPremierId, campChampionshipId, campLaLigaId, campSerieAId, campChampionsId, campEuropaId, campLibertadoresId).filter { it > 0 }.forEach { id ->
            gameRepository.recalcularClassificacao(id)
        }
        // Determina a divisão inicial pelo time do jogador; usa Série A como fallback.
        // Times ingleses têm divisao=11 (Premier) ou 12 (Championship); espanhóis têm
        // divisao=13 (La Liga). Traduz para os códigos de aba 20/21/22 desta tela
        // (11/12/13 já são usados por torneios uruguaios).
        val allTimes = timeRepository.observeTodos().first { it.isNotEmpty() }
        val divTime = if (timeJogadorId > 0) allTimes.find { it.id == timeJogadorId }?.divisao ?: 1 else 1
        val divisaoJogador = when (divTime) {
            11 -> 20
            12 -> 21
            13 -> 22
            14 -> 23
            else -> divTime
        }
        _divisaoSelecionada.value = divisaoJogador
        when (divisaoJogador) {
            8 -> coletarTabelaGeral()
            13 -> coletarTabelaGeralUruguai()
            else -> coletarTabela(campIdParaDivisao(divisaoJogador), divisaoJogador)
        }
    }

    fun selecionarDivisao(divisao: Int) {
        if (_divisaoSelecionada.value == divisao) return
        _divisaoSelecionada.value = divisao
        when (divisao) {
            8 -> coletarTabelaGeral()
            13 -> coletarTabelaGeralUruguai()
            else -> coletarTabela(campIdParaDivisao(divisao), divisao)
        }
    }

    private fun campIdParaDivisao(div: Int) = when (div) {
        1 -> campeonatoAId; 2 -> campeonatoBId; 3 -> campeonatoCId; 4 -> campeonatoDId
        5 -> campeonatoArgAId; 6 -> campeonatoArgBId; 7 -> campeonatoArgClausuraId
        9 -> campeonatoUruAperturaId; 10 -> campeonatoUruBId
        11 -> campeonatoUruIntermedId; 12 -> campeonatoUruClausuraId
        14 -> campeonatoUruBCompetId
        20 -> campeonatoPremierId
        21 -> campeonatoChampionshipId
        22 -> campeonatoLaLigaId
        23 -> campeonatoSerieAId
        30 -> championsId          // Fase de Liga da Champions: tabela única de 36
        31 -> europaLeagueId       // Fase de Liga da Europa League, idem
        // 40 = fase de grupos da Libertadores: 8 tabelas de 4, filtradas pelo
        // rótulo do grupo em ClassificacaoEntity (ver coletarTabela).
        40 -> libertadoresId
        else -> campeonatoArgAId
    }

    private fun coletarTabelaGeralUruguai() {
        coletaJob?.cancel()
        coletaJob = viewModelScope.launch {
            val aId = campeonatoUruAperturaId
            val cId = campeonatoUruClausuraId
            val fluxoA = if (aId > 0) gameRepository.observarTabela(aId) else flowOf(emptyList())
            val fluxoC = if (cId > 0) gameRepository.observarTabela(cId) else flowOf(emptyList())
            combine(fluxoA, fluxoC) { apertura, clausura ->
                (apertura + clausura)
                    .groupBy { it.timeId }
                    .map { (timeId, rows) ->
                        ClassificacaoEntity(
                            campeonatoId = aId,
                            timeId = timeId,
                            grupo = null,
                            pontos     = rows.sumOf { it.pontos },
                            jogos      = rows.sumOf { it.jogos },
                            vitorias   = rows.sumOf { it.vitorias },
                            empates    = rows.sumOf { it.empates },
                            derrotas   = rows.sumOf { it.derrotas },
                            golsPro    = rows.sumOf { it.golsPro },
                            golsContra = rows.sumOf { it.golsContra },
                            saldoGols  = rows.sumOf { it.saldoGols }
                        )
                    }
                    .sortedWith(
                        compareByDescending<ClassificacaoEntity> { it.pontos }
                            .thenByDescending { it.vitorias }
                            .thenByDescending { it.saldoGols }
                            .thenByDescending { it.golsPro }
                    )
            }.collect { merged -> _tabela.value = merged }
        }
    }

    private fun coletarTabelaGeral() {
        coletaJob?.cancel()
        coletaJob = viewModelScope.launch {
            val aId = campeonatoArgAId
            val cId = campeonatoArgClausuraId
            val fluxoA = if (aId > 0) gameRepository.observarTabela(aId) else flowOf(emptyList())
            val fluxoC = if (cId > 0) gameRepository.observarTabela(cId) else flowOf(emptyList())
            combine(fluxoA, fluxoC) { apertura, clausura ->
                (apertura + clausura)
                    .groupBy { it.timeId }
                    .map { (timeId, rows) ->
                        ClassificacaoEntity(
                            campeonatoId = aId,
                            timeId = timeId,
                            grupo = null,
                            pontos     = rows.sumOf { it.pontos },
                            jogos      = rows.sumOf { it.jogos },
                            vitorias   = rows.sumOf { it.vitorias },
                            empates    = rows.sumOf { it.empates },
                            derrotas   = rows.sumOf { it.derrotas },
                            golsPro    = rows.sumOf { it.golsPro },
                            golsContra = rows.sumOf { it.golsContra },
                            saldoGols  = rows.sumOf { it.saldoGols }
                        )
                    }
                    .sortedWith(
                        compareByDescending<ClassificacaoEntity> { it.pontos }
                            .thenByDescending { it.vitorias }
                            .thenByDescending { it.saldoGols }
                            .thenByDescending { it.golsPro }
                    )
            }.collect { merged -> _tabela.value = merged }
        }
    }

    private fun coletarTabela(campId: Int, divisao: Int = _divisaoSelecionada.value) {
        coletaJob?.cancel()
        coletaJob = viewModelScope.launch {
            if (campId <= 0) { _tabela.value = emptyList(); return@launch }
            gameRepository.observarTabela(campId).collect { lista ->
                // 5 e 7 = Apertura/Clausura argentinos; 40 = fase de grupos da
                // Libertadores, que são 8 grupos de 4.
                val ehGrupos = divisao == 5 || divisao == 7 || divisao == 40
                _tabela.value = if (ehGrupos) {
                    lista.sortedWith(
                        compareBy<ClassificacaoEntity> { it.grupo ?: "Z" }
                            .thenByDescending { it.pontos }
                            .thenByDescending { it.vitorias }
                            .thenByDescending { it.saldoGols }
                            .thenByDescending { it.golsPro }
                    )
                } else {
                    lista.sortedWith(
                        compareByDescending<ClassificacaoEntity> { it.pontos }
                            .thenByDescending { it.vitorias }
                            .thenByDescending { it.saldoGols }
                            .thenByDescending { it.golsPro }
                    )
                }
            }
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

    // IDs das quatro divisões + campeonatos argentinos
    private var campeonatoAId: Int = -1
    private var campeonatoBId: Int = -1
    private var campeonatoCId: Int = -1
    private var campeonatoDId: Int = -1
    private var copaId: Int = -1
    private var copaArgId: Int = -1
    private var copaInglaterraId: Int = -1
    private var campeonatoArgAId: Int = -1
    private var campeonatoArgBId: Int = -1
    private var campeonatoArgClausuraId: Int = -1
    private var campeonatoUruAperturaId: Int = -1
    private var campeonatoUruIntermedId: Int = -1
    private var campeonatoUruClausuraId: Int = -1
    private var campeonatoUruBId: Int = -1
    private var campeonatoUruBCompetId: Int = -1
    private var campeonatoPremierId: Int = -1
    private var campeonatoChampionshipId: Int = -1
    private var campeonatoLaLigaId: Int = -1
    private var campeonatoSerieAId: Int = -1
    private var campeonatoAmistososId: Int = -1
    private var championsId: Int = -1
    private var europaLeagueId: Int = -1
    private var libertadoresId: Int = -1

    private val _divisaoSelecionada = MutableStateFlow(1)
    val divisaoSelecionada: StateFlow<Int> = _divisaoSelecionada.asStateFlow()

    private val _divisaoHistoricoSelecionada = MutableStateFlow(0)
    val divisaoHistoricoSelecionada: StateFlow<Int> = _divisaoHistoricoSelecionada.asStateFlow()

    fun carregar(campAId: Int, campBId: Int, campCId: Int = -1, campDId: Int = -1, copaId: Int = -1, copaArgId: Int = -1, campArgAId: Int = -1, campArgBId: Int = -1, campArgClausuraId: Int = -1, campUruApertId: Int = -1, campUruInterId: Int = -1, campUruClausId: Int = -1, campUruBId: Int = -1, campUruBCompetId: Int = -1, campPremierId: Int = -1, campChampionshipId: Int = -1, campLaLigaId: Int = -1, campSerieAId: Int = -1, campAmistosos: Int = -1, copaIng: Int = -1, campChampionsId: Int = -1, campEuropaId: Int = -1, campLibertadoresId: Int = -1, divisaoInicial: Int = 1) = viewModelScope.launch {
        campeonatoAId = campAId
        campeonatoBId = campBId
        campeonatoCId = campCId
        campeonatoDId = campDId
        this@ArtilheirosViewModel.copaId = copaId
        this@ArtilheirosViewModel.copaArgId = copaArgId
        this@ArtilheirosViewModel.copaInglaterraId = copaIng
        campeonatoArgAId = campArgAId
        campeonatoArgBId = campArgBId
        campeonatoArgClausuraId = campArgClausuraId
        campeonatoUruAperturaId = campUruApertId
        campeonatoUruIntermedId = campUruInterId
        campeonatoUruClausuraId = campUruClausId
        campeonatoUruBId = campUruBId
        campeonatoUruBCompetId = campUruBCompetId
        campeonatoPremierId = campPremierId
        campeonatoChampionshipId = campChampionshipId
        campeonatoLaLigaId = campLaLigaId
        campeonatoSerieAId = campSerieAId
        campeonatoAmistososId = campAmistosos
        championsId = campChampionsId
        europaLeagueId = campEuropaId
        libertadoresId = campLibertadoresId
        _divisaoSelecionada.value = divisaoInicial
        coletarArtilheiros(divisaoInicial)
        launch { gameRepository.observarArtilheirosAllTime().collect { _artilheirosAllTime.value = it } }
        launch { gameRepository.observarAssistentesAllTime().collect { _assistentesAllTime.value = it } }
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
        1 -> campeonatoAId; 2 -> campeonatoBId; 3 -> campeonatoCId; 4 -> campeonatoDId
        5 -> copaId; 10 -> copaArgId; 7 -> campeonatoArgBId; 8 -> campeonatoArgAId; 9 -> campeonatoArgClausuraId
        20 -> campeonatoUruAperturaId; 21 -> campeonatoUruIntermedId; 22 -> campeonatoUruClausuraId
        23 -> campeonatoUruBId; 24 -> campeonatoUruBCompetId
        30 -> campeonatoPremierId
        31 -> campeonatoChampionshipId
        32 -> campeonatoLaLigaId
        33 -> copaInglaterraId
        34 -> campeonatoSerieAId
        35 -> championsId   // Champions League (Fase de Liga + mata-mata)
        36 -> europaLeagueId // Europa League, idem
        37 -> libertadoresId // Libertadores (Pré + grupos + mata-mata)
        else -> -1 // div=6,25,26 (combinados) e div=0,100,200,300 (todos) tratados em coletarArtilheiros
    }

    /** País do campeonato de cada divisão do histórico (o `tipo` sozinho não separa países). */
    private fun paisDaDivHistorico(div: Int): String? = when (div) {
        1, 2, 3, 4          -> "Brasil"
        6, 7, 8, 9          -> "Argentina"
        20, 21, 22, 23, 24, 25, 26 -> "Uruguay"
        30, 31, 33          -> "Inglaterra"
        32                  -> "Espanha"
        34                  -> "Italia"
        // A Champions nao pertence a um pais: o campeonato e gravado com
        // pais = Continentes.EUROPA, e e por isso que o historico casa aqui.
        35, 36              -> Continentes.EUROPA
        // A Libertadores e gravada com pais = Continentes.AMERICA_DO_SUL, pelo
        // mesmo motivo: um torneio continental nao pertence a um pais.
        37                  -> Continentes.AMERICA_DO_SUL
        else                -> null
    }

    /** Mapeia divisão → tipo(s) de campeonato para filtrar o histórico */
    private fun tiposDaDiv(div: Int): List<String> = when (div) {
        1 -> listOf("NACIONAL_DIVISAO1")
        2 -> listOf("NACIONAL_DIVISAO2")
        3 -> listOf("NACIONAL_DIVISAO3")
        4 -> listOf("NACIONAL_DIVISAO4")
        5 -> listOf("COPA_NACIONAL")
        6, 8, 9, 25 -> listOf("EXTRANGEIRO_DIVISAO1") // Argentina/Uruguai Primera
        7, 23, 26   -> listOf("EXTRANGEIRO_DIVISAO2") // Argentina/Uruguai Segunda
        20, 21, 22  -> listOf("EXTRANGEIRO_DIVISAO1") // Uruguai Apertura/Intermed/Clausura
        24          -> listOf("EXTRANGEIRO_DIVISAO2") // Uruguai Competencia
        30          -> listOf("NACIONAL_DIVISAO1")    // Premier League (mesmo tipo da Série A)
        31          -> listOf("NACIONAL_DIVISAO2")    // EFL Championship
        32          -> listOf("NACIONAL_DIVISAO1")    // La Liga
        33          -> listOf("COPA_NACIONAL")        // Copa da Inglaterra
        34          -> listOf("NACIONAL_DIVISAO1")    // Serie A italiana
        35          -> listOf("CONTINENTAL")          // Champions League
        36          -> listOf("CONTINENTAL")          // Europa League
        37          -> listOf("CONTINENTAL")          // Libertadores
        else -> emptyList()
    }

    /** div == 0 → todas as competições da temporada (multi-query) */
    private fun coletarArtilheiros(div: Int) = viewModelScope.launch {
        val useMulti: List<Int>? = when (div) {
            // Todos os países
            0    -> listOf(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, copaId, copaArgId,
                           campeonatoArgAId, campeonatoArgBId, campeonatoArgClausuraId,
                           campeonatoUruAperturaId, campeonatoUruIntermedId, campeonatoUruClausuraId,
                           campeonatoUruBId, campeonatoUruBCompetId,
                           campeonatoPremierId, campeonatoChampionshipId, campeonatoLaLigaId,
                           campeonatoSerieAId, copaInglaterraId, campeonatoAmistososId,
                           championsId, europaLeagueId, libertadoresId).filter { it > 0 }
            // Combinados por país
            100  -> listOf(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, copaId).filter { it > 0 }
            200  -> listOf(campeonatoArgAId, campeonatoArgBId, campeonatoArgClausuraId, copaArgId).filter { it > 0 }
            300  -> listOf(campeonatoUruAperturaId, campeonatoUruIntermedId, campeonatoUruClausuraId,
                           campeonatoUruBId, campeonatoUruBCompetId).filter { it > 0 }
            // Argentina Primera combinado (Apertura + Clausura)
            6    -> listOf(campeonatoArgAId, campeonatoArgClausuraId).filter { it > 0 }
            // Uruguai Primera combinado (Apertura + Intermediário + Clausura)
            25   -> listOf(campeonatoUruAperturaId, campeonatoUruIntermedId, campeonatoUruClausuraId).filter { it > 0 }
            // Uruguai Segunda combinado (Segunda + Competencia)
            26   -> listOf(campeonatoUruBId, campeonatoUruBCompetId).filter { it > 0 }
            else -> null
        }
        if (useMulti != null) {
            launch { gameRepository.observarArtilheirosMulti(useMulti).collect { _artilheiros.value = it } }
            launch { gameRepository.observarAssistentesMulti(useMulti).collect { _assistentes.value = it } }
        } else {
            val campId = campIdParaDivisao(div)
            launch { gameRepository.observarArtilheiros(campId).collect { _artilheiros.value = it } }
            launch { gameRepository.observarAssistentes(campId).collect { _assistentes.value = it } }
        }
    }

    /** div == 0/100/200/300 → usa AllTime sem filtro (já populado no carregar); caso contrário filtra por tipo */
    private fun coletarHistoricoFiltrado(div: Int) = viewModelScope.launch {
        if (div in listOf(0, 100, 200, 300)) {
            _artilheirosHistorico.value = emptyList()
            _assistentesHistorico.value = emptyList()
            return@launch
        }
        when (div) {
            5 -> {
                launch { gameRepository.observarArtilheirosHistoricoCopaBrasil().collect { _artilheirosHistorico.value = it } }
                launch { gameRepository.observarAssistentesHistoricoCopaBrasil().collect { _assistentesHistorico.value = it } }
            }
            10 -> {
                launch { gameRepository.observarArtilheirosHistoricoCopaArgentina().collect { _artilheirosHistorico.value = it } }
                launch { gameRepository.observarAssistentesHistoricoCopaArgentina().collect { _assistentesHistorico.value = it } }
            }
            else -> {
                val tipos = tiposDaDiv(div)
                val pais = paisDaDivHistorico(div)
                if (pais != null) {
                    // Filtra por país + tipo — evita misturar Brasil/Premier/La Liga/Serie A
                    // (que compartilham NACIONAL_DIVISAO1) ou as copas nacionais entre países.
                    launch { gameRepository.observarArtilheirosHistoricoPaisTipo(pais, tipos).collect { _artilheirosHistorico.value = it } }
                    launch { gameRepository.observarAssistentesHistoricoPaisTipo(pais, tipos).collect { _assistentesHistorico.value = it } }
                } else {
                    launch { gameRepository.observarArtilheirosHistoricoFiltrado(tipos).collect { _artilheirosHistorico.value = it } }
                    launch { gameRepository.observarAssistentesHistoricoFiltrado(tipos).collect { _assistentesHistorico.value = it } }
                }
            }
        }
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
            when (div) {
                0    -> lista
                100  -> lista.filter { it.divisao in 1..6 }
                200  -> lista.filter { it.divisao in 7..10 || it.divisao == 17 }
                300  -> lista.filter { it.divisao in 11..16 }
                400  -> lista.filter { it.divisao in 20..21 || it.divisao == 23 || it.divisao == 27 }   // Inglaterra (Premier + Championship + Copa + Supercopa)
                500  -> lista.filter { it.divisao == 22 || it.divisao == 25 || it.divisao == 28 }       // Espanha (La Liga + Copa + Supercopa)
                600  -> lista.filter { it.divisao == 24 || it.divisao == 26 || it.divisao == 29 }       // Italia (Serie A + Copa + Supercopa)
                700  -> lista.filter { it.divisao in 30..33 }                                          // Europa (Champions, Europa League, Supercopa da UEFA e futura Conference)
                800  -> lista.filter { it.divisao in 34..35 }                                          // América do Sul (Libertadores e futura Sul-Americana)
                else -> lista.filter { it.divisao == div }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selecionarDivisao(div: Int) { _divisaoSelecionada.value = div }
}

// ═══════════════════════════════════════════════════════════
//  MetasViewModel — meta de temporada de cada time
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class MetasViewModel @Inject constructor(
    private val timeRepository: TimeRepository,
    private val gameDataStore: GameDataStore
) : ViewModel() {
    val times: StateFlow<List<Time>> = timeRepository.observeTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** País do time do jogador — usado para pré-selecionar a aba de país. */
    val paisJogador: StateFlow<String?> = combine(times, gameDataStore.saveState) { lista, save ->
        lista.find { it.id == save.timeIdJogador }?.pais
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

// ═══════════════════════════════════════════════════════════
//  ConquistasViewModel — títulos conquistados por um time
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class ConquistasViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val timeRepository: TimeRepository
) : ViewModel() {

    private val _timeId = MutableStateFlow(0)

    private val _time = MutableStateFlow<br.com.managerfoot.domain.model.Time?>(null)
    val time: StateFlow<br.com.managerfoot.domain.model.Time?> = _time.asStateFlow()

    /**
     * Lista de conquistas do time, ordenadas por ano DESC.
     * Cada item é um HallDaFamaEntity onde campeaoTimeId == timeId.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val conquistas: StateFlow<List<br.com.managerfoot.data.database.entities.HallDaFamaEntity>> =
        _timeId
            .filter { it > 0 }
            .flatMapLatest { id -> gameRepository.observarConquistasDoTime(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Vice-campeonatos do time (opcional, exibido em seção secundária). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val vices: StateFlow<List<br.com.managerfoot.data.database.entities.HallDaFamaEntity>> =
        _timeId
            .filter { it > 0 }
            .flatMapLatest { id -> gameRepository.observarVicesDoTime(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun carregar(timeId: Int) {
        _timeId.value = timeId
        viewModelScope.launch {
            _time.value = timeRepository.buscarPorId(timeId)
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  RankingTecnicosViewModel — ranking de técnicos por pontos
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class RankingTecnicosViewModel @Inject constructor(
    private val tecnicoRepository: TecnicoRepository,
    private val timeRepository: TimeRepository
) : ViewModel() {

    val ranking: StateFlow<List<br.com.managerfoot.data.database.entities.TecnicoEntity>> =
        tecnicoRepository.observeRanking()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val livres: StateFlow<List<br.com.managerfoot.data.database.entities.TecnicoEntity>> =
        tecnicoRepository.observeLivres()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mapa timeId → Time (escudo + nome) para enriquecer a lista. */
    val timesPorId: StateFlow<Map<Int, br.com.managerfoot.domain.model.Time>> =
        timeRepository.observeTodos()
            .map { lista -> lista.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

// ═══════════════════════════════════════════════════════════
//  HistoricoTecnicoViewModel — carreira completa de um técnico
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class HistoricoTecnicoViewModel @Inject constructor(
    private val tecnicoRepository: TecnicoRepository
) : ViewModel() {

    private val _tecnico = MutableStateFlow<br.com.managerfoot.data.database.entities.TecnicoEntity?>(null)
    val tecnico: StateFlow<br.com.managerfoot.data.database.entities.TecnicoEntity?> = _tecnico.asStateFlow()

    private val _passagens = MutableStateFlow<List<br.com.managerfoot.data.database.entities.PassagemTecnicoEntity>>(emptyList())
    val passagens: StateFlow<List<br.com.managerfoot.data.database.entities.PassagemTecnicoEntity>> = _passagens.asStateFlow()

    private val _titulos = MutableStateFlow<List<br.com.managerfoot.data.database.entities.HallDaFamaEntity>>(emptyList())
    val titulos: StateFlow<List<br.com.managerfoot.data.database.entities.HallDaFamaEntity>> = _titulos.asStateFlow()

    private val _carregando = MutableStateFlow(false)
    val carregando: StateFlow<Boolean> = _carregando.asStateFlow()

    fun carregar(tecnicoId: Int) {
        viewModelScope.launch {
            _carregando.value = true
            _tecnico.value = tecnicoRepository.buscarPorId(tecnicoId)
            _passagens.value = tecnicoRepository.buscarPassagens(tecnicoId)
            _titulos.value = tecnicoRepository.buscarTitulos(tecnicoId)
            _carregando.value = false
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  HistoricoJogadorViewModel — carreira completa de um jogador
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class HistoricoJogadorViewModel @Inject constructor(
    private val jogadorRepository: JogadorRepository
) : ViewModel() {

    private val _jogador = MutableStateFlow<br.com.managerfoot.domain.model.Jogador?>(null)
    val jogador: StateFlow<br.com.managerfoot.domain.model.Jogador?> = _jogador.asStateFlow()

    private val _historico = MutableStateFlow<HistoricoCarreira?>(null)
    val historico: StateFlow<HistoricoCarreira?> = _historico.asStateFlow()

    private val _carregando = MutableStateFlow(false)
    val carregando: StateFlow<Boolean> = _carregando.asStateFlow()

    fun carregar(jogadorId: Int) {
        viewModelScope.launch {
            _carregando.value = true
            _jogador.value = jogadorRepository.buscarPorId(jogadorId)
            _historico.value = jogadorRepository.buscarHistoricoCarreira(jogadorId)
            _carregando.value = false
        }
    }
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

    private val _partidasArg = MutableStateFlow<List<CopaPartidaDto>>(emptyList())
    val partidasArg: StateFlow<List<CopaPartidaDto>> = _partidasArg.asStateFlow()

    private val _partidasIng = MutableStateFlow<List<CopaPartidaDto>>(emptyList())
    val partidasIng: StateFlow<List<CopaPartidaDto>> = _partidasIng.asStateFlow()

    private val _partidasEsp = MutableStateFlow<List<CopaPartidaDto>>(emptyList())
    val partidasEsp: StateFlow<List<CopaPartidaDto>> = _partidasEsp.asStateFlow()

    private val _partidasIta = MutableStateFlow<List<CopaPartidaDto>>(emptyList())
    val partidasIta: StateFlow<List<CopaPartidaDto>> = _partidasIta.asStateFlow()

    // Mata-mata da Champions: play-off + oitavas às finais. A Fase de Liga não
    // entra aqui — ela é uma tabela, e aparece na tela de Classificação.
    private val _partidasChampions = MutableStateFlow<List<CopaPartidaDto>>(emptyList())
    val partidasChampions: StateFlow<List<CopaPartidaDto>> = _partidasChampions.asStateFlow()

    // Mata-mata da Europa League, pela mesma porta: observeCopaPartidas filtra
    // fase e confrontoId não-nulos, então a Fase de Liga não entra no chaveamento.
    private val _partidasEuropa = MutableStateFlow<List<CopaPartidaDto>>(emptyList())
    val partidasEuropa: StateFlow<List<CopaPartidaDto>> = _partidasEuropa.asStateFlow()

    // Chaveamento da Libertadores: as três fases da Pré-Libertadores e o mata-mata
    // das oitavas à final. A fase de grupos não entra — observeCopaPartidas filtra
    // fase e confrontoId não-nulos, e as partidas de grupo têm fase = null.
    private val _partidasLibertadores = MutableStateFlow<List<CopaPartidaDto>>(emptyList())
    val partidasLibertadores: StateFlow<List<CopaPartidaDto>> = _partidasLibertadores.asStateFlow()

    fun carregar(copaId: Int) = viewModelScope.launch {
        if (copaId <= 0) return@launch
        gameRepository.observarCopaPartidas(copaId).collect { _partidas.value = it }
    }

    fun carregarTodas(
        copaId: Int, copaArgId: Int, copaInglaterraId: Int,
        copaEspanholaId: Int = -1, copaItalianaId: Int = -1,
        championsId: Int = -1,
        europaLeagueId: Int = -1,
        libertadoresId: Int = -1
    ) = viewModelScope.launch {
        if (copaId > 0) {
            launch { gameRepository.observarCopaPartidas(copaId).collect { _partidas.value = it } }
        }
        if (copaArgId > 0) {
            launch { gameRepository.observarCopaPartidas(copaArgId).collect { _partidasArg.value = it } }
        }
        if (copaInglaterraId > 0) {
            launch { gameRepository.observarCopaPartidas(copaInglaterraId).collect { _partidasIng.value = it } }
        }
        if (copaEspanholaId > 0) {
            launch { gameRepository.observarCopaPartidas(copaEspanholaId).collect { _partidasEsp.value = it } }
        }
        if (copaItalianaId > 0) {
            launch { gameRepository.observarCopaPartidas(copaItalianaId).collect { _partidasIta.value = it } }
        }
        if (championsId > 0) {
            launch { gameRepository.observarCopaPartidas(championsId).collect { _partidasChampions.value = it } }
        }
        if (europaLeagueId > 0) {
            launch { gameRepository.observarCopaPartidas(europaLeagueId).collect { _partidasEuropa.value = it } }
        }
        if (libertadoresId > 0) {
            launch { gameRepository.observarCopaPartidas(libertadoresId).collect { _partidasLibertadores.value = it } }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  RankingGeralViewModel
// ═══════════════════════════════════════════════════════════
/** Mapeia país → continente para os recortes do ranking. Delega ao domínio,
 *  que é a mesma fonte usada para decidir o escopo das regras de pontuação. */
fun continenteDePais(pais: String): String = Continentes.dePais(pais)

/** Linha do ranking para a UI, com país e continente resolvidos. */
data class RankingGeralUi(
    val entry: RankingGeralEntity,
    val pais: String,
    val continente: String
)

@HiltViewModel
class RankingGeralViewModel @Inject constructor(
    gameRepository: GameRepository,
    gameDataStore: GameDataStore,
    private val timeRepository: TimeRepository
) : ViewModel() {

    val ranking: StateFlow<List<RankingGeralUi>> =
        gameRepository.observarRankingGeralComPais()
            .map { lista ->
                lista.map { RankingGeralUi(it.ranking, it.pais, continenteDePais(it.pais)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // País do time do jogador — usado como recorte padrão ao abrir a tela.
    val paisJogador: StateFlow<String?> =
        flow {
            val save = gameDataStore.saveState.first()
            emit(timeRepository.buscarPorId(save.timeIdJogador)?.pais)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Ranking CONMEBOL (por colocação nas competições) ──────────────
    // A depreciação anual depende do ano corrente do save, então o flow só é
    // montado depois de ler o ano; antes disso a lista fica vazia.
    val rankingConmebol: StateFlow<List<RankingConmebolLinha>> =
        flow {
            val ano = gameDataStore.saveState.first().anoAtual
            emitAll(gameRepository.observarRankingConmebol(ano))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Ranking Nacional de Clubes (CBF) ──────────────────────────────
    // Conjunto POR PAÍS do Brasil do ranking por colocação: o peso do ano (5/4/3/2/1)
    // depende do ano corrente do save, como no CONMEBOL.
    val rankingBrasileiro: StateFlow<List<RankingBrasileiroLinha>> =
        flow {
            val ano = gameDataStore.saveState.first().anoAtual
            emitAll(gameRepository.observarRankingBrasileiro(ano))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Coeficiente de Clubes da UEFA, usado quando o recorte é a Europa.
    // Fica vazio enquanto as competições da UEFA não existirem no jogo.
    val rankingEuropa: StateFlow<List<RankingEuropaLinha>> =
        flow {
            val ano = gameDataStore.saveState.first().anoAtual
            emitAll(gameRepository.observarRankingEuropa(ano))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Ranking Mundial de Clubes (IFFHS), usado no recorte Mundo. Não vem de tabela:
    // é derivado das partidas da janela de 12 meses na leitura.
    val rankingMundial: StateFlow<List<RankingMundialLinha>> =
        flow {
            val ano = gameDataStore.saveState.first().anoAtual
            emitAll(gameRepository.observarRankingMundial(ano))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
            save.campeonatoArgAId -> "Apertura"
            save.campeonatoArgClausuraId -> "Clausura"
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

        // Supercopa Rei da temporada corrente
        if (save.supercopaId > 0) {
            val partidas = gameRepository.buscarPartidasDaEquipe(save.supercopaId, timeId)
            if (partidas.isNotEmpty()) {
                val jogadores = gameRepository.buscarEstatisticasJogadoresPorCampeonato(save.supercopaId, timeId)
                var v = 0; var e = 0; var d = 0; var gp = 0; var gc = 0
                for (p in partidas) {
                    val ehCasa = p.timeCasaId == timeId
                    val gf = if (ehCasa) p.golsCasa ?: 0 else p.golsFora ?: 0
                    val ga = if (ehCasa) p.golsFora ?: 0 else p.golsCasa ?: 0
                    gp += gf; gc += ga
                    when { gf > ga -> v++; gf < ga -> d++; else -> e++ }
                }
                temporadaList.add(
                    TemporadaCompeticao(
                        campeonatoId = save.supercopaId,
                        nome         = "Supercopa Rei",
                        vitorias     = v,
                        empates      = e,
                        derrotas     = d,
                        golsPro      = gp,
                        golsContra   = gc,
                        pontos       = 0,
                        jogos        = partidas.size,
                        jogadores    = jogadores,
                        ehCopa       = true,
                        faseAtingida = null
                    )
                )
            }
        }

        // Copa Argentina da temporada corrente
        if (save.copaArgId > 0) {
            val partidas = gameRepository.buscarPartidasDaEquipe(save.copaArgId, timeId)
            if (partidas.isNotEmpty()) {
                val jogadores = gameRepository.buscarEstatisticasJogadoresPorCampeonato(save.copaArgId, timeId)
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
                        campeonatoId = save.copaArgId,
                        nome         = "Copa da Argentina",
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

        // Histórico da Supercopa Rei
        val supercopaHistoricoPartidas = gameRepository.buscarHistoricoSupercopaDoTime(timeId)
        val supercopaEntries = supercopaHistoricoPartidas
            .groupBy { it.campeonatoId }
            .map { (campId, lista) ->
                var v = 0; var e = 0; var d = 0; var gp = 0; var gc = 0
                for (p in lista) {
                    val ehCasa = p.timeCasaId == timeId
                    val gf = if (ehCasa) p.golsCasa ?: 0 else p.golsFora ?: 0
                    val ga = if (ehCasa) p.golsFora ?: 0 else p.golsCasa ?: 0
                    gp += gf; gc += ga
                    when { gf > ga -> v++; gf < ga -> d++; else -> e++ }
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
                    faseAtingida   = null
                )
            }

        // Liga Argentina: combina Apertura + Clausura por temporada
        val argLigaEntries = ligaEntries
            .filter { it.tipo == "EXTRANGEIRO_DIVISAO1" }
            .groupBy { it.temporadaId }
            .map { (tempId, lista) ->
                val ano = Regex("\\d{4}").find(lista.first().nomeCampeonato)?.value ?: ""
                HistoricoTemporada(
                    campeonatoId   = -tempId,
                    nomeCampeonato = "Liga Argentina $ano",
                    temporadaId    = tempId,
                    tipo           = "LIGA_ARGENTINA",
                    posicao        = 0,
                    vitorias       = lista.sumOf { it.vitorias },
                    empates        = lista.sumOf { it.empates },
                    derrotas       = lista.sumOf { it.derrotas },
                    golsPro        = lista.sumOf { it.golsPro },
                    golsContra     = lista.sumOf { it.golsContra },
                    pontos         = lista.sumOf { it.pontos },
                    jogos          = lista.sumOf { it.jogos }
                )
            }

        _historicoStats.value = (ligaEntries + copaEntries + supercopaEntries + argLigaEntries).sortedByDescending { it.temporadaId }

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

    private val _mesAtual = MutableStateFlow(1)
    val mesAtual: StateFlow<Int> = _mesAtual.asStateFlow()

    private val _anoAtual = MutableStateFlow(2026)
    val anoAtual: StateFlow<Int> = _anoAtual.asStateFlow()

    private val _receitasPartidas = MutableStateFlow<List<br.com.managerfoot.data.dao.CalendarioPartidaDto>>(emptyList())
    val receitasPartidas: StateFlow<List<br.com.managerfoot.data.dao.CalendarioPartidaDto>> = _receitasPartidas.asStateFlow()

    private val _transferencias = MutableStateFlow<List<br.com.managerfoot.data.dao.TransferenciaDetalhe>>(emptyList())
    val transferencias: StateFlow<List<br.com.managerfoot.data.dao.TransferenciaDetalhe>> = _transferencias.asStateFlow()

    private val _despesasMensais = MutableStateFlow<List<br.com.managerfoot.data.database.entities.FinancaEntity>>(emptyList())
    val despesasMensais: StateFlow<List<br.com.managerfoot.data.database.entities.FinancaEntity>> = _despesasMensais.asStateFlow()

    private val _compras = MutableStateFlow<List<br.com.managerfoot.data.dao.TransferenciaDetalhe>>(emptyList())
    val compras: StateFlow<List<br.com.managerfoot.data.dao.TransferenciaDetalhe>> = _compras.asStateFlow()

    private val _saldoAtual = MutableStateFlow(0L)
    val saldoAtual: StateFlow<Long> = _saldoAtual.asStateFlow()

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
            listOf(save.campeonatoId, save.copaId, save.supercopaId).filter { it > 0 }
        )
        val ordemRef = (proximaPartida ?: ultimosResultados.firstOrNull())?.ordemGlobal ?: 0
        _mesAtual.value = if (ordemRef > 0) ordemGlobalParaMes(ordemRef) else save.mesAtual
        _anoAtual.value = save.anoAtual
        // Carrega receitas de partidas em casa
        launch {
            gameRepository.observarReceitasPartidas(timeId)
                .collect { lista -> _receitasPartidas.value = lista }
        }
        // Carrega vendas de jogadores do time
        launch {
            jogadorRepository.observeVendasDoTime(timeId)
                .collect { lista -> _transferencias.value = lista }
        }
        // Carrega histórico de despesas mensais
        launch {
            gameRepository.observarFinancasMensais(timeId)
                .collect { lista -> _despesasMensais.value = lista }
        }
        // Carrega compras de jogadores
        launch {
            jogadorRepository.observeComprasDoTime(timeId)
                .collect { lista -> _compras.value = lista }
        }
        // Observa saldo em caixa do time de forma reativa
        launch {
            gameRepository.observarSaldoTime(timeId)
                .collect { saldo -> _saldoAtual.value = saldo }
        }
    }
}

// ══════════════════════════════════════════════════════
//  EstadioViewModel
// ══════════════════════════════════════════════════════
@HiltViewModel
class EstadioViewModel @Inject constructor(
    private val estadioRepository: br.com.managerfoot.data.repository.EstadioRepository,
    private val timeRepository: TimeRepository,
    private val gameDataStore: GameDataStore,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _estadio = MutableStateFlow<br.com.managerfoot.data.database.entities.EstadioEntity?>(null)
    val estadio: StateFlow<br.com.managerfoot.data.database.entities.EstadioEntity?> = _estadio.asStateFlow()

    private val _time = MutableStateFlow<Time?>(null)
    val time: StateFlow<Time?> = _time.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    // Projeção de público/receita por jogo em casa (adversário neutro nível 5),
    // com os preços por setor atuais — atualiza ao carregar e ao mudar preços.
    private val _projecao = MutableStateFlow(0 to 0L)
    val projecao: StateFlow<Pair<Int, Long>> = _projecao.asStateFlow()

    fun carregar(timeId: Int) = viewModelScope.launch {
        _estadio.value = estadioRepository.buscarOuCriar(timeId)
        _time.value = timeRepository.buscarPorId(timeId)
        recalcularProjecao()
    }

    private fun recalcularProjecao() {
        val e = _estadio.value; val t = _time.value
        _projecao.value = if (e != null && t != null) {
            br.com.managerfoot.domain.engine.MotorFinanceiro.calcularPublicoSetorizado(
                t, 5, null,
                br.com.managerfoot.data.database.entities.EstadioEntity.CAPACIDADE_ARQUIBANCADA[e.nivelArquibancada],
                br.com.managerfoot.data.database.entities.EstadioEntity.CAPACIDADE_CADEIRA[e.nivelCadeira],
                br.com.managerfoot.data.database.entities.EstadioEntity.CAPACIDADE_CAMAROTE[e.nivelCamarote],
                e.precoArquibancada, e.precoCadeira, e.precoCamarote
            )
        } else 0 to 0L
    }

    /** Salva os novos preços por setor (centavos) e atualiza a projeção. */
    fun atualizarPrecos(timeId: Int, precoArq: Long, precoCad: Long, precoCam: Long) = viewModelScope.launch {
        _estadio.value = estadioRepository.atualizarPrecos(timeId, precoArq, precoCad, precoCam)
        recalcularProjecao()
        _mensagem.value = "Preços de ingresso atualizados."
    }

    fun upgradeSetor(timeId: Int, setor: Int) = viewModelScope.launch {
        val custo = estadioRepository.upgradeSetor(timeId, setor)
        if (custo != null) {
            val save = gameDataStore.saveState.first()
            gameRepository.registrarUpgradeEstadio(
                timeId      = timeId,
                temporadaId = save.temporadaId,
                mes         = save.mesAtual,
                custo       = custo
            )
            _estadio.value = estadioRepository.buscarOuCriar(timeId)
            _time.value = timeRepository.buscarPorId(timeId)
            recalcularProjecao()
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
    private val jogadorRepository: br.com.managerfoot.data.repository.JogadorRepository,
    private val timeRepository: TimeRepository,
    private val gameDataStore: GameDataStore
) : ViewModel() {

    companion object {
        const val CUSTO_FORMACAO = 1_000_000_00L  // R$ 1.000.000 por 2 jogadores
        const val QTD_FORMACAO = 2
    }

    private val _juniores = MutableStateFlow<List<br.com.managerfoot.domain.model.Jogador>>(emptyList())
    val juniores: StateFlow<List<br.com.managerfoot.domain.model.Jogador>> = _juniores.asStateFlow()

    private val _jogadorSelecionado = MutableStateFlow<br.com.managerfoot.domain.model.Jogador?>(null)
    val jogadorSelecionado: StateFlow<br.com.managerfoot.domain.model.Jogador?> = _jogadorSelecionado.asStateFlow()

    private val _saldo = MutableStateFlow(0L)
    val saldo: StateFlow<Long> = _saldo.asStateFlow()

    private val _formando = MutableStateFlow(false)
    val formando: StateFlow<Boolean> = _formando.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    private var timeIdAtual: Int = -1

    fun carregar(timeId: Int) = viewModelScope.launch {
        timeIdAtual = timeId
        launch { _saldo.value = timeRepository.buscarPorId(timeId)?.saldo ?: 0L }
        jogadorRepository.observeJuniores(timeId).collect { _juniores.value = it }
    }

    /** Forma [QTD_FORMACAO] novos jogadores na base gastando [CUSTO_FORMACAO]. */
    fun formarJuniores() = viewModelScope.launch {
        if (timeIdAtual <= 0 || _formando.value) return@launch
        _formando.value = true
        val save = gameDataStore.saveState.first()
        val ok = jogadorRepository.formarJuniores(
            timeId = timeIdAtual,
            temporadaId = save.temporadaId,
            mes = save.mesAtual,
            quantidade = QTD_FORMACAO,
            custo = CUSTO_FORMACAO
        )
        _saldo.value = timeRepository.buscarPorId(timeIdAtual)?.saldo ?: _saldo.value
        _mensagem.value = if (ok)
            "$QTD_FORMACAO novos jogadores formados na base! (−R$ 1.000.000)"
        else
            "Saldo insuficiente para formar jogadores (custo: R$ 1.000.000)."
        _formando.value = false
    }

    fun selecionarJogador(jogador: br.com.managerfoot.domain.model.Jogador?) {
        _jogadorSelecionado.value = jogador
    }

    fun promoverJunior(jogadorId: Int, nomeAbrev: String) = viewModelScope.launch {
        if (timeIdAtual > 0) {
            val qtdElenco = jogadorRepository.contarJogadores(timeIdAtual)
            if (qtdElenco >= 35) {
                _mensagem.value = "Elenco cheio (35/35). Não é possível promover $nomeAbrev."
                return@launch
            }
        }
        jogadorRepository.promoverJunior(jogadorId)
        _mensagem.value = "$nomeAbrev promovido ao elenco principal!"
    }

    fun dispensarJunior(jogadorId: Int, nomeAbrev: String) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        jogadorRepository.dispensarJunior(jogadorId, save.temporadaId, save.mesAtual)
        _mensagem.value = "$nomeAbrev dispensado para o mercado livre."
    }

    fun limparMensagem() { _mensagem.value = null }
}

// ══════════════════════════════════════════════════════
//  RodadaViewModel
// ══════════════════════════════════════════════════════
@HiltViewModel
class RodadaViewModel @Inject constructor(
    private val partidaDao: PartidaDao
) : ViewModel() {

    private val _partidas = MutableStateFlow<List<CalendarioPartidaDto>>(emptyList())
    val partidas: StateFlow<List<CalendarioPartidaDto>> = _partidas.asStateFlow()

    // knockout matches for Apertura/Clausura mata-mata view
    private val _knockoutPartidas = MutableStateFlow<List<CalendarioPartidaDto>>(emptyList())
    val knockoutPartidas: StateFlow<List<CalendarioPartidaDto>> = _knockoutPartidas.asStateFlow()

    private val _divisaoSelecionada = MutableStateFlow(1)
    val divisaoSelecionada: StateFlow<Int> = _divisaoSelecionada.asStateFlow()

    private val _rodadaSelecionada = MutableStateFlow(1)
    val rodadaSelecionada: StateFlow<Int> = _rodadaSelecionada.asStateFlow()

    private val _maxRodada = MutableStateFlow(38)
    val maxRodada: StateFlow<Int> = _maxRodada.asStateFlow()

    /**
     * Primeira rodada que a divisão selecionada ocupa. É 1 em quase tudo, mas a
     * fase de grupos da Libertadores começa na 11: as rodadas 1 a 6 são da
     * Pré-Libertadores e a partir da 21 é mata-mata, e nenhuma das duas pertence
     * à aba de grupos.
     */
    private val _rodadaMinima = MutableStateFlow(1)
    val rodadaMinima: StateFlow<Int> = _rodadaMinima.asStateFlow()

    private var campeonatoAId = -1
    private var campeonatoBId = -1
    private var campeonatoCId = -1
    private var campeonatoDId = -1
    private var campeonatoArgAId = -1
    private var campeonatoArgBId = -1
    private var campeonatoArgClausuraId = -1
    private var campeonatoUruAperturaId = -1
    private var campeonatoUruIntermedId = -1
    private var campeonatoUruClausuraId = -1
    private var campeonatoUruBId = -1
    private var campeonatoUruBCompetId = -1
    private var campeonatoPremierId = -1
    private var campeonatoChampionshipId = -1
    private var campeonatoLaLigaId = -1
    private var campeonatoSerieAId = -1
    private var campeonatoChampionsId = -1
    private var campeonatoEuropaId = -1
    private var campeonatoLibertadoresId = -1

    private var coletarJob: Job? = null

    fun carregar(campAId: Int, campBId: Int, campCId: Int, campDId: Int, campArgAId: Int = -1, campArgBId: Int = -1, campArgClausuraId: Int = -1,
                 campUruApertId: Int = -1, campUruIntermedId: Int = -1, campUruClausId: Int = -1, campUruBId: Int = -1, campUruBCompetId: Int = -1,
                 campPremierId: Int = -1, campChampionshipId: Int = -1, campLaLigaId: Int = -1, campSerieAId: Int = -1,
                 campChampionsId: Int = -1, campEuropaId: Int = -1,
                 campLibertadoresId: Int = -1) = viewModelScope.launch {
        campeonatoAId = campAId
        campeonatoBId = campBId
        campeonatoCId = campCId
        campeonatoDId = campDId
        campeonatoArgAId = campArgAId
        campeonatoArgBId = campArgBId
        campeonatoArgClausuraId = campArgClausuraId
        campeonatoUruAperturaId = campUruApertId
        campeonatoUruIntermedId = campUruIntermedId
        campeonatoUruClausuraId = campUruClausId
        campeonatoUruBId = campUruBId
        campeonatoUruBCompetId = campUruBCompetId
        campeonatoPremierId = campPremierId
        campeonatoChampionshipId = campChampionshipId
        campeonatoLaLigaId = campLaLigaId
        campeonatoSerieAId = campSerieAId
        campeonatoChampionsId = campChampionsId
        campeonatoEuropaId = campEuropaId
        campeonatoLibertadoresId = campLibertadoresId
        atualizarMaxEColetar()
    }

    fun selecionarDivisao(divisao: Int) = viewModelScope.launch {
        if (_divisaoSelecionada.value == divisao) return@launch
        _divisaoSelecionada.value = divisao
        _rodadaSelecionada.value = 1
        atualizarMaxEColetar()
    }

    fun selecionarRodada(rodada: Int) = viewModelScope.launch {
        if (_rodadaSelecionada.value == rodada) return@launch
        _rodadaSelecionada.value = rodada
        coletar(campIdAtual(), rodada)
    }

    private suspend fun atualizarMaxEColetar() {
        val div = _divisaoSelecionada.value
        // Divisões de mata-mata, que compartilham o campeonatoId da fase anterior:
        // 8 e 9 = Apertura/Clausura argentinos, 31 = play-off e mata-mata da Champions,
        // 33 = o mesmo na Europa League.
        if (div == 8 || div == 9 || div == 31 || div == 33 || div == 41) {
            val campId = when (div) {
                8    -> campeonatoArgAId
                9    -> campeonatoArgClausuraId
                33   -> campeonatoEuropaId
                41   -> campeonatoLibertadoresId
                else -> campeonatoChampionsId
            }
            _partidas.value = emptyList()
            coletarJob?.cancel()
            coletarJob = viewModelScope.launch {
                partidaDao.observeArgKnockout(campId).collect { _knockoutPartidas.value = it }
            }
            return
        }
        val campId = campIdAtual()
        if (campId <= 0) {
            _partidas.value = emptyList(); _maxRodada.value = 38; _rodadaMinima.value = 1; return
        }
        val (minimaDaDivisao, maximaDaDivisao) = faixaDeRodadas(div, campId)
        _rodadaMinima.value = minimaDaDivisao
        _maxRodada.value = maximaDaDivisao
        val rodada = _rodadaSelecionada.value.coerceIn(minimaDaDivisao, maximaDaDivisao)
        _rodadaSelecionada.value = rodada
        coletar(campId, rodada)
    }

    /**
     * Faixa de rodadas que a divisão ocupa dentro do seu campeonato.
     *
     * O caso geral é 1 até a última rodada que existe. A fase de grupos da
     * Libertadores é a exceção: ela ocupa uma faixa no MEIO do campeonato, porque
     * a Pré-Libertadores vem antes e o mata-mata depois, todos no mesmo
     * campeonatoId. Sem esta faixa a aba "Grupos" abriria na rodada 1, que é um
     * jogo da Pré.
     */
    private suspend fun faixaDeRodadas(div: Int, campId: Int): Pair<Int, Int> {
        val ultima = partidaDao.maxRodada(campId).coerceAtLeast(1)
        if (div != 40) return 1 to ultima
        val formato = MotorCampeonato.FORMATO_LIBERTADORES
        val primeira = formato.rodadaBaseGrupos
        val fim = primeira + formato.rodadasGrupos - 1
        // Enquanto os grupos não forem sorteados, a última rodada que existe é da
        // Pré: aí a faixa fica vazia e o seletor mostra só a primeira de grupo.
        return primeira to maxOf(primeira, minOf(fim, ultima))
    }

    private fun coletar(campId: Int, rodada: Int) {
        if (campId <= 0) { _partidas.value = emptyList(); return }
        coletarJob?.cancel()
        coletarJob = viewModelScope.launch {
            partidaDao.observeRodada(campId, rodada).collect { _partidas.value = it }
        }
    }

    private fun campIdAtual() = when (_divisaoSelecionada.value) {
        1 -> campeonatoAId
        2 -> campeonatoBId
        3 -> campeonatoCId
        4 -> campeonatoDId
        5 -> campeonatoArgAId
        6 -> campeonatoArgBId
        7 -> campeonatoArgClausuraId
        10 -> campeonatoUruAperturaId
        11 -> campeonatoUruIntermedId
        12 -> campeonatoUruClausuraId
        13 -> campeonatoUruBId
        14 -> campeonatoUruBCompetId
        20 -> campeonatoPremierId
        21 -> campeonatoChampionshipId
        22 -> campeonatoLaLigaId
        23 -> campeonatoSerieAId
        // 30 = Fase de Liga da Champions, mesmo código usado na tela de Classificação.
        // Só a Fase de Liga: as partidas dela têm fase = null e rodada de 1 a 8, então
        // observeRodada e maxRodada funcionam sem adaptação.
        30 -> campeonatoChampionsId
        // 32 = Fase de Liga da Europa League, nas mesmas condições.
        32 -> campeonatoEuropaId
        // 40 = fase de grupos da Libertadores. As partidas dela também têm
        // fase = null, mas as rodadas vão de 11 a 16, e não de 1 a 6 — a Pré
        // ocupa as rodadas 1..6 e aparece na visão de mata-mata (código 41).
        40 -> campeonatoLibertadoresId
        else -> campeonatoArgAId
    }

    // fases in the current knockout view, ordered by importance
    val ARG_FASES_ORDER = listOf("Oitavas", "Quartas", "Semi", "Final")

    /**
     * Ordem das fases do mata-mata da divisão selecionada. A Champions começa no
     * play-off, que antecede as oitavas e não existe no mata-mata argentino.
     */
    fun fasesDaDivisao(divisao: Int): List<String> = when (divisao) {
        31   -> MotorCampeonato.FORMATO_CHAMPIONS.fases
        33   -> MotorCampeonato.FORMATO_EUROPA_LEAGUE.fases
        // A Libertadores começa nas três fases da Pré-Libertadores, que antecedem
        // os grupos, e segue das oitavas à final.
        41   -> MotorCampeonato.FORMATO_LIBERTADORES.fases
        else -> ARG_FASES_ORDER
    }
}

// ═══════════════════════════════════════════════════════════
//  JogadoresViewModel
//  Tab 0 — Elenco com progressão individual
//  Tab 1 — Pesquisa global com filtros (posição, força, idade)
// ═══════════════════════════════════════════════════════════
data class FiltrosPesquisaJogador(
    val posicao: Posicao? = null,
    val forcaMin: Int = 0,
    val forcaMax: Int = 99,
    val idadeMin: Int = JogadoresViewModel.IDADE_MIN_PADRAO,
    val idadeMax: Int = JogadoresViewModel.IDADE_MAX_PADRAO,
    val valorMin: Long = 0,
    val valorMax: Long = JogadoresViewModel.VALOR_MAX_PADRAO,
    val salarioMin: Long = 0,
    val salarioMax: Long = JogadoresViewModel.SALARIO_MAX_PADRAO
)

@HiltViewModel
class JogadoresViewModel @Inject constructor(
    private val jogadorRepository: JogadorRepository,
    private val gameRepository: GameRepository,
    private val timeRepository: TimeRepository,
    private val gameDataStore: GameDataStore
) : ViewModel() {

    companion object {
        const val IDADE_MIN_PADRAO = 16
        const val IDADE_MAX_PADRAO = 40
        const val VALOR_MAX_PADRAO = 300_000_000_00L  // R$ 300 mi (sem teto efetivo)
        const val SALARIO_MAX_PADRAO = 2_000_000_00L  // R$ 2 mi/mês (sem teto efetivo)
    }

    private var timeId = -1

    private val _elenco = MutableStateFlow<List<Jogador>>(emptyList())
    val elenco: StateFlow<List<Jogador>> = _elenco.asStateFlow()

    private val _todosList = MutableStateFlow<List<Jogador>>(emptyList())

    // Filtros de pesquisa aplicados (um único objeto — aplicado ao concluir no painel).
    private val _filtros = MutableStateFlow(FiltrosPesquisaJogador())
    val filtros: StateFlow<FiltrosPesquisaJogador> = _filtros.asStateFlow()

    private val _saldo = MutableStateFlow(0L)
    val saldo: StateFlow<Long> = _saldo.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    val nomesPorTime: StateFlow<Map<Int, String>> =
        timeRepository.observeTodos()
            .map { times -> times.associate { it.id to it.nome } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val resultadoPesquisa: StateFlow<List<Jogador>> = combine(
        _todosList, _filtros
    ) { todos, f ->
        todos.filter { j ->
            (f.posicao == null || j.posicao == f.posicao) &&
            j.forca in f.forcaMin..f.forcaMax &&
            j.idade in f.idadeMin..f.idadeMax &&
            j.valorMercado in f.valorMin..f.valorMax &&
            j.salario in f.salarioMin..f.salarioMax
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun carregar(tiId: Int) {
        if (timeId == tiId) return
        timeId = tiId
        viewModelScope.launch {
            launch { jogadorRepository.observeElenco(timeId).collect { _elenco.value = it } }
            launch { jogadorRepository.observeTodosJogadoresAtivos().collect { _todosList.value = it } }
            launch { timeRepository.observeTodos().collect { times -> _saldo.value = times.find { it.id == timeId }?.saldo ?: 0L } }
        }
    }

    /** Aplica todos os filtros de uma vez (ao concluir no painel de filtros). */
    fun aplicarFiltros(filtros: FiltrosPesquisaJogador) { _filtros.value = filtros }
    fun limparFiltros() { _filtros.value = FiltrosPesquisaJogador() }
    fun limparMensagem() { _mensagem.value = null }

    fun fazerOferta(jogador: Jogador) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        val time = timeRepository.buscarPorId(timeId) ?: return@launch
        val preco = CalculadoraMercado.precoCompraUsuario(jogador.valorMercado)
        if (time.saldo < preco) {
            _mensagem.value = "Saldo insuficiente. Necessário: ${formatarValor(preco)}"
            return@launch
        }
        val qtdElenco = jogadorRepository.contarJogadores(timeId)
        if (qtdElenco >= 35) {
            _mensagem.value = "Elenco cheio (35/35). Venda ou dispense um jogador antes de contratar."
            return@launch
        }
        timeRepository.debitarSaldo(timeId, preco)
        jogador.timeId?.let { vendedorId -> timeRepository.creditarSaldo(vendedorId, preco) }
        jogadorRepository.realizarTransferencia(
            oferta = OfertaTransferencia(
                jogadorId       = jogador.id,
                timeCompradorId = timeId,
                timeVendedorId  = jogador.timeId,
                valor           = preco,
                salarioProposto = jogador.salario,
                contratoAnos    = 3
            ),
            temporadaId = save.temporadaId,
            mes         = save.mesAtual
        )
        // Se o time vendedor ficou com elenco abaixo de 18, recompõe automaticamente
        if (jogador.timeId != null) {
            gameRepository.recompletarElencoEmergenciaIA(jogador.timeId, save.temporadaId, save.mesAtual)
        }
        _mensagem.value = "${jogador.nomeAbreviado} contratado por ${formatarValor(preco)}!"
    }

    fun venderJogador(jogador: Jogador) = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        val preco = CalculadoraMercado.precoVendaUsuario(jogador.valorMercado)
        val comprador = timeRepository.buscarTodosOrdenadosPorReputacao()
            .filter { it.id != timeId && it.saldo >= preco }
            .randomOrNull()
        if (comprador == null) {
            _mensagem.value = "Nenhum clube interessado em ${jogador.nomeAbreviado} no momento."
            return@launch
        }
        timeRepository.creditarSaldo(timeId, preco)
        timeRepository.debitarSaldo(comprador.id, preco)
        jogadorRepository.realizarVenda(
            oferta = OfertaTransferencia(
                jogadorId       = jogador.id,
                timeCompradorId = comprador.id,
                timeVendedorId  = timeId,
                valor           = preco,
                salarioProposto = jogador.salario,
                contratoAnos    = 3
            ),
            temporadaId = save.temporadaId,
            mes         = save.mesAtual
        )
        _mensagem.value = "${jogador.nomeAbreviado} vendido para ${comprador.nome} por ${formatarValor(preco)}!"
    }

    fun renovarContrato(jogador: Jogador, novoSalario: Long, novosAnos: Int) = viewModelScope.launch {
        jogadorRepository.renovarContrato(jogador.id, novoSalario, novosAnos)
        _mensagem.value = "Contrato de ${jogador.nomeAbreviado} renovado por $novosAnos ano(s)."
    }

    fun dispensarJogador(jogador: Jogador) = viewModelScope.launch {
        jogadorRepository.dispensarParaMercado(jogador.id)
        _mensagem.value = "${jogador.nomeAbreviado} dispensado para o mercado livre."
    }

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
//  PatrocinioViewModel
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class PatrocinioViewModel @Inject constructor(
    private val gameDataStore: GameDataStore,
    private val timeRepository: TimeRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _ofertas = MutableStateFlow<List<MotorPatrocinio.OfertaPatrocinio>>(emptyList())
    val ofertas: StateFlow<List<MotorPatrocinio.OfertaPatrocinio>> = _ofertas.asStateFlow()

    private val _patrocinadorAtualTipo = MutableStateFlow(0)
    val patrocinadorAtualTipo: StateFlow<Int> = _patrocinadorAtualTipo.asStateFlow()

    private val _patrocinadorAtualValorAnual = MutableStateFlow(0L)
    val patrocinadorAtualValorAnual: StateFlow<Long> = _patrocinadorAtualValorAnual.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    fun carregar() = viewModelScope.launch {
        val save = gameDataStore.saveState.first()
        val time = timeRepository.buscarPorId(save.timeIdJogador) ?: return@launch
        _patrocinadorAtualTipo.value = save.patrocinadorTipo
        _patrocinadorAtualValorAnual.value = save.patrocinadorValorAnual
        _ofertas.value = MotorPatrocinio.gerarOfertas(time.reputacao.toInt(), save.temporadaId)
    }

    fun escolherPatrocinador(tipo: Int, valorAnual: Long) = viewModelScope.launch {
        val save        = gameDataStore.saveState.first()
        val valorMensal = valorAnual / 12L
        gameDataStore.salvarPatrocinador(tipo, valorAnual)
        // Credita imediatamente a primeira parcela no saldo do clube
        gameRepository.creditarPatrocinioImediato(save.timeIdJogador, valorMensal)
        // Marca o adiantamento para que fecharMes não duplique o crédito ao processar este mês
        gameDataStore.marcarPatrocinioPreCreditado(valorMensal)
        // Insere o lançamento na tabela financas → aparece imediatamente em Receitas
        gameRepository.registrarPatrocinioMes(save.timeIdJogador, save.temporadaId, save.mesAtual, valorMensal)
        _patrocinadorAtualTipo.value = tipo
        _patrocinadorAtualValorAnual.value = valorAnual
        _mensagem.value = "Contrato de patrocínio fechado! ${formatarValorPatrocinio(valorMensal)}/mês creditados."
    }

    private fun formatarValorPatrocinio(centavos: Long): String {
        val reais = centavos / 100.0
        return when {
            reais >= 1_000_000 -> "R$ %.1f M".format(reais / 1_000_000)
            reais >= 1_000     -> "R$ %.0f mil".format(reais / 1_000)
            else               -> "R$ %.0f".format(reais)
        }
    }

    fun limparMensagem() { _mensagem.value = null }
}

// ═══════════════════════════════════════════════════════════
//  TreinamentoViewModel
//  Gerencia sessões de treinamento do elenco sênior + base.
//  Cada treinamento reduz fadiga em 5% e acumula progresso
//  de evolução nos atributos principais da posição.
// ═══════════════════════════════════════════════════════════
@HiltViewModel
class TreinamentoViewModel @Inject constructor(
    private val jogadorRepository: JogadorRepository,
    private val gameDataStore: GameDataStore
) : ViewModel() {

    private val _elenco = MutableStateFlow<List<Jogador>>(emptyList())
    val elenco: StateFlow<List<Jogador>> = _elenco.asStateFlow()

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    fun carregar(timeId: Int) = viewModelScope.launch {
        jogadorRepository.observeElenco(timeId).collect { lista ->
            val juniores = jogadorRepository.buscarElencoCompleto(timeId)
                .filter { it.categoriaBase }
            _elenco.value = (lista.filterNot { it.categoriaBase } + juniores)
                .sortedWith(compareBy({ it.categoriaBase }, { it.posicao.name }))
        }
    }

    fun treinar(jogadorId: Int) = viewModelScope.launch {
        val jogador = _elenco.value.find { it.id == jogadorId } ?: return@launch
        if (jogador.treinouNestaCiclo) return@launch
        jogadorRepository.treinarJogador(jogadorId)
        val novaEnergia = ((jogador.fadiga - 0.05f).coerceIn(0f, 1f) * 100).toInt()
        _mensagem.value = "${jogador.nomeAbreviado} treinou. Energia: $novaEnergia%"
    }

    fun treinarTudo(timeId: Int) = viewModelScope.launch {
        val pendentes = _elenco.value.count { !it.treinouNestaCiclo && !it.aposentado }
        if (pendentes == 0) {
            _mensagem.value = "Todos os jogadores já treinaram neste ciclo."
            return@launch
        }
        jogadorRepository.treinarTimeCompleto(timeId)
        _mensagem.value = "$pendentes jogador(es) treinado(s)."
    }

    fun descansar(jogadorId: Int) = viewModelScope.launch {
        val jogador = _elenco.value.find { it.id == jogadorId } ?: return@launch
        if (jogador.treinouNestaCiclo) return@launch
        jogadorRepository.descansarJogador(jogadorId)
        val novaEnergia = ((jogador.fadiga + 0.10f).coerceIn(0f, 1f) * 100).toInt()
        _mensagem.value = "${jogador.nomeAbreviado} descansou. Energia: $novaEnergia%"
    }

    fun limparMensagem() { _mensagem.value = null }
}
