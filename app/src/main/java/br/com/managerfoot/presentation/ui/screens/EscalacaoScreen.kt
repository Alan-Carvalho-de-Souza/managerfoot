package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.database.entities.EstiloJogo
import br.com.managerfoot.data.database.entities.Posicao
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.presentation.ui.components.*
import br.com.managerfoot.presentation.viewmodel.EscalacaoViewModel
import br.com.managerfoot.presentation.viewmodel.MercadoViewModel

// ═══════════════════════════════════════════════════════════
//  EscalacaoScreen
// ═══════════════════════════════════════════════════════════
@Composable
fun EscalacaoScreen(
    timeId: Int,
    vm: EscalacaoViewModel = hiltViewModel()
) {
    val elenco      by vm.elenco.collectAsState()
    val escalacao   by vm.escalacao.collectAsState()
    val selecionado by vm.jogadorSelecionado.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    var abaAtiva by remember { mutableIntStateOf(0) }
    val abas = listOf("Titulares", "Reservas", "Elenco", "Tática")

    Column(Modifier.fillMaxSize()) {
        // Info da formação atual
        escalacao?.let { esc ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Formação: ${esc.formacaoEfetiva}", fontWeight = FontWeight.Bold)
                    Text("${esc.titulares.size}/11 titulares", style = MaterialTheme.typography.bodySmall)
                }
                val forcaMedia = esc.titulares
                    .map { it.jogador.forcaEfetiva(it.posicaoUsada) }
                    .takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0
                ForcaBadge(forcaMedia)
            }
        }

        TabRow(selectedTabIndex = abaAtiva) {
            abas.forEachIndexed { idx, titulo ->
                Tab(selected = abaAtiva == idx, onClick = { abaAtiva = idx }, text = { Text(titulo) })
            }
        }

        when (abaAtiva) {
            0 -> { // Titulares
                if (escalacao == null) {
                    EmptyState("Carregando escalação...")
                } else {
                    LazyColumn {
                        items(escalacao!!.titulares) { jne ->
                            JogadorRow(
                                jogador = jne.jogador,
                                onClick = { vm.selecionarJogador(jne.jogador) },
                                trailing = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(jne.posicaoUsada.abreviacao, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        if (jne.posicaoUsada != jne.jogador.posicao) {
                                            Text("*Improvisado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        }
                                        IconButton(onClick = { vm.moverParaReserva(jne.jogador) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Remove, contentDescription = "Mover para reserva", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
            1 -> { // Reservas
                if (escalacao?.reservas.isNullOrEmpty()) {
                    EmptyState("Sem reservas escalados")
                } else {
                    LazyColumn {
                        items(escalacao!!.reservas) { jne ->
                            JogadorRow(
                                jogador = jne.jogador,
                                trailing = {
                                    IconButton(onClick = { vm.moverParaTitular(jne.jogador, jne.posicaoUsada) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "Tornar titular", modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
            2 -> { // Elenco completo para arrastar
                LazyColumn {
                    items(elenco) { jogador ->
                        val naEscalacao = escalacao?.titulares?.any { it.jogador.id == jogador.id } == true
                        JogadorRow(
                            jogador = jogador,
                            onClick = { vm.selecionarJogador(jogador) },
                            trailing = {
                                if (!naEscalacao && !jogador.lesionado) {
                                    IconButton(
                                        onClick = { vm.moverParaTitular(jogador, jogador.posicao) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Adicionar aos titulares", modifier = Modifier.size(16.dp))
                                    }
                                } else if (jogador.lesionado) {
                                    Text("Lesão", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
            3 -> { // Tática
                TaticaTab(escalacao = escalacao, vm = vm)
            }
        }
    }

    // Dialog de detalhe do jogador selecionado
    selecionado?.let { jogador ->
        JogadorDetalheDialog(jogador = jogador, onDismiss = { vm.selecionarJogador(null) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaticaTab(
    escalacao: br.com.managerfoot.domain.model.Escalacao?,
    vm: EscalacaoViewModel
) {
    val formacoes = listOf("4-4-2", "4-3-3", "3-5-2", "4-2-3-1", "5-3-2", "4-1-4-1")
    val estilos = EstiloJogo.entries

    val formacaoAtual = escalacao?.time?.taticaFormacao ?: "4-4-2"
    val estiloAtual = escalacao?.time?.estiloJogo ?: EstiloJogo.EQUILIBRADO

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Formação
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Formação tática", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Escolha o esquema tático. Afeta como os jogadores são distribuídos em campo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formacoes.forEach { f ->
                    FilterChip(
                        selected = f == formacaoAtual,
                        onClick = { vm.mudarFormacao(f) },
                        label = { Text(f) }
                    )
                }
            }
        }

        HorizontalDivider()

        // Estilo de jogo
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estilo de jogo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Define a postura tática da equipe. Afeta ataques, defesas e probabilidades de gol.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                estilos.forEach { estilo ->
                    val label = when (estilo) {
                        EstiloJogo.OFENSIVO      -> "⚔️ Ofensivo"
                        EstiloJogo.EQUILIBRADO   -> "⚖️ Equilibrado"
                        EstiloJogo.DEFENSIVO     -> "🛡️ Defensivo"
                        EstiloJogo.CONTRA_ATAQUE -> "⚡ Contra-ataque"
                    }
                    FilterChip(
                        selected = estilo == estiloAtual,
                        onClick = { vm.mudarEstilo(estilo) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun JogadorDetalheDialog(jogador: Jogador, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(jogador.nome) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Posição: ${jogador.posicao.name}", style = MaterialTheme.typography.bodySmall)
                Text("Idade: ${jogador.idade} anos", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                AtributoRow("Força geral", jogador.forca)
                AtributoRow("Técnica", jogador.tecnica)
                AtributoRow("Passe", jogador.passe)
                AtributoRow("Velocidade", jogador.velocidade)
                AtributoRow("Finalização", jogador.finalizacao)
                AtributoRow("Defesa", jogador.defesa)
                AtributoRow("Físico", jogador.fisico)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Salário: ${formatarSaldo(jogador.salario)}/mês", style = MaterialTheme.typography.bodySmall)
                Text("Contrato: ${jogador.contratoAnos} anos", style = MaterialTheme.typography.bodySmall)
                Text("Valor de mercado: ${formatarSaldo(jogador.valorMercado)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun AtributoRow(label: String, valor: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(
                progress = { valor / 99f },
                modifier = Modifier.width(80.dp).height(4.dp),
                color = when {
                    valor >= 80 -> MaterialTheme.colorScheme.primary
                    valor >= 65 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                }
            )
            Text("$valor", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  MercadoScreen
// ═══════════════════════════════════════════════════════════
@Composable
fun MercadoScreen(
    timeId: Int,
    vm: MercadoViewModel = hiltViewModel()
) {
    val livres    by vm.jogadoresLivres.collectAsState()
    val elenco    by vm.elencoAtual.collectAsState()
    val saldo     by vm.saldo.collectAsState()
    val mensagem  by vm.mensagem.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    var abaAtiva by remember { mutableIntStateOf(0) }

    mensagem?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(3000)
            vm.limparMensagem()
        }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
    }

    Column(Modifier.fillMaxSize()) {
        // Saldo disponível
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Saldo disponível", style = MaterialTheme.typography.labelMedium)
            Text(formatarSaldo(saldo), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        TabRow(selectedTabIndex = abaAtiva) {
            Tab(selected = abaAtiva == 0, onClick = { abaAtiva = 0 }, text = { Text("Mercado livre") })
            Tab(selected = abaAtiva == 1, onClick = { abaAtiva = 1 }, text = { Text("Meu elenco") })
        }

        when (abaAtiva) {
            0 -> {
                if (livres.isEmpty()) {
                    EmptyState("Nenhum jogador disponível no mercado")
                } else {
                    LazyColumn {
                        item { SecaoHeader("${livres.size} jogadores livres") }
                        items(livres) { jogador ->
                            JogadorRow(
                                jogador = jogador,
                                trailing = {
                                    val podeContratar = saldo >= jogador.valorMercado
                                    Button(
                                        onClick = { vm.contratarJogador(jogador) },
                                        enabled = podeContratar,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(formatarSaldo(jogador.valorMercado), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
            1 -> {
                LazyColumn {
                    item { SecaoHeader("${elenco.size} jogadores no elenco") }
                    items(elenco) { jogador ->
                        JogadorRow(
                            jogador = jogador,
                            trailing = {
                                OutlinedButton(
                                    onClick = { vm.venderJogador(jogador) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Vender", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
