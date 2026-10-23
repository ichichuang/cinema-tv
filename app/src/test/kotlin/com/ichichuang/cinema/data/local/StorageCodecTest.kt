package com.ichichuang.cinema.data.local

import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import org.junit.Assert.*
import org.junit.Test

class StorageCodecTest {
    private val reference = EvidenceReference(SourceId("source-b"), SourceItemId("item,7"), " 原值\n", "rule-one")

    @Test fun query_with_old_years_round_trips() {
        val queries = listOf(
            CatalogQuery.create("1994", yearStart = 1994, yearEnd = 1994),
            CatalogQuery.create("decade", yearStart = 1980, yearEnd = 1989),
            CatalogQuery.create("upper", yearEnd = 1),
            CatalogQuery.create("lower", yearStart = 1895),
        )
        for (query in queries) roundTrip(CatalogQueryCodec, query)
    }

    @Test fun browse_snapshot_preserves_all_fields() {
        val query = CatalogQuery.create("query", ContentKind.SERIES, LanguageScope.NON_CHINESE,
            setOf("US", "JP"), setOf("drama", "comedy"), 1895, 1994,
            CatalogSort.YEAR_ASCENDING, setOf(SourceId("b"), SourceId("a")))
        roundTrip(BrowseSnapshotCodec, BrowseSnapshot("library", "detail", "library", query,
            Long.MAX_VALUE, WorkId(" work "), WorkId("anchor"), -0.375f, -123L))
        roundTrip(BrowseSnapshotCodec, BrowseSnapshot("home", "home", null, null,
            null, null, null, -0.0f, Long.MIN_VALUE))
    }

    @Test fun playback_snapshot_preserves_all_fields() {
        for (state in WatchState.entries) {
            for (duration in listOf(null, 0L, 100L)) {
                roundTrip(PlaybackSnapshotCodec, PlaybackSnapshot(WorkId(" w "), EpisodeId("e"),
                    VariantId("v"), "session", Long.MAX_VALUE, Long.MAX_VALUE, duration, state, Long.MIN_VALUE))
            }
        }
        roundTrip(PlaybackSnapshotCodec, PlaybackSnapshot(WorkId("w"), null, null,
            "s", 0, 0, null, WatchState.NOT_STARTED, -1))
    }

    @Test fun fact_evidence_states_round_trip() {
        val observations = listOf(FieldEvidence.Unknown(), FieldEvidence.Unknown(reference.copy(rawValue = "")))
        for (unknown in observations) roundTrip(CatalogItemCodec, CatalogItem(WorkId("w"), "", unknown, unknown, unknown))
        val knownEmpty = FieldEvidence.Confirmed(FactIds.of(emptySet()), reference)
        val conflict = FieldEvidence.Conflict.of(listOf(
            FieldEvidence.SourceReported(FactIds.of(setOf("zh", "en")), reference),
            FieldEvidence.SourceReported(FactIds.of(emptySet()), reference.copy(sourceId = SourceId("second"))),
            FieldEvidence.SourceReported(FactIds.of(setOf("zh", "en")), reference.copy(ruleVersion = "other")),
        ))
        roundTrip(CatalogItemCodec, CatalogItem(WorkId("w"), "synthetic", FieldEvidence.Conflict.of(listOf(
            FieldEvidence.SourceReported(1895, reference), FieldEvidence.SourceReported(1994, reference))),
            knownEmpty, conflict))
        roundTrip(CatalogItemCodec, CatalogItem(WorkId("w"), "synthetic", FieldEvidence.Confirmed(1895, reference),
            FieldEvidence.SourceReported(FactIds.of(setOf("zh")), reference), knownEmpty))
        roundTrip(MediaVariantCodec, MediaVariant(VariantId("v"), WorkId("w"), SourceId("s"), SourceItemId("i"), conflict))
        roundTrip(MediaVariantCodec, MediaVariant(VariantId("v"), WorkId("w"), SourceId("s"), SourceItemId("i")))
    }

    @Test fun future_payload_version_is_not_silently_decoded() {
        val encoded = CatalogQueryCodec.encode(CatalogQuery.create("q"))
        val error = assertThrows(StorageReadException::class.java) {
            CatalogQueryCodec.decode(encoded.replace("\"version\":1", "\"version\":2"))
        }
        assertEquals(StorageReadFailure.UNSUPPORTED_VERSION, error.reason)
    }

    @Test fun malformed_payload_has_sanitized_error() {
        for (raw in listOf("private-payload", "{}", "null", "[]",
            "{\"kind\":\"catalog_query\",\"version\":1,\"value\":{}}")) {
            val error = assertThrows(StorageReadException::class.java) { CatalogQueryCodec.decode(raw) }
            assertEquals(StorageReadFailure.MALFORMED, error.reason)
            assertFalse(error.toString().contains("private-payload"))
            assertNull(error.cause)
        }
    }

    @Test fun wrong_payload_kind_is_rejected() {
        val raw = CatalogQueryCodec.encode(CatalogQuery.create("q"))
        assertEquals(StorageReadFailure.WRONG_KIND, assertThrows(StorageReadException::class.java) {
            BrowseSnapshotCodec.decode(raw)
        }.reason)
    }

    @Test fun unordered_sets_encode_deterministically() {
        val a = CatalogQuery.create("q", countryIds = linkedSetOf("JP", "US"),
            enabledSourceIds = linkedSetOf(SourceId("b"), SourceId("a")))
        val b = CatalogQuery.create("q", countryIds = linkedSetOf("US", "JP"),
            enabledSourceIds = linkedSetOf(SourceId("a"), SourceId("b")))
        assertEquals(CatalogQueryCodec.encode(a), CatalogQueryCodec.encode(b))
    }

    @Test fun enum_tags_and_null_boundaries_round_trip() {
        for (kind in ContentKind.entries + listOf(null)) for (language in LanguageScope.entries) {
            for (sort in CatalogSort.entries) roundTrip(CatalogQueryCodec,
                CatalogQuery.create("q", kind, language, sort = sort))
        }
    }

    @Test fun finite_float_offsets_remain_exact() {
        for (offset in listOf(-0.0f, 0f, Float.MAX_VALUE, Float.MIN_VALUE, -Float.MAX_VALUE)) {
            val snapshot = BrowseSnapshot("c", "r", null, null, null, null, null, offset, -1)
            val decoded = BrowseSnapshotCodec.decode(BrowseSnapshotCodec.encode(snapshot))
            assertEquals(offset.toRawBits(), decoded.relativeOffset.toRawBits())
        }
    }

    @Test fun payloads_preserve_every_utf16_code_unit_through_utf8_storage() {
        val item = CatalogItem(WorkId("\uD800"), "\uD801?\\ud800\u0000\uDC00",
            FieldEvidence.SourceReported(1895, EvidenceReference(SourceId("\uD802"), null, "\uD803", "\uD804")))
        val encoded = CatalogItemCodec.encode(item)
        assertEquals(item, CatalogItemCodec.decode(encoded.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)))
    }

    private fun <T> roundTrip(codec: StorageCodec<T>, value: T) {
        val raw = codec.encode(value)
        assertTrue("A format envelope must be stored", raw.contains("\"version\":1"))
        assertEquals(value, codec.decode(raw))
    }
}
