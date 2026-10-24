# 隔离 Debug 播放运行时探针

本轮只接入测试授权的 Debug T04 切片。普通 Debug 和 unsigned Release 继续调用原 PlaybackProgress，不迁移、不双写。

## 实施前的真实接口图

| 既有边界 | Round 06 最小接入 |
| --- | --- |
| Koin AppModule 的 PlayerViewModel(repository, progress, settings, context) | 保留参数及默认装配；测试通过既有 repository 参数传入本地 fixture 仓库 |
| PlaybackProgress.get/save 与 source-key fallback | 仅 Legacy 分支原调用；Probe 不读写该文件 |
| PlayerViewModel.start/playEpisode/selectEpisode/selectLine/retry | Probe 分支等待旧检查点提交后更新媒体及集上下文；失败不换集 |
| Player.Listener、500ms 心跳、pauseAndSave、onCleared | 同一 bridge 收集不可变事件及接收时刻；调用方拥有的串行协程；销毁取消，不承诺最后未提交瞬间 |
| PlaybackCheckpointCoordinator.select/ready/playing/pulse/seek/loading/boundary/ended | 直接沿用 Round 05 类型、五秒政策及持久 generation/sequence；不复制模型 |
| RoomPlaybackProgressStore.beginSession/commit/loadResume | 真 Room v2 file-backed；Committed 才是持久确认 |
| 既有 MovieDetail / PlayLine / Episode | 没有可信稳定 episode/variant ID；只接受测试仓库显式提供的既有 PlaybackTarget，禁止按标题、URL或下标造 domain ID |

## 门禁设计

`cinema.playbackRoomProbe` 默认 false。true 只允许同时请求精确的 `:app:assembleDebug :app:assembleDebugAndroidTest`；另校验实际任务图。ARM64 仍独立 opt-in。Release 的常量永远 false，签名冻结保持。

运行时还要求 Debug、仪器测试写入的私有授权标记和无任何旧 progress/browse/filter 文件的可丢弃 profile。探针开启但授权失败时拒绝构造，不自动回退到另一个 writer；探针关闭时不检查标记、不打开 Room。测试仓库必须提供唯一且完整的显式作品/集/版本映射和 app-private 本地 URI；普通线上仓库没有这类身份时拒绝启动 Probe，不猜关联。

采用的规则：严格拒绝任何旧记录文件（包含空文件或暂存文件），比解析“是否有有效观看记录”保守；代价是旧 Debug profile 不可用于探针。只能使用新的测试 profile。

最终实测为 JVM177/177、Python62/62、原生71/71；普通两属性OFF回归140个任务实际执行。分层证据与未验收边界见 [Round 06 验证](../verification/2026-10-24-round-06-playback-runtime.md)。

## 实际事件和持久化路径

`PlayerViewModel` 构造前先选择 Legacy 或 RoomProbe；Koin 参数和普通分支原调用保持。Probe 使用 `DefaultDataSource` 读取测试仓库提供的 app-private file URI；路径经 canonical 校验，拒绝网络、外部文件、缺失映射和重复键。`ProbeMedia` 只携带既有 `PlaybackTarget` 与已核对的目录坐标，不生成作品／集／版本身份。

`RoomPlaybackProbe` 是唯一 Media3/Room 绑定。它在 player looper 捕获 session token、接收单调时间、状态、实际 playing、位置、原始 duration 和明确意图；`PlaybackRuntimeBridge` 只翻译事件并调用原协调器，不是另一个进度模型。默认500ms心跳只向 Probe 提供实际播放观察。未知或负 duration 输入按 absent 处理，保留已有时长；媒体位置不参与保存计时或持久仲裁。

| 输入 | 实际处理 |
| --- | --- |
| ready、真实 isPlaying | 累计实际播放，五秒尝试检查点；恢复自身不算已看 |
| buffering、idle、error | 停止累计并保留最后有效位置，不将 false/0 当暂停提交 |
| 用户 pause / noisy | 记录明确边界；普通 isPlaying=false 不直接提交 |
| SEEK discontinuity | 使用新 PositionInfo、原 session token 和准备状态；允许快退、0、重看 |
| retry | 保留最后有效位置重新 prepare；不分配错误的新身份；成功重试清除 Probe 错误提示 |
| 切集／线路 | 等待旧快照真实提交，失败则保留原上下文；按精确身份读取目标记录，不跨版本沿用位置 |
| ended | 仅完成当前真实观看集；Probe 不自动跨集，不伪造整剧完成 |
| 后台／离开 | 同步撤销待执行切集和 retry 的自动播放意图，执行时再次暂停实际 player；旧 observation 不改绑新集 |
| 旧 token | 忽略；Room generation / operationSequence 继续负责持久顺序 |

`PlaybackEventQueue` 是 ViewModel scope 的单个串行子协程。每次可等待请求还有独立且隶属于 worker 的 Job；取消单次请求会取消其已进入的挂起块，队列仍可接下一个请求。播放器副作用前检查取消。销毁移除监听、取消整个 worker，完成取消／事务收尾后关闭 Room，没有 GlobalScope 或销毁后的游离写入。

Room v2 的真实 `beginSession/commit/loadResume` 保持原样，schema 1/2 均不改。不成功的保存不报告已保存；损坏／读取失败不回退空值或 Legacy。最后成功提交是持久保证；Back、ON_STOP、硬杀前尚未完成的请求可能被取消，不能承诺最后一瞬间或任意断电零损失。

Media3 把播放状态与 playWhenReady／suppression 分开；组合状态使用 onEvents，seek 使用带 reason 的单独回调，依据本轮核查的[官方事件说明](https://developer.android.com/media/media3/exoplayer/listening-to-player-events)和 [Player.Listener](https://developer.android.com/reference/androidx/media3/common/Player.Listener)。

## 证据和激活边界

实际执行与产物指纹见 [Round 06 验证](../verification/2026-10-24-round-06-playback-runtime.md)。一次只读独立审阅发现两项异步竞态，分别由真实 SQLite 写锁的 Media3 测试、已进入请求的单次 Job 取消测试复现后修正。修正后不再独立复审，作者负责红绿及完整回归。

这条路径只能在测试构建、测试标记和新建可丢弃 profile 中验证。普通 Debug／Release 的 Legacy 快退、重试、切集等原有行为并未在本轮修复。WAV 只验证 Media3 事件和持久化；TV 视频解码、ARMv7、实体遥控器、真实用户迁移、T03／T04 完整生产接入、M02 和 A06–A09 仍未验收。
