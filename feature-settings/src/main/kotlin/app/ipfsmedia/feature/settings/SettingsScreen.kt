package app.ipfsmedia.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AccentButton
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.BusyIndicator
import app.ipfsmedia.core.design.FactRow
import app.ipfsmedia.core.design.GroupLabel
import app.ipfsmedia.core.design.InlineNotice
import app.ipfsmedia.core.design.LocalInputKind
import app.ipfsmedia.core.design.PageHeader
import app.ipfsmedia.core.design.Panel
import app.ipfsmedia.core.design.ReadingColumn
import app.ipfsmedia.core.design.SegmentedSwitch
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.ThemeMode
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.library.SHORTCUTS
import app.ipfsmedia.core.library.ShortcutGroup
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.library.formatAge
import app.ipfsmedia.core.library.formatBytes
import app.ipfsmedia.core.player.StorageUse
import app.ipfsmedia.core.social.RefreshState
import cloud.filecoin.foc.cache.FocChain
import haven.mobile.core.cache.AUTOMATIC_QUOTA
import haven.mobile.core.domain.HavenChain
import androidx.compose.foundation.layout.size

/**
 * Settings. Everything here is per identity except the Filecoin network, which belongs to the
 * deployment being read rather than to whoever is reading it.
 *
 * Each setting says what it does in a sentence, and the ones that delete something say what
 * will be deleted before they do it.
 */
@Composable
fun SettingsScreen() {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshState by viewModel.refreshState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    // Sizes change while the page is away (a download finishes); measure again on return.
    LaunchedEffect(Unit) { viewModel.measure() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.x12),
        verticalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        item(key = "header", contentType = "header") {
            ReadingColumn { PageHeader(title = "Settings", subtitle = "Kept on this device, for this identity") }
        }
        item(key = "appearance", contentType = "panel") { ReadingColumn { AppearancePanel(state, viewModel) } }
        item(key = "storage", contentType = "panel") {
            ReadingColumn {
                StoragePanel(
                    state = state,
                    use = viewModel.use,
                    clearing = viewModel.clearing,
                    result = viewModel.result,
                    viewModel = viewModel,
                    onClearEverything = { confirmClear = true },
                )
            }
        }
        item(key = "access", contentType = "panel") { ReadingColumn { AccessPanel(state, viewModel) } }
        item(key = "refresh", contentType = "panel") { ReadingColumn { RefreshPanel(refreshState, viewModel::refreshNow) } }
        item(key = "privacy", contentType = "panel") { ReadingColumn { PrivacyPanel() } }
        item(key = "shortcuts", contentType = "panel") { ReadingColumn { ShortcutsPanel() } }
        item(key = "about", contentType = "panel") { ReadingColumn { AboutPanel() } }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Remove everything from this device?") },
            text = {
                Text(
                    "Every download and all cached content for this identity will be deleted. Your playlists, " +
                        "favourites, history and follows are not touched. Anything you want offline again has to be " +
                        "downloaded again.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearEverything()
                    },
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun AppearancePanel(state: SettingsUiState, viewModel: SettingsViewModel) {
    Panel(title = "Appearance and video") {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            SettingText(
                title = "Theme",
                description = "The player bar and the queue are dark in both.",
            )
            SegmentedSwitch(
                options = THEME_LABELS,
                selectedIndex = ThemeMode.entries.indexOf(state.theme),
                onSelect = { viewModel.setTheme(ThemeMode.entries[it]) },
            )
        }
        HorizontalDivider(color = appColors.border)
        SettingSwitch(
            title = "Keep video in a small window",
            description = "When you leave the app while a video is playing, it carries on in picture-in-picture.",
            checked = state.pictureInPicture,
            onCheckedChange = viewModel::setPictureInPicture,
        )
    }
}

@Composable
private fun StoragePanel(
    state: SettingsUiState,
    use: StorageUse?,
    clearing: Boolean,
    result: String?,
    viewModel: SettingsViewModel,
    onClearEverything: () -> Unit,
) {
    val colors = appColors
    Panel(title = "Storage") {
        StorageUseBlock(use = use)
        HorizontalDivider(color = colors.border)
        SettingChoice(
            title = "Room for downloads and cached content",
            description = "Automatic sizes it from the free space on this device. When it is full, what was " +
                "opened longest ago goes first.",
            options = QUOTA_CHOICES.map { if (it == AUTOMATIC_QUOTA) "Automatic" else formatBytes(it) },
            selectedIndex = nearestIndex(QUOTA_CHOICES, state.quotaBytes),
            onSelect = { viewModel.setQuotaBytes(QUOTA_CHOICES[it]) },
        )
        SettingChoice(
            title = "Keep content for",
            description = "Content not opened for this long is removed to make room.",
            options = TTL_CHOICES.map { countOf(it, "day") },
            selectedIndex = nearestIndex(TTL_CHOICES.map(Int::toLong), state.ttlDays.toLong()),
            onSelect = { viewModel.setTtlDays(TTL_CHOICES[it]) },
        )
        SettingChoice(
            title = "Room for what is ready to play",
            description = "Playing something gated makes an unlocked copy for the player to read. The copies are " +
                "dropped oldest first, never the ones in the queue, and can always be made again. " +
                "A long video needs as much room here as its file.",
            options = SPOOL_CHOICES.map(::formatBytes),
            selectedIndex = nearestIndex(SPOOL_CHOICES, state.spoolBudgetBytes),
            onSelect = { viewModel.setSpoolBudgetBytes(SPOOL_CHOICES[it]) },
        )
        HorizontalDivider(color = colors.border)
        SettingSwitch(
            title = "Keep unlocked content on this device",
            description = "Gated uploads are stored unlocked the first time you open them, so they open again " +
                "with no signature and no connection, and stay playable even if this wallet later stops " +
                "being a member. Anyone who can read this device's storage could read them. " +
                "Turning this off deletes them.",
            checked = state.keepUnlocked,
            onCheckedChange = viewModel::setKeepUnlocked,
            enabled = !clearing,
        )
        SettingSwitch(
            title = "Wipe this identity when its wallet is disconnected",
            description = "Disconnecting deletes its cached content, keys and local data from this device. " +
                "Off keeps them for the next time the same wallet connects.",
            checked = state.clearOnDisconnect,
            onCheckedChange = viewModel::setClearOnDisconnect,
        )
        HorizontalDivider(color = colors.border)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            OutlinedButton(onClick = viewModel::clearExpired, enabled = !clearing) { Text("Remove expired content") }
            OutlinedButton(onClick = onClearEverything, enabled = !clearing) { Text("Remove everything from this device") }
            if (clearing) BusyIndicator(modifier = Modifier.size(Sizes.touchTarget), label = "Removing")
        }
        if (result != null) {
            Text(text = result, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

/** How much is on the device, as two plain figures and a bar for the one that has a limit. */
@Composable
private fun StorageUseBlock(use: StorageUse?) {
    val colors = appColors
    if (use == null) {
        Text(text = "Measuring what is on this device…", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        FactRow(
            label = "Downloads and cached content",
            value = if (use.cacheQuotaBytes > 0L) {
                "${formatBytes(use.cacheUsedBytes)} of ${formatBytes(use.cacheQuotaBytes)} · ${countOf(use.cachedItems, "item")}"
            } else {
                "${formatBytes(use.cacheUsedBytes)} · ${countOf(use.cachedItems, "item")}"
            },
        )
        if (use.cacheQuotaBytes > 0L) {
            val fraction = (use.cacheUsedBytes.toFloat() / use.cacheQuotaBytes).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                color = colors.accent,
                trackColor = colors.accentSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FactRow(label = "Ready to play (unlocked copies)", value = formatBytes(use.stagedBytes))
    }
}

@Composable
private fun AccessPanel(state: SettingsUiState, viewModel: SettingsViewModel) {
    val chains = HavenChain.entries
    val enabled = state.chains.ifEmpty { HavenChain.mainnets.toSet() }
    Panel(title = "Membership and networks") {
        SettingMultiChoice(
            title = "Check membership on",
            description = "A DataDAO's rule names a chain. Each chain here is one more place this wallet's " +
                "holdings are read at every refresh. A DataDAO on a chain that is off shows as not checked.",
            options = chains.map { if (it.isTestnet) "${it.label} (test network)" else it.label },
            selected = chains.indices.filter { chains[it] in enabled }.toSet(),
            onToggle = { index, on -> viewModel.setChain(chains[index], on) },
        )
        InlineNotice(
            text = "Access follows membership, but not to the second. Membership is read at each refresh, " +
                "and what this device has already unlocked stays playable until it leaves the cache. " +
                "Leaving a DataDAO does not reach into this device and take things back.",
        )
        SettingChoice(
            title = "Filecoin network",
            description = "Where uploads are fetched from. Automatic follows each upload's own record. " +
                "This one setting is for the whole app, not per identity.",
            options = FOC_LABELS,
            selectedIndex = when (state.focNetwork) {
                null -> 0
                FocChain.MAINNET -> 1
                FocChain.CALIBRATION -> 2
            },
            onSelect = { index ->
                viewModel.setFocNetwork(
                    when (index) {
                        1 -> FocChain.MAINNET
                        2 -> FocChain.CALIBRATION
                        else -> null
                    },
                )
            },
        )
    }
}

@Composable
private fun RefreshPanel(state: RefreshState, onRefresh: () -> Unit) {
    val colors = appColors
    val now = remember(state) { System.currentTimeMillis() }
    Panel(title = "Refresh") {
        Text(
            text = "The catalogue is read from the index when the app opens and when you ask: pull down on Home, " +
                "the Refresh button, or Ctrl+R. Never on a timer, and never because a page was opened. " +
                "Between refreshes everything is read from this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        FactRow(
            label = "Last refreshed",
            value = when {
                state.running -> state.stage?.label ?: "Refreshing…"
                else -> formatAge(state.lastRefreshedAtMillis, now) ?: "Not yet"
            },
        )
        val error = state.error
        if (error != null && !state.running) {
            InlineNotice(text = listOfNotNull(error.what, error.todo).joinToString(" "), isError = true)
        }
        val limitedUntil = state.rateLimitedUntilMillis
        if (limitedUntil != null && limitedUntil > now) {
            InlineNotice(
                text = "The index is rate limited for about another ${((limitedUntil - now) / MINUTE_MS + 1)} minutes.",
                icon = AppIcons.Warning,
            )
        }
        val note = state.note
        if (note != null && !state.running) InlineNotice(text = note)
        AccentButton(text = "Refresh now", icon = AppIcons.Refresh, onClick = onRefresh, enabled = !state.running)
    }
}

@Composable
private fun PrivacyPanel() {
    val colors = appColors
    Panel(title = "What stays on this device") {
        Text(
            text = "What you play, your history, playlists, favourites, the people you follow, the DataDAOs you pin " +
                "and what you type into search are kept on this device, per identity, and are sent nowhere. " +
                "There are no accounts, no analytics and no ads.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "The app talks to: the Arkiv index (to read the catalogue), Filecoin storage providers (to fetch " +
                "uploads), the Haven-AOL canister (to unlock gated ones), public chain endpoints (to read this " +
                "wallet's memberships), and the WalletConnect relay (to reach your wallet). A storage provider " +
                "sees which piece is fetched, as any server sees what is asked of it.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

/** The shortcut table, as the same data that drives the keys and the system's shortcuts helper. */
@Composable
private fun ShortcutsPanel() {
    val colors = appColors
    val hasKeyboard = LocalInputKind.current.hasKeyboard
    // One row per action, with every chord that triggers it.
    val byGroup = remember {
        ShortcutGroup.entries.map { group ->
            group to SHORTCUTS.filter { it.action.group == group }
                .groupBy { it.action }
                .map { (action, specs) -> action.label to specs.map { it.chord.display } }
        }
    }
    Panel(title = "Keyboard shortcuts") {
        if (!hasKeyboard) {
            Text(
                text = "No keyboard is attached. These apply when one is.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        byGroup.forEach { (group, rows) ->
            GroupLabel(text = group.title, modifier = Modifier.padding(top = Space.x2))
            rows.forEach { (label, chords) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    chords.forEach { KeyCap(text = it) }
                }
            }
        }
        Text(
            text = "Space and Q act on a focused item when there is one. None of these fire while you are typing.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun AboutPanel() {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
    }
    Panel(title = "About") {
        FactRow(label = "Googlebook IPFSMedia", value = "Version $version · a debug build, not signed for release")
        Text(
            text = "A player for music and video published to Filecoin and indexed on Arkiv, with access decided by " +
                "DataDAO membership. Playback is only ever through the local content-addressed cache: there are no " +
                "local files to open and nothing is streamed from a server of ours.",
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textSecondary,
        )
        Text(
            text = "Built on haven-mobile's storage and unlock code, in Parachord's design, with Material Symbols. " +
                "Their licences are in the app's NOTICE file.",
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textSecondary,
        )
    }
}

/** The choice closest to [value], so a value set elsewhere still shows a selection. */
private fun nearestIndex(choices: List<Long>, value: Long): Int =
    choices.indices.minByOrNull { kotlin.math.abs(choices[it] - value) } ?: 0

private const val GIB = 1024L * 1024L * 1024L
private const val MINUTE_MS = 60_000L
private val THEME_LABELS = listOf("System", "Light", "Dark")
private val FOC_LABELS = listOf("Automatic", "Mainnet", "Calibration (test)")
private val QUOTA_CHOICES = listOf(AUTOMATIC_QUOTA, 2 * GIB, 5 * GIB, 10 * GIB, 20 * GIB, 50 * GIB)
private val TTL_CHOICES = listOf(7, 14, 30, 90)
private val SPOOL_CHOICES = listOf(1 * GIB, 2 * GIB, 4 * GIB, 8 * GIB, 16 * GIB, 32 * GIB)
