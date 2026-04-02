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
import br.com.managerfoot.domain.model.InfoSubstituicao
import br.com.managerfoot.domain.model.ResultadoPartida
import br.com.managerfoot.domain.model.ResultadoPenaltis
import br.com.managerfoot.presentation.ui.components.TeamBadge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalConfiguration
import br.com.managerfoot.domain.model.DadosPenaltiAdversario
import br.com.managerfoot.domain.model.EventoPenalti
import kotlin.random.Random
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
    dadosPenaltisAdversario: DadosPenaltiAdversario? = null,
    penaltisInterativoConcluido: Boolean = false,
    onPenaltisConfirmados: ((ResultadoPenaltis) -> Unit)? = null,
    onObterEventosSegundoTempo: (suspend (List<JogadorNaEscalacao>, List<JogadorNaEscalacao>, List<InfoSubstituicao>, String, EstiloJogo, Int, Int) -> List<EventoSimulado>)? = null,
    onFinalizarPartida: (suspend (Int, Int) -> Unit)? = null,
    onSimulacaoFinalizada: () -> Unit
) {
    // Tamanho adaptativo do placar — reduz em telas mais estreitas (ex.: S23 ~393dp vs S23 Ultra ~412dp)
    // garante que o número de gols + escudos não comprimam o nome dos times
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val scoreFontSize = if (screenWidthDp < 400) 36.sp else 48.sp

    // Estado do placar — chaveado ao ID da partida para garantir reset limpo a cada jogo
    var golsCasaAtual by remember(resultado.partidaId) { mutableIntStateOf(0) }
    var golsForaAtual by remember(resultado.partidaId) { mutableIntStateOf(0) }

    // Relógio da partida
    var minutoAtual by remember(resultado.partidaId) { mutableIntStateOf(0) }
    var faseAtual by remember(resultado.partidaId) { mutableStateOf("PRÉ-JOGO") }
    var simulacaoEncerrada by remember(resultado.partidaId) { mutableStateOf(false) }

    // Intervalo: pausa para intervenção do jogador
    var pausadoNoIntervalo by remember(resultado.partidaId) { mutableStateOf(false) }
    val intervaloDeferred = remember(resultado.partidaId) { CompletableDeferred<Unit>() }

    // Estado do painel do intervalo
    var formacaoAtual by remember(resultado.partidaId) { mutableStateOf(escalacaoJogador?.time?.taticaFormacao ?: "4-4-2") }
    var estiloAtual by remember(resultado.partidaId) { mutableStateOf(escalacaoJogador?.time?.estiloJogo ?: EstiloJogo.EQUILIBRADO) }
    val substituicoes = remember(resultado.partidaId) { mutableStateListOf<SubstituicaoIntervalo>() }
    // Jogadores titulares correntes (pode perder jogadores pós-substituição de tela)
    val titularesAtuais = remember(resultado.partidaId) { mutableStateListOf<JogadorNaEscalacao>() }
    val reservasDisponiveis = remember(resultado.partidaId) { mutableStateListOf<JogadorNaEscalacao>() }

    // Feed de eventos já exibidos
    val eventosExibidos = remember(resultado.partidaId) { mutableStateListOf<EventoExibicao>() }
    val listState = rememberLazyListState()

    // ID do time controlado pelo jogador (constante para esta composição)
    val jogadorTimeId = if (isTimeCasaOJogador) resultado.timeCasaId else resultado.timeForaId

    // Lesão em campo: pausa a animação para o jogador escolher o substituto
    var pausadoPorLesao by remember(resultado.partidaId) { mutableStateOf(false) }
    var jogadorLesionadoAtual by remember(resultado.partidaId) { mutableStateOf<JogadorNaEscalacao?>(null) }
    val lesaoDeferred = remember(resultado.partidaId) { mutableStateOf<CompletableDeferred<JogadorNaEscalacao?>?>(null) }

    // Pausa voluntária "Mexer no Time" durante a partida
    var pausadoParaMexerNoTime by remember(resultado.partidaId) { mutableStateOf(false) }
    val mexerNoTimeDeferred = remember(resultado.partidaId) { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    // Número de substituições já injetadas no feed antes da última pausa (para injetar somente as novas)
    var subsInjetadasNoFeed by remember(resultado.partidaId) { mutableIntStateOf(0) }

    // Enriquece eventos. Os eventos SUBSTITUICAO_SAI/ENTRA do time do jogador humano
    // por lesão nunca chegam no resultado (o motor não os gera — é escolha interativa).
    // Filtramos apenas eventos de tipos que devem ser visíveis na timeline.
    val eventosOrdenados = remember(resultado, isTimeCasaOJogador, escalacaoJogador) {
        val sortedEvts = resultado.eventos.sortedBy { it.minuto }
        sortedEvts.mapIndexed { _, ev ->
            val ehCasa = ev.timeId == resultado.timeCasaId
            EventoExibicao(
                minuto = ev.minuto,
                tipo = ev.tipo,
                descricao = ev.descricao,
                timeId = ev.timeId,
                nomeTime = if (ehCasa) nomeTimeCasa else nomeTimeFora,
                jogadorId = ev.jogadorId,
                pularExibicao = false
            )
        }
    }

    // Acréscimos gerados aleatoriamente — vinculados ao ID da partida para consistência
    val acrescimo1Tempo = remember(resultado.partidaId) { (1..5).random() }
    val acrescimo2Tempo = remember(resultado.partidaId) { (2..7).random() }

    // Inicializa titulares/reservas do painel a partir da escalaÃ§Ã£o passada
    LaunchedEffect(escalacaoJogador) {
        if (escalacaoJogador != null) {
            titularesAtuais.clear()
            titularesAtuais.addAll(escalacaoJogador.titulares)
            reservasDisponiveis.clear()
            reservasDisponiveis.addAll(escalacaoJogador.reservas)
        }
    }

    // Simulação do relógio e eventos — reinicia se a partida mudar (chave = partidaId)
    LaunchedEffect(resultado.partidaId) {
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
                if (ev.pularExibicao) continue
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

            // Pausa voluntária do jogador (Mexer no Time) — aguarda entre minutos
            val def1 = mexerNoTimeDeferred.value
            if (def1 != null) { def1.await(); mexerNoTimeDeferred.value = null }
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

        // Obtém eventos do 2o tempo da engine (reflete mudanças táticas do intervalo)
        val subsInfoIntervalo = substituicoes.map { InfoSubstituicao(it.sai.jogador.id, it.entra.jogador.id, it.minuto, it.timeId) }
        var eventosT2: List<EventoExibicao> = if (onObterEventosSegundoTempo != null) {
            val rawT2 = onObterEventosSegundoTempo.invoke(
                titularesAtuais.toList(),
                reservasDisponiveis.toList(),
                subsInfoIntervalo,
                formacaoAtual,
                estiloAtual,
                46,
                90 + acrescimo2Tempo
            )
            rawT2.map { ev ->
                val ehCasa = ev.timeId == resultado.timeCasaId
                EventoExibicao(ev.minuto, ev.tipo, ev.descricao, ev.timeId,
                    if (ehCasa) nomeTimeCasa else nomeTimeFora, ev.jogadorId)
            }
        } else {
            eventosOrdenados.filter { it.minuto > 45 }
        }

        // Segundo tempo (46-90 + acrescimo)
        faseAtual = "2º TEMPO"
        val limiteT2 = 90 + acrescimo2Tempo
        // IDs dos jogadores substituídos pelo time do jogador (intervalo + lesão 1o tempo)
        var substituidosSaiIds = substituicoes.map { it.sai.jogador.id }.toSet()
        for (minuto in 46..limiteT2) {
            minutoAtual = minuto

            for (ev in eventosT2.filter { it.minuto == minuto }) {
                // Contagem do placar sempre ocorre, independente de quem é o autor
                if (ev.tipo == TipoEvento.GOL || ev.tipo == TipoEvento.PENALTI_CONVERTIDO) {
                    if (ev.timeId == resultado.timeCasaId) golsCasaAtual++
                    else golsForaAtual++
                }

                val ehSubstituidoDoTimeJogador = ev.timeId == jogadorTimeId && ev.jogadorId in substituidosSaiIds
                if (ehSubstituidoDoTimeJogador) continue

                eventosExibidos.add(0, ev)
                // Cartão vermelho: remove o expulso dos titulares
                if (ev.tipo == TipoEvento.CARTAO_VERMELHO && ev.timeId == jogadorTimeId && escalacaoJogador != null) {
                    titularesAtuais.removeIf { it.jogador.id == ev.jogadorId }
                }
                // Lesão no 2o tempo: pausa e pede substituto
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
                            eventosExibidos.add(0, EventoExibicao(minSub, TipoEvento.SUBSTITUICAO_SAI, "\u2193 ${lesionado.jogador.nomeAbreviado}", ev.timeId, nomeTimeJog))
                            eventosExibidos.add(0, EventoExibicao(minSub, TipoEvento.SUBSTITUICAO_ENTRA, "\u2191 ${substituto.jogador.nomeAbreviado}", ev.timeId, nomeTimeJog))
                            launch { listState.animateScrollToItem(0) }
                        }
                    }
                }
                launch { listState.animateScrollToItem(0) }
                delay(800)
            }

            val temEventoNesteMinuto = eventosT2.any { it.minuto == minuto }
            delay(if (temEventoNesteMinuto) 300L else 80L)

            // Pausa voluntária do jogador (Mexer no Time)  aguarda entre minutos
            val def2 = mexerNoTimeDeferred.value
            if (def2 != null) {
                def2.await()
                mexerNoTimeDeferred.value = null
                // Re-simula o restante do 2o tempo com o novo lineup
                if (onObterEventosSegundoTempo != null) {
                    val subsInfo2 = substituicoes.map { InfoSubstituicao(it.sai.jogador.id, it.entra.jogador.id, it.minuto, it.timeId) }
                    val rawT2novo = onObterEventosSegundoTempo.invoke(
                        titularesAtuais.toList(),
                        reservasDisponiveis.toList(),
                        subsInfo2,
                        formacaoAtual,
                        estiloAtual,
                        minuto + 1,
                        90 + acrescimo2Tempo
                    )
                    eventosT2 = rawT2novo.map { ev ->
                        val ehCasa = ev.timeId == resultado.timeCasaId
                        EventoExibicao(ev.minuto, ev.tipo, ev.descricao, ev.timeId,
                            if (ehCasa) nomeTimeCasa else nomeTimeFora, ev.jogadorId)
                    }
                    substituidosSaiIds = substituicoes.map { it.sai.jogador.id }.toSet()
                }
            }
        }

        // Fim de jogo
        faseAtual = "FIM DE JOGO"
        onFinalizarPartida?.invoke(golsCasaAtual, golsForaAtual)
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

    // Painel "Mexer no Time" — pausa voluntária durante a partida
    if (pausadoParaMexerNoTime && escalacaoJogador != null) {
        val nomeTimeJog = if (isTimeCasaOJogador) nomeTimeCasa else nomeTimeFora
        val timeJogadorId = if (isTimeCasaOJogador) resultado.timeCasaId else resultado.timeForaId
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
                    substituicoes.add(SubstituicaoIntervalo(minuto = minutoAtual, sai = sai, entra = entra, timeId = timeJogadorId))
                    titularesAtuais.remove(sai)
                    titularesAtuais.add(entra)
                    reservasDisponiveis.remove(entra)
                    reservasDisponiveis.add(sai)
                }
            },
            onContinuar = {
                // Injeta no feed apenas as substituições feitas nesta pausa
                val subsTotais = substituicoes.filter { !it.ehLesao }
                val novas = subsTotais.drop(subsInjetadasNoFeed)
                novas.forEach { sub ->
                    eventosExibidos.add(0, EventoExibicao(sub.minuto, TipoEvento.SUBSTITUICAO_SAI, "↓ ${sub.sai.jogador.nomeAbreviado}", timeJogadorId, nomeTimeJog))
                    eventosExibidos.add(0, EventoExibicao(sub.minuto, TipoEvento.SUBSTITUICAO_ENTRA, "↑ ${sub.entra.jogador.nomeAbreviado}", timeJogadorId, nomeTimeJog))
                }
                subsInjetadasNoFeed = subsTotais.size
                pausadoParaMexerNoTime = false
                mexerNoTimeDeferred.value?.complete(Unit)
            },
            onTrocarPosicoes = { a, b ->
                val idxA = titularesAtuais.indexOfFirst { it.jogador.id == a.jogador.id }
                val idxB = titularesAtuais.indexOfFirst { it.jogador.id == b.jogador.id }
                if (idxA >= 0 && idxB >= 0) {
                    val posA = titularesAtuais[idxA].posicaoUsada
                    val posB = titularesAtuais[idxB].posicaoUsada
                    titularesAtuais[idxA] = titularesAtuais[idxA].copy(posicaoUsada = posB)
                    titularesAtuais[idxB] = titularesAtuais[idxB].copy(posicaoUsada = posA)
                }
            },
            rotuloBotao = "Continuar Partida",
            tituloHeader = "MEXER NO TIME",
            subtituloHeader = "Partida pausada — jogo continua do minuto ${minutoAtual}'"
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
                    substituicoes.add(SubstituicaoIntervalo(sai = sai, entra = entra, timeId = jogadorTimeId))
                    titularesAtuais.remove(sai)
                    titularesAtuais.add(entra)
                    reservasDisponiveis.remove(entra)
                    reservasDisponiveis.add(sai)
                }
            },
            onContinuar = { intervaloDeferred.complete(Unit) },
            onTrocarPosicoes = { a, b ->
                val idxA = titularesAtuais.indexOfFirst { it.jogador.id == a.jogador.id }
                val idxB = titularesAtuais.indexOfFirst { it.jogador.id == b.jogador.id }
                if (idxA >= 0 && idxB >= 0) {
                    val posA = titularesAtuais[idxA].posicaoUsada
                    val posB = titularesAtuais[idxB].posicaoUsada
                    titularesAtuais[idxA] = titularesAtuais[idxA].copy(posicaoUsada = posB)
                    titularesAtuais[idxB] = titularesAtuais[idxB].copy(posicaoUsada = posA)
                }
            }
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
                containerColor = MaterialTheme.colorScheme.surface
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
                    color = MaterialTheme.colorScheme.primary
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
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                        Text(
                            text = "Casa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                fontSize = scoreFontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "x",
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AnimatedContent(
                            targetState = golsForaAtual,
                            transitionSpec = { slideInVertically { -it } togetherWith slideOutVertically { it } },
                            label = "gols_fora"
                        ) { gols ->
                            Text(
                                text = gols.toString(),
                                fontSize = scoreFontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                        Text(
                            text = "Visitante",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("45'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("90'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // â”€â”€ Feed de eventos â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // ── Botão Mexer no Time ─────────────────────────────────────
        if (escalacaoJogador != null && !simulacaoEncerrada && !pausadoNoIntervalo
            && faseAtual in listOf("1º TEMPO", "2º TEMPO")
        ) {
            OutlinedButton(
                onClick = {
                    if (mexerNoTimeDeferred.value == null) {
                        val d = CompletableDeferred<Unit>()
                        mexerNoTimeDeferred.value = d
                        pausadoParaMexerNoTime = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("Mexer no Time")
            }
        }

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
            if (resultado.precisaPenaltis && penaltisInterativoConcluido) {
                // Disputa interativa já concluída — notas + botão de retorno
                NotasDaPartidaCard(
                    resultado          = resultado,
                    escalacaoJogador   = escalacaoJogador,
                    isTimeCasaOJogador = isTimeCasaOJogador
                )
                Button(
                    onClick = onSimulacaoFinalizada,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("Voltar ao painel") }
            } else if (resultado.precisaPenaltis && penaltisResultado == null) {
                val dadosAdv = dadosPenaltisAdversario
                if (dadosAdv != null) {
                    val gk = titularesAtuais.firstOrNull {
                        it.posicaoUsada.setor == br.com.managerfoot.data.database.entities.Setor.GOLEIRO
                    }
                    PenaltiInterativoPainel(
                        cobradorElegiveis  = titularesAtuais.filter {
                            it.posicaoUsada.setor != br.com.managerfoot.data.database.entities.Setor.GOLEIRO
                        }.toList(),
                        gkJogadorDefesa    = gk?.jogador?.defesa ?: 70,
                        dadosAdversario    = dadosAdv,
                        isTimeCasaOJogador = isTimeCasaOJogador,
                        timeCasaId         = resultado.timeCasaId,
                        timeForaId         = resultado.timeForaId,
                        nomeTimeJogador    = if (isTimeCasaOJogador) nomeTimeCasa else nomeTimeFora,
                        nomeAdversario     = if (isTimeCasaOJogador) nomeTimeFora else nomeTimeCasa,
                        agregadoJogador    = if (isTimeCasaOJogador) resultado.golsAgregadoCasa else resultado.golsAgregadoFora,
                        agregadoAdversario = if (isTimeCasaOJogador) resultado.golsAgregadoFora else resultado.golsAgregadoCasa,
                        onConcluir         = { res -> onPenaltisConfirmados?.invoke(res) }
                    )
                } else {
                    // Dados ainda carregando
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (resultado.precisaPenaltis && penaltisResultado != null) {
                PenaltiResultadoPainel(
                    penaltis      = penaltisResultado,
                    nomeTimeCasa  = nomeTimeCasa,
                    nomeTimeFora  = nomeTimeFora,
                    timeJogadorId = if (isTimeCasaOJogador) resultado.timeCasaId else resultado.timeForaId,
                    onConcluir    = onSimulacaoFinalizada
                )
            } else {
                NotasDaPartidaCard(
                    resultado          = resultado,
                    escalacaoJogador   = escalacaoJogador,
                    isTimeCasaOJogador = isTimeCasaOJogador
                )
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


// 
//  Notas da Partida  card exibido ao encerrar a simulação
// 
@Composable
private fun NotasDaPartidaCard(
    resultado: ResultadoPartida,
    escalacaoJogador: Escalacao?,
    isTimeCasaOJogador: Boolean
) {
    if (escalacaoJogador == null || resultado.notasJogadores.isEmpty()) return

    val jogadoresDoTime = (escalacaoJogador.titulares + escalacaoJogador.reservas)
        .associate { it.jogador.id to it.jogador }

    val notasDoTime = resultado.notasJogadores
        .filter { (id, _) -> id in jogadoresDoTime }
        .entries.sortedByDescending { it.value }

    if (notasDoTime.isEmpty()) return

    val mediaTime = notasDoTime.map { it.value }.average().toFloat()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Notas da Partida",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Média: ${"%.1f".format(mediaTime)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = notaColor(mediaTime)
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            notasDoTime.forEach { (jogadorId, nota) ->
                val j = jogadoresDoTime[jogadorId] ?: return@forEach
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            j.posicao.abreviacao,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(
                            j.nomeAbreviado,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "%.1f".format(nota),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = notaColor(nota)
                    )
                }
            }
        }
    }
}

private fun notaColor(nota: Float): Color = when {
    nota >= 8.0f -> Color(0xFF4CAF50)
    nota >= 6.5f -> Color(0xFF2196F3)
    nota >= 5.0f -> Color(0xFFFFC107)
    else         -> Color(0xFFF44336)
}
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  Painel do intervalo (tÃ¡tica + substituiÃ§Ãµes)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// ─────────────────────────────────────────────────────────────
//  PenaltiInterativoPainel  ─  disputa pênalti a pênalti
// ─────────────────────────────────────────────────────────────

private enum class FasePenalti { SELECAO, RESULTADO, FINALIZADO }

@Composable
private fun PenaltiInterativoPainel(
    cobradorElegiveis: List<JogadorNaEscalacao>,
    gkJogadorDefesa: Int,
    dadosAdversario: DadosPenaltiAdversario,
    isTimeCasaOJogador: Boolean,
    timeCasaId: Int,
    timeForaId: Int,
    nomeTimeJogador: String,
    nomeAdversario: String,
    agregadoJogador: Int,
    agregadoAdversario: Int,
    onConcluir: (ResultadoPenaltis) -> Unit
) {
    var fase by remember { mutableStateOf(FasePenalti.SELECAO) }
    var golsJogador    by remember { mutableIntStateOf(0) }
    var golsAdversario by remember { mutableIntStateOf(0) }
    val cobrancasJogador    = remember { mutableStateListOf<EventoPenalti>() }
    val cobrancasAdversario = remember { mutableStateListOf<EventoPenalti>() }
    var ultimoJogador    by remember { mutableStateOf<EventoPenalti?>(null) }
    var ultimoAdversario by remember { mutableStateOf<EventoPenalti?>(null) }
    var jaCobraramIds    by remember { mutableStateOf(emptySet<Int>()) }
    var idxAdversario    by remember { mutableIntStateOf(0) }

    val allCobradorIds = remember(cobradorElegiveis) { cobradorElegiveis.map { it.jogador.id }.toSet() }

    // Jogadores elegíveis neste ciclo
    val eligiveis = if (jaCobraramIds.containsAll(allCobradorIds)) {
        cobradorElegiveis   // ciclo completo: mostra todos para o próximo ciclo
    } else {
        cobradorElegiveis.filter { it.jogador.id !in jaCobraramIds }
    }

    fun probConversao(fin: Int, gkDef: Int) =
        (0.55 + (fin / 99.0) * 0.40 - (gkDef / 99.0) * 0.15).coerceIn(0.40, 0.95)

    fun verificarFim(): Boolean {
        val n = cobrancasJogador.size
        if (n == 0) return false
        if (n == 5 && golsJogador != golsAdversario) return true
        if (n > 5 && ultimoJogador!!.convertido != ultimoAdversario!!.convertido) return true
        return false
    }

    fun construirResultado(): ResultadoPenaltis {
        val golsCasa = if (isTimeCasaOJogador) golsJogador else golsAdversario
        val golsFora = if (isTimeCasaOJogador) golsAdversario else golsJogador
        val venc = if (golsJogador > golsAdversario) {
            if (isTimeCasaOJogador) timeCasaId else timeForaId
        } else {
            if (isTimeCasaOJogador) timeForaId else timeCasaId
        }
        val (ccasa, cfora) = if (isTimeCasaOJogador)
            cobrancasJogador.toList() to cobrancasAdversario.toList()
        else
            cobrancasAdversario.toList() to cobrancasJogador.toList()
        return ResultadoPenaltis(
            cobrancasCasa = ccasa, cobrancasFora = cfora,
            golsCasa = golsCasa, golsFora = golsFora,
            vencedorId = venc, timeCasaId = timeCasaId, timeForaId = timeForaId
        )
    }

    fun onJogadorSelecionado(jne: JogadorNaEscalacao) {
        val cicloCompleto = jaCobraramIds.containsAll(allCobradorIds)
        jaCobraramIds = if (cicloCompleto) setOf(jne.jogador.id) else jaCobraramIds + jne.jogador.id

        val convJ = Random.nextDouble() < probConversao(jne.jogador.finalizacao, dadosAdversario.gkDefesa)
        val evJ = EventoPenalti(jne.jogador.id, jne.jogador.nomeAbreviado, convJ)
        if (convJ) golsJogador++
        cobrancasJogador.add(evJ)
        ultimoJogador = evJ

        val realIdx = idxAdversario % dadosAdversario.cobradores.size
        val (advId, advNome) = dadosAdversario.cobradores[realIdx]
        val advFin = dadosAdversario.finalizacoes[realIdx]
        idxAdversario++
        val convA = Random.nextDouble() < probConversao(advFin, gkJogadorDefesa)
        val evA = EventoPenalti(advId, advNome, convA)
        if (convA) golsAdversario++
        cobrancasAdversario.add(evA)
        ultimoAdversario = evA

        fase = if (verificarFim()) FasePenalti.FINALIZADO else FasePenalti.RESULTADO
    }

    val numRodadas = cobrancasJogador.size
    val isSuddenDeath = numRodadas >= 5

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
                    if (isSuddenDeath) "MORTE SÚBITA" else "DISPUTA DE PÊNALTIS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$nomeTimeJogador $agregadoJogador × $agregadoAdversario $nomeAdversario (agg.)",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        nomeTimeJogador,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "$golsJogador – $golsAdversario",
                        fontSize = 36.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        nomeAdversario,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isSuddenDeath) "Cobrança ${numRodadas + 1} (morte súbita)"
                    else "Cobrança ${numRodadas + 1} de 5",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        when (fase) {
            FasePenalti.SELECAO -> {
                Text(
                    "Escolha o próximo batedor:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(eligiveis) { jne ->
                        ListItem(
                            headlineContent = { Text(jne.jogador.nome) },
                            supportingContent = { Text("${jne.posicaoUsada.abreviacao} · Fin: ${jne.jogador.finalizacao}") },
                            trailingContent = {
                                Button(
                                    onClick = { onJogadorSelecionado(jne) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) { Text("Cobrar") }
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }

            FasePenalti.RESULTADO -> {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ultimoJogador?.let { ev ->
                        PenaltiCobrancaCard(nomeTime = nomeTimeJogador, evento = ev)
                    }
                    ultimoAdversario?.let { ev ->
                        PenaltiCobrancaCard(nomeTime = nomeAdversario, evento = ev)
                    }
                }
                Button(
                    onClick = { fase = FasePenalti.SELECAO },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("Próxima cobrança") }
            }

            FasePenalti.FINALIZADO -> {
                val timVenc = if (golsJogador > golsAdversario) nomeTimeJogador else nomeAdversario
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ultimoJogador?.let { ev ->
                        PenaltiCobrancaCard(nomeTime = nomeTimeJogador, evento = ev)
                    }
                    ultimoAdversario?.let { ev ->
                        PenaltiCobrancaCard(nomeTime = nomeAdversario, evento = ev)
                    }
                    Spacer(Modifier.height(8.dp))
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "🏆 $timVenc avança!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (timVenc == nomeTimeJogador) Color(0xFF2E7D32)
                                        else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "$golsJogador – $golsAdversario nos pênaltis",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Button(
                    onClick = { onConcluir(construirResultado()) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("Concluir") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  PenaltiCobrancaCard  ─  exibe resultado de uma cobrança
// ─────────────────────────────────────────────────────────────
@Composable
private fun PenaltiCobrancaCard(nomeTime: String, evento: EventoPenalti) {
    val cor = if (evento.convertido) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cor.copy(alpha = 0.12f))
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (evento.convertido) "⚽" else "❌", fontSize = 28.sp)
            Column(Modifier.weight(1f)) {
                Text(evento.nomeAbrev, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(nomeTime, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (evento.convertido) "Gol!" else "Defendido",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = cor
            )
        }
    }
}

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
    val penaltiScoreFontSize = if (LocalConfiguration.current.screenWidthDp < 400) 32.sp else 40.sp
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
                            g.toString(), fontSize = penaltiScoreFontSize, fontWeight = FontWeight.Bold,
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
                            g.toString(), fontSize = penaltiScoreFontSize, fontWeight = FontWeight.Bold,
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
    onContinuar: () -> Unit,
    onTrocarPosicoes: (a: JogadorNaEscalacao, b: JogadorNaEscalacao) -> Unit = { _, _ -> },
    rotuloBotao: String = "Continuar para o 2º Tempo",
    tituloHeader: String = "INTERVALO",
    subtituloHeader: String = "Faça sua intervenção antes do 2º tempo"
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
                Text(tituloHeader, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    subtituloHeader,
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
            Tab(selected = abaAtiva == 2, onClick = { abaAtiva = 2 }, text = { Text("Posições") })
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
                    titulares = titulares,
                    onFormacaoChange = onFormacaoChange,
                    onEstiloChange = onEstiloChange
                )
                2 -> TrocarPosicoesTab(
                    titulares = titulares,
                    onTrocarPosicoes = onTrocarPosicoes
                )
            }
        }

        Button(
            onClick = onContinuar,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(rotuloBotao)
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

@Composable
private fun TrocarPosicoesTab(
    titulares: List<JogadorNaEscalacao>,
    onTrocarPosicoes: (a: JogadorNaEscalacao, b: JogadorNaEscalacao) -> Unit
) {
    var selecionado by remember { mutableStateOf<JogadorNaEscalacao?>(null) }
    if (selecionado == null) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Selecione o jogador para mover de posição:",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
            LazyColumn {
                items(titulares) { jne ->
                    ListItem(
                        headlineContent = { Text(jne.jogador.nome) },
                        supportingContent = { Text("${jne.posicaoUsada.abreviacao} · Força ${jne.jogador.forca}") },
                        trailingContent = {
                            OutlinedButton(
                                onClick = { selecionado = jne },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Selecionar", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Trocar posição de:", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${selecionado!!.jogador.nome} (${selecionado!!.posicaoUsada.abreviacao})",
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = { selecionado = null }) { Text("Cancelar") }
            }
            Text(
                "Selecione quem vai receber a posição:",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
            LazyColumn {
                items(titulares.filter { it.jogador.id != selecionado!!.jogador.id }) { jne ->
                    ListItem(
                        headlineContent = { Text(jne.jogador.nome) },
                        supportingContent = {
                            Text("${selecionado!!.posicaoUsada.abreviacao} ↔ ${jne.posicaoUsada.abreviacao}")
                        },
                        trailingContent = {
                            Button(
                                onClick = {
                                    onTrocarPosicoes(selecionado!!, jne)
                                    selecionado = null
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Trocar", style = MaterialTheme.typography.labelSmall)
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
    titulares: List<JogadorNaEscalacao>,
    onFormacaoChange: (String) -> Unit,
    onEstiloChange: (EstiloJogo) -> Unit
) {
    val formacoes = listOf(
        "4-4-2", "4-5-1", "4-3-3",
        "4-3-2-1", "5-4-1", "4-1-2-1-2",
        "3-5-2", "5-3-2", "4-2-3-1",
        "3-2-4-1", "2-3-5", "2-3-2-3", "4-2-4",
        "3-4-3", "4-2-2-2"
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Formação ──────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Formação tática", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Escolha o esquema tático para o 2º tempo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formacoes.forEach { f ->
                    FilterChip(selected = f == formacaoAtual, onClick = { onFormacaoChange(f) }, label = { Text(f) })
                }
            }
        }

        // ── Gramado tático ────────────────────────────────────────
        if (titulares.isNotEmpty()) {
            GramadoTatico(
                titulares = titulares,
                formacao  = formacaoAtual,
                modifier  = Modifier.padding(vertical = 4.dp)
            )
        }

        HorizontalDivider()

        // ── Estilo de jogo ────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estilo de jogo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Define a postura tática da equipe no 2º tempo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EstiloJogo.entries.forEach { estilo ->
                    val label = when (estilo) {
                        EstiloJogo.OFENSIVO      -> "Ofensivo"
                        EstiloJogo.EQUILIBRADO   -> "Equilibrado"
                        EstiloJogo.DEFENSIVO     -> "Defensivo"
                        EstiloJogo.CONTRA_ATAQUE -> "Contra-ataque"
                    }
                    FilterChip(selected = estilo == estiloAtual, onClick = { onEstiloChange(estilo) }, label = { Text(label) })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
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
        TipoEvento.ASSISTENCIA ->
            "👟" to Color(0xFF0D47A1).copy(alpha = 0.12f)
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
