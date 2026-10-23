package com.ichichuang.cinema.data.local.migration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.util.Collections

/** Caller owns these handles exclusively. No default Context, migration, or corruption handler. */
internal class LegacySnapshotReader(stores: Map<LegacyStoreKind, DataStore<Preferences>>) {
    private val stores = Collections.unmodifiableMap(LinkedHashMap(stores))

    suspend fun capture(capturedAt: Long): LegacySnapshot {
        val entries = mutableListOf<LegacyEntry>()
        for ((kind, store) in stores) {
            currentCoroutineContext().ensureActive()
            for ((key, value) in store.data.first().asMap()) entries += LegacyEntry(kind, key.name, LegacyValue.from(value))
        }
        currentCoroutineContext().ensureActive()
        return LegacySnapshot.create(entries, capturedAt)
    }
}
