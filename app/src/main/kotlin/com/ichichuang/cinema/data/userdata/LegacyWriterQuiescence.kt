package com.ichichuang.cinema.data.userdata

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class HandoffWriter { LEGACY, ROOM, BLOCKED }
internal class LegacyWriterQuiescence {
    private val mutex = Mutex()
    suspend fun <T> exclusive(action: suspend () -> T): T = mutex.withLock {
        currentCoroutineContext().ensureActive()
        action()
    }
    suspend fun <T> legacy(writer: suspend () -> HandoffWriter, action: suspend () -> T): T = exclusive {
        check(writer() == HandoffWriter.LEGACY) { "旧记录写入已停止，请保留数据并检查交接状态" }
        action()
    }
}
