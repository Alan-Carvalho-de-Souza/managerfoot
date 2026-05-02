package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import br.com.managerfoot.data.dao.EstatisticaJogadorDto
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.PosicaoBadge
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.components.TabRowPill
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.EstatisticasTimeViewModel
import br.com.managerfoot.presentation.viewmodel.HistoricoTemporada
import br.com.managerfoot.presentation.viewmodel.TemporadaCompeticao

// ─────────────────────────────────────────────────────────────
//  EstatisticasTimeScreen — Tactical Dark
//  Stats da temporada, historico e notas medias do elenco
// ─────────────────────────────────────────────────────────────
@Composable
fun EstatisticasTimeScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: EstatisticasTimeViewModel = hiltViewModel()
) {
    val temporadaStats by vm.temporadaStats.collectAsState()
    val historicoStats by vm.historicoStats.collectAsState()
    val jogadoresHistorico by vm.jogadoresHistorico.collectAsState()
    val notasElenco by vm.notasElenco.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    var abaSelecionada by remember { mutableStateOf(0) }
    val abas = listOf("Temporada", "Histórico", "Notas")

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Estatísticas",
            subtitulo = "Desempenho da equipe",
            onVoltar = onVoltar
        )

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

        when (abaSelecionada) {
            0 -> AbaTemporada(temporadaStats)
            1 -> AbaHistorico(historicoStats, jogadoresHistorico)
            2 -> AbaNotas(notasElenco)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Aba: Temporada atual
// ─────────────────────────────────────────────────────────────
@Composable
private fun AbaTemporada(competicoes: List<TemporadaCompeticao>) {
    if (competicoes.isEmpty()) {
        EmptyState("Nenhuma partida disputada ainda nesta temporada.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = Spacing.md,
            vertical = Spacing.sm
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(competicoes, key = { it.campeonatoId }) { comp ->
            CompeticaoCard(comp)
        }
        item { Spacer(Modifier.height(Spacing.lg)) }
    }
}

@Composable
private fun CompeticaoCard(comp: TemporadaCompeticao) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    comp.nome,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GreenElectric,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (comp.ehCopa && comp.faseAtingida != null) {
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = LibertadoresBlue.copy(alpha = 0.18f),
                        border = BorderStroke(0.5.dp, LibertadoresBlue.copy(alpha = 0.5f))
                    ) {
                        Text(
                            comp.faseAtingida.faseLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = LibertadoresBlue,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EstatStat("J", comp.jogos)
                EstatStat("V", comp.vitorias, cor = PromotionGreen)
                EstatStat("E", comp.empates)
                EstatStat("D", comp.derrotas, cor = RelegationRed)
                EstatStat("GF", comp.golsPro)
                EstatStat("GC", comp.golsContra)
                if (!comp.ehCopa) EstatStat("Pts", comp.pontos, cor = GreenElectric, bold = true)
            }

            if (comp.jogadores.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "JOGADORES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xs))
                JogadorTableHeader()
                comp.jogadores
                    .filter { it.gols > 0 || it.assistencias > 0 || it.partidas > 0 }
                    .forEach { j -> JogadorEstatRow(j) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Aba: Histórico
// ─────────────────────────────────────────────────────────────
@Composable
private fun AbaHistorico(
    historico: List<HistoricoTemporada>,
    jogadoresAllTime: List<EstatisticaJogadorDto>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = Spacing.md,
            vertical = Spacing.sm
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // Colocações históricas
        item {
            SectionTitle("Colocações nas Competições")
            Spacer(Modifier.height(Spacing.xs))
        }

        if (historico.isEmpty()) {
            item {
                Text(
                    "Nenhum histórico disponível ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(historico, key = { it.campeonatoId }) { h ->
                HistoricoCard(h)
            }
        }

        // Jogadores históricos
        item {
            Spacer(Modifier.height(Spacing.sm))
            SectionTitle("Artilheiros & Assistências (All-Time)")
            Spacer(Modifier.height(Spacing.xs))
        }

        if (jogadoresAllTime.isEmpty()) {
            item {
                Text(
                    "Nenhuma estatística disponível.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(Modifier.padding(Spacing.sm)) {
                        JogadorTableHeader()
                        jogadoresAllTime
                            .filter { it.gols > 0 || it.assistencias > 0 }
                            .take(20)
                            .forEach { j -> JogadorEstatRow(j) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Spacing.lg)) }
    }
}

@Composable
private fun HistoricoCard(h: HistoricoTemporada) {
    val tipoLabel = when {
        h.tipo == "COPA_NACIONAL" && h.nomeCampeonato.contains("Supercopa", ignoreCase = true) -> "Supercopa Rei"
        h.tipo == "COPA_NACIONAL" && h.nomeCampeonato.contains("Argentina", ignoreCase = true) -> "Copa da Argentina"
        h.tipo == "COPA_NACIONAL" -> "Copa do Brasil"
        h.tipo == "LIGA_ARGENTINA" -> "Liga Argentina"
        h.tipo == "EXTRANGEIRO_DIVISAO1" && h.nomeCampeonato.contains("Apertura", ignoreCase = true) -> "Apertura"
        h.tipo == "EXTRANGEIRO_DIVISAO1" && h.nomeCampeonato.contains("Clausura", ignoreCase = true) -> "Clausura"
        h.tipo == "EXTRANGEIRO_DIVISAO1" -> "Primera División"
        h.tipo == "EXTRANGEIRO_DIVISAO2" -> "Segunda División"
        h.tipo == "NACIONAL_DIVISAO1" -> "Série A"
        h.tipo == "NACIONAL_DIVISAO2" -> "Série B"
        h.tipo == "NACIONAL_DIVISAO3" -> "Série C"
        h.tipo == "NACIONAL_DIVISAO4" -> "Série D"
        else -> h.nomeCampeonato
    }
    val ano = Regex("\\d{4}").find(h.nomeCampeonato)?.value ?: ""

    // Resultado/posição com cor
    val (resultadoTexto, resultadoCor) = when {
        h.tipo == "COPA_NACIONAL" && h.nomeCampeonato.contains("Supercopa", ignoreCase = true) -> {
            when {
                h.vitorias > 0 -> "Campeão" to GoldChampion
                h.derrotas > 0 -> "Vice"    to SilverRunnerUp
                else           -> "—"        to MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
        h.tipo == "COPA_NACIONAL" -> (h.faseAtingida?.faseLabel() ?: "—") to LibertadoresBlue
        h.tipo == "LIGA_ARGENTINA" -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> {
            val pos = "${h.posicao}º"
            val cor = when {
                h.posicao == 1 -> GoldChampion
                h.posicao == 2 -> SilverRunnerUp
                h.posicao == 3 -> BronzePlace
                h.posicao <= 6 -> LibertadoresBlue
                h.posicao <= 12 -> SulAmericanaTeal
                else -> MaterialTheme.colorScheme.onSurface
            }
            pos to cor
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, resultadoCor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tipoLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ano.isNotEmpty()) {
                    Text(
                        ano,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Resultado/posição em surface tonal colorida
            Surface(
                shape = RoundedCornerShape(Radius.sm),
                color = resultadoCor.copy(alpha = 0.15f),
                border = BorderStroke(0.5.dp, resultadoCor.copy(alpha = 0.5f))
            ) {
                Text(
                    resultadoTexto,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = resultadoCor,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                EstatStat("V", h.vitorias, cor = PromotionGreen)
                EstatStat("E", h.empates)
                EstatStat("D", h.derrotas, cor = RelegationRed)
                if (h.tipo != "COPA_NACIONAL") EstatStat("Pts", h.pontos, cor = GreenElectric, bold = true)
                if (h.tipo == "LIGA_ARGENTINA") EstatStat("J", h.jogos)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Aba: Notas médias do elenco
// ─────────────────────────────────────────────────────────────
@Composable
private fun AbaNotas(jogadores: List<Jogador>) {
    if (jogadores.isEmpty()) {
        EmptyState("Nenhum jogador com partidas disputadas ainda.")
        return
    }

    val mediaGeral = jogadores.map { it.notaMedia }.average().toFloat()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = Spacing.md,
            vertical = Spacing.sm
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // Card de média geral (verde elétrico destacado)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, GreenMid)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "MÉDIA GERAL DO ELENCO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Baseado em ${jogadores.size} jogadores",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        "%.2f".format(mediaGeral),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = corDaNotaEstat(mediaGeral)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Spacing.xs)) }

        // Header da tabela
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "JOGADOR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "P",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    "NOTA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        items(jogadores, key = { it.id }) { j ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    PosicaoBadge(
                        abreviacao = j.posicao.abreviacao,
                        setor = j.posicao.setor
                    )
                    Text(
                        j.nomeAbreviado,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    j.partidasTemporada.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    "%.2f".format(j.notaMedia),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = corDaNotaEstat(j.notaMedia),
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
        }
        item { Spacer(Modifier.height(Spacing.lg)) }
    }
}

// ─────────────────────────────────────────────────────────────
//  Componentes reutilizáveis
// ─────────────────────────────────────────────────────────────
@Composable
private fun EstatStat(
    label: String,
    valor: Int,
    cor: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            valor.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = cor
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun JogadorTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "JOGADOR",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        listOf("J", "G", "A").forEach { col ->
            Text(
                col,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center
            )
        }
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
}

@Composable
private fun JogadorEstatRow(j: EstatisticaJogadorDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            j.nomeAbrev,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            j.partidas.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center
        )
        Text(
            j.gols.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (j.gols > 0) FontWeight.Bold else FontWeight.Normal,
            color = if (j.gols > 0) GreenElectric else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center
        )
        Text(
            j.assistencias.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (j.assistencias > 0) FontWeight.SemiBold else FontWeight.Normal,
            color = if (j.assistencias > 0) LibertadoresBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center
        )
    }
}

private fun String.faseLabel() = when (this) {
    "Primeira Fase" -> "1ª Fase"
    "Segunda Fase"  -> "2ª Fase"
    "Terceira Fase" -> "3ª Fase"
    "Quarta Fase"   -> "4ª Fase"
    "Oitavas"       -> "Oitavas"
    "Quartas"       -> "Quartas"
    "Semi"          -> "Semifinal"
    "Final"         -> "Final"
    else            -> this
}

private fun corDaNotaEstat(nota: Float): Color = when {
    nota >= 8.0f -> PromotionGreen
    nota >= 6.5f -> GreenElectric
    nota >= 5.0f -> AmberAccent
    else         -> RelegationRed
}
