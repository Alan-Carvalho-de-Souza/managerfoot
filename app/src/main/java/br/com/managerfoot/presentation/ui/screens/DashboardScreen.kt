package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.managerfoot.data.database.entities.StatusProposta
import br.com.managerfoot.data.database.entities.TipoProposta
import br.com.managerfoot.presentation.ui.components.*
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
    val notificacoesContador by vm.notificacoesContador.collectAsStateWithLifecycle()
    val notificacoes by vm.notificacoes.collectAsStateWithLifecycle()

    var abaAtual by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    // Quando o jogo foi iniciado e o patrocinador ainda não foi escolhido, navega automaticamente
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
                abaAtual               = abaAtual,
                onAbaChanged           = { abaAtual = it },
                onIrParaEscalacao      = onIrParaEscalacao,
                onIrParaTabela         = onIrParaTabela,
                notificacoesContador   = notificacoesContador
            )
        }
    ) { ip ->
        if (abaAtual == 0) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = ip.calculateBottomPadding() + 32.dp)
        ) {
            item {
                time?.let {
                    // Deriva o mês do chip a partir do ordemGlobal da próxima partida (ou
                    // do último resultado se a temporada já acabou), evitando depender do
                    // mesAtual do DataStore que só avançava via botão removido.
                    val ordemRef = (proximaPartida ?: ultimosResultados.firstOrNull())?.ordemGlobal ?: 0
                    val (mesChip, diaChip) = if (ordemRef > 0) {
                        val nomeCamp = when {
                            saveState != null && saveState!!.supercopaId > 0 &&
                                proximaPartida?.campeonatoId == saveState!!.supercopaId -> "Supercopa"
                            saveState != null && saveState!!.copaId > 0 &&
                                (proximaPartida ?: ultimosResultados.firstOrNull())?.campeonatoId == saveState!!.copaId -> "Copa"
                            saveState != null && (
                                (proximaPartida ?: ultimosResultados.firstOrNull())?.campeonatoId == saveState!!.campeonatoArgAId ||
                                (proximaPartida ?: ultimosResultados.firstOrNull())?.campeonatoId == saveState!!.campeonatoArgBId ||
                                (proximaPartida ?: ultimosResultados.firstOrNull())?.campeonatoId == saveState!!.campeonatoArgClausuraId
                            ) -> "Argentina"
                            else -> "Brasileirao"
                        }
                        dashOrdemGlobalParaData(ordemRef, nomeCamp)
                    } else (saveState?.mesAtual ?: 0) to 0
                    TimeHeaderCard(
                        it,
                        Modifier.padding(16.dp),
                        posicao = posicaoNaTabela,
                        rodadaAtual = proximaPartida?.rodada ?: 0,
                        mes = mesChip,
                        dia = diaChip,
                        ano = saveState?.anoAtual ?: 0
                    )
                }
            }

        item {
            SecaoHeader("Próxima partida")
            if (proximaPartida != null) {
                val timeCasa = todosOsTimes.find { it.id == proximaPartida!!.timeCasaId }
                val timeFora = todosOsTimes.find { it.id == proximaPartida!!.timeForaId }
                val nomeCasa = timeCasa?.nome ?: "Time ${proximaPartida!!.timeCasaId}"
                val nomeFora = timeFora?.nome ?: "Time ${proximaPartida!!.timeForaId}"
                val escudoCasa = timeCasa?.escudoRes ?: ""
                val escudoFora = timeFora?.escudoRes ?: ""

                val snap = saveState
                val ehCopa = snap != null && snap.copaId > 0 &&
                    proximaPartida!!.campeonatoId == snap.copaId
                val ehSupercopa = snap != null && snap.supercopaId > 0 &&
                    proximaPartida!!.campeonatoId == snap.supercopaId
                val competitionLabel = when {
                    ehSupercopa -> "Supercopa Rei"
                    ehCopa -> "Copa do Brasil \u2014 ${proximaPartida!!.fase ?: "Copa"}"
                    else -> when (proximaPartida!!.campeonatoId) {
                        snap?.campeonatoAId -> "S\u00e9rie A"
                        snap?.campeonatoBId -> "S\u00e9rie B"
                        snap?.campeonatoCId -> "S\u00e9rie C"
                        snap?.campeonatoDId -> "S\u00e9rie D"
                        snap?.campeonatoArgAId -> "Apertura\u2014${proximaPartida!!.fase ?: "Grupo"}"
                        snap?.campeonatoArgBId -> "Segunda Div."
                        snap?.campeonatoArgClausuraId -> "Clausura\u2014${proximaPartida!!.fase ?: "Grupo"}"
                        else -> "Brasileir\u00e3o"
                    }
                }
                MatchCard(
                    nomeCasa    = nomeCasa,
                    nomeFora    = nomeFora,
                    escudoCasa  = escudoCasa,
                    escudoFora  = escudoFora,
                    competicao  = competitionLabel,
                    rodada      = proximaPartida!!.rodada,
                    dataJogo    = run {
                        val nomeCamp = when {
                            ehSupercopa -> "Supercopa"
                            ehCopa -> "Copa"
                            competitionLabel.contains("Argentina", ignoreCase = true) -> "Argentina"
                            competitionLabel.contains("Primera") -> "Argentina"
                            competitionLabel.contains("Segunda") -> "Argentina"
                            else -> "Brasileirao"
                        }
                        val (mes, dia) = dashOrdemGlobalParaData(proximaPartida!!.ordemGlobal, nomeCamp)
                        val meses = listOf("","Jan","Fev","Mar","Abr","Mai","Jun","Jul","Ago","Set","Out","Nov","Dez")
                        "$dia ${meses[mes]}"
                    },
                    enabled     = uiState != DashboardUiState.Simulando,
                    onSimular   = {
                        val adversarioId = if (proximaPartida!!.timeCasaId == (saveState?.timeIdJogador ?: -1))
                            proximaPartida!!.timeForaId
                        else
                            proximaPartida!!.timeCasaId
                        onIrParaPreJogo(
                            proximaPartida!!.campeonatoId,
                            proximaPartida!!.rodada,
                            adversarioId
                        )
                    },
                    onEscalacao = onIrParaEscalacao,
                    modifier    = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                // Temporada concluída — exibe card para avançar ao próximo ano
                val proximoAno = (saveState?.anoAtual ?: 2026) + 1
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Temporada concluída! ðŸ†",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Encerre a temporada para iniciar $proximoAno.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { vm.encerrarTemporada() },
                            enabled = uiState != DashboardUiState.Simulando,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState == DashboardUiState.Simulando) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Avançar para $proximoAno")
                            }
                        }
                    }
                }
            }
        }

        if (ultimosResultados.isNotEmpty()) {
            item { SecaoHeader("Últimos resultados") }
            items(ultimosResultados.size.coerceAtMost(5)) { idx ->
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
                    ehCopaPartida -> "Copa do Brasil \u2014 ${partida.fase ?: "Copa"}"
                    ehCopaArgPartida -> "Copa Argentina \u2014 ${partida.fase ?: "Copa"}"
                    partida.campeonatoId == snap?.campeonatoAId -> "S\u00e9rie A \u2014 Rodada ${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoBId -> "S\u00e9rie B \u2014 Rodada ${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoCId -> "S\u00e9rie C \u2014 Rodada ${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoDId -> "S\u00e9rie D \u2014 Rodada ${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoArgAId -> "Apertura \u2014 Rodada ${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoArgBId -> "Segunda Div. \u2014 Rodada ${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoArgClausuraId -> "Clausura \u2014 Rodada ${partida.rodada}"
                    else -> "Rodada ${partida.rodada}"
                }
                Column {
                    Text(
                        text = resultLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            ehSupercopaPartida -> MaterialTheme.colorScheme.tertiary
                            ehCopaPartida || ehCopaArgPartida -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                    )
                    ResultadoCard(
                        nomeCasa   = nomeCasa,
                        nomeVis    = nomeFora,
                        golsCasa   = partida.golsCasa,
                        golsVis    = partida.golsFora,
                        escudoCasa = escudoCasa,
                        escudoVis  = escudoFora,
                        meuTimeId  = snap?.timeIdJogador ?: -1,
                        timeCasaId = partida.timeCasaId,
                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }

        } // end LazyColumn
        } else if (abaAtual == 1) {
            NotificacoesAba(
                padding       = ip,
                notificacoes  = notificacoes,
                todosOsTimes  = todosOsTimes,
                onMarcarLida  = { vm.marcarNotificacaoLida(it) },
                onMarcarTodas = { vm.marcarTodasNotificacoesLidas() }
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
    } // end Scaffold
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
            icon     = {
                BadgedBox(badge = {
                    if (notificacoesContador > 0) Badge { Text(notificacoesContador.toString()) }
                }) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notificações")
                }
            },
            label    = { Text("Notif.") }
        )
        NavigationBarItem(
            selected = abaAtual == 2,
            onClick  = { onAbaChanged(2) },
            icon     = { Icon(Icons.Filled.Menu, contentDescription = "Menu") },
            label    = { Text("Menu") }
        )
    }
}

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
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = padding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { OutlinedButton(onClick = onIrParaArtilheiros,      modifier = Modifier.fillMaxWidth()) { Text("Artilharia & Assistências") } }
        item { OutlinedButton(onClick = onIrParaCalendario,       modifier = Modifier.fillMaxWidth()) { Text("Calendário") } }
        item { OutlinedButton(onClick = onIrParaClubes,           modifier = Modifier.fillMaxWidth()) { Text("Clubes") } }
        item { OutlinedButton(onClick = onIrParaCopaChaveamento,  modifier = Modifier.fillMaxWidth()) { Text("Copas") } }
        item { OutlinedButton(onClick = onIrParaEstadio,          modifier = Modifier.fillMaxWidth()) { Text("Estádio") } }
        item { OutlinedButton(onClick = onIrParaEstatisticasTime, modifier = Modifier.fillMaxWidth()) { Text("Estatísticas do Time") } }
        item { OutlinedButton(onClick = onIrParaFinancas,         modifier = Modifier.fillMaxWidth()) { Text("Finanças do Clube") } }
        item { OutlinedButton(onClick = onIrParaHallDaFama,       modifier = Modifier.fillMaxWidth()) { Text("Hall da Fama") } }
        item { OutlinedButton(onClick = onIrParaConfronto,        modifier = Modifier.fillMaxWidth()) { Text("Histórico de Confrontos") } }
        item { OutlinedButton(onClick = onIrParaJogadores,        modifier = Modifier.fillMaxWidth()) { Text("Jogadores") } }
        item { OutlinedButton(onClick = onIrParaJuniores,         modifier = Modifier.fillMaxWidth()) { Text("Juniores") } }
        item { OutlinedButton(onClick = onIrParaMercado,          modifier = Modifier.fillMaxWidth()) { Text("Mercado de Transferências") } }
        item { OutlinedButton(onClick = onIrParaPatrocinadores,   modifier = Modifier.fillMaxWidth()) { Text("Patrocinadores") } }
        item { OutlinedButton(onClick = onIrParaRankingGeral,     modifier = Modifier.fillMaxWidth()) { Text("Ranking Geral") } }
        item { OutlinedButton(onClick = onIrParaRodada,           modifier = Modifier.fillMaxWidth()) { Text("Rodadas") } }
        item { OutlinedButton(onClick = onIrParaTabela,           modifier = Modifier.fillMaxWidth()) { Text("Tabela de Classificação") } }
        item { OutlinedButton(onClick = onIrParaTreinamento,      modifier = Modifier.fillMaxWidth()) { Text("Treinamento") } }
    }
}

// ─────────────────────────────────────────────
//  Aba de Notificações
// ─────────────────────────────────────────────
@Composable
private fun NotificacoesAba(
    padding: PaddingValues,
    notificacoes: List<br.com.managerfoot.data.database.entities.PropostaIAEntity>,
    todosOsTimes: List<br.com.managerfoot.domain.model.Time>,
    onMarcarLida: (Int) -> Unit,
    onMarcarTodas: () -> Unit
) {
    val temEncerradas = notificacoes.any {
        (it.status == StatusProposta.ACEITA || it.status == StatusProposta.RECUSADA) && !it.lida
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = 12.dp,
            bottom = padding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (notificacoes.isEmpty()) {
            item {
                Text(
                    text = "Nenhuma notificação no momento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            if (temEncerradas) {
                item {
                    TextButton(onClick = onMarcarTodas, modifier = Modifier.fillMaxWidth()) {
                        Text("Marcar todas como lidas")
                    }
                }
            }
            items(notificacoes, key = { it.id }) { notif ->
                val nomeTime = todosOsTimes.find { it.id == notif.timeCompradorId }?.nome
                    ?: "Time ${notif.timeCompradorId}"
                val encerrada = notif.status == StatusProposta.ACEITA || notif.status == StatusProposta.RECUSADA
                val containerColor = when {
                    !encerrada -> MaterialTheme.colorScheme.primaryContainer
                    notif.status == StatusProposta.ACEITA -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
                val onContainerColor = when {
                    !encerrada -> MaterialTheme.colorScheme.onPrimaryContainer
                    notif.status == StatusProposta.ACEITA -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
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
                        descricao = "A IA está avaliando sua contra-oferta de $tipoLabel."
                    }
                    StatusProposta.ACEITA -> {
                        titulo = "Negociação aceita — $nomeTime"
                        val valorAceito = if (notif.valorSolicitadoJogador > 0)
                            "R$ %,.0f".format(notif.valorSolicitadoJogador / 100.0)
                        else valorFmt
                        descricao = "A IA aceitou a proposta de $tipoLabel por $valorAceito."
                    }
                    StatusProposta.RECUSADA -> {
                        titulo = "Negociação encerrada — $nomeTime"
                        descricao = "A IA recusou a proposta de $tipoLabel."
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = containerColor)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(titulo,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = onContainerColor)
                            Spacer(Modifier.height(4.dp))
                            Text(descricao,
                                style = MaterialTheme.typography.bodySmall,
                                color = onContainerColor)
                        }
                        if (encerrada && !notif.lida) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { onMarcarLida(notif.id) }) {
                                Text("OK", color = onContainerColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Utilitário de data para o card da próxima partida
// ─────────────────────────────────────────────
private val DASH_COPA_DATAS: Map<Int, Pair<Int, Int>> = mapOf(
    13 to (2 to 10),  33 to (2 to 25),
    53 to (3 to 15),  83 to (4 to  5),
    133 to (5 to 15), 163 to (6 to 10),
    207 to (7 to 15), 233 to (8 to  5),
    278 to (9 to 10), 313 to (10 to  8),
    357 to (11 to 12),383 to (12 to  3)
)
private val DASH_DIAS_MES = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
private fun dashDiaDanoParaMesDia(diaDoAno: Int): Pair<Int, Int> {
    var rem = diaDoAno.coerceIn(1, 365)
    for (m in 1..12) { if (rem <= DASH_DIAS_MES[m]) return m to rem; rem -= DASH_DIAS_MES[m] }
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