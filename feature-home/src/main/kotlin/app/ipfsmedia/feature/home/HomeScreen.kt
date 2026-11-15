package app.ipfsmedia.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.BusyIndicator
import app.ipfsmedia.core.design.CardGrid
import app.ipfsmedia.core.design.CardShelf
import app.ipfsmedia.core.design.Cursor
import app.ipfsmedia.core.design.DaoCard
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.EntityShelf
import app.ipfsmedia.core.design.FilterChips
import app.ipfsmedia.core.design.Gestures
import app.ipfsmedia.core.design.GroupLabel
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.InlineNotice
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.PageHeader
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.RefreshBox
import app.ipfsmedia.core.design.SectionHeader
import app.ipfsmedia.core.design.SegmentedSwitch
import app.ipfsmedia.core.design.ShelfSkeleton
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.SkeletonLine
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.UploaderCard
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.cardSkeletons
import app.ipfsmedia.core.design.cursor
import app.ipfsmedia.core.design.edgeToEdge
import app.ipfsmedia.core.design.fullWidth
import app.ipfsmedia.core.design.mediaCards
import app.ipfsmedia.core.design.toChord
import app.ipfsmedia.core.library.FeedEntry
import app.ipfsmedia.core.library.FeedKind
import app.ipfsmedia.core.library.FeedReason
import app.ipfsmedia.core.library.ItemKind
import app.ipfsmedia.core.library.LibraryItem
import app.ipfsmedia.core.library.OfflineState
import app.ipfsmedia.core.library.ShortcutAction
import app.ipfsmedia.core.library.ShortcutScope
import app.ipfsmedia.core.library.buildFeed
import app.ipfsmedia.core.library.formatAge
import app.ipfsmedia.core.library.formatClock
import app.ipfsmedia.core.library.groupByDay
import app.ipfsmedia.core.library.newestBlock
import app.ipfsmedia.core.library.shortcutFor
import app.ipfsmedia.core.navigation.CollectionRoute
import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.DataDaosRoute
import app.ipfsmedia.core.navigation.HistoryRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.UploaderRoute
import app.ipfsmedia.core.navigation.mediaRoute
import app.ipfsmedia.core.social.RefreshState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

/**
 * Home: shelves of the user's own things, then the upload feed.
 *
 * The feed is the newest uploads across the DataDAOs the wallet is a member of and from the
 * uploaders it follows, grouped by day (docs/PLAN.md §11). It is a feed of *uploads*: nothing
 * about what anyone played is in it. It changes only when a refresh brings something, and a
 * refresh happens when the app opens and when the user asks: pull down, the Refresh button, or
 * Ctrl+R. It never polls.
 *
 * The shelves above it are local: where a long item was left, what was played lately, what is on
 * the device, the wallet's DataDAOs, the people it follows. Each has a grip, and their order is
 * the user's.
 */
@Composable
fun HomeScreen() {
    val viewModel: HomeViewModel = hiltViewModel()
    val library = LocalLibraryState.current
    val navigator = LocalNavigator.current
    val refreshState by viewModel.refreshState.collectAsStateWithLifecycle()
    val seen by viewModel.seenBlock.collectAsStateWithLifecycle()
    val order by viewModel.shelfOrder.collectAsStateWithLifecycle()
    val resume by viewModel.resume.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    var daoFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val now = remember(library, refreshState) { System.currentTimeMillis() }

    // The feed, unfiltered, with "new" decided against what had been shown before.
    val all = remember(library, seen) { buildFeed(library, seenBlock = seen ?: 0L) }
    val filterDaos = remember(all, library) {
        val keys = all.mapNotNull { it.item.daoKey }.toSet()
        library.daos.filter { it.key in keys }
    }
    // A DataDAO that has left the feed cannot stay selected as its filter.
    val activeDao = daoFilter?.takeIf { key -> filterDaos.any { it.key == key } }
    val kind = FeedKind.entries[kindIndex.coerceIn(0, FeedKind.entries.lastIndex)]
    val feed = remember(all, kind, activeDao) {
        all.filter { entry ->
            val kindMatches = when (kind) {
                FeedKind.ALL -> true
                FeedKind.MUSIC -> entry.item.kind == ItemKind.ALBUM
                FeedKind.VIDEO -> entry.item.kind == ItemKind.VIDEO
            }
            kindMatches && (activeDao == null || entry.item.daoKey == activeDao)
        }
    }
    val days = remember(feed, now) { groupByDay(feed, now) }
    val newKeys = remember(feed) { feed.filter { it.isNew }.map { it.item.id }.toSet() }
    val reasons = remember(feed) { feed.associate { it.item.id to it.reason } }

    val newest = remember(all) { newestBlock(all) }
    LaunchedEffect(newest, seen) {
        // Only once "seen" is known for this identity; then what is on screen has been shown.
        if (seen != null && newest > 0L) viewModel.feedShown(newest)
    }

    // The shelves' contents. All local; none of this asks the index anything.
    val continueItems = remember(resume, library) {
        resume.mapNotNull { left ->
            library.item(left.recordKey)?.takeIf { !it.access.isLocked }?.let { it to left.positionMs }
        }.distinctBy { it.first.id }.take(SHELF_LIMIT)
    }
    val recentItems = remember(history, library) {
        history.asSequence().map { it.entry.recordKey }.distinct().mapNotNull(library::item).take(SHELF_LIMIT).toList()
    }
    val deviceItems = remember(library) {
        library.items.filter { it.offline == OfflineState.ON_DEVICE }.take(SHELF_LIMIT)
    }
    val yourDaos = remember(library) { library.yourDaos }
    val following = remember(library) {
        library.uploaders.filter { it.followed }.sortedByDescending { it.latestBlock ?: 0L }
    }
    val visible = remember(continueItems, recentItems, deviceItems, yourDaos, following) {
        buildSet {
            if (continueItems.isNotEmpty()) add(HomeShelf.CONTINUE)
            if (recentItems.isNotEmpty()) add(HomeShelf.RECENT)
            if (deviceItems.isNotEmpty()) add(HomeShelf.ON_DEVICE)
            if (yourDaos.isNotEmpty()) add(HomeShelf.DATADAOS)
            if (following.isNotEmpty()) add(HomeShelf.FOLLOWING)
        }
    }
    val openItem: (LibraryItem) -> Unit = { navigator.open(mediaRoute(it.id, it.kind == ItemKind.VIDEO)) }

    RefreshBox(isRefreshing = refreshState.running, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        CardGrid(state = gridState) {
            fullWidth(key = "header", contentType = "header") {
                HomeHeader(state = refreshState, nowMillis = now, onRefresh = viewModel::refresh)
            }
            refreshNotices(state = refreshState, onRefresh = viewModel::refresh)

            if (!library.ready) {
                edgeToEdge(key = "shelf-skeleton", contentType = "shelfSkeleton") { ShelfSkeleton() }
                fullWidth(key = "feed-skeleton", contentType = "line") {
                    SkeletonLine(fraction = TITLE_SKELETON_FRACTION, height = Space.x5, modifier = Modifier.padding(top = Space.x4))
                }
                cardSkeletons()
                return@CardGrid
            }

            val shown = order.filter { it in visible }
            shown.forEach { shelf ->
                edgeToEdge(key = "shelf-${shelf.id}", contentType = "shelf") {
                    // A grip is only worth showing when there is somewhere to move to.
                    val grip: (@Composable () -> Unit)? = if (shown.size > 1) {
                        { ShelfGrip(title = shelf.title, onMove = { delta -> viewModel.moveShelf(shelf, delta, visible) }) }
                    } else {
                        null
                    }
                    Box(modifier = Modifier.padding(bottom = Space.x3)) {
                        when (shelf) {
                            HomeShelf.CONTINUE -> {
                                val leftAt = remember(continueItems) { continueItems.associate { it.first.id to it.second } }
                                CardShelf(
                                    title = shelf.title,
                                    items = continueItems.map { it.first },
                                    onOpen = openItem,
                                    surface = shelf.id,
                                    leading = grip,
                                    caption = { item -> leftAt[item.id]?.let { "Left at ${formatClock(it)}" } },
                                )
                            }
                            HomeShelf.RECENT -> CardShelf(
                                title = shelf.title,
                                items = recentItems,
                                onOpen = openItem,
                                surface = shelf.id,
                                leading = grip,
                                actionLabel = "History",
                                onAction = { navigator.open(HistoryRoute) },
                            )
                            HomeShelf.ON_DEVICE -> CardShelf(
                                title = shelf.title,
                                items = deviceItems,
                                onOpen = openItem,
                                surface = shelf.id,
                                leading = grip,
                                actionLabel = "Collection",
                                onAction = { navigator.open(CollectionRoute) },
                            )
                            HomeShelf.DATADAOS -> EntityShelf(
                                title = shelf.title,
                                items = yourDaos,
                                key = { it.key },
                                leading = grip,
                                actionLabel = "Browse",
                                onAction = { navigator.open(DataDaosRoute) },
                            ) { dao, modifier ->
                                DaoCard(dao = dao, onOpen = { navigator.open(DataDaoRoute(dao.key)) }, modifier = modifier)
                            }
                            HomeShelf.FOLLOWING -> EntityShelf(
                                title = shelf.title,
                                items = following,
                                key = { it.address },
                                leading = grip,
                            ) { uploader, modifier ->
                                UploaderCard(
                                    uploader = uploader,
                                    onOpen = { navigator.open(UploaderRoute(uploader.address)) },
                                    modifier = modifier,
                                )
                            }
                        }
                    }
                }
            }

            fullWidth(key = "feed-header", contentType = "feedHeader") {
                Column(
                    modifier = Modifier.padding(top = Space.x2),
                    verticalArrangement = Arrangement.spacedBy(Space.x3),
                ) {
                    SectionHeader(title = "Latest uploads", count = feed.size.takeIf { all.isNotEmpty() })
                    if (all.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.x3),
                            verticalArrangement = Arrangement.spacedBy(Space.x2),
                        ) {
                            SegmentedSwitch(options = FEED_KIND_LABELS, selectedIndex = kindIndex, onSelect = { kindIndex = it })
                            if (filterDaos.size > 1) {
                                FilterChips(
                                    options = listOf("All DataDAOs") + filterDaos.map { it.name },
                                    selectedIndex = filterDaos.indexOfFirst { it.key == activeDao } + 1,
                                    onSelect = { index -> daoFilter = filterDaos.getOrNull(index - 1)?.key },
                                )
                            }
                        }
                    }
                }
            }

            when {
                all.isEmpty() && yourDaos.isEmpty() && following.isEmpty() -> fullWidth(key = "feed-empty") {
                    EmptyState(
                        icon = AppIcons.Home,
                        title = "Nothing in your feed yet",
                        body = "New uploads from DataDAOs this wallet is a member of, and from people you follow, " +
                            "appear here. Browse the DataDAOs to find some.",
                        actionLabel = "Browse DataDAOs",
                        onAction = { navigator.open(DataDaosRoute) },
                    )
                }
                all.isEmpty() -> fullWidth(key = "feed-empty") {
                    EmptyState(
                        icon = AppIcons.Home,
                        title = "No uploads right now",
                        body = "Your DataDAOs and the people you follow have nothing live. Refresh to look again.",
                        actionLabel = "Refresh",
                        onAction = viewModel::refresh,
                    )
                }
                feed.isEmpty() -> fullWidth(key = "feed-empty") {
                    EmptyState(
                        icon = AppIcons.Sort,
                        title = "Nothing matches these filters",
                        body = "There are uploads in your feed, but none of this kind in this DataDAO.",
                        actionLabel = "Show everything",
                        onAction = {
                            kindIndex = 0
                            daoFilter = null
                        },
                    )
                }
                else -> days.forEach { day ->
                    fullWidth(key = "day-${day.label}", contentType = "day") {
                        GroupLabel(text = day.label, modifier = Modifier.padding(top = Space.x2))
                    }
                    mediaCards(
                        items = day.entries.map(FeedEntry::item),
                        onOpen = openItem,
                        newKeys = newKeys,
                        caption = { item -> reasons[item.id]?.caption() },
                    )
                }
            }
        }
    }
}

/** The page title, when the catalogue was last refreshed, and the Refresh button. */
@Composable
private fun HomeHeader(state: RefreshState, nowMillis: Long, onRefresh: () -> Unit) {
    PageHeader(
        title = "Home",
        subtitle = when {
            state.running -> state.stage?.label ?: "Refreshing…"
            state.lastRefreshedAtMillis != null -> {
                val age = formatAge(state.lastRefreshedAtMillis, nowMillis).orEmpty()
                "Last refreshed ${age.replaceFirstChar { it.lowercase() }}"
            }
            else -> "Not refreshed yet"
        },
        actions = {
            if (state.running) {
                Box(modifier = Modifier.size(Sizes.touchTarget), contentAlignment = Alignment.Center) {
                    BusyIndicator(modifier = Modifier.size(Sizes.iconLarge + Space.x2), label = "Refreshing")
                }
            } else {
                IconAction(
                    icon = AppIcons.Refresh,
                    label = "Refresh",
                    onClick = onRefresh,
                    shortcut = ShortcutAction.REFRESH,
                )
            }
        },
    )
}

/**
 * What the last refresh could not do, said once under the header: a failure (everything shown
 * is from before it), the index's rate limit with the time it lifts, or a part that was skipped.
 */
private fun LazyGridScope.refreshNotices(state: RefreshState, onRefresh: () -> Unit) {
    val limitedUntil = state.rateLimitedUntilMillis
    val error = state.error
    when {
        limitedUntil != null && limitedUntil > System.currentTimeMillis() -> fullWidth(key = "refresh-limited", contentType = "notice") {
            InlineNotice(
                text = "The index is rate limited. It will take requests again at ${clockTime(limitedUntil)}. " +
                    "Everything here is from the last refresh and still plays.",
                icon = AppIcons.Warning,
            )
        }
        error != null && !state.running -> fullWidth(key = "refresh-error", contentType = "notice") {
            InlineNotice(
                text = listOfNotNull(error.what, error.todo).joinToString(" "),
                isError = true,
                actionLabel = "Try again",
                onAction = onRefresh,
            )
        }
    }
    val note = state.note
    if (note != null && !state.running) {
        fullWidth(key = "refresh-note", contentType = "notice") { InlineNotice(text = note) }
    }
}

/**
 * The handle that moves a shelf. Dragging it up or down a short way swaps the shelf with its
 * neighbour, and keeps swapping as the drag goes on; Alt+Up and Alt+Down do the same from the
 * keyboard, and a screen reader gets "Move up" and "Move down" as actions.
 */
@Composable
private fun ShelfGrip(title: String, onMove: (delta: Int) -> Unit) {
    val colors = appColors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val threshold = with(LocalDensity.current) { Gestures.shelfMoveDistance.toPx() }
    val currentOnMove by rememberUpdatedState(onMove)
    var travelled by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .size(Sizes.touchTarget)
            .clip(Radius.md)
            .background(if (focused || hovered) colors.inset else Color.Transparent)
            .cursor(Cursor.Grab)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.toChord()?.let { shortcutFor(it, ShortcutScope.ITEM) }) {
                    ShortcutAction.MOVE_ROW_UP -> {
                        currentOnMove(-1)
                        true
                    }
                    ShortcutAction.MOVE_ROW_DOWN -> {
                        currentOnMove(1)
                        true
                    }
                    else -> false
                }
            }
            .hoverable(interaction)
            .focusable(interactionSource = interaction)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    travelled += delta
                    if (abs(travelled) >= threshold) {
                        currentOnMove(if (travelled > 0f) 1 else -1)
                        travelled = 0f
                    }
                },
                // A grip moves at once: no long-press, and it never competes with scrolling.
                startDragImmediately = true,
                onDragStopped = { travelled = 0f },
            )
            .semantics {
                contentDescription = "Move $title"
                customActions = listOf(
                    CustomAccessibilityAction("Move up") {
                        currentOnMove(-1)
                        true
                    },
                    CustomAccessibilityAction("Move down") {
                        currentOnMove(1)
                        true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.Grip,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

/** Why an upload is in the feed, where the card does not already say it. */
private fun FeedReason.caption(): String? = when (this) {
    FeedReason.FOLLOWED_UPLOADER -> "Following"
    FeedReason.YOUR_UPLOAD -> "Your upload"
    // The card already names the DataDAO.
    FeedReason.YOUR_DATADAO -> null
}

private fun clockTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private val FEED_KIND_LABELS = listOf("All", "Music", "Video")
private const val SHELF_LIMIT = 20
private const val TITLE_SKELETON_FRACTION = 0.3f
