package com.misgus.pengu.ui.screens

import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.settings_about
import com.misgus.pengu.app.shared.generated.resources.settings_about_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_account
import com.misgus.pengu.app.shared.generated.resources.settings_account_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_addons
import com.misgus.pengu.app.shared.generated.resources.settings_addons_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_advanced
import com.misgus.pengu.app.shared.generated.resources.settings_advanced_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_appearance
import com.misgus.pengu.app.shared.generated.resources.settings_appearance_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_group_about
import com.misgus.pengu.app.shared.generated.resources.settings_group_account
import com.misgus.pengu.app.shared.generated.resources.settings_group_advanced
import com.misgus.pengu.app.shared.generated.resources.settings_group_general
import com.misgus.pengu.app.shared.generated.resources.settings_home
import com.misgus.pengu.app.shared.generated.resources.settings_home_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_metadata
import com.misgus.pengu.app.shared.generated.resources.settings_metadata_subtitle
import com.misgus.pengu.app.shared.generated.resources.settings_playback
import com.misgus.pengu.app.shared.generated.resources.settings_playback_subtitle
import org.jetbrains.compose.resources.StringResource

/** A titled cluster of [SettingsCategory] entries in the settings sidebar. */
enum class SettingsGroup(
    val label: StringResource,
) {
    ACCOUNT(Res.string.settings_group_account),
    GENERAL(Res.string.settings_group_general),
    ABOUT(Res.string.settings_group_about),
    ADVANCED(Res.string.settings_group_advanced),
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
        group = SettingsGroup.GENERAL,
    ),
    APPEARANCE(
        label = Res.string.settings_appearance,
        subtitle = Res.string.settings_appearance_subtitle,
        group = SettingsGroup.GENERAL,
    ),
    HOME(
        label = Res.string.settings_home,
        subtitle = Res.string.settings_home_subtitle,
        group = SettingsGroup.GENERAL,
    ),
    ADDONS(
        label = Res.string.settings_addons,
        subtitle = Res.string.settings_addons_subtitle,
        group = SettingsGroup.GENERAL,
    ),
    METADATA(
        label = Res.string.settings_metadata,
        subtitle = Res.string.settings_metadata_subtitle,
        group = SettingsGroup.GENERAL,
    ),
    ACCOUNT(
        label = Res.string.settings_account,
        subtitle = Res.string.settings_account_subtitle,
        group = SettingsGroup.ACCOUNT,
    ),
    ABOUT(
        label = Res.string.settings_about,
        subtitle = Res.string.settings_about_subtitle,
        group = SettingsGroup.ABOUT,
    ),
    ADVANCED(
        label = Res.string.settings_advanced,
        subtitle = Res.string.settings_advanced_subtitle,
        group = SettingsGroup.ADVANCED,
    ),
    ;

    companion object {
        /** Categories by [SettingsGroup], in declaration order. */
        val grouped: List<Pair<SettingsGroup, List<SettingsCategory>>> =
            SettingsGroup.entries.map { group -> group to SettingsCategory.entries.filter { it.group == group } }
    }
}
