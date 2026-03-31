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
import br.com.managerfoot.presentation.viewmodel.CalendarioViewModel

// ─────────────────────────────────────────────
//  CalendarioScreen
//  Exibe próximos jogos e resultados anteriores
//  do clube do jogador, por competição.
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarioScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: CalendarioViewModel = hiltViewModel()
) {
    val partidas by vm.partidas.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    val proximos = remember(partidas) {
        partidas.filter { !it.jogada }.sortedWith(
            compareBy(
                { mesDePartida(it.nomeCampeonato, it.rodada, it.fase) },
                { it.ordemGlobal }
            )
        )
    }
    val realizados = remember(partidas) {
        partidas.filter { it.jogada }.sortedWith(
            Comparator<CalendarioPartidaDto> { a, b ->
                val mA = mesDePartida(a.nomeCampeonato, a.rodada, a.fase)
                val mB = mesDePartida(b.nomeCampeonato, b.rodada, b.fase)
                if (mA != mB) mB - mA else b.ordemGlobal - a.ordemGlobal
            }
        )
    }

    var tabSelecionada by remember { mutableIntStateOf(0) }
    val tabs = listOf("Próximos (${proximos.size})", "Realizados (${realizados.size})")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendário") },
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
            TabRow(selectedTabIndex = tabSelecionada) {
                tabs.forEachIndexed { idx, titulo ->
                    Tab(
                        selected = tabSelecionada == idx,
                        onClick = { tabSelecionada = idx },
                        text = { Text(titulo) }
                    )
                }
            }

            val lista = if (tabSelecionada == 0) proximos else realizados

            if (lista.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tabSelecionada == 0) "Nenhum jogo agendado." else "Nenhum jogo realizado ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val itensList = remember(lista) {
                    buildList {
                        var mesPrev = -1
                        lista.forEach { p ->
                            val mes = mesDePartida(p.nomeCampeonato, p.rodada, p.fase)
                            if (mes != mesPrev) {
                                add(CalendarioItem.Header(mes))
                                mesPrev = mes
                            }
                            add(CalendarioItem.Jogo(p))
                        }
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(itensList, key = { item ->
                        when (item) {
                            is CalendarioItem.Header -> "mes_${item.mes}"
                            is CalendarioItem.Jogo   -> "jogo_${item.partida.partidaId}"
                        }
                    }) { item ->
                        when (item) {
                            is CalendarioItem.Header -> MesHeader(NOMES_MESES[item.mes])
                            is CalendarioItem.Jogo   -> CalendarioCard(
                                partida = item.partida, timeJogadorId = timeId
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarioCard(
    partida: CalendarioPartidaDto,
    timeJogadorId: Int
) {
    val isJogadorCasa = partida.timeCasaId == timeJogadorId
    val isJogadorFora = partida.timeForaId == timeJogadorId
    val destaqueColor = MaterialTheme.colorScheme.primaryContainer
    val normalColor   = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isJogadorCasa || isJogadorFora) destaqueColor else normalColor
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Cabeçalho: competição + rodada
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = partida.nomeCampeonato,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = partida.fase ?: "Rodada ${partida.rodada}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // Times + placar
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        size = 40.dp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = partida.nomeCasa,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isJogadorCasa) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }

                // Placar / "vs"
                Box(
                    modifier = Modifier.width(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (partida.jogada && partida.golsCasa != null && partida.golsFora != null) {
                        Text(
                            text = "${partida.golsCasa} × ${partida.golsFora}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "vs",
                            style = MaterialTheme.typography.bodyMedium
                        )
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
                        size = 40.dp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = partida.nomeFora,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isJogadorFora) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Cabeçalho de mês
// ─────────────────────────────────────────────
@Composable
private fun MesHeader(nome: String) {
    Text(
        text = nome.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
    )
}

// ─────────────────────────────────────────────
//  Tipos para lista agrupada
// ─────────────────────────────────────────────
private sealed class CalendarioItem {
    data class Header(val mes: Int) : CalendarioItem()
    data class Jogo(val partida: CalendarioPartidaDto) : CalendarioItem()
}

// ─────────────────────────────────────────────
//  Mapeamento de mês
//  • Copa do Brasil: fevereiro (Prim. Fase) → novembro (Final)
//  • Brasileirão:   março (R1) → dezembro (R37-38), 38 rodadas / 10 meses
// ─────────────────────────────────────────────
private val NOMES_MESES = listOf(
    "", "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

private fun mesDePartida(nomeCampeonato: String, rodada: Int, fase: String?): Int =
    if (nomeCampeonato.contains("Copa", ignoreCase = true)) {
        when (fase) {
            "Primeira Fase" -> 2
            "Segunda Fase"  -> 3
            "Oitavas"       -> 5
            "Quartas"       -> 7
            "Semi"          -> 9
            "Final"         -> 11
            else            -> 5
        }
    } else {
        // 38 rodadas de março (3) a dezembro (12) = 10 meses
        (3 + ((rodada - 1) * 10 / 38)).coerceIn(3, 12)
    }
