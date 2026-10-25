# cinema-tv 原创图标母版（第一组，共 10 枚）

本目录是电视界面第一批原创图标的**可编辑矢量母版**，与
`app/src/main/kotlin/com/ichichuang/cinema/core/design/TvOriginalIcons.kt`
中的 `TvIconPaths` 逐字符一致（由 JVM 测试 `TvOriginalIconsTest` 双向核对）。

## 绘制规则

- 画布 `viewBox="0 0 24 24"`，全部坐标落在 24 单位网格内；
- `fill="none"`、`stroke="currentColor"`、`stroke-width="2"`、圆头端点与拐角；
- 每枚图标一条 `<path>`，不嵌套位图（`<image>`）、外部引用（`href`/`xlink:href`）、
  外部样式（`<style>`/`import`）或字体；
- 路径数据由本项目绘制，未采用第三方图标库（Material、Netflix 等）的路径数据，
  也不从参考效果图裁图；本组图标仅代表“第一组电视导航与动作概念”，
  不声称完成全部应用图标盘点（完整 T02 仍开放）。

## 概念清单与光学说明

| 文件 | 概念 | 中文语义 | 光学说明 |
| --- | --- | --- | --- |
| `tv-home.svg` | home | 首页 | 山墙屋体与居中门洞；无烟囱，保持低噪轮廓 |
| `tv-library.svg` | library | 片库 | 胶片框：外框+两条纵分隔，外侧齿孔刻度呼应胶片 |
| `tv-favorite.svg` | favorite | 我的收藏 | 不对称双瓣心形，底尖略圆润 |
| `tv-history.svg` | history | 最近观看 | 表盘逆时针箭头，指针短促，表达“回到过去” |
| `tv-settings.svg` | settings | 设置 | 三档滑杆：表达可调节而非机械齿轮 |
| `tv-play.svg` | play | 播放 | 圆角三角；几何中心左移 0.4 单位补偿视觉重量 |
| `tv-details.svg` | details | 查看详情 | 放大镜内置两行资料线 |
| `tv-filter.svg` | filter | 筛选 | 漏斗三段折线，出口略窄于入口 |
| `tv-back.svg` | back | 返回 | 单折线粗箭头（`>` 形），无尾线 |
| `tv-retry.svg` | retry | 重试 | 近整圈弧线+开口箭头，缺口留在右上 |

## 原生映射

`TvOriginalIcons` 以 `ImageVector` 呈现同一份路径数据（描边 2f、圆头、无填充），
颜色由调用点 `tint` 决定。`TvOriginalIcons.accessibilityLabels` 是概念 → 中文名称
的唯一映射，供调用点核对图标语义与文案一致；图标本身绘制为装饰（contentDescription
为空），可访问名称由相邻文本与 onClick 标签承载。
