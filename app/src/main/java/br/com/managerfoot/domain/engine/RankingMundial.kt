package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.TipoCampeonato

// ─────────────────────────────────────────────────────────────────────────────
// Regras de pontuação por colocação do recorte MUNDO — o Ranking Mundial de
// Clubes da IFFHS. Ver ranking_mundial.md.
//
// É o mais simples dos quatro conjuntos e o único que soma POR PARTIDA, sem
// nenhum bônus por colocação ou fase alcançada. A IFFHS não olha a força do
// adversário (não é Elo): só o peso do torneio em que o jogo foi disputado.
//
//   IFFHS/Mundo : janela de 12 meses, sem recência, partidas por peso do torneio.
//   UEFA/Europa : 5 temporadas sem pesos, partidas + posição + fase.
//   RNC/Brasil  : 5 temporadas, pesos 5/4/3/2/1, posição final e fase máxima.
//   CONMEBOL/AmS: 10 anos, −10%/ano, fase + partidas + liga + histórico.
//
// Diferente dos outros três, este conjunto NÃO tem tabela de lançamentos: a
// pontuação é derivada das partidas já gravadas, na leitura. Ver a justificativa
// em GameRepository.observarRankingMundial.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Linha consolidada do Ranking Mundial, com a decomposição por origem dos pontos.
 * Não vem de tabela: é montada na leitura, a partir das partidas da janela.
 */
data class RankingMundialLinha(
    val timeId: Int,
    val nomeTime: String,
    val escudoRes: String,
    val pais: String,
    val pontos: Double = 0.0,
    val pontosLiga: Double = 0.0,
    val pontosCopa: Double = 0.0,
    val pontosInternacional: Double = 0.0,
    // Partidas que pontuaram na janela (vitórias + empates). As derrotas não
    // somam pontos, então não entram nesta contagem.
    val jogos: Int = 0
)

/** Os quatro níveis de força de liga da IFFHS. O empate vale metade da vitória. */
enum class NivelLiga(val nivel: Int, val pontosVitoria: Double) {
    NIVEL_4(4, 4.0),
    NIVEL_3(3, 3.0),
    NIVEL_2(2, 2.0),
    NIVEL_1(1, 1.0);

    companion object {
        /** Nível a partir do número, limitado à faixa 1..4. */
        fun de(nivel: Int): NivelLiga = when {
            nivel >= 4 -> NIVEL_4
            nivel == 3 -> NIVEL_3
            nivel == 2 -> NIVEL_2
            else       -> NIVEL_1
        }
    }
}

object RankingMundial {

    /** Janela móvel: os últimos 12 meses de competições oficiais. */
    const val JANELA_MESES = 12

    // ── Competições internacionais ───────────────────────────────────────
    // Nenhuma existe no jogo ainda (TipoCampeonato.CONTINENTAL e MUNDIAL_CLUBES
    // estão no enum, mas nenhum campeonato é criado com elas). Os pesos já ficam
    // definidos para pontuarem sozinhos quando existirem.
    const val PESO_MUNDIAL_FIFA   = 21.0
    const val PESO_LIBERTADORES   = 14.0   // e UEFA Champions League
    const val PESO_SUL_AMERICANA  = 12.0   // e UEFA Europa League
    const val PESO_CONTINENTAL_DEMAIS = 9.0 // CAF, AFC e CONCACAF

    // ── Níveis das ligas nacionais ───────────────────────────────────────
    // O nível é do PAÍS, e muda anualmente nas regras reais conforme o
    // desempenho internacional dos clubes daquele país. Aqui é fixo, por não
    // existirem competições internacionais para medir esse desempenho.
    private val NIVEL_POR_PAIS = mapOf(
        "brasil"     to 4,   // citados no enunciado como as ligas mais fortes
        "brazil"     to 4,
        "inglaterra" to 4,
        "espanha"    to 4,
        "italia"     to 4,
        "itália"     to 4,
        "argentina"  to 3,   // citada no enunciado como intermediária
        "uruguay"    to 2,   // proposto: não é citado no enunciado
        "uruguai"    to 2
    )

    /** Nível da PRIMEIRA divisão do país. Países não listados caem no nível 1. */
    fun nivelDoPais(pais: String): NivelLiga =
        NivelLiga.de(NIVEL_POR_PAIS[pais.trim().lowercase()] ?: 1)

    /**
     * Profundidade da divisão: 1 para a elite, 2 para a segunda e assim por diante.
     * Sai do tipo do campeonato, que já codifica isso — a Premier League é
     * NACIONAL_DIVISAO1 com pais "Inglaterra", e a Championship é NACIONAL_DIVISAO2.
     * Devolve null para o que não é liga.
     */
    fun profundidadeDaDivisao(tipo: TipoCampeonato): Int? = when (tipo) {
        TipoCampeonato.NACIONAL_DIVISAO1,
        TipoCampeonato.EXTRANGEIRO_DIVISAO1 -> 1
        TipoCampeonato.NACIONAL_DIVISAO2,
        TipoCampeonato.EXTRANGEIRO_DIVISAO2 -> 2
        TipoCampeonato.NACIONAL_DIVISAO3    -> 3
        TipoCampeonato.NACIONAL_DIVISAO4    -> 4
        else                                -> null
    }

    /**
     * Nível de uma liga: o nível do país cai um degrau por divisão abaixo da elite,
     * com piso no nível 1. Então no Brasil (país nível 4) a Série A vale 4, a B 3,
     * a C 2 e a D 1; na Inglaterra a Premier vale 4 e a Championship 3; na Argentina
     * (nível 3) a Primera vale 3 e a segunda 2.
     */
    fun nivelDaLiga(pais: String, tipo: TipoCampeonato): NivelLiga? {
        val profundidade = profundidadeDaDivisao(tipo) ?: return null
        return NivelLiga.de(nivelDoPais(pais).nivel - (profundidade - 1))
    }


    // ── Peso de uma partida ──────────────────────────────────────────────
    /**
     * Pontos que uma VITÓRIA naquela partida vale, ou null quando a partida não
     * pontua. O empate sai disso por [pontosPorEmpate].
     *
     * As copas nacionais pontuam em **todas as fases**, não só das oitavas em
     * diante: é o que põe no ranking o clube de Série C ou D que cai cedo na copa.
     *
     * Já supercopas, Troféu dos Campeões e estaduais **não pontuam**, e o amistoso
     * e os jogos de seleções também não — ver a lista no fim deste `when`.
     *
     * Antes as copas pontuavam só das oitavas em diante, e supercopas, o Troféu
     * dos Campeões e os estaduais não pontuavam, por serem "torneios secundários"
     * na leitura do enunciado da IFFHS. Isso deixava o ranking mundial cego para
     * boa parte do calendário: o clube eliminado na Primeira Fase da Copa do
     * Brasil não somava nada por aqueles jogos, e os clubes que não disputam liga
     * nenhuma (o pool das divisões 90/91/92) dependiam de uma vaga continental
     * para sequer aparecer. Agora cada clube soma tudo que disputa, com o peso do
     * torneio em que o jogo aconteceu.
     */
    fun pesoDaVitoria(
        tipo: TipoCampeonato,
        paisCampeonato: String,
        nomeCampeonato: String,
        fase: String?
    ): Double? = when (tipo) {
        TipoCampeonato.MUNDIAL_CLUBES -> PESO_MUNDIAL_FIFA

        // Copa Intercontinental: decide o título mundial entre os campeões da
        // Champions e da Libertadores, então vale o mesmo peso da final do
        // Mundial da FIFA, de quem é a antecessora direta.
        TipoCampeonato.INTERCONTINENTAL -> PESO_MUNDIAL_FIFA

        TipoCampeonato.CONTINENTAL -> pesoContinentalPorNome(nomeCampeonato)

        // Copa nacional: todas as fases, e vale o nível da liga do PAÍS, não o da
        // divisão do clube — uma vitória na Copa do Brasil vale os mesmos 4 pontos
        // de uma vitória no Brasileirão, inclusive na Primeira Fase. É o que põe no
        // ranking o clube de Série C ou D que cai cedo na copa.
        //
        // Supercopa e Troféu dos Campeões são jogo único entre campeões, decidido
        // em campo e oficial: mesmo peso das copas do país.
        TipoCampeonato.COPA_NACIONAL -> nivelDoPais(paisCampeonato).pontosVitoria

        TipoCampeonato.NACIONAL_DIVISAO1,
        TipoCampeonato.NACIONAL_DIVISAO2,
        TipoCampeonato.NACIONAL_DIVISAO3,
        TipoCampeonato.NACIONAL_DIVISAO4,
        TipoCampeonato.EXTRANGEIRO_DIVISAO1,
        TipoCampeonato.EXTRANGEIRO_DIVISAO2 ->
            nivelDaLiga(paisCampeonato, tipo)?.pontosVitoria

        // Não pontuam, por decisão de produto registrada em ranking_mundial.md:
        // supercopa e Troféu dos Campeões são jogo único entre campeões — o
        // "torneio secundário" que o enunciado da IFFHS manda ignorar, como a Copa
        // da Liga —, e o enunciado também exclui os campeonatos estaduais. O
        // amistoso não é competição oficial, e seleções não são clube.
        TipoCampeonato.SUPERCOPA,
        TipoCampeonato.TROFEU_CAMPEOES,
        TipoCampeonato.ESTADUAL,
        TipoCampeonato.SELECOES,
        TipoCampeonato.AMISTOSO -> null
    }

    private fun pesoContinentalPorNome(nome: String): Double {
        val n = nome.lowercase()
        // CAF, AFC e CONCACAF são testadas PRIMEIRO porque os nomes colidem: a
        // "CAF Champions League" contém "champions league" e, sem esta ordem, cairia
        // no peso 14 da UEFA em vez do 9 que lhe cabe.
        val ehContinentalMenor = n.contains("concacaf") || n.contains("afc") ||
            Regex("\\bcaf\\b").containsMatchIn(n)
        return when {
            ehContinentalMenor -> PESO_CONTINENTAL_DEMAIS
            n.contains("libertadores") || n.contains("champions league") -> PESO_LIBERTADORES
            n.contains("sul-americana") || n.contains("sulamericana") ||
                n.contains("europa league")                             -> PESO_SUL_AMERICANA
            else                                                        -> PESO_CONTINENTAL_DEMAIS
        }
    }

    // ── Soma ─────────────────────────────────────────────────────────────
    /** O empate vale exatamente metade da vitória, em todas as competições. */
    fun pontosPorEmpate(pesoDaVitoria: Double): Double = pesoDaVitoria / 2.0

    /**
     * Pontos de um conjunto de partidas do mesmo peso. A derrota nunca soma nem
     * subtrai: o modelo é puramente de acumulação.
     */
    fun pontos(pesoDaVitoria: Double, vitorias: Int, empates: Int): Double =
        pesoDaVitoria * vitorias + pontosPorEmpate(pesoDaVitoria) * empates
}
