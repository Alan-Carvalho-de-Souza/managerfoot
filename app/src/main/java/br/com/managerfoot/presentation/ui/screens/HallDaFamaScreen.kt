package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.database.entities.HallDaFamaEntity
import br.com.managerfoot.presentation.viewmodel.HallDaFamaViewModel
import br.com.managerfoot.presentation.ui.components.TeamBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HallDaFamaScreen(
    onVoltar: () -> Unit,
    vm: HallDaFamaViewModel = hiltViewModel()
) {
    val hallDaFama by vm.hallDaFama.collectAsState()
    val divisaoSelecionada by vm.divisaoSelecionada.collectAsState()

    // Opções por país
    val opcoesBrasil = listOf(
        100 to "Todas",
        1 to "Série A", 2 to "Série B", 3 to "Série C", 4 to "Série D",
        5 to "Copa do Brasil", 6 to "Supercopa Rei"
    )
    val opcoesArgentina = listOf(
        200 to "Todas",
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
            in 7..10, 200  -> "Argentina"
            in 11..16, 300 -> "Uruguai"
            0              -> "Todos"
            else           -> "Brasil"
        }
    }
    var paisSelecionado by remember(divisaoSelecionada) { mutableStateOf(paisDaDivisao) }

    val opcoesDaDiv = when (paisSelecionado) {
        "Argentina" -> opcoesArgentina
        "Uruguai"   -> opcoesUruguai
        "Brasil"    -> opcoesBrasil
        else        -> emptyList()
    }

    fun bandeiraPais(pais: String) = when (pais) {
        "Brasil"    -> "\uD83C\uDDE7\uD83C\uDDF7"
        "Argentina" -> "\uD83C\uDDE6\uD83C\uDDF7"
        "Uruguai"   -> "\uD83C\uDDFA\uD83C\uDDFE"
        else -> ""
    }

    var expandidoComp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hall da Fama") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Chips de país
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val paises = listOf("Todos", "Argentina", "Brasil", "Uruguai")
                paises.forEach { pais ->
                    FilterChip(
                        selected = paisSelecionado == pais,
                        onClick = {
                            paisSelecionado = pais
                            val div = when (pais) {
                                "Argentina" -> 200; "Brasil" -> 100; "Uruguai" -> 300; else -> 0
                            }
                            vm.selecionarDivisao(div)
                            expandidoComp = false
                        },
                        label = {
                            Text(
                                if (pais == "Todos") pais else "${bandeiraPais(pais)} $pais",
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            // Dropdown de competição (só quando país específico selecionado)
            if (paisSelecionado != "Todos") {
                val labelComp = opcoesDaDiv.firstOrNull { it.first == divisaoSelecionada }?.second
                    ?: opcoesDaDiv.firstOrNull()?.second ?: ""
                ExposedDropdownMenuBox(
                    expanded = expandidoComp,
                    onExpandedChange = { expandidoComp = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = labelComp,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Competição") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoComp) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandidoComp,
                        onDismissRequest = { expandidoComp = false }
                    ) {
                        opcoesDaDiv.forEach { (div, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    vm.selecionarDivisao(div)
                                    expandidoComp = false
                                }
                            )
                        }
                    }
                }
            }

            if (hallDaFama.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nenhuma temporada concluída ainda.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(hallDaFama, key = { it.id }) { entrada ->
                        HallDaFamaCard(entrada)
                    }
                }
            }
        }
    }
}

@Composable
private fun HallDaFamaCard(entrada: HallDaFamaEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Título da temporada
            Text(
                text = "${entrada.ano} — ${entrada.nomeCampeonato}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Campeão
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Campeão", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        TeamBadge(nome = entrada.campeaoNome, escudoRes = entrada.campeaoEscudo, size = 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(entrada.campeaoNome, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Vice-campeão (só mostra se tiver)
            if (entrada.viceNome.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Vice-campeão", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            TeamBadge(nome = entrada.viceNome, escudoRes = entrada.viceEscudo, size = 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(entrada.viceNome, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Artilheiro
            if (entrada.artilheiroNome.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Artilheiro", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            TeamBadge(nome = entrada.artilheiroNomeTime, escudoRes = entrada.artilheiroEscudo, size = 20.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${entrada.artilheiroNomeAbrev.ifEmpty { entrada.artilheiroNome }} " +
                                        "(${entrada.artilheiroNomeTime}) — ${entrada.artilheiroGols} gols",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Garçom (mais assistências)
            if (entrada.assistenteNome.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Garçom", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            TeamBadge(nome = entrada.assistenteNomeTime, escudoRes = entrada.assistenteEscudo, size = 20.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${entrada.assistenteNomeAbrev.ifEmpty { entrada.assistenteNome }} " +
                                        "(${entrada.assistenteNomeTime}) — ${entrada.assistenciasTotais} assistências",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
