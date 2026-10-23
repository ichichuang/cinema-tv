package com.ichichuang.cinema.data.local.codec

internal interface StorageCodec<T> {
    fun encode(value: T): String
    fun decode(encoded: String): T
}

internal enum class StorageReadFailure { MALFORMED, UNSUPPORTED_VERSION, WRONG_KIND, IO }

internal class StorageReadException(val reason: StorageReadFailure) :
    RuntimeException("storage_read_${reason.name.lowercase(java.util.Locale.ROOT)}")
