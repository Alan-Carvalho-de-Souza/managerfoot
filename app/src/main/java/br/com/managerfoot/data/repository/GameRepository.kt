package br.com.managerfoot.data.repository

import androidx.room.withTransaction
import br.com.managerfoot.data.dao.CampeonatoDao
import br.com.managerfoot.data.dao.ClassificacaoDao
import br.com.managerfoot.data.dao.CopaPartidaDto
import br.com.managerfoot.data.dao.EstadioDao
import br.com.managerfoot.data.dao.HallDaFamaDao
import br.com.managerfoot.data.dao.PartidaDao
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
    private val estadioDao: EstadioDao
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
            timeJogadorId = timeJogadorId
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
        val (estadoApos1H, eventos1H) = simulador.simularPeriodo(
            minInicio     = 1,
            minFim        = 45,
            fracaoGols    = 0.5,
            escalacaoCasa = escalacaoCasa,
            escalacaoFora = escalacaoFora,
            estado        = estadoInicial,
            timeJogadorId = timeJogadorId
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
            estatisticasCasa = EstatisticasTime(0, 0, 50, 0, 0, 0),
            estatisticasFora = EstatisticasTime(0, 0, 50, 0, 0, 0),
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
            eventosAcumulados        = todosEventos1H
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
            timeJogadorId = ctx.timeJogadorId
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
            estatisticasCasa = EstatisticasTime(
                chutes        = golsCasaFinal * 4 + 3,
                chutesNoGol   = golsCasaFinal + 2,
                posse         = 50,
                faltas        = 12,
                cartaoAmarelo  = 0,
                cartaoVermelho = 0
            ),
            estatisticasFora = EstatisticasTime(
                chutes        = golsForaFinal * 4 + 3,
                chutesNoGol   = golsForaFinal + 2,
                posse         = 50,
                faltas        = 12,
                cartaoAmarelo  = 0,
                cartaoVermelho = 0
            )
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
        val copaId: Int,
        val temporadaId: Int,
        val ano: Int
    )

    suspend fun encerrarTemporadaComHallDaFama(
        campeonatoAId: Int,
        campeonatoBId: Int,
        campeonatoCId: Int,
        campeonatoDId: Int,
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
            val nomeSerie  = when (div) { 1 -> "A"; 2 -> "B"; 3 -> "C"; else -> "D" }
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

        // ── Premiações de títulos — somente para o time do jogador ─────────
        // Os valores estão em centavos (1 milhão = 1_000_000_00L no padrão monetário do jogo)
        suspend fun premiarSeJogador(campId: Int, premio: Long, desc: String) {
            if (campId <= 0 || timeJogadorId <= 0) return
            val campeao = classificacaoDao.buscarTop2(campId).firstOrNull() ?: return
            if (campeao.timeId != timeJogadorId) return
            timeRepository.creditarSaldo(timeJogadorId, premio)
            financaDao.inserir(
                br.com.managerfoot.data.database.entities.FinancaEntity(
                    timeId            = timeJogadorId,
                    temporadaId       = temporadaId,
                    mes               = 13,  // mês 13 = encerramento da temporada
                    receitaPremiacoes = premio,
                    saldoFinal        = premio
                )
            )
        }
        premiarSeJogador(campeonatoAId, 20_000_000_00L, "Campeão Série A")  // R$ 20 milhões
        premiarSeJogador(campeonatoBId,  8_000_000_00L, "Campeão Série B")  // R$  8 milhões
        premiarSeJogador(campeonatoCId,  5_000_000_00L, "Campeão Série C")  // R$  5 milhões
        premiarSeJogador(campeonatoDId,  2_000_000_00L, "Campeão Série D")  // R$  2 milhões

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
        listOf(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId)
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

        return NovaTemporadaInfo(novoCampeonatoAId, novoCampeonatoBId, novoCampeonatoCId, novoCampeonatoDId, novoCopaId, novoTemporadaId, novoAno)
    }

    fun observarHallDaFama(): Flow<List<HallDaFamaEntity>> = hallDaFamaDao.observeTodos()

    fun observarCopaPartidas(copaId: Int): Flow<List<CopaPartidaDto>> =
        partidaDao.observeCopaPartidas(copaId)

    fun observarRankingGeral(): Flow<List<RankingGeralEntity>> =
        rankingGeralDao.observeRanking()

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

    // ── Verifica se fase atual foi concluída e avança para próxima ──
    // Retorna true se a Copa foi finalizada (Final concluída).
    suspend fun verificarEAvancarFaseCopa(
        copaId: Int,
        anoAtual: Int,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1
    ): Boolean {
        val todasPartidas = partidaDao.buscarTodasPorCampeonato(copaId)
        if (todasPartidas.isEmpty()) return false

        for (faseAtual in MotorCampeonato.COPA_FASES) {
            val partidasFase = todasPartidas.filter { it.fase == faseAtual }
            if (partidasFase.isEmpty()) continue

            // Fase em andamento? Se houver alguma não jogada, ainda não acabou
            if (!partidasFase.all { it.jogada }) return false

            // Fase completa → checar se próxima já foi gerada
            val proximaFase = MotorCampeonato.proximaFaseCopa(faseAtual)
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

                val campeao   = timeRepository.buscarEntityPorId(campeaoId)
                val vice      = timeRepository.buscarEntityPorId(viceId)
                val copaEnt   = campeonatoDao.buscarPorId(copaId)
                val artilheiro = partidaDao.buscarArtilheiroTop1(copaId)
                val assistente = partidaDao.buscarAssisteTop1(copaId)

                hallDaFamaDao.inserir(
                    HallDaFamaEntity(
                        ano                 = anoAtual,
                        nomeCampeonato      = copaEnt?.nome ?: "Copa do Brasil $anoAtual",
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
                        divisao             = 5   // 5 = Copa
                    )
                )
                // Incrementa copasVencidas do campeão no ranking geral
                val rankCampeao = rankingGeralDao.buscarPorTime(campeaoId)
                if (rankCampeao != null) {
                    rankingGeralDao.inserirOuAtualizar(
                        rankCampeao.copy(copasVencidas = rankCampeao.copasVencidas + 1)
                    )
                }
                campeonatoDao.encerrar(copaId)
                // Premiação Copa do Brasil → somente para o time do jogador
                if (timeJogadorId > 0 && campeaoId == timeJogadorId) {
                    timeRepository.creditarSaldo(timeJogadorId, 30_000_000_00L)  // R$ 30 milhões
                    financaDao.inserir(
                        br.com.managerfoot.data.database.entities.FinancaEntity(
                            timeId            = timeJogadorId,
                            temporadaId       = temporadaId,
                            mes               = 13,
                            receitaPremiacoes = 30_000_000_00L,
                            saldoFinal        = 30_000_000_00L
                        )
                    )
                }
                // Copa do Brasil: +4% de reputação para o campeão
                val campeaoCopa = timeRepository.buscarPorId(campeaoId)
                if (campeaoCopa != null) {
                    val bonus = (campeaoCopa.reputacao * 0.04f).coerceAtLeast(0.1f)
                    timeRepository.atualizarReputacao(campeaoId, campeaoCopa.reputacao + bonus)
                }
                return true
            }

            // Gera partidas da próxima fase
            val faseIndex   = MotorCampeonato.COPA_FASES.indexOf(proximaFase)
            val rodadaIda   = MotorCampeonato.rodadaIdaDeFase(faseIndex)
            val maxConfId   = todasPartidas.mapNotNull { it.confrontoId }.maxOrNull() ?: 0
            val novosPares  = MotorCampeonato.sortearPares(vencedores)
            val novasPartidas = MotorCampeonato.gerarFaseIdaVolta(
                campeonatoId       = copaId,
                pares              = novosPares,
                fase               = proximaFase,
                rodadaIda          = rodadaIda,
                confrontoIdInicio  = maxConfId + 1,
                ordemGlobalIda     = MotorCampeonato.COPA_ORDEM_GLOBAL[faseIndex * 2],
                ordemGlobalVolta   = MotorCampeonato.COPA_ORDEM_GLOBAL[faseIndex * 2 + 1]
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

    // ── Atualiza ranking geral após término de uma competição ────────
    // Chamado ao final de cada temporada: registra +1 temporada jogada, atualiza divisão
    // e contabiliza +1 título nacional para o campeão (1º colocado).
    // Pontos/V/E/D já foram acumulados em tempo real via atualizarRankingAposPartida.
    suspend fun atualizarRankingGeral(campeonatoId: Int) {
        if (campeonatoId <= 0) return
        val classificacoes = classificacaoDao.buscarTabelaOrdenada(campeonatoId)
        val campeaoTimeId = classificacoes.firstOrNull()?.timeId
        for (cls in classificacoes) {
            val time     = timeRepository.buscarEntityPorId(cls.timeId) ?: continue
            val existing = rankingGeralDao.buscarPorTime(cls.timeId) ?: continue
            val ehCampeao = cls.timeId == campeaoTimeId
            rankingGeralDao.inserirOuAtualizar(
                existing.copy(
                    nomeTime          = time.nome,
                    escudoRes         = time.escudoRes,
                    divisaoAtual      = time.divisao,
                    temporadasJogadas = existing.temporadasJogadas + 1,
                    titulosNacionais  = existing.titulosNacionais + if (ehCampeao) 1 else 0
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
            // Primeira temporada: top 64 por reputação
            timeRepository.buscarTodosOrdenadosPorReputacao().take(64).map { it.id }
        } else {
            // Série A (automáticos) + top 44 do ranking geral não presentes na Série A
            val serieASet = participantesSerieA.toSet()
            val top44 = rankingGeralDao.buscarTopN(200)
                .filter { it.timeId !in serieASet }
                .take(64 - participantesSerieA.size)
                .map { it.timeId }
            (participantesSerieA + top44).take(64)
        }
    }

    suspend fun fecharMes(timeId: Int, temporadaId: Int, mes: Int, patrocinioMensal: Long = 0L, patrocinioJaCreditado: Long = 0L) {
        val time = timeRepository.buscarPorId(timeId) ?: return
        val elenco = jogadorRepository.buscarDisponiveis(timeId)

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

    /**
     * Chamado imediatamente após o jogador contratar um atleta de um time da IA.
     * Se o time vendedor ficou com menos de 18 jogadores, recompõe o elenco até 22
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

        for (time in todos) {
            if (time.id == playerTimeId) continue
            val elenco = jogadorRepository.buscarDisponiveis(time.id)
            if (elenco.size >= 20) continue   // IA só contrata se abaixo de 20 jogadores

            val ofertas = IATimeRival.decidirContratacoes(
                time         = time,
                elencoAtual  = elenco,
                mercado      = mercadoLivre.filter { it.timeId == null },  // só livres, sem custo
                orcamento    = time.saldo
            )

            for (oferta in ofertas.take(2)) {
                // Livre = valor 0; não debitar saldo da IA por passe livre
                val ofertaLivre = oferta.copy(valor = 0L, timeVendedorId = null)
                jogadorRepository.realizarTransferencia(ofertaLivre, temporadaId, mes)
                // Atualiza salário do jogador no banco (apenas timeId já foi atualizado em realizarTransferencia)
            }
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

        for (time in todos) {
            if (time.id == playerTimeId) continue
            val elencoAtual = jogadorRepository.buscarDisponiveis(time.id)
            if (elencoAtual.isEmpty()) continue

            val escalacao = IATimeRival.gerarEscalacao(time, elencoAtual)
            val maisFrago = escalacao.titulares.minByOrNull { it.jogador.forca } ?: continue
            val posNecessaria = maisFrago.posicaoUsada

            val orcamentoReforco = (time.saldo * 0.30).toLong()
            if (orcamentoReforco <= 0L) continue

            val candidato = livres
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

                val qtd = when {
                    classi.aproveitamento < 0.20f -> 3
                    classi.aproveitamento < 0.30f -> 2
                    else                          -> 1
                }

                val elenco = jogadorRepository.buscarDisponiveis(classi.timeId)
                if (elenco.size >= 30) return@forEachIndexed   // elenco grande demais, não precisa

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

        val resultado = simulador.simular(
            partidaId = partida.id,
            casa = escalacaoCasa,
            fora = escalacaoFora
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
    private fun calcularNotasJogadores(resultado: ResultadoPartida): Map<Int, Float> {
        // Participantes = titulares (PARTICIPOU) + substitutos (SUBSTITUICAO_ENTRA)
        val participantes = resultado.eventos
            .filter { it.tipo == TipoEvento.PARTICIPOU || it.tipo == TipoEvento.SUBSTITUICAO_ENTRA }
            .groupBy { it.jogadorId }

        val vitoriosaCasa = resultado.golsCasa > resultado.golsFora
        val vitoriosafora = resultado.golsFora > resultado.golsCasa

        return participantes.mapValues { (jogadorId, participacoes) ->
            var nota = 6.0f
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
                    TipoEvento.DEFESA_GOLEIRO        -> nota += 1.0f
                    else -> Unit
                }
            }
            // Penalidade do goleiro por gols sofridos
            if (jogadorId == resultado.gkCasaId) nota -= resultado.golsFora * 0.7f
            if (jogadorId == resultado.gkForaId) nota -= resultado.golsCasa * 0.7f
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
}