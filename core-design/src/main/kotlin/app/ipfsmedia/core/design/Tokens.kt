package app.ipfsmedia.core.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design tokens: Parachord's (its `docs/DESIGN_SYSTEM.md` and the design rules in its
 * `CLAUDE.md`), restated for Compose.
 *
 * Feature modules never write a colour or a `dp` literal; they use these. The drift test
 * (`DesignDriftTest`) fails the build when one does.
 */

/** Spacing, on a 4dp scale. `x6` (24dp) is the one page gutter. */
object Space {
    val none: Dp = 0.dp
    val x1: Dp = 4.dp
    val x2: Dp = 8.dp
    val x3: Dp = 12.dp
    val x4: Dp = 16.dp
    val x5: Dp = 20.dp
    val x6: Dp = 24.dp
    val x8: Dp = 32.dp
    val x10: Dp = 40.dp
    val x12: Dp = 48.dp
    val x16: Dp = 64.dp

    /** The page gutter: hero, filter bar and content all start here. */
    val gutter: Dp = x6

    /** The gutter in a window under 600dp wide. */
    val gutterCompact: Dp = x4
}

/** Corner radii: 4, 6, 8, 10, 12, 16 and full. */
object Radius {
    val xs: Shape = RoundedCornerShape(4.dp)
    val sm: Shape = RoundedCornerShape(6.dp)
    val md: Shape = RoundedCornerShape(8.dp)
    val lg: Shape = RoundedCornerShape(10.dp)
    val xl: Shape = RoundedCornerShape(12.dp)
    val pill: Shape = RoundedCornerShape(16.dp)
    val full: Shape = CircleShape
}

/** Fixed measurements of shared pieces. Anything a skeleton has to match is here. */
object Sizes {
    val iconSmall: Dp = 16.dp
    val icon: Dp = 20.dp
    val iconLarge: Dp = 24.dp
    val iconHero: Dp = 48.dp

    /** Smallest touch target (docs/PLAN.md §6). */
    val touchTarget: Dp = 48.dp

    val rowHeight: Dp = 56.dp

    /** Rows get taller, not smaller, when touch is the only input. */
    val rowHeightTouch: Dp = 64.dp
    val rowArt: Dp = 40.dp

    /** Card grids take their column count from this minimum width and refill on resize. */
    val cardMinWidth: Dp = 172.dp
    val cardMinWidthCompact: Dp = 148.dp

    /** Text block under a card's art: title, uploader, badge row. */
    val cardTextHeight: Dp = 76.dp

    val heroHeight: Dp = 232.dp
    val heroHeightCompact: Dp = 168.dp
    val heroArt: Dp = 168.dp
    val heroArtCompact: Dp = 104.dp

    val sidebarWidth: Dp = 256.dp
    val sidebarMinWidth: Dp = 208.dp
    val sidebarMaxWidth: Dp = 380.dp
    val railWidth: Dp = 80.dp
    val sidebarItemHeight: Dp = 40.dp
    val avatar: Dp = 28.dp
    val avatarLarge: Dp = 96.dp

    val playerBarHeight: Dp = 84.dp
    val playerBarHeightCompact: Dp = 68.dp
    val playerArt: Dp = 56.dp
    val playButton: Dp = 44.dp

    /** The play button on the full-size Now Playing view. */
    val playButtonLarge: Dp = 68.dp

    /** The largest the picture gets on the Now Playing view. */
    val nowPlayingArt: Dp = 360.dp

    val queueWidth: Dp = 360.dp
    val queueMinWidth: Dp = 296.dp
    val queueMaxWidth: Dp = 520.dp

    /** The divider between panes: thin to look at, wide to grab. */
    val dividerHitWidth: Dp = 12.dp
    val dividerLine: Dp = 1.dp

    val traySlotWidth: Dp = 112.dp
    val traySlotHeight: Dp = 72.dp
    val carriedCardWidth: Dp = 220.dp

    val badgeHeight: Dp = 22.dp
    val chipHeight: Dp = 32.dp
    val progressHeight: Dp = 4.dp
    val dropLine: Dp = 3.dp
    val focusRing: Dp = 2.dp

    /** Widest a column of running text gets: comments, About, Settings. */
    val readingWidth: Dp = 720.dp
    val dialogWidth: Dp = 420.dp
    val menuMinWidth: Dp = 220.dp
    val searchFieldWidth: Dp = 360.dp
    val qrCode: Dp = 220.dp
}

/** Shadow depths. Cards lift on hover and settle on press; a carried card lifts further. */
object Elevation {
    val none: Dp = 0.dp
    val card: Dp = 1.dp
    val cardPressed: Dp = 2.dp
    val cardHover: Dp = 6.dp
    val carried: Dp = 16.dp
    val tray: Dp = 12.dp
    val overlay: Dp = 8.dp
}

/**
 * Parachord's raw palette. Screens do not read this; they read the roles in [AppColors]
 * (`MaterialTheme.colorScheme` for anything a Material component draws).
 */
internal object Palette {
    val purple600 = Color(0xFF7C3AED)
    val purple700 = Color(0xFF6D28D9)
    val purple500 = Color(0xFF8B5CF6)
    val purple400 = Color(0xFFA78BFA)
    val purple300 = Color(0xFFC4B5FD)
    val purple100 = Color(0xFFEDE9FE)
    val purpleDeep = Color(0xFF2E1065)

    val pink600 = Color(0xFFDB2777)
    val pink400 = Color(0xFFF472B6)

    val white = Color(0xFFFFFFFF)
    val gray50 = Color(0xFFF9FAFB)
    val gray100 = Color(0xFFF3F4F6)
    val gray200 = Color(0xFFE5E7EB)
    val gray300 = Color(0xFFD1D5DB)
    val gray400 = Color(0xFF9CA3AF)
    val gray500 = Color(0xFF6B7280)
    val gray600 = Color(0xFF4B5563)
    val gray700 = Color(0xFF374151)
    val gray800 = Color(0xFF1F2937)
    val gray900 = Color(0xFF111827)

    val ink = Color(0xFF161616)
    val inkInset = Color(0xFF1A1A1A)
    val inkSecondary = Color(0xFF1E1E1E)
    val inkElevated = Color(0xFF252525)
    val inkRaised = Color(0xFF262626)
    val inkBorder = Color(0xFF2E2E2E)
    val inkHigh = Color(0xFF333333)

    val success = Color(0xFF10B981)
    val successSoft = Color(0xFFD1FAE5)
    val warning = Color(0xFFF59E0B)
    val warningSoft = Color(0xFFFEF3C7)
    val error = Color(0xFFEF4444)
    val errorSoft = Color(0xFFFEE2E2)
    val errorDeep = Color(0xFF7F1D1D)
}
