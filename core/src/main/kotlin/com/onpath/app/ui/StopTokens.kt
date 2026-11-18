package com.onpath.app.ui

import com.onpath.strings.*

/**
 * A plan point's numbered circle inside a text (owner 2026-11-03: "anstelle des Wortes Stopp diese Kreise mit der Zahl"):
 * "Über {3}" shows "Über ③" with the same sign as on the map – near-black stop, dark green start, dark red goal with a
 * flag. Only in buttons and the turn card (big enough to read); voice, notifications and small grey lines keep the word.
 * TalkBack reads "Stopp 3" / "Start" / "Ziel" in its place ([StopTokens.spoken]).
 */
object StopTokens {
    enum class Kind { STOP, START, GOAL }

    /** One circle in a text: [kind], its [number] (the map's number; the goal shows a flag). */
    data class Token(val kind: Kind, val number: Int)

    /** `{3}` = stop 3, `{s1}` = the start, `{g5}` = the destination (numbered 5). */
    private val TOKEN = Regex("""\{([sg]?)(\d+)\}""")

    fun stop(n: Int) = "{$n}"
    fun start(n: Int = 1) = "{s$n}"
    fun goal(n: Int) = "{g$n}"

    /** The text cut into plain parts and circles, in order (pure – unit-tested). */
    fun parts(text: String): List<Any> {
        val out = mutableListOf<Any>()
        var at = 0
        for (m in TOKEN.findAll(text)) {
            if (m.range.first > at) out += text.substring(at, m.range.first)
            val kind = when (m.groupValues[1]) { "s" -> Kind.START; "g" -> Kind.GOAL; else -> Kind.STOP }
            out += Token(kind, m.groupValues[2].toInt())
            at = m.range.last + 1
        }
        if (at < text.length) out += text.substring(at)
        return out
    }

    /** What TalkBack hears for a circle: "Stopp 3" / "Start" / "Ziel". */
    fun spoken(t: Token): String = when (t.kind) {
        Kind.STOP -> Txt.s(Res.string.nav_stop_n, t.number)
        Kind.START -> Txt.s(Res.string.nav_stop_start)
        Kind.GOAL -> Txt.s(Res.string.nav_card_goal)
    }

    /** The plain text with each circle spoken: "Über Stopp 3" – for semantics and logs. */
    fun plain(text: String): String = parts(text).joinToString("") { if (it is Token) spoken(it) else it as String }

    fun has(text: String): Boolean = TOKEN.containsMatchIn(text)
}
