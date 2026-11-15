package app.ipfsmedia.feature.discussion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.BusyIndicator
import app.ipfsmedia.core.design.HexAvatar
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.InlineNotice
import app.ipfsmedia.core.design.SectionHeader
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.library.formatAge
import app.ipfsmedia.core.library.formatRemainingLife
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.UploaderRoute
import app.ipfsmedia.core.social.CommentView
import app.ipfsmedia.core.social.ReceiptStatus

/**
 * The comments under an album, a video or a thread.
 *
 * Read-only and secondary: when there are none, this draws nothing at all, not even a heading
 * (docs/PLAN.md §11). Uploads rarely have comments, and an empty "Comments" block on every page
 * would make the app about something its users mostly do not do.
 *
 * @param rootKey the record the comments hang from: the upload's key, or the thread's.
 */
@Composable
fun CommentsSection(rootKey: String, modifier: Modifier = Modifier) {
    val viewModel: DiscussionViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val comments = state.commentsUnder(rootKey)
    if (comments.isEmpty()) return

    val sealed = state.sealedCount(rootKey)
    val now = remember(comments) { System.currentTimeMillis() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        SectionHeader(title = "Comments", count = comments.size)
        if (state.truncated) {
            InlineNotice(text = "There is more discussion than one refresh reads. This may not be all of it.")
        }
        if (sealed > 0) {
            SealedNotice(rootKey = rootKey, count = sealed, viewModel = viewModel)
        }
        comments.forEach { comment ->
            CommentCard(comment = comment, nowMillis = now, onMute = { viewModel.setMuted(comment.authorAddress, it) })
        }
    }
}

/**
 * Some comments are sealed for members. Unsealing asks the key service, which the first time
 * in an epoch means one wallet signature, so it is a button that says so and never automatic.
 */
@Composable
private fun SealedNotice(rootKey: String, count: Int, viewModel: DiscussionViewModel) {
    val colors = appColors
    when (val unsealing = viewModel.unsealing[rootKey]) {
        UnsealState.Working -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            BusyIndicator(label = "Unsealing")
            Text(text = "Checking your access…", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        is UnsealState.Failed -> InlineNotice(
            text = listOfNotNull(unsealing.message.what, unsealing.message.todo).joinToString(" "),
            isError = true,
            icon = AppIcons.Error,
            actionLabel = "OK",
            onAction = { viewModel.dismissUnsealError(rootKey) },
        )
        null -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            Text(
                text = "${countOf(count, "comment is", "comments are")} sealed for members. " +
                    "Reading them may ask your wallet for one signature. No transaction, no fee.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { viewModel.unseal(rootKey) }) { Text("Unseal") }
        }
    }
}

/** One comment: who, when, whether their membership receipt verifies, and the text. */
@Composable
fun CommentCard(comment: CommentView, nowMillis: Long, onMute: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors
    val navigator = LocalNavigator.current
    var menuOpen by remember { mutableStateOf(false) }
    // Replies step in under what they answer, up to the depth the tree keeps.
    val indent = Space.x6 * comment.depth.coerceAtMost(MAX_INDENT_LEVELS)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = indent),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        HexAvatar(address = comment.authorAddress, name = comment.authorName)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                Text(text = comment.authorName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                ReceiptMark(comment.receipt)
                val age = formatAge(comment.createdAtMillis, nowMillis)
                if (age != null) {
                    Text(text = age, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
            }
            val text = comment.text
            when {
                comment.isMuted -> Quiet("You muted this address.")
                text != null -> Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                comment.isSealed -> Quiet("Sealed comment")
                else -> Quiet("This comment has no text.")
            }
            // Arkiv records are leased, not permanent, and a comment says how long it has left.
            formatRemainingLife(comment.expiresAtMillis, nowMillis)?.let { life ->
                Text(text = life, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            }
        }
        Box {
            IconAction(
                icon = AppIcons.More,
                label = "Actions for this comment",
                onClick = { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Go to uploader") },
                    leadingIcon = { Icon(AppIcons.Person, contentDescription = null, modifier = Modifier.size(Sizes.icon)) },
                    onClick = {
                        menuOpen = false
                        navigator.open(UploaderRoute(comment.authorAddress))
                    },
                )
                DropdownMenuItem(
                    // Muting is local: nobody else can tell, and nothing is sent.
                    text = { Text(if (comment.isMuted) "Unmute this address" else "Mute this address") },
                    leadingIcon = { Icon(AppIcons.Mute, contentDescription = null, modifier = Modifier.size(Sizes.icon)) },
                    onClick = {
                        menuOpen = false
                        onMute(!comment.isMuted)
                    },
                )
            }
        }
    }
}

/** Whether the author's membership receipt verifies. Says nothing when it was not checked. */
@Composable
fun ReceiptMark(receipt: ReceiptStatus) {
    val colors = appColors
    when (receipt) {
        ReceiptStatus.VERIFIED -> Icon(
            imageVector = AppIcons.Verified,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier
                .size(Sizes.iconSmall)
                .semantics { contentDescription = "Member when they posted" },
        )
        ReceiptStatus.FAILED -> Text(
            text = "Membership not verified",
            style = MaterialTheme.typography.labelSmall,
            color = colors.warning,
        )
        ReceiptStatus.UNCHECKED -> Unit
    }
}

@Composable
private fun Quiet(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = appColors.textTertiary,
    )
}

private const val MAX_INDENT_LEVELS = 4
