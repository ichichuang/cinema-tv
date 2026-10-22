package com.ichichuang.cinema.data.update

import com.ichichuang.cinema.data.remote.SourceException
import java.io.File

/** Owns partial files, including transfer, size-check and rename failures. */
internal object UpdateDownloadFiles {
    fun write(target: File, expectedBytes: Long, transfer: (File) -> Unit): File {
        val partial = File(target.parentFile, "${target.nameWithoutExtension}.part")
        if (partial.exists() && !partial.delete()) {
            throw SourceException("无法清理上次的下载，请检查电视存储空间")
        }
        try {
            transfer(partial)
            if (expectedBytes > 0 && partial.length() != expectedBytes) {
                throw SourceException("下载不完整，已丢弃")
            }
            if (!partial.renameTo(target)) {
                throw SourceException("保存安装包失败，电视存储空间可能不够")
            }
            return target
        } finally {
            partial.delete()
        }
    }
}
