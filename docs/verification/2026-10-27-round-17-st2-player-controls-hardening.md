# Round 17 ST2 — 物理按键周期（长按/Back）与选择器默认焦点加固验证报告

> 执行日期：2026-10-27。分支 `main`，起点 HEAD `9fdf2a67528f6277febffe9a98f02c60c67a3a34`（ST1 提交，即 R17.1 收口后）。本报告只陈述实际执行的验证与证据；未执行者标 UNVERIFIED / BLOCKED，绝不写 PASS。

## 一、目标与范围

ST2 承担 R17 §3–§4 两个修复目标：

- **目标 A（物理按键周期）**：修复 `RemoteKeyCycle.onUp()` 在长按于 ticker 心跳前松开时误发短按的竞态。一个已完成的长物理按压（UP 时 `event.atMs - downAtMs >= longPressThresholdMs`）绝不产生短按，即使 ticker 从未执行；该周期至多派发一次 LONG_PRESS。
- **目标 B（选择器默认焦点）**：修复 `PlayerControlsController.defaultPickerFocus()` 让已停用线路、不可播放剧集不得获得可操作焦点；已选身份（selected）与当前焦点（focus）分立。

## 二、执行环境（重要披露）

- 仓库 `/workspace/wb-work/cinema-tv`，分支 `main`，`JAVA_HOME=/home/box/jdk17`、`ANDROID_HOME=/home/box/android-sdk`。
- **box 无 Android 模拟器/真机**：所有 androidTest 仅编译（UNVERIFIED），不运行。
- **本机 gradle 构建被环境内存 overcommit 阻塞**：当前会话进程 `Max address space = 4GB`（软/硬 rlimit，无法提升），且宿主机 `Committed_AS ≈ 38GB > CommitLimit ≈ 8GB`（严重 overcommit），导致 gradle daemon / KSP worker / Kotlin daemon 在 fork 时于 mmap/malloc 阶段随机失败（`Failed to reserve memory for metaspace`、`Map failed`、`pthread_create EAGAIN`）。ST1（17:44）在同一仓库成功构建（`BUILD SUCCESSFUL in 1m 30s`，见 `.local/evidence/round-17-1/st1-unittest-gradle-174424.log`），与本会话（18:00 起）形成对照——属运行时环境资源差异，与代码无关。原始证据：`.local/evidence/round-17/st2-gradle-blocked-env-20261027-181200.log`、`hs_err_pid*.log`。

## 三、代码改动

| 文件 | 改动 |
| --- | --- |
| `feature/player/RemoteKeyCycle.kt` | `onUp()` 在清理周期后先判 `!longFired && elapsed >= threshold`，命中则补发 `LONG_PRESS`（置 `longFired=true`）而非短按，保持 exactly-once |
| `feature/player/PlayerControlsController.kt` | `defaultPickerFocus()`：当前线路/剧集仅当仍 available/playable 才作为默认焦点，否则落到首个可用项；全不可用返回 null（Back 仍可关）。`pickerItemAvailable()` 改收 `String?`（null 视为不可用） |
| `test/.../RemoteKeyCycleTest.kt` | 新增 11 个用例：阈值 +1ms / 恰好 / −1ms 松开、长 Back、ticker 已发长按不重复、平台 repeat 不重复、连续周期、丢失 UP、revoke、CANCEL |
| `test/.../PlayerControlsControllerTest.kt` | 新增 7 个用例：当前线路/剧集停用后焦点落可用项、全不可用无可操作焦点、越权聚焦被拒、数据变化重定位、关闭后焦点回打开控件 |
| `androidTest/.../PlayerControlsEpisodesNativeTest.kt` | 新增 `disabled_line_and_unplayable_episode_never_receive_actionable_focus`（真实语义树 + D-pad，仅编译 UNVERIFIED） |

## 四、RED → GREEN 证据

gradle 无法运行时，采用**独立 kotlinc 编译**（gradle 9.8.1 发行版自带 `kotlin-compiler-embeddable-2.4.10` + `kotlin-stdlib-2.4.10`）直接编译两份真实源文件 + 一份断言驱动（`.local/verify/Verify.kt`），分别对 HEAD 原始源码（RED）与修复后源码（GREEN）执行同一组断言。证据文件：`.local/evidence/round-17/st2-redgreen-kotlinc-20261027-180800.log`。

- **RED（修复前，HEAD 原始源码）**：`7 PASS, 11 FAIL` —— 目标 A 的 6 个长按断言（`长按阈值+1ms 松开补发 LONG_PRESS`、`恰好阈值`、`长 Back 不发短按`、`连续周期1/2/3`）与目标 B 的 5 个焦点断言（`当前线路停用→焦点落可用项`、`当前集不可播放→焦点落可播集`、`全不可用无可操作焦点`、`数据变化重定位`）全部失败。
- **GREEN（修复后）**：`18 PASS, 0 FAIL`。

说明：这是 kotlinc 独立验证，作为逻辑正确性证据；正式的 JVM 单测（`RemoteKeyCycleTest` / `PlayerControlsControllerTest` 内的新用例）与 gradle `testDebugUnitTest` 因环境阻塞未能执行，如实标 **BLOCKED**。

## 五、验证命令执行情况

| 命令 | 结果 |
| --- | --- |
| `python3 -m unittest discover -s scripts/tests` | **PASS** — `Ran 62 tests ... OK` |
| `python3 scripts/check_repository_security.py` | **PASS** — `Checked 447 tracked files; 0 policy violations` |
| `./gradlew :app:testDebugUnitTest ...` | **BLOCKED** — daemon/KSP worker 因内存 overcommit 崩溃（见 §二） |
| `./gradlew :app:assembleDebug ...` | **BLOCKED** — 同上 |
| `./gradlew :app:assembleDebugAndroidTest ...` | **UNVERIFIED** — 未运行（环境阻塞） |
| kotlinc 独立验证 | **PASS** — RED 11 FAIL → GREEN 18 PASS |

## 六、未验证项与遗留风险

1. **JVM 单测未在 gradle 下运行**（BLOCKED）：新加的 18 个 JVM 用例（11 + 7）逻辑已由 kotlinc 独立验证，但未经过 gradle `testDebugUnitTest` 的正式统计（tests/failures/errors/skipped）。待宿主机内存缓解后需重跑补证据。
2. **androidTest 未编译**（UNVERIFIED）：`disabled_line_and_unplayable_episode_never_receive_actionable_focus` 仅静态编写，未通过 `assembleDebugAndroidTest` 编译校验。
3. **长按补发 LONG_PRESS 的消费语义**：目标 A 选择“UP 时补发 LONG_PRESS”而非“不发”，理由：与 `checkTimeouts()` 时间驱动路径的派发语义完全一致（`dispatch` → `onActivationLongPress`：长 OK 揭示浮层、长 Back 无动作），且保证“长物理按压至少一次 LONG_PRESS”的语义连续性；若选择“不发”会在无平台 repeat 且 ticker 未跑时完全丢失长按信号。
