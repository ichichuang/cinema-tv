package com.ichichuang.cinema.data.local.codec

import kotlinx.serialization.json.*

internal abstract class JsonStorageCodec<T>(private val kind: String) : StorageCodec<T> {
    final override fun encode(value: T): String = buildJsonObject {
        put("kind", kind)
        put("version", 1)
        put("value", toJson(value))
    }.storageJson()

    final override fun decode(encoded: String): T {
        try {
            val envelope = Json.parseToJsonElement(encoded).jsonObject
            if (envelope.required("version").integer() != 1) {
                throw StorageReadException(StorageReadFailure.UNSUPPORTED_VERSION)
            }
            if (envelope.required("kind").text() != kind) {
                throw StorageReadException(StorageReadFailure.WRONG_KIND)
            }
            return fromJson(envelope.required("value").jsonObject)
        } catch (_: IllegalArgumentException) {
            throw StorageReadException(StorageReadFailure.MALFORMED)
        }
    }

    protected abstract fun toJson(value: T): JsonObject
    protected abstract fun fromJson(value: JsonObject): T
}

internal fun JsonObject.required(key: String): JsonElement = get(key) ?: invalidPayload()
internal fun JsonElement.text(): String = (this as? JsonPrimitive)
    ?.takeIf { it.isString }?.content ?: invalidPayload()
internal fun JsonElement.integer(): Int = number().intOrNull ?: invalidPayload()
internal fun JsonElement.longNumber(): Long = number().longOrNull ?: invalidPayload()
internal fun JsonElement.floatNumber(): Float = number().floatOrNull
    ?.takeIf { it.isFinite() } ?: invalidPayload()
private fun JsonElement.number(): JsonPrimitive = (this as? JsonPrimitive)
    ?.takeIf { !it.isString && it !== JsonNull } ?: invalidPayload()
internal fun invalidPayload(): Nothing = throw IllegalArgumentException("Invalid storage payload")
internal fun stringJson(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
internal fun intJson(value: Int?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
internal fun longJson(value: Long?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
internal fun <T> JsonElement.nullable(read: (JsonElement) -> T): T? = if (this === JsonNull) null else read(this)
internal fun stringsJson(values: Set<String>): JsonArray = JsonArray(values.sorted().map(::JsonPrimitive))
internal fun JsonElement.stringSet(): Set<String> {
    val values = jsonArray.map { it.text() }
    require(values.distinct().size == values.size)
    return values.toSet()
}

/** Escape UTF-16 code units before any UTF-8/SQLite text boundary can replace them. */
internal fun JsonElement.storageJson(): String = buildString {
    for (character in this@storageJson.toString()) {
        if (character.code in 0xd800..0xdfff) {
            append("\\u")
            append(character.code.toString(16).padStart(4, '0'))
        } else append(character)
    }
}
