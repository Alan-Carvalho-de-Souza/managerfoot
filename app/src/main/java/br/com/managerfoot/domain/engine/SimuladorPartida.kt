package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.EstiloJogo
import br.com.managerfoot.data.database.entities.Setor
import br.com.managerfoot.data.database.entities.TipoEvento
import br.com.managerfoot.domain.model.*
import kotlin.math.roundToInt
import kotlin.random.Random

// ─────────────────────────────────────────────
//  CalculadoraForca
//  Determina a força de um time a partir da
//  escalação, formação e estilo de jogo.
// ─────────────────────────────────────────────
object CalculadoraForca {

    // Pesos por setor (somam 1.0)
    private const val PESO_GOLEIRO = 0.15
    private const val PESO_DEFESA  = 0.30
    private const val PESO_MEIO    = 0.30
    private const val PESO_ATAQUE  = 0.25

    fun calcularForcaTime(escalacao: Escalacao): Double {
        val titulares = escalacao.titulares
        if (titulares.isEmpty()) return 0.0

        // Valor de cada jogador leva em conta forca efetiva + atributos específicos do setor
        // fisico contribui como resistência/imposição física em todos os setores
        fun valorJogador(jne: JogadorNaEscalacao): Double {
            val fe  = jne.jogador.forcaEfetiva(jne.posicaoUsada).toDouble()
            val fis = jne.jogador.fisico.toDouble()
            return when (jne.posicaoUsada.setor) {
                // Goleiro: defesa crítica (35%) + passe/saída (10%) + fisico p/ duelos (10%)
                Setor.GOLEIRO -> fe * 0.45 + jne.jogador.defesa  * 0.35 + jne.jogador.passe * 0.10 + fis * 0.10
                // Zagueiros/Laterais: defesa (22%) + fisico para marcação (15%) + técnica (13%) + passe (10%)
                Setor.DEFESA  -> fe * 0.40 + jne.jogador.defesa  * 0.22 + fis * 0.15 + jne.jogador.tecnica * 0.13 + jne.jogador.passe * 0.10
                // Meias: técnica (18%) + passe (18%) + fisico/stamina (10%) + marcação (14%)
                Setor.MEIO    -> fe * 0.40 + jne.jogador.tecnica * 0.18 + jne.jogador.passe * 0.18 + jne.jogador.defesa * 0.14 + fis * 0.10
                // Atacantes: finalizacao via técnica (15%) + passe (8%) + fisico p/ duelos (8%)
                Setor.ATAQUE  -> fe * 0.62 + jne.jogador.tecnica * 0.15 + jne.jogador.passe * 0.08 + fis * 0.08 + jne.jogador.defesa * 0.07
            }
        }

        fun mediaSetor(setor: Setor) = titulares
            .filter { it.posicaoUsada.setor == setor }
            .map { valorJogador(it) }
            .takeIf { it.isNotEmpty() }
            ?.average() ?: 0.0

        val fGoleiro = mediaSetor(Setor.GOLEIRO)
        val fDefesa  = mediaSetor(Setor.DEFESA)
        val fMeio    = mediaSetor(Setor.MEIO)
        val fAtaque  = mediaSetor(Setor.ATAQUE)

        return fGoleiro * PESO_GOLEIRO +
                fDefesa  * PESO_DEFESA  +
                fMeio    * PESO_MEIO    +
                fAtaque  * PESO_ATAQUE
    }

    // Fatores modificadores externos
    // Fator casa proporcional à capacidade do estádio
    // 5k=1% ... 100k=12% conforme tabela de tiers
    fun fatorMandante(capacidade: Int): Double = when {
        capacidade >= 100_000 -> 1.12
        capacidade >=  80_000 -> 1.11
        capacidade >=  60_000 -> 1.10
        capacidade >=  50_000 -> 1.09
        capacidade >=  40_000 -> 1.08
        capacidade >=  35_000 -> 1.07
        capacidade >=  30_000 -> 1.06
        capacidade >=  25_000 -> 1.05
        capacidade >=  20_000 -> 1.04
        capacidade >=  15_000 -> 1.03
        capacidade >=  10_000 -> 1.02
        capacidade >=   5_000 -> 1.01
        else                  -> 1.0
    }
    fun fatorCansaco(jogosUltimos7Dias: Int): Double =
        when {
            jogosUltimos7Dias >= 3 -> 0.93
            jogosUltimos7Dias == 2 -> 0.97
            else -> 1.0
        }
    fun fatorEstilo(atacante: EstiloJogo, defensor: EstiloJogo): Double =
        when {
            // CONTRA_ATAQUE explora os espaços deixados pelo time OFENSIVO (transições devastadoras)
            atacante == EstiloJogo.CONTRA_ATAQUE && defensor == EstiloJogo.OFENSIVO -> 1.14
            // OFENSIVO deixa espaços às costas para o adversário atacar em transição
            atacante == EstiloJogo.OFENSIVO && defensor == EstiloJogo.CONTRA_ATAQUE -> 0.92
            // Bloco DEFENSIVO compacto sufoca o time OFENSIVO — poucas chances criadas
            atacante == EstiloJogo.OFENSIVO && defensor == EstiloJogo.DEFENSIVO     -> 0.88
            // DEFENSIVO absorve a pressão e sai com eficiência contra time aberto
            atacante == EstiloJogo.DEFENSIVO && defensor == EstiloJogo.OFENSIVO     -> 1.10
            else -> 1.0
        }
}

// ─────────────────────────────────────────────
//  SimuladorPartida
//  Motor probabilístico de geração de gols e
//  eventos. Usa distribuição de Poisson para
//  placares realistas.
// ─────────────────────────────────────────────
class SimuladorPartida(private val rng: Random = Random.Default) {

    companion object {
        private const val MEDIA_GOLS_JOGO = 2.7   // média histórica do futebol brasileiro
        private const val PROB_CARTAO_AMARELO = 0.12
        // Probabilidade base de lesão (fisico=50 ≈ risco neutro)
        // fisico=99 → ~1.5%  |  fisico=50 → ~3.0%  |  fisico=1 → ~4.5%
        private const val PROB_LESAO_BASE = 0.03
    }

    fun simular(
        partidaId: Int,
        casa: Escalacao,
        fora: Escalacao,
        jogosRecentesCasa: Int = 0,
        jogosRecentesFora: Int = 0,
        // Quando informado, inibe as substituições táticas aleatórias para o time
        // do jogador — apenas subs forçadas (lesão/expulsão) ocorrem nesses casos.
        timeJogadorId: Int = -1
    ): ResultadoPartida {

        val fCasaRaw = CalculadoraForca.calcularForcaTime(casa) *
                CalculadoraForca.fatorMandante(casa.time.estadioCapacidade) *
                CalculadoraForca.fatorCansaco(jogosRecentesCasa) *
                CalculadoraForca.fatorEstilo(casa.time.estiloJogo, fora.time.estiloJogo)

        val fForaRaw = CalculadoraForca.calcularForcaTime(fora) *
                CalculadoraForca.fatorCansaco(jogosRecentesFora) *
                CalculadoraForca.fatorEstilo(fora.time.estiloJogo, casa.time.estiloJogo)

        // Força mínima de 50 para evitar NaN quando o elenco está vazio
        val fCasa = if (fCasaRaw > 0.0) fCasaRaw else 50.0
        val fFora = if (fForaRaw > 0.0) fForaRaw else 50.0

        // Squads ativos mutáveis — serão alterados por lesões e expulsões
        val titsCasa = casa.titulares.toMutableList()
        val titsFora = fora.titulares.toMutableList()
        val resCasa  = casa.reservas.toMutableList()
        val resFora  = fora.reservas.toMutableList()

        val eventos = mutableListOf<EventoSimulado>()

        // Rastreia janelas de atividade de cada jogador (minuto de entrada / saída)
        val entryMinutes = mutableMapOf<Int, Int>()   // jogadorId → minuto de entrada
        val exitMinutes  = mutableMapOf<Int, Int>()   // jogadorId → minuto de saída
        for (jne in casa.titulares) entryMinutes[jne.jogador.id] = 0
        for (jne in fora.titulares) entryMinutes[jne.jogador.id] = 0

        // Marca a participação de todos os 11 titulares de cada equipe.
        // Substitutos que entram recebem SUBSTITUICAO_ENTRA, cobrindo sua participação.
        for (jne in casa.titulares) eventos.add(EventoSimulado(
            minuto = 0, tipo = TipoEvento.PARTICIPOU,
            jogadorId = jne.jogador.id, timeId = casa.time.id, descricao = ""
        ))
        for (jne in fora.titulares) eventos.add(EventoSimulado(
            minuto = 0, tipo = TipoEvento.PARTICIPOU,
            jogadorId = jne.jogador.id, timeId = fora.time.id, descricao = ""
        ))

        // Penalizações na expectativa de gols:
        //   expulsão ou lesão sem reserva → reduz ~13% por jogador a menos
        var penalCasa = 1.0
        var penalFora = 1.0

        // Processar incidentes (cartões + lesões) em ordem cronológica
        val incidentes = (
            gerarIncidentes(casa.titulares, casa.time.id) +
            gerarIncidentes(fora.titulares, fora.time.id)
        ).sortedBy { it.minuto }

        for (inc in incidentes) {
            val ehCasaInc  = inc.timeId == casa.time.id
            val titsAtivos = if (ehCasaInc) titsCasa else titsFora
            val reservas   = if (ehCasaInc) resCasa  else resFora

            // Ignora evento se o jogador já saiu do campo (expulsão ou lesão anterior)
            if (titsAtivos.none { it.jogador.id == inc.jogadorId }) continue

            eventos.add(inc)
            when (inc.tipo) {
                TipoEvento.CARTAO_VERMELHO -> {
                    // Expulsão: jogador sai sem reposição
                    titsAtivos.removeIf { it.jogador.id == inc.jogadorId }
                    exitMinutes[inc.jogadorId] = inc.minuto
                    if (ehCasaInc) penalCasa *= 0.87 else penalFora *= 0.87
                }
                TipoEvento.LESAO -> {
                    val lesionado = titsAtivos.find { it.jogador.id == inc.jogadorId }
                    if (lesionado != null) {
                        titsAtivos.remove(lesionado)
                        exitMinutes[lesionado.jogador.id] = inc.minuto
                        // Para o time do jogador humano, NÃO substituímos automaticamente.
                        // A escolha de quem entra é feita interativamente na UI (LesaoPainel).
                        // O time joga com um jogador a menos; o custo de força é aplicado.
                        val ehTimeJogador = (inc.timeId == timeJogadorId)
                        if (!ehTimeJogador && reservas.isNotEmpty()) {
                            // Substituição automática por lesão (somente para times IA)
                            val substituto = reservas.removeAt(0)
                            titsAtivos.add(substituto)
                            val minSub = (inc.minuto + 1).coerceAtMost(90)
                            entryMinutes[substituto.jogador.id] = minSub
                            eventos.add(EventoSimulado(
                                minuto = minSub, tipo = TipoEvento.SUBSTITUICAO_SAI,
                                jogadorId = lesionado.jogador.id, timeId = inc.timeId,
                                descricao = lesionado.jogador.nomeAbreviado
                            ))
                            eventos.add(EventoSimulado(
                                minuto = minSub, tipo = TipoEvento.SUBSTITUICAO_ENTRA,
                                jogadorId = substituto.jogador.id, timeId = inc.timeId,
                                descricao = substituto.jogador.nomeAbreviado
                            ))
                        } else {
                            // Time do jogador humano OU sem reservas: fica com um a menos
                            if (ehCasaInc) penalCasa *= 0.87 else penalFora *= 0.87
                        }
                    }
                }
                else -> {}
            }
        }

        val total    = fCasa + fFora
        val probCasa = fCasa / total
        val probFora = fFora / total

        // ── Substituições táticas (até 3 por time) ──────────────────
        // Ocorrem após os incidentes para que reserves de lesão não
        // sejam contados de novo, e ANTES da geração de gols para que
        // o jogador substituído nunca apareça como marcador/assistente.
        // O time do jogador humano não recebe subs aleatórias: elas só
        // ocorrem por escolha dele (ordem do banco que ele próprio definiu).
        if (casa.time.id != timeJogadorId)
            aplicarSubstituicoesTaticas(titsCasa, resCasa, casa.time.id, entryMinutes, exitMinutes, eventos)
        if (fora.time.id != timeJogadorId)
            aplicarSubstituicoesTaticas(titsFora, resFora, fora.time.id, entryMinutes, exitMinutes, eventos)

        // Dominância: time com força maior cria mais oportunidades de gol.
        // dominance ∈ [-1, +1]; o time mais forte ganha até +55% de chances extra.
        val dominance = ((fCasa - fFora) / total).coerceIn(-1.0, 1.0)
        val chancesMultCasa = 1.0 + dominance.coerceAtLeast(0.0) * 0.55
        val chancesMultFora = 1.0 + (-dominance).coerceAtLeast(0.0) * 0.55

        // Média de gols esperados — total esperado é MEDIA_GOLS_JOGO (sem multiplicador extra)
        // Com times iguais: probCasa=probFora=0.5 → mediaGols cada lado = 2.7×0.5 = 1.35 → total = 2.7 ✓
        val mediaGolsCasa = MEDIA_GOLS_JOGO * probCasa * penalCasa * chancesMultCasa
        val mediaGolsFora = MEDIA_GOLS_JOGO * probFora * penalFora * chancesMultFora

        val golsCasa = poissonRandom(mediaGolsCasa)
        val golsFora = poissonRandom(mediaGolsFora)

        // Gols atribuídos aos squads que restaram após os incidentes
        val escalacaoCasaFinal = casa.copy(titulares = titsCasa)
        val escalacaoForaFinal = fora.copy(titulares = titsFora)
        eventos.addAll(gerarEventosGol(golsCasa, escalacaoCasaFinal, true,  entryMinutes, exitMinutes))
        eventos.addAll(gerarEventosGol(golsFora, escalacaoForaFinal, false, entryMinutes, exitMinutes))
        eventos.sortBy { it.minuto }

        // Estatísticas derivadas
        val posseCasa = (50 + ((probCasa - 0.5) * 40)).roundToInt().coerceIn(30, 70)
        val chutesCasa = golsCasa * rng.nextInt(3, 6) + rng.nextInt(2, 5)
        val chutesFora = golsFora * rng.nextInt(3, 6) + rng.nextInt(2, 5)

        return ResultadoPartida(
            partidaId = partidaId,
            timeCasaId = casa.time.id,
            timeForaId = fora.time.id,
            golsCasa = golsCasa,
            golsFora = golsFora,
            eventos = eventos,
            estatisticasCasa = EstatisticasTime(
                chutes = chutesCasa,
                chutesNoGol = golsCasa + rng.nextInt(0, 3),
                posse = posseCasa,
                faltas = rng.nextInt(8, 18),
                cartaoAmarelo = eventos.count { e -> e.tipo == TipoEvento.CARTAO_AMARELO && casa.titulares.any { it.jogador.id == e.jogadorId } },
                cartaoVermelho = eventos.count { e -> e.tipo == TipoEvento.CARTAO_VERMELHO && casa.titulares.any { it.jogador.id == e.jogadorId } }
            ),
            estatisticasFora = EstatisticasTime(
                chutes = chutesFora,
                chutesNoGol = golsFora + rng.nextInt(0, 3),
                posse = 100 - posseCasa,
                faltas = rng.nextInt(8, 18),
                cartaoAmarelo = eventos.count { e -> e.tipo == TipoEvento.CARTAO_AMARELO && fora.titulares.any { it.jogador.id == e.jogadorId } },
                cartaoVermelho = eventos.count { e -> e.tipo == TipoEvento.CARTAO_VERMELHO && fora.titulares.any { it.jogador.id == e.jogadorId } }
            )
        )
    }

    // ── Geração de gols ──
    private fun gerarEventosGol(
        totalGols: Int,
        escalacao: Escalacao,
        ehCasa: Boolean,
        entryMinutes: Map<Int, Int> = emptyMap(),
        exitMinutes: Map<Int, Int> = emptyMap()
    ): List<EventoSimulado> {
        if (totalGols == 0) return emptyList()

        // Todos os não-goleiros são elegíveis; o peso de finalização já distribui corretamente
        val candidatos = escalacao.titulares
            .filter { it.posicaoUsada.setor != Setor.GOLEIRO }

        if (candidatos.isEmpty()) return emptyList()

        // Peso de gol por setor:
        //   ATAQUE: finalizacao é o fator dominante + velocidade + base fixa de 50
        //           garante que qualquer atacante (min ~54) supere qualquer defensor (max 33)
        //   MEIO:   finalizacao x2 + passe → goleadores e meias criativos pontuam bem
        //   DEFESA: apenas finalizacao/3 → bom finalizador marca levemente mais que
        //           colegas de setor, mas nunca ultrapassa atacantes ou meias goleadores
        fun pesoGol(jne: JogadorNaEscalacao): Int = when (jne.posicaoUsada.setor) {
            Setor.ATAQUE -> jne.jogador.finalizacao * 3 + jne.jogador.velocidade + 50
            Setor.MEIO   -> jne.jogador.finalizacao * 2 + jne.jogador.passe
            Setor.DEFESA -> (jne.jogador.finalizacao / 3).coerceAtLeast(1)
            else         -> 1
        }

        // Peso de assistência por setor: meias são os melhores passadores
        fun pesoAssist(jne: JogadorNaEscalacao): Int = when (jne.posicaoUsada.setor) {
            Setor.MEIO   -> jne.jogador.passe * 3 + jne.jogador.tecnica
            Setor.ATAQUE -> jne.jogador.passe * 2 + jne.jogador.velocidade
            Setor.DEFESA -> jne.jogador.passe * 2 + jne.jogador.tecnica
            else         -> 1
        }

        return (1..totalGols).flatMap {
            val marcador = sortearPonderado(candidatos, ::pesoGol)
            // Minuto do gol restrito à janela de atividade do marcador
            val minEntry = (entryMinutes[marcador.jogador.id] ?: 0).coerceAtLeast(1)
            val minExit  = (exitMinutes[marcador.jogador.id]  ?: 90).coerceAtMost(90)
            val minuto   = if (minEntry < minExit) rng.nextInt(minEntry, minExit) else minEntry
            val eventos = mutableListOf(
                EventoSimulado(
                    minuto = minuto,
                    tipo = TipoEvento.GOL,
                    jogadorId = marcador.jogador.id,
                    timeId = escalacao.time.id,
                    descricao = "${marcador.jogador.nomeAbreviado} ${minuto}'"
                )
            )
            // ~72% chance de assistência — apenas jogadores ativos nesse minuto
            if (rng.nextDouble() < 0.72) {
                val candidatosAssist = candidatos.filter { c ->
                    if (c.jogador.id == marcador.jogador.id) return@filter false
                    val cEntry = (entryMinutes[c.jogador.id] ?: 0)
                    val cExit  = (exitMinutes[c.jogador.id]  ?: 90)
                    minuto in cEntry..cExit
                }
                if (candidatosAssist.isNotEmpty()) {
                    val assistente = sortearPonderado(candidatosAssist, ::pesoAssist)
                    eventos.add(
                        EventoSimulado(
                            minuto = minuto,
                            tipo = TipoEvento.ASSISTENCIA,
                            jogadorId = assistente.jogador.id,
                            timeId = escalacao.time.id,
                            descricao = "Ass. ${assistente.jogador.nomeAbreviado}"
                        )
                    )
                }
            }
            eventos
        }
    }

    // ── Substituições táticas por time ──────────────────────────
    // Até 3 trocas por time, espalhadas no intervalo 46–88 min.
    // O jogador que sai é removido de titsAtivos e tem seu exitMinutes
    // definido, garantindo que não apareça em nenhum evento de gol/assist
    // gerado depois desta fase.
    private fun aplicarSubstituicoesTaticas(
        titsAtivos: MutableList<JogadorNaEscalacao>,
        reservas:   MutableList<JogadorNaEscalacao>,
        timeId:     Int,
        entryMinutes: MutableMap<Int, Int>,
        exitMinutes:  MutableMap<Int, Int>,
        eventos:    MutableList<EventoSimulado>
    ) {
        val maxSubs = 3
        if (reservas.isEmpty()) return

        // Escolhe até 3 minutos aleatórios na segunda metade, sem repetição
        val minutosSub = (46..88).toList().shuffled(rng).take(maxSubs).sorted()
        var subsFeitas = 0

        for (minuto in minutosSub) {
            if (subsFeitas >= maxSubs || reservas.isEmpty()) break

            // Prefere não substituir o goleiro
            val candidatosSaida = titsAtivos.filter { it.posicaoUsada.setor != Setor.GOLEIRO }
            if (candidatosSaida.isEmpty()) break

            val saindo   = candidatosSaida[rng.nextInt(candidatosSaida.size)]
            val entrando = reservas.removeAt(0)

            titsAtivos.removeIf { it.jogador.id == saindo.jogador.id }
            titsAtivos.add(entrando)
            exitMinutes[saindo.jogador.id]    = minuto
            entryMinutes[entrando.jogador.id] = minuto

            eventos.add(EventoSimulado(
                minuto    = minuto,
                tipo      = TipoEvento.SUBSTITUICAO_SAI,
                jogadorId = saindo.jogador.id,
                timeId    = timeId,
                descricao = saindo.jogador.nomeAbreviado
            ))
            eventos.add(EventoSimulado(
                minuto    = minuto,
                tipo      = TipoEvento.SUBSTITUICAO_ENTRA,
                jogadorId = entrando.jogador.id,
                timeId    = timeId,
                descricao = entrando.jogador.nomeAbreviado
            ))
            subsFeitas++
        }
    }

    // ── Incidentes (cartões + lesões) por jogador ──
    private fun gerarIncidentes(titulares: List<JogadorNaEscalacao>, timeId: Int): List<EventoSimulado> {
        return titulares.flatMap { jne ->
            val lista = mutableListOf<EventoSimulado>()
            if (rng.nextDouble() < PROB_CARTAO_AMARELO) {
                val minuto = rng.nextInt(1, 90)
                val vermelho = rng.nextDouble() < 0.08
                lista.add(EventoSimulado(
                    minuto = minuto,
                    tipo = if (vermelho) TipoEvento.CARTAO_VERMELHO else TipoEvento.CARTAO_AMARELO,
                    jogadorId = jne.jogador.id,
                    timeId = timeId,
                    descricao = jne.jogador.nomeAbreviado
                ))
            }
            // Jogadores com alto fisico sofrem menos lesões; fisico=99 → ~1.5%, fisico=1 → ~4.5%
            val probLesao = PROB_LESAO_BASE * (1.5 - jne.jogador.fisico / 99.0)
            if (rng.nextDouble() < probLesao) {
                lista.add(EventoSimulado(
                    minuto = rng.nextInt(10, 85),
                    tipo = TipoEvento.LESAO,
                    jogadorId = jne.jogador.id,
                    timeId = timeId,
                    descricao = "${jne.jogador.nomeAbreviado} saiu lesionado"
                ))
            }
            lista
        }
    }

    // ── Distribuição de Poisson ──
    private fun poissonRandom(lambda: Double): Int {
        val l = Math.exp(-lambda)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= rng.nextDouble()
        } while (p > l)
        return (k - 1).coerceIn(0, 9) // máx 9 gols por time
    }

    // ── Sorteia jogador ponderado por função de peso customizável ──
    private fun sortearPonderado(
        candidatos: List<JogadorNaEscalacao>,
        pesoFn: (JogadorNaEscalacao) -> Int = { it.jogador.forca }
    ): JogadorNaEscalacao {
        val pesos = candidatos.map { pesoFn(it).coerceAtLeast(1).toLong() }
        val totalPeso = pesos.sum()
        if (totalPeso <= 0L) return candidatos[rng.nextInt(candidatos.size)]
        var acumulado = 0L
        val alvo = rng.nextLong(totalPeso)
        for ((idx, c) in candidatos.withIndex()) {
            acumulado += pesos[idx]
            if (acumulado > alvo) return c
        }
        return candidatos.last()
    }

    // ── Disputa de pênaltis ───────────────────────────────────────
    // cobraCasa / cobraFora: jogadores em ordem de cobrança (mínimo 5 por time).
    // goleiroCasaDefesa / goleiroForaDefesa: atributo `defesa` do goleiro de cada time.
    // Usa série de 5 cobranças + morte súbita se necessário.
    fun simularDisputaPenaltis(
        timeCasaId: Int,
        cobraCasa: List<Pair<Int, String>>,   // List<(jogadorId, nomeAbrev)>
        cobraFinalizacaoCasa: List<Int>,       // finalizacao de cada cobrador, em ordem
        goleiroCasaDefesa: Int,
        timeForaId: Int,
        cobraFora: List<Pair<Int, String>>,
        cobraFinalizacaoFora: List<Int>,
        goleiroForaDefesa: Int
    ): ResultadoPenaltis {
        fun conversionProb(finalizacao: Int, goleiroDefesa: Int): Double {
            val base = 0.55 + (finalizacao / 99.0) * 0.40   // 55%..95%
            val save = (goleiroDefesa / 99.0) * 0.15          // até 15% bloco
            return (base - save).coerceIn(0.40, 0.95)
        }

        val cobrancasCasa = mutableListOf<EventoPenalti>()
        val cobrancasFora = mutableListOf<EventoPenalti>()

        var golsCasa = 0
        var golsFora = 0

        // Pool circular para morte súbita
        var idx = 0
        var rodada = 0

        while (true) {
            // Casa cobra
            val idxCasa = idx % cobraCasa.size
            val finCasa = cobraFinalizacaoCasa.getOrElse(idxCasa) { 70 }
            val convCasa = conversionProb(finCasa, goleiroForaDefesa)
            val convertidoCasa = rng.nextDouble() < convCasa
            if (convertidoCasa) golsCasa++
            cobrancasCasa.add(EventoPenalti(cobraCasa[idxCasa].first, cobraCasa[idxCasa].second, convertidoCasa))

            // Fora cobra
            val idxFora = idx % cobraFora.size
            val finFora = cobraFinalizacaoFora.getOrElse(idxFora) { 70 }
            val convFora = conversionProb(finFora, goleiroCasaDefesa)
            val convertidoFora = rng.nextDouble() < convFora
            if (convertidoFora) golsFora++
            cobrancasFora.add(EventoPenalti(cobraFora[idxFora].first, cobraFora[idxFora].second, convertidoFora))

            idx++
            rodada++

            val minRodadas = 5
            if (rodada >= minRodadas) {
                // Verificar se o resultado já é decidido ou se a série normal terminou
                val cobrandasRestantes = minRodadas - rodada
                if (cobrandasRestantes <= 0) {
                    // Série de 5 finalizada
                    if (golsCasa != golsFora) break
                    // Empate → morte súbita: continua até um converter e outro não na mesma rodada
                    if (convertidoCasa != convertidoFora) break
                }
                // Eliminação antecipada: uma equipe não pode mais alcançar a outra
                val maxGolsCasaRestantes = 0
                val maxGolsForaRestantes = 0
                if (golsCasa > golsFora + maxGolsForaRestantes) break
                if (golsFora > golsCasa + maxGolsCasaRestantes) break
            }

            // Segurança: máximo 50 cobranças para evitar loop infinito (probabilidade ínfima)
            if (idx >= 50) break
        }

        // Se ainda empatado após o limite de segurança, desempate por sorteio.
        // Incrementa o gol do vencedor para garantir que o placar reflita o resultado
        // e evitar inconsistências na determinação do vencedor ao persistir na copa.
        val vencedorId = when {
            golsCasa > golsFora -> timeCasaId
            golsFora > golsCasa -> timeForaId
            else -> if (rng.nextBoolean()) { golsCasa++; timeCasaId } else { golsFora++; timeForaId }
        }

        return ResultadoPenaltis(
            cobrancasCasa = cobrancasCasa,
            cobrancasFora = cobrancasFora,
            golsCasa = golsCasa,
            golsFora = golsFora,
            vencedorId = vencedorId,
            timeCasaId = timeCasaId,
            timeForaId = timeForaId
        )
    }
}