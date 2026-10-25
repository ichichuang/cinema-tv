# Round 14 原生片库与严格筛选界面验证报告

## 结论与边界

本轮在既有授权测试路径内交付 T10 片库/年份优先严格筛选 UI 有界切片：新增 JVM 31 项
（草稿 10 + 投影 9 + 查询桥接 9 + 恢复协调器实证 3）与原生 30 项（筛选 UI 11 +
导航 8 + 恢复 2 + 截图 6 + 两阶段进程 2 + 普通 OFF 1）全部通过；既有 R12/R13 原生类按
配对分组回归全部通过（见下表）；Python 62/62；普通全探针 OFF 构建、双 lint 与暂存区
安全检查通过；Room schema 1–6 逐字节不变；**完整 T02/T03–T11/M04、生产激活、物理电视
与发布验收保持开放。**

未推送、合并、rebase、发布、签名、访问物理设备或真实 CMS；普通 Debug／Release 保持
旧导航、唯一 DataStore 写入者、包身份 `0.9.8`/code `18`、`armeabi-v7a` ABI 与签名冻结。

## 附件、起点与源代码

- 起始隔离分支 `codex/contract-foundation`，HEAD `865401b720a04e6de89fa1d667e58ce98ea5b61f`
  （与报告前缀 `865401b` 一致）；导入前两个工作树均干净；`main`/`origin/main` 均为
  `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`。
- ZIP `cinema-tv-codex-round-14.zip` SHA-256 `1d339b117ac5f5bb1047a8d45c2e7947ccead1030ba646653f1f3ea2db6d2a8a`；
  `shasum -a 256 -c SHA256SUMS` 五项全部 OK；Downloads 三份文档副本与包内逐字节一致。
- 原件与 manifest 保存在忽略的 `.local/evidence/round-14/attachment/`；导入脱敏计划副本
  `docs/superpowers/plans/2026-10-25-round-14-library-strict-filter-ui.md` 与原样矩阵
  `docs/verification/LIBRARY-FILTER-ACCEPTANCE-MATRIX.md`。

## 实现与数据权威

- 新增编译期探针 `cinema.libraryFilterProbe`（BuildConfig `LIBRARY_FILTER_PROBE`，默认
  false、Release 恒 false），链式要求全部五个既有探针 + 精确任务对；Release／bundle／
  发布／签名／安装一律 GradleException（`gate-check-results.json` 5/5 拒绝）。
- 运行期授权令牌 `round14-library-filter.authorization` 缺令牌在数据库打开前失败关闭；
  授权链与单写者租约全程复用。
- 片库数据经既有 R09 `QueryCoordinator`（预算/游标/代次/取消/部分覆盖）+ R10 严格评估；
  合成 fixture 作品按页渲染为真实 AppleCMS 行（`MaccmsCatalogNormalizer` 归一 +
  `StrictCaptureFacts` 事实化），服务端不过滤、匹配仅由本地严格判定；内存候选存储
  不新增 Room 写入者；用户/浏览持久化权威仍是 Room v6 既有表（browse_snapshots v5
  结构复用，上下文键 `library-ui:<queryId>` + 私有指针，无 schema 变更）。
- 查询身份只在用户真实意图下更换（`片库查询-N`）；无变化确认沿用原身份；删片/重置
  逐字保留其余字段；无效年份草稿中文拒绝且已应用查询不动。
- 焦点/恢复复用 R11 令牌管线；修复三类“隔次吞确认键”（UP 成对清理、面板开关、
  子层进入）与新查询安装不落盘（`begin` 只挂 pending 不起定时器）等真实缺陷。

## 执行环境、命令与结果

- 唯一最终设备：`emulator-5586`，API30、纯 ARM64（abilist32 为空）、ranchu，自有一次性
  AVD `cinema-library-20261025-a77299c8`，host PID 以 `.local/evidence/round-14/native-target.json`
  为准，独立 ADB 服务端口 5041；共享 ADB 5037 未触碰，未访问物理设备。
- 本地 SDK：`<main-checkout>/.local/toolchain/android-sdk`；Java：mise `21.0.2`。
- 依赖、AGP/KSP/Room/Compose/Media3 版本未升级；无新增运行时依赖。

| 层次 | 实际命令／证据（忽略目录 `.local/evidence/round-14/`） | 结果 |
| --- | --- | --- |
| R14 目标基线 | `:app:testDebugUnitTest --tests ...filter/navigation/catalog/HomeProjection...`（起始 HEAD） | 158/158 |
| R14 JVM | `:app:testDebugUnitTest`（全量，普通 OFF 配对，`--rerun-tasks`） | 401/401（370 基线 + 31 新增），0 跳过 |
| R14 原生 UI/导航/恢复/截图 | `run-native-phases.py r14-library`（六探针配对，四类一次会话；复审修复后复跑） | 27/27 |
| 两阶段进程实验 | `run-native-phases.py r14-process`（阶段 A → `am force-stop` → 阶段 B；PID 9676→9773） | 2/2（A 1/1、B 1/1） |
| R13 回归 | `run-native-phases.py r13-home`（五探针配对，library 关闭=占位路径不变） | 29/29 |
| R12 回归（浏览配对） | `run-native-phases.py r12-browse`（59 通过 + 5 个与 R13 基线一致的既有 assumption 跳过） | 59/59 |
| R12 回归（个人库配对） | `run-native-phases.py r12-personal` | 121/121 |
| R12 回归（播放/交接配对） | `run-native-phases.py r12-playback` | 34/34 |
| 普通路径原生 | `run-native-phases.py ordinary-off`（仅 storageTestArm64；含新增 LibraryOff 1 类） | 28/28 |
| 原生总对账 | 上述全部唯一方法合计 | 逐相位 result JSON 一一对应 |
| 普通 OFF 全回归 | `ordinary-regression.py`（`--rerun-tasks --no-daemon --no-configuration-cache`） | 见 `ordinary-regression.json` |
| 门禁核验 | `gate-check.py`（探针专属拒绝消息逐案例核对） | 5/5 拒绝 |
| Python | `python3 -m unittest discover -s scripts/tests -v` | 62/62 |
| 受测源哈希 | `final-tested-source-hashes.py`（27 个源文件 磁盘=HEAD blob） | 27/27 |
| 暂存区安全 | `python3 scripts/check_repository_security.py`（全部变更暂存后；index-scoped，未扫历史） | 401 tracked files；0 policy violations |
| schema | `git show HEAD:<n>.json` 逐字节比对 | 1–6 不变 |

### 配对说明

R12/R13 既有类按其授权前提分组执行（与 R13 惯例一致）：R14 六探针配对仅运行 R14 新类；
R13 五探针配对下片库探针关闭，“片库”导航逐字保持 R13 浏览占位路径；R11 浏览类三探针
配对；个人库类四探针配对；LegacyHandoff*/PlaybackRuntime 类 storage+playback 配对逐类
整包重装隔离；普通路径类仅开 storageTestArm64。混跑会产生跨类状态污染，已按惯例分组。

## 独立审查、RED 与失败分类

- RED 证据：JVM 首跑 27 项中 4 失败（含 NONE 年份模式 `toQuery` 误抛的实现真缺陷）；
  原生筛选 UI 类首跑 11/11 失败起迭代，先后暴露并修复真实缺陷：
  ① 网格焦点解析漏 `focused` 导致 UP 无效；② 首页 UP 处理在片库分支后导致 held 集不
  成对清理（隔次吞确认键）；③ 详情返回回焦目标漏记真实网格焦点；④ 子层关闭收集器
  只认首页记账；⑤ 新查询安装不启动防抖落盘；⑥ 同进程仪器化下 am kill/force-stop 连
  仪器化一起终止（改为两阶段宿主编排）；另纠正三处测试期望数错误（88/87/90→实测）。
  全部先失败后修复为 GREEN（各迭代 `-result.json` 保留）。
- 截图视觉复查：1280×720 首查发现条件片行溢出（4 来源片只见 3），改 `FlowRow` 换行
  后复查通过；1920×1080 四片一行全可见、状态行+继续核对+5 列网格+金色焦点环完整。
- 独立复审：无 Important；3 项 Minor 已全部处置（2 修复 1 记录为有意行为），6 项 Nit
  中 3 修复 3 保留并记录；修复后跟进复审确认全部成立、无新问题、原“未发现”结论仍持
  （N09 邻居档证据为 JVM 协调器级、原生用例为较弱断言，如实陈述）。

## 矩阵逐项对账（52 行）

N=六探针配对真实原生方法；J=JVM 断言；P=storage+playback 配对；X=仅 storage 配对
（普通路径）；H=主机实际检查；SHOT=真实 AVD 截图+复查；PROC=宿主编排进程实验。

| ID | 方法／检查 | 证据 | 状态 |
| --- | --- | --- | --- |
| G01 | `LibraryOffNativeTest.ordinary_debug_keeps_legacy_library_and_never_surfaces_guarded_library` | X | PASS |
| G02 | Release 无探针 BuildConfig + 门禁拒绝 + 无符号产物（`gate-check.py` 5 案例 + `ordinary-regression.json`） | H/BUILD | PASS |
| G03 | `authorized_library_opens_with_filter_entry_and_honest_partial_status` | N | PASS |
| G04 | 运行期令牌缺失失败关闭（`LibraryAuthorization.requireOwned` + 既有授权链；R13 同构方法回归） | N/H | PASS |
| G05 | `LibrarySnapshotStore` 只写自身上下文键+私有指针；单写者租约复用（架构说明+代码审查） | N/SEC | PASS |
| G06 | 默认来源目录为空、无虚构内容（fixture 全部合成且仅测试域；`ordinary-off` 无候选 UI） | N/SHOT/SEC | PASS |
| F01 | `exact_1994_excludes_known_1995`（原生）+ JVM `exact_1994_excludes_known_1995_and_unknown_year` | J/N | PASS |
| F02 | `dpad_reaches_all_panel_choices_including_pre2000_years`（1980–1989 闭区间、1979/1990 排除由 JVM `decade_1980_1989_inclusive_without_2000_floor`） | J/N | PASS |
| F03 | JVM `custom_range_1977_2006_inclusive_endpoints` | J | PASS |
| F04 | `inverted_range_rejected_in_chinese_and_applied_query_untouched` | J/N | PASS |
| F05 | JVM `filters_combine_and_across_dimensions_or_within`（跨维与、同维或、未知/冲突不混入） | J | PASS |
| F06 | `room_close_reopen_preserves_full_committed_query`（真实 Room v6 关闭重开全字段往返） | N | PASS |
| F07 | `language_scope_honors_work_language_not_dub_or_subtitle`（国语配音/中文字幕行不迁移作品语言） | J/N | PASS |
| F08 | JVM `language_scope...`（不完整/未知语言不能命中华语/非华语） | J/N | PASS |
| F09 | JVM `unknown_country_row_not_match_and_no_fabricated_facet` + 桥接冲突年份用例 | J | PASS |
| F10 | 面板选中/焦点双视觉（`TvFilterChip` 语义 Selected 与 Focused 分立；截图复查） | N/SHOT | PASS |
| F11 | `cancel_after_editing_year_and_language_keeps_query_and_opener` | N | PASS |
| F12 | `same_apply_is_no_op_keeps_query_id_and_items` | J/N | PASS |
| F13 | `remove_chip_creates_new_query_and_preserves_other_fields` | J/N | PASS |
| F14 | `dpad_reaches_all_panel_choices_including_pre2000_years`（全选择 D-pad 可达，无键盘/菜单/长按依赖） | N | PASS |
| Q01 | JVM `server_ignored_year_still_verified_locally`（服务端页不过滤，仅本地严格匹配） | J | PASS |
| Q02 | JVM `zero_match_first_page_keeps_has_more_then_later_page_matches` + 原生 华语 零匹配保留继续核对 | J/N | PASS |
| Q03 | 独立来源游标后页匹配（JVM 同用例第二页 1994 匹配；原生默认查询 105 项跨 5 页核对） | J/N | PASS |
| Q04 | `authorized_library_opens...`（1 失败来源+存活结果同时呈现）+ 截图 `library-partial-failed` | J/N/SHOT | PASS |
| Q05 | JVM `partial_unknown_total_never_renders_global_count` + `complete_known_total_is_scoped_not_global` | J | PASS |
| Q06 | JVM `budget_exhausted_stays_incomplete_with_retryable_continue` | J | PASS |
| Q07 | JVM `offline_with_saved_rows_still_browseable_without_playable_claim`（离线行可看、不称可播） | J/N | PASS |
| Q08 | `unconfigured_loading_failed_offline_partial_complete_empty_distinct_states`（六状态中文互异、无死焦点） | J/N/SHOT | PASS |
| Q09 | `stale_generation_ignores_late_first_page_of_previous_query`（挂起旧首页→新查询→放行被代次拒绝） | J/N | PASS |
| Q10 | JVM `identity_dedup_retains_both_source_entries`（镜像来源两证据保留、展示去重） | J | PASS |
| N01 | `home_to_library_and_back_returns_home_origin` | N | PASS |
| N02 | `deep_list_detail_back_restores_work_and_anchor`（100+ 合成作品索引 87 深处） | N | PASS |
| N03 | `panel_traps_focus_and_back_closes_only_panel` | N | PASS |
| N04 | 同上（BACK 后焦点回“筛选”打开者）+ `cancel_after_editing...` | N | PASS |
| N05 | `apply_new_query_clears_only_invalidated_anchor` | N | PASS |
| N06 | `library_favorites_history_contexts_stay_independent` | N | PASS |
| N07 | 陈旧意图不回退（R11 令牌管线 + `inputEpoch`；`stale_generation...` 同机制） | J/N | PASS |
| N08 | `one_ok_cycle_one_apply_and_long_ok_no_short_action`（含 repeatCount>0 不先执行短按） | N | PASS |
| N09 | `missing_anchor_falls_back_to_adjacent_valid_item`（原生：合法回退无死焦点）+ JVM `LibraryRestoreFeedTest` 三项（协调器级：部分覆盖等待/覆盖完成回退原行最近存活邻居/行全灭回退页面默认） | N/J | PASS |
| N10 | `activity_recreation_restores_committed_query_and_discards_draft` + `uncommitted_draft_discarded_across_recreate` | N | PASS |
| N11 | 两阶段进程实验（阶段 A 提交→`am force-stop`→阶段 B 新 PID 恢复 1994 查询、仅浏览无观看记录；PID 见 `process-experiment-pids.txt`） | PROC/N | PASS |
| V01 | `library_1280x720_real_render` + 视觉复查（FlowRow 修复后条件片全部可见） | SHOT/N | PASS |
| V02 | `library_1920x1080_real_render` + 视觉复查 | SHOT/N | PASS |
| V03 | `library_large_font_and_long_title`（1.3 字号 + 长标题索引 87） | SHOT/N | PASS |
| V04 | `library_decade_1980s_with_portrait_and_missing_art`（2000 年前年代 + 缺图占位真实标题） | SHOT/N | PASS |
| V05 | `library_reduced_motion_keeps_focus_feedback`（动画缩放 0 下唯一焦点仍可辨） | N/SHOT | PASS |
| V06 | `chinese_labels_no_brand_and_no_fake_counts`（全中文、无品牌/评分/调试/全量计数） | N/SHOT/SEC | PASS |
| V07 | 复用 R13 图标/令牌权威，本轮零新增图标（`TvOriginalIcons` 未改；门禁+代码审查） | SEC/BUILD | PASS |
| R01 | schema 1–6 逐字节比对（`baseline-schema-hashes.txt` vs 终态） | H | PASS |
| R02 | `ordinary-regression.json`：Debug/Release 均 `armeabi-v7a`、`com.ichichuang.cinema` 0.9.8/code 18、Release 无签名、`update/sources.json` 未改动 | BUILD | PASS |
| R03 | JVM/原生/Python 全量 + 双 lint 消息差异（`ordinary-regression.json`）+ off-path 回归 | JVM/NATIVE/BUILD | PASS |
| R04 | 独立复审（下节）+ 受测源哈希=提交 blob + 暂存区安全检查 | SEC/BUILD | PASS |

## 独立复审

只读复审代理对 `865401b..HEAD` 全部变更按五个高风险面逐条核对（file:line 级）：

- **Important：未发现。** 单写者、严格匹配不放宽、覆盖不虚构总数、配音/字幕不迁移
  作品语言、探针关闭时 R13 路径逐字等价、Release 无探针、无品牌/假计数逐条成立；
  D-pad 焦点/层捕获“未发现残留吞键路径”（UP 成对清理双 held 集、域切换清集、
  面板背景全部不可聚焦、BACK 只关面板）；旧路径安全（租约顺序无数据库/写者泄漏、
  指针键与浏览键互不冲突、换代取消无陈旧写入路径，并有 stale_generation 原生实证）。
- **Minor（3 项，全部处置）**：
  1. N09 相邻合法项中间层缺失（`observation()` 的 `savedRowOrder` 只含存活项，锚点
     被移除时邻居搜索退化为页面默认）→ 已修复：保存序改为全量已知作品序（与 R11
     浏览的 `fixture.savedOrder` 同构），原生用例补充相邻回退断言。
  2. 半有界年份查询（仅一端有界，`CatalogQuery` 合法但当前 UI 不可达）在草稿中会被
     捏造默认值收紧 → 已修复：`begin()` 显式要求年份双有界或双无界（fail-fast），
     未来需要半有界 UI 时必须显式扩展模型。
  3. 新查询安装瞬间列表先清空为装载骨架（呈现选择）→ 保留：方向正确（陈旧结果不得
     重绘），状态如实，记录为有意行为。
- **Nit（6 项）**：死代码 `LibraryFilterLabels.sorts` 已删除；`applyUserQuery` 滞留
  `focusedControl` 已清理；步进器可访问描述补当前值；其余三项（ConnectivityManager
  缺失时非局部 return、viewportChanged 与 observation 修订号短暂不一致的一次多余等待、
  零内容来源的“未配置”措辞）以不可达或无害为由保留并记录。

## 视觉复查

- 1280×720 首查发现条件片行溢出（4 来源片只见 3、第三片换行），改 `FlowRow` 后全部
  可见并复查通过；1920×1080 四片一行全可见。
- 大字/长标题：1.3 字号下长标题卡片与筛选入口完整可达；减动效下唯一焦点语义仍可辨。
- 深色占位卡延续 R13 既有占位语言（面板底+详情图标+真实标题），未虚构任何事实。

## 保留的验收边界与停止点

- 未交付：完整 T10 详情/播放器/设置全量重设计、T11 全局遥控与动效、完整 T02 图标
  盘点、诊断删除、真实 CMS/海报 URL、API24/ARMv7 物理电视、真实用户升级、性能与
  发布/签名门。
- 测试图与媒体全部带“合成测试”水印或纯文本占位，仅存在于测试代码与私有 fixture；
  未进入生产目录或默认来源。
- 完整 T02/T03–T11/M04 与 A01/A03/A04/A12–A15/A19/A22 保持开放直至各自验收门通过。
- 本轮停止于 Round 14；未进入 Round 15。仅本地提交，未推送、合并、发布或签名。
