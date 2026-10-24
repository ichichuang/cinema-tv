package com.ichichuang.cinema.data.userdata

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ichichuang.cinema.BuildConfig
import com.ichichuang.cinema.data.local.migration.*
import com.ichichuang.cinema.data.settings.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*

internal object LegacyHandoffRuntime {
    const val DATABASE = "round07-legacy-handoff.db"
    const val PROFILE = "round07-profile.authorization"
    const val CONSENT = "round07-migration.authorization"
    const val REQUIRED = "round07-room.required"
    const val PROFILE_TOKEN = "cinema-round07-owned-disposable-profile"
    const val CONSENT_TOKEN = "cinema-round07-explicit-legacy-handoff"
    val writers = LegacyWriterQuiescence()
    fun required(context: Context) = listOf("", ".bak", ".new").any { File(context.noBackupFilesDir, REQUIRED + it).exists() }
    fun present(context: Context) = required(context) ||
        (File(context.noBackupFilesDir, PROFILE).exists() && context.getDatabasePath(DATABASE).exists())
    private fun token(context: Context, name: String, expected: String): Boolean {
        val file = File(context.noBackupFilesDir, name)
        return try { file.isFile && file.length() == expected.length.toLong() && file.readText() == expected }
        catch (_: IOException) { false }
    }
    @Suppress("DEPRECATION") // GET_SIGNATURES supports the unchanged minSdk 24 contract.
    fun authorized(context: Context): Boolean {
        if (!BuildConfig.PLAYBACK_ROOM_PROBE || !BuildConfig.DEBUG ||
            context.packageName != "com.ichichuang.cinema.debug") return false
        val matchingApk = try {
            val pm = context.packageManager
            val app = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            val test = pm.getPackageInfo(context.packageName + ".test", PackageManager.GET_SIGNATURES).signatures
            !app.isNullOrEmpty() && !test.isNullOrEmpty() && app.toSet() == test.toSet()
        } catch (_: PackageManager.NameNotFoundException) { false }
        return HandoffAuthorization(BuildConfig.PLAYBACK_ROOM_PROBE, BuildConfig.DEBUG, matchingApk,
            token(context, CONSENT, CONSENT_TOKEN) && token(context, "round06-playback-test.authorization", "cinema-round06-disposable-local-media"),
            token(context, PROFILE, PROFILE_TOKEN), Build.VERSION.SDK_INT == 30 && Build.HARDWARE == "ranchu" &&
                Build.SUPPORTED_ABIS.contentEquals(arrayOf("arm64-v8a")) &&
                Build.FINGERPRINT.startsWith("Android/sdk_phone_arm64/emulator_arm64:11/")).allows()
    }
    fun stores(context: Context): Map<LegacyStoreKind, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>> {
        check(authorized(context)) { "旧记录审计需要独立的测试迁移授权" }
        return mapOf(LegacyStoreKind.PROGRESS to context.progressStore, LegacyStoreKind.BROWSE to context.browsePrefs,
            LegacyStoreKind.FILTERS to context.filterPrefs, LegacyStoreKind.SETTINGS to context.dataStore)
    }
    suspend fun capture(context: Context): LegacySnapshot = LegacySnapshotReader(stores(context)).capture(System.currentTimeMillis())
    suspend fun <T> legacyWrite(context: Context, action: suspend () -> T): T {
        if (!required(context) && !File(context.noBackupFilesDir, PROFILE).exists()) return action()
        val caller = currentCoroutineContext()
        return writers.legacy({ if (!present(context)) HandoffWriter.LEGACY else durableWriter(context) }) {
            // DataStore's actor can outlive cancellation of updateData's caller. Keep the
            // protected writer slot until its acknowledgement confirms the actual IO drained.
            val result = withContext(NonCancellable) { action() }
            caller.ensureActive()
            result
        }
    }
    suspend fun durableWriter(context: Context): HandoffWriter {
        if (!authorized(context) || !context.getDatabasePath(DATABASE).isFile) return HandoffWriter.BLOCKED
        return try { LegacyHandoffController.open(context).use { it.writer() } }
        catch (error: CancellationException) { throw error }
        catch (_: IllegalStateException) { HandoffWriter.BLOCKED }
    }
    // Only the explicit disposable path needs IO in the existing synchronous VM constructor.
    fun playbackWriter(context: Context): HandoffWriter = runBlocking(Dispatchers.IO) {
        withTimeoutOrNull(5_000) { durableWriter(context) } ?: HandoffWriter.BLOCKED
    }
}
