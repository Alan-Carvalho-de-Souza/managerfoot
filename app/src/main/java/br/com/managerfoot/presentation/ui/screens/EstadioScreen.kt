package br.com.managerfoot.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.database.entities.EstadioEntity
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.EstadioViewModel

// ─────────────────────────────────────────────────────────────
//  EstadioScreen — Tactical Dark
//  Capacidade do estadio + upgrades por setor + fator mandante
// ─────────────────────────────────────────────────────────────
@Composable
fun EstadioScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: EstadioViewModel = hiltViewModel()
) {
    val estadio  by vm.estadio.collectAsState()
    val time     by vm.time.collectAsState()
    val mensagem by vm.mensagem.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    mensagem?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2_500)
            vm.limparMensagem()
        }
    }

    val nomeEstadio = time?.estadioNome?.ifBlank { null } ?: "Estádio"

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                titulo = nomeEstadio,
                subtitulo = estadio?.let { "${it.capacidadeTotal.formatarCapacidade()} lugares" },
                onVoltar = onVoltar
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Hero card de capacidade total + fator mandante
                item {
                    estadio?.let { est ->
                        EstadioHeroCard(
                            nome = nomeEstadio,
                            capacidadeTotal = est.capacidadeTotal
                        )
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
                            icone       = "🪑",
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
                            icone       = "💺",
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
                            icone       = "👑",
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

                // Tabela de fator mandante (recolhida)
                item { TabelaFatorMandanteCard() }

                item { Spacer(Modifier.height(Spacing.lg)) }
            }
        }

        // Snackbar inferior (Tactical Dark)
        mensagem?.let { msg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Spacing.md),
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, GreenMid),
                shadowElevation = Elev.floating
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.limparMensagem() }) {
                        Text("OK", color = GreenElectric, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Hero card com capacidade total + fator mandante
// ─────────────────────────────────────────────────────────────
@Composable
private fun EstadioHeroCard(nome: String, capacidadeTotal: Int) {
    val fatorPercent = fatorMandantePercent(capacidadeTotal)
    val fatorLabel = fatorMandanteDescricao(capacidadeTotal)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GreenMid)
    ) {
        Column(
            Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Default.Stadium,
                    contentDescription = null,
                    tint = GreenElectric,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    nome,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "CAPACIDADE TOTAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        capacidadeTotal.formatarCapacidade(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = GreenElectric
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "FATOR MANDANTE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        fatorLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = corDoFator(fatorPercent)
                    )
                }
            }

            Text(
                "Quanto maior o estádio, maior o bônus percentual de desempenho do time jogando em casa. Expanda os setores para fortalecer sua torcida.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de upgrade de um setor
// ─────────────────────────────────────────────────────────────
@Composable
private fun SetorCard(
    nome: String,
    descricao: String,
    icone: String,
    nivel: Int,
    capacidade: Int,
    proximaCap: Int?,
    custo: Long?,
    saldo: Long,
    onUpgrade: () -> Unit
) {
    val nivelMaximo = nivel >= 10
    val nivelCor = when {
        nivelMaximo -> GoldChampion
        nivel >= 7  -> PromotionGreen
        nivel >= 4  -> LibertadoresBlue
        else        -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (nivelMaximo) GoldChampion.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(icone, fontSize = 24.sp)
                    Column {
                        Text(
                            nome,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            descricao,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = nivelCor.copy(alpha = 0.18f),
                    border = BorderStroke(0.5.dp, nivelCor.copy(alpha = 0.5f))
                ) {
                    Text(
                        "Nv. $nivel/10",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = nivelCor,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            // Barra de progresso do nível
            LinearProgressIndicator(
                progress = { nivel / 10f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = nivelCor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "ATUAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        capacidade.formatarCapacidade(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (proximaCap != null) {
                    Text(
                        "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "PRÓXIMO NÍVEL",
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenElectric,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            proximaCap.formatarCapacidade(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GreenElectric
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            if (custo != null) {
                val podeComprar = saldo >= custo
                Button(
                    onClick = onUpgrade,
                    enabled = podeComprar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        "Expandir · ${formatarSaldo(custo)}",
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!podeComprar) {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        "⚠ Saldo insuficiente",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    color = GoldChampion.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, GoldChampion.copy(alpha = 0.5f))
                ) {
                    Text(
                        "🏆 NÍVEL MÁXIMO ATINGIDO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoldChampion,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.sm),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Tabela de fator mandante (recolhida por padrão)
// ─────────────────────────────────────────────────────────────
@Composable
private fun TabelaFatorMandanteCard() {
    var expandida by remember { mutableStateOf(false) }
    val faixas = listOf(
        "≥ 5.000 lugares"   to "+1%",
        "≥ 10.000 lugares"  to "+2%",
        "≥ 15.000 lugares"  to "+3%",
        "≥ 20.000 lugares"  to "+4%",
        "≥ 25.000 lugares"  to "+5%",
        "≥ 30.000 lugares"  to "+6%",
        "≥ 35.000 lugares"  to "+7%",
        "≥ 40.000 lugares"  to "+8%",
        "≥ 50.000 lugares"  to "+9%",
        "≥ 60.000 lugares"  to "+10%",
        "≥ 80.000 lugares"  to "+11%",
        "≥ 100.000 lugares" to "+12% (máximo)"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandida = !expandida },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tabela · Bônus Mandante",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expandida) Icons.Default.KeyboardArrowUp
                                  else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expandida) "Recolher" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expandida) {
                Column(
                    modifier = Modifier.padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    faixas.forEachIndexed { idx, (label, valor) ->
                        FatorRow(label = label, valor = valor, ehMaximo = idx == faixas.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun FatorRow(label: String, valor: String, ehMaximo: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            valor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (ehMaximo) GoldChampion else GreenElectric
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Helpers de formatação e cálculo
// ─────────────────────────────────────────────────────────────
private fun Int.formatarCapacidade(): String {
    return when {
        this >= 1_000 -> "%,.0f".format(this.toDouble()).replace(",", ".")
        else -> this.toString()
    }
}

private fun fatorMandantePercent(capacidade: Int): Int = when {
    capacidade >= 100_000 -> 12
    capacidade >=  80_000 -> 11
    capacidade >=  60_000 -> 10
    capacidade >=  50_000 -> 9
    capacidade >=  40_000 -> 8
    capacidade >=  35_000 -> 7
    capacidade >=  30_000 -> 6
    capacidade >=  25_000 -> 5
    capacidade >=  20_000 -> 4
    capacidade >=  15_000 -> 3
    capacidade >=  10_000 -> 2
    capacidade >=   5_000 -> 1
    else                  -> 0
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
    else                  -> "0%"
}

private fun corDoFator(percent: Int): Color = when {
    percent >= 10 -> GoldChampion
    percent >= 6  -> PromotionGreen
    percent >= 3  -> GreenElectric
    percent >= 1  -> LibertadoresBlue
    else          -> AmberAccent
}
