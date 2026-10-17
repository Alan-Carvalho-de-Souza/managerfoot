package br.com.managerfoot.domain.engine

import br.com.managerfoot.data.database.entities.EstiloJogo
import br.com.managerfoot.data.database.entities.MoraleEstado
import br.com.managerfoot.data.database.entities.Posicao
import br.com.managerfoot.data.database.entities.Setor
import br.com.managerfoot.domain.model.*
import kotlin.math.roundToLong
import kotlin.random.Random

// ─────────────────────────────────────────────
//  IATimeRival
//  Simula decisões automáticas dos times rivais:
//  escalação, compras, vendas e contratos.
// ─────────────────────────────────────────────
object IATimeRival {

    // Gera uma escalação automática: titulares + reservas
    // Prioriza jogadores com maior forcaEfetiva(), que já incorpora penalidade de fadiga.
    // Isso faz com que a IA naturalmente evite escalar jogadores excessivamente cansados.
    fun gerarEscalacao(time: Time, elenco: List<Jogador>): Escalacao {
        val disponiveis = elenco.filter { !it.lesionado && !it.suspenso && !it.categoriaBase }
            .sortedByDescending { it.forcaEfetiva() }

        val formacao = time.taticaFormacao   // ex: "4-4-2"
        val slots = parseFormacao(formacao)  // [1(GL), 4, 4, 2] por setor

        val titulares = mutableListOf<JogadorNaEscalacao>()
        val usados = mutableSetOf<Int>()

        // Escolher titulares por setor
        listOf(
            Setor.GOLEIRO to slots[0],
            Setor.DEFESA  to slots[1],
            Setor.MEIO    to slots[2],
            Setor.ATAQUE  to slots[3]
        ).forEach { (setor, qtd) ->
            val candidatos = disponiveis
                .filter { it.id !in usados }
                .filter { it.posicao.setor == setor || it.posicaoSecundaria?.setor == setor }
                .take(qtd)

            candidatos.forEach { j ->
                val posicaoUsada = if (j.posicao.setor == setor) j.posicao
                                   else j.posicaoSecundaria ?: j.posicao
                titulares.add(JogadorNaEscalacao(j, posicaoUsada))
                usados.add(j.id)
            }
        }

        // Se ainda faltar titular (elenco pequeno / desbalanceado / muitas ausências),
        // completar com qualquer jogador disponível ainda restante, escalando-o na
        // posição natural. Isso gera penalidade de improviso naturalmente na força
        // efetiva, mas evita que o time entre em campo com menos de 11 jogadores.
        if (titulares.size < 11) {
            val faltam = 11 - titulares.size
            val complemento = disponiveis
                .filter { it.id !in usados }
                .take(faltam)
            complemento.forEach { j ->
                titulares.add(JogadorNaEscalacao(j, j.posicao))
                usados.add(j.id)
            }
        }

        // Completar com qualquer jogador disponível como reserva (até 11)
        val restantes = disponiveis.filter { it.id !in usados }
        val reservas = restantes.take(11).map {
            JogadorNaEscalacao(it, it.posicao)
        }

        return Escalacao(time = time, titulares = titulares, reservas = reservas)
    }

    // Decide quais jogadores a IA tenta contratar no mercado
    fun decidirContratacoes(
        time: Time,
        elencoAtual: List<Jogador>,
        mercado: List<Jogador>,
        orcamento: Long
    ): List<OfertaTransferencia> {
        if (elencoAtual.size >= 35) return emptyList()
        val forcaMedia = elencoAtual.map { it.forca }.average().toInt()
        val carencias = detectarCarencias(elencoAtual, time.taticaFormacao)
        val ofertas = mutableListOf<OfertaTransferencia>()
        var saldoDisponivel = (orcamento * 0.5).roundToLong() // usa até 50% do saldo

        for (posicao in carencias) {
            if (saldoDisponivel <= 0) break
            val alvo = mercado
                .filter { it.posicao == posicao || it.posicaoSecundaria == posicao }
                .filter { it.forca > forcaMedia - 5 }   // não contrata muito fraco
                .filter { it.valorMercado <= saldoDisponivel }
                .filter { it.timeId == null || it.timeId != time.id }
                .maxByOrNull { it.forca }
                ?: continue

            val valorOferta = (alvo.valorMercado * Random.nextDouble(0.9, 1.1)).roundToLong()
            ofertas.add(OfertaTransferencia(
                jogadorId = alvo.id,
                timeCompradorId = time.id,
                timeVendedorId = alvo.timeId,
                valor = valorOferta,
                salarioProposto = (alvo.salario * Random.nextDouble(1.0, 1.15)).roundToLong(),
                contratoAnos = Random.nextInt(2, 4)
            ))
            saldoDisponivel -= valorOferta
        }

        return ofertas
    }

    // Detecta quais posições estão carentes no elenco
    fun detectarCarencias(elenco: List<Jogador>, formacao: String): List<Posicao> {
        val slots = parseFormacao(formacao)
        val carencias = mutableListOf<Posicao>()

        val goleiros = elenco.count { it.posicao.setor == Setor.GOLEIRO }
        val defensores = elenco.count { it.posicao.setor == Setor.DEFESA }
        val meias = elenco.count { it.posicao.setor == Setor.MEIO }
        val atacantes = elenco.count { it.posicao.setor == Setor.ATAQUE }

        if (goleiros < 2) carencias.add(Posicao.GOLEIRO)
        if (defensores < slots[1] + 2) carencias.add(Posicao.ZAGUEIRO)
        if (meias < slots[2] + 2) carencias.add(Posicao.VOLANTE)
        if (atacantes < slots[3] + 1) carencias.add(Posicao.CENTROAVANTE)

        return carencias
    }

    // Parseia formação normalizando para [1(GL), nDef, nMeio, nAtk].
    // Formações com múltiplos grupos centrais (ex: 4-3-2-1, 4-1-2-1-2) têm
    // todos os grupos intermediários somados como MEIO.
    private fun parseFormacao(formacao: String): List<Int> {
        return try {
            val partes = formacao.split("-").map { it.toInt() }
            val nDef  = partes.first()
            val nAtk  = partes.last()
            val nMeio = when {
                partes.size >= 3 -> partes.subList(1, partes.size - 1).sum()
                else             -> partes.getOrElse(1) { 4 }
            }
            listOf(1, nDef, nMeio, nAtk)
        } catch (e: Exception) {
            listOf(1, 4, 4, 2) // fallback
        }
    }
}

// ─────────────────────────────────────────────
//  MotorFinanceiro
//  Calcula receitas, despesas e processa o
//  fechamento financeiro mensal do clube.
// ─────────────────────────────────────────────
object MotorFinanceiro {

    // Fator de ocupação baseado na reputação do clube
    private fun fatorReputacao(reputacao: Float): Double = when {
        reputacao >= 90f -> 0.95
        reputacao >= 70f -> 0.80
        reputacao >= 50f -> 0.65
        reputacao >= 30f -> 0.50
        else             -> 0.35
    }

    /**
     * Fator do PREÇO DE REFERÊNCIA da bilheteria conforme a reputação do clube.
     * O preço de referência é aquele em que a elasticidade fica neutra (fatorPreço≈1):
     * clubes grandes sustentam ingressos mais caros sem esvaziar; pequenos são
     * penalizados se cobrarem caro. Escala linear de 0,8 (reputação 30) a 2,0 (rep 99).
     */
    fun fatorReferenciaBilheteria(reputacao: Float): Double {
        val t = ((reputacao - 30f) / 69f).coerceIn(0f, 1f)
        return 0.8 + (2.0 - 0.8) * t
    }

    // Fator de rivalidade baseado no nível do adversário
    private fun fatorRivalidade(adversarioNivel: Int): Double = when {
        adversarioNivel >= 8 -> 1.25  // clássico / rival forte
        adversarioNivel >= 6 -> 1.10
        adversarioNivel >= 4 -> 1.00
        else                 -> 0.85
    }

    /**
     * Bônus de público para clássicos regionais — mesma cidade tem impacto maior
     * que mesmo estado. Fla-Flu, Grenal, Choque-Rei, Superclásico argentino etc.
     */
    private fun fatorRivalidadeRegional(mandante: Time, visitante: Time?): Double {
        if (visitante == null || visitante.id == mandante.id) return 1.0
        return when {
            mandante.cidade.isNotBlank() &&
                mandante.cidade.equals(visitante.cidade, ignoreCase = true) -> 1.35  // clássico municipal
            mandante.estado.isNotBlank() &&
                mandante.estado.equals(visitante.estado, ignoreCase = true) -> 1.15  // clássico estadual
            else -> 1.0
        }
    }

    /** Retorna true quando o confronto é clássico regional (mesma cidade ou estado). */
    fun ehClassicoRegional(mandante: Time, visitante: Time): Boolean =
        fatorRivalidadeRegional(mandante, visitante) > 1.0

    /**
     * Calcula público e receita de bilheteria para uma partida em casa,
     * dividindo o estádio em três setores:
     *  - Arquibancada (60% dos assentos): preço base
     *  - Cadeira     (30% dos assentos): 2× preço base
     *  - Camarote    (10% dos assentos): 5× preço base
     *
     * @param adversario opcional — quando informado, aplica bônus regional se for
     *                   um clássico da mesma cidade/estado do mandante.
     * @return Pair(torcedores, receita em centavos)
     */
    fun calcularPublico(time: Time, adversarioNivel: Int, adversario: Time? = null): Pair<Int, Long> {
        val fatorOcup  = fatorReputacao(time.reputacao) *
                         fatorRivalidade(adversarioNivel) *
                         fatorRivalidadeRegional(time, adversario)
        val publico    = (time.estadioCapacidade * fatorOcup).toInt().coerceAtLeast(0)

        val arquibancada = (publico * 0.60).toLong()
        val cadeira      = (publico * 0.30).toLong()
        val camarote     = (publico * 0.10).toLong()

        val precoBase = time.precoIngresso
        val receita   = arquibancada * precoBase +
                        cadeira      * precoBase * 2 +
                        camarote     * precoBase * 5

        return Pair(publico, receita)
    }

    // Mantém compatibilidade com chamadas que precisam apenas da receita total
    fun calcularBilheteria(time: Time, adversarioNivel: Int): Long =
        calcularPublico(time, adversarioNivel).second

    /**
     * Bilheteria SETORIZADA (jogos em casa do jogador): usa a capacidade real de cada
     * setor do estádio e o preço definido pelo usuário, com **elasticidade de preço**.
     *
     * Para cada setor: ocupação = capacidade × fatorDemanda × fatorPreço, onde
     *  - fatorDemanda = reputação × rivalidade × rivalidade regional (mesmo da versão flat);
     *  - fatorPreço   = (2 − preço/precoReferência) limitado a [0.10, 1.30].
     * O preço de referência do setor é o preço-base do clube (`time.precoIngresso`)
     * multiplicado por 1× (arquibancada), 2× (cadeira) e 5× (camarote). Assim, cobrar
     * no preço de referência maximiza a receita; barato lota mas rende menos; caro esvazia.
     */
    fun calcularPublicoSetorizado(
        time: Time,
        adversarioNivel: Int,
        adversario: Time?,
        capArquibancada: Int, capCadeira: Int, capCamarote: Int,
        precoArquibancada: Long, precoCadeira: Long, precoCamarote: Long
    ): Pair<Int, Long> {
        val fatorDemanda = fatorReputacao(time.reputacao) *
                           fatorRivalidade(adversarioNivel) *
                           fatorRivalidadeRegional(time, adversario)
        val base = time.precoIngresso.coerceAtLeast(1)
        // Preço de referência escala com a reputação: clube grande sustenta ingresso mais caro.
        val fatorRef = fatorReferenciaBilheteria(time.reputacao)

        fun setor(cap: Int, preco: Long, refMult: Int): Pair<Int, Long> {
            if (cap <= 0) return 0 to 0L
            val ref = (base * refMult * fatorRef).toLong().coerceAtLeast(1)
            val fatorPreco = (2.0 - preco.coerceAtLeast(0).toDouble() / ref).coerceIn(0.10, 1.30)
            val ocup = (cap * fatorDemanda * fatorPreco).toInt().coerceIn(0, cap)
            return ocup to ocup.toLong() * preco
        }

        val (oA, rA) = setor(capArquibancada, precoArquibancada, 1)
        val (oC, rC) = setor(capCadeira,      precoCadeira,      2)
        val (oM, rM) = setor(capCamarote,     precoCamarote,     5)
        return (oA + oC + oM) to (rA + rC + rM)
    }

    /**
     * Deriva o nível do clube (1..10) a partir da sua reputação (0..100).
     * Usado em [GameRepository.recalcularNiveisGlobais] ao final de cada
     * temporada para que clubes que conquistam títulos / sobem de divisão
     * vejam o `nivel` subir, e times rebaixados / sem títulos vejam cair.
     *
     * Mapeamento simples e estável:
     *  reputação 0–9   → nível 1
     *  reputação 10–19 → nível 2
     *  ...
     *  reputação 90+   → nível 10
     */
    fun nivelDerivadoDeReputacao(reputacao: Float): Int =
        (reputacao / 10f).toInt().coerceIn(1, 10)

    // Patrocínio mensal baseado na reputação do clube
    fun calcularPatrocinioMensal(time: Time): Long {
        val base = when {
            time.reputacao >= 90f -> 50_000_000_00L  // R$ 50.000.000
            time.reputacao >= 70f -> 20_000_000_00L
            time.reputacao >= 50f -> 8_000_000_00L
            time.reputacao >= 30f -> 3_000_000_00L
            else                  -> 1_000_000_00L
        }
        return (base - penalidadeDivisaoPatrocinio(time.divisao)).coerceAtLeast(500_000_00L)
    }

    /**
     * Desconto de patrocínio por divisão — aplica-se SÓ às divisões inferiores do BRASIL
     * (2=B, 3=C, 4=D). Ligas estrangeiras usam códigos de divisão altos (La Liga=13,
     * Serie A=14, etc.) e não são penalizadas; antes a fórmula zerava o patrocínio delas.
     */
    fun penalidadeDivisaoPatrocinio(divisao: Int): Long =
        if (divisao in 2..4) (divisao - 1) * 500_000_00L else 0L

    // Folha salarial mensal
    fun calcularFolhaMensal(elenco: List<Jogador>): Long =
        elenco.sumOf { it.salario }

    // Custo de manutenção e infraestrutura
    fun calcularDespesaInfraestrutura(time: Time): Long =
        (time.estadioCapacidade * 2_50L)  // R$ 2,50 por assento por mês (manutenção)

    // Processo de encerramento de mês: retorna o saldo atualizado
    data class FechamentoMensal(
        val receitaBilheteria: Long,
        val receitaPatrocinio: Long,
        val despesaSalarios: Long,
        val despesaInfraestrutura: Long,
        val saldoFinal: Long,
        val saldoAnterior: Long
    ) {
        val lucroOuPrejuizo: Long get() = saldoFinal - saldoAnterior
    }

    fun processarFechamentoMensal(
        time: Time,
        elenco: List<Jogador>,
        partidasEmCasa: Int,
        nivelMedioAdversarios: Int = 5
    ): FechamentoMensal {
        val bilheteria = calcularBilheteria(time, nivelMedioAdversarios) * partidasEmCasa
        val patrocinio = calcularPatrocinioMensal(time)
        val folha = calcularFolhaMensal(elenco)
        val infraestrutura = calcularDespesaInfraestrutura(time)
        val novoSaldo = time.saldo + bilheteria + patrocinio - folha - infraestrutura

        return FechamentoMensal(
            receitaBilheteria = bilheteria,
            receitaPatrocinio = patrocinio,
            despesaSalarios = folha,
            despesaInfraestrutura = infraestrutura,
            saldoFinal = novoSaldo,
            saldoAnterior = time.saldo
        )
    }

    // Custo de ampliação do estádio
    fun calcularCustoAmpliacaoEstadio(capacidadeAtual: Int, novaCapacidade: Int): Long {
        val assentosNovos = novaCapacidade - capacidadeAtual
        return assentosNovos * 5_000_00L  // R$ 500 por assento novo
    }

    // ── Metas de temporada ────────────────────────────────────
    /**
     * Meta oficial do clube para a temporada. Calculada a partir da reputação
     * do clube e da divisão em que ele joga. Serve para dar direção ao usuário
     * (o que se espera dele no ano) e para o técnico ser julgado ao final.
     */
    enum class MetaTemporada(val titulo: String, val descricao: String) {
        TITULO             ("Vencer o campeonato",  "O clube não aceita menos que o título"),
        G4                 ("Terminar no G4",        "Vaga em torneio continental é obrigação"),
        TOP_10             ("Ficar no Top 10",       "Terminar entre os 10 primeiros"),
        MEIO_TABELA        ("Meio de tabela",        "Uma campanha tranquila, sem sustos"),
        FUGIR_REBAIXAMENTO ("Não ser rebaixado",     "Se manter na divisão é o essencial"),
        ACESSO             ("Conquistar o acesso",   "Terminar no G4 e subir de divisão"),
        SOBREVIVER         ("Sobreviver",            "Elenco modesto — evitar humilhação")
    }

    /**
     * Deriva a meta de temporada a partir do time e da divisão em que joga.
     * Reputação vai de ~30 a ~99. Regras:
     *  - Primeiras divisões (Série A/Primera/Premier/La Liga):
     *      rep ≥ 90 → TITULO | ≥ 78 → G4 | ≥ 66 → TOP_10 | senão → NÃO SER REBAIXADO
     *    (times recém-promovidos, de reputação baixa, caem naturalmente em "não ser
     *     rebaixado"; times médios miram o Top 10; só a elite mira o título/G4.)
     *  - Divisões inferiores (B/C/D, Championship, Segundas):
     *      rep ≥ 70 → ACESSO | ≥ 52 → MEIO_TABELA | senão → SOBREVIVER
     */
    fun calcularMetaTemporada(time: Time): MetaTemporada {
        val rep = time.reputacao
        // Primeiras divisões (todos os países): Série A Brasil = 1, Primera Argentina = 5,
        // Primera Uruguai = 9, Premier League = 11, La Liga = 13, Serie A italiana = 14.
        val ehPrimeiraDivisao = time.divisao == 1 || time.divisao == 5 || time.divisao == 9 ||
                                time.divisao == 11 || time.divisao == 13 || time.divisao == 14
        return if (ehPrimeiraDivisao) {
            when {
                rep >= 90f -> MetaTemporada.TITULO
                rep >= 78f -> MetaTemporada.G4
                rep >= 66f -> MetaTemporada.TOP_10
                else       -> MetaTemporada.FUGIR_REBAIXAMENTO
            }
        } else {
            when {
                rep >= 70f -> MetaTemporada.ACESSO
                rep >= 52f -> MetaTemporada.MEIO_TABELA
                else       -> MetaTemporada.SOBREVIVER
            }
        }
    }

    // Atualiza morale do elenco com base em resultados recentes
    fun calcularMorale(
        vitoriasUltimas5: Int,
        derrotasUltimas5: Int
    ): MoraleEstado = when {
        vitoriasUltimas5 >= 4 -> MoraleEstado.EXCELENTE
        vitoriasUltimas5 >= 3 -> MoraleEstado.BOM
        derrotasUltimas5 >= 4 -> MoraleEstado.REVOLTADO
        derrotasUltimas5 >= 3 -> MoraleEstado.INSATISFEITO
        else -> MoraleEstado.NORMAL
    }
}

// ─────────────────────────────────────────────
//  MotorPatrocinio
//  Gera as ofertas de patrocínio disponíveis
//  para o clube do jogador com base na reputação.
//  São 3 tiers; tiers maiores exigem reputação
//  mínima e oferecem valores mais altos.
// ─────────────────────────────────────────────
object MotorPatrocinio {

    enum class Tier(
        val tipo: Int,
        val label: String,
        val descricao: String,
        val fatorValor: Double,
        val reputacaoMinima: Int
    ) {
        REGIONAL (1, "Patrocinador Regional",  "empresa local",    0.30, 0),
        NACIONAL (2, "Patrocinador Nacional",  "empresa nacional", 0.60, 78),
        PREMIUM  (3, "Patrocinador Premium",   "multinacional",    1.00, 90)
    }

    /**
     * Faixa de patrocínio a que o clube TEM DIREITO, conforme a reputação:
     *  - reputação ≥ 90         → Premium
     *  - reputação 78–89        → Nacional
     *  - reputação ≤ 77         → Regional
     * Cada clube fecha apenas com o patrocinador da sua faixa (exclusivo).
     */
    fun tierElegivel(reputacao: Int): Tier = when {
        reputacao >= 90 -> Tier.PREMIUM
        reputacao >= 78 -> Tier.NACIONAL
        else            -> Tier.REGIONAL
    }

    private val nomesPorTier = mapOf(
        Tier.REGIONAL to listOf("Banco Regional", "Supermercado ZéMais",  "Supermercados Unidos"),
        Tier.NACIONAL to listOf("Construtora Nacional", "Banco Nacional", "Telecom Plus"),
        Tier.PREMIUM  to listOf("Multinacional Corp",  "Google",    "Energia Máxima")
    )

    /** Valor base anual para a reputação dada (de R$1M a R$100M). */
    private fun valorBase(reputacao: Int): Long {
        val rep = reputacao.coerceIn(1, 100)
        val minimo = 1_000_000_00L    // R$ 1.000.000,00
        val maximo = 100_000_000_00L  // R$ 100.000.000,00
        return minimo + ((rep - 1).toLong() * (maximo - minimo) / 99L)
    }

    data class OfertaPatrocinio(
        val tipo: Int,
        val tier: Tier,
        val nomeEmpresa: String,
        val valorAnual: Long,
        val disponivel: Boolean
    )

    /**
     * Gera as 3 ofertas de patrocínio para a temporada.
     * O [temporadaId] é usado para variar os nomes de empresa a cada temporada.
     */
    fun gerarOfertas(reputacao: Int, temporadaId: Int): List<OfertaPatrocinio> {
        val base = valorBase(reputacao)
        val elegivel = tierElegivel(reputacao)
        return Tier.values().map { tier ->
            val empresas = nomesPorTier[tier] ?: listOf("Patrocinador")
            val empresa  = empresas[temporadaId % empresas.size]
            val valor    = (base * tier.fatorValor).toLong()
            OfertaPatrocinio(
                tipo         = tier.tipo,
                tier         = tier,
                nomeEmpresa  = empresa,
                valorAnual   = valor,
                // Exclusivo por faixa: só o patrocinador da faixa do clube fica disponível.
                disponivel   = tier == elegivel
            )
        }
    }
}
