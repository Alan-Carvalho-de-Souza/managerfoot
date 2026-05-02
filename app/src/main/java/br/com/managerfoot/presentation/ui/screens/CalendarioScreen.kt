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
import br.com.managerfoot.data.dao.CalendarioPartidaDto
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.TabRowPill
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.CalendarioViewModel

// ─────────────────────────────────────────────────────────────
//  CalendarioScreen — Tactical Dark
//  Próximos jogos e resultados anteriores do clube do jogador
// ─────────────────────────────────────────────────────────────
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
    val abas = listOf("Próximos (${proximos.size})", "Realizados (${realizados.size})")

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Calendário",
            subtitulo = "Jogos da temporada",
            onVoltar = onVoltar
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.Center
        ) {
            TabRowPill(
                abas = abas,
                selecionada = tabSelecionada,
                onSelecionar = { tabSelecionada = it }
            )
        }

        val lista = if (tabSelecionada == 0) proximos else realizados

        if (lista.isEmpty()) {
            EmptyState(
                if (tabSelecionada == 0) "Nenhum jogo agendado."
                else "Nenhum jogo realizado ainda."
            )
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
                contentPadding = PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(itensList, key = { item ->
                    when (item) {
                        is CalendarioItem.Header -> "mes_${item.chave}"
                        is CalendarioItem.Jogo   -> "jogo_${item.partida.partidaId}"
                    }
                }) { item ->
                    when (item) {
                        is CalendarioItem.Header -> DataHeader(item.label)
                        is CalendarioItem.Jogo   -> CalendarioCard(
                            partida = item.partida,
                            timeJogadorId = timeId
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de uma partida do calendário
// ─────────────────────────────────────────────────────────────
@Composable
private fun CalendarioCard(
    partida: CalendarioPartidaDto,
    timeJogadorId: Int
) {
    val isJogadorCasa = partida.timeCasaId == timeJogadorId
    val isJogadorFora = partida.timeForaId == timeJogadorId
    val envolvido = isJogadorCasa || isJogadorFora

    val (mes, dia) = ordemGlobalParaData(partida.ordemGlobal, partida.nomeCampeonato)
    val dataCurta  = "$dia ${NOMES_MESES[mes].take(3)}"

    // Cor da borda: verde elétrico quando o jogador está envolvido
    val borderColor = when {
        envolvido && partida.jogada -> resultadoBorderColor(partida, isJogadorCasa)
        envolvido -> GreenElectric
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (envolvido) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            // Cabeçalho: competição + data · rodada/fase
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = partida.nomeCampeonato,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (envolvido) GreenElectric
                            else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "$dataCurta · ${partida.fase ?: "Rod. ${partida.rodada}"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            // Times + placar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarioTimeColuna(
                    nome = partida.nomeCasa,
                    escudoRes = partida.escudoCasa,
                    destaque = isJogadorCasa,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier.width(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (partida.jogada && partida.golsCasa != null && partida.golsFora != null) {
                        Text(
                            text = "${partida.golsCasa} × ${partida.golsFora}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = "vs",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                CalendarioTimeColuna(
                    nome = partida.nomeFora,
                    escudoRes = partida.escudoFora,
                    destaque = isJogadorFora,
                    modifier = Modifier.weight(1f)
                )
            }

            // Público e receita (apenas para partidas jogadas pelo clube do jogador)
            if (partida.jogada && envolvido && partida.torcedores != null && partida.torcedores > 0) {
                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 0.5.dp
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👥 ${"%,d".format(partida.torcedores)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (partida.receitaPartida != null) {
                        Text(
                            text = "Bilheteria: ${formatarSaldo(partida.receitaPartida)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isJogadorCasa) MoneyPositive
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarioTimeColuna(
    nome: String,
    escudoRes: String,
    destaque: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        TeamBadge(nome = nome, escudoRes = escudoRes, size = 40.dp)
        Text(
            text = nome,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal,
            color = if (destaque) GreenElectric else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Cor da borda quando o jogador está envolvido e a partida foi jogada.
 * Verde para vitória, âmbar para empate, vermelho para derrota.
 */
private fun resultadoBorderColor(
    partida: CalendarioPartidaDto,
    isJogadorCasa: Boolean
): androidx.compose.ui.graphics.Color {
    val gp = if (isJogadorCasa) partida.golsCasa else partida.golsFora
    val ga = if (isJogadorCasa) partida.golsFora else partida.golsCasa
    if (gp == null || ga == null) return GreenElectric
    return when {
        gp > ga -> PromotionGreen
        gp < ga -> RelegationRed
        else    -> AmberAccent
    }
}

// ─────────────────────────────────────────────────────────────
//  Header do dia (uppercase com letterSpacing — Tactical Dark)
// ─────────────────────────────────────────────────────────────
@Composable
private fun DataHeader(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = GreenElectric,
        letterSpacing = 1.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xxs)
    )
}

// ─────────────────────────────────────────────────────────────
//  Tipos para lista agrupada
// ─────────────────────────────────────────────────────────────
private sealed class CalendarioItem {
    data class Header(val chave: String, val label: String) : CalendarioItem()
    data class Jogo(val partida: CalendarioPartidaDto) : CalendarioItem()
}

// ─────────────────────────────────────────────────────────────
//  Mapeamento ordemGlobal → data exata (preservado de dev)
//
//  Regras por campeonato:
//  • Supercopa Rei          → ordemGlobal = 1  → 25 Jan
//  • Copa do Brasil         → mapeamento fixo pelos ordemGlobal conhecidos
//  • Argentina A/B          → 58 rodadas, OG 10-580 → Fev 8 – Nov 30
//  • Brasileirão A-D        → 38 rodadas, OG 10-380 → Fev 8 – Nov 30
// ─────────────────────────────────────────────────────────────
private val NOMES_MESES = listOf(
    "", "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

private val COPA_DATAS: Map<Int, Pair<Int, Int>> = mapOf(
    13  to (2 to 10),   33  to (2 to 25),
    53  to (3 to 15),   83  to (4 to  5),
    133 to (5 to 15),  163  to (6 to 10),
    207 to (7 to 15),  233  to (8 to  5),
    278 to (9 to 10),  313  to (10 to  8),
    357 to (11 to 12), 383  to (12 to  3)
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
        diaDanoParaMesDia((39 + (ordemGlobal - 10) * 295 / 410).coerceIn(1, 365))
    }
    else -> {
        diaDanoParaMesDia((39 + (ordemGlobal - 10) * 295 / 370).coerceIn(1, 365))
    }
}

private fun anoDePartida(nomeCampeonato: String): Int =
    Regex("\\d{4}").find(nomeCampeonato)?.value?.toIntOrNull() ?: 0
