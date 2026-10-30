package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.Posicao

// ─────────────────────────────────────────────────────────────
//  FormacoesTaticas
//  Catálogo central de formações e suas VARIAÇÕES. Cada variante
//  define um template ordenado de 11 posições específicas (não só
//  a contagem por setor) e um viés ofensivo/defensivo que a engine
//  usa para inclinar o resultado.
//
//  O código da variante é o que fica salvo em Time.taticaFormacao:
//   • base pura ("4-4-2", "4-3-3", ...) → compatível com saves antigos
//   • variante com sufixo ("4-3-3/DM")  → ignora-se o sufixo na
//     contagem por setor (parseFormacao), mas o template e o viés
//     são os da variante.
//
//  A ordem dos slots é sempre GL → DEFESA → MEIO → ATAQUE, casando
//  com a contagem de `base` (ex.: "4-3-3" = 1+4+3+3).
// ─────────────────────────────────────────────────────────────
object FormacoesTaticas {

    data class Variante(
        val codigo: String,        // salvo em taticaFormacao, ex.: "4-3-3/DM"
        val rotulo: String,        // exibido no dropdown
        val base: String,          // "4-3-3" — para contagem por setor
        val slots: List<Posicao>,  // 11 posições, na ordem GL→DEF→MEIO→ATK
        val viesOfensivo: Float    // -1 (defensivo) .. 0 (neutro) .. +1 (ofensivo)
    )

    // Atalhos de leitura
    private val GL  = Posicao.GOLEIRO
    private val ZAG = Posicao.ZAGUEIRO
    private val LD  = Posicao.LATERAL_DIREITO
    private val LE  = Posicao.LATERAL_ESQUERDO
    private val VOL = Posicao.VOLANTE
    private val MEC = Posicao.MEIA_CENTRAL
    private val MEA = Posicao.MEIA_ATACANTE
    private val PD  = Posicao.PONTA_DIREITA
    private val PE  = Posicao.PONTA_ESQUERDA
    private val CA  = Posicao.CENTROAVANTE
    private val SA  = Posicao.SEGUNDA_ATACANTE

    // Blocos defensivos reaproveitados
    private val DEF4 = listOf(LE, ZAG, ZAG, LD)
    private val DEF3 = listOf(ZAG, ZAG, ZAG)
    private val DEF5 = listOf(LE, ZAG, ZAG, ZAG, LD)

    val todas: List<Variante> = listOf(
        // ── 4-4-2 ───────────────────────────────────────────────
        Variante("4-4-2", "4-4-2 · clássico", "4-4-2",
            listOf(GL) + DEF4 + listOf(VOL, MEC, MEC, MEA) + listOf(CA, SA), 0f),
        Variante("4-4-2/DM", "4-4-2 · 2 volantes", "4-4-2",
            listOf(GL) + DEF4 + listOf(VOL, VOL, MEC, MEC) + listOf(CA, SA), -0.35f),
        Variante("4-4-2/OF", "4-4-2 · 2 meias ofensivos", "4-4-2",
            listOf(GL) + DEF4 + listOf(VOL, MEC, MEA, MEA) + listOf(CA, SA), +0.30f),

        // ── 4-3-3 ───────────────────────────────────────────────
        Variante("4-3-3", "4-3-3 · equilibrado", "4-3-3",
            listOf(GL) + DEF4 + listOf(VOL, MEC, MEC) + listOf(PE, CA, PD), 0f),
        Variante("4-3-3/DM", "4-3-3 · 2 volantes, 1 meia", "4-3-3",
            listOf(GL) + DEF4 + listOf(VOL, VOL, MEC) + listOf(PE, CA, PD), -0.30f),
        Variante("4-3-3/OF", "4-3-3 · 1 volante, 2 meias", "4-3-3",
            listOf(GL) + DEF4 + listOf(VOL, MEC, MEA) + listOf(PE, CA, PD), +0.25f),

        // ── 4-5-1 ───────────────────────────────────────────────
        Variante("4-5-1", "4-5-1 · contenção", "4-5-1",
            listOf(GL) + DEF4 + listOf(VOL, VOL, MEC, MEC, MEA) + listOf(CA), -0.20f),

        // ── 3-5-2 ───────────────────────────────────────────────
        Variante("3-5-2", "3-5-2 · alas", "3-5-2",
            listOf(GL) + DEF3 + listOf(VOL, VOL, MEC, MEC, MEA) + listOf(CA, SA), 0f),

        // ── 3-4-3 ───────────────────────────────────────────────
        Variante("3-4-3", "3-4-3 · ofensivo", "3-4-3",
            listOf(GL) + DEF3 + listOf(VOL, MEC, MEC, MEA) + listOf(PE, CA, PD), +0.25f),

        // ── 5-3-2 ───────────────────────────────────────────────
        Variante("5-3-2", "5-3-2 · retranca", "5-3-2",
            listOf(GL) + DEF5 + listOf(VOL, MEC, MEC) + listOf(CA, SA), -0.30f),

        // ── 5-4-1 ───────────────────────────────────────────────
        Variante("5-4-1", "5-4-1 · muralha", "5-4-1",
            listOf(GL) + DEF5 + listOf(VOL, VOL, MEC, MEC) + listOf(CA), -0.45f),
    )

    private val porCodigo: Map<String, Variante> = todas.associateBy { it.codigo }

    /** Códigos exibidos no dropdown, na ordem de catálogo. */
    val codigos: List<String> = todas.map { it.codigo }

    fun variante(codigo: String): Variante? = porCodigo[codigo]

    /** Template de 11 posições, ou null se o código não tiver variante conhecida. */
    fun slots(codigo: String): List<Posicao>? = porCodigo[codigo]?.slots

    /** Esquema base para contagem por setor; tolera sufixo desconhecido. */
    fun base(codigo: String): String = porCodigo[codigo]?.base ?: codigo.substringBefore("/")

    /** Rótulo amigável; devolve o próprio código se não houver variante. */
    fun rotulo(codigo: String): String = porCodigo[codigo]?.rotulo ?: codigo

    /** Viés ofensivo (-1..+1) da variante; 0 para esquemas sem variação. */
    fun viesOfensivo(codigo: String): Float = porCodigo[codigo]?.viesOfensivo ?: 0f
}
