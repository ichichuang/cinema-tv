package com.ichichuang.cinema.data.local

import com.ichichuang.cinema.data.local.codec.StorageReadException
import com.ichichuang.cinema.data.local.migration.*
import org.junit.Assert.*
import org.junit.Test

class LegacySnapshotCodecTest {
    private fun entry(key: String, value: Any, store: LegacyStoreKind = LegacyStoreKind.PROGRESS) =
        LegacyEntry(store, key, LegacyValue.from(value))

    @Test fun same_content_different_iteration_order_has_same_digest() {
        val entries = listOf(entry("wp_7", "0,1,20,0"), entry("wp_8", "1,8,100,1000"))
        val a = LegacySnapshot.create(entries, 1)
        val b = LegacySnapshot.create(entries.reversed(), 1)
        assertTrue(a.contentDigest.matches(Regex("[a-f0-9]{64}")))
        assertEquals(a.contentDigest, b.contentDigest)
        assertEquals(a.entries, b.entries)
    }

    @Test fun same_content_new_capture_time_is_not_new_data() {
        val entries = listOf(entry("wp_7", "0,1,20,0"))
        val a = LegacySnapshot.create(entries, Long.MIN_VALUE)
        val b = LegacySnapshot.create(entries, Long.MAX_VALUE)
        assertEquals(a.contentDigest, b.contentDigest)
        assertNotEquals(a.capturedAt, b.capturedAt)
    }

    @Test fun changed_snapshot_has_different_digest() {
        val a = LegacySnapshot.create(listOf(entry("wp_7", "0,1,20,0")), 1)
        for (different in listOf(entry("wp_7", "0,1,21,0"), entry("wp_8", "0,1,20,0"),
            entry("wp_7", "0,1,20,0", LegacyStoreKind.FILTERS), entry("wp_7", 20))) {
            assertNotEquals(a.contentDigest, LegacySnapshot.create(listOf(different), 1).contentDigest)
        }
    }

    @Test fun typed_values_are_lossless_and_defensively_copied() {
        val bytes = byteArrayOf(0, -128, 127)
        val set = linkedSetOf(" b ", "a", "")
        val values: List<Any> = listOf("\n exact ", Int.MIN_VALUE, Long.MAX_VALUE, -0.0f,
            Float.fromBits(0x7fc00001), -0.0, Double.fromBits(0x7ff8000000000001), true, set, bytes)
        val snapshot = LegacySnapshot.create(values.mapIndexed { i, value -> entry("key$i", value) }, -1)
        val encoded = LegacySnapshotCodec.encode(snapshot)
        bytes[0] = 9
        set.add("mutated")
        assertEquals(encoded, LegacySnapshotCodec.encode(snapshot))
        val decoded = LegacySnapshotCodec.decode(encoded)
        assertEquals(snapshot.entries, decoded.entries)
        assertEquals(snapshot.capturedAt, decoded.capturedAt)
        assertEquals(snapshot.contentDigest, decoded.contentDigest)
    }

    @Test fun string_set_order_does_not_change_digest() {
        assertEquals(LegacySnapshot.create(listOf(entry("s", linkedSetOf("b", "a"))), 1).contentDigest,
            LegacySnapshot.create(listOf(entry("s", linkedSetOf("a", "b"))), 2).contentDigest)
    }

    @Test fun snapshot_owns_entries_and_rejects_duplicate_store_keys() {
        val entries = mutableListOf(entry("wp_7", "0,0,0,0"))
        val snapshot = LegacySnapshot.create(entries, 1)
        entries.clear()
        assertEquals(1, snapshot.entries.size)
        assertThrows(IllegalArgumentException::class.java) {
            LegacySnapshot.create(listOf(snapshot.entries.single(), snapshot.entries.single()), 1)
        }
    }

    @Test fun legacy_progress_coordinates_are_not_canonical_ids() {
        for (key in listOf("wp_7", "wp_https://synthetic.invalid/a::7", "wp_https://other.invalid/a::7")) {
            val analysis = LegacyEntryClassifier.classify(entry(key, "2,9001,1001,1000"))
            assertEquals(LegacyEntryStatus.UNRESOLVED, analysis.status)
            assertEquals(LegacyCoordinates(2, 9001, 1001, 1000), analysis.coordinates)
        }
    }

    @Test fun malformed_progress_is_preserved_without_default_duration() {
        for (raw in listOf("0,1,20,unknown", "0,1,20", "-1,1,20,30", "0,1,-2,3", "")) {
            val original = entry("wp_7", raw)
            val analysis = LegacyEntryClassifier.classify(original)
            assertEquals(LegacyEntryStatus.MALFORMED, analysis.status)
            assertNull(analysis.coordinates)
            assertEquals(raw, original.value.stringOrNull())
        }
    }

    @Test fun old_filter_browse_and_unknown_fields_are_kept() {
        for (key in listOf("f_category", "f_lang", "f_area", "f_year", "f_types")) {
            assertEquals(LegacyEntryStatus.RECOGNIZED,
                LegacyEntryClassifier.classify(entry(key, "", LegacyStoreKind.FILTERS)).status)
        }
        assertEquals(LegacyEntryStatus.UNRESOLVED,
            LegacyEntryClassifier.classify(entry("last_index", 9001, LegacyStoreKind.BROWSE)).status)
        assertEquals(LegacyEntryStatus.UNRESOLVED,
            LegacyEntryClassifier.classify(entry("last_movie_id", "", LegacyStoreKind.BROWSE)).status)
        assertEquals(LegacyEntryStatus.UNRESOLVED,
            LegacyEntryClassifier.classify(entry("unknown-key", true)).status)
        assertEquals(LegacyEntryStatus.MALFORMED,
            LegacyEntryClassifier.classify(entry("wp_7", 3)).status)
    }

    @Test fun unsupported_snapshot_format_does_not_decode() {
        val raw = LegacySnapshotCodec.encode(LegacySnapshot.create(emptyList(), 1))
        assertThrows(StorageReadException::class.java) {
            LegacySnapshotCodec.decode(raw.replace("\"version\":1", "\"version\":999"))
        }
    }

    @Test fun distinct_utf16_values_never_share_a_content_digest() {
        val values = listOf("\uD800", "\uD801", "?", "\\ud800", "\uDC00", "\uD83D\uDE00")
        val snapshots = values.map { LegacySnapshot.create(listOf(entry("same-key", it)), 1) }
        assertEquals(values.size, snapshots.map { it.contentDigest }.toSet().size)
        for (snapshot in snapshots) {
            val raw = snapshot.entries.single().value.encoded
            assertEquals(raw, raw.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
            val encoded = LegacySnapshotCodec.encode(snapshot)
            assertEquals(snapshot.entries, LegacySnapshotCodec.decode(encoded.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)).entries)
        }
    }
}
