package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.domain.model.Time
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.ForcaBadge
import br.com.managerfoot.presentation.ui.components.PosicaoBadge
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SecaoHeader
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.components.corSetor
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.ClubesViewModel

// ═══════════════════════════════════════════════════════════
//  ClubesScreen — Tactical Dark
//  Browser de todos os clubes (BR/ARG/URU) + elenco do clube
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

    var jogadorParaOfertar by remember { mutableStateOf<Jogador?>(null) }

    jogadorParaOfertar?.let { jogador ->
        OfertaConfirmDialog(
            jogador = jogador,
            saldo = saldo,
            onConfirmar = {
                vm.fazerOferta(jogador)
                jogadorParaOfertar = null
            },
            onDismiss = { jogadorParaOfertar = null }
        )
    }

    if (timeSelecionado != null) {
        ClubeDetalheTela(
            time = timeSelecionado!!,
            elenco = elenco,
            saldo = saldo,
            mensagem = mensagem,
            onVoltar = { vm.limparTimeSelecionado() },
            onOferta = { jogadorParaOfertar = it }
        )
    } else {
        ClubesListaTela(
            times = times,
            onVoltar = onVoltar,
            onSelecionar = { vm.selecionarTime(it) }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Lista de clubes (com filtro de país)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClubesListaTela(
    times: List<Time>,
    onVoltar: () -> Unit,
    onSelecionar: (Time) -> Unit
) {
    var filtro by remember { mutableStateOf("Todos") }
    var dropdownAberto by remember { mutableStateOf(false) }

    val opcoesFiltro = remember(times) {
        listOf("Todos") + times.map { it.pais }.distinct().sorted()
    }
    val timesFiltrados = remember(times, filtro) {
        val base = if (filtro == "Todos") times else times.filter { it.pais == filtro }
        base.sortedBy { it.nome }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Clubes",
            subtitulo = "${times.size} clubes cadastrados",
            onVoltar = onVoltar
        )

        if (times.isEmpty()) {
            EmptyState("Carregando clubes…")
        } else {
            // Filtro de país
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            ) {
                ExposedDropdownMenuBox(
                    expanded = dropdownAberto,
                    onExpandedChange = { dropdownAberto = it }
                ) {
                    OutlinedTextField(
                        value = if (filtro == "Todos") "Todos os países" else filtro,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Filtrar por país", fontWeight = FontWeight.SemiBold) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownAberto) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.md),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenElectric,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedLabelColor = GreenElectric
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownAberto,
                        onDismissRequest = { dropdownAberto = false }
                    ) {
                        opcoesFiltro.forEach { opcao ->
                            val sel = opcao == filtro
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (opcao == "Todos") "Todos os países" else opcao,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (sel) GreenElectric else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = { filtro = opcao; dropdownAberto = false }
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                item { SecaoHeader("${timesFiltrados.size} clubes") }
                items(timesFiltrados) { time ->
                    ClubeCard(time = time, onClick = { onSelecionar(time) })
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Detalhe de um clube — elenco com botão de oferta
// ─────────────────────────────────────────────────────────────
@Composable
private fun ClubeDetalheTela(
    time: Time,
    elenco: List<Jogador>,
    saldo: Long,
    mensagem: String?,
    onVoltar: () -> Unit,
    onOferta: (Jogador) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = time.nome,
            subtitulo = "${nomeDivisao(time.divisao)} · ${time.pais}",
            onVoltar = onVoltar
        )

        // Card de saldo (verde elétrico destacado)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, GreenMid)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "SEU SALDO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatarSaldo(saldo),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (saldo >= 0) MoneyPositive else MoneyNegative
                    )
                }
                TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 48.dp)
            }
        }

        if (elenco.isEmpty()) {
            EmptyState("Carregando elenco…")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item { SecaoHeader("${elenco.size} jogadores") }
                items(elenco) { jogador ->
                    ClubeJogadorLinha(
                        jogador = jogador,
                        podeContratar = saldo >= jogador.valorMercado,
                        onOferta = { onOferta(jogador) }
                    )
                }
            }
        }

        // Snackbar inferior
        mensagem?.let {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, GreenMid)
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(Spacing.md)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de um clube na lista
// ─────────────────────────────────────────────────────────────
@Composable
private fun ClubeCard(time: Time, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    time.nome,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    DivisaoChip(divisao = time.divisao)
                    Text(
                        text = if (time.pais == "Argentina" || time.pais == "Uruguay" || time.pais == "Uruguai") time.pais
                               else time.estado,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DivisaoChip(divisao: Int) {
    val cor = when (divisao) {
        1, 5 -> GreenElectric                    // 1ª divisão BR/ARG
        2, 6 -> LibertadoresBlue                 // 2ª divisão
        3 -> SulAmericanaTeal                    // 3ª divisão
        4 -> AmberAccent                         // 4ª divisão
        9 -> GreenElectric                       // 1ª URU
        10 -> LibertadoresBlue                   // 2ª URU
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = cor.copy(alpha = 0.15f),
        border = BorderStroke(0.5.dp, cor.copy(alpha = 0.5f))
    ) {
        Text(
            nomeDivisao(divisao),
            style = MaterialTheme.typography.labelSmall,
            color = cor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Linha de jogador no elenco do clube (com faixa lateral)
// ─────────────────────────────────────────────────────────────
@Composable
private fun ClubeJogadorLinha(
    jogador: Jogador,
    podeContratar: Boolean,
    onOferta: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Faixa lateral pelo setor
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(corSetor(jogador.posicao.setor))
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PosicaoBadge(
                    abreviacao = jogador.posicao.abreviacao,
                    setor = jogador.posicao.setor
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        jogador.nome,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${jogador.idade} anos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ForcaBadge(jogador.forca)
                Button(
                    onClick = onOferta,
                    enabled = podeContratar,
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (podeContratar) MaterialTheme.colorScheme.primary
                                         else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        formatarSaldo(jogador.valorMercado),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Dialog de confirmação da oferta
// ─────────────────────────────────────────────────────────────
@Composable
private fun OfertaConfirmDialog(
    jogador: Jogador,
    saldo: Long,
    onConfirmar: () -> Unit,
    onDismiss: () -> Unit
) {
    val saldoSuficiente = saldo >= jogador.valorMercado

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PosicaoBadge(jogador.posicao.abreviacao, jogador.posicao.setor)
                Text("Confirmar oferta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                    ) {
                        Text(jogador.nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${jogador.posicao.name.replace("_", " ")} · ${jogador.idade} anos · Força ${jogador.forca}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Valor do jogador", style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatarSaldo(jogador.valorMercado),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Seu saldo", style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatarSaldo(saldo),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (saldoSuficiente) MoneyPositive else MoneyNegative
                    )
                }
                if (!saldoSuficiente) {
                    Text(
                        "⚠ Saldo insuficiente para essa contratação.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirmar, enabled = saldoSuficiente) {
                Text("Fazer oferta", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun nomeDivisao(divisao: Int): String = when (divisao) {
    1 -> "Série A"
    2 -> "Série B"
    3 -> "Série C"
    4 -> "Série D"
    5 -> "Primera División"
    6 -> "Primera Nacional"
    9 -> "Primera División Uruguaia"
    10 -> "Segunda División Uruguaia"
    else -> "Divisão $divisao"
}
