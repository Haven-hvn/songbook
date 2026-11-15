package app.ipfsmedia.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AccentButton
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.AppType
import app.ipfsmedia.core.design.BusyIndicator
import app.ipfsmedia.core.design.GeneratedCover
import app.ipfsmedia.core.design.HexagonShape
import app.ipfsmedia.core.design.InlineNotice
import app.ipfsmedia.core.design.Panel
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.rememberCopyText

/**
 * The only screen until a wallet is connected (docs/PLAN.md §2: wallet first).
 *
 * It says what the app is, what connecting does and does not do, and connects. The app hands a
 * `wc:` link to the wallet, the wallet opens in its own window, the user approves, and the
 * return link brings them back. If no wallet picks the link up it can be copied.
 */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val pairingUri by viewModel.pairingUri.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val copy = rememberCopyText()
    val colors = appColors
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var copied by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Sizes.dialogWidth + Space.x16)
                .padding(Space.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            GeneratedCover(
                seed = "googlebook-ipfsmedia",
                title = "",
                modifier = Modifier.size(Sizes.avatarLarge),
                shape = HexagonShape,
                showInitials = false,
            )
            Text(
                text = "Googlebook IPFSMedia".uppercase(),
                style = AppType.hero,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Music and video from DataDAOs, fetched from IPFS and kept on this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            Panel(title = "What connecting does") {
                Bullet("Your wallet's address is your identity here. There is no account and no password.")
                Bullet("The DataDAOs you are a member of decide what you can open.")
                Bullet("Connecting is not a transaction. Nothing is spent, and the app never asks to move anything.")
                Bullet("What you play, your playlists and who you follow stay on this device.")
            }

            if (!viewModel.configured) {
                InlineNotice(
                    text = "This build has no WalletConnect project id, so no wallet can connect. " +
                        "Set wallet.projectId in local.properties (the WALLET_PROJECT_ID secret in CI) and rebuild.",
                    icon = AppIcons.Warning,
                    isError = true,
                )
            }

            viewModel.error?.let { message ->
                InlineNotice(
                    text = listOfNotNull(message.what, message.todo).joinToString(" "),
                    icon = AppIcons.Error,
                    isError = true,
                )
            }

            if (viewModel.connecting) {
                BusyIndicator(label = "Waiting for your wallet")
                Text(
                    text = "Approve the connection in your wallet. It opens in its own window.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                AccentButton(
                    text = "Connect wallet",
                    icon = AppIcons.Wallet,
                    onClick = viewModel::connect,
                    enabled = viewModel.configured,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // No installed wallet took the link: it can be pasted into one by hand.
            pairingUri?.let { uri ->
                OutlinedButton(
                    onClick = {
                        copy(uri)
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (copied) "Link copied" else "Copy the connection link instead")
                }
            }

            TextButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Hide connection details" else "Show connection details")
            }
            if (showDetails) {
                Panel(title = "Connection details") {
                    if (diagnostics.isEmpty()) {
                        Text(
                            text = "Nothing has happened yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    diagnostics.forEach { line ->
                        Text(text = line, style = AppType.clock, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text(
        text = "•  $text",
        style = MaterialTheme.typography.bodyMedium,
        color = appColors.textPrimary,
    )
}
