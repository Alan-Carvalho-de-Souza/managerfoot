package br.com.managerfoot.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.presentation.ui.components.FilterChipPill
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.StandingsRow
import br.com.managerfoot.presentation.ui.theme.LibertadoresBlue
import br.com.managerfoot.presentation.ui.theme.PromotionGreen
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.RelegationRed
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.ui.theme.SulAmericanaTeal
import br.com.managerfoot.presentation.viewmodel.TabelaViewModel

@Composable
fun TabelaScreen(
    campeonatoAId: Int,
    campeonatoBId: Int,
    campeonatoCId: Int = -1,
    campeonatoDId: Int = -1,
    campeonatoArgAId: Int = -1,
    timeJogadorId: Int,
    onVoltar: () -> Unit = {},
    vm: TabelaViewModel = hiltViewModel()
) {
    val tabela by vm.tabela.collectAsState()
    val times by vm.times.collectAsState()
    val divisaoSelecionada by vm.divisaoSelecionada.collectAsState()

    LaunchedEffect(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, campeonatoArgAId) {
        vm.carregar(campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId, campeonatoArgAId, timeJogadorId)
    }

    // Opções por país
    val opcoesBrasil = buildList {
        add(1 to "Série A")
        add(2 to "Série B")
        if (campeonatoCId > 0) add(3 to "Série C")
        if (campeonatoDId > 0) add(4 to "Série D")
    }
    val opcoesArgentina = buildList {
        if (campeonatoArgAId > 0) add(5 to "Primera Div.")
    }

    val paises = buildList {
        if (opcoesBrasil.isNotEmpty()) add("Brasil")
        if (opcoesArgentina.isNotEmpty()) add("Argentina")
    }

    val paisSelecionado = remember(divisaoSelecionada) {
        if (divisaoSelecionada == 5) "Argentina" else "Brasil"
    }
    val opcoesDivisaoPais = if (paisSelecionado == "Argentina") opcoesArgentina else opcoesBrasil
    val labelDivisaoAtual = opcoesDivisaoPais.firstOrNull { it.first == divisaoSelecionada }?.second
        ?: opcoesDivisaoPais.firstOrNull()?.second ?: ""

    // Posição do clube do jogador (se na divisão atual)
    val posicaoJogador = remember(tabela, timeJogadorId) {
        val idx = tabela.indexOfFirst { it.timeId == timeJogadorId }
        if (idx >= 0) idx + 1 else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Classificação",
            subtitulo = buildString {
                append(labelDivisaoAtual)
                if (posicaoJogador != null) append(" · seu time em ${posicaoJogador}º")
            },
            onVoltar = onVoltar
        )

        // Filtro de país (chips horizontais) — só aparece se houver mais de um país
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
                            val primeiraDivisao = if (pais == "Argentina")
                                opcoesArgentina.firstOrNull()?.first ?: 5
                            else
                                opcoesBrasil.firstOrNull()?.first ?: 1
                            vm.selecionarDivisao(primeiraDivisao)
                        }
                    )
                }
            }
        }

        // Filtro de divisão (chips horizontais) — só aparece se houver mais de uma divisão no país
        if (opcoesDivisaoPais.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(bottom = Spacing.md)
            ) {
                items(opcoesDivisaoPais) { (div, label) ->
                    FilterChipPill(
                        label = label,
                        selected = div == divisaoSelecionada,
                        onClick = { vm.selecionarDivisao(div) }
                    )
                }
            }
        } else {
            Spacer(Modifier.height(Spacing.sm))
        }

        TabelaColunasHeader()

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(tabela) { index, item ->
                val time = times.find { it.id == item.timeId }
                val nomeTime = time?.nome ?: "Time ${item.timeId}"
                val escudoRes = time?.escudoRes ?: ""
                val ehJogador = item.timeId == timeJogadorId
                val zona = zonaParaDivisao(index + 1, divisaoSelecionada)

                StandingsRow(
                    posicao = index + 1,
                    nomeTime = nomeTime,
                    escudoRes = escudoRes,
                    pontos = item.pontos,
                    jogos = item.jogos,
                    vitorias = item.vitorias,
                    empates = item.empates,
                    derrotas = item.derrotas,
                    saldoGols = item.saldoGols,
                    zonaColor = zona ?: Color.Transparent,
                    destaque = ehJogador
                )
            }
            item { Spacer(Modifier.height(Spacing.sm)) }
        }

        LegendaZonas(divisaoSelecionada)
    }
}

/** Linha de cabeçalho das colunas — alinhada com `StandingsRow`. */
@Composable
private fun TabelaColunasHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Espaço pra faixa lateral colorida (3dp) que a StandingsRow tem
        Spacer(Modifier.width(3.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Largura combinando: posição (24dp) + escudo (24dp) + spacer xs (4dp)
            Spacer(Modifier.width(24.dp + 24.dp + Spacing.xs))
            Text(
                "TIME",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            listOf("P", "J", "V", "E", "D", "SG").forEach { col ->
                Text(
                    col,
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Cor da zona pela posição na tabela (G4, Sul-Americana, Z4). */
private fun zonaParaDivisao(posicao: Int, divisao: Int): Color? = when (divisao) {
    1 -> when {
        posicao <= 4  -> LibertadoresBlue
        posicao <= 6  -> SulAmericanaTeal
        posicao >= 17 -> RelegationRed
        else          -> null
    }
    2 -> when {
        posicao <= 4  -> PromotionGreen
        posicao >= 17 -> RelegationRed
        else          -> null
    }
    3 -> when {
        posicao <= 4  -> PromotionGreen
        posicao >= 17 -> RelegationRed
        else          -> null
    }
    5 -> when {
        posicao <= 4  -> LibertadoresBlue
        else          -> null
    }
    else -> when {
        posicao <= 4  -> PromotionGreen
        else          -> null
    }
}

/** Legenda compacta no rodapé com chips de cada zona da divisão atual. */
@Composable
private fun LegendaZonas(divisao: Int) {
    val itens = when (divisao) {
        1 -> listOf(
            LibertadoresBlue to "G4 Libertadores",
            SulAmericanaTeal to "G6 Sul-Americana",
            RelegationRed   to "Z4 Rebaixamento"
        )
        2 -> listOf(
            PromotionGreen to "G4 Acesso",
            RelegationRed  to "Z4 Rebaixamento"
        )
        3 -> listOf(
            PromotionGreen to "G4 Acesso",
            RelegationRed  to "Z4 Rebaixamento"
        )
        5 -> listOf(
            LibertadoresBlue to "G4 Libertadores"
        )
        else -> listOf(
            PromotionGreen to "G4 Acesso"
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itens.forEach { (cor, label) ->
                ZonaLegendaChip(cor, label)
            }
        }
    }
}

@Composable
private fun ZonaLegendaChip(cor: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(cor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
