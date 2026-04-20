package br.com.managerfoot.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.domain.model.Time

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

@Composable
fun TimeHeaderCard(
    time: Time,
    modifier: Modifier = Modifier,
    posicao: Int = 0,
    rodadaAtual: Int = 0,
    mes: Int = 0,
    dia: Int = 0,
    ano: Int = 0
) {
    val serieLabel = when (time.divisao) { 1 -> "Série A"; 2 -> "Série B"; 3 -> "Série C"; 4 -> "Série D"; else -> "Série" }
    val subtitle = buildString {
        append(serieLabel)
        if (rodadaAtual > 0) append(" · Rodada $rodadaAtual")
    }
    val periodoLabel = if (mes in 1..12 && ano > 0) {
        if (dia > 0) "$dia ${NOMES_MESES_HEADER[mes]}. $ano"
        else "${NOMES_MESES_HEADER[mes]}. $ano"
    } else ""
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = time.nome,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (periodoLabel.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            periodoLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                KpiItem(label = "Saldo", value = formatarSaldo(time.saldo))
                Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outline))
                KpiItem(label = "Reputação", value = "%.1f".format(time.reputacao))
                Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outline))
                KpiItem(label = "Posição", value = if (posicao > 0) "${posicao}º" else "—")
            }
        }
    }
}

@Composable
private fun KpiItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

// ─── Card para próxima partida ───────────────────────────────
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
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            val rodadaOuFase = if (competicao.contains("—")) competicao
                               else if (competicao.isNotBlank()) "$competicao · Rodada $rodada"
                               else "Rodada $rodada"
            val labelCompleto = if (dataJogo.isNotBlank()) "$rodadaOuFase · $dataJogo" else rodadaOuFase
            Text(
                text = labelCompleto,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamBadge(nome = nomeCasa, escudoRes = escudoCasa, size = 48.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = nomeCasa,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "VS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamBadge(nome = nomeFora, escudoRes = escudoFora, size = 48.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = nomeFora,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSimular,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (!enabled) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("▶ Próxima Partida", fontWeight = FontWeight.SemiBold)
                }
            }
            if (onEscalacao != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onEscalacao,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Escalação")
                }
            }
        }
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
