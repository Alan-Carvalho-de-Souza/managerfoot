package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.managerfoot.data.database.entities.PropostaClubeEntity
import br.com.managerfoot.data.database.entities.PropostaIAEntity
import br.com.managerfoot.data.database.entities.StatusProposta
import br.com.managerfoot.data.database.entities.StatusPropostaClube
import br.com.managerfoot.data.database.entities.TipoCampeonato
import br.com.managerfoot.data.database.entities.TipoProposta
import br.com.managerfoot.data.repository.InfoPropostaClube
import br.com.managerfoot.data.repository.ResultadoTemporadaClube
import br.com.managerfoot.presentation.ui.components.*
import br.com.managerfoot.presentation.ui.theme.GoldChampion
import br.com.managerfoot.presentation.ui.theme.MoneyNegative
import br.com.managerfoot.presentation.ui.theme.MoneyPositive
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.viewmodel.DashboardUiState
import br.com.managerfoot.presentation.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    timeId: Int,
    onIrParaEscalacao: () -> Unit,
    onIrParaPreJogo: (campeonatoId: Int, rodada: Int, adversarioId: Int) -> Unit,
    onIrParaMercado: () -> Unit,
    onIrParaTabela: () -> Unit,
    onIrParaArtilheiros: () -> Unit,
    onIrParaFinancas: () -> Unit,
    onIrParaHallDaFama: () -> Unit,
    onIrParaConfronto: () -> Unit,
    onIrParaCalendario: () -> Unit,
    onIrParaCopaChaveamento: () -> Unit = {},
    onIrParaRankingGeral: () -> Unit = {},
    onIrParaEstatisticasTime: () -> Unit = {},
    onIrParaEstadio: () -> Unit = {},
    onIrParaJuniores: () -> Unit = {},
    onIrParaJogadores: () -> Unit = {},
    onIrParaRodada: () -> Unit = {},
    onIrParaClubes: () -> Unit = {},
    onIrParaPatrocinadores: () -> Unit = {},
    onIrParaTreinamento: () -> Unit = {},
    onIrParaConquistas: () -> Unit = {},
    vm: DashboardViewModel = hiltViewModel()
) {
    val time by vm.timeJogador.collectAsStateWithLifecycle()
    val todosOsTimes by vm.todosOsTimes.collectAsStateWithLifecycle()
    val ultimosResultados by vm.ultimosResultados.collectAsStateWithLifecycle()
    val proximaPartida by vm.proximaPartida.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val resultadoSimulado by vm.resultadoSimulado.collectAsStateWithLifecycle()
    val saveState by vm.saveState.collectAsStateWithLifecycle()
    val escalacaoSimulacao by vm.escalacaoSimulacao.collectAsStateWithLifecycle()
    val escalacaoAdversarioSimulacao by vm.escalacaoAdversarioSimulacao.collectAsStateWithLifecycle()
    val penaltisResultado by vm.penaltisResultado.collectAsStateWithLifecycle()
    val dadosPenaltisAdversario by vm.dadosPenaltisAdversario.collectAsStateWithLifecycle()
    val penaltisInterativoConcluido by vm.penaltisInterativoConcluido.collectAsStateWithLifecycle()
    val posicaoNaTabela by vm.posicaoNaTabela.collectAsStateWithLifecycle()
    val precisaEscolherPatrocinador by vm.precisaEscolherPatrocinador.collectAsStateWithLifecycle()
    val notificacoesContador by vm.notificacoesContador.collectAsStateWithLifecycle()
    val notificacoes by vm.notificacoes.collectAsStateWithLifecycle()
    val propostasClube by vm.propostasClube.collectAsStateWithLifecycle()
    val infoPropostaClubeSelecionada by vm.infoPropostaClubeSelecionada.collectAsStateWithLifecycle()

    var abaAtual by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    LaunchedEffect(precisaEscolherPatrocinador) {
        if (precisaEscolherPatrocinador) onIrParaPatrocinadores()
    }

    // Quando há resultado simulado, abre a tela de simulação em tempo real
    resultadoSimulado?.let { resultado ->
        val timeCasaSim = todosOsTimes.find { it.id == resultado.timeCasaId }
        val timeForaSim = todosOsTimes.find { it.id == resultado.timeForaId }
        val nomeCasa = timeCasaSim?.nome ?: "Time ${resultado.timeCasaId}"
        val nomeFora = timeForaSim?.nome ?: "Time ${resultado.timeForaId}"
        val isTimeCasaOJogador = resultado.timeCasaId == (saveState?.timeIdJogador ?: -1)
        PartidaSimulacaoScreen(
            resultado = resultado,
            nomeTimeCasa = nomeCasa,
            nomeTimeFora = nomeFora,
            escudoTimeCasa = timeCasaSim?.escudoRes ?: "",
            escudoTimeFora = timeForaSim?.escudoRes ?: "",
            nomeEstadio = timeCasaSim?.estadioNome ?: "",
            escalacaoJogador = escalacaoSimulacao,
            escalacaoAdversario = escalacaoAdversarioSimulacao,
            isTimeCasaOJogador = isTimeCasaOJogador,
            penaltisResultado = penaltisResultado,
            dadosPenaltisAdversario = dadosPenaltisAdversario,
            penaltisInterativoConcluido = penaltisInterativoConcluido,
            onObterEventosSegundoTempo = { titulares, reservas, subs, formacao, estilo, minInicio, minFim ->
                vm.obterEventosSegundoTempo(titulares, reservas, subs, formacao, estilo, minInicio, minFim)
            },
            onFinalizarPartida = { golsCasa, golsFora ->
                vm.finalizarPartidaSimulada(golsCasa, golsFora)
            },
            onPenaltisConfirmados = { resultado -> vm.finalizarPenaltisJogador(resultado) },
            onSimulacaoFinalizada = { vm.fecharSimulacao() }
        )
        return
    }

    Scaffold(
        bottomBar = {
            DashboardBottomBar(
                abaAtual = abaAtual,
                onAbaChanged = { abaAtual = it },
                onIrParaEscalacao = onIrParaEscalacao,
                onIrParaTabela = onIrParaTabela,
                notificacoesContador = notificacoesContador
            )
        }
    ) { ip ->
        when (abaAtual) {
            0 -> AbaInicio(
                contentPaddingBottom = ip.calculateBottomPadding(),
                time = time,
                todosOsTimes = todosOsTimes,
                proximaPartida = proximaPartida,
                ultimosResultados = ultimosResultados,
                saveState = saveState,
                uiState = uiState,
                posicaoNaTabela = posicaoNaTabela,
                onSimular = { campId, rodada, advId ->
                    onIrParaPreJogo(campId, rodada, advId)
                },
                onEscalacao = onIrParaEscalacao,
                onEncerrarTemporada = { vm.encerrarTemporada() },
                onIrParaTabela = onIrParaTabela,
                onIrParaCalendario = onIrParaCalendario,
                onIrParaCopaChaveamento = onIrParaCopaChaveamento,
                onIrParaRankingGeral = onIrParaRankingGeral,
                onIrParaArtilheiros = onIrParaArtilheiros,
                onIrParaJogadores = onIrParaJogadores,
                onIrParaMercado = onIrParaMercado,
                onIrParaTreinamento = onIrParaTreinamento,
                onIrParaFinancas = onIrParaFinancas,
                onIrParaEstadio = onIrParaEstadio,
                onIrParaPatrocinadores = onIrParaPatrocinadores,
                onIrParaRodada = onIrParaRodada
            )
            1 -> NotificacoesAba(
                padding = ip,
                notificacoes = notificacoes,
                propostasClube = propostasClube,
                todosOsTimes = todosOsTimes,
                onMarcarLida = { vm.marcarNotificacaoLida(it) },
                onMarcarLidaClube = { vm.marcarPropostaClubeComoLida(it) },
                onMarcarTodas = { vm.marcarTodasNotificacoesLidas() },
                onVerPropostaClube = { proposta -> vm.carregarInfoPropostaClube(proposta.timeOfertanteId) },
                onRecusarPropostaClube = { vm.recusarPropostaClube(it) }
            )
            else -> MenuAba(
                padding = ip,
                onIrParaTabela = onIrParaTabela,
                onIrParaArtilheiros = onIrParaArtilheiros,
                onIrParaEstatisticasTime = onIrParaEstatisticasTime,
                onIrParaCalendario = onIrParaCalendario,
                onIrParaCopaChaveamento = onIrParaCopaChaveamento,
                onIrParaRankingGeral = onIrParaRankingGeral,
                onIrParaHallDaFama = onIrParaHallDaFama,
                onIrParaConfronto = onIrParaConfronto,
                onIrParaMercado = onIrParaMercado,
                onIrParaFinancas = onIrParaFinancas,
                onIrParaEstadio = onIrParaEstadio,
                onIrParaJuniores = onIrParaJuniores,
                onIrParaJogadores = onIrParaJogadores,
                onIrParaRodada = onIrParaRodada,
                onIrParaClubes = onIrParaClubes,
                onIrParaPatrocinadores = onIrParaPatrocinadores,
                onIrParaTreinamento = onIrParaTreinamento,
                onIrParaConquistas = onIrParaConquistas
            )
        }
    }

    // Painel de detalhes de proposta de clube (exibido sobre a tela)
    infoPropostaClubeSelecionada?.let { info ->
        PropostaClubeDetalheDialog(
            info = info,
            onAceitar = {
                vm.aceitarPropostaClube(
                    propostaId = propostasClube.firstOrNull { it.timeOfertanteId == info.timeId }?.id ?: 0,
                    novoTimeId = info.timeId
                )
            },
            onRecusar = {
                val id = propostasClube.firstOrNull { it.timeOfertanteId == info.timeId }?.id ?: 0
                vm.recusarPropostaClube(id)
            },
            onDismiss = { vm.fecharInfoPropostaClube() }
        )
    }
}

// ─── Aba 0 — Início ─────────────────────────────────────────
@Composable
private fun AbaInicio(
    contentPaddingBottom: androidx.compose.ui.unit.Dp,
    time: br.com.managerfoot.domain.model.Time?,
    todosOsTimes: List<br.com.managerfoot.domain.model.Time>,
    proximaPartida: br.com.managerfoot.data.database.entities.PartidaEntity?,
    ultimosResultados: List<br.com.managerfoot.data.database.entities.PartidaEntity>,
    saveState: br.com.managerfoot.data.datasource.SaveState?,
    uiState: DashboardUiState,
    posicaoNaTabela: Int,
    onSimular: (campId: Int, rodada: Int, advId: Int) -> Unit,
    onEscalacao: () -> Unit,
    onEncerrarTemporada: () -> Unit,
    onIrParaTabela: () -> Unit,
    onIrParaCalendario: () -> Unit,
    onIrParaCopaChaveamento: () -> Unit,
    onIrParaRankingGeral: () -> Unit,
    onIrParaArtilheiros: () -> Unit,
    onIrParaJogadores: () -> Unit,
    onIrParaMercado: () -> Unit,
    onIrParaTreinamento: () -> Unit,
    onIrParaFinancas: () -> Unit,
    onIrParaEstadio: () -> Unit,
    onIrParaPatrocinadores: () -> Unit,
    onIrParaRodada: () -> Unit
) {
    val meuTimeId = saveState?.timeIdJogador ?: -1
    val forma = remember(ultimosResultados, meuTimeId) {
        derivarFormaUltimos5(ultimosResultados, meuTimeId)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = Spacing.md,
            bottom = contentPaddingBottom + Spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // ── Header do clube ──
        item {
            time?.let {
                val ordemRef = (proximaPartida ?: ultimosResultados.firstOrNull())?.ordemGlobal ?: 0
                val (mesChip, diaChip) = if (ordemRef > 0) {
                    val nomeCamp = nomeCampeonatoParaData(saveState, proximaPartida, ultimosResultados)
                    dashOrdemGlobalParaData(ordemRef, nomeCamp)
                } else (saveState?.mesAtual ?: 0) to 0

                TimeHeaderCard(
                    time = it,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    posicao = posicaoNaTabela,
                    rodadaAtual = proximaPartida?.rodada ?: 0,
                    mes = mesChip,
                    dia = diaChip,
                    ano = saveState?.anoAtual ?: 0,
                    forma = forma
                )
            }
        }

        // ── Próxima partida ou encerramento de temporada ──
        item {
            SectionTitle(titulo = "Próxima partida")
            if (proximaPartida != null) {
                val timeCasa = todosOsTimes.find { it.id == proximaPartida.timeCasaId }
                val timeFora = todosOsTimes.find { it.id == proximaPartida.timeForaId }
                val nomeCasa = timeCasa?.nome ?: "Time ${proximaPartida.timeCasaId}"
                val nomeFora = timeFora?.nome ?: "Time ${proximaPartida.timeForaId}"
                val escudoCasa = timeCasa?.escudoRes ?: ""
                val escudoFora = timeFora?.escudoRes ?: ""

                val competitionLabel = competitionLabelFor(saveState, proximaPartida)
                val dataJogoLabel = run {
                    val nomeCamp = nomeCampeonatoParaData(saveState, proximaPartida, ultimosResultados)
                    val (mes, dia) = dashOrdemGlobalParaData(proximaPartida.ordemGlobal, nomeCamp)
                    val meses = listOf("", "Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
                    "$dia ${meses[mes]}"
                }

                MatchCard(
                    nomeCasa = nomeCasa,
                    nomeFora = nomeFora,
                    escudoCasa = escudoCasa,
                    escudoFora = escudoFora,
                    competicao = competitionLabel,
                    rodada = proximaPartida.rodada,
                    dataJogo = dataJogoLabel,
                    enabled = uiState != DashboardUiState.Simulando,
                    onSimular = {
                        val adversarioId = if (proximaPartida.timeCasaId == meuTimeId)
                            proximaPartida.timeForaId
                        else
                            proximaPartida.timeCasaId
                        onSimular(
                            proximaPartida.campeonatoId,
                            proximaPartida.rodada,
                            adversarioId
                        )
                    },
                    onEscalacao = onEscalacao,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            } else {
                EncerrarTemporadaCard(
                    proximoAno = (saveState?.anoAtual ?: 2026) + 1,
                    simulando = uiState == DashboardUiState.Simulando,
                    onEncerrar = onEncerrarTemporada,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }
        }

        // ── Últimos resultados ──
        if (ultimosResultados.isNotEmpty()) {
            item {
                SectionTitle(
                    titulo = "Últimos resultados",
                    acao = { SectionLink("Ver tudo", onClick = onIrParaRodada) }
                )
            }
            items(ultimosResultados.take(5).size) { idx ->
                val partida = ultimosResultados[idx]
                val snap = saveState
                val nomeCasa = todosOsTimes.find { it.id == partida.timeCasaId }?.nome ?: "Time ${partida.timeCasaId}"
                val nomeFora = todosOsTimes.find { it.id == partida.timeForaId }?.nome ?: "Time ${partida.timeForaId}"
                val escudoCasa = todosOsTimes.find { it.id == partida.timeCasaId }?.escudoRes ?: ""
                val escudoFora = todosOsTimes.find { it.id == partida.timeForaId }?.escudoRes ?: ""

                val ehCopaPartida = snap != null && snap.copaId > 0 && partida.campeonatoId == snap.copaId
                val ehCopaArgPartida = snap != null && snap.copaArgId > 0 && partida.campeonatoId == snap.copaArgId
                val ehSupercopaPartida = snap != null && snap.supercopaId > 0 && partida.campeonatoId == snap.supercopaId
                val resultLabel = when {
                    ehSupercopaPartida -> "Supercopa Rei"
                    ehCopaPartida -> "Copa do Brasil — ${partida.fase ?: "Copa"}"
                    ehCopaArgPartida -> "Copa Argentina — ${partida.fase ?: "Copa"}"
                    partida.campeonatoId == snap?.campeonatoAId -> "Série A · R${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoBId -> "Série B · R${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoCId -> "Série C · R${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoDId -> "Série D · R${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoArgAId -> "Apertura · R${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoArgBId -> "Segunda Div. · R${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoArgClausuraId -> "Clausura · R${partida.rodada}"
                    else -> "Rodada ${partida.rodada}"
                }
                val labelCor = when {
                    ehSupercopaPartida -> GoldChampion
                    ehCopaPartida || ehCopaArgPartida -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(Modifier.padding(horizontal = Spacing.lg)) {
                    Text(
                        text = resultLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = labelCor,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs, bottom = Spacing.xxs)
                    )
                    ResultadoCard(
                        nomeCasa = nomeCasa,
                        nomeVis = nomeFora,
                        golsCasa = partida.golsCasa,
                        golsVis = partida.golsFora,
                        escudoCasa = escudoCasa,
                        escudoVis = escudoFora,
                        meuTimeId = meuTimeId,
                        timeCasaId = partida.timeCasaId
                    )
                }
            }
        }

        // ── Gestão (grid 3×2) ──
        item {
            SectionTitle(titulo = "Gestão")
            ManagementGrid(
                onIrParaJogadores = onIrParaJogadores,
                onIrParaMercado = onIrParaMercado,
                onIrParaTreinamento = onIrParaTreinamento,
                onIrParaFinancas = onIrParaFinancas,
                onIrParaEstadio = onIrParaEstadio,
                onIrParaPatrocinadores = onIrParaPatrocinadores,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
        }

        // ── Competições (chips horizontais) ──
        item {
            SectionTitle(titulo = "Competições")
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item {
                    CompetitionChip(
                        icon = Icons.Filled.TableChart,
                        label = "Tabela",
                        onClick = onIrParaTabela
                    )
                }
                item {
                    CompetitionChip(
                        icon = Icons.Filled.CalendarMonth,
                        label = "Calendário",
                        onClick = onIrParaCalendario
                    )
                }
                item {
                    CompetitionChip(
                        icon = Icons.Filled.EmojiEvents,
                        label = "Copas",
                        onClick = onIrParaCopaChaveamento
                    )
                }
                item {
                    CompetitionChip(
                        icon = Icons.Filled.Public,
                        label = "Ranking",
                        onClick = onIrParaRankingGeral
                    )
                }
                item {
                    CompetitionChip(
                        icon = Icons.Filled.Star,
                        label = "Artilheiros",
                        onClick = onIrParaArtilheiros
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagementGrid(
    onIrParaJogadores: () -> Unit,
    onIrParaMercado: () -> Unit,
    onIrParaTreinamento: () -> Unit,
    onIrParaFinancas: () -> Unit,
    onIrParaEstadio: () -> Unit,
    onIrParaPatrocinadores: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ManagementTile(
                icon = Icons.Filled.Groups,
                label = "Elenco",
                onClick = onIrParaJogadores,
                modifier = Modifier.weight(1f)
            )
            ManagementTile(
                icon = Icons.Filled.Storefront,
                label = "Mercado",
                onClick = onIrParaMercado,
                modifier = Modifier.weight(1f)
            )
            ManagementTile(
                icon = Icons.Filled.FitnessCenter,
                label = "Treino",
                onClick = onIrParaTreinamento,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ManagementTile(
                icon = Icons.Filled.AccountBalance,
                label = "Finanças",
                onClick = onIrParaFinancas,
                modifier = Modifier.weight(1f)
            )
            ManagementTile(
                icon = Icons.Filled.Stadium,
                label = "Estádio",
                onClick = onIrParaEstadio,
                modifier = Modifier.weight(1f)
            )
            ManagementTile(
                icon = Icons.Filled.Handshake,
                label = "Patrocínio",
                onClick = onIrParaPatrocinadores,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EncerrarTemporadaCard(
    proximoAno: Int,
    simulando: Boolean,
    onEncerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.5.dp, GoldChampion.copy(alpha = 0.5f))
    ) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                GoldChampion.copy(alpha = 0.10f),
                                GoldChampion.copy(alpha = 0.02f)
                            )
                        )
                    )
            )
            Column(
                Modifier.padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(GoldChampion.copy(alpha = 0.18f))
                        .border(2.dp, GoldChampion, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = GoldChampion,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Temporada concluída",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Encerre a temporada para iniciar $proximoAno.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))
                Button(
                    onClick = onEncerrar,
                    enabled = !simulando,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    if (simulando) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Avançar para $proximoAno", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardBottomBar(
    abaAtual: Int,
    onAbaChanged: (Int) -> Unit,
    onIrParaEscalacao: () -> Unit,
    onIrParaTabela: () -> Unit,
    notificacoesContador: Int = 0
) {
    NavigationBar(tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = abaAtual == 0,
            onClick = { onAbaChanged(0) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Início") },
            label = { Text("Início") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onIrParaEscalacao,
            icon = { Icon(Icons.Filled.People, contentDescription = "Elenco") },
            label = { Text("Elenco") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onIrParaTabela,
            icon = { Icon(Icons.Filled.Leaderboard, contentDescription = "Tabela") },
            label = { Text("Tabela") }
        )
        NavigationBarItem(
            selected = abaAtual == 1,
            onClick = { onAbaChanged(1) },
            icon = {
                BadgedBox(badge = {
                    if (notificacoesContador > 0) Badge { Text(notificacoesContador.toString()) }
                }) {
                    Icon(
                        imageVector = if (notificacoesContador > 0) Icons.Filled.Notifications
                                      else Icons.Filled.NotificationsNone,
                        contentDescription = "Notificações"
                    )
                }
            },
            label = { Text("Notif.") }
        )
        NavigationBarItem(
            selected = abaAtual == 2,
            onClick = { onAbaChanged(2) },
            icon = { Icon(Icons.Filled.Menu, contentDescription = "Mais") },
            label = { Text("Mais") }
        )
    }
}

// ─── Aba "Mais" — agrupada por seção ────────────────────────
@Composable
private fun MenuAba(
    padding: PaddingValues,
    onIrParaTabela: () -> Unit,
    onIrParaArtilheiros: () -> Unit,
    onIrParaEstatisticasTime: () -> Unit,
    onIrParaCalendario: () -> Unit,
    onIrParaCopaChaveamento: () -> Unit,
    onIrParaRankingGeral: () -> Unit,
    onIrParaHallDaFama: () -> Unit,
    onIrParaConfronto: () -> Unit,
    onIrParaMercado: () -> Unit,
    onIrParaFinancas: () -> Unit,
    onIrParaEstadio: () -> Unit,
    onIrParaJuniores: () -> Unit,
    onIrParaJogadores: () -> Unit,
    onIrParaRodada: () -> Unit,
    onIrParaClubes: () -> Unit,
    onIrParaPatrocinadores: () -> Unit,
    onIrParaTreinamento: () -> Unit,
    onIrParaConquistas: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = Spacing.md,
            bottom = padding.calculateBottomPadding() + Spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item { SectionTitle("Competições") }
        item {
            MenuTileGrid(listOf(
                MenuTileItem(Icons.Filled.TableChart, "Tabela", onIrParaTabela),
                MenuTileItem(Icons.Filled.CalendarMonth, "Calendário", onIrParaCalendario),
                MenuTileItem(Icons.Filled.PlayArrow, "Rodadas", onIrParaRodada),
                MenuTileItem(Icons.Filled.EmojiEvents, "Copas", onIrParaCopaChaveamento),
                MenuTileItem(Icons.Filled.Public, "Ranking", onIrParaRankingGeral),
                MenuTileItem(Icons.Filled.Star, "Artilheiros", onIrParaArtilheiros),
            ))
        }

        item { SectionTitle("Clube") }
        item {
            MenuTileGrid(listOf(
                MenuTileItem(Icons.Filled.Groups, "Elenco", onIrParaJogadores),
                MenuTileItem(Icons.Filled.School, "Juniores", onIrParaJuniores),
                MenuTileItem(Icons.Filled.FitnessCenter, "Treino", onIrParaTreinamento),
                MenuTileItem(Icons.Filled.Storefront, "Mercado", onIrParaMercado),
                MenuTileItem(Icons.Filled.AccountBalance, "Finanças", onIrParaFinancas),
                MenuTileItem(Icons.Filled.Handshake, "Patrocínio", onIrParaPatrocinadores),
                MenuTileItem(Icons.Filled.Stadium, "Estádio", onIrParaEstadio),
            ))
        }

        item { SectionTitle("Estatísticas & Histórico") }
        item {
            MenuTileGrid(listOf(
                MenuTileItem(Icons.Filled.EmojiEvents, "Conquistas", onIrParaConquistas),
                MenuTileItem(Icons.Filled.Insights, "Estatísticas", onIrParaEstatisticasTime),
                MenuTileItem(Icons.Filled.TrackChanges, "Confrontos", onIrParaConfronto),
                MenuTileItem(Icons.Filled.AutoAwesome, "Hall da Fama", onIrParaHallDaFama),
                MenuTileItem(Icons.AutoMirrored.Filled.MenuBook, "Clubes", onIrParaClubes),
                MenuTileItem(Icons.Filled.Timeline, "Tabela Geral", onIrParaTabela),
            ))
        }
    }
}

private data class MenuTileItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
private fun MenuTileGrid(items: List<MenuTileItem>) {
    val rows = items.chunked(3)
    Column(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { item ->
                    ManagementTile(
                        icon = item.icon,
                        label = item.label,
                        onClick = item.onClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ─── Aba 1 — Notificações ───────────────────────────────────
@Composable
private fun NotificacoesAba(
    padding: PaddingValues,
    notificacoes: List<PropostaIAEntity>,
    propostasClube: List<PropostaClubeEntity>,
    todosOsTimes: List<br.com.managerfoot.domain.model.Time>,
    onMarcarLida: (Int) -> Unit,
    onMarcarLidaClube: (Int) -> Unit,
    onMarcarTodas: () -> Unit,
    onVerPropostaClube: (PropostaClubeEntity) -> Unit,
    onRecusarPropostaClube: (Int) -> Unit
) {
    val temEncerradasTransf = notificacoes.any {
        (it.status == StatusProposta.ACEITA || it.status == StatusProposta.RECUSADA) && !it.lida
    }
    val temEncerradasClube = propostasClube.any {
        (it.status == StatusPropostaClube.ACEITA || it.status == StatusPropostaClube.RECUSADA) && !it.lida
    }
    val temEncerradas = temEncerradasTransf || temEncerradasClube
    val tudoVazio = notificacoes.isEmpty() && propostasClube.isEmpty()

    if (tudoVazio) {
        NotificacoesEmptyState(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = Spacing.md,
            bottom = padding.calculateBottomPadding() + Spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (temEncerradas) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onMarcarTodas) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Marcar todas como lidas", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (propostasClube.isNotEmpty()) {
            item { SectionTitle("Propostas de trabalho") }
            items(propostasClube, key = { "clube_${it.id}" }) { proposta ->
                val nomeTime = todosOsTimes.find { it.id == proposta.timeOfertanteId }?.nome
                    ?: "Clube ${proposta.timeOfertanteId}"
                val escudo = todosOsTimes.find { it.id == proposta.timeOfertanteId }?.escudoRes ?: ""
                PropostaClubeCard(
                    proposta = proposta,
                    nomeTime = nomeTime,
                    escudo = escudo,
                    onVer = { onVerPropostaClube(proposta) },
                    onMarcarLida = { onMarcarLidaClube(proposta.id) },
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }
        }

        if (notificacoes.isNotEmpty()) {
            item { SectionTitle("Transferências") }
            items(notificacoes, key = { "notif_${it.id}" }) { notif ->
                val nomeTime = todosOsTimes.find { it.id == notif.timeCompradorId }?.nome
                    ?: "Time ${notif.timeCompradorId}"
                val escudo = todosOsTimes.find { it.id == notif.timeCompradorId }?.escudoRes ?: ""
                PropostaTransferenciaCard(
                    notif = notif,
                    nomeTime = nomeTime,
                    escudo = escudo,
                    onMarcarLida = { onMarcarLida(notif.id) },
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }
        }
    }
}

@Composable
private fun NotificacoesEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsNone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "TUDO TRANQUILO",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Nenhuma notificação no momento.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
    }
}

@Composable
private fun PropostaClubeCard(
    proposta: PropostaClubeEntity,
    nomeTime: String,
    escudo: String,
    onVer: () -> Unit,
    onMarcarLida: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (accent, ehEncerrada) = when (proposta.status) {
        StatusPropostaClube.PENDENTE -> MaterialTheme.colorScheme.primary to false
        StatusPropostaClube.ACEITA -> GoldChampion to true
        StatusPropostaClube.RECUSADA -> MaterialTheme.colorScheme.onSurfaceVariant to true
    }
    val titulo = when (proposta.status) {
        StatusPropostaClube.PENDENTE -> "Proposta de $nomeTime"
        StatusPropostaClube.ACEITA -> "Aceito — $nomeTime"
        StatusPropostaClube.RECUSADA -> "Recusado — $nomeTime"
    }
    val descricao = when (proposta.status) {
        StatusPropostaClube.PENDENTE -> "$nomeTime quer que você comande o clube na próxima temporada."
        StatusPropostaClube.ACEITA -> "Você aceitou o cargo de treinador em $nomeTime."
        StatusPropostaClube.RECUSADA -> "Você recusou a proposta de $nomeTime."
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (proposta.status == StatusPropostaClube.PENDENTE) 1.5.dp else 1.dp,
            color = accent.copy(alpha = if (proposta.status == StatusPropostaClube.PENDENTE) 0.6f else 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TeamBadge(nome = nomeTime, escudoRes = escudo, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        titulo,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        descricao,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ehEncerrada && !proposta.lida) {
                    TextButton(onClick = onMarcarLida) {
                        Text("OK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (proposta.status == StatusPropostaClube.PENDENTE) {
                Spacer(Modifier.height(Spacing.sm))
                Button(
                    onClick = onVer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Text("Ver detalhes", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PropostaTransferenciaCard(
    notif: PropostaIAEntity,
    nomeTime: String,
    escudo: String,
    onMarcarLida: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (accent, icon) = when (notif.status) {
        StatusProposta.PENDENTE -> MaterialTheme.colorScheme.primary to Icons.Filled.Info
        StatusProposta.AGUARDANDO_RESPOSTA_IA -> MaterialTheme.colorScheme.tertiary to Icons.Filled.Info
        StatusProposta.ACEITA -> MoneyPositive to Icons.Filled.CheckCircle
        StatusProposta.RECUSADA -> MoneyNegative to Icons.Filled.Block
    }
    val tipoLabel = if (notif.tipoProposta == TipoProposta.EMPRESTIMO) "empréstimo" else "compra"
    val valorFmt = "R$ %,.0f".format(notif.valorOfertado / 100.0)
    val titulo: String
    val descricao: String
    when (notif.status) {
        StatusProposta.PENDENTE -> {
            titulo = "Proposta de $nomeTime"
            descricao = "Oferta de $tipoLabel: $valorFmt. Acesse o Mercado para responder."
        }
        StatusProposta.AGUARDANDO_RESPOSTA_IA -> {
            titulo = "Aguardando resposta — $nomeTime"
            descricao = "A diretoria está avaliando sua contra-oferta de $tipoLabel."
        }
        StatusProposta.ACEITA -> {
            titulo = "Negociação aceita — $nomeTime"
            val valorAceito = if (notif.valorSolicitadoJogador > 0)
                "R$ %,.0f".format(notif.valorSolicitadoJogador / 100.0)
            else valorFmt
            descricao = "A diretoria aceitou a proposta de $tipoLabel por $valorAceito."
        }
        StatusProposta.RECUSADA -> {
            titulo = "Negociação encerrada — $nomeTime"
            descricao = "A diretoria recusou a proposta de $tipoLabel."
        }
    }
    val ehEncerrada = notif.status == StatusProposta.ACEITA || notif.status == StatusProposta.RECUSADA

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (notif.status == StatusProposta.PENDENTE) 1.5.dp else 1.dp,
            color = accent.copy(alpha = if (notif.status == StatusProposta.PENDENTE) 0.6f else 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .border(0.5.dp, accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    descricao,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (ehEncerrada && !notif.lida) {
                TextButton(onClick = onMarcarLida) {
                    Text("OK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Dialog de detalhes de proposta de clube ────────────────
@Composable
private fun PropostaClubeDetalheDialog(
    info: InfoPropostaClube,
    onAceitar: () -> Unit,
    onRecusar: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                TeamBadge(nome = info.nomeClube, escudoRes = info.escudoRes, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        "PROPOSTA DE TRABALHO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        info.nomeClube,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Nível ${info.nivel}") }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(nomeDivisaoClube(info.divisao)) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                InfoRow("Dinheiro em caixa", formatarSaldo(info.saldo))
                InfoRow("Força média do elenco", "%.1f".format(info.forcaMediaElenco))
                InfoRow("Capacidade do estádio", "%,d torcedores".format(info.capacidadeEstadio))

                if (info.resultadosTemporada.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text(
                        "ÚLTIMA TEMPORADA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    info.resultadosTemporada.forEach { resultado ->
                        ResultadoClubeRow(resultado)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAceitar, shape = RoundedCornerShape(Radius.md)) {
                Text("Aceitar", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onRecusar) {
                Text("Recusar", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ResultadoClubeRow(resultado: ResultadoTemporadaClube) {
    val nomeComp = when (resultado.tipoCampeonato) {
        TipoCampeonato.NACIONAL_DIVISAO1 -> "Série A"
        TipoCampeonato.NACIONAL_DIVISAO2 -> "Série B"
        TipoCampeonato.NACIONAL_DIVISAO3 -> "Série C"
        TipoCampeonato.NACIONAL_DIVISAO4 -> "Série D"
        TipoCampeonato.COPA_NACIONAL -> "Copa do Brasil"
        TipoCampeonato.SUPERCOPA -> "Supercopa Rei"
        TipoCampeonato.EXTRANGEIRO_DIVISAO1 -> "Div. 1 Estrangeiro"
        TipoCampeonato.EXTRANGEIRO_DIVISAO2 -> "Div. 2 Estrangeiro"
        else -> resultado.nomeCampeonato.substringBefore(" 20").trim()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Text(
                nomeComp,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Spacing.xxs))
            if (resultado.posicao != null) {
                Text(
                    "${resultado.posicao}º colocado · ${resultado.pontos} pts",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "${resultado.vitorias}V ${resultado.empates}E ${resultado.derrotas}D (${resultado.jogos} jogos)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun nomeDivisaoClube(divisao: Int): String = when (divisao) {
    1 -> "Série A"; 2 -> "Série B"; 3 -> "Série C"; 4 -> "Série D"
    5 -> "Primeira Div. Argentina (Apertura)"; 6 -> "Segunda Div. Argentina"
    9 -> "Primeira Div. Uruguai"; 10 -> "Segunda Div. Uruguai"
    else -> "Div. $divisao"
}

// ─── Helpers de cálculo de data e identificação de campeonato ────────

/**
 * Determina o "nome do campeonato" para a função de cálculo de data
 * a partir do save state e da partida (próxima ou último resultado).
 */
private fun nomeCampeonatoParaData(
    saveState: br.com.managerfoot.data.datasource.SaveState?,
    proximaPartida: br.com.managerfoot.data.database.entities.PartidaEntity?,
    ultimosResultados: List<br.com.managerfoot.data.database.entities.PartidaEntity>
): String {
    val ref = proximaPartida ?: ultimosResultados.firstOrNull() ?: return "Brasileirao"
    val snap = saveState ?: return "Brasileirao"
    return when {
        snap.supercopaId > 0 && ref.campeonatoId == snap.supercopaId -> "Supercopa"
        snap.copaId > 0 && ref.campeonatoId == snap.copaId -> "Copa"
        ref.campeonatoId == snap.campeonatoArgAId ||
        ref.campeonatoId == snap.campeonatoArgBId ||
        ref.campeonatoId == snap.campeonatoArgClausuraId -> "Argentina"
        else -> "Brasileirao"
    }
}

/**
 * Constrói o label de competição mostrado no card de "Próxima partida".
 * Cobre Brasileirão (Série A-D), Copa do Brasil, Supercopa, Argentina (Apertura/Clausura/Segunda Div).
 */
private fun competitionLabelFor(
    saveState: br.com.managerfoot.data.datasource.SaveState?,
    proximaPartida: br.com.managerfoot.data.database.entities.PartidaEntity
): String {
    val snap = saveState ?: return "Brasileirão"
    val ehCopa = snap.copaId > 0 && proximaPartida.campeonatoId == snap.copaId
    val ehSupercopa = snap.supercopaId > 0 && proximaPartida.campeonatoId == snap.supercopaId
    return when {
        ehSupercopa -> "Supercopa Rei"
        ehCopa -> "Copa do Brasil — ${proximaPartida.fase ?: "Copa"}"
        else -> when (proximaPartida.campeonatoId) {
            snap.campeonatoAId -> "Série A"
            snap.campeonatoBId -> "Série B"
            snap.campeonatoCId -> "Série C"
            snap.campeonatoDId -> "Série D"
            snap.campeonatoArgAId -> "Apertura — ${proximaPartida.fase ?: "Grupo"}"
            snap.campeonatoArgBId -> "Segunda Div."
            snap.campeonatoArgClausuraId -> "Clausura — ${proximaPartida.fase ?: "Grupo"}"
            else -> "Brasileirão"
        }
    }
}

// ─── Cálculo de data por ordem global (preservado da versão dev) ────
private val DASH_COPA_DATAS: Map<Int, Pair<Int, Int>> = mapOf(
    13 to (2 to 10), 33 to (2 to 25),
    53 to (3 to 15), 83 to (4 to 5),
    133 to (5 to 15), 163 to (6 to 10),
    207 to (7 to 15), 233 to (8 to 5),
    278 to (9 to 10), 313 to (10 to 8),
    357 to (11 to 12), 383 to (12 to 3)
)
private val DASH_DIAS_MES = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
private fun dashDiaDanoParaMesDia(diaDoAno: Int): Pair<Int, Int> {
    var rem = diaDoAno.coerceIn(1, 365)
    for (m in 1..12) {
        if (rem <= DASH_DIAS_MES[m]) return m to rem
        rem -= DASH_DIAS_MES[m]
    }
    return 12 to 31
}
private fun dashOrdemGlobalParaData(ordemGlobal: Int, nomeCampeonato: String): Pair<Int, Int> = when {
    ordemGlobal == 1 -> 1 to 25
    nomeCampeonato.contains("Copa", ignoreCase = true) ->
        DASH_COPA_DATAS[ordemGlobal] ?: dashDiaDanoParaMesDia((39 + (ordemGlobal - 10) * 295 / 370).coerceIn(1, 365))
    nomeCampeonato.contains("Argentina", ignoreCase = true) ->
        dashDiaDanoParaMesDia((39 + (ordemGlobal - 10) * 295 / 410).coerceIn(1, 365))
    else ->
        dashDiaDanoParaMesDia((39 + (ordemGlobal - 10) * 295 / 370).coerceIn(1, 365))
}
