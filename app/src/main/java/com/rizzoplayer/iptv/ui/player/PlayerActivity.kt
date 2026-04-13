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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rizzoplayer.iptv.RizzoApp
import com.rizzoplayer.iptv.data.local.PlaybackPositionStore
import com.rizzoplayer.iptv.data.model.ChannelRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerActivity : ComponentActivity() {

    // Player — created at Activity level for direct key-event access
    private var player: ExoPlayer? = null
    private var isVod = false
    private lateinit var currentUrl: String

    // Content identification for persistent positions
    private var contentId: String = ""
    private var contentType: String = "live"

    // Episode auto-advance
    private var nextUrl: String = ""
    private var nextTitle: String = ""
    private val showNextEpisode = mutableStateOf(false)
    private val nextCountdown = mutableIntStateOf(5)
    private var countdownJob: Job? = null

    // Overlay state
    private val showResolution    = mutableStateOf(false)
    private val showRecentBar     = mutableStateOf(false)
    private val showTrackPicker   = mutableStateOf(false)
    private val showVodControls   = mutableStateOf(false)
    private val requestOverlayFocus = mutableStateOf(false)

    // Aspect ratio
    private val resizeMode = mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)

    // Jobs
    private var resolutionJob: Job? = null
    private var recentChannelsJob: Job? = null
    private var retryJob: Job? = null
    private var vodControlsJob: Job? = null

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

        // Buffer config: VOD gets larger buffers for smooth playback
        val loadControl = if (isVod) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(5_000, 60_000, 2_500, 5_000)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(1_500, 8_000, 1_000, 1_500)
                .build()
        }

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaItem(MediaItem.fromUri(currentUrl))
                prepare()
                if (resumeMs > 30_000L) seekTo(resumeMs)
                playWhenReady = true

                // Episode auto-advance listener
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED && nextUrl.isNotEmpty()) {
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
                })
            }

        setContent {
            PlayerScreen(
                player           = player!!,
                isVod            = isVod,
                title            = title,
                resumeMs         = resumeMs,
                recentChannels   = recentChannels,
                favoriteChannels = favoriteChannels,
                showResolution   = showResolution,
                showRecentBar    = showRecentBar,
                showTrackPicker  = showTrackPicker,
                showVodControls  = showVodControls,
                showNextEpisode  = showNextEpisode,
                nextCountdown    = nextCountdown,
                nextTitle        = nextTitle,
                resizeMode       = resizeMode,
                requestOverlayFocus = requestOverlayFocus,
                onRetrySetup     = { retryJob = it },
                onBack           = ::finish,
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

    private fun parseChannelRefs(json: String): List<ChannelRef> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<ChannelRef>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
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
        when (event.keyCode) {
            // Play / Pause
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (showTrackPicker.value) return super.dispatchKeyEvent(event)
                if (p.isPlaying) p.pause() else p.play()
                showVodControlsBriefly()
                return true
            }
            // Rewind: 10 s on first press, 30 s when held
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (showTrackPicker.value) return super.dispatchKeyEvent(event)
                val seekMs = if (event.repeatCount > 0) 30_000L else 10_000L
                p.seekTo((p.currentPosition - seekMs).coerceAtLeast(0))
                showVodControlsBriefly()
                return true
            }
            // Forward: 10 s on first press, 30 s when held
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (showTrackPicker.value) return super.dispatchKeyEvent(event)
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
                if (showTrackPicker.value) {
                    showTrackPicker.value = false
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
        player?.let { p ->
            // Save position + duration to persistent PlaybackPositionStore
            if (contentId.isNotEmpty()) {
                val key = "${contentType}:${contentId}"
                val positionMs = p.currentPosition
                val durationMs = p.duration.coerceAtLeast(0)
                (application as RizzoApp).playbackPositionStore.save(key, positionMs, durationMs)
            }
            p.release()
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
        const val EXTRA_RECENT_CHANNELS   = "recent_channels"
        const val EXTRA_FAVORITE_CHANNELS = "favorite_channels"
        const val EXTRA_NEXT_URL          = "next_url"
        const val EXTRA_NEXT_TITLE        = "next_title"
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
    onSwitchChannel: (url: String, title: String) -> Unit
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
                val retryJob = scope.launch {
                    delay(3_000)
                    player.prepare()
                    player.play()
                }
                onRetrySetup(retryJob)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Resume banner
    var showResumeBanner by remember { mutableStateOf(resumeMs > 30_000L) }
    LaunchedEffect(Unit) {
        if (showResumeBanner) {
            delay(5_000)
            showResumeBanner = false
        }
    }

    // Track selector state
    val audioTracks = remember { mutableStateListOf<Pair<String, Int>>() }
    val textTracks  = remember { mutableStateListOf<Pair<String, Int>>() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                audioTracks.clear()
                textTracks.clear()
                tracks.groups.forEachIndexed { groupIdx, group ->
                    when (group.type) {
                        C.TRACK_TYPE_AUDIO -> {
                            for (i in 0 until group.length) {
                                val fmt = group.getTrackFormat(i)
                                val lang = fmt.language ?: "Audio ${groupIdx + 1}"
                                val label = "${lang.uppercase()} ${if (fmt.channelCount > 0) "· ${fmt.channelCount}ch" else ""}".trim()
                                audioTracks.add(label to groupIdx)
                            }
                        }
                        C.TRACK_TYPE_TEXT -> {
                            for (i in 0 until group.length) {
                                val fmt = group.getTrackFormat(i)
                                val lang = fmt.language ?: "Sub ${groupIdx + 1}"
                                textTracks.add(lang.uppercase() to groupIdx)
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

        // ── Resume banner ─────────────────────────────────────────────────
        if (showResumeBanner) {
            val mins = (resumeMs / 1000 / 60).toInt()
            val secs = (resumeMs / 1000 % 60).toInt()
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        "▶ Resumed from %d:%02d".format(mins, secs),
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isVod) "Use ◀ / ▶ to seek" else "Press ← to restart from beginning",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp
                    )
                }
            }
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
                    requestFocus     = requestOverlayFocus,
                    onSwitchChannel  = switchChannel,
                    onAddFavorite    = { channelTitle ->
                        val streamId = currentUrl.substringAfterLast("/").substringBefore(".").trim()
                        if (streamId.isNotEmpty()) {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val store = com.rizzoplayer.iptv.data.local.FavoritesStore(appContext)
                                store.add(com.rizzoplayer.iptv.data.model.Favorite(streamId, channelTitle, "live"))
                            }
                        }
                    }
                )
            }
        }

        // ── Track picker ──────────────────────────────────────────────────
        if (showTrackPicker.value) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xEE0C0F1A), RoundedCornerShape(16.dp))
                    .padding(24.dp)
                    .widthIn(min = 280.dp, max = 420.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Audio, Subtitles & Video",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))

                    if (audioTracks.isNotEmpty()) {
                        Text("AUDIO", color = Color(0xFF4D8EFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        audioTracks.forEach { (label, groupIdx) ->
                            TrackRow(label = label) {
                                val group = player.currentTracks.groups.getOrNull(groupIdx) ?: return@TrackRow
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                    .build()
                            }
                        }
                    }

                    if (textTracks.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("SUBTITLES", color = Color(0xFF4D8EFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        TrackRow(label = "Off") {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()
                        }
                        textTracks.forEach { (label, groupIdx) ->
                            TrackRow(label = label) {
                                val group = player.currentTracks.groups.getOrNull(groupIdx) ?: return@TrackRow
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                    .build()
                            }
                        }
                    }

                    // ── Aspect ratio / video section ──────────────────────
                    Spacer(Modifier.height(4.dp))
                    Text("VIDEO", color = Color(0xFF4D8EFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    TrackRow(label = "Fit") {
                        resizeMode.intValue = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    TrackRow(label = "Zoom") {
                        resizeMode.intValue = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    TrackRow(label = "Stretch") {
                        resizeMode.intValue = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }

                    if (audioTracks.isEmpty() && textTracks.isEmpty()) {
                        Text(
                            "No alternate tracks available",
                            color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp
                        )
                    }
                }
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
    requestFocus: MutableState<Boolean>,
    onSwitchChannel: (url: String, title: String) -> Unit,
    onAddFavorite: (String) -> Unit
) {
    val firstCardFocus = remember { FocusRequester() }

    LaunchedEffect(requestFocus.value) {
        if (requestFocus.value) {
            delay(150)
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
        var addedFav by remember { mutableStateOf(false) }
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
                    .clickable {
                        if (!addedFav) { onAddFavorite(currentTitle); addedFav = true }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (addedFav) "♥" else "♡", color = Color(0xFFFFB800), fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (addedFav) "Added!" else "Favorite: $currentTitle",
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
                        modifier = if (isFirst && !hasFavs.not()) Modifier.focusRequester(firstCardFocus) else Modifier
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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF00CFFF) else Color(0x44222222))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!channel.icon.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.icon)
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
                    .background(if (focused) Color.White.copy(alpha = 0.3f) else Color(0xFF00CFFF).copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    channel.name.firstOrNull { it.isLetter() }?.uppercase() ?: "?",
                    color = if (focused) Color.Black else Color(0xFF00CFFF),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            channel.name,
            color = if (focused) Color.Black else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
    }
}

@Composable
private fun TrackRow(label: String, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF162040) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (focused) Color.White else Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
    }
}
