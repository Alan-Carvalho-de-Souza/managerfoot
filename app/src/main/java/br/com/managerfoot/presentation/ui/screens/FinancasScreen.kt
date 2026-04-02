package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.ui.components.SecaoHeader
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.viewmodel.FinancasViewModel

private val NOMES_MESES_FIN = listOf(
    "", "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancasScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: FinancasViewModel = hiltViewModel()
) {
    val elenco by vm.elenco.collectAsState()
    val mesAtual by vm.mesAtual.collectAsState()
    val anoAtual by vm.anoAtual.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    val totalFolha = elenco.sumOf { it.salario }
    val mesLabel = NOMES_MESES_FIN.getOrElse(mesAtual) { "" }
    val anoLabel = anoAtual.toString()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finanças") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        bottomBar = {
            // Rodapé fixo com total da folha salarial
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total da folha salarial",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${formatarSaldo(totalFolha)}/mês",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        "${elenco.size} jogadores no elenco",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            item {
                // Cabeçalho de período vigente
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Competência",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "$mesLabel $anoLabel",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            item {
                SecaoHeader("Salários do elenco")
            }

            items(elenco) { jogador ->
                SalarioJogadorRow(jogador = jogador)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun SalarioJogadorRow(jogador: Jogador) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                jogador.nome,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${jogador.posicao.abreviacao} · ${jogador.idade} anos · Contrato: ${jogador.contratoAnos} ano${if (jogador.contratoAnos != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${formatarSaldo(jogador.salario)}/mês",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
