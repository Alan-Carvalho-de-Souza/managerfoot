package br.com.managerfoot.data.repository

import br.com.managerfoot.data.dao.EstadioDao
import br.com.managerfoot.data.dao.JogadorDao
import br.com.managerfoot.data.dao.TimeDao
import br.com.managerfoot.data.database.entities.*
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.domain.model.OfertaTransferencia
import br.com.managerfoot.domain.model.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────
//  TimeRepository
// ─────────────────────────────────────────────
@Singleton
class TimeRepository @Inject constructor(
    private val timeDao: TimeDao
) {
    fun observeTodos(): Flow<List<Time>> =
        timeDao.observeTodos().map { lista -> lista.map { it.toDomain() } }

    fun observeTimeDoJogador(): Flow<Time?> =
        timeDao.observePorId(-1).map { it?.toDomain() } // id resolvido abaixo

    fun observePorId(id: Int): Flow<Time?> =
        timeDao.observePorId(id).map { it?.toDomain() }

    suspend fun buscarTimeDoJogador(): Time? =
        timeDao.buscarTimeDoJogador()?.toDomain()

    suspend fun buscarPorId(id: Int): Time? =
        timeDao.buscarPorId(id)?.toDomain()

    suspend fun buscarEntityPorId(id: Int): TimeEntity? =
        timeDao.buscarPorId(id)

    suspend fun buscarTodosOrdenadosPorReputacao(): List<Time> =
        timeDao.observeTodos().first().sortedByDescending { it.reputacao }.map { it.toDomain() }

    suspend fun inserir(time: TimeEntity): Long = timeDao.inserir(time)

    suspend fun atualizar(time: TimeEntity) = timeDao.atualizar(time)

    suspend fun creditarSaldo(timeId: Int, valor: Long) =
        timeDao.creditarSaldo(timeId, valor)

    suspend fun debitarSaldo(timeId: Int, valor: Long) =
        timeDao.debitarSaldo(timeId, valor)

    suspend fun ampliarEstadio(timeId: Int, novaCapacidade: Int) =
        timeDao.ampliarEstadio(timeId, novaCapacidade)

    suspend fun atualizarReputacao(timeId: Int, rep: Float) =
        timeDao.atualizarReputacao(timeId, rep.coerceIn(0f, 100f))

    // Mapeamento Entity -> Domain
    private fun TimeEntity.toDomain() = Time(
        id = id,
        nome = nome,
        cidade = cidade,
        estado = estado,
        nivel = nivel,
        divisao = divisao,
        saldo = saldo,
        estadioNome = estadioNome,
        estadioCapacidade = estadioCapacidade,
        precoIngresso = precoIngresso,
        taticaFormacao = taticaFormacao,
        estiloJogo = estiloJogo,
        reputacao = reputacao,
        controladoPorJogador = controladoPorJogador,
        escudoRes = escudoRes,
        pais = pais
    )
}

// ─────────────────────────────────────────────
//  Dados para geração de juniores
// ─────────────────────────────────────────────
private val NOMES_JUNIOR_PRIMEIRO = listOf(
    "Gabriel", "Lucas", "Mateus", "Rafael", "Felipe",
    "Bruno", "Diego", "Thiago", "Leonardo", "Guilherme",
    "Eduardo", "Gustavo", "Henrique", "Carlos", "Pedro",
    "João", "André", "Vinícius", "Renato", "Arthur"
)
private val NOMES_JUNIOR_SOBRENOME = listOf(
    "Silva", "Santos", "Oliveira", "Souza", "Lima",
    "Pereira", "Costa", "Ferreira", "Rodrigues", "Alves",
    "Nascimento", "Carvalho", "Araújo", "Gomes", "Martins",
    "Ribeiro", "Castro", "Freitas", "Rocha", "Barbosa"
)

// ─────────────────────────────────────────────
//  JogadorRepository
// ─────────────────────────────────────────────
@Singleton
class JogadorRepository @Inject constructor(
    private val jogadorDao: JogadorDao,
    private val financaDao: br.com.managerfoot.data.dao.FinancaDao,
    private val timeDao: TimeDao,
    private val partidaDao: br.com.managerfoot.data.dao.PartidaDao
) {
    fun observeElenco(timeId: Int): Flow<List<Jogador>> =
        jogadorDao.observeElenco(timeId).map { lista -> lista.map { it.toDomain() } }

    fun observeLivres(): Flow<List<Jogador>> =
        jogadorDao.observeLivres().map { lista -> lista.map { it.toDomain() } }

    fun observeTodosJogadoresAtivos(): Flow<List<Jogador>> =
        jogadorDao.observeTodosJogadoresAtivos().map { lista -> lista.map { it.toDomain() } }

    fun observeTodasTransferencias(): Flow<List<br.com.managerfoot.data.dao.TransferenciaDetalhe>> =
        financaDao.observeTodasTransferencias()

    fun observeVendasDoTime(timeId: Int): Flow<List<br.com.managerfoot.data.dao.TransferenciaDetalhe>> =
        financaDao.observeVendasDoTime(timeId)

    fun observeComprasDoTime(timeId: Int): Flow<List<br.com.managerfoot.data.dao.TransferenciaDetalhe>> =
        financaDao.observeComprasDoTime(timeId)

    suspend fun buscarDisponiveis(timeId: Int): List<Jogador> =
        jogadorDao.buscarDisponiveisPorTime(timeId).map { it.toDomain() }

    suspend fun buscarPorId(id: Int): Jogador? =
        jogadorDao.buscarPorId(id)?.toDomain()

    suspend fun atualizarDisponibilidadeVenda(jogadorId: Int, flag: Boolean) =
        jogadorDao.atualizarDisponibilidadeVenda(jogadorId, flag)

    suspend fun atualizarDisponibilidadeEmprestimo(jogadorId: Int, flag: Boolean) =
        jogadorDao.atualizarDisponibilidadeEmprestimo(jogadorId, flag)

    suspend fun buscarListadosParaTransferencia(timeId: Int): List<Jogador> =
        jogadorDao.buscarListadosParaTransferencia(timeId).map { it.toDomain() }

    suspend fun buscarListadosPorTimeIA(playerTimeId: Int): List<Jogador> =
        jogadorDao.buscarListadosPorTimeIA(playerTimeId).map { it.toDomain() }

    /** Retorna todos os jogadores sênior do time (incluindo lesionados/suspensos) — usado para folha salarial. */
    suspend fun buscarSeniores(timeId: Int): List<Jogador> =
        jogadorDao.buscarSenioresDoTime(timeId).map { it.toDomain() }

    suspend fun buscarMercado(
        posicao: Posicao? = null,
        forcaMin: Int = 1,
        forcaMax: Int = 99,
        limite: Int = 20
    ): List<Jogador> =
        jogadorDao.buscarLivresParaTransferencia(
            posicao?.name, forcaMin, forcaMax, limite
        ).map { it.toDomain() }

    suspend fun realizarTransferencia(
        oferta: OfertaTransferencia,
        temporadaId: Int,
        mes: Int
    ) {
        // Mudar dono do jogador
        jogadorDao.transferirJogador(oferta.jogadorId, oferta.timeCompradorId)

        // Registrar transação financeira
        // COMPRA se houver clube vendedor OU se houver valor pago pelo passe;
        // FIM_CONTRATO apenas para contratações gratuitas sem clube de origem.
        financaDao.inserirTransferencia(
            TransferenciaEntity(
                jogadorId = oferta.jogadorId,
                timeOrigemId = oferta.timeVendedorId,
                timeDestinoId = oferta.timeCompradorId,
                valor = oferta.valor,
                temporadaId = temporadaId,
                mes = mes,
                tipo = if (oferta.timeVendedorId != null || oferta.valor > 0L)
                           TipoTransferencia.COMPRA
                       else
                           TipoTransferencia.FIM_CONTRATO
            )
        )
    }

    /**
     * Registra a venda de um jogador do time do usuário para um time da IA (ou
     * para o mercado livre se [oferta].timeCompradorId <= 0).
     * Diferente de [realizarTransferencia], usa [TipoTransferencia.VENDA] e
     * garante que o [timeCompradorId] inválido nunca seja gravado no banco
     * (evitando violação de FK em jogadores.timeId).
     */
    suspend fun realizarVenda(
        oferta: OfertaTransferencia,
        temporadaId: Int,
        mes: Int
    ) {
        // compradorId <= 0 significa "sem comprador" → jogador vai para o mercado livre (timeId = null)
        val novoTimeId = if (oferta.timeCompradorId > 0) oferta.timeCompradorId else null

        // Transferir jogador e limpar sua escalação
        jogadorDao.transferirJogador(oferta.jogadorId, novoTimeId)
        jogadorDao.atualizarEscalacaoSemPosicao(oferta.jogadorId, 0)

        // Registrar como VENDA para o time de origem
        financaDao.inserirTransferencia(
            TransferenciaEntity(
                jogadorId = oferta.jogadorId,
                timeOrigemId = oferta.timeVendedorId,
                timeDestinoId = novoTimeId,
                valor = oferta.valor,
                temporadaId = temporadaId,
                mes = mes,
                tipo = TipoTransferencia.VENDA
            )
        )
    }

    /**
     * Realiza o empréstimo de um jogador do time de origem para [oferta].timeCompradorId.
     * O jogador é movido para o clube tomador, mas mantém referência ao clube de origem
     * e à data de retorno (12 meses à frente).
     */
    suspend fun realizarEmprestimo(
        oferta: OfertaTransferencia,
        temporadaId: Int,
        mes: Int,
        anoRetorno: Int,
        mesRetorno: Int
    ) {
        jogadorDao.transferirJogador(oferta.jogadorId, oferta.timeCompradorId)
        jogadorDao.atualizarEscalacaoSemPosicao(oferta.jogadorId, 0)
        oferta.timeVendedorId?.let { origem ->
            jogadorDao.atualizarEmprestimo(oferta.jogadorId, origem, anoRetorno, mesRetorno)
        }
        financaDao.inserirTransferencia(
            TransferenciaEntity(
                jogadorId = oferta.jogadorId,
                timeOrigemId = oferta.timeVendedorId,
                timeDestinoId = oferta.timeCompradorId,
                valor = oferta.valor,
                temporadaId = temporadaId,
                mes = mes,
                tipo = TipoTransferencia.EMPRESTIMO_SAIDA
            )
        )
    }

    /** Observa os jogadores emprestados pelo time de origem (aparecem na aba Meu elenco). */
    fun observeEmprestadosDoTime(timeOrigemId: Int): kotlinx.coroutines.flow.Flow<List<Jogador>> =
        jogadorDao.observeEmprestadosPorOrigem(timeOrigemId).map { lista -> lista.map { it.toDomain() } }

    /**
     * Verifica se algum empréstimo expirou no mês corrente e devolve os jogadores
     * ao clube de origem, registrando um [TipoTransferencia.EMPRESTIMO_RETORNO].
     */
    suspend fun processarRetornosEmprestimo(anoAtual: Int, mesAtual: Int, temporadaId: Int) {
        val expirados = jogadorDao.buscarEmprestadosParaRetorno(anoAtual, mesAtual)
        for (j in expirados) {
            val origemId = j.timeOrigemEmprestimo ?: continue
            jogadorDao.transferirJogador(j.id, origemId)
            jogadorDao.atualizarEscalacaoSemPosicao(j.id, 0)
            jogadorDao.limparEmprestimo(j.id)
            financaDao.inserirTransferencia(
                TransferenciaEntity(
                    jogadorId = j.id,
                    timeOrigemId = j.timeId,
                    timeDestinoId = origemId,
                    valor = 0L,
                    temporadaId = temporadaId,
                    mes = mesAtual,
                    tipo = TipoTransferencia.EMPRESTIMO_RETORNO
                )
            )
        }
    }

    /**
     * Processa o fim de temporada de todos os jogadores:
     * - Incrementa um ano na idade
     * - Jogadores sem clube (timeId=null) e não aposentados recebem pequena regressão
     *   por inatividade nos atributos principais
     * - Juniores (categoriaBase=true) que jogaram na temporada: apenas reset (evolução já ocorreu
     *   incrementalmente via [atualizarNotaEEvolucao]). Juniores sem nenhuma partida recebem
     *   bônus automático de treinamento (+1 nos atributos principais).
     * - Jogadores ativos com partidas na temporada: apenas reset (evolução já ocorreu durante a temporada).
     * - Jogadores ativos SEM partidas na temporada: evolução/declínio passivo por idade:
     *     • Jovens (16–24): +1 nos atributos principais (treino, menor que jogando)
     *     • Adultos (25–32): neutro (sem estímulo, sem perda)
     *     • Veteranos (33+): -1 nos atributos principais (declínio natural, igual ao ritmo em campo)
     * - Reseta notaMedia, partidasTemporada e progressoEvolucao para a nova temporada
     *
     * A evolução/regressão dos jogadores ativos durante a temporada já ocorreu
     * incrementalmente via [atualizarNotaEEvolucao] após cada partida.
     */
    suspend fun processarDesenvolvimentoAnual() {
        val todos = jogadorDao.buscarTodos()
        val atualizados = todos.map { j ->
            val novaIdade = j.idade + 1
            when {
                j.aposentado -> j.copy(idade = novaIdade)

                // Juniores: se jogaram na temporada, evolução já ocorreu via atualizarNotaEEvolucao;
                // caso contrário, aplica bônus de treinamento (+1 nos atributos principais)
                j.categoriaBase -> {
                    if (j.partidasTemporada > 0) {
                        j.copy(
                            idade = novaIdade,
                            notaMedia = 6.0f, partidasTemporada = 0, progressoEvolucao = 0f
                        )
                    } else {
                        val principais = atributosPrincipais(j.posicao)
                        fun crescer(attr: Int, nome: String) =
                            if (nome in principais) (attr + 1).coerceIn(1, 99)
                            else attr
                        val t = crescer(j.tecnica,     "tecnica")
                        val p = crescer(j.passe,        "passe")
                        val v = crescer(j.velocidade,   "velocidade")
                        val f = crescer(j.finalizacao,  "finalizacao")
                        val d = crescer(j.defesa,       "defesa")
                        val fi= crescer(j.fisico,       "fisico")
                        val novaForca = ((t + p + v + f + d + fi) / 6).coerceIn(1, 99)
                        j.copy(
                            idade = novaIdade, forca = novaForca,
                            tecnica = t, passe = p, velocidade = v,
                            finalizacao = f, defesa = d, fisico = fi,
                            notaMedia = 6.0f, partidasTemporada = 0, progressoEvolucao = 0f
                        )
                    }
                }

                // Jogadores sem clube: regressão por inatividade
                j.timeId == null -> {
                    fun regredir(attr: Int) = (attr - 1).coerceIn(1, 99)
                    val t = regredir(j.tecnica);    val p = regredir(j.passe)
                    val v = regredir(j.velocidade); val f = regredir(j.finalizacao)
                    val d = regredir(j.defesa);     val fi= regredir(j.fisico)
                    val novaForca = ((t + p + v + f + d + fi) / 6).coerceIn(1, 99)
                    j.copy(
                        idade = novaIdade, forca = novaForca,
                        tecnica = t, passe = p, velocidade = v,
                        finalizacao = f, defesa = d, fisico = fi,
                        notaMedia = 6.0f, partidasTemporada = 0, progressoEvolucao = 0f
                    )
                }

                // Jogadores ativos com clube:
                // - Se jogaram na temporada: apenas reset (evolução já ocorreu incrementalmente).
                // - Se não jogaram: evolução/declínio passivo por faixa etária.
                //   Jovens evoluem menos que jogando (treino sem jogo); veteranos declinam igual.
                else -> {
                    if (j.partidasTemporada > 0) {
                        // Evolução já processada partida a partida — apenas reinicia contadores
                        j.copy(
                            idade = novaIdade,
                            notaMedia = 6.0f,
                            partidasTemporada = 0,
                            progressoEvolucao = 0f
                        )
                    } else {
                        val principais = atributosPrincipais(j.posicao)
                        when {
                            // Jovem sem jogar: +1 nos atributos principais (treino passivo)
                            j.idade in 16..24 -> {
                                fun crescer(attr: Int, nome: String) =
                                    if (nome in principais) (attr + 1).coerceIn(1, 99) else attr
                                val t  = crescer(j.tecnica,     "tecnica")
                                val p  = crescer(j.passe,        "passe")
                                val v  = crescer(j.velocidade,   "velocidade")
                                val f  = crescer(j.finalizacao,  "finalizacao")
                                val d  = crescer(j.defesa,       "defesa")
                                val fi = crescer(j.fisico,       "fisico")
                                val novaForca = ((t + p + v + f + d + fi) / 6).coerceIn(1, 99)
                                j.copy(
                                    idade = novaIdade, forca = novaForca,
                                    tecnica = t, passe = p, velocidade = v,
                                    finalizacao = f, defesa = d, fisico = fi,
                                    notaMedia = 6.0f, partidasTemporada = 0, progressoEvolucao = 0f
                                )
                            }
                            // Adulto sem jogar: neutro — sem evolução nem declínio
                            j.idade in 25..32 -> j.copy(
                                idade = novaIdade,
                                notaMedia = 6.0f, partidasTemporada = 0, progressoEvolucao = 0f
                            )
                            // Veterano sem jogar: -1 nos atributos principais (declínio natural)
                            else -> {
                                fun regredir(attr: Int, nome: String) =
                                    if (nome in principais) (attr - 1).coerceIn(1, 99) else attr
                                val t  = regredir(j.tecnica,     "tecnica")
                                val p  = regredir(j.passe,        "passe")
                                val v  = regredir(j.velocidade,   "velocidade")
                                val f  = regredir(j.finalizacao,  "finalizacao")
                                val d  = regredir(j.defesa,       "defesa")
                                val fi = regredir(j.fisico,       "fisico")
                                val novaForca = ((t + p + v + f + d + fi) / 6).coerceIn(1, 99)
                                j.copy(
                                    idade = novaIdade, forca = novaForca,
                                    tecnica = t, passe = p, velocidade = v,
                                    finalizacao = f, defesa = d, fisico = fi,
                                    notaMedia = 6.0f, partidasTemporada = 0, progressoEvolucao = 0f
                                )
                            }
                        }
                    }
                }
            }
        }
        // Recalcula o valor de mercado de cada jogador com base na força e idade atualizadas.
        // Feito em passe separado para usar os atributos já computados acima.
        val comValoresMercado = atualizados.map { j ->
            if (j.aposentado) j
            else j.copy(valorMercado = calcularValorMercado(j.forca, j.idade))
        }
        jogadorDao.atualizarTodos(comValoresMercado)
    }

    /**
     * Calcula o valor de mercado de um jogador em centavos com base em força e idade.
     *
     * A base é calculada por interpolação linear por faixa de força (piecewise),
     * garantindo os seguintes valores de referência no pico etário (fator 1.0):
     *   F60 → R$1,4M | F70 → R$5,4M | F80 → R$10,6M | F90 → R$35,2M | F99 → R$80M
     *
     * O fator etário é multiplicado sobre a base:
     *  - até 29 anos: crescente (valorização progressiva)
     *  - 30–32 anos:  estável no pico (fator 1.00)
     *  - 33+ anos:    decrescente conforme o declínio
     */
    private fun calcularValorMercado(forca: Int, idade: Int): Long {
        // Interpolação linear entre os breakpoints de força (valores em centavos no pico)
        val baseCentavos: Long = when {
            forca >= 90 -> lerpLong(3_520_000_000L, 8_000_000_000L, (forca - 90).toFloat() / 9f)
            forca >= 80 -> lerpLong(1_060_000_000L, 3_520_000_000L, (forca - 80).toFloat() / 10f)
            forca >= 70 -> lerpLong(  540_000_000L, 1_060_000_000L, (forca - 70).toFloat() / 10f)
            forca >= 60 -> lerpLong(  140_000_000L,   540_000_000L, (forca - 60).toFloat() / 10f)
            forca >= 50 -> lerpLong(   30_000_000L,   140_000_000L, (forca - 50).toFloat() / 10f)
            else        -> lerpLong(    5_000_000L,    30_000_000L,  (forca -  1).toFloat() / 49f)
        }

        val fatorIdade = when {
            idade <= 16 -> 0.50f
            idade <= 17 -> 0.56f
            idade <= 18 -> 0.62f
            idade <= 19 -> 0.68f
            idade <= 20 -> 0.74f
            idade <= 21 -> 0.80f
            idade <= 22 -> 0.85f
            idade <= 23 -> 0.90f
            idade <= 24 -> 0.93f
            idade <= 25 -> 0.96f
            idade <= 26 -> 0.98f
            idade <= 32 -> 1.00f  // pico de mercado — estabilizado 26-32
            idade <= 33 -> 0.90f
            idade <= 34 -> 0.80f
            idade <= 35 -> 0.68f
            idade <= 36 -> 0.56f
            idade <= 37 -> 0.46f
            else        -> 0.36f
        }

        return (baseCentavos * fatorIdade).toLong().coerceAtLeast(5_000_000L)
    }

    private fun lerpLong(from: Long, to: Long, t: Float): Long =
        (from + (to - from) * t.coerceIn(0f, 1f)).toLong()

    private fun atributosPrincipais(posicao: Posicao): Set<String> = when (posicao) {
        Posicao.GOLEIRO            -> setOf("defesa", "fisico", "tecnica")
        Posicao.ZAGUEIRO           -> setOf("defesa", "fisico", "tecnica")
        Posicao.LATERAL_DIREITO,
        Posicao.LATERAL_ESQUERDO   -> setOf("defesa", "velocidade", "passe")
        Posicao.VOLANTE            -> setOf("defesa", "fisico", "passe")
        Posicao.MEIA_CENTRAL       -> setOf("passe", "tecnica", "fisico")
        Posicao.MEIA_ATACANTE      -> setOf("tecnica", "passe", "velocidade")
        Posicao.PONTA_DIREITA,
        Posicao.PONTA_ESQUERDA     -> setOf("velocidade", "finalizacao", "tecnica")
        Posicao.CENTROAVANTE       -> setOf("finalizacao", "fisico", "tecnica")
        Posicao.SEGUNDA_ATACANTE   -> setOf("finalizacao", "tecnica", "velocidade")
    }

    /**
     * Atualiza nota média e aplica evolução/regressão incremental após cada partida.
     *
     * O acumulador [progressoEvolucao] cresce ou diminui a cada partida conforme
     * a nota do jogador e sua faixa etária:
     * - Juniores (categoriaBase): maior taxa de desenvolvimento, refletindo o potencial de crescimento
     *   acelerado em jovens talentos. Evoluem e regridem apenas nos atributos principais da posição.
     * - Jovens (16–24): nota alta → acumula positivo → ao cruzar +1.0, atributos
     *   principais ganham +1; nota baixa → acumula negativo → atributos principais perdem -1.
     * - Adultos (25–32): mesma lógica, porém com taxas menores.
     * - Veteranos (33+): sempre acumulam negativo (declínio progressivo independente
     *   da nota, mas boas atuações desaceleram a queda).
     *
     * Tanto evolução quanto regressão afetam apenas os atributos principais da posição,
     * garantindo simetria: o jogador cresce e decai na sua especialidade.
     *
     * Ao final de uma temporada completa (~38 partidas):
     * - Junior com nota média ≈ 10 → +6 a +7 pts nos atributos principais → +3 a +4 força
     * - Jovem com nota média ≈ 10 → +4 a +5 pts nos atributos principais → +2 a +3 força
     * - Adulto com nota média ≈ 10 → +2 a +3 pts nos atributos principais → +1 a +2 força
     * - Veterano com nota média ≈ 6  → -1 a -2 força (declínio natural)
     *
     * Aplica-se igualmente a jogadores de times controlados pela IA.
     */
    suspend fun atualizarNotaEEvolucao(jogadorId: Int, notaPartida: Float) {
        val j = jogadorDao.buscarPorId(jogadorId) ?: return
        if (j.aposentado) return

        // Atualiza nota média (média corrida)
        val qtd = j.partidasTemporada
        val novaMedia = if (qtd == 0) notaPartida
                        else ((j.notaMedia * qtd.toFloat() + notaPartida) / (qtd + 1).toFloat())

        // Delta por partida: baseado na nota individual desta partida
        // notaFactor in [-1, +1]: 10 → +1.0 | 5.5 → 0.0 | 1 → -1.0
        val notaFactor = ((notaPartida - 5.5f) / 4.5f).coerceIn(-1.0f, 1.0f)
        val deltaPartida: Float = when {
            j.categoriaBase   -> notaFactor * 0.18f  // juniores: maior taxa de desenvolvimento
            j.idade in 16..24 -> notaFactor * 0.13f
            j.idade in 25..32 -> notaFactor * 0.08f
            // Veterano: declina sempre; nota muito boa desacelera a queda
            else -> -(0.055f - notaFactor * 0.025f)
        }

        var prog = j.progressoEvolucao + deltaPartida
        var tecnica     = j.tecnica
        var passe       = j.passe
        var velocidade  = j.velocidade
        var finalizacao = j.finalizacao
        var defesa      = j.defesa
        var fisico      = j.fisico
        val principais  = atributosPrincipais(j.posicao)

        // Evolução: incrementa apenas os atributos principais da posição
        while (prog >= 1.0f) {
            if ("tecnica"     in principais) tecnica     = (tecnica     + 1).coerceIn(1, 99)
            if ("passe"       in principais) passe       = (passe       + 1).coerceIn(1, 99)
            if ("velocidade"  in principais) velocidade  = (velocidade  + 1).coerceIn(1, 99)
            if ("finalizacao" in principais) finalizacao = (finalizacao + 1).coerceIn(1, 99)
            if ("defesa"      in principais) defesa      = (defesa      + 1).coerceIn(1, 99)
            if ("fisico"      in principais) fisico      = (fisico      + 1).coerceIn(1, 99)
            prog -= 1.0f
        }

        // Regressão: decrementa apenas os atributos principais (simétrico à evolução)
        while (prog <= -1.0f) {
            if ("tecnica"     in principais) tecnica     = (tecnica     - 1).coerceIn(1, 99)
            if ("passe"       in principais) passe       = (passe       - 1).coerceIn(1, 99)
            if ("velocidade"  in principais) velocidade  = (velocidade  - 1).coerceIn(1, 99)
            if ("finalizacao" in principais) finalizacao = (finalizacao - 1).coerceIn(1, 99)
            if ("defesa"      in principais) defesa      = (defesa      - 1).coerceIn(1, 99)
            if ("fisico"      in principais) fisico      = (fisico      - 1).coerceIn(1, 99)
            prog += 1.0f
        }

        val novaForca = ((tecnica + passe + velocidade + finalizacao + defesa + fisico) / 6)
            .coerceIn(1, 99)

        jogadorDao.atualizar(j.copy(
            notaMedia         = novaMedia,
            partidasTemporada = qtd + 1,
            progressoEvolucao = prog,
            tecnica           = tecnica,
            passe             = passe,
            velocidade        = velocidade,
            finalizacao       = finalizacao,
            defesa            = defesa,
            fisico            = fisico,
            forca             = novaForca
        ))
    }

    /** Aposenta o jogador e gera automaticamente um jogador na base de juniores do mesmo clube. */
    suspend fun aposentarJogador(jogadorId: Int) {
        val entity = jogadorDao.buscarPorId(jogadorId) ?: return
        val timeId = entity.timeId
        jogadorDao.aposentarJogador(jogadorId)
        if (timeId != null) {
            val junior = gerarJuniorDeAposentado(entity, timeId)
            jogadorDao.inserir(junior)
        }
    }

    private fun gerarJuniorDeAposentado(aposentado: JogadorEntity, timeId: Int): JogadorEntity {
        val rng = kotlin.random.Random.Default
        val principais = atributosPrincipais(aposentado.posicao)
        val baseForca = rng.nextInt(65, 81)
        fun attr(nome: String): Int =
            if (nome in principais) (baseForca + rng.nextInt(5, 16)).coerceIn(1, 99)
            else (baseForca - rng.nextInt(5, 16)).coerceIn(1, 99)
        val tecnica     = attr("tecnica")
        val passe       = attr("passe")
        val velocidade  = attr("velocidade")
        val finalizacao = attr("finalizacao")
        val defesa      = attr("defesa")
        val fisico      = attr("fisico")
        val forca = ((tecnica + passe + velocidade + finalizacao + defesa + fisico) / 6).coerceIn(1, 99)
        val idade = rng.nextInt(16, 20)
        return JogadorEntity(
            timeId            = timeId,
            nome              = aposentado.nome,          // herda o nome do jogador aposentado
            nomeAbreviado     = aposentado.nomeAbreviado, // herda o nome abreviado
            idade             = idade,
            posicao           = aposentado.posicao,
            posicaoSecundaria = aposentado.posicaoSecundaria,
            forca             = forca,
            tecnica           = tecnica,
            passe             = passe,
            velocidade        = velocidade,
            finalizacao       = finalizacao,
            defesa            = defesa,
            fisico            = fisico,
            salario           = 200_000L,
            contratoAnos      = 3,
            valorMercado      = forca.toLong() * 500_000L,
            categoriaBase     = true,
            notaMedia         = 6.0f,
            partidasTemporada = 0,
            aposentado        = false
        )
    }

    fun observeJuniores(timeId: Int): Flow<List<Jogador>> =
        jogadorDao.observeJuniores(timeId).map { lista -> lista.map { it.toDomain() } }

    suspend fun promoverJunior(jogadorId: Int) = jogadorDao.promoverJunior(jogadorId)

    /**
     * Dispensa um jogador da base de juniores, enviando-o para mercado livre.
     * Registra a movimentação no histórico de transferências.
     */
    suspend fun dispensarJunior(jogadorId: Int, temporadaId: Int, mes: Int) {
        val entity = jogadorDao.buscarPorId(jogadorId) ?: return
        val timeOrigemId = entity.timeId
        jogadorDao.dispensarJuniorDb(jogadorId)
        financaDao.inserirTransferencia(
            TransferenciaEntity(
                jogadorId    = jogadorId,
                timeOrigemId = timeOrigemId,
                timeDestinoId = null,
                valor        = 0L,
                temporadaId  = temporadaId,
                mes          = mes,
                tipo         = TipoTransferencia.DISPENSADO_BASE
            )
        )
    }

    /** Retorna o total de jogadores (sênior + base) de um time — para verificar limite de 35. */
    suspend fun contarJogadores(timeId: Int): Int = jogadorDao.contarJogadoresPorTime(timeId)

    suspend fun atualizarEscalacao(jogadorId: Int, status: Int, posicao: Posicao? = null) {
        if (posicao != null) jogadorDao.atualizarEscalacaoComPosicao(jogadorId, status, posicao)
        else jogadorDao.atualizarEscalacaoSemPosicao(jogadorId, status)
    }

    /**
     * Atualiza a fadiga dos jogadores sênior de ambos os times após uma partida.
     * Jogadores que participaram perdem fadiga conforme a idade:
     *   - ≤ 25 anos: -4% · 26–30 anos: -6% · 31+: -8%
     * Jogadores que não participaram recuperam +10%.
     * Aplica-se a todos os times envolvidos (times do jogador e da IA).
     */
    suspend fun atualizarFadigaAposPartida(participantes: Set<Int>, timeIds: Set<Int>) {
        for (timeId in timeIds) {
            val seniores = jogadorDao.buscarSenioresDoTime(timeId)
            val atualizados = seniores.map { j ->
                val delta = if (j.id in participantes) {
                    when {
                        j.idade <= 25 -> -0.04f
                        j.idade <= 30 -> -0.06f
                        else          -> -0.08f
                    }
                } else {
                    +0.10f
                }
                // Decrementa contador de ausência por lesão (players lesionados de jogos anteriores)
                val novoPartidas = if (j.lesionado && j.partidasSemJogar > 0) j.partidasSemJogar - 1 else j.partidasSemJogar
                val novoLesionado = j.lesionado && novoPartidas > 0
                j.copy(
                    fadiga = (j.fadiga + delta).coerceIn(0f, 1f),
                    treinouNestaCiclo = false,  // libera novo treinamento para o próximo ciclo
                    partidasSemJogar = novoPartidas,
                    lesionado = novoLesionado
                )
            }
            jogadorDao.atualizarTodos(atualizados)

            // Também libera o treinamento dos juniores para o próximo ciclo
            val juniores = jogadorDao.buscarJunioresDoTime(timeId)
            if (juniores.isNotEmpty()) {
                jogadorDao.atualizarTodos(juniores.map { it.copy(treinouNestaCiclo = false) })
            }
        }
    }

    /** Aplica lesão programada ao jogador: marca como lesionado por [partidas] jogos. */
    suspend fun aplicarLesao(jogadorId: Int, partidas: Int) {
        jogadorDao.aplicarLesao(jogadorId, partidas)
    }

    /**
     * Executa uma sessão de treinamento para o jogador.
     * - Reduz fadiga em 5%
     * - Adiciona progresso de evolução nos atributos principais:
     *     • Juniores (categoriaBase): +0.25 (desenvolvimento acelerado)
     *     • Jovens (16–24): +0.10 · Adultos (25–32): +0.06 · Veteranos (33+): +0.02
     * - Se o acumulador cruzar +1.0, os atributos principais ganham +1.
     */
    suspend fun treinarJogador(jogadorId: Int) {
        val j = jogadorDao.buscarPorId(jogadorId) ?: return
        if (j.aposentado) return
        if (j.treinouNestaCiclo) return  // já treinou neste ciclo (entre dois jogos)

        val novaFadiga = (j.fadiga - 0.05f).coerceIn(0f, 1f)

        val deltaProgress: Float = when {
            j.categoriaBase   -> 0.25f
            j.idade in 16..24 -> 0.10f
            j.idade in 25..32 -> 0.06f
            else              -> 0.02f
        }

        val principais = atributosPrincipais(j.posicao)
        var prog = j.progressoEvolucao + deltaProgress
        var tecnica     = j.tecnica
        var passe       = j.passe
        var velocidade  = j.velocidade
        var finalizacao = j.finalizacao
        var defesa      = j.defesa
        var fisico      = j.fisico

        while (prog >= 1.0f) {
            if ("tecnica"     in principais) tecnica     = (tecnica     + 1).coerceIn(1, 99)
            if ("passe"       in principais) passe       = (passe       + 1).coerceIn(1, 99)
            if ("velocidade"  in principais) velocidade  = (velocidade  + 1).coerceIn(1, 99)
            if ("finalizacao" in principais) finalizacao = (finalizacao + 1).coerceIn(1, 99)
            if ("defesa"      in principais) defesa      = (defesa      + 1).coerceIn(1, 99)
            if ("fisico"      in principais) fisico      = (fisico      + 1).coerceIn(1, 99)
            prog -= 1.0f
        }

        val novaForca = ((tecnica + passe + velocidade + finalizacao + defesa + fisico) / 6).coerceIn(1, 99)

        jogadorDao.atualizar(j.copy(
            fadiga            = novaFadiga,
            progressoEvolucao = prog,
            tecnica = tecnica, passe = passe, velocidade = velocidade,
            finalizacao = finalizacao, defesa = defesa, fisico = fisico,
            forca = novaForca,
            treinouNestaCiclo = true
        ))
    }

    /** Treina todos os jogadores do elenco que ainda não treinaram neste ciclo. */
    suspend fun treinarTimeCompleto(timeId: Int) {
        val elenco = jogadorDao.buscarElencoCompletoDoTime(timeId)
        elenco.filter { !it.aposentado && !it.treinouNestaCiclo }
              .forEach { treinarJogador(it.id) }
    }

    /**
     * Aplica treino ou descanso automático para todos os jogadores de um time da IA.
     * - Fadiga < 65%  → descansa (+10% fadiga, sem progressoEvolucao)
     * - Fadiga ≥ 65%  → treina (-5% fadiga, acumula progressoEvolucao;
     *                   ao cruzar 1.0: TODOS os seis atributos ganham +1)
     * Respeita treinouNestaCiclo para não processar o mesmo jogador duas vezes por ciclo.
     */
    suspend fun treinarOuDescansarTimeIA(timeId: Int) {
        val elenco = jogadorDao.buscarElencoCompletoDoTime(timeId)
        val atualizados = elenco.mapNotNull { j ->
            if (j.aposentado || j.treinouNestaCiclo) return@mapNotNull null
            if (j.fadiga < 0.65f) {
                // Descanso: recupera fadiga sem acumular progresso
                j.copy(fadiga = (j.fadiga + 0.10f).coerceIn(0f, 1f), treinouNestaCiclo = true)
            } else {
                // Treino: reduz fadiga e acumula progressoEvolucao
                val deltaProgress: Float = when {
                    j.categoriaBase   -> 0.25f
                    j.idade in 16..24 -> 0.10f
                    j.idade in 25..32 -> 0.06f
                    else              -> 0.02f
                }
                var prog        = j.progressoEvolucao + deltaProgress
                var tecnica     = j.tecnica
                var passe       = j.passe
                var velocidade  = j.velocidade
                var finalizacao = j.finalizacao
                var defesa      = j.defesa
                var fisico      = j.fisico
                // Ao cruzar 1.0: todos os atributos ganham +1 (não só os principais)
                while (prog >= 1.0f) {
                    tecnica     = (tecnica     + 1).coerceIn(1, 99)
                    passe       = (passe       + 1).coerceIn(1, 99)
                    velocidade  = (velocidade  + 1).coerceIn(1, 99)
                    finalizacao = (finalizacao + 1).coerceIn(1, 99)
                    defesa      = (defesa      + 1).coerceIn(1, 99)
                    fisico      = (fisico      + 1).coerceIn(1, 99)
                    prog -= 1.0f
                }
                val novaForca = ((tecnica + passe + velocidade + finalizacao + defesa + fisico) / 6).coerceIn(1, 99)
                j.copy(
                    fadiga            = (j.fadiga - 0.05f).coerceIn(0f, 1f),
                    progressoEvolucao = prog,
                    tecnica = tecnica, passe = passe, velocidade = velocidade,
                    finalizacao = finalizacao, defesa = defesa, fisico = fisico,
                    forca = novaForca,
                    treinouNestaCiclo = true
                )
            }
        }
        if (atualizados.isNotEmpty()) jogadorDao.atualizarTodos(atualizados)
    }

    /**
     * Coloca um jogador para descansar no ciclo atual (ao invés de treinar):
     * - Recupera +10% de fadiga (até máx 100%)
     * - Marca treinouNestaCiclo = true (consome a ação do ciclo)
     * - Não acumula progresso de evolução
     */
    suspend fun descansarJogador(jogadorId: Int) {
        val j = jogadorDao.buscarPorId(jogadorId) ?: return
        if (j.aposentado) return
        if (j.treinouNestaCiclo) return
        val novaFadiga = (j.fadiga + 0.10f).coerceIn(0f, 1f)
        jogadorDao.atualizar(j.copy(fadiga = novaFadiga, treinouNestaCiclo = true))
    }

    suspend fun buscarElencoCompleto(timeId: Int): List<Jogador> =
        jogadorDao.buscarElencoCompletoDoTime(timeId).map { it.toDomain() }

    suspend fun buscarTitularesSalvos(timeId: Int): List<Jogador> =
        jogadorDao.buscarTitularesSalvos(timeId).map { it.toDomain() }

    suspend fun buscarReservasSalvas(timeId: Int): List<Jogador> =
        jogadorDao.buscarReservasSalvas(timeId).map { it.toDomain() }

    suspend fun limparEscalacaoTime(timeId: Int) = jogadorDao.limparEscalacaoTime(timeId)

    /**
     * Decrementa contratos de todos os jogadores sênior com clube ao final da temporada.
     * Jogadores de times da IA com contrato expirado (contratoAnos = 0) são liberados
     * automaticamente para o mercado livre.
     * Jogadores do time do usuário com contrato expirado permanecem no elenco —
     * o usuário decide via UI se renova ou dispensa.
     */
    suspend fun processarExpiracaoContratos(timeJogadorId: Int) {
        jogadorDao.decrementarContratos()
        val expirados = jogadorDao.buscarComContratoExpirado()
        expirados
            .filter { it.timeId != null && it.timeId != timeJogadorId }
            .forEach { jogadorDao.transferirJogador(it.id, null) }
    }

    /** Renova o contrato de um jogador com novo salário e duração. */
    suspend fun renovarContrato(jogadorId: Int, novoSalario: Long, novosAnos: Int) {
        val j = jogadorDao.buscarPorId(jogadorId) ?: return
        jogadorDao.atualizar(j.copy(salario = novoSalario, contratoAnos = novosAnos))
    }

    /** Dispensa o jogador para o mercado livre sem transferência financeira. */
    suspend fun dispensarParaMercado(jogadorId: Int) {
        jogadorDao.transferirJogador(jogadorId, null)
    }

    // ─────────────────────────────────────────────
    //  Histórico de carreira (derivado, sem nova entity)
    // ─────────────────────────────────────────────
    /**
     * Reconstrói o histórico de carreira de um jogador derivando de
     * [TransferenciaEntity] + eventos de partida persistidos.
     *
     * - Cruza transferências (períodos) com eventos de gol/assistência/participação
     * - Agrupa por (timeId, ano) e mescla anos consecutivos no mesmo clube
     * - Não cria nenhuma nova tabela; é tudo derivado em runtime
     */
    suspend fun buscarHistoricoCarreira(jogadorId: Int): HistoricoCarreira {
        val jogador = jogadorDao.buscarPorId(jogadorId)?.toDomain()
            ?: return HistoricoCarreira(emptyList(), 0, 0, 0, 0, null, null, 0f)

        val transfers = try {
            financaDao.buscarTransferenciasDoJogador(jogadorId)
        } catch (e: Exception) { emptyList() }
        val eventos = try {
            partidaDao.buscarEventosCarreiraJogador(jogadorId)
        } catch (e: Exception) { emptyList() }

        // Mapa de timeId → entity para nome/escudo
        val timeIdsRelevantes = buildSet<Int> {
            transfers.forEach {
                it.timeOrigemId?.let { id -> add(id) }
                it.timeDestinoId?.let { id -> add(id) }
            }
            eventos.forEach { add(it.timeCasaId); add(it.timeForaId) }
            jogador.timeId?.let { add(it) }
        }
        val timesPorId: Map<Int, br.com.managerfoot.data.database.entities.TimeEntity> =
            timeIdsRelevantes
                .mapNotNull { id -> timeDao.buscarPorId(id)?.let { id to it } }
                .toMap()

        // ── Construção dos "moves" cronológicos
        data class Move(val temporadaId: Int, val mes: Int, val novoTime: Int?)
        val moves: List<Move> = transfers
            .sortedWith(compareBy({ it.temporadaId }, { it.mes }))
            .map { t ->
                val novoTime: Int? = when (t.tipo) {
                    TipoTransferencia.COMPRA,
                    TipoTransferencia.EMPRESTIMO_SAIDA,
                    TipoTransferencia.PROMOVIDO_BASE,
                    TipoTransferencia.EMPRESTIMO_RETORNO -> t.timeDestinoId
                    TipoTransferencia.VENDA,
                    TipoTransferencia.FIM_CONTRATO,
                    TipoTransferencia.DISPENSADO_BASE -> null
                }
                Move(t.temporadaId, t.mes, novoTime)
            }

        // Time do jogador num momento (temporadaId, ordemGlobal aproximado para mês)
        fun timeNoMomento(temporadaId: Int, ordemGlobal: Int): Int? {
            val mesAprox = ordemGlobalParaMesCarreira(ordemGlobal)
            var atual: Int? = null
            for (m in moves) {
                val moveBeforeOrAt = m.temporadaId < temporadaId ||
                    (m.temporadaId == temporadaId && m.mes <= mesAprox)
                if (moveBeforeOrAt) atual = m.novoTime else break
            }
            return atual
        }

        // ── Agrupa eventos por (timeId, ano)
        val porTimeAno = mutableMapOf<Pair<Int, Int>, MutableList<br.com.managerfoot.data.dao.EventoCarreiraDto>>()
        for (ev in eventos) {
            val derivado = timeNoMomento(ev.temporadaId, ev.ordemGlobal)
            // Validação: time derivado deve ser um dos dois da partida
            val timeId: Int? = when {
                derivado != null && (derivado == ev.timeCasaId || derivado == ev.timeForaId) -> derivado
                jogador.timeId == ev.timeCasaId || jogador.timeId == ev.timeForaId -> jogador.timeId
                else -> ev.timeCasaId  // último fallback (raro)
            }
            if (timeId != null) {
                porTimeAno.getOrPut(timeId to ev.ano) { mutableListOf() }.add(ev)
            }
        }

        // ── Constrói passagens-ano e mescla anos consecutivos no mesmo time
        val passagensAno = porTimeAno.entries
            .map { (key, evs) ->
                Triple(
                    key,
                    evs.map { it.partidaId }.toSet().size,
                    evs.count { it.tipo == "GOL" } to evs.count { it.tipo == "ASSISTENCIA" }
                )
            }
            .sortedWith(compareBy({ it.first.first }, { it.first.second }))

        val passagens = mutableListOf<PassagemClube>()
        var atual: PassagemClube? = null
        for ((key, partidas, golAss) in passagensAno) {
            val (timeId, ano) = key
            val (gols, assists) = golAss
            val cur = atual
            if (cur != null && cur.timeId == timeId && ano <= cur.anoFim + 1) {
                atual = cur.copy(
                    anoFim = ano,
                    partidas = cur.partidas + partidas,
                    gols = cur.gols + gols,
                    assistencias = cur.assistencias + assists
                )
            } else {
                cur?.let { passagens.add(it) }
                val time = timesPorId[timeId]
                atual = PassagemClube(
                    timeId = timeId,
                    timeNome = time?.nome ?: "Clube #$timeId",
                    escudoRes = time?.escudoRes ?: "",
                    anoInicio = ano,
                    anoFim = ano,
                    partidas = partidas,
                    gols = gols,
                    assistencias = assists
                )
            }
        }
        atual?.let { passagens.add(it) }

        // Marca a passagem atual com nota média da temporada
        val passagensComNota: List<PassagemClube> = if (passagens.isNotEmpty() &&
            passagens.last().timeId == jogador.timeId
        ) {
            passagens.dropLast(1) + passagens.last().copy(notaMedia = jogador.notaMedia)
        } else {
            passagens.toList()
        }

        // Fallback: se não há nenhuma passagem mas o jogador tem time atual,
        // cria uma "passagem atual" vazia para pelo menos mostrar o clube atual
        // (saves antigos podem não ter eventos persistidos para os jogadores)
        val passagensFinais: List<PassagemClube> = if (passagensComNota.isEmpty() && jogador.timeId != null) {
            val time = timesPorId[jogador.timeId] ?: timeDao.buscarPorId(jogador.timeId)
            if (time != null) {
                val anoAtual = eventos.mapNotNull { it.ano.takeIf { y -> y > 0 } }.maxOrNull() ?: 0
                listOf(
                    PassagemClube(
                        timeId = jogador.timeId,
                        timeNome = time.nome,
                        escudoRes = time.escudoRes,
                        anoInicio = if (anoAtual > 0) anoAtual else 0,
                        anoFim = if (anoAtual > 0) anoAtual else 0,
                        partidas = jogador.partidasTemporada,
                        gols = 0,
                        assistencias = 0,
                        notaMedia = jogador.notaMedia
                    )
                )
            } else emptyList()
        } else passagensComNota

        return HistoricoCarreira(
            passagens = passagensFinais.sortedBy { it.anoInicio },
            totalPartidas = passagensFinais.sumOf { it.partidas },
            totalGols = passagensFinais.sumOf { it.gols },
            totalAssistencias = passagensFinais.sumOf { it.assistencias },
            clubesDiferentes = passagensFinais.map { it.timeId }.distinct().size,
            anoEstreia = passagensFinais.minOfOrNull { it.anoInicio }?.takeIf { it > 0 },
            anoUltimo = passagensFinais.maxOfOrNull { it.anoFim }?.takeIf { it > 0 },
            notaMediaAtual = jogador.notaMedia
        )
    }

    // Mapeamento Entity -> Domain
    private fun JogadorEntity.toDomain() = Jogador(
        id = id,
        timeId = timeId,
        nome = nome,
        nomeAbreviado = nomeAbreviado,
        idade = idade,
        posicao = posicao,
        posicaoSecundaria = posicaoSecundaria,
        forca = forca,
        tecnica = tecnica,
        passe = passe,
        velocidade = velocidade,
        finalizacao = finalizacao,
        defesa = defesa,
        fisico = fisico,
        salario = salario,
        contratoAnos = contratoAnos,
        valorMercado = valorMercado,
        lesionado = lesionado,
        suspenso = suspensoCicloAmarelos,
        moraleEstado = moraleEstado,
        escalarStatus = escalarStatus,
        posicaoEscalado = posicaoEscalado,
        notaMedia = notaMedia,
        partidasTemporada = partidasTemporada,
        aposentado = aposentado,
        progressoEvolucao = progressoEvolucao,
        categoriaBase = categoriaBase,
        fadiga = fadiga,
        treinouNestaCiclo = treinouNestaCiclo,
        partidasSemJogar = partidasSemJogar,
        disponívelParaVenda = disponívelParaVenda,
        disponívelParaEmprestimo = disponívelParaEmprestimo,
        timeOrigemEmprestimo = timeOrigemEmprestimo,
        anoRetornoEmprestimo = anoRetornoEmprestimo,
        mesRetornoEmprestimo = mesRetornoEmprestimo
    )
}

// ─────────────────────────────────────────────────
//  Histórico de carreira de um jogador (DTOs públicos)
// ─────────────────────────────────────────────────
data class PassagemClube(
    val timeId: Int,
    val timeNome: String,
    val escudoRes: String,
    val anoInicio: Int,
    val anoFim: Int,
    val partidas: Int,
    val gols: Int,
    val assistencias: Int,
    val notaMedia: Float? = null  // nota da temporada atual (apenas última passagem)
)

data class HistoricoCarreira(
    val passagens: List<PassagemClube>,
    val totalPartidas: Int,
    val totalGols: Int,
    val totalAssistencias: Int,
    val clubesDiferentes: Int,
    val anoEstreia: Int?,
    val anoUltimo: Int?,
    val notaMediaAtual: Float
)

/**
 * Converte ordemGlobal (1..580) em mês aproximado (1..12).
 * Consistente com CalendarioScreen: temporada vai de fev (dia 39) a nov (dia 334).
 */
internal fun ordemGlobalParaMesCarreira(ordemGlobal: Int): Int {
    if (ordemGlobal == 1) return 1   // Supercopa
    if (ordemGlobal == 620) return 12 // Troféu dos Campeões (mid-Dezembro)
    val diaDoAno = (39 + (ordemGlobal - 10) * 295 / 380).coerceIn(1, 365)
    val diasMes = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var rem = diaDoAno
    for (m in 1..12) {
        if (rem <= diasMes[m]) return m
        rem -= diasMes[m]
    }
    return 12
}

// ─────────────────────────────────────────────────
//  EstadioRepository
// ─────────────────────────────────────────────────
@Singleton
class EstadioRepository @Inject constructor(
    private val estadioDao: EstadioDao,
    private val timeDao: TimeDao
) {
    /** Retorna o EstadioEntity do time, criando um registro inicial se necessário. */
    suspend fun buscarOuCriar(timeId: Int): EstadioEntity {
        estadioDao.buscarPorTime(timeId)?.let { return it }
        val capacidade = timeDao.buscarPorId(timeId)?.estadioCapacidade ?: 0
        val novo = EstadioEntity.inicializar(timeId, capacidade)
        estadioDao.salvar(novo)
        return novo
    }

    /**
     * Faz o upgrade de um setor (0=arquibancada, 1=cadeira, 2=camarote).
     * Debita o custo do saldo do time e atualiza estadioCapacidade no TimeEntity.
     * Retorna o custo do upgrade em caso de sucesso, ou null se o saldo for insuficiente
     * ou o setor já estiver no nível máximo.
     */
    suspend fun upgradeSetor(timeId: Int, setor: Int): Long? {
        val estadio = buscarOuCriar(timeId)
        val nivelAtual = when (setor) {
            0 -> estadio.nivelArquibancada
            1 -> estadio.nivelCadeira
            2 -> estadio.nivelCamarote
            else -> return null
        }
        if (nivelAtual >= 10) return null

        val custoArray = when (setor) {
            0 -> EstadioEntity.CUSTO_ARQUIBANCADA
            1 -> EstadioEntity.CUSTO_CADEIRA
            2 -> EstadioEntity.CUSTO_CAMAROTE
            else -> return null
        }
        val custo = custoArray[nivelAtual]

        val saldoAtual = timeDao.buscarPorId(timeId)?.saldo ?: 0L
        if (saldoAtual < custo) return null

        // Debita custo
        timeDao.debitarSaldo(timeId, custo)

        // Atualiza nível
        val atualizado = when (setor) {
            0 -> estadio.copy(nivelArquibancada = nivelAtual + 1)
            1 -> estadio.copy(nivelCadeira      = nivelAtual + 1)
            2 -> estadio.copy(nivelCamarote     = nivelAtual + 1)
            else -> estadio
        }
        estadioDao.salvar(atualizado)

        // Sincroniza estadioCapacidade no TimeEntity
        timeDao.ampliarEstadio(timeId, atualizado.capacidadeTotal)

        return custo
    }
}
