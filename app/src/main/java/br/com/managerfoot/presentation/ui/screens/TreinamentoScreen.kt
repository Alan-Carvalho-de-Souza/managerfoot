package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.ui.components.FadigaBadge
import br.com.managerfoot.presentation.ui.components.ForcaBadge
import br.com.managerfoot.presentation.viewmodel.TreinamentoViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Treinamento") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = {
            mensagem?.let {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.inverseSurface
                        )
                    ) {
                        Text(
                            it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Legenda de fadiga
            item {
                FadigaLegenda()
            }

            // Descrição + botão Treinar Time
            item {
                val pendentes = elenco.count { !it.treinouNestaCiclo && !it.aposentado }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Cada sessão de treino reduz a fadiga em 5% e acumula progresso de evolução. Permitido apenas uma sessão por ciclo (entre dois jogos).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Button(
                            onClick = { vm.treinarTudo(timeId) },
                            enabled = pendentes > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (pendentes > 0) "Treinar Time Inteiro ($pendentes pendentes)"
                                else "Time já treinou neste ciclo"
                            )
                        }
                    }
                }
            }

            val seniores = elenco.filter { !it.categoriaBase }
            val juniores = elenco.filter { it.categoriaBase }

            if (seniores.isNotEmpty()) {
                item {
                    Text(
                        "Elenco Sênior",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
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
                    Text(
                        "Base de Juniores",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(juniores, key = { it.id }) { jogador ->
                    TreinamentoJogadorCard(
                        jogador = jogador,
                        onTreinar  = { vm.treinar(jogador.id) },
                        onDescansar = { vm.descansar(jogador.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TreinamentoJogadorCard(
    jogador: Jogador,
    onTreinar: () -> Unit,
    onDescansar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ForcaBadge(jogador.forca)
            Spacer(Modifier.width(8.dp))
            FadigaBadge(jogador.fadiga)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = jogador.nomeAbreviado,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (jogador.categoriaBase) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF7B1FA2))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("BASE", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (jogador.treinouNestaCiclo) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF388E3C))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("✓ FEITO", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = "${jogador.posicao.abreviacao} · ${jogador.idade} anos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Barra de progresso de evolução
                val prog = jogador.progressoEvolucao.coerceIn(0f, 1f)
                if (prog > 0f) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { prog },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onTreinar,
                    enabled = !jogador.treinouNestaCiclo,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (jogador.treinouNestaCiclo) "Treinado" else "Treinar",
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(
                    onClick = onDescansar,
                    enabled = !jogador.treinouNestaCiclo,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (jogador.treinouNestaCiclo) "—" else "Descansar",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FadigaLegenda() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Fadiga:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            LegendaItem(cor = Color(0xFF2E7D32), texto = "≥80%")
            LegendaItem(cor = Color(0xFFF9A825), texto = "60–79%")
            LegendaItem(cor = Color(0xFFE65100), texto = "40–59%")
            LegendaItem(cor = Color(0xFFC62828), texto = "<40%")
        }
    }
}

@Composable
private fun LegendaItem(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(cor)
        )
        Text(texto, style = MaterialTheme.typography.labelSmall)
    }
}
