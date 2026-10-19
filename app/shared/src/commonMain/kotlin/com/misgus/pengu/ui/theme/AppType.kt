package com.misgus.pengu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type roles that step down one size on a compact window.
 *
 * Material's `display*` roles and the largest `headline` sizes are sized for a
 * big screen; on a phone they tower over the layout, so the next role down is
 * used there. Desktop and tablet keep the full scale, so nothing changes for
 * them.
 */
object AppType {
    /** Title of a hero (a movie or series name). */
    @Composable
    fun heroTitle(): TextStyle =
        if (LocalWindowSize.current == WindowSize.COMPACT) {
            MaterialTheme.typography.headlineLarge
        } else {
            MaterialTheme.typography.displayMedium
        }

    /**
     * Hero synopsis and other long copy over artwork.
     *
     * Both billboards share this style, so the description reads at the same
     * weight and size on Home and Detail; the measure is shared by
     * `HeroSynopsis` too. It sits one point under `bodyLarge` (17sp / 15sp on a
     * phone), with a Normal weight, so the teaser reads lighter than body copy.
     */
    @Composable
    fun heroBody(): TextStyle =
        if (LocalWindowSize.current == WindowSize.COMPACT) {
            MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp)
        } else {
            MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp)
        }

    /**
     * Metadata line under a hero title (`Series • 2026 • Drama • ★ 7.8`).
     *
     * Reads one step down the label roles, like the rest of the billboard copy,
     * but in Inter Tight: the line is a dense ribbon, and the tighter fit keeps
     * it to one line under the title on both the home and the detail billboard.
     */
    @Composable
    fun heroMeta(): TextStyle {
        val base =
            if (LocalWindowSize.current == WindowSize.COMPACT) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelLarge
            }
        return base.copy(fontFamily = interTightFontFamily())
    }

    /** Title of a screen: onboarding, profile picker, splash, catalog, settings. */
    @Composable
    fun screenTitle(): TextStyle =
        if (LocalWindowSize.current == WindowSize.COMPACT) {
            MaterialTheme.typography.headlineMedium
        } else {
            MaterialTheme.typography.headlineLarge
        }

    /** Main title of a dialog (e.g. the "Select a stream" sheet). */
    @Composable
    fun dialogTitle(): TextStyle = MaterialTheme.typography.headlineSmall

    /**
     * Heading of a screen section or a content rail.
     *
     * The level below [screenTitle]: Home and Search rails and the detail
     * sections all read it, so two sections never differ in size. Stepped down
     * from `titleLarge` (22sp) so a catalog or section name reads as a row
     * header rather than a page title while staying above [cardTitle]; a phone
     * steps it down one more point (16sp) where the layout is tighter.
     */
    @Composable
    fun sectionTitle(): TextStyle =
        if (LocalWindowSize.current == WindowSize.COMPACT) {
            MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, lineHeight = 22.sp)
        } else {
            MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp)
        }

    /**
     * Heading of a card or a self-contained block, such as an empty state.
     *
     * One step under [sectionTitle]: a card nests inside a screen, so its
     * heading stays smaller than the section it sits in.
     */
    @Composable
    fun cardTitle(): TextStyle = MaterialTheme.typography.titleMedium

    /**
     * Primary line of an interactive row: a list item, a dialog result, a
     * profile name or the media title in the player.
     */
    @Composable
    fun itemTitle(): TextStyle = MaterialTheme.typography.titleMedium

    /** Default reading copy: a value, a name or a paragraph. */
    @Composable
    fun body(): TextStyle = MaterialTheme.typography.bodyLarge

    /** Secondary copy: a description, a helper or an error line. */
    @Composable
    fun bodySecondary(): TextStyle = MaterialTheme.typography.bodyMedium

    /** Small metadata attached to an item (e.g. a poster's secondary line). */
    @Composable
    fun caption(): TextStyle = MaterialTheme.typography.bodySmall

    /** Dense label that is not body copy, such as the player timecode. */
    @Composable
    fun label(): TextStyle = MaterialTheme.typography.labelMedium

    /**
     * Label of a button.
     *
     * Material's `labelLarge` is Medium by default, but the app pins every
     * typography slot to Normal; a button is an action, so it opts back into a
     * heavier weight here instead of at the call site.
     */
    @Composable
    fun buttonLabel(): TextStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)

    /**
     * Label of a primary call to action (the filled and amber buttons).
     *
     * One size up and still SemiBold, so "Stream Now" carries the billboard
     * without shouting like a display heading.
     */
    @Composable
    fun ctaLabel(): TextStyle =
        MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
}
