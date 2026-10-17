package br.com.managerfoot.presentation.ui.screens

import br.com.managerfoot.presentation.ui.theme.fundoApp

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.dao.RankingConmebolLinha
import br.com.managerfoot.data.database.entities.RankingGeralEntity
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.FilterChipPill
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.TabRowPill
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.RankingGeralUi
import br.com.managerfoot.presentation.viewmodel.RankingGeralViewModel
import br.com.managerfoot.presentation.viewmodel.continenteDePais

// ─────────────────────────────────────────────────────────────
//  RankingGeralScreen — Tactical Dark
//  Pontuação acumulada de todas as temporadas (base da Copa)
// ─────────────────────────────────────────────────────────────
@Composable
fun RankingGeralScreen(
    onVoltar: () -> Unit,
    vm: RankingGeralViewModel = hiltViewModel()
) {
    val ranking by vm.ranking.collectAsState()
    val paisJogador by vm.paisJogador.collectAsState()
    val rankingConmebol by vm.rankingConmebol.collectAsState()

    // Qual dos dois rankings está em tela: 0 = Geral (desempenho) · 1 = Colocação
    var tipoRanking by remember { mutableIntStateOf(0) }

    // 0 = Mundo · 1 = Continente · 2 = País
    var modo by remember { mutableIntStateOf(0) }

    // Os recortes valem para os dois rankings, então as opções são derivadas do
    // ranking ativo: o de colocação cobre só a América do Sul, por exemplo.
    val paisesDoRankingAtivo = remember(tipoRanking, ranking, rankingConmebol) {
        if (tipoRanking == 1) rankingConmebol.map { it.pais } else ranking.map { it.pais }
    }
    val continentes = remember(paisesDoRankingAtivo) {
        paisesDoRankingAtivo.map { continenteDePais(it) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
    }
    val paises = remember(paisesDoRankingAtivo) {
        paisesDoRankingAtivo.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
    }
    var continenteSel by remember { mutableStateOf<String?>(null) }
    var paisSel by remember { mutableStateOf<String?>(null) }

    // Default: recorte do próprio clube do jogador (quando disponível). Também
    // corrige a seleção ao trocar de ranking, se ela não existir no novo recorte.
    LaunchedEffect(paisJogador, continentes, paises) {
        if (continenteSel == null || continenteSel !in continentes) {
            continenteSel = paisJogador?.let { continenteDePais(it) }?.takeIf { it in continentes }
                ?: continentes.firstOrNull()
        }
        if (paisSel == null || paisSel !in paises) {
            paisSel = paisJogador?.takeIf { it in paises } ?: paises.firstOrNull()
        }
    }

    val visivel = when (modo) {
        1 -> ranking.filter { it.continente == continenteSel }
        2 -> ranking.filter { it.pais == paisSel }
        else -> ranking
    }
    val visivelConmebol = when (modo) {
        1 -> rankingConmebol.filter { continenteDePais(it.pais) == continenteSel }
        2 -> rankingConmebol.filter { it.pais == paisSel }
        else -> rankingConmebol
    }
    val totalVisivel = if (tipoRanking == 1) visivelConmebol.size else visivel.size
    val rankingAtivoVazio = if (tipoRanking == 1) rankingConmebol.isEmpty() else ranking.isEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .fundoApp()
    ) {
        ScreenTopBar(
            titulo = "Ranking de Clubes",
            subtitulo = when (modo) {
                1 -> "${continenteSel ?: "Continente"} · $totalVisivel clubes"
                2 -> "${paisSel ?: "País"} · $totalVisivel clubes"
                else -> if (!rankingAtivoVazio) "$totalVisivel clubes pontuados" else "Pontuação histórica"
            },
            onVoltar = onVoltar
        )

        // Seletor do ranking: desempenho em campo vs. colocação nas competições
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm),
            horizontalArrangement = Arrangement.Center
        ) {
            TabRowPill(
                abas = listOf("Geral", "Colocação"),
                selecionada = tipoRanking,
                onSelecionar = { tipoRanking = it }
            )
        }

        if (rankingAtivoVazio) {
            EmptyState(
                if (tipoRanking == 1)
                    "Nenhum clube pontuado ainda.\n" +
                    "Por ora só a América do Sul pontua por colocação."
                else
                    "Nenhum jogo realizado ainda.\n" +
                    "O ranking é atualizado após cada partida."
            )
            return@Column
        }

        // Seletor de recorte: Mundo / Continente / País
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.Center
        ) {
            TabRowPill(
                abas = listOf("Mundo", "Continente", "País"),
                selecionada = modo,
                onSelecionar = { modo = it }
            )
        }

        // Chips do recorte ativo (continentes ou países)
        if (modo == 1 || modo == 2) {
            val opcoes = if (modo == 1) continentes else paises
            val selecionado = if (modo == 1) continenteSel else paisSel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                opcoes.forEach { opc ->
                    FilterChipPill(
                        label = opc,
                        selected = opc == selecionado,
                        onClick = { if (modo == 1) continenteSel = opc else paisSel = opc }
                    )
                }
            }
        }

        if (tipoRanking == 1) {
            RankingConmebolLista(visivelConmebol)
            return@Column
        }

        RankingGeralHeader()
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        if (visivel.isEmpty()) {
            EmptyState("Nenhum clube pontuado neste recorte ainda.")
            return@Column
        }

        LazyColumn {
            itemsIndexed(visivel) { index, ui ->
                RankingGeralRow(
                    posicao = index + 1,
                    entry = ui.entry,
                    isOdd = index % 2 == 0
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            }
            item { Spacer(Modifier.height(Spacing.lg)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Ranking de Colocação (regras CONMEBOL) — pontos por colocação nas
//  competições. A pontuação já vem do DAO com a depreciação anual aplicada.
// ─────────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.RankingConmebolLista(linhas: List<RankingConmebolLinha>) {
    if (linhas.isEmpty()) {
        EmptyState("Nenhum clube pontuado neste recorte ainda.")
        return
    }

    RankingConmebolHeader()
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

    LazyColumn {
        itemsIndexed(linhas) { index, linha ->
            RankingConmebolRow(
                posicao = index + 1,
                linha = linha,
                isOdd = index % 2 == 0
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
        }
        item { Spacer(Modifier.height(Spacing.lg)) }
    }
}

@Composable
private fun RankingConmebolHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 11.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#",
            modifier = Modifier.width(26.dp),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(30.dp))
        Text(
            "CLUBE",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "PTS",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 2.dp)
        )
    }
}

@Composable
private fun RankingConmebolRow(posicao: Int, linha: RankingConmebolLinha, isOdd: Boolean) {
    val (posBarColor, posTextColor) = when (posicao) {
        1 -> GoldChampion to GoldChampion
        2 -> SilverRunnerUp to SilverRunnerUp
        3 -> BronzePlace to BronzePlace
        else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
    }
    val ptsBgColor = when (posicao) {
        1 -> GoldChampion.copy(alpha = 0.18f)
        2 -> SilverRunnerUp.copy(alpha = 0.18f)
        3 -> BronzePlace.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val rowBg = if (isOdd) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(56.dp)
                .background(posBarColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$posicao",
                    modifier = Modifier.width(26.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    fontWeight = if (posicao <= 3) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = posTextColor
                )
                TeamBadge(nome = linha.nomeTime, escudoRes = linha.escudoRes, size = 24.dp)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    linha.nomeTime,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = ptsBgColor
                ) {
                    Text(
                        formatarPontosConmebol(linha.pontos),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (posicao <= 3) posTextColor else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            // Decomposição da pontuação: continental · liga nacional · histórico
            Row(modifier = Modifier.padding(start = 56.dp)) {
                Text(
                    buildString {
                        append(linha.pais.ifBlank { "—" })
                        append(" · Cont ${formatarPontosConmebol(linha.pontosDesempenho)}")
                        append(" · Liga ${formatarPontosConmebol(linha.pontosLiga)}")
                        append(" · Hist ${formatarPontosConmebol(linha.pontosHistorico)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Pontos CONMEBOL com no máximo uma casa decimal (7,2 do coeficiente histórico). */
private fun formatarPontosConmebol(valor: Double): String {
    val arredondado = Math.round(valor * 10.0) / 10.0
    return if (arredondado % 1.0 == 0.0) arredondado.toInt().toString()
           else String.format("%.1f", arredondado)
}

// ─────────────────────────────────────────────────────────────
//  Header da tabela (uppercase letterSpacing — Tactical Dark)
// ─────────────────────────────────────────────────────────────
@Composable
private fun RankingGeralHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 11.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#",
                modifier = Modifier.width(26.dp),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(30.dp))
            Text(
                "CLUBE",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "PTS",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 2.dp)
            )
        }
        Row(
            modifier = Modifier
                .padding(start = 56.dp)
                .fillMaxWidth()
        ) {
            listOf("Div", "J", "V", "E", "D", "GP", "GC", "SG", "🏆").forEach { col ->
                Text(
                    col,
                    modifier = Modifier.weight(1f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Linha de um clube no ranking
// ─────────────────────────────────────────────────────────────
@Composable
private fun RankingGeralRow(posicao: Int, entry: RankingGeralEntity, isOdd: Boolean = false) {
    val jogos = entry.vitorias + entry.empates + entry.derrotas
    val saldo = entry.golsPro - entry.golsContra
    val saldoText = if (saldo > 0) "+$saldo" else "$saldo"
    val divLabel = when (entry.divisaoAtual) {
        1 -> "A"; 2 -> "B"; 3 -> "C"; 4 -> "D"
        5 -> "A"; 6 -> "B"
        9 -> "A"; 10 -> "B"
        else -> "?"
    }

    // Cores de pódio Tactical Dark
    val (posBarColor, posTextColor) = when (posicao) {
        1 -> GoldChampion to GoldChampion
        2 -> SilverRunnerUp to SilverRunnerUp
        3 -> BronzePlace to BronzePlace
        else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
    }
    val ptsBgColor = when (posicao) {
        1 -> GoldChampion.copy(alpha = 0.18f)
        2 -> SilverRunnerUp.copy(alpha = 0.18f)
        3 -> BronzePlace.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val ptsBorderColor = when (posicao) {
        1 -> GoldChampion.copy(alpha = 0.5f)
        2 -> SilverRunnerUp.copy(alpha = 0.5f)
        3 -> BronzePlace.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val rowBg = if (isOdd) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
    ) {
        // Faixa lateral colorida (top 3)
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(56.dp)
                .background(posBarColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) {
            // Linha 1: posição + escudo + nome + pontos
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$posicao",
                    modifier = Modifier.width(26.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    fontWeight = if (posicao <= 3) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = posTextColor
                )
                TeamBadge(nome = entry.nomeTime, escudoRes = entry.escudoRes, size = 24.dp)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    entry.nomeTime,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = ptsBgColor,
                    border = if (posicao <= 3) androidx.compose.foundation.BorderStroke(0.5.dp, ptsBorderColor)
                             else null
                ) {
                    Text(
                        "${entry.pontosAcumulados}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (posicao <= 3) posTextColor else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            // Linha 2: stats com cores semânticas Tactical Dark
            Row(
                modifier = Modifier
                    .padding(start = 56.dp)
                    .fillMaxWidth()
            ) {
                StatNumero(divLabel)
                StatNumero("$jogos")
                StatNumero("${entry.vitorias}", cor = PromotionGreen, bold = true)
                StatNumero("${entry.empates}")
                StatNumero("${entry.derrotas}", cor = RelegationRed, bold = true)
                StatNumero("${entry.golsPro}")
                StatNumero("${entry.golsContra}")
                StatNumero(
                    saldoText,
                    cor = when {
                        saldo > 0 -> PromotionGreen
                        saldo < 0 -> RelegationRed
                        else      -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    bold = saldo != 0
                )
                val totalTitulos = entry.copasVencidas + entry.titulosNacionais
                StatNumero(
                    if (totalTitulos > 0) "$totalTitulos" else "—",
                    cor = if (totalTitulos > 0) GoldChampion else MaterialTheme.colorScheme.onSurfaceVariant,
                    bold = totalTitulos > 0
                )
            }
        }
    }
}

@Composable
private fun RowScope.StatNumero(
    valor: String,
    cor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bold: Boolean = false
) {
    Text(
        valor,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        color = cor,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}
