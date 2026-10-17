package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// Regras de pontuação por colocação da AMÉRICA DO SUL — ver ranking_conmebol.md.
//
// O ranking por colocação é definido por continente: este é o conjunto
// sul-americano, aplicado aos clubes que Continentes.ehAmericaDoSul() aceita.
// Europa e os demais continentes terão seus próprios conjuntos de regras, que
// entram como novos objetos ao lado deste — nada aqui vale para eles.
//
// Pontuação Total = Desempenho (últimos 10 anos, com depreciação de 10%/ano)
//                 + Coeficiente Histórico (fixo, sem depreciação)
//                 + Bônus por Campeonatos Locais (com depreciação)
//
// Importante: este ranking é separado do ranking_geral, que pontua apenas
// desempenho em campo (3 por vitória / 1 por empate) e não concede bônus
// por título ou vice-campeonato.
// ─────────────────────────────────────────────────────────────────────────────

enum class CompeticaoConmebol(val rotulo: String) {
    LIBERTADORES("Copa Libertadores"),
    SUL_AMERICANA("Copa Sul-Americana"),
    LIGA_NACIONAL("Campeonato Nacional"),
    HISTORICO("Coeficiente Histórico")
}

// Fase final atingida por um clube numa competição continental.
enum class FaseConmebol {
    CAMPEAO,
    VICE,
    SEMIFINAL,
    QUARTAS,
    OITAVAS,
    FASE_DE_GRUPOS,
    PRELIMINAR_3,
    PRELIMINAR_2,
    PRELIMINAR_1
}

object RankingConmebol {

    // Marcador de ano para lançamentos do coeficiente histórico (1960-2014).
    const val ANO_HISTORICO = 0

    // Janela de depreciação: o ano mais recente vale 100% e cada ano anterior perde 10%.
    const val JANELA_ANOS = 10

    // ── Bloco 3: bônus por campeonato local ──────────────────────────────
    // 50 pontos ao campeão de cada liga nacional. Havendo mais de um campeão no
    // ano (Apertura e Clausura), os 50 pontos são divididos igualmente.
    const val PONTOS_LIGA_NACIONAL = 50.0

    // ── Bloco 1: pontuação por desempenho (últimos 10 anos) ──────────────
    // Pontos fixos pela fase final atingida.
    private val FASE_LIBERTADORES = mapOf(
        FaseConmebol.CAMPEAO        to 1000.0,
        FaseConmebol.VICE           to  400.0,
        FaseConmebol.SEMIFINAL      to  240.0,
        FaseConmebol.QUARTAS        to  160.0,
        FaseConmebol.OITAVAS        to   80.0,
        FaseConmebol.FASE_DE_GRUPOS to   20.0,
        FaseConmebol.PRELIMINAR_3   to   12.0,
        FaseConmebol.PRELIMINAR_2   to    8.0,
        FaseConmebol.PRELIMINAR_1   to    4.0
    )

    private val FASE_SUL_AMERICANA = mapOf(
        FaseConmebol.CAMPEAO        to 300.0,
        FaseConmebol.VICE           to 120.0,
        FaseConmebol.SEMIFINAL      to  72.0,
        FaseConmebol.QUARTAS        to  48.0,
        FaseConmebol.OITAVAS        to  24.0,
        FaseConmebol.FASE_DE_GRUPOS to   6.0,
        FaseConmebol.PRELIMINAR_1   to   4.0
    )

    // Pontos dinâmicos por partida, da fase de grupos em diante.
    private const val VITORIA_LIBERTADORES = 80.0
    private const val EMPATE_LIBERTADORES  = 40.0
    private const val VITORIA_SUL_AMERICANA = 24.0
    private const val EMPATE_SUL_AMERICANA  = 12.0

    // ── Bloco 2: coeficiente histórico (1960-2014), sem depreciação ──────
    // Nota: o documento lista 40 pontos por empate na Libertadores histórica, o que
    // seria 5x o valor da vitória; adotamos 4, coerente com a proporção da
    // Sul-Americana (1,2 de empate contra 2,4 de vitória).
    private val HISTORICO_LIBERTADORES = mapOf(
        FaseConmebol.CAMPEAO   to 100.0,
        FaseConmebol.VICE      to  40.0,
        FaseConmebol.SEMIFINAL to  24.0
    )
    private const val HIST_VITORIA_LIBERTADORES = 8.0
    private const val HIST_EMPATE_LIBERTADORES  = 4.0

    private val HISTORICO_SUL_AMERICANA = mapOf(
        FaseConmebol.CAMPEAO   to 30.0,
        FaseConmebol.VICE      to 12.0,
        FaseConmebol.SEMIFINAL to  7.2
    )
    private const val HIST_VITORIA_SUL_AMERICANA = 2.4
    private const val HIST_EMPATE_SUL_AMERICANA  = 1.2

    // ── Consultas ────────────────────────────────────────────────────────

    // Pontos fixos pela fase final atingida numa competição continental.
    fun pontosPorFase(competicao: CompeticaoConmebol, fase: FaseConmebol): Double = when (competicao) {
        CompeticaoConmebol.LIBERTADORES  -> FASE_LIBERTADORES[fase] ?: 0.0
        CompeticaoConmebol.SUL_AMERICANA -> FASE_SUL_AMERICANA[fase] ?: 0.0
        else                             -> 0.0
    }

    // Pontos por partida da fase de grupos em diante.
    fun pontosPorVitoria(competicao: CompeticaoConmebol): Double = when (competicao) {
        CompeticaoConmebol.LIBERTADORES  -> VITORIA_LIBERTADORES
        CompeticaoConmebol.SUL_AMERICANA -> VITORIA_SUL_AMERICANA
        else                             -> 0.0
    }

    fun pontosPorEmpate(competicao: CompeticaoConmebol): Double = when (competicao) {
        CompeticaoConmebol.LIBERTADORES  -> EMPATE_LIBERTADORES
        CompeticaoConmebol.SUL_AMERICANA -> EMPATE_SUL_AMERICANA
        else                             -> 0.0
    }

    // Coeficiente histórico de uma campanha anterior a 2015.
    fun pontosHistoricosPorFase(competicao: CompeticaoConmebol, fase: FaseConmebol): Double = when (competicao) {
        CompeticaoConmebol.LIBERTADORES  -> HISTORICO_LIBERTADORES[fase] ?: 0.0
        CompeticaoConmebol.SUL_AMERICANA -> HISTORICO_SUL_AMERICANA[fase] ?: 0.0
        else                             -> 0.0
    }

    fun pontosHistoricosPorVitoria(competicao: CompeticaoConmebol): Double = when (competicao) {
        CompeticaoConmebol.LIBERTADORES  -> HIST_VITORIA_LIBERTADORES
        CompeticaoConmebol.SUL_AMERICANA -> HIST_VITORIA_SUL_AMERICANA
        else                             -> 0.0
    }

    fun pontosHistoricosPorEmpate(competicao: CompeticaoConmebol): Double = when (competicao) {
        CompeticaoConmebol.LIBERTADORES  -> HIST_EMPATE_LIBERTADORES
        CompeticaoConmebol.SUL_AMERICANA -> HIST_EMPATE_SUL_AMERICANA
        else                             -> 0.0
    }

    // Peso de depreciação: 100% no ano mais recente, -10% por ano, zero fora da janela.
    // peso(n) = 1.0 - 0.1 * (n - 1), com n = 1 para o ano mais recente.
    fun peso(ano: Int, anoAtual: Int): Double {
        val distancia = anoAtual - ano
        if (distancia < 0 || distancia >= JANELA_ANOS) return 0.0
        return 1.0 - 0.1 * distancia
    }

    // ── Semeadura do coeficiente histórico ───────────────────────────────
    // O jogo não tem passado antes da primeira temporada, então derivamos o
    // coeficiente histórico da reputação do clube no seed: um clube de reputação
    // máxima nasce com o equivalente a ~5 títulos de Libertadores, e abaixo de
    // REPUTACAO_MINIMA_HISTORICO o clube nasce sem tradição continental.
    private const val REPUTACAO_MINIMA_HISTORICO = 55f
    private const val HISTORICO_MAXIMO = 520.0

    fun coeficienteHistoricoPorReputacao(reputacao: Float): Double {
        if (reputacao <= REPUTACAO_MINIMA_HISTORICO) return 0.0
        val faixa = (100f - REPUTACAO_MINIMA_HISTORICO).toDouble()
        val fator = ((reputacao - REPUTACAO_MINIMA_HISTORICO) / faixa).toDouble().coerceIn(0.0, 1.0)
        // Curva acelerada: concentra a tradição nos clubes de reputação muito alta.
        return HISTORICO_MAXIMO * fator * fator
    }
}
