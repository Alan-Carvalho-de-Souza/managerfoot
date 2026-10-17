package br.com.managerfoot.data.repository

import br.com.managerfoot.data.dao.HallDaFamaDao
import br.com.managerfoot.data.dao.TecnicoDao
import br.com.managerfoot.data.dao.TimeDao
import br.com.managerfoot.data.database.entities.HallDaFamaEntity
import br.com.managerfoot.data.database.entities.PassagemTecnicoEntity
import br.com.managerfoot.data.database.entities.TecnicoEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository de técnicos: contratos, livres no mercado, ranking, estatísticas
 * e histórico de passagens por clube.
 */
@Singleton
class TecnicoRepository @Inject constructor(
    private val tecnicoDao: TecnicoDao,
    private val timeDao: TimeDao,
    private val hallDaFamaDao: HallDaFamaDao
) {

    // ── Observers ────────────────────────────────────────────
    fun observeRanking(): Flow<List<TecnicoEntity>> = tecnicoDao.observeRanking()
    fun observeLivres(): Flow<List<TecnicoEntity>> = tecnicoDao.observeLivres()
    fun observePorTime(timeId: Int): Flow<TecnicoEntity?> = tecnicoDao.observePorTime(timeId)
    fun observeTodos(): Flow<List<TecnicoEntity>> = tecnicoDao.observeTodos()

    // ── Ajustes ──────────────────────────────────────────────
    /**
     * Aplica um delta à reputação do técnico atual do time (cumprimento/falha de meta,
     * bônus de título etc.). Se não houver técnico no time, é no-op.
     */
    suspend fun ajustarReputacaoTecnicoDoTime(timeId: Int, delta: Float) {
        val t = tecnicoDao.buscarPorTime(timeId) ?: return
        val nova = (t.reputacao + delta).coerceIn(0f, 100f)
        tecnicoDao.atualizarReputacao(t.id, nova)
    }

    // ── Suspend lookups ──────────────────────────────────────
    suspend fun buscarPorId(id: Int): TecnicoEntity? = tecnicoDao.buscarPorId(id)
    suspend fun buscarPorTime(timeId: Int): TecnicoEntity? = tecnicoDao.buscarPorTime(timeId)
    suspend fun buscarTecnicoDoJogador(): TecnicoEntity? = tecnicoDao.buscarTecnicoDoJogador()
    suspend fun buscarTodos(): List<TecnicoEntity> = tecnicoDao.buscarTodos()
    suspend fun buscarPassagens(tecnicoId: Int): List<PassagemTecnicoEntity> =
        tecnicoDao.buscarPassagens(tecnicoId)

    /** Títulos conquistados pelo técnico (Hall da Fama), com campeonato e temporada. */
    suspend fun buscarTitulos(tecnicoId: Int): List<HallDaFamaEntity> =
        hallDaFamaDao.buscarTitulosPorTecnico(tecnicoId)

    // ─────────────────────────────────────────────────────────
    //  Cadastro / liberação / contratação
    // ─────────────────────────────────────────────────────────

    /**
     * Cadastra o técnico do usuário. Antes, libera o técnico atual do time
     * (se houver) — ele vira agente livre. Cria também a passagem ativa
     * do usuário no clube escolhido.
     */
    suspend fun cadastrarTecnicoUsuario(
        nome: String,
        nacionalidade: String,
        timeId: Int,
        anoInicial: Int
    ): Int {
        // Demite técnico atual do time → vira livre (sem timeId)
        val atual = tecnicoDao.buscarPorTime(timeId)
        if (atual != null) {
            // Encerra passagem ativa do técnico atual, se houver
            val passagemAtual = tecnicoDao.buscarPassagemAtiva(atual.id)
            if (passagemAtual != null) {
                tecnicoDao.encerrarPassagem(passagemAtual.id, anoInicial)
            }
            tecnicoDao.liberarDoTime(atual.id)
        }

        // Cria entrada do usuário (controladoPorJogador = true)
        val nomeTrimado = nome.trim().take(30).ifBlank { "Técnico" }
        val partes = nomeTrimado.split(" ").filter { it.isNotBlank() }
        val nomeAbrev = if (partes.size >= 2) "${partes[0].first()}. ${partes.last()}"
                        else nomeTrimado.take(15)

        val tecnicoUsuario = TecnicoEntity(
            id = 0,
            nome = nomeTrimado,
            nomeAbreviado = nomeAbrev,
            idade = 40,
            nacionalidade = nacionalidade,
            timeId = timeId,
            salario = 500_000_00L,    // R$ 5.000/mês — começa modesto
            contratoAnos = 3,
            reputacao = 50f,
            controladoPorJogador = true
        )
        val novoId = tecnicoDao.inserir(tecnicoUsuario).toInt()

        // Cria passagem ativa do usuário no time
        val time = timeDao.buscarPorId(timeId)
        tecnicoDao.inserirPassagem(
            PassagemTecnicoEntity(
                tecnicoId = novoId,
                timeId = timeId,
                timeNome = time?.nome ?: "",
                timeEscudo = time?.escudoRes ?: "",
                anoInicio = anoInicial,
                anoFim = anoInicial,
                ativa = true
            )
        )

        return novoId
    }

    /**
     * Cria a passagem ATIVA inicial de cada técnico já empregado (timeId != null)
     * que ainda não tenha passagem ativa. Serve para que o Ranking/Histórico
     * exiba as passagens dos técnicos da IA desde os primeiros jogos — do mesmo
     * modo que o técnico do usuário já ganha a sua em [cadastrarTecnicoUsuario].
     *
     * Idempotente: técnicos com passagem ativa (ex.: o do usuário) são ignorados,
     * então pode ser chamado com segurança no início do jogo.
     */
    suspend fun criarPassagensIniciaisEmClubes(anoInicial: Int) {
        val tecnicos = tecnicoDao.buscarTodos()
        for (t in tecnicos) {
            val timeId = t.timeId ?: continue
            if (tecnicoDao.buscarPassagemAtiva(t.id) != null) continue
            val time = timeDao.buscarPorId(timeId)
            tecnicoDao.inserirPassagem(
                PassagemTecnicoEntity(
                    tecnicoId  = t.id,
                    timeId     = timeId,
                    timeNome   = time?.nome ?: "",
                    timeEscudo = time?.escudoRes ?: "",
                    anoInicio  = anoInicial,
                    anoFim     = anoInicial,
                    ativa      = true
                )
            )
        }
    }

    /** Libera técnico de um time (vira free agent). Encerra a passagem ativa. */
    suspend fun liberarDoTime(tecnicoId: Int, anoFim: Int) {
        val passagem = tecnicoDao.buscarPassagemAtiva(tecnicoId)
        if (passagem != null) {
            tecnicoDao.encerrarPassagem(passagem.id, anoFim)
        }
        tecnicoDao.liberarDoTime(tecnicoId)
    }

    /**
     * Contrata um técnico livre para um time. Se o time já tinha técnico,
     * libera o atual antes. Cria nova passagem ativa.
     */
    suspend fun contratar(
        tecnicoId: Int,
        timeId: Int,
        salario: Long,
        anos: Int,
        anoAtual: Int
    ) {
        // Demite técnico atual do time, se houver
        val atual = tecnicoDao.buscarPorTime(timeId)
        if (atual != null && atual.id != tecnicoId) {
            liberarDoTime(atual.id, anoAtual)
        }

        tecnicoDao.contratar(tecnicoId, timeId, salario, anos)

        // Cria passagem ativa
        val time = timeDao.buscarPorId(timeId)
        tecnicoDao.inserirPassagem(
            PassagemTecnicoEntity(
                tecnicoId = tecnicoId,
                timeId = timeId,
                timeNome = time?.nome ?: "",
                timeEscudo = time?.escudoRes ?: "",
                anoInicio = anoAtual,
                anoFim = anoAtual,
                ativa = true
            )
        )
    }

    // ─────────────────────────────────────────────────────────
    //  Estatísticas
    // ─────────────────────────────────────────────────────────

    /**
     * Registra o resultado de uma partida para os técnicos dos dois times.
     * Atualiza tanto o stats acumulado do técnico quanto a passagem ativa.
     * V/E/D = uma das três deve ser 1, as outras 0.
     */
    suspend fun registrarResultadoPartida(
        timeCasaId: Int,
        timeForaId: Int,
        golsCasa: Int,
        golsFora: Int
    ) {
        suspend fun aplicar(timeId: Int, foiVencedor: Boolean, foiEmpate: Boolean) {
            val tecnico = tecnicoDao.buscarPorTime(timeId) ?: return
            val v = if (foiVencedor) 1 else 0
            val e = if (foiEmpate) 1 else 0
            val d = if (!foiVencedor && !foiEmpate) 1 else 0
            tecnicoDao.acrescentarResultado(tecnico.id, v, e, d)
            // Atualiza passagem ativa também
            val passagem = tecnicoDao.buscarPassagemAtiva(tecnico.id)
            if (passagem != null) {
                tecnicoDao.acrescentarResultadoPassagem(passagem.id, v, e, d)
            }
        }

        when {
            golsCasa > golsFora -> {
                aplicar(timeCasaId, foiVencedor = true,  foiEmpate = false)
                aplicar(timeForaId, foiVencedor = false, foiEmpate = false)
            }
            golsFora > golsCasa -> {
                aplicar(timeCasaId, foiVencedor = false, foiEmpate = false)
                aplicar(timeForaId, foiVencedor = true,  foiEmpate = false)
            }
            else -> {
                aplicar(timeCasaId, foiVencedor = false, foiEmpate = true)
                aplicar(timeForaId, foiVencedor = false, foiEmpate = true)
            }
        }
    }

    /** Registra um título conquistado pelo técnico do time campeão. */
    suspend fun registrarTitulo(timeCampeaoId: Int) {
        val tecnico = tecnicoDao.buscarPorTime(timeCampeaoId) ?: return
        tecnicoDao.acrescentarTitulo(tecnico.id)
        val passagem = tecnicoDao.buscarPassagemAtiva(tecnico.id)
        if (passagem != null) {
            tecnicoDao.acrescentarTituloPassagem(passagem.id)
        }
        // Bônus de reputação por título: +5%, mínimo 1pt
        val bonus = (tecnico.reputacao * 0.05f).coerceAtLeast(1.0f)
        tecnicoDao.atualizarReputacao(tecnico.id, (tecnico.reputacao + bonus).coerceIn(0f, 100f))
    }

    /** Reset stats de temporada de todos os técnicos. */
    suspend fun resetarStatsTemporada() = tecnicoDao.resetarStatsTemporada()

    // ─────────────────────────────────────────────────────────
    //  Demissão automática por mau desempenho
    // ─────────────────────────────────────────────────────────

    /**
     * Demite técnicos de times que tiveram desempenho ruim na temporada.
     * O técnico do time controlado pelo jogador NUNCA é demitido por esta
     * regra (o usuário decide).
     *
     * Critérios:
     *  - **Rebaixados** (passados como [timesRebaixados]): probabilidade alta
     *    de demitir (≈80%). Quase sempre o clube troca de comando.
     *  - **Demais times com aproveitamento da temporada < 0.30 (30%)**:
     *    probabilidade média (≈40%) de demitir.
     *
     * @param timesRebaixados IDs de times que foram rebaixados nesta temporada
     * @param anoFim Ano que está encerrando (para gravar fim da passagem)
     */
    suspend fun demitirPorDesempenho(timesRebaixados: Set<Int>, anoFim: Int) {
        val todos = tecnicoDao.buscarTodos()
        for (tecnico in todos) {
            if (tecnico.timeId == null) continue           // já está livre
            if (tecnico.controladoPorJogador) continue     // usuário decide

            val foiRebaixado = tecnico.timeId in timesRebaixados
            val aproveitamentoTemp = if (tecnico.jogosTemporada > 0)
                (tecnico.vitoriasTemporada * 3 + tecnico.empatesTemporada).toFloat() /
                    (tecnico.jogosTemporada * 3)
            else 1.0f  // se não jogou, não demite

            val prob = when {
                foiRebaixado                  -> 0.80f
                aproveitamentoTemp < 0.30f    -> 0.40f
                else                          -> 0.0f
            }
            if (prob == 0.0f) continue
            if (kotlin.random.Random.nextFloat() > prob) continue

            liberarDoTime(tecnico.id, anoFim)
        }
    }

    // ─────────────────────────────────────────────────────────
    //  IA: contrata técnicos livres para times sem técnico
    // ─────────────────────────────────────────────────────────

    /**
     * Troca o técnico de um time no meio da temporada por crise de resultados
     * (usado quando o clube está na zona de risco com aproveitamento ruim).
     * Escolhe um substituto livre realista — preferindo mesma nacionalidade e
     * reputação compatível com o clube. Se não houver substituto adequado,
     * mantém o técnico atual (não demite "no vácuo"). Nunca mexe no time do
     * usuário. Retorna true se houve troca.
     */
    suspend fun trocarTecnicoPorCrise(timeId: Int, anoAtual: Int): Boolean {
        val atual = tecnicoDao.buscarPorTime(timeId)
        if (atual?.controladoPorJogador == true) return false
        val time = timeDao.buscarPorId(timeId) ?: return false
        val nac = if (time.pais in listOf("Uruguay", "Uruguai")) "Uruguai" else time.pais

        // Livres, excluindo o técnico atual (que ainda tem timeId até a troca)
        val livres = tecnicoDao.buscarTodos().filter { it.timeId == null && it.id != atual?.id }
        if (livres.isEmpty()) return false

        val compat = livres.filter { it.reputacao <= time.reputacao + 15f }
        val substituto = compat.filter { it.nacionalidade == nac }.maxByOrNull { it.reputacao }
            ?: compat.maxByOrNull { it.reputacao }
            ?: return false

        val rep = time.reputacao.toLong()
        val salario = (150_000L + (rep - 30L) * 50_000L).coerceAtLeast(150_000L)
        // contratar() já demite o atual (vira livre) e cria a nova passagem.
        contratar(substituto.id, timeId, salario, 2, anoAtual)
        return true
    }

    /**
     * Movimento natural do mercado de técnicos ao FINAL da temporada: clubes de
     * maior reputação, iterados dos melhores para os piores, podem contratar um
     * técnico livre de destaque quando o atual é claramente inferior ao patamar
     * do clube. O técnico substituído volta ao mercado e pode "descer" para um
     * clube menor mais adiante no laço — gerando um efeito cascata realista.
     *
     * Deve rodar APÓS [demitirPorDesempenho] e [preencherVagasComTecnicosLivres],
     * quando os recém-demitidos já estão no pool de livres.
     */
    suspend fun movimentarMercadoTecnicosFimTemporada(anoAtual: Int) {
        val times = timeDao.buscarTodos().sortedByDescending { it.reputacao }
        for (time in times) {
            val atual = tecnicoDao.buscarPorTime(time.id)
            if (atual?.controladoPorJogador == true) continue

            val livres = tecnicoDao.buscarTodos().filter { it.timeId == null }
            if (livres.isEmpty()) break

            val nac = if (time.pais in listOf("Uruguay", "Uruguai")) "Uruguai" else time.pais
            val repAtual = atual?.reputacao ?: 0f
            val compat = livres.filter { it.reputacao <= time.reputacao + 10f }
                .filter { it.reputacao > repAtual + 8f }   // só troca se for upgrade claro
            val alvo = compat.filter { it.nacionalidade == nac }.maxByOrNull { it.reputacao }
                ?: compat.maxByOrNull { it.reputacao }
                ?: continue

            // Probabilidade moderada de o clube se mexer (nem todo ano há troca).
            if (kotlin.random.Random.nextFloat() > 0.35f) continue

            val rep = time.reputacao.toLong()
            val salario = (150_000L + (rep - 30L) * 50_000L).coerceAtLeast(150_000L)
            contratar(alvo.id, time.id, salario, 2, anoAtual)  // libera o atual → volta ao pool
        }
    }

    /**
     * Para cada time sem técnico, contrata um técnico livre — preferindo
     * técnicos da mesma nacionalidade do clube e com reputação compatível
     * (não mais do que 15 acima da reputação do time, para evitar Ronaldinho
     * Gaúcho dirigindo time da Série D).
     */
    suspend fun preencherVagasComTecnicosLivres(anoAtual: Int) {
        val todosTimes = timeDao.buscarTodos()
        val livres = tecnicoDao.buscarTodos().filter { it.timeId == null }
            .toMutableList()
        for (time in todosTimes) {
            val ja = tecnicoDao.buscarPorTime(time.id)
            if (ja != null) continue
            // Filtra técnicos compatíveis
            val nac = if (time.pais in listOf("Uruguay", "Uruguai")) "Uruguai" else time.pais
            val candidato = livres
                .filter { it.nacionalidade == nac || livres.none { c -> c.nacionalidade == nac } }
                .filter { it.reputacao <= time.reputacao + 15f }
                .maxByOrNull { it.reputacao }
                ?: livres.maxByOrNull { it.reputacao }
                ?: continue
            // Salário proporcional à reputação do time (R$ 1.500-30.000/mês em centavos)
            val rep = time.reputacao.toLong()
            val salario = (150_000L + (rep - 30L) * 50_000L).coerceAtLeast(150_000L)
            contratar(candidato.id, time.id, salario, 2, anoAtual)
            livres.remove(candidato)
        }
    }
}
