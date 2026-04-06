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

        // Completar com qualquer jogador disponível se faltar titular
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

    // Fator de rivalidade baseado no nível do adversário
    private fun fatorRivalidade(adversarioNivel: Int): Double = when {
        adversarioNivel >= 8 -> 1.25  // clássico / rival forte
        adversarioNivel >= 6 -> 1.10
        adversarioNivel >= 4 -> 1.00
        else                 -> 0.85
    }

    /**
     * Calcula público e receita de bilheteria para uma partida em casa,
     * dividindo o estádio em três setores:
     *  - Arquibancada (60% dos assentos): preço base
     *  - Cadeira     (30% dos assentos): 2× preço base
     *  - Camarote    (10% dos assentos): 5× preço base
     *
     * @return Pair(torcedores, receita em centavos)
     */
    fun calcularPublico(time: Time, adversarioNivel: Int): Pair<Int, Long> {
        val fatorOcup  = fatorReputacao(time.reputacao) * fatorRivalidade(adversarioNivel)
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

    // Patrocínio mensal baseado na reputação do clube
    fun calcularPatrocinioMensal(time: Time): Long {
        val base = when {
            time.reputacao >= 90f -> 50_000_000_00L  // R$ 5.000.000
            time.reputacao >= 70f -> 20_000_000_00L
            time.reputacao >= 50f -> 8_000_000_00L
            time.reputacao >= 30f -> 3_000_000_00L
            else                  -> 1_000_000_00L
        }
        return base + (time.divisao - 1) * -500_000_00L // divisões menores = menos patrocínio
    }

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
        NACIONAL (2, "Patrocinador Nacional",  "empresa nacional", 0.60, 25),
        PREMIUM  (3, "Patrocinador Premium",   "multinacional",    1.00, 50)
    }

    private val nomesPorTier = mapOf(
        Tier.REGIONAL to listOf("Banco Regional", "Cerveja Local BR",  "Supermercados Unidos"),
        Tier.NACIONAL to listOf("Construtora Nacional", "Banco Nacional BR", "Telecom Plus"),
        Tier.PREMIUM  to listOf("Multinacional Corp",  "FinTech Global",    "Energia Máxima")
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
        return Tier.values().map { tier ->
            val empresas = nomesPorTier[tier] ?: listOf("Patrocinador")
            val empresa  = empresas[temporadaId % empresas.size]
            val valor    = (base * tier.fatorValor).toLong()
            OfertaPatrocinio(
                tipo         = tier.tipo,
                tier         = tier,
                nomeEmpresa  = empresa,
                valorAnual   = valor,
                disponivel   = reputacao >= tier.reputacaoMinima
            )
        }
    }
}
