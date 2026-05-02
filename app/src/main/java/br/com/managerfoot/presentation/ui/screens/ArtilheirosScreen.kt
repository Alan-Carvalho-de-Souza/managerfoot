package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import br.com.managerfoot.data.dao.ArtilheiroDto
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.FilterChipPill
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.TabRowPill
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.ArtilheirosViewModel

// ─────────────────────────────────────────────────────────────
//  ArtilheirosScreen — Tactical Dark
//  Tabela de artilheiros e assistentes (Brasil/Argentina/Uruguai)
// ─────────────────────────────────────────────────────────────
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

    LaunchedEffect(
        campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, copaId, copaArgId,
        campeonatoArgAId, campeonatoArgBId, campeonatoArgClausuraId,
        campeonatoUruAperturaId, campeonatoUruIntermedId, campeonatoUruClausuraId,
        campeonatoUruBId, campeonatoUruBCompetId
    ) {
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

    var escopoSelecionado by remember { mutableIntStateOf(0) }
    var abaSelecionada by remember { mutableIntStateOf(0) }
    val abas = listOf("Artilharia", "Assistências")

    val opcoesBrasil = buildList<Pair<Int, String>> {
        add(100 to "Todas")
        add(1 to "Série A"); add(2 to "Série B")
        if (campeonatoCId > 0) add(3 to "Série C")
        if (campeonatoDId > 0) add(4 to "Série D")
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
        if (campeonatoUruIntermedId > 0) add(21 to "Intermédio")
        if (campeonatoUruClausuraId > 0) add(22 to "Clausura")
        val temSegunda = campeonatoUruBId > 0 || campeonatoUruBCompetId > 0
        if (temSegunda) add(26 to "Segunda (Combinado)")
        if (campeonatoUruBId > 0) add(23 to "Segunda Div.")
        if (campeonatoUruBCompetId > 0) add(24 to "Competência")
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Artilharia & Assistências",
            subtitulo = if (escopoSelecionado == 0) "Temporada atual" else "Histórico total",
            onVoltar = onVoltar
        )

        // Escopo: Temporada / Histórico
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            listOf("Temporada atual", "Histórico total").forEachIndexed { idx, label ->
                Box(modifier = Modifier.weight(1f)) {
                    FilterChipPill(
                        label = label,
                        selected = escopoSelecionado == idx,
                        onClick = { escopoSelecionado = idx },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Chips de país
        val paises = buildList {
            add("Todos"); add("Brasil")
            if (temArgentina) add("Argentina")
            if (temUruguai) add("Uruguai")
        }
        if (paises.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                paises.forEach { pais ->
                    val divAtualScope = if (escopoSelecionado == 0) divisaoSelecionada
                                        else divisaoHistoricoSelecionada
                    val sel = if (pais == "Todos") divAtualScope == 0
                              else paisSelecionado == pais && divAtualScope != 0
                    FilterChipPill(
                        label = pais,
                        selected = sel,
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
                        }
                    )
                }
            }
        }

        // Dropdown de competição (só quando país específico)
        val divAtualGlobal = if (escopoSelecionado == 0) divisaoSelecionada
                             else divisaoHistoricoSelecionada
        if (divAtualGlobal != 0) {
            val labelComp = opcoesDaDiv.firstOrNull { it.first == divAtualGlobal }?.second
                ?: opcoesDaDiv.firstOrNull()?.second ?: ""
            var expandidoComp by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandidoComp,
                onExpandedChange = { expandidoComp = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = 2.dp)
            ) {
                OutlinedTextField(
                    value = labelComp,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Competição", fontWeight = FontWeight.SemiBold) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoComp) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenElectric,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedLabelColor = GreenElectric
                    )
                )
                ExposedDropdownMenu(
                    expanded = expandidoComp,
                    onDismissRequest = { expandidoComp = false }
                ) {
                    opcoesDaDiv.forEach { (div, label) ->
                        val sel = div == divAtualGlobal
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) GreenElectric else MaterialTheme.colorScheme.onSurface
                                )
                            },
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

        // Tabs Artilharia / Assistências
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.Center
        ) {
            TabRowPill(
                abas = abas,
                selecionada = abaSelecionada,
                onSelecionar = { abaSelecionada = it }
            )
        }

        val lista = when {
            escopoSelecionado == 0 && abaSelecionada == 0 -> artilheiros
            escopoSelecionado == 0 && abaSelecionada == 1 -> assistentes
            escopoSelecionado == 1 && abaSelecionada == 0 ->
                if (divisaoHistoricoSelecionada in listOf(0, 100, 200, 300)) artilheirosTotal
                else artilheirosHist
            else ->
                if (divisaoHistoricoSelecionada in listOf(0, 100, 200, 300)) assistentesTotal
                else assistentesHist
        }
        val colTitulo = if (abaSelecionada == 0) "G" else "A"

        if (lista.isEmpty()) {
            EmptyState("Nenhum evento registrado ainda")
        } else {
            // Header Tactical Dark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "#",
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "JOGADOR",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "CLUBE",
                    modifier = Modifier.weight(0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    colTitulo,
                    modifier = Modifier.width(32.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            LazyColumn {
                itemsIndexed(lista) { index, item ->
                    ArtilheiroRow(
                        posicao = index + 1,
                        item = item,
                        colTitulo = colTitulo,
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
}

// ─────────────────────────────────────────────────────────────
//  Linha de um artilheiro/assistente (com pódio top 3)
// ─────────────────────────────────────────────────────────────
@Composable
private fun ArtilheiroRow(
    posicao: Int,
    item: ArtilheiroDto,
    colTitulo: String,
    isOdd: Boolean = false
) {
    val (medalha, corPos) = when (posicao) {
        1 -> "🥇" to GoldChampion
        2 -> "🥈" to SilverRunnerUp
        3 -> "🥉" to BronzePlace
        else -> "" to MaterialTheme.colorScheme.onSurface
    }
    val rowBg = if (isOdd) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Posição com medalha para top 3
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (posicao <= 3) {
                Text(medalha, fontSize = 16.sp)
            } else {
                Text(
                    "$posicao",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(
            item.nomeAbrev,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (posicao <= 3) FontWeight.Bold else FontWeight.Normal,
            color = if (posicao <= 3) corPos else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.weight(0.7f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamBadge(nome = item.nomeTime, escudoRes = item.escudoRes, size = 18.dp)
            Spacer(Modifier.width(Spacing.xs))
            Text(
                item.nomeTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = RoundedCornerShape(Radius.sm),
            color = if (posicao <= 3) corPos.copy(alpha = 0.18f)
                    else GreenMid,
            border = BorderStroke(
                0.5.dp,
                if (posicao <= 3) corPos.copy(alpha = 0.5f)
                else GreenElectric.copy(alpha = 0.4f)
            ),
            modifier = Modifier.width(36.dp)
        ) {
            Text(
                "${item.total}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (posicao <= 3) corPos else GreenElectric,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        }
    }
}
