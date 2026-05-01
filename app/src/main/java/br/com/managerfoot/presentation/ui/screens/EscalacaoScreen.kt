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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
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
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.domain.engine.CalculadoraForca
import br.com.managerfoot.domain.model.JogadorNaEscalacao
import br.com.managerfoot.presentation.ui.components.*
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.viewmodel.EscalacaoViewModel
import br.com.managerfoot.presentation.viewmodel.MercadoViewModel

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
    val escalacaoAdversario by vm.escalacaoAdversario.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }
    LaunchedEffect(adversarioId) {
        if (adversarioId > 0) vm.carregarAdversario(adversarioId)
    }

    var abaAtiva by remember { mutableIntStateOf(if (modoPreJogo) 3 else 0) }
    val abas = if (modoPreJogo) listOf("Titulares", "Reservas", "Elenco", "Tática", "Adversário")
               else listOf("Titulares", "Reservas", "Elenco", "Tática")
    var titularParaTroca by remember { mutableStateOf<JogadorNaEscalacao?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            if (modoPreJogo) {
                PreJogoHeader(
                    nomeMeuTime = escalacao?.time?.nome ?: "—",
                    escudoMeuTime = escalacao?.time?.escudoRes ?: "",
                    formacaoMeuTime = escalacao?.time?.taticaFormacao ?: "—",
                    estiloMeuTime = escalacao?.time?.estiloJogo,
                    nomeAdversario = adversario?.nome,
                    escudoAdversario = adversario?.escudoRes ?: "",
                    formacaoAdversario = adversario?.taticaFormacao,
                    estiloAdversario = adversario?.estiloJogo
                )
            } else {
                ScreenTopBar(titulo = "Escalação")
            }

            // KPIs da escalação
            escalacao?.let { esc ->
                EscalacaoStatsRow(
                    formacao = esc.formacaoEfetiva,
                    titularesCount = esc.titulares.size,
                    forcaMedia = esc.titulares
                        .map { it.jogador.forcaEfetiva(it.posicaoUsada) }
                        .takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0
                )
            }

            TabRowPill(
                abas = abas,
                selecionada = abaAtiva,
                onSelecionar = { abaAtiva = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )

            // Banner de troca
            if (titularParaTroca != null) {
                TrocaBanner(
                    nome = titularParaTroca!!.jogador.nomeAbreviado,
                    posicao = titularParaTroca!!.posicaoUsada.abreviacao,
                    onCancelar = { titularParaTroca = null }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (abaAtiva) {
                    0 -> TitularesTab(
                        escalacao = escalacao,
                        titularParaTroca = titularParaTroca,
                        onSelecionarParaTroca = { titularParaTroca = it },
                        onTrocarComOutro = { outro ->
                            vm.trocarPosicoes(titularParaTroca!!, outro)
                            titularParaTroca = null
                        },
                        onAbrirDetalhe = { vm.selecionarJogador(it) },
                        onMoverParaReserva = { vm.moverParaReserva(it) },
                        modoPreJogo = modoPreJogo
                    )
                    1 -> ReservasTab(
                        escalacao = escalacao,
                        titularParaTroca = titularParaTroca,
                        onTrocarComReserva = { reserva ->
                            vm.trocarTitularComReserva(titularParaTroca!!, reserva)
                            titularParaTroca = null
                        },
                        onPromoverTitular = { jne -> vm.moverParaTitular(jne.jogador, jne.posicaoUsada) },
                        onRemoverDoBanco = { vm.removerDaEscalacao(it) },
                        modoPreJogo = modoPreJogo
                    )
                    2 -> ElencoTab(
                        elenco = elenco,
                        escalacao = escalacao,
                        titularParaTroca = titularParaTroca,
                        onSubstituirNaEscalacao = { jogador ->
                            vm.substituirTitularPorElenco(titularParaTroca!!, jogador)
                            titularParaTroca = null
                        },
                        onMoverParaTitular = { vm.moverParaTitular(it, it.posicao) },
                        onAdicionarReserva = { vm.adicionarComoReserva(it) },
                        onAbrirDetalhe = { vm.selecionarJogador(it) },
                        modoPreJogo = modoPreJogo
                    )
                    3 -> TaticaTab(escalacao = escalacao, vm = vm, modoPreJogo = modoPreJogo)
                    4 -> {
                        val esc = escalacaoAdversario
                        if (esc == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            EscalacaoAdversarioPreJogoTab(escalacao = esc)
                        }
                    }
                }
            }
        }

        // Botão flutuante "Iniciar Partida" no modo pré-jogo
        if (modoPreJogo && onIniciarPartida != null) {
            val totalTitulares = escalacao?.titulares?.size ?: 0
            val escalacaoCompleta = totalTitulares == 11
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shadowElevation = 8.dp,
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(Spacing.lg)) {
                    if (!escalacaoCompleta) {
                        val faltam = 11 - totalTitulares
                        Text(
                            "⚠ Adicione mais $faltam jogador${if (faltam > 1) "es" else ""} aos titulares ($totalTitulares/11)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = Spacing.sm)
                        )
                    }
                    Button(
                        onClick = onIniciarPartida,
                        enabled = escalacaoCompleta,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.md),
                        contentPadding = PaddingValues(vertical = Spacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "INICIAR PARTIDA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
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

// ─── Header de pré-jogo ─────────────────────────────────────
@Composable
private fun PreJogoHeader(
    nomeMeuTime: String,
    escudoMeuTime: String,
    formacaoMeuTime: String,
    estiloMeuTime: EstiloJogo?,
    nomeAdversario: String?,
    escudoAdversario: String,
    formacaoAdversario: String?,
    estiloAdversario: EstiloJogo?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            Text(
                "PRÉ-JOGO",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreJogoTimeColumn(
                    nome = nomeMeuTime,
                    escudo = escudoMeuTime,
                    formacao = formacaoMeuTime,
                    estilo = estiloMeuTime,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "VS",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
                if (nomeAdversario != null) {
                    PreJogoTimeColumn(
                        nome = nomeAdversario,
                        escudo = escudoAdversario,
                        formacao = formacaoAdversario ?: "—",
                        estilo = estiloAdversario,
                        accent = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreJogoTimeColumn(
    nome: String,
    escudo: String,
    formacao: String,
    estilo: EstiloJogo?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val estiloLabel = when (estilo) {
        EstiloJogo.OFENSIVO      -> "Ofensivo"
        EstiloJogo.EQUILIBRADO   -> "Equilibrado"
        EstiloJogo.DEFENSIVO     -> "Defensivo"
        EstiloJogo.CONTRA_ATAQUE -> "Contra-ataque"
        null                     -> "—"
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        TeamBadge(nome = nome, escudoRes = escudo, size = 48.dp)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            nome,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            formacao,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            estiloLabel,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Linha de KPIs da escalação ─────────────────────────────
@Composable
private fun EscalacaoStatsRow(formacao: String, titularesCount: Int, forcaMedia: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KpiCard(
            label = "Formação",
            valor = formacao,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            label = "Titulares",
            valor = "$titularesCount/11",
            accent = if (titularesCount == 11) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            label = "Força",
            valor = if (forcaMedia > 0) "$forcaMedia" else "—",
            accent = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─── Banner de troca ────────────────────────────────────────
@Composable
private fun TrocaBanner(nome: String, posicao: String, onCancelar: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "TROCANDO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.sp
                )
                Text(
                    "$nome ($posicao)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onCancelar) {
                Text("Cancelar")
            }
        }
    }
}

// ─── Aba Titulares ──────────────────────────────────────────
@Composable
private fun TitularesTab(
    escalacao: br.com.managerfoot.domain.model.Escalacao?,
    titularParaTroca: JogadorNaEscalacao?,
    onSelecionarParaTroca: (JogadorNaEscalacao) -> Unit,
    onTrocarComOutro: (JogadorNaEscalacao) -> Unit,
    onAbrirDetalhe: (Jogador) -> Unit,
    onMoverParaReserva: (Jogador) -> Unit,
    modoPreJogo: Boolean
) {
    if (escalacao == null) {
        EmptyState("Carregando escalação...")
        return
    }
    val setorOrder = mapOf(Setor.GOLEIRO to 0, Setor.DEFESA to 1, Setor.MEIO to 2, Setor.ATAQUE to 3)
    val titularesOrdenados = escalacao.titulares.sortedWith(
        compareBy({ setorOrder[it.posicaoUsada.setor] ?: 2 }, { it.posicaoUsada.ordinal })
    )

    LazyColumn {
        items(titularesOrdenados, key = { it.jogador.id }) { jne ->
            val eSelecionado = titularParaTroca?.jogador?.id == jne.jogador.id
            JogadorEscalacaoLinha(
                jne = jne,
                onClick = { if (titularParaTroca == null) onAbrirDetalhe(jne.jogador) },
                trailing = {
                    val ehImproviso = jne.posicaoUsada != jne.jogador.posicao &&
                        jne.posicaoUsada != jne.jogador.posicaoSecundaria &&
                        jne.posicaoUsada.setor != jne.jogador.posicao.setor
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        if (ehImproviso) {
                            Text(
                                "Improvisado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        when {
                            eSelecionado -> {
                                OutlinedButton(
                                    onClick = { /* cancela via banner */ },
                                    enabled = false,
                                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                    shape = RoundedCornerShape(Radius.sm)
                                ) {
                                    Text("SELECIONADO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            titularParaTroca != null -> {
                                Button(
                                    onClick = { onTrocarComOutro(jne) },
                                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 2.dp),
                                    shape = RoundedCornerShape(Radius.sm)
                                ) {
                                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(Spacing.xxs))
                                    Text("TROCAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onSelecionarParaTroca(jne) },
                                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                        shape = RoundedCornerShape(Radius.sm),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                    OutlinedButton(
                                        onClick = { onMoverParaReserva(jne.jogador) },
                                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                        shape = RoundedCornerShape(Radius.sm),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Mover para reserva", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                destaque = eSelecionado
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        }
        if (modoPreJogo) item { Spacer(Modifier.height(96.dp)) }
    }
}

// ─── Aba Reservas ───────────────────────────────────────────
@Composable
private fun ReservasTab(
    escalacao: br.com.managerfoot.domain.model.Escalacao?,
    titularParaTroca: JogadorNaEscalacao?,
    onTrocarComReserva: (JogadorNaEscalacao) -> Unit,
    onPromoverTitular: (JogadorNaEscalacao) -> Unit,
    onRemoverDoBanco: (Jogador) -> Unit,
    modoPreJogo: Boolean
) {
    if (escalacao?.reservas.isNullOrEmpty()) {
        EmptyState("Sem reservas escalados")
        return
    }
    val reservasOrdenadas = escalacao!!.reservas
        .sortedWith(compareBy({ it.jogador.posicao.ordinal }, { -it.jogador.forca }))

    LazyColumn {
        items(reservasOrdenadas) { jne ->
            JogadorEscalacaoLinha(
                jne = jne,
                trailing = {
                    if (titularParaTroca != null) {
                        Button(
                            onClick = { onTrocarComReserva(jne) },
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 2.dp),
                            shape = RoundedCornerShape(Radius.sm)
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(Spacing.xxs))
                            Text("TROCAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onPromoverTitular(jne) },
                                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                shape = RoundedCornerShape(Radius.sm),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Tornar titular", modifier = Modifier.size(14.dp))
                            }
                            OutlinedButton(
                                onClick = { onRemoverDoBanco(jne.jogador) },
                                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                shape = RoundedCornerShape(Radius.sm),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remover do banco", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        }
        if (modoPreJogo) item { Spacer(Modifier.height(96.dp)) }
    }
}

// ─── Aba Elenco completo ────────────────────────────────────
@Composable
private fun ElencoTab(
    elenco: List<Jogador>,
    escalacao: br.com.managerfoot.domain.model.Escalacao?,
    titularParaTroca: JogadorNaEscalacao?,
    onSubstituirNaEscalacao: (Jogador) -> Unit,
    onMoverParaTitular: (Jogador) -> Unit,
    onAdicionarReserva: (Jogador) -> Unit,
    onAbrirDetalhe: (Jogador) -> Unit,
    modoPreJogo: Boolean
) {
    val titularesCheios = (escalacao?.titulares?.size ?: 0) >= 11
    val reservasCheias = (escalacao?.reservas?.size ?: 0) >= 11

    LazyColumn {
        items(elenco) { jogador ->
            val naEscalacao = escalacao?.titulares?.any { it.jogador.id == jogador.id } == true
            val naReserva = escalacao?.reservas?.any { it.jogador.id == jogador.id } == true

            JogadorElencoLinha(
                jogador = jogador,
                onClick = { onAbrirDetalhe(jogador) },
                trailing = {
                    when {
                        jogador.lesionado -> StatusChip("Lesão", MaterialTheme.colorScheme.error)
                        naEscalacao -> StatusChip("Titular", MaterialTheme.colorScheme.primary)
                        naReserva -> StatusChip("Reserva", MaterialTheme.colorScheme.secondary)
                        else -> Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.widthIn(max = 130.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (titularParaTroca != null) {
                                Button(
                                    onClick = { onSubstituirNaEscalacao(jogador) },
                                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                    shape = RoundedCornerShape(Radius.sm),
                                    modifier = Modifier.fillMaxWidth().height(28.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(Spacing.xxs))
                                    Text("ESCALAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                if (!titularesCheios) {
                                    OutlinedButton(
                                        onClick = { onMoverParaTitular(jogador) },
                                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                        shape = RoundedCornerShape(Radius.sm),
                                        modifier = Modifier.fillMaxWidth().height(28.dp)
                                    ) {
                                        Text("Titular", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (!reservasCheias) {
                                    OutlinedButton(
                                        onClick = { onAdicionarReserva(jogador) },
                                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                        shape = RoundedCornerShape(Radius.sm),
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        }
        if (modoPreJogo) item { Spacer(Modifier.height(96.dp)) }
    }
}

// ─── Linha de jogador na escalação (titular/reserva) ────────
@Composable
private fun JogadorEscalacaoLinha(
    jne: JogadorNaEscalacao,
    onClick: (() -> Unit)? = null,
    destaque: Boolean = false,
    trailing: @Composable () -> Unit
) {
    val cor = corSetor(jne.posicaoUsada.setor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                if (destaque) MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(cor))
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            PosicaoBadge(abreviacao = jne.posicaoUsada.abreviacao, setor = jne.posicaoUsada.setor)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = jne.jogador.nome,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${jne.jogador.idade} anos · Força ${jne.jogador.forca}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing()
        }
    }
}

// ─── Linha de jogador na aba Elenco (sem posicaoUsada) ──────
@Composable
private fun JogadorElencoLinha(
    jogador: Jogador,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    val cor = corSetor(jogador.posicao.setor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(cor))
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            PosicaoBadge(abreviacao = jogador.posicao.abreviacao, setor = jogador.posicao.setor)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = jogador.nome,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${jogador.idade} anos · Força ${jogador.forca}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing()
        }
    }
}

@Composable
private fun StatusChip(label: String, cor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(cor.copy(alpha = 0.18f))
            .border(0.5.dp, cor.copy(alpha = 0.5f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = cor,
            letterSpacing = 1.sp
        )
    }
}

// ─── Aba Adversário (pré-jogo) ──────────────────────────────
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
    val estiloLabel = when (escalacao.time.estiloJogo) {
        EstiloJogo.OFENSIVO -> "Ofensivo"
        EstiloJogo.EQUILIBRADO -> "Equilibrado"
        EstiloJogo.DEFENSIVO -> "Defensivo"
        EstiloJogo.CONTRA_ATAQUE -> "Contra-ataque"
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                TeamBadge(
                    nome = escalacao.time.nome,
                    escudoRes = escalacao.time.escudoRes,
                    size = 44.dp
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        escalacao.time.nome,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${escalacao.time.taticaFormacao} · $estiloLabel · Força $forcaAdversario",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.pill))
                        .padding(2.dp)
                ) {
                    AdvToggle("Lista", !mostrarGramado) { mostrarGramado = false }
                    AdvToggle("Campo", mostrarGramado) { mostrarGramado = true }
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(titularesOrdenados) { jne ->
                    JogadorEscalacaoLinha(jne = jne, trailing = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun AdvToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Aba Tática ─────────────────────────────────────────────
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
    val formacaoAtual = escalacao?.time?.taticaFormacao ?: "4-4-2"
    val estiloAtual = escalacao?.time?.estiloJogo ?: EstiloJogo.EQUILIBRADO

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "FORMAÇÃO TÁTICA",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                "Escolha o esquema. A melhor escalação é gerada automaticamente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                formacoes.forEach { f ->
                    FilterChip(
                        selected = f == formacaoAtual,
                        onClick = { vm.mudarFormacao(f) },
                        label = {
                            Text(
                                f,
                                fontWeight = if (f == formacaoAtual) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(Radius.sm)
                    )
                }
            }
        }

        if (escalacao != null) {
            GramadoTatico(
                titulares = escalacao.titulares,
                formacao = formacaoAtual,
                modifier = Modifier.padding(vertical = Spacing.xs)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "ESTILO DE JOGO",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                "Define a postura tática da equipe. Afeta ataques, defesas e probabilidades de gol.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EstiloJogo.entries.forEach { estilo ->
                EstiloJogoCardEsc(
                    estilo = estilo,
                    selecionado = estilo == estiloAtual,
                    onClick = { vm.mudarEstilo(estilo) }
                )
            }
        }
        if (modoPreJogo) Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun EstiloJogoCardEsc(estilo: EstiloJogo, selecionado: Boolean, onClick: () -> Unit) {
    val (label, descricao) = when (estilo) {
        EstiloJogo.OFENSIVO      -> "Ofensivo"      to "Pressiona alto, busca o gol mas se expõe atrás."
        EstiloJogo.EQUILIBRADO   -> "Equilibrado"   to "Postura padrão, sem riscos extras."
        EstiloJogo.DEFENSIVO     -> "Defensivo"     to "Recua linhas, foco em segurar o resultado."
        EstiloJogo.CONTRA_ATAQUE -> "Contra-ataque" to "Aguarda o adversário e explora espaços rápidos."
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (selecionado)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selecionado) 1.5.dp else 1.dp,
            color = if (selecionado) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        if (selecionado) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selecionado) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    descricao,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

// ─── Dialog de detalhe do jogador ───────────────────────────
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
                PosicaoBadge(abreviacao = jogador.posicao.abreviacao, setor = jogador.posicao.setor)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        jogador.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${jogador.idade} anos${jogador.posicaoSecundaria?.let { " · alt ${it.abreviacao}" } ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ForcaBadge(jogador.forca)
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Desenvolvimento
                val statusDesenv = when {
                    jogador.categoriaBase    -> Triple("Base", Color(0xFF6A1B9A), "Talento em desenvolvimento")
                    jogador.idade in 16..24  -> Triple("Crescimento", Color(0xFF2E7D32), "Jovem em ascensão")
                    jogador.idade in 25..32  -> Triple("Estabilização", Color(0xFF1565C0), "Auge da carreira")
                    else                     -> Triple("Declínio", Color(0xFFC62828), "Veterano em fim de carreira")
                }
                Text(
                    "DESENVOLVIMENTO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = statusDesenv.second.copy(alpha = 0.18f),
                        border = BorderStroke(0.5.dp, statusDesenv.second.copy(alpha = 0.5f))
                    ) {
                        Text(
                            statusDesenv.first.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusDesenv.second,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                        )
                    }
                    Text(
                        statusDesenv.third,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Barra de momentum
                val prog = jogador.progressoEvolucao.coerceIn(-1f, 1f)
                val progNormalizado = (prog + 1f) / 2f
                val progColor = when {
                    prog >= 0.5f  -> Color(0xFF2E7D32)
                    prog >= 0.1f  -> Color(0xFF558B2F)
                    prog >= -0.1f -> MaterialTheme.colorScheme.onSurfaceVariant
                    prog >= -0.5f -> Color(0xFFE65100)
                    else          -> Color(0xFFC62828)
                }
                val progLabel = when {
                    prog >  0.05f -> "Evoluindo ${"%.2f".format(prog)}/+1.0"
                    prog < -0.05f -> "Regredindo ${"%.2f".format(prog)}/−1.0"
                    else          -> "Estável"
                }
                Text(
                    progLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = progColor,
                    fontWeight = FontWeight.SemiBold
                )
                LinearProgressIndicator(
                    progress = { progNormalizado },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = progColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val notaCor = when {
                            jogador.notaMedia >= 8.0f -> Color(0xFF4CAF50)
                            jogador.notaMedia >= 6.5f -> Color(0xFF2196F3)
                            jogador.notaMedia >= 5.0f -> Color(0xFFFFC107)
                            else -> Color(0xFFF44336)
                        }
                        Text(
                            "%.1f".format(jogador.notaMedia),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = notaCor
                        )
                        Text("Nota média", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${jogador.partidasTemporada}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Partidas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Atributos com StatBar (Fase 1)
                Text(
                    "ATRIBUTOS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                StatBar("Força geral", jogador.forca)
                StatBar("Técnica", jogador.tecnica)
                StatBar("Passe", jogador.passe)
                StatBar("Velocidade", jogador.velocidade)
                StatBar("Finalização", jogador.finalizacao)
                StatBar("Defesa", jogador.defesa)
                StatBar("Físico", jogador.fisico)

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Contrato
                Text(
                    "CONTRATO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                ContratoLinha("Salário", "${formatarSaldo(jogador.salario)}/mês")
                ContratoLinha("Contrato restante", "${jogador.contratoAnos} ano${if (jogador.contratoAnos != 1) "s" else ""}")
                ContratoLinha("Valor de mercado", formatarSaldo(jogador.valorMercado))

                // Aposentadoria
                if (onAposentar != null && jogador.idade in 33..44 && !jogador.aposentado) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = Spacing.xs))
                    if (confirmarAposentadoria) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(
                                "Confirma a aposentadoria de ${jogador.nomeAbreviado}? Esta ação não pode ser desfeita.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                OutlinedButton(
                                    onClick = { confirmarAposentadoria = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(Radius.sm)
                                ) { Text("Cancelar", style = MaterialTheme.typography.labelSmall) }
                                Button(
                                    onClick = {
                                        onAposentar(jogador.id)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(Radius.sm),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) { Text("Aposentar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { confirmarAposentadoria = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.sm),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                "Aposentar jogador",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
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
private fun ContratoLinha(label: String, valor: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            valor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
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
    val livres by vm.jogadoresLivres.collectAsState()
    val elenco by vm.elencoAtual.collectAsState()
    val saldo by vm.saldo.collectAsState()
    val mensagem by vm.mensagem.collectAsState()
    val transferencias by vm.transferencias.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    var abaAtiva by remember { mutableIntStateOf(0) }

    mensagem?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(3000)
            vm.limparMensagem()
        }
        Snackbar(modifier = Modifier.padding(Spacing.lg)) { Text(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Mercado",
            subtitulo = "Saldo ${formatarSaldo(saldo)}",
            onVoltar = onVoltar,
            acoes = {
                TextButton(onClick = onIrParaClubes) {
                    Text("Ver clubes", style = MaterialTheme.typography.labelMedium)
                }
            }
        )

        TabRowPill(
            abas = listOf("Mercado", "Elenco", "Transferências"),
            selecionada = abaAtiva,
            onSelecionar = { abaAtiva = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(vertical = Spacing.md)
        )

        Box(modifier = Modifier.weight(1f)) {
            when (abaAtiva) {
                0 -> {
                    if (livres.isEmpty()) {
                        EmptyState("Nenhum jogador disponível no mercado")
                    } else {
                        LazyColumn {
                            item { SectionTitle("${livres.size} jogadores livres") }
                            items(livres) { jogador ->
                                JogadorElencoLinha(
                                    jogador = jogador,
                                    trailing = {
                                        val podeContratar = saldo >= jogador.valorMercado
                                        Button(
                                            onClick = { vm.contratarJogador(jogador) },
                                            enabled = podeContratar,
                                            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                            shape = RoundedCornerShape(Radius.sm),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(formatarSaldo(jogador.valorMercado), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn {
                        item { SectionTitle("${elenco.size} jogadores no elenco") }
                        items(elenco) { jogador ->
                            JogadorElencoLinha(
                                jogador = jogador,
                                trailing = {
                                    OutlinedButton(
                                        onClick = { vm.venderJogador(jogador) },
                                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                                        shape = RoundedCornerShape(Radius.sm),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Vender", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                        }
                    }
                }
                2 -> {
                    if (transferencias.isEmpty()) {
                        EmptyState("Nenhuma transferência registrada")
                    } else {
                        LazyColumn {
                            item { SectionTitle("${transferencias.size} transferências") }
                            items(transferencias) { t ->
                                TransferenciaRow(t)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                            }
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
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                t.jogadorNome,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val descricao = buildString {
                append(t.origemNome ?: "Mercado livre")
                append(" → ")
                append(t.destinoNome ?: "Mercado livre")
            }
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Temp. ${t.temporadaId} · Mês ${t.mes}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusChip(tipoLabel, tipoColor)
            if (t.valor > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    formatarSaldo(t.valor),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
