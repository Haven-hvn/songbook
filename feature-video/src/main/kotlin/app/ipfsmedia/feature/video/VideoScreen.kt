package app.ipfsmedia.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.Artwork
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.HeroHeader
import app.ipfsmedia.core.design.HeroSkeleton
import app.ipfsmedia.core.design.InlineNotice
import app.ipfsmedia.core.design.ItemHeroFacts
import app.ipfsmedia.core.design.ItemPageActions
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.LocalStages
import app.ipfsmedia.core.design.LockedNotice
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.ReadingColumn
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.StageIndicator
import app.ipfsmedia.core.design.VIDEO_ASPECT
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.heroArt
import app.ipfsmedia.core.design.heroArtSize
import app.ipfsmedia.core.library.formatClock
import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.UploaderRoute
import app.ipfsmedia.feature.discussion.CommentsSection

/**
 * One video.
 *
 * Until it is the thing playing, the page is its hero and its actions. Once it is, the video
 * plays here in the content area with its own controls, and the player bar stays where it is.
 * The whole file is fetched before it plays (`foc-cache` works in whole pieces), so a long
 * video waits on its download the first time; the page says which stage it is at, and offers
 * Download so the wait can happen ahead of time.
 */
@Composable
fun VideoScreen(recordKey: String, onFullscreen: () -> Unit) {
    val viewModel: VideoViewModel = hiltViewModel()
    val library = LocalLibraryState.current
    val navigator = LocalNavigator.current
    val playerState by viewModel.state.collectAsStateWithLifecycle()
    val resume by viewModel.resume.collectAsStateWithLifecycle()
    val colors = appColors
    val item = library.item(recordKey)
    val stage = LocalStages.current[recordKey]
    val isCurrent = playerState.current?.entry?.recordKey == recordKey

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.x12)) {
        when {
            item == null && !library.ready -> item(key = "loading") { HeroSkeleton() }
            item == null -> item(key = "gone") {
                EmptyState(
                    icon = AppIcons.Video,
                    title = "This video is not in the index any more",
                    body = "Its record has expired or was removed. Refresh to see what is there now.",
                    actionLabel = "Back",
                    onAction = { navigator.back() },
                )
            }
            else -> {
                if (isCurrent) {
                    item(key = "player") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(playerState.videoAspectRatio?.coerceIn(MIN_PAGE_ASPECT, MAX_PAGE_ASPECT) ?: VIDEO_ASPECT),
                        ) {
                            VideoPlayerBox(
                                fullscreen = false,
                                onToggleFullscreen = onFullscreen,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                item(key = "hero") {
                    HeroHeader(
                        title = item.title,
                        seed = item.id,
                        overline = "Video",
                        subtitle = "Uploaded by ${item.uploaderName}",
                        art = if (isCurrent) {
                            null
                        } else {
                            { Artwork(recordKey = item.id, title = item.title, modifier = Modifier.heroArtSize().heroArt(item.id), shape = Radius.lg) }
                        },
                        facts = {
                            val daoKey = item.daoKey
                            ItemHeroFacts(
                                item = item,
                                onOpenDao = if (daoKey != null) ({ navigator.open(DataDaoRoute(daoKey)) }) else null,
                            )
                        },
                        actions = { ItemPageActions(item) },
                    )
                }
                item(key = "details") {
                    ReadingColumn(modifier = Modifier.padding(top = Space.x4)) {
                        val daoKey = item.daoKey
                        LockedNotice(
                            item = item,
                            onSeeRule = if (daoKey != null) ({ navigator.open(DataDaoRoute(daoKey, tab = "ABOUT")) }) else null,
                        )
                        if (stage != null && !isCurrent) StageIndicator(stage = stage)
                        val left = resume[recordKey]
                        if (left != null && !isCurrent) {
                            InlineNotice(
                                text = "Play continues from ${formatClock(left.positionMs)}, where you left it.",
                                icon = AppIcons.History,
                            )
                        }
                        if (!item.access.isLocked && stage == null && !isCurrent) {
                            Text(
                                text = "A video is fetched whole before it starts. Download it first if you want it ready.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        val description = item.media.description
                        if (!description.isNullOrBlank()) {
                            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        }
                        TextButton(onClick = { navigator.open(UploaderRoute(item.uploaderAddress)) }) {
                            Text("More from ${item.uploaderName}")
                        }
                        // Shown only when there are comments; otherwise nothing at all.
                        CommentsSection(rootKey = item.id)
                    }
                }
            }
        }
    }
}

private const val MIN_PAGE_ASPECT = 1.2f
private const val MAX_PAGE_ASPECT = 2.4f
