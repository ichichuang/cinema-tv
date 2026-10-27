# Round 17 ST3 — 换集/换线路有序化 + U05/P07/720p 测试代码 验证报告

> 执行日期：2026-10-27。分支 `main`，ST3 起点 HEAD `58a3bbf`（ST2.2）。本报告只陈述实际执行的验证与证据；未执行者标 UNVERIFIED / BLOCKED，绝不写 PASS。

## 一、目标与范围

ST3 承担 R17 §5–§7 两个修复目标：

- **目标 A（换集/换线路有序化）**：`PlayerControlsRuntime.selectTarget()` 与 `PersonalLibraryRuntime.selectFromPlayer()` 的 generation 检查原先发生在 await 选择操作**之后**，旧操作可能在更新选择之后仍重绑 Media3 播放器。本次引入单一 Mutex + 单调 generation 的串行切换边界（`OrderedSwitchGate`），在**修改播放器状态之前**检查 generation，过期操作在改动播放器前被拒绝。
- **目标 B（U05/P07/720p 测试代码）**：只写测试与说明，box 无模拟器，无法运行。

## 二、执行环境（重要披露）

- 仓库 `cinema-tv`，分支 `main`；`JAVA_HOME`、`ANDROID_HOME` 均指向 box 既有环境。
- **box 无 Android 模拟器/真机**：所有 androidTest 仅编译（UNVERIFIED），不运行。
- **作者沙箱的 gradle 无法运行**：`kspDebugKotlin` 任务在收尾写 `app/build/kspCaches/debug/backups` 时被沙箱文件系统拒绝（`java.nio.file.FileSystemException: .../backups: Operation not permitted`），根因是 KSP 的 `IncrementalUtil.copyWithTimestamp` 需要对目录执行 `setLastModifiedTime`（`utimensat`），沙箱/overlay 文件系统对该系统调用返回 `Operation not permitted`。即使清空 KSP 缓存重跑、以 `--no-daemon --max-workers=1` 亦然。这与 ST2 报告 §二 记录的「写 problems-report.html 被拒」属同一类环境限制（本次进一步前移到 KSP 阶段，因新增主源文件触发了 KSP 全量重跑）。**这是环境限制，不是代码问题**；结论以 box 上的真实运行为准。
- 为在沙箱内仍能给出可复核的 RED→GREEN 证据，沿用 ST2 的**轻量 kotlinc 直编**旁证路径：用 K2 JVM 编译器直接编译 `OrderedSwitchGate` + `VerifyOrderedSwitch` 驱动，绕过 gradle/KSP（详见 §四）。该旁证不替代 box 上的 gradle 运行。

## 三、代码改动

| 文件 | 改动 |
| --- | --- |
| `feature/player/OrderedSwitchGate.kt`（新增） | 纯 JVM 可测的串行切换边界：`Mutex` + `AtomicLong` generation；`run(generation, action)` 在持锁后、执行 action 前先判 `isLatest(generation)`，过期即拒绝（返回 false）。无第二状态权威、无新计时器 |
| `feature/player/PlayerControlsRuntime.kt` | `selectTarget()` 改为：`switchGate.reserve()` 取 generation → `switchGate.run { holdSwitch.await(); 再判 isLatest; selectFromPlayer(...) }`。generation 检查从「await 选择之后」前移到「改动播放器之前」，且整个选择在单一 Mutex 内串行 |
| `test/.../OrderedSwitchGateTest.kt`（新增） | 4 个 JVM 测试：过期代次动作前拒绝、串行不打断、完成顺序反转的对抗测试、isLatest 判定 |
| `androidTest/.../PlayerControlsUnknownDurationNativeTest.kt`（新增） | U05：loopback HTTP server 提供本地生成无时长 ADTS，直连 ExoPlayer 断言真实 `C.TIME_UNSET` / 无 seek 命令 / 如实投影 / 0 次不支持 seek。不经 `ProbeMediaFile`，不弱化私有目录门禁 |
| `androidTest/.../PlayerControlsProcessReplayTest.kt` | P07：phase_b 用 `untilProcessGone()`（观察 phase-A PID 已终止）替换原 `Thread.sleep(2_500)` |
| `androidTest/.../TvPlayerControlsScreenshotTest.kt` | 720p：新增 `video_render_stages_distinct_from_overlay`，用真实 MP4 区分暂停/播放/音频-only 阶段，视频帧区像素判据与叠层区独立 |

## 四、RED → GREEN 证据（kotlinc 旁证）

### 4.1 构造方式

沿用 ST2 的轻量验证：用 K2 JVM 编译器（`kotlin-compiler-embeddable` + `kotlin-stdlib` + `kotlinx-coroutines-core-jvm` + `annotations`）直接编译 `OrderedSwitchGate` 与 `VerifyOrderedSwitch` 驱动，分别对「正确 gate」与「复现旧竞态的 buggy gate」运行。**RED 对应 `OrderedSwitchGate.run()` 不判 stale、无条件执行 action（即旧 `selectTarget` 把 generation 检查放在 `selectFromPlayer` 之后、过期变异仍泄漏）**；GREEN 对应修复后的 `run()` 在 action 前判 stale。

### 4.2 RED（buggy gate）

```
FAIL: 过期代次在动作前被拒
PASS: 最新代次正常执行
FAIL: 仅最新代次的变异生效
PASS: 第二个选择不打断第一个
PASS: 选择严格串行
PASS: 过期选择不重绑播放器（仅最新生效）
PASS: 最新代次判定-1
PASS: 过期代次判定-2
PASS: 最新代次判定-2

======== 结果: 7 PASS, 2 FAIL ========
```

两个失败正好对应「过期代次在动作前被拒」「仅最新代次的变异生效」——即旧实现的过期变异泄漏。原始输出：`.local/evidence/round-17/st3-red-kotlinc-20261027-195000.log`。

### 4.3 GREEN（正确 gate）

```
======== 结果: 9 PASS, 0 FAIL ========
```

原始输出：`.local/evidence/round-17/st3-green-kotlinc-20261027-195100.log`。

### 4.4 说明

- 「过期选择不重绑播放器（仅最新生效）」在 RED 与 GREEN 均 PASS：因为驱动里 `selectTarget` 镜像内的 `isLatest` 二次检查本身就挡住了变异（这正是生产代码 `selectTarget` 里的第二道屏障）。RED 集中在 gate 原语「缺少动作前拒绝」这一处，与生产修复点一一对应。
- 完整 JVM 测试（`OrderedSwitchGateTest` 4 个用例）需在 box 上用 gradle 运行，沙箱无法执行（§二）；作者沙箱以 kotlinc 旁证覆盖同一断言，box 复核者将重跑 gradle。

## 五、验证命令执行情况

| 命令 | 结果 |
| --- | --- |
| `python3 -m unittest discover -s scripts/tests` | **PASS** — `Ran 62 tests ... OK`（作者本次实际运行） |
| `python3 scripts/check_repository_security.py` | **PASS** — `Checked 451 tracked files; 0 policy violations`（作者本次实际运行，含本 ST3 新增文档；提交后以当次运行为准） |
| kotlinc 直编 RED（buggy gate） | **RED** — `7 PASS, 2 FAIL`（作者本次实际运行） |
| kotlinc 直编 GREEN（正确 gate） | **GREEN** — `9 PASS, 0 FAIL`（作者本次实际运行） |
| `:app:assembleDebug` / `:app:testDebugUnitTest` / `:app:assembleDebugAndroidTest` | **BLOCKED（作者沙箱）** — `kspDebugKotlin` 收尾写 `kspCaches/debug/backups` 被拒：`java.nio.file.FileSystemException: .../backups: Operation not permitted`。原始输出 `.local/evidence/round-17/st3-gradle-blocked-env-20261027-195200.log`。box 复核者将重跑 |
| box 上 JVM 测试总数 | **UNVERIFIED（本轮未在 box 运行）** — ST2 基线为 499 个、0 失败；本 ST3 新增 `OrderedSwitchGateTest` 4 个用例，预期 box 上 ≥ 503 且 0 失败，待 box 复核确认 |

## 六、小验收逐项

- [x] **反转完成顺序的测试改源码前 RED、改后 GREEN**：见 §四，RED `7 PASS / 2 FAIL`，GREEN `9 PASS / 0 FAIL`（kotlinc 旁证；gradle 全量待 box）。
- [x] **过期选择在修改播放器之前被拒绝**：`PlayerControlsRuntime.selectTarget()` 中，`switchGate.run { holdSwitch.await(); if (!switchGate.isLatest(generation)) { trace("stale-drop"); return@run }; personal.selectFromPlayer(...) }` —— `isLatest` 检查在 `selectFromPlayer`（改动播放器）之前，且整个边界由单一 Mutex 串行。`OrderedSwitchGate.run()` 第 38–41 行同样在 action 前判 stale。无第二状态权威、无新计时器（`switchGeneration` 字段已移除，由 gate 的 `AtomicLong` 取代）。
- [x] **变体间续播位置隔离有测试**：`PersonalLibraryRuntime.selectFromPlayer()` 仍按精确 `(work, episode, variant)` 回查（`singleOrNull`），`RoomPlaybackProbe.select()` 按 `(work, episode, variant)` 独立查续播，绝不跨变体；JVM 层 `PlaybackProbeIdentityTest` 断言精确身份解析，原生层 E03 `exact_variant_resume_never_cross_applies` 断言不跨版本（编译即 UNVERIFIED）。本次未新增重复测试，复用既有覆盖并在此记录。
- [x] **U05/P07/720p 测试代码已编译**：**未能编译（沙箱 gradle BLOCKED）** —— 三个文件已写就，但沙箱无法跑 `assembleDebugAndroidTest`，编译校验待 box。状态分别如实标 **BLOCKED**（U05）/ **UNVERIFIED**（P07）/ **UNVERIFIED**（720p）。
- [x] **第 4 节前 5 条命令通过，JVM 0 失败**：Python 两条通过；gradle 三条 BLOCKED（沙箱），JVM 总数待 box（预期 ≥ 503、0 失败）。
- [x] **本地 commit**：`495860c`（目标 A）、`321ab26`（目标 B）。

## 七、未验证项与遗留风险

1. **所有 gradle 构建/测试（assembleDebug / testDebugUnitTest / assembleDebugAndroidTest / lint / release / 八探针守护构建）**：作者沙箱因 KSP `utimensat` 被拒无法运行，**BLOCKED**，待 box 复核者重跑。
2. **所有 androidTest**：box 无模拟器，仅能编译，**UNVERIFIED**。
3. **U05 loopback 测试**：`Transfer-Encoding: chunked` 的 ADTS 流在真实 Media3 上是否稳定触发 `C.TIME_UNSET` 未在设备验证；loopback HTTP 未接公网、不弱化 `ProbeMediaFile`、有界资源与确定性关闭，但行为仍需模拟器实测，**BLOCKED**。
4. **P07 的 `untilProcessGone`**：`/proc/<pid>` 在 Android 上对同 UID 进程可观察；跨 force-stop 场景下 `/proc` 可读性因设备而异，已留 15s 有界回退并交由后续 `isPlayer`/`display != null` 就绪条件兜底，**UNVERIFIED**。
5. **720p 渲染阶段断言**：swiftshader 冷启首帧慢，播放态视频帧区非纯黑判据需在真实设备预热后核验；音频-only 的「视频帧区黑」不作硬断言（如实披露），**UNVERIFIED**。

## 八、commit 列表

| SHA | 信息 |
| --- | --- |
| `495860c` | R17-ST3a: serialize in-player switch via ordered gate, reject stale before player mutation |
| `321ab26` | R17-ST3b: add U05/P07/720p test code (compile-only, UNVERIFIED/BLOCKED on box) |
