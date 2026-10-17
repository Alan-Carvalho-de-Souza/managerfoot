package br.com.managerfoot.presentation.ui.screens

import br.com.managerfoot.presentation.ui.theme.fundoApp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.managerfoot.domain.engine.MotorFinanceiro
import br.com.managerfoot.domain.engine.MotorFinanceiro.MetaTemporada
import br.com.managerfoot.domain.model.Time
import br.com.managerfoot.presentation.ui.components.FilterChipPill
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.theme.AmberAccent
import br.com.managerfoot.presentation.ui.theme.GoldChampion
import br.com.managerfoot.presentation.ui.theme.GreenElectric
import br.com.managerfoot.presentation.ui.theme.PromotionGreen
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.SilverRunnerUp
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.viewmodel.MetasViewModel

// ─────────────────────────────────────────────────────────────
//  MetasScreen — meta de temporada de cada time, por país/divisão
// ─────────────────────────────────────────────────────────────
@Composable
fun MetasScreen(
    onVoltar: () -> Unit = {},
    vm: MetasViewModel = hiltViewModel()
) {
    val times by vm.times.collectAsStateWithLifecycle()
    val paisJogador by vm.paisJogador.collectAsStateWithLifecycle()

    // Ordem canônica dos países; só exibe os que têm times carregados.
    val ordemPaises = listOf("Brasil", "Argentina", "Uruguai", "Inglaterra", "Espanha", "Italia")
    val paises = ordemPaises.filter { p -> times.any { it.pais == p } }

    var paisSelecionado by remember(paises, paisJogador) {
        mutableStateOf(paisJogador?.takeIf { it in paises } ?: paises.firstOrNull() ?: "Brasil")
    }

    // Times do país selecionado agrupados por divisão, ordenados por reputação.
    val porDivisao = remember(times, paisSelecionado) {
        times.filter { it.pais == paisSelecionado }
            .groupBy { it.divisao }
            .toSortedMap()
            .mapValues { (_, lista) -> lista.sortedByDescending { it.reputacao } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fundoApp()
    ) {
        ScreenTopBar(
            titulo = "Metas da Temporada",
            subtitulo = "O que se espera de cada clube no ano",
            onVoltar = onVoltar
        )

        if (paises.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md, bottom = Spacing.sm)
            ) {
                items(paises) { pais ->
                    FilterChipPill(
                        label = "${bandeiraPaisMeta(pais)} $pais",
                        selected = pais == paisSelecionado,
                        onClick = { paisSelecionado = pais }
                    )
                }
            }
        } else {
            Spacer(Modifier.height(Spacing.sm))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = Spacing.lg,
                vertical = Spacing.xs
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            porDivisao.forEach { (divisao, lista) ->
                item(key = "sec_$divisao") { SectionTitle(nomeDivisaoMeta(divisao)) }
                items(lista, key = { it.id }) { time ->
                    MetaTimeRow(time)
                }
            }
            item { Spacer(Modifier.height(Spacing.lg)) }
        }
    }
}

@Composable
private fun MetaTimeRow(time: Time) {
    val meta = MotorFinanceiro.calcularMetaTemporada(time)
    val (icone, cor) = estiloMeta(meta)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, cor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    time.nome,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    meta.descricao,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$icone ${meta.titulo}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = cor,
                    maxLines = 1
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
}

private fun estiloMeta(meta: MetaTemporada): Pair<String, Color> = when (meta) {
    MetaTemporada.TITULO             -> "🏆" to GoldChampion
    MetaTemporada.G4                 -> "🥇" to PromotionGreen
    MetaTemporada.ACESSO             -> "⬆️" to PromotionGreen
    MetaTemporada.TOP_10             -> "🔟" to GreenElectric
    MetaTemporada.MEIO_TABELA        -> "🎯" to GreenElectric
    MetaTemporada.FUGIR_REBAIXAMENTO -> "⚠️" to AmberAccent
    MetaTemporada.SOBREVIVER         -> "🛡️" to SilverRunnerUp
}

private fun nomeDivisaoMeta(divisao: Int): String = when (divisao) {
    1  -> "🇧🇷 Brasileiro Série A"
    2  -> "🇧🇷 Brasileiro Série B"
    3  -> "🇧🇷 Brasileiro Série C"
    4  -> "🇧🇷 Brasileiro Série D"
    5  -> "🇦🇷 Primera División (Argentina)"
    6  -> "🇦🇷 Segunda División (Argentina)"
    9  -> "🇺🇾 Primera División (Uruguai)"
    10 -> "🇺🇾 Segunda División (Uruguai)"
    11 -> "🏴󠁧󠁢󠁥󠁮󠁧󠁿 Premier League"
    12 -> "🏴󠁧󠁢󠁥󠁮󠁧󠁿 EFL Championship"
    13 -> "🇪🇸 La Liga"
    14 -> "🇮🇹 Serie A"
    else -> "Divisão $divisao"
}

private fun bandeiraPaisMeta(pais: String): String = when (pais) {
    "Brasil" -> "🇧🇷"
    "Argentina" -> "🇦🇷"
    "Uruguai" -> "🇺🇾"
    "Inglaterra" -> "🏴󠁧󠁢󠁥󠁮󠁧󠁿"
    "Espanha" -> "🇪🇸"
    "Italia" -> "🇮🇹"
    else -> ""
}
