package com.misgus.pengu.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.core.player.PlayerService
import com.misgus.pengu.core.player.PlayerState
import com.misgus.pengu.core.repository.MetadataRepository
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.ui.theme.AppMotion
import com.misgus.pengu.ui.theme.LocalMotionEnabled
import com.misgus.pengu.ui.theme.motionTween
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The piece of the title the pre-roll paints: its wide artwork and its logo. */
data class SplashArtwork(
    val backdrop: String?,
    val logo: String?,
)

/**
 * Everything the player's pre-roll drives, in one place.
 *
 * `PlayerScreen` owns the video, the controls and the lifecycle; the pre-roll owns
 * the wait — when to reveal, what to paint, and when the controls may come back.
 * Keeping that here leaves one effect per concern and a single place to tune the
 * timings, instead of five related states inside the screen.
 */
class PlayerPreRoll internal constructor(
    /** The title's artwork, or `null` while it loads and when the title has none. */
    val art: State<SplashArtwork?>,
    /** Alpha of the splash; it is driven to `0` as the pre-roll dissolves. */
    val alpha: State<Float>,
    /** True once the splash has dissolved and left the screen. */
    val gone: State<Boolean>,
    /** True once the controls may appear, which is after the dissolve clears. */
    val controlsReady: State<Boolean>,
)

/**
 * Drives the pre-roll for [target] and remembers it across recompositions.
 *
 * The reveal waits for real evidence that the video is playable — a painted frame
 * plus lookahead in the engine's cache — for at least [PRE_ROLL_MIN_DELAY] and at
 * most [PRE_ROLL_DEADLINE]. Whichever comes first wins: a source that cannot
 * buffer the lookahead reveals at the deadline anyway, so holding the artwork
 * longer only delays it with no gain. An engine error cuts the wait short.
 */
@Composable
fun rememberPlayerPreRoll(
    target: PlaybackTarget,
    player: PlayerService,
    metadata: MetadataRepository,
): PlayerPreRoll {
    // The dissolve collapses to zero when motion is off, so nothing waits on an
    // animation that will never play.
    val dissolveMillis = AppMotion.spec<Float>(LocalMotionEnabled.current, AppMotion.DISSOLVE).durationMillis
    val dissolve = dissolveMillis.milliseconds
    val controlsDelay = minOf(CONTROLS_REVEAL_DELAY, dissolve)

    val art = remember(target) { mutableStateOf<SplashArtwork?>(null) }
    val visible = remember(target) { mutableStateOf(true) }
    val gone = remember(target) { mutableStateOf(false) }
    val controlsReady = remember(target) { mutableStateOf(false) }

    val alpha =
        // Keyed so a new target starts fully visible: autoplay-next reuses this
        // composition, and the previous title left the value at zero.
        key(target) {
            animateFloatAsState(
                targetValue = if (visible.value) 1f else 0f,
                animationSpec = motionTween(durationMillis = AppMotion.DISSOLVE, easing = AppMotion.emphasizedAccelerate),
                label = "preRollAlpha",
            )
        }

    // Best-effort artwork. The screen the user came from just painted the same
    // URLs, so this is normally a warm HTTP cache hit; it also covers autoplay-next
    // and a target opened straight from a link, which never visited the detail
    // screen. Without it the pre-roll is the plain black frame the player had.
    LaunchedEffect(target) {
        val type = target.contentType ?: return@LaunchedEffect
        val id = target.contentId ?: return@LaunchedEffect
        val meta =
            runCatchingCancellable {
                metadata.detail(type, id)
            }.getOrNull() ?: return@LaunchedEffect
        art.value = SplashArtwork(backdrop = meta.background ?: meta.poster, logo = meta.logo)
    }

    LaunchedEffect(target) {
        val started = TimeSource.Monotonic.markNow()
        withTimeoutOrNull(PRE_ROLL_DEADLINE) {
            player.state.first { it.preRollReady() || it.error != null }
        }
        val remaining = PRE_ROLL_MIN_DELAY - started.elapsedNow()
        if (remaining > Duration.ZERO) delay(remaining)
        visible.value = false
    }

    // The splash leaves the composition once it has dissolved, so its logo pulse
    // stops instead of animating behind the rest of the playback, and the controls
    // come back behind the dissolve instead of with it.
    LaunchedEffect(visible.value, target) {
        if (visible.value) return@LaunchedEffect
        delay(controlsDelay)
        controlsReady.value = true
        delay(dissolve - controlsDelay)
        gone.value = true
    }

    return PlayerPreRoll(art = art, alpha = alpha, gone = gone, controlsReady = controlsReady)
}

/**
 * Whether the pre-roll has something worth revealing.
 *
 * A painted frame is the real requirement: a parsed duration arrives before the
 * engine has a picture, so gating on the duration revealed onto a black frame. On
 * top of that the engine's lookahead is required when it reports one, so the video
 * does not open into an immediate re-buffer. Bounded media only — a live stream
 * sits at the buffer edge by definition, and asking it for lookahead would hold
 * the splash until the deadline. An engine without a cache to fill (a local file)
 * only needs the frame.
 */
private fun PlayerState.preRollReady(): Boolean {
    if (buffering || position <= Duration.ZERO) return false
    if (duration <= Duration.ZERO) return true
    val buffered = bufferedPosition ?: return true
    // A clip shorter than the lookahead can never have that much ahead of it.
    val wanted = minOf(PRE_ROLL_LOOKAHEAD, duration - position)
    return buffered - position >= wanted
}

/**
 * Shortest spell the pre-roll stays up.
 *
 * A stream that opens in a moment would otherwise flash the artwork for a frame,
 * which reads as a glitch rather than as a title card. Kept short on purpose: the
 * lookahead decides the wait, and this only floors it.
 */
private val PRE_ROLL_MIN_DELAY = 1.seconds

/**
 * Lookahead the pre-roll waits for, when the engine reports its buffer.
 *
 * The point of the pre-roll is that the video starts *playable*: revealing as soon
 * as the media is parsed opened the picture on a source that re-buffered a second
 * later, which is the double loading screen this avoids.
 */
private val PRE_ROLL_LOOKAHEAD = 2.seconds

/**
 * Longest the pre-roll waits for that lookahead.
 *
 * Past this point the buffering indicator is the better answer, because it shows
 * progress over a picture instead of over artwork, and the viewer keeps the
 * controls.
 */
private val PRE_ROLL_DEADLINE = 4.seconds

/** How long the controls wait after the dissolve starts, before appearing. */
private val CONTROLS_REVEAL_DELAY = 250.milliseconds
