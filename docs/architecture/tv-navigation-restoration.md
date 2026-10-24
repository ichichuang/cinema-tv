# 候选原生浏览与焦点恢复

Round 11 仅覆盖测试自有合成 profile 的 T08 切片。普通 Debug／Release 仍使用原导航与 DataStore；不代表完整 T08、T09、T10 或目标电视验收。

## 原有实现与插入边界

| 原有位置 | 实际职责与保留边界 |
| --- | --- |
| MainActivity、CinemaNavHost | Activity 延迟获取 HomeViewModel；原 NavController 管理 home/detail/player/settings。候选选择必须在获取旧 VM 前完成，OFF 保留原路由。 |
| HomeViewModel | 共享首页与详情，旧 BrowseMemory 保存 movie.uniqueKey/index，700ms 防抖；不承担 CatalogQuery 恢复。 |
| DataStoreBrowseStore | cinema_browse 的 last_movie_id/last_index；通过已有 editLegacy 写屏障。旧文件和旧逻辑保留。 |
| BrowseSnapshot、BrowseStateStore | 沿用 contextKey、route/parentRoute、完整 CatalogQuery、revision、稳定 work/anchor 和相对视口偏移；不添加另一套查询模型。 |
| RoomBrowseStateStore、browse_snapshots | 现有 v5 表按 contextKey 保存版本化 payload。Room 事务返回后才报告 Committed；不拿请求发出当落盘。 |
| LegacyHandoffRuntime | 现有测试签名／独立私有 profile 与 consent／API30 ARM64 镜像授权，以及单 writer 互斥。候选不调用迁移 prepare/activate。 |

## 门禁

`cinema.browseRestoreProbe` 默认 OFF。ON 只接受完整 assembleDebug/assembleDebugAndroidTest 根任务，并要求已有 playbackRoomProbe 编译门；拒绝 Release/bundle/publish/install。实际运行还必须通过已有 R07 的应用与测试 APK 同签名、独立私有授权、API30 ranchu ARM64 镜像检查，并且没有已启动的旧数据交接、四类旧 store 没有记录。

`DebugBrowseProbeGate.withOwnedProfile` 的 action 持有已有 writer 互斥并独占数据库生命周期，先读取旧记录并校验 Room schema，再交付候选 store。异常不变成空记录。候选编译模式立即拒绝 legacyWrite，防止旧写请求排队后恢复。OFF 在访问 Context／数据库／锁前返回。宿主每次安装／运行另核对自有 AVD、serial、host PID、boot ID、API／ABI 和测试包；应用内权限不能替代宿主身份核验。

## 验证基础

使用已锁定的 Compose、AndroidX Test core/runner/ext-junit、Room 与协程；不新增 ui-test 或其他依赖。原生测试通过实际 ViewRootForTest 的 SemanticsOwner 检查 Focused/Selected，使用原生 KeyEvent 驱动控件。FocusRequester 只在实际布局确认后调用；ActivityScenario.recreate 只算 Activity 重建。参考已核对的 [Compose 焦点控制](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior)、[ActivityScenario](https://developer.android.com/reference/androidx/test/core/app/ActivityScenario) 和 [UiAutomation](https://developer.android.com/reference/android/app/UiAutomation)。

本轮完整命令、逐方法结果、42 项矩阵、生命周期差异和未验证范围见本轮验证报告；本说明中的设计不单独构成执行证据。

## 恢复与持久化契约

`BrowseRestoreCoordinator` 在 IO 前 reserveLoad，加载后 completeLoad；方向键／确认／返回同步撤销 ticket 和 restore token。观察必须匹配完整查询与当前 route generation；同查询的旧 result revision 不得逆序生效。每次安装结果或改变列数时 advanceLayout，placement 回调携带该 result revision 与 layout generation 的 token，旧测量不能替新布局提供就绪证明。目标与锚点可用且已放置后才交付一次 focus action，UI 必须在同一事件回合消费。Partial 缺失继续等待，不放宽条件；已证明缺失时只在原行邻居／行入口／页面控件之间回退。

`BrowseCheckpointCoordinator` 将 token 绑定 context 与完整查询，500ms 稳定后保存；route leave 和后台由调用方 flush 并检查真实 CommitResult。一个 mutex 串行化 store 调用，旧路由回调不能覆盖新已提交现场；Failed／RejectedStale 保留 pending，取消传播。新 begin 前应先处理 flush 结果，不能静默丢失未保存状态。

`RestoreNavigationState` 只允许三个稳定顶层页面及最多两个不同 DETAIL／PLAYER 子路由，返回链做防御性复制。它不表示 modal、确认或播放命令。精确查询变化必须使用新 queryId，contextKey 为顶层 route 与 queryId 的组合；旧查询 context 行保留。

`RoomBrowseStateStore.saveActive` 在既有 v5 browse_snapshots 的单次事务里保存原始 context、全局 active pointer、该顶层的 latest pointer。pointer payload 使用完整快照及有界返回链，复用原 BrowseSnapshotCodec；loadLatest 校验请求顶层与 payload route 一致，损坏记录报错。没有 v6 表或破坏性迁移，旧 schema 1–5 的字节保留由验证报告核对。
