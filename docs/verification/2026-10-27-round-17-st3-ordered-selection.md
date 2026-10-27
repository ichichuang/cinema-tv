# Round 17 ST3.1a-2 — 换集/换线路有序化返工（runtime/JVM 层）验证报告

> 执行日期：2026-10-27。分支 `main`，起点 HEAD `fb0b0d1`（ST3.1a，未通过独立复核）。
> 本报告只陈述实际执行的验证与证据；未执行者标 UNVERIFIED / BLOCKED，绝不写 PASS。
> **结论更正（独立复核后，方案 A）**：目标 A 的生产修复经代码审查接受（代码审查通过）；但 `SwitchSelectionTest` 对生产修复**无区分力**，相关验收项为 **UNVERIFIED**，详见 §5.2、§七、§八。

## 一、目标与范围

本轮只做 **runtime / JVM 层** 的换集/换线路有序化返工，逐条回应独立复核者（box 上真实 gradle，fb0b0d1）发现的 6 项事实。U05/P07/720p 原生测试（ST3.1b）不在本轮范围，仍为 BLOCKED/UNVERIFIED。

目标 A：`PlayerControlsRuntime.selectTarget()` 与 `PersonalLibraryRuntime.selectFromPlayer()` 的过期检查必须发生在修改播放器/Room 状态之前；旧操作不能在更新的选择之后改变底层 Media3 会话。

## 二、对独立复核结论的逐条处置

| # | 复核者发现 | 处置 |
|---|---|---|
| 1 | `OrderedSwitchCoordinator` 第 69–70 行 `catch (CancellationException)` 先于 `catch (TimeoutCancellationException)`，后者是前者子类，`onTimeout` 是死代码，超时测试 10000ms 超时 | **删除整层**（Coordinator/Gate），改为「代次令牌 + `RoomPlaybackProbe.select` 队列内、在 `bridge.accept/select` 与 `setMediaItem` 之前检查」。超时/死代码问题随该层一并消除，而非修补 |
| 2 | 新增测试只驱动 Coordinator/Gate，从不经过 `selectTarget`/`selectFromPlayer`，对 runtime 无区分力 | **未解决**：新测试只驱动新写的 `SwitchSelection` 类与测试自写的 `FakeProbe`，同样不经过 `selectTarget`/`selectFromPlayer`/`RoomPlaybackProbe`，对生产修复无区分力（UNVERIFIED），见 §5.2 |
| 3 | 「反转 ≥2 次」不成立（g1 一直持锁，g2 卡 mutex，实际只反转一次）；断言假 Authority、固定 42L；变体续播隔离验收消失 | **未解决**：每个选择虽各持 `CompletableDeferred`，但 `two_rapid_selections_inverted_only_newest_mutates` 先放行较旧的 A、再放行 B，是正常顺序而非反转；没有反转完成顺序 ≥2 次的测试；`playerPositionMs` 与 UI 选中项从未断言；没有按变体区分续播位置的隔离测试（UNVERIFIED） |
| 4 | `selectFromPlayer` 第二次 staleGuard 在 `probe.select` 之后，而 `probe.select` 内部已 `bridge.accept`/`bridge.select`/`setMediaItem`，过期旧操作已改播放器与 Room | 过期检查**前移进 `RoomPlaybackProbe.select` 的队列内**，在 `bridge.accept/select` 与 `setMediaItem` 之前（generation 令牌传入 probe） |
| 5 | 回归：runtime 的 `catch (_: Exception) { hint("切换未完成…") }` 被删；`selectFromPlayer` 里 try 之外的 `remember()` 抛异常会冒出 `scope.launch` | `selectTarget` 恢复通用异常提示；`remember()` 移入 try（异常落入 `catch (Exception)` 返回 false + 提示） |
| 6 | 文档不实（第 30/31/69/72/81/85 行等） | 本文件即改正结果，见下 |

## 三、取舍说明（池闯「最小改动」要求）

原 ST3.1a 引入了 `OrderedSwitchCoordinator` + `OrderedSwitchGate` 两层抽象。复核确认：该层本身有一个死代码 bug（超时 catch 不可达）、且测试只驱动该层而非真实 runtime 路径。按「一个代次令牌 + 在 `RoomPlaybackProbe.select` 的队列内、在 `bridge.accept/select` 与 `setMediaItem` 之前检查」的最小方案重做后，**删除 Coordinator/Gate 层整体更简单**，故删除并同步删除其专属测试，改为：

- `SelectionGeneration`（纯 JVM `AtomicLong` 代次令牌，单一权威）
- `SwitchSelection`（纯 JVM 编排，`selectFromPlayer` 委托它，可被 JVM 测试直接驱动）
- `RoomPlaybackProbe.select(..., generation)` 在串行队列内、任何播放器/Room 变更之前做权威过期检查

没有新增第二个状态权威、没有新增计时器；单一 ExoPlayer 与既有 checkpoint 协调者仍是唯一写入方。旧的 `OrderedSwitchCoordinatorTest`/`OrderedSwitchGateTest`（含超时测试）随该层删除；新的对抗测试见 §五，不削弱。

## 四、代码改动

| 文件 | 改动 |
| --- | --- |
| `player/SelectionGeneration.kt`（新增） | 纯 JVM 代次令牌：`reserve()` / `isLatest(generation)`，单一权威 |
| `player/SwitchSelection.kt`（新增） | 纯 JVM 切换编排：select 前快路径过期拒绝 → `select()`（probe.select，含权威队列内检查）→ select 后过期则不 adopt。`selectFromPlayer` 委托它 |
| `player/RoomPlaybackProbe.kt`（修改） | 持有 `SelectionGeneration`；`reserveSelection()` / `isSelectionStale()`；`select()` 新增 `generation: Long? = null`，在 `queue.await` 内、`verifyHandoffCatalog`/`bridge.accept`/`bridge.select`/`setMediaItem` **之前**检查 `isSelectionStale(generation)` |
| `player/PlayerControlsRuntime.kt`（修改） | `selectTarget()` 同步 `personal.reserveSelection()`，`scope.launch` 内先 `holdSwitch?.await()`（E05/E06 扣住钩子）再 `selectFromPlayer(..., generation)`；失败且已过期时 trace `stale-drop:<gen>`；恢复 `catch (_: Exception) { hint("切换未完成，原记录保持") }`，`CancellationException` 始终传播 |
| `personal/PersonalLibraryRuntime.kt`（修改） | 新增 `reserveSelection()` / `isSelectionStale()`；`selectFromPlayer(workId, episodeId, variantId, generation)`（`selectFromPlayer` 自身的 generation 为必填，但其内部调用的 `RoomPlaybackProbe.select` 的 `generation` 参数默认 `null`，不传令牌**能编译**，没有编译约束保护）；`remember()` 移入 try；委托 `switchSelection.perform(...)` |
| `player/OrderedSwitchCoordinator.kt` / `OrderedSwitchGate.kt`（删除） | 随最小方案删除 |
| `test/.../OrderedSwitchCoordinatorTest.kt` / `OrderedSwitchGateTest.kt`（删除） | 随被删层删除 |
| `test/.../SwitchSelectionTest.kt`（新增） | 7 个 JVM 测试，只驱动新写的 `SwitchSelection` + 测试自写的 `FakeProbe`（权威过期检查由 `FakeProbe.select` 自行实现）；对生产修复无区分力 |

## 五、验证结果

### 5.1 JVM 单元测试（真实 gradle）

命令（真实运行，含工作区，附环境绕过说明见 §六）：
```
./gradlew :app:testDebugUnitTest --no-daemon --no-configuration-cache -Dorg.gradle.workers.max=1 --max-workers=1 -Pksp.incremental=false -Pkotlin.incremental=false
```

真实结果（从 `app/build/test-results/testDebugUnitTest/*.xml` 聚合）：
```
files=50 tests=506 failures=0 errors=0 skipped=0
```

基线（fb0b0d1）为 508 个测试 1 失败（`a_hung_selection_times_out...` 超时）。删除 Coordinator/Gate 层（9 个测试）+ 新增 SwitchSelection（7 个测试）→ 506 个测试 0 失败，且 > 499。

### 5.2 RED→GREEN

1. **原超时 bug（复核发现 #1）RED**（真实 gradle，改前）：
   ```
   OrderedSwitchCoordinatorTest > a_hung_selection_times_out_and_does_not_starve_later_selections FAILED
       org.junit.runners.model.TestTimedOutException at OrderedSwitchCoordinatorTest.kt:157
   tests=6 failures=1
   ```
   处置为删除该层（而非修补死代码 catch），该失败随层消失，无遗留超时路径。

2. **`SwitchSelection` 变异 RED**（真实 gradle）：注意，这是对**本轮新写的 `SwitchSelection` 类**做变异得到的 RED，**不是**针对修复前生产代码的 RED。临时删除 `SwitchSelection.perform` 内的两处 `isStale()` 检查后运行 `--tests '*SwitchSelectionTest*'`：
   ```
   7 tests completed, 3 failed
   superseded_selection_is_rejected_before_entering_the_probe FAILED (TestTimedOutException)
   rejected_selection_does_not_starve_a_fresh_one FAILED (AssertionError)
   latest_selection_adopts_and_old_session_cannot_overwrite_it FAILED (AssertionError)
   ```
   随后还原 `isStale()` 检查，重跑 `--tests '*SwitchSelectionTest*'` → `7 tests, 0 failures`（GREEN）。变异未提交。

   证据文件：`st3-1a-red-coordinator-timeout-20261027.xml`、`st3-1a-mutation-red-20261027.log`、`st3-1a-green-switch-xml-20261027.xml`。

3. **独立复核（box，真实 gradle）对生产修复的区分力检查：无 RED**
   - 把 `PlayerControlsRuntime`、`PersonalLibraryRuntime`、`RoomPlaybackProbe` 还原到 `58a3bbf` 版本、测试文件不变：可编译，`SwitchSelectionTest` 7 个测试**全部通过**（无 RED）。
   - 4 个生产变异**全部未被抓到**（均 7/7 通过）：删除 probe 队列内过期检查；把该检查挪到 `prepare()` 之后；`selectFromPlayer` 调 `probe.select` 时不传令牌（`generation` 参数默认 `null`，能编译）；`selectFromPlayer` 的 `isStale` 恒为 false。
   - 唯一被抓到的变异是删除新写的 `SwitchSelection` 内的检查（3 个失败），即上面第 2 条。
   - 结论：针对修复前生产代码的 RED→GREEN 为 **UNVERIFIED**。

### 5.3 其他验收命令（真实输出）

| 命令 | 结果 |
| --- | --- |
| `python3 -m unittest discover -s scripts/tests` | PASS — `Ran 62 tests ... OK` |
| `python3 scripts/check_repository_security.py` | `Checked 453 tracked files; 0 policy violations`（本次文档更正后在主仓库实际运行） |
| `:app:assembleDebug` | 产出 `app-debug.apk`（20.9 MB）；gradle 收尾因 `futimens` 报错（见 §六），任务本身成功 |
| `:app:assembleDebugAndroidTest` | 产出 `app-debug-androidTest.apk`（2.19 MB）；`compileDebugAndroidTestKotlin` 无错误（`holdSwitch`/`stale-drop` 引用均解析）；未在模拟器运行（UNVERIFIED） |

## 六、执行环境与 gradle 报错的重要披露

- box 无 Android 模拟器/真机：所有 androidTest 仅能编译（UNVERIFIED），不能运行。
- 作者沙箱的底层安全策略拦截 `futimens(2)` 系统调用，导致 gradle 三个环节失败：
  1. KSP 增量编译写 `kspCaches/debug/backups` → `Operation not permitted`；
  2. Kotlin 增量编译写 `/tmp/kotlin-backups*/*.backup` → 失败；
  3. gradle 收尾写 `problems-report.html`（`Files.move`）→ 失败，导致**即使所有任务已成功也返回 `BUILD FAILED`（异常信息为空）**。
- 绕过：加 `-Pksp.incremental=false -Pkotlin.incremental=false` 后，1 与 2 消失；3 仍存在，但发生在所有任务（test/assemble）成功之后，测试 XML 与 APK 均已正确产出。因此上表的「BUILD FAILED」是**假阴性**：真实结果以测试 XML 与 APK 产物为准。独立复核者在 box 上（不带上述 `-P` 参数）实际得到干净 `BUILD SUCCESSFUL`：`testDebugUnitTest --rerun` 506 个测试 0 失败，`assembleDebugAndroidTest` 与 `assembleDebug` 成功。
- 未使用 `.local/tools/utimens_shim.so`。

## 七、小验收逐项（对应 ST3.1a-2 的 a–h）

- [x] **a. 超时死代码消除**：删除含死代码 catch 的 Coordinator/Gate 层；注意新方案**没有任何超时保护**（见 §八 第 6 条）；新方案无独立超时器（由 `PlaybackEventQueue` 串行化 + 队列内过期检查保证确定性），不存在「静默超时」路径。原超时测试随层删除，不再有 10000ms 超时失败。
- [x] **b. 过期检查前移进 probe 队列内**：`RoomPlaybackProbe.select` 在 `queue.await` 内、`bridge.accept/select` 与 `setMediaItem` 之前检查 `isSelectionStale(generation)`，过期返回 null，不改播放器或 Room。
- [ ] **c. 真正经过 runtime 委托链的 JVM 测试**：**UNVERIFIED**。7 个 `SwitchSelectionTest` 只驱动新写的 `SwitchSelection` 类与测试自写的 `FakeProbe`，不经过 `selectTarget`/`selectFromPlayer`/`RoomPlaybackProbe`；把这三个生产文件还原到 `58a3bbf` 后 7 个测试仍全过（无 RED）；4 个生产变异全部未被抓到（§5.2 第 3 条）。`selectFromPlayer` 调 `probe.select` 时令牌参数默认 `null`，不传令牌**能编译**，不会被编译约束拦住。
- [ ] **d. 真正双重反转 + 变体隔离测试**：**UNVERIFIED**。没有真正反转完成顺序 ≥2 次的测试（`two_rapid_selections_inverted_only_newest_mutates` 按正常顺序放行）；`playerPositionMs` 与 UI 选中项从未断言；`latest_selection_adopts_and_old_session_cannot_overwrite_it` 只验证代次比较，不是按变体区分续播位置的隔离测试。
- [x] **e. 恢复通用异常提示**：`selectTarget` 恢复 `catch (_: Exception) { hint("切换未完成，原记录保持") }`；`selectFromPlayer` 的 `remember()` 移入 try；`CancellationException` 始终 `throw`，不吞。
- [ ] **f. 文档只写真实结果**：上一版（`967f7be`）的 c/d/g 误勾与第 106/107/110/122 行不实说法已在本次文档提交中更正；是否完全符合以独立复核为准。ST3.1b（U05/P07/720p）未做，BLOCKED/UNVERIFIED。
- [ ] **g. RED→GREEN 用真实 gradle**：**UNVERIFIED**。§5.2 第 2 条的 RED 是对新写的 `SwitchSelection` 变异得到的，不是针对修复前生产代码；独立复核把生产代码还原到 `58a3bbf` 后测试仍全过，无 RED（§5.2 第 3 条）。
- [x] **h. 验收命令**：独立复核在 box 上实际运行：`testDebugUnitTest` 506 tests / 0 failures（> 499）；`check_repository_security.py` 0 违规；`assembleDebugAndroidTest` 成功（androidTest **仅编译，UNVERIFIED**，未在模拟器运行）；`unittest` 62 个 OK。

## 八、未验证项与遗留风险

1. **U05/P07/720p 原生测试（ST3.1b）**：未做，BLOCKED/UNVERIFIED。
2. **Android 层变异（删 `RoomPlaybackProbe.select` 队列内检查 / `selectFromPlayer` 不传 generation 的实际运行行为）**：需模拟器仪器化验证，box 上 UNVERIFIED。「`selectFromPlayer` 调 `probe.select` 时不传 generation」**能编译**（参数默认 `null`），JVM 测试也未抓到（§5.2 第 3 条）。
3. **E05/E06 原生测试**：保留 `holdSwitch` 钩子与 `stale-drop` trace，androidTest 编译通过，但未在模拟器运行（UNVERIFIED）。
4. **`selectFromPlayer` 的 select 后「不 adopt」守卫**：由于权威检查已前移到 probe 队列内，`SwitchSelection.perform` 的 select 后检查是防御性保险（防止过期结果被 adopt 进 `selectedMedia` 内存镜像），不夸大其作用。
5. **`SwitchSelectionTest` 对生产修复无区分力**：见 §5.2 第 3 条与 §七 c/d/g，相关验收 UNVERIFIED。
6. **已知限制：生产残留（独立复核的代码分析，未运行验证）**：
   - (i) 过期检查只在进入 `RoomPlaybackProbe.select` 的队列时做一次；已在队列里执行的旧操作（例如正在 `bridge.accept`/`bridge.select`）遇到新请求后仍会执行到 `setMediaItem`，只是不更新 `selectedMedia`，会出现短暂状态分裂。在 `setMediaItem` 前再查一次可消除。
   - (ii) 队列外的 `player.pause()` / `capture()` 在过期检查之前执行，过期操作仍会暂停播放器（原有行为）。
   - (iii) probe 内拒绝（`select` 返回 null）后，`SwitchSelection.perform` 不调用 `onStale`，`message` 可能保留上一次的旧提示。
   - (iv) 代次令牌放在可重建的 probe 里（`PersonalLibraryRuntime` 约第 210 行 delete 时 `createProbe()`），重建后计数归零，可能与进行中的旧代次撞号。
   - (v) `PlayerControlsRuntime.selectTarget` 中 `reproject()` 被移到 try 之外。
   - (vi) 串行化依赖既有 `PlaybackEventQueue`；队列挂住会让后续切换一直排队（原有限制）。
   - `OrderedSwitchCoordinator` / `OrderedSwitchGate` / Mutex / 30 秒超时已在 `fb0b0d1` 之后的返工中整层删除，因此当前**没有超时保护**。

## 九、commit 列表

| SHA | 内容 |
| --- | --- |
| `fb3a4ed` | R17-ST3.1a：Coordinator/Gate 方案（已被后续返工删除） |
| `fb0b0d1` | R17-ST3.1a 验证文档 |
| `b4564a1` | R17-ST3.1a-2：代次令牌 + probe 队列内过期检查，删除 Coordinator/Gate |
| `967f7be` | R17-ST3.1a-2 验证文档 |
| 本次文档更正提交 | 见 git log |
