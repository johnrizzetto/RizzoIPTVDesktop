package com.rizzoplayer.iptv.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp

/**
 * RizzoIPTV motion constants — spring physics for all focus/scale/enter/exit animations.
 *
 * Default spring: [Spring.DampingRatioLowBouncy] — satisfying "snap" without feeling stiff.
 * This matches the bouncy feel expected on a TV remote where the user is
 * navigating quickly with a D-pad.
 */
object RizzoMotion {

    // ── Spring specs ──────────────────────────────────────────────────────────

    /** Default spring — low bouncy snap for card/item focus animations */
    val DefaultSpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Snappy spring — faster, less bounce (for buttons, chips) */
    val SnappySpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Gentle spring — slow, heavy bounce (for modals, sheets) */
    val GentleSpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
    )

    // ── Durations (ms) ───────────────────────────────────────────────────────

    /** Instant — no animation */
    const val DurationInstant = 0L

    /** Fast — 100ms, micro-interactions (icon press) */
    const val DurationFast = 100L

    /** Normal — 200ms, default for most UI transitions */
    const val DurationNormal = 200L

    /** Slow — 350ms, larger elements (cards, rows) */
    const val DurationSlow = 350L

    /** Enter — 400ms, content appearing */
    const val DurationEnter = 400L

    /** Exit — 250ms, content leaving */
    const val DurationExit = 250L

    // ── Focus scale constants (mirrors TvFocus.kt) ───────────────────────────

    const val FocusScale = 1.04f
    const val PressScale = 0.95f
}

/**
 * Animates the scale of a focusable composable to the focused/pressed state.
 *
 * Usage:
 * ```
 * val scale by animateFocusScale(isFocused = isFocused, isPressed = isPressed)
 * Modifier.scale(scale)
 * ```
 */
@Composable
@Stable
fun animateFocusScale(isFocused: Boolean, isPressed: Boolean): Float {
    val target = when {
        isPressed -> RizzoMotion.PressScale
        isFocused -> RizzoMotion.FocusScale
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = RizzoMotion.DefaultSpring,
        label = "focusScale",
    )
    return scale
}
