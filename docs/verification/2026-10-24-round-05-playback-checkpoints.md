# Round 05 候选播放检查点验证

本轮完成 T04 的候选检查点、持久会话仲裁及合成 Room schema 1→2 迁移切片。**没有接入生产播放器，整个 T04、T03、M02 和 A06–A09 均未验收。** 实际运行中的快退、重试、切集等旧问题仍待未来接入与设备验收。

## 范围、输入与起点

用户在区分附件指令与当前请求后，明确授权“按附件实施 Round 05，仅本地提交，不推送”。沿用既有隔离工作区 `cinema-contract-foundation`，分支 `codex/contract-foundation`。起始 HEAD `a46a8a97947d1b72fefd3361f9742df9540a5be6`；原 main 为 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`。两处 checkout 起始均干净，未重建工作树、丢弃或重置任何状态。

ZIP 内三项 SHA-256 全部匹配；独立 plan / instruction 与包内正文逐字节一致。ZIP 摘要 `7979ed92b35d6bc614f440eb53530013073965fedd305afbd5ad474646127e0c`；计划 `662e6d80c8b1ebc30f2e25abe91c5bf35d689d34a246be24aa1d6c579677a039`；指令 `caf723b576aad80c099790ba29150186e8e9d99f1a1048c90209c4e6ea199822`。

原计划已原字节导入 `docs/superpowers/plans/2026-10-24-round-05-playback-checkpoints.md`。初期沿用上一轮“计划含个人路径”的假设，经本轮原文及实际安全规则核对后纠正；本轮计划无该匹配，没有因旧假设改写正文。原附件、指纹、完整测试输出和 APK 副本保存在忽略的 `.local/evidence/round-05/`，不提交二进制或私人路径。

## 实现与政策

- 沿用原 `PlaybackSnapshot`、`CommitResult` 和 `PlaybackProgressStore.commit(snapshot)`，未改公共 domain 签名或复制模型。新增内部协调器、注入单调钟和真实 Room adapter。
- 以实际 playing 的 elapsed time 累计五秒保存目标，媒体位置仅为保存值。加载／缓冲／重试不以零覆盖最后有效位置；明确 seek 支持快退和归零，未知时长保留 nullable／已有已知时长，不将超过陈旧 duration 的位置夹紧。
- 新选会话普通观看资格为累计实际播放至少 5,000ms；4,999ms 误点即退不提升续播。真实短片有大于零的实际播放并收到有效 ended，可完成该集。显式 seek 的未观看状态不提升续播，也不能降级已有观看记录。此阈值是本轮可审查项目政策，仍须未来产品／实机评估。
- Mutation 事件在等待 Mutex 前捕获单调时间，结算基线不倒退；慢写入期间已发生的暂停不多算观看时间，持续实际播放跨写入的时间仍计入。保存和切集串行，无游离任务或吞取消。
- SQLite AUTOINCREMENT 在事务内分配持久 generation；同集／同版本下限在选择时即建立；更低序号和同序号不同 payload 拒绝；同序号完整相同记录确认既有持久提交。旧集最终状态可以独立保存，但不能覆盖更新会话的作品续播指针。
- 逐集快照、最后接受的 session sequence 和合格作品指针处于同一 Room 事务。Committed 在真实提交后返回；RejectedStale 不覆盖；真实 SQL／I/O 错误返回脱敏 Failed；取消向上传播。回执丢失与事务回滚分别验证。

具体类型、文件及未来事件映射见 [播放检查点结构](../architecture/tv-playback-checkpoints.md)。T03 的真实旧 DataStore 迁移／cutover、T05 的稳定身份／版本时间轴、T09 的实际 Media3／生命周期／页面接入继续独立验收。

## schema 与迁移

| 项目 | 实际证据 |
| --- | --- |
| 原 schema 1 | 原字节保持；SHA-256 `14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959` |
| schema 2 | 真实 Room 导出；10 张表；SHA-256 `6b5abe547c00a7a2d2cf2ab1e30e995347b3d9cd014c9700a639e5f87677aca9` |
| 原七表 | exported entity 定义全部逐项一致，没有删表／改键／重写 payload |
| 新三表 | playback_sessions、playback_record_order、playback_resume；带外键与索引 |
| 迁移执行 | 从打包的原 1.json 建全部旧表、identity 和 version=1；七表各种合成行逐字段、逐类型比较后，真实工厂升级到 2 |
| 迁移后 | 旧 browse、favorite、cache、playback、staging 和 batch 均保持；新会话低序号及归零可正常提交；不从旧 payload 推测 generation 或续播目标 |
| 不支持版本 | 原负向测试改用 3，因为 2 现已支持；仍核对失败且原数据保留，没有削弱该断言 |

没有 destructive migration fallback，没有真实用户数据迁移。Debug / Release 生成的 CinemaDatabase_Impl 文件逐字节一致。

## 红绿、独立审阅与修正

首个 seek-back JVM 测试先观察到未生成检查点，再由最小单调钟实现转绿。扩展的 15 项策略测试中，9 项观察到行为失败后修正，其他保护已由最小实现满足。最初 19 项新增 native 测试在已核实 AVD 上实际失败，主要失败点为缺少持久会话／提交实现；迁移另实际失败于“期望 version 2、实际 version 1”。这些不是编译结果，也不把全部方法称为各自完整路径的 RED。

一次独立只读审阅核对 15 个候选文件指纹、源码、schema、测试和执行证据，发现 0 Critical、3 Important、0 Minor。三项均在协调器范围内接受并作单次修正：

| 发现 | 实际 RED | 最小修正与 GREEN |
| --- | --- | --- |
| 不足五秒时，失败的 seek／短片完成被离开或切集跳过 | 三项新增 JVM 方法分别返回错误的 null／Committed；真实 Room 触发器组合也失败 | pendingSave 独立保留，只有 Committed 清除；边界／选集必须重试，失败不分配新 session |
| 恢复 COMPLETED 后重看，没有 seek 时仍保存 COMPLETED | 一项 JVM 方法期望 IN_PROGRESS、实际 COMPLETED | 真实 playing 恢复进行中；仅恢复／ready 不改变完成；正常 ended 后离开仍保持完成 |
| 慢写入中排队的短暂停被误算成五秒观看 | 一项 JVM 方法失败；真实 Room 组合也错误提升 | 事件入参在等待锁前捕获 elapsed time；单调基线不倒退；200ms 观看／6,000ms 存储等待不提升作品指针 |

审阅后的 RED 阶段：JVM 23 项中上述 5 项失败；native 26 项中 2 项组合失败、24 项通过。修正后协调器 25/25；最终全量见下表。另补真实 Job.cancel 事务回滚、取消后的显式意图重试及持续播放跨慢写入的正向保护。没有第二次独立复审；修正由作者用实际 RED→GREEN 和完整回归确认。

非行为 RED 的工具／fixture 错误单列：两处 JUnit 返回类型需明确 Unit、一次 importer 符号误写、aapt badging 不提供 runner 而改查真实 xmltree；Java 依赖的 SDK 探针改用已安装 mise Java。交付指纹探针另将 Git LF blob 与原工作区 CRLF 的 gradlew.bat 直接比较而误报；已按既有 .gitattributes 核对规范化 blob 相同，工作区原字节也与已验证指纹相同，未修改脚本。均未计为应用行为 RED，未安装或升级工具链。

## 最终实际验证

沿用 mise Temurin `21.0.11+10.0.LTS`、Java target 17、Python 3.13.3、Gradle 9.8.1、AGP 9.4.1、KSP 2.3.12、Room 2.8.5 和既有 SDK。未增加依赖或全局配置。

| 验证 | 最终实际结果 |
| --- | --- |
| JVM XML | 13 suites、149/149；其中 checkpoint 25；0 failure / error / skipped |
| Python | 62/62，退出 0；审阅修正后重跑 |
| Android instrumentation | 61/61 实际执行；0 failed / skipped / not-run；逐项 source inventory 与唯一 start / terminal pass 对应 |
| 原生组成 | 原 CinemaDatabaseTest 15 + LegacyUserDataImportTest 19 + PlaybackCheckpointNativeTest 26 + PlaybackSchemaMigrationTest 1 |
| 普通 property-off 全量 | 134 tasks 全部实际执行；testDebugUnitTest、assembleDebug、assembleRelease、assembleDebugAndroidTest、lintDebug、lintRelease 成功，退出 0 |
| Debug Lint | 0 Error、21 Warning、1 Hint |
| Release Lint | 0 Error、22 Warning、1 Hint |
| 告警比较 | id / severity / message 多重集与 Round 04 完全一致，无新增或压制 |
| 正常包身份／SDK／JNI／manifest | 实际 APK 检查；三份二进制 manifest 与基线逐字节一致，产品包仍 ARMv7，JNI 字节一致；opt-in／普通 Debug 的 482 个非 JNI ZIP 条目逐字节一致 |
| ARM64 开关门禁 | 默认关闭；Release + opt-in 在任务执行前拒绝；未改原白名单和 task graph 门禁 |
| 签名 | Debug／test 开发签名实际验证成功；Release 未签名，apksigner 退出 1；冻结源码保持 |
| diff／源码指纹 | diff whitespace 检查；原 PlayerViewModel、PlaybackProgress、DI、Application、导航、更新元数据及生产 manifest 均未改 |

全量命令（SDK 使用既有本机设置，不在 Git 中记录私人路径）：

```sh
mise exec -- python3 -m unittest discover -s scripts/tests -v
mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
  :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease \
  --rerun-tasks --no-daemon --no-configuration-cache --console=plain
```

专用 native pair 仅用精确两个 assemble 根任务及 `-Pcinema.storageTestArm64=true` 构建，实际 runner 为 `com.ichichuang.cinema.debug.test/androidx.test.runner.AndroidJUnitRunner`。

| 最终产物 | 身份／ABI | SHA-256 |
| --- | --- | --- |
| 普通 Debug | `.debug`，仅 armeabi-v7a | `dbe96db804ebdd8a873fdfe3ef1226ff68d50e8483d066944afe339f637bd1e9` |
| unsigned Release | `com.ichichuang.cinema`，仅 armeabi-v7a | `b25698c857247de4720215f24f35e97538b9853d3d87bd1d275ff3a3a4566651` |
| 专用 ARM64 Debug | `.debug`，仅 arm64-v8a | `0c70870336f8b57426b153c57f698827827f375df6eee30ce38d04a819c4efd9` |
| test APK | `.debug.test`，无 JNI；两种配对字节相同 | `a566324826de47db1e6f5997263f5dcd1b3320e5d24ffbe38cd20b63dc1bde9a` |

版本仍 0.9.8 / code 18；Debug 仍 0.9.8-debug。新增候选 DEX 使 APK 整体摘要改变，不将身份／JNI／manifest 一致说成整个 APK 与旧版字节相同。实际大小、ELF class/machine、JNI 摘要和签名输出在忽略证据中。

## 原生目标与证据边界

唯一新建、独占的 AVD 为 `cinema-checkpoints-20261024-6386d9b0`，serial `emulator-5562`；使用现有 API 30 default arm64-v8a 镜像和 pixel_2 profile，没有下载新镜像。每次 install、instrumentation 及关闭前均检查 AVD name、ro.kernel.qemu=1、API 30、abi=arm64-v8a、空 abilist32 和 boot_completed=1；所有 ADB 操作固定 `-H 127.0.0.1 -P 5037 -s emulator-5562`。实际 APK 的 runner、target、JNI ABI／ELF 已检查，不安装普通 ARMv7／unsigned Release 或正式包。

最终 runner `OK (61 tests)`，逐方法 61 个唯一执行和 pass，与源码集合完全一致，0 重复／遗漏／未知方法。使用真实 file-backed Room、新实例关闭重开、触发器失败、SQLiteFullException 注入、实际 Job 取消和事务后确认丢失。最后按身份门禁关闭该 AVD，再确认进程及 serial 均消失；共享 ADB server 保留。没有操作 TV、个人设备或生产用户记录。

普通 ARMv7 Android 测试包仅编译和包检查，未在 ARMv7 运行。真实 Media3 播放、TV 遥控、系统回收／断电、实际磁盘写满、真实旧用户迁移及升级／回滚均未验证；合成 v1 Room 迁移不等于旧 DataStore 用户迁移。新 storage/coordinator 未接入生产路径，没有双写或真实迁移激活。

## 本地交付

原 main、既有 tags、生产身份、SDK、工具链、签名冻结、Release assets 均保持。提交前检查专用分支和原 checkout；按常驻 Git 规则仅在收口阶段 fetch --no-tags，origin/main 仍为基线，不合并或同步 main。候选分支无 upstream 是既有、明确 local-only 状态，不创建远端分支。

实现提交为 `194040bf92868c15b8570e136d515ac3546a41eb`（15 个精确路径）。提交前实际 index 扫描 223 个文件，0 policy violations；staged blob 与已验证工作区逐文件摘要一致。最终文档另作本地提交，仅包含本报告和既有进度账本；文档暂存后实际 index 扫描 224 个文件，0 policy violations；最终更新交付文字后再次扫描同一最终 index。安全脚本范围为当前 index，不扫描历史。最终 Git 自引用 SHA 不写进自身内容；完整最终 HEAD、两处 clean 状态和提交结果记录在 `.local/evidence/round-05/delivery.json` 及对话交接，便于与实际 Git 核对。

按已授权范围保留现有工作树及本地分支，不推送、不合并、不发布、不安装生产包、不接入运行时、不开始 T05、T09 或后续轮次。
