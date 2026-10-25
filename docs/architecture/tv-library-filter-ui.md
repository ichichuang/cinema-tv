# 原生片库与严格筛选界面（R14 架构说明）

日期：2026-10-25。范围：T10 片库/筛选 UI 有界切片；仅在既有 R11–R13 授权测试
路径内生效，普通 Debug／Release 不变。

## 触达面与门控

- 新增编译期探针 `cinema.libraryFilterProbe` → `BuildConfig.LIBRARY_FILTER_PROBE`
  （默认 false、Release 恒 false），链式要求既有 ARM64 存储、Room 播放、浏览恢复、
  个人记录、首页视觉五个探针全部开启，且任务图严格限定
  `:app:assembleDebug` + `:app:assembleDebugAndroidTest`；Release／bundle／发布／
  签名／安装任务一律 GradleException。
- 运行期私有授权令牌 `round14-library-filter.authorization`
  （`cinema-round14-disposable-library-filter`，noBackupFilesDir），缺令牌在数据库
  打开前失败关闭。原生测试配对参数 `-e cinema.library round14`；探针关闭时
  “片库”导航逐字保持 R13 浏览占位路径（`VisualHomeRuntime.openTopDestination` 分支）。

## 数据权威与查询桥接

- 片库数据一律经既有 R09 `QueryCoordinator`（`CatalogRepository` 权威实现：
  预算、来源独立游标、会话代次、取消保护、部分覆盖）与 R10 `StrictFilterEvaluator`
  严格评估；UI 层不聚合来源、不直接拼列表、不新增查询/游标/持久化权威。
- `LibraryFixtureTransport` 把既有 R11 私有 fixture 作品按页渲染为 AppleCMS 原始行
  （真实 `MaccmsCatalogNormalizer` 归一 + `StrictCaptureFacts` 事实化），服务端不过滤
  ——匹配只由本地严格判定；`vod_lang`/`subtitle_language` 只描述播放版本。
  `LibraryCandidateStore` 为内存候选存储：进程重启后重新核对（确定性合成数据），
  不新增 Room 写入者；用户/浏览持久化权威仍是 Room v6 既有表。
- fixture 可选 `library` 计划（分页大小、失败来源、镜像来源）；镜像来源以不同
  `vod_id`、相同 `work_ref` 再发布同一已证作品——展示按组去重，两条来源证据
  与入口全部保留可寻址（不按标题/年份合并）。

## 查询身份与筛选草稿

- `LibraryFilterDraft`/`LibraryFilterController`（纯 JVM 可测）：草稿完整复制已应用
  `CatalogQuery` 的每个字段；确认时仅在真实变化下生成新 queryId 的完整查询
  （`片库查询-N` 递增，重开沿用编号起点），无变化确认沿用原身份不重启结果；
  年份无效（非正数、非整十年代起点、起止倒置、溢出）以中文拒绝且已应用查询不动；
  删片/重置只在用户显式动作下生成新身份并逐字保留其余字段。
- 上下文键 `library-ui:<queryId>`（route=library，R11 语义：查询变化=新身份+新上下文，
  旧上下文行保留）；私有指针 `\u0000cinema.library.active` 独立于浏览活动指针。

## 界面与焦点

- `TvLibraryScreen`/`TvLibraryFilterPanel` 只消费 `LibraryUiState` 投影与回调；
  R13 令牌/组件权威复用（`TvPosterTile` 新增 `fillWidth` 网格模式、`TvStatusMessage`
  新增 `actionEnabled`，默认关闭时 R13 行为不变）。条件片 `FlowRow` 换行保证全部
  可见；筛选面板焦点捕获（背景 `enabled=false`），BACK 只关面板并恢复打开者；
  年份步进器全程 D-pad 可达 2000 年之前；候选词条只来自 fixture 有证据资料。
- 焦点/恢复复用 R11 令牌管线（`BrowseRestoreCoordinator`/
  `BrowseCheckpointCoordinator` 独立实例）；每个平台按键先同步作废待恢复焦点；
  首页 `key()` 的 UP 处理成对清理两个 held 集（片库根画面在 DOWN→UP 间切换时
  不留滞留键——否则隔次吞确认键）；`openWork` 清空本类去重集后才进入浏览子层；
  详情关闭经子层观察器恢复进入前的真实焦点（网格键走 `userTarget` 带滚动定位）。
- 新查询安装即启动 500ms 防抖落盘（`begin` 只挂 pending 不起定时器；无焦点后续
  变化时进程终止前将无提交的真实缺陷修复）。

## 真话状态与覆盖

- `LibraryProjection`（纯 JVM 可测）状态优先级
  FAILED > LOADING > UNCONFIGURED > OFFLINE > PARTIAL > READY/COMPLETE_EMPTY；
  部分覆盖不给出总数；零匹配且仍有来源未核对时保留“继续核对”，不宣告最终空；
  年份未知/冲突不显示具体年份；完整核对的总数明确限定“当前筛选与来源范围”，
  不冒充全库。

## 明确未做

完整 T10 详情/播放器/设置全量重设计、T11 全局遥控与动效、完整 T02 图标盘点、
诊断删除、真实 CMS/海报、生产路由激活、API24/ARMv7 物理电视与发布验收。
同进程仪器化下 am kill/force-stop 会连仪器化一起终止：真实进程死亡证据由
`TvLibraryProcessReplayTest` 两阶段方法 + 宿主 force-stop 编排单独执行。
