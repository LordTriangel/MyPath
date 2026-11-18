package com.onpath.app.suggest

/**
 * Every number of the tour-suggestion method, read from ONE file: `tools/suggest-poc/settings.json` (each value is
 * explained there). The core gets the file's text ([parse]); it never has numbers of its own. The POC reads the same
 * file; when the Python method is deleted (end of stage 2) the file moves into the app's assets and the planner's
 * engine gets it the way it gets the BRouter profiles.
 *
 * A tiny JSON reader of its own (objects keep their order; numbers as Double) – no library, nothing TeaVM lacks.
 */
class SuggestSettings private constructor(val root: J) {
    operator fun get(key: String): J = root[key]

    /** A JSON value: object (LinkedHashMap), array (List), Double, String, Boolean or null. */
    class J(val v: Any?) {
        @Suppress("UNCHECKED_CAST")
        operator fun get(key: String): J = J((v as? Map<String, Any?>)?.get(key))
        @Suppress("UNCHECKED_CAST")
        operator fun get(i: Int): J = J((v as? List<Any?>)?.get(i))
        val d: Double get() = (v as? Double) ?: throw IllegalArgumentException("settings: not a number: $v")
        val i: Int get() = d.toInt()
        val s: String get() = v as? String ?: throw IllegalArgumentException("settings: not a text: $v")
        val isNull: Boolean get() = v == null
        val isList: Boolean get() = v is List<*>
        @Suppress("UNCHECKED_CAST")
        val list: List<J> get() = (v as? List<Any?>)?.map { J(it) } ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val keys: List<String> get() = (v as? Map<String, Any?>)?.keys?.toList() ?: emptyList()
        fun has(key: String): Boolean = (v as? Map<*, *>)?.containsKey(key) == true
        fun doubles(): DoubleArray = list.map { it.d }.toDoubleArray()
    }

    companion object {
        fun parse(text: String): SuggestSettings = SuggestSettings(J(Parser(text).value()))
    }

    private class Parser(val s: String) {
        var i = 0
        fun value(): Any? {
            ws()
            if (i >= s.length) throw IllegalArgumentException("settings: unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { word("true"); true }
                'f' -> { word("false"); false }
                'n' -> { word("null"); null }
                else -> if (c == '-' || c in '0'..'9') num() else throw IllegalArgumentException("settings: bad char '$c' at $i")
            }
        }
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun word(w: String) { if (!s.startsWith(w, i)) throw IllegalArgumentException("settings: expected $w at $i"); i += w.length }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws()
                val k = str()
                ws()
                if (s[i++] != ':') throw IllegalArgumentException("settings: ':' expected at $i")
                m[k] = value()
                ws()
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return m
                    else -> throw IllegalArgumentException("settings: ',' or '}' expected at $i")
                }
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l.add(value())
                ws()
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return l
                    else -> throw IllegalArgumentException("settings: ',' or ']' expected at $i")
                }
            }
        }
        fun str(): String {
            if (s[i] != '"') throw IllegalArgumentException("settings: text expected at $i")
            i++
            val b = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r'); 'b' -> b.append('\b')
                            'f' -> b.append('\u000c')
                            'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> b.append(e)
                        }
                    }
                    else -> b.append(c)
                }
            }
        }
        fun num(): Double {
            val st = i
            while (i < s.length && (s[i] in "+-.eE" || s[i] in '0'..'9')) i++
            return s.substring(st, i).toDouble()
        }
    }
}
