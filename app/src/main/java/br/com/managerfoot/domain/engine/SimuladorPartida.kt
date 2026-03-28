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

        fun mediaSetor(setor: Setor) = titulares
            .filter { it.posicaoUsada.setor == setor }
            .map { it.jogador.forcaEfetiva(it.posicaoUsada).toDouble() }
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
    fun fatorMandante(): Double = 1.20        // vantagem de jogar em casa (~20% mais força)
    fun fatorCansaco(jogosUltimos7Dias: Int): Double =
        when {
            jogosUltimos7Dias >= 3 -> 0.93
            jogosUltimos7Dias == 2 -> 0.97
            else -> 1.0
        }
    fun fatorEstilo(atacante: EstiloJogo, defensor: EstiloJogo): Double =
        // Counter-attack é mais eficaz contra times ofensivos
        when {
            atacante == EstiloJogo.CONTRA_ATAQUE && defensor == EstiloJogo.OFENSIVO -> 1.06
            atacante == EstiloJogo.OFENSIVO && defensor == EstiloJogo.DEFENSIVO     -> 0.95
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
        private const val PROB_LESAO = 0.03
    }

    fun simular(
        partidaId: Int,
        casa: Escalacao,
        fora: Escalacao,
        jogosRecentesCasa: Int = 0,
        jogosRecentesFora: Int = 0
    ): ResultadoPartida {

        val fCasaRaw = CalculadoraForca.calcularForcaTime(casa) *
                CalculadoraForca.fatorMandante() *
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
            eventos.add(inc)
            val ehCasaInc  = inc.timeId == casa.time.id
            val titsAtivos = if (ehCasaInc) titsCasa else titsFora
            val reservas   = if (ehCasaInc) resCasa  else resFora

            when (inc.tipo) {
                TipoEvento.CARTAO_VERMELHO -> {
                    // Expulsão: jogador sai sem reposição
                    titsAtivos.removeIf { it.jogador.id == inc.jogadorId }
                    if (ehCasaInc) penalCasa *= 0.87 else penalFora *= 0.87
                }
                TipoEvento.LESAO -> {
                    val lesionado = titsAtivos.find { it.jogador.id == inc.jogadorId }
                    if (lesionado != null) {
                        titsAtivos.remove(lesionado)
                        if (reservas.isNotEmpty()) {
                            // Substituição automática por lesão
                            val substituto = reservas.removeAt(0)
                            titsAtivos.add(substituto)
                            val minSub = (inc.minuto + 1).coerceAtMost(90)
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
                            // Sem reservas: fica com um jogador a menos
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

        // Média de gols esperados ajustada pelas penalizações (Poisson)
        val mediaGolsCasa = MEDIA_GOLS_JOGO * probCasa * 1.8 * penalCasa
        val mediaGolsFora = MEDIA_GOLS_JOGO * probFora * 1.8 * penalFora

        val golsCasa = poissonRandom(mediaGolsCasa)
        val golsFora = poissonRandom(mediaGolsFora)

        // Gols atribuídos aos squads que restaram após os incidentes
        val escalacaoCasaFinal = casa.copy(titulares = titsCasa)
        val escalacaoForaFinal = fora.copy(titulares = titsFora)
        eventos.addAll(gerarEventosGol(golsCasa, escalacaoCasaFinal, true))
        eventos.addAll(gerarEventosGol(golsFora, escalacaoForaFinal, false))
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
        ehCasa: Boolean
    ): List<EventoSimulado> {
        if (totalGols == 0) return emptyList()

        // Todos os não-goleiros são elegíveis; o peso de finalização já distribui corretamente
        val candidatos = escalacao.titulares
            .filter { it.posicaoUsada.setor != Setor.GOLEIRO }

        if (candidatos.isEmpty()) return emptyList()

        // Peso de gol por setor: atacantes têm 3x finalizacao, meias 2x, defensores leve chance
        fun pesoGol(jne: JogadorNaEscalacao): Int = when (jne.posicaoUsada.setor) {
            Setor.ATAQUE -> jne.jogador.finalizacao * 3 + jne.jogador.velocidade
            Setor.MEIO   -> jne.jogador.finalizacao * 2 + jne.jogador.passe
            Setor.DEFESA -> jne.jogador.finalizacao + jne.jogador.fisico / 2
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
            val minuto = rng.nextInt(1, 90)
            val eventos = mutableListOf(
                EventoSimulado(
                    minuto = minuto,
                    tipo = TipoEvento.GOL,
                    jogadorId = marcador.jogador.id,
                    timeId = escalacao.time.id,
                    descricao = "${marcador.jogador.nomeAbreviado} ${minuto}'"
                )
            )
            // ~72% chance de assistência
            if (rng.nextDouble() < 0.72) {
                val candidatosAssist = candidatos
                    .filter { it.jogador.id != marcador.jogador.id }
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
            if (rng.nextDouble() < PROB_LESAO) {
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
}