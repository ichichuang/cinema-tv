# Round 06 隔离 Debug 播放运行时验证

本轮仅完成测试授权的 Debug T04 运行时切片。普通 Debug／unsigned Release 继续原 Legacy DataStore 路径；不构成 T03／T04 整体、M02、A06–A09 或生产验收。

## 起点、附件与范围

沿用现有隔离工作树和 `codex/contract-foundation`。编辑前核对实际 HEAD `d75cb40622a5adc8d9680a198063d0a0fc01cec4`、分支、两处 checkout 的干净状态及既有源码，与 Round 05 报告一致。主机先新跑基线 JVM 149/149；旧 61 个原生方法在本轮最终 AVD 上重新逐项执行。没有从远端重建本地未推送源码。

ZIP SHA-256 `75e65545e6500c303b7032f8de17a1aaa4e2938587039a19efa4dd1eb0e4f456`；包内 README、instruction、plan 三项全部匹配 SHA256SUMS，独立附件与包内字节一致。指令 SHA-256 `a07440dc08a447b1f113379dbcb5b928e39dffcafbefc60b3c78a16e2dd0740d`；原计划 SHA-256 `cfe9aa6efd09fdd60f17a6eca44b103f588765d082eab9c1f6dda44e48f5c471`，原字节已提交。

实现提交及本报告记录时 HEAD：`23dfac429d5fc1cf8a547f8cb69304490593b617`。所有下述代码／测试／构建证据绑定该提交，提交前核对工作区与 index 的内容指纹一致。原 main 和交付前刷新后的 origin/main 均保持 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`。分支仍无 upstream，按已授权的本地候选用途保留；没有合并、推送、发布或移动标签。

## 实际实现与门禁

- `cinema.playbackRoomProbe` 默认 false，true 仅接受精确的完整 Debug／DebugAndroidTest assemble 配对；真实任务图另拒绝 Release／bundle／publish／生产签名。Release 常量始终 false，签名冻结保持。`cinema.storageTestArm64` 仍独立、默认关闭。
- Runtime 再要求私有测试授权标记和无旧 progress／browse／filter 文件的 profile；任何旧文件（包括空／临时）均拒绝，而非读空库、迁移或回退另一 writer。OFF 直接选 Legacy，不探测标记或打开 Room。
- 当前 MovieDetail 不含可信稳定 episode／variant ID，因此仅测试仓库显式提供既有 PlaybackTarget 与 app-private file URI；不从名字、URL、索引生成 domain ID。缺失、重复、来源／列表变化或时间轴不匹配时拒绝，保留原记录。
- 真实 Media3 listener、心跳、seek、暂停、retry、切集和生命周期操作通过同一 bridge 调用原 Round 05 coordinator／Room store。接收时刻在 player looper 捕获，使用单调实际 playing 时间，不按位置大小、session 字典序或墙上时间排序。缓冲／idle／失败零值不覆盖有效位置，明确归零／快退可以保存，未知 duration 保持 absent。
- 切集等待旧记录真实提交；Failed 不换集、不假报已保存。后台边界同步撤销挂起切集／retry 的自动播放意图。单个 await 请求具有隶属 owner 的可取消 Job，取消已进入的挂起工作后不会设置旧媒体，队列仍能服务新请求。销毁取消 worker 并关闭 Room，没有游离补写。
- 默认 Legacy 调用与 Koin 参数保持；Probe 不自动跨版本换线或跨集连播，不宣称修复生产旧问题。详细接口见 [运行时结构](../architecture/tv-playback-runtime-probe.md)。

## RED、实际失败与独立审阅

| 证据 | 实际结果和处理 |
| --- | --- |
| 构建门禁 RED | 实现前 Probe flag＋Release dry-run 退出0，证明原构建忽略该 flag；实现后六类非法请求均非零、执行任务前拒绝 |
| 首轮 JVM bridge | 最小空实现的18项中15项失败；实现后18/18；其余初期已通过的保护不宣称单独经历RED |
| 队列／身份／路径 | 初期队列2/2失败、身份4项中1失败；新增路径边界3项中1失败；之后分别转绿。最初空队列的超时及补测试时的等待问题不计为实际播放器故障 |
| 旧 ViewModel retry | 专用 AVD 上真实 ExoPlayer 先到有效非零位置，再调用旧 retry 实际归零，断言失败；Probe 路径修正后通过，普通 Legacy 未修复 |
| 真 IO 失败后的 retry | 移开合成媒体文件触发真实 ERROR_CODE_IO_FILE_NOT_FOUND；恢复文件后播放已恢复而错误文案未清除，实际RED；仅 Probe retry 清除错误后转绿 |
| 一次独立只读审阅 | 15项候选指纹全部匹配；0 Critical、2 Important、0 Minor。发现“慢写切集／retry覆盖后台暂停”和“单次取消只取消回执，旧块继续设置媒体” |
| 审阅修正 RED→GREEN | 用真实 SQLite 写锁＋真实 Media3 复现后台自动播放错误；JVM用已进入的挂起请求复现取消后的旧副作用。分别实际失败后以 pause revision／请求 Job 作最小修正，最终全部回归通过 |
| 两处 fixture 同步失败 | 首次 native 66/67，以及首次进程B失败，均因预期取自较早 boundary 回执，后面还有合法同位置 pause 提交（序号／时间不同）。冻结写入后取回滚基线；Phase A 等真实 pause onEvents 后再 await boundary。完整逐字段断言保留，未改成只比位置 |
| 首次完整 Lint | Debug 3个 NewApi Error：File.toPath／Path.startsWith 需API26。改用 canonical File 与带分隔符的边界校验，新增邻目录、遍历、symlink、缺文件及URI拒绝用例；未改minSdk或压制规则 |

审阅后的修正及 API 兼容修正由作者用实际 RED→GREEN 和完整回归确认，**未进行第二次独立复审**。审阅中未列为缺陷的默认 OFF、严格旧文件拒绝、显式身份、未知时长、旧会话仲裁和五秒政策均按本轮边界保留；真机／迁移／Legacy旧问题不扩展为本轮修复。

## 最终主机与普通产物回归

沿用 mise Temurin 21.0.11+10.0.LTS、Java target17、Python3.13.3、Gradle9.8.1、AGP9.4.1、Media3 1.11.1、Room2.8.5／KSP2.3.12 及既有SDK，未安装或升级依赖／工具链。

| 层次 | 实际最终结果 |
| --- | --- |
| JVM | 17 suites，177/177；failure/error/skipped均0；含原149项及新增28项 |
| Python | 62/62，退出0；修正后重跑 |
| 普通两属性 OFF 全量 | testDebugUnitTest、assembleDebug、assembleRelease、assembleDebugAndroidTest、lintDebug、lintRelease 成功；140个任务全部实际执行，退出0 |
| Debug Lint | 0 Error、21 Warning、1 Hint |
| Release Lint | 0 Error、22 Warning、1 Hint |
| 告警比较 | id／severity／message 多重集与Round05完全相同，无新增或压制 |
| Gate负向 | Release、单独Debug、bundle/publish、install、签名root、允许root注入Release依赖，共6项均在执行前拒绝；不执行签名／发布 |
| 默认包身份与兼容 | .debug及正式ID、18／0.9.8、min24／target28、普通armeabi-v7a均实际检查；三份binary manifest与原JNI字节均与Round05相同 |
| 签名 | Debug／test开发签名verify退出0；Release未签名verify退出1，冻结保持 |
| 最终输出恢复 | ARM64验证后再次无属性构建普通Debug／test；三份最终普通APK摘要与完整回归产物完全一致 |
| 暂存安全 | 实现提交前检查236个index文件，0违规；完整文档暂存后的检查见交付记录。非历史审计或完整漏洞扫描 |

完整普通回归命令，不带任一 opt-in 属性；SDK 仍用既有本机设置：

```sh
mise exec -- python3 -m unittest discover -s scripts/tests -v
mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
  :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease \
  --rerun-tasks --no-daemon --no-configuration-cache --console=plain
```

| 实际产物 | 身份／ABI | SHA-256 |
| --- | --- | --- |
| 普通 Debug | .debug／armeabi-v7a | `b4bfa86bcae1d1494d8523c91cd6f47c24da9e3c4705790882d9f4b5c1e8a4f2` |
| unsigned Release | com.ichichuang.cinema／armeabi-v7a | `011529d6ce47e11bde2e54c71c77bb69805c270ba1d581eaed18b49991394ebd` |
| 普通 test | .debug.test／无JNI | `fcb99215139a46111a196c702947abd54fa6eb359696612c0a3427b55e624fcc` |
| 实际受测 Probe Debug | .debug／arm64-v8a，Probe ON | `f2651c6d649638f2ae761000bf3334be42df2ddc1cf8c9798dcc030017fcca99` |
| Probe test | .debug.test／无JNI | `9da52bf1d5ad76f3376b1ff5b43cae4bbb6e8f823a056465201209adfcc415d9` |

## 最终原生目标与逐方法执行

最终新建并独占的 AVD 为 `cinema-playback-20261024-afb36413`，serial `emulator-5564`；API30 default ARM64 镜像、pixel_2 profile，ro.kernel.qemu=1、ABI=arm64-v8a、abilist32为空、boot_completed=1，boot ID `4b1dd389-7015-4926-924c-8e59d0dda044`。不是 Android TV 或 ARMv7 目标。使用现有镜像，未下载额外镜像。

最终目标的每次安装／执行前核对精确serial、AVD名、host PID／命令、boot ID、API／ABI／qemu和第三方包清单；最终轮两份APK的runner／target在首次安装前即统一验证，后续执行再核对安装后的runner。初始可丢弃profile无第三方包，之后只有测试Debug及test包。最终目标的ADB均固定 `-H 127.0.0.1 -P 5037 -s emulator-5564`；无 unqualified install/shell、pm clear、实体设备或真实用户数据操作。初期的独占测试AVD已分别关闭；最终AVD也已通过同一身份门禁关闭，host PID消失，保留共享ADB server。

| 实际执行类 | 方法数／通过 |
| --- | --- |
| CinemaDatabaseTest | 15/15 |
| LegacyUserDataImportTest | 19/19 |
| PlaybackCheckpointNativeTest | 26/26 |
| PlaybackSchemaMigrationTest | 1/1 |
| PlaybackRuntimeNativeTest | 7/7 |
| PlaybackProcessReplayTest | 2/2，分别在独立进程运行 |
| LegacyPlaybackRuntimeNativeTest | 1/1，Probe OFF的ARM64配对APK |
| 最终唯一方法合计 | 71/71，0 failed／skipped／not-run／重复或未知方法 |

68项主套件含原61项，两个进程方法另分开执行，最后一个Legacy方法在Probe OFF APK执行；不将这些说成一次同进程套件。源方法inventory与每个start／terminal code0逐项一致。真实file-backed Room、新实例关闭重开、事务回滚、序号冲突、迟到callback、旧作品指针、seek2秒／归零、真实文件IO重试及慢SQLite锁下后台暂停均有实际断言。生成的12秒、8kHz／单声道／16bit PCM WAV约192044字节，仅存测试私有目录，不提交媒体资产或调用线上片源。

最终 Phase A PID2677，由实际播放提交 `5554ms`、duration `12000ms`、sequence `2`、episode `fixture-episode-a`。在两个instrumentation调用之间，仅对测试Debug包执行serial固定的 am force-stop。Phase B PID2783恢复完整相同快照；实际Player位置严格相等，playWhenReady=false、isPlaying=false，300ms后快照仍相同。验证的是最后成功提交，而非硬杀前任意未保存瞬间。新进程通过测试仓库重新创建真实PlayerViewModel，不代表Activity／导航的系统自动恢复已验收。

## schema、数据与剩余限制

原 coordinator、Room store、PlaybackProgress、Koin、Application、PlayerScreen、生产manifest、发布元数据和资源均保持；未双写、未迁移或删除旧用户记录，测试仅清理自己创建的合成文件。Room v2不变，原1.json SHA-256 `14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959`；2.json `6b5abe547c00a7a2d2cf2ab1e30e995347b3d9cd014c9700a639e5f87677aca9`。既有1→2迁移原生方法重新通过，没有新增schema或destructive fallback。

现有Legacy旧问题、真实旧数据关联迁移、版本时间轴映射、ARMv7／API24实机运行、TV视频解码／遥控器／视觉、Activity导航恢复、任意断电及真实磁盘写满、生产升级／回滚均未验收。WAV证明Media3事件与Room连接，不证明电视视频播放；五秒是正常检查点目标，持久保证只到最后成功提交。不得据此标记整个T03／T04、M02、A06–A09或可发布APK完成。

原始命令、方法清单、identity gates、APK副本、签名／Lint／schema／source指纹及失败输出保存在忽略目录 `.local/evidence/round-06/`，不提交二进制或个人路径。本轮到此停止，不进入T05／T09／UI／图标／订阅。

## 文档交付记录

本报告与结构／账本在实现提交之后单独本地提交；最终文档提交可通过当前Git日志确认。最终完整暂存索引检查238个文件，0违规，退出0；重新暂存定稿文字后再检查同一完整index。
