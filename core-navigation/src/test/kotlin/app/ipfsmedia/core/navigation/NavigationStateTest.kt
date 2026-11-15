package app.ipfsmedia.core.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationStateTest {

    private fun state(): NavigationState = NavigationState(
        start = HomeRoute,
        selected = mutableStateOf<NavKey>(HomeRoute),
        stacks = SECTION_ROUTES.associateWith { NavBackStack<NavKey>(it) },
        forwards = SECTION_ROUTES.associateWith { NavBackStack<NavKey>() },
    )

    @Test
    fun `starts on home with nowhere to go`() {
        val nav = state()
        assertEquals(HomeRoute, nav.section)
        assertEquals(HomeRoute, nav.current)
        assertFalse(nav.canGoBack)
        assertFalse(nav.canGoForward)
        assertFalse(nav.back())
    }

    @Test
    fun `back then forward returns to the same page`() {
        val nav = state()
        nav.open(AlbumRoute("0xabc"))
        nav.open(UploaderRoute("0x1"))
        assertTrue(nav.back())
        assertEquals(AlbumRoute("0xabc"), nav.current)
        assertTrue(nav.canGoForward)
        assertTrue(nav.forward())
        assertEquals(UploaderRoute("0x1"), nav.current)
        assertFalse(nav.canGoForward)
    }

    @Test
    fun `opening something new clears forward`() {
        val nav = state()
        nav.open(AlbumRoute("a"))
        nav.back()
        nav.open(VideoRoute("v"))
        assertFalse(nav.canGoForward)
        assertEquals(VideoRoute("v"), nav.current)
    }

    @Test
    fun `each section keeps its own history`() {
        val nav = state()
        nav.open(AlbumRoute("a"))
        nav.open(CollectionRoute)
        assertEquals(CollectionRoute, nav.current)
        nav.open(VideoRoute("v"))
        nav.open(HomeRoute)
        assertEquals(AlbumRoute("a"), nav.current)
        nav.open(CollectionRoute)
        assertEquals(VideoRoute("v"), nav.current)
    }

    @Test
    fun `the first page of another section goes back to home`() {
        val nav = state()
        nav.open(SettingsRoute)
        assertTrue(nav.canGoBack)
        assertTrue(nav.back())
        assertEquals(HomeRoute, nav.section)
    }

    @Test
    fun `a datadao opened from the sidebar lands in the datadao section`() {
        val nav = state()
        nav.open(DataDaoRoute("BaseMainnet:0xabc"), inHomeSection = true)
        assertEquals(DataDaosRoute, nav.section)
        assertEquals(DataDaoRoute("BaseMainnet:0xabc"), nav.current)
    }

    @Test
    fun `a datadao opened from a page stays in that section`() {
        val nav = state()
        nav.open(DataDaoRoute("BaseMainnet:0xabc"))
        assertEquals(HomeRoute, nav.section)
    }

    @Test
    fun `opening the page already showing does not grow the history`() {
        val nav = state()
        nav.open(AlbumRoute("a"))
        nav.open(AlbumRoute("a"))
        assertTrue(nav.back())
        assertEquals(HomeRoute, nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun `reset returns a section to its first page`() {
        val nav = state()
        nav.open(AlbumRoute("a"))
        nav.open(AlbumRoute("b"))
        nav.resetSection()
        assertEquals(HomeRoute, nav.current)
        assertFalse(nav.canGoForward)
    }
}
