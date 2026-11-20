package com.onpath.app.live

import com.onpath.app.debug.Problems
import com.onpath.app.sync.B64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * One shared tour's box over HTTPS (docs/design/live-tracking.md §6; the server is `web/host/src/live.js` on
 * plan.onpath.earth): `HttpURLConnection`, [CONNECT_MS] / [READ_MS], the write token as `Authorization: Bearer`.
 *
 * **Never throws**: no answer (no network, timeout, DNS, anything else) is `LiveReply(0)`, an answer is its status with
 * the server's `retry-after`. Blocking – only ever called on the live lane's own thread, never on the recording's.
 *
 * The bodies are written by hand (names are "h" / "r" / "t0000", blobs and hashes Base64url / hex – nothing to escape),
 * and no answer body is read beyond draining it: the uploader needs the status only.
 */
class HttpLiveBox(base: String, private val boxId: String, private val writeToken: String) : LiveBox {
    private val box = base.trimEnd('/') + "/live/v1/" + boxId

    /** The server hashes the bearer (write) token itself, so [writeHash] is not sent. */
    override fun claim(readHash: String, writeHash: String): LiveReply =
        call("POST", box, """{"read":"${safe(readHash)}"}""")

    override fun push(items: List<LivePush>, ended: Boolean): LiveReply = call("POST", "$box/push", buildString {
        append("{\"items\":[")
        items.forEachIndexed { i, it ->
            if (i > 0) append(',')
            append("{\"name\":\"").append(safe(it.name)).append("\",\"blob\":\"").append(B64.encode(it.blob)).append("\"}")
        }
        append(']')
        if (ended) append(",\"ended\":true")
        append('}')
    })

    override fun delete(): LiveReply = call("DELETE", box, null)

    private fun call(method: String, url: String, body: String?): LiveReply {
        var c: HttpURLConnection? = null
        return try {
            c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_MS; readTimeout = READ_MS
                useCaches = false
                setRequestProperty("Authorization", "Bearer $writeToken")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    val bytes = body.encodeToByteArray()
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setFixedLengthStreamingMode(bytes.size)
                    outputStream.use { it.write(bytes) }
                }
            }
            val status = c.responseCode
            // drain the (small) answer so the connection can be reused; its content is not needed
            (if (status in 200..299) c.inputStream else c.errorStream)?.use { s ->
                val buf = ByteArray(1024)
                var total = 0
                while (total < MAX_ANSWER) { val n = s.read(buf); if (n < 0) break; total += n }
            }
            LiveReply(status, retryAfter(c.getHeaderField("retry-after")))
        } catch (e: IOException) {
            // expected: no network, timeout, DNS – hours of it in the mountains; the uploader waits and tries again
            Problems.info("live", "Live sharing: server not reached ($method): ${e.javaClass.simpleName}")
            LiveReply(0)
        } catch (e: Exception) {
            Problems.warn("live", "Live sharing: request failed ($method)", e)
            LiveReply(0)
        } finally {
            try { c?.disconnect() } catch (e: Exception) { Problems.warn("live", "Live sharing: connection not closed", e) }
        }
    }

    /** Only the characters a name, a hash or a token can have; anything else would break the hand-written JSON. */
    private fun safe(s: String): String = s.filter { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' }

    companion object {
        const val CONNECT_MS = 10_000
        const val READ_MS = 15_000
        private const val MAX_ANSWER = 65_536

        /** The retry-after header in seconds; null = none / not a number. */
        fun retryAfter(header: String?): Int? = header?.trim()?.toLongOrNull()?.coerceIn(0, 86_400)?.toInt()
    }
}

/** Opens boxes at the server [base] (e.g. "https://plan.onpath.earth/"). */
class HttpLiveBoxes(private val base: String) : LiveBoxes {
    override fun open(boxId: String, writeToken: String): LiveBox = HttpLiveBox(base, boxId, writeToken)
}
