package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.background
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
import br.com.managerfoot.data.database.entities.ClassificacaoEntity
import br.com.managerfoot.presentation.viewmodel.TabelaViewModel
import br.com.managerfoot.presentation.ui.components.TeamBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabelaScreen(
    campeonatoId: Int,
    timeJogadorId: Int,
    onVoltar: () -> Unit = {},
    vm: TabelaViewModel = hiltViewModel()
) {
    val tabela by vm.tabela.collectAsState()
    val times by vm.times.collectAsState()

    LaunchedEffect(campeonatoId) { vm.carregar(campeonatoId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tabela de Classificação") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header row
            TabelaHeader()
            HorizontalDivider()

            LazyColumn {
                itemsIndexed(tabela) { index, item ->
                    val time = times.find { it.id == item.timeId }
                    val nomeTime = time?.nome ?: "Time ${item.timeId}"
                    val escudoRes = time?.escudoRes ?: ""
                    val ehJogador = item.timeId == timeJogadorId
                    TabelaRow(
                        posicao = index + 1,
                        nomeTime = nomeTime,
                        escudoRes = escudoRes,
                        item = item,
                        destaque = ehJogador
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun TabelaHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#",  modifier = Modifier.width(28.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Time", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        listOf("J", "V", "E", "D", "GP", "GC", "SG", "Pts").forEach { col ->
            Text(col, modifier = Modifier.width(28.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TabelaRow(
    posicao: Int,
    nomeTime: String,
    escudoRes: String = "",
    item: ClassificacaoEntity,
    destaque: Boolean
) {
    val bg = if (destaque) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val textColorPrimary = if (destaque) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val textColorSecondary = if (destaque) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$posicao",
            modifier = Modifier.width(28.dp),
            fontSize = 12.sp,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = textColorPrimary
        )
        TeamBadge(nome = nomeTime, escudoRes = escudoRes, size = 22.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            nomeTime,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = textColorPrimary
        )
        listOf(
            item.jogos, item.vitorias, item.empates, item.derrotas,
            item.golsPro, item.golsContra, item.saldoGols, item.pontos
        ).forEachIndexed { idx, value ->
            val isSaldo = idx == 6  // saldoGols can be negative
            val isBold = idx == 7   // pontos
            Text(
                if (isSaldo && value > 0) "+$value" else "$value",
                modifier = Modifier.width(28.dp),
                fontSize = 12.sp,
                fontWeight = if (isBold || destaque) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = if (isBold) textColorPrimary else textColorSecondary
            )
        }
    }
}
