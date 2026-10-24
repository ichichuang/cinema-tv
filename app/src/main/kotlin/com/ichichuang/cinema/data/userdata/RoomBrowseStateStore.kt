package com.ichichuang.cinema.data.userdata

import android.database.sqlite.SQLiteException
import com.ichichuang.cinema.core.navigation.*
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

internal class RoomBrowseStateStore(private val database: CinemaDatabase) : BrowseStateStore, BrowseNavigationStore {
    companion object { const val ACTIVE_NAVIGATION_KEY = "\u0000cinema.browse.active-navigation" }

    override suspend fun saveActive(state: RestoreNavigationState): CommitResult {
        require(state.snapshot.contextKey != ACTIVE_NAVIGATION_KEY)
        return storageCommit {
            database.withTransaction {
                browseNavigationRecords(state).forEach { database.browseSnapshots().upsert(it) }
            }
        }
    }

    override suspend fun loadLatest(topLevel: BrowseTopLevel): RestoreNavigationState? = loadNavigation("$ACTIVE_NAVIGATION_KEY.${topLevel.route}", topLevel)

    override suspend fun loadActive(): RestoreNavigationState? = loadNavigation(ACTIVE_NAVIGATION_KEY)

    private suspend fun loadNavigation(key: String, topLevel: BrowseTopLevel? = null): RestoreNavigationState? {
        currentCoroutineContext().ensureActive()
        val row = try { database.browseSnapshots().find(key) }
        catch (_: SQLiteException) { throw StorageReadException(StorageReadFailure.IO) }
        catch (_: IOException) { throw StorageReadException(StorageReadFailure.IO) }
        return row?.let {
            if (it.contextKey != key) throw StorageReadException(StorageReadFailure.MALFORMED)
            decodeBrowseNavigationPointer(it.payload, topLevel)
        }
    }

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
