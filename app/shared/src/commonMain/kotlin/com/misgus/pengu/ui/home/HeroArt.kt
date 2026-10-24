package com.misgus.pengu.ui.home

import com.misgus.pengu.core.model.MetaPreview

/**
 * The artwork the billboard draws for a slide, and how it should fill the frame.
 *
 * [fit] selects a portrait, language-neutral poster shown whole, instead of a
 * backdrop cropped to fill. A phone crops a wide backdrop into a tall cover and
 * cuts the composition apart; the textless poster is already portrait and the
 * compact frame is exactly its size, so it is drawn as-is and only the washes
 * darken its ends.
 */
internal data class HeroArt(
    val url: String,
    val fit: Boolean,
)

/**
 * The artwork the billboard draws for [item].
 *
 * A wide window shows the backdrop it always used. A phone prefers the
 * language-neutral (textless) poster when the provider sends one, fitted at the
 * bottom, and falls back to the old backdrop/poster chain when there is none.
 * The layer and the preloader both go through here, so the preload always warms
 * the URL that is actually drawn.
 */
internal fun heroArtwork(
    item: MetaPreview,
    wide: Boolean,
): HeroArt? {
    val textless = item.textlessPoster.nonEmpty()
    val fallback = (item.background ?: item.poster).nonEmpty() ?: return null
    return when {
        wide -> HeroArt(fallback, fit = false)
        textless != null -> HeroArt(textless, fit = true)
        else -> HeroArt(fallback, fit = false)
    }
}

private fun String?.nonEmpty(): String? = this?.takeIf { it.isNotEmpty() }
