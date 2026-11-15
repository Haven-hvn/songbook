package app.ipfsmedia.feature.uploader

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AccentButton
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.CardGrid
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.HeroFact
import app.ipfsmedia.core.design.HeroHeader
import app.ipfsmedia.core.design.HeroSkeleton
import app.ipfsmedia.core.design.HexAvatar
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.LocalItemActions
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.LocalWidthClass
import app.ipfsmedia.core.design.MainActionButton
import app.ipfsmedia.core.design.SectionHeader
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.SplitChoice
import app.ipfsmedia.core.design.cardSkeletons
import app.ipfsmedia.core.design.edgeToEdge
import app.ipfsmedia.core.design.fullWidth
import app.ipfsmedia.core.design.mediaCards
import app.ipfsmedia.core.design.rememberCopyText
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.ItemKind
import app.ipfsmedia.core.library.LibraryItem
import app.ipfsmedia.core.library.PUBLIC_GROUP_KEY
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.library.playEntries
import app.ipfsmedia.core.library.shortAddress
import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.mediaRoute

/**
 * An uploader's profile: everything that address has live, grouped by the DataDAO it was
 * published in.
 *
 * "Uploader" is where Parachord has "artist": the address that published, with the name it
 * gives itself when it gives one. That name is self-asserted, so the address is always shown
 * beside it. Following is a local list; it is published nowhere and the uploader never learns
 * of it.
 */
@Composable
fun UploaderScreen(address: String) {
    val viewModel: UploaderViewModel = hiltViewModel()
    val library = LocalLibraryState.current
    val navigator = LocalNavigator.current
    val actions = LocalItemActions.current
    val follows by viewModel.follows.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val copy = rememberCopyText()

    val id = remember(address) { address.trim().lowercase() }
    val uploader = library.uploader(id)
    val follow = follows[id]
    val followed = uploader?.followed ?: (follow != null)
    val isSelf = uploader?.isSelf ?: (library.walletAddress == id)
    val name = uploader?.displayName ?: follow?.label?.takeIf { it.isNotBlank() } ?: shortAddress(id)
    val uploads = remember(library, id) { library.itemsBy(id) }
    // Grouped by DataDAO in the order each first appears, which is newest upload first.
    val groups = remember(uploads) { uploads.groupBy { it.daoKey ?: PUBLIC_GROUP_KEY }.toList() }
    val openItem: (LibraryItem) -> Unit = { navigator.open(mediaRoute(it.id, it.kind == ItemKind.VIDEO)) }

    // Opening the profile is what "seen" means for a followed uploader's new uploads.
    val latestBlock = uploader?.latestBlock
    LaunchedEffect(id, latestBlock, followed) {
        if (followed && latestBlock != null && (uploader?.unseenCount ?: 0) > 0) viewModel.markSeen(id, latestBlock)
    }

    val payload = remember(id, name, followed, isSelf) {
        DragPayload.Uploader(address = id, name = name, followed = followed, isSelf = isSelf)
    }

    CardGrid(state = gridState) {
        if (!library.ready) {
            edgeToEdge(key = "loading", contentType = "hero") { HeroSkeleton() }
            cardSkeletons()
            return@CardGrid
        }

        edgeToEdge(key = "hero", contentType = "hero") {
            val playable = remember(uploads) { uploads.filter { it.access.canOpen } }
            val all = remember(playable) { DragPayload.Media(entries = playable.flatMap { it.playEntries() }) }
            val refusal = when {
                uploads.isEmpty() -> "Nothing live right now"
                playable.isEmpty() -> "Everything here is for members of DataDAOs this wallet is not in"
                else -> null
            }
            HeroHeader(
                title = name,
                seed = id,
                overline = if (isSelf) "You" else "Uploader",
                subtitle = shortAddress(id),
                art = {
                    HexAvatar(
                        address = id,
                        name = name,
                        size = if (LocalWidthClass.current.isCompact) Sizes.heroArtCompact else Sizes.heroArt,
                    )
                },
                facts = {
                    HeroFact(text = countOf(uploads.size, "upload"))
                    if (groups.size > 1) HeroFact(text = countOf(groups.size, "DataDAO"), icon = AppIcons.DataDao)
                    if (followed) HeroFact(text = "Following", icon = AppIcons.Check)
                },
                actions = {
                    MainActionButton(
                        label = "Play all",
                        icon = AppIcons.Play,
                        onClick = { actions.playFrom(all.entries, 0, name) },
                        refusal = refusal,
                        choices = listOf(
                            SplitChoice("Play next", AppIcons.PlayNext, { actions.perform(Destination.PlayNext, all) }, refusal),
                            SplitChoice("Add to queue", AppIcons.AddToQueue, { actions.perform(Destination.AddToQueue, all) }, refusal),
                        ),
                    )
                    when {
                        // There is nobody to follow when the profile is your own.
                        isSelf -> Unit
                        followed -> IconAction(
                            icon = AppIcons.Unfollow,
                            label = "Unfollow",
                            onClick = { actions.perform(Destination.Unfollow, payload) },
                            tint = LocalContentColor.current,
                        )
                        else -> AccentButton(
                            text = "Follow",
                            icon = AppIcons.Follow,
                            onClick = { actions.perform(Destination.Follow, payload) },
                        )
                    }
                    IconAction(
                        icon = AppIcons.Copy,
                        label = "Copy the address",
                        onClick = { copy(id) },
                        tint = LocalContentColor.current,
                    )
                },
            )
        }

        if (uploads.isEmpty()) {
            fullWidth(key = "empty") {
                EmptyState(
                    icon = AppIcons.Person,
                    title = "Nothing live right now",
                    body = if (followed) {
                        "Everything this address published has expired or was removed. You still follow it, " +
                            "so new uploads will appear in your feed."
                    } else {
                        "Everything this address published has expired or was removed."
                    },
                )
            }
            return@CardGrid
        }

        groups.forEach { (daoKey, groupItems) ->
            val dao = library.dao(daoKey)
            fullWidth(key = "group-$daoKey", contentType = "title") {
                SectionHeader(
                    title = dao?.name ?: groupItems.first().daoName ?: "Public",
                    count = groupItems.size,
                    actionLabel = if (dao != null) "Open" else null,
                    onAction = { navigator.open(DataDaoRoute(daoKey)) },
                    modifier = Modifier.padding(top = Space.x4),
                )
            }
            mediaCards(items = groupItems, onOpen = openItem)
        }
    }
}
