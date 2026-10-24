package com.ichichuang.cinema.feature.player

import com.ichichuang.cinema.data.userdata.PlaybackTarget
import com.ichichuang.cinema.domain.model.MovieDetail

/** Only instrumentation repositories supply these explicit identities; never infer them from URLs. */
internal interface PlaybackProbeCatalog { val probeMedia: List<ProbeMedia> }
internal interface HandoffPlaybackProbeCatalog : PlaybackProbeCatalog {
    val handoffManifest: com.ichichuang.cinema.data.userdata.HandoffManifest
}
internal data class ProbeMedia(val detail: MovieDetail, val lineIndex: Int, val episodeIndex: Int, val target: PlaybackTarget)
internal object ProbeIdentity {
    fun resolve(catalog: PlaybackProbeCatalog, detail: MovieDetail, line: Int, episode: Int): ProbeMedia? {
        val media = catalog.probeMedia
        if (detail.movie.id.isBlank() || detail.movie.origin.isBlank() || media.isEmpty()) return null
        if (media.any { it.target.episodeId == null || it.target.variantId == null ||
                it.detail.movie.id.isBlank() || it.detail.movie.origin.isBlank() ||
                it.detail.lines.getOrNull(it.lineIndex)?.episodes?.getOrNull(it.episodeIndex) == null }) return null
        if (media.map { it.target }.distinct().size != media.size) return null
        if (media.map { listOf(it.detail.movie.origin, it.detail.movie.id, it.lineIndex, it.episodeIndex) }.distinct().size != media.size) return null
        return media.singleOrNull { it.detail == detail && it.lineIndex == line && it.episodeIndex == episode }
    }
}

internal object ProbeMediaFile {
    fun requirePrivate(root: java.io.File, rawUri: String): java.io.File {
        val uri = java.net.URI(rawUri)
        require(uri.scheme == "file" && uri.authority == null && uri.query == null && uri.fragment == null)
        val file = java.io.File(uri).canonicalFile
        val prefix = root.canonicalPath + java.io.File.separator
        require(file.path.startsWith(prefix) && file.isFile) { "仅允许私有合成测试媒体" }
        return file
    }
}
