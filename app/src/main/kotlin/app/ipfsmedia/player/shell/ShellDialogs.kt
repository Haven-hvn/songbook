package app.ipfsmedia.player.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.userdata.UserDataRepository
import app.ipfsmedia.player.actions.ShellDialog
import app.ipfsmedia.player.actions.ShellViewModel

/** The questions an action can ask before it finishes: a name for a new playlist, a new name. */
@Composable
fun ShellDialogs(shell: ShellViewModel) {
    when (val dialog = shell.dialog) {
        null -> Unit
        is ShellDialog.NamePlaylist -> NameDialog(
            title = "New playlist",
            detail = if (dialog.entries.isEmpty()) null else "With ${countOf(dialog.entries.size, "entry", "entries")}.",
            initial = dialog.suggested,
            confirmLabel = "Create",
            onConfirm = { name -> shell.confirmNewPlaylist(name, dialog.entries) },
            onDismiss = shell::dismissDialog,
        )
        is ShellDialog.RenamePlaylist -> NameDialog(
            title = "Rename playlist",
            detail = null,
            initial = dialog.current,
            confirmLabel = "Rename",
            onConfirm = { name -> shell.confirmRenamePlaylist(dialog.id, name) },
            onDismiss = shell::dismissDialog,
        )
    }
}

/** A dialog that returns a name. Enter confirms, Escape cancels, and the field has the focus. */
@Composable
private fun NameDialog(
    title: String,
    detail: String?,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    label = { Text("Name") },
                    placeholder = { Text(UserDataRepository.DEFAULT_PLAYLIST_NAME) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                if (detail != null) Text(detail)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MAX_NAME_LENGTH = 80
