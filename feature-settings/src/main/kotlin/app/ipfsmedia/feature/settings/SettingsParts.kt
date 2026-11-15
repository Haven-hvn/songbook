package app.ipfsmedia.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import app.ipfsmedia.core.design.AppType
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.appColors

/** A setting's name and what it does, with its control after or under it. */
@Composable
fun SettingText(title: String, description: String?, modifier: Modifier = Modifier) {
    val colors = appColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        if (description != null) {
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

/** A setting that is on or off. The whole row is the switch, so it is easy to hit. */
@Composable
fun SettingSwitch(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clip(Radius.md)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        SettingText(title = title, description = description, modifier = Modifier.weight(1f))
        // The row handles the click; the switch only shows the state.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A setting with a few fixed choices, one of which is in force. */
@Composable
fun SettingChoice(
    title: String,
    description: String?,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        SettingText(title = title, description = description)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    label = { Text(text = option, maxLines = 1) },
                )
            }
        }
    }
}

/** A setting where any number of choices can be on at once. */
@Composable
fun SettingMultiChoice(
    title: String,
    description: String?,
    options: List<String>,
    selected: Set<Int>,
    onToggle: (index: Int, on: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        SettingText(title = title, description = description)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            options.forEachIndexed { index, option ->
                val on = index in selected
                FilterChip(
                    selected = on,
                    onClick = { onToggle(index, !on) },
                    label = { Text(text = option, maxLines = 1) },
                )
            }
        }
    }
}

/** A key, or a chord of keys, drawn as a key cap: `Ctrl+Shift+Z`. */
@Composable
fun KeyCap(text: String, modifier: Modifier = Modifier) {
    val colors = appColors
    Text(
        text = text,
        style = AppType.clock,
        color = colors.textPrimary,
        maxLines = 1,
        modifier = modifier
            .clip(Radius.sm)
            .background(colors.inset)
            .padding(horizontal = Space.x2, vertical = Space.x1),
    )
}
