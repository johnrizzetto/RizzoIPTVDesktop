package com.rizzoplayer.iptv.ui.player

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.serialization.json.Json
import com.rizzoplayer.iptv.RizzoApp
import com.rizzoplayer.iptv.data.local.PlaybackPositionStore
import com.rizzoplayer.iptv.data.model.ChannelRef
import com.rizzoplayer.iptv.data.model.Favorite
import com.rizzoplayer.iptv.data.local.FavoritesStore
import com.rizzoplayer.iptv.ui.viewmodel.MainViewModel
import com.rizzoplayer.iptv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

private sealed interface TrackItem {
    data class Audio(val label: String, val groupIndex: Int) : TrackItem
    data class Text(val label: String, val groupIndex: Int, val isOff: Boolean = false) : TrackItem
}

class PlayerActivity : ComponentActivity() {

    // Player — created at Activity level for direct key-event access
    private var player: ExoPlayer? = null
    private var isVod = false
    private lateinit var currentUrl: String

    // Content identification for persistent positions
    private var contentId: String = ""
    private var contentType: String = "live"
    private val liveFavoriteIds = mutableStateOf<Set<String>>(emptySet())

    // Episode auto-advance
    private var nextUrl: String = ""
    private var nextTitle: String = ""
    private val showNextEpisode = mutableStateOf(false)
    private val nextCountdown = mutableIntStateOf(5)
    private var countdownJob: Job? = null

    // Overlay state
    private val showResolution    = mutableStateOf(false)
    private val showRecentBar     = mutableStateOf(false)
    private val showTrackPicker: MutableState<Boolean> = mutableStateOf(false)
    private val showVodControls   = mutableStateOf(false)
    private val requestOverlayFocus = mutableStateOf(false)

    // Playback error
    private val playerError = mutableStateOf<String?>(null)
    private var onPlayerError: ((String) -> Unit)? = null

    // Aspect ratio
    private val resizeMode = mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)

    // Jobs
    private var resolutionJob: Job? = null
    private var recentChannelsJob: Job? = null
    private var retryJob: Job? = null
    private var vodControlsJob: Job? = null
    private var positionSaveJob: Job? = null

    // Batch position save — MutableStateFlow polled every 250ms, saves every 5s
    private val _positionFlow = MutableStateFlow(0L)
    private var lastSaveTimeMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        currentUrl  = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title       = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val resumeMs    = intent.getLongExtra(EXTRA_RESUME_MS, 0L)
        contentType     = intent.getStringExtra(EXTRA_CONTENT_TYPE) ?: "live"
        contentId       = intent.getStringExtra(EXTRA_CONTENT_ID) ?: ""
        nextUrl         = intent.getStringExtra(EXTRA_NEXT_URL) ?: ""
        nextTitle       = intent.getStringExtra(EXTRA_NEXT_TITLE) ?: ""
        isVod = contentType != "live"
        val recentJson  = intent.getStringExtra(EXTRA_RECENT_CHANNELS) ?: ""
        val favJson     = intent.getStringExtra(EXTRA_FAVORITE_CHANNELS) ?: ""
        val recentChannels: List<ChannelRef> = parseChannelRefs(recentJson)
        val favoriteChannels: List<ChannelRef> = parseChannelRefs(favJson)

        player = if (isVod) {
            PlayerPool.buildVodPlayer(this)
        } else {
            PlayerPool.acquireLive(this)
        }.apply {
            setMediaItem(MediaItem.fromUri(currentUrl))
                prepare()
                if (resumeMs > 60_000L) {
                    // Let resume dialog choose position; don't auto-seek here
                } else if (resumeMs > 0L) {
                    seekTo(resumeMs)
                }
                playWhenReady = true

                // Episode auto-advance listener
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            savePositionNow()
                            if (nextUrl.isNotEmpty()) {
                                showNextEpisode.value = true
                                nextCountdown.intValue = 5
                                countdownJob?.cancel()
                                countdownJob = lifecycleScope.launch {
                                    for (i in 5 downTo 0) {
                                        nextCountdown.intValue = i
                                        if (i == 0) {
                                            advanceToNextEpisode()
                                            return@launch
                                        }
                                        delay(1_000)
                                    }
                                }
                            }
                        }
                    }
                })
            }

        // Start periodic position tracking + batch save (every 5s)
        startPositionTracking()

        // Subscribe to live favorites from DataStore
        // Read directly from DataStore — PlayerActivity has no parent ViewModel scope
        // to inherit from. Write path still goes through MainViewModel.toggleFavorite().
        lifecycleScope.launch {
            FavoritesStore(this@PlayerActivity).favorites
                .collect { map -> liveFavoriteIds.value = map.keys }
        }

        setContent {
            PlayerScreen(
                player           = player!!,
                isVod            = isVod,
                title            = title,
                resumeMs         = resumeMs,
                recentChannels   = recentChannels,
                favoriteChannels = favoriteChannels,
                contentId        = contentId,
                liveFavoriteIds  = liveFavoriteIds,
                showResolution   = showResolution,
                showRecentBar    = showRecentBar,
                showTrackPicker  = showTrackPicker,
                showVodControls  = showVodControls,
                showNextEpisode  = showNextEpisode,
                nextCountdown    = nextCountdown,
                nextTitle        = nextTitle,
                resizeMode       = resizeMode,
                requestOverlayFocus = requestOverlayFocus,
                playerError        = playerError,
                onDismissError     = ::finish,
                onPlayerError      = { msg -> playerError.value = msg },
                onRetrySetup     = { retryJob = it },
                onBack           = ::finish,
                onVodPlaybackError = ::onVodPlaybackError,
                onSwitchChannel  = { newUrl, _ -> currentUrl = newUrl }
            )
        }
    }

    private fun advanceToNextEpisode() {
        val p = player ?: return
        showNextEpisode.value = false
        countdownJob?.cancel()
        val url = nextUrl
        nextUrl = ""  // Clear — no infinite chain
        nextTitle = ""
        currentUrl = url
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.play()
    }

    private fun onVodPlaybackError() {
        setResult(RESULT_PLAYBACK_ERROR)
        finish()
    }

    private fun savePositionNow() {
        val p = player ?: return
        if (contentId.isEmpty()) return
        if (!isVod) return  // live streams have no meaningful resume position
        val key = "${contentType}:${contentId}"
        val positionMs = p.currentPosition
        val durationMs = p.duration.coerceAtLeast(0)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val app = application as RizzoApp
            app.playbackPositionStore.saveAsync(key, positionMs, durationMs)
            
            // Save to WatchHistoryStore
            if (contentType == "tmdb_movie") {
                app.watchHistoryStore.updateMovieProgress(contentId, positionMs, durationMs)
            } else if (contentType == "tmdb_episode") {
                val parts = contentId.split(":")
                if (parts.size == 3) {
                    val showId = parts[0]
                    val season = parts[1].toIntOrNull() ?: 0
                    val epNum = parts[2].toIntOrNull() ?: 0
                    app.watchHistoryStore.updateSeriesProgress(showId, season, epNum, positionMs, durationMs)
                }
            }
        }
    }

    /** Start periodic position tracking: emit position every 250ms, save every 5s. */
    private fun startPositionTracking() {
        positionSaveJob?.cancel()
        positionSaveJob = lifecycleScope.launch {
            while (true) {
                _positionFlow.value = player?.currentPosition ?: 0L
                val now = System.currentTimeMillis()
                if (now - lastSaveTimeMs >= 5_000) {
                    lastSaveTimeMs = now
                    savePositionNow()
                }
                delay(250)
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parseChannelRefs(data: String): List<ChannelRef> {
        if (data.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<ChannelRef>>(data)
        } catch (_: Exception) { emptyList() }
    }

    // ── Key dispatch ──────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // Intercept BACK during next-episode countdown
            if (event.keyCode == KeyEvent.KEYCODE_BACK && showNextEpisode.value) {
                countdownJob?.cancel()
                showNextEpisode.value = false
                return true
            }
            return if (isVod) handleVodKey(event) else handleLiveKey(event)
        }
        return super.dispatchKeyEvent(event)
    }

    // ── VOD key handling: play/pause, rewind, forward ─────────────────────

    private fun handleVodKey(event: KeyEvent): Boolean {
        val p = player ?: return super.dispatchKeyEvent(event)

        // ── When track picker is open: BACK closes, LEFT/RIGHT pass to Compose LazyRow ──
        if (showTrackPicker.value) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    showTrackPicker.value = false
                    true
                }
                else -> super.dispatchKeyEvent(event)
            }
        }

        when (event.keyCode) {
            // Play / Pause
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (p.isPlaying) p.pause() else p.play()
                showVodControlsBriefly()
                return true
            }
            // Rewind: 10 s on first press, 30 s when held
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val seekMs = if (event.repeatCount > 0) 30_000L else 10_000L
                p.seekTo((p.currentPosition - seekMs).coerceAtLeast(0))
                showVodControlsBriefly()
                return true
            }
            // Forward: 10 s on first press, 30 s when held
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val seekMs = if (event.repeatCount > 0) 30_000L else 10_000L
                p.seekTo(p.currentPosition + seekMs)
                showVodControlsBriefly()
                return true
            }
            // Resolution info
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (showVodControls.value) {
                    showVodControls.value = false
                    vodControlsJob?.cancel()
                }
                resolutionJob?.cancel()
                showResolution.value = true
                resolutionJob = lifecycleScope.launch {
                    delay(3_000)
                    showResolution.value = false
                }
                return true
            }
            // Audio/Subtitle picker
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_MENU -> {
                showTrackPicker.value = !showTrackPicker.value
                return true
            }
            // Keyboard: 1 = seek back 10s, 3 = seek forward 10s
            KeyEvent.KEYCODE_1 -> {
                p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                showVodControlsBriefly()
                return true
            }
            KeyEvent.KEYCODE_3 -> {
                val seekMs = if (event.repeatCount > 0) 30_000L else 10_000L
                p.seekTo(p.currentPosition + seekMs)
                showVodControlsBriefly()
                return true
            }
            // Info: show current position/duration overlay
            KeyEvent.KEYCODE_INFO -> {
                showVodControlsBriefly()
                return true
            }
            // Back
            KeyEvent.KEYCODE_BACK -> {
                if (showVodControls.value) {
                    showVodControls.value = false
                    vodControlsJob?.cancel()
                    return true
                }
                if (showTrackPicker.value) {
                    showTrackPicker.value = false
                    return true
                }
            }
            // Media keys (Bluetooth remotes, etc.)
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (p.isPlaying) p.pause() else p.play()
                showVodControlsBriefly()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                p.play(); showVodControlsBriefly(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                p.pause(); showVodControlsBriefly(); return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                showVodControlsBriefly(); return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                p.seekTo(p.currentPosition + 10_000)
                showVodControlsBriefly(); return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ── Live key handling (existing behaviour) ────────────────────────────

    private fun handleLiveKey(event: KeyEvent): Boolean {

        // ── When track picker is open, only capture BACK — let Compose handle all D-pad navigation ──
        if (showTrackPicker.value) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    showTrackPicker.value = false
                    true
                }
                else -> super.dispatchKeyEvent(event)
            }
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (showRecentBar.value) {
                    // Let Compose handle UP navigation within the overlay (between rows)
                    return super.dispatchKeyEvent(event)
                }
                resolutionJob?.cancel()
                showResolution.value = true
                resolutionJob = lifecycleScope.launch {
                    delay(3_000)
                    showResolution.value = false
                }
                return false
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (showRecentBar.value) {
                    // Let Compose handle DOWN navigation within the overlay
                    return super.dispatchKeyEvent(event)
                }
                // Open overlay — no auto-hide timer, only BACK closes it
                showRecentBar.value = true
                requestOverlayFocus.value = true
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (showRecentBar.value) return super.dispatchKeyEvent(event)
            }
            KeyEvent.KEYCODE_BACK -> {
                // BACK closes overlays
                if (showRecentBar.value) {
                    showRecentBar.value = false
                    return true
                }
            }
            KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_MENU -> {
                showTrackPicker.value = !showTrackPicker.value
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (showRecentBar.value) return super.dispatchKeyEvent(event)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showVodControlsBriefly() {
        showVodControls.value = true
        vodControlsJob?.cancel()
        vodControlsJob = lifecycleScope.launch {
            delay(5_000)
            showVodControls.value = false
        }
    }

    override fun onPause() {
        super.onPause()
        savePositionNow()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    override fun onDestroy() {
        positionSaveJob?.cancel()
        player?.let { p ->
            savePositionNow()
            PlayerPool.release(p)
        }
        player = null
        retryJob?.cancel()
        resolutionJob?.cancel()
        recentChannelsJob?.cancel()
        vodControlsJob?.cancel()
        countdownJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL               = "url"
        const val EXTRA_TITLE             = "title"
        const val EXTRA_CONTENT_TYPE      = "content_type"
        const val EXTRA_CONTENT_ID        = "content_id"
        const val EXTRA_RESUME_MS         = "resume_ms"
        const val EXTRA_RECENT_CHANNELS    = "recent_channels"
        const val EXTRA_FAVORITE_CHANNELS = "favorite_channels"
        const val EXTRA_NEXT_URL          = "next_url"
        const val EXTRA_NEXT_TITLE        = "next_title"
        const val RESULT_PLAYBACK_ERROR     = RESULT_FIRST_USER + 1

        // Speed options (indices into this list)
        val SPEED_OPTIONS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

        // Quality options: null = Auto, Int = max height in px
        val QUALITY_OPTIONS = listOf(null, 2160, 1080, 720, 480)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PLAYER SCREEN
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun PlayerScreen(
    player: ExoPlayer,
    isVod: Boolean,
    title: String,
    resumeMs: Long,
    recentChannels: List<ChannelRef>,
    favoriteChannels: List<ChannelRef>,
    contentId: String,
    liveFavoriteIds: State<Set<String>>,
    showResolution: State<Boolean>,
    showRecentBar: State<Boolean>,
    showTrackPicker: State<Boolean>,
    showVodControls: State<Boolean>,
    showNextEpisode: State<Boolean>,
    nextCountdown: State<Int>,
    nextTitle: String,
    resizeMode: MutableIntState,
    requestOverlayFocus: MutableState<Boolean>,
    onRetrySetup: (Job?) -> Unit,
    onBack: () -> Unit,
    onSwitchChannel: (url: String, title: String) -> Unit,
    playerError: State<String?>,
    onDismissError: () -> Unit,
    onPlayerError: (String) -> Unit,
    onVodPlaybackError: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext

    var currentUrl by remember { mutableStateOf("") }
    var currentTitle by remember { mutableStateOf(title) }

    BackHandler(onBack = onBack)

    // Track video size
    var videoWidth  by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    // Auto-retry on error
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth  = videoSize.width
                videoHeight = videoSize.height
            }
            override fun onPlayerError(error: PlaybackException) {
                if (isVod) {
                    onVodPlaybackError()
                } else {
                    val retryJob = scope.launch {
                        delay(3_000)
                        player.prepare()
                        player.play()
                    }
                    onRetrySetup(retryJob)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Resume dialog — shown when resumeMs > 60s so user can choose to start over or resume
    var showResumeDialog by remember { mutableStateOf(resumeMs > 60_000L) }
    if (showResumeDialog) {
        val mins = (resumeMs / 1000 / 60).toInt()
        val secs = (resumeMs / 1000 % 60).toInt()
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text("Resume playback?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Continue from $mins:${"%02d".format(secs)}?", fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Or start from the beginning.", fontSize = 12.sp, color = TextMuted)
                }
            },
            confirmButton = {
                var resumeFocused by remember { mutableStateOf(false) }
                Text(
                    "Resume",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (resumeFocused) AccentBlue else TextSecondary,
                    modifier = Modifier
                        .focusable()
                        .onFocusChanged { resumeFocused = it.isFocused }
                        .clickable {
                            showResumeDialog = false
                            player.seekTo(resumeMs)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            },
            dismissButton = {
                var startFocused by remember { mutableStateOf(false) }
                Text(
                    "Start over",
                    fontSize = 14.sp,
                    color = if (startFocused) TextPrimary else TextMuted,
                    modifier = Modifier
                        .focusable()
                        .onFocusChanged { startFocused = it.isFocused }
                        .clickable {
                            showResumeDialog = false
                            player.seekTo(0)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        )
    }

    // Track selector state
    // Track selector state — store the actual group index so we can query it for selection checks
    val audioTracks = remember { mutableStateListOf<Pair<String, Int>>() } // label, groupIndex
    val textTracks  = remember { mutableStateListOf<Pair<String, Int>>() } // label, groupIndex

    // Currently selected group indices (null = off / not selected)
    var selectedAudioGroupIdx by remember { mutableStateOf<Int?>(null) }
    var selectedTextGroupIdx by remember { mutableStateOf<Int?>(null) }

    // Speed selector: index into SPEED_OPTIONS, default 1 (1×)
    var selectedSpeedIdx by remember { mutableIntStateOf(1) }

    // Quality selector: index into QUALITY_OPTIONS, default 0 (Auto)
    var selectedQualityIdx by remember { mutableIntStateOf(0) }

    // Local aliases so lambdas passed to TrackPickerPanel don't capture mutables
    val qualityOptions = PlayerActivity.QUALITY_OPTIONS
    val speedOptions = PlayerActivity.SPEED_OPTIONS
    val onSelectQuality: (Int) -> Unit = { idx -> selectedQualityIdx = idx }
    val onSelectSpeed: (Int) -> Unit = { idx -> selectedSpeedIdx = idx }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                audioTracks.clear()
                textTracks.clear()
                tracks.groups.forEachIndexed { groupIdx, group ->
                    when (group.type) {
                        C.TRACK_TYPE_AUDIO -> {
                            for (i in 0 until group.length) {
                                val fmt = group.getTrackFormat(i)
                                val lang = fmt.language ?: "Audio ${groupIdx + 1}"
                                val displayLang = try {
                                    Locale.forLanguageTag(lang).displayLanguage.let {
                                        if (it.isNotBlank()) it.uppercase() else lang.uppercase()
                                    }
                                } catch (_: Exception) { lang.uppercase() }
                                val label = "${displayLang}${if (fmt.channelCount > 0) " · ${fmt.channelCount}ch" else ""}".trim()
                                audioTracks.add(label to groupIdx)
                                if (group.isTrackSelected(i)) selectedAudioGroupIdx = groupIdx
                            }
                        }
                        C.TRACK_TYPE_TEXT -> {
                            for (i in 0 until group.length) {
                                val fmt = group.getTrackFormat(i)
                                val lang = fmt.language ?: "Sub ${groupIdx + 1}"
                                val displayLang = try {
                                    Locale.forLanguageTag(lang).displayLanguage.let {
                                        if (it.isNotBlank()) it.uppercase() else lang.uppercase()
                                    }
                                } catch (_: Exception) { lang.uppercase() }
                                textTracks.add(displayLang.uppercase() to groupIdx)
                                if (group.isTrackSelected(i)) selectedTextGroupIdx = groupIdx
                            }
                        }
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Mutable recent list that updates when switching channels
    val liveRecentChannels = remember { mutableStateListOf<ChannelRef>().apply { addAll(recentChannels) } }

    // Channel switch handler — reuses existing player (live only)
    val switchChannel: (String, String) -> Unit = remember {
        { newUrl: String, newTitle: String ->
            // Add current channel to recent list before switching
            if (currentUrl.isNotEmpty() && currentTitle.isNotEmpty()) {
                liveRecentChannels.removeAll { it.url == currentUrl }
                liveRecentChannels.add(0, ChannelRef(currentTitle, currentUrl, null))
                if (liveRecentChannels.size > 20) liveRecentChannels.removeRange(20, liveRecentChannels.size)
            }
            currentUrl = newUrl
            currentTitle = newTitle
            player.setMediaItem(MediaItem.fromUri(newUrl))
            player.prepare()
            player.playWhenReady = true
            onSwitchChannel(newUrl, newTitle)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { view ->
                view.resizeMode = resizeMode.intValue
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── VOD transport controls ────────────────────────────────────────
        if (isVod && showVodControls.value) {
            VodControlsOverlay(player = player, title = currentTitle.ifEmpty { title })
        }

        // ── Resolution overlay ────────────────────────────────────────────
        if (showResolution.value) {
            val label = when {
                videoHeight >= 2160 -> "4K UHD"
                videoHeight >= 1080 -> "Full HD · 1080p"
                videoHeight >= 720  -> "HD · 720p"
                videoHeight >= 480  -> "SD · 480p"
                videoWidth  >  0    -> "SD"
                else                -> "Detecting…"
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (videoWidth > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "$videoWidth × $videoHeight",
                            color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // ── Quick-switch overlay — live only ──────────────────────────────
        if (!isVod && showRecentBar.value) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                QuickSwitchOverlay(
                    recentChannels   = liveRecentChannels,
                    favoriteChannels = favoriteChannels,
                    currentTitle     = currentTitle.ifEmpty { title },
                    currentStreamId  = contentId,
                    isCurrentlyFavorited = liveFavoriteIds.value.contains(contentId),
                    requestFocus     = requestOverlayFocus,
                    onSwitchChannel  = switchChannel,
                    onToggleFavorite = { id, title, isFav ->
                        scope.launch(Dispatchers.IO) {
                            val store = FavoritesStore(appContext)
                            if (isFav) store.remove(id) else store.add(Favorite(id, title, "live"))
                        }
                    }
                )
            }
        }

        // ── Track picker — bottom panel ──────────────────────────────────
        if (showTrackPicker.value) {
            // Non-capturing local alias — avoids lambda capture type narrowing to State
            val pickerState = showTrackPicker
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(onClick = { (pickerState as MutableState<Boolean>).value = false })
            ) {
                TrackPickerPanel(
                    player = player,
                    audioTracks = audioTracks.map { TrackItem.Audio(it.first, it.second) },
                    textTracks = listOf(TrackItem.Text("Off", -1, true)) +
                        textTracks.map { TrackItem.Text(it.first, it.second, false) },
                    selectedAudioGroupIdx = selectedAudioGroupIdx,
                    selectedTextGroupIdx = selectedTextGroupIdx,
                    onSelectAudio = { groupIdx ->
                        val group = player.currentTracks.groups.getOrNull(groupIdx) ?: return@TrackPickerPanel
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                            .build()
                        selectedAudioGroupIdx = groupIdx
                    },
                    onSelectText = { groupIdxOrNull ->
                        if (groupIdxOrNull == null || groupIdxOrNull == -1) {
                            // Turn subtitles OFF
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build()
                            selectedTextGroupIdx = -1
                        } else {
                            val group = player.currentTracks.groups.getOrNull(groupIdxOrNull) ?: return@TrackPickerPanel
                            // Turn subtitles ON with specific track
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                .build()
                            selectedTextGroupIdx = groupIdxOrNull
                        }
                    },
                    qualityOptions = qualityOptions,
                    selectedQualityIdx = selectedQualityIdx,
                    onSelectQuality = { idx ->
                        val maxHeight = qualityOptions[idx]
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .apply {
                                if (maxHeight != null) setMaxVideoSize(Int.MAX_VALUE, maxHeight)
                                else setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                            }
                            .build()
                        onSelectQuality(idx)
                    },
                    speedOptions = speedOptions,
                    selectedSpeedIdx = selectedSpeedIdx,
                    onSelectSpeed = { idx ->
                        player.setPlaybackSpeed(speedOptions[idx])
                        onSelectSpeed(idx)
                    },
                    onDismiss = { (pickerState as MutableState<Boolean>).value = false },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // ── Next Episode overlay ──────────────────────────────────────────
        if (showNextEpisode.value) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .background(Color(0xDD000000), RoundedCornerShape(14.dp))
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Next: $nextTitle",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${nextCountdown.value}s",
                        color = Color(0xFF00CFFF),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Press BACK to cancel",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ── Playback error overlay ────────────────────────────────────────
        val errMsg = playerError.value
        if (errMsg != null) {
            val errBtnFocus = remember { FocusRequester() }
            LaunchedEffect(errMsg) { try { errBtnFocus.requestFocus() } catch (_: Exception) {} }
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Playback Error",
                        color = Color(0xFFEF4444),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        errMsg,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    var btnFocused by remember { mutableStateOf(false) }
                    Text(
                        "Go Back",
                        color = if (btnFocused) Color(0xFF00CFFF) else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1A40))
                            .onFocusChanged { btnFocused = it.isFocused }
                            .focusRequester(errBtnFocus)
                            .focusable()
                            .clickable(onClick = onDismissError)
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// VOD TRANSPORT CONTROLS OVERLAY
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun VodControlsOverlay(player: ExoPlayer, title: String) {
    // Poll player state at 250 ms while overlay visible
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position  by remember { mutableLongStateOf(player.currentPosition) }
    var duration  by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }

    LaunchedEffect(Unit) {
        while (true) {
            isPlaying = player.isPlaying
            position  = player.currentPosition
            duration  = player.duration.coerceAtLeast(0)
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0x55000000))) {

        // Title — top left
        Text(
            title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .fillMaxWidth(0.65f)
        )

        // Center transport icon + hints
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (isPlaying) "⏸" else "▶",
                color = Color.White,
                fontSize = 56.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                Text("◀◀ 10s", color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
                Text("10s ▶▶", color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
            }
        }

        // Bottom progress bar + time
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000)))
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            if (duration > 0) {
                LinearProgressIndicator(
                    progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = Color(0xFF00CFFF),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatMs(position),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                if (duration > 0) {
                    Text(
                        formatMs(duration),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val mins  = (totalSec % 3600) / 60
    val secs  = totalSec % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs)
    else "%d:%02d".format(mins, secs)
}

// ═══════════════════════════════════════════════════════════════════════════
// QUICK-SWITCH OVERLAY — Favorites + Recent channels (live only)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickSwitchOverlay(
    recentChannels: List<ChannelRef>,
    favoriteChannels: List<ChannelRef>,
    currentTitle: String,
    currentStreamId: String,
    isCurrentlyFavorited: Boolean,
    requestFocus: MutableState<Boolean>,
    onSwitchChannel: (url: String, title: String) -> Unit,
    onToggleFavorite: (id: String, title: String, isFav: Boolean) -> Unit
) {
    val firstCardFocus = remember { FocusRequester() }

    LaunchedEffect(requestFocus.value) {
        if (requestFocus.value) {
            withFrameNanos { } // one frame for layout, no arbitrary delay
            try { firstCardFocus.requestFocus() } catch (_: Exception) {}
            requestFocus.value = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xEE000000))
            .padding(bottom = 20.dp, top = 12.dp)
    ) {
        val hasRecent = recentChannels.isNotEmpty()
        val hasFavs = favoriteChannels.isNotEmpty()

        // ── "♥ Add to Favorites" button for current channel ──────────────
        Row(
            modifier = Modifier.padding(start = 24.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var favBtnFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (favBtnFocused) Color(0xFFFFB800).copy(alpha = 0.25f) else Color.Transparent)
                    .then(if (favBtnFocused) Modifier.border(1.dp, Color(0xFFFFB800), RoundedCornerShape(6.dp)) else Modifier)
                    .onFocusChanged { favBtnFocused = it.isFocused }
                    .focusable()
                    .clickable { onToggleFavorite(currentStreamId, currentTitle, isCurrentlyFavorited) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isCurrentlyFavorited) "♥" else "♡", color = Color(0xFFFFB800), fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isCurrentlyFavorited) "Remove from Favorites" else "Add to Favorites",
                    color = if (favBtnFocused) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 300.dp)
                )
            }
        }

        if (!hasRecent && !hasFavs) {
            Text(
                "No recent or favorite channels yet",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 24.dp)
            )
        }

        // ── Recent channels ──────────────────────────────────────────────
        if (hasRecent) {
            Text(
                "  RECENT",
                color = Color(0xFF00CFFF).copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(recentChannels, key = { "rec_${it.url}" }) { channel ->
                    val isFirst = recentChannels.firstOrNull()?.url == channel.url
                    QuickSwitchCard(
                        channel = channel,
                        onClick = { onSwitchChannel(channel.url, channel.name) },
                        modifier = if (isFirst) Modifier.focusRequester(firstCardFocus) else Modifier
                    )
                }
            }
            if (hasFavs) Spacer(Modifier.height(10.dp))
        }

        // ── Favorite channels ────────────────────────────────────────────
        if (hasFavs) {
            Text(
                "  ♥ FAVORITES",
                color = Color(0xFFFFB800).copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(favoriteChannels, key = { "fav_${it.url}" }) { channel ->
                    val isFirst = favoriteChannels.firstOrNull()?.url == channel.url && !hasRecent
                    QuickSwitchCard(
                        channel = channel,
                        onClick = { onSwitchChannel(channel.url, channel.name) },
                        modifier = if (isFirst) Modifier.focusRequester(firstCardFocus) else Modifier
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// QUICK-SWITCH CARD
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickSwitchCard(
    channel: ChannelRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "cardScale"
    )
    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) Color(0xFF00CFFF).copy(alpha = 0.22f)
                else Color(0xFF141428)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color(0xFF00CFFF) else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!channel.icon.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.icon)
                    .size(Size(56, 56))
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp))
            )
        } else {
            Box(
                Modifier
                    .size(28.dp)
                    .background(Color(0xFF00CFFF).copy(alpha = if (focused) 0.35f else 0.18f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    channel.name.firstOrNull { it.isLetter() }?.uppercase() ?: "?",
                    color = Color(0xFF00CFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            channel.name,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
    }
}

@Composable
private fun TrackChip(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val isActive = focused || isSelected
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused && isSelected -> Color(0xFF00CFFF).copy(alpha = 0.35f)
                    focused -> Color(0xFF162040)
                    isSelected -> Color(0xFF0D2A45)
                    else -> Color(0xFF0D1525)
                }
            )
            .then(
                if (focused || isSelected) Modifier.border(
                    when {
                        focused && isSelected -> 2.dp
                        isSelected -> 1.5.dp
                        else -> 1.dp
                    },
                    when {
                        focused && isSelected -> Color(0xFF00CFFF)
                        isSelected -> Color(0xFF00CFFF).copy(alpha = 0.8f)
                        focused -> Color(0xFF4D8EFF).copy(alpha = 0.7f)
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = if (isSelected) Color.White else if (focused) Color.White else Color.White.copy(alpha = 0.55f),
                fontSize = 14.sp
            )
            if (isSelected) {
                Text("✓", color = Color(0xFF00CFFF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AudioTracksRow(
    tracks: List<TrackItem.Audio>,
    selectedGroupIdx: Int?,
    onSelect: (groupIndex: Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedGroupIdx) {
        if (selectedGroupIdx != null) {
            val idx = tracks.indexOfFirst { it.groupIndex == selectedGroupIdx }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(tracks, key = { "audio_${it.groupIndex}" }) { track ->
            TrackChip(
                label = track.label,
                isSelected = track.groupIndex == selectedGroupIdx,
                onSelect = { onSelect(track.groupIndex) }
            )
        }
    }
}

@Composable
private fun QualityRow(
    options: List<Int?>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit
) {
    val labels = options.map { it?.let { h -> "${h}p" } ?: "Auto" }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIdx) {
        if (selectedIdx >= 0) listState.animateScrollToItem(selectedIdx)
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(options.size) { idx ->
            TrackChip(
                label = labels[idx],
                isSelected = idx == selectedIdx,
                onSelect = { onSelect(idx) }
            )
        }
    }
}

@Composable
private fun SpeedRow(
    options: List<Float>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit
) {
    val labels = options.map { if (it == 1f) "1×" else "${it}×" }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIdx) {
        if (selectedIdx >= 0) listState.animateScrollToItem(selectedIdx)
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(options.size) { idx ->
            TrackChip(
                label = labels[idx],
                isSelected = idx == selectedIdx,
                onSelect = { onSelect(idx) }
            )
        }
    }
}

@Composable
private fun SubtitleTracksRow(
    tracks: List<TrackItem.Text>,
    selectedGroupIdx: Int?,
    onSelect: (groupIndex: Int?) -> Unit
) {
    val listState = rememberLazyListState()

    val selectedIdx = tracks.indexOfFirst {
        if (it.isOff) selectedGroupIdx == null || selectedGroupIdx == -1
        else it.groupIndex == selectedGroupIdx
    }

    LaunchedEffect(selectedGroupIdx) {
        if (selectedIdx >= 0) listState.animateScrollToItem(selectedIdx)
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(tracks, key = { if (it.isOff) "text_off" else "text_${it.groupIndex}" }) { track ->
            val isSelected = if (track.isOff) {
                selectedGroupIdx == null || selectedGroupIdx == -1
            } else {
                track.groupIndex == selectedGroupIdx
            }
            TrackChip(
                label = track.label,
                isSelected = isSelected,
                onSelect = {
                    if (track.isOff) onSelect(null)
                    else onSelect(track.groupIndex)
                }
            )
        }
    }
}

@Composable
private fun TrackPickerPanel(
    player: ExoPlayer,
    audioTracks: List<TrackItem.Audio>,
    textTracks: List<TrackItem.Text>,
    selectedAudioGroupIdx: Int?,
    selectedTextGroupIdx: Int?,
    onSelectAudio: (Int) -> Unit,
    onSelectText: (Int?) -> Unit,
    qualityOptions: List<Int?>,
    selectedQualityIdx: Int,
    onSelectQuality: (Int) -> Unit,
    speedOptions: List<Float>,
    selectedSpeedIdx: Int,
    onSelectSpeed: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }

    val progressFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitleFocus = remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        try { progressFocus.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        while (true) {
            isPlaying = player.isPlaying
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(250)
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // Section 1: Progress bar + time
            Box(
                Modifier
                    .fillMaxWidth()
                    .focusRequester(progressFocus)
                    .focusProperties {
                        down = audioFocus
                    }
                    .focusable()
            ) {
                Column {
                    LinearProgressIndicator(
                        progress = { if (duration > 0) position.toFloat() / duration.toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFF00CFFF),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatMs(position),
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            "AUDIO & SUBTITLES",
                            color = Color(0xFF00CFFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            formatMs(duration),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Section 2: Audio tracks
            Box(
                Modifier
                    .fillMaxWidth()
                    .focusRequester(audioFocus)
                    .focusProperties {
                        down = subtitleFocus
                        up = progressFocus
                    }
            ) {
                Column {
                    Text(
                        "AUDIO",
                        color = Color(0xFF00CFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                    AudioTracksRow(audioTracks, selectedAudioGroupIdx, onSelectAudio)
                }
            }

            // Section 3: Subtitle tracks
            Box(
                Modifier
                    .fillMaxWidth()
                    .focusRequester(subtitleFocus)
                    .focusProperties {
                        down = progressFocus
                        up = audioFocus
                    }
            ) {
                Column {
                    Text(
                        "SUBTITLES",
                        color = Color(0xFF00CFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                    SubtitleTracksRow(textTracks, selectedTextGroupIdx, onSelectText)
                }
            }

        }
    }
}
