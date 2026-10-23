package com.misgus.pengu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Raw brand palette of one theme.
 *
 * It is internal on purpose: it feeds the Material colour scheme and the scrims
 * only, while the UI reads every semantic colour from `MaterialTheme.colorScheme`
 * roles. That keeps stock Material3 components and custom surfaces on the same
 * colours. The palette ships as data ([CoffeePalette]); the composition reads it
 * through [LocalBrandPalette].
 */
internal data class BrandPalette(
    /**
     * App background. The single source of truth for the near-black the whole
     * UI sits on and the colour every page fade ends on.
     */
    val background: Color,
    /** Cards, panels and rails. */
    val surface: Color,
    /** Hover / selected surfaces. */
    val surfaceHigh: Color,
    /**
     * Fill of a card or raised panel: the settings categories, the settings
     * pages and every container that groups rows. Warmer and darker than
     * [surfaceHigh], so it sits on the page background without a border.
     */
    val surfaceCard: Color,
    /** Fill of a selected settings row, chip or sidebar entry. */
    val surfaceSelected: Color,
    /**
     * Brand / attention colour (amber): selection, active states, badges and
     * the featured call to action on a media billboard. Off media, the default
     * primary call to action is the high-contrast pill (`inverseSurface`).
     */
    val accent: Color,
    /** Text and glyphs drawn on [accent]. */
    val onAccent: Color,
    /** Deep attention fill (selected chips, container accents). */
    val accentContainer: Color,
    /** Text and glyphs drawn on [accentContainer]. */
    val onAccentContainer: Color,
    /** The light pill used by the default primary call to action. */
    val inverseSurface: Color,
    /** Text and glyphs drawn on [inverseSurface]. */
    val inverseOnSurface: Color,
    /**
     * Default text colour. Headings and body copy share it; secondary roles step
     * down through alpha instead of a separate grey.
     */
    val textPrimary: Color,
    /** Metadata, captions and secondary copy, dimmed so hierarchy survives. */
    val textSecondary: Color,
    /** Release year / metadata under a poster title. */
    val posterYear: Color,
    /** Idle hero carousel dot: a neutral grey that reads over artwork. */
    val indicator: Color,
    /** Hairline separators between surfaces. */
    val divider: Color,
)

/**
 * Warm coffee/amber palette: dark near-black background, amber accent, burnt
 * orange container and cream text. The app's only palette.
 *
 * [accent] and [accentContainer] are the icon's two mandible tones
 * (`branding/pengu.svg`), so the in-app chrome matches the launcher icon.
 */
internal val CoffeePalette =
    BrandPalette(
        background = Color(0xFF0B0803),
        surface = Color(0xFF151518),
        surfaceHigh = Color(0xFF1F1F24),
        surfaceCard = Color(0xFF191610),
        surfaceSelected = Color(0xFF1A1814),
        accent = Color(0xFFF5B02A),
        onAccent = Color(0xFF2A1A0E),
        accentContainer = Color(0xFFD98A0B),
        onAccentContainer = Color(0xFF2A1A0E),
        inverseSurface = Color(0xFFFEF3E2),
        inverseOnSurface = Color(0xFF2A1A0E),
        textPrimary = Color(0xFFFEF3E2),
        textSecondary = Color(0xFFFEF3E2).copy(alpha = 0.70f),
        posterYear = Color(0xFFFEF3E2).copy(alpha = 0.60f),
        indicator = Color(0xFF9E9E9E),
        divider = Color(0xFF26262B),
    )

/**
 * The palette of the theme being composed.
 *
 * Provided by `PenguTheme`; read by the scrims, the window chrome and the
 * few overlay colours the `colorScheme` has no role for. It has a default so a
 * preview or a test can compose without wrapping the content in the theme.
 */
internal val LocalBrandPalette = staticCompositionLocalOf { CoffeePalette }

/**
 * Overlay colours that the Material colour scheme has no role for.
 *
 * A scrim over media is not a theme colour, so it lives here rather than in a
 * `colorScheme` slot.
 */
object AppColors {
    /** Dark scrim over media, used behind badges and progress tracks. */
    val mediaScrim = Color.Black.copy(alpha = 0.54f)

    /** Fill of a selected settings row, chip or sidebar entry. */
    val surfaceSelected: Color
        @Composable get() = LocalBrandPalette.current.surfaceSelected

    /** Fill of a settings card or page panel. */
    val surfaceCard: Color
        @Composable get() = LocalBrandPalette.current.surfaceCard

    /** Hero carousel dots over artwork: muted grey when idle, bright when active. */
    val heroIndicatorInactive: Color
        @Composable get() = LocalBrandPalette.current.indicator
    val heroIndicatorActive: Color
        @Composable get() = LocalBrandPalette.current.textPrimary

    /**
     * Player chrome. The video is always black, so the controls are white
     * regardless of the app theme; these tokens keep that decision in one place.
     */
    val playerBackground = Color.Black
    val playerControl = Color.White
}

/** Spacing scale, on the Material 8dp system (plus a 2dp hairline). */
object AppSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

/**
 * Corner radii, as the raw values behind `AppShapes`.
 *
 * Kept small on purpose: the UI reads square-ish, not rounded. A surface that
 * needs a radius in the draw phase (such as `motionBorder`) reads the `Dp` here,
 * while anything that takes a `Shape` uses `MaterialTheme.shapes`.
 */
object AppCorner {
    val extraSmall: Dp = 1.dp
    val small: Dp = 2.dp
    val medium: Dp = 3.dp
    val large: Dp = 4.dp
    val extraLarge: Dp = 6.dp
}

/** Sizes shared by the shell. */
object AppSizes {
    /** Width of the vertical navigation rail used on wide windows. */
    val railWidth: Dp = 80.dp

    /**
     * Height of the icon-only bottom navigation bar, used on compact windows.
     *
     * Shorter than Material's 80dp bar: that height fits an icon plus a label,
     * and the bar carries no labels.
     */
    val bottomBarHeight: Dp = 64.dp

    /**
     * Scroll distance that collapses or expands the compact navigation bar.
     *
     * Small enough to react to a real scroll, large enough that a jitter does not
     * flicker the bar. The scroll connection converts it to pixels and applies it
     * in both directions.
     */
    val scrollCollapseThreshold: Dp = 12.dp

    /** Height of the desktop custom title bar. */
    val titleBarHeight: Dp = 40.dp

    /** Width of one caption button (minimize / maximize / close) on the title bar. */
    val captionButtonWidth: Dp = 46.dp

    /**
     * Height of a primary control (call-to-action button, action icon box).
     *
     * Material's minimum touch target is 48dp, so the control is at least that
     * tall even though the visual design reads tighter.
     */
    val controlHeight: Dp = 48.dp

    /** Side of an addon logo in the settings list. */
    val addonLogoSize: Dp = 48.dp

    /** Compact switch track, smaller than Material's 52x32 so it sits next to a 24dp glyph. */
    val switchTrackWidth: Dp = 40.dp
    val switchTrackHeight: Dp = 22.dp
    val switchThumbSize: Dp = 16.dp
    val switchThumbPadding: Dp = 3.dp

    /** Smallest side of an interactive target, per Material's touch guidance. */
    val minTouchTarget: Dp = 48.dp

    /**
     * Height of a form field.
     *
     * Taller than [controlHeight]: an outlined field reserves vertical room for
     * the floating label, so forcing it down to the control height crowds both
     * the label and the text. Kept here so the difference from a button is a
     * deliberate decision instead of an accident of Material's default.
     */
    val fieldHeight: Dp = 56.dp

    /** Width of the focus ring drawn around interactive controls for keyboard/D-pad. */
    val focusRing: Dp = 2.dp

    /** Hairline border around cards, posters and outlined controls. */
    val borderWidth: Dp = 1.dp

    /** Stroke of an inline progress indicator. */
    val progressStroke: Dp = 2.dp

    /** Height of the thin progress bar drawn under a poster. */
    val progressBarHeight: Dp = 3.dp

    /**
     * Widest a readable column (settings, profile picker, hero copy) grows, so
     * text does not stretch across a wide window.
     */
    val contentMaxWidth: Dp = 720.dp

    /** Widest a single-column form grows (onboarding). */
    val formMaxWidth: Dp = 420.dp

    /** Widest a centered empty/error message grows. */
    val messageMaxWidth: Dp = 360.dp

    /** Width of the settings sidebar that groups the categories on a wide window. */
    val settingsSidebarWidth: Dp = 260.dp

    /**
     * Inner padding of a settings card.
     *
     * Shared by the category cards, the rows inside a page and the hairlines
     * between them, so the copy of both levels lines up.
     */
    val cardPadding: Dp = 20.dp
}

/** Scale factors shared by interactive surfaces. */
object AppScale {
    /** Scale a poster grows to on hover or focus. */
    const val HOVER = 1.04f

    /** Press-down scale for interactive controls: a small, quick feedback step. */
    const val PRESS = 0.97f

    /** Scale a dialog starts at before it grows into place. */
    const val DIALOG_ENTER = 0.96f
}

/**
 * Sizing for the title artwork (logo) on a billboard.
 *
 * A logo is contained in a box: an optical height plus a width guard, so a square
 * logo cannot tower over the copy while a very wide one cannot take over the
 * screen. Both billboards read these values, so they stay in sync,
 * [maxWidthForWindow] trims the width guard on a phone and [opticalHeight] scales
 * the box down there.
 */
object AppLogoSize {
    /** Optical height of the title logo on the wide home billboard. */
    val homeHeight: Dp = 150.dp

    /** Optical height of the title logo on the wide detail billboard. */
    val detailHeight: Dp = 96.dp

    /**
     * Optical height of the title logo on the player's pre-roll.
     *
     * The pre-roll has no copy around the logo, so it can be a step larger than
     * the detail billboard's without competing with anything.
     */
    val splashHeight: Dp = 120.dp

    /** Widest a logo may grow on a wide window; a wider logo scales down to fit. */
    val maxWidth: Dp = 520.dp

    /**
     * Share of a compact window the logo box may span.
     *
     * A phone's copy column is nearly the whole window, so the wide guard alone
     * let a wide wordmark fill the screen and dominate the billboard. Capping it
     * keeps the logo in proportion with the rest of the copy.
     */
    private const val COMPACT_MAX_WIDTH_FRACTION = 0.6f

    /**
     * Widest the logo box may grow on the current window.
     *
     * A wide window uses [maxWidth]. A phone caps it to
     * [COMPACT_MAX_WIDTH_FRACTION] of the window, so a wide wordmark cannot span
     * the full width.
     */
    @Composable
    fun maxWidthForWindow(): Dp {
        if (LocalWindowSize.current != WindowSize.COMPACT) return maxWidth
        return minOf(maxWidth, LocalWindowWidth.current * COMPACT_MAX_WIDTH_FRACTION)
    }

    /**
     * Optical height of the logo box on the current window.
     *
     * A wide window uses [wideHeight]. A phone scales the whole box to the copy
     * width, keeping the wide billboard's [wideHeight]-over-[maxWidth]
     * proportion: a fixed height was the only binding limit there, so a square
     * logo boxed down to a sliver while a wide one still filled the column. The
     * result is capped at [wideHeight], so a narrower column can only shrink it.
     */
    @Composable
    fun opticalHeight(wideHeight: Dp): Dp {
        if (LocalWindowSize.current != WindowSize.COMPACT) return wideHeight
        val copyWidth = LocalWindowWidth.current - LocalWindowSize.current.screenPadding * 2
        return (wideHeight * (copyWidth / maxWidth)).coerceIn(0.dp, wideHeight)
    }
}

/**
 * Layout for a hero synopsis.
 *
 * A hero synopsis is a teaser, not reading copy: it shows [MAX_LINES] lines at
 * most, with the rest behind "More info", and a [maxWidth] shared by both
 * billboards, so the copy column is the same width on Home and Detail. It
 * keeps the measure around 80 characters (45–90 is the legibility range).
 */
object AppSynopsis {
    /**
     * Widest the synopsis grows, capped at the readable column so both
     * billboards wrap identically.
     */
    val maxWidth: Dp = AppSizes.contentMaxWidth

    /** Lines shown on a billboard; the detail screen shows the rest. */
    const val MAX_LINES = 3
}

/**
 * Shared icon sizes.
 *
 * [md] is Material 3's standard 24dp and is the default for icon controls, text
 * fields and list affordances; [sm] is for compact, inline and in-button glyphs.
 * Keeping the roles distinct stops an in-button glyph from being sized like a
 * standalone control. [nav] is the navigation chrome, which carries no labels.
 */
object AppIconSize {
    val sm: Dp = 20.dp
    val md: Dp = 24.dp

    /** Prominent controls over artwork. */
    val lg: Dp = 28.dp

    /**
     * Navigation rail and bottom bar glyphs.
     *
     * A step above [md]: both navigations are icon-only, so the glyph alone
     * carries the section and needs more weight than an in-row affordance.
     */
    val nav: Dp = lg
}

/** Reusable overlay alphas, so scrims and shadows stay consistent. */
object AppAlpha {
    /** Top scrim that keeps the detail back button legible over any content. */
    const val DETAIL_TOP_SCRIM = 0.55f

    /** Secondary hero action background. */
    const val HERO_ACTION = 0.18f

    /**
     * Flat dim kept over the hero artwork, so it reads darker than the raw
     * image. Applied as the terminal stop of the hero gradient.
     */
    const val HERO_IMAGE_DIM = 0.30f

    /** Readability scrim over media, from the strong left edge to the soft right edge. */
    const val MEDIA_SCRIM_STRONG = 0.85f
    const val MEDIA_SCRIM_MEDIUM = 0.35f

    /** Hero synopsis copy over artwork, lighter than body copy. */
    const val HERO_SYNOPSIS = 0.92f

    /** Opacity of an action whose real state is still being loaded. */
    const val ACTION_LOADING = 0.35f

    /** Mid stop of the hero's bottom fade into the page background. */
    const val HERO_BOTTOM_MID = 0.55f

    /** Top fade that keeps the window controls legible over artwork. */
    const val HERO_TOP = 0.45f

    /** Player top and bottom control scrims. */
    const val PLAYER_SCRIM = 0.8f

    /** Flat wash over the player's pre-roll artwork, so a bright backdrop cannot wash out the logo. */
    const val PLAYER_SPLASH = 0.55f

    /** Deeper bottom edge of that wash, so the splash never meets the video on a hard line. */
    const val PLAYER_SPLASH_BOTTOM = 0.85f

    /** Secondary line of the player header (episode code and title). */
    const val PLAYER_TEXT_SECONDARY = 0.72f

    /** Unplayed part of the player's seek and volume tracks. */
    const val PLAYER_TRACK = 0.28f

    /** Interaction feedback for custom surfaces. */
    const val INTERACT_HOVER = 0.08f
    const val INTERACT_FOCUS = 0.18f
    const val INTERACT_PRESSED = 0.16f

    /** Disabled fill and content, per Material's disabled state spec. */
    const val DISABLED_CONTAINER = 0.12f
    const val DISABLED_CONTENT = 0.38f

    /** Translucent fill behind the selected top-navigation item. */
    const val NAV_SELECTED = 0.12f

    /**
     * Tint of the open settings category on its card.
     *
     * Kept light on purpose: the card fill is already a warm tone, so a stronger
     * accent reads as brown instead of as a highlight.
     */
    const val SELECTED_CARD = 0.07f
}

/**
 * Prebuilt scrims.
 *
 * Defined once so a gradient can be tuned in a single place and reused by any
 * screen that needs the same readability treatment.
 */
object AppScrim {
    /** Left-to-right scrim that keeps copy readable over bright artwork. */
    val mediaHorizontal: Brush =
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = AppAlpha.MEDIA_SCRIM_STRONG),
            0.45f to Color.Black.copy(alpha = AppAlpha.MEDIA_SCRIM_MEDIUM),
            0.8f to Color.Transparent,
        )

    /** Bottom fade to the page background, dark enough for copy to sit on any artwork. */
    val mediaBottom: Brush
        @Composable get() =
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.4f to Color.Black.copy(alpha = AppAlpha.MEDIA_SCRIM_MEDIUM),
                1f to LocalBrandPalette.current.background,
            )

    /**
     * Bottom fade inside a media card: the card keeps its own edge, unlike
     * [mediaBottom], which dissolves into the page background.
     */
    val cardBottom: Brush =
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.5f to Color.Black.copy(alpha = AppAlpha.MEDIA_SCRIM_MEDIUM),
            1f to Color.Black.copy(alpha = AppAlpha.MEDIA_SCRIM_STRONG),
        )

    /**
     * Wide desktop billboard, left-to-right: opaque over the left column and
     * the copy, then a short fade that dissolves the artwork's left edge into
     * the page background. The fade ends on a flat [AppAlpha.HERO_IMAGE_DIM]
     * over the artwork, so the image reads a touch darker without going black.
     */
    val heroWideLeft: Brush
        @Composable get() {
            val background = LocalBrandPalette.current.background
            return Brush.horizontalGradient(
                0f to background,
                0.25f to background,
                0.45f to background.copy(alpha = AppAlpha.HERO_IMAGE_DIM),
            )
        }

    /**
     * Home billboard, bottom-up: clear over the upper half and a long, soft fade
     * into the page background, so the hero dissolves into the section below
     * without a hard seam. The mid stop keeps the ramp gentle instead of sudden.
     */
    val heroBottom: Brush
        @Composable get() {
            val background = LocalBrandPalette.current.background
            return Brush.verticalGradient(
                0f to Color.Transparent,
                0.45f to Color.Transparent,
                0.78f to background.copy(alpha = AppAlpha.HERO_BOTTOM_MID),
                1f to background,
            )
        }

    /**
     * Phone billboard, the whole wash in one fill.
     *
     * A fitted, centred poster is darkened by the flat [AppAlpha.HERO_IMAGE_DIM]
     * over the middle, darkens further toward the top ([AppAlpha.HERO_TOP], never
     * all the way to the page background, so the cover stays visible), and melts
     * into that background at the bottom, where the next section starts.
     *
     * The three washes this replaces were all the same colour, so they fold into
     * one gradient through the composite alpha `1 - (1 - dim)(1 - top)`: the
     * result is identical, in a single full-screen fill instead of three.
     */
    val heroCompact: Brush
        @Composable get() {
            val background = LocalBrandPalette.current.background
            val dim = AppAlpha.HERO_IMAGE_DIM
            val top = 1f - (1f - dim) * (1f - AppAlpha.HERO_TOP)
            return Brush.verticalGradient(
                0f to background.copy(alpha = top),
                0.30f to background.copy(alpha = dim),
                0.70f to background.copy(alpha = dim),
                0.92f to background,
                1f to background,
            )
        }

    /** Home billboard top fade, subtle enough to keep the window controls legible. */
    val heroTop: Brush
        @Composable get() =
            Brush.verticalGradient(
                0f to LocalBrandPalette.current.background.copy(alpha = AppAlpha.HERO_TOP),
                0.2f to Color.Transparent,
            )

    /** Home billboard right-edge vignette. */
    val heroRightVignette: Brush
        @Composable get() =
            Brush.horizontalGradient(
                0f to Color.Transparent,
                0.7f to Color.Transparent,
                1f to LocalBrandPalette.current.background.copy(alpha = 0.25f),
            )

    /**
     * Dark base behind a full-screen hero.
     *
     * A slow or missing image never leaves a bright gap: whatever the artwork
     * does not cover falls back to this near-black gradient.
     */
    val detailBackdrop: Brush
        @Composable get() {
            val palette = LocalBrandPalette.current
            return Brush.verticalGradient(
                0f to palette.surfaceHigh,
                0.55f to palette.surface,
                1f to palette.background,
            )
        }

    /**
     * Fade along the top of a screen, so a fixed control such as a back button
     * always sits on a dark strip instead of on bright artwork or content.
     */
    val detailTop: Brush =
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = AppAlpha.DETAIL_TOP_SCRIM),
            1f to Color.Transparent,
        )

    /** Top control scrim over the player. */
    val playerTop: Brush =
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = AppAlpha.PLAYER_SCRIM),
            0.5f to Color.Black.copy(alpha = AppAlpha.PLAYER_SCRIM * 0.5f),
            1f to Color.Transparent,
        )

    /** Bottom control scrim over the player: dark under the seek bar, deeper under the row. */
    val playerBottom: Brush =
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = AppAlpha.PLAYER_SCRIM * 0.45f),
            0.35f to Color.Black.copy(alpha = AppAlpha.PLAYER_SCRIM * 0.7f),
            1f to Color.Black.copy(alpha = AppAlpha.PLAYER_SCRIM),
        )

    /**
     * Wash over the player's pre-roll.
     *
     * Flat through the middle, where the logo sits, and deeper at the bottom, so
     * the artwork stays visible while a bright backdrop cannot swallow the logo.
     */
    val playerSplash: Brush =
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = AppAlpha.PLAYER_SPLASH),
            0.6f to Color.Black.copy(alpha = AppAlpha.PLAYER_SPLASH),
            1f to Color.Black.copy(alpha = AppAlpha.PLAYER_SPLASH_BOTTOM),
        )
}

/**
 * Caption styles for the title and secondary metadata shown under a poster.
 *
 * These are Material type roles, so every poster in the app — library grid, home
 * rows, catalog grids and "more like this" — shares the same size and weight
 * hierarchy. Colour is applied at the call site from the colour scheme.
 */
object PosterCaption {
    /** Movie / series title under the artwork. */
    @Composable
    fun title(): TextStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = TITLE_LINE)

    /** Year, release info or episode tag under the title. */
    @Composable
    fun meta(): TextStyle =
        MaterialTheme.typography.bodySmall.copy(
            fontSize = 13.sp,
            lineHeight = META_LINE,
            fontWeight = FontWeight.Medium,
        )

    /**
     * Height a caption block occupies under the artwork: the gap, the title line
     * and the meta line.
     *
     * Measured in `dp` from the caption type, so it tracks the font scale, and
     * kept constant across widths so a phone's narrow cell reserves the same room
     * as a wide one. A ratio would reserve less on a narrow cell, leaving the
     * artwork taller than its 2:3 and cropping its sides.
     */
    @Composable
    fun blockHeight(): Dp {
        val lines = with(LocalDensity.current) { (TITLE_LINE.value + META_LINE.value).sp.toDp() }
        return AppSpacing.sm + lines
    }

    private val TITLE_LINE = 21.sp
    private val META_LINE = 17.sp
}

/**
 * Colours for the desktop window chrome.
 *
 * The custom title bar and the window background behind Compose read the selected
 * [LocalBrandPalette] through these tokens, so the chrome stays one decision next
 * to the caption metrics in [AppSizes]. The composition root draws the bar inside
 * `PenguTheme`, so `MaterialTheme` is available there too.
 */
object AppWindowChrome {
    /** Title-bar background; matches the page background and the rail. */
    val titleBar: Color
        @Composable get() = LocalBrandPalette.current.background

    /** Window background behind Compose, so a resize does not flash. */
    val windowBackground: Color
        @Composable get() = LocalBrandPalette.current.background

    /** Title-bar text and caption-button glyphs. */
    val foreground: Color
        @Composable get() = LocalBrandPalette.current.textPrimary

    /** Caption-button background while hovered or pressed. */
    val captionHover: Color
        @Composable get() = LocalBrandPalette.current.surfaceHigh

    /** Close-button background while hovered or pressed. */
    val captionClose: Color = Color(0xFFC42B1C)

    /** Glyph on the close button while hovered or pressed. */
    val captionCloseForeground: Color = Color.White
}
