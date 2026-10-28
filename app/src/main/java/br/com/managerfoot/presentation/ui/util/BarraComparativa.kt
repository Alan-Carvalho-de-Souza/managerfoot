package br.com.managerfoot.presentation.ui.util

// ─────────────────────────────────────────────────────────────────────────────
// A barra de duas cores que compara dois valores lado a lado — o comparativo
// por setor da escalação e o de estatísticas da partida.
//
// Existe por um motivo específico: o Compose **recusa `Modifier.weight(0f)`** com
// `IllegalArgumentException("invalid weight 0.0")`, e os dois lados podem valer
// zero de verdade. Um setor sem titular vale 0, e num jogo novo, com a escalação
// ainda não montada, os quatro setores valem 0 para os dois times. Dividir e
// passar o resultado direto para `weight` derruba o app no primeiro desenho.
//
// O cálculo estava duplicado nas duas telas, e só uma delas protegia o numerador.
// ─────────────────────────────────────────────────────────────────────────────
object BarraComparativa {

    /**
     * Fatia mínima de cada lado. Garante weight > 0 e mantém visível que o lado
     * existe, mesmo valendo zero contra um valor alto.
     */
    const val FATIA_MINIMA = 0.02f

    /**
     * Fração da barra que cabe ao primeiro valor, sempre em `[FATIA_MINIMA, 1 −
     * FATIA_MINIMA]`. A do segundo é `1 − ` esta.
     *
     * Devolve meio a meio quando os dois são zero: isso é um empate, não uma
     * barra torta. Valores negativos não deveriam chegar aqui, e são tratados
     * como zero em vez de produzirem uma fração fora da faixa.
     */
    fun fracaoDoPrimeiro(primeiro: Int, segundo: Int): Float {
        val a = primeiro.coerceAtLeast(0)
        val b = segundo.coerceAtLeast(0)
        val soma = a + b
        if (soma <= 0) return 0.5f
        return (a.toFloat() / soma).coerceIn(FATIA_MINIMA, 1f - FATIA_MINIMA)
    }
}
