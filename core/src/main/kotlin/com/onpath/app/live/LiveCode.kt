package com.onpath.app.live

import com.onpath.app.sync.B64
import com.onpath.app.sync.Hkdf

/**
 * The live link's code (docs/design/live-tracking.md §6): the sharer's phone makes one random secret per shared tour and
 * sends it inside a link by messenger; the secret sits after "#" and never reaches a server. Whoever has the link can
 * **read** the tour ([LiveKeys]) – nothing in it allows writing (the write token is made apart on the phone).
 *
 * Link: `https://plan.onpath.earth/live/#t=<code>`. Format version 1 (bytes, then Base64url without padding, 44
 * characters): version (1), secret (32 bytes). A newer version may append fields; version-1 readers ignore them.
 *
 * Reading never throws: anything else is null.
 */
object LiveCode {
    const val VERSION = 1
    const val SECRET_BYTES = 32
    /** The viewer page (its path is not the planner's "/", so the app's App Link filter leaves it to the browser). */
    const val PAGE = "https://plan.onpath.earth/live/"
    private val MARKER = Regex("""(?:^|[#?&])t=([A-Za-z0-9_-]+)""")

    /** The code for [secret] (32 random bytes from the platform's secure random). */
    fun encode(secret: ByteArray): String {
        require(secret.size == SECRET_BYTES) { "live secret must be $SECRET_BYTES bytes" }
        return B64.encode(byteArrayOf(VERSION.toByte()) + secret)
    }

    /** The link to send, at the viewer page [base]. */
    fun link(secret: ByteArray, base: String = PAGE): String = base.substringBefore('#') + "#t=" + encode(secret)

    /** The secret in [text] – a whole link (`…#t=<code>`), `t=<code>` or the bare code – or null. */
    fun parse(text: String): ByteArray? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 4000) return null
        val code = MARKER.find(t)?.groupValues?.get(1) ?: t.takeIf { it.all(B64::isCodeChar) } ?: return null
        val b = B64.decode(code) ?: return null
        if (b.size < 1 + SECRET_BYTES || b[0].toInt() != VERSION) return null
        return b.copyOfRange(1, 1 + SECRET_BYTES)
    }
}

/**
 * What the phone and the viewer page derive from the link's secret (HKDF-SHA256, salt "OnPath live v1"):
 * - [boxId]: the box's name on the server – the only name it knows the tour by (16 bytes, 22 characters).
 * - [readToken]: proves the right to **read** that box; the server keeps only its SHA-256 (32 bytes, 43 characters).
 * - content key: seals every item (AES-256-GCM); never leaves the two devices.
 *
 * There is no write token here on purpose: it is random, stays on the phone, and cannot be derived from the link.
 */
class LiveKeys(secret: ByteArray) {
    init { require(secret.size == LiveCode.SECRET_BYTES) }

    internal val boxBytes = derive(secret, "box id", 16)
    val boxId: String = B64.encode(boxBytes)
    val readToken: String = B64.encode(derive(secret, "read token", 32))
    internal val content: ByteArray = derive(secret, "content key", 32)

    private fun derive(secret: ByteArray, label: String, n: Int) =
        Hkdf.derive(secret, SALT, label.encodeToByteArray(), n)

    companion object {
        private val SALT = "OnPath live v1".encodeToByteArray()

        /** The keys for a live link or code, or null when [text] holds none. */
        fun of(text: String): LiveKeys? = LiveCode.parse(text)?.let(::LiveKeys)
    }
}
