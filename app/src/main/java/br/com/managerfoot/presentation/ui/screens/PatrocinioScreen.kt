package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.domain.engine.MotorPatrocinio
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.viewmodel.PatrocinioViewModel

// ─────────────────────────────────────────────────
//  PatrocinioScreen
// ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrocinioScreen(
    onVoltar: () -> Unit,
    vm: PatrocinioViewModel = hiltViewModel()
) {
    val ofertas             by vm.ofertas.collectAsState()
    val tipoAtual           by vm.patrocinadorAtualTipo.collectAsState()
    val valorAnualAtual     by vm.patrocinadorAtualValorAnual.collectAsState()
    val mensagem            by vm.mensagem.collectAsState()

    LaunchedEffect(Unit) { vm.carregar() }

    mensagem?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2_500)
            vm.limparMensagem()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patrocinadores") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = {
            mensagem?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { vm.limparMensagem() }) { Text("OK") }
                    }
                ) { Text(msg) }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Patrocinador ativo ───────────────────────────────────────
            if (tipoAtual > 0 && valorAnualAtual > 0L) {
                item {
                    PatrocinadorAtivoCard(
                        tipo          = tipoAtual,
                        valorAnual    = valorAnualAtual,
                        ofertas       = ofertas
                    )
                }
                item {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text  = "Outras ofertas disponíveis",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text     = "Nenhum patrocinador ativo esta temporada.\nEscolha um contrato abaixo.",
                            modifier = Modifier.padding(16.dp),
                            color    = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── Ofertas ──────────────────────────────────────────────────
            if (ofertas.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(ofertas) { oferta ->
                    OfertaPatrocinioCard(
                        oferta      = oferta,
                        isSelecionado = tipoAtual == oferta.tipo,
                        onEscolher  = { vm.escolherPatrocinador(oferta.tipo, oferta.valorAnual) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PatrocinadorAtivoCard(
    tipo: Int,
    valorAnual: Long,
    ofertas: List<MotorPatrocinio.OfertaPatrocinio>
) {
    val oferta = ofertas.find { it.tipo == tipo }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = "Patrocinador Ativo",
                style      = MaterialTheme.typography.labelMedium,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = oferta?.nomeEmpresa ?: "—",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = oferta?.tier?.label ?: tierLabel(tipo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Valor anual", style = MaterialTheme.typography.labelSmall)
                    Text(formatarSaldo(valorAnual), fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Por mês", style = MaterialTheme.typography.labelSmall)
                    Text(formatarSaldo(valorAnual / 12L), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun OfertaPatrocinioCard(
    oferta: MotorPatrocinio.OfertaPatrocinio,
    isSelecionado: Boolean,
    onEscolher: () -> Unit
) {
    val borderColor = when {
        isSelecionado    -> MaterialTheme.colorScheme.primary
        !oferta.disponivel -> MaterialTheme.colorScheme.outlineVariant
        else               -> MaterialTheme.colorScheme.outline
    }
    val containerColor = when {
        isSelecionado    -> MaterialTheme.colorScheme.primaryContainer
        !oferta.disponivel -> MaterialTheme.colorScheme.surfaceVariant
        else               -> MaterialTheme.colorScheme.surface
    }

    Card(
        border  = BorderStroke(if (isSelecionado) 2.dp else 1.dp, borderColor),
        colors  = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = oferta.tier.label,
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = if (oferta.disponivel) MaterialTheme.colorScheme.onSurface
                                         else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!oferta.disponivel) {
                            Spacer(Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick  = {},
                                enabled  = false,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(
                                    "Rep. mín. ${oferta.tier.reputacaoMinima}",
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    Text(
                        text  = oferta.nomeEmpresa,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (oferta.disponivel) MaterialTheme.colorScheme.onSurface
                                else Color.Gray
                    )
                    Text(
                        text  = oferta.tier.descricao,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (oferta.disponivel) {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Valor anual", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text       = formatarSaldo(oferta.valorAnual),
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary,
                            style      = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text  = "${formatarSaldo(oferta.valorAnual / 12L)}/mês",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelecionado) {
                        FilledTonalButton(onClick = {}, enabled = false) {
                            Text("Contrato ativo")
                        }
                    } else {
                        Button(onClick = onEscolher) {
                            Text("Escolher")
                        }
                    }
                }
            }
        }
    }
}

private fun tierLabel(tipo: Int) = when (tipo) {
    1    -> "Patrocinador Regional"
    2    -> "Patrocinador Nacional"
    3    -> "Patrocinador Premium"
    else -> "Patrocinador"
}
