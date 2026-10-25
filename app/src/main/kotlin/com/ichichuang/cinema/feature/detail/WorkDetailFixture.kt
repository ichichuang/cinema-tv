package com.ichichuang.cinema.feature.detail

import android.content.Context
import com.ichichuang.cinema.domain.model.catalog.EpisodeId
import com.ichichuang.cinema.domain.model.catalog.SourceId
import com.ichichuang.cinema.domain.model.catalog.VariantId
import com.ichichuang.cinema.domain.model.catalog.WorkId
import org.json.JSONObject
import java.io.File

/**
 * R15 私有作品详情档案读取器：只读取授权原生测试写入的
 * noBackupFilesDir 私有文件，绝不内置演示目录、不访问网络。
 * 身份与标签全部为声明值；缺失保持缺失，不按序号或标签推断。
 */
internal class WorkDetailFixture(context: Context) {
    companion object { const val FILE = "round15-work-detail.json" }

    val works: List<DetailFixtureWork> = run {
        val file = File(context.noBackupFilesDir, FILE)
        if (!file.isFile) emptyList() else parse(JSONObject(file.readText()))
    }

    fun routes(workId: WorkId): List<DetailFixtureRoute> =
        works.firstOrNull { it.workId == workId }?.routes ?: emptyList()

    private fun parse(root: JSONObject): List<DetailFixtureWork> {
        val array = root.getJSONArray("works")
        val parsed = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val routes = item.getJSONArray("routes")
            DetailFixtureWork(WorkId(item.getString("workId")), (0 until routes.length()).map { r ->
                val route = routes.getJSONObject(r)
                val episodes = route.optJSONArray("episodes") ?: org.json.JSONArray()
                DetailFixtureRoute(
                    routeId = VariantId(route.getString("routeId")),
                    entryId = route.getString("entryId"),
                    sourceId = SourceId(route.getString("sourceId")),
                    sourceItemId = route.getString("itemId"),
                    label = route.getString("label"),
                    state = when (route.getString("state")) {
                        "AVAILABLE" -> DetailRouteState.AVAILABLE
                        "DISABLED" -> DetailRouteState.DISABLED
                        "UNREACHABLE" -> DetailRouteState.UNREACHABLE
                        "STALE" -> DetailRouteState.STALE
                        else -> error("未知线路状态：${route.getString("state")}")
                    },
                    episodes = (0 until episodes.length()).map { e ->
                        val episode = episodes.getJSONObject(e)
                        // org.json 的 optString 对 JSON null 会给出 "null" 字符串：显式 isNull 判空。
                        DetailFixtureEpisode(
                            EpisodeId(episode.getString("episodeId")),
                            if (episode.isNull("label")) null else episode.optString("label").takeIf { it.isNotBlank() },
                            if (episode.isNull("order")) null else episode.optInt("order"),
                            if (episode.isNull("uri")) null else episode.optString("uri").takeIf { it.isNotBlank() },
                        )
                    },
                    audioLanguages = route.optJSONArray("audio")?.let { a ->
                        (0 until a.length()).map { a.getString(it) }.toSet()
                    } ?: emptySet(),
                    observedAt = route.optLong("observedAt", 0),
                    ruleVersion = route.optString("ruleVersion", ""),
                )
            })
        }
        require(parsed.map { it.workId }.distinct().size == parsed.size) { "详情档案作品身份重复" }
        parsed.forEach { work ->
            require(work.routes.map { it.routeId }.distinct().size == work.routes.size) { "作品线路身份重复" }
            work.routes.forEach { route ->
                require(route.episodes.map { it.episodeId }.distinct().size == route.episodes.size) { "线路剧集身份重复" }
            }
        }
        return parsed
    }
}
