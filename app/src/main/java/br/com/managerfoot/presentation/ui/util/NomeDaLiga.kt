package br.com.managerfoot.presentation.ui.util

// ─────────────────────────────────────────────────────────────────────────────
// O nome da liga de um clube a partir do campo `divisao` de Time.
//
// Esta tabela estava copiada em três telas, e as cópias divergiram: a do header
// do Dashboard ainda mapeava 11..12 para o Uruguai, de quando as divisões
// europeias não existiam — um clube da Premier League aparecia como
// "Primera Div. (URU)", e um da La Liga como "Segunda Div. (URU)".
// ─────────────────────────────────────────────────────────────────────────────
object NomeDaLiga {

    /**
     * Nome da liga da divisão, ou "Divisão N" quando ela não é conhecida.
     *
     * As divisões 90, 91 e 92 são o pool de clubes sem liga própria, que existem
     * só para preencher os torneios intercontinentais.
     */
    fun deDivisao(divisao: Int): String = when (divisao) {
        1  -> "Série A"
        2  -> "Série B"
        3  -> "Série C"
        4  -> "Série D"
        5  -> "Primera División"
        6  -> "Primera Nacional"
        9  -> "Primera División Uruguaia"
        10 -> "Segunda División Uruguaia"
        11 -> "Premier League"
        12 -> "EFL Championship"
        13 -> "La Liga"
        14 -> "Serie A"
        90, 91, 92 -> "Outros times"
        else -> "Divisão $divisao"
    }

    /**
     * Versão curta, para o subtítulo do header do clube, onde o nome divide a
     * linha com a posição, a rodada e a data.
     */
    fun curto(divisao: Int): String = when (divisao) {
        5  -> "Primera Div. (ARG)"
        6  -> "Primera Nacional"
        9  -> "Primera Div. (URU)"
        10 -> "Segunda Div. (URU)"
        else -> deDivisao(divisao)
    }
}
