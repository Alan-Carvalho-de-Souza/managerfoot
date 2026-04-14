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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.dao.CopaPartidaDto
import br.com.managerfoot.domain.engine.MotorCampeonato
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.viewmodel.CopaChaveamentoViewModel

// ─────────────────────────────────────────────
//  CopaChaveamentoScreen
//  Exibe o chaveamento por fase até a final.
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopaChaveamentoScreen(
    copaId: Int,
    copaArgId: Int = -1,
    timeJogadorId: Int,
    onVoltar: () -> Unit,
    vm: CopaChaveamentoViewModel = hiltViewModel()
) {
    val partidas    by vm.partidas.collectAsState()
    val partidasArg by vm.partidasArg.collectAsState()

    LaunchedEffect(copaId, copaArgId) { vm.carregarAmbas(copaId, copaArgId) }

    val mostrarFiltro = copaId > 0 && copaArgId > 0
    var paisSelecionado by remember { mutableStateOf("Brasil") }

    val partidasAtivas = if (paisSelecionado == "Argentina") partidasArg else partidas
    val fasesMotor     = if (paisSelecionado == "Argentina") MotorCampeonato.COPA_ARG_FASES else MotorCampeonato.COPA_FASES

    // Agrupa por fase (ordem definida pelo motor)
    val faseDisponivel = remember(partidasAtivas, fasesMotor) {
        fasesMotor.filter { fase -> partidasAtivas.any { it.fase == fase } }
    }

    var faseSelecionada by remember(faseDisponivel) {
        mutableStateOf(faseDisponivel.firstOrNull() ?: "")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Copas") },
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
            // ── Filtro de país (só exibido quando ambas as copas existem) ──────────
            if (mostrarFiltro) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("🇧🇷 Brasil", "🇦🇷 Argentina").forEach { label ->
                        val pais = if (label.contains("Brasil")) "Brasil" else "Argentina"
                        FilterChip(
                            selected = paisSelecionado == pais,
                            onClick  = { paisSelecionado = pais },
                            label    = { Text(label) }
                        )
                    }
                }
            }

            if (faseDisponivel.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Copa ainda não iniciada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Scaffold
            }

            // Seletor de fase
            ScrollableTabRow(
                selectedTabIndex = faseDisponivel.indexOf(faseSelecionada).coerceAtLeast(0),
                edgePadding = 0.dp
            ) {
                faseDisponivel.forEachIndexed { idx, fase ->
                    Tab(
                        selected = faseSelecionada == fase,
                        onClick  = { faseSelecionada = fase },
                        text     = { Text(fase, maxLines = 1) }
                    )
                }
            }

            val confrontosDaFase = remember(partidasAtivas, faseSelecionada) {
                partidasAtivas.filter { it.fase == faseSelecionada }
                    .groupBy { it.confrontoId }
                    .values.toList()
                    .sortedBy { it.firstOrNull()?.confrontoId ?: 0 }
            }

            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(confrontosDaFase, key = { it.firstOrNull()?.confrontoId ?: 0 }) { jogos ->
                    val ida   = jogos.minByOrNull { it.rodada }
                    val volta = jogos.maxByOrNull { it.rodada }
                    if (ida != null) {
                        ConfrontoCopaCard(
                            ida            = ida,
                            volta          = volta,
                            timeJogadorId  = timeJogadorId
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfrontoCopaCard(
    ida: CopaPartidaDto,
    volta: CopaPartidaDto?,
    timeJogadorId: Int
) {
    // Team A = casa no jogo de ida
    val teamAId   = ida.timeCasaId
    val teamANome = ida.nomeCasa
    val teamAEscudo = ida.escudoCasa
    val teamBId   = ida.timeForaId
    val teamBNome = ida.nomeFora
    val teamBEscudo = ida.escudoFora

    val golsAIda   = ida.golsCasa
    val golsBIda   = ida.golsFora
    val golsAVolta = volta?.golsFora   // A é visitante na volta
    val golsBVolta = volta?.golsCasa   // B é mandante na volta

    val totalA = (golsAIda ?: 0) + (golsAVolta ?: 0)
    val totalB = (golsBIda ?: 0) + (golsBVolta ?: 0)

    val confrontoCompleto = ida.jogada && (volta?.jogada == true)

    // Penalty shootout (stored on the volta row, from perspective of volta's casa/fora teams)
    // volta.timeCasaId == teamB, volta.timeForaId == teamA
    val penBVolta = volta?.penaltisCasa   // volta casa = team B
    val penAVolta = volta?.penaltisForaId  // volta fora = team A
    val foiParaPenaltis = confrontoCompleto && totalA == totalB && penAVolta != null && penBVolta != null
    val teamAVenceuPenaltis = foiParaPenaltis && (penAVolta ?: 0) > (penBVolta ?: 0)
    val teamBVenceuPenaltis = foiParaPenaltis && (penBVolta ?: 0) > (penAVolta ?: 0)

    val teamAVenceu = confrontoCompleto && (totalA > totalB || teamAVenceuPenaltis)
    val teamBVenceu = confrontoCompleto && (totalB > totalA || teamBVenceuPenaltis)

    val jogadorEnvolvido = teamAId == timeJogadorId || teamBId == timeJogadorId

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (jogadorEnvolvido)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: confronto N
            Text(
                "Confronto #${ida.confrontoId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            // Times com placar agregado
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time A
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamBadge(nome = teamANome, escudoRes = teamAEscudo, size = 36.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        teamANome,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (teamAVenceu) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        color = if (teamAVenceu) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Placar agregado
                Column(
                    Modifier.width(80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (confrontoCompleto || golsAIda != null) {
                        Text(
                            "$totalA × $totalB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("agregado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("vs", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Time B
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamBadge(nome = teamBNome, escudoRes = teamBEscudo, size = 36.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        teamBNome,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (teamBVenceu) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        color = if (teamBVenceu) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Detalhes ida / volta
            if (golsAIda != null || volta != null) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Ida
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ida", style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (ida.jogada && golsAIda != null && golsBIda != null)
                            Text("$golsAIda × $golsBIda", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        else
                            Text("—", style = MaterialTheme.typography.bodyMedium)
                    }
                    // Volta
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Volta", style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (volta?.jogada == true && golsBVolta != null && golsAVolta != null)
                            Text("$golsBVolta × $golsAVolta", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        else
                            Text("—", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Resultado nos pênaltis
                if (foiParaPenaltis) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(thickness = 0.5.dp)
                    Spacer(Modifier.height(4.dp))
                    val nomeVencedor = if (teamAVenceuPenaltis) teamANome else teamBNome
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Pênaltis: $penAVolta × $penBVolta",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "$nomeVencedor avançou nos pênaltis",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
