package com.misgus.pengu.ui.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.misgus.pengu.ui.components.MediaTitle
import com.misgus.pengu.ui.components.RemoteImage
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.theme.AppLogoSize
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.AppScrim
import com.misgus.pengu.ui.theme.LocalMotionEnabled
import com.misgus.pengu.ui.theme.motionLayer
import com.misgus.pengu.ui.theme.motionTween

/**
 * What the player paints while the engine opens the stream.
 *
 * It is the title's own backdrop with its logo in the middle, so the wait reads
 * as that title loading instead of as a black screen. The logo breathes — it
 * starts dim and clears, then reverses — because a still logo gives no sign of
 * progress and a spinner would add a third element to a screen that already
 * carries the artwork. No label is drawn: the artwork is the identification.
 *
 * It is also the player's entrance: the splash is on screen from the first frame
 * and only its backdrop settles in, so choosing a stream lands on the title's
 * artwork instead of fading up from black.
 *
 * @param title falls back to the name when the metadata has no logo.
 * @param backdrop the title's wide artwork; the detail screen just painted the
 *   same URL, so this is normally a cache hit.
 * @param logo the title logo, or `null` to fall back to the name.
 * @param alpha fade-out driven by the caller, so the splash dissolves into the
 *   video it was hiding. The same value drives the recede, so the dissolve has a
 *   timeline without one of its own.
 */
@Composable
fun PlayerSplash(
    title: String,
    backdrop: String?,
    logo: String?,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    // The backdrop is the one thing that settles: it fades and zooms in a touch
    // while the engine opens the stream. Read in the draw phase below, so the
    // settle never recomposes the artwork. Keyed on the URL, so the next title
    // settles too while a plain recomposition does not replay it.
    var settled by remember(backdrop) { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    val settle =
        animateFloatAsState(
            targetValue = if (settled) 1f else 0f,
            animationSpec = motionTween(durationMillis = ARTWORK_SETTLE_MILLIS, easing = AppMotion.emphasizedDecelerate),
            label = "splashSettle",
        )
    val pulse = rememberLogoPulse()

    Box(modifier = modifier.fillMaxSize().background(AppColors.playerBackground)) {
        // The whole splash recedes as it dissolves, so the video arrives from
        // behind the artwork instead of through a plain alpha ramp.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .motionLayer(
                        scale = { 1f + EXIT_SCALE * (1f - alpha()) },
                        alpha = alpha,
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .motionLayer(scale = { ARTWORK_START_SCALE + (1f - ARTWORK_START_SCALE) * settle.value }),
            ) {
                RemoteImage(
                    url = backdrop,
                    contentDescription = null,
                    fadeIn = true,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(AppScrim.playerSplash))
            }
            // A raw source (the `--play` shortcut) has no metadata, so there is no
            // artwork and no logo: showing its URL as a title would be worse than
            // the plain black frame the player always had.
            if (backdrop != null || logo != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // The pulse layer wraps the logo instead of the window, so the
                    // animated alpha only buffers the glyph.
                    Box(Modifier.motionLayer(alpha = { pulse.value })) {
                        MediaTitle(
                            name = title,
                            logo = logo,
                            logoHeight = AppLogoSize.splashHeight,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The logo's loading pulse: dim to solid and back, forever.
 *
 * Returned as a [State] instead of an animated value so callers read it inside
 * their layer block (the draw phase) and the pulse repaints without recomposing.
 * When motion is off it is a plain `1f`, so nothing spins for nothing.
 */
@Composable
private fun rememberLogoPulse(): State<Float> {
    if (!LocalMotionEnabled.current) return remember { mutableStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "splashLogo")
    return transition.animateFloat(
        initialValue = LOGO_DIM_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOGO_PULSE_MILLIS), RepeatMode.Reverse),
        label = "splashLogoAlpha",
    )
}

/**
 * Scale the backdrop starts at and settles from.
 *
 * Three percent is enough to read as motion at screen size while never showing
 * an edge, and it costs a layer transform rather than a relayout.
 */
private const val ARTWORK_START_SCALE = 1.03f

/** How long the backdrop takes to settle, in milliseconds. */
private const val ARTWORK_SETTLE_MILLIS = 700

/**
 * Scale the splash recedes to as it dissolves.
 *
 * Derived from the fade, so the recede and the alpha ramp share one timeline: the
 * artwork pushes back while the video comes through it.
 */
private const val EXIT_SCALE = 0.05f

/** Opacity the pulse starts from: dim enough to read as loading, not as missing. */
private const val LOGO_DIM_ALPHA = 0.45f

/** One direction of the logo pulse, in milliseconds. */
private const val LOGO_PULSE_MILLIS = 1_100
