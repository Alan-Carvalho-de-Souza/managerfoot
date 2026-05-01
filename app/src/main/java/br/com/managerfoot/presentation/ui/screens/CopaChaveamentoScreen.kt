package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.dao.CopaPartidaDto
import br.com.managerfoot.domain.engine.MotorCampeonato
import br.com.managerfoot.presentation.ui.components.FilterChipPill
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.theme.GoldChampion
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.viewmodel.CopaChaveamentoViewModel

@Composable
fun CopaChaveamentoScreen(
    copaId: Int,
    timeJogadorId: Int,
    onVoltar: () -> Unit,
    vm: CopaChaveamentoViewModel = hiltViewModel()
) {
    val partidas by vm.partidas.collectAsState()

    LaunchedEffect(copaId) { vm.carregar(copaId) }

    val faseDisponivel = remember(partidas) {
        MotorCampeonato.COPA_FASES.filter { fase -> partidas.any { it.fase == fase } }
    }

    var faseSelecionada by remember(faseDisponivel) {
        mutableStateOf(faseDisponivel.lastOrNull() ?: "")
    }

    // Detecta o campeão (se a Final foi concluída)
    val campeao = remember(partidas) {
        val finais = partidas.filter { it.fase == "Final" }
        if (finais.isEmpty()) null
        else {
            val vencedor = computarVencedorConfronto(finais)
            vencedor
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Copa do Brasil",
            subtitulo = if (campeao != null) "${campeao.nomeVencedor} é o campeão!"
                        else if (faseDisponivel.isNotEmpty()) "Fase atual: ${faseDisponivel.last()}"
                        else "Não iniciada",
            onVoltar = onVoltar
        )

        if (faseDisponivel.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Copa ainda não iniciada",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        // Card de campeão (se existir)
        if (campeao != null) {
            CampeaoCard(
                nomeVencedor = campeao.nomeVencedor,
                escudoVencedor = campeao.escudoVencedor,
                placarFinal = campeao.placarFinal,
                jogadorEhCampeao = campeao.timeIdVencedor == timeJogadorId,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(top = Spacing.md)
            )
        }

        // Filtro de fase com chips horizontais
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md, bottom = Spacing.sm)
        ) {
            items(faseDisponivel) { fase ->
                FilterChipPill(
                    label = fase,
                    selected = faseSelecionada == fase,
                    onClick = { faseSelecionada = fase }
                )
            }
        }

        val confrontosDaFase = remember(partidas, faseSelecionada) {
            partidas.filter { it.fase == faseSelecionada }
                .groupBy { it.confrontoId }
                .values.toList()
                .sortedBy { it.firstOrNull()?.confrontoId ?: 0 }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxSize()
        ) {
            items(confrontosDaFase, key = { it.firstOrNull()?.confrontoId ?: 0 }) { jogos ->
                val ida = jogos.minByOrNull { it.rodada }
                val volta = jogos.maxByOrNull { it.rodada }
                if (ida != null) {
                    ConfrontoCopaCard(
                        ida = ida,
                        volta = volta,
                        timeJogadorId = timeJogadorId,
                        ehFinal = faseSelecionada == "Final"
                    )
                }
            }
            item { Spacer(Modifier.height(Spacing.lg)) }
        }
    }
}

// ─── Card especial de campeão (Final concluída) ─────────────
@Composable
private fun CampeaoCard(
    nomeVencedor: String,
    escudoVencedor: String,
    placarFinal: String,
    jogadorEhCampeao: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, GoldChampion.copy(alpha = 0.6f))
    ) {
        Box {
            // Brilho dourado de fundo
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                GoldChampion.copy(alpha = 0.18f),
                                GoldChampion.copy(alpha = 0.04f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(GoldChampion.copy(alpha = 0.18f))
                        .border(2.dp, GoldChampion, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = GoldChampion,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (jogadorEhCampeao) "VOCÊ É O CAMPEÃO!" else "CAMPEÃO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoldChampion,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = nomeVencedor,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Final: $placarFinal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TeamBadge(nome = nomeVencedor, escudoRes = escudoVencedor, size = 56.dp)
            }
        }
    }
}

// ─── Card de confronto (chave) ──────────────────────────────
@Composable
private fun ConfrontoCopaCard(
    ida: CopaPartidaDto,
    volta: CopaPartidaDto?,
    timeJogadorId: Int,
    ehFinal: Boolean
) {
    val teamAId = ida.timeCasaId
    val teamANome = ida.nomeCasa
    val teamAEscudo = ida.escudoCasa
    val teamBId = ida.timeForaId
    val teamBNome = ida.nomeFora
    val teamBEscudo = ida.escudoFora

    val golsAIda = ida.golsCasa
    val golsBIda = ida.golsFora
    val golsAVolta = volta?.golsFora   // A é visitante na volta
    val golsBVolta = volta?.golsCasa   // B é mandante na volta

    val totalA = (golsAIda ?: 0) + (golsAVolta ?: 0)
    val totalB = (golsBIda ?: 0) + (golsBVolta ?: 0)

    val confrontoCompleto = ida.jogada && (volta?.jogada == true)

    val penBVolta = volta?.penaltisCasa
    val penAVolta = volta?.penaltisForaId
    val foiParaPenaltis = confrontoCompleto && totalA == totalB && penAVolta != null && penBVolta != null
    val teamAVenceuPenaltis = foiParaPenaltis && (penAVolta ?: 0) > (penBVolta ?: 0)
    val teamBVenceuPenaltis = foiParaPenaltis && (penBVolta ?: 0) > (penAVolta ?: 0)

    val teamAVenceu = confrontoCompleto && (totalA > totalB || teamAVenceuPenaltis)
    val teamBVenceu = confrontoCompleto && (totalB > totalA || teamBVenceuPenaltis)

    val jogadorEnvolvido = teamAId == timeJogadorId || teamBId == timeJogadorId
    val accent = if (ehFinal && confrontoCompleto) GoldChampion
                 else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (jogadorEnvolvido || ehFinal) 1.5.dp else 1.dp,
            color = if (jogadorEnvolvido) accent.copy(alpha = 0.6f)
                    else if (ehFinal) GoldChampion.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Faixa lateral colorida
            if (jogadorEnvolvido) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Spacing.md)
            ) {
                // Header: Confronto #N + indicador de "VOCÊ"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (ehFinal) "FINAL" else "CONFRONTO #${ida.confrontoId}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (jogadorEnvolvido) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(accent.copy(alpha = 0.18f))
                                .border(0.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(Radius.sm))
                                .padding(horizontal = Spacing.sm, vertical = 2.dp)
                        ) {
                            Text(
                                "VOCÊ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))

                // Linha principal: Time A | placar | Time B
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeColumn(
                        nome = teamANome,
                        escudo = teamAEscudo,
                        venceu = teamAVenceu,
                        modifier = Modifier.weight(1f)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = Spacing.sm)
                    ) {
                        if (confrontoCompleto || ida.jogada) {
                            Text(
                                "$totalA - $totalB",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "agregado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                        } else {
                            Text(
                                "VS",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TimeColumn(
                        nome = teamBNome,
                        escudo = teamBEscudo,
                        venceu = teamBVenceu,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Mini-cards Ida / Volta com placar
                if (ida.jogada || volta != null) {
                    Spacer(Modifier.height(Spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        JogoCard(
                            label = "IDA",
                            placarCasa = if (ida.jogada) golsAIda else null,
                            placarFora = if (ida.jogada) golsBIda else null,
                            modifier = Modifier.weight(1f)
                        )
                        JogoCard(
                            label = "VOLTA",
                            // Volta: B em casa, A fora — exibimos do POV de A vs B
                            placarCasa = if (volta?.jogada == true) golsAVolta else null,
                            placarFora = if (volta?.jogada == true) golsBVolta else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Banner de pênaltis
                if (foiParaPenaltis) {
                    Spacer(Modifier.height(Spacing.sm))
                    PenaltisBanner(
                        penA = penAVolta ?: 0,
                        penB = penBVolta ?: 0,
                        nomeVencedor = if (teamAVenceuPenaltis) teamANome else teamBNome
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeColumn(
    nome: String,
    escudo: String,
    venceu: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        TeamBadge(nome = nome, escudoRes = escudo, size = 44.dp)
        Text(
            nome,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (venceu) FontWeight.Bold else FontWeight.Medium,
            color = if (venceu) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun JogoCard(
    label: String,
    placarCasa: Int?,
    placarFora: Int?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.sm))
            .padding(vertical = Spacing.xs)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            if (placarCasa != null && placarFora != null) {
                Text(
                    "$placarCasa - $placarFora",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PenaltisBanner(
    penA: Int,
    penB: Int,
    nomeVencedor: String
) {
    val cor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(cor.copy(alpha = 0.10f))
            .border(0.5.dp, cor.copy(alpha = 0.4f), RoundedCornerShape(Radius.sm))
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            imageVector = Icons.Filled.SportsSoccer,
            contentDescription = null,
            tint = cor,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "PÊNALTIS $penA × $penB",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = cor,
                letterSpacing = 1.sp
            )
            Text(
                "$nomeVencedor avançou",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────

private data class VencedorCopa(
    val timeIdVencedor: Int,
    val nomeVencedor: String,
    val escudoVencedor: String,
    val placarFinal: String
)

/** Calcula o vencedor de um confronto de copa (ida + volta + pênaltis). */
private fun computarVencedorConfronto(jogos: List<CopaPartidaDto>): VencedorCopa? {
    if (jogos.isEmpty()) return null
    val ida = jogos.minByOrNull { it.rodada } ?: return null
    val volta = jogos.maxByOrNull { it.rodada }
    if (!ida.jogada) return null
    if (volta == null || !volta.jogada) return null

    val golsAIda = ida.golsCasa ?: 0
    val golsBIda = ida.golsFora ?: 0
    val golsAVolta = volta.golsFora ?: 0
    val golsBVolta = volta.golsCasa ?: 0

    val totalA = golsAIda + golsAVolta
    val totalB = golsBIda + golsBVolta

    val penA = volta.penaltisForaId
    val penB = volta.penaltisCasa

    val (vencedorId, vencedorNome, vencedorEscudo) = when {
        totalA > totalB -> Triple(ida.timeCasaId, ida.nomeCasa, ida.escudoCasa)
        totalB > totalA -> Triple(ida.timeForaId, ida.nomeFora, ida.escudoFora)
        penA != null && penB != null && penA > penB ->
            Triple(ida.timeCasaId, ida.nomeCasa, ida.escudoCasa)
        penA != null && penB != null && penB > penA ->
            Triple(ida.timeForaId, ida.nomeFora, ida.escudoFora)
        else -> return null
    }

    val placarFinal = "$totalA × $totalB" + if (penA != null && penB != null && totalA == totalB) " (pen $penA × $penB)" else ""
    return VencedorCopa(vencedorId, vencedorNome, vencedorEscudo, placarFinal)
}
