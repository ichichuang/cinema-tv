package com.ichichuang.cinema.data.userdata

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.AtomicFile
import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.local.CinemaDatabaseFactory
import com.ichichuang.cinema.data.local.entity.LegacyHandoffEntity
import com.ichichuang.cinema.data.local.migration.*
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.data.local.codec.StorageReadException
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

internal enum class HandoffPhase { CAPTURED, STAGED, VERIFIED, ACTIVATING, ACTIVATED }
internal class HandoffUnavailableException : IllegalStateException("交接数据或授权不可用，已停止写入；原记录保持")
private class HandoffIntegrityException : RuntimeException("handoff_integrity")
internal class LegacyHandoffController private constructor(private val context: Context, val database: CinemaDatabase,
    private val atPhase: suspend (HandoffPhase) -> Unit) : java.io.Closeable {
    private fun denied(manifest: HandoffManifest, reason: String) =
        LegacyHandoffAudit(false, reason, manifest.expectedDigest, manifest.mappingDigest, emptyList())
    private fun requireAuthorization() { if (!LegacyHandoffRuntime.authorized(context)) throw HandoffIntegrityException() }
    private suspend fun preserved(state: LegacyHandoffEntity): LegacySnapshot {
        val batch = database.legacyImports().batch(state.importVersion, state.contentDigest) ?: throw HandoffIntegrityException()
        val rows = database.legacyImports().records(state.importVersion, state.contentDigest)
        val snapshot = LegacySnapshot.create(rows.map { row -> LegacyEntry(
            LegacyStoreKind.entries.singleOrNull { it.tag == row.storeKind } ?: throw HandoffIntegrityException(), row.entryKey,
            LegacyValue.fromEncoded(row.valueType, Json.parseToJsonElement(row.rawValue))) }, batch.capturedAt)
        if (snapshot.contentDigest != state.contentDigest || rows.size != state.totalCount ||
            LegacySnapshotImporter(database).preserve(snapshot) !is LegacyImportResult.AlreadyPreserved) throw HandoffIntegrityException()
        return snapshot
    }
    suspend fun prepare(manifest: HandoffManifest): LegacyHandoffAudit = LegacyHandoffRuntime.writers.exclusive {
        guarded({ denied(manifest, "storage_or_integrity") }) {
            requireAuthorization()
            val current = database.legacyHandoff().state()
            if (current?.phase == "room_active") {
                if (writer() != HandoffWriter.ROOM || current.mappingDigest != manifest.mappingDigest ||
                    !manifest.catalogIsCurrent()) throw HandoffIntegrityException()
                return@guarded LegacyHandoffAudit.inspect(preserved(current), manifest)
            }
            val snapshot = LegacyHandoffRuntime.capture(context)
            atPhase(HandoffPhase.CAPTURED)
            val staged = LegacyHandoffEntity(phase = "staged", contentDigest = snapshot.contentDigest,
                mappingDigest = manifest.mappingDigest, totalCount = snapshot.entries.size)
            database.withTransaction {
                requireAuthorization()
                if (LegacySnapshotImporter(database).preserve(snapshot) is LegacyImportResult.Failed) throw HandoffIntegrityException()
                database.legacyHandoff().put(staged)
            }
            atPhase(HandoffPhase.STAGED)
            val audit = LegacyHandoffAudit.inspect(preserved(staged), manifest)
            if (audit.eligible && !manifest.catalogIsCurrent()) return@guarded denied(manifest, "changed_catalog")
            if (audit.eligible) {
                database.withTransaction { requireAuthorization(); database.legacyHandoff().put(staged.copy(phase = "verified")) }
                atPhase(HandoffPhase.VERIFIED)
            }
            audit
        }
    }
    suspend fun activate(manifest: HandoffManifest, digest: String): CommitResult = LegacyHandoffRuntime.writers.exclusive {
        guarded({ CommitResult.Failed("handoff_storage_or_integrity") }) {
            requireAuthorization()
            val state = database.legacyHandoff().state() ?: throw HandoffIntegrityException()
            if (digest != state.contentDigest || manifest.expectedDigest != digest || manifest.mappingDigest != state.mappingDigest)
                throw HandoffIntegrityException()
            if (state.phase == "room_active") {
                if (writer() != HandoffWriter.ROOM || !manifest.catalogIsCurrent()) throw HandoffIntegrityException()
                return@guarded CommitResult.Committed // Lost acknowledgement retry never replays old checkpoints.
            }
            if (state.phase != "verified") throw HandoffIntegrityException()
            val snapshot = LegacyHandoffRuntime.capture(context)
            atPhase(HandoffPhase.CAPTURED)
            if (snapshot.contentDigest != digest) throw HandoffIntegrityException()
            val audit = LegacyHandoffAudit.inspect(preserved(state), manifest)
            if (!audit.eligible || database.playbackSnapshots().count() != 0) throw HandoffIntegrityException()
            // This durable intent precedes the Room transaction. If the DB later disappears,
            // absence is ambiguous and must block, never create a new empty DB / select stale legacy.
            val latch = AtomicFile(File(context.noBackupFilesDir, LegacyHandoffRuntime.REQUIRED))
            val stream = latch.startWrite()
            try { stream.write(REQUIRED_TOKEN.toByteArray(Charsets.UTF_8)); latch.finishWrite(stream) }
            catch (error: IOException) { latch.failWrite(stream); throw error }
            database.withTransaction {
                requireAuthorization()
                // Re-read the trusted catalog's actual media before any activation writes.
                // A caller-supplied revision alone is not proof of the current timebase.
                if (!manifest.catalogIsCurrent()) throw HandoffIntegrityException()
                val store = RoomPlaybackProgressStore(database)
                for (record in audit.restored) {
                    val target = PlaybackTarget(record.workId, record.episodeId, record.variantId)
                    if (store.beginSession(target, record.sessionId) != CommitResult.Committed ||
                        store.commit(record) != CommitResult.Committed) throw HandoffIntegrityException()
                }
                database.legacyHandoff().put(state.copy(phase = "room_active"))
                atPhase(HandoffPhase.ACTIVATING)
                currentCoroutineContext().ensureActive()
            }
            atPhase(HandoffPhase.ACTIVATED)
            CommitResult.Committed
        }
    }
    suspend fun writer(): HandoffWriter {
        currentCoroutineContext().ensureActive()
        if (!LegacyHandoffRuntime.authorized(context)) return HandoffWriter.BLOCKED
        if (LegacyHandoffRuntime.required(context) && !context.getDatabasePath(LegacyHandoffRuntime.DATABASE).isFile) return HandoffWriter.BLOCKED
        return try {
            val state = database.legacyHandoff().state()
            when (state?.phase) {
                null -> if (LegacyHandoffRuntime.required(context)) HandoffWriter.BLOCKED else HandoffWriter.LEGACY
                "staged", "verified" -> HandoffWriter.LEGACY
                "room_active" -> {
                    val latch = AtomicFile(File(context.noBackupFilesDir, LegacyHandoffRuntime.REQUIRED))
                    if (latch.readFully().toString(Charsets.UTF_8) != REQUIRED_TOKEN ||
                        !state.mappingDigest.matches(Regex("[a-f0-9]{64}"))) return HandoffWriter.BLOCKED
                    preserved(state)
                    HandoffWriter.ROOM
                }
                else -> HandoffWriter.BLOCKED
            }
        } catch (error: CancellationException) { throw error }
        catch (_: SQLiteException) { HandoffWriter.BLOCKED }
        catch (_: IOException) { HandoffWriter.BLOCKED }
        catch (_: HandoffIntegrityException) { HandoffWriter.BLOCKED }
        catch (_: StorageReadException) { HandoffWriter.BLOCKED }
        catch (_: IllegalArgumentException) { HandoffWriter.BLOCKED }
        catch (_: IllegalStateException) { HandoffWriter.BLOCKED }
    }
    suspend fun verifyManifest(manifest: HandoffManifest) = room {
        val state = database.legacyHandoff().state() ?: throw HandoffIntegrityException()
        check(state.contentDigest == manifest.expectedDigest && state.mappingDigest == manifest.mappingDigest) { "合成目录或审计摘要已变化，原记录保持" }
    }
    suspend fun <T> room(action: suspend () -> T): T = LegacyHandoffRuntime.writers.exclusive {
        if (writer() != HandoffWriter.ROOM) throw HandoffUnavailableException()
        action()
    }
    private suspend fun <T> guarded(failed: () -> T, action: suspend () -> T): T = try { action() }
        catch (error: CancellationException) { throw error }
        catch (_: SQLiteException) { failed() }
        catch (_: IOException) { failed() }
        catch (_: HandoffIntegrityException) { failed() }
        catch (_: StorageReadException) { failed() }
        catch (_: IllegalArgumentException) { failed() }
    override fun close() = database.close()
    companion object {
        fun open(context: Context, atPhase: suspend (HandoffPhase) -> Unit = {}): LegacyHandoffController {
            check(LegacyHandoffRuntime.authorized(context))
            check(!LegacyHandoffRuntime.required(context) || context.getDatabasePath(LegacyHandoffRuntime.DATABASE).isFile) {
                "交接数据库缺失，禁止创建空库或退回旧记录写入"
            }
            return LegacyHandoffController(context, CinemaDatabaseFactory.open(context, LegacyHandoffRuntime.DATABASE), atPhase)
        }
        private const val REQUIRED_TOKEN = "round07-room-may-have-committed"
    }
}
