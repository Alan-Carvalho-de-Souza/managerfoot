package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.ForcaBadge
import br.com.managerfoot.presentation.ui.components.PosicaoBadge
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SecaoHeader
import br.com.managerfoot.presentation.ui.components.corSetor
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.JunioresViewModel

// ─────────────────────────────────────────────────────────────
//  JunioresScreen — Tactical Dark
//  Base de juniores: promoção e dispensa de jovens talentos
// ─────────────────────────────────────────────────────────────
@Composable
fun JunioresScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: JunioresViewModel = hiltViewModel()
) {
    val juniores by vm.juniores.collectAsStateWithLifecycle()
    val selecionado by vm.jogadorSelecionado.collectAsStateWithLifecycle()
    val mensagem by vm.mensagem.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    LaunchedEffect(mensagem) {
        mensagem?.let {
            snackbarHostState.showSnackbar(it)
            vm.limparMensagem()
        }
    }

    selecionado?.let { jogador ->
        JuniorDetalheDialog(
            jogador    = jogador,
            onFechar   = { vm.selecionarJogador(null) },
            onPromover = {
                vm.promoverJunior(jogador.id, jogador.nomeAbreviado)
                vm.selecionarJogador(null)
            },
            onDispensar = {
                vm.dispensarJunior(jogador.id, jogador.nomeAbreviado)
                vm.selecionarJogador(null)
            }
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                titulo = "Base de Juniores",
                subtitulo = "Talentos em formação",
                onVoltar = onVoltar
            )

            if (juniores.isEmpty()) {
                EmptyState(
                    "Nenhum jogador na base de juniores.\n" +
                    "Aposente jogadores para gerar novos talentos."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        SecaoHeader("${juniores.size} jogador${if (juniores.size != 1) "es" else ""} na base")
                    }
                    items(juniores, key = { it.id }) { jogador ->
                        JuniorCard(
                            jogador = jogador,
                            onClick = { vm.selecionarJogador(jogador) }
                        )
                    }
                    item { Spacer(Modifier.height(Spacing.lg)) }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de jogador júnior (faixa lateral pelo setor + nota+idade)
// ─────────────────────────────────────────────────────────────
@Composable
private fun JuniorCard(
    jogador: Jogador,
    onClick: () -> Unit
) {
    val notaCor = corDaNota(jogador.notaMedia)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
                    Text(
                        jogador.nome,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${jogador.idade} anos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ForcaBadge(jogador.forca)
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = GoldChampion,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "%.1f".format(jogador.notaMedia),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = notaCor
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Dialog de detalhe do jogador júnior
// ─────────────────────────────────────────────────────────────
@Composable
private fun JuniorDetalheDialog(
    jogador: Jogador,
    onFechar: () -> Unit,
    onPromover: () -> Unit,
    onDispensar: () -> Unit
) {
    var confirmarPromocao by remember { mutableStateOf(false) }
    var confirmarDispensa by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PosicaoBadge(jogador.posicao.abreviacao, jogador.posicao.setor)
                Column {
                    Text(
                        jogador.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${jogador.idade} anos · Força ${jogador.forca}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // Nota média e força
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "NOTA MÉDIA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = GoldChampion,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    "%.1f".format(jogador.notaMedia),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = corDaNota(jogador.notaMedia)
                                )
                            }
                        }
                        ForcaBadge(jogador.forca)
                    }
                }

                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "ATRIBUTOS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                JuniorAtributoRow("Técnica",     jogador.tecnica)
                JuniorAtributoRow("Passes",      jogador.passe)
                JuniorAtributoRow("Velocidade",  jogador.velocidade)
                JuniorAtributoRow("Finalização", jogador.finalizacao)
                JuniorAtributoRow("Defesa",      jogador.defesa)
                JuniorAtributoRow("Físico",      jogador.fisico)

                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "CONTRATO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Duração", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${jogador.contratoAnos} ano${if (jogador.contratoAnos != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Salário", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${formatarSaldo(jogador.salario)}/mês",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MoneyNegative
                            )
                        }
                    }
                }

                if (confirmarPromocao) {
                    Spacer(Modifier.height(Spacing.sm))
                    ConfirmacaoCard(
                        mensagem = "Promover ${jogador.nomeAbreviado} ao elenco principal?",
                        cor = PromotionGreen,
                        onCancelar = { confirmarPromocao = false },
                        onConfirmar = onPromover,
                        textoConfirmar = "Confirmar"
                    )
                }

                if (confirmarDispensa) {
                    Spacer(Modifier.height(Spacing.sm))
                    ConfirmacaoCard(
                        mensagem = "Dispensar ${jogador.nomeAbreviado}? Ele irá para o mercado livre.",
                        cor = RelegationRed,
                        onCancelar = { confirmarDispensa = false },
                        onConfirmar = onDispensar,
                        textoConfirmar = "Dispensar"
                    )
                }
            }
        },
        confirmButton = {
            if (!confirmarPromocao && !confirmarDispensa) {
                Button(
                    onClick = { confirmarPromocao = true },
                    shape = RoundedCornerShape(Radius.sm)
                ) {
                    Text("Promover ao Elenco", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!confirmarPromocao && !confirmarDispensa) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(
                        onClick = { confirmarDispensa = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Dispensar") }
                    TextButton(onClick = onFechar) { Text("Fechar") }
                }
            }
        }
    )
}

@Composable
private fun ConfirmacaoCard(
    mensagem: String,
    cor: Color,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit,
    textoConfirmar: String
) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = cor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, cor.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                mensagem,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedButton(
                    onClick = onCancelar,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancelar") }
                Button(
                    onClick = onConfirmar,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = cor)
                ) { Text(textoConfirmar, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun JuniorAtributoRow(label: String, valor: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(96.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { valor / 99f },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = when {
                valor >= 75 -> PromotionGreen
                valor >= 55 -> MaterialTheme.colorScheme.primary
                valor >= 40 -> AmberAccent
                else        -> RelegationRed
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            valor.toString(),
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.width(28.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun corDaNota(nota: Float): Color = when {
    nota >= 7.5f -> PromotionGreen
    nota >= 6.0f -> GreenElectric
    nota >= 5.0f -> AmberAccent
    else         -> RelegationRed
}
