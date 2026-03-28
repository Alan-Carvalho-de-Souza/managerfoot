package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.dao.ArtilheiroDto
import br.com.managerfoot.data.dao.ConfrontoPartidaDto
import br.com.managerfoot.domain.model.Time
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.viewmodel.ConfrontoStats
import br.com.managerfoot.presentation.viewmodel.ConfrontoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfrontoScreen(
    preTimeId: Int,
    onVoltar: () -> Unit,
    vm: ConfrontoViewModel = hiltViewModel()
) {
    LaunchedEffect(preTimeId) { vm.carregar(preTimeId) }

    val todos by vm.todos.collectAsState()
    val timeA by vm.timeASelecionado.collectAsState()
    val timeB by vm.timeBSelecionado.collectAsState()
    val stats by vm.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Confrontos") },
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // Seletores de time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeDropdownMenu(
                    label = "Time A",
                    times = todos,
                    selecionado = timeA,
                    onSelecionar = { vm.selecionarTimeA(it) },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "vs",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                TimeDropdownMenu(
                    label = "Time B",
                    times = todos,
                    selecionado = timeB,
                    onSelecionar = { vm.selecionarTimeB(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            when {
                timeA == null || timeB == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Selecione dois times para ver o histórico de confrontos",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }

                stats == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                stats!!.partidas.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nenhuma partida encontrada entre ${timeA!!.nome} e ${timeB!!.nome}",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }

                else -> {
                    val s = stats!!
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        item { RetrospectCard(timeA!!, timeB!!, s) }
                        item { GolsCard(timeA!!, timeB!!, s) }
                        item { SequenciasCard(timeA!!, timeB!!, s) }
                        if (s.artilheiros.isNotEmpty()) {
                            item { ArtilheirosConfrontoCard(s.artilheiros) }
                        }
                        item {
                            Text(
                                "Partidas (${s.partidas.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(s.partidas.reversed(), key = { it.partidaId }) { partida ->
                            ConfrontoPartidaRow(partida, timeA!!, timeB!!)
                        }
                    }
                }
            }
        }
    }
}

// ─── Dropdown de seleção de time ──────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDropdownMenu(
    label: String,
    times: List<Time>,
    selecionado: Time?,
    onSelecionar: (Time) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selecionado?.nome ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 11.sp) },
            placeholder = { Text("Selecione", fontSize = 13.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            times.forEach { time ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(time.nome, fontSize = 14.sp)
                        }
                    },
                    onClick = {
                        onSelecionar(time)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ─── Card de retrospecto geral ────────────────────────────

@Composable
private fun RetrospectCard(timeA: Time, timeB: Time, stats: ConfrontoStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Retrospecto geral · ${stats.partidas.size} partidas",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Time A
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamBadge(nome = timeA.nome, escudoRes = timeA.escudoRes, size = 54.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        timeA.nome,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Placar V - E - D (perspectiva de A)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            "${stats.vitoriasA}",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "-",
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            "${stats.empates}",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "-",
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            "${stats.vitoriasB}",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("V", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                        Text("E", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                        Text("D", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                    }
                }

                // Time B
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamBadge(nome = timeB.nome, escudoRes = timeB.escudoRes, size = 54.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        timeB.nome,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ─── Card de gols ─────────────────────────────────────────

@Composable
private fun GolsCard(timeA: Time, timeB: Time, stats: ConfrontoStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Gols no confronto",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${stats.golsA}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        timeA.nome,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "×",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${stats.golsB}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        timeB.nome,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Card de sequências ───────────────────────────────────

@Composable
private fun SequenciasCard(timeA: Time, timeB: Time, stats: ConfrontoStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Maiores sequências",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            SequenciaLinha(
                titulo = "Vitórias consecutivas",
                nomeA = timeA.nome,
                valA = stats.maiorSequenciaVitoriasA,
                nomeB = timeB.nome,
                valB = stats.maiorSequenciaVitoriasB
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            SequenciaLinha(
                titulo = "Invencibilidade (sem derrota)",
                nomeA = timeA.nome,
                valA = stats.maiorSequenciaInvicA,
                nomeB = timeB.nome,
                valB = stats.maiorSequenciaInvicB
            )
        }
    }
}

@Composable
private fun SequenciaLinha(titulo: String, nomeA: String, valA: Int, nomeB: String, valB: Int) {
    Text(titulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
            Text("$valA jogo${if (valA != 1) "s" else ""}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(nomeA, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Text("$valB jogo${if (valB != 1) "s" else ""}", fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            Text(nomeB, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ─── Card de artilheiros do confronto ─────────────────────

@Composable
private fun ArtilheirosConfrontoCard(artilheiros: List<ArtilheiroDto>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Artilheiros do confronto",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            artilheiros.forEachIndexed { idx, a ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${idx + 1}",
                        modifier = Modifier.width(24.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        fontWeight = if (idx < 3) FontWeight.Bold else FontWeight.Normal,
                        color = when (idx) {
                            0 -> MaterialTheme.colorScheme.primary
                            1 -> MaterialTheme.colorScheme.secondary
                            2 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        a.nomeAbrev,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontWeight = if (idx < 3) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TeamBadge(nome = a.nomeTime, escudoRes = a.escudoRes, size = 18.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        a.nomeTime,
                        modifier = Modifier.width(80.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${a.total} gols",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ─── Linha de partida individual ──────────────────────────

@Composable
private fun ConfrontoPartidaRow(
    partida: ConfrontoPartidaDto,
    timeA: Time,
    timeB: Time
) {
    val aEhCasa = partida.timeCasaId == timeA.id
    val golsA = if (aEhCasa) partida.golsCasa else partida.golsFora
    val golsB = if (aEhCasa) partida.golsFora else partida.golsCasa

    val (labelResultado, corResultado) = when {
        golsA > golsB -> "V" to Color(0xFF388E3C)
        golsA < golsB -> "D" to MaterialTheme.colorScheme.error
        else          -> "E" to MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge V / E / D
            Surface(
                color = corResultado,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(end = 10.dp)
            ) {
                Text(
                    labelResultado,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // Competição + rodada
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    partida.nomeCampeonato,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Rodada ${partida.rodada} · ${if (aEhCasa) "Casa" else "Fora"}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Placar com badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TeamBadge(nome = timeA.nome, escudoRes = timeA.escudoRes, size = 22.dp)
                Text(
                    "$golsA × $golsB",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                TeamBadge(nome = timeB.nome, escudoRes = timeB.escudoRes, size = 22.dp)
            }
        }
    }
}
