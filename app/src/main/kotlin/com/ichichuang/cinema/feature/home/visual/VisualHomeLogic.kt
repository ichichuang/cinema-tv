package com.ichichuang.cinema.feature.home.visual

import com.ichichuang.cinema.domain.model.catalog.SourceId

/**
 * Pure decision logic for the candidate home/settings runtime, extracted from
 * [VisualHomeRuntime] so the dispatch, navigation and read-fact derivations are
 * JVM-testable without a composition or an Android [android.content.Context].
 *
 * Every function here is a straight relocation of a decision that used to live
 * inline in the runtime; the runtime now delegates to these so a mutation that
 * guts a branch (for example "减弱动效" dispatching to nothing, or the retry key
 * dropping out of the focus order) is caught by a test that calls this production
 * code — not a re-implemented stand-in.
 */

/**
 * The observable effect a settings action must produce. Kept as an enum so the
 * dispatch mapping can be asserted value-for-value; a branch that silently maps
 * to [NONE] fails the corresponding test.
 */
internal enum class SettingsEffect {
    TOGGLE_LARGE_TEXT,
    TOGGLE_FILTER,
    CHANGE_COLUMNS,
    TOGGLE_REDUCE_MOTION,
    REFRESH,
    RELOAD,
    NONE,
}

/**
 * Resolves a settings item label to the effect it must trigger.
 *
 * This is the single dispatch authority: [VisualHomeRuntime.activateSettingsItem]
 * applies whatever this returns, so the "减弱动效" item is proven to resolve to
 * [SettingsEffect.TOGGLE_REDUCE_MOTION] by a JVM test. A mutation that drops the
 * branch (or maps it to [SettingsEffect.NONE]) fails that test.
 */
internal fun settingsEffectFor(label: String): SettingsEffect = when (label) {
    "大字模式" -> SettingsEffect.TOGGLE_LARGE_TEXT
    "筛选栏" -> SettingsEffect.TOGGLE_FILTER
    "列数" -> SettingsEffect.CHANGE_COLUMNS
    "减弱动效" -> SettingsEffect.TOGGLE_REDUCE_MOTION
    "刷新目录" -> SettingsEffect.REFRESH
    "重载首页" -> SettingsEffect.RELOAD
    else -> SettingsEffect.NONE
}

/**
 * Whether a confirm press on [focusedKey] must trigger a retry.
 *
 * The settings screen confirms a retry only when the retry key is both in the
 * focus order (via [settingsFocusKeys]) and is the key being activated; any
 * other key never retries. A mutation that drops the retry branch fails the
 * confirmation test.
 */
internal fun isRetryConfirm(focusedKey: String, display: SettingsDisplayState): Boolean =
    focusedKey == SettingsSourceSection.RETRY_KEY &&
        settingsFocusKeys(display).contains(SettingsSourceSection.RETRY_KEY)

/**
 * The human-readable reason for a catalogue read failure.
 *
 * Never drops the cause: a throwable always yields a non-null reason (falling
 * back to a fixed message when the exception carries none), so the settings
 * slice can state why a read failed. A mutation that returns null here (or in
 * the runtime's [VisualHomeRuntime] capture) fails the reason test.
 */
internal fun readFailureReason(cause: Throwable?): String =
    cause?.message?.takeIf { it.isNotBlank() } ?: "未知原因"

/**
 * The number of enabled sources, derived from the fixture query only.
 *
 * Zero means unconfigured; the settings slice and the home screen share this
 * single fact. A mutation that hard-codes zero fails the source-count test.
 */
internal fun configuredSourceCount(enabledSourceIds: Set<SourceId>): Int = enabledSourceIds.size

/**
 * Whether the settings page entrance must actually play.
 *
 * The page enters with [androidx.compose.animation.core.MutableTransitionState]
 * seeded to `false` and flipped to `true` after composition; a mutation that
 * never flips it would leave the page invisible. Kept as a pure predicate over
 * the raw "first frame" flag so the flip decision is testable.
 */
internal fun shouldTriggerEntrance(firstCompositionDone: Boolean): Boolean = firstCompositionDone

/**
 * The ordered steps a retry must run, returned as a pure list so a mutation that
 * drops the connectivity re-check is caught.
 *
 * [VisualHomeRuntime.retry] applies these in order: refresh the offline flag
 * first (so a retry that re-gained network actually re-checks connectivity),
 * then reload. A mutation that removes the offline refresh fails the test.
 */
internal fun retrySteps(): List<String> = listOf(
    "refreshOffline",
    "reload",
)
