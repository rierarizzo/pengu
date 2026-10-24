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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.inter_bold
import com.misgus.pengu.app.shared.generated.resources.inter_medium
import com.misgus.pengu.app.shared.generated.resources.inter_regular
import com.misgus.pengu.app.shared.generated.resources.inter_semibold
import com.misgus.pengu.app.shared.generated.resources.roboto_bold
import com.misgus.pengu.app.shared.generated.resources.roboto_medium
import com.misgus.pengu.app.shared.generated.resources.roboto_regular
import com.misgus.pengu.app.shared.generated.resources.roboto_semibold
import com.misgus.pengu.core.repository.AppTypeface
import org.jetbrains.compose.resources.Font

/** Material 3 shapes, built from [AppCorner]. */
private val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(AppCorner.extraSmall),
        small = RoundedCornerShape(AppCorner.small),
        medium = RoundedCornerShape(AppCorner.medium),
        large = RoundedCornerShape(AppCorner.large),
        extraLarge = RoundedCornerShape(AppCorner.extraLarge),
    )

/** Bottom-sheet shape: only the top corners carry the radius, and only AppCorner. */
val AppBottomSheetShape: Shape =
    RoundedCornerShape(topStart = AppCorner.extraLarge, topEnd = AppCorner.extraLarge)

/**
 * Builds the app's Material 3 colour scheme from the [BrandPalette].
 *
 * Every role is filled so stock Material3 components (text fields, switches,
 * chips, dialogs, snackbars) render on the same palette as the custom surfaces.
 * The roles the palette does not own keep their Material defaults, so a palette
 * only declares what makes it different.
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
 *
 * The small roles (`bodySmall`, `labelMedium`, `labelSmall`) sit at Medium rather
 * than Normal: at 12sp the Regular stems are thin enough that grayscale
 * antialiasing (Compose Desktop has no ClearType) makes them read as jagged.
 * Everything above them keeps the lighter-than-stock scale.
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
        bodySmall = base.bodySmall.copy(fontFamily = font, fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontFamily = font, fontWeight = FontWeight.Normal),
        labelMedium = base.labelMedium.copy(fontFamily = font, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = font, fontWeight = FontWeight.Medium),
    )
}

/**
 * The family for [typeface], bundled at the four weights the scale uses.
 *
 * Both bundled families follow the same weight map, so switching the setting
 * changes the letterforms only: Regular carries body and titles, Medium the
 * display and section headings and SemiBold buttons and the player title. Bold is
 * bundled with each family so a heavier accent is one call-site choice away.
 */
@Composable
internal fun appFontFamily(typeface: AppTypeface): FontFamily =
    when (typeface) {
        AppTypeface.ROBOTO ->
            FontFamily(
                Font(Res.font.roboto_regular, FontWeight.Normal),
                Font(Res.font.roboto_medium, FontWeight.Medium),
                Font(Res.font.roboto_semibold, FontWeight.SemiBold),
                Font(Res.font.roboto_bold, FontWeight.Bold),
            )
        AppTypeface.INTER ->
            FontFamily(
                Font(Res.font.inter_regular, FontWeight.Normal),
                Font(Res.font.inter_medium, FontWeight.Medium),
                Font(Res.font.inter_semibold, FontWeight.SemiBold),
                Font(Res.font.inter_bold, FontWeight.Bold),
            )
    }

/**
 * The app's dark Material3 theme, with the [typeface] and the MD3 shapes.
 *
 * [CoffeePalette] is provided through [LocalBrandPalette] so the scrims and the
 * window chrome repaint together with the scheme.
 */
@Composable
fun PenguTheme(
    typeface: AppTypeface = AppTypeface.ROBOTO,
    content: @Composable () -> Unit,
) {
    val fontFamily = appFontFamily(typeface)

    CompositionLocalProvider(LocalBrandPalette provides CoffeePalette) {
        MaterialTheme(
            colorScheme = CoffeePalette.toColorScheme(),
            typography = appTypography(fontFamily),
            shapes = AppShapes,
            content = content,
        )
    }
}
