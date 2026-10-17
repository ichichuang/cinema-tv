package com.ichichuang.cinema.data.settings

import android.content.Context
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 海报缓存清理。
 *
 * 电视（尤其 32 位机型）的可用空间经常只剩几百 MB，海报缓存攒久了会明显占地方。
 * 缓存对用户是"看不见的负担"，所以必须给一个能真清掉的入口 ——
 * 而不是在设置页写一句"自动管理"糊弄过去。
 */
object ImageCache {

    /**
     * 清空内存 + 磁盘缓存。
     * @return 清掉的磁盘字节数；`null` 表示这次没清成功（不夸大战果，由界面如实说明）
     */
    suspend fun clear(context: Context): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            val disk = loader.diskCache
            val bytes = runCatching { disk?.size ?: 0L }.getOrDefault(0L)
            disk?.clear()
            bytes
        }.getOrNull()
    }
}
