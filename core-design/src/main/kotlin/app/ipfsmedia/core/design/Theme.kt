package app.ipfsmedia.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The user's choice of theme. Stored as its name (`PrefKeys.THEME`). */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun of(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * Colour roles Material's scheme has no slot for. Each has one meaning everywhere.
 *
 * Access and offline states in particular: green always means "you can open this / it is on this
 * device", red "you cannot", amber "could not tell", and the quiet purple "not open to anyone
 * yet". A screen never picks one of these colours for anything else.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    /** Links, the playing row, focus rings. Shifts between themes for contrast. */
    val accent: Color,
    /** Filled accent buttons. The same purple in both themes, so white text stays readable. */
    val accentFill: Color,
    val onAccentFill: Color,
    val accentSurface: Color,
    /** The one main action on a page (Play on an album, video or playlist). */
    val callToAction: Color,
    val onCallToAction: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val ground: Color,
    val groundSecondary: Color,
    val elevated: Color,
    val inset: Color,
    val border: Color,
    val borderSubtle: Color,
    val success: Color,
    val successSurface: Color,
    val warning: Color,
    val warningSurface: Color,
    val error: Color,
    val errorSurface: Color,
    /** The player bar and the queue: dark in both themes. */
    val darkPanel: Color,
    val darkPanelBorder: Color,
    /** Behind the tray and the carried card, and over everything that cannot take the held item. */
    val scrim: Color,
    val skeleton: Color,
    val skeletonHighlight: Color,
)

private val LightAppColors = AppColors(
    isDark = false,
    accent = Palette.purple600,
    accentFill = Palette.purple600,
    onAccentFill = Palette.white,
    accentSurface = Palette.purple100,
    callToAction = Palette.pink600,
    onCallToAction = Palette.white,
    textPrimary = Palette.gray900,
    textSecondary = Palette.gray500,
    textTertiary = Palette.gray400,
    ground = Palette.white,
    groundSecondary = Palette.gray50,
    elevated = Palette.white,
    inset = Palette.gray100,
    border = Palette.gray200,
    borderSubtle = Color.Black.copy(alpha = 0.06f),
    success = Palette.success,
    successSurface = Palette.successSoft,
    warning = Palette.warning,
    warningSurface = Palette.warningSoft,
    error = Palette.error,
    errorSurface = Palette.errorSoft,
    darkPanel = Palette.gray800,
    darkPanelBorder = Palette.gray700,
    scrim = Palette.gray900.copy(alpha = 0.32f),
    skeleton = Palette.gray100,
    skeletonHighlight = Palette.gray200,
)

private val DarkAppColors = AppColors(
    isDark = true,
    accent = Palette.purple400,
    accentFill = Palette.purple600,
    onAccentFill = Palette.white,
    accentSurface = Palette.purple500.copy(alpha = 0.15f),
    callToAction = Palette.pink600,
    onCallToAction = Palette.white,
    textPrimary = Palette.gray100,
    textSecondary = Palette.gray400,
    textTertiary = Palette.gray500,
    ground = Palette.ink,
    groundSecondary = Palette.inkSecondary,
    elevated = Palette.inkElevated,
    inset = Palette.inkInset,
    border = Palette.inkBorder,
    borderSubtle = Color.White.copy(alpha = 0.06f),
    success = Palette.success,
    successSurface = Palette.success.copy(alpha = 0.16f),
    warning = Palette.warning,
    warningSurface = Palette.warning.copy(alpha = 0.16f),
    error = Palette.error,
    errorSurface = Palette.error.copy(alpha = 0.16f),
    darkPanel = Palette.inkRaised,
    darkPanelBorder = Color.White.copy(alpha = 0.1f),
    scrim = Color.Black.copy(alpha = 0.5f),
    skeleton = Palette.inkElevated,
    skeletonHighlight = Palette.inkHigh,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Palette.purple600,
    onPrimary = Palette.white,
    primaryContainer = Palette.purple100,
    onPrimaryContainer = Palette.purpleDeep,
    inversePrimary = Palette.purple400,
    secondary = Palette.purple500,
    onSecondary = Palette.white,
    secondaryContainer = Palette.purple100,
    onSecondaryContainer = Palette.purple700,
    tertiary = Palette.pink600,
    onTertiary = Palette.white,
    tertiaryContainer = Palette.pink400.copy(alpha = 0.2f),
    onTertiaryContainer = Palette.pink600,
    background = Palette.white,
    onBackground = Palette.gray900,
    surface = Palette.white,
    onSurface = Palette.gray900,
    surfaceVariant = Palette.gray100,
    onSurfaceVariant = Palette.gray500,
    surfaceTint = Palette.purple600,
    inverseSurface = Palette.gray800,
    inverseOnSurface = Palette.gray100,
    error = Palette.error,
    onError = Palette.white,
    errorContainer = Palette.errorSoft,
    onErrorContainer = Palette.errorDeep,
    outline = Palette.gray300,
    outlineVariant = Palette.gray200,
    scrim = Color.Black,
    surfaceBright = Palette.white,
    surfaceDim = Palette.gray100,
    surfaceContainerLowest = Palette.white,
    surfaceContainerLow = Palette.gray50,
    surfaceContainer = Palette.gray50,
    surfaceContainerHigh = Palette.gray100,
    surfaceContainerHighest = Palette.gray200,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Palette.purple400,
    onPrimary = Palette.purpleDeep,
    primaryContainer = Palette.purple700,
    onPrimaryContainer = Palette.purple100,
    inversePrimary = Palette.purple600,
    secondary = Palette.purple300,
    onSecondary = Palette.purpleDeep,
    secondaryContainer = Palette.purple500.copy(alpha = 0.24f),
    onSecondaryContainer = Palette.purple100,
    tertiary = Palette.pink400,
    onTertiary = Palette.gray900,
    tertiaryContainer = Palette.pink600.copy(alpha = 0.24f),
    onTertiaryContainer = Palette.pink400,
    background = Palette.ink,
    onBackground = Palette.gray100,
    surface = Palette.ink,
    onSurface = Palette.gray100,
    surfaceVariant = Palette.inkElevated,
    onSurfaceVariant = Palette.gray400,
    surfaceTint = Palette.purple400,
    inverseSurface = Palette.gray100,
    inverseOnSurface = Palette.gray900,
    error = Palette.error,
    onError = Palette.white,
    errorContainer = Palette.errorDeep,
    onErrorContainer = Palette.errorSoft,
    outline = Palette.gray600,
    outlineVariant = Palette.inkBorder,
    scrim = Color.Black,
    surfaceBright = Palette.inkHigh,
    surfaceDim = Palette.ink,
    surfaceContainerLowest = Palette.inkInset,
    surfaceContainerLow = Palette.inkSecondary,
    surfaceContainer = Palette.inkElevated,
    surfaceContainerHigh = Palette.inkBorder,
    surfaceContainerHighest = Palette.inkHigh,
)

/**
 * Type sizes 11, 12, 13, 14, 16, 20, 24, 28 and 48, in the system typeface, mapped onto Material's
 * scale so Material components pick them up.
 */
private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Light),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Light),
    displaySmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Light),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

/** Radii 4, 6, 8, 12 and 16 on Material's shape scale. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** Text styles Material's scale has no slot for. */
object AppType {
    /** Hero titles: uppercase (applied by the component), wide tracking, light weight. */
    val hero: TextStyle = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 0.2.em,
    )

    val heroLarge: TextStyle = hero.copy(fontSize = 48.sp, lineHeight = 56.sp)

    /** The small uppercase labels over sidebar groups and metadata. */
    val groupLabel: TextStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.08.em,
    )

    /** Numbers that change in place (position, duration): equal-width digits, so they do not jitter. */
    val clock: TextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = "tnum",
    )
}

private val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** The extended colour roles for the current theme. */
val appColors: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current

/**
 * The app's theme: Material 3 Expressive (components, expressive spring motion) carrying
 * Parachord's palette. The palette is fixed; it is not derived from the wallpaper.
 */
@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkAppColors else LightAppColors) {
        MaterialExpressiveTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}

/**
 * Content that is dark in both themes: the player bar and the queue. Inside it, Material
 * components and [appColors] both answer with dark values, and the surface is the panel colour of
 * the theme outside, so in dark mode the panel still reads as a separate layer.
 */
@Composable
fun AlwaysDark(content: @Composable () -> Unit) {
    val outer = appColors
    val panel = outer.darkPanel
    val colors = DarkAppColors.copy(
        ground = panel,
        elevated = Palette.inkHigh,
        inset = Palette.ink.copy(alpha = 0.4f),
        border = outer.darkPanelBorder,
    )
    val scheme = DarkScheme.copy(
        background = panel,
        surface = panel,
        surfaceContainerLowest = panel,
        surfaceContainerLow = panel,
        surfaceContainer = Palette.inkHigh,
        surfaceContainerHigh = Palette.inkHigh,
        outlineVariant = outer.darkPanelBorder,
    )
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
