package com.rizzoplayer.iptv.ui.navigation

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for cross-screen focus state.
 *
 * Replaces the two-tick system (contentFocusRestorer + focusContentTick)
 * which had a race condition: onFocusChanged in the outer box and
 * LaunchedEffect(items) in content both fired on navigation, whichever
 * ran last overwrote the other's focus request.
 *
 * HOW IT WORKS:
 * - Sidebar click → FocusManager.requestContentFocus() → flag set TRUE
 * - Content observes flag → restores focus → clears flag
 * - No racing LaunchedEffects, no arbitrary delays
 *
 * Also tracks last-focused sidebar index for return navigation
 * and per-section item indices for position memory.
 *
 * Phase 1: Focus state tracking only.
 * Phase 2-7: Sidebar index tracking, section index tracking.
 */
object FocusManager {

    // ── Section type (local — avoids circular dep with MainViewModel) ───────

    enum class Section { LIVE, VOD, SERIES, FAVORITES }

    // ── Current active section ────────────────────────────────────────────────

    private val _currentSection = MutableStateFlow(Section.LIVE)
    val currentSection: StateFlow<Section> = _currentSection.asStateFlow()

    fun setCurrentSection(section: Section) {
        _currentSection.value = section
    }

    // ── Sidebar → Content focus restoration ────────────────────────────────

    /**
     * Flag: should content composables restore focus to their first item?
     * Single boolean replaces the int-tick system.
     *
     * Set TRUE by HomeScreen when sidebar selects a new section.
     * Content composables observe this, restore focus, then clear it.
     */
    private val _shouldRestoreContentFocus = MutableStateFlow(false)
    val shouldRestoreContentFocus: StateFlow<Boolean> = _shouldRestoreContentFocus.asStateFlow()

    /** Sidebar calls this when user clicks a section */
    fun requestContentFocus() {
        _shouldRestoreContentFocus.value = false  // reset first for reliable recomposition
        _shouldRestoreContentFocus.value = true
    }

    /** Content calls this after restoring focus */
    fun clearContentFocusRequest() {
        _shouldRestoreContentFocus.value = false
    }

    // ── Sidebar last-focused index ─────────────────────────────────────────

    /** Which sidebar nav item was last focused (for return navigation) */
    private val _lastSidebarFocusedIndex = mutableIntStateOf(0)
    val lastSidebarFocusedIndex: Int
        get() = _lastSidebarFocusedIndex.intValue

    fun setSidebarFocusedIndex(index: Int) {
        _lastSidebarFocusedIndex.intValue = index
    }

    // ── Content last-focused item index per section ─────────────────────────

    /** Per-section focus position memory */
    private val sectionFocusIndices = mutableMapOf<Section, Int>()

    fun getSectionFocusIndex(section: Section): Int =
        sectionFocusIndices[section] ?: 0

    fun setSectionFocusIndex(section: Section, index: Int) {
        sectionFocusIndices[section] = index
    }
}
