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
    copaArgId: Int = -1,
    campeonatoArgAId: Int = -1,
    campeonatoArgBId: Int = -1,
    campeonatoArgClausuraId: Int = -1,
    campeonatoUruAperturaId: Int = -1,
    campeonatoUruIntermedId: Int = -1,
    campeonatoUruClausuraId: Int = -1,
    campeonatoUruBId: Int = -1,
    campeonatoUruBCompetId: Int = -1,
    onVoltar: () -> Unit = {},
    vm: ArtilheirosViewModel = hiltViewModel()
) {
    val artilheiros       by vm.artilheiros.collectAsState()
    val assistentes       by vm.assistentes.collectAsState()
    val artilheirosTotal  by vm.artilheirosAllTime.collectAsState()
    val assistentesTotal  by vm.assistentesAllTime.collectAsState()
    val artilheirosHist   by vm.artilheirosHistorico.collectAsState()
    val assistentesHist   by vm.assistentesHistorico.collectAsState()
    val divisaoSelecionada          by vm.divisaoSelecionada.collectAsState()
    val divisaoHistoricoSelecionada by vm.divisaoHistoricoSelecionada.collectAsState()

    val temArgentina = campeonatoArgAId > 0 || campeonatoArgBId > 0 || campeonatoArgClausuraId > 0
    val temUruguai   = campeonatoUruAperturaId > 0 || campeonatoUruBId > 0

    LaunchedEffect(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, copaId, copaArgId,
                   campeonatoArgAId, campeonatoArgBId, campeonatoArgClausuraId,
                   campeonatoUruAperturaId, campeonatoUruIntermedId, campeonatoUruClausuraId,
                   campeonatoUruBId, campeonatoUruBCompetId) {
        vm.carregar(
            campAId           = campeonatoAId,
            campBId           = campeonatoBId,
            campCId           = campeonatoCId,
            campDId           = campeonatoDId,
            copaId            = copaId,
            copaArgId         = copaArgId,
            campArgAId        = campeonatoArgAId,
            campArgBId        = campeonatoArgBId,
            campArgClausuraId = campeonatoArgClausuraId,
            campUruApertId    = campeonatoUruAperturaId,
            campUruInterId    = campeonatoUruIntermedId,
            campUruClausId    = campeonatoUruClausuraId,
            campUruBId        = campeonatoUruBId,
            campUruBCompetId  = campeonatoUruBCompetId,
            divisaoInicial    = 0
        )
    }

    // Escopo: 0 = temporada atual, 1 = historico total
    var escopoSelecionado by remember { mutableIntStateOf(0) }
    // Aba: 0 = artilharia, 1 = assistencias
    var abaSelecionada by remember { mutableIntStateOf(0) }
    val abas = listOf("Artilharia", "Assistencias")

    // Opcoes por pais
    val opcoesBrasil = buildList<Pair<Int, String>> {
        add(100 to "Todas")
        add(1 to "Serie A"); add(2 to "Serie B")
        if (campeonatoCId > 0) add(3 to "Serie C")
        if (campeonatoDId > 0) add(4 to "Serie D")
        if (copaId > 0) add(5 to "Copa do Brasil")
    }
    val opcoesArgentina = buildList<Pair<Int, String>> {
        add(200 to "Todas")
        if (campeonatoArgAId > 0 || campeonatoArgClausuraId > 0) add(6 to "Primera (Combinado)")
        if (campeonatoArgAId > 0) add(8 to "Apertura")
        if (campeonatoArgClausuraId > 0) add(9 to "Clausura")
        if (campeonatoArgBId > 0) add(7 to "Segunda Div.")
        if (copaArgId > 0) add(10 to "Copa Argentina")
    }
    val opcoesUruguai = buildList<Pair<Int, String>> {
        add(300 to "Todas")
        val temPrimera = campeonatoUruAperturaId > 0 || campeonatoUruClausuraId > 0
        if (temPrimera) add(25 to "Primera (Combinado)")
        if (campeonatoUruAperturaId > 0) add(20 to "Apertura")
        if (campeonatoUruIntermedId > 0) add(21 to "Intermedio")
        if (campeonatoUruClausuraId > 0) add(22 to "Clausura")
        val temSegunda = campeonatoUruBId > 0 || campeonatoUruBCompetId > 0
        if (temSegunda) add(26 to "Segunda (Combinado)")
        if (campeonatoUruBId > 0) add(23 to "Segunda Div.")
        if (campeonatoUruBCompetId > 0) add(24 to "Competencia")
    }

    val paisDaDivisao = remember(divisaoSelecionada) {
        when (divisaoSelecionada) {
            in 6..10, 200 -> "Argentina"
            in 20..26, 300 -> "Uruguai"
            else -> "Brasil"
        }
    }
    var paisSelecionado by remember(divisaoSelecionada) { mutableStateOf(paisDaDivisao) }

    val opcoesDaDiv = when (paisSelecionado) {
        "Argentina" -> opcoesArgentina
        "Uruguai"   -> opcoesUruguai
        else        -> opcoesBrasil
    }

    fun divisaoDoFiltroPais(pais: String) = when (pais) {
        "Argentina" -> 200; "Uruguai" -> 300; else -> 100
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artilharia & Assistencias") },
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
            // Seletor de escopo (Temporada / Historico)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Temporada atual", "Historico total").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = escopoSelecionado == idx,
                        onClick  = { escopoSelecionado = idx },
                        label    = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Chips de pais
            val paises = buildList {
                add("Todos"); add("Brasil")
                if (temArgentina) add("Argentina")
                if (temUruguai) add("Uruguai")
            }
            if (paises.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    paises.forEach { pais ->
                        val divAtualScope = if (escopoSelecionado == 0) divisaoSelecionada else divisaoHistoricoSelecionada
                        FilterChip(
                            selected = if (pais == "Todos") divAtualScope == 0
                                       else paisSelecionado == pais && divAtualScope != 0,
                            onClick = {
                                if (pais == "Todos") {
                                    paisSelecionado = "Brasil"
                                    if (escopoSelecionado == 0) vm.selecionarDivisao(0)
                                    else vm.selecionarDivisaoHistorico(0)
                                } else {
                                    paisSelecionado = pais
                                    val divPais = divisaoDoFiltroPais(pais)
                                    if (escopoSelecionado == 0) vm.selecionarDivisao(divPais)
                                    else vm.selecionarDivisaoHistorico(divPais)
                                }
                            },
                            label = { Text(pais, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Dropdown de competicao (so quando pais especifico selecionado)
            val divAtualGlobal = if (escopoSelecionado == 0) divisaoSelecionada else divisaoHistoricoSelecionada
            if (divAtualGlobal != 0) {
                val labelComp = opcoesDaDiv.firstOrNull { it.first == divAtualGlobal }?.second
                    ?: opcoesDaDiv.firstOrNull()?.second ?: ""
                var expandidoComp by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandidoComp,
                    onExpandedChange = { expandidoComp = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    OutlinedTextField(
                        value = labelComp,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Competicao") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoComp) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandidoComp,
                        onDismissRequest = { expandidoComp = false }
                    ) {
                        opcoesDaDiv.forEach { (div, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    if (escopoSelecionado == 0) vm.selecionarDivisao(div)
                                    else vm.selecionarDivisaoHistorico(div)
                                    expandidoComp = false
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
                escopoSelecionado == 1 && abaSelecionada == 0 ->
                    if (divisaoHistoricoSelecionada in listOf(0, 100, 200, 300)) artilheirosTotal else artilheirosHist
                else ->
                    if (divisaoHistoricoSelecionada in listOf(0, 100, 200, 300)) assistentesTotal else assistentesHist
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#",       modifier = Modifier.width(28.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Jogador", modifier = Modifier.weight(1f),   fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Clube",   modifier = Modifier.weight(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(colTitulo, modifier = Modifier.width(32.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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