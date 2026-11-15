package app.ipfsmedia.player.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AppTheme
import app.ipfsmedia.core.design.ArtworkSource
import app.ipfsmedia.core.design.BusyIndicator
import app.ipfsmedia.core.design.LocalArtworkSource
import app.ipfsmedia.core.design.LocalInputKind
import app.ipfsmedia.core.design.LocalItemActions
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.LocalNowPlaying
import app.ipfsmedia.core.design.LocalStages
import app.ipfsmedia.core.design.NowPlaying
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.rememberInputKind
import app.ipfsmedia.core.design.rememberCopyText
import app.ipfsmedia.core.navigation.EntryProviderInstaller
import app.ipfsmedia.feature.onboarding.OnboardingScreen
import app.ipfsmedia.feature.video.PictureInPictureVideo
import app.ipfsmedia.player.CrashNote
import app.ipfsmedia.player.WindowSignals
import app.ipfsmedia.player.actions.Gate
import app.ipfsmedia.player.actions.ShellViewModel

/**
 * The root of the interface: the theme, what every screen reads from the composition (the
 * catalogue, the item actions, cover art, what is being fetched, what is playing), and the
 * one decision the app makes before anything else. It is wallet-first: with no identity there
 * is nothing but onboarding.
 */
@Composable
fun AppRoot(installers: Set<EntryProviderInstaller>, signals: WindowSignals) {
    val shell: ShellViewModel = hiltViewModel()
    val theme by shell.theme.collectAsStateWithLifecycle()
    val gate by shell.gate.collectAsStateWithLifecycle()
    val library by shell.library.collectAsStateWithLifecycle()
    val stages by shell.stages.collectAsStateWithLifecycle()
    val playerState by shell.playerState.collectAsStateWithLifecycle()
    val artworkRevision by shell.artworkRevision.collectAsStateWithLifecycle()

    val input = rememberInputKind()
    // A new source whenever a picture is extracted, so the cards that read it look again.
    val artworkSource = remember(artworkRevision, library.walletAddress) { ArtworkSource { shell.artworkFor(it) } }
    val current = playerState.current?.entry
    val nowPlaying = remember(current?.recordKey, current?.chapterIndex, playerState.isPlaying) {
        NowPlaying(
            recordKey = current?.recordKey,
            chapterIndex = current?.chapterIndex ?: Int.MIN_VALUE,
            isPlaying = playerState.isPlaying,
        )
    }

    AppTheme(mode = theme) {
        CompositionLocalProvider(
            LocalInputKind provides input,
            LocalItemActions provides shell,
            LocalArtworkSource provides artworkSource,
            LocalStages provides stages,
            LocalNowPlaying provides nowPlaying,
            LocalLibraryState provides library,
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = appColors.ground, contentColor = appColors.textPrimary) {
                when {
                    // In picture-in-picture the window is the video. Edge to edge: no bars to avoid.
                    signals.inPictureInPicture && gate == Gate.Open -> PictureInPictureVideo()
                    else -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Clear of the status and navigation bars, the display cutout, the
                            // keyboard, and the caption bar a desktop window has.
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        when (gate) {
                            Gate.Loading -> BusyIndicator(modifier = Modifier.align(Alignment.Center), label = "Starting")
                            Gate.NeedsWallet -> OnboardingScreen()
                            Gate.Open -> MainShell(shell = shell, installers = installers, signals = signals)
                        }
                        CrashNoteDialog(signals)
                    }
                }
            }
        }
    }
}

/** Shown once after a run that ended in a crash: what happened, and a way to copy it. */
@Composable
private fun CrashNoteDialog(signals: WindowSignals) {
    val note = signals.crashNote ?: return
    val context = LocalContext.current
    val copy = rememberCopyText()
    val dismiss = {
        CrashNote.clear(context)
        signals.crashNote = null
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("The app closed unexpectedly last time") },
        text = {
            Text(
                "The details below stay on this device. Copy them if you want to report the problem.\n\n" +
                    note.take(CRASH_PREVIEW_CHARS),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    copy(note)
                    dismiss()
                },
            ) { Text("Copy and close") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Close") } },
    )
}

private const val CRASH_PREVIEW_CHARS = 600
