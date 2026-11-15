package app.ipfsmedia.player.shell

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clipToBounds
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import app.ipfsmedia.core.design.ArtTransitions
import app.ipfsmedia.core.design.DragController
import app.ipfsmedia.core.design.DragHost
import app.ipfsmedia.core.design.Gestures
import app.ipfsmedia.core.design.LocalArtTransitions
import app.ipfsmedia.core.design.LocalPageTransitionScope
import app.ipfsmedia.core.design.LocalSearchFocusRequests
import app.ipfsmedia.core.design.LocalWidthClass
import app.ipfsmedia.core.design.PaneDivider
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.WidthClass
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.library.ShortcutAction
import app.ipfsmedia.core.navigation.EntryProviderInstaller
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.NavigationState
import app.ipfsmedia.core.navigation.NowPlayingRoute
import app.ipfsmedia.core.navigation.SECTION_ROUTES
import app.ipfsmedia.core.navigation.SearchRoute
import app.ipfsmedia.core.navigation.rememberNavigationState
import app.ipfsmedia.core.userdata.PrefKeys
import app.ipfsmedia.feature.video.FullscreenVideo
import app.ipfsmedia.feature.video.LocalRequestFullscreen
import app.ipfsmedia.player.WindowSignals
import app.ipfsmedia.player.actions.ShellEvent
import app.ipfsmedia.player.actions.ShellViewModel
import kotlinx.coroutines.launch

/**
 * The shell: sidebar (or rail, or bottom bar), the content area, the queue, and the player bar,
 * arranged by the window's width, with the drag layer over all of it.
 *
 * | Window width | Navigation | Queue |
 * |---|---|---|
 * | under 600dp | bottom bar; the sidebar slides over from its button | a full-height sheet |
 * | 600 to 839dp | rail | an overlay |
 * | 840 to 1199dp | sidebar, resizable | an overlay |
 * | 1200dp and over | sidebar, resizable | docked beside the content, resizable |
 *
 * The queue is a pane of the shell, not a navigation destination, so opening it never changes
 * the history. Nothing opens from the window's edges: those belong to the system's back gesture.
 */
@Composable
fun MainShell(shell: ShellViewModel, installers: Set<EntryProviderInstaller>, signals: WindowSignals) {
    val navigation = rememberNavigationState()
    val drag = remember { DragController() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val prefs by shell.prefs.collectAsStateWithLifecycle()
    val playerState by shell.playerState.collectAsStateWithLifecycle()

    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var resizingSidebar by remember { mutableStateOf(false) }
    var searchFocusRequests by rememberSaveable { mutableIntStateOf(0) }
    var mutedFrom by rememberSaveable { mutableFloatStateOf(1f) }

    // Pane widths are remembered per identity. The live value follows the drag; the saved one
    // is written when the drag ends.
    var sidebarWidth by remember(prefs[PrefKeys.SIDEBAR_WIDTH_DP]) {
        mutableStateOf(prefs[PrefKeys.SIDEBAR_WIDTH_DP]?.toFloatOrNull() ?: Sizes.sidebarWidth.value)
    }
    var queueWidth by remember(prefs[PrefKeys.QUEUE_WIDTH_DP]) {
        mutableStateOf(prefs[PrefKeys.QUEUE_WIDTH_DP]?.toFloatOrNull() ?: Sizes.queueWidth.value)
    }

    LaunchedEffect(Unit) { shell.refreshOnOpen() }

    // The media notification was tapped: show what is playing.
    LaunchedEffect(signals.openNowPlayingRequests) {
        if (signals.openNowPlayingRequests > 0) navigation.open(NowPlayingRoute)
    }

    LaunchedEffect(shell, navigation) {
        shell.events.collect { event ->
            when (event) {
                is ShellEvent.Navigate -> {
                    navigation.open(event.route, event.inHomeSection)
                    menuOpen = false
                }
                is ShellEvent.Notice -> scope.launch {
                    // A newer notice replaces the one on screen rather than queueing behind it.
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(
                        message = event.text,
                        actionLabel = if (event.undo) "Undo" else null,
                        duration = if (event.undo) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) shell.undo()
                }
                // Only when that page is the one showing: a playlist can be deleted from the
                // sidebar or the playlists page while something else is open.
                is ShellEvent.LeavePage -> if (navigation.current == event.route) navigation.back()
                ShellEvent.FocusSearch -> {
                    navigation.open(SearchRoute)
                    searchFocusRequests++
                }
            }
        }
    }

    // Leaving a video, or the queue running out, ends full screen.
    LaunchedEffect(playerState.isVideo) { if (!playerState.isVideo) fullscreen = false }
    LaunchedEffect(fullscreen) { signals.setFullscreen(fullscreen) }

    NotificationPermission(wantsPlayback = playerState.playWhenReady)

    // Keys nothing in the composition took arrive here from the activity. The handler reads
    // the state above when it is called, so it always acts on current values.
    SideEffect {
        signals.onShortcut = { action ->
            handleShortcut(
                action = action,
                shell = shell,
                navigation = navigation,
                drag = drag,
                isVideo = playerState.isVideo,
                volume = playerState.volume,
                mutedFrom = mutedFrom,
                onMutedFrom = { mutedFrom = it },
                queueOpen = queueOpen,
                onQueueOpen = { queueOpen = it },
                menuOpen = menuOpen,
                onMenuOpen = { menuOpen = it },
                fullscreen = fullscreen,
                onFullscreen = { fullscreen = it },
            )
        }
    }
    DisposableEffect(signals) {
        onDispose { signals.onShortcut = null }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = WidthClass.of(maxWidth)
        val rail = width == WidthClass.Medium
        val animatedSidebar by animateDpAsState(
            targetValue = when {
                width.isCompact -> Space.none
                rail -> Sizes.railWidth
                else -> Dp(sidebarWidth)
            },
            // While the divider is being dragged the sidebar follows the pointer exactly.
            animationSpec = if (resizingSidebar) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "sidebarWidth",
        )
        val barHeight = if (width.isCompact) Sizes.playerBarHeightCompact else Sizes.playerBarHeight
        val trayInset: Dp = if (width.isCompact) barHeight + Sizes.rowHeightTouch else barHeight

        CompositionLocalProvider(
            LocalWidthClass provides width,
            LocalNavigator provides navigation,
            LocalSearchFocusRequests provides searchFocusRequests,
            LocalRequestFullscreen provides { fullscreen = true },
        ) {
            if (fullscreen && playerState.isVideo) {
                FullscreenVideo(onExit = { fullscreen = false })
            } else {
            DragHost(controller = drag, trayBottomInset = trayInset) {
              Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        // One sidebar across window sizes: its width animates between rail and
                        // full, and to nothing below 600dp, instead of snapping (docs/PLAN.md §7).
                        if (animatedSidebar > Space.none) {
                            Sidebar(
                                shell = shell,
                                rail = rail,
                                queueOpen = queueOpen,
                                onToggleQueue = { queueOpen = !queueOpen },
                                modifier = Modifier
                                    .width(animatedSidebar)
                                    .fillMaxHeight()
                                    .clipToBounds(),
                            )
                            if (!rail && !width.isCompact) {
                                PaneDivider(
                                    label = "Resize the sidebar",
                                    onDrag = { delta ->
                                        resizingSidebar = true
                                        sidebarWidth = (sidebarWidth + delta.value)
                                            .coerceIn(Sizes.sidebarMinWidth.value, Sizes.sidebarMaxWidth.value)
                                    },
                                    onDragEnd = {
                                        resizingSidebar = false
                                        shell.setPref(PrefKeys.SIDEBAR_WIDTH_DP, sidebarWidth.toString())
                                    },
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            ContentArea(navigation = navigation, installers = installers)
                        }
                        // Docked beside the content at 1200dp and over; it slides in and out.
                        AnimatedVisibility(
                            visible = width == WidthClass.Large && queueOpen,
                            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                        ) {
                            Row(modifier = Modifier.fillMaxHeight()) {
                                PaneDivider(
                                    label = "Resize the queue",
                                    // The queue is at the end of the row: dragging towards the start widens it.
                                    onDrag = { delta ->
                                        queueWidth = (queueWidth - delta.value)
                                            .coerceIn(Sizes.queueMinWidth.value, Sizes.queueMaxWidth.value)
                                    },
                                    onDragEnd = { shell.setPref(PrefKeys.QUEUE_WIDTH_DP, queueWidth.toString()) },
                                )
                                QueuePane(
                                    shell = shell,
                                    onClose = { queueOpen = false },
                                    modifier = Modifier.width(Dp(queueWidth)).fillMaxHeight(),
                                )
                            }
                        }
                    }
                    PlayerBar(
                        shell = shell,
                        queueOpen = queueOpen,
                        onToggleQueue = { queueOpen = !queueOpen },
                        onOpenQueue = { queueOpen = true },
                        onFullscreen = { fullscreen = true },
                    )
                    if (width.isCompact) {
                        BottomBar(onMenu = { menuOpen = true })
                    }
                }

                // Below the docked width the queue lies over the content, and under 600dp it
                // takes the whole window.
                Overlay(
                    visible = queueOpen && width != WidthClass.Large,
                    fromEnd = true,
                    onDismiss = { queueOpen = false },
                    bottomInset = if (width.isCompact) Space.none else barHeight,
                ) {
                    QueuePane(
                        shell = shell,
                        onClose = { queueOpen = false },
                        modifier = if (width.isCompact) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.widthIn(max = Sizes.queueWidth).fillMaxHeight()
                        },
                    )
                }

                // Under 600dp the sidebar comes over the content from its button in the bottom bar.
                Overlay(
                    visible = menuOpen && width.isCompact,
                    fromEnd = false,
                    onDismiss = { menuOpen = false },
                    bottomInset = Space.none,
                ) {
                    Sidebar(
                        shell = shell,
                        rail = false,
                        queueOpen = queueOpen,
                        onToggleQueue = {
                            menuOpen = false
                            queueOpen = !queueOpen
                        },
                        onNavigated = { menuOpen = false },
                        modifier = Modifier.widthIn(max = Sizes.sidebarMaxWidth).fillMaxHeight(),
                    )
                }

                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = trayInset + Space.x2)
                        .widthIn(max = Sizes.readingWidth),
                )
              }
            }
            }
            ShellDialogs(shell)
        }
    }
}

/** The content area: the history of the selected section, each screen supplied by its module. */
@Composable
private fun ContentArea(navigation: NavigationState, installers: Set<EntryProviderInstaller>) {
    val provider = remember(installers) {
        entryProvider<NavKey> { installers.forEach { install -> install() } }
    }
    // Each page is told which transition it is part of, so a card's picture can travel to the
    // hero of the page it opens (core-design's Transitions.kt).
    val pageDecorators = remember {
        listOf(
            NavEntryDecorator<NavKey>(
                decorate = { entry ->
                    CompositionLocalProvider(LocalPageTransitionScope provides LocalNavAnimatedContentScope.current) {
                        entry.Content()
                    }
                },
            ),
        )
    }
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        val transitions = remember(this) { ArtTransitions(this) }
        CompositionLocalProvider(LocalArtTransitions provides transitions) {
            NavDisplay(
                entries = navigation.decoratedEntries(pageDecorators = pageDecorators, entryProvider = provider),
                sharedTransitionScope = this,
                onBack = { navigation.back() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A pane laid over the content: a scrim that closes it when tapped, and the pane sliding in
 * from its side. Keeps clear of the player bar when [bottomInset] says so.
 */
@Composable
private fun Overlay(
    visible: Boolean,
    fromEnd: Boolean,
    onDismiss: () -> Unit,
    bottomInset: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomInset),
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appColors.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close",
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { if (fromEnd) it else -it },
            exit = slideOutHorizontally { if (fromEnd) it else -it },
            modifier = Modifier.align(if (fromEnd) Alignment.CenterEnd else Alignment.CenterStart),
        ) {
            content()
        }
    }
}

/**
 * Asks for the notification permission the first time something is played, never at launch.
 * Playback and the system's media controls work without it; only the notification needs it.
 */
@Composable
private fun NotificationPermission(wantsPlayback: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(wantsPlayback) {
        if (!wantsPlayback || asked) return@LaunchedEffect
        asked = true
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Shortcuts for the whole window (the shortcut table is `core-library`'s `SHORTCUTS`). Returns
 * false for anything it does not act on, so the key carries on to the system.
 */
private fun handleShortcut(
    action: ShortcutAction,
    shell: ShellViewModel,
    navigation: NavigationState,
    drag: DragController,
    isVideo: Boolean,
    volume: Float,
    mutedFrom: Float,
    onMutedFrom: (Float) -> Unit,
    queueOpen: Boolean,
    onQueueOpen: (Boolean) -> Unit,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    fullscreen: Boolean,
    onFullscreen: (Boolean) -> Unit,
): Boolean {
    val player = shell.player
    val section = action.sectionIndex
    if (section != null) {
        val route = SECTION_ROUTES.getOrNull(section) ?: return false
        navigation.open(route)
        if (route == SearchRoute) shell.focusSearch()
        return true
    }
    when (action) {
        ShortcutAction.PLAY_PAUSE -> player.togglePlay()
        ShortcutAction.NEXT -> player.next()
        ShortcutAction.PREVIOUS -> player.previous()
        ShortcutAction.SEEK_BACK -> player.seekBy(-Gestures.KEY_SEEK_MS)
        ShortcutAction.SEEK_FORWARD -> player.seekBy(Gestures.KEY_SEEK_MS)
        ShortcutAction.VOLUME_UP -> player.changeVolume(Gestures.KEY_VOLUME_STEP)
        ShortcutAction.VOLUME_DOWN -> player.changeVolume(-Gestures.KEY_VOLUME_STEP)
        ShortcutAction.MUTE -> toggleMute(shell, volume, mutedFrom, onMutedFrom)
        ShortcutAction.SHUFFLE -> player.toggleShuffle()
        ShortcutAction.REPEAT -> player.cycleRepeat()
        ShortcutAction.FULLSCREEN -> if (isVideo) onFullscreen(!fullscreen) else return false
        ShortcutAction.SEARCH -> shell.focusSearch()
        ShortcutAction.TOGGLE_QUEUE -> onQueueOpen(!queueOpen)
        ShortcutAction.NEW_PLAYLIST -> shell.newPlaylist()
        ShortcutAction.REFRESH -> shell.refreshNow()
        ShortcutAction.UNDO -> shell.undo()
        ShortcutAction.REDO -> shell.redo()
        // Escape closes temporary things, innermost first. It is never a general Back.
        ShortcutAction.DISMISS -> when {
            drag.isActive -> drag.cancel()
            shell.dialog != null -> shell.dismissDialog()
            fullscreen -> onFullscreen(false)
            menuOpen -> onMenuOpen(false)
            queueOpen -> onQueueOpen(false)
            else -> return false
        }
        else -> return false
    }
    return true
}

internal fun toggleMute(shell: ShellViewModel, volume: Float, mutedFrom: Float, onMutedFrom: (Float) -> Unit) {
    if (volume > 0f) {
        onMutedFrom(volume)
        shell.player.setVolume(0f)
    } else {
        shell.player.setVolume(mutedFrom.takeIf { it > 0f } ?: 1f)
    }
}
