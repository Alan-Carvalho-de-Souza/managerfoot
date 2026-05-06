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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.database.entities.PassagemTecnicoEntity
import br.com.managerfoot.data.database.entities.TecnicoEntity
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.KpiCard
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.HistoricoTecnicoViewModel

// ─────────────────────────────────────────────────────────────
//  HistoricoTecnicoScreen — Tactical Dark
//  Carreira completa do técnico: passagens, totais, KPIs.
// ─────────────────────────────────────────────────────────────
@Composable
fun HistoricoTecnicoScreen(
    tecnicoId: Int,
    onVoltar: () -> Unit,
    vm: HistoricoTecnicoViewModel = hiltViewModel()
) {
    LaunchedEffect(tecnicoId) { vm.carregar(tecnicoId) }

    val tecnico by vm.tecnico.collectAsState()
    val passagens by vm.passagens.collectAsState()
    val carregando by vm.carregando.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = tecnico?.nome ?: "Carreira",
            subtitulo = tecnico?.let { "${it.nacionalidade} · ${it.idade} anos" }
                ?: "Histórico do técnico",
            onVoltar = onVoltar
        )

        when {
            carregando && tecnico == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GreenElectric)
                }
            }
            tecnico == null -> {
                EmptyState("Técnico não encontrado (id=$tecnicoId).")
            }
            else -> {
                val t = tecnico!!
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item { TecnicoHeaderCard(t) }

                    item {
                        Spacer(Modifier.height(Spacing.xs))
                        SectionTitle("Carreira em números")
                    }
                    item { KpiTotaisRow(t) }

                    if (passagens.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(Spacing.xs))
                            SectionTitle("Passagens · ${passagens.size} clube${if (passagens.size != 1) "s" else ""}")
                        }
                        items(passagens.reversed()) { passagem ->
                            PassagemTecnicoCard(passagem)
                        }
                    } else {
                        item {
                            Spacer(Modifier.height(Spacing.xs))
                            SectionTitle("Passagens")
                        }
                        item {
                            EmptyState("Sem passagens registradas. As passagens aparecem aqui após a primeira contratação.")
                        }
                    }
                    item { Spacer(Modifier.height(Spacing.lg)) }
                }
            }
        }
    }
}

@Composable
private fun TecnicoHeaderCard(t: TecnicoEntity) {
    val borderColor = if (t.controladoPorJogador) GreenElectric else GreenMid
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (t.controladoPorJogador) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text("👔", fontSize = 28.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (t.controladoPorJogador) {
                            Surface(
                                shape = RoundedCornerShape(Radius.sm),
                                color = GreenElectric.copy(alpha = 0.18f),
                                border = BorderStroke(0.5.dp, GreenElectric.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(end = Spacing.xs)
                            ) {
                                Text(
                                    "VOCÊ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GreenElectric,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            t.nome,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "${t.idade} anos · ${t.nacionalidade}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = GreenElectric.copy(alpha = 0.18f),
                    border = BorderStroke(0.5.dp, GreenElectric.copy(alpha = 0.5f))
                ) {
                    Text(
                        "${t.pontos} pts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GreenElectric,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiTotaisRow(t: TecnicoEntity) {
    val aproveitamento = "%.0f%%".format(t.aproveitamento)
    val reputacaoCor = when {
        t.reputacao >= 80f -> GoldChampion
        t.reputacao >= 60f -> PromotionGreen
        t.reputacao >= 40f -> GreenElectric
        t.reputacao >= 20f -> AmberAccent
        else               -> RelegationRed
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            KpiCard(
                label = "Jogos",
                valor = "${t.jogos}",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Vitórias",
                valor = "${t.vitorias}",
                accent = PromotionGreen,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Empates",
                valor = "${t.empates}",
                accent = AmberAccent,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Derrotas",
                valor = "${t.derrotas}",
                accent = RelegationRed,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            KpiCard(
                label = "Aproveit.",
                valor = aproveitamento,
                accent = GreenElectric,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Títulos",
                valor = "${t.titulos}",
                accent = GoldChampion,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Reputação",
                valor = "%.0f".format(t.reputacao),
                accent = reputacaoCor,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Pontos",
                valor = "${t.pontos}",
                accent = GreenElectric,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PassagemTecnicoCard(p: PassagemTecnicoEntity) {
    val faixaCor = if (p.titulos > 0) GoldChampion else GreenElectric
    val aproveitamento = if (p.jogos > 0)
        (p.vitorias * 3 + p.empates).toFloat() / (p.jogos * 3) * 100f
    else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, faixaCor.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(faixaCor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (p.timeEscudo.isNotEmpty()) {
                        TeamBadge(nome = p.timeNome, escudoRes = p.timeEscudo, size = 36.dp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            p.timeNome.ifEmpty { "Clube #${p.timeId}" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (p.anoInicio == p.anoFim) "${p.anoInicio}${if (p.ativa) " (atual)" else ""}"
                            else "${p.anoInicio} – ${p.anoFim}${if (p.ativa) " (atual)" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (p.ativa) GreenElectric
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (p.titulos > 0) {
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = GoldChampion.copy(alpha = 0.18f),
                            border = BorderStroke(0.5.dp, GoldChampion.copy(alpha = 0.5f))
                        ) {
                            Text(
                                "🏆 ${p.titulos}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoldChampion,
                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColuna("J", "${p.jogos}")
                    StatColuna("V", "${p.vitorias}", PromotionGreen)
                    StatColuna("E", "${p.empates}", AmberAccent)
                    StatColuna("D", "${p.derrotas}", RelegationRed)
                    StatColuna("PTS", "${p.pontos}", GreenElectric, bold = true)
                    StatColuna("APROV.", "%.0f%%".format(aproveitamento))
                }
            }
        }
    }
}

@Composable
private fun StatColuna(
    label: String,
    valor: String,
    cor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            valor,
            style = MaterialTheme.typography.titleSmall,
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
