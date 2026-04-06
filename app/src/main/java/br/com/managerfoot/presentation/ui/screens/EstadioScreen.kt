package br.com.managerfoot.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.database.entities.EstadioEntity
import br.com.managerfoot.presentation.viewmodel.EstadioViewModel

// ─────────────────────────────────────────────────
//  EstadioScreen
// ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstadioScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: EstadioViewModel = hiltViewModel()
) {
    val estadio by vm.estadio.collectAsState()
    val time    by vm.time.collectAsState()
    val mensagem by vm.mensagem.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    mensagem?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2_500)
            vm.limparMensagem()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(time?.estadioNome?.ifBlank { null } ?: "Estádio") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = {
            mensagem?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { vm.limparMensagem() }) { Text("OK") }
                    }
                ) { Text(msg) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Resumo do estádio
            item {
                estadio?.let { est ->
                    val fatorCasa = fatorMandanteDescricao(est.capacidadeTotal)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                time?.estadioNome?.ifBlank { null } ?: "Estádio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Capacidade Total",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                est.capacidadeTotal.formatarCapacidade(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Fator mandante: $fatorCasa",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Quanto maior o estádio, maior o bônus percentual de desempenho do time jogando em casa. Expanda os setores para aumentar a capacidade e fortalecer sua torcida.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Setor: Arquibancadas
            item {
                estadio?.let { est ->
                    val custo = if (est.nivelArquibancada < 10)
                        EstadioEntity.CUSTO_ARQUIBANCADA[est.nivelArquibancada]
                    else null
                    SetorCard(
                        nome        = "Arquibancadas",
                        descricao   = "Assentos populares de grande volume",
                        nivel       = est.nivelArquibancada,
                        capacidade  = EstadioEntity.CAPACIDADE_ARQUIBANCADA[est.nivelArquibancada],
                        proximaCap  = if (est.nivelArquibancada < 10)
                            EstadioEntity.CAPACIDADE_ARQUIBANCADA[est.nivelArquibancada + 1] else null,
                        custo       = custo,
                        saldo       = time?.saldo ?: 0L,
                        onUpgrade   = { vm.upgradeSetor(timeId, 0) }
                    )
                }
            }

            // Setor: Cadeiras
            item {
                estadio?.let { est ->
                    val custo = if (est.nivelCadeira < 10)
                        EstadioEntity.CUSTO_CADEIRA[est.nivelCadeira]
                    else null
                    SetorCard(
                        nome        = "Cadeiras",
                        descricao   = "Assentos numerados com maior conforto",
                        nivel       = est.nivelCadeira,
                        capacidade  = EstadioEntity.CAPACIDADE_CADEIRA[est.nivelCadeira],
                        proximaCap  = if (est.nivelCadeira < 10)
                            EstadioEntity.CAPACIDADE_CADEIRA[est.nivelCadeira + 1] else null,
                        custo       = custo,
                        saldo       = time?.saldo ?: 0L,
                        onUpgrade   = { vm.upgradeSetor(timeId, 1) }
                    )
                }
            }

            // Setor: Camarote
            item {
                estadio?.let { est ->
                    val custo = if (est.nivelCamarote < 10)
                        EstadioEntity.CUSTO_CAMAROTE[est.nivelCamarote]
                    else null
                    SetorCard(
                        nome        = "Camarote",
                        descricao   = "Área VIP com serviços premium",
                        nivel       = est.nivelCamarote,
                        capacidade  = EstadioEntity.CAPACIDADE_CAMAROTE[est.nivelCamarote],
                        proximaCap  = if (est.nivelCamarote < 10)
                            EstadioEntity.CAPACIDADE_CAMAROTE[est.nivelCamarote + 1] else null,
                        custo       = custo,
                        saldo       = time?.saldo ?: 0L,
                        onUpgrade   = { vm.upgradeSetor(timeId, 2) }
                    )
                }
            }

            // Tabela de fator mandante (recolhida por padrão)
            item {
                var expandida by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandida = !expandida },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Tabela — Bônus Mandante",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (expandida) Icons.Default.KeyboardArrowUp
                                              else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (expandida) "Recolher" else "Expandir"
                            )
                        }
                        AnimatedVisibility(visible = expandida) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FatorRow("≥ 5.000 lugares",   "+1%")
                                FatorRow("≥ 10.000 lugares",  "+2%")
                                FatorRow("≥ 15.000 lugares",  "+3%")
                                FatorRow("≥ 20.000 lugares",  "+4%")
                                FatorRow("≥ 25.000 lugares",  "+5%")
                                FatorRow("≥ 30.000 lugares",  "+6%")
                                FatorRow("≥ 35.000 lugares",  "+7%")
                                FatorRow("≥ 40.000 lugares",  "+8%")
                                FatorRow("≥ 50.000 lugares",  "+9%")
                                FatorRow("≥ 60.000 lugares",  "+10%")
                                FatorRow("≥ 80.000 lugares",  "+11%")
                                FatorRow("≥ 100.000 lugares", "+12% (máximo)")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SetorCard(
    nome: String,
    descricao: String,
    nivel: Int,
    capacidade: Int,
    proximaCap: Int?,
    custo: Long?,
    saldo: Long,
    onUpgrade: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(descricao, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        "Nível $nivel / 10",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Capacidade atual", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(capacidade.formatarCapacidade(), style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold)
                }
                if (proximaCap != null) {
                    Column {
                        Text("Próximo nível", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(proximaCap.formatarCapacidade(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (custo != null) {
                val podeComprar = saldo >= custo
                Button(
                    onClick = onUpgrade,
                    enabled = podeComprar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Expandir — ${custo.formatarDinheiro()}")
                }
                if (!podeComprar) {
                    Text(
                        "Saldo insuficiente",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Text(
                    "Nível máximo atingido",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FatorRow(label: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun Int.formatarCapacidade(): String {
    return when {
        this >= 1_000 -> "%,.0f".format(this.toDouble()).replace(",", ".")
        else -> this.toString()
    }
}

private fun Long.formatarDinheiro(): String {
    val reais = this / 100.0
    return when {
        reais >= 1_000_000.0 -> "R$ %.1fM".format(reais / 1_000_000.0)
        reais >= 1_000.0     -> "R$ %.0fk".format(reais / 1_000.0)
        else                 -> "R$ %.0f".format(reais)
    }
}

private fun fatorMandanteDescricao(capacidade: Int): String = when {
    capacidade >= 100_000 -> "+12%"
    capacidade >=  80_000 -> "+11%"
    capacidade >=  60_000 -> "+10%"
    capacidade >=  50_000 -> "+9%"
    capacidade >=  40_000 -> "+8%"
    capacidade >=  35_000 -> "+7%"
    capacidade >=  30_000 -> "+6%"
    capacidade >=  25_000 -> "+5%"
    capacidade >=  20_000 -> "+4%"
    capacidade >=  15_000 -> "+3%"
    capacidade >=  10_000 -> "+2%"
    capacidade >=   5_000 -> "+1%"
    else                  -> "0% (menos de 5.000 lugares)"
}
