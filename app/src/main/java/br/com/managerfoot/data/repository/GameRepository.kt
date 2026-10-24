package br.com.managerfoot.data.repository

import androidx.room.withTransaction
import br.com.managerfoot.data.dao.CampeonatoDao
import br.com.managerfoot.data.dao.ClassificacaoDao
import br.com.managerfoot.data.dao.CampanhaMundialDto
import br.com.managerfoot.data.dao.CopaPartidaDto
import br.com.managerfoot.data.dao.EstadioDao
import br.com.managerfoot.data.dao.HallDaFamaDao
import br.com.managerfoot.data.dao.PartidaDao
import br.com.managerfoot.data.dao.PropostaClubeDao
import br.com.managerfoot.data.dao.PropostaIADao
import br.com.managerfoot.data.dao.RankingBrasileiroDao
import br.com.managerfoot.data.dao.RankingEuropaDao
import br.com.managerfoot.data.dao.RankingEuropaLinha
import br.com.managerfoot.data.dao.RankingConmebolDao
import br.com.managerfoot.data.dao.RankingGeralDao
import br.com.managerfoot.data.database.AppDatabase
import br.com.managerfoot.data.database.entities.*
import br.com.managerfoot.domain.engine.*
import br.com.managerfoot.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
    private val tecnicoRepository: TecnicoRepository,
    private val financaDao: br.com.managerfoot.data.dao.FinancaDao,
    private val hallDaFamaDao: HallDaFamaDao,
    private val rankingGeralDao: RankingGeralDao,
    private val rankingConmebolDao: RankingConmebolDao,
    private val rankingBrasileiroDao: RankingBrasileiroDao,
    private val rankingEuropaDao: RankingEuropaDao,
    private val estadioDao: EstadioDao,
    private val propostaIADao: PropostaIADao,
    private val propostaClubeDao: PropostaClubeDao
) {
    private val simulador = SimuladorPartida()

    /**
     * Insere times e jogadores do seed em uma única transação atômica.
     * Se qualquer parte falhar, nenhuma linha fica no banco — evitando o
     * estado corrompido de "times sem jogadores".
     */
    suspend fun inserirSeedTransacional(
        times: List<TimeEntity>,
        jogadores: List<JogadorEntity>,
        tecnicos: List<TecnicoEntity> = emptyList()
    ) {
        db.withTransaction {
            db.timeDao().inserirTodos(times)
            db.jogadorDao().inserirTodos(jogadores)
            if (tecnicos.isNotEmpty()) {
                db.tecnicoDao().inserirTodos(tecnicos)
            }
        }
    }

    /**
     * Inserção ADITIVA e idempotente: adiciona apenas os times do seed que ainda
     * não existem no banco (e seus jogadores), SEM apagar nem sobrescrever dados
     * de um jogo em andamento. Serve para expor ligas novas (ex.: Premier League)
     * na tela de seleção quando o banco já foi populado por uma versão anterior
     * do seed — caso em que [seedEstaIntegro] retorna true e o seed não seria
     * recarregado. Retorna a quantidade de times novos inseridos.
     */
    suspend fun inserirSeedFaltante(
        times: List<TimeEntity>,
        jogadores: List<JogadorEntity>
    ): Int {
        val existentes = db.timeDao().buscarTodos().map { it.id }.toSet()
        val novosTimes = times.filter { it.id !in existentes }
        if (novosTimes.isEmpty()) return 0
        val novosIds = novosTimes.map { it.id }.toSet()
        val novosJogadores = jogadores.filter { it.timeId != null && it.timeId in novosIds }
        db.withTransaction {
            db.timeDao().inserirTodos(novosTimes)
            db.jogadorDao().inserirTodos(novosJogadores)
        }
        return novosTimes.size
    }

    /**
     * Backfill idempotente dos escudos. Para times JÁ existentes cujo escudoRes
     * está vazio no banco mas o seed passou a ter um valor, preenche apenas o
     * escudoRes — sem tocar em saldo, elenco ou qualquer estado de jogo. Cobre o
     * caso de um banco populado por uma versão do seed anterior aos escudos de
     * uma liga (ex.: Premier), em que [inserirSeedFaltante] ignora o time por já
     * existir e o escudo nunca seria atualizado. Retorna quantos foram preenchidos.
     */
    suspend fun sincronizarEscudosDoSeed(times: List<TimeEntity>): Int {
        val atuais = db.timeDao().buscarTodos().associateBy { it.id }
        var alterados = 0
        db.withTransaction {
            times.forEach { seed ->
                val atual = atuais[seed.id] ?: return@forEach
                // Backfill de escudo quando vazio (nunca sobrescreve um já definido).
                if (seed.escudoRes.isNotBlank() && atual.escudoRes.isBlank()) {
                    db.timeDao().preencherEscudoSeVazio(seed.id, seed.escudoRes)
                    alterados++
                }
                // Concilia a divisão APENAS de clubes espanhóis (La Liga=13) e
                // italianos (Serie A=14), cuja divisão é invariante no seed (sem
                // 2ª divisão no MVP). Ingleses têm promoção/rebaixamento
                // Premier↔Championship, portanto NÃO são forçados (a divisão
                // atual pode ser legítima e diferente do seed). Demais países
                // também têm promoção/rebaixamento alterando a divisão.
                if ((seed.pais == "Espanha" || seed.pais == "Italia") && atual.divisao != seed.divisao) {
                    db.timeDao().atualizarDivisao(seed.id, seed.divisao)
                    alterados++
                }
            }
        }
        return alterados
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
        rankingConmebolDao.deleteAll()
        rankingBrasileiroDao.deleteAll()
        rankingEuropaDao.deleteAll()
        estadioDao.deleteAll()
        propostaIADao.limparTodas()
        propostaClubeDao.limparTodas()
        db.tecnicoDao().deleteAllPassagens()
        db.tecnicoDao().deleteAll()
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
    fun observarArtilheirosHistoricoPaisTipo(pais: String, tipos: List<String>) = partidaDao.observeArtilheirosHistoricoPaisTipo(pais, tipos)
    fun observarAssistentesHistoricoPaisTipo(pais: String, tipos: List<String>) = partidaDao.observeAssistentesHistoricoPaisTipo(pais, tipos)
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
    /** Um jogo da mesma rodada (outra partida), com os minutos dos gols p/ revelar "ao vivo". */
    data class JogoRodadaAoVivo(
        val partidaId: Int,
        val timeCasaId: Int, val nomeCasa: String, val escudoCasa: String,
        val timeForaId: Int, val nomeFora: String, val escudoFora: String,
        val minutosGolsCasa: List<Int>,
        val minutosGolsFora: List<Int>,
        // Copa: resultado de pênaltis quando o confronto termina empatado (null em liga/amistoso).
        val penaltisCasa: Int? = null,
        val penaltisFora: Int? = null
    )

    /** Retorno de [iniciarPartidaComJogador]: contexto + resultado parcial + os outros jogos da rodada. */
    data class InicioPartidaComJogador(
        val contexto: ContextoSimulacaoMetade,
        val resultadoParcial: ResultadoPartida,
        val outrosJogos: List<JogoRodadaAoVivo>
    )

    // Bilheteria de uma partida: se o time da casa tem estádio com preços por setor
    // (time do jogador), usa a versão SETORIZADA (com elasticidade); senão, a flat (IA).
    private suspend fun calcularPublicoDaPartida(timeCasa: Time, adversario: Time?): Pair<Int, Long> {
        val nivelAdv = adversario?.nivel ?: 5
        val estadio = estadioDao.buscarPorTime(timeCasa.id)
        return if (estadio != null) {
            MotorFinanceiro.calcularPublicoSetorizado(
                timeCasa, nivelAdv, adversario,
                capArquibancada = br.com.managerfoot.data.database.entities.EstadioEntity.CAPACIDADE_ARQUIBANCADA[estadio.nivelArquibancada],
                capCadeira      = br.com.managerfoot.data.database.entities.EstadioEntity.CAPACIDADE_CADEIRA[estadio.nivelCadeira],
                capCamarote     = br.com.managerfoot.data.database.entities.EstadioEntity.CAPACIDADE_CAMAROTE[estadio.nivelCamarote],
                precoArquibancada = estadio.precoArquibancada,
                precoCadeira      = estadio.precoCadeira,
                precoCamarote     = estadio.precoCamarote
            )
        } else {
            MotorFinanceiro.calcularPublico(timeCasa, nivelAdv, adversario)
        }
    }

    suspend fun iniciarPartidaComJogador(
        campeonatoId: Int,
        rodada: Int,
        timeJogadorId: Int,
        escalacaoJogador: Escalacao
    ): InicioPartidaComJogador {
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

        // Calcula público antes de montar o resultado parcial — exibido desde o início da partida.
        // adversarioNivel = nível real do time visitante (afeta o fator de rivalidade da bilheteria).
        val timeCasaInfo = timeRepository.buscarPorId(escalacaoCasa.time.id)
        val (torcedoresParcial, receitaParcial) = if (timeCasaInfo != null) {
            calcularPublicoDaPartida(timeCasaInfo, escalacaoFora.time)
        } else {
            Pair(0, 0L)
        }

        val rngStats1H = kotlin.random.Random(partidaDoJogador.id.toLong() * 131 + golsCasa1H * 7L + golsFora1H)
        val posseCasa1H = SimuladorPartida.estimarPosseCasa(
            escalacaoCasa.titulares, escalacaoFora.titulares,
            escalacaoCasa.time.estiloJogo, escalacaoFora.time.estiloJogo,
            mandante = true, rng = rngStats1H
        )
        val resultadoParcial = ResultadoPartida(
            partidaId        = partidaDoJogador.id,
            timeCasaId       = escalacaoCasa.time.id,
            timeForaId       = escalacaoFora.time.id,
            golsCasa         = golsCasa1H,
            golsFora         = golsFora1H,
            eventos          = todosEventos1H,
            estatisticasCasa = estatisticasDeEventos(todosEventos1H, escalacaoCasa.time.id, escalacaoFora.time.id, golsCasa1H, escalacaoCasa.titulares, posseCasa1H, rngStats1H),
            estatisticasFora = estatisticasDeEventos(todosEventos1H, escalacaoFora.time.id, escalacaoCasa.time.id, golsFora1H, escalacaoFora.titulares, 100 - posseCasa1H, rngStats1H),
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

        // ── Pré-simula os OUTROS jogos da MESMA rodada para exibir "Placares ao vivo"
        // durante a partida. Vale para liga (fase == null) e para copas: as fases de
        // copa são 1 rodada por fase (jogo único) ou 2 (ida/volta), então buscarPorRodada
        // já devolve os confrontos certos. Ficam persistidos aqui (jogada=1); o avanço de
        // fase/finalize apenas LÊ os resultados (filtram !jogada → sem dupla simulação).
        // Empate em copa é decidido por pênaltis para revelar quem avança:
        //  - MATA_MATA_SIMPLES: pênaltis no empate do jogo único;
        //  - MATA_MATA_IDA_VOLTA: só a volta decide, via agregado com a ida.
        val formato = campeonatoDao.buscarPorId(campeonatoId)?.formato
        val ehCopaFase = partidaDoJogador.fase != null
        // Habilita apenas para: liga (fase == null) e copas de mata-mata cujo avanço
        // de fase lê os resultados já persistidos (SIMPLES/IDA_VOLTA). Outros formatos
        // (ex.: fase de grupos) mantêm o comportamento anterior (sem painel).
        val preSimHabilitado = !ehCopaFase ||
            formato == FormatoCampeonato.MATA_MATA_SIMPLES ||
            formato == FormatoCampeonato.MATA_MATA_IDA_VOLTA
        val demais = if (preSimHabilitado)
            partidas.filter { it.id != partidaDoJogador.id && (ehCopaFase || it.fase == null) }
        else emptyList()
        val outrosJogos = if (demais.isNotEmpty()) {
            val lista = demais.mapNotNull { p ->
                val res = try { simularPartidaInterna(p) } catch (_: Exception) { null }
                    ?: return@mapNotNull null

                // Resolução de empate em copa (para exibir o placar de pênaltis / quem passou)
                var penCasa: Int? = null
                var penFora: Int? = null
                if (ehCopaFase && res.golsCasa == res.golsFora) {
                    when (formato) {
                        FormatoCampeonato.MATA_MATA_SIMPLES,
                        FormatoCampeonato.GRUPOS_E_MATA_MATA -> {
                            simularEPersistirPenaltisIA(p, p)
                            val atualizada = partidaDao.buscarPorId(p.id)
                            penCasa = atualizada?.penaltisCasa
                            penFora = atualizada?.penaltisForaId
                        }
                        FormatoCampeonato.MATA_MATA_IDA_VOLTA -> {
                            // O confronto só se decide na volta (agregado com a ida).
                            val doConfronto = partidaDao.buscarTodasPorCampeonato(campeonatoId)
                                .filter { it.confrontoId != null && it.confrontoId == p.confrontoId }
                            val ida   = doConfronto.minByOrNull { it.rodada }
                            val volta = doConfronto.maxByOrNull { it.rodada }
                            if (ida != null && volta != null && ida.id != volta.id && p.id == volta.id) {
                                // Garante a ida simulada para computar o agregado
                                val idaResolvida = if (ida.jogada) ida
                                    else { simularPartidaInterna(ida); partidaDao.buscarPorId(ida.id) ?: ida }
                                val vencedor = MotorCampeonato.determinarVencedorTie(
                                    timeCasaIdaId = idaResolvida.timeCasaId,
                                    timeForaIdaId = idaResolvida.timeForaId,
                                    golsCasaIda   = idaResolvida.golsCasa ?: 0,
                                    golsForaIda   = idaResolvida.golsFora ?: 0,
                                    golsCasaVolta = res.golsCasa,
                                    golsForaVolta = res.golsFora
                                )
                                if (vencedor == null) {
                                    simularEPersistirPenaltisIA(idaResolvida, p)
                                    val atualizada = partidaDao.buscarPorId(p.id)
                                    penCasa = atualizada?.penaltisCasa
                                    penFora = atualizada?.penaltisForaId
                                }
                            }
                        }
                        else -> {}
                    }
                }

                val casa = timeRepository.buscarPorId(res.timeCasaId)
                val fora = timeRepository.buscarPorId(res.timeForaId)
                JogoRodadaAoVivo(
                    partidaId  = res.partidaId,
                    timeCasaId = res.timeCasaId, nomeCasa = casa?.nome ?: "?", escudoCasa = casa?.escudoRes ?: "",
                    timeForaId = res.timeForaId, nomeFora = fora?.nome ?: "?", escudoFora = fora?.escudoRes ?: "",
                    minutosGolsCasa = res.eventos.filter { it.tipo == TipoEvento.GOL && it.timeId == res.timeCasaId }.map { it.minuto }.sorted(),
                    minutosGolsFora = res.eventos.filter { it.tipo == TipoEvento.GOL && it.timeId == res.timeForaId }.map { it.minuto }.sorted(),
                    penaltisCasa = penCasa,
                    penaltisFora = penFora
                )
            }
            // Treino/descanso automático dos times da IA que jogaram agora (o finalize
            // não os alcança mais, pois já estão jogada=1).
            val idsIA = demais.flatMap { listOf(it.timeCasaId, it.timeForaId) }
                .filter { it != timeJogadorId }.toSet()
            aplicarTreinamentoAutomaticoIA(idsIA)
            lista
        } else emptyList()

        return InicioPartidaComJogador(contexto, resultadoParcial, outrosJogos)
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

        val rngStats = kotlin.random.Random(ctx.partidaDoJogadorId.toLong() * 131 + golsCasaFinal * 7L + golsForaFinal)
        val posseCasaFinal = SimuladorPartida.estimarPosseCasa(
            escalacaoCasa.titulares, escalacaoFora.titulares,
            escalacaoCasa.time.estiloJogo, escalacaoFora.time.estiloJogo,
            mandante = true, rng = rngStats
        )
        val resultadoBase = ResultadoPartida(
            partidaId        = ctx.partidaDoJogadorId,
            timeCasaId       = escalacaoCasa.time.id,
            timeForaId       = escalacaoFora.time.id,
            golsCasa         = golsCasaFinal,
            golsFora         = golsForaFinal,
            eventos          = ctx.eventosAcumulados,
            estatisticasCasa = estatisticasDeEventos(ctx.eventosAcumulados, escalacaoCasa.time.id, escalacaoFora.time.id, golsCasaFinal, escalacaoCasa.titulares, posseCasaFinal, rngStats),
            estatisticasFora = estatisticasDeEventos(ctx.eventosAcumulados, escalacaoFora.time.id, escalacaoCasa.time.id, golsForaFinal, escalacaoFora.titulares, 100 - posseCasaFinal, rngStats)
        )

        val notasPartida = persistirResultado(resultadoBase)

        // Calcula público para exibição na tela da partida.
        // adversarioNivel = nível real do time visitante para fator de rivalidade.
        val timeCasa = timeRepository.buscarPorId(resultadoBase.timeCasaId)
        val timeForaInfo = timeRepository.buscarPorId(resultadoBase.timeForaId)
        val (torcedoresCalc, receitaCalc) = if (timeCasa != null) {
            calcularPublicoDaPartida(timeCasa, timeForaInfo)
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
            // Só trata como agregado quando há DOIS jogos (ida e volta). Copas de jogo
            // único (ida == volta == mesma partida) caem nas regras específicas abaixo.
            if (todasDoConfronto.size >= 2 && volta != null && ida != null && volta.id == ctx.partidaDoJogadorId && ida.jogada) {
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

        // Copa da Inglaterra (MATA_MATA_SIMPLES): jogo único — empate decide por pênaltis
        if (!precisaPenaltis && golsCasaFinal == golsForaFinal) {
            val formato = campeonatoDao.buscarPorId(ctx.campeonatoId)?.formato
            val fasePartida = partidaDoJogador?.fase
            if (formato == FormatoCampeonato.MATA_MATA_SIMPLES && fasePartida != null) {
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

    // ── Amistosos de entressafra ─────────────────────────────────────────────
    // Container único (tipo AMISTOSO) que guarda todas as partidas de amistoso.
    // Excluído de buscarProximaPartida, classificação, artilheiros e Hall da Fama.
    suspend fun criarOuObterCampeonatoAmistosos(campIdExistente: Int, temporadaId: Int, ano: Int): Int {
        if (campIdExistente > 0 && campeonatoDao.buscarPorId(campIdExistente) != null) return campIdExistente
        return campeonatoDao.inserir(
            CampeonatoEntity(
                temporadaId  = temporadaId,
                // Nome inclui o ano p/ o Calendário agrupar/ordenar corretamente (anoDePartida).
                nome         = "Amistosos $ano",
                tipo         = TipoCampeonato.AMISTOSO,
                formato      = FormatoCampeonato.MATA_MATA_SIMPLES,
                totalRodadas = 0
            )
        ).toInt()
    }

    /**
     * Cria uma partida de amistoso (jogador × adversário, casa/fora) no container.
     * Retorna (partidaId, rodada). `ordemGlobalBase` posiciona no calendário:
     * dezembro (~450, após todos os jogos oficiais) ou janeiro (~5, antes da 1ª rodada),
     * para o amistoso aparecer/envelhecer corretamente em "Últimos resultados".
     */
    suspend fun criarAmistoso(campId: Int, timeJogadorId: Int, adversarioId: Int, ehCasa: Boolean, ordemGlobalBase: Int): Pair<Int, Int> {
        val existentes = partidaDao.buscarTodasPorCampeonato(campId)
        val rodada = (existentes.maxOfOrNull { it.rodada } ?: 0) + 1
        val casa = if (ehCasa) timeJogadorId else adversarioId
        val fora = if (ehCasa) adversarioId else timeJogadorId
        val id = partidaDao.inserir(
            PartidaEntity(
                campeonatoId = campId,
                rodada       = rodada,
                timeCasaId   = casa,
                timeForaId   = fora,
                fase         = "Amistoso",
                ordemGlobal  = ordemGlobalBase + rodada
            )
        ).toInt()
        return id to rodada
    }

    /** Remove as partidas de um container de amistosos (ao virar a temporada). */
    suspend fun limparAmistosos(campId: Int) {
        if (campId > 0) partidaDao.deletePorCampeonato(campId)
    }

    /**
     * Finaliza um amistoso: persiste placar + bilheteria (só mandante), aplica energia
     * (fadiga) e moral. NÃO mexe em tabela, artilheiros, Hall da Fama, ranking de
     * técnicos, reputação, cartões/suspensão nem avanço de rodada.
     */
    suspend fun finalizarAmistosoComJogador(
        ctx: ContextoSimulacaoMetade,
        golsCasaFinal: Int,
        golsForaFinal: Int
    ): ResultadoPartida {
        val escalacaoCasa = if (ctx.ehMandante) ctx.escalacaoJogadorOriginal else ctx.escalacaoAdversario
        val escalacaoFora = if (ctx.ehMandante) ctx.escalacaoAdversario else ctx.escalacaoJogadorOriginal

        val rngStats = kotlin.random.Random(ctx.partidaDoJogadorId.toLong() * 131 + golsCasaFinal * 7L + golsForaFinal)
        val posseCasaFinal = SimuladorPartida.estimarPosseCasa(
            escalacaoCasa.titulares, escalacaoFora.titulares,
            escalacaoCasa.time.estiloJogo, escalacaoFora.time.estiloJogo,
            mandante = true, rng = rngStats
        )
        val resultado = ResultadoPartida(
            partidaId        = ctx.partidaDoJogadorId,
            timeCasaId       = escalacaoCasa.time.id,
            timeForaId       = escalacaoFora.time.id,
            golsCasa         = golsCasaFinal,
            golsFora         = golsForaFinal,
            eventos          = ctx.eventosAcumulados,
            estatisticasCasa = estatisticasDeEventos(ctx.eventosAcumulados, escalacaoCasa.time.id, escalacaoFora.time.id, golsCasaFinal, escalacaoCasa.titulares, posseCasaFinal, rngStats),
            estatisticasFora = estatisticasDeEventos(ctx.eventosAcumulados, escalacaoFora.time.id, escalacaoCasa.time.id, golsForaFinal, escalacaoFora.titulares, 100 - posseCasaFinal, rngStats)
        )

        // Bilheteria (só o mandante arrecada)
        val timeCasa = timeRepository.buscarPorId(resultado.timeCasaId)
        val timeForaInfo = timeRepository.buscarPorId(resultado.timeForaId)
        val (torcedores, receita) = if (timeCasa != null) {
            MotorFinanceiro.calcularPublico(timeCasa, timeForaInfo?.nivel ?: 5, timeForaInfo)
        } else Pair(0, 0L)
        partidaDao.registrarResultado(resultado.partidaId, golsCasaFinal, golsForaFinal, torcedores, receita)
        if (receita > 0) timeRepository.creditarSaldo(resultado.timeCasaId, receita)

        // Persiste os eventos (gol/assistência/participação/etc.) para que o amistoso
        // conte em artilharia, assistências e no retrospecto de confrontos. NÃO afeta a
        // artilharia das ligas (query é por campeonatoId) — só aparece no container Amistosos.
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

        val notas = calcularNotasJogadores(resultado)

        // Energia: titulares perdem, banco recupera
        val participantesIds = resultado.eventos
            .filter { it.tipo == TipoEvento.PARTICIPOU || it.tipo == TipoEvento.SUBSTITUICAO_ENTRA }
            .map { it.jogadorId }.toSet()
        val timesEnvolvidos = setOf(resultado.timeCasaId, resultado.timeForaId)
        jogadorRepository.atualizarFadigaAposPartida(participantesIds, timesEnvolvidos)
        // Moral: recomputa a partir das últimas partidas (o amistoso entra por ter OG alto)
        timesEnvolvidos.forEach { jogadorRepository.atualizarMoraleTime(it) }

        return resultado.copy(notasJogadores = notas, torcedores = torcedores, receitaPartida = receita)
    }

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
        val campeonatoPremierId: Int,     // Premier League inglesa (divisao 11)
        val campeonatoChampionshipId: Int, // EFL Championship inglesa (divisao 12)
        val campeonatoLaLigaId: Int,       // La Liga espanhola (divisao 13)
        val campeonatoSerieAId: Int,       // Serie A italiana (divisao 14)
        val copaId: Int,
        val copaArgId: Int,               // Copa Argentina
        val copaInglaterraId: Int,        // Copa da Inglaterra
        val copaEspanholaId: Int,         // Copa Espanhola (Copa do Rei)
        val copaItalianaId: Int,          // Copa Italiana (Coppa Italia)
        val championsId: Int,             // UEFA Champions League
        val europaLeagueId: Int,          // UEFA Europa League
        val supercopaId: Int,
        val supercopaInglaterraId: Int,   // Supercopa da Inglaterra
        val supercopaEspanholaId: Int,    // Supercopa da Espanha
        val supercopaItalianaId: Int,     // Supercopa da Itália
        val temporadaId: Int,
        val ano: Int,
        val resultadoMeta: String? = null // Mensagem "Meta cumprida/falhada" para exibir em Snackbar
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
        campeonatoPremierId: Int = -1,      // Premier League inglesa (divisao 11)
        campeonatoChampionshipId: Int = -1, // EFL Championship inglesa (divisao 12)
        campeonatoLaLigaId: Int = -1,       // La Liga espanhola (divisao 13)
        campeonatoSerieAId: Int = -1,       // Serie A italiana (divisao 14)
        championsId: Int = -1,              // UEFA Champions League da temporada que encerra
        europaLeagueId: Int = -1,           // UEFA Europa League da temporada que encerra
        temporadaId: Int,
        ano: Int,
        timeJogadorId: Int = -1
    ): NovaTemporadaInfo {
        // ── Série A ──────────────────────────────────────────────
        val participantesA = campeonatoDao.buscarIdsParticipantes(campeonatoAId)
        // ── Helper local: encurtamento que fixa o ano da temporada ───
        suspend fun registrarHallDaFama(campId: Int, div: Int) =
            registrarHallDaFamaPontosCorridos(campId, div, ano)

        registrarHallDaFama(campeonatoAId, 1)
        registrarHallDaFama(campeonatoBId, 2)
        registrarHallDaFama(campeonatoCId, 3)
        registrarHallDaFama(campeonatoDId, 4)
        // ── Garante conclusão dos torneios estrangeiros ───────────────────────
        // Quando o usuário controla um time brasileiro os torneios Argentino Clausura
        // e Uruguaio Clausura/Intermediário podem não ter terminado. Forçamos a
        // simulação completa antes de registrar o Hall da Fama e fazer promoções.
        if (campeonatoArgAId > 0 && campeonatoDao.buscarPorId(campeonatoArgAId)?.encerrado == false) {
            simularProximaFaseArgentinaSeNecessario(campeonatoArgAId, -1, ano, temporadaId)
        }
        if (campeonatoArgClausuraId > 0 && campeonatoDao.buscarPorId(campeonatoArgClausuraId)?.encerrado == false) {
            simularProximaFaseArgentinaSeNecessario(campeonatoArgClausuraId, -1, ano, temporadaId)
        }
        // Uruguai: simula cadeia Apertura → Intermediário → Clausura em loop (1 rodada por iteração)
        var itrUru = 100
        while (itrUru-- > 0) {
            val algumUruAberto = listOf(campeonatoUruAperturaId, campeonatoUruIntermedId, campeonatoUruClausuraId)
                .any { it > 0 && campeonatoDao.buscarPorId(it)?.encerrado == false }
            if (!algumUruAberto) break
            simularUruguaiSeNecessario(
                aperturaId    = campeonatoUruAperturaId,
                clausuraId    = campeonatoUruClausuraId,
                intermedidId  = campeonatoUruIntermedId,
                competBId     = campeonatoUruBCompetId,
                timeJogadorId = -1,
                anoAtual      = ano,
                temporadaId   = temporadaId
            )
        }
        // Argentine Segunda Div. e Uruguayan Apertura/Segunda Div./Competencia
        // são PONTOS_CORRIDOS — registra via tabela de classificação normalmente.
        registrarHallDaFama(campeonatoArgBId, 10)  // 10 = Segunda Div. Argentina
        // Uruguai: Apertura (11), Segunda Div. (15) e Competencia B (16)
        registrarHallDaFama(campeonatoUruAperturaId, 11)  // 11 = Apertura Uruguaio (PONTOS_CORRIDOS)
        registrarHallDaFama(campeonatoUruBId, 15)         // 15 = Segunda Div. Uruguaia
        registrarHallDaFama(campeonatoUruBCompetId, 16)   // 16 = Competencia URU B
        registrarHallDaFama(campeonatoPremierId, 20)      // 20 = Premier League (PONTOS_CORRIDOS)
        registrarHallDaFama(campeonatoChampionshipId, 21) // 21 = EFL Championship (PONTOS_CORRIDOS)
        registrarHallDaFama(campeonatoLaLigaId, 22)       // 22 = La Liga (PONTOS_CORRIDOS)
        registrarHallDaFama(campeonatoSerieAId, 24)       // 24 = Serie A italiana (PONTOS_CORRIDOS)

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
        aplicarBonusTitulo(campeonatoPremierId, 0.05) // Premier League +5%
        aplicarBonusTitulo(campeonatoChampionshipId, 0.03) // EFL Championship +3%
        aplicarBonusTitulo(campeonatoLaLigaId, 0.05)  // La Liga +5%
        aplicarBonusTitulo(campeonatoSerieAId, 0.05)  // Serie A +5%

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
        premiarSeJogador(campeonatoPremierId, 20_000_000_00L)   // Premier: Cam R$ 20M, Vice R$ 10M
        premiarSeJogador(campeonatoChampionshipId, 8_000_000_00L) // Championship: Cam R$ 8M, Vice R$ 4M
        premiarSeJogador(campeonatoLaLigaId, 20_000_000_00L)     // La Liga: Cam R$ 20M, Vice R$ 10M
        premiarSeJogador(campeonatoSerieAId, 20_000_000_00L)     // Serie A: Cam R$ 20M, Vice R$ 10M

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

        // ── Reputação: penalidades de rebaixamento e bônus de promoção (Brasil) ──
        aplicarPenalidadeRebaixamento(rebaixadosA, 0.06)  // A → B: -6%
        aplicarPenalidadeRebaixamento(rebaixadosB, 0.05)  // B → C: -5%
        aplicarPenalidadeRebaixamento(rebaixadosC, 0.04)  // C → D: -4%
        aplicarBonusPromocao(promovidosB, 0.04)           // B → A: +4%
        aplicarBonusPromocao(promovidosC, 0.03)           // C → B: +3%
        aplicarBonusPromocao(promovidosD, 0.02)           // D → C: +2%

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
               campeonatoUruAperturaId, campeonatoUruClausuraId, campeonatoUruIntermedId, campeonatoUruBId,
               campeonatoPremierId, campeonatoChampionshipId, campeonatoLaLigaId, campeonatoSerieAId)
            .filter { it > 0 }.forEach { campeonatoDao.encerrar(it) }

        jogadorRepository.processarDesenvolvimentoAnual(timeJogadorId)
        jogadorRepository.processarExpiracaoContratos(timeJogadorId)
        // Repõe os elencos da IA que ficaram abaixo do piso (contratos expirados
        // sem reposição + vendas) — promove juniores e gera reforços jovens.
        jogadorRepository.reporElencosIA(timeJogadorId)

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
        atualizarRankingGeral(campeonatoPremierId)
        atualizarRankingGeral(campeonatoChampionshipId)
        atualizarRankingGeral(campeonatoLaLigaId)
        atualizarRankingGeral(campeonatoSerieAId)

        // ── Rankings por colocação (CONMEBOL + RNC da CBF) ────────────
        registrarRankingsPorColocacaoDaTemporada(
            campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, temporadaId, ano
        )

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
                titulosNacionais = entry.titulosNacionais + 1
            ))
            // Ranking CONMEBOL: dois campeões no ano (Apertura e Clausura) dividem os 50 pontos
            registrarPontosConmebol(
                timeId = campeaoId,
                ano = ano,
                competicao = CompeticaoConmebol.LIGA_NACIONAL,
                pontos = RankingConmebol.PONTOS_LIGA_NACIONAL / 2.0
            )
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
            val ordenadosPontos = pontosCombinados.entries.sortedByDescending { it.value }
            val campGeral = ordenadosPontos.getOrNull(0)?.key
            val viceGeral = ordenadosPontos.getOrNull(1)?.key
            if (campGeral != null) {
                val timeEnt = timeRepository.buscarEntityPorId(campGeral)
                val rankCG = rankingGeralDao.buscarPorTime(campGeral)
                    ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                        timeId = campGeral, nomeTime = timeEnt?.nome ?: "", escudoRes = timeEnt?.escudoRes ?: "", divisaoAtual = timeEnt?.divisao ?: 5
                    )
                // Os 50 pontos CONMEBOL da liga argentina já foram divididos entre os
                // campeões do Apertura e do Clausura; o campeão geral não pontua de novo.
                rankingGeralDao.inserirOuAtualizar(rankCG.copy(
                    titulosNacionais = rankCG.titulosNacionais + 1
                ))

                // ── Hall da Fama: Campeonato Argentino geral (divisão 17) ──
                val viceEnt = if (viceGeral != null) timeRepository.buscarEntityPorId(viceGeral) else null
                val idsArg = listOfNotNull(
                    campeonatoArgAId.takeIf { it > 0 },
                    campeonatoArgClausuraId.takeIf { it > 0 }
                )
                val artilheiro = if (idsArg.isNotEmpty()) partidaDao.buscarArtilheiroTop1Multi(idsArg) else null
                val assistente = if (idsArg.isNotEmpty()) partidaDao.buscarAssisteTop1Multi(idsArg) else null
                inserirNoHallDaFamaComTecnico(HallDaFamaEntity(
                    ano                 = ano,
                    nomeCampeonato      = "Campeonato Argentino $ano",
                    campeaoTimeId       = campGeral,
                    campeaoNome         = timeEnt?.nome ?: "",
                    campeaoEscudo       = timeEnt?.escudoRes ?: "",
                    viceTimeId          = viceGeral ?: -1,
                    viceNome            = viceEnt?.nome ?: "",
                    viceEscudo          = viceEnt?.escudoRes ?: "",
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
                    divisao             = 17
                ))
            }
        }

        // ── Incrementa temporadasJogadas para la Primera División Argentina ──
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

        // ── Reputação: rebaixamento e promoção (Argentina) ──
        aplicarPenalidadeRebaixamento(rebaixadosArgA, 0.05)  // Primera → Segunda: -5%
        aplicarBonusPromocao(promovidosArgB, 0.035)          // Segunda → Primera: +3.5%

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

        // ── Promoção / Rebaixamento e renovação — Uruguai ────────────
        // Extraído em helper para manter este método abaixo do limite de 64KB de bytecode.
        val renovUru = renovarTorneiosUruguaios(
            campeonatoUruAperturaId, campeonatoUruClausuraId, campeonatoUruIntermedId,
            campeonatoUruBId, novoTemporadaId, novoAno
        )
        val rebaixadosUruA    = renovUru.rebaixadosPrimera
        val novoUruAperturaId = renovUru.novoAperturaId
        val novoUruIntermedId = renovUru.novoIntermedId
        val novoUruClausuraId = renovUru.novoClausuraId
        val novoUruBId        = renovUru.novoSegundaId
        val novoUruBCompetId  = renovUru.novoCompetenciaId

        // ── Renovação das ligas europeias (Premier↔Championship + La Liga) ──
        // Extraído em helper para manter este método abaixo do limite de 64KB de bytecode.
        val renovEuropa = renovarLigasEuropeiasPontosCorridos(
            campeonatoPremierId, campeonatoChampionshipId, campeonatoLaLigaId, campeonatoSerieAId,
            novoTemporadaId, novoAno
        )
        val novoPremierId      = renovEuropa.novoPremierId
        val novoChampionshipId = renovEuropa.novoChampionshipId
        val novoLaLigaId       = renovEuropa.novoLaLigaId
        val novoSerieAId       = renovEuropa.novoSerieAId

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

        // Cria Copa da Inglaterra para a próxima temporada (jogo único, com o novo
        // conjunto Premier + Championship após promoção/rebaixamento).
        val participantesCopaIng = determinarParticipantesCopaInglaterra(novoPremierId, novoChampionshipId)
        val novoCopaInglaterraId = if (participantesCopaIng.size >= 2) criarCopaInglaterra(
            CampeonatoEntity(
                temporadaId  = novoTemporadaId,
                nome         = "Copa da Inglaterra $novoAno",
                tipo         = TipoCampeonato.COPA_NACIONAL,
                formato      = FormatoCampeonato.MATA_MATA_SIMPLES,
                totalRodadas = 5,
                pais         = "Inglaterra"
            ),
            participantesCopaIng
        ) else -1

        // Cria Copa Espanhola (Copa do Rei) e Copa Italiana (Coppa Italia) — 16 melhores,
        // ida/volta a partir das Oitavas (modelo Copa do Brasil). Extraído em helper para
        // não estourar o limite de bytecode do método de rollover.
        val (novoCopaEspanholaId, novoCopaItalianaId) =
            criarCopasEspanholaItaliana(novoTemporadaId, novoAno, novoLaLigaId, novoSerieAId)

        // Conclui a Champions que encerra e cria a da próxima temporada.
        val novoChampionsId = encerrarECriarChampions(
            championsId, ano, temporadaId, novoTemporadaId, novoAno
        )

        // E depois a Europa League, nesta ordem: a lista de acesso dela exclui
        // quem já está na Champions nova, então precisa dela criada antes.
        val novoEuropaLeagueId = encerrarECriarEuropa(
            europaLeagueId, novoChampionsId, ano, temporadaId, novoTemporadaId, novoAno
        )

        // Cria as Supercopas (Brasil/Inglaterra/Espanha/Itália) para a próxima temporada.
        // Extraído em helper para manter este método sob o limite de bytecode da JVM.
        val supers = criarSupercopasDaTemporada(ano, novoAno, novoTemporadaId)
        val novoSupercopaId    = supers.brasil
        val novoSupercopaIngId = supers.inglaterra
        val novoSupercopaEspId = supers.espanha
        val novoSupercopaItaId = supers.italia

        // Troféu dos Campeões (ARG) — disputado no MESMO ano (após Apertura+Clausura).
        // Auto-simulado pois ambos os campeões já estão registrados no Hall da Fama.
        // Se o mesmo time venceu Apertura e Clausura, o troféu não é criado.
        try {
            criarESimularTrofeuCampeoes(ano = ano, temporadaId = temporadaId, timeJogadorId = timeJogadorId)
        } catch (_: Exception) { }

        // ── Decay anual: times sem nenhum título no ano perdem reputação ──
        // Times que conquistaram qualquer título no Hall da Fama (divisao 1-18)
        // do ano que está encerrando ficam imunes ao decay. Os demais perdem 1%.
        // Aplica decay extra a times no fundo da tabela (mau desempenho consistente).
        try {
            aplicarDecayAnualReputacao(ano)
        } catch (_: Exception) { }

        // ── Recalcula `nivel` de TODOS os times a partir da reputação atualizada ──
        // Após bônus de título, penalidades de rebaixamento, bônus de promoção e decay.
        try {
            recalcularNiveisGlobais()
        } catch (_: Exception) { }

        // ── Demissão automática por mau desempenho ──────────────
        // Antes de resetar os stats da temporada, usa o aproveitamento
        // do ano que encerrou para demitir técnicos: rebaixados (≈80%)
        // e times com aproveitamento <30% (≈40%). O técnico do usuário
        // está protegido. Acontece ANTES de preencherVagas para que os
        // recém-demitidos entrem no pool de livres.
        try {
            val rebaixadosTotais = buildSet<Int> {
                addAll(rebaixadosA); addAll(rebaixadosB); addAll(rebaixadosC)
                if (campeonatoArgAId > 0) addAll(rebaixadosArgA)
                if (campeonatoUruAperturaId > 0) addAll(rebaixadosUruA)
            }
            tecnicoRepository.demitirPorDesempenho(rebaixadosTotais, ano)
        } catch (_: Exception) { }

        // ── Resetar stats da temporada dos técnicos ──
        try {
            tecnicoRepository.resetarStatsTemporada()
        } catch (_: Exception) { }

        // ── IA: preencher times sem técnico (vagos) com técnicos livres ──
        // Garante que após o usuário ter "sequestrado" o cargo do técnico
        // anterior do seu time, e qualquer outro time que tenha ficado sem
        // comando, recebe um técnico livre baseado em reputação.
        try {
            tecnicoRepository.preencherVagasComTecnicosLivres(novoAno)
        } catch (_: Exception) { }

        // ── Mercado de técnicos de fim de temporada ──
        // Depois de demitir os fracos e preencher as vagas, os clubes maiores
        // podem contratar técnicos livres de destaque (efeito cascata realista).
        try {
            tecnicoRepository.movimentarMercadoTecnicosFimTemporada(novoAno)
        } catch (_: Exception) { }

        // ── Verifica cumprimento da meta de temporada do jogador ──
        // Baseado na posição final do time no seu campeonato principal (por divisão).
        // Aplica bônus/penalidade de reputação ao TÉCNICO e devolve uma mensagem
        // amigável ao usuário para exibição em Snackbar.
        val resultadoMeta: String? = if (timeJogadorId > 0) {
            verificarMetaTemporadaJogador(
                timeJogadorId            = timeJogadorId,
                campeonatoAId            = campeonatoAId,
                campeonatoBId            = campeonatoBId,
                campeonatoCId            = campeonatoCId,
                campeonatoDId            = campeonatoDId,
                campeonatoPremierId      = campeonatoPremierId,
                campeonatoChampionshipId = campeonatoChampionshipId,
                campeonatoLaLigaId       = campeonatoLaLigaId,
                campeonatoSerieAId       = campeonatoSerieAId
            )
        } else null

        return NovaTemporadaInfo(novoCampeonatoAId, novoCampeonatoBId, novoCampeonatoCId, novoCampeonatoDId, novoAperturaId, novoArgBId, novoClausuraId, novoUruAperturaId, novoUruBId, novoUruClausuraId, novoUruIntermedId, novoUruBCompetId, novoPremierId, novoChampionshipId, novoLaLigaId, novoSerieAId, novoCopaId, novoCopaArgId, novoCopaInglaterraId, novoCopaEspanholaId, novoCopaItalianaId, novoChampionsId, novoEuropaLeagueId, novoSupercopaId, novoSupercopaIngId, novoSupercopaEspId, novoSupercopaItaId, novoTemporadaId, novoAno, resultadoMeta)
    }

    // ── Reputação por mudança de divisão ────────────────────────
    // Eram funções locais de [encerrarTemporadaComHallDaFama]; viraram membros
    // quando a renovação dos torneios uruguaios saiu daquele método e passou a
    // precisar delas — uma função local não é visível de fora do seu corpo.

    /** Penalidade de rebaixamento (multiplicativa, mínima 0,5pt).
     *  Rebaixamento é o gatilho mais forte de queda de reputação/nível. */
    private suspend fun aplicarPenalidadeRebaixamento(timeIds: List<Int>, dropPercent: Double) {
        for (id in timeIds) {
            val t = timeRepository.buscarPorId(id) ?: continue
            val drop = (t.reputacao * dropPercent).toFloat().coerceAtLeast(0.5f)
            timeRepository.atualizarReputacao(id, t.reputacao - drop)
        }
    }

    /** Bônus de promoção: independente do título, subir de divisão sempre dá
     *  ganho de prestígio. */
    private suspend fun aplicarBonusPromocao(timeIds: List<Int>, gainPercent: Double) {
        for (id in timeIds) {
            val t = timeRepository.buscarPorId(id) ?: continue
            val gain = (t.reputacao * gainPercent).toFloat().coerceAtLeast(0.5f)
            timeRepository.atualizarReputacao(id, t.reputacao + gain)
        }
    }

    private data class RenovacaoUruguaia(
        /** Rebaixados da Primera — reaproveitados nas demissões por desempenho. */
        val rebaixadosPrimera: List<Int>,
        val novoAperturaId: Int,
        val novoIntermedId: Int,
        val novoClausuraId: Int,
        val novoSegundaId: Int,
        val novoCompetenciaId: Int
    )

    /**
     * Aplica promoção/rebaixamento da Primera ↔ Segunda División Uruguaia (2 vagas
     * de cada lado, pela Tabela Anual) e renova os torneios uruguaios da próxima
     * temporada: Apertura, Intermediário, Clausura, Segunda e Competencia.
     *
     * Extraído de [encerrarTemporadaComHallDaFama] para manter aquele método
     * abaixo do limite de 64KB de bytecode da JVM, como já se fez com a renovação
     * das ligas europeias: num método só não cabe tudo o que o fim de temporada faz.
     */
    private suspend fun renovarTorneiosUruguaios(
        campeonatoUruAperturaId: Int,
        campeonatoUruClausuraId: Int,
        campeonatoUruIntermedId: Int,
        campeonatoUruBId: Int,
        novoTemporadaId: Int,
        novoAno: Int
    ): RenovacaoUruguaia {
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

        // ── Reputação: rebaixamento e promoção (Uruguai) ──
        aplicarPenalidadeRebaixamento(rebaixadosUruA, 0.04)  // Primera URU → Segunda: -4%
        aplicarBonusPromocao(promovidosUruB, 0.03)           // Segunda URU → Primera: +3%

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

        return RenovacaoUruguaia(
            rebaixadosPrimera = rebaixadosUruA,
            novoAperturaId    = novoUruAperturaId,
            novoIntermedId    = novoUruIntermedId,
            novoClausuraId    = novoUruClausuraId,
            novoSegundaId     = novoUruBId,
            novoCompetenciaId = novoUruBCompetId
        )
    }

    private data class RenovacaoEuropeia(
        val novoPremierId: Int,
        val novoChampionshipId: Int,
        val novoLaLigaId: Int,
        val novoSerieAId: Int
    )

    /**
     * Aplica promoção/rebaixamento Premier↔Championship (3 vagas cada lado) e
     * renova as três ligas europeias pontos-corridos (Premier, Championship, La Liga)
     * para a nova temporada. La Liga não tem 2ª divisão espanhola no MVP, então
     * apenas renova com os mesmos participantes.
     *
     * Extraído de [encerrarTemporadaComHallDaFama] para manter o método principal
     * abaixo do limite de 64KB de bytecode da JVM.
     */
    private suspend fun renovarLigasEuropeiasPontosCorridos(
        campeonatoPremierId: Int,
        campeonatoChampionshipId: Int,
        campeonatoLaLigaId: Int,
        campeonatoSerieAId: Int,
        novoTemporadaId: Int,
        novoAno: Int
    ): RenovacaoEuropeia {
        // Promoção / rebaixamento (Premier ↔ Championship)
        val dfPremier = if (campeonatoPremierId > 0) {
            val entity = campeonatoDao.buscarPorId(campeonatoPremierId)
            val tabela = classificacaoDao.buscarTabelaOrdenada(campeonatoPremierId)
            entity?.let { MotorCampeonato.calcularDesfecho(it, tabela) }
        } else null
        val dfChampionship = if (campeonatoChampionshipId > 0) {
            val entity = campeonatoDao.buscarPorId(campeonatoChampionshipId)
            val tabela = classificacaoDao.buscarTabelaOrdenada(campeonatoChampionshipId)
            entity?.let { MotorCampeonato.calcularDesfecho(it, tabela) }
        } else null

        val rebaixadosPremier      = (dfPremier?.rebaixados ?: emptyList()).takeLast(3)
        val promovidosChampionship = (dfChampionship?.promovidos ?: emptyList()).take(3)

        // Atualiza divisão
        rebaixadosPremier.forEach { id ->
            timeRepository.buscarEntityPorId(id)?.let { timeRepository.atualizar(it.copy(divisao = 12)) }
        }
        promovidosChampionship.forEach { id ->
            timeRepository.buscarEntityPorId(id)?.let { timeRepository.atualizar(it.copy(divisao = 11)) }
        }

        // Reputação: rebaixados perdem 6%, promovidos ganham 4% (mínimo 0.5 pt em cada)
        for (id in rebaixadosPremier) {
            val t = timeRepository.buscarPorId(id) ?: continue
            val drop = (t.reputacao * 0.06).toFloat().coerceAtLeast(0.5f)
            timeRepository.atualizarReputacao(id, t.reputacao - drop)
        }
        for (id in promovidosChampionship) {
            val t = timeRepository.buscarPorId(id) ?: continue
            val gain = (t.reputacao * 0.04).toFloat().coerceAtLeast(0.5f)
            timeRepository.atualizarReputacao(id, t.reputacao + gain)
        }

        // Renova Premier League
        val partPremAtuais = if (campeonatoPremierId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoPremierId) else emptyList()
        val novosPremier = (partPremAtuais - rebaixadosPremier.toSet()) + promovidosChampionship
        val novoPremierId = if (novosPremier.isNotEmpty()) criarCampeonato(
            CampeonatoEntity(temporadaId = novoTemporadaId,
                nome = "Premier League $novoAno/${(novoAno + 1) % 100}",
                tipo = TipoCampeonato.NACIONAL_DIVISAO1, formato = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = (novosPremier.size - 1) * 2, pais = "Inglaterra"), novosPremier) else -1

        // Renova EFL Championship
        val partChampAtuais = if (campeonatoChampionshipId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoChampionshipId) else emptyList()
        val novosChampionship = (partChampAtuais - promovidosChampionship.toSet()) + rebaixadosPremier
        val novoChampionshipId = if (novosChampionship.isNotEmpty()) criarCampeonato(
            CampeonatoEntity(temporadaId = novoTemporadaId,
                nome = "EFL Championship $novoAno/${(novoAno + 1) % 100}",
                tipo = TipoCampeonato.NACIONAL_DIVISAO2, formato = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = (novosChampionship.size - 1) * 2, pais = "Inglaterra"), novosChampionship) else -1

        // Renova La Liga (sem 2ª divisão espanhola no MVP)
        val partLaLigaAtuais = if (campeonatoLaLigaId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoLaLigaId) else emptyList()
        val novoLaLigaId = if (partLaLigaAtuais.isNotEmpty()) criarCampeonato(
            CampeonatoEntity(temporadaId = novoTemporadaId,
                nome = "La Liga $novoAno/${(novoAno + 1) % 100}",
                tipo = TipoCampeonato.NACIONAL_DIVISAO1, formato = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = (partLaLigaAtuais.size - 1) * 2, pais = "Espanha"), partLaLigaAtuais) else -1

        // Renova Serie A (sem 2ª divisão italiana no MVP)
        val partSerieAAtuais = if (campeonatoSerieAId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoSerieAId) else emptyList()
        val novoSerieAId = if (partSerieAAtuais.isNotEmpty()) criarCampeonato(
            CampeonatoEntity(temporadaId = novoTemporadaId,
                nome = "Serie A $novoAno/${(novoAno + 1) % 100}",
                tipo = TipoCampeonato.NACIONAL_DIVISAO1, formato = FormatoCampeonato.PONTOS_CORRIDOS,
                totalRodadas = (partSerieAAtuais.size - 1) * 2, pais = "Italia"), partSerieAAtuais) else -1

        return RenovacaoEuropeia(novoPremierId, novoChampionshipId, novoLaLigaId, novoSerieAId)
    }

    /**
     * Verifica se o time do jogador cumpriu a meta de temporada (calculada em
     * [MotorFinanceiro.calcularMetaTemporada]) com base na posição final no
     * campeonato principal da sua divisão. Aplica bônus/penalidade de reputação
     * ao técnico e retorna uma mensagem descritiva.
     */
    private suspend fun verificarMetaTemporadaJogador(
        timeJogadorId: Int,
        campeonatoAId: Int,
        campeonatoBId: Int,
        campeonatoCId: Int,
        campeonatoDId: Int,
        campeonatoPremierId: Int,
        campeonatoChampionshipId: Int,
        campeonatoLaLigaId: Int,
        campeonatoSerieAId: Int
    ): String? {
        val time = timeRepository.buscarPorId(timeJogadorId) ?: return null
        val meta = br.com.managerfoot.domain.engine.MotorFinanceiro.calcularMetaTemporada(time)
        // Campeonato principal por divisão
        val campId = when (time.divisao) {
            1  -> campeonatoAId
            2  -> campeonatoBId
            3  -> campeonatoCId
            4  -> campeonatoDId
            11 -> campeonatoPremierId
            12 -> campeonatoChampionshipId
            13 -> campeonatoLaLigaId
            14 -> campeonatoSerieAId
            else -> return null
        }
        if (campId <= 0) return null

        val tabela = classificacaoDao.buscarTabelaOrdenada(campId)
        val posicao = tabela.indexOfFirst { it.timeId == timeJogadorId }.let { if (it < 0) return null else it + 1 }
        val total = tabela.size

        // Regras de cumprimento por meta:
        //   TITULO             → posição == 1
        //   G4                 → posição ≤ 4
        //   ACESSO             → posição ≤ 4 (divisões inferiores promovem os 4 primeiros)
        //   MEIO_TABELA        → posição entre 5 e (total - zonaRebaixamento)
        //   FUGIR_REBAIXAMENTO → não terminou nas últimas 4
        //   SOBREVIVER         → não terminou nas últimas 4
        val zonaRebaixamento = 4
        val cumpriu = when (meta) {
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.TITULO ->
                posicao == 1
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.G4,
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.ACESSO ->
                posicao <= 4
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.TOP_10 ->
                posicao <= 10
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.MEIO_TABELA ->
                posicao in 5..(total - zonaRebaixamento)
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.FUGIR_REBAIXAMENTO,
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.SOBREVIVER ->
                posicao <= (total - zonaRebaixamento)
        }

        // Bônus/penalidade ao técnico do jogador (reputação).
        // Metas mais ambiciosas geram deltas maiores.
        val delta = when (meta) {
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.TITULO             -> if (cumpriu) +6f else -4f
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.G4                 -> if (cumpriu) +4f else -3f
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.ACESSO             -> if (cumpriu) +5f else -3f
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.TOP_10             -> if (cumpriu) +3f else -2f
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.MEIO_TABELA        -> if (cumpriu) +2f else -2f
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.FUGIR_REBAIXAMENTO -> if (cumpriu) +3f else -5f
            br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada.SOBREVIVER         -> if (cumpriu) +2f else -3f
        }
        try {
            tecnicoRepository.ajustarReputacaoTecnicoDoTime(timeJogadorId, delta)
        } catch (_: Exception) { /* técnico pode não existir se save antigo */ }

        val icone = if (cumpriu) "✅" else "❌"
        val verbo = if (cumpriu) "CUMPRIDA" else "FALHADA"
        val sinal = if (delta >= 0) "+" else ""
        return "$icone Meta \"${meta.titulo}\" $verbo — encerrou em ${posicao}º de $total (rep. técnico $sinal${delta.toInt()})"
    }

    /**
     * Aplica decay anual de reputação aos times que não conquistaram nenhum
     * título no ano informado. Times que conquistaram QUALQUER título (divisão
     * 1-18 do Hall da Fama) ficam imunes — só os "perdedores" perdem prestígio.
     *
     * Decay base: -1.5% da reputação atual (mínimo 0.3pt).
     * Decay extra para times com baixa força de elenco / fora da elite (divisão >= 3): -0.5% extra.
     */
    suspend fun aplicarDecayAnualReputacao(ano: Int) {
        // Coleta IDs dos times campeões em qualquer divisão do Hall da Fama daquele ano
        val campeoes = mutableSetOf<Int>()
        // divisão 1..18 abrange Brasil A/B/C/D + Copa BR + Supercopa + ARG + URU + Trofeu Campeoes
        for (div in 1..18) {
            val champ = hallDaFamaDao.buscarCampeaoPorAnoEDivisao(ano, div)
            if (champ != null) campeoes.add(champ.campeaoTimeId)
        }
        // Champions League (divisão 30): o maior título europeu também imuniza.
        hallDaFamaDao.buscarCampeaoPorAnoEDivisao(ano, DIVISAO_HALL_CHAMPIONS)
            ?.let { campeoes.add(it.campeaoTimeId) }

        val todos = timeRepository.observeTodos().first()
        for (time in todos) {
            if (time.id in campeoes) continue  // imune por ter conquistado algo
            val basePct = 0.015  // 1.5%
            // Penalidade extra para times de divisão menor (mau desempenho persistente)
            val extraPct = if (time.divisao >= 3) 0.005 else 0.0
            val total = basePct + extraPct
            val drop = (time.reputacao * total).toFloat().coerceAtLeast(0.3f)
            timeRepository.atualizarReputacao(time.id, time.reputacao - drop)
        }
    }

    /**
     * Recalcula o `nivel` (1..10) de todos os times a partir da reputação atual.
     * Chamado ao final de [encerrarTemporadaComHallDaFama] depois de todos os
     * ajustes de reputação (títulos, promoções, rebaixamentos, decay) terem
     * sido aplicados — assim o nível reflete o estado consolidado da temporada.
     */
    suspend fun recalcularNiveisGlobais() {
        val todos = timeRepository.observeTodos().first()
        for (time in todos) {
            val novoNivel = MotorFinanceiro.nivelDerivadoDeReputacao(time.reputacao)
            if (time.nivel != novoNivel) {
                val entity = timeRepository.buscarEntityPorId(time.id) ?: continue
                timeRepository.atualizar(entity.copy(nivel = novoNivel))
            }
        }
    }

    fun observarHallDaFama(): Flow<List<HallDaFamaEntity>> = hallDaFamaDao.observeTodos()

    /**
     * Insere uma entrada no Hall da Fama, automaticamente:
     *  - Buscando o técnico atual do time campeão
     *  - Preenchendo tecnicoId/tecnicoNome no HallDaFamaEntity
     *  - Registrando o título nas estatísticas do técnico (acrescenta +1 título
     *    e bônus de reputação) — para que o ranking e histórico de técnicos
     *    fique automaticamente sincronizado
     */
    private suspend fun inserirNoHallDaFamaComTecnico(entity: HallDaFamaEntity) {
        val tecnico = try {
            tecnicoRepository.buscarPorTime(entity.campeaoTimeId)
        } catch (_: Exception) { null }
        val enriched = entity.copy(
            tecnicoId = tecnico?.id ?: -1,
            tecnicoNome = tecnico?.nome ?: ""
        )
        hallDaFamaDao.inserir(enriched)
        if (tecnico != null) {
            try {
                tecnicoRepository.registrarTitulo(entity.campeaoTimeId)
            } catch (_: Exception) { }
        }
    }

    /**
     * Registra Hall da Fama para campeonatos de pontos corridos (líder da
     * tabela é o campeão). Idempotente — pula se já existe entrada para o
     * mesmo `ano + divisao`. Compartilhado entre o fim de temporada (Série
     * A/B/C/D, Apertura, Segunda Div., etc.) e o registro do Clausura
     * uruguaio assim que sua fase regular termina (antes do playoff).
     */
    private suspend fun registrarHallDaFamaPontosCorridos(campId: Int, div: Int, ano: Int) {
        if (campId <= 0) return
        if (hallDaFamaDao.buscarCampeaoPorAnoEDivisao(ano, div) != null) return
        val tabTop2    = classificacaoDao.buscarTop2(campId)
        val campeao    = tabTop2.getOrNull(0) ?: return
        val vice       = tabTop2.getOrNull(1)
        val artilheiro = partidaDao.buscarArtilheiroTop1(campId)
        val assistente = partidaDao.buscarAssisteTop1(campId)
        val campEntity = campeonatoDao.buscarPorId(campId)
        val nomeSerie  = when (div) {
            1 -> "A"; 2 -> "B"; 3 -> "C"; 4 -> "D"
            5 -> "Copa"; 6 -> "Supercopa"
            7 -> "Apertura"; 8 -> "Clausura"
            10 -> "Segunda Div. Argentina"
            11 -> "Apertura Uruguaio"; 13 -> "Clausura Uruguaio"
            15 -> "Segunda Div. Uruguaia"; 16 -> "Competencia URU B"
            17 -> "Campeonato Argentino"
            20 -> "Premier League"; 21 -> "EFL Championship"; 22 -> "La Liga"; 24 -> "Serie A"
            else -> "D"
        }
        inserirNoHallDaFamaComTecnico(HallDaFamaEntity(
            ano                  = ano,
            nomeCampeonato       = campEntity?.nome ?: "Brasileiro Série $nomeSerie $ano",
            campeaoTimeId        = campeao.timeId,
            campeaoNome          = timeRepository.buscarPorId(campeao.timeId)?.nome ?: "",
            campeaoEscudo        = timeRepository.buscarPorId(campeao.timeId)?.escudoRes ?: "",
            viceTimeId           = vice?.timeId ?: -1,
            viceNome             = vice?.let { timeRepository.buscarPorId(it.timeId)?.nome } ?: "",
            viceEscudo           = vice?.let { timeRepository.buscarPorId(it.timeId)?.escudoRes } ?: "",
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
            divisao              = div
        ))
    }

    /** Conquistas (títulos) de um time específico — usado em ConquistasScreen. */
    fun observarConquistasDoTime(timeId: Int): Flow<List<HallDaFamaEntity>> =
        hallDaFamaDao.observeConquistasDoTime(timeId)

    /** Vice-campeonatos de um time — usado opcionalmente em ConquistasScreen. */
    fun observarVicesDoTime(timeId: Int): Flow<List<HallDaFamaEntity>> =
        hallDaFamaDao.observeVicesDoTime(timeId)

    fun observarCopaPartidas(copaId: Int): Flow<List<CopaPartidaDto>> =
        partidaDao.observeCopaPartidas(copaId)

    fun observarRankingGeral(): Flow<List<RankingGeralEntity>> =
        rankingGeralDao.observeRanking()

    fun observarRankingGeralComPais(): Flow<List<br.com.managerfoot.data.dao.RankingGeralComPais>> =
        rankingGeralDao.observeRankingComPais()

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

                inserirNoHallDaFamaComTecnico(HallDaFamaEntity(
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
                // via registrarTituloArgentino(). O vice não recebe pontos: o ranking
                // geral pontua apenas desempenho em campo.

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

    // ── Cria Copa da Inglaterra (FA Cup): 32 times, JOGO ÚNICO + pênaltis ──
    // Inicia a Primeira Fase (16 confrontos de jogo único).
    suspend fun criarCopaInglaterra(campeonato: CampeonatoEntity, participantes: List<Int>): Int {
        val copaId = campeonatoDao.inserir(campeonato).toInt()
        campeonatoDao.inserirParticipantes(participantes.map { CampeonatoTimeEntity(copaId, it) })
        val pares = MotorCampeonato.sortearPares(participantes)
        val partidas = MotorCampeonato.gerarFaseJogoUnico(
            campeonatoId      = copaId,
            pares             = pares,
            fase              = MotorCampeonato.COPA_ING_FASES[0],
            rodada            = 1,
            confrontoIdInicio = 1,
            ordemGlobal       = MotorCampeonato.COPA_ING_ORDEM_GLOBAL[0]
        )
        partidaDao.inserirTodas(partidas)
        return copaId
    }

    // ── Participantes da Copa da Inglaterra: 20 da Premier + 12 melhores da Championship ──
    suspend fun determinarParticipantesCopaInglaterra(
        campeonatoPremierId: Int,
        campeonatoChampionshipId: Int
    ): List<Int> {
        val premier = if (campeonatoPremierId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoPremierId)
        else
            timeRepository.buscarTodosOrdenadosPorReputacao()
                .filter { it.pais == "Inglaterra" && it.divisao == 11 }.map { it.id }

        val top12Championship = if (campeonatoChampionshipId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoChampionshipId)
                .mapNotNull { timeRepository.buscarPorId(it) }
                .sortedByDescending { it.reputacao }.take(12).map { it.id }
        else
            timeRepository.buscarTodosOrdenadosPorReputacao()
                .filter { it.pais == "Inglaterra" && it.divisao == 12 }.take(12).map { it.id }

        return (premier + top12Championship).distinct().take(32)
    }

    // ── Copas de 16 times (Copa do Rei / Coppa Italia) — ida/volta, inicia nas Oitavas ──
    // Mesmo modelo da Copa do Brasil, porém começando direto nas Oitavas (16 clubes).
    suspend fun criarCopa16(campeonato: CampeonatoEntity, participantes: List<Int>): Int {
        val copaId = campeonatoDao.inserir(campeonato).toInt()
        campeonatoDao.inserirParticipantes(participantes.map { CampeonatoTimeEntity(copaId, it) })
        val pares = MotorCampeonato.sortearPares(participantes)
        val partidas = MotorCampeonato.gerarFaseIdaVolta(
            campeonatoId      = copaId,
            pares             = pares,
            fase              = MotorCampeonato.COPA16_FASES[0],
            rodadaIda         = MotorCampeonato.rodadaIdaDeFase(0),
            confrontoIdInicio = 1,
            ordemGlobalIda    = MotorCampeonato.COPA16_ORDEM_GLOBAL[0],
            ordemGlobalVolta  = MotorCampeonato.COPA16_ORDEM_GLOBAL[1]
        )
        partidaDao.inserirTodas(partidas)
        return copaId
    }

    // ── Participantes da Copa Espanhola (Copa do Rei): 16 melhores da La Liga ──
    suspend fun determinarParticipantesCopaEspanhola(campeonatoLaLigaId: Int): List<Int> {
        val base = if (campeonatoLaLigaId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoLaLigaId)
                .mapNotNull { timeRepository.buscarPorId(it) }
                .sortedByDescending { it.reputacao }.map { it.id }
        else
            timeRepository.buscarTodosOrdenadosPorReputacao()
                .filter { it.pais == "Espanha" && it.divisao == 13 }.map { it.id }
        return base.take(16)
    }

    // ── Participantes da Copa Italiana (Coppa Italia): 16 melhores da Serie A ──
    suspend fun determinarParticipantesCopaItaliana(campeonatoSerieAId: Int): List<Int> {
        val base = if (campeonatoSerieAId > 0)
            campeonatoDao.buscarIdsParticipantes(campeonatoSerieAId)
                .mapNotNull { timeRepository.buscarPorId(it) }
                .sortedByDescending { it.reputacao }.map { it.id }
        else
            timeRepository.buscarTodosOrdenadosPorReputacao()
                .filter { it.pais == "Italia" && it.divisao == 14 }.map { it.id }
        return base.take(16)
    }

    // ── Cria as copas de Espanha e Itália de uma temporada (16 melhores, ida/volta) ──
    // Extraído do rollover para manter encerrarTemporadaComHallDaFama abaixo do
    // limite de bytecode da JVM (método muito grande).
    private suspend fun criarCopasEspanholaItaliana(
        temporadaId: Int, ano: Int, laLigaId: Int, serieAId: Int
    ): Pair<Int, Int> {
        val partEsp = determinarParticipantesCopaEspanhola(laLigaId)
        val espId = if (partEsp.size >= 2) criarCopa16(
            CampeonatoEntity(
                temporadaId = temporadaId, nome = "Copa Espanhola $ano",
                tipo = TipoCampeonato.COPA_NACIONAL, formato = FormatoCampeonato.MATA_MATA_IDA_VOLTA,
                totalRodadas = 8, pais = "Espanha"
            ), partEsp
        ) else -1
        val partIta = determinarParticipantesCopaItaliana(serieAId)
        val itaId = if (partIta.size >= 2) criarCopa16(
            CampeonatoEntity(
                temporadaId = temporadaId, nome = "Copa Italiana $ano",
                tipo = TipoCampeonato.COPA_NACIONAL, formato = FormatoCampeonato.MATA_MATA_IDA_VOLTA,
                totalRodadas = 8, pais = "Italia"
            ), partIta
        ) else -1
        return espId to itaId
    }

    // ── Copa Espanhola (Copa do Rei): avanço de fase ida/volta (Hall divisao 25) ──
    suspend fun verificarEAvancarFaseCopaEspanhola(
        copaId: Int, anoAtual: Int, timeJogadorId: Int = -1, temporadaId: Int = 1
    ): Boolean = verificarEAvancarFaseCopa(
        copaId            = copaId,
        anoAtual          = anoAtual,
        timeJogadorId     = timeJogadorId,
        temporadaId       = temporadaId,
        fases             = MotorCampeonato.COPA16_FASES,
        ordemGlobal       = MotorCampeonato.COPA16_ORDEM_GLOBAL,
        divisaoHallDaFama = 25,
        premioCampeao     = 15_000_000_00L,
        premioVice        = 7_000_000_00L
    )

    // ── Copa Italiana (Coppa Italia): avanço de fase ida/volta (Hall divisao 26) ──
    suspend fun verificarEAvancarFaseCopaItaliana(
        copaId: Int, anoAtual: Int, timeJogadorId: Int = -1, temporadaId: Int = 1
    ): Boolean = verificarEAvancarFaseCopa(
        copaId            = copaId,
        anoAtual          = anoAtual,
        timeJogadorId     = timeJogadorId,
        temporadaId       = temporadaId,
        fases             = MotorCampeonato.COPA16_FASES,
        ordemGlobal       = MotorCampeonato.COPA16_ORDEM_GLOBAL,
        divisaoHallDaFama = 26,
        premioCampeao     = 15_000_000_00L,
        premioVice        = 7_000_000_00L
    )

    // ── Continua uma copa ida/volta quando o jogador não tem jogo pendente na fase ──
    // Simula os confrontos IA×IA pendentes e avança via [avancar].
    private suspend fun avancarCopaIdaVoltaSeJogadorEliminado(
        copaId: Int,
        timeJogadorId: Int,
        avancar: suspend () -> Unit
    ) {
        if (copaId <= 0) return
        if (campeonatoDao.buscarPorId(copaId)?.encerrado == true) return
        val todasPartidas = partidaDao.buscarTodasPorCampeonato(copaId)
        if (todasPartidas.any { !it.jogada && (it.timeCasaId == timeJogadorId || it.timeForaId == timeJogadorId) }) return
        val pendentes = todasPartidas.filter { !it.jogada }
        if (pendentes.isEmpty()) { avancar(); return }
        pendentes.map { it.rodada }.toSortedSet().forEach { rodada -> simularRodada(copaId, rodada) }
        avancar()
    }

    suspend fun simularProximaFaseCopaEspanholaSeJogadorEliminado(
        copaId: Int, timeJogadorId: Int, anoAtual: Int, temporadaId: Int
    ) = avancarCopaIdaVoltaSeJogadorEliminado(copaId, timeJogadorId) {
        verificarEAvancarFaseCopaEspanhola(copaId, anoAtual, timeJogadorId, temporadaId)
    }

    suspend fun simularProximaFaseCopaItalianaSeJogadorEliminado(
        copaId: Int, timeJogadorId: Int, anoAtual: Int, temporadaId: Int
    ) = avancarCopaIdaVoltaSeJogadorEliminado(copaId, timeJogadorId) {
        verificarEAvancarFaseCopaItaliana(copaId, anoAtual, timeJogadorId, temporadaId)
    }

    // ══════════════════════════════════════════════════════════════
    //  UEFA Champions League  (TipoCampeonato.CONTINENTAL)
    //  Ver regras_championsleague.md e MotorCampeonato (seção Champions).
    //
    //  Fase de Liga de 36 clubes com fase = null — é o que faz a tabela de 1 a
    //  36 ser alimentada pelo caminho normal da classificação — seguida de
    //  play-off (9º–24º) e mata-mata das oitavas em diante, todos ida e volta.
    //  O coeficiente da UEFA ordena os potes, e ao encerrar a competição cada
    //  participante tem a sua campanha registrada de volta nesse coeficiente.
    // ══════════════════════════════════════════════════════════════

    /** Código de divisão da Champions no Hall da Fama. */
    private val DIVISAO_HALL_CHAMPIONS = 30

    /**
     * Código de divisão da Europa League no Hall da Fama. A 30 é da Champions e a
     * 32 fica reservada à Conference League — ver a lista de opções continentais
     * em HallDaFamaScreen, que é a fonte desses códigos.
     */
    private val DIVISAO_HALL_EUROPA = 31

    /**
     * Monta a lista de acesso da temporada: clubes europeus das divisões de elite
     * e do pool, ordenados pelo coeficiente da UEFA com a reputação desempatando.
     *
     * Devolve menos de 36 vagas quando o jogo não tem clubes europeus suficientes
     * — hoje é o caso enquanto o pool de clubes estrangeiros não existir, e o
     * sinal para não criar a competição.
     */
    suspend fun determinarParticipantesChampions(
        anoAtual: Int,
        temporadaAnteriorId: Int = -1
    ): List<VagaChampions> =
        ListaDeAcessoChampions.montar(candidatosEuropeus(anoAtual, temporadaAnteriorId))

    /**
     * Monta a lista de acesso da Europa League, que é a mesma leitura de
     * coeficientes e colocações da Champions com duas diferenças de fundo.
     *
     * A primeira é [ocupados]: um clube não disputa as duas competições, e a
     * Europa League é montada DEPOIS da Champions, recebendo os clubes dela. A
     * segunda são os campeões das copas nacionais europeias da temporada que
     * encerra, que entram por uma porta própria — ver europa_league.md.
     *
     * Devolve menos de 36 vagas quando o jogo não tem clubes europeus livres
     * suficientes, e aí a competição não é criada nesta temporada.
     */
    suspend fun determinarParticipantesEuropa(
        anoAtual: Int,
        temporadaAnteriorId: Int = -1,
        ocupados: Set<Int> = emptySet()
    ): List<VagaEuropa> = ListaDeAcessoEuropa.montar(
        candidatos     = candidatosEuropeus(anoAtual, temporadaAnteriorId),
        campeoesDaCopa = campeoesDasCopasEuropeias(temporadaAnteriorId),
        ocupados       = ocupados
    )

    /**
     * Campeão de cada copa nacional europeia da temporada informada, por país
     * canônico da lista de acesso — Copa da Inglaterra, Copa do Rei e Coppa
     * Italia, hoje as três copas europeias do jogo.
     *
     * Vazio na primeira temporada, quando nenhuma copa encerrou ainda: aí a vaga
     * da copa desce para o colocado seguinte da liga, como manda a regra.
     */
    private suspend fun campeoesDasCopasEuropeias(temporadaId: Int): Map<String, Int> {
        if (temporadaId <= 0) return emptyMap()
        val copas = campeonatoDao.buscarPorTemporadaId(temporadaId).filter { camp ->
            camp.tipo == TipoCampeonato.COPA_NACIONAL && Continentes.ehEuropa(camp.pais)
        }
        val campeoes = HashMap<String, Int>()
        for (copa in copas) {
            val pais = ListaDeAcessoEuropa.paisCanonico(copa.pais) ?: continue
            val campeao = buscarCampeaoCopaSeEncerrada(copa.id)
            if (campeao > 0) campeoes[pais] = campeao
        }
        return campeoes
    }

    /**
     * Clubes europeus elegíveis às competições continentais, com o coeficiente da
     * UEFA e a colocação final na liga nacional já resolvidos. Base comum das
     * listas de acesso da Champions e da Europa League.
     */
    private suspend fun candidatosEuropeus(
        anoAtual: Int,
        temporadaAnteriorId: Int
    ): List<CandidatoChampions> {
        val coeficientes = rankingEuropaDao.buscarCoeficientes(anoAtual)
            .associate { it.timeId to it.coeficiente }
        val posicoes = posicoesFinaisDasLigasEuropeias(temporadaAnteriorId)
        val candidatos = timeRepository.buscarTodosOrdenadosPorReputacao()
            .filter { it.divisao in RankingEuropa.DIVISOES_ELEGIVEIS_CHAMPIONS }
            .filter { Continentes.ehEuropa(it.pais) }
            .map { time ->
                CandidatoChampions(
                    timeId      = time.id,
                    nome        = time.nome,
                    pais        = time.pais,
                    coeficiente = coeficientes[time.id] ?: 0.0,
                    reputacao   = time.reputacao,
                    posicaoLiga = posicoes[time.id]
                )
            }
        return candidatos
    }

    /**
     * Colocação final de cada clube nas primeiras divisões europeias da temporada
     * que encerra — Premier League, La Liga e Serie A. É o que decide quem vai à
     * Champions da temporada seguinte, em vez de reputação ou coeficiente.
     *
     * Devolve um mapa vazio quando não há temporada anterior (primeiro ano de
     * jogo), e aí a lista de acesso cai no coeficiente e na reputação. Clubes do
     * pool estrangeiro nunca aparecem aqui, porque não têm liga própria.
     */
    private suspend fun posicoesFinaisDasLigasEuropeias(temporadaId: Int): Map<Int, Int> {
        if (temporadaId <= 0) return emptyMap()
        val ligas = campeonatoDao.buscarPorTemporadaId(temporadaId).filter { camp ->
            camp.tipo == TipoCampeonato.NACIONAL_DIVISAO1 && Continentes.ehEuropa(camp.pais)
        }
        val posicoes = HashMap<Int, Int>()
        for (liga in ligas) {
            classificacaoDao.buscarTabelaOrdenada(liga.id).forEachIndexed { indice, linha ->
                posicoes[linha.timeId] = indice + 1
            }
        }
        return posicoes
    }

    /**
     * Cria a Champions e persiste as 144 partidas da Fase de Liga.
     *
     * Devolve -1 quando o sorteio é impossível: as travas geográficas exigem no
     * mínimo 5 países e no máximo 7 clubes por país — e no máximo 4 de um país
     * dentro de um pote, restrição que a lista de acesso não consegue garantir
     * sozinha, porque os potes saem do coeficiente. Em vez de criar um torneio
     * sem calendário, não criamos nada e o resto do jogo segue sem a competição.
     */
    suspend fun criarChampions(
        campeonato: CampeonatoEntity,
        vagas: List<VagaChampions>,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): Int = criarCompeticaoContinental(
        campeonato = campeonato,
        clubes     = ListaDeAcessoChampions.paraSorteio(vagas),
        formato    = MotorCampeonato.FORMATO_CHAMPIONS,
        random     = random
    )

    /**
     * Cria a Europa League e persiste as 144 partidas da sua Fase de Liga.
     *
     * Mesmo sorteio e mesmas travas da Champions: o que muda é a lista de acesso
     * (ver [determinarParticipantesEuropa]) e as casas do calendário, que vêm de
     * [MotorCampeonato.FORMATO_EUROPA_LEAGUE] e nunca colidem com as da Champions.
     */
    suspend fun criarEuropaLeague(
        campeonato: CampeonatoEntity,
        vagas: List<VagaEuropa>,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): Int = criarCompeticaoContinental(
        campeonato = campeonato,
        clubes     = ListaDeAcessoEuropa.paraSorteio(vagas),
        formato    = MotorCampeonato.FORMATO_EUROPA_LEAGUE,
        random     = random
    )

    /**
     * Grava uma competição continental de Fase de Liga: participantes, tabela
     * inicial de 36 linhas e as 144 partidas já distribuídas em 8 rodadas.
     *
     * Devolve -1 quando o sorteio é impossível: as travas geográficas exigem no
     * mínimo 5 países e no máximo 7 clubes por país — e no máximo 4 de um país
     * dentro de um pote, restrição que a lista de acesso não consegue garantir
     * sozinha, porque os potes saem do coeficiente. Em vez de criar um torneio
     * sem calendário, não criamos nada e o resto do jogo segue sem a competição.
     */
    private suspend fun criarCompeticaoContinental(
        campeonato: CampeonatoEntity,
        clubes: List<ClubeChampions>,
        formato: MotorCampeonato.FormatoContinental,
        random: kotlin.random.Random
    ): Int {
        if (clubes.size < SorteioChampions.CLUBES) return -1
        if (SorteioChampions.verificarViabilidade(clubes).isNotEmpty()) return -1
        val sorteio = SorteioChampions.sortear(clubes, random) ?: return -1

        val campeonatoId = campeonatoDao.inserir(campeonato).toInt()
        val participantes = clubes.map { it.timeId }
        campeonatoDao.inserirParticipantes(
            participantes.map { CampeonatoTimeEntity(campeonatoId, it) }
        )
        classificacaoDao.inserirTodos(
            MotorCampeonato.gerarClassificacaoInicial(campeonatoId, participantes)
        )
        partidaDao.inserirTodas(
            MotorCampeonato.gerarFaseLigaChampions(
                campeonatoId, sorteio.confrontos, random, formato
            )
        )
        return campeonatoId
    }

    /**
     * Conclui a Champions da temporada que encerra e cria a da temporada nova.
     *
     * A conclusão forçada é necessária porque, quando o jogador não disputa a
     * Champions (ou caiu cedo), ela pode chegar ao fim da temporada inacabada —
     * e sem o campeão registrado não há título no Hall da Fama nem campanhas no
     * coeficiente da UEFA, que é justamente o que ordena os potes da edição
     * seguinte. Por isso conclui primeiro e cria depois, nesta ordem.
     *
     * Extraído de [encerrarTemporadaComHallDaFama] para manter aquele método
     * abaixo do limite de 64KB de bytecode da JVM, onde ele já estava raspando.
     */
    private suspend fun encerrarECriarChampions(
        championsIdAnterior: Int,
        ano: Int,
        temporadaId: Int,
        novoTemporadaId: Int,
        novoAno: Int
    ): Int {
        concluirCompeticaoContinental(championsIdAnterior) {
            simularProximaRodadaChampionsSeNecessario(championsIdAnterior, -1, ano, temporadaId)
        }
        return try {
            // temporadaId é a que encerra: a sua tabela final é a que classifica
            // os clubes para a Champions da temporada nova.
            criarChampionsDaTemporada(novoTemporadaId, novoAno, temporadaAnteriorId = temporadaId)
        } catch (_: Exception) { -1 }
    }

    /**
     * O mesmo para a Europa League. Tem de ser chamada DEPOIS de
     * [encerrarECriarChampions], recebendo em [championsIdNovo] a Champions que
     * acabou de ser criada: os clubes dela são os ocupados da lista de acesso.
     */
    private suspend fun encerrarECriarEuropa(
        europaIdAnterior: Int,
        championsIdNovo: Int,
        ano: Int,
        temporadaId: Int,
        novoTemporadaId: Int,
        novoAno: Int
    ): Int {
        concluirCompeticaoContinental(europaIdAnterior) {
            simularProximaRodadaEuropaSeNecessario(europaIdAnterior, -1, ano, temporadaId)
        }
        return try {
            criarEuropaDaTemporada(
                temporadaId         = novoTemporadaId,
                ano                 = novoAno,
                temporadaAnteriorId = temporadaId,
                championsId         = championsIdNovo
            )
        } catch (_: Exception) { -1 }
    }

    /**
     * Força a conclusão de uma competição continental que ficou pendente. É
     * necessário porque, quando o jogador não a disputa (ou caiu cedo), ela pode
     * chegar ao fim da temporada inacabada — e sem o campeão registrado não há
     * título no Hall da Fama nem campanhas no coeficiente da UEFA, que é
     * justamente o que ordena os potes da edição seguinte.
     */
    private suspend fun concluirCompeticaoContinental(
        campeonatoId: Int,
        simularUmaRodada: suspend () -> Unit
    ) {
        var iteracoes = 60
        while (campeonatoId > 0 && iteracoes-- > 0) {
            if (campeonatoDao.buscarPorId(campeonatoId)?.encerrado != false) break
            try { simularUmaRodada() } catch (_: Exception) { break }
        }
    }

    /**
     * Cria a Champions de uma temporada, ou devolve -1 quando a lista de acesso
     * não fecha os 36 clubes. Extraído em helper porque o rollover de temporada
     * já está no limite de bytecode da JVM.
     */
    suspend fun criarChampionsDaTemporada(
        temporadaId: Int,
        ano: Int,
        temporadaAnteriorId: Int = -1
    ): Int {
        val vagas = determinarParticipantesChampions(ano, temporadaAnteriorId)
        if (vagas.size < SorteioChampions.CLUBES) return -1
        return criarChampions(
            CampeonatoEntity(
                temporadaId  = temporadaId,
                nome         = "Champions League $ano",
                tipo         = TipoCampeonato.CONTINENTAL,
                formato      = FormatoCampeonato.FASE_LIGA_E_MATA_MATA,
                totalRodadas = MotorCampeonato.CHAMPIONS_TOTAL_RODADAS,
                // Torneio continental: não pertence a um país. O nome é o que
                // identifica a competição para o peso 14 da IFFHS.
                pais         = Continentes.EUROPA
            ),
            vagas
        )
    }

    /**
     * Cria a Europa League de uma temporada, ou devolve -1 quando a lista de
     * acesso não fecha os 36 clubes.
     *
     * **A ordem importa:** tem de ser chamada DEPOIS de [criarChampionsDaTemporada],
     * recebendo o id da Champions criada. Os participantes dela são os `ocupados`
     * da lista de acesso, porque um clube não disputa as duas competições. Com
     * championsId = -1 (Champions que não cabe no seed) a Europa League é montada
     * com o campo europeu inteiro disponível, que é o comportamento desejado.
     */
    suspend fun criarEuropaDaTemporada(
        temporadaId: Int,
        ano: Int,
        temporadaAnteriorId: Int = -1,
        championsId: Int = -1
    ): Int {
        val ocupados = if (championsId > 0) {
            campeonatoDao.buscarIdsParticipantes(championsId).toSet()
        } else emptySet()
        val vagas = determinarParticipantesEuropa(ano, temporadaAnteriorId, ocupados)
        if (vagas.size < SorteioChampions.CLUBES) return -1
        return criarEuropaLeague(
            CampeonatoEntity(
                temporadaId  = temporadaId,
                nome         = "Europa League $ano",
                tipo         = TipoCampeonato.CONTINENTAL,
                formato      = FormatoCampeonato.FASE_LIGA_E_MATA_MATA,
                totalRodadas = MotorCampeonato.FORMATO_EUROPA_LEAGUE.totalRodadas,
                pais         = Continentes.EUROPA
            ),
            vagas
        )
    }

    /**
     * Avança a Champions: gera o play-off quando a Fase de Liga termina, gera as
     * oitavas quando o play-off termina, e delega o resto do mata-mata ao motor
     * de copa ida/volta. Devolve true quando a final foi concluída nesta chamada.
     */
    suspend fun verificarEAvancarFaseChampions(
        championsId: Int,
        anoAtual: Int,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1
    ): Boolean = verificarEAvancarFaseContinental(
        campeonatoId      = championsId,
        formato           = MotorCampeonato.FORMATO_CHAMPIONS,
        competicao        = CompeticaoUefa.CHAMPIONS_LEAGUE,
        divisaoHallDaFama = DIVISAO_HALL_CHAMPIONS,
        premioCampeao     = 120_000_000_00L,
        premioVice        = 60_000_000_00L,
        anoAtual          = anoAtual,
        timeJogadorId     = timeJogadorId,
        temporadaId       = temporadaId
    )

    /**
     * Avança a Europa League. É a mesma máquina da Champions, com o formato, o
     * código do Hall da Fama e os pesos de coeficiente da Europa League — e
     * prêmios menores, como na competição real.
     */
    suspend fun verificarEAvancarFaseEuropa(
        europaLeagueId: Int,
        anoAtual: Int,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1
    ): Boolean = verificarEAvancarFaseContinental(
        campeonatoId      = europaLeagueId,
        formato           = MotorCampeonato.FORMATO_EUROPA_LEAGUE,
        competicao        = CompeticaoUefa.EUROPA_LEAGUE,
        divisaoHallDaFama = DIVISAO_HALL_EUROPA,
        premioCampeao     = 40_000_000_00L,
        premioVice        = 20_000_000_00L,
        anoAtual          = anoAtual,
        timeJogadorId     = timeJogadorId,
        temporadaId       = temporadaId
    )

    /**
     * Avança uma competição continental: gera o play-off quando a Fase de Liga
     * termina, gera as oitavas quando o play-off termina, e delega o resto do
     * mata-mata ao motor de copa ida/volta. Devolve true quando a final foi
     * concluída nesta chamada.
     *
     * Parametrizada pelo [formato] porque Champions e Europa League são a mesma
     * máquina em casas diferentes do calendário — ver europa_league.md.
     */
    private suspend fun verificarEAvancarFaseContinental(
        campeonatoId: Int,
        formato: MotorCampeonato.FormatoContinental,
        competicao: CompeticaoUefa,
        divisaoHallDaFama: Int,
        premioCampeao: Long,
        premioVice: Long,
        anoAtual: Int,
        timeJogadorId: Int,
        temporadaId: Int
    ): Boolean {
        if (campeonatoId <= 0) return false
        val camp = campeonatoDao.buscarPorId(campeonatoId) ?: return false
        if (camp.encerrado) return false
        val todas = partidaDao.buscarTodasPorCampeonato(campeonatoId)
        if (todas.isEmpty()) return false

        // ── Fase de Liga (fase = null) precisa estar completa ──────────
        val faseLiga = todas.filter { it.fase == null }
        if (faseLiga.isEmpty() || !faseLiga.all { it.jogada }) return false

        val temPlayoff = todas.any { it.fase == formato.fasePlayoff }
        if (!temPlayoff) {
            gerarPlayoffContinental(campeonatoId, formato)
            return false
        }

        // ── Play-off completo → oitavas com os 8 cabeças de chave ──────
        val partidasPlayoff = todas.filter { it.fase == formato.fasePlayoff }
        val temOitavas = todas.any { it.fase == formato.fasesMataMata.first() }
        if (!temOitavas) {
            if (!partidasPlayoff.all { it.jogada }) return false
            gerarOitavasContinental(campeonatoId, partidasPlayoff, formato)
            return false
        }

        // ── Oitavas → Final: o motor de copa ida/volta dá conta ────────
        val encerrou = verificarEAvancarFaseCopa(
            copaId            = campeonatoId,
            anoAtual          = anoAtual,
            timeJogadorId     = timeJogadorId,
            temporadaId       = temporadaId,
            fases             = formato.fasesMataMata,
            ordemGlobal       = formato.ordemGlobalMataMata,
            divisaoHallDaFama = divisaoHallDaFama,
            premioCampeao     = premioCampeao,
            premioVice        = premioVice,
            rodadaBase        = formato.rodadaBaseMataMata
        )
        if (encerrou) registrarCampanhasEuropeias(campeonatoId, anoAtual, competicao)
        return encerrou
    }

    /** Play-off: 9º–24º da Fase de Liga, ida e volta, melhor colocado decide em casa. */
    private suspend fun gerarPlayoffContinental(
        championsId: Int,
        formato: MotorCampeonato.FormatoContinental
    ) {
        // A tabela decide quem passa direto, quem joga o play-off e quem cai, e
        // ainda alimenta o bônus de posição do coeficiente da UEFA. Recalcula das
        // partidas antes de ler, para que uma atualização incremental perdida não
        // vire um chaveamento errado — na Champions não há como corrigir depois.
        recalcularClassificacao(championsId)
        val classificados = classificacaoDao.buscarTabelaOrdenada(championsId).map { it.timeId }
        val pares = MotorCampeonato.paresPlayoffChampions(classificados, formato)
        if (pares.isEmpty()) return
        partidaDao.inserirTodas(
            MotorCampeonato.gerarFaseIdaVolta(
                campeonatoId      = championsId,
                pares             = pares,
                fase              = formato.fasePlayoff,
                rodadaIda         = formato.rodadaIdaPlayoff,
                confrontoIdInicio = 1,
                ordemGlobalIda    = formato.ordemGlobalPlayoff[0],
                ordemGlobalVolta  = formato.ordemGlobalPlayoff[1]
            )
        )
    }

    /**
     * Oitavas: os 8 primeiros da Fase de Liga contra os 8 vencedores do play-off,
     * o 1º contra o vencedor do último par. Os vencedores saem na ordem dos
     * confrontos do play-off, para o cruzamento ficar estável.
     */
    private suspend fun gerarOitavasContinental(
        championsId: Int,
        partidasPlayoff: List<PartidaEntity>,
        formato: MotorCampeonato.FormatoContinental
    ) {
        recalcularClassificacao(championsId)
        val classificados = classificacaoDao.buscarTabelaOrdenada(championsId).map { it.timeId }
        val vencedores = partidasPlayoff
            .groupBy { it.confrontoId }
            .toSortedMap(compareBy { it ?: 0 })
            .mapNotNull { (_, jogos) -> vencedorDoTie(jogos) }
        if (vencedores.size < formato.classificadosDireto) return

        val pares = MotorCampeonato.paresOitavasChampions(classificados, vencedores, formato)
        partidaDao.inserirTodas(
            MotorCampeonato.gerarFaseIdaVolta(
                campeonatoId      = championsId,
                pares             = pares,
                fase              = formato.fasesMataMata.first(),
                rodadaIda         = formato.rodadaIdaDeFaseMataMata(0),
                confrontoIdInicio = (partidasPlayoff.mapNotNull { it.confrontoId }.maxOrNull() ?: 0) + 1,
                ordemGlobalIda    = formato.ordemGlobalMataMata[0],
                ordemGlobalVolta  = formato.ordemGlobalMataMata[1]
            )
        )
    }

    /**
     * Vencedor de um confronto ida/volta, decidindo nos pênaltis quando o
     * agregado empata. Para confrontos IA×IA sem pênaltis persistidos, simula a
     * disputa aqui — o mesmo caminho das copas.
     */
    private suspend fun vencedorDoTie(jogos: List<PartidaEntity>): Int? {
        val ida   = jogos.minByOrNull { it.rodada } ?: return null
        val volta = jogos.maxByOrNull { it.rodada } ?: return null
        val direto = MotorCampeonato.determinarVencedorTie(
            timeCasaIdaId = ida.timeCasaId,
            timeForaIdaId = ida.timeForaId,
            golsCasaIda   = ida.golsCasa   ?: 0,
            golsForaIda   = ida.golsFora   ?: 0,
            golsCasaVolta = volta.golsCasa ?: 0,
            golsForaVolta = volta.golsFora ?: 0
        )
        if (direto != null) return direto
        val pCasa = volta.penaltisCasa
        val pFora = volta.penaltisForaId
        if (pCasa != null && pFora != null) {
            return if (pCasa > pFora) volta.timeCasaId else volta.timeForaId
        }
        return simularEPersistirPenaltisIA(ida, volta)
    }

    /**
     * Registra a campanha de cada participante no Coeficiente de Clubes da UEFA.
     *
     * Vitórias e empates contam todas as partidas da competição, Fase de Liga e
     * mata-mata — inclusive as do play-off, que pontuam por jogo ainda que não
     * dêem bônus de avanço. Jogo decidido nos pênaltis entra como empate, que é
     * exatamente o que o placar dos 90 minutos devolve.
     *
     * A fase alcançada começa nas oitavas: quem caiu no play-off não leva bônus
     * de avanço, só os pontos por jogo (ver RankingEuropa.bonusPorAvanco).
     *
     * Apaga os lançamentos do ano antes de registrar, para reencerrar a mesma
     * temporada não duplicar a pontuação.
     */
    private suspend fun registrarCampanhasEuropeias(
        championsId: Int,
        ano: Int,
        competicao: CompeticaoUefa
    ) {
        rankingEuropaDao.apagarLancamentos(ano, competicao.name)

        recalcularClassificacao(championsId)
        val classificados = classificacaoDao.buscarTabelaOrdenada(championsId).map { it.timeId }
        val partidas = partidaDao.buscarTodasPorCampeonato(championsId).filter { it.jogada }

        for ((indice, timeId) in classificados.withIndex()) {
            val doClube = partidas.filter { it.timeCasaId == timeId || it.timeForaId == timeId }
            var vitorias = 0
            var empates  = 0
            for (partida in doClube) {
                val golsPro    = if (partida.timeCasaId == timeId) partida.golsCasa else partida.golsFora
                val golsContra = if (partida.timeCasaId == timeId) partida.golsFora else partida.golsCasa
                if (golsPro == null || golsContra == null) continue
                when {
                    golsPro > golsContra  -> vitorias++
                    golsPro == golsContra -> empates++
                }
            }
            registrarCampanhaEuropeia(
                timeId          = timeId,
                ano             = ano,
                competicao      = competicao,
                posicaoFaseLiga = indice + 1,
                vitorias        = vitorias,
                empates         = empates,
                faseAlcancada   = faseUefaAlcancada(doClube)
            )
        }
    }

    /** Fase mais avançada do mata-mata em que o clube jogou, ou null se nenhuma. */
    private fun faseUefaAlcancada(partidasDoClube: List<PartidaEntity>): FaseUefa? {
        val fases = partidasDoClube.mapNotNull { it.fase }.toSet()
        return FaseUefa.entries
            .filter { fase -> nomeDaFase(fase) in fases }
            .maxByOrNull { it.ordem }
    }

    /** Nome da fase no banco para cada fase pontuável do coeficiente da UEFA. */
    private fun nomeDaFase(fase: FaseUefa): String = when (fase) {
        FaseUefa.OITAVAS   -> "Oitavas"
        FaseUefa.QUARTAS   -> "Quartas"
        FaseUefa.SEMIFINAL -> "Semi"
        FaseUefa.FINAL     -> "Final"
    }

    /**
     * Mantém a Champions andando quando o jogador não tem jogo pendente nela:
     * simula uma rodada da Fase de Liga por chamada (como as ligas nacionais) e,
     * terminada a Fase de Liga, simula as fases de mata-mata pendentes.
     */
    suspend fun simularProximaRodadaChampionsSeNecessario(
        championsId: Int,
        timeJogadorId: Int,
        anoAtual: Int,
        temporadaId: Int
    ) = simularProximaRodadaContinentalSeNecessario(
        championsId, timeJogadorId, anoAtual, temporadaId
    ) { verificarEAvancarFaseChampions(championsId, anoAtual, timeJogadorId, temporadaId) }

    /** O mesmo, para a Europa League. */
    suspend fun simularProximaRodadaEuropaSeNecessario(
        europaLeagueId: Int,
        timeJogadorId: Int,
        anoAtual: Int,
        temporadaId: Int
    ) = simularProximaRodadaContinentalSeNecessario(
        europaLeagueId, timeJogadorId, anoAtual, temporadaId
    ) { verificarEAvancarFaseEuropa(europaLeagueId, anoAtual, timeJogadorId, temporadaId) }

    /**
     * Corpo comum das duas: a simulação é idêntica — o que muda é só qual máquina
     * de fases chamar no fim, que vem em [avancarFase].
     */
    private suspend fun simularProximaRodadaContinentalSeNecessario(
        championsId: Int,
        timeJogadorId: Int,
        anoAtual: Int,
        temporadaId: Int,
        avancarFase: suspend () -> Unit
    ) {
        if (championsId <= 0) return
        val camp = campeonatoDao.buscarPorId(championsId) ?: return
        if (camp.encerrado) return
        val todas = partidaDao.buscarTodasPorCampeonato(championsId)
        // Jogador com jogo pendente → não interferir, ele joga quando chegar a vez.
        if (todas.any { !it.jogada && (it.timeCasaId == timeJogadorId || it.timeForaId == timeJogadorId) }) return

        val pendentesLiga = todas.filter { !it.jogada && it.fase == null }
        if (pendentesLiga.isNotEmpty()) {
            // Uma rodada por chamada, começando pela mais antiga pendente.
            simularRodada(championsId, pendentesLiga.minOf { it.rodada })
        } else {
            val pendentesMataMata = todas.filter { !it.jogada && it.fase != null }
            pendentesMataMata.map { it.rodada }.toSortedSet().forEach { rodada ->
                simularRodada(championsId, rodada)
            }
        }
        avancarFase()
    }

    // ── Verifica se fase atual da Copa da Inglaterra (jogo único) acabou e avança ──
    // Vencedor de cada confronto = quem marcou mais; empate → pênaltis (já persistidos
    // pelo jogador/IA, ou simulados aqui para confrontos IA×IA).
    suspend fun verificarEAvancarFaseCopaInglaterra(
        copaId: Int,
        anoAtual: Int,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1
    ): Boolean {
        val fases = MotorCampeonato.COPA_ING_FASES
        val campEntity = campeonatoDao.buscarPorId(copaId) ?: return false
        if (campEntity.encerrado) return false
        val todasPartidas = partidaDao.buscarTodasPorCampeonato(copaId)
        if (todasPartidas.isEmpty()) return false

        for (faseAtual in fases) {
            val partidasFase = todasPartidas.filter { it.fase == faseAtual }
            if (partidasFase.isEmpty()) continue
            if (!partidasFase.all { it.jogada }) return false

            val proximaFase = MotorCampeonato.proximaFaseCopa(faseAtual, fases)
            if (proximaFase != null && todasPartidas.any { it.fase == proximaFase }) continue

            // Determina vencedores (1 partida por confronto)
            val vencedores = mutableListOf<Int>()
            for (jogo in partidasFase.sortedBy { it.confrontoId }) {
                val gC = jogo.golsCasa ?: 0
                val gF = jogo.golsFora ?: 0
                when {
                    gC > gF -> vencedores.add(jogo.timeCasaId)
                    gF > gC -> vencedores.add(jogo.timeForaId)
                    else -> {
                        val pCasa = jogo.penaltisCasa
                        val pFora = jogo.penaltisForaId
                        if (pCasa != null && pFora != null) {
                            vencedores.add(if (pCasa > pFora) jogo.timeCasaId else jogo.timeForaId)
                        } else {
                            // Empate IA×IA sem pênaltis persistidos → simula agora
                            vencedores.add(simularEPersistirPenaltisIA(jogo, jogo))
                        }
                    }
                }
            }

            if (proximaFase == null) {
                // Final concluída → Hall da Fama + encerra
                val finalJogo = partidasFase.firstOrNull() ?: return true
                val campeaoId = vencedores.firstOrNull() ?: return true
                val viceId = if (campeaoId == finalJogo.timeCasaId) finalJogo.timeForaId else finalJogo.timeCasaId
                registrarCampeaoCopaInglaterra(copaId, campeaoId, viceId, anoAtual, timeJogadorId, temporadaId)
                campeonatoDao.encerrar(copaId)
                return true
            }

            // Gera a próxima fase (jogo único)
            val faseIndex     = fases.indexOf(proximaFase)
            val maxConfId     = todasPartidas.mapNotNull { it.confrontoId }.maxOrNull() ?: 0
            val novosPares    = MotorCampeonato.sortearPares(vencedores)
            val novasPartidas = MotorCampeonato.gerarFaseJogoUnico(
                campeonatoId      = copaId,
                pares             = novosPares,
                fase              = proximaFase,
                rodada            = faseIndex + 1,
                confrontoIdInicio = maxConfId + 1,
                ordemGlobal       = MotorCampeonato.COPA_ING_ORDEM_GLOBAL[faseIndex]
            )
            partidaDao.inserirTodas(novasPartidas)
            return false
        }
        return false
    }

    // Hall da Fama + premiação do campeão da Copa da Inglaterra (divisão 23).
    private suspend fun registrarCampeaoCopaInglaterra(
        copaId: Int, campeaoId: Int, viceId: Int,
        anoAtual: Int, timeJogadorId: Int, temporadaId: Int
    ) {
        val campeao    = timeRepository.buscarEntityPorId(campeaoId)
        val vice       = timeRepository.buscarEntityPorId(viceId)
        val copaEnt    = campeonatoDao.buscarPorId(copaId)
        val artilheiro = partidaDao.buscarArtilheiroTop1(copaId)
        val assistente = partidaDao.buscarAssisteTop1(copaId)
        inserirNoHallDaFamaComTecnico(
            HallDaFamaEntity(
                ano                 = anoAtual,
                nomeCampeonato      = copaEnt?.nome ?: "Copa da Inglaterra $anoAtual",
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
                divisao             = 23  // 23 = Copa da Inglaterra
            )
        )
        // Ranking geral + reputação
        val rankCampeao = rankingGeralDao.buscarPorTime(campeaoId)
            ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                timeId = campeaoId, nomeTime = campeao?.nome ?: "",
                escudoRes = campeao?.escudoRes ?: "", divisaoAtual = campeao?.divisao ?: 11
            )
        rankingGeralDao.inserirOuAtualizar(rankCampeao.copy(
            copasVencidas    = rankCampeao.copasVencidas + 1
        ))
        campeao?.let {
            val bonus = (it.reputacao * 0.04f).coerceAtLeast(0.1f)
            timeRepository.atualizarReputacao(campeaoId, it.reputacao + bonus)
        }
        // Premiação se o campeão/vice é o time do jogador
        val premios = listOf(campeaoId to (25_000_000_00L to "Campeão"), viceId to (12_000_000_00L to "Vice-campeão"))
        val nomePremio = copaEnt?.nome ?: "Copa da Inglaterra $anoAtual"
        for ((timePremiado, info) in premios) {
            val (valor, posicao) = info
            if (timeJogadorId > 0 && timePremiado == timeJogadorId) {
                timeRepository.creditarSaldo(timeJogadorId, valor)
                financaDao.inserir(
                    br.com.managerfoot.data.database.entities.FinancaEntity(
                        timeId = timeJogadorId, temporadaId = temporadaId, mes = 13,
                        receitaPremiacoes = valor, descricaoPremio = "$nomePremio — $posicao", saldoFinal = valor
                    )
                )
            }
        }
    }

    // ── Continua a Copa da Inglaterra quando o jogador não está na fase pendente ──
    suspend fun simularProximaFaseCopaInglaterraSeJogadorEliminado(
        copaId: Int,
        timeJogadorId: Int,
        anoAtual: Int,
        temporadaId: Int
    ) {
        if (copaId <= 0) return
        if (campeonatoDao.buscarPorId(copaId)?.encerrado == true) return
        val todasPartidas = partidaDao.buscarTodasPorCampeonato(copaId)
        if (todasPartidas.isEmpty()) return

        // Fase corrente = primeira fase com jogos não jogados
        val faseCorrente = MotorCampeonato.COPA_ING_FASES.firstOrNull { fase ->
            todasPartidas.any { it.fase == fase && !it.jogada }
        } ?: return
        val partidasFase = todasPartidas.filter { it.fase == faseCorrente }
        // Se o jogador tem jogo pendente nesta fase, não simula (ele vai jogar)
        if (partidasFase.any { !it.jogada && (it.timeCasaId == timeJogadorId || it.timeForaId == timeJogadorId) }) return

        // Simula os jogos pendentes desta fase (IA×IA) — jogo único: 1 rodada por fase
        partidasFase.filter { !it.jogada }.map { it.rodada }.toSortedSet().forEach { rodada ->
            simularRodada(copaId, rodada)
        }
        verificarEAvancarFaseCopaInglaterra(copaId, anoAtual, timeJogadorId, temporadaId)
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
            inserirNoHallDaFamaComTecnico(HallDaFamaEntity(
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
            inserirNoHallDaFamaComTecnico(HallDaFamaEntity(
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

        // ── Hall da Fama: Campeão do Torneio Clausura (líder da fase regular) ──
        // Registramos ANTES de gerar partidas de playoff, para que a tabela ainda
        // reflita apenas a fase regular. O playoff define o Campeão Uruguaio
        // (divisão 14, registrado em registrarCampeaoUruguai).
        try {
            registrarHallDaFamaPontosCorridos(clausuraId, 13, anoAtual)
        } catch (_: Exception) { }

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
        inserirNoHallDaFamaComTecnico(HallDaFamaEntity(
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
            titulosNacionais = existing.titulosNacionais + 1
        ))
        // Ranking CONMEBOL: campeão único da liga uruguaia leva os 50 pontos
        registrarPontosConmebol(
            timeId = campeaoId,
            ano = anoAtual,
            competicao = CompeticaoConmebol.LIGA_NACIONAL,
            pontos = RankingConmebol.PONTOS_LIGA_NACIONAL
        )
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
        // Cadência espelhando o Argentino Clausura: avança UMA rodada por chamada.
        // Quando a fase regular termina, apenas geramos a próxima fase (Semi ou
        // Final) via verificarEAvancarPlayoffUruguai e RETORNAMOS — a próxima
        // partida do jogador dispara a simulação dessa fase recém-criada.
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
            // Sem partidas pendentes: tenta gerar a próxima fase do playoff.
            // Não simula imediatamente — deixamos para a próxima chamada (cadenciado).
            verificarEAvancarPlayoffUruguai(clausuraId, aperturaId, intermedidId, anoAtual, timeJogadorId, temporadaId)
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
    private data class SupercopasNovas(val brasil: Int, val inglaterra: Int, val espanha: Int, val italia: Int)

    // Cria as 4 supercopas do rollover num só lugar (mantém encerrarTemporada... enxuto).
    private suspend fun criarSupercopasDaTemporada(ano: Int, novoAno: Int, temporadaId: Int): SupercopasNovas =
        SupercopasNovas(
            brasil     = criarSupercopaDeCampeoes(ano, novoAno, temporadaId, 1, 5, "Supercopa Rei $novoAno", "Brasil"),
            inglaterra = criarSupercopaDeCampeoes(ano, novoAno, temporadaId, 20, 23, "Supercopa da Inglaterra $novoAno", "Inglaterra"),
            espanha    = criarSupercopaDeCampeoes(ano, novoAno, temporadaId, 22, 25, "Supercopa da Espanha $novoAno", "Espanha"),
            italia     = criarSupercopaDeCampeoes(ano, novoAno, temporadaId, 24, 26, "Supercopa da Italia $novoAno", "Italia")
        )

    // Cria a Supercopa de um país: campeão da liga (Hall [ligaDiv]) vs campeão da
    // copa (Hall [copaDiv]) do ano encerrado. Se o mesmo clube venceu ambos, a 2ª
    // vaga vai ao VICE-campeão da liga. Retorna -1 se faltar campeão/participante.
    // Extraído do rollover para manter encerrarTemporadaComHallDaFama sob o limite
    // de bytecode da JVM.
    private suspend fun criarSupercopaDeCampeoes(
        ano: Int, novoAno: Int, temporadaId: Int,
        ligaDiv: Int, copaDiv: Int, nome: String, pais: String
    ): Int {
        val champLiga = hallDaFamaDao.buscarCampeaoPorAnoEDivisao(ano, ligaDiv) ?: return -1
        val champCopa = hallDaFamaDao.buscarCampeaoPorAnoEDivisao(ano, copaDiv) ?: return -1
        val segundo = if (champCopa.campeaoTimeId == champLiga.campeaoTimeId)
            champLiga.viceTimeId else champCopa.campeaoTimeId
        if (segundo <= 0 || segundo == champLiga.campeaoTimeId) return -1
        return criarSupercopa(champLiga.campeaoTimeId, segundo, novoAno, temporadaId, nome, pais)
    }

    suspend fun criarSupercopa(
        campeonatoBrasileiraoId: Int,   // timeId do campeão da liga
        campeonatoCopaId: Int,          // timeId do campeão da copa (ou vice da liga)
        ano: Int,
        temporadaId: Int,
        nome: String = "Supercopa Rei $ano",
        pais: String = "Brasil"
    ): Int {
        val supercopaId = campeonatoDao.inserir(
            CampeonatoEntity(
                temporadaId  = temporadaId,
                nome         = nome,
                tipo         = TipoCampeonato.SUPERCOPA,
                formato      = FormatoCampeonato.MATA_MATA_SIMPLES,
                totalRodadas = 1,
                pais         = pais
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

    // ─────────────────────────────────────────────────────────────
    //  Troféu dos Campeões (Argentina)
    //  Jogo único entre o campeão do Apertura e o campeão do Clausura
    //  da MESMA temporada. Disputado em campo neutro no fim do ano.
    //  - Hall da Fama: divisao = 18
    //  - ordemGlobal = 620 (mapeado para mid-Dezembro no calendário)
    //  - Auto-simulado dentro de encerrarTemporadaComHallDaFama, pois
    //    ocorre depois que ambos os torneios já foram decididos.
    // ─────────────────────────────────────────────────────────────
    suspend fun criarESimularTrofeuCampeoes(
        ano: Int,
        temporadaId: Int,
        timeJogadorId: Int = -1
    ): Int {
        // Busca campeões já registrados no Hall da Fama do ano corrente
        val champApertura = hallDaFamaDao.buscarCampeaoPorAnoEDivisao(ano, 7)
        val champClausura = hallDaFamaDao.buscarCampeaoPorAnoEDivisao(ano, 8)
        if (champApertura == null || champClausura == null) return -1

        // Mesmo time campeão de ambos: troféu sem disputa (não cria nem registra)
        if (champApertura.campeaoTimeId == champClausura.campeaoTimeId) return -1

        // Evita duplicata se função for chamada novamente no mesmo ano
        if (hallDaFamaDao.buscarCampeaoPorAnoEDivisao(ano, 18) != null) return -1

        val nomeCampeonato = "Troféu dos Campeões $ano"

        // Cria o campeonato (jogo único, MATA_MATA_SIMPLES)
        val trofeuId = campeonatoDao.inserir(
            CampeonatoEntity(
                temporadaId  = temporadaId,
                nome         = nomeCampeonato,
                tipo         = TipoCampeonato.TROFEU_CAMPEOES,
                formato      = FormatoCampeonato.MATA_MATA_SIMPLES,
                totalRodadas = 1,
                pais         = "Argentina"
            )
        ).toInt()
        campeonatoDao.inserirParticipantes(listOf(
            CampeonatoTimeEntity(trofeuId, champApertura.campeaoTimeId),
            CampeonatoTimeEntity(trofeuId, champClausura.campeaoTimeId)
        ))

        // Cria a única partida (campeão do Apertura como mandante por convenção)
        val partidaId = partidaDao.inserir(
            PartidaEntity(
                campeonatoId = trofeuId,
                rodada       = 1,
                timeCasaId   = champApertura.campeaoTimeId,
                timeForaId   = champClausura.campeaoTimeId,
                fase         = "Final",
                ordemGlobal  = 620
            )
        ).toInt()

        // Auto-simula a partida (gols no tempo normal)
        val partidaCriada = partidaDao.buscarPorId(partidaId) ?: return trofeuId
        try { simularPartidaInterna(partidaCriada) } catch (_: Exception) { }

        // Determina o vencedor (com pênaltis se empate)
        val partidaJogada = partidaDao.buscarPorId(partidaId) ?: return trofeuId
        val gCasa = partidaJogada.golsCasa ?: 0
        val gFora = partidaJogada.golsFora ?: 0
        val campeaoId = when {
            gCasa > gFora -> partidaJogada.timeCasaId
            gFora > gCasa -> partidaJogada.timeForaId
            else -> {
                val pCasa = partidaJogada.penaltisCasa
                val pFora = partidaJogada.penaltisForaId
                if (pCasa != null && pFora != null) {
                    if (pCasa > pFora) partidaJogada.timeCasaId else partidaJogada.timeForaId
                } else {
                    simularEPersistirPenaltisIA(partidaJogada, partidaJogada)
                }
            }
        }
        val viceId = if (campeaoId == partidaJogada.timeCasaId)
            partidaJogada.timeForaId else partidaJogada.timeCasaId

        val campeao = timeRepository.buscarEntityPorId(campeaoId)
        val vice    = timeRepository.buscarEntityPorId(viceId)
        val artilheiro = partidaDao.buscarArtilheiroTop1(trofeuId)
        val assistente = partidaDao.buscarAssisteTop1(trofeuId)

        // Registra Hall da Fama (divisao = 18 = Troféu dos Campeões)
        inserirNoHallDaFamaComTecnico(
            HallDaFamaEntity(
                ano                  = ano,
                nomeCampeonato       = nomeCampeonato,
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
                divisao              = 18  // 18 = Troféu dos Campeões (ARG)
            )
        )

        // Premiação (R$ 6M campeão, R$ 3M vice) — credita só se for o time do jogador
        if (timeJogadorId > 0) {
            val premio = when (timeJogadorId) {
                campeaoId -> 6_000_000_00L
                viceId    -> 3_000_000_00L
                else      -> 0L
            }
            if (premio > 0L) timeRepository.creditarSaldo(timeJogadorId, premio)
        }

        // Marca campeonato como encerrado
        campeonatoDao.encerrar(trofeuId)

        // Bônus de reputação para o campeão (+2%, similar a Segunda Div.)
        campeao?.let {
            val bonus = (it.reputacao * 0.02).toFloat().coerceAtLeast(0.1f)
            timeRepository.atualizarReputacao(it.id, it.reputacao + bonus)
        }

        return trofeuId
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
        premioVice: Long = 15_000_000_00L,
        // Deslocamento das rodadas do mata-mata. Zero para as copas, cujas fases
        // ocupam as rodadas 1..n; a Champions usa um deslocamento para o seu
        // mata-mata não colidir com as rodadas da Fase de Liga.
        rodadaBase: Int = 0
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

                inserirNoHallDaFamaComTecnico(
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
                // Incrementa copasVencidas do campeão (sem bônus de pontos no ranking geral)
                val rankCampeaoBase = rankingGeralDao.buscarPorTime(campeaoId)
                    ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                        timeId = campeaoId, nomeTime = campeao?.nome ?: "",
                        escudoRes = campeao?.escudoRes ?: "", divisaoAtual = campeao?.divisao ?: 1
                    )
                rankingGeralDao.inserirOuAtualizar(
                    rankCampeaoBase.copy(
                        copasVencidas = rankCampeaoBase.copasVencidas + 1
                    )
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
            val rodadaIda   = rodadaBase + MotorCampeonato.rodadaIdaDeFase(faseIndex)
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

        // Código de divisão do Hall da Fama por país (Supercopa): Brasil=6,
        // Inglaterra=27, Espanha=28, Itália=29.
        val divisaoSupercopa = when (supercopa.pais) {
            "Inglaterra" -> 27
            "Espanha"    -> 28
            "Italia"     -> 29
            else         -> 6
        }

        // Registra no Hall da Fama
        inserirNoHallDaFamaComTecnico(
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
                divisao              = divisaoSupercopa
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
                        descricaoPremio   = "${supercopa.nome} — $posicaoSuper",
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

        // Atualiza ranking geral: +1 copa para o campeão (sem bônus de pontos)
        val rankingCampeaoBase = rankingGeralDao.buscarPorTime(campeaoId)
            ?: br.com.managerfoot.data.database.entities.RankingGeralEntity(
                timeId = campeaoId, nomeTime = campeao?.nome ?: "",
                escudoRes = campeao?.escudoRes ?: "", divisaoAtual = campeao?.divisao ?: 1
            )
        rankingGeralDao.inserirOuAtualizar(
            rankingCampeaoBase.copy(
                nomeTime         = campeao?.nome ?: rankingCampeaoBase.nomeTime,
                escudoRes        = campeao?.escudoRes ?: rankingCampeaoBase.escudoRes,
                copasVencidas    = rankingCampeaoBase.copasVencidas + 1
            )
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
    // O ranking geral NÃO concede bônus por título ou vice-campeonato: conquistas
    // pontuam apenas no Ranking CONMEBOL (ver registrarPontosConmebol).
    suspend fun atualizarRankingGeral(campeonatoId: Int) {
        if (campeonatoId <= 0) return
        val classificacoes = classificacaoDao.buscarTabelaOrdenada(campeonatoId)
        val campeaoTimeId  = classificacoes.firstOrNull()?.timeId
        for (cls in classificacoes) {
            val time      = timeRepository.buscarEntityPorId(cls.timeId) ?: continue
            val existing  = rankingGeralDao.buscarPorTime(cls.timeId)
                ?: RankingGeralEntity(
                    timeId = cls.timeId, nomeTime = time.nome,
                    escudoRes = time.escudoRes, divisaoAtual = time.divisao
                )
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

    // ── Ranking CONMEBOL ────────────────────────────────────────────
    // Segundo ranking, por colocação nas competições (ver ranking_conmebol.md).
    // Cada conquista é um lançamento com o ano em que aconteceu, porque a
    // pontuação por desempenho perde 10% de valor a cada ano que passa.

    // O escopo é o CONTINENTE, não uma lista de países: as regras do
    // ranking_conmebol.md valem para a América do Sul inteira. Europa e os demais
    // continentes terão seus próprios conjuntos de regras, ainda a implementar.
    private suspend fun ehClubeConmebol(timeId: Int): Boolean {
        val time = timeRepository.buscarEntityPorId(timeId) ?: return false
        return Continentes.ehAmericaDoSul(time.pais)
    }

    /** Lança pontos no Ranking CONMEBOL, ignorando clubes de fora da América do Sul. */
    suspend fun registrarPontosConmebol(
        timeId: Int,
        ano: Int,
        competicao: CompeticaoConmebol,
        pontos: Double,
        motivo: String = "Campeão",
        deprecia: Boolean = true
    ) {
        if (timeId <= 0 || pontos <= 0.0) return
        if (!ehClubeConmebol(timeId)) return
        rankingConmebolDao.inserir(
            RankingConmebolEntity(
                timeId = timeId, ano = ano, competicao = competicao.name,
                motivo = motivo, pontos = pontos, deprecia = deprecia
            )
        )
    }

    /**
     * Lança os pontos de uma campanha continental (Libertadores / Sul-Americana):
     * pontos fixos pela fase final atingida + pontos por vitória e empate da fase
     * de grupos em diante.
     *
     * Ainda não há competição continental no jogo; a função já existe para que o
     * ranking passe a pontuar sozinho quando a Libertadores for implementada.
     */
    suspend fun registrarCampanhaContinental(
        timeId: Int,
        ano: Int,
        competicao: CompeticaoConmebol,
        fase: FaseConmebol,
        vitorias: Int = 0,
        empates: Int = 0
    ) {
        registrarPontosConmebol(
            timeId, ano, competicao,
            RankingConmebol.pontosPorFase(competicao, fase),
            motivo = fase.name
        )
        registrarPontosConmebol(
            timeId, ano, competicao,
            RankingConmebol.pontosPorVitoria(competicao) * vitorias,
            motivo = "$vitorias vitória(s)"
        )
        registrarPontosConmebol(
            timeId, ano, competicao,
            RankingConmebol.pontosPorEmpate(competicao) * empates,
            motivo = "$empates empate(s)"
        )
    }

    /**
     * Semeia o coeficiente histórico (bloco 2 do documento: 1960-2014) dos clubes
     * sul-americanos. O jogo não tem passado antes da primeira temporada, então a
     * tradição continental é derivada da reputação do clube no seed.
     *
     * Idempotente: não faz nada se já houver lançamentos históricos no banco.
     */
    suspend fun semearCoeficienteHistoricoConmebol() {
        if (rankingConmebolDao.contarLancamentosHistoricos() > 0) return
        val lancamentos = db.timeDao().buscarTodos()
            .filter { Continentes.ehAmericaDoSul(it.pais) }
            .mapNotNull { time ->
                val pontos = RankingConmebol.coeficienteHistoricoPorReputacao(time.reputacao)
                if (pontos <= 0.0) null
                else RankingConmebolEntity(
                    timeId     = time.id,
                    ano        = RankingConmebol.ANO_HISTORICO,
                    competicao = CompeticaoConmebol.HISTORICO.name,
                    motivo     = "Coeficiente histórico (até 2014)",
                    pontos     = pontos,
                    deprecia   = false
                )
            }
        if (lancamentos.isNotEmpty()) rankingConmebolDao.inserirTodos(lancamentos)
    }

    fun observarRankingConmebol(anoAtual: Int) = rankingConmebolDao.observeRanking(anoAtual)

    // ── Ranking Nacional de Clubes (CBF) ────────────────────────────
    // Conjunto POR PAÍS do Brasil do ranking por colocação (ver ranking_brasileiro.md).
    // Independente do CONMEBOL: janela de 5 anos com pesos inteiros 5/4/3/2/1, e só
    // pontuam o Campeonato Brasileiro (na série disputada) e a Copa do Brasil.

    // O escopo é o PAÍS, não o continente: um clube argentino na Copa do Brasil
    // (hipótese futura) não entraria no RNC.
    private suspend fun ehClubeBrasileiro(timeId: Int): Boolean {
        val time = timeRepository.buscarEntityPorId(timeId) ?: return false
        return Continentes.ehBrasil(time.pais)
    }

    /** Lança pontos do RNC, ignorando clubes de fora do Brasil. */
    private suspend fun registrarPontosBrasileiro(
        timeId: Int,
        ano: Int,
        competicao: CompeticaoRnc,
        pontos: Int,
        motivo: String
    ) {
        if (timeId <= 0 || pontos <= 0) return
        if (!ehClubeBrasileiro(timeId)) return
        rankingBrasileiroDao.inserir(
            RankingBrasileiroEntity(
                timeId = timeId, ano = ano, competicao = competicao.name,
                motivo = motivo, pontos = pontos
            )
        )
    }

    /**
     * Pontua uma série do Campeonato Brasileiro no RNC, pela posição final de cada
     * clube na tabela. A escala percentual é a mesma nas quatro séries; muda a base
     * (800/400/200/100).
     *
     * Apaga os lançamentos daquele ano/série antes de inserir, para que reencerrar
     * a mesma temporada não duplique a pontuação.
     */
    suspend fun registrarColocacaoBrasileirao(campeonatoId: Int, ano: Int, divisao: Int) {
        if (campeonatoId <= 0) return
        val serie = RankingBrasileiro.serieDaDivisao(divisao) ?: return
        rankingBrasileiroDao.apagarLancamentos(ano, serie.name)
        val classificacoes = classificacaoDao.buscarTabelaOrdenada(campeonatoId)
        classificacoes.forEachIndexed { indice, cls ->
            val posicao = indice + 1
            registrarPontosBrasileiro(
                timeId     = cls.timeId,
                ano        = ano,
                competicao = serie,
                pontos     = RankingBrasileiro.pontosPorPosicao(serie, posicao),
                motivo     = "${serie.rotulo} · ${posicao}º"
            )
        }
    }

    /**
     * Pontua a Copa do Brasil no RNC, estritamente pela fase máxima que cada clube
     * alcançou: todos os eliminados na mesma fase recebem a mesma pontuação.
     *
     * A fase máxima não está registrada em lugar nenhum — o Hall da Fama guarda só
     * campeão e vice — então é derivada de PartidaEntity.fase: para cada clube, a
     * fase mais avançada em que ele entrou em campo. Quem disputou a Final é campeão
     * ou vice conforme o resultado da decisão.
     */
    suspend fun registrarCampanhaCopaDoBrasil(copaId: Int, ano: Int) {
        if (copaId <= 0) return
        rankingBrasileiroDao.apagarLancamentos(ano, CompeticaoRnc.COPA_DO_BRASIL.name)

        val partidas = partidaDao.buscarTodasPorCampeonato(copaId).filter { it.jogada }
        if (partidas.isEmpty()) return

        // Fase mais avançada por clube, pela ordem de MotorCampeonato.COPA_FASES.
        val faseMaxima = mutableMapOf<Int, Int>()
        for (p in partidas) {
            val nomeFase = p.fase ?: continue
            val indice = MotorCampeonato.COPA_FASES.indexOf(nomeFase)
            if (indice < 0) continue
            for (timeId in listOf(p.timeCasaId, p.timeForaId)) {
                if ((faseMaxima[timeId] ?: -1) < indice) faseMaxima[timeId] = indice
            }
        }
        if (faseMaxima.isEmpty()) return

        val campeaoId = determinarCampeaoCopa(copaId)

        for ((timeId, indice) in faseMaxima) {
            val nomeFase = MotorCampeonato.COPA_FASES.getOrNull(indice) ?: continue
            // faseDaCopaPorNome devolve VICE para a Final; o campeão é promovido aqui.
            val fase = RankingBrasileiro.faseDaCopaPorNome(nomeFase) ?: continue
            val faseFinal = if (fase == FaseCopaDoBrasil.VICE && timeId == campeaoId)
                FaseCopaDoBrasil.CAMPEAO else fase
            registrarPontosBrasileiro(
                timeId     = timeId,
                ano        = ano,
                competicao = CompeticaoRnc.COPA_DO_BRASIL,
                pontos     = RankingBrasileiro.pontosCopaDoBrasil(faseFinal),
                motivo     = "Copa do Brasil · ${faseFinal.rotulo}"
            )
        }
    }

    /**
     * Campeão de uma copa ida/volta a partir das partidas da Final, sem exigir que a
     * competição já esteja marcada como encerrada — diferente de
     * [buscarCampeaoCopaSeEncerrada], que é usada pela UI da tabela.
     */
    private suspend fun determinarCampeaoCopa(copaId: Int): Int {
        val finais = partidaDao.buscarTodasPorCampeonato(copaId)
            .filter { it.fase == "Final" && it.jogada }
        if (finais.isEmpty()) return -1
        val ida   = finais.minByOrNull { it.rodada } ?: return -1
        val volta = finais.maxByOrNull { it.rodada } ?: return -1
        if (ida.id == volta.id) {
            val gC = ida.golsCasa ?: return -1
            val gF = ida.golsFora ?: return -1
            return when {
                gC > gF -> ida.timeCasaId
                gF > gC -> ida.timeForaId
                (ida.penaltisCasa ?: 0) > (ida.penaltisForaId ?: 0) -> ida.timeCasaId
                (ida.penaltisForaId ?: 0) > (ida.penaltisCasa ?: 0) -> ida.timeForaId
                else -> -1
            }
        }
        MotorCampeonato.determinarVencedorTie(
            timeCasaIdaId = ida.timeCasaId,
            timeForaIdaId = ida.timeForaId,
            golsCasaIda   = ida.golsCasa   ?: 0,
            golsForaIda   = ida.golsFora   ?: 0,
            golsCasaVolta = volta.golsCasa ?: 0,
            golsForaVolta = volta.golsFora ?: 0
        )?.let { return it }
        val pCasa = volta.penaltisCasa ?: return -1
        val pFora = volta.penaltisForaId ?: return -1
        return if (pCasa > pFora) volta.timeCasaId else volta.timeForaId
    }

    /**
     * Lança no fim da temporada tudo o que os dois conjuntos de regras do ranking
     * por colocação pontuam a partir das competições brasileiras:
     *
     *  - CONMEBOL (continente): bônus de campeão de liga nacional da Série A. O
     *    Brasileirão é a única liga sul-americana de campeão único aqui; Argentina
     *    (Apertura/Clausura) e Uruguai pontuam nos seus próprios fluxos.
     *  - RNC da CBF (país): colocação nas quatro séries + campanha na Copa do Brasil.
     *
     * Extraído em helper porque [encerrarTemporadaComHallDaFama] vive no limite de
     * bytecode do método da JVM.
     */
    private suspend fun registrarRankingsPorColocacaoDaTemporada(
        campeonatoAId: Int,
        campeonatoBId: Int,
        campeonatoCId: Int,
        campeonatoDId: Int,
        temporadaId: Int,
        ano: Int
    ) {
        if (campeonatoAId > 0) {
            classificacaoDao.buscarTabelaOrdenada(campeonatoAId).firstOrNull()?.let { campeao ->
                registrarPontosConmebol(
                    timeId = campeao.timeId,
                    ano = ano,
                    competicao = CompeticaoConmebol.LIGA_NACIONAL,
                    pontos = RankingConmebol.PONTOS_LIGA_NACIONAL
                )
            }
        }
        registrarTemporadaNoRankingBrasileiro(
            campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, temporadaId, ano
        )
    }

    /**
     * Pontua no RNC tudo o que a temporada que está terminando rendeu: as quatro
     * séries do Brasileirão e a Copa do Brasil.
     *
     * A Copa do Brasil não é parâmetro de [encerrarTemporadaComHallDaFama], então é
     * localizada pela temporada: o único COPA_NACIONAL sem país próprio, cujo nome
     * começa com "Copa do Brasil" (as copas estrangeiras gravam `pais`).
     */
    private suspend fun registrarTemporadaNoRankingBrasileiro(
        campeonatoAId: Int,
        campeonatoBId: Int,
        campeonatoCId: Int,
        campeonatoDId: Int,
        temporadaId: Int,
        ano: Int
    ) {
        registrarColocacaoBrasileirao(campeonatoAId, ano, 1)
        registrarColocacaoBrasileirao(campeonatoBId, ano, 2)
        registrarColocacaoBrasileirao(campeonatoCId, ano, 3)
        registrarColocacaoBrasileirao(campeonatoDId, ano, 4)

        val copaDoBrasil = campeonatoDao.buscarPorTemporadaId(temporadaId)
            .firstOrNull {
                it.tipo == TipoCampeonato.COPA_NACIONAL && it.nome.startsWith("Copa do Brasil")
            }
        if (copaDoBrasil != null) registrarCampanhaCopaDoBrasil(copaDoBrasil.id, ano)
    }

    fun observarRankingBrasileiro(anoAtual: Int) = rankingBrasileiroDao.observeRanking(anoAtual)

    // ── Ranking por colocação mundial (IFFHS) ───────────────────────
    // Conjunto de regras do recorte MUNDO (ver ranking_mundial.md): janela de 12
    // meses, soma por partida pelo peso do torneio, sem recência.
    //
    // Único dos quatro SEM tabela de lançamentos: a pontuação é derivada das
    // partidas já gravadas, na leitura. O motivo é que `partidas` guarda toda
    // partida oficial de todas as temporadas — só amistosos são apagados, e eles
    // não pontuam — com tudo que a regra precisa: resultado, fase, ordemGlobal, e
    // o tipo e o país via campeonatos. Derivar em vez de acumular dá três coisas:
    // nenhuma migração, nenhuma escrita por partida dentro do laço de simulação, e
    // recálculo automático se os níveis de liga mudarem, como mandam as regras.

    /**
     * Ranking mundial consolidado. Combina o relógio da janela (maior ordemGlobal
     * jogado na temporada) com as campanhas agregadas, aplica o peso de cada
     * competição em Kotlin e soma por clube.
     */
    fun observarRankingMundial(anoAtual: Int): Flow<List<RankingMundialLinha>> =
        partidaDao.observeOrdemGlobalAtual(anoAtual)
            .flatMapLatest { ordemGlobalAtual ->
                partidaDao.observeCampanhasMundial(anoAtual, ordemGlobalAtual)
            }
            .map { campanhas -> consolidarRankingMundial(campanhas) }

    private fun consolidarRankingMundial(
        campanhas: List<CampanhaMundialDto>
    ): List<RankingMundialLinha> {
        val acumulado = mutableMapOf<Int, RankingMundialLinha>()
        for (c in campanhas) {
            val peso = RankingMundial.pesoDaVitoria(
                tipo = c.tipo,
                paisCampeonato = c.paisCampeonato,
                nomeCampeonato = c.nomeCampeonato,
                fase = c.fase
            ) ?: continue   // competição que não pontua, ou fase anterior às oitavas
            val pontos = RankingMundial.pontos(peso, c.vitorias, c.empates)
            if (pontos <= 0.0) continue

            val atual = acumulado[c.timeId] ?: RankingMundialLinha(
                timeId = c.timeId, nomeTime = c.nomeTime,
                escudoRes = c.escudoRes, pais = c.pais
            )
            val ehLiga = RankingMundial.profundidadeDaDivisao(c.tipo) != null
            val ehInternacional = c.tipo == TipoCampeonato.CONTINENTAL ||
                c.tipo == TipoCampeonato.MUNDIAL_CLUBES
            acumulado[c.timeId] = atual.copy(
                pontos             = atual.pontos + pontos,
                pontosLiga         = atual.pontosLiga + if (ehLiga) pontos else 0.0,
                pontosCopa         = atual.pontosCopa +
                    if (c.tipo == TipoCampeonato.COPA_NACIONAL) pontos else 0.0,
                pontosInternacional = atual.pontosInternacional + if (ehInternacional) pontos else 0.0,
                jogos              = atual.jogos + c.vitorias + c.empates
            )
        }
        return acumulado.values
            .sortedWith(compareByDescending<RankingMundialLinha> { it.pontos }.thenBy { it.nomeTime })
    }

    // ── Ranking por colocação da Europa (Coeficiente da UEFA) ───────
    // Conjunto de regras do recorte EUROPA (ver ranking_europa.md): janela de 5
    // temporadas sem pesos, pontuando só Champions, Europa e Conference League.
    //
    // ⚠️ DORMENTE: nenhuma competição da UEFA existe no jogo, então não há de onde
    // tirar pontos e o ranking fica vazio. Ligas e copas nacionais não pontuam aqui,
    // por regra. A função de registro abaixo já está pronta para o dia em que as
    // competições existirem — é só chamá-la ao encerrar cada uma.

    /**
     * Registra a campanha de um clube numa competição da UEFA. Ignora clubes de fora
     * da Europa. Apaga antes o lançamento daquele ano/competição, para que reencerrar
     * a mesma temporada não duplique a pontuação.
     */
    suspend fun registrarCampanhaEuropeia(
        timeId: Int,
        ano: Int,
        competicao: CompeticaoUefa,
        posicaoFaseLiga: Int,
        vitorias: Int,
        empates: Int,
        faseAlcancada: FaseUefa? = null
    ) {
        if (timeId <= 0) return
        val time = timeRepository.buscarEntityPorId(timeId) ?: return
        if (!Continentes.ehEuropa(time.pais)) return
        val pontos = RankingEuropa.pontosDaCampanha(
            competicao, posicaoFaseLiga, vitorias, empates, faseAlcancada
        )
        if (pontos <= 0.0) return
        rankingEuropaDao.inserir(
            RankingEuropaEntity(
                timeId     = timeId,
                ano        = ano,
                competicao = competicao.name,
                motivo     = montarMotivoUefa(posicaoFaseLiga, faseAlcancada),
                pontos     = pontos
            )
        )
    }

    /**
     * Registra a eliminação na qualificação, que rende apenas a consolação fixa da
     * rodada: partidas de qualificação não dão pontos por jogo.
     */
    suspend fun registrarQualificacaoEuropeia(
        timeId: Int,
        ano: Int,
        competicao: CompeticaoUefa,
        fase: FaseQualificatoriaUefa
    ) {
        if (timeId <= 0) return
        val time = timeRepository.buscarEntityPorId(timeId) ?: return
        if (!Continentes.ehEuropa(time.pais)) return
        rankingEuropaDao.inserir(
            RankingEuropaEntity(
                timeId     = timeId,
                ano        = ano,
                competicao = competicao.name,
                motivo     = "Eliminado na ${fase.rotulo}",
                pontos     = RankingEuropa.pontosDaQualificacao(fase)
            )
        )
    }

    private fun montarMotivoUefa(posicaoFaseLiga: Int, faseAlcancada: FaseUefa?): String {
        val posicao = if (posicaoFaseLiga > 0) "${posicaoFaseLiga}º na Fase de Liga" else "Fase de Liga"
        return if (faseAlcancada == null) posicao else "$posicao, ${faseAlcancada.rotulo}"
    }

    /**
     * Ranking europeu consolidado. O DAO devolve a soma bruta da janela por clube; o
     * piso de 20% do país é aplicado aqui, em Kotlin, porque depende do coeficiente da
     * associação — a média dos clubes daquele país — e fazer isso em SQL exigiria função
     * de janela, cuja disponibilidade varia com a versão do Android.
     *
     * Clubes que continuam em zero depois do piso não entram no ranking.
     */
    fun observarRankingEuropa(anoAtual: Int): Flow<List<RankingEuropaLinha>> =
        rankingEuropaDao.observeRankingBruto(anoAtual, RankingEuropa.DIVISOES_ELITE)
            .map { linhas ->
                val coeficientePorPais = linhas
                    .groupBy { it.pais }
                    .mapValues { (_, clubes) ->
                        RankingEuropa.coeficienteDoPais(clubes.map { it.pontosBrutos })
                    }
                linhas
                    .map { linha ->
                        val piso = coeficientePorPais[linha.pais] ?: 0.0
                        linha.copy(
                            pontosBrutos = RankingEuropa.aplicarPisoDoPais(linha.pontosBrutos, piso)
                        )
                    }
                    .filter { it.pontosBrutos > 0.0 }
                    .sortedWith(compareByDescending<RankingEuropaLinha> { it.pontosBrutos }
                        .thenBy { it.nomeTime })
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
                .filter { it.timeId !in serieASet && it.divisaoAtual in 1..4 } // apenas times brasileiros (exclui Argentina e Uruguai)
                .take(64 - participantesSerieA.size)
                .map { it.timeId }
            (participantesSerieA + top44).take(64)
        }
    }

    suspend fun fecharMes(timeId: Int, temporadaId: Int, mes: Int, patrocinioMensal: Long = 0L, patrocinioJaCreditado: Long = 0L, anoAtual: Int = 0) {
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
                    // Preserva despesas pontuais já lançadas no mês (base/estádio) caso o
                    // fechamento reutilize a mesma linha — evita perder o registro.
                    despesaBase             = existente?.despesaBase ?: 0L,
                    despesaAmpliacaoEstadio = existente?.despesaAmpliacaoEstadio ?: 0L,
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

        // Devolve ao clube de origem jogadores cujo empréstimo expirou neste mês
        if (anoAtual > 0) {
            jogadorRepository.processarRetornosEmprestimo(anoAtual, mes, temporadaId)
        }
        // ── Set global de jogadores já negociados neste mês ──
        // Compartilhado entre TODAS as sub-rotinas de IA do mercado para impedir
        // que o mesmo jogador seja transferido várias vezes na mesma janela
        // (bug onde Time A vendia → mercado livre → Time B comprava de novo).
        val jogadoresNegociadosNoMes = mutableSetOf<Int>()

        // IA contrata jogadores do mercado livre (cada time IA verifica necessidades)
        executarContratacoesLivresIA(timeId, temporadaId, mes, jogadoresNegociadosNoMes)
        // IA avalia reforço mensal: tenta melhorar a posição mais fraca do time titular
        executarReforcoMensalIA(timeId, temporadaId, mes, jogadoresNegociadosNoMes)
        // IA faz reforços emergenciais quando o time está com desempenho ruim nos campeonatos
        executarReforcoDesempenhoIA(timeId, temporadaId, mes, jogadoresNegociadosNoMes)
        // Nos meses de início (1-2) e final (10-12) da temporada, gera propostas de
        // times da IA para jogadores do time do usuário
        if (mes in setOf(1, 2, 10, 11, 12)) {
            gerarPropostasIAParaTimeJogador(timeId, temporadaId, mes, jogadoresNegociadosNoMes)
            executarTransferenciasIAParaIA(timeId, temporadaId, mes, jogadoresNegociadosNoMes)
        }
        // Troca de técnicos no meio da temporada: clubes na zona de risco com
        // resultados ruins podem demitir e contratar um substituto livre.
        if (anoAtual > 0) {
            try { processarTrocaTecnicosMeioTemporada(timeId, anoAtual) } catch (_: Exception) { }
        }
    }

    /**
     * Troca natural de técnicos no MEIO da temporada. Para cada liga nacional ativa,
     * os times nas ÚLTIMAS posições (zona de risco) com aproveitamento ruim e jogos
     * suficientes têm chance de demitir o técnico e contratar um substituto livre.
     * O time do usuário é preservado. A troca só ocorre se houver substituto adequado.
     */
    private suspend fun processarTrocaTecnicosMeioTemporada(playerTimeId: Int, anoAtual: Int) {
        val tiposCopa = setOf(TipoCampeonato.COPA_NACIONAL, TipoCampeonato.CONTINENTAL)
        val ligas = campeonatoDao.buscarAtivos().filter { it.tipo !in tiposCopa }
        for (liga in ligas) {
            val tabela = classificacaoDao.buscarTabelaOrdenada(liga.id)
            val totalTimes = tabela.size
            if (totalTimes < 6) continue
            val inicioZonaRisco = totalTimes - 3   // últimas 3 posições
            tabela.forEachIndexed { idx, classi ->
                if (classi.timeId == playerTimeId) return@forEachIndexed  // usuário decide
                if (idx < inicioZonaRisco) return@forEachIndexed           // fora da zona de risco
                if (classi.jogos < 8) return@forEachIndexed                // cedo demais na temporada
                if (classi.aproveitamento >= 0.30f) return@forEachIndexed  // desempenho não tão ruim
                // Probabilidade mensal — mantém o movimento gradual/realista.
                if (kotlin.random.Random.nextFloat() > 0.25f) return@forEachIndexed
                try { tecnicoRepository.trocarTecnicoPorCrise(classi.timeId, anoAtual) } catch (_: Exception) { }
            }
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
    suspend fun gerarPropostasIAParaTimeJogador(
        playerTimeId: Int,
        temporadaId: Int,
        mes: Int,
        jogadoresMovidos: MutableSet<Int> = mutableSetOf()
    ) {
        val elenco = jogadorRepository.buscarDisponiveis(playerTimeId)
        if (elenco.isEmpty()) return
        val todos = timeRepository.buscarTodosOrdenadosPorReputacao().filter { it.id != playerTimeId }
        if (todos.isEmpty()) return

        // ── Caso 1: proposta espontânea para os melhores jogadores ──────────────
        val candidatos = elenco
            .filter { !it.lesionado && !it.categoriaBase }
            .filter { it.id !in jogadoresMovidos }
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
            // Bloqueia outras IA-to-IA do mesmo ciclo de tentar mover este jogador
            jogadoresMovidos.add(jogador.id)
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
     * Aceita uma proposta: realiza a transferência (venda ou empréstimo) imediatamente,
     * credita o saldo ao time do jogador e debita do time comprador.
     * Para empréstimos, [anoAtual] é usado para calcular a data de retorno (12 meses).
     */
    suspend fun aceitarProposta(
        proposta: PropostaIAEntity,
        playerTimeId: Int,
        temporadaId: Int,
        mes: Int,
        anoAtual: Int = 0
    ) {
        val jogador = jogadorRepository.buscarPorId(proposta.jogadorId) ?: return
        timeRepository.creditarSaldo(playerTimeId, proposta.valorOfertado)
        timeRepository.debitarSaldo(proposta.timeCompradorId, proposta.valorOfertado)

        val oferta = OfertaTransferencia(
            jogadorId       = jogador.id,
            timeCompradorId = proposta.timeCompradorId,
            timeVendedorId  = playerTimeId,
            valor           = proposta.valorOfertado,
            salarioProposto = jogador.salario,
            contratoAnos    = 3
        )

        if (proposta.tipoProposta == TipoProposta.EMPRESTIMO) {
            val anoRetorno = anoAtual + 1
            val mesRetorno = mes
            jogadorRepository.realizarEmprestimo(
                oferta      = oferta,
                temporadaId = temporadaId,
                mes         = mes,
                anoRetorno  = anoRetorno,
                mesRetorno  = mesRetorno
            )
        } else {
            jogadorRepository.realizarVenda(
                oferta      = oferta,
                temporadaId = temporadaId,
                mes         = mes
            )
        }
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
    private suspend fun executarContratacoesLivresIA(
        playerTimeId: Int,
        temporadaId: Int,
        mes: Int,
        jogadoresMovidos: MutableSet<Int>
    ) {
        val todos = timeRepository.buscarTodosOrdenadosPorReputacao()
        val mercadoLivre = jogadorRepository.buscarMercado(limite = 40)
        if (mercadoLivre.isEmpty()) return

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
    private suspend fun executarReforcoMensalIA(
        playerTimeId: Int,
        temporadaId: Int,
        mes: Int,
        jogadoresMovidos: MutableSet<Int>
    ) {
        val todos  = timeRepository.buscarTodosOrdenadosPorReputacao()
        val livres = jogadorRepository.buscarMercado(limite = 60).filter { it.timeId == null }
        if (livres.isEmpty()) return

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
    private suspend fun executarReforcoDesempenhoIA(
        playerTimeId: Int,
        temporadaId: Int,
        mes: Int,
        jogadoresMovidos: MutableSet<Int>
    ) {
        val tiposCopa = setOf(
            TipoCampeonato.COPA_NACIONAL,
            TipoCampeonato.CONTINENTAL
        )
        val ligas = campeonatoDao.buscarAtivos().filter { it.tipo !in tiposCopa }
        if (ligas.isEmpty()) return

        val livres = jogadorRepository.buscarMercado(limite = 80)
            .filter { it.timeId == null && it.id !in jogadoresMovidos }
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
                    jogadoresMovidos.add(candidato.id)
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
                        jogadoresMovidos.add(candidato.id)
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

    private suspend fun simularPartidaInterna(partida: PartidaEntity): ResultadoPartida {
        val escalacaoCasa = gerarEscalacaoIA(partida.timeCasaId)
        val escalacaoFora = gerarEscalacaoIA(partida.timeForaId)
        val tipoCamp = campeonatoDao.buscarPorId(partida.campeonatoId)?.tipo
        val campoNeutro = tipoCamp == TipoCampeonato.SUPERCOPA
                       || tipoCamp == TipoCampeonato.TROFEU_CAMPEOES
                       || partida.fase == "Final"

        val resultado = simulador.simular(
            partidaId   = partida.id,
            casa        = escalacaoCasa,
            fora        = escalacaoFora,
            campoNeutro = campoNeutro
        )
        persistirResultado(resultado)
        return resultado
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
        goleiroJogadorDefesa: Int,
        // Parâmetros de avanço de fase — default = Copa do Brasil (retrocompat).
        fases: List<String> = MotorCampeonato.COPA_FASES,
        ordemGlobal: IntArray = MotorCampeonato.COPA_ORDEM_GLOBAL,
        divisaoHallDaFama: Int = 5,
        premioCampeao: Long = 30_000_000_00L,
        premioVice: Long = 15_000_000_00L,
        temporadaId: Int = 1,
        /**
         * Avanço alternativo, para competições cujo chaveamento o motor de copa
         * não conhece — a Champions passa antes pelo play-off e pelo cruzamento
         * das oitavas. Quando nulo, avança como copa ida/volta.
         */
        avancoAlternativo: (suspend () -> Unit)? = null
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
        if (avancoAlternativo != null) avancoAlternativo()
        else verificarEAvancarFaseCopa(copaId, anoAtual, timeJogadorId, temporadaId, fases, ordemGlobal, divisaoHallDaFama, premioCampeao, premioVice)
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
        voltaPartidaId: Int,
        // Parâmetros de avanço de fase — default = Copa do Brasil (retrocompat).
        fases: List<String> = MotorCampeonato.COPA_FASES,
        ordemGlobal: IntArray = MotorCampeonato.COPA_ORDEM_GLOBAL,
        divisaoHallDaFama: Int = 5,
        premioCampeao: Long = 30_000_000_00L,
        premioVice: Long = 15_000_000_00L,
        timeJogadorId: Int = -1,
        temporadaId: Int = 1
    ) {
        partidaDao.registrarPenaltis(voltaPartidaId, resultado.golsCasa, resultado.golsFora)
        verificarEAvancarFaseCopa(copaId, anoAtual, timeJogadorId, temporadaId, fases, ordemGlobal, divisaoHallDaFama, premioCampeao, premioVice)
    }

    /**
     * Persiste pênaltis da UI interativa e avança a Champions. Não dá para usar
     * [persistirResultadoPenaltisJogador]: o avanço da Champions passa antes pelo
     * play-off e pelo cruzamento das oitavas, que o motor de copa não conhece.
     */
    suspend fun persistirResultadoPenaltisJogadorChampions(
        resultado: ResultadoPenaltis,
        championsId: Int,
        anoAtual: Int,
        voltaPartidaId: Int,
        timeJogadorId: Int,
        temporadaId: Int
    ) {
        partidaDao.registrarPenaltis(voltaPartidaId, resultado.golsCasa, resultado.golsFora)
        verificarEAvancarFaseChampions(championsId, anoAtual, timeJogadorId, temporadaId)
    }

    /** O mesmo para a Europa League, que tem a mesma máquina de fases. */
    suspend fun persistirResultadoPenaltisJogadorEuropa(
        resultado: ResultadoPenaltis,
        europaLeagueId: Int,
        anoAtual: Int,
        voltaPartidaId: Int,
        timeJogadorId: Int,
        temporadaId: Int
    ) {
        partidaDao.registrarPenaltis(voltaPartidaId, resultado.golsCasa, resultado.golsFora)
        verificarEAvancarFaseEuropa(europaLeagueId, anoAtual, timeJogadorId, temporadaId)
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

    /** Persiste pênaltis da UI interativa e avança a fase da Copa da Inglaterra (jogo único). */
    suspend fun persistirResultadoPenaltisJogadorCopaInglaterra(
        resultado: ResultadoPenaltis,
        copaId: Int,
        anoAtual: Int,
        partidaId: Int,
        timeJogadorId: Int,
        temporadaId: Int
    ) {
        partidaDao.registrarPenaltis(partidaId, resultado.golsCasa, resultado.golsFora)
        verificarEAvancarFaseCopaInglaterra(copaId, anoAtual, timeJogadorId, temporadaId)
    }

    /** Simula pênaltis com cobradores do jogador e avança a fase da Copa da Inglaterra. */
    suspend fun simularEPersistirPenaltisJogadorCopaInglaterra(
        copaId: Int,
        anoAtual: Int,
        partidaId: Int,
        cobradoresJogador: List<JogadorNaEscalacao>,
        timeJogadorId: Int,
        goleiroJogadorDefesa: Int,
        temporadaId: Int
    ): ResultadoPenaltis {
        val todasPartidas = partidaDao.buscarTodasPorCampeonato(copaId)
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
        verificarEAvancarFaseCopaInglaterra(copaId, anoAtual, timeJogadorId, temporadaId)
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
            // Filtra jogadores indisponíveis (lesionados ou suspensos) da escalação salva.
            val titularesDisp = titularesSalvos.filter { !it.lesionado && !it.suspenso }
            val reservasDisp  = reservasSalvas.filter  { !it.lesionado && !it.suspenso }
            val idsUsados = (titularesDisp + reservasDisp).map { it.id }.toSet()

            // Se sobrar menos de 11 titulares, completa automaticamente com jogadores
            // disponíveis do elenco (mesmo critério da IA: melhores primeiro por setor).
            val titularesFinais = if (titularesDisp.size < 11) {
                val complementoPool = jogadorRepository.buscarDisponiveis(timeId)
                    .filter { it.id !in idsUsados }
                val complemento = IATimeRival.gerarEscalacao(time, complementoPool)
                    .titulares
                    .filter { it.jogador.id !in idsUsados }
                    .take(11 - titularesDisp.size)
                titularesDisp.map { j -> JogadorNaEscalacao(j, j.posicaoEscalado ?: j.posicao) } +
                    complemento
            } else {
                titularesDisp.map { j -> JogadorNaEscalacao(j, j.posicaoEscalado ?: j.posicao) }
            }
            val reservas = reservasDisp.map { j -> JogadorNaEscalacao(j, j.posicaoEscalado ?: j.posicao) }
            return Escalacao(time = time, titulares = titularesFinais, reservas = reservas)
        }
        val elenco = jogadorRepository.buscarDisponiveis(timeId)
        return IATimeRival.gerarEscalacao(time, elenco)
    }

    private suspend fun persistirResultado(resultado: ResultadoPartida): Map<Int, Float> {
        // Calcula público e receita para partidas em casa.
        // adversarioNivel = nível real do time visitante (clássico contra time grande
        // = +25% receita; partida fraca contra time pequeno = -15%).
        val timeCasa = timeRepository.buscarPorId(resultado.timeCasaId)
        val timeFora = timeRepository.buscarPorId(resultado.timeForaId)
        val (torcedores, receita) = if (timeCasa != null) {
            MotorFinanceiro.calcularPublico(
                time = timeCasa,
                adversarioNivel = timeFora?.nivel ?: 5,
                adversario = timeFora
            )
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

        // Registra V/E/D para os técnicos dos dois times — ranking de técnicos
        try {
            tecnicoRepository.registrarResultadoPartida(
                timeCasaId = resultado.timeCasaId,
                timeForaId = resultado.timeForaId,
                golsCasa   = resultado.golsCasa,
                golsFora   = resultado.golsFora
            )
        } catch (_: Exception) { }

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
        val timesEnvolvidos = setOf(resultado.timeCasaId, resultado.timeForaId)
        jogadorRepository.atualizarFadigaAposPartida(
            participantes = participantesIds,
            timeIds       = timesEnvolvidos
        )

        // Processa cartões e suspensões:
        //  - acumula amarelos (3 no ciclo → 1 partida) e vermelhos (1 partida imediata);
        //  - decrementa suspensões pendentes dos jogadores que ficaram fora desta partida.
        val amarelosPorJogador = resultado.eventos
            .filter { it.tipo == TipoEvento.CARTAO_AMARELO && it.jogadorId > 0 }
            .groupingBy { it.jogadorId }.eachCount()
        val vermelhosPorJogador = resultado.eventos
            .filter { it.tipo == TipoEvento.CARTAO_VERMELHO && it.jogadorId > 0 }
            .groupingBy { it.jogadorId }.eachCount()
        jogadorRepository.processarCartoesESuspensoes(
            amarelosPorJogador = amarelosPorJogador,
            vermelhosPorJogador = vermelhosPorJogador,
            participantes = participantesIds,
            timeIds = timesEnvolvidos
        )

        // Atualiza moral do elenco com base nas últimas 5 partidas de cada time.
        // 4+ vitórias → EXCELENTE | 3 vitórias → BOM | 3 derrotas → INSATISFEITO |
        // 4+ derrotas → REVOLTADO | caso contrário → NORMAL. Impacta força efetiva.
        timesEnvolvidos.forEach { timeId ->
            jogadorRepository.atualizarMoraleTime(timeId)
        }

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
        golsTime: Int,
        titsTime: List<JogadorNaEscalacao>,
        posse: Int,
        rng: kotlin.random.Random
    ): EstatisticasTime {
        val amarelos   = eventos.count { it.tipo == TipoEvento.CARTAO_AMARELO  && it.timeId == timeId }
        val vermelhos  = eventos.count { it.tipo == TipoEvento.CARTAO_VERMELHO && it.timeId == timeId }
        val defesas    = eventos.count { it.tipo == TipoEvento.DEFESA_GOLEIRO  && it.timeId == timeId }
        val defAdv     = eventos.count { it.tipo == TipoEvento.DEFESA_GOLEIRO  && it.timeId == adversarioId }
        // chutes no alvo por este time = defesas do adversário + gols marcados (100% dos eventos reais)
        val chutesNoGol = defAdv + golsTime
        // Total de finalizações: as no alvo representam ~30-38% do total (média real ~33%).
        // Deriva o total escalando as no alvo, com um piso ofensivo ligado à posse para
        // não ficar irreal em jogos travados. Antes: chutesNoGol*2 travava o alvo em 50%.
        val onTargetPct  = rng.nextInt(30, 39)
        val porAlvo      = if (onTargetPct > 0) chutesNoGol * 100 / onTargetPct else chutesNoGol
        val pisoOfensivo = 6 + posse / 12                       // posse 50 → ~10 finalizações
        val chutes       = maxOf(porAlvo, pisoOfensivo, chutesNoGol).coerceIn(chutesNoGol, 26)
        // Usa a mesma fórmula da engine (SimuladorPartida.calcularFaltas) — evita
        // divergência entre estatísticas persistidas e as recém-simuladas.
        val faltas      = SimuladorPartida.calcularFaltas(amarelos, vermelhos)
        val precisao    = SimuladorPartida.estimarPrecisaoPasse(titsTime, posse, rng)
        return EstatisticasTime(
            chutes         = chutes,
            chutesNoGol    = chutesNoGol,
            posse          = posse,
            faltas         = faltas,
            cartaoAmarelo  = amarelos,
            cartaoVermelho = vermelhos,
            defesasGoleiro = defesas,
            precisaoPasse  = precisao
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
            // Penalidade do goleiro por gols sofridos — contextualizada pela pressão sofrida.
            //   ≥6 chutes no alvo → goleiro sob bombardeio, cada gol dói menos (-0.6)
            //   3-5 chutes         → pressão média (-0.8)
            //   ≤2 chutes          → gols em poucas finalizações são falha grave (-1.2)
            // Já existe bônus de +0.6 por defesa (DEFESA_GOLEIRO), que naturalmente premia
            // um goleiro trabalhador mesmo quando sofre gols.
            fun penaltyPorGol(chutesAlvoAdv: Int): Float = when {
                chutesAlvoAdv >= 6 -> 0.6f
                chutesAlvoAdv >= 3 -> 0.8f
                else                -> 1.2f
            }
            if (jogadorId == resultado.gkCasaId) {
                nota -= resultado.golsFora * penaltyPorGol(resultado.estatisticasFora.chutesNoGol)
            }
            if (jogadorId == resultado.gkForaId) {
                nota -= resultado.golsCasa * penaltyPorGol(resultado.estatisticasCasa.chutesNoGol)
            }
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
    private suspend fun executarTransferenciasIAParaIA(
        playerTimeId: Int,
        temporadaId: Int,
        mes: Int,
        jogadoresMovidos: MutableSet<Int>
    ) {
        val todos = timeRepository.buscarTodosOrdenadosPorReputacao()
        val timesIA = todos.filter { it.id != playerTimeId }
        if (timesIA.size < 2) return

        // Janela atual: um jogador contratado nesta janela só pode ser revendido na PRÓXIMA.
        val janelaAtual = JogadorRepository.janelaTransferencia(temporadaId, mes)
        val probabilidade = if (mes <= 2) 0.30f else 0.15f

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
                        .filter { it.janelaUltimaTransferencia < janelaAtual }  // só libera na próxima janela
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

    // ─────────────────────────────────────────────────────────────────────
    //  Propostas de trabalho (clube → treinador)
    // ─────────────────────────────────────────────────────────────────────

    /** Fluxo reativo de notificações de proposta de clube (pendentes + encerradas não lidas). */
    fun observarPropostasClube(): Flow<List<PropostaClubeEntity>> =
        propostaClubeDao.observeNotificacoes()

    /** Fluxo reativo do contador de propostas de clube não lidas. */
    fun observarContadorPropostasClube(): Flow<Int> =
        propostaClubeDao.observeContadorNaoLidas()

    /** Marca uma proposta de clube como lida. */
    suspend fun marcarPropostaClubeComoLida(id: Int) =
        propostaClubeDao.marcarLida(id)

    /** Marca todas as encerradas como lidas de uma vez. */
    suspend fun marcarTodasPropostasClubeLidas() =
        propostaClubeDao.marcarTodasEncerradasLidas()

    /** Recusa a proposta de clube. */
    suspend fun recusarPropostaClube(id: Int) {
        propostaClubeDao.atualizarStatus(id, StatusPropostaClube.RECUSADA)
    }

    /**
     * Aceita a proposta de clube: o jogador passa a controlar [novoTimeId].
     * Atualiza os flags controladoPorJogador em ambos os times **e** migra
     * a `TecnicoEntity` do usuário para o novo clube — encerrando sua
     * passagem ativa no clube anterior, demitindo o técnico que estava no
     * novo clube (vira agente livre) e abrindo nova passagem.
     *
     * Em seguida, preenche a vaga deixada no clube anterior com um técnico
     * livre do mercado (mesmo critério da troca de temporada).
     *
     * @param anoAtual ano em vigor — usado para registrar fim/início de passagens.
     */
    suspend fun aceitarPropostaClube(id: Int, timeAtualId: Int, novoTimeId: Int, anoAtual: Int) {
        propostaClubeDao.atualizarStatus(id, StatusPropostaClube.ACEITA)
        // Recusa todas as outras propostas pendentes
        propostaClubeDao.buscarPendentes().forEach { p ->
            if (p.id != id) propostaClubeDao.atualizarStatus(p.id, StatusPropostaClube.RECUSADA)
        }
        val timeAtual = db.timeDao().buscarPorId(timeAtualId)
        if (timeAtual != null) {
            db.timeDao().atualizar(timeAtual.copy(controladoPorJogador = false))
        }
        val novoTime = db.timeDao().buscarPorId(novoTimeId)
        if (novoTime != null) {
            db.timeDao().atualizar(novoTime.copy(controladoPorJogador = true))
        }

        // ── Migra TecnicoEntity do usuário ────────────────────
        try {
            val tecnicoUsuario = tecnicoRepository.buscarTecnicoDoJogador()
            if (tecnicoUsuario != null) {
                // Encerra passagem ativa do usuário no clube anterior
                tecnicoRepository.liberarDoTime(tecnicoUsuario.id, anoAtual)
                // Contrata no novo clube — isso demite o técnico que estava
                // lá (vira agente livre) e abre nova passagem ativa.
                tecnicoRepository.contratar(
                    tecnicoId = tecnicoUsuario.id,
                    timeId    = novoTimeId,
                    salario   = tecnicoUsuario.salario,
                    anos      = tecnicoUsuario.contratoAnos.coerceAtLeast(3),
                    anoAtual  = anoAtual
                )
            }
            // Preenche a vaga deixada no clube anterior com um técnico livre
            tecnicoRepository.preencherVagasComTecnicosLivres(anoAtual)
        } catch (_: Exception) { }
    }

    /**
     * Retorna informações detalhadas de um clube para exibição no painel de proposta.
     * Inclui: saldo, força média do elenco, capacidade do estádio e resultados da temporada passada.
     */
    suspend fun buscarInfoPropostaClube(timeId: Int, temporadaEncerradaId: Int): InfoPropostaClube? {
        val time = timeRepository.buscarPorId(timeId) ?: return null
        val jogadores = db.jogadorDao().buscarDisponiveisPorTime(timeId)
        val forcaMedia = if (jogadores.isNotEmpty()) jogadores.map { it.forca }.average().toFloat() else 0f

        // Campeonatos da temporada recém-encerrada em que o time participou
        val campeonatosTemporada = db.campeonatoDao().buscarPorTemporadaId(temporadaEncerradaId)
        val resultados = mutableListOf<ResultadoTemporadaClube>()
        for (camp in campeonatosTemporada) {
            val participantes = db.campeonatoDao().buscarIdsParticipantes(camp.id)
            if (timeId !in participantes) continue
            val tabela = classificacaoDao.buscarTabelaOrdenada(camp.id)
            val posicao = tabela.indexOfFirst { it.timeId == timeId }.takeIf { it >= 0 }?.plus(1)
            val classif = tabela.firstOrNull { it.timeId == timeId }
            if (classif != null) {
                resultados.add(
                    ResultadoTemporadaClube(
                        nomeCampeonato = camp.nome,
                        tipoCampeonato = camp.tipo,
                        posicao = posicao,
                        vitorias = classif.vitorias,
                        empates = classif.empates,
                        derrotas = classif.derrotas,
                        pontos = classif.pontos,
                        jogos = classif.jogos
                    )
                )
            }
        }

        return InfoPropostaClube(
            timeId            = time.id,
            nomeClube         = time.nome,
            nivel             = time.nivel,
            divisao           = time.divisao,
            escudoRes         = time.escudoRes,
            saldo             = time.saldo,
            forcaMediaElenco  = forcaMedia,
            capacidadeEstadio = time.estadioCapacidade,
            resultadosTemporada = resultados
        )
    }

    /**
     * Gera propostas de trabalho de outros clubes com base nos resultados da temporada.
     * Chamado ao final de cada temporada, antes de criar a nova.
     *
     * Regras:
     * - Campeão da copa nacional OU liga nacional → clubes nivel 8–10 podem oferecer
     * - Top 5 na liga (sem título) → clubes nivel 6–9
     * - 6º ao 11º na liga → clubes nivel 5–7
     * - Fora das condições acima → clubes nivel ≤ 5
     */
    suspend fun gerarPropostasDeClube(
        campeonatoLigaId: Int,
        copaId: Int,
        timeJogadorId: Int,
        temporadaId: Int
    ) {
        if (timeJogadorId <= 0) return
        // Limpa propostas anteriores
        propostaClubeDao.limparTodas()

        // Determina posição na liga
        val tabelaLiga = classificacaoDao.buscarTabelaOrdenada(campeonatoLigaId)
        val posicaoLiga = tabelaLiga.indexOfFirst { it.timeId == timeJogadorId }.takeIf { it >= 0 }?.plus(1)

        // Verifica se ganhou a copa nacional
        val campeaoCopa = if (copaId > 0) classificacaoDao.buscarTop2(copaId).firstOrNull()?.timeId else null
        val ganhouCopa = campeaoCopa == timeJogadorId

        // Verifica se ganhou a liga
        val campeaoLiga = tabelaLiga.firstOrNull()?.timeId
        val ganhouLiga = campeaoLiga == timeJogadorId

        // Define faixa de nível dos clubes que podem oferecer
        val (nivelMin, nivelMax) = when {
            ganhouCopa || ganhouLiga           -> 8 to 10
            posicaoLiga != null && posicaoLiga <= 5  -> 6 to 9
            posicaoLiga != null && posicaoLiga <= 11 -> 5 to 7
            else                                     -> 1 to 5
        }

        val todosOsTimes = db.timeDao().buscarTodos()

        // Determina as divisões do mesmo país do jogador
        val timeJogador = db.timeDao().buscarPorId(timeJogadorId)
        val divisoesDoMesmoPais: IntRange = when (timeJogador?.divisao) {
            in 1..4  -> 1..4    // Brasil
            in 5..6  -> 5..6    // Argentina
            in 9..10 -> 9..10   // Uruguai
            else     -> 1..4
        }

        // Candidatos da mesma liga do jogador
        val candidatosMesmaLiga = todosOsTimes.filter { time ->
            !time.controladoPorJogador &&
            time.id != timeJogadorId &&
            time.nivel in nivelMin..nivelMax &&
            time.divisao in divisoesDoMesmoPais
        }.shuffled()

        // Candidatos de outras ligas/países
        val candidatosOutrasLigas = todosOsTimes.filter { time ->
            !time.controladoPorJogador &&
            time.id != timeJogadorId &&
            time.nivel in nivelMin..nivelMax &&
            time.divisao !in divisoesDoMesmoPais
        }.shuffled()

        // Garante ao menos 1 proposta da própria liga e 1 de outra liga
        val propostasParaInserir = mutableListOf<TimeEntity>()
        candidatosMesmaLiga.firstOrNull()?.let { propostasParaInserir.add(it) }
        candidatosOutrasLigas.firstOrNull()?.let { propostasParaInserir.add(it) }

        // Proposta adicional OBRIGATORIAMENTE de uma das grandes ligas estrangeiras
        // (Argentina, Uruguai, Inglaterra, Espanha ou Itália), excluindo o país do
        // próprio jogador e times já escolhidos acima.
        val ligasEstrangeiras = setOf(5, 6, 9, 10, 11, 12, 13, 14)
        val divisoesPaisJogador: Set<Int> = when (timeJogador?.divisao) {
            in 1..4  -> (1..4).toSet()
            in 5..6  -> setOf(5, 6)
            in 9..10 -> setOf(9, 10)
            11, 12   -> setOf(11, 12)  // Inglaterra (Premier + Championship)
            13       -> setOf(13)      // Espanha (La Liga)
            14       -> setOf(14)      // Itália (Serie A)
            else     -> emptySet()
        }
        val jaEscolhidos = propostasParaInserir.map { it.id }.toSet()
        val candidatoEstrangeiro = todosOsTimes.filter { time ->
            !time.controladoPorJogador &&
            time.id != timeJogadorId &&
            time.id !in jaEscolhidos &&
            time.nivel in nivelMin..nivelMax &&
            time.divisao in ligasEstrangeiras &&
            time.divisao !in divisoesPaisJogador
        }.shuffled().firstOrNull()
        candidatoEstrangeiro?.let { propostasParaInserir.add(it) }

        propostasParaInserir.forEach { time ->
            propostaClubeDao.inserir(
                PropostaClubeEntity(
                    timeOfertanteId = time.id,
                    temporadaId     = temporadaId
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Modelos de dados para propostas de clube
// ─────────────────────────────────────────────────────────────────────

data class ResultadoTemporadaClube(
    val nomeCampeonato: String,
    val tipoCampeonato: br.com.managerfoot.data.database.entities.TipoCampeonato,
    val posicao: Int?,
    val vitorias: Int,
    val empates: Int,
    val derrotas: Int,
    val pontos: Int,
    val jogos: Int
)

data class InfoPropostaClube(
    val timeId: Int,
    val nomeClube: String,
    val nivel: Int,
    val divisao: Int,
    val escudoRes: String,
    val saldo: Long,
    val forcaMediaElenco: Float,
    val capacidadeEstadio: Int,
    val resultadosTemporada: List<ResultadoTemporadaClube>
)