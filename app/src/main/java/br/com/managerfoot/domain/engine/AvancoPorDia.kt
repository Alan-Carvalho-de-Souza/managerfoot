package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.PartidaEntity

// ─────────────────────────────────────────────────────────────────────────────
// Quais partidas o mundo deve ter jogado até hoje.
//
// Até aqui o tempo do jogo era "um tique por partida do jogador": depois de cada
// jogo dele, TODA outra competição avançava exatamente uma rodada. Isso faz as
// competições correrem em ritmos diferentes, porque elas têm números de rodadas
// diferentes — a EFL Championship tem 46 e a Série A 38, e o Apertura argentino
// acaba na 20ª. Com um tique por partida do jogador, a de 46 chega ao fim da
// temporada com 8 rodadas por jogar e a argentina ultrapassa o próprio torneio.
//
// Com a etapa 2 cada partida sabe o DIA em que acontece, e aí existe uma regra
// melhor e mais simples: **depois que o jogador joga no dia D, tudo que estava
// marcado até D já aconteceu**. A rodada deixa de ser o que anda e passa a ser
// consequência de quais partidas foram jogadas.
//
// A regra tem três propriedades que importam:
//
//  - é **idempotente**: partidas já jogadas não entram de novo, então chamá-la
//    duas vezes no mesmo dia não faz nada;
//  - **recupera atraso sozinha**: uma competição parada há semanas volta a estar
//    em dia na primeira chamada, sem rotina de reconciliação à parte;
//  - **não depende da ordem** em que as competições são percorridas.
//
// Ver docs/calendario-da-temporada.md, etapa 5.
// ─────────────────────────────────────────────────────────────────────────────
object AvancoPorDia {

    /**
     * As partidas de uma competição que já deveriam ter sido jogadas em [ateODia],
     * na ordem em que aconteceram.
     *
     * Ficam de fora:
     *  - as já jogadas;
     *  - as de mata-mata ([PartidaEntity.fase] não nula), que nascem de
     *    `verificarEAvancarFase*` quando a fase anterior termina e têm o seu
     *    próprio gatilho — jogá-las por data atropelaria esse encadeamento;
     *  - as sem posição no calendário (`ordemGlobal` até zero), que são as que
     *    ainda não foram agendadas.
     */
    fun partidasAtePorDia(partidas: List<PartidaEntity>, ateODia: Int): List<PartidaEntity> {
        if (ateODia <= 0) return emptyList()
        return partidas
            .filter { !it.jogada && it.fase == null && it.ordemGlobal in 1..ateODia }
            .sortedWith(compareBy({ it.ordemGlobal }, { it.rodada }, { it.id }))
    }

    /**
     * A rodada em que uma competição fica depois de pôr o mundo em dia.
     *
     * É a maior rodada entre as partidas jogadas — as que já estavam e as que
     * [partidasAtePorDia] acabou de devolver. Devolve 0 quando nada foi jogado,
     * e quem chama usa isso para não mexer no contador.
     */
    fun rodadaDepoisDoAvanco(partidas: List<PartidaEntity>, ateODia: Int): Int {
        val jogadas = partidas.filter { it.jogada && it.fase == null }
        val aJogar = partidasAtePorDia(partidas, ateODia)
        return (jogadas + aJogar).maxOfOrNull { it.rodada } ?: 0
    }
}
