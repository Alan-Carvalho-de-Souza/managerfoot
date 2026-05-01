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
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.database.entities.HallDaFamaEntity
import br.com.managerfoot.presentation.ui.components.FilterChipPill
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.theme.BronzePlace
import br.com.managerfoot.presentation.ui.theme.GoldChampion
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.SilverRunnerUp
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.viewmodel.HallDaFamaViewModel

@Composable
fun HallDaFamaScreen(
    onVoltar: () -> Unit,
    vm: HallDaFamaViewModel = hiltViewModel()
) {
    val hallDaFama by vm.hallDaFama.collectAsState()
    val divisaoSelecionada by vm.divisaoSelecionada.collectAsState()

    val opcoes = listOf(
        0 to "Todas",
        1 to "Série A",
        2 to "Série B",
        3 to "Série C",
        4 to "Série D",
        5 to "Copa do Brasil"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Hall da Fama",
            subtitulo = if (hallDaFama.isEmpty()) "Nenhuma temporada registrada"
                        else "${hallDaFama.size} título${if (hallDaFama.size != 1) "s" else ""} registrado${if (hallDaFama.size != 1) "s" else ""}",
            onVoltar = onVoltar
        )

        // Filtro de competição (chips horizontais)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md, bottom = Spacing.sm)
        ) {
            items(opcoes) { (div, label) ->
                FilterChipPill(
                    label = label,
                    selected = divisaoSelecionada == div,
                    onClick = { vm.selecionarDivisao(div) }
                )
            }
        }

        if (hallDaFama.isEmpty()) {
            HallDaFamaEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                items(hallDaFama, key = { it.id }) { entrada ->
                    HallDaFamaCard(entrada)
                }
                item { Spacer(Modifier.height(Spacing.lg)) }
            }
        }
    }
}

// ─── Empty state com ícone de troféu ────────────────────────
@Composable
private fun HallDaFamaEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(GoldChampion.copy(alpha = 0.10f))
                .border(2.dp, GoldChampion.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = GoldChampion.copy(alpha = 0.7f),
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "AINDA SEM CONQUISTAS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = GoldChampion,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Conquiste seu primeiro título para começar a construir a história do clube.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
    }
}

// ─── Card de uma temporada do Hall da Fama ──────────────────
@Composable
private fun HallDaFamaCard(entrada: HallDaFamaEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GoldChampion.copy(alpha = 0.4f))
    ) {
        Box {
            // Gradiente dourado sutil de fundo
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                GoldChampion.copy(alpha = 0.10f),
                                GoldChampion.copy(alpha = 0.02f)
                            )
                        )
                    )
            )
            Column(modifier = Modifier.padding(Spacing.lg)) {
                // Header: ano gigante + nome do campeonato
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = entrada.ano.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = GoldChampion,
                        letterSpacing = (-1).sp
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(GoldChampion.copy(alpha = 0.4f))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "TEMPORADA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = GoldChampion.copy(alpha = 0.8f),
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = entrada.nomeCampeonato,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                // Linha do campeão (destaque dourado)
                CampeaoLinha(
                    posicao = "CAMPEÃO",
                    nome = entrada.campeaoNome,
                    escudo = entrada.campeaoEscudo,
                    cor = GoldChampion,
                    medalha = "🥇",
                    forte = true
                )

                // Linha do vice (prata)
                if (entrada.viceNome.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    CampeaoLinha(
                        posicao = "VICE-CAMPEÃO",
                        nome = entrada.viceNome,
                        escudo = entrada.viceEscudo,
                        cor = SilverRunnerUp,
                        medalha = "🥈",
                        forte = false
                    )
                }

                // Premiações individuais (Artilheiro / Garçom)
                val temArtilheiro = entrada.artilheiroNome.isNotEmpty()
                val temAssistente = entrada.assistenteNome.isNotEmpty()
                if (temArtilheiro || temAssistente) {
                    Spacer(Modifier.height(Spacing.md))
                    HorizontalDivider(color = GoldChampion.copy(alpha = 0.3f))
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        "PRÊMIOS INDIVIDUAIS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    if (temArtilheiro) {
                        PremioIndividualLinha(
                            icone = Icons.Filled.SportsSoccer,
                            label = "Artilheiro",
                            nomeJogador = entrada.artilheiroNomeAbrev.ifEmpty { entrada.artilheiroNome },
                            nomeTime = entrada.artilheiroNomeTime,
                            escudoTime = entrada.artilheiroEscudo,
                            valor = "${entrada.artilheiroGols} gols",
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (temAssistente) {
                        if (temArtilheiro) Spacer(Modifier.height(Spacing.sm))
                        PremioIndividualLinha(
                            icone = Icons.Filled.Handshake,
                            label = "Garçom",
                            nomeJogador = entrada.assistenteNomeAbrev.ifEmpty { entrada.assistenteNome },
                            nomeTime = entrada.assistenteNomeTime,
                            escudoTime = entrada.assistenteEscudo,
                            valor = "${entrada.assistenciasTotais} assist.",
                            accent = BronzePlace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CampeaoLinha(
    posicao: String,
    nome: String,
    escudo: String,
    cor: Color,
    medalha: String,
    forte: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(cor.copy(alpha = if (forte) 0.14f else 0.08f))
            .border(0.5.dp, cor.copy(alpha = if (forte) 0.5f else 0.3f), RoundedCornerShape(Radius.md))
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Avatar circular dourado/prata com escudo dentro
        Box(
            modifier = Modifier
                .size(if (forte) 48.dp else 40.dp)
                .clip(CircleShape)
                .background(cor.copy(alpha = 0.20f))
                .border(if (forte) 2.dp else 1.5.dp, cor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            TeamBadge(
                nome = nome,
                escudoRes = escudo,
                size = if (forte) 36.dp else 30.dp
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = posicao,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = cor,
                letterSpacing = 1.5.sp
            )
            Text(
                text = nome,
                style = if (forte) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleSmall,
                fontWeight = if (forte) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = medalha,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun PremioIndividualLinha(
    icone: ImageVector,
    label: String,
    nomeJogador: String,
    nomeTime: String,
    escudoTime: String,
    valor: String,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f))
                .border(0.5.dp, accent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                letterSpacing = 1.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    nomeJogador,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TeamBadge(nome = nomeTime, escudoRes = escudoTime, size = 16.dp)
                Text(
                    nomeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            valor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}
