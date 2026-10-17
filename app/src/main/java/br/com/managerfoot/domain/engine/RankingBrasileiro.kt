package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// Regras de pontuação por colocação dos clubes do BRASIL — o Ranking Nacional
// de Clubes (RNC) da CBF. Ver ranking_brasileiro.md.
//
// O ranking por colocação é definido por recorte geográfico, e cada recorte tem
// o seu próprio conjunto de regras. Este é o conjunto POR PAÍS do Brasil; o
// conjunto continental da América do Sul é o RankingConmebol, independente
// deste — ciclos, pesos e competições diferentes:
//
//   CONMEBOL : janela de 10 anos, depreciação de 10%/ano, competições
//              continentais + bônus de liga + coeficiente histórico.
//   RNC      : janela de 5 anos, pesos inteiros 5/4/3/2/1, e só pontua o
//              Campeonato Brasileiro (na série disputada) + a Copa do Brasil.
//
// Pontuação Total = Σ (pontos da temporada × peso do ano), para os 5 anos da
// janela. Fora da janela a temporada não decai: sai da conta por completo.
// ─────────────────────────────────────────────────────────────────────────────

/** Competições que pontuam no RNC. Um clube joga uma única série por ano. */
enum class CompeticaoRnc(val rotulo: String, val base: Int) {
    SERIE_A("Brasileiro Série A", 800),
    SERIE_B("Brasileiro Série B", 400),
    SERIE_C("Brasileiro Série C", 200),
    SERIE_D("Brasileiro Série D", 100),
    COPA_DO_BRASIL("Copa do Brasil", 600)
}

/** Fase máxima atingida na Copa do Brasil (6 fases, 64 clubes). */
enum class FaseCopaDoBrasil(val rotulo: String, val percentual: Double) {
    CAMPEAO("Campeão", 1.00),
    VICE("Vice-campeão", 0.80),
    SEMIFINAL("Semifinal", 0.70),
    QUARTAS("Quartas de final", 0.55),
    OITAVAS("Oitavas de final", 0.40),
    SEGUNDA_FASE("Segunda Fase", 0.25),
    PRIMEIRA_FASE("Primeira Fase", 0.15)
}

object RankingBrasileiro {

    /** Janela do ciclo: só os últimos 5 anos entram na conta. */
    const val JANELA_ANOS = 5

    // ── Pesos por ano ────────────────────────────────────────────────────
    /**
     * Peso inteiro do ano: 5 para o mais recente, 4, 3, 2, 1 para os anteriores
     * e 0 para o que já saiu da janela (ou para um ano futuro, por segurança).
     */
    fun peso(ano: Int, anoAtual: Int): Int {
        val distancia = anoAtual - ano
        if (distancia < 0 || distancia >= JANELA_ANOS) return 0
        return JANELA_ANOS - distancia
    }

    // ── Campeonato Brasileiro: escala por posição final ──────────────────
    // 100% para o 1º, 80% para o 2º, 75% para o 3º e queda linear de 3,294
    // pontos percentuais por posição até o 20º (19%). A escala é a mesma nas
    // quatro séries; só muda a base.
    private const val QUEDA_POR_POSICAO = 0.03294
    private const val PRIMEIRA_POSICAO_LINEAR = 3
    private const val PERCENTUAL_TERCEIRO = 0.75

    // Piso da escala: percentual do 20º colocado (19%).
    private val PERCENTUAL_PISO =
        PERCENTUAL_TERCEIRO - QUEDA_POR_POSICAO * (20 - PRIMEIRA_POSICAO_LINEAR)

    /** Percentual da base para a posição final, já limitado ao piso do 20º. */
    fun percentualPorPosicao(posicao: Int): Double = when {
        posicao <= 0 -> 0.0
        posicao == 1 -> 1.00
        posicao == 2 -> 0.80
        else -> {
            val linear = PERCENTUAL_TERCEIRO - QUEDA_POR_POSICAO * (posicao - PRIMEIRA_POSICAO_LINEAR)
            // Liga com mais de 20 clubes não pode gerar percentual negativo:
            // da 20ª posição em diante todos recebem o piso de 19%.
            linear.coerceAtLeast(PERCENTUAL_PISO)
        }
    }

    /** Pontos de uma temporada de liga, pela série disputada e posição final. */
    fun pontosPorPosicao(serie: CompeticaoRnc, posicao: Int): Int {
        if (serie == CompeticaoRnc.COPA_DO_BRASIL) return 0
        return Math.round(serie.base * percentualPorPosicao(posicao)).toInt()
    }

    /** Série do RNC correspondente à divisão brasileira (1..4), ou null fora dela. */
    fun serieDaDivisao(divisao: Int): CompeticaoRnc? = when (divisao) {
        1 -> CompeticaoRnc.SERIE_A
        2 -> CompeticaoRnc.SERIE_B
        3 -> CompeticaoRnc.SERIE_C
        4 -> CompeticaoRnc.SERIE_D
        else -> null
    }

    // ── Copa do Brasil: pontos pela fase máxima atingida ─────────────────
    // Estritamente por fase: todos os eliminados na mesma fase recebem a mesma
    // pontuação, sem desempate por campanha, saldo ou número de vitórias.

    /** Pontos pela fase máxima atingida na Copa do Brasil. */
    fun pontosCopaDoBrasil(fase: FaseCopaDoBrasil): Int =
        Math.round(CompeticaoRnc.COPA_DO_BRASIL.base * fase.percentual).toInt()

    /**
     * Converte o nome da fase gravado em PartidaEntity.fase (ver
     * MotorCampeonato.COPA_FASES) na fase do RNC. A Final não é decidida aqui:
     * quem chegou nela é campeão ou vice conforme o resultado, então devolve
     * [FaseCopaDoBrasil.VICE] como piso de quem disputou a decisão.
     */
    fun faseDaCopaPorNome(nome: String): FaseCopaDoBrasil? = when (nome) {
        "Primeira Fase" -> FaseCopaDoBrasil.PRIMEIRA_FASE
        "Segunda Fase"  -> FaseCopaDoBrasil.SEGUNDA_FASE
        "Oitavas"       -> FaseCopaDoBrasil.OITAVAS
        "Quartas"       -> FaseCopaDoBrasil.QUARTAS
        "Semi"          -> FaseCopaDoBrasil.SEMIFINAL
        "Final"         -> FaseCopaDoBrasil.VICE
        else            -> null
    }
}
