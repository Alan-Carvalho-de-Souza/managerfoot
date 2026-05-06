package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import br.com.managerfoot.data.database.entities.TecnicoEntity
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.RankingTecnicosViewModel

// ─────────────────────────────────────────────────────────────
//  RankingTecnicosScreen — Tactical Dark
//  Ranking global de técnicos: V*5 + E*1 + D*0
// ─────────────────────────────────────────────────────────────
@Composable
fun RankingTecnicosScreen(
    onVoltar: () -> Unit,
    onAbrirHistorico: (tecnicoId: Int) -> Unit = {},
    vm: RankingTecnicosViewModel = hiltViewModel()
) {
    val ranking by vm.ranking.collectAsState()
    val livres by vm.livres.collectAsState()
    val timesPorId by vm.timesPorId.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Ranking de Técnicos",
            subtitulo = "${ranking.size} técnicos · ${livres.size} livres no mercado",
            onVoltar = onVoltar
        )

        if (ranking.isEmpty()) {
            EmptyState("Nenhum técnico cadastrado ainda.")
            return@Column
        }

        // Header da tabela
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#",
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "TÉCNICO",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "CLUBE",
                modifier = Modifier.weight(0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "PTS",
                modifier = Modifier.width(48.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        LazyColumn {
            itemsIndexed(ranking) { idx, tecnico ->
                TecnicoRankingRow(
                    posicao = idx + 1,
                    tecnico = tecnico,
                    time = tecnico.timeId?.let { timesPorId[it] },
                    isOdd = idx % 2 == 0,
                    onClick = { onAbrirHistorico(tecnico.id) }
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            }
            item { Spacer(Modifier.height(Spacing.lg)) }
        }
    }
}

@Composable
private fun TecnicoRankingRow(
    posicao: Int,
    tecnico: TecnicoEntity,
    time: br.com.managerfoot.domain.model.Time?,
    isOdd: Boolean,
    onClick: () -> Unit
) {
    val (medalha, corPos) = when (posicao) {
        1 -> "🥇" to GoldChampion
        2 -> "🥈" to SilverRunnerUp
        3 -> "🥉" to BronzePlace
        else -> "" to MaterialTheme.colorScheme.onSurface
    }
    val rowBg = if (isOdd) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface

    val borderColor = when {
        tecnico.controladoPorJogador -> GreenElectric
        posicao <= 3 -> corPos.copy(alpha = 0.4f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .then(
                if (borderColor != androidx.compose.ui.graphics.Color.Transparent)
                    Modifier
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Posição
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (posicao <= 3) {
                Text(medalha, fontSize = 18.sp)
            } else {
                Text(
                    "$posicao",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        // Nome do técnico (com indicação se é o usuário)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tecnico.controladoPorJogador) {
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
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    tecnico.nome,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (posicao <= 3 || tecnico.controladoPorJogador)
                        FontWeight.Bold else FontWeight.SemiBold,
                    color = if (tecnico.controladoPorJogador) GreenElectric
                            else if (posicao <= 3) corPos
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { onClick() }
                )
            }
            Text(
                "${tecnico.idade}a · ${tecnico.nacionalidade} · Rep ${tecnico.reputacao.toInt()} · J ${tecnico.jogos}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Clube
        Row(
            modifier = Modifier.weight(0.7f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (time != null) {
                TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 18.dp)
                Spacer(Modifier.width(Spacing.xxs))
                Text(
                    time.nome,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = AmberAccent.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, AmberAccent.copy(alpha = 0.5f))
                ) {
                    Text(
                        "🔓 LIVRE",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 1.dp)
                    )
                }
            }
        }
        // Pontos
        Surface(
            shape = RoundedCornerShape(Radius.sm),
            color = if (posicao <= 3) corPos.copy(alpha = 0.18f)
                    else GreenMid,
            border = BorderStroke(
                0.5.dp,
                if (posicao <= 3) corPos.copy(alpha = 0.5f)
                else GreenElectric.copy(alpha = 0.4f)
            ),
            modifier = Modifier.width(48.dp)
        ) {
            Text(
                "${tecnico.pontos}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (posicao <= 3) corPos else GreenElectric,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        }
    }
}

