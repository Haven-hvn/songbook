package app.ipfsmedia.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import app.ipfsmedia.core.library.ShortcutAction
import app.ipfsmedia.core.library.formatClock

/**
 * Calls [onWheel] with +1 or -1 for each notch the mouse wheel turns over this element, and
 * keeps the wheel from also scrolling the page behind it.
 */
fun Modifier.onMouseWheel(onWheel: (direction: Int) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.firstOrNull() ?: continue
            val delta = change.scrollDelta.y
            if (delta != 0f) {
                // Wheel up (negative delta) means "more": forward, louder.
                onWheel(if (delta < 0f) 1 else -1)
                change.consume()
            }
        }
    }
}

/**
 * The position in what is playing: elapsed time, a bar that can be dragged or wheeled, and the
 * length. While the bar is being dragged it shows where the drag is, and seeks once on release,
 * so dragging does not send a seek per pixel.
 *
 * @param positionMs read as a lambda, so the bar moves without recomposing its parent.
 */
@Composable
fun SeekBar(
    positionMs: () -> Long,
    durationMs: Long?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showTimes: Boolean = true,
) {
    val colors = appColors
    val duration = durationMs?.takeIf { it > 0 }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val position = positionMs().coerceAtLeast(0L)
    val fraction = dragging ?: if (duration == null) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)
    val shownMs = if (duration != null) (fraction * duration).toLong() else position

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        if (showTimes) {
            Text(text = formatClock(shownMs), style = AppType.clock, color = colors.textSecondary)
        }
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                val target = dragging
                if (target != null && duration != null) currentOnSeek((target * duration).toLong())
                dragging = null
            },
            enabled = enabled && duration != null,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.border,
            ),
            modifier = Modifier
                .weight(1f)
                .onMouseWheel { direction ->
                    if (duration != null) {
                        val target = (positionMs() + direction * Gestures.WHEEL_SEEK_MS).coerceIn(0L, duration)
                        currentOnSeek(target)
                    }
                }
                .semantics {
                    contentDescription = "Position"
                    stateDescription = if (duration != null) {
                        "${formatClock(shownMs)} of ${formatClock(duration)}"
                    } else {
                        formatClock(shownMs)
                    }
                },
        )
        if (showTimes) {
            Text(text = formatClock(duration), style = AppType.clock, color = colors.textSecondary)
        }
    }
}

/** The app's own volume: a mute button and a bar, both of which answer to the wheel. */
@Composable
fun VolumeControl(
    volume: Float,
    onVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
    barWidth: Dp = Space.x16 + Space.x8,
) {
    val colors = appColors
    val currentVolume by rememberUpdatedState(volume)
    val currentOnVolume by rememberUpdatedState(onVolume)
    val wheel: (Int) -> Unit = { direction ->
        currentOnVolume((currentVolume + direction * Gestures.WHEEL_VOLUME_STEP).coerceIn(0f, 1f))
    }
    Row(
        modifier = modifier.onMouseWheel(wheel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = when {
                volume <= 0f -> AppIcons.VolumeOff
                volume < 0.5f -> AppIcons.VolumeDown
                else -> AppIcons.VolumeUp
            },
            label = if (volume <= 0f) "Unmute" else "Mute",
            onClick = onToggleMute,
            shortcut = ShortcutAction.MUTE,
        )
        Slider(
            value = volume.coerceIn(0f, 1f),
            onValueChange = onVolume,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.border,
            ),
            modifier = Modifier
                .width(barWidth)
                .semantics {
                    contentDescription = "Volume"
                    stateDescription = "${(volume * 100).toInt()} percent"
                },
        )
    }
}
