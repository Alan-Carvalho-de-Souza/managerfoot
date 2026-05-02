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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.domain.engine.MotorPatrocinio
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.PatrocinioViewModel

// ─────────────────────────────────────────────────────────────
//  PatrocinioScreen — Tactical Dark
//  Contratos de patrocinio (Regional / Nacional / Premium)
// ─────────────────────────────────────────────────────────────
@Composable
fun PatrocinioScreen(
    onVoltar: () -> Unit,
    vm: PatrocinioViewModel = hiltViewModel()
) {
    val ofertas         by vm.ofertas.collectAsState()
    val tipoAtual       by vm.patrocinadorAtualTipo.collectAsState()
    val valorAnualAtual by vm.patrocinadorAtualValorAnual.collectAsState()
    val mensagem        by vm.mensagem.collectAsState()

    LaunchedEffect(Unit) { vm.carregar() }

    mensagem?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2_500)
            vm.limparMensagem()
        }
    }

    val temAtivo = tipoAtual > 0 && valorAnualAtual > 0L

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                titulo = "Patrocinadores",
                subtitulo = if (temAtivo) "Contrato ativo · ${formatarSaldo(valorAnualAtual)}/ano"
                            else "Sem contrato ativo",
                onVoltar = onVoltar
            )

            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxSize()
            ) {
                // Patrocinador ativo OU aviso
                if (temAtivo) {
                    item {
                        PatrocinadorAtivoCard(
                            tipo = tipoAtual,
                            valorAnual = valorAnualAtual,
                            ofertas = ofertas
                        )
                    }
                    item {
                        Spacer(Modifier.height(Spacing.xs))
                        SectionTitle("Outras ofertas disponíveis")
                    }
                } else {
                    item { SemContratoCard() }
                    item {
                        Spacer(Modifier.height(Spacing.xs))
                        SectionTitle("Ofertas recebidas")
                    }
                }

                // Lista de ofertas
                if (ofertas.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(Spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GreenElectric)
                        }
                    }
                } else {
                    items(ofertas) { oferta ->
                        OfertaPatrocinioCard(
                            oferta = oferta,
                            isSelecionado = tipoAtual == oferta.tipo,
                            onEscolher = { vm.escolherPatrocinador(oferta.tipo, oferta.valorAnual) }
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.limparMensagem() }) {
                        Text("OK", color = GreenElectric, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card do patrocinador ativo (verde elétrico destacado)
// ─────────────────────────────────────────────────────────────
@Composable
private fun PatrocinadorAtivoCard(
    tipo: Int,
    valorAnual: Long,
    ofertas: List<MotorPatrocinio.OfertaPatrocinio>
) {
    val oferta = ofertas.find { it.tipo == tipo }
    val tierCor = corDoTier(tipo)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, GreenElectric)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = GreenElectric.copy(alpha = 0.18f),
                    border = BorderStroke(0.5.dp, GreenElectric.copy(alpha = 0.5f))
                ) {
                    Text(
                        "✓ CONTRATO ATIVO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GreenElectric,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = tierCor.copy(alpha = 0.18f),
                    border = BorderStroke(0.5.dp, tierCor.copy(alpha = 0.5f))
                ) {
                    Text(
                        oferta?.tier?.label ?: tierLabel(tipo),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tierCor,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            Text(
                oferta?.nomeEmpresa ?: "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(Spacing.sm))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "VALOR ANUAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatarSaldo(valorAnual),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MoneyPositive
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "POR MÊS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatarSaldo(valorAnual / 12L),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MoneyPositive
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de aviso "Sem contrato ativo"
// ─────────────────────────────────────────────────────────────
@Composable
private fun SemContratoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text("⚠️", fontSize = 24.sp)
            Column {
                Text(
                    "Sem patrocinador ativo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AmberAccent
                )
                Text(
                    "Esta temporada está sem contrato. Escolha uma oferta abaixo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de uma oferta de patrocínio
// ─────────────────────────────────────────────────────────────
@Composable
private fun OfertaPatrocinioCard(
    oferta: MotorPatrocinio.OfertaPatrocinio,
    isSelecionado: Boolean,
    onEscolher: () -> Unit
) {
    val tierCor = corDoTier(oferta.tipo)
    val borderColor = when {
        isSelecionado     -> GreenElectric
        !oferta.disponivel -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        else               -> tierCor.copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (!oferta.disponivel) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (isSelecionado) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            // Header: tier + nome empresa + chip de bloqueio
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = tierCor.copy(alpha = if (oferta.disponivel) 0.18f else 0.08f),
                    border = BorderStroke(
                        0.5.dp,
                        tierCor.copy(alpha = if (oferta.disponivel) 0.5f else 0.25f)
                    )
                ) {
                    Text(
                        oferta.tier.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tierCor.copy(alpha = if (oferta.disponivel) 1f else 0.5f),
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                    )
                }
                if (!oferta.disponivel) {
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = AmberAccent.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, AmberAccent.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "🔒 Rep. mín. ${oferta.tier.reputacaoMinima}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberAccent,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            Text(
                oferta.nomeEmpresa,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (oferta.disponivel) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                oferta.tier.descricao,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (oferta.disponivel) {
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "VALOR ANUAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatarSaldo(oferta.valorAnual),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelecionado) GreenElectric else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${formatarSaldo(oferta.valorAnual / 12L)}/mês",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelecionado) {
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = GreenElectric.copy(alpha = 0.18f),
                            border = BorderStroke(0.5.dp, GreenElectric.copy(alpha = 0.5f))
                        ) {
                            Text(
                                "✓ ATIVO",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenElectric,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                            )
                        }
                    } else {
                        Button(
                            onClick = onEscolher,
                            shape = RoundedCornerShape(Radius.sm),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tierCor
                            )
                        ) {
                            Text("Escolher", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────
private fun tierLabel(tipo: Int) = when (tipo) {
    1    -> "Patrocinador Regional"
    2    -> "Patrocinador Nacional"
    3    -> "Patrocinador Premium"
    else -> "Patrocinador"
}

/** Cor por tier — Regional=teal, Nacional=azul, Premium=ouro */
private fun corDoTier(tipo: Int): Color = when (tipo) {
    1    -> SulAmericanaTeal
    2    -> LibertadoresBlue
    3    -> GoldChampion
    else -> SulAmericanaTeal
}
