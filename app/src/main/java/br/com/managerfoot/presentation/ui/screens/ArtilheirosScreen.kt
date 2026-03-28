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
import br.com.managerfoot.data.dao.ArtilheiroDto
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.viewmodel.ArtilheirosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtilheirosScreen(
    campeonatoAId: Int,
    campeonatoBId: Int,
    campeonatoCId: Int = -1,
    campeonatoDId: Int = -1,
    copaId: Int = -1,
    onVoltar: () -> Unit = {},
    vm: ArtilheirosViewModel = hiltViewModel()
) {
    val artilheiros       by vm.artilheiros.collectAsState()
    val assistentes       by vm.assistentes.collectAsState()
    val artilheirosTotal  by vm.artilheirosAllTime.collectAsState()
    val assistentesTotal  by vm.assistentesAllTime.collectAsState()
    val artilheirosHist   by vm.artilheirosHistorico.collectAsState()
    val assistentesHist   by vm.assistentesHistorico.collectAsState()
    val divisaoSelecionada        by vm.divisaoSelecionada.collectAsState()
    val divisaoHistoricoSelecionada by vm.divisaoHistoricoSelecionada.collectAsState()

    LaunchedEffect(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, copaId) {
        vm.carregar(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, copaId)
    }

    // Escopo: 0 = temporada atual, 1 = histórico total
    var escopoSelecionado by remember { mutableIntStateOf(0) }
    // Aba: 0 = artilharia, 1 = assistências
    var abaSelecionada by remember { mutableIntStateOf(0) }
    val abas = listOf("Artilharia", "Assistências")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artilharia & Assistências") },
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
            // Seletor de escopo (Temporada / Histórico)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Temporada atual", "Histórico total").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = escopoSelecionado == idx,
                        onClick  = { escopoSelecionado = idx },
                        label    = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Seletor de competição (dropdown) — visível em ambos os escopos
            val opcoesBase = buildList {
                add(0 to "Todas as competições")
                add(1 to "Série A")
                add(2 to "Série B")
                if (campeonatoCId > 0) add(3 to "Série C")
                if (campeonatoDId > 0) add(4 to "Série D")
                if (copaId > 0) add(5 to "Copa do Brasil")
            }

            // Seletor de competição (dropdown) — só visível em "Temporada atual"
            if (escopoSelecionado == 0) {
                val opcoes = opcoesBase
                val labelSelecionado = opcoes.firstOrNull { it.first == divisaoSelecionada }?.second
                    ?: opcoes.first().second
                var expandido by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expandido,
                    onExpandedChange = { expandido = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = labelSelecionado,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Competição") },
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
            }

            // Seletor de competição (dropdown) — só visível em "Histórico total"
            if (escopoSelecionado == 1) {
                val opcoes = opcoesBase
                val labelSelecionado = opcoes.firstOrNull { it.first == divisaoHistoricoSelecionada }?.second
                    ?: opcoes.first().second
                var expandido by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expandido,
                    onExpandedChange = { expandido = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = labelSelecionado,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Competição") },
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
                                    vm.selecionarDivisaoHistorico(div)
                                    expandido = false
                                }
                            )
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = abaSelecionada) {
                abas.forEachIndexed { idx, titulo ->
                    Tab(
                        selected = abaSelecionada == idx,
                        onClick  = { abaSelecionada = idx },
                        text     = { Text(titulo) }
                    )
                }
            }

            val lista = when {
                escopoSelecionado == 0 && abaSelecionada == 0 -> artilheiros
                escopoSelecionado == 0 && abaSelecionada == 1 -> assistentes
                // histórico: se div=0 usa AllTime completo; se filtrado e não vazio usa filtrado; senão AllTime
                escopoSelecionado == 1 && abaSelecionada == 0 ->
                    if (divisaoHistoricoSelecionada == 0) artilheirosTotal else artilheirosHist
                else ->
                    if (divisaoHistoricoSelecionada == 0) assistentesTotal else assistentesHist
            }
            val colTitulo = if (abaSelecionada == 0) "G" else "A"

            if (lista.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum evento registrado ainda",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#",          modifier = Modifier.width(28.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Jogador",    modifier = Modifier.weight(1f),   fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Clube",      modifier = Modifier.weight(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(colTitulo,    modifier = Modifier.width(32.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                HorizontalDivider()

                LazyColumn {
                    itemsIndexed(lista) { index, item ->
                        ArtilheiroRow(posicao = index + 1, item = item, colTitulo = colTitulo)
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtilheiroRow(posicao: Int, item: ArtilheiroDto, colTitulo: String) {
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
            color = when (posicao) {
                1 -> MaterialTheme.colorScheme.primary
                2 -> MaterialTheme.colorScheme.secondary
                3 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        Text(
            item.nomeAbrev,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = if (posicao <= 3) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.weight(0.7f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamBadge(nome = item.nomeTime, escudoRes = item.escudoRes, size = 18.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                item.nomeTime,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "${item.total}",
            modifier = Modifier.width(32.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
