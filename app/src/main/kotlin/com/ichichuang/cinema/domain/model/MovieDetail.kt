package com.ichichuang.cinema.domain.model

/** 一集（或一条播放地址） */
data class Episode(
    val name: String,
    val url: String,
)

/** 一条播放线路（片源站常有多条线路互为备份，某条挂了可以换） */
data class PlayLine(
    val name: String,
    val episodes: List<Episode>,
)

/**
 * 影片详情。
 *
 * 列表页为了速度和流量只拿简表；用户焦点停在某部片上时才来取这个
 * （简介 + 播放线路）。播放器需要的地址也在这里，阶段 2 直接用。
 */
data class MovieDetail(
    val movie: Movie,
    val synopsis: String = "",
    val area: String = "",
    val lang: String = "",
    val lines: List<PlayLine> = emptyList(),
) {
    /** 最多那一条线路的集数（用来显示"共 24 集"） */
    val episodeCount: Int get() = lines.maxOfOrNull { it.episodes.size } ?: 0
}
