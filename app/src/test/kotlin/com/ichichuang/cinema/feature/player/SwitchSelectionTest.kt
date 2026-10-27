package com.ichichuang.cinema.feature.player

import com.ichichuang.cinema.data.userdata.PlaybackTarget
import com.ichichuang.cinema.domain.model.*
import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17 goal A (JVM, runtime layer): drives the production [SwitchSelection] orchestration — the
 * exact code [com.ichichuang.cinema.feature.personal.PersonalLibraryRuntime.selectFromPlayer]
 * delegates to — against a fake `probe.select`. The fake probe models the authoritative
 * in-queue stale check of [RoomPlaybackProbe.select]: it blocks on a per-selection
 * [CompletableDeferred], then checks the single [SelectionGeneration] authority before it
 * mutates the fake player identity and Room checkpoint.
 *
 * The single [SelectionGeneration] instance is the one shared authority (mirroring
 * [RoomPlaybackProbe.selectionGeneration]); the test never invents its own second isLatest
 * check. A mutation that removes the stale check inside the orchestration, or that stops
 * threading the generation into the fake probe, is caught here.
 */
class SwitchSelectionTest {

    /** A fake single-writer authority; a stale selection must not touch any of its fields. */
    private class FakeProbe(val generation: SelectionGeneration) {
        var playerMediaId: String? = null; private set
        var playerPositionMs: Long = 0L; private set
        var lastCheckpoint: String? = null; private set
        var selectedMedia: ProbeMedia? = null; private set
        val mutations = mutableListOf<String>()

        // Per-selection blocking step: models the Room-bound queue.await inside probe.select.
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var reservedInProbe: Long = -1L

        /** Models RoomPlaybackProbe.select(..., generation): authoritative in-queue stale check. */
        suspend fun select(media: ProbeMedia, reserved: Long): ProbeMedia? {
            reservedInProbe = reserved
            entered.complete(Unit)
            release.await()
            // The authoritative check happens BEFORE any player or Room mutation.
            if (!generation.isLatest(reserved)) return null
            val key = "${media.target.episodeId?.value}:${media.target.variantId?.value}"
            playerMediaId = "media-$key"
            playerPositionMs = media.target.variantId?.value?.hashCode()?.toLong() ?: 0L
            lastCheckpoint = "${media.target.workId.value}/${media.target.episodeId?.value}/${media.target.variantId?.value}"
            selectedMedia = media
            mutations += key
            return media
        }
    }

    private fun media(episode: String, variant: String): ProbeMedia {
        val detail = MovieDetail(Movie("w1", "测试", 0, 0.0, "", 0, "", origin = "source"),
            lines = listOf(PlayLine(variant, listOf(Episode(episode, "file:/synthetic/$episode")))))
        return ProbeMedia(detail, 0, 0, PlaybackTarget(WorkId("w1"), EpisodeId(episode), VariantId(variant)))
    }

    // ── the core race: an older selection resolves last but must not mutate the player ──

    @Test(timeout = 10_000)
    fun stale_selection_held_in_probe_never_mutates_player_or_checkpoint() = runBlocking<Unit> {
        val generation = SelectionGeneration()
        val probe = FakeProbe(generation)
        val switch = SwitchSelection()
        val gOlder = generation.reserve()

        // Older selection: reserve g1, enters probe.select and blocks on its own release.
        val olderJob = async(Dispatchers.Unconfined) {
            switch.perform(
                isStale = { !generation.isLatest(gOlder) },
                select = { probe.select(media("ep-a", "v1"), gOlder) },
                adopt = { },
                onStale = { },
            )
        }
        probe.entered.await()
        // A newer selection is reserved (gNewer > gOlder) while the older one is blocked inside
        // the probe. Release the older one in reverse order: it must be rejected inside the fake
        // probe (authoritative in-queue check) and must NOT mutate the player or checkpoint.
        val gNewer = generation.reserve()
        probe.release.complete(Unit)
        assertFalse("older superseded selection must not be adopted", olderJob.await())

        assertNull(probe.playerMediaId)
        assertNull(probe.lastCheckpoint)
        assertNull(probe.selectedMedia)
        assertTrue(probe.mutations.isEmpty())
    }

    // ── two rapid selections, completion inverted: only the newest may mutate ──

    @Test(timeout = 10_000)
    fun two_rapid_selections_inverted_only_newest_mutates() = runBlocking<Unit> {
        val generation = SelectionGeneration()
        val switch = SwitchSelection()

        // Each selection gets its own fake probe (its own blocking step), sharing one authority.
        val probeA = FakeProbe(generation)
        val probeB = FakeProbe(generation)
        val gA = generation.reserve()

        val a = async(Dispatchers.Unconfined) {
            switch.perform({ !generation.isLatest(gA) },
                { probeA.select(media("ep-a", "v1"), gA) }, { }, { })
        }
        probeA.entered.await()

        val gB = generation.reserve()
        val b = async(Dispatchers.Unconfined) {
            switch.perform({ !generation.isLatest(gB) },
                { probeB.select(media("ep-b", "v2"), gB) }, { }, { })
        }
        probeB.entered.await()

        // Release in reverse order: A (older) is rejected inside its probe; B (newest) mutates.
        probeA.release.complete(Unit)
        probeB.release.complete(Unit)
        assertFalse("older selection must be rejected", a.await())
        assertTrue("newest selection must be adopted", b.await())

        assertNull(probeA.playerMediaId)
        assertNull(probeA.lastCheckpoint)
        assertTrue(probeA.mutations.isEmpty())
        assertEquals("ep-b:v2", probeB.mutations.single())
        assertEquals("media-ep-b:v2", probeB.playerMediaId)
        assertEquals("w1/ep-b/v2", probeB.lastCheckpoint)
    }

    // ── pre-select fast path: an already-superseded selection never enters the probe ──

    @Test(timeout = 10_000)
    fun superseded_selection_is_rejected_before_entering_the_probe() = runBlocking<Unit> {
        val generation = SelectionGeneration()
        val switch = SwitchSelection()
        val probe = FakeProbe(generation)
        val gOlder = generation.reserve()
        val gNewer = generation.reserve()   // supersedes gOlder before gOlder runs

        val result = switch.perform(
            isStale = { !generation.isLatest(gOlder) },
            select = { probe.select(media("ep-a", "v1"), gOlder) },
            adopt = { },
            onStale = { },
        )
        assertFalse(result)
        assertTrue("stale selection must never enter the probe", probe.mutations.isEmpty())
        assertNull(probe.playerMediaId)
        assertNull(probe.lastCheckpoint)
    }

    // ── post-select guard: a selection superseded while its probe was in flight is not adopted ──

    @Test(timeout = 10_000)
    fun selection_superseded_while_probe_in_flight_is_not_adopted() = runBlocking<Unit> {
        val generation = SelectionGeneration()
        val switch = SwitchSelection()
        val probe = FakeProbe(generation)
        val adopted = mutableListOf<String>()
        val gOlder = generation.reserve()

        val older = async(Dispatchers.Unconfined) {
            switch.perform(
                isStale = { !generation.isLatest(gOlder) },
                select = { probe.select(media("ep-a", "v1"), gOlder) },
                adopt = { m -> adopted += m.target.episodeId!!.value },
                onStale = { },
            )
        }
        probe.entered.await()
        // Supersede while the older probe is in flight, then release: the in-flight probe's own
        // authoritative check rejects it (returns null), so adopt is never invoked.
        val gNewer = generation.reserve()
        probe.release.complete(Unit)
        assertFalse(older.await())
        assertTrue("superseded result must not be adopted", adopted.isEmpty())
        assertTrue(probe.mutations.isEmpty())
    }

    // ── variant resume isolation: an old session callback cannot overwrite the new progress ──

    @Test(timeout = 10_000)
    fun latest_selection_adopts_and_old_session_cannot_overwrite_it() = runBlocking<Unit> {
        val generation = SelectionGeneration()
        val switch = SwitchSelection()
        val adopted = mutableListOf<String>()
        val gNew = generation.reserve()
        val ok = switch.perform(
            isStale = { !generation.isLatest(gNew) },
            select = { media("ep-b", "v2") },
            adopt = { m -> adopted += "${m.target.episodeId?.value}:${m.target.variantId?.value}" },
            onStale = { },
        )
        assertTrue(ok)
        assertEquals(listOf("ep-b:v2"), adopted)
        // A later "old session" generation (lower than current) can never be re-adopted.
        val gOld = gNew - 1
        val stale = switch.perform(
            isStale = { !generation.isLatest(gOld) },
            select = { media("ep-a", "v1") },
            adopt = { m -> adopted += "STALE-${m.target.episodeId?.value}" },
            onStale = { },
        )
        assertFalse(stale)
        assertEquals("old session must not overwrite adopted identity", listOf("ep-b:v2"), adopted)
    }

    // ── generation token is monotonically increasing and the single authority ──

    @Test
    fun generation_reserves_monotonically_and_is_latest_only_for_newest() {
        val g = SelectionGeneration()
        val g1 = g.reserve()
        val g2 = g.reserve()
        val g3 = g.reserve()
        assertTrue(g1 < g2 && g2 < g3)
        assertFalse(g.isLatest(g1))
        assertFalse(g.isLatest(g2))
        assertTrue(g.isLatest(g3))
    }

    // ── a fresh selection after a rejected one proceeds normally (no starvation) ──

    @Test(timeout = 10_000)
    fun rejected_selection_does_not_starve_a_fresh_one() = runBlocking<Unit> {
        val generation = SelectionGeneration()
        val switch = SwitchSelection()
        val gOld = generation.reserve()
        val gNew = generation.reserve()
        // Old is rejected (superseded).
        assertFalse(switch.perform({ !generation.isLatest(gOld) }, { media("ep-a", "v1") }, { }, { }))
        // New proceeds.
        val adopted = mutableListOf<String>()
        assertTrue(switch.perform({ !generation.isLatest(gNew) }, { media("ep-b", "v2") },
            { m -> adopted += m.target.episodeId!!.value }, { }))
        assertEquals(listOf("ep-b"), adopted)
    }
}
