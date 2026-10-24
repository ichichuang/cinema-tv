package com.ichichuang.cinema.data.userdata

import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.domain.model.userdata.*
import com.ichichuang.cinema.domain.repository.PlaybackProgressStore
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.data.local.entity.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

internal class RoomPlaybackProgressStore(
    private val database: CinemaDatabase,
    private val beforeCommit: suspend () -> Unit = {},
    private val afterCommit: suspend () -> Unit = {},
) : PlaybackProgressStore {
    suspend fun beginSession(target: PlaybackTarget, sessionId: String): CommitResult {
        require(sessionId.isNotBlank()) { "Session identity is required" }
        return storageResult {
            database.withTransaction {
                val dao = database.playbackSessions()
                val existing = dao.session(sessionId)
                if (existing != null) {
                    if (existing.matches(target)) CommitResult.Committed else CommitResult.RejectedStale
                } else {
                    database.userRecords().insertReference(WorkReferenceEntity(target.workId.value))
                    val generation = dao.insertSession(PlaybackSessionEntity(sessionId = sessionId,
                        workId = target.workId.value, episodeKey = target.episodeId?.value ?: "", variantKey = target.variantId?.value ?: ""))
                    dao.upsertOrder(PlaybackRecordOrderEntity(target.workId.value, target.episodeId?.value ?: "",
                        target.variantId?.value ?: "", generation))
                    CommitResult.Committed
                }
            }
        }
    }

    override suspend fun commit(snapshot: PlaybackSnapshot): CommitResult = storageResult {
        val outcome = database.withTransaction {
            val dao = database.playbackSessions()
            val session = dao.session(snapshot.sessionId) ?: return@withTransaction CommitResult.RejectedStale
            val target = PlaybackTarget(snapshot.workId, snapshot.episodeId, snapshot.variantId)
            if (!session.matches(target)) return@withTransaction CommitResult.RejectedStale
            val order = dao.order(session.workId, session.episodeKey, session.variantKey)
            if (order?.generation != session.generation) return@withTransaction CommitResult.RejectedStale
            val row = database.playbackSnapshots().find(session.workId, session.episodeKey, session.variantKey)
            val payload = PlaybackSnapshotCodec.encode(snapshot)
            if (snapshot.operationSequence <= session.sequence) {
                return@withTransaction if (snapshot.operationSequence == session.sequence && session.payload == payload && row?.payload == payload)
                    CommitResult.Committed else CommitResult.RejectedStale
            }
            // An unviewed preparation cannot downgrade an existing viewing record.
            if (snapshot.watchState == WatchState.NOT_STARTED && row != null && row.snapshot().watchState != WatchState.NOT_STARTED) {
                return@withTransaction CommitResult.RejectedStale
            }
            database.playbackSnapshots().upsert(PlaybackSnapshotEntity.from(snapshot))
            dao.updateSession(session.copy(sequence = snapshot.operationSequence, payload = payload))
            if (snapshot.watchState != WatchState.NOT_STARTED && dao.latestGeneration(session.workId) == session.generation) {
                dao.upsertResume(PlaybackResumeEntity(session.workId, session.generation, payload))
            }
            beforeCommit()
            currentCoroutineContext().ensureActive()
            CommitResult.Committed
        }
        if (outcome == CommitResult.Committed) afterCommit()
        outcome
    }

    suspend fun loadResume(workId: WorkId): PlaybackSnapshot? {
        currentCoroutineContext().ensureActive()
        val row = try { database.playbackSessions().resume(workId.value) }
        catch (_: SQLiteException) { throw StorageReadException(StorageReadFailure.IO) }
        catch (_: IOException) { throw StorageReadException(StorageReadFailure.IO) }
        return row?.let { PlaybackSnapshotCodec.decode(it.payload).also { snapshot ->
            if (snapshot.workId != workId) throw StorageReadException(StorageReadFailure.MALFORMED)
        } }
    }

    private fun PlaybackSessionEntity.matches(target: PlaybackTarget) = workId == target.workId.value &&
        episodeKey == (target.episodeId?.value ?: "") && variantKey == (target.variantId?.value ?: "")

    private suspend fun storageResult(action: suspend () -> CommitResult): CommitResult {
        currentCoroutineContext().ensureActive()
        return try { action() }
        catch (_: SQLiteException) { CommitResult.Failed("storage_sql") }
        catch (_: IOException) { CommitResult.Failed("storage_io") }
        catch (_: StorageReadException) { CommitResult.Failed("storage_read") }
    }
}
