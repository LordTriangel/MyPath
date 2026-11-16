package com.onpath.app.suggest

/**
 * The kinds of way in a [WayNet]: one entry per distinct BRouter way description (a few thousand per network – many
 * links share one). Per kind the profile's cost factor in both directions (as the description is stored) and the few
 * way tags the tour suggestions use (docs/design/tour-suggestions.md §3 "facts"), as small codes: per key a short value
 * table (BRouter's lookups.dat has a fixed list of values per key, so the tables stay small), and the signed route
 * flags (`route_bicycle_ncn=yes` …) as one bit field.
 *
 * Pure Kotlin (shared with the web planner).
 */
class WayKinds {
    var size = 0; private set
    private var cfF = FloatArray(64)
    private var cfB = FloatArray(64)
    private var codes = ByteArray(64 * KEYS.size)
    private var routeBits = IntArray(64)
    private val values = Array(KEYS.size) { arrayListOf<String>("") }
    private val routeKeys = ArrayList<String>()

    /** The profile's cost factor of [kind]: as stored, or against the stored direction ([reverse]). ≥ 9999 = closed. */
    fun costFactor(kind: Int, reverse: Boolean): Float = if (reverse) cfB[kind] else cfF[kind]

    /** Value of tag [key] (index into [KEYS]) on [kind], null when the way does not have it. */
    fun tag(kind: Int, key: Int): String? = code(kind, key).let { if (it == 0 || it == OTHER) null else values[key][it] }

    /** The value code of tag [key] on [kind] (0 = no such tag, [OTHER] = a value beyond the table) – compare with [codeOf]. */
    fun code(kind: Int, key: Int): Int = codes[kind * KEYS.size + key].toInt() and 0xff

    /** The code of [value] for [key] in this network, −1 when no way here has it. */
    fun codeOf(key: Int, value: String): Int = values[key].indexOf(value).let { if (it <= 0) -1 else it }

    /** Signed route flags of [kind]: bit i = [routeKey] i is "yes". */
    fun routes(kind: Int): Int = routeBits[kind]

    /** The route key of bit [bit], e.g. "route_bicycle_ncn". */
    fun routeKey(bit: Int): String = routeKeys[bit]

    val routeKeyCount: Int get() = routeKeys.size

    /** Adds a kind from its cost factors and BRouter's tag text ("highway=track surface=gravel …"); → its index. */
    fun add(costFactor: Float, costFactorReverse: Float, tagText: String): Int {
        if (size == cfF.size) {
            val c = size * 2
            cfF = cfF.copyOf(c); cfB = cfB.copyOf(c); codes = codes.copyOf(c * KEYS.size); routeBits = routeBits.copyOf(c)
        }
        val k = size++
        cfF[k] = costFactor; cfB[k] = costFactorReverse
        var bits = 0
        for (kv in tagText.split(' ')) {
            val i = kv.indexOf('=')
            if (i <= 0 || i == kv.length - 1) continue
            val key = kv.substring(0, i)
            val value = kv.substring(i + 1)
            if (key.startsWith("route_") && value == "yes") {
                var b = routeKeys.indexOf(key)
                if (b < 0 && routeKeys.size < 32) { routeKeys.add(key); b = routeKeys.size - 1 }
                if (b >= 0) bits = bits or (1 shl b)
                continue
            }
            val ki = KEY_INDEX[key] ?: continue
            val table = values[ki]
            var code = table.indexOf(value)
            if (code < 0 && table.size < OTHER) { table.add(value); code = table.size - 1 }
            codes[k * KEYS.size + ki] = (if (code < 0) OTHER else code).toByte()
        }
        routeBits[k] = bits
        return k
    }

    fun bytes(): Long = cfF.size * 12L + codes.size + 64L * KEYS.size

    companion object {
        /** The way tags kept per kind (what facts.py reads, plus a few for the hints of later steps). */
        val KEYS = arrayOf(
            "highway", "surface", "tracktype", "sac_scale", "mtb:scale", "smoothness", "maxspeed", "railway", "bicycle",
            "foot", "route", "tunnel", "bridge", "access", "trail_visibility", "ford", "estimated_noise_class",
            "estimated_river_class", "estimated_traffic_class", "estimated_forest_class", "estimated_town_class",
        )
        /** Code of a value beyond the 254 a key can hold here ([tag] answers null; lookups.dat has far fewer per key). */
        const val OTHER = 255
        const val HIGHWAY = 0
        const val SURFACE = 1
        const val TRACKTYPE = 2
        const val SAC_SCALE = 3
        const val MTB_SCALE = 4
        const val SMOOTHNESS = 5
        const val MAXSPEED = 6
        const val RAILWAY = 7
        const val BICYCLE = 8
        const val FOOT = 9
        const val ROUTE = 10
        const val TUNNEL = 11
        const val BRIDGE = 12
        const val ACCESS = 13
        const val TRAIL_VISIBILITY = 14
        const val FORD = 15
        const val NOISE_CLASS = 16
        const val RIVER_CLASS = 17
        const val TRAFFIC_CLASS = 18
        const val FOREST_CLASS = 19
        const val TOWN_CLASS = 20
        private val KEY_INDEX: Map<String, Int> = KEYS.withIndex().associate { it.value to it.index }
    }
}
