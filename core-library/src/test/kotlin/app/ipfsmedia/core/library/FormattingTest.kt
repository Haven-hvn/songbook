package app.ipfsmedia.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FormattingTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `clock positions`() {
        assertEquals("0:00", formatClock(0L))
        assertEquals("3:07", formatClock(187_000L))
        assertEquals("1:02:45", formatClock(hour + 2 * minute + 45_000))
        assertEquals("–:––", formatClock(null))
        assertEquals("–:––", formatClock(-1L))
    }

    @Test
    fun `lengths to read`() {
        assertNull(formatLength(null))
        assertNull(formatLength(0L))
        assertEquals("42 min", formatLength(42 * minute))
        assertEquals("1 h", formatLength(hour))
        assertEquals("1 h 5 min", formatLength(hour + 5 * minute))
    }

    @Test
    fun `sizes`() {
        assertEquals("Unknown size", formatBytes(null))
        assertEquals("512 B", formatBytes(512L))
        assertEquals("2 KB", formatBytes(2L * 1024))
        assertEquals("1.5 MB", formatBytes(1024L * 1024 * 3 / 2))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `ages`() {
        val now = 100 * day
        assertNull(formatAge(null, now))
        assertEquals("Just now", formatAge(now - 30_000, now))
        assertEquals("5 minutes ago", formatAge(now - 5 * minute, now))
        assertEquals("3 hours ago", formatAge(now - 3 * hour, now))
        assertEquals("Yesterday", formatAge(now - 30 * hour, now))
        assertEquals("3 days ago", formatAge(now - 3 * day, now))
        // A time slightly in the future (clock estimate) is not negative.
        assertEquals("Just now", formatAge(now + minute, now))
    }

    @Test
    fun `remaining life of a leased record`() {
        val now = 100 * day
        assertNull(formatRemainingLife(null, now))
        assertEquals("Expired", formatRemainingLife(now - 1, now))
        assertEquals("Expires in 5 hours", formatRemainingLife(now + 5 * hour, now))
        assertEquals("Expires in 6 days", formatRemainingLife(now + 6 * day, now))
    }

    @Test
    fun `counts`() {
        assertEquals("1 song", countOf(1, "song"))
        assertEquals("12 songs", countOf(12, "song"))
        assertEquals("2 entries", countOf(2, "entry", "entries"))
    }

    @Test
    fun `the block clock projects both ways from its anchor`() {
        val clock = BlockClock(headBlock = 1_000, observedAtMillis = 5_000_000)
        assertEquals(5_000_000L, clock.millisAt(1_000))
        assertEquals(5_000_000L - 2_000L * 10, clock.millisAt(990))
        assertEquals(5_000_000L + 2_000L * 10, clock.millisAt(1_010))
        assertNull(BlockClock.of(null, "5"))
        assertNull(BlockClock.of("x", "5"))
        assertEquals(clock, BlockClock.of("1000", "5000000"))
    }
}
