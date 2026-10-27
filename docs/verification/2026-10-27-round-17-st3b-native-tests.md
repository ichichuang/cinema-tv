# Round 17 ST3.2 — U05 / P07 / 720p 原生测试返工验证报告（第三轮收口）

> 执行日期：2026-10-27。分支 `main`，起点 HEAD `9394937`（ST3.1b 文档收尾提交）。
> 本轮只做 **ST3.2 原生 androidTest 与文档收口**：独立复核者已用 JVM 复刻与 Media3 字节码确认
> 上一轮（`d97877a`）的 U05/P07/720p 测试仍存在三类缺陷——线程、HTTP 响应头、恒真断言、日志
> 唯一文件名——逐条真实修复。**box 无模拟器，所有 androidTest 仅能编译，状态如实标 UNVERIFIED，
> 不打任何 PASS 勾。**
>
> **ST3.3 对齐说明（2026-10-27）**：独立复核发现 `ab36e3f` 版本的本文档与代码不符（U05 的
> `ExoPlayer.Builder(...).build()` 实际仍在 instrumentation 线程；P07 收敛环含 `Thread.sleep(100)`；
> §五安全检查「0 violations」不实，本文件自身含一处 personal-path）。代码已在 `9536cec` 修正，
> 本文档按 `9536cec` 最终代码改写，下文描述均以最终代码为准。

## 一、本轮修复清单（对照独立复核者事实）

| 文件 | 复核者指出的缺陷 | 本轮处置 |
|---|---|---|
| `PlayerControlsUnknownDurationNativeTest.kt`（U05） | ExoPlayer 在 instrumentation 线程（无 Looper）创建/访问，Media3 1.11.1 抛 wrong-thread | 最终代码（`9536cec`）：`ExoPlayer.Builder(context)...build()` 在 `instrumentation.runOnMainSync { ... }` 内执行（Builder 取主线程 Looper 为应用线程），之后 `setMediaItem/prepare`、`playbackState/playerError/duration/isCommandAvailable` 读取、`@After` 中 `release` 也全部经 `runOnMainSync` 在主线程执行，值用 AtomicReference/AtomicInteger/AtomicLong 带出；`controller` 为纯逻辑，在测试线程调用。注：`ab36e3f` 时 `build()` 仍在 instrumentation 线程，本文档当时的描述不实，已在 `9536cec` 修正 |
| 同上 | loopback 响应写了 `Transfer-Encoding: chunked` 但响应体是原始 ADTS 字节，客户端读报 `Bogus chunk size` | 响应头只保留 `HTTP/1.1 200 OK` + `Content-Type: audio/aac` + `Connection: close`，不写 Content-Length、不写 Transfer-Encoding；响应体是仓库已有 `live.aac` fixture 原始字节（复用，不新增媒体） |
| 同上 | 断言 5–7 传写死常量 `durationMs=null, seekAvailable=false`；`overrideFocus` 后 `assertNull(previewMs)` 恒真 | `controller.install` 的 duration/seek 改用真实播放器值：`p.duration.takeIf { it != C.TIME_UNSET }` 与 `p.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)`（主线程读取）；删除恒真 `assertNull(previewMs)` |
| `PlayerControlsProcessReplayTest.kt`（P07） | 日志文件名固定 `round16-process-a.json`，未做唯一化 | 每次运行用唯一文件名 `round17-process-<uuid>.json`，经 `noBackupFilesDir/round17-process-current.txt` 指针文件在两进程间传递文件名，不覆盖旧日志 |
| 同上 | `untilProcessGone` 超时后静默继续 | 超时抛 `AssertionError` 并附 PID 诊断，绝不静默继续 |
| 同上 | 阶段 A 条件含 `display!!` 与 `Thread.sleep(2_500)` | 去掉 `display!!`（改 null 安全 `it.display?.positionMs ?: 0L`）；`Thread.sleep(2_500)` 换成有界收敛条件（最终代码见 §三：`until(20_000)` 内要求提交位置 ≥13000 且连续 ≥2,500 ms 不变，无任何 sleep） |
| 文档 | §二称断言 5–7 驱动真实状态机、称 loopback 能到 READY（原实现有线程+HTTP 缺陷到不了）；安全检查文件数写 453（实为 454）；P07 未提唯一文件名未做 | 逐条改正，见下 |

## 二、U05 逐条断言与「为何能在错误行为下失败」

`loopback_live_stream_reports_unknown_duration_and_no_seek`（全程主线程访问 Media3）：

| # | 断言 | 正确行为 | 错误行为下如何失败 |
|---|---|---|---|
| 1 | `playbackState == STATE_READY`（有界 15s 等待，主线程取样，超时失败附 `state`/`playerError`/`servedRequests`） | 回环流正常取到 READY | 服务器响应头/端口/协议不对、或错误写了 `Transfer-Encoding: chunked` 时，读失败/永远到不了 READY，超时抛断言错误并附当前状态与已服务请求数 |
| 2 | `playerError == null` | 未知时长不是错误态 | 若服务器返回错误（404、Content-Type 错、chunked 分块与原始 ADTS 字节不匹配导致 `Bogus chunk size`），`playerError` 非空，失败 |
| 3 | `duration == C.TIME_UNSET` | 无 Content-Length 的常率流不推断时长 | 若服务器误带 Content-Length 或 Media3 推断出时长，`duration` 为具体值，失败 |
| 4 | `!isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)` | 未知时长不暴露绝对 seek | 若时长被推断为已知，命令变为可用，失败 |
| 5 | `controller.install(duration = p.duration.takeIf { it != C.TIME_UNSET }, seek = p.isCommandAvailable(...))` 后 `!controlEnabled("时间轴")` | 真实播放器值为 null/false 时时间轴禁用 | 若上游把时长当成已知（错误行为），install 收到的 duration 非空 → `controlEnabled` 变 true，失败 |
| 6 | `!beginPreview(0L)` | 无时长不可建立预览 | 若时长已知，`beginPreview` 返回 true，失败 |
| 7 | `overrideFocus("时间轴")` 后 `confirm() == None` | 聚焦禁用时间轴后 OK 不产生 SeekTo | 若控制器在禁用态仍产出 seek 命令，返回非 `None`，失败 |

说明：断言 5–7 的输入（duration/seek）来自断言 3/4 得到的**真实播放器值**，而非测试自写的
`null/false` 常量；若 Media3 错误推断时长为已知，`controlEnabled("时间轴")`/`beginPreview`/`confirm`
都会随之变化，测试即失败。上一轮被删除的恒真断言：`overrideFocus("时间轴")` 之后的
`assertNull("聚焦禁用时间轴后 OK 不产生预览", controller.previewMs)` —— `overrideFocus` 不触碰
`previewMs`，该断言恒真，已删除；它原本想验证的「聚焦禁用时间轴后 OK 不产生 preview」由断言 7
（`confirm() == None`，且 `confirm` 只在 `previewMs != null` 时返回 SeekTo）实质覆盖。

**UNVERIFIED 项（无法在本测试验证，不做假断言）**：
- 「真实 Compose 语义节点上的中文“时长未知”文案」：生产播放管线经 `ProbeMediaFile.requirePrivate`
  只接受私有目录内的 `file://` 媒体，loopback `http://` URL 过不了该门禁，本测试直接构造 ExoPlayer，
  不弱化 `requirePrivate`、不新增生产网络例外。中文文案渲染分支（`TvPlayerControlsScreen.TimelineRow`
  的 `duration==null -> "时长未知"`）由 JVM/其他原生测试覆盖，本测试只验证其上游事实 → UNVERIFIED。
- 「真实 `player.seekTo` 零调用」：本测试无真实 Activity/Runtime 链路、无 seek 调用计数挂钩，
  不写假断言 → UNVERIFIED。上游事实（命令缺失 → 控制器 confirm 不返回 SeekTo）已由断言 4/7 覆盖。

## 三、P07 逐条处置

| 原代码 | 问题 | 处置 |
|---|---|---|
| 记录文件名固定 `round16-process-a.json` | 每次运行覆盖旧日志，无法区分 | 唯一文件名 `round17-process-<uuid>.json`；阶段 A 写指针文件 `round17-process-current.txt`，阶段 B 读指针取得同一文件名（两进程共享 `noBackupFilesDir`） |
| `untilProcessGone` 超时后静默继续 | 吞掉进程残留 | 超时抛 `AssertionError` 并附 `PID=… 未退出` 诊断 |
| 阶段 A `player { position = it.display!!.positionMs }` | 裸 `display!!` 装配前抛 NPE | 改 null 安全 `it.display?.positionMs ?: 0L` |
| 阶段 A `Thread.sleep(2_500)` | 固定 sleep 堆时间 | 最终代码（`9536cec`）：在 `until(20_000) { ... }`（fixture 每 25 ms 轮询，超时抛 `AssertionError` 附诊断）内反复读取 `loadResume(w[0]).positionMs`；位置一旦变化即重置稳定窗口起点（`SystemClock.uptimeMillis()`），仅当位置 ≥13000 且已连续 ≥2,500 ms 不变时判定收敛，并把该值记为 `committed`，随后 `assertTrue(committed >= 13_000)`。收敛环内**无 `Thread.sleep`**（`ab36e3f` 版本曾有 `Thread.sleep(100)`，已删除）。该条件比旧版「到 13000 后盲等 2.5 s 再读一次」更严格 |

阶段 A/B 的原有断言（PID 不同、身份一致、恢复到最后已提交位置、保持暂停）**未改动**。
`untilProcessGone` 用 `/proc/$pid` 存在性做有界轮询（`Thread.sleep(100)` 为轮询间隔，是等待外部
进程退出的可观察就绪条件，非盲等堆时间）；超时改为失败而非降级。

**最终代码中剩余的 `Thread.sleep`（均为既有代码，`9394937..9536cec` 未新增）**：

| 位置 | 调用 | 用途 |
|---|---|---|
| `PlayerControlsProcessReplayTest.kt:85` | `Thread.sleep(100)` | `untilProcessGone` 的 `/proc/$pid` 有界轮询间隔，超时失败 |
| `PlayerControlsUnknownDurationNativeTest.kt:144` | `Thread.sleep(10)` | loopback 服务端分块写 ADTS 字节的节流（模拟直播流） |
| `PlayerControlsUnknownDurationNativeTest.kt:181` | `if (!reachedReady) Thread.sleep(50)` | 等待 READY/错误的有界轮询间隔（读取在主线程） |

## 四、720p 逐条断言与「为何能在错误行为下失败」

`video_render_stages_distinct_from_overlay`（本轮未改动断言，仅如实标 UNVERIFIED）：

| 阶段 | 断言 | 错误行为下如何失败 |
|---|---|---|
| A 解码器/轨道初始化 | `currentTracks.groups` 含 `C.TRACK_TYPE_VIDEO` | 若媒体被误当作纯音频 / 解码器未初始化，无视频轨道，失败 |
| B surface 附着/解码尺寸 | `videoSize.width > 0 && height > 0`（有界等待） | surface 未附着 / 解码器未出尺寸时 `videoSize` 为 UNKNOWN(0,0)，失败 |
| C 播放就绪 | `playbackState == STATE_READY && isPlaying` | 未就绪或未播放则失败 |
| D 真实渲染帧 | `videoFrameRendered(shot)`（画面中部非纯黑，与叠层底部分区分离） | 解码器初始化但 surface 未附着 / 未真正渲染时，视频帧区保持黑，失败 |
| E 仅音频进度 | 位置前进且 `currentTracks` 无视频轨道、`videoSize == 0`、`!videoFrameRendered(audioShot)` | 若仅凭进度/状态就通过（把叠层当视频证据），或音频媒体意外带视频轨道，失败 |

关键区分：阶段 D 用**视频帧区（画面中部）非纯黑像素**作为真实渲染证据，绝不把可见的播放器控制
叠层（底部）当视频证据；阶段 E 把「视频帧区非黑」作为**失败条件**，与阶段 D 形成对照。仓库已有
合法本地视频 fixture（`round16/video.mp4`，w5 的 `EPISODE_H1`），不新增来源不明的媒体。

## 五、真实编译证据与验收命令

ST3.3 独立复核在 box 上对 `9536cec` 的临时 worktree 重跑的真实结果（`2026-10-27`，`--offline --max-workers=1`；安全检查为本文档修正后在主仓库重跑）：

| 命令 | 真实结果 |
|---|---|
| `:app:testDebugUnitTest --rerun` | **BUILD SUCCESSFUL**；XML 统计 `files=50 tests=506 failures=0 errors=0 skipped=0` |
| `:app:assembleDebugAndroidTest --rerun-tasks` | **BUILD SUCCESSFUL**（57 个任务全部执行，含 `kspDebugKotlin`、`kspDebugAndroidTestKotlin`、`compileDebugAndroidTestKotlin`；产出 `app-debug-androidTest.apk`） |
| `python3 -m unittest discover -s scripts/tests` | `Ran 62 tests ... OK` |
| `python3 scripts/check_repository_security.py`（修正前，`9536cec`） | `Checked 454 tracked files; 1 policy violations`（本文件 personal-path，即旧 §六的 JDK 绝对路径） |
| `python3 scripts/check_repository_security.py`（本文档修正后） | `Checked 454 tracked files; 0 policy violations`（暂存本文档后重跑；脚本只读暂存/已跟踪 blob） |

注：`ab36e3f` 版本本节所写「`0 policy violations`」与「`assembleDebugAndroidTest` 沙箱 BUILD FAILED」
均为执行者沙箱内的结论；前者不实（当时即有 1 处违规），后者在 box 上不复现。

androidTest 在 box 上**无设备、只能编译**：U05/P07/720p 状态全部 UNVERIFIED。

## 六、执行环境披露

- box 无 Android 模拟器/真机，所有 androidTest 仅能编译，不能运行。
- 使用 JDK 17，显式设置 `JAVA_HOME`、`GRADLE_USER_HOME`、`ANDROID_USER_HOME`；加
  `--no-daemon -Dorg.gradle.workers.max=1 --max-workers=1`，并加
  `-Pksp.incremental=false -Pkotlin.incremental=false` 绕过沙箱对 KSP/Kotlin 增量编译 futimens
  的拦截。
- （以下为 ST3.2 执行者沙箱的环境，box 复核不受此限制，见 §五。）
- **ST3.2 执行沙箱内存约束（如实披露）**：沙箱对进程有硬性 `ulimit -v 4194304`（4GB 虚拟内存，不可调高），
  且共享宿主内存已严重超售（`Committed_AS ≈ 25.8GB` vs `CommitLimit ≈ 8GB`，`SwapTotal 0`）。
  默认 G1 GC + 常规堆在 daemon fork 即 OOM（`Native memory allocation (malloc) failed`）；
  改用 `-XX:+UseSerialGC -Xmx512m -Xss384k` 与 `-Dkotlin.compiler.execution.strategy=in-process`
  后，`compileDebugAndroidTestKotlin` 成功。`assembleDebugAndroidTest` 需要 `kspDebugKotlin`
  真实重跑（Room 注解处理器），其 `Failed to reserve memory for metaspace`（mmap 失败）在 4GB
  vmem 上限下无法完成 → `BUILD FAILED`（假阴性，与代码无关；独立复核者可在 box 重跑）。

## 七、未验证项与遗留风险

1. **U05/P07/720p 全部 UNVERIFIED**：无模拟器，仅编译；运行行为（尤其 `duration==C.TIME_UNSET`、
   `videoSize`、`videoFrameRendered` 像素判据、进程 PID 语义）需真机/模拟器验证。
2. **U05 中文文案 + 真实 seek 零调用**：见 §二，已如实标 UNVERIFIED，未写假断言。
3. **720p 像素判据的鲁棒性**：`videoFrameRendered`（红色通道 > 18）与 `overlayRendered`（亮度 > 90）
   阈值依赖合成视频 fixture 的实际帧内容，需在模拟器上首次运行时校准；纯音频时视频帧区是否严格
   为黑也需实测确认。
4. **U05 主线程 build 的失败形态（UNVERIFIED）**：`build()` 若在 `runOnMainSync` 内抛异常，可能
   表现为 instrumentation 进程崩溃而非单条测试干净失败。
5. **P07 稳定窗口的残余风险（UNVERIFIED）**：若最后一次提交晚于某个已满 2,500 ms 的稳定窗口到达，
   仍可能被漏掉；末尾 `assertTrue(committed >= 13_000)` 与收敛条件基本重复。风险低，未在设备验证。
6. **P07 `untilProcessGone` 的 PID 语义（UNVERIFIED）**：依赖 `/proc/$pid` 对测试进程可见且 PID
   未被复用，需在设备上确认。
7. **U05 `@After` 中服务端线程 `join(2000)` 不做断言**：超时仅放弃等待，不使测试失败。
8. **注释语言混用**：`9536cec` 新增注释为英文，部分既有注释仍为中文（如「记录阶段 A PID…」）。

## 八、commit 列表

| SHA | 内容 |
|---|---|
| `d97877a` | R17-ST3.1b: U05/P07/720p 原生测试首版（独立复核 FAIL） |
| `9394937` | R17-ST3.1b 文档收尾 |
| `ab36e3f` | R17-ST3.2: 修复 U05 HTTP 头/恒真断言、P07 唯一日志文件名与超时失败（独立复核 FAIL：build 仍不在主线程、文档含绝对路径且安全检查结论不实） |
| `9536cec` | R17-ST3.3: U05 在主线程 build ExoPlayer；P07 提交收敛去掉 sleep、改为有界稳定窗口 |
| （本提交，见 git log） | R17-ST3.3: align st3b doc with final code（仅文档） |
