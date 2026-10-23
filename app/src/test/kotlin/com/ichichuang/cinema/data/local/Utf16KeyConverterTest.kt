package com.ichichuang.cinema.data.local

import com.ichichuang.cinema.data.local.codec.*
import org.junit.Assert.*
import org.junit.Test

class Utf16KeyConverterTest {
    private val converter = Utf16KeyConverter()

    @Test fun opaque_keys_preserve_all_code_units_without_collisions() {
        val keys = listOf("", " work ", "\uD800", "\uD801", "?", "\\ud800", "\uDC00", "\uD83D\uDE00", "\u0000")
        val bytes = keys.map(converter::encode)
        assertEquals(keys.size, bytes.map { it.toList() }.toSet().size)
        assertEquals(keys, bytes.map(converter::decode))
    }

    @Test fun incomplete_key_bytes_fail_without_disclosing_content() {
        val error = assertThrows(StorageReadException::class.java) { converter.decode(byteArrayOf(65)) }
        assertEquals(StorageReadFailure.MALFORMED, error.reason)
        assertNull(error.cause)
    }
}
