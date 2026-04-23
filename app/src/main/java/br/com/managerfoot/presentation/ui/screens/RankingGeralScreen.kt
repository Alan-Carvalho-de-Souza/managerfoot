package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.viewmodel.RankingGeralViewModel

// ─────────────────────────────────────────────
//  RankingGeralScreen
//  Tabela de pontuação acumulada de todas as
//  temporadas — base para classificação na Copa.
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingGeralScreen(
    onVoltar: () -> Unit,
    vm: RankingGeralViewModel = hiltViewModel()
) {
    val ranking by vm.ranking.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ranking Geral") },
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
            if (ranking.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum jogo realizado ainda.\nO ranking é atualizado após cada partida.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
                return@Scaffold
            }

            RankingGeralHeader()
            HorizontalDivider()

            LazyColumn {
                itemsIndexed(ranking) { index, entry ->
                    RankingGeralRow(posicao = index + 1, entry = entry, isOdd = index % 2 == 0)
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun RankingGeralHeader() {
    // Barra lateral placeholder (3dp) + padding (8dp) + # (26dp) + badge (24dp) + gap (6dp) = 67dp total indent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 11.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#",     modifier = Modifier.width(26.dp),  fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.width(30.dp)) // badge placeholder (24dp) + gap (6dp)
            Text("Clube", modifier = Modifier.weight(1f),    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Pts",   fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 2.dp))
        }
        Row(
            modifier = Modifier
                .padding(start = 56.dp) // 26dp (#) + 24dp (badge) + 6dp (gap)
                .fillMaxWidth()
        ) {
            listOf("Div", "J", "V", "E", "D", "GP", "GC", "SG", "🏆").forEach { col ->
                Text(col, modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RankingGeralRow(posicao: Int, entry: RankingGeralEntity, isOdd: Boolean = false) {
    val jogos = entry.vitorias + entry.empates + entry.derrotas
    val saldo = entry.golsPro - entry.golsContra
    val saldoText = if (saldo > 0) "+$saldo" else "$saldo"
    val divLabel = when (entry.divisaoAtual) {
        1 -> "A"; 2 -> "B"; 3 -> "C"; 4 -> "D"; 5 -> "A"; 6 -> "B"; else -> "?"
    }

    // Cores de posição: ouro / prata / bronze
    val posBarColor = when (posicao) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFB0BEC5)
        3 -> Color(0xFFCD7F32)
        else -> Color.Transparent
    }
    val posTextColor = when (posicao) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFB0BEC5)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val ptsBgColor = when (posicao) {
        1 -> Color(0xFFFFD700).copy(alpha = 0.15f)
        2 -> Color(0xFFB0BEC5).copy(alpha = 0.15f)
        3 -> Color(0xFFCD7F32).copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val rowBg = if (isOdd) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface

    val colorVitorias  = Color(0xFF4CAF50)
    val colorDerrotas  = Color(0xFFE53935)
    val colorMuted     = MaterialTheme.colorScheme.onSurfaceVariant
    val colorSaldoPos  = Color(0xFF4CAF50)
    val colorSaldoNeg  = Color(0xFFE53935)
    val colorSaldo     = when {
        saldo > 0 -> colorSaldoPos
        saldo < 0 -> colorSaldoNeg
        else      -> colorMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
    ) {
        // Barra lateral colorida (top 3)
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(56.dp)
                .background(posBarColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Linha 1: posição + escudo + nome completo + pontos
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$posicao",
                    modifier = Modifier.width(26.dp),
                    fontSize = 13.sp,
                    fontWeight = if (posicao <= 3) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = posTextColor
                )
                TeamBadge(nome = entry.nomeTime, escudoRes = entry.escudoRes, size = 24.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    entry.nomeTime,
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ptsBgColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${entry.pontosAcumulados}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = posTextColor.takeIf { posicao <= 3 } ?: MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            // Linha 2: stats com cores semânticas
            Row(
                modifier = Modifier
                    .padding(start = 56.dp) // 26dp (#) + 24dp (badge) + 6dp (gap)
                    .fillMaxWidth()
            ) {
                // Div, J — neutros
                listOf(divLabel, "$jogos").forEach { value ->
                    Text(value, modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, color = colorMuted)
                }
                // V — verde
                Text("${entry.vitorias}", modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, color = colorVitorias, fontWeight = FontWeight.Medium)
                // E — neutro
                Text("${entry.empates}", modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, color = colorMuted)
                // D — vermelho
                Text("${entry.derrotas}", modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, color = colorDerrotas, fontWeight = FontWeight.Medium)
                // GP, GC — neutros
                listOf("${entry.golsPro}", "${entry.golsContra}").forEach { value ->
                    Text(value, modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, color = colorMuted)
                }
                // SG — semântico
                Text(saldoText, modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, color = colorSaldo, fontWeight = FontWeight.Medium)
                // Títulos totais (copa + liga)
                val totalTitulos = entry.copasVencidas + entry.titulosNacionais
                Text(
                    if (totalTitulos > 0) "$totalTitulos" else "—",
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (totalTitulos > 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (totalTitulos > 0) Color(0xFFFFD700) else colorMuted
                )
            }
        }
    }
}
