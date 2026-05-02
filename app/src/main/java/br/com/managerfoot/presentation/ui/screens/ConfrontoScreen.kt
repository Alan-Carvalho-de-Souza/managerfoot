package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.dao.ArtilheiroDto
import br.com.managerfoot.data.dao.ConfrontoPartidaDto
import br.com.managerfoot.domain.model.Time
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.ConfrontoStats
import br.com.managerfoot.presentation.viewmodel.ConfrontoViewModel

// ─────────────────────────────────────────────────────────────
//  ConfrontoScreen — Tactical Dark
//  Histórico de confrontos entre dois times
// ─────────────────────────────────────────────────────────────
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Confrontos",
            subtitulo = "Histórico entre dois clubes",
            onVoltar = onVoltar
        )

        // ── Seletores de time ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GreenElectric,
                modifier = Modifier.padding(top = Spacing.sm)
            )
            TimeDropdownMenu(
                label = "Time B",
                times = todos,
                selecionado = timeB,
                onSelecionar = { vm.selecionarTimeB(it) },
                modifier = Modifier.weight(1f)
            )
        }

        when {
            timeA == null || timeB == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Selecione dois times para ver o histórico de confrontos",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            stats == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GreenElectric)
                }
            }
            stats!!.partidas.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nenhuma partida encontrada entre ${timeA!!.nome} e ${timeB!!.nome}",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                val s = stats!!
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item { RetrospectoCard(timeA!!, timeB!!, s) }
                    item { GolsCard(timeA!!, timeB!!, s) }
                    item { SequenciasCard(timeA!!, timeB!!, s) }
                    if (s.artilheiros.isNotEmpty()) {
                        item { ArtilheirosConfrontoCard(s.artilheiros) }
                    }
                    item {
                        Spacer(Modifier.height(Spacing.xs))
                        SectionTitle("Partidas (${s.partidas.size})")
                    }
                    items(s.partidas.reversed(), key = { it.partidaId }) { partida ->
                        ConfrontoPartidaRow(partida, timeA!!, timeB!!)
                    }
                    item { Spacer(Modifier.height(Spacing.lg)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Dropdown de seleção de time (Tactical Dark)
// ─────────────────────────────────────────────────────────────
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
            label = { Text(label, fontWeight = FontWeight.SemiBold) },
            placeholder = { Text("Selecione", style = MaterialTheme.typography.bodySmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(Radius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GreenElectric,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                focusedLabelColor = GreenElectric
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            times.forEach { time ->
                val sel = time.id == selecionado?.id
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 22.dp)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                time.nome,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) GreenElectric else MaterialTheme.colorScheme.onSurface
                            )
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

// ─────────────────────────────────────────────────────────────
//  Card de retrospecto geral (V-E-D + escudos grandes)
// ─────────────────────────────────────────────────────────────
@Composable
private fun RetrospectoCard(timeA: Time, timeB: Time, stats: ConfrontoStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GreenMid)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "RETROSPECTO GERAL · ${stats.partidas.size} PARTIDAS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(Spacing.md))
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
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        timeA.nome,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Placar V - E - D (perspectiva de A)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        ContagemBig(
                            valor = stats.vitoriasA,
                            label = "V",
                            cor = PromotionGreen
                        )
                        ContagemBig(
                            valor = stats.empates,
                            label = "E",
                            cor = AmberAccent
                        )
                        ContagemBig(
                            valor = stats.vitoriasB,
                            label = "D",
                            cor = RelegationRed
                        )
                    }
                }

                // Time B
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamBadge(nome = timeB.nome, escudoRes = timeB.escudoRes, size = 54.dp)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        timeB.nome,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ContagemBig(valor: Int, label: String, cor: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$valor",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = cor
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = cor.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de gols
// ─────────────────────────────────────────────────────────────
@Composable
private fun GolsCard(timeA: Time, timeB: Time, stats: ConfrontoStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            SectionTitle("Gols no confronto")
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GolsColuna(
                    nome = timeA.nome,
                    escudoRes = timeA.escudoRes,
                    gols = stats.golsA,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "×",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GolsColuna(
                    nome = timeB.nome,
                    escudoRes = timeB.escudoRes,
                    gols = stats.golsB,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GolsColuna(
    nome: String,
    escudoRes: String,
    gols: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        TeamBadge(nome = nome, escudoRes = escudoRes, size = 28.dp)
        Text(
            "$gols",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            nome,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de sequências
// ─────────────────────────────────────────────────────────────
@Composable
private fun SequenciasCard(timeA: Time, timeB: Time, stats: ConfrontoStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            SectionTitle("Maiores sequências")
            Spacer(Modifier.height(Spacing.sm))
            SequenciaLinha(
                titulo = "Vitórias consecutivas",
                nomeA = timeA.nome,
                valA = stats.maiorSequenciaVitoriasA,
                nomeB = timeB.nome,
                valB = stats.maiorSequenciaVitoriasB
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = Spacing.sm)
            )
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
    Text(
        titulo,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(Spacing.xs))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
            Text(
                "$valA jogo${if (valA != 1) "s" else ""}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                nomeA,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Text(
                "$valB jogo${if (valB != 1) "s" else ""}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                nomeB,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de artilheiros (com pódio Gold/Silver/Bronze)
// ─────────────────────────────────────────────────────────────
@Composable
private fun ArtilheirosConfrontoCard(artilheiros: List<ArtilheiroDto>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            SectionTitle("Artilheiros do confronto")
            Spacer(Modifier.height(Spacing.sm))
            artilheiros.forEachIndexed { idx, a ->
                ArtilheiroLinha(idx = idx, artilheiro = a)
                if (idx < artilheiros.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = Spacing.xxs)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtilheiroLinha(idx: Int, artilheiro: ArtilheiroDto) {
    val (corPos, medalha) = when (idx) {
        0 -> GoldChampion to "🥇"
        1 -> SilverRunnerUp to "🥈"
        2 -> BronzePlace to "🥉"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Posição
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (idx < 3) {
                Text(medalha, fontSize = 16.sp)
            } else {
                Text(
                    "${idx + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = corPos
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        // Nome
        Text(
            artilheiro.nomeAbrev,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (idx < 3) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Time
        TeamBadge(nome = artilheiro.nomeTime, escudoRes = artilheiro.escudoRes, size = 18.dp)
        Spacer(Modifier.width(Spacing.xs))
        Text(
            artilheiro.nomeTime,
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Total
        Text(
            "${artilheiro.total} gols",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Linha de partida individual (com badge V/E/D)
// ─────────────────────────────────────────────────────────────
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
        golsA > golsB -> "V" to PromotionGreen
        golsA < golsB -> "D" to RelegationRed
        else          -> "E" to AmberAccent
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, corResultado.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge V / E / D
            Surface(
                color = corResultado.copy(alpha = 0.18f),
                shape = RoundedCornerShape(Radius.sm),
                border = BorderStroke(0.5.dp, corResultado.copy(alpha = 0.6f)),
                modifier = Modifier.padding(end = Spacing.sm)
            ) {
                Text(
                    labelResultado,
                    color = corResultado,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                )
            }

            // Competição + rodada
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    partida.nomeCampeonato,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Rodada ${partida.rodada} · ${if (aEhCasa) "Casa" else "Fora"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Placar com badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                TeamBadge(nome = timeA.nome, escudoRes = timeA.escudoRes, size = 22.dp)
                Text(
                    "$golsA × $golsB",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TeamBadge(nome = timeB.nome, escudoRes = timeB.escudoRes, size = 22.dp)
            }
        }
    }
}
