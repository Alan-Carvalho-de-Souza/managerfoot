package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// A distribuição das vagas continentais do Brasil sobre a tabela da Série A.
//
// ── A distribuição padrão (7 vagas na Libertadores) ────────────────────────
//
//   Brasileirão 1º ao 4º ............ 4 vagas ... Fase de Grupos
//   Copa do Brasil (campeão) ........ 1 vaga .... Fase de Grupos
//   Brasileirão 5º .................. 1 vaga .... Pré-Libertadores
//   Copa do Brasil (vice) ........... 1 vaga .... Pré-Libertadores
//
// Mais as 6 da Copa Sul-Americana, que ficam com os colocados seguintes.
//
// ── O efeito cascata ──────────────────────────────────────────────────────
//
// Nenhum clube ocupa duas vagas, e vaga que sobra não se perde: ela desce para o
// próximo colocado da Série A, mantendo o DESTINO original (uma vaga de grupo
// continua de grupo, uma de Pré continua de Pré). É daí que o G-5 vira G-6, G-7
// e, nos cenários de acúmulo máximo, mais que isso:
//
//  - **Campeão continental brasileiro** — campeão da Libertadores ou da
//    Sul-Americana — entra pela vaga ESTRUTURAL, por fora da cota do país. Se ele
//    também se classificaria pela liga, a vaga da liga cascateia.
//  - **Finalista da Copa do Brasil já classificado** pelo G-4/G-5 ocupa a vaga
//    de título e a dele na liga cascateia — o efeito é o mesmo, visto do outro
//    lado: a conta do país não muda, e um colocado a mais entra.
//  - **Rebaixado não joga a Libertadores**: é regulamento da CONMEBOL. Um
//    finalista de copa que cai para a Série B perde a vaga, que volta para o
//    Brasileirão. Aqui isso aparece como a zona de rebaixamento vindo ANTES de
//    qualquer vaga; na lista de acesso aparece por si, porque o rollover atualiza
//    as divisões antes de criar a Libertadores e o clube deixa de ser candidato.
//
// Esta é a mesma regra que [ListaDeAcessoLibertadores.montar] aplica sobre os
// clubes; aqui ela é aplicada sobre POSIÇÕES, que é o que a tabela conhece. As
// quantidades saem das cotas, então mudar uma cota lá muda a tabela aqui.
// ─────────────────────────────────────────────────────────────────────────────

/** O destino de uma posição na tabela da Série A. */
enum class ZonaBrasileirao {
    /** Vaga direta na fase de grupos da Libertadores. */
    LIBERTADORES_GRUPOS,
    /** Vaga na Pré-Libertadores, as três fases eliminatórias prévias. */
    LIBERTADORES_PRE,
    /** Vaga na fase de grupos da Copa Sul-Americana. */
    SUL_AMERICANA,
    /** Zona de rebaixamento. */
    REBAIXAMENTO,
    /** Nem vaga continental nem rebaixamento. */
    NENHUMA
}

/** As zonas de uma tabela inteira, já com a cascata resolvida. */
data class ZonasDoBrasileirao(private val porPosicao: List<ZonaBrasileirao>) {

    /** A zona da [posicao] (1-based), ou [ZonaBrasileirao.NENHUMA] fora da tabela. */
    fun zona(posicao: Int): ZonaBrasileirao =
        porPosicao.getOrNull(posicao - 1) ?: ZonaBrasileirao.NENHUMA

    /** Quantas posições cada zona ocupa — é o que a legenda mostra. */
    val contagem: Map<ZonaBrasileirao, Int> = porPosicao.groupingBy { it }.eachCount()

    fun quantas(zona: ZonaBrasileirao): Int = contagem[zona] ?: 0
}

object VagasDoBrasileirao {

    const val CLUBES = 20
    const val REBAIXADOS = 4

    private val COTA_LIBERTADORES =
        ListaDeAcessoLibertadores.COTAS_OFICIAIS.getValue("Brasil")
    private val COTA_SUL_AMERICANA =
        ListaDeAcessoSulAmericana.COTAS_OFICIAIS.getValue("Brasil")

    /** 7: cinco vagas de grupo e duas na Pré-Libertadores. */
    val VAGAS_LIBERTADORES = COTA_LIBERTADORES.total

    /** 6, todas direto na fase de grupos. */
    val VAGAS_SUL_AMERICANA = COTA_SUL_AMERICANA.total

    /** Vagas de grupo que a LIGA distribui na distribuição padrão: o G4. */
    val VAGAS_DE_GRUPO_DA_LIGA = COTA_LIBERTADORES.grupos - 1

    /** Vagas de Pré que a liga distribui na distribuição padrão: o 5º. */
    val VAGAS_DE_PRE_DA_LIGA = COTA_LIBERTADORES.pre - 1

    /** A primeira posição da zona de rebaixamento. */
    val PRIMEIRA_REBAIXADA = CLUBES - REBAIXADOS + 1

    /**
     * Distribui as vagas sobre a tabela.
     *
     * Todas as posições são 1-based e na MESMA tabela; -1 (ou fora da tabela)
     * significa "não se aplica": o detentor não existe, não é brasileiro ou não
     * está na Série A. Nesse caso a vaga dele desce para a liga, que é a regra de
     * queda da lista de acesso.
     *
     * @param posicaoDoCampeaoDaCopa campeão da Copa do Brasil — vaga de grupo.
     * @param posicaoDoViceDaCopa vice da Copa do Brasil — vaga de Pré.
     * @param posicoesDosCampeoesContinentais campeões vigentes da Libertadores e
     *        da Sul-Americana, quando brasileiros: entram pela vaga estrutural.
     * @param vagasRemanejadas vagas estruturais sem titular que desceram para o
     *        Brasil, 0 ou 1.
     */
    fun distribuir(
        posicaoDoCampeaoDaCopa: Int = -1,
        posicaoDoViceDaCopa: Int = -1,
        posicoesDosCampeoesContinentais: List<Int> = emptyList(),
        vagasRemanejadas: Int = 0,
        clubes: Int = CLUBES
    ): ZonasDoBrasileirao {
        val primeiraRebaixada = clubes - REBAIXADOS + 1
        val zonas = MutableList(clubes) { ZonaBrasileirao.NENHUMA }

        // ── 1. O rebaixamento vem antes de tudo ────────────────────────
        // Clube rebaixado não disputa a Libertadores, nem se for finalista de
        // copa: é o regulamento da CONMEBOL, e a vaga dele volta para a liga.
        for (posicao in primeiraRebaixada..clubes) {
            zonas[posicao - 1] = ZonaBrasileirao.REBAIXAMENTO
        }

        /** Quem já está classificado por uma via que não é a colocação. */
        val jaDentro = HashSet<Int>()
        fun elegivel(posicao: Int) =
            posicao in 1 until primeiraRebaixada && posicao !in jaDentro

        // ── 2. Campeões continentais: vaga estrutural ──────────────────
        // Por fora da cota do país. Marcá-los aqui é o que faz a vaga que eles
        // teriam na liga cascatear — o G-5 virando G-6 ou mais.
        for (posicao in posicoesDosCampeoesContinentais) {
            if (!elegivel(posicao)) continue
            zonas[posicao - 1] = ZonaBrasileirao.LIBERTADORES_GRUPOS
            jaDentro += posicao
        }

        // ── 3. As vagas de título da Copa do Brasil ────────────────────
        // Cada uma sai da cota do seu destino. Sem detentor elegível, a vaga
        // volta para a liga — e é só isso que a cascata precisa fazer, porque a
        // conta por destino é fixa.
        var deGrupoNaLiga = VAGAS_DE_GRUPO_DA_LIGA + vagasRemanejadas
        var naPreNaLiga = VAGAS_DE_PRE_DA_LIGA

        if (elegivel(posicaoDoCampeaoDaCopa)) {
            zonas[posicaoDoCampeaoDaCopa - 1] = ZonaBrasileirao.LIBERTADORES_GRUPOS
            jaDentro += posicaoDoCampeaoDaCopa
        } else {
            deGrupoNaLiga++
        }

        if (elegivel(posicaoDoViceDaCopa)) {
            zonas[posicaoDoViceDaCopa - 1] = ZonaBrasileirao.LIBERTADORES_PRE
            jaDentro += posicaoDoViceDaCopa
        } else {
            naPreNaLiga++
        }

        // ── 4. A liga preenche o resto, de cima para baixo ─────────────
        var restamGrupo = deGrupoNaLiga
        var restamPre = naPreNaLiga
        var restamSulAmericana = VAGAS_SUL_AMERICANA
        for (posicao in 1 until primeiraRebaixada) {
            if (posicao in jaDentro) continue
            when {
                restamGrupo > 0 -> {
                    zonas[posicao - 1] = ZonaBrasileirao.LIBERTADORES_GRUPOS
                    restamGrupo--
                }
                restamPre > 0 -> {
                    zonas[posicao - 1] = ZonaBrasileirao.LIBERTADORES_PRE
                    restamPre--
                }
                restamSulAmericana > 0 -> {
                    zonas[posicao - 1] = ZonaBrasileirao.SUL_AMERICANA
                    restamSulAmericana--
                }
            }
        }
        return ZonasDoBrasileirao(zonas)
    }
}
