package br.com.managerfoot.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A distribuição das vagas da Champions e da Europa League sobre a tabela da
 * Premier League, da La Liga e da Serie A.
 *
 * As cotas não são escritas aqui: saem das listas de acesso, e os testes as leem
 * de lá. Assim, mudar uma cota não deixa um teste mentindo — ele acompanha.
 */
class VagasEuropeiasTest {

    private val CLUBES = 20

    private fun zonas(pais: String, copa: VagaDeTitulo = VagaDeTitulo.Indefinida) =
        VagasEuropeias.distribuir(pais, CLUBES, copa)

    // ── Cotas ────────────────────────────────────────────────────────────

    @Test
    fun `cotas da champions saem da lista de acesso`() {
        assertEquals(5, VagasEuropeias.vagasChampions("Inglaterra"))
        assertEquals(5, VagasEuropeias.vagasChampions("Espanha"))
        assertEquals(4, VagasEuropeias.vagasChampions("Italia"))
    }

    @Test
    fun `cota da europa league e de duas vagas nos tres paises`() {
        for (pais in listOf("Inglaterra", "Espanha", "Italia")) {
            assertEquals(pais, 2, VagasEuropeias.vagasEuropaLeague(pais))
        }
    }

    @Test
    fun `pais fora das listas nao tem zona`() {
        val z = zonas("Brasil")
        assertEquals(0, z.quantas(ZonaEuropeia.CHAMPIONS))
        assertEquals(0, z.quantas(ZonaEuropeia.EUROPA_LEAGUE))
    }

    @Test
    fun `grafias alternativas do pais sao aceitas`() {
        assertEquals(4, VagasEuropeias.vagasChampions("Itália"))
        assertEquals(5, VagasEuropeias.vagasChampions("England"))
    }

    // ── Zona da Champions ────────────────────────────────────────────────

    @Test
    fun `champions pega os primeiros colocados, pela cota do pais`() {
        val ing = zonas("Inglaterra")
        for (p in 1..5) assertEquals("ING $p", ZonaEuropeia.CHAMPIONS, ing.zona(p))
        assertEquals(5, ing.quantas(ZonaEuropeia.CHAMPIONS))

        val ita = zonas("Italia")
        for (p in 1..4) assertEquals("ITA $p", ZonaEuropeia.CHAMPIONS, ita.zona(p))
        // O 5º italiano NÃO está na Champions: a Itália tem 4 vagas diretas.
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, ita.zona(5))
        assertEquals(4, ita.quantas(ZonaEuropeia.CHAMPIONS))
    }

    // ── Copa em andamento: a vaga fica reservada ─────────────────────────

    @Test
    fun `copa em andamento reserva uma vaga e a liga leva so uma`() {
        // É a diferença entre "não se sabe quem" e "não tem dono": a vaga da copa
        // não pode descer para o 7º só porque o campeão ainda é desconhecido.
        val z = zonas("Inglaterra", VagaDeTitulo.Indefinida)
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, z.zona(6))
        assertEquals(ZonaEuropeia.NENHUMA, z.zona(7))
        assertEquals(1, z.quantas(ZonaEuropeia.EUROPA_LEAGUE))
    }

    // ── Cascata ──────────────────────────────────────────────────────────

    @Test
    fun `campeao da copa dentro da zona da champions faz a vaga descer`() {
        // Ele entra pela colocação, então as DUAS vagas de Europa League sobram
        // para a liga: 6º e 7º.
        val z = zonas("Inglaterra", VagaDeTitulo.De(3))
        assertEquals(ZonaEuropeia.CHAMPIONS, z.zona(3))
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, z.zona(6))
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, z.zona(7))
        assertEquals(2, z.quantas(ZonaEuropeia.EUROPA_LEAGUE))
    }

    @Test
    fun `campeao da copa fora da zona entra pela copa, na posicao dele`() {
        // Campeão da FA Cup em 12º: ele leva uma vaga, e a outra fica com o 6º.
        val z = zonas("Inglaterra", VagaDeTitulo.De(12))
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, z.zona(12))
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, z.zona(6))
        assertEquals(ZonaEuropeia.NENHUMA, z.zona(7))
        assertEquals(2, z.quantas(ZonaEuropeia.EUROPA_LEAGUE))
    }

    @Test
    fun `campeao da copa logo abaixo da zona nao duplica a vaga do 6`() {
        // Campeão em 6º: ele é o mesmo clube que pegaria a vaga da liga, então a
        // segunda vaga desce para o 7º — e não há duas vagas no 6º.
        val z = zonas("Inglaterra", VagaDeTitulo.De(6))
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, z.zona(6))
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, z.zona(7))
        assertEquals(2, z.quantas(ZonaEuropeia.EUROPA_LEAGUE))
    }

    @Test
    fun `campeao da copa fora da tabela faz a vaga descer`() {
        // Campeão de outra divisão: a vaga volta para a liga.
        val z = zonas("Inglaterra", VagaDeTitulo.SemDetentor)
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, z.zona(6))
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, z.zona(7))
        assertEquals(2, z.quantas(ZonaEuropeia.EUROPA_LEAGUE))
    }

    @Test
    fun `posicao impossivel e tratada como sem detentor`() {
        val z = zonas("Inglaterra", VagaDeTitulo.De(99))
        assertEquals(2, z.quantas(ZonaEuropeia.EUROPA_LEAGUE))
    }

    // ── Itália: a cota menor desloca tudo ────────────────────────────────

    @Test
    fun `na italia a europa league comeca no 5 porque a champions e so ate o 4`() {
        val z = zonas("Italia", VagaDeTitulo.De(2))   // campeão da Coppa no G4
        assertEquals(ZonaEuropeia.CHAMPIONS, z.zona(4))
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, z.zona(5))
        assertEquals(ZonaEuropeia.EUROPA_LEAGUE, z.zona(6))
        assertEquals(ZonaEuropeia.NENHUMA, z.zona(7))
    }

    // ── Invariante ───────────────────────────────────────────────────────

    @Test
    fun `o pais nunca distribui mais vagas do que a soma das cotas`() {
        val copas = listOf(
            VagaDeTitulo.Indefinida,
            VagaDeTitulo.SemDetentor,
            VagaDeTitulo.De(1), VagaDeTitulo.De(6), VagaDeTitulo.De(15)
        )
        for (pais in listOf("Inglaterra", "Espanha", "Italia")) {
            val teto = VagasEuropeias.vagasChampions(pais) + VagasEuropeias.vagasEuropaLeague(pais)
            for (copa in copas) {
                val z = zonas(pais, copa)
                val total = z.quantas(ZonaEuropeia.CHAMPIONS) + z.quantas(ZonaEuropeia.EUROPA_LEAGUE)
                assertEquals("$pais com $copa", true, total <= teto)
            }
        }
    }

    @Test
    fun `tabela menor que a cota nao estoura`() {
        val z = VagasEuropeias.distribuir("Inglaterra", clubes = 3)
        assertEquals(3, z.quantas(ZonaEuropeia.CHAMPIONS))
        assertEquals(0, z.quantas(ZonaEuropeia.EUROPA_LEAGUE))
    }
}
