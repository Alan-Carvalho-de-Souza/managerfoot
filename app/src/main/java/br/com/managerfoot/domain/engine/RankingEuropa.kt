package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// Regras de pontuação por colocação dos clubes da EUROPA — o Coeficiente de
// Clubes da UEFA. Ver ranking_europa.md.
//
// O ranking por colocação é definido por recorte geográfico, e cada recorte tem
// o seu próprio conjunto de regras. Os três que existem hoje:
//
//   CONMEBOL : janela de 10 anos, depreciação de 10%/ano, competições
//              continentais + bônus de liga + coeficiente histórico.
//   RNC/BRA  : janela de 5 anos, pesos inteiros 5/4/3/2/1, Brasileirão da série
//              disputada + Copa do Brasil.
//   UEFA     : janela de 5 temporadas SEM pesos (todas valem igual), e pontua
//              exclusivamente Champions, Europa e Conference League.
//
// ⚠️ DORMENTE: este conjunto pontua apenas competições da UEFA, e nenhuma delas
// existe no jogo — não há enum de campeonato, calendário nem participantes.
// Ligas e copas nacionais NÃO pontuam aqui, por regra. Enquanto as três
// competições não forem criadas, o ranking europeu fica vazio; as funções abaixo
// já estão prontas para pontuar no dia em que existirem.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Competições da UEFA que pontuam, com os parâmetros que as diferenciam:
 * o topo do bônus de posição na Fase de Liga e o bônus por fase do mata-mata.
 */
enum class CompeticaoUefa(
    val rotulo: String,
    /** Bônus de posição do 1º colocado da Fase de Liga. */
    val topoBonusFaseLiga: Double,
    /** Bônus do 24º colocado — define o passo da escada junto com o topo. */
    val bonusVigesimoQuarto: Double,
    /** Bônus fixo por fase alcançada no mata-mata, a partir das oitavas. */
    val bonusPorFaseMataMata: Double,
    /** Piso do total da Fase de Liga, garantido a quem participa dela. */
    val garantiaParticipacao: Double,
    /**
     * Bônus das posições 25º–36º. A Champions fixa 6; para Europa e Conference o
     * enunciado não define, e repetimos o valor do 24º (ver ranking_europa.md).
     */
    val bonusAbaixoDoVigesimoQuarto: Double
) {
    CHAMPIONS_LEAGUE("Champions League", 12.0, 6.25, 1.5, 0.0, 6.0),
    EUROPA_LEAGUE("Europa League", 6.0, 0.25, 1.0, 3.0, 0.25),
    CONFERENCE_LEAGUE("Conference League", 4.0, 0.125, 0.5, 2.5, 0.125)
}

/** Fases do mata-mata que rendem bônus de avanço, das oitavas em diante. */
enum class FaseUefa(val rotulo: String, val ordem: Int) {
    OITAVAS("Oitavas de final", 1),
    QUARTAS("Quartas de final", 2),
    SEMIFINAL("Semifinal", 3),
    FINAL("Final", 4)
}

/** Fases de qualificação, anteriores à Fase de Liga. Só dão consolação fixa. */
enum class FaseQualificatoriaUefa(val rotulo: String, val consolacao: Double) {
    PLAY_OFF("Play-off de qualificação", 1.0),
    TERCEIRA_PRE("3ª pré-eliminatória", 0.75),
    SEGUNDA_PRE("2ª pré-eliminatória", 0.50),
    PRIMEIRA_PRE("1ª pré-eliminatória", 0.25)
}

object RankingEuropa {

    /** Janela do ciclo: as últimas 5 temporadas completas da UEFA. */
    const val JANELA_ANOS = 5

    /** Quantidade de clubes na Fase de Liga do formato de liga única. */
    const val CLUBES_FASE_LIGA = 36

    /** Última posição da escada decrescente de bônus. */
    const val ULTIMA_POSICAO_ESCADA = 24

    // ── Pontos por partida (da Fase de Liga em diante) ───────────────────
    const val PONTOS_VITORIA = 2.0
    const val PONTOS_EMPATE  = 1.0

    /**
     * Pontos por partida. Jogo decidido nos pênaltis conta como empate para o
     * coeficiente, então quem avançou na disputa NÃO deve ser contado como
     * vitória: some-o em [empates], como manda a regra.
     */
    fun pontosPorPartidas(vitorias: Int, empates: Int): Double =
        PONTOS_VITORIA * vitorias + PONTOS_EMPATE * empates

    // ── Peso por temporada ───────────────────────────────────────────────
    /**
     * Diferente do RNC e do CONMEBOL, a UEFA não pondera por recência: as cinco
     * temporadas da janela valem 100%. Fora da janela a temporada não decai —
     * sai da conta de uma vez.
     */
    fun peso(ano: Int, anoAtual: Int): Double {
        val distancia = anoAtual - ano
        if (distancia < 0 || distancia >= JANELA_ANOS) return 0.0
        return 1.0
    }

    // ── Bônus por posição na Fase de Liga ────────────────────────────────
    /**
     * Escada decrescente do 1º ao 24º, com passo constante derivado do topo e do
     * valor do 24º. Na Champions e na Europa League o passo resultante é exato
     * (0,25); na Conference as âncoras do enunciado dão ≈0,1685.
     */
    fun passoDaEscada(competicao: CompeticaoUefa): Double =
        (competicao.topoBonusFaseLiga - competicao.bonusVigesimoQuarto) /
            (ULTIMA_POSICAO_ESCADA - 1)

    /** Bônus de posição na Fase de Liga, para a posição final [posicao] (1-based). */
    fun bonusPorPosicao(competicao: CompeticaoUefa, posicao: Int): Double = when {
        posicao <= 0 -> 0.0
        posicao > ULTIMA_POSICAO_ESCADA -> competicao.bonusAbaixoDoVigesimoQuarto
        else -> competicao.topoBonusFaseLiga - passoDaEscada(competicao) * (posicao - 1)
    }

    // ── Bônus por avanço no mata-mata ────────────────────────────────────
    /**
     * Acumulativo por fase alcançada, a partir das oitavas: quem chega à final
     * leva o bônus das quatro fases. O play-off entre a Fase de Liga e as oitavas
     * não entra, porque a contagem começa nas oitavas; e o campeão não ganha nada
     * além do bônus da final.
     */
    fun bonusPorAvanco(competicao: CompeticaoUefa, faseAlcancada: FaseUefa?): Double {
        if (faseAlcancada == null) return 0.0
        return competicao.bonusPorFaseMataMata * faseAlcancada.ordem
    }

    // ── Total de uma campanha ────────────────────────────────────────────
    /**
     * Pontos de uma temporada na competição: bônus de posição na Fase de Liga +
     * pontos das partidas (Fase de Liga e mata-mata) + bônus por avanço, com a
     * garantia de participação agindo como piso do total.
     */
    fun pontosDaCampanha(
        competicao: CompeticaoUefa,
        posicaoFaseLiga: Int,
        vitorias: Int,
        empates: Int,
        faseAlcancada: FaseUefa? = null
    ): Double {
        val total = bonusPorPosicao(competicao, posicaoFaseLiga) +
            pontosPorPartidas(vitorias, empates) +
            bonusPorAvanco(competicao, faseAlcancada)
        return total.coerceAtLeast(competicao.garantiaParticipacao)
    }

    /**
     * Pontos de quem caiu na qualificação: só a consolação fixa da rodada em que
     * foi eliminado. As partidas de qualificação não rendem pontos por jogo.
     */
    fun pontosDaQualificacao(fase: FaseQualificatoriaUefa): Double = fase.consolacao

    // ── Piso de proteção de 20% (fator país) ─────────────────────────────
    /**
     * Coeficiente do país: a MÉDIA por clube, não a soma. O enunciado fala em
     * "coeficiente total do país", mas usar a soma torna o piso gigante — com 20
     * clubes somando, 20% da soma supera o que um clube conquista sozinho e
     * achata o ranking, dando a todos os clubes do país a mesma pontuação. A UEFA
     * usa o coeficiente da associação, que é a média por clube. Ver a nota de
     * calibragem em ranking_europa.md.
     */
    fun coeficienteDoPais(pontosDosClubes: List<Double>): Double =
        if (pontosDosClubes.isEmpty()) 0.0
        else pontosDosClubes.sum() / pontosDosClubes.size

    const val FRACAO_PISO_PAIS = 0.20

    /** Piso individual garantido pelo país: 20% do coeficiente da associação. */
    fun pisoDoPais(coeficienteDoPais: Double): Double =
        FRACAO_PISO_PAIS * coeficienteDoPais

    /**
     * Rede de segurança para o clube tradicional que passou a janela sem se
     * classificar: o coeficiente individual nunca fica abaixo do piso do país.
     */
    fun aplicarPisoDoPais(pontosDoClube: Double, coeficienteDoPais: Double): Double =
        maxOf(pontosDoClube, pisoDoPais(coeficienteDoPais))

    // ── Elegibilidade ────────────────────────────────────────────────────
    /**
     * Divisões de elite europeias no seed: 11 = Premier League, 13 = La Liga,
     * 14 = Serie A. A 12 é a Championship, segunda divisão inglesa, que não
     * disputa a Europa — e, sem este corte, o piso de 20% daria pontos às suas
     * 24 equipes sem elas jogarem nada.
     */
    val DIVISOES_ELITE = setOf(11, 13, 14)

    fun ehDivisaoElite(divisao: Int): Boolean = divisao in DIVISOES_ELITE

    /**
     * Clubes sem liga nacional no jogo, que existem para disputar os torneios
     * continentais — ver o pool de "Outros times" e regras_championsleague.md.
     * Entram na Champions e pontuam, mas ficam fora do ranking por divisão de
     * elite (e do piso de 20%, que depende do coeficiente da associação).
     */
    val DIVISOES_POOL = setOf(90, 91, 92)

    /**
     * Divisões de onde saem os candidatos à Champions: as de elite europeia mais
     * o pool. O filtro de continente é aplicado por fora, porque o pool também
     * abriga clubes de outros continentes (o Inter Miami, por exemplo).
     */
    val DIVISOES_ELEGIVEIS_CHAMPIONS = DIVISOES_ELITE + DIVISOES_POOL
}
