package br.com.managerfoot.presentation.ui.screens

import br.com.managerfoot.presentation.ui.theme.fundoApp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.managerfoot.domain.model.Time
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.theme.GreenElectric
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.viewmodel.DashboardViewModel

// ─────────────────────────────────────────────────────────────
//  AmistososScreen — agenda amistosos de entressafra (dez/jan)
//  Compartilha a DashboardViewModel (a simulação abre no Dashboard).
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmistososScreen(
    vm: DashboardViewModel,
    onEscalar: (adversarioId: Int, ehCasa: Boolean) -> Unit,
    onVoltar: () -> Unit
) {
    val janela by vm.amistosoJanela.collectAsStateWithLifecycle()
    val todosOsTimes by vm.todosOsTimes.collectAsStateWithLifecycle()
    val meuTime by vm.timeJogador.collectAsStateWithLifecycle()

    var ehCasa by remember { mutableStateOf(true) }
    var busca by remember { mutableStateOf("") }
    var adversarioSelecionado by remember { mutableStateOf<Time?>(null) }

    val candidatos = remember(todosOsTimes, meuTime, busca) {
        todosOsTimes
            .filter { it.id != (meuTime?.id ?: -1) }
            .filter { busca.isBlank() || it.nome.contains(busca, ignoreCase = true) }
            .sortedByDescending { it.reputacao }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fundoApp()
    ) {
        ScreenTopBar(
            titulo = "Amistosos",
            subtitulo = if (janela.ativa)
                "${if (janela.ehJaneiro) "Janeiro" else "Dezembro"} · ${janela.restantes} restante${if (janela.restantes != 1) "s" else ""}"
            else "Fora do período de amistosos",
            onVoltar = onVoltar
        )

        if (!janela.ativa) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Os amistosos só podem ser marcados na entressafra\n(dezembro, ao fim da temporada, e janeiro, antes do início).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.xl)
                )
            }
            return
        }

        // Mando de campo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            MandoChip("Jogar em casa", ehCasa, Modifier.weight(1f)) { ehCasa = true }
            MandoChip("Jogar fora", !ehCasa, Modifier.weight(1f)) { ehCasa = false }
        }

        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            placeholder = { Text("Buscar adversário...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            items(candidatos, key = { it.id }) { time ->
                val selecionado = adversarioSelecionado?.id == time.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { adversarioSelecionado = time },
                    shape = RoundedCornerShape(Radius.md),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (selecionado) 1.5.dp else 1.dp,
                        if (selecionado) GreenElectric.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 32.dp)
                        Text(
                            time.nome,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Rep. ${time.reputacao.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.xl)) }
        }

        Button(
            onClick = {
                adversarioSelecionado?.let { adv ->
                    onEscalar(adv.id, ehCasa)
                }
            },
            enabled = adversarioSelecionado != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            shape = RoundedCornerShape(Radius.md)
        ) {
            Text(
                adversarioSelecionado?.let { "Escalar e jogar vs ${it.nome}" } ?: "Escolha um adversário",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MandoChip(label: String, selecionado: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        color = if (selecionado) GreenElectric.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selecionado) 1.5.dp else 1.dp,
            if (selecionado) GreenElectric.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
            color = if (selecionado) GreenElectric else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
