# Round 09 候选来源、查询与订阅验证

限定 Round 09 实现、可用验证及独立源码审查已完成，按明确路径作本地实现及文档交付。最终 JVM276/276、Python62/62、Android原生143/143；没有失败、跳过或原生漏跑/重复计数。只标记本轮合成候选切片和两个前轮覆盖缺口，不将整个 T06、T07、M03 或任何 A 场景标为完成。

复用既有 T02 查询/订阅契约与 Round08 来源身份、作品关联和原始字段依据，新增候选能力观察、独立有界查询及显式信任/授权订阅刷新。实际接口、限额、所有权及裁定见 [结构说明](../architecture/tv-query-subscription.md)。候选只在测试显式构造；无普通运行入口接线。

## 起点与交付包

复用既有隔离工作树和 `codex/contract-foundation`，起点 `96962f6a5255431bf9d316a4bfacac0d55ef7ca3`，初始干净。主树 main 及 metadata-only fetch 后 origin/main 均保持 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`；未 pull、merge、重置或新建工作树。无 upstream 是本轮明确 local-only 用途，不建立远端分支。

ZIP SHA-256 `f56ddd5e942780fdb2f43f48af5281913f94c44ff0c892fe13dc7c4d7f181341`。验证所有归档路径无绝对路径/上级跳转/重复项，四项 SHA256SUMS 全部匹配，三个单独附件逐字节匹配归档原件。

| 包内文件 | SHA-256 |
| --- | --- |
| CODEX_INSTRUCTION.txt | 39682fcac20570d77db02c68608943c42be88813dd9a8c20659f272ed15622dc |
| scoped plan | 8b4001a9f3d9ac0065bd45ded0d2b882f5118d702923df1b64ee2aaa5c11602c |
| acceptance matrix | b94485c6b1e36e58038bec074371565c77958b242e0e7ee2520beb9fee4c9efd |
| README.md | e9de8ee8e9f8fc6338a54df2123c40b1ac1ab605734f96356ac62807ebc88f78 |

原件保存于忽略证据 `.local/evidence/round-09/attachment/`；仓库计划仅将个人工作树绝对路径替换为 `<isolated-worktree>`，不放宽现有 index personal-path 规则。附件是实施规格；本轮操作授权来自用户在聊天中的明确请求。

## 已执行基线与两个覆盖缺口

基线 JVM 实际重跑 221/221，Python 62/62，均无失败/跳过。PRE-01 在原缓存用例预置两行实际缓存、断言删除数、重开后仅缓存消失，逐字段保留个人表并精确比较 A/B 完整 captures 与 routes。PRE-02 新增 A–B–C 两证明三成员组，从 B 拆分后重开，三份 home/group 身份、三个收藏/检查点、入口及原始观察保留，四条标识证明撤销，原两 proof 禁止重用。

原实现立即通过，不伪造产品 RED。首轮身份 native 24/24；补强 capture/route 精确比较后，包含真实 4→5 迁移用例的 native 25/25。独立源码/日志审查确认原两 Minor 已覆盖；其后指出的 route 精确比较不足也已补入并实际重跑。

## 持久化

实际 v4 的十九表不能保存能力缓存、查询游标和订阅授权配置，因此添加显式 `QUERY_SUBSCRIPTION_MIGRATION_4_5`，仅新建 candidate_capability_state、candidate_query_sessions、candidate_subscription_state。工厂保留完整 1→2→3→4→5 路径，无 destructive fallback。

迁移原生测试先真实失败 expected5/actual4；实现后从原 schema4 建库，十九张旧表各一行完整种子，升级、PRAGMA foreign_key_check、关闭重开及整行逐字段比较通过。schema5 由真实 KSP 导出，原十九表实体定义与 schema4 完全一致。

| schema | SHA-256 |
| --- | --- |
| 1，原字节 | 14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959 |
| 2，原字节 | 6b5abe547c00a7a2d2cf2ab1e30e995347b3d9cd014c9700a639e5f87677aca9 |
| 3，原字节 | bda6901970ecceb7c0c1adbe8aec0f0a665243cce9e61e95fcade2b0c3c98b95 |
| 4，原字节 | 140609a645dba466cfd0f1d57059198e4d9527692f3c6402878be87704f808b6 |
| 5，实际生成 | 74d5c2d74324d9f58aeb54ff1c2678d23d3b01d89823e2e3a4f5a4b4d7d8ec2e |

能力 Room adapter 的初始空实现曾在实际 native 重开后 assertNotNull 失败；已补事务保存和解析前长度门禁，最终候选原生102/102包含三项QuerySubscriptionNativeTest且逐方法匹配源码。超限测试使用合法 snapshot 加超量尾部空白，避免非法 JSON 本身掩盖缺少长度门禁。最终 Debug/Release 的 KSP CinemaDatabase_Impl 字节相同，SHA-256 `df7f3fd9dfa68d631c226761f8f59bf67719c7e8603844d05b0a955b517eaa2b`。

## 验证命令与证据约定

所有原始日志、退出码、真实 XML、APK、schema/源码指纹和逐方法 native 记录保留于忽略目录 `.local/evidence/round-09/`。使用已有 mise Temurin21 和 Android SDK，不安装或更换依赖。`run.py` 注入既有 SDK 环境，以本地文件锁串行 Gradle，并保存命令、时间与退出码。编译失败、夹具错误、门禁误用、真实断言失败分别记录，不混作产品 RED。

J1：最终普通门禁 OFF 命令，UTC 12:08:09–12:09:12，退出0，140/140任务实际执行。XML为28套件276测试，0failure/error/skipped；新增能力17、查询17、订阅21，共55项，原221项保留。Python在同源脚本下实际执行62项、退出0；最后一处纯Kotlin缓存条件修正不改变Python或其受测脚本。

```sh
mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  -Pcinema.storageTestArm64=false -Pcinema.playbackRoomProbe=false \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
  :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease \
  --rerun-tasks --no-daemon --no-configuration-cache --console=plain
mise exec -- python3 -m unittest discover -s scripts/tests -v
mise exec -- python3 scripts/check_repository_security.py
```

原生配对构建仅使用 `:app:assembleDebug :app:assembleDebugAndroidTest -Pcinema.storageTestArm64=true`，旧播放/交接回归另启既有 `cinema.playbackRoomProbe=true`；它们不是普通 ARMv7 产物。每个安装、instrumentation、force-stop 和关闭命令固定 ADB host/port/serial，事前核对本轮 owner、唯一 AVD 名、宿主 PID/参数、boot ID、qemu=1、API30、ARM64/无32位、boot完成、测试包及 runner。未使用物理或个人设备，未重启共享 ADB。

N1：最终修正后的ARM64/Probe-OFF构建退出0，已保存配对；随后实际执行下列固定serial命令。`$SDK_ADB` 指既有SDK的platform-tools/adb，绝对路径和每次身份观测保存在忽略证据，未改变SDK或Java版本。所有新原生用例与PRE均包含在N1的102项内。

```sh
mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  -Pcinema.storageTestArm64=true -Pcinema.playbackRoomProbe=false \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  --no-daemon --no-configuration-cache --console=plain
"$SDK_ADB" -H 127.0.0.1 -P 5037 -s emulator-5570 shell am instrument -w -r \
  -e cinema.disposable round06 -e cinema.handoff round07 \
  -e class com.ichichuang.cinema.data.catalog.WorkIdentityStoreInstrumentedTest,com.ichichuang.cinema.data.catalog.QueryCoordinatorNativeTest,com.ichichuang.cinema.data.catalog.QuerySubscriptionNativeTest,com.ichichuang.cinema.data.subscription.SubscriptionRefreshNativeTest,com.ichichuang.cinema.data.local.CinemaDatabaseTest,com.ichichuang.cinema.data.local.LegacyUserDataImportTest,com.ichichuang.cinema.data.local.PlaybackCheckpointNativeTest,com.ichichuang.cinema.data.local.PlaybackSchemaMigrationTest \
  com.ichichuang.cinema.debug.test/androidx.test.runner.AndroidJUnitRunner
```

N2：另41项为既有播放/交接/独立进程回归。保存的Probe-ON/OFF配对按各用例要求切换，用相同instrument参数和明确class或class#method执行，分进程前显式force-stop。N2执行后最后修正只改变能力缓存一条条件、新增其JVM方法及现有native方法六行；N2全部相关源码和二十九条关键路径字节保持。修正后的N1与J1已重新运行，不将先前N1重复计入143。完整11个最终日志的命令/方法清单保存在final-native-summary.json、驱动和identity-gates.jsonl。

| 原生组 | 实际通过数 | 最终日志 |
| --- | ---: | --- |
| N1：身份24、查询6、能力/迁移3、订阅7、原数据库15、原导入19、checkpoint26、旧schema迁移2 | 102 | final-candidate-fixed/instrumentation.log |
| N2：旧播放运行 | 7 | final-old-runtime/instrumentation.log |
| N2：交接gate4、交接17、writer取消1、媒体4 | 26 | handoff/final-handoff/instrumentation.log |
| N2：播放独立进程A/B | 2 | final-old-process-a.log、final-old-process-b.log |
| N2：Legacy Probe OFF | 1 | final-legacy-off/instrumentation.log |
| N2：交接stage/重启/激活/新进程/OFF | 5 | handoff/final-handoff-stage.log、final-handoff-preactive.log、final-handoff-process-a.log、final-handoff-process-b.log、final-handoff-off/instrumentation.log |

源码@Test清单与143个start/terminal记录按多重集完全相等，failed/skipped/not_run/unexpected/duplicate均0。独立进程回放PID3470→3577保持5544ms暂停；交接PID2562→2660仍Legacy，激活PID2731→2836保持新Room7522ms暂停，最后OFF不恢复旧writer。

| 自有目标 | serial / API / ABI | boot ID | 收尾 |
| --- | --- | --- | --- |
| cinema-query-20261024-283ca1f4 | emulator-5570 / 30 / arm64-v8a | c7b8bad0-8b73-44f9-b975-492d34498724 | 身份核对后emu kill，host PID52771消失 |
| cinema-handoff-20261024-33cff492 | emulator-5572 / 30 / arm64-v8a | e1fdca26-1513-4ee7-b714-3cec867e74b4 | 身份核对后emu kill，host PID71255消失 |

AVD数据与原始证据保留，共享ADB保持。未把API30 ARM64模拟器解释为API24、ARMv7 TV或真实用户迁移验收。

## 验收矩阵逐项账本

命令列J1/N1/N2对应上面的实际执行命令/日志。方法别名：C=SourceCapabilityTest，Q=QueryCoordinatorTest，S=SubscriptionRefreshTest，I=WorkIdentityStoreInstrumentedTest，QN=QueryCoordinatorNativeTest，DN=QuerySubscriptionNativeTest，SN=SubscriptionRefreshNativeTest，L=LegacyPlaybackRuntimeNativeTest。每行列出实际方法，结束状态仅限该合成候选断言；“起始未实现/未验”不是宣称该行曾独立断言RED。真实断言RED另在持久化及审查记录逐项说明。

| ID | 测试方法 | 层 / 实际命令 | 起始 → 结束状态及结果 |
| --- | --- | --- | --- |
| PRE-01 | I.cache_eviction_preserves_identity_evidence_routes_and_personal_records | native / N1 | 正确代码、组合覆盖不足 → 两实缓存删除数2、重开零缓存、个人记录/完整captures/routes不变；通过，无产品RED |
| PRE-02 | I.three_member_split_revokes_all_proofs_and_retains_original_personal_references_after_reopen | native / N1 | 二成员覆盖 → 三成员/原身份/个人引用保留，四proof撤销且原proof不可重连；通过，无产品RED |
| CAP-01 | C.ignored_year_is_contradicted_while_raw_year_is_supplied；QN.query02_ignored_year_and_unknown_rows_remain_evidence_outside_accepted_matches | JVM+native / J1+N1 | 未实现/未验 → 忽略1994返回1995不成正向能力或精确匹配；通过 |
| CAP-02 | C.null_empty_and_missing_fields_remain_distinct_from_filter_support；同上QN.query02_ignored_year_and_unknown_rows_remain_evidence_outside_accepted_matches | JVM+native / J1+N1 | 未实现/未验 → 原字段存在性与过滤能力分开、未知留原证据不算匹配；通过 |
| CAP-03 | C.genuine_category_ids_are_source_scoped_and_ignored_category_is_contradicted | JVM / J1 | 未实现/未验 → 两源分类6不等同、忽略t有反证；通过；真实来源语义未验 |
| CAP-04 | C.timeout_updates_health_without_erasing_or_extending_previous_proof；C.fresh_capability_expires_and_revision_or_endpoint_changes_do_not_reuse_claims；DN.capability_cache_reopens_without_turning_timeout_into_unsupported_or_renewing_expiry | JVM+native / J1+N1 | 未实现/未验 → 超时保留原证明/expiry、过期/回拨拒绝fresh、重探恢复；通过 |
| CAP-05 | C.authorized_alias_keeps_identity_but_requires_new_probe_and_similarity_never_authorizes；C.invalidated_alias_survives_reopen_and_old_endpoint_probe_cannot_restore_it | JVM / J1 | 未实现/未验 → 仅显式本地证明令旧能力失效、重开与重探受限；通过；生产授权来源未接入 |
| QRY-01 | Q.independent_cursors_deduplicate_only_source_keys_and_complete_on_explicit_exhaustion；QN.query01_independent_cursors_partial_revision_and_source_keys_survive_reopen | JVM+native / J1+N1 | 未实现/未验 → 实际请求A2/B5/A3，独立游标及revision持久化；通过 |
| QRY-02 | Q.empty_accepted_page_is_partial_then_continues_and_retains_mismatch_and_unknown_evidence | JVM / J1 | 未实现/未验 → 首页零精确匹配仍Partial/hasMore，后页有匹配，原观察保留；通过 |
| QRY-03 | Q.partial_failure_keeps_success_and_retry_cursor_without_guessing_remote_total；Q.timed_out_source_keeps_explicit_failed_coverage_and_retry_cursor | JVM / J1 | 未实现/未验 → 保留A及B失败ID/cursor、总量null且当前已证数独立；通过 |
| QRY-04 | Q.repeated_page_and_cursor_cycles_stop_partial_in_finite_requests | JVM / J1 | 未实现/未验 → 重复/循环两次有限抓取后Partial、重复项不增；通过 |
| QRY-05 | Q.byte_time_and_record_budgets_remain_partial_without_overbudget_commits；Q.empty_accepted_page_is_partial_then_continues_and_retains_mismatch_and_unknown_evidence | JVM / J1 | 未实现/未验 → bytes/time/records/requests限额保留Partial和有限续查状态，不放宽条件；通过 |
| QRY-06 | Q.cancellation_propagates_and_noncooperative_late_q1_cannot_write_after_q2_starts；Q.cancelling_observation_fences_separately_started_noncooperative_fetch；QN.query03_late_guard_or_cancellation_rolls_back_evidence_and_session_together | JVM+native / J1+N1 | 未实现/未验 → Q1晚响应不写Q2、观察取消生效、真实事务整体回滚；通过 |
| QRY-07 | Q.verified_groups_keep_home_and_playback_entries_and_same_title_never_merges；QN.query04_only_actual_verified_group_deduplicates_and_keeps_both_home_and_playback_entries | JVM+native / J1+N1 | 未实现/未验 → 仅Round08已证组去重、两home/入口保留，片名年份不自动关联；通过 |
| SUB-01 | S.shipped_empty_directory_with_declarative_notice_reports_empty_without_consent_or_save；S.exact_empty_default_reports_empty_without_trust_or_write | JVM / J1 | 原实现未覆盖真实notice → 实际文件逐字节夹具CatalogEmpty、不授权/保存/假造source；通过 |
| SUB-02 | S.malformed_duplicate_script_executable_bad_utf8_depth_and_oversize_are_rejected；S.duplicate_ids_endpoints_and_unknown_source_fields_are_rejected | JVM / J1 | 未实现/未验 → 类型化拒绝坏/大/脚本/重复/未知输入，不执行/保存；通过 |
| SUB-03 | S.owner_rule_binds_catalog_origin_publisher_and_strong_key；S.replay_lower_changed_same_revision_and_clock_rollback_preserve_exact_row；SN.subscription02_network_signature_replay_and_clock_failure_preserve_exact_row_after_reopen | JVM+native / J1+N1 | 未实现/未验 → 未授权信任/签名/重放/时钟失败保持原候选行；通过 |
| SUB-04 | S.public_url_and_all_special_address_classes_never_execute_request；S.mixed_dns_answer_rebind_redirect_and_byte_cap_are_rejected_without_followup | JVM / J1 | 未实现/未验 → 注入resolver/exchange实证私网/redirect不发后续请求、地址固定；通过；真实socket未验 |
| SUB-05 | S.consent_revoked_while_fetching_is_checked_at_atomic_commit；SN.subscription03_revoked_consent_and_disabled_tombstone_are_rechecked_at_real_commit | JVM+native / J1+N1 | 未实现/未验 → 提交时拒绝陈旧授权、停用tombstone不重启用；通过 |
| SUB-06 | S.cancelled_refresh_propagates_and_preserves_row；SN.subscription04_cancellation_after_real_upsert_rolls_back_consent_config_and_user_rows；SN.subscription05_real_sql_abort_rolls_back_without_losing_prior_consent_or_user_rows | JVM+native / J1+N1 | 未实现/未验 → 取消传播/SQL故障真实回滚，授权/配置/个人表完整一致；通过 |
| SUB-07 | S.valid_signed_fixture_needs_real_consent_then_configures_candidate_only；SN.subscription01_valid_signature_and_explicit_bound_consent_config_reopen_without_touching_user_rows | JVM+native / J1+N1 | 未实现/未验 → 合成可信签名+实际绑定授权原子保存/重开，Configured只证明候选；通过 |
| DB-01 | DN.schema_four_to_five_preserves_all_nineteen_seeded_tables_and_reopens；DN.oversized_capability_snapshot_fails_closed_without_replacing_original_row | native / N1 | 实际expected5/actual4断言RED → 迁移十九表完整种子/外键/重开一致，旧schema哈希不变；通过 |
| OFF-01 | L.probe_off_uses_original_datastore_with_existing_records_and_does_not_touch_room；全普通产物及29路径比对 | native+build/static / N2+J1 | 原路径冻结 → ProbeOFF原DataStore工作、无候选接线，身份/ABI/签名/默认目录保持；通过本轮限定检查 |

无本矩阵合成用例未跑或阻塞；真实来源兼容、私网/公网实际连接、真实用户/TV/UI/生产仍明确未验证，不因此把整个A场景标为通过。

## 最终普通产物与静态回归

普通门禁OFF Debug/unsigned Release/androidTest实际构建及双Lint退出0。Debug为21Warning/1Hint，Release为22Warning/1Hint，均0Error，id/severity/message多重集与Round08逐项相同；保留既有Gradle弃用提示，不削弱规则。

| 普通产物 | SHA-256 | 实际身份 / ABI / 签名 |
| --- | --- | --- |
| app-debug.apk | 1d7539aaa5f0d82caef55912c6d17737d66c31638860bfdbdc21d7f44dbea7ca | com.ichichuang.cinema.debug / armeabi-v7a / 既有Debug签名verify0 |
| app-debug-androidTest.apk | 51b68e67c608a650f7c42fc16e3e8a738f5b874747a05037955b8dc2a4a43c0d | com.ichichuang.cinema.debug.test / 无JNI / 既有Debug签名verify0 |
| app-release-unsigned.apk | e396e81e6d0480514648f29b4a91e1852618de178319b34eba45ad9a6de10ca6 | com.ichichuang.cinema / armeabi-v7a / unsigned，预期verify1及缺META-INF/MANIFEST.MF |

实际解包和ELF检查为ARMv7 class1/machine40，manifest及JNI内容与Round08完全一致，版本0.9.8/code18、min24/target28不变。N1另用的ARM64 Debug SHA-256 `cb91c9dbe456d4e7c5e81515c94e300ddf9497af73655efb35f48ebcdd4bb8be`，不作为普通产物或ARMv7执行证据。

## 已知验证过程纠正

avdmanager 最初缺省 Java 不存在，改用已有 mise Temurin21，未安装新工具。未完成启动或尚未安装 runner 的身份探测拒绝，未绕过安装门禁。第二台 AVD 的端口预检最初误查前一台 ADB 端口，改为本台 console/ADB 配对端口后创建，无停止其他会话。

一次旧 native 基线调用误将 Probe-ON-only 的二十二项交接方法放入 OFF 构建；既有门禁拒绝，原始失败记录保留，不计为产品缺陷或最终通过。后续按正确构建/档案重跑。查询初始未实现取消测试曾等待骨架从不触发的 transport，终止仅本次测试 worker 后增加有界等待；完整断言 RED 使用独立实际执行结果。一次 query digest 调用 protected codec.toJson 导致编译错误，修正为现有 internal helper，不计为行为 RED。

## 独立审查与边界

能力切片首审发现一项 Important：反序列化可接受实际 probe 不会产生的双维度请求或相同 sample/control 正向证明；新增两项真实 codec→cache.open RED 后修复，16/16 GREEN，限定第二次审查确认 addressed。持久化独立审查确认 additive schema、原表/原字节和解析前门禁；指出的超限夹具已改为合法 JSON 加空白，最终N1已实际重跑。

查询首审三项 Important（snapshot/fetch 修订竞态、UTF-16 页指纹碰撞、非法 UTF-8 逃出类型化失败）已真实RED17/3→GREEN17/17，限定复审全部addressed。订阅首审唯一Important为实际空默认目录notice不兼容，真实RED21/1→GREEN21/21，限定复审确认修复；fixture逐字节匹配实际文件SHA-256 `dbce77a3921f424c6cceab2171deae3cf45ae52fa2bf09e8361c439a580091c6`。

随后fresh-context全改动源码终审0Critical/0Important/1Minor。Minor M1为成功100/expiry150后记录较晚timeout200，再重开并回拨125时旧能力可能误报fresh；按本轮新鲜度契约决定修复。新增 `SourceCapabilityTest.reopened_cache_does_not_revive_expired_proof_after_rollback_behind_later_timeout` 真实RED17tests/1failure（CLOCK_ROLLBACK vs FRESH），修改lookup对比持久化latest.observedAt，GREEN17/17；现有native方法增加重开回拨/过期/原expiry断言并重新执行N1。仅三路径相对终审包变化，完整J1也重新执行。原终审报告保持原字节；限定第二次复审确认M1 addressed、修复新增0Critical/0Important/0Minor，没有剩余延后Minor。

审查范围覆盖SSRF、发布者/授权信任、重放、查询取消/循环/假完成、原始依据/个人记录保留及schema。审查者读源码与部分执行者日志，未独立运行Gradle、JVM、ADB或网络；限定复审不冒充第二次全改动审查，实际执行由本报告负责核对。最终25项应用/测试/schema指纹与已运行源码保持一致。

普通 Application/Koin/Home/Settings/MovieRepository、legacy DataStore、播放器和 release/signing/ABI/默认目录等二十九条关键路径相对起点字节相同。默认 sources 仍空，notice 原样保留；签名和 ABI/版本冻结不变，无生产可信根或新媒体目录。

本轮不验证真实来源语义/兼容性、真实公网 socket、API24 设备、ARMv7 TV、遥控/UI、真实用户升级、系统掉电或生产切换。合成限额/取消证明不等于 A21 播放负载和全设备性能验收；RSA 算法按 [Android Signature 官方支持表](https://developer.android.com/reference/java/security/Signature) 核对，实际 provider 执行仅限本轮 JVM/API30。私网判定参考 [IANA IPv4](https://www.iana.org/assignments/iana-ipv4-special-registry/) 与 [IANA IPv6](https://www.iana.org/assignments/iana-ipv6-special-registry/)；候选采用保守地址子集，不能声称所有公共来源兼容。

## 本地 Git 交付

实现提交 `3cb684fabcf0a98f9bc0845ecb40bcecaff6a562`：27个明确路径，3905增行/7删行，包含25项应用/测试/schema及结构说明和导入计划。暂存blob、工作区、最终受测指纹与commit blob逐字节一致；提交前index302文件、0所检查规则违规。本报告和既有进度账本单独作结项文档提交，最终完整index303文件、0所检查规则违规。扫描只涵盖index的既定规则，没有扫描历史，不作全面安全无缺陷声明。

提交前重新检查分支、工作区、upstream及ahead/behind，metadata-only `git fetch --no-tags origin`成功；相对origin/main为ahead16/behind0，无upstream是既定local-only用途，用户本轮明确禁止push。主树及origin/main均保持原基线，没有同步合并需要。全部29个任务路径明确列于下方；没有其他非忽略工作改动。两个提交均用简体中文说明目的、范围、实现和验证；最终文档提交的完整HEAD随用户交付报告及忽略证据final-delivery.json记录，避免文档自引用commit hash。

未push/merge/publish、未触碰既有Release或生产签名；未扫描实时资源、添加生产信任根/媒体目录、访问物理或个人设备、激活生产catalog、实现T07或UI改版。仅本轮候选范围本地收口，完整T06/M03/A14/A15/A17/A18/A21继续部分/未验收；停止于Round09。

精确路径（原始patch另存implementation-staged-diff.patch及最终diff证据）：

```text
app/schemas/com.ichichuang.cinema.data.local.CinemaDatabase/5.json
app/src/androidTest/kotlin/com/ichichuang/cinema/data/catalog/QueryCoordinatorNativeTest.kt
app/src/androidTest/kotlin/com/ichichuang/cinema/data/catalog/QuerySubscriptionNativeTest.kt
app/src/androidTest/kotlin/com/ichichuang/cinema/data/catalog/WorkIdentityStoreInstrumentedTest.kt
app/src/androidTest/kotlin/com/ichichuang/cinema/data/local/PlaybackSchemaMigrationTest.kt
app/src/androidTest/kotlin/com/ichichuang/cinema/data/subscription/SubscriptionRefreshNativeTest.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/QueryCoordinator.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/QuerySessionCodec.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/RoomCandidateQueryStore.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/RoomSourceCapabilityPersistence.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/SourceCapabilityCache.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/SourceCapabilityProbe.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/SourceCursorState.kt
app/src/main/kotlin/com/ichichuang/cinema/data/local/CinemaDatabase.kt
app/src/main/kotlin/com/ichichuang/cinema/data/local/CinemaDatabaseFactory.kt
app/src/main/kotlin/com/ichichuang/cinema/data/local/dao/QuerySubscriptionDao.kt
app/src/main/kotlin/com/ichichuang/cinema/data/local/entity/QuerySubscriptionEntities.kt
app/src/main/kotlin/com/ichichuang/cinema/data/local/migration/QuerySubscriptionMigration.kt
app/src/main/kotlin/com/ichichuang/cinema/data/subscription/CandidateSubscriptionStore.kt
app/src/main/kotlin/com/ichichuang/cinema/data/subscription/DirectoryValidator.kt
app/src/main/kotlin/com/ichichuang/cinema/data/subscription/PublicDirectoryFetcher.kt
app/src/main/kotlin/com/ichichuang/cinema/data/subscription/SubscriptionRefreshCoordinator.kt
app/src/test/kotlin/com/ichichuang/cinema/data/catalog/QueryCoordinatorTest.kt
app/src/test/kotlin/com/ichichuang/cinema/data/catalog/SourceCapabilityTest.kt
app/src/test/kotlin/com/ichichuang/cinema/data/subscription/SubscriptionRefreshTest.kt
docs/architecture/tv-query-subscription.md
docs/superpowers/plans/2026-10-24-round-09-source-capability-query-subscription.md
docs/superpowers/progress/2026-10-23-tv-rebuild.md
docs/verification/2026-10-24-round-09-source-query-subscription.md
```
