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
    @Synchronized fun observe(token: RestoreToken, observation: RestoreObservation): RestoreOutcome {
        val s = snapshot ?: return RestoreOutcome.Cancelled
        if (token.routeGeneration != generation || token.contextKey != s.contextKey || token.query != s.query || delivered)
            return RestoreOutcome.Cancelled
        if (observation.query != s.query || observation.resultRevision == null ||
            observation.resultRevision < latestResultRevision) return RestoreOutcome.WaitingForQuery
        if (observation.resultRevision > latestResultRevision) {
            latestResultRevision = observation.resultRevision
            layout = null
        }
        val layoutMatches = layout != null && observation.layoutToken == layout && layout?.resultRevision == observation.resultRevision
        val ids = observation.rows.values.flatten().toSet()
        val target = s.focusedWorkId ?: s.anchorWorkId
        if (target != null && target in ids) {
            if (s.anchorWorkId != null && s.anchorWorkId !in ids && !observation.complete) return RestoreOutcome.WaitingForItem
            val anchor = s.anchorWorkId?.takeIf { it in ids }
            if (!layoutMatches || target !in observation.layoutReady || (anchor != null && anchor !in observation.layoutReady))
                return RestoreOutcome.WaitingForLayout
            delivered = true
            return RestoreOutcome.ReadyToFocus(target, anchor, s.relativeOffset)
        }
        if (target != null && !observation.complete) return RestoreOutcome.WaitingForItem
        val row = observation.rows[observation.savedRowKey].orEmpty()
        val oldIndex = observation.savedRowOrder.indexOf(target)
        val neighbor = if (oldIndex >= 0) observation.savedRowOrder.withIndex()
            .filter { it.value in row }.minByOrNull { kotlin.math.abs(it.index - oldIndex) }?.value else null
        val fallback = neighbor ?: row.firstOrNull()
        if (!layoutMatches || (fallback != null && fallback !in observation.layoutReady)) return RestoreOutcome.WaitingForLayout
        if (fallback == null && !observation.pageControlReady) return RestoreOutcome.WaitingForLayout
        delivered = true
        return RestoreOutcome.Fallback(fallback)
    }
}
