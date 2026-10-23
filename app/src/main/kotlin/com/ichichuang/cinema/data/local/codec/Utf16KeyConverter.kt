package com.ichichuang.cinema.data.local.codec

import androidx.room.TypeConverter

internal class Utf16KeyConverter {
    // Fixed big-endian code units, with no charset encoder's replacement behavior.
    @TypeConverter fun encode(value: String): ByteArray = ByteArray(value.length * 2).also { bytes ->
        value.forEachIndexed { index, character ->
            bytes[index * 2] = (character.code ushr 8).toByte()
            bytes[index * 2 + 1] = character.code.toByte()
        }
    }

    @TypeConverter fun decode(value: ByteArray): String {
        if (value.size % 2 != 0) throw StorageReadException(StorageReadFailure.MALFORMED)
        return CharArray(value.size / 2) { index ->
            (((value[index * 2].toInt() and 0xff) shl 8) or (value[index * 2 + 1].toInt() and 0xff)).toChar()
        }.concatToString()
    }
}
