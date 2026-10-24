package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.data.local.CinemaDatabase
import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.entity.CandidateCapabilityStateEntity
import com.ichichuang.cinema.data.local.codec.StorageReadException
import com.ichichuang.cinema.data.local.codec.StorageReadFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Explicit candidate file-backed persistence, never wired into Application or DI. */
internal class RoomSourceCapabilityPersistence(private val database: CinemaDatabase) : SourceCapabilityPersistence {
    override suspend fun load(): String? {
        val payload=database.querySubscription().capability()?.payload ?: return null
        // Bound before parsing the persisted JSON; oversized/corrupt input is never an empty cache.
        if (payload.length > 1_048_576) throw StorageReadException(StorageReadFailure.MALFORMED)
        return payload
    }
    override suspend fun save(snapshot: String) {
        require(snapshot.length <= 1_048_576)
        SourceCapabilityCacheCodec.decode(snapshot)
        database.withTransaction {
            database.querySubscription().capability(CandidateCapabilityStateEntity(payload=snapshot))
            currentCoroutineContext().ensureActive()
        }
    }
}
