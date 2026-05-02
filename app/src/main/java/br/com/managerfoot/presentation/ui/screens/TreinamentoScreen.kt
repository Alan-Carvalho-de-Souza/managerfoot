package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.ui.components.FadigaBadge
import br.com.managerfoot.presentation.ui.components.ForcaBadge
import br.com.managerfoot.presentation.ui.components.PosicaoBadge
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.components.corSetor
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.TreinamentoViewModel

// ─────────────────────────────────────────────────────────────
//  TreinamentoScreen — Tactical Dark
//  Treinos individuais e coletivos, separando Sênior/Base
// ─────────────────────────────────────────────────────────────
@Composable
fun TreinamentoScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: TreinamentoViewModel = hiltViewModel()
) {
    val elenco by vm.elenco.collectAsState()
    val mensagem by vm.mensagem.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    mensagem?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            vm.limparMensagem()
        }
    }

    val pendentes = elenco.count { !it.treinouNestaCiclo && !it.aposentado }
    val total     = elenco.count { !it.aposentado }
    val seniores  = elenco.filter { !it.categoriaBase }
    val juniores  = elenco.filter { it.categoriaBase }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                titulo = "Treinamento",
                subtitulo = if (total > 0) "$pendentes pendentes · $total total" else null,
                onVoltar = onVoltar
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Card descritivo + botão Treinar Time
                item {
                    TreinarTodosCard(
                        pendentes = pendentes,
                        onTreinarTudo = { vm.treinarTudo(timeId) }
                    )
                }

                // Legenda de fadiga
                item { FadigaLegenda() }

                if (seniores.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Spacing.xs))
                        SectionTitle("Elenco Sênior · ${seniores.size}")
                    }
                    items(seniores, key = { it.id }) { jogador ->
                        TreinamentoJogadorCard(
                            jogador = jogador,
                            onTreinar  = { vm.treinar(jogador.id) },
                            onDescansar = { vm.descansar(jogador.id) }
                        )
                    }
                }

                if (juniores.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Spacing.xs))
                        SectionTitle("Base de Juniores · ${juniores.size}")
                    }
                    items(juniores, key = { it.id }) { jogador ->
                        TreinamentoJogadorCard(
                            jogador = jogador,
                            onTreinar  = { vm.treinar(jogador.id) },
                            onDescansar = { vm.descansar(jogador.id) }
                        )
                    }
                }

                item { Spacer(Modifier.height(Spacing.lg)) }
            }
        }

        // Snackbar inferior
        mensagem?.let { msg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Spacing.md),
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, GreenMid),
                shadowElevation = Elev.floating
            ) {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(Spacing.md)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card descritivo + botão "Treinar Time Inteiro"
// ─────────────────────────────────────────────────────────────
@Composable
private fun TreinarTodosCard(
    pendentes: Int,
    onTreinarTudo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = GreenElectric,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Sessão de treino",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "Cada sessão reduz a fadiga em 5% e acumula progresso de evolução. " +
                "Permitido apenas uma sessão por ciclo (entre dois jogos).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onTreinarTudo,
                enabled = pendentes > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    if (pendentes > 0) "Treinar Time Inteiro ($pendentes pendentes)"
                    else "Time já treinou neste ciclo",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de jogador no treinamento (com faixa lateral pelo setor)
// ─────────────────────────────────────────────────────────────
@Composable
private fun TreinamentoJogadorCard(
    jogador: Jogador,
    onTreinar: () -> Unit,
    onDescansar: () -> Unit
) {
    val borderColor = when {
        jogador.treinouNestaCiclo -> PromotionGreen.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Faixa lateral pelo setor
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(corSetor(jogador.posicao.setor))
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PosicaoBadge(
                    abreviacao = jogador.posicao.abreviacao,
                    setor = jogador.posicao.setor
                )
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            jogador.nomeAbreviado,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (jogador.categoriaBase) {
                            TagPill(texto = "BASE", cor = SetorGoleiro)
                        }
                        if (jogador.treinouNestaCiclo) {
                            TagPill(texto = "✓ FEITO", cor = PromotionGreen)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            "${jogador.idade}a",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FadigaBadge(jogador.fadiga)
                        ForcaBadge(jogador.forca)
                    }
                    val prog = jogador.progressoEvolucao.coerceIn(0f, 1f)
                    if (prog > 0f) {
                        Spacer(Modifier.height(Spacing.xs))
                        LinearProgressIndicator(
                            progress = { prog },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = GreenElectric,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Button(
                        onClick = onTreinar,
                        enabled = !jogador.treinouNestaCiclo,
                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(Radius.sm)
                    ) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(Spacing.xxs))
                        Text(
                            if (jogador.treinouNestaCiclo) "Treinado" else "Treinar",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onDescansar,
                        enabled = !jogador.treinouNestaCiclo,
                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(Radius.sm)
                    ) {
                        Text(
                            if (jogador.treinouNestaCiclo) "—" else "Descansar",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Tag pill (BASE / FEITO)
// ─────────────────────────────────────────────────────────────
@Composable
private fun TagPill(texto: String, cor: Color) {
    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = cor.copy(alpha = 0.18f),
        border = BorderStroke(0.5.dp, cor.copy(alpha = 0.5f))
    ) {
        Text(
            texto,
            fontSize = 9.sp,
            color = cor,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Legenda de fadiga (4 níveis Tactical Dark)
// ─────────────────────────────────────────────────────────────
@Composable
private fun FadigaLegenda() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "FADIGA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LegendaItem(cor = Color(0xFF2E7D32), texto = "≥80%")
            LegendaItem(cor = Color(0xFFF9A825), texto = "60–79%")
            LegendaItem(cor = Color(0xFFE65100), texto = "40–59%")
            LegendaItem(cor = Color(0xFFC62828), texto = "<40%")
        }
    }
}

@Composable
private fun LegendaItem(cor: Color, texto: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(cor)
        )
        Text(texto, style = MaterialTheme.typography.labelSmall)
    }
}
