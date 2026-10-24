package com.ichichuang.cinema.core.navigation

import com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot

/** Synchronous input arbitration. The UI must consume the returned action in the same event turn. */
internal class BrowseRestoreCoordinator {
    private var generation = 0L
    private var snapshot: BrowseSnapshot? = null
    private var delivered = false
    private var latestResultRevision = -1L
    private var layoutGeneration = 0L
    private var layout: RestoreLayoutToken? = null
    @Synchronized fun reserveLoad(): RestoreLoadToken = RestoreLoadToken(++generation).also { snapshot = null; delivered = false }
    @Synchronized fun completeLoad(ticket: RestoreLoadToken, snapshot: BrowseSnapshot): RestoreToken? =
        if (ticket.generation == generation) begin(snapshot) else null
    @Synchronized fun begin(snapshot: BrowseSnapshot): RestoreToken {
        this.snapshot = snapshot; delivered = false; latestResultRevision = snapshot.resultRevision ?: -1L; layout = null
        return RestoreToken(++generation, snapshot.contextKey, snapshot.query)
    }
    @Synchronized fun onUserInput() { generation++; snapshot = null }
    /** Call before each new result/layout pass. Capture the returned token in real placement callbacks. */
    @Synchronized fun advanceLayout(token: RestoreToken, resultRevision: Long): RestoreLayoutToken? {
        val s = snapshot ?: return null
        if (token.routeGeneration != generation || token.contextKey != s.contextKey || token.query != s.query || delivered ||
            resultRevision < latestResultRevision || resultRevision < 0) return null
        latestResultRevision = resultRevision
        return RestoreLayoutToken(generation, resultRevision, ++layoutGeneration).also { layout = it }
    }
    private fun currentSnapshot(token: RestoreToken): BrowseSnapshot? = snapshot?.takeIf {
        token.routeGeneration == generation && token.contextKey == it.contextKey && token.query == it.query && !delivered
    }
    private fun matchesQuery(saved: BrowseSnapshot, observation: RestoreObservation) =
        observation.query == saved.query && observation.resultRevision != null && observation.resultRevision >= latestResultRevision

    /** Resolve the same destination before layout, without consuming the cancellable focus request. */
    @Synchronized fun positioning(token: RestoreToken, observation: RestoreObservation): RestorePositioning? {
        val saved = currentSnapshot(token) ?: return null
        if (!matchesQuery(saved, observation) || layout == null || observation.layoutToken != layout ||
            layout?.resultRevision != observation.resultRevision) return null
        // A new context without any saved position must retain its existing viewport behavior.
        if (saved.focusedWorkId == null && saved.anchorWorkId == null) return RestorePositioning(null, 0f)
        return when (val destination = destination(saved, observation)) {
            is RestoreOutcome.ReadyToFocus -> RestorePositioning(destination.anchorWorkId ?: destination.workId,
                if (destination.anchorWorkId != null) destination.relativeOffset else 0f)
            is RestoreOutcome.Fallback -> RestorePositioning(destination.workId, 0f)
            else -> null
        }
    }
    @Synchronized fun observe(token: RestoreToken, observation: RestoreObservation): RestoreOutcome {
        val saved = currentSnapshot(token) ?: return RestoreOutcome.Cancelled
        if (!matchesQuery(saved, observation)) return RestoreOutcome.WaitingForQuery
        val revision = requireNotNull(observation.resultRevision)
        if (revision > latestResultRevision) {
            latestResultRevision = revision
            layout = null
        }
        val layoutMatches = layout != null && observation.layoutToken == layout && layout?.resultRevision == revision
        val destination = destination(saved, observation)
        when (destination) {
            is RestoreOutcome.ReadyToFocus -> if (!layoutMatches || destination.workId !in observation.layoutReady ||
                (destination.anchorWorkId != null && destination.anchorWorkId !in observation.layoutReady)) return RestoreOutcome.WaitingForLayout
            is RestoreOutcome.Fallback -> if (!layoutMatches ||
                (destination.workId?.let { it !in observation.layoutReady } ?: !observation.pageControlReady)) return RestoreOutcome.WaitingForLayout
            else -> return destination
        }
        delivered = true
        return destination
    }
    /** One removal/fallback policy shared by positioning and final layout-gated focus delivery. */
    private fun destination(saved: BrowseSnapshot, observation: RestoreObservation): RestoreOutcome {
        val ids = observation.rows.values.flatten().toSet()
        val target = saved.focusedWorkId ?: saved.anchorWorkId
        if (target != null && target in ids) {
            if (saved.anchorWorkId != null && saved.anchorWorkId !in ids && !observation.complete) return RestoreOutcome.WaitingForItem
            return RestoreOutcome.ReadyToFocus(target, saved.anchorWorkId?.takeIf { it in ids }, saved.relativeOffset)
        }
        if (target != null && !observation.complete) return RestoreOutcome.WaitingForItem
        val row = observation.rows[observation.savedRowKey].orEmpty()
        val oldIndex = observation.savedRowOrder.indexOf(target)
        val neighbor = if (oldIndex >= 0) observation.savedRowOrder.withIndex()
            .filter { it.value in row }.minByOrNull { kotlin.math.abs(it.index - oldIndex) }?.value else null
        return RestoreOutcome.Fallback(neighbor ?: row.firstOrNull())
    }
}
