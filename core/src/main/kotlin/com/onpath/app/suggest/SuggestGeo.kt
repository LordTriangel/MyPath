package com.onpath.app.suggest

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Small helpers of the tour-suggestion core that must compute exactly as the POC (`net.py`, Python's own number
 * rules), so the port gives the same numbers: flat-earth metres, bearing, the eight directions in words, Python's
 * `round(x, n)` (half to even on the exact value), `%` on floats, `str(float)`.
 */
object SuggestGeo {
    val DIRS = arrayOf("Norden", "Nordosten", "Osten", "Südosten", "Süden", "Südwesten", "Westen", "Nordwesten")

    /** net.py `dist`: metres between two lon / lat points (flat earth, fine for a few kilometres). */
    fun dist(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
        val kx = cos(Math.toRadians((lat1 + lat2) / 2.0)) * 111320.0
        return hypot((lon1 - lon2) * kx, (lat1 - lat2) * 111320.0)
    }

    /** net.py `bearing`: degrees 0..360 from a to b, north = 0. */
    fun bearing(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
        val kx = cos(Math.toRadians((lat1 + lat2) / 2.0))
        return pyMod(Math.toDegrees(atan2((lon2 - lon1) * kx, lat2 - lat1)), 360.0)
    }

    /** net.py `direction`: "Norden" … "Nordwesten". */
    fun direction(lon1: Double, lat1: Double, lon2: Double, lat2: Double): String =
        DIRS[pyMod(Math.floor((bearing(lon1, lat1, lon2, lat2) + 22.5) / 45.0), 8.0).toInt()]

    /** Python's float `%`: the sign of the divisor. */
    fun pyMod(x: Double, m: Double): Double {
        var r = x % m
        if (r != 0.0 && (r < 0) != (m < 0)) r += m
        return r
    }

    /** Python's `round(x, digits)` for a float: the exact value rounded half to even. */
    fun pyRound(x: Double, digits: Int): Double =
        if (x.isNaN() || x.isInfinite()) x else BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN).toDouble()

    /** Python's `round(x)` → int (half to even). */
    fun pyRoundInt(x: Double): Long = BigDecimal(x).setScale(0, RoundingMode.HALF_EVEN).toLong()

    /** Python's `str(float)` for the plain numbers of the cards ("1.2", "3.0"). */
    fun pyStr(x: Double): String {
        if (x == Math.floor(x) && Math.abs(x) < 1e15) return x.toLong().toString() + ".0"
        return x.toString()
    }

    /** "1,2" – a number as the cards write it (German decimal comma). */
    fun comma(x: Double): String = pyStr(x).replace('.', ',')
}
