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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ichichuang.cinema.core.design.Gold
import com.ichichuang.cinema.core.design.PanelHi
import com.ichichuang.cinema.core.design.TextSub
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
    focusable: Boolean = true,
    focusRequester: FocusRequester? = null,
    onPlaced: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    var focused by remember { mutableStateOf(false) }
    val tileWidth: Dp
    val tileHeight: Dp
    // wide（首行）用 16:9 紧凑尺寸，保证 1280x720 逻辑宽度下动作按钮不被裁切（L01/L03）。
    if (landscape) { tileWidth = if (wide) 232.dp else 208.dp; tileHeight = if (wide) 130.dp else 117.dp }
    else { tileWidth = 120.dp; tileHeight = 180.dp }
    Column(modifier = modifier
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .let { if (focusable) it.tvFocus(cornerRadius = 12.dp) else it }
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
        Box(Modifier.width(tileWidth).height(tileHeight).clip(shape).background(PanelHi), contentAlignment = Alignment.Center) {
            // 占位在图下层：成功装载覆盖占位，失败或无图时保留占位与真实标题。
            androidx.compose.material3.Icon(com.ichichuang.cinema.core.design.TvOriginalIcons.Details,
                contentDescription = null, tint = TextSub.copy(alpha = 0.6f), modifier = Modifier.size(26.dp))
            if (artworkUri != null) {
                val fit = imageShape == HomeImageShape.PORTRAIT || imageShape == HomeImageShape.UNKNOWN
                AsyncImage(model = artworkUri, contentDescription = null,
                    contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            }
            if (progressFraction != null && progressText != null && progressFraction > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                    .height(3.dp).background(Gold))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium,
            maxLines = if (landscape) 1 else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(tileWidth))
        if (secondaryText != null) Text(secondaryText, color = TextSub, style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.width(tileWidth))
        if (progressText != null) Text(progressText, color = TextSub, style = MaterialTheme.typography.bodySmall,
            maxLines = 1, modifier = Modifier.width(tileWidth))
    }
}
