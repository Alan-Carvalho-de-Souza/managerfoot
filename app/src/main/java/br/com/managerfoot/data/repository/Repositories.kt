package br.com.managerfoot.data.repository

import br.com.managerfoot.data.dao.EstadioDao
import br.com.managerfoot.data.dao.JogadorDao
import br.com.managerfoot.data.dao.TimeDao
import br.com.managerfoot.data.database.entities.*
import br.com.managerfoot.domain.engine.CalculadoraMercado
import br.com.managerfoot.domain.model.Jogador
import br.com.managerfoot.domain.model.LimitesElenco
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
    private val partidaDao: br.com.managerfoot.data.dao.PartidaDao,
    private val campeonatoDao: br.com.managerfoot.data.dao.CampeonatoDao
) {

    /**
     * O carimbo de data de uma transferência: (ano, ordemGlobal).
     *
     * `ordemGlobal` é o relógio da temporada e dá o DIA exato da negociação pela
     * mesma conversão que data as partidas; o ano vem do nome de um campeonato
     * da temporada, que é onde ele mora. Nenhuma das duas falhas é motivo para
     * perder a transferência: sem carimbo o histórico cai para mês/temporada,
     * como era antes.
     */
    private suspend fun carimboDeData(temporadaId: Int): Pair<Int, Int> {
        val ordemGlobal = try { partidaDao.ordemGlobalAtual(temporadaId) } catch (_: Exception) { 0 }
        val ano = try {
            br.com.managerfoot.domain.engine.CalendarioDoJogo.anoDoNomeDoCampeonato(
                campeonatoDao.nomeDeUmCampeonatoDaTemporada(temporadaId) ?: ""
            )
        } catch (_: Exception) { 0 }
        return ano to ordemGlobal
    }
    companion object {
        /**
         * Janela de transferências monotônica: 2 por temporada
         * (início: meses 1–6 → índice 0; final: meses 7–12 → índice 1).
         * Usada para liberar a revenda de um jogador pela IA só na PRÓXIMA janela.
         */
        fun janelaTransferencia(temporadaId: Int, mes: Int): Int =
            temporadaId * 2 + if (mes <= 6) 0 else 1
    }

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
        val (ano, ordemGlobal) = carimboDeData(temporadaId)
        // Mudar dono do jogador (carimbando a janela → anti-revenda IA na mesma janela)
        jogadorDao.transferirJogadorComJanela(
            oferta.jogadorId, oferta.timeCompradorId, janelaTransferencia(temporadaId, mes)
        )
        // Salário renegociado no novo clube: pelo menos o salário-alvo de mercado
        // (corrige salários de seed defasados) ou o proposto, o que for maior.
        aplicarSalarioContratacao(oferta.jogadorId, oferta.salarioProposto)

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
                           TipoTransferencia.FIM_CONTRATO,
                ano = ano,
                ordemGlobal = ordemGlobal
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
        val (ano, ordemGlobal) = carimboDeData(temporadaId)
        // compradorId <= 0 significa "sem comprador" → jogador vai para o mercado livre (timeId = null)
        val novoTimeId = if (oferta.timeCompradorId > 0) oferta.timeCompradorId else null

        // Transferir jogador e limpar sua escalação (carimba a janela quando há comprador)
        if (novoTimeId != null) {
            jogadorDao.transferirJogadorComJanela(oferta.jogadorId, novoTimeId, janelaTransferencia(temporadaId, mes))
            // Novo clube renegocia o salário (mercado). Jogador solto (mercado livre) mantém o salário.
            aplicarSalarioContratacao(oferta.jogadorId, oferta.salarioProposto)
        } else {
            jogadorDao.transferirJogador(oferta.jogadorId, null)
        }
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
                tipo = TipoTransferencia.VENDA,
                ano = ano,
                ordemGlobal = ordemGlobal
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
        val (ano, ordemGlobal) = carimboDeData(temporadaId)
        jogadorDao.transferirJogadorComJanela(oferta.jogadorId, oferta.timeCompradorId, janelaTransferencia(temporadaId, mes))
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
                tipo = TipoTransferencia.EMPRESTIMO_SAIDA,
                ano = ano,
                ordemGlobal = ordemGlobal
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
        val (ano, ordemGlobal) = carimboDeData(temporadaId)
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
                    tipo = TipoTransferencia.EMPRESTIMO_RETORNO,
                    ano = ano,
                    ordemGlobal = ordemGlobal
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
    suspend fun processarDesenvolvimentoAnual(timeJogadorId: Int = -1) {
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
                        val tetoPot = if (j.potencial in 1..99) j.potencial else 99
                        val podeCrescer = j.forca < tetoPot
                        fun crescer(attr: Int, nome: String) =
                            if (nome in principais && podeCrescer) (attr + 1).coerceIn(1, 99)
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
                        val rngLocal = kotlin.random.Random.Default
                        val tetoPot = if (j.potencial in 1..99) j.potencial else 99
                        val podeCrescer = j.forca < tetoPot
                        when {
                            // Jovem sem jogar: evolução MUITO reduzida (25% de chance de +1
                            // em cada atributo principal). Antes era +1 fixo em todos, o que
                            // igualava a evolução de quem jogou 30+ partidas na temporada e
                            // desincentivava dar minutos aos jovens do próprio elenco.
                            j.idade in 16..24 -> {
                                fun crescerAleatorio(attr: Int, nome: String) =
                                    if (nome in principais && podeCrescer && rngLocal.nextFloat() < 0.25f)
                                        (attr + 1).coerceIn(1, 99)
                                    else attr
                                val t  = crescerAleatorio(j.tecnica,     "tecnica")
                                val p  = crescerAleatorio(j.passe,        "passe")
                                val v  = crescerAleatorio(j.velocidade,   "velocidade")
                                val f  = crescerAleatorio(j.finalizacao,  "finalizacao")
                                val d  = crescerAleatorio(j.defesa,       "defesa")
                                val fi = crescerAleatorio(j.fisico,       "fisico")
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
        // Reset de cartões/suspensão ao virar a temporada:
        //  - cartões amarelos do ciclo, estatísticas cumulativas e suspensões pendentes zeram.
        //  - aposentados também zeram (não competem mais).
        val comResetCartoes = atualizados.map { j ->
            j.copy(
                cartoesAmarelosCiclo = 0,
                cartoesAmarelosTemporada = 0,
                cartoesVermelhosTemporada = 0,
                partidasSuspensao = 0
            )
        }
        // Aposentadoria automática — evita jogadores decaídos ficarem para sempre no elenco.
        // Regras aplicadas sobre a NOVA idade (já incrementada acima):
        //  - 40+                          → compulsória
        //  - 38+ com força ≤ 65           → declínio evidente
        //  - 35+ com força ≤ 55           → carreira encerra
        // Categoriabase e já aposentados não passam pelo filtro.
        val comAposentadoria = comResetCartoes.map { j ->
            val elegivel = !j.aposentado && !j.categoriaBase
            val deveAposentar = elegivel && when {
                j.idade >= 40                    -> true
                j.idade >= 38 && j.forca <= 65   -> true
                j.idade >= 35 && j.forca <= 55   -> true
                else                             -> false
            }
            if (deveAposentar) j.copy(aposentado = true, timeId = null) else j
        }
        // Para cada aposentado deste ano que tinha clube, gera um junior de reposição
        // (mesmo tratamento do fluxo manual aposentarJogador).
        val juniores = comResetCartoes.zip(comAposentadoria).mapNotNull { (antes, depois) ->
            if (!antes.aposentado && depois.aposentado && antes.timeId != null)
                // Times da IA: o junior de reposição herda o nome+posição do
                // aposentado. Time do usuário: nome novo (ele gerencia a base).
                gerarJuniorDeAposentado(antes, antes.timeId, manterNome = antes.timeId != timeJogadorId)
            else null
        }
        // Recalcula o valor de mercado de cada jogador com base na força, idade,
        // contrato e posição atualizados. Feito em passe separado para usar os
        // atributos já computados acima. (No fluxo completo de virada, o contrato
        // é decrementado logo depois em processarExpiracaoContratos, que revaloriza
        // todos novamente — aqui cobre o caminho processarFimDeTemporada.)
        val comValoresMercado = comAposentadoria.map { j ->
            if (j.aposentado) j
            else {
                val novoValor = CalculadoraMercado.valorDeMercado(j.forca, j.idade, j.contratoAnos, j.posicao, j.potencial)
                // Salário pegajoso: quem tem clube e evoluiu recebe aumento PARCIAL
                // (metade do caminho até o alvo); nunca cai sozinho (corte só na renegociação).
                val alvoSal = CalculadoraMercado.salarioAlvo(j.forca, j.idade, j.posicao)
                val novoSal = if (j.timeId != null && alvoSal > j.salario)
                                  j.salario + (alvoSal - j.salario) / 2
                              else j.salario
                j.copy(valorMercado = novoValor, salario = novoSal)
            }
        }
        jogadorDao.atualizarTodos(comValoresMercado)
        if (juniores.isNotEmpty()) jogadorDao.inserirTodos(juniores)
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
        // Teto de evolução: a força não cresce acima do potencial (0 = sem teto).
        val tetoPot = if (j.potencial in 1..99) j.potencial else 99

        // Evolução: incrementa os atributos principais da posição — apenas enquanto
        // a força atual estiver abaixo do teto de potencial.
        while (prog >= 1.0f && ((tecnica + passe + velocidade + finalizacao + defesa + fisico) / 6) < tetoPot) {
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

        // Valor de mercado acompanha a força em tempo real (não fica defasado até a virada).
        val novoValor = CalculadoraMercado.valorDeMercado(novaForca, j.idade, j.contratoAnos, j.posicao, j.potencial)

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
            forca             = novaForca,
            valorMercado      = novoValor
        ))
    }

    /**
     * Aposenta o jogador e gera automaticamente um jogador na base de juniores do
     * mesmo clube. O usuário escolhe a posição em que a "cria" entra ([posicaoJunior],
     * null = mesma do aposentado) e o nome dela: [manterNome] herda o nome do ídolo,
     * [nomeJunior] define um nome digitado, e nenhum dos dois gera um nome aleatório.
     */
    suspend fun aposentarJogador(
        jogadorId: Int,
        posicaoJunior: Posicao? = null,
        manterNome: Boolean = false,
        nomeJunior: String? = null
    ) {
        val entity = jogadorDao.buscarPorId(jogadorId) ?: return
        val timeId = entity.timeId
        jogadorDao.aposentarJogador(jogadorId)
        if (timeId != null) {
            val junior = gerarJuniorDeAposentado(
                aposentado = entity,
                timeId     = timeId,
                manterNome = manterNome,
                posicao    = posicaoJunior,
                nomeCustom = nomeJunior
            )
            jogadorDao.inserir(junior)
        }
    }

    private fun gerarJuniorDeAposentado(
        aposentado: JogadorEntity,
        timeId: Int,
        manterNome: Boolean = false,
        posicao: Posicao? = null,
        nomeCustom: String? = null
    ): JogadorEntity {
        val rng = kotlin.random.Random.Default
        val posJunior = posicao ?: aposentado.posicao
        val principais = atributosPrincipais(posJunior)
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
        val potencial = (forca + rng.nextInt(5, 16)).coerceAtMost(99)
        // nomeCustom (aposentadoria manual com nome digitado) tem prioridade.
        // manterNome=true (times da IA, ou escolha do usuário): o junior HERDA o
        // nome do aposentado — a "cria" que leva o nome do ídolo. Sem nenhum dos
        // dois (dispensa manual): nome novo por nacionalidade.
        val custom = nomeCustom?.trim()?.takeIf { it.isNotEmpty() }
        val (nomeGerado, nomeAbrevGerado) = when {
            custom != null -> custom to abreviarNome(custom)
            manterNome     -> aposentado.nome to aposentado.nomeAbreviado
            else           -> gerarNomeAleatorio(aposentado.nacionalidade)
        }
        return JogadorEntity(
            timeId            = timeId,
            nome              = nomeGerado,
            nomeAbreviado     = nomeAbrevGerado,
            nacionalidade     = aposentado.nacionalidade,
            idade             = idade,
            posicao           = posJunior,
            posicaoSecundaria = aposentado.posicaoSecundaria.takeIf { it != posJunior },
            forca             = forca,
            tecnica           = tecnica,
            passe             = passe,
            velocidade        = velocidade,
            finalizacao       = finalizacao,
            defesa            = defesa,
            fisico            = fisico,
            potencial         = potencial,
            salario           = CalculadoraMercado.salarioAlvo(forca, idade, posJunior),
            contratoAnos      = 3,
            valorMercado      = CalculadoraMercado.valorDeMercado(forca, idade, 3, posJunior, potencial),
            categoriaBase     = true,
            notaMedia         = 6.0f,
            partidasTemporada = 0,
            aposentado        = false
        )
    }

    /**
     * Piso de elenco SÊNIOR para os times da IA — evita a depleção de elencos ao
     * longo das temporadas. No rollover, jogadores de IA com contrato expirado são
     * liberados ([processarExpiracaoContratos]) sem reposição, então os elencos
     * encolhiam ano a ano. Aqui, para cada time da IA abaixo do piso:
     *   1) promove os próprios juniores (melhores primeiro) ao elenco sênior;
     *   2) se ainda faltar, gera reforços jovens escalados ao nível do clube.
     * O time do usuário NÃO é tocado (ele gerencia o próprio elenco e a base).
     * Roda depois de [processarDesenvolvimentoAnual] e [processarExpiracaoContratos].
     */
    suspend fun reporElencosIA(timeJogadorId: Int) {
        val pisoSenior = 18
        val novos = mutableListOf<JogadorEntity>()
        for (t in timeDao.buscarTodos()) {
            if (t.id == timeJogadorId) continue
            val seniores = jogadorDao.buscarSenioresDoTime(t.id)
            var faltam = pisoSenior - seniores.size
            if (faltam <= 0) continue
            // 1) promove juniores do próprio clube (melhores primeiro)
            val juns = jogadorDao.buscarJunioresDoTime(t.id).sortedByDescending { it.forca }
            for (jr in juns) {
                if (faltam <= 0) break
                jogadorDao.promoverJunior(jr.id)
                faltam--
            }
            // 2) gera reforços jovens cobrindo as posições mais escassas
            if (faltam > 0) {
                val posCount = seniores.groupingBy { it.posicao }.eachCount().toMutableMap()
                val nac = seniores.groupingBy { it.nacionalidade }.eachCount()
                    .maxByOrNull { it.value }?.key ?: "Brasil"
                repeat(faltam) {
                    val pos = posicaoMaisEscassa(posCount)
                    posCount[pos] = (posCount[pos] ?: 0) + 1
                    novos.add(gerarReforcoJovem(t.id, pos, nac, t.reputacao))
                }
            }
        }
        if (novos.isNotEmpty()) jogadorDao.inserirTodos(novos)
    }

    // Formação-alvo por posição usada para escolher o reforço mais necessário.
    private val alvoElenco = mapOf(
        Posicao.GOLEIRO to 3, Posicao.ZAGUEIRO to 5,
        Posicao.LATERAL_DIREITO to 2, Posicao.LATERAL_ESQUERDO to 2,
        Posicao.VOLANTE to 3, Posicao.MEIA_CENTRAL to 3, Posicao.MEIA_ATACANTE to 2,
        Posicao.PONTA_DIREITA to 2, Posicao.PONTA_ESQUERDA to 2,
        Posicao.CENTROAVANTE to 3, Posicao.SEGUNDA_ATACANTE to 1
    )

    /** Posição com o maior déficit em relação à formação-alvo. */
    private fun posicaoMaisEscassa(atual: Map<Posicao, Int>): Posicao =
        alvoElenco.maxByOrNull { (pos, alvo) -> alvo - (atual[pos] ?: 0) }!!.key

    /** Reforço jovem (sênior, escalado ao nível do clube) para o piso de elenco da IA. */
    private fun gerarReforcoJovem(
        timeId: Int, posicao: Posicao, nacionalidade: String, reputacaoTime: Float
    ): JogadorEntity {
        val rng = kotlin.random.Random.Default
        val principais = atributosPrincipais(posicao)
        // Clube fraco → jovem fraco; clube forte → jovem melhor (mas ainda modesto).
        val centro = (reputacaoTime * 0.85f).toInt().coerceIn(50, 74)
        val baseForca = (centro + rng.nextInt(-4, 5)).coerceIn(48, 78)
        fun attr(nome: String): Int =
            if (nome in principais) (baseForca + rng.nextInt(4, 12)).coerceIn(1, 99)
            else (baseForca - rng.nextInt(4, 12)).coerceIn(1, 99)
        val tecnica     = attr("tecnica")
        val passe       = attr("passe")
        val velocidade  = attr("velocidade")
        val finalizacao = attr("finalizacao")
        val defesa      = attr("defesa")
        val fisico      = attr("fisico")
        val forca = ((tecnica + passe + velocidade + finalizacao + defesa + fisico) / 6).coerceIn(1, 99)
        val idade = rng.nextInt(17, 22)
        val contrato = rng.nextInt(2, 5)
        val potencial = (forca + rng.nextInt(3, 12)).coerceAtMost(99)
        val (nome, nomeAbrev) = gerarNomeAleatorio(nacionalidade)
        return JogadorEntity(
            timeId            = timeId,
            nome              = nome,
            nomeAbreviado     = nomeAbrev,
            nacionalidade     = nacionalidade,
            idade             = idade,
            posicao           = posicao,
            posicaoSecundaria = null,
            forca             = forca,
            tecnica           = tecnica,
            passe             = passe,
            velocidade        = velocidade,
            finalizacao       = finalizacao,
            defesa            = defesa,
            fisico            = fisico,
            potencial         = potencial,
            salario           = CalculadoraMercado.salarioAlvo(forca, idade, posicao),
            contratoAnos      = contrato,
            valorMercado      = CalculadoraMercado.valorDeMercado(forca, idade, contrato, posicao, potencial),
            categoriaBase     = false,
            notaMedia         = 6.0f,
            partidasTemporada = 0,
            aposentado        = false
        )
    }

    /**
     * Forma [quantidade] novos jogadores na base de juniores do clube, gastando
     * [custo] (centavos). Debita o saldo do clube, gera os juniores nas posições
     * mais escassas do elenco e registra a despesa nas finanças (campo despesaBase).
     * Retorna false se o clube não existir ou o saldo for insuficiente.
     */
    suspend fun formarJuniores(
        timeId: Int,
        temporadaId: Int,
        mes: Int,
        quantidade: Int = 2,
        custo: Long = 1_000_000_00L
    ): Boolean {
        val time = timeDao.buscarPorId(timeId) ?: return false
        if (time.saldo < custo) return false

        val seniores = jogadorDao.buscarSenioresDoTime(timeId)
        val juniores = jogadorDao.buscarJunioresDoTime(timeId)
        // Teto da base. Antes não havia nenhum: a base só competia pelo limite
        // somado de 35, então um clube podia formar juniores indefinidamente desde
        // que o elenco profissional estivesse enxuto. Forma só o que couber.
        val vagas = LimitesElenco.vagasNaBase(juniores.size)
        if (vagas <= 0) return false
        val aFormar = quantidade.coerceAtMost(vagas)
        val posCount = (seniores + juniores).groupingBy { it.posicao }.eachCount().toMutableMap()
        val nac = (seniores + juniores).groupingBy { it.nacionalidade }.eachCount()
            .maxByOrNull { it.value }?.key ?: "Brasileiro"

        val novos = (0 until aFormar).map {
            val pos = posicaoMaisEscassa(posCount)
            posCount[pos] = (posCount[pos] ?: 0) + 1
            gerarJuniorFormado(timeId, pos, nac)
        }

        timeDao.debitarSaldo(timeId, custo)
        jogadorDao.inserirTodos(novos)

        val saldoAtual = timeDao.buscarPorId(timeId)?.saldo ?: 0L
        financaDao.inserir(
            FinancaEntity(
                timeId      = timeId,
                temporadaId = temporadaId,
                mes         = mes,
                despesaBase = custo,
                saldoFinal  = saldoAtual
            )
        )
        return true
    }

    /** Jovem recém-formado na base (categoriaBase=true), com atributos de promessa. */
    private fun gerarJuniorFormado(
        timeId: Int, posicao: Posicao, nacionalidade: String
    ): JogadorEntity {
        val rng = kotlin.random.Random.Default
        val principais = atributosPrincipais(posicao)
        val baseForca = rng.nextInt(62, 78)
        fun attr(nome: String): Int =
            if (nome in principais) (baseForca + rng.nextInt(4, 14)).coerceIn(1, 99)
            else (baseForca - rng.nextInt(4, 14)).coerceIn(1, 99)
        val tecnica     = attr("tecnica")
        val passe       = attr("passe")
        val velocidade  = attr("velocidade")
        val finalizacao = attr("finalizacao")
        val defesa      = attr("defesa")
        val fisico      = attr("fisico")
        val forca = ((tecnica + passe + velocidade + finalizacao + defesa + fisico) / 6).coerceIn(1, 99)
        val idade = rng.nextInt(16, 19)
        val potencial = (forca + rng.nextInt(6, 18)).coerceAtMost(99)
        val (nome, nomeAbrev) = gerarNomeAleatorio(nacionalidade)
        return JogadorEntity(
            timeId            = timeId,
            nome              = nome,
            nomeAbreviado     = nomeAbrev,
            nacionalidade     = nacionalidade,
            idade             = idade,
            posicao           = posicao,
            posicaoSecundaria = null,
            forca             = forca,
            tecnica           = tecnica,
            passe             = passe,
            velocidade        = velocidade,
            finalizacao       = finalizacao,
            defesa            = defesa,
            fisico            = fisico,
            potencial         = potencial,
            salario           = CalculadoraMercado.salarioAlvo(forca, idade, posicao),
            contratoAnos      = 3,
            valorMercado      = CalculadoraMercado.valorDeMercado(forca, idade, 3, posicao, potencial),
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
        val (ano, ordemGlobal) = carimboDeData(temporadaId)
        jogadorDao.dispensarJuniorDb(jogadorId)
        financaDao.inserirTransferencia(
            TransferenciaEntity(
                jogadorId    = jogadorId,
                timeOrigemId = timeOrigemId,
                timeDestinoId = null,
                valor        = 0L,
                temporadaId  = temporadaId,
                mes          = mes,
                tipo         = TipoTransferencia.DISPENSADO_BASE,
                ano          = ano,
                ordemGlobal  = ordemGlobal
            )
        )
    }

    /**
     * Total de jogadores (sênior + base). NÃO é a conta de nenhum limite: os
     * limites são separados, ver [contarSeniores] e [contarBase]. Segue aqui
     * porque o piso anti-depleção do rollover olha o elenco inteiro.
     */
    suspend fun contarJogadores(timeId: Int): Int = jogadorDao.contarJogadoresPorTime(timeId)

    /** Profissionais do clube — a conta do limite do elenco (LimitesElenco.MAX_SENIORES). */
    suspend fun contarSeniores(timeId: Int): Int = jogadorDao.contarSenioresPorTime(timeId)

    /** Jogadores da base — a conta do limite da base (LimitesElenco.MAX_BASE). */
    suspend fun contarBase(timeId: Int): Int = jogadorDao.contarBasePorTime(timeId)

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
                    lesionado = novoLesionado,
                    grauLesao = if (novoLesionado) j.grauLesao else 0  // zera o grau ao recuperar
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

    /** Aplica lesão ao jogador: marca como lesionado por [partidas] jogos, com [grau] (1/2/3). */
    suspend fun aplicarLesao(jogadorId: Int, partidas: Int, grau: Int) {
        jogadorDao.aplicarLesao(jogadorId, partidas, grau)
    }

    /**
     * Processa os cartões emitidos em uma partida:
     *  - Cartão vermelho → 1 partida de suspensão (não sobrescreve suspensão maior já pendente).
     *  - 3 cartões amarelos acumulados no ciclo → 1 partida de suspensão + reset dos amarelos.
     *  - Acumula também estatísticas da temporada (amarelos/vermelhos).
     *
     * E, em seguida, decrementa em 1 o [partidasSuspensao] dos jogadores dos times envolvidos
     * que NÃO participaram desta partida (cumprem a suspensão ficando de fora).
     */
    suspend fun processarCartoesESuspensoes(
        amarelosPorJogador: Map<Int, Int>,
        vermelhosPorJogador: Map<Int, Int>,
        participantes: Set<Int>,
        timeIds: Set<Int>
    ) {
        // 1) Aplica cartões da partida atual em cada jogador afetado
        val afetadosIds = (amarelosPorJogador.keys + vermelhosPorJogador.keys).filter { it > 0 }
        val atualizadosCartao = afetadosIds.mapNotNull { id ->
            val j = jogadorDao.buscarPorId(id) ?: return@mapNotNull null
            val amarelos = amarelosPorJogador[id] ?: 0
            val vermelhos = vermelhosPorJogador[id] ?: 0

            var novoCicloAmarelos = j.cartoesAmarelosCiclo + amarelos
            var novaSuspensao = j.partidasSuspensao

            // 3 amarelos no ciclo → 1 partida + zera contador
            if (novoCicloAmarelos >= 3) {
                novaSuspensao = maxOf(novaSuspensao, 1)
                novoCicloAmarelos = 0
            }
            // Vermelho → 1 partida (independente dos amarelos)
            if (vermelhos > 0) {
                novaSuspensao = maxOf(novaSuspensao, 1)
            }

            j.copy(
                cartoesAmarelosCiclo = novoCicloAmarelos,
                cartoesAmarelosTemporada = j.cartoesAmarelosTemporada + amarelos,
                cartoesVermelhosTemporada = j.cartoesVermelhosTemporada + vermelhos,
                partidasSuspensao = novaSuspensao
            )
        }
        if (atualizadosCartao.isNotEmpty()) {
            jogadorDao.atualizarTodos(atualizadosCartao)
        }

        // 2) Decrementa suspensão dos jogadores ausentes desta partida
        //    (cumprem 1 partida ficando de fora). Não afeta quem entrou em campo,
        //    inclusive quem foi expulso agora (já teve partidasSuspensao setado).
        for (timeId in timeIds) {
            val seniores = jogadorDao.buscarSenioresDoTime(timeId)
            val decrementados = seniores.mapNotNull { j ->
                if (j.partidasSuspensao <= 0) return@mapNotNull null
                if (j.id in participantes) return@mapNotNull null
                // Recém-suspenso nesta mesma partida? Não decrementar (a suspensão vale para a PRÓXIMA).
                if (j.id in afetadosIds) return@mapNotNull null
                j.copy(partidasSuspensao = (j.partidasSuspensao - 1).coerceAtLeast(0))
            }
            if (decrementados.isNotEmpty()) {
                jogadorDao.atualizarTodos(decrementados)
            }
        }
    }

    /**
     * Atualiza o [MoraleEstado] de todos os jogadores do elenco de um time com base
     * nos resultados das últimas 5 partidas em qualquer competição.
     * Delega a regra V/D → morale para [MotorFinanceiro.calcularMorale].
     */
    suspend fun atualizarMoraleTime(timeId: Int) {
        val ultimas = partidaDao.buscarUltimasPartidasDoTime(timeId, limite = 5)
        if (ultimas.isEmpty()) return
        var vitorias = 0
        var derrotas = 0
        ultimas.forEach { p ->
            val golsCasa = p.golsCasa ?: return@forEach
            val golsFora = p.golsFora ?: return@forEach
            val ehCasa = p.timeCasaId == timeId
            val golsPro = if (ehCasa) golsCasa else golsFora
            val golsContra = if (ehCasa) golsFora else golsCasa
            when {
                golsPro > golsContra -> vitorias++
                golsPro < golsContra -> derrotas++
            }
        }
        val novaMorale = br.com.managerfoot.domain.engine.MotorFinanceiro.calcularMorale(vitorias, derrotas)
        val seniores = jogadorDao.buscarSenioresDoTime(timeId)
        val atualizados = seniores
            .filter { it.moraleEstado != novaMorale }
            .map { it.copy(moraleEstado = novaMorale) }
        if (atualizados.isNotEmpty()) jogadorDao.atualizarTodos(atualizados)
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
     *                   ao cruzar 1.0: apenas os atributos PRINCIPAIS da posição ganham +1,
     *                   mesma regra aplicada ao jogador humano em treinarJogador).
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
                val principais = atributosPrincipais(j.posicao)
                var prog        = j.progressoEvolucao + deltaProgress
                var tecnica     = j.tecnica
                var passe       = j.passe
                var velocidade  = j.velocidade
                var finalizacao = j.finalizacao
                var defesa      = j.defesa
                var fisico      = j.fisico
                // Ao cruzar 1.0: apenas os atributos principais da posição ganham +1
                // (paridade com treinarJogador — evita a IA evoluir 2× mais rápido que o jogador).
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
     * Jogadores de times da IA com contrato expirado (contratoAnos = 0) são
     * RENOVADOS automaticamente (evita depleção de elenco) e a renovação gera
     * uma VALORIZAÇÃO no valor de mercado por faixa de força geral:
     *   força ≥ 90 → +25% (teto) · ≥ 84 → +20% · ≥ 76 → +15% · demais → +10%.
     * Jogadores do time do usuário com contrato expirado permanecem no elenco —
     * o usuário decide via UI se renova ou dispensa.
     */
    suspend fun processarExpiracaoContratos(timeJogadorId: Int) {
        jogadorDao.decrementarContratos()
        val rng = kotlin.random.Random.Default
        // Passe único pós-decremento: revaloriza TODOS pelo contrato já atualizado.
        //  - IA com contrato expirado (0): renova (2–4 anos) e aplica o bônus de valorização.
        //  - Demais (usuário, livres, base): apenas revaloriza pelo contrato corrente.
        val atualizados = jogadorDao.buscarTodos().mapNotNull { j ->
            when {
                j.aposentado -> null
                j.contratoAnos == 0 && j.timeId != null && j.timeId != timeJogadorId -> {
                    val novoContrato = rng.nextInt(2, 5)
                    val pct = fatorValorizacaoRenovacao(j.forca)
                    val base = CalculadoraMercado.valorDeMercado(j.forca, j.idade, novoContrato, j.posicao, j.potencial)
                    // Renovação reajusta o salário ao mercado (nunca corta).
                    val novoSal = maxOf(j.salario, CalculadoraMercado.salarioAlvo(j.forca, j.idade, j.posicao))
                    j.copy(
                        contratoAnos = novoContrato,
                        valorMercado = (base * (1.0 + pct)).toLong(),
                        salario = novoSal
                    )
                }
                else -> {
                    val novo = CalculadoraMercado.valorDeMercado(j.forca, j.idade, j.contratoAnos, j.posicao, j.potencial)
                    if (novo != j.valorMercado) j.copy(valorMercado = novo) else null
                }
            }
        }
        if (atualizados.isNotEmpty()) jogadorDao.atualizarTodos(atualizados)
    }

    /** Faixa de valorização de mercado aplicada na renovação de contrato (IA), por força geral. */
    private fun fatorValorizacaoRenovacao(forca: Int): Double = when {
        forca >= 90 -> 0.25   // teto
        forca >= 84 -> 0.20
        forca >= 76 -> 0.15
        else        -> 0.10
    }

    /** Renova o contrato de um jogador com novo salário e duração. */
    suspend fun renovarContrato(jogadorId: Int, novoSalario: Long, novosAnos: Int) {
        val j = jogadorDao.buscarPorId(jogadorId) ?: return
        jogadorDao.atualizar(j.copy(salario = novoSalario, contratoAnos = novosAnos))
    }

    /**
     * Aplica o salário do jogador ao assinar por um novo clube: o maior entre o
     * salário-alvo de mercado (força/idade/posição) e o proposto na oferta. Isso
     * corrige salários de seed defasados e mata o antigo "salarioProposto" ignorado.
     */
    private suspend fun aplicarSalarioContratacao(jogadorId: Int, salarioProposto: Long) {
        val j = jogadorDao.buscarPorId(jogadorId) ?: return
        val alvo = CalculadoraMercado.salarioAlvo(j.forca, j.idade, j.posicao)
        val novo = maxOf(alvo, salarioProposto)
        if (novo != j.salario) jogadorDao.atualizar(j.copy(salario = novo))
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
        data class Move(
            val temporadaId: Int,
            val mes: Int,
            val novoTime: Int?,
            val data: br.com.managerfoot.domain.engine.DataDoJogo
        )
        val moves: List<Move> = transfers
            .sortedWith(compareBy({ it.temporadaId }, { it.ordemGlobal }, { it.id }))
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
                val data = if (t.ordemGlobal > 0)
                    br.com.managerfoot.domain.engine.CalendarioDoJogo
                        .ordemGlobalParaData(t.ordemGlobal, t.ano)
                else
                    br.com.managerfoot.domain.engine.DataDoJogo(t.ano, t.mes, 0)
                Move(t.temporadaId, t.mes, novoTime, data)
            }

        // ── Períodos de contrato, do encadeamento das transferências ─────
        // Cada move abre um período no clube de destino e fecha o anterior. É
        // isto que responde "quando ele chegou e quando saiu", que as partidas
        // não respondem: entre assinar e estrear, e entre o último jogo e a
        // saída, ele seguia no clube.
        data class PeriodoNoClube(
            val timeId: Int,
            val anoInicio: Int,
            val chegada: br.com.managerfoot.domain.engine.DataDoJogo,
            val saida: br.com.managerfoot.domain.engine.DataDoJogo?
        )
        val periodos = moves.mapIndexedNotNull { idx, move ->
            val timeId = move.novoTime ?: return@mapIndexedNotNull null
            PeriodoNoClube(
                timeId = timeId,
                anoInicio = move.data.ano,
                chegada = move.data,
                saida = moves.getOrNull(idx + 1)?.data
            )
        }
        /** O período que cobre a passagem [anoInicio]..[anoFim] naquele clube. */
        fun periodoDa(timeId: Int, anoInicio: Int, anoFim: Int): PeriodoNoClube? =
            periodos.firstOrNull { p ->
                p.timeId == timeId &&
                    // Sem ano no carimbo não dá para casar com a passagem.
                    (p.anoInicio <= 0 || p.anoInicio <= anoFim) &&
                    (p.saida?.ano?.let { it <= 0 || it >= anoInicio } ?: true)
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
        // Estatísticas por (time, ano), incluindo o intervalo de meses (via ordemGlobal)
        // em que o jogador de fato atuou naquele ano por aquele clube.
        data class AnoStats(
            val timeId: Int,
            val ano: Int,
            val partidas: Int,
            val gols: Int,
            val assists: Int,
            val minOrdem: Int,
            val maxOrdem: Int
        )
        val passagensAno = porTimeAno.entries
            .map { (key, evs) ->
                AnoStats(
                    timeId = key.first,
                    ano = key.second,
                    partidas = evs.map { it.partidaId }.toSet().size,
                    gols = evs.count { it.tipo == "GOL" },
                    assists = evs.count { it.tipo == "ASSISTENCIA" },
                    minOrdem = evs.minOf { it.ordemGlobal },
                    maxOrdem = evs.maxOf { it.ordemGlobal }
                )
            }
            .sortedWith(compareBy({ it.timeId }, { it.ano }))

        val passagens = mutableListOf<PassagemClube>()
        var atual: PassagemClube? = null
        for (s in passagensAno) {
            val cur = atual
            if (cur != null && cur.timeId == s.timeId && s.ano <= cur.anoFim + 1) {
                atual = cur.copy(
                    anoFim = s.ano,
                    mesFim = ordemGlobalParaMesCarreira(s.maxOrdem),
                    partidas = cur.partidas + s.partidas,
                    gols = cur.gols + s.gols,
                    assistencias = cur.assistencias + s.assists
                )
            } else {
                cur?.let { passagens.add(it) }
                val time = timesPorId[s.timeId]
                atual = PassagemClube(
                    timeId = s.timeId,
                    timeNome = time?.nome ?: "Clube #${s.timeId}",
                    escudoRes = time?.escudoRes ?: "",
                    anoInicio = s.ano,
                    anoFim = s.ano,
                    mesInicio = ordemGlobalParaMesCarreira(s.minOrdem),
                    mesFim = ordemGlobalParaMesCarreira(s.maxOrdem),
                    partidas = s.partidas,
                    gols = s.gols,
                    assistencias = s.assists
                )
            }
        }
        atual?.let { passagens.add(it) }

        // Cola em cada passagem o período de contrato correspondente.
        val passagensComPeriodo = passagens.map { p ->
            val periodo = periodoDa(p.timeId, p.anoInicio, p.anoFim)
            if (periodo == null) p else p.copy(chegada = periodo.chegada, saida = periodo.saida)
        }
        passagens.clear()
        passagens.addAll(passagensComPeriodo)

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
        potencial = potencial,
        salario = salario,
        contratoAnos = contratoAnos,
        valorMercado = valorMercado,
        lesionado = lesionado,
        partidasSuspensao = partidasSuspensao,
        cartoesAmarelosCiclo = cartoesAmarelosCiclo,
        cartoesAmarelosTemporada = cartoesAmarelosTemporada,
        cartoesVermelhosTemporada = cartoesVermelhosTemporada,
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
        grauLesao = grauLesao,
        disponívelParaVenda = disponívelParaVenda,
        disponívelParaEmprestimo = disponívelParaEmprestimo,
        timeOrigemEmprestimo = timeOrigemEmprestimo,
        anoRetornoEmprestimo = anoRetornoEmprestimo,
        mesRetornoEmprestimo = mesRetornoEmprestimo,
        janelaUltimaTransferencia = janelaUltimaTransferencia
    )

    // ── Gerador de nomes por nacionalidade ────────────────────────────────
    // Listas curtas e representativas. Se a nacionalidade não bater, cai no default (BR).

    private val nomesBr = listOf("Rodrigo","Bruno","Lucas","Gustavo","Felipe","Rafael","Marcelo","Diego","Thiago","André","Vinícius","Matheus","João","Pedro","Gabriel","Henrique","Danilo","Renato","Igor","Everton","Léo","Caio","Douglas","Fábio","Ricardo","Marcos","Alan","Jean")
    private val sobrenomesBr = listOf("Silva","Santos","Oliveira","Costa","Souza","Ferreira","Almeida","Ribeiro","Gomes","Martins","Barbosa","Nascimento","Mendes","Araújo","Cardoso","Correia","Cavalcante","Freitas","Fernandes","Moreira","Rocha","Dias","Melo","Teixeira","Pereira")

    private val nomesAr = listOf("Juan","Diego","Sebastián","Federico","Nicolás","Matías","Ezequiel","Franco","Emiliano","Cristian","Rodrigo","Facundo","Marcos","Leandro","Gonzalo","Julián","Alejandro","Ignacio","Lucas","Agustín")
    private val sobrenomesAr = listOf("García","Fernández","López","González","Rodríguez","Pérez","Martínez","Sánchez","Romero","Álvarez","Torres","Ramírez","Flores","Benítez","Acosta","Medina","Herrera","Ríos","Ortega","Suárez")

    private val nomesUr = listOf("Diego","Luis","Nicolás","Federico","Facundo","Sebastián","Cristian","Emiliano","Rodrigo","Marcelo","Gastón","Matías","Bruno","Andrés","Álvaro")
    private val sobrenomesUr = listOf("Suárez","Cavani","Godín","Bentancur","Nández","Cáceres","Torreira","Rodríguez","Núñez","Coates","Pereira","Olivera","Vecino","Valverde","Fernández")

    private val nomesEn = listOf("James","Harry","Jack","Thomas","Oliver","Jamie","Callum","George","Charlie","Alex","Daniel","Tom","Ben","Kyle","Marcus","Luke","Reece","Aaron","Kieran","Mason")
    private val sobrenomesEn = listOf("Smith","Jones","Taylor","Brown","Williams","Wilson","Johnson","Davies","Robinson","Wright","Walker","Roberts","Green","Hall","Wood","Harris","Clark","Baker","Cooper","Bennett")

    /** "Carlos Henrique Silva" → "Carlos S."; nome único volta inteiro. */
    private fun abreviarNome(nomeCompleto: String): String {
        val partes = nomeCompleto.trim().split(Regex("\\s+"))
        return if (partes.size < 2) partes.first()
               else "${partes.first()} ${partes.last().take(1)}."
    }

    private fun gerarNomeAleatorio(nacionalidade: String): Pair<String, String> {
        val rng = kotlin.random.Random.Default
        val (nomes, sobrenomes) = when (nacionalidade.lowercase()) {
            "argentino"                        -> nomesAr to sobrenomesAr
            "uruguaio"                         -> nomesUr to sobrenomesUr
            "inglês", "ingles", "britânico", "britanico" -> nomesEn to sobrenomesEn
            else                               -> nomesBr to sobrenomesBr
        }
        val nome = nomes[rng.nextInt(nomes.size)]
        val sobrenome = sobrenomes[rng.nextInt(sobrenomes.size)]
        val nomeCompleto = "$nome $sobrenome"
        val abreviado = "$nome ${sobrenome.take(1)}."
        return nomeCompleto to abreviado
    }
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
    val mesInicio: Int = 0,       // mês (1–12) da 1ª partida no clube; 0 = desconhecido
    val mesFim: Int = 0,          // mês (1–12) da última partida no clube; 0 = desconhecido
    val partidas: Int,
    val gols: Int,
    val assistencias: Int,
    val notaMedia: Float? = null, // nota da temporada atual (apenas última passagem)

    /**
     * Quando ele CHEGOU e quando SAIU, pelas transferências.
     *
     * [anoInicio]/[mesInicio] dizem quando ele estreou e quando jogou pela
     * última vez naquele clube — são derivados das partidas, e por isso nunca
     * contaram o tempo entre a assinatura e o primeiro jogo, nem entre o último
     * jogo e a saída. Estes dois dizem o período de contrato de fato.
     *
     * null quando não há transferência que delimite a passagem: é o caso do
     * clube de origem de todo jogador do seed, que nunca foi contratado de
     * lugar nenhum. Aí a tela volta a mostrar o período pelas partidas.
     */
    val chegada: br.com.managerfoot.domain.engine.DataDoJogo? = null,
    val saida: br.com.managerfoot.domain.engine.DataDoJogo? = null
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
    /** Retorna o EstadioEntity do time, criando/inicializando preços se necessário. */
    suspend fun buscarOuCriar(timeId: Int): EstadioEntity {
        val time = timeDao.buscarPorId(timeId)
        val precoBase = time?.precoIngresso ?: 0L
        val existente = estadioDao.buscarPorTime(timeId)
        if (existente != null) {
            // Backfill de preços para estádios criados antes do sistema por setor.
            if (existente.precoArquibancada <= 0 && existente.precoCadeira <= 0 && existente.precoCamarote <= 0) {
                val (a, c, m) = EstadioEntity.precosPadrao(precoBase.coerceAtLeast(1))
                val atualizado = existente.copy(precoArquibancada = a, precoCadeira = c, precoCamarote = m)
                estadioDao.salvar(atualizado)
                return atualizado
            }
            return existente
        }
        val capacidade = time?.estadioCapacidade ?: 0
        val (a, c, m) = EstadioEntity.precosPadrao(precoBase.coerceAtLeast(1))
        val novo = EstadioEntity.inicializar(timeId, capacidade)
            .copy(precoArquibancada = a, precoCadeira = c, precoCamarote = m)
        estadioDao.salvar(novo)
        return novo
    }

    /** Atualiza os preços por setor (centavos), mantendo os níveis. */
    suspend fun atualizarPrecos(timeId: Int, precoArq: Long, precoCad: Long, precoCam: Long): EstadioEntity {
        val estadio = buscarOuCriar(timeId)
        val atualizado = estadio.copy(
            precoArquibancada = precoArq.coerceAtLeast(0),
            precoCadeira      = precoCad.coerceAtLeast(0),
            precoCamarote     = precoCam.coerceAtLeast(0)
        )
        estadioDao.salvar(atualizado)
        return atualizado
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
