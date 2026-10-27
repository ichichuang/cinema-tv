# Round 17 — 播放器可靠性加固 验证报告（在 main 上重做）

> 执行日期：2026-10-27。分支 `main`。起点 `6cd8bae64f24ed6ede85c125c4644a8810fef7e1`（R16）。
> 配套文件：`docs/superpowers/plans/2026-10-27-round-17-player-reliability.md`、
> `docs/verification/PLAYER-RELIABILITY-ACCEPTANCE-MATRIX.md`。
> 本报告只写本轮真实跑出来的结果；没有跑过的一律 UNVERIFIED / NOT RUN。

## 说明：本轮是重做，不是续写

R17 的原提交 `46e40ab` 只存在于原作者本地，远端开发分支仍停在 `6cd8bae`，该提交不可得、不可复核。
因此本报告的一切结论都从 `6cd8bae` 重新做出。原 R17 的「44 PASS / 1 FAIL / 1 BLOCKED」与
「P07 PID 4341 → 4430」作为历史记录保留在 §九附录，**不改写为通过**，也不作为本轮的数字来源。

## 一、起点与提交链

起点 `6cd8bae64f24ed6ede85c125c4644a8810fef7e1`；收口前 HEAD `48ac4a39792cd83beb61b9b9cff282d5a8bac845`。
`git log --oneline 6cd8bae..HEAD`（旧→新，共 18 笔，全部本地、未 push）：

| # | SHA | 内容 |
| --- | --- | --- |
| 1 | `9fdf2a67528f6277febffe9a98f02c60c67a3a34` | R17.1-ST1：等 `browseProbe` 发布后再轮询（R12 测试竞态） |
| 2 | `7d8addcf2b6d1023def0c333dd9aea64d6a844b9` | R17-ST2a：长按在 ticker 心跳前松开绝不误发短按 |
| 3 | `57a397dffd1b17712902f863bf6ec15062a1c05f` | R17-ST2b：选择器默认焦点不落停用线路/不可播剧集 |
| 4 | `548a066417227532f1b94375b29c8b417b3c23d5` | R17-ST2.1：修正断言类型、补齐 Back/选择器边界测试 |
| 5 | `58a3bbf2e735d6352f037ac8248d4dfce45daaa8` | R17-ST2.2：修正 ST2 验证文档 |
| 6 | `495860cefbc49458b0f2cc07eef08ff45bf35f07` | R17-ST3a：有序门（后被 ST3.1a-2 删除） |
| 7 | `321ab26e073f9b08ef79878bd3c521b924f3adf2` | R17-ST3b：U05/P07/720p 测试代码（仅编译） |
| 8 | `79204ef5131b8219d452199792287f40de9101c6` | docs：记录 ST3 证据与 U05/P07/720p 状态 |
| 9 | `fb3a4edf6f1d67cf2b2705774a19e331e7c2175c` | R17-ST3.1a：串行门 + `selectFromPlayer` 代次守卫 |
| 10 | `fb0b0d14e4af93d11f248576658fc40d69391ba3` | docs：ST3.1a 结果重写 |
| 11 | `b4564a1ea9bdb18bccf19e25805cf439a38711eb` | R17-ST3.1a-2：改为代次令牌 + probe 队列内过期检查 |
| 12 | `967f7be59ec286a2b992877d940df0593d13d488` | docs：ST3.1a-2 真实 RED→GREEN |
| 13 | `7382e831e2ec51ff6c3b0972c4da84e98f76b8ee` | docs：更正 ST3 说法与已知限制 |
| 14 | `d97877a64f7573544dbe69b6ae36f875cf5c8526` | R17-ST3b：U05/P07/720p 断言可判别化 |
| 15 | `93949372d7c213681e46313a88fc64336d0562fe` | docs：记录 ST3b SHA |
| 16 | `ab36e3f234fb270f8e9d28a793b54530c50b48b5` | R17-ST3.2：U05 线程/HTTP 头、P07 唯一日志名 |
| 17 | `9536ceccef4c3386bff7323f5576e815effee019` | R17-ST3.3：U05 主线程 build；P07 去掉 sleep |
| 18 | `48ac4a39792cd83beb61b9b9cff282d5a8bac845` | docs：ST3b 文档与最终代码对齐 |

本轮 ST4 的文档提交见 §十一：ST4 主提交 `709587d252bf0e71e23badcffab6da0ad4146b1f`、ST4 更正 `213024f25214f064d7299dfd73bd8469f5d93a35`，按独立复核清单所做的文档修正提交见 `git log` 末尾（提交无法写入自身 SHA）。

## 二、四类风险的处置（代码级）

### A. 物理按键周期（`RemoteKeyCycle.kt`，+8/−2）

`onUp()` 原来只看 `longFired`：长按在 ticker 心跳前松开时 `longFired==false`，于是发短按。现改为：

1. 清理周期后先判 `isActivationKey`；
2. `!longFired && elapsed >= longPressThresholdMs` → 置 `longFired = true` 并补发 `LONG_PRESS`；
3. 否则按 `!longFired` 决定是否发 `PRESS`。

补发而不是静默丢弃，是为了与 `checkTimeouts()` 的派发语义一致（长 OK 揭示浮层、长 Back 无动作），
保证「一个完整长物理按压至少一次 LONG_PRESS、且绝不产生短按」，并靠 `longFired` 保持 exactly-once。

### B. 选择器默认焦点（`PlayerControlsController.kt`，+12/−6）

`defaultPickerFocus()` 不再「当前项存在就给它焦点」，而是先用 `pickerItemAvailable()` 判断
当前线路/剧集是否仍可用：可用才作默认焦点，否则取第一个可用项；全部不可用返回 `null`。
`pickerItemAvailable()` 的参数类型放宽为 `String?`（`null` 身份视为不可用）。已选身份与焦点保持分立。

### C. 异步换集/换线路（`SelectionGeneration.kt`、`SwitchSelection.kt` 新增；`RoomPlaybackProbe.kt` +8/−1；`PlayerControlsRuntime.kt` +18/−9；`PersonalLibraryRuntime.kt` +33/−6）

- 新增纯 JVM 的 `SelectionGeneration`（`AtomicLong`，`reserve()` / `isLatest()`），作为唯一代次权威。
- `RoomPlaybackProbe` 持有该令牌，`select(..., generation: Long? = null)` 在**串行队列内、`verifyHandoffCatalog` /
  `bridge.accept` / `bridge.select` / `setMediaItem` 之前**检查过期，过期直接返回 `null`，不碰播放器与 Room。
- `PlayerControlsRuntime.selectTarget()` 在调用线程同步 `reserveSelection()`，再在协程内 `holdSwitch?.await()`
  后走 `selectFromPlayer(..., generation)`；失败且已过期时 trace `stale-drop:<gen>`；恢复
  `catch (_: Exception) { hint("切换未完成，原记录保持") }`，`CancellationException` 始终重抛。
- `PersonalLibraryRuntime` 新增 `reserveSelection()` / `isSelectionStale()`，`remember()` 移入 try。

### D. 证据类（U05 / P07 / 720p，测试代码）

- `PlayerControlsUnknownDurationNativeTest.kt`（新增 217 行）：回环 `http://` 直播流 + 真实 Media3 播放器，
  ExoPlayer 的 `build()` 与全部状态读取都在主线程（`runOnMainSync`），响应头不带 `Content-Length`/`Transfer-Encoding`。
- `PlayerControlsProcessReplayTest.kt`（+118/−?）：每次运行唯一日志文件名（`round17-process-<uuid>.json` + 指针文件），
  `untilProcessGone` 超时抛 `AssertionError`，提交收敛改为有界稳定窗口（无盲等）。
- `TvPlayerControlsScreenshotTest.kt`（+73）：`video_render_stages_distinct_from_overlay` 按
  解码初始化 / surface 附着 / 就绪 / 真实渲染帧 / 仅音频对照 五阶段断言。

## 三、RED → GREEN 证据

| 目标 | RED（改源码前） | GREEN（改源码后） | 可复核性 |
| --- | --- | --- | --- |
| A 长按误发短按 | 在 ST1 生产码（`9fdf2a6`）上只换入 ST2 测试：`52 tests completed, 11 failed`，其中 5 个是 `RemoteKeyCycleTest` 的 `expected LONG_PRESS but was PRESS` | `RemoteKeyCycleTest` 34 tests / 0 failures | box 上真实 gradle 运行（记录见 `docs/verification/2026-10-27-round-17-st2-player-controls-hardening.md` §四） |
| B 焦点落停用项 | 同一次 RED 中 `PlayerControlsControllerTest` 5 个失败（期望 `variant-b` 实得 `variant-a` 等） | `PlayerControlsControllerTest` 28 tests / 0 failures | 同上 |
| C 过期选择被拒 | 对**新写的** `SwitchSelection.perform` 做变异（删两处 `isStale()`）→ `7 tests completed, 3 failed` | 还原后 `7 tests, 0 failures` | **只有对新增类的区分力**；把三个生产文件还原到 `58a3bbf` 后 7 个测试仍全过，即**针对生产实现的 RED = UNVERIFIED** |
| D U05/P07/720p | 无（测试本身新增，代码未运行） | 无 | 仅编译，UNVERIFIED |

第 1、2 行是 ST2 阶段在 box 上跑出的真实 RED→GREEN（11 个失败里 1 个是测试自身断言类型写错，
已在 ST2.1 修正，不计为有效 RED）。第 3 行的限制已在
`docs/verification/2026-10-27-round-17-st3-ordered-selection.md` §5.2/§八 中如实记录，本报告不改写。

## 四、原生测试清单（R17.1 §5）

脚本对 `6cd8bae` 与 HEAD 两版逐文件 `git show` 后抽取 `@Test`：

- `6cd8bae`：397 个方法 / 47 个类 → HEAD：**400 个方法 / 48 个类**，净增 3、无删除。
- 新增：`PlayerControlsUnknownDurationNativeTest.loopback_live_stream_reports_unknown_duration_and_no_seek`、
  `TvPlayerControlsScreenshotTest.video_render_stages_distinct_from_overlay`、
  `PlayerControlsEpisodesNativeTest.disabled_line_and_unplayable_episode_never_receive_actionable_focus`。
- 播放器控制子家族：**53 个方法 / 9 个类**（详表见 R17.1 报告 §五）；起点 `6cd8bae` 同口径为 50 个方法 / 8 个类（独立复核枚举）。历史「49/49」不采用。
- 同类内重名 0；跨类同名 1（`user_input_cancels_late_focus_restore`，两个不同类各自执行）。

全部 400 个方法**只有编译证据，没有运行结果**：box 无模拟器，UNVERIFIED。

## 五、验证命令与真实输出

环境：JDK 17，`ANDROID_HOME`+`ANDROID_USER_HOME`+`GRADLE_USER_HOME` 显式指定，`--no-daemon --max-workers=1`。
沙箱对 `utimens`/部分构建输出有拦截，因此使用仓库内忽略目录的 `.local/tools/utimens_shim.so`（`LD_PRELOAD`）
与 `--no-build-cache`；这些是**环境变通，不是仓库改动**（`.local/` 已在 `.gitignore`）。

| 命令 | 关键输出 |
| --- | --- |
| `python3 -m unittest discover -s scripts/tests` | `Ran 62 tests in 7.947s` / `OK` |
| `python3 scripts/check_repository_security.py` | `Checked 460 tracked files; 0 policy violations`（staged 状态下运行） |
| `:app:assembleDebug`（普通，`--rerun-tasks` 后重跑） | `BUILD SUCCESSFUL`；执行方那一次构建的指纹：`app-debug.apk` 20 955 289 B，`sha256 bba0d1e5f32cd9629f1492fc7b2c774910726cc2c4a892e2a1d44d7d1bd8d581`（独立复核方在 box 上对同一提交构建得到 20 731 187 B、`sha256 c016194b33bb77589371f39dfe8bc8a74c06111d6e78e4173ae1742a0b010832`，两者不同，构建不可复现，该指纹仅代表那一次构建） |
| `:app:testDebugUnitTest --rerun` | `BUILD SUCCESSFUL in 31s`；XML 聚合 `files=50 tests=506 failures=0 errors=0 skipped=0` |
| `:app:assembleDebugAndroidTest` + `:app:compileDebugAndroidTestKotlin --rerun` | `BUILD SUCCESSFUL`（后者 1 个任务真实执行）；产物 `app-debug-androidTest.apk` |
| `:app:assembleRelease` | `BUILD SUCCESSFUL in 59s`；执行方那一次构建的指纹：`app-release-unsigned.apk` 14 956 049 B，`sha256 54737078aa9af9ee1cd11d438c6242b91c3323405c3768223e0b25b817d9cfbc`（独立复核方构建大小相同、`sha256 82a5bcde3c4aa8c54ea9a7d7f59445abc69eb9aee148391d50fe35c9f062c454`，SHA 不同，构建不可复现，该指纹仅代表那一次构建；包名/版本/ABI/未签名复核一致） |
| `apksigner verify`（Release） | `DOES NOT VERIFY` / `ERROR: Missing META-INF/MANIFEST.MF` → 未签名 |
| `:app:lintDebug :app:lintRelease` | `BUILD SUCCESSFUL in 3m 36s`；XML：debug 23 条（22 Warning + 1 Hint）、release 同样 23 条，**0 Error**，两套 ID 集合相同。注：23 条是联网时版本检查参与的结果；独立复核方离线（`--offline`）重跑为各 18 条（17 Warning + 1 Hint，0 Error），多出的 5 条均为版本检查类（`AndroidGradlePluginVersion` 1、`GradleDependency` 1、`NewerVersionAvailable` 3，位于 `gradle-wrapper.properties` / `build.gradle.kts`），代码类问题两者一致 |
| 八探针守护构建（严格任务对 + 8 个 `-Pcinema.*=true` + `--rerun-tasks`） | `BUILD SUCCESSFUL in 2m 2s`，`74 actionable tasks: 74 executed`（含 `kspDebugKotlin`、`compileDebugKotlin`、`assembleDebug`、`assembleDebugAndroidTest`） |
| `aapt2 dump badging`（Release） | `com.ichichuang.cinema`、`0.9.8`、`versionCode 18`、minSdk 24、targetSdk 28、`native-code: armeabi-v7a` |
| `git diff 6cd8bae -- app/schemas` | 空 |

八探针构建的产物：`app-debug.apk` 20 733 111 B（`sha256 97b0a74867ec2a1eacff6124162b2cb49637089c74be38db96b987c45cf5334a`）、
`app-debug-androidTest.apk` 2 003 878 B（`sha256 9270c5e7e88e5029a32d636f69b7f37b9238c31b0ee0ea50de0950a59930c40c`）。
这两个 SHA-256 已由独立复核方在 box 上用同一命令（`--rerun-tasks`）重建，逐字节一致。
该构建覆盖了先前的普通 Debug APK；普通 Debug 与 Release 的指纹不可复现，见上表注。

**Lint 与 R16 的对比**：23 条（离线 18 条）问题全部落在本轮未改动的文件（`SettingsScreen`、`PlayerViewModel`、
`gradle-wrapper.properties`、`app/build.gradle.kts`、图标资源等），**没有任何一条来自 R17 改动的文件**，
因此「无新增代码类问题」。

## 六、未验证项、BLOCKED 与 assumption skip

- **UNVERIFIED（已编译，未运行）**：全部 400 个 androidTest 方法，包括
  - U05 `loopback_live_stream_reports_unknown_duration_and_no_seek`；
  - P07 `PlayerControlsProcessReplayTest` 两阶段；
  - 720p `video_render_stages_distinct_from_overlay`；
  - 原生长 OK（`PlayerControlsKeyNativeTest.long_ok_does_not_fire_short_first`）、`disabled_line_and_unplayable_episode_never_receive_actionable_focus`、
    E05/E06 `holdSwitch` 屏障路径、各 `*OffNativeTest`；
  - R12 浏览回归（含 R17.1 修正的 `old_pending_layout_cannot_focus_after_new_query_is_installed`）。
  - 说明：长 Back 只有 JVM 测试（`RemoteKeyCycleTest`，见矩阵 K07），androidTest 中**没有**原生长 Back 测试方法。
- **BLOCKED**：无新增 BLOCKED 项。U05 与 P07 的状态从历史的「BLOCKED」改为
  **「代码已写、已编译、未运行（UNVERIFIED）」**——不是 PASS，也不再是「无代码」。
- **assumption skip**：R14 的 5 个 assumption skip 保持 skip，本轮未重跑，不计入 PASS。

> **口径矛盾披露**：ST4 任务正文的小验收写「U05 = BLOCKED」，而 ST4 补充说明第 3 条要求
> 「U05 现在有已编译的测试代码，状态写 UNVERIFIED，不要写 PASS，也不要沿用旧的『BLOCKED 无代码』说法」。
> 两者冲突，本报告按补充说明执行（补充说明声明优先），U05 记为 **UNVERIFIED（代码已写、已编译、未运行）**，
> 并在矩阵 E01 行同样标注，避免读者以为是 PASS。

## 七、剩余限制

### 7.1 生产代码残余（ST3 有序化，未消除）

1. 过期检查只在**进入** `RoomPlaybackProbe.select` 队列时做一次；已在队列中执行到 `bridge.accept`/`bridge.select`
   的旧操作仍可能走到 `setMediaItem`，只是不更新 `selectedMedia`（短暂状态分裂）。
2. 队列外的 `player.pause()` / `capture()` 在过期检查**之前**执行，过期操作仍会暂停播放器。
3. probe 内拒绝（`select` 返回 null）后，`SwitchSelection.perform` 不调用 `onStale`，提示可能保留旧文案。
4. 代次令牌放在可重建的 probe 里；`PersonalLibraryRuntime` 删除路径重建 probe 后计数归零，可能与进行中的旧代次撞号。
5. `PlayerControlsRuntime.selectTarget` 的 `reproject()` 在 try 之外。
6. 串行化依赖既有 `PlaybackEventQueue`：队列卡死会让后续切换一直排队；当前**没有任何超时保护**（原 Coordinator
   超时层已在 ST3.1a-2 整层删除）。

### 7.2 测试能力残余

- `SwitchSelectionTest` 只驱动新写的 `SwitchSelection` + 测试自写 `FakeProbe`，对生产实现无区分力（§三）。
- 没有「≥2 次反转完成顺序」的对抗测试；没有按变体隔离续播位置的测试；`playerPositionMs` 与 UI 选中项未被断言。
- 720p 像素阈值（红通道 > 18、亮度 > 90）依赖合成视频 fixture 的实际帧内容，需首次在设备上校准。
- P07 的稳定窗口、`/proc/$pid` 语义均需设备确认。

### 7.2a 引用：ST3b 文档的已知限制（UNVERIFIED，全部条目）

以下逐条引自 `docs/verification/2026-10-27-round-17-st3b-native-tests.md` §七，本轮均未在设备上验证：

1. U05/P07/720p 全部仅编译、未运行；`duration==C.TIME_UNSET`、`videoSize`、`videoFrameRendered` 像素判据、进程 PID 语义需真机/模拟器验证。
2. U05 的中文「时长未知」文案与真实 `seekTo` 零调用不在测试覆盖内，未验证。
3. 720p 像素判据（红通道 > 18、亮度 > 90）依赖合成 fixture 帧内容，需在设备上首次运行时校准；纯音频时视频帧区是否严格为黑需实测。
4. U05 的 ExoPlayer `build()` 在 `runOnMainSync` 内执行：若在主线程抛异常，可能表现为 instrumentation 进程崩溃，而非单条测试干净失败。
5. P07 稳定窗口：若最后一次提交晚于某个已满 2,500 ms 的稳定窗口到达仍可能漏掉；末尾 `assertTrue(committed >= 13_000)` 与收敛条件基本重复。
6. P07 `untilProcessGone` 依赖 `/proc/$pid` 对测试进程可见且 PID 未被复用。
7. U05 `@After` 中服务端线程 `join(2000)` 超时只放弃等待，不使测试失败。
8. 注释语言混用：ST3.3 新增注释为英文，部分既有注释仍为中文。

ST3 有序化文档（`docs/verification/2026-10-27-round-17-st3-ordered-selection.md` §八）的限制已汇总于 §7.1 与 §7.2。

### 7.3 环境

- box 无 Android 模拟器/真机 → 所有仪器化验证不可用。
- 沙箱对 `utimens` 与部分构建输出有拦截。`assembleRelease` 带 `--offline` 时曾失败，原因是当时 lint 工具
  （`androidLintTool` 配置）缓存未就绪；去掉 `--offline` 后成功。缓存就绪后，独立复核方带 `--offline` 也成功；`--rerun-tasks` + 构建缓存写盘失败时改用 `--no-build-cache`。

## 八、独立评审

**待编排方独立评审。** 本报告不声称已通过任何独立评审；ST2/ST3 报告中记录的是子任务阶段的复核过程，
不能替代对最终 HEAD 的独立评审。

## 九、附录：R17 历史结果的处置（2026-10-27 追加，不改写历史）

- 原提交 `46e40ab`：不可得，不可复核。
- 原结果「44 PASS / 1 FAIL / 1 BLOCKED」与「P07 PID 4341 → 4430」：保留为历史记录，
  **本轮不把它们改写为通过**，也不把其中的数字当作本轮基线。
- 原 R12 失败（`old_pending_layout_cannot_focus_after_new_query_is_installed`）在本轮以代码级修正处置，
  但因无设备，修复效果仍为 UNVERIFIED（见 R17.1 报告 §七）。

## 十、改动文件总表（`git diff --stat 6cd8bae..HEAD`）

生产代码 7 个（与 `git diff --name-only 6cd8bae..HEAD -- app/src/main` 一致）：

```
feature/player/RemoteKeyCycle.kt
feature/player/PlayerControlsController.kt
feature/player/PlayerControlsRuntime.kt
feature/player/RoomPlaybackProbe.kt
feature/player/SelectionGeneration.kt        （新增）
feature/player/SwitchSelection.kt            （新增）
feature/personal/PersonalLibraryRuntime.kt
```

测试 8 个：

```
test/.../RemoteKeyCycleTest.kt
test/.../PlayerControlsControllerTest.kt
test/.../SwitchSelectionTest.kt              （新增）
androidTest/.../BrowsePersistenceNativeTest.kt
androidTest/.../PlayerControlsEpisodesNativeTest.kt
androidTest/.../PlayerControlsProcessReplayTest.kt
androidTest/.../PlayerControlsUnknownDurationNativeTest.kt（新增）
androidTest/.../TvPlayerControlsScreenshotTest.kt
```

文档：`docs/verification/2026-10-27-round-17-st2-*.md`、`-st3-*.md`、`-st3b-*.md`，以及本轮新增的
计划/矩阵/报告各两份与进度文档追加条目。

未改动：`build.gradle.kts`、`app/build.gradle.kts`、`settings.gradle.kts`、`gradle/`、`gradle.properties`、
`gradlew*`、`app/schemas/`、`AndroidManifest.xml` 的生产入口。

## 十一、本轮 commit

staged 安全检查通过后本地提交（仅文档）。ST4 主提交 `709587d252bf0e71e23badcffab6da0ad4146b1f`、ST4 更正 `213024f25214f064d7299dfd73bd8469f5d93a35`，按独立复核清单所做的文档修正提交见 `git log` 末尾（提交无法写入自身 SHA）。
