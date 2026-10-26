# R16 — 原生 TV 播放器控制与遥控输入架构

> 范围：仅私有一次性 Debug 探针配对路径（`cinema.playerControlsProbe` 链在
> 既有七级探针授权之上）。普通 Debug/Release、用户数据、ABI、签名冻结全部不变。
> 本切片是 T10 播放器控制 + T11 遥控/焦点的有界子集，不是全局 T11/T12/M04 收口。

## 1. 权威链（不新建第二权威）

- **播放器**：唯一 `ExoPlayer` 仍由 R12 `PersonalLibraryRuntime` 持有；
  R16 运行时只做只读投影（`currentPlayer()` 供画面渲染视频面）。
- **检查点/会话**：仍是 R05 `PlaybackCheckpointCoordinator` → R06
  `RoomPlaybackProbe`/`PlaybackRuntimeBridge` → R07 交接管线。R16 的一切
  播放/暂停/seek/换集都经 `player.*` 与 `personal.selectFromPlayer`（内部
  即 `probe.select`：先落旧观察屏障，再按精确 (作品, 剧集, 版本) 回查续播）。
- **用户记录**：单写者仍是 `RoomPersonalLibraryRepository`。
- **输入**：R16 `PlayerControlsRuntime.key` 是播放层的唯一按键解释者，
  挂在 `MainActivity` 窗口回调链最顶层（播放器 > 详情 > 首页 > 浏览）；
  画面内不拦截方向键，避免 `.clickable` 双派发。
- **计时**：唯一周期心跳仍是 `PersonalLibraryRuntime` 的 500ms 探针脉冲；
  R16 的 200ms UI 心跳只在播放层就位时运行、只做只读重投影与按键周期
  超时检查，绝不产生第二个检查点计时器或进度写入者。

## 2. 物理按键周期状态机（`RemoteKeyCycle`，纯 JVM 可测）

每个物理周期（DOWN…配对 UP/CANCEL）至多一次激活：

- OK/Enter/Back：DOWN 不激活；短按只在完整短周期 UP 触发一次；
  长按在阈值（平台 repeat 事件或流逝时间先到者，repeat 也须满足流逝阈值）
  触发一次，且绝不先出短按 —— 修复旧播放器“长按先出短按”缺陷类别。
- 方向键：DOWN 立即移动一次（保留裸播放器语义），随后按节奏有界自动重复。
- 滞留键、丢失 UP（同键再来真实 DOWN 静默作废旧周期）、孤儿 UP、
  ACTION_CANCEL（映射 `FLAG_CANCELED` 的 UP）、焦点丢失只作废周期，
  绝不产生幽灵点击。
- 第二键在另一键持有期间按下被忽略（单周期所有权）。

## 3. 分层语义（K01/K02）

- **隐藏层**（控件收起）：方向键保留既有裸播放器语义 ——
  左右 = 相对快进/快退（仅当 `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` 可用，
  真实时长内钳制；能力缺失时如实提示“当前内容不支持快进快退”，零调用）；
  上下 = 同线路证据清单内上一集/下一集（经 `selectFromPlayer`，无跨身份映射）。
  短 OK 只揭示控件（绝不顺带播放/暂停）；长 OK 同样揭示（快捷方式，
  非必需路径 —— K10：所有操作都有短 OK 可达控件）。
- **可见层**（控件浮层）：方向键一次只移动一个焦点/预览目标；
  时间轴聚焦后左右建立/推进预览（真实时长内钳制），OK 恰好一次
  `seekTo` 提交并退出预览，Back 取消且无任何进度变更。
- **选择器**（线路/集数弹层）：焦点陷阱；条目只来自 R15 详情档案
  （同一证据源，不建第二数据源）；DISABLED/UNREACHABLE 如实标注、
  不可聚焦不可激活（E02）；Back 精确恢复打开控件（E04/K08）。
- **Back 逐层**（K07）：预览 → 选择器 → 控件浮层 → 退出播放层（先检查点
  屏障；提交失败如实提示且第二次 Back 直接退出，绝不无限困住 —— P03）
  → 详情 → 来源（R11/R15 既有返回链）。

## 4. 诚实投影（U01–U07）

- `PlayerStatusProjection`：缓冲/用户暂停/播放被抑制/播完/失败互不冒充
  （`onIsPlayingChanged(false)` ≠ 用户暂停；BUFFERING ≠ 播完）。
- 时长未知（本地无 ENDLIST 的直播式 HLS）：`durationMs = null` →
  界面显示“时长未知”，时间轴禁用绝对拖动，无假总时长/假百分比（U05/S06）。
- 真实 `Player.availableCommands` 门控一切动作（U06）：禁用动作在语义层
  Disabled、不可聚焦、绝不调用播放器命令。
- 浮层自动隐藏（U03/U04）：仅真实播放中且空闲 ≥5s、无选择器/预览/错误/缓冲
  时允许；进行中的选择器与时间轴预览交互永不被计时器隐藏。焦点保护语义：
  “进行中的焦点交互”（预览）受保护；静止焦点在播放中随空闲计时隐藏并
  释放焦点（与主流 TV 播放器一致）—— 该解释在评审中如实披露。
- 进入/恢复一律暂停（P05/P06/P07）：`probe.select/prepareTarget(autoPlay=false)`。

## 5. 门禁与授权

- `cinema.playerControlsProbe`（默认 false）链在全部七级既有探针之上：
  缺任一上级、或任务组合不是精确的 `:app:assembleDebug` +
  `:app:assembleDebugAndroidTest` 配对、或含 Release/bundle/publish/
  install/签名任务 —— 在任何副作用前 `GradleException` 拒绝。
- 私有授权档案 `round16-player-controls.authorization`
  （token `cinema-round16-disposable-player-controls`），仅在
  `DebugBrowseProbeGate.withOwnedProfile` 的结构化作用域内消费。
- 仪器参数 `cinema.player=round16`；探针 OFF 时播放器层保持 R12 简易形态。

## 6. 测试夹具（一次性私有档案）

- 合成媒体全部本地生成（宿主 ffmpeg 合成原创测试图案/正弦音，随
  androidTest 资产分发，复制进目标应用 filesDir 后以 `file://` 引用）：
  - `audio.mp4`：M4A/AAC 24s（主控制类媒体：时长已知、可拖动）。
  - `live.aac`：原始 ADTS 流 30s。探针播放器按 Media3 机制禁用常率
    seek 推断后**不可拖动**（真实 seek 命令缺失），时长可知——“时长未知”
    需直播网络流，本地静态媒体不可达（矩阵 U05 记 BLOCKED，见验证报告）。
  - `video.mp4`：H.264 baseline 320×192 24s（真实视频帧证据，w5 长文案作品）。
  - `corrupt.wav`：真实存在但不可解析（真实 Media3 错误注入）。
  - 其余线路沿用 R15 的合成 WAV。
- 过程披露（三段均已在一次性档案留证）：
  1. 最初尝试设备端 MediaCodec 合成（AVC `getInputImage` + AAC），在该 AVD
     上产出空容器（3KB MP4）——不采用，改宿主 ffmpeg 合成同类原创图案。
  2. 曾以“无 ENDLIST 的本地 HLS（含 TS 分段）”追求直播语义：MediaMuxer
     无 TS 容器改 ADTS 分段；静态本地清单在 Media3 1.11 下仍得出时长且部分
     组合可拖动——最终以“原始 ADTS + 禁用常率 seek 推断”获得不可拖动语义。
  3. 视频基线档位教训：H.264 level 11 违规（宏块率超限）会在部分解码路径
     失败；固定 baseline level 3.0 + 16 对齐尺寸后稳定。
- 媒体矩阵与探针播放器构建（解码器回退、OMX 优先、常率 seek 推断禁用）
  均为既有唯一播放器实例上的配置选择，不构成第二权威。

## 7. 与 R12/R15 的关系

- R12 `PersonalLibraryPlayer` 简易层保留：探针 OFF（或未全开）时原样渲染。
- R15 详情 → 播放器派发（`prepareTarget` 精确三元组）不变；R16 只接管
  播放层内部的控件/输入/换集体验，换集/换线经
  `PersonalLibraryRuntime.selectFromPlayer` 回到同一身份管线
  （屏障先存旧集，迟到回调按代次拒绝 —— E05/E06）。
