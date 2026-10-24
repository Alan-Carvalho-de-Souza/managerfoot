package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// Lista de acesso da CONMEBOL Libertadores. Ver regras_libertadores.md.
//
// 47 clubes: 45 vagas nacionais + 2 dos campeões vigentes da Libertadores e da
// Copa Sul-Americana. Das 45, 26 entram direto na fase de grupos e 19 na
// Pré-Libertadores, de onde só 4 sobrevivem.
//
// A aritmética fecha exatamente, e as duas vagas de campeão vigente são
// ESTRUTURAIS: sem elas a fase de grupos teria 30 clubes em vez de 32.
//
//     grupos = 26 diretos + 4 da Pré-Libertadores + 2 campeões = 32
//     total  = 45 nacionais + 2 campeões                       = 47
// ─────────────────────────────────────────────────────────────────────────────

/** Mesmo conjunto de dados de um candidato continental. */
typealias CandidatoLibertadores = CandidatoChampions

/** Por onde o clube entra na Libertadores. */
enum class PortaDaLibertadores {
    /** Direto na fase de grupos, pela colocação na liga nacional. */
    GRUPOS,
    /** Pela Pré-Libertadores, as três fases eliminatórias prévias. */
    PRE_LIBERTADORES,
    /** Campeão vigente da Libertadores ou da Copa Sul-Americana. */
    CAMPEAO_VIGENTE
}

data class VagaLibertadores(
    val candidato: CandidatoLibertadores,
    val porta: PortaDaLibertadores
)

/** Cota de um país: quantas vagas diretas nos grupos e quantas na Pré. */
data class CotaPais(val grupos: Int, val pre: Int) {
    val total: Int get() = grupos + pre
}

object ListaDeAcessoLibertadores {

    const val CLUBES_NA_FASE_DE_GRUPOS = 32
    const val GRUPOS = 8
    const val CLUBES_POR_GRUPO = 4
    const val AVANCAM_POR_GRUPO = 2

    /** Vagas dos campeões vigentes: Libertadores e Copa Sul-Americana. */
    const val VAGAS_CAMPEOES_VIGENTES = 2

    /** Sobreviventes da Pré-Libertadores que chegam aos grupos. */
    const val SOBREVIVENTES_DA_PRE = 4

    /**
     * Cotas oficiais por país. Brasil e Argentina têm 5 vagas diretas; os outros
     * oito países têm 2. A soma dá 45 vagas nacionais, 26 diretas e 19 na Pré.
     */
    val COTAS_OFICIAIS: Map<String, CotaPais> = linkedMapOf(
        "Brasil"    to CotaPais(grupos = 5, pre = 2),
        "Argentina" to CotaPais(grupos = 5, pre = 1),
        "Bolívia"   to CotaPais(grupos = 2, pre = 2),
        "Chile"     to CotaPais(grupos = 2, pre = 2),
        "Colômbia"  to CotaPais(grupos = 2, pre = 2),
        "Equador"   to CotaPais(grupos = 2, pre = 2),
        "Paraguai"  to CotaPais(grupos = 2, pre = 2),
        "Peru"      to CotaPais(grupos = 2, pre = 2),
        "Uruguai"   to CotaPais(grupos = 2, pre = 2),
        "Venezuela" to CotaPais(grupos = 2, pre = 2)
    )

    /**
     * Grafias aceitas de cada associacao da CONMEBOL, em minusculas, mapeadas
     * para a chave canonica de [COTAS_OFICIAIS].
     *
     * Existe porque o seed grava "Uruguay": sem esta traducao o pais nao casaria
     * com a chave "Uruguai" e as suas quatro vagas ficariam orfas, redistribuidas
     * entre Brasil e Argentina sem ninguem notar.
     */
    private val GRAFIAS: Map<String, String> = mapOf(
        "brasil"    to "Brasil",    "brazil"   to "Brasil",
        "argentina" to "Argentina",
        "bolivia"   to "Bolívia",   "bolívia"  to "Bolívia",
        "chile"     to "Chile",
        "colombia"  to "Colômbia",  "colômbia" to "Colômbia",
        "equador"   to "Equador",   "ecuador"  to "Equador",
        "paraguai"  to "Paraguai",  "paraguay" to "Paraguai",
        "peru"      to "Peru",      "perú"     to "Peru",
        "uruguai"   to "Uruguai",   "uruguay"  to "Uruguai",
        "venezuela" to "Venezuela"
    )

    /**
     * Rotulo canonico de uma associacao da CONMEBOL, ou null para um pais de
     * fora. E o que casa o `pais` do clube com as chaves de [COTAS_OFICIAIS].
     */
    fun paisCanonico(pais: String): String? = GRAFIAS[pais.trim().lowercase()]

    val VAGAS_NACIONAIS   = COTAS_OFICIAIS.values.sumOf { it.total }   // 45
    val DIRETAS_NOS_GRUPOS = COTAS_OFICIAIS.values.sumOf { it.grupos } // 26
    val NA_PRE_LIBERTADORES = COTAS_OFICIAIS.values.sumOf { it.pre }   // 19
    val TOTAL_DE_CLUBES = VAGAS_NACIONAIS + VAGAS_CAMPEOES_VIGENTES    // 47

    // ── Estrutura da Pré-Libertadores ───────────────────────────────────
    /**
     * As três fases prévias, derivadas de trás para frente: a Fase 3 precisa de 8
     * clubes para produzir os 4 que vão aos grupos; a Fase 2 precisa de 16 para
     * produzir esses 8; e a Fase 1 entrega 3 dos 16, com os outros 13 entrando
     * direto na Fase 2.
     *
     *     Fase 1:  6 clubes → 3 vencedores
     *     Fase 2:  3 + 13   = 16 clubes → 8 vencedores
     *     Fase 3:  8 clubes → 4 vencedores → fase de grupos
     *
     * Note que 19 é ímpar, mas **nenhum clube precisa de bye**: a entrada
     * escalonada absorve a sobra, porque só 6 começam na Fase 1 e os outros 13
     * entram na Fase 2.
     */
    const val PRE_FASE1_CLUBES = 6
    const val PRE_FASE2_ENTRAM_DIRETO = 13
    const val PRE_FASE2_CLUBES = 16
    const val PRE_FASE3_CLUBES = 8

    val PRE_FASES = listOf("Fase 1", "Fase 2", "Fase 3")

    // ── Redistribuição quando faltam associações ────────────────────────
    /**
     * Cotas efetivas, dadas as associações que têm clubes disponíveis.
     *
     * O jogo tem clubes de apenas 3 das 10 associações da CONMEBOL, o que deixaria
     * 28 das 45 vagas órfãs. Em vez de encolher o torneio, as vagas órfãs são
     * redistribuídas entre os países presentes, **proporcionalmente às suas cotas
     * oficiais** — o Brasil, que tem a maior cota, recebe a maior fatia.
     *
     * A função é genérica de propósito: quando os sete países que faltam ganharem
     * clubes, ela devolve as cotas oficiais sem alteração nenhuma de código.
     */
    fun cotasEfetivas(paisesComClubes: Set<String>): Map<String, CotaPais> {
        val presentes = COTAS_OFICIAIS.filterKeys { it in paisesComClubes }
        if (presentes.isEmpty()) return emptyMap()
        if (presentes.size == COTAS_OFICIAIS.size) return COTAS_OFICIAIS

        val pesoTotal = presentes.values.sumOf { it.total }
        // Reparte 26 vagas de grupo e 19 de Pré proporcionalmente ao peso de cada
        // país presente, distribuindo o resto pelos maiores pesos.
        val grupos = repartir(DIRETAS_NOS_GRUPOS, presentes, pesoTotal)
        val pre    = repartir(NA_PRE_LIBERTADORES, presentes, pesoTotal)
        return presentes.keys.associateWith { pais ->
            CotaPais(grupos = grupos.getValue(pais), pre = pre.getValue(pais))
        }
    }

    /** Reparte [total] vagas proporcionalmente, sem perder nenhuma no arredondamento. */
    private fun repartir(
        total: Int,
        presentes: Map<String, CotaPais>,
        pesoTotal: Int
    ): Map<String, Int> {
        val exato = presentes.mapValues { (_, cota) -> total * cota.total.toDouble() / pesoTotal }
        val base = exato.mapValues { it.value.toInt() }.toMutableMap()
        var sobra = total - base.values.sum()
        // A sobra do arredondamento vai para quem tem a maior parte fracionária.
        presentes.keys
            .sortedByDescending { exato.getValue(it) - base.getValue(it) }
            .forEach { pais ->
                if (sobra <= 0) return@forEach
                base[pais] = base.getValue(pais) + 1
                sobra--
            }
        return base
    }

    // ── Montagem ────────────────────────────────────────────────────────
    /**
     * Monta o campo da Libertadores.
     *
     * @param candidatos clubes sul-americanos, com a colocação na liga nacional
     *        quando o país tem liga completa no jogo.
     * @param campeaoLibertadores clube campeão vigente da Libertadores, ou null.
     * @param campeaoSulAmericana clube campeão vigente da Copa Sul-Americana.
     *
     * Os campeões vigentes entram direto nos grupos, antes de tudo. Se um deles já
     * se classificaria pela liga, a sua vaga nacional **desce para o colocado
     * seguinte** do mesmo país — sem isso o país entraria com um clube a menos e os
     * 32 não fechariam. É a mesma regra de queda que a Europa League usa para o
     * campeão da copa.
     */
    fun montar(
        candidatos: List<CandidatoLibertadores>,
        campeaoLibertadores: Int? = null,
        campeaoSulAmericana: Int? = null,
        /**
         * Quando true, as vagas de campeão vigente que não têm titular descem para
         * os próximos colocados, de modo que a fase de grupos feche os 32. Ver o
         * bloco no fim desta função.
         */
        fecharFaseDeGrupos: Boolean = false
    ): List<VagaLibertadores> {
        val vagas = mutableListOf<VagaLibertadores>()
        val escolhidos = HashSet<Int>()

        val porPais = candidatos
            .groupBy { it.pais.trim() }
            .mapValues { (_, lista) -> lista.sortedWith(ListaDeAcessoChampions.ORDEM) }

        // ── Campeões vigentes: direto nos grupos ────────────────────────
        for (id in listOfNotNull(campeaoLibertadores, campeaoSulAmericana).distinct()) {
            val clube = candidatos.firstOrNull { it.timeId == id } ?: continue
            vagas += VagaLibertadores(clube, PortaDaLibertadores.CAMPEAO_VIGENTE)
            escolhidos += clube.timeId
        }

        // ── Vagas nacionais, país por país ──────────────────────────────
        val cotas = cotasEfetivas(porPais.keys)
        for ((pais, cota) in cotas) {
            val fila = porPais[pais].orEmpty().filter { it.timeId !in escolhidos }
            // Primeiro as vagas de grupo, depois as da Pré — a fila já está na
            // ordem de preferência, então os melhores pegam as vagas diretas.
            fila.take(cota.grupos).forEach {
                vagas += VagaLibertadores(it, PortaDaLibertadores.GRUPOS)
                escolhidos += it.timeId
            }
            fila.drop(cota.grupos).take(cota.pre).forEach {
                vagas += VagaLibertadores(it, PortaDaLibertadores.PRE_LIBERTADORES)
                escolhidos += it.timeId
            }
        }

        // ── Vagas de campeão vigente não ocupadas ───────────────────────
        // As duas vagas de campeão vigente são ESTRUTURAIS: 26 diretas + 4 da Pré
        // + 2 campeões = 32. Mas elas podem não ter titular — na primeira
        // temporada não houve Libertadores ainda, e a Copa Sul-Americana pode nem
        // existir no jogo. Aí a vaga desce para o próximo colocado, que é a mesma
        // regra de queda que vale quando o campeão já se classificou pela liga.
        //
        // Sem isso a fase de grupos teria 30 ou 31 clubes em vez de 32, e a
        // competição simplesmente não poderia ser criada. Fica atrás de
        // [fecharFaseDeGrupos] porque quem só quer conferir as cotas precisa das 45
        // vagas nacionais limpas, sem o fechamento.
        val faltamNosGrupos = if (!fecharFaseDeGrupos) 0
        else CLUBES_NA_FASE_DE_GRUPOS - SOBREVIVENTES_DA_PRE -
            vagas.count {
                it.porta == PortaDaLibertadores.GRUPOS ||
                    it.porta == PortaDaLibertadores.CAMPEAO_VIGENTE
            }
        if (faltamNosGrupos > 0) {
            candidatos
                .filter { it.timeId !in escolhidos }
                .sortedWith(ListaDeAcessoChampions.ORDEM)
                .take(faltamNosGrupos)
                .forEach {
                    vagas += VagaLibertadores(it, PortaDaLibertadores.GRUPOS)
                    escolhidos += it.timeId
                }
        }
        return vagas
    }

    /** Os clubes que entram direto na fase de grupos (nacionais + campeões). */
    fun diretosNosGrupos(vagas: List<VagaLibertadores>): List<CandidatoLibertadores> =
        vagas.filter {
            it.porta == PortaDaLibertadores.GRUPOS ||
                it.porta == PortaDaLibertadores.CAMPEAO_VIGENTE
        }.map { it.candidato }

    /** Os clubes que começam na Pré-Libertadores. */
    fun naPreLibertadores(vagas: List<VagaLibertadores>): List<CandidatoLibertadores> =
        vagas.filter { it.porta == PortaDaLibertadores.PRE_LIBERTADORES }.map { it.candidato }
}
