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
import androidx.compose.ui.graphics.Color
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
    campeonatoAId: Int,
    campeonatoBId: Int,
    campeonatoCId: Int = -1,
    campeonatoDId: Int = -1,
    timeJogadorId: Int,
    onVoltar: () -> Unit = {},
    vm: TabelaViewModel = hiltViewModel()
) {
    val tabela by vm.tabela.collectAsState()
    val times by vm.times.collectAsState()
    val divisaoSelecionada by vm.divisaoSelecionada.collectAsState()

    LaunchedEffect(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId) {
        val divisaoJogador = times.find { it.id == timeJogadorId }?.divisao ?: 1
        vm.carregar(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, divisaoJogador)
    }

    // Opções de divisão: apenas as que têm campeonato ativo (Copa não tem tabela)
    val opcoes = buildList {
        add(1 to "Série A")
        add(2 to "Série B")
        if (campeonatoCId > 0) add(3 to "Série C")
        if (campeonatoDId > 0) add(4 to "Série D")
    }
    val labelSelecionado = opcoes.firstOrNull { it.first == divisaoSelecionada }?.second
        ?: opcoes.first().second
    var expandido by remember { mutableStateOf(false) }

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
            // Seletor de divisão (dropdown)
            ExposedDropdownMenuBox(
                expanded = expandido,
                onExpandedChange = { expandido = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = labelSelecionado,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Divisão") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandido,
                    onDismissRequest = { expandido = false }
                ) {
                    opcoes.forEach { (div, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                vm.selecionarDivisao(div)
                                expandido = false
                            }
                        )
                    }
                }
            }

            // Header row
            TabelaHeader()
            HorizontalDivider()

            LazyColumn {
                itemsIndexed(tabela) { index, item ->
                    val time = times.find { it.id == item.timeId }
                    val nomeTime = time?.nome ?: "Time ${item.timeId}"
                    val escudoRes = time?.escudoRes ?: ""
                    val ehJogador = item.timeId == timeJogadorId
                    val zona = zonaParaDivisao(index + 1, divisaoSelecionada)
                    TabelaRow(
                        posicao = index + 1,
                        nomeTime = nomeTime,
                        escudoRes = escudoRes,
                        item = item,
                        destaque = ehJogador,
                        zonaColor = zona
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }

            // Legenda de zonas
            LegendaZonas(divisaoSelecionada)
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

// Retorna a cor da zona baseado na posição e divisão
@Composable
private fun zonaParaDivisao(posicao: Int, divisao: Int): Color? = when (divisao) {
    1 -> when {
        posicao <= 4  -> Color(0xFF1565C0)  // Libertadores
        posicao <= 6  -> Color(0xFF4CAF50)  // Sul-Americana
        posicao >= 17 -> Color(0xFFE53935)  // Rebaixamento
        else          -> null
    }
    2 -> when {
        posicao <= 4  -> Color(0xFF1565C0)  // Acesso à Série A
        posicao >= 17 -> Color(0xFFE53935)  // Rebaixamento Série C
        else          -> null
    }
    3 -> when {
        posicao <= 4  -> Color(0xFF1565C0)  // Acesso à Série B
        posicao >= 17 -> Color(0xFFE53935)  // Rebaixamento Série D
        else          -> null
    }
    else -> when {
        posicao <= 4  -> Color(0xFF1565C0)  // Acesso à Série C
        else          -> null
    }
}

@Composable
private fun LegendaZonas(divisao: Int) {
    val itens = when (divisao) {
        1 -> listOf(
            Color(0xFF1565C0) to "1-4: Libertadores",
            Color(0xFF4CAF50) to "5-6: Sul-Americana",
            Color(0xFFE53935) to "17-20: Rebaixamento"
        )
        2 -> listOf(
            Color(0xFF1565C0) to "1-4: Acesso à Série A",
            Color(0xFFE53935) to "17-20: Rebaixamento"
        )
        3 -> listOf(
            Color(0xFF1565C0) to "1-4: Acesso à Série B",
            Color(0xFFE53935) to "17-20: Rebaixamento"
        )
        else -> listOf(
            Color(0xFF1565C0) to "1-4: Acesso à Série C"
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itens.forEach { (cor, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(10.dp).background(cor))
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TabelaRow(
    posicao: Int,
    nomeTime: String,
    escudoRes: String = "",
    item: ClassificacaoEntity,
    destaque: Boolean,
    zonaColor: Color? = null
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
        // Indicador de zona (barra colorida à esquerda)
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .background(zonaColor ?: Color.Transparent)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "$posicao",
            modifier = Modifier.width(24.dp),
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
