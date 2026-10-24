package com.ichichuang.cinema.core.navigation

import org.junit.Assert.*
import org.junit.Test

class DebugBrowseProbeGateTest {
    @Test fun ordinary_mode_never_opens_candidate_even_with_authorization() {
        assertFalse(DebugBrowseProbeGate.requireProfile(false, true, true, false, false))
    }
    @Test fun unknown_profile_or_legacy_data_refuses_candidate() {
        for (values in listOf(listOf(false, true, false, false), listOf(true, false, false, false),
            listOf(true, true, true, false), listOf(true, true, false, true))) {
            assertThrows(IllegalStateException::class.java) {
                DebugBrowseProbeGate.requireProfile(true, values[0], values[1], values[2], values[3])
            }
        }
    }
    @Test fun authorized_clean_debug_profile_allows_candidate() {
        assertTrue(DebugBrowseProbeGate.requireProfile(true, true, true, false, false))
    }
}
