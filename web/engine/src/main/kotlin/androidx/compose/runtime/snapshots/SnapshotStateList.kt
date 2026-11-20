package androidx.compose.runtime.snapshots

// Web stand-in for Compose's SnapshotStateList (docs/design/web-ready-status.md §1: the one name between :state's
// ui/plan/PlanDocument.kt and the planner) – a plain list over an ArrayList, no snapshots, like the other holders in
// ../State.kt. As in Compose: equality is identity (two lists with the same points are two lists), toList() is a copy.

class SnapshotStateList<T> private constructor(private val items: ArrayList<T>) : MutableList<T> by items, RandomAccess {
    constructor() : this(ArrayList())

    /** A copy of what the list holds now (Compose: the current snapshot's list). */
    fun toList(): List<T> = ArrayList(items)

    override fun toString(): String = "SnapshotStateList(value=$items)"
}
