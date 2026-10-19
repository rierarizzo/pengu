package com.misgus.pengu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.inter_bold
import com.misgus.pengu.app.shared.generated.resources.inter_medium
import com.misgus.pengu.app.shared.generated.resources.inter_regular
import com.misgus.pengu.app.shared.generated.resources.inter_semibold
import com.misgus.pengu.app.shared.generated.resources.inter_tight_bold
import com.misgus.pengu.app.shared.generated.resources.inter_tight_medium
import com.misgus.pengu.app.shared.generated.resources.inter_tight_regular
import com.misgus.pengu.app.shared.generated.resources.inter_tight_semibold
import com.misgus.pengu.core.repository.AppTheme
import com.misgus.pengu.fonts.generated.resources.ibm_plex_sans_bold
import com.misgus.pengu.fonts.generated.resources.ibm_plex_sans_medium
import com.misgus.pengu.fonts.generated.resources.ibm_plex_sans_regular
import com.misgus.pengu.fonts.generated.resources.ibm_plex_sans_semibold
import org.jetbrains.compose.resources.Font
import com.misgus.pengu.fonts.generated.resources.Res as FontsRes

/** Material 3 shapes, built from [AppCorner]. */
private val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(AppCorner.extraSmall),
        small = RoundedCornerShape(AppCorner.small),
        medium = RoundedCornerShape(AppCorner.medium),
        large = RoundedCornerShape(AppCorner.large),
        extraLarge = RoundedCornerShape(AppCorner.extraLarge),
    )

/**
 * Builds the app's Material 3 colour scheme from the [BrandPalette].
 *
 * Every role is filled so stock Material3 components (text fields, switches,
 * chips, dialogs, snackbars) render on the same palette as the custom surfaces.
 * The roles a theme does not own keep their Material defaults, so a theme only
 * declares what makes it different.
 */
private fun BrandPalette.toColorScheme(): ColorScheme =
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        inversePrimary = Color(0xFF7A5900),
        secondary = Color(0xFFCBC9CE),
        onSecondary = Color(0xFF323034),
        secondaryContainer = Color(0xFF47464A),
        onSecondaryContainer = Color(0xFFE6E4E9),
        // Tertiary mirrors the accent, so no cold hue sneaks into a warm theme.
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentContainer,
        onTertiaryContainer = onAccentContainer,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = textSecondary,
        surfaceTint = accent,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF5A5A63),
        outlineVariant = divider,
        scrim = Color.Black,
        surfaceBright = Color(0xFF35353B),
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = Color(0xFF2A2A30),
    )

/**
 * Applies [font] to every Material3 typography slot.
 *
 * Building the full scale (instead of only a handful of slots) keeps components
 * that reach for other roles — dialogs, text fields, chips — on the same
 * typeface and weight hierarchy. No slot carries a colour: text colour comes
 * from the colour scheme at the call site, exactly as Material intends.
 */
private fun appTypography(font: FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = font, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
        displayMedium = base.displayMedium.copy(fontFamily = font, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
        displaySmall = base.displaySmall.copy(fontFamily = font, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = font, fontWeight = FontWeight.Medium, letterSpacing = (-0.25).sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = font, fontWeight = FontWeight.Medium, letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = font, fontWeight = FontWeight.Normal),
        titleLarge = base.titleLarge.copy(fontFamily = font, fontWeight = FontWeight.Normal),
        titleMedium = base.titleMedium.copy(fontFamily = font, fontWeight = FontWeight.Normal),
        titleSmall = base.titleSmall.copy(fontFamily = font, fontWeight = FontWeight.Normal),
        bodyLarge = base.bodyLarge.copy(fontFamily = font, fontWeight = FontWeight.Normal),
        bodyMedium = base.bodyMedium.copy(fontFamily = font, fontWeight = FontWeight.Normal),
        bodySmall = base.bodySmall.copy(fontFamily = font, fontWeight = FontWeight.Normal),
        labelLarge = base.labelLarge.copy(fontFamily = font, fontWeight = FontWeight.Normal),
        labelMedium = base.labelMedium.copy(fontFamily = font, fontWeight = FontWeight.Normal),
        labelSmall = base.labelSmall.copy(fontFamily = font, fontWeight = FontWeight.Normal),
    )
}

/** Typeface in use. Set to `true` to compare against IBM Plex Sans. */
private const val USE_IBM_PLEX_SANS = false

@Composable
private fun interFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )

@Composable
private fun ibmPlexSansFontFamily(): FontFamily =
    FontFamily(
        Font(FontsRes.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(FontsRes.font.ibm_plex_sans_medium, FontWeight.Medium),
        Font(FontsRes.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
        Font(FontsRes.font.ibm_plex_sans_bold, FontWeight.Bold),
    )

/**
 * Inter Tight, the compact companion of the UI typeface.
 *
 * Used where a line has to read as one ribbon, such as the billboard metadata,
 * instead of the default Inter spacing. It is an accent of the app's own
 * typeface, not the IBM Plex comparison target, so it stays Inter Tight in both
 * modes.
 */
@Composable
internal fun interTightFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.inter_tight_regular, FontWeight.Normal),
        Font(Res.font.inter_tight_medium, FontWeight.Medium),
        Font(Res.font.inter_tight_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_tight_bold, FontWeight.Bold),
    )

/**
 * Provides the palette for [theme] without installing a `MaterialTheme`.
 *
 * `PenguTheme` uses it internally; the desktop composition root wraps the
 * window in it too, because it paints the native title bar — which reads the
 * palette — outside the app's `MaterialTheme`.
 */
@Composable
fun PenguThemePalette(
    theme: AppTheme = AppTheme.COFFEE,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBrandPalette provides AppThemes.of(theme), content = content)
}

/**
 * The app's dark Material3 theme, with the Inter typeface and the MD3 shapes.
 *
 * [theme] selects the [BrandPalette]; it is provided through [LocalBrandPalette]
 * so the scrims and the window chrome repaint together with the scheme.
 */
@Composable
fun PenguTheme(
    theme: AppTheme = AppTheme.COFFEE,
    content: @Composable () -> Unit,
) {
    val fontFamily = if (USE_IBM_PLEX_SANS) ibmPlexSansFontFamily() else interFontFamily()

    PenguThemePalette(theme) {
        MaterialTheme(
            colorScheme = AppThemes.of(theme).toColorScheme(),
            typography = appTypography(fontFamily),
            shapes = AppShapes,
            content = content,
        )
    }
}
