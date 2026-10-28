package com.ichichuang.cinema.core.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Storage boundary for the in-app "减弱动效" setting.
 *
 * Production backs this with [com.ichichuang.cinema.data.settings.CinemaSettings]
 * (DataStore). Tests inject an in-memory fake so the "toggle writes the store"
 * and "a store change re-emits the effective signal" contracts run on the JVM
 * without an Android [android.content.Context]. The merge logic is never faked:
 * it stays in [ReduceMotionController], which the host constructs from the real
 * store flow and the injected [AnimatorScaleSource].
 */
interface ReduceMotionStore {
    /** The stored preference, off until the user flips the switch. */
    val reduceMotion: Flow<Boolean>

    /** Persist the new value; the flow re-emits and the effective signal follows. */
    suspend fun setReduceMotion(enabled: Boolean)
}

/**
 * Holds the reduced-motion state for one subtree and wires the two sources
 * (user setting + platform animator scale) into a single effective signal.
 *
 * Extracted from the probe runtime so the whole chain is JVM-testable:
 *  - [reduceMotionSetting] mirrors the store flow (drives the switch label);
 *  - [reduceMotion] is the merged signal provided as [LocalReducedMotion] at the
 *    root (drives every component in the subtree);
 *  - [toggle] flips the stored preference; the effective signal follows the store
 *    flow rather than being set directly.
 *
 * [scope] is the owner's scope; collectors are cancelled with it.
 */
class ReducedMotionHost(
    private val scope: CoroutineScope,
    private val store: ReduceMotionStore,
    scaleSource: AnimatorScaleSource,
) {
    /** The stored preference, read live from the store. Drives the switch label only. */
    var reduceMotionSetting by mutableStateOf(false)
        private set

    /** The effective signal: setting OR platform scale == 0. Provided at the root. */
    var reduceMotion by mutableStateOf(false)
        private set

    /**
     * Human-readable error from the last failed write, or null. Set instead of
     * swallowing the exception so the UI can show a message and offer a retry.
     */
    var writeError by mutableStateOf<String?>(null)
        private set

    private val controller = ReduceMotionController(scaleSource, store.reduceMotion)

    init {
        scope.launch { store.reduceMotion.collect { reduceMotionSetting = it } }
        scope.launch { controller.reduced.collect { reduceMotion = it } }
    }

    /**
     * Toggle the stored preference; the merged signal follows the store flow.
     *
     * The write is not wrapped in `runCatching`: a failed persist surfaces in
     * [writeError] (with a retryable message) rather than vanishing, and the
     * setting is left untouched so the switch label never lies about state.
     */
    fun toggle() {
        val next = !reduceMotionSetting
        scope.launch {
            try {
                store.setReduceMotion(next)
                writeError = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                writeError = "未能保存“减弱动效”设置，请重试"
            }
        }
    }
}
