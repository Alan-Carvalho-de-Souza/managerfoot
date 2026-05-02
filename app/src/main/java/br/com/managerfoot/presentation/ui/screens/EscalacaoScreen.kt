package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.SwapHoriz
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
import br.com.managerfoot.data.database.entities.StatusProposta
import br.com.managerfoot.data.database.entities.TipoProposta
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.domain.model.PropostaIATransferencia
import br.com.managerfoot.domain.engine.CalculadoraForca
import br.com.managerfoot.domain.model.JogadorNaEscalacao
import br.com.managerfoot.presentation.ui.components.*
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.EscalacaoViewModel
import br.com.managerfoot.presentation.viewmodel.MercadoViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.MaterialTheme

// ═══════════════════════════════════════════════════════════
//  EscalacaoScreen — Tactical Dark
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
    val escalacaoAdversario by vm.escalacaoAdversario.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }
    LaunchedEffect(adversarioId) {
        if (adversarioId > 0) vm.carregarAdversario(adversarioId)
    }

    // Em modo pré-jogo, começa na aba Tática (3) para o jogador definir o esquema
    var abaAtiva by remember { mutableIntStateOf(if (modoPreJogo) 3 else 0) }
    val abas = if (modoPreJogo) listOf("Titulares", "Reservas", "Elenco", "Tática", "Adversário")
               else listOf("Titulares", "Reservas", "Elenco", "Tática")
    var titularParaTroca by remember { mutableStateOf<JogadorNaEscalacao?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Banner pré-jogo: comparativo dos dois times
        if (modoPreJogo) {
            PreJogoBanner(
                escalacaoMeu = escalacao,
                adversario = adversario,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )
        }

        // Card de informação da formação atual
        escalacao?.let { esc ->
            FormacaoInfoCard(
                formacao = esc.formacaoEfetiva,
                titularesCount = esc.titulares.size,
                forcaMedia = esc.titulares
                    .map { it.jogador.forcaEfetiva(it.posicaoUsada) }
                    .takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }

        // Tabs em formato pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.Center
        ) {
            TabRowPill(
                abas = abas,
                selecionada = abaAtiva,
                onSelecionar = { abaAtiva = it }
            )
        }

        // Banner de troca ativa
        if (titularParaTroca != null) {
            TrocaAtivoBanner(
                jne = titularParaTroca!!,
                onCancelar = { titularParaTroca = null },
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
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
                    LazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = Spacing.md,
                            vertical = Spacing.sm
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(titularesOrdenados, key = { it.jogador.id }) { jne ->
                            val ehSelecionado = titularParaTroca?.jogador?.id == jne.jogador.id
                            val ehImproviso = jne.posicaoUsada != jne.jogador.posicao &&
                                    jne.posicaoUsada != jne.jogador.posicaoSecundaria &&
                                    jne.posicaoUsada.setor != jne.jogador.posicao.setor
                            JogadorEscalacaoLinha(
                                jogador = jne.jogador,
                                posicaoUsada = jne.posicaoUsada,
                                improviso = ehImproviso,
                                selecionado = ehSelecionado,
                                onClick = { if (titularParaTroca == null) vm.selecionarJogador(jne.jogador) },
                                trailing = {
                                    when {
                                        ehSelecionado -> {
                                            OutlinedButton(
                                                onClick = { titularParaTroca = null },
                                                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("✕ Cancelar", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        titularParaTroca != null -> {
                                            Button(
                                                onClick = {
                                                    vm.trocarPosicoes(titularParaTroca!!, jne)
                                                    titularParaTroca = null
                                                },
                                                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("↔ Trocar", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        else -> {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedButton(
                                                    onClick = { titularParaTroca = jne },
                                                    contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.SwapHoriz,
                                                        contentDescription = "Trocar",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { vm.moverParaReserva(jne.jogador) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Remove,
                                                        contentDescription = "Mover para reserva",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        if (modoPreJogo) item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
            1 -> { // Reservas
                if (escalacao?.reservas.isNullOrEmpty()) {
                    EmptyState("Sem reservas escalados")
                } else {
                    val reservasOrdenadas = escalacao!!.reservas
                        .sortedWith(compareBy({ it.jogador.posicao.ordinal }, { -it.jogador.forca }))
                    LazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = Spacing.md,
                            vertical = Spacing.sm
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(reservasOrdenadas) { jne ->
                            JogadorEscalacaoLinha(
                                jogador = jne.jogador,
                                posicaoUsada = jne.posicaoUsada,
                                improviso = false,
                                selecionado = false,
                                onClick = { vm.selecionarJogador(jne.jogador) },
                                trailing = {
                                    if (titularParaTroca != null) {
                                        Button(
                                            onClick = {
                                                vm.trocarTitularComReserva(titularParaTroca!!, jne)
                                                titularParaTroca = null
                                            },
                                            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("↔ Trocar", style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { vm.moverParaTitular(jne.jogador, jne.posicaoUsada) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Tornar titular",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { vm.removerDaEscalacao(jne.jogador) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remover do banco",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        if (modoPreJogo) item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
            2 -> { // Elenco completo
                val titularesCheios = (escalacao?.titulares?.size ?: 0) >= 11
                val reservasCheias  = (escalacao?.reservas?.size  ?: 0) >= 11
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(elenco) { jogador ->
                        val naEscalacao = escalacao?.titulares?.any { it.jogador.id == jogador.id } == true
                        val naReserva   = escalacao?.reservas?.any  { it.jogador.id == jogador.id } == true
                        JogadorEscalacaoLinha(
                            jogador = jogador,
                            posicaoUsada = jogador.posicao,
                            improviso = false,
                            selecionado = false,
                            onClick = { vm.selecionarJogador(jogador) },
                            trailing = {
                                when {
                                    jogador.lesionado -> StatusChip(
                                        texto = "Lesão",
                                        cor = MaterialTheme.colorScheme.error
                                    )
                                    naEscalacao -> StatusChip(
                                        texto = "Titular",
                                        cor = MaterialTheme.colorScheme.primary
                                    )
                                    naReserva -> StatusChip(
                                        texto = "Reserva",
                                        cor = MaterialTheme.colorScheme.secondary
                                    )
                                    else -> Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.widthIn(max = 110.dp),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                                    ) {
                                        if (titularParaTroca != null) {
                                            Button(
                                                onClick = {
                                                    vm.substituirTitularPorElenco(titularParaTroca!!, jogador)
                                                    titularParaTroca = null
                                                },
                                                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                                modifier = Modifier.fillMaxWidth().height(28.dp)
                                            ) {
                                                Text("→ Escalar", style = MaterialTheme.typography.labelSmall)
                                            }
                                        } else {
                                            if (!titularesCheios) {
                                                OutlinedButton(
                                                    onClick = { vm.moverParaTitular(jogador, jogador.posicao) },
                                                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                                    modifier = Modifier.fillMaxWidth().height(28.dp)
                                                ) {
                                                    Text("Titular", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            if (!reservasCheias) {
                                                OutlinedButton(
                                                    onClick = { vm.adicionarComoReserva(jogador) },
                                                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
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
                    }
                    if (modoPreJogo) item { Spacer(Modifier.height(80.dp)) }
                }
            }
            3 -> { // Tática
                TaticaTab(escalacao = escalacao, vm = vm, modoPreJogo = modoPreJogo)
            }
            4 -> { // Adversário (apenas no pré-jogo)
                val esc = escalacaoAdversario
                if (esc == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    EscalacaoAdversarioPreJogoTab(escalacao = esc)
                }
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
                shadowElevation = Elev.floating,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    if (!escalacaoCompleta) {
                        val faltam = 11 - totalTitulares
                        Text(
                            "⚠ Adicione mais $faltam jogador${if (faltam > 1) "es" else ""} aos titulares ($totalTitulares/11)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = Spacing.xs)
                        )
                    }
                    Button(
                        onClick = onIniciarPartida,
                        enabled = escalacaoCompleta,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.md),
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

// ─────────────────────────────────────────────────────────────
//  Componentes auxiliares — Tactical Dark
// ─────────────────────────────────────────────────────────────
@Composable
private fun PreJogoBanner(
    escalacaoMeu: br.com.managerfoot.domain.model.Escalacao?,
    adversario: br.com.managerfoot.domain.model.Time?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GreenMid)
    ) {
        Column(
            Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                "PRÉ-JOGO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = GreenElectric,
                letterSpacing = 1.sp
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                escalacaoMeu?.let { esc ->
                    TimeColumnPreJogo(
                        nome = esc.time.nome,
                        escudoRes = esc.time.escudoRes,
                        formacao = esc.time.taticaFormacao,
                        estilo = esc.time.estiloJogo,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "vs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (adversario != null) {
                    TimeColumnPreJogo(
                        nome = adversario.nome,
                        escudoRes = adversario.escudoRes,
                        formacao = adversario.taticaFormacao,
                        estilo = adversario.estiloJogo,
                        accent = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeColumnPreJogo(
    nome: String,
    escudoRes: String,
    formacao: String,
    estilo: EstiloJogo,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        TeamBadge(nome = nome, escudoRes = escudoRes, size = 40.dp)
        Text(
            nome,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(formacao, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val estiloLabel = when (estilo) {
            EstiloJogo.OFENSIVO      -> "Ofensivo"
            EstiloJogo.EQUILIBRADO   -> "Equil."
            EstiloJogo.DEFENSIVO     -> "Defensivo"
            EstiloJogo.CONTRA_ATAQUE -> "C.-Ataque"
        }
        Text(estiloLabel, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FormacaoInfoCard(
    formacao: String,
    titularesCount: Int,
    forcaMedia: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KpiCard(label = "Formação", valor = formacao)
                KpiCard(
                    label = "Titulares",
                    valor = "$titularesCount/11",
                    accent = if (titularesCount == 11) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.error
                )
            }
            ForcaBadge(forcaMedia)
        }
    }
}

@Composable
private fun TrocaAtivoBanner(
    jne: JogadorNaEscalacao,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.6f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = AmberAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Trocando: ${jne.jogador.nomeAbreviado} (${jne.posicaoUsada.abreviacao})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(onClick = onCancelar) {
                Text("Cancelar", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Linha de jogador estilo Tactical Dark com faixa lateral colorida pelo setor,
 * PosicaoBadge e marcação de improviso/seleção. Compartilhada entre Titulares,
 * Reservas e Elenco.
 */
@Composable
private fun JogadorEscalacaoLinha(
    jogador: Jogador,
    posicaoUsada: Posicao,
    improviso: Boolean,
    selecionado: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    val borderColor = when {
        selecionado -> AmberAccent
        improviso -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    val containerColor = if (selecionado)
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
    else
        MaterialTheme.colorScheme.surface

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (selecionado) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Faixa lateral pelo setor da posição USADA (não da posição natural)
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(corSetor(posicaoUsada.setor))
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PosicaoBadge(
                    abreviacao = posicaoUsada.abreviacao,
                    setor = posicaoUsada.setor
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        jogador.nome,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            "${jogador.idade}a",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FadigaBadge(jogador.fadiga)
                        if (improviso) {
                            Text(
                                "*Improviso",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                ForcaBadge(jogador.forca)
                trailing()
            }
        }
    }
}

@Composable
private fun StatusChip(texto: String, cor: Color) {
    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = cor.copy(alpha = 0.15f),
        border = BorderStroke(0.5.dp, cor.copy(alpha = 0.5f))
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = cor,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Aba Adversário (modo pré-jogo)
// ─────────────────────────────────────────────────────────────
@Composable
private fun EscalacaoAdversarioPreJogoTab(escalacao: br.com.managerfoot.domain.model.Escalacao) {
    val posOrder = mapOf(Setor.GOLEIRO to 0, Setor.DEFESA to 1, Setor.MEIO to 2, Setor.ATAQUE to 3)
    val titularesOrdenados = remember(escalacao) {
        escalacao.titulares.sortedWith(compareBy(
            { posOrder[it.posicaoUsada.setor] ?: 2 },
            { it.posicaoUsada.ordinal }
        ))
    }
    val forcaAdversario = remember(escalacao) { CalculadoraForca.calcularForcaTime(escalacao).toInt() }
    var mostrarGramado by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        TeamBadge(nome = escalacao.time.nome, escudoRes = escalacao.time.escudoRes, size = 32.dp)
                        Column {
                            Text(
                                escalacao.time.nome,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${escalacao.time.taticaFormacao} · ${when (escalacao.time.estiloJogo) {
                                    EstiloJogo.OFENSIVO -> "Ofensivo"
                                    EstiloJogo.EQUILIBRADO -> "Equilibrado"
                                    EstiloJogo.DEFENSIVO -> "Defensivo"
                                    EstiloJogo.CONTRA_ATAQUE -> "Contra-ataque"
                                }} · Força $forcaAdversario",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                TextButton(onClick = { mostrarGramado = !mostrarGramado }) {
                    Text(if (mostrarGramado) "Lista" else "Campo")
                }
            }
        }

        if (mostrarGramado) {
            GramadoTatico(
                titulares = escalacao.titulares,
                formacao = escalacao.time.taticaFormacao,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(Spacing.sm)
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(titularesOrdenados) { jne ->
                    JogadorEscalacaoLinha(
                        jogador = jne.jogador,
                        posicaoUsada = jne.posicaoUsada,
                        improviso = false,
                        selecionado = false,
                        onClick = { /* read-only */ },
                        trailing = { /* sem ações */ }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Aba Tática
// ─────────────────────────────────────────────────────────────
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
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // ── Formação ──────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(
                Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SectionTitle("Formação tática")
                Text(
                    "Selecione o esquema. A melhor escalação é gerada automaticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    formacoes.forEach { f ->
                        FilterChipPill(
                            label = f,
                            selected = f == formacaoAtual,
                            onClick = { vm.mudarFormacao(f) }
                        )
                    }
                }
            }
        }

        // ── Gramado tático ────────────────────────────────────────
        if (escalacao != null) {
            GramadoTatico(
                titulares = escalacao.titulares,
                formacao  = formacaoAtual,
                modifier  = Modifier.padding(vertical = Spacing.xs)
            )
        }

        // ── Estilo de jogo ────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(
                Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SectionTitle("Estilo de jogo")
                Text(
                    "Define a postura tática da equipe. Afeta ataques, defesas e probabilidades de gol.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    estilos.forEach { estilo ->
                        val label = when (estilo) {
                            EstiloJogo.OFENSIVO      -> "Ofensivo"
                            EstiloJogo.EQUILIBRADO   -> "Equilibrado"
                            EstiloJogo.DEFENSIVO     -> "Defensivo"
                            EstiloJogo.CONTRA_ATAQUE -> "Contra-ataque"
                        }
                        FilterChipPill(
                            label = label,
                            selected = estilo == estiloAtual,
                            onClick = { vm.mudarEstilo(estilo) }
                        )
                    }
                }
            }
        }
        // Espaço extra no final para o botão flutuante não cobrir o último item
        if (modoPreJogo) Spacer(Modifier.height(96.dp))
    }
}

// ─────────────────────────────────────────────────────────────
//  GramadoTatico — visão 2D do campo
// ─────────────────────────────────────────────────────────────
@Composable
internal fun GramadoTatico(
    titulares: List<JogadorNaEscalacao>,
    formacao: String,
    modifier: Modifier = Modifier
) {
    val linhas = remember(formacao) {
        try { listOf(1) + formacao.split("-").map { it.toInt() } }
        catch (_: Exception) { listOf(1, 4, 4, 2) }
    }

    val sortOrder = mapOf(Setor.GOLEIRO to 0, Setor.DEFESA to 1, Setor.MEIO to 2, Setor.ATAQUE to 3)
    val ordered = remember(titulares) {
        titulares.sortedWith(compareBy(
            { sortOrder[it.posicaoUsada.setor] ?: 2 },
            { it.posicaoUsada.ordinal }
        ))
    }

    val chipW = 52.dp
    val chipH = 48.dp
    val numLinhas = linhas.size
    val fieldHeight = (numLinhas * 68).dp.coerceAtLeast(300.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(fieldHeight)
            .clip(RoundedCornerShape(Radius.md))
            .border(
                BorderStroke(1.dp, GreenMid),
                RoundedCornerShape(Radius.md)
            )
    ) {
        val w = maxWidth
        val h = maxHeight

        // Fundo do gramado (paleta Tactical Dark)
        Box(Modifier.fillMaxSize().background(PitchGreenLight))

        // Linhas do campo
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.width
            val sh = size.height
            val lineColor = Color.White.copy(alpha = 0.40f)
            val lw = 1.5.dp.toPx()

            drawRect(lineColor, style = Stroke(lw))
            drawLine(lineColor, Offset(0f, sh * 0.5f), Offset(sw, sh * 0.5f), lw)
            drawCircle(lineColor, sw * 0.12f, Offset(sw / 2, sh * 0.5f), style = Stroke(lw))
            drawCircle(lineColor, 4f, Offset(sw / 2, sh * 0.5f))

            val paW = sw * 0.48f; val paH = sh * 0.13f
            drawRect(lineColor, Offset((sw - paW) / 2, 0f), Size(paW, paH), style = Stroke(lw))
            drawRect(lineColor, Offset((sw - paW) / 2, sh - paH), Size(paW, paH), style = Stroke(lw))

            val gW = sw * 0.22f; val gH = sh * 0.035f
            drawRect(lineColor, Offset((sw - gW) / 2, 0f),      Size(gW, gH), style = Stroke(lw))
            drawRect(lineColor, Offset((sw - gW) / 2, sh - gH), Size(gW, gH), style = Stroke(lw))
        }

        val placements = remember(ordered, linhas) {
            val list = mutableListOf<Triple<Float, Float, JogadorNaEscalacao>>()
            var idx = 0
            linhas.forEachIndexed { rowIdx, count ->
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
                .background(corSetor(jne.posicaoUsada.setor), CircleShape)
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

// Mantida para compatibilidade com outros lugares que usem posicaoColor(Posicao).
// Internamente chama corSetor() do design system.
internal fun posicaoColor(posicao: Posicao): Color = corSetor(posicao.setor)

// ─────────────────────────────────────────────────────────────
//  Dialog de detalhe do jogador
// ─────────────────────────────────────────────────────────────
@Composable
private fun JogadorDetalheDialog(
    jogador: Jogador,
    onDismiss: () -> Unit,
    onAposentar: ((Int) -> Unit)? = null
) {
    var confirmarAposentadoria by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PosicaoBadge(jogador.posicao.abreviacao, jogador.posicao.setor)
                Text(jogador.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
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

                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))

                // ── Progressão / Desenvolvimento ────────────────────
                val statusDesenv = when {
                    jogador.categoriaBase    -> Triple("Base", SetorMeio, "Talento em desenvolvimento")
                    jogador.idade in 16..24  -> Triple("Crescimento", PromotionGreen, "Jovem em ascensão")
                    jogador.idade in 25..32  -> Triple("Estabilização", LibertadoresBlue, "Auge da carreira")
                    else                     -> Triple("Declínio", RelegationRed, "Veterano em fim de carreira")
                }
                Text(
                    "DESENVOLVIMENTO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = statusDesenv.second.copy(alpha = 0.18f),
                        border = BorderStroke(0.5.dp, statusDesenv.second.copy(alpha = 0.5f))
                    ) {
                        Text(
                            statusDesenv.first,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusDesenv.second,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp)
                        )
                    }
                    Text(
                        statusDesenv.third,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                val prog = jogador.progressoEvolucao.coerceIn(-1f, 1f)
                val progNormalizado = (prog + 1f) / 2f
                val progColor = when {
                    prog >= 0.5f  -> PromotionGreen
                    prog >= 0.1f  -> MoraleBom
                    prog >= -0.1f -> MaterialTheme.colorScheme.onSurfaceVariant
                    prog >= -0.5f -> AmberAccent
                    else          -> RelegationRed
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
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { progNormalizado },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = progColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val notaColor = when {
                            jogador.notaMedia >= 8.0f -> PromotionGreen
                            jogador.notaMedia >= 6.5f -> MaterialTheme.colorScheme.primary
                            jogador.notaMedia >= 5.0f -> AmberAccent
                            else -> RelegationRed
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

                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))

                // ── Atributos ───────────────────────────────────────
                Text(
                    "ATRIBUTOS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(2.dp))
                AtributoRow("Força geral", jogador.forca)
                AtributoRow("Técnica", jogador.tecnica)
                AtributoRow("Passe", jogador.passe)
                AtributoRow("Velocidade", jogador.velocidade)
                AtributoRow("Finalização", jogador.finalizacao)
                AtributoRow("Defesa", jogador.defesa)
                AtributoRow("Físico", jogador.fisico)

                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))

                // ── Contrato ────────────────────────────────────────
                Text(
                    "CONTRATO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(2.dp))
                Text("Salário: ${formatarSaldo(jogador.salario)}/mês", style = MaterialTheme.typography.bodySmall)
                Text("Contrato: ${jogador.contratoAnos} anos restantes", style = MaterialTheme.typography.bodySmall)
                Text("Valor de mercado: ${formatarSaldo(jogador.valorMercado)}", style = MaterialTheme.typography.bodySmall)

                // ── Aposentadoria ───────────────────────────────────
                if (onAposentar != null && jogador.idade in 33..44 && !jogador.aposentado) {
                    HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                    if (confirmarAposentadoria) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                "Confirma a aposentadoria de ${jogador.nomeAbreviado}? Esta ação não pode ser desfeita.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            LinearProgressIndicator(
                progress = { valor / 99f },
                modifier = Modifier.width(80.dp).height(4.dp),
                color = when {
                    valor >= 80 -> PromotionGreen
                    valor >= 65 -> MaterialTheme.colorScheme.primary
                    valor >= 50 -> AmberAccent
                    else -> RelegationRed
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text("$valor", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  MercadoScreen — Tactical Dark
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
    val propostas by vm.propostas.collectAsState()
    val emprestados    by vm.jogadoresEmprestados.collectAsState()
    val nomesTimesPorId by vm.nomesTimesPorId.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    var abaAtiva by remember { mutableIntStateOf(0) }
    val pendentesCount = propostas.count { it.status == StatusProposta.PENDENTE }
    val abas = remember(pendentesCount) {
        listOf(
            "Mercado",
            "Elenco",
            "Histórico",
            if (pendentesCount > 0) "Propostas ($pendentesCount)" else "Propostas"
        )
    }

    mensagem?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(3000)
            vm.limparMensagem()
        }
        Snackbar(modifier = Modifier.padding(Spacing.lg)) { Text(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar com saldo e ação Ver Clubes
        ScreenTopBar(
            titulo = "Mercado",
            subtitulo = "Saldo: ${formatarSaldo(saldo)}",
            onVoltar = onVoltar,
            acoes = {
                TextButton(onClick = onIrParaClubes) {
                    Text("Ver Clubes", style = MaterialTheme.typography.labelMedium)
                }
            }
        )

        // KPI Saldo destacado
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, GreenMid)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "SALDO DISPONÍVEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Text(
                        formatarSaldo(saldo),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (saldo >= 0) MoneyPositive else MoneyNegative
                    )
                }
                if (pendentesCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = AmberAccent.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, AmberAccent.copy(alpha = 0.6f))
                    ) {
                        Column(
                            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "$pendentesCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AmberAccent
                            )
                            Text(
                                "PROPOSTA${if (pendentesCount > 1) "S" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberAccent,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }

        // Tabs em formato pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.Center
        ) {
            TabRowPill(
                abas = abas,
                selecionada = abaAtiva,
                onSelecionar = { abaAtiva = it }
            )
        }

        when (abaAtiva) {
            0 -> { // Mercado livre
                if (livres.isEmpty()) {
                    EmptyState("Nenhum jogador disponível no mercado")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = Spacing.md,
                            vertical = Spacing.sm
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        item {
                            SecaoHeader("${livres.size} jogadores livres")
                        }
                        items(livres) { jogador ->
                            JogadorEscalacaoLinha(
                                jogador = jogador,
                                posicaoUsada = jogador.posicao,
                                improviso = false,
                                selecionado = false,
                                onClick = { },
                                trailing = {
                                    val podeContratar = saldo >= jogador.valorMercado
                                    Button(
                                        onClick = { vm.contratarJogador(jogador) },
                                        enabled = podeContratar,
                                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            formatarSaldo(jogador.valorMercado),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            1 -> { // Meu elenco
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item { SecaoHeader("${elenco.size} jogadores no elenco") }
                    items(elenco) { jogador ->
                        JogadorEscalacaoLinha(
                            jogador = jogador,
                            posicaoUsada = jogador.posicao,
                            improviso = false,
                            selecionado = false,
                            onClick = { },
                            trailing = {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                                ) {
                                    FilterChipPill(
                                        label = "💲 À venda",
                                        selected = jogador.disponívelParaVenda,
                                        onClick = { vm.marcarParaVenda(jogador, !jogador.disponívelParaVenda) }
                                    )
                                    FilterChipPill(
                                        label = "🤝 Empréstimo",
                                        selected = jogador.disponívelParaEmprestimo,
                                        onClick = { vm.marcarParaEmprestimo(jogador, !jogador.disponívelParaEmprestimo) }
                                    )
                                }
                            }
                        )
                    }
                    if (emprestados.isNotEmpty()) {
                        item {
                            SecaoHeader("${emprestados.size} jogador${if (emprestados.size != 1) "es" else ""} emprestado${if (emprestados.size != 1) "s" else ""}")
                        }
                        items(emprestados) { jogador ->
                            val nomeClube = jogador.timeId?.let { nomesTimesPorId[it] } ?: "Clube desconhecido"
                            val mes = jogador.mesRetornoEmprestimo
                            val ano = jogador.anoRetornoEmprestimo
                            val retorno = if (mes != null && ano != null) {
                                "${mes.toString().padStart(2, '0')}/$ano"
                            } else "-"
                            JogadorEscalacaoLinha(
                                jogador = jogador,
                                posicaoUsada = jogador.posicao,
                                improviso = false,
                                selecionado = false,
                                onClick = { },
                                trailing = {
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        StatusChip(texto = "Emprestado", cor = SulAmericanaTeal)
                                        Text(
                                            nomeClube,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 110.dp)
                                        )
                                        Text(
                                            "Retorna $retorno",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            2 -> { // Transferências
                if (transferencias.isEmpty()) {
                    EmptyState("Nenhuma transferência registrada")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = Spacing.md,
                            vertical = Spacing.sm
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        item { SecaoHeader("${transferencias.size} transferências") }
                        items(transferencias) { t ->
                            TransferenciaCard(t)
                        }
                    }
                }
            }
            3 -> { // Propostas
                PropostasTab(
                    propostas = propostas,
                    onAceitar  = { vm.aceitarProposta(it) },
                    onRecusar  = { vm.recusarProposta(it.id) },
                    onNegociar = { proposta, valor -> vm.negociarProposta(proposta.id, valor) }
                )
            }
        }
    }
}

@Composable
private fun PropostasTab(
    propostas: List<PropostaIATransferencia>,
    onAceitar:  (PropostaIATransferencia) -> Unit,
    onRecusar:  (PropostaIATransferencia) -> Unit,
    onNegociar: (PropostaIATransferencia, Long) -> Unit
) {
    if (propostas.isEmpty()) {
        EmptyState("Nenhuma proposta recebida no momento")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = Spacing.md,
            vertical = Spacing.sm
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item {
            SecaoHeader("${propostas.size} proposta${if (propostas.size != 1) "s" else ""} recebida${if (propostas.size != 1) "s" else ""}")
        }
        items(propostas) { proposta ->
            PropostaCard(
                proposta   = proposta,
                onAceitar  = { onAceitar(proposta) },
                onRecusar  = { onRecusar(proposta) },
                onNegociar = { valor -> onNegociar(proposta, valor) }
            )
        }
    }
}

@Composable
private fun PropostaCard(
    proposta:   PropostaIATransferencia,
    onAceitar:  () -> Unit,
    onRecusar:  () -> Unit,
    onNegociar: (Long) -> Unit
) {
    var mostrarDialogoNegociacao by remember { mutableStateOf(false) }

    val statusColor = when (proposta.status) {
        StatusProposta.PENDENTE                -> AmberAccent
        StatusProposta.AGUARDANDO_RESPOSTA_IA  -> LibertadoresBlue
        else                                   -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Cabeçalho
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        proposta.nomeTimeComprador,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val (tipoLabel, tipoCor) = when (proposta.tipoProposta) {
                        TipoProposta.EMPRESTIMO -> "Empréstimo" to SulAmericanaTeal
                        else                   -> "Compra" to MaterialTheme.colorScheme.tertiary
                    }
                    StatusChip(texto = tipoLabel, cor = tipoCor)
                }
                val statusLabel = when (proposta.status) {
                    StatusProposta.PENDENTE                -> "Aguardando"
                    StatusProposta.AGUARDANDO_RESPOSTA_IA  -> "Negociando"
                    else                                   -> proposta.status.name
                }
                StatusChip(texto = statusLabel, cor = statusColor)
            }

            // Dados do jogador
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PosicaoBadge(
                    abreviacao = proposta.posicao.abreviacao,
                    setor = proposta.posicao.setor
                )
                Text(
                    proposta.jogadorNome,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Valores
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "OFERTA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Text(
                        formatarSaldo(proposta.valorOfertado),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (proposta.valorOfertado >= proposta.valorMercadoJogador)
                            MoneyPositive else MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "VALOR DE MERCADO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Text(
                        formatarSaldo(proposta.valorMercadoJogador),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Negociação em andamento
            if (proposta.status == StatusProposta.AGUARDANDO_RESPOSTA_IA && proposta.valorSolicitadoJogador > 0L) {
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = LibertadoresBlue.copy(alpha = 0.1f),
                    border = BorderStroke(0.5.dp, LibertadoresBlue.copy(alpha = 0.4f))
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Seu valor solicitado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatarSaldo(proposta.valorSolicitadoJogador),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = LibertadoresBlue
                            )
                        }
                        Text(
                            "Tentativas restantes: ${3 - proposta.tentativasNegociacao}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Botões de ação
            if (proposta.status == StatusProposta.PENDENTE) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Button(
                        onClick = onAceitar,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                        colors = ButtonDefaults.buttonColors(containerColor = PromotionGreen)
                    ) {
                        Text("Aceitar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { mostrarDialogoNegociacao = true },
                        modifier = Modifier.weight(1f),
                        enabled = proposta.tentativasNegociacao < 3,
                        contentPadding = PaddingValues(vertical = Spacing.xs)
                    ) {
                        Text("Negociar", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onRecusar,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            "Recusar",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (mostrarDialogoNegociacao) {
        NegociacaoDialog(
            valorOfertado       = proposta.valorOfertado,
            valorMercado        = proposta.valorMercadoJogador,
            tentativasRestantes = 3 - proposta.tentativasNegociacao,
            onConfirmar         = { valorReais ->
                val valorCentavos = (valorReais * 100L).toLong().coerceAtLeast(proposta.valorOfertado + 1L)
                onNegociar(valorCentavos)
                mostrarDialogoNegociacao = false
            },
            onDismiss = { mostrarDialogoNegociacao = false }
        )
    }
}

@Composable
private fun NegociacaoDialog(
    valorOfertado: Long,
    valorMercado: Long,
    tentativasRestantes: Int,
    onConfirmar: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val ofertaReais  = valorOfertado / 100L
    val mercadoReais = valorMercado / 100L
    var textoValor by remember { mutableStateOf(((ofertaReais * 1.10).toLong()).toString()) }
    val valorLong = textoValor.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Solicitar valor", fontWeight = FontWeight.Bold) },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Informe o valor que deseja receber pela venda (em R$).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Oferta do clube:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                formatarSaldo(valorOfertado),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Valor de mercado:", style = MaterialTheme.typography.labelSmall)
                            Text(formatarSaldo(valorMercado), style = MaterialTheme.typography.labelSmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tentativas restantes:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "$tentativasRestantes",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (tentativasRestantes > 1) MaterialTheme.colorScheme.primary else AmberAccent
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value  = textoValor,
                    onValueChange = { textoValor = it.filter { c -> c.isDigit() } },
                    label  = { Text("Valor solicitado (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (valorLong > ofertaReais) onConfirmar(valorLong) },
                enabled  = valorLong > ofertaReais
            ) {
                Text("Enviar proposta", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun TransferenciaCard(t: br.com.managerfoot.data.dao.TransferenciaDetalhe) {
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
        br.com.managerfoot.data.database.entities.TipoTransferencia.VENDA  -> MoneyPositive
        br.com.managerfoot.data.database.entities.TipoTransferencia.COMPRA -> MoneyNegative
        br.com.managerfoot.data.database.entities.TipoTransferencia.EMPRESTIMO_SAIDA,
        br.com.managerfoot.data.database.entities.TipoTransferencia.EMPRESTIMO_RETORNO -> SulAmericanaTeal
        br.com.managerfoot.data.database.entities.TipoTransferencia.PROMOVIDO_BASE -> PromotionGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    t.jogadorNome,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    TeamBadge(nome = t.origemNome ?: "", escudoRes = t.origemEscudo ?: "", size = 14.dp)
                    Text(
                        t.origemNome ?: "Mercado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 80.dp)
                    )
                    Text("→", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TeamBadge(nome = t.destinoNome ?: "", escudoRes = t.destinoEscudo ?: "", size = 14.dp)
                    Text(
                        t.destinoNome ?: "Mercado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 80.dp)
                    )
                }
                Text(
                    "Temp. ${t.temporadaId} · Mês ${t.mes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(texto = tipoLabel, cor = tipoColor)
                if (t.valor > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatarSaldo(t.valor),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
