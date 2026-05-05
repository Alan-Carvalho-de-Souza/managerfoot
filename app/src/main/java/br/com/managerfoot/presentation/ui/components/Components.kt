package br.com.managerfoot.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.managerfoot.data.database.entities.ClassificacaoEntity
import br.com.managerfoot.data.database.entities.PartidaEntity
import br.com.managerfoot.data.database.entities.Setor
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.domain.model.Time
import br.com.managerfoot.presentation.ui.theme.SetorAtaque
import br.com.managerfoot.presentation.ui.theme.SetorDefesa
import br.com.managerfoot.presentation.ui.theme.SetorGoleiro
import br.com.managerfoot.presentation.ui.theme.SetorMeio
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.Spacing

// ─── Escudo do time ─────────────────────────────────────────
// Carrega o drawable cujo nome está em escudoRes.
// Se vazio ou não encontrado, exibe um círculo com iniciais.
@Composable
fun TeamBadge(
    nome: String,
    escudoRes: String = "",
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resId = remember(escudoRes) {
        if (escudoRes.isNotBlank())
            context.resources.getIdentifier(escudoRes, "drawable", context.packageName)
        else 0
    }

    if (resId != 0) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = nome,
            modifier = modifier
                .size(size),
            contentScale = ContentScale.Fit
        )
    } else {
        // Fallback: círculo colorido com iniciais
        val initials = nome.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .take(2)
        val bgColor = remember(nome) {
            val h = nome.fold(0) { acc, c -> acc * 31 + c.code } and 0x7FFFFFFF
            Color(hslToRgb((h % 360).toFloat(), 0.55f, 0.40f))
        }
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp
            )
        }
    }
}

/** Converts HSL to packed ARGB int for use in Color(). */
private fun hslToRgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60  -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else    -> Triple(c, 0f, x)
    }
    val r = ((r1 + m) * 255).toInt().coerceIn(0, 255)
    val g = ((g1 + m) * 255).toInt().coerceIn(0, 255)
    val b = ((b1 + m) * 255).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

// ─── Cabeçalho do time do jogador ───────────────────────────
private val NOMES_MESES_HEADER = listOf(
    "", "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
    "Jul", "Ago", "Set", "Out", "Nov", "Dez"
)

/**
 * Header dramático do clube: escudo grande, série + período, KPIs em cards.
 * Forma é opcional (lista de últimos 5 resultados V/E/D do clube do jogador).
 */
@Composable
fun TimeHeaderCard(
    time: Time,
    modifier: Modifier = Modifier,
    posicao: Int = 0,
    rodadaAtual: Int = 0,
    mes: Int = 0,
    dia: Int = 0,
    ano: Int = 0,
    forma: List<Resultado> = emptyList()
) {
    val serieLabel = when (time.divisao) {
        1 -> "Série A"; 2 -> "Série B"; 3 -> "Série C"; 4 -> "Série D"
        5 -> "Primera Div."; 6 -> "Segunda Div. (ARG)"
        in 11..12 -> "Primera Div. (URU)"; in 13..16 -> "Segunda Div. (URU)"
        else -> "Série"
    }
    val periodoLabel = if (mes in 1..12 && ano > 0) {
        if (dia > 0) "$dia ${NOMES_MESES_HEADER[mes]} $ano"
        else "${NOMES_MESES_HEADER[mes]} $ano"
    } else ""
    val subtitle = buildString {
        append(serieLabel)
        if (posicao > 0) append(" · ${posicao}º")
        if (rodadaAtual > 0) append(" · R$rodadaAtual")
        if (periodoLabel.isNotEmpty()) append(" · $periodoLabel")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(Radius.lg)
    ) {
        Box {
            // Watermark do escudo no canto direito (sutil)
            TeamBadge(
                nome = time.nome,
                escudoRes = time.escudoRes,
                size = 160.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 32.dp)
                    .alpha(0.08f)
            )
            Column(Modifier.padding(Spacing.lg)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 64.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = time.nome,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    KpiCard(
                        label = "Saldo",
                        valor = formatarSaldo(time.saldo),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        label = "Reputação",
                        valor = "%.1f".format(time.reputacao),
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    if (forma.isNotEmpty()) {
                        KpiCardForma(
                            label = "Forma",
                            forma = forma,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        KpiCard(
                            label = "Posição",
                            valor = if (posicao > 0) "${posicao}º" else "—",
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ─── KPI Card (label + valor) ───────────────────────────────
@Composable
fun KpiCard(
    label: String,
    valor: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── KPI Card especializado em "Forma" ──────────────────────
@Composable
fun KpiCardForma(
    label: String,
    forma: List<Resultado>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FormaIndicator(forma = forma, dotSize = 12.dp)
    }
}

// ─── Badge de fadiga colorido ───────────────────────────────
@Composable
fun FadigaBadge(fadiga: Float, modifier: Modifier = Modifier) {
    val pct = (fadiga * 100).toInt()
    val (bg, fg) = when {
        fadiga >= 0.80f -> Color(0xFF2E7D32) to Color.White   // verde
        fadiga >= 0.60f -> Color(0xFFF9A825) to Color.Black   // amarelo
        fadiga >= 0.40f -> Color(0xFFE65100) to Color.White   // laranja
        else            -> Color(0xFFC62828) to Color.White   // vermelho
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$pct%",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

// ─── Linha de jogador no elenco ─────────────────────────────
@Composable
fun JogadorRow(
    jogador: Jogador,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge de força
        ForcaBadge(jogador.forca)
        Spacer(Modifier.width(8.dp))
        // Badge de fadiga
        FadigaBadge(jogador.fadiga)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = jogador.nome,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${jogador.posicao.abreviacao} · ${jogador.idade} anos · ${formatarSaldo(jogador.salario)}/mês",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

// ─── Badge de força colorido ────────────────────────────────
@Composable
fun ForcaBadge(forca: Int, modifier: Modifier = Modifier) {
    val (bg, fg, border) = when {
        forca >= 80 -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        forca >= 65 -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline
        )
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(0.5.dp, border, RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = forca.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = fg
        )
    }
}

// ─── Linha da tabela de classificação ───────────────────────
@Composable
fun ClassificacaoRow(
    posicao: Int,
    nomeTime: String,
    escudoRes: String = "",
    cls: ClassificacaoEntity,
    destaque: Boolean = false
) {
    val bgColor = when {
        destaque -> MaterialTheme.colorScheme.primaryContainer
        posicao <= 6 -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = posicao.toString(),
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        TeamBadge(nome = nomeTime, escudoRes = escudoRes, size = 24.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = nomeTime,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TableCell(cls.pontos.toString(), bold = destaque)
        TableCell(cls.jogos.toString())
        TableCell(cls.vitorias.toString())
        TableCell(cls.empates.toString())
        TableCell(cls.derrotas.toString())
        TableCell(
            text = if (cls.saldoGols >= 0) "+${cls.saldoGols}" else cls.saldoGols.toString(),
            color = when {
                cls.saldoGols > 0 -> Color(0xFF1B5E20)
                cls.saldoGols < 0 -> Color(0xFFC62828)
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        modifier = modifier.width(32.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        maxLines = 1
    )
}

// ─── Card de resultado de partida ───────────────────────────
@Composable
fun ResultadoCard(
    nomeCasa: String,
    nomeVis: String,
    golsCasa: Int?,
    golsVis: Int?,
    escudoCasa: String = "",
    escudoVis: String = "",
    meuTimeId: Int = -1,
    timeCasaId: Int = -1,
    modifier: Modifier = Modifier
) {
    val sidebarColor = when {
        meuTimeId == -1 || timeCasaId == -1 || golsCasa == null || golsVis == null ->
            Color.Transparent
        else -> {
            val meuGols = if (meuTimeId == timeCasaId) golsCasa else golsVis
            val advGols = if (meuTimeId == timeCasaId) golsVis else golsCasa
            when {
                meuGols > advGols  -> MaterialTheme.colorScheme.primary
                meuGols == advGols -> MaterialTheme.colorScheme.secondary
                else               -> MaterialTheme.colorScheme.error
            }
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(sidebarColor)
            )
            Row(
                modifier = Modifier.weight(1f).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeamBadge(nome = nomeCasa, escudoRes = escudoCasa, size = 28.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = nomeCasa,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (golsCasa != null && golsVis != null) {
                    Text(
                        text = "$golsCasa x $golsVis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                } else {
                    Text("vs", modifier = Modifier.padding(horizontal = 12.dp))
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = nomeVis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    TeamBadge(nome = nomeVis, escudoRes = escudoVis, size = 28.dp)
                }
            }
        }
    }
}

// ─── Chips e utilitários ────────────────────────────────────
@Composable
fun InfoChip(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SecaoHeader(titulo: String, modifier: Modifier = Modifier) {
    Text(
        text = titulo.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun EmptyState(mensagem: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = mensagem,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Card para próxima partida (versão polida) ──────────────
@Composable
fun MatchCard(
    nomeCasa: String,
    nomeFora: String,
    escudoCasa: String = "",
    escudoFora: String = "",
    competicao: String = "",
    rodada: Int,
    dataJogo: String = "",
    enabled: Boolean = true,
    onSimular: () -> Unit,
    onEscalacao: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infoLinha = buildString {
        if (competicao.isNotBlank()) append(competicao)
        // Para fases de copa o "competicao" já vem com formato "Copa — Fase",
        // então só adiciona "Rodada N" quando é campeonato regular.
        if (rodada > 0 && !competicao.contains("—")) {
            if (isNotEmpty()) append(" · ")
            append("Rodada $rodada")
        }
        if (dataJogo.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append(dataJogo)
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            // Faixa superior com competição (acento verde)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            ) {
                Text(
                    text = infoLinha.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MatchTeamColumn(
                    nome = nomeCasa,
                    escudo = escudoCasa,
                    modifier = Modifier.weight(1f)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                ) {
                    Text(
                        text = "VS",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "casa",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MatchTeamColumn(
                    nome = nomeFora,
                    escudo = escudoFora,
                    modifier = Modifier.weight(1f)
                )
            }
            // Ações
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (onEscalacao != null) {
                    OutlinedButton(
                        onClick = onEscalacao,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radius.md),
                        enabled = enabled
                    ) {
                        Text("Escalar")
                    }
                }
                Button(
                    onClick = onSimular,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    if (!enabled) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("▶ Simular", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchTeamColumn(
    nome: String,
    escudo: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        TeamBadge(nome = nome, escudoRes = escudo, size = 64.dp)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = nome,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Forma (V/E/D) ──────────────────────────────────────────
enum class Resultado { V, E, D }

/**
 * Calcula V/E/D dos últimos 5 jogos do clube nas partidas dadas.
 * Espera-se que `partidas` venha já filtrada para o time do jogador
 * (como faz `GameRepository.buscarUltimosResultados`).
 * A lista vem ordenada do mais recente para o mais antigo; invertemos
 * para exibir em ordem cronológica (mais antigo à esquerda → mais recente à direita).
 */
fun derivarFormaUltimos5(partidas: List<PartidaEntity>, meuTimeId: Int): List<Resultado> {
    return partidas
        .asSequence()
        .filter { it.golsCasa != null && it.golsFora != null }
        .filter { it.timeCasaId == meuTimeId || it.timeForaId == meuTimeId }
        .take(5)
        .toList()
        .reversed()
        .map { p ->
            val gols    = if (p.timeCasaId == meuTimeId) p.golsCasa!! else p.golsFora!!
            val golsAdv = if (p.timeCasaId == meuTimeId) p.golsFora!! else p.golsCasa!!
            when {
                gols >  golsAdv -> Resultado.V
                gols == golsAdv -> Resultado.E
                else            -> Resultado.D
            }
        }
}

@Composable
fun FormaIndicator(
    forma: List<Resultado>,
    dotSize: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val empty = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val win   = MaterialTheme.colorScheme.primary
    val draw  = MaterialTheme.colorScheme.secondary
    val loss  = MaterialTheme.colorScheme.error
    val slots = 5
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        repeat(slots) { i ->
            val r = forma.getOrNull(i)
            val color = when (r) {
                Resultado.V -> win
                Resultado.E -> draw
                Resultado.D -> loss
                null        -> empty
            }
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ─── Tile de gestão (grid do dashboard) ─────────────────────
@Composable
fun ManagementTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md, horizontal = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Chip de competição (atalho) ────────────────────────────
@Composable
fun CompetitionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─── Título de seção polido (substitui SecaoHeader) ─────────
@Composable
fun SectionTitle(
    titulo: String,
    modifier: Modifier = Modifier,
    acao: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.sm))
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = titulo.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f)
        )
        if (acao != null) acao()
    }
}

@Composable
fun SectionLink(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Componentes utilitários da Fase 1
// ─────────────────────────────────────────────────────────────

/**
 * Card padrão "seção com header" — wrapper consistente para conteúdos
 * agrupados (estatísticas, listas, etc.).
 */
@Composable
fun SectionCard(
    titulo: String? = null,
    acaoTrailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(br.com.managerfoot.presentation.ui.theme.Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            if (titulo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = 14.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.sm))
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = titulo.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (acaoTrailing != null) acaoTrailing()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            content()
        }
    }
}

/**
 * TopBar padrão para telas com 2 linhas (título + subtítulo opcional).
 * Mantém consistência visual em todas as telas com botão "Voltar".
 */
@Composable
fun ScreenTopBar(
    titulo: String,
    subtitulo: String? = null,
    onVoltar: (() -> Unit)? = null,
    acoes: (@Composable RowScope.() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onVoltar != null) {
                IconButton(onClick = onVoltar) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(Modifier.width(Spacing.md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitulo.isNullOrBlank()) {
                    Text(
                        text = subtitulo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (acoes != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { acoes() }
            }
        }
    }
}

/**
 * Exibe um valor monetário com cor por sinal:
 *  - positivo / zero → MoneyPositive (verde)
 *  - negativo → MoneyNegative (vermelho)
 *
 * Usado em telas de finanças, mercado, etc.
 */
@Composable
fun MoneyDelta(
    centavos: Long,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight = FontWeight.SemiBold,
    mostrarSinal: Boolean = true,
    modifier: Modifier = Modifier
) {
    val positivo = centavos >= 0
    val cor = if (positivo) br.com.managerfoot.presentation.ui.theme.MoneyPositive
             else br.com.managerfoot.presentation.ui.theme.MoneyNegative
    val abs = formatarSaldo(kotlin.math.abs(centavos))
    val texto = if (mostrarSinal) {
        if (positivo) "+$abs" else "−${abs.removePrefix("R$ ").let { "R$ $it" }}"
    } else {
        if (positivo) abs else "-$abs"
    }
    Text(
        text = texto,
        style = style,
        fontWeight = fontWeight,
        color = cor,
        modifier = modifier
    )
}

/**
 * Barra horizontal de atributo de jogador (força, técnica, finalização, etc.).
 * - 0..99 → 0..100% de preenchimento
 * - cor por faixa: ≥80 verde, ≥65 azul, ≥50 âmbar, demais cinza
 */
@Composable
fun StatBar(
    label: String,
    valor: Int,
    max: Int = 99,
    modifier: Modifier = Modifier
) {
    val pct = (valor.toFloat() / max).coerceIn(0f, 1f)
    val cor = when {
        valor >= 80 -> MaterialTheme.colorScheme.primary
        valor >= 65 -> Color(0xFF42A5F5)
        valor >= 50 -> Color(0xFFFFB300)
        else        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(pct)
                    .fillMaxHeight()
                    .background(cor)
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = valor.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = cor,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Linha de tabela de classificação polida — substitui ClassificacaoRow no
 * TabelaScreen. Tem faixa lateral colorida indicando zona (G4/Z4/etc.).
 */
@Composable
fun StandingsRow(
    posicao: Int,
    nomeTime: String,
    escudoRes: String = "",
    pontos: Int,
    jogos: Int,
    vitorias: Int,
    empates: Int,
    derrotas: Int,
    saldoGols: Int,
    golsPro: Int? = null,
    golsContra: Int? = null,
    forma: List<Resultado> = emptyList(),
    zonaColor: Color = Color.Transparent,
    destaque: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                if (destaque) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else if (posicao % 2 == 0) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Faixa lateral indicando zona da tabela
        Box(
            Modifier
                .width(if (destaque) 4.dp else 3.dp)
                .fillMaxHeight()
                .background(if (destaque) MaterialTheme.colorScheme.primary else zonaColor)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = posicao.toString(),
                modifier = Modifier.width(24.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (destaque) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TeamBadge(nome = nomeTime, escudoRes = escudoRes, size = 24.dp)
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = nomeTime,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (destaque) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            StandingsCell(pontos.toString(), bold = true)
            StandingsCell(jogos.toString())
            StandingsCell(vitorias.toString())
            StandingsCell(empates.toString())
            StandingsCell(derrotas.toString())
            if (golsPro != null) StandingsCell(golsPro.toString())
            if (golsContra != null) StandingsCell(golsContra.toString())
            StandingsCell(
                text = if (saldoGols >= 0) "+$saldoGols" else saldoGols.toString(),
                color = when {
                    saldoGols > 0 -> MaterialTheme.colorScheme.primary
                    saldoGols < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (forma.isNotEmpty()) {
                Spacer(Modifier.width(Spacing.xs))
                FormaIndicator(forma = forma, dotSize = 8.dp)
            }
        }
    }
}

@Composable
private fun StandingsCell(
    text: String,
    bold: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        modifier = Modifier.width(28.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

/**
 * Pílula de filtro selecionável — usado em filtros horizontais de país,
 * divisão, posição, competição, etc. Substitui dropdowns Material onde
 * cabe.
 */
@Composable
fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.pill),
        color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 0.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Abas em formato pill — alternativa ao TabRow Material para casos onde
 * as abas são poucas e queremos identidade do app (não do Material).
 */
@Composable
fun TabRowPill(
    abas: List<String>,
    selecionada: Int,
    onSelecionar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radius.pill)
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            abas.forEachIndexed { idx, label ->
                val sel = idx == selecionada
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(
                            if (sel) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { onSelecionar(idx) }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Cor semântica de cada setor (GOLEIRO, DEFESA, MEIO, ATAQUE).
 * Usada em badges de posição em Escalação, Jogadores, Finanças, etc.
 */
fun corSetor(setor: Setor): Color = when (setor) {
    Setor.GOLEIRO -> SetorGoleiro
    Setor.DEFESA  -> SetorDefesa
    Setor.MEIO    -> SetorMeio
    Setor.ATAQUE  -> SetorAtaque
}

/**
 * Badge retangular colorido com a abreviação da posição. Padrão visual
 * compartilhado entre Escalação, Jogadores, Finanças e painéis modais.
 */
@Composable
fun PosicaoBadge(
    abreviacao: String,
    setor: Setor,
    modifier: Modifier = Modifier
) {
    val cor = corSetor(setor)
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 26.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(cor.copy(alpha = 0.18f))
            .border(0.5.dp, cor.copy(alpha = 0.55f), RoundedCornerShape(Radius.sm)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = abreviacao,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = cor
        )
    }
}

fun formatarSaldo(centavos: Long): String {
    val reais = centavos / 100.0
    val sign = if (reais < 0) "-" else ""
    val abs = kotlin.math.abs(reais)
    return when {
        abs >= 1_000_000_000 -> "${sign}R$ %.2f bi".format(abs / 1_000_000_000)
        abs >= 1_000_000     -> "${sign}R$ %.1f M".format(abs / 1_000_000)
        abs >= 1_000         -> "${sign}R$ %.0f mil".format(abs / 1_000)
        else                 -> "${sign}R$ %.0f".format(abs)
    }
}

/**
 * Card de troféu de campeonato — usado em ConquistasScreen.
 * Exibe o ícone do troféu, nome curto, quantidade de títulos e lista
 * resumida de anos.
 *
 * @param tintable se true (ex: placeholder vetorial mono-cor), aplica
 *  `ColorFilter.tint(tier)` para colorir o ícone com a cor do tier.
 *  Se false (ex: PNG/vector customizado com cores próprias), o ícone
 *  é renderizado com suas cores originais — apenas a borda e o "x N"
 *  ficam na cor do tier.
 */
@Composable
fun TrofeuCard(
    iconRes: Int,
    nomeCampeonato: String,
    tier: Color,
    quantidade: Int,
    anos: List<Int>,
    modifier: Modifier = Modifier,
    tintable: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val anosLabel = when {
        anos.isEmpty() -> ""
        anos.size <= 4 -> anos.sortedDescending().joinToString(" · ")
        else           -> anos.sortedDescending().take(4).joinToString(" · ") + " +${anos.size - 4}"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, tier.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(tier.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = nomeCampeonato,
                    modifier = Modifier.size(56.dp),
                    colorFilter = if (tintable) {
                        androidx.compose.ui.graphics.ColorFilter.tint(tier)
                    } else null
                )
            }
            Text(
                "x$quantidade",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tier
            )
            Text(
                nomeCampeonato,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (anosLabel.isNotEmpty()) {
                Text(
                    anosLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Card de passagem por clube — usado em HistoricoJogadorScreen.
 * Mostra um período de carreira com escudo, anos, gols, assistências,
 * partidas e nota média.
 */
@Composable
fun PassagemClubeCard(
    timeNome: String,
    escudoRes: String,
    anoInicio: Int,
    anoFim: Int,
    gols: Int,
    assistencias: Int,
    partidas: Int,
    notaMedia: Float? = null,
    destaque: Boolean = false,
    modifier: Modifier = Modifier
) {
    val faixaCor = if (destaque) br.com.managerfoot.presentation.ui.theme.GoldChampion
                   else br.com.managerfoot.presentation.ui.theme.GreenElectric

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, faixaCor.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(faixaCor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    TeamBadge(nome = timeNome, escudoRes = escudoRes, size = 36.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            timeNome,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (anoInicio == anoFim) "$anoInicio"
                            else "$anoInicio – $anoFim",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (notaMedia != null && notaMedia > 0f) {
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = corDaNotaCarreira(notaMedia).copy(alpha = 0.18f),
                            border = BorderStroke(0.5.dp, corDaNotaCarreira(notaMedia).copy(alpha = 0.5f))
                        ) {
                            Text(
                                "★ %.1f".format(notaMedia),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = corDaNotaCarreira(notaMedia),
                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCarreira(label = "PARTIDAS", valor = partidas.toString())
                    StatCarreira(
                        label = "GOLS",
                        valor = gols.toString(),
                        cor = br.com.managerfoot.presentation.ui.theme.PromotionGreen
                    )
                    StatCarreira(
                        label = "ASSIST.",
                        valor = assistencias.toString(),
                        cor = br.com.managerfoot.presentation.ui.theme.LibertadoresBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCarreira(
    label: String,
    valor: String,
    cor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            valor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = cor
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
    }
}

private fun corDaNotaCarreira(nota: Float): Color = when {
    nota >= 8.0f -> br.com.managerfoot.presentation.ui.theme.PromotionGreen
    nota >= 6.5f -> br.com.managerfoot.presentation.ui.theme.GreenElectric
    nota >= 5.0f -> br.com.managerfoot.presentation.ui.theme.AmberAccent
    else         -> br.com.managerfoot.presentation.ui.theme.RelegationRed
}
