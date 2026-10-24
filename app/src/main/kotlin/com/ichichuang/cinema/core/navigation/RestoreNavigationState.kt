package com.ichichuang.cinema.core.navigation

import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*

internal data class RestoreLoadToken internal constructor(val generation: Long)
internal data class RestoreToken(val routeGeneration: Long, val contextKey: String, val query: CatalogQuery?)
internal data class RestoreLayoutToken internal constructor(val routeGeneration: Long, val resultRevision: Long, val layoutGeneration: Long)
internal data class RestoreObservation(
    val query: CatalogQuery?,
    val resultRevision: Long?,
    val rows: Map<String, List<WorkId>>,
    val complete: Boolean,
    val layoutReady: Set<WorkId> = emptySet(),
    val pageControlReady: Boolean = false,
    val savedRowKey: String? = null,
    val savedRowOrder: List<WorkId> = emptyList(),
    val layoutToken: RestoreLayoutToken? = null,
)
internal sealed interface RestoreOutcome {
    data object WaitingForQuery : RestoreOutcome
    data object WaitingForItem : RestoreOutcome
    data object WaitingForLayout : RestoreOutcome
    data class ReadyToFocus(val workId: WorkId, val anchorWorkId: WorkId?, val relativeOffset: Float) : RestoreOutcome
    data class Fallback(val workId: WorkId?) : RestoreOutcome
    data object Cancelled : RestoreOutcome
    data class Failed(val reason: String) : RestoreOutcome
}

/** Only stable destinations can be persisted; modal/command routes cannot be represented. */
internal enum class BrowseTopLevel(val route: String) {
    LIBRARY("library"), FAVORITES("favorites"), HISTORY("history");
    /** R10 query IDs are immutable identities: changed filters/sort/sources require a new queryId. */
    fun contextKey(query: CatalogQuery) = "$route:${query.queryId}"
}
internal enum class BrowseChildRoute { DETAIL, PLAYER }
internal class RestoreNavigationState(val snapshot: BrowseSnapshot, returnChain: List<BrowseChildRoute> = emptyList()) {
    val returnChain: List<BrowseChildRoute> = java.util.Collections.unmodifiableList(ArrayList(returnChain))
    init {
        require(BrowseTopLevel.entries.any { it.route == snapshot.route })
        require(this.returnChain.size <= 2 && this.returnChain.distinct().size == this.returnChain.size)
    }
    fun copy(snapshot: BrowseSnapshot = this.snapshot, returnChain: List<BrowseChildRoute> = this.returnChain) =
        RestoreNavigationState(snapshot, returnChain)
    fun enter(route: BrowseChildRoute) = copy(returnChain = returnChain + route)
    fun back(): RestoreNavigationState? = if (returnChain.isEmpty()) null else copy(returnChain = returnChain.dropLast(1))
    override fun equals(other: Any?) = other is RestoreNavigationState && snapshot == other.snapshot && returnChain == other.returnChain
    override fun hashCode() = 31 * snapshot.hashCode() + returnChain.hashCode()
    override fun toString() = "RestoreNavigationState(snapshot=$snapshot, returnChain=$returnChain)"
}
internal interface BrowseNavigationStore {
    suspend fun saveActive(state: RestoreNavigationState): CommitResult
    suspend fun loadLatest(topLevel: BrowseTopLevel): RestoreNavigationState?
    suspend fun loadActive(): RestoreNavigationState?
}

/** Modal return stays in its captured control row; only an empty row uses the page control. */
internal fun modalReturnControl(invoker: String?, originalRow: List<String>, availableRow: List<String>, pageDefault: String): String {
    if (invoker in availableRow) return requireNotNull(invoker)
    val index = originalRow.indexOf(invoker)
    if (index < 0) return pageDefault
    return availableRow.filter { it in originalRow }.minWithOrNull(
        compareBy<String> { kotlin.math.abs(originalRow.indexOf(it) - index) }
            .thenBy { if (originalRow.indexOf(it) >= index) 0 else 1 }
    ) ?: pageDefault
}
