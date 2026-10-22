package com.ichichuang.cinema.data.update.auth

import com.ichichuang.cinema.data.update.UpdateDownloadFiles
import com.ichichuang.cinema.data.update.UpdatePackageIdentity
import java.io.File
import java.io.RandomAccessFile
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey

internal class AuthenticatedDownload internal constructor(
    internal val payload: SignedUpdatePayload, internal val digest: String, private val owner: Any,
) {
    val url: String get() = payload.url
    internal fun belongsTo(candidate: Any) = owner === candidate
}

/** Candidate only: no network, production roots, DI registration or legacy fallback. */
internal class AuthenticatedUpdateVerifier(
    trustRoots: Map<String, PublicKey>, private val store: FileUpdateReplayStore,
    private val platform: UpdatePlatform, private val clock: () -> Long,
) {
    private val roots = trustRoots.toMap()
    private val owner = Any()

    fun authorize(wire: ByteArray): AuthenticatedDownload {
        val envelope = SignedUpdateWire.parse(wire)
        val payload = envelope.payload
        val root = roots[payload.keyId] as? RSAPublicKey
            ?: throw UpdateAuthenticationException("Unknown publisher key")
        authenticated(root.modulus.bitLength() in 3072..4096 && root.publicExponent.toLong() == 65537L)
        val validSignature = try {
            Signature.getInstance("SHA256withRSA").run {
                initVerify(root)
                update(envelope.bytes)
                verify(envelope.signature)
            }
        } catch (_: Exception) { false }
        authenticated(validSignature)
        authenticated(payload.packageName == platform.packageName && payload.version > platform.installedVersion)
        authenticated(platform.sdk in payload.minSdk..payload.maxSdk && platform.abi in payload.abis)
        val digest = SignedUpdateWire.digest(envelope.bytes)
        store.update { old ->
            val now = validClock(payload, old)
            authenticated(payload.sequence >= old.sequence && payload.version >= old.version)
            if (payload.sequence == old.sequence) authenticated(!old.completed && digest == old.digest)
            UpdateReplayRecord(payload.sequence, digest, payload.version, false, now)
        }
        return AuthenticatedDownload(payload, digest, owner)
    }

    fun download(
        permit: AuthenticatedDownload, target: File, transfer: (File) -> Unit,
        readArchive: (File) -> UpdatePackageIdentity?,
    ): File = synchronized(downloadLock) {
        val destination = target.canonicalFile
        RandomAccessFile(File(destination.parentFile, destination.name + ".download-lock"), "rw").use { lock ->
            lock.channel.lock().use { downloadLocked(permit, destination, transfer, readArchive) }
        }
    }

    private fun downloadLocked(
        permit: AuthenticatedDownload, target: File, transfer: (File) -> Unit,
        readArchive: (File) -> UpdatePackageIdentity?,
    ): File {
        // An invalid/stale permit owns no file and must not remove a prior successful download.
        validatePermit(permit, store.read())
        var accepted = false
        var replacedTarget = false
        try {
            UpdateDownloadFiles.write(target, permit.payload.size, transfer)
            replacedTarget = true
            authenticated(target.isFile && target.length() == permit.payload.size)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    digest.update(buffer, 0, length)
                }
            }
            authenticated(digest.digest().joinToString("") { "%02x".format(it) } == permit.payload.sha256)
            verifyIdentity(permit.payload, readArchive(target))
            validatePermit(permit, store.read())
            accepted = true
            return target
        } finally { if (replacedTarget && !accepted) target.delete() }
    }

    fun confirmInstalled(permit: AuthenticatedDownload, installed: UpdatePackageIdentity) {
        store.update { old ->
            validatePermit(permit, old)
            verifyIdentity(permit.payload, installed)
            old.copy(completed = true, lastClock = validClock(permit.payload, old))
        }
    }

    private fun verifyIdentity(payload: SignedUpdatePayload, identity: UpdatePackageIdentity?) {
        authenticated(identity != null && identity.packageName == payload.packageName
            && identity.versionCode == payload.version && identity.signers == payload.signers)
    }

    private fun validatePermit(permit: AuthenticatedDownload, state: UpdateReplayRecord) {
        authenticated(permit.belongsTo(owner) && !state.completed && permit.payload.sequence == state.sequence
            && permit.digest == state.digest)
        validClock(permit.payload, state)
    }

    private fun validClock(payload: SignedUpdatePayload, state: UpdateReplayRecord): Long {
        val now = try { clock() } catch (_: Exception) { throw UpdateAuthenticationException("Clock unavailable") }
        authenticated(now >= SignedUpdateWire.MIN_CLOCK && now >= state.lastClock
            && now >= payload.notBefore && now <= payload.expires)
        return now
    }

    companion object {
        private val downloadLock = Any()
        private const val PRODUCTION_ENABLED = false
        fun production(): AuthenticatedUpdateVerifier {
            authenticated(PRODUCTION_ENABLED)
            throw UpdateAuthenticationException("Production trust bootstrap is not configured")
        }
    }
}
