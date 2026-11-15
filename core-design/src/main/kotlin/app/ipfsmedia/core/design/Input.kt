package app.ipfsmedia.core.design

import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import android.content.res.Configuration
import android.view.PointerIcon as PlatformPointerIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.ipfsmedia.core.library.KeyChord
import app.ipfsmedia.core.library.ShortcutKey

/**
 * What the user has to hand, as far as the interface cares.
 *
 * The plan reads this from Compose's `mediaQuery`, which is experimental; this wrapper is the
 * "one small wrapper of our own" it asks for. It reads the same facts from the platform
 * configuration instead, which has reported them since API 1 and updates when a keyboard is
 * attached or folded away. Swapping the source later touches only this file.
 */
@Immutable
data class InputKind(
    /** A hardware keyboard is attached: show shortcut hints. */
    val hasKeyboard: Boolean,
    /** There is a touchscreen: keep rows roomy and never rely on hover. */
    val hasTouch: Boolean,
) {
    /** Touch is the only way in: no pointer to hover with, no keys. */
    val touchOnly: Boolean get() = hasTouch && !hasKeyboard
}

@Composable
fun rememberInputKind(): InputKind {
    val configuration = LocalConfiguration.current
    val keyboard = configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
        configuration.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
    val touch = configuration.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH
    return remember(keyboard, touch) { InputKind(hasKeyboard = keyboard, hasTouch = touch) }
}

/**
 * This key event as a chord the shortcut table understands, or null for a key it does not use.
 * The table (`core-library`) knows nothing about Compose; this is the one translation.
 */
fun KeyEvent.toChord(): KeyChord? {
    val shortcutKey = when (key) {
        Key.Spacebar -> ShortcutKey.SPACE
        Key.Enter, Key.NumPadEnter -> ShortcutKey.ENTER
        Key.Escape -> ShortcutKey.ESCAPE
        Key.Delete, Key.Backspace -> ShortcutKey.DELETE
        Key.Menu -> ShortcutKey.MENU
        Key.DirectionLeft -> ShortcutKey.LEFT
        Key.DirectionRight -> ShortcutKey.RIGHT
        Key.DirectionUp -> ShortcutKey.UP
        Key.DirectionDown -> ShortcutKey.DOWN
        Key.A -> ShortcutKey.A
        Key.F -> ShortcutKey.F
        Key.M -> ShortcutKey.M
        Key.N -> ShortcutKey.N
        Key.Q -> ShortcutKey.Q
        Key.R -> ShortcutKey.R
        Key.S -> ShortcutKey.S
        Key.V -> ShortcutKey.V
        Key.X -> ShortcutKey.X
        Key.Z -> ShortcutKey.Z
        Key.F5 -> ShortcutKey.F5
        Key.F10 -> ShortcutKey.F10
        Key.One -> ShortcutKey.DIGIT_1
        Key.Two -> ShortcutKey.DIGIT_2
        Key.Three -> ShortcutKey.DIGIT_3
        Key.Four -> ShortcutKey.DIGIT_4
        Key.Five -> ShortcutKey.DIGIT_5
        Key.Six -> ShortcutKey.DIGIT_6
        Key.Seven -> ShortcutKey.DIGIT_7
        Key.Eight -> ShortcutKey.DIGIT_8
        Key.Nine -> ShortcutKey.DIGIT_9
        else -> return null
    }
    return KeyChord(key = shortcutKey, ctrl = isCtrlPressed, shift = isShiftPressed, alt = isAltPressed)
}

/**
 * The pointer says what will happen (docs/PLAN.md §6): an open hand over anything that can be
 * picked up, a closed hand while carrying, a double arrow over the pane dividers.
 */
enum class Cursor(internal val platformType: Int) {
    Hand(PlatformPointerIcon.TYPE_HAND),
    Grab(PlatformPointerIcon.TYPE_GRAB),
    Grabbing(PlatformPointerIcon.TYPE_GRABBING),
    ResizeHorizontal(PlatformPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW),
    Text(PlatformPointerIcon.TYPE_TEXT),
}

@Composable
fun Modifier.cursor(cursor: Cursor?): Modifier {
    if (cursor == null) return this
    val context = LocalContext.current
    val icon = remember(cursor, context) {
        PointerIcon(PlatformPointerIcon.getSystemIcon(context, cursor.platformType))
    }
    return pointerHoverIcon(icon)
}

/**
 * Copies text to the clipboard: an address, a pairing link, a crash note. Returns the function
 * to call, so a click handler can use it without a coroutine of its own.
 */
@Composable
fun rememberCopyText(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { text: String ->
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text))) }
            Unit
        }
    }
}
