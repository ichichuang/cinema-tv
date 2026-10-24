# Round 10 严格多维与年份筛选验证

本轮只实现 T07 候选筛选切片，普通应用仍走原路径。最终 JVM 314/314、Python 62/62、Android native 145/145 实际通过；无失败、跳过、原生漏跑或重复计数。没有将完整 T07、M03、A12–A16、真实来源或 TV/UI/生产验收标为完成。独立终审与 index 安全检查的最终结果见本文交付段。

## 起点、附件与范围

复用既有隔离工作树和 `codex/contract-foundation`，初始干净，实际起点 `87303304947649a4a464c781b57feae8e01dbb04`。main 起点为 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`。用户聊天明确授权 Round10 实施、本地提交和专用模拟器验证；附件提供规格，未作为额外操作授权。没有新建工作树、reset、pull、merge 或覆盖既有实现。

ZIP SHA-256：`5fc05c6fecc14468718f824a56319f2dae1d4b2d558bf118981624a79a563fde`。归档路径检查与四项 SHA256SUMS 均通过。

| 原始包内文件 | SHA-256 |
| --- | --- |
| CODEX_INSTRUCTION.txt | 377c255ab36cbfac8df66ab38e63962c7004249ffcb8f0a500556be10bbf0189 |
| README.md | c56c2bb3fad4dcd0b20593a0e2cb7e64bcde8f336efe41a60d7262c4954acb78 |
| STRICT-FILTER-ACCEPTANCE-MATRIX.md | 72a3ab49dd438ad7fb152884a8e3f70c0d963c90b09af5fa8bdba7fdf9c032d8 |
| scoped plan | c6acf39614bc191d80fbc77f0dc8dd9a9b6f06459b6823503165ab25ff5e2122 |

原件在忽略目录 `.local/evidence/round-10/attachment/`。导入计划仅把个人绝对路径替为 `<isolated-worktree>`，保留完整要求，导入后 SHA-256 为 `d1fdda5949e2f773903d8839a18522ca83c3f24a0b71b7b648f1d015b71e4eb4`。没有修改安全检查规则。

## 实现与保留边界

实际接口映射及规则见 [结构说明](../architecture/tv-strict-filtering.md)。复用 `CatalogQuery`、不可变 `FieldEvidence`、已证 `groupWorkId`、Room 候选 capture 和既有有界 `QueryCoordinator`。跨维 AND、维内 OR，仅 MATCH 入选；缺失、空值、冲突和作品语言作用域不足保留原因与出处。配音和字幕不作为作品语言、制作国家或类别证明。精确补充映射仅为泰国→TH、犯罪→crime，使用独立规则版本，不改原 normalizer/capture 字节。

`YearSelection.Exact/Decade/Range` 使用正整数闭区间；1895、1962、1987、未来年份均可选，没有 2000 下限或当前年上限。零、负数、倒置、非整十起点、十年溢出及改变条件复用原 queryId 均明确拒绝。来源 `vod_year` 仍标注来源声明，更新时间和修复日期不成为原始发行年份。

查询在已证作品分组后本地重验每个维度，保留全部原观察与各播放入口。分页、独立游标、预算、超时、重复/循环停止、代次与事务取消守卫沿用既有实现。`verifiedObservedMatchCount` 只报告已观察匹配数；`knownScopedMatchCount` 仅在选定来源耗尽且所需事实/身份无缺口时有值；`globalTotal` 始终 null。排序使用年份、标题和 work key，未知年份两方向均置后，部分资料明确标记 OBSERVED_ONLY。中文候选状态与显式移除条件的审计结果尚未接入 UI。

完整选择存入已有 `candidate_query_sessions.payload`，复用 `CatalogQueryCodec`，旧无 fullQuery 的实际 payload 继续可读；存在但损坏的 fullQuery 拒绝，不退回宽条件。新严格事实纳入 projection digest。没有新表或 schema 变更，Room 仍为5；DB-02 的5→6迁移条件未触发。

## 测试命令与真实 RED

原始命令、退出码、时间、JUnit XML、native 逐方法事件、APK 与指纹均在 `.local/evidence/round-10/`。使用原有 mise Python、Temurin21 和 Android SDK，没有安装工具、升级依赖或改变全局版本。`run.py` 注入原 SDK 并串行 Gradle。

```sh
# J1：最终完整 JVM，314/314；final-jvm-matrix.log/json/-jvm-results
mise exec -- python3 .local/evidence/round-10/run.py final-jvm-matrix mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew -Pcinema.storageTestArm64=false -Pcinema.playbackRoomProbe=false :app:testDebugUnitTest --rerun-tasks --no-daemon --no-configuration-cache --console=plain
# B1：普通双门禁 OFF 构建、当时313项全 JVM、双 Lint；140任务实际执行
mise exec -- python3 .local/evidence/round-10/run.py final-ordinary mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew -Pcinema.storageTestArm64=false -Pcinema.playbackRoomProbe=false :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease --rerun-tasks --no-daemon --no-configuration-cache --console=plain
# P1：62项 Python，final-python.log/json
mise exec -- python3 -m unittest discover -s scripts/tests -v
```

B1 后只增加 FAC-03 的精确合成夹具测试，应用及 androidTest 源码没有变化；J1 完整重跑增至314项。FAC-11 和该 FAC-03 是覆盖补充，已有实现首次即通过，没有伪造产品 RED。

| 阶段 | 实际 tests / failures | 证据 label |
| --- | --- | --- |
| 基线 JVM / Python | 276/0；62/0 | baseline-jvm；baseline-python |
| R10-1 RED → focused/full GREEN | 2/2，5/4 → 5/0，281/0 | task-1-red；task-1-red-expanded；task-1-green；task-1-suite |
| R10-2 RED → focused/full GREEN | 1/1，6/6，9/3 → 14/0，290/0 | r10-2-red；r10-2-facets-red；r10-2-adapter-red；r10-2-focused-green；r10-2-full-green |
| R10-3 RED → focused/full GREEN | 1/1，7/7，10/10 → 24/0，300/0 | r10-3-red；r10-3-boundaries-red；r10-3-evidence-red；r10-3-green；r10-3-full-jvm |
| R10-4 RED → focused/full GREEN | 1/1，6/5，25/2，28/1 → 35/0，313/0 | r10-4-country-red；composition-red；relax-red；digest-red；focused-green；full-jvm（后五项同前缀） |
| Room RED → native GREEN | 1/1 → 新2项及既有102项共104/0 | r10-4-native-red/instrumentation.log；final-candidate/instrumentation.log |
| 最终 JVM / Python / 全 native | 314/0；62/0；145/0 | final-jvm-matrix；final-python；final-native-summary.json |

以上真实执行无 errors/skipped。Room RED 明确显示 MOVIE/NON_CHINESE/JP+TH/crime/YEAR_ASCENDING 关闭重开后变成 null/ALL/空集合/DEFAULT，修复后相同文件数据库路径通过。一次 `r10-4-composition-green` 在构建中并发编辑测试导致编译失败，未当作 RED/GREEN；其目录拷贝的旧 XML 不计入结果。native RED 首次构建与测试夹具编辑交叠，故重新冻结、构建 `r10-4-native-assertion-red-build` 后才运行并引用真实断言失败。没有隐藏这些尝试或削弱检查。

## Native 实际执行

N1 使用 `cinema.storageTestArm64=true`、probe OFF，仅允许的 assembleDebug/assembleDebugAndroidTest 构建，运行候选/存储104方法。N2 使用相同 ARM64 路线、probe ON 跑既有播放/交接；需要 OFF 的回归重新安装保存的 OFF APK 配对。全程 fixture 数据，设备侧每次安装、执行、force-stop、shutdown 前验证自有 AVD、host PID、boot ID、qemu=1、API30、唯一 arm64-v8a、空32位ABI及目标包/runner，全部 ADB 固定 serial。

```sh
# N1/N2 的实际 runner 模板；每次准确 class/method 列表在 native-commands.jsonl、handoff/native-commands.jsonl
adb -H 127.0.0.1 -P 5037 -s emulator-5574 shell am instrument -w -r -e cinema.disposable round06 -e cinema.handoff round07 -e class CLASS_OR_METHOD com.ichichuang.cinema.debug.test/androidx.test.runner.AndroidJUnitRunner
# 第二台同一命令固定 -s emulator-5576，绝不省略 serial
```

| 自有目标 | serial | boot ID | 收尾 |
| --- | --- | --- | --- |
| cinema-strict-filter-20261024-eb3aae98 | emulator-5574 | 60a7e2a6-79f1-45d3-b3da-41a1172d4dba | 正常emu kill，host PID48946已消失 |
| cinema-strict-handoff-20261024-84f52559 | emulator-5576 | 1071255e-dc67-4c2f-81e1-400b99c82d8c | 正常emu kill，host PID85979已消失 |

| 本轮实际方法分组 | 数量 | 原始日志 |
| --- | ---: | --- |
| 作品身份24、查询8、能力/迁移3、订阅7、Room15、旧数据导入19、检查点26、播放schema迁移2 | 104 | final-candidate/instrumentation.log |
| 播放运行7、独立进程2、普通OFF旧路径1 | 10 | final-runtime-on/instrumentation.log；final-process-a.log；final-process-b.log；final-runtime-off/instrumentation.log |
| 交接门禁4、事务17、取消1、媒体4、独立进程及OFF5 | 31 | handoff/final-handoff-on/instrumentation.log；final-handoff-interrupted/-resume、final-handoff-process-a/-b及final-handoff-off日志 |

`collect-native.py` 比较源码145个 @Test 与145个 start/terminal事件的多重集，failed/skipped/not_run/unexpected/duplicate 全为0。原Room1→5、作品拆分、订阅、播放、交接回归均为本轮重新执行。真实进程回放 PID2949→3055 恢复5448ms并暂停；交接预激活 PID2537→2634 仍为Legacy，激活 PID2705→2835 恢复较新Room7533ms并暂停，最后probe OFF不恢复旧writer。合成AVD数据/证据保留，共享ADB未重启。

## 35项验收矩阵逐项证据

J1 为上面的最终314项 JVM 命令，N1 为 final-candidate 的104项真实 native，N2 为上述播放/交接41项，B1为普通构建/静态产物核对。别名：Y=YearSelectorTest，F=StrictFacetTest，C=StrictCaptureFactsTest，Q=QueryCoordinatorTest，S=SourceCapabilityTest，R=StrictProjectionTest，N=QueryCoordinatorNativeTest，L=LegacyPlaybackRuntimeNativeTest。所有作品/来源/完整性字段都是命名合成 fixture，Y的来源年份与C/Q/N的字段通过实际 normalizer/capture 取得；手工 confirmed/source-reported 判定夹具保留 source/item/raw/rule 参考，不代表真实 CMS准确性。每行状态仅针对列出的合成断言；35项不等于35个独立测试方法。

| ID | 实际方法 | 层 / 命令 | 结果与字段依据 |
| --- | --- | --- | --- |
| YEAR-01 | Y.exact_year_is_strict | JVM/J1 | 通过；Confirmed1994支持精确匹配 |
| YEAR-02 | Y.exact_selection_sets_inclusive_bounds_and_excludes_foreign_year | JVM/J1 | 通过；1995排除 |
| YEAR-03 | Y.unavailable_year_is_indeterminate_and_keeps_actual_observation | JVM/J1 | 通过；缺失/null/空串/非法原文区分且均不入选 |
| YEAR-04 | Y.contradictory_years_stay_indeterminate_after_work_fact_combination | JVM/J1 | 通过；1994/1995 claims保留冲突 |
| YEAR-05 | Y.decade_has_literal_inclusive_endpoints | JVM/J1 | 通过；1980/1989入选，1979/1990排除 |
| YEAR-06 | Y.range_has_literal_inclusive_endpoints | JVM/J1 | 通过；1985/1995入选，1984/1996排除 |
| YEAR-07 | Y.positive_old_and_future_years_need_no_inventory | JVM/J1 | 通过；1895/1962/1987/未来值可选，不声称库存 |
| YEAR-08 | Y.invalid_bounds_have_typed_failures | JVM/J1 | 通过；非法边界类型化拒绝 |
| YEAR-09 | Y.source_declared_year_preserves_provenance_and_ignores_version_dates | JVM/J1 | 通过；vod_year来源声明与版本/更新日期分离 |
| FAC-01 | F.country_or_coproduction_and_conflict | JVM/J1 | 通过；JP或TH任一有据即匹配 |
| FAC-02 | F.genre_or_and_separate_content_kind；C.exact_extensions_preserve_versioned_references_without_broad_guesses | JVM/J1 | 通过；action/crime为OR，犯罪原文与映射版本保留 |
| FAC-03 | F.japan_or_thailand_horror_in_nineties_requires_every_selected_dimension | JVM/J1 | 通过；JP/TH＋horror＋1990..1999必须全部满足，各错维分别排除 |
| FAC-04 | F.country_or_coproduction_and_conflict | JVM/J1 | 通过；JP/US合拍成员支持JP |
| FAC-05 | C.exact_extensions_preserve_versioned_references_without_broad_guesses | JVM/J1 | 通过；欧美不变US，惊悚不变horror |
| FAC-06 | F.language_positive_and_scoped_negative_proof；C.explicit_work_fields_only_and_multilingual_facts_retained | JVM/J1 | 通过；zh/en作品事实保留并支持华语 |
| FAC-07 | F.language_positive_and_scoped_negative_proof；C.complete_scope_must_bind_to_actual_language_in_same_capture | JVM/J1 | 通过；完整en作品声明支持非华语，scope必须同capture绑定 |
| FAC-08 | C.explicit_work_fields_only_and_multilingual_facts_retained | JVM/J1 | 通过；国语音轨/中文字幕不改变作品语言或国家 |
| FAC-09 | F.empty_and_conflict_stay_indeterminate；F.language_positive_and_scoped_negative_proof | JVM/J1 | 通过；空、冲突、partial en均不自动归非华语 |
| FAC-10 | F.and_absorbs_mismatch_and_unselected_is_neutral | JVM/J1 | 通过；明确错国家吸收未知类型 |
| FAC-11 | F.or_unknown_alternative_and_mismatching_alternative_remain_indeterminate | JVM/J1 | 通过；JP/US冲突配JP/TH选项保持indeterminate |
| FAC-12 | F.and_absorbs_mismatch_and_unselected_is_neutral | JVM/J1 | 通过；未选维度中立 |
| QRY-01 | Q.strict_composition_rechecks_all_facets_and_sorts_scoped_rows；S.ignored_year_is_contradicted_while_raw_year_is_supplied | JVM/J1 | 通过；模拟上游忽略参数仍本地排除，不提升能力 |
| QRY-02 | Q.empty_accepted_page_is_partial_then_continues_and_retains_mismatch_and_unknown_evidence | JVM/J1 | 通过；首个严格空页保持Partial，后页续查匹配 |
| QRY-03 | Q.partial_failure_keeps_success_and_retry_cursor_without_guessing_remote_total；Q.timed_out_source_keeps_explicit_failed_coverage_and_retry_cursor | JVM/J1 | 通过；A保留、B失败和cursor保留、总量未知 |
| QRY-04 | Q.verified_groups_keep_home_and_playback_entries_and_same_title_never_merges；N.query08_strict_projection_coverage_identity_routes_and_raw_data_survive_reopen | JVM+native/J1+N1 | 通过；实际可信group计1并保留2入口 |
| QRY-05 | Q.verified_groups_keep_home_and_playback_entries_and_same_title_never_merges | JVM/J1 | 通过；同名同年无证明仍独立 |
| QRY-06 | Q.chronological_sort_uses_title_work_key_ties_and_unknowns_last_in_both_directions；Q.strict_composition_rechecks_all_facets_and_sorts_scoped_rows | JVM/J1 | 通过；稳定年/标题/key顺序、未知置后及观察范围标记 |
| QRY-07 | Q.strict_empty_exhaustion_distinguishes_known_empty_from_unknown_facts；Q.verified_group_conflict_excludes_whole_group_and_complete_unknown_total_stays_null | JVM/J1 | 通过；限定范围已知0与未知/冲突区别，不声称全局空库 |
| QRY-08 | Q.repeated_page_and_cursor_cycles_stop_partial_in_finite_requests；Q.byte_time_and_record_budgets_remain_partial_without_overbudget_commits | JVM/J1 | 通过；预算/重复/循环有限停止且保持Partial |
| QRY-09 | Q.strict_superseded_query_cannot_replace_new_selection_cursors_or_rows；Q.cancellation_propagates_and_noncooperative_late_q1_cannot_write_after_q2_starts | JVM/J1 | 通过；非协作Q1晚回复不能写Q2选择、行或游标 |
| REL-01 | Q.strict_empty_exhaustion_distinguishes_known_empty_from_unknown_facts；Q.partial_failure_keeps_success_and_retry_cursor_without_guessing_remote_total | JVM/J1 | 通过；空和失败均不改选择 |
| REL-02 | R.explicit_removal_changes_only_named_facet_and_records_chinese_audit；R.explicit_removal_rejects_absent_facet_instead_of_audit_without_selection_change | JVM/J1 | 通过；drop year只删年份、新ID、中文原因；未选facet拒绝 |
| DB-01 | N.query07_full_strict_selection_survives_real_room_close_reopen；N.query08_strict_projection_coverage_identity_routes_and_raw_data_survive_reopen | native/N1 | 通过；真实文件Room完整选择、覆盖、work、home、route、raw和合成收藏关闭重开保留 |
| DB-02 | 条件未触发 | schema静态核对 | N/A；复用v5 payload，无5→6迁移，不伪称执行 |
| OFF-01 | StrictFilterBoundaryTest.candidate_result_preserves_legacy_filter_and_persisted_preferences；L.probe_off_uses_original_datastore_with_existing_records_and_does_not_touch_room；原路径/产物指纹 | JVM+native+build/J1+N2+B1 | 通过限定检查；JVM仅真实Preferences哨兵，原DataStore路径由native另验，不能替代真实TV |

34项适用合成检查有执行证据；1项条件性迁移N/A，没有把N/A计为通过的测试方法。

## 产物、schema、安全与交付

普通 Debug/Release 的应用ID、0.9.8/code18、min24/target28、ARMv7 JNI内容和实际manifest与既有冻结基线一致；androidTest无JNI且runner/target不变。Release保持unsigned，apksigner verify预期exit1并报告缺META-INF/MANIFEST.MF；Debug/test使用既有Debug签名verify0。候选没有接入启动、DI、旧筛选、来源选择或writer authority；未修改旧normalizer、Room1–5、默认空sources或签名/发布配置。

双Lint均0Error，Debug21Warning/1Hint、Release22Warning/1Hint；id/severity/message多重集与本轮基线完全相同，新增0、删除0。既有Gradle/Kotlin弃用提示保留，没有禁用或削弱规则。产物、schema及精确文件清单由本文后续表和 `.local/evidence/round-10/final-compatibility-summary.json`、`final-preservation.json` 留存。

独立任务审查R10-1至R10-4以及全轮23路径终审均为0Critical/0Important/0Minor，没有待修复或延后项，因此没有需要第二次修复复审的发现。全轮审查另外核对既有normalizer、CatalogClaims、CatalogQuery、FieldEvidence及CatalogQueryCodec；报告在 `.local/evidence/round-10/final-review.md`。这是源码/测试设计复核，审查者没有独立重跑套件、构建、ADB或安全检查。审查未裁定的真实CMS、全局库存、UI/设备、生产/掉电、完整T07及不存在的5→6迁移均保留为本轮范围外，未据此扩张实现。

提交前metadata-only `git fetch --no-tags origin`成功；分支仍为 `codex/contract-foundation`、HEAD仍为核验起点，相对origin/main为ahead18/behind0。main与origin/main均为原基线且主树干净，无同步合并需要。无upstream是既定本地用途，用户明确禁止push，未创建远端分支。全部23个任务路径明确暂存，18个应用/测试文件的受测SHA-256与工作树和index blob相等；起点303个文件中仅7个本轮既有路径变化，其他296个逐字节相同。

暂存index安全检查覆盖319个文件、0项所检查规则违规，不扫描历史，不作全面无安全问题声明。首轮报告把work、home、route串成斜线短语，命中个人路径规则；只改为中文分隔并重跑通过，没有弱化检查。`git diff --cached --check`通过，未遗留暂存外修改。本轮采用一个完整验证后的中文本地提交，最终HEAD及commit blob核对结果在用户交付及忽略证据 `final-delivery.json` 中记录，避免本文自引用commit hash。

真实CMS语义/字段覆盖、全来源库存、API24/ARMv7硬件、TV/D-pad/UI、真实用户升级、掉电与生产切换仍未验证。未访问物理/个人设备、未扫描媒体目录、未添加生产信任根，未push/merge/publish、签名Release、修改发布资产或进入后续任务。停止于Round10。

### 原schema与普通产物指纹

| schema | SHA-256（起点与最终相同） |
| --- | --- |
| 1.json | 14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959 |
| 2.json | 6b5abe547c00a7a2d2cf2ab1e30e995347b3d9cd014c9700a639e5f87677aca9 |
| 3.json | bda6901970ecceb7c0c1adbe8aec0f0a665243cce9e61e95fcade2b0c3c98b95 |
| 4.json | 140609a645dba466cfd0f1d57059198e4d9527692f3c6402878be87704f808b6 |
| 5.json | 74d5c2d74324d9f58aeb54ff1c2678d23d3b01d89823e2e3a4f5a4b4d7d8ec2e |

| 普通产物 | SHA-256 |
| --- | --- |
| debug | 2424646fff4ad517045ab270c31f56678c772064b39b4f5c71ff21dbada26282 |
| androidTest | a1cfb71f8b89a6a4ebd90cdb71666f9080e46aed491d0593c7260932c5f6d101 |
| release | bbf1a4f5f4f1d7ac4c4c6cedebf5ba8f16df8a725e5fbc43dbb9c25f63e5388b |

### 本轮精确变更路径

```text
app/src/androidTest/kotlin/com/ichichuang/cinema/data/catalog/QueryCoordinatorNativeTest.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/QueryCoordinator.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/QuerySessionCodec.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/RoomCandidateQueryStore.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/SourceCursorState.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/StrictCaptureFacts.kt
app/src/main/kotlin/com/ichichuang/cinema/data/catalog/StrictQueryProjection.kt
app/src/main/kotlin/com/ichichuang/cinema/domain/filter/StrictFilterEvaluator.kt
app/src/main/kotlin/com/ichichuang/cinema/domain/filter/StrictWorkFacts.kt
app/src/main/kotlin/com/ichichuang/cinema/domain/filter/YearFacetEvaluator.kt
app/src/main/kotlin/com/ichichuang/cinema/domain/filter/YearSelection.kt
app/src/test/kotlin/com/ichichuang/cinema/data/catalog/QueryCoordinatorTest.kt
app/src/test/kotlin/com/ichichuang/cinema/data/catalog/StrictCaptureFactsTest.kt
app/src/test/kotlin/com/ichichuang/cinema/data/catalog/StrictProjectionTest.kt
app/src/test/kotlin/com/ichichuang/cinema/data/catalog/StrictQueryCodecTest.kt
app/src/test/kotlin/com/ichichuang/cinema/domain/filter/StrictFacetTest.kt
app/src/test/kotlin/com/ichichuang/cinema/domain/filter/StrictFilterBoundaryTest.kt
app/src/test/kotlin/com/ichichuang/cinema/domain/filter/YearSelectorTest.kt
docs/architecture/tv-strict-filtering.md
docs/superpowers/plans/2026-10-24-round-10-strict-multifacet-year-filtering.md
docs/superpowers/progress/2026-10-23-tv-rebuild.md
docs/verification/2026-10-24-round-10-strict-filtering.md
docs/verification/STRICT-FILTER-ACCEPTANCE-MATRIX.md
```
