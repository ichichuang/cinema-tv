package com.ichichuang.cinema.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.IconSize
import com.ichichuang.cinema.core.design.PanelHi
import com.ichichuang.cinema.core.design.Radii
import com.ichichuang.cinema.core.design.Sizes
import com.ichichuang.cinema.core.design.Spacing
import com.ichichuang.cinema.core.design.TextSub
import com.ichichuang.cinema.core.design.TileSize
import com.ichichuang.cinema.core.focus.tvFocus
import com.ichichuang.cinema.feature.home.visual.HomeImageShape

/**
 * 海报/画块瓦片：真实数据卡片的原生呈现。
 * - 竖版画只在竖版容器裁切；竖图进横版容器一律 Fit 收纳，不拉伸、不裁成假宽画（L04）；
 * - 无图/加载失败显示可聚焦占位：面板底 + 详情图标 + 真实标题仍在（H10）；
 * - 进度条只画已确认检查点的位置/时长比例；未知时长不画总量；
 * - 图片完成回调绝不请求焦点（N06），焦点仅由运行时状态驱动。
 */
@Composable
internal fun TvPosterTile(
    title: String,
    artworkUri: String?,
    imageShape: HomeImageShape,
    modifier: Modifier = Modifier,
    tag: String = title,
    secondaryText: String? = null,
    progressText: String? = null,
    progressFraction: Float? = null,
    landscape: Boolean = true,
    wide: Boolean = false,
    /** R14 片库网格：瓦片填满网格列宽并按画幅定高；默认关闭保持 R13 行式固定尺寸。 */
    fillWidth: Boolean = false,
    focusable: Boolean = true,
    focusRequester: FocusRequester? = null,
    onPlaced: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Radii.md)
    var focused by remember { mutableStateOf(false) }
    val tileWidth: Dp
    val tileHeight: Dp
    // wide（首行）用 16:9 紧凑尺寸，保证 1280x720 逻辑宽度下动作按钮不被裁切（L01/L03）。
    if (landscape) {
        tileWidth = if (wide) TileSize.wideWidth else TileSize.landscapeWidth
        tileHeight = if (wide) TileSize.wideHeight else TileSize.landscapeHeight
    } else {
        tileWidth = TileSize.portraitWidth
        tileHeight = TileSize.portraitHeight
    }
    val imageModifier = if (fillWidth) Modifier.fillMaxWidth()
        .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
    else Modifier.width(tileWidth).height(tileHeight)
    val textWidthModifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(tileWidth)
    Column(modifier = modifier
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .let { if (focusable) it.tvFocus(cornerRadius = Radii.lg) else it }
        .onFocusChanged { focused = it.isFocused; onFocusChanged?.invoke(it.isFocused) }
        .semantics {
            contentDescription = buildString {
                append(title)
                if (imageShape == HomeImageShape.PORTRAIT) append("，竖版画")
                secondaryText?.let { append("，"); append(it) }
                progressText?.let { append("，"); append(it) }
            }
            if (onClick != null) onClick(label = title) { onClick(); true }
        }
        .then(if (focusable) Modifier.focusable() else Modifier)
        .onGloballyPositioned { onPlaced?.invoke() }
        .testTag(tag)) {
        Box(imageModifier.clip(shape).background(PanelHi), contentAlignment = Alignment.Center) {
            // 占位在图下层：成功装载覆盖占位，失败或无图时保留占位与真实标题。
            androidx.compose.material3.Icon(com.ichichuang.cinema.core.design.TvOriginalIcons.Details,
                contentDescription = null, tint = TextSub.copy(alpha = 0.6f), modifier = Modifier.size(IconSize.placeholder))
            if (artworkUri != null) {
                val fit = imageShape == HomeImageShape.PORTRAIT || imageShape == HomeImageShape.UNKNOWN
                AsyncImage(model = artworkUri, contentDescription = null,
                    contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            }
            if (progressFraction != null && progressText != null && progressFraction > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                    .height(Sizes.indicator).background(Gold))
            }
        }
        Spacer(Modifier.height(Spacing.n6))
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium,
            maxLines = if (landscape) 1 else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = textWidthModifier)
        if (secondaryText != null) Text(secondaryText, color = TextSub, style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = textWidthModifier)
        if (progressText != null) Text(progressText, color = TextSub, style = MaterialTheme.typography.bodySmall,
            maxLines = 1, modifier = textWidthModifier)
    }
}
