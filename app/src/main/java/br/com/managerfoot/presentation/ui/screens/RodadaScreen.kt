package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.dao.CalendarioPartidaDto
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.RodadaViewModel

// ─────────────────────────────────────────────────────────────
//  RodadaScreen — Tactical Dark
//  Consulta de jogos por rodada (Brasil A/B/C/D + Argentina/Uruguai)
// ─────────────────────────────────────────────────────────────
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

    LaunchedEffect(
        campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, campeonatoArgAId, campeonatoArgBId, campeonatoArgClausuraId,
        campeonatoUruAperturaId, campeonatoUruIntermedId, campeonatoUruClausuraId, campeonatoUruBId, campeonatoUruBCompetId
    ) {
        vm.carregar(
            campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, campeonatoArgAId, campeonatoArgBId,
            campArgClausuraId = campeonatoArgClausuraId,
            campUruApertId = campeonatoUruAperturaId, campUruIntermedId = campeonatoUruIntermedId, campUruClausId = campeonatoUruClausuraId,
            campUruBId = campeonatoUruBId, campUruBCompetId = campeonatoUruBCompetId
        )
    }

    // Fases do mata-mata disponíveis
    val fasesDisponiveisMM = remember(knockoutPartidas) {
        vm.ARG_FASES_ORDER.filter { fase -> knockoutPartidas.any { it.fase == fase } }
    }
    var faseSelecionadaMM by remember(fasesDisponiveisMM) {
        mutableStateOf(fasesDisponiveisMM.firstOrNull() ?: "")
    }

    // Opções por país
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

    var paisSelecionado by remember {
        mutableStateOf(
            when {
                divisaoSelecionada in 5..9   -> "Argentina"
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

    var expandidoPais   by remember { mutableStateOf(false) }
    var expandidoSerie  by remember { mutableStateOf(false) }
    var expandidoRodada by remember { mutableStateOf(false) }

    val labelSerie = opcoesDivisaoAtual.firstOrNull { it.first == divisaoSelecionada }?.second
        ?: opcoesDivisaoAtual.firstOrNull()?.second ?: "Série A"
    val opcoesRodada = (1..maxRodada).toList()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Rodadas",
            subtitulo = paisSelecionado,
            onVoltar = onVoltar
        )

        // ── Filtros (cards agrupados) ──────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (temOutrosPaises) {
                FiltroDropdown(
                    label = "País",
                    valor = paisSelecionado,
                    expanded = expandidoPais,
                    onExpand = { expandidoPais = it },
                    options = buildList {
                        add("Brasil")
                        if (temArgentina) add("Argentina")
                        if (temUruguai)   add("Uruguai")
                    },
                    onSelect = { pais ->
                        paisSelecionado = pais
                        expandidoPais = false
                        val primeiraOpcao = when (pais) {
                            "Argentina" -> opcoesArgentina
                            "Uruguai"   -> opcoesUruguai
                            else        -> opcoesBrasil
                        }
                        primeiraOpcao.firstOrNull()?.let { vm.selecionarDivisao(it.first) }
                    }
                )
            }

            FiltroDropdown(
                label = "Competição",
                valor = labelSerie,
                expanded = expandidoSerie,
                onExpand = { expandidoSerie = it },
                options = opcoesDivisaoAtual.map { it.second },
                onSelect = { selectedLabel ->
                    val match = opcoesDivisaoAtual.firstOrNull { it.second == selectedLabel }
                    match?.let { vm.selecionarDivisao(it.first) }
                    expandidoSerie = false
                }
            )

            if (!esMataMataDivisao) {
                FiltroDropdown(
                    label = "Rodada",
                    valor = "Rodada $rodadaSelecionada",
                    expanded = expandidoRodada,
                    onExpand = { expandidoRodada = it },
                    options = opcoesRodada.map { "Rodada $it" },
                    onSelect = { label ->
                        val r = label.removePrefix("Rodada ").toIntOrNull()
                        if (r != null) vm.selecionarRodada(r)
                        expandidoRodada = false
                    }
                )
            }
        }

        // ── Conteúdo ───────────────────────────────────────────
        if (esMataMataDivisao) {
            ConteudoMataMata(
                fasesDisponiveis = fasesDisponiveisMM,
                faseSelecionada = faseSelecionadaMM,
                onSelecionarFase = { faseSelecionadaMM = it },
                knockoutPartidas = knockoutPartidas
            )
        } else {
            if (partidas.isEmpty()) {
                EmptyState("Nenhuma partida encontrada para esta rodada.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(partidas, key = { it.partidaId }) { partida ->
                        RodadaCard(partida = partida)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Dropdown de filtro estilizado (Tactical Dark)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltroDropdown(
    label: String,
    valor: String,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpand,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontWeight = FontWeight.SemiBold) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GreenElectric,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                focusedLabelColor = GreenElectric
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpand(false) }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            opt,
                            fontWeight = if (opt == valor) FontWeight.Bold else FontWeight.Normal,
                            color = if (opt == valor) GreenElectric else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onSelect(opt) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Conteúdo do mata-mata (fases + jogos)
// ─────────────────────────────────────────────────────────────
@Composable
private fun ConteudoMataMata(
    fasesDisponiveis: List<String>,
    faseSelecionada: String,
    onSelecionarFase: (String) -> Unit,
    knockoutPartidas: List<CalendarioPartidaDto>
) {
    if (fasesDisponiveis.isEmpty()) {
        EmptyState("Mata-mata ainda não iniciado.")
        return
    }

    // Abas de fase em formato pill (ScrollableTabRow customizado)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        fasesDisponiveis.forEach { fase ->
            val sel = fase == faseSelecionada
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .clickable(onClick = { onSelecionarFase(fase) }),
                shape = RoundedCornerShape(Radius.pill),
                color = if (sel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    width = 0.5.dp,
                    color = if (sel) GreenElectric
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    fase,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }
        }
    }

    val jogosDoFase = remember(knockoutPartidas, faseSelecionada) {
        knockoutPartidas.filter { it.fase == faseSelecionada }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = Spacing.md,
            vertical = Spacing.sm
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(jogosDoFase, key = { it.partidaId }) { partida ->
            RodadaKnockoutCard(partida = partida)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de jogo único do mata-mata (com pênaltis quando empate)
// ─────────────────────────────────────────────────────────────
@Composable
private fun RodadaKnockoutCard(partida: CalendarioPartidaDto) {
    val borderColor = if (partida.jogada) GreenMid
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            // Tag "JOGO ÚNICO"
            Text(
                "JOGO ÚNICO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeColuna(
                    nome = partida.nomeCasa,
                    escudoRes = partida.escudoCasa,
                    modifier = Modifier.weight(1f)
                )
                Column(
                    Modifier.width(96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (partida.jogada && partida.golsCasa != null && partida.golsFora != null) {
                        Text(
                            "${partida.golsCasa} × ${partida.golsFora}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (partida.penaltisCasa != null && partida.penaltisForaId != null
                            && partida.golsCasa == partida.golsFora) {
                            Text(
                                "pen ${partida.penaltisCasa}–${partida.penaltisForaId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            "vs",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TimeColuna(
                    nome = partida.nomeFora,
                    escudoRes = partida.escudoFora,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de jogo de fase de grupos
// ─────────────────────────────────────────────────────────────
@Composable
private fun RodadaCard(partida: CalendarioPartidaDto) {
    val borderColor = if (partida.jogada) GreenMid
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeColuna(
                nome = partida.nomeCasa,
                escudoRes = partida.escudoCasa,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier.width(80.dp),
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "vs",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            partida.nomeCampeonato.take(14),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            TimeColuna(
                nome = partida.nomeFora,
                escudoRes = partida.escudoFora,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Coluna time (escudo + nome) reutilizada nos cards
// ─────────────────────────────────────────────────────────────
@Composable
private fun TimeColuna(
    nome: String,
    escudoRes: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        TeamBadge(nome = nome, escudoRes = escudoRes, size = 44.dp)
        Text(
            nome,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

