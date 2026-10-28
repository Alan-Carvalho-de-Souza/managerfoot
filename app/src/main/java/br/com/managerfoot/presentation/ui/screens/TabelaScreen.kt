package br.com.managerfoot.presentation.ui.screens

import br.com.managerfoot.presentation.ui.theme.fundoApp

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
import br.com.managerfoot.domain.engine.VagasDoBrasileirao
import br.com.managerfoot.domain.engine.VagasEuropeias
import br.com.managerfoot.domain.engine.ZonaEuropeia
import br.com.managerfoot.domain.engine.ZonasEuropeias
import br.com.managerfoot.domain.engine.VagaDeTitulo
import br.com.managerfoot.domain.engine.ZonaBrasileirao
import br.com.managerfoot.domain.engine.ZonasDoBrasileirao
import br.com.managerfoot.presentation.ui.components.ScreenTopBar
import br.com.managerfoot.presentation.ui.components.StandingsRow
import br.com.managerfoot.presentation.ui.theme.GoldChampion
import br.com.managerfoot.presentation.ui.theme.LibertadoresBlue
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.RelegationRed
import br.com.managerfoot.presentation.ui.theme.Spacing
import br.com.managerfoot.presentation.ui.theme.SulAmericanaTeal
import br.com.managerfoot.presentation.viewmodel.TabelaViewModel

// Cor de repescagem: playoff de acesso da Segunda uruguaia, vagas de
// Pré-Libertadores na Série A e os playoffs da Sul-Americana — sem token
// semântico ainda
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
    campeonatoPremierId: Int = -1,
    campeonatoChampionshipId: Int = -1,
    campeonatoLaLigaId: Int = -1,
    campeonatoSerieAId: Int = -1,
    campeonatoChampionsId: Int = -1,
    campeonatoEuropaId: Int = -1,
    campeonatoLibertadoresId: Int = -1,
    campeonatoSulAmericanaId: Int = -1,
    timeJogadorId: Int,
    onVoltar: () -> Unit = {},
    vm: TabelaViewModel = hiltViewModel()
) {
    val tabela by vm.tabela.collectAsState()
    val times by vm.times.collectAsState()
    val divisaoSelecionada by vm.divisaoSelecionada.collectAsState()
    val vagasBrasil by vm.vagasBrasil.collectAsState()
    val vagasEuropa by vm.vagasEuropa.collectAsState()

    // A distribuição das vagas continentais sobre a tabela da Série A, com o
    // efeito cascata já resolvido: o campeão da Copa do Brasil na vaga de grupo,
    // o vice na da Pré, os campeões continentais pela vaga estrutural, e a liga
    // preenchendo o resto de cima para baixo. Ver VagasDoBrasileirao.
    val zonasSerieA = remember(tabela, vagasBrasil, divisaoSelecionada) {
        if (divisaoSelecionada != 1) null
        else {
            fun posicaoDe(timeId: Int) =
                tabela.indexOfFirst { it.timeId == timeId }.let { if (it >= 0) it + 1 else -1 }
            // O detentor pode estar fora desta tabela — campeão da Série B, ou um
            // clube que já caiu. Aí a vaga dele desce para a liga.
            fun vagaNaTabela(vaga: VagaDeTitulo): VagaDeTitulo = when (vaga) {
                is VagaDeTitulo.De -> posicaoDe(vaga.posicao)
                    .let { if (it > 0) VagaDeTitulo.De(it) else VagaDeTitulo.SemDetentor }
                else -> vaga
            }
            VagasDoBrasileirao.distribuir(
                campeaoDaCopa = vagaNaTabela(vagasBrasil.campeaoDaCopa),
                viceDaCopa    = vagaNaTabela(vagasBrasil.viceDaCopa),
                posicoesDosCampeoesContinentais =
                    vagasBrasil.campeoesContinentaisTimeIds.map { posicaoDe(it) },
                vagasRemanejadas = vagasBrasil.vagasRemanejadas,
                clubes = tabela.size.coerceAtLeast(VagasDoBrasileirao.CLUBES)
            )
        }
    }

    // As zonas da Champions e da Europa League na liga europeia em exibicao. As
    // cotas saem das listas de acesso (ING 5, ESP 5, ITA 4 na Champions; 2 na
    // Europa League para os tres), e a vaga do campeao da copa nacional cascateia
    // como no Brasil. Ver VagasEuropeias.
    val zonasEuropa = remember(tabela, vagasEuropa, divisaoSelecionada) {
        val pais = paisDaDivisaoEuropeia(divisaoSelecionada)
        if (pais == null) null
        else {
            // O campeao da copa pode estar fora desta tabela — de outra divisao,
            // por exemplo. Ai a vaga dele desce para a liga.
            val vaga = when (val v = vagasEuropa.campeaoDaCopa(pais)) {
                is VagaDeTitulo.De ->
                    tabela.indexOfFirst { it.timeId == v.posicao }
                        .let { if (it >= 0) VagaDeTitulo.De(it + 1) else VagaDeTitulo.SemDetentor }
                else -> v
            }
            VagasEuropeias.distribuir(
                pais = pais,
                clubes = tabela.size,
                campeaoDaCopa = vaga
            )
        }
    }

    LaunchedEffect(
        campeonatoAId, campeonatoBId, campeonatoCId, campeonatoDId,
        campeonatoArgAId, campeonatoArgBId, campeonatoArgClausuraId,
        campeonatoUruAperturaId, campeonatoUruBId, campeonatoUruClausuraId,
        campeonatoUruIntermedId, campeonatoUruBCompetId, copaId,
        campeonatoPremierId, campeonatoChampionshipId, campeonatoLaLigaId, campeonatoSerieAId,
        campeonatoChampionsId, campeonatoEuropaId, campeonatoLibertadoresId,
        campeonatoSulAmericanaId
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
            campPremierId = campeonatoPremierId,
            campChampionshipId = campeonatoChampionshipId,
            campLaLigaId = campeonatoLaLigaId,
            campSerieAId = campeonatoSerieAId,
            campChampionsId = campeonatoChampionsId,
            campEuropaId = campeonatoEuropaId,
            campLibertadoresId = campeonatoLibertadoresId,
            campSulAmericanaId = campeonatoSulAmericanaId,
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
    val opcoesInglaterra = buildList {
        if (campeonatoPremierId > 0) add(20 to "Premier League")
        if (campeonatoChampionshipId > 0) add(21 to "EFL Championship")
    }
    val opcoesEspanha = buildList {
        if (campeonatoLaLigaId > 0) add(22 to "La Liga")
    }
    val opcoesItalia = buildList {
        if (campeonatoSerieAId > 0) add(23 to "Serie A")
    }
    // Competições continentais, que não pertencem a um país: a Fase de Liga da
    // Champions é uma tabela única de 36 clubes (ver regras_championsleague.md).
    val opcoesEuropa = buildList {
        if (campeonatoChampionsId > 0) add(30 to "Champions League")
        if (campeonatoEuropaId > 0) add(31 to "Europa League")
    }
    // A Libertadores manteve o formato tradicional: a classificação dela são 8
    // tabelas de 4, não uma tabela única (ver regras_libertadores.md).
    val opcoesAmericaDoSul = buildList {
        if (campeonatoLibertadoresId > 0) add(40 to "Libertadores")
        if (campeonatoSulAmericanaId > 0) add(41 to "Copa Sul-Americana")
    }

    val paises = buildList {
        if (opcoesBrasil.isNotEmpty()) add("Brasil")
        if (opcoesArgentina.isNotEmpty()) add("Argentina")
        if (opcoesUruguai.isNotEmpty()) add("Uruguai")
        if (opcoesInglaterra.isNotEmpty()) add("Inglaterra")
        if (opcoesEspanha.isNotEmpty()) add("Espanha")
        if (opcoesItalia.isNotEmpty()) add("Italia")
        if (opcoesEuropa.isNotEmpty()) add("Europa")
        if (opcoesAmericaDoSul.isNotEmpty()) add("América do Sul")
    }

    // País selecionado: derivado da divisão atual
    val paisSelecionado = remember(divisaoSelecionada) {
        when (divisaoSelecionada) {
            in 5..8 -> "Argentina"
            in 9..14 -> "Uruguai"
            in 20..21 -> "Inglaterra"
            22 -> "Espanha"
            23 -> "Italia"
            30, 31 -> "Europa"
            40, 41 -> "América do Sul"
            else -> "Brasil"
        }
    }
    val opcoesDivisaoPais = when (paisSelecionado) {
        "Argentina" -> opcoesArgentina
        "Uruguai" -> opcoesUruguai
        "Inglaterra" -> opcoesInglaterra
        "Espanha" -> opcoesEspanha
        "Italia" -> opcoesItalia
        "Europa" -> opcoesEuropa
        "América do Sul" -> opcoesAmericaDoSul
        else -> opcoesBrasil
    }
    val labelDivisaoAtual = opcoesDivisaoPais.firstOrNull { it.first == divisaoSelecionada }?.second
        ?: opcoesDivisaoPais.firstOrNull()?.second ?: ""

    // Posição do clube do jogador (se na divisão atual e não em modo grupos)
    // 5 e 7 = Apertura/Clausura argentinos, 14 = Competencia uruguaio,
    // 40 = fase de grupos da Libertadores (8 grupos de 4).
    val ehGrupos = divisaoSelecionada == 5 || divisaoSelecionada == 7 ||
        divisaoSelecionada == 14 || divisaoSelecionada == 40 || divisaoSelecionada == 41
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
            .fundoApp()
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
                                "Inglaterra" -> opcoesInglaterra.firstOrNull()?.first ?: 20
                                "Espanha" -> opcoesEspanha.firstOrNull()?.first ?: 22
                                "Italia" -> opcoesItalia.firstOrNull()?.first ?: 23
                                "Europa" -> opcoesEuropa.firstOrNull()?.first ?: 30
                                "América do Sul" -> opcoesAmericaDoSul.firstOrNull()?.first ?: 40
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

        // As continentais sul-americanas nascem SEM fase de grupos: a tabela delas
        // só passa a existir quando a fase prévia termina e o sorteio acontece —
        // a Pré-Libertadores na 40, a Primeira Fase na 41. Até ali a tela ficava
        // em branco, sem dizer ao jogador que não havia nada errado.
        val aindaNaoSorteada = ehGrupos && gruposCompetencia.isEmpty() &&
            (divisaoSelecionada == 40 || divisaoSelecionada == 41)

        if (!ehGrupos) {
            TabelaColunasHeader()
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (aindaNaoSorteada) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(Spacing.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (divisaoSelecionada == 41)
                                "Fase de grupos ainda não sorteada — a Primeira Fase está em andamento"
                            else
                                "Fase de grupos ainda não sorteada — a Pré-Libertadores está em andamento",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (ehGrupos) {
                gruposCompetencia.forEach { (grupo, grupoTabela) ->
                    item { GrupoHeader("Grupo $grupo") }
                    item { TabelaColunasHeader() }
                    itemsIndexed(grupoTabela) { index, item ->
                        val time = times.find { it.id == item.timeId }
                        val nomeTime = time?.nome ?: "Time ${item.timeId}"
                        val escudoRes = time?.escudoRes ?: ""
                        val ehJogador = item.timeId == timeJogadorId
                        // Nas continentais cada posição do grupo tem destino
                        // próprio — oitavas, repescagem, eliminado —, e quem sabe
                        // disso é zonaParaDivisao. O corte único de classificados
                        // vale só para as zonas argentinas e o Competencia uruguaio.
                        val zona = when (divisaoSelecionada) {
                            40, 41 -> zonaParaDivisao(index + 1, divisaoSelecionada)
                                ?: Color.Transparent
                            else -> {
                                val classificadosPorGrupo = if (divisaoSelecionada == 14) 1 else 8
                                if (index < classificadosPorGrupo) LibertadoresBlue
                                else Color.Transparent
                            }
                        }

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
                    val zona = zonaParaDivisao(index + 1, divisaoSelecionada, zonasSerieA, zonasEuropa)

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

        if (!aindaNaoSorteada) {
            LegendaZonas(
                divisaoSelecionada, zonasSerieA, zonasEuropa,
                copaEmAndamento = vagasBrasil.campeaoDaCopa is VagaDeTitulo.Indefinida,
                copaEuropeiaEmAndamento = paisDaDivisaoEuropeia(divisaoSelecionada)
                    ?.let { vagasEuropa.campeaoDaCopa(it) is VagaDeTitulo.Indefinida } == true
            )
        }
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
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            listOf("P", "J", "V", "E", "D", "GP", "GC", "SG").forEach { col ->
                Text(
                    col,
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
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
/** País da liga europeia de um código de aba, ou null se não for uma delas. */
private fun paisDaDivisaoEuropeia(divisao: Int): String? = when (divisao) {
    20   -> "Inglaterra"   // Premier League
    22   -> "Espanha"      // La Liga
    23   -> "Italia"       // Serie A
    else -> null
}

private fun zonaParaDivisao(
    posicao: Int,
    divisao: Int,
    zonasSerieA: ZonasDoBrasileirao? = null,
    zonasEuropa: ZonasEuropeias? = null
): Color? = when (divisao) {
    // A Série A é a única divisão cujas zonas vêm das COTAS das competições
    // continentais, e não de um número escrito aqui: 7 vagas na Libertadores (5
    // de grupo + 2 na Pré) e 6 na Sul-Americana, com o efeito cascata por cima.
    // Ver VagasDoBrasileirao.
    1 -> when (zonasSerieA?.zona(posicao) ?: ZonaBrasileirao.NENHUMA) {
        ZonaBrasileirao.LIBERTADORES_GRUPOS -> LibertadoresBlue
        ZonaBrasileirao.LIBERTADORES_PRE    -> PlayoffOrange
        ZonaBrasileirao.SUL_AMERICANA       -> SulAmericanaTeal
        ZonaBrasileirao.REBAIXAMENTO        -> RelegationRed
        ZonaBrasileirao.NENHUMA             -> null
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
    // As duas competições continentais têm a mesma estrutura de classificação:
    // 1º–8º direto às oitavas, 9º–24º no play-off, 25º–36º eliminados.
    30, 31 -> when {                        // Fase de Liga (Champions / Europa League)
        posicao <= 8 -> LibertadoresBlue    // passa direto às oitavas
        posicao <= 24 -> PlayoffOrange      // play-off de acesso às oitavas
        else -> null                        // 25º–36º: eliminados
    }
    40 -> when {                            // Grupo da Libertadores (4 clubes)
        posicao <= 2 -> LibertadoresBlue    // avançam às oitavas
        posicao == 3 -> SulAmericanaTeal    // 3º vai à Copa Sul-Americana
        else -> null                        // 4º: eliminado
    }
    41 -> when {                            // Grupo da Sul-Americana (4 clubes)
        posicao == 1 -> LibertadoresBlue    // 1º vai direto às oitavas
        posicao == 2 -> PlayoffOrange       // 2º vai aos playoffs
        else -> null                        // 3º e 4º: eliminados
    }
    // Premier League, La Liga e Serie A: as vagas continentais vem das COTAS das
    // listas de acesso, nao de um numero escrito aqui — Champions 5/5/4 e Europa
    // League 2 para os tres, com a vaga da copa nacional cascateando. A unica das
    // tres com rebaixamento e a Premier, que tem a Championship abaixo; La Liga e
    // Serie A nao tem segunda divisao no jogo.
    20, 22, 23 -> when (zonasEuropa?.zona(posicao) ?: ZonaEuropeia.NENHUMA) {
        ZonaEuropeia.CHAMPIONS     -> LibertadoresBlue
        ZonaEuropeia.EUROPA_LEAGUE -> SulAmericanaTeal
        ZonaEuropeia.NENHUMA       -> if (divisao == 20 && posicao >= 18) RelegationRed else null
    }
    else -> when {
        posicao <= 4 -> LibertadoresBlue
        else -> null
    }
}

@Composable
private fun LegendaZonas(
    divisao: Int,
    zonasSerieA: ZonasDoBrasileirao? = null,
    zonasEuropa: ZonasEuropeias? = null,
    copaEmAndamento: Boolean = false,
    copaEuropeiaEmAndamento: Boolean = false
) {
    val itens = when (divisao) {
        1 -> buildList {
            // As contagens saem da MESMA distribuição que pinta as linhas, então
            // a legenda nunca diz um número e a tabela mostra outro — inclusive
            // quando a cascata estica as vagas.
            val grupos = zonasSerieA?.quantas(ZonaBrasileirao.LIBERTADORES_GRUPOS) ?: 0
            val pre = zonasSerieA?.quantas(ZonaBrasileirao.LIBERTADORES_PRE) ?: 0
            val sulAmericana = zonasSerieA?.quantas(ZonaBrasileirao.SUL_AMERICANA) ?: 0
            fun vagas(n: Int) = if (n == 1) "1 vaga" else "$n vagas"
            if (grupos > 0) add(LibertadoresBlue to "${vagas(grupos)} Libertadores (grupos)")
            if (pre > 0) add(PlayoffOrange to "${vagas(pre)} Pré-Libertadores")
            if (sulAmericana > 0) add(SulAmericanaTeal to "${vagas(sulAmericana)} Sul-Americana")
            // As duas vagas da Copa do Brasil ainda não têm dono, e por isso não
            // estão pintadas em ninguém. Dizer isso evita a leitura de que o
            // Brasil só tem as vagas que a tabela mostra.
            if (copaEmAndamento) {
                add(LibertadoresBlue to "+2 pela Copa do Brasil (campeão e vice)")
            }
            add(RelegationRed to "Z${VagasDoBrasileirao.REBAIXADOS} Rebaixamento")
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
        30, 31 -> listOf(
            LibertadoresBlue to "G8 Oitavas",
            PlayoffOrange to "9º–24º Play-off"
        )
        40 -> listOf(
            LibertadoresBlue to "G2 Oitavas",
            SulAmericanaTeal to "3º Sul-Americana"
        )
        41 -> listOf(
            LibertadoresBlue to "1º Oitavas",
            PlayoffOrange to "2º Playoffs"
        )
        20, 22, 23 -> buildList {
            // As contagens saem da MESMA distribuição que pinta as linhas, para a
            // legenda nunca dizer um número e a tabela mostrar outro.
            val champions = zonasEuropa?.quantas(ZonaEuropeia.CHAMPIONS) ?: 0
            val europa = zonasEuropa?.quantas(ZonaEuropeia.EUROPA_LEAGUE) ?: 0
            fun vagas(n: Int) = if (n == 1) "1 vaga" else "$n vagas"
            if (champions > 0) add(LibertadoresBlue to "${vagas(champions)} Champions League")
            if (europa > 0) add(SulAmericanaTeal to "${vagas(europa)} Europa League")
            // A vaga da copa nacional ainda não tem dono, e por isso não está
            // pintada em ninguém. Dizer isso evita a leitura de que o país só tem
            // as vagas que a tabela mostra.
            if (copaEuropeiaEmAndamento) {
                add(SulAmericanaTeal to "+1 pela copa nacional (campeão)")
            }
            // Das três, só a Premier tem divisão abaixo no jogo.
            if (divisao == 20) add(RelegationRed to "Z3 Rebaixamento")
        }
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
    "Inglaterra" -> "🏴󠁧󠁢󠁥󠁮󠁧󠁿"
    "Espanha" -> "🇪🇸"
    "Italia" -> "🇮🇹"
    "Europa" -> "🇪🇺"
    "América do Sul" -> "🌎"
    else -> ""
}
