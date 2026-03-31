package br.com.managerfoot.data.repository

import br.com.managerfoot.data.dao.CampeonatoDao
import br.com.managerfoot.data.dao.ClassificacaoDao
import br.com.managerfoot.data.dao.CopaPartidaDto
import br.com.managerfoot.data.dao.EstadioDao
import br.com.managerfoot.data.dao.HallDaFamaDao
import br.com.managerfoot.data.dao.PartidaDao
import br.com.managerfoot.data.dao.RankingGeralDao
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
    private val hallDaFamaDao: HallDaFamaDao,
    private val rankingGeralDao: RankingGeralDao,
    private val estadioDao: EstadioDao
) {
    private val simulador = SimuladorPartida()

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
            partidaId     = partidaDoJogador.id,
            casa          = escalacaoFinalCasa,
            fora          = escalacaoFinalFora,
            timeJogadorId = timeJogadorId
        )
        persistirResultado(resultadoJogador)

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
            precisaPenaltis = precisaPenaltis,
            golsAgregadoCasa = golsAgregadoCasa,
            golsAgregadoFora = golsAgregadoFora
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
        ano: Int
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
    suspend fun verificarEAvancarFaseCopa(copaId: Int, anoAtual: Int): Boolean {
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
        val time = timeRepository.buscarPorId(timeId)
            ?: throw IllegalStateException("Time $timeId não encontrado")
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

            // Atualiza ranking geral em tempo real (inclui Copa e ligas)
            atualizarRankingAposPartida(resultado)
        }

        resultado.eventos
            .filter { it.tipo == TipoEvento.LESAO }
            .forEach { _ -> }
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