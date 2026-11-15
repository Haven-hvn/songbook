package app.ipfsmedia.core.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Card-to-hero transitions (docs/PLAN.md §7, "Live interface"): the picture on a card travels to
 * the hero of the page it opens, and back again when the page is left.
 *
 * Two pictures share bounds when they are tagged with the same record. The same upload can be on
 * screen several times at once (in the feed and on a shelf under it), and two pictures with one
 * tag in one page would both be drawn where the transition says the tag is. So a card is only
 * tagged while it is [source]: the card the last page was opened from. Every other card is an
 * ordinary picture.
 */
@Stable
class ArtTransitions(internal val scope: SharedTransitionScope) {
    /** `surface|recordKey` of the card that was opened last. */
    internal var source: String? by mutableStateOf(null)

    /** Call when a card is opened, before navigating. */
    fun openedFrom(surface: String, recordKey: String) {
        source = tag(surface, recordKey)
    }

    internal fun isSource(surface: String, recordKey: String): Boolean = source == tag(surface, recordKey)

    private fun tag(surface: String, recordKey: String): String = "$surface|$recordKey"
}

/** Provided by the shell around the content area. Null elsewhere: pictures are then just pictures. */
val LocalArtTransitions = staticCompositionLocalOf<ArtTransitions?> { null }

/** The page being shown, as the transition sees it. Provided per page by the shell. */
val LocalPageTransitionScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Names the part of a page a card is in, when the same upload can appear in more than one part.
 * A shelf sets its own; a page with one grid can leave it alone.
 */
val LocalArtSurface = compositionLocalOf { "" }

/** For the picture on a card: joins the transition only while this card is the one that was opened. */
@Composable
fun Modifier.cardArt(recordKey: String): Modifier {
    val transitions = LocalArtTransitions.current ?: return this
    val page = LocalPageTransitionScope.current ?: return this
    if (!transitions.isSource(LocalArtSurface.current, recordKey)) return this
    return with(transitions.scope) {
        this@cardArt.sharedElement(rememberSharedContentState(key = artKey(recordKey)), page)
    }
}

/** For the picture on a hero: where the opened card's picture lands. */
@Composable
fun Modifier.heroArt(recordKey: String): Modifier {
    val transitions = LocalArtTransitions.current ?: return this
    val page = LocalPageTransitionScope.current ?: return this
    return with(transitions.scope) {
        this@heroArt.sharedElement(rememberSharedContentState(key = artKey(recordKey)), page)
    }
}

private fun artKey(recordKey: String): String = "art:$recordKey"
