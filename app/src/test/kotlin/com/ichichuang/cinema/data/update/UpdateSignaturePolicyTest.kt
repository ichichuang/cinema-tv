package com.ichichuang.cinema.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSignaturePolicyTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)
    private val c = "c".repeat(64)

    @Test fun missingInstalledSignersAreRejected() {
        assertFalse(UpdateSignaturePolicy.matches(null, setOf(a)))
    }
    @Test fun missingCandidateSignersAreRejected() {
        assertFalse(UpdateSignaturePolicy.matches(setOf(a), null))
    }
    @Test fun bothMissingAreRejected() {
        assertFalse(UpdateSignaturePolicy.matches(null, null))
    }
    @Test fun emptyInstalledSignersAreRejected() {
        assertFalse(UpdateSignaturePolicy.matches(emptySet(), setOf(a)))
    }
    @Test fun emptyCandidateSignersAreRejected() {
        assertFalse(UpdateSignaturePolicy.matches(setOf(a), emptySet()))
    }
    @Test fun bothEmptyAreRejected() {
        assertFalse(UpdateSignaturePolicy.matches(emptySet(), emptySet()))
    }
    @Test fun identicalSingleSignerIsAccepted() {
        assertTrue(UpdateSignaturePolicy.matches(setOf(a), setOf(a)))
    }
    @Test fun unrelatedSignerIsRejected() {
        assertFalse(UpdateSignaturePolicy.matches(setOf(a), setOf(b)))
    }
    @Test fun completeSignerSetsMatchRegardlessOfOrder() {
        assertTrue(UpdateSignaturePolicy.matches(linkedSetOf(a, b), linkedSetOf(b, a)))
    }
    @Test fun addedSignerIsRejected() {
        assertFalse(UpdateSignaturePolicy.matches(setOf(a), setOf(a, b)))
    }
    @Test fun removedSignerIsRejected() {
        assertFalse(UpdateSignaturePolicy.matches(setOf(a, b), setOf(a)))
    }
    @Test fun matchingFirstSignerDoesNotHideAnotherSigner() {
        assertFalse(UpdateSignaturePolicy.matches(setOf(a, b), setOf(a, c)))
    }
    @Test fun blankFingerprintIsRejected() {
        assertFalse(UpdateSignaturePolicy.matches(setOf(""), setOf("")))
    }
    @Test fun malformedFingerprintIsRejected() {
        assertFalse(UpdateSignaturePolicy.matches(setOf("bad"), setOf("bad")))
    }
    @Test fun nonHexFingerprintIsRejected() {
        val invalid = "z".repeat(64)
        assertFalse(UpdateSignaturePolicy.matches(setOf(invalid), setOf(invalid)))
    }
    @Test fun validAndBlankMixtureIsRejected() {
        assertFalse(UpdateSignaturePolicy.matches(setOf(a, ""), setOf(a, "")))
    }
}
