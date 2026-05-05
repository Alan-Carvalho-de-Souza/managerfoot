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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.database.entities.HallDaFamaEntity
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.components.TrofeuCard
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.ui.util.Trofeus
import br.com.managerfoot.presentation.viewmodel.ConquistasViewModel

// ─────────────────────────────────────────────────────────────
//  ConquistasScreen — Tactical Dark
//  Galeria de troféus conquistados pelo clube.
// ─────────────────────────────────────────────────────────────
@Composable
fun ConquistasScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: ConquistasViewModel = hiltViewModel()
) {
    LaunchedEffect(timeId) { vm.carregar(timeId) }

    val conquistas by vm.conquistas.collectAsState()
    val vices by vm.vices.collectAsState()
    val time by vm.time.collectAsState()

    // Agrupa conquistas por (nomeCampeonato, divisao) preservando o nome real
    // do troféu (resolvido via Trofeus.resolver).
    val agrupadas = remember(conquistas) {
        conquistas
            .groupBy { Trofeus.resolver(it.nomeCampeonato, it.divisao) }
            .toList()
            .sortedByDescending { (_, lista) -> lista.size }  // mais conquistados primeiro
    }

    val totalTitulos = conquistas.size
    val totalVices = vices.size

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = time?.nome ?: "Conquistas",
            subtitulo = if (totalTitulos > 0) "$totalTitulos título${if (totalTitulos != 1) "s" else ""} conquistado${if (totalTitulos != 1) "s" else ""}"
                        else "Sala de troféus",
            onVoltar = onVoltar
        )

        if (conquistas.isEmpty()) {
            // Empty state com escudo do time
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                time?.let {
                    TeamBadge(nome = it.nome, escudoRes = it.escudoRes, size = 80.dp)
                    Spacer(Modifier.height(Spacing.md))
                }
                Text(
                    "Nenhum título conquistado ainda",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Os troféus aparecem aqui ao final de cada campeonato vencido pelo clube.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = Spacing.md,
                vertical = Spacing.sm
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // KPI: total de troféus
            item {
                ResumoConquistasCard(
                    nomeTime = time?.nome ?: "—",
                    escudoRes = time?.escudoRes ?: "",
                    totalTitulos = totalTitulos,
                    totalVices = totalVices,
                    diferentesCampeonatos = agrupadas.size
                )
            }

            // Galeria de troféus em grid 2 colunas
            item {
                Spacer(Modifier.height(Spacing.xs))
                SectionTitle("Galeria de Troféus")
            }

            // Como estamos dentro de LazyColumn, usamos uma estrutura manual
            // de Row de 2 colunas em vez de LazyVerticalGrid (que conflita).
            val pares = agrupadas.chunked(2)
            items(pares) { par ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    par.forEach { (info, listaConq) ->
                        TrofeuCard(
                            iconRes = info.drawable,
                            nomeCampeonato = info.nomeCurto,
                            tier = info.tier,
                            quantidade = listaConq.size,
                            anos = listaConq.map { it.ano },
                            tintable = info.tintable,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(2 - par.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Vice-campeonatos (opcional, só se houver)
            if (vices.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(Spacing.sm))
                    SectionTitle("Vice-campeonatos · ${vices.size}")
                }
                item { ViceList(vices = vices) }
            }

            item { Spacer(Modifier.height(Spacing.lg)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de resumo (totais + escudo do time)
// ─────────────────────────────────────────────────────────────
@Composable
private fun ResumoConquistasCard(
    nomeTime: String,
    escudoRes: String,
    totalTitulos: Int,
    totalVices: Int,
    diferentesCampeonatos: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoldChampion.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            TeamBadge(nome = nomeTime, escudoRes = escudoRes, size = 56.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SALA DE TROFÉUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$totalTitulos",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = GoldChampion
                )
                Text(
                    "$diferentesCampeonatos campeonato${if (diferentesCampeonatos != 1) "s" else ""} diferente${if (diferentesCampeonatos != 1) "s" else ""}" +
                    if (totalVices > 0) " · $totalVices vice${if (totalVices != 1) "s" else ""}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Lista compacta de vice-campeonatos
// ─────────────────────────────────────────────────────────────
@Composable
private fun ViceList(vices: List<HallDaFamaEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        vices.take(10).forEach { vice ->
            val info = Trofeus.resolver(vice.nomeCampeonato, vice.divisao)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.sm),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, SilverRunnerUp.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text("🥈", fontSize = 16.sp)
                    Text(
                        info.nomeCurto,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${vice.ano}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SilverRunnerUp
                    )
                }
            }
        }
        if (vices.size > 10) {
            Text(
                "+${vices.size - 10} outros",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
