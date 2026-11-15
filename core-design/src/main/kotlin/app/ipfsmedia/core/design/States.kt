package app.ipfsmedia.core.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.ipfsmedia.core.library.UserMessage

/**
 * Nothing to show, and that is not an error: an empty playlist, no favourites yet, a search with
 * no hits. Says what would put something here, and offers the action when there is one.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = appColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.x12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(Sizes.iconHero))
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = Sizes.dialogWidth),
        )
        if (actionLabel != null && onAction != null) {
            AccentButton(text = actionLabel, onClick = onAction)
        }
    }
}

/**
 * Something went wrong. Always the pair Parachord's audit asks for: what happened, and what to
 * do about it. Announced to screen readers when it appears.
 */
@Composable
fun ErrorState(
    message: UserMessage,
    modifier: Modifier = Modifier,
    retryLabel: String = "Try again",
    onRetry: (() -> Unit)? = null,
) {
    val colors = appColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.x12)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Icon(imageVector = AppIcons.Error, contentDescription = null, tint = colors.error, modifier = Modifier.size(Sizes.iconHero))
        Text(text = message.what, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
        val todo = message.todo
        if (todo != null) {
            Text(
                text = todo,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = Sizes.dialogWidth),
            )
        }
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry) { Text(retryLabel) }
        }
    }
}

/** A one-line notice inside a page: a refresh that was refused, a list that is partly loaded. */
@Composable
fun InlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = AppIcons.Info,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = appColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.md)
            .background(if (isError) colors.errorSurface else colors.inset)
            .padding(horizontal = Space.x3, vertical = Space.x2)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isError) colors.error else colors.textSecondary,
            modifier = Modifier.size(Sizes.icon),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// ── Skeletons ───────────────────────────────────────────────────────────────────────────────
//
// A view that can be waiting on data with nothing to show gets a skeleton in its layout's shape,
// never a blank area or a lone spinner. Each skeleton below takes its measurements from the same
// tokens as the thing it stands in for, so content replaces it without the page jumping.

/** Draws this box as a pulsing placeholder. */
fun Modifier.skeleton(shape: Shape = Radius.md): Modifier = composed {
    val colors = appColors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MS), RepeatMode.Reverse),
        label = "skeletonPhase",
    )
    clip(shape).drawBehind { drawRect(lerp(colors.skeleton, colors.skeletonHighlight, phase)) }
}

/** Stands in for a [MediaCard]: the art box and the text block beneath it. */
@Composable
fun MediaCardSkeleton(modifier: Modifier = Modifier, wide: Boolean = false) {
    Column(modifier = modifier.semantics { contentDescription = "Loading" }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (wide) VIDEO_ASPECT else 1f)
                .skeleton(Radius.lg),
        )
        Column(
            modifier = Modifier
                .height(Sizes.cardTextHeight)
                .padding(top = Space.x2),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            SkeletonLine(fraction = 0.8f)
            SkeletonLine(fraction = 0.5f)
        }
    }
}

/** Stands in for a [MediaRow]. */
@Composable
fun MediaRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.rowHeight)
            .padding(horizontal = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Box(modifier = Modifier.size(Sizes.rowArt).skeleton(Radius.sm))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            SkeletonLine(fraction = 0.6f)
            SkeletonLine(fraction = 0.35f)
        }
        Box(modifier = Modifier.width(Space.x10).height(Space.x3).skeleton(Radius.xs))
    }
}

/** Stands in for a [HeroHeader]. */
@Composable
fun HeroSkeleton(modifier: Modifier = Modifier) {
    val compact = LocalWidthClass.current.isCompact
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) Sizes.heroHeightCompact else Sizes.heroHeight)
            .background(appColors.inset)
            .padding(horizontal = if (compact) Space.gutterCompact else Space.gutter, vertical = Space.x6),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Space.x6),
    ) {
        Box(modifier = Modifier.size(if (compact) Sizes.heroArtCompact else Sizes.heroArt).skeleton(Radius.lg))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            SkeletonLine(fraction = 0.2f)
            SkeletonLine(fraction = 0.6f, height = Space.x8)
            SkeletonLine(fraction = 0.4f)
        }
    }
}

@Composable
fun SkeletonLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = Space.x3) {
    Spacer(
        modifier = modifier
            .fillMaxWidth(fraction)
            .height(height)
            .skeleton(Radius.xs),
    )
}

/** Width over height of a video card's picture. */
const val VIDEO_ASPECT: Float = 16f / 9f
private const val SKELETON_PULSE_MS = 900
