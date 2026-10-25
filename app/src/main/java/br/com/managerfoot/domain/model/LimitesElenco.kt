package br.com.managerfoot.domain.model

/**
 * Tamanho máximo do elenco de um clube.
 *
 * Antes havia **um limite só, de 35, somando sênior e base** — a base competia por
 * vaga com o time principal, e formar juniores podia travar uma contratação. Agora
 * são duas contas independentes: o elenco profissional tem o seu teto e a base tem
 * o dela, e encher uma não bloqueia a outra.
 *
 * Promover um júnior move o jogador de uma conta para a outra: ocupa uma vaga no
 * elenco e libera uma na base, então a promoção só é permitida com o elenco
 * profissional abaixo do teto.
 */
object LimitesElenco {

    /** Jogadores profissionais (categoriaBase = 0). */
    const val MAX_SENIORES = 40

    /** Jogadores da base (categoriaBase = 1). */
    const val MAX_BASE = 15

    /** Teto somado, só para exibição — nunca é verificado diretamente. */
    const val MAX_TOTAL = MAX_SENIORES + MAX_BASE

    fun elencoCheio(seniores: Int) = seniores >= MAX_SENIORES
    fun baseCheia(juniores: Int) = juniores >= MAX_BASE

    /** Vagas livres no elenco profissional, nunca negativo. */
    fun vagasNoElenco(seniores: Int) = (MAX_SENIORES - seniores).coerceAtLeast(0)

    /** Vagas livres na base, nunca negativo. */
    fun vagasNaBase(juniores: Int) = (MAX_BASE - juniores).coerceAtLeast(0)
}
