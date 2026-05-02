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
import br.com.managerfoot.presentation.ui.theme.GoldChampion
import br.com.managerfoot.presentation.ui.theme.LibertadoresBlue
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.RelegationRed
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.ui.theme.SulAmericanaTeal
import br.com.managerfoot.presentation.viewmodel.TabelaViewModel

// Cor especial para playoff (Uruguai Segunda Divisão) — sem token semântico ainda
private val PlayoffOrange = Color(0xFFFF6F00)

@Composable
fun TabelaScreen(
    campeonatoAId: Int,
    campeonatoBId: Int,
    campeonatoCId: Int = -1,
    campeonatoDId: Int = -1,
    campeonatoArgAId: Int = -1,
    campeonatoArgBId: Int = -1,
    campeonatoArgClausuraId: Int = -1,
    campeonatoUruAperturaId: Int = -1,
    campeonatoUruBId: Int = -1,
    campeonatoUruClausuraId: Int = -1,
    campeonatoUruIntermedId: Int = -1,
    campeonatoUruBCompetId: Int = -1,
    copaId: Int = -1,
    timeJogadorId: Int,
    onVoltar: () -> Unit = {},
    vm: TabelaViewModel = hiltViewModel()
) {
    val tabela by vm.tabela.collectAsState()
    val times by vm.times.collectAsState()
    val divisaoSelecionada by vm.divisaoSelecionada.collectAsState()
    val copaChampeaoTimeId by vm.copaChampeaoTimeId.collectAsState()

    // Posição (1-indexed) do campeão da Copa na tabela da Série A, -1 se não aplicável
    val copaChampeaoPosSerieA = remember(tabela, copaChampeaoTimeId, divisaoSelecionada) {
        if (divisaoSelecionada == 1 && copaChampeaoTimeId > 0)
            tabela.indexOfFirst { it.timeId == copaChampeaoTimeId }.let { if (it >= 0) it + 1 else -1 }
        else -1
    }

    LaunchedEffect(
        campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId,
        campeonatoArgAId, campeonatoArgBId, campeonatoArgClausuraId,
        campeonatoUruAperturaId, campeonatoUruBId, campeonatoUruClausuraId,
        campeonatoUruIntermedId, campeonatoUruBCompetId, copaId
    ) {
        vm.carregar(
            campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId,
            campeonatoArgAId, campeonatoArgBId,
            campArgClausuraId = campeonatoArgClausuraId,
            campUruAperturaId = campeonatoUruAperturaId,
            campUruBId = campeonatoUruBId,
            campUruClausuraId = campeonatoUruClausuraId,
            campUruIntermedId = campeonatoUruIntermedId,
            campUruBCompetId = campeonatoUruBCompetId,
            copaIdBrasil = copaId,
            timeJogadorId = timeJogadorId
        )
    }

    // Opções por país (preservadas do dev)
    val opcoesBrasil = buildList {
        add(1 to "Série A")
        add(2 to "Série B")
        if (campeonatoCId > 0) add(3 to "Série C")
        if (campeonatoDId > 0) add(4 to "Série D")
    }
    val opcoesArgentina = buildList {
        if (campeonatoArgAId > 0) add(5 to "Apertura")
        if (campeonatoArgClausuraId > 0) add(7 to "Clausura")
        if (campeonatoArgAId > 0 && campeonatoArgClausuraId > 0) add(8 to "Geral")
        if (campeonatoArgBId > 0) add(6 to "Segunda Div.")
    }
    val opcoesUruguai = buildList {
        if (campeonatoUruAperturaId > 0) add(9 to "Apertura")
        if (campeonatoUruIntermedId > 0) add(11 to "Intermédio")
        if (campeonatoUruClausuraId > 0) add(12 to "Clausura")
        if (campeonatoUruAperturaId > 0 && campeonatoUruClausuraId > 0) add(13 to "Geral")
        if (campeonatoUruBCompetId > 0) add(14 to "Competencia")
        if (campeonatoUruBId > 0) add(10 to "Segunda Div.")
    }

    val paises = buildList {
        if (opcoesBrasil.isNotEmpty()) add("Brasil")
        if (opcoesArgentina.isNotEmpty()) add("Argentina")
        if (opcoesUruguai.isNotEmpty()) add("Uruguai")
    }

    // País selecionado: derivado da divisão atual
    val paisSelecionado = remember(divisaoSelecionada) {
        when (divisaoSelecionada) {
            in 5..8 -> "Argentina"
            in 9..14 -> "Uruguai"
            else -> "Brasil"
        }
    }
    val opcoesDivisaoPais = when (paisSelecionado) {
        "Argentina" -> opcoesArgentina
        "Uruguai" -> opcoesUruguai
        else -> opcoesBrasil
    }
    val labelDivisaoAtual = opcoesDivisaoPais.firstOrNull { it.first == divisaoSelecionada }?.second
        ?: opcoesDivisaoPais.firstOrNull()?.second ?: ""

    // Posição do clube do jogador (se na divisão atual e não em modo grupos)
    val ehGrupos = divisaoSelecionada == 5 || divisaoSelecionada == 7 || divisaoSelecionada == 14
    val posicaoJogador = remember(tabela, timeJogadorId, ehGrupos) {
        if (ehGrupos) null
        else {
            val idx = tabela.indexOfFirst { it.timeId == timeJogadorId }
            if (idx >= 0) idx + 1 else null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenTopBar(
            titulo = "Classificação",
            subtitulo = buildString {
                if (paisSelecionado != "Brasil" || paises.size > 1) {
                    append(bandeiraPais(paisSelecionado)).append(" ")
                }
                append(labelDivisaoAtual)
                if (posicaoJogador != null) append(" · seu time em ${posicaoJogador}º")
            }.trim(),
            onVoltar = onVoltar
        )

        // Filtro de país (chips horizontais) — só aparece se houver mais de um
        if (paises.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(bottom = Spacing.sm)
            ) {
                items(paises) { pais ->
                    FilterChipPill(
                        label = "${bandeiraPais(pais)} $pais",
                        selected = pais == paisSelecionado,
                        onClick = {
                            val primeiraDivisao = when (pais) {
                                "Argentina" -> opcoesArgentina.firstOrNull()?.first ?: 5
                                "Uruguai" -> opcoesUruguai.firstOrNull()?.first ?: 9
                                else -> opcoesBrasil.firstOrNull()?.first ?: 1
                            }
                            vm.selecionarDivisao(primeiraDivisao)
                        }
                    )
                }
            }
        }

        // Filtro de divisão (chips horizontais)
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

        // Conteúdo: tabela única ou em grupos
        val gruposCompetencia = remember(tabela, ehGrupos) {
            if (ehGrupos) tabela.groupBy { it.grupo ?: "?" }.entries.sortedBy { it.key }
            else emptyList()
        }

        if (!ehGrupos) {
            TabelaColunasHeader()
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (ehGrupos) {
                gruposCompetencia.forEach { (grupo, grupoTabela) ->
                    item { GrupoHeader("Zona $grupo") }
                    item { TabelaColunasHeader() }
                    itemsIndexed(grupoTabela) { index, item ->
                        val time = times.find { it.id == item.timeId }
                        val nomeTime = time?.nome ?: "Time ${item.timeId}"
                        val escudoRes = time?.escudoRes ?: ""
                        val ehJogador = item.timeId == timeJogadorId
                        val classificadosPorGrupo = if (divisaoSelecionada == 14) 1 else 8
                        val zona = if (index < classificadosPorGrupo) LibertadoresBlue else Color.Transparent

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
                            golsPro = item.golsPro,
                            golsContra = item.golsContra,
                            zonaColor = zona,
                            destaque = ehJogador
                        )
                    }
                    item { Spacer(Modifier.height(Spacing.sm)) }
                }
            } else {
                itemsIndexed(tabela) { index, item ->
                    val time = times.find { it.id == item.timeId }
                    val nomeTime = time?.nome ?: "Time ${item.timeId}"
                    val escudoRes = time?.escudoRes ?: ""
                    val ehJogador = item.timeId == timeJogadorId
                    val zona = zonaParaDivisao(index + 1, divisaoSelecionada, copaChampeaoPosSerieA)

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
                        golsPro = item.golsPro,
                        golsContra = item.golsContra,
                        zonaColor = zona ?: Color.Transparent,
                        destaque = ehJogador
                    )
                }
            }
            item { Spacer(Modifier.height(Spacing.sm)) }
        }

        LegendaZonas(divisaoSelecionada, copaChampeaoPosSerieA)
    }
}

@Composable
private fun TabelaColunasHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(3.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // pos (24dp) + escudo (24dp) + xs gap
            Spacer(Modifier.width(24.dp + 24.dp + Spacing.xs))
            Text(
                "TIME",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            listOf("P", "J", "V", "E", "D", "GP", "GC", "SG").forEach { col ->
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

@Composable
private fun GrupoHeader(nome: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    ) {
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
                text = nome.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
        }
    }
}

/** Cor da zona pela posição na tabela. Cobre todas as competições. */
private fun zonaParaDivisao(posicao: Int, divisao: Int, copaChampeaoPosSerieA: Int = -1): Color? = when (divisao) {
    1 -> when {
        // Copa campeão já está no top-6 → 7º também entra para Libertadores
        copaChampeaoPosSerieA in 1..6 && posicao == 7 -> LibertadoresBlue
        posicao <= 4 -> LibertadoresBlue
        posicao <= 6 -> SulAmericanaTeal
        // Copa campeão fora do top-6 → sua posição fica azul (vaga Copa)
        copaChampeaoPosSerieA >= 7 && posicao == copaChampeaoPosSerieA -> LibertadoresBlue
        posicao >= 17 -> RelegationRed
        else -> null
    }
    2 -> when {
        posicao <= 4 -> LibertadoresBlue
        posicao >= 17 -> RelegationRed
        else -> null
    }
    3 -> when {
        posicao <= 4 -> LibertadoresBlue
        posicao >= 17 -> RelegationRed
        else -> null
    }
    5, 7 -> when {
        posicao <= 8 -> LibertadoresBlue   // Oitavas de Final
        else -> null
    }
    8 -> when {
        posicao == 1 -> GoldChampion       // Campeão Geral Argentina
        else -> null
    }
    13 -> null                              // Geral Uruguai: ranking informativo
    10 -> when {
        posicao <= 2 -> LibertadoresBlue   // Acesso direto à Primera Uruguai
        posicao <= 6 -> PlayoffOrange      // Playoff de acesso
        posicao >= 13 -> RelegationRed
        else -> null
    }
    else -> when {
        posicao <= 4 -> LibertadoresBlue
        else -> null
    }
}

@Composable
private fun LegendaZonas(divisao: Int, copaChampeaoPosSerieA: Int = -1) {
    val itens = when (divisao) {
        1 -> buildList {
            if (copaChampeaoPosSerieA in 1..6) {
                add(LibertadoresBlue to "G7 Libertadores (incl. Copa)")
            } else {
                add(LibertadoresBlue to "G4 Libertadores")
            }
            add(SulAmericanaTeal to "G6 Sul-Americana")
            if (copaChampeaoPosSerieA >= 7) {
                add(LibertadoresBlue to "${copaChampeaoPosSerieA}º Libertadores (Copa)")
            }
            add(RelegationRed to "Z4 Rebaixamento")
        }
        2 -> listOf(
            LibertadoresBlue to "G4 Acesso à Série A",
            RelegationRed to "Z4 Rebaixamento"
        )
        3 -> listOf(
            LibertadoresBlue to "G4 Acesso à Série B",
            RelegationRed to "Z4 Rebaixamento"
        )
        5, 7 -> listOf(
            LibertadoresBlue to "G8 Oitavas de Final"
        )
        8 -> listOf(
            GoldChampion to "1º Campeão Geral"
        )
        13 -> emptyList()  // Geral Uruguai: sem legenda
        10 -> listOf(
            LibertadoresBlue to "G2 Acesso direto",
            PlayoffOrange to "G6 Playoff",
            RelegationRed to "Z2 Rebaixamento"
        )
        14 -> listOf(
            LibertadoresBlue to "1º Classifica para a Final"
        )
        else -> listOf(
            LibertadoresBlue to "G4 Acesso"
        )
    }
    if (itens.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(itens) { (cor, label) ->
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

private fun bandeiraPais(pais: String): String = when (pais) {
    "Brasil" -> "🇧🇷"
    "Argentina" -> "🇦🇷"
    "Uruguai" -> "🇺🇾"
    else -> ""
}
