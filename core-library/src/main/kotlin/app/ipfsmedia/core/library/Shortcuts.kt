package app.ipfsmedia.core.library

/**
 * Keyboard shortcuts as data (docs/PLAN.md §6).
 *
 * One table drives three things, so they cannot disagree: what a key press does, what the
 * system's Keyboard Shortcuts Helper lists, and the hints in tooltips ("Queue (Q)"). The table
 * knows nothing about Android or Compose; `:app` translates a key event into a [KeyChord] and
 * back.
 *
 * Conventions follow Android's desktop guidance: Ctrl is the modifier, Escape dismisses
 * temporary things and is never a general Back, and nothing here overrides text editing — see
 * [shortcutFor].
 */
enum class ShortcutKey {
    SPACE, ENTER, ESCAPE, DELETE, MENU,
    LEFT, RIGHT, UP, DOWN,
    A, F, M, N, Q, R, S, V, X, Z,
    F5, F10,
    DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
}

data class KeyChord(
    val key: ShortcutKey,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
) {
    /** `Ctrl+Shift+Z`, as shown in tooltips. */
    val display: String
        get() = buildString {
            if (ctrl) append("Ctrl+")
            if (alt) append("Alt+")
            if (shift) append("Shift+")
            append(
                when (key) {
                    ShortcutKey.SPACE -> "Space"
                    ShortcutKey.ENTER -> "Enter"
                    ShortcutKey.ESCAPE -> "Esc"
                    ShortcutKey.DELETE -> "Delete"
                    ShortcutKey.MENU -> "Menu"
                    ShortcutKey.LEFT -> "Left"
                    ShortcutKey.RIGHT -> "Right"
                    ShortcutKey.UP -> "Up"
                    ShortcutKey.DOWN -> "Down"
                    else -> key.name.removePrefix("DIGIT_")
                },
            )
        }
}

enum class ShortcutGroup(val title: String) {
    PLAYBACK("Playback"),
    NAVIGATION("Navigation"),
    ITEMS("Items and lists"),
    GENERAL("General"),
}

/**
 * Everything a key can do. Actions marked [needsFocusedItem] act on the focused card or row and
 * are handled there; the rest are handled by the app shell.
 */
enum class ShortcutAction(
    val label: String,
    val group: ShortcutGroup,
    val needsFocusedItem: Boolean = false,
) {
    PLAY_PAUSE("Play or pause", ShortcutGroup.PLAYBACK),
    NEXT("Next", ShortcutGroup.PLAYBACK),
    PREVIOUS("Previous", ShortcutGroup.PLAYBACK),
    SEEK_BACK("Back 10 seconds", ShortcutGroup.PLAYBACK),
    SEEK_FORWARD("Forward 10 seconds", ShortcutGroup.PLAYBACK),
    VOLUME_UP("Volume up", ShortcutGroup.PLAYBACK),
    VOLUME_DOWN("Volume down", ShortcutGroup.PLAYBACK),
    MUTE("Mute", ShortcutGroup.PLAYBACK),
    SHUFFLE("Shuffle", ShortcutGroup.PLAYBACK),
    REPEAT("Repeat", ShortcutGroup.PLAYBACK),
    FULLSCREEN("Full screen video", ShortcutGroup.PLAYBACK),

    SEARCH("Search", ShortcutGroup.NAVIGATION),
    TOGGLE_QUEUE("Show or hide the queue", ShortcutGroup.NAVIGATION),
    SECTION_1("Home", ShortcutGroup.NAVIGATION),
    SECTION_2("Collection", ShortcutGroup.NAVIGATION),
    SECTION_3("Playlists", ShortcutGroup.NAVIGATION),
    SECTION_4("History", ShortcutGroup.NAVIGATION),
    SECTION_5("DataDAOs", ShortcutGroup.NAVIGATION),
    SECTION_6("Search page", ShortcutGroup.NAVIGATION),
    SECTION_7("Settings", ShortcutGroup.NAVIGATION),

    OPEN_ITEM("Open", ShortcutGroup.ITEMS, needsFocusedItem = true),
    PLAY_ITEM("Play", ShortcutGroup.ITEMS, needsFocusedItem = true),
    QUEUE_ITEM("Add to queue", ShortcutGroup.ITEMS, needsFocusedItem = true),
    ITEM_ACTIONS("Actions for the item", ShortcutGroup.ITEMS, needsFocusedItem = true),
    CUT_ITEM("Pick up", ShortcutGroup.ITEMS, needsFocusedItem = true),
    PASTE_ITEM("Put down", ShortcutGroup.ITEMS),
    REMOVE_ITEM("Remove from queue or playlist", ShortcutGroup.ITEMS, needsFocusedItem = true),
    MOVE_ROW_UP("Move row up", ShortcutGroup.ITEMS, needsFocusedItem = true),
    MOVE_ROW_DOWN("Move row down", ShortcutGroup.ITEMS, needsFocusedItem = true),
    SELECT_ALL("Select all", ShortcutGroup.ITEMS),

    NEW_PLAYLIST("New playlist", ShortcutGroup.GENERAL),
    REFRESH("Refresh", ShortcutGroup.GENERAL),
    UNDO("Undo", ShortcutGroup.GENERAL),
    REDO("Redo", ShortcutGroup.GENERAL),
    DISMISS("Close or cancel", ShortcutGroup.GENERAL),
    ;

    /** 0-based sidebar position for the section shortcuts, or null. */
    val sectionIndex: Int?
        get() = if (name.startsWith("SECTION_")) name.removePrefix("SECTION_").toInt() - 1 else null
}

data class ShortcutSpec(val action: ShortcutAction, val chord: KeyChord)

/**
 * The shortcut table. Order within a group is the order the Keyboard Shortcuts Helper shows.
 *
 * Space and Q appear twice on purpose. On a focused item, Space plays that item and Q queues it;
 * the item handles the key and it never reaches the shell. Anywhere else, Space plays or pauses
 * and Q shows the queue.
 */
val SHORTCUTS: List<ShortcutSpec> = listOf(
    ShortcutSpec(ShortcutAction.PLAY_PAUSE, KeyChord(ShortcutKey.SPACE)),
    ShortcutSpec(ShortcutAction.NEXT, KeyChord(ShortcutKey.RIGHT, ctrl = true)),
    ShortcutSpec(ShortcutAction.PREVIOUS, KeyChord(ShortcutKey.LEFT, ctrl = true)),
    ShortcutSpec(ShortcutAction.SEEK_BACK, KeyChord(ShortcutKey.LEFT)),
    ShortcutSpec(ShortcutAction.SEEK_FORWARD, KeyChord(ShortcutKey.RIGHT)),
    ShortcutSpec(ShortcutAction.VOLUME_UP, KeyChord(ShortcutKey.UP)),
    ShortcutSpec(ShortcutAction.VOLUME_DOWN, KeyChord(ShortcutKey.DOWN)),
    ShortcutSpec(ShortcutAction.MUTE, KeyChord(ShortcutKey.M)),
    ShortcutSpec(ShortcutAction.SHUFFLE, KeyChord(ShortcutKey.S)),
    ShortcutSpec(ShortcutAction.REPEAT, KeyChord(ShortcutKey.R)),
    ShortcutSpec(ShortcutAction.FULLSCREEN, KeyChord(ShortcutKey.F)),

    ShortcutSpec(ShortcutAction.SEARCH, KeyChord(ShortcutKey.F, ctrl = true)),
    ShortcutSpec(ShortcutAction.TOGGLE_QUEUE, KeyChord(ShortcutKey.Q)),
    ShortcutSpec(ShortcutAction.SECTION_1, KeyChord(ShortcutKey.DIGIT_1, ctrl = true)),
    ShortcutSpec(ShortcutAction.SECTION_2, KeyChord(ShortcutKey.DIGIT_2, ctrl = true)),
    ShortcutSpec(ShortcutAction.SECTION_3, KeyChord(ShortcutKey.DIGIT_3, ctrl = true)),
    ShortcutSpec(ShortcutAction.SECTION_4, KeyChord(ShortcutKey.DIGIT_4, ctrl = true)),
    ShortcutSpec(ShortcutAction.SECTION_5, KeyChord(ShortcutKey.DIGIT_5, ctrl = true)),
    ShortcutSpec(ShortcutAction.SECTION_6, KeyChord(ShortcutKey.DIGIT_6, ctrl = true)),
    ShortcutSpec(ShortcutAction.SECTION_7, KeyChord(ShortcutKey.DIGIT_7, ctrl = true)),

    ShortcutSpec(ShortcutAction.OPEN_ITEM, KeyChord(ShortcutKey.ENTER)),
    ShortcutSpec(ShortcutAction.PLAY_ITEM, KeyChord(ShortcutKey.SPACE)),
    ShortcutSpec(ShortcutAction.QUEUE_ITEM, KeyChord(ShortcutKey.Q)),
    ShortcutSpec(ShortcutAction.ITEM_ACTIONS, KeyChord(ShortcutKey.MENU)),
    ShortcutSpec(ShortcutAction.ITEM_ACTIONS, KeyChord(ShortcutKey.F10, shift = true)),
    ShortcutSpec(ShortcutAction.CUT_ITEM, KeyChord(ShortcutKey.X, ctrl = true)),
    ShortcutSpec(ShortcutAction.PASTE_ITEM, KeyChord(ShortcutKey.V, ctrl = true)),
    ShortcutSpec(ShortcutAction.REMOVE_ITEM, KeyChord(ShortcutKey.DELETE)),
    ShortcutSpec(ShortcutAction.MOVE_ROW_UP, KeyChord(ShortcutKey.UP, alt = true)),
    ShortcutSpec(ShortcutAction.MOVE_ROW_DOWN, KeyChord(ShortcutKey.DOWN, alt = true)),
    ShortcutSpec(ShortcutAction.SELECT_ALL, KeyChord(ShortcutKey.A, ctrl = true)),

    ShortcutSpec(ShortcutAction.NEW_PLAYLIST, KeyChord(ShortcutKey.N, ctrl = true)),
    ShortcutSpec(ShortcutAction.REFRESH, KeyChord(ShortcutKey.R, ctrl = true)),
    ShortcutSpec(ShortcutAction.REFRESH, KeyChord(ShortcutKey.F5)),
    ShortcutSpec(ShortcutAction.UNDO, KeyChord(ShortcutKey.Z, ctrl = true)),
    ShortcutSpec(ShortcutAction.REDO, KeyChord(ShortcutKey.Z, ctrl = true, shift = true)),
    ShortcutSpec(ShortcutAction.DISMISS, KeyChord(ShortcutKey.ESCAPE)),
)

/** Where a key press landed, which decides which of two same-key actions applies. */
enum class ShortcutScope {
    /** The app shell: nothing more specific handled the key. */
    SHELL,

    /** A focused card or row. */
    ITEM,
}

/** Chords that still work while a text field has focus: none of them is a text-editing key. */
private val ALLOWED_WHILE_TYPING: Set<ShortcutAction> = setOf(
    ShortcutAction.SEARCH,
    ShortcutAction.NEW_PLAYLIST,
    ShortcutAction.REFRESH,
    ShortcutAction.DISMISS,
    ShortcutAction.SECTION_1, ShortcutAction.SECTION_2, ShortcutAction.SECTION_3, ShortcutAction.SECTION_4,
    ShortcutAction.SECTION_5, ShortcutAction.SECTION_6, ShortcutAction.SECTION_7,
)

/**
 * The action for [chord] in [scope], or null when the key should be left alone.
 *
 * While [typing] (a text field has focus) only shortcuts that cannot be mistaken for text editing
 * fire: Space types a space, Left moves the caret, Ctrl+Z undoes typing, and so on.
 */
fun shortcutFor(chord: KeyChord, scope: ShortcutScope, typing: Boolean = false): ShortcutAction? {
    val action = SHORTCUTS.firstOrNull { spec ->
        spec.chord == chord && spec.action.needsFocusedItem == (scope == ShortcutScope.ITEM)
    }?.action
        // An item passes on what it does not handle itself; shell-wide actions still apply there.
        ?: SHORTCUTS.firstOrNull { it.chord == chord && !it.action.needsFocusedItem }?.action
            ?.takeIf { scope == ShortcutScope.ITEM }
        ?: return null
    if (typing && action !in ALLOWED_WHILE_TYPING) return null
    return action
}

/** The hint to put after a tooltip's text, e.g. `Q`, or null when the action has no shortcut. */
fun shortcutHint(action: ShortcutAction): String? =
    SHORTCUTS.firstOrNull { it.action == action }?.chord?.display

/** `Queue (Q)`: a tooltip's text with its shortcut, when a keyboard is attached. */
fun withShortcutHint(text: String, action: ShortcutAction, keyboardAttached: Boolean): String {
    val hint = shortcutHint(action)?.takeIf { keyboardAttached } ?: return text
    return "$text ($hint)"
}
