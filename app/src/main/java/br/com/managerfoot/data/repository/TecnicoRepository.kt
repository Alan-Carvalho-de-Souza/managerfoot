package br.com.managerfoot.data.repository

import br.com.managerfoot.data.dao.TecnicoDao
import br.com.managerfoot.data.dao.TimeDao
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
    private val timeDao: TimeDao
) {

    // ── Observers ────────────────────────────────────────────
    fun observeRanking(): Flow<List<TecnicoEntity>> = tecnicoDao.observeRanking()
    fun observeLivres(): Flow<List<TecnicoEntity>> = tecnicoDao.observeLivres()
    fun observePorTime(timeId: Int): Flow<TecnicoEntity?> = tecnicoDao.observePorTime(timeId)
    fun observeTodos(): Flow<List<TecnicoEntity>> = tecnicoDao.observeTodos()

    // ── Suspend lookups ──────────────────────────────────────
    suspend fun buscarPorId(id: Int): TecnicoEntity? = tecnicoDao.buscarPorId(id)
    suspend fun buscarPorTime(timeId: Int): TecnicoEntity? = tecnicoDao.buscarPorTime(timeId)
    suspend fun buscarTecnicoDoJogador(): TecnicoEntity? = tecnicoDao.buscarTecnicoDoJogador()
    suspend fun buscarTodos(): List<TecnicoEntity> = tecnicoDao.buscarTodos()
    suspend fun buscarPassagens(tecnicoId: Int): List<PassagemTecnicoEntity> =
        tecnicoDao.buscarPassagens(tecnicoId)

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
    //  IA: contrata técnicos livres para times sem técnico
    // ─────────────────────────────────────────────────────────

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
