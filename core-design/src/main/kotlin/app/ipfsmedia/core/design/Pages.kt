package app.ipfsmedia.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.ipfsmedia.core.library.ItemKind
import app.ipfsmedia.core.library.LibraryItem
import kotlinx.coroutines.launch

/**
 * The top of a page that has no hero: Home, Collection, Playlists, History, Search, Settings,
 * the DataDAO directory. A title, an optional line under it, and the page's own actions.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = appColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Space.x6, bottom = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actions != null) actions()
    }
}

/** A row of filter chips that scrolls sideways when the window is narrow. One is selected. */
@Composable
fun FilterChips(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(text = option, maxLines = 1) },
            )
        }
    }
}

/** A short fact on a hero: a count, a length, a date. Light on the hero's dark ground. */
@Composable
fun HeroFact(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .height(Sizes.badgeHeight)
            .clip(Radius.full)
            .background(HeroFactGround)
            .padding(horizontal = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = HeroFactText, modifier = Modifier.size(Sizes.iconSmall))
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = HeroFactText, maxLines = 1)
    }
}

/** Running text at a readable width, with the page gutter: comments, About, Settings. */
@Composable
fun ReadingColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Space.x4),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        Column(
            modifier = Modifier
                .widthIn(max = Sizes.readingWidth)
                .padding(horizontal = pageGutter),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/** A titled block of settings or facts, on the elevated ground. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.xl)
            .background(colors.groundSecondary)
            .padding(Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        if (title != null) GroupLabel(text = title)
        content()
    }
}

/** Two lines of label and value, for facts that are read rather than acted on. */
@Composable
fun FactRow(label: String, value: String, modifier: Modifier = Modifier, monospace: Boolean = false) {
    val colors = appColors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        Text(
            text = value,
            style = if (monospace) AppType.clock else MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
    }
}

/**
 * Cards for a list of uploads, as items of a [CardGrid]. Stable keys and one content type, so a
 * grid of thousands recycles its cards.
 *
 * @param newKeys record keys that arrived with the last refresh; their cards are marked new.
 */
fun LazyGridScope.mediaCards(
    items: List<LibraryItem>,
    onOpen: (LibraryItem) -> Unit,
    keyPrefix: String = "",
    newKeys: Set<String> = emptySet(),
    caption: (LibraryItem) -> String? = { null },
) {
    items(items = items, key = { "$keyPrefix${it.id}" }, contentType = { "mediaCard" }) { item ->
        MediaCard(
            item = item,
            onOpen = { onOpen(item) },
            isNew = item.id in newKeys,
            caption = caption(item),
            modifier = Modifier.animateItem(),
        )
    }
}

/** Card-shaped placeholders for a grid that is waiting on its first data. */
fun LazyGridScope.cardSkeletons(count: Int = 12, wide: Boolean = false, keyPrefix: String = "skeleton") {
    items(count = count, key = { "$keyPrefix-$it" }, contentType = { "cardSkeleton" }) {
        MediaCardSkeleton(wide = wide)
    }
}

/**
 * The tabs of a page with several faces (a DataDAO: Music, Video, Discussion, Uploaders, About).
 * They scroll sideways when the window is narrow, and start at the page gutter like everything
 * else on the page.
 */
@Composable
fun PageTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceIn(0, (labels.size - 1).coerceAtLeast(0)),
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        contentColor = colors.accent,
        edgePadding = Space.none,
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                selectedContentColor = colors.accent,
                unselectedContentColor = colors.textSecondary,
                text = { Text(text = label, maxLines = 1) },
            )
        }
    }
}

/**
 * A button that opens a short list where exactly one choice is in force: how a page is sorted.
 * The button's label says the current choice, so it is readable without opening it.
 */
@Composable
fun ChoiceMenu(
    icon: ImageVector,
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconAction(
            icon = icon,
            label = "$label: ${options.getOrNull(selectedIndex).orEmpty()}",
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        open = false
                        onSelect(index)
                    },
                    leadingIcon = {
                        if (index == selectedIndex) {
                            Icon(imageVector = AppIcons.Check, contentDescription = "Selected", modifier = Modifier.size(Sizes.icon))
                        } else {
                            Spacer(modifier = Modifier.size(Sizes.icon))
                        }
                    },
                )
            }
        }
    }
}

/**
 * A titled row of cards that scrolls sideways: a Home shelf, "More from this uploader".
 *
 * Video cards are wider than album cards by the video aspect, so every picture on a shelf is the
 * same height. With a pointer there are buttons to move a page at a time; with touch the row is
 * simply dragged.
 *
 * @param surface names this shelf for card-to-hero transitions, since an upload can be on two
 *   shelves of one page at once (see [LocalArtSurface]).
 * @param leading something before the title: the grip that rearranges Home's shelves.
 */
@Composable
fun CardShelf(
    title: String,
    items: List<LibraryItem>,
    onOpen: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
    surface: String = title,
    newKeys: Set<String> = emptySet(),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    caption: (LibraryItem) -> String? = { null },
) {
    val gutter = pageGutter
    val compact = LocalWidthClass.current.isCompact
    val cardWidth = if (compact) Sizes.cardMinWidthCompact else Sizes.cardMinWidth
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pointer = !LocalInputKind.current.touchOnly

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            if (leading != null) leading()
            SectionHeader(
                title = title,
                modifier = Modifier.weight(1f),
                count = items.size,
                actionLabel = actionLabel,
                onAction = onAction,
            )
            if (pointer && !compact) {
                IconAction(
                    icon = AppIcons.ChevronLeft,
                    label = "Scroll $title back",
                    enabled = rowState.canScrollBackward,
                    onClick = { scope.launch { rowState.animateScrollBy(-pageOf(rowState.layoutInfo.viewportSize.width)) } },
                )
                IconAction(
                    icon = AppIcons.ChevronRight,
                    label = "Scroll $title on",
                    enabled = rowState.canScrollForward,
                    onClick = { scope.launch { rowState.animateScrollBy(pageOf(rowState.layoutInfo.viewportSize.width)) } },
                )
            }
        }
        CompositionLocalProvider(LocalArtSurface provides "shelf:$surface") {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(if (compact) Space.x3 else Space.x5),
            ) {
                items(items = items, key = { it.id }, contentType = { it.kind }) { item ->
                    MediaCard(
                        item = item,
                        onOpen = { onOpen(item) },
                        isNew = item.id in newKeys,
                        caption = caption(item),
                        modifier = Modifier
                            .width(if (item.kind == ItemKind.VIDEO) cardWidth * VIDEO_ASPECT else cardWidth)
                            .animateItem(),
                    )
                }
            }
        }
    }
}

/** A shelf that is waiting on its first data: the same title bar and a row of card placeholders. */
@Composable
fun ShelfSkeleton(modifier: Modifier = Modifier) {
    val gutter = pageGutter
    val cardWidth = if (LocalWidthClass.current.isCompact) Sizes.cardMinWidthCompact else Sizes.cardMinWidth
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = gutter),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        SkeletonLine(fraction = SHELF_TITLE_FRACTION, height = Space.x5)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
            repeat(SHELF_SKELETON_CARDS) { MediaCardSkeleton(modifier = Modifier.width(cardWidth)) }
        }
    }
}

/**
 * Whether an activation plays a row or only selects it. A mouse click selects (a double-click
 * plays); a tap and Enter play, because touch has no second, lighter click to select with
 * (docs/PLAN.md §6, the input table).
 */
val ActivatedBy.plays: Boolean get() = this != ActivatedBy.Mouse

/** How far the shelf buttons move: most of a screenful, so one card stays as a landmark. */
private fun pageOf(viewportWidth: Int): Float = viewportWidth * SHELF_PAGE_FRACTION

private const val SHELF_PAGE_FRACTION = 0.8f
private const val SHELF_TITLE_FRACTION = 0.25f
private const val SHELF_SKELETON_CARDS = 4

private val HeroFactGround = Color.White.copy(alpha = 0.16f)
private val HeroFactText = Color.White.copy(alpha = 0.92f)
