package app.ipfsmedia.feature.datadao

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DataDaoTabTest {

    @Test
    fun `about is always there, and is all there is for an empty datadao`() {
        assertEquals(listOf(DataDaoTab.ABOUT), tabsFor(albumCount = 0, videoCount = 0, hasThreads = false, uploaderCount = 0))
    }

    @Test
    fun `a tab with nothing behind it is left out`() {
        val tabs = tabsFor(albumCount = 3, videoCount = 0, hasThreads = false, uploaderCount = 2)
        assertEquals(listOf(DataDaoTab.MUSIC, DataDaoTab.UPLOADERS, DataDaoTab.ABOUT), tabs)
    }

    @Test
    fun `discussion exists only when someone has started a thread`() {
        assertFalse(DataDaoTab.DISCUSSION in tabsFor(albumCount = 1, videoCount = 1, hasThreads = false, uploaderCount = 1))
        assertTrue(DataDaoTab.DISCUSSION in tabsFor(albumCount = 1, videoCount = 1, hasThreads = true, uploaderCount = 1))
    }

    @Test
    fun `tabs keep one order`() {
        assertEquals(
            DataDaoTab.entries.toList(),
            tabsFor(albumCount = 1, videoCount = 1, hasThreads = true, uploaderCount = 1),
        )
    }

    @Test
    fun `route tab names are the enum names other modules link with`() {
        // feature-discussion opens "DISCUSSION"; the album and video pages open "ABOUT".
        assertEquals(DataDaoTab.DISCUSSION, DataDaoTab.of("DISCUSSION"))
        assertEquals(DataDaoTab.ABOUT, DataDaoTab.of("ABOUT"))
        assertNull(DataDaoTab.of(null))
        assertNull(DataDaoTab.of("Charts"))
    }
}
