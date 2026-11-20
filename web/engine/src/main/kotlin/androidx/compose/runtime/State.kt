package androidx.compose.runtime

import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlin.reflect.KProperty

// Web stand-in for the five Compose runtime names a web-ready slice of :state may use (state/web-ready.txt,
// docs/design/umbau-phase-2.md §2.2: mutableStateOf, getValue, setValue, mutableStateListOf, mutableIntStateOf) – plain
// holders, no snapshots: the page reads a slice's state after each call and draws it itself, nothing observes it. The
// real runtime jar is not compiled for the web (not verified under TeaVM). getValue / setValue are inline, as Compose's
// are, so a delegated property needs no reflection. mutableStateListOf / toMutableStateList give the plain
// SnapshotStateList (snapshots/SnapshotStateList.kt) so a file that names that type (PlanDocument) compiles too.

interface State<T> { val value: T }

interface MutableState<T> : State<T> { override var value: T }

private class Holder<T>(override var value: T) : MutableState<T>

fun <T> mutableStateOf(value: T): MutableState<T> = Holder(value)

fun mutableIntStateOf(value: Int): MutableState<Int> = Holder(value)

fun <T> mutableStateListOf(vararg elements: T): SnapshotStateList<T> = SnapshotStateList<T>().also { it.addAll(elements) }

fun <T> Collection<T>.toMutableStateList(): SnapshotStateList<T> = SnapshotStateList<T>().also { it.addAll(this) }

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> State<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) { this.value = value }
