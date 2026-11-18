package com.onpath.app.update

/** Comparing app version names – pure; [AppUpdater.isNewer] delegates here (map cells need it too). */
object AppVersions {
    /** True if [candidate] ("0.4.1") is newer than [current] ("0.4.0"). Non-numeric parts count as 0. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.', '-').take(3)
            .map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }.let { it + List(3 - it.size) { 0 } }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until 3) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }
}
