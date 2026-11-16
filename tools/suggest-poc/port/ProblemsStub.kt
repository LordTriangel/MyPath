package com.onpath.app.debug

/** Plain-JVM stand-in for the app's Problems log (the real one needs Android): prints to stderr. */
object Problems {
    fun warn(area: String, message: String, t: Throwable? = null) =
        System.err.println("[Problems WARN] $area: $message" + (t?.let { " ($it)" } ?: ""))

    fun error(area: String, message: String, t: Throwable? = null) =
        System.err.println("[Problems ERROR] $area: $message" + (t?.let { " ($it)" } ?: ""))
}
