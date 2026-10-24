package com.ichichuang.cinema.data.subscription

import com.ichichuang.cinema.domain.model.subscription.SubscriptionRefreshRequest
import com.ichichuang.cinema.domain.model.subscription.SubscriptionState
import com.ichichuang.cinema.domain.repository.SubscriptionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Candidate-only manager. Constructing this does not grant consent, trust a publisher, or activate sources. */
internal class SubscriptionRefreshCoordinator(
    private val store: CandidateSubscriptionStore,
    private val validator: DirectoryValidator,
    private val fetcher: PublicDirectoryFetcher,
    catalogUrls: Map<String, String>,
    private val clock: () -> Long,
    parallelism: Int = 2,
) : SubscriptionManager {
    private val urls = catalogUrls.toMap()
    private val permits = Semaphore(parallelism.also { require(it in 1..4) })
    init { require(urls.size <= 64); urls.values.forEach { PublicDirectoryPolicy.url(it) } }

    /** Read-only review stage. No consent/config write occurs, including for unsigned empty defaults. */
    suspend fun inspect(catalogId: String): DirectoryValidation = withTimeoutOrNull(15_000) {
        permits.withPermit {
            val url = urls[catalogId] ?: reject(SubscriptionFailure.UNTRUSTED)
            validator.validate(fetcher.fetch(url), catalogId, PublicDirectoryPolicy.origin(PublicDirectoryPolicy.url(url)), clock())
        }
    } ?: reject(SubscriptionFailure.TIMEOUT)

    override fun refresh(request: SubscriptionRefreshRequest): Flow<SubscriptionState> = flow {
        emit(SubscriptionState.Checking)
        val started = attempt { store.begin(request.catalogId) }
        if (started.failure != null) { emit(SubscriptionState.Failed(started.failure.name)); return@flow }
        val prepared = attempt { inspect(request.catalogId) }
        if (prepared.failure != null) { emit(SubscriptionState.Failed(prepared.failure.name)); return@flow }
        when (val value = prepared.value!!) {
            DirectoryValidation.Empty -> emit(SubscriptionState.CatalogEmpty)
            is DirectoryValidation.Trusted -> {
                emit(SubscriptionState.CandidatesFound(value.directory.sources.size))
                emit(SubscriptionState.Saving)
                val saved = attempt { store.commit(request, value.directory, started.value!!) }
                currentCoroutineContext().ensureActive()
                if (saved.failure != null) emit(SubscriptionState.Failed(saved.failure.name))
                else emit(SubscriptionState.Configured(saved.value!!))
            }
        }
    }
    private data class Attempt<T>(val value: T? = null, val failure: SubscriptionFailure? = null)
    private suspend fun <T> attempt(block: suspend () -> T): Attempt<T> = try { Attempt(value = block()) }
    catch (e: CancellationException) { throw e }
    catch (e: SubscriptionException) { Attempt(failure = e.failure) }
    catch (_: Exception) { Attempt(failure = SubscriptionFailure.STORAGE) }
}
