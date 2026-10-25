package com.ichichuang.cinema.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ichichuang.cinema.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * R14 视觉证据：真实 AVD 渲染截图（1280x720 / 1920x1080 / 大字长标题 /
 * 2000 年前年代 + 竖版与缺图 / 减动效 / 部分失败状态），全部来自实际 Android 屏幕。
 * 截图存放在应用文件目录，随后由证据脚本 adb pull 导出。
 */
class TvLibraryScreenshotTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var wide = false

    @Before fun ownedPrivateFixture() = LibraryNativeFixture.setup(context)

    @After fun restoreEnvironment() {
        scenario?.close(); scenario = null
        LibraryNativeFixture.teardown(context)
        instrumentation.uiAutomation.executeShellCommand("wm size reset").close()
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
        instrumentation.uiAutomation.executeShellCommand("settings put global window_animation_scale 1.0").close()
        instrumentation.uiAutomation.executeShellCommand("settings put global animator_duration_scale 1.0").close()
    }

    private fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).close() }

    private fun shot(name: String) {
        Thread.sleep(600)
        val file = File(context.filesDir, "round14-$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        assertTrue("截图必须落盘：$name", file.length() > 0)
    }

    /** 合成测试图：带“合成测试”水印，仅测试代码生成，绝不进入生产目录。 */
    private fun syntheticArt(name: String, width: Int, height: Int): String {
        val file = File(context.filesDir, "round14-$name.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(0x17, 0x1E, 0x27))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0xF5, 0xC6, 0x93); textSize = width / 5f; textAlign = Paint.Align.CENTER }
            drawText("合成测试", width / 2f, height / 2f, paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.toURI().toString()
    }

    private fun key(code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }

    private fun semantics() = LibraryNativeFixture.semantics(scenario!!)
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) = LibraryNativeFixture.until(timeout, condition)
    private fun focus(tag: String) = until { semantics().any { it.tag == tag && it.focused } }
    private fun library(action: (LibraryRuntime) -> Unit) { scenario!!.onActivity { action(requireNotNull(it.libraryProbe)) } }

    private fun navTo(label: String) {
        until { semantics().any { it.focused } }
        repeat(6) { key(KeyEvent.KEYCODE_DPAD_UP) }
        until { semantics().any { it.focused && it.tag.startsWith("nav:") } }
        val order = com.ichichuang.cinema.feature.home.visual.VisualHomeRuntime.NAV_ITEMS
        var steps = 0
        while (steps < 8) {
            val current = semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag
            if (current == "nav:$label") break
            val currentIndex = order.indexOf(current?.removePrefix("nav:"))
            check(currentIndex >= 0) { "导航行未聚焦：$current" }
            val targetIndex = order.indexOf(label)
            key(if (targetIndex > currentIndex) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            until(2_000) { semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag != current }
            steps++
        }
        focus("nav:$label")
    }

    private fun panelSelect(keyTag: String) {
        var guard = 0
        while (guard++ < 40) {
            val actual = semantics().firstOrNull { it.focused }?.tag
            if (actual == keyTag) break
            var zones: List<List<String>> = emptyList()
            library { zones = it.panelZones().map { it.second } }
            val currentZone = zones.indexOfFirst { actual in it }
            val targetZone = zones.indexOfFirst { keyTag in it }
            val code = when {
                currentZone < 0 -> KeyEvent.KEYCODE_DPAD_DOWN
                currentZone == targetZone ->
                    if (zones[targetZone].indexOf(keyTag) > zones[currentZone].indexOf(actual))
                        KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                targetZone > currentZone -> KeyEvent.KEYCODE_DPAD_DOWN
                else -> KeyEvent.KEYCODE_DPAD_UP
            }
            key(code)
            until(2_000) { semantics().firstOrNull { it.focused }?.tag != actual }
        }
        focus(keyTag); key(KeyEvent.KEYCODE_DPAD_CENTER)
    }

    private fun launchLibrary() {
        shell("wm size ${if (wide) "1920x1080" else "1280x720"}")
        Thread.sleep(400)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        LibraryNativeFixture.scenarioRef = scenario
        until { semantics().any { it.tag == "候选首页" } }
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 105 }
        // 等待网格条目真实合成再截图/继续，避免取帧竞态。
        until { semantics().any { it.tag == LibraryNativeFixture.ids[0].value } }
    }

    @Test fun library_1280x720_real_render() {
        launchLibrary()
        shot("library-1280x720")
    }

    @Test fun library_1920x1080_real_render() {
        wide = true
        launchLibrary()
        shot("library-1920x1080")
    }

    @Test fun library_large_font_and_long_title() {
        launchLibrary()
        shell("settings put system font_scale 1.3")
        Thread.sleep(800)
        // 字体缩放触发配置变更重建（回到首页根）：重新进入片库再走到长标题处。
        until { semantics().any { it.tag == "候选首页" } }
        navTo("片库"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        until { var size = -1; library { size = it.display.items.size }; size == 105 }
        until { semantics().any { it.focused } }
        LibraryNativeFixture.screenReach(scenario!!, LibraryNativeFixture.ids[87].value)
        shot("library-large-font")
    }

    @Test fun library_decade_1980s_with_portrait_and_missing_art() {
        // 带真实合成图（竖版 + 横版）与缺图占位的年代筛选画面。
        val works = JSONArray()
        LibraryNativeFixture.ids.forEachIndexed { i, id ->
            works.put(JSONObject()
                .put("id", id.value)
                .put("title", if (i == 87) "很长的合成作品标题第八十八号用于长文案核对" else "合成作品${i + 1}")
                .put("source", if (i == 104) "disabled-source" else "test-source")
                .put("year", LibraryNativeFixture.workYear(i))
                .put("countries", JSONArray(listOf(if (i == 101) "US" else "JP")))
                .put("genres", JSONArray(listOf(if (i == 102) "drama" else "horror")))
                .put("kind", if (i == 103) "OTHER" else "MOVIE")
                .put("languages", JSONArray(listOf("ja")))
                .put("favorite", false).put("history", false)
                .put("artwork", when (i) {
                    0 -> syntheticArt("landscape-0", 320, 180)
                    1 -> syntheticArt("portrait-1", 120, 180)
                    else -> JSONObject.NULL
                })
                .put("artworkShape", when (i) { 1 -> "portrait"; 0 -> "landscape"; else -> JSONObject.NULL }))
        }
        LibraryNativeFixture.writeFixture(context, works)
        launchLibrary()
        LibraryNativeFixture.screenReach(scenario!!, "筛选"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "筛选面板" } }
        panelSelect("year-mode-DECADE")
        panelSelect("year-decade-minus")
        panelSelect("panel-apply")
        until { var size = -1; library { size = it.display.items.size }; size == LibraryNativeFixture.decadeIndices.count() }
        // 等待条目真实合成（非零边界）再截图，避免取帧竞态。
        until { semantics().any { it.tag == LibraryNativeFixture.ids[30].value } }
        shot("library-decade-1980s")
    }

    @Test fun library_reduced_motion_keeps_focus_feedback() {
        launchLibrary()
        shell("settings put global window_animation_scale 0.0")
        shell("settings put global animator_duration_scale 0.0")
        shell("settings put global transition_animation_scale 0.0")
        Thread.sleep(400)
        until { semantics().any { it.focused } }
        shot("library-reduced-motion")
        // 减动效下焦点语义仍可辨（唯一焦点 + 焦点节点存在）。
        assertEquals(1, semantics().count { it.focused })
    }

    @Test fun library_partial_state_with_failed_source_visible() {
        launchLibrary()
        // 默认即含失败来源的如实部分覆盖（broken-source）：状态条与继续核对动作。
        until { semantics().any { it.tag == "片库状态" && it.text.contains("1 个失败") } }
        shot("library-partial-failed")
    }
}
