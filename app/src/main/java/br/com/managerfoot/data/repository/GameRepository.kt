package br.com.managerfoot.data.repository

import androidx.room.withTransaction
import br.com.managerfoot.data.dao.CampeonatoDao
import br.com.managerfoot.data.dao.ClassificacaoDao
import br.com.managerfoot.data.dao.CopaPartidaDto
import br.com.managerfoot.data.dao.EstadioDao
import br.com.managerfoot.data.dao.HallDaFamaDao
import br.com.managerfoot.data.dao.PartidaDao
import br.com.managerfoot.data.dao.PropostaIADao
import br.com.managerfoot.data.dao.RankingGeralDao
import br.com.managerfoot.data.database.AppDatabase
import br.com.managerfoot.data.database.entities.*
import br.com.managerfoot.domain.engine.*
import br.com.managerfoot.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val db: AppDatabase,
    private val campeonatoDao: CampeonatoDao,
    private val partidaDao: PartidaDao,
    private val classificacaoDao: ClassificacaoDao,
    private val timeRepository: TimeRepository,
    private val jogadorRepository: JogadorRepository,
    private val financaDao: br.com.managerfoot.data.dao.FinancaDao,
    private val hallDaFamaDao: HallDaFamaDao,
    private val rankingGeralDao: RankingGeralDao,
    private val estadioDao: EstadioDao,
    private val propostaIADao: PropostaIADao
) {
    private val simulador = SimuladorPartida()

    /**
     * Insere times e jogadores do seed em uma única transação atômica.
     * Se qualquer parte falhar, nenhuma linha fica no banco — evitando o
     * estado corrompido de "times sem jogadores".
     */
    suspend fun inserirSeedTransacional(
        times: List<TimeEntity>,
        jogadores: List<JogadorEntity>
    ) {
        db.withTransaction {
            db.timeDao().inserirTodos(times)
            db.jogadorDao().inserirTodos(jogadores)
        }
    }

    /**
     * Retorna true se o seed está íntegro: há times E cada time possui pelo menos
     * [ELENCO_MINIMO_SEED] jogadores associados.
     *
     * O limiar de [ELENCO_MINIMO_SEED] (16) cobre tanto o seed inicial (20-26 jogadores
     * por time) quanto times em andamento de jogo que venderam jogadores — a IA mantém
     * elencos acima de 18, portanto 16 é o piso seguro que distingue um seed válido de
     * uma inserção parcial (crash entre inserirTodos(times) e inserirTodos(jogadores)).
     */
    suspend fun seedEstaIntegro(): Boolean {
        val times = db.timeDao().buscarTodos()
        if (times.isEmpty()) return false
        val contagemPorTime = db.jogadorDao()
            .contarJogadoresPorTime()
            .associate { it.timeId to it.total }
        return times.all { (contagemPorTime[it.id] ?: 0) >= ELENCO_MINIMO_SEED }
    }

    companion object {
        /** Mínimo de jogadores por time para considerar o seed íntegro. */
        private const val ELENCO_MINIMO_SEED = 16
    }

    // Limpa todos os dados do jogo em ordem segura (filhos antes de pais)
    suspend fun limparTodosDados() {
        partidaDao.deleteAllEventos()
        partidaDao.deleteAllEscalacoes()
        financaDao.deleteAllTransferencias()
        financaDao.deleteAll()
        classificacaoDao.deleteAll()
        campeonatoDao.deleteAllParticipantes()
        partidaDao.deleteAll()
        campeonatoDao.deleteAll()
        hallDaFamaDao.deleteAll()
        rankingGeralDao.deleteAll()
        estadioDao.deleteAll()
        propostaIADao.limparTodas()
    }

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

    /**
     * Reconstrói a classificação do zero a partir das partidas reais jogadas na liga.
     * Garante consistência total: corrige qualquer divergência causada por falhas
     * em updates incrementais anteriores (ex.: partidas marcadas como jogadas mas
     * sem pontos contabilizados). Só processa partidas da liga (fase = null);
     * partidas de Copa (fase != null) não afetam a tabela.
     */
    suspend fun recalcularClassificacao(campeonatoId: Int) {
        val partidas = partidaDao.buscarPartidasJogadasDeLiga(campeonatoId)
        classificacaoDao.resetarEstatisticas(campeonatoId)
        for (p in partidas) {
            val gc = p.golsCasa ?: continue   // skip if no result (safety)
            val gf = p.golsFora ?: continue
            val casaV = if (gc > gf) 1 else 0; val casaE = if (gc == gf) 1 else 0; val casaD = if (gc < gf) 1 else 0
            val foraV = if (gf > gc) 1 else 0; val foraE = casaE;                    val foraD = if (gf < gc) 1 else 0
            classificacaoDao.atualizarEstatisticas(campeonatoId, p.timeCasaId, casaV, casaE, casaD, gc, gf)
            classificacaoDao.atualizarEstatisticas(campeonatoId, p.timeForaId, foraV, foraE, foraD, gf, gc)
        }
    }

    fun observarArtilheiros(campeonatoId: Int) = partidaDao.observeArtilheiros(campeonatoId)
    fun observarAssistentes(campeonatoId: Int) = partidaDao.observeAssistentes(campeonatoId)
    fun observarArtilheirosAllTime() = partidaDao.observeArtilheirosAllTime()
    fun observarAssistentesAllTime() = partidaDao.observeAssistentesAllTime()
    fun observarArtilheirosMulti(ids: List<Int>) = partidaDao.observeArtilheirosMulti(ids)
    fun observarAssistentesMulti(ids: List<Int>) = partidaDao.observeAssistentesMulti(ids)
    fun observarArtilheirosHistoricoFiltrado(tipos: List<String>) = partidaDao.observeArtilheirosHistoricoFiltrado(tipos)
    fun observarAssistentesHistoricoFiltrado(tipos: List<String>) = partidaDao.observeAssistentesHistoricoFiltrado(tipos)
    fun observarArtilheirosHistoricoCopaArgentina() = partidaDao.observeArtilheirosHistoricoCopaArgentina()
    fun observarAssistentesHistoricoCopaArgentina() = partidaDao.observeAssistentesHistoricoCopaArgentina()
    fun observarArtilheirosHistoricoCopaBrasil() = partidaDao.observeArtilheirosHistoricoCopaBrasil()
    fun observarAssistentesHistoricoCopaBrasil() = partidaDao.observeAssistentesHistoricoCopaBrasil()

    suspend fun buscarPartidasConfronto(timeAId: Int, timeBId: Int) =
        partidaDao.buscarPartidasConfronto(timeAId, timeBId)

    suspend fun buscarArtilheirosConfronto(timeAId: Int, timeBId: Int) =
        partidaDao.buscarArtilheirosConfronto(timeAId, timeBId)

    // ── Estatísticas do Time ──

    suspend fun buscarClassificacaoDoTime(campeonatoId: Int, timeId: Int) =
        classificacaoDao.buscarPosicao(campeonatoId, timeId)

    suspend fun buscarPosicaoNaTabela(campeonatoId: Int, timeId: Int): Int {
        val tabela = classificacaoDao.buscarTabelaOrdenada(campeonatoId)
        val idx = tabela.indexOfFirst { it.timeId == timeId }
        return if (idx >= 0) idx + 1 else 0
    }

    suspend fun buscarHistoricoDoTime(timeId: Int) =
        classificacaoDao.buscarHistoricoDoTime(timeId)

    suspend fun buscarEstatisticasJogadoresPorCampeonato(campeonatoId: Int, timeId: Int) =
        partidaDao.buscarEstatisticasJogadoresDaEquipe(campeonatoId, timeId)

    suspend fun buscarEstatisticasJogadoresAllTime(timeId: Int) =
        partidaDao.buscarEstatisticasJogadoresAllTime(timeId)

    suspend fun buscarPartidasDaEquipe(campeonatoId: Int, timeId: Int) =
        partidaDao.buscarPartidasDaEquipe(campeonatoId, timeId)

    suspend fun buscarHistoricoCopaDoTime(timeId: Int) =
        partidaDao.buscarHistoricoCopaDoTime(timeId)

    suspend fun buscarHistoricoSupercopaDoTime(timeId: Int) =
        partidaDao.buscarHistoricoSupercopaDoTime(timeId)

    suspend fun simularRodada(campeonatoId: Int, rodada: Int) {
        val partidas = partidaDao.buscarPorRodada(campeonatoId, rodada)
            .filter { !it.jogada }

        for (partida in partidas) {
            try {
                simularPartidaInterna(partida)
            } catch (_: Exception) {
                // Falha isolada: ignora esta partida e continua as demais
            }
        }

        campeonatoDao.avancarRodada(campeonatoId)

        // Treinamento/descanso automático para todos os times desta rodada
        val timeIds = partidas.flatMap { listOf(it.timeCasaId, it.timeForaId) }.toSet()
        aplicarTreinamentoAutomaticoIA(timeIds)
    }

    /** Aplica treino ou descanso automático para times da IA (exclui time do jogador se fornecido). */
    private suspend fun aplicarTreinamentoAutomaticoIA(timeIds: Collection<Int>) {
        for (timeId in timeIds) {
            if (timeId > 0) jogadorRepository.treinarOuDescansarTimeIA(timeId)
        }
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
            partidaId     = partidaDoJogador.id,
            casa          = escalacaoFinalCasa,
            fora          = escalacaoFinalFora,
            timeJogadorId = timeJogadorId,
            campoNeutro   = campeonatoDao.buscarPorId(campeonatoId)?.tipo == TipoCampeonato.SUPERCOPA
        )
        val notasPartida = persistirResultado(resultadoJogador)

        // Simula as demais partidas da rodada com IA
        for (partida in partidas) {
            if (partida.id != partidaDoJogador.id) {
                simularPartidaInterna(partida)
            }
        }

        campeonatoDao.avancarRodada(campeonatoId)

        // Verificar se é a volta de um duelo Copa com agregado empatado (pênaltis)
        var precisaPenaltis = false
        var golsAgregadoCasa = 0
        var golsAgregadoFora = 0
        if (partidaDoJogador.confrontoId != null) {
            val todasDoConfronto = partidaDao.buscarTodasPorCampeonato(campeonatoId)
                .filter { it.confrontoId == partidaDoJogador.confrontoId }
            val ida   = todasDoConfronto.minByOrNull { it.rodada }
            val volta = todasDoConfronto.maxByOrNull { it.rodada }

            if (volta != null && ida != null && volta.id == partidaDoJogador.id && ida.jogada) {
                val vencedor = MotorCampeonato.determinarVencedorTie(
                    timeCasaIdaId = ida.timeCasaId,
                    timeForaIdaId = ida.timeForaId,
                    golsCasaIda   = ida.golsCasa ?: 0,
                    golsForaIda   = ida.golsFora ?: 0,
                    golsCasaVolta = resultadoJogador.golsCasa,
                    golsForaVolta = resultadoJogador.golsFora
                )
                if (vencedor == null) {
                    precisaPenaltis = true
                    // Agregado do ponto de vista dos times como aparecem na volta:
                    // volta.timeCasaId = ida.timeForaId  → seu agregado = golsForaIda + golsCasaVolta
                    // volta.timeForaId = ida.timeCasaId  → seu agregado = golsCasaIda + golsForaVolta
                    golsAgregadoCasa = (ida.golsFora ?: 0) + resultadoJogador.golsCasa
                    golsAgregadoFora = (ida.golsCasa ?: 0) + resultadoJogador.golsFora
                }
            }
        }

        return resultadoJogador.copy(
            precisaPenaltis  = precisaPenaltis,
            golsAgregadoCasa = golsAgregadoCasa,
            golsAgregadoFora = golsAgregadoFora,
            notasJogadores   = notasPartida
        )
    }

    // ─────────────────────────────────────────────────────────────
    //  Simulação por períodos — a partida é dividida em 1º e 2º
    //  tempos (com possíveis re-simulações) para que as mudanças
    //  do jogador interfiram no resultado real.
    // ─────────────────────────────────────────────────────────────

    /**
     * Inicia a partida do jogador: simula apenas o 1º tempo (sem persistência).
     * Retorna um [ContextoSimulacaoMetade] com o estado do jogo ao intervalo e
     * um [ResultadoPartida] parcial (somente eventos do 1º tempo) para a UI.
     */
    suspend fun iniciarPartidaComJogador(
        campeonatoId: Int,
        rodada: Int,
        timeJogadorId: Int,
        escalacaoJogador: Escalacao
    ): Pair<ContextoSimulacaoMetade, ResultadoPartida> {
        val partidas = partidaDao.buscarPorRodada(campeonatoId, rodada).filter { !it.jogada }
        val partidaDoJogador = partidas.firstOrNull {
            it.timeCasaId == timeJogadorId || it.timeForaId == timeJogadorId
        } ?: throw IllegalStateException("Partida do jogador não encontrada na rodada $rodada")

        val ehMandante = partidaDoJogador.timeCasaId == timeJogadorId
        val escalacaoAdversario = gerarEscalacaoIA(
            if (ehMandante) partidaDoJogador.timeForaId else partidaDoJogador.timeCasaId
        )
        val escalacaoCasa = if (ehMandante) escalacaoJogador else escalacaoAdversario
        val escalacaoFora = if (ehMandante) escalacaoAdversario else escalacaoJogador

        // Estado inicial: todos os titulares entram no minuto 0
        val entryMinutes0 = (escalacaoCasa.titulares + escalacaoFora.titulares)
            .associate { it.jogador.id to 0 }
        val estadoInicial = EstadoMetade(
            titsCasa     = escalacaoCasa.titulares,
            titsFora     = escalacaoFora.titulares,
            resCasa      = escalacaoCasa.reservas,
            resFora      = escalacaoFora.reservas,
            entryMinutes = entryMinutes0
        )

        // Registra participação dos titulares iniciais
        val eventosParticipacao = escalacaoCasa.titulares.map { jne ->
            EventoSimulado(0, TipoEvento.PARTICIPOU, jne.jogador.id, escalacaoCasa.time.id, "")
        } + escalacaoFora.titulares.map { jne ->
            EventoSimulado(0, TipoEvento.PARTICIPOU, jne.jogador.id, escalacaoFora.time.id, "")
        }

        // Simula 1º tempo (minutos 1–45, fração = 0,5 dos gols esperados)
        val campoNeutro = campeonatoDao.buscarPorId(campeonatoId)?.tipo == TipoCampeonato.SUPERCOPA
        val (estadoApos1H, eventos1H) = simulador.simularPeriodo(
            minInicio     = 1,
            minFim        = 45,
            fracaoGols    = 0.5,
            escalacaoCasa = escalacaoCasa,
            escalacaoFora = escalacaoFora,
            estado        = estadoInicial,
            timeJogadorId = timeJogadorId,
            campoNeutro   = campoNeutro
        )

        val golsCasa1H = eventos1H.count { it.tipo == TipoEvento.GOL && it.timeId == escalacaoCasa.time.id }
        val golsFora1H = eventos1H.count { it.tipo == TipoEvento.GOL && it.timeId == escalacaoFora.time.id }
        val todosEventos1H = (eventosParticipacao + eventos1H).sortedBy { it.minuto }

        // Calcula público antes de montar o resultado parcial — exibido desde o início da partida
        val timeCasaInfo = timeRepository.buscarPorId(escalacaoCasa.time.id)
        val (torcedoresParcial, receitaParcial) = if (timeCasaInfo != null) {
            MotorFinanceiro.calcularPublico(timeCasaInfo, adversarioNivel = 5)
        } else {
            Pair(0, 0L)
        }

        val resultadoParcial = ResultadoPartida(
            partidaId        = partidaDoJogador.id,
            timeCasaId       = escalacaoCasa.time.id,
            timeForaId       = escalacaoFora.time.id,
            golsCasa         = golsCasa1H,
            golsFora         = golsFora1H,
            eventos          = todosEventos1H,
            estatisticasCasa = estatisticasDeEventos(todosEventos1H, escalacaoCasa.time.id, escalacaoFora.time.id, golsCasa1H),
            estatisticasFora = estatisticasDeEventos(todosEventos1H, escalacaoFora.time.id, escalacaoCasa.time.id, golsFora1H),
            torcedores       = torcedoresParcial,
            receitaPartida   = receitaParcial
        )

        val contexto = ContextoSimulacaoMetade(
            campeonatoId             = campeonatoId,
            rodada                   = rodada,
            timeJogadorId            = timeJogadorId,
            ehMandante               = ehMandante,
            partidaDoJogadorId       = partidaDoJogador.id,
            escalacaoJogadorOriginal = escalacaoJogador,
            escalacaoAdversario      = escalacaoAdversario,
            estadoMetade1            = estadoApos1H,
            golsCasaMetade1          = golsCasa1H,
            golsForaMetade1          = golsFora1H,
            eventosAcumulados        = todosEventos1H,
            campoNeutro              = campoNeutro
        )
        return Pair(contexto, resultadoParcial)
    }

    /**
     * Simula um período da partida com a escalação atualizada pelo jogador.
     * Deve ser chamado após o intervalo e opcionalmente após cada "Mexer no Time".
     * Retorna o contexto atualizado (com eventos acumulados) e os eventos do período.
     */
    suspend fun simularPeriodoComJogador(
        ctx: ContextoSimulacaoMetade,
        titularesJogador: List<JogadorNaEscalacao>,
        reservasJogador: List<JogadorNaEscalacao>,
        substituicoes: List<InfoSubstituicao>,
        formacao: String,
        estilo: EstiloJogo,
        minInicio: Int,
        minFim: Int
    ): Pair<ContextoSimulacaoMetade, List<EventoSimulado>> {
        // Reconstrói escalação do jogador com a tática/estilo atual
        val timeJogadorAtualizado = ctx.escalacaoJogadorOriginal.time.copy(
            taticaFormacao = formacao,
            estiloJogo     = estilo
        )
        val escalacaoAtualJogador = Escalacao(
            time      = timeJogadorAtualizado,
            titulares = titularesJogador,
            reservas  = reservasJogador
        )
        // Adversário usa a última formação conhecida do motor (pós-1º tempo)
        val escalacaoAtualAdversario = Escalacao(
            time      = ctx.escalacaoAdversario.time,
            titulares = if (ctx.ehMandante) ctx.estadoMetade1.titsFora else ctx.estadoMetade1.titsCasa,
            reservas  = if (ctx.ehMandante) ctx.estadoMetade1.resFora  else ctx.estadoMetade1.resCasa
        )
        val escalacaoCasaAtual = if (ctx.ehMandante) escalacaoAtualJogador else escalacaoAtualAdversario
        val escalacaoForaAtual = if (ctx.ehMandante) escalacaoAtualAdversario else escalacaoAtualJogador

        // Mescla entry/exit do motor com as substituições manuais do jogador
        val combinedEntry = ctx.estadoMetade1.entryMinutes.toMutableMap()
        val combinedExit  = ctx.estadoMetade1.exitMinutes.toMutableMap()
        for (sub in substituicoes) {
            combinedExit[sub.saiId]    = sub.minuto
            combinedEntry[sub.entrouId] = sub.minuto
        }

        val estadoPeriodo = EstadoMetade(
            titsCasa     = escalacaoCasaAtual.titulares,
            titsFora     = escalacaoForaAtual.titulares,
            resCasa      = escalacaoCasaAtual.reservas,
            resFora      = escalacaoForaAtual.reservas,
            penalCasa    = ctx.estadoMetade1.penalCasa,
            penalFora    = ctx.estadoMetade1.penalFora,
            entryMinutes = combinedEntry,
            exitMinutes  = combinedExit
        )

        val fracaoGols = (minFim - minInicio + 1).toDouble() / 90.0
        val (estadoFinal, eventosPeriodo) = simulador.simularPeriodo(
            minInicio     = minInicio,
            minFim        = minFim,
            fracaoGols    = fracaoGols,
            escalacaoCasa = escalacaoCasaAtual,
            escalacaoFora = escalacaoForaAtual,
            estado        = estadoPeriodo,
            timeJogadorId = ctx.timeJogadorId,
            campoNeutro   = ctx.campoNeutro
        )

        // Injeta SUBSTITUICAO_ENTRA/SAI para as substituições manuais do jogador que
        // ainda não foram registradas nos eventos acumulados.
        // Isso garante que os subs recebam nota na partida e evoluam como os titulares.
        val jogadoresJaTrackeados = ctx.eventosAcumulados
            .filter { it.tipo == TipoEvento.SUBSTITUICAO_ENTRA || it.tipo == TipoEvento.PARTICIPOU }
            .map { it.jogadorId }.toSet()
        val subEventsJogador = substituicoes
            .filter { it.entrouId !in jogadoresJaTrackeados }
            .flatMap { sub ->
                listOf(
                    EventoSimulado(sub.minuto, TipoEvento.SUBSTITUICAO_SAI,   sub.saiId,    ctx.timeJogadorId, ""),
                    EventoSimulado(sub.minuto, TipoEvento.SUBSTITUICAO_ENTRA, sub.entrouId, ctx.timeJogadorId, "")
                )
            }

        // Acumula eventos: mantém os anteriores a minInicio, substitui os restantes
        val eventosAnteriores = ctx.eventosAcumulados.filter { it.minuto < minInicio }
        val novosAcumulados   = (eventosAnteriores + subEventsJogador + eventosPeriodo).sortedBy { it.minuto }

        val ctxAtualizado = ctx.copy(
            estadoMetade1     = estadoFinal,
            eventosAcumulados = novosAcumulados
        )
        return Pair(ctxAtualizado, eventosPeriodo)
    }

    /**
     * Finaliza a partida: persiste o resultado completo, simula outros campeonatos,
     * avança rodada e retorna o [ResultadoPartida] definitivo (com notas + Copa check).
     *
     * [outrosCampeonatoIds] — IDs dos campeonatos paralelos (Séries B, C, D e Copa) que
     * devem ter sua rodada atual simulada simultaneamente ao jogo do jogador.
     */
    suspend fun finalizarPartidaComJogador(
        ctx: ContextoSimulacaoMetade,
        golsCasaFinal: Int,
        golsForaFinal: Int,
        outrosCampeonatoIds: List<Int> = emptyList()
    ): ResultadoPartida {
        val escalacaoCasa = if (ctx.ehMandante) ctx.escalacaoJogadorOriginal else ctx.escalacaoAdversario
        val escalacaoFora = if (ctx.ehMandante) ctx.escalacaoAdversario else ctx.escalacaoJogadorOriginal

        val resultadoBase = ResultadoPartida(
            partidaId        = ctx.partidaDoJogadorId,
            timeCasaId       = escalacaoCasa.time.id,
            timeForaId       = escalacaoFora.time.id,
            golsCasa         = golsCasaFinal,
            golsFora         = golsForaFinal,
            eventos          = ctx.eventosAcumulados,
            estatisticasCasa = estatisticasDeEventos(ctx.eventosAcumulados, escalacaoCasa.time.id, escalacaoFora.time.id, golsCasaFinal),
            estatisticasFora = estatisticasDeEventos(ctx.eventosAcumulados, escalacaoFora.time.id, escalacaoCasa.time.id, golsForaFinal)
        )

        val notasPartida = persistirResultado(resultadoBase)

        // Calcula público para exibição na tela da partida
        val timeCasa = timeRepository.buscarPorId(resultadoBase.timeCasaId)
        val (torcedoresCalc, receitaCalc) = if (timeCasa != null) {
            MotorFinanceiro.calcularPublico(timeCasa, adversarioNivel = 5)
        } else {
            Pair(0, 0L)
        }

        // Simula demais partidas da rodada com IA
        // Primeiro, recupera e simula partidas atrasadas de rodadas anteriores na liga do jogador
        val atrasadasLiga = partidaDao.buscarTodasPorCampeonato(ctx.campeonatoId)
            .filter { !it.jogada && it.rodada < ctx.rodada && it.fase == null }
        for (partida in atrasadasLiga) {
            if (partida.id != ctx.partidaDoJogadorId) {
                try { simularPartidaInterna(partida) } catch (_: Exception) {}
            }
        }

        // Simula as partidas da rodada atual com IA
        val partidas = partidaDao.buscarPorRodada(ctx.campeonatoId, ctx.rodada).filter { !it.jogada }
        for (partida in partidas) {
            if (partida.id != ctx.partidaDoJogadorId) {
                try {
                    simularPartidaInterna(partida)
                } catch (_: Exception) {
                    // Falha isolada: não bloqueia o avanço da rodada
                }
            }
        }

        campeonatoDao.avancarRodada(ctx.campeonatoId)

        // Treinamento/descanso automático para os times da IA na liga do jogador (exclui o time do jogador)
        val iaTimeIdsLiga = (atrasadasLiga + partidas)
            .flatMap { listOf(it.timeCasaId, it.timeForaId) }
            .filter { it != ctx.timeJogadorId }
            .toSet()
        aplicarTreinamentoAutomaticoIA(iaTimeIdsLiga)

        // Simula rodada atual em todos os outros campeonatos paralelos (Séries B, C, D, Copa)
        simularRodadaEmOutrosCampeonatos(ctx.campeonatoId, outrosCampeonatoIds)

        // Verifica Copa (agregado + pênaltis)
        var precisaPenaltis = false
        var golsAgregadoCasa = 0
        var golsAgregadoFora = 0
        val partidaDoJogador = partidaDao.buscarPorId(ctx.partidaDoJogadorId)
        if (partidaDoJogador?.confrontoId != null) {
            val todasDoConfronto = partidaDao.buscarTodasPorCampeonato(ctx.campeonatoId)
                .filter { it.confrontoId == partidaDoJogador.confrontoId }
            val ida   = todasDoConfronto.minByOrNull { it.rodada }
            val volta = todasDoConfronto.maxByOrNull { it.rodada }
            if (volta != null && ida != null && volta.id == ctx.partidaDoJogadorId && ida.jogada) {
                val vencedor = MotorCampeonato.determinarVencedorTie(
                    timeCasaIdaId = ida.timeCasaId,
                    timeForaIdaId = ida.timeForaId,
                    golsCasaIda   = ida.golsCasa ?: 0,
                    golsForaIda   = ida.golsFora ?: 0,
                    golsCasaVolta = golsCasaFinal,
                    golsForaVolta = golsForaFinal
                )
                if (vencedor == null) {
                    precisaPenaltis  = true
                    golsAgregadoCasa = (ida.golsFora ?: 0) + golsCasaFinal
                    golsAgregadoFora = (ida.golsCasa ?: 0) + golsForaFinal
                }
            }
        }

        // Supercopa: jogo único — empate nos 90min decide por pênaltis
        if (!precisaPenaltis) {
            val tipoDoJogo = campeonatoDao.buscarPorId(ctx.campeonatoId)?.tipo
            if (tipoDoJogo == TipoCampeonato.SUPERCOPA && golsCasaFinal == golsForaFinal) {
                precisaPenaltis = true
            }
        }

        // Argentine GRUPOS_E_MATA_MATA knockout: jogo único — empate decide por pênaltis
        if (!precisaPenaltis && golsCasaFinal == golsForaFinal) {
            val formato = campeonatoDao.buscarPorId(ctx.campeonatoId)?.formato
            val fasePartida = partidaDoJogador?.fase
            if (formato == FormatoCampeonato.GRUPOS_E_MATA_MATA && fasePartida != null) {
                precisaPenaltis = true
            }
        }

        return resultadoBase.copy(
            precisaPenaltis  = precisaPenaltis,
            golsAgregadoCasa = golsAgregadoCasa,
            golsAgregadoFora = golsAgregadoFora,
            notasJogadores   = notasPartida,
            torcedores       = torcedoresCalc,
            receitaPartida   = receitaCalc
        )
    }

    suspend fun simularPartida(
        partida: PartidaEntity,
        escalacaoCasa: Escalacao? = null,
        escalacaoFora: Escalacao? = null
    ): ResultadoPartida {
        val escalacaoFinalCasa = escalacaoCasa ?: gerarEscalacaoIA(partida.timeCasaId)
        val escalacaoFinalFora = escalacaoFora ?: gerarEscalacaoIA(partida.timeForaId)
        val campoNeutro = campeonatoDao.buscarPorId(partida.campeonatoId)?.tipo == TipoCampeonato.SUPERCOPA

        val resultado = simulador.simular(
            partidaId   = partida.id,
            casa        = escalacaoFinalCasa,
            fora        = escalacaoFinalFora,
            campoNeutro = campoNeutro
        )

        persistirResultado(resultado)
        return resultado
    }

    /** Corrige ordemGlobal de partidas da Copa do Brasil criadas com os valores antigos. */
    suspend fun migrarOrdemGlobalCopaBrasil() = partidaDao.migrarOrdemGlobalCopaBrasil()

    suspend fun buscarProximaPartida(timeId: Int): PartidaEntity? =
        partidaDao.buscarProximaPartida(timeId)

    fun observarUltimosResultados(timeId: Int, campeonatoId: Int): Flow<List<PartidaEntity>> =
        partidaDao.observeUltimosResultados(timeId, campeonatoId)

    fun observarReceitasPartidas(timeId: Int): Flow<List<br.com.managerfoot.data.dao.CalendarioPartidaDto>> =
        partidaDao.observeReceitasPartidas(timeId)

    fun observarFinancasMensais(timeId: Int): Flow<List<br.com.managerfoot.data.database.entities.FinancaEntity>> =
        financaDao.observeFinancasMensais(timeId)

    fun observarSaldoTime(timeId: Int): Flow<Long> =
        timeRepository.observePorId(timeId).map { it?.saldo ?: 0L }

    suspend fun registrarUpgradeEstadio(timeId: Int, temporadaId: Int, mes: Int, custo: Long) {
        val saldoAtual = timeRepository.buscarPorId(timeId)?.saldo ?: 0L
        financaDao.inserir(
            FinancaEntity(
                timeId = timeId,
                temporadaId = temporadaId,
                mes = mes,
                despesaAmpliacaoEstadio = custo,
                saldoFinal = saldoAtual
            )
        )
    }

    suspend fun buscarUltimosResultados(timeId: Int, campeonatoId: Int): List<PartidaEntity> =
        partidaDao.buscarUltimosResultados(timeId, campeonatoId)

    suspend fun buscarUltimosResultados(timeId: Int, campeonatoIds: List<Int>): List<PartidaEntity> =
        partidaDao.buscarUltimosResultadosMultiCamp(timeId, campeonatoIds)

    suspend fun processarFimDeTemporada(temporadaId: Int) {
        val campeonatos = campeonatoDao.buscarAtivos()
        for (campeonato in campeonatos) {
            campeonatoDao.encerrar(campeonato.id)
        }
        jogadorRepository.processarDesenvolvimentoAnual()
    }

    // Retorna informações da nova temporada criada
    data class NovaTemporadaInfo(
        val campeonatoAId: Int,
        val campeonatoBId: Int,
        val campeonatoCId: Int,
        val campeonatoDId: Int,
        val campeonatoArgAId: Int,        // Apertura
        val campeonatoArgBId: Int,        // Segunda División Argentina
        val campeonatoArgClausuraId: Int, // Clausura Argentina
        val campeonatoUruAperturaId: Int, // Apertura Uruguaio
        val campeonatoUruBId: Int,        // Segunda División Uruguaia
        val campeonatoUruClausuraId: Int, // Clausura Uruguaio
        val campeonatoUruIntermedId: Int, // Intermediário Uruguaio
        val campeonatoUruBCompetId: Int,  // Torneo Competencia – Segunda División
        val copaId: Int,
        val copaArgId: Int,               // Copa Argentina
        val supercopaId: Int,
        val temporadaId: Int,
        val ano: Int
    )

    suspend fun encerrarTemporadaComHallDaFama(
        campeonatoAId: Int,
        campeonatoBId: Int,
        campeonatoCId: Int,
        campeonatoDId: Int,
        campeonatoArgAId: Int = -1,         // Apertura
        campeonatoArgBId: Int = -1,         // Segunda División
        campeonatoArgClausuraId: Int = -1,  // Clausura
        campeonatoUruAperturaId: Int = -1,  // Apertura Uruguaio
        campeonatoUruClausuraId: Int = -1,  // Clausura Uruguaio
        campeonatoUruIntermedId: Int = -1,  // Intermediário Uruguaio
        campeonatoUruBId: Int = -1,         // Segunda División Uruguaia
        campeonatoUruBCompetId: Int = -1,   // Torneo Competencia – Segunda División
        temporadaId: Int,
        ano: Int,
        timeJogadorId: Int = -1
    ): NovaTemporadaInfo {
        // ── Série A ──────────────────────────────────────────────
        val participantesA = campeonatoDao.buscarIdsParticipantes(campeonatoAId)
        // ── Helper: registra Hall da Fama para uma divisão ────────
        suspend fun registrarHallDaFama(campId: Int, div: Int) {
            if (campId <= 0) return
            val tabTop2    = classificacaoDao.buscarTop2(campId)
            val campeao    = tabTop2.getOrNull(0) ?: return
            val vice       = tabTop2.getOrNull(1)
            val artilheiro = partidaDao.buscarArtilheiroTop1(campId)
            val assistente = partidaDao.buscarAssisteTop1(campId)
            val campEntity = campeonatoDao.buscarPorId(campId)
            val nomeSerie  = when (div) { 1 -> "A"; 2 -> "B"; 3 -> "C"; 4 -> "D"; 5 -> "Copa"; 6 -> "Supercopa"; 7 -> "Apertura"; 8 -> "Clausura"; 10 -> "Segunda Div. Argentina"; 11 -> "Apertura Uruguaio"; 13 -> "Clausura Uruguaio"; 15 -> "Segunda Div. Uruguaia"; 16 -> "Competencia URU B"; else -> "D" }
            hallDaFamaDao.inserir(HallDaFamaEntity(
                ano = ano,
                nomeCampeonato     = campEntity?.nome ?: "Brasileiro Série $nomeSerie $ano",
                campeaoTimeId      = campeao.timeId,
                campeaoNome        = timeRepository.buscarPorId(campeao.timeId)?.nome ?: "",
                campeaoEscudo      = timeRepository.buscarPorId(campeao.timeId)?.escudoRes ?: "",
                viceTimeId         = vice?.timeId ?: -1,
                viceNome           = vice?.let { timeRepository.buscarPorId(it.timeId)?.nome } ?: "",
                viceEscudo         = vice?.let { timeRepository.buscarPorId(it.timeId)?.escudoRes } ?: "",
                artilheiroId       = artilheiro?.jogadorId ?: -1,
                artilheiroNome     = artilheiro?.nomeJogador ?: "",
                artilheiroNomeAbrev = artilheiro?.nomeAbrev ?: "",
                artilheiroGols     = artilheiro?.total ?: 0,
                artilheiroNomeTime = artilheiro?.nomeTime ?: "",
                artilheiroEscudo   = artilheiro?.escudoRes ?: "",
                assistenteId       = assistente?.jogadorId ?: -1,
                assistenteNome     = assistente?.nomeJogador ?: "",
                assistenteNomeAbrev = assistente?.nomeAbrev ?: "",
                assistenciasTotais = assistente?.total ?: 0,
                assistenteNomeTime = assistente?.nomeTime ?: "",
                assistenteEscudo   = assistente?.escudoRes ?: "",
                divisao = div
            ))
        }

        registrarHallDaFama(campeonatoAId, 1)
        registrarHallDaFama(campeonatoBId, 2)
        registrarHallDaFama(campeonatoCId, 3)
        registrarHallDaFama(campeonatoDId, 4)
        // Apertura e Clausura ARG já tiveram Hall da Fama registrado ao encerrar o torneio
        registrarHallDaFama(campeonatoArgBId, 10)  // 10 = Segunda Div. Argentina
        // Uruguai: Apertura e Clausura já registrados; registra a Segunda Div.
        registrarHallDaFama(campeonatoUruAperturaId, 11)  // 11 = Apertura Uruguaio (PONTOS_CORRIDOS)
        registrarHallDaFama(campeonatoUruBId, 15)         // 15 = Segunda Div. Uruguaia
        registrarHallDaFama(campeonatoUruBCompetId, 16)  // 16 = Competencia URU B
        registrarHallDaFama(campeonatoUruBCompetId, 16)   // 16 = Competencia URU B

        // ── Bônus de reputação por título de divisão ──────────────
        suspend fun aplicarBonusTitulo(campId: Int, bonusPercent: Double) {
            if (campId <= 0) return
            val campeaoEntry = classificacaoDao.buscarTop2(campId).firstOrNull() ?: return
            val campeaoTime = timeRepository.buscarPorId(campeaoEntry.timeId) ?: return
            val bonus = (campeaoTime.reputacao * bonusPercent).toFloat().coerceAtLeast(0.1f)
            timeRepository.atualizarReputacao(campeaoTime.id, campeaoTime.reputacao + bonus)
        }
        aplicarBonusTitulo(campeonatoAId, 0.05) // Série A +5%
        aplicarBonusTitulo(campeonatoBId, 0.03) // Série B +3%
        aplicarBonusTitulo(campeonatoCId, 0.02) // Série C +2%
        aplicarBonusTitulo(campeonatoDId, 0.01) // Série D +1%
        // Apertura/Clausura: bônus já aplicado ao encerrar cada torneio
        aplicarBonusTitulo(campeonatoArgBId, 0.02) // Segunda Div. ARG +2%
        aplicarBonusTitulo(campeonatoUruBId, 0.02) // Segunda Div. URU +2%

        // ── Premiações de títulos — para o time do jogador se ele é campeão ou vice ─────────
        // Os valores estão em centavos (1 milhão = 1_000_000_00L no padrão monetário do jogo)
        suspend fun premiarSeJogador(campId: Int, premioCampeao: Long) {
            if (campId <= 0 || timeJogadorId <= 0) return
            val top2 = classificacaoDao.buscarTop2(campId)
            val campeao = top2.getOrNull(0) ?: return
            val vice    = top2.getOrNull(1)
            val (premio, posicao) = when (timeJogadorId) {
                campeao.timeId -> premioCampeao to "Campeão"
                vice?.timeId   -> premioCampeao / 2 to "Vice-campeão"
                else           -> return
            }
            val nomeCamp = campeonatoDao.buscarPorId(campId)?.nome ?: "Campeonato"
            timeRepository.creditarSaldo(timeJogadorId, premio)
            financaDao.inserir(
                br.com.managerfoot.data.database.entities.FinancaEntity(
                    timeId            = timeJogadorId,
                    temporadaId       = temporadaId,
                    mes               = 13,  // mês 13 = encerramento da temporada
                    receitaPremiacoes = premio,
                    descricaoPremio   = "$nomeCamp — $posicao",
                    saldoFinal        = premio
                )
            )
        }
        premiarSeJogador(campeonatoAId, 20_000_000_00L)  // Cam: R$ 20M, Vice: R$ 10M
        premiarSeJogador(campeonatoBId,  8_000_000_00L)  // Cam: R$  8M, Vice: R$  4M
        premiarSeJogador(campeonatoCId,  5_000_000_00L)  // Cam: R$  5M, Vice: R$2.5M
        premiarSeJogador(campeonatoDId,  2_000_000_00L)  // Cam: R$  2M, Vice: R$  1M
        // Apertura/Clausura: premiação já feita ao encerrar cada torneio
        premiarSeJogador(campeonatoArgBId,  6_000_000_00L) // Cam: R$  6M, Vice: R$  3M

        // ── Desfechos (promoções / rebaixamentos) ─────────────────
        suspend fun desfecho(campId: Int): MotorCampeonato.DesfechoCampeonato? {
            if (campId <= 0) return null
            val entity = campeonatoDao.buscarPorId(campId) ?: return null
            val tabela = classificacaoDao.buscarTabelaOrdenada(campId)
            return MotorCampeonato.calcularDesfecho(entity, tabela)
        }

        val dfA = desfecho(campeonatoAId)
        val dfB = desfecho(campeonatoBId)
        val dfC = desfecho(campeonatoCId)
        val dfD = desfecho(campeonatoDId)

        val rebaixadosA = dfA?.rebaixados ?: emptyList()  // A → B
        val promovidosB = dfB?.promovidos ?: emptyList()  // B → A
        val rebaixadosB = dfB?.rebaixados ?: emptyList()  // B → C
        val promovidosC = dfC?.promovidos ?: emptyList()  // C → B
        val rebaixadosC = dfC?.rebaixados ?: emptyList()  // C → D
        val promovidosD = dfD?.promovidos ?: emptyList()  // D → C (sem rebaixamento)

        // Atualiza campo divisao de cada time
        suspend fun setDivisao(ids: List<Int>, div: Int) = ids.forEach { id ->
            timeRepository.buscarEntityPorId(id)?.let { timeRepository.atualizar(it.copy(divisao = div)) }
        }
        setDivisao(rebaixadosA, 2); setDivisao(promovidosB, 1)
        setDivisao(rebaixadosB, 3); setDivisao(promovidosC, 2)
        setDivisao(rebaixadosC, 4); setDivisao(promovidosD, 3)

        // Recalcula participantes
        val partA = campeonatoDao.buscarIdsParticipantes(campeonatoAId)
        val partB = campeonatoDao.buscarIdsParticipantes(campeonatoBId)
        val partC = if (campeonatoCId > 0) campeonatoDao.buscarIdsParticipantes(campeonatoCId) else emptyList()
        val partD = if (campeonatoDId > 0) campeonatoDao.buscarIdsParticipantes(campeonatoDId) else emptyList()

        val novosA = (partA - rebaixadosA.toSet()) + promovidosB
        val novosB = (partB - promovidosB.toSet() - rebaixadosB.toSet()) + rebaixadosA + promovidosC
        val novosC = (partC - promovidosC.toSet() - rebaixadosC.toSet()) + rebaixadosB + promovidosD
        val novosD = (partD - promovidosD.toSet()) + rebaixadosC

        // Encerra todos os campeonatos
        listOf(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId,
               campeonatoArgAId, campeonatoArgBId, campeonatoArgClausuraId,
               campeonatoUruAperturaId, campeonatoUruClausuraId, campeonatoUruIntermedId, campeonatoUruBId)
            .filter { it > 0 }.forEach { campeonatoDao.encerrar(it) }

        jogadorRepository.processarDesenvolvimentoAnual()

        val novoAno         = ano + 1
        val novoTemporadaId = temporadaId + 1

        val novoCampeonatoAId = criarCampeonato(
            CampeonatoEntity(temporadaId = novoTemporadaId, nome = "Brasileiro Série A $novoAno",
                tipo = TipoCampeonato.NACIONAL_DIVISAO1, formato = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = (novosA.size - 1) * 2), novosA)
        val novoCampeonatoBId = criarCampeonato(
            CampeonatoEntity(temporadaId = novoTemporadaId, nome = "Brasileiro Série B $novoAno",
                tipo = TipoCampeonato.NACIONAL_DIVISAO2, formato = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = (novosB.size - 1) * 2), novosB)
        val novoCampeonatoCId = if (campeonatoCId > 0) criarCampeonato(
            CampeonatoEntity(temporadaId = novoTemporadaId, nome = "Brasileiro Série C $novoAno",
                tipo = TipoCampeonato.NACIONAL_DIVISAO3, formato = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = (novosC.size - 1) * 2), novosC) else -1
        val novoCampeonatoDId = if (campeonatoDId > 0) criarCampeonato(
            CampeonatoEntity(temporadaId = novoTemporadaId, nome = "Brasileiro Série D $novoAno",
                tipo = TipoCampeonato.NACIONAL_DIVISAO4, formato = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = (novosD.size - 1) * 2), novosD) else -1

        // Atualiza ranking geral com os resultados desta temporada
        atualizarRankingGeral(campeonatoAId)
        atualizarRankingGeral(campeonatoBId)
        atualizarRankingGeral(campeonatoCId)
        atualizarRankingGeral(campeonatoDId)
        atualizarRankingGeral(campeonatoArgBId)
        atualizarRankingGeral(campeonatoUruBId)

        // ── Títulos dos campeões do Apertura e Clausura ────────────
        // Centralizado aqui para garantir que ambos campeões sejam contabilizados,
        // inclusive quando verificarEAvancarFaseArgentina não processou a Final.
        suspend fun registrarTituloArgentino(campId: Int) {
            if (campId <= 0) return
            val finalPartida = partidaDao.buscarTodasPorCampeonato(campId)
                .filter { it.fase == "Final" && it.jogada }
                .firstOrNull() ?: return
            val gC = finalPartida.golsCasa ?: return
            val gF = finalPartida.golsFora ?: return
            val campeaoId = when {
                gC > gF -> finalPartida.timeCasaId
                gF > gC -> finalPartida.timeForaId
                (finalPartida.penaltisCasa ?: 0) > (finalPartida.penaltisForaId ?: 0) -> finalPartida.timeCasaId
                else -> finalPartida.timeForaId
            }
            val timeEnt = timeRepository.buscarEntityPorId(campeaoId) ?: return
            val existing = rankingGeralDao.buscarPorTime(campeaoId)
            val entry = existing ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                timeId = campeaoId, nomeTime = timeEnt.nome, escudoRes = timeEnt.escudoRes, divisaoAtual = timeEnt.divisao
            )
            rankingGeralDao.inserirOuAtualizar(entry.copy(
                titulosNacionais = entry.titulosNacionais + 1,
                pontosAcumulados = entry.pontosAcumulados + 20L
            ))
        }
        registrarTituloArgentino(campeonatoArgAId)
        registrarTituloArgentino(campeonatoArgClausuraId)

        // Campeão Geral da temporada Argentina: time com mais pontos acumulados
        // somando fase de grupos do Apertura + Clausura
        run {
            val tabAp = if (campeonatoArgAId > 0) classificacaoDao.buscarTabelaOrdenada(campeonatoArgAId) else emptyList()
            val tabCl = if (campeonatoArgClausuraId > 0) classificacaoDao.buscarTabelaOrdenada(campeonatoArgClausuraId) else emptyList()
            val pontosCombinados = mutableMapOf<Int, Int>()
            for (cls in tabAp + tabCl) pontosCombinados[cls.timeId] = (pontosCombinados[cls.timeId] ?: 0) + cls.pontos
            val campGeral = pontosCombinados.maxByOrNull { it.value }?.key
            if (campGeral != null) {
                val timeEnt = timeRepository.buscarEntityPorId(campGeral)
                val rankCG = rankingGeralDao.buscarPorTime(campGeral)
                    ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                        timeId = campGeral, nomeTime = timeEnt?.nome ?: "", escudoRes = timeEnt?.escudoRes ?: "", divisaoAtual = timeEnt?.divisao ?: 5
                    )
                rankingGeralDao.inserirOuAtualizar(rankCG.copy(
                    titulosNacionais = rankCG.titulosNacionais + 1,
                    pontosAcumulados = rankCG.pontosAcumulados + 10L
                ))
            }
        }

        // ── Incrementa temporadasJogadas para a Primera División Argentina ──
        // Feito a partir dos participantes do Apertura (Clausura tem os mesmos times),
        // garantindo que cada clube incremente a contagem uma única vez por temporada.
        if (campeonatoArgAId > 0) {
            val participantesArgentina = campeonatoDao.buscarIdsParticipantes(campeonatoArgAId)
            for (timeId in participantesArgentina) {
                val timeEnt = timeRepository.buscarEntityPorId(timeId) ?: continue
                val existing = rankingGeralDao.buscarPorTime(timeId)
                    ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                        timeId = timeId, nomeTime = timeEnt.nome, escudoRes = timeEnt.escudoRes, divisaoAtual = timeEnt.divisao
                    )
                rankingGeralDao.inserirOuAtualizar(existing.copy(
                    nomeTime          = timeEnt.nome,
                    escudoRes         = timeEnt.escudoRes,
                    divisaoAtual      = timeEnt.divisao,
                    temporadasJogadas = existing.temporadasJogadas + 1
                ))
            }
        }

        // ── Promoção / Rebaixamento — Argentina ───────────────────
        // Tabela geral: soma de pontos do Apertura + Clausura (fase de grupos)
        val tabAp2 = if (campeonatoArgAId > 0) classificacaoDao.buscarTabelaOrdenada(campeonatoArgAId) else emptyList()
        val tabCl2 = if (campeonatoArgClausuraId > 0) classificacaoDao.buscarTabelaOrdenada(campeonatoArgClausuraId) else emptyList()
        val pontosGeralArg = mutableMapOf<Int, Int>()
        for (cls in tabAp2 + tabCl2) pontosGeralArg[cls.timeId] = (pontosGeralArg[cls.timeId] ?: 0) + cls.pontos
        // Dois últimos da tabela geral → rebaixados para Segunda División
        val rebaixadosArgA = pontosGeralArg.entries
            .sortedBy { it.value }
            .take(2)
            .map { it.key }
        // Dois primeiros da Segunda División → promovidos para Primera
        val dfArgB = if (campeonatoArgBId > 0) {
            val tabB = classificacaoDao.buscarTabelaOrdenada(campeonatoArgBId)
            tabB.sortedWith(
                compareByDescending<ClassificacaoEntity> { it.pontos }
                    .thenByDescending { it.vitorias }
                    .thenByDescending { it.saldoGols }
                    .thenByDescending { it.golsPro }
            ).take(2).map { it.timeId }
        } else emptyList()
        val promovidosArgB = dfArgB  // Segunda → Primera

        // Atualiza campo divisao dos times afetados
        rebaixadosArgA.forEach { id ->
            timeRepository.buscarEntityPorId(id)?.let { timeRepository.atualizar(it.copy(divisao = 6)) }
        }
        promovidosArgB.forEach { id ->
            timeRepository.buscarEntityPorId(id)?.let { timeRepository.atualizar(it.copy(divisao = 5)) }
        }

        // Renova Primera División Argentina – Apertura e Clausura (com promoção/rebaixamento)
        val participantesArgA = if (campeonatoArgAId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoArgAId) else emptyList()
        val novosArgA = (participantesArgA - rebaixadosArgA.toSet()) + promovidosArgB
        val novoAperturaId = if (novosArgA.isNotEmpty()) criarArgentinaTorneio(
            novosArgA, novoTemporadaId,
            "Primera División Argentina Apertura $novoAno", ogOffset = 0
        ) else -1
        val novoClausuraId = if (novosArgA.isNotEmpty()) criarArgentinaTorneio(
            novosArgA, novoTemporadaId,
            "Primera División Argentina Clausura $novoAno",
            ogOffset = MotorCampeonato.ARG_CLAUSURA_OG_OFFSET
        ) else -1

        // Renova Segunda División Argentina (com promoção/rebaixamento)
        val participantesArgB = if (campeonatoArgBId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoArgBId) else emptyList()
        val novosArgB = (participantesArgB - promovidosArgB.toSet()) + rebaixadosArgA
        val novoArgBId = if (novosArgB.isNotEmpty()) criarCampeonato(
            CampeonatoEntity(
                temporadaId  = novoTemporadaId,
                nome         = "Segunda División Argentina $novoAno",
                tipo         = TipoCampeonato.EXTRANGEIRO_DIVISAO2,
                formato      = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = (novosArgB.size - 1) * 2,
                pais         = "Argentina"
            ), novosArgB) else -1

        // ── Promoção / Rebaixamento — Uruguai ─────────────────────────────
        // Tabela Anual: soma de pontos do Apertura + Clausura + Intermediário
        val tabelaAnualUru = calcularTabelaAnualUruguai(campeonatoUruAperturaId, campeonatoUruClausuraId, campeonatoUruIntermedId)
        // 2 últimos rebaixados para a Segunda Divisão
        val rebaixadosUruA = tabelaAnualUru.entries.sortedBy { it.value }.take(2).map { it.key }
        // 2 primeiros da Segunda División Uruguaia promovidos (acesso direto)
        val promovidosUruB = if (campeonatoUruBId > 0) {
            classificacaoDao.buscarTabelaOrdenada(campeonatoUruBId)
                .sortedWith(compareByDescending<ClassificacaoEntity> { it.pontos }
                    .thenByDescending { it.vitorias }.thenByDescending { it.saldoGols })
                .take(2).map { it.timeId }
        } else emptyList()
        rebaixadosUruA.forEach { id -> timeRepository.buscarEntityPorId(id)?.let { timeRepository.atualizar(it.copy(divisao = 10)) } }
        promovidosUruB.forEach { id -> timeRepository.buscarEntityPorId(id)?.let { timeRepository.atualizar(it.copy(divisao = 9)) } }

        // Renova Primera División Uruguaia
        val participantesUruA = if (campeonatoUruAperturaId > 0) campeonatoDao.buscarIdsParticipantes(campeonatoUruAperturaId) else emptyList()
        val novosUruA = (participantesUruA - rebaixadosUruA.toSet()) + promovidosUruB
        val novoUruAperturaId = if (novosUruA.isNotEmpty()) criarUruguaiApertura(novosUruA, novoTemporadaId, novoAno) else -1
        val novoUruIntermedId = if (novosUruA.isNotEmpty()) criarUruguaiIntermediario(novosUruA, novoTemporadaId, novoAno) else -1
        val novoUruClausuraId = if (novoUruAperturaId > 0) criarUruguaiClausura(novoUruAperturaId, novosUruA, novoTemporadaId, novoAno) else -1

        // Renova Segunda División Uruguaia
        val participantesUruB = if (campeonatoUruBId > 0) campeonatoDao.buscarIdsParticipantes(campeonatoUruBId) else emptyList()
        val novosUruB = (participantesUruB - promovidosUruB.toSet()) + rebaixadosUruA
        val novoUruBId = if (novosUruB.isNotEmpty()) criarCampeonato(
            CampeonatoEntity(temporadaId = novoTemporadaId, nome = "Segunda División Uruguaia $novoAno",
                tipo = TipoCampeonato.EXTRANGEIRO_DIVISAO2, formato = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = (novosUruB.size - 1) * 2, pais = "Uruguay"), novosUruB) else -1
        val novoUruBCompetId = if (novosUruB.size >= 2) criarUruguaiSegundaDivCompetencia(novosUruB, novoTemporadaId, novoAno) else -1

        // Cria Copa do Brasil para a próxima temporada
        val participantesCopa = determinarParticipantesCopa(novoTemporadaId, novosA)
        val novoCopaId = criarCopa(
            CampeonatoEntity(
                temporadaId  = novoTemporadaId,
                nome         = "Copa do Brasil $novoAno",
                tipo         = TipoCampeonato.COPA_NACIONAL,
                formato      = FormatoCampeonato.MATA_MATA_IDA_VOLTA,
                totalRodadas = 12
            ),
            participantesCopa
        )

        // Cria Copa Argentina para a próxima temporada
        val participantesCopaArg = determinarParticipantesCopaArgentina(novoAperturaId, novoArgBId)
        val novoCopaArgId = if (participantesCopaArg.isNotEmpty()) criarCopaArgentina(
            CampeonatoEntity(
                temporadaId  = novoTemporadaId,
                nome         = "Copa Argentina $novoAno",
                tipo         = TipoCampeonato.COPA_NACIONAL,
                formato      = FormatoCampeonato.MATA_MATA_IDA_VOLTA,
                totalRodadas = 10,
                pais         = "Argentina"
            ),
            participantesCopaArg
        ) else -1

        // Cria Supercopa Rei para a próxima temporada:
        // campeão Série A do ano que encerrou vs campeão Copa do Brasil do ano que encerrou.
        val champBrasileiro = hallDaFamaDao.buscarCampeaoPorAnoEDivisao(ano, 1)
        val champCopa       = hallDaFamaDao.buscarCampeaoPorAnoEDivisao(ano, 5)
        val novoSupercopaId = if (champBrasileiro != null && champCopa != null) {
            criarSupercopa(
                campeonatoBrasileiraoId = champBrasileiro.campeaoTimeId,
                campeonatoCopaId        = champCopa.campeaoTimeId,
                ano                     = novoAno,
                temporadaId             = novoTemporadaId
            )
        } else -1

        return NovaTemporadaInfo(novoCampeonatoAId, novoCampeonatoBId, novoCampeonatoCId, novoCampeonatoDId, novoAperturaId, novoArgBId, novoClausuraId, novoUruAperturaId, novoUruBId, novoUruClausuraId, novoUruIntermedId, novoUruBCompetId, novoCopaId, novoCopaArgId, novoSupercopaId, novoTemporadaId, novoAno)
    }

    fun observarHallDaFama(): Flow<List<HallDaFamaEntity>> = hallDaFamaDao.observeTodos()

    fun observarCopaPartidas(copaId: Int): Flow<List<CopaPartidaDto>> =
        partidaDao.observeCopaPartidas(copaId)

    fun observarRankingGeral(): Flow<List<RankingGeralEntity>> =
        rankingGeralDao.observeRanking()

    // ══════════════════════════════════════════════════════════════
    //  Formato Argentine Apertura / Clausura (GRUPOS_E_MATA_MATA)
    // ══════════════════════════════════════════════════════════════

    /**
     * Cria um torneio Argentine Primera División (Apertura ou Clausura).
     * - Divide os 30 participantes aleatoriamente em Zona A e Zona B (15 cada)
     * - Gera fase de grupos: 15 rodadas intra-zona + 2 rodadas interzonais
     * - Knockout (Oitavas–Quartas–Semi–Final) gerado mais tarde via verificarEAvancarFaseArgentina
     * [ogOffset] = 0 para Apertura, ARG_CLAUSURA_OG_OFFSET (210) para Clausura
     */
    suspend fun criarArgentinaTorneio(
        participantes: List<Int>,
        temporadaId: Int,
        nome: String,
        ogOffset: Int = 0
    ): Int {
        if (participantes.size < 2) return -1
        val shuffled   = participantes.shuffled()
        val mid        = shuffled.size / 2
        val grupoA     = shuffled.subList(0, mid)
        val grupoB     = shuffled.subList(mid, shuffled.size)

        val campeonatoId = campeonatoDao.inserir(
            CampeonatoEntity(
                temporadaId  = temporadaId,
                nome         = nome,
                tipo         = TipoCampeonato.EXTRANGEIRO_DIVISAO1,
                formato      = FormatoCampeonato.GRUPOS_E_MATA_MATA,
                totalRodadas = MotorCampeonato.ARG_TOTAL_ROUNDS,
                pais         = "Argentina"
            )
        ).toInt()

        // Participantes com grupo atribuído
        campeonatoDao.inserirParticipantes(
            grupoA.map { CampeonatoTimeEntity(campeonatoId, it, MotorCampeonato.ARG_GRUPO_A) } +
            grupoB.map { CampeonatoTimeEntity(campeonatoId, it, MotorCampeonato.ARG_GRUPO_B) }
        )

        // Fase de grupos: turno único por zona (cap=ARG_ROUNDS_ZONA) + 2 rodadas interzonais
        val partidasZonaA   = MotorCampeonato.gerarTurnoUnicoGrupo(campeonatoId, grupoA, 1, ogOffset, MotorCampeonato.ARG_ROUNDS_ZONA)
        val partidasZonaB   = MotorCampeonato.gerarTurnoUnicoGrupo(campeonatoId, grupoB, 1, ogOffset, MotorCampeonato.ARG_ROUNDS_ZONA)
        val partidasInter   = MotorCampeonato.gerarInterzonais(campeonatoId, grupoA, grupoB,
            MotorCampeonato.ARG_ROUNDS_ZONA + 1, ogOffset)
        partidaDao.inserirTodas(partidasZonaA + partidasZonaB + partidasInter)

        // Classificação inicial por grupo
        val tabela =
            MotorCampeonato.gerarClassificacaoInicial(campeonatoId, grupoA, MotorCampeonato.ARG_GRUPO_A) +
            MotorCampeonato.gerarClassificacaoInicial(campeonatoId, grupoB, MotorCampeonato.ARG_GRUPO_B)
        classificacaoDao.inserirTodos(tabela)

        return campeonatoId
    }

    /**
     * Verifica se a fase atual do torneio argentine se completou e avança:
     * - Grupo completo → gera Oitavas
     * - Oitavas/Quartas/Semi completo → gera fase seguinte (resolvendo pênaltis de empates IA)
     * - Final completa → encerra torneio e registra Hall da Fama
     * Retorna true quando o torneio é encerrado.
     */
    suspend fun verificarEAvancarFaseArgentina(
        campeonatoId: Int,
        anoAtual: Int,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1
    ): Boolean {
        if (campeonatoId <= 0) return false
        val camp = campeonatoDao.buscarPorId(campeonatoId) ?: return false
        if (camp.encerrado) return false

        val todasPartidas = partidaDao.buscarTodasPorCampeonato(campeonatoId)
        if (todasPartidas.isEmpty()) return false

        val isClausura = todasPartidas.any { it.ordemGlobal > MotorCampeonato.ARG_CLAUSURA_OG_OFFSET + 10 }
        val ogOffset   = if (isClausura) MotorCampeonato.ARG_CLAUSURA_OG_OFFSET else 0

        // ── Transição: grupo → oitavas ─────────────────────────────
        val grupoCompleto = todasPartidas.any { it.fase == null } &&
                            todasPartidas.filter { it.fase == null }.all { it.jogada }
        val temOitavas    = todasPartidas.any { it.fase == "Oitavas" }

        if (grupoCompleto && !temOitavas) {
            val tabelaOrdenada = classificacaoDao.buscarTabelaOrdenada(campeonatoId)
            val top8A = tabelaOrdenada.filter { it.grupo == MotorCampeonato.ARG_GRUPO_A }.take(8).map { it.timeId }
            val top8B = tabelaOrdenada.filter { it.grupo == MotorCampeonato.ARG_GRUPO_B }.take(8).map { it.timeId }
            if (top8A.size < 8 || top8B.size < 8) return false
            val oitavas = MotorCampeonato.gerarOitavasArgentina(
                campeonatoId, top8A, top8B, MotorCampeonato.ARG_ROUND_KNOCKOUT_START, ogOffset)
            partidaDao.inserirTodas(oitavas)
            return false
        }

        // ── Fases eliminatórias ────────────────────────────────────
        for ((faseIdx, fase) in MotorCampeonato.ARG_FASES_KNOCKOUT.withIndex()) {
            val partidasFase = todasPartidas.filter { it.fase == fase }
            if (partidasFase.isEmpty()) continue
            if (!partidasFase.all { it.jogada }) return false   // fase em andamento

            val proximaFase = MotorCampeonato.ARG_FASES_KNOCKOUT.getOrNull(faseIdx + 1)
            if (proximaFase != null && todasPartidas.any { it.fase == proximaFase }) continue  // já gerada

            // Resolve pênaltis de empates IA e coleta vencedores
            val vencedores = mutableListOf<Int>()
            for (partida in partidasFase.sortedBy { it.id }) {
                val gC = partida.golsCasa ?: continue
                val gF = partida.golsFora ?: continue
                val vencedor = when {
                    gC > gF -> partida.timeCasaId
                    gF > gC -> partida.timeForaId
                    else -> {
                        val pC = partida.penaltisCasa
                        val pF = partida.penaltisForaId
                        if (pC != null && pF != null) {
                            if (pC > pF) partida.timeCasaId else partida.timeForaId
                        } else {
                            // IA vs IA draw → simulate penalties now
                            simularEPersistirPenaltisIA(partida, partida)
                        }
                    }
                }
                vencedores.add(vencedor)
            }

            if (proximaFase == null) {
                // ── Final concluída → encerrar torneio ──
                val campeaoId = vencedores.firstOrNull() ?: return true
                val finalPartida = partidasFase.firstOrNull() ?: return true
                val viceId = if (campeaoId == finalPartida.timeCasaId) finalPartida.timeForaId else finalPartida.timeCasaId

                val divisaoHallDaFama = if (isClausura) 8 else 7  // 7=Apertura, 8=Clausura
                val campeaoEnt = timeRepository.buscarEntityPorId(campeaoId)
                val viceEnt    = timeRepository.buscarEntityPorId(viceId)
                val artilheiro = partidaDao.buscarArtilheiroTop1(campeonatoId)
                val assistente = partidaDao.buscarAssisteTop1(campeonatoId)

                hallDaFamaDao.inserir(HallDaFamaEntity(
                    ano                  = anoAtual,
                    nomeCampeonato       = camp.nome,
                    campeaoTimeId        = campeaoId,
                    campeaoNome          = campeaoEnt?.nome ?: "",
                    campeaoEscudo        = campeaoEnt?.escudoRes ?: "",
                    viceTimeId           = viceId,
                    viceNome             = viceEnt?.nome ?: "",
                    viceEscudo           = viceEnt?.escudoRes ?: "",
                    artilheiroId         = artilheiro?.jogadorId ?: -1,
                    artilheiroNome       = artilheiro?.nomeJogador ?: "",
                    artilheiroNomeAbrev  = artilheiro?.nomeAbrev ?: "",
                    artilheiroGols       = artilheiro?.total ?: 0,
                    artilheiroNomeTime   = artilheiro?.nomeTime ?: "",
                    artilheiroEscudo     = artilheiro?.escudoRes ?: "",
                    assistenteId         = assistente?.jogadorId ?: -1,
                    assistenteNome       = assistente?.nomeJogador ?: "",
                    assistenteNomeAbrev  = assistente?.nomeAbrev ?: "",
                    assistenciasTotais   = assistente?.total ?: 0,
                    assistenteNomeTime   = assistente?.nomeTime ?: "",
                    assistenteEscudo     = assistente?.escudoRes ?: "",
                    divisao              = divisaoHallDaFama
                ))

                // Premiação ao time do jogador
                if (timeJogadorId > 0) {
                    val top2 = listOf(campeaoId to "Campeão", viceId to "Vice-campeão")
                    for ((timePremiado, posicao) in top2) {
                        if (timePremiado != timeJogadorId) continue
                        val valor = if (posicao == "Campeão") 15_000_000_00L else 7_500_000_00L
                        timeRepository.creditarSaldo(timeJogadorId, valor)
                        financaDao.inserir(br.com.managerfoot.data.database.entities.FinancaEntity(
                            timeId = timeJogadorId, temporadaId = temporadaId, mes = 13,
                            receitaPremiacoes = valor,
                            descricaoPremio   = "${camp.nome} — $posicao",
                            saldoFinal        = valor
                        ))
                    }
                }

                // Título do campeão é contabilizado em encerrarTemporadaComHallDaFama
                // via registrarTituloArgentino() para evitar dupla contagem.
                // Aqui apenas premiamos o vice-campeão com pontos.
                val rankVice = rankingGeralDao.buscarPorTime(viceId)
                val rankViceArg = rankingGeralDao.buscarPorTime(viceId)
                    ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                        timeId = viceId, nomeTime = viceEnt?.nome ?: "",
                        escudoRes = viceEnt?.escudoRes ?: "", divisaoAtual = viceEnt?.divisao ?: 5
                    )
                rankingGeralDao.inserirOuAtualizar(rankViceArg.copy(
                    pontosAcumulados = rankViceArg.pontosAcumulados + 8L
                ))

                campeonatoDao.encerrar(campeonatoId)
                return true
            }

            // Gera próxima fase
            val rodadaProxima = MotorCampeonato.ARG_ROUND_KNOCKOUT_START +
                    MotorCampeonato.ARG_FASES_KNOCKOUT.indexOf(proximaFase)
            val novasPartidas = MotorCampeonato.gerarProximaFaseKnockoutArgentina(
                campeonatoId, vencedores, rodadaProxima, proximaFase, ogOffset)
            partidaDao.inserirTodas(novasPartidas)
            return false
        }
        return false
    }

    /**
     * Avança automaticamente as fases do torneio Argentine quando o jogador não participa
     * (já eliminado ou jogo num time sem participação).
     * Simula TODAS as fases/rodadas pendentes de uma vez até o torneio ser encerrado,
     * garantindo que o campeão seja definido mesmo quando o jogador não tem mais jogos
     * suficientes para acionar chamadas individuais.
     */
    suspend fun simularProximaFaseArgentinaSeNecessario(
        campeonatoId: Int,
        timeJogadorId: Int,
        anoAtual: Int,
        temporadaId: Int = 1
    ) {
        if (campeonatoId <= 0) return
        if (campeonatoDao.buscarPorId(campeonatoId)?.encerrado == true) return

        val todasPartidas = partidaDao.buscarTodasPorCampeonato(campeonatoId)

        // Se jogador ainda tem partidas pendentes neste torneio, não avança IA
        val playerStillIn = todasPartidas.any {
            !it.jogada && (it.timeCasaId == timeJogadorId || it.timeForaId == timeJogadorId)
        }
        if (playerStillIn) return

        // Simula TODAS as fases/rodadas pendentes até o torneio ser encerrado
        var iteracoes = 10 // limite de segurança para evitar loop infinito
        while (iteracoes-- > 0) {
            if (campeonatoDao.buscarPorId(campeonatoId)?.encerrado == true) return

            val pendentes = partidaDao.buscarTodasPorCampeonato(campeonatoId).filter { !it.jogada }
            if (pendentes.isEmpty()) {
                // Todas as partidas da fase atual já foram jogadas — avança/finaliza
                verificarEAvancarFaseArgentina(campeonatoId, anoAtual, timeJogadorId, temporadaId)
                return
            }

            // Simula a menor rodada pendente
            val proximaRodada = pendentes.minByOrNull { it.rodada }?.rodada ?: return
            val partidasRodada = pendentes.filter { it.rodada == proximaRodada }
            for (p in partidasRodada) {
                try { simularPartidaInterna(p) } catch (_: Exception) {}
            }
            campeonatoDao.avancarRodada(campeonatoId)
            val encerrou = verificarEAvancarFaseArgentina(campeonatoId, anoAtual, timeJogadorId, temporadaId)
            if (encerrou) return
            // se não encerrou, continua o loop para simular a próxima fase/rodada
        }
    }

    /** Retorna true se o campeonato com [campeonatoId] está encerrado. */
    suspend fun isCampeonatoEncerrado(campeonatoId: Int): Boolean =
        campeonatoDao.buscarPorId(campeonatoId)?.encerrado == true

    /**
     * Retorna o timeId do campeão da Copa (ida/volta) se encerrada, ou -1 caso contrário.
     * Usado para exibir a vaga extra da Copa do Brasil na tabela da Série A.
     */
    suspend fun buscarCampeaoCopaSeEncerrada(copaId: Int): Int {
        if (copaId <= 0) return -1
        val copa = campeonatoDao.buscarPorId(copaId) ?: return -1
        if (!copa.encerrado) return -1
        val todasPartidas = partidaDao.buscarTodasPorCampeonato(copaId)
        val finalMatches = todasPartidas.filter { it.fase == "Final" && it.jogada }
        if (finalMatches.isEmpty()) return -1
        val ida   = finalMatches.minByOrNull { it.rodada } ?: return -1
        val volta = finalMatches.maxByOrNull { it.rodada } ?: return -1
        if (ida == volta) {
            val gC = ida.golsCasa ?: return -1
            val gF = ida.golsFora ?: return -1
            return when { gC > gF -> ida.timeCasaId; gF > gC -> ida.timeForaId; else -> -1 }
        }
        val vencedorDireto = MotorCampeonato.determinarVencedorTie(
            timeCasaIdaId = ida.timeCasaId,
            timeForaIdaId = ida.timeForaId,
            golsCasaIda   = ida.golsCasa   ?: 0,
            golsForaIda   = ida.golsFora   ?: 0,
            golsCasaVolta = volta.golsCasa ?: 0,
            golsForaVolta = volta.golsFora ?: 0
        )
        if (vencedorDireto != null) return vencedorDireto
        val pCasa = volta.penaltisCasa
        val pFora = volta.penaltisForaId
        if (pCasa != null && pFora != null) {
            return if (pCasa > pFora) volta.timeCasaId else volta.timeForaId
        }
        return -1
    }

    /**
     * Avança o torneio argentino em exatamente UMA rodada (ou transição de fase),
     * garantindo sincronismo com o ritmo da liga do jogador quando ele não é argentino.
     * Diferente de [simularProximaFaseArgentinaSeNecessario], não tenta completar
     * o torneio inteiro — apenas dá um passo de cada vez.
     */
    suspend fun simularUmaRodadaArgentina(
        campeonatoId: Int,
        timeJogadorId: Int,
        anoAtual: Int,
        temporadaId: Int = 1
    ) {
        if (campeonatoId <= 0) return
        val camp = campeonatoDao.buscarPorId(campeonatoId) ?: return
        if (camp.encerrado) return

        val todasPartidas = partidaDao.buscarTodasPorCampeonato(campeonatoId)
        // Se o jogador ainda tem partidas pendentes neste torneio, não avança
        if (todasPartidas.any { !it.jogada && (it.timeCasaId == timeJogadorId || it.timeForaId == timeJogadorId) }) return

        val pendentes = todasPartidas.filter { !it.jogada }
        if (pendentes.isEmpty()) {
            // Fase atual completa → avança/gera próxima fase ou encerra
            verificarEAvancarFaseArgentina(campeonatoId, anoAtual, timeJogadorId, temporadaId)
            return
        }

        // Simula apenas a menor rodada pendente (UMA rodada)
        val proximaRodada = pendentes.minByOrNull { it.rodada }?.rodada ?: return
        val partidasRodada = pendentes.filter { it.rodada == proximaRodada }
        for (p in partidasRodada) {
            try { simularPartidaInterna(p) } catch (_: Exception) {}
        }
        campeonatoDao.avancarRodada(campeonatoId)
        verificarEAvancarFaseArgentina(campeonatoId, anoAtual, timeJogadorId, temporadaId)
    }

    // ── Cria a Copa: gera Primeira Fase ──────────────────────────────
    suspend fun criarCopa(campeonato: CampeonatoEntity, participantes: List<Int>): Int {
        val copaId = campeonatoDao.inserir(campeonato).toInt()
        campeonatoDao.inserirParticipantes(participantes.map { CampeonatoTimeEntity(copaId, it) })
        val pares = MotorCampeonato.sortearPares(participantes)
        val partidas = MotorCampeonato.gerarFaseIdaVolta(
            campeonatoId = copaId,
            pares = pares,
            fase = MotorCampeonato.COPA_FASES[0],
            rodadaIda = MotorCampeonato.rodadaIdaDeFase(0),
            confrontoIdInicio = 1,
            ordemGlobalIda   = MotorCampeonato.COPA_ORDEM_GLOBAL[0],
            ordemGlobalVolta = MotorCampeonato.COPA_ORDEM_GLOBAL[1]
        )
        partidaDao.inserirTodas(partidas)
        return copaId
    }

    // ── Cria Copa Argentina: 32 times — inicia Primeira Fase (16 confrontos ida/volta) ──
    suspend fun criarCopaArgentina(campeonato: CampeonatoEntity, participantes: List<Int>): Int {
        val copaArgId = campeonatoDao.inserir(campeonato).toInt()
        campeonatoDao.inserirParticipantes(participantes.map { CampeonatoTimeEntity(copaArgId, it) })
        val pares = MotorCampeonato.sortearPares(participantes)
        val partidas = MotorCampeonato.gerarFaseIdaVolta(
            campeonatoId      = copaArgId,
            pares             = pares,
            fase              = MotorCampeonato.COPA_ARG_FASES[0],
            rodadaIda         = MotorCampeonato.rodadaIdaDeFase(0),
            confrontoIdInicio = 1,
            ordemGlobalIda    = MotorCampeonato.COPA_ARG_ORDEM_GLOBAL[0],
            ordemGlobalVolta  = MotorCampeonato.COPA_ARG_ORDEM_GLOBAL[1]
        )
        partidaDao.inserirTodas(partidas)
        return copaArgId
    }

    // ── Determina participantes Copa Argentina: todos da Primera División + top 2 da Segunda ──
    suspend fun determinarParticipantesCopaArgentina(
        campeonatoArgAId: Int,
        campeonatoArgBId: Int
    ): List<Int> {
        val primeiraDiv = if (campeonatoArgAId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoArgAId)
        else
            timeRepository.buscarTodosOrdenadosPorReputacao()
                .filter { it.pais == "Argentina" && it.divisao == 5 }.map { it.id }

        val top2SegundaDiv = if (campeonatoArgBId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoArgBId)
                .let { ids -> ids.mapNotNull { timeRepository.buscarPorId(it) }
                    .sortedByDescending { it.reputacao }.take(2).map { it.id } }
        else
            timeRepository.buscarTodosOrdenadosPorReputacao()
                .filter { it.pais == "Argentina" && it.divisao == 6 }.take(2).map { it.id }

        return (primeiraDiv + top2SegundaDiv).distinct().take(32)
    }

    // ── Verifica se fase atual da Copa Argentina foi concluída e avança para próxima ──
    suspend fun verificarEAvancarFaseCopaArgentina(
        copaArgId: Int,
        anoAtual: Int,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1
    ): Boolean = verificarEAvancarFaseCopa(
        copaId            = copaArgId,
        anoAtual          = anoAtual,
        timeJogadorId     = timeJogadorId,
        temporadaId       = temporadaId,
        fases             = MotorCampeonato.COPA_ARG_FASES,
        ordemGlobal       = MotorCampeonato.COPA_ARG_ORDEM_GLOBAL,
        divisaoHallDaFama = 9,
        premioCampeao     = 2_000_000_00L,
        premioVice        = 1_000_000_00L
    )

    // ── Continua Copa Argentina quando jogador já foi eliminado ──────
    suspend fun simularProximaFaseCopaArgentinaSeJogadorEliminado(
        copaArgId: Int,
        timeJogadorId: Int,
        anoAtual: Int
    ) {
        if (copaArgId <= 0) return
        if (campeonatoDao.buscarPorId(copaArgId)?.encerrado == true) return

        val todasPartidas = partidaDao.buscarTodasPorCampeonato(copaArgId)
        if (todasPartidas.any { !it.jogada && (it.timeCasaId == timeJogadorId || it.timeForaId == timeJogadorId) }) return

        val pendentes = todasPartidas.filter { !it.jogada }
        if (pendentes.isEmpty()) {
            verificarEAvancarFaseCopaArgentina(copaArgId, anoAtual)
            return
        }
        pendentes.map { it.rodada }.toSortedSet().forEach { rodada ->
            simularRodada(copaArgId, rodada)
        }
        verificarEAvancarFaseCopaArgentina(copaArgId, anoAtual)
    }

    // ══════════════════════════════════════════════════════════════
    //  Campeonato Uruguaio — Apertura / Intermediário / Clausura / Playoff
    // ══════════════════════════════════════════════════════════════

    /**
     * Cria o Torneio Apertura Uruguaio.
     * 16 times, turno único (15 rodadas), OG = rodada*10 (10–150).
     */
    suspend fun criarUruguaiApertura(
        participantes: List<Int>,
        temporadaId: Int,
        ano: Int
    ): Int {
        if (participantes.size < 2) return -1
        val campeonatoId = campeonatoDao.inserir(
            CampeonatoEntity(
                temporadaId  = temporadaId,
                nome         = "Torneio Apertura Uruguai $ano",
                tipo         = TipoCampeonato.EXTRANGEIRO_DIVISAO1,
                formato      = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = MotorCampeonato.URU_ROUNDS_APERTURA,
                pais         = "Uruguay"
            )
        ).toInt()
        campeonatoDao.inserirParticipantes(participantes.map { CampeonatoTimeEntity(campeonatoId, it) })
        val partidas = MotorCampeonato.gerarTurnoUnicoGrupo(campeonatoId, participantes, rodadaBase = 1, ogOffset = 0)
        partidaDao.inserirTodas(partidas)
        classificacaoDao.inserirTodos(MotorCampeonato.gerarClassificacaoInicial(campeonatoId, participantes))
        return campeonatoId
    }

    /**
     * Cria o Torneio Clausura Uruguaio.
     * 16 times, turno único com mandos de campo invertidos em relação ao Apertura (15 rodadas).
     * OG = ordemGlobal_apertura + URU_CLAUSURA_OG_OFFSET → 260–400.
     * Adicionalmente recebe playoff phases (Semi R16/Final R17) via verificarEAvancarPlayoffUruguai.
     */
    suspend fun criarUruguaiClausura(
        aperturaId: Int,
        participantes: List<Int>,
        temporadaId: Int,
        ano: Int
    ): Int {
        if (participantes.size < 2) return -1
        val clausuraId = campeonatoDao.inserir(
            CampeonatoEntity(
                temporadaId  = temporadaId,
                nome         = "Torneio Clausura Uruguai $ano",
                tipo         = TipoCampeonato.EXTRANGEIRO_DIVISAO1,
                formato      = FormatoCampeonato.GRUPOS_E_MATA_MATA,   // permite fases de playoff
                totalRodadas = MotorCampeonato.URU_PLAYOFF_FINAL_RODADA, // até R17
                pais         = "Uruguay"
            )
        ).toInt()
        campeonatoDao.inserirParticipantes(participantes.map { CampeonatoTimeEntity(clausuraId, it) })
        // Mandos de campo invertidos: swap timeCasaId ↔ timeForaId do Apertura
        val aperturaPartidas = partidaDao.buscarTodasPorCampeonato(aperturaId).filter { it.fase == null }
        val clausuraPartidas = aperturaPartidas.map { ap ->
            PartidaEntity(
                campeonatoId = clausuraId,
                rodada       = ap.rodada,
                timeCasaId   = ap.timeForaId,  // invertido
                timeForaId   = ap.timeCasaId,  // invertido
                ordemGlobal  = ap.ordemGlobal + MotorCampeonato.URU_CLAUSURA_OG_OFFSET
            )
        }
        partidaDao.inserirTodas(clausuraPartidas)
        classificacaoDao.inserirTodos(MotorCampeonato.gerarClassificacaoInicial(clausuraId, participantes))
        return clausuraId
    }

    /**
     * Cria o Torneio Intermediário Uruguaio.
     * 16 times divididos em 2 grupos de 8, turno único por grupo (7 rodadas) + Final (R8).
     * OG de grupos: rodada*10 + URU_INTERM_OG_OFFSET (165–225); Final OG = 235.
     * A Final é gerada ao encerrar o grupo via verificarEAvancarFaseIntermediarioUruguai.
     */
    suspend fun criarUruguaiIntermediario(
        participantes: List<Int>,
        temporadaId: Int,
        ano: Int
    ): Int {
        if (participantes.size < 2) return -1
        val shuffled = participantes.shuffled()
        val mid      = shuffled.size / 2
        val grupoA   = shuffled.subList(0, mid)
        val grupoB   = shuffled.subList(mid, shuffled.size)

        val campeonatoId = campeonatoDao.inserir(
            CampeonatoEntity(
                temporadaId  = temporadaId,
                nome         = "Torneio Intermediário Uruguai $ano",
                tipo         = TipoCampeonato.EXTRANGEIRO_DIVISAO1,
                formato      = FormatoCampeonato.GRUPOS_E_MATA_MATA,
                totalRodadas = MotorCampeonato.URU_INTERM_FINAL_RODADA,
                pais         = "Uruguay"
            )
        ).toInt()
        campeonatoDao.inserirParticipantes(
            grupoA.map { CampeonatoTimeEntity(campeonatoId, it, MotorCampeonato.ARG_GRUPO_A) } +
            grupoB.map { CampeonatoTimeEntity(campeonatoId, it, MotorCampeonato.ARG_GRUPO_B) }
        )
        val partidasA = MotorCampeonato.gerarTurnoUnicoGrupo(
            campeonatoId, grupoA, rodadaBase = 1,
            ogOffset = MotorCampeonato.URU_INTERM_OG_OFFSET,
            maxRodadas = MotorCampeonato.URU_ROUNDS_INTERM_GROUP
        )
        val partidasB = MotorCampeonato.gerarTurnoUnicoGrupo(
            campeonatoId, grupoB, rodadaBase = 1,
            ogOffset = MotorCampeonato.URU_INTERM_OG_OFFSET,
            maxRodadas = MotorCampeonato.URU_ROUNDS_INTERM_GROUP
        )
        partidaDao.inserirTodas(partidasA + partidasB)
        val tabela =
            MotorCampeonato.gerarClassificacaoInicial(campeonatoId, grupoA, MotorCampeonato.ARG_GRUPO_A) +
            MotorCampeonato.gerarClassificacaoInicial(campeonatoId, grupoB, MotorCampeonato.ARG_GRUPO_B)
        classificacaoDao.inserirTodos(tabela)
        return campeonatoId
    }

    /**
     * Verifica se os grupos do Intermediário terminaram e gera a Final;
     * e se a Final terminou, encerra o torneio e registra o Hall da Fama.
     * Retorna true quando o torneio é encerrado.
     */
    suspend fun verificarEAvancarFaseIntermediarioUruguai(
        intermedidId: Int,
        anoAtual: Int,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1
    ): Boolean {
        if (intermedidId <= 0) return false
        val camp = campeonatoDao.buscarPorId(intermedidId) ?: return false
        if (camp.encerrado) return false

        val todasPartidas = partidaDao.buscarTodasPorCampeonato(intermedidId)
        if (todasPartidas.isEmpty()) return false

        val grupoPartidas = todasPartidas.filter { it.fase == null }
        val finalPartidas = todasPartidas.filter { it.fase == "Final" }

        // Grupos → Final
        if (grupoPartidas.isNotEmpty() && grupoPartidas.all { it.jogada } && finalPartidas.isEmpty()) {
            val tabelaOrdenada = classificacaoDao.buscarTabelaOrdenada(intermedidId)
            val liderA = tabelaOrdenada.filter { it.grupo == MotorCampeonato.ARG_GRUPO_A }.firstOrNull()?.timeId
                ?: return false
            val liderB = tabelaOrdenada.filter { it.grupo == MotorCampeonato.ARG_GRUPO_B }.firstOrNull()?.timeId
                ?: return false
            partidaDao.inserirTodas(listOf(PartidaEntity(
                campeonatoId = intermedidId,
                rodada       = MotorCampeonato.URU_INTERM_FINAL_RODADA,
                timeCasaId   = liderA,
                timeForaId   = liderB,
                fase         = "Final",
                ordemGlobal  = MotorCampeonato.URU_INTERM_OG_FINAL
            )))
            return false
        }

        // Final concluída → registra Hall da Fama e encerra
        if (finalPartidas.isNotEmpty() && finalPartidas.all { it.jogada }) {
            val finalPartida = finalPartidas.first()
            val gC = finalPartida.golsCasa ?: return false
            val gF = finalPartida.golsFora ?: return false
            val campeaoId = when {
                gC > gF -> finalPartida.timeCasaId
                gF > gC -> finalPartida.timeForaId
                else    -> {
                    val pC = finalPartida.penaltisCasa; val pF = finalPartida.penaltisForaId
                    if (pC != null && pF != null) { if (pC > pF) finalPartida.timeCasaId else finalPartida.timeForaId }
                    else simularEPersistirPenaltisIA(finalPartida, finalPartida)
                }
            }
            val viceId     = if (campeaoId == finalPartida.timeCasaId) finalPartida.timeForaId else finalPartida.timeCasaId
            val campeaoEnt = timeRepository.buscarEntityPorId(campeaoId)
            val viceEnt    = timeRepository.buscarEntityPorId(viceId)
            val artilheiro = partidaDao.buscarArtilheiroTop1(intermedidId)
            val assistente = partidaDao.buscarAssisteTop1(intermedidId)
            hallDaFamaDao.inserir(HallDaFamaEntity(
                ano = anoAtual, nomeCampeonato = camp.nome,
                campeaoTimeId = campeaoId, campeaoNome = campeaoEnt?.nome ?: "", campeaoEscudo = campeaoEnt?.escudoRes ?: "",
                viceTimeId = viceId, viceNome = viceEnt?.nome ?: "", viceEscudo = viceEnt?.escudoRes ?: "",
                artilheiroId = artilheiro?.jogadorId ?: -1, artilheiroNome = artilheiro?.nomeJogador ?: "",
                artilheiroNomeAbrev = artilheiro?.nomeAbrev ?: "", artilheiroGols = artilheiro?.total ?: 0,
                artilheiroNomeTime = artilheiro?.nomeTime ?: "", artilheiroEscudo = artilheiro?.escudoRes ?: "",
                assistenteId = assistente?.jogadorId ?: -1, assistenteNome = assistente?.nomeJogador ?: "",
                assistenteNomeAbrev = assistente?.nomeAbrev ?: "", assistenciasTotais = assistente?.total ?: 0,
                assistenteNomeTime = assistente?.nomeTime ?: "", assistenteEscudo = assistente?.escudoRes ?: "",
                divisao = 12  // 12 = Intermediário Uruguai
            ))
            // Premiação
            if (timeJogadorId > 0 && (campeaoId == timeJogadorId || viceId == timeJogadorId)) {
                val (valor, pos) = if (campeaoId == timeJogadorId) 5_000_000_00L to "Campeão" else 2_500_000_00L to "Vice-campeão"
                timeRepository.creditarSaldo(timeJogadorId, valor)
                financaDao.inserir(br.com.managerfoot.data.database.entities.FinancaEntity(
                    timeId = timeJogadorId, temporadaId = temporadaId, mes = 13,
                    receitaPremiacoes = valor, descricaoPremio = "${camp.nome} — $pos", saldoFinal = valor
                ))
            }
            campeonatoDao.encerrar(intermedidId)
            return true
        }
        return false
    }

    // ══════════════════════════════════════════════════════════════
    //  Torneo Competencia – Segunda División Uruguaia
    //  14 times → 2 grupos de 7, turno único (6 rods) + Final (R7)
    // ══════════════════════════════════════════════════════════════

    /**
     * Cria o Torneo Competencia da Segunda División Uruguaia.
     * 14 times divididos em 2 grupos de 7, turno único por grupo (6 rodadas) + Final (R7).
     * OG de grupos: rodada*10 + URU_B_COMPET_OG_OFFSET (460–510); Final OG = 520.
     * A Final é gerada ao encerrar o grupo via verificarEAvancarFaseCompetenciaUruguaiB.
     */
    suspend fun criarUruguaiSegundaDivCompetencia(
        participantes: List<Int>,
        temporadaId: Int,
        ano: Int
    ): Int {
        if (participantes.size < 2) return -1
        val shuffled = participantes.shuffled()
        val mid      = shuffled.size / 2
        val grupoA   = shuffled.subList(0, mid)
        val grupoB   = shuffled.subList(mid, shuffled.size)

        val campeonatoId = campeonatoDao.inserir(
            CampeonatoEntity(
                temporadaId  = temporadaId,
                nome         = "Torneo Competencia Uruguay B $ano",
                tipo         = TipoCampeonato.EXTRANGEIRO_DIVISAO2,
                formato      = FormatoCampeonato.GRUPOS_E_MATA_MATA,
                totalRodadas = MotorCampeonato.URU_B_COMPET_FINAL_RODADA,
                pais         = "Uruguay"
            )
        ).toInt()
        campeonatoDao.inserirParticipantes(
            grupoA.map { CampeonatoTimeEntity(campeonatoId, it, MotorCampeonato.ARG_GRUPO_A) } +
            grupoB.map { CampeonatoTimeEntity(campeonatoId, it, MotorCampeonato.ARG_GRUPO_B) }
        )
        val partidasA = MotorCampeonato.gerarTurnoUnicoGrupo(
            campeonatoId, grupoA, rodadaBase = 1,
            ogOffset = MotorCampeonato.URU_B_COMPET_OG_OFFSET,
            maxRodadas = MotorCampeonato.URU_B_COMPET_ROUNDS_GROUP
        )
        val partidasB = MotorCampeonato.gerarTurnoUnicoGrupo(
            campeonatoId, grupoB, rodadaBase = 1,
            ogOffset = MotorCampeonato.URU_B_COMPET_OG_OFFSET,
            maxRodadas = MotorCampeonato.URU_B_COMPET_ROUNDS_GROUP
        )
        partidaDao.inserirTodas(partidasA + partidasB)
        val tabela =
            MotorCampeonato.gerarClassificacaoInicial(campeonatoId, grupoA, MotorCampeonato.ARG_GRUPO_A) +
            MotorCampeonato.gerarClassificacaoInicial(campeonatoId, grupoB, MotorCampeonato.ARG_GRUPO_B)
        classificacaoDao.inserirTodos(tabela)
        return campeonatoId
    }

    /**
     * Verifica se os grupos do Torneo Competencia terminaram e gera a Final;
     * e se a Final terminou, encerra o torneio e registra o Hall da Fama.
     * Retorna true quando o torneio é encerrado.
     */
    suspend fun verificarEAvancarFaseCompetenciaUruguaiB(
        competId: Int,
        anoAtual: Int,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1
    ): Boolean {
        if (competId <= 0) return false
        val camp = campeonatoDao.buscarPorId(competId) ?: return false
        if (camp.encerrado) return false

        val todasPartidas = partidaDao.buscarTodasPorCampeonato(competId)
        if (todasPartidas.isEmpty()) return false

        val grupoPartidas = todasPartidas.filter { it.fase == null }
        val finalPartidas = todasPartidas.filter { it.fase == "Final" }

        // Grupos → Final
        if (grupoPartidas.isNotEmpty() && grupoPartidas.all { it.jogada } && finalPartidas.isEmpty()) {
            val tabelaOrdenada = classificacaoDao.buscarTabelaOrdenada(competId)
            val liderA = tabelaOrdenada.filter { it.grupo == MotorCampeonato.ARG_GRUPO_A }.firstOrNull()?.timeId
                ?: return false
            val liderB = tabelaOrdenada.filter { it.grupo == MotorCampeonato.ARG_GRUPO_B }.firstOrNull()?.timeId
                ?: return false
            partidaDao.inserirTodas(listOf(PartidaEntity(
                campeonatoId = competId,
                rodada       = MotorCampeonato.URU_B_COMPET_FINAL_RODADA,
                timeCasaId   = liderA,
                timeForaId   = liderB,
                fase         = "Final",
                ordemGlobal  = MotorCampeonato.URU_B_COMPET_OG_FINAL
            )))
            return false
        }

        // Final concluída → registra Hall da Fama e encerra
        if (finalPartidas.isNotEmpty() && finalPartidas.all { it.jogada }) {
            val finalPartida = finalPartidas.first()
            val gC = finalPartida.golsCasa ?: return false
            val gF = finalPartida.golsFora ?: return false
            val campeaoId = when {
                gC > gF -> finalPartida.timeCasaId
                gF > gC -> finalPartida.timeForaId
                else    -> {
                    val pC = finalPartida.penaltisCasa; val pF = finalPartida.penaltisForaId
                    if (pC != null && pF != null) { if (pC > pF) finalPartida.timeCasaId else finalPartida.timeForaId }
                    else simularEPersistirPenaltisIA(finalPartida, finalPartida)
                }
            }
            val viceId     = if (campeaoId == finalPartida.timeCasaId) finalPartida.timeForaId else finalPartida.timeCasaId
            val campeaoEnt = timeRepository.buscarEntityPorId(campeaoId)
            val viceEnt    = timeRepository.buscarEntityPorId(viceId)
            val artilheiro = partidaDao.buscarArtilheiroTop1(competId)
            val assistente = partidaDao.buscarAssisteTop1(competId)
            hallDaFamaDao.inserir(HallDaFamaEntity(
                ano = anoAtual, nomeCampeonato = camp.nome,
                campeaoTimeId = campeaoId, campeaoNome = campeaoEnt?.nome ?: "", campeaoEscudo = campeaoEnt?.escudoRes ?: "",
                viceTimeId = viceId, viceNome = viceEnt?.nome ?: "", viceEscudo = viceEnt?.escudoRes ?: "",
                artilheiroId = artilheiro?.jogadorId ?: -1, artilheiroNome = artilheiro?.nomeJogador ?: "",
                artilheiroNomeAbrev = artilheiro?.nomeAbrev ?: "", artilheiroGols = artilheiro?.total ?: 0,
                artilheiroNomeTime = artilheiro?.nomeTime ?: "", artilheiroEscudo = artilheiro?.escudoRes ?: "",
                assistenteId = assistente?.jogadorId ?: -1, assistenteNome = assistente?.nomeJogador ?: "",
                assistenteNomeAbrev = assistente?.nomeAbrev ?: "", assistenciasTotais = assistente?.total ?: 0,
                assistenteNomeTime = assistente?.nomeTime ?: "", assistenteEscudo = assistente?.escudoRes ?: "",
                divisao = 16  // 16 = Torneo Competencia URU B
            ))
            if (timeJogadorId > 0 && (campeaoId == timeJogadorId || viceId == timeJogadorId)) {
                val (valor, pos) = if (campeaoId == timeJogadorId) 2_000_000_00L to "Campeão" else 1_000_000_00L to "Vice-campeão"
                timeRepository.creditarSaldo(timeJogadorId, valor)
                financaDao.inserir(br.com.managerfoot.data.database.entities.FinancaEntity(
                    timeId = timeJogadorId, temporadaId = temporadaId, mes = 13,
                    receitaPremiacoes = valor, descricaoPremio = "${camp.nome} — $pos", saldoFinal = valor
                ))
            }
            campeonatoDao.encerrar(competId)
            return true
        }
        return false
    }

    /**
     * Helper para calcular a Tabela Anual uruguaia:
     * soma pontos do Apertura + Clausura (rodadas regulares) + Intermediário (grupos + final).
     */
    private suspend fun calcularTabelaAnualUruguai(
        aperturaId: Int,
        clausuraId: Int,
        intermedidId: Int
    ): Map<Int, Int> {
        val pontos = mutableMapOf<Int, Int>()
        suspend fun somar(campId: Int) {
            if (campId <= 0) return
            for (cls in classificacaoDao.buscarTabelaOrdenada(campId))
                pontos[cls.timeId] = (pontos[cls.timeId] ?: 0) + cls.pontos
        }
        somar(aperturaId); somar(clausuraId); somar(intermedidId)
        return pontos
    }

    /**
     * Determina o campeão do Apertura/Clausura (líder da tabela do torneio).
     * Retorna -1 se o campeonato não existe.
     */
    private suspend fun liderTabela(campId: Int): Int {
        if (campId <= 0) return -1
        return classificacaoDao.buscarTabelaOrdenada(campId)
            .firstOrNull { it.grupo == null }?.timeId
            ?: classificacaoDao.buscarTabelaOrdenada(campId).firstOrNull()?.timeId
            ?: -1
    }

    /**
     * Verifica o avanço do playoff do Campeonato Uruguaio (rodadas no Clausura):
     *  - Após todas as rodadas regulares do Clausura:
     *    • Arrastão → campeão direto
     *    • Líder da Tabela Anual = Apertura ou Clausura champ → Final direto
     *    • Caso normal → gera Semi (R16, OG 411)
     *  - Após Semi → gera Final (R17, OG 421)
     *  - Após Final → registra campeão e encerra
     * Retorna true quando o torneio é encerrado.
     */
    suspend fun verificarEAvancarPlayoffUruguai(
        clausuraId: Int,
        aperturaId: Int,
        intermedidId: Int,
        anoAtual: Int,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1
    ): Boolean {
        if (clausuraId <= 0) return false
        val camp = campeonatoDao.buscarPorId(clausuraId) ?: return false
        if (camp.encerrado) return false

        val todasPartidas = partidaDao.buscarTodasPorCampeonato(clausuraId)
        if (todasPartidas.isEmpty()) return false

        val rodadasReg  = todasPartidas.filter { it.fase == null }
        val semiPartidas = todasPartidas.filter { it.fase == "Semi" }
        val finalPartidas = todasPartidas.filter { it.fase == "Final" }

        // ── Todas as rodadas regulares devem estar completas ─────────────────
        if (rodadasReg.isEmpty() || !rodadasReg.all { it.jogada }) return false

        // ── Gera playoff (Semi ou Final, ou arrastão) ────────────────────────
        if (semiPartidas.isEmpty() && finalPartidas.isEmpty()) {
            val aperturaChampId = liderTabela(aperturaId).takeIf { it > 0 } ?: return false
            val clausuraChampId = liderTabela(clausuraId).takeIf { it > 0 } ?: return false
            val tabelaAnual = calcularTabelaAnualUruguai(aperturaId, clausuraId, intermedidId)
            val anualLiderId = tabelaAnual.maxByOrNull { it.value }?.key ?: return false

            // Arrastão: mesmo time venceu Apertura e Clausura → campeão automático
            if (aperturaChampId == clausuraChampId) {
                registrarCampeaoUruguai(aperturaChampId, null, clausuraId, anoAtual, temporadaId, timeJogadorId)
                campeonatoDao.encerrar(clausuraId)
                return true
            }

            // Líder Anual = campeão do Apertura ou Clausura → Final direta (skip Semi)
            if (anualLiderId == aperturaChampId || anualLiderId == clausuraChampId) {
                val adversario = if (anualLiderId == aperturaChampId) clausuraChampId else aperturaChampId
                partidaDao.inserirTodas(listOf(PartidaEntity(
                    campeonatoId = clausuraId, rodada = MotorCampeonato.URU_PLAYOFF_FINAL_RODADA,
                    timeCasaId = anualLiderId, timeForaId = adversario,
                    fase = "Final", ordemGlobal = MotorCampeonato.URU_PLAYOFF_OG_FINAL
                )))
            } else {
                // Caso normal: Semi = Apertura champ vs Clausura champ
                partidaDao.inserirTodas(listOf(PartidaEntity(
                    campeonatoId = clausuraId, rodada = MotorCampeonato.URU_PLAYOFF_SEMI_RODADA,
                    timeCasaId = aperturaChampId, timeForaId = clausuraChampId,
                    fase = "Semi", ordemGlobal = MotorCampeonato.URU_PLAYOFF_OG_SEMI
                )))
            }
            return false
        }

        // ── Semi concluída → gera Final ──────────────────────────────────────
        if (semiPartidas.isNotEmpty() && finalPartidas.isEmpty()) {
            if (!semiPartidas.all { it.jogada }) return false
            val semiPartida = semiPartidas.first()
            val gC = semiPartida.golsCasa ?: return false
            val gF = semiPartida.golsFora ?: return false
            val semiVencedor = when {
                gC > gF -> semiPartida.timeCasaId
                gF > gC -> semiPartida.timeForaId
                else    -> {
                    val pC = semiPartida.penaltisCasa; val pF = semiPartida.penaltisForaId
                    if (pC != null && pF != null) { if (pC > pF) semiPartida.timeCasaId else semiPartida.timeForaId }
                    else simularEPersistirPenaltisIA(semiPartida, semiPartida)
                }
            }
            val tabelaAnual  = calcularTabelaAnualUruguai(aperturaId, clausuraId, intermedidId)
            val anualLiderId = tabelaAnual.maxByOrNull { it.value }?.key ?: return false
            partidaDao.inserirTodas(listOf(PartidaEntity(
                campeonatoId = clausuraId, rodada = MotorCampeonato.URU_PLAYOFF_FINAL_RODADA,
                timeCasaId = semiVencedor, timeForaId = anualLiderId,
                fase = "Final", ordemGlobal = MotorCampeonato.URU_PLAYOFF_OG_FINAL
            )))
            return false
        }

        // ── Final concluída → determina campeão ──────────────────────────────
        if (finalPartidas.isNotEmpty() && finalPartidas.all { it.jogada }) {
            val finalPartida = finalPartidas.first()
            val gC = finalPartida.golsCasa ?: return false
            val gF = finalPartida.golsFora ?: return false
            val campeaoId = when {
                gC > gF -> finalPartida.timeCasaId
                gF > gC -> finalPartida.timeForaId
                else    -> {
                    val pC = finalPartida.penaltisCasa; val pF = finalPartida.penaltisForaId
                    if (pC != null && pF != null) { if (pC > pF) finalPartida.timeCasaId else finalPartida.timeForaId }
                    else simularEPersistirPenaltisIA(finalPartida, finalPartida)
                }
            }
            registrarCampeaoUruguai(campeaoId, finalPartida, clausuraId, anoAtual, temporadaId, timeJogadorId)
            campeonatoDao.encerrar(clausuraId)
            return true
        }
        return false
    }

    /** Registra Hall da Fama e premiação do Campeão Uruguaio. */
    private suspend fun registrarCampeaoUruguai(
        campeaoId: Int,
        finalPartida: PartidaEntity?,
        clausuraId: Int,
        anoAtual: Int,
        temporadaId: Int,
        timeJogadorId: Int
    ) {
        val camp       = campeonatoDao.buscarPorId(clausuraId)
        val viceId     = if (finalPartida != null) {
            if (campeaoId == finalPartida.timeCasaId) finalPartida.timeForaId else finalPartida.timeCasaId
        } else -1
        val campeaoEnt = timeRepository.buscarEntityPorId(campeaoId)
        val viceEnt    = if (viceId > 0) timeRepository.buscarEntityPorId(viceId) else null
        val artilheiro = partidaDao.buscarArtilheiroTop1(clausuraId)
        val assistente = partidaDao.buscarAssisteTop1(clausuraId)
        hallDaFamaDao.inserir(HallDaFamaEntity(
            ano = anoAtual, nomeCampeonato = "Campeonato Uruguaio $anoAtual",
            campeaoTimeId = campeaoId, campeaoNome = campeaoEnt?.nome ?: "", campeaoEscudo = campeaoEnt?.escudoRes ?: "",
            viceTimeId = viceId, viceNome = viceEnt?.nome ?: "", viceEscudo = viceEnt?.escudoRes ?: "",
            artilheiroId = artilheiro?.jogadorId ?: -1, artilheiroNome = artilheiro?.nomeJogador ?: "",
            artilheiroNomeAbrev = artilheiro?.nomeAbrev ?: "", artilheiroGols = artilheiro?.total ?: 0,
            artilheiroNomeTime = artilheiro?.nomeTime ?: "", artilheiroEscudo = artilheiro?.escudoRes ?: "",
            assistenteId = assistente?.jogadorId ?: -1, assistenteNome = assistente?.nomeJogador ?: "",
            assistenteNomeAbrev = assistente?.nomeAbrev ?: "", assistenciasTotais = assistente?.total ?: 0,
            assistenteNomeTime = assistente?.nomeTime ?: "", assistenteEscudo = assistente?.escudoRes ?: "",
            divisao = 14  // 14 = Campeão Uruguaio
        ))
        // Ranking Geral
        val timeEnt = campeaoEnt ?: return
        val existing = rankingGeralDao.buscarPorTime(campeaoId)
            ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                timeId = campeaoId, nomeTime = timeEnt.nome, escudoRes = timeEnt.escudoRes, divisaoAtual = timeEnt.divisao)
        rankingGeralDao.inserirOuAtualizar(existing.copy(
            titulosNacionais = existing.titulosNacionais + 1,
            pontosAcumulados = existing.pontosAcumulados + 20L
        ))
        // Premiação ao jogador
        if (timeJogadorId > 0 && (campeaoId == timeJogadorId || viceId == timeJogadorId)) {
            val (valor, pos) = if (campeaoId == timeJogadorId) 20_000_000_00L to "Campeão" else 10_000_000_00L to "Vice-campeão"
            timeRepository.creditarSaldo(timeJogadorId, valor)
            financaDao.inserir(br.com.managerfoot.data.database.entities.FinancaEntity(
                timeId = timeJogadorId, temporadaId = temporadaId, mes = 13,
                receitaPremiacoes = valor, descricaoPremio = "Campeonato Uruguaio $anoAtual — $pos", saldoFinal = valor
            ))
        }
    }

    /**
     * Simula automaticamente os torneios uruguaios quando o jogador não participa
     * (time brasileiro, argentino, ou equipe uruguaia já eliminada do playoff).
     */
    suspend fun simularUruguaiSeNecessario(
        aperturaId: Int,
        clausuraId: Int,
        intermedidId: Int,
        competBId: Int = -1,
        timeJogadorId: Int,
        anoAtual: Int,
        temporadaId: Int = 1
    ) {
        // Não simula se o jogador ainda tem partidas pendentes em qualquer torneio uruguaio
        val todosCampIds = listOf(aperturaId, clausuraId, intermedidId, competBId).filter { it > 0 }
        for (campId in todosCampIds) {
            if (campeonatoDao.buscarPorId(campId)?.encerrado == true) continue
            val pendentes = partidaDao.buscarTodasPorCampeonato(campId).filter { !it.jogada }
            if (pendentes.any { it.timeCasaId == timeJogadorId || it.timeForaId == timeJogadorId }) return
        }

        // ── Cadência: avança EXATAMENTE UMA rodada da cadeia sequencial Apertura → Intermediário → Clausura
        // Isso garante que o Uruguai avança no mesmo ritmo que o time do jogador (1 rodada por partida jogada).
        // A Competencia B roda em paralelo (liga independente) e também avança 1 rodada por chamada.

        // ── Apertura ─────────────────────────────────────────────────────────
        if (aperturaId > 0 && campeonatoDao.buscarPorId(aperturaId)?.encerrado == false) {
            val pendentes = partidaDao.buscarTodasPorCampeonato(aperturaId).filter { !it.jogada }
            if (pendentes.isNotEmpty()) {
                val proxRodada = pendentes.minByOrNull { it.rodada }?.rodada ?: 0
                pendentes.filter { it.rodada == proxRodada }
                    .forEach { try { simularPartidaInterna(it) } catch (_: Exception) {} }
                campeonatoDao.avancarRodada(aperturaId)
                // Apertura rodada avançada — não prossegue para Intermediário nesta chamada
                avançarCompetenciaB(competBId, anoAtual, timeJogadorId, temporadaId)
                return
            }
        }

        // ── Intermediário (começa após Apertura encerrada) ────────────────────
        if (intermedidId > 0 && campeonatoDao.buscarPorId(intermedidId)?.encerrado == false) {
            val pendentes = partidaDao.buscarTodasPorCampeonato(intermedidId).filter { !it.jogada }
            if (pendentes.isNotEmpty()) {
                val proxRodada = pendentes.minByOrNull { it.rodada }?.rodada ?: 0
                pendentes.filter { it.rodada == proxRodada }
                    .forEach { try { simularPartidaInterna(it) } catch (_: Exception) {} }
                campeonatoDao.avancarRodada(intermedidId)
                verificarEAvancarFaseIntermediarioUruguai(intermedidId, anoAtual, timeJogadorId, temporadaId)
                avançarCompetenciaB(competBId, anoAtual, timeJogadorId, temporadaId)
                return
            }
            // Sem partidas pendentes mas não encerrado → grupos concluídos, gera Final
            val encerrou = verificarEAvancarFaseIntermediarioUruguai(intermedidId, anoAtual, timeJogadorId, temporadaId)
            if (!encerrou) {
                // Final pode ter sido inserida — simula-a imediatamente (transição de fase, não conta como rodada extra)
                val novas = partidaDao.buscarTodasPorCampeonato(intermedidId).filter { !it.jogada }
                if (novas.isNotEmpty()) {
                    val proxRodada = novas.minByOrNull { it.rodada }?.rodada ?: 0
                    novas.filter { it.rodada == proxRodada }
                        .forEach { try { simularPartidaInterna(it) } catch (_: Exception) {} }
                    campeonatoDao.avancarRodada(intermedidId)
                    verificarEAvancarFaseIntermediarioUruguai(intermedidId, anoAtual, timeJogadorId, temporadaId)
                }
            }
            avançarCompetenciaB(competBId, anoAtual, timeJogadorId, temporadaId)
            return
        }

        // ── Clausura + Playoff (começa após Intermediário encerrado) ──────────
        if (clausuraId > 0 && campeonatoDao.buscarPorId(clausuraId)?.encerrado == false) {
            val pendentes = partidaDao.buscarTodasPorCampeonato(clausuraId).filter { !it.jogada }
            if (pendentes.isNotEmpty()) {
                val proxRodada = pendentes.minByOrNull { it.rodada }?.rodada ?: 0
                pendentes.filter { it.rodada == proxRodada }
                    .forEach { try { simularPartidaInterna(it) } catch (_: Exception) {} }
                campeonatoDao.avancarRodada(clausuraId)
                verificarEAvancarPlayoffUruguai(clausuraId, aperturaId, intermedidId, anoAtual, timeJogadorId, temporadaId)
                avançarCompetenciaB(competBId, anoAtual, timeJogadorId, temporadaId)
                return
            }
            // Sem partidas pendentes mas não encerrado → gera fase seguinte do Playoff
            val encerrou = verificarEAvancarPlayoffUruguai(clausuraId, aperturaId, intermedidId, anoAtual, timeJogadorId, temporadaId)
            if (!encerrou) {
                val novas = partidaDao.buscarTodasPorCampeonato(clausuraId).filter { !it.jogada }
                if (novas.isNotEmpty()) {
                    val proxRodada = novas.minByOrNull { it.rodada }?.rodada ?: 0
                    novas.filter { it.rodada == proxRodada }
                        .forEach { try { simularPartidaInterna(it) } catch (_: Exception) {} }
                    campeonatoDao.avancarRodada(clausuraId)
                    verificarEAvancarPlayoffUruguai(clausuraId, aperturaId, intermedidId, anoAtual, timeJogadorId, temporadaId)
                }
            }
        }

        // Competencia B em paralelo (mesmo que cadeia Principal não tenha avançado)
        avançarCompetenciaB(competBId, anoAtual, timeJogadorId, temporadaId)
    }

    /** Avança exatamente uma rodada do Torneo Competencia B. Chamado em paralelo com a cadeia Principal. */
    private suspend fun avançarCompetenciaB(
        competBId: Int,
        anoAtual: Int,
        timeJogadorId: Int,
        temporadaId: Int
    ) {
        if (competBId <= 0) return
        val camp = campeonatoDao.buscarPorId(competBId) ?: return
        if (camp.encerrado) return
        val pendentes = partidaDao.buscarTodasPorCampeonato(competBId).filter { !it.jogada }
        if (pendentes.isNotEmpty()) {
            val proxRodada = pendentes.minByOrNull { it.rodada }?.rodada ?: return
            pendentes.filter { it.rodada == proxRodada }
                .forEach { try { simularPartidaInterna(it) } catch (_: Exception) {} }
            campeonatoDao.avancarRodada(competBId)
            verificarEAvancarFaseCompetenciaUruguaiB(competBId, anoAtual, timeJogadorId, temporadaId)
        } else {
            // Sem pendentes: tenta gerar próxima fase (Final) e simula-a
            val encerrou = verificarEAvancarFaseCompetenciaUruguaiB(competBId, anoAtual, timeJogadorId, temporadaId)
            if (!encerrou) {
                val novas = partidaDao.buscarTodasPorCampeonato(competBId).filter { !it.jogada }
                if (novas.isNotEmpty()) {
                    val proxRodada = novas.minByOrNull { it.rodada }?.rodada ?: return
                    novas.filter { it.rodada == proxRodada }
                        .forEach { try { simularPartidaInterna(it) } catch (_: Exception) {} }
                    campeonatoDao.avancarRodada(competBId)
                    verificarEAvancarFaseCompetenciaUruguaiB(competBId, anoAtual, timeJogadorId, temporadaId)
                }
            }
        }
    }

    /** Persiste pênaltis interativos do jogador no Intermediário e avança a fase. */
    suspend fun persistirResultadoPenaltisJogadorUruguaiIntermediario(
        resultado: br.com.managerfoot.domain.model.ResultadoPenaltis,
        intermedidId: Int,
        anoAtual: Int,
        partidaId: Int,
        timeJogadorId: Int,
        temporadaId: Int
    ) {
        partidaDao.registrarPenaltis(partidaId, resultado.golsCasa, resultado.golsFora)
        verificarEAvancarFaseIntermediarioUruguai(intermedidId, anoAtual, timeJogadorId, temporadaId)
    }

    /** Persiste pênaltis interativos do jogador no playoff Clausura e avança. */
    suspend fun persistirResultadoPenaltisJogadorUruguaiPlayoff(
        resultado: br.com.managerfoot.domain.model.ResultadoPenaltis,
        clausuraId: Int,
        aperturaId: Int,
        intermedidId: Int,
        anoAtual: Int,
        partidaId: Int,
        timeJogadorId: Int,
        temporadaId: Int
    ) {
        partidaDao.registrarPenaltis(partidaId, resultado.golsCasa, resultado.golsFora)
        verificarEAvancarPlayoffUruguai(clausuraId, aperturaId, intermedidId, anoAtual, timeJogadorId, temporadaId)
    }

    // ── Supercopa Rei: jogo único entre campeão do Brasileirão e da Copa ──
    // ordemGlobal = 1 → disputada no final de janeiro, antes de qualquer outra partida.
    suspend fun criarSupercopa(
        campeonatoBrasileiraoId: Int,   // timeId do campeão do Brasileirão
        campeonatoCopaId: Int,          // timeId do campeão da Copa do Brasil
        ano: Int,
        temporadaId: Int
    ): Int {
        val supercopaId = campeonatoDao.inserir(
            CampeonatoEntity(
                temporadaId  = temporadaId,
                nome         = "Supercopa Rei $ano",
                tipo         = TipoCampeonato.SUPERCOPA,
                formato      = FormatoCampeonato.MATA_MATA_SIMPLES,
                totalRodadas = 1
            )
        ).toInt()
        campeonatoDao.inserirParticipantes(listOf(
            CampeonatoTimeEntity(supercopaId, campeonatoBrasileiraoId),
            CampeonatoTimeEntity(supercopaId, campeonatoCopaId)
        ))
        // Jogo único disputado no final de janeiro (ordemGlobal = 1, antes de tudo)
        partidaDao.inserirTodas(listOf(
            PartidaEntity(
                campeonatoId = supercopaId,
                rodada       = 1,
                timeCasaId   = campeonatoBrasileiraoId,
                timeForaId   = campeonatoCopaId,
                fase         = "Final",
                ordemGlobal  = 1
            )
        ))
        return supercopaId
    }

    // ── Verifica se fase atual foi concluída e avança para próxima ──
    // Retorna true se a Copa foi finalizada (Final concluída).
    // Parâmetros opcionais permitem reutilizar a lógica para Copa Argentina.
    suspend fun verificarEAvancarFaseCopa(
        copaId: Int,
        anoAtual: Int,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1,
        fases: List<String> = MotorCampeonato.COPA_FASES,
        ordemGlobal: IntArray = MotorCampeonato.COPA_ORDEM_GLOBAL,
        divisaoHallDaFama: Int = 5,
        premioCampeao: Long = 30_000_000_00L,
        premioVice: Long = 15_000_000_00L
    ): Boolean {
        val campEntity = campeonatoDao.buscarPorId(copaId) ?: return false
        if (campEntity.encerrado) return false
        val todasPartidas = partidaDao.buscarTodasPorCampeonato(copaId)
        if (todasPartidas.isEmpty()) return false

        for (faseAtual in fases) {
            val partidasFase = todasPartidas.filter { it.fase == faseAtual }
            if (partidasFase.isEmpty()) continue

            // Fase em andamento? Se houver alguma não jogada, ainda não acabou
            if (!partidasFase.all { it.jogada }) return false

            // Fase completa → checar se próxima já foi gerada
            val proximaFase = MotorCampeonato.proximaFaseCopa(faseAtual, fases)
            if (proximaFase != null && todasPartidas.any { it.fase == proximaFase }) {
                continue  // próxima fase já existe
            }

            // Determina vencedores desta fase (agrupa por confrontoId)
            val vencedores = mutableListOf<Int>()
            val ties = partidasFase.groupBy { it.confrontoId }
            for ((_, jogos) in ties) {
                val ida   = jogos.minByOrNull { it.rodada } ?: continue
                val volta = jogos.maxByOrNull { it.rodada } ?: continue
                val vencedorDireto = MotorCampeonato.determinarVencedorTie(
                    timeCasaIdaId = ida.timeCasaId,
                    timeForaIdaId = ida.timeForaId,
                    golsCasaIda   = ida.golsCasa   ?: 0,
                    golsForaIda   = ida.golsFora   ?: 0,
                    golsCasaVolta = volta.golsCasa ?: 0,
                    golsForaVolta = volta.golsFora ?: 0
                )
                if (vencedorDireto != null) {
                    vencedores.add(vencedorDireto)
                } else {
                    // Agregado empatado: verificar se pênaltis já foram disputados
                    val pCasa = volta.penaltisCasa
                    val pFora = volta.penaltisForaId
                    if (pCasa != null && pFora != null) {
                        // Resultado de pênaltis já persitido (pelo jogador ou pelo IA)
                        val winner = if (pCasa > pFora) volta.timeCasaId else volta.timeForaId
                        vencedores.add(winner)
                    } else {
                        // IA vs IA: simular pênaltis automaticamente e persistir
                        vencedores.add(simularEPersistirPenaltisIA(ida, volta))
                    }
                }
            }

            if (proximaFase == null) {
                // Final concluída → registrar Hall da Fama e encerrar Copa
                val campeaoId = vencedores.firstOrNull() ?: return true
                val ties2 = partidasFase.groupBy { it.confrontoId }
                val finalTie = ties2.values.firstOrNull() ?: return true
                val idaFinal = finalTie.minByOrNull { it.rodada } ?: return true
                val viceId = if (campeaoId == idaFinal.timeCasaId) idaFinal.timeForaId else idaFinal.timeCasaId

                val campeao    = timeRepository.buscarEntityPorId(campeaoId)
                val vice       = timeRepository.buscarEntityPorId(viceId)
                val copaEnt    = campeonatoDao.buscarPorId(copaId)
                val artilheiro = partidaDao.buscarArtilheiroTop1(copaId)
                val assistente = partidaDao.buscarAssisteTop1(copaId)
                val nomePadrao = if (divisaoHallDaFama == 9) "Copa Argentina $anoAtual" else "Copa do Brasil $anoAtual"

                hallDaFamaDao.inserir(
                    HallDaFamaEntity(
                        ano                 = anoAtual,
                        nomeCampeonato      = copaEnt?.nome ?: nomePadrao,
                        campeaoTimeId       = campeaoId,
                        campeaoNome         = campeao?.nome ?: "",
                        campeaoEscudo       = campeao?.escudoRes ?: "",
                        viceTimeId          = viceId,
                        viceNome            = vice?.nome ?: "",
                        viceEscudo          = vice?.escudoRes ?: "",
                        artilheiroId        = artilheiro?.jogadorId ?: -1,
                        artilheiroNome      = artilheiro?.nomeJogador ?: "",
                        artilheiroNomeAbrev = artilheiro?.nomeAbrev ?: "",
                        artilheiroGols      = artilheiro?.total ?: 0,
                        artilheiroNomeTime  = artilheiro?.nomeTime ?: "",
                        artilheiroEscudo    = artilheiro?.escudoRes ?: "",
                        assistenteId        = assistente?.jogadorId ?: -1,
                        assistenteNome      = assistente?.nomeJogador ?: "",
                        assistenteNomeAbrev = assistente?.nomeAbrev ?: "",
                        assistenciasTotais  = assistente?.total ?: 0,
                        assistenteNomeTime  = assistente?.nomeTime ?: "",
                        assistenteEscudo    = assistente?.escudoRes ?: "",
                        divisao             = divisaoHallDaFama
                    )
                )
                // Incrementa copasVencidas do campeão e aplica bônus de pontos
                val rankCampeaoBase = rankingGeralDao.buscarPorTime(campeaoId)
                    ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                        timeId = campeaoId, nomeTime = campeao?.nome ?: "",
                        escudoRes = campeao?.escudoRes ?: "", divisaoAtual = campeao?.divisao ?: 1
                    )
                rankingGeralDao.inserirOuAtualizar(
                    rankCampeaoBase.copy(
                        copasVencidas    = rankCampeaoBase.copasVencidas + 1,
                        pontosAcumulados = rankCampeaoBase.pontosAcumulados + 30L
                    )
                )
                // Bônus de pontos para o vice
                val rankViceBase = rankingGeralDao.buscarPorTime(viceId)
                    ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                        timeId = viceId, nomeTime = vice?.nome ?: "",
                        escudoRes = vice?.escudoRes ?: "", divisaoAtual = vice?.divisao ?: 1
                    )
                rankingGeralDao.inserirOuAtualizar(
                    rankViceBase.copy(pontosAcumulados = rankViceBase.pontosAcumulados + 15L)
                )
                campeonatoDao.encerrar(copaId)
                // Premiação: campeão e vice se for o time do jogador
                val nomePremio = copaEnt?.nome ?: nomePadrao
                val premios = listOf(campeaoId to (premioCampeao to "Campeão"), viceId to (premioVice to "Vice-campeão"))
                for ((timePremiado, premioInfo) in premios) {
                    val (valorPremio, posicao) = premioInfo
                    if (timeJogadorId > 0 && timePremiado == timeJogadorId) {
                        timeRepository.creditarSaldo(timeJogadorId, valorPremio)
                        financaDao.inserir(
                            br.com.managerfoot.data.database.entities.FinancaEntity(
                                timeId            = timeJogadorId,
                                temporadaId       = temporadaId,
                                mes               = 13,
                                receitaPremiacoes = valorPremio,
                                descricaoPremio   = "$nomePremio — $posicao",
                                saldoFinal        = valorPremio
                            )
                        )
                    }
                }
                // +4% de reputação para o campeão
                val campeaoCopa = timeRepository.buscarPorId(campeaoId)
                if (campeaoCopa != null) {
                    val bonus = (campeaoCopa.reputacao * 0.04f).coerceAtLeast(0.1f)
                    timeRepository.atualizarReputacao(campeaoId, campeaoCopa.reputacao + bonus)
                }
                return true
            }

            // Gera partidas da próxima fase
            val faseIndex   = fases.indexOf(proximaFase)
            val rodadaIda   = MotorCampeonato.rodadaIdaDeFase(faseIndex)
            val maxConfId   = todasPartidas.mapNotNull { it.confrontoId }.maxOrNull() ?: 0
            val novosPares  = MotorCampeonato.sortearPares(vencedores)
            val novasPartidas = MotorCampeonato.gerarFaseIdaVolta(
                campeonatoId       = copaId,
                pares              = novosPares,
                fase               = proximaFase,
                rodadaIda          = rodadaIda,
                confrontoIdInicio  = maxConfId + 1,
                ordemGlobalIda     = ordemGlobal[faseIndex * 2],
                ordemGlobalVolta   = ordemGlobal[faseIndex * 2 + 1]
            )
            partidaDao.inserirTodas(novasPartidas)
            return false
        }
        return false
    }

    // ── Continua a Copa quando o jogador já foi eliminado ────────────
    // Simula a fase Copa pendente (ida + volta) e avança para a próxima.
    // Chamado após cada partida da liga para manter o chaveamento atualizado.
    // Não faz nada se: Copa encerrada, Copa inexistente, jogador ainda ativo na Copa.
    suspend fun simularProximaFaseCopaSeJogadorEliminado(
        copaId: Int,
        timeJogadorId: Int,
        anoAtual: Int
    ) {
        if (copaId <= 0) return
        if (campeonatoDao.buscarPorId(copaId)?.encerrado == true) return

        val todasPartidas = partidaDao.buscarTodasPorCampeonato(copaId)

        // Jogador ainda tem partidas pendentes na Copa? Não interferir.
        if (todasPartidas.any { !it.jogada && (it.timeCasaId == timeJogadorId || it.timeForaId == timeJogadorId) }) return

        val pendentes = todasPartidas.filter { !it.jogada }
        if (pendentes.isEmpty()) {
            // Todas as partidas da fase atual já foram jogadas — avança a fase ou finaliza.
            verificarEAvancarFaseCopa(copaId, anoAtual)
            return
        }

        // Simula todas as rodadas da fase pendente (geralmente ida e volta de uma mesma fase).
        pendentes.map { it.rodada }.toSortedSet().forEach { rodada ->
            simularRodada(copaId, rodada)
        }
        // Gera a próxima fase ou registra campeão/vice se for a Final.
        verificarEAvancarFaseCopa(copaId, anoAtual)
    }

    // ── Verifica e encerra a Supercopa Rei após o jogo único ser jogado ──
    // Registra o campeão no Hall da Fama, aplica premiação e encerra o campeonato.
    // Retorna true se a Supercopa foi encerrada nesta chamada.
    suspend fun verificarEEncerrarSupercopa(
        supercopaId: Int,
        anoAtual: Int,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1
    ): Boolean {
        if (supercopaId <= 0) return false
        val supercopa = campeonatoDao.buscarPorId(supercopaId) ?: return false
        if (supercopa.encerrado) return false

        val partidas = partidaDao.buscarTodasPorCampeonato(supercopaId)
        if (partidas.isEmpty() || !partidas.all { it.jogada }) return false

        val jogo = partidas.first()
        val gCasa = jogo.golsCasa ?: return false
        val gFora = jogo.golsFora ?: return false

        val campeaoId = when {
            gCasa > gFora -> jogo.timeCasaId
            gFora > gCasa -> jogo.timeForaId
            else -> {
                // Pênaltis: verifica se já foram persistidos
                val pCasa = jogo.penaltisCasa
                val pFora = jogo.penaltisForaId
                if (pCasa != null && pFora != null) {
                    if (pCasa > pFora) jogo.timeCasaId else jogo.timeForaId
                } else {
                    // Simula pênaltis IA vs IA
                    simularEPersistirPenaltisIA(jogo, jogo)
                }
            }
        }
        val viceId = if (campeaoId == jogo.timeCasaId) jogo.timeForaId else jogo.timeCasaId

        val campeao  = timeRepository.buscarEntityPorId(campeaoId)
        val vice     = timeRepository.buscarEntityPorId(viceId)
        val artilheiro = partidaDao.buscarArtilheiroTop1(supercopaId)
        val assistente = partidaDao.buscarAssisteTop1(supercopaId)

        // Registra no Hall da Fama (divisao = 6 → Supercopa)
        hallDaFamaDao.inserir(
            HallDaFamaEntity(
                ano                  = anoAtual,
                nomeCampeonato       = supercopa.nome,
                campeaoTimeId        = campeaoId,
                campeaoNome          = campeao?.nome ?: "",
                campeaoEscudo        = campeao?.escudoRes ?: "",
                viceTimeId           = viceId,
                viceNome             = vice?.nome ?: "",
                viceEscudo           = vice?.escudoRes ?: "",
                artilheiroId         = artilheiro?.jogadorId ?: -1,
                artilheiroNome       = artilheiro?.nomeJogador ?: "",
                artilheiroNomeAbrev  = artilheiro?.nomeAbrev ?: "",
                artilheiroGols       = artilheiro?.total ?: 0,
                artilheiroNomeTime   = artilheiro?.nomeTime ?: "",
                artilheiroEscudo     = artilheiro?.escudoRes ?: "",
                assistenteId         = assistente?.jogadorId ?: -1,
                assistenteNome       = assistente?.nomeJogador ?: "",
                assistenteNomeAbrev  = assistente?.nomeAbrev ?: "",
                assistenciasTotais   = assistente?.total ?: 0,
                assistenteNomeTime   = assistente?.nomeTime ?: "",
                assistenteEscudo     = assistente?.escudoRes ?: "",
                divisao              = 6   // 6 = Supercopa Rei
            )
        )

        // Premiação: R$ 10M campeão, R$ 5M vice
        val premios = listOf(campeaoId to (10_000_000_00L to "Campeão"), viceId to (5_000_000_00L to "Vice-campeão"))
        for ((timePremiado, premioInfo) in premios) {
            val (valorPremio, posicaoSuper) = premioInfo
            if (timeJogadorId > 0 && timePremiado == timeJogadorId) {
                timeRepository.creditarSaldo(timeJogadorId, valorPremio)
                financaDao.inserir(
                    br.com.managerfoot.data.database.entities.FinancaEntity(
                        timeId            = timeJogadorId,
                        temporadaId       = temporadaId,
                        mes               = 1,
                        receitaPremiacoes = valorPremio,
                        descricaoPremio   = "Supercopa Rei — $posicaoSuper",
                        saldoFinal        = valorPremio
                    )
                )
            }
        }

        // Bônus de reputação +3% para o campeão
        val campeaoTime = timeRepository.buscarPorId(campeaoId)
        if (campeaoTime != null) {
            val bonus = (campeaoTime.reputacao * 0.03f).coerceAtLeast(0.1f)
            timeRepository.atualizarReputacao(campeaoId, campeaoTime.reputacao + bonus)
        }

        // Atualiza ranking geral: +1 copa e +25 pontos para o campeão, +10 para o vice
        val rankingCampeaoBase = rankingGeralDao.buscarPorTime(campeaoId)
            ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                timeId = campeaoId, nomeTime = campeao?.nome ?: "",
                escudoRes = campeao?.escudoRes ?: "", divisaoAtual = campeao?.divisao ?: 1
            )
        rankingGeralDao.inserirOuAtualizar(
            rankingCampeaoBase.copy(
                nomeTime         = campeao?.nome ?: rankingCampeaoBase.nomeTime,
                escudoRes        = campeao?.escudoRes ?: rankingCampeaoBase.escudoRes,
                copasVencidas    = rankingCampeaoBase.copasVencidas + 1,
                pontosAcumulados = rankingCampeaoBase.pontosAcumulados + 25L
            )
        )
        val rankingViceBase = rankingGeralDao.buscarPorTime(viceId)
            ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                timeId = viceId, nomeTime = vice?.nome ?: "",
                escudoRes = vice?.escudoRes ?: "", divisaoAtual = vice?.divisao ?: 1
            )
        rankingGeralDao.inserirOuAtualizar(
            rankingViceBase.copy(pontosAcumulados = rankingViceBase.pontosAcumulados + 10L)
        )

        campeonatoDao.encerrar(supercopaId)
        return true
    }

    // ── Simula a Supercopa quando o jogador não é participante ───────
    // Chamado passivamente após cada partida da liga/copa, para manter o resultado atualizado.
    suspend fun simularSupercopaSeJogadorNaoParticipa(
        supercopaId: Int,
        timeJogadorId: Int,
        anoAtual: Int,
        temporadaId: Int
    ) {
        if (supercopaId <= 0) return
        if (campeonatoDao.buscarPorId(supercopaId)?.encerrado == true) return

        val partidas = partidaDao.buscarTodasPorCampeonato(supercopaId)
        val jogadorParticipa = partidas.any {
            it.timeCasaId == timeJogadorId || it.timeForaId == timeJogadorId
        }
        if (jogadorParticipa) return  // Jogador participa — não interferir

        val pendentes = partidas.filter { !it.jogada }
        if (pendentes.isEmpty()) {
            verificarEEncerrarSupercopa(supercopaId, anoAtual, timeJogadorId, temporadaId)
            return
        }
        pendentes.forEach { simularRodada(supercopaId, it.rodada) }
        verificarEEncerrarSupercopa(supercopaId, anoAtual, timeJogadorId, temporadaId)
    }

    // ── Atualiza ranking geral após término de uma competição ────────
    // Chamado ao final de cada temporada: registra +1 temporada jogada, atualiza divisão
    // e contabiliza +1 título nacional para o campeão (1º colocado).
    // Pontos/V/E/D já foram acumulados em tempo real via atualizarRankingAposPartida.
    suspend fun atualizarRankingGeral(campeonatoId: Int) {
        if (campeonatoId <= 0) return
        val classificacoes = classificacaoDao.buscarTabelaOrdenada(campeonatoId)
        val campeonato     = campeonatoDao.buscarPorId(campeonatoId)
        val campeaoTimeId  = classificacoes.firstOrNull()?.timeId
        val viceTimeId     = classificacoes.getOrNull(1)?.timeId
        val (bonusCampeao, bonusVice) = when (campeonato?.tipo) {
            TipoCampeonato.NACIONAL_DIVISAO1        -> 35L to 15L
            TipoCampeonato.NACIONAL_DIVISAO2        -> 20L to 12L
            TipoCampeonato.NACIONAL_DIVISAO3        -> 15L to  9L
            TipoCampeonato.NACIONAL_DIVISAO4        -> 10L to  6L
            TipoCampeonato.EXTRANGEIRO_DIVISAO1     -> 20L to 10L
            else                                    ->  0L to  0L
        }
        for (cls in classificacoes) {
            val time      = timeRepository.buscarEntityPorId(cls.timeId) ?: continue
            val existing  = rankingGeralDao.buscarPorTime(cls.timeId)
                ?: RankingGeralEntity(
                    timeId = cls.timeId, nomeTime = time.nome,
                    escudoRes = time.escudoRes, divisaoAtual = time.divisao
                )
            val ehCampeao = cls.timeId == campeaoTimeId
            val ehVice    = cls.timeId == viceTimeId
            val pontosExtras = when {
                ehCampeao -> bonusCampeao
                ehVice    -> bonusVice
                else      -> 0L
            }
            rankingGeralDao.inserirOuAtualizar(
                existing.copy(
                    nomeTime          = time.nome,
                    escudoRes         = time.escudoRes,
                    divisaoAtual      = time.divisao,
                    temporadasJogadas = existing.temporadasJogadas + 1,
                    titulosNacionais  = existing.titulosNacionais + if (ehCampeao) 1 else 0,
                    pontosAcumulados  = existing.pontosAcumulados + pontosExtras
                )
            )
        }
    }

    // ── Determina participantes da Copa ─────────────────────────────
    suspend fun determinarParticipantesCopa(
        temporadaId: Int,
        participantesSerieA: List<Int>
    ): List<Int> {
        return if (temporadaId <= 1) {
            // Primeira temporada: top 64 por reputação (somente times brasileiros)
            timeRepository.buscarTodosOrdenadosPorReputacao()
                .filter { it.pais == "Brasil" }.take(64).map { it.id }
        } else {
            // Série A (automáticos) + top 44 do ranking geral não presentes na Série A
            val serieASet = participantesSerieA.toSet()
            val top44 = rankingGeralDao.buscarTopN(200)
                .filter { it.timeId !in serieASet && it.divisaoAtual != 5 } // exclui times argentinos
                .take(64 - participantesSerieA.size)
                .map { it.timeId }
            (participantesSerieA + top44).take(64)
        }
    }

    suspend fun fecharMes(timeId: Int, temporadaId: Int, mes: Int, patrocinioMensal: Long = 0L, patrocinioJaCreditado: Long = 0L) {
        val time = timeRepository.buscarPorId(timeId) ?: return
        // Usa todos os seniores (incluindo lesionados/suspensos) para calcular a folha salarial real
        val elenco = jogadorRepository.buscarSeniores(timeId)

        val fechamento = MotorFinanceiro.processarFechamentoMensal(
            time = time,
            elenco = elenco,
            partidasEmCasa = 0  // bilheteria real já creditada por partida individual
        )

        // Usa o valor de patrocínio escolhido pelo usuário (mensal = anual / 12)
        // Se zero, cai no valor automático calculado pelo motor (para times IA)
        val receitaPatrocinioFinal = if (patrocinioMensal > 0L) patrocinioMensal else fechamento.receitaPatrocinio

        // Recalcula o lucro/prejuízo do mês com o patrocínio correto
        val lucroReal = fechamento.receitaBilheteria + receitaPatrocinioFinal -
            fechamento.despesaSalarios - fechamento.despesaInfraestrutura

        // Tenta inserir o registro financeiro
        // Se já existir um registro para este mês (ex: patrocínio adiantado), reutiliza o id
        // para que o REPLACE atualize a linha existente em vez de criar uma nova
        val existente = try { financaDao.buscarPorMes(timeId, temporadaId, mes) } catch (_: Exception) { null }
        try {
            financaDao.inserir(
                FinancaEntity(
                    id                = existente?.id ?: 0,
                    timeId            = timeId,
                    temporadaId       = temporadaId,
                    mes               = mes,
                    receitaBilheteria = fechamento.receitaBilheteria,
                    receitaPatrocinio = receitaPatrocinioFinal,
                    despesaSalarios   = fechamento.despesaSalarios,
                    despesaInfraestrutura = fechamento.despesaInfraestrutura,
                    saldoFinal        = lucroReal
                )
            )
        } catch (e: Exception) {
            // FK da temporada ainda não persistida — ignora o registro
            // mas continua atualizando o saldo do clube normalmente
        }

        // Desconta o valor já adiantado ao saldo quando o patrocínio foi escolhido
        // evitando dupla contagem: o registro exibe o valor cheio, o ajuste só cobre o delta
        val saldoAjuste = lucroReal - patrocinioJaCreditado
        if (saldoAjuste >= 0) {
            timeRepository.creditarSaldo(timeId, saldoAjuste)
        } else {
            timeRepository.debitarSaldo(timeId, -saldoAjuste)
        }

        // IA contrata jogadores do mercado livre (cada time IA verifica necessidades)
        executarContratacoesLivresIA(timeId, temporadaId, mes)
        // IA avalia reforço mensal: tenta melhorar a posição mais fraca do time titular
        executarReforcoMensalIA(timeId, temporadaId, mes)
        // IA faz reforços emergenciais quando o time está com desempenho ruim nos campeonatos
        executarReforcoDesempenhoIA(timeId, temporadaId, mes)
        // Nos meses de início (1-2) e final (10-12) da temporada, gera propostas de
        // times da IA para jogadores do time do usuário
        if (mes in setOf(1, 2, 10, 11, 12)) {
            gerarPropostasIAParaTimeJogador(timeId, temporadaId, mes)
            executarTransferenciasIAParaIA(timeId, temporadaId, mes)
        }
    }

    /** Credita imediatamente o patrocínio mensal ao saldo do clube quando o contrato é fechado. */
    suspend fun creditarPatrocinioImediato(timeId: Int, valorMensal: Long) {
        if (valorMensal > 0L) timeRepository.creditarSaldo(timeId, valorMensal)
    }

    /**
     * Insere (ou atualiza) o lançamento de patrocínio do mês corrente na tabela financas,
     * garantindo que a aba Receitas mostre o valor imediatamente após o contrato ser fechado.
     */
    suspend fun registrarPatrocinioMes(timeId: Int, temporadaId: Int, mes: Int, valorMensal: Long) {
        if (valorMensal <= 0L) return
        val existente = try { financaDao.buscarPorMes(timeId, temporadaId, mes) } catch (_: Exception) { null }
        try {
            financaDao.inserir(
                FinancaEntity(
                    id                = existente?.id ?: 0,
                    timeId            = timeId,
                    temporadaId       = temporadaId,
                    mes               = mes,
                    receitaPatrocinio = valorMensal,
                    // demais campos mantidos ou zerados; fecharMes sobrescreverá depois
                    receitaBilheteria    = existente?.receitaBilheteria    ?: 0L,
                    despesaSalarios      = existente?.despesaSalarios      ?: 0L,
                    despesaInfraestrutura = existente?.despesaInfraestrutura ?: 0L,
                    saldoFinal           = valorMensal
                )
            )
        } catch (_: Exception) { }
    }

    // ═══════════════════════════════════════════════════════════
    //  Propostas de transferência (IA → Time do jogador)
    // ═══════════════════════════════════════════════════════════

    /**
     * Gera propostas de compra de jogadores do time do usuário por times da IA.
     * Chamado no início (meses 1–2) e final (meses 10–12) de temporada via [fecharMes].
     *
     * Cobre dois casos:
     *  1. Jogadores fortes (top-8): IA oferece 90%–120% do valor de mercado.
     *  2. Jogadores listados para venda pelo usuário: IA oferece 85%–100% do valor de mercado.
     *  3. Jogadores listados para empréstimo pelo usuário: IA gera proposta de empréstimo (sem custo de compra).
     */
    suspend fun gerarPropostasIAParaTimeJogador(playerTimeId: Int, temporadaId: Int, mes: Int) {
        val elenco = jogadorRepository.buscarDisponiveis(playerTimeId)
        if (elenco.isEmpty()) return
        val todos = timeRepository.buscarTodosOrdenadosPorReputacao().filter { it.id != playerTimeId }
        if (todos.isEmpty()) return

        // ── Caso 1: proposta espontânea para os melhores jogadores ──────────────
        val candidatos = elenco
            .filter { !it.lesionado && !it.categoriaBase }
            .sortedByDescending { it.forca }
            .take(8)

        val qtdPropostas = (1..3).random()
        val selecionados = candidatos.shuffled().take(qtdPropostas)

        for (jogador in selecionados) {
            val ativas = propostaIADao.buscarAtivasPorJogadorETipo(jogador.id, TipoProposta.VENDA)
            if (ativas.isNotEmpty()) continue
            val comprador = todos
                .filter { it.saldo >= (jogador.valorMercado * 0.80).toLong() }
                .randomOrNull() ?: continue
            val multiplicador = kotlin.random.Random.nextDouble(0.90, 1.20)
            val valorOferta = (jogador.valorMercado * multiplicador).toLong().coerceAtLeast(1L)
            propostaIADao.inserir(
                PropostaIAEntity(
                    jogadorId       = jogador.id,
                    timeCompradorId = comprador.id,
                    valorOfertado   = valorOferta,
                    temporadaId     = temporadaId,
                    mes             = mes,
                    tipoProposta    = TipoProposta.VENDA
                )
            )
        }

        // ── Caso 2 & 3: jogadores listados explicitamente pelo usuário ──────────
        val listados = jogadorRepository.buscarListadosParaTransferencia(playerTimeId)
        for (jogador in listados) {
            if (jogador.disponívelParaVenda) {
                // Apenas uma proposta de venda ativa por jogador
                val ativas = propostaIADao.buscarAtivasPorJogadorETipo(jogador.id, TipoProposta.VENDA)
                if (ativas.isEmpty()) {
                    val comprador = todos
                        .filter { it.saldo >= (jogador.valorMercado * 0.80).toLong() }
                        .randomOrNull() ?: continue
                    // Oferta de 85% a 100% do valor de mercado (usuário listou para vender)
                    val multiplicador = kotlin.random.Random.nextDouble(0.85, 1.00)
                    val valorOferta = (jogador.valorMercado * multiplicador).toLong().coerceAtLeast(1L)
                    propostaIADao.inserir(
                        PropostaIAEntity(
                            jogadorId       = jogador.id,
                            timeCompradorId = comprador.id,
                            valorOfertado   = valorOferta,
                            temporadaId     = temporadaId,
                            mes             = mes,
                            tipoProposta    = TipoProposta.VENDA
                        )
                    )
                }
            }
            if (jogador.disponívelParaEmprestimo) {
                // Apenas uma proposta de empréstimo ativa por jogador
                val ativasEmp = propostaIADao.buscarAtivasPorJogadorETipo(jogador.id, TipoProposta.EMPRESTIMO)
                if (ativasEmp.isEmpty()) {
                    val interessado = todos.randomOrNull() ?: continue
                    // Empréstimo: valor simbólico = 10%–20% do valor de mercado (taxa de empréstimo)
                    val taxaEmprestimo = (jogador.valorMercado * kotlin.random.Random.nextDouble(0.10, 0.20)).toLong().coerceAtLeast(1L)
                    propostaIADao.inserir(
                        PropostaIAEntity(
                            jogadorId       = jogador.id,
                            timeCompradorId = interessado.id,
                            valorOfertado   = taxaEmprestimo,
                            temporadaId     = temporadaId,
                            mes             = mes,
                            tipoProposta    = TipoProposta.EMPRESTIMO
                        )
                    )
                }
            }
        }
    }

    /** Fluxo reativo de propostas ativas para exibição na aba Propostas do Mercado. */
    fun observarPropostasAtivas(): Flow<List<PropostaIAEntity>> =
        propostaIADao.observeAtivas()

    /** Fluxo reativo de notificações (pendentes + aceites/recusas não lidas). */
    fun observarNotificacoes(): Flow<List<PropostaIAEntity>> =
        propostaIADao.observeNotificacoes()

    /** Fluxo reativo do contador de notificações não lidas (para badge). */
    fun observarContadorNotificacoes(): Flow<Int> =
        propostaIADao.observeContadorNaoLidas()

    /** Marca uma proposta como lida. */
    suspend fun marcarNotificacaoLida(id: Int) =
        propostaIADao.marcarLida(id)

    /** Marca todas as encerradas (aceitas/recusadas) como lidas de uma vez. */
    suspend fun marcarTodasNotificacoesLidas() =
        propostaIADao.marcarTodasEncerradasLidas()

    /**
     * Aceita uma proposta: realiza a transferência imediatamente, credita o saldo ao
     * time do jogador e debita do time comprador.
     */
    suspend fun aceitarProposta(
        proposta: PropostaIAEntity,
        playerTimeId: Int,
        temporadaId: Int,
        mes: Int
    ) {
        val jogador = jogadorRepository.buscarPorId(proposta.jogadorId) ?: return
        timeRepository.creditarSaldo(playerTimeId, proposta.valorOfertado)
        timeRepository.debitarSaldo(proposta.timeCompradorId, proposta.valorOfertado)
        jogadorRepository.realizarVenda(
            oferta = OfertaTransferencia(
                jogadorId       = jogador.id,
                timeCompradorId = proposta.timeCompradorId,
                timeVendedorId  = playerTimeId,
                valor           = proposta.valorOfertado,
                salarioProposto = jogador.salario,
                contratoAnos    = 3
            ),
            temporadaId = temporadaId,
            mes         = mes
        )
        propostaIADao.atualizarStatus(proposta.id, StatusProposta.ACEITA)
    }

    /** Recusa a proposta: o jogador permanece no elenco. */
    suspend fun recusarProposta(propostaId: Int) {
        propostaIADao.atualizarStatus(propostaId, StatusProposta.RECUSADA)
    }

    /**
     * Contra-oferta do usuário: registra o valor solicitado e muda o status para
     * AGUARDANDO_RESPOSTA_IA. A IA responderá após a próxima rodada simulada.
     */
    suspend fun negociarProposta(propostaId: Int, valorSolicitado: Long) {
        val proposta = propostaIADao.buscarPorId(propostaId) ?: return
        propostaIADao.atualizarNegociacao(
            id              = propostaId,
            status          = StatusProposta.AGUARDANDO_RESPOSTA_IA,
            tentativas      = proposta.tentativasNegociacao + 1,
            valorSolicitado = valorSolicitado
        )
    }

    /**
     * Processado após o usuário simular uma rodada: a IA avalia e responde a todas as
     * contra-ofertas em estado AGUARDANDO_RESPOSTA_IA.
     *
     * Regras de aceite:
     *  - 3ª tentativa atingida → rejeita automaticamente
     *  - sobrevalorização ≤  5% → aceite garantido
     *  - sobrevalorização ≤ 15% → 70% de probabilidade de aceite
     *  - sobrevalorização ≤ 30% → 40% de probabilidade de aceite
     *  - sobrevalorização > 30% → rejeita automaticamente
     *
     * Se aceito, a transferência é concluída imediatamente e a proposta marcada ACEITA.
     */
    suspend fun processarRespostasIANegociacao(playerTimeId: Int, temporadaId: Int, mes: Int) {
        val aguardando = propostaIADao.buscarAguardandoRespostaIA()
        for (proposta in aguardando) {
            if (proposta.tentativasNegociacao >= 3) {
                propostaIADao.atualizarStatus(proposta.id, StatusProposta.RECUSADA)
                continue
            }
            val jogador   = jogadorRepository.buscarPorId(proposta.jogadorId) ?: continue
            val comprador = timeRepository.buscarPorId(proposta.timeCompradorId) ?: continue
            val valorPed  = proposta.valorSolicitadoJogador
            val valorOrig = proposta.valorOfertado

            if (comprador.saldo < valorPed) {
                propostaIADao.atualizarStatus(proposta.id, StatusProposta.RECUSADA)
                continue
            }

            val sobretaxa = if (valorOrig > 0L) ((valorPed - valorOrig).toDouble() / valorOrig) else 1.0
            val aceitou = when {
                sobretaxa <= 0.05 -> true
                sobretaxa <= 0.15 -> kotlin.random.Random.nextDouble() < 0.70
                sobretaxa <= 0.30 -> kotlin.random.Random.nextDouble() < 0.40
                else              -> false
            }

            if (aceitou) {
                timeRepository.creditarSaldo(playerTimeId, valorPed)
                timeRepository.debitarSaldo(proposta.timeCompradorId, valorPed)
                jogadorRepository.realizarVenda(
                    oferta = OfertaTransferencia(
                        jogadorId       = jogador.id,
                        timeCompradorId = proposta.timeCompradorId,
                        timeVendedorId  = playerTimeId,
                        valor           = valorPed,
                        salarioProposto = jogador.salario,
                        contratoAnos    = 3
                    ),
                    temporadaId = temporadaId,
                    mes         = mes
                )
                propostaIADao.atualizarStatus(proposta.id, StatusProposta.ACEITA)
            } else {
                propostaIADao.atualizarStatus(proposta.id, StatusProposta.RECUSADA)
            }
        }
    }

    /**
     * Chamado imediatamente após o jogador contratar um atleta de um time da IA.
     * usando jogadores livres, priorizando as carências posicionais do esquema tático.
     */
    suspend fun recompletarElencoEmergenciaIA(timeVendedorId: Int?, temporadaId: Int, mes: Int) {
        val vendedorId = timeVendedorId ?: return
        val time = timeRepository.buscarPorId(vendedorId) ?: return
        val totalElenco = jogadorRepository.contarJogadores(vendedorId)
        if (totalElenco >= 18) return
        val faltam = (22 - totalElenco).coerceAtLeast(0)
        if (faltam <= 0) return

        val elencoAtual = jogadorRepository.buscarDisponiveis(vendedorId)
        val livres = jogadorRepository.buscarMercado(limite = 60).filter { it.timeId == null }
        if (livres.isEmpty()) return

        val carencias = IATimeRival.detectarCarencias(elencoAtual, time.taticaFormacao)
        val usados = mutableSetOf<Int>()
        var contratados = 0

        // Primeira passagem: preenche as carências posicionais da formação
        for (posicao in carencias) {
            if (contratados >= faltam) break
            val alvo = livres
                .filter { it.id !in usados }
                .filter { it.posicao == posicao || it.posicaoSecundaria == posicao }
                .maxByOrNull { it.forca } ?: continue
            jogadorRepository.realizarTransferencia(
                OfertaTransferencia(
                    jogadorId       = alvo.id,
                    timeCompradorId = vendedorId,
                    timeVendedorId  = null,
                    valor           = 0L,
                    salarioProposto = alvo.salario,
                    contratoAnos    = 2
                ), temporadaId, mes
            )
            usados.add(alvo.id)
            contratados++
        }

        // Segunda passagem: completa com os melhores disponíveis restantes
        for (alvo in livres.filter { it.id !in usados }.sortedByDescending { it.forca }) {
            if (contratados >= faltam) break
            jogadorRepository.realizarTransferencia(
                OfertaTransferencia(
                    jogadorId       = alvo.id,
                    timeCompradorId = vendedorId,
                    timeVendedorId  = null,
                    valor           = 0L,
                    salarioProposto = alvo.salario,
                    contratoAnos    = 2
                ), temporadaId, mes
            )
            usados.add(alvo.id)
            contratados++
        }
    }

    /**
     * Para cada time da IA (exceto o do jogador), verifica se há jogadores livres
     * disponíveis para contratar e executa até 2 contratações por mês.
     */
    private suspend fun executarContratacoesLivresIA(playerTimeId: Int, temporadaId: Int, mes: Int) {
        val todos = timeRepository.buscarTodosOrdenadosPorReputacao()
        val mercadoLivre = jogadorRepository.buscarMercado(limite = 40)
        if (mercadoLivre.isEmpty()) return

        // Rastreia jogadores já movimentados nesta execução para não negociar o mesmo
        // jogador duas vezes dentro do mesmo fechamento mensal.
        val jogadoresMovidos = mutableSetOf<Int>()

        for (time in todos) {
            if (time.id == playerTimeId) continue
            val elenco = jogadorRepository.buscarDisponiveis(time.id)
            val precisaUrgente = elenco.size < 18
            val podeReforcar  = elenco.size < 20 && kotlin.random.Random.nextFloat() < 0.30f
            if (!precisaUrgente && !podeReforcar) continue

            val ofertas = IATimeRival.decidirContratacoes(
                time         = time,
                elencoAtual  = elenco,
                mercado      = mercadoLivre.filter { it.timeId == null && it.id !in jogadoresMovidos },
                orcamento    = time.saldo
            )

            val oferta = ofertas.firstOrNull() ?: continue
            val ofertaLivre = oferta.copy(valor = 0L, timeVendedorId = null)
            jogadorRepository.realizarTransferencia(ofertaLivre, temporadaId, mes)
            jogadoresMovidos.add(oferta.jogadorId)
        }
    }

    /**
     * Avaliação mensal de reforço da IA: cada time identifica a posição mais fraca
     * no titular, busca um jogador livre melhor naquela posição e, se o valor de
     * mercado caber em até 30% do saldo, realiza a contratação pagando pelo passe.
     */
    private suspend fun executarReforcoMensalIA(playerTimeId: Int, temporadaId: Int, mes: Int) {
        val todos  = timeRepository.buscarTodosOrdenadosPorReputacao()
        val livres = jogadorRepository.buscarMercado(limite = 60).filter { it.timeId == null }
        if (livres.isEmpty()) return

        val jogadoresMovidos = mutableSetOf<Int>()

        for (time in todos) {
            if (time.id == playerTimeId) continue
            val elencoAtual = jogadorRepository.buscarDisponiveis(time.id)
            if (elencoAtual.isEmpty()) continue
            if (elencoAtual.size >= 26) continue
            if (kotlin.random.Random.nextFloat() >= 0.25f) continue

            val escalacao = IATimeRival.gerarEscalacao(time, elencoAtual)
            val maisFrago = escalacao.titulares.minByOrNull { it.jogador.forca } ?: continue
            val posNecessaria = maisFrago.posicaoUsada

            val orcamentoReforco = (time.saldo * 0.25).toLong()
            if (orcamentoReforco <= 0L) continue

            val candidato = livres
                .filter { it.id !in jogadoresMovidos }
                .filter { it.posicao == posNecessaria || it.posicaoSecundaria == posNecessaria }
                .filter { it.forca > maisFrago.jogador.forca }
                .filter { it.valorMercado <= orcamentoReforco }
                .maxByOrNull { it.forca } ?: continue

            timeRepository.debitarSaldo(time.id, candidato.valorMercado)
            jogadorRepository.realizarTransferencia(
                OfertaTransferencia(
                    jogadorId       = candidato.id,
                    timeCompradorId = time.id,
                    timeVendedorId  = null,
                    valor           = candidato.valorMercado,
                    salarioProposto = candidato.salario,
                    contratoAnos    = 2
                ), temporadaId, mes
            )
            jogadoresMovidos.add(candidato.id)
        }
    }

    /**
     * Reforço emergencial de desempenho: para cada liga ativa (não Copa), identifica os
     * times da IA com aproveitamento ruim (< 40% dos pontos disputados, mínimo 5 jogos)
     * e assina 1–3 jogadores livres do mercado visando as posições mais carentes.
     *
     * Escala de contratações:
     *   aproveitamento < 0.20 → 3 jogadores
     *   aproveitamento < 0.30 → 2 jogadores
     *   aproveitamento < 0.40 → 1 jogador
     *
     * As contratações são registradas via [realizarTransferencia] e aparecem
     * automaticamente na aba "Transferências" do Mercado de Transferências.
     */
    private suspend fun executarReforcoDesempenhoIA(playerTimeId: Int, temporadaId: Int, mes: Int) {
        val tiposCopa = setOf(
            TipoCampeonato.COPA_NACIONAL,
            TipoCampeonato.CONTINENTAL
        )
        val ligas = campeonatoDao.buscarAtivos().filter { it.tipo !in tiposCopa }
        if (ligas.isEmpty()) return

        val livres = jogadorRepository.buscarMercado(limite = 80).filter { it.timeId == null }
        if (livres.isEmpty()) return
        val livresDisponiveis = livres.toMutableList()

        for (liga in ligas) {
            val tabela = classificacaoDao.buscarTabelaOrdenada(liga.id)
            val totalTimes = tabela.size

            tabela.forEachIndexed { idx, classi ->
                if (classi.timeId == playerTimeId) return@forEachIndexed
                if (classi.jogos < 5) return@forEachIndexed          // muito cedo para agir
                if (classi.aproveitamento >= 0.40f) return@forEachIndexed  // desempenho aceitável

                // Emergencial: máximo 1 reforço livre por time por mês,
                // independente de quão ruim esteja o desempenho.
                val qtd = 1

                val elenco = jogadorRepository.buscarDisponiveis(classi.timeId)
                if (elenco.size >= 24) return@forEachIndexed   // elenco suficiente, não precisa

                val time = timeRepository.buscarPorId(classi.timeId) ?: return@forEachIndexed
                val carencias = IATimeRival.detectarCarencias(elenco, time.taticaFormacao)
                val posicoesPrioritarias = if (carencias.isNotEmpty()) carencias else listOf(Posicao.ZAGUEIRO, Posicao.VOLANTE, Posicao.CENTROAVANTE)

                var contratados = 0
                for (posicao in posicoesPrioritarias) {
                    if (contratados >= qtd) break
                    val candidato = livresDisponiveis
                        .filter { it.posicao == posicao || it.posicaoSecundaria == posicao }
                        .maxByOrNull { it.forca } ?: continue

                    livresDisponiveis.remove(candidato)
                    jogadorRepository.realizarTransferencia(
                        OfertaTransferencia(
                            jogadorId       = candidato.id,
                            timeCompradorId = classi.timeId,
                            timeVendedorId  = null,
                            valor           = 0L,
                            salarioProposto = candidato.salario,
                            contratoAnos    = 2
                        ), temporadaId, mes
                    )
                    contratados++
                }

                // Complementa com outros perfis se ainda não atingiu a cota
                if (contratados < qtd) {
                    for (candidato in livresDisponiveis.sortedByDescending { it.forca }) {
                        if (contratados >= qtd) break
                        livresDisponiveis.remove(candidato)
                        jogadorRepository.realizarTransferencia(
                            OfertaTransferencia(
                                jogadorId       = candidato.id,
                                timeCompradorId = classi.timeId,
                                timeVendedorId  = null,
                                valor           = 0L,
                                salarioProposto = candidato.salario,
                                contratoAnos    = 2
                            ), temporadaId, mes
                        )
                        contratados++
                    }
                }
            }
        }
    }

    // ─── Helpers privados ───

    /**
     * Para cada campeonato em [outrasIds] que não seja o [playerCampeonatoId], simula
     * a próxima rodada pendente (rodadaAtual + 1) de forma a manter todos os campeonatos
     * progredindo em sincronismo com o jogo do jogador.
     */
    private suspend fun simularRodadaEmOutrosCampeonatos(
        playerCampeonatoId: Int,
        outrasIds: List<Int>
    ) {
        for (campId in outrasIds) {
            if (campId <= 0 || campId == playerCampeonatoId) continue
            val camp = campeonatoDao.buscarPorId(campId) ?: continue
            if (camp.encerrado) continue

            // Recupera e simula partidas atrasadas de rodadas já encerradas nesta liga
            val atrasadas = partidaDao.buscarTodasPorCampeonato(campId)
                .filter { !it.jogada && it.rodada <= camp.rodadaAtual && it.fase == null }
            for (partida in atrasadas) {
                try { simularPartidaInterna(partida) } catch (_: Exception) {}
            }

            val proximaRodada = camp.rodadaAtual + 1
            if (proximaRodada <= camp.totalRodadas) {
                simularRodada(campId, proximaRodada)
            }
        }
    }

    private suspend fun simularPartidaInterna(partida: PartidaEntity) {
        val escalacaoCasa = gerarEscalacaoIA(partida.timeCasaId)
        val escalacaoFora = gerarEscalacaoIA(partida.timeForaId)
        val campoNeutro = campeonatoDao.buscarPorId(partida.campeonatoId)?.tipo == TipoCampeonato.SUPERCOPA
                       || partida.fase == "Final"

        val resultado = simulador.simular(
            partidaId   = partida.id,
            casa        = escalacaoCasa,
            fora        = escalacaoFora,
            campoNeutro = campoNeutro
        )
        persistirResultado(resultado)
    }

    // ── Pênaltis: IA vs IA ────────────────────────────────────────────
    private suspend fun simularEPersistirPenaltisIA(ida: PartidaEntity, volta: PartidaEntity): Int {
        val escCasa = gerarEscalacaoIA(volta.timeCasaId)
        val escFora = gerarEscalacaoIA(volta.timeForaId)

        fun cobradores(esc: Escalacao): Pair<List<Pair<Int, String>>, List<Int>> {
            val lista = esc.titulares
                .filter { it.posicaoUsada.setor != Setor.GOLEIRO }
                .sortedByDescending { it.jogador.finalizacao }
                .take(5)
            return lista.map { it.jogador.id to it.jogador.nomeAbreviado } to
                    lista.map { it.jogador.finalizacao }
        }

        val (cobraCasa, finCasa) = cobradores(escCasa)
        val (cobraFora, finFora) = cobradores(escFora)
        val gkCasa = escCasa.titulares.firstOrNull { it.posicaoUsada.setor == Setor.GOLEIRO }?.jogador?.defesa ?: 70
        val gkFora = escFora.titulares.firstOrNull { it.posicaoUsada.setor == Setor.GOLEIRO }?.jogador?.defesa ?: 70

        val resultado = simulador.simularDisputaPenaltis(
            timeCasaId            = volta.timeCasaId,
            cobraCasa             = cobraCasa,
            cobraFinalizacaoCasa  = finCasa,
            goleiroCasaDefesa     = gkCasa,
            timeForaId            = volta.timeForaId,
            cobraFora             = cobraFora,
            cobraFinalizacaoFora  = finFora,
            goleiroForaDefesa     = gkFora
        )
        partidaDao.registrarPenaltis(volta.id, resultado.golsCasa, resultado.golsFora)
        return resultado.vencedorId
    }

    // ── Pênaltis: jogador seleciona cobradores ─────────────────────────
    // Chamado pelo ViewModel depois que o jogador escolhe os 5 cobradores.
    // Simula a disputa, persiste o resultado na volta e avança a fase Copa.
    suspend fun simularEPersistirPenaltisJogador(
        copaId: Int,
        anoAtual: Int,
        voltaPartidaId: Int,
        cobradoresJogador: List<JogadorNaEscalacao>,
        timeJogadorId: Int,
        goleiroJogadorDefesa: Int
    ): ResultadoPenaltis {
        val todasPartidas  = partidaDao.buscarTodasPorCampeonato(copaId)
        val voltaPartida   = todasPartidas.firstOrNull { it.id == voltaPartidaId }
            ?: error("Volta não encontrada ($voltaPartidaId)")
        val idaPartida     = todasPartidas
            .filter { it.confrontoId == voltaPartida.confrontoId }
            .minByOrNull { it.rodada }
            ?: error("Ida não encontrada para confronto ${voltaPartida.confrontoId}")

        val adversarioId = if (timeJogadorId == voltaPartida.timeCasaId)
            voltaPartida.timeForaId else voltaPartida.timeCasaId

        val escAdversario = gerarEscalacaoIA(adversarioId)
        val cobraAdversario = escAdversario.titulares
            .filter { it.posicaoUsada.setor != Setor.GOLEIRO }
            .sortedByDescending { it.jogador.finalizacao }
            .take(5).let { lista ->
                lista.map { it.jogador.id to it.jogador.nomeAbreviado } to
                        lista.map { it.jogador.finalizacao }
            }
        val gkAdversarioDefesa = escAdversario.titulares
            .firstOrNull { it.posicaoUsada.setor == Setor.GOLEIRO }?.jogador?.defesa ?: 70

        val cobraJogadorPairs = cobradoresJogador.map { it.jogador.id to it.jogador.nomeAbreviado }
        val finJogador        = cobradoresJogador.map { it.jogador.finalizacao }

        val resultado: ResultadoPenaltis
        if (timeJogadorId == voltaPartida.timeCasaId) {
            resultado = simulador.simularDisputaPenaltis(
                timeCasaId            = voltaPartida.timeCasaId,
                cobraCasa             = cobraJogadorPairs,
                cobraFinalizacaoCasa  = finJogador,
                goleiroCasaDefesa     = goleiroJogadorDefesa,
                timeForaId            = voltaPartida.timeForaId,
                cobraFora             = cobraAdversario.first,
                cobraFinalizacaoFora  = cobraAdversario.second,
                goleiroForaDefesa     = gkAdversarioDefesa
            )
        } else {
            resultado = simulador.simularDisputaPenaltis(
                timeCasaId            = voltaPartida.timeCasaId,
                cobraCasa             = cobraAdversario.first,
                cobraFinalizacaoCasa  = cobraAdversario.second,
                goleiroCasaDefesa     = gkAdversarioDefesa,
                timeForaId            = voltaPartida.timeForaId,
                cobraFora             = cobraJogadorPairs,
                cobraFinalizacaoFora  = finJogador,
                goleiroForaDefesa     = goleiroJogadorDefesa
            )
        }

        partidaDao.registrarPenaltis(voltaPartida.id, resultado.golsCasa, resultado.golsFora)
        verificarEAvancarFaseCopa(copaId, anoAtual)
        return resultado
    }

    /** Carrega os dados do adversário (GK + cobradores ordenados) para a disputa interativa. */
    suspend fun buscarDadosPenaltisAdversario(
        copaId: Int,
        voltaPartidaId: Int,
        timeJogadorId: Int
    ): DadosPenaltiAdversario {
        val todasPartidas = partidaDao.buscarTodasPorCampeonato(copaId)
        val voltaPartida  = todasPartidas.firstOrNull { it.id == voltaPartidaId }
            ?: error("Volta não encontrada ($voltaPartidaId)")
        val adversarioId = if (timeJogadorId == voltaPartida.timeCasaId)
            voltaPartida.timeForaId else voltaPartida.timeCasaId
        val escAdversario = gerarEscalacaoIA(adversarioId)
        val naoGoleiros = escAdversario.titulares
            .filter { it.posicaoUsada.setor != Setor.GOLEIRO }
            .sortedByDescending { it.jogador.finalizacao }
        return DadosPenaltiAdversario(
            gkDefesa    = escAdversario.titulares
                .firstOrNull { it.posicaoUsada.setor == Setor.GOLEIRO }?.jogador?.defesa ?: 70,
            cobradores  = naoGoleiros.map { it.jogador.id to it.jogador.nomeAbreviado },
            finalizacoes = naoGoleiros.map { it.jogador.finalizacao }
        )
    }

    /** Persiste o resultado já construído pela UI interativa e avança a fase da copa. */
    suspend fun persistirResultadoPenaltisJogador(
        resultado: ResultadoPenaltis,
        copaId: Int,
        anoAtual: Int,
        voltaPartidaId: Int
    ) {
        partidaDao.registrarPenaltis(voltaPartidaId, resultado.golsCasa, resultado.golsFora)
        verificarEAvancarFaseCopa(copaId, anoAtual)
    }

    /** Persiste pênaltis da UI interativa e avança a fase do torneio Argentine (jogo único). */
    suspend fun persistirResultadoPenaltisJogadorArgentina(
        resultado: ResultadoPenaltis,
        argCampeonatoId: Int,
        anoAtual: Int,
        partidaId: Int,
        timeJogadorId: Int,
        temporadaId: Int
    ) {
        partidaDao.registrarPenaltis(partidaId, resultado.golsCasa, resultado.golsFora)
        verificarEAvancarFaseArgentina(argCampeonatoId, anoAtual, timeJogadorId, temporadaId)
    }

    /** Simula pênaltis com cobradores do jogador e avança a fase do torneio Argentine. */
    suspend fun simularEPersistirPenaltisJogadorArgentina(
        argCampeonatoId: Int,
        anoAtual: Int,
        partidaId: Int,
        cobradoresJogador: List<JogadorNaEscalacao>,
        timeJogadorId: Int,
        goleiroJogadorDefesa: Int,
        temporadaId: Int
    ): ResultadoPenaltis {
        val todasPartidas = partidaDao.buscarTodasPorCampeonato(argCampeonatoId)
        val partida = todasPartidas.firstOrNull { it.id == partidaId }
            ?: error("Partida não encontrada ($partidaId)")

        val adversarioId = if (timeJogadorId == partida.timeCasaId) partida.timeForaId else partida.timeCasaId
        val escAdversario = gerarEscalacaoIA(adversarioId)
        val cobraAdversario = escAdversario.titulares
            .filter { it.posicaoUsada.setor != Setor.GOLEIRO }
            .sortedByDescending { it.jogador.finalizacao }
            .take(5).let { lista ->
                lista.map { it.jogador.id to it.jogador.nomeAbreviado } to
                        lista.map { it.jogador.finalizacao }
            }
        val gkAdversarioDefesa = escAdversario.titulares
            .firstOrNull { it.posicaoUsada.setor == Setor.GOLEIRO }?.jogador?.defesa ?: 70

        val cobraJogadorPairs = cobradoresJogador.map { it.jogador.id to it.jogador.nomeAbreviado }
        val finJogador        = cobradoresJogador.map { it.jogador.finalizacao }

        val resultado: ResultadoPenaltis = if (timeJogadorId == partida.timeCasaId) {
            simulador.simularDisputaPenaltis(
                timeCasaId            = partida.timeCasaId,
                cobraCasa             = cobraJogadorPairs,
                cobraFinalizacaoCasa  = finJogador,
                goleiroCasaDefesa     = goleiroJogadorDefesa,
                timeForaId            = partida.timeForaId,
                cobraFora             = cobraAdversario.first,
                cobraFinalizacaoFora  = cobraAdversario.second,
                goleiroForaDefesa     = gkAdversarioDefesa
            )
        } else {
            simulador.simularDisputaPenaltis(
                timeCasaId            = partida.timeCasaId,
                cobraCasa             = cobraAdversario.first,
                cobraFinalizacaoCasa  = cobraAdversario.second,
                goleiroCasaDefesa     = gkAdversarioDefesa,
                timeForaId            = partida.timeForaId,
                cobraFora             = cobraJogadorPairs,
                cobraFinalizacaoFora  = finJogador,
                goleiroForaDefesa     = goleiroJogadorDefesa
            )
        }

        partidaDao.registrarPenaltis(partida.id, resultado.golsCasa, resultado.golsFora)
        verificarEAvancarFaseArgentina(argCampeonatoId, anoAtual, timeJogadorId, temporadaId)
        return resultado
    }

    /** Simula e persiste pênaltis para a Supercopa (jogo único). */
    suspend fun simularEPersistirPenaltisJogadorSupercopa(
        supercopaId: Int,
        anoAtual: Int,
        partidaId: Int,
        cobradoresJogador: List<JogadorNaEscalacao>,
        timeJogadorId: Int,
        goleiroJogadorDefesa: Int,
        temporadaId: Int
    ): ResultadoPenaltis {
        val partida = partidaDao.buscarPorId(partidaId)
            ?: error("Partida não encontrada ($partidaId)")
        val adversarioId = if (timeJogadorId == partida.timeCasaId) partida.timeForaId else partida.timeCasaId
        val escAdversario = gerarEscalacaoIA(adversarioId)
        val naoGoleiros = escAdversario.titulares
            .filter { it.posicaoUsada.setor != Setor.GOLEIRO }
            .sortedByDescending { it.jogador.finalizacao }
            .take(5)
        val cobraAdversario = naoGoleiros.map { it.jogador.id to it.jogador.nomeAbreviado } to
                naoGoleiros.map { it.jogador.finalizacao }
        val gkAdversarioDefesa = escAdversario.titulares
            .firstOrNull { it.posicaoUsada.setor == Setor.GOLEIRO }?.jogador?.defesa ?: 70

        val cobraJogadorPairs = cobradoresJogador.map { it.jogador.id to it.jogador.nomeAbreviado }
        val finJogador        = cobradoresJogador.map { it.jogador.finalizacao }

        val resultado: ResultadoPenaltis
        if (timeJogadorId == partida.timeCasaId) {
            resultado = simulador.simularDisputaPenaltis(
                timeCasaId           = partida.timeCasaId,
                cobraCasa            = cobraJogadorPairs,
                cobraFinalizacaoCasa = finJogador,
                goleiroCasaDefesa    = goleiroJogadorDefesa,
                timeForaId           = partida.timeForaId,
                cobraFora            = cobraAdversario.first,
                cobraFinalizacaoFora = cobraAdversario.second,
                goleiroForaDefesa    = gkAdversarioDefesa
            )
        } else {
            resultado = simulador.simularDisputaPenaltis(
                timeCasaId           = partida.timeCasaId,
                cobraCasa            = cobraAdversario.first,
                cobraFinalizacaoCasa = cobraAdversario.second,
                goleiroCasaDefesa    = gkAdversarioDefesa,
                timeForaId           = partida.timeForaId,
                cobraFora            = cobraJogadorPairs,
                cobraFinalizacaoFora = finJogador,
                goleiroForaDefesa    = goleiroJogadorDefesa
            )
        }

        partidaDao.registrarPenaltis(partida.id, resultado.golsCasa, resultado.golsFora)
        verificarEEncerrarSupercopa(supercopaId, anoAtual, timeJogadorId, temporadaId)
        return resultado
    }

    /** Persiste o resultado interativo de pênaltis e encerra a Supercopa. */
    suspend fun persistirResultadoPenaltisJogadorSupercopa(
        resultado: ResultadoPenaltis,
        supercopaId: Int,
        anoAtual: Int,
        partidaId: Int,
        timeJogadorId: Int,
        temporadaId: Int
    ) {
        partidaDao.registrarPenaltis(partidaId, resultado.golsCasa, resultado.golsFora)
        verificarEEncerrarSupercopa(supercopaId, anoAtual, timeJogadorId, temporadaId)
    }

    private suspend fun gerarEscalacaoIA(timeId: Int): Escalacao {
        val time = timeRepository.buscarPorId(timeId) ?: Time(
            id                  = timeId,
            nome                = "Time $timeId",
            cidade              = "",
            estado              = "",
            nivel               = 1,
            divisao             = 1,
            saldo               = 0L,
            estadioCapacidade   = 0,
            precoIngresso       = 0L,
            taticaFormacao      = "4-4-2",
            estiloJogo          = EstiloJogo.EQUILIBRADO,
            reputacao           = 50f,
            controladoPorJogador = false
        )
        val elenco = jogadorRepository.buscarDisponiveis(timeId)
        return IATimeRival.gerarEscalacao(time, elenco)
    }

    /** Usa a escalação salva pelo jogador; se vazia, cai no IA. */
    suspend fun gerarEscalacaoJogador(timeId: Int): Escalacao {
        val time = timeRepository.buscarPorId(timeId)
            ?: throw IllegalStateException("Time $timeId não encontrado")
        val titularesSalvos = jogadorRepository.buscarTitularesSalvos(timeId)
        if (titularesSalvos.isNotEmpty()) {
            val reservasSalvas = jogadorRepository.buscarReservasSalvas(timeId)
            val titulares = titularesSalvos.map { j -> JogadorNaEscalacao(j, j.posicaoEscalado ?: j.posicao) }
            val reservas  = reservasSalvas.map  { j -> JogadorNaEscalacao(j, j.posicaoEscalado ?: j.posicao) }
            return Escalacao(time = time, titulares = titulares, reservas = reservas)
        }
        val elenco = jogadorRepository.buscarDisponiveis(timeId)
        return IATimeRival.gerarEscalacao(time, elenco)
    }

    private suspend fun persistirResultado(resultado: ResultadoPartida): Map<Int, Float> {
        // Calcula público e receita para partidas em casa
        val timeCasa = timeRepository.buscarPorId(resultado.timeCasaId)
        val (torcedores, receita) = if (timeCasa != null) {
            MotorFinanceiro.calcularPublico(timeCasa, adversarioNivel = 5)
        } else {
            Pair(0, 0L)
        }

        partidaDao.registrarResultado(
            resultado.partidaId,
            resultado.golsCasa,
            resultado.golsFora,
            torcedores,
            receita
        )

        // Credita a receita de bilheteria diretamente no saldo do clube mandante
        if (receita > 0) {
            timeRepository.creditarSaldo(resultado.timeCasaId, receita)
        }

        partidaDao.inserirEventos(resultado.eventos
            .filter { ev -> ev.jogadorId > 0 }
            .map { ev ->
                EventoPartidaEntity(
                    partidaId = resultado.partidaId,
                    jogadorId = ev.jogadorId,
                    minuto = ev.minuto,
                    tipo = ev.tipo,
                    descricao = ev.descricao
                )
            }
        )

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

            // Atualiza ranking geral em tempo real (inclui Copa e ligas)
            atualizarRankingAposPartida(resultado)
        }

        // Atualiza reputação: +1 para o vencedor, -1 para o perdedor (sem alteração em empate)
        val timeFora = timeRepository.buscarPorId(resultado.timeForaId)
        if (timeCasa != null && timeFora != null) {
            when {
                resultado.golsCasa > resultado.golsFora -> {
                    timeRepository.atualizarReputacao(resultado.timeCasaId, timeCasa.reputacao + 0.1f)
                    timeRepository.atualizarReputacao(resultado.timeForaId, timeFora.reputacao - 0.1f)
                }
                resultado.golsFora > resultado.golsCasa -> {
                    timeRepository.atualizarReputacao(resultado.timeForaId, timeFora.reputacao + 0.1f)
                    timeRepository.atualizarReputacao(resultado.timeCasaId, timeCasa.reputacao - 0.1f)
                }
                // empate: sem alteração
            }
        }

        // Calcula e persiste a nota de cada jogador participante;
        // aplica também a evolução/regressão incremental baseada na performance
        val notas = calcularNotasJogadores(resultado)
        notas.forEach { (jogadorId, nota) ->
            if (jogadorId > 0) jogadorRepository.atualizarNotaEEvolucao(jogadorId, nota)
        }

        // Atualiza fadiga: participantes perdem energia, quem ficou no banco recupera.
        // Também decrementa o contador de ausência de jogadores lesionados de jogos anteriores.
        val participantesIds = resultado.eventos
            .filter { it.tipo == TipoEvento.PARTICIPOU || it.tipo == TipoEvento.SUBSTITUICAO_ENTRA }
            .map { it.jogadorId }.toSet()
        jogadorRepository.atualizarFadigaAposPartida(
            participantes = participantesIds,
            timeIds       = setOf(resultado.timeCasaId, resultado.timeForaId)
        )

        // Aplica lesões ocorridas nesta partida (após o decremento, para não consumir imediatamente).
        // Grau 1 (leve) = 2–3 jogos | Grau 2 (moderada) = 4–5 | Grau 3 (grave) = 6–8
        resultado.eventos
            .filter { it.tipo == TipoEvento.LESAO }
            .forEach { ev ->
                val partidas = when (ev.grauLesao) {
                    3    -> (6..8).random()
                    2    -> (4..5).random()
                    else -> (2..3).random()  // grau 1 ou desconhecido
                }
                jogadorRepository.aplicarLesao(ev.jogadorId, partidas)
            }

        return notas
    }

    /**
     * Calcula a nota da partida (1.0–10.0) para cada jogador que entrou em campo.
     * Base 6.0 · +1.2 por gol · +0.7 por assistência · -0.3 amarelo · -1.5 vermelho ·
     * -0.5 lesão · +0.3 bônus de vitória.
     */
    /**
     * Deriva [EstatisticasTime] a partir de eventos acumulados.
     * Cartões e defesas são contados diretamente; chutes estimados a partir das defesas e gols.
     */
    private fun estatisticasDeEventos(
        eventos: List<EventoSimulado>,
        timeId: Int,
        adversarioId: Int,
        golsTime: Int
    ): EstatisticasTime {
        val amarelos   = eventos.count { it.tipo == TipoEvento.CARTAO_AMARELO  && it.timeId == timeId }
        val vermelhos  = eventos.count { it.tipo == TipoEvento.CARTAO_VERMELHO && it.timeId == timeId }
        val defesas    = eventos.count { it.tipo == TipoEvento.DEFESA_GOLEIRO  && it.timeId == timeId }
        val defAdv     = eventos.count { it.tipo == TipoEvento.DEFESA_GOLEIRO  && it.timeId == adversarioId }
        // chutes no alvo por este time = defesas do adversário + gols marcados
        val chutesNoGol = defAdv + golsTime
        val chutes      = (chutesNoGol * 2).coerceAtLeast(golsTime + 2)
        val faltas      = (amarelos * 3 + vermelhos * 5 + 5).coerceIn(5, 25)
        return EstatisticasTime(
            chutes         = chutes,
            chutesNoGol    = chutesNoGol,
            posse          = 50,
            faltas         = faltas,
            cartaoAmarelo  = amarelos,
            cartaoVermelho = vermelhos,
            defesasGoleiro = defesas,
            passesErrados  = 15
        )
    }

    private fun calcularNotasJogadores(resultado: ResultadoPartida): Map<Int, Float> {
        // Participantes = titulares (PARTICIPOU) + substitutos (SUBSTITUICAO_ENTRA)
        val participantes = resultado.eventos
            .filter { it.tipo == TipoEvento.PARTICIPOU || it.tipo == TipoEvento.SUBSTITUICAO_ENTRA }
            .groupBy { it.jogadorId }

        val vitoriosaCasa = resultado.golsCasa > resultado.golsFora
        val vitoriosafora = resultado.golsFora > resultado.golsCasa

        return participantes.mapValues { (jogadorId, participacoes) ->
            var nota = 5.0f
            val ehCasa = participacoes.any { it.timeId == resultado.timeCasaId }
            val eventos = resultado.eventos.filter { it.jogadorId == jogadorId }
            for (ev in eventos) {
                when (ev.tipo) {
                    TipoEvento.GOL                  -> nota += 1.2f
                    TipoEvento.ASSISTENCIA          -> nota += 0.7f
                    TipoEvento.PENALTI_CONVERTIDO   -> nota += 0.5f
                    TipoEvento.PENALTI_PERDIDO      -> nota -= 0.5f
                    TipoEvento.CARTAO_AMARELO        -> nota -= 0.3f
                    TipoEvento.CARTAO_VERMELHO       -> nota -= 1.5f
                    TipoEvento.LESAO                 -> nota -= 0.5f
                    TipoEvento.DEFESA_GOLEIRO        -> nota += 0.6f
                    else -> Unit
                }
            }
            // Penalidade do goleiro por gols sofridos (-1.0 por gol)
            if (jogadorId == resultado.gkCasaId) nota -= resultado.golsFora * 1.0f
            if (jogadorId == resultado.gkForaId) nota -= resultado.golsCasa * 1.0f
            if ((ehCasa && vitoriosaCasa) || (!ehCasa && vitoriosafora)) nota += 0.3f
            nota.coerceIn(1.0f, 10.0f)
        }
    }

    private suspend fun atualizarRankingAposPartida(resultado: ResultadoPartida) {
        val casa = timeRepository.buscarEntityPorId(resultado.timeCasaId) ?: return
        val fora = timeRepository.buscarEntityPorId(resultado.timeForaId) ?: return

        val golsCasa = resultado.golsCasa
        val golsFora = resultado.golsFora
        val casaVenceu = golsCasa > golsFora
        val foraVenceu = golsFora > golsCasa
        val empate     = golsCasa == golsFora

        val ptsCasa = if (casaVenceu) 3 else if (empate) 1 else 0
        val ptsFora = if (foraVenceu) 3 else if (empate) 1 else 0

        val existCasa = rankingGeralDao.buscarPorTime(casa.id)
        rankingGeralDao.inserirOuAtualizar(
            RankingGeralEntity(
                timeId            = casa.id,
                nomeTime          = casa.nome,
                escudoRes         = casa.escudoRes,
                divisaoAtual      = casa.divisao,
                pontosAcumulados  = (existCasa?.pontosAcumulados ?: 0L) + ptsCasa,
                temporadasJogadas = existCasa?.temporadasJogadas ?: 0,
                copasVencidas     = existCasa?.copasVencidas ?: 0,
                titulosNacionais  = existCasa?.titulosNacionais ?: 0,
                vitorias          = (existCasa?.vitorias ?: 0) + if (casaVenceu) 1 else 0,
                empates           = (existCasa?.empates  ?: 0) + if (empate)     1 else 0,
                derrotas          = (existCasa?.derrotas ?: 0) + if (foraVenceu) 1 else 0,
                golsPro           = (existCasa?.golsPro   ?: 0) + golsCasa,
                golsContra        = (existCasa?.golsContra ?: 0) + golsFora
            )
        )

        val existFora = rankingGeralDao.buscarPorTime(fora.id)
        rankingGeralDao.inserirOuAtualizar(
            RankingGeralEntity(
                timeId            = fora.id,
                nomeTime          = fora.nome,
                escudoRes         = fora.escudoRes,
                divisaoAtual      = fora.divisao,
                pontosAcumulados  = (existFora?.pontosAcumulados ?: 0L) + ptsFora,
                temporadasJogadas = existFora?.temporadasJogadas ?: 0,
                copasVencidas     = existFora?.copasVencidas ?: 0,
                titulosNacionais  = existFora?.titulosNacionais ?: 0,
                vitorias          = (existFora?.vitorias ?: 0) + if (foraVenceu) 1 else 0,
                empates           = (existFora?.empates  ?: 0) + if (empate)     1 else 0,
                derrotas          = (existFora?.derrotas ?: 0) + if (casaVenceu) 1 else 0,
                golsPro           = (existFora?.golsPro   ?: 0) + golsFora,
                golsContra        = (existFora?.golsContra ?: 0) + golsCasa
            )
        )
    }

    private suspend fun buscarCampeonatoIdDaPartida(partidaId: Int): Int? =
        partidaDao.buscarCampeonatoId(partidaId)

    /**
     * Nas janelas de transferência (meses 1, 2, 10, 11, 12), times da IA mais fortes
     * compram jogadores de outros times da IA (não do usuário).
     *
     * Regras:
     *  - Compradores: times ordenados por reputação (melhores primeiro)
     *  - Alvo: jogadores de outros times da IA cujo nível de força seja compatível
     *    com o time comprador (±10 de força média)
     *  - Preço ofertado: 90%–110% do valor de mercado do jogador alvo
     *  - O time vendedor recebe o valor e o jogador é transferido
     *  - Cada time da IA executa no máximo 1 compra de outro time da IA por ciclo
     */
    private suspend fun executarTransferenciasIAParaIA(playerTimeId: Int, temporadaId: Int, mes: Int) {
        val todos = timeRepository.buscarTodosOrdenadosPorReputacao()
        val timesIA = todos.filter { it.id != playerTimeId }
        if (timesIA.size < 2) return

        val probabilidade = if (mes <= 2) 0.30f else 0.15f

        // Jogadores já transferidos nesta rodada — impede que o mesmo jogador
        // seja negociado por mais de um time dentro do mesmo fechamento mensal.
        val jogadoresMovidos = mutableSetOf<Int>()

        for (comprador in timesIA) {
            if (kotlin.random.Random.nextFloat() >= probabilidade) continue

            val orcamento = (comprador.saldo * 0.20).toLong()
            if (orcamento <= 0L) continue

            val elencoComprador = jogadorRepository.buscarDisponiveis(comprador.id)
            if (elencoComprador.size >= 26) continue
            val forcaMediaComprador = if (elencoComprador.isNotEmpty())
                elencoComprador.map { it.forca }.average().toInt() else 50

            val candidatos = timesIA
                .filter { it.id != comprador.id }
                .flatMap { timeVendedor ->
                    val elencoVendedor = jogadorRepository.buscarDisponiveis(timeVendedor.id)
                    if (elencoVendedor.size <= 18) return@flatMap emptyList()
                    elencoVendedor
                        .filter { !it.lesionado && !it.categoriaBase }
                        .filter { it.id !in jogadoresMovidos }  // não negociar jogador já transferido
                        .filter { it.forca in (forcaMediaComprador - 8)..(forcaMediaComprador + 12) }
                        .filter { (it.valorMercado * 0.90).toLong() <= orcamento }
                        .map { jogador -> Pair(timeVendedor.id, jogador) }
                }
            if (candidatos.isEmpty()) continue

            val (vendedorId, alvo) = candidatos
                .sortedByDescending { (_, j) -> j.forca }
                .firstOrNull() ?: continue

            val valorOferta = (alvo.valorMercado * kotlin.random.Random.nextDouble(0.90, 1.10)).toLong()
                .coerceAtLeast(1L)

            timeRepository.debitarSaldo(comprador.id, valorOferta)
            timeRepository.creditarSaldo(vendedorId, valorOferta)
            jogadorRepository.realizarTransferencia(
                OfertaTransferencia(
                    jogadorId       = alvo.id,
                    timeCompradorId = comprador.id,
                    timeVendedorId  = vendedorId,
                    valor           = valorOferta,
                    salarioProposto = alvo.salario,
                    contratoAnos    = 3
                ),
                temporadaId, mes
            )
            jogadoresMovidos.add(alvo.id)
        }
    }
}