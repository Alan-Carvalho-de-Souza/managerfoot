package br.com.managerfoot.presentation.ui.screens

import br.com.managerfoot.presentation.ui.theme.fundoApp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.database.entities.Posicao
import br.com.managerfoot.domain.engine.CalculadoraMercado
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.ui.components.FilterChipPill
import br.com.managerfoot.presentation.ui.components.ForcaBadge
import br.com.managerfoot.presentation.ui.components.PosicaoBadge
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.components.TabRowPill
import br.com.managerfoot.presentation.ui.components.corSetor
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.ui.theme.MoneyNegative
import br.com.managerfoot.presentation.ui.theme.MoneyPositive
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.viewmodel.FiltrosPesquisaJogador
import br.com.managerfoot.presentation.viewmodel.JogadoresViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════
//  JogadoresScreen
//  Tab 0 — Elenco com barra de progressão por jogador
//  Tab 1 — Pesquisa global com filtros de posição/força/idade
// ═══════════════════════════════════════════════════════════
@Composable
fun JogadoresScreen(
    timeId: Int,
    onVoltar: () -> Unit = {},
    onIrParaHistorico: ((Int) -> Unit)? = null,
    vm: JogadoresViewModel = hiltViewModel()
) {
    val elenco            by vm.elenco.collectAsState()
    val resultadoPesquisa by vm.resultadoPesquisa.collectAsState()
    val filtros           by vm.filtros.collectAsState()
    val saldo             by vm.saldo.collectAsState()
    val mensagem          by vm.mensagem.collectAsState()
    val nomesPorTime      by vm.nomesPorTime.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    mensagem?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            vm.limparMensagem()
        }
    }

    var jogadorParaOfertar   by remember { mutableStateOf<Jogador?>(null) }
    var jogadorParaVender    by remember { mutableStateOf<Jogador?>(null) }
    var jogadorParaRenovar   by remember { mutableStateOf<Jogador?>(null) }
    var jogadorParaAposentar by remember { mutableStateOf<Jogador?>(null) }

    // Dialog: Renovar / Dispensar (contrato expirado)
    jogadorParaRenovar?.let { jogador ->
        ContratoExpiradoDialog(
            jogador   = jogador,
            onRenovar = { novoSalario, novosAnos ->
                vm.renovarContrato(jogador, novoSalario, novosAnos)
                jogadorParaRenovar = null
            },
            onDispensar = {
                vm.dispensarJogador(jogador)
                jogadorParaRenovar = null
            },
            onAposentar = if (podeAposentar(jogador)) {
                { jogadorParaRenovar = null; jogadorParaAposentar = jogador }
            } else null,
            onDismiss = { jogadorParaRenovar = null }
        )
    }

    // Dialog: Aposentar jogador (posição e nome do sucessor)
    jogadorParaAposentar?.let { jogador ->
        AposentadoriaDialog(
            jogador = jogador,
            onConfirmar = { posicao, manterNome, novoNome ->
                vm.aposentarJogador(jogador, posicao, manterNome, novoNome)
                jogadorParaAposentar = null
            },
            onDismiss = { jogadorParaAposentar = null }
        )
    }

    // Dialog: Fazer oferta (aba Pesquisa)
    jogadorParaOfertar?.let { jogador ->
        OfertaDialog(
            jogador = jogador,
            saldo = saldo,
            jaNoElenco = elenco.any { it.id == jogador.id },
            onContratar = { vm.fazerOferta(jogador); jogadorParaOfertar = null },
            onDismiss = { jogadorParaOfertar = null },
            onIrParaHistorico = onIrParaHistorico?.let { cb ->
                { id -> jogadorParaOfertar = null; cb(id) }
            }
        )
    }

    // Dialog: Vender jogador (aba Meu Elenco)
    jogadorParaVender?.let { jogador ->
        VenderDialog(
            jogador = jogador,
            onConfirmar = { vm.venderJogador(jogador); jogadorParaVender = null },
            onAposentar = if (podeAposentar(jogador)) {
                { jogadorParaVender = null; jogadorParaAposentar = jogador }
            } else null,
            onDismiss = { jogadorParaVender = null },
            onIrParaHistorico = onIrParaHistorico?.let { cb ->
                { id -> jogadorParaVender = null; cb(id) }
            }
        )
    }

    var abaAtiva by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fundoApp()
    ) {
        ScreenTopBar(
            titulo = "Jogadores",
            subtitulo = "Saldo ${formatarSaldo(saldo)}",
            onVoltar = onVoltar
        )

        TabRowPill(
            abas = listOf("Meu elenco (${elenco.size})", "Pesquisa"),
            selecionada = abaAtiva,
            onSelecionar = { abaAtiva = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(vertical = Spacing.md)
        )

        Box(modifier = Modifier.weight(1f)) {
            when (abaAtiva) {
                0 -> ElencoProgressaoTab(
                    elenco          = elenco,
                    onClicarJogador = { jogador ->
                        if (jogador.contratoAnos == 0) jogadorParaRenovar = jogador
                        else jogadorParaVender = jogador
                    }
                )
                1 -> PesquisaTab(
                    jogadores = resultadoPesquisa,
                    filtros = filtros,
                    saldo = saldo,
                    nomesPorTime = nomesPorTime,
                    onAplicarFiltros = vm::aplicarFiltros,
                    onClicarJogador = { jogadorParaOfertar = it }
                )
            }
        }

        // Snackbar como surface ancorada no rodapé (substitui Scaffold.snackbarHost)
        mensagem?.let { msg ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Text(
                    text = msg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Dialog: Contrato Expirado
// ─────────────────────────────────────────────────────────

@Composable
private fun ContratoExpiradoDialog(
    jogador:     Jogador,
    onRenovar:   (novoSalario: Long, novosAnos: Int) -> Unit,
    onDispensar: () -> Unit,
    onAposentar: (() -> Unit)? = null,
    onDismiss:   () -> Unit
) {
    val salarioAtualReais  = jogador.salario / 100L
    val podeReduzirSalario = jogador.idade >= 29

    var salarioTexto by remember { mutableStateOf(salarioAtualReais.toString()) }
    var anosContrato by remember { mutableIntStateOf(2) }

    val salarioNovo   = salarioTexto.toLongOrNull() ?: 0L
    val salarioValido = if (podeReduzirSalario) salarioNovo > 0
                        else salarioNovo >= salarioAtualReais

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.Warning,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.error,
                    modifier           = Modifier.size(20.dp)
                )
                Text("Contrato expirado")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(jogador.nome, fontWeight = FontWeight.Bold)
                Text("Posição: ${jogador.posicao.name.replace("_", " ")}")
                Text("Força: ${jogador.forca}")
                Text("Valor de mercado: ${formatarSaldo(jogador.valorMercado)}", fontWeight = FontWeight.Bold)

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    "O contrato deste jogador encerrou. Deseja renová-lo?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value           = salarioTexto,
                    onValueChange   = { salarioTexto = it.filter { c -> c.isDigit() } },
                    label           = { Text("Salário mensal (R\$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine      = true,
                    modifier        = Modifier.fillMaxWidth(),
                    supportingText  = {
                        when {
                            !salarioValido && salarioNovo > 0 ->
                                Text(
                                    "Salário mínimo: R\$ $salarioAtualReais (jogador abaixo de 29 anos)",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            podeReduzirSalario ->
                                Text(
                                    "Jogador com ${jogador.idade} anos — pode aceitar redução salarial",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                        }
                    }
                )

                Text("Duração: $anosContrato ano(s)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3, 4, 5).forEach { anos ->
                        FilterChip(
                            selected = anosContrato == anos,
                            onClick  = { anosContrato = anos },
                            label    = { Text("$anos") }
                        )
                    }
                }

                if (onAposentar != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    BotaoAposentar(onClick = onAposentar)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRenovar(salarioNovo * 100L, anosContrato) },
                enabled = salarioValido
            ) { Text("Renovar") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                TextButton(
                    onClick = onDispensar,
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Dispensar") }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────
//  Tab 0 — Elenco com progressão
// ─────────────────────────────────────────────────────────

@Composable
private fun ElencoProgressaoTab(
    elenco: List<Jogador>,
    onClicarJogador: (Jogador) -> Unit
) {
    if (elenco.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nenhum jogador no elenco",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(elenco, key = { it.id }) { jogador ->
            JogadorProgressaoCard(jogador = jogador, onClick = { onClicarJogador(jogador) })
        }
        item { Spacer(Modifier.height(Spacing.lg)) }
    }
}

@Composable
private fun JogadorProgressaoCard(jogador: Jogador, onClick: () -> Unit) {
    // Borda destacada quando contrato expirado ou prestes a expirar
    val borderColor = when (jogador.contratoAnos) {
        0    -> MaterialTheme.colorScheme.error
        1    -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (jogador.contratoAnos <= 1) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Faixa lateral colorida pelo setor
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(corSetor(jogador.posicao.setor))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Linha 1: posição + nome + idade + força
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    PosicaoBadge(
                        abreviacao = jogador.posicao.abreviacao,
                        setor = jogador.posicao.setor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            jogador.nome,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
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
                }

                // Linha 2: nota · partidas · evolução
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    InfoBadge(
                        icone = Icons.Filled.Star,
                        texto = "%.1f".format(jogador.notaMedia),
                        accent = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${jogador.partidasTemporada} jogos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    EvolucaoChip(progresso = jogador.progressoEvolucao)
                }

                // Aviso de contrato expirado / prestes a expirar
                if (jogador.contratoAnos <= 1) {
                    ContratoAvisoChip(anos = jogador.contratoAnos)
                }

                // Barra de progressão bidirecional (negativo à esquerda, positivo à direita)
                ProgressoEvolucaoBar(progresso = jogador.progressoEvolucao)
            }
        }
    }
}

@Composable
private fun InfoBadge(icone: ImageVector, texto: String, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(12.dp)
        )
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

@Composable
private fun ContratoAvisoChip(anos: Int) {
    val (label, cor) = when (anos) {
        0    -> "CONTRATO EXPIRADO" to MaterialTheme.colorScheme.error
        else -> "1 ANO RESTANTE" to MaterialTheme.colorScheme.tertiary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(cor.copy(alpha = 0.14f))
            .border(0.5.dp, cor.copy(alpha = 0.5f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = cor,
            modifier = Modifier.size(12.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = cor,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun EvolucaoChip(progresso: Float) {
    val (label, icone, cor) = when {
        progresso >  0.05f -> Triple("EVOLUINDO", Icons.Filled.TrendingUp, MoneyPositive)
        progresso < -0.05f -> Triple("REGREDINDO", Icons.Filled.TrendingDown, MoneyNegative)
        else               -> Triple("ESTÁVEL", Icons.Filled.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(cor.copy(alpha = 0.18f))
            .border(0.5.dp, cor.copy(alpha = 0.5f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = cor,
            modifier = Modifier.size(12.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = cor,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Barra de progressão bidirecional para evolução do jogador.
 * - progresso 0 → centro
 * - progresso > 0 → barra verde da metade pra direita
 * - progresso < 0 → barra vermelha da metade pra esquerda
 */
@Composable
private fun ProgressoEvolucaoBar(progresso: Float) {
    val ev = progresso.coerceIn(-1f, 1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val center = MaterialTheme.colorScheme.outline
    val cor = if (ev >= 0) MoneyPositive else MoneyNegative
    val abs = kotlin.math.abs(ev)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track)
    ) {
        // Marca central
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(center)
                .align(Alignment.Center)
        )
        // Barra preenchida
        if (ev > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f * abs)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(cor),
                content = {}
            )
        } else if (ev < 0) {
            // Para negativo, alinhar à direita do centro indo pra esquerda
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Start
            ) {
                Spacer(Modifier.fillMaxWidth(0.5f - 0.5f * abs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(abs / (0.5f + 0.5f * abs).coerceAtLeast(0.001f) * 0.5f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
                        .background(cor)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Tab 1 — Pesquisa global
// ─────────────────────────────────────────────────────────

private fun contarFiltrosAtivos(f: FiltrosPesquisaJogador): Int {
    var n = 0
    if (f.posicao != null) n++
    if (f.forcaMin > 0 || f.forcaMax < 99) n++
    if (f.idadeMin > JogadoresViewModel.IDADE_MIN_PADRAO || f.idadeMax < JogadoresViewModel.IDADE_MAX_PADRAO) n++
    if (f.valorMin > 0 || f.valorMax < JogadoresViewModel.VALOR_MAX_PADRAO) n++
    if (f.salarioMin > 0 || f.salarioMax < JogadoresViewModel.SALARIO_MAX_PADRAO) n++
    return n
}

@Composable
private fun PesquisaTab(
    jogadores: List<Jogador>,
    filtros: FiltrosPesquisaJogador,
    saldo: Long,
    nomesPorTime: Map<Int, String>,
    onAplicarFiltros: (FiltrosPesquisaJogador) -> Unit,
    onClicarJogador: (Jogador) -> Unit
) {
    var mostrarFiltros by remember { mutableStateOf(false) }
    val qtdFiltros = contarFiltrosAtivos(filtros)

    Column(modifier = Modifier.fillMaxSize()) {
        // Barra: contador + botão que abre o painel de filtros
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${jogadores.size} jogador${if (jogadores.size != 1) "es" else ""} encontrado${if (jogadores.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = { mostrarFiltros = true },
                shape = RoundedCornerShape(Radius.md),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs)
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(if (qtdFiltros > 0) "Filtros ($qtdFiltros)" else "Filtros")
            }
        }

        if (jogadores.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nenhum jogador corresponde aos filtros",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(jogadores, key = { it.id }) { jogador ->
                    JogadorPesquisaCard(
                        jogador = jogador,
                        saldo = saldo,
                        nomeTime = nomesPorTime[jogador.timeId],
                        onClick = { onClicarJogador(jogador) }
                    )
                }
                item { Spacer(Modifier.height(Spacing.lg)) }
            }
        }
    }

    if (mostrarFiltros) {
        FiltrosBottomSheet(
            filtrosIniciais = filtros,
            onFiltrar = { onAplicarFiltros(it); mostrarFiltros = false },
            onDismiss = { mostrarFiltros = false }
        )
    }
}

// ── Painel de filtros em bottom sheet (aplicação adiada via botão "Filtrar") ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltrosBottomSheet(
    filtrosIniciais: FiltrosPesquisaJogador,
    onFiltrar: (FiltrosPesquisaJogador) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var posicao    by remember { mutableStateOf(filtrosIniciais.posicao) }
    var forcaMin   by remember { mutableIntStateOf(filtrosIniciais.forcaMin) }
    var forcaMax   by remember { mutableIntStateOf(filtrosIniciais.forcaMax) }
    var idadeMin   by remember { mutableIntStateOf(filtrosIniciais.idadeMin) }
    var idadeMax   by remember { mutableIntStateOf(filtrosIniciais.idadeMax) }
    var valorMin   by remember { mutableStateOf(filtrosIniciais.valorMin) }
    var valorMax   by remember { mutableStateOf(filtrosIniciais.valorMax) }
    var salarioMin by remember { mutableStateOf(filtrosIniciais.salarioMin) }
    var salarioMax by remember { mutableStateOf(filtrosIniciais.salarioMax) }

    fun montar() = FiltrosPesquisaJogador(
        posicao, forcaMin, forcaMax, idadeMin, idadeMax, valorMin, valorMax, salarioMin, salarioMax
    )
    val qtd = contarFiltrosAtivos(montar())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "Filtrar jogadores",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Posição — lista suspensa
            var menuPosAberto by remember { mutableStateOf(false) }
            val labelPosicao = posicao
                ?.let { "${it.abreviacao} · ${it.name.replace("_", " ")}" }
                ?: "Todas as posições"
            ExposedDropdownMenuBox(
                expanded = menuPosAberto,
                onExpandedChange = { menuPosAberto = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = labelPosicao,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Posição") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuPosAberto) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(Radius.md)
                )
                ExposedDropdownMenu(
                    expanded = menuPosAberto,
                    onDismissRequest = { menuPosAberto = false },
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Todas as posições") },
                        onClick = { posicao = null; menuPosAberto = false }
                    )
                    Posicao.entries.forEach { pos ->
                        DropdownMenuItem(
                            text = { Text("${pos.abreviacao} · ${pos.name.replace("_", " ")}") },
                            onClick = { posicao = pos; menuPosAberto = false }
                        )
                    }
                }
            }

            // Força — faixa (mín/máx)
            FiltroRangeSlider("Força", "$forcaMin — $forcaMax", forcaMin.toFloat()..forcaMax.toFloat(), 0f..99f) { r ->
                forcaMin = r.start.roundToInt().coerceIn(0, 99)
                forcaMax = r.endInclusive.roundToInt().coerceIn(0, 99)
            }

            // Idade — faixa (mín/máx)
            FiltroRangeSlider("Idade", "$idadeMin — $idadeMax", idadeMin.toFloat()..idadeMax.toFloat(), 16f..45f) { r ->
                idadeMin = r.start.roundToInt().coerceIn(16, 45)
                idadeMax = r.endInclusive.roundToInt().coerceIn(16, 45)
            }

            // Valor de mercado — faixa (mín/máx), em milhões de reais
            FiltroRangeSlider(
                "Valor de mercado",
                "${formatarSaldo(valorMin)} — ${formatarSaldo(valorMax)}",
                (valorMin / 1_000_000_00L).toFloat()..(valorMax / 1_000_000_00L).toFloat(),
                0f..300f
            ) { r ->
                valorMin = r.start.toLong() * 1_000_000_00L
                valorMax = r.endInclusive.toLong() * 1_000_000_00L
            }

            // Salário (mês) — faixa (mín/máx), em milhares de reais (passo R$ 1.000)
            FiltroRangeSlider(
                "Salário (mês)",
                "${formatarSaldo(salarioMin)} — ${formatarSaldo(salarioMax)}",
                (salarioMin / 100_000L).toFloat()..(salarioMax / 100_000L).toFloat(),
                0f..2000f
            ) { r ->
                salarioMin = r.start.toLong() * 100_000L
                salarioMax = r.endInclusive.toLong() * 100_000L
            }

            // Ações: Limpar (reseta o painel) + Filtrar (aplica e fecha)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(
                    onClick = {
                        val d = FiltrosPesquisaJogador()
                        posicao = d.posicao; forcaMin = d.forcaMin; forcaMax = d.forcaMax
                        idadeMin = d.idadeMin; idadeMax = d.idadeMax
                        valorMin = d.valorMin; valorMax = d.valorMax
                        salarioMin = d.salarioMin; salarioMax = d.salarioMax
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.md)
                ) { Text("Limpar") }
                Button(
                    onClick = { onFiltrar(montar()) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.md)
                ) { Text(if (qtd > 0) "Filtrar ($qtd)" else "Filtrar") }
            }
        }
    }
}

@Composable
private fun FiltroRangeSlider(
    label: String,
    texto: String,
    valor: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (ClosedFloatingPointRange<Float>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(texto, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        RangeSlider(value = valor, onValueChange = onChange, valueRange = valueRange)
    }
}

@Composable
private fun JogadorPesquisaCard(
    jogador: Jogador,
    saldo: Long,
    nomeTime: String?,
    onClick: () -> Unit
) {
    val podeContratar = saldo >= jogador.valorMercado
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        jogador.nome,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${jogador.idade} anos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        nomeTime ?: "Livre no mercado",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (nomeTime == null) FontWeight.Bold else FontWeight.Normal,
                        color = if (nomeTime == null) MoneyPositive
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    ForcaBadge(jogador.forca)
                    Text(
                        formatarSaldo(jogador.valorMercado),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (podeContratar) MoneyPositive else MoneyNegative
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Dialogs polidos com PosicaoBadge no header
// ─────────────────────────────────────────────────────────

@Composable
private fun OfertaDialog(
    jogador: Jogador,
    saldo: Long,
    jaNoElenco: Boolean,
    onContratar: () -> Unit,
    onDismiss: () -> Unit,
    onIrParaHistorico: ((Int) -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PosicaoBadge(jogador.posicao.abreviacao, jogador.posicao.setor)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (jaNoElenco) jogador.nome else "Fazer oferta",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!jaNoElenco) {
                        Text(
                            jogador.nome,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ForcaBadge(jogador.forca)
            }
        },
        text = {
            if (jaNoElenco) {
                Text(
                    "Este jogador já faz parte do seu elenco.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val preco = CalculadoraMercado.precoCompraUsuario(jogador.valorMercado)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DialogDataRow("Posição", jogador.posicao.name.replace("_", " "))
                    DialogDataRow("Idade", "${jogador.idade} anos")
                    DialogDataRow("Status", if (jogador.timeId != null) "Com clube" else "Livre")
                    DialogDataRow("Valor de mercado", formatarSaldo(jogador.valorMercado))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Preço (com prêmio)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            formatarSaldo(preco),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (saldo >= preco) MoneyPositive else MoneyNegative
                        )
                    }
                    if (saldo < preco) {
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AttachMoney,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text(
                                    "Saldo insuficiente",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!jaNoElenco) {
                Button(
                    onClick = onContratar,
                    enabled = saldo >= CalculadoraMercado.precoCompraUsuario(jogador.valorMercado),
                    shape = RoundedCornerShape(Radius.sm)
                ) {
                    Text("Contratar", fontWeight = FontWeight.SemiBold)
                }
            } else if (onIrParaHistorico != null) {
                TextButton(onClick = { onIrParaHistorico(jogador.id) }) {
                    Text("📊 Histórico", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (!jaNoElenco && onIrParaHistorico != null) {
                    TextButton(onClick = { onIrParaHistorico(jogador.id) }) {
                        Text("📊 Histórico", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(if (jaNoElenco) "Fechar" else "Cancelar")
                }
            }
        }
    )
}

@Composable
private fun VenderDialog(
    jogador: Jogador,
    onConfirmar: () -> Unit,
    onAposentar: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onIrParaHistorico: ((Int) -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PosicaoBadge(jogador.posicao.abreviacao, jogador.posicao.setor)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Vender jogador",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        jogador.nome,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ForcaBadge(jogador.forca)
            }
        },
        text = {
            val preco = CalculadoraMercado.precoVendaUsuario(jogador.valorMercado)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DialogDataRow("Posição", jogador.posicao.name.replace("_", " "))
                DialogDataRow("Idade", "${jogador.idade} anos")
                DialogDataRow("Valor de mercado", formatarSaldo(jogador.valorMercado))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Você recebe (com deságio)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        formatarSaldo(preco),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MoneyPositive
                    )
                }
                Text(
                    "O jogo buscará automaticamente um clube com saldo para comprar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (onAposentar != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    BotaoAposentar(onClick = onAposentar)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                shape = RoundedCornerShape(Radius.sm),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Confirmar venda", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (onIrParaHistorico != null) {
                    TextButton(onClick = { onIrParaHistorico(jogador.id) }) {
                        Text("📊 Histórico", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

/** Atalho para o fluxo de aposentadoria, exibido nos dialogs do elenco. */
@Composable
private fun BotaoAposentar(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.sm),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Text(
            "Aposentar jogador",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun DialogDataRow(label: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
