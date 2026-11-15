package app.ipfsmedia.player.actions

/**
 * Something that was done and can be taken back.
 *
 * @param label what the notice says: "Added to Night Drive".
 * @param undo puts things back as they were.
 * @param redo does it again after an undo; null when the action cannot be repeated exactly.
 */
class UndoEntry(
    val label: String,
    val undo: suspend () -> Unit,
    val redo: (suspend () -> Unit)? = null,
)

/**
 * What Ctrl+Z and Ctrl+Shift+Z walk (docs/PLAN.md §7: "every move can be undone").
 *
 * Two lists: what has been done, and what has been undone and can be done again. Doing
 * something new clears the second, as in any editor. Bounded, oldest dropped first; it lives
 * for the session and is cleared when the identity changes, because an undo must never write
 * into another identity's data.
 */
class UndoStack(private val limit: Int = DEFAULT_LIMIT) {
    private val done = ArrayDeque<UndoEntry>()
    private val undone = ArrayDeque<UndoEntry>()

    val canUndo: Boolean get() = done.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    /** The label of what Undo would take back, for the notice and the menu. */
    val nextUndoLabel: String? get() = done.lastOrNull()?.label

    fun push(entry: UndoEntry) {
        done.addLast(entry)
        while (done.size > limit) done.removeFirst()
        undone.clear()
    }

    /** Takes back the latest action. Returns its label, or null when there was nothing to undo. */
    suspend fun undo(): String? {
        val entry = done.removeLastOrNull() ?: return null
        entry.undo()
        if (entry.redo != null) undone.addLast(entry) else undone.clear()
        return entry.label
    }

    /** Repeats the latest undone action. Returns its label, or null when there was none. */
    suspend fun redo(): String? {
        val entry = undone.removeLastOrNull() ?: return null
        entry.redo?.invoke()
        done.addLast(entry)
        return entry.label
    }

    fun clear() {
        done.clear()
        undone.clear()
    }

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}

/**
 * Where a row lands when it is dropped "before row [insertBefore]" of the list it is already in.
 *
 * Dropping on a row says "put it before this one" (or after the last), counted in the list as
 * it is drawn, with the moved row still in it. Taking the row out first shifts everything after
 * it up by one, so a move downwards lands one index lower than the number it was dropped on.
 * Returns null when the row would end up where it already is.
 */
fun moveTarget(from: Int, insertBefore: Int, size: Int): Int? {
    if (from !in 0 until size) return null
    val bounded = insertBefore.coerceIn(0, size)
    val target = if (bounded > from) bounded - 1 else bounded
    return target.takeIf { it != from }
}
