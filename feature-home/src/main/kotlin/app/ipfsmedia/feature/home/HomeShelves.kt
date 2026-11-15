package app.ipfsmedia.feature.home

/**
 * The shelves above the feed. Their order is the user's: each has a grip, and the order is kept
 * per identity (docs/PLAN.md §7, "Also movable in place").
 *
 * A shelf with nothing in it is not drawn, but it keeps its place in the order.
 */
enum class HomeShelf(val id: String, val title: String) {
    CONTINUE("continue", "Pick up where you left off"),
    RECENT("recent", "Played recently"),
    ON_DEVICE("device", "On this device"),
    DATADAOS("daos", "Your DataDAOs"),
    FOLLOWING("following", "People you follow"),
    ;

    companion object {
        val DEFAULT_ORDER: List<HomeShelf> = entries.toList()
    }
}

/**
 * The saved order as shelves. Ids this version does not know are dropped, and shelves the saved
 * order does not mention (added in a later version) keep their default place after the ones it
 * does, so nothing can go missing from Home.
 */
fun shelfOrderOf(saved: String?): List<HomeShelf> {
    if (saved.isNullOrBlank()) return HomeShelf.DEFAULT_ORDER
    val known = saved.split(',')
        .mapNotNull { id -> HomeShelf.entries.firstOrNull { it.id == id.trim() } }
        .distinct()
    return known + HomeShelf.DEFAULT_ORDER.filter { it !in known }
}

fun encodeShelfOrder(order: List<HomeShelf>): String = order.joinToString(",") { it.id }

/**
 * [order] with [shelf] moved one place among the shelves that are [visible]. Hidden shelves are
 * stepped over: moving a shelf "up" puts it above the shelf the user sees above it, wherever the
 * empty ones happen to sit. Returns [order] unchanged when there is nowhere to go.
 */
fun shelfMoved(order: List<HomeShelf>, shelf: HomeShelf, delta: Int, visible: Set<HomeShelf>): List<HomeShelf> {
    val from = order.indexOf(shelf)
    if (from < 0 || delta == 0) return order
    val step = if (delta > 0) 1 else -1
    var to = from + step
    while (to in order.indices && order[to] !in visible) to += step
    if (to !in order.indices) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}
