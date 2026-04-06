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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.dao.CalendarioPartidaDto
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.viewmodel.RodadaViewModel

// ─────────────────────────────────────────────
//  RodadaScreen
//  Consulta de jogos por rodada — Séries A/B/C/D
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RodadaScreen(
    campeonatoAId: Int,
    campeonatoBId: Int,
    campeonatoCId: Int = -1,
    campeonatoDId: Int = -1,
    onVoltar: () -> Unit = {},
    vm: RodadaViewModel = hiltViewModel()
) {
    val partidas          by vm.partidas.collectAsState()
    val divisaoSelecionada by vm.divisaoSelecionada.collectAsState()
    val rodadaSelecionada  by vm.rodadaSelecionada.collectAsState()
    val maxRodada          by vm.maxRodada.collectAsState()

    LaunchedEffect(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId) {
        vm.carregar(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId)
    }

    // ── Dropdown Série ──────────────────────────────────
    val opcoesDivisao = buildList {
        add(1 to "Série A")
        add(2 to "Série B")
        if (campeonatoCId > 0) add(3 to "Série C")
        if (campeonatoDId > 0) add(4 to "Série D")
    }
    var expandidoSerie by remember { mutableStateOf(false) }
    val labelSerie = opcoesDivisao.firstOrNull { it.first == divisaoSelecionada }?.second ?: "Série A"

    // ── Dropdown Rodada ─────────────────────────────────
    val opcoesRodada = (1..maxRodada).toList()
    var expandidoRodada by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rodadas") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Filtros ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Filtro Série
                ExposedDropdownMenuBox(
                    expanded = expandidoSerie,
                    onExpandedChange = { expandidoSerie = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = labelSerie,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Série") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoSerie) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandidoSerie,
                        onDismissRequest = { expandidoSerie = false }
                    ) {
                        opcoesDivisao.forEach { (div, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    vm.selecionarDivisao(div)
                                    expandidoSerie = false
                                }
                            )
                        }
                    }
                }

                // Filtro Rodada
                ExposedDropdownMenuBox(
                    expanded = expandidoRodada,
                    onExpandedChange = { expandidoRodada = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = "Rodada $rodadaSelecionada",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rodada") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoRodada) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandidoRodada,
                        onDismissRequest = { expandidoRodada = false }
                    ) {
                        opcoesRodada.forEach { rodada ->
                            DropdownMenuItem(
                                text = { Text("Rodada $rodada") },
                                onClick = {
                                    vm.selecionarRodada(rodada)
                                    expandidoRodada = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Lista de partidas ────────────────────────
            if (partidas.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma partida encontrada.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(partidas, key = { it.partidaId }) { partida ->
                        RodadaCard(partida = partida)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  RodadaCard
// ─────────────────────────────────────────────
@Composable
private fun RodadaCard(partida: CalendarioPartidaDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (partida.jogada)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (!partida.jogada)
            CardDefaults.outlinedCardBorder()
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time da casa
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TeamBadge(
                    nome = partida.nomeCasa,
                    escudoRes = partida.escudoCasa,
                    size = 44.dp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = partida.nomeCasa,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }

            // Centro: placar ou "vs"
            Box(
                modifier = Modifier.width(80.dp),
                contentAlignment = Alignment.Center
            ) {
                if (partida.jogada && partida.golsCasa != null && partida.golsFora != null) {
                    Text(
                        text = "${partida.golsCasa}  ×  ${partida.golsFora}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "vs",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = partida.nomeCampeonato.take(14),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Time visitante
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TeamBadge(
                    nome = partida.nomeFora,
                    escudoRes = partida.escudoFora,
                    size = 44.dp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = partida.nomeFora,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}
