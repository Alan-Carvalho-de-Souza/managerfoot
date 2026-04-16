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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.dao.EstatisticaJogadorDto
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.viewmodel.EstatisticasTimeViewModel
import br.com.managerfoot.presentation.viewmodel.HistoricoTemporada
import br.com.managerfoot.presentation.viewmodel.TemporadaCompeticao

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstatisticasTimeScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: EstatisticasTimeViewModel = hiltViewModel()
) {
    val temporadaStats by vm.temporadaStats.collectAsState()
    val historicoStats by vm.historicoStats.collectAsState()
    val jogadoresHistorico by vm.jogadoresHistorico.collectAsState()
    val notasElenco by vm.notasElenco.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    var abaSelecionada by remember { mutableStateOf(0) }
    val abas = listOf("Temporada", "Histórico", "Notas")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estatísticas do Time") },
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
            TabRow(selectedTabIndex = abaSelecionada) {
                abas.forEachIndexed { idx, titulo ->
                    Tab(
                        selected = abaSelecionada == idx,
                        onClick = { abaSelecionada = idx },
                        text = { Text(titulo) }
                    )
                }
            }

            when (abaSelecionada) {
                0 -> AbaTemporada(temporadaStats)
                1 -> AbaHistorico(historicoStats, jogadoresHistorico)
                2 -> AbaNotas(notasElenco)
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Aba: Temporada atual
// ─────────────────────────────────────────────
@Composable
private fun AbaTemporada(competicoes: List<TemporadaCompeticao>) {
    if (competicoes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nenhuma partida disputada ainda nesta temporada.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(competicoes, key = { it.campeonatoId }) { comp ->
            CompeticaoCard(comp)
        }
    }
}

@Composable
private fun CompeticaoCard(comp: TemporadaCompeticao) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                comp.nome,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))

            // Fase atingida na Copa (quando aplicável)
            if (comp.ehCopa && comp.faseAtingida != null) {
                Text(
                    comp.faseAtingida.faseLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Linha de resultados
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EstatStat("J", comp.jogos)
                EstatStat("V", comp.vitorias)
                EstatStat("E", comp.empates)
                EstatStat("D", comp.derrotas)
                EstatStat("GF", comp.golsPro)
                EstatStat("GC", comp.golsContra)
                if (!comp.ehCopa) EstatStat("Pts", comp.pontos)
            }

            if (comp.jogadores.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Jogadores",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                JogadorTableHeader()
                comp.jogadores.filter { it.gols > 0 || it.assistencias > 0 || it.partidas > 0 }
                    .forEach { j -> JogadorRow(j) }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Aba: Histórico
// ─────────────────────────────────────────────
@Composable
private fun AbaHistorico(
    historico: List<HistoricoTemporada>,
    jogadoresAllTime: List<EstatisticaJogadorDto>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Colocações históricas ──
        item {
            Text(
                "Colocações nas Competições",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
        }

        if (historico.isEmpty()) {
            item {
                Text(
                    "Nenhum histórico disponível ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(historico, key = { it.campeonatoId }) { h ->
                HistoricoCard(h)
            }
        }

        // ── Jogadores históricos ──
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Text(
                "Artilheiros & Assistências do Clube",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
        }

        if (jogadoresAllTime.isEmpty()) {
            item {
                Text(
                    "Nenhuma estatística disponível.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item { JogadorTableHeader() }
            items(
                jogadoresAllTime.filter { it.gols > 0 || it.assistencias > 0 }.take(20),
                key = { it.jogadorId }
            ) { j ->
                JogadorRow(j)
            }
        }
    }
}

@Composable
private fun HistoricoCard(h: HistoricoTemporada) {
    val tipoLabel = when {
        h.tipo == "COPA_NACIONAL" && h.nomeCampeonato.contains("Supercopa", ignoreCase = true) -> "Supercopa Rei"
        h.tipo == "COPA_NACIONAL" && h.nomeCampeonato.contains("Argentina", ignoreCase = true) -> "Copa da Argentina"
        h.tipo == "COPA_NACIONAL" -> "Copa do Brasil"
        h.tipo == "LIGA_ARGENTINA" -> "Liga Argentina"
        h.tipo == "EXTRANGEIRO_DIVISAO1" && h.nomeCampeonato.contains("Apertura", ignoreCase = true) -> "Apertura"
        h.tipo == "EXTRANGEIRO_DIVISAO1" && h.nomeCampeonato.contains("Clausura", ignoreCase = true) -> "Clausura"
        h.tipo == "EXTRANGEIRO_DIVISAO1" -> "Primera División"
        h.tipo == "EXTRANGEIRO_DIVISAO2" -> "Segunda División"
        h.tipo == "NACIONAL_DIVISAO1" -> "Série A"
        h.tipo == "NACIONAL_DIVISAO2" -> "Série B"
        h.tipo == "NACIONAL_DIVISAO3" -> "Série C"
        h.tipo == "NACIONAL_DIVISAO4" -> "Série D"
        else -> h.nomeCampeonato
    }

    // Extract year from the competition name (e.g. "Brasileiro Série A 2026" → "2026")
    val ano = Regex("\\d{4}").find(h.nomeCampeonato)?.value ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tipoLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ano.isNotEmpty()) {
                    Text(
                        ano,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (h.tipo == "COPA_NACIONAL" && h.nomeCampeonato.contains("Supercopa", ignoreCase = true)) {
                val resultado = when {
                    h.vitorias > 0 -> "Campeão"
                    h.derrotas > 0 -> "Vice"
                    else -> "—"
                }
                val corResultado = if (h.vitorias > 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    resultado,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = corResultado,
                    modifier = Modifier.width(56.dp),
                    textAlign = TextAlign.Center
                )
            } else if (h.tipo == "COPA_NACIONAL") {
                Text(
                    h.faseAtingida?.faseLabel() ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(56.dp),
                    textAlign = TextAlign.Center
                )
            } else if (h.tipo == "LIGA_ARGENTINA") {
                Text(
                    "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    "${h.posicao}º",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        h.posicao == 1 -> MaterialTheme.colorScheme.primary
                        h.posicao <= 4 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.width(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstatStat("V", h.vitorias)
                EstatStat("E", h.empates)
                EstatStat("D", h.derrotas)
                if (h.tipo != "COPA_NACIONAL") EstatStat("Pts", h.pontos)
                if (h.tipo == "LIGA_ARGENTINA") EstatStat("J", h.jogos)
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Componentes reutilizáveis
// ─────────────────────────────────────────────
@Composable
private fun EstatStat(label: String, valor: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            valor.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun JogadorTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Jogador",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            "J",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )
        Text(
            "G",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )
        Text(
            "A",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun JogadorRow(j: EstatisticaJogadorDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            j.nomeAbrev,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            j.partidas.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )
        Text(
            j.gols.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (j.gols > 0) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )
        Text(
            j.assistencias.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (j.assistencias > 0) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )
    }
}

private fun String.faseLabel() = when (this) {
    "Primeira Fase" -> "1ª Fase"
    "Segunda Fase"  -> "2ª Fase"
    "Terceira Fase" -> "3ª Fase"
    "Quarta Fase"   -> "4ª Fase"
    "Oitavas"       -> "Oitavas de Final"
    "Quartas"       -> "Quartas de Final"
    "Semi"          -> "Semifinal"
    "Final"         -> "Final"
    else            -> this
}

// ─────────────────────────────────────────────
//  Aba: Notas médias da temporada
// ─────────────────────────────────────────────
@Composable
private fun AbaNotas(jogadores: List<Jogador>) {
    if (jogadores.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nenhum jogador com partidas disputadas ainda.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val mediaGeral = jogadores.map { it.notaMedia }.average().toFloat()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Média geral do elenco",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "%.2f".format(mediaGeral),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = notaColorEstat(mediaGeral)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Jogador", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("P", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center)
                Text("Nota", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End)
            }
            HorizontalDivider()
        }

        items(jogadores, key = { it.id }) { j ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        j.nomeAbreviado,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        j.posicao.abreviacao,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    j.partidasTemporada.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    "%.2f".format(j.notaMedia),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = notaColorEstat(j.notaMedia),
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

private fun notaColorEstat(nota: Float): androidx.compose.ui.graphics.Color = when {
    nota >= 8.0f -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    nota >= 6.5f -> androidx.compose.ui.graphics.Color(0xFF2196F3)
    nota >= 5.0f -> androidx.compose.ui.graphics.Color(0xFFFFC107)
    else         -> androidx.compose.ui.graphics.Color(0xFFF44336)
}
