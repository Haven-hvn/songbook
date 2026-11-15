package app.ipfsmedia.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.ipfsmedia.core.library.AccessState
import app.ipfsmedia.core.library.DataDao
import app.ipfsmedia.core.library.Membership
import app.ipfsmedia.core.library.Uploader
import app.ipfsmedia.core.library.asPayload
import app.ipfsmedia.core.library.countOf

/** The wallet's standing in a DataDAO, as the access state every item of that DataDAO shows. */
fun DataDao.accessState(): AccessState = when {
    isPublicGroup -> AccessState.OPEN
    membership == Membership.MEMBER -> AccessState.MEMBER
    membership == Membership.NOT_MEMBER -> AccessState.NOT_MEMBER
    else -> AccessState.UNKNOWN
}

/**
 * A DataDAO as a card: its generated emblem, how much it holds, and whether this wallet is a
 * member. It can be opened, pinned to the sidebar, or carried there.
 */
@Composable
fun DaoCard(dao: DataDao, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    EntityCard(
        title = dao.name,
        subtitle = if (dao.itemCount == 0) "Nothing live right now" else countOf(dao.itemCount, "upload"),
        seed = dao.key,
        payload = dao.asPayload(),
        onOpen = onOpen,
        modifier = modifier,
        icon = if (dao.isPublicGroup) AppIcons.Public else AppIcons.DataDao,
        badge = { AccessBadge(access = dao.accessState()) },
    )
}

/**
 * An uploader as a card: a hexagon, as Parachord draws people, with how much they have live and
 * a count of uploads the user has not been shown yet when they are followed.
 */
@Composable
fun UploaderCard(uploader: Uploader, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    EntityCard(
        title = uploader.displayName,
        subtitle = when {
            uploader.isSelf -> "You · ${countOf(uploader.uploadCount, "upload")}"
            else -> countOf(uploader.uploadCount, "upload")
        },
        seed = uploader.address,
        payload = uploader.asPayload(),
        onOpen = onOpen,
        modifier = modifier,
        hexagon = true,
        badge = if (uploader.followed) {
            { StatusPill(text = "Following", icon = AppIcons.Check, foreground = appColors.accent, ground = appColors.accentSurface) }
        } else {
            null
        },
        count = if (uploader.followed) uploader.unseenCount else null,
    )
}

/** A titled, sideways row of DataDAOs or uploaders: the entity counterpart of [CardShelf]. */
@Composable
fun <T : Any> EntityShelf(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    card: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val gutter = pageGutter
    val compact = LocalWidthClass.current.isCompact
    val cardWidth = if (compact) Sizes.cardMinWidthCompact else Sizes.cardMinWidth
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            if (leading != null) leading()
            SectionHeader(
                title = title,
                modifier = Modifier.weight(1f),
                count = items.size,
                actionLabel = actionLabel,
                onAction = onAction,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(if (compact) Space.x3 else Space.x5),
        ) {
            items(items = items, key = key, contentType = { "entity" }) { item ->
                card(item, Modifier.width(cardWidth).animateItem())
            }
        }
    }
}
