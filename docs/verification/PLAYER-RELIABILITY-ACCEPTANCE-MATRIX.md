# Round 17 — 播放器可靠性 验收矩阵

> 日期：2026-10-27。分支 `main`，起点 `6cd8bae64f24ed6ede85c125c4644a8810fef7e1`（R16）。
> 本矩阵把 R17 的每一条可测试声明对应到真实测试方法/命令与证据。
>
> **状态口径（严格执行）**：
> - `PASS` 只用于本机（box）本轮**真实执行过**的验证：JVM 单测、Gradle 构建、Python 测试、
>   安全检查、以及对源码/清单**跑脚本**得到的结构性事实（计数、diff 为空、badging）。
> - 一切需要 Android 设备/模拟器的声明（全部 `androidTest`）一律 `UNVERIFIED`——**已编译，未运行**。
> - 没有对应测试、或测试对生产实现无区分力的声明写 `NOT RUN` / `UNVERIFIED` 并说明原因。
> - R14 的 5 个 assumption skip 保持 `SKIP`，永不升级为 PASS。
>
> 证据码：`JVM` / `BUILD` / `SEC` / `STATIC` / `NATIVE`（本轮不可用）/ `REPORT`。

## G — 基线、边界与不变量（6）

| ID | 可测试的声明 | 对应真实方法 / 证据 | 状态 |
| --- | --- | --- | --- |
| G01 | 起点为 `6cd8bae`，全部改动都在 `main` 上、未 push | `git log --oneline 6cd8bae..HEAD`；`git status --short` 为空 | PASS (STATIC) |
| G02 | 未改构建配置/依赖版本 | `git diff --stat 6cd8bae..HEAD` 不含 `*.gradle.kts`、`gradle/`、`gradle.properties`、`gradlew*` | PASS (STATIC) |
| G03 | Room schema v1–v6 相对起点无变化 | `git diff 6cd8bae -- app/schemas` 输出为空 | PASS (STATIC) |
| G04 | 包名/版本/minSdk/targetSdk/ABI 未变，Release 未签名 | `aapt2 dump badging` × 2；`apksigner verify` → `DOES NOT VERIFY` | PASS (BUILD) |
| G05 | 单一 ExoPlayer / 单一 checkpoint 协调者 / Room 唯一写入方未被破坏 | 《R17 ST3 有序化报告》§三 + 本轮 diff 审阅（未新增第二个写入者/计时器） | PASS (STATIC) |
| G06 | 普通 Debug/Release 不启用新界面（探针默认 OFF） | 既有 `*OffNativeTest` 仅编译；普通 Debug APK 构建通过 | UNVERIFIED（native 需设备） |

## K — 物理按键周期（9）

| ID | 可测试的声明 | 对应真实方法 | 状态 |
| --- | --- | --- | --- |
| K01 | 长 OK 在 ticker 心跳前松开（恰在阈值 / 阈值 +1 ms）绝不发短按 | `RemoteKeyCycleTest.long_release_exactly_at_threshold_emits_long_not_short`、`long_release_before_ticker_1ms_above_threshold_emits_long_not_short` | PASS (JVM) |
| K02 | 阈值 −1 ms 松开仍发短按（边界另一侧未被误改） | `RemoteKeyCycleTest.release_1ms_below_threshold_still_emits_short` | PASS (JVM) |
| K03 | 有平台 repeat 时，UP 前补发不导致重复 LONG_PRESS | `long_release_before_ticker_then_platform_repeat_does_not_double` | PASS (JVM) |
| K04 | 无平台 repeat 时按 elapsed 判长按，且不先发短按 | `long_ok_by_elapsed_threshold_fires_once_without_platform_repeat`、`long_ok_with_zero_initial_repeat_count_never_fires_short_first` | PASS (JVM) |
| K05 | 连续多个长周期各自恰好一次 LONG_PRESS | `consecutive_long_cycles_each_emit_exactly_one_long_press` | PASS (JVM) |
| K06 | 丢失 UP / 孤儿 UP / 重复 DOWN / CANCEL / 第二键按下 均有界且无幽灵激活 | `lost_up_then_new_down_does_not_ghost_double_activation`、`lost_up_then_long_release_does_not_ghost_extra_activation`、`orphan_up_without_down_is_ignored`、`action_cancel_revokes_cycle_without_activation`、`second_key_down_while_one_held_is_ignored` | PASS (JVM) |
| K07 | 长 Back 绝不误发短 Back（含阈值 −1/恰好/+1 ms、repeat、CANCEL、revoke） | `long_back_release_before_ticker_never_emits_short_back`、`back_release_1ms_below_threshold_emits_short_back`、`back_release_exactly_at_threshold_emits_long_not_short`、`back_release_1ms_above_threshold_emits_long_not_short`、`back_platform_repeat_past_threshold_fires_long_in_down_phase`、`back_repeat_before_threshold_then_release_after_threshold_emits_single_long`、`back_cancel_revokes_cycle_without_activation` | PASS (JVM) |
| K08 | 连续 Back 周期每周期恰好一次逻辑激活 | `consecutive_back_cycles_each_emit_exactly_one_activation` | PASS (JVM) |
| K09 | 真实窗口按键流（DOWN/repeat/UP）下长 OK 不先发短 OK、每周期一次 | `PlayerControlsKeyNativeTest.long_ok_does_not_fire_short_first`、`rapid_ok_and_back_deduplicated`、`picker_trap_back_unwinds_and_restores_opener` | UNVERIFIED |

## F — 选择器默认焦点（6）

| ID | 可测试的声明 | 对应真实方法 | 状态 |
| --- | --- | --- | --- |
| F01 | 已选线路变停用后，默认焦点跳到仍可用线路而非停用项 | `PlayerControlsControllerTest.default_line_focus_skips_selected_line_turned_unavailable` | PASS (JVM) |
| F02 | 当前剧集变不可播后，默认焦点跳到可播剧集 | `default_episode_focus_skips_selected_episode_turned_unplayable` | PASS (JVM) |
| F03 | 选择器打开期间数据变化使焦点项停用 → 焦点重定位到可用项 | `episode_turned_unplayable_while_picker_open_repositions_focus`、`data_change_disabling_focused_line_repositions_focus_to_available` | PASS (JVM) |
| F04 | 全部线路/剧集不可用时焦点为 null，OK 不产生动作，选择器仍停留原层（Back 仍可退出） | `all_lines_unavailable_leave_no_actionable_focus`、`all_episodes_unplayable_leave_no_actionable_focus`、`disabled_line_never_becomes_focus_even_on_confirm` | PASS (JVM) |
| F05 | 关闭选择器后焦点回到真实调用控件（含真实数据变化后） | `close_picker_then_focus_returns_to_opener_control`、`picker_close_after_real_data_change_returns_focus_to_opener` | PASS (JVM) |
| F06 | 真实 Compose 语义树 + D-pad 下停用线路/不可播剧集拿不到可操作焦点 | `PlayerControlsEpisodesNativeTest.disabled_line_and_unplayable_episode_never_receive_actionable_focus` | UNVERIFIED |

## S — 异步换集/换线路（8）

| ID | 可测试的声明 | 对应真实方法 / 证据 | 状态 |
| --- | --- | --- | --- |
| S01 | 代次令牌单调保留，只有最新代次算「最新」 | `SwitchSelectionTest.generation_reserves_monotonically_and_is_latest_only_for_newest` | PASS (JVM) |
| S02 | 过期选择在进入 probe 之前就被拒绝，且不饿死新选择 | `superseded_selection_is_rejected_before_entering_the_probe`、`rejected_selection_does_not_starve_a_fresh_one` | PASS (JVM)，**区分力有限**：只驱动 `SwitchSelection` + 测试自写 `FakeProbe` |
| S03 | 权威过期检查位于 `RoomPlaybackProbe.select` 串行队列内、任何播放器/Room 变更之前 | `RoomPlaybackProbe.select(...)` 源码（`git diff 6cd8bae..HEAD`）；**无针对生产实现的 RED** | UNVERIFIED |
| S04 | 过期旧操作不得在新选择之后重新绑定 Media3 会话/写入过期 checkpoint | 需仪器化 + 真实 Media3 | UNVERIFIED |
| S05 | 「≥2 次反转完成顺序」的对抗测试 | **未写**：现有 `two_rapid_selections_inverted_only_newest_mutates` 按正常顺序放行（ST3 报告 §5.2/§八 已承认） | NOT RUN |
| S06 | 不同线路各自独立续播位置，位置不跨变体串用 | **无对应测试** | NOT RUN |
| S07 | 换前检查点屏障（E05）在真实切换中仍成立 | `PlayerControlsEpisodesNativeTest` 的 `holdSwitch` 路径 | UNVERIFIED |
| S08 | `CancellationException` 始终向外传播，不被吞 | `PlayerControlsRuntime.selectTarget` 的 `catch (cancelled: CancellationException) { throw cancelled }`（源码） | UNVERIFIED（无专门 JVM 测试） |

## E — U05 / P07 / 720p 证据（5）

| ID | 可测试的声明 | 对应测试代码 | 状态 |
| --- | --- | --- | --- |
| E01 | U05：回环未知时长流下 `duration == C.TIME_UNSET`、绝对 seek 命令不可用、时间轴禁用、OK 不产生 SeekTo | `PlayerControlsUnknownDurationNativeTest.loopback_live_stream_reports_unknown_duration_and_no_seek` | UNVERIFIED（代码已写、已编译、未运行） |
| E02 | P07：两阶段强制停止后新 PID、身份一致、恢复到已提交位置、保持暂停 | `PlayerControlsProcessReplayTest`（唯一日志文件名 + 有界收敛，无盲等） | UNVERIFIED（已编译、未运行） |
| E03 | 720p：解码初始化 / surface 附着 / 就绪 / 真实渲染帧四阶段可与叠层区分 | `TvPlayerControlsScreenshotTest.video_render_stages_distinct_from_overlay` | UNVERIFIED（已编译、未运行） |
| E04 | U05 的「中文时长未知文案」与「真实 `seekTo` 零调用」 | 明确**不在** U05 测试覆盖内（测试内已声明） | UNVERIFIED |
| E05 | 本轮 P07 的 PID 证据 | 无设备，未取得任何新 PID | NOT RUN（历史 4341→4430 不可复核，不改写为通过） |

## V — 视频渲染与历史证据复核（2）

| ID | 可测试的声明 | 证据 | 状态 |
| --- | --- | --- | --- |
| V01 | 720p 视频渲染缺陷是否已复现/关闭 | 无设备、无新截图 | UNVERIFIED |
| V02 | R16 V01/V03/R03 文本与实际截图的差异已逐条重核 | 本轮未取得新截图，未做像素复核 | NOT RUN |

## R — 回归、构建与交付（10）

| ID | 可测试的声明 | 命令 / 证据 | 状态 |
| --- | --- | --- | --- |
| R01 | JVM 全量单测通过，总数来自 JUnit XML | `:app:testDebugUnitTest --rerun` → XML 聚合 | PASS (JVM) |
| R02 | Python 测试通过 | `python3 -m unittest discover -s scripts/tests` | PASS |
| R03 | 仓库安全检查 0 违规（staged） | `python3 scripts/check_repository_security.py` | PASS (SEC) |
| R04 | 普通 Debug APK 构建通过 | `:app:assembleDebug` | PASS (BUILD) |
| R05 | androidTest 源码编译通过 | `:app:assembleDebugAndroidTest`、`:app:compileDebugAndroidTestKotlin --rerun` | PASS (BUILD)，运行 UNVERIFIED |
| R06 | 未签名 Release 构建通过 | `:app:assembleRelease` | PASS (BUILD) |
| R07 | Debug/Release Lint 无错误、无本轮新增代码类问题 | `:app:lintDebug :app:lintRelease` + XML 逐条定位 | PASS (BUILD) |
| R08 | 八探针守护构建（严格任务对 + 八个 `-Pcinema.*=true`）通过 | `74 actionable tasks: 74 executed` / `BUILD SUCCESSFUL` | PASS (BUILD) |
| R09 | 原生测试清单（类/方法/新增/重名）可复核 | 枚举脚本 + `git show 6cd8bae` 对比 | PASS (STATIC) |
| R10 | 独立评审 | 编排方另行安排 | 待编排方独立评审 |

## 计数与会计

- 本矩阵共 46 行：`PASS` 29、`UNVERIFIED` 12、`NOT RUN` 4、`待编排方独立评审` 1、`FAIL` 0、
  `SKIP`（R14 历史 assumption，另计 5，不计入 PASS）。
- 需要模拟器的行：K09、F06、S03、S04、S07、S08、E01–E04、V01、G06 → 全部不是 PASS。
- R14 的 5 个 assumption skip 保持 SKIP，未计入 PASS。

> 边界：本矩阵只覆盖播放器可靠性切片（且仍处于私有 Debug 授权链之后）。它不关闭 T10/T11/M04、
> 旧用户升级、物理 ARMv7/API24 电视、真实 CMS 与发布/签名门禁。

