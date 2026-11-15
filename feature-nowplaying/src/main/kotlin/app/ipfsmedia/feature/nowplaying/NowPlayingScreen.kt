package app.ipfsmedia.feature.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AlwaysDark
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.Artwork
import app.ipfsmedia.core.design.CoverBackdrop
import app.ipfsmedia.core.design.DaoBadge
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.Gestures
import app.ipfsmedia.core.design.GroupLabel
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.LocalDragController
import app.ipfsmedia.core.design.LocalItemActions
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.LocalWidthClass
import app.ipfsmedia.core.design.MediaRow
import app.ipfsmedia.core.design.PlayPauseButton
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.RowLeading
import app.ipfsmedia.core.design.SeekBar
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.StageIndicator
import app.ipfsmedia.core.design.StageInfo
import app.ipfsmedia.core.design.VIDEO_ASPECT
import app.ipfsmedia.core.design.VolumeControl
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.pageGutter
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.EntryKind
import app.ipfsmedia.core.library.Origin
import app.ipfsmedia.core.library.PlayableEntry
import app.ipfsmedia.core.library.ShortcutAction
import app.ipfsmedia.core.library.formatClock
import app.ipfsmedia.core.navigation.AlbumRoute
import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.HomeRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.UploaderRoute
import app.ipfsmedia.core.navigation.VideoRoute
import app.ipfsmedia.core.player.PlayerConnection
import app.ipfsmedia.core.player.PlayerUiState
import app.ipfsmedia.core.player.QueueRow
import app.ipfsmedia.core.player.RepeatMode
import app.ipfsmedia.feature.video.LocalRequestFullscreen
import app.ipfsmedia.feature.video.VideoPlayerBox

/**
 * The full-size view of what is playing: the picture (or the video itself), the whole
 * transport, and what comes next.
 *
 * It is dark in both themes, over a ground drawn from the upload's own generated pattern, so
 * the page takes on the colour of what is playing and changes with it. The player bar stays
 * where it is underneath; this is a bigger view of the same player, not a second one.
 */
@Composable
fun NowPlayingScreen() {
    val viewModel: NowPlayingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favouriteIds by viewModel.favouriteIds.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val entry = state.current?.entry

    AlwaysDark {
        val colors = appColors
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.ground),
        ) {
            // The ground changes with what is playing, by a cross-fade rather than a cut.
            AnimatedContent(
                targetState = entry?.recordKey,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "nowPlayingGround",
                modifier = Modifier.matchParentSize(),
            ) { recordKey ->
                if (recordKey != null) CoverBackdrop(seed = recordKey, modifier = Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(colors.scrim),
            )

            if (entry == null) {
                EmptyState(
                    icon = AppIcons.Playing,
                    title = "Nothing is playing",
                    body = "Play an album, a song or a video and it will be shown here at full size.",
                    actionLabel = "Go to Home",
                    onAction = { navigator.open(HomeRoute) },
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Stage(
                    state = state,
                    entry = entry,
                    isFavourite = entry.id in favouriteIds,
                    connection = viewModel.connection,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun Stage(
    state: PlayerUiState,
    entry: PlayableEntry,
    isFavourite: Boolean,
    connection: PlayerConnection,
    viewModel: NowPlayingViewModel,
) {
    val gutter = pageGutter
    val compact = LocalWidthClass.current.isCompact
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The picture takes what the window can spare: never more than half its height, so the
        // transport is on screen without scrolling at the minimum window size.
        val artSide = minOf(maxWidth - gutter * 2, maxHeight * ART_HEIGHT_FRACTION, Sizes.nowPlayingArt).coerceAtLeast(Sizes.heroArtCompact)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter, vertical = if (compact) Space.x4 else Space.x8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) Space.x4 else Space.x6),
        ) {
            if (state.isVideo) {
                Box(
                    modifier = Modifier
                        .widthIn(max = Sizes.readingWidth)
                        .fillMaxWidth()
                        .aspectRatio(state.videoAspectRatio?.coerceIn(MIN_VIDEO_ASPECT, MAX_VIDEO_ASPECT) ?: VIDEO_ASPECT)
                        .clip(Radius.xl),
                ) {
                    VideoPlayerBox(
                        fullscreen = false,
                        onToggleFullscreen = LocalRequestFullscreen.current,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Artwork(
                    recordKey = entry.recordKey,
                    title = entry.albumTitle,
                    modifier = Modifier.size(artSide),
                    shape = Radius.xl,
                )
            }

            Titles(state = state, entry = entry)

            Column(
                modifier = Modifier.widthIn(max = Sizes.readingWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                val position by viewModel.positionMs.collectAsStateWithLifecycle()
                SeekBar(
                    positionMs = { position },
                    durationMs = state.durationMs,
                    onSeek = connection::seekTo,
                    modifier = Modifier.fillMaxWidth(),
                )
                Transport(state = state, connection = connection, compact = compact)
                Extras(state = state, entry = entry, isFavourite = isFavourite, connection = connection, compact = compact)
            }

            UpNext(rows = state.upNext, firstIndex = state.playOrder.drop(1), connection = connection)
        }
    }
}

/** Title, uploader, album and DataDAO, each a way to the page it names. */
@Composable
private fun Titles(state: PlayerUiState, entry: PlayableEntry) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val library = LocalLibraryState.current
    val dao = library.dao(entry.daoKey)
    val waiting = state.waitingOn
    Column(
        modifier = Modifier.widthIn(max = Sizes.readingWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { navigator.open(UploaderRoute(entry.uploaderAddress)) }) {
                Text(text = entry.uploaderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when (entry.kind) {
                // A song names its album, which is where the rest of it is.
                EntryKind.SONG -> TextButton(onClick = { navigator.open(AlbumRoute(entry.recordKey)) }) {
                    Text(text = entry.albumTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                EntryKind.ALBUM -> TextButton(onClick = { navigator.open(AlbumRoute(entry.recordKey)) }) { Text("Album page") }
                EntryKind.VIDEO -> TextButton(onClick = { navigator.open(VideoRoute(entry.recordKey)) }) { Text("Video page") }
            }
        }
        if (dao != null) {
            DaoBadge(name = dao.name, onClick = { navigator.open(DataDaoRoute(dao.key)) })
        }
        if (waiting != null) {
            // What is really happening while nothing is heard: fetching, or unlocking.
            StageIndicator(
                stage = StageInfo(waiting.step.label, waiting.fraction),
                modifier = Modifier
                    .padding(top = Space.x2)
                    .widthIn(max = Sizes.searchFieldWidth),
            )
        }
    }
}

@Composable
private fun Transport(state: PlayerUiState, connection: PlayerConnection, compact: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) Space.none else Space.x2),
    ) {
        IconAction(
            icon = AppIcons.Shuffle,
            label = if (state.shuffle) "Shuffle is on" else "Shuffle",
            onClick = connection::toggleShuffle,
            selected = state.shuffle,
            shortcut = ShortcutAction.SHUFFLE,
        )
        IconAction(
            icon = AppIcons.SkipPrevious,
            label = "Previous",
            onClick = connection::previous,
            shortcut = ShortcutAction.PREVIOUS,
            large = true,
        )
        if (!compact) {
            IconAction(
                icon = AppIcons.Replay10,
                label = "Back 10 seconds",
                onClick = { connection.seekBy(-Gestures.KEY_SEEK_MS) },
                shortcut = ShortcutAction.SEEK_BACK,
            )
        }
        PlayPauseButton(
            showsPause = state.showsPause,
            onClick = connection::togglePlay,
            large = true,
            modifier = Modifier.padding(horizontal = Space.x2),
        )
        if (!compact) {
            IconAction(
                icon = AppIcons.Forward10,
                label = "Forward 10 seconds",
                onClick = { connection.seekBy(Gestures.KEY_SEEK_MS) },
                shortcut = ShortcutAction.SEEK_FORWARD,
            )
        }
        IconAction(
            icon = AppIcons.SkipNext,
            label = "Next",
            onClick = connection::next,
            enabled = state.hasNext,
            shortcut = ShortcutAction.NEXT,
            large = true,
        )
        IconAction(
            icon = if (state.repeat == RepeatMode.ONE) AppIcons.RepeatOne else AppIcons.Repeat,
            label = when (state.repeat) {
                RepeatMode.OFF -> "Repeat"
                RepeatMode.ALL -> "Repeating the queue"
                RepeatMode.ONE -> "Repeating this one"
            },
            onClick = connection::cycleRepeat,
            selected = state.repeat != RepeatMode.OFF,
            shortcut = ShortcutAction.REPEAT,
        )
    }
}

/** Favourite, "add to…" (the same tray a long-press raises), and the app's own volume. */
@Composable
private fun Extras(
    state: PlayerUiState,
    entry: PlayableEntry,
    isFavourite: Boolean,
    connection: PlayerConnection,
    compact: Boolean,
) {
    val actions = LocalItemActions.current
    val drag = LocalDragController.current
    val payload = remember(entry, isFavourite) { DragPayload.Media(entries = listOf(entry), isFavourite = isFavourite) }
    var mutedFrom by rememberSaveable { mutableFloatStateOf(1f) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
        IconAction(
            icon = if (isFavourite) AppIcons.Favourite else AppIcons.FavouriteOutline,
            label = if (isFavourite) "Remove from favourites" else "Favourite",
            onClick = { actions.perform(if (isFavourite) Destination.Unfavourite else Destination.Favourite, payload) },
            selected = isFavourite,
        )
        IconAction(
            icon = AppIcons.PlaylistAdd,
            label = "Add to a playlist, and more",
            // The tray, opened as a menu: the same list of places a long-press offers.
            onClick = { drag.openTray(payload, Origin.Library) },
        )
        if (!compact) {
            VolumeControl(
                volume = state.volume,
                onVolume = connection::setVolume,
                onToggleMute = {
                    if (state.volume > 0f) {
                        mutedFrom = state.volume
                        connection.setVolume(0f)
                    } else {
                        connection.setVolume(mutedFrom.takeIf { it > 0f } ?: 1f)
                    }
                },
            )
        }
    }
}

/** The next few rows in the order they will really play. The whole queue is in the queue pane. */
@Composable
private fun UpNext(rows: List<QueueRow>, firstIndex: List<Int>, connection: PlayerConnection) {
    if (rows.isEmpty()) return
    val colors = appColors
    Column(
        modifier = Modifier
            .widthIn(max = Sizes.readingWidth)
            .fillMaxWidth()
            .clip(Radius.xl)
            .background(colors.ground.copy(alpha = PANEL_ALPHA))
            .padding(Space.x2),
        verticalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        GroupLabel(text = "Up next", modifier = Modifier.padding(start = Space.x2, top = Space.x1))
        rows.take(UP_NEXT_ROWS).forEachIndexed { position, row ->
            val entry = row.entry
            MediaRow(
                title = entry.title,
                subtitle = "${entry.uploaderName} · ${entry.albumTitle}",
                payload = { DragPayload.Media(entries = listOf(entry)) },
                // Its place in the queue, so "Remove from queue" removes this row.
                origin = Origin.Queue(firstIndex.getOrElse(position) { 0 }),
                onActivate = { connection.skipTo(row.uid) },
                leading = RowLeading.Art(entry.recordKey, entry.albumTitle),
                recordKey = entry.recordKey,
                chapterIndex = entry.chapterIndex,
                trailing = formatClock(entry.durationMs),
                swipeToQueue = false,
            )
        }
        if (rows.size > UP_NEXT_ROWS) {
            Text(
                text = "and ${rows.size - UP_NEXT_ROWS} more in the queue",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = Space.x2, bottom = Space.x1),
            )
        }
    }
}

private const val ART_HEIGHT_FRACTION = 0.5f
private const val MIN_VIDEO_ASPECT = 1.2f
private const val MAX_VIDEO_ASPECT = 2.4f
private const val PANEL_ALPHA = 0.55f
private const val UP_NEXT_ROWS = 4
