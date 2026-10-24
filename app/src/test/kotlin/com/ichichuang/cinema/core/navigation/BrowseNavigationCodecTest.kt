package com.ichichuang.cinema.core.navigation

import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.domain.model.catalog.*
import org.junit.Assert.*
import org.junit.Test

class BrowseNavigationCodecTest {
    @Test fun filter_query_roundtrip_retains_all_dimensions() {
        for ((index, context) in listOf("library", "favorites", "history").withIndex()) {
            val snapshot = browseSnapshot(context).copy(query = CatalogQuery.create("$context-query", ContentKind.MOVIE,
                LanguageScope.NON_CHINESE, setOf("JP"), setOf("horror"), 1994, 1994,
                listOf(CatalogSort.DEFAULT, CatalogSort.YEAR_ASCENDING, CatalogSort.YEAR_DESCENDING)[index], setOf(SourceId("synthetic-$index"))),
                anchorWorkId = WorkId("anchor-$index"))
            val state = RestoreNavigationState(snapshot).enter(BrowseChildRoute.DETAIL).enter(BrowseChildRoute.PLAYER)
            val actual = BrowseNavigationCodec.decode(BrowseNavigationCodec.encode(state))
            assertEquals(snapshot, actual.snapshot)
            assertEquals(listOf(BrowseChildRoute.DETAIL, BrowseChildRoute.PLAYER), actual.returnChain)
            assertEquals("JP", actual.snapshot.query!!.countryIds.single())
            assertEquals(1994, actual.snapshot.query!!.yearStart)
        }
    }
    @Test fun navigation_commit_preserves_old_queries_and_latest_per_top_level() {
        val rows = linkedMapOf<String, String>()
        val firstQuery = browseSnapshot().query!!
        val secondQuery = CatalogQuery.create("library-new", sort = CatalogSort.YEAR_ASCENDING)
        val first = RestoreNavigationState(browseSnapshot().copy(contextKey = BrowseTopLevel.LIBRARY.contextKey(firstQuery)))
        val second = RestoreNavigationState(browseSnapshot().copy(contextKey = BrowseTopLevel.LIBRARY.contextKey(secondQuery), query = secondQuery))
        val favorites = RestoreNavigationState(browseSnapshot("favorites"))
        for (state in listOf(first, second, favorites)) browseNavigationRecords(state).forEach { rows[it.contextKey] = it.payload }
        val key = com.ichichuang.cinema.data.userdata.RoomBrowseStateStore.ACTIVE_NAVIGATION_KEY
        assertTrue(rows.containsKey("$key.library"))
        assertEquals(second, BrowseNavigationCodec.decode(rows.getValue("$key.library")))
        assertEquals(favorites, BrowseNavigationCodec.decode(rows.getValue("$key.favorites")))
        assertEquals(first.snapshot, BrowseSnapshotCodec.decode(rows.getValue(first.snapshot.contextKey)))
        assertEquals(second.snapshot, BrowseSnapshotCodec.decode(rows.getValue(second.snapshot.contextKey)))
        assertEquals(favorites, BrowseNavigationCodec.decode(rows.getValue(key)))
    }

    @Test fun misplaced_top_level_pointer_fails_malformed_without_rewriting_payload() {
        val payload = BrowseNavigationCodec.encode(RestoreNavigationState(browseSnapshot("history")))
        val error = assertThrows(StorageReadException::class.java) { decodeBrowseNavigationPointer(payload, BrowseTopLevel.LIBRARY) }
        assertEquals(StorageReadFailure.MALFORMED, error.reason)
        assertEquals("history", BrowseNavigationCodec.decode(payload).snapshot.route)
    }
    @Test fun global_pointer_rejects_reserved_context_namespace() {
        val state = RestoreNavigationState(browseSnapshot().copy(contextKey = "\u0000cinema.browse.active-navigation.library"))
        val payload = BrowseNavigationCodec.encode(state)
        assertThrows(StorageReadException::class.java) { decodeBrowseNavigationPointer(payload) }
    }

}
