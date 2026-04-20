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
    campeonatoArgAId: Int = -1,
    campeonatoArgBId: Int = -1,
    campeonatoArgClausuraId: Int = -1,
    campeonatoUruAperturaId: Int = -1,
    campeonatoUruBId: Int = -1,
    campeonatoUruClausuraId: Int = -1,
    campeonatoUruIntermedId: Int = -1,
    campeonatoUruBCompetId: Int = -1,
    copaId: Int = -1,
    timeJogadorId: Int,
    onVoltar: () -> Unit = {},
    vm: TabelaViewModel = hiltViewModel()
) {
    val tabela by vm.tabela.collectAsState()
    val times by vm.times.collectAsState()
    val divisaoSelecionada by vm.divisaoSelecionada.collectAsState()
    val copaChampeaoTimeId by vm.copaChampeaoTimeId.collectAsState()

    // Posição (1-indexed) do campeão da Copa na tabela da Série A, -1 se não aplicável
    val copaChampeaoPosSerieA = remember(tabela, copaChampeaoTimeId, divisaoSelecionada) {
        if (divisaoSelecionada == 1 && copaChampeaoTimeId > 0)
            tabela.indexOfFirst { it.timeId == copaChampeaoTimeId }.let { if (it >= 0) it + 1 else -1 }
        else -1
    }

    LaunchedEffect(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, campeonatoArgAId, campeonatoArgBId, campeonatoArgClausuraId, campeonatoUruAperturaId, campeonatoUruBId, campeonatoUruClausuraId, campeonatoUruIntermedId, campeonatoUruBCompetId, copaId) {
        vm.carregar(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, campeonatoArgAId, campeonatoArgBId, campArgClausuraId = campeonatoArgClausuraId, campUruAperturaId = campeonatoUruAperturaId, campUruBId = campeonatoUruBId, campUruClausuraId = campeonatoUruClausuraId, campUruIntermedId = campeonatoUruIntermedId, campUruBCompetId = campeonatoUruBCompetId, copaIdBrasil = copaId, timeJogadorId = timeJogadorId)
    }

    // Opções por país
    val opcoesBrasil = buildList {
        add(1 to "Série A")
        add(2 to "Série B")
        if (campeonatoCId > 0) add(3 to "Série C")
        if (campeonatoDId > 0) add(4 to "Série D")
    }
    val opcoesArgentina = buildList {
        if (campeonatoArgAId > 0) add(5 to "Apertura")
        if (campeonatoArgClausuraId > 0) add(7 to "Clausura")
        if (campeonatoArgAId > 0 && campeonatoArgClausuraId > 0) add(8 to "Geral")
        if (campeonatoArgBId > 0) add(6 to "Segunda Div.")
    }
    val opcoesUruguai = buildList {
        if (campeonatoUruAperturaId > 0) add(9 to "Apertura")
        if (campeonatoUruBId > 0) add(10 to "Segunda Div.")
        if (campeonatoUruIntermedId > 0) add(11 to "Intermédio")
        if (campeonatoUruClausuraId > 0) add(12 to "Clausura")
        if (campeonatoUruAperturaId > 0 && campeonatoUruClausuraId > 0) add(13 to "Geral")
        if (campeonatoUruBCompetId > 0) add(14 to "Competencia")
    }

    val paises = buildList {
        if (opcoesBrasil.isNotEmpty()) add("Brasil")
        if (opcoesArgentina.isNotEmpty()) add("Argentina")
        if (opcoesUruguai.isNotEmpty()) add("Uruguai")
    }

    fun bandeiraPais(pais: String) = when (pais) {
        "Brasil"    -> "\uD83C\uDDE7\uD83C\uDDF7"
        "Argentina" -> "\uD83C\uDDE6\uD83C\uDDF7"
        "Uruguai"   -> "\uD83C\uDDFA\uD83C\uDDFE"
        else -> ""
    }

    val paisesOrdenados = paises.sortedBy { it }

    // País selecionado: derivado da divisão atual
    val paisSelecionado = remember(divisaoSelecionada) {
        if (divisaoSelecionada in 5..8) "Argentina"
        else if (divisaoSelecionada in 9..14) "Uruguai"
        else "Brasil"
    }

    val opcoesDivisaoPais = when (paisSelecionado) {
        "Argentina" -> opcoesArgentina
        "Uruguai" -> opcoesUruguai
        else -> opcoesBrasil
    }

    val labelSelecionado = opcoesDivisaoPais.firstOrNull { it.first == divisaoSelecionada }?.second
        ?: opcoesDivisaoPais.firstOrNull()?.second ?: ""
    var expandidoPais by remember { mutableStateOf(false) }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Filtro de país (dropdown — só exibe se houver mais de um)
                if (paises.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = expandidoPais,
                        onExpandedChange = { expandidoPais = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = "${bandeiraPais(paisSelecionado)} $paisSelecionado",
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
                            paisesOrdenados.forEach { pais ->
                                DropdownMenuItem(
                                    text = { Text("${bandeiraPais(pais)} $pais") },
                                    onClick = {
                                        val primeiraDivisao = when (pais) {
                                        "Argentina" -> opcoesArgentina.firstOrNull()?.first ?: 5
                                        "Uruguai" -> opcoesUruguai.firstOrNull()?.first ?: 9
                                        else -> opcoesBrasil.firstOrNull()?.first ?: 1
                                    }
                                        vm.selecionarDivisao(primeiraDivisao)
                                        expandidoPais = false
                                        expandido = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Seletor de divisão (dropdown — só exibe se houver mais de uma divisão no país)
                if (opcoesDivisaoPais.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = expandido,
                        onExpandedChange = { expandido = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = labelSelecionado,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Divisão") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandido,
                            onDismissRequest = { expandido = false }
                        ) {
                            opcoesDivisaoPais.forEach { (div, label) ->
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
            }

            val ehGrupos = divisaoSelecionada == 5 || divisaoSelecionada == 7 || divisaoSelecionada == 14
            val gruposArgentina = remember(tabela, ehGrupos) {
                if (ehGrupos) tabela.groupBy { it.grupo ?: "?" }.entries.sortedBy { it.key }
                else emptyList()
            }

            // Header row (só para visualização plana)
            if (!ehGrupos) {
                TabelaHeader()
                HorizontalDivider()
            }

            LazyColumn {
                if (ehGrupos) {
                    gruposArgentina.forEach { (grupo, grupoTabela) ->
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Zona $grupo",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            TabelaHeader()
                            HorizontalDivider()
                        }
                        itemsIndexed(grupoTabela) { index, item ->
                            val time = times.find { it.id == item.timeId }
                            val nomeTime = time?.nome ?: "Time ${item.timeId}"
                            val escudoRes = time?.escudoRes ?: ""
                            val ehJogador = item.timeId == timeJogadorId
                            val classificadosPorGrupo = if (divisaoSelecionada == 14) 1 else 8
                            val zona = if (index < classificadosPorGrupo) Color(0xFF1565C0) else null
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
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                } else {
                    itemsIndexed(tabela) { index, item ->
                        val time = times.find { it.id == item.timeId }
                        val nomeTime = time?.nome ?: "Time ${item.timeId}"
                        val escudoRes = time?.escudoRes ?: ""
                        val ehJogador = item.timeId == timeJogadorId
                        val zona = zonaParaDivisao(index + 1, divisaoSelecionada, copaChampeaoPosSerieA)
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
            }

            // Legenda de zonas
            LegendaZonas(divisaoSelecionada, copaChampeaoPosSerieA)
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
// copaChampeaoPosSerieA: posição (1-indexed) do campeão da Copa do Brasil na Série A,
//   -1 se Copa não encerrada ou não aplicável.
@Composable
private fun zonaParaDivisao(posicao: Int, divisao: Int, copaChampeaoPosSerieA: Int = -1): Color? = when (divisao) {
    1 -> when {
        // Copa campeão já está no top-6 → 7º também entra para Libertadores
        copaChampeaoPosSerieA in 1..6 && posicao == 7 -> Color(0xFF1565C0) // Libertadores (vaga Copa)
        posicao <= 4  -> Color(0xFF1565C0)  // Libertadores
        posicao <= 6  -> Color(0xFF4CAF50)  // Sul-Americana
        // Copa campeão fora do top-6 → sua posição fica azul (vaga Copa)
        copaChampeaoPosSerieA >= 7 && posicao == copaChampeaoPosSerieA -> Color(0xFF1565C0) // Libertadores (vaga Copa)
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
    5, 7 -> when {
        posicao <= 8  -> Color(0xFF1565C0)  // Oitavas de Final
        else          -> null
    }
    8 -> when {
        posicao == 1  -> Color(0xFFFFD700)  // Campeão Geral
        else          -> null
    }
    10 -> when {
        posicao <= 2  -> Color(0xFF1565C0)  // Acesso direto à Primera
        posicao <= 6  -> Color(0xFFFF6F00)  // Playoff de acesso
        posicao >= 13 -> Color(0xFFE53935)  // Rebaixamento
        else          -> null
    }
    else -> when {
        posicao <= 4  -> Color(0xFF1565C0)  // Acesso à Série C
        else          -> null
    }
}

@Composable
private fun LegendaZonas(divisao: Int, copaChampeaoPosSerieA: Int = -1) {
    val itens = when (divisao) {
        1 -> buildList {
            if (copaChampeaoPosSerieA in 1..6) {
                add(Color(0xFF1565C0) to "1-7: Libertadores (incl. Copa)")
            } else {
                add(Color(0xFF1565C0) to "1-4: Libertadores")
            }
            add(Color(0xFF4CAF50) to "5-6: Sul-Americana")
            if (copaChampeaoPosSerieA >= 7) {
                add(Color(0xFF1565C0) to "${copaChampeaoPosSerieA}º: Libertadores (Copa)")
            }
            add(Color(0xFFE53935) to "17-20: Rebaixamento")
        }
        2 -> listOf(
            Color(0xFF1565C0) to "1-4: Acesso à Série A",
            Color(0xFFE53935) to "17-20: Rebaixamento"
        )
        3 -> listOf(
            Color(0xFF1565C0) to "1-4: Acesso à Série B",
            Color(0xFFE53935) to "17-20: Rebaixamento"
        )
        5, 7 -> listOf(
            Color(0xFF1565C0) to "1-8: Oitavas de Final"
        )
        8 -> listOf(
            Color(0xFFFFD700) to "1º: Campeão Geral"
        )
        10 -> listOf(
            Color(0xFF1565C0) to "1-2: Acesso direto",
            Color(0xFFFF6F00) to "3-6: Playoff",
            Color(0xFFE53935) to "13-14: Rebaixamento"
        )
        14 -> listOf(
            Color(0xFF1565C0) to "1º: Classifica para a Final"
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
