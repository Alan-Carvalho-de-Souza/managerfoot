package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.domain.model.Time
import br.com.managerfoot.presentation.ui.components.FilterChipPill
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.ui.theme.BgPrimary
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.ui.theme.SurfaceCard
import br.com.managerfoot.presentation.viewmodel.InicioUiState
import br.com.managerfoot.presentation.viewmodel.InicioViewModel

private val NOMES_MESES_INICIO = listOf(
    "", "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

// ═══════════════════════════════════════════════════════════
//  InicioScreen
// ═══════════════════════════════════════════════════════════
@Composable
fun InicioScreen(
    onJogoIniciado: (timeId: Int) -> Unit,
    vm: InicioViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is InicioUiState.JogoIniciado) {
            onJogoIniciado((uiState as InicioUiState.JogoIniciado).timeId)
        }
    }

    if (uiState is InicioUiState.SelecionandoTime) {
        SelecionarTimeScreen(
            onTimeSelecionado = { timeId -> vm.iniciarNovoJogo(timeId) }
        )
        return
    }

    // Fundo com gradiente vertical sutil — base do splash
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgPrimary, SurfaceCard, BgPrimary)
                )
            )
    ) {
        when (uiState) {
            is InicioUiState.Carregando -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is InicioUiState.SemSave -> SemSaveContent(
                onIniciar = { vm.iniciarSelecionarTime() }
            )
            is InicioUiState.TemSave -> {
                val saveState by vm.saveState.collectAsState()
                val times by vm.timesDisponiveis.collectAsState()
                val timeId = saveState?.timeIdJogador ?: -1
                val time = times.find { it.id == timeId }
                TemSaveContent(
                    time = time,
                    mes = saveState?.mesAtual ?: 0,
                    ano = saveState?.anoAtual ?: 0,
                    onContinuar = { vm.continuarJogo() },
                    onNovoJogo = { vm.iniciarSelecionarTime() }
                )
            }
            is InicioUiState.Erro -> ErroContent(
                mensagem = (uiState as InicioUiState.Erro).mensagem,
                onTentarNovamente = { vm.iniciarSelecionarTime() }
            )
            else -> Unit
        }
    }
}

// ─── Conteúdo SemSave (primeira vez) ─────────────────────────
@Composable
private fun SemSaveContent(onIniciar: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Watermark com bola estilizada no canto inferior direito
        Icon(
            imageVector = Icons.Filled.SportsSoccer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 80.dp)
                .alpha(0.06f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo ⚽ verde elétrico em círculo
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.SportsSoccer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = "MANAGERFOOT",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Brasil · Sul-América · Mundo",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "Assuma o comando de um clube e conquiste o futebol brasileiro.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.xxl))

            Button(
                onClick = onIniciar,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                contentPadding = PaddingValues(vertical = Spacing.md)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "NOVO JOGO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

// ─── Conteúdo TemSave (jogo em andamento) ───────────────────
@Composable
private fun TemSaveContent(
    time: Time?,
    mes: Int,
    ano: Int,
    onContinuar: () -> Unit,
    onNovoJogo: () -> Unit
) {
    val periodoLabel = if (mes in 1..12 && ano > 0)
        "${NOMES_MESES_INICIO[mes]} de $ano" else ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header compacto com logo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Filled.SportsSoccer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "MANAGERFOOT",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 3.sp
            )
        }

        Spacer(Modifier.weight(1f))

        // Card destacado do clube em andamento
        if (time != null) {
            ClubeContinuarCard(
                time = time,
                periodo = periodoLabel,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Fallback enquanto times ainda não foram carregados
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // Botão primário "Continuar"
        Button(
            onClick = onContinuar,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            contentPadding = PaddingValues(vertical = Spacing.md)
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "CONTINUAR",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
        Spacer(Modifier.height(Spacing.sm))

        // Botão secundário discreto
        TextButton(
            onClick = onNovoJogo,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.AddCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                "Iniciar novo jogo",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun ClubeContinuarCard(
    time: Time,
    periodo: String,
    modifier: Modifier = Modifier
) {
    val serieLabel = when (time.divisao) {
        1 -> "Série A"; 2 -> "Série B"; 3 -> "Série C"; 4 -> "Série D"; 5 -> "Primera Div."; else -> "Série"
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box {
            // Watermark do escudo no canto direito
            TeamBadge(
                nome = time.nome,
                escudoRes = time.escudoRes,
                size = 200.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 50.dp)
                    .alpha(0.07f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 64.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "JOGO EM ANDAMENTO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = time.nome,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(serieLabel)
                            if (periodo.isNotEmpty()) append(" · ").append(periodo)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── Conteúdo Erro ───────────────────────────────────────────
@Composable
private fun ErroContent(mensagem: String, onTentarNovamente: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Erro ao iniciar jogo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            mensagem,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(
            onClick = onTentarNovamente,
            shape = RoundedCornerShape(Radius.md)
        ) {
            Text("Tentar novamente", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  SelecionarTimeScreen
// ═══════════════════════════════════════════════════════════
@Composable
fun SelecionarTimeScreen(
    onTimeSelecionado: (timeId: Int) -> Unit,
    vm: InicioViewModel = hiltViewModel()
) {
    val times by vm.timesDisponiveis.collectAsState()
    var busca by remember { mutableStateOf("") }
    var paisSelecionado by remember { mutableStateOf("Brasil") }
    var divisaoSelecionada by remember { mutableIntStateOf(0) }  // 0 = todas

    val paises = remember(times) { times.map { it.pais }.distinct().sorted() }

    val divisoesDoPais = remember(times, paisSelecionado) {
        times.filter { it.pais == paisSelecionado }.map { it.divisao }.distinct().sorted()
    }

    val filtrados = remember(times, busca, paisSelecionado, divisaoSelecionada) {
        times
            .filter { it.pais == paisSelecionado }
            .filter { divisaoSelecionada == 0 || it.divisao == divisaoSelecionada }
            .let { t -> if (busca.isBlank()) t else t.filter { it.nome.contains(busca, ignoreCase = true) } }
            .sortedBy { it.nome }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header com título
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.xl, bottom = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ESCOLHA SEU CLUBE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "${filtrados.size} clube${if (filtrados.size != 1) "s" else ""} disponível${if (filtrados.size != 1) "is" else ""}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Filtro de país (chips)
        if (paises.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(bottom = Spacing.sm)
            ) {
                items(paises) { pais ->
                    FilterChipPill(
                        label = pais,
                        selected = pais == paisSelecionado,
                        onClick = {
                            paisSelecionado = pais
                            divisaoSelecionada = 0
                            busca = ""
                        }
                    )
                }
            }
        }

        // Filtro de divisão (chips)
        if (divisoesDoPais.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(bottom = Spacing.sm)
            ) {
                item {
                    FilterChipPill(
                        label = "Todas",
                        selected = divisaoSelecionada == 0,
                        onClick = { divisaoSelecionada = 0 }
                    )
                }
                items(divisoesDoPais) { div ->
                    FilterChipPill(
                        label = serieLabelDe(div),
                        selected = divisaoSelecionada == div,
                        onClick = { divisaoSelecionada = div }
                    )
                }
            }
        }

        // Barra de busca
        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            placeholder = { Text("Buscar clube...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.xs, bottom = Spacing.md),
            singleLine = true,
            shape = RoundedCornerShape(Radius.md)
        )

        if (filtrados.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                if (times.isEmpty()) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        "Nenhum clube encontrado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(filtrados) { time ->
                    ClubeCard(
                        time = time,
                        onClick = { onTimeSelecionado(time.id) }
                    )
                }
                item { Spacer(Modifier.height(Spacing.xl)) }
            }
        }
    }
}

@Composable
private fun ClubeCard(time: Time, onClick: () -> Unit) {
    val serieLabel = serieLabelDe(time.divisao)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = time.nome,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${time.cidade} · ${time.estado}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                SerieBadge(label = serieLabel, divisao = time.divisao)
                Text(
                    formatarSaldo(time.saldo),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SerieBadge(label: String, divisao: Int) {
    val cor = corDivisao(divisao)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(cor.copy(alpha = 0.18f))
            .border(0.5.dp, cor.copy(alpha = 0.55f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = cor
        )
    }
}

private fun serieLabelDe(divisao: Int): String = when (divisao) {
    1 -> "Série A"
    2 -> "Série B"
    3 -> "Série C"
    4 -> "Série D"
    5 -> "Primera Div."
    else -> "Série $divisao"
}

private fun corDivisao(divisao: Int): Color = when (divisao) {
    1 -> Color(0xFF00E676)   // verde elétrico — Série A
    2 -> Color(0xFF42A5F5)   // azul — Série B
    3 -> Color(0xFFFFB300)   // âmbar — Série C
    4 -> Color(0xFFFF7043)   // laranja — Série D
    5 -> Color(0xFFAB47BC)   // roxo — Argentina
    else -> Color(0xFF7B8394)
}
