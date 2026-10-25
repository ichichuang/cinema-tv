# Round 15 — 原生作品详情（线路/集数/收藏/续播）验证报告

> 执行日期：2026-10-25/26。分支 `codex/contract-foundation`，起点 HEAD `827b3983f8271b3f35eac0e6996a9dac38ea8392`（已核对）。本报告只陈述实际执行的验证与证据；未执行者不标 PASS。
> 56 行验收矩阵源：`docs/verification/DETAIL-LINES-ACCEPTANCE-MATRIX.md`（附件导入副本）。

## 执行环境

- 一次性自有 AVD：`cinema-detail-ca73d7d0`（API 30、纯 `arm64-v8a`、`abis32` 为空、固定串号 `emulator-5586`、ADB `127.0.0.1:5041`、`-no-window -gpu swiftshader`），身份核验脚本 `.local/evidence/round-15/wait-boot.py`（boot_id `7d83e5ba-…`）。中途一次冷重启（更换全新 AVD 实例）后全部相位在同一实例上重跑。
- 工具链：SDK `<仓库本地>/.local/toolchain/android-sdk`（主检出工作区内）、JDK 21.0.2、Gradle `--no-daemon --no-configuration-cache`。
- 七级探针配对（全部 `-P…=true`）：`storageTestArm64/playbackRoomProbe/browseRestoreProbe/personalLibraryProbe/homeVisualProbe/libraryFilterProbe/detailProbe`，仪器参数 `cinema.disposable=round06 handoff=round07 browse=round11 personal=round12 home=round13 library=round14 detail=round15`；R12–R14 回归相位按各自原配对（详情探针 OFF、`cinema.detail=round14regression` 等）。
- 阶段驱动：`.local/evidence/round-15/run-native-phases.py`；截图拉取 `pull-shots.py`；全量普通回归 `ordinary-regression.py`；门禁检查 `gate-check.py`。

## 实际执行的总账

| 套件 | 命令/证据 | 结果 |
| --- | --- | --- |
| R15 详情原生（6 类） | `run-native-phases.py r15-detail` → `r15-detail-native-FINAL.log/.json` | **37/37 通过**（Ui 14 + Actions 7 + Nav 9 + Lifecycle 1 + NeighborFocus 1 + Screenshot 5），0 失败 0 跳过 |
| 详情探针 OFF（普通 Debug 门禁） | `r15-detail-off` → `r15-detail-off-native.log` | 1/1（`ordinary_debug_keeps_legacy_detail_and_never_surfaces_candidate_detail`） |
| 两阶段真实进程实验 N09 | `r15-process`（a/b 两相位，驱动比对 PID） | 2/2：阶段 A PID 与阶段 B PID 不同（`round15-process-pids.json`），已提交（作品/线路/剧集）上下文与 8000ms 暂停续播跨进程恢复且保持暂停，未提交弹层不复活 |
| R12 浏览回归 | `r12-browse` | **59/59 通过 + 5 个 assumption 跳过**（见披露）；一次冷跑 flake（`old_pending_layout_cannot_focus_after_new_query_is_installed` 在 `requireNotNull(it.browseProbe)` 处抛出）经同相位重跑全绿 |
| R12 个人库回归 | `r12-personal` | 121/121 |
| R12 播放回归 | `r12-playback` | 34/34 |
| R13 首页回归 | `r13-home` | 29/29 |
| R14 片库回归（4 类含截图） | `r14-library` | 27/27 |
| 普通（全探针 OFF）回归 | `ordinary-off` | 29/29 |
| JVM 单元 | `ordinary-regression.py`（`:app:testDebugUnitTest`，全 OFF 配对） | 见 `ordinary-regression.json`（本轮含既有全部 JVM 套件；新增 `DetailProjectionTest` 17 + `DetailSelectionControllerTest` 13） |
| 双 lint + APK 指纹 | 同上 | `final-lint-debug.xml`/`final-lint-release.xml` 与 R14 基线逐条消息差异 + debug/release/androidTest APK SHA-256/字节数 + aapt `native-code` ABI |
| 门禁拒绝检查 | `gate-check.py` | 6/6 拒绝（见下） |
| Python | `python3 -m unittest discover -s scripts/tests -v` | 全绿（见下） |
| Room schema 1–6 | `baseline-schema-hashes.txt` 对账 | 字节级不变（见下） |
| 截图 | `shots/round15-*.png`（6 张，全部真实 AVD 渲染） | 两轮“观察—修复—复查”完成（见视觉复核） |

## 56 行逐行核对

判据缩写：原生方法 = 真实仪器执行的真实断言；JVM = 宿主单测；PROC = 两阶段真实进程；SHOT = 真实 AVD 截图 + 像素/视觉复核；SEC = 代码/工件检查；BUILD = 真实 Gradle 任务。

### G — 授权、探针隔离与个人数据保护（6）

| ID | 结果 | 真实方法/命令与证据 |
| --- | --- | --- |
| G01 `ordinary_debug_stays_legacy` | **PASS** | `DetailOffNativeTest.ordinary_debug_keeps_legacy_detail_and_never_surfaces_candidate_detail`（`cinema.detail=round15` 参数下 BuildConfig=false 不出现候选详情/线路单）；`ordinary-off` 相位 29/29（BrowseProbeOff/LegacyPlayback 仍校验各自参数）；普通回归全 OFF 配对构建 + lint（`ordinary-regression.json`） |
| G02 `release_cannot_activate_detail` | **PASS** | `gate-check.py` 6/6：Release/bundle/publish/install/signing 任务对全部被 `GradleException` 拒绝（含 `cinema.detailProbe` 级）；`DETAIL_PROBE` 默认 `false`（`app/build.gradle.kts`）；签名冻结不变（R02 指纹） |
| G03 `authorized_debug_profile_opens_detail` | **PASS** | `WorkDetailUiNativeTest.authorized_profile_opens_candidate_detail_from_library`：经 R14 片库真实 D-pad 到达，断言 4 线路、默认选择 variant-a/epA2、主操作“继续播放”启用、四线路 chip 与主操作 settle 后在语义树 |
| G04 `unauthorized_profile_refuses_detail` | **PASS** | `unauthorized_profile_refuses_candidate_detail`：删除授权档案后整体拒绝（browse/detail/home 运行时全 null，候选屏不出现、档案不消费） |
| G05 `default_catalog_remains_empty` | **PASS** | `DetailOffNativeTest`（普通路径不出现任何合成内容）+ SEC：默认媒体源清单 `update/sources.json` 仍为 `"sources": []`（不含任何演示源；`scripts/check_repository_security.py` 对账 0 违规）；全部合成数据只写入 `noBackupFilesDir` 私有一次性档案（`WorkDetailFixture`/`WorkDetailNativeFixture`） |
| G06 `personal_records_keep_one_writer` | **PASS** | `detail_browse_does_not_write_personal_records`（进入/聚焦/换线/取消弹层/图片装载零写入，Room 计数不变）；单写者仍为 R12 `PersonalLibraryRuntime`（复核确认无新写入者） |

### P — 稳定身份、溯源与诚实元数据（9）

| ID | 结果 | 真实方法/命令与证据 |
| --- | --- | --- |
| P01 `canonical_work_id_drives_detail` | **PASS** | 原生 `same_title_year_opens_distinct_stable_works`（同题同年两作品分别打开各自稳定 workId）+ JVM `DetailProjectionTest`（同题异身投影分立） |
| P02 `source_entries_are_provenance_bound` | **PASS** | JVM `DetailProjectionTest` 全部事实字段按 `FieldEvidence`（SourceReported/Confirmed/Conflict）投影；线路显示携带来源声明（源条目 + 版本身份），无 UI 推断 |
| P03 `unknown_year_is_not_filled` | **PASS** | 原生 `unknown_year_and_conflict_language_truthful`（“年份未知”不造年份）+ JVM（unknown → 年份未知） |
| P04 `conflicted_fields_are_not_silent_facts` | **PASS** | 同上原生方法（“证据冲突，尚未裁决”）+ JVM 冲突用例 |
| P05 `audio_subtitles_not_work_language` | **PASS** | JVM `DetailProjectionTest`：线路音轨声明（`audioLanguages`）不参与作品语言事实 |
| P06 `same_work_all_distinct_routes_preserved` | **PASS** | 原生 `same_label_distinct_routes_selectable_by_stable_id`（a/b 同名不同 id 不合并）+ 4 线路（a/b/c/d）全保留 |
| P07 `missing_portrait_image_truthful` | **PASS** | SHOT `detail_portrait_and_missing_poster_states`：竖幅真实标题保留、缺图=深色块+详情图标（像素断言最大亮度 >60，实测 95）不冒充海报，无伪造演员信息 |
| P08 `source_status_freshness_disclosed` | **PASS** | 原生 `disabled_and_unreachable_routes_truthful`（已停用/不可达如实标注且不可播放）+ `stale_route_refresh_never_overwrites_choice_or_focus`（陈旧不充当可播承诺） |
| P09 `not_finished_without_work_evidence` | **PASS** | 原生 `watched_latest_episode_not_series_finality`（看完最新集≠全剧终、无伪造总集数/评分）+ JVM |

### E — 播放线路、剧集身份与选择器（10）

| ID | 结果 | 真实方法/命令与证据 |
| --- | --- | --- |
| E01 `same_label_distinct_route_ids` | **PASS** | 原生 `same_label_distinct_routes_selectable_by_stable_id` + JVM `DetailSelectionControllerTest`（同名线路按 id 分立选择） |
| E02 `disabled_or_unreachable_line_semantics` | **PASS** | 原生 `disabled_and_unreachable_routes_truthful`：c/d 状态文字如实、语义 Disabled、OK 不派发 |
| E03 `long_source_name_is_reachable` | **PASS** | 原生 `long_route_name_focusable_and_selectable_by_stable_id`（独立评审 Minor-1 补强）：真实 D-pad DOWN 聚焦超长名线路 chip（`线路:round15-variant-h`，语义标签完整不截断）、OK 激活同一稳定身份且同名 STALE 线路 i 不合并、不开弹层 + SHOT（1080p 线路行截图） |
| E04 `episode_progress_is_variant_scoped` | **PASS** | 原生 `resume_targets_exact_episode_variant_position_and_stays_paused`（epA2 进度徽标仅在 variant-a；换线后从 0 开始）+ JVM |
| E05 `unknown_episode_number_no_fabrication` | **PASS** | 原生 `unknown_episode_and_empty_routes_no_fabrication`（“未提供集数名”、不造“第 N 集”、不造总数）+ JVM |
| E06 `episode_picker_dpad_and_back` | **PASS** | 原生 `episode_picker_dpad_and_back_trap` + `episode_sheet_lists_selected_route_and_restores_opener`（弹层焦点陷阱、Back 精确恢复打开者） |
| E07 `switch_line_does_not_copy_resume` | **PASS** | 原生 `explicit_play_uses_selected_verified_route_without_cross_variant_resume` + 进程阶段 B（切到 variant-b 播放从 0 开始，8000ms 检查点不跨版本携带） |
| E08 `old_episode_history_survives_line_switch` | **PASS** | 原生 `favorite_toggles_only_canonical_work_and_survives_disabled_route` 与换线用例（历史/收藏/续播目标不被换线清写） |
| E09 `empty_routes_no_fake_episode_one` | **PASS** | 原生 `unknown_episode_and_empty_routes_no_fabrication`（无线路作品主操作禁用 + 中文可行动状态，无虚构第 1 集）；`只剩停用线路的作品` 同样禁用 |
| E10 `stale_line_refresh_never_overwrites_choice` | **PASS** | 原生 `stale_route_refresh_never_overwrites_choice_or_focus` + JVM 刷新令牌用例（迟到令牌丢弃；所选线路消失才回退顶替位置） |

### A — 播放/续播、收藏与失败行为（9）

| ID | 结果 | 真实方法/命令与证据 |
| --- | --- | --- |
| A01 `resume_targets_exact_episode_variant_position` | **PASS** | 原生 `resume_targets_exact_episode_variant_position_and_stays_paused`（真实 Media3 本地合成 wav，`requestedResumePosition=8000ms`、暂停待确认）；三元组精确回查由既有 `RoomPlaybackProbe.select` 保证 |
| A02 `restore_paused_until_user_confirms_play` | **PASS** | 同上（`playerPlaying=false`）+ PROC 阶段 B（跨进程重开后英雄区“epA2 · 0:08/0:12”，进入播放器仍暂停） |
| A03 `detail_browse_does_not_write_history` | **PASS** | 原生 `detail_browse_does_not_write_personal_records`（Room 计数前后不变） |
| A04 `explicit_play_uses_selected_verified_route` | **PASS** | 原生 `explicit_play_uses_selected_verified_route_without_cross_variant_resume` + `play_dispatch_validates_at_point_of_dispatch`（派发时点校验线路可用与身份，经 `beforePlayDispatch` 钩子注入） |
| A05 `unavailable_play_disables_without_fallback` | **PASS** | 原生 `disabled_and_unreachable_routes_truthful` + `unknown_episode_and_empty_routes_no_fabrication`（不自动回退、不造 URL）+ JVM |
| A06 `favorite_toggles_only_canonical_work` | **PASS** | 原生 `favorite_toggles_only_canonical_work_and_survives_disabled_route`（稳定 workId、不影响历史/进度、离线态可用） |
| A07 `favorite_sql_failure_rolls_back_ui` | **PASS** | 原生 `favorite_sql_failure_rolls_back_ui_and_permits_retry`（`BEGIN EXCLUSIVE` 原生持锁逼真实提交失败，UI 回滚 + 中文重试） |
| A08 `favorite_lost_ack_retry_idempotent` | **PASS** | 原生 `favorite_lost_ack_retry_idempotent`（丢回执重试收敛到目标态，绝不反向翻转） |
| A09 `media_error_preserves_resume_and_route` | **PASS** | 原生 `media_error_preserves_resume_and_route`（真实本地媒体错误：不换线、不清检查点、未确认不标已看） |

### N — 来源、D-pad、焦点与生命周期（10）

| ID | 结果 | 真实方法/命令与证据 |
| --- | --- | --- |
| N01 `detail_back_returns_actual_origin` | **PASS** | 原生四用例：`detail_back_returns_home_origin` / `…_library_origin_with_query_and_anchor` / `…_favorites_origin` / `…_history_origin` |
| N02 `deep_library_query_and_anchor_preserved` | **PASS** | `…_library_origin_with_query_and_anchor`：1994/JP/恐怖深查询（5 部）+ 锚点作品 Back 后恢复（`appliedQuery.queryId`、`navigation.snapshot.query` 非空、焦点/锚点意图回到原作品） |
| N03 `picker_focus_trap_and_opener_restore` | **PASS** | 原生 `episode_picker_dpad_and_back_trap`（背景键不可达）+ `episode_sheet_lists_selected_route_and_restores_opener` |
| N04 `user_input_cancels_late_focus` | **PASS** | 原生 `user_input_cancels_late_focus_restore`（`inputEpoch` 撤销迟到恢复） |
| N05 `back_unwinds_exactly_one_layer` | **PASS** | 原生 `back_unwinds_exactly_one_layer`（弹层→详情→播放器→详情→来源，逐层一次一格） |
| N06 `held_ok_one_action_and_long_ok_safe` | **PASS** | 原生 `held_ok_one_action_and_long_ok_safe`（held 去重、长按不先执行短按） |
| N07 `empty_or_removed_route_focus_fallback` | **PASS** | 原生 `removed_route_focus_falls_back_to_replacement_neighbor`（所选线路被移除 → 顶替位置的稳定线路，绝不跳无关首页） |
| N08 `activity_recreate_restores_committed_detail` | **PASS** | 原生 `WorkDetailLifecycleNativeTest.activity_recreate_restores_committed_detail_context_not_uncommitted_sheet`（真实重建恢复已提交身份/来源与有效焦点，未提交弹层不复活） |
| N09 `external_restart_restores_committed_pause` | **PASS** | PROC 两相位（真实 force-stop/新 PID）：`WorkDetailProcessReplayTest.phase_a_commit_detail_context_for_process_replay` + `phase_b_new_process_restores_committed_context_and_paused_resume`（PID 断言 `assertNotEquals`；8000ms 精确且暂停；换线 epA2 重选后仍精确恢复） |
| N10 `r14_n09_missing_deep_anchor_native_assertion` | **PASS** | 新增原生 `TvLibraryNeighborFocusNativeTest.removed_deep_anchor_focus_lands_on_specific_adjacent_stable_work`：深列表第 87 号真实聚焦后经用户意图查询真实移除，以保存查询上下文 + 真实移除后条目/全量保存序喂给**真实** `BrowseRestoreCoordinator`，断言 `RestoreOutcome.Fallback(ids[86])`（原行 85–89 存活邻居中最近者）。层次披露：协调器/快照为真实组件直连，未走 Compose 焦点落地（R14 相位 `deep_list_detail_back_restores_work_and_anchor` 已覆盖 Compose 落点，本轮 27/27 重跑通过） |

### L — 原生视觉、内容诚实与可访问性（7）

| ID | 结果 | 真实方法/命令与证据 |
| --- | --- | --- |
| L01 `detail_1280_720_no_clipped_actions` | **PASS** | SHOT `detail_1280x720_real_render_no_clipped_actions` + `shots/round15-detail-1280x720-long.png`：五动作 settle 断言 + 像素/视觉复核确认**两行换行布局**（第一行 播放+选择线路，第二行 选择集数+收藏+返回），无右缘裁切；长标题完整；剧集入口可达 |
| L02 `detail_1920_1080_render_verified` | **PASS** | SHOT `detail_1920x1080_real_render` + `round15-detail-1920x1080-lines.png`：对齐正常、线路 chip 焦点描边清晰、元数据/状态完整（像素簇分析 + 视觉复核） |
| L03 `large_text_long_title_and_episode` | **PASS** | SHOT `detail_large_font_long_texts_keep_actions_reachable`（1.3× 字号真实 D-pad 逐键走焦到 选择线路/选择集数/收藏操作/详情返回）+ 截图复核五操作全部可见、无文字截断 |
| L04 `portrait_and_no_poster_states` | **PASS** | SHOT `detail_portrait_and_missing_poster_states`：竖幅（300×450 合成图）裁切合理、真实标题保留、图片装载不抢焦点；缺图占位图标**像素断言**最大亮度 >60（修复前实测 20，见视觉复核） |
| L05 `reduced_motion_interaction_not_blocked` | **PASS** | SHOT `detail_reduced_motion_keeps_feedback_and_input`（三动画缩放 0 后方向/Back 输入不被阻塞，焦点反馈保留） |
| L06 `simplified_chinese_no_brand_or_false_badges` | **PASS** | 原生 `ui_copy_is_chinese_without_brand_or_diagnostics`（界面文案中文、无品牌/人气/总集数/评分/诊断 ID）+ 全部截图复核 |
| L07 `original_vector_authority_remains_single` | **PASS** | SEC：全部图标复用 R13 `TvOriginalIcons`（Details/Favorite/Back/Play/Filter），未新增图标资产、未引入第二图标权威；`PosterImage`/`TvPrimaryButton`/tvFocus 令牌全部复用 |

### R — 回归、安全、可追溯与评审（5）

| ID | 结果 | 真实方法/命令与证据 |
| --- | --- | --- |
| R01 `room_schemas_1_to_6_unchanged` | **PASS** | `baseline-schema-hashes.txt`（R14 基线）与工作区 `app/schemas/**` 1–6 逐文件 SHA-256 对账全部一致；本轮未新增 schema/迁移 |
| R02 `ordinary_artifacts_identity_unchanged` | **PASS** | `ordinary-regression.json`：普通（全 OFF）配对下 debug/release-unsigned/androidTest APK SHA-256/字节数 + aapt badging（`native-code: armeabi-v7a`、versionCode 0.9.8/18）；签名冻结未触碰 |
| R03 `jvm_native_python_lint_actual_runs` | **PASS** | JVM `testDebugUnitTest` 全量（含新增 30 个详情用例）；原生九相位如上表（方法名/串号/参数全部记录）；Python `unittest discover`；双 lint 与 R14 基线逐消息差异（新增/删除清单见 `ordinary-regression.json` 的 `lint.*.added/removed`） |
| R04 `independent_review_and_committed_hashes` | **PASS** | 独立只读评审（fork 代理）见下文；`final-tested-source-hashes.py` 对账 磁盘 == HEAD blob（提交后执行）；本地提交不推送不合并 |
| R05 `prior_assumption_skips_disclosed` | **PASS** | R14 报告的 5 个 assumption 跳过在本轮 r12-browse 相位保持可见（`'2': 5`，`BrowsePersistenceNativeTest` 的 code-2 假设跳过事件），与 PASS 分开统计；未外推为通过 |

## 视觉复核（两轮“观察—修复—复查”，CLAUDE.md 上限内完成）

方法：真实 AVD 截图 → 像素级分析（亮度/簇/行块）+ 视觉模型读图交叉验证。视觉模型单份整屏报告存在误报（同屏两份 1080p 报告互相矛盾），**所有结论以像素证据为准**。

**第一轮发现并修复（2 项真实缺陷）：**
1. **缺图占位图标不可见**（海报区 0 亮像素，最大亮度 22/255 = 纯 Panel 背景）：根因一为 `PosterImage` 自带不透明背景，图标画在其下被完全遮盖（z 序）；根因二为 org.json 对显式 `null` 的 `optString` 返回字面量 `"null"`，绕过 `== null` 图标条件且令 Coil 加载必败。修复：图标叠画在 `PosterImage` 之上且仅缺图时显示；`WorkDetailRuntime.reproject` 消费前归一化 `"null"`/空白 → null。复查像素断言亮度 95（>60）✓。
2. **动作行不换行导致按钮被推出屏外**：修复前 1080p 长主按钮文案下“返回”整体在屏外、720p 下“选择集数/收藏/返回”三个在屏外（像素簇证据），而旧断言因 bounds 字符串格式失配（`Rect(0, 0 - 0, 0)` vs 实际 `Rect.fromLTRB(...)`）从未发现。修复：`Row` → `FlowRow`（与线路 chips 同语言）。复查 720p/1.3× 截图确认两行换行、五操作全部可见 ✓。

**第二轮发现并处理（3 项）：**
1. 正则大小写笔误（`FromLTRB` vs 实际 `fromLTRB`）——修正为大小写不敏感。
2. 两处真实 settle 竞态（打开详情后线路/剧集节点晚一两帧进语义树；`applyUserQuery` 挂钩异步重派发焦点在走格后落地抢焦点）——测试改为等待 settle 信号（标签齐全 / 运行时 `focused != null`），并补充片库侧超时转储便于诊断。
3. **720p 语义 `boundsInWindow` 零值伪影**：Row 与 FlowRow 两版构建下，该屏滚动内容节点普遍报 `Rect.fromLTRB(0,0,0,0)`，与像素实测矛盾（二分验证排除 FlowRow 回归）。处置：不基于语义边界做可见性断言；可见性用像素断言（L04）+ 截图视觉复核背书，可达性用真实 D-pad 走焦断言。已在架构文档记录该观测特性。

**过程性事件披露：**
- 一次 Gradle 增量编译脏状态：源码修改后 `assembleDebugAndroidTest` 被误判 up-to-date，设备跑了旧 APK（ dex 字面量核对发现）。已删除中间产物并以 `--rerun-tasks` 强制重建后全部相位在同一新 APK 上重跑；其后每次构建核对 compile 任务真实执行。
- 模拟器长时间多轮运行后出现片库入口偶发 flake（历史已知），冷重启（全新 AVD 实例）后消除；本报告全部最终结果取自冷重启后的实例。
- `Instrumentation.waitForIdleSync()` 在播放层切换期间会阻塞测试线程约 5 秒并可能触发系统 watchdog 注入 BACK（此前已记录）；本轮显式播放派发一律使用裸 `sendKeySync` 对。
- r12-browse 一次冷跑 flake（`requireNotNull(it.browseProbe)` 竞态）与一次无效人工重跑（误用其他探针组合 APK，30/30 假失败）——均以相位驱动正确配对重跑取代，最终 59/59。

## 截图清单（`/.local/evidence/round-15/shots/`，均真实 AVD 渲染）

- `round15-detail-1280x720-long.png`（720p 长标题，两行动作换行）
- `round15-detail-1920x1080-lines.png`（1080p 线路行焦点）
- `round15-detail-large-font-long.png`（1.3× 大字号长文案）
- `round15-detail-portrait-art.png`（竖幅海报）
- `round15-detail-missing-art.png`（缺图占位图标可见）
- `round15-detail-reduced-motion-back.png`（减动效返回片库）

## 独立评审

只读 fork 代理评审（评审范围：身份/可用性/动态剧集清单/嵌套返回/单写者/数据删除边界）结论与本报告合并记录于提交说明；发现项与处置逐条列出（见下方“评审记录”小节，由评审代理产出后回填）。

## 评审记录

独立只读评审（fork 代理，未修改文件、未运行构建，基线与本报告同源）：**无 Critical 发现**。身份诚实性（未知/冲突不填充、同名线路不合并、集数不伪造）、可用性语义（DISABLED/UNREACHABLE/STALE 不可派发且文字如实、派发时点再校验）、动态剧集清单（换线只取新线路自身清单、弹层与主清单同源）、嵌套返回（弹层先关一层再 `browse.back` 逐层）、单写者（详情层零 Room/DataStore 写入、三元组精确回查未绕过）、删除边界（`teardown` 只删自有一次性档案）均核验通过；抽查 8 行（G03/E05/E07/A01/A07/A08/N07/N10/L04/E06）与代码相符。

**Minor（2 项，均已处置）**
1. E03 证据表述强于实际覆盖（超长名线路无机器聚焦断言）→ 已补原生 `long_route_name_focusable_and_selectable_by_stable_id`（真实 D-pad 聚焦 + OK 激活 + 同名不合并 + 不开弹层），r15-detail 相位重跑 **37/37**。
2. `WorkDetailContextStore.load()` 复现 org.json null 怪癖（save 写 `JSONObject.NULL`、load 读回 `"null"` 字面量，与 `reproject` 归一化口径不一致）→ 已改为 `takeUnless { it.isBlank() || it == "null" }` 同一口径；r15-detail 37/37 + r15-process 两相位 2/2 重跑通过（今日行为本就无害：未知 id 被 `resolveSelection` 拒绝后回退默认选择）。

**Note（3 项，知悉/修正文档）**
3. G05 行原引路径不准确 → 已改为实际路径 `update/sources.json`（内容确为 `"sources": []`）。
4. 字块封面被 `TextCoverGuard` 隐藏时（uri 非空但判定为字块图）缺图图标不显示、海报区呈安静深色块——与浏览/首页既有行为一致，属有意设计，不改。
5. `WorkDetailContextStore.save` 为非原子写；进程死于写入中时 `load()` 的 `runCatching` 自愈回默认选择，可接受，不改。
