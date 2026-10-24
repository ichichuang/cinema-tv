package com.ichichuang.cinema.data.userdata

/** Independent migration consent; ordinary probe consent alone is insufficient. */
internal data class HandoffAuthorization(
    val probe: Boolean = false, val debug: Boolean = false, val matchingTestApk: Boolean = false,
    val privateConsent: Boolean = false, val ownedProfile: Boolean = false,
    val verifiedEmulator: Boolean = false,
) {
    fun allows(): Boolean = probe && debug && matchingTestApk && privateConsent && ownedProfile && verifiedEmulator
}
