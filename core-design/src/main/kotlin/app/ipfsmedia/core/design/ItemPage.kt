package app.ipfsmedia.core.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.ipfsmedia.core.library.AccessState
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.ItemKind
import app.ipfsmedia.core.library.LibraryItem
import app.ipfsmedia.core.library.OfflineState
import app.ipfsmedia.core.library.asPayload
import app.ipfsmedia.core.library.formatAge
import app.ipfsmedia.core.library.formatLength
import app.ipfsmedia.core.library.formatRemainingLife
import app.ipfsmedia.core.library.lockedReason

/**
 * The actions on an album or video page, for a hero's action row: the one main action (Play,
 * with Play next, Add to queue and Download behind its arrow), favourite, and download or
 * remove the download.
 *
 * A locked item keeps its buttons and says why they are off, rather than hiding what it cannot
 * do (docs/PLAN.md §5).
 */
@Composable
fun RowScope.ItemPageActions(item: LibraryItem) {
    val actions = LocalItemActions.current
    val payload = remember(item) { item.asPayload() }
    val locked = item.lockedReason()
    val onDevice = item.offline == OfflineState.ON_DEVICE

    MainActionButton(
        label = "Play",
        icon = AppIcons.Play,
        onClick = { actions.perform(Destination.PlayNow, payload) },
        refusal = locked,
        choices = listOf(
            SplitChoice("Play next", AppIcons.PlayNext, { actions.perform(Destination.PlayNext, payload) }, locked),
            SplitChoice("Add to queue", AppIcons.AddToQueue, { actions.perform(Destination.AddToQueue, payload) }, locked),
            SplitChoice(
                label = "Download",
                icon = AppIcons.Download,
                onClick = { actions.perform(Destination.Download, payload) },
                refusal = locked ?: "Already on this device".takeIf { onDevice },
            ),
        ),
    )
    IconAction(
        icon = if (item.isFavourite) AppIcons.Favourite else AppIcons.FavouriteOutline,
        label = if (item.isFavourite) "Remove from favourites" else "Favourite",
        onClick = {
            actions.perform(if (item.isFavourite) Destination.Unfavourite else Destination.Favourite, payload)
        },
        tint = Color.White,
    )
    if (onDevice || item.offline == OfflineState.PARTIAL) {
        IconAction(
            icon = AppIcons.DownloadDone,
            label = "Remove the download",
            onClick = { actions.removeDownloads(listOf(item.id), item.title) },
            tint = Color.White,
        )
    } else {
        IconAction(
            icon = AppIcons.Download,
            label = "Download",
            onClick = { actions.perform(Destination.Download, payload) },
            enabled = locked == null,
            tint = Color.White,
        )
    }
}

/**
 * The facts on an album or video hero: its DataDAO, the one access state, whether it is on the
 * device, how long it is, when it was uploaded and how long its record has left.
 */
@Composable
fun ItemHeroFacts(item: LibraryItem, onOpenDao: (() -> Unit)? = null) {
    val now = remember(item) { System.currentTimeMillis() }
    val daoName = item.daoName
    if (daoName != null) DaoBadge(name = daoName, onClick = onOpenDao)
    AccessBadge(access = item.access)
    OfflineBadge(offline = item.offline)
    formatLength(item.durationMs)?.let { HeroFact(text = it) }
    formatAge(item.createdAtMillis, now)?.let { HeroFact(text = "Uploaded ${it.replaceFirstChar { first -> first.lowercase() }}") }
    // Arkiv records are leased. An upload says how long it has left before it leaves the index.
    formatRemainingLife(item.expiresAtMillis, now)?.let { HeroFact(text = it) }
}

/**
 * Why an item is locked, and where to read what would open it. Shown under the hero of a locked
 * item. States the membership rule's location, never a price or a place to buy.
 */
@Composable
fun LockedNotice(item: LibraryItem, onSeeRule: (() -> Unit)?, modifier: Modifier = Modifier) {
    val text = when (item.access) {
        AccessState.NOT_MEMBER ->
            "This ${item.kind.noun()} is for members of ${item.daoName ?: "its DataDAO"}. " +
                "This wallet is not a member, so it cannot be opened."
        AccessState.NOT_OPEN_YET ->
            "This ${item.kind.noun()} is not open yet. It opens for every member once its DataDAO reaches the target set for it."
        AccessState.UNKNOWN ->
            "Membership could not be checked just now (no connection, or the chain did not answer). " +
                "It will still play if it is already on this device."
        AccessState.OPEN, AccessState.MEMBER -> return
    }
    Row(modifier = modifier) {
        InlineNotice(
            text = text,
            icon = item.access.icon(),
            actionLabel = if (onSeeRule != null && item.access != AccessState.UNKNOWN) "See the rule" else null,
            onAction = onSeeRule,
        )
    }
}

/** "album" or "video", for sentences. */
fun ItemKind.noun(): String = if (this == ItemKind.VIDEO) "video" else "album"
