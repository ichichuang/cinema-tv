# 原生电视视觉系统与功能化首页（R13 架构说明）

日期：2026-10-25。范围：T02 视觉系统第一组切片 + T10 功能化首页／顶层导航切片；
仅在既有 R11/R12 授权测试路径内生效，普通 Debug／Release 不变。

## 触达面与门控

- 新增编译期探针 `cinema.homeVisualProbe` → `BuildConfig.HOME_VISUAL_PROBE`，
  复用既有链式授权：必须同时启用 ARM64 存储、Room 播放、浏览恢复、个人记录四个探针，
  且任务图严格限定 `:app:assembleDebug` + `:app:assembleDebugAndroidTest`；
  任何 Release／bundle／发布／签名／安装任务被拒绝（G01/G02/G06）。
- 运行期新增私有授权令牌 `round13-home-visual.authorization`
  （`cinema-round13-disposable-home-visual`，noBackupFilesDir），缺令牌在打开数据库前失败关闭（G04）。
- 原生测试新增配对参数 `-e cinema.home round13`；其余 round06/07/11/12 参数与授权文件不变。

## 视觉系统（T02 第一组）

- **单一 token 权威**：继续以 `core/design/Color.kt`（Ink/Panel/PanelHi/Gold/TextMain/TextSub/TextBody）、
  `Motion.kt`、`TvMetrics.kt` 与 `core/focus/TvFocus.kt` 为唯一设计令牌来源；
  本轮不新增并列颜色／动效常量，首页组件只消费既有令牌与 `MaterialTheme`（Theme.kt 接线）。
- **原创图标（10 概念）**：可编辑母版 `design/icons/tv-*.svg`（24 单位网格、fill=none、
  stroke=currentColor、宽 2、圆头端点）与 `core/design/TvOriginalIcons.kt` 的 `ImageVector`
  共用同一份路径数据（`TvIconPaths`），JVM 测试 `TvOriginalIconsTest` 双向核对，
  并检查无位图／外部 href／外部样式／多路径。概念清单：首页、片库、我的收藏、最近观看、
  设置、播放、查看详情、筛选、返回、重试；`TvOriginalIcons.accessibilityLabels` 为概念 →
  中文名称的唯一映射（调用点核对图标语义与文案一致；图标绘制为装饰，可访问名称由相邻
  文本与 onClick 标签承载）。本组仅为第一组，完整图标盘点仍开放。
- **可复用组件**（`core/ui/`）：`TvNavTile`（选中=金色下划线、焦点=tvFocus 描边，两种视觉独立，
  N10/V12）、`TvPrimaryButton`（胶囊、禁用不可聚焦）、`TvPosterTile`（竖图进横容器一律 Fit
  收纳、缺图占位保留真实标题、进度条只画已确认检查点、图片回调不抢焦点）、
  `TvStatusMessage`（如实状态 + 可选动作）、`TvLoadingTile`（骨架、无无限动画、无虚构内容）。

## 首页投影与数据权威

- home visual 模块的 `HomeDisplayState.kt`（feature 包内）：`HomeContentStatus`（UNCONFIGURED/LOADING/READY/
  PARTIAL/OFFLINE/FAILED）、`HomeImageShape`、`HomeDisplayCard`、`HomeDisplayState` 为
  **只读 UI 投影**，不是新的权威影片记录。
- `HomeProjection`（纯函数，JVM 可测）：状态优先级 FAILED > LOADING > UNCONFIGURED > OFFLINE >
  PARTIAL > READY；`totalKnown` 只在已配置且查询声明完整时采用，部分覆盖／未配置一律 null。
- `VisualHomeRuntime`：与 `BrowseProbeRuntime` 同一 `withOwnedProfile` 租约内构造，
  共享其 `personal`（同一 PersonalLibraryRuntime 实例，无第二写入者）与候选目录 fixture；
  通过 `browse.onPersonalChanged` 在个人记录变化时重投影。网络状态只读
  `ConnectivityManager`（注册 default callback），不做任何网络请求。
- 持久化：只写自身上下文键 `home:<queryId>`（`HomeSnapshotStore`，
  browse_snapshots 表 v5 既有结构，无 schema 变更）；浏览活动指针仍由浏览运行时独占。

## 导航与焦点

- 候选 NavHost（`CinemaNavHost` 内部重载）在 `home != null` 时以“候选首页”为根，
  新增“候选设置”根级目的地；详情／播放器仍由浏览运行时的 returnChain 驱动、叠于任何根画面。
  home 为 null（R12 及更早配对）时导航图与 R12 完全一致。
- 五个中文根目的地：首页／片库／我的收藏／最近观看／设置；片库／收藏／历史复用既有
  `BrowseTopLevel` 切换与持久化，设置切片（大字模式／筛选栏／列数／刷新目录／重载首页）
  每项都有真实效果，无死胡同按钮。
- 首页焦点复用 R11 令牌管线（`BrowseRestoreCoordinator`/`BrowseCheckpointCoordinator` 独立实例）：
  每个平台按键先同步作废待恢复焦点；确认键 held-set 去重防双导航；
  图片装载／迟到投递不请求焦点；`BrowseTopLevel` 追加 `HOME` 仅为首页快照路由校验，
  浏览运行时从不切换到它。

## R12-M1 Minor 关闭

`PersonalLibraryTestHooks.lostReceiptAcknowledgements` 提供一次性“仓储已提交、UI 回执丢弃”注入：
注入点在 PersonalLibraryRuntime 收藏动作的确认边界（仓储真实 Committed 之后、UI 消息之前），
同时延续待定意图 —— 修复了原缺陷（回执丢失后意图被清除，重试读到已提交状态而反向切换）。
原生用例 `VisualHomeUiNativeTest.ack_lost_after_commit_ui_retry_has_true_state` 在真实 UI 上
验证：失败文案如实、数据库已有真实收藏、用户重试经幂等目标值收敛为唯一持久状态、
成功文案准确、焦点稳定。默认计数 0，既有 R12 测试行为不变（仓储级回执丢失幂等仍由 R12
既有 `PersonalLibraryNativeTest` 方法覆盖）。

## 明确未做

完整 T02 图标盘点、完整 T10 片库严格筛选面板／详情／播放器／设置重构、T11 全局遥控与动效、
诊断代码删除、生产路由激活、真实 CMS／海报、物理电视（API24/ARMv7）与发布验收。
