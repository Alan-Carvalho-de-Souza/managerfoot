package br.com.managerfoot.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
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
import br.com.managerfoot.data.database.entities.EstiloJogo
import br.com.managerfoot.data.database.entities.TipoEvento
import br.com.managerfoot.domain.model.Escalacao
import br.com.managerfoot.domain.model.EventoSimulado
import br.com.managerfoot.domain.model.JogadorNaEscalacao
import br.com.managerfoot.domain.model.ResultadoPartida
import br.com.managerfoot.domain.model.ResultadoPenaltis
import br.com.managerfoot.presentation.ui.components.TeamBadge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  Dados de um evento enriquecido para exibiÃ§Ã£o
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
data class EventoExibicao(
    val minuto: Int,
    val tipo: TipoEvento,
    val descricao: String,
    val timeId: Int,
    val nomeTime: String,
    val jogadorId: Int = -1,
    val pularExibicao: Boolean = false
)

// Registra uma substituição realizada (intervalo ou lesão em jogo)
data class SubstituicaoIntervalo(
    val minuto: Int = 46,
    val sai: JogadorNaEscalacao,
    val entra: JogadorNaEscalacao,
    val timeId: Int,
    val ehLesao: Boolean = false  // true = substituição forçada por lesão (não repetir no feed do intervalo)
)

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  PartidaSimulacaoScreen
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
fun PartidaSimulacaoScreen(
    resultado: ResultadoPartida,
    nomeTimeCasa: String,
    nomeTimeFora: String,
    escudoTimeCasa: String = "",
    escudoTimeFora: String = "",
    escalacaoJogador: Escalacao? = null,
    isTimeCasaOJogador: Boolean = true,
    penaltisResultado: ResultadoPenaltis? = null,
    onPenaltisConfirmados: ((cobradores: List<JogadorNaEscalacao>, goleiroDefesa: Int) -> Unit)? = null,
    onSimulacaoFinalizada: () -> Unit
) {
    // Estado do placar
    var golsCasaAtual by remember { mutableIntStateOf(0) }
    var golsForaAtual by remember { mutableIntStateOf(0) }

    // RelÃ³gio da partida
    var minutoAtual by remember { mutableIntStateOf(0) }
    var faseAtual by remember { mutableStateOf("PRÉ-JOGO") }
    var simulacaoEncerrada by remember { mutableStateOf(false) }

    // Intervalo: pausa para intervenÃ§Ã£o do jogador
    var pausadoNoIntervalo by remember { mutableStateOf(false) }
    val intervaloDeferred = remember { CompletableDeferred<Unit>() }

    // Estado do painel do intervalo
    var formacaoAtual by remember { mutableStateOf(escalacaoJogador?.time?.taticaFormacao ?: "4-4-2") }
    var estiloAtual by remember { mutableStateOf(escalacaoJogador?.time?.estiloJogo ?: EstiloJogo.EQUILIBRADO) }
    val substituicoes = remember { mutableStateListOf<SubstituicaoIntervalo>() }
    // Jogadores titulares correntes (pode perder jogadores pÃ³s-substituiÃ§Ã£o de tela)
    val titularesAtuais = remember { mutableStateListOf<JogadorNaEscalacao>() }
    val reservasDisponiveis = remember { mutableStateListOf<JogadorNaEscalacao>() }

    // Feed de eventos já exibidos
    val eventosExibidos = remember { mutableStateListOf<EventoExibicao>() }
    val listState = rememberLazyListState()

    // ID do time controlado pelo jogador (constante para esta composição)
    val jogadorTimeId = if (isTimeCasaOJogador) resultado.timeCasaId else resultado.timeForaId

    // Lesão em campo: pausa a animação para o jogador escolher o substituto
    var pausadoPorLesao by remember { mutableStateOf(false) }
    var jogadorLesionadoAtual by remember { mutableStateOf<JogadorNaEscalacao?>(null) }
    val lesaoDeferred = remember { mutableStateOf<CompletableDeferred<JogadorNaEscalacao?>?>(null) }

    // Enriquece eventos e pré-calcula quais sub-eventos da IA devem ser omitidos
    // (os que seguem lesões do time do jogador — o usuário escolherá quem entra).
    val eventosOrdenados = remember(resultado, isTimeCasaOJogador, escalacaoJogador) {
        val sortedEvts = resultado.eventos.sortedBy { it.minuto }
        val teamId = if (isTimeCasaOJogador) resultado.timeCasaId else resultado.timeForaId
        val skipIndices: Set<Int> = if (escalacaoJogador == null) emptySet() else buildSet {
            sortedEvts.forEachIndexed { i, ev ->
                if (ev.tipo == TipoEvento.LESAO && ev.timeId == teamId) {
                    val lesionadoId = ev.jogadorId
                    var k = i + 1; var saiDone = false; var entraDone = false
                    while (k < sortedEvts.size && !(saiDone && entraDone)) {
                        val ne = sortedEvts[k]
                        if (ne.timeId == teamId) {
                            if (!saiDone && ne.tipo == TipoEvento.SUBSTITUICAO_SAI && ne.jogadorId == lesionadoId) {
                                add(k); saiDone = true
                            } else if (saiDone && !entraDone && ne.tipo == TipoEvento.SUBSTITUICAO_ENTRA) {
                                add(k); entraDone = true
                            }
                        }
                        k++
                    }
                }
            }
        }
        sortedEvts.mapIndexed { i, ev ->
            val ehCasa = ev.timeId == resultado.timeCasaId
            EventoExibicao(
                minuto = ev.minuto,
                tipo = ev.tipo,
                descricao = ev.descricao,
                timeId = ev.timeId,
                nomeTime = if (ehCasa) nomeTimeCasa else nomeTimeFora,
                jogadorId = ev.jogadorId,
                pularExibicao = i in skipIndices
            )
        }
    }

    // AcrÃ©scimos gerados aleatoriamente
    val acrescimo1Tempo = remember { (1..5).random() }
    val acrescimo2Tempo = remember { (2..7).random() }

    // Inicializa titulares/reservas do painel a partir da escalaÃ§Ã£o passada
    LaunchedEffect(escalacaoJogador) {
        if (escalacaoJogador != null) {
            titularesAtuais.clear()
            titularesAtuais.addAll(escalacaoJogador.titulares)
            reservasDisponiveis.clear()
            reservasDisponiveis.addAll(escalacaoJogador.reservas)
        }
    }

    // SimulaÃ§Ã£o do relÃ³gio e eventos
    LaunchedEffect(Unit) {
        // PrÃ©-jogo
        faseAtual = "PRÉ-JOGO"
        delay(1500)

        // Primeiro tempo (0-45 + acrÃ©scimo)
        faseAtual = "1º TEMPO"
        val limiteT1 = 45 + acrescimo1Tempo
        for (minuto in 1..limiteT1) {
            minutoAtual = minuto

            // Exibir eventos desse minuto — loop `for` para manter o CoroutineScope do
            // LaunchedEffect como receptor implícito, permitindo usar `launch {}`.
            for (ev in eventosOrdenados.filter { it.minuto == minuto && it.minuto <= 45 }) {
                if (ev.pularExibicao) continue  // sub automática da IA omitida — usuário escolhe
                eventosExibidos.add(0, ev)
                if (ev.tipo == TipoEvento.GOL || ev.tipo == TipoEvento.PENALTI_CONVERTIDO) {
                    if (ev.timeId == resultado.timeCasaId) golsCasaAtual++
                    else golsForaAtual++
                }
                // Cartão vermelho: remove o expulso dos titulares (joga com um a menos, sem reposição)
                if (ev.tipo == TipoEvento.CARTAO_VERMELHO && ev.timeId == jogadorTimeId && escalacaoJogador != null) {
                    titularesAtuais.removeIf { it.jogador.id == ev.jogadorId }
                }
                // Lesão: pausa a animação e pede ao usuário para escolher o substituto
                if (ev.tipo == TipoEvento.LESAO && ev.timeId == jogadorTimeId && escalacaoJogador != null) {
                    val lesionado = titularesAtuais.find { it.jogador.id == ev.jogadorId }
                    if (lesionado != null) {
                        titularesAtuais.remove(lesionado)
                        jogadorLesionadoAtual = lesionado
                        val deferred = CompletableDeferred<JogadorNaEscalacao?>()
                        lesaoDeferred.value = deferred
                        pausadoPorLesao = true
                        val substituto = deferred.await()
                        pausadoPorLesao = false
                        lesaoDeferred.value = null
                        if (substituto != null) {
                            val nomeTimeJog = if (isTimeCasaOJogador) nomeTimeCasa else nomeTimeFora
                            val minSub = (ev.minuto + 1).coerceAtMost(90)
                            substituicoes.add(SubstituicaoIntervalo(minuto = minSub, sai = lesionado, entra = substituto, timeId = ev.timeId, ehLesao = true))
                            eventosExibidos.add(0, EventoExibicao(minSub, TipoEvento.SUBSTITUICAO_SAI, "↓ ${lesionado.jogador.nomeAbreviado}", ev.timeId, nomeTimeJog))
                            eventosExibidos.add(0, EventoExibicao(minSub, TipoEvento.SUBSTITUICAO_ENTRA, "↑ ${substituto.jogador.nomeAbreviado}", ev.timeId, nomeTimeJog))
                            launch { listState.animateScrollToItem(0) }
                        }
                    }
                }
                // launch{} isola a animação: se o usuário scrollar manualmente,
                // apenas o filho é cancelado — o loop do relógio continua.
                launch { listState.animateScrollToItem(0) }
                delay(800) // pausa extra em eventos
            }

            // Velocidade do relógio: eventos importantes = mais devagar
            val temEventoNesteMinuto = eventosOrdenados.any {
                it.minuto == minuto && it.minuto <= 45
            }
            delay(if (temEventoNesteMinuto) 300L else 80L)
        }

        // Intervalo â€” exibe separador e pausa para intervenÃ§Ã£o do jogador
        faseAtual = "INTERVALO"
        eventosExibidos.add(
            0, EventoExibicao(45, TipoEvento.GOL, "--- Intervalo ---", -1, "")
        )

        // SÃ³ mostra o painel se o jogador tem dados de escalaÃ§Ã£o
        if (escalacaoJogador != null) {
            pausadoNoIntervalo = true
            intervaloDeferred.await()
            pausadoNoIntervalo = false

            // Injeta eventos de substituiÃ§Ã£o no feed
            val timeJogadorId = if (isTimeCasaOJogador) resultado.timeCasaId else resultado.timeForaId
            val nomeTime = if (isTimeCasaOJogador) nomeTimeCasa else nomeTimeFora
            // Apenas subs do intervalo — as de lesão já foram injetadas no feed durante o 1º tempo
            substituicoes.filter { !it.ehLesao }.forEach { sub ->
                eventosExibidos.add(0, EventoExibicao(sub.minuto, TipoEvento.SUBSTITUICAO_SAI, "↓ ${sub.sai.jogador.nomeAbreviado}", timeJogadorId, nomeTime))
                eventosExibidos.add(0, EventoExibicao(sub.minuto, TipoEvento.SUBSTITUICAO_ENTRA, "↑ ${sub.entra.jogador.nomeAbreviado}", timeJogadorId, nomeTime))
            }
            if (substituicoes.isNotEmpty()) launch { listState.animateScrollToItem(0) }
        } else {
            delay(2500)
        }

        // Segundo tempo (46-90 + acrÃ©scimo)
        faseAtual = "2º TEMPO"
        val limiteT2 = 90 + acrescimo2Tempo
        for (minuto in 46..limiteT2) {
            minutoAtual = minuto

            for (ev in eventosOrdenados.filter { it.minuto == minuto && it.minuto > 45 }) {
                if (ev.pularExibicao) continue
                eventosExibidos.add(0, ev)
                if (ev.tipo == TipoEvento.GOL || ev.tipo == TipoEvento.PENALTI_CONVERTIDO) {
                    if (ev.timeId == resultado.timeCasaId) golsCasaAtual++
                    else golsForaAtual++
                }
                // Cartão vermelho: remove o expulso dos titulares
                if (ev.tipo == TipoEvento.CARTAO_VERMELHO && ev.timeId == jogadorTimeId && escalacaoJogador != null) {
                    titularesAtuais.removeIf { it.jogador.id == ev.jogadorId }
                }
                // Lesão no 2º tempo: pausa e pede substituto
                if (ev.tipo == TipoEvento.LESAO && ev.timeId == jogadorTimeId && escalacaoJogador != null) {
                    val lesionado = titularesAtuais.find { it.jogador.id == ev.jogadorId }
                    if (lesionado != null) {
                        titularesAtuais.remove(lesionado)
                        jogadorLesionadoAtual = lesionado
                        val deferred = CompletableDeferred<JogadorNaEscalacao?>()
                        lesaoDeferred.value = deferred
                        pausadoPorLesao = true
                        val substituto = deferred.await()
                        pausadoPorLesao = false
                        lesaoDeferred.value = null
                        if (substituto != null) {
                            val nomeTimeJog = if (isTimeCasaOJogador) nomeTimeCasa else nomeTimeFora
                            val minSub = (ev.minuto + 1).coerceAtMost(90)
                            substituicoes.add(SubstituicaoIntervalo(minuto = minSub, sai = lesionado, entra = substituto, timeId = ev.timeId, ehLesao = true))
                            eventosExibidos.add(0, EventoExibicao(minSub, TipoEvento.SUBSTITUICAO_SAI, "↓ ${lesionado.jogador.nomeAbreviado}", ev.timeId, nomeTimeJog))
                            eventosExibidos.add(0, EventoExibicao(minSub, TipoEvento.SUBSTITUICAO_ENTRA, "↑ ${substituto.jogador.nomeAbreviado}", ev.timeId, nomeTimeJog))
                            launch { listState.animateScrollToItem(0) }
                        }
                    }
                }
                launch { listState.animateScrollToItem(0) }
                delay(800)
            }

            val temEventoNesteMinuto = eventosOrdenados.any {
                it.minuto == minuto && it.minuto > 45
            }
            delay(if (temEventoNesteMinuto) 300L else 80L)
        }

        // Fim de jogo
        faseAtual = "FIM DE JOGO"
        simulacaoEncerrada = true
    }

    // Painel de lesão — interrompe a animação para substituição imediata
    if (pausadoPorLesao) {
        LesaoPainel(
            jogadorLesionado = jogadorLesionadoAtual,
            reservas = reservasDisponiveis.toList(),
            onSubstituicao = { entra ->
                titularesAtuais.add(entra)
                reservasDisponiveis.remove(entra)
                lesaoDeferred.value?.complete(entra)
            },
            onSemReservas = { lesaoDeferred.value?.complete(null) }
        )
        return
    }

    // Painel do intervalo (mostrado sobre a tela de jogo)
    if (pausadoNoIntervalo) {
        IntervaloPainel(
            formacaoAtual = formacaoAtual,
            estiloAtual = estiloAtual,
            titulares = titularesAtuais,
            reservas = reservasDisponiveis,
            substituicoes = substituicoes,
            onFormacaoChange = { formacaoAtual = it },
            onEstiloChange = { estiloAtual = it },
            onSubstituicao = { sai, entra ->
                if (substituicoes.size < 6) {
                    substituicoes.add(SubstituicaoIntervalo(sai = sai, entra = entra, timeId = resultado.timeCasaId))
                    titularesAtuais.remove(sai)
                    titularesAtuais.add(entra)
                    reservasDisponiveis.remove(entra)
                    reservasDisponiveis.add(sai)
                }
            },
            onContinuar = { intervaloDeferred.complete(Unit) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // â”€â”€ Placar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Fase da partida
                Text(
                    text = if (minutoAtual > 0 && !simulacaoEncerrada)
                        "$faseAtual  ${minutoAtual}'"
                    else faseAtual,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )

                Spacer(Modifier.height(12.dp))

                // Times e placar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time da casa
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        TeamBadge(
                            nome = nomeTimeCasa,
                            escudoRes = escudoTimeCasa,
                            size = 44.dp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = nomeTimeCasa,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                        Text(
                            text = "Casa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        )
                    }

                    // Placar central
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedContent(
                            targetState = golsCasaAtual,
                            transitionSpec = { slideInVertically { -it } togetherWith slideOutVertically { it } },
                            label = "gols_casa"
                        ) { gols ->
                            Text(
                                text = gols.toString(),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            text = "x",
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        )
                        AnimatedContent(
                            targetState = golsForaAtual,
                            transitionSpec = { slideInVertically { -it } togetherWith slideOutVertically { it } },
                            label = "gols_fora"
                        ) { gols ->
                            Text(
                                text = gols.toString(),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // Time visitante
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        TeamBadge(
                            nome = nomeTimeFora,
                            escudoRes = escudoTimeFora,
                            size = 44.dp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = nomeTimeFora,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                        Text(
                            text = "Visitante",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        )
                    }
                }

                // Barra de progresso da partida
                Spacer(Modifier.height(12.dp))
                val progresso = (minutoAtual / (90f + acrescimo2Tempo)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progresso },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
                    Text("45'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
                    Text("90'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
                }
            }
        }

        // â”€â”€ Feed de eventos â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Text(
            text = "Eventos da partida",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            reverseLayout = false
        ) {
            items(eventosExibidos) { evento ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically { -it } + fadeIn()
                ) {
                    EventoCard(evento = evento, nomeTimeCasa = nomeTimeCasa)
                }
            }
        }

        // â”€â”€ BotÃ£o de encerrar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (simulacaoEncerrada) {
            if (resultado.precisaPenaltis && penaltisResultado == null) {
                PenaltiSelecaoPainel(
                    titulares      = titularesAtuais.toList(),
                    nomeTimeJogador    = if (isTimeCasaOJogador) nomeTimeCasa else nomeTimeFora,
                    nomeAdversario     = if (isTimeCasaOJogador) nomeTimeFora else nomeTimeCasa,
                    agregadoJogador    = if (isTimeCasaOJogador) resultado.golsAgregadoCasa else resultado.golsAgregadoFora,
                    agregadoAdversario = if (isTimeCasaOJogador) resultado.golsAgregadoFora else resultado.golsAgregadoCasa,
                    onConfirmar = { cobradores ->
                        val gk = titularesAtuais.firstOrNull {
                            it.posicaoUsada.setor == br.com.managerfoot.data.database.entities.Setor.GOLEIRO
                        }
                        onPenaltisConfirmados?.invoke(cobradores, gk?.jogador?.defesa ?: 70)
                    }
                )
            } else if (resultado.precisaPenaltis && penaltisResultado != null) {
                PenaltiResultadoPainel(
                    penaltis      = penaltisResultado,
                    nomeTimeCasa  = nomeTimeCasa,
                    nomeTimeFora  = nomeTimeFora,
                    timeJogadorId = if (isTimeCasaOJogador) resultado.timeCasaId else resultado.timeForaId,
                    onConcluir    = onSimulacaoFinalizada
                )
            } else {
                Button(
                    onClick = onSimulacaoFinalizada,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Voltar ao painel")
                }
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  Painel do intervalo (tÃ¡tica + substituiÃ§Ãµes)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// ─────────────────────────────────────────────────────────────
//  PenaltiSelecaoPainel  ─  jogador escolhe os 5 cobradores
// ─────────────────────────────────────────────────────────────
@Composable
private fun PenaltiSelecaoPainel(
    titulares: List<JogadorNaEscalacao>,
    nomeTimeJogador: String,
    nomeAdversario: String,
    agregadoJogador: Int,
    agregadoAdversario: Int,
    onConfirmar: (cobradores: List<JogadorNaEscalacao>) -> Unit
) {
    val selecionados = remember { mutableStateListOf<JogadorNaEscalacao>() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "DISPUTA DE PÊNALTIS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$nomeTimeJogador $agregadoJogador × $agregadoAdversario $nomeAdversario",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Agregado empatado — decisão nos pênaltis",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
        }

        Text(
            "Escolha os 5 cobradores (em ordem):",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(titulares) { jne ->
                val ordem = selecionados.indexOf(jne) + 1
                val selecionado = ordem > 0
                ListItem(
                    headlineContent = { Text(jne.jogador.nome) },
                    supportingContent = { Text("${jne.posicaoUsada.abreviacao} · Fin: ${jne.jogador.finalizacao}") },
                    trailingContent = {
                        if (selecionado) {
                            OutlinedButton(
                                onClick = { selecionados.remove(jne) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("#$ordem  ✕", style = MaterialTheme.typography.labelMedium)
                            }
                        } else if (selecionados.size < 5) {
                            Button(
                                onClick = { selecionados.add(jne) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("+", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        Text(
            "${selecionados.size}/5 selecionados",
            style = MaterialTheme.typography.labelMedium,
            color = if (selecionados.size >= 5) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { onConfirmar(selecionados.toList()) },
            enabled = selecionados.size >= 5,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Confirmar e disputar pênaltis")
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  PenaltiResultadoPainel  ─  exibe animação da disputa
// ─────────────────────────────────────────────────────────────
@Composable
private fun PenaltiResultadoPainel(
    penaltis: ResultadoPenaltis,
    nomeTimeCasa: String,
    nomeTimeFora: String,
    timeJogadorId: Int,
    onConcluir: () -> Unit
) {
    data class ItemCobranca(val ehCasa: Boolean, val evento: br.com.managerfoot.domain.model.EventoPenalti)
    val cobrancasExibidas  = remember { mutableStateListOf<ItemCobranca>() }
    var animacaoFinalizada by remember { mutableStateOf(false) }
    var golsCasaAtual      by remember { mutableIntStateOf(0) }
    var golsForaAtual      by remember { mutableIntStateOf(0) }
    val listState          = rememberLazyListState()
    val scope              = rememberCoroutineScope()

    val cobrancasIntercaladas = remember(penaltis) {
        val lista = mutableListOf<Pair<Boolean, br.com.managerfoot.domain.model.EventoPenalti>>()
        val n = maxOf(penaltis.cobrancasCasa.size, penaltis.cobrancasFora.size)
        for (i in 0 until n) {
            penaltis.cobrancasCasa.getOrNull(i)?.let { lista.add(true to it) }
            penaltis.cobrancasFora.getOrNull(i)?.let { lista.add(false to it) }
        }
        lista
    }

    LaunchedEffect(penaltis) {
        delay(500)
        cobrancasIntercaladas.forEach { (ehCasa, ev) ->
            if (ehCasa) { if (ev.convertido) golsCasaAtual++ }
            else         { if (ev.convertido) golsForaAtual++ }
            cobrancasExibidas.add(ItemCobranca(ehCasa, ev))
            scope.launch { listState.animateScrollToItem(cobrancasExibidas.size - 1) }
            delay(900)
        }
        animacaoFinalizada = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "DISPUTA DE PÊNALTIS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        nomeTimeCasa,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    AnimatedContent(
                        targetState = golsCasaAtual,
                        transitionSpec = { slideInVertically { -it } togetherWith slideOutVertically { it } },
                        label = "pc"
                    ) { g ->
                        Text(
                            g.toString(), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        " – ",
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                    AnimatedContent(
                        targetState = golsForaAtual,
                        transitionSpec = { slideInVertically { -it } togetherWith slideOutVertically { it } },
                        label = "pf"
                    ) { g ->
                        Text(
                            g.toString(), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        nomeTimeFora,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
                if (animacaoFinalizada) {
                    Spacer(Modifier.height(8.dp))
                    val vencedorNome = if (penaltis.vencedorId == penaltis.timeCasaId) nomeTimeCasa else nomeTimeFora
                    Text(
                        "🏆 $vencedorNome avança!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (penaltis.vencedorId == timeJogadorId)
                            Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(cobrancasExibidas) { item ->
                AnimatedVisibility(visible = true, enter = slideInVertically { -it } + fadeIn()) {
                    val icone    = if (item.evento.convertido) "✅" else "❌"
                    val nomeTime = if (item.ehCasa) nomeTimeCasa else nomeTimeFora
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (item.evento.convertido) Color(0xFF1B5E20).copy(alpha = 0.12f)
                                else Color(0xFFC62828).copy(alpha = 0.10f)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = if (item.ehCasa) Arrangement.Start else Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.ehCasa) {
                            Text(icone, fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(item.evento.nomeAbrev, style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium)
                                Text(nomeTime, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(item.evento.nomeAbrev, style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium)
                                Text(nomeTime, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(icone, fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        if (animacaoFinalizada) {
            Button(
                onClick = onConcluir,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Voltar ao painel")
            }
        }
    }
}

@Composable
private fun IntervaloPainel(
    formacaoAtual: String,
    estiloAtual: EstiloJogo,
    titulares: List<JogadorNaEscalacao>,
    reservas: List<JogadorNaEscalacao>,
    substituicoes: List<SubstituicaoIntervalo>,
    onFormacaoChange: (String) -> Unit,
    onEstiloChange: (EstiloJogo) -> Unit,
    onSubstituicao: (sai: JogadorNaEscalacao, entra: JogadorNaEscalacao) -> Unit,
    onContinuar: () -> Unit
) {
    var abaAtiva by remember { mutableIntStateOf(0) }
    // Titular selecionado aguardando escolha do reserva
    var titularSelecionado by remember { mutableStateOf<JogadorNaEscalacao?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // CabeÃ§alho
        Card(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("INTERVALO", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Faça sua intervenção antes do 2º tempo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${substituicoes.size}/6 substituições realizadas",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (substituicoes.size >= 6) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        TabRow(selectedTabIndex = abaAtiva) {
            Tab(selected = abaAtiva == 0, onClick = { abaAtiva = 0; titularSelecionado = null }, text = { Text("Substituições") })
            Tab(selected = abaAtiva == 1, onClick = { abaAtiva = 1 }, text = { Text("Tática") })
        }

        Box(Modifier.weight(1f)) {
            when (abaAtiva) {
                0 -> SubstituicoesTab(
                    titulares = titulares,
                    reservas = reservas,
                    substituicoes = substituicoes,
                    titularSelecionado = titularSelecionado,
                    onSelecionarTitular = { titularSelecionado = it },
                    onSubstituicao = { sai, entra ->
                        onSubstituicao(sai, entra)
                        titularSelecionado = null
                    }
                )
                1 -> TaticaIntervaloPainel(
                    formacaoAtual = formacaoAtual,
                    estiloAtual = estiloAtual,
                    onFormacaoChange = onFormacaoChange,
                    onEstiloChange = onEstiloChange
                )
            }
        }

        Button(
            onClick = onContinuar,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Continuar para o 2º Tempo")
        }
    }
}

@Composable
private fun SubstituicoesTab(
    titulares: List<JogadorNaEscalacao>,
    reservas: List<JogadorNaEscalacao>,
    substituicoes: List<SubstituicaoIntervalo>,
    titularSelecionado: JogadorNaEscalacao?,
    onSelecionarTitular: (JogadorNaEscalacao?) -> Unit,
    onSubstituicao: (sai: JogadorNaEscalacao, entra: JogadorNaEscalacao) -> Unit
) {
    val podeFazerMais = substituicoes.size < 6

    if (titularSelecionado == null) {
        // Fase 1: Escolher quem sai
        Column(Modifier.fillMaxSize()) {
            Text(
                text = if (podeFazerMais) "Escolha quem vai sair:" else "Limite de 6 substituições atingido",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (podeFazerMais) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error
            )
            LazyColumn {
                items(titulares) { jne ->
                    ListItem(
                        headlineContent = { Text(jne.jogador.nome) },
                        supportingContent = { Text("${jne.posicaoUsada.abreviacao} · Força ${jne.jogador.forca}") },
                        trailingContent = {
                            if (podeFazerMais) {
                                OutlinedButton(
                                    onClick = { onSelecionarTitular(jne) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Substituir", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    } else {
        // Fase 2: Escolher quem entra
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Substituindo:", style = MaterialTheme.typography.labelSmall)
                    Text(titularSelecionado.jogador.nome, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { onSelecionarTitular(null) }) { Text("Cancelar") }
            }
            Text(
                text = "Escolha quem entra:",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
            LazyColumn {
                items(reservas) { jne ->
                    ListItem(
                        headlineContent = { Text(jne.jogador.nome) },
                        supportingContent = { Text("${jne.posicaoUsada.abreviacao} · Força ${jne.jogador.forca}") },
                        trailingContent = {
                            Button(
                                onClick = { onSubstituicao(titularSelecionado, jne) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Colocar", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaticaIntervaloPainel(
    formacaoAtual: String,
    estiloAtual: EstiloJogo,
    onFormacaoChange: (String) -> Unit,
    onEstiloChange: (EstiloJogo) -> Unit
) {
    val formacoes = listOf("4-4-2", "4-3-3", "3-5-2", "4-2-3-1", "5-3-2", "4-1-4-1")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Formação tática", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formacoes.forEach { f ->
                    FilterChip(selected = f == formacaoAtual, onClick = { onFormacaoChange(f) }, label = { Text(f) })
                }
            }
        }

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estilo de jogo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EstiloJogo.entries.forEach { estilo ->
                    val label = when (estilo) {
                        EstiloJogo.OFENSIVO      -> "⚔️ Ofensivo"
                        EstiloJogo.EQUILIBRADO   -> "⚖️ Equilibrado"
                        EstiloJogo.DEFENSIVO     -> "🛡️ Defensivo"
                        EstiloJogo.CONTRA_ATAQUE -> "⚡ Contra-ataque"
                    }
                    FilterChip(selected = estilo == estiloAtual, onClick = { onEstiloChange(estilo) }, label = { Text(label) })
                }
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  Card de evento individual no feed
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
fun EventoCard(evento: EventoExibicao, nomeTimeCasa: String) {
    // Linha de intervalo
    if (evento.timeId == -1) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            thickness = 1.dp
        )
        Text(
            text = "INTERVALO",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            thickness = 1.dp
        )
        return
    }

    val ehCasa = evento.nomeTime == nomeTimeCasa
    val (icone, corFundo) = when (evento.tipo) {
        TipoEvento.GOL, TipoEvento.PENALTI_CONVERTIDO ->
            "⚽" to Color(0xFF1B5E20).copy(alpha = 0.15f)
        TipoEvento.CARTAO_AMARELO ->
            "🟨" to Color(0xFFF9A825).copy(alpha = 0.15f)
        TipoEvento.CARTAO_VERMELHO ->
            "🟥" to Color(0xFFC62828).copy(alpha = 0.15f)
        TipoEvento.LESAO ->
            "🚑" to Color(0xFF880E4F).copy(alpha = 0.15f)
        TipoEvento.PENALTI_PERDIDO ->
            "❌" to Color(0xFFE65100).copy(alpha = 0.15f)
        TipoEvento.SUBSTITUICAO_ENTRA ->
            "↑" to Color(0xFF1565C0).copy(alpha = 0.12f)
        TipoEvento.SUBSTITUICAO_SAI ->
            "↓" to Color(0xFF1565C0).copy(alpha = 0.08f)
        else -> "📋" to MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(corFundo)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (ehCasa) Arrangement.Start else Arrangement.End
    ) {
        if (ehCasa) {
            // Minuto
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Text(
                    text = "${evento.minuto}'",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(text = icone, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = evento.descricao,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = evento.nomeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = evento.descricao,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                )
                Text(
                    text = evento.nomeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(text = icone, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Text(
                    text = "${evento.minuto}'",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Painel de substituição por lesão (interrompe a partida)
// ─────────────────────────────────────────────────────────────
@Composable
private fun LesaoPainel(
    jogadorLesionado: JogadorNaEscalacao?,
    reservas: List<JogadorNaEscalacao>,
    onSubstituicao: (JogadorNaEscalacao) -> Unit,
    onSemReservas: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "🚑 JOGADOR LESIONADO",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (jogadorLesionado != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${jogadorLesionado.jogador.nome} (${jogadorLesionado.posicaoUsada.abreviacao}) saiu de campo lesionado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (reservas.isEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Sem reservas disponíveis.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "O time continuará com um jogador a menos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onSemReservas,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text("Continuar") }
        } else {
            Text(
                "Escolha o substituto:",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
            LazyColumn(Modifier.weight(1f)) {
                items(reservas) { jne ->
                    ListItem(
                        headlineContent = { Text(jne.jogador.nome) },
                        supportingContent = { Text("${jne.posicaoUsada.abreviacao} · Força ${jne.jogador.forca}") },
                        trailingContent = {
                            Button(
                                onClick = { onSubstituicao(jne) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Colocar", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}
