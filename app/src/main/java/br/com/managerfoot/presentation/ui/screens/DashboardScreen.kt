package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val penaltisResultado by vm.penaltisResultado.collectAsStateWithLifecycle()
    val dadosPenaltisAdversario by vm.dadosPenaltisAdversario.collectAsStateWithLifecycle()
    val penaltisInterativoConcluido by vm.penaltisInterativoConcluido.collectAsStateWithLifecycle()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

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
            escalacaoJogador = escalacaoSimulacao,
            isTimeCasaOJogador = isTimeCasaOJogador,
            penaltisResultado = penaltisResultado,
            dadosPenaltisAdversario = dadosPenaltisAdversario,
            penaltisInterativoConcluido = penaltisInterativoConcluido,
            onPenaltisConfirmados = { resultado -> vm.finalizarPenaltisJogador(resultado) },
            onSimulacaoFinalizada = { vm.fecharSimulacao() }
        )
        return
    }

    Scaffold(
        bottomBar = {
            DashboardBottomBar(
                onIrParaEscalacao  = onIrParaEscalacao,
                onIrParaTabela     = onIrParaTabela,
                onIrParaMercado    = onIrParaMercado
            )
        }
    ) { ip ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = ip.calculateBottomPadding() + 32.dp)
        ) {
            item {
                time?.let {
                    TimeHeaderCard(
                        it,
                        Modifier.padding(16.dp),
                        rodadaAtual = proximaPartida?.rodada ?: 0
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
                val competitionLabel = if (ehCopa) {
                    "Copa do Brasil \u2014 ${proximaPartida!!.fase ?: "Copa"}"
                } else {
                    when (proximaPartida!!.campeonatoId) {
                        snap?.campeonatoAId -> "S\u00e9rie A"
                        snap?.campeonatoBId -> "S\u00e9rie B"
                        snap?.campeonatoCId -> "S\u00e9rie C"
                        snap?.campeonatoDId -> "S\u00e9rie D"
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
                            "Temporada concluída! 🏆",
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
                val resultLabel = when {
                    ehCopaPartida -> "Copa do Brasil \u2014 ${partida.fase ?: "Copa"}"
                    partida.campeonatoId == snap?.campeonatoAId -> "S\u00e9rie A \u2014 Rodada ${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoBId -> "S\u00e9rie B \u2014 Rodada ${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoCId -> "S\u00e9rie C \u2014 Rodada ${partida.rodada}"
                    partida.campeonatoId == snap?.campeonatoDId -> "S\u00e9rie D \u2014 Rodada ${partida.rodada}"
                    else -> "Rodada ${partida.rodada}"
                }
                Column {
                    Text(
                        text = resultLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ehCopaPartida)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
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

        item {
            SecaoHeader("Menu")
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onIrParaTabela, modifier = Modifier.fillMaxWidth()) { Text("Tabela de Classificação") }
                OutlinedButton(onClick = onIrParaArtilheiros, modifier = Modifier.fillMaxWidth()) { Text("Artilharia & Assistências") }
                OutlinedButton(onClick = onIrParaEstatisticasTime, modifier = Modifier.fillMaxWidth()) { Text("Estatísticas do Time") }
                OutlinedButton(onClick = onIrParaCalendario, modifier = Modifier.fillMaxWidth()) { Text("Calendário") }
                OutlinedButton(onClick = onIrParaCopaChaveamento, modifier = Modifier.fillMaxWidth()) { Text("Copa do Brasil — Chaveamento") }
                OutlinedButton(onClick = onIrParaRankingGeral, modifier = Modifier.fillMaxWidth()) { Text("Ranking Geral") }
                OutlinedButton(onClick = onIrParaHallDaFama, modifier = Modifier.fillMaxWidth()) { Text("Hall da Fama") }
                OutlinedButton(onClick = onIrParaConfronto, modifier = Modifier.fillMaxWidth()) { Text("Histórico de Confrontos") }
                OutlinedButton(onClick = onIrParaMercado, modifier = Modifier.fillMaxWidth()) { Text("Mercado de Transferências") }
                OutlinedButton(onClick = onIrParaFinancas, modifier = Modifier.fillMaxWidth()) { Text("Finanças do Clube") }
                OutlinedButton(onClick = onIrParaEstadio, modifier = Modifier.fillMaxWidth()) { Text("Estádio") }
                OutlinedButton(onClick = { vm.fecharMes() }, modifier = Modifier.fillMaxWidth()) { Text("Avançar Mês") }
            }
        }
    } // end LazyColumn
    } // end Scaffold
}

@Composable
private fun DashboardBottomBar(
    onIrParaEscalacao: () -> Unit,
    onIrParaTabela: () -> Unit,
    onIrParaMercado: () -> Unit
) {
    NavigationBar(tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = true,
            onClick  = {},
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
            selected = false,
            onClick  = onIrParaMercado,
            icon     = { Icon(Icons.Filled.ShoppingCart, contentDescription = "Mercado") },
            label    = { Text("Mercado") }
        )
    }
}