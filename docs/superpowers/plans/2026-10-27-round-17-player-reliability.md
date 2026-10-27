# Round 17 — 播放器可靠性加固（重做）执行计划

> 执行日期：2026-10-27。分支 `main`，起点 `6cd8bae64f24ed6ede85c125c4644a8810fef7e1`（R16）。
> 本计划描述本轮真实执行的顺序与边界；实际结果见
> `docs/verification/2026-10-27-round-17-player-reliability.md` 与
> `docs/verification/PLAYER-RELIABILITY-ACCEPTANCE-MATRIX.md`。

## 一、为什么要重做

R17 的原提交 `46e40ab` 只存在于原作者（池闯）的 Mac 本地，远端开发分支仍停在 `6cd8bae`，
该提交不可得、不可复核。因此本轮的全部结论都从 `6cd8bae` 重新做出：不改写、不继承原
「44 PASS / 1 FAIL / 1 BLOCKED」的结论，仅把它作为历史记录在报告的附录里引用。

R17 的完成标准不是测试数量变多，而是：**已知风险有代码级处置、RED→GREEN 证据可复核、
未解决的问题仍明确标 BLOCKED。**

## 二、范围（只做四类风险 + 两处证据）

| # | 风险 | 处置目标 | 主要代码位置 |
| --- | --- | --- | --- |
| A | 物理按键周期：长按 OK/Back 在 ticker 心跳前松开时误发短按 | 一个完整的长物理按压绝不产生短按；每周期至多一次 LONG_PRESS；Back 每周期至多一次逻辑导航动作 | `feature/player/RemoteKeyCycle.kt` |
| B | 选择器默认焦点：已停用线路 / 不可播放剧集可获得可操作焦点 | 默认焦点只落在仍可用项；全不可用时焦点为 null 且 OK 不产生动作；selected 与 focus 分立 | `feature/player/PlayerControlsController.kt` |
| C | 连续换集/换线路：过期异步操作在新选择后重新绑定 Media3 播放器 | 权威过期检查前移到任何播放器/Room 变更之前 | `feature/player/PlayerControlsRuntime.kt`、`feature/personal/PersonalLibraryRuntime.kt`、`feature/player/RoomPlaybackProbe.kt` |
| D | 未完成证据：U05 未知时长、P07 跨进程恢复、720p 视频渲染 | 写出可复核的测试代码；box 无模拟器只做到编译，如实标 UNVERIFIED | `app/src/androidTest/.../PlayerControlsUnknownDurationNativeTest.kt`、`PlayerControlsProcessReplayTest.kt`、`TvPlayerControlsScreenshotTest.kt` |

## 三、硬边界

- 只在 `main` 工作；不新建分支，不 `push`/`merge`/`rebase`/`tag`/`reset --hard`/`fetch`/`pull`，不签名、不发布。
- 不动 `build.gradle.kts`、`app/build.gradle.kts`、`settings.gradle.kts`、`gradle/`、`gradle.properties`、`gradlew*`；不安装新依赖。
- Room schema v1–v6 不新增 migration，字节保持一致；包名、版本号、ABI、签名配置不变；`AndroidManifest.xml` 生产入口不变。
- 新功能全部留在既有私有 Debug 探针授权链之后；默认片源目录为空；不接公网片源。
- 单一 ExoPlayer、单一 checkpoint 协调者、Room 仓库唯一写入方；不新增第二个进度写入者/导航权威/播放计时器。
- 最小改动、修根因；不吞异常；不加 `Thread.sleep` 掩盖竞态，只用可观察就绪条件。
- 不为通过而改测试、不削弱断言。唯一允许修改的测试问题：测试自身的初始化竞态（见 R17.1）。

## 四、子任务与顺序

按「一次只做一个、每个做完停下汇报」的编排要求拆成四个子任务：

| # | 子任务 | 产出 |
| --- | --- | --- |
| ST1 | 基线确认 + R12 浏览测试初始化竞态修复（R17.1 §2–4） | `BrowsePersistenceNativeTest` 的 `awaitBrowseProbe()` 就绪等待 |
| ST2 | `RemoteKeyCycle` 长按/Back 修复 + 选择器默认焦点修复（R17 §3–4） | 目标 A、B 的生产修复与 JVM 测试 |
| ST3 | 异步换集/换线路有序化 + U05/P07/720p 测试代码（R17 §5–7） | 代次令牌 + probe 队列内过期检查；三个原生测试类 |
| ST4 | 全量回归、测试清单核对、矩阵与文档、R17.1 收口（R17 §8–10、R17.1 §5–8） | 本计划的执行结果、两份矩阵、两份报告 |

## 五、测试策略

1. **先 RED 后 GREEN**：新增 JVM 测试先在改源码前运行一次并贴出失败行，再改源码后运行通过。
   对无法在 box 上构造真实前置条件的断言，不写假断言，直接标 UNVERIFIED。
2. **区分力优先**：测试必须能在「错误行为」下失败。`SwitchSelectionTest` 只驱动新建的
   纯 JVM 编排类，对生产 `RoomPlaybackProbe` 内的权威检查**没有区分力**，该事实已在
   `docs/verification/2026-10-27-round-17-st3-ordered-selection.md` 中如实记录。
3. **原生测试只编译不运行**：box 无 Android 模拟器/真机，所有 androidTest 一律标「已编译，未运行（UNVERIFIED）」，
   矩阵中这类行不得是 PASS。
4. **历史 skip 保持 skip**：R14 的 5 个 assumption skip 不计入 PASS。

## 六、验收命令

```
python3 -m unittest discover -s scripts/tests
python3 scripts/check_repository_security.py
./gradlew :app:assembleDebug --no-daemon --no-configuration-cache
./gradlew :app:testDebugUnitTest --no-daemon --no-configuration-cache
./gradlew :app:assembleDebugAndroidTest --no-daemon --no-configuration-cache
```

ST4 另加 `assembleRelease`、`lintDebug`、`lintRelease`，以及八探针守护构建（任务图只能是
`:app:assembleDebug :app:assembleDebugAndroidTest` 这一对，八个 `-Pcinema.*=true` 一个不能少）。

JVM 总数一律从 `app/build/test-results/testDebugUnitTest/*.xml` 聚合，不以 `BUILD SUCCESSFUL` 代替测试结论。

## 七、完成定义

- 四类风险各有代码级处置，且每条处置能指向具体文件/行。
- RED→GREEN 有真实运行记录；无法取得针对生产代码的 RED 的项，明确写 UNVERIFIED 并说明原因。
- U05/P07/720p：测试代码已写、已编译、未运行 → UNVERIFIED（不写 BLOCKED 的「无代码」旧说法，也不写 PASS）。
- 全量回归真实跑过，数字来自本轮输出；staged 安全检查 0 违规；本地提交、工作区干净、未 push。

## 八、已知限制（计划阶段即接受）

- 生产代码里「队列内过期检查只在入队时做一次」等残余（见 R17 ST3 报告 §八）本轮不消除，
  如实写进剩余限制，不宣称「已完全解决」。
- 独立评审由编排方另行安排，本轮不自我认证为「已通过评审」。
