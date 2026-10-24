# Round 08 候选作品身份与字段溯源验证

本轮仅完成T05离线候选标准化、证据、来源条目与保守关联切片；不代表T05、T03/T04、M02或A13/A16/A18整体验收。用户明确授权按附件实施、仅本地提交、不推送，原生测试只在确认自有的专用模拟器执行。

## 起点、材料与交付

分支 `codex/contract-foundation`，起点 `4097694bef7107ca1c6953d55bf9c78c3914b8ac`。主树main与本轮刷新后的origin/main均为 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`，未修改主树。起始两工作区均干净；现有linked worktree复用，未另建或重置。分支无upstream是已明确的local-only用途，不建立远端分支。

ZIP SHA-256：`f5e2a19df8028a49a33976d46e93a33022da780bd6f38e3780423e98e2d61bd5`。CRC无错误，清单四项全部匹配，三个独立附件与包内原件逐字节一致。原计划SHA-256为 `16a8dc1e89c1ed550c30c8e48e46b74ef5d50885a521793db5e70b08f3354471`。

原件含个人绝对路径，直接应用现有scan_path得到personal-path；原件保存在忽略的 `.local/evidence/round-08/attachment/`，仓库同名计划仅把工作树绝对路径替为占位符。不放宽索引安全规则。具体接口及裁定见 [架构说明](../architecture/tv-catalog-identity.md) 和 [进度账本](../superpowers/progress/2026-10-23-tv-rebuild.md)。

实现提交 `f3da504dfece3ee2600d04b197b10d24da9284ca`，21个明确路径；本报告和账本另作本地文档提交。最终文档提交HEAD由Git日志及忽略证据 `final-delivery.json` 记录，避免在提交内容中自引用其尚未生成的commit hash。没有push、merge、rebase、tag、发布、GitHub可见性或生产签名操作。

## 实际实现与保留边界

复用既有WorkId、SourceId、SourceItemId、EpisodeId、VariantId和FieldEvidence，不引入平行公共身份。原始JSON与字段名、观察时间、原值、规则版本持久保留；来源元数据始终只是SourceReported。精确词表保留多国家、多类型，惊悚不改为恐怖；作品语言、音轨和字幕独立。缺失、null、已知空集合与未知可区分；年份矛盾和纠正追加历史，不以最新值或多数票覆盖。

旧RemoteVod解析会trim、合并缺失/空值并为缺ID生成标题哈希，因此候选入口接收原始合成JSON、再复用RemoteVod与播放DTO；没有修改旧解析、Movie键或运行仓库。缺ID输入保存pending capture，不伪造条目或回填legacy staging。`work_original_language`、`subtitle_language`只作合成夹具的显式字段，不宣称真实CMS普遍提供这些语义。

每个来源条目保留不可重新分配的homeWorkId；可审计groupWorkId只表示候选关系。标题与年份最多Candidate，来源原始外部ID不是证明。显式本地证明绑定完整左右source-item键及命名空间和值，核对两组全部有效标识，传递冲突保守拒绝。端点注册与别名分别受控，别名证明还必须通过旧端点所有权及新端点无冲突检查。

拆分解散整组并恢复每个成员原home，撤销该组证明，保留来源、入口、观察及事件；旧proof不能重用，正确关联恢复也需新证明。收藏、browse payload、逐集检查点、session/resume和未解析旧staging不删除、不猜测重分配。来源不可用只改候选状态。

Room v3原11张表定义在v4完整保留，新增8张候选表及真实主键、唯一索引、外键RESTRICT。3→4只增加表/索引；工厂显式注册完整1→2→3→4路径。schema1/2/3原字节保持，schema4由KSP实际导出，Debug/Release生成CinemaDatabase_Impl逐字节一致。候选DAO实际生成BLOB绑定，沿用无损UTF-16 ID转换。

迁移API按既有依赖及 [Android官方Room迁移文档](https://developer.android.com/training/data-storage/room/migrating-db-versions) 核对；未升级或安装依赖。没有destructive fallback、假内存数据库或运行时自动开库。

## RED→GREEN与主机验证

证据在忽略目录 `.local/evidence/round-08/`，保留原始失败、修正、退出码和最终逐方法结果。

- 基线JVM使用 `--rerun-tasks`，28个任务实际执行，退出0。首次UP-TO-DATE请求单独记录，不当作重新执行。
- 标准化最初API骨架运行失败单独记录；行为未实现时8项中7项失败，原始字段、精确映射及冲突补足后通过。
- 身份判定实际断言先有5项失败；标题年份、验证命名空间及跨组冲突实现后通过。
- 投影实际3项失败后实现DTO复用、明确身份和坐标验证。
- 新codec的损坏负年份与原字段名保留先实际断言失败，再校验及编码补足通过。
- 新原生文件数据库方法最初20项全部实际失败；完整实现和夹具纠正后最终23/23通过。

主要完整命令（SDK环境由本地run.py注入，沿用已有mise Java）为：

```sh
mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
  :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease \
  --rerun-tasks --no-daemon --no-configuration-cache --console=plain
mise exec -- python3 -m unittest discover -s scripts/tests -v
```

- 最终140/140个Gradle任务实际执行，完整JVM221/221（既有199＋新增22），0 failure/error/skip。
- Python62/62，退出0；没有0测试discover冒充通过。
- Debug Lint：0 Error、21 Warning、1 Hint；Release：0 Error、22 Warning、1 Hint。id/severity/message多重集合与Round07相同，无新增/删除，不压制规则。
- 普通Debug、未签名Release、AndroidTest实际编译；既有Gradle10兼容弃用提示保留。
- 六类Probe ON非法任务请求（Release、单Debug、bundle/publish、install、signing、注入Release任务图）均在任务执行前拒绝。没有运行被拒绝的安装或签名任务。

夹具/运行器纠正单独记录，不计为产品RED：投影夹具Kotlin美元符号转义、StorageReadException字段名与现有DAO方法名编译纠正；schema3部分entity没有indices字段，改用可选读取；旧core第一次class字符串包名写错，初始化失败后按真实源码inventory重跑69/69。原始错误日志保留，不把其43项重复通过累加。

产物比较脚本首次把历史aapt文本SHA当作二进制manifest SHA，已根据原始记录证明其实际为文本摘要；修正为同类型比较，另记录当前binary hash，不声称旧binary字节相同。这是验证脚本纠正，没有修改构建产物。

## 原生目标、执行方法与进程证据

仅使用现有API30 default arm64-v8a镜像，新建两个本轮专用AVD。每个mutating ADB命令均固定 `-H 127.0.0.1 -P 5037 -s <serial>`，事前校验owner记录、AVD名、宿主PID/参数、boot ID、qemu=1、SDK30、ARM64且abilist32为空、boot_completed、第三方包及runner。只有Debug/test两测试包；安装前先核对APK ABI、runner和target。未扫描网络、未使用个人设备或TV，未重启共享ADB。

| 目标 | 固定serial / 宿主PID | boot ID | 最终状态 |
| --- | --- | --- | --- |
| cinema-catalog-20261024-4cce81b5 | emulator-5568 / 18351 | 386574e2-7b3b-49c1-b00c-00347c7c1e13 | 已关闭，数据保留，宿主PID已消失 |
| cinema-handoff-20261024-9e65e108 | emulator-5570 / 27214 | b5dbe23b-df31-46b4-a8bd-aabf3eb0821e | 已关闭，数据保留，宿主PID已消失 |

原生只使用opt-in配对构建：`:app:assembleDebug :app:assembleDebugAndroidTest -Pcinema.storageTestArm64=true`，播放回归另加 `-Pcinema.playbackRoomProbe=true`。普通产物另以两属性OFF全量重建，不混用测试ABI。

| 实际执行类 | 唯一方法 / 通过 |
| --- | --- |
| WorkIdentityStoreInstrumentedTest | 23 / 23 |
| CinemaDatabaseTest | 15 / 15 |
| LegacyUserDataImportTest | 19 / 19 |
| PlaybackCheckpointNativeTest | 26 / 26 |
| PlaybackSchemaMigrationTest | 2 / 2 |
| PlaybackRuntimeNativeTest | 7 / 7 |
| LegacyPlaybackRuntimeNativeTest | 1 / 1，Probe OFF |
| LegacyHandoffGateNativeTest | 4 / 4 |
| LegacyHandoffNativeTest | 17 / 17 |
| LegacyWriterCancellationNativeTest | 1 / 1 |
| LegacyHandoffMediaNativeTest | 4 / 4 |
| LegacyHandoffProcessTest | 5 / 5，逐进程并含Probe OFF |
| PlaybackProcessReplayTest | 2 / 2，逐进程 |
| 合计 | 126 / 126，0失败/跳过/未运行/重复 |

最终日志的start、terminal code0与源码inventory逐方法Counter完全一致，不把多次RED/GREEN或重复执行累加。新23项含真实文件close/reopen、schema3的全部11旧表种子逐字段保留、关联/别名、损坏/缺ID、真实事务内取消、身份碰撞回滚、配置FK/唯一约束与gate-OFF。

旧进程回归：PID3190提交5551ms，force-stop后PID3296恢复完整相同快照且暂停。交接进程：PID2630持久staged后确认异常，PID2727恢复仍为Legacy；PID2797旧2000ms暂停恢复并提交7527ms，force-stop后PID2904恢复完整相同Room快照并暂停。随后安装配对Probe OFF APK，实际方法拒绝回退旧writer、旧文件哈希保持。媒体只是既有测试生成的12秒私有PCM WAV，不是TV视频或Activity/导航自动恢复验收。

## 合成验收矩阵对应

| 矩阵 | 实际新原生方法前缀 / 边界 |
| --- | --- |
| ID-01、ID-02 | id01来源隔离重开；id02标题年份仅Candidate |
| ID-03、ID-04 | id03证明关联和入口保留/幂等；id04原始外部声明不授信 |
| ID-05 | id05两组其他命名空间的传递冲突保留原组及冲突事件 |
| ID-06 | id06明确alias及重开；registration_cannot_bypass_alias另测注册绕过和端点占用 |
| ID-07 | id07同token幂等、不同内容拒绝且不覆盖 |
| ID-08 | id08新观察年纠正与冲突历史；JVM跨来源证据同样保留 |
| ID-09 | id09缺失/null/已知空集合真实重开区分 |
| ID-10、ID-11、ID-12 | id10语言/音轨/字幕；id11合拍及欧美不猜测；id12精确别名与惊悚独立 |
| ID-13 | id13二元错误关联拆分、个人旧行完全相同、证明撤销及禁止复用 |
| ID-14 | id14来源不可用保留全部个人与目录行 |
| ID-15 | id15原schema3所有旧表种子→4逐字段一致；既有v1/v2迁移同样到4通过；真实关闭重开 |
| ID-16 | id16列表详情追加原文、稳定entry与多次路由历史 |
| ID-17 | id17普通门禁OFF；既有真实Legacy OFF及已激活profile OFF断言 |
| ID-18 | id18缺旧来源staging不回填、缺新ID保存pending |

这里只核验表中合成子场景，不把A13/A16/A18或T05整项标记完成。

## 产物、schema与普通运行保持

25个关键运行/发布路径相对起点逐字节一致：Gradle配置、manifest、旧CMS适配、Movie键、已有settings/DataStore及播放器等；具体清单在 `runtime-release-neutral.json`。候选只显式开库，没有Koin、Application、导航、播放器或旧writer选择改动。普通Debug/Release的BuildConfig.PLAYBACK_ROOM_PROBE均实际为false。

| 最终普通产物 | 身份 / ABI | SHA-256 |
| --- | --- | --- |
| Debug | com.ichichuang.cinema.debug / armeabi-v7a | cfd460417c0a7bdcf52aec6c715b2a14e5d4f9502b93171bc634ad389bbc47cc |
| unsigned Release | com.ichichuang.cinema / armeabi-v7a | fe5eb4abd6d77a3b6b687a34f6c3dedcd6a039e86b448d679492fc473f29d572 |
| AndroidTest | com.ichichuang.cinema.debug.test / 无JNI | 850b502d38562657422543226635a96675d971d8eb1bc1b26bee7a701519edc3 |

versionName 0.9.8（Debug后缀-debug）、code18、minSdk24、targetSdk28、正常ARMv7和签名冻结保持。Debug/test的apksigner verify退出0；unsigned Release退出1并报告Missing META-INF/MANIFEST.MF，符合未签名边界。三个角色的aapt manifest文本输出及两份JNI的SHA/ELF与Round07完全一致；完整APK因新增代码/schema改变，未声称旧版原字节或可分发生产更新。

| schema | SHA-256 |
| --- | --- |
| 1.json，原字节 | 14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959 |
| 2.json，原字节 | 6b5abe547c00a7a2d2cf2ab1e30e995347b3d9cd014c9700a639e5f87677aca9 |
| 3.json，原字节 | bda6901970ecceb7c0c1adbe8aec0f0a665243cce9e61e95fcade2b0c3c98b95 |
| 4.json | 140609a645dba466cfd0f1d57059198e4d9527692f3c6402878be87704f808b6 |

## 独立审查、安全与剩余限制

Round07三项Important得到有限fresh-context独立源码/测试设计复审，未发现受控profile范围内剩余Important/Critical；该复审不验证历史103方法日志。

本轮另一次fresh-context独立只读审查覆盖全部新文件、现有差异、旧schema、scope与测试：0 Critical、0 Important、2 Minor。未运行构建或ADB，实际运行验证由执行者完成。19项应用/测试/schema的审查指纹与最终测试及实现commit blob逐项一致；没有审查后源码修正或第二次复审。

两项Minor按技能流程明确保留：新增cache_eviction用例没有seed缓存行，不能单凭它证明实际非空缓存删除的组合场景（既有测试另覆盖缓存删除保留个人记录）；split用例只有二成员一个proof，三成员传递拆分的独立原生断言未补充。代码全组撤销已审阅，不将上述覆盖限制说成实现缺陷或全覆盖。

实现提交前检查branch/status/upstream/ahead-behind并fetch，无远端漂移；只暂存21条明确路径，逐项验证index/worktree/blob身份。index安全检查278文件、0所检查规则违规；本报告和账本作为两个明确路径另暂存并检查完整index。检查只覆盖当前索引的选定凭据/二进制/个人路径等规则，不是历史、全面安全或许可证审计。

本轮没有阻碍已授权候选切片本地交付的未决问题。生产核验authority/命名空间登记、真实CMS语义及实时来源、查询协调和严格筛选、历史容量/回收策略、真实用户迁移/身份再分配、UI/Activity导航恢复、ARMv7 TV与真实断电均未验收。无真实用户记录、媒体目录或个人设备访问，无默认来源、订阅激活、生产切换或签名发布。

停止于Round08。
