# Round 17 ST2.1 — 物理按键周期（长按/Back）与选择器默认焦点加固验证报告

> 执行日期：2026-10-27。分支 `main`，ST2 起点 HEAD `57a397d`，本文修订时 HEAD `548a066`（ST2.1 修正后）。本报告只陈述实际执行的验证与证据；未执行者标 UNVERIFIED / BLOCKED，绝不写 PASS。

> **证据来源说明**：本报告中的 RED / GREEN 运行结果与耗时，均由独立复核者在 box 上用真实 gradle 亲自运行得到（在临时 worktree 中进行，不改动 `main`），原始记录为独立复核日志（box 上的 red.log / green.log）。本文作者所在的 WorkBuddy 沙箱没有可用的 gradle 构建条件（见 §二），因此**不**引用本机 `app/build/` 下的任何产物作为证据——那些 `*.xml` 会被后续运行覆盖，不能作为可复核的固定证据。

## 一、目标与范围

ST2 承担 R17 §3–§4 两个修复目标（本轮 ST2.1 为返工收口）：

- **目标 A（物理按键周期）**：修复 `RemoteKeyCycle.onUp()` 在长按于 ticker 心跳前松开时误发短按的竞态。一个已完成的长物理按压（UP 时 `event.atMs - downAtMs >= longPressThresholdMs`）绝不产生短按，即使 ticker 从未执行；该周期至多派发一次 LONG_PRESS。Back 键单独验证：每个有效物理周期至多一个逻辑导航动作，长 Back 绝不误发短 Back。
- **目标 B（选择器默认焦点）**：修复 `PlayerControlsController.defaultPickerFocus()` 让已停用线路、不可播放剧集不得获得可操作焦点；已选身份（selected）与当前焦点（focus）分立。

## 二、执行环境（重要披露）

- 仓库 `cinema-tv`，分支 `main`；`JAVA_HOME`、`ANDROID_HOME` 均为 box 上的既有环境，未做额外设置。
- **box 无 Android 模拟器/真机**：所有 androidTest 仅编译（UNVERIFIED），不运行。
- **独立复核**：复核者在 box 上使用真实 gradle，未加载任何 shim；为不影响 `main`，RED 运行放在临时 worktree 中进行（只换入测试文件，生产代码保持 ST1 版本）。
- **作者沙箱的 gradle 只能部分运行**：显式指定 `GRADLE_USER_HOME` 等环境变量后 gradle 可以启动，**定向测试能真实执行并产出结果**；但构建收尾写 `build/reports/problems-report.html` 时被沙箱文件系统拒绝（`Operation not permitted`），gradle 最终以 `BUILD FAILED` 结束——失败发生在**报告写入阶段**，此时测试结果 XML 已正常生成。另外，全量 `testDebugUnitTest` 曾受宿主机内存 overcommit 影响（宿主机 `Committed_AS` 显著高于 `CommitLimit`，沙箱地址空间硬上限 4GB 且不可提升），测试 worker JVM 在 malloc / pthread_create 阶段随机失败（`Native memory allocation (malloc) failed to allocate 56 bytes`、`pthread_create failed (EAGAIN)`）。**这些都是环境限制，不是代码问题**；因此本报告的结论以 box 上的真实运行为准，沙箱结果只作旁证。

## 三、代码改动

| 文件 | 改动 |
| --- | --- |
| `feature/player/RemoteKeyCycle.kt` | **本子任务未改**（ST2 阶段修正后已正确：`onUp()` 在清理周期后先判 `!longFired && elapsed >= threshold`，命中则补发 LONG_PRESS；ST2.1 复核确认无需改动） |
| `feature/player/PlayerControlsController.kt` | **本子任务未改**（ST2 阶段修正后已正确：`defaultPickerFocus()` 的当前线路/剧集仅当仍 available/playable 才作默认焦点，否则落首个可用项，全不可用返回 null；`pickerItemAvailable()` 收 `String?`；ST2.1 复核确认无需改动） |
| `test/.../RemoteKeyCycleTest.kt` | 修正/补齐：新增 Back 键完整边界套（阈值 −1ms / 恰好 / +1ms 松开、平台 repeat 有/无、早于阈值的 repeat 后阈值外松开、CANCEL、`revoke()`、连续物理周期）。R17 新增块的中文注释全部改英文 |
| `test/.../PlayerControlsControllerTest.kt` | 修正 `pickerMove` 断言类型（返回 `String?` 而非 `PlayerControlCommand`）；把原先语义偏宽的「停用项 confirm 返回 None」改为准确断言：**当所有线路都不可用时焦点为 null，此时 `confirm()` 返回 `None`，且确认后选择器仍停在线路层、焦点仍为 null**（`all_lines_unavailable_leave_no_actionable_focus`）。停用项本身永远不会获得焦点，因此不存在「对停用项确认」这条路径。另新增「选择器打开期间当前剧集被停用」与「选择器关闭后焦点回调用控件（含真实数据变化）」两个用例。R17 新增块的中文注释全部改英文 |
| `androidTest/.../PlayerControlsEpisodesNativeTest.kt` | 收紧 `disabled_line_and_unplayable_episode_never_receive_actionable_focus`：移除无法进入播放层的 w4 分支（fixture 的 w4 只有一条 DISABLED 线路且 uri=null，`primaryActionEnabled=false`，OK 无法进入播放层），保留 w0 可用线路 + 停用线路 c/d 不聚焦的语义树验证；「全部线路不可用」分支如实标 UNVERIFIED（仅 JVM 层覆盖） |

## 四、RED → GREEN 证据

### 4.1 构造方式

在 **ST1 的生产代码（`9fdf2a6`）** 上，只换入 ST2 新增/修改的测试文件，生产代码保持 ST1 版本，在 box 上运行 `:app:testDebugUnitTest`：

```
52 tests completed, 11 failed
BUILD FAILED in 32s
```

11 个失败中 **10 个是有效 RED**（正好对应 ST2 的 10 处生产修复），第 11 个是测试自身的断言写错，不计入有效 RED（详见 4.3）。

### 4.2 有效 RED（10 个）

**`RemoteKeyCycleTest` — 5 个**，失败信息均为 `expected LONG_PRESS but was PRESS`（长按已越过阈值却在 ticker 未跑到时发出了短按）：

| 测试 |
| --- |
| `long_release_before_ticker_1ms_above_threshold_emits_long_not_short` |
| `long_release_exactly_at_threshold_emits_long_not_short` |
| `long_back_release_before_ticker_never_emits_short_back` |
| `consecutive_long_cycles_each_emit_exactly_one_long_press` |
| `lost_up_then_long_release_does_not_ghost_extra_activation` |

**`PlayerControlsControllerTest` — 5 个**，失败原因是焦点落在已停用 / 不可播放的当前项，或应当为 null 却非 null：

| 测试 | 失败表现 |
| --- | --- |
| `default_line_focus_skips_selected_line_turned_unavailable` | 期望 `variant-b`，实得 `variant-a` |
| `default_episode_focus_skips_selected_episode_turned_unplayable` | 期望 `ep-2`，实得 `ep-1` |
| `all_lines_unavailable_leave_no_actionable_focus` | 期望 `null`，实得 `variant-a` |
| `all_episodes_unplayable_leave_no_actionable_focus` | 期望 `null`，实得 `ep-1` |
| `data_change_disabling_focused_line_repositions_focus_to_available` | 期望 `variant-b`，实得 `variant-a` |

### 4.3 第 11 个失败（非有效 RED，测试自身错误）

`PlayerControlsControllerTest.disabled_line_never_becomes_focus_even_on_confirm`：该用例把 `pickerMove(PlayerDirection.DOWN)`（返回 `String?`，唯一可用项时为 `"picker:variant-a"`）的返回值直接断言成 `PlayerControlCommand.None`，属于**断言类型写错**，不是生产代码缺陷。因此它虽然在 RED 运行中失败，但**不计为有效 RED**；该错误已在 ST2.1 中修正（把断言改为对 `String?` 焦点值的比较）。

### 4.4 另有 6 个新测试在 RED 上本来就通过（回归保护，不是 RED）

这些用例针对的是修复前已成立的行为，作用是防止后续回归，因此在 RED 基线上即为绿色：

- `release_1ms_below_threshold...`（阈值下 1ms 松开仍发短按）
- `...after_ticker_already_fired...`（ticker 已触发后再松开为静默）
- `...platform_repeat_does_not_double`（平台 repeat 不重复派发）
- `...revoke...`（revoke 撤销周期后不再激活）
- `...cancel...`（CANCEL 撤销周期，不发长按）
- `close_picker_then_focus_returns...`（关闭选择器后焦点回调用控件）

### 4.5 修复后的定向运行

在 `57a397d`（ST2 修复提交）上运行同一命令 `:app:testDebugUnitTest`：

```
52 tests completed, 1 failed
BUILD FAILED in 21s
```

仅剩的 1 个失败就是 4.3 中那个测试自身断言错误，说明 10 处生产修复已全部转绿；该测试错误由 ST2.1 修正。

### 4.6 GREEN（ST2.1 之后，`548a066`）

独立复核者在 `548a066` 上运行 `:app:testDebugUnitTest --rerun`（不使用 shim）：

```
BUILD SUCCESSFUL in 35.7s
```

按测试类统计：

| 测试类 | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| `RemoteKeyCycleTest` | 34 | 0 | 0 | 0 |
| `PlayerControlsControllerTest` | 28 | 0 | 0 | 0 |

> 耗时口径统一说明：ST2 定向运行（`57a397d`，1 失败）为 **21 秒**；ST2.1 修正后（`548a066`，`--rerun`）为 **`BUILD SUCCESSFUL in 35.7s`**。二者是不同提交、不同命令范围的两次真实运行，不可混用。

## 五、验证命令执行情况

| 命令 | 结果 |
| --- | --- |
| `:app:testDebugUnitTest --rerun`（`548a066`，box，无 shim） | **PASS** — 499 个测试，0 失败、0 错误、0 跳过；共 49 个测试类，`BUILD SUCCESSFUL in 35.7s` |
| `:app:assembleDebug`（box） | **PASS** — `BUILD SUCCESSFUL` |
| `:app:assembleDebugAndroidTest --rerun-tasks`（box） | **PASS（仅编译）** — `BUILD SUCCESSFUL in 1m10s`；未在设备/模拟器运行，运行结果 **UNVERIFIED** |
| `python3 -m unittest discover -s scripts/tests` | **PASS** — `Ran 62 tests ... OK`（本文作者本次实际运行，2026-10-27） |
| `python3 scripts/check_repository_security.py` | **PASS** — `Checked 448 tracked files; 0 policy violations`（本文作者本次实际运行，2026-10-27；提交后若跟踪文件数变化，以当次运行为准） |
| 定向 `:app:testDebugUnitTest --tests '*RemoteKeyCycleTest*' --tests '*PlayerControlsControllerTest*'` | **测试通过（旁证）** — 本文作者本次实际运行，测试结果 XML：`RemoteKeyCycleTest` 34 tests / 0 failures / 0 errors / 0 skipped，`PlayerControlsControllerTest` 28 tests / 0 failures / 0 errors / 0 skipped（共 62）。gradle 收尾写 problems-report 时被沙箱文件系统拒绝（`Operation not permitted`），故进程退出码非零，失败在报告写入阶段而非测试断言 |
| 作者沙箱内的全量 `:app:testDebugUnitTest` | **无法完成** — 受宿主内存 overcommit 限制，worker JVM 随机 malloc / pthread_create 失败；全量总数以 box 上 499 为准，见 §二 |

## 六、已知小缺口

1. **「早于阈值的 repeat、在阈值之后松开」只有 Back 键版本**：`RemoteKeyCycleTest` 为该组合写了 Back 用例，没有写 OK 键的对应用例。两者走同一段 `RemoteKeyCycle` 代码路径，逻辑上等价，但 OK 键这一侧缺少显式回归保护。
2. **「全部线路 / 剧集不可用」只覆盖到 JVM 层**：`all_lines_unavailable_leave_no_actionable_focus`、`all_episodes_unplayable_leave_no_actionable_focus` 均为 JVM 单元测试。原生层（语义树 + D-pad）没有对应覆盖，native 侧 **UNVERIFIED**。
3. **所有 androidTest 均未在设备上运行**：仅做编译校验（`assembleDebugAndroidTest` BUILD SUCCESSFUL），运行层面的行为 **UNVERIFIED**。

## 七、未验证项与遗留风险

1. **androidTest 未在模拟器/真机运行**（UNVERIFIED）：`disabled_line_and_unplayable_episode_never_receive_actionable_focus` 仅编译校验；「全部线路不可用」分支因 fixture 无法进入播放层而只保留 JVM 覆盖，如实标 UNVERIFIED。
2. **原生层焦点/按键语义未验证**：语义树与 D-pad 路径依赖设备运行，见 §六 第 2、3 条。
3. **长按补发 LONG_PRESS 的消费语义**：目标 A 选择「UP 时补发 LONG_PRESS」而非「不发」，理由：与 `checkTimeouts()` 时间驱动路径的派发语义完全一致（`dispatch` → 长 OK 揭示浮层、长 Back 无动作），且保证「长物理按压至少一次 LONG_PRESS」的语义连续性；若选「不发」，会在无平台 repeat 且 ticker 未跑时完全丢失长按信号。
