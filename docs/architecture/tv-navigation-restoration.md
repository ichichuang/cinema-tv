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

`cinema.browseRestoreProbe` 默认 OFF。ON 只接受完整 assembleDebug/assembleDebugAndroidTest 根任务，并要求已有 playbackRoomProbe 与 storageTestArm64 编译门；拒绝 Release/bundle/publish/install。实际运行还必须通过已有 R07 的应用与测试 APK 同签名、独立私有授权、API30 ranchu ARM64 镜像检查，并且没有已启动的旧数据交接、四类旧 store 没有记录。

`DebugBrowseProbeGate.withOwnedProfile` 的 action 持有已有 writer 互斥并独占数据库生命周期，先读取旧记录；现存候选数据库经只读 quick_check 和拒绝删除的 corruption handler 检查后，再打开 Room 校验 schema 并交付候选 store。异常不变成空记录。候选编译模式立即拒绝 legacyWrite，防止旧写请求排队后恢复。OFF 在访问 Context／数据库／锁前返回。宿主每次安装／运行另核对自有 AVD、serial、host PID、boot ID、API／ABI 和测试包；应用内权限不能替代宿主身份核验。

## 验证基础

使用已锁定的 Compose、AndroidX Test core/runner/ext-junit、Room 与协程；不新增 ui-test 或其他依赖。原生测试通过实际 ViewRootForTest 的 SemanticsOwner 检查 Focused/Selected，使用原生 KeyEvent 驱动控件。FocusRequester 只在实际布局确认后调用；ActivityScenario.recreate 只算 Activity 重建。参考已核对的 [Compose 焦点控制](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior)、[ActivityScenario](https://developer.android.com/reference/androidx/test/core/app/ActivityScenario) 和 [UiAutomation](https://developer.android.com/reference/android/app/UiAutomation)。

本轮完整命令、逐方法结果、42 项矩阵、生命周期差异和未验证范围见本轮验证报告；本说明中的设计不单独构成执行证据。

## 恢复与持久化契约

`BrowseRestoreCoordinator` 在 IO 前 reserveLoad，加载后 completeLoad；方向键／确认／返回同步撤销 ticket 和 restore token。观察必须匹配完整查询与当前 route generation；同查询的旧 result revision 不得逆序生效。每次安装结果或改变列数时 advanceLayout，placement 回调携带该 result revision 与 layout generation 的 token，旧测量不能替新布局提供就绪证明。目标与锚点可用且已放置后才交付一次 focus action，UI 必须在同一事件回合消费。Partial 缺失继续等待，不放宽条件；已证明缺失时只在原行邻居／行入口／页面控件之间回退。 滚动定位与最终焦点使用同一目标／回退策略：Complete 证实锚点移除时，先将仍存在的目标或合法同行邻居滚入实际布局；Partial 不提前选择替代项。没有保存 focus/anchor 的新 context 不施加新的滚动位置。定位本身不消费 focus token，scroll 后再次确认 token 与 layout pass。

`BrowseCheckpointCoordinator` 将 token 绑定 context 与完整查询，500ms 稳定后保存；route leave 和后台由调用方 flush 并检查真实 CommitResult。一个 mutex 串行化 store 调用，旧路由回调不能覆盖新已提交现场；Failed／RejectedStale 保留 pending，取消传播。自动防抖的当前提交结果同步反馈状态，失败不显示成功，重试成功清除旧失败状态。新 begin 前应先处理 flush 结果，不能静默丢失未保存状态。 同一当前 token 的显式 flush 等待串行写完成后，若 pending 已由成功事务清空，则复用已确认的 Committed；陈旧 token／定时 revision 仍拒绝，有新 pending 时保存新状态，不能借旧确认跳过新写。

`RestoreNavigationState` 只允许三个稳定顶层页面及最多两个不同 DETAIL／PLAYER 子路由，返回链做防御性复制。它不表示 modal、确认或播放命令。精确查询变化必须使用新 queryId，contextKey 为顶层 route 与 queryId 的组合；旧查询 context 行保留。

`RoomBrowseStateStore.saveActive` 在既有 v5 browse_snapshots 的单次事务里保存原始 context、全局 active pointer、该顶层的 latest pointer。pointer payload 使用完整快照及有界返回链，复用原 BrowseSnapshotCodec；loadLatest 校验请求顶层与 payload route 一致，损坏记录报错。没有 v6 表或破坏性迁移，旧 schema 1–5 的字节保留由验证报告核对。

## 候选原生接线

MainActivity 在解引用旧 HomeViewModel／UpdateCenter 前按编译门分流，仅 ON 指定横屏，使用公开 Window.Callback 在原窗口派发前同步处理候选按键，未消费事件交还原回调。OFF 不安装包装。实际 CinemaNavHost 提供 browse/detail；详情返回保留三个来源页和完整查询，候选没有自动播放入口。授权 lease 内的结构化协程拥有 runtime 和数据库，退出先处理 pending checkpoint，启动失败清除 runtime 并显示中文不可用状态。

合成 catalog 仅由 instrumentation 写入私有 no-backup fixture，普通应用无内置目录；投影复用 StrictFilterEvaluator，对年份、国家、语言、类型、题材和来源范围执行精确匹配。布局以稳定 work ID 为 LazyGrid key，使用实际 placement、窗口焦点、Focused/Selected semantics 和视口锚点。协调器交付的单次动作仅在原 token／布局／结果 revision 下保留；原生 requestFocus 返回 false 时等待实际就绪事件重试，用户按键撤销后不得复活。首次有效视口与后续尺寸变化触发锚点换算，scroll 完成才允许恢复焦点；列数改变创建新布局 token 并按新行重定位锚点；刷新保留当前顺序和焦点。模态层限制背景可聚焦性，关闭回到仍存在的入口，否则按原工具条顺序选择最近可用的同排控件；同排没有可用控件才回页面默认。路由动作在 IO 前取得请求序号并串行等待提交；新的返回动作可取代尚未执行的旧跳转，方向键仍被处理，详情返回只完成 IO 前已有且未被输入撤销的恢复 ticket。

`round11-browse-trace.json` 是隔离 profile 的调试补充记录，不能代替实际 Compose 焦点、数据库重开或外部新 PID 证据。候选 onPause 同步冻结布局／焦点观察并保存最后有效状态，避免停止期间系统 Insets 改变写入临时锚点；onResume 恢复观察而不复活已撤销 token。Activity 重建、进程 kill 和 force-stop 分别记录；持久化只保证最近一次已确认成功的事务。重建后的 result revision 以已保存 revision 为下界；私有 fixture 的保存暂停有 10 秒上限，默认 0，仅用于已提交／未提交边界实验。候选 semantics 导出稳定 work ID，外部 XML 的真实 focused 节点与私有快照共同核对。
