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
import br.com.managerfoot.presentation.viewmodel.RodadaViewModel

//  — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — —
//  RodadaScreen
//  Consulta de jogos por rodada  — Séries A/B/C/D + Apertura/Clausura
//  — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — —
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RodadaScreen(
    campeonatoAId: Int,
    campeonatoBId: Int,
    campeonatoCId: Int = -1,
    campeonatoDId: Int = -1,
    campeonatoArgAId: Int = -1,
    campeonatoArgBId: Int = -1,
    campeonatoArgClausuraId: Int = -1,
    campeonatoUruAperturaId: Int = -1,
    campeonatoUruIntermedId: Int = -1,
    campeonatoUruClausuraId: Int = -1,
    campeonatoUruBId: Int = -1,
    campeonatoUruBCompetId: Int = -1,
    onVoltar: () -> Unit = {},
    vm: RodadaViewModel = hiltViewModel()
) {
    val partidas           by vm.partidas.collectAsState()
    val knockoutPartidas   by vm.knockoutPartidas.collectAsState()
    val divisaoSelecionada by vm.divisaoSelecionada.collectAsState()
    val rodadaSelecionada  by vm.rodadaSelecionada.collectAsState()
    val maxRodada          by vm.maxRodada.collectAsState()

    val esMataMataDivisao = divisaoSelecionada == 8 || divisaoSelecionada == 9

    LaunchedEffect(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, campeonatoArgAId, campeonatoArgBId, campeonatoArgClausuraId,
                   campeonatoUruAperturaId, campeonatoUruIntermedId, campeonatoUruClausuraId, campeonatoUruBId, campeonatoUruBCompetId) {
        vm.carregar(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, campeonatoArgAId, campeonatoArgBId, campArgClausuraId = campeonatoArgClausuraId,
                    campUruApertId = campeonatoUruAperturaId, campUruIntermedId = campeonatoUruIntermedId, campUruClausId = campeonatoUruClausuraId,
                    campUruBId = campeonatoUruBId, campUruBCompetId = campeonatoUruBCompetId)
    }

    // Agrupa as fases do mata-mata disponíveis
    val fasesDisponiveisMM = remember(knockoutPartidas) {
        vm.ARG_FASES_ORDER.filter { fase -> knockoutPartidas.any { it.fase == fase } }
    }
    var faseSelecionadaMM by remember(fasesDisponiveisMM) {
        mutableStateOf(fasesDisponiveisMM.firstOrNull() ?: "")
    }

    //  — — Opções por país  — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — —
    val opcoesBrasil = buildList {
        add(1 to "Série A")
        add(2 to "Série B")
        if (campeonatoCId > 0) add(3 to "Série C")
        if (campeonatoDId > 0) add(4 to "Série D")
    }
    val opcoesArgentina = buildList {
        if (campeonatoArgAId > 0) {
            add(5 to "Apertura — Grupos")
            add(8 to "Apertura — Mata-Mata")
        }
        if (campeonatoArgBId > 0) add(6 to "Segunda Div.")
        if (campeonatoArgClausuraId > 0) {
            add(7 to "Clausura — Grupos")
            add(9 to "Clausura — Mata-Mata")
        }
    }
    val opcoesUruguai = buildList {
        if (campeonatoUruAperturaId > 0) add(10 to "Apertura")
        if (campeonatoUruIntermedId > 0) add(11 to "Intermediário")
        if (campeonatoUruClausuraId > 0) add(12 to "Clausura")
        if (campeonatoUruBId > 0) add(13 to "Segunda Div.")
        if (campeonatoUruBCompetId > 0) add(14 to "Competência")
    }
    val temArgentina = opcoesArgentina.isNotEmpty()
    val temUruguai   = opcoesUruguai.isNotEmpty()
    val temOutrosPaises = temArgentina || temUruguai

    // País selecionado — sincroniza com a divisão atual
    var paisSelecionado by remember {
        mutableStateOf(
            when {
                divisaoSelecionada in 5..9  -> "Argentina"
                divisaoSelecionada in 10..14 -> "Uruguai"
                else -> "Brasil"
            }
        )
    }
    LaunchedEffect(divisaoSelecionada) {
        paisSelecionado = when {
            divisaoSelecionada in 5..9   -> "Argentina"
            divisaoSelecionada in 10..14 -> "Uruguai"
            else -> "Brasil"
        }
    }

    val opcoesDivisaoAtual = when (paisSelecionado) {
        "Argentina" -> opcoesArgentina
        "Uruguai"   -> opcoesUruguai
        else        -> opcoesBrasil
    }

    var expandidoPais  by remember { mutableStateOf(false) }
    var expandidoSerie by remember { mutableStateOf(false) }
    val labelSerie = opcoesDivisaoAtual.firstOrNull { it.first == divisaoSelecionada }?.second
        ?: opcoesDivisaoAtual.firstOrNull()?.second ?: "Série A"

    //  — — Dropdown Rodada  — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — —
    val opcoesRodada = (1..maxRodada).toList()
    var expandidoRodada by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rodadas") },
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
            //  — — Filtro de país  — — — — — — — — — — — — — — — — — — — — — — — — —
            if (temOutrosPaises) {
                ExposedDropdownMenuBox(
                    expanded = expandidoPais,
                    onExpandedChange = { expandidoPais = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = paisSelecionado,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("País") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoPais) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandidoPais,
                        onDismissRequest = { expandidoPais = false }
                    ) {
                        val paises = buildList {
                            add("Brasil")
                            if (temArgentina) add("Argentina")
                            if (temUruguai)   add("Uruguai")
                        }
                        paises.forEach { pais ->
                            DropdownMenuItem(
                                text = { Text(pais) },
                                onClick = {
                                    paisSelecionado = pais
                                    expandidoPais = false
                                    // seleciona automaticamente a primeira competição do país
                                    val primeiraOpcao = when (pais) {
                                        "Argentina" -> opcoesArgentina
                                        "Uruguai"   -> opcoesUruguai
                                        else        -> opcoesBrasil
                                    }
                                    primeiraOpcao.firstOrNull()?.let { vm.selecionarDivisao(it.first) }
                                }
                            )
                        }
                    }
                }
            }

            //  — — Filtro de competição  — — — — — — — — — — — — — — — — — — — — — — —
            ExposedDropdownMenuBox(
                expanded = expandidoSerie,
                onExpandedChange = { expandidoSerie = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (temOutrosPaises) 4.dp else 8.dp)
            ) {
                OutlinedTextField(
                    value = labelSerie,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Competição") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoSerie) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandidoSerie,
                    onDismissRequest = { expandidoSerie = false }
                ) {
                    opcoesDivisaoAtual.forEach { (div, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                vm.selecionarDivisao(div)
                                expandidoSerie = false
                            }
                        )
                    }
                }
            }

            if (!esMataMataDivisao) {
                //  — — Filtro de Rodada (somente para grupos)  — —
                ExposedDropdownMenuBox(
                    expanded = expandidoRodada,
                    onExpandedChange = { expandidoRodada = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = "Rodada $rodadaSelecionada",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rodada") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoRodada) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandidoRodada,
                        onDismissRequest = { expandidoRodada = false }
                    ) {
                        opcoesRodada.forEach { rodada ->
                            DropdownMenuItem(
                                text = { Text("Rodada $rodada") },
                                onClick = {
                                    vm.selecionarRodada(rodada)
                                    expandidoRodada = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            if (esMataMataDivisao) {
                //  — — Chaveamento do mata-mata argentino  — — — — — — —
                if (fasesDisponiveisMM.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Mata-mata ainda não iniciado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Abas de fase
                    ScrollableTabRow(
                        selectedTabIndex = fasesDisponiveisMM.indexOf(faseSelecionadaMM).coerceAtLeast(0),
                        edgePadding = 0.dp
                    ) {
                        fasesDisponiveisMM.forEachIndexed { idx, fase ->
                            Tab(
                                selected = faseSelecionadaMM == fase,
                                onClick  = { faseSelecionadaMM = fase },
                                text     = { Text(fase) }
                            )
                        }
                    }

                    val jogosDoFase = remember(knockoutPartidas, faseSelecionadaMM) {
                        knockoutPartidas.filter { it.fase == faseSelecionadaMM }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(jogosDoFase, key = { it.partidaId }) { partida ->
                            RodadaKnockoutCard(partida = partida)
                        }
                    }
                }
            } else {
                //  — — Lista de partidas dos grupos  — — — — — — — — — — — — —
                if (partidas.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma partida encontrada.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(partidas, key = { it.partidaId }) { partida ->
                            RodadaCard(partida = partida)
                        }
                    }
                }
            }
        }
    }
}

//  — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — —
//  RodadaKnockoutCard   — jogo único de mata-mata
//  — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — —
@Composable
private fun RodadaKnockoutCard(partida: CalendarioPartidaDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (partida.jogada)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (!partida.jogada) CardDefaults.outlinedCardBorder() else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamBadge(nome = partida.nomeCasa, escudoRes = partida.escudoCasa, size = 44.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(partida.nomeCasa, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2)
                }
                Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (partida.jogada && partida.golsCasa != null && partida.golsFora != null) {
                        Text("${partida.golsCasa} × ${partida.golsFora}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (partida.penaltisCasa != null && partida.penaltisForaId != null && partida.golsCasa == partida.golsFora) {
                            Text("pen ${partida.penaltisCasa}–${partida.penaltisForaId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("jogo único", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("vs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamBadge(nome = partida.nomeFora, escudoRes = partida.escudoFora, size = 44.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(partida.nomeFora, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

//  — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — —
//  RodadaCard
//  — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — — —
@Composable
private fun RodadaCard(partida: CalendarioPartidaDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (partida.jogada)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (!partida.jogada)
            CardDefaults.outlinedCardBorder()
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    size = 44.dp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = partida.nomeCasa,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }

            // Centro: placar ou "vs"
            Box(
                modifier = Modifier.width(80.dp),
                contentAlignment = Alignment.Center
            ) {
                if (partida.jogada && partida.golsCasa != null && partida.golsFora != null) {
                    Text(
                        text = "${partida.golsCasa} × ${partida.golsFora}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "vs",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = partida.nomeCampeonato.take(14),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
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
                    size = 44.dp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = partida.nomeFora,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}
