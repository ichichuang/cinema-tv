package com.ichichuang.cinema.data.local.migration

import com.ichichuang.cinema.data.local.codec.*
import kotlinx.serialization.json.*
import java.util.Collections
import java.security.MessageDigest

internal enum class LegacyStoreKind(val tag: String) { PROGRESS("progress"), BROWSE("browse"), FILTERS("filters") }
@ConsistentCopyVisibility
internal data class LegacyValue private constructor(val type: String, val encoded: String) {
    fun stringOrNull(): String? = if (type == "string") Json.parseToJsonElement(encoded).text() else null

    companion object {
        fun fromEncoded(type: String, encoded: JsonElement): LegacyValue = from(when (type) {
            "string" -> encoded.text()
            "int" -> encoded.integer()
            "long" -> encoded.longNumber()
            "float_bits" -> Float.fromBits(encoded.integer())
            "double_bits" -> Double.fromBits(encoded.longNumber())
            "boolean" -> (encoded as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: invalidPayload()
            "string_set" -> encoded.stringSet()
            "bytes" -> encoded.jsonArray.map { element ->
                val byte = element.integer()
                require(byte in -128..127)
                byte.toByte()
            }.toByteArray()
            else -> invalidPayload()
        })

        fun from(value: Any): LegacyValue {
            val (type, json) = when (value) {
                is String -> "string" to JsonPrimitive(value)
                is Int -> "int" to JsonPrimitive(value)
                is Long -> "long" to JsonPrimitive(value)
                is Float -> "float_bits" to JsonPrimitive(value.toRawBits())
                is Double -> "double_bits" to JsonPrimitive(value.toRawBits())
                is Boolean -> "boolean" to JsonPrimitive(value)
                is Set<*> -> {
                    require(value.all { it is String }) { "Unsupported legacy value type" }
                    "string_set" to stringsJson(value.map { it as String }.toSet())
                }
                is ByteArray -> "bytes" to JsonArray(value.map { JsonPrimitive(it.toInt()) })
                else -> throw IllegalArgumentException("Unsupported legacy value type")
            }
            return LegacyValue(type, json.storageJson())
        }
    }
}

internal data class LegacyEntry(val store: LegacyStoreKind, val key: String, val value: LegacyValue)
internal class LegacySnapshot private constructor(val entries: List<LegacyEntry>, val capturedAt: Long) {
    // Hash precisely the immutable logical content being imported, excluding capture metadata.
    val contentDigest: String = MessageDigest.getInstance("SHA-256").digest(buildJsonObject {
        put("format", 1)
        put("entries", entriesJson(entries))
    }.storageJson().toByteArray(Charsets.UTF_8)).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    companion object {
        fun create(entries: List<LegacyEntry>, capturedAt: Long): LegacySnapshot {
            require(entries.map { it.store to it.key }.distinct().size == entries.size) { "Duplicate legacy entry" }
            return LegacySnapshot(Collections.unmodifiableList(entries.sortedWith(compareBy({ it.store.tag }, { it.key }))), capturedAt)
        }
    }
}

private fun entriesJson(entries: List<LegacyEntry>): JsonArray = JsonArray(entries.map { entry -> buildJsonObject {
    put("store", entry.store.tag)
    put("key", entry.key)
    put("type", entry.value.type)
    put("value", Json.parseToJsonElement(entry.value.encoded))
} })

internal object LegacySnapshotCodec : JsonStorageCodec<LegacySnapshot>("legacy_snapshot") {
    override fun toJson(value: LegacySnapshot): JsonObject = buildJsonObject {
        put("capturedAt", value.capturedAt)
        put("entries", entriesJson(value.entries))
    }

    override fun fromJson(value: JsonObject): LegacySnapshot = LegacySnapshot.create(
        value.required("entries").jsonArray.map { element -> with(element.jsonObject) {
            LegacyEntry(LegacyStoreKind.entries.firstOrNull { it.tag == required("store").text() } ?: invalidPayload(),
                required("key").text(), LegacyValue.fromEncoded(required("type").text(), required("value")))
        } }, value.required("capturedAt").longNumber())
}
