package com.onpath.app.live

/** Byte writer of the live formats: varints, zigzag varints and tag-length fields (the sync formats' coding). */
internal class LiveOut {
    private var buf = ByteArray(128)
    var size = 0
        private set
    fun byte(v: Int) { if (size == buf.size) buf = buf.copyOf(buf.size * 2); buf[size++] = v.toByte() }
    fun bytes(b: ByteArray) = b.forEach { byte(it.toInt()) }
    fun varint(v: Long) { var x = v; while (x < 0 || x >= 0x80) { byte(((x and 0x7f) or 0x80).toInt()); x = x ushr 7 }; byte(x.toInt()) }
    fun zigzag(v: Long) = varint((v shl 1) xor (v shr 63))
    fun field(tag: Int, data: ByteArray) { varint(tag.toLong()); varint(data.size.toLong()); bytes(data) }
    /** A field holding one number (never negative). */
    fun number(tag: Int, v: Long) = field(tag, LiveOut().apply { varint(v.coerceAtLeast(0)) }.toArray())
    fun toArray() = buf.copyOf(size)
}

/** Byte reader of the live formats; running off the end throws IndexOutOfBoundsException, odd numbers IllegalArgumentException. */
internal class LiveIn(private val b: ByteArray) {
    private var pos = 0
    fun more() = pos < b.size
    fun left() = b.size - pos
    fun byte(): Int { if (pos >= b.size) throw IndexOutOfBoundsException(); return b[pos++].toInt() and 0xff }
    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val v = byte()
            result = result or ((v and 0x7f).toLong() shl shift)
            if (v and 0x80 == 0) return result
            shift += 7
            require(shift < 64)
        }
    }
    fun zigzag(): Long { val v = varint(); return (v ushr 1) xor -(v and 1) }
    fun take(n: Int): ByteArray { if (n < 0 || n > left()) throw IndexOutOfBoundsException(); return b.copyOfRange(pos, pos + n).also { pos += n } }
    /** The next field as (tag, bytes). */
    fun field(): Pair<Long, ByteArray> {
        val tag = varint()
        val len = varint()
        require(len in 0..left().toLong())
        return tag to take(len.toInt())
    }
}
