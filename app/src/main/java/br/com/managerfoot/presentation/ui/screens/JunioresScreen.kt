package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.ui.components.*
import br.com.managerfoot.presentation.viewmodel.JunioresViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JunioresScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: JunioresViewModel = hiltViewModel()
) {
    val juniores by vm.juniores.collectAsStateWithLifecycle()
    val selecionado by vm.jogadorSelecionado.collectAsStateWithLifecycle()
    val mensagem by vm.mensagem.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    LaunchedEffect(mensagem) {
        mensagem?.let {
            snackbarHostState.showSnackbar(it)
            vm.limparMensagem()
        }
    }

    selecionado?.let { jogador ->
        JuniorDetalheDialog(
            jogador  = jogador,
            onFechar = { vm.selecionarJogador(null) },
            onPromover = {
                vm.promoverJunior(jogador.id, jogador.nomeAbreviado)
                vm.selecionarJogador(null)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Base de Juniores") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        if (juniores.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    mensagem = "Nenhum jogador na base de juniores.\nAposente jogadores para gerar novos talentos."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    SecaoHeader("${juniores.size} jogador(es) na base")
                }
                items(juniores, key = { it.id }) { jogador ->
                    JogadorRow(
                        jogador  = jogador,
                        onClick  = { vm.selecionarJogador(jogador) },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text  = "★ %.1f".format(jogador.notaMedia),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text  = "${jogador.idade} anos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

// ── Diálogo de detalhe de um jogador júnior ──────────────────────────────────
@Composable
private fun JuniorDetalheDialog(
    jogador: Jogador,
    onFechar: () -> Unit,
    onPromover: () -> Unit
) {
    var confirmarPromocao by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ForcaBadge(jogador.forca)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(jogador.nome, fontWeight = FontWeight.Bold)
                    Text(
                        "${jogador.posicao.abreviacao} · ${jogador.idade} anos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                // Nota média
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nota média", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "%.1f".format(jogador.notaMedia),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                jogador.notaMedia >= 7.5f -> Color(0xFF4CAF50)
                                jogador.notaMedia >= 6.0f -> MaterialTheme.colorScheme.onSurface
                                else -> Color(0xFFF44336)
                            }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // Atributos
                JuniorAtributoRow("Técnica",     jogador.tecnica)
                JuniorAtributoRow("Passes",      jogador.passe)
                JuniorAtributoRow("Velocidade",  jogador.velocidade)
                JuniorAtributoRow("Finalização", jogador.finalizacao)
                JuniorAtributoRow("Defesa",      jogador.defesa)
                JuniorAtributoRow("Físico",      jogador.fisico)

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // Contrato e salário
                Text(
                    "Contrato: ${jogador.contratoAnos} ano(s)  ·  ${formatarSaldo(jogador.salario)}/mês",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (confirmarPromocao) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Promover ${jogador.nomeAbreviado} ao elenco principal?",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { confirmarPromocao = false },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cancelar") }
                                Button(
                                    onClick = onPromover,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Confirmar") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!confirmarPromocao) {
                Button(onClick = { confirmarPromocao = true }) {
                    Text("Promover ao Elenco")
                }
            }
        },
        dismissButton = {
            if (!confirmarPromocao) {
                TextButton(onClick = onFechar) { Text("Fechar") }
            }
        }
    )
}

@Composable
private fun JuniorAtributoRow(label: String, valor: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(90.dp)
        )
        LinearProgressIndicator(
            progress = { valor / 99f },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
            color = when {
                valor >= 75 -> Color(0xFF4CAF50)
                valor >= 55 -> MaterialTheme.colorScheme.primary
                else        -> MaterialTheme.colorScheme.outline
            }
        )
        Spacer(Modifier.width(6.dp))
        Text(
            valor.toString(),
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.width(24.dp)
        )
    }
}
