package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.managerfoot.presentation.ui.components.*
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
                abaAtual          = abaAtual,
                onAbaChanged      = { abaAtual = it },
                onIrParaEscalacao = onIrParaEscalacao,
                onIrParaTabela    = onIrParaTabela
            )
        }
    ) { ip ->
        if (abaAtual == 0) {
            AbaInicio(
                contentPaddingBottom = ip.calculateBottomPadding(),
                time                 = time,
                todosOsTimes         = todosOsTimes,
                proximaPartida       = proximaPartida,
                ultimosResultados    = ultimosResultados,
                saveState            = saveState,
                uiState              = uiState,
                posicaoNaTabela      = posicaoNaTabela,
                onSimular            = { campId, rodada, advId ->
                    onIrParaPreJogo(campId, rodada, advId)
                },
                onEscalacao          = onIrParaEscalacao,
                onEncerrarTemporada  = { vm.encerrarTemporada() },
                onIrParaTabela       = onIrParaTabela,
                onIrParaCalendario   = onIrParaCalendario,
                onIrParaCopaChaveamento = onIrParaCopaChaveamento,
                onIrParaRankingGeral = onIrParaRankingGeral,
                onIrParaArtilheiros  = onIrParaArtilheiros,
                onIrParaJogadores    = onIrParaJogadores,
                onIrParaMercado      = onIrParaMercado,
                onIrParaTreinamento  = onIrParaTreinamento,
                onIrParaFinancas     = onIrParaFinancas,
                onIrParaEstadio      = onIrParaEstadio,
                onIrParaPatrocinadores = onIrParaPatrocinadores,
                onIrParaRodada       = onIrParaRodada
            )
        } else {
            MenuAba(
                padding                  = ip,
                onIrParaTabela           = onIrParaTabela,
                onIrParaArtilheiros      = onIrParaArtilheiros,
                onIrParaEstatisticasTime = onIrParaEstatisticasTime,
                onIrParaCalendario       = onIrParaCalendario,
                onIrParaCopaChaveamento  = onIrParaCopaChaveamento,
                onIrParaRankingGeral     = onIrParaRankingGeral,
                onIrParaHallDaFama       = onIrParaHallDaFama,
                onIrParaConfronto        = onIrParaConfronto,
                onIrParaMercado          = onIrParaMercado,
                onIrParaFinancas         = onIrParaFinancas,
                onIrParaEstadio          = onIrParaEstadio,
                onIrParaJuniores         = onIrParaJuniores,
                onIrParaJogadores        = onIrParaJogadores,
                onIrParaRodada           = onIrParaRodada,
                onIrParaClubes           = onIrParaClubes,
                onIrParaPatrocinadores   = onIrParaPatrocinadores,
                onIrParaTreinamento      = onIrParaTreinamento
            )
        }
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
                val mesChip = if (ordemRef > 0)
                    (1 + (ordemRef.coerceAtLeast(1) - 1) * 11 / 379).coerceIn(1, 12)
                else saveState?.mesAtual ?: 0

                TimeHeaderCard(
                    time        = it,
                    modifier    = Modifier.padding(horizontal = Spacing.lg),
                    posicao     = posicaoNaTabela,
                    rodadaAtual = proximaPartida?.rodada ?: 0,
                    mes         = mesChip,
                    ano         = saveState?.anoAtual ?: 0,
                    forma       = forma
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

                val snap = saveState
                val ehCopa = snap != null && snap.copaId > 0 &&
                    proximaPartida.campeonatoId == snap.copaId
                val competitionLabel = if (ehCopa) {
                    "Copa do Brasil — ${proximaPartida.fase ?: "Copa"}"
                } else {
                    when (proximaPartida.campeonatoId) {
                        snap?.campeonatoAId -> "Série A"
                        snap?.campeonatoBId -> "Série B"
                        snap?.campeonatoCId -> "Série C"
                        snap?.campeonatoDId -> "Série D"
                        snap?.campeonatoArgAId -> "Primera Div."
                        else -> "Brasileirão"
                    }
                }
                MatchCard(
                    nomeCasa    = nomeCasa,
                    nomeFora    = nomeFora,
                    escudoCasa  = escudoCasa,
                    escudoFora  = escudoFora,
                    competicao  = competitionLabel,
                    rodada      = proximaPartida.rodada,
                    enabled     = uiState != DashboardUiState.Simulando,
                    onSimular   = {
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
                    modifier    = Modifier.padding(horizontal = Spacing.lg)
                )
            } else {
                EncerrarTemporadaCard(
                    proximoAno = (saveState?.anoAtual ?: 2026) + 1,
                    simulando  = uiState == DashboardUiState.Simulando,
                    onEncerrar = onEncerrarTemporada,
                    modifier   = Modifier.padding(horizontal = Spacing.lg)
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
                val resultLabel = when {
                    ehCopaPartida -> "Copa do Brasil — ${partida.fase ?: "Copa"}"
                    partida.campeonatoId == snap?.campeonatoAId -> "Série A · R${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoBId -> "Série B · R${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoCId -> "Série C · R${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoDId -> "Série D · R${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoArgAId -> "Primera Div. · R${partida.rodada}"
                    else -> "Rodada ${partida.rodada}"
                }
                Column(Modifier.padding(horizontal = Spacing.lg)) {
                    Text(
                        text = resultLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ehCopaPartida)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs, bottom = Spacing.xxs)
                    )
                    ResultadoCard(
                        nomeCasa   = nomeCasa,
                        nomeVis    = nomeFora,
                        golsCasa   = partida.golsCasa,
                        golsVis    = partida.golsFora,
                        escudoCasa = escudoCasa,
                        escudoVis  = escudoFora,
                        meuTimeId  = meuTimeId,
                        timeCasaId = partida.timeCasaId
                    )
                }
            }
        }

        // ── Gestão (grid 3×2) ──
        item {
            SectionTitle(titulo = "Gestão")
            ManagementGrid(
                onIrParaJogadores      = onIrParaJogadores,
                onIrParaMercado        = onIrParaMercado,
                onIrParaTreinamento    = onIrParaTreinamento,
                onIrParaFinancas       = onIrParaFinancas,
                onIrParaEstadio        = onIrParaEstadio,
                onIrParaPatrocinadores = onIrParaPatrocinadores,
                modifier               = Modifier.padding(horizontal = Spacing.lg)
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
                        label = "Copa do Brasil",
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(Radius.lg)
    ) {
        Column(
            Modifier.padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Temporada concluída",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Encerre a temporada para iniciar $proximoAno.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onTertiaryContainer
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

@Composable
private fun DashboardBottomBar(
    abaAtual: Int,
    onAbaChanged: (Int) -> Unit,
    onIrParaEscalacao: () -> Unit,
    onIrParaTabela: () -> Unit
) {
    NavigationBar(tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = abaAtual == 0,
            onClick  = { onAbaChanged(0) },
            icon     = { Icon(Icons.Filled.Home, contentDescription = "Início") },
            label    = { Text("Início") }
        )
        NavigationBarItem(
            selected = false,
            onClick  = onIrParaEscalacao,
            icon     = { Icon(Icons.Filled.People, contentDescription = "Elenco") },
            label    = { Text("Elenco") }
        )
        NavigationBarItem(
            selected = false,
            onClick  = onIrParaTabela,
            icon     = { Icon(Icons.Filled.Leaderboard, contentDescription = "Tabela") },
            label    = { Text("Tabela") }
        )
        NavigationBarItem(
            selected = abaAtual == 1,
            onClick  = { onAbaChanged(1) },
            icon     = { Icon(Icons.Filled.Menu, contentDescription = "Menu") },
            label    = { Text("Mais") }
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
    onIrParaTreinamento: () -> Unit
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
                MenuTileItem(Icons.Filled.TableChart,    "Tabela",     onIrParaTabela),
                MenuTileItem(Icons.Filled.CalendarMonth, "Calendário", onIrParaCalendario),
                MenuTileItem(Icons.Filled.PlayArrow,     "Rodadas",    onIrParaRodada),
                MenuTileItem(Icons.Filled.EmojiEvents,   "Copa",       onIrParaCopaChaveamento),
                MenuTileItem(Icons.Filled.Public,        "Ranking",    onIrParaRankingGeral),
                MenuTileItem(Icons.Filled.Star,          "Artilheiros", onIrParaArtilheiros),
            ))
        }

        item { SectionTitle("Clube") }
        item {
            MenuTileGrid(listOf(
                MenuTileItem(Icons.Filled.Groups,        "Elenco",     onIrParaJogadores),
                MenuTileItem(Icons.Filled.School,        "Juniores",   onIrParaJuniores),
                MenuTileItem(Icons.Filled.FitnessCenter, "Treino",     onIrParaTreinamento),
                MenuTileItem(Icons.Filled.Storefront,    "Mercado",    onIrParaMercado),
                MenuTileItem(Icons.Filled.AccountBalance,"Finanças",   onIrParaFinancas),
                MenuTileItem(Icons.Filled.Handshake,     "Patrocínio", onIrParaPatrocinadores),
                MenuTileItem(Icons.Filled.Stadium,       "Estádio",    onIrParaEstadio),
            ))
        }

        item { SectionTitle("Estatísticas & Histórico") }
        item {
            MenuTileGrid(listOf(
                MenuTileItem(Icons.Filled.Insights,         "Estatísticas",       onIrParaEstatisticasTime),
                MenuTileItem(Icons.Filled.TrackChanges,     "Confrontos",         onIrParaConfronto),
                MenuTileItem(Icons.Filled.AutoAwesome,      "Hall da Fama",       onIrParaHallDaFama),
                MenuTileItem(Icons.AutoMirrored.Filled.MenuBook, "Clubes",        onIrParaClubes),
                MenuTileItem(Icons.Filled.Timeline,         "Tabela Geral",      onIrParaTabela),
            ))
        }
    }
}

private data class MenuTileItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                // Preenche slots vazios para manter alinhamento
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
