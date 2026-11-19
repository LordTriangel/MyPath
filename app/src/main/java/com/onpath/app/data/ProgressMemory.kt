package com.onpath.app.data

import android.content.Context
import com.onpath.app.debug.Problems
import com.onpath.app.domain.RouteShares
import com.onpath.app.domain.StepShares

/**
 * The wait window's learned shares of time (owner 2026-11-19: each step owns the share it really takes, measured on this
 * phone, learned from the last loads): the map load's steps ([StepShares]) and the route's check ([RouteShares]). Kept in
 * the app's preferences; robust first – a value that cannot be read or written gives the start values (reported), the
 * session goes on with what it learned in memory. Thread-safe.
 */
interface ProgressMemory {
    var shares: StepShares
    var routeCheck: Double
    /**
     * The round-trip search's three steps (read the paths · find loops · rate – ui/progress/SuggestWait) in the three
     * places of a [StepShares]; start [SUGGEST_DEFAULT]. Default accessors: a memory that keeps none uses the start values.
     */
    var suggestShares: StepShares
        get() = SUGGEST_DEFAULT
        set(@Suppress("UNUSED_PARAMETER") v) {}

    /** For tests and when nothing can be stored: in memory only. */
    class InMemory(override var shares: StepShares = StepShares.DEFAULT, override var routeCheck: Double = RouteShares.DEFAULT_CHECK,
                   override var suggestShares: StepShares = SUGGEST_DEFAULT) : ProgressMemory

    companion object {
        private const val FILE = "onpath_progress"
        private const val KEY_SHARES = "map_steps"
        private const val KEY_ROUTE = "route_check"
        private const val KEY_SUGGEST = "suggest_steps"

        /**
         * The round-trip search's start shares: read 65 · find 30 · rate 5 (measured: a 3 h bike wish network + facts
         * 4.5 s, place + loops 1.8 s, judging 0.1 s – docs/design/tour-suggestions.md).
         */
        val SUGGEST_DEFAULT = StepShares(0.65, 0.30, 0.05)

        @Volatile private var app: ProgressMemory? = null

        /** The app's one memory (the loading window, the sign beside and the notification read the same shares). */
        fun of(context: Context): ProgressMemory = app ?: synchronized(this) {
            app ?: Stored(context.applicationContext).also { app = it }
        }
    }

    /** In the preferences [FILE]; read once, then kept in memory too. */
    private class Stored(private val context: Context) : ProgressMemory {
        @Volatile private var s: StepShares? = null
        @Volatile private var r: Double? = null
        @Volatile private var g: StepShares? = null

        private fun prefs() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        override var shares: StepShares
            get() = s ?: (try { StepShares.decode(prefs().getString(KEY_SHARES, null)) } catch (e: Exception) {
                Problems.warn("progress", "Learned shares of the wait window not readable; the start values are used", e)
                StepShares.DEFAULT
            }).also { s = it }
            set(v) {
                s = v
                try { prefs().edit().putString(KEY_SHARES, v.encode()).apply() } catch (e: Exception) {
                    Problems.warn("progress", "Learned shares of the wait window not stored; kept for this session", e)
                }
            }

        override var routeCheck: Double
            get() = r ?: (try { RouteShares.decode(prefs().getString(KEY_ROUTE, null)) } catch (e: Exception) {
                Problems.warn("progress", "Learned route check share not readable; the start value is used", e)
                RouteShares.DEFAULT_CHECK
            }).also { r = it }
            set(v) {
                r = RouteShares.bounded(v)
                try { prefs().edit().putString(KEY_ROUTE, RouteShares.encode(v)).apply() } catch (e: Exception) {
                    Problems.warn("progress", "Learned route check share not stored; kept for this session", e)
                }
            }

        override var suggestShares: StepShares
            get() = g ?: (try { prefs().getString(KEY_SUGGEST, null)?.let(StepShares::decode) ?: SUGGEST_DEFAULT } catch (e: Exception) {
                Problems.warn("progress", "Learned shares of the round-trip search not readable; the start values are used", e)
                SUGGEST_DEFAULT
            }).also { g = it }
            set(v) {
                g = v
                try { prefs().edit().putString(KEY_SUGGEST, v.encode()).apply() } catch (e: Exception) {
                    Problems.warn("progress", "Learned shares of the round-trip search not stored; kept for this session", e)
                }
            }
    }
}
