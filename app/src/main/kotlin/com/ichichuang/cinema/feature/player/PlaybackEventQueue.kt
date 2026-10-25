package com.ichichuang.cinema.feature.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Single ordered child of the ViewModel owner; queued work has no independent lifetime. */
internal class PlaybackEventQueue(scope: CoroutineScope, onClosed: () -> Unit = {}) {
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val worker = scope.launch { for (command in commands) command() }
    init { worker.invokeOnCompletion { commands.cancel(); onClosed() } }
    fun post(block: suspend () -> Unit): Boolean = worker.isActive && commands.trySend(block).isSuccess
    suspend fun <T> await(block: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        val reply = CompletableDeferred<T>()
        val request = Job(worker)
        request.invokeOnCompletion { cause ->
            if (cause != null) reply.cancel(CancellationException("播放请求已结束").apply { initCause(cause) })
        }
        if (!post {
            if (reply.isActive) {
                try { reply.complete(withContext(request) { block() }) }
                catch (e: CancellationException) {
                    reply.cancel(e)
                    // A superseded request stops here; destruction still cancels the whole worker.
                    currentCoroutineContext().ensureActive()
                }
                catch (e: Exception) { reply.completeExceptionally(e) }
                finally { request.complete() }
            }
        }) reply.cancel(CancellationException("播放会话已结束"))
        return try { reply.await() } finally { request.cancel(); if (!reply.isCompleted) reply.cancel() }
    }
    fun close() { worker.cancel(); commands.cancel() }
    suspend fun closeAndJoin() { close(); worker.join() }
}
