package app.ipfsmedia.feature.identity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AccentButton
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.BusyIndicator
import app.ipfsmedia.core.design.GroupLabel
import app.ipfsmedia.core.design.HexAvatar
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.InlineNotice
import app.ipfsmedia.core.design.SidebarItem
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.StatusPill
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.rememberCopyText
import haven.mobile.core.wallet.WalletIdentity

/**
 * The identity entry at the foot of the sidebar: who is active, and whether that wallet can
 * sign right now. Opens the identity sheet.
 */
@Composable
fun IdentityButton(rail: Boolean, modifier: Modifier = Modifier) {
    val viewModel: IdentityViewModel = hiltViewModel()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val live by viewModel.isLive.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    val identity = active ?: return

    SidebarItem(
        label = identity.displayLabel,
        supporting = if (live) "Wallet connected" else "Reconnect to sign",
        leading = { HexAvatar(address = identity.id, name = identity.displayLabel) },
        selected = false,
        rail = rail,
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) IdentitySheet(viewModel = viewModel, onDismiss = { open = false })
}

@Composable
private fun IdentitySheet(viewModel: IdentityViewModel, onDismiss: () -> Unit) {
    val identities by viewModel.identities.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val live by viewModel.isLive.collectAsStateWithLifecycle()
    val colors = appColors
    val copy = rememberCopyText()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val current = active

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.x8),
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            Text(
                text = "Identity",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )

            if (current != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                ) {
                    HexAvatar(address = current.id, name = current.displayLabel, size = Sizes.touchTarget)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = current.displayLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = current.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    IconAction(icon = AppIcons.Edit, label = "Rename", onClick = { renaming = true })
                    IconAction(
                        icon = AppIcons.Copy,
                        label = "Copy address",
                        onClick = { copy(current.address) },
                    )
                }
                StatusPill(
                    text = if (live) "Wallet connected: can sign" else "Stored on this device: reconnect to sign",
                    icon = if (live) AppIcons.Check else AppIcons.Key,
                    foreground = if (live) colors.success else colors.warning,
                    ground = if (live) colors.successSurface else colors.warningSurface,
                )
                Text(
                    text = "Browsing and playing what is already on this device work without the wallet. " +
                        "Opening something from a DataDAO for the first time needs it, to sign once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            viewModel.busy?.let { label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    BusyIndicator(label = label)
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            viewModel.message?.let { text ->
                InlineNotice(text = text, actionLabel = "OK", onAction = viewModel::dismissMessage)
            }

            if (current != null && !live) {
                AccentButton(
                    text = "Reconnect wallet",
                    icon = AppIcons.Wallet,
                    onClick = viewModel::reconnect,
                    enabled = viewModel.busy == null,
                )
            }

            val others = identities.filter { it.id != current?.id }
            if (others.isNotEmpty()) {
                HorizontalDivider()
                GroupLabel(text = "Switch to")
                others.forEach { identity -> IdentityRow(identity = identity, onClick = { viewModel.switchTo(identity.address) }) }
                Text(
                    text = "Switching clears nothing. Each identity keeps its own downloads, playlists and history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            HorizontalDivider()
            OutlinedButton(onClick = viewModel::addWallet, enabled = viewModel.busy == null) {
                Text("Add another wallet")
            }
            if (current != null) {
                OutlinedButton(onClick = { viewModel.disconnect(current.address) }, enabled = viewModel.busy == null) {
                    Text("Disconnect wallet")
                }
                Text(
                    text = "Disconnecting removes keys, unlocked files, the queue and cover art taken from files. " +
                        "Playlists, history and follows stay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                TextButton(onClick = { confirmRemove = true }, enabled = viewModel.busy == null) {
                    Text(text = "Remove this identity from the device…", color = colors.error)
                }
            }
        }
    }

    if (renaming && current != null) {
        RenameDialog(
            initial = current.label,
            onConfirm = { label ->
                viewModel.rename(current.address, label)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmRemove && current != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${current.displayLabel}?") },
            text = {
                Text(
                    "This deletes everything this identity has on the device: downloads, playlists, " +
                        "favourites, history, follows and settings. It cannot be undone. " +
                        "Nothing changes for your wallet or your DataDAO memberships.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        viewModel.remove(current.address)
                    },
                ) { Text(text = "Remove", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IdentityRow(identity: WalletIdentity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(identity.displayLabel) },
        supportingContent = { Text(WalletIdentity.shortAddress(identity.address)) },
        leadingContent = { HexAvatar(address = identity.id, name = identity.displayLabel) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClickLabel = "Switch to ${identity.displayLabel}", onClick = onClick),
    )
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var label by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename identity") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(MAX_LABEL_LENGTH) },
                label = { Text("Name") },
                supportingText = { Text("Only shown on this device.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(label) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(label) }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MAX_LABEL_LENGTH = 40
