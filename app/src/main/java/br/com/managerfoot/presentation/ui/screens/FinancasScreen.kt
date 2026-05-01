package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.dao.CalendarioPartidaDto
import br.com.managerfoot.data.dao.TransferenciaDetalhe
import br.com.managerfoot.data.database.entities.FinancaEntity
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.ui.components.KpiCard
import br.com.managerfoot.presentation.ui.components.MoneyDelta
import br.com.managerfoot.presentation.ui.components.PosicaoBadge
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SectionCard
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.components.TabRowPill
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.ui.theme.MoneyNegative
import br.com.managerfoot.presentation.ui.theme.MoneyPositive
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.viewmodel.FinancasViewModel

private val NOMES_MESES_FIN = listOf(
    "", "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    "Encerramento"
)

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
    val periodoLabel = "$mesLabel $anoAtual"

    // Receitas e despesas do mês atual (último FinancaEntity)
    val ultimaFinanca = despesasMensais.maxByOrNull { it.mes }
    val receitasMes = (ultimaFinanca?.receitaBilheteria ?: 0L) +
            (ultimaFinanca?.receitaPatrocinio ?: 0L) +
            (ultimaFinanca?.receitaTransferencias ?: 0L) +
            (ultimaFinanca?.receitaPremiacoes ?: 0L)
    val despesasMes = (ultimaFinanca?.despesaSalarios ?: 0L) +
            (ultimaFinanca?.despesaTransferencias ?: 0L) +
            (ultimaFinanca?.despesaInfraestrutura ?: 0L) +
            (ultimaFinanca?.despesaAmpliacaoEstadio ?: 0L)

    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Salários", "Receitas", "Despesas", "Saldo")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Finanças",
            subtitulo = periodoLabel,
            onVoltar = onVoltar
        )

        // KPIs hero — sempre visíveis
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            KpiCard(
                label = "Saldo",
                valor = formatarSaldo(saldoAtual),
                accent = if (saldoAtual >= 0) MoneyPositive else MoneyNegative,
                modifier = Modifier.weight(1.2f)
            )
            KpiCard(
                label = "Receitas",
                valor = if (receitasMes > 0) formatarSaldo(receitasMes) else "—",
                accent = MoneyPositive,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Despesas",
                valor = if (despesasMes > 0) formatarSaldo(despesasMes) else "—",
                accent = MoneyNegative,
                modifier = Modifier.weight(1f)
            )
        }

        TabRowPill(
            abas = tabs,
            selecionada = tabIndex,
            onSelecionar = { tabIndex = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.md)
        )

        Box(modifier = Modifier.weight(1f)) {
            when (tabIndex) {
                0 -> SalariosTab(elenco = elenco, totalFolha = totalFolha)
                1 -> ReceitasTab(receitasPartidas, transferencias, despesasMensais)
                2 -> DespesasTab(despesasMensais, compras)
                3 -> SaldoCaixaTab(saldoAtual, receitasPartidas, transferencias, despesasMensais, compras)
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Aba Salários
// ─────────────────────────────────────────────
@Composable
private fun SalariosTab(elenco: List<Jogador>, totalFolha: Long) {
    val ordenado = remember(elenco) { elenco.sortedByDescending { it.salario } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Resumo da folha salarial
        item {
            SectionCard(titulo = "Folha salarial") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Total mensal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${ordenado.size} jogadores",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    MoneyDelta(
                        centavos = -totalFolha,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        mostrarSinal = false
                    )
                }
            }
        }
        item { SectionTitle("Salários por jogador") }
        items(ordenado) { jogador ->
            SalarioJogadorRow(jogador = jogador)
        }
        item { Spacer(Modifier.height(Spacing.lg)) }
    }
}

@Composable
private fun SalarioJogadorRow(jogador: Jogador) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        PosicaoBadge(jogador.posicao.abreviacao, jogador.posicao.setor)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                jogador.nome,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${jogador.idade} anos · ${jogador.contratoAnos} ano${if (jogador.contratoAnos != 1) "s" else ""} de contrato",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${formatarSaldo(jogador.salario)}/mês",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MoneyNegative
        )
    }
}

// ─────────────────────────────────────────────
//  Aba Receitas
// ─────────────────────────────────────────────
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
    val totalReceitas = totalBilheteria + totalPatrocinio + totalVendas + totalPremiacoes

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Resumo geral
        item {
            ResumoCard(
                titulo = "Resumo de receitas",
                linhas = buildList {
                    add("Bilheteria" to totalBilheteria)
                    add("Patrocínio" to totalPatrocinio)
                    add("Vendas de jogadores" to totalVendas)
                    if (totalPremiacoes > 0) add("Premiações" to totalPremiacoes)
                },
                total = totalReceitas,
                positivo = true
            )
        }

        if (partidas.isNotEmpty()) {
            item { SectionTitle("Bilheteria por jogo") }
            items(partidas) { p -> PartidaReceitaRow(p) }
        }
        if (patrocinios.isNotEmpty()) {
            item { SectionTitle("Patrocínio mensal") }
            items(patrocinios) { f -> SimpleFinancaRow("Patrocínio", NOMES_MESES_FIN.getOrElse(f.mes) { "—" }, f.receitaPatrocinio, positivo = true) }
        }
        if (vendas.isNotEmpty()) {
            item { SectionTitle("Vendas de jogadores") }
            items(vendas) { v -> TransferenciaRow(v, positivo = true, prefixo = "Para") }
        }
        if (premiacoes.isNotEmpty()) {
            item { SectionTitle("Premiações") }
            items(premiacoes) { f -> SimpleFinancaRow(f.descricaoPremio ?: "Prêmio de campeão", NOMES_MESES_FIN.getOrElse(f.mes) { "—" }, f.receitaPremiacoes, positivo = true) }
        }
        if (partidas.isEmpty() && vendas.isEmpty() && patrocinios.isEmpty() && premiacoes.isEmpty()) {
            item { EmptyFinancas("Nenhuma receita registrada ainda") }
        }
        item { Spacer(Modifier.height(Spacing.lg)) }
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
    val fechamentos = historico.filter { it.despesaSalarios > 0 }
    val upgrades = historico.filter { it.despesaAmpliacaoEstadio > 0 }
    val totalSalarios = fechamentos.sumOf { it.despesaSalarios }
    val totalAmpliacoes = upgrades.sumOf { it.despesaAmpliacaoEstadio }
    val totalCompras = compras.sumOf { it.valor }
    val totalGeral = totalSalarios + totalAmpliacoes + totalCompras

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            ResumoCard(
                titulo = "Resumo de despesas",
                linhas = listOf(
                    "Salários pagos" to totalSalarios,
                    "Reformas no estádio" to totalAmpliacoes,
                    "Contratações" to totalCompras
                ),
                total = totalGeral,
                positivo = false
            )
        }

        if (fechamentos.isNotEmpty()) {
            item { SectionTitle("Folha salarial por mês") }
            items(fechamentos) { f -> SimpleFinancaRow("Folha", NOMES_MESES_FIN.getOrElse(f.mes) { "—" }, f.despesaSalarios, positivo = false) }
        }
        if (upgrades.isNotEmpty()) {
            item { SectionTitle("Reformas no estádio") }
            items(upgrades) { f -> SimpleFinancaRow("Reforma", NOMES_MESES_FIN.getOrElse(f.mes) { "—" }, f.despesaAmpliacaoEstadio, positivo = false) }
        }
        if (compras.isNotEmpty()) {
            item { SectionTitle("Contratações de jogadores") }
            items(compras) { c -> TransferenciaRow(c, positivo = false, prefixo = "De") }
        }
        if (historico.isEmpty() && compras.isEmpty()) {
            item { EmptyFinancas("Nenhuma despesa registrada ainda") }
        }
        item { Spacer(Modifier.height(Spacing.lg)) }
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
    val totalBilheteria = partidas.sumOf { it.receitaPartida ?: 0L }
    val totalPatrocinio = historico.sumOf { it.receitaPatrocinio }
    val totalVendas = vendas.sumOf { it.valor }
    val totalPremiacoes = historico.sumOf { it.receitaPremiacoes }
    val totalEntradas = totalBilheteria + totalPatrocinio + totalVendas + totalPremiacoes

    val totalSalarios = historico.sumOf { it.despesaSalarios }
    val totalAmpliacoes = historico.sumOf { it.despesaAmpliacaoEstadio }
    val totalCompras = compras.sumOf { it.valor }
    val totalSaidas = totalSalarios + totalAmpliacoes + totalCompras

    val resultado = totalEntradas - totalSaidas

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Hero do saldo atual
        item {
            SaldoHeroCard(saldoAtual = saldoAtual)
        }

        // Balanço da temporada
        item {
            SectionCard(titulo = "Balanço da temporada") {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    BalancoSubtitulo("Entradas")
                    BalancoLinha("Bilheteria", totalBilheteria, positivo = true)
                    BalancoLinha("Patrocínio", totalPatrocinio, positivo = true)
                    BalancoLinha("Vendas de jogadores", totalVendas, positivo = true)
                    if (totalPremiacoes > 0) {
                        BalancoLinha("Premiações", totalPremiacoes, positivo = true)
                    }
                    BalancoSubtotal("Subtotal entradas", totalEntradas, positivo = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    BalancoSubtitulo("Saídas")
                    BalancoLinha("Salários pagos", totalSalarios, positivo = false)
                    BalancoLinha("Reformas no estádio", totalAmpliacoes, positivo = false)
                    BalancoLinha("Contratações", totalCompras, positivo = false)
                    BalancoSubtotal("Subtotal saídas", totalSaidas, positivo = false)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Resultado líquido",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        MoneyDelta(
                            centavos = resultado,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Spacing.lg)) }
    }
}

// ─── Componentes auxiliares (uso interno da tela) ─────────────

@Composable
private fun ResumoCard(
    titulo: String,
    linhas: List<Pair<String, Long>>,
    total: Long,
    positivo: Boolean
) {
    SectionCard(titulo = titulo) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            linhas.forEach { (label, valor) ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatarSaldo(valor),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (positivo) MoneyPositive else MoneyNegative
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    formatarSaldo(total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (positivo) MoneyPositive else MoneyNegative
                )
            }
        }
    }
}

@Composable
private fun SaldoHeroCard(saldoAtual: Long) {
    val cor = if (saldoAtual >= 0) MoneyPositive else MoneyNegative
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, cor.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cor.copy(alpha = 0.08f))
                .padding(Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    "SALDO EM CAIXA",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cor,
                    letterSpacing = 2.sp
                )
                Text(
                    formatarSaldo(saldoAtual),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = cor
                )
            }
        }
    }
}

@Composable
private fun PartidaReceitaRow(partida: CalendarioPartidaDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "vs ${partida.nomeFora}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${"%,d".format(partida.torcedores ?: 0)} torcedores · ${partida.golsCasa} × ${partida.golsFora}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatarSaldo(partida.receitaPartida ?: 0L),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MoneyPositive
        )
    }
}

@Composable
private fun TransferenciaRow(t: TransferenciaDetalhe, positivo: Boolean, prefixo: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                t.jogadorNome,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$prefixo: ${if (positivo) t.destinoNome ?: "livre" else t.origemNome ?: "livre"} · ${t.posicao}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatarSaldo(t.valor),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (positivo) MoneyPositive else MoneyNegative
        )
    }
}

@Composable
private fun SimpleFinancaRow(label: String, mes: String, valor: Long, positivo: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                mes,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatarSaldo(valor),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (positivo) MoneyPositive else MoneyNegative
        )
    }
}

@Composable
private fun BalancoSubtitulo(texto: String) {
    Text(
        text = texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp
    )
}

@Composable
private fun BalancoLinha(label: String, valor: Long, positivo: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            formatarSaldo(valor),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (positivo) MoneyPositive else MoneyNegative
        )
    }
}

@Composable
private fun BalancoSubtotal(label: String, valor: Long, positivo: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            formatarSaldo(valor),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (positivo) MoneyPositive else MoneyNegative
        )
    }
}

@Composable
private fun EmptyFinancas(texto: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
