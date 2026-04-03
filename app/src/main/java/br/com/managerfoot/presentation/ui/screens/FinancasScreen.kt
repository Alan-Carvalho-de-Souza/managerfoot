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
import br.com.managerfoot.data.dao.CalendarioPartidaDto
import br.com.managerfoot.data.dao.TransferenciaDetalhe
import br.com.managerfoot.data.database.entities.FinancaEntity
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
    val receitasPartidas by vm.receitasPartidas.collectAsState()
    val transferencias by vm.transferencias.collectAsState()
    val despesasMensais by vm.despesasMensais.collectAsState()
    val compras by vm.compras.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    val totalFolha = elenco.sumOf { it.salario }
    val mesLabel = NOMES_MESES_FIN.getOrElse(mesAtual) { "" }
    val anoLabel = anoAtual.toString()

    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Salários", "Receitas", "Despesas")

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
            if (tabIndex == 0) {
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
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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

            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            when (tabIndex) {
                0 -> SalariosTab(elenco)
                1 -> ReceitasTab(receitasPartidas, transferencias)
                2 -> DespesasTab(despesasMensais, compras)
            }
        }
    }
}

@Composable
private fun SalariosTab(elenco: List<Jogador>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item { SecaoHeader("Salários do elenco") }
        items(elenco) { jogador ->
            SalarioJogadorRow(jogador = jogador)
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun ReceitasTab(
    partidas: List<CalendarioPartidaDto>,
    vendas: List<TransferenciaDetalhe>
) {
    val totalBilheteria = partidas.sumOf { it.receitaPartida ?: 0L }
    val totalVendas = vendas.sumOf { it.valor }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Resumo total
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Resumo de Receitas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bilheteria total", style = MaterialTheme.typography.bodySmall)
                        Text(formatarSaldo(totalBilheteria), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Vendas de jogadores", style = MaterialTheme.typography.bodySmall)
                        Text(formatarSaldo(totalVendas), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formatarSaldo(totalBilheteria + totalVendas), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (partidas.isNotEmpty()) {
            item { SecaoHeader("Bilheteria por jogo") }
            items(partidas) { partida ->
                PartidaReceitaRow(partida)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        if (vendas.isNotEmpty()) {
            item { SecaoHeader("Vendas de jogadores") }
            items(vendas) { venda ->
                VendaRow(venda)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        if (partidas.isEmpty() && vendas.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nenhuma receita registrada ainda",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PartidaReceitaRow(partida: CalendarioPartidaDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "x ${partida.nomeFora}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "\uD83D\uDC65 ${"%,d".format(partida.torcedores ?: 0)} torcedores · ${partida.golsCasa} × ${partida.golsFora}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatarSaldo(partida.receitaPartida ?: 0L),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun VendaRow(venda: TransferenciaDetalhe) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                venda.jogadorNome,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Para: ${venda.destinoNome ?: "livre"} · ${venda.posicao}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatarSaldo(venda.valor),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
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

// ─────────────────────────────────────────────
//  Aba Despesas
// ─────────────────────────────────────────────
@Composable
private fun DespesasTab(
    historico: List<FinancaEntity>,
    compras: List<TransferenciaDetalhe>
) {
    val totalSalarios    = historico.sumOf { it.despesaSalarios }
    val totalInfra       = historico.sumOf { it.despesaInfraestrutura }
    val totalCompras     = compras.sumOf { it.valor }
    val totalGeral       = totalSalarios + totalInfra + totalCompras

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Resumo
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Resumo de Despesas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    DespesaResumoRow("Salários pagos",       totalSalarios)
                    DespesaResumoRow("Manutenção do estádio", totalInfra)
                    DespesaResumoRow("Contratações",          totalCompras)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Total",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatarSaldo(totalGeral),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Histórico mensal de salários + infraestrutura
        if (historico.isNotEmpty()) {
            item { SecaoHeader("Folha Salarial por Mês") }
            items(historico) { financa ->
                FechamentoMensalRow(financa)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        // Compras de jogadores
        if (compras.isNotEmpty()) {
            item { SecaoHeader("Contratações de Jogadores") }
            items(compras) { compra ->
                CompraRow(compra)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        if (historico.isEmpty() && compras.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nenhuma despesa registrada ainda",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DespesaResumoRow(label: String, valor: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            formatarSaldo(valor),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun FechamentoMensalRow(financa: FinancaEntity) {
    val mesNome = NOMES_MESES_FIN.getOrElse(financa.mes) { "Mês ${financa.mes}" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                mesNome,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Infra: ${formatarSaldo(financa.despesaInfraestrutura)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${formatarSaldo(financa.despesaSalarios)}/mês",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun CompraRow(compra: TransferenciaDetalhe) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                compra.jogadorNome,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "De: ${compra.origemNome ?: "livre"} · ${compra.posicao}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatarSaldo(compra.valor),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

