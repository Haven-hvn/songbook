package app.ipfsmedia.feature.discussion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.HexAvatar
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.InlineNotice
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.PageHeader
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.ReadingColumn
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.library.formatAge
import app.ipfsmedia.core.library.formatRemainingLife
import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.ThreadRoute
import app.ipfsmedia.core.social.ThreadView

/**
 * A DataDAO's discussion threads, newest first, as rows that open the thread. Used by the
 * DataDAO page's Discussion tab, which only exists when the DataDAO has threads.
 */
@Composable
fun ThreadList(daoKey: String, modifier: Modifier = Modifier) {
    val viewModel: DiscussionViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    var showHidden by rememberSaveable { mutableStateOf(false) }
    val threads = state.threadsFor(daoKey, includeHidden = showHidden)
    val hiddenCount = state.threadsFor(daoKey, includeHidden = true).size - state.threadsFor(daoKey).size
    val now = remember(threads) { System.currentTimeMillis() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        if (state.truncated) {
            InlineNotice(text = "There is more discussion than one refresh reads. This may not be all of it.")
        }
        if (threads.isEmpty()) {
            EmptyState(
                icon = AppIcons.Comments,
                title = "No threads to show",
                body = if (hiddenCount > 0) "You have hidden every thread here." else "Nobody has started a thread in this DataDAO.",
            )
        }
        threads.forEach { thread ->
            ThreadRow(
                thread = thread,
                nowMillis = now,
                onOpen = { navigator.open(ThreadRoute(thread.key)) },
                onHide = { viewModel.setThreadHidden(thread.key, !thread.isHidden) },
            )
            HorizontalDivider(color = appColors.border)
        }
        if (hiddenCount > 0) {
            TextButton(onClick = { showHidden = !showHidden }) {
                Text(if (showHidden) "Leave hidden threads out" else "Show ${countOf(hiddenCount, "hidden thread")}")
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: ThreadView, nowMillis: Long, onOpen: () -> Unit, onHide: () -> Unit) {
    val colors = appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.md)
            .clickable(role = Role.Button, onClickLabel = "Open thread", onClick = onOpen)
            .padding(vertical = Space.x3, horizontal = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        HexAvatar(address = thread.authorAddress, name = thread.authorName)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            Text(
                text = thread.subject.ifBlank { "Untitled thread" },
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                Text(text = thread.authorName, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                ReceiptMark(thread.receipt)
                Text(
                    text = listOfNotNull(
                        formatAge(thread.createdAtMillis, nowMillis),
                        countOf(thread.commentCount, "comment"),
                        if (thread.isSealed) "sealed for members" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
        IconAction(
            icon = AppIcons.Hide,
            // Hiding is local: the thread is only left out of this device's list.
            label = if (thread.isHidden) "Show this thread again" else "Hide this thread",
            onClick = onHide,
            selected = thread.isHidden,
        )
    }
}

/** One thread: what was asked or said, by whom, and the comments under it. Read-only. */
@Composable
fun ThreadScreen(threadKey: String) {
    val viewModel: DiscussionViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library = LocalLibraryState.current
    val navigator = LocalNavigator.current
    val colors = appColors
    val thread = state.thread(threadKey)
    val now = remember(thread) { System.currentTimeMillis() }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.x12)) {
        if (thread == null) {
            item(key = "gone") {
                EmptyState(
                    icon = AppIcons.Comments,
                    title = "This thread is not here any more",
                    body = "Threads are kept for about two weeks. It may have expired since the last refresh.",
                    actionLabel = "Back",
                    onAction = { navigator.back() },
                )
            }
            return@LazyColumn
        }
        item(key = "thread") {
            ReadingColumn {
                val dao = library.dao(thread.daoKey)
                PageHeader(
                    title = thread.subject.ifBlank { "Untitled thread" },
                    subtitle = listOfNotNull(
                        thread.authorName,
                        formatAge(thread.createdAtMillis, now),
                        formatRemainingLife(thread.expiresAtMillis, now),
                    ).joinToString(" · "),
                    actions = {
                        ReceiptMark(thread.receipt)
                        if (dao != null) {
                            TextButton(onClick = { navigator.open(DataDaoRoute(dao.key, tab = "DISCUSSION")) }) {
                                Text(dao.name)
                            }
                        }
                    },
                )
                val text = thread.text
                when {
                    text != null -> Text(text = text, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                    thread.isSealed -> InlineNotice(text = "This thread's text is sealed for members.", icon = AppIcons.Lock)
                    else -> Unit
                }
                if (thread.hasAttachment) {
                    InlineNotice(text = "This thread has an attachment. Attachments are not shown in this version.")
                }
                if (thread.isSealed && text == null && state.sealedCount(threadKey) == 0) {
                    // The thread itself is the only sealed record under this root.
                    TextButton(onClick = { viewModel.unseal(threadKey) }) { Text("Unseal for members") }
                }
                CommentsSection(rootKey = threadKey)
                if (state.commentCount(threadKey) == 0) {
                    Text(
                        text = "No comments on this thread.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}
