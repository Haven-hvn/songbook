package app.ipfsmedia.feature.video

import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.ipfsmedia.core.design.AlwaysDark
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.Gestures
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.SeekBar
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.StageIndicator
import app.ipfsmedia.core.design.StageInfo
import app.ipfsmedia.core.design.VIDEO_ASPECT
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.library.ShortcutAction
import kotlinx.coroutines.delay

/**
 * Where the video is drawn. The player lives in the playback service; this hands it a surface
 * to draw on and takes it back when it leaves the screen. Letterboxed to the video's shape.
 */
@Composable
fun VideoSurface(player: Player?, aspectRatio: Float?, modifier: Modifier = Modifier) {
    var surface by remember { mutableStateOf<SurfaceView?>(null) }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.scrim),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context -> SurfaceView(context).also { surface = it } },
            modifier = Modifier.aspectRatio(aspectRatio?.takeIf { it > 0f } ?: VIDEO_ASPECT),
        )
    }
    DisposableEffect(player, surface) {
        val view = surface
        if (player != null && view != null) player.setVideoSurfaceView(view)
        onDispose {
            if (player != null && view != null) player.clearVideoSurfaceView(view)
        }
    }
}

/**
 * The video with its controls, for touch, mouse and keyboard.
 *
 * Tap toggles the controls; double-tap on either side seeks ten seconds; the controls show
 * while the pointer is over the video and hide a few seconds after the last touch while it
 * plays. F toggles full screen (handled by the shell), and the buttons do the same things for
 * anyone without a keyboard. Nothing depends on hover.
 */
@Composable
fun VideoPlayerBox(
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VideoViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val position by viewModel.positionMs.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val connection = viewModel.connection
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var touched by remember { mutableIntStateOf(0) }
    var pinned by remember { mutableStateOf(true) }

    // Controls stay while paused or waiting; while playing they go a moment after the last touch.
    LaunchedEffect(touched, state.isPlaying) {
        pinned = true
        if (state.isPlaying) {
            delay(Gestures.VIDEO_CONTROLS_HIDE_MS)
            pinned = false
        }
    }
    val controlsVisible = pinned || hovered || !state.isPlaying

    Box(
        modifier = modifier
            .hoverable(hoverSource)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (pinned) pinned = false else touched++
                    },
                    onDoubleTap = { at ->
                        // Either side of the picture: back or forward ten seconds.
                        connection.seekBy(if (at.x < size.width / 2) -Gestures.KEY_SEEK_MS else Gestures.KEY_SEEK_MS)
                        touched++
                    },
                )
            }
            .semantics { contentDescription = "Video: ${state.current?.entry?.title.orEmpty()}" },
    ) {
        VideoSurface(player = player, aspectRatio = state.videoAspectRatio, modifier = Modifier.fillMaxSize())

        AlwaysDark {
            val colors = appColors
            state.waitingOn?.let { waiting ->
                // Fetching or unlocking: what is really happening, on the video itself.
                StageIndicator(
                    stage = StageInfo(waiting.step.label, waiting.fraction),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(STAGE_WIDTH_FRACTION)
                        .background(colors.scrim, Radius.md)
                        .padding(Space.x3),
                )
            }
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.scrim)
                        .padding(horizontal = Space.x3, vertical = Space.x1),
                ) {
                    SeekBar(
                        positionMs = { position },
                        durationMs = state.durationMs,
                        onSeek = {
                            connection.seekTo(it)
                            touched++
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.x1),
                    ) {
                        IconAction(
                            icon = AppIcons.Replay10,
                            label = "Back 10 seconds",
                            onClick = {
                                connection.seekBy(-Gestures.KEY_SEEK_MS)
                                touched++
                            },
                            shortcut = ShortcutAction.SEEK_BACK,
                            tint = colors.textPrimary,
                        )
                        IconAction(
                            icon = if (state.showsPause) AppIcons.Pause else AppIcons.Play,
                            label = if (state.showsPause) "Pause" else "Play",
                            onClick = {
                                connection.togglePlay()
                                touched++
                            },
                            shortcut = ShortcutAction.PLAY_PAUSE,
                            tint = colors.textPrimary,
                            large = true,
                        )
                        IconAction(
                            icon = AppIcons.Forward10,
                            label = "Forward 10 seconds",
                            onClick = {
                                connection.seekBy(Gestures.KEY_SEEK_MS)
                                touched++
                            },
                            shortcut = ShortcutAction.SEEK_FORWARD,
                            tint = colors.textPrimary,
                        )
                        Text(
                            text = state.current?.entry?.title.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = Space.x2),
                        )
                        IconAction(
                            icon = if (fullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                            label = if (fullscreen) "Leave full screen" else "Full screen",
                            onClick = onToggleFullscreen,
                            shortcut = ShortcutAction.FULLSCREEN,
                            tint = colors.textPrimary,
                        )
                    }
                }
            }
            if (!state.isPlaying && state.waitingOn == null && state.blocked == null) {
                // A large play button in the middle while paused: an obvious touch target.
                Surface(
                    onClick = {
                        connection.togglePlay()
                        touched++
                    },
                    shape = Radius.full,
                    color = colors.accentFill,
                    contentColor = colors.onAccentFill,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(Sizes.touchTarget + Space.x4)
                        .semantics { contentDescription = "Play" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = AppIcons.Play, contentDescription = null, modifier = Modifier.size(Sizes.iconLarge))
                    }
                }
            }
        }
    }
}

/** The video filling the window, with its controls. Escape, F or the button leaves it. */
@Composable
fun FullscreenVideo(onExit: () -> Unit) {
    VideoPlayerBox(fullscreen = true, onToggleFullscreen = onExit, modifier = Modifier.fillMaxSize())
}

/**
 * What the window shows in picture-in-picture: the video and nothing else. On a Googlebook
 * this is the interactive desktop form, and the system draws its own controls from the media
 * session.
 */
@Composable
fun PictureInPictureVideo() {
    val viewModel: VideoViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    VideoSurface(player = player, aspectRatio = state.videoAspectRatio, modifier = Modifier.fillMaxSize())
}

private const val STAGE_WIDTH_FRACTION = 0.6f
