package com.ichichuang.cinema.data.userdata

import com.ichichuang.cinema.data.local.migration.LegacySnapshot
import com.ichichuang.cinema.data.local.migration.*
import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Collections

internal data class HandoffLink(val origin: String, val movieId: String, val line: Int, val episode: Int,
    val target: PlaybackTarget) {
    val movieKey get() = "$origin#$movieId"
}
internal class HandoffManifest(val expectedDigest: String, val catalogRevision: String, links: List<HandoffLink>,
    private val catalogProof: (() -> String)? = null) {
    val links: List<HandoffLink> = Collections.unmodifiableList(ArrayList(links))
    fun catalogIsCurrent(): Boolean = catalogProof?.invoke() == catalogRevision
    val mappingDigest: String = MessageDigest.getInstance("SHA-256").digest(buildJsonObject {
        put("revision", catalogRevision)
        put("links", JsonArray(links.sortedWith(compareBy({ it.origin }, { it.movieId }, { it.line }, { it.episode })).map {
            buildJsonObject {
                put("origin", it.origin); put("movie", it.movieId); put("line", it.line); put("episode", it.episode)
                put("work", it.target.workId.value); put("episodeId", it.target.episodeId?.value)
                put("variant", it.target.variantId?.value)
            }
        }))
    }.storageJson().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
internal data class LegacyHandoffAudit(val eligible: Boolean, val reason: String,
    val digest: String, val mappingDigest: String, val restored: List<com.ichichuang.cinema.domain.model.userdata.PlaybackSnapshot>) {
    companion object {
        fun inspect(snapshot: LegacySnapshot, manifest: HandoffManifest): LegacyHandoffAudit {
            fun denied(reason: String) = LegacyHandoffAudit(false, reason, snapshot.contentDigest, manifest.mappingDigest, emptyList())
            if (snapshot.contentDigest != manifest.expectedDigest) return denied("changed_snapshot")
            val links = manifest.links
            if (manifest.catalogRevision.isBlank() || links.isEmpty() || links.any {
                    it.origin.isBlank() || it.movieId.isBlank() || it.line < 0 || it.episode < 0 ||
                        it.target.episodeId == null || it.target.variantId == null } ||
                links.map { listOf(it.movieKey, it.line, it.episode) }.distinct().size != links.size ||
                links.map { it.target }.distinct().size != links.size) return denied("ambiguous_identity")
            val restored = mutableListOf<PlaybackSnapshot>()
            for (entry in snapshot.entries) {
                val analysis = LegacyEntryClassifier.classify(entry)
                if (analysis.status == LegacyEntryStatus.MALFORMED) return denied("malformed_entry")
                when (entry.store) {
                    LegacyStoreKind.PROGRESS -> {
                        val c = analysis.coordinates ?: return denied("unresolved_progress")
                        val link = links.singleOrNull { entry.key == "wp_${it.movieKey}" && it.line == c.lineIndex && it.episode == c.episodeIndex }
                            ?: return denied("unresolved_progress")
                        restored += PlaybackSnapshot(link.target.workId, link.target.episodeId, link.target.variantId,
                            "legacy-handoff:${snapshot.contentDigest}:${restored.size}", 0, c.positionMs,
                            c.durationMs.takeIf { it > 0 }, WatchState.IN_PROGRESS, snapshot.capturedAt)
                    }
                    LegacyStoreKind.BROWSE -> when (entry.key) {
                        "last_movie_id" -> if (links.none { it.movieKey == entry.value.stringOrNull() }) return denied("unresolved_browse")
                        "last_index" -> if (entry.value.type != "int") return denied("malformed_browse")
                        else -> return denied("unknown_browse")
                    }
                    LegacyStoreKind.FILTERS -> if (analysis.status != LegacyEntryStatus.RECOGNIZED) return denied("unknown_filter")
                    LegacyStoreKind.SETTINGS -> Unit // Immutable configuration preservation; no inferred identity.
                }
            }
            if (restored.isEmpty() || restored.map { it.workId }.distinct().size != restored.size) return denied("ambiguous_resume")
            return LegacyHandoffAudit(true, "exact_declared_fixture", snapshot.contentDigest, manifest.mappingDigest,
                Collections.unmodifiableList(restored))
        }
    }
}
