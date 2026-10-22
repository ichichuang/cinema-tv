package com.ichichuang.cinema.data.update

import com.ichichuang.cinema.domain.model.UpdateInfo
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateArtifactVerifierTest {
    @get:Rule val temp = TemporaryFolder()
    private val signer = setOf("a".repeat(64))
    private val installed = UpdatePackageIdentity("sample.app", 18, signer)
    private val candidate = installed.copy(versionCode = 19)
    private val manifest = UpdateInfo(19, "sample", "https://example.invalid/app.apk")

    private fun file() = temp.newFile().apply { writeText("synthetic artifact") }
    private fun verifier(
        current: UpdatePackageIdentity? = installed,
        archive: UpdatePackageIdentity? = candidate,
    ) = UpdateArtifactVerifier({ current }, { archive })

    private fun rejected(apk: File, action: () -> Unit) {
        try {
            action()
            fail("Expected verification to reject the artifact")
        } catch (_: Exception) {
            assertFalse("Rejected artifacts must be removed", apk.exists())
        }
    }

    @Test fun validLegacyManifestRemainsCompatible() {
        val apk = file()
        assertEquals(apk, verifier().verify(apk, manifest))
        assertTrue(apk.exists())
    }

    @Test fun mismatchedPackageIsRejected() {
        val apk = file()
        rejected(apk) { verifier(archive = candidate.copy(packageName = "other.app")).verify(apk, manifest) }
    }

    @Test fun mismatchedManifestVersionIsRejected() {
        val apk = file()
        rejected(apk) { verifier(archive = candidate.copy(versionCode = 20)).verify(apk, manifest) }
    }

    @Test fun replayOfInstalledVersionIsRejected() {
        val apk = file()
        rejected(apk) { verifier(archive = installed).verify(apk, manifest.copy(versionCode = 18)) }
    }

    @Test fun downgradeIsRejectedEvenWhenManifestAgrees() {
        val apk = file()
        rejected(apk) { verifier(archive = candidate.copy(versionCode = 17)).verify(apk, manifest.copy(versionCode = 17)) }
    }

    @Test fun missingCandidateMetadataIsRejected() {
        val apk = file()
        rejected(apk) { verifier(archive = null).verify(apk, manifest) }
    }

    @Test fun missingInstalledMetadataIsRejected() {
        val apk = file()
        rejected(apk) { verifier(current = null).verify(apk, manifest) }
    }

    @Test fun failedInstalledLookupRemovesFile() {
        val apk = file()
        rejected(apk) { UpdateArtifactVerifier({ error("lookup failed") }, { candidate }).verify(apk, manifest) }
    }

    @Test fun failedArchiveLookupRemovesFile() {
        val apk = file()
        rejected(apk) { UpdateArtifactVerifier({ installed }, { error("invalid archive") }).verify(apk, manifest) }
    }

    @Test fun missingOrMalformedSignersAreRejected() {
        for (signers in listOf(null, emptySet(), setOf(""), setOf("z".repeat(64)))) {
            val apk = file()
            rejected(apk) { verifier(archive = candidate.copy(signers = signers)).verify(apk, manifest) }
        }
    }

    @Test fun additionalSignerIsRejected() {
        val apk = file()
        rejected(apk) { verifier(archive = candidate.copy(signers = signer + "b".repeat(64))).verify(apk, manifest) }
    }

    @Test fun emptyFileIsRejectedEvenIfMetadataIsReturned() {
        val apk = temp.newFile()
        rejected(apk) { verifier().verify(apk, manifest) }
    }

    @Test fun cachedArtifactSizeIsRechecked() {
        val apk = file()
        rejected(apk) { verifier().verify(apk, manifest.copy(sizeBytes = apk.length() + 1)) }
    }

    @Test fun matchingDigestIsAccepted() {
        val apk = file()
        val hash = MessageDigest.getInstance("SHA-256").digest(apk.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(apk, verifier().verify(apk, manifest.copy(sha256 = hash)))
    }

    @Test fun sameSizeTamperingIsRejectedWhenDigestIsProvided() {
        val apk = file()
        val hash = MessageDigest.getInstance("SHA-256").digest(apk.readBytes()).joinToString("") { "%02x".format(it) }
        apk.writeText("x".repeat(apk.length().toInt()))
        rejected(apk) { verifier().verify(apk, manifest.copy(sha256 = hash)) }
    }

    @Test fun malformedDigestIsRejected() {
        val apk = file()
        rejected(apk) { verifier().verify(apk, manifest.copy(sha256 = "invalid")) }
    }
}
