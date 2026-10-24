# Round 11 原生浏览与焦点恢复验证

本矩阵42项中41项通过、1项条件N/A；整轮终审的F-I1/F-I2经过真实失败、统一修复及限定复审，最终结论见文末。仅限自有合成Debug profile，不代表完整T08或目标电视验收。

## 起点与附件

- 隔离分支 `codex/contract-foundation`，起点 `32b6bfdcfce6e730af68c246f87c694d09e30915`；开始时两个工作树均干净。main 与已 fetch 的 origin/main 为 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`。
- ZIP SHA-256：`b4d5576a1959e656f89a19162eaaba16bb59a5a0864e092c757ca9971a38b015`。归档路径合法，四项 manifest 校验通过，独立下载的指令／计划／矩阵均与包内字节相同。
- 原始附件位于忽略的 `.local/evidence/round-11/attachment/`。仅导入 scoped plan；个人绝对路径和会被当前安全扫描误判的 home-feature 路径表述做了最小替换，完整原件保留，未修改安全规则。

| 包内原件 | SHA-256 |
| --- | --- |
| CODEX_INSTRUCTION.txt | dc144045bf90a9dbbd9a043320704cd1fbd89eb16b9d588190c9e4a9fded42a1 |
| README.md | 47018259d481773fadd05a3e212102c37b44bb2b4eb5901d37d0c09515e69122 |
| BROWSE-FOCUS-ACCEPTANCE-MATRIX.md | ac891ce0a3e7bd6d98f8961db9cfdb65605c2dd8b330029a3df2f304ecd6276b |
| scoped plan | 923c221f9070ee6843d4f63c5a61023937f22f400a112a2337d1d0f3eaf01dfb |

## 已执行阶段与独立审查

| 阶段 | 真实证据 |
| --- | --- |
| A 门禁 | 非法请求原先可执行的实际 RED；门禁 GREEN；JVM317/317，Python62/62。独立只读审查无阻塞发现。提交 ef58782。 |
| B 协调与持久化 | 恢复／checkpoint／codec／异步加载实际断言 RED→GREEN；首轮 JVM336/336。独立审查发现旧结果、旧布局、可变链与错置指针四项问题。修复均有真实失败用例，最终 JVM342/342（B25）；限定复审四项均解决。提交66afb33。 |
| A 原生构建接线修正 | 实际合法配对被内部 bundleDebugClassesToCompileJar 错拒；ARMv7不一致配对却可配置。两项行为经 RED→GREEN 修正，三门ON配对完整构建成功，15个配置请求通过，独立限定复审通过。提交ea5af76。 |
| C 原生接线 | 原 MainActivity 缺少候选入口的真实断言 RED；完成真实 Compose/NavHost、平台按键、语义焦点与视口恢复。启动失败清理、拒绝焦点确认和真实尺寸变化均有实际失败证据。最终29/29原生，0失败0跳过。独立审查I1/I2及修复引入N1均RED→GREEN后限定复审解决；提交bf59b15。 |
| D 文件与故障 | 最终`final-fix-browse/review-fix-regression-c-and-d`同源ON pair为C29+D30，共59/59、0失败0跳过；OFF另1/1。真实Room重开、故障、取消、容量失败、旧文件保护、停止Insets污染及首次零高恢复均实测；限定复审spec/quality通过。实现提交6ab8d96。 |
| 终审统一修复 | F-I1深锚点删除的两个冷启动变体、F-I2同token并发flush均取得真实RED。共享定位策略及成功确认复用后，新增5 JVM与3原生；既有无锚点新页回归58/59保留，新增守卫后59/59。最终限定复审两项ADDRESSED，0新增Critical/Important/Minor。提交3c47c8c。 |
| E 中途旧原生回归 | 145个唯一旧方法本轮新执行，145通过、0失败、0跳过；使用owned5580/5582并关闭。该批早于C审查修复，保留为中途记录，最终修复后回归另列。 |

TDD 记录保留失败尝试：B checkpoint 首次缺少独立 RED 门后丢弃实现、重建最小 baseline；codec 首次缺字段异常和新 runBlocking 测试的非 void 初始化错误不计有效 RED，随后另行取得断言失败。完整原始命令、退出码和 XML 位于 `.local/evidence/round-11/`。

## 42 项验收矩阵

每行按实际层次分别填入 JVM、原生或外部生命周期证据。PASS 只指该行实际列明的合成候选证据；没有远程图片、真实源、物理电视或完整生产筛选页面验收。MIG-02 因 schema 仍为 v5 标记 N/A；未执行不记通过。矩阵项数与测试方法数不同，同一真实方法可支持多个断言，汇总不得重复累计。

### 环境、类名与精确命令键

命令工作目录均为现有 `codex/contract-foundation` 隔离工作树。`N`、`O` 为 API30 ARM64 自有 `emulator-5586`，AVD `cinema-browse-20261025-d66fb5f4`，每次操作经 `final-fix-browse/native.py` 核验 host PID、AVD、boot ID、应用集合与签名配对。`N` 的三个 probe 属性均 ON；`O` 的 browse/playback 为 OFF、仅 storageTestArm64 为 ON，因此 `O` 不替代普通 ARMv7 产物检查。

矩阵类名前缀：`NAV`=`com.ichichuang.cinema.feature.browse.BrowseNavigationNativeTest`；`PERSIST`=`com.ichichuang.cinema.feature.browse.BrowsePersistenceNativeTest`；`OFF`=`com.ichichuang.cinema.feature.browse.BrowseProbeOffNativeTest`；`RESTORE`=`com.ichichuang.cinema.core.navigation.BrowseRestoreCoordinatorTest`；`CHECKPOINT`=`com.ichichuang.cinema.core.navigation.BrowseCheckpointCoordinatorTest`；`CODEC`=`com.ichichuang.cinema.core.navigation.BrowseNavigationCodecTest`；`GATE`=`com.ichichuang.cinema.core.navigation.DebugBrowseProbeGateTest`；`BUILD`=`test_browse_probe_build_gate.BrowseProbeBuildGateTests`。

以下为已经实际执行的命令；label 对应 `.local/evidence/round-11/` 下命令 JSON、日志及结果。

```sh
# N：完整同源 ON 构建及59个唯一原生方法，0失败/0跳过
python3 .local/evidence/round-11/run.py review-fix-final-pair-build mise exec java@temurin-21.0.11+10.0.LTS -- ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -Pcinema.storageTestArm64=true -Pcinema.playbackRoomProbe=true -Pcinema.browseRestoreProbe=true --offline --console=plain
python3 .local/evidence/round-11/final-fix-browse/native.py run review-fix-regression-c-and-d com.ichichuang.cinema.feature.browse.BrowseNavigationNativeTest,com.ichichuang.cinema.feature.browse.BrowsePersistenceNativeTest

# O：OFF ARM64 控制，1 executed/1 passed/0 failed/0 skipped
python3 .local/evidence/round-11/run.py review-fix-final-off-build mise exec java@temurin-21.0.11+10.0.LTS -- ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -Pcinema.storageTestArm64=true -Pcinema.playbackRoomProbe=false -Pcinema.browseRestoreProbe=false --offline --console=plain
python3 .local/evidence/round-11/final-fix-browse/native.py run review-fix-final-off-native com.ichichuang.cinema.feature.browse.BrowseProbeOffNativeTest

# J：最终源码完整JVM，349/349；三门OFF，140任务全部重新执行
python3 .local/evidence/round-11/run.py review-fix-ordinary mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew -Pcinema.browseRestoreProbe=false -Pcinema.storageTestArm64=false -Pcinema.playbackRoomProbe=false :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease --rerun-tasks --no-daemon --no-configuration-cache --console=plain

# G：4个host unittest，15个真实Gradle配置请求；非法组合均在任务执行前拒绝
python3 .local/evidence/round-11/run.py gate-abi-pair-green mise exec -- python3 -m unittest discover -s .local/evidence/round-11 -p test_browse_probe_build_gate.py -v

# P：起点逐字节比对，不运行设备或迁移
mise exec -- python3 .local/evidence/round-11/verify-preservation.py
```

`N` 的 app SHA-256 为 `0a5eece65d91f3948c7bb9038326e04741def7922a3e3bf5173d049203fe7710`，test SHA-256 为 `68bf0bb1b0d7a7789ae1b65b82e896e78789b0b2df0d2472102a1a060f8e21c9`。`final-fix-browse/review-fix-regression-c-and-d/result.json` 保存59对唯一 start/terminal；`final-fix-browse/review-fix-final-off-native/result.json` 保存1对。外部准备方法已包含在59个方法内，随后重复执行不能再算新唯一方法。表内6ab8d96为持久化及lint修复提交；最终受测源码还包含终审限定修复3c47c8c。

| Case ID | 方法／类 | 环境与命令 | 状态 | 实际断言／观察值 | 问题与提交 |
| --- | --- | --- | --- | --- | --- |
| GATE-01 | `OFF.ordinary_activity_uses_legacy_browse_and_never_opens_candidate`；`GATE.ordinary_mode_never_opens_candidate_even_with_authorization` | O + J | PASS | 真实普通MainActivity无候选语义节点、browseProbe=null、旧BrowseStore读写保留、空源与needsSetup保持、候选DB不存在；测试替换Updater阻止启动网络，不覆盖在线更新行为 | ef58782；6ab8d96 |
| GATE-02 | `BUILD.test_probe_rejects_non_pair_before_any_task_executes`；`test_both_probe_flags_reject_unsafe_roots` | G，host真实Gradle配置 | PASS | Release/bundleDebug/install/publish/help/单Debug及缺配对请求非零退出，含“仅允许”，没有执行任务；内部bundleDebugClassesToCompileJar与合法pair允许 | ef58782、ea5af76 |
| GATE-03 | `PERSIST.missing_or_corrupt_profile_and_consent_fail_before_database_creation`；`corrupt_database_fails_closed_without_replacement`；`NAV.malformed_active_snapshot_closes_candidate_route` | N | PASS | 缺失/损坏profile与consent均不进入action且不创建DB；现存坏DB hash不变；malformed active时lease归零且Activity不保留失效runtime。坏DB测试wal/shm/journal均不存在，不代表带WAL损坏覆盖 | bf59b15；D损坏保护6ab8d96 |
| GATE-04 | `PERSIST.existing_legacy_record_refuses_candidate_and_preserves_exact_file_hashes` | N | PASS | 合成旧DataStore非空时拒绝候选且DB不存在；前后cinema_browse文件SHA-256均为`f3f0285277c410b7809bc9f42146b074c08819e795e8023d3c3e79e067c9ddeb` | ef58782；6ab8d96 |
| GATE-05 | `BUILD.test_armv7_pair_rejected_despite_both_probe_flags`；`test_all_three_flags_accept_legal_pair_graph`；`PERSIST.candidate_lease_serializes_other_candidate_and_refuses_legacy_writer` | G + N | PASS | browse/playback ON但storage ARM64 OFF拒绝；三门ON合法pair可构建；第二candidate等待同lease，旧writer实际save被拒绝，释放后第二candidate进入 | ea5af76；6ab8d96 |
| CONTEXT-01 | `NAV.library_deep_item_detail_back_returns_same_item` | N | PASS | 真实详情/Back后query完整相等：1994、JP、horror、非中文、movie、降序、test-source；稳定work99、深列表anchor及offset保持 | bf59b15 |
| CONTEXT-02 | `NAV.return_uses_origin_context_not_home_default` | N | PASS | Favorites→详情→实际Back后仍为Favorites，原完整query与原favorite焦点保持 | bf59b15 |
| CONTEXT-03 | `NAV.back_returns_true_origin`；`RESTORE.navigation_origin_is_bounded_and_back_exits` | N + J | PASS | 原生History→详情→Back保留History；JVM DETAIL→PLAYER两层退回原History。候选没有播放器入口，不自动启动播放；PLAYER链仅JVM契约证据 | 66afb33、bf59b15 |
| CONTEXT-04 | `NAV.switch_tabs_preserves_independent_focus`；`PERSIST.file_reopen_preserves_three_exact_contexts_and_existing_user_rows` | N | PASS | 三页切换实际唯一焦点99/24/54各自恢复；另有三独立context完整query、anchor、offset关库重开相等 | bf59b15；6ab8d96 |
| CONTEXT-05 | `NAV.explicit_query_switch_preserves_old_precise_context`；`PERSIST.source_year_sort_switch_roundtrips_after_activity_recreation` | N | PASS | 显式新queryId，1994→1995、降序→升序、test-source→test-other-source；仅1严格匹配，重建后实际work100与新完整query保持，旧context仍可读取 | bf59b15；D重建revision修复6ab8d96 |
| CONTEXT-06 | `NAV.library_deep_item_detail_back_returns_same_item` | N | PASS | fixture第1与第100作品标题相同；详情返回实际聚焦供应的opaque work99 ID，没有跳到第1项或按标题寻址 | bf59b15 |
| CONTEXT-07 | `RESTORE.navigation_origin_is_bounded_and_back_exits`；`NAV.rapid_inputs_and_physical_repeats_do_not_duplicate_routes`；`final_back_exits_activity` | J + N | PASS | JVM最多DETAIL/PLAYER两个不同子路由、重复链拒绝；原生重复OK/Back仅一层，顶层最后Back使Activity DESTROYED | 66afb33、bf59b15 |
| CONTEXT-08 | `PERSIST.activity_recreation_same_pid_preserves_query_focus_and_drops_modal` | N | PASS | 真实modal打开后ActivityScenario.recreate，同PID、modal=false、detail=false、原完整query和work99实际焦点恢复；候选无删除/确认命令可重放 | 6ab8d96 |
| FOCUS-01 | `NAV.deep_list_waits_for_anchor_and_layout`；`viewport_resize_repositions_pending_deep_anchor`；`PERSIST.cold_complete_removed_anchor_keeps_existing_deep_target` | N | PASS | placement交付暂停时没有任何海报焦点；真实放置后才到work99；portrait→landscape后新pass重新定位深列表，避免停在80..89；冷启动Complete移除95锚点后仍实际聚焦99，布局放行前无海报抢焦点 | bf59b15；F-I1修复 |
| FOCUS-02 | `NAV.deep_list_waits_for_anchor_and_layout`；`window_readiness_blocks_focus_delivery`；`failed_focus_request_retries_only_current_token`；`rejected_focus_then_resize_requires_new_placement_pass`；`PERSIST.first_nonzero_grid_measure_restores_nonzero_offset_before_focus` | N | PASS | 实际grid高度0→574，首次有效尺寸才换算偏移并完成scroll，anchor45/焦点54/offset−.15保留（像素误差<.002）；未放置/无窗口/拒绝focus均不能抢焦点，旧布局不可复用 | bf59b15；6ab8d96 |
| FOCUS-03 | `NAV.user_input_cancels_late_focus_restore`；`user_input_cancels_delayed_top_level_restore`；`failed_focus_retry_is_cancelled_by_real_input`；`direction_during_detail_back_flush_cannot_revive_saved_focus` | N | PASS | 真实按键同步撤销Room load/focus重试/Back等待中的ticket；迟到完成不抢回99或24，控件合法焦点保持且旧restoreToken=null | bf59b15，含审查I1 |
| FOCUS-04 | `PERSIST.old_initial_query_load_cannot_replace_new_explicit_query_and_layout`；`old_pending_layout_cannot_focus_after_new_query_is_installed`；`RESTORE.old_result_layout_callback_cannot_prove_new_result_placed` | N + J | PASS | 真实延迟Q1完成晚于显式Q2后，当前完整query/实际焦点仍Q2；旧pending placement释放也不能聚焦旧99；JVM旧revision/layout事件拒绝 | D真实RED→GREEN；6ab8d96 |
| FOCUS-05 | `NAV.refresh_does_not_steal_focus`；`refresh_without_prior_user_input_keeps_actual_focus`；`PERSIST.offline_partial_local_snapshot_and_metadata_completion_preserve_query_focus` | N | PASS | 用户实际移到98后刷新仍98；无新输入刷新仍99；本地长中文metadata、5→3与重建Complete保持query和ID。仅合成本地metadata/布局，**没有远程图片加载或图片pipeline验证** | bf59b15；6ab8d96 |
| FOCUS-06 | `NAV.modal_traps_focus_and_returns_invoker` | N | PASS | 筛选modal打开后实际DOWN/RIGHT仍唯一聚焦“关闭筛选”，Back恢复“筛选”，背景未响应方向键 | bf59b15 |
| FOCUS-07 | `NAV.modal_removed_invoker_returns_same_row_control`；`PERSIST.removed_modal_invoker_returns_same_toolbar_neighbor`；`RESTORE.modal_removed_invoker_uses_same_row_then_page_default` | N + J | PASS | 移除筛选入口后真实Back唯一聚焦同排列数；JVM同排为空时才回页面默认。未把JVM空行分支称为原生布局执行 | D真实RED→GREEN；6ab8d96 |
| FOCUS-08 | `NAV.layout_change_preserves_work_not_column_index`；`viewport_resize_repositions_pending_deep_anchor`；`rejected_focus_then_resize_requires_new_placement_pass` | N | PASS | 实际placedColumns=3时仍聚焦work99；真实视口变化和先拒绝focus组合均以新布局pass恢复同ID，旧placement作废 | bf59b15 |
| FOCUS-09 | `PERSIST.cold_complete_removed_anchor_and_target_restores_same_row_neighbor`；`NAV.removed_complete_target_uses_same_row_neighbor`；`RESTORE.removed_target_uses_nearest_same_row_then_control`；`removed_target_without_old_row_neighbor_uses_current_row_entry` | N + J | PASS | 冷启动Complete同时缺95锚点及99目标，真实深视口同排98；原有Complete缺99也为98；JVM同排旧邻居不可定位但当前排存在时row-entry；整排不存在时页面控件。三段链分别具名断言 | 66afb33、bf59b15；D补测6ab8d96 |
| FOCUS-10 | `NAV.incomplete_result_does_not_prove_target_removed`；`RESTORE.target_missing_while_partial_is_not_removed` | N + J | PASS | 缺99但Partial时无海报焦点、knownMatchedCount=null且显示部分；状态机WaitingForItem；实际输入仍可到页面控件，没有假定删除或Complete-empty | 66afb33、bf59b15 |
| FOCUS-11 | `NAV.selection_is_distinct_from_focus` | N | PASS | 选择99后LEFT到98，99 Selected=true；98 Focused=true且Selected=false，实际只有一个焦点 | bf59b15 |
| FOCUS-12 | `NAV.rapid_inputs_and_physical_repeats_do_not_duplicate_routes`；`rapid_dpad_keeps_all_requested_steps`；`held_ok_repeat_during_flush_keeps_single_accepted_entry`；`back_during_detail_entry_flush_prevents_late_detail`；`direction_during_detail_entry_flush_moves_and_cancels_entry` | N | PASS | 20次LEFT从99到79；重复物理OK仅一个DETAIL；同downTime repeat不撤销已接受入口；IO等待期间Back优先退出、LEFT到98后不迟到进入旧详情 | bf59b15，含I1及N1修复 |
| STORE-01 | `PERSIST.stable_focus_burst_writes_exactly_one_real_room_transaction`；`CHECKPOINT.save_on_leave_survives_debounce` | N + J | PASS | 20次真实LEFT到79，SQL audit记录释放前0、稳定提交后1次active写事务，真实Room读取最终79；JVM明确delay参数500ms，不按每帧写 | D实际SQL audit；6ab8d96 |
| STORE-02 | `CHECKPOINT.concurrent_same_revision_flushes_share_one_committed_acknowledgement`；`PERSIST.background_flush_and_navigation_share_same_room_acknowledgement`；`CHECKPOINT.save_on_leave_survives_debounce`；`NAV.held_ok_repeat_during_flush_keeps_single_accepted_entry`；`direction_during_detail_entry_flush_moves_and_cancels_entry` | J + N | PASS | 同token/revision双flush仅一写且均Committed；原生直接background入口挂起实际Room后单次平台OK进入详情，无假失败，重开链为DETAIL（此新增方法不执行pause/resume）；debounce尚未完成时flush确认最后offset；原生入详情实际Room save等待完成后才进入；期间新输入按真实时序取消旧入口，不吞丢按键 | 66afb33、bf59b15 |
| STORE-03 | `CHECKPOINT.stale_route_write_cannot_replace_newer_context`；`in_flight_old_route_commit_finishes_before_new_active_pointer`；`PERSIST.two_rapid_page_changes_and_background_flush_keep_new_context` | J + N | PASS | 旧token返回RejectedStale；在途旧写先于新active提交；实际后台flush挂起期间连续Favorites/History，释放并关库重开仍为最新History | 66afb33；6ab8d96 |
| STORE-04 | `PERSIST.file_reopen_preserves_three_exact_contexts_and_existing_user_rows` | N，实际文件库两次独立gate open/close | PASS | Library/Favorites/History各自snapshot/parent/query/focus/anchor/offset及codec payload字节相等；loadLatest各自正确、loadActive为最后History，实际schema version=5 | 66afb33；6ab8d96 |
| STORE-05 | `PERSIST.failing_room_transaction_keeps_old_active_and_retry_pending`；`cancelled_checkpoint_before_commit_keeps_retryable_state`；`cancelled_transaction_before_commit_rolls_back_all_navigation_pointers`；`cancellation_after_acknowledged_commit_preserves_last_successful_snapshot`；`debounced_room_failure_is_visible_and_retryable`；`sqlite_page_limit_failure_without_disk_fill_keeps_last_commit` | N | PASS | 实际Room触发器故障/取消回滚context与active，pending保留；UI不伪造Committed，重试后清除失败提示；成功确认后cancel不丢数据；主写连接页数上限真实Failed(storage_sql)，旧值仍可读，未填满系统磁盘 | D自动结果观察与失败保护6ab8d96 |
| STORE-06 | `PERSIST.offline_partial_local_snapshot_and_metadata_completion_preserve_query_focus` | N | PASS | 私有本地snapshot先恢复work99，Partial保留完整query且未知总数；本地资料Complete后重建为100匹配、仍同query/焦点。未接真实离线网络源 | 6ab8d96 |
| STORE-07 | `PERSIST.file_reopen_preserves_three_exact_contexts_and_existing_user_rows` | N | PASS | metadata evictAll返回1，重开metadata为空；favorites引用1、完整playback snapshot、含NUL/孤立代理的raw capture、三个browse context与active均保留 | 6ab8d96 |
| STORE-08 | `PERSIST.file_reopen_preserves_three_exact_contexts_and_existing_user_rows`；`existing_legacy_record_refuses_candidate_and_preserves_exact_file_hashes` | N，writer静默排他期间取文件hash | PASS | 正常候选前后空旧文件SHA-256均e3b0…b855；非空拒绝前后均f3f0…ddeb，完整hash在result.json。仅自有合成profile，未读取用户真实DataStore | 6ab8d96 |
| LIFE-01 | `NAV.library_deep_item_detail_back_returns_same_item` | N，实际同Activity Compose/NavHost及平台Back | PASS | 真实详情返回仍为原query、opaque work99、原深列表anchor和relativeOffset，非仅ViewModel状态断言 | bf59b15 |
| LIFE-02 | `PERSIST.activity_recreation_same_pid_preserves_query_focus_and_drops_modal`；`recreation_same_geometry_preserves_exact_anchor_and_relative_offset`；`shutdown_cannot_replace_last_stable_viewport_checkpoint`；`pause_resume_does_not_revive_cancelled_poster_restore` | N | PASS | 同PID重建，query/focus/modal保持；同1668×574视口anchor90/offset−.080139376保持；停止前后Room快照相等；pause/resume不复活被真实LEFT撤销的海报恢复 | D-I1与零高布局真实RED→GREEN；6ab8d96 |
| LIFE-03 | `PERSIST.prepare_external_lifecycle`＋外部`lifecycle.py kill/launch/capture` | X：后台HOME→am kill→显式am start | PASS | PID5247→5698；实际XML唯一焦点99，完整query/context/anchor90/offset−.080139376一致，detail=false；ActivityManager杀后台进程，不称run-as SIGKILL | final-fix-browse/external-lifecycle-summary.json；6ab8d96 |
| LIFE-04 | `lifecycle.py force-stop/launch/capture` | X：am force-stop→外部显式am start | PASS | PID5698→6080，am start状态COLD/ok；实际焦点99，完整query、anchor90及offset一致；与Activity重建和kill分别记录 | final-fix-browse/external-after-force-stop-external.json |
| LIFE-05 | `PERSIST.prepare_external_uncommitted_delay`＋外部未提交边界中断 | X：真实LEFT98、waitingSave/committingSnapshot98→force-stop→显式启动 | PASS | 已确认99；XML实际焦点98、事务仍在有界10秒暂停中，force-stop后PID6520→7149；实际焦点恢复99，完整query/anchor/offset保持。未确认输入不保证持久化 | final-fix-browse/external-uncommitted-boundary.json；final-fix-browse/external-after-uncommitted-external.json |
| MIG-01 | `PERSIST.file_reopen_preserves_three_exact_contexts_and_existing_user_rows`；`verify-preservation.py` | N + P | PASS | 实际Room version=5；schema1–5及6项manifest/legacy/update文件共11项与起点字节相同，无6.json：**v5 unchanged** | 66afb33；D未更改schema |
| MIG-02 | v5→v6迁移不适用 | P，schema未升级 | N/A | 沿用既有Room v5 payload，不创建v6迁移，不把空库重开声称迁移成功 | v5 unchanged |
| OFF-01 | `OFF.ordinary_activity_uses_legacy_browse_and_never_opens_candidate`；全JVM与普通产物/lint | O + J + `review-fix-ordinary`、`verify-artifacts.py`、`compare-lint.py` | PASS | JVM349/349；普通140任务全部执行；0.9.8/code18、ARMv7 JNI和manifest与基线一致，Release未签名；双lint 0 Error，ID/级别/消息多重集新增0/移除0；旧HomeVM/DataStore/schema/update字节不变 | 5项真实lint错误已最小修复，无suppress/baseline；6ab8d96 |
| OFF-02 | 范围、Git与设备身份记录核验 | `final-avd-shutdown.json`、逐命令身份门、`git status/rev-parse`、metadata-only fetch | PASS | 全程仅本轮owned合成AVD；所有11个历史/最终owned host PID均已消失；main/origin-main仍7f3e063；仅本地提交，无push/merge/tag/release，未访问真实源或物理设备 | 实现6ab8d96；最终文档HEAD由交付记录给出 |

### 当前证据边界

修复后最终原生205/205（候选59、OFF1、旧回归145）、外部LIFE03/04/05及普通OFF产物已验证。远程图片、生产T08/T09/T10、A03/A04/A05/A19、M02/M04与实体电视不由该合成切片验收。

外部命令X：`mise exec -- python3 .local/evidence/round-11/final-fix-browse/run-external-lifecycle.py`。脚本每次ADB动作前复核owned5586，使用完整组件`com.ichichuang.cinema.debug/com.ichichuang.cinema.MainActivity`，以实际XML、完整快照、前后PID及截图交叉验证；命令/状态/boot身份记录完整保留。首轮组件名错误、run-as kill不支持、随后真实anchor漂移均保留为失败历史，最终使用ActivityManager后台am kill并单独记录force-stop，未混称SIGKILL。最终5586关闭结果见设备清单。

## 最终验证与边界

- JVM：349/349，0 failures/errors/skipped；`review-fix-ordinary`，普通三门OFF，140 tasks全部重新执行。
- Python：`mise exec -- python3 -m unittest discover -s scripts/tests -v`，62/62；`final-python.log`。
- Lint：`review-fix-ordinary`真实运行Debug/Release，0 Error/Fatal；Debug21 warnings+1Hint、Release22 warnings+1Hint；与起点按ID/severity/message计数多重集完全相同，added/removed均空。
- 初次最终普通检查真实失败5项（RememberInComposition2、RestrictedApi3），未计通过；修复使用remember和仅ON的公开Window.Callback，无lint抑制、baseline或依赖变更。修复后重新执行全部原生及外部生命周期。
- 普通产物保存于`.local/evidence/round-11/ordinary-artifacts-after-review/`；manifest dump和JNI逐字节与已记录普通基线一致，包版本0.9.8/code18、min24/target28、armeabi-v7a保持。Debug/test正常测试签名，Release仍未签名，生产签名冻结未改变。
- schema1–5、manifest、旧DataStore/HomeViewModel、update三文件共11项与起点字节相同；无schema6、无新依赖、没有普通来源目录注入。

| 普通APK | SHA-256 |
| --- | --- |
| Debug | d8442ab927d6c8597748e1a0e82dbdd093ffcff75685cbb4bf87cafb63054f67 |
| AndroidTest | baa18aa147550bba711fab4536bcc06b677987a462a9a0973e4fb9e2bab4afce |
| unsigned Release | 678cb29d3aafacf3f95d6dee62f255b27b628169f6def53d5354c941ce77c582 |

原生测试仅使用明确创建并拥有的API30、纯ARM64、ranchu AVD。每次安装/运行核验serial、host PID、AVD名、boot ID、API/ABI、包集合、runner及同签名配对；不枚举/访问物理设备，不使用盲目connectedAndroidTest，不关闭共享ADB。完整命令、逐方法start/result与APK/源码hash在忽略的证据目录；历史失败和中途成功保留，不计入最终唯一方法数。

完整T08、A03/A04/A05/A19、M02/M04仍未验收。此处没有生产导航/DI切换、真实来源、远程图片pipeline、真实用户升级、API24/ARMv7电视/遥控器、任意断电或真实磁盘耗尽证明。强停只保证最后已确认Room事务，不保证未提交输入；停止于Round11，不进入T09/T10，不push/merge/tag/publish/release。

## 最终原生方法对账

候选同源pair由`review-fix-final-pair-build`构建，`final-fix-browse/native.py run review-fix-regression-c-and-d`执行ON59，`review-fix-final-off-native`执行OFF1；外部生命周期脚本exit0。`mise exec -- python3 .local/evidence/round-11/run-final-fix-native-regression.py`重新执行旧145，exit0。`collect-native.py` 将最终日志的start与terminal和当前源码@Test方法按多重集对齐：205 expected / 205 executed / 205 passed / 0 failed / 0 skipped / 0 not-run / 0 duplicate。重复prepare与历史执行不重复计数。完整方法名、日志清单见 `.local/evidence/round-11/final-native-summary.json`。

| 原生类 | 唯一执行/通过 |
| --- | --- |
| BrowseNavigationNativeTest | 29/29 |
| BrowsePersistenceNativeTest | 30/30 |
| BrowseProbeOffNativeTest | 1/1 |
| WorkIdentityStoreInstrumentedTest | 24/24 |
| QueryCoordinatorNativeTest | 8/8 |
| QuerySubscriptionNativeTest | 3/3 |
| SubscriptionRefreshNativeTest | 7/7 |
| CinemaDatabaseTest | 15/15 |
| LegacyUserDataImportTest | 19/19 |
| PlaybackCheckpointNativeTest | 26/26 |
| PlaybackSchemaMigrationTest | 2/2 |
| PlaybackRuntimeNativeTest | 7/7 |
| PlaybackProcessReplayTest | 2/2 |
| LegacyPlaybackRuntimeNativeTest | 1/1 |
| LegacyHandoffGateNativeTest | 4/4 |
| LegacyHandoffNativeTest | 17/17 |
| LegacyWriterCancellationNativeTest | 1/1 |
| LegacyHandoffMediaNativeTest | 4/4 |
| LegacyHandoffProcessTest | 5/5 |

| 最终环境 | AVD / host PID / boot ID | 状态 |
| --- | --- | --- |
| emulator-5586 API30 ARM64 | cinema-browse-20261025-d66fb5f4 / 2274 / 0550cdaf-effe-4562-b733-79c505f52a46 | 已关停、host PID消失 |
| emulator-5580 API30 ARM64 | cinema-round11-regression-20261024-e8b160ff / 5806 / 6e1d33cf-8a17-4c7d-b6f4-ecdcab092748 | 已关停、host PID消失 |
| emulator-5582 API30 ARM64 | cinema-round11-handoff-20261024-a5fe88ca / 6306 / 3f4bc798-2a95-4f78-b13f-ad10d916f3f7 | 已关停、host PID消失 |

## 审查与Git交付

A/B/C/D均有独立只读任务审查，发现的问题按真实失败→修复→限定复审闭合。整轮终审在6ab8d96发现两个Important：F-I1已移除深锚点阻止尚存目标/同行邻居进入布局；F-I2同token并发flush错误返回RejectedStale。统一修复后，唯一一次限定复审确认两项均ADDRESSED，修复范围0 Critical / 0 Important / 0 Minor，无deferred/parked项。审查读取实际源码与保存证据，没有独立重跑构建、测试或ADB。

修复波保留所有失败历史：最初同token JVM RED中另一条错误的“新revision必须拒绝显式flush”预期被撤回，不算产品RED；anchor+target首次native前置等待失败亦不计产品RED，随后独立取得真实布局超时。共享定位最初导致新无锚点页面主动滚首项，既有测试实际58/59失败；不改原断言，增加无锚点守卫的真实RED后再得到最终59/59。新增background flush native直接调用同一生产background入口、真实Room阻塞和平台OK，并未执行Activity pause/resume；已有pause/resume用例和外部进程实验分别记录。

完整审查记录位于忽略的`.superpowers/sdd/2026-10-24-round-11-browse-navigation-focus-restore/final-review.md`、`task-final-fix-report.md`及`final-fix-review.md`。整轮初审核对旧202方法；限定复审核对最终59+1、349个JVM XML、140任务、双lint及五份外部XML；最终旧145和205多重集由主执行者重新对账，未冒称reviewer重跑。

本轮实现提交依次为ef58782、66afb33、ea5af76、bf59b15、6ab8d96、3c47c8c；后续仅提交本报告、架构说明和进度账本。20项最终受测源码指纹与工作文件、index和实现HEAD blob一致；当前index337文件0违规，未扫描Git历史。main/origin-main均保持7f3e063且ahead/behind为0/0；候选分支保留原无upstream状态。普通构建输出最后恢复OFF，三份APK与保存的最终普通产物哈希相同。最终文档HEAD与Git状态见`.local/evidence/round-11/final-delivery.json`及最终聊天。
