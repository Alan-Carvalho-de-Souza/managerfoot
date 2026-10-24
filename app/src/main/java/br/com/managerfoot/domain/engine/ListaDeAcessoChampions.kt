package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// Lista de acesso da Fase de Liga da Champions — quem entra e por qual porta.
// Ver a seção "Lista de acesso" de regras_championsleague.md.
//
// São 28 vagas diretas, distribuídas por país, e 8 pela qualificação. A soma da
// tabela de países do enunciado dá 28, e não 29, então a qualificação entrega 8
// clubes em vez de 7 para fechar os 36 — decisão registrada no documento.
//
// Separado do sorteio de propósito: o SorteioChampions recebe 36 clubes prontos
// e não sabe de onde vieram, e esta parte é pura o bastante para ser testada sem
// banco.
// ─────────────────────────────────────────────────────────────────────────────

/** Clube candidato, com o que decide a sua ordem dentro do país. */
data class CandidatoChampions(
    val timeId: Int,
    val nome: String,
    val pais: String,
    /** Coeficiente da UEFA acumulado na janela de 5 temporadas. */
    val coeficiente: Double,
    /** Reputação do clube — único critério disponível na 1ª temporada. */
    val reputacao: Float,
    /**
     * Posição final na liga nacional da temporada que encerra, 1-based, ou null
     * quando o clube não disputou uma liga completa — o caso dos clubes do pool
     * estrangeiro, que existem só para a Champions e não têm campeonato próprio.
     */
    val posicaoLiga: Int? = null
)

/** Por que um clube está na Fase de Liga. */
enum class PortaDeEntrada { DIRETA, QUALIFICACAO }

data class VagaChampions(
    val candidato: CandidatoChampions,
    val porta: PortaDeEntrada
)

object ListaDeAcessoChampions {

    /**
     * Vagas diretas por país. A ordem do mapa não importa para o resultado, mas
     * manter a do documento ajuda a conferir a tabela lado a lado.
     */
    val VAGAS_DIRETAS: Map<String, Int> = linkedMapOf(
        "Inglaterra" to 5,
        "Espanha"    to 5,
        "Alemanha"   to 4,
        "Italia"     to 4,
        "França"     to 3,
        "Holanda"    to 2,
        "Bélgica"    to 1,
        "Tchéquia"   to 1,
        "Turquia"    to 1,
        "Portugal"   to 1,
        "Ucrânia"    to 1
    )

    val TOTAL_DIRETAS = VAGAS_DIRETAS.values.sum()                       // 28
    val VAGAS_QUALIFICACAO = SorteioChampions.CLUBES - TOTAL_DIRETAS      // 8

    /**
     * Grafias aceitas para cada país da lista, em minúsculas e sem acento. O
     * seed e o pool de clubes estrangeiros não escrevem os nomes de forma
     * uniforme ("Italia" sem acento, "Paises Baixos" em vez de "Holanda"), e
     * errar o casamento aqui silenciosamente tiraria um país inteiro do torneio.
     */
    private val ALIASES: Map<String, String> = buildMap {
        fun registrar(canonico: String, vararg grafias: String) {
            put(normalizar(canonico), canonico)
            grafias.forEach { put(normalizar(it), canonico) }
        }
        registrar("Inglaterra", "England")
        registrar("Espanha", "Spain", "España")
        registrar("Alemanha", "Germany", "Deutschland")
        registrar("Italia", "Itália", "Italy")
        registrar("França", "Franca", "France")
        registrar("Holanda", "Países Baixos", "Paises Baixos", "Netherlands")
        registrar("Bélgica", "Belgica", "Belgium")
        registrar("Tchéquia", "Tchequia", "República Tcheca", "Republica Tcheca", "Czechia")
        registrar("Turquia", "Turkey", "Türkiye", "Turkiye")
        registrar("Portugal")
        registrar("Ucrânia", "Ucrania", "Ukraine")
    }

    /** Minúsculas, sem acento e sem espaços nas pontas. */
    private fun normalizar(texto: String): String {
        val semAcento = java.text.Normalizer
            .normalize(texto.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return semAcento
    }

    /** País canônico da lista de acesso, ou null se não estiver nela. */
    fun paisCanonico(pais: String): String? = ALIASES[normalizar(pais)]

    /**
     * Ordem de preferência dentro de um país e na fila da qualificação.
     *
     * A **colocação na liga nacional** manda: quem terminou melhor no campeonato
     * da temporada que encerra pega a vaga, que é como a classificação funciona
     * de verdade. Vale para os países que têm liga completa no jogo — Inglaterra,
     * Espanha e Itália.
     *
     * Os clubes sem liga própria (o pool estrangeiro, que cobre Alemanha, França
     * e as demais associações) não têm colocação, e para eles decide o
     * coeficiente da UEFA, com a reputação desempatando. Na 1ª temporada o
     * coeficiente é zero para todos, então a reputação decide sozinha.
     *
     * Um clube com colocação sempre vem antes de um sem: dentro de um mesmo país
     * isso nunca mistura os dois, e na fila da qualificação significa que um
     * 6º colocado da Premier passa à frente de um clube de pool sem campanha.
     */
    val ORDEM: Comparator<CandidatoChampions> =
        compareBy<CandidatoChampions> { it.posicaoLiga ?: Int.MAX_VALUE }
            .thenByDescending { it.coeficiente }
            .thenByDescending { it.reputacao }
            .thenBy { it.nome }
            .thenBy { it.timeId }

    /**
     * Monta a Fase de Liga a partir de todos os clubes europeus candidatos.
     *
     * Primeiro as vagas diretas, país por país; depois a qualificação.
     *
     * A qualificação é das **ligas menores**, como manda o enunciado ("um
     * mata-mata que reúne mais de 50 clubes de ligas menores"): a fila começa
     * pelos clubes de associações que não têm vaga direta na tabela, e só recorre
     * aos melhores restantes dos países da tabela se aquelas não preencherem as 8
     * vagas. Sem essa ordem, as 8 sobras iriam todas para os reservas de
     * Inglaterra, Espanha e Itália — que têm as maiores reputações do jogo — e a
     * Champions viraria um torneio de três países com 7 clubes cada, justamente o
     * que o documento recusa.
     *
     * A fila respeita [SorteioChampions.MAX_CLUBES_POR_PAIS]: um país com 5 vagas
     * diretas não pode levar mais 3 pela qualificação, porque 8 clubes de um
     * mesmo país tornam o sorteio impossível sob as travas geográficas. Sem esse
     * corte, o torneio seria montado e o sorteio recusaria, sem causa aparente.
     *
     * Devolve menos de 36 vagas quando não há clubes suficientes — é o sinal de
     * que a competição não deve ser criada nesta temporada.
     */
    fun montar(candidatos: List<CandidatoChampions>): List<VagaChampions> {
        val vagas = mutableListOf<VagaChampions>()
        val escolhidos = HashSet<Int>()
        val porPais = HashMap<String, Int>()

        // ── Vagas diretas, país por país ────────────────────────────────
        val porPaisCanonico = candidatos
            .mapNotNull { candidato ->
                paisCanonico(candidato.pais)?.let { it to candidato }
            }
            .groupBy({ it.first }, { it.second })

        for ((pais, vagasDoPais) in VAGAS_DIRETAS) {
            val fila = porPaisCanonico[pais].orEmpty().sortedWith(ORDEM)
            for (candidato in fila.take(vagasDoPais)) {
                vagas += VagaChampions(candidato, PortaDeEntrada.DIRETA)
                escolhidos += candidato.timeId
                porPais[pais] = (porPais[pais] ?: 0) + 1
            }
        }

        // ── Qualificação: as ligas menores primeiro, com o teto por país ──
        val restantes = candidatos
            .filter { it.timeId !in escolhidos }
            .sortedWith(ORDEM)
        val (ligasMenores, ligasDaTabela) = restantes.partition { paisCanonico(it.pais) == null }

        for (candidato in ligasMenores + ligasDaTabela) {
            if (vagas.size >= SorteioChampions.CLUBES) break
            val pais = paisCanonico(candidato.pais) ?: candidato.pais.trim()
            val jaDoPais = porPais[pais] ?: 0
            if (jaDoPais >= SorteioChampions.MAX_CLUBES_POR_PAIS) continue
            vagas += VagaChampions(candidato, PortaDeEntrada.QUALIFICACAO)
            porPais[pais] = jaDoPais + 1
        }
        return vagas
    }

    /**
     * Peso que ordena os potes. O coeficiente da UEFA manda, e a reputação entra
     * só como desempate — multiplicar o coeficiente por [ESCALA_COEFICIENTE] o
     * mantém estritamente dominante, já que a reputação vai de 0 a 100 e o
     * coeficiente de uma janela de 5 temporadas não chega perto de 1000.
     *
     * Sem o desempate, a 1ª temporada quebraria o torneio: todos os coeficientes
     * são zero, e [SorteioChampions.montarPotes] cairia no seu desempate por
     * NOME. Aí os potes sairiam em ordem alfabética, os 5 ingleses viriam juntos
     * no mesmo pote e o sorteio seria recusado por concentração — nenhum clube
     * teria adversários suficientes dentro do próprio pote.
     */
    fun coeficienteDeSorteio(candidato: CandidatoChampions): Double =
        candidato.coeficiente * ESCALA_COEFICIENTE + candidato.reputacao

    const val ESCALA_COEFICIENTE = 1_000.0

    /** Os clubes da Fase de Liga no formato que [SorteioChampions] consome. */
    fun paraSorteio(vagas: List<VagaChampions>): List<ClubeChampions> =
        vagas.map { vaga ->
            ClubeChampions(
                timeId      = vaga.candidato.timeId,
                nome        = vaga.candidato.nome,
                // O país do sorteio é o canônico quando existe, para que duas
                // grafias do mesmo país não burlem as travas geográficas.
                pais        = paisCanonico(vaga.candidato.pais) ?: vaga.candidato.pais.trim(),
                coeficiente = coeficienteDeSorteio(vaga.candidato)
            )
        }
}
