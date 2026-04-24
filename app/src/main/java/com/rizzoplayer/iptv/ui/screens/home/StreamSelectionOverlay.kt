package com.rizzoplayer.iptv.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.data.model.TorrentioStream
import com.rizzoplayer.iptv.data.model.UnifiedTorrent
import com.rizzoplayer.iptv.ui.designsystem.rizzoFocusGroup
import com.rizzoplayer.iptv.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════════
// TORRENTIO STREAM PARSER
// ═══════════════════════════════════════════════════════════════════════════════

object TorrentioParser {

    private val RE_RESOLUTION_4K   = Regex("""(?:\b|[\.\-_])((?:2160|4k)\s*p?)\b""", RegexOption.IGNORE_CASE)
    private val RE_RESOLUTION_1080 = Regex("""(?:\b|[\.\-_])((?:1080|720|480)\s*p?)\b""", RegexOption.IGNORE_CASE)
    private val RE_DV              = Regex("""\b(DV|Dolby\s*Vision)\b""", RegexOption.IGNORE_CASE)
    private val RE_HDR10PLUS      = Regex("""\b(HDR10\+|HDR\s*10\s*\+|PQ)\b""", RegexOption.IGNORE_CASE)
    private val RE_HDR_GENERIC     = Regex("""\b(HDR)\b""", RegexOption.IGNORE_CASE)
    private val RE_ATMOS           = Regex("""\b(Atmos)\b""", RegexOption.IGNORE_CASE)
    private val RE_AUDIO_51        = Regex("""\b(5\.1)\b""", RegexOption.IGNORE_CASE)
    private val RE_AUDIO_71        = Regex("""\b(7\.1)\b""", RegexOption.IGNORE_CASE)
    private val RE_SIZE_GB         = Regex("""(\d+(?:[.,]\d+)?)\s*(?:GB|Gb|gb|gig)""")
    private val RE_SIZE_MB         = Regex("""(\d+(?:[.,]\d+)?)\s*(?:MB|Mb|mb|meg)""")
    private val RE_SEEDERS         = Regex("""👤\s*(\d+)""")
    private val RE_SEEDERS_ALT     = Regex("""(?:S|s|Seeders?)[\s:]*(\d+)""")

    data class StreamMetadata(
        val resolution:  ResolutionBadge? = null,
        val videoTech:  VideoTechBadge?  = null,
        val audio:      String?          = null,
        val fileSizeGb: Float?          = null,
        val seeders:    Int?            = null,
        val cleanName:  String           = "",
    )

    enum class ResolutionBadge { SD, RES_720P, RES_1080P, RES_4K }
    enum class VideoTechBadge { TECH_DV, TECH_HDR10PLUS, TECH_HDR }

    fun parse(stream: TorrentioStream): StreamMetadata {
        val combined = "${stream.title} ${stream.name}"

        val resolution = detectResolution(combined)
        val videoTech  = detectVideoTech(combined, resolution)
        val audio      = detectAudio(combined)
        val fileSize   = detectFileSize(combined)
        val seeders    = detectSeeders(combined)
        val cleanName  = cleanFileName(stream.title)

        return StreamMetadata(
            resolution = resolution,
            videoTech  = videoTech,
            audio      = audio,
            fileSizeGb = fileSize,
            seeders    = seeders,
            cleanName  = cleanName,
        )
    }

    fun parse(torrent: UnifiedTorrent): StreamMetadata {
        // UnifiedTorrent has title, url (magnet), no separate name field — derive name from title
        val combined = torrent.title
        val resolution = detectResolution(combined)
        val videoTech  = detectVideoTech(combined, resolution)
        val audio      = detectAudio(combined)
        val fileSize   = detectFileSize(combined)
        val seeders    = if (torrent.seeders > 0) torrent.seeders else detectSeeders(combined)
        val cleanName  = cleanFileName(torrent.title)

        return StreamMetadata(
            resolution = resolution,
            videoTech  = videoTech,
            audio      = audio,
            fileSizeGb = fileSize,
            seeders    = seeders,
            cleanName  = cleanName,
        )
    }

    private fun detectResolution(text: String): ResolutionBadge? {
        val upper = text.uppercase()
        return when {
            Regex("""\b(2160|4K)\b""").containsMatchIn(upper) -> ResolutionBadge.RES_4K
            Regex("""\b1080P?\b""").containsMatchIn(text)             -> ResolutionBadge.RES_1080P
            Regex("""\b720P?\b""").containsMatchIn(text)              -> ResolutionBadge.RES_720P
            Regex("""\b480P?\b""").containsMatchIn(text)              -> ResolutionBadge.SD
            else -> null
        }
    }

    private fun detectVideoTech(text: String, @Suppress("UNUSED_PARAMETER") res: ResolutionBadge?): VideoTechBadge? {
        return when {
            RE_DV.containsMatchIn(text)             -> VideoTechBadge.TECH_DV
            RE_HDR10PLUS.containsMatchIn(text.uppercase()) -> VideoTechBadge.TECH_HDR10PLUS
            RE_HDR_GENERIC.containsMatchIn(text)    -> VideoTechBadge.TECH_HDR
            else -> null
        }
    }

    private fun detectAudio(text: String): String? {
        return when {
            RE_ATMOS.containsMatchIn(text) -> "Atmos"
            RE_AUDIO_71.containsMatchIn(text) -> "7.1"
            RE_AUDIO_51.containsMatchIn(text) -> "5.1"
            else -> null
        }
    }

    private fun detectFileSize(text: String): Float? {
        val gbMatch = RE_SIZE_GB.find(text)
        if (gbMatch != null) {
            val v = gbMatch.groupValues[1].replace(',', '.').toFloatOrNull()
            return v
        }
        val mbMatch = RE_SIZE_MB.find(text)
        if (mbMatch != null) {
            val v = mbMatch.groupValues[1].replace(',', '.').toFloatOrNull()
            return v?.div(1024f)
        }
        return null
    }

    private fun detectSeeders(text: String): Int? {
        val seederMatch = RE_SEEDERS.find(text)
        if (seederMatch != null) {
            return seederMatch.groupValues[1].toIntOrNull()
        }
        val altMatch = RE_SEEDERS_ALT.find(text)
        return altMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun cleanFileName(raw: String): String {
        var result = raw
            .replace(Regex("""[\.\-_]+"""), " ")
        result = Regex("""\b(2160p|1080p|720p|480p|4k|4kp)\b""", RegexOption.IGNORE_CASE).replace(result, " ")
        result = Regex("""\b(HDR|DV|Dolby\s*Vision|HDR10\+|Atmos|5\.1|7\.1)\b""", RegexOption.IGNORE_CASE).replace(result, " ")
        result = Regex("""👤[\s\d]*""").replace(result, "")
        result = Regex("""💾[\s\d.]*(?:GB|MB)?""", RegexOption.IGNORE_CASE).replace(result, "")
        result = Regex("""\s{2,}""").replace(result, " ")
        return result.trim().ifEmpty { raw.take(60).replace('.', ' ') }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STREAM ITEM CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun StreamItemCard(
    stream: Any,  // UnifiedTorrent or TorrentioStream
    metadata: TorrentioParser.StreamMetadata,
    isFocused: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "cardScale",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val cardBg = when {
        isFocused && pressed -> CardBgFocusedPressed
        isFocused           -> CardBgFocused
        else                -> CardBgDefault
    }

    val borderColor = when (isFocused) {
        true  -> AccentBlue.copy(alpha = 0.8f)
        false -> BorderColor.copy(alpha = 0.3f)
    }

    val textColor = if (isFocused) Color.White else TextSecondary
    val seedColor = if (isFocused) GreenSeeders else TextMuted.copy(alpha = 0.6f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(if (isFocused) 12.dp else 2.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .border(
                width = if (isFocused) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // ── Top: Clean filename ──────────────────────────────────────────────
        Text(
            text = metadata.cleanName.ifEmpty {
                val title = when (stream) {
                    is UnifiedTorrent -> stream.title
                    else -> (stream as TorrentioStream).title
                }
                title.take(60).replace('.', ' ')
            },
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
        )

        Spacer(Modifier.height(7.dp))

        // ── Middle: Quality + Video Tech badges ────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            metadata.resolution?.let { res -> QualityBadge(resolution = res, isFocused = isFocused) }
            metadata.videoTech?.let  { tech -> VideoTechBadge(tech = tech, isFocused = isFocused) }
            metadata.audio?.let      { audio -> Badge(text = audio, bgColor = BadgeBlue, isFocused = isFocused) }
        }

        Spacer(Modifier.height(7.dp))

        // ── Bottom: File size + Seeders ─────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            metadata.fileSizeGb?.let { size ->
                Text(
                    text = if (size >= 1f) "%.1f GB".format(size) else "%.0f MB".format(size * 1024),
                    fontSize = 11.sp,
                    color = seedColor,
                    fontWeight = FontWeight.Medium,
                )
            }
            metadata.seeders?.let { seeds ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("👤", fontSize = 11.sp, color = seedColor)
                    Text(
                        text = formatSeeders(seeds),
                        fontSize = 11.sp,
                        color = seedColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityBadge(resolution: TorrentioParser.ResolutionBadge, isFocused: Boolean) {
    val (text, bg, fg) = when (resolution) {
        TorrentioParser.ResolutionBadge.RES_4K    -> Triple("4K",    BadgeGold,    Color(0xFF1A1000))
        TorrentioParser.ResolutionBadge.RES_1080P  -> Triple("1080p", BadgeGrey,    if (isFocused) Color.White else TextSecondary)
        TorrentioParser.ResolutionBadge.RES_720P   -> Triple("720p",  BadgeGrey,    if (isFocused) Color.White else TextSecondary)
        TorrentioParser.ResolutionBadge.SD          -> Triple("480p",  BadgeGrey,    if (isFocused) Color.White else TextSecondary)
    }
    Badge(text = text, bgColor = bg, fgColorOverride = fg, isFocused = isFocused)
}

@Composable
private fun VideoTechBadge(tech: TorrentioParser.VideoTechBadge, isFocused: Boolean) {
    val (text, bg, fg) = when (tech) {
        TorrentioParser.VideoTechBadge.TECH_DV         -> Triple("DV",      BadgePurple,     Color.White)
        TorrentioParser.VideoTechBadge.TECH_HDR10PLUS  -> Triple("HDR10+",  BadgePurpleDeep, Color.White)
        TorrentioParser.VideoTechBadge.TECH_HDR        -> Triple("HDR",     BadgePurple,     Color.White)
    }
    Badge(text = text, bgColor = bg, fgColorOverride = fg, isFocused = isFocused)
}

@Composable
private fun Badge(
    text: String,
    bgColor: Color,
    isFocused: Boolean,
    fgColorOverride: Color? = null
) {
    val textColor = fgColorOverride ?: when (isFocused) {
        true  -> Color.White
        false -> Color.White.copy(alpha = 0.85f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 0.5.sp,
        )
    }
}

private fun formatSeeders(n: Int): String = when {
    n >= 1_000 -> "%.1fK".format(n / 1_000f)
    else        -> n.toString()
}

// ═══════════════════════════════════════════════════════════════════════════════
// PREMIUM STREAM SELECTION OVERLAY
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PremiumStreamSelectionOverlay(
    title: String,
    streams: List<UnifiedTorrent>,
    onSelect: (UnifiedTorrent) -> Unit,
    onDismiss: () -> Unit,
) {
    // Debounce: guard against rapid D-pad mashing firing multiple onSelect calls
    var selectedLocked by remember { mutableStateOf(false) }
    val parsedStreams = remember(streams) { streams.map { s -> s to TorrentioParser.parse(s) } }

    var focusedIdx by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val firstItemFocus = remember { FocusRequester() }

    // Reset focus index whenever a new stream list is presented
    LaunchedEffect(streams) {
        focusedIdx = 0
        kotlinx.coroutines.delay(80)
        try { firstItemFocus.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(focusedIdx) {
        listState.animateScrollToItem(focusedIdx.coerceIn(0, (parsedStreams.size - 1).coerceAtLeast(0)))
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { }
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(16.dp))
                .background(PanelBg)
                .border(0.5.dp, BorderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelBg.copy(alpha = 0.95f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ButtonBg)
                            .border(1.dp, BorderColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .focusable()
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("✕  Close", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── Column headers ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${streams.size} streams",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(
                        "4K"  to BadgeGold,
                        "DV"  to BadgePurple,
                        "HDR" to BadgePurple,
                    ).forEach { (label, color) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(color.copy(alpha = 0.85f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(BorderColor.copy(alpha = 0.3f)))
            Spacer(Modifier.height(6.dp))

            // ── Stream list ──────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().rizzoFocusGroup(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = parsedStreams,
                    key = { _, (stream, _) -> stream.url }
                ) { idx, (stream, metadata) ->
                    val isFocused = idx == focusedIdx
                    StreamItemCard(
                        stream = stream,
                        metadata = metadata,
                        isFocused = isFocused,
                        onSelect = {
                            if (!selectedLocked) {
                                selectedLocked = true
                                onSelect(stream)
                            }
                        },
                        modifier = Modifier
                            .focusRequester(if (idx == 0) firstItemFocus else FocusRequester())
                            .onFocusChanged { if (it.isFocused) focusedIdx = idx }
                            .focusable(),
                    )
                }
            }
        }
    }
}
