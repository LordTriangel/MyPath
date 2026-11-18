package com.onpath.app.recording

/** GPS silent this long → an accurate network fix may stand in (indoors). */
const val GPS_STALE_MS = 30_000L
/** Network fixes worse than this are never shown once GPS was seen (cell towers: km off). */
const val NET_MAX_ACCURACY_M = 150f

/** A GPS fix worse than this is weak (station roof, indoors at a window): a clearly better Wi-Fi fix may win. */
const val GPS_WEAK_M = 30f
/** A network fix counts as current this long (network updates come every 5 s). */
const val NET_FRESH_MS = 12_000L

/**
 * Whether a network fix is shown: before the first GPS fix; when GPS is stale; or (owner, e.g. under a station roof)
 * when the last GPS fix is weak and this one is at least twice as accurate – in the last two cases only when it is
 * Wi-Fi accurate. Pure.
 */
fun useNetworkFix(lastGpsElapsed: Long, nowElapsed: Long, hasAccuracy: Boolean, accuracyM: Float,
                  lastGpsAccuracyM: Float = 0f): Boolean =
    lastGpsElapsed == 0L || (hasAccuracy && accuracyM <= NET_MAX_ACCURACY_M &&
        (nowElapsed - lastGpsElapsed > GPS_STALE_MS || (lastGpsAccuracyM > GPS_WEAK_M && accuracyM * 2 <= lastGpsAccuracyM)))

/**
 * Whether a GPS fix is shown: always, unless it is weak and a current network fix is at least twice as accurate
 * (otherwise the dot would jump between the two every second). [gpsAccuracyM] null = unknown → shown. Pure.
 */
fun showGpsFix(gpsAccuracyM: Float?, lastNetElapsed: Long, lastNetAccuracyM: Float, nowElapsed: Long): Boolean =
    gpsAccuracyM == null || gpsAccuracyM <= GPS_WEAK_M || lastNetElapsed == 0L ||
        nowElapsed - lastNetElapsed > NET_FRESH_MS || lastNetAccuracyM * 2 > gpsAccuracyM
