package com.ichichuang.cinema.feature.player

import com.ichichuang.cinema.data.userdata.PlaybackTarget
import com.ichichuang.cinema.domain.model.*
import com.ichichuang.cinema.domain.model.catalog.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class HandoffCatalogFingerprintTest {
    @Test fun actual_media_bytes_bind_the_declared_variant_even_when_uri_and_identity_stay_the_same() {
        val root = Files.createTempDirectory("handoff-fingerprint").toFile()
        try {
            val file = root.resolve("synthetic.wav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val detail = MovieDetail(Movie("id", "合成", 0, 0.0, "", 0, "", origin = "source"),
                lines = listOf(PlayLine("line", listOf(Episode("一", file.toURI().toString())))))
            val item = ProbeMedia(detail, 0, 0, PlaybackTarget(WorkId("work"), EpisodeId("episode"), VariantId("cut")))
            val catalog = object : PlaybackProbeCatalog { override val probeMedia = listOf(item) }
            val before = HandoffCatalogFingerprint.compute(catalog, root)
            assertTrue(before.matches(Regex("[a-f0-9]{64}")))
            assertEquals(before, HandoffCatalogFingerprint.compute(catalog, root))
            file.writeBytes(byteArrayOf(1, 2, 4))
            assertNotEquals(before, HandoffCatalogFingerprint.compute(catalog, root))
        } finally { root.deleteRecursively() }
    }
}
