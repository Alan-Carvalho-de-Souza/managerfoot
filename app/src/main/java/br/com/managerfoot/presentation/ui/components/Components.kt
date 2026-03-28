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
import androidx.compose.ui.text.font.FontWeight
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
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
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
@Composable
fun TimeHeaderCard(time: Time, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(nome = time.nome, escudoRes = time.escudoRes, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = time.nome,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoChip("Série A", MaterialTheme.colorScheme.primaryContainer)
                InfoChip(formatarSaldo(time.saldo), MaterialTheme.colorScheme.secondaryContainer)
                InfoChip("Reputação ${time.reputacao}", MaterialTheme.colorScheme.primaryContainer)
            }
        }
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
        Spacer(Modifier.width(12.dp))
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
fun ForcaBadge(forca: Int) {
    val cor = when {
        forca >= 85 -> Color(0xFF1B5E20)  // verde escuro
        forca >= 75 -> Color(0xFF388E3C)  // verde
        forca >= 65 -> Color(0xFFF9A825)  // amarelo
        forca >= 55 -> Color(0xFFE65100)  // laranja
        else        -> Color(0xFFC62828)  // vermelho
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(cor)
    ) {
        Text(
            text = forca.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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

// ─── Chips e utilitários ────────────────────────────────────
@Composable
fun InfoChip(text: String, containerColor: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SecaoHeader(titulo: String, modifier: Modifier = Modifier) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
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

fun formatarSaldo(centavos: Long): String {
    val reais = centavos / 100.0
    return when {
        reais >= 1_000_000_000 -> "R$ %.2f bi".format(reais / 1_000_000_000)
        reais >= 1_000_000     -> "R$ %.1f M".format(reais / 1_000_000)
        reais >= 1_000         -> "R$ %.0f mil".format(reais / 1_000)
        else                   -> "R$ %.0f".format(reais)
    }
}
