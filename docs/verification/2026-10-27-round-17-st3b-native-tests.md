# Round 17 ST3.1b — U05 / P07 / 720p 原生测试返工验证报告

> 执行日期：2026-10-27。分支 `main`，起点 HEAD `7382e83`（ST3 文档修正提交）。
> 本轮只做 **ST3.1b 原生 androidTest**：逐条复核上一轮（`321ab26`）新增的 U05/P07/720p 测试，
> 修复「能编译但断言无区分力 / 恒真 / 用裸 requireNotNull 竞态」的问题，使每个断言在正确行为下通过、
> 在错误行为下失败。**box 无模拟器，所有 androidTest 仅能编译，状态如实标 UNVERIFIED，不打任何 PASS 勾。**

## 一、目标与范围

对三个原生测试文件做独立复核并修复断言质量：

| 文件 | 上一轮问题 | 本轮处置 |
|---|---|---|
| `PlayerControlsUnknownDurationNativeTest.kt`（U05） | 用自造 ADTS 帧而非仓库 `live.aac`；「0 次 seek」是恒真断言（孤立控制器不触发 `seekCalls`）；未断言 READY / `playerError==null`；「中文未知时长」读测试自写常量 | 改用 `live.aac` fixture 字节经 loopback server 提供；补齐 READY / `playerError==null` / `C.TIME_UNSET` / 无 seek 命令；「时间轴禁用/无预览/OK 不产生 SeekTo」改为驱动真实控制器状态机 |
| `PlayerControlsProcessReplayTest.kt`（P07） | `player()`/`personal()` 用手写 while+Thread.sleep + 裸 `requireNotNull`；阶段 B 的 `until` 条件内 `requireNotNull` 会在装配前抛 NPE | 改为可观察的有界就绪条件（`until` 辅助），condition 内 null 安全，超时统一失败并附诊断；阶段 A/B 断言保持不变 |
| `TvPlayerControlsScreenshotTest.kt`（720p） | 「仅音频进度」阶段用注释回避「视频帧区必须黑」的硬断言；未区分解码器初始化 / surface 附着 | 补齐 A 解码器轨道 / B surface 尺寸 / C 播放就绪 / D 真实帧 / E 仅音频（无视频轨道且视频帧区黑，否则失败）五段断言 |

## 二、U05 逐条断言与「为何能在错误行为下失败」

`loopback_live_stream_reports_unknown_duration_and_no_seek`：

| # | 断言 | 正确行为 | 错误行为下如何失败 |
|---|---|---|---|
| 1 | `playbackState == STATE_READY`（有界 15s 等待，超时失败附 `state`/`playerError`/`servedRequests`） | 回环流正常取到 READY | 服务器响应头/端口/协议不对时，永远到不了 READY，超时抛断言错误并附当前状态与已服务请求数 |
| 2 | `playerError == null` | 未知时长不是错误态 | 若服务器返回错误（如 404、Content-Type 错、连接中断）导致播放失败，`playerError` 非空，失败 |
| 3 | `duration == C.TIME_UNSET` | 无 Content-Length 的常率流不推断时长 | 若服务器误带 Content-Length 或 Media3 推断出时长，`duration` 为具体值，失败 |
| 4 | `!isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)` | 未知时长不暴露绝对 seek | 若时长被推断为已知，命令变为可用，失败 |
| 5 | `!controlEnabled("时间轴")` | durationMs==null 时时间轴禁用 | 若上游把时长当成已知（错误行为），`controlEnabled` 变 true，失败 |
| 6 | `!beginPreview(0L)` 且 `previewMs == null` | 无时长不可建立预览 | 若时长已知，`beginPreview` 返回 true、`previewMs` 非空，失败 |
| 7 | `overrideFocus("时间轴")` 后 `confirm() == None` | 聚焦禁用时间轴后 OK 不产生 SeekTo | 若控制器在禁用态仍产出 seek 命令，返回非 `None`，失败 |

说明：断言 5–7 不是读测试自写的常量，而是驱动真实 `PlayerControlsController` 状态机；
其正确性的上游事实由断言 3（`duration == C.TIME_UNSET`）确立。**「真实 Compose 语义节点上的中文“时长未知”文案」与「真实 `player.seekTo` 零调用」无法在本测试验证**：生产播放管线经 `ProbeMediaFile.requirePrivate` 只接受私有目录内的 `file://` 媒体，loopback `http://` URL 过不了该门禁，故本测试直接构造 ExoPlayer（不弱化 `requirePrivate`、不新增生产网络例外）。中文文案渲染分支（`TvPlayerControlsScreen.TimelineRow` 的 `duration==null -> "时长未知"`）由 JVM 测试与其他原生测试覆盖，本测试只验证其上游事实 → **该环节 UNVERIFIED**。

## 三、P07 逐条处置

| 原代码 | 问题 | 处置 |
|---|---|---|
| `player()`/`personal()` 手写 `while(… < end){ onActivity{…}; if(!=null) …; Thread.sleep(100) }` + 兜底 `requireNotNull` | 用 sleep 堆时间；兜底 `requireNotNull` 会裸抛 NPE 而非可诊断超时 | 改为 `until(10_000){ onActivity{ ready = probe != null }; ready }`，超时由 `until` 统一抛 `AssertionError` 附诊断 |
| 阶段 B `until{ onActivity{ requireNotNull(it.browseProbe).isPlayer } }`、`until{ onActivity{ requireNotNull(it.playerControlsProbe).display… } }` | 探针装配前 `requireNotNull` 抛 NPE | 改为 null 安全 `it.browseProbe?.isPlayer == true`、`it.playerControlsProbe?.display?.variantId != null` |

阶段 A/B 的原有断言（PID 不同、身份一致、恢复到最后已提交位置、保持暂停）**未改动**，只替换就绪机制。
`untilProcessGone` 用 `/proc/$pid` 存在性做有界轮询（`Thread.sleep(100)` 为轮询间隔，是等待外部进程退出的可观察就绪条件，非盲等堆时间）；超时后按设计降级为「由后续可观察条件（isPlayer / display != null）守卫就绪」，不吞异常。

## 四、720p 逐条断言与「为何能在错误行为下失败」

`video_render_stages_distinct_from_overlay`：

| 阶段 | 断言 | 错误行为下如何失败 |
|---|---|---|
| A 解码器/轨道初始化 | `currentTracks.groups` 含 `C.TRACK_TYPE_VIDEO` | 若媒体被误当作纯音频 / 解码器未初始化，无视频轨道，失败 |
| B surface 附着/解码尺寸 | `videoSize.width > 0 && height > 0`（有界等待） | surface 未附着 / 解码器未出尺寸时 `videoSize` 为 UNKNOWN(0,0)，失败 |
| C 播放就绪 | `playbackState == STATE_READY && isPlaying` | 未就绪或未播放则失败 |
| D 真实渲染帧 | `videoFrameRendered(shot)`（画面中部非纯黑，与叠层底部分区分离） | 解码器初始化但 surface 未附着 / 未真正渲染时，视频帧区保持黑，失败 |
| E 仅音频进度 | 位置前进且 `currentTracks` 无视频轨道、`videoSize == 0`、`!videoFrameRendered(audioShot)` | 若仅凭进度/状态就通过（把叠层当视频证据），或音频媒体意外带视频轨道，失败 |

关键区分：阶段 D 用**视频帧区（画面中部）非纯黑像素**作为真实渲染证据，绝不把可见的播放器控制叠层（底部）当视频证据；阶段 E 把「视频帧区非黑」作为**失败条件**，与阶段 D 形成对照。仓库已有合法本地视频 fixture（`round16/video.mp4`，w5 的 `EPISODE_H1`），不新增来源不明的媒体。

## 五、真实编译证据与验收命令

| 命令 | 真实结果 |
|---|---|
| `compileDebugAndroidTestKotlin` | **通过**（三次运行；第一次暴露 `videoFormat` 未解析，修复为 `videoSize`+`currentTracks` 后通过）。编译产物 `.class` 时间戳 22:02:03 > 源码最晚修改 22:00:17，证明修复后代码已编译。最终 `compileDebugAndroidTestKotlin UP-TO-DATE` |
| `:app:assembleDebugAndroidTest` | `mergeExtDexDebugAndroidTest` 因沙箱拦截 transforms 缓存（`Operation not permitted`）失败；`compileDebugAndroidTestKotlin` 已成功，剩余失败与代码无关（见 §七） |
| `:app:testDebugUnitTest` | `files=50 tests=506 failures=0 errors=0 skipped=0`（FROM-CACHE，≥ 506 且 0 失败） |
| `python3 -m unittest discover -s scripts/tests` | `Ran 62 tests ... OK` |
| `python3 scripts/check_repository_security.py` | `Checked 453 tracked files; 0 policy violations` |

androidTest 在 box 上**无设备、只能编译**：U05/P07/720p 状态全部 UNVERIFIED（720p 用仓库已有视频 fixture，非 BLOCKED）。

## 六、执行环境披露

- box 无 Android 模拟器/真机，所有 androidTest 仅能编译，不能运行。
- 加 `-Pksp.incremental=false -Pkotlin.incremental=false` 绕过沙箱对 KSP/Kotlin 增量编译 futimens 的拦截（与上一轮 ST3 文档 §六 一致）。
- 即使加上述参数，gradle 收尾写 `problems-report.html` 与 dex 合并读 transforms 缓存仍被沙箱 `Operation not permitted` 拦截，导致 `BUILD FAILED`（假阴性）；真实结果以「`compileDebugAndroidTestKotlin` 通过 + `.class` 产物时间戳」与「测试 XML / unittest / security」为准。
- 未使用任何 shim；未新增 Thread.sleep 到测试逻辑（`untilProcessGone` 的轮询间隔与阶段 A 既有 `Thread.sleep(2500)` 为等待异步收敛的既存代码，非本轮新增）。

## 七、未验证项与遗留风险

1. **U05/P07/720p 全部 UNVERIFIED**：无模拟器，仅编译；运行行为（尤其 `duration==C.TIME_UNSET`、`videoSize`、`videoFrameRendered` 像素判据）需真机/模拟器验证。
2. **U05 中文文案 + 真实 seek 零调用**：因 loopback `http://` URL 无法过 `ProbeMediaFile.requirePrivate` 私有门禁，本测试直接构造 ExoPlayer，无法经真实 Activity/Runtime 投影验证中文语义节点与 `seekCalls`；已在上游事实（`C.TIME_UNSET`、命令缺失、控制器禁用时间轴/无预览/无 SeekTo）层验证，中文文案渲染分支环节标 UNVERIFIED。
3. **720p 像素判据的鲁棒性**：`videoFrameRendered`（红色通道 > 18）与 `overlayRendered`（亮度 > 90）阈值依赖合成视频 fixture 的实际帧内容，需在模拟器上首次运行时校准；纯音频时视频帧区是否严格为黑也需实测确认（若 PlayerSurface 在无视频时输出非黑背景，阶段 E 的 `!videoFrameRendered` 可能误报，需在模拟器上核查后微调分区判据）。

## 八、commit 列表

| SHA | 内容 |
|---|---|
| （见 git log） | R17-ST3b: 修复 U05/P07/720p 原生测试断言（有区分力），并撰写本报告 |

（提交 SHA 待 `git commit` 后回填。）
