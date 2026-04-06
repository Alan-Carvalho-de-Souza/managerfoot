package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.database.entities.Posicao
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.viewmodel.JogadoresViewModel

// ═══════════════════════════════════════════════════════════
//  JogadoresScreen
//  Tab 0 — Elenco com barra de progressão por jogador
//  Tab 1 — Pesquisa global com filtros de posição/força/idade
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JogadoresScreen(
    timeId: Int,
    onVoltar: () -> Unit = {},
    vm: JogadoresViewModel = hiltViewModel()
) {
    val elenco            by vm.elenco.collectAsState()
    val resultadoPesquisa by vm.resultadoPesquisa.collectAsState()
    val posicaoFiltro     by vm.posicaoFiltro.collectAsState()
    val forcaMinFiltro    by vm.forcaMinFiltro.collectAsState()
    val idadeMaxFiltro    by vm.idadeMaxFiltro.collectAsState()
    val saldo             by vm.saldo.collectAsState()
    val mensagem          by vm.mensagem.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    mensagem?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            vm.limparMensagem()
        }
    }

    var jogadorParaOfertar by remember { mutableStateOf<Jogador?>(null) }
    var jogadorParaVender  by remember { mutableStateOf<Jogador?>(null) }

    // ── Dialog: Fazer oferta (aba Pesquisa) ───────────────────────
    jogadorParaOfertar?.let { jogador ->
        val eNoElenco = elenco.any { it.id == jogador.id }
        AlertDialog(
            onDismissRequest = { jogadorParaOfertar = null },
            title = { Text(if (eNoElenco) jogador.nome else "Fazer oferta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (eNoElenco) {
                        Text("Este jogador já faz parte do seu elenco.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Jogador: ${jogador.nome}")
                        Text("Posição: ${jogador.posicao.name.replace("_", " ")}")
                        Text("Força: ${jogador.forca}")
                        Text("Valor: ${formatarValorJog(jogador.valorMercado)}",
                            fontWeight = FontWeight.Bold)
                        Text("Status: ${if (jogador.timeId != null) "Com clube" else "Livre"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (saldo < jogador.valorMercado) {
                            Text("Saldo insuficiente!",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            },
            confirmButton = {
                if (!eNoElenco) {
                    Button(
                        onClick = { vm.fazerOferta(jogador); jogadorParaOfertar = null },
                        enabled = saldo >= jogador.valorMercado
                    ) { Text("Contratar") }
                }
            },
            dismissButton = {
                TextButton(onClick = { jogadorParaOfertar = null }) { Text(if (eNoElenco) "Fechar" else "Cancelar") }
            }
        )
    }

    // ── Dialog: Vender jogador (aba Meu Elenco) ──────────────────
    jogadorParaVender?.let { jogador ->
        AlertDialog(
            onDismissRequest = { jogadorParaVender = null },
            title = { Text("Vender jogador") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(jogador.nome, fontWeight = FontWeight.Bold)
                    Text("Posição: ${jogador.posicao.name.replace("_", " ")}")
                    Text("Força: ${jogador.forca}")
                    Text("Valor de mercado: ${formatarValorJog(jogador.valorMercado)}",
                        fontWeight = FontWeight.Bold)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "O jogo buscará automaticamente um clube com saldo para comprar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.venderJogador(jogador); jogadorParaVender = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Confirmar venda") }
            },
            dismissButton = {
                TextButton(onClick = { jogadorParaVender = null }) { Text("Cancelar") }
            }
        )
    }

    var abaAtiva by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jogadores") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = {
            mensagem?.let {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = abaAtiva) {
                Tab(
                    selected = abaAtiva == 0,
                    onClick  = { abaAtiva = 0 },
                    text     = { Text("Meu Elenco (${elenco.size})") }
                )
                Tab(
                    selected = abaAtiva == 1,
                    onClick  = { abaAtiva = 1 },
                    text     = { Text("Pesquisa") }
                )
            }

            when (abaAtiva) {
                0 -> ElencoProgressaoTab(
                    elenco         = elenco,
                    onClicarJogador = { jogadorParaVender = it }
                )
                1 -> PesquisaTab(
                    jogadores          = resultadoPesquisa,
                    posicaoFiltro      = posicaoFiltro,
                    forcaMin           = forcaMinFiltro,
                    idadeMax           = idadeMaxFiltro,
                    saldo              = saldo,
                    onFiltrarPosicao   = vm::filtrarPosicao,
                    onFiltrarForcaMin  = vm::filtrarForcaMin,
                    onFiltrarIdadeMax  = vm::filtrarIdadeMax,
                    onLimparFiltros    = vm::limparFiltros,
                    onClicarJogador    = { jogadorParaOfertar = it }
                )
            }
        }
    }
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
            Text("Nenhum jogador no elenco", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(elenco, key = { it.id }) { jogador ->
            JogadorProgressaoCard(jogador = jogador, onClick = { onClicarJogador(jogador) })
        }
    }
}

@Composable
private fun JogadorProgressaoCard(jogador: Jogador, onClick: () -> Unit) {
    Card(
        onClick    = onClick,
        modifier   = Modifier.fillMaxWidth(),
        elevation  = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── Linha 1: posição · nome · idade · força ──────────
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(color = posicaoColor(jogador.posicao), shape = MaterialTheme.shapes.small) {
                    Text(
                        text     = jogador.posicao.abreviacao,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color    = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    jogador.nome,
                    modifier    = Modifier.weight(1f),
                    fontWeight  = FontWeight.SemiBold,
                    fontSize    = 14.sp,
                    maxLines    = 1
                )
                Text("${jogador.idade}a", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = forcaBadgeColor(jogador.forca), shape = MaterialTheme.shapes.small) {
                    Text(
                        text     = "${jogador.forca}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color    = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Linha 2: nota · partidas · label evolução ────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("★ ${"%.1f".format(jogador.notaMedia)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text("${jogador.partidasTemporada} jogos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                val ev = jogador.progressoEvolucao
                val (labelEv, corEv) = when {
                    ev > 0.05f  -> "↑ evoluindo"   to Color(0xFF2E7D32)
                    ev < -0.05f -> "↓ regredindo"  to Color(0xFFB71C1C)
                    else        -> "estável"        to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(labelEv, fontSize = 11.sp, color = corEv, fontWeight = FontWeight.Medium)
            }

            // ── Barra de progressão ───────────────────────────────
            val ev = jogador.progressoEvolucao.coerceIn(-1f, 1f)
            val progresso = if (ev >= 0f) ev else 0f
            val corBarra  = if (jogador.progressoEvolucao >= 0f) Color(0xFF43A047) else Color(0xFFE53935)
            LinearProgressIndicator(
                progress    = { progresso },
                modifier    = Modifier.fillMaxWidth().height(5.dp),
                color       = corBarra,
                trackColor  = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Tab 1 — Pesquisa global
// ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PesquisaTab(
    jogadores:        List<Jogador>,
    posicaoFiltro:    Posicao?,
    forcaMin:         Int,
    idadeMax:         Int,
    saldo:            Long,
    onFiltrarPosicao: (Posicao?) -> Unit,
    onFiltrarForcaMin:(Int) -> Unit,
    onFiltrarIdadeMax:(Int) -> Unit,
    onLimparFiltros:  () -> Unit,
    onClicarJogador:  (Jogador) -> Unit
) {
    val filtroAtivo = posicaoFiltro != null || forcaMin > 0 || idadeMax < 40

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Painel de filtros ────────────────────────────────────
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filtros", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (filtroAtivo) {
                        TextButton(
                            onClick = onLimparFiltros,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Limpar", fontSize = 12.sp)
                        }
                    }
                }

                // Dropdown posição
                var expandidoPosicao by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded        = expandidoPosicao,
                    onExpandedChange = { expandidoPosicao = it }
                ) {
                    OutlinedTextField(
                        value          = posicaoFiltro?.name?.replace("_", " ") ?: "Todas as posições",
                        onValueChange  = {},
                        readOnly       = true,
                        label          = { Text("Posição", fontSize = 12.sp) },
                        trailingIcon   = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoPosicao) },
                        modifier       = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded        = expandidoPosicao,
                        onDismissRequest = { expandidoPosicao = false }
                    ) {
                        DropdownMenuItem(
                            text    = { Text("Todas as posições") },
                            onClick = { onFiltrarPosicao(null); expandidoPosicao = false }
                        )
                        Posicao.entries.forEach { pos ->
                            DropdownMenuItem(
                                text    = { Text(pos.name.replace("_", " ")) },
                                onClick = { onFiltrarPosicao(pos); expandidoPosicao = false }
                            )
                        }
                    }
                }

                // Slider força mínima
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Força ≥ $forcaMin", fontSize = 12.sp, modifier = Modifier.width(90.dp))
                    Slider(
                        value          = forcaMin.toFloat(),
                        onValueChange  = { onFiltrarForcaMin(it.toInt()) },
                        valueRange     = 0f..99f,
                        modifier       = Modifier.weight(1f)
                    )
                }

                // Slider idade máxima
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Idade ≤ $idadeMax", fontSize = 12.sp, modifier = Modifier.width(90.dp))
                    Slider(
                        value          = idadeMax.toFloat(),
                        onValueChange  = { onFiltrarIdadeMax(it.toInt()) },
                        valueRange     = 16f..45f,
                        modifier       = Modifier.weight(1f)
                    )
                }
            }
        }

        Text(
            text     = "${jogadores.size} jogadores encontrados",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(
            modifier        = Modifier.fillMaxSize(),
            contentPadding  = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(jogadores, key = { it.id }) { jogador ->
                JogadorPesquisaItem(
                    jogador  = jogador,
                    saldo    = saldo,
                    onClick  = { onClicarJogador(jogador) }
                )
            }
        }
    }
}

@Composable
private fun JogadorPesquisaItem(
    jogador: Jogador,
    saldo:   Long,
    onClick: () -> Unit
) {
    val podeContratar = saldo >= jogador.valorMercado
    Card(
        onClick    = onClick,
        modifier   = Modifier.fillMaxWidth(),
        elevation  = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(color = posicaoColor(jogador.posicao), shape = MaterialTheme.shapes.small) {
                Text(
                    text     = jogador.posicao.abreviacao,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    color    = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(jogador.nome, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    text     = "${jogador.idade} anos · ${jogador.posicao.name.replace("_", " ")}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(color = forcaBadgeColor(jogador.forca), shape = MaterialTheme.shapes.small) {
                    Text(
                        text     = "${jogador.forca}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color    = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text  = formatarValorJog(jogador.valorMercado),
                    fontSize = 10.sp,
                    color    = if (podeContratar) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Helpers de cor
// ─────────────────────────────────────────────────────────

private fun forcaBadgeColor(forca: Int): Color = when {
    forca >= 80 -> Color(0xFF1B5E20)
    forca >= 70 -> Color(0xFF2E7D32)
    forca >= 60 -> Color(0xFFF9A825)
    forca >= 50 -> Color(0xFFE65100)
    else        -> Color(0xFFB71C1C)
}

private fun formatarValorJog(centavos: Long): String {
    val reais = centavos / 100.0
    return when {
        reais >= 1_000_000 -> "R\$ %.1f M".format(reais / 1_000_000)
        reais >= 1_000     -> "R\$ %.0f mil".format(reais / 1_000)
        else               -> "R\$ %.0f".format(reais)
    }
}
