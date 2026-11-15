package app.ipfsmedia.feature.datadao

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.CardGrid
import app.ipfsmedia.core.design.DaoCard
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.InlineNotice
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.PageHeader
import app.ipfsmedia.core.design.SectionHeader
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.cardSkeletons
import app.ipfsmedia.core.design.fullWidth
import app.ipfsmedia.core.library.Membership
import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.LocalNavigator

/**
 * The DataDAO directory: yours first (pinned, then the ones this wallet is a member of), then
 * everything else the index holds, with the ungated uploads gathered as "Public".
 *
 * A DataDAO is a membership rule: hold a token, and its uploads open. The directory shows which
 * rules this wallet meets as of the last refresh. It shows no prices, balances or places to buy.
 */
@Composable
fun DataDaosScreen() {
    val library = LocalLibraryState.current
    val navigator = LocalNavigator.current
    val gridState = rememberLazyGridState()
    val yours = remember(library) { library.yourDaos }
    val others = remember(library) { library.otherDaos }
    val unchecked = remember(library) { library.daos.count { !it.isPublicGroup && it.membership == Membership.UNKNOWN } }

    CardGrid(state = gridState) {
        fullWidth(key = "header", contentType = "header") {
            PageHeader(
                title = "DataDAOs",
                subtitle = "Communities whose members can open their uploads. Membership is read from this wallet at each refresh.",
            )
        }
        when {
            !library.ready -> cardSkeletons()

            yours.isEmpty() && others.isEmpty() -> fullWidth(key = "empty") {
                EmptyState(
                    icon = AppIcons.DataDao,
                    title = "No DataDAOs to show",
                    body = "Nothing has been read from the index yet, or nothing is live there. Refresh from Home to look again.",
                )
            }

            else -> {
                if (unchecked > 0) {
                    fullWidth(key = "unchecked", contentType = "notice") {
                        InlineNotice(
                            text = if (unchecked == 1) {
                                "Membership could not be checked for one DataDAO. Refresh to try again."
                            } else {
                                "Membership could not be checked for $unchecked DataDAOs. Refresh to try again."
                            },
                            icon = AppIcons.Unknown,
                        )
                    }
                }
                if (yours.isNotEmpty()) {
                    fullWidth(key = "yours-title", contentType = "title") {
                        SectionHeader(title = "Yours", count = yours.size, modifier = Modifier.padding(top = Space.x2))
                    }
                    items(items = yours, key = { "yours-${it.key}" }, contentType = { "dao" }) { dao ->
                        DaoCard(dao = dao, onOpen = { navigator.open(DataDaoRoute(dao.key)) }, modifier = Modifier.animateItem())
                    }
                }
                if (others.isNotEmpty()) {
                    fullWidth(key = "browse-title", contentType = "title") {
                        SectionHeader(title = "Browse", count = others.size, modifier = Modifier.padding(top = Space.x4))
                    }
                    items(items = others, key = { "browse-${it.key}" }, contentType = { "dao" }) { dao ->
                        DaoCard(dao = dao, onOpen = { navigator.open(DataDaoRoute(dao.key)) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}
