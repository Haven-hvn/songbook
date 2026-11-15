package app.ipfsmedia.core.social

/** A comment placed in its thread: how deep it sits, and in what order it is shown. */
data class PlacedComment(
    val record: BoardRecord,
    /** 0 for a comment on the root; 1 for a reply to it; and so on, capped at [MAX_DEPTH]. */
    val depth: Int,
)

/** Replies deeper than this are shown at this depth, so a long exchange does not run off the side. */
const val MAX_DEPTH = 6

/**
 * Orders the comments under one root for display: each comment followed by its replies, oldest
 * first at every level (docs/PLAN.md §11). There is no vote ranking.
 *
 * Nesting follows `parent_ref`. A comment whose parent is missing — it expired first, since
 * comments live seven days, or it is under a different root — is shown at the top level rather
 * than dropped. A cycle (which a hostile writer could construct) cannot loop: each comment is
 * placed once.
 */
fun nestComments(posts: List<BoardRecord>): List<PlacedComment> {
    val byKey = posts.associateBy { it.key }
    val oldestFirst = compareBy<BoardRecord>({ it.createdAtBlock ?: Long.MAX_VALUE }, { it.key })
    val children = posts
        .filter { it.parentRef != null && it.parentRef != it.key && it.parentRef in byKey }
        .groupBy { it.parentRef!! }
        .mapValues { (_, replies) -> replies.sortedWith(oldestFirst) }
    val placed = LinkedHashSet<String>()
    val out = ArrayList<PlacedComment>(posts.size)

    fun place(record: BoardRecord, depth: Int) {
        if (!placed.add(record.key)) return
        out += PlacedComment(record, depth.coerceAtMost(MAX_DEPTH))
        children[record.key].orEmpty().forEach { place(it, depth + 1) }
    }

    posts.filter { it.parentRef == null || it.parentRef == it.key || it.parentRef !in byKey }
        .sortedWith(oldestFirst)
        .forEach { place(it, 0) }
    // Whatever is left sits in a reply cycle with no way in from the top; show it flat.
    posts.sortedWith(oldestFirst).forEach { place(it, 0) }
    return out
}
