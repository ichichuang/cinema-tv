# Round 17 ST2.1 — 物理按键周期（长按/Back）与选择器默认焦点加固验证报告

> 执行日期：2026-10-27。分支 `main`，起点 HEAD `57a397d`（ST2 原提交）。本报告只陈述实际执行的验证与证据；未执行者标 UNVERIFIED / BLOCKED，绝不写 PASS。

## 一、目标与范围

ST2 承担 R17 §3–§4 两个修复目标（本轮 ST2.1 为返工收口）：

- **目标 A（物理按键周期）**：修复 `RemoteKeyCycle.onUp()` 在长按于 ticker 心跳前松开时误发短按的竞态。一个已完成的长物理按压（UP 时 `event.atMs - downAtMs >= longPressThresholdMs`）绝不产生短按，即使 ticker 从未执行；该周期至多派发一次 LONG_PRESS。Back 键单独验证：每个有效物理周期至多一个逻辑导航动作，长 Back 绝不误发短 Back。
- **目标 B（选择器默认焦点）**：修复 `PlayerControlsController.defaultPickerFocus()` 让已停用线路、不可播放剧集不得获得可操作焦点；已选身份（selected）与当前焦点（focus）分立。

## 二、执行环境（重要披露）

- 仓库 `cinema-tv`，分支 `main`，`JAVA_HOME=$JAVA_HOME`、`ANDROID_HOME=$ANDROID_HOME`（均为 box 既有路径）。
- **box 无 Android 模拟器/真机**：所有 androidTest 仅编译（UNVERIFIED），不运行。
- **本机 gradle 构建受宿主内存 overcommit 影响**：宿主机 `Committed_AS` 显著高于 `CommitLimit`，沙箱 `vmemory`（地址空间）硬上限为 4GB 且不可提升，导致 gradle 测试 worker JVM 在运行**全量**测试时于 malloc/pthread_create 阶段随机失败（`Native memory allocation (malloc) failed to allocate 56 bytes`、`pthread_create failed (EAGAIN)`）。**单类/定向测试与编译可正常完成**（本报告 §四 定向测试 BUILD SUCCESSFUL、§五 编译通过）。全量 `testDebugUnitTest` 的完成度受限属运行时环境资源差异，与代码无关。

## 三、代码改动

| 文件 | 改动 |
| --- | --- |
| `feature/player/RemoteKeyCycle.kt` | **未改**（上一轮已正确：`onUp()` 在清理周期后先判 `!longFired && elapsed >= threshold` 命中则补发 LONG_PRESS；本轮复核确认无需改动） |
| `feature/player/PlayerControlsController.kt` | **未改**（上一轮已正确：`defaultPickerFocus()` 当前线路/剧集仅当仍 available/playable 才作默认焦点，否则落首个可用项，全不可用返回 null；`pickerItemAvailable()` 收 `String?`。本轮复核确认无需改动） |
| `test/.../RemoteKeyCycleTest.kt` | 修正/补齐：新增 Back 键完整边界套（阈值 −1ms / 恰好 / +1ms 松开、平台 repeat 有/无、早于阈值的 repeat 后阈值外松开、CANCEL、revoke()、连续物理周期）。R17 新增块的中文注释全部改英文 |
| `test/.../PlayerControlsControllerTest.kt` | 修正 line 341 断言类型（`pickerMove` 返回 `String?`）；补「对停用项 confirm 返回 None」真实断言；新增「选择器打开期间当前剧集被停用」与「选择器关闭后焦点回调用控件（含真实数据变化）」两个用例。R17 新增块的中文注释全部改英文 |
| `androidTest/.../PlayerControlsEpisodesNativeTest.kt` | 收紧 `disabled_line_and_unplayable_episode_never_receive_actionable_focus`：移除无法进入播放层的 w4 分支（fixture 的 w4 只有一条 DISABLED 线路且 uri=null，`primaryActionEnabled=false`，OK 无法进入播放层），保留 w0 可用线路 + 停用线路 c/d 不聚焦的语义树验证；「全部线路不可用」分支如实标 UNVERIFIED（仅 JVM 层覆盖） |

## 四、RED → GREEN 证据

**RED（改前，HEAD `57a397d`）**：`PlayerControlsControllerTest.disabled_line_never_becomes_focus_even_on_confirm` 在 line 341 失败——`pickerMove(PlayerDirection.DOWN)` 返回 `String?`（唯一可用项时返回 `"picker:variant-a"`），却被断言成 `PlayerControlCommand.None`。失败原文：

```
java.lang.AssertionError: expected:<None> but was:<picker:variant-a>
    at ...PlayerControlsControllerTest.disabled_line_never_becomes_focus_even_on_confirm(PlayerControlsControllerTest.kt:341)
```

（证据文件：ST1 基线产物 `app/build/test-results/testDebugUnitTest/TEST-com.ichichuang.cinema.feature.player.PlayerControlsControllerTest.xml`，`tests=26 failures=1`，该失败即上述断言。）

**GREEN（改后）**：定向运行两个受影响的测试类，全部通过：

```
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 22s
```

结果统计（自 `app/build/test-results/testDebugUnitTest/*.xml`）：

| 测试类 | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| `RemoteKeyCycleTest` | 34 | 0 | 0 | 0 |
| `PlayerControlsControllerTest` | 28 | 0 | 0 | 0 |

## 五、验证命令执行情况

| 命令 | 结果 |
| --- | --- |
| `python3 -m unittest discover -s scripts/tests` | **PASS** — `Ran 62 tests ... OK` |
| `python3 scripts/check_repository_security.py` | **PASS** — `Checked 448 tracked files; 0 policy violations`（本轮修正文档后绝对路径违规消除） |
| 定向 `testDebugUnitTest`（两个测试类） | **PASS** — `BUILD SUCCESSFUL`，62 个用例 0 失败（见 §四） |
| 全量 `testDebugUnitTest` | **PARTIAL/BLOCKED** — 测试 worker JVM 因宿主内存 overcommit 随机崩溃（`malloc failed` / `pthread_create EAGAIN`），无法完整统计 489 个用例；见 §二 |
| `./gradlew :app:assembleDebugAndroidTest` | **UNVERIFIED** — 本轮尚未完成（box 无模拟器，仅需编译） |

## 六、未验证项与遗留风险

1. **全量 JVM 单测总数**：因宿主内存 overcommit，本沙箱无法完成全量 `testDebugUnitTest`（worker 随机崩溃）。定向的两类已 GREEN（62 用例 0 失败）。全量总数待独立复核者在 box 上重跑确认。
2. **androidTest 未在模拟器运行**（UNVERIFIED）：`disabled_line_and_unplayable_episode_never_receive_actionable_focus` 仅编译校验，未运行；「全部线路不可用」分支因 fixture 无法进入播放层而仅保留 JVM 覆盖，如实标 UNVERIFIED。
3. **长按补发 LONG_PRESS 的消费语义**：目标 A 选择「UP 时补发 LONG_PRESS」而非「不发」，理由：与 `checkTimeouts()` 时间驱动路径的派发语义完全一致（`dispatch` → 长 OK 揭示浮层、长 Back 无动作），且保证「长物理按压至少一次 LONG_PRESS」的语义连续性；若选「不发」会在无平台 repeat 且 ticker 未跑时完全丢失长按信号。
