package br.com.managerfoot.domain.model

/**
 * Nacionalidades disponíveis para o técnico do usuário selecionar.
 *
 * Cobre os principais países da Europa, toda a América Latina, América do
 * Norte e Central/Caribe, Ásia, África e Oceania. A ordem prioriza os países
 * mais próximos do contexto do jogo (América do Sul) e segue por região, para
 * exibição num dropdown rolável.
 */
object Nacionalidades {

    // Ordenação alfabética sensível a acentos (pt-BR): "Á" fica junto de "A".
    private val COLLATOR_PT = java.text.Collator.getInstance(java.util.Locale("pt", "BR"))

    // Fonte agrupada por região (facilita manutenção/adições).
    private val PAISES = listOf(
        // ── América do Sul (América Latina) ──
        "Brasil", "Argentina", "Uruguai", "Paraguai", "Chile", "Bolívia",
        "Peru", "Equador", "Colômbia", "Venezuela",
        // ── América do Norte ──
        "México", "Estados Unidos", "Canadá",
        // ── América Central e Caribe ──
        "Costa Rica", "Panamá", "Honduras", "Guatemala", "El Salvador",
        "Nicarágua", "Cuba", "República Dominicana", "Jamaica",
        "Trinidad e Tobago", "Haiti",
        // ── Europa (principais) ──
        "Portugal", "Espanha", "Inglaterra", "Itália", "França", "Alemanha",
        "Holanda", "Bélgica", "Croácia", "Sérvia", "Escócia", "Suíça",
        "Áustria", "Polônia", "Rússia", "Ucrânia", "Grécia", "Turquia",
        "Dinamarca", "Suécia", "Noruega",
        // ── Ásia ──
        "Japão", "Coreia do Sul", "China", "Irã", "Arábia Saudita", "Catar",
        "Emirados Árabes", "Iraque", "Uzbequistão", "Índia", "Tailândia",
        "Vietnã", "Indonésia",
        // ── África ──
        "Marrocos", "Egito", "Argélia", "Tunísia", "Nigéria", "Senegal",
        "Camarões", "Gana", "Costa do Marfim", "África do Sul", "Mali",
        "RD Congo", "Quênia",
        // ── Oceania ──
        "Austrália", "Nova Zelândia", "Fiji"
    )

    /** Lista exibida ao usuário, em ordem alfabética (pt-BR). */
    val TODAS: List<String> = PAISES.sortedWith { a, b -> COLLATOR_PT.compare(a, b) }

    /** Nacionalidade padrão a partir do país do clube escolhido pelo usuário. */
    fun padraoParaPais(pais: String?): String = when (pais) {
        "Argentina" -> "Argentina"
        "Uruguay", "Uruguai" -> "Uruguai"
        "Inglaterra", "England" -> "Inglaterra"
        "Espanha", "Spain" -> "Espanha"
        "Itália", "Italia", "Italy" -> "Itália"
        else -> "Brasil"
    }
}
