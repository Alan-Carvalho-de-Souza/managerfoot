package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.domain.model.Time
import br.com.managerfoot.presentation.ui.components.InfoChip
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.viewmodel.InicioUiState
import br.com.managerfoot.presentation.viewmodel.InicioViewModel

// ═══════════════════════════════════════════════════════════
//  InicioScreen
// ═══════════════════════════════════════════════════════════
@Composable
fun InicioScreen(
    onJogoIniciado: (timeId: Int) -> Unit,
    vm: InicioViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is InicioUiState.JogoIniciado) {
            onJogoIniciado((uiState as InicioUiState.JogoIniciado).timeId)
        }
    }

    // Tela de seleção de clube (sobrepõe as demais quando ativa)
    if (uiState is InicioUiState.SelecionandoTime) {
        SelecionarTimeScreen(
            onTimeSelecionado = { timeId -> vm.iniciarNovoJogo(timeId) }
        )
        return
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (uiState) {
            is InicioUiState.Carregando -> CircularProgressIndicator()

            is InicioUiState.SemSave -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("⚽ ManagerFoot", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Assuma o comando de um clube e conquiste o Brasil!",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { vm.iniciarSelecionarTime() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Novo Jogo", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            is InicioUiState.TemSave -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("⚽ ManagerFoot", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Button(onClick = { vm.continuarJogo() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continuar", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(
                        onClick = { vm.iniciarSelecionarTime() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Novo Jogo") }
                }
            }

            is InicioUiState.Erro -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("Erro ao iniciar jogo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text((uiState as InicioUiState.Erro).mensagem, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.iniciarSelecionarTime() }) { Text("Tentar novamente") }
                }
            }

            else -> Unit
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  SelecionarTimeScreen
// ═══════════════════════════════════════════════════════════
@Composable
fun SelecionarTimeScreen(
    onTimeSelecionado: (timeId: Int) -> Unit,
    vm: InicioViewModel = hiltViewModel()
) {
    val times by vm.timesDisponiveis.collectAsState()
    var busca by remember { mutableStateOf("") }

    val filtrados = remember(times, busca) {
        if (busca.isBlank()) times
        else times.filter { it.nome.contains(busca, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        // Barra de busca
        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            placeholder = { Text("Buscar clube...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true
        )

        if (filtrados.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(filtrados) { time ->
                    TimeItemRow(time = time, onClick = { onTimeSelecionado(time.id) })
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun TimeItemRow(time: Time, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(time.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${time.cidade} - ${time.estado}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoChip("Série ${time.divisao}", MaterialTheme.colorScheme.primaryContainer)
            Text(formatarSaldo(time.saldo), style = MaterialTheme.typography.labelSmall)
        }
    }
}
