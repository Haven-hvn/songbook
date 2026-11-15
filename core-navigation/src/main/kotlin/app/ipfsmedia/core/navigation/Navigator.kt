package app.ipfsmedia.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * How a feature module contributes its screens: a block run inside the app's `entryProvider`.
 * Each module provides one into a Hilt set (the `modular/hilt` recipe), and `:app` only
 * assembles them — it never names a feature's screens.
 */
typealias EntryProviderInstaller = EntryProviderScope<NavKey>.() -> Unit

/**
 * What screens use to move around. Implemented by [NavigationState]; an interface so screens can
 * be previewed and tested with a recording fake.
 */
interface Navigator {
    /** The section whose history is showing. */
    val section: SectionRoute

    /** What the content area shows now. */
    val current: Route

    val canGoBack: Boolean
    val canGoForward: Boolean

    /**
     * Open [route]. A section route switches to that section. Anything else is pushed onto the
     * current section's history, unless [inHomeSection] is true and the route has a section of
     * its own (a DataDAO or a playlist opened from the sidebar), in which case that section is
     * selected first. Opening the route already on top does nothing.
     */
    fun open(route: Route, inHomeSection: Boolean = false)

    /** Step back within the section, or to Home from a section's first page. False at the very start. */
    fun back(): Boolean

    /** Undo the last [back]. A new [open] clears what could be gone forward to. */
    fun forward(): Boolean

    /** Return the current section to its first page (selecting the section again in the sidebar). */
    fun resetSection()
}

/** Provided by the app shell around the content area. */
val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided. Wrap the content in the app shell, or provide a fake in tests.")
}
