package br.com.managerfoot.domain.model

// ─────────────────────────────────────────────────────────────────────────────
// Mapa país → continente, compartilhado entre domínio e UI.
//
// Serve tanto aos recortes da tela de ranking quanto ao escopo das regras de
// pontuação por colocação, que são definidas por continente: a América do Sul
// usa as regras CONMEBOL (ver ranking_conmebol.md); os demais continentes terão
// os seus próprios conjuntos.
// ─────────────────────────────────────────────────────────────────────────────
object Continentes {

    const val AMERICA_DO_SUL   = "América do Sul"
    const val AMERICA_DO_NORTE = "América do Norte"
    const val EUROPA           = "Europa"
    const val OUTROS           = "Outros"

    // Grafias aceitas por continente, em minúsculas e sem espaços nas pontas.
    // Cobre as dez associações da CONMEBOL, inclusive as que ainda não têm
    // clubes no seed, para que um país novo entre no ranking sem outra alteração.
    private val PAISES_AMERICA_DO_SUL = setOf(
        "brasil", "brazil",
        "argentina",
        "uruguay", "uruguai",
        "chile",
        "colombia", "colômbia",
        "paraguay", "paraguai",
        "peru", "perú",
        "equador", "ecuador",
        "bolivia", "bolívia",
        "venezuela"
    )

    private val PAISES_EUROPA = setOf(
        "espanha", "italia", "itália", "inglaterra",
        "portugal", "franca", "frança", "alemanha", "holanda", "paises baixos", "países baixos",
        "escocia", "escócia", "belgica", "bélgica", "turquia"
    )

    private val PAISES_AMERICA_DO_NORTE = setOf(
        "estados unidos", "eua", "usa", "mexico", "méxico", "canada", "canadá"
    )

    fun dePais(pais: String): String = when (pais.trim().lowercase()) {
        in PAISES_AMERICA_DO_SUL   -> AMERICA_DO_SUL
        in PAISES_EUROPA           -> EUROPA
        in PAISES_AMERICA_DO_NORTE -> AMERICA_DO_NORTE
        else                       -> OUTROS
    }

    fun ehAmericaDoSul(pais: String): Boolean = dePais(pais) == AMERICA_DO_SUL

    // Rótulo canônico do Brasil, usado pelos recortes POR PAÍS. O conjunto de
    // regras do ranking por colocação do Brasil é o RNC da CBF (ver
    // ranking_brasileiro.md), independente das regras CONMEBOL.
    const val BRASIL = "Brasil"

    private val GRAFIAS_BRASIL = setOf("brasil", "brazil")

    fun ehBrasil(pais: String): Boolean = pais.trim().lowercase() in GRAFIAS_BRASIL
}
