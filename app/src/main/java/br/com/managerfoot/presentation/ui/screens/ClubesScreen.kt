package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.domain.model.Time
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.JogadorRow
import br.com.managerfoot.presentation.ui.components.SecaoHeader
import br.com.managerfoot.presentation.viewmodel.ClubesViewModel

// ═══════════════════════════════════════════════════════════
//  ClubesScreen
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubesScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: ClubesViewModel = hiltViewModel()
) {
    val times by vm.times.collectAsState()
    val timeSelecionado by vm.timeSelecionado.collectAsState()
    val elenco by vm.elencoTimeSelecionado.collectAsState()
    val saldo by vm.saldo.collectAsState()
    val mensagem by vm.mensagem.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    mensagem?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            vm.limparMensagem()
        }
    }

    // Confirmação de oferta
    var jogadorParaOfertar by remember { mutableStateOf<Jogador?>(null) }

    jogadorParaOfertar?.let { jogador ->
        AlertDialog(
            onDismissRequest = { jogadorParaOfertar = null },
            title = { Text("Confirmar oferta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Jogador: ${jogador.nome}")
                    Text("Posição: ${jogador.posicao.name.replace("_", " ")}")
                    Text("Força: ${jogador.forca}")
                    Text(
                        "Valor: ${formatarSaldoClubes(jogador.valorMercado)}",
                        fontWeight = FontWeight.Bold
                    )
                    if (saldo < jogador.valorMercado) {
                        Text(
                            "Saldo insuficiente!",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.fazerOferta(jogador)
                        jogadorParaOfertar = null
                    },
                    enabled = saldo >= jogador.valorMercado
                ) { Text("Fazer oferta") }
            },
            dismissButton = {
                TextButton(onClick = { jogadorParaOfertar = null }) { Text("Cancelar") }
            }
        )
    }

    if (timeSelecionado != null) {
        // Tela de elenco do clube selecionado
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(timeSelecionado!!.nome, style = MaterialTheme.typography.titleMedium)
                            Text(
                                nomeDivisao(timeSelecionado!!.divisao),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { vm.limparTimeSelecionado() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                )
            },
            snackbarHost = {
                mensagem?.let {
                    Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Seu saldo", style = MaterialTheme.typography.labelMedium)
                    Text(
                        formatarSaldoClubes(saldo),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (elenco.isEmpty()) {
                    EmptyState("Carregando elenco…")
                } else {
                    LazyColumn {
                        item { SecaoHeader("${elenco.size} jogadores") }
                        items(elenco) { jogador ->
                            JogadorRow(
                                jogador = jogador,
                                trailing = {
                                    val podeContratar = saldo >= jogador.valorMercado
                                    OutlinedButton(
                                        onClick = { jogadorParaOfertar = jogador },
                                        enabled = podeContratar,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            formatarSaldoClubes(jogador.valorMercado),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    } else {
        // Lista de clubes
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Clubes") },
                    navigationIcon = {
                        IconButton(onClick = onVoltar) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                )
            }
        ) { padding ->
            if (times.isEmpty()) {
                EmptyState("Carregando clubes…")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    item { SecaoHeader("${times.size} clubes") }
                    items(times) { time ->
                        ClubeRow(time = time, onClick = { vm.selecionarTime(time) })
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClubeRow(time: Time, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(time.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${nomeDivisao(time.divisao)} · ${time.estado}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun nomeDivisao(divisao: Int): String = when (divisao) {
    1 -> "Série A"
    2 -> "Série B"
    3 -> "Série C"
    4 -> "Série D"
    else -> "Divisão $divisao"
}

private fun formatarSaldoClubes(centavos: Long): String {
    val reais = centavos / 100.0
    return when {
        reais >= 1_000_000 -> "R$ %.1f M".format(reais / 1_000_000)
        reais >= 1_000     -> "R$ %.0f mil".format(reais / 1_000)
        else               -> "R$ %.0f".format(reais)
    }
}
