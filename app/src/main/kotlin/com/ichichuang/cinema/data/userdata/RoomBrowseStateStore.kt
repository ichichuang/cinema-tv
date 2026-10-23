package com.ichichuang.cinema.data.userdata

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.data.local.entity.BrowseSnapshotEntity
import com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.domain.repository.BrowseStateStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

internal class RoomBrowseStateStore(private val database: CinemaDatabase) : BrowseStateStore {
    override suspend fun save(snapshot: BrowseSnapshot): CommitResult = storageCommit {
        database.withTransaction {
            database.browseSnapshots().upsert(BrowseSnapshotEntity(snapshot.contextKey, BrowseSnapshotCodec.encode(snapshot)))
        }
    }

    override suspend fun load(contextKey: String): BrowseSnapshot? {
        currentCoroutineContext().ensureActive()
        require(contextKey.isNotBlank()) { "Invalid browse context" }
        val row = try {
            database.browseSnapshots().find(contextKey)
        } catch (_: SQLiteException) {
            throw StorageReadException(StorageReadFailure.IO)
        } catch (_: IOException) {
            throw StorageReadException(StorageReadFailure.IO)
        } ?: return null
        return BrowseSnapshotCodec.decode(row.payload).also {
            if (it.contextKey != contextKey) throw StorageReadException(StorageReadFailure.MALFORMED)
        }
    }
}

internal suspend fun storageCommit(write: suspend () -> Unit): CommitResult {
    currentCoroutineContext().ensureActive()
    return try {
        write()
        CommitResult.Committed
    } catch (_: SQLiteException) {
        CommitResult.Failed("storage_sql")
    } catch (_: IOException) {
        CommitResult.Failed("storage_io")
    }
}
