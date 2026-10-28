package br.com.managerfoot.data.database

import br.com.managerfoot.data.database.entities.CalendarioVersao
import br.com.managerfoot.data.database.entities.CampeonatoEntity
import br.com.managerfoot.data.database.entities.FormatoCampeonato
import br.com.managerfoot.data.database.entities.TipoCampeonato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A corrente de migrações do banco.
 *
 * Este teste existe por causa de uma linha: `fallbackToDestructiveMigration()`.
 * Com ela, uma versão de esquema subida sem a migração correspondente não dá
 * erro — o Room **apaga o banco e cria outro**, e o save do jogador desaparece
 * em silêncio. Nenhuma outra falha deste arquivo destrói dado.
 *
 * O que NÃO está coberto aqui: o `ALTER TABLE` de verdade. Executá-lo pede um
 * SQLite real, e o projeto não tem infraestrutura de teste instrumentado — um
 * `androidTest` exigiria instalar no aparelho, o que apagaria o save do usuário.
 * O que dá para garantir na JVM é que a corrente existe, é contígua e chega na
 * versão declarada; é isso que este teste faz.
 */
class AppDatabaseMigracoesTest {

    @Test
    fun `a corrente de migracoes chega na versao declarada`() {
        val maiorDestino = AppDatabase.MIGRACOES.maxOf { it.endVersion }
        assertEquals(
            "a versão do esquema subiu sem migração: o save seria apagado",
            EsquemaDoBanco.VERSAO,
            maiorDestino
        )
    }

    @Test
    fun `a corrente nao tem buraco nem degrau repetido`() {
        val porOrigem = AppDatabase.MIGRACOES.sortedBy { it.startVersion }
        val primeira = porOrigem.first().startVersion
        var esperada = primeira
        for (migracao in porOrigem) {
            assertEquals(
                "a migração ${migracao.startVersion}→${migracao.endVersion} " +
                    "não continua de $esperada",
                esperada,
                migracao.startVersion
            )
            assertEquals(
                "a migração ${migracao.startVersion}→${migracao.endVersion} pula versão",
                migracao.startVersion + 1,
                migracao.endVersion
            )
            esperada = migracao.endVersion
        }
        assertEquals(EsquemaDoBanco.VERSAO, esperada)
    }

    @Test
    fun `nenhuma migracao desce de versao`() {
        for (migracao in AppDatabase.MIGRACOES) {
            assertTrue(
                "${migracao.startVersion}→${migracao.endVersion}",
                migracao.endVersion > migracao.startVersion
            )
        }
    }

    // ── A versão do calendário ───────────────────────────────────────────

    @Test
    fun `um campeonato novo nasce na grade e os gravados ficam na curva`() {
        // A migração 36 → 37 usa DEFAULT CalendarioVersao.CURVA, então toda
        // linha que já estava no save fica na escala em que foi gravada. Quem dá
        // o valor das linhas NOVAS é este default do Kotlin — se os dois
        // coincidissem, a temporada em andamento passaria a ser lida pela escala
        // errada.
        val novo = CampeonatoEntity(
            temporadaId = 1,
            nome = "Brasileirão Série A 2027",
            tipo = TipoCampeonato.NACIONAL_DIVISAO1,
            formato = FormatoCampeonato.PONTOS_CORRIDOS,
            totalRodadas = 38
        )
        assertEquals(CalendarioVersao.ATUAL, novo.calendarioVersao)
        assertEquals(CalendarioVersao.GRADE, CalendarioVersao.ATUAL)
        assertTrue(
            "a versão nova tem de ser diferente da antiga",
            CalendarioVersao.GRADE != CalendarioVersao.CURVA
        )
    }

    @Test
    fun `a versao da curva e 1, que e o DEFAULT da migracao`() {
        // O `DEFAULT 1` está escrito no SQL da migração 36 → 37. Se esta
        // constante mudar sem o SQL mudar junto, os campeonatos antigos passam a
        // ser lidos por uma escala que não é a deles.
        assertEquals(1, CalendarioVersao.CURVA)
    }
}
