package com.ichichuang.cinema.data.update.auth

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

internal data class UpdateReplayRecord(
    val sequence: Long = 0, val digest: String = "-", val version: Long = 0,
    val completed: Boolean = false, val lastClock: Long = 0,
)

/** App-private state only. Missing/corrupt state never implicitly resets the replay floor. */
internal class FileUpdateReplayStore(directory: File) {
    private val directory = directory.canonicalFile
    private val state get() = File(directory, "update-replay")

    fun initialize() = locked {
        authenticated(!state.exists())
        persist(UpdateReplayRecord())
    }

    fun read(): UpdateReplayRecord = locked { load() }

    fun update(change: (UpdateReplayRecord) -> UpdateReplayRecord): UpdateReplayRecord = locked {
        change(load()).also(::persist)
    }

    private fun load(): UpdateReplayRecord {
        authenticated(state.isFile && state.length() in 1..512)
        try {
            val lines = state.readText(Charsets.US_ASCII).split('\n')
            authenticated(lines.size == 7 && lines[0] == "cinema-update-state/1" && lines[6] == "")
            fun number(index: Int): Long {
                authenticated(Regex("0|[1-9][0-9]{0,18}").matches(lines[index]))
                return lines[index].toLongOrNull() ?: throw UpdateAuthenticationException("Invalid replay state")
            }
            val sequence = number(1); val version = number(3); val clock = number(5)
            authenticated(lines[4] in setOf("0", "1"))
            val record = UpdateReplayRecord(sequence, lines[2], version, lines[4] == "1", clock)
            authenticated(if (sequence == 0L) record == UpdateReplayRecord() else
                Regex("[0-9a-f]{64}").matches(record.digest) && version in 1..Int.MAX_VALUE.toLong()
                    && clock >= SignedUpdateWire.MIN_CLOCK)
            return record
        } catch (error: UpdateAuthenticationException) { throw error }
        catch (_: Exception) { throw UpdateAuthenticationException("Cannot read replay state") }
    }

    private fun persist(record: UpdateReplayRecord) {
        val staged = File(directory, "update-replay.next")
        try {
            val bytes = ("cinema-update-state/1\n${record.sequence}\n${record.digest}\n${record.version}\n"
                + "${if (record.completed) 1 else 0}\n${record.lastClock}\n").toByteArray(Charsets.US_ASCII)
            FileOutputStream(staged).use { it.write(bytes); it.fd.sync() }
            authenticated(staged.renameTo(state))
        } finally { staged.delete() }
    }

    private fun <T> locked(action: () -> T): T = synchronized(processLock) {
        try {
            authenticated(directory.isDirectory && directory.canonicalFile == directory.absoluteFile)
            RandomAccessFile(File(directory, "update-replay.lock"), "rw").use { lockFile ->
                lockFile.channel.lock().use { action() }
            }
        } catch (error: UpdateAuthenticationException) { throw error }
        catch (_: Exception) { throw UpdateAuthenticationException("Replay storage unavailable") }
    }

    private companion object { val processLock = Any() }
}
