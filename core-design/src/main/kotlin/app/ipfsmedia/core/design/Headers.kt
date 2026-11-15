package app.ipfsmedia.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

/**
 * The header of an album, video, DataDAO, uploader or playlist page: a gradient ground with a
 * faint repeated pattern, the picture, a wide-tracked uppercase title, a line of facts, and the
 * page's actions.
 *
 * The ground is drawn from [seed] (the same generated pattern as the item's cover), so a page
 * and the card that led to it belong together. Text on it is always light: the ground is dark
 * in both themes.
 *
 * @param art the picture, already sized by the caller with [heroArtSize].
 * @param facts short pieces under the title: badges, counts, a date.
 * @param actions the main action first, then the others.
 */
@Composable
fun HeroHeader(
    title: String,
    seed: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    art: (@Composable () -> Unit)? = null,
    facts: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val compact = LocalWidthClass.current.isCompact
    val gutter = if (compact) Space.gutterCompact else Space.gutter
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) Sizes.heroHeightCompact else Sizes.heroHeight),
    ) {
        CoverBackdrop(seed = seed, modifier = Modifier.matchParentSize())
        // A scrim for legible text, and the faint repeated pattern over the gradient.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(HeroScrimTop, HeroScrimBottom)))
                .drawWithContent {
                    drawContent()
                    val step = HERO_PATTERN_STEP_DP * density
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(
                            color = HeroPatternLine,
                            start = Offset(x, size.height),
                            end = Offset(x + size.height, 0f),
                            strokeWidth = density,
                        )
                        x += step
                    }
                },
        )
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = gutter, vertical = if (compact) Space.x4 else Space.x6),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(if (compact) Space.x4 else Space.x6),
            ) {
                if (art != null) art()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    if (overline != null) {
                        Text(
                            text = overline.uppercase(),
                            style = AppType.groupLabel,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        // Never typed in capitals: the component upper-cases what it is given.
                        text = title.uppercase(),
                        style = if (compact || title.length > HERO_LONG_TITLE) AppType.hero else AppType.heroLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (facts != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.x2),
                            verticalArrangement = Arrangement.spacedBy(Space.x2),
                        ) { facts() }
                    }
                    if (actions != null) {
                        Row(
                            modifier = Modifier.padding(top = Space.x2),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.x2),
                            content = actions,
                        )
                    }
                }
            }
        }
    }
}

/** The size for a hero's picture in the current window. */
@Composable
fun Modifier.heroArtSize(): Modifier =
    size(if (LocalWidthClass.current.isCompact) Sizes.heroArtCompact else Sizes.heroArt)

/**
 * The title over a row or grid of content. Every section title in the app is this one: a
 * heading over content never carries its own font or link styles.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = appColors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .semantics { heading() },
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textTertiary,
            )
        }
        Box(modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** The small uppercase label over a group: sidebar sections, groups of settings, a day in the feed. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = AppType.groupLabel,
        color = appColors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
    )
}

private val HeroScrimTop = Color.Black.copy(alpha = 0.15f)
private val HeroScrimBottom = Color.Black.copy(alpha = 0.6f)
private val HeroPatternLine = Color.White.copy(alpha = 0.04f)
private const val HERO_PATTERN_STEP_DP = 14f
private const val HERO_LONG_TITLE = 22
