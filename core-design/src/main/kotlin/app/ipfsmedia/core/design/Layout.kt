package app.ipfsmedia.core.design

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/** The page gutter for the current window: 24dp, or 16dp under 600dp. */
val pageGutter: Dp
    @Composable get() = if (LocalWidthClass.current.isCompact) Space.gutterCompact else Space.gutter

/**
 * A page that is a grid of cards under full-width parts (a hero, a filter bar, section titles).
 *
 * The column count comes from a minimum card width, so the grid refills as the window is
 * resized. Full-width parts are added with [fullWidth]; they sit edge to edge, and cards get the
 * page gutter. Keys and content types are required, so a grid of thousands scrolls without
 * recomposing what it does not have to.
 */
@Composable
fun CardGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    bottomInset: Dp = Space.none,
    content: LazyGridScope.() -> Unit,
) {
    val compact = LocalWidthClass.current.isCompact
    val gutter = pageGutter
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (compact) Sizes.cardMinWidthCompact else Sizes.cardMinWidth),
        state = state,
        modifier = modifier
            .fillMaxSize()
            .dragAutoScroll(state),
        contentPadding = PaddingValues(start = gutter, end = gutter, bottom = bottomInset + Space.x6),
        horizontalArrangement = Arrangement.spacedBy(if (compact) Space.x3 else Space.x5),
        verticalArrangement = Arrangement.spacedBy(if (compact) Space.x3 else Space.x5),
        content = content,
    )
}

/** A part of a [CardGrid] that spans every column: a section title, a filter bar, a list row. */
fun LazyGridScope.fullWidth(
    key: Any,
    contentType: Any = "fullWidth",
    content: @Composable LazyGridItemScope.() -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = contentType, content = content)
}

/**
 * A full-width part that ignores the grid's side padding: a hero that runs edge to edge.
 * Lazy grids pad every item, so the hero is given negative room on both sides to win it back.
 */
fun LazyGridScope.edgeToEdge(
    key: Any,
    contentType: Any = "edgeToEdge",
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = contentType) {
        val gutter = pageGutter
        Box(
            // Animated placement, so shelves glide when their order changes.
            modifier = Modifier
                .animateItem()
                .bleed(gutter),
            content = { content() },
        )
    }
}

/** Grows this box by [by] on each side, past its parent's padding. */
fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val side = by.roundToPx()
    val extra = side * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    layout(constraints.constrainWidth(placeable.width - extra), placeable.height) {
        placeable.place(-side, 0)
    }
}

/**
 * The line between two panes that can be dragged to resize them. Thin to look at, wide to grab,
 * with a double-arrow pointer over it; arrow keys are handled by the pane it belongs to.
 *
 * @param onDrag change in dp; positive is towards the end of the row.
 */
@Composable
fun PaneDivider(
    label: String,
    onDrag: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    onDragEnd: () -> Unit = {},
) {
    val density = LocalDensity.current
    val currentOnDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(Sizes.dividerHitWidth)
            .cursor(Cursor.ResizeHorizontal)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> currentOnDrag(with(density) { delta.toDp() }) },
                onDragStopped = { onDragEnd() },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(thickness = Sizes.dividerLine, color = appColors.border)
    }
}

/**
 * Pull down to refresh, with the expressive loading indicator. Pulling is a touch gesture, so
 * every page that uses this also has a Refresh button and answers to Ctrl+R; none of them
 * refreshes on its own (docs/PLAN.md §11).
 */
@Composable
fun RefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
}

/** Zero, for call sites that must pass a dp and have none to add. */
val NoInset: Dp = 0.dp
