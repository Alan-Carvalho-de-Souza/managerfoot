package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// Lista de acesso da CONMEBOL Copa Sul-Americana. Ver regras_sulamericana.md.
//
// 56 clubes, e eles só fecham COM a Libertadores:
//
//     44 vagas nacionais              (Brasil 6 + Argentina 6 + 8 países × 4)
//    + 4 eliminados da Pré-Libertadores   → entram na fase de grupos
//    + 8 terceiros dos grupos da Libertadores → entram nos playoffs
//    = 56
//
// E as fases encaixam:
//
//     grupos   = 12 (BRA + ARG) + 16 (Primeira Fase) + 4 (da Pré-Libertadores) = 32
//     playoffs = 8 segundos de grupo + 8 terceiros da Libertadores             = 16
//     oitavas  = 8 primeiros de grupo + 8 vencedores dos playoffs              = 16
//
// Os 8 terceiros da Libertadores não são um detalhe: sem eles os playoffs
// teriam 8 clubes em vez de 16 e as oitavas não fechariam.
//
// A diferença estrutural em relação à Libertadores é que aqui a fase prévia é
// NACIONAL: os 4 clubes de cada associação pequena se enfrentam entre si, em
// partida única, e sobram 2 por país.
// ─────────────────────────────────────────────────────────────────────────────

/** Mesmo conjunto de dados de um candidato continental. */
typealias CandidatoSulAmericana = CandidatoChampions

/** Por onde o clube entra na Copa Sul-Americana. */
enum class PortaDaSulAmericana {
    /** Direto na fase de grupos: as 6 vagas do Brasil e as 6 da Argentina. */
    GRUPOS,
    /** Pela Primeira Fase, a preliminar entre clubes do mesmo país. */
    PRIMEIRA_FASE,
    /**
     * Vaga de grupo que não tinha titular — um país sem clubes suficientes — e
     * foi remanejada a outra associação.
     *
     * Tem porta própria pelo mesmo motivo da Libertadores: misturá-la com
     * [GRUPOS] tornaria impossível ler quantas vagas um país recebeu de verdade.
     */
    VAGA_REMANEJADA
}

data class VagaSulAmericana(
    val candidato: CandidatoSulAmericana,
    val porta: PortaDaSulAmericana
)

/** Cota de um país: vagas diretas nos grupos e vagas na Primeira Fase. */
data class CotaSulAmericana(val grupos: Int, val primeiraFase: Int) {
    val total: Int get() = grupos + primeiraFase
}

object ListaDeAcessoSulAmericana {

    const val CLUBES_NA_FASE_DE_GRUPOS = 32
    const val GRUPOS = 8
    const val CLUBES_POR_GRUPO = 4

    /** O 1º de cada grupo vai direto às oitavas; o 2º vai aos playoffs. */
    const val VAO_AS_OITAVAS_POR_GRUPO = 1
    const val VAO_AOS_PLAYOFFS_POR_GRUPO = 1

    /** Clubes que a Libertadores manda para cá, em dois momentos diferentes. */
    const val DA_PRE_LIBERTADORES = 4          // eliminados da Fase 3, vão aos grupos
    const val TERCEIROS_DA_LIBERTADORES = 8    // terceiros de grupo, vão aos playoffs

    /** A Primeira Fase: 32 clubes de 8 países, em partida única, sobram 16. */
    const val PRIMEIRA_FASE_CLUBES = 32
    const val VENCEDORES_DA_PRIMEIRA_FASE = PRIMEIRA_FASE_CLUBES / 2

    /** A fase prévia é intra-país, então a cota de cada país tem de ser PAR. */
    const val CLUBES_POR_PAIS_NA_PRIMEIRA_FASE = 4

    val PRIMEIRA_FASE = "Primeira Fase"

    /**
     * Cotas oficiais. Brasil e Argentina entram com 6 clubes direto na fase de
     * grupos; os outros oito países entram com 4 na Primeira Fase.
     *
     * A ordem é a mesma de [ListaDeAcessoLibertadores.COTAS_OFICIAIS], porque é
     * ela que decide a hierarquia quando uma vaga precisa ser remanejada.
     */
    val COTAS_OFICIAIS: Map<String, CotaSulAmericana> = linkedMapOf(
        "Brasil"    to CotaSulAmericana(grupos = 6, primeiraFase = 0),
        "Argentina" to CotaSulAmericana(grupos = 6, primeiraFase = 0),
        "Bolívia"   to CotaSulAmericana(grupos = 0, primeiraFase = 4),
        "Chile"     to CotaSulAmericana(grupos = 0, primeiraFase = 4),
        "Colômbia"  to CotaSulAmericana(grupos = 0, primeiraFase = 4),
        "Equador"   to CotaSulAmericana(grupos = 0, primeiraFase = 4),
        "Paraguai"  to CotaSulAmericana(grupos = 0, primeiraFase = 4),
        "Peru"      to CotaSulAmericana(grupos = 0, primeiraFase = 4),
        "Uruguai"   to CotaSulAmericana(grupos = 0, primeiraFase = 4),
        "Venezuela" to CotaSulAmericana(grupos = 0, primeiraFase = 4)
    )

    val VAGAS_NACIONAIS = COTAS_OFICIAIS.values.sumOf { it.total }          // 44
    val DIRETAS_NOS_GRUPOS = COTAS_OFICIAIS.values.sumOf { it.grupos }      // 12
    val NA_PRIMEIRA_FASE = COTAS_OFICIAIS.values.sumOf { it.primeiraFase }  // 32
    val TOTAL_DE_CLUBES =
        VAGAS_NACIONAIS + DA_PRE_LIBERTADORES + TERCEIROS_DA_LIBERTADORES   // 56

    /**
     * Rótulo canônico de uma associação da CONMEBOL. Reaproveita a tradução de
     * grafias da Libertadores — o seed grava "Uruguay", e as cotas são indexadas
     * por "Uruguai" — para as duas competições casarem país por país.
     */
    fun paisCanonico(pais: String): String? = ListaDeAcessoLibertadores.paisCanonico(pais)

    // ── Montagem ────────────────────────────────────────────────────────
    /**
     * Monta o campo nacional da Copa Sul-Americana: as 44 vagas que saem das
     * ligas, sem os 12 clubes que a Libertadores manda.
     *
     * @param candidatos clubes sul-americanos elegíveis, com a colocação na liga
     *        nacional quando o país tem liga completa no jogo.
     * @param jaNaLibertadores clubes que estão na Libertadores da MESMA
     *        temporada. Um clube não disputa as duas, então eles são removidos da
     *        fila de cada país antes de qualquer coisa — e é por isso que as duas
     *        competições juntas precisam de 8 clubes por associação pequena.
     * @param fecharCampo quando true, vagas que um país não tem clubes para
     *        ocupar são remanejadas, de modo que os 12 diretos e os 32 da
     *        Primeira Fase fechem. Sem isso o torneio não pode ser criado.
     */
    fun montar(
        candidatos: List<CandidatoSulAmericana>,
        jaNaLibertadores: Set<Int> = emptySet(),
        fecharCampo: Boolean = false
    ): List<VagaSulAmericana> {
        val vagas = mutableListOf<VagaSulAmericana>()
        val escolhidos = HashSet<Int>(jaNaLibertadores)

        val porPais = candidatos
            .filter { it.timeId !in jaNaLibertadores }
            .groupBy { it.pais.trim() }
            .mapValues { (_, lista) -> lista.sortedWith(ListaDeAcessoChampions.ORDEM) }

        // ── As vagas de cada país, na ordem de preferência da liga ──────
        val cotas = COTAS_OFICIAIS.filterKeys { it in porPais.keys }
        for ((pais, cota) in cotas) {
            val fila = porPais[pais].orEmpty().filter { it.timeId !in escolhidos }
            fila.take(cota.grupos).forEach {
                vagas += VagaSulAmericana(it, PortaDaSulAmericana.GRUPOS)
                escolhidos += it.timeId
            }
            fila.drop(cota.grupos).take(cota.primeiraFase).forEach {
                vagas += VagaSulAmericana(it, PortaDaSulAmericana.PRIMEIRA_FASE)
                escolhidos += it.timeId
            }
        }
        if (!fecharCampo) return vagas

        // ── Vagas que ficaram órfãs ─────────────────────────────────────
        // Um país com menos clubes do que a cota deixa vagas por preencher. Elas
        // são oferecidas às outras associações na ordem da cota — Brasil e
        // Argentina primeiro —, uma por país por volta, e cada uma leva o
        // próximo clube da sua fila.
        val ordemDasCotas = cotas.keys.sortedWith(
            compareByDescending<String> { cotas.getValue(it).total }
                .thenBy { COTAS_OFICIAIS.keys.indexOf(it) }
        )
        fun proximoDe(pais: String): CandidatoSulAmericana? =
            porPais[pais].orEmpty().firstOrNull { it.timeId !in escolhidos }

        var faltamGrupos = DIRETAS_NOS_GRUPOS -
            vagas.count {
                it.porta == PortaDaSulAmericana.GRUPOS ||
                    it.porta == PortaDaSulAmericana.VAGA_REMANEJADA
            }
        var volta = 0
        while (faltamGrupos > 0 && volta < CLUBES_NA_FASE_DE_GRUPOS) {
            volta++
            for (pais in ordemDasCotas) {
                if (faltamGrupos == 0) break
                val proximo = proximoDe(pais) ?: continue
                vagas += VagaSulAmericana(proximo, PortaDaSulAmericana.VAGA_REMANEJADA)
                escolhidos += proximo.timeId
                faltamGrupos--
            }
        }

        // A Primeira Fase é intra-país, então as vagas dela andam EM PARES: um
        // país com um número ímpar de clubes nela não teria com quem emparelhar
        // o último, e o sorteio deixaria um clube de fora sem ninguém notar.
        var faltamNaPrimeira = NA_PRIMEIRA_FASE -
            vagas.count { it.porta == PortaDaSulAmericana.PRIMEIRA_FASE }
        volta = 0
        while (faltamNaPrimeira >= 2 && volta < CLUBES_NA_FASE_DE_GRUPOS) {
            volta++
            for (pais in ordemDasCotas) {
                if (faltamNaPrimeira < 2) break
                val dois = porPais[pais].orEmpty().filter { it.timeId !in escolhidos }.take(2)
                if (dois.size < 2) continue
                dois.forEach {
                    vagas += VagaSulAmericana(it, PortaDaSulAmericana.PRIMEIRA_FASE)
                    escolhidos += it.timeId
                }
                faltamNaPrimeira -= 2
            }
        }
        return vagas
    }

    /** Os clubes que entram direto na fase de grupos (as 12 vagas de BRA e ARG). */
    fun diretosNosGrupos(vagas: List<VagaSulAmericana>): List<CandidatoSulAmericana> =
        vagas.filter {
            it.porta == PortaDaSulAmericana.GRUPOS ||
                it.porta == PortaDaSulAmericana.VAGA_REMANEJADA
        }.map { it.candidato }

    /** Os clubes que começam na Primeira Fase. */
    fun naPrimeiraFase(vagas: List<VagaSulAmericana>): List<CandidatoSulAmericana> =
        vagas.filter { it.porta == PortaDaSulAmericana.PRIMEIRA_FASE }.map { it.candidato }
}
