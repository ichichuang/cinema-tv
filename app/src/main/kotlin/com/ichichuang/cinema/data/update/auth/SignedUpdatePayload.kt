package com.ichichuang.cinema.data.update.auth

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

internal class UpdateAuthenticationException(message: String) : IOException(message)
internal fun authenticated(condition: Boolean) {
    if (!condition) throw UpdateAuthenticationException("Authenticated update rejected")
}

internal data class UpdatePlatform(val packageName: String, val installedVersion: Long, val sdk: Int, val abi: String)

internal data class SignedUpdatePayload(
    val keyId: String, val packageName: String, val sequence: Long, val version: Long,
    val size: Long, val sha256: String, val signers: Set<String>, val minSdk: Int, val maxSdk: Int,
    val abis: Set<String>, val notBefore: Long, val expires: Long, val url: String,
)

/** Protocol version is a wire format version. No JSON normalization or unsigned extension fields. */
internal object SignedUpdateWire {
    private val fields = listOf("key", "package", "sequence", "version", "size", "sha256", "signers",
        "min-sdk", "max-sdk", "abis", "not-before", "expires", "url")
    private val hex = Regex("[0-9a-f]{64}")
    private val number = Regex("0|[1-9][0-9]{0,18}")

    data class Envelope(val bytes: ByteArray, val signature: ByteArray, val payload: SignedUpdatePayload)

    fun parse(wire: ByteArray): Envelope {
        authenticated(wire.size in 14..16384)
        try {
            DataInputStream(ByteArrayInputStream(wire)).use { input ->
                val magic = ByteArray(8).also(input::readFully)
                authenticated(magic.contentEquals("AUTHUPD1".toByteArray(Charsets.US_ASCII)))
                val size = input.readInt()
                authenticated(size in 1..8192 && input.available() >= size + 2)
                val bytes = ByteArray(size).also(input::readFully)
                val signatureSize = input.readUnsignedShort()
                authenticated(signatureSize in 384..512 && input.available() == signatureSize)
                val signature = ByteArray(signatureSize).also(input::readFully)
                return Envelope(bytes, signature, payload(bytes))
            }
        } catch (error: UpdateAuthenticationException) {
            throw error
        } catch (_: Exception) {
            throw UpdateAuthenticationException("Invalid authenticated-update envelope")
        }
    }

    private fun payload(bytes: ByteArray): SignedUpdatePayload {
        authenticated(bytes.all { it == 10.toByte() || it.toInt() in 32..126 })
        val lines = bytes.toString(Charsets.US_ASCII).split('\n')
        authenticated(lines.size == fields.size + 2 && lines.first() == "cinema-update-auth/1" && lines.last() == "")
        val values = fields.mapIndexed { index, name ->
            authenticated(lines[index + 1].startsWith("$name="))
            lines[index + 1].substring(name.length + 1)
        }
        fun numeric(index: Int): Long {
            authenticated(number.matches(values[index]))
            return values[index].toLongOrNull() ?: throw UpdateAuthenticationException("Invalid numeric field")
        }
        fun sortedSet(index: Int, allowed: (String) -> Boolean): Set<String> {
            val items = values[index].split(',')
            authenticated(items.size in 1..8 && items == items.distinct().sorted() && items.all(allowed))
            return items.toSet()
        }
        authenticated(Regex("[A-Za-z0-9_-]{1,64}").matches(values[0]))
        authenticated(values[1].length <= 255 && Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(values[1]))
        val sequence = numeric(2); val version = numeric(3); val size = numeric(4)
        val minSdk = numeric(7); val maxSdk = numeric(8)
        val notBefore = numeric(10); val expires = numeric(11)
        authenticated(sequence > 0 && version in 1..Int.MAX_VALUE.toLong() && size in 1..536870912L)
        authenticated(hex.matches(values[5]) && minSdk in 24..100 && maxSdk in minSdk..100)
        authenticated(notBefore >= MIN_CLOCK && expires > notBefore && expires - notBefore <= 31L * 86400)
        val signers = sortedSet(6, hex::matches)
        val abis = sortedSet(9) { it in setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
        authenticated(values[12].length <= 2048)
        val uri = URI(values[12])
        authenticated(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null && uri.rawFragment == null)
        return SignedUpdatePayload(values[0], values[1], sequence, version, size, values[5], signers,
            minSdk.toInt(), maxSdk.toInt(), abis, notBefore, expires, values[12])
    }

    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    const val MIN_CLOCK = 1_704_067_200L // 2024-01-01; unset device clocks fail closed.
}
