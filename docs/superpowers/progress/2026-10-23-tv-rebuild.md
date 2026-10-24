# TV 重构进度

Round 01–06 已留存，当前完成限定的 Round 07 自有合成旧记录交接演练；普通路径生产接入与整体验收仍未完成。原计划文件保持原样，执行状态以本记录及验证报告为准。最初基线 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`，隔离分支 `codex/contract-foundation`。

## Round 07 执行记录

用户明确授权按附件执行，仅本地提交，不推送、合并或发布。起点 `a7deea8a88ff0afa2f7ba06fd7c4f615f8018af1`，隔离分支及 main 均干净；包内四项 SHA-256 和三个独立附件逐字节一致，原计划导入。

- Pre-flight: R07-1 的默认关闭授权和实际旧 writer 图供 R07-2 持久状态及 R07-4 探针使用；R07-3 实际单例捕获和精确映射供 R07-2 激活事务使用；R07-5 验证全部边界。
- Ruling: 沿用既有账本及 R07 标题和 `.local/evidence/round-07/`，不改写附件来适配技能数字 Task 脚本，不删除原始证据。代价：人工对应步骤及保留本地证据空间。
- Ruling: 原工作树不是 App managed worktree，原生附加工具已明确拒绝；继续使用现有 Git linked worktree，不另建或重置。代价：本轮不增加 App 管理附件。
- Ruling: v2 无交接 authority 表，新增 v3 单独状态表及显式2→3迁移；原1／2 schema字节保持，1→2→3与2→3均做native保留测试。代价：候选数据库的schema增加一版，普通生产路径仍不开库。
- Ruling: 演练只启动播放器；browse／filter／来源原始值完整staging并保留旧只读访问，激活后这些旧writer显式拒绝修改，不提供新配置UI或生产导航切换。代价：本轮不能宣称完整产品迁移。
- Ruling: 分支没有 upstream 是既定 local-only 用途；用户本轮明确禁止 push，保留该状态，不建立远端分支。

| 步骤 | 状态 |
| --- | --- |
| R07-1 | 基线原生68＋独立进程2、JVM177实际通过；最终授权8项及单例捕获／门禁原生4项通过，普通生产入口保持 |
| R07-2 | 真实 Room 状态、AtomicFile 意图、屏障内重新捕获及单事务激活已核验；17项状态／导入／故障原生测试通过，缺库／缺授权／缺意图均不退回旧写入 |
| R07-3 | 四个既有 DataStore 单例捕获，完整 typed staging／accounting；10项主机精确映射测试及原生幂等、内容变化、损坏／未解析保留通过；原文件哈希一致 |
| R07-4 | 真实 WAV／Media3：旧2000ms暂停恢复→新Room7520ms提交→PID5829换5935完整相同快照暂停恢复；实际Probe OFF拒绝回退；媒体替换及撤销授权4项原生通过 |
| R07-5 | 最终JVM199／Python62／native103逐方法全部通过，无skip／not-run／重复；140个普通Gradle任务实际执行；双Lint与Round06相同；一次独立审查的3项Important均实际RED→GREEN，未二次复审 |

- Final review: 35个审查时候选文件前后SHA一致，独立只读审查0 Critical／3 Important／0 Minor。修复为：测试profile持锁等待已接受的DataStore actor真实IO确认再传播取消；prepare／激活重验实际媒体证明；切集／retry在副作用前重验目录。新增4项原生断言先4失败，分项修复后4通过，再执行完整回归。作者负责修正后验证，没有第二次独立审查。
- Ruling: 交接AVD保留了本轮合成旧文件，不能满足原Round06空白profile前置条件；关闭并保留原数据后，用现有镜像新建空白自有AVD跑67项既有核心及2项进程／1项Legacy OFF测试，不清空旧profile。两个AVD逐操作门禁、最后均已关闭，host PID消失，共享ADB保持。
- Ruling: 状态、审计、屏障及探针互相依赖，采用验证后的一个完整实现提交，再单独记录交付；不提交临时stub或未通过状态。
- Local delivery: 实现提交 `e8fecd200c1cfe32f18e94dff974d7844a30401b`；36个明确暂存路径，33个应用／测试／schema指纹与验证及commit blob一致；实现index260文件、0所检查的违规。main与刷新后origin/main均仍为原基线，隔离分支仅本地保留，无push／merge／publish。
- Final documentation: 验证报告与本账本定稿独立本地提交；最终完整index261文件、0所检查的违规，33项受测源码保持与实现提交相等；最终HEAD由Git日志及忽略证据final-delivery.json记录。
- Evidence: [完整本轮验证](../../verification/2026-10-24-round-07-legacy-handoff.md)；[本轮结构](../../architecture/tv-legacy-handoff.md)；原始证据在忽略目录 `.local/evidence/round-07/`。普通Debug／Release仍旧存储独占，T03／T04整体、M02、A05–A11／A20、真实用户升级、ARMv7／TV及签名发布仍未验收；停止于Round07。

## Round 06 执行记录

起点实测 `d75cb40622a5adc8d9680a198063d0a0fc01cec4`，分支 `codex/contract-foundation`；隔离工作树与 main 起始均干净。包内三项 SHA-256 及独立文档字节一致，计划原样导入。仅授权本地提交，禁止 push/merge/publish/真实设备及后续轮次。

- Pre-flight: R06-1 门禁产出唯一 writer 模式，R06-2 bridge 消费；R06-2 不可变事件与真实身份供 R06-3 Media3/Room 和独立进程验证；R06-4 在两属性关闭后重建普通产物。
- Ruling: 沿用本账本和 R06 标题，不套用技能数字 Task 脚本；原附件不改写。代价是手工记录每步证据。
- Ruling: 旧 MovieDetail 无稳定 episode/variant ID，只允许测试仓库显式注入既有 PlaybackTarget 和私有本地 URI；线上仓库无法证明身份则拒绝探针播放。代价是本轮不能用于真实来源或宣称生产接入。
- Ruling: 运行时拒绝任何旧 progress/browse/filter 文件，包括空文件；不解析、隐藏或清除旧记录。代价是只能使用全新可丢弃 profile。

| 步骤 | 状态 |
| --- | --- |
| R06-1 基线和门禁 | 起点／源码／既有工作树已核；新跑基线JVM149/149；六类非法请求均在执行前拒绝，默认和旧记录门禁有用例 |
| R06-2 事件和生命周期 | 最终JVM177/177；真实retry与两项审阅竞态均实际RED→GREEN；单调事件时间、切集屏障、单次请求取消及后台暂停保护已接入Probe |
| R06-3 实际 Media3 / Room 与进程恢复 | 最终71/71，逐方法源清单与start/pass对应：68主套件＋2独立进程＋1 Legacy OFF；AVD afb36413／emulator-5564 已关闭；PID2677→2783恢复5554ms且暂停 |
| R06-4 回归、独立审阅、安全和本地提交 | 两属性OFF全量140任务实际执行；Python62/62；双Lint 0Error，21/22Warning各1Hint与Round05相同；实现本地提交23dfac4；最终完整index安全检查238项0违规；文档提交见验证报告 |

- Final review: 一次独立只读审阅核对15个候选文件指纹；0 Critical、2 Important、0 Minor。发现“慢切集／retry覆盖较新的后台暂停”和“取消单次await仍可能执行旧媒体副作用”；两项均补真实SQLite锁／单次Job取消的确定性RED并修正，完整回归已通过。修正不再独立复审，作者用红绿和全量证据负责确认。
- Ruling: 双进程 Phase A 的预期取自真实 pause onEvents 处理后、明确 boundary 提交之后，Phase B 保留完整快照比较。首次失败是预期记录早于合法的下一次同位置提交，不能将较早回执称为最后提交。代价是测试需显式等待播放器事件，不能以任意固定延迟冒充同步。

- Final verification: 首次全量Lint暴露3个API26调用错误；改为兼容min24的canonical File边界校验，路径／URI越界测试3项红绿后重跑全部。schema1/2、原coordinator/store、DataStore、Koin、manifest及发布身份不变。
- Local delivery: 实现提交 `23dfac429d5fc1cf8a547f8cb69304490593b617`，暂存236个index文件安全检查0违规；原main／刷新后origin/main仍为 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`。无upstream的本地分支用途明确，不建立远端分支，不push/merge/publish。
- Evidence: [完整Round06验证](../../verification/2026-10-24-round-06-playback-runtime.md)；[运行时结构](../../architecture/tv-playback-runtime-probe.md)。真实旧用户迁移、ARMv7／TV视频和遥控、Activity导航自动恢复、T03／T04整体、M02、A06–A09均未验收。本轮停止，不进入后续工作。

## Round 05 执行记录

用户明确授权按附件实施、仅本地提交不推送。起点 `a46a8a97947d1b72fefd3361f9742df9540a5be6`，原 main `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`，两处干净；复用原工作树。计划原字节导入，SHA-256 `662e6d80c8b1ebc30f2e25abe91c5bf35d689d34a246be24aa1d6c579677a039`。完整证据见 [Round 05 验证](../../verification/2026-10-24-round-05-playback-checkpoints.md) 与 [候选结构](../../architecture/tv-playback-checkpoints.md)。

| 步骤 | 实际状态 |
| --- | --- |
| R05-1 真实契约／事件映射 | 沿用原 snapshot／commit；现有播放器、schema、旧文档和官方 Media3 语义已核对；生产源码保持 |
| R05-2 单调钟与候选协调器 | JVM 红绿及最终 25/25；五秒实际播放、零值／时长、快退、预加载、切集屏障、完成及取消 |
| R05-3 原子提交／持久仲裁 | 真 Room generation／sequence／payload 幂等及独立作品指针；原 schema 1 不改，真实 1→2 增量迁移；原生全部 61/61 实际通过 |
| R05-4 未来事件接入 | 记录 Media3 ready、playing、buffering、seek、ended、后台语义；未接现有播放器或 DI，不将候选视为运行时修复 |
| R05-5 回归／审阅／本地交付 | 修正后 JVM 149/149、Python 62/62；134 个普通 Gradle task 实际重跑；Debug／Release Lint 21／22 Warning，各 1 Hint、0 Error；暂存安全检查和提交信息见验证报告交付段 |

- Pre-flight: 真实 domain/schema 是策略与 adapter 的输入；策略事件和持久代次分别控制调度及跨会话顺序；迁移与实际 AVD 共同决定 native 证据；property-off 产物必须在 ARM64 验证后独立重建。
- Ruling: 继续使用原账本及 R05-1–5 标题，不改写附件以适配技能数字 Task 脚本；计划原字节导入。代价：人工对应步骤。初期“原文含私人路径”的旧轮次假设已由本轮实际扫描纠正，没有因此改正文。
- Ruling: 新选普通观看以 5,000ms 实际播放提升；4,999ms 误点退出不提升；真实短片结束可完成当前集。显式 seek／完成的失败或取消保存独立保留待确认状态。代价：阈值是待未来产品／实机评估的政策，不能宣称故障下最多丢五秒。
- Ruling: schema 2 新增三张仲裁／续播表，原七表和 1.json 字节保持；不从旧记录推测会话代次或作品指针。代价：真实旧 DataStore 迁移、身份关联与 runtime cutover 仍未完成。
- Final review: 一次 fresh-context 独立只读审阅，0 Critical、3 Important、0 Minor；三项均实际 RED→GREEN 修正。五项 JVM 回归先失败；真实 Room 两项组合先失败；修正后 149 JVM／61 native／62 Python 全绿。修复为短会话显式保存重试、真正重看恢复进行中、锁前捕获事件时间防止慢写入虚增观看。没有第二次独立复审，没有延后 Minor。
- Final: Ruling: 审阅保留项继续未验证／范围外：生产 Media3／生命周期／TV、真实旧 DataStore 升级回滚备份、跨源身份与时间轴、进入 API 前的外部事件乱序、违约复用 sessionId／低层 DAO 写入、长期保留与删除政策、真实断电／进程／磁盘故障、全剧完结及 UI 展示。代价：候选测试不提供这些产品关口的通过证据；本轮不扩张去实现。
- Ruling: 用户已选 local-only，按保留分支／工作树收口，无需再次询问 merge／push 菜单。原始证据保留于忽略目录，不按技能默认清理证据。代价：没有远端备份，AVD 文件占用保留但已关闭。
- 本地实现提交：`194040bf92868c15b8570e136d515ac3546a41eb`，15 个精确路径；提交前 index 223 文件、0 违规。本报告／账本另作本地结项提交，最终文档 index 224 文件、0 违规，已核对暂存 blob 与工作区字节；最终 HEAD 见交接记录，不推送。

## Round 04 执行记录

起点 `541b7ce5370693c5f96c848bb0cd3e8ec8cbc7b5`，两处 checkout 干净。唯一执行计划 `2026-10-24-round-04-abi-native-verification.md` 的 SHA-256 为 `67c5a1c73022423ed574ec3ebe9f57d27b151e16790c14dfd0dbc6bffff096cc`；包内校验及独立附件字节均一致。原始计划和日志在忽略的 `.local/evidence/round-04/`；详见 [Round 04 记录](../../verification/2026-10-24-round-04-abi-native.md)。

| 步骤 | 实际状态 |
| --- | --- |
| R04-1 ABI 根因与路线 | property-off 重建、实际 APK / AAR / ELF / AGP 合并逻辑已核实；选择 Route B，复用现有 API 30 ARM64 镜像 |
| R04-2 隔离测试产物 | 打包前置断言 RED → GREEN；真实 ARM64 库、482 个非 JNI 条目相同；property-off 三个 manifest / JNI 与基线一致；Release / bundle / publish 及依赖注入门禁通过 |
| R04-3 单目标原生执行 | 新建唯一 API 30 ARM64 AVD，emulator-5562；15 + 19 方法全部实际执行 / 通过，0 失败 / 跳过 / 未运行；目标已关闭 |
| R04-4 实际缺陷修复 | 没有原生缺陷可修复，全部存储 / 测试源码保持；仅修正了本轮构建门禁误判 |
| R04-5 回归、审阅、本地交付 | 应用 124/124、Python 62/62、135 个 Gradle 任务实际重跑通过；Debug / Release Lint 21 / 22 Warning，各 1 Hint、0 Error；独立只读审阅无 Critical / Important / Minor；审阅后 index 212 files、0 违规；仅本地交付，最终提交 HEAD 随终端交接记录，不推送 |

- Pre-flight: R04-1 的实际依赖 / ABI 决定 R04-2 产物；R04-2 的兼容产物及 R04-3 的目标身份共同决定能否执行；仅 R04-3 复现的缺陷允许 R04-4 修复；R04-5 必须独立重建 property-off 产物，不能混用 opt-in 结果。
- Ruling: 沿用既有账本对应 R04-1–R04-5，保留原计划字节而不改写为技能脚本的数字 Task 标题；原始证据不删除。原因：与既有记录及附件证据要求一致。代价：步骤需人工对应。
- Ruling: 原始计划含个人路径，按既有 index 安全规则仅保存在忽略证据中，Git 记录文件名及 SHA-256。代价：复核原字节需要本地附件 / 忽略证据。
- Ruling: 本轮明确只允许本地提交，不推送；专用分支无 upstream 是既定交付状态，不建立远端分支。代价：本轮没有远端备份。
- Ruling: 使用现有 ARM64 镜像与真实 AAR 库，不下载其他镜像；明确两个 buildType 的 ABI，避免实际 addAll 合并造成混合 ABI。代价：测试结果只能证明 ARM64 候选行为，ARMv7 实机仍未验证。
- Ruling: 保留 `.debug` 测试身份，因为原 fixture 明确要求此身份；以新建一次性 AVD 和逐命令 serial 门禁隔离数据，不修改原测试。代价：测试 APK 使用常规 Debug ID，必须坚持目标身份门禁。
- Ruling: 保留两处 buildType ABI 声明造成的额外一条重复 ChromeOS 告警；实际普通产物与基线相同，不压制 Lint 或增加范围外 ABI。代价：Debug / Release Warning 数各增加 1。
- Final review: 一次独立只读审阅，无 Critical / Important / Minor；未重跑测试，无审阅修正或二次复核。九项保留边界逐项裁定见 R04 报告：TV / ARMv7、进程 / 断电、真实耗尽、真实迁移 / T04、完整读失败编排、其他 DataStore 类型、设备端回提均未验证；index / commit 由作者随后实查；整个 T03 / M02 / A01–A24 与发布继续不作通过声明。代价：本轮原生证据严格限于所列候选操作。

## Round 03 执行记录

唯一执行计划为附件 `2026-10-24-round-03-native-storage-verification.md`，SHA-256 `4f0b86c7a3efe4a4349e6fd2a97a4a93b4fd829b87c7205e15b41cb479cc7f17`；原字节及原始输出保存在忽略的 `.local/evidence/round-03/`。实查起点 `f588ed11d6f998b36de16feaa7a141fda1cc2bef`，两处 checkout 干净、无 HEAD 漂移。详细证据及全部 34 项方法见 [Round 03 记录](../../verification/2026-10-24-round-03-native-storage.md)。

| 步骤 | 实际状态 |
| --- | --- |
| R03-1 交接、工具链与兼容目标 | 清点 15 + 19 项原生方法，无 Ignore；构建成功；最小新增 emulator 37.2.12 与一个 API 30 arm64 镜像；APK 仅 armeabi-v7a，而镜像 abilist32 为空，NATIVE_TARGET_BLOCKED |
| R03-2 单目标原生执行 | 未创建 AVD / serial，未安装 APK、未启动 runner；executed / passed / failed / skipped 均 0，not run 34 |
| R03-3 实际失败修复 | 未开展；没有原生失败或红绿证据，应用 / 测试 / schema / 契约全部保持 |
| R03-4 可用回归与证据 | Python 62/62、应用 124/124；134 个 Gradle 任务实际重跑成功；debug Lint 20 Warning + 1 Hint，release 21 Warning + 1 Hint，新增一条 DataStore 版本提示；一次独立只读审阅无 Critical / Important / Minor；实际暂存 index 211 文件、0 违规；仅本地证据提交，最终 HEAD 随终端交接记录，不推送 |

- Pre-flight: R03-1 的真实 APK / runner / 兼容目标是 R03-2 的输入；R03-2 的实际失败才允许 R03-3 修改代码；R03-4 必须区分 host、compiled-only、native 和 blocked。ABI 不匹配使原生执行与修复步骤不可进入。
- Ruling: 沿用现有账本对应 R03-1–R03-4，不改写原计划以适配技能数字任务脚本。代价：人工对应状态。
- Ruling: 只裁定已核实路径因 ABI 受阻，不把未验证的 ARM32 / 其他镜像路径说成不可能；停止额外下载或 ABI 改造，完成可用主机检查并据实交付。代价：34 项仍为 compiled-only，整个 T03 / M02 / A01–A24 未验收。
- Ruling: 原始计划含个人路径，保留在忽略证据中；Git 仅提交报告与本账本，保留原计划文件名 / 摘要。代价：原字节需通过本地附件复核。
- Ruling: 按本轮 scoped plan 允许的阻塞证据提交完成本地交付，没有 upstream 也不推送 / 合并。代价：没有远端备份；T04 未开始。
- Final review: 一次独立只读审阅，无 Critical / Important / Minor，没有修正或二次复核。审阅者重读实际产物并重新解析现有结果，未重跑测试。其保留项均维持原生 / 目标门禁 / 其他环境未验证、真实迁移 / 实机 / 生产关口范围外、历史操作全面证明不成立；暂存与提交终态由作者随后实查，不把本轮解释为产品验收。

## Round 02 执行记录

计划：`docs/superpowers/plans/2026-10-23-round-02-storage-foundation.md`。实查起点为 `4ddd1530bb06f4faa8ac2f05cec0f440d2a453ee`，原 main 及隔离工作树均干净，无基线漂移。ZIP 四项清单哈希、独立附件和两份既有计划哈希均一致；仅导入 scoped plan。

| 步骤 | 状态与依赖 |
| --- | --- |
| R02-A 基线与映射 | 已核对十一模型／五接口及三类旧 DataStore；新鲜基线应用 100/100、Python 62/62、完整构建与 Lint 通过 |
| R02-B 编码 | 已实现并验证；24 项新增纯测试，含审阅修正；全部应用 124/124 |
| R02-C Room 候选 | 七表、真实生成 schema、browse／favorite 候选及低层 playback DAO 已编译；原生 close/reopen／SQL 约束仍阻塞，不接运行时 |
| R02-D 保留式导入 | reader／typed snapshot／无损 digest／staging＋batch 事务已实现；合成导入与真实事务故障仅有原生测试编译证据，执行阻塞 |
| R02-E 验证矩阵 | 应用 124/124、Python 62/62、debug／unsigned release／双 Lint／原生 APK 编译成功；34 项原生测试 0 执行，无已核实的一次性 emulator |
| R02-F 审阅与本地提交 | 整轮独立审阅及 Important 修正定向复核已完成；实现提交 49a2032，25 个候选文件与提交字节一致；实现／最终 index 扫描 209／210 文件，均 0 违规；本记录另做文档提交，不推送、不合并、不发布 |

- Ruling: 继续沿用 R01 对字母步骤的记录方式，保留 scoped plan 原字节，原始输出放在忽略的 `.local/evidence/round-02/`；不改写为技能脚本专用数字任务。代价：步骤状态需人工核对。
- Ruling: 用户常驻规则要求简体中文提交信息，优先于附件中 English commit messages 的一句要求。代价：附件该句不执行，代码注释仍用英文。
- Pre-flight: A 的真实契约是 B 的输入，B 的格式是 C 的输入，D 的 snapshot 和 C 的事务共同决定 E 的测试；没有新增公共接口，也不实现 PlaybackProgressStore.commit。
- Ruling: 独立审阅实证 UTF-8 替换会合并不同 String；JSON surrogate 转义、精确 UTF-16 BLOB 键及已有 batch 的完整行比较共同修正，四项新测试实际 RED → GREEN，原审阅者定向复核清除 Important。代价：首个未发布 schema 使用内部键编码，后续版本必须按该格式真实迁移，不能按 TEXT 身份猜写。
- Ruling: 原生目标不可用时仅编译已先写出的真实 Room／DataStore 测试，不以主机 codec 或 mocks 代替原生红绿。代价：34 项原生测试及存储／导入的原生验收保持阻塞，本轮只交付已通过可用检查的隔离候选。
- Final review: 一次整轮只读独立审阅及同一审阅者的 Important 修正复核；没有未解决 Critical／Important／Minor。原生执行、真实旧数据、断电、升级、运行时 cutover、T04 仲裁均不作通过裁定；最终 Gradle 与解析图以终态日志为准。

## Round 01 历史步骤

| 步骤 | 状态 | 证据 |
| --- | --- | --- |
| R01-A 输入、隔离、本地基线 | 本地部分已核实，设备/旧样本未验证 | [基线记录](../../verification/2026-10-23-round-01-baseline.md) |
| R01-B 数据值对象与单测 | 已实现并完成定向测试 | 最终 25/25；原 9 个输入校验断言及审阅新增总量断言先失败后通过 |
| R01-C 纯接口及迁移边界 | 已定义并编译 | 五个接口，无实现或运行绑定；[契约文档](../../architecture/tv-data-contracts.md) |
| R01-D 回归、审阅、本地提交 | 本轮本地交付已核实，停止于 Round 01 | 应用 100/100、脚本 62/62、两种构建及 Lint 成功；186 个暂存文件安全扫描无违规；保留本地分支 |

## 执行决定

- Ruling: 桌面工具无法识别子仓库后使用原生 Git worktree 隔离；保持同一基线与独立分支。代价：该工作区未登记为桌面托管附件，保留明确的相对位置。
- Ruling: 分轮计划使用 R01-A–D 标题，技能脚本要求数字 Task 标题；保留原计划不改写，以本记录对应步骤并保存测试原始证据。代价：不能依赖脚本自动识别步骤。
- Ruling: 按分轮计划暂不定义序列化格式；总计划的序列化要求留给后续明确存储格式时完成。代价：当前类型尚不能宣称可落盘或兼容迁移。

## 独立审阅裁定

审阅范围 `7f3e063..6a56515`。两个发现已在 `41bb688` 处理：已知总量矛盾有实际红绿测试；焦点回退文档已逐句对齐主规格 4.2。修复后完整应用单测为 100/100。没有第二次独立复审，亦无延后处理的 Minor。

- Ruling: 将审阅者标为 Minor 的回退文档冲突作为本轮契约一致性问题修正；否则后续实现可能依据错误规则跳离深列表。代价：这里只修正文档，真实恢复与 UI 仍须在 T08 验证。
- Ruling: 序列化和存储格式继续按已记录决定暂缓；当前没有编码器或格式消费者。代价：不提供持久化或兼容性保证。
- Ruling: 真实查询、分类、身份关联和资料去重仍留在 T05–T07；本轮只校验结果总量不能自相矛盾。代价：不提供真实筛选或关联正确性的运行证据。
- Ruling: 数据库事务、跨会话排序、取消竞态和实际持久性仅记录接口责任，不引入假实现。代价：这些运行行为未验收。
- Ruling: 不新增任意泛型负载的通用深层冻结机制；FieldEvidence 的现有资料字段只使用不可变 Int / FactIds，其他调用方仍必须遵守不可变 T 约束。代价：未来新增可变负载时必须重新设计或验证。
- Ruling: 保留负 Unix 时间戳、超过陈旧时长的位置、已知空语言集合的表示能力，不在值对象中臆测为非法。代价：未来显示与业务策略须正确处理，不能拿时间戳排序授权或把空语言直接归类。
- Ruling: UI、遥控器、播放、设备、迁移、签名和发布继续保持未验收，符合明确授权边界。代价：不能把本轮本地测试解释为产品可发布。
- Ruling: 审阅时报告中的“待审阅”是过程状态，最终记录在修复和回归后更新。代价：这些记录的更新不构成新的独立复审。

最终代码提交为 `41bb6885f5945159d825b53eef2cecb59d623865`，之前两笔为计划导入 `f0afef7`、契约实现 `6a56515`。结项记录另行本地提交，最终 HEAD 在用户答复中记录。原始 main 保持基线；没有执行 T03 或后续任务。

## G01–G12

| 目标 | 状态与本轮边界 |
| --- | --- |
| G01 中文、无品牌 | 未实现 |
| G02 删除诊断 | 未实现，旧诊断保持原样 |
| G03 全新视觉、原创图标、状态 | 未实现 |
| G04 遥控器、焦点、动效 | 未实现 |
| G05 页面、筛选、焦点、进度持久化 | 快照契约与候选存储／编码已实现；R04 的 34 项候选存储原生检查在专用 ARM64 AVD 通过，未接运行时或验证用户体验 |
| G06 续播、历史、收藏 | 接口与快照已定义，未实现功能 |
| G07 多源统一数据、稳定身份 | ID 与字段依据类型已实现和单测，无身份关联算法 |
| G08 严格分类、国家、类型、年份 | 字段与年份边界校验已实现和单测，无查询引擎 |
| G09 一键订阅维护 | 状态契约已实现和单测，默认目录未接通 |
| G10 性能、低配置、升级保数据 | 未验证 |
| G11 筛选组合、备份、睡眠等增强 | 未实现 |
| G12 字幕音轨、追更、外部资料增强 | 仅区分版本音轨元数据，增强功能未实现 |

## T01–T16

| 任务 | 状态 |
| --- | --- |
| T01 基线与隔离 | 本地隔离、原有测试/构建/Lint 已核实；旧样本、设备矩阵等未完成 |
| T02 规格冻结 | 数据契约及本轮持久化编码切片已通过纯测试；视觉／图标／交互等仍未完成，M01 未完成 |
| T03 数据库与迁移 | Round 02 候选与 synthetic staging 保持；R04 在专用 ARM64 AVD 实际执行 34/34 通过；真实旧数据、runtime cutover 及整个 T03／M02 未完成 |
| T04 播放检查点 | Round 05 候选策略／真实持久仲裁与合成 schema 迁移已测试；149 JVM／61 native 全量通过；生产播放器未接入，整个 T04 未验收 |
| T05 身份与字段标准化 | 未开始 |
| T06 来源能力与订阅 | 未开始 |
| T07 严格多维查询 | 未开始 |
| T08 导航快照与焦点恢复 | 未开始 |
| T09 续播、收藏、历史与播放器 | 未开始 |
| T10 新界面 | 未开始 |
| T11 遥控器与动效 | 未开始 |
| T12 设置、中文化、删除诊断 | 未开始 |
| T13 增强能力 | 未开始 |
| T14 性能与耐久 | 未开始 |
| T15 回归与升级 | 未开始 |
| T16 发布安全 | 未开始 |

## A01–A24

所有 A 场景均未验收。值对象测试只可能提供局部证据，不证明未来存储、查询或设备行为。

| 场景 | 状态与本轮可能关联的证据 |
| --- | --- |
| A01 中文界面 | 未验收 |
| A02 原创图标 | 未验收 |
| A03 基础遥控路径 | 未验收 |
| A04 详情返回深位置 | 未验收；仅快照字段 |
| A05 重启、回收、强停恢复 | 未验收 |
| A06 不同集独立进度 | 未验收；仅作品/剧集 ID |
| A07 快退保存 | 未验收；不按位置大小判断新旧 |
| A08 零位置、旧回调 | 未验收；仅零位置及非负序号约束 |
| A09 最新集与预加载 | 未验收 |
| A10 收藏持久化、失败 | 未验收；仅提交结果及幂等接口 |
| A11 删除记录与清缓存 | 未验收 |
| A12 老年份与区间 | 未验收；仅包含边界及非法范围拒绝 |
| A13 语言、国家、音轨 | 未验收；仅独立证据模型 |
| A14 空页与后续页 | 未验收；仅 partial/complete 值区分 |
| A15 来源失败、重复、忽略参数 | 未验收；仅失败来源字段 |
| A16 身份关联与冲突 | 未验收；仅不透明 ID 和冲突值 |
| A17 目录空与刷新失败 | 未验收；仅订阅状态区分 |
| A18 来源换域、停用 | 未验收 |
| A19 刷新、面板与焦点 | 未验收 |
| A20 迁移中断与重复执行 | 未验收；有候选与合成／故障原生测试编译证据，原生执行仍阻塞 |
| A21 播放、后台与低资源 | 未验收 |
| A22 长字、缺图等界面状态 | 未验收 |
| A23 生产包安全与清理 | 未验收；仅当前 Git index 安全检查 |
| A24 性能与耐久 | 未验收 |
