package com.ichichuang.cinema.feature.player

import java.io.File
import java.security.MessageDigest
import com.ichichuang.cinema.data.local.codec.storageJson
import kotlinx.serialization.json.*

/** Verification of a declared fixture mapping; this never allocates identities from media. */
internal object HandoffCatalogFingerprint {
    fun compute(catalog: PlaybackProbeCatalog, root: File): String {
        require(catalog.probeMedia.isNotEmpty())
        val rows = catalog.probeMedia.sortedWith(compareBy({ it.detail.movie.origin }, { it.detail.movie.id }, { it.lineIndex }, { it.episodeIndex }))
        val proof = JsonArray(rows.map { media ->
            require(ProbeIdentity.resolve(catalog, media.detail, media.lineIndex, media.episodeIndex) == media)
            val item = media.detail.lines[media.lineIndex].episodes[media.episodeIndex]
            val file = ProbeMediaFile.requirePrivate(root, item.url)
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            buildJsonObject {
                put("declaredDetail", media.detail.toString()); put("line", media.lineIndex); put("episode", media.episodeIndex)
                put("work", media.target.workId.value); put("episodeId", media.target.episodeId!!.value)
                put("variant", media.target.variantId!!.value); put("mediaDigest", digest.digest().hex())
            }
        })
        return MessageDigest.getInstance("SHA-256").digest(proof.storageJson().toByteArray(Charsets.UTF_8)).hex()
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
