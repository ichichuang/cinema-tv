package com.ichichuang.cinema.feature.home.visual

/**
 * The focusable keys of the settings screen, in D-pad order.
 *
 * The source section's retry button sits between the "动效" item and the two
 * maintenance items in the layout, so it must be part of the ordered key list —
 * otherwise a remote that only walks [VisualHomeRuntime.SETTINGS_ITEMS] can never
 * reach it (the defect this function guards against). The retry key is present
 * only when the source slice is in a retryable state.
 */
internal fun settingsFocusKeys(display: SettingsDisplayState): List<String> {
    val retryable = display.source?.let { SettingsProjection.retryable(it) } == true ||
        display.status == SettingsPageStatus.ERROR
    return buildList {
        addAll(listOf("大字模式", "筛选栏", "列数", "减弱动效"))
        if (retryable) add(SettingsSourceSection.RETRY_KEY)
        addAll(listOf("刷新目录", "重载首页"))
    }
}

/**
 * Focusable keys owned by the source-management section of the settings screen.
 * Distinct from [VisualHomeRuntime.SETTINGS_ITEMS] so the retry (and any future
 * add-source) button can join the D-pad order via [settingsFocusKeys].
 */
internal object SettingsSourceSection {
    /** The retry button shown when the source slice is in a retryable failure state. */
    const val RETRY_KEY = "设置来源重试"
}

/**
 * Settings page display projection: only states that the runtime can verify.
 *
 * The four page states (loading / empty / error / content) and the source line
 * are all derived by [SettingsProjection.project] from observable facts; no
 * fabricated totals, no invented "recent result" when none was read.
 */
internal enum class SettingsPageStatus { LOADING, EMPTY, ERROR, CONTENT }

/**
 * A single source-management line, stating only what is known.
 * [activeCount] is the number of enabled sources (0 = unconfigured);
 * [readStatus] is the last catalogue read outcome;
 * [failedSourceCount] is how many enabled sources failed on the last read;
 * [reason] is the human-readable cause of a failure, when one was observed.
 */
internal data class SettingsSourceState(
    val activeCount: Int,
    val readStatus: HomeContentStatus,
    val failedSourceCount: Int,
    val reason: String? = null,
)

/**
 * The projected settings screen.
 * [source] is null only while the page is still loading.
 */
internal data class SettingsDisplayState(
    val status: SettingsPageStatus,
    val source: SettingsSourceState?,
)

/**
 * Pure projection for the settings slice. Kept side-effect free so the four
 * states and the source line are JVM-testable without a composition.
 */
internal object SettingsProjection {

    /**
     * [loading] is true until the first catalogue read has landed;
     * [loadFailed] is true when the read threw;
     * [activeCount] is the number of enabled sources;
     * [readStatus]/[failedSourceCount] mirror the home read result;
     * [reason] is the observed cause of a failed read, when known.
     */
    fun project(
        loading: Boolean,
        loadFailed: Boolean,
        activeCount: Int,
        readStatus: HomeContentStatus,
        failedSourceCount: Int,
        reason: String? = null,
    ): SettingsDisplayState {
        val pageStatus = when {
            loadFailed -> SettingsPageStatus.ERROR
            loading -> SettingsPageStatus.LOADING
            activeCount == 0 -> SettingsPageStatus.EMPTY
            else -> SettingsPageStatus.CONTENT
        }
        val source = if (loading) null else SettingsSourceState(activeCount, readStatus, failedSourceCount, reason)
        return SettingsDisplayState(pageStatus, source)
    }

    /** The source line text; states only verifiable facts, never a fake count. */
    fun sourceLine(state: SettingsSourceState): String = when {
        state.activeCount == 0 -> "未配置任何来源；片库目录为空属正常状态，个人记录不受影响"
        state.readStatus == HomeContentStatus.OFFLINE -> "已配置 ${state.activeCount} 个来源；当前离线，最近一次读取未完成"
        state.readStatus == HomeContentStatus.FAILED ->
            "已配置 ${state.activeCount} 个来源；最近一次读取失败" + (state.reason?.let { "（$it）" } ?: "")
        state.failedSourceCount > 0 ->
            "已配置 ${state.activeCount} 个来源，其中 ${state.failedSourceCount} 个读取失败；结果不代表全部片库" +
                (state.reason?.let { "（$it）" } ?: "")
        state.readStatus == HomeContentStatus.PARTIAL -> "已配置 ${state.activeCount} 个来源；最近一次读取仅部分覆盖"
        else -> "已配置 ${state.activeCount} 个来源；最近一次读取已完成"
    }

    /**
     * Whether the source line carries a retryable failure. A failed read is
     * retryable; so are offline (a retry re-checks connectivity) and a partial
     * read with failures (a retry re-queries the failing sources).
     */
    fun retryable(state: SettingsSourceState): Boolean = when (state.readStatus) {
        HomeContentStatus.FAILED, HomeContentStatus.OFFLINE -> true
        HomeContentStatus.PARTIAL -> state.failedSourceCount > 0
        else -> false
    }
}
