# Round 02 候选存储验证记录

## 范围与基线

本轮只执行 scoped plan 的 T02 编码延续及 T03 候选存储／合成记录保留切片。未接运行时，不代表整个 T03、M02 或任何 A01–A24 通过。

- 隔离工作树：既有 `cinema-contract-foundation`；分支 `codex/contract-foundation`。
- 实查基线 HEAD：`4ddd1530bb06f4faa8ac2f05cec0f440d2a453ee`，无后来提交、暂存或未暂存改动。
- 原 main：`7f3e06318ddec8385e7d8be698a922a9cd85c5dd`；原 checkout 未改动。
- ZIP 的四项 SHA256SUMS 均一致；独立 instruction／plan 与 ZIP 内同名内容一致。仅导入本轮 scoped plan。
- master plan SHA-256：`439c9be55bf8e889168711b650aa09dec02f05c38d04ac0e3011d24a86bbe1af`。
- Round 01 plan SHA-256：`393c50390733799af5534cc439d3b3913d0ad381e68301e8baa96649458a507a`。
- Round 02 plan SHA-256：`385b4bed07d8f38f70100ba79e0f54e2d9fa1496d340af39adf68525afc0a97e`。

先核对真实十一模型／五接口、known-total 校验、深列表回退裁定及三个旧 store，映射见 `docs/architecture/tv-storage-contracts.md`。未重复全仓审计，也未回退到交接哈希、重建 worktree 或执行 master plan 后续任务。

## 工具链与依赖

沿用 mise Temurin `21.0.11+10.0.LTS`、Python `3.13.3`、Gradle wrapper `9.8.1`、AGP `9.4.1`、内置 KGP／Compose `2.4.20`、compile SDK 37。新增固定 Room runtime/compiler/schema plugin `2.8.5`、KSP `2.3.12`、AndroidX Test core／runner `1.7.0`、ext junit `1.3.0`。没有 Kotlin Android plugin／built-in Kotlin opt-out、JDK/AGP/Gradle 升级、KMP、Robolectric、serializer plugin 或新全局工具。

直接版本已依据官方 Room／KSP／AndroidX Test 发布资料核对，并通过 Gradle 实际解析与代码生成。`debugRuntimeClasspath`、`debugAndroidTestRuntimeClasspath` 和 processor graph 的输出保存在本地忽略证据目录。Room 引入 Android SQLite 支持依赖；未添加 bundled native driver。既有 coroutines 解析版本仍为 1.10.2。

## 实际命令与结果

命令均在隔离工作树运行；下列环境变量表示传入的已有本地 SDK，不在仓库提交个人绝对路径。原始输出和 XML 留在忽略目录 `.local/evidence/round-02/`。

```sh
mise exec -- python3 -m unittest discover -s scripts/tests -v
ANDROID_HOME="$CINEMA_ANDROID_SDK" ANDROID_SDK_ROOT="$CINEMA_ANDROID_SDK" \
  mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  :app:testDebugUnitTest --rerun-tasks --no-daemon --no-configuration-cache
ANDROID_HOME="$CINEMA_ANDROID_SDK" ANDROID_SDK_ROOT="$CINEMA_ANDROID_SDK" \
  mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
  :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest \
  --no-daemon --no-configuration-cache
```

定向测试使用同样环境和 `:app:testDebugUnitTest --tests`，精确过滤为 `com.ichichuang.cinema.data.local.StorageCodecTest`、`com.ichichuang.cinema.data.local.LegacySnapshotCodecTest` 及审阅修正的 `com.ichichuang.cinema.data.local.*`。

| 检查 | 实际结果 |
| --- | --- |
| 新鲜基线 Python | 62/62，0 失败 |
| 新鲜基线应用单测 | 100/100，0 失败／错误／跳过；24 个 Gradle 任务重跑 |
| 基线两种 assembly／Lint | 成功，99 个任务；release unsigned |
| 编码 RED | 10 项中 8 项行为失败；编译成功后实际执行断言 |
| 编码 GREEN | 完整应用单测 110/110 |
| legacy codec／digest／parser RED | 10 项中 6 项行为失败 |
| legacy codec／digest／parser GREEN | 完整应用单测 120/120 |
| 审阅修正 RED | 24 项中 4 项行为失败，复现 UTF-16 摘要／负载／键损失 |
| 审阅修正 GREEN／最终完整单测 | 124/124，0 失败／错误／跳过；新增 24 项 |
| 最终 Python | 62/62，0 失败 |
| 最终两种 assembly、Lint、原生测试 APK 编译 | 成功，134 个任务（33 executed，101 up-to-date） |
| 原生 Android 测试 | 34 项源码已编译，0 项执行；无独立 emulator，阻塞 |
| staged-index security | 实现提交前 209 文件、最终证据暂存后 210 文件，均为 0 违规；未扫描历史 |

第一次组合构建因一项 public 测试函数推断暴露 internal exception 返回类型而失败；显式 `runBlocking<Unit>` 修正。新增的 private data-class copy 警告按既有 `@ConsistentCopyVisibility` 模式处理。最终两个 Lint 各 20 Warning + 1 Hint，与新鲜基线逐项比较没有新增、消失或消息变化；既有 Kotlin／Gradle／overridePathCheck 警告未通过压制掩盖。

## schema、产物与隔离

- schema：`app/schemas/com.ichichuang.cinema.data.local.CinemaDatabase/1.json`，由真实 Room compiler 生成，非手写。
- database version：1；identityHash：`ecf80cb44c0d3908fb222ce47999b3bf`。
- schema SHA-256：`14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959`。
- 实际 debug/release `CinemaDatabase_Impl.kt` 字节一致，SHA-256 为 `276b375de44118a5ebe60f36fcdae24c6c1978e54e3274ba6f6810f4a3fe1e11`。插件导出到一个共同路径，没有虚构 variant 目录。
- 七表主键、RESTRICT 外键、staging → batch 的延迟外键、BLOB 身份键及实际 bindBlob/getBlob 映射已源码与生成物核对。
- debug 产物：`com.ichichuang.cinema.debug`，18 / `0.9.8-debug`；release：`com.ichichuang.cinema`，18 / `0.9.8`，`app-release-unsigned.apk`。SDK/ABI、签名冻结保持。
- 测试 APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`；actual test package `com.ichichuang.cinema.debug.test`，target `com.ichichuang.cinema.debug`，runner `androidx.test.runner.AndroidJUnitRunner`，均从实际 metadata／manifest 读取。
- 新 fixture 仅在 androidTest 源码中生成；主 assets/resources/release 中未加入 fixture，两个 APK 的 assets／raw 未发现 synthetic／fixture 路径。
- 候选 store／factory／reader／importer 在 main 中仅有定义及内部实现引用，无 DI／启动／导航／player 调用。既有 production DataStore 文件保持原样；未实现 `PlaybackProgressStore.commit`。

## 原生、合成数据与故障证据的边界

现有 SDK 不含 emulator／system image，没有已核实且获准的一次性测试目标。本轮没有安装、执行 instrumentation、运行未指定目标的 connected task 或访问任何实体电视／个人设备。未来必须核实独立 disposable emulator 的身份、空用户数据及 ABI/SDK 兼容性，再在每次 install／instrument 前固定并核实 serial；不能凭 `ANDROID_SERIAL` 宣称已锁定任务。

原生测试源码包括文件写入／关闭／重开、独立浏览 context、幂等收藏、缓存隔离、真实 SQL 主键／非空／外键、损坏 payload 和不支持 schema 的保留；这些当前只有编译证据。合成 fixture 使用真实 `wp_` 四段格式、来源碰撞／bare ID、深索引、五类筛选、未知／错误类型或格式，独占 DataStore handles，无默认生产 Context reader。

故障测试源码在真实 Room 事务路径设置 ROWS_STAGED、MARKER_WRITTEN、COMMITTED 屏障，覆盖提交前 I/O 回滚、marker 后提交前回滚、实际协程取消、提交后确认丢失与幂等重试、注入 SQLiteFullException、source read 损坏；均未原生执行。未填满开发磁盘，未制造真实断电，未拿返回 enum／mock 代替数据库行验证。

纯测试实际证明格式、typed values、digest 和 classifier 的行为，包括 Float/Double raw bits、set 顺序、capture time 不参与摘要、不同 UTF-16 内容不碰撞、原始空串保留和错误 duration 不默认为零。它们不证明 DataStore 文件读取、Room 事务或持久性成功。

## 独立审阅与决定

完成一次整轮只读独立审阅，覆盖实际新增文件、既有契约和生成 schema。发现一个 Important：UTF-8 替换不同 surrogate 导致内容摘要碰撞；已由四项真实 RED → GREEN 回归修正，未收紧既有 domain 输入。原审阅者又做定向复核，独立运行实际编译 classes 的原始三值探针及全部 65,536 UTF-16 码元往返，并核对实际 KSP BLOB 映射与 124/124 XML；原 Important 已清除，未留 Critical／Important／Minor。

该审阅者没有重新运行完整 Gradle 或原生测试。declined 项作如下裁定：原生 SQLite／close-reopen／注入结果保持阻塞；真实旧数据、生产升级、断电不在本轮；T04 排序、身份关联、UI 与运行时切换保持未实施；依赖解析与最终构建以本代理终态输出为准；首个未发布 schema 的修正不虚构版本迁移。

## 本地交付与剩余关口

实现提交：`49a2032c140ccc3861ec4bc7b019897c1f446712`（`feat: 实现隔离的候选存储与旧记录暂存`）。验证代码范围为 `4ddd153..49a2032`；提交前记录的 25 个候选文件 SHA-256 已分别对照 staged blobs 与 committed files，全数一致。清单保存在忽略目录 `verified-candidate-sha256.json`，涵盖两份 build 文件、14 份 main Kotlin、3 份纯测试、3 份原生测试／fixture、1 份 generated schema、存储契约和本轮计划。

本记录与进度账本作为第二笔仅文档的本地提交，交付 HEAD 为该文档提交，可用 `git log -2 --oneline`／`git rev-parse HEAD` 核对，具体 hash 在本轮答复给出；没有在测试后再修改应用／测试／schema 字节。

提交前已确认本地专用分支没有 upstream（符合本轮不推送的交付方式），在全部代码验证后执行 `git fetch --no-tags origin`；远端 main 与原本地 main 一致，`origin/main...HEAD` 为 0/4（提交本轮前），无需同步或合并。只提交本轮明确路径；不 merge／push／publish／sign／tag，不调整仓库可见性，不删除 worktree。没有进入 T04–T16，也未标记整个 T03、M02 或 A01–A24 通过。

仍缺原生数据库／synthetic import／真实事务执行、授权真实旧数据验证、单写者运行时接入、T04 仲裁及用户设备验收；这些是下一关口，不是已获通过的本轮结果。
