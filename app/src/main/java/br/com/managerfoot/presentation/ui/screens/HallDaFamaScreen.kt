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
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.database.entities.HallDaFamaEntity
import br.com.managerfoot.presentation.viewmodel.HallDaFamaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HallDaFamaScreen(
    onVoltar: () -> Unit,
    vm: HallDaFamaViewModel = hiltViewModel()
) {
    val hallDaFama by vm.hallDaFama.collectAsState()

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
        if (hallDaFama.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
                Text("🏆", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Campeão", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(entrada.campeaoNome, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold)
                }
            }

            // Vice-campeão (só mostra se tiver)
            if (entrada.viceNome.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🥈", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Vice-campeão", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(entrada.viceNome, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Artilheiro
            if (entrada.artilheiroNome.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚽", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Artilheiro", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${entrada.artilheiroNomeAbrev.ifEmpty { entrada.artilheiroNome }} " +
                                    "(${entrada.artilheiroNomeTime}) — ${entrada.artilheiroGols} gols",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Garçom (mais assistências)
            if (entrada.assistenteNome.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Garçom", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
