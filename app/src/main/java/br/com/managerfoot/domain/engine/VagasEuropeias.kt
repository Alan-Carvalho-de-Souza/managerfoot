package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// A distribuição das vagas da Champions e da Europa League sobre a tabela das
// ligas nacionais europeias — Premier League, La Liga e Serie A.
//
// ── Champions League ──────────────────────────────────────────────────────
//
// Vagas DIRETAS, por colocação na liga (ListaDeAcessoChampions.VAGAS_DIRETAS):
//
//   Inglaterra ... 5     Espanha ... 5     Itália ... 4
//
// A qualificação (8 vagas) NÃO entra na conta desta tabela de propósito: ela é
// das ligas menores, e só recorre aos melhores restantes dos países da tabela se
// aquelas não preencherem as 8 — ver o comentário em ListaDeAcessoChampions.montar.
// Com o pool europeu atual as ligas menores preenchem sozinhas, então o 6º da
// Premier não entra. Pintá-lo de azul prometeria uma vaga que ele não tem.
//
// ── Europa League ─────────────────────────────────────────────────────────
//
// 2 vagas para cada um dos três países (ListaDeAcessoEuropa.VAGAS_DIRETAS), e
// aqui a vaga tem DUAS PORTAS, preenchidas nesta ordem:
//
//   1. o campeão da copa nacional (FA Cup, Copa do Rei, Coppa Italia);
//   2. o melhor colocado da liga fora da zona da Champions.
//
// ── O efeito cascata ──────────────────────────────────────────────────────
//
// Nenhum clube ocupa duas vagas, e vaga que sobra desce para o próximo colocado:
//
//  - **Campeão da copa já classificado à Champions** — terminou dentro da zona —
//    entra pela colocação, e a vaga da copa desce para a liga. O país passa a ter
//    DOIS classificados pela liga, logo abaixo da zona da Champions.
//  - **Copa em andamento não libera vaga.** Durante a temporada a vaga da copa
//    fica RESERVADA: a liga distribui a zona da Champions e UMA vaga de Europa
//    League, e nada mais. Cascatear só porque o dono ainda não é conhecido
//    prometeria ao colocado seguinte uma vaga que a copa vai levar. É a mesma
//    distinção entre "não se sabe quem" e "não tem dono" de VagasDoBrasileirao.
//  - **Campeão da copa fora desta tabela** — de outra divisão, ou de outro país —
//    não usa a vaga, e ela desce para a liga.
//
// Esta é a mesma regra que [ListaDeAcessoEuropa.montar] aplica sobre os clubes;
// aqui ela é aplicada sobre POSIÇÕES, que é o que a tabela conhece. As
// quantidades saem das cotas, então mudar uma cota lá muda a tabela aqui.
// ─────────────────────────────────────────────────────────────────────────────

/** Zona continental de uma posição numa liga europeia. */
enum class ZonaEuropeia { CHAMPIONS, EUROPA_LEAGUE, NENHUMA }

/** O resultado da distribuição: que zona cabe a cada posição da tabela. */
class ZonasEuropeias(private val porPosicao: Map<Int, ZonaEuropeia>) {

    fun zona(posicao: Int): ZonaEuropeia = porPosicao[posicao] ?: ZonaEuropeia.NENHUMA

    fun quantas(zona: ZonaEuropeia): Int = porPosicao.values.count { it == zona }

    companion object {
        val VAZIA = ZonasEuropeias(emptyMap())
    }
}

object VagasEuropeias {

    /** Vagas diretas do país na Champions, ou 0 se ele não está na lista de acesso. */
    fun vagasChampions(pais: String): Int {
        val canonico = ListaDeAcessoChampions.paisCanonico(pais) ?: return 0
        return ListaDeAcessoChampions.VAGAS_DIRETAS[canonico] ?: 0
    }

    /** Vagas diretas do país na Europa League, ou 0 se ele não está na lista. */
    fun vagasEuropaLeague(pais: String): Int {
        val canonico = ListaDeAcessoEuropa.paisCanonico(pais) ?: return 0
        return ListaDeAcessoEuropa.VAGAS_DIRETAS[canonico] ?: 0
    }

    /**
     * Distribui as vagas continentais do país sobre as posições da tabela.
     *
     * @param pais país da liga, em qualquer grafia aceita pelas listas de acesso.
     * @param clubes quantos clubes a tabela tem.
     * @param campeaoDaCopa estado da vaga do campeão da copa nacional. A posição
     *        de [VagaDeTitulo.De] é a posição NESTA tabela.
     */
    fun distribuir(
        pais: String,
        clubes: Int,
        campeaoDaCopa: VagaDeTitulo = VagaDeTitulo.Indefinida
    ): ZonasEuropeias {
        val nChampions = vagasChampions(pais)
        val nEuropa = vagasEuropaLeague(pais)
        if (nChampions == 0 && nEuropa == 0) return ZonasEuropeias.VAZIA

        val zonas = linkedMapOf<Int, ZonaEuropeia>()

        // ── Champions: as vagas diretas, de cima para baixo ──────────────
        for (posicao in 1..minOf(nChampions, clubes)) {
            zonas[posicao] = ZonaEuropeia.CHAMPIONS
        }

        // ── Europa League: a porta da copa primeiro ──────────────────────
        // `consumidas` conta as vagas do país que a liga NÃO vai distribuir.
        var consumidas = 0
        when (campeaoDaCopa) {
            // Copa correndo: a vaga tem dono, só não se sabe qual. Fica reservada.
            VagaDeTitulo.Indefinida -> consumidas++

            // Sem detentor que possa usá-la: desce para a liga.
            VagaDeTitulo.SemDetentor -> Unit

            is VagaDeTitulo.De -> {
                val posicao = campeaoDaCopa.posicao
                when {
                    // Fora da tabela: desce para a liga.
                    posicao !in 1..clubes -> Unit
                    // Já está na Champions pela colocação: a vaga da copa desce.
                    zonas[posicao] == ZonaEuropeia.CHAMPIONS -> Unit
                    // Entra pela copa, na posição em que terminou.
                    else -> {
                        zonas[posicao] = ZonaEuropeia.EUROPA_LEAGUE
                        consumidas++
                    }
                }
            }
        }

        // ── Europa League: a liga completa a cota ────────────────────────
        var restantes = nEuropa - consumidas
        var posicao = nChampions + 1
        while (restantes > 0 && posicao <= clubes) {
            if (zonas[posicao] == null) {
                zonas[posicao] = ZonaEuropeia.EUROPA_LEAGUE
                restantes--
            }
            posicao++
        }

        return ZonasEuropeias(zonas)
    }
}
