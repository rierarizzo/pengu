package com.misgus.pengu.player

import com.misgus.pengu.core.repository.AppSettings
import com.misgus.pengu.core.repository.ToneMapping
import org.openani.mediamp.mpv.MPVHandle

/**
 * The mpv options Pengu exposes in Settings, already spelled the way mpv reads
 * them.
 *
 * A plain value object so the mapping is testable without a native player: the
 * call sites only differ in *when* they apply it — [configurePlaybackOptions] at
 * construction, [applyPlaybackOptions] before each playback.
 *
 * Only options the legacy render path (the libmpv render API) actually honours
 * are exposed: `vo=gpu-next`-only curves such as `spline` are deliberately absent.
 */
internal data class MpvPlaybackOptions(
    /** mpv's HDR tone-mapping curve. `auto` is mpv's own default, not "unset". */
    val toneMapping: String,
    val deband: Boolean,
    val hardwareDecoding: Boolean,
) {
    /** mpv's flag spelling for the debanding filter. */
    val debandValue: String get() = if (deband) "yes" else "no"

    /** `auto` lets mpv pick the API; `no` forces software decoding. */
    val hwdecValue: String get() = if (hardwareDecoding) "auto" else "no"
}

internal fun AppSettings.toMpvPlaybackOptions(): MpvPlaybackOptions =
    MpvPlaybackOptions(
        toneMapping = toneMapping.mpvValue(),
        deband = deband,
        hardwareDecoding = hardwareDecoding,
    )

private fun ToneMapping.mpvValue(): String =
    when (this) {
        ToneMapping.AUTO -> "auto"
        ToneMapping.BT_2390 -> "bt.2390"
        ToneMapping.HABLE -> "hable"
        ToneMapping.MOBIUS -> "mobius"
    }

/**
 * Applies the options at construction, before mpv is initialized.
 *
 * These are the values the very first playback uses, and the ones that survive if
 * the engine refuses a property at runtime.
 */
internal fun MPVHandle.configurePlaybackOptions(options: MpvPlaybackOptions) {
    option("tone-mapping", options.toneMapping)
    option("deband", options.debandValue)
    option("hwdec", options.hwdecValue)
}

/**
 * Applies the options to the current session, before the next file loads.
 *
 * It is what makes a Settings change take effect on the next video instead of the
 * next launch, so it is best-effort: playback must never fail because a
 * preference could not be applied.
 */
internal fun MPVHandle.applyPlaybackOptions(options: MpvPlaybackOptions) {
    setPropertyString("tone-mapping", options.toneMapping)
    setPropertyString("deband", options.debandValue)
    setPropertyString("hwdec", options.hwdecValue)
}
