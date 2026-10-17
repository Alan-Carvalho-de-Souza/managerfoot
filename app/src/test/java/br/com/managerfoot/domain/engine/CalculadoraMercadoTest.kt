package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.Posicao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do modelo econômico (Ondas 1–3): valor de mercado, salário, potencial e
 * margem de mercado do usuário. Funções puras → testáveis sem Android/DB.
 */
class CalculadoraMercadoTest {

    private val MEI = Posicao.MEIA_CENTRAL
    private val ATA = Posicao.CENTROAVANTE
    private val DEF = Posicao.ZAGUEIRO
    private val GOL = Posicao.GOLEIRO

    // ─────────────────────────────────────────────────────────
    //  VALOR DE MERCADO (Onda 1)
    // ─────────────────────────────────────────────────────────

    @Test
    fun valor_referencia_MEI_80_28_contrato4() {
        // base(80)=R$10,6M × idade28(1.0) × contrato4(1.0) × MEI(1.05) = R$11,13M
        val valor = CalculadoraMercado.valorDeMercado(80, 28, 4, MEI)
        assertEquals(1_113_000_000L, valor)
    }

    @Test
    fun valor_cresce_com_tempo_de_contrato() {
        val c1 = CalculadoraMercado.valorDeMercado(78, 26, 1, MEI)
        val c2 = CalculadoraMercado.valorDeMercado(78, 26, 2, MEI)
        val c3 = CalculadoraMercado.valorDeMercado(78, 26, 3, MEI)
        val c4 = CalculadoraMercado.valorDeMercado(78, 26, 4, MEI)
        assertTrue("contrato mais curto vale menos", c1 < c2 && c2 < c3 && c3 < c4)
    }

    @Test
    fun valor_contrato1_proximo_de_55pct_do_contrato4() {
        val c4 = CalculadoraMercado.valorDeMercado(78, 26, 4, MEI).toDouble()
        val c1 = CalculadoraMercado.valorDeMercado(78, 26, 1, MEI).toDouble()
        // fatorContrato: 1 ano = 0.55, 4 anos = 1.00 → razão esperada ~0.55
        assertEquals(0.55, c1 / c4, 0.01)
    }

    @Test
    fun valor_por_posicao_ATA_maior_que_GOL() {
        val ata = CalculadoraMercado.valorDeMercado(80, 28, 3, ATA)
        val mei = CalculadoraMercado.valorDeMercado(80, 28, 3, MEI)
        val def = CalculadoraMercado.valorDeMercado(80, 28, 3, DEF)
        val gol = CalculadoraMercado.valorDeMercado(80, 28, 3, GOL)
        assertTrue(ata > mei && mei > def && def > gol)
    }

    @Test
    fun valor_pico_etario_maior_que_veterano_e_que_jovem() {
        val jovem    = CalculadoraMercado.valorDeMercado(80, 18, 3, MEI)
        val pico     = CalculadoraMercado.valorDeMercado(80, 28, 3, MEI)
        val veterano = CalculadoraMercado.valorDeMercado(80, 35, 3, MEI)
        assertTrue("pico > jovem", pico > jovem)
        assertTrue("pico > veterano", pico > veterano)
    }

    @Test
    fun valor_respeita_piso() {
        // Jogador fraquíssimo, contrato acabando: mesmo assim >= piso R$50k.
        val valor = CalculadoraMercado.valorDeMercado(30, 39, 0, GOL)
        assertEquals(CalculadoraMercado.PISO_VALOR, valor)
        assertTrue(valor >= CalculadoraMercado.PISO_VALOR)
    }

    // ─────────────────────────────────────────────────────────
    //  POTENCIAL (Onda 3)
    // ─────────────────────────────────────────────────────────

    @Test
    fun potencial_da_upside_a_jovens() {
        val semUpside = CalculadoraMercado.valorDeMercado(70, 19, 3, MEI, potencial = 70)
        val comUpside = CalculadoraMercado.valorDeMercado(70, 19, 3, MEI, potencial = 90)
        assertTrue("jovem com maior potencial vale mais", comUpside > semUpside)
    }

    @Test
    fun potencial_sem_efeito_em_jogador_maduro() {
        val a = CalculadoraMercado.valorDeMercado(70, 28, 3, MEI, potencial = 70)
        val b = CalculadoraMercado.valorDeMercado(70, 28, 3, MEI, potencial = 95)
        assertEquals("acima de 23 anos o potencial não valoriza", a, b)
    }

    @Test
    fun fatorPotencial_limita_em_30_pontos() {
        val cap30 = CalculadoraMercado.fatorPotencial(50, 19, 80) // diff 30
        val cap40 = CalculadoraMercado.fatorPotencial(50, 19, 90) // diff 40 → limitado a 30
        val diff25 = CalculadoraMercado.fatorPotencial(50, 19, 75)
        assertEquals(cap30, cap40, 0.0001)
        assertTrue(cap30 > diff25)
        // 30 pontos × 0.6% = +18% → fator 1.18
        assertEquals(1.18, cap30, 0.0001)
    }

    @Test
    fun derivarPotencial_soma_bonus_por_juventude_com_teto_99() {
        assertEquals(82, CalculadoraMercado.derivarPotencial(70, 18)) // +12
        assertEquals(78, CalculadoraMercado.derivarPotencial(70, 20)) // +8
        assertEquals(74, CalculadoraMercado.derivarPotencial(70, 23)) // +4
        assertEquals(70, CalculadoraMercado.derivarPotencial(70, 30)) // +0 (maduro)
        assertEquals(99, CalculadoraMercado.derivarPotencial(95, 16)) // 95+12=107 → teto 99
    }

    // ─────────────────────────────────────────────────────────
    //  SALÁRIO (Onda 2)
    // ─────────────────────────────────────────────────────────

    @Test
    fun salario_referencia_MEI_80_28() {
        // valorSemContrato = base(80) × idade(1.0) × MEI(1.05) = 1_113_000_000; ×ratio(28)=1/60
        val salario = CalculadoraMercado.salarioAlvo(80, 28, MEI)
        assertEquals(18_550_000L, salario)
    }

    @Test
    fun salario_independe_do_contrato() {
        // salarioAlvo não recebe contrato como parâmetro: mesma força/idade/posição = mesmo salário.
        val s1 = CalculadoraMercado.salarioAlvo(78, 26, MEI)
        val s2 = CalculadoraMercado.salarioAlvo(78, 26, MEI)
        assertEquals(s1, s2)
    }

    @Test
    fun salario_veterano_proporcionalmente_maior_que_jovem() {
        // Mesma força/posição: razão salário/valorSemContrato cresce com a idade (1/72 → 1/32).
        // Comparamos jovem (24) e veterano (34) de mesma força para checar a razão relativa.
        val jovemRatio = CalculadoraMercado.salarioAlvo(70, 23, MEI).toDouble() /
                CalculadoraMercado.valorDeMercado(70, 23, 4, MEI).toDouble()
        val vetRatio = CalculadoraMercado.salarioAlvo(70, 34, MEI).toDouble() /
                CalculadoraMercado.valorDeMercado(70, 34, 4, MEI).toDouble()
        assertTrue("veterano gasta proporcionalmente mais em salário", vetRatio > jovemRatio)
    }

    @Test
    fun salario_respeita_piso() {
        val salario = CalculadoraMercado.salarioAlvo(40, 18, GOL)
        assertEquals(CalculadoraMercado.PISO_SALARIO, salario)
    }

    @Test
    fun salario_respeita_teto() {
        // Craque veterano de ataque: 8e9 × idade34(0.80) × ATA(1.10) ÷ 32 = 220M > teto 200M.
        val salario = CalculadoraMercado.salarioAlvo(99, 34, ATA)
        assertEquals(CalculadoraMercado.TETO_SALARIO, salario)
    }

    // ─────────────────────────────────────────────────────────
    //  FATORES (unitário direto)
    // ─────────────────────────────────────────────────────────

    @Test
    fun fatorContrato_por_faixa() {
        assertEquals(1.00, CalculadoraMercado.fatorContrato(5), 0.0001)
        assertEquals(1.00, CalculadoraMercado.fatorContrato(4), 0.0001)
        assertEquals(0.92, CalculadoraMercado.fatorContrato(3), 0.0001)
        assertEquals(0.80, CalculadoraMercado.fatorContrato(2), 0.0001)
        assertEquals(0.55, CalculadoraMercado.fatorContrato(1), 0.0001)
        assertEquals(0.35, CalculadoraMercado.fatorContrato(0), 0.0001)
    }

    @Test
    fun fatorPosicao_por_setor() {
        assertEquals(1.10, CalculadoraMercado.fatorPosicao(ATA), 0.0001)
        assertEquals(1.05, CalculadoraMercado.fatorPosicao(MEI), 0.0001)
        assertEquals(0.95, CalculadoraMercado.fatorPosicao(DEF), 0.0001)
        assertEquals(0.90, CalculadoraMercado.fatorPosicao(GOL), 0.0001)
    }

    // ─────────────────────────────────────────────────────────
    //  MARGEM DE MERCADO DO USUÁRIO (Onda 3 / Pilar 5)
    // ─────────────────────────────────────────────────────────

    @Test
    fun precoCompra_tem_premio_de_5pct() {
        assertEquals(1_050_000_000L, CalculadoraMercado.precoCompraUsuario(1_000_000_000L))
    }

    @Test
    fun precoVenda_tem_desagio_de_10pct() {
        assertEquals(900_000_000L, CalculadoraMercado.precoVendaUsuario(1_000_000_000L))
    }

    @Test
    fun compra_mais_cara_que_venda() {
        val valor = 500_000_000L
        val compra = CalculadoraMercado.precoCompraUsuario(valor)
        val venda = CalculadoraMercado.precoVendaUsuario(valor)
        assertTrue(compra > valor && valor > venda)
    }
}
