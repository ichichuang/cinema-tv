# Round 12 个人记录候选原生切片验证报告

## 结论与边界

本轮限定的测试授权原生切片满足下列 52 个适用案例；53 项矩阵中仅 MIG-01 为 N/A（实际执行 v5→v6）。最终唯一原生方法 247/247，通过 247，失败／跳过／未执行／重复计数均 0。JVM 351/351，Python 62/62。完整 T09、M04、A06/A08–A11/A18 及实体电视验收仍未关闭。

未推送、合并、rebase、发布、签名、访问物理设备或真实来源；没有生产迁移激活，没有进入 T10/T11。普通 Debug／Release 保持原 Legacy 导航、唯一 DataStore writer、包身份及签名冻结。

## 附件、起点与源代码

- 起始隔离分支：`codex/contract-foundation`，HEAD `e9f6cc1875a8a2eb93060feb115cf3b74b9a9204`；原主工作树和隔离工作树在导入前均干净。main/origin/main 基线 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`。
- 分支原本没有 upstream，本轮保留；仅做本地提交，不创建远端关联。最终本地提交 ID 以本轮最终答复及忽略证据 `final-delivery.json` 为准。
- ZIP SHA-256：`68d0d8c3f392333c71938c97d6b5b5b3fe1f9d3562b04ec82e0ea442585eadf7`。四条 manifest 均真实校验通过，另附三份独立文件也与包内原件字节相同。
- 原件与 manifest 保存在忽略的 `.local/evidence/round-12/attachment/`。仅导入 scoped plan；提交副本按既有 R11 约定最小替换个人绝对路径及会触发既有 personal-path 规则的 home-feature 路径表述，原件不改、安全规则不改。
- 提交计划副本 SHA-256：`a628c0b5bd579c4afc01e05aa7d255f89f084b7facb5a7116efa3f61275b27ea`。
- 受测源文件共 252 项，逐文件 SHA-256 在 `final-tested-source-hashes.json`；该清单文件 SHA-256 `794f9806478ffcacf2065030e5f01c53ba7b7d96d3e98d77407a81ec2849efec`。最终 stage/blob 与受测工作文件另行核对。

| 包内原件 | SHA-256 |
| --- | --- |
| `README.md` | `19a008c9a6f15a5e5aed464b20f75e83f2aa759135c3215cdc6be755e8c73e34` |
| `CODEX_INSTRUCTION.txt` | `022e6fde25cf5e59b5932f5593326bdb1e7075a45d633126257195838de1c447` |
| `PERSONAL-LIBRARY-ACCEPTANCE-MATRIX.md` | `cae47ad910f9f81d2a8fc61a14ef4a779a1864a7882323d1db14392bd208a3c2` |
| `docs/superpowers/plans/2026-10-25-round-12-personal-library-native-slice.md` | `0a4cd117f2d8f24b545db9fe9be62edfb5e65b108c57c448c1c5f75b0ac6ccb0` |

## 实现与数据保留

- 复用既有 RoomFavoritesStore、R05 会话／逐集仲裁、R06 Media3 桥、R07 writer lease、R08 opaque 身份和 R11 原生 NavHost／焦点恢复。三个 Room 投影与三个持久动作各自独立。
- 实际 `READY && isPlaying` 才提交观看凭据；历史使用持久事件顺序，短播放保留准确集／版本而不晋升续播。5000ms 政策和短片 ended 既有例外保留；已完成一集不推测全剧终结。
- 隐藏仅影响继续观看；删除历史在确认的 generation 范围内删该作品历史／对应检查点和 resume；取消收藏仅改关系。固定请求范围及持久删除水位抵抗迟到回调、旧 staging／激活重试、回执丢失后的旧请求重试。
- 取消收藏准备令牌和持久修订绑定实际撤销；重试确认原提交，较新意图拒绝旧撤销。UI SQL 故障、真实提交撤销、10 秒窗口及可见焦点均有原生证据。
- 采用显式 schema 5→6，新增 3 表、总计 25 表；旧 22 表实体定义及 schema 1–5 字节保留。真实 v5 文件库有效多集／版本、三个 browse context、来源依据、暂存／handoff／查询／订阅种子保留，测试了失败回滚、FK、Room identity 和重开。v5 合格续播保留，但不伪造实际播放历史。
- 原 schema、manifest、旧 DataStoreBrowseStore／HomeViewModel 和 update 三文件共 11 项与起点逐字节一致。缓存淘汰、停用来源、三成员保守拆分不重分配或清除用户行。

| schema | SHA-256 |
| --- | --- |
| `1.json` | `14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959` |
| `2.json` | `6b5abe547c00a7a2d2cf2ab1e30e995347b3d9cd014c9700a639e5f87677aca9` |
| `3.json` | `bda6901970ecceb7c0c1adbe8aec0f0a665243cce9e61e95fcade2b0c3c98b95` |
| `4.json` | `140609a645dba466cfd0f1d57059198e4d9527692f3c6402878be87704f808b6` |
| `5.json` | `74d5c2d74324d9f58aeb54ff1c2678d23d3b01d89823e2e3a4f5a4b4d7d8ec2e` |
| `6.json` | `f93635543479a5ec5d775fc67ffb7ae4b459046936b16a85523cdedea3430fed` |

## 执行环境、命令与结果

- 唯一最终设备：`emulator-5586`，API30、纯 ARM64、ranchu，自有 AVD `cinema-personal-20261025-e1caae81`，host PID `35457`，boot ID `d9b9c9cb-d61e-46c4-a38d-da5f87fe747b`；独立 ADB 服务端口 5041。每次安装／执行前重新核对 host PID／AVD／boot／API／ABI／包集合／runner／同签名 app-test 配对。
- 未枚举或访问物理设备，未重启共享 ADB 5037。最初自有 5590 超出 ADB 推荐端口范围而超时，仅终止核实属于本轮的 host PID 33748，随后使用 5586。所有历史失败日志保留，不计最终通过。
- 本地 SDK 与既有 mise Temurin 21 运行 Gradle；Java target 17、AGP／KSP／Room／Compose／Media3 版本及依赖未升级。

| 层次 | 实际命令／证据（均在忽略的 `.local/evidence/round-12/`） | 结果 |
| --- | --- | --- |
| 本轮原生 | `native.py run scope-fixture-personal-native`，选择 PersonalLibraryNativeTest、PersonalLibraryMigrationNativeTest、PersonalLibraryUiNativeTest；`scope-fixture-personal-native/result.json` | 41/41 |
| 前轮原生回归 | `run-native-regression.py`；全部步骤 `regression-steps.json`；源方法对账 `final-native-summary.json` | 206/206（含新增 R07 防复活 1 项） |
| 原生总对账 | 实际 runner start／terminal 与当前源码 @Test 方法逐项、多重集比较 | 247 expected/executed/passed；0 failed/skipped/not-run/duplicates |
| 普通 OFF 全回归 | `run.py final-visual-ordinary mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew`；四门 false；`:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease --rerun-tasks --no-daemon --no-configuration-cache --console=plain` | 351 JVM；140/140 task 实际执行；Debug/unsigned Release/Test；双 lint 0 Error/Fatal |
| Python | `mise exec -- python3 -m unittest discover -s scripts/tests -v`；`final-python.log` | 62/62 |
| 门禁 | `gate-check.py green`，`green-gate-results.json` | 5/5 拒绝非法根任务／配对 |
| 真实离线 | `disable-owned-network.py`，`owned-offline-network.json`；`native.py execute final-offline-ui` | 自有 AVD wifi/mobile_data=0，Active default network=none，原生 case PASS；宿主网络未改 |
| 撤销可见复查 | `native.py execute final-undo-visual`，实际 bounds 断言＋截图 | PASS；重复执行不另增 unique 方法数 |
| 当前 index 安全 | `mise exec -- python3 scripts/check_repository_security.py`，`final-security.log` | Checked 350 tracked files; 0 policy violations. History was not scanned. |

Lint 按 ID、severity、message 多重集比较：Debug 基线／最终 22 项（21 warning＋1 hint），Release 23 项（22 warning＋1 hint）；新增与移除均 0。Kotlin／Gradle 既有弃用警告保留，没有 suppress、baseline 或测试规则放宽。

前轮206项执行之后，仅补强本轮HIS-05测试fixture，使A/B共享集与版本键并同时收藏；生产代码与前轮测试源码均未改变。本轮41项已再次完整通过，再与前轮206项重做唯一方法对账；两个源码快照及唯一差异见 `post-regression-source-delta.json`，未将旧fixture结果冒充新断言执行。

## 独立进程、强停与视觉证据

`run-external-lifecycle.py` 在当前安装的最终 app/test 配对上执行，`external-lifecycle-summary.json` 保存逐操作命令、前后 PID、实际 XML、browse/personal trace 和截图。ActivityScenario.recreate 与下面两个独立进程实验分别记录，不混称。

| 外部阶段 | 实际 app PID | 观察 |
| --- | --- | --- |
| 外部显式启动 | `12152` | History 原 context／work，实际唯一焦点“播放”，episode2／original variant／8000ms，isPlaying=false、playWhenReady=false；收藏98/99保留，已删98不在历史／继续观看 |
| HOME → am kill → 显式启动 | `12558` | History 原 context／work，实际唯一焦点“播放”，episode2／original variant／8000ms，isPlaying=false、playWhenReady=false；收藏98/99保留，已删98不在历史／继续观看 |
| am force-stop → 显式启动 | `12897` | History 原 context／work，实际唯一焦点“播放”，episode2／original variant／8000ms，isPlaying=false、playWhenReady=false；收藏98/99保留，已删98不在历史／继续观看 |

实际列表与详情／播放器均在原生 Compose 上观察。首轮发现末尾动作裁切、收藏卡遮蔽历史集身份和恢复 PLAYER 无有效焦点；在限定修复后复查滚动撤销 bounds、实际截图与按键。没有进行 T10 视觉重构，也没有把浏览器或静态测试当作电视表现。

## 独立审查、RED 与失败分类

- 新存储／门禁／UI 首轮真实 RED：原生 19/19 预期失败；桥 JVM 20 方法中新增 2 项失败。它们证明目标行为未实现，不计最终通过。
- 存储独立中审 S-I1（丢回执重试扩大删除范围）与 S-I2（短历史丢集／版本）均有真实 native RED→GREEN。另补取消收藏 receipt、v5 续播、同进程删除后重新播放及已收藏历史卡标签的行为 RED→GREEN。
- 整分支独立审查发现 R12-I1：Continue／恢复 PLAYER 的未知版本选择不可达；R12-I2：明确换版本误跳第1集。两项以真实入口／实际播放器 episode 断言复现，再修复到同一已有详情路径、保留来源上下文、同已知集从0暂停准备，原版本进度保留；限定复审关闭两项。
- 中途编译／测试夹具失败保留：SDK 环境配置、迁移测试括号／import、闭库后使用旧 repository、原生焦点帧等待、异步 tab 切换、直接写 DataStore 文件绕过 actor、恢复路由错误等待 browse 帧、异步初始化前 requireNotNull。这些均与产品 RED 分开记录；未删旧断言以制造通过。
- 最终源代码独立审查及证据复核没有未关闭的 Critical/Important。审查者只读源码、diff 和保存的真实执行产物，没有声称其本人重跑构建／ADB。
- Conditional Minor R12-M1 保留边界：未在实际 UI 运行时单独注入“已提交但回执丢失”来复现待重试意图与按钮文案的歧义；真实 UI 验证了 SQL 事务前失败、焦点和实际提交撤销，事务后回执丢失／幂等性由真实 Room 原生方法验证。未以此声称完整故障 UI 验收。

## 53 项矩阵逐项对账

每行列出实际全限定方法及其最终日志，均来自当前源码和真实 start/terminal。N=`emulator-5586` 上本轮自有 API30 ARM64 profile；X=同一目标的外部 PID 实验；H=主机实际脚本／产物检查。共 52 PASS，MIG-01 单独 N/A。一个方法可支持多个要求，不因此重复增加原生计数。

| ID | 结果／目标 | 实际方法与命令日志 | 实际观察／范围 |
| --- | --- | --- | --- |
| GATE-01 | PASS／N | `com.ichichuang.cinema.feature.browse.BrowseProbeOffNativeTest.ordinary_activity_uses_legacy_browse_and_never_opens_candidate` → `regression-storage-off/instrumentation.log` | 普通 OFF Activity 为旧导航、旧 HomeVM／DataStore；未打开候选库 |
| GATE-02 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.personal_probe_requires_owned_profile` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.feature.browse.BrowsePersistenceNativeTest.missing_or_corrupt_profile_and_consent_fail_before_database_creation` → `regression-browse/instrumentation.log` | 缺失个人授权／自有 profile 失败关闭，数据库未创建 |
| GATE-03 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.personal_probe_rejects_legacy_conflict` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.feature.browse.BrowsePersistenceNativeTest.candidate_lease_serializes_other_candidate_and_refuses_legacy_writer` → `regression-browse/instrumentation.log` | 真实 Preferences actor 写入旧记录后拒绝候选，字节不变；排他 writer |
| GATE-04 | PASS／H | `green-gate-results.json` | Release／bundle／publish／不完整配对／缺已有授权五种请求均在执行前拒绝 |
| PROJ-01 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.actual_media_play_creates_history_after_prepare_and_acknowledged_resume` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.preload_and_detail_do_not_create_history_but_confirmed_play_does_once` → `scope-fixture-personal-native/instrumentation.log` | 实际 Media3 isPlaying 后历史出现；重复确认同 session 只有一条 |
| PROJ-02 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.preload_and_detail_do_not_create_history_but_confirmed_play_does_once` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.local.PlaybackCheckpointNativeTest.preload_does_not_mark_watched_or_promote_pointer` → `regression-storage-off/instrumentation.log` | begin／prepare／预加载不产生实际观看历史或下一集状态 |
| PROJ-03 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.actual_media_play_creates_history_after_prepare_and_acknowledged_resume` → `scope-fixture-personal-native/instrumentation.log` | 浏览／进入详情／准备媒体时历史仍空；用户确认播放后才出现 |
| PROJ-04 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.separate_episodes_and_variants_keep_distinct_positions` → `scope-fixture-personal-native/instrumentation.log` | 同 work 的 episode1/2 与 cutA/B 分别9000/20000/30000ms |
| PROJ-05 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.separate_episodes_and_variants_keep_distinct_positions` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.source_disable_and_three_member_split_preserve_personal_rows_and_staging` → `scope-fixture-personal-native/instrumentation.log` | 逐版本独立，三成员关联／拆分保留 opaque work 和原 source/capture/staging |
| PROJ-06 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.brief_play_keeps_resume_and_completed_latest_does_not_imply_series_finality` → `scope-fixture-personal-native/instrumentation.log` | 本集 COMPLETED，继续观看不误标全剧完成；真实未知终结状态 |
| PROJ-07 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.cache_eviction_keeps_saved_offline_labels_and_opaque_identity` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.empty_and_offline_chinese_states_are_truthful` → `scope-fixture-personal-native/instrumentation.log` | 纯本地 Room 标签可辨识；无媒体显示来源不可用，不宣称离线视频可播 |
| FAV-01 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.favorite_idempotency_order_unfavorite_scope_and_committed_undo` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.concurrent_idempotent_favorite_taps_keep_one_committed_relation` → `scope-fixture-personal-native/instrumentation.log` | 重复／8并发设 true 仅一关系，不盲 toggle、不重排 |
| FAV-02 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.favorite_idempotency_order_unfavorite_scope_and_committed_undo` → `scope-fixture-personal-native/instrumentation.log` | 重复A=true仍B,A；真实重收藏A才变A,B，重开后保持A,B |
| FAV-03 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.favorite_failure_rolls_back_visible_state` → `scope-fixture-personal-native/instrumentation.log` | 真实 SQLite trigger 拒绝 INSERT；DB 未收藏、中文错误、实际焦点仍收藏按钮 |
| FAV-04 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.favorite_idempotency_order_unfavorite_scope_and_committed_undo` → `scope-fixture-personal-native/instrumentation.log` | 仅收藏关系删除，history和resume仍在 |
| FAV-05 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.undo_unfavorite_requires_commit` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.undo_is_bound_to_committed_unfavorite_and_rejects_intervening_action` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.unfavorite_lost_ack_retry_recovers_same_undo_receipt_without_stealing_new_intent` → `scope-fixture-personal-native/instrumentation.log` | 撤销 SQL 故障不假成功；实际提交后恢复；新动作拒绝旧撤销，回执丢失可幂等核实 |
| FAV-06 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.favorite_idempotency_order_unfavorite_scope_and_committed_undo` → `scope-fixture-personal-native/instrumentation.log` | 实际 file Room close/reopen 后稳定ID／动作顺序保持 |
| HIS-01 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.actual_media_play_creates_history_after_prepare_and_acknowledged_resume` → `scope-fixture-personal-native/instrumentation.log` | 实际 Media3事件和Room历史对应，详情不产生观看 |
| HIS-02 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.history_order_ignores_capture_time_rollback_and_duplicate_confirmation` → `scope-fixture-personal-native/instrumentation.log` | capturedAt10000→-10000仍B,A；重复确认不重排 |
| HIS-03 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.delete_history_preserves_favorite_other_work_and_rejects_late_callbacks` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.confirmed_history_delete_allows_new_actual_play_in_same_process` → `scope-fixture-personal-native/instrumentation.log` | 该work历史／各scope检查点／resume删除，收藏保留；新真实会话可再次观看 |
| HIS-04 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.actual_legacy_staging_retry_preserves_delete_tombstone_and_original_records` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.LegacyHandoffNativeTest.explicitly_deleted_history_is_not_replayed_by_active_handoff_retry` → `regression-handoff/instrumentation.log` | 真实 staging 导入及已激活handoff重试均不复活，暂存原行保留 |
| HIS-05 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.delete_history_preserves_favorite_other_work_and_rejects_late_callbacks` → `scope-fixture-personal-native/instrumentation.log` | A/B共享episode1/cutA；删A后两者收藏保留，B历史及精确checkpoint/resume在重开后不变 |
| HIS-06 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.source_disable_and_three_member_split_preserve_personal_rows_and_staging` → `scope-fixture-personal-native/instrumentation.log` | 来源停用／资料修正／三成员拆分不删除原用户引用及来源依据 |
| CONT-01 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.continue_click_uses_committed_episode_and_pauses_on_restore` → `scope-fixture-personal-native/instrumentation.log` | 实际Media3 READY、episode2／original variant、8000±100ms与私有文件URI一致 |
| CONT-02 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.brief_play_keeps_resume_and_completed_latest_does_not_imply_series_finality` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.local.PlaybackCheckpointNativeTest.queued_short_exit_during_slow_room_commit_preserves_previous_work_resume` → `regression-storage-off/instrumentation.log` | 新短会话不覆盖先前9000ms，R05真实5秒策略及慢提交边界保留 |
| CONT-03 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.remove_from_continue_preserves_history_favorite_and_checkpoint` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.continue_toolbar_remove_preserves_other_personal_rows` → `scope-fixture-personal-native/instrumentation.log` | 仅隐藏续播；收藏、历史、逐集checkpoint及持久resume原值保留 |
| CONT-04 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.dismissed_resume_requires_new_confirmed_session_and_eligible_resume` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.retried_delete_and_hide_actions_cannot_remove_newly_confirmed_playback` → `scope-fixture-personal-native/instrumentation.log` | 旧确认／补写不重显；新确认+合格resume才重显；旧请求重试不影响新播放 |
| CONT-05 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.delete_history_preserves_favorite_other_work_and_rejects_late_callbacks` → `scope-fixture-personal-native/instrumentation.log` | 旧session更高sequence与重新确认均RejectedStale；新合法session允许 |
| CONT-06 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.brief_play_keeps_resume_and_completed_latest_does_not_imply_series_finality` → `scope-fixture-personal-native/instrumentation.log` | 完成仅当前episode，未知全剧终结不推断 |
| CONT-07 | PASS／N | `com.ichichuang.cinema.feature.player.PlaybackRuntimeNativeTest.loading_and_failed_retry_do_not_replace_last_committed_position_with_zero` → `regression-runtime/instrumentation.log` | 真实播放器加载／错误重试零值不覆盖最后有效已提交位置 |
| PLAY-01 | PASS／N | `com.ichichuang.cinema.feature.player.PlaybackRuntimeNativeTest.current_player_retry_retains_nonzero_position_instead_of_restarting_at_zero` → `regression-runtime/instrumentation.log` | 真实 Media3 retry 从有效非零检查点继续 |
| PLAY-02 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.unknown_variant_requires_explicit_current_version_choice_without_mapping_position` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.unknown_variant_continue_entry_reaches_existing_detail_choice` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.restored_player_with_removed_variant_reaches_existing_detail_choice` → `scope-fixture-personal-native/instrumentation.log` | 未知旧版本8000ms保留；详情／继续／恢复播放器都可明确选择同集当前版本从0暂停准备，不自动映射 |
| PLAY-03 | PASS／N | `com.ichichuang.cinema.feature.player.PlaybackRuntimeNativeTest.episode_switch_and_late_callback_keep_new_work_resume_pointer` → `regression-runtime/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.separate_episodes_and_variants_keep_distinct_positions` → `scope-fixture-personal-native/instrumentation.log` | 切集保存屏障／旧回调仲裁，独立集及版本位置保留 |
| PLAY-04 | PASS／N | `com.ichichuang.cinema.data.local.PlaybackCheckpointNativeTest.preload_does_not_mark_watched_or_promote_pointer` → `regression-storage-off/instrumentation.log` | 预加载没有实际观看／下一集提升 |
| PLAY-05 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.continue_click_uses_committed_episode_and_pauses_on_restore` → `scope-fixture-personal-native/instrumentation.log` | 实际 Activity recreate 恢复精准位置，isPlaying及playWhenReady均false；独立PID另见外部记录 |
| PLAY-06 | PASS／N | `com.ichichuang.cinema.data.local.PlaybackCheckpointNativeTest.old_session_cannot_overwrite_new_episode_resume_pointer` → `regression-storage-off/instrumentation.log`<br>`com.ichichuang.cinema.feature.player.PlaybackRuntimeNativeTest.episode_switch_and_late_callback_keep_new_work_resume_pointer` → `regression-runtime/instrumentation.log` | 更大位置／迟到sequence不能替换新session持久episode指针 |
| NAV-01 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.dpad_reaches_remove_actions_without_menu_key` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.continue_toolbar_remove_preserves_other_personal_rows` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.undo_unfavorite_requires_commit` → `scope-fixture-personal-native/instrumentation.log` | 仅真实方向／OK／Back可到三动作、确认和撤销；撤销实际bounds可见 |
| NAV-02 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.focusable_favorite_opens_detail_and_returns` → `scope-fixture-personal-native/instrumentation.log` | 收藏→真实详情→Back回同opaquework；独立Favoritescontext |
| NAV-03 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.history_to_player_back_restores_history_context` → `scope-fixture-personal-native/instrumentation.log` | History→DETAIL→PLAYER→Back→Back仍History同项，未跳Home |
| NAV-04 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.continue_click_uses_committed_episode_and_pauses_on_restore` → `scope-fixture-personal-native/instrumentation.log` | 实际Media3目标、URI、READY位置，非只测标签 |
| NAV-05 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.unfavorite_focused_card_uses_valid_neighbor` → `scope-fixture-personal-native/instrumentation.log` | 取消当前收藏后Back实际唯一焦点为现存邻居98 |
| NAV-06 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.user_input_cancels_pending_personal_restore` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.delayed_unchanged_personal_flow_preserves_pending_layout_restore` → `scope-fixture-personal-native/instrumentation.log` | 真实方向键先于延迟Roomflow恢复，迟到结果不抢焦点；无变化flow不破坏有效layout |
| NAV-07 | PASS／N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.confirmation_back_cancels_destructive_action` → `scope-fixture-personal-native/instrumentation.log` | Back取消确认，history／resume／favorite不变、焦点回删除按钮 |
| STORE-01 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.cache_eviction_keeps_saved_offline_labels_and_opaque_identity` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.source_disable_and_three_member_split_preserve_personal_rows_and_staging` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.feature.browse.BrowsePersistenceNativeTest.file_reopen_preserves_three_exact_contexts_and_existing_user_rows` → `regression-browse/instrumentation.log`<br>`com.ichichuang.cinema.data.local.PlaybackCheckpointNativeTest.cache_eviction_keeps_progress_and_original_import_staging` → `regression-storage-off/instrumentation.log` | 真实metadata缓存行淘汰；个人、browse、staging、identity原值保留，文件重开 |
| STORE-02 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.source_disable_and_three_member_split_preserve_personal_rows_and_staging` → `scope-fixture-personal-native/instrumentation.log` | 真实三成员LINKED→保守split；来源available=false，原个人work引用不重分配 |
| STORE-03 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.delete_history_preserves_favorite_other_work_and_rejects_late_callbacks` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.remove_from_continue_preserves_history_favorite_and_checkpoint` → `scope-fixture-personal-native/instrumentation.log` | 真实Room文件close/reopen后列表／隐藏／删除水位和顺序保持 |
| STORE-04 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.action_io_failure_and_cancellation_roll_back_without_false_success` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.lost_acknowledgement_after_commit_retries_without_duplicate_or_reorder` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryNativeTest.retried_delete_and_hide_actions_cannot_remove_newly_confirmed_playback` → `scope-fixture-personal-native/instrumentation.log` | 真实事务前失败／取消回滚，事务后回执丢失幂等确认；固定scope不删除新记录 |
| STORE-05 | PASS／X+N | `com.ichichuang.cinema.feature.personal.PersonalLibraryUiNativeTest.prepare_external_process_personal_restore` → `scope-fixture-personal-native/instrumentation.log`；`external-lifecycle-summary.json` | 外部HOME→am kill→显式启动，以及独立force-stop→启动；新PID／实际XML焦点／8000ms暂停；已删除98不复活、收藏98保留 |
| MIG-01 | N/A／H | 无执行；只标不适用的迁移分支 | v5缺少实际播放确认和持久用户意图，采用显式v6；不声称schema未变 |
| MIG-02 | PASS／N | `com.ichichuang.cinema.data.userdata.PersonalLibraryMigrationNativeTest.v5_to_v6_preserves_all_twenty_two_seeded_tables_and_reopens` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryMigrationNativeTest.failed_v5_to_v6_migration_rolls_back_version_all_rows_and_new_tables` → `scope-fixture-personal-native/instrumentation.log`<br>`com.ichichuang.cinema.data.userdata.PersonalLibraryMigrationNativeTest.v5_resume_projects_without_fabricating_confirmed_history` → `scope-fixture-personal-native/instrumentation.log` | 真实v5的22旧表、有效多集/版本/三browse/订阅/source/staging保留；失败回滚、FK、identity、重开及v5resume无假历史 |
| OFF-01 | PASS／N | `com.ichichuang.cinema.feature.browse.BrowseProbeOffNativeTest.ordinary_activity_uses_legacy_browse_and_never_opens_candidate` → `regression-storage-off/instrumentation.log` | 普通OFF JVM／Debug／unsignedRelease／AndroidTest／双lint，旧运行时唯一writer |
| OFF-02 | PASS／H | `regular-fixture-check.json` | 普通assets／res无合成作品或个人记录注入；update/sources仍空；私有fixture仅instrumentation生成 |
| OFF-03 | PASS／H | `final-compatibility-summary.json` | 普通APK包身份／0.9.8code18／min24target28／ARMv7 JNI／manifest和签名冻结／update字节核对 |

## APK 与兼容性指纹

普通 APK 的 manifest dump 与各 ARMv7 JNI 字节均与既有基线一致；包名、0.9.8/code18、min24/target28 保持。Debug/test 为测试配对签名，Release 仍未签名；生产签名 freeze=true。APK 全字节随新增候选代码变化，不声称与起点 APK 相同。

| 普通产物 | SHA-256 |
| --- | --- |
| debug | `04f1a832caafc27ac30592f3951ba722cf8635f7ee64942f4f3846bc605155ff` |
| androidTest | `05d551721b3b0fc1d2acc37b0a996827e232a26d51c21ede7576ae2168e68343` |
| release | `c4c8e97941845566bd7a225b2ce57a6573ca87750ce3866cc89d7389ba133d80` |

| 最终候选配对 | SHA-256 |
| --- | --- |
| `app-debug-androidTest.apk` | `6729bfe9219d4226d480efe45dc20b85692bed59989f9297fd9bfd8ca2c41b35` |
| `app-debug.apk` | `7fcf5f69ee2929a6c8ade34fab0f41c9793a2a392c38447905dadc64ab9c47cc` |

## 保留的验收边界与停止点

仅保证最近一次真实确认成功的 Room 事务；未确认输入不作持久化承诺。没有证明任意断电／真实磁盘耗尽、旧用户原地升级、生产来源或不同视频时间轴等价、真实离线视频、ARMv7/API24 电视、物理遥控器、生产性能或完整 T09/M04/A06–A11/A18。

本轮停止于 Round 12。完整原始执行证据保持在忽略目录，仓库只提交限定代码、schema、架构／验证说明与进度；没有 merge、push、tag、release、publish 或生产切换。最终本地提交后核对两个工作树、受测 source/index/blob 一致性和自有 AVD 关闭结果，准确 HEAD 与关闭记录见最终答复及 `final-delivery.json`。
