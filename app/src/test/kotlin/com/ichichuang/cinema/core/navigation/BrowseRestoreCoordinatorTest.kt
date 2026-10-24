package com.ichichuang.cinema.core.navigation

import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import org.junit.Assert.*
import org.junit.Test

internal fun browseSnapshot(context: String = "library") = BrowseSnapshot(context, context, null,
    CatalogQuery.create(context, ContentKind.MOVIE, LanguageScope.NON_CHINESE, setOf("JP"), setOf("horror"),
        1994, 1994, CatalogSort.YEAR_DESCENDING, setOf(SourceId("synthetic"))),
    4, WorkId("work-100"), WorkId("work-95"), -.2f, 100)
class BrowseRestoreCoordinatorTest {
    private val work = WorkId("work-100")
    private fun observation(s: BrowseSnapshot, ready: Boolean = false) = RestoreObservation(s.query, 4,
        mapOf("row" to listOf(work, WorkId("work-95"))), false,
        if (ready) setOf(work, WorkId("work-95")) else emptySet())
    @Test fun deep_list_waits_for_anchor_and_layout() {
        val c = BrowseRestoreCoordinator(); val s = browseSnapshot(); val t = c.begin(s)
        assertEquals(RestoreOutcome.WaitingForLayout, c.observe(t, observation(s)))
        assertEquals(RestoreOutcome.ReadyToFocus(work, s.anchorWorkId, -.2f), c.observe(t, observation(s, true).copy(layoutToken = c.advanceLayout(t, 4))))
    }
    @Test fun user_input_cancels_late_focus_restore() {
        val c = BrowseRestoreCoordinator(); val s = browseSnapshot(); val t = c.begin(s)
        c.onUserInput(); assertEquals(RestoreOutcome.Cancelled, c.observe(t, observation(s, true).copy(layoutToken = c.advanceLayout(t, 4))))
    }
    @Test fun new_route_invalidates_old_restore_token() {
        val c = BrowseRestoreCoordinator(); val s = browseSnapshot(); val t = c.begin(s)
        c.begin(browseSnapshot("history")); assertEquals(RestoreOutcome.Cancelled, c.observe(t, observation(s, true).copy(layoutToken = c.advanceLayout(t, 4))))
    }
    @Test fun target_missing_while_partial_is_not_removed() {
        val c = BrowseRestoreCoordinator(); val s = browseSnapshot(); val t = c.begin(s)
        assertEquals(RestoreOutcome.WaitingForItem, c.observe(t, observation(s).copy(rows = emptyMap())))
    }
    @Test fun exact_query_and_revision_gate_observations() {
        val c = BrowseRestoreCoordinator(); val s = browseSnapshot(); val t = c.begin(s)
        assertEquals(RestoreOutcome.WaitingForQuery, c.observe(t, observation(s, true).copy(query = browseSnapshot("favorites").query)))
        assertEquals(RestoreOutcome.WaitingForQuery, c.observe(t, observation(s, true).copy(resultRevision = 3)))
    }
    @Test fun removed_target_uses_nearest_same_row_then_control() {
        val c = BrowseRestoreCoordinator(); val s = browseSnapshot(); val t = c.begin(s); val neighbor = WorkId("neighbor")
        val o = RestoreObservation(s.query, 5, mapOf("saved" to listOf(neighbor), "other" to listOf(WorkId("unrelated"))), true,
            setOf(neighbor), true, "saved", listOf(work, neighbor))
        assertEquals(RestoreOutcome.Fallback(neighbor), c.observe(t, o.copy(layoutToken = c.advanceLayout(t, o.resultRevision!!))))
        val t2 = c.begin(s)
        assertEquals(RestoreOutcome.Fallback(null), c.observe(t2, o.copy(rows = mapOf("other" to listOf(neighbor)), layoutToken = c.advanceLayout(t2, 5))))
    }
    @Test fun navigation_origin_is_bounded_and_back_exits() {
        val s = RestoreNavigationState(browseSnapshot("history")).enter(BrowseChildRoute.DETAIL).enter(BrowseChildRoute.PLAYER)
        assertEquals("history", s.back()!!.back()!!.snapshot.contextKey)
        assertNull(s.back()!!.back()!!.back())
        assertThrows(IllegalArgumentException::class.java) { s.enter(BrowseChildRoute.DETAIL) }
    }
    @Test fun complete_result_drops_removed_anchor_but_keeps_focused_identity() {
        val c = BrowseRestoreCoordinator(); val s = browseSnapshot(); val t = c.begin(s)
        val o = observation(s, true).copy(rows = mapOf("new-geometry" to listOf(work)), complete = true)
        assertEquals(RestoreOutcome.ReadyToFocus(work, null, -.2f), c.observe(t, o.copy(layoutToken = c.advanceLayout(t, o.resultRevision!!))))
        assertEquals(RestoreOutcome.Cancelled, c.observe(t, o.copy(layoutToken = c.advanceLayout(t, o.resultRevision!!))))
    }

    @Test fun user_input_during_room_load_cancels_completion() {
        val c = BrowseRestoreCoordinator(); val ticket = c.reserveLoad()
        c.onUserInput()
        assertNull(c.completeLoad(ticket, browseSnapshot()))
    }
    @Test fun second_load_invalidates_first_load_ticket() {
        val c = BrowseRestoreCoordinator(); val first = c.reserveLoad(); val second = c.reserveLoad()
        assertNull(c.completeLoad(first, browseSnapshot()))
        assertNotNull(c.completeLoad(second, browseSnapshot("history")))
    }

    @Test fun older_complete_result_cannot_replace_newer_partial() {
        val c = BrowseRestoreCoordinator(); val s = browseSnapshot(); val t = c.begin(s)
        val partial = observation(s).copy(resultRevision = 6, rows = emptyMap())
        assertEquals(RestoreOutcome.WaitingForItem, c.observe(t, partial))
        assertEquals(RestoreOutcome.WaitingForQuery, c.observe(t, partial.copy(resultRevision = 5, complete = true, pageControlReady = true)))
    }
    @Test fun old_result_layout_callback_cannot_prove_new_result_placed() {
        val c = BrowseRestoreCoordinator(); val s = browseSnapshot(); val t = c.begin(s)
        val oldLayout = c.advanceLayout(t, 4)!!
        c.observe(t, observation(s).copy(resultRevision = 5, rows = emptyMap()))
        assertEquals(RestoreOutcome.WaitingForLayout, c.observe(t, observation(s, true).copy(resultRevision = 5, layoutToken = oldLayout)))
        val currentLayout = c.advanceLayout(t, 5)!!
        assertEquals(RestoreOutcome.ReadyToFocus(work, s.anchorWorkId, -.2f), c.observe(t, observation(s, true).copy(resultRevision = 5, layoutToken = currentLayout)))
    }
    @Test fun column_change_invalidates_prior_placement_for_same_result() {
        val c = BrowseRestoreCoordinator(); val s = browseSnapshot(); val t = c.begin(s)
        val oldColumns = c.advanceLayout(t, 4)!!
        val newColumns = c.advanceLayout(t, 4)!!
        assertEquals(RestoreOutcome.WaitingForLayout, c.observe(t, observation(s, true).copy(layoutToken = oldColumns)))
        assertEquals(RestoreOutcome.ReadyToFocus(work, s.anchorWorkId, -.2f), c.observe(t, observation(s, true).copy(layoutToken = newColumns)))
    }

}
