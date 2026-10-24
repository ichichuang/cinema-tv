package com.ichichuang.cinema.feature.player

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProbeMediaFileTest {
    @Test fun canonical_private_file_is_accepted() {
        val root = Files.createTempDirectory("cinema-private-media").toFile()
        try {
            val media = File(root, "synthetic.wav").apply { writeBytes(byteArrayOf(1)) }
            assertEquals(media.canonicalFile, ProbeMediaFile.requirePrivate(root, media.toURI().toString()))
        } finally { root.deleteRecursively() }
    }
    @Test fun sibling_prefix_directory_traversal_symlink_and_missing_file_are_refused() {
        val parent = Files.createTempDirectory("cinema-private-boundary").toFile()
        try {
            val root = File(parent, "private").apply { mkdir() }
            val neighbor = File(parent, "private-neighbor").apply { mkdir() }
            val outside = File(neighbor, "synthetic.wav").apply { writeBytes(byteArrayOf(1)) }
            val link = File(root, "link.wav")
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            for (file in listOf(outside, link, File(root, "../private-neighbor/synthetic.wav"), File(root, "missing.wav"), root)) {
                assertThrows(IllegalArgumentException::class.java) { ProbeMediaFile.requirePrivate(root, file.toURI().toString()) }
            }
        } finally { parent.deleteRecursively() }
    }
    @Test fun network_uri_and_file_uri_query_or_fragment_are_refused() {
        val root = Files.createTempDirectory("cinema-private-uri").toFile()
        try {
            val media = File(root, "synthetic.wav").apply { writeBytes(byteArrayOf(1)) }
            for (uri in listOf("https://synthetic.invalid/media.wav", media.toURI().toString() + "?x=1", media.toURI().toString() + "#part")) {
                assertThrows(IllegalArgumentException::class.java) { ProbeMediaFile.requirePrivate(root, uri) }
            }
        } finally { root.deleteRecursively() }
    }
}
