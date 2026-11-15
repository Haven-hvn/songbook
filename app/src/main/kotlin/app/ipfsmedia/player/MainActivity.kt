package app.ipfsmedia.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.ipfsmedia.core.design.toChord
import app.ipfsmedia.core.library.SHORTCUTS
import app.ipfsmedia.core.library.ShortcutAction
import app.ipfsmedia.core.library.ShortcutGroup
import app.ipfsmedia.core.library.ShortcutKey
import app.ipfsmedia.core.library.ShortcutScope
import app.ipfsmedia.core.library.shortcutFor
import app.ipfsmedia.core.navigation.EntryProviderInstaller
import app.ipfsmedia.core.player.PlaybackService
import app.ipfsmedia.core.player.PlayerConnection
import app.ipfsmedia.core.userdata.PrefKeys
import app.ipfsmedia.core.userdata.UserDataRepository
import app.ipfsmedia.player.shell.AppRoot
import com.reown.appkit.client.AppKit
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** What the window tells the composition, and what the composition asks of the window. */
@Stable
class WindowSignals {
    /** The window is in picture-in-picture: show the video and nothing else. */
    var inPictureInPicture: Boolean by mutableStateOf(false)

    /** Bumped when the media notification was tapped: show what is playing. */
    var openNowPlayingRequests: Int by mutableIntStateOf(0)

    /** A crash note left by the previous run, shown once. */
    var crashNote: String? by mutableStateOf(null)

    /** Set by the shell: handles a shortcut nothing more specific took. Returns whether it did. */
    var onShortcut: ((ShortcutAction) -> Boolean)? = null

    /** Set by the activity: enter or leave full screen for video. */
    var setFullscreen: (Boolean) -> Unit = {}
}

/**
 * The app's one window.
 *
 * A resize recreates it (the manifest does not suppress that), and everything the user would
 * miss is saved state. Playback is not here: the activity only binds to the playback service
 * while it is visible.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var installers: Set<@JvmSuppressWildcards EntryProviderInstaller>

    @Inject
    lateinit var player: PlayerConnection

    @Inject
    lateinit var userData: UserDataRepository

    private val signals = WindowSignals()

    /** A video is playing and the user wants it to follow them out of the window. */
    private var pipWanted = false
    private var pipAspect: Rational? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge is mandatory at this target level; the shell keeps clear of the system
        // bars and of the caption bar a desktop window has.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        registerWithWallet()
        handleIntent(intent)
        if (savedInstanceState == null) signals.crashNote = CrashNote.read(this)
        signals.setFullscreen = ::setFullscreen
        signals.inPictureInPicture = isInPictureInPictureMode

        setContent {
            AppRoot(installers = installers, signals = signals)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(player.state, userData.pref(PrefKeys.VIDEO_PIP)) { state, pref ->
                    val wanted = pref != "false" && state.isVideo && state.isPlaying
                    wanted to state.videoAspectRatio
                }.collect { (wanted, aspect) -> updatePictureInPicture(wanted, aspect) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player.connect()
    }

    override fun onStop() {
        // Media3 can only stop the service once every controller has let go. Background
        // playback does not need the window's.
        player.disconnect()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        runCatching { AppKit.unregister() }
        super.onDestroy()
    }

    // ── Wallet return link ──────────────────────────────────────────────────────────────────

    /**
     * AppKit needs the activity for the wallet hand-off and its return. It throws when it has
     * not been initialised (no `wallet.projectId`), which onboarding explains, so that is
     * logged rather than fatal.
     */
    private fun registerWithWallet() {
        runCatching { AppKit.register(this) }
            .onFailure { Timber.w(it, "AppKit.register skipped: the wallet is not configured") }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(PlaybackService.EXTRA_OPEN_NOW_PLAYING, false)) {
            signals.openNowPlayingRequests++
        }
        // The only link this activity accepts is the wallet's return leg, and it is passed on
        // only when it is a WalletConnect envelope. Anything else is ignored: the link carries
        // no command and nothing in it is trusted.
        val data = intent.data ?: return
        if (data.scheme != WALLET_RETURN_SCHEME || data.host != WALLET_RETURN_HOST) return
        val link = data.toString()
        if (!link.contains("wc_ev") && !link.contains("wc:")) return
        runCatching {
            AppKit.handleDeepLink(link) { error ->
                Timber.w(error.throwable, "the wallet's return link was not accepted")
            }
        }.onFailure { Timber.w(it, "the wallet's return link was ignored: AppKit is not initialised") }
    }

    // ── Keyboard ────────────────────────────────────────────────────────────────────────────

    /**
     * Shortcuts for the whole window. This is only reached by keys nothing in the composition
     * took: a focused text field keeps its typing keys, and a focused card or row handles its
     * own (Space plays that item rather than toggling playback).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val chord = androidx.compose.ui.input.key.KeyEvent(event).toChord()
        val action = chord?.let { shortcutFor(it, ShortcutScope.SHELL) }
        if (action != null && signals.onShortcut?.invoke(action) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    /** Publishes every shortcut to the system's Keyboard Shortcuts Helper, from the one table. */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        ShortcutGroup.entries.forEach { group ->
            val infos = SHORTCUTS.filter { it.action.group == group }.mapNotNull { spec ->
                val keyCode = keyCodeOf(spec.chord.key) ?: return@mapNotNull null
                var modifiers = 0
                if (spec.chord.ctrl) modifiers = modifiers or KeyEvent.META_CTRL_ON
                if (spec.chord.shift) modifiers = modifiers or KeyEvent.META_SHIFT_ON
                if (spec.chord.alt) modifiers = modifiers or KeyEvent.META_ALT_ON
                KeyboardShortcutInfo(spec.action.label, keyCode, modifiers)
            }
            if (infos.isNotEmpty()) data += KeyboardShortcutGroup(group.title, infos)
        }
    }

    private fun keyCodeOf(key: ShortcutKey): Int? = when (key) {
        ShortcutKey.SPACE -> KeyEvent.KEYCODE_SPACE
        ShortcutKey.ENTER -> KeyEvent.KEYCODE_ENTER
        ShortcutKey.ESCAPE -> KeyEvent.KEYCODE_ESCAPE
        ShortcutKey.DELETE -> KeyEvent.KEYCODE_FORWARD_DEL
        ShortcutKey.MENU -> KeyEvent.KEYCODE_MENU
        ShortcutKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        ShortcutKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        ShortcutKey.UP -> KeyEvent.KEYCODE_DPAD_UP
        ShortcutKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        ShortcutKey.A -> KeyEvent.KEYCODE_A
        ShortcutKey.F -> KeyEvent.KEYCODE_F
        ShortcutKey.M -> KeyEvent.KEYCODE_M
        ShortcutKey.N -> KeyEvent.KEYCODE_N
        ShortcutKey.Q -> KeyEvent.KEYCODE_Q
        ShortcutKey.R -> KeyEvent.KEYCODE_R
        ShortcutKey.S -> KeyEvent.KEYCODE_S
        ShortcutKey.V -> KeyEvent.KEYCODE_V
        ShortcutKey.X -> KeyEvent.KEYCODE_X
        ShortcutKey.Z -> KeyEvent.KEYCODE_Z
        ShortcutKey.F5 -> KeyEvent.KEYCODE_F5
        ShortcutKey.F10 -> KeyEvent.KEYCODE_F10
        ShortcutKey.DIGIT_1 -> KeyEvent.KEYCODE_1
        ShortcutKey.DIGIT_2 -> KeyEvent.KEYCODE_2
        ShortcutKey.DIGIT_3 -> KeyEvent.KEYCODE_3
        ShortcutKey.DIGIT_4 -> KeyEvent.KEYCODE_4
        ShortcutKey.DIGIT_5 -> KeyEvent.KEYCODE_5
        ShortcutKey.DIGIT_6 -> KeyEvent.KEYCODE_6
        ShortcutKey.DIGIT_7 -> KeyEvent.KEYCODE_7
        ShortcutKey.DIGIT_8 -> KeyEvent.KEYCODE_8
        ShortcutKey.DIGIT_9 -> KeyEvent.KEYCODE_9
    }

    // ── Picture-in-picture and full screen ──────────────────────────────────────────────────

    /**
     * Keeps the window's picture-in-picture settings in step with playback. On API 31 and later
     * the system enters it by itself when the user leaves a playing video; on a Googlebook
     * that is the interactive desktop form, an always-on-top window whose controls work.
     */
    private fun updatePictureInPicture(wanted: Boolean, aspect: Float?) {
        pipWanted = wanted
        pipAspect = aspect?.takeIf { it in MIN_PIP_ASPECT..MAX_PIP_ASPECT }?.let { Rational((it * 1000).toInt(), 1000) }
        runCatching { setPictureInPictureParams(pictureInPictureParams()) }
    }

    private fun pictureInPictureParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        pipAspect?.let(builder::setAspectRatio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(pipWanted)
        return builder.build()
    }

    /** Before API 31 there is no automatic entry, so it is asked for as the user leaves. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipWanted && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            runCatching { enterPictureInPictureMode(pictureInPictureParams()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        signals.inPictureInPicture = isInPictureInPictureMode
    }

    /** Full screen for video: the system bars go, and come back with a swipe. */
    private fun setFullscreen(enabled: Boolean) {
        runCatching {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (enabled) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private companion object {
        const val WALLET_RETURN_SCHEME = "ipfsmedia"
        const val WALLET_RETURN_HOST = "connect"
        const val MIN_PIP_ASPECT = 0.42f
        const val MAX_PIP_ASPECT = 2.38f
    }
}
