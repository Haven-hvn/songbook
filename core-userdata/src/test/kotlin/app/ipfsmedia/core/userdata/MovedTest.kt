package app.ipfsmedia.core.userdata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MovedTest {
    private val rows = listOf("a", "b", "c", "d")

    @Test
    fun `moves a row down`() {
        assertEquals(listOf("b", "c", "a", "d"), moved(rows, from = 0, to = 2))
    }

    @Test
    fun `moves a row up`() {
        assertEquals(listOf("d", "a", "b", "c"), moved(rows, from = 3, to = 0))
    }

    @Test
    fun `moving a row onto itself changes nothing`() {
        assertNull(moved(rows, from = 1, to = 1))
    }

    @Test
    fun `out of range indices change nothing`() {
        assertNull(moved(rows, from = -1, to = 2))
        assertNull(moved(rows, from = 0, to = 4))
        assertNull(moved(emptyList<String>(), from = 0, to = 0))
    }
}
