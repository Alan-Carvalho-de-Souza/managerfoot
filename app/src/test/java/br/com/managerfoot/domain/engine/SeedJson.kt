package br.com.managerfoot.domain.engine

// ─────────────────────────────────────────────────────────────────────────────
// Leitor de JSON mínimo, compartilhado pelos testes que leem o seed.
//
// O SeedDataSource usa org.json, que no android.jar é um stub e lança
// "not mocked" num teste de unidade — e a implementação real não pode ser
// baixada neste ambiente. Como o teste precisa apenas de alguns campos de cada
// objeto do seed, um descida recursiva de ~60 linhas resolve sem dependência.
// ─────────────────────────────────────────────────────────────────────────────
internal typealias JsonObj = Map<String, Any?>

internal object MiniJson {

    fun objeto(texto: String): JsonObj {
        val leitor = Leitor(texto)
        val valor = leitor.valor()
        @Suppress("UNCHECKED_CAST")
        return valor as? JsonObj ?: error("JSON não começa com um objeto")
    }

    private class Leitor(val s: String) {
        var i = 0

        fun espacos() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun valor(): Any? {
            espacos()
            return when (s[i]) {
                '{' -> objeto()
                '[' -> lista()
                '"' -> texto()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> numero()
            }
        }

        fun objeto(): JsonObj {
            val mapa = LinkedHashMap<String, Any?>()
            i++                                     // '{'
            espacos()
            if (s[i] == '}') { i++; return mapa }
            while (true) {
                espacos()
                val chave = texto()
                espacos()
                i++                                 // ':'
                mapa[chave] = valor()
                espacos()
                i++                                 // ',' ou '}'
                if (s[i - 1] == '}') return mapa
            }
        }

        fun lista(): List<Any?> {
            val itens = mutableListOf<Any?>()
            i++                                     // '['
            espacos()
            if (s[i] == ']') { i++; return itens }
            while (true) {
                itens += valor()
                espacos()
                i++                                 // ',' ou ']'
                if (s[i - 1] == ']') return itens
            }
        }

        fun texto(): String {
            i++                                     // '"' de abertura
            val sb = StringBuilder()
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (val e = s[i]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            sb.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(e)         // " \ /
                    }
                } else {
                    sb.append(s[i])
                }
                i++
            }
            i++                                     // '"' de fechamento
            return sb.toString()
        }

        fun numero(): Number {
            val inicio = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val bruto = s.substring(inicio, i)
            return bruto.toLongOrNull() ?: bruto.toDouble()
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun JsonObj.lista(chave: String): List<JsonObj> =
    this[chave] as? List<JsonObj> ?: error("chave '$chave' ausente ou não é uma lista")

@Suppress("UNCHECKED_CAST")
internal fun JsonObj.listaOpcional(chave: String): List<JsonObj> =
    this[chave] as? List<JsonObj> ?: emptyList()

internal fun JsonObj.inteiro(chave: String): Int =
    (this[chave] as? Number)?.toInt() ?: error("chave '$chave' ausente ou não é número")

internal fun JsonObj.inteiro(chave: String, padrao: Int): Int =
    (this[chave] as? Number)?.toInt() ?: padrao

internal fun JsonObj.texto(chave: String): String =
    this[chave] as? String ?: error("chave '$chave' ausente ou não é texto")

internal fun JsonObj.texto(chave: String, padrao: String): String =
    this[chave] as? String ?: padrao
