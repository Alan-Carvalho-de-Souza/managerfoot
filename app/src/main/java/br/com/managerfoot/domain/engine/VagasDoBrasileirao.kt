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
//  - **Finalista da Copa do Brasil já classificado** pela colocação fica com a
//    VAGA MAIS BENÉFICA (Art. 5 do regulamento): o campeão da Copa (vaga de
//    grupo) e o vice (vaga de Pré) terminando na zona de grupos vão direto aos
//    grupos pela colocação, e a vaga de título cascateia para baixo mantendo o
//    seu destino. É por isso que o vice no G-4/G-5 sobe aos grupos e a sua vaga
//    de Pré desce para o próximo colocado — a conta do país não muda, mas quem
//    joga grupos e quem joga a Pré, sim.
//  - **Copa em andamento não libera vaga.** Durante a temporada as duas vagas da
//    Copa do Brasil ficam RESERVADAS: a liga distribui o G4 e o 5º, e nada mais.
//    Cascatear uma vaga só porque o dono dela ainda não é conhecido prometeria ao
//    5º e ao 6º lugares que a copa vai levar.
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

/**
 * O estado de uma vaga de título — a do campeão da Copa do Brasil e a do vice.
 *
 * A diferença entre [Indefinida] e [SemDetentor] é o que esta tabela errava: uma
 * vaga cuja copa ainda está em andamento **não** é uma vaga sem dono. Ela tem
 * dono, só não se sabe qual ainda, e portanto NÃO desce para a liga — senão a
 * tabela promete ao 5º e ao 6º uma vaga que a Copa do Brasil vai levar.
 */
sealed interface VagaDeTitulo {
    /** A copa ainda não terminou: a vaga fica reservada e não cascateia. */
    data object Indefinida : VagaDeTitulo

    /** Decidida, e o detentor está nesta tabela, na [posicao]. */
    data class De(val posicao: Int) : VagaDeTitulo

    /**
     * Decidida, mas sem detentor que possa usá-la: campeão da Série B, de outro
     * país ou rebaixado. Aí sim a vaga desce para o Brasileirão.
     */
    data object SemDetentor : VagaDeTitulo
}

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
     * @param campeaoDaCopa estado da vaga do campeão da Copa do Brasil, que é de
     *        GRUPO. Enquanto a copa não termina ela fica reservada.
     * @param viceDaCopa o mesmo para a vaga do vice, que é de PRÉ.
     * @param posicoesDosCampeoesContinentais campeões vigentes da Libertadores e
     *        da Sul-Americana, quando brasileiros: entram pela vaga estrutural.
     * @param vagasRemanejadas vagas estruturais sem titular que desceram para o
     *        Brasil, 0 ou 1.
     */
    fun distribuir(
        campeaoDaCopa: VagaDeTitulo = VagaDeTitulo.Indefinida,
        viceDaCopa: VagaDeTitulo = VagaDeTitulo.Indefinida,
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

        // ── 3. Pools de vaga por destino, com as vagas de título dobradas ──
        // Uma vaga de título entra na CONTA do pool do seu destino (a do campeão é
        // de GRUPO, a do vice é de PRÉ) assim que a copa termina — tenha detentor
        // elegível (que então ganha um PISO), tenha ido para um rebaixado ou um
        // clube de fora (SemDetentor: volta para a liga). Uma copa EM ANDAMENTO
        // (Indefinida) fica reservada e NÃO entra no pool, senão a liga prometeria
        // ao 5º/6º uma vaga que a copa ainda vai levar.
        fun emJogo(v: VagaDeTitulo) = v !is VagaDeTitulo.Indefinida
        fun pisoDe(v: VagaDeTitulo): Int? = (v as? VagaDeTitulo.De)?.posicao?.takeIf { elegivel(it) }

        var grupoBerths = VAGAS_DE_GRUPO_DA_LIGA + vagasRemanejadas + if (emJogo(campeaoDaCopa)) 1 else 0
        var preBerths = VAGAS_DE_PRE_DA_LIGA + if (emJogo(viceDaCopa)) 1 else 0

        // Posições com PISO de destino garantido (o detentor do título), ainda que
        // a colocação não desse. O campeão tem piso de GRUPO; o vice, de PRÉ.
        val pisoGrupo = pisoDe(campeaoDaCopa)
        val pisoPre = pisoDe(viceDaCopa)

        // ── 4. A liga preenche de cima para baixo, respeitando os pisos ────
        // Regra da "vaga mais benéfica" (Art. 5): quem tem vaga de título mas cai
        // numa colocação que já garante destino MELHOR fica com o melhor, e a vaga
        // de título cascateia mantendo o seu destino. Na prática, o vice (vaga de
        // Pré) que termina na zona de grupos vai aos grupos e a Pré desce.
        var reservaGrupo = if (pisoGrupo != null) 1 else 0  // o campeão precisa de 1 grupo
        var reservaPre = if (pisoPre != null) 1 else 0      // o vice precisa de 1 (Pré ou melhor)
        var restamSulAmericana = VAGAS_SUL_AMERICANA
        for (posicao in 1 until primeiraRebaixada) {
            if (posicao in jaDentro) continue
            when (posicao) {
                pisoGrupo -> {
                    zonas[posicao - 1] = ZonaBrasileirao.LIBERTADORES_GRUPOS
                    jaDentro += posicao
                    grupoBerths--; reservaGrupo = 0
                }
                pisoPre -> {
                    // Sobe aos grupos se há vaga de grupo livre além da do campeão.
                    if (grupoBerths - reservaGrupo > 0) {
                        zonas[posicao - 1] = ZonaBrasileirao.LIBERTADORES_GRUPOS
                        grupoBerths--
                    } else {
                        zonas[posicao - 1] = ZonaBrasileirao.LIBERTADORES_PRE
                        preBerths--
                    }
                    jaDentro += posicao
                    reservaPre = 0
                }
                else -> when {
                    grupoBerths - reservaGrupo > 0 -> {
                        zonas[posicao - 1] = ZonaBrasileirao.LIBERTADORES_GRUPOS
                        grupoBerths--
                    }
                    preBerths - reservaPre > 0 -> {
                        zonas[posicao - 1] = ZonaBrasileirao.LIBERTADORES_PRE
                        preBerths--
                    }
                    restamSulAmericana > 0 -> {
                        zonas[posicao - 1] = ZonaBrasileirao.SUL_AMERICANA
                        restamSulAmericana--
                    }
                }
            }
        }
        return ZonasDoBrasileirao(zonas)
    }
}
