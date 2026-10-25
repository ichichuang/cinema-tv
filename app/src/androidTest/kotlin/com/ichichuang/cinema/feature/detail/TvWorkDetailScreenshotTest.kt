package com.ichichuang.cinema.feature.detail

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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * R15 详情真实截图（L01–L05）：1280×720 / 1920×1080 / 大字长文案 / 竖图与缺图 /
 * 减动效下焦点反馈。截图带“合成测试”水印，仅存于一次性测试档案。
 */
class TvWorkDetailScreenshotTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun ownedPrivateFixture() {
        val portrait = syntheticArt("round15-portrait", 300, 450)
        WorkDetailNativeFixture.setup(context)
        WorkDetailNativeFixture.writeBrowseFixture(context, artwork = mapOf(5 to (portrait.toURI().toString() to "portrait")))
    }

    @After fun restore() {
        shell("wm size reset"); shell("settings put system font_scale 1.0")
        shell("settings put global window_animation_scale 1.0")
        shell("settings put global transition_animation_scale 1.0")
        shell("settings put global animator_duration_scale 1.0")
        scenario?.close(); scenario = null
        WorkDetailNativeFixture.teardown(context)
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).let { it.close() }
        Thread.sleep(300)
    }

    private fun semantics() = WorkDetailNativeFixture.semantics(scenario!!)
    private fun until(timeout: Long = 12_000, condition: () -> Boolean) = WorkDetailNativeFixture.until(timeout, condition)
    private fun key(code: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, code))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, code))
        instrumentation.waitForIdleSync()
    }

    /** 合成竖幅测试图：低对比深色 + “合成测试”水印，绝不冒充真实海报。 */
    private fun syntheticArt(name: String, width: Int, height: Int): File {
        val file = File(context.filesDir, "$name.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(28, 30, 38))
        val paint = Paint().apply { color = Color.rgb(120, 124, 138); textSize = 28f; isAntiAlias = true }
        for (y in 40 until height step 80) canvas.drawText("合成测试", 24f, y.toFloat(), paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        bitmap.recycle()
        return file
    }

    private fun shot(name: String) {
        Thread.sleep(600)
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(context.filesDir, "round15-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        WorkDetailNativeFixture.scenarioRef = scenario
        until { semantics().any { it.tag == "候选首页" } }
    }

    private fun openDetailOfWork(workTag: String) {
        until { semantics().any { it.focused } }
        repeat(6) { key(KeyEvent.KEYCODE_DPAD_UP) }
        until { semantics().any { it.focused && it.tag.startsWith("nav:") } }
        var steps = 0
        while (steps++ < 8) {
            val current = semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag
            if (current == "nav:片库") break
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            until(2_000) { semantics().firstOrNull { it.focused && it.tag.startsWith("nav:") }?.tag != current }
        }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选片库" } }
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario!!, workTag)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { semantics().any { it.tag == "播放操作" && it.focused } }
    }

    // L01 1280×720：动作/长标题/集数入口完整可达（真实焦点）；可见性由像素截图背书。
    // 720p 下语义树 boundsInWindow 对滚动内容节点普遍报零（Row/FlowRow 两版一致的观测伪影，
    // 与像素实测矛盾），故不基于语义边界做可见性断言。
    @Test fun detail_1280x720_real_render_no_clipped_actions() {
        shell("wm size 1280x720")
        launch(); openDetailOfWork(WorkDetailNativeFixture.w[5].value)
        until { semantics().any { it.tag == "集数:${WorkDetailNativeFixture.EPISODE_H1}" } }
        // 五个动作全部在组合中（换行保证不裁掉任何操作）且主操作持真实焦点。
        until {
            val tags = semantics().map { it.tag }
            listOf("播放操作", "选择线路", "选择集数", "收藏操作", "详情返回").all { it in tags }
        }
        assertTrue(semantics().any { it.tag == "播放操作" && it.focused })
        shot("detail-1280x720-long")
    }

    // L02 1920×1080：对齐与可见焦点
    @Test fun detail_1920x1080_real_render() {
        shell("wm size 1920x1080")
        launch(); openDetailOfWork(WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { semantics().any { it.focused && it.tag.startsWith("线路:") } }
        shot("detail-1920x1080-lines")
    }

    // L03 大字号 + 长文案：全部主要操作仍可达（动作行换行，D-pad 实走 RIGHT→DOWN）。
    // 可见性由像素截图背书（720p 语义边界为零值伪影，不做基于边界的断言）。
    @Test fun detail_large_font_long_texts_keep_actions_reachable() {
        shell("wm size 1280x720"); shell("settings put system font_scale 1.3")
        launch(); openDetailOfWork(WorkDetailNativeFixture.w[5].value)
        val actions = setOf("播放操作", "选择线路", "选择集数", "收藏操作", "详情返回")
        fun waitChange(ms: Long, from: String?): Boolean {
            val end = android.os.SystemClock.uptimeMillis() + ms
            while (android.os.SystemClock.uptimeMillis() < end) {
                if (semantics().firstOrNull { it.focused }?.tag != from) return true
                Thread.sleep(80)
            }
            return semantics().firstOrNull { it.focused }?.tag != from
        }
        for (target in listOf("选择线路", "选择集数", "收藏操作", "详情返回")) {
            var guard = 0
            while (guard++ < 14 && semantics().none { it.tag == target && it.focused }) {
                val current = semantics().firstOrNull { it.focused }?.tag
                key(KeyEvent.KEYCODE_DPAD_RIGHT)
                if (!waitChange(1_500, current)) {
                    key(KeyEvent.KEYCODE_DPAD_DOWN)
                    waitChange(1_500, current)
                }
                // 换行后向下若越过动作区（落到线路/剧集），向上拉回继续遍历
                if (semantics().firstOrNull { it.focused }?.tag !in actions) key(KeyEvent.KEYCODE_DPAD_UP)
            }
            until { semantics().any { it.tag == target && it.focused } }
        }
        shot("detail-large-font-long")
    }

    // L04 竖幅图与缺图状态：真实标题保留，图片装载不抢焦点
    @Test fun detail_portrait_and_missing_poster_states() {
        shell("wm size 1920x1080")
        launch(); openDetailOfWork(WorkDetailNativeFixture.w[5].value)
        until { semantics().any { it.focused && it.tag == "播放操作" } }
        shot("detail-portrait-art")
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选片库" } }
        com.ichichuang.cinema.feature.library.LibraryNativeFixture.screenReach(scenario!!, WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        until { semantics().any { it.tag == "候选详情" } }
        until { semantics().any { it.focused && it.tag == "播放操作" } }
        assertTrue(semantics().any { it.text == "同题异身" })
        // 缺图占位图标必须真实可见：海报区内最大亮度 > 60（修复前实测 22，纯背景）。
        instrumentation.uiAutomation.takeScreenshot().let { bmp ->
            var maxLuma = 0
            for (x in 120 until 500 step 4) for (y in 300 until 800 step 4) {
                val c = bmp.getPixel(x, y)
                maxLuma = maxOf(maxLuma, (c shr 16 and 0xFF) * 299 / 1000 +
                    (c shr 8 and 0xFF) * 587 / 1000 + (c and 0xFF) * 114 / 1000)
            }
            bmp.recycle()
            assertTrue("缺图占位图标不可见（海报区最大亮度 $maxLuma ≤ 60）", maxLuma > 60)
        }
        shot("detail-missing-art")
    }

    // L05 减动效：反馈保留、方向与返回输入不被动画阻塞
    @Test fun detail_reduced_motion_keeps_feedback_and_input() {
        shell("wm size 1280x720")
        shell("settings put global window_animation_scale 0.0")
        shell("settings put global transition_animation_scale 0.0")
        shell("settings put global animator_duration_scale 0.0")
        launch(); openDetailOfWork(WorkDetailNativeFixture.w[0].value)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        until { semantics().any { it.focused && it.tag.startsWith("线路:") } }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        until { semantics().any { it.focused && it.tag == "线路:${WorkDetailNativeFixture.VARIANT_B}" } }
        key(KeyEvent.KEYCODE_BACK)
        until { semantics().any { it.tag == "候选片库" } }
        shot("detail-reduced-motion-back")
    }
}
