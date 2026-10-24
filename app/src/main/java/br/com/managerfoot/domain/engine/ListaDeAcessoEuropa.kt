package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// Lista de acesso da Fase de Liga da UEFA Europa League — quem entra e por qual
// porta. Ver a seção "Distribuição de vagas por país" de europa_league.md.
//
// São 21 vagas diretas (5 países com 2, 10 países com 1, e Liechtenstein com 1
// pela copa local) e 15 pela qualificação, fechando os 36.
//
// Diferente da Champions, aqui a vaga de cada país tem DUAS portas distintas: o
// campeão da copa nacional e o melhor colocado da liga fora da zona da Champions.
// E o campo depende da Champions: quem já está lá não entra aqui.
// ─────────────────────────────────────────────────────────────────────────────

/** Por que um clube está na Fase de Liga da Europa League. */
enum class PortaDeEntradaEuropa {
    /** Campeão da copa nacional do seu país. */
    COPA_NACIONAL,
    /** Melhor colocado da liga fora da zona da Champions. */
    LIGA,
    /** Veio da fila de qualificação. */
    QUALIFICACAO
}

data class VagaEuropa(
    val candidato: CandidatoChampions,
    val porta: PortaDeEntradaEuropa
)

object ListaDeAcessoEuropa {

    /**
     * Vagas diretas por país. Os 5 primeiros do ranking de associações levam 2, do
     * 6º ao 15º levam 1, e Liechtenstein entra como exceção: o país não tem liga
     * própria — seus clubes jogam no campeonato suíço —, então a copa local é a
     * única porta. Ver a nota em europa_league.md antes de "corrigir" isso.
     */
    val VAGAS_DIRETAS: Map<String, Int> = linkedMapOf(
        // 1º ao 5º do ranking de associações
        "Inglaterra" to 2,
        "Italia"     to 2,
        "Espanha"    to 2,
        "Alemanha"   to 2,
        "França"     to 2,
        // 6º ao 15º
        "Holanda"    to 1,
        "Portugal"   to 1,
        "Bélgica"    to 1,
        "Escócia"    to 1,
        "Áustria"    to 1,
        "Sérvia"     to 1,
        "Turquia"    to 1,
        "Suíça"      to 1,
        "Ucrânia"    to 1,
        "Tchéquia"   to 1,
        // Exceção
        "Liechtenstein" to 1
    )

    val TOTAL_DIRETAS = VAGAS_DIRETAS.values.sum()                      // 21
    val VAGAS_QUALIFICACAO = SorteioChampions.CLUBES - TOTAL_DIRETAS     // 15

    /**
     * Países cuja vaga sai SÓ da copa local, por não terem liga nacional própria.
     * Liechtenstein é o caso real: seus clubes disputam o campeonato suíço.
     */
    val SOMENTE_COPA = setOf("Liechtenstein")

    /** Grafias aceitas, reaproveitando o normalizador da lista da Champions. */
    fun paisCanonico(pais: String): String? =
        ListaDeAcessoChampions.paisCanonico(pais) ?: aliasesProprios[normalizar(pais)]

    /**
     * Países que existem nesta lista mas não na da Champions — Escócia, Sérvia,
     * Suíça e Liechtenstein não têm vaga direta na Champions, então as suas
     * grafias não estão registradas lá.
     */
    private val aliasesProprios: Map<String, String> = buildMap {
        fun registrar(canonico: String, vararg grafias: String) {
            put(normalizar(canonico), canonico)
            grafias.forEach { put(normalizar(it), canonico) }
        }
        registrar("Escócia", "Escocia", "Scotland")
        registrar("Áustria", "Austria")
        registrar("Sérvia", "Servia", "Serbia")
        registrar("Suíça", "Suica", "Switzerland")
        registrar("Liechtenstein")
    }

    private fun normalizar(texto: String): String =
        java.text.Normalizer
            .normalize(texto.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    /**
     * Monta a Fase de Liga da Europa League.
     *
     * @param candidatos todos os clubes europeus, com a colocação na liga quando
     *        o país tem liga completa no jogo.
     * @param campeoesDaCopa clube campeão da copa nacional, por país canônico.
     *        Vazio na primeira temporada, quando nenhuma copa encerrou ainda.
     * @param ocupados clubes já classificados à Champions. **Obrigatório na
     *        prática:** um clube não disputa as duas competições, e a Europa
     *        League é montada depois.
     *
     * Ordem de preenchimento, por país: primeiro a vaga da copa, depois a da liga.
     * Se o campeão da copa já estiver na Champions (ou não existir), a vaga desce
     * para o colocado seguinte da liga — sem isso o país entraria com um time a
     * menos, ou o mesmo clube ocuparia duas vagas.
     *
     * Devolve menos de 36 vagas quando não há clubes suficientes — o sinal de que
     * a competição não deve ser criada nesta temporada.
     */
    fun montar(
        candidatos: List<CandidatoChampions>,
        campeoesDaCopa: Map<String, Int> = emptyMap(),
        ocupados: Set<Int> = emptySet()
    ): List<VagaEuropa> {
        val vagas = mutableListOf<VagaEuropa>()
        val escolhidos = HashSet(ocupados)
        val porPais = HashMap<String, Int>()

        val disponiveis = candidatos.filter { it.timeId !in ocupados }
        val porPaisCanonico = disponiveis
            .mapNotNull { c -> paisCanonico(c.pais)?.let { it to c } }
            .groupBy({ it.first }, { it.second })

        for ((pais, cotaDoPais) in VAGAS_DIRETAS) {
            val fila = porPaisCanonico[pais].orEmpty()
                .sortedWith(ListaDeAcessoChampions.ORDEM)
            var usadas = 0

            // Porta 1: campeão da copa nacional, se ainda estiver livre.
            val campeaoCopa = campeoesDaCopa[pais]
                ?.let { id -> fila.firstOrNull { it.timeId == id } }
            if (campeaoCopa != null && campeaoCopa.timeId !in escolhidos) {
                vagas += VagaEuropa(campeaoCopa, PortaDeEntradaEuropa.COPA_NACIONAL)
                escolhidos += campeaoCopa.timeId
                usadas++
            }

            // País sem liga própria só tem a porta da copa.
            if (pais in SOMENTE_COPA) {
                porPais[pais] = usadas
                continue
            }

            // Porta 2: os melhores da liga ainda livres completam a cota. É aqui
            // que a vaga "desce" quando o campeão da copa já estava na Champions.
            for (candidato in fila) {
                if (usadas >= cotaDoPais) break
                if (candidato.timeId in escolhidos) continue
                vagas += VagaEuropa(candidato, PortaDeEntradaEuropa.LIGA)
                escolhidos += candidato.timeId
                usadas++
            }
            porPais[pais] = usadas
        }

        // ── Qualificação: as ligas menores primeiro, com o teto por país ──
        // Mesmo critério da Champions: sem essa ordem as 15 sobras iriam para os
        // reservas dos países grandes e o torneio perderia o caráter continental.
        val restantes = disponiveis
            .filter { it.timeId !in escolhidos }
            .sortedWith(ListaDeAcessoChampions.ORDEM)
        val (ligasMenores, ligasDaTabela) = restantes.partition { paisCanonico(it.pais) == null }

        for (candidato in ligasMenores + ligasDaTabela) {
            if (vagas.size >= SorteioChampions.CLUBES) break
            val pais = paisCanonico(candidato.pais) ?: candidato.pais.trim()
            val jaDoPais = porPais[pais] ?: 0
            if (jaDoPais >= SorteioChampions.MAX_CLUBES_POR_PAIS) continue
            vagas += VagaEuropa(candidato, PortaDeEntradaEuropa.QUALIFICACAO)
            escolhidos += candidato.timeId
            porPais[pais] = jaDoPais + 1
        }
        return vagas
    }

    /** Os clubes da Fase de Liga no formato que [SorteioChampions] consome. */
    fun paraSorteio(vagas: List<VagaEuropa>): List<ClubeChampions> =
        ListaDeAcessoChampions.paraSorteio(
            vagas.map { VagaChampions(it.candidato, PortaDeEntrada.DIRETA) }
        )
}
