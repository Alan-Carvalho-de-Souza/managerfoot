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
import br.com.managerfoot.presentation.ui.components.formatarSaldo
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
                { anoDePartida(it.nomeCampeonato) },
                { val (m, d) = ordemGlobalParaData(it.ordemGlobal, it.nomeCampeonato); m * 100 + d }
            )
        )
    }
    val realizados = remember(partidas) {
        partidas.filter { it.jogada }.sortedWith(
            compareByDescending<CalendarioPartidaDto> { anoDePartida(it.nomeCampeonato) }
                .thenByDescending { val (m, d) = ordemGlobalParaData(it.ordemGlobal, it.nomeCampeonato); m * 100 + d }
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
                        var chavePrev = ""
                        lista.forEach { p ->
                            val (mes, dia) = ordemGlobalParaData(p.ordemGlobal, p.nomeCampeonato)
                            val ano = anoDePartida(p.nomeCampeonato)
                            val chave = "${ano}_${"%02d".format(mes)}_${"%02d".format(dia)}"
                            val mesNome = NOMES_MESES[mes]
                            val label = if (ano > 0) "$dia de $mesNome de $ano" else "$dia de $mesNome"
                            if (chave != chavePrev) {
                                add(CalendarioItem.Header(chave, label))
                                chavePrev = chave
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
                            is CalendarioItem.Header -> "mes_${item.chave}"
                            is CalendarioItem.Jogo   -> "jogo_${item.partida.partidaId}"
                        }
                    }) { item ->
                        when (item) {
                            is CalendarioItem.Header -> MesHeader(item.label)
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

    val (mes, dia) = ordemGlobalParaData(partida.ordemGlobal, partida.nomeCampeonato)
    val dataCurta  = "$dia ${NOMES_MESES[mes].take(3)}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isJogadorCasa || isJogadorFora) destaqueColor else normalColor
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Cabeçalho: competição + data · rodada/fase
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = partida.nomeCampeonato,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$dataCurta · ${partida.fase ?: "Rodada ${partida.rodada}"}",
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
            // Público e receita (partidas jogadas pelo jogador: casa ou visitante)
            if (partida.jogada && (isJogadorCasa || isJogadorFora) && partida.torcedores != null && partida.torcedores > 0) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "\uD83D\uDC65 ${"%,d".format(partida.torcedores)} torcedores",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (partida.receitaPartida != null) {
                        Text(
                            text = "Bilheteria: ${formatarSaldo(partida.receitaPartida)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isJogadorCasa) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Cabeçalho de dia
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
    data class Header(val chave: String, val label: String) : CalendarioItem()
    data class Jogo(val partida: CalendarioPartidaDto) : CalendarioItem()
}

// ─────────────────────────────────────────────
//  Mapeamento ordemGlobal → data exata
//
//  Regras por campeonato:
//  • Supercopa Rei          → ordemGlobal = 1  → 25 Jan
//  • Copa do Brasil         → mapeamento fixo pelos ordemGlobal conhecidos
//  • Argentina A/B          → 58 rodadas, OG 10-580 → Fev 8 – Nov 30
//  • Brasileirão A-D        → 38 rodadas, OG 10-380 → Fev 8 – Nov 30
// ─────────────────────────────────────────────
private val NOMES_MESES = listOf(
    "", "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

// COPA_ORDEM_GLOBAL = intArrayOf(2, 7, 15, 35, 95, 115, 175, 195, 245, 265, 335, 355)
private val COPA_DATAS: Map<Int, Pair<Int, Int>> = mapOf(
    2   to (2 to 10),   7   to (2 to 25),
    15  to (3 to 15),   35  to (4 to  5),
    95  to (5 to 15),  115  to (6 to 10),
    175 to (7 to 15),  195  to (8 to  5),
    245 to (9 to 10),  265  to (10 to  8),
    335 to (11 to 12), 355  to (12 to  3)
)

private val DIAS_MES = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

/** Converte dia-do-ano (1-365) em (mês, dia). */
private fun diaDanoParaMesDia(diaDoAno: Int): Pair<Int, Int> {
    var rem = diaDoAno.coerceIn(1, 365)
    for (m in 1..12) {
        if (rem <= DIAS_MES[m]) return m to rem
        rem -= DIAS_MES[m]
    }
    return 12 to 31
}

/** Retorna (mês, dia) para uma partida baseado em ordemGlobal e nome do campeonato. */
private fun ordemGlobalParaData(ordemGlobal: Int, nomeCampeonato: String): Pair<Int, Int> = when {
    ordemGlobal == 1 -> 1 to 25   // Supercopa Rei: 25 de Janeiro
    nomeCampeonato.contains("Copa", ignoreCase = true) ->
        COPA_DATAS[ordemGlobal]
            ?: diaDanoParaMesDia((39 + (ordemGlobal - 10) * 295 / 370).coerceIn(1, 365))
    nomeCampeonato.contains("Argentina", ignoreCase = true) -> {
        // 21 rodadas × 2 torneios → OG 10..420; temporada fev (dia 39) a nov (dia 334) = 295 dias
        diaDanoParaMesDia((39 + (ordemGlobal - 10) * 295 / 410).coerceIn(1, 365))
    }
    else -> {
        // Brasileirão A-D: 38 rodadas → OG 10..380; temporada fev (dia 39) a nov (dia 334)
        diaDanoParaMesDia((39 + (ordemGlobal - 10) * 295 / 370).coerceIn(1, 365))
    }
}

private fun anoDePartida(nomeCampeonato: String): Int =
    Regex("\\d{4}").find(nomeCampeonato)?.value?.toIntOrNull() ?: 0
