package app.ipfsmedia.feature.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.Artwork
import app.ipfsmedia.core.design.CardShelf
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.HeroFact
import app.ipfsmedia.core.design.HeroHeader
import app.ipfsmedia.core.design.HeroSkeleton
import app.ipfsmedia.core.design.InlineNotice
import app.ipfsmedia.core.design.ItemHeroFacts
import app.ipfsmedia.core.design.ItemPageActions
import app.ipfsmedia.core.design.LocalItemActions
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.LocalStages
import app.ipfsmedia.core.design.LockedNotice
import app.ipfsmedia.core.design.MediaRow
import app.ipfsmedia.core.design.MediaRowSkeleton
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.ReadingColumn
import app.ipfsmedia.core.design.RowLeading
import app.ipfsmedia.core.design.SectionHeader
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.StageIndicator
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.dragAutoScroll
import app.ipfsmedia.core.design.heroArt
import app.ipfsmedia.core.design.heroArtSize
import app.ipfsmedia.core.design.pageGutter
import app.ipfsmedia.core.design.plays
import app.ipfsmedia.core.library.AccessState
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.ItemKind
import app.ipfsmedia.core.library.LibraryItem
import app.ipfsmedia.core.library.asPayload
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.library.formatClock
import app.ipfsmedia.core.library.lockedReason
import app.ipfsmedia.core.library.playEntries
import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.UploaderRoute
import app.ipfsmedia.core.navigation.mediaRoute
import app.ipfsmedia.feature.discussion.CommentsSection

/**
 * One album: one audio upload, and the songs inside it.
 *
 * An album is a single file. Its songs are the chapters written into that file, so the app only
 * knows them once the file has been fetched (docs/PLAN.md §5, "Songs"). Before that, the page
 * says so and offers the album as the one thing it is; after, each song is a row that can be
 * played, queued, favourited or carried into a playlist on its own.
 *
 * A tap on a song plays it and leaves the rest of the album queued around it. With a mouse, one
 * click selects the row and a double-click plays.
 */
@Composable
fun AlbumScreen(recordKey: String) {
    val library = LocalLibraryState.current
    val navigator = LocalNavigator.current
    val item = library.item(recordKey)
    val listState = rememberLazyListState()
    val gutter = pageGutter
    // Rows have their own inner padding; taking it off the gutter lines their text up with the hero's.
    val rowInset = gutter - Space.x2
    var selected by rememberSaveable(recordKey) { mutableStateOf<Int?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .dragAutoScroll(listState),
        contentPadding = PaddingValues(bottom = Space.x12),
    ) {
        when {
            item == null && !library.ready -> {
                item(key = "loading", contentType = "hero") { HeroSkeleton() }
                items(count = SKELETON_ROWS, key = { "skeleton-$it" }, contentType = { "skeleton" }) {
                    MediaRowSkeleton(modifier = Modifier.padding(horizontal = rowInset))
                }
            }

            item == null -> item(key = "gone") {
                EmptyState(
                    icon = AppIcons.Album,
                    title = "This album is not in the index any more",
                    body = "Its record has expired or was removed. Refresh to see what is there now.",
                    actionLabel = "Back",
                    onAction = { navigator.back() },
                )
            }

            else -> {
                val songs = item.songs
                item(key = "hero", contentType = "hero") {
                    HeroHeader(
                        title = item.title,
                        seed = item.id,
                        overline = "Album",
                        subtitle = "Uploaded by ${item.uploaderName}",
                        art = {
                            Artwork(
                                recordKey = item.id,
                                title = item.title,
                                modifier = Modifier
                                    .heroArtSize()
                                    .heroArt(item.id),
                                shape = Radius.lg,
                            )
                        },
                        facts = {
                            val daoKey = item.daoKey
                            ItemHeroFacts(
                                item = item,
                                onOpenDao = if (daoKey != null) ({ navigator.open(DataDaoRoute(daoKey)) }) else null,
                            )
                            if (!songs.isNullOrEmpty()) HeroFact(text = countOf(songs.size, "song"))
                        },
                        actions = { ItemPageActions(item) },
                    )
                }

                item(key = "notices", contentType = "notices") {
                    AlbumNotices(item = item)
                }

                when {
                    // Never fetched: the song list is still inside the file.
                    songs == null -> item(key = "whole", contentType = "song") {
                        WholeAlbumRow(
                            item = item,
                            subtitle = "Plays as one track until its songs are known",
                            selected = selected == WHOLE,
                            onSelect = { selected = WHOLE },
                            modifier = Modifier.padding(horizontal = rowInset),
                        )
                    }

                    // Fetched, and the file has no chapters: it really is one track.
                    songs.isEmpty() -> item(key = "whole", contentType = "song") {
                        WholeAlbumRow(
                            item = item,
                            subtitle = "This album has no song list. It plays as one track.",
                            selected = selected == WHOLE,
                            onSelect = { selected = WHOLE },
                            modifier = Modifier.padding(horizontal = rowInset),
                        )
                    }

                    else -> itemsIndexed(
                        items = songs,
                        key = { _, song -> "song-${song.index}" },
                        contentType = { _, _ -> "song" },
                    ) { position, song ->
                        val actions = LocalItemActions.current
                        val locked = item.lockedReason()
                        MediaRow(
                            title = song.title,
                            subtitle = null,
                            payload = { item.asPayload(song) },
                            onActivate = { by ->
                                when {
                                    !by.plays -> selected = song.index
                                    // A locked song says why, in the words its badge uses.
                                    locked != null -> actions.perform(Destination.PlayNow, item.asPayload(song))
                                    else -> actions.playFrom(item.playEntries(), position, item.title)
                                }
                            },
                            leading = RowLeading.Number(position + 1),
                            recordKey = item.id,
                            chapterIndex = song.index,
                            trailing = formatClock(song.durationMs),
                            unavailable = if (locked != null) "Locked" else null,
                            selected = selected == song.index,
                            modifier = Modifier
                                .padding(horizontal = rowInset)
                                .animateItem(),
                        )
                    }
                }

                item(key = "about", contentType = "about") {
                    AlbumAbout(item = item)
                }

                val more = library.itemsBy(item.uploaderAddress).filter { it.id != item.id }.take(MORE_LIMIT)
                if (more.isNotEmpty()) {
                    item(key = "more", contentType = "shelf") {
                        CardShelf(
                            title = "More from ${item.uploaderName}",
                            items = more,
                            onOpen = { navigator.open(mediaRoute(it.id, it.kind == ItemKind.VIDEO)) },
                            actionLabel = "See all",
                            onAction = { navigator.open(UploaderRoute(item.uploaderAddress)) },
                            modifier = Modifier.padding(top = Space.x8),
                        )
                    }
                }
            }
        }
    }
}

/** Under the hero: why it is locked, what is being fetched, and why there are no songs yet. */
@Composable
private fun AlbumNotices(item: LibraryItem) {
    val navigator = LocalNavigator.current
    val actions = LocalItemActions.current
    val stage = LocalStages.current[item.id]
    val daoKey = item.daoKey
    val locked = item.access.isLocked
    val showsAnything = locked || stage != null || item.songs == null || item.access == AccessState.UNKNOWN
    if (!showsAnything) {
        // Still a little air between the hero and the first song.
        Spacer(modifier = Modifier.height(Space.x4))
        return
    }
    ReadingColumn(
        modifier = Modifier.padding(top = Space.x4, bottom = Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        LockedNotice(
            item = item,
            onSeeRule = if (daoKey != null) ({ navigator.open(DataDaoRoute(daoKey, tab = "ABOUT")) }) else null,
        )
        if (stage != null) StageIndicator(stage = stage)
        if (item.songs == null && !locked && stage == null) {
            InlineNotice(
                text = "The song list is inside the album's file, so it appears here once the album " +
                    "has been fetched. Play it, or download it first.",
                icon = AppIcons.Song,
                actionLabel = "Download",
                onAction = { actions.perform(Destination.Download, item.asPayload()) },
            )
        }
    }
}

/** The album as one row, for when it has no songs to show. */
@Composable
private fun WholeAlbumRow(
    item: LibraryItem,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = LocalItemActions.current
    val locked = item.lockedReason()
    MediaRow(
        title = item.title,
        subtitle = subtitle,
        payload = { item.asPayload() },
        onActivate = { by ->
            if (by.plays) actions.perform(Destination.PlayNow, item.asPayload()) else onSelect()
        },
        leading = RowLeading.Number(1),
        recordKey = item.id,
        trailing = formatClock(item.durationMs),
        unavailable = if (locked != null) "Locked" else null,
        selected = selected,
        modifier = modifier,
    )
}

/** What the uploader wrote about it, where to find more of theirs, and comments if there are any. */
@Composable
private fun AlbumAbout(item: LibraryItem) {
    val navigator = LocalNavigator.current
    val colors = appColors
    val description = item.media.description
    ReadingColumn(modifier = Modifier.padding(top = Space.x6)) {
        if (!description.isNullOrBlank()) {
            SectionHeader(title = "About")
            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        }
        TextButton(onClick = { navigator.open(UploaderRoute(item.uploaderAddress)) }) {
            Text("Go to ${item.uploaderName}")
        }
        // Shown only when there are comments; otherwise nothing at all (docs/PLAN.md §11).
        CommentsSection(rootKey = item.id)
    }
}

/** Stands for "the whole album" in the page's selection, where songs use their index. */
private const val WHOLE = -1
private const val SKELETON_ROWS = 8
private const val MORE_LIMIT = 12
