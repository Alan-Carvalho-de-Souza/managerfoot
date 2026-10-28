package br.com.managerfoot.presentation.ui.screens

import br.com.managerfoot.presentation.ui.theme.fundoApp

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.ForcaBadge
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.ui.components.KpiCard
import br.com.managerfoot.presentation.ui.components.PassagemClubeCard
import br.com.managerfoot.presentation.ui.components.PosicaoBadge
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.HistoricoJogadorViewModel

// ─────────────────────────────────────────────────────────────
//  HistoricoJogadorScreen — Tactical Dark
//  Carreira completa do jogador: passagens, totais, KPIs.
// ─────────────────────────────────────────────────────────────
@Composable
fun HistoricoJogadorScreen(
    jogadorId: Int,
    onVoltar: () -> Unit,
    vm: HistoricoJogadorViewModel = hiltViewModel()
) {
    LaunchedEffect(jogadorId) { vm.carregar(jogadorId) }

    val jogador by vm.jogador.collectAsState()
    val historico by vm.historico.collectAsState()
    val carregando by vm.carregando.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .fundoApp()
    ) {
        ScreenTopBar(
            titulo = jogador?.nome ?: "Carreira",
            subtitulo = jogador?.let { "${it.posicao.abreviacao} · ${it.idade} anos" }
                ?: "Histórico do jogador",
            onVoltar = onVoltar
        )

        when {
            carregando && jogador == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GreenElectric)
                }
            }
            jogador == null -> {
                EmptyState("Jogador não encontrado (id=$jogadorId).")
            }
            else -> {
                // Sempre mostra header + KPIs do jogador. Lista de passagens
                // pode ser vazia (saves antigos sem eventos persistidos).
                val j = jogador!!
                val h = historico ?: br.com.managerfoot.data.repository.HistoricoCarreira(
                    emptyList(), 0, 0, 0, 0, null, null, j.notaMedia
                )
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item { JogadorHeaderCard(jogador = j, historico = h) }

                    item {
                        Spacer(Modifier.height(Spacing.xs))
                        SectionTitle("Carreira em números")
                    }
                    item { KpiTotaisRow(historico = h) }

                    if (h.passagens.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(Spacing.xs))
                            SectionTitle("Passagens · ${h.passagens.size} clube${if (h.passagens.size != 1) "s" else ""}")
                        }
                        items(h.passagens.reversed()) { passagem ->
                            val totalGoalsAssists = passagem.gols + passagem.assistencias
                            val destaque = (passagem.notaMedia != null && passagem.notaMedia >= 7.5f) ||
                                (passagem.partidas > 0 && totalGoalsAssists >= passagem.partidas / 3)
                            PassagemClubeCard(
                                timeNome = passagem.timeNome,
                                escudoRes = passagem.escudoRes,
                                anoInicio = passagem.anoInicio,
                                anoFim = passagem.anoFim,
                                mesInicio = passagem.mesInicio,
                                mesFim = passagem.mesFim,
                                chegada = passagem.chegada,
                                saida = passagem.saida,
                                gols = passagem.gols,
                                assistencias = passagem.assistencias,
                                partidas = passagem.partidas,
                                notaMedia = passagem.notaMedia,
                                destaque = destaque
                            )
                        }
                    } else {
                        item {
                            Spacer(Modifier.height(Spacing.xs))
                            SectionTitle("Passagens")
                        }
                        item {
                            EmptyState(
                                "Histórico de carreira indisponível para este jogador.\n" +
                                "Saves antigos podem não ter eventos persistidos. " +
                                "O histórico será preenchido a partir das próximas partidas."
                            )
                        }
                    }
                    item { Spacer(Modifier.height(Spacing.lg)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card-resumo do jogador no topo (PosicaoBadge + Força + atributos)
// ─────────────────────────────────────────────────────────────
@Composable
private fun JogadorHeaderCard(
    jogador: br.com.managerfoot.domain.model.Jogador,
    historico: br.com.managerfoot.data.repository.HistoricoCarreira
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GreenMid)
    ) {
        Column(
            Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PosicaoBadge(
                    abreviacao = jogador.posicao.abreviacao,
                    setor = jogador.posicao.setor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        jogador.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${jogador.posicao.name.replace("_", " ")} · ${jogador.idade} anos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ForcaBadge(jogador.forca)
            }

            // Lesão (grau + período de afastamento)
            if (jogador.lesionado && jogador.partidasSemJogar > 0) {
                val (grauLabel, grauCor) = when (jogador.grauLesao) {
                    3    -> "Lesão grave"    to RelegationRed
                    2    -> "Lesão moderada" to AmberAccent
                    1    -> "Lesão leve"     to AmberAccent
                    else -> "Lesionado"      to AmberAccent
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.sm),
                    color = grauCor.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, grauCor.copy(alpha = 0.5f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text("🩹", fontSize = 18.sp)
                        Column {
                            Text(
                                grauLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = grauCor
                            )
                            Text(
                                "Fora por mais ${jogador.partidasSemJogar} jogo${if (jogador.partidasSemJogar > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Valor de mercado
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "VALOR DE MERCADO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatarSaldo(jogador.valorMercado),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MoneyPositive
                )
            }

            // Potencial (teto de evolução)
            val pot = if (jogador.potencial in 1..99) jogador.potencial else jogador.forca
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "POTENCIAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (pot > jogador.forca) "$pot · pode evoluir" else "$pot · no auge",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (pot > jogador.forca) MoneyPositive else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Atributos técnicos
            Text(
                "ATRIBUTOS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AtributoMini("TÉC", jogador.tecnica)
                AtributoMini("PAS", jogador.passe)
                AtributoMini("VEL", jogador.velocidade)
                AtributoMini("FIN", jogador.finalizacao)
                AtributoMini("DEF", jogador.defesa)
                AtributoMini("FÍS", jogador.fisico)
            }

            // Linha de info: estreia → último ano
            if (historico.anoEstreia != null && historico.anoUltimo != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "ESTREIA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${historico.anoEstreia}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenElectric
                        )
                    }
                    Text(
                        "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "ÚLTIMA TEMPORADA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${historico.anoUltimo}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenElectric
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "CLUBES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${historico.clubesDiferentes}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AtributoMini(label: String, valor: Int) {
    val cor = when {
        valor >= 80 -> GreenElectric
        valor >= 65 -> PromotionGreen
        valor >= 50 -> AmberAccent
        else        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$valor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─────────────────────────────────────────────────────────────
//  Linha de KPIs: Partidas / Gols / Assist / Nota
// ─────────────────────────────────────────────────────────────
@Composable
private fun KpiTotaisRow(historico: br.com.managerfoot.data.repository.HistoricoCarreira) {
    val mediaPorJogo = if (historico.totalPartidas > 0)
        "%.2f".format(historico.totalGols.toFloat() / historico.totalPartidas)
    else "—"
    val notaCor = when {
        historico.notaMediaAtual >= 8f -> PromotionGreen
        historico.notaMediaAtual >= 6.5f -> GreenElectric
        historico.notaMediaAtual >= 5f -> AmberAccent
        else -> RelegationRed
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            KpiCard(
                label = "Partidas",
                valor = "${historico.totalPartidas}",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Gols",
                valor = "${historico.totalGols}",
                accent = PromotionGreen,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Assistências",
                valor = "${historico.totalAssistencias}",
                accent = LibertadoresBlue,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            KpiCard(
                label = "Gols/jogo",
                valor = mediaPorJogo,
                accent = AmberAccent,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Nota atual",
                valor = "%.2f".format(historico.notaMediaAtual),
                accent = notaCor,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Participações",
                valor = "${historico.totalGols + historico.totalAssistencias}",
                accent = GoldChampion,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
