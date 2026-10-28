package com.ichichuang.cinema.core.design

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * 图标母版与原生映射的结构性核对（V01–V11 的 ART 部分）：
 * SVG 可编辑、24 单位网格、纯描边、无位图/外部引用/外部样式；
 * Kotlin ImageVector 与母版共用同一份路径数据；概念→中文语义唯一映射。
 * 原创性依结构证据（无第三方库路径拷贝来源、无裁图）与项目绘制记录，
 * 不宣称注册商标级核验。
 */
class TvOriginalIconsTest {
    private val concepts = mapOf(
        "home" to TvIconPaths.HOME, "library" to TvIconPaths.LIBRARY, "favorite" to TvIconPaths.FAVORITE,
        "history" to TvIconPaths.HISTORY, "settings" to TvIconPaths.SETTINGS, "play" to TvIconPaths.PLAY,
        "details" to TvIconPaths.DETAILS, "filter" to TvIconPaths.FILTER, "back" to TvIconPaths.BACK,
        "retry" to TvIconPaths.RETRY, "text" to TvIconPaths.TEXT, "columns" to TvIconPaths.COLUMNS)

    private fun repoRoot(): File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .firstOrNull { File(it, "design/icons/tv-home.svg").isFile }
        ?: error("找不到 design/icons 母版目录（user.dir=${System.getProperty("user.dir")}）")

    @Test fun twelve_concepts_have_editable_svg_masters_matching_native_paths() {
        val root = File(repoRoot(), "design/icons")
        val files = root.listFiles { f -> f.extension == "svg" }.orEmpty().map { it.name }.sorted()
        assertEquals(listOf("tv-back.svg", "tv-columns.svg", "tv-details.svg", "tv-favorite.svg", "tv-filter.svg",
            "tv-history.svg", "tv-home.svg", "tv-library.svg", "tv-play.svg", "tv-retry.svg", "tv-settings.svg",
            "tv-text.svg"), files)
        val names = mapOf("home" to "tv-home", "library" to "tv-library", "favorite" to "tv-favorite",
            "history" to "tv-history", "settings" to "tv-settings", "play" to "tv-play",
            "details" to "tv-details", "filter" to "tv-filter", "back" to "tv-back", "retry" to "tv-retry",
            "text" to "tv-text", "columns" to "tv-columns")
        concepts.forEach { (concept, pathData) ->
            val svg = File(root, "${names.getValue(concept)}.svg").readText()
            assertTrue("$concept 母版缺少项目绘制声明", svg.contains("cinema-tv 原创图标母版"))
            assertTrue("$concept 视图框不是 24 单位网格", svg.contains("""viewBox="0 0 24 24""""))
            assertEquals("$concept 母版路径数据与 TvIconPaths 不一致", pathData, Regex("""\s*d="([^"]+)"""").find(svg)!!.groupValues[1])
            assertTrue("$concept 使用填充而非统一描边语言", svg.contains("""fill="none""""))
        }
    }

    @Test fun svg_masters_have_no_raster_external_href_or_style_import() {
        val root = File(repoRoot(), "design/icons")
        concepts.keys.forEach { concept ->
            val file = File(root, "tv-$concept.svg")
            assertTrue("缺少 $concept 母版", file.isFile)
            val text = file.readText()
            assertFalse("$concept 嵌入位图", Regex("""<image|\.png|\.jpe?g|base64""", RegexOption.IGNORE_CASE).containsMatchIn(text))
            assertFalse("$concept 引用外部资源", Regex("""xlink:href|href="http|src="http""").containsMatchIn(text))
            assertFalse("$concept 引入外部样式", Regex("""<style|@import|<font""", RegexOption.IGNORE_CASE).containsMatchIn(text))
            assertEquals("$concept 母版应只有一条路径", 1, Regex("""<path\b""").findAll(text).count())
        }
    }

    @Test fun native_vectors_parse_from_the_same_path_data() {
        val vectors = mapOf("home" to TvOriginalIcons.Home, "library" to TvOriginalIcons.Library,
            "favorite" to TvOriginalIcons.Favorite, "history" to TvOriginalIcons.History,
            "settings" to TvOriginalIcons.Settings, "play" to TvOriginalIcons.Play,
            "details" to TvOriginalIcons.Details, "filter" to TvOriginalIcons.Filter,
            "back" to TvOriginalIcons.Back, "retry" to TvOriginalIcons.Retry,
            "text" to TvOriginalIcons.Text, "columns" to TvOriginalIcons.Columns)
        val parser = androidx.compose.ui.graphics.vector.PathParser()
        vectors.forEach { (concept, vector) ->
            assertEquals("$concept 视口宽不是 24", 24f, vector.viewportWidth)
            assertEquals("$concept 视口高不是 24", 24f, vector.viewportHeight)
            val nodes = parser.parsePathString(concepts[concept]!!).toNodes()
            assertTrue("$concept 路径数据解析后无节点", nodes.isNotEmpty())
            assertTrue("$concept 必须以移动指令开始", nodes.first() is androidx.compose.ui.graphics.vector.PathNode.MoveTo)
        }
        assertEquals("十二个概念必须是十二个不同矢量实例", 12, vectors.values.toSet().size)
    }

    @Test fun concept_to_chinese_accessibility_label_is_single_authority() {
        val labels = TvOriginalIcons.accessibilityLabels
        assertEquals(setOf("home", "library", "favorite", "history", "settings", "play", "details", "filter", "back", "retry", "text", "columns"), labels.keys)
        assertEquals(listOf("首页", "片库", "我的收藏", "最近观看", "设置", "播放", "查看详情", "筛选", "返回", "重试", "大字模式", "列数"),
            listOf("home", "library", "favorite", "history", "settings", "play", "details", "filter", "back", "retry", "text", "columns").map { labels[it] })
        labels.values.forEach { label -> assertTrue("标签必须是非空中文：$label", label.isNotBlank() && !label.matches(Regex("[A-Za-z]+"))) }
    }
}
