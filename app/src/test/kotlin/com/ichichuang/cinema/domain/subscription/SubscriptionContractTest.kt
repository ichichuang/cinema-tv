package com.ichichuang.cinema.domain.subscription

import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.subscription.*
import org.junit.Assert.*
import org.junit.Test

class SubscriptionContractTest {
    @Test fun configuration_saved_does_not_imply_playable() {
        // Configured records a configuration revision only. No media address or playback proof is supplied.
        val configured: SubscriptionState = SubscriptionState.Configured(configurationRevision = 3)
        assertEquals(3L, (configured as SubscriptionState.Configured).configurationRevision)
        assertNotEquals(SubscriptionState.CatalogEmpty, configured)
        assertNotEquals(SubscriptionState.Failed("catalog_unavailable"), configured)
        assertNotEquals(SubscriptionState.CandidatesFound(2), configured)
    }

    @Test fun catalog_identity_is_not_user_consent() {
        val request = SubscriptionRefreshRequest.create(RefreshReason.USER_REQUEST, "catalog-a", emptySet(), null)
        assertEquals("catalog-a", request.catalogId)
        assertNull(request.consentRevision)
    }

    @Test fun subscription_selection_is_an_immutable_snapshot() {
        val source = SourceId("source-a")
        val enabled = mutableSetOf(source)
        val request = SubscriptionRefreshRequest.create(RefreshReason.AUTOMATIC_REFRESH, "catalog-a", enabled, 2)
        enabled.clear()
        assertEquals(setOf(source), request.enabledSourceIds)
        assertEquals(2L, request.consentRevision)
        assertThrows(UnsupportedOperationException::class.java) { (request.enabledSourceIds as MutableSet).clear() }
    }

    @Test fun invalid_subscription_metadata_is_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionRefreshRequest.create(RefreshReason.USER_REQUEST, "", emptySet(), null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionRefreshRequest.create(RefreshReason.USER_REQUEST, "catalog-a", emptySet(), -1)
        }
        assertThrows(IllegalArgumentException::class.java) { SubscriptionState.CandidatesFound(0) }
        assertThrows(IllegalArgumentException::class.java) { SubscriptionState.Configured(-1) }
    }
}
