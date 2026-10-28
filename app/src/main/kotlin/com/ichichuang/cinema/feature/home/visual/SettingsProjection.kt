package com.ichichuang.cinema.feature.home.visual

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
 * [readStatus] is the last catalogue read outcome.
 */
internal data class SettingsSourceState(
    val activeCount: Int,
    val readStatus: HomeContentStatus,
    val failedSourceCount: Int,
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
     * [readStatus]/[failedSourceCount] mirror the home read result.
     */
    fun project(
        loading: Boolean,
        loadFailed: Boolean,
        activeCount: Int,
        readStatus: HomeContentStatus,
        failedSourceCount: Int,
    ): SettingsDisplayState {
        val pageStatus = when {
            loadFailed -> SettingsPageStatus.ERROR
            loading -> SettingsPageStatus.LOADING
            activeCount == 0 -> SettingsPageStatus.EMPTY
            else -> SettingsPageStatus.CONTENT
        }
        val source = if (loading) null else SettingsSourceState(activeCount, readStatus, failedSourceCount)
        return SettingsDisplayState(pageStatus, source)
    }

    /** The source line text; states only verifiable facts, never a fake count. */
    fun sourceLine(state: SettingsSourceState): String = when {
        state.activeCount == 0 -> "未配置任何来源；片库目录为空属正常状态，个人记录不受影响"
        state.readStatus == HomeContentStatus.OFFLINE -> "已配置 ${state.activeCount} 个来源；当前离线，最近一次读取未完成"
        state.readStatus == HomeContentStatus.FAILED -> "已配置 ${state.activeCount} 个来源；最近一次读取失败"
        state.failedSourceCount > 0 -> "已配置 ${state.activeCount} 个来源，其中 ${state.failedSourceCount} 个读取失败；结果不代表全部片库"
        state.readStatus == HomeContentStatus.PARTIAL -> "已配置 ${state.activeCount} 个来源；最近一次读取仅部分覆盖"
        else -> "已配置 ${state.activeCount} 个来源；最近一次读取已完成"
    }

    /** Whether the source line carries a retryable failure. */
    fun retryable(state: SettingsSourceState): Boolean =
        state.readStatus == HomeContentStatus.FAILED
}
