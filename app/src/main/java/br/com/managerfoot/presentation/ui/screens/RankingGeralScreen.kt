package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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

            // Cabeçalho
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#",   modifier = Modifier.width(28.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Clube", modifier = Modifier.weight(1f),  fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Div", modifier = Modifier.width(30.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Pts", modifier = Modifier.width(38.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("J",   modifier = Modifier.width(26.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("V",   modifier = Modifier.width(26.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("E",   modifier = Modifier.width(26.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("D",   modifier = Modifier.width(26.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("🏆",  modifier = Modifier.width(28.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            HorizontalDivider()

            LazyColumn {
                itemsIndexed(ranking) { index, entry ->
                    RankingGeralRow(posicao = index + 1, entry = entry)
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun RankingGeralRow(posicao: Int, entry: RankingGeralEntity) {
    val posColor = when (posicao) {
        1 -> MaterialTheme.colorScheme.primary
        2 -> MaterialTheme.colorScheme.secondary
        3 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val divLabel = when (entry.divisaoAtual) {
        1 -> "A"; 2 -> "B"; 3 -> "C"; 4 -> "D"; else -> "?"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$posicao",
            modifier = Modifier.width(28.dp),
            fontSize = 13.sp,
            fontWeight = if (posicao <= 3) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = posColor
        )
        TeamBadge(nome = entry.nomeTime, escudoRes = entry.escudoRes, size = 24.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            entry.nomeTime,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            divLabel,
            modifier = Modifier.width(30.dp),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${entry.pontosAcumulados}",
            modifier = Modifier.width(38.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = posColor
        )
        val jogos = entry.vitorias + entry.empates + entry.derrotas
        Text("$jogos",            modifier = Modifier.width(26.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
        Text("${entry.vitorias}", modifier = Modifier.width(26.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
        Text("${entry.empates}",  modifier = Modifier.width(26.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
        Text("${entry.derrotas}", modifier = Modifier.width(26.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
        Text(
            if (entry.copasVencidas > 0) "${entry.copasVencidas}" else "—",
            modifier = Modifier.width(28.dp),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            fontWeight = if (entry.copasVencidas > 0) FontWeight.Bold else FontWeight.Normal,
            color = if (entry.copasVencidas > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
