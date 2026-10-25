package com.ichichuang.cinema.feature.home.visual

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.ichichuang.cinema.MainActivity

/** 截图测试的语义树提取助手（与 UI 测试同源，不引入额外测试依赖）。 */
internal object VisualHomeScreenshotDump {
    fun tags(view: View): List<String> = flatten(view).map { node ->
        node.config.getOrElse(SemanticsProperties.TestTag) { "" }
    }.filter { it.isNotBlank() }
    private fun flatten(view: View): List<SemanticsNode> {
        fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
        if (view is ViewRootForTest) return walk(view.semanticsOwner.unmergedRootSemanticsNode)
        return if (view is ViewGroup) (0 until view.childCount).flatMap { flatten(view.getChildAt(it)) } else emptyList()
    }
}

internal object VisualHomeScreenshotTexts {
    fun of(activity: MainActivity): List<String> = VisualHomeScreenshotDump2.textsOf(activity.window.decorView)
}

private object VisualHomeScreenshotDump2 {
    fun textsOf(view: View): List<String> {
        fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
        fun flattenView(v: View): List<SemanticsNode> = when (v) {
            is ViewRootForTest -> walk(v.semanticsOwner.unmergedRootSemanticsNode)
            is ViewGroup -> (0 until v.childCount).flatMap { flattenView(v.getChildAt(it)) }
            else -> emptyList()
        }
        return flattenView(view).mapNotNull { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { it.text }.takeIf { it.isNotBlank() }
        }
    }
}
