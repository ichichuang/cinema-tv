package com.ichichuang.cinema.data.local.migration

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.local.entity.LegacyImportBatchEntity
import com.ichichuang.cinema.data.local.entity.LegacyImportRecordEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

internal data class LegacyImportReport(val totalCount: Int, val recognizedCount: Int, val unresolvedCount: Int, val malformedCount: Int)
internal sealed interface LegacyImportResult {
    data class Preserved(val report: LegacyImportReport) : LegacyImportResult
    data class AlreadyPreserved(val report: LegacyImportReport) : LegacyImportResult
    data class Failed(val reason: String) : LegacyImportResult
}
internal enum class LegacyImportPhase { ROWS_STAGED, MARKER_WRITTEN, COMMITTED }
private class ImportAccountingException : RuntimeException("import_accounting")

/** Staging only. The hook is a deterministic test seam around real transaction boundaries. */
internal class LegacySnapshotImporter(
    private val database: CinemaDatabase,
    private val atPhase: suspend (LegacyImportPhase) -> Unit = {},
) {
    suspend fun preserve(snapshot: LegacySnapshot): LegacyImportResult {
        currentCoroutineContext().ensureActive()
        return try {
            val result = database.withTransaction {
                val dao = database.legacyImports()
                val existing = dao.batch(IMPORT_VERSION, snapshot.contentDigest)
                if (existing != null) {
                    val rows = dao.records(IMPORT_VERSION, snapshot.contentDigest)
                    if (existing.totalCount != snapshot.entries.size || existing.report() != rows.report() ||
                        rows.toSet() != snapshot.stagingRows().toSet()) throw ImportAccountingException()
                    LegacyImportResult.AlreadyPreserved(existing.report())
                } else {
                    val rows = snapshot.stagingRows()
                    dao.insertRecords(rows)
                    atPhase(LegacyImportPhase.ROWS_STAGED)
                    currentCoroutineContext().ensureActive()
                    val stored = dao.records(IMPORT_VERSION, snapshot.contentDigest)
                    val report = stored.report()
                    if (stored.toSet() != rows.toSet() || report.totalCount != snapshot.entries.size ||
                        report.totalCount != report.recognizedCount + report.unresolvedCount + report.malformedCount) throw ImportAccountingException()
                    dao.insertBatch(LegacyImportBatchEntity(IMPORT_VERSION, snapshot.contentDigest, snapshot.capturedAt,
                        report.totalCount, report.recognizedCount, report.unresolvedCount, report.malformedCount))
                    atPhase(LegacyImportPhase.MARKER_WRITTEN)
                    currentCoroutineContext().ensureActive()
                    LegacyImportResult.Preserved(report)
                }
            }
            // Losing acknowledgement here cannot roll back a transaction that has already committed.
            atPhase(LegacyImportPhase.COMMITTED)
            result
        } catch (_: SQLiteException) {
            LegacyImportResult.Failed("storage_sql")
        } catch (_: IOException) {
            LegacyImportResult.Failed("storage_io")
        } catch (_: ImportAccountingException) {
            LegacyImportResult.Failed("import_accounting")
        }
    }

    companion object { const val IMPORT_VERSION = 1 }
}

private fun LegacyImportBatchEntity.report() = LegacyImportReport(totalCount, recognizedCount, unresolvedCount, malformedCount)
private fun List<LegacyImportRecordEntity>.report() = LegacyImportReport(size, count { it.status == "recognized" },
    count { it.status == "unresolved" }, count { it.status == "malformed" })
private fun LegacySnapshot.stagingRows() = entries.map { entry ->
    val analysis = LegacyEntryClassifier.classify(entry)
    val coordinates = analysis.coordinates
    LegacyImportRecordEntity(LegacySnapshotImporter.IMPORT_VERSION, contentDigest, entry.store.tag, entry.key,
        entry.value.type, entry.value.encoded, analysis.status.tag, analysis.reason,
        coordinates?.lineIndex, coordinates?.episodeIndex, coordinates?.positionMs, coordinates?.durationMs)
}
