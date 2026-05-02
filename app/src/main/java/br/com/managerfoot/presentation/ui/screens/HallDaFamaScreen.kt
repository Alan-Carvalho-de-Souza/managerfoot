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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.R
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

    // Opções por país (preservadas do dev)
    val opcoesBrasil = listOf(
        100 to "Todas",
        1 to "Série A", 2 to "Série B", 3 to "Série C", 4 to "Série D",
        5 to "Copa do Brasil", 6 to "Supercopa Rei"
    )
    val opcoesArgentina = listOf(
        200 to "Todas",
        17 to "Campeão Argentino",
        7 to "Apertura", 8 to "Clausura", 9 to "Copa Argentina", 10 to "Segunda Div."
    )
    val opcoesUruguai = listOf(
        300 to "Todas",
        14 to "Campeão Uruguaio",
        11 to "Apertura", 12 to "Intermediário", 13 to "Clausura",
        15 to "Segunda Divisão", 16 to "Competencia"
    )

    val paisDaDivisao = remember(divisaoSelecionada) {
        when (divisaoSelecionada) {
            in 7..10, 17, 200 -> "Argentina"
            in 11..16, 300 -> "Uruguai"
            0 -> "Todos"
            else -> "Brasil"
        }
    }
    var paisSelecionado by remember(divisaoSelecionada) { mutableStateOf(paisDaDivisao) }

    val opcoesDaDiv = when (paisSelecionado) {
        "Argentina" -> opcoesArgentina
        "Uruguai" -> opcoesUruguai
        "Brasil" -> opcoesBrasil
        else -> emptyList()
    }

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

        // Filtro de país (chips horizontais)
        val paises = listOf("Todos", "Brasil", "Argentina", "Uruguai")
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md, bottom = Spacing.sm)
        ) {
            items(paises) { pais ->
                FilterChipPill(
                    label = if (pais == "Todos") pais else "${bandeiraPais(pais)} $pais",
                    selected = paisSelecionado == pais,
                    onClick = {
                        paisSelecionado = pais
                        val div = when (pais) {
                            "Argentina" -> 200
                            "Brasil" -> 100
                            "Uruguai" -> 300
                            else -> 0
                        }
                        vm.selecionarDivisao(div)
                    }
                )
            }
        }

        // Filtro de competição (chips horizontais) — só quando país específico selecionado
        if (paisSelecionado != "Todos") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md)
            ) {
                items(opcoesDaDiv) { (div, label) ->
                    FilterChipPill(
                        label = label,
                        selected = div == divisaoSelecionada,
                        onClick = { vm.selecionarDivisao(div) }
                    )
                }
            }
        } else {
            Spacer(Modifier.height(Spacing.sm))
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

// ─── Empty state ────────────────────────────────────────────
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

// ─── Card de uma temporada ──────────────────────────────────
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
                                GoldChampion.copy(alpha = 0.12f),
                                GoldChampion.copy(alpha = 0.02f)
                            )
                        )
                    )
            )
            Column(modifier = Modifier.padding(Spacing.lg)) {
                // ── Header: ano gigante + nome do campeonato ──
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                // ── Linha do campeão (destaque dourado) ──
                CampeaoLinha(
                    posicao = "CAMPEÃO",
                    nome = entrada.campeaoNome,
                    escudo = entrada.campeaoEscudo,
                    cor = GoldChampion,
                    medalha = "🥇",
                    forte = true
                )

                // ── Linha do vice (prata) ──
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

                // ── Premiações individuais (Artilheiro / Garçom) ──
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (temArtilheiro) {
                            PremioColuna(
                                modifier = Modifier.weight(1f),
                                painter = painterResource(R.drawable.ic_artilheiro_trophy),
                                label = "Artilheiro",
                                nomeJogador = entrada.artilheiroNomeAbrev.ifEmpty { entrada.artilheiroNome },
                                nomeTime = entrada.artilheiroNomeTime,
                                escudoTime = entrada.artilheiroEscudo,
                                valor = "${entrada.artilheiroGols} gols",
                                accent = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (temArtilheiro && temAssistente) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(96.dp)
                                    .background(GoldChampion.copy(alpha = 0.25f))
                            )
                        }
                        if (temAssistente) {
                            PremioColuna(
                                modifier = Modifier.weight(1f),
                                painter = painterResource(R.drawable.ic_garcom_trophy),
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
                .size(if (forte) 56.dp else 44.dp)
                .clip(CircleShape)
                .background(cor.copy(alpha = 0.20f))
                .border(if (forte) 2.dp else 1.5.dp, cor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            TeamBadge(
                nome = nome,
                escudoRes = escudo,
                size = if (forte) 42.dp else 32.dp
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
private fun PremioColuna(
    modifier: Modifier = Modifier,
    painter: Painter,
    label: String,
    nomeJogador: String,
    nomeTime: String,
    escudoTime: String,
    valor: String,
    accent: Color
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(48.dp)
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            letterSpacing = 1.sp
        )
        Text(
            nomeJogador,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            TeamBadge(nome = nomeTime, escudoRes = escudoTime, size = 14.dp)
            Text(
                nomeTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            valor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

private fun bandeiraPais(pais: String): String = when (pais) {
    "Brasil" -> "🇧🇷"
    "Argentina" -> "🇦🇷"
    "Uruguai" -> "🇺🇾"
    else -> ""
}
