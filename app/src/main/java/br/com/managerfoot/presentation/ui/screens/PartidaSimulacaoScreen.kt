package br.com.managerfoot.presentation.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.managerfoot.data.database.entities.EstiloJogo
import br.com.managerfoot.data.database.entities.Setor
import br.com.managerfoot.data.database.entities.TipoEvento
import br.com.managerfoot.domain.engine.CalculadoraForca
import br.com.managerfoot.domain.model.Escalacao
import br.com.managerfoot.domain.model.EventoSimulado
import br.com.managerfoot.domain.model.JogadorNaEscalacao
import br.com.managerfoot.domain.model.InfoSubstituicao
import br.com.managerfoot.domain.model.ResultadoPartida
import br.com.managerfoot.domain.model.ResultadoPenaltis
import br.com.managerfoot.domain.model.EstatisticasTime
import br.com.managerfoot.presentation.ui.components.PosicaoBadge
import br.com.managerfoot.presentation.ui.components.SectionTitle
import br.com.managerfoot.presentation.ui.components.TeamBadge
import br.com.managerfoot.presentation.ui.components.corSetor
import br.com.managerfoot.presentation.ui.theme.Radius
import br.com.managerfoot.presentation.ui.theme.Spacing
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalConfiguration
import br.com.managerfoot.domain.model.DadosPenaltiAdversario
import br.com.managerfoot.domain.model.EventoPenalti
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────
//  Dados de um evento enriquecido para exibição
// ─────────────────────────────────────────────
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

// ─────────────────────────────────────────────
//  PartidaSimulacaoScreen
// ─────────────────────────────────────────────
@Composable
fun PartidaSimulacaoScreen(
    resultado: ResultadoPartida,
    nomeTimeCasa: String,
    nomeTimeFora: String,
    escudoTimeCasa: String = "",
    escudoTimeFora: String = "",
    nomeEstadio: String = "",
    escalacaoJogador: Escalacao? = null,
    escalacaoAdversario: Escalacao? = null,
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

    // ID e estado dinâmico do time adversário (titulares atualizados conforme subs da IA)
    val adversarioTimeId = if (isTimeCasaOJogador) resultado.timeForaId else resultado.timeCasaId
    val titularesAdversarioAtuais = remember(resultado.partidaId) { mutableStateListOf<JogadorNaEscalacao>() }
    val reservasAdversarioAtuais  = remember(resultado.partidaId) { mutableStateListOf<JogadorNaEscalacao>() }

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
                pularExibicao = ev.tipo == TipoEvento.DEFESA_GOLEIRO
            )
        }
    }

    // Acréscimos gerados aleatoriamente — vinculados ao ID da partida para consistência
    val acrescimo1Tempo = remember(resultado.partidaId) { (1..5).random() }
    val acrescimo2Tempo = remember(resultado.partidaId) { (2..7).random() }

    // Inicializa titulares/reservas do painel a partir da escalação passada
    LaunchedEffect(escalacaoJogador) {
        if (escalacaoJogador != null) {
            titularesAtuais.clear()
            titularesAtuais.addAll(escalacaoJogador.titulares)
            reservasDisponiveis.clear()
            reservasDisponiveis.addAll(escalacaoJogador.reservas)
        }
    }

    // Inicializa titulares/reservas do adversário (para exibição de força em tempo real)
    LaunchedEffect(escalacaoAdversario) {
        if (escalacaoAdversario != null) {
            titularesAdversarioAtuais.clear()
            titularesAdversarioAtuais.addAll(escalacaoAdversario.titulares)
            reservasAdversarioAtuais.clear()
            reservasAdversarioAtuais.addAll(escalacaoAdversario.reservas)
        }
    }

    // Simulação do relógio e eventos — reinicia se a partida mudar (chave = partidaId)
    LaunchedEffect(resultado.partidaId) {
        // Pré-jogo
        faseAtual = "PRÉ-JOGO"
        delay(1500)

        // Primeiro tempo (0-45 + acréscimo)
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
                // Rastreia substituições da IA adversária para recalcular força exibida
                if (ev.tipo == TipoEvento.SUBSTITUICAO_SAI && ev.timeId == adversarioTimeId) {
                    titularesAdversarioAtuais.removeIf { it.jogador.id == ev.jogadorId }
                }
                if (ev.tipo == TipoEvento.SUBSTITUICAO_ENTRA && ev.timeId == adversarioTimeId) {
                    val entrando = reservasAdversarioAtuais.find { it.jogador.id == ev.jogadorId }
                    if (entrando != null) {
                        titularesAdversarioAtuais.add(entrando)
                        reservasAdversarioAtuais.remove(entrando)
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

        // Intervalo — exibe separador e pausa para intervenção do jogador
        faseAtual = "INTERVALO"
        eventosExibidos.add(
            0, EventoExibicao(45, TipoEvento.GOL, "--- Intervalo ---", -1, "")
        )

        // Só mostra o painel se o jogador tem dados de escalação
        if (escalacaoJogador != null) {
            pausadoNoIntervalo = true
            intervaloDeferred.await()
            pausadoNoIntervalo = false

            // Injeta eventos de substituição no feed
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
                // Rastreia substituições da IA adversária para recalcular força exibida
                if (ev.tipo == TipoEvento.SUBSTITUICAO_SAI && ev.timeId == adversarioTimeId) {
                    titularesAdversarioAtuais.removeIf { it.jogador.id == ev.jogadorId }
                }
                if (ev.tipo == TipoEvento.SUBSTITUICAO_ENTRA && ev.timeId == adversarioTimeId) {
                    val entrando = reservasAdversarioAtuais.find { it.jogador.id == ev.jogadorId }
                    if (entrando != null) {
                        titularesAdversarioAtuais.add(entrando)
                        reservasAdversarioAtuais.remove(entrando)
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

    // Rola o feed para exibir estatísticas e botão ao encerrar a partida
    LaunchedEffect(simulacaoEncerrada, penaltisInterativoConcluido) {
        if (simulacaoEncerrada && (!resultado.precisaPenaltis || penaltisInterativoConcluido)) {
            delay(400) // aguarda recomposição adicionando os novos itens
            listState.animateScrollToItem(eventosExibidos.size + 2)
        }
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
                    // sai NÃO é devolvido às reservas: jogador substituído não pode voltar
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
            subtituloHeader = "Partida pausada — jogo continua do minuto ${minutoAtual}'",
            estatisticasCasa = resultado.estatisticasCasa,
            estatisticasFora = resultado.estatisticasFora,
            nomeTimeCasaStats = nomeTimeCasa,
            nomeTimeForaStats = nomeTimeFora,
            escalacaoAdversario = escalacaoAdversario,
            titularesAdversarioAtuais = titularesAdversarioAtuais.toList()
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
                    // sai NÃO é devolvido às reservas: jogador substituído não pode voltar
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
            },
            estatisticasCasa = resultado.estatisticasCasa,
            estatisticasFora = resultado.estatisticasFora,
            nomeTimeCasaStats = nomeTimeCasa,
            nomeTimeForaStats = nomeTimeFora,
            escalacaoAdversario = escalacaoAdversario,
            titularesAdversarioAtuais = titularesAdversarioAtuais.toList()
        )
        return
    }

    // Painéis de pênaltis — exibidos em tela inteira antes do Column principal
    if (simulacaoEncerrada && resultado.precisaPenaltis && !penaltisInterativoConcluido) {
        if (penaltisResultado != null) {
            PenaltiResultadoPainel(
                penaltis      = penaltisResultado,
                nomeTimeCasa  = nomeTimeCasa,
                nomeTimeFora  = nomeTimeFora,
                timeJogadorId = if (isTimeCasaOJogador) resultado.timeCasaId else resultado.timeForaId,
                onConcluir    = onSimulacaoFinalizada
            )
            return
        }
        val dadosAdv = dadosPenaltisAdversario
        if (dadosAdv != null) {
            val gk = titularesAtuais.firstOrNull { it.posicaoUsada.setor == Setor.GOLEIRO }
            PenaltiInterativoPainel(
                cobradorElegiveis  = titularesAtuais.filter { it.posicaoUsada.setor != Setor.GOLEIRO }.toList(),
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
            return
        }
        // dadosPenaltisAdversario ainda null: exibe spinner no Column principal abaixo
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Placar (scoreboard polido) ──────────────────────────
        MatchScoreboard(
            nomeTimeCasa       = nomeTimeCasa,
            nomeTimeFora       = nomeTimeFora,
            escudoTimeCasa     = escudoTimeCasa,
            escudoTimeFora     = escudoTimeFora,
            faseAtual          = faseAtual,
            minutoAtual        = minutoAtual,
            golsCasaAtual      = golsCasaAtual,
            golsForaAtual      = golsForaAtual,
            scoreFontSize      = scoreFontSize,
            progresso          = (minutoAtual / (90f + acrescimo2Tempo)).coerceIn(0f, 1f),
            eventosTimeline    = eventosExibidos.toList(),
            timeCasaId         = resultado.timeCasaId,
            simulacaoEncerrada = simulacaoEncerrada,
            torcedores         = resultado.torcedores,
            nomeEstadio        = nomeEstadio,
            modifier           = Modifier.padding(Spacing.lg)
        )

        // ── Feed de eventos ──────────────────────────────────────
        // ── Botão Mexer no Time ─────────────────────────────────
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
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                shape = RoundedCornerShape(Radius.md)
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text("Mexer no Time")
            }
        }

        SectionTitle(titulo = "Eventos da partida")

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
            // Resultado final: tabs Estatísticas / Notas + botão de retorno
            if (simulacaoEncerrada && (!resultado.precisaPenaltis || penaltisInterativoConcluido)) {
                item {
                    ResultadoFinalCard(
                        estatisticasCasa   = resultado.estatisticasCasa,
                        estatisticasFora   = resultado.estatisticasFora,
                        nomeTimeCasa       = nomeTimeCasa,
                        nomeTimeFora       = nomeTimeFora,
                        resultado          = resultado,
                        escalacaoJogador   = escalacaoJogador,
                        escalacaoAdversario = escalacaoAdversario,
                        isTimeCasaOJogador = isTimeCasaOJogador
                    )
                }
                item {
                    Button(
                        onClick = onSimulacaoFinalizada,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        shape = RoundedCornerShape(Radius.md),
                        contentPadding = PaddingValues(vertical = Spacing.md)
                    ) {
                        Text("Voltar ao painel", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(Spacing.sm))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ── Botão de encerrar ────────────────────────────────────
        // Spinner enquanto dadosPenaltisAdversario ainda não chegou
        if (simulacaoEncerrada && resultado.precisaPenaltis && !penaltisInterativoConcluido && penaltisResultado == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Estatísticas — tab usada no IntervaloPainel e card ao final da partida
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EstatisticasTab(
    estatisticasCasa: EstatisticasTime,
    estatisticasFora: EstatisticasTime,
    nomeTimeCasa: String,
    nomeTimeFora: String
) {
    val corCasa = MaterialTheme.colorScheme.primary
    val corFora = MaterialTheme.colorScheme.error
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                nomeTimeCasa,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = corCasa,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "VS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.sm)
            )
            Text(
                nomeTimeFora,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = corFora,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        EstatisticaRow("Finalizações",       estatisticasCasa.chutes,          estatisticasFora.chutes)
        EstatisticaRow("No alvo",            estatisticasCasa.chutesNoGol,     estatisticasFora.chutesNoGol)
        EstatisticaRow("Defesas do goleiro", estatisticasCasa.defesasGoleiro,  estatisticasFora.defesasGoleiro)
        EstatisticaRow("Posse de bola",      estatisticasCasa.posse,           estatisticasFora.posse, sufixo = "%")
        EstatisticaRow("Passes errados",     estatisticasCasa.passesErrados,   estatisticasFora.passesErrados, invertido = true)
        EstatisticaRow("Faltas",             estatisticasCasa.faltas,          estatisticasFora.faltas,        invertido = true)
        EstatisticaRow("Cartões amarelos",   estatisticasCasa.cartaoAmarelo,   estatisticasFora.cartaoAmarelo, invertido = true)
        EstatisticaRow("Cartões vermelhos",  estatisticasCasa.cartaoVermelho,  estatisticasFora.cartaoVermelho, invertido = true)
    }
}

@Composable
private fun EstatisticaRow(
    label: String,
    valorCasa: Int,
    valorFora: Int,
    sufixo: String = "",
    invertido: Boolean = false
) {
    val corCasa = MaterialTheme.colorScheme.primary
    val corFora = MaterialTheme.colorScheme.error
    val total = valorCasa + valorFora
    val fracCasa = if (total > 0) (valorCasa.toFloat() / total).coerceIn(0.02f, 0.98f) else 0.5f
    val fracFora = 1f - fracCasa

    val casaMelhor = if (invertido) valorCasa < valorFora else valorCasa > valorFora
    val foraMelhor = if (invertido) valorFora < valorCasa else valorFora > valorCasa
    val empate = valorCasa == valorFora && total > 0

    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$valorCasa$sufixo",
                modifier = Modifier.width(48.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (casaMelhor) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    empate    -> MaterialTheme.colorScheme.onSurface
                    casaMelhor -> corCasa
                    else      -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$valorFora$sufixo",
                modifier = Modifier.width(48.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (foraMelhor) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    empate    -> MaterialTheme.colorScheme.onSurface
                    foraMelhor -> corFora
                    else      -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.End
            )
        }
        Spacer(Modifier.height(Spacing.xxs))
        Row(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .weight(fracCasa)
                    .fillMaxHeight()
                    .background(corCasa.copy(alpha = if (casaMelhor) 1f else 0.55f))
            )
            Box(
                Modifier
                    .weight(fracFora)
                    .fillMaxHeight()
                    .background(corFora.copy(alpha = if (foraMelhor) 1f else 0.55f))
            )
        }
    }
}

@Composable
private fun EstatisticasPartidaCard(
    estatisticasCasa: EstatisticasTime,
    estatisticasFora: EstatisticasTime,
    nomeTimeCasa: String,
    nomeTimeFora: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Estatísticas da Partida",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            EstatisticasTab(
                estatisticasCasa = estatisticasCasa,
                estatisticasFora = estatisticasFora,
                nomeTimeCasa = nomeTimeCasa,
                nomeTimeFora = nomeTimeFora
            )
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

    val substitutosQueEntraram = resultado.eventos
        .filter { it.tipo == TipoEvento.SUBSTITUICAO_ENTRA }
        .map { it.jogadorId }
        .toSet()
        .intersect(jogadoresDoTime.keys)

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
                val ehSub = jogadorId in substitutosQueEntraram
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
                        if (ehSub) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "↑ Sub",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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

// ─────────────────────────────────────────────────────────────────────
//  Card de resultado final — tabs Estatísticas / Notas
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun ResultadoFinalCard(
    estatisticasCasa: EstatisticasTime,
    estatisticasFora: EstatisticasTime,
    nomeTimeCasa: String,
    nomeTimeFora: String,
    resultado: ResultadoPartida,
    escalacaoJogador: Escalacao?,
    escalacaoAdversario: Escalacao? = null,
    isTimeCasaOJogador: Boolean
) {
    var abaAtiva by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        ManOfTheMatchCard(
            resultado = resultado,
            escalacaoJogador = escalacaoJogador,
            isTimeCasaOJogador = isTimeCasaOJogador
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.lg),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column {
                TabRow(
                    selectedTabIndex = abaAtiva,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = abaAtiva == 0,
                        onClick  = { abaAtiva = 0 },
                        text     = {
                            Text(
                                "ESTATÍSTICAS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        }
                    )
                    Tab(
                        selected = abaAtiva == 1,
                        onClick  = { abaAtiva = 1 },
                        text     = {
                            Text(
                                "NOTAS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        }
                    )
                }
                when (abaAtiva) {
                    0 -> EstatisticasTab(
                        estatisticasCasa = estatisticasCasa,
                        estatisticasFora = estatisticasFora,
                        nomeTimeCasa     = nomeTimeCasa,
                        nomeTimeFora     = nomeTimeFora
                    )
                    1 -> NotasConteudo(
                        resultado           = resultado,
                        escalacaoJogador    = escalacaoJogador,
                        escalacaoAdversario = escalacaoAdversario,
                        isTimeCasaOJogador  = isTimeCasaOJogador,
                        nomeTimeFora        = if (isTimeCasaOJogador) nomeTimeFora else nomeTimeCasa
                    )
                }
            }
        }
    }
}

@Composable
private fun NotasConteudo(
    resultado: ResultadoPartida,
    escalacaoJogador: Escalacao?,
    escalacaoAdversario: Escalacao? = null,
    isTimeCasaOJogador: Boolean,
    nomeTimeFora: String = ""
) {
    if (resultado.notasJogadores.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Notas não disponíveis nesta partida",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val timeJogadorId = if (isTimeCasaOJogador) resultado.timeCasaId else resultado.timeForaId
    val timeAdversarioId = if (isTimeCasaOJogador) resultado.timeForaId else resultado.timeCasaId

    // Se houver informações do adversário, mostra sub-abas
    if (escalacaoAdversario != null) {
        var subAbaAtiva by remember { mutableIntStateOf(0) }
        Column {
            TabRow(selectedTabIndex = subAbaAtiva) {
                Tab(selected = subAbaAtiva == 0, onClick = { subAbaAtiva = 0 }, text = { Text("Meu Time") })
                Tab(selected = subAbaAtiva == 1, onClick = { subAbaAtiva = 1 }, text = { Text("Adversário") })
            }
            when (subAbaAtiva) {
                0 -> NotasTimeConteudo(
                    resultado = resultado,
                    timeId = timeJogadorId,
                    jogadoresInfoMap = ((escalacaoJogador?.titulares ?: emptyList()) +
                            (escalacaoJogador?.reservas ?: emptyList()))
                        .associate { it.jogador.id to it.jogador }
                )
                1 -> NotasTimeConteudo(
                    resultado = resultado,
                    timeId = timeAdversarioId,
                    jogadoresInfoMap = (escalacaoAdversario.titulares + escalacaoAdversario.reservas)
                        .associate { it.jogador.id to it.jogador },
                    corTitulo = MaterialTheme.colorScheme.error
                )
            }
        }
    } else {
        NotasTimeConteudo(
            resultado = resultado,
            timeId = timeJogadorId,
            jogadoresInfoMap = ((escalacaoJogador?.titulares ?: emptyList()) +
                    (escalacaoJogador?.reservas ?: emptyList()))
                .associate { it.jogador.id to it.jogador }
        )
    }
}

@Composable
private fun NotasTimeConteudo(
    resultado: ResultadoPartida,
    timeId: Int,
    jogadoresInfoMap: Map<Int, br.com.managerfoot.domain.model.Jogador>,
    corTitulo: Color = MaterialTheme.colorScheme.onSurface
) {
    val playerTeamIds = resultado.eventos
        .filter { (it.tipo == TipoEvento.PARTICIPOU || it.tipo == TipoEvento.SUBSTITUICAO_ENTRA)
                   && it.timeId == timeId }
        .map { it.jogadorId }
        .toSet()

    val notasDoTime = resultado.notasJogadores
        .filter { (id, _) -> id in playerTeamIds }
        .entries.sortedByDescending { it.value }

    if (notasDoTime.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Notas não disponíveis",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val mediaTime = notasDoTime.map { it.value }.average().toFloat()

    val substitutosQueEntraram = resultado.eventos
        .filter { it.tipo == TipoEvento.SUBSTITUICAO_ENTRA && it.timeId == timeId }
        .map { it.jogadorId }
        .toSet()

    Column(modifier = Modifier.padding(Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "NOTAS DA PARTIDA",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = corTitulo,
                letterSpacing = 1.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Média ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NotaPill(mediaTime)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(Spacing.xs))

        notasDoTime.forEachIndexed { idx, (jogadorId, nota) ->
            val j = jogadoresInfoMap[jogadorId]
            val ehSub = jogadorId in substitutosQueEntraram
            val ehTop3 = idx < 3
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (ehTop3) notaColor(nota).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${idx + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (ehTop3) notaColor(nota)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        j?.posicao?.abreviacao ?: "?",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        j?.nomeAbreviado ?: "#$jogadorId",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (ehTop3) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (ehSub) {
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "Substituto",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                NotaPill(nota)
            }
        }
    }
}

@Composable
private fun NotaPill(nota: Float) {
    val cor = notaColor(nota)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(cor.copy(alpha = 0.18f))
            .border(0.5.dp, cor.copy(alpha = 0.5f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
    ) {
        Text(
            text = "%.1f".format(nota),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = cor
        )
    }
}
// ─────────────────────────────────────────────
//  Painel do intervalo (tática + substituições)
// ─────────────────────────────────────────────
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

    val eligiveis = if (jaCobraramIds.containsAll(allCobradorIds)) {
        cobradorElegiveis
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
        // Header dramático com placar gigante e tracker
        PenaltiPlacardCard(
            nomeTimeCasa     = nomeTimeJogador,
            nomeTimeFora     = nomeAdversario,
            golsCasa         = golsJogador,
            golsFora         = golsAdversario,
            cobrancasCasa    = cobrancasJogador.toList(),
            cobrancasFora    = cobrancasAdversario.toList(),
            isSuddenDeath    = isSuddenDeath,
            tituloRodada     = if (isSuddenDeath) "MORTE SÚBITA · COBRANÇA ${numRodadas + 1}"
                               else "COBRANÇA ${numRodadas + 1} DE 5",
            agregadoCasa     = agregadoJogador,
            agregadoFora     = agregadoAdversario,
            destaqueVencedor = fase == FasePenalti.FINALIZADO,
            timeJogadorEhCasa = true
        )

        when (fase) {
            FasePenalti.SELECAO -> {
                Text(
                    "ESCOLHA O PRÓXIMO BATEDOR",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(eligiveis) { jne ->
                        JogadorLinhaIntervalo(
                            jne = jne,
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "FIN ${jne.jogador.finalizacao}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.width(Spacing.sm))
                                    Button(
                                        onClick = { onJogadorSelecionado(jne) },
                                        shape = RoundedCornerShape(Radius.sm),
                                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs)
                                    ) {
                                        Icon(
                                            Icons.Filled.SportsSoccer,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(Spacing.xs))
                                        Text("COBRAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    }
                }
            }

            FasePenalti.RESULTADO -> {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        "RESULTADO DA COBRANÇA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    ultimoJogador?.let { ev ->
                        PenaltiCobrancaCard(nomeTime = nomeTimeJogador, evento = ev)
                    }
                    ultimoAdversario?.let { ev ->
                        PenaltiCobrancaCard(nomeTime = nomeAdversario, evento = ev)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = { fase = FasePenalti.SELECAO },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        shape = RoundedCornerShape(Radius.md),
                        contentPadding = PaddingValues(vertical = Spacing.md)
                    ) {
                        Text("Próxima cobrança", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(Spacing.sm))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            FasePenalti.FINALIZADO -> {
                val timVenc = if (golsJogador > golsAdversario) nomeTimeJogador else nomeAdversario
                val jogadorVenceu = timVenc == nomeTimeJogador
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    ultimoJogador?.let { ev ->
                        PenaltiCobrancaCard(nomeTime = nomeTimeJogador, evento = ev)
                    }
                    ultimoAdversario?.let { ev ->
                        PenaltiCobrancaCard(nomeTime = nomeAdversario, evento = ev)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    PenaltiVencedorCard(
                        nomeVencedor = timVenc,
                        placar = "$golsJogador – $golsAdversario",
                        jogadorVenceu = jogadorVenceu
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = { onConcluir(construirResultado()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        shape = RoundedCornerShape(Radius.md),
                        contentPadding = PaddingValues(vertical = Spacing.md)
                    ) {
                        Text("Concluir", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(Spacing.sm))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  PenaltiCobrancaCard  ─  exibe resultado de uma cobrança
// ─────────────────────────────────────────────────────────────
@Composable
private fun PenaltiCobrancaCard(nomeTime: String, evento: EventoPenalti) {
    val cor = if (evento.convertido) Color(0xFF00E676) else MaterialTheme.colorScheme.error
    val labelTexto = if (evento.convertido) "CONVERTEU" else "PERDEU"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = cor.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, cor.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(cor))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cor.copy(alpha = 0.20f))
                        .border(1.dp, cor.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (evento.convertido) Icons.Filled.SportsSoccer else Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = cor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        evento.nomeAbrev,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        nomeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = labelTexto,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = cor,
                    letterSpacing = 1.sp
                )
            }
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
        // Header dramático
        PainelHeader(
            titulo = "Disputa de pênaltis",
            subtitulo = "$nomeTimeJogador $agregadoJogador × $agregadoAdversario $nomeAdversario · agregado empatado",
            accent = MaterialTheme.colorScheme.error,
            icone = Icons.Filled.SportsSoccer,
            chipDireita = {
                val cor = if (selecionados.size >= 5) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(cor.copy(alpha = 0.18f))
                        .border(0.5.dp, cor.copy(alpha = 0.5f), RoundedCornerShape(Radius.sm))
                        .padding(horizontal = Spacing.sm, vertical = 4.dp)
                ) {
                    Text(
                        "${selecionados.size}/5",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = cor
                    )
                }
            }
        )

        Text(
            "ESCOLHA OS 5 COBRADORES (EM ORDEM)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(titulares) { jne ->
                val ordem = selecionados.indexOf(jne) + 1
                val selecionado = ordem > 0
                JogadorLinhaIntervalo(
                    jne = jne,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "FIN ${jne.jogador.finalizacao}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            if (selecionado) {
                                FilledTonalButton(
                                    onClick = { selecionados.remove(jne) },
                                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(
                                        "#$ordem",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(Spacing.xs))
                                    Icon(
                                        Icons.Filled.Cancel,
                                        contentDescription = "Remover",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else if (selecionados.size < 5) {
                                OutlinedButton(
                                    onClick = { selecionados.add(jne) },
                                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                                    shape = RoundedCornerShape(Radius.sm)
                                ) {
                                    Text(
                                        "ADICIONAR",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = { onConfirmar(selecionados.toList()) },
                enabled = selecionados.size >= 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                shape = RoundedCornerShape(Radius.md),
                contentPadding = PaddingValues(vertical = Spacing.md)
            ) {
                Text("Confirmar e disputar pênaltis", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
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
    val cobrancasCasaShown = remember { mutableStateListOf<br.com.managerfoot.domain.model.EventoPenalti>() }
    val cobrancasForaShown = remember { mutableStateListOf<br.com.managerfoot.domain.model.EventoPenalti>() }
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
            if (ehCasa) {
                if (ev.convertido) golsCasaAtual++
                cobrancasCasaShown.add(ev)
            } else {
                if (ev.convertido) golsForaAtual++
                cobrancasForaShown.add(ev)
            }
            cobrancasExibidas.add(ItemCobranca(ehCasa, ev))
            scope.launch { listState.animateScrollToItem(cobrancasExibidas.size - 1) }
            delay(900)
        }
        animacaoFinalizada = true
    }

    val isJogadorCasa = penaltis.timeCasaId == timeJogadorId

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PenaltiPlacardCard(
            nomeTimeCasa     = nomeTimeCasa,
            nomeTimeFora     = nomeTimeFora,
            golsCasa         = golsCasaAtual,
            golsFora         = golsForaAtual,
            cobrancasCasa    = cobrancasCasaShown.toList(),
            cobrancasFora    = cobrancasForaShown.toList(),
            isSuddenDeath    = (cobrancasCasaShown.size > 5 || cobrancasForaShown.size > 5),
            tituloRodada     = if (animacaoFinalizada) "DISPUTA ENCERRADA" else "DISPUTA EM ANDAMENTO",
            agregadoCasa     = 0,
            agregadoFora     = 0,
            destaqueVencedor = animacaoFinalizada,
            timeJogadorEhCasa = isJogadorCasa,
            mostrarVencedor  = animacaoFinalizada,
            vencedorNome     = if (animacaoFinalizada) {
                if (penaltis.vencedorId == penaltis.timeCasaId) nomeTimeCasa else nomeTimeFora
            } else null,
            jogadorVenceu    = penaltis.vencedorId == timeJogadorId
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(cobrancasExibidas) { item ->
                AnimatedVisibility(visible = true, enter = slideInVertically { -it } + fadeIn()) {
                    val nomeTime = if (item.ehCasa) nomeTimeCasa else nomeTimeFora
                    PenaltiCobrancaCard(nomeTime = nomeTime, evento = item.evento)
                }
            }
        }

        if (animacaoFinalizada) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = onConcluir,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    shape = RoundedCornerShape(Radius.md),
                    contentPadding = PaddingValues(vertical = Spacing.md)
                ) {
                    Text("Voltar ao painel", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(Spacing.sm))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
    subtituloHeader: String = "Faça sua intervenção antes do 2º tempo",
    estatisticasCasa: EstatisticasTime? = null,
    estatisticasFora: EstatisticasTime? = null,
    nomeTimeCasaStats: String = "",
    nomeTimeForaStats: String = "",
    escalacaoAdversario: Escalacao? = null,
    titularesAdversarioAtuais: List<JogadorNaEscalacao> = escalacaoAdversario?.titulares ?: emptyList()
) {
    var abaAtiva by remember { mutableIntStateOf(0) }
    var titularSelecionado by remember { mutableStateOf<JogadorNaEscalacao?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header com chip de substituições restantes
        PainelHeader(
            titulo = tituloHeader,
            subtitulo = subtituloHeader,
            accent = MaterialTheme.colorScheme.primary,
            chipDireita = {
                val limiteAtingido = substituicoes.size >= 6
                val cor = if (limiteAtingido) MaterialTheme.colorScheme.error
                          else MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(cor.copy(alpha = 0.18f))
                        .border(0.5.dp, cor.copy(alpha = 0.5f), RoundedCornerShape(Radius.sm))
                        .padding(horizontal = Spacing.sm, vertical = 4.dp)
                ) {
                    Text(
                        "${substituicoes.size}/6 subs",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = cor
                    )
                }
            }
        )

        // Tabs scrolláveis pra caber 5–6 abas confortavelmente
        ScrollableTabRow(
            selectedTabIndex = abaAtiva,
            edgePadding = Spacing.md,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            val labelStyle = MaterialTheme.typography.labelMedium
            Tab(
                selected = abaAtiva == 0,
                onClick = { abaAtiva = 0; titularSelecionado = null },
                text = { Text("SUBS", style = labelStyle, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp) }
            )
            Tab(
                selected = abaAtiva == 1, onClick = { abaAtiva = 1 },
                text = { Text("TÁTICA", style = labelStyle, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp) }
            )
            Tab(
                selected = abaAtiva == 2, onClick = { abaAtiva = 2 },
                text = { Text("POSIÇÕES", style = labelStyle, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp) }
            )
            Tab(
                selected = abaAtiva == 3, onClick = { abaAtiva = 3 },
                text = { Text("BANCO", style = labelStyle, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp) }
            )
            Tab(
                selected = abaAtiva == 4, onClick = { abaAtiva = 4 },
                text = { Text("STATS", style = labelStyle, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp) }
            )
            if (escalacaoAdversario != null) {
                Tab(
                    selected = abaAtiva == 5, onClick = { abaAtiva = 5 },
                    text = { Text("ADVERSÁRIO", style = labelStyle, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp) }
                )
            }
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
                3 -> BancoTab(reservas = reservas)
                4 -> if (estatisticasCasa != null && estatisticasFora != null) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        EstatisticasTab(
                            estatisticasCasa = estatisticasCasa,
                            estatisticasFora = estatisticasFora,
                            nomeTimeCasa = nomeTimeCasaStats,
                            nomeTimeFora = nomeTimeForaStats
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Estatísticas não disponíveis",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                5 -> if (escalacaoAdversario != null) {
                    EscalacaoAdversarioTab(
                        escalacao = escalacaoAdversario,
                        titularesAtuais = titularesAdversarioAtuais
                    )
                }
            }
        }

        // Botão final destacado
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = onContinuar,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                shape = RoundedCornerShape(Radius.md),
                contentPadding = PaddingValues(vertical = Spacing.md)
            ) {
                Text(rotuloBotao, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
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

    val posOrder = mapOf(Setor.GOLEIRO to 0, Setor.DEFESA to 1, Setor.MEIO to 2, Setor.ATAQUE to 3)
    val titularesOrdenados = remember(titulares) {
        titulares.sortedWith(compareBy(
            { posOrder[it.posicaoUsada.setor] ?: 2 },
            { it.posicaoUsada.ordinal }
        ))
    }
    val reservasOrdenadas = remember(reservas, titularSelecionado) {
        // Quando há titular selecionado, prioriza reservas do mesmo setor
        val byPos = compareBy<JogadorNaEscalacao>(
            { posOrder[it.posicaoUsada.setor] ?: 2 },
            { it.posicaoUsada.ordinal }
        )
        if (titularSelecionado != null) {
            val mesmoSetor = compareBy<JogadorNaEscalacao> {
                if (it.posicaoUsada.setor == titularSelecionado.posicaoUsada.setor) 0 else 1
            }
            reservas.sortedWith(mesmoSetor.then(byPos))
        } else {
            reservas.sortedWith(byPos)
        }
    }

    if (titularSelecionado == null) {
        // Fase 1: Escolher quem sai
        Column(Modifier.fillMaxSize()) {
            Text(
                text = if (podeFazerMais) "ESCOLHA QUEM VAI SAIR"
                       else "LIMITE DE 6 SUBSTITUIÇÕES ATINGIDO",
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = if (podeFazerMais) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
            )
            LazyColumn {
                items(titularesOrdenados) { jne ->
                    JogadorLinhaIntervalo(
                        jne = jne,
                        trailing = {
                            if (podeFazerMais) {
                                FilledTonalButton(
                                    onClick = { onSelecionarTitular(jne) },
                                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text("SAI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                }
            }
        }
    } else {
        // Fase 2: Escolher quem entra
        Column(Modifier.fillMaxSize()) {
            // Banner contextual mostrando quem sai
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "SUBSTITUINDO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "${titularSelecionado.jogador.nome} (${titularSelecionado.posicaoUsada.abreviacao})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = { onSelecionarTitular(null) }) {
                        Text("Cancelar")
                    }
                }
            }
            Text(
                text = "ESCOLHA QUEM ENTRA",
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn {
                items(reservasOrdenadas) { jne ->
                    JogadorLinhaIntervalo(
                        jne = jne,
                        trailing = {
                            Button(
                                onClick = { onSubstituicao(titularSelecionado, jne) },
                                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                                shape = RoundedCornerShape(Radius.sm)
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(Spacing.xs))
                                Text("ENTRA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun BancoTab(reservas: List<JogadorNaEscalacao>) {
    val posOrder = mapOf(Setor.GOLEIRO to 0, Setor.DEFESA to 1, Setor.MEIO to 2, Setor.ATAQUE to 3)
    val reservasOrdenadas = remember(reservas) {
        reservas.sortedWith(compareBy(
            { posOrder[it.posicaoUsada.setor] ?: 2 },
            { it.posicaoUsada.ordinal }
        ))
    }

    if (reservas.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(Spacing.xxl),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Nenhum jogador disponível no banco",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "${reservasOrdenadas.size} JOGADOR${if (reservasOrdenadas.size != 1) "ES" else ""} NO BANCO",
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        LazyColumn(Modifier.weight(1f)) {
            items(reservasOrdenadas) { jne ->
                JogadorLinhaIntervalo(jne = jne)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun EscalacaoAdversarioTab(
    escalacao: Escalacao,
    titularesAtuais: List<JogadorNaEscalacao> = escalacao.titulares
) {
    val posOrder = mapOf(Setor.GOLEIRO to 0, Setor.DEFESA to 1, Setor.MEIO to 2, Setor.ATAQUE to 3)
    val titularesOrdenados = remember(titularesAtuais) {
        titularesAtuais.sortedWith(compareBy(
            { posOrder[it.posicaoUsada.setor] ?: 2 },
            { it.posicaoUsada.ordinal }
        ))
    }
    val forcaAtual = remember(titularesAtuais) {
        CalculadoraForca.calcularForcaTime(escalacao.copy(titulares = titularesAtuais)).toInt()
    }
    var mostrarGramado by remember { mutableStateOf(false) }
    val estiloLabel = when (escalacao.time.estiloJogo) {
        EstiloJogo.OFENSIVO -> "Ofensivo"
        EstiloJogo.EQUILIBRADO -> "Equilibrado"
        EstiloJogo.DEFENSIVO -> "Defensivo"
        EstiloJogo.CONTRA_ATAQUE -> "Contra-ataque"
    }

    Column(Modifier.fillMaxSize()) {
        // Cabeçalho com escudo + dados táticos do adversário
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                TeamBadge(
                    nome = escalacao.time.nome,
                    escudoRes = escalacao.time.escudoRes,
                    size = 44.dp
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        escalacao.time.nome,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${escalacao.time.taticaFormacao} · $estiloLabel · Força $forcaAtual",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Toggle Lista | Campo
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.pill))
                        .padding(2.dp)
                ) {
                    ToggleSegment(
                        label = "Lista",
                        selected = !mostrarGramado,
                        onClick = { mostrarGramado = false }
                    )
                    ToggleSegment(
                        label = "Campo",
                        selected = mostrarGramado,
                        onClick = { mostrarGramado = true }
                    )
                }
            }
        }

        if (mostrarGramado) {
            GramadoTatico(
                titulares = titularesAtuais,
                formacao = escalacao.time.taticaFormacao,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(Spacing.sm)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(titularesOrdenados) { jne ->
                    JogadorLinhaIntervalo(jne = jne)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun ToggleSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrocarPosicoesTab(
    titulares: List<JogadorNaEscalacao>,
    onTrocarPosicoes: (a: JogadorNaEscalacao, b: JogadorNaEscalacao) -> Unit
) {
    var selecionado by remember { mutableStateOf<JogadorNaEscalacao?>(null) }
    val sel = selecionado
    if (sel == null) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "SELECIONE QUEM VAI MOVER DE POSIÇÃO",
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            LazyColumn {
                items(titulares) { jne ->
                    JogadorLinhaIntervalo(
                        jne = jne,
                        trailing = {
                            FilledTonalButton(
                                onClick = { selecionado = jne },
                                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                                    contentColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("MOVER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "TROCAR POSIÇÃO DE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "${sel.jogador.nome} (${sel.posicaoUsada.abreviacao})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = { selecionado = null }) { Text("Cancelar") }
                }
            }
            Text(
                "ESCOLHA COM QUEM TROCAR",
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            LazyColumn {
                items(titulares.filter { it.jogador.id != sel.jogador.id }) { jne ->
                    JogadorLinhaIntervalo(
                        jne = jne,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${sel.posicaoUsada.abreviacao} ↔ ${jne.posicaoUsada.abreviacao}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Button(
                                    onClick = {
                                        onTrocarPosicoes(sel, jne)
                                        selecionado = null
                                    },
                                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                                    shape = RoundedCornerShape(Radius.sm)
                                ) {
                                    Text("TROCAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
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
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // ── Formação ──────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "FORMAÇÃO TÁTICA",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                "Escolha o esquema tático para o 2º tempo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                formacoes.forEach { f ->
                    FilterChip(
                        selected = f == formacaoAtual,
                        onClick = { onFormacaoChange(f) },
                        label = {
                            Text(
                                f,
                                fontWeight = if (f == formacaoAtual) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(Radius.sm)
                    )
                }
            }
        }

        // ── Gramado tático ────────────────────────────────────────
        if (titulares.isNotEmpty()) {
            GramadoTatico(
                titulares = titulares,
                formacao  = formacaoAtual,
                modifier  = Modifier.padding(vertical = Spacing.xs)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // ── Estilo de jogo ────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "ESTILO DE JOGO",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                "Postura tática da equipe no 2º tempo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EstiloJogo.entries.forEach { estilo ->
                EstiloJogoCard(
                    estilo = estilo,
                    selecionado = estilo == estiloAtual,
                    onClick = { onEstiloChange(estilo) }
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun EstiloJogoCard(
    estilo: EstiloJogo,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    val (label, descricao) = when (estilo) {
        EstiloJogo.OFENSIVO      -> "Ofensivo"      to "Pressiona alto, busca o gol mas se expõe atrás."
        EstiloJogo.EQUILIBRADO   -> "Equilibrado"   to "Postura padrão, sem riscos extras."
        EstiloJogo.DEFENSIVO     -> "Defensivo"     to "Recua linhas, foco em segurar o resultado."
        EstiloJogo.CONTRA_ATAQUE -> "Contra-ataque" to "Aguarda o adversário e explora espaços rápidos."
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (selecionado)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selecionado) 1.5.dp else 1.dp,
            color = if (selecionado) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Indicador radio à esquerda
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        if (selecionado) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selecionado) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    descricao,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Card de evento individual no feed
// ─────────────────────────────────────────────
@Composable
fun EventoCard(evento: EventoExibicao, nomeTimeCasa: String) {
    // Linha de intervalo (separador entre 1ºT e 2ºT)
    if (evento.timeId == -1) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                thickness = 1.dp
            )
            Text(
                text = "INTERVALO",
                modifier = Modifier.padding(horizontal = Spacing.md),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                thickness = 1.dp
            )
        }
        return
    }

    val ehCasa = evento.nomeTime == nomeTimeCasa
    val style = eventoStyle(evento.tipo)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = style.background),
        shape = RoundedCornerShape(Radius.md),
        border = if (style.destacado)
            BorderStroke(1.dp, style.accent.copy(alpha = 0.5f))
        else null
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (ehCasa) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(style.accent))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (ehCasa) Arrangement.Start else Arrangement.End
            ) {
                if (ehCasa) {
                    EventoMinuto(evento.minuto, style.accent)
                    Spacer(Modifier.width(Spacing.sm))
                    EventoIcone(evento.tipo, style.accent)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = evento.descricao,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (style.destacado) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = evento.nomeTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = evento.descricao,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (style.destacado) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = evento.nomeTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    EventoIcone(evento.tipo, style.accent)
                    Spacer(Modifier.width(Spacing.sm))
                    EventoMinuto(evento.minuto, style.accent)
                }
            }
            if (!ehCasa) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(style.accent))
            }
        }
    }
}

private data class EventoStyle(
    val accent: Color,
    val background: Color,
    val destacado: Boolean = false
)

@Composable
private fun eventoStyle(tipo: TipoEvento): EventoStyle {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    return when (tipo) {
        TipoEvento.GOL, TipoEvento.PENALTI_CONVERTIDO -> EventoStyle(
            accent = Color(0xFF00E676),
            background = Color(0xFF00E676).copy(alpha = 0.10f),
            destacado = true
        )
        TipoEvento.CARTAO_AMARELO -> EventoStyle(
            accent = Color(0xFFFFB300),
            background = Color(0xFFFFB300).copy(alpha = 0.08f)
        )
        TipoEvento.CARTAO_VERMELHO -> EventoStyle(
            accent = Color(0xFFFF4444),
            background = Color(0xFFFF4444).copy(alpha = 0.10f),
            destacado = true
        )
        TipoEvento.LESAO -> EventoStyle(
            accent = Color(0xFFE91E63),
            background = Color(0xFFE91E63).copy(alpha = 0.08f)
        )
        TipoEvento.PENALTI_PERDIDO -> EventoStyle(
            accent = Color(0xFFFF6F00),
            background = Color(0xFFFF6F00).copy(alpha = 0.08f)
        )
        TipoEvento.ASSISTENCIA -> EventoStyle(
            accent = Color(0xFF42A5F5),
            background = Color(0xFF42A5F5).copy(alpha = 0.06f)
        )
        TipoEvento.SUBSTITUICAO_ENTRA -> EventoStyle(
            accent = Color(0xFF66BB6A),
            background = Color(0xFF66BB6A).copy(alpha = 0.06f)
        )
        TipoEvento.SUBSTITUICAO_SAI -> EventoStyle(
            accent = Color(0xFFFF7043),
            background = Color(0xFFFF7043).copy(alpha = 0.05f)
        )
        else -> EventoStyle(
            accent = primary,
            background = surface
        )
    }
}

@Composable
private fun EventoMinuto(minuto: Int, accent: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f))
    ) {
        Text(
            text = "${minuto}'",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

@Composable
private fun EventoIcone(tipo: TipoEvento, accent: Color) {
    when (tipo) {
        TipoEvento.CARTAO_AMARELO -> CartaoIcon(Color(0xFFFFB300))
        TipoEvento.CARTAO_VERMELHO -> CartaoIcon(Color(0xFFFF4444))
        TipoEvento.GOL, TipoEvento.PENALTI_CONVERTIDO -> Icon(
            imageVector = Icons.Filled.SportsSoccer,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
        TipoEvento.LESAO -> Icon(
            imageVector = Icons.Filled.LocalHospital,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        TipoEvento.PENALTI_PERDIDO -> Icon(
            imageVector = Icons.Filled.Cancel,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        TipoEvento.SUBSTITUICAO_ENTRA -> Icon(
            imageVector = Icons.Filled.ArrowUpward,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        TipoEvento.SUBSTITUICAO_SAI -> Icon(
            imageVector = Icons.Filled.ArrowDownward,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        TipoEvento.ASSISTENCIA -> Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        else -> Icon(
            imageVector = Icons.Filled.SportsSoccer,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CartaoIcon(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 14.dp, height = 20.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
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
    val erro = MaterialTheme.colorScheme.error

    // Reservas ordenadas: mesmo setor do lesionado primeiro
    val reservasOrdenadas = remember(reservas, jogadorLesionado) {
        val posOrder = mapOf(Setor.GOLEIRO to 0, Setor.DEFESA to 1, Setor.MEIO to 2, Setor.ATAQUE to 3)
        val byPos = compareBy<JogadorNaEscalacao>(
            { posOrder[it.posicaoUsada.setor] ?: 2 },
            { it.posicaoUsada.ordinal }
        )
        if (jogadorLesionado != null) {
            val mesmoSetor = compareBy<JogadorNaEscalacao> {
                if (it.posicaoUsada.setor == jogadorLesionado.posicaoUsada.setor) 0 else 1
            }
            reservas.sortedWith(mesmoSetor.then(byPos))
        } else {
            reservas.sortedWith(byPos)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header de alerta
        PainelHeader(
            titulo = "Jogador lesionado",
            subtitulo = if (jogadorLesionado != null)
                "${jogadorLesionado.jogador.nome} (${jogadorLesionado.posicaoUsada.abreviacao}) saiu de campo"
            else "Substituição forçada",
            accent = erro,
            icone = Icons.Filled.LocalHospital
        )

        if (reservas.isEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "Sem reservas disponíveis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "O time continuará com um jogador a menos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = onSemReservas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    shape = RoundedCornerShape(Radius.md),
                    contentPadding = PaddingValues(vertical = Spacing.md)
                ) {
                    Text("Continuar com 10", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Text(
                "ESCOLHA O SUBSTITUTO",
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            LazyColumn(Modifier.weight(1f)) {
                items(reservasOrdenadas) { jne ->
                    JogadorLinhaIntervalo(
                        jne = jne,
                        trailing = {
                            Button(
                                onClick = { onSubstituicao(jne) },
                                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                                shape = RoundedCornerShape(Radius.sm)
                            ) {
                                Icon(
                                    Icons.Filled.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text("ENTRA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Sub-entrega C: helpers comuns dos painéis modais
// ─────────────────────────────────────────────────────────────

// corSetor + PosicaoBadge promovidos para Components.kt (públicos)

/**
 * Linha de jogador padrão usada em SubstituicoesTab, BancoTab,
 * TrocarPosicoesTab, EscalacaoAdversarioTab e LesaoPainel.
 *
 * - Faixa lateral colorida pelo setor à esquerda (3dp).
 * - Badge de posição + nome + força.
 * - Slot trailing para botão de ação.
 */
@Composable
private fun JogadorLinhaIntervalo(
    jne: JogadorNaEscalacao,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cor = corSetor(jne.posicaoUsada.setor)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(cor)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            PosicaoBadge(
                abreviacao = jne.posicaoUsada.abreviacao,
                setor = jne.posicaoUsada.setor
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = jne.jogador.nome,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Força ${jne.jogador.forca} · ${jne.jogador.idade} anos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing()
        }
    }
}

/**
 * Cabeçalho contextual usado em IntervaloPainel e LesaoPainel.
 * Fica sticky no topo, com barra de acento colorida à esquerda.
 */
@Composable
private fun PainelHeader(
    titulo: String,
    subtitulo: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    icone: ImageVector? = null,
    chipDireita: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.10f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(accent, RoundedCornerShape(Radius.sm))
            )
            if (icone != null) {
                Icon(
                    imageVector = icone,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = titulo.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (chipDireita != null) chipDireita()
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Sub-entrega A: scoreboard polido + timeline de eventos
// ─────────────────────────────────────────────────────────────

@Composable
private fun MatchScoreboard(
    nomeTimeCasa: String,
    nomeTimeFora: String,
    escudoTimeCasa: String,
    escudoTimeFora: String,
    faseAtual: String,
    minutoAtual: Int,
    golsCasaAtual: Int,
    golsForaAtual: Int,
    scoreFontSize: androidx.compose.ui.unit.TextUnit,
    progresso: Float,
    eventosTimeline: List<EventoExibicao>,
    timeCasaId: Int,
    simulacaoEncerrada: Boolean,
    torcedores: Int,
    nomeEstadio: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            MatchPhaseStripe(faseAtual, minutoAtual, simulacaoEncerrada)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreboardTeamColumn(
                    nome = nomeTimeCasa,
                    escudo = escudoTimeCasa,
                    label = "Casa",
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    AnimatedContent(
                        targetState = golsCasaAtual,
                        transitionSpec = {
                            slideInVertically { -it } + fadeIn() togetherWith
                                slideOutVertically { it } + fadeOut()
                        },
                        label = "gols_casa"
                    ) { gols ->
                        Text(
                            text = gols.toString(),
                            fontSize = scoreFontSize,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "-",
                        fontSize = (scoreFontSize.value * 0.6f).sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnimatedContent(
                        targetState = golsForaAtual,
                        transitionSpec = {
                            slideInVertically { -it } + fadeIn() togetherWith
                                slideOutVertically { it } + fadeOut()
                        },
                        label = "gols_fora"
                    ) { gols ->
                        Text(
                            text = gols.toString(),
                            fontSize = scoreFontSize,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                ScoreboardTeamColumn(
                    nome = nomeTimeFora,
                    escudo = escudoTimeFora,
                    label = "Visitante",
                    modifier = Modifier.weight(1f)
                )
            }

            MatchTimelineBar(
                progresso = progresso,
                eventos = eventosTimeline,
                timeCasaId = timeCasaId,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("45'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("90'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (torcedores > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .padding(bottom = Spacing.md),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "👥", fontSize = 12.sp)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = "%,d".format(torcedores) + (if (nomeEstadio.isNotBlank()) " · $nomeEstadio" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchPhaseStripe(faseAtual: String, minutoAtual: Int, simulacaoEncerrada: Boolean) {
    val accent = when (faseAtual) {
        "PRÉ-JOGO" -> MaterialTheme.colorScheme.onSurfaceVariant
        "1º TEMPO", "2º TEMPO" -> MaterialTheme.colorScheme.primary
        "INTERVALO" -> Color(0xFFFFB300)
        "FIM DE JOGO" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (faseAtual in listOf("1º TEMPO", "2º TEMPO") && !simulacaoEncerrada) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            text = if (minutoAtual > 0 && !simulacaoEncerrada)
                "$faseAtual  ·  ${minutoAtual}'"
            else faseAtual,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun ScoreboardTeamColumn(
    nome: String,
    escudo: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TeamBadge(nome = nome, escudoRes = escudo, size = 56.dp)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = nome,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MatchTimelineBar(
    progresso: Float,
    eventos: List<EventoExibicao>,
    timeCasaId: Int,
    modifier: Modifier = Modifier
) {
    val totalMin = 95
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val progressColor = MaterialTheme.colorScheme.primary

    val dots = remember(eventos) {
        eventos
            .filter { it.timeId != -1 }
            .filter { it.tipo in setOf(
                TipoEvento.GOL,
                TipoEvento.PENALTI_CONVERTIDO,
                TipoEvento.PENALTI_PERDIDO,
                TipoEvento.CARTAO_AMARELO,
                TipoEvento.CARTAO_VERMELHO
            ) }
    }

    BoxWithConstraints(modifier = modifier.height(20.dp)) {
        val widthPx = constraints.maxWidth.toFloat()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(trackColor)
                .align(Alignment.Center)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progresso)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(progressColor)
                .align(Alignment.CenterStart)
        )

        dots.forEach { ev ->
            val frac = (ev.minuto / totalMin.toFloat()).coerceIn(0f, 1f)
            val color = when (ev.tipo) {
                TipoEvento.GOL, TipoEvento.PENALTI_CONVERTIDO -> Color(0xFF00E676)
                TipoEvento.PENALTI_PERDIDO -> Color(0xFFFF6F00)
                TipoEvento.CARTAO_AMARELO -> Color(0xFFFFB300)
                TipoEvento.CARTAO_VERMELHO -> Color(0xFFFF4444)
                else -> MaterialTheme.colorScheme.primary
            }
            val dotSize = if (ev.tipo in setOf(TipoEvento.GOL, TipoEvento.PENALTI_CONVERTIDO, TipoEvento.CARTAO_VERMELHO)) 10.dp else 7.dp
            Box(
                modifier = Modifier
                    .offset {
                        val sizePx = dotSize.toPx()
                        androidx.compose.ui.unit.IntOffset(
                            x = (frac * widthPx - sizePx / 2f).toInt(),
                            y = 0
                        )
                    }
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color)
                    .align(Alignment.CenterStart)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Sub-entrega B: Man of the Match — destaque do clube do jogador
// ─────────────────────────────────────────────────────────────

@Composable
private fun ManOfTheMatchCard(
    resultado: ResultadoPartida,
    escalacaoJogador: Escalacao?,
    isTimeCasaOJogador: Boolean
) {
    if (escalacaoJogador == null || resultado.notasJogadores.isEmpty()) return

    val timeJogadorId = if (isTimeCasaOJogador) resultado.timeCasaId else resultado.timeForaId
    val jogadoresDoTime = (escalacaoJogador.titulares + escalacaoJogador.reservas)
        .associate { it.jogador.id to it.jogador }

    val melhor = resultado.notasJogadores
        .filter { (id, _) -> id in jogadoresDoTime }
        .maxByOrNull { it.value } ?: return

    val jogador = jogadoresDoTime[melhor.key] ?: return
    val nota = melhor.value
    val cor = notaColor(nota)

    val golsJog = resultado.eventos.count {
        it.jogadorId == jogador.id &&
        (it.tipo == TipoEvento.GOL || it.tipo == TipoEvento.PENALTI_CONVERTIDO)
    }
    val assistJog = resultado.eventos.count {
        it.jogadorId == jogador.id && it.tipo == TipoEvento.ASSISTENCIA
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, cor.copy(alpha = 0.5f))
    ) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                cor.copy(alpha = 0.10f),
                                cor.copy(alpha = 0f)
                            )
                        )
                    )
            )
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = cor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "MELHOR EM CAMPO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = cor,
                        letterSpacing = 1.5.sp
                    )
                }
                Spacer(Modifier.height(Spacing.md))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(cor.copy(alpha = 0.18f))
                            .border(2.dp, cor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "%.1f".format(nota),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = cor
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = jogador.nome,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${jogador.posicao.abreviacao} · ${jogador.idade} anos · Força ${jogador.forca}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (golsJog > 0 || assistJog > 0) {
                            Spacer(Modifier.height(Spacing.xs))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (golsJog > 0) {
                                    StatPill(
                                        icon = Icons.Filled.SportsSoccer,
                                        valor = golsJog,
                                        label = if (golsJog == 1) "gol" else "gols",
                                        accent = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (assistJog > 0) {
                                    StatPill(
                                        icon = Icons.Filled.SwapHoriz,
                                        valor = assistJog,
                                        label = if (assistJog == 1) "assist." else "assists",
                                        accent = Color(0xFF42A5F5)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    icon: ImageVector,
    valor: Int,
    label: String,
    accent: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "$valor $label",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Sub-entrega D: helpers da disputa de pênaltis
// ─────────────────────────────────────────────────────────────

/**
 * Placard dramático mostrando placar atual + tracker de cobranças (bolinhas
 * verde/vermelho/cinza) + opcionalmente um banner com o vencedor.
 *
 * Reusado pelo PenaltiInterativoPainel (durante a disputa) e pelo
 * PenaltiResultadoPainel (replay animado).
 */
@Composable
private fun PenaltiPlacardCard(
    nomeTimeCasa: String,
    nomeTimeFora: String,
    golsCasa: Int,
    golsFora: Int,
    cobrancasCasa: List<EventoPenalti>,
    cobrancasFora: List<EventoPenalti>,
    isSuddenDeath: Boolean,
    tituloRodada: String,
    agregadoCasa: Int,
    agregadoFora: Int,
    destaqueVencedor: Boolean,
    timeJogadorEhCasa: Boolean,
    mostrarVencedor: Boolean = false,
    vencedorNome: String? = null,
    jogadorVenceu: Boolean = false
) {
    val accent = if (isSuddenDeath) MaterialTheme.colorScheme.error
                 else MaterialTheme.colorScheme.primary
    val mostrarAgregado = agregadoCasa > 0 || agregadoFora > 0

    Card(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Column {
            // Faixa superior com título da rodada
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent.copy(alpha = 0.14f))
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSuddenDeath) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(
                    text = tituloRodada,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    letterSpacing = 1.sp
                )
            }

            // Placar gigante
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    nomeTimeCasa,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.padding(horizontal = Spacing.md)
                ) {
                    AnimatedContent(
                        targetState = golsCasa,
                        transitionSpec = {
                            slideInVertically { -it } + fadeIn() togetherWith
                                slideOutVertically { it } + fadeOut()
                        },
                        label = "pgc"
                    ) { g ->
                        Text(
                            g.toString(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "-",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnimatedContent(
                        targetState = golsFora,
                        transitionSpec = {
                            slideInVertically { -it } + fadeIn() togetherWith
                                slideOutVertically { it } + fadeOut()
                        },
                        label = "pgf"
                    ) { g ->
                        Text(
                            g.toString(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    nomeTimeFora,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Tracker de cobranças
            PenaltiTracker(
                cobrancasCasa = cobrancasCasa,
                cobrancasFora = cobrancasFora,
                slotsMin = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )

            // Agregado (se aplicável)
            if (mostrarAgregado) {
                Text(
                    "Agregado: $agregadoCasa × $agregadoFora",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .padding(bottom = Spacing.sm),
                    textAlign = TextAlign.Center
                )
            }

            // Banner de vencedor
            if (mostrarVencedor && vencedorNome != null) {
                val cor = if (jogadorVenceu) Color(0xFF00E676)
                          else MaterialTheme.colorScheme.error
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cor.copy(alpha = 0.14f))
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = cor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "$vencedorNome AVANÇA",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = cor,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * Duas linhas de bolinhas (uma por time) representando cada cobrança:
 *  - verde = convertido
 *  - vermelho = perdido
 *  - cinza vazio = ainda não cobrado (até o slot mínimo)
 */
@Composable
private fun PenaltiTracker(
    cobrancasCasa: List<EventoPenalti>,
    cobrancasFora: List<EventoPenalti>,
    slotsMin: Int = 5,
    modifier: Modifier = Modifier
) {
    val totalSlots = maxOf(slotsMin, cobrancasCasa.size, cobrancasFora.size)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        PenaltiTrackerLinha(cobrancas = cobrancasCasa, totalSlots = totalSlots)
        PenaltiTrackerLinha(cobrancas = cobrancasFora, totalSlots = totalSlots)
    }
}

@Composable
private fun PenaltiTrackerLinha(
    cobrancas: List<EventoPenalti>,
    totalSlots: Int
) {
    val empty = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val gol   = Color(0xFF00E676)
    val miss  = MaterialTheme.colorScheme.error
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(totalSlots) { i ->
            val ev = cobrancas.getOrNull(i)
            val color = when {
                ev == null         -> empty
                ev.convertido      -> gol
                else               -> miss
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * Card final de vencedor — mostrado em PenaltiInterativoPainel.FINALIZADO.
 */
@Composable
private fun PenaltiVencedorCard(
    nomeVencedor: String,
    placar: String,
    jogadorVenceu: Boolean
) {
    val cor = if (jogadorVenceu) Color(0xFF00E676) else MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, cor.copy(alpha = 0.5f))
    ) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                cor.copy(alpha = 0.10f),
                                cor.copy(alpha = 0f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(cor.copy(alpha = 0.18f))
                        .border(2.dp, cor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = cor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "VENCEDOR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cor,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = nomeVencedor,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$placar nos pênaltis",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
