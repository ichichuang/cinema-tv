# Round 17 ST3.1a — 换集/换线路有序化（runtime/JVM 层）验证报告

> 执行日期：2026-10-27。分支 `main`，ST3.1a 起点 HEAD `79204ef`（含上一轮 ST3 的 `495860c`/`321ab26`/`79204ef`，未通过独立复核）。
> 本报告只陈述实际执行的验证与证据；未执行者标 UNVERIFIED / BLOCKED，绝不写 PASS。ST3.1b（原生 U05/P07/720p）不在本轮范围，仍为 BLOCKED/UNVERIFIED。

## 一、目标与范围

ST3.1a 只做 **runtime / JVM 层**的换集/换线路有序化返工，目标 B（U05/P07/720p 原生测试）留给后续 ST3.1b，本轮不碰。

- **目标 A（换集/换线路有序化）**：`PlayerControlsRuntime.selectTarget()` 与 `PersonalLibraryRuntime.selectFromPlayer()` 的 generation 检查原先发生在 await 选择操作之后，旧操作可能在更新选择之后仍重绑 Media3 播放器。本次在**修改播放器状态之前**检查 generation，过期操作在改动播放器前被拒绝；单一 Mutex 串行化切换边界；Mutex 具备取消/超时保护。

## 二、执行环境（重要披露）

- 仓库 `cinema-tv`，分支 `main`；`JAVA_HOME`、`ANDROID_HOME` 均指向 box 既有环境（`$JAVA_HOME`、`$ANDROID_HOME`）。
- **box 无 Android 模拟器/真机**：所有 androidTest 仅能编译（UNVERIFIED），不能运行。
- **作者沙箱的 gradle 无法运行**：`kspDebugKotlin` 任务在收尾写 `app/build/kspCaches/debug/backups` 时被沙箱文件系统拒绝（`java.nio.file.FileSystemException: .../backups: Operation not permitted`）。根因已进一步定位为 **`futimens(2)` 系统调用被底层安全策略拦截**（见 §四），而非 KSP 缓存或代码问题。
  - 已验证：`utimensat(AT_FDCWD, path, ...)` 返回 ok（Python `os.utime` 走该变体，成功）；但 `utimensat(fd, NULL, ...)`（即 `futimens`）被拦截，即便 `dangerouslyDisableSandbox` 也无法绕过（拦截发生在 seccomp/容器层，不在 Bash 工具沙箱层）。
  - KSP 增量编译的 `IncrementalUtil.copyWithTimestamp` 依赖 `Files.setLastModifiedTime`（底层走 `futimens`），故 `kspDebugKotlin` 必然失败。
  - 结论：这是环境限制，不是代码问题；所有 gradle 构建/测试结果**以 box 上独立复核者的真实运行为准**。
- 原始证据保存于 `.local/evidence/round-17/`（被 `.gitignore` 忽略），每次运行用唯一文件名，不覆盖旧日志。

## 三、代码改动

| 文件 | 改动 |
| --- | --- |
| `feature/player/OrderedSwitchGate.kt`（修改） | 单一 Mutex + `AtomicLong` generation 的串行切换边界。`run(generation, action)` 在持锁后、执行 action 前判 `isLatest(generation)`，过期即拒绝（返回 false）。**新增 `withTimeout(30_000L)` 有界保护**：持锁的挂起 action 超时或被取消时释放锁，超时视为失败（抛 `TimeoutCancellationException`），绝不静默放行 |
| `feature/player/OrderedSwitchCoordinator.kt`（新增） | 纯 JVM 的切换协调器，`PlayerControlsRuntime.selectTarget()` 直接调用它。`select()` 完成 reserve → launch → gate.run → （测试 hold）→ staleGuard 的完整协调；generation 检查前移到改动播放器之前；`onStale` 回调 trace `stale-drop`；`onTimeout` 回调报告超时失败 |
| `feature/player/PlayerControlsRuntime.kt`（修改） | `selectTarget()` 改为委托 `OrderedSwitchCoordinator.select()`，把 `staleGuard` 传给 `selectFromPlayer`；保留 `select:` 与 `stale-drop:` 两条 trace（E05/E06 依赖） |
| `feature/personal/PersonalLibraryRuntime.kt`（修改） | `selectFromPlayer()` 新增可选参数 `staleGuard: (suspend () -> Boolean)? = null`，在 `probe.select` 之前与之后各做一次 generation 检查，过期返回 false 且**不**改动播放器/`selectedMedia`。改动最小，不新增第二状态权威，`CancellationException` 始终传播 |
| `test/.../OrderedSwitchGateTest.kt`（修改） | 补 `import kotlinx.coroutines.cancel`（上一轮缺 import 导致编译失败）；移除自带第二道 isLatest 的镜像测试，改为纯 gate 原语测试（`superseded_generation_is_rejected_without_running_the_action`） |
| `test/.../OrderedSwitchCoordinatorTest.kt`（新增） | 6 个 runtime 层 JVM 对抗测试，真实驱动 `OrderedSwitchCoordinator`，用可控 `CompletableDeferred` 反转 ≥2 次快速选择的完成顺序，断言最终状态在所有权威（作品/剧集/变体/媒体身份/位置/checkpoint/UI 选中项）一致 |

## 四、gradle 构建验证：BLOCKED（环境限制，贴原文）

命令（真实运行）：
```
./gradlew :app:testDebugUnitTest --tests '*OrderedSwitchCoordinatorTest*' --tests '*OrderedSwitchGateTest*' --no-daemon --no-configuration-cache -Dorg.gradle.workers.max=1 --max-workers=1
```

关键输出行：
```
> Task :app:kspDebugKotlin FAILED
e: [ksp] java.nio.file.FileSystemException: .../app/build/kspCaches/debug/backups: Operation not permitted

FAILURE: Build completed with 2 failures.
1) Execution failed for task ':app:kspDebugKotlin'
   > A failure occurred while executing com.google.devtools.ksp.gradle.KspAAWorkerAction
   > .../app/build/kspCaches/debug/backups: Operation not permitted
2) java.nio.file.FileSystemException: .../build/reports/problems/problems-report.html: Operation not permitted

BUILD FAILED in 20s
```

完整证据：`.local/evidence/round-17/st3-1a-gradle-blocked-20261027-202400.log`。

因此：
- **RED→GREEN（gradle）**：**BLOCKED（作者沙箱）**。无法在作者沙箱运行 gradle 编译/测试；`futimens` 被底层拦截是硬性环境限制，不用 kotlinc 或其他方式冒充 RED/GREEN。独立复核者将在 box 上用真实 gradle 重跑。
- **gate 变异测试**：未能通过 gradle 运行；变异逻辑（临时删 `OrderedSwitchGate.run()` 第 52 行的 `if (!isLatest(generation)) return@withLock false`，确认 `OrderedSwitchCoordinatorTest.superseded_generation_queued_behind_the_gate_is_rejected_without_staleGuard` 失败后还原）已按此设计写入测试，但实际运行留待 box 复核。

## 五、可在作者沙箱真实运行的验证

| 命令 | 结果（真实输出） |
| --- | --- |
| `python3 -m unittest discover -s scripts/tests` | **PASS** — `Ran 62 tests ... OK` |
| `python3 scripts/check_repository_security.py` | **PASS** — `Checked 452 tracked files; 0 policy violations`（含本轮新增/修改文档的状态） |

## 六、小验收逐项（ST3.1a 第 1–6 条对应）

- [x] **补 import 让 JVM 测试编译通过**：`OrderedSwitchGateTest.kt` 补 `import kotlinx.coroutines.cancel`（上一轮 `owner.cancel()` 缺该 import，是独立复核者报出的编译错误）。
- [ ] **runtime 层对抗测试真实驱动 coordinator**：测试代码已写就（`OrderedSwitchCoordinatorTest` 6 个用例，驱动生产 `OrderedSwitchCoordinator`，不用镜像 gate，测试内不自带第二道 isLatest）。**但未能通过 gradle 运行（BLOCKED，环境限制）**，编译与运行结果待 box 复核。
- [ ] **RED→GREEN（真实 gradle）**：**BLOCKED（作者沙箱）**，见 §四。
- [x] **处理"已进入 selectFromPlayer 的旧操作"**：`selectFromPlayer` 加 `staleGuard` 参数，在 `probe.select` 前后各判一次 generation；配合 `RoomPlaybackProbe.select` 内 `ensureActive()` 取消检查点与单一 Mutex 串行化。`Mutex` 有取消/超时保护（`withTimeout` + `withLock` 取消释放锁），超时视为失败（`onTimeout` 报告），非静默放行。
- [ ] **文档改正并只记真实结果**：本文件即改正结果；未做到项取消勾选；ST3.1b 明确未做。
- [ ] **验收命令（testDebugUnitTest / assembleDebugAndroidTest / 八探针）**：**BLOCKED（作者沙箱，`futimens` 环境限制）**，待 box 复核。Python 两条已真实通过（§五）。

## 七、未验证项与遗留风险

1. **所有 gradle 构建/测试（testDebugUnitTest / assembleDebug / assembleDebugAndroidTest / lint / release / 八探针守护构建）**：作者沙箱因 `futimens(2)` 被底层拦截无法运行，**BLOCKED**，待 box 复核者重跑。JVM 测试总数（应 > 499 且 0 失败）待 box 复核确认。
2. **RED→GREEN 与 gate 变异测试**：无法在作者沙箱运行，**BLOCKED**。
3. **所有 androidTest（含 E05/E06 与 U05/P07/720p）**：box 无模拟器，仅能编译，**UNVERIFIED**；U05/P07/720p 属 ST3.1b，本轮未做，**BLOCKED/UNVERIFIED**。
4. **`selectFromPlayer` 的"select 之后" staleGuard 局限**（如实披露）：该检查能阻止旧操作覆盖 `selectedMedia`/`requestedResumePosition`，但**无法撤销** `probe.select` 已经对播放器执行的 `setMediaItem`。真正的"旧操作不改播放器"由**单一 Mutex 串行化 + 取消传播（`ensureActive` 在 `setMediaItem` 前）**保证；"select 之后"的 staleGuard 是防御性保险，不夸大其作用。

## 八、commit 列表

本轮尚未提交（先贴 `git status --short` 与 `git diff --cached --stat`，待提交）。计划分 1–2 个本地提交：目标 A 一次（`OrderedSwitchGate`/`OrderedSwitchCoordinator`/`PlayerControlsRuntime`/`PersonalLibraryRuntime` + 两个测试文件），文档一次。
