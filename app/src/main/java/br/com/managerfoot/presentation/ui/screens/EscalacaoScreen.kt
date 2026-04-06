package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.database.entities.EstiloJogo
import br.com.managerfoot.data.database.entities.Posicao
import br.com.managerfoot.data.database.entities.Setor
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.domain.model.JogadorNaEscalacao
import br.com.managerfoot.presentation.ui.components.*
import br.com.managerfoot.presentation.viewmodel.EscalacaoViewModel
import br.com.managerfoot.presentation.viewmodel.MercadoViewModel
import androidx.compose.material3.MaterialTheme

// ═══════════════════════════════════════════════════════════
//  EscalacaoScreen
// ═══════════════════════════════════════════════════════════
@Composable
fun EscalacaoScreen(
    timeId: Int,
    modoPreJogo: Boolean = false,
    adversarioId: Int = -1,
    onIniciarPartida: (() -> Unit)? = null,
    vm: EscalacaoViewModel = hiltViewModel()
) {
    val elenco      by vm.elenco.collectAsState()
    val escalacao   by vm.escalacao.collectAsState()
    val selecionado by vm.jogadorSelecionado.collectAsState()
    val adversario  by vm.adversario.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }
    LaunchedEffect(adversarioId) {
        if (adversarioId > 0) vm.carregarAdversario(adversarioId)
    }

    // Em modo pré-jogo, começa na aba Tática (3) para o jogador definir o esquema
    var abaAtiva by remember { mutableIntStateOf(if (modoPreJogo) 3 else 0) }
    val abas = listOf("Titulares", "Reservas", "Elenco", "Tática")
    var titularParaTroca by remember { mutableStateOf<JogadorNaEscalacao?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Banner pré-jogo: mostra escalação tática dos dois times
        if (modoPreJogo) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Pré-Jogo",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Meu time
                        escalacao?.let { esc ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                TeamBadge(nome = esc.time.nome, escudoRes = esc.time.escudoRes, size = 40.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(esc.time.nome, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(esc.time.taticaFormacao, style = MaterialTheme.typography.labelSmall)
                                val estiloLabel = when (esc.time.estiloJogo) {
                                    EstiloJogo.OFENSIVO      -> "Ofensivo"
                                    EstiloJogo.EQUILIBRADO   -> "Equil."
                                    EstiloJogo.DEFENSIVO     -> "Defensivo"
                                    EstiloJogo.CONTRA_ATAQUE -> "C.-Ataque"
                                }
                                Text(estiloLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text("vs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        // Adversário
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (adversario != null) {
                                TeamBadge(nome = adversario!!.nome, escudoRes = adversario!!.escudoRes, size = 40.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(adversario!!.nome, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(adversario!!.taticaFormacao, style = MaterialTheme.typography.labelSmall)
                                val estiloAdv = when (adversario!!.estiloJogo) {
                                    EstiloJogo.OFENSIVO      -> "Ofensivo"
                                    EstiloJogo.EQUILIBRADO   -> "Equil."
                                    EstiloJogo.DEFENSIVO     -> "Defensivo"
                                    EstiloJogo.CONTRA_ATAQUE -> "C.-Ataque"
                                }
                                Text(estiloAdv, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            } else {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }

        // Info da formação atual
        escalacao?.let { esc ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Formação: ${esc.formacaoEfetiva}", fontWeight = FontWeight.Bold)
                    Text("${esc.titulares.size}/11 titulares", style = MaterialTheme.typography.bodySmall)
                }
                val forcaMedia = esc.titulares
                    .map { it.jogador.forcaEfetiva(it.posicaoUsada) }
                    .takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0
                ForcaBadge(forcaMedia)
            }
        }

        TabRow(selectedTabIndex = abaAtiva) {
            abas.forEachIndexed { idx, titulo ->
                Tab(selected = abaAtiva == idx, onClick = { abaAtiva = idx }, text = { Text(titulo) })
            }
        }

        // Banner de troca ativo em todas as abas
        if (titularParaTroca != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Trocar: ${titularParaTroca!!.jogador.nomeAbreviado} (${titularParaTroca!!.posicaoUsada.abreviacao})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { titularParaTroca = null }) { Text("Cancelar") }
            }
        }

        when (abaAtiva) {
            0 -> { // Titulares
                if (escalacao == null) {
                    EmptyState("Carregando escalação...")
                } else {
                    val setorOrder = mapOf(Setor.GOLEIRO to 0, Setor.DEFESA to 1, Setor.MEIO to 2, Setor.ATAQUE to 3)
                    val titularesOrdenados = escalacao!!.titulares.sortedWith(
                        compareBy({ setorOrder[it.posicaoUsada.setor] ?: 2 }, { it.posicaoUsada.ordinal })
                    )
                    Column(Modifier.fillMaxSize()) {
                        LazyColumn {
                            items(titularesOrdenados, key = { it.jogador.id }) { jne ->
                                val eSelecionado = titularParaTroca?.jogador?.id == jne.jogador.id
                                JogadorRow(
                                    jogador = jne.jogador,
                                    onClick = { if (titularParaTroca == null) vm.selecionarJogador(jne.jogador) },
                                    trailing = {
                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 130.dp)) {
                                            Text(jne.posicaoUsada.abreviacao, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        val ehImproviso = jne.posicaoUsada != jne.jogador.posicao &&
                                            jne.posicaoUsada != jne.jogador.posicaoSecundaria &&
                                            jne.posicaoUsada.setor != jne.jogador.posicao.setor
                                        if (ehImproviso) {
                                                Text("*Improvisado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            when {
                                                eSelecionado -> {
                                                    OutlinedButton(
                                                        onClick = { titularParaTroca = null },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) { Text("✕ Cancelar", style = MaterialTheme.typography.labelSmall) }
                                                }
                                                titularParaTroca != null -> {
                                                    Button(
                                                        onClick = {
                                                            vm.trocarPosicoes(titularParaTroca!!, jne)
                                                            titularParaTroca = null
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) { Text("↔ Trocar", style = MaterialTheme.typography.labelSmall) }
                                                }
                                                else -> {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        OutlinedButton(
                                                            onClick = { titularParaTroca = jne },
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) { Text("↔", style = MaterialTheme.typography.labelSmall) }
                                                        IconButton(onClick = { vm.moverParaReserva(jne.jogador) }, modifier = Modifier.size(24.dp)) {
                                                            Icon(Icons.Default.Remove, contentDescription = "Mover para reserva", modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                            if (modoPreJogo) item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
            1 -> { // Reservas
                if (escalacao?.reservas.isNullOrEmpty()) {
                    EmptyState("Sem reservas escalados")
                } else {
                    val reservasOrdenadas = escalacao!!.reservas
                        .sortedWith(compareBy({ it.jogador.posicao.ordinal }, { -it.jogador.forca }))
                    LazyColumn {
                        items(reservasOrdenadas) { jne ->
                            JogadorRow(
                                jogador = jne.jogador,
                                trailing = {
                                    if (titularParaTroca != null) {
                                        Button(
                                            onClick = {
                                                vm.trocarTitularComReserva(titularParaTroca!!, jne)
                                                titularParaTroca = null
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) { Text("↔ Trocar", style = MaterialTheme.typography.labelSmall) }
                                    } else {
                                        Box(Modifier.widthIn(max = 80.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    jne.posicaoUsada.abreviacao,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                IconButton(onClick = { vm.moverParaTitular(jne.jogador, jne.posicaoUsada) }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.Add, contentDescription = "Tornar titular", modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(onClick = { vm.removerDaEscalacao(jne.jogador) }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remover do banco", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                        if (modoPreJogo) item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
            2 -> { // Elenco completo
                val titularesCheios = (escalacao?.titulares?.size ?: 0) >= 11
                val reservasCheias  = (escalacao?.reservas?.size  ?: 0) >= 11
                LazyColumn {
                    items(elenco) { jogador ->
                        val naEscalacao = escalacao?.titulares?.any { it.jogador.id == jogador.id } == true
                        val naReserva   = escalacao?.reservas?.any  { it.jogador.id == jogador.id } == true
                        JogadorRow(
                            jogador = jogador,
                            onClick = { vm.selecionarJogador(jogador) },
                            trailing = {
                                when {
                                    jogador.lesionado -> Text(
                                        "Lesão",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    naEscalacao -> Text(
                                        "Titular",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    naReserva -> Text(
                                        "Reserva",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    else -> Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.widthIn(max = 110.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (titularParaTroca != null) {
                                            Button(
                                                onClick = {
                                                    vm.substituirTitularPorElenco(titularParaTroca!!, jogador)
                                                    titularParaTroca = null
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.fillMaxWidth().height(28.dp)
                                            ) {
                                                Text("→ Escalar", style = MaterialTheme.typography.labelSmall)
                                            }
                                        } else {
                                            if (!titularesCheios) {
                                                OutlinedButton(
                                                    onClick = { vm.moverParaTitular(jogador, jogador.posicao) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.fillMaxWidth().height(28.dp)
                                                ) {
                                                    Text("Titular", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            if (!reservasCheias) {
                                                OutlinedButton(
                                                    onClick = { vm.adicionarComoReserva(jogador) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.fillMaxWidth().height(28.dp)
                                                ) {
                                                    Text("Reserva", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                    if (modoPreJogo) item { Spacer(Modifier.height(80.dp)) }
                }
            }
            3 -> { // Tática
                TaticaTab(escalacao = escalacao, vm = vm, modoPreJogo = modoPreJogo)
            }
        }
    }

    // Botão flutuante "Iniciar Partida" no modo pré-jogo
    if (modoPreJogo && onIniciarPartida != null) {
        val totalTitulares = escalacao?.titulares?.size ?: 0
        val escalacaoCompleta = totalTitulares == 11
        Box(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    if (!escalacaoCompleta) {
                        val faltam = 11 - totalTitulares
                        Text(
                            "\u26a0 Adicione mais ${faltam} jogador${if (faltam > 1) "es" else ""} aos titulares (${totalTitulares}/11)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                    }
                    Button(
                        onClick = onIniciarPartida,
                        enabled = escalacaoCompleta,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            "▶ Iniciar Partida",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Dialog de detalhe do jogador selecionado
    selecionado?.let { jogador ->
        JogadorDetalheDialog(
            jogador    = jogador,
            onDismiss  = { vm.selecionarJogador(null) },
            onAposentar = { id -> vm.aposentarJogador(id); vm.selecionarJogador(null) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaticaTab(
    escalacao: br.com.managerfoot.domain.model.Escalacao?,
    vm: EscalacaoViewModel,
    modoPreJogo: Boolean = false
) {
    val formacoes = listOf(
        "4-4-2", "4-5-1", "4-3-3",
        "4-3-2-1", "5-4-1", "4-1-2-1-2",
        "3-5-2", "5-3-2", "4-2-3-1",
        "3-2-4-1", "2-3-5", "2-3-2-3", "4-2-4",
        "3-4-3", "4-2-2-2"
    )
    val estilos = EstiloJogo.entries

    val formacaoAtual = escalacao?.time?.taticaFormacao ?: "4-4-2"
    val estiloAtual = escalacao?.time?.estiloJogo ?: EstiloJogo.EQUILIBRADO

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Formação ──────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Formação tática", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Escolha o esquema tático. Ao selecionar, a melhor escalação é gerada automaticamente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formacoes.forEach { f ->
                    FilterChip(
                        selected = f == formacaoAtual,
                        onClick = { vm.mudarFormacao(f) },
                        label = { Text(f) }
                    )
                }
            }
        }

        // ── Gramado tático ────────────────────────────────────────
        if (escalacao != null) {
            GramadoTatico(
                titulares = escalacao.titulares,
                formacao  = formacaoAtual,
                modifier  = Modifier.padding(vertical = 4.dp)
            )
        }

        HorizontalDivider()

        // ── Estilo de jogo ────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estilo de jogo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Define a postura tática da equipe. Afeta ataques, defesas e probabilidades de gol.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                estilos.forEach { estilo ->
                    val label = when (estilo) {
                        EstiloJogo.OFENSIVO      -> "Ofensivo"
                        EstiloJogo.EQUILIBRADO   -> "Equilibrado"
                        EstiloJogo.DEFENSIVO     -> "Defensivo"
                        EstiloJogo.CONTRA_ATAQUE -> "Contra-ataque"
                    }
                    FilterChip(
                        selected = estilo == estiloAtual,
                        onClick = { vm.mudarEstilo(estilo) },
                        label = { Text(label) }
                    )
                }
            }
        }
        // Espaço extra no final para o botão flutuante não cobrir o último item
        if (modoPreJogo) Spacer(Modifier.height(96.dp))
    }
}

// ─────────────────────────────────────────────────────────
//  GramadoTatico — visão 2D do campo com posições dos jogadores
// ─────────────────────────────────────────────────────────
@Composable
internal fun GramadoTatico(
    titulares: List<JogadorNaEscalacao>,
    formacao: String,
    modifier: Modifier = Modifier
) {
    // Linhas do campo incluindo GK: ex. "4-3-3" → [1, 4, 3, 3]
    val linhas = remember(formacao) {
        try { listOf(1) + formacao.split("-").map { it.toInt() } }
        catch (_: Exception) { listOf(1, 4, 4, 2) }
    }

    // Ordena titulares para o campo: GL → DEF → MEIO → ATK
    val sortOrder = mapOf(Setor.GOLEIRO to 0, Setor.DEFESA to 1, Setor.MEIO to 2, Setor.ATAQUE to 3)
    val ordered = remember(titulares) {
        titulares.sortedWith(compareBy(
            { sortOrder[it.posicaoUsada.setor] ?: 2 },
            { it.posicaoUsada.ordinal }
        ))
    }

    val chipW = 52.dp
    val chipH = 48.dp
    // Altura dinâmica: cada linha precisa de ao menos 68dp para não sobrepor chips.
    // Mínimo de 300dp para formações com 4 linhas ou menos.
    val numLinhas = linhas.size
    val fieldHeight = (numLinhas * 68).dp.coerceAtLeast(300.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(fieldHeight)
            .clip(RoundedCornerShape(10.dp))
    ) {
        val w = maxWidth
        val h = maxHeight

        // Fundo do gramado
        Box(Modifier.fillMaxSize().background(Color(0xFF2D8653)))

        // Linhas do campo
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.width
            val sh = size.height
            val lineColor = Color.White.copy(alpha = 0.40f)
            val lw = 1.5.dp.toPx()

            drawRect(lineColor, style = Stroke(lw))                        // borda
            drawLine(lineColor, Offset(0f, sh * 0.5f), Offset(sw, sh * 0.5f), lw)  // linha do meio
            drawCircle(lineColor, sw * 0.12f, Offset(sw / 2, sh * 0.5f), style = Stroke(lw))  // círculo
            drawCircle(lineColor, 4f, Offset(sw / 2, sh * 0.5f))          // ponto central

            val paW = sw * 0.48f; val paH = sh * 0.13f
            // Área adversário (topo)
            drawRect(lineColor, Offset((sw - paW) / 2, 0f), Size(paW, paH), style = Stroke(lw))
            // Área própria (base)
            drawRect(lineColor, Offset((sw - paW) / 2, sh - paH), Size(paW, paH), style = Stroke(lw))

            val gW = sw * 0.22f; val gH = sh * 0.035f
            drawRect(lineColor, Offset((sw - gW) / 2, 0f),      Size(gW, gH), style = Stroke(lw))  // gol adv
            drawRect(lineColor, Offset((sw - gW) / 2, sh - gH), Size(gW, gH), style = Stroke(lw))  // gol próprio
        }

        // Cálculo de posições dos chips
        val placements = remember(ordered, linhas) {
            val list = mutableListOf<Triple<Float, Float, JogadorNaEscalacao>>()
            var idx = 0
            linhas.forEachIndexed { rowIdx, count ->
                // rowIdx=0 = GK (base), último = ataque (topo)
                val yFrac = 1f - (rowIdx.toFloat() + 0.65f) / numLinhas.toFloat()
                for (col in 0 until count) {
                    val xFrac = (col.toFloat() + 1f) / (count.toFloat() + 1f)
                    ordered.getOrNull(idx)?.let { list.add(Triple(xFrac, yFrac, it)) }
                    idx++
                }
            }
            list
        }

        placements.forEach { (xF, yF, jne) ->
            val xOff = (w * xF - chipW / 2).coerceIn(2.dp, w - chipW - 2.dp)
            val yOff = (h * yF - chipH / 2).coerceIn(2.dp, h - chipH - 2.dp)
            JogadorChipGramado(
                jne = jne,
                modifier = Modifier
                    .offset(x = xOff, y = yOff)
                    .width(chipW)
            )
        }
    }
}

@Composable
internal fun JogadorChipGramado(
    jne: JogadorNaEscalacao,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(posicaoColor(jne.posicaoUsada), CircleShape)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = jne.posicaoUsada.abreviacao,
                fontSize = 7.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Text(
            text = jne.jogador.nomeAbreviado.take(9),
            fontSize = 8.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal fun posicaoColor(posicao: Posicao): Color = when (posicao.setor) {
    Setor.GOLEIRO -> Color(0xFFF9A825)
    Setor.DEFESA  -> Color(0xFF1565C0)
    Setor.MEIO    -> Color(0xFF6A1B9A)
    Setor.ATAQUE  -> Color(0xFFC62828)
}

@Composable
private fun JogadorDetalheDialog(
    jogador: Jogador,
    onDismiss: () -> Unit,
    onAposentar: ((Int) -> Unit)? = null
) {
    var confirmarAposentadoria by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(jogador.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ── Informações gerais ──────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "${jogador.posicao.name.replace("_", " ")} · ${jogador.idade} anos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (jogador.posicaoSecundaria != null) {
                            Text(
                                "Alt: ${jogador.posicaoSecundaria.abreviacao}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    ForcaBadge(jogador.forca)
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                // ── Progressão / Desenvolvimento ────────────────────
                val statusDesenv = when {
                    jogador.categoriaBase    -> Triple("Base", Color(0xFF6A1B9A), "Talento em desenvolvimento")
                    jogador.idade in 16..24  -> Triple("Crescimento", Color(0xFF2E7D32), "Jovem em ascensão")
                    jogador.idade in 25..32  -> Triple("Estabilização", Color(0xFF1565C0), "Auge da carreira")
                    else                     -> Triple("Declínio", Color(0xFFC62828), "Veterano em fim de carreira")
                }
                Text(
                    "Desenvolvimento",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusDesenv.second.copy(alpha = 0.15f)
                        ) {
                            Text(
                                statusDesenv.first,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = statusDesenv.second,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            statusDesenv.third,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Barra de momentum da temporada: mostra o progressoEvolucao real (-1..+1)
                val prog = jogador.progressoEvolucao.coerceIn(-1f, 1f)
                val progNormalizado = (prog + 1f) / 2f  // mapeia -1..+1 para 0..1
                val progColor = when {
                    prog >= 0.5f  -> Color(0xFF2E7D32)
                    prog >= 0.1f  -> Color(0xFF558B2F)
                    prog >= -0.1f -> MaterialTheme.colorScheme.onSurfaceVariant
                    prog >= -0.5f -> Color(0xFFE65100)
                    else          -> Color(0xFFC62828)
                }
                val progLabel = when {
                    prog >  0.05f -> "Evoluindo ${"%.2f".format(prog)}/+1.0"
                    prog < -0.05f -> "Regredindo ${"%.2f".format(prog)}/-1.0"
                    else          -> "Estável"
                }
                Text(
                    progLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = progColor,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { progNormalizado },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = progColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                // Nota média e partidas da temporada
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val notaColor = when {
                            jogador.notaMedia >= 8.0f -> Color(0xFF2E7D32)
                            jogador.notaMedia >= 6.5f -> MaterialTheme.colorScheme.primary
                            jogador.notaMedia >= 5.0f -> Color(0xFFE65100)
                            else -> Color(0xFFC62828)
                        }
                        Text(
                            "%.1f".format(jogador.notaMedia),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = notaColor
                        )
                        Text(
                            "Nota média",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${jogador.partidasTemporada}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Partidas",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                // ── Atributos ───────────────────────────────────────
                Text(
                    "Atributos",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                AtributoRow("Força geral", jogador.forca)
                AtributoRow("Técnica", jogador.tecnica)
                AtributoRow("Passe", jogador.passe)
                AtributoRow("Velocidade", jogador.velocidade)
                AtributoRow("Finalização", jogador.finalizacao)
                AtributoRow("Defesa", jogador.defesa)
                AtributoRow("Físico", jogador.fisico)

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                // ── Contrato ────────────────────────────────────────
                Text(
                    "Contrato",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text("Salário: ${formatarSaldo(jogador.salario)}/mês", style = MaterialTheme.typography.bodySmall)
                Text("Contrato: ${jogador.contratoAnos} anos restantes", style = MaterialTheme.typography.bodySmall)
                Text("Valor de mercado: ${formatarSaldo(jogador.valorMercado)}", style = MaterialTheme.typography.bodySmall)

                // ── Aposentadoria ───────────────────────────────────
                if (onAposentar != null && jogador.idade in 33..44 && !jogador.aposentado) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    if (confirmarAposentadoria) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Confirma a aposentadoria de ${jogador.nomeAbreviado}? Esta ação não pode ser desfeita.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { confirmarAposentadoria = false },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cancelar", style = MaterialTheme.typography.labelSmall) }
                                Button(
                                    onClick = {
                                        onAposentar(jogador.id)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) { Text("Aposentar", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { confirmarAposentadoria = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                "Aposentar jogador",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun AtributoRow(label: String, valor: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(
                progress = { valor / 99f },
                modifier = Modifier.width(80.dp).height(4.dp),
                color = when {
                    valor >= 80 -> MaterialTheme.colorScheme.primary
                    valor >= 65 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                }
            )
            Text("$valor", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  MercadoScreen
// ═══════════════════════════════════════════════════════════
@Composable
fun MercadoScreen(
    timeId: Int,
    onVoltar: () -> Unit = {},
    onIrParaClubes: () -> Unit = {},
    vm: MercadoViewModel = hiltViewModel()
) {
    val livres    by vm.jogadoresLivres.collectAsState()
    val elenco    by vm.elencoAtual.collectAsState()
    val saldo     by vm.saldo.collectAsState()
    val mensagem  by vm.mensagem.collectAsState()
    val transferencias by vm.transferencias.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    var abaAtiva by remember { mutableIntStateOf(0) }

    mensagem?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(3000)
            vm.limparMensagem()
        }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
    }

    Column(Modifier.fillMaxSize()) {
        // Cabeçalho com saldo e botão para Clubes
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Saldo disponível", style = MaterialTheme.typography.labelMedium)
                Text(formatarSaldo(saldo), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Button(
                onClick = onIrParaClubes,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Ver Clubes", style = MaterialTheme.typography.labelMedium)
            }
        }

        TabRow(selectedTabIndex = abaAtiva) {
            Tab(selected = abaAtiva == 0, onClick = { abaAtiva = 0 }, text = { Text("Mercado livre") })
            Tab(selected = abaAtiva == 1, onClick = { abaAtiva = 1 }, text = { Text("Meu elenco") })
            Tab(selected = abaAtiva == 2, onClick = { abaAtiva = 2 }, text = { Text("Transferências") })
        }

        when (abaAtiva) {
            0 -> {
                if (livres.isEmpty()) {
                    EmptyState("Nenhum jogador disponível no mercado")
                } else {
                    LazyColumn {
                        item { SecaoHeader("${livres.size} jogadores livres") }
                        items(livres) { jogador ->
                            JogadorRow(
                                jogador = jogador,
                                trailing = {
                                    val podeContratar = saldo >= jogador.valorMercado
                                    Button(
                                        onClick = { vm.contratarJogador(jogador) },
                                        enabled = podeContratar,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(formatarSaldo(jogador.valorMercado), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
            1 -> {
                LazyColumn {
                    item { SecaoHeader("${elenco.size} jogadores no elenco") }
                    items(elenco) { jogador ->
                        JogadorRow(
                            jogador = jogador,
                            trailing = {
                                OutlinedButton(
                                    onClick = { vm.venderJogador(jogador) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Vender", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
            2 -> {
                if (transferencias.isEmpty()) {
                    EmptyState("Nenhuma transferência registrada")
                } else {
                    LazyColumn {
                        item { SecaoHeader("${transferencias.size} transferências") }
                        items(transferencias) { t ->
                            TransferenciaRow(t)
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferenciaRow(t: br.com.managerfoot.data.dao.TransferenciaDetalhe) {
    val tipoLabel = when (t.tipo) {
        br.com.managerfoot.data.database.entities.TipoTransferencia.COMPRA          -> "Contratação"
        br.com.managerfoot.data.database.entities.TipoTransferencia.VENDA           -> "Venda"
        br.com.managerfoot.data.database.entities.TipoTransferencia.FIM_CONTRATO    -> "Livre"
        br.com.managerfoot.data.database.entities.TipoTransferencia.EMPRESTIMO_SAIDA -> "Empréstimo"
        br.com.managerfoot.data.database.entities.TipoTransferencia.EMPRESTIMO_RETORNO -> "Retorno"
        br.com.managerfoot.data.database.entities.TipoTransferencia.PROMOVIDO_BASE  -> "Base"
        br.com.managerfoot.data.database.entities.TipoTransferencia.DISPENSADO_BASE -> "Dispensado"
    }
    val tipoColor = when (t.tipo) {
        br.com.managerfoot.data.database.entities.TipoTransferencia.VENDA  -> MaterialTheme.colorScheme.tertiary
        br.com.managerfoot.data.database.entities.TipoTransferencia.COMPRA -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.jogadorNome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            val descricao = buildString {
                append(t.origemNome ?: "Mercado livre")
                append(" → ")
                append(t.destinoNome ?: "Mercado livre")
            }
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Temp. ${t.temporadaId} · Mês ${t.mes}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(tipoLabel, style = MaterialTheme.typography.labelSmall,
                color = tipoColor, fontWeight = FontWeight.SemiBold)
            if (t.valor > 0) {
                Text(formatarSaldo(t.valor), style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}
