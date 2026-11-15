package app.ipfsmedia.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer

/**
 * The app's navigation history: one stack per sidebar section, plus what can be gone forward to.
 *
 * This follows the `multiplestacks` recipe in `nav3-recipes` — a back stack per top-level route,
 * each decorated separately so a section's screens keep their state while another section is
 * showing — and adds the one thing Parachord's sidebar has that the recipe does not: a forward
 * button. Forward is a second list beside each section's history, filled by [back] and cleared
 * by the next [open].
 *
 * Everything here is saved state. Resizing a desktop window recreates the activity, and the
 * history, the selected section and the forward list all come back.
 */
class NavigationState internal constructor(
    private val start: SectionRoute,
    selected: MutableState<NavKey>,
    private val stacks: Map<SectionRoute, NavBackStack<NavKey>>,
    private val forwards: Map<SectionRoute, NavBackStack<NavKey>>,
) : Navigator {

    private var selectedKey: NavKey by selected

    override val section: SectionRoute
        get() = (selectedKey as? SectionRoute)?.takeIf { it in stacks } ?: start

    override val current: Route
        get() = stackOf(section).lastOrNull() as? Route ?: section

    override val canGoBack: Boolean
        get() = stackOf(section).size > 1 || section != start

    override val canGoForward: Boolean
        get() = forwardOf(section).isNotEmpty()

    override fun open(route: Route, inHomeSection: Boolean) {
        if (route is SectionRoute) {
            if (route in stacks) selectedKey = route
            return
        }
        val home = if (inHomeSection) route.homeSection() else null
        if (home != null && home in stacks) selectedKey = home
        val stack = stackOf(section)
        if (stack.lastOrNull() == route) return
        stack.add(route)
        forwardOf(section).clear()
    }

    override fun back(): Boolean {
        val stack = stackOf(section)
        if (stack.size > 1) {
            forwardOf(section).add(stack.removeAt(stack.lastIndex))
            return true
        }
        if (section != start) {
            // "Exit through home": the first page of any other section goes back to the start.
            selectedKey = start
            return true
        }
        return false
    }

    override fun forward(): Boolean {
        val forward = forwardOf(section)
        if (forward.isEmpty()) return false
        stackOf(section).add(forward.removeAt(forward.lastIndex))
        return true
    }

    override fun resetSection() {
        val stack = stackOf(section)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        forwardOf(section).clear()
    }

    private fun stackOf(section: SectionRoute): NavBackStack<NavKey> = stacks.getValue(section)

    private fun forwardOf(section: SectionRoute): NavBackStack<NavKey> = forwards.getValue(section)

    /**
     * The entries `NavDisplay` should show: the start section's history, then the selected
     * section's when it is a different one. Each stack gets its own saveable-state and view-model
     * decorators, so a section that is not showing keeps its screens' state and view models.
     */
    @Composable
    fun decoratedEntries(
        /** Decorators the shell adds around every page (it provides the page's transition scope). */
        pageDecorators: List<NavEntryDecorator<NavKey>> = emptyList(),
        entryProvider: (NavKey) -> NavEntry<NavKey>,
    ): List<NavEntry<NavKey>> {
        val decorated = stacks.mapValues { (_, stack) ->
            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            ) + pageDecorators
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = decorators,
                entryProvider = entryProvider,
            )
        }
        val inUse = if (section == start) listOf(start) else listOf(start, section)
        return inUse.flatMap { decorated[it].orEmpty() }
    }
}

/**
 * Creates the navigation state and keeps it across recreation and process death.
 *
 * [sections] must contain [start]. The set must not change between compositions: one saved
 * stack is remembered per section, in order.
 */
@Composable
fun rememberNavigationState(
    start: SectionRoute = HomeRoute,
    sections: List<SectionRoute> = SECTION_ROUTES,
): NavigationState {
    val selected: MutableState<NavKey> =
        rememberSerializable(stateSerializer = NavKeySerializer<NavKey>()) {
            mutableStateOf<NavKey>(start)
        }
    val stacks = sections.associateWith { section -> rememberNavBackStack(section) }
    val forwards = sections.associateWith { rememberNavBackStack() }
    return remember(start, sections) {
        NavigationState(start = start, selected = selected, stacks = stacks, forwards = forwards)
    }
}
