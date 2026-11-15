package app.ipfsmedia.core.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.ipfsmedia.core.library.AccessState
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.ItemKind
import app.ipfsmedia.core.library.LibraryItem
import app.ipfsmedia.core.library.OfflineState
import app.ipfsmedia.core.library.asPayload

/**
 * An album or a video as a card: the one card every grid in the app is made of.
 *
 * It carries its own behaviour. Tap or click opens it ([onOpen]); its play button plays it;
 * long-press lifts it and raises the tray; right-click opens its actions; it shows its DataDAO,
 * its access state, whether it is on the device, and what the fetch pipeline is doing to it. A
 * screen only has to place it.
 *
 * The play button is always there, quieter at rest and brighter on hover or focus: nothing
 * depends on hover (docs/PLAN.md §6).
 *
 * @param caption a short extra line: "2 days ago" in the feed, a count on a profile.
 */
@Composable
fun MediaCard(
    item: LibraryItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    caption: String? = null,
) {
    val actions = LocalItemActions.current
    val interaction = rememberItemInteraction()
    val payload = remember(item) { item.asPayload() }
    val stage = LocalStages.current[item.id]
    val playing = LocalNowPlaying.current.let { it.isRecord(item.id) && it.isPlaying }
    val wide = item.kind == ItemKind.VIDEO
    val transitions = LocalArtTransitions.current
    val surface = LocalArtSurface.current

    CardFrame(
        interaction = interaction,
        modifier = modifier
            .itemBehaviour(
                interaction = interaction,
                payload = { payload },
                onActivate = {
                    // This card's picture is the one that travels to the page's hero.
                    transitions?.openedFrom(surface, item.id)
                    onOpen()
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    item.title,
                    if (wide) "Video" else "Album",
                    "by ${item.uploaderName}",
                    item.daoName,
                    item.access.label(),
                    item.offline.label(),
                    stage?.label,
                ).joinToString(", ")
            },
    ) {
        ItemMenu(interaction = interaction, payload = { payload })
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (wide) VIDEO_ASPECT else 1f),
        ) {
            Artwork(
                recordKey = item.id,
                title = item.title,
                modifier = Modifier
                    .matchParentSize()
                    .cardArt(item.id),
                shape = Radius.lg,
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Space.x2),
                horizontalArrangement = Arrangement.spacedBy(Space.x1),
            ) {
                if (isNew) NewBadge()
                if (item.access != AccessState.OPEN) AccessBadge(access = item.access, iconOnly = true)
            }
            if (item.offline != OfflineState.NOT_DOWNLOADED) {
                OfflineBadge(
                    offline = item.offline,
                    iconOnly = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.x2),
                )
            }
            if (playing) {
                PlayingMarker(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Space.x2),
                )
            }
            CardPlayButton(
                interaction = interaction,
                label = "Play ${item.title}",
                locked = item.access.isLocked,
                onClick = { actions.perform(Destination.PlayNow, payload) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Space.x2),
            )
            if (stage != null) {
                // Over the picture, on a dark scrim, so it reads the same in both themes.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(CardStageScrim)
                        .padding(Space.x2),
                ) {
                    AlwaysDark { StageIndicator(stage = stage) }
                }
            }
        }
        CardText(
            title = item.title,
            subtitle = item.uploaderName,
            badge = item.daoName,
            caption = caption,
        )
    }
}

/**
 * A card for something that is not an upload: a DataDAO, an uploader, a playlist. Same frame,
 * same lift, same pick-up behaviour as [MediaCard].
 *
 * @param hexagon draw the picture as a hexagon (uploaders, as Parachord draws people).
 */
@Composable
fun EntityCard(
    title: String,
    subtitle: String?,
    seed: String,
    payload: DragPayload,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hexagon: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    count: Int? = null,
) {
    val interaction = rememberItemInteraction()
    CardFrame(
        interaction = interaction,
        modifier = modifier
            .itemBehaviour(interaction = interaction, payload = { payload }, onActivate = { onOpen() })
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(title, subtitle).joinToString(", ")
            },
    ) {
        ItemMenu(interaction = interaction, payload = { payload })
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            val shape: Shape = if (hexagon) HexagonShape else Radius.lg
            GeneratedCover(
                seed = seed,
                title = title,
                modifier = Modifier.matchParentSize(),
                shape = shape,
                showInitials = icon == null,
            )
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(Sizes.iconHero),
                )
            }
            if (count != null && count > 0) {
                CountBadge(
                    count = count,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.x2),
                )
            }
        }
        Column(
            modifier = Modifier
                .height(Sizes.cardTextHeight)
                .padding(top = Space.x2),
            verticalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = appColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badge != null) badge()
        }
    }
}

/**
 * The card's box. Cards lift on hover or focus and settle on press; while this card is the one
 * being carried it fades, because the carried copy is under the pointer.
 */
@Composable
private fun CardFrame(
    interaction: ItemInteraction,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = appColors
    val hovered by interaction.interactionSource.collectIsHoveredAsState()
    val focused by interaction.interactionSource.collectIsFocusedAsState()
    val pressed by interaction.interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(
        targetValue = when {
            pressed -> Elevation.cardPressed
            hovered || focused -> Elevation.cardHover
            else -> Elevation.card
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "cardElevation",
    )
    Surface(
        shape = Radius.xl,
        color = colors.elevated,
        contentColor = colors.textPrimary,
        shadowElevation = elevation,
        modifier = modifier.cursor(Cursor.Grab),
    ) {
        Column(modifier = Modifier.padding(Space.x2)) { content() }
    }
}

@Composable
private fun CardText(title: String, subtitle: String, badge: String?, caption: String?) {
    val colors = appColors
    Column(
        modifier = Modifier
            .height(Sizes.cardTextHeight)
            .padding(top = Space.x2),
        verticalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            if (badge != null) DaoBadge(name = badge, modifier = Modifier.weight(1f, fill = false))
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The round play button on a card's picture. */
@Composable
private fun CardPlayButton(
    interaction: ItemInteraction,
    label: String,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val hovered by interaction.interactionSource.collectIsHoveredAsState()
    val focused by interaction.interactionSource.collectIsFocusedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (hovered || focused) 1f else REST_ALPHA,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "playButtonAlpha",
    )
    Surface(
        onClick = onClick,
        shape = Radius.full,
        color = if (locked) colors.darkPanel else colors.accentFill,
        contentColor = colors.onAccentFill,
        shadowElevation = Elevation.cardPressed,
        modifier = modifier
            .size(Sizes.playButton)
            .alpha(alpha)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (locked) AppIcons.Lock else AppIcons.Play,
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconLarge),
            )
        }
    }
}

/** Marks the card or row whose upload is playing. */
@Composable
fun PlayingMarker(modifier: Modifier = Modifier) {
    val colors = appColors
    Box(
        modifier = modifier
            .size(Sizes.badgeHeight)
            .clip(Radius.full)
            .background(colors.accentFill)
            .semantics { contentDescription = "Playing" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.Playing,
            contentDescription = null,
            tint = colors.onAccentFill,
            modifier = Modifier.size(Sizes.iconSmall),
        )
    }
}

private val CardStageScrim = Color.Black.copy(alpha = 0.55f)
private const val REST_ALPHA = 0.82f
