package com.ichichuang.cinema.data.update

import com.ichichuang.cinema.data.remote.SourceException
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class UpdateManifestParserTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun parse(extra: String = "") = UpdateManifestParser.parse(
        """{"versionCode":19,"apkUrl":"https://example.invalid/app.apk"$extra}""", json,
    )

    @Test fun legacyManifestNeedsNoDigest() {
        val info = parse()
        assertEquals(19, info.versionCode)
        assertNull(info.sha256)
    }

    @Test fun legacyAliasesArePreserved() {
        val info = UpdateManifestParser.parse(
            """{"code":"19","name":"sample","download_url":"http://example.invalid/app.apk","mirror_url":"https://example.invalid/mirror.apk","size_bytes":"42"}""", json,
        )
        assertEquals("sample", info.versionName)
        assertEquals(42L, info.sizeBytes)
        assertEquals("http://example.invalid/app.apk", info.apkUrl)
        assertNotNull(info.mirrorUrl)
    }

    @Test fun digestIsNotDiscarded() {
        val digest = "a".repeat(64)
        assertEquals(digest, parse(",\"sha256\":\"$digest\"").sha256)
    }

    @Test fun malformedDigestDoesNotFallBackToLegacy() {
        for (value in listOf("null", "{}", "17", "\"\"", "\"bad\"")) {
            assertThrows(SourceException::class.java) { parse(",\"sha256\":$value") }
        }
    }

    @Test fun nonpositiveVersionIsRejected() {
        assertThrows(SourceException::class.java) {
            UpdateManifestParser.parse("""{"versionCode":0,"apkUrl":"https://example.invalid/app.apk"}""", json)
        }
    }

    @Test fun numericDigestIsNotAStringDigest() {
        assertThrows(SourceException::class.java) { parse(",\"sha256\":" + "1".repeat(64)) }
    }
}
