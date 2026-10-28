package br.com.managerfoot.presentation.ui.screens

import br.com.managerfoot.presentation.ui.theme.fundoApp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.managerfoot.data.dao.CalendarioPartidaDto
import br.com.managerfoot.presentation.ui.components.EmptyState
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.TabRowPill
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.components.formatarSaldo
import br.com.managerfoot.presentation.ui.theme.*
import br.com.managerfoot.presentation.viewmodel.CalendarioViewModel

// ─────────────────────────────────────────────────────────────
//  CalendarioScreen — Tactical Dark
//  Próximos jogos e resultados anteriores do clube do jogador
// ─────────────────────────────────────────────────────────────
@Composable
fun CalendarioScreen(
    timeId: Int,
    onVoltar: () -> Unit,
    vm: CalendarioViewModel = hiltViewModel()
) {
    val partidas by vm.partidas.collectAsState()
    val competicoes by vm.competicoes.collectAsState()

    LaunchedEffect(timeId) { vm.carregar(timeId) }

    val proximos = remember(partidas) {
        partidas.filter { !it.jogada }.sortedWith(
            compareBy(
                { anoDePartida(it.nomeCampeonato) },
                { val (m, d) = ordemGlobalParaData(it); m * 100 + d }
            )
        )
    }
    val realizados = remember(partidas) {
        partidas.filter { it.jogada }.sortedWith(
            compareByDescending<CalendarioPartidaDto> { anoDePartida(it.nomeCampeonato) }
                .thenByDescending { val (m, d) = ordemGlobalParaData(it); m * 100 + d }
        )
    }

    var tabSelecionada by remember { mutableIntStateOf(0) }
    val abas = listOf("Próximos (${proximos.size})", "Realizados (${realizados.size})")

    Column(
        Modifier
            .fillMaxSize()
            .fundoApp()
    ) {
        ScreenTopBar(
            titulo = "Calendário",
            subtitulo = "Jogos da temporada",
            onVoltar = onVoltar
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.Center
        ) {
            TabRowPill(
                abas = abas,
                selecionada = tabSelecionada,
                onSelecionar = { tabSelecionada = it }
            )
        }

        val lista = if (tabSelecionada == 0) proximos else realizados

        if (lista.isEmpty()) {
            EmptyState(
                if (tabSelecionada == 0) "Nenhum jogo agendado."
                else "Nenhum jogo realizado ainda."
            )
        } else {
            val itensList = remember(lista) {
                buildList {
                    var chavePrev = ""
                    lista.forEach { p ->
                        val (mes, dia) = ordemGlobalParaData(p)
                        val ano = anoDePartida(p.nomeCampeonato)
                        val chave = "${ano}_${"%02d".format(mes)}_${"%02d".format(dia)}"
                        val mesNome = NOMES_MESES[mes]
                        val label = if (ano > 0) "$dia de $mesNome de $ano" else "$dia de $mesNome"
                        if (chave != chavePrev) {
                            add(CalendarioItem.Header(chave, label))
                            chavePrev = chave
                        }
                        add(CalendarioItem.Jogo(p))
                    }
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(itensList, key = { item ->
                    when (item) {
                        is CalendarioItem.Header -> "mes_${item.chave}"
                        is CalendarioItem.Jogo   -> "jogo_${item.partida.partidaId}"
                    }
                }) { item ->
                    when (item) {
                        is CalendarioItem.Header -> DataHeader(item.label)
                        is CalendarioItem.Jogo   -> CalendarioCard(
                            partida = item.partida,
                            timeJogadorId = timeId,
                            competicoes = competicoes
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Card de uma partida do calendário
// ─────────────────────────────────────────────────────────────
@Composable
private fun CalendarioCard(
    partida: CalendarioPartidaDto,
    timeJogadorId: Int,
    competicoes: br.com.managerfoot.presentation.ui.util.MapaDeCompeticoes
) {
    val isJogadorCasa = partida.timeCasaId == timeJogadorId
    val isJogadorFora = partida.timeForaId == timeJogadorId
    val envolvido = isJogadorCasa || isJogadorFora

    val (mes, dia) = ordemGlobalParaData(partida)
    val dataCurta  = "$dia ${NOMES_MESES[mes].take(3)}"

    // Cor da borda: verde elétrico quando o jogador está envolvido
    val borderColor = when {
        envolvido && partida.jogada -> resultadoBorderColor(partida, isJogadorCasa)
        envolvido -> GreenElectric
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    val corCasa = if (isJogadorCasa) GreenElectric else MaterialTheme.colorScheme.onSurface
    val corFora = if (isJogadorFora) GreenElectric else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = if (envolvido) GreenElectric.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (envolvido) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            // Cabeçalho enxuto: competição + data · rodada/fase
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = partida.nomeCampeonato,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (envolvido) GreenElectric else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    // A rodada como o jogador a conta: a 1ª rodada dos grupos da
                    // Libertadores é gravada como 11 (a Pré ocupa 1..6 no mesmo
                    // campeonato) e aparecia aqui como "R11".
                    text = "$dataCurta · ${partida.fase
                        ?: "R${competicoes.rodadaVisivel(partida.campeonatoId, partida.rodada)}"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Linha compacta do confronto (estilo "placares ao vivo")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    partida.nomeCasa,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isJogadorCasa) FontWeight.Bold else FontWeight.SemiBold,
                    color = corCasa
                )
                Spacer(Modifier.width(Spacing.xs))
                TeamBadge(nome = partida.nomeCasa, escudoRes = partida.escudoCasa, size = 22.dp)
                Text(
                    text = if (partida.jogada && partida.golsCasa != null && partida.golsFora != null)
                        "  ${partida.golsCasa} × ${partida.golsFora}  " else "  vs  ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TeamBadge(nome = partida.nomeFora, escudoRes = partida.escudoFora, size = 22.dp)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    partida.nomeFora,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isJogadorFora) FontWeight.Bold else FontWeight.SemiBold,
                    color = corFora
                )
            }

            // Público e receita — só nos jogos JÁ disputados pelo clube do jogador
            if (partida.jogada && envolvido && partida.torcedores != null && partida.torcedores > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👥 ${"%,d".format(partida.torcedores)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (partida.receitaPartida != null) {
                        Text(
                            text = formatarSaldo(partida.receitaPartida),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isJogadorCasa) MoneyPositive else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cor da borda quando o jogador está envolvido e a partida foi jogada.
 * Verde para vitória, âmbar para empate, vermelho para derrota.
 */
private fun resultadoBorderColor(
    partida: CalendarioPartidaDto,
    isJogadorCasa: Boolean
): androidx.compose.ui.graphics.Color {
    val gp = if (isJogadorCasa) partida.golsCasa else partida.golsFora
    val ga = if (isJogadorCasa) partida.golsFora else partida.golsCasa
    if (gp == null || ga == null) return GreenElectric
    return when {
        gp > ga -> PromotionGreen
        gp < ga -> RelegationRed
        else    -> AmberAccent
    }
}

// ─────────────────────────────────────────────────────────────
//  Header do dia (uppercase com letterSpacing — Tactical Dark)
// ─────────────────────────────────────────────────────────────
@Composable
private fun DataHeader(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = GreenElectric,
        letterSpacing = 1.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xxs)
    )
}

// ─────────────────────────────────────────────────────────────
//  Tipos para lista agrupada
// ─────────────────────────────────────────────────────────────
private sealed class CalendarioItem {
    data class Header(val chave: String, val label: String) : CalendarioItem()
    data class Jogo(val partida: CalendarioPartidaDto) : CalendarioItem()
}

// ─────────────────────────────────────────────────────────────
//  Datas: a conversão vive em CalendarioDoJogo, compartilhada com o dashboard
//  e com o histórico de transferências. Aqui ficam só os apelidos locais.
// ─────────────────────────────────────────────────────────────
private val NOMES_MESES = br.com.managerfoot.domain.engine.CalendarioDoJogo.MESES

private fun ordemGlobalParaData(
    partida: br.com.managerfoot.data.dao.CalendarioPartidaDto
): Pair<Int, Int> =
    br.com.managerfoot.domain.engine.CalendarioDoJogo.ordemGlobalParaMesDia(
        partida.ordemGlobal, partida.nomeCampeonato, partida.calendarioVersao
    )

private fun anoDePartida(nomeCampeonato: String): Int =
    br.com.managerfoot.domain.engine.CalendarioDoJogo.anoDoNomeDoCampeonato(nomeCampeonato)
