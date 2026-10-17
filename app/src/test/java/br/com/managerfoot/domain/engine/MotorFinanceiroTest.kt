package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes do preço de referência da bilheteria por reputação (Opção 1). */
class MotorFinanceiroTest {

    @Test
    fun referencia_cresce_com_reputacao() {
        val pequeno = MotorFinanceiro.fatorReferenciaBilheteria(30f)
        val medio   = MotorFinanceiro.fatorReferenciaBilheteria(60f)
        val grande  = MotorFinanceiro.fatorReferenciaBilheteria(90f)
        assertTrue(pequeno < medio && medio < grande)
    }

    @Test
    fun referencia_tem_piso_08_e_teto_20() {
        assertEquals(0.8, MotorFinanceiro.fatorReferenciaBilheteria(30f), 0.0001)
        assertEquals(0.8, MotorFinanceiro.fatorReferenciaBilheteria(10f), 0.0001) // abaixo de 30 → piso
        assertEquals(2.0, MotorFinanceiro.fatorReferenciaBilheteria(99f), 0.0001)
        assertEquals(2.0, MotorFinanceiro.fatorReferenciaBilheteria(120f), 0.0001) // acima → teto
    }

    @Test
    fun referencia_valor_intermediario() {
        // rep 50 → 0.8 + 1.2 × (20/69) ≈ 1.148
        assertEquals(1.148, MotorFinanceiro.fatorReferenciaBilheteria(50f), 0.005)
    }

    @Test
    fun penalidade_patrocinio_so_para_divisoes_br_inferiores() {
        // Série A (1) e ligas estrangeiras (códigos altos) não são penalizadas.
        assertEquals(0L, MotorFinanceiro.penalidadeDivisaoPatrocinio(1))
        assertEquals(0L, MotorFinanceiro.penalidadeDivisaoPatrocinio(13)) // La Liga
        assertEquals(0L, MotorFinanceiro.penalidadeDivisaoPatrocinio(14)) // Serie A
        assertEquals(0L, MotorFinanceiro.penalidadeDivisaoPatrocinio(90)) // Outros times
        // Série B/C/D do Brasil levam desconto progressivo.
        assertEquals(500_000_00L, MotorFinanceiro.penalidadeDivisaoPatrocinio(2))
        assertEquals(1_000_000_00L, MotorFinanceiro.penalidadeDivisaoPatrocinio(3))
        assertEquals(1_500_000_00L, MotorFinanceiro.penalidadeDivisaoPatrocinio(4))
    }
}
