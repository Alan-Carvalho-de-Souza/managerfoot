package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import br.com.managerfoot.data.database.entities.RankingGeralEntity
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.RankingGeralViewModel

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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Ranking Geral",
            subtitulo = if (ranking.isNotEmpty()) "${ranking.size} clubes pontuados"
                        else "Pontuação histórica",
            onVoltar = onVoltar
        )

        if (ranking.isEmpty()) {
            EmptyState(
                "Nenhum jogo realizado ainda.\n" +
                "O ranking é atualizado após cada partida."
            )
            return@Column
        }

        RankingGeralHeader()
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        LazyColumn {
            itemsIndexed(ranking) { index, entry ->
                RankingGeralRow(
                    posicao = index + 1,
                    entry = entry,
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
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "PTS",
                style = MaterialTheme.typography.labelSmall,
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
                    fontSize = 9.sp,
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
        textAlign = TextAlign.Center,
        color = cor,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}
