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
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    "Encerramento"
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
    val saldoAtual by vm.saldoAtual.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    val totalFolha = elenco.sumOf { it.salario }
    val mesLabel = NOMES_MESES_FIN.getOrElse(mesAtual) { "" }
    val anoLabel = anoAtual.toString()

    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Salários", "Receitas", "Despesas", "Saldo em Caixa")

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
                1 -> ReceitasTab(receitasPartidas, transferencias, despesasMensais)
                2 -> DespesasTab(despesasMensais, compras)
                3 -> SaldoCaixaTab(saldoAtual, receitasPartidas, transferencias, despesasMensais, compras)
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
    vendas: List<TransferenciaDetalhe>,
    historico: List<FinancaEntity>
) {
    val totalBilheteria = partidas.sumOf { it.receitaPartida ?: 0L }
    val totalVendas = vendas.sumOf { it.valor }
    val patrocinios = historico.filter { it.receitaPatrocinio > 0 }
    val totalPatrocinio = patrocinios.sumOf { it.receitaPatrocinio }
    val premiacoes = historico.filter { it.receitaPremiacoes > 0 }
    val totalPremiacoes = premiacoes.sumOf { it.receitaPremiacoes }

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
                        Text("Patrocínio total", style = MaterialTheme.typography.bodySmall)
                        Text(formatarSaldo(totalPatrocinio), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Vendas de jogadores", style = MaterialTheme.typography.bodySmall)
                        Text(formatarSaldo(totalVendas), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    if (totalPremiacoes > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Premiações", style = MaterialTheme.typography.bodySmall)
                            Text(formatarSaldo(totalPremiacoes), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formatarSaldo(totalBilheteria + totalPatrocinio + totalVendas + totalPremiacoes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

        if (patrocinios.isNotEmpty()) {
            item { SecaoHeader("Patrocínio mensal") }
            items(patrocinios) { financa ->
                PatrocinioMensalRow(financa)
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

        if (premiacoes.isNotEmpty()) {
            item { SecaoHeader("Premiações") }
            items(premiacoes) { financa ->
                PremiacaoRow(financa)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        if (partidas.isEmpty() && vendas.isEmpty() && patrocinios.isEmpty() && premiacoes.isEmpty()) {
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
private fun PatrocinioMensalRow(financa: FinancaEntity) {
    val mesNome = NOMES_MESES_FIN.getOrElse(financa.mes) { "Mês ${financa.mes}" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Patrocínio",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                mesNome,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatarSaldo(financa.receitaPatrocinio),
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
private fun PremiacaoRow(financa: FinancaEntity) {
    val mesNome = NOMES_MESES_FIN.getOrElse(financa.mes) { "Mês ${financa.mes}" }
    val titulo = financa.descricaoPremio ?: "Prêmio de Campeão"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                titulo,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                mesNome,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatarSaldo(financa.receitaPremiacoes),
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
    val fechamentos      = historico.filter { it.despesaSalarios > 0 }
    val upgrades         = historico.filter { it.despesaAmpliacaoEstadio > 0 }
    val totalSalarios    = fechamentos.sumOf { it.despesaSalarios }
    val totalAmpliacoes  = upgrades.sumOf { it.despesaAmpliacaoEstadio }
    val totalCompras     = compras.sumOf { it.valor }
    val totalGeral       = totalSalarios + totalAmpliacoes + totalCompras

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
                    DespesaResumoRow("Salários pagos",          totalSalarios)
                    DespesaResumoRow("Reformas no estádio",     totalAmpliacoes)
                    DespesaResumoRow("Contratações",             totalCompras)
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
        if (fechamentos.isNotEmpty()) {
            item { SecaoHeader("Folha Salarial por Mês") }
            items(fechamentos) { financa ->
                FechamentoMensalRow(financa)
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        // Reformas no estádio
        if (upgrades.isNotEmpty()) {
            item { SecaoHeader("Reformas no Estádio") }
            items(upgrades) { financa ->
                AmpliacaoEstadioRow(financa)
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
private fun AmpliacaoEstadioRow(financa: FinancaEntity) {
    val mesNome = NOMES_MESES_FIN.getOrElse(financa.mes) { "Mês ${financa.mes}" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Reforma no estádio",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                mesNome,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatarSaldo(financa.despesaAmpliacaoEstadio),
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

// ─────────────────────────────────────────────
//  Aba Saldo em Caixa
// ─────────────────────────────────────────────
@Composable
private fun SaldoCaixaTab(
    saldoAtual: Long,
    partidas: List<CalendarioPartidaDto>,
    vendas: List<TransferenciaDetalhe>,
    historico: List<FinancaEntity>,
    compras: List<TransferenciaDetalhe>
) {
    val totalBilheteria  = partidas.sumOf { it.receitaPartida ?: 0L }
    val totalPatrocinio  = historico.sumOf { it.receitaPatrocinio }
    val totalVendas      = vendas.sumOf { it.valor }
    val totalPremiacoes  = historico.sumOf { it.receitaPremiacoes }
    val totalEntradas    = totalBilheteria + totalPatrocinio + totalVendas + totalPremiacoes

    val totalSalarios    = historico.sumOf { it.despesaSalarios }
    val totalAmpliacoes  = historico.sumOf { it.despesaAmpliacaoEstadio }
    val totalCompras     = compras.sumOf { it.valor }
    val totalSaidas      = totalSalarios + totalAmpliacoes + totalCompras

    val resultado        = totalEntradas - totalSaidas

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Saldo atual em destaque
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (saldoAtual >= 0)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Saldo em Caixa",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        formatarSaldo(saldoAtual),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (saldoAtual >= 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Balanço resumido
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Balanço da Temporada",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    // Entradas
                    SaldoResumoSubtitulo("Entradas")
                    SaldoResumoLinha("Bilheteria",       totalBilheteria, positivo = true)
                    SaldoResumoLinha("Patrocínio",       totalPatrocinio, positivo = true)
                    SaldoResumoLinha("Vendas de jogadores", totalVendas,  positivo = true)
                    SaldoResumoLinha("Premiações",        totalPremiacoes, positivo = true)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Subtotal entradas",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            formatarSaldo(totalEntradas),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Saídas
                    SaldoResumoSubtitulo("Saídas")
                    SaldoResumoLinha("Salários pagos",       totalSalarios,   positivo = false)
                    SaldoResumoLinha("Reformas no estádio",  totalAmpliacoes, positivo = false)
                    SaldoResumoLinha("Contratações",         totalCompras,    positivo = false)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Subtotal saídas",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            formatarSaldo(totalSaidas),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Resultado líquido
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Resultado líquido",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatarSaldo(resultado),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (resultado >= 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaldoResumoSubtitulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SaldoResumoLinha(label: String, valor: Long, positivo: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            formatarSaldo(valor),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (positivo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
