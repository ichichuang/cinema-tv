# Round 07：隔离旧记录交接验证

本轮完成的是自有合成 profile 的实际 DataStore→Room 单写交接演练。普通 Debug／Release 继续使用旧存储，生产迁移、完整 T03／T04、M02 和电视验收仍未完成。本报告依据修正后的实际执行结果；不把编译、故障注入或进程测试外推为真实用户升级、任意断电或物理磁盘耗尽。

## 授权、基线与交付

用户明确授权“执行 Round 07，按附件限制实施并本地提交，不推送、合并或发布”。附件先作为待核实材料，未将附件中的执行语句自行视为用户授权。

- 原工作树：`../.worktrees/cinema-contract-foundation`，分支 `codex/contract-foundation`；起点 `a7deea8a88ff0afa2f7ba06fd7c4f615f8018af1`，起始无暂存、未暂存或未跟踪改动。
- 原 main／刷新后的 origin/main：`7f3e06318ddec8385e7d8be698a922a9cd85c5dd`，main 保持干净。实现前本地隔离分支相对 origin/main 为 0 behind／12 ahead；没有 upstream 是既定本地用途，不创建远端分支。
- ZIP SHA-256：`2fdad374506333d47a5aaef8230846568630b55109c07042d8d8e8d7cc01b536`。包内四项 SHA256SUMS 全部相符；独立的 instruction、matrix、plan 三附件分别与包内原字节一致。
- 计划原字节导入，SHA-256：`a20a48d672b617a5bfa79f664d4dca7e417c60ad3ae2ec38f9e55efdbd970bee`。不改写附件勾选框，以既有进度账本记录执行状态。
- 实现提交：`e8fecd200c1cfe32f18e94dff974d7844a30401b`，`feat: 实现隔离旧记录单写交接演练`；36 个明确路径，33 个应用／测试／schema 文件的工作区、暂存 blob 与验证时 SHA-256 一致。
- 本报告及最终账本另作本地文档提交；最终 HEAD、两项提交及干净状态写入忽略证据 `final-delivery.json`，并在最终聊天交付中报告。不推送、不合并、不变基、不发布、不修改远端元数据。

App 工具不能附加这个非 managed 的已有 Git linked worktree，继续复用原工作树，没有创建或重置开发分支。使用 capability-router 路由；依照 executing-plans、TDD、systematic-debugging、独立审查及验证／分支收尾技能执行。状态、审计、屏障和播放器存在相互依赖，采用通过验证后的完整实现提交与独立文档提交，不提交临时 stub。

## 实际实现与保持的边界

详细 ownership／接口和状态边界见 [交接结构](../architecture/tv-legacy-handoff.md)。四个既有 `preferencesDataStore` 单例分别持有 progress、browse、filters、settings；生产写 API 仅增加测试 profile 所需的 `editLegacy` 边界。无迁移 profile／意图的普通写入直接执行原 edit，不改变普通取消语义，不创建第二个同文件 DataStore，也不复制活跃偏好文件作为迁移实现。

候选 `prepare` 在同一旧 writer 屏障内读取实际单例，保留全部 typed raw 值、摘要、计数及未解析记录。`staged`／`verified` 都仍选 Legacy。只接受显式 source-qualified `wp_来源#movieId` 与可信合成目录的完整关联；不从 bare ID、标题、索引、URL 或时间猜 canonical ID，不从一条旧 watch point 伪造逐集历史。空串／absent、seek-to-zero、未知 duration、陈旧 duration 小于 position 等保留原契约语义。

激活前重新捕获旧摘要、重验 staging accounting 和实际目录／WAV 字节。在同一 Room 事务中写入初始检查点、session／resume 与 `room_active`；真实事务返回后才承诺提交。先持久化的 app-private AtomicFile 只表示 Room **可能已提交**；两个引擎无共享事务。缺库／缺意图／完整性异常或授权失效时阻止写入，不重建空库或回退陈旧 Legacy。提交后确认丢失的重试不重放旧点覆盖较新 Room 点，后续仲裁沿用原 generation／sequence。

本轮只启动测试播放器。browse／filter／已保存来源完整 staging 并保留原只读访问，激活后拒绝这些旧 API 的修改；没有新的生产配置 writer 或导航恢复 UI。播放进度只有选中的候选 writer 写入，没有双写。

PlayerViewModel、Koin、Application、页面、图片图标、遥控映射、Gradle 门禁、依赖、manifest、发布元数据及签名冻结控制保持。普通包名 `com.ichichuang.cinema`、Debug `.debug`、versionCode=18、versionName=0.9.8、minSdk=24、targetSdk=28、正常 ARMv7 保持。测试 APK 和独立迁移同意只在宿主确认的新建自有 API30 ARM64 AVD 中使用；未访问 TV、个人设备、真实旧记录、线上片源或签名凭据。

## 门禁与验收矩阵

| 场景 | 结果及证据层 |
| --- | --- |
| 普通 Debug／Release，Probe OFF，无交接 profile／意图 | 原 Legacy；普通产物 BuildConfig 均 false；真实 Legacy OFF 方法通过 |
| Probe ON，未知旧 profile／旧文件，没有单独迁移授权 | 保持 Round 06 拒绝；既有原生门禁测试与新增独立同意测试通过 |
| Debug、同签名测试 APK、两种私有同意、预声明自有 profile、限定镜像全部满足 | 允许实际捕获／审计，激活前仍 Legacy；正向签名与目标检查实际执行 |
| Release、另一 AVD、测试 APK 不匹配、未知 profile | 授权纯逻辑各反例通过；Release 等六类非法 Gradle 请求在执行任务前拒绝。没有为制造反例操作其他设备或安装异签 APK |
| 旧摘要变化、未解析／损坏进度、映射或实际媒体变化 | 保留原始 staging，拒绝激活；实际替换同 URI WAV 的原生断言通过 |
| 已激活且授权仍有效 | Room 独占进度；旧 save／clear／browse／filter／settings 写入拒绝 |
| 已激活后 Probe OFF、撤销同意、缺库或缺 activation 意图 | 明确 Blocked／Failed，不选 Legacy；Probe OFF 配对 APK 在已激活 profile 实际执行通过 |

附件 R07-A01–A15 的证据对应如下，均仅限所列测试子场景：

| 附件场景 | 实际证据 |
| --- | --- |
| A01、A13 | 8 项授权主机测试、原生门禁／Legacy OFF／激活后 OFF；六类构建请求拒绝 |
| A02、A04 | 四个既有单例的 typed 捕获；10 项主机审计；原生 malformed／bare ID 原值保留及拒绝 |
| A03、A10、A11 | 真实 WAV／Media3 旧2000ms暂停恢复、新7520ms提交、独立进程暂停恢复、旧文件哈希不变 |
| A05、A06、A09 | 四阶段取消／事务内 SQL-full 注入／捕获和 staged 确认 IO 异常；staged 后换进程仍 Legacy |
| A07、A08 | 内容摘要幂等、实际旧字段变化、stale digest 拒绝、ACTIVE 确认丢失重试不覆盖较新 rewatch=0 |
| A12 | 激活后缺授权／缺库／缺意图，保守阻止，不创建新空库或修改旧文件 |
| A14 | 真实 ImageCache.clear 保留 Room 进度、收藏及原始 browse／filter／来源 staging；不代表三种产品移除流程已验收 |
| A15 | 全部103个源码方法与start／terminal逐项一致，0失败／跳过／未执行／重复 |

## 独立审查与实际 RED→GREEN

一次 fresh-context 独立只读审查核对审查时35个候选文件 SHA-256 前后一致；0 Critical、3 Important、0 Minor。审查者没有运行构建或操作设备。三项问题均补实际原生失败测试，四个新方法先4失败，再分项修复为1＋1＋2通过，最后重跑完整回归。修正后没有第二次独立复审，作者以红绿与全量结果负责确认。

| Important | 真实失败 | 修正与绿色断言 |
| --- | --- | --- |
| 取消旧 writer 可早于 DataStore actor 写盘结束而释放屏障 | 在真实 Preferences `writeScope` 内阻塞 IO 后取消调用方，捕获提前读到旧2000ms | 测试 profile 已取得权限的 edit 在 NonCancellable 等待实际 ack，随后传播原调用方取消；屏障捕获必须等到3000ms真实写盘。取消等待锁仍不启动写入 |
| prepare 到 activate 之间媒体变化未重验 | 替换 WAV 第44字节、保持 URI／ID／variant，原manifest仍返回Committed | 可信 fixture 提供可重读目录证明；prepare和激活事务内重新计算，变更后拒绝，progress／session／resume均零新增 |
| 切集／retry 可在已替换媒体上继续开新会话 | 实际VM切到第2集；真实retry没有抛出拒绝 | 串行队列内、副作用前重新核验；VM保持原集并给出原错误状态，无新增session或checkpoint，旧文件不变 |

DataStore actor 语义按仓库实际1.2.1版本的 [官方源码](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-core/1.2.1/datastore-core-1.2.1-sources.jar)核对；没有升级依赖。文件系统与 Room 无共享事务，不声称抵御任意同时恶意媒体改写。原控制器、媒体指纹、授权等新增测试也先有实际断言 RED；编译语法错误不计为行为 RED。

夹具和运行器纠正另记：schema2 fixture 最初漏建导出索引，已按真实 schema 建表及索引；新增manifest helper括号错误修正后重新编译；撤权测试先等待真实有效播放及 pause onEvents，再检查失败提交。一次 Python discover 指向上层目录执行0项，已改回仓库 `scripts/tests` 实跑62项；签名CLI／AVD创建显式选用现有 mise Java，未安装运行时。第二个AVD先等待原端口TIME_WAIT结束，未中断其他服务。这些纠正不计为实现红绿或新增通过方法。

## 全量主机验证与普通产物

使用现有 mise `java@temurin-21.0.11+10.0.LTS`、Gradle9.8.1、AGP9.4.1、现有 SDK／API30 ARM64 镜像；无依赖或全局工具安装。主要命令（SDK环境由忽略的run.py注入）为：

```sh
mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
  :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease \
  --rerun-tasks --no-daemon --no-configuration-cache --console=plain
mise exec -- python3 -m unittest discover -s scripts/tests -v
```

- 修正后140／140个Gradle任务实际执行，JVM199／199、Python62／62通过，0 failure／error／skip。
- Debug Lint：0 Error、21 Warning、1 Hint；Release：0 Error、22 Warning、1 Hint。id／severity／message 多重集合与Round06完全相同，新增／删除均0；保留现有Gradle10兼容性弃用提示，没有压制检查。
- Probe ON只配对构建 `:app:assembleDebug :app:assembleDebugAndroidTest -Pcinema.playbackRoomProbe=true -Pcinema.storageTestArm64=true`；Probe OFF原生配对仅显式ARM64属性。六类Release／single-Debug／bundle-publish／install／signing／注入Release任务图请求均在执行前拒绝。
- 原生完成后两属性均关闭重建普通Debug／unsigned Release／test，ABI与下表相符；实际BuildConfig均为false。各角色manifest及两份JNI库ELF／SHA与Round06相同，不能将APK整体称为旧版原字节。

| 最终普通产物 | 身份／ABI | SHA-256 |
| --- | --- | --- |
| Debug | .debug／armeabi-v7a | `c8c379a3b13e96a614c1cc92951f39cf54f224ec18bb974e356eae550605ec5d` |
| unsigned Release | com.ichichuang.cinema／armeabi-v7a | `b02e13c71d1e379c73e89f5d9b67e06dda2ea2bdf1b39e0466060b72483e2fb8` |
| AndroidTest | .debug.test／无JNI | `a5b63b88b302c2d6b2ca02f8be13f5720eb483a27c1c51479e8fe573bb061cfb` |

apksigner 对普通Debug／test验证退出0，对unsigned Release退出1且报告缺少签名manifest，符合本轮未签名构建边界；未读取或使用生产签名材料。各次原生配对APK副本、manifest／runner、ABI及独立SHA保存在对应执行目录，未声称不同构建的APK整体字节相同。

## 原生目标、逐方法计数与独立进程

| 用途 | 自有AVD／宿主PID | boot ID |
| --- | --- | --- |
| 交接及故障演练 | cinema-handoff-20261024-655a7889／45554 | `55df72d3-43cd-4e41-a62f-ac5118003980` |
| 既有空白profile回归 | cinema-handoff-20261024-4a0092cf／79941 | `d637bd2b-9a12-41d5-85c6-a8f0a489f623` |

两者顺序独占 `emulator-5566`，不同时运行。第二个profile初始第三方包列表为空；第一个保留本轮合成旧文件，无法满足旧探针空白前置条件，因此关闭保留后新建第二个，不清空旧profile。两者均为现有API30 default arm64-v8a镜像、pixel_2、qemu=1、abilist32为空、boot_completed=1；不代表Android TV。所有mutating ADB操作固定 `-H 127.0.0.1 -P 5037 -s emulator-5566`，每次安装／执行／force-stop／关闭前检查AVD名、宿主PID与启动参数、boot ID、API／ABI／qemu、包清单及runner。首次安装前先检查配对APK runner／target；后续包清单仅测试Debug和test。两个AVD最终均经身份门禁关闭，宿主PID已消失，数据及共享ADB server保留。

| 实际执行类 | 方法数／通过 |
| --- | --- |
| CinemaDatabaseTest | 15／15 |
| LegacyUserDataImportTest | 19／19 |
| PlaybackCheckpointNativeTest | 26／26 |
| PlaybackSchemaMigrationTest | 2／2 |
| PlaybackRuntimeNativeTest | 7／7 |
| PlaybackProcessReplayTest | 2／2，分别进程执行 |
| LegacyPlaybackRuntimeNativeTest | 1／1，Probe OFF |
| LegacyHandoffGateNativeTest | 4／4 |
| LegacyHandoffNativeTest | 17／17 |
| LegacyWriterCancellationNativeTest | 1／1 |
| LegacyHandoffMediaNativeTest | 4／4 |
| LegacyHandoffProcessTest | 5／5，含Probe OFF单独APK |
| 唯一方法合计 | 103／103，0失败／跳过／未执行／重复／未知 |

实际分批为：交接／schema套件28；旧核心67；新交接独立进程4；交接OFF1；原播放独立进程2；原Legacy OFF1。不是一次同进程suite，不把重复红绿执行加入最终唯一计数。源码inventory、start和terminal code0逐项对应。

最终新交接进程证据：

1. PID5660在staging commit后注入确认IO异常；force-stop后PID5759读取持久staged，仍选Legacy，旧2000ms和原文件不变。
2. PID5829从旧2000ms关联激活，在真实PlayerViewModel／Media3中暂停恢复2000ms；播放到可保存后，等待实际pause onEvents并明确boundary成功，提交7520ms、duration12000、sequence2。
3. 仅对测试包force-stop，PID5935恢复完整相同Room快照，实际position7520、playWhenReady=false、isPlaying=false，延迟后仍相同；不退回旧2000ms。
4. 安装配对Probe OFF APK，PID6101对已激活profile实际断言播放器与旧save都拒绝，旧文件字节不变。

既有播放进程回归另外为PID2682提交5533ms，PID2787完整恢复并暂停。生成的12秒本地PCM WAV只在测试私有目录，无外部媒体请求。证明的是最后成功提交和重新构造测试VM后的恢复，不是Activity／导航系统自动恢复，也不保证未提交瞬间或任意断电。

## 原始数据与schema保留证明

最终新交接A／B／OFF均比较四个合成旧文件的完整SHA-256。测试setup的授权写入和迁移本身分开计量，实际旧文件不因prepare／activate、拒绝或异常而改写：

| 文件 | A前、A后、B、OFF一致的SHA-256 |
| --- | --- |
| cinema_progress.preferences_pb | `c6afa1d4f616a6cd233d74ee774de1e75bb75d2dde420673754d17bc84e523cc` |
| cinema_browse.preferences_pb | `b72b7fe4989d82b28ef151d3456c5840c8de32ad08637289b05efc2e9c50eb88` |
| cinema_filters.preferences_pb | `0372d357cb27391361228c7fe5be15a77ac8f9059eee34d8c1437726be7cbecd` |
| cinema_settings.preferences_pb | `9513cda7ee3c221be359c915f6f816b9261360fd89db48e0af666ec860bf68b7` |

对应内容摘要 `c019bb3a414f444306b67edff81d706d25c5453280160455081f5bb9e95f070f`。真实singleton捕获和完整staging重构／accounting校验另外验证typed值。实际旧字段改变会产生新摘要，旧digest激活拒绝；异常／未解析记录原raw仍在staging，原writer保持。

原v2缺少持久handoff authority，所以显式增加v3单独表／外键／索引，不改旧表字段，不启用破坏性fallback。两项原生测试分别从原schema1经过1→2→3及原schema2经过2→3，种入并逐字段比较旧favorites／browse／progress／staging；v2还保留sessions／record-order／resume。未来不支持版本测试由3改为99，因为3现为合法当前版本；仍实际拒绝不支持升级并保留旧行。原fixture固定原来三种store，不把新增SETTINGS误作既有seed文件。

| schema | SHA-256 |
| --- | --- |
| 1.json，原字节保持 | `14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959` |
| 2.json，原字节保持 | `6b5abe547c00a7a2d2cf2ab1e30e995347b3d9cd014c9700a639e5f87677aca9` |
| 3.json，Room实际导出 | `bda6901970ecceb7c0c1adbe8aec0f0a665243cce9e61e95fcade2b0c3c98b95` |

## Git安全检查、原始证据与停止边界

提交前检查分支、status、upstream及ahead／behind，`git fetch --no-tags origin`成功后确认main和origin/main均原基线。没有同步、覆盖其他工作或建立upstream。实现仅暂存36个明确路径；独立检查staged／unstaged diff，33项受测源码与暂存blob逐项相等，未暂存diff为空。实际index检查260个tracked文件、0所检查的违规；仅覆盖索引中的选定凭据／发布二进制／个人路径等规则，未扫描历史，不是全面安全或许可证结论。

本报告与账本按两个明确路径暂存后，同一完整索引检查261个tracked文件、0所检查的违规，退出0。最终33项受测源码与实现commit blob及工作区逐项相等，本地Markdown链接均可解析，未暂存diff为空。main保持原样；文档提交与最终HEAD见本轮交付及 `final-delivery.json`。本轮没有阻碍已授权隔离切片交付的未决问题。

原始命令、退出码、RED／GREEN、逐方法inventory／start／terminal、身份门禁、两AVD记录、每次APK配对副本／SHA／manifest／ABI、JVM／Lint XML、旧文件与staging摘要、schema和Git blob指纹均保存在忽略目录 `.local/evidence/round-07/`。核心入口为 `final-regression-summary.json`、`final-native-summary.json`、`handoff-process-console.log`、`handoff-process-expected.json`、`implementation-staged-blobs.json`；不提交APK、媒体、AVD或个人路径。

仍未验收的产品关口：

- T03整体：真实旧用户身份关联、真实应用升级／备份／回滚、生产来源可见性与生产迁移切换；本轮只有受控合成profile。
- T04整体：普通生产播放器和真实来源逐集／时间轴／生命周期持久闭环；候选generation／sequence及WAV测试不替代它。
- M02：还需要T05／T08等真实产品最小路径，未完成。
- A05：Activity／导航现场、系统回收及生产重启自动恢复；本轮仅测试重新构造VM和app进程。
- A06–A09：真实剧集来回切换、实际二十分钟快退、初始化／retry／旧回调、最新集／预加载的全产品语义与TV运行；已有候选断言只作对应子证据。
- A10：收藏后立即退出、写盘失败的真实UI反馈；A11：三种移除操作的产品删除范围。只核验合成缓存清除保留数据。
- A20：真实旧版升级中断／重复执行／跨源关联及回滚；当前故障注入和process restart不足以验收。
- ARMv7／API24实机、TV视频解码／D-pad／视觉、任意电源中断、真实磁盘耗尽、生产签名与发布均未验证；SQLiteFullException是事务内注入，没有填满磁盘。

停止于Round07，不进入Round08／T05／T08，不启用默认Room或改变品牌UI。
