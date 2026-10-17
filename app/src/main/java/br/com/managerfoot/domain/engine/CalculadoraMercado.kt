package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.Posicao
import br.com.managerfoot.data.database.entities.Setor

/**
 * Fonte única de verdade para a economia de jogadores (valor de mercado).
 *
 * Valor multiplicativo (Pilar 1 do modelo de mercado):
 *
 *   valor = base(forca) × fatorIdade(idade) × fatorContrato(contratoAnos) × fatorPosicao(posicao)
 *
 * - base(forca): curva piecewise em centavos, no pico etário / contrato longo / posição neutra.
 * - fatorIdade: curva de carreira (pico 26–32).
 * - fatorContrato: alavanca de leverage — contrato curto derruba o valor (no limite, Bosman).
 * - fatorPosicao: o mercado precifica gol/criação mais caro que defesa/goleiro.
 *
 * Todos os valores em CENTAVOS de Real (Long).
 *
 * Salário (Pilar 2): derivado do valor SEM o fator contrato (o salário não cai só porque
 * o vínculo é curto), por uma razão que cresce com a idade — jovem craque ganha
 * proporcionalmente pouco (o clube lucra na revenda), veterano ganha caro (sem revenda).
 */
object CalculadoraMercado {

    /** Piso do valor de mercado: R$ 50.000. */
    const val PISO_VALOR = 5_000_000L

    /** Piso e teto do salário mensal: R$ 5.000 e R$ 2.000.000. */
    const val PISO_SALARIO = 500_000L
    const val TETO_SALARIO = 200_000_000L

    /** Margem de mercado do usuário: prêmio ao comprar (+5%) e deságio ao vender (−10%). */
    const val PREMIO_COMPRA_USUARIO = 1.05
    const val DESAGIO_VENDA_USUARIO = 0.90

    /** Preço que o usuário paga ao comprar diretamente (valor + prêmio de negociação). */
    fun precoCompraUsuario(valorMercado: Long): Long = (valorMercado * PREMIO_COMPRA_USUARIO).toLong()

    /** Preço que o usuário recebe ao vender diretamente (valor − deságio de liquidez). */
    fun precoVendaUsuario(valorMercado: Long): Long = (valorMercado * DESAGIO_VENDA_USUARIO).toLong()

    private fun lerpLong(from: Long, to: Long, t: Float): Long =
        (from + (to - from) * t.coerceIn(0f, 1f)).toLong()

    /** Valor-base por força (centavos), no pico etário, contrato longo e posição neutra. */
    private fun baseForca(forca: Int): Long = when {
        forca >= 90 -> lerpLong(3_520_000_000L, 8_000_000_000L, (forca - 90).toFloat() / 9f)
        forca >= 80 -> lerpLong(1_060_000_000L, 3_520_000_000L, (forca - 80).toFloat() / 10f)
        forca >= 70 -> lerpLong(  540_000_000L, 1_060_000_000L, (forca - 70).toFloat() / 10f)
        forca >= 60 -> lerpLong(  140_000_000L,   540_000_000L, (forca - 60).toFloat() / 10f)
        forca >= 50 -> lerpLong(   30_000_000L,   140_000_000L, (forca - 50).toFloat() / 10f)
        else        -> lerpLong(    5_000_000L,    30_000_000L,  (forca -  1).toFloat() / 49f)
    }

    /** Fator multiplicativo por idade — curva de carreira, pico estabilizado em 26–32. */
    private fun fatorIdade(idade: Int): Double = when {
        idade <= 16 -> 0.50
        idade <= 17 -> 0.56
        idade <= 18 -> 0.62
        idade <= 19 -> 0.68
        idade <= 20 -> 0.74
        idade <= 21 -> 0.80
        idade <= 22 -> 0.85
        idade <= 23 -> 0.90
        idade <= 24 -> 0.93
        idade <= 25 -> 0.96
        idade <= 26 -> 0.98
        idade <= 32 -> 1.00  // pico de mercado
        idade <= 33 -> 0.90
        idade <= 34 -> 0.80
        idade <= 35 -> 0.68
        idade <= 36 -> 0.56
        idade <= 37 -> 0.46
        else        -> 0.36
    }

    /**
     * Fator por tempo de contrato restante (leverage do clube).
     * Contrato curto → menos poder de barganha → valor menor; 0 anos = livre em breve.
     */
    fun fatorContrato(contratoAnos: Int): Double = when {
        contratoAnos >= 4 -> 1.00
        contratoAnos == 3 -> 0.92
        contratoAnos == 2 -> 0.80
        contratoAnos == 1 -> 0.55
        else              -> 0.35   // 0 (ou expirado): próximo do fim / Bosman
    }

    /** Fator por posição — mercado real valoriza gols e criação acima de defesa/goleiro. */
    fun fatorPosicao(posicao: Posicao): Double = when (posicao.setor) {
        Setor.ATAQUE  -> 1.10
        Setor.MEIO    -> 1.05
        Setor.DEFESA  -> 0.95
        Setor.GOLEIRO -> 0.90
    }

    /**
     * Prêmio de potencial: promessas jovens (≤23) abaixo do próprio teto valem mais
     * pela projeção. Cada ponto de potencial acima da força atual soma 0,6% ao valor.
     * potencial 0/desconhecido ou jogador maduro → sem prêmio (1.0).
     */
    fun fatorPotencial(forca: Int, idade: Int, potencial: Int): Double =
        if (idade <= 23 && potencial > forca)
            1.0 + (potencial - forca).coerceAtMost(30) / 100.0 * 0.6
        else 1.0

    /** Potencial estimado quando não informado: força + bônus por juventude (teto 99). */
    fun derivarPotencial(forca: Int, idade: Int): Int {
        val bonus = when {
            idade <= 18 -> 12
            idade <= 21 -> 8
            idade <= 24 -> 4
            else        -> 0
        }
        return (forca + bonus).coerceAtMost(99)
    }

    /**
     * Valor "de referência" sem o desconto de contrato — base × idade × posição.
     * É o patrimônio potencial do jogador com contrato longo; serve de âncora do salário.
     */
    private fun valorSemContrato(forca: Int, idade: Int, posicao: Posicao): Double =
        baseForca(forca).toDouble() * fatorIdade(idade) * fatorPosicao(posicao)

    /** Valor de mercado completo (patrimônio), em centavos. */
    fun valorDeMercado(forca: Int, idade: Int, contratoAnos: Int, posicao: Posicao, potencial: Int = 0): Long {
        val v = valorSemContrato(forca, idade, posicao) *
                fatorContrato(contratoAnos) *
                fatorPotencial(forca, idade, potencial)
        return v.toLong().coerceAtLeast(PISO_VALOR)
    }

    /**
     * Razão salário mensal / valor-de-referência, crescente com a idade:
     * jovem ganha proporcionalmente pouco; veterano, muito (sem valor de revenda).
     */
    private fun ratioSalario(idade: Int): Double = when {
        idade <= 23 -> 1.0 / 72.0
        idade <= 29 -> 1.0 / 60.0
        idade <= 33 -> 1.0 / 45.0
        else        -> 1.0 / 32.0
    }

    /** Salário mensal-alvo (centavos), derivado do valor-de-referência por idade/posição. */
    fun salarioAlvo(forca: Int, idade: Int, posicao: Posicao): Long {
        val sal = (valorSemContrato(forca, idade, posicao) * ratioSalario(idade)).toLong()
        return sal.coerceIn(PISO_SALARIO, TETO_SALARIO)
    }
}
