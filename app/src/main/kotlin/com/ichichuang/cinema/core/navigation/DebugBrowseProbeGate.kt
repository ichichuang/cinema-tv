package com.ichichuang.cinema.core.navigation

import android.content.Context
import com.ichichuang.cinema.BuildConfig
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.local.CinemaDatabaseFactory
import com.ichichuang.cinema.data.userdata.LegacyHandoffRuntime

/** A scoped lease on the existing writer mutex; candidate stores must not outlive [withOwnedProfile]. */
internal object DebugBrowseProbeGate {
    const val DATABASE = "round11-browse-restore.db"

    fun requireProfile(compiled: Boolean, debug: Boolean, authorized: Boolean,
        handoffPresent: Boolean, legacyPopulated: Boolean): Boolean {
        if (!compiled) return false
        check(debug && authorized && !handoffPresent && !legacyPopulated) {
            "浏览恢复测试需要已授权、无旧记录且未进行旧数据交接的独立 Debug 环境"
        }
        return true
    }

    /** OFF performs no authorization reads, legacy reads, lock acquisition or database construction. */
    suspend fun <T> withOwnedProfile(context: Context, action: suspend (CinemaDatabase) -> T): T? {
        if (!BuildConfig.BROWSE_RESTORE_PROBE) return null
        check(BuildConfig.PLAYBACK_ROOM_PROBE) { "浏览恢复测试缺少已有播放测试授权" }
        return LegacyHandoffRuntime.writers.exclusive {
            requireProfile(true, BuildConfig.DEBUG, LegacyHandoffRuntime.authorized(context),
                LegacyHandoffRuntime.present(context), false)
            requireProfile(true, BuildConfig.DEBUG, true, false,
                LegacyHandoffRuntime.capture(context).entries.isNotEmpty())
            val database = CinemaDatabaseFactory.open(context, DATABASE)
            try {
                // Force schema/integrity validation before exposing any browse writer to the route.
                database.openHelper.writableDatabase
                action(database)
            } finally {
                database.close()
            }
        }
    }
}
