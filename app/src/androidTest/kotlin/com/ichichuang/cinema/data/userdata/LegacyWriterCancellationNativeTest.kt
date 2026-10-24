package com.ichichuang.cinema.data.userdata

import androidx.datastore.core.*
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.*
import com.ichichuang.cinema.data.local.migration.*
import com.ichichuang.cinema.data.settings.editLegacy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class LegacyWriterCancellationNativeTest {
    @Test fun cancelled_writer_in_real_datastore_write_scope_must_drain_before_capture() = runBlocking<Unit> {
        val context = ownedHandoffContext(); resetOwnedHandoffCandidate(context)
        val file = File(context.filesDir, "round07-cancel-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val armed = AtomicBoolean(false)
        val actual = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.absolutePath.toPath() }
        val storage = object : Storage<Preferences> {
            override fun createConnection(): StorageConnection<Preferences> {
                val connection = actual.createConnection()
                return object : StorageConnection<Preferences> by connection {
                    override suspend fun writeScope(block: suspend WriteScope<Preferences>.() -> Unit) = connection.writeScope {
                        if (armed.getAndSet(false)) { entered.complete(Unit); release.await() }
                        block()
                    }
                }
            }
        }
        // Exactly one owner for this new file; the wrapped storage still writes real preferences bytes.
        val store = PreferenceDataStoreFactory.create(storage = storage, scope = scope)
        val key = stringPreferencesKey("wp_fixture-source#fixture-movie")
        try {
            store.edit { it[key] = "0,0,2000,12000" }
            armed.set(true)
            val writer = async(start = CoroutineStart.UNDISPATCHED) { store.editLegacy(context) { it[key] = "0,0,3000,12000" } }
            withTimeout(5_000) { entered.await() }; writer.cancel()
            val capture = async { LegacyHandoffRuntime.writers.exclusive {
                LegacySnapshotReader(mapOf(LegacyStoreKind.PROGRESS to store)).capture(0)
            } }
            val premature = withTimeoutOrNull(250) { capture.await() }
            try { assertNull("migration must not enter while the independent DataStore actor still owns IO", premature) }
            finally { release.complete(Unit) }
            val snapshot = withTimeout(5_000) { capture.await() }
            assertEquals("0,0,3000,12000", snapshot.entries.single().value.stringOrNull())
            assertThrows(CancellationException::class.java) { runBlocking { writer.await() } }
            assertTrue(file.length() > 0)
            println("ROUND07_WRITER_CANCEL actualIoDrained=true callerCancelled=true captured=3000")
        } finally {
            release.complete(Unit)
            if (entered.isCompleted) withTimeoutOrNull(5_000) { store.data.first { it[key] == "0,0,3000,12000" } }
            scope.coroutineContext.job.cancelAndJoin()
            file.delete(); File(file.path + ".tmp").delete()
        }
    }
}
