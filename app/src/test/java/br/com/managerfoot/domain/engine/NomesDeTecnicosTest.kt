package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Os nomes dos técnicos que entram no mercado quando um veterano se aposenta. */
class NomesDeTecnicosTest {

    private val primeiro: (Int) -> Int = { 0 }

    @Test
    fun `o nome abreviado e a inicial mais o sobrenome`() {
        val (nome, abreviado) = NomesDeTecnicos.gerar("Brasil", primeiro)
        val partes = nome.split(" ")
        assertEquals("${partes.first().first()}. ${partes.drop(1).joinToString(" ")}", abreviado)
    }

    @Test
    fun `cada nacionalidade puxa nomes do seu proprio pool`() {
        val nomes = listOf("Brasil", "Argentina", "Espanha", "Italia", "Inglaterra")
            .map { NomesDeTecnicos.gerar(it, primeiro).first }
        assertEquals(nomes.distinct().size, nomes.size)
    }

    @Test
    fun `nacionalidade desconhecida ainda gera um nome`() {
        // O seed tem ~65 nacionalidades; nenhuma pode devolver nome vazio.
        for (pais in listOf("Liechtenstein", "Tchequia", "", "Marte")) {
            val (nome, abreviado) = NomesDeTecnicos.gerar(pais, primeiro)
            assertTrue(pais, nome.isNotBlank() && abreviado.isNotBlank())
            assertTrue(pais, nome.contains(" "))
        }
    }

    @Test
    fun `grafias com e sem acento caem no mesmo pool`() {
        assertEquals(
            NomesDeTecnicos.gerar("Italia", primeiro),
            NomesDeTecnicos.gerar("Itália", primeiro)
        )
    }

    @Test
    fun `sorteio fora do intervalo nao estoura`() {
        for (indice in listOf(-5, 0, 999)) {
            val (nome, _) = NomesDeTecnicos.gerar("Brasil") { indice }
            assertTrue(nome.isNotBlank())
        }
    }
}
