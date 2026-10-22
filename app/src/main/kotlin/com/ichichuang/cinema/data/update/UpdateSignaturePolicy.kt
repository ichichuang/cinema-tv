package com.ichichuang.cinema.data.update

/**
 * Fail-closed comparison of complete current-signer sets.
 * This is not proof-of-rotation validation and does not restore trust in a leaked key.
 * Migration to a new signer requires a separately reviewed policy and device tests.
 */
internal object UpdateSignaturePolicy {
    private val sha256 = Regex("[0-9a-f]{64}")

    fun matches(installed: Set<String>?, candidate: Set<String>?): Boolean {
        if (installed.isNullOrEmpty() || candidate.isNullOrEmpty()) return false
        if (installed.any { !sha256.matches(it) } || candidate.any { !sha256.matches(it) }) return false
        return installed == candidate
    }
}
