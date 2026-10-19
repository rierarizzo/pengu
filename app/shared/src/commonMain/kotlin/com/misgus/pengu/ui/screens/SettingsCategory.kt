package com.misgus.pengu.ui.screens

import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.settings_account
import com.misgus.pengu.app.shared.generated.resources.settings_account_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_advanced
import com.misgus.pengu.app.shared.generated.resources.settings_advanced_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_group_app
import com.misgus.pengu.app.shared.generated.resources.settings_group_playback
import com.misgus.pengu.app.shared.generated.resources.settings_hero
import com.misgus.pengu.app.shared.generated.resources.settings_hero_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_interface
import com.misgus.pengu.app.shared.generated.resources.settings_interface_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_metadata
import com.misgus.pengu.app.shared.generated.resources.settings_metadata_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_playback
import com.misgus.pengu.app.shared.generated.resources.settings_playback_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_theme
import com.misgus.pengu.app.shared.generated.resources.settings_theme_subtitle
import org.jetbrains.compose.resources.StringResource

/** A titled cluster of [SettingsCategory] entries in the settings sidebar. */
enum class SettingsGroup(
    val label: StringResource,
) {
    PLAYBACK(Res.string.settings_group_playback),
    APP(Res.string.settings_group_app),
}

/**
 * One page of the settings screen.
 *
 * On a wide window the entries fill the sidebar and the selected page renders
 * beside it; on a compact window they are the index the user drills into. Each
 * entry owns a title and a one-line description, so the sidebar and the index
 * row read the same vocabulary without borrowing a glyph.
 *
 * A page never nests a [SettingsCategory]: a finer choice (the manual billboard
 * catalogs, the TMDB key) is a control inside the page, not another level.
 */
enum class SettingsCategory(
    val label: StringResource,
    val subtitle: StringResource,
    val group: SettingsGroup,
) {
    PLAYBACK(
        label = Res.string.settings_playback,
        subtitle = Res.string.settings_playback_subtitle,
        group = SettingsGroup.PLAYBACK,
    ),
    INTERFACE(
        label = Res.string.settings_interface,
        subtitle = Res.string.settings_interface_subtitle,
        group = SettingsGroup.APP,
    ),
    THEME(
        label = Res.string.settings_theme,
        subtitle = Res.string.settings_theme_subtitle,
        group = SettingsGroup.APP,
    ),
    HERO(
        label = Res.string.settings_hero,
        subtitle = Res.string.settings_hero_subtitle,
        group = SettingsGroup.APP,
    ),
    METADATA(
        label = Res.string.settings_metadata,
        subtitle = Res.string.settings_metadata_subtitle,
        group = SettingsGroup.APP,
    ),
    ACCOUNT(
        label = Res.string.settings_account,
        subtitle = Res.string.settings_account_subtitle,
        group = SettingsGroup.APP,
    ),
    ADVANCED(
        label = Res.string.settings_advanced,
        subtitle = Res.string.settings_advanced_subtitle,
        group = SettingsGroup.APP,
    ),
    ;

    companion object {
        /** Categories by [SettingsGroup], in declaration order. */
        val grouped: List<Pair<SettingsGroup, List<SettingsCategory>>> =
            SettingsGroup.entries.map { group -> group to SettingsCategory.entries.filter { it.group == group } }
    }
}
