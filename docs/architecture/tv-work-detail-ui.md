# TV 作品详情（R15 守卫切片）架构说明

> 状态：R15 候选实现，仅在既有七级探针配对（storageArm64 + playback + browse + personal + home + library + detail）与一次性私有授权档案全部就位时可达。普通 Debug/Release 路径不受影响：`BuildConfig.DETAIL_PROBE` 默认 `false`，详情层回落到 R12 既有动作列表详情。

## 分层

```
MainActivity（探针装配 + 窗口回调按键分发：detail → home → browse）
└─ CinemaNavHost（探针重载，目的地“候选详情”）
   └─ TvWorkDetailScreen（feature/detail，纯渲染 + 焦点请求）
      └─ WorkDetailRuntime（焦点/弹层/刷新令牌/派发/一次性上下文档案）
         ├─ DetailSelectionController（纯状态机，JVM 可测）
         ├─ DetailProjection（纯投影，JVM 可测：只读、无副作用、无网络）
         ├─ WorkDetailFixture（round15-work-detail.json 私有档案读取）
         ├─ PersonalLibraryRuntime（既有 R12 单写者：favorite/prepareTarget）
         │   └─ RoomPlaybackProbe.select（既有 R06 守卫 Media3，精确三元组）
         └─ BrowseProbeRuntime（既有 R11 子层导航：enter/detail/back/来源恢复）
```

## 权威与数据流

- **作品身份与事实**：只读自 R08 严格事实（`StrictWorkFacts` 的 `FieldEvidence`）。未知渲染“年份未知”，冲突渲染“证据冲突，尚未裁决”，绝不取任一方值；线路音轨声明不参与作品语言事实（P05）。
- **线路/剧集证据**：私有档案 `round15-work-detail.json` 声明每部作品的线路（源条目 + 播放版本）与剧集（身份/来源标签/声明序号/本地合成媒体 uri）。缺失保持缺失：无标签即“未提供集数名”，无序号不造“第 N 集”，无清单不造总数（E05/P09）。`BrowseProbeFixture` 增加可选 `yearEvidence/yearClaims/languageClaims` 字段（旧档案缺省＝全部 Confirmed，R11–R14 行为零变化）。
- **个人记录**：全部经既有 `PersonalLibraryRuntime`——收藏走 R12 单写者事务（幂等目标值 + 丢回执重试 + 撤销回执修订号）；播放走 `prepareTarget(workId, episodeId, variantId)`：以精确三元组调用 `RoomPlaybackProbe.select(autoPlay=false)`，恢复位置只映射完全相同目标（`playback_snapshots` 按三元组精确回查），绝不携带其他版本检查点（E07/A01）。
- **进入详情/聚焦/换线/取消弹层/图片装载**：零用户记录写入（A03）。仅显式 OK 走收藏/播放；播放派发时点再校验线路可用与身份（A04/A05），失败给可行动中文状态，绝不自动回退其他线路。

## 焦点与弹层

- 焦点（`focusedKey`）与选择（`selectedRouteId/selectedEpisodeId`）永远分立；选择只按稳定线路/剧集身份，绝不同意合并同名线路（E01）。
- 三区焦点序：动作行（播放/选线/选集/收藏/返回）→ 线路行 → 剧集列；真实 D-pad 由窗口回调层 `WorkDetailRuntime.key` 统一处理（与 R13/R14 同族：held 去重、`repeatCount>0` 不先执行短按、UP 成对清理）。
- 动作行为 `FlowRow`（与线路 chips 同语言）：窄面板（720p）或大字号（1.3×）下自动换到第二行，五个操作全部真实可见，绝不被推出屏外（视觉复核两轮像素证据：修复前 720p 下"选择集数/收藏/返回"整体在屏外）。右列为 `verticalScroll`：下方线路/剧集段超出视口属正常滚动，D-pad 走焦自动滚入。
- 缺图兜底：占位 `Details` 图标叠画在 `PosterImage` 之上（`PosterImage` 自带不透明 `Panel` 背景，图标画在其下会被完全遮盖——实测 0 亮像素）；`WorkDetailRuntime.reproject` 在消费前把 org.json 对显式 JSON null 给出的 `"null"` 字面量归一化为缺图（BrowseProbeFixture 的 `optString` 怪癖，仅在详情层归一化，R11–R14 行为不变）。
- 弹层（线路单/集数单）：打开后背景一律不可聚焦（陷阱），Back 精确恢复打开者；迟到焦点恢复请求被任何用户输入撤销（N04，`inputEpoch`）。
- 刷新：`beginRefresh/completeRefresh` 令牌管线——旧令牌迟到即丢弃；新令牌应用也绝不覆盖用户选择/焦点，仅在所选线路消失时回退到“顶替位置”的稳定线路（N07）。

## 测试观测注意（原生层）

- 720p 下未合并语义树的 `boundsInWindow` 对该屏滚动内容节点普遍报 `Rect.fromLTRB(0,0,0,0)`（Row 与 FlowRow 两版一致，与像素实测矛盾）——**不要基于语义边界做可见性/裁切断言**；可见性用像素截图断言（见 L04 图标亮度断言）或人工视觉复核，可达性用真实 D-pad 走焦断言。
- 组合 settle 竞态：打开详情后线路/剧集节点可能晚一两帧进入语义树；`applyUserQuery` 挂钩会异步重派发一次焦点（token 重装后的首结果焦点）。断言前等待 settle 信号（标签齐全 / 运行时 `focused != null`），不要瞬时断言。

## 返回与来源

- 详情 Back = 既有 `BrowseProbeRuntime.back`（R11）：逐层返回（播放器→详情→来源），来源恢复由 R14 片库 `childReturned()` 与 R11 快照权威完成（查询/锚点/焦点）。
- 已提交（作品, 线路, 剧集）上下文持久化于一次性档案 `round15-detail-context.json`（仅 UI 上下文，非用户数据；Activity 重建/进程重启后重开详情时恢复）；未提交弹层状态从不持久化。

## 新增文件（全部 `feature/detail`，R13 视觉令牌复用）

| 文件 | 职责 |
| --- | --- |
| `DetailUiState.kt` | 不可变显示契约（事实状态、线路/剧集 UI、主操作） |
| `DetailProjection.kt` | 纯投影 + 默认选择（精确恢复版本优先 → 首个可核实线路） |
| `DetailSelectionController.kt` | 纯状态机：焦点/弹层/选择/刷新令牌 |
| `WorkDetailFixture.kt` | 私有详情档案读取（身份查重、缺失保缺） |
| `WorkDetailRuntime.kt` | Android 壳：按键、派发、上下文档案、测试钩子 |
| `TvWorkDetailScreen.kt` | Compose 渲染：海报缺图兜底、焦点≠选中双视觉、弹层 |

修改文件：`MainActivity`（装配/按键链）、`CinemaNavHost`（候选详情渲染分流）、`BrowseProbeRuntime.enterPlayerPrepared`、`PersonalLibraryRuntime.prepareTarget`、`BrowseProbeFixture`（可选证据字段）、`app/build.gradle.kts`（`cinema.detailProbe` 门禁：链全部既有探针、拒绝 Release/bundle/publish/install/签名）。

## 边界

- 不新增 Room 写入者、不建目录仓储、不替换播放器；本轮不含完整播放控制 UI、全局输入重构或普通路径激活。
- 播放层仍为 R12 `PersonalLibraryPlayer`（播放/暂停/重试语义不变）。
