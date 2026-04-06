package br.com.managerfoot.data.datasource

import android.content.Context
import br.com.managerfoot.data.database.entities.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────
//  SeedDataSource
//  Lê o arquivo assets/seed_brasileirao.json e
//  converte para entidades Room.
// ─────────────────────────────────────────────
@Singleton
class SeedDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class SeedData(
        val times: List<TimeEntity>,
        val jogadores: List<JogadorEntity>
    )

    suspend fun carregar(): SeedData = withContext(Dispatchers.IO) {
        val json = context.assets.open("seed_brasileirao.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val root = JSONObject(json)
        val timesJson  = root.getJSONArray("times")
        val jogadoresJson = root.getJSONArray("jogadores")
        SeedData(
            times = parseTimes(timesJson),
            jogadores = parseJogadores(jogadoresJson)
        )
    }

    private fun parseTimes(arr: JSONArray): List<TimeEntity> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TimeEntity(
                id            = o.getInt("id"),
                nome          = o.getString("nome"),
                cidade        = o.getString("cidade"),
                estado        = o.getString("estado"),
                divisao       = when (o.optString("divisao").uppercase()) {
                    "A" -> 1; "B" -> 2; "C" -> 3; "D" -> 4
                    else -> o.optInt("divisao", 1)
                },
                nivel         = o.getInt("nivel"),
                saldo         = o.getLong("saldo"),
                estadioNome   = o.getString("estadio_nome"),
                estadioCapacidade = o.getInt("estadio_capacidade"),
                precoIngresso = o.getLong("preco_ingresso"),
                taticaFormacao = o.getString("tatica"),
                estiloJogo    = EstiloJogo.valueOf(o.getString("estilo")),
                reputacao     = o.getInt("reputacao").toFloat(),
                escudoRes     = o.optString("escudo_res", "")
            )
        }

    private fun parseJogadores(arr: JSONArray): List<JogadorEntity> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            JogadorEntity(
                id              = o.getInt("id"),
                timeId          = if (o.isNull("time_id")) null else o.getInt("time_id"),
                nome            = o.getString("nome"),
                nomeAbreviado   = o.getString("nome_abrev"),
                idade           = o.getInt("idade"),
                posicao         = Posicao.valueOf(o.getString("posicao")),
                posicaoSecundaria = o.optString("posicao_sec", "")
                    .takeIf { it.isNotBlank() }?.let { Posicao.valueOf(it) },
                forca           = o.getInt("forca"),
                tecnica         = o.getInt("tecnica"),
                passe           = o.getInt("passe"),
                velocidade      = o.getInt("velocidade"),
                finalizacao     = o.getInt("finalizacao"),
                defesa          = o.getInt("defesa"),
                fisico          = o.getInt("fisico"),
                salario         = o.getLong("salario"),
                contratoAnos    = o.getInt("contrato"),
                valorMercado    = o.getLong("valor_mercado")
            )
        }
}

// ─────────────────────────────────────────────
//  Seed JSON embutido — gerado em runtime para
//  o arquivo assets/seed_brasileirao.json.
//  Representa os 20 clubes da Série A com
//  elencos fictícios mas plausíveis.
// ─────────────────────────────────────────────
object SeedJsonBuilder {

    fun build(): String {
        val times = buildTimesJson()
        val jogadores = buildJogadoresJson()
        return """{"times":$times,"jogadores":$jogadores}"""
    }

    private fun buildTimesJson(): String {
        val lista = listOf(
            mapOf("id" to 1,  "nome" to "Flamengo",          "cidade" to "Rio de Janeiro", "estado" to "RJ", "divisao" to "A", "nivel" to 10, "saldo" to 50_000_000_00L, "estadio_nome" to "Maracanã",             "estadio_capacidade" to 78838, "preco_ingresso" to 8000L,  "tatica" to "4-2-3-1", "estilo" to "OFENSIVO",       "reputacao" to 98),
            mapOf("id" to 2,  "nome" to "Palmeiras",          "cidade" to "São Paulo",      "estado" to "SP", "divisao" to "A", "nivel" to 10, "saldo" to 55_000_000_00L, "estadio_nome" to "Allianz Parque",       "estadio_capacidade" to 43713, "preco_ingresso" to 9000L,  "tatica" to "4-4-2",   "estilo" to "EQUILIBRADO",    "reputacao" to 97),
            mapOf("id" to 3,  "nome" to "Atlético-MG",        "cidade" to "Belo Horizonte", "estado" to "MG", "divisao" to "A", "nivel" to 9,  "saldo" to 40_000_000_00L, "estadio_nome" to "Arena MRV",            "estadio_capacidade" to 46000, "preco_ingresso" to 7500L,  "tatica" to "3-5-2",   "estilo" to "EQUILIBRADO",    "reputacao" to 94),
            mapOf("id" to 4,  "nome" to "São Paulo",          "cidade" to "São Paulo",      "estado" to "SP", "divisao" to "A", "nivel" to 9,  "saldo" to 38_000_000_00L, "estadio_nome" to "Morumbis",             "estadio_capacidade" to 66795, "preco_ingresso" to 7000L,  "tatica" to "4-4-2",   "estilo" to "EQUILIBRADO",    "reputacao" to 93),
            mapOf("id" to 5,  "nome" to "Corinthians",        "cidade" to "São Paulo",      "estado" to "SP", "divisao" to "A", "nivel" to 9,  "saldo" to 30_000_000_00L, "estadio_nome" to "Neo Química Arena",    "estadio_capacidade" to 49205, "preco_ingresso" to 7000L,  "tatica" to "4-3-3",   "estilo" to "EQUILIBRADO",    "reputacao" to 95),
            mapOf("id" to 6,  "nome" to "Fluminense",         "cidade" to "Rio de Janeiro", "estado" to "RJ", "divisao" to "A", "nivel" to 8,  "saldo" to 25_000_000_00L, "estadio_nome" to "Maracanã",             "estadio_capacidade" to 78838, "preco_ingresso" to 6500L,  "tatica" to "4-3-3",   "estilo" to "OFENSIVO",       "reputacao" to 90),
            mapOf("id" to 7,  "nome" to "Botafogo",           "cidade" to "Rio de Janeiro", "estado" to "RJ", "divisao" to "A", "nivel" to 8,  "saldo" to 28_000_000_00L, "estadio_nome" to "Nilton Santos",        "estadio_capacidade" to 46000, "preco_ingresso" to 6000L,  "tatica" to "4-4-2",   "estilo" to "CONTRA_ATAQUE",  "reputacao" to 88),
            mapOf("id" to 8,  "nome" to "Internacional",      "cidade" to "Porto Alegre",   "estado" to "RS", "divisao" to "A", "nivel" to 8,  "saldo" to 30_000_000_00L, "estadio_nome" to "Beira-Rio",            "estadio_capacidade" to 50128, "preco_ingresso" to 6500L,  "tatica" to "4-4-2",   "estilo" to "EQUILIBRADO",    "reputacao" to 89),
            mapOf("id" to 9,  "nome" to "Grêmio",             "cidade" to "Porto Alegre",   "estado" to "RS", "divisao" to "A", "nivel" to 8,  "saldo" to 28_000_000_00L, "estadio_nome" to "Arena do Grêmio",     "estadio_capacidade" to 55000, "preco_ingresso" to 6000L,  "tatica" to "3-5-2",   "estilo" to "DEFENSIVO",      "reputacao" to 88),
            mapOf("id" to 10, "nome" to "Cruzeiro",           "cidade" to "Belo Horizonte", "estado" to "MG", "divisao" to "A", "nivel" to 7,  "saldo" to 20_000_000_00L, "estadio_nome" to "Mineirão",             "estadio_capacidade" to 61170, "preco_ingresso" to 5500L,  "tatica" to "4-4-2",   "estilo" to "EQUILIBRADO",    "reputacao" to 85),
            mapOf("id" to 11, "nome" to "Athletico-PR",       "cidade" to "Curitiba",       "estado" to "PR", "divisao" to "A", "nivel" to 7,  "saldo" to 22_000_000_00L, "estadio_nome" to "Ligga Arena",          "estadio_capacidade" to 42372, "preco_ingresso" to 5500L,  "tatica" to "4-3-3",   "estilo" to "CONTRA_ATAQUE",  "reputacao" to 84),
            mapOf("id" to 12, "nome" to "Vasco da Gama",      "cidade" to "Rio de Janeiro", "estado" to "RJ", "divisao" to "A", "nivel" to 7,  "saldo" to 18_000_000_00L, "estadio_nome" to "São Januário",         "estadio_capacidade" to 21880, "preco_ingresso" to 5000L,  "tatica" to "4-4-2",   "estilo" to "EQUILIBRADO",    "reputacao" to 83),
            mapOf("id" to 13, "nome" to "Fortaleza",          "cidade" to "Fortaleza",      "estado" to "CE", "divisao" to "A", "nivel" to 7,  "saldo" to 15_000_000_00L, "estadio_nome" to "Castelão",             "estadio_capacidade" to 63903, "preco_ingresso" to 4500L,  "tatica" to "4-4-2",   "estilo" to "DEFENSIVO",      "reputacao" to 80),
            mapOf("id" to 14, "nome" to "Bahia",              "cidade" to "Salvador",       "estado" to "BA", "divisao" to "A", "nivel" to 6,  "saldo" to 12_000_000_00L, "estadio_nome" to "Arena Fonte Nova",     "estadio_capacidade" to 49000, "preco_ingresso" to 4000L,  "tatica" to "4-4-2",   "estilo" to "EQUILIBRADO",    "reputacao" to 78),
            mapOf("id" to 15, "nome" to "Bragantino",         "cidade" to "Bragança Paulista","estado" to "SP","divisao" to "A", "nivel" to 6,  "saldo" to 14_000_000_00L, "estadio_nome" to "Nabizão",              "estadio_capacidade" to 17000, "preco_ingresso" to 4000L,  "tatica" to "4-3-3",   "estilo" to "OFENSIVO",       "reputacao" to 77),
            mapOf("id" to 16, "nome" to "Criciúma",           "cidade" to "Criciúma",       "estado" to "SC", "divisao" to "A", "nivel" to 5,  "saldo" to 8_000_000_00L,  "estadio_nome" to "Heriberto Hülse",     "estadio_capacidade" to 19300, "preco_ingresso" to 3500L,  "tatica" to "4-4-2",   "estilo" to "DEFENSIVO",      "reputacao" to 70),
            mapOf("id" to 17, "nome" to "Juventude",          "cidade" to "Caxias do Sul",  "estado" to "RS", "divisao" to "A", "nivel" to 5,  "saldo" to 7_000_000_00L,  "estadio_nome" to "Alfredo Jaconi",      "estadio_capacidade" to 14950, "preco_ingresso" to 3000L,  "tatica" to "4-4-2",   "estilo" to "EQUILIBRADO",    "reputacao" to 68),
            mapOf("id" to 18, "nome" to "Cuiabá",             "cidade" to "Cuiabá",         "estado" to "MT", "divisao" to "A", "nivel" to 5,  "saldo" to 7_500_000_00L,  "estadio_nome" to "Arena Pantanal",      "estadio_capacidade" to 42968, "preco_ingresso" to 3000L,  "tatica" to "4-4-2",   "estilo" to "DEFENSIVO",      "reputacao" to 66),
            mapOf("id" to 19, "nome" to "Atlético-GO",        "cidade" to "Goiânia",        "estado" to "GO", "divisao" to "A", "nivel" to 5,  "saldo" to 6_500_000_00L,  "estadio_nome" to "Serrinha",            "estadio_capacidade" to 13500, "preco_ingresso" to 2800L,  "tatica" to "4-4-2",   "estilo" to "CONTRA_ATAQUE",  "reputacao" to 64),
            mapOf("id" to 20, "nome" to "Vitória",            "cidade" to "Salvador",       "estado" to "BA", "divisao" to "A", "nivel" to 5,  "saldo" to 6_000_000_00L,  "estadio_nome" to "Barradão",            "estadio_capacidade" to 35000, "preco_ingresso" to 2500L,  "tatica" to "4-4-2",   "estilo" to "EQUILIBRADO",    "reputacao" to 62),
        )
        return "[${lista.joinToString(",") { m ->
            """{${m.entries.joinToString(",") { (k,v) ->
                if (v is String) "\"$k\":\"$v\"" else "\"$k\":$v"
            }}}"""
        }}]"
    }

    private fun buildJogadoresJson(): String {
        // Gerar elencos fictícios por time (25 jogadores cada)
        val jogadores = mutableListOf<Map<String, Any>>()
        var jogId = 1

        val posicoesPorFormacao = mapOf(
            "GOLEIRO" to 2, "ZAGUEIRO" to 4, "LATERAL_DIREITO" to 2,
            "LATERAL_ESQUERDO" to 2, "VOLANTE" to 3, "MEIA_CENTRAL" to 3,
            "MEIA_ATACANTE" to 3, "CENTROAVANTE" to 3, "SEGUNDA_ATACANTE" to 3
        )

        for (timeId in 1..40) {
            val nivelBase = when (timeId) {
                in 1..3   -> 82; in 4..6   -> 76; in 7..10  -> 71
                in 11..15 -> 65; in 16..20 -> 58  // Série A
                in 21..25 -> 62; in 26..30 -> 57; in 31..35 -> 53; in 36..40 -> 49  // Série B
                else -> 45
            }
            posicoesPorFormacao.forEach { (posicao, qtd) ->
                repeat(qtd) { idx ->
                    val idades = listOf(20, 22, 24, 25, 27, 28, 30, 32)
                    val idade = idades[(idx + timeId) % idades.size]
                    val forca = (nivelBase + (-8..8).random()).coerceIn(40, 99)
                    val salBase = when {
                        nivelBase >= 80 -> 400_000_00L
                        nivelBase >= 75 -> 200_000_00L
                        nivelBase >= 70 -> 100_000_00L
                        nivelBase >= 65 -> 50_000_00L
                        else -> 25_000_00L
                    }
                    val salario = salBase + (-salBase/4..salBase/4).random()
                    val valorMercado = salario * 36L

                    jogadores.add(mapOf(
                        "id" to jogId++,
                        "time_id" to timeId,
                        "nome" to nomeFicticio(jogId, timeId),
                        "nome_abrev" to "Jog. ${jogId-1}",
                        "idade" to idade,
                        "posicao" to posicao,
                        "forca" to forca,
                        "tecnica" to (forca + (-5..5).random()).coerceIn(1, 99),
                        "passe" to (forca + (-5..5).random()).coerceIn(1, 99),
                        "velocidade" to (forca + (-5..5).random()).coerceIn(1, 99),
                        "finalizacao" to when (posicao) {
                            "CENTROAVANTE", "SEGUNDA_ATACANTE" -> (forca + 5).coerceIn(1, 99)
                            "GOLEIRO", "ZAGUEIRO" -> (forca - 15).coerceIn(1, 99)
                            else -> forca
                        },
                        "defesa" to when (posicao) {
                            "GOLEIRO", "ZAGUEIRO", "LATERAL_DIREITO", "LATERAL_ESQUERDO" -> (forca + 5).coerceIn(1, 99)
                            "CENTROAVANTE" -> (forca - 15).coerceIn(1, 99)
                            else -> forca
                        },
                        "fisico" to (forca + (-5..5).random()).coerceIn(1, 99),
                        "salario" to salario,
                        "contrato" to (1..4).random(),
                        "valor_mercado" to valorMercado
                    ))
                }
            }
        }

        return "[${jogadores.joinToString(",") { m ->
            """{${m.entries.joinToString(",") { (k,v) ->
                if (v is String) "\"$k\":\"$v\"" else "\"$k\":$v"
            }}}"""
        }}]"
    }

    private fun nomeFicticio(id: Int, timeId: Int): String {
        val prenomes = listOf("Carlos","Lucas","Marcos","Rafael","Pedro","Thiago","Bruno","Felipe","Diego","Rodrigo","Anderson","Alan","Mateus","Vinicius","Gabriel","Gustavo","Eduardo","Leandro","Renato","Victor")
        val sobrenomes = listOf("Silva","Santos","Oliveira","Souza","Costa","Ferreira","Gomes","Lima","Rocha","Cardoso","Barbosa","Martins","Alves","Nascimento","Carvalho","Moreira","Nunes","Mendes","Teixeira","Araújo")
        return "${prenomes[id % prenomes.size]} ${sobrenomes[timeId % sobrenomes.size]}"
    }

    private fun IntRange.random() = (Math.random() * (last - first + 1) + first).toInt()
    private fun LongRange.random() = (Math.random() * (last - first + 1) + first).toLong()
}
