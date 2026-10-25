package com.misgus.pengu.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.detail_episode_code
import com.misgus.pengu.app.shared.generated.resources.player_audio
import com.misgus.pengu.app.shared.generated.resources.player_error
import com.misgus.pengu.app.shared.generated.resources.player_progress_error
import com.misgus.pengu.app.shared.generated.resources.player_subtitles
import com.misgus.pengu.app.shared.generated.resources.player_track_off
import com.misgus.pengu.core.model.ContentTypes
import com.misgus.pengu.core.model.MetaVideo
import com.misgus.pengu.core.model.Subtitle
import com.misgus.pengu.core.player.MediaTrack
import com.misgus.pengu.core.player.PlaybackTarget
import com.misgus.pengu.core.repository.LastStream
import com.misgus.pengu.core.util.runCatchingCancellable
import com.misgus.pengu.ui.platform.PlayerWindowMode
import com.misgus.pengu.ui.platform.isDesktop
import com.misgus.pengu.ui.services.AppServices
import com.misgus.pengu.ui.services.LocalAppServices
import com.misgus.pengu.ui.shell.LocalSnackbarHost
import com.misgus.pengu.ui.theme.AppColors
import com.misgus.pengu.ui.video.VideoSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Integrated player: custom controls, track selection, fullscreen, autoplay of
 * the next episode and watch-progress reporting.
 */
@Composable
fun PlayerScreen(
    target: PlaybackTarget,
    onBack: () -> Unit,
    onPlayNext: (PlaybackTarget) -> Unit = {},
) {
    val services = LocalAppServices.current
    val player = services.player
    val chrome = services.chrome
    val window = services.window
    val snackbar = LocalSnackbarHost.current
    val state by player.state.collectAsStateWithLifecycle()
    val reporter = remember(target) { PlaybackProgressReporter(services.progress, services.settings, target) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // The player owns the screen on Android (landscape + immersive); desktop
    // keeps its windowed, decorated frame.
    PlayerWindowMode()

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    var addonSubtitles by remember { mutableStateOf<List<Subtitle>>(emptyList()) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    // Volume to restore when mute is toggled back on.
    var volumeBeforeMute by remember { mutableStateOf(100.0) }
    // Subtitle `C` turns back on; kept here so it does not reopen the menu.
    var lastSubtitleId by remember { mutableStateOf<String?>(null) }
    // Addon subtitles as the menu lists them, resolved here because their label
    // is a composable call and the `C` shortcut is not.
    val addonTracks =
        addonSubtitles.map { subtitle ->
            addonSubtitleTrack(subtitle, subtitleSubtitleTitle(subtitle))
        }

    // The pre-roll owns the wait while the engine opens the stream: it paints the
    // title's artwork, decides when there is a frame worth revealing and hands the
    // controls back once it has dissolved. It is opaque from its first frame, so
    // the push never lands on the bare player background.
    val preRoll = rememberPlayerPreRoll(target, player, services.metadata)
    val splashArt = preRoll.art.value
    val controlsUnlocked = preRoll.controlsReady.value

    // A spinner that appears at once flickers on a stream that opens in a moment,
    // so it waits for a buffering spell that lasts.
    var showBuffering by remember { mutableStateOf(false) }
    LaunchedEffect(state.buffering) {
        showBuffering = false
        if (state.buffering) {
            delay(BUFFERING_INDICATOR_DELAY)
            showBuffering = true
        }
    }

    DisposableEffect(target) {
        chrome.enterImmersive()
        player.open(target)
        onDispose {
            chrome.exitImmersive()
            // The last position has to survive the screen: this composable's own
            // scope is cancelled as it leaves, which used to drop the final write
            // (and Continue watching never saw it). The save runs on the app scope
            // and ticks the update only once it has landed, so the screens that
            // show progress repaint from the data that was actually stored.
            services.appScope.launch {
                reporter.flush(force = true)
                // A position that could not be stored is worth saying out loud:
                // the row simply not updating is indistinguishable from not having
                // watched anything.
                reporter.lastFailure?.let { failure ->
                    snackbar?.showSnackbar(getString(Res.string.player_progress_error, failure.message ?: ""))
                }
                services.playbackUpdates.notifyClosed()
            }
            player.stop()
        }
    }

    // Leaving the player always returns to a windowed window, so the navigation
    // comes back. Keyed on the screen, not the target, so autoplay-next does not
    // drop fullscreen between episodes.
    DisposableEffect(Unit) {
        onDispose { window.setFullScreen(false) }
    }

    // The player owns the screen: the OS must not idle the display out during a
    // film, and the pointer follows the overlay. Both are no-ops on a touch
    // screen, where the display flag lives in the Android window instead.
    DisposableEffect(window) {
        window.setScreenAwake(true)
        onDispose {
            window.setScreenAwake(false)
            window.setCursorHidden(false)
        }
    }

    LaunchedEffect(controlsVisible) { window.setCursorHidden(!controlsVisible) }

    LaunchedEffect(target) {
        reporter.resumePosition()?.let { position ->
            // Wait until the media is parsed, then jump to the saved position.
            // A timeout keeps a stream that never reports a duration from
            // leaving the resume pending forever.
            withTimeoutOrNull(RESUME_TIMEOUT) {
                player.state.first { it.duration > Duration.ZERO }
            }
            player.seek(position)
        }
        val id = target.videoId ?: target.contentId
        val type = target.contentType
        if (id != null && type != null) {
            addonSubtitles =
                runCatchingCancellable {
                    services.subtitles.all(type, id)
                }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(player) {
        player.state.collect { snapshot ->
            reporter.update(snapshot.position, snapshot.duration)
            if (!snapshot.playing || snapshot.completed) reporter.flush(force = true) else reporter.flush()
            if (snapshot.completed && target.videoId != null && services.settings.state.value.autoPlayNext) {
                playNextIfPossible(target, services)?.let(onPlayNext)
            }
        }
    }

    // Auto-hide the controls after 3 seconds of playback.
    LaunchedEffect(state.playing, lastInteraction, controlsUnlocked) {
        if (!state.playing) {
            controlsVisible = true
            return@LaunchedEffect
        }
        // The clock starts once the pre-roll is over, so the controls are not
        // already halfway to hiding when the video is revealed.
        if (!controlsUnlocked) return@LaunchedEffect
        controlsVisible = true
        delay(CONTROLS_AUTO_HIDE)
        controlsVisible = false
    }

    val revealControls = {
        lastInteraction = lastInteraction + 1
        controlsVisible = true
    }

    // Shared by the volume glyph and the `M` shortcut, so both remember the
    // level to restore when mute is toggled back on.
    val toggleMute = {
        if (state.volume > 0.0) {
            volumeBeforeMute = state.volume
            player.setVolume(0.0)
        } else {
            player.setVolume(volumeBeforeMute.coerceAtLeast(1.0))
        }
        revealControls()
    }

    // `C`: off remembers the track so the next press restores that one instead of
    // the first in the list.
    val toggleSubtitles = {
        val current = state.selectedSubtitleId
        if (current != null) {
            lastSubtitleId = current
            player.selectSubtitleTrack(null)
        } else {
            val tracks = state.subtitleTracks + addonTracks
            (tracks.firstOrNull { it.id == lastSubtitleId } ?: tracks.firstOrNull())
                ?.let(player::selectSubtitleTrack)
        }
        revealControls()
    }

    // `N` resolves the next episode the same way autoplay does, so a series can
    // be continued without waiting for the end or turning autoplay on.
    val playNext = {
        if (target.videoId != null) {
            scope.launch { playNextIfPossible(target, services)?.let(onPlayNext) }
        }
        revealControls()
    }

    val heading = playerHeading(target)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppColors.playerBackground)
                .focusRequester(focusRequester)
                .focusable()
                .pointerInput(Unit) {
                    // Moving the pointer reveals the controls, which then auto-hide
                    // again. A press is left to the surface below: on a desktop it
                    // toggles playback, on a phone it toggles the overlay.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Move) {
                                lastInteraction = lastInteraction + 1
                                controlsVisible = true
                            }
                        }
                    }
                }.onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    SEEK_PERCENT_KEYS[event.key]?.let { fraction ->
                        if (state.duration > Duration.ZERO) player.seek(state.duration * fraction)
                        revealControls()
                        return@onPreviewKeyEvent true
                    }
                    // Shift turns an arrow seek into a long skip, the way every
                    // desktop player reads it.
                    val seekBack = if (event.isShiftPressed) SEEK_BACK_LONG else SEEK_BACK
                    val seekForward = if (event.isShiftPressed) SEEK_FORWARD_LONG else SEEK_FORWARD
                    when (event.key) {
                        Key.Spacebar, Key.K, Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.MediaPlayPause -> {
                            player.playOrPause()
                            revealControls()
                            true
                        }
                        Key.DirectionLeft, Key.J -> {
                            player.seekBy(seekBack)
                            revealControls()
                            true
                        }
                        Key.DirectionRight, Key.L -> {
                            player.seekBy(seekForward)
                            revealControls()
                            true
                        }
                        Key.DirectionUp -> {
                            player.setVolume((state.volume + VOLUME_STEP).coerceAtMost(100.0))
                            revealControls()
                            true
                        }
                        Key.DirectionDown -> {
                            player.setVolume((state.volume - VOLUME_STEP).coerceAtLeast(0.0))
                            revealControls()
                            true
                        }
                        Key.M -> {
                            toggleMute()
                            true
                        }
                        Key.C -> {
                            toggleSubtitles()
                            true
                        }
                        Key.N, Key.MediaNext -> {
                            playNext()
                            true
                        }
                        Key.F -> {
                            if (window.supportsFullScreen) {
                                window.setFullScreen(!window.isFullScreen.value)
                                true
                            } else {
                                false
                            }
                        }
                        Key.Escape, Key.Backspace -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                },
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        VideoSurface(player, Modifier.fillMaxSize())
        // The video is the click target. A desktop click is the play/pause
        // toggle; a phone tap shows the overlay and the next tap hides it. It
        // sits under the controls, so a click on a button still belongs to it.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures {
                    when {
                        isDesktop -> {
                            player.playOrPause()
                            lastInteraction = lastInteraction + 1
                            controlsVisible = true
                        }
                        controlsVisible -> controlsVisible = false
                        else -> {
                            lastInteraction = lastInteraction + 1
                            controlsVisible = true
                        }
                    }
                }
            },
        )
        if (showBuffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        PlayerControls(
            state =
                PlayerControlsState(
                    title = heading.title,
                    subtitle = heading.subtitle,
                    playing = state.playing,
                    position = state.position,
                    duration = state.duration,
                    volume = state.volume,
                    subtitlesOn = state.selectedSubtitleId != null,
                    // The pre-roll owns the screen until it dissolves, so the
                    // controls enter after the reveal instead of with it.
                    visible = controlsVisible && controlsUnlocked,
                ),
            onPlayPause = {
                player.playOrPause()
                revealControls()
            },
            onSeek = {
                player.seek(it)
                revealControls()
            },
            onSeekBy = {
                player.seekBy(it)
                revealControls()
            },
            onVolumeChanged = {
                player.setVolume(it)
                revealControls()
            },
            onToggleMute = toggleMute,
            onToggleFullscreen =
                if (window.supportsFullScreen) {
                    { window.setFullScreen(!window.isFullScreen.value) }
                } else {
                    null
                },
            onSubtitles = { showSubtitleMenu = true },
            onAudio = { showAudioMenu = true },
            onBack = onBack,
        )

        state.error?.let { error ->
            val message = stringResource(Res.string.player_error, error)
            LaunchedEffect(error) {
                snackbar?.showSnackbar(message)
                // A remembered URL that will not play is dropped, so the next
                // attempt goes through the picker instead of failing the same way
                // (debrid links expire and rotate).
                target.contentId?.let { contentId ->
                    runCatchingCancellable { services.streamCache.remove(contentId, target.videoId) }
                }
            }
        }

        // Drawn last, over the video and the controls, and not hit-testable: the
        // pre-roll owns the screen until it has dissolved.
        if (!preRoll.gone.value) {
            PlayerSplash(
                title = heading.title,
                backdrop = splashArt?.backdrop,
                logo = splashArt?.logo,
                alpha = { preRoll.alpha.value },
            )
        }
    }

    if (showSubtitleMenu) {
        TrackMenu(
            title = stringResource(Res.string.player_subtitles),
            rows =
                buildList {
                    add(
                        TrackRow(label = stringResource(Res.string.player_track_off), selected = state.selectedSubtitleId == null) {
                            player.selectSubtitleTrack(null)
                        },
                    )
                    state.subtitleTracks.forEach { track ->
                        add(
                            TrackRow(
                                label = trackLabel(track),
                                selected = state.selectedSubtitleId == track.id,
                            ) { player.selectSubtitleTrack(track) },
                        )
                    }
                    addonSubtitles.forEach { subtitle ->
                        val track = addonSubtitleTrack(subtitle, subtitleSubtitleTitle(subtitle))
                        add(
                            TrackRow(
                                label = subtitleTrackLabel(subtitle),
                                detail = subtitleDetail(subtitle),
                                selected = state.selectedSubtitleId == subtitle.url,
                            ) { player.selectSubtitleTrack(track) },
                        )
                    }
                },
            onDismiss = { showSubtitleMenu = false },
        )
    }

    if (showAudioMenu) {
        TrackMenu(
            title = stringResource(Res.string.player_audio),
            rows =
                state.audioTracks.map { track ->
                    TrackRow(
                        label = trackLabel(track),
                        selected = state.selectedAudioId == track.id,
                    ) { player.selectAudioTrack(track) }
                },
            onDismiss = { showAudioMenu = false },
        )
    }
}

/** The track an addon's subtitle row plays: its URL is the track id. */
private fun addonSubtitleTrack(
    subtitle: Subtitle,
    title: String,
): MediaTrack =
    MediaTrack(
        id = subtitle.url,
        title = title,
        language = subtitle.langCode ?: subtitle.lang,
        uri = subtitle.url,
        isExternal = true,
    )

/** Best-effort autoplay: finds the next episode and its first direct stream. */
private suspend fun playNextIfPossible(
    target: PlaybackTarget,
    services: AppServices,
): PlaybackTarget? {
    val seriesId = target.contentId ?: return null
    return runCatchingCancellable {
        val meta = services.metadata.detail(ContentTypes.SERIES, seriesId) ?: return@runCatchingCancellable null
        val episodes =
            meta.videos
                .filter { (it.season?.number ?: 0) > 0 && (it.episode?.number ?: 0) > 0 }
                .sortedBy { episodeOrder(it) }
        val index = episodes.indexOfFirst { it.season == target.season && it.episode == target.episode }
        if (index < 0 || index + 1 >= episodes.size) return@runCatchingCancellable null
        val next = episodes[index + 1]
        // The addon's own episode id: an anime addon names it `kitsu:43248:2`, so
        // rebuilding it from the title id would ask for the wrong thing.
        val groups = services.streams.all(ContentTypes.SERIES, next.id)
        val stream =
            groups.firstNotNullOfOrNull { group ->
                group.streams.firstOrNull { it.isDirectPlayable }
            } ?: return@runCatchingCancellable null
        val url = stream.url ?: return@runCatchingCancellable null
        val episodeCode = getString(Res.string.detail_episode_code, next.season?.number ?: 0, next.episode?.number ?: 0)
        // Autoplay resolves a stream of its own, so it writes the same record the
        // picker does; otherwise the next resume would always need the picker.
        val resolvedAt = Clock.System.now()
        runCatchingCancellable {
            services.streamCache.save(
                LastStream(
                    contentId = seriesId,
                    videoId = next.id,
                    url = url,
                    httpHeaders = stream.httpHeaders.orEmpty(),
                    resolvedAt = resolvedAt,
                    episodeTitle = next.title,
                ),
            )
        }
        PlaybackTarget.fromContent(
            type = ContentTypes.SERIES,
            id = next.id,
            title = "${meta.name} · $episodeCode",
            source = url,
            httpHeaders = stream.httpHeaders,
            season = next.season,
            episode = next.episode,
            contentId = seriesId,
            episodeTitle = next.title,
        )
    }.getOrNull()
}

private fun episodeOrder(video: MetaVideo): Int = (video.season?.number ?: 0) * 1000 + (video.episode?.number ?: 0)

/** The player header: a main title and the optional episode line under it. */
private data class PlayerHeading(
    val title: String,
    val subtitle: String?,
)

/**
 * Splits a playback target into the header's two lines.
 *
 * An episode target is titled `Show · Episode` by the stream picker, while the
 * episode code lives on the target; the header shows the show on the first line
 * and `S01E01 · Episode` on the second, so a long show name never pushes the
 * episode out of view. A movie or a raw source has no second line.
 */
@Composable
private fun playerHeading(target: PlaybackTarget): PlayerHeading {
    val season = target.season ?: return PlayerHeading(target.title, null)
    val episode = target.episode ?: return PlayerHeading(target.title, null)
    val parts = target.title.split(TITLE_SEPARATOR, limit = 2)
    val show = parts.first().ifBlank { target.title }
    val code = stringResource(Res.string.detail_episode_code, season.number, episode.number)
    // Autoplay-next titles carry the code as their second part, so it is not
    // repeated in front of the episode name.
    val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() && it != code }
    return PlayerHeading(show, if (name == null) code else "$code$TITLE_SEPARATOR$name")
}

/** Separator the stream picker uses between a show and its episode. */
private const val TITLE_SEPARATOR = " · "

/** How long to wait for a parsed duration before resuming anyway. */
private val RESUME_TIMEOUT = 5.seconds

/** Idle time before the controls auto-hide during playback, in milliseconds. */
private const val CONTROLS_AUTO_HIDE = 3_000L

/** Buffering shorter than this shows no spinner, so quick starts stay clean. */
private val BUFFERING_INDICATOR_DELAY = 300.milliseconds

/** Volume change applied by the up/down arrow shortcuts, in percent. */
private const val VOLUME_STEP = 5.0

/**
 * Number-key seek targets: `0`…`9` jump to 0 %…90 % of the media, the
 * convention used by the major streaming players.
 */
private val SEEK_PERCENT_KEYS =
    mapOf(
        Key.Zero to 0.0,
        Key.One to 0.1,
        Key.Two to 0.2,
        Key.Three to 0.3,
        Key.Four to 0.4,
        Key.Five to 0.5,
        Key.Six to 0.6,
        Key.Seven to 0.7,
        Key.Eight to 0.8,
        Key.Nine to 0.9,
    )
