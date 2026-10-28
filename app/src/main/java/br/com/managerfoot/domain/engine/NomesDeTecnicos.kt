package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// Nomes para os técnicos que entram no mercado quando um veterano se aposenta.
//
// O seed tem 363 técnicos reais e nada nunca criava um novo, então sem isto a
// aposentadoria esvaziaria o mundo: cada carreira encerrada seria um clube a
// menos com comando. Cada aposentado gera um substituto da MESMA
// nacionalidade, o que mantém a distribuição por país estável ao longo das
// temporadas — um clube italiano continua achando técnicos italianos em 2050.
// ─────────────────────────────────────────────────────────────────────────────
object NomesDeTecnicos {

    private val BRASIL = listOf(
        "André", "Bruno", "Caio", "Diego", "Eduardo", "Fábio", "Gustavo", "Henrique",
        "Jorge", "Leandro", "Marcelo", "Paulo", "Rafael", "Ricardo", "Rodrigo", "Vinícius"
    ) to listOf(
        "Almeida", "Barbosa", "Cardoso", "Dias", "Ferreira", "Gomes", "Lima", "Macedo",
        "Nogueira", "Oliveira", "Pereira", "Queiroz", "Ribeiro", "Santos", "Teixeira", "Vieira"
    )

    private val ARGENTINA = listOf(
        "Agustín", "Diego", "Emiliano", "Facundo", "Gonzalo", "Hernán", "Ignacio", "Javier",
        "Leandro", "Martín", "Nicolás", "Pablo", "Ramiro", "Sebastián", "Tomás", "Walter"
    ) to listOf(
        "Acosta", "Benítez", "Cabrera", "Domínguez", "Escobar", "Fernández", "Gutiérrez",
        "Herrera", "Juárez", "Lamela", "Medina", "Ortega", "Paredes", "Quiroga", "Rossi", "Sosa"
    )

    private val ESPANHA = listOf(
        "Alberto", "Borja", "Carlos", "David", "Enrique", "Fernando", "Gonzalo", "Iker",
        "Javier", "Luis", "Manuel", "Óscar", "Pedro", "Rubén", "Sergio", "Víctor"
    ) to listOf(
        "Alonso", "Bermúdez", "Castro", "Delgado", "Esteban", "Fuentes", "Garrido", "Hidalgo",
        "Iglesias", "Jiménez", "Lozano", "Molina", "Navarro", "Peña", "Ramos", "Serrano"
    )

    private val ITALIA = listOf(
        "Alessandro", "Andrea", "Claudio", "Davide", "Emanuele", "Fabio", "Giuseppe", "Lorenzo",
        "Marco", "Matteo", "Nicola", "Paolo", "Riccardo", "Simone", "Stefano", "Tommaso"
    ) to listOf(
        "Barzaghi", "Colombo", "De Santis", "Ferrara", "Gallo", "Lombardi", "Marchetti",
        "Negri", "Orsini", "Pellegrini", "Riva", "Sacchi", "Tosi", "Ventura", "Zanetti", "Bruno"
    )

    private val INGLATERRA = listOf(
        "Adam", "Ben", "Callum", "Daniel", "Edward", "Frank", "George", "Harry",
        "Jack", "Kieran", "Liam", "Mark", "Nathan", "Oliver", "Ryan", "Tom"
    ) to listOf(
        "Ashworth", "Bennett", "Carver", "Dawson", "Ellis", "Fletcher", "Grainger", "Hollis",
        "Ingram", "Jarvis", "Keane", "Lawson", "Mercer", "Norton", "Preston", "Rowley"
    )

    private val GENERICO = listOf(
        "Aleksandar", "Bogdan", "Christian", "Dimitri", "Erik", "Filip", "Hugo", "Ivan",
        "Jonas", "Karl", "Lukas", "Milan", "Niklas", "Petar", "Stefan", "Viktor"
    ) to listOf(
        "Anders", "Brandt", "Cerny", "Dvorak", "Ekdal", "Forsberg", "Halle", "Ilic",
        "Jansen", "Kovac", "Lind", "Mertens", "Novak", "Pavlov", "Rask", "Vogel"
    )

    private fun poolDe(nacionalidade: String): Pair<List<String>, List<String>> =
        when (nacionalidade.lowercase()) {
            "brasil", "portugal" -> BRASIL
            "argentina", "uruguai", "uruguay", "chile", "colombia", "colômbia",
            "paraguai", "peru", "bolivia", "bolívia", "equador", "venezuela", "mexico", "méxico" -> ARGENTINA
            "espanha" -> ESPANHA
            "italia", "itália" -> ITALIA
            "inglaterra", "escocia", "escócia", "irlanda", "pais de gales", "país de gales",
            "estados unidos" -> INGLATERRA
            else -> GENERICO
        }

    /** Nome completo e abreviado ("M. Ribeiro") de um técnico novo. */
    fun gerar(nacionalidade: String, sorteio: (Int) -> Int): Pair<String, String> {
        val (nomes, sobrenomes) = poolDe(nacionalidade)
        val nome = nomes[sorteio(nomes.size).coerceIn(0, nomes.size - 1)]
        val sobrenome = sobrenomes[sorteio(sobrenomes.size).coerceIn(0, sobrenomes.size - 1)]
        return "$nome $sobrenome" to "${nome.first()}. $sobrenome"
    }
}
