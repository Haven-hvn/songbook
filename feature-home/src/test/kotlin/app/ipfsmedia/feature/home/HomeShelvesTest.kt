package app.ipfsmedia.feature.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class HomeShelvesTest {

    private val all = HomeShelf.entries.toSet()

    @Test
    fun `no saved order is the default order`() {
        assertEquals(HomeShelf.DEFAULT_ORDER, shelfOrderOf(null))
        assertEquals(HomeShelf.DEFAULT_ORDER, shelfOrderOf("  "))
    }

    @Test
    fun `a saved order round-trips`() {
        val order = listOf(HomeShelf.FOLLOWING, HomeShelf.CONTINUE, HomeShelf.DATADAOS, HomeShelf.RECENT, HomeShelf.ON_DEVICE)
        assertEquals(order, shelfOrderOf(encodeShelfOrder(order)))
    }

    @Test
    fun `unknown ids are dropped and missing shelves are appended`() {
        val order = shelfOrderOf("daos, charts ,recent,daos")
        assertEquals(listOf(HomeShelf.DATADAOS, HomeShelf.RECENT), order.take(2))
        assertEquals(all, order.toSet())
        assertEquals(HomeShelf.entries.size, order.size)
    }

    @Test
    fun `a shelf moves one place among the visible shelves`() {
        val order = HomeShelf.DEFAULT_ORDER
        val moved = shelfMoved(order, HomeShelf.RECENT, delta = -1, visible = all)
        assertEquals(listOf(HomeShelf.RECENT, HomeShelf.CONTINUE), moved.take(2))
    }

    @Test
    fun `moving steps over shelves that are not shown`() {
        // CONTINUE, RECENT, ON_DEVICE, DATADAOS, FOLLOWING with RECENT and ON_DEVICE empty.
        val visible = setOf(HomeShelf.CONTINUE, HomeShelf.DATADAOS, HomeShelf.FOLLOWING)
        val moved = shelfMoved(HomeShelf.DEFAULT_ORDER, HomeShelf.DATADAOS, delta = -1, visible = visible)
        assertEquals(HomeShelf.DATADAOS, moved.first())
        assertEquals(HomeShelf.CONTINUE, moved[1])
    }

    @Test
    fun `the first shelf cannot move up and the last cannot move down`() {
        val order = HomeShelf.DEFAULT_ORDER
        assertSame(order, shelfMoved(order, order.first(), delta = -1, visible = all))
        assertSame(order, shelfMoved(order, order.last(), delta = 1, visible = all))
    }
}
