package com.ichichuang.cinema.data.update.auth

import com.ichichuang.cinema.data.update.UpdatePackageIdentity
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuthenticatedUpdateVerifierTest {
    @get:Rule val temp = TemporaryFolder()
    private val signer = "b".repeat(64)
    private val identity = UpdatePackageIdentity("sample.app", 19, setOf(signer))
    private var now = 1_800_000_000L
    private val store by lazy { FileUpdateReplayStore(temp.newFolder("state")).also { it.initialize() } }
    private fun verifier() = AuthenticatedUpdateVerifier(
        mapOf("test-publisher" to keys.public), store,
        UpdatePlatform("sample.app", 18, 30, "armeabi-v7a"), { now },
    )
    private fun payload(vararg changes: Pair<String, String>): ByteArray {
        val fields = linkedMapOf(
            "key" to "test-publisher", "package" to "sample.app", "sequence" to "1",
            "version" to "19", "size" to "4", "sha256" to "3a6eb0790f39ac87c94f3856b2dd2c5d110e6811602261a9a923d3bb23adc8b7",
            "signers" to signer, "min-sdk" to "24", "max-sdk" to "35", "abis" to "armeabi-v7a",
            "not-before" to "1799999900", "expires" to "1800001000",
            "url" to "https://updates.example.invalid/fixture.apk",
        )
        for ((key, value) in changes) fields[key] = value
        return ("cinema-update-auth/1\n" + fields.entries.joinToString("") { "${it.key}=${it.value}\n" })
            .toByteArray(Charsets.US_ASCII)
    }
    private fun envelope(bytes: ByteArray = payload(), wrongKey: Boolean = false): ByteArray {
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign((if (wrongKey) otherKeys else keys).private)
            update(bytes)
            sign()
        }
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeBytes("AUTHUPD1")
                out.writeInt(bytes.size)
                out.write(bytes)
                out.writeShort(signature.size)
                out.write(signature)
            }
        }.toByteArray()
    }
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Untrusted input was accepted") } catch (_: UpdateAuthenticationException) { }
    }
    private fun download(v: AuthenticatedUpdateVerifier, permit: AuthenticatedDownload): File =
        v.download(permit, temp.newFile(), { it.writeText("data") }, { identity })

    @Test fun authenticMetadataAuthorizesExactArtifactAndPersistsBeforeDownload() {
        val v = verifier()
        val permit = v.authorize(envelope())
        assertEquals("https://updates.example.invalid/fixture.apk", permit.url)
        assertEquals(1L, store.read().sequence)
        assertEquals("data", download(v, permit).readText())
        assertFalse(store.read().completed)
    }
    @Test fun forgedSignatureCannotAuthorizeOrChangeState() {
        val wire = envelope().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        rejected { verifier().authorize(wire) }
        assertEquals(0L, store.read().sequence)
    }
    @Test fun everyChangedSignedFieldInvalidatesOriginalSignature() {
        val original = envelope()
        for ((key, value) in listOf("package" to "other.app", "sequence" to "2", "version" to "20",
            "size" to "5", "sha256" to "a".repeat(64), "signers" to "c".repeat(64),
            "min-sdk" to "25", "max-sdk" to "36", "abis" to "x86_64",
            "not-before" to "1799999800", "expires" to "1800002000",
            "url" to "https://updates.example.invalid/other.apk")) {
            val changed = payload(key to value)
            val header = ByteArrayOutputStream().also { DataOutputStream(it).apply {
                writeBytes("AUTHUPD1"); writeInt(changed.size); write(changed)
                write(original, 12 + payload().size, original.size - 12 - payload().size)
            } }.toByteArray()
            rejected { verifier().authorize(header) }
        }
        assertEquals(0L, store.read().sequence)
    }
    @Test fun wrongPublicKeyIsRejected() {
        rejected { verifier().authorize(envelope(wrongKey = true)) }
        assertEquals(0L, store.read().sequence)
    }
    @Test fun unknownKeyIsRejectedWithoutTrustLearning() {
        rejected { verifier().authorize(envelope(payload("key" to "unknown"))) }
        assertEquals(0L, store.read().sequence)
    }
    @Test fun expiredAndNotYetValidMetadataAreRejected() {
        rejected { verifier().authorize(envelope(payload("expires" to "1799999999"))) }
        rejected { verifier().authorize(envelope(payload("not-before" to "1800000001"))) }
        assertEquals(0L, store.read().sequence)
    }
    @Test fun absurdOrReversedValidityIsRejected() {
        rejected { verifier().authorize(envelope(payload("expires" to "1900000000"))) }
        rejected { verifier().authorize(envelope(payload("expires" to "1799999800"))) }
    }
    @Test fun installedVersionAndPreviouslyAuthorizedVersionCannotBeDowngraded() {
        rejected { verifier().authorize(envelope(payload("version" to "18"))) }
        verifier().authorize(envelope(payload("version" to "21")))
        rejected { verifier().authorize(envelope(payload("sequence" to "2", "version" to "20"))) }
        assertEquals(1L, store.read().sequence)
    }
    @Test fun wrongPackageSdkAndAbiAreRejected() {
        for (change in listOf("package" to "other.app", "min-sdk" to "31", "max-sdk" to "29", "abis" to "x86_64")) {
            rejected { verifier().authorize(envelope(payload(change))) }
        }
        assertEquals(0L, store.read().sequence)
    }
    @Test fun parserRejectsUnknownDuplicateNoncanonicalAndOversizedInput() {
        val valid = payload().toString(Charsets.US_ASCII)
        for (text in listOf(valid + "extra=value\n", valid + "sequence=2\n", valid.replace("sequence=1", "sequence=01"),
            valid.replace("\n", "\r\n"), valid.dropLast(1), "x".repeat(9000))) {
            rejected { verifier().authorize(envelope(text.toByteArray(Charsets.US_ASCII))) }
        }
        for (wire in listOf(envelope() + byteArrayOf(1), byteArrayOf(), ByteArray(17000))) {
            rejected { verifier().authorize(wire) }
        }
        assertEquals(0L, store.read().sequence)
    }
    @Test fun malformedBoundsDigestSignersAndUrlAreRejected() {
        for (change in listOf("size" to "0", "size" to "536870913", "sequence" to "9223372036854775808",
            "sha256" to "bad", "signers" to "$signer,$signer", "signers" to "bad", "min-sdk" to "23",
            "url" to "http://updates.example.invalid/app.apk", "url" to ("https://" + "user@" + "example.invalid/app.apk"))) {
            rejected { verifier().authorize(envelope(payload(change))) }
        }
    }
    @Test fun completedSequenceIsRejectedAfterRestart() {
        val v = verifier()
        val permit = v.authorize(envelope())
        v.confirmInstalled(permit, identity)
        assertTrue(store.read().completed)
        rejected { verifier().authorize(envelope()) }
    }
    @Test fun olderSequenceAndChangedSameSequenceAreRejected() {
        verifier().authorize(envelope(payload("sequence" to "2")))
        rejected { verifier().authorize(envelope()) }
        rejected { verifier().authorize(envelope(payload("sequence" to "2", "url" to "https://updates.example.invalid/different.apk"))) }
        assertEquals(2L, store.read().sequence)
    }
    @Test fun interruptedDownloadCanRetryIdenticalAuthenticatedPayloadAfterRestart() {
        val v = verifier()
        val permit = v.authorize(envelope())
        val target = File(temp.root, "retry.apk")
        try {
            v.download(permit, target, { it.writeText("da"); throw IOException("interrupted") }, { identity })
            fail("Transfer should fail")
        } catch (_: IOException) { }
        assertFalse(target.exists())
        assertFalse(File(temp.root, "retry.part").exists())
        val restarted = verifier()
        val retry = restarted.authorize(envelope())
        assertEquals("data", download(restarted, retry).readText())
        assertEquals(1L, store.read().sequence)
    }
    @Test fun cancellationLeavesNoPartialAndDoesNotConsumePendingRetry() {
        val v = verifier()
        val permit = v.authorize(envelope())
        val target = File(temp.root, "cancel.apk")
        try {
            v.download(permit, target, { it.writeText("da"); throw CancellationException() }, { identity })
            fail("Transfer should cancel")
        } catch (_: CancellationException) { }
        assertFalse(File(temp.root, "cancel.part").exists())
        assertFalse(target.exists())
        assertNotNull(verifier().authorize(envelope()))
    }
    @Test fun digestMismatchDeletesArtifactAndAllowsIdenticalRetry() {
        val v = verifier(); val permit = v.authorize(envelope()); val target = temp.newFile()
        rejected { v.download(permit, target, { it.writeText("evil") }, { identity }) }
        assertFalse(target.exists())
        assertEquals("data", download(v, v.authorize(envelope())).readText())
    }
    @Test fun wrongArchivePackageVersionOrCurrentSignerIsRejected() {
        val v = verifier(); val permit = v.authorize(envelope())
        for (archive in listOf(identity.copy(packageName = "other.app"), identity.copy(versionCode = 20),
            identity.copy(signers = setOf("a".repeat(64))), identity.copy(signers = setOf(signer, "a".repeat(64))))) {
            val target = temp.newFile()
            rejected { v.download(permit, target, { it.writeText("data") }, { archive }) }
            assertFalse(target.exists())
        }
    }
    @Test fun newerAuthorizationRevokesOldDownloadPermitBeforeTransfer() {
        val v = verifier(); val permit = v.authorize(envelope())
        v.authorize(envelope(payload("sequence" to "2", "version" to "20")))
        var transferred = false
        rejected { v.download(permit, temp.newFile(), { transferred = true }, { identity }) }
        assertFalse(transferred)
    }
    @Test fun rejectedStalePermitCannotDeleteAnotherVerifiedTarget() {
        val v = verifier(); val old = v.authorize(envelope())
        v.authorize(envelope(payload("sequence" to "2", "version" to "20")))
        val target = temp.newFile().apply { writeText("newer verified artifact") }
        rejected { v.download(old, target, { fail("Stale permit must not transfer") }, { identity }) }
        assertEquals("newer verified artifact", target.readText())
    }
    @Test fun failedConcurrentVerifierCannotDeleteSuccessfulVerifierArtifact() {
        val firstVerifier = verifier(); val firstPermit = firstVerifier.authorize(envelope())
        val secondVerifier = verifier(); val secondPermit = secondVerifier.authorize(envelope())
        val target = File(temp.root, "shared.apk")
        val enteredArchive = CountDownLatch(1); val releaseFirst = CountDownLatch(1)
        val secondCompleted = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val first = workers.submit<Boolean> {
                try {
                    firstVerifier.download(firstPermit, target, { it.writeText("data") }, {
                        enteredArchive.countDown()
                        check(releaseFirst.await(5, TimeUnit.SECONDS))
                        throw IOException("First archive lookup failed")
                    })
                    false
                } catch (_: IOException) { true }
            }
            assertTrue(enteredArchive.await(5, TimeUnit.SECONDS))
            val second = workers.submit<File> {
                secondVerifier.download(secondPermit, target, { it.writeText("data") }, { identity })
                    .also { secondCompleted.countDown() }
            }
            val overlapped = secondCompleted.await(300, TimeUnit.MILLISECONDS)
            releaseFirst.countDown()
            assertTrue(first.get(5, TimeUnit.SECONDS))
            val accepted = second.get(5, TimeUnit.SECONDS)
            assertTrue("Failure cleanup removed another verifier's accepted file", accepted.isFile)
            assertEquals("data", accepted.readText())
            assertFalse("Same-target downloads must serialize across instances", overlapped)
        } finally { releaseFirst.countDown(); workers.shutdownNow() }
    }
    @Test fun permitCannotBeTransferredToAnotherVerifier() {
        val permit = verifier().authorize(envelope())
        rejected { download(verifier(), permit) }
    }
    @Test fun invalidInstallConfirmationCannotConsumePendingRelease() {
        val v = verifier(); val permit = v.authorize(envelope())
        rejected { v.confirmInstalled(permit, identity.copy(signers = setOf("a".repeat(64)))) }
        assertFalse(store.read().completed)
    }
    @Test fun missingCorruptOrUninitializedReplayStateFailsClosed() {
        val directory = temp.newFolder("missing")
        val missing = FileUpdateReplayStore(directory)
        val v = AuthenticatedUpdateVerifier(mapOf("test-publisher" to keys.public), missing,
            UpdatePlatform("sample.app", 18, 30, "armeabi-v7a"), { now })
        rejected { v.authorize(envelope()) }
        missing.initialize()
        File(directory, "update-replay").writeText("corrupt")
        rejected { v.authorize(envelope()) }
        rejected { missing.initialize() }
    }
    @Test fun unsetAndBackwardClockAreRejectedAcrossRestart() {
        verifier().authorize(envelope())
        now--
        rejected { verifier().authorize(envelope(payload("sequence" to "2"))) }
        now = 0
        rejected { verifier().authorize(envelope()) }
        assertEquals(1L, store.read().sequence)
    }
    @Test fun clockSupplierFailureDoesNotChangeTrustedState() {
        val v = AuthenticatedUpdateVerifier(mapOf("test-publisher" to keys.public), store,
            UpdatePlatform("sample.app", 18, 30, "armeabi-v7a"), { error("Clock unavailable") })
        rejected { v.authorize(envelope()) }
        assertEquals(0L, store.read().sequence)
    }
    @Test fun failedReplayPersistenceDoesNotAuthorizeDownload() {
        val v = verifier()
        assertTrue(File(temp.root, "state/update-replay.next").mkdir())
        rejected { v.authorize(envelope()) }
        assertEquals(0L, store.read().sequence)
    }
    @Test fun expiredPermitCannotDownloadOrConfirmInstallation() {
        val v = verifier(); val permit = v.authorize(envelope())
        now += 1001
        rejected { download(v, permit) }
        rejected { v.confirmInstalled(permit, identity) }
    }
    @Test fun productionActivationRemainsUnavailable() {
        rejected { AuthenticatedUpdateVerifier.production() }
    }
    companion object {
        private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        private val otherKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
    }
}
