package com.rizzoplayer.iptv.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand ──────────────────────────────────────────────────────────────────────

/**
 * v4 brand indigo — distinguishes v4 from v2/v3 (which use AccentBlue #2563EB).
 * Used for focused state glow rings, primary buttons, and brand accents.
 */
val RizzoIndigo = Color(0xFF7C3AED)
val RizzoIndigoLight = Color(0xFF9F67FF)
val RizzoIndigoDark = Color(0xFF5B21B6)

// ── Core palette ───────────────────────────────────────────────────────────────

val RizzoBackground = Color(0xFF0A0A0F)
val RizzoSurface = Color(0xFF14141F)
val RizzoSurfaceVariant = Color(0xFF1E1E2E)
val RizzoSurfaceElevated = Color(0xFF252538)

// ── Text ─────────────────────────────────────────────────────────────────────

val RizzoTextPrimary = Color(0xFFF5F5F7)
val RizzoTextSecondary = Color(0xFFA1A1AA)
val RizzoTextTertiary = Color(0xFF71717A)
val RizzoTextDisabled = Color(0xFF52525B)

// ── Accent (focus / interactive) ─────────────────────────────────────────────

/**
 * Primary accent — used for focused glow rings, selected states,
 * and primary interactive elements in v4.
 */
val RizzoAccent = RizzoIndigo
val RizzoAccentLight = RizzoIndigoLight
val RizzoAccentDark = RizzoIndigoDark

/**
 * Accent with 14% alpha — used for focus tint background on cards.
 * Matches the old AccentBlue.copy(alpha=0.14f) pattern.
 */
val RizzoAccentTint = RizzoIndigo.copy(alpha = 0.08f)
val RizzoAccentGlow = RizzoIndigo.copy(alpha = 0.20f)

// ── Semantic ──────────────────────────────────────────────────────────────────

val RizzoSuccess = Color(0xFF22C55E)
val RizzoWarning = Color(0xFFF59E0B)
val RizzoError = Color(0xFFEF4444)
val RizzoInfo = Color(0xFF3B82F6)

// ── Poster / card scrim ───────────────────────────────────────────────────────

/**
 * Bottom scrim gradient for posters — black 0% → 80% from mid to bottom.
 * Use with Brush.verticalGradient.
 */
val RizzoPosterScrimStart = Color.Transparent
val RizzoPosterScrimEnd = Color(0xCC000000)

// ── Skeleton / shimmer ────────────────────────────────────────────────────────

val RizzoSkeletonBase = Color(0xFF1E1E2E)
val RizzoSkeletonHighlight = Color(0xFF2A2A3E)

// ── Stream state colors ───────────────────────────────────────────────────────

val RizzoStreamSearching = Color(0xFFF59E0B)
val RizzoStreamQueuing = Color(0xFF3B82F6)
val RizzoStreamCaching = Color(0xFF8B5CF6)
val RizzoStreamReady = Color(0xFF22C55E)
val RizzoStreamFailed = Color(0xFFEF4444)

// ── Backward-compatibility aliases ────────────────────────────────────────────
// All old Color.kt consumers use these via the Color {} shim object.
// Prefer the new Rizzo* names; these are only for incremental migration.

/** @deprecated Use [RizzoBackground] */        val RizzoMainBg = RizzoBackground
/** @deprecated Use [RizzoSurface] */          val RizzoSidebarBg = RizzoSurface
/** @deprecated Use [RizzoSurfaceVariant] */    val RizzoCardBg = RizzoSurfaceVariant
/** @deprecated Use [RizzoAccentTint] */         val RizzoAccentDim = RizzoAccentTint
/** @deprecated Use [RizzoAccent] */           val RizzoAccentBorder = RizzoAccent
/** @deprecated Use [RizzoAccentLight] */      val RizzoGold = RizzoAccentLight
/** @deprecated Use [RizzoTextPrimary] */       val RizzoTextMuted = RizzoTextPrimary
/** @deprecated Use [RizzoTextTertiary] */      val RizzoBorder = RizzoTextTertiary
/** @deprecated Use [RizzoAccent] */           val RizzoNavActive = RizzoAccent
/** @deprecated Use [RizzoAccent] */           val RizzoNavHover = RizzoAccent
/** @deprecated Use [RizzoAccentTint] */        val RizzoNavFocusBg = RizzoAccentTint
/** @deprecated Use [RizzoError] */             val RizzoRed = RizzoError
/** @deprecated Use [RizzoSuccess] */           val RizzoGreen = RizzoSuccess
/** @deprecated Use [RizzoSkeletonBase] */       val RizzoShimmerBase = RizzoSkeletonBase
/** @deprecated Use [RizzoSkeletonHighlight] */ val RizzoShimmerHighlight = RizzoSkeletonHighlight
/** @deprecated Use [RizzoTextSecondary] */     val RizzoBadgeGold = RizzoTextSecondary
/** @deprecated Use [RizzoAccent] */            val RizzoBadgePurple = RizzoAccent
/** @deprecated Use [RizzoAccentDark] */       val RizzoBadgePurpleDeep = RizzoAccentDark
/** @deprecated Use [RizzoInfo] */             val RizzoBadgeBlue = RizzoInfo
/** @deprecated Use [RizzoTextTertiary] */     val RizzoBadgeGrey = RizzoTextTertiary
/** @deprecated Use [RizzoAccentTint] */        val RizzoCardFocused = RizzoAccentTint
/** @deprecated Use [RizzoAccent] */            val RizzoCardPressed = RizzoAccent
/** @deprecated Use [RizzoAccentDark] */       val BrandIndigoDark = RizzoAccentDark
