package com.ichichuang.cinema.data.userdata

import org.junit.Assert.*
import org.junit.Test

class LegacyHandoffPolicyTest {
    private val authorized = HandoffAuthorization(true, true, true, true, true, true)
    @Test fun explicit_disposable_migration_consent_is_required() { assertTrue(authorized.allows()) }
    @Test fun defaults_are_off() { assertFalse(HandoffAuthorization().allows()) }
    @Test fun ordinary_debug_is_off() { assertFalse(authorized.copy(probe = false).allows()) }
    @Test fun release_is_off() { assertFalse(authorized.copy(debug = false).allows()) }
    @Test fun wrong_test_apk_is_refused() { assertFalse(authorized.copy(matchingTestApk = false).allows()) }
    @Test fun missing_migration_token_is_refused() { assertFalse(authorized.copy(privateConsent = false).allows()) }
    @Test fun unknown_or_dirty_profile_is_refused() { assertFalse(authorized.copy(ownedProfile = false).allows()) }
    @Test fun another_emulator_is_refused() { assertFalse(authorized.copy(verifiedEmulator = false).allows()) }
}
