package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import br.com.managerfoot.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            in 7..10, 17, 200  -> "Argentina"
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
        containerColor = Color(0xFF0F1115),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1115), titleContentColor = Color.White, navigationIconContentColor = Color.White),
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
                .background(Color(0xFF0F1115))
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
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF1C2026),
                            selectedContainerColor = Color(0xFF1C2026),
                            labelColor = Color(0xFF9BA1A6),
                            selectedLabelColor = Color(0xFFD4AF37)
                        ),
                        border = if (paisSelecionado == pais)
                            BorderStroke(1.dp, Color(0xFFD4AF37))
                        else
                            BorderStroke(1.dp, Color(0xFF2C313A))
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
    val gold          = Color(0xFFD4AF37)
    val cardGradTop   = Color(0xFF252A32)
    val cardGradBot   = Color(0xFF1C2026)
    val borderColor   = Color(0xFF2C313A)
    val textPrimary   = Color.White
    val textSecondary = Color(0xFF9BA1A6)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(cardGradTop, cardGradBot)))
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Título ────────────────────────────────────────────
                Text(
                    text = "${entrada.ano} — ${entrada.nomeCampeonato.uppercase()}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp
                )

                Spacer(Modifier.height(24.dp))

                // ── Troféu ────────────────────────────────────────────
                Text("🏆", fontSize = 48.sp)

                Spacer(Modifier.height(20.dp))

                // ── Escudo do campeão (grande) ────────────────────────
                TeamBadge(
                    nome = entrada.campeaoNome,
                    escudoRes = entrada.campeaoEscudo,
                    size = 120.dp
                )

                Spacer(Modifier.height(16.dp))

                // ── Nome do campeão ───────────────────────────────────
                Text(
                    text = entrada.campeaoNome.uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = gold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )

                // ── Vice-campeão ──────────────────────────────────────
                if (entrada.viceNome.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TeamBadge(nome = entrada.viceNome, escudoRes = entrada.viceEscudo, size = 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Vice-campeão: ${entrada.viceNome}",
                            fontSize = 16.sp,
                            fontStyle = FontStyle.Italic,
                            color = textSecondary
                        )
                    }
                }

                val temStats = entrada.artilheiroNome.isNotEmpty() || entrada.assistenteNome.isNotEmpty()
                if (temStats) {
                    Spacer(Modifier.height(24.dp))

                    HorizontalDivider(color = borderColor)

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Estatísticas de Destaque",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        if (entrada.artilheiroNome.isNotEmpty()) {
                            EstatisticaColuna(
                                modifier = Modifier.weight(1f),
                                painter = painterResource(R.drawable.ic_artilheiro_trophy),
                                iconTint = Color.Unspecified,
                                titulo = "Artilheiro",
                                nome = entrada.artilheiroNomeAbrev.ifEmpty { entrada.artilheiroNome },
                                nomeTime = entrada.artilheiroNomeTime,
                                escudoTime = entrada.artilheiroEscudo,
                                valor = "${entrada.artilheiroGols} gols",
                                gold = gold,
                                textSecondary = textSecondary
                            )
                        }
                        if (entrada.artilheiroNome.isNotEmpty() && entrada.assistenteNome.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(72.dp)
                                    .background(borderColor)
                            )
                        }
                        if (entrada.assistenteNome.isNotEmpty()) {
                            EstatisticaColuna(
                                modifier = Modifier.weight(1f),
                                painter = painterResource(R.drawable.ic_garcom_trophy),
                                iconTint = Color.Unspecified,
                                titulo = "Garçom",
                                nome = entrada.assistenteNomeAbrev.ifEmpty { entrada.assistenteNome },
                                nomeTime = entrada.assistenteNomeTime,
                                escudoTime = entrada.assistenteEscudo,
                                valor = "${entrada.assistenciasTotais} assistências",
                                gold = gold,
                                textSecondary = textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EstatisticaColuna(
    modifier: Modifier = Modifier,
    painter: Painter,
    iconTint: Color = Color.Unspecified,
    titulo: String,
    nome: String,
    nomeTime: String,
    escudoTime: String,
    valor: String,
    gold: Color,
    textSecondary: Color
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(64.dp)
        )
        Text(
            titulo,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            TeamBadge(nome = nomeTime, escudoRes = escudoTime, size = 16.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                nome,
                fontSize = 12.sp,
                color = textSecondary,
                textAlign = TextAlign.Center
            )
        }
        Text(
            "($nomeTime) — $valor",
            fontSize = 12.sp,
            color = textSecondary,
            textAlign = TextAlign.Center
        )
    }
}
