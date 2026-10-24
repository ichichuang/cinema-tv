package com.ichichuang.cinema.data.subscription

import com.ichichuang.cinema.domain.model.catalog.SourceId
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Collections

internal enum class SubscriptionFailure {
    MALFORMED, TOO_LARGE, UNTRUSTED, SIGNATURE, EXPIRED, CLOCK_ROLLBACK, REPLAY,
    CONSENT_REQUIRED, STALE_CONSENT, SUPERSEDED, DESTINATION, NETWORK, TIMEOUT, STORAGE,
}
internal class SubscriptionException(val failure: SubscriptionFailure) : Exception(failure.name)
internal fun reject(failure: SubscriptionFailure): Nothing = throw SubscriptionException(failure)

/** Explicit owner-supplied catalog/origin/key binding. There is deliberately no default root. */
internal data class CatalogTrustRule(val catalogId: String, val origin: String, val publisherId: String, val key: PublicKey)
internal data class DirectorySource(val id: SourceId, val name: String, val endpoint: String)
internal class VerifiedDirectory private constructor(
    val catalogId: String, val origin: String, val revision: Long, val issuedAt: Long,
    val expiresAt: Long, val digest: String, sources: List<DirectorySource>,
) {
    val sources: List<DirectorySource> = Collections.unmodifiableList(ArrayList(sources))
    companion object {
        internal fun verified(catalogId: String, origin: String, revision: Long, issuedAt: Long,
            expiresAt: Long, digest: String, sources: List<DirectorySource>) =
            VerifiedDirectory(catalogId, origin, revision, issuedAt, expiresAt, digest, sources)
    }
}
internal sealed interface DirectoryValidation {
    data object Empty : DirectoryValidation
    data class Trusted(val directory: VerifiedDirectory) : DirectoryValidation
}

internal class DirectoryValidator(rules: List<CatalogTrustRule>) {
    private val rules = rules.toList()
    fun validate(bytes: ByteArray, catalogId: String, origin: String, now: Long): DirectoryValidation {
        val root = DirectoryJson.parse(bytes)
        if (root["sources"] == JsonArray(emptyList()) &&
            (root.keys == setOf("sources") || root.keys == setOf("sources", "notice"))) {
            if ("notice" in root) root.string("notice")
            return DirectoryValidation.Empty
        }
        root.fields("catalogId", "origin", "publisherId", "revision", "issuedAt", "expiresAt", "sources", "signature")
        val suppliedId = root.string("catalogId"); val suppliedOrigin = root.string("origin")
        val publisher = root.string("publisherId")
        if (suppliedId != catalogId || suppliedOrigin != origin) reject(SubscriptionFailure.UNTRUSTED)
        val matching = rules.filter { it.catalogId == catalogId && it.origin == origin && it.publisherId == publisher }
        if (matching.size != 1) reject(SubscriptionFailure.UNTRUSTED)
        val key = matching.single().key as? RSAPublicKey ?: reject(SubscriptionFailure.UNTRUSTED)
        if (key.modulus.bitLength() < 3072 || key.modulus.bitLength() > 8192 || key.publicExponent.bitLength() < 2)
            reject(SubscriptionFailure.UNTRUSTED)
        val revision = root.number("revision"); val issuedAt = root.number("issuedAt"); val expiresAt = root.number("expiresAt")
        if (revision < 0) reject(SubscriptionFailure.MALFORMED)
        if (now < 0 || issuedAt < 0 || issuedAt > now || expiresAt <= now || expiresAt <= issuedAt ||
            expiresAt - issuedAt > MAX_VALIDITY_MILLIS) reject(SubscriptionFailure.EXPIRED)
        val entries = root["sources"] as? JsonArray ?: reject(SubscriptionFailure.MALFORMED)
        if (entries.size > MAX_SOURCES) reject(SubscriptionFailure.TOO_LARGE)
        val ids = mutableSetOf<String>(); val endpoints = mutableSetOf<String>()
        val sources = entries.map { entry ->
            val source = entry as? JsonObject ?: reject(SubscriptionFailure.MALFORMED)
            source.fields("id", "name", "endpoint")
            val id = source.string("id"); val name = source.string("name"); val endpoint = source.string("endpoint")
            if (!id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) || name.isBlank() || name.length > 160)
                reject(SubscriptionFailure.MALFORMED)
            val normalizedEndpoint = PublicDirectoryPolicy.url(endpoint).toString()
            if (!ids.add(id) || !endpoints.add(normalizedEndpoint)) reject(SubscriptionFailure.MALFORMED)
            DirectorySource(SourceId(id), name, endpoint)
        }
        val canonical = canonical(catalogId, origin, publisher, revision, issuedAt, expiresAt, sources)
        val signatureHex = root.string("signature")
        if (signatureHex.length != (key.modulus.bitLength() + 7) / 8 * 2) reject(SubscriptionFailure.SIGNATURE)
        val signature = unhex(signatureHex)
        val valid = try { Signature.getInstance("SHA256withRSA").run { initVerify(key); update(canonical); verify(signature) } }
        catch (_: Exception) { false }
        if (!valid) reject(SubscriptionFailure.SIGNATURE)
        // An authenticated empty directory still carries no replacement/consent authority.
        if (sources.isEmpty()) return DirectoryValidation.Empty
        return DirectoryValidation.Trusted(VerifiedDirectory.verified(catalogId, origin, revision, issuedAt,
            expiresAt, digest(canonical), sources))
    }

    companion object {
        const val MAX_BYTES = 65_536
        const val MAX_SOURCES = 64
        const val MAX_VALIDITY_MILLIS = 7 * 24 * 60 * 60 * 1000L
        internal fun canonical(catalogId: String, origin: String, publisherId: String, revision: Long,
            issuedAt: Long, expiresAt: Long, sources: List<DirectorySource>): ByteArray = buildJsonObject {
            put("catalogId", catalogId); put("origin", origin); put("publisherId", publisherId)
            put("revision", revision); put("issuedAt", issuedAt); put("expiresAt", expiresAt)
            put("sources", buildJsonArray { sources.sortedBy { it.id.value }.forEach { source -> add(buildJsonObject {
                put("id", source.id.value); put("name", source.name); put("endpoint", source.endpoint)
            }) } })
        }.toString().toByteArray(Charsets.UTF_8)
        internal fun digest(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        internal fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        internal fun unhex(value: String): ByteArray {
            if (value.isEmpty() || value.length % 2 != 0 || value.any { it !in "0123456789abcdef" }) reject(SubscriptionFailure.MALFORMED)
            return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
    }
}

/** Bounded grammar pass detects decoded duplicate keys before building a general JSON tree. */
internal object DirectoryJson {
    fun parse(bytes: ByteArray): JsonObject {
        if (bytes.size > DirectoryValidator.MAX_BYTES) reject(SubscriptionFailure.TOO_LARGE)
        val raw = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
        catch (_: Exception) { reject(SubscriptionFailure.MALFORMED) }
        return Parser(raw).root()
    }
    private class Parser(private val raw: String) {
        private var p = 0
        private var tokens = 0
        fun root(): JsonObject {
            val value = value(0); space()
            if (p != raw.length || value !is JsonObject) reject(SubscriptionFailure.MALFORMED)
            return value
        }
        private fun space() { while (p < raw.length && raw[p] in " \n\r\t") p++ }
        private fun take(c: Char): Boolean { space(); if (p < raw.length && raw[p] == c) { p++; return true }; return false }
        private fun expect(c: Char) { if (!take(c)) reject(SubscriptionFailure.MALFORMED) }
        private fun value(depth: Int): JsonElement {
            if (depth > 8 || ++tokens > 2048) reject(SubscriptionFailure.MALFORMED)
            space(); if (p == raw.length) reject(SubscriptionFailure.MALFORMED)
            return when (raw[p]) {
                '{' -> { p++; val map = linkedMapOf<String, JsonElement>(); if (!take('}')) {
                    do { val key = string(); expect(':'); if (map.containsKey(key)) reject(SubscriptionFailure.MALFORMED)
                        map[key] = value(depth + 1)
                    } while (take(',')); expect('}')
                }; JsonObject(map) }
                '[' -> { p++; val list = arrayListOf<JsonElement>(); if (!take(']')) {
                    do { if (list.size >= 128) reject(SubscriptionFailure.MALFORMED); list += value(depth + 1) } while (take(',')); expect(']')
                }; JsonArray(list) }
                '"' -> JsonPrimitive(string())
                else -> { val start = p; while (p < raw.length && raw[p] !in ",]} \n\r\t") p++
                    val token = raw.substring(start, p)
                    if (token.length > 20) reject(SubscriptionFailure.MALFORMED)
                    when (token) { "null" -> JsonNull; "true" -> JsonPrimitive(true); "false" -> JsonPrimitive(false)
                        else -> { if (!token.matches(Regex("-?(0|[1-9][0-9]*)"))) reject(SubscriptionFailure.MALFORMED)
                            JsonPrimitive(token.toLongOrNull() ?: reject(SubscriptionFailure.MALFORMED)) }
                    }
                }
            }
        }
        private fun string(): String {
            space(); val start = p; if (p == raw.length || raw[p++] != '"') reject(SubscriptionFailure.MALFORMED)
            var escaped = false
            while (p < raw.length) {
                val c = raw[p++]; if (p - start > 4096) reject(SubscriptionFailure.MALFORMED)
                if (!escaped && c == '"') {
                    val decoded = try { Json.parseToJsonElement(raw.substring(start, p)).jsonPrimitive.content }
                    catch (_: Exception) { reject(SubscriptionFailure.MALFORMED) }
                    if (decoded.length > 2048 || decoded.any { it.isSurrogate() || it.code < 32 }) reject(SubscriptionFailure.MALFORMED)
                    return decoded
                }
                if (c.code < 32) reject(SubscriptionFailure.MALFORMED)
                escaped = !escaped && c == '\\'
            }
            reject(SubscriptionFailure.MALFORMED)
        }
    }
}
internal fun JsonObject.fields(vararg expected: String) { if (keys != expected.toSet()) reject(SubscriptionFailure.MALFORMED) }
internal fun JsonObject.string(key: String): String {
    val p = get(key) as? JsonPrimitive ?: reject(SubscriptionFailure.MALFORMED)
    if (!p.isString) reject(SubscriptionFailure.MALFORMED)
    return p.content
}
internal fun JsonObject.number(key: String): Long {
    val p = get(key) as? JsonPrimitive ?: reject(SubscriptionFailure.MALFORMED)
    if (p.isString) reject(SubscriptionFailure.MALFORMED)
    return p.longOrNull ?: reject(SubscriptionFailure.MALFORMED)
}
