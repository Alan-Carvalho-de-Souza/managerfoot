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

    suspend fun atualizarReputacao(timeId: Int, rep: Int) =
        timeDao.atualizarReputacao(timeId, rep.coerceIn(0, 100))

    // Mapeamento Entity -> Domain
    private fun TimeEntity.toDomain() = Time(
        id = id,
        nome = nome,
        cidade = cidade,
        estado = estado,
        nivel = nivel,
        divisao = divisao,
        saldo = saldo,
        estadioCapacidade = estadioCapacidade,
        precoIngresso = precoIngresso,
        taticaFormacao = taticaFormacao,
        estiloJogo = estiloJogo,
        reputacao = reputacao,
        controladoPorJogador = controladoPorJogador,
        escudoRes = escudoRes
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
    private val financaDao: br.com.managerfoot.data.dao.FinancaDao
) {
    fun observeElenco(timeId: Int): Flow<List<Jogador>> =
        jogadorDao.observeElenco(timeId).map { lista -> lista.map { it.toDomain() } }

    fun observeLivres(): Flow<List<Jogador>> =
        jogadorDao.observeLivres().map { lista -> lista.map { it.toDomain() } }

    suspend fun buscarDisponiveis(timeId: Int): List<Jogador> =
        jogadorDao.buscarDisponiveisPorTime(timeId).map { it.toDomain() }

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
        financaDao.inserirTransferencia(
            TransferenciaEntity(
                jogadorId = oferta.jogadorId,
                timeOrigemId = oferta.timeVendedorId,
                timeDestinoId = oferta.timeCompradorId,
                valor = oferta.valor,
                temporadaId = temporadaId,
                mes = mes,
                tipo = if (oferta.timeVendedorId == null) TipoTransferencia.FIM_CONTRATO
                       else TipoTransferencia.COMPRA
            )
        )
    }

    /**
     * Processa o desenvolvimento anual de todos os jogadores:
     * - Incrementa um ano na idade de todos
     * - Jovens (16–24): evoluem até 5% nos atributos principais e 2% nos demais,
     *   escalonado pela nota média (nota=10 → evolução máxima)
     * - Adultos (25–32): evoluem até 3% nos principais e 1% nos demais
     * - Veteranos (33+): regridem 2% em todos os atributos, independente de nota
     * - Reseta notaMedia e partidasTemporada para a nova temporada
     */
    suspend fun processarDesenvolvimentoAnual() {
        val todos = jogadorDao.buscarTodos()
        val atualizados = todos.map { j -> aplicarDesenvolvimento(j) }
        jogadorDao.atualizarTodos(atualizados)
    }

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

    private fun aplicarDesenvolvimento(j: JogadorEntity): JogadorEntity {
        val principais = atributosPrincipais(j.posicao)
        val novaIdade  = j.idade + 1

        fun evoluir(attr: Int, isPrincipal: Boolean, fatorPos: Double, fatorOther: Double): Int {
            val delta = if (isPrincipal) (attr * fatorPos).toInt() else (attr * fatorOther).toInt()
            return (attr + delta.coerceAtLeast(1)).coerceIn(1, 99)
        }
        fun regredir(attr: Int): Int {
            val delta = (attr * 0.02).toInt().coerceAtLeast(1)
            return (attr - delta).coerceAtLeast(1)
        }

        val attrs = when {
            j.idade in 16..24 -> {
                val fator = (j.notaMedia / 10.0).coerceIn(0.0, 1.0)
                listOf(
                    evoluir(j.tecnica,     "tecnica"     in principais, 0.05 * fator, 0.02 * fator),
                    evoluir(j.passe,       "passe"       in principais, 0.05 * fator, 0.02 * fator),
                    evoluir(j.velocidade,  "velocidade"  in principais, 0.05 * fator, 0.02 * fator),
                    evoluir(j.finalizacao, "finalizacao" in principais, 0.05 * fator, 0.02 * fator),
                    evoluir(j.defesa,      "defesa"      in principais, 0.05 * fator, 0.02 * fator),
                    evoluir(j.fisico,      "fisico"      in principais, 0.05 * fator, 0.02 * fator)
                )
            }
            j.idade in 25..32 -> {
                val fator = (j.notaMedia / 10.0).coerceIn(0.0, 1.0)
                listOf(
                    evoluir(j.tecnica,     "tecnica"     in principais, 0.03 * fator, 0.01 * fator),
                    evoluir(j.passe,       "passe"       in principais, 0.03 * fator, 0.01 * fator),
                    evoluir(j.velocidade,  "velocidade"  in principais, 0.03 * fator, 0.01 * fator),
                    evoluir(j.finalizacao, "finalizacao" in principais, 0.03 * fator, 0.01 * fator),
                    evoluir(j.defesa,      "defesa"      in principais, 0.03 * fator, 0.01 * fator),
                    evoluir(j.fisico,      "fisico"      in principais, 0.03 * fator, 0.01 * fator)
                )
            }
            else -> listOf(
                regredir(j.tecnica), regredir(j.passe),    regredir(j.velocidade),
                regredir(j.finalizacao), regredir(j.defesa), regredir(j.fisico)
            )
        }
        val tecnica     = attrs[0]
        val passe       = attrs[1]
        val velocidade  = attrs[2]
        val finalizacao = attrs[3]
        val defesa      = attrs[4]
        val fisico      = attrs[5]

        val novaForca = ((tecnica + passe + velocidade + finalizacao + defesa + fisico) / 6)
            .coerceIn(1, 99)

        return j.copy(
            tecnica     = tecnica,
            passe       = passe,
            velocidade  = velocidade,
            finalizacao = finalizacao,
            defesa      = defesa,
            fisico      = fisico,
            forca       = novaForca,
            idade       = novaIdade,
            notaMedia   = 6.0f,         // reset para a nova temporada
            partidasTemporada = 0
        )
    }

    /**
     * Atualiza a nota média de um jogador após cada partida (média corrida).
     * Não deve ser chamado para aposentados ou jogadores sem time.
     */
    suspend fun atualizarNotaAposPartida(jogadorId: Int, novaNota: Float) {
        val jogador = jogadorDao.buscarPorId(jogadorId) ?: return
        val qtd = jogador.partidasTemporada
        val novaMedia = if (qtd == 0) novaNota
                        else ((jogador.notaMedia * qtd.toFloat() + novaNota) / (qtd + 1).toFloat())
        jogadorDao.atualizarNota(jogadorId, novaMedia, qtd + 1)
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
        val primeiro = NOMES_JUNIOR_PRIMEIRO[rng.nextInt(NOMES_JUNIOR_PRIMEIRO.size)]
        val sobre    = NOMES_JUNIOR_SOBRENOME[rng.nextInt(NOMES_JUNIOR_SOBRENOME.size)]
        return JogadorEntity(
            timeId            = timeId,
            nome              = "$primeiro $sobre",
            nomeAbreviado     = "$primeiro ${sobre.first()}.",
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

    suspend fun atualizarEscalacao(jogadorId: Int, status: Int, posicao: Posicao? = null) {
        if (posicao != null) jogadorDao.atualizarEscalacaoComPosicao(jogadorId, status, posicao)
        else jogadorDao.atualizarEscalacaoSemPosicao(jogadorId, status)
    }

    suspend fun buscarTitularesSalvos(timeId: Int): List<Jogador> =
        jogadorDao.buscarTitularesSalvos(timeId).map { it.toDomain() }

    suspend fun buscarReservasSalvas(timeId: Int): List<Jogador> =
        jogadorDao.buscarReservasSalvas(timeId).map { it.toDomain() }

    suspend fun limparEscalacaoTime(timeId: Int) = jogadorDao.limparEscalacaoTime(timeId)

    suspend fun processarExpiracaoContratos(timeId: Int) {
        jogadorDao.decrementarContratos()
        val expirados = jogadorDao.buscarComContratoExpirado()
            .filter { it.timeId == timeId }
        expirados.forEach {
            jogadorDao.transferirJogador(it.id, null) // libera o jogador
        }
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
        aposentado = aposentado
    )
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
     * Retorna false se o saldo for insuficiente ou o setor já estiver no nível máximo.
     */
    suspend fun upgradeSetor(timeId: Int, setor: Int): Boolean {
        val estadio = buscarOuCriar(timeId)
        val nivelAtual = when (setor) {
            0 -> estadio.nivelArquibancada
            1 -> estadio.nivelCadeira
            2 -> estadio.nivelCamarote
            else -> return false
        }
        if (nivelAtual >= 10) return false

        val custoArray = when (setor) {
            0 -> EstadioEntity.CUSTO_ARQUIBANCADA
            1 -> EstadioEntity.CUSTO_CADEIRA
            2 -> EstadioEntity.CUSTO_CAMAROTE
            else -> return false
        }
        val custo = custoArray[nivelAtual]

        val saldoAtual = timeDao.buscarPorId(timeId)?.saldo ?: 0L
        if (saldoAtual < custo) return false

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

        return true
    }
}
