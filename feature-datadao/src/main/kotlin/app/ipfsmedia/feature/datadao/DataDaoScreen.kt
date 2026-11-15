package app.ipfsmedia.feature.datadao

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AccessBadge
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.CardGrid
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.FactRow
import app.ipfsmedia.core.design.GeneratedCover
import app.ipfsmedia.core.design.HeroFact
import app.ipfsmedia.core.design.HeroHeader
import app.ipfsmedia.core.design.HeroSkeleton
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.InlineNotice
import app.ipfsmedia.core.design.LocalItemActions
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.MainActionButton
import app.ipfsmedia.core.design.PageTabs
import app.ipfsmedia.core.design.Panel
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.SplitChoice
import app.ipfsmedia.core.design.UploaderCard
import app.ipfsmedia.core.design.accessState
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.cardSkeletons
import app.ipfsmedia.core.design.edgeToEdge
import app.ipfsmedia.core.design.fullWidth
import app.ipfsmedia.core.design.heroArtSize
import app.ipfsmedia.core.design.mediaCards
import app.ipfsmedia.core.design.rememberCopyText
import app.ipfsmedia.core.library.AccessState
import app.ipfsmedia.core.library.DataDao
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.ItemKind
import app.ipfsmedia.core.library.LibraryItem
import app.ipfsmedia.core.library.asPayload
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.library.playEntries
import app.ipfsmedia.core.navigation.DataDaosRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.UploaderRoute
import app.ipfsmedia.core.navigation.mediaRoute
import app.ipfsmedia.feature.discussion.DiscussionViewModel
import app.ipfsmedia.feature.discussion.ThreadList
import haven.mobile.core.domain.TokenStandard

/**
 * One DataDAO: what it holds, who publishes in it, what its members are saying (only if anyone
 * is), and the rule that makes a wallet a member.
 *
 * The page never shows a price, a balance or a place to buy. It states the rule and whether
 * this wallet met it at the last refresh; that is all the app knows and all it needs.
 */
@Composable
fun DataDaoScreen(daoKey: String, initialTab: String?) {
    val library = LocalLibraryState.current
    val navigator = LocalNavigator.current
    val discussion: DiscussionViewModel = hiltViewModel()
    val discussionState by discussion.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val dao = library.dao(daoKey)
    var chosenTab by rememberSaveable(daoKey) { mutableStateOf(initialTab) }

    val uploads = remember(library, daoKey) { library.itemsOf(daoKey) }
    val albums = remember(uploads) { uploads.filter { it.kind == ItemKind.ALBUM } }
    val videos = remember(uploads) { uploads.filter { it.kind == ItemKind.VIDEO } }
    val uploaders = remember(uploads, library) {
        uploads.map { it.uploaderAddress }.distinct().mapNotNull(library::uploader)
    }
    val tabs = tabsFor(
        albumCount = albums.size,
        videoCount = videos.size,
        hasThreads = discussionState.hasThreads(daoKey),
        uploaderCount = uploaders.size,
    )
    // The chosen tab if it still exists (a refresh can empty one), else the first there is.
    val tab = DataDaoTab.of(chosenTab)?.takeIf { it in tabs } ?: tabs.first()
    val openItem: (LibraryItem) -> Unit = { navigator.open(mediaRoute(it.id, it.kind == ItemKind.VIDEO)) }

    CardGrid(state = gridState) {
        when {
            dao == null && !library.ready -> {
                edgeToEdge(key = "loading", contentType = "hero") { HeroSkeleton() }
                cardSkeletons()
            }

            dao == null -> fullWidth(key = "gone") {
                EmptyState(
                    icon = AppIcons.DataDao,
                    title = "This DataDAO has nothing live",
                    body = "Its uploads have expired or were removed, so there is nothing to show for it now.",
                    actionLabel = "Browse DataDAOs",
                    onAction = { navigator.open(DataDaosRoute) },
                )
            }

            else -> {
                edgeToEdge(key = "hero", contentType = "hero") { DaoHero(dao = dao, uploads = uploads) }
                fullWidth(key = "tabs", contentType = "tabs") {
                    PageTabs(
                        labels = tabs.map { it.label },
                        selectedIndex = tabs.indexOf(tab),
                        onSelect = { index -> chosenTab = tabs.getOrNull(index)?.name },
                    )
                }
                when (tab) {
                    DataDaoTab.MUSIC -> mediaCards(items = albums, onOpen = openItem)
                    DataDaoTab.VIDEO -> mediaCards(items = videos, onOpen = openItem)
                    DataDaoTab.DISCUSSION -> fullWidth(key = "discussion", contentType = "discussion") {
                        ThreadList(daoKey = daoKey, modifier = Modifier.widthIn(max = Sizes.readingWidth))
                    }
                    DataDaoTab.UPLOADERS -> items(items = uploaders, key = { "uploader-${it.address}" }, contentType = { "uploader" }) { uploader ->
                        UploaderCard(
                            uploader = uploader,
                            onOpen = { navigator.open(UploaderRoute(uploader.address)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    DataDaoTab.ABOUT -> fullWidth(key = "about", contentType = "about") { DaoAbout(dao = dao) }
                }
            }
        }
    }
}

@Composable
private fun DaoHero(dao: DataDao, uploads: List<LibraryItem>) {
    val actions = LocalItemActions.current
    // What "Play all" plays: everything in it this wallet can open, newest first.
    val playable = remember(uploads) { uploads.filter { it.access.canOpen } }
    val payload = remember(playable, dao.name) {
        DragPayload.Media(entries = playable.flatMap { it.playEntries() })
    }
    val refusal = when {
        uploads.isEmpty() -> "Nothing live right now"
        playable.isEmpty() -> "Members of ${dao.name} only"
        else -> null
    }
    HeroHeader(
        title = dao.name,
        seed = dao.key,
        overline = if (dao.isPublicGroup) "Open to everyone" else "DataDAO",
        subtitle = dao.about?.lineSequence()?.firstOrNull { it.isNotBlank() },
        art = {
            GeneratedCover(seed = dao.key, title = dao.name, modifier = Modifier.heroArtSize(), shape = Radius.lg)
        },
        facts = {
            AccessBadge(access = dao.accessState())
            if (dao.albumCount > 0) HeroFact(text = countOf(dao.albumCount, "album"), icon = AppIcons.Album)
            if (dao.videoCount > 0) HeroFact(text = countOf(dao.videoCount, "video"), icon = AppIcons.Video)
            if (dao.uploaderCount > 0) HeroFact(text = countOf(dao.uploaderCount, "uploader"), icon = AppIcons.Person)
        },
        actions = {
            MainActionButton(
                label = "Play all",
                icon = AppIcons.Play,
                onClick = { actions.playFrom(payload.entries, 0, dao.name) },
                refusal = refusal,
                choices = listOf(
                    SplitChoice("Play next", AppIcons.PlayNext, { actions.perform(Destination.PlayNext, payload) }, refusal),
                    SplitChoice("Add to queue", AppIcons.AddToQueue, { actions.perform(Destination.AddToQueue, payload) }, refusal),
                ),
            )
            if (!dao.isPublicGroup) {
                IconAction(
                    icon = if (dao.pinned) AppIcons.Pin else AppIcons.PinOutline,
                    label = if (dao.pinned) "Unpin from the sidebar" else "Pin to the sidebar",
                    onClick = { actions.perform(if (dao.pinned) Destination.Unpin else Destination.Pin, dao.asPayload()) },
                    // Light on the hero's dark ground, as the hero's own text is.
                    tint = LocalContentColor.current,
                )
            }
        },
    )
}

/** The membership rule in words, this wallet's standing, and the facts of the gate. */
@Composable
private fun DaoAbout(dao: DataDao) {
    val colors = appColors
    val copy = rememberCopyText()
    Column(
        modifier = Modifier
            .widthIn(max = Sizes.readingWidth)
            .padding(top = Space.x2),
        verticalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        Panel(title = if (dao.isPublicGroup) "Access" else "Membership") {
            Text(text = dao.ruleInWords, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                AccessBadge(access = dao.accessState())
                Text(
                    text = when (dao.accessState()) {
                        AccessState.OPEN -> "These uploads have no gate."
                        AccessState.MEMBER -> "This wallet met the rule at the last refresh."
                        AccessState.NOT_MEMBER -> "This wallet did not meet the rule at the last refresh, so its uploads are locked."
                        AccessState.UNKNOWN, AccessState.NOT_OPEN_YET ->
                            "The rule could not be checked at the last refresh. Refresh from Home to try again."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (!dao.isPublicGroup) {
            InlineNotice(
                text = "Access follows membership, but not to the second. What this device has already unlocked " +
                    "stays playable here until it leaves the cache, even if the wallet stops being a member.",
            )
        }
        val about = dao.about
        if (!about.isNullOrBlank()) {
            Panel(title = "About") {
                Text(text = about, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            }
        }
        if (!dao.isPublicGroup) {
            Panel(title = "Details") {
                FactRow(label = "Network", value = dao.chain?.label ?: "A network this app cannot check")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FactRow(
                        label = if (dao.standard == TokenStandard.ERC20) "Token contract" else "Collection contract",
                        value = dao.tokenAddress,
                        monospace = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(
                        icon = AppIcons.Copy,
                        label = "Copy the contract address",
                        onClick = { copy(dao.tokenAddress) },
                    )
                }
                FactRow(
                    label = "Kind",
                    value = when (dao.standard) {
                        TokenStandard.ERC20 -> "Token (ERC-20)"
                        TokenStandard.ERC721 -> "Collection (ERC-721)"
                        TokenStandard.ERC1155 -> "Collection (ERC-1155)"
                    },
                )
                FactRow(
                    label = "Holds",
                    value = listOf(
                        countOf(dao.albumCount, "album"),
                        countOf(dao.videoCount, "video"),
                        countOf(dao.uploaderCount, "uploader"),
                    ).joinToString(" · "),
                )
            }
        }
    }
}
