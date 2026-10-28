package br.com.managerfoot.presentation.ui.util

import br.com.managerfoot.data.datasource.SaveState
import br.com.managerfoot.domain.engine.MotorCampeonato

// ─────────────────────────────────────────────────────────────────────────────
// De qual competição é uma partida, a partir do id do campeonato.
//
// As telas identificavam a competição com cadeias de `when` escritas à mão,
// cada uma conhecendo um subconjunto diferente dos campeonatos do save e todas
// terminando num `else` que chutava o Brasileirão. Resultado: um clube da La
// Liga via "BRASILEIRÃO · RODADA 4" no card de próxima partida, e as copas e
// supercopas europeias apareciam como "Rodada N".
//
// Aqui o save vira um MAPA de id para competição, uma vez só. Um campeonato que
// o save não conhece não tem rótulo inventado: a tela mostra a rodada e pronto.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Como o rótulo da competição é montado.
 *
 * [LIGA] acompanha a rodada ("La Liga · R4"); [COPA] e [CONTINENTAL] acompanham
 * a fase ("Copa do Rei — Oitavas"); [SUPERCOPA] e [AMISTOSO] são jogo único e
 * não têm nem uma nem outra.
 */
enum class EstiloCompeticao { LIGA, COPA, SUPERCOPA, CONTINENTAL, AMISTOSO }

/**
 * @param fasePadrao o que exibir quando a partida vem sem fase. A fase de grupos
 *        da Libertadores e a fase de liga da Champions gravam `fase = null`, que
 *        é justamente o que alimenta a tabela de classificação delas.
 */
data class Competicao(
    val nome: String,
    val estilo: EstiloCompeticao,
    val fasePadrao: String? = null,
    /**
     * Faixa de rodadas em que a fase de grupos é GRAVADA, quando a competição
     * tem uma. Na Libertadores e na Sul-Americana ela é 11..16, e não 1..6,
     * para não colidir com a Pré dentro do mesmo campeonato — ver
     * [MapaDeCompeticoes.rodadaVisivel].
     */
    val rodadasDosGrupos: IntRange? = null
)

class MapaDeCompeticoes(competicoes: Map<Int, Competicao>) {

    // Campeonato ausente no save vale -1, e vários ao mesmo tempo. Sem este
    // filtro todos eles colidiriam numa chave só.
    private val porId: Map<Int, Competicao> = competicoes.filterKeys { it > 0 }

    fun de(campeonatoId: Int): Competicao? = porId[campeonatoId]

    /**
     * Rótulo da faixa do card de próxima partida.
     *
     * O travessão não é enfeite: MatchCard só acrescenta "Rodada N" quando ele
     * NÃO está presente, porque copa e mata-mata se identificam pela fase.
     */
    fun daProximaPartida(campeonatoId: Int, fase: String?, rodada: Int): String {
        val competicao = de(campeonatoId) ?: return rodadaOuPartida(rodada)
        return when (competicao.estilo) {
            EstiloCompeticao.LIGA,
            EstiloCompeticao.SUPERCOPA,
            EstiloCompeticao.AMISTOSO -> competicao.nome

            EstiloCompeticao.COPA,
            EstiloCompeticao.CONTINENTAL -> comFase(competicao, fase)
        }
    }

    /** Rótulo curto da lista de últimos resultados. */
    fun doResultado(campeonatoId: Int, fase: String?, rodada: Int): String {
        val competicao = de(campeonatoId) ?: return rodadaOuPartida(rodada)
        return when (competicao.estilo) {
            EstiloCompeticao.LIGA ->
                if (rodada > 0) "${competicao.nome} · R$rodada" else competicao.nome

            EstiloCompeticao.SUPERCOPA,
            EstiloCompeticao.AMISTOSO -> competicao.nome

            EstiloCompeticao.COPA,
            EstiloCompeticao.CONTINENTAL -> comFase(competicao, fase)
        }
    }

    private fun comFase(competicao: Competicao, fase: String?): String {
        val efetiva = fase ?: competicao.fasePadrao ?: return competicao.nome
        return "${competicao.nome} — $efetiva"
    }

    private fun rodadaOuPartida(rodada: Int): String =
        if (rodada > 0) "Rodada $rodada" else "Partida"

    /**
     * A rodada como o jogador a conta.
     *
     * Para quase toda competição é a própria rodada gravada. Na fase de grupos
     * da Libertadores e da Sul-Americana a gravação começa em 11, porque a Pré
     * ocupa 1..6 no mesmo campeonato — e o jogador via "R11" na primeira rodada
     * dos grupos. A Pré e o mata-mata não passam por aqui: as partidas deles têm
     * fase, e quem nomeia é a fase.
     */
    fun rodadaVisivel(campeonatoId: Int, rodada: Int): Int {
        val faixa = de(campeonatoId)?.rodadasDosGrupos ?: return rodada
        return if (rodada in faixa) rodada - faixa.first + 1 else rodada
    }

    companion object {
        val VAZIO = MapaDeCompeticoes(emptyMap())

        /**
         * Todas as competições que o save conhece, do Brasileirão às supercopas
         * europeias. Acrescentar um campeonato ao SaveState é acrescentar uma
         * linha aqui — e aí todas as telas passam a nomeá-lo.
         */
        private fun faixaDeGrupos(formato: MotorCampeonato.FormatoLibertadores): IntRange =
            formato.rodadaBaseGrupos until formato.rodadaBaseGrupos + formato.rodadasGrupos

        fun de(save: SaveState?): MapaDeCompeticoes {
            if (save == null) return VAZIO
            val liga = EstiloCompeticao.LIGA
            val copa = EstiloCompeticao.COPA
            val supercopa = EstiloCompeticao.SUPERCOPA
            val continental = EstiloCompeticao.CONTINENTAL
            return MapaDeCompeticoes(
                mapOf(
                    // ── Ligas ────────────────────────────────────────────────
                    save.campeonatoAId            to Competicao("Série A", liga),
                    save.campeonatoBId            to Competicao("Série B", liga),
                    save.campeonatoCId            to Competicao("Série C", liga),
                    save.campeonatoDId            to Competicao("Série D", liga),
                    save.campeonatoArgAId         to Competicao("Apertura", liga),
                    save.campeonatoArgClausuraId  to Competicao("Clausura", liga),
                    save.campeonatoArgBId         to Competicao("Primera Nacional", liga),
                    save.campeonatoUruAperturaId  to Competicao("Apertura (URU)", liga),
                    save.campeonatoUruClausuraId  to Competicao("Clausura (URU)", liga),
                    save.campeonatoUruIntermedId  to Competicao("Intermedio", liga),
                    save.campeonatoUruBId         to Competicao("Segunda Div. (URU)", liga),
                    save.campeonatoUruBCompetId   to Competicao("Competencia", liga),
                    save.campeonatoPremierId      to Competicao("Premier League", liga),
                    save.campeonatoChampionshipId to Competicao("EFL Championship", liga),
                    save.campeonatoLaLigaId       to Competicao("La Liga", liga),
                    save.campeonatoSerieAId       to Competicao("Serie A", liga),

                    // ── Copas nacionais ──────────────────────────────────────
                    save.copaId                   to Competicao("Copa do Brasil", copa, "Copa"),
                    save.copaArgId                to Competicao("Copa da Argentina", copa, "Copa"),
                    save.copaInglaterraId         to Competicao("Copa da Inglaterra", copa, "Copa"),
                    save.copaEspanholaId          to Competicao("Copa do Rei", copa, "Copa"),
                    save.copaItalianaId           to Competicao("Coppa Italia", copa, "Copa"),

                    // ── Supercopas (jogo único) ──────────────────────────────
                    save.supercopaId              to Competicao("Supercopa Rei", supercopa),
                    save.supercopaInglaterraId    to Competicao("Supercopa da Inglaterra", supercopa),
                    save.supercopaEspanholaId     to Competicao("Supercopa da Espanha", supercopa),
                    save.supercopaItalianaId      to Competicao("Supercopa da Itália", supercopa),
                    save.supercopaUefaId          to Competicao("Supercopa da UEFA", supercopa),

                    // ── Continentais ─────────────────────────────────────────
                    save.championsId              to Competicao("Champions League", continental, "Fase de Liga"),
                    save.europaLeagueId           to Competicao("Europa League", continental, "Fase de Liga"),
                    save.libertadoresId           to Competicao(
                        "Libertadores", continental, "Fase de Grupos",
                        rodadasDosGrupos = faixaDeGrupos(MotorCampeonato.FORMATO_LIBERTADORES)
                    ),
                    save.sulAmericanaId           to Competicao(
                        "Copa Sul-Americana", continental, "Fase de Grupos",
                        rodadasDosGrupos = faixaDeGrupos(MotorCampeonato.FORMATO_SULAMERICANA)
                    ),
                    save.recopaId                 to Competicao("Recopa Sul-Americana", continental, "Final"),
                    save.intercontinentalId       to Competicao("Copa Intercontinental", continental, "Final"),

                    // ── Amistosos de entressafra ─────────────────────────────
                    save.campeonatoAmistososId    to Competicao("Amistoso", EstiloCompeticao.AMISTOSO)
                )
            )
        }
    }
}
