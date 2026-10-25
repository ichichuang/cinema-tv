# Round 13 原生电视视觉系统与功能化首页验证报告

## 结论与边界

本轮在既有授权测试路径内交付 T02 第一组原创视觉切片与 T10 功能化首页切片：24 项新原生 UI 方法
（`VisualHomeUiNativeTest`）全部通过，5 项真实 AVD 截图方法（`VisualHomeScreenshotTest`）全部通过，
新增 JVM 19 项（图标 4 + 投影 15）全部通过，合计 370/370；
既有 247 项原生方法按配对分组逐类回归全部通过（见下表）；Python 62/62；普通全探针 OFF 构建、
双 lint 与暂存区安全检查通过；Room schema 1–6 逐字节不变；R12-M1 回执丢失 UI 用例以真实重试
关闭并顺带修复“重试反向切换”缺陷。**完整 T02/T10/T11/M04、生产激活、物理电视与发布验收保持开放。**

未推送、合并、rebase、发布、签名、访问物理设备或真实 CMS；普通 Debug／Release 保持旧导航、
唯一 DataStore 写入者、包身份 `0.9.8`/code `18`、`armeabi-v7a` ABI 与签名冻结。

## 附件、起点与源代码

- 起始隔离分支 `codex/contract-foundation`，HEAD `4a35d35e555b184bff93a1e68e4aa0cbb94bf7c3`；导入前两个工作树均干净；`main`/`origin/main` 均为 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`，与报告一致。
- ZIP `cinema-tv-codex-round-13.zip` SHA-256 `5738f2ec3e78a0c02afdf31ff7c51fe85945231f8e56c5c6b7b8c8b6eb8e857b`；`shasum -c SHA256SUMS` 五项全部 OK；包内三份文档与另行提供的 Downloads 副本逐字节相同。
- 原件与 manifest 保存在忽略的 `.local/evidence/round-13/attachment/`；仅导入脱敏计划副本
  `docs/superpowers/plans/2026-10-25-round-13-visual-home-native.md`（个人绝对路径已替换，原件与安全规则未改）。

| 包内原件 | SHA-256 |
| --- | --- |
| `README.md` | 见忽略目录 `attachment/SHA256SUMS` 校验记录 |
| `CODEX_INSTRUCTION.txt` | 同上 |
| `VISUAL-HOME-ACCEPTANCE-MATRIX.md` | 同上 |
| `docs/superpowers/plans/2026-10-25-round-13-visual-system-home-native.md` | 同上 |
| `references/no-brand-visual-direction.png` | 同上 |

## 实现与数据权威

- 新增编译期探针 `cinema.homeVisualProbe`（BuildConfig `HOME_VISUAL_PROBE`，默认 false、Release 恒 false），
  链式要求全部四个既有探针 + 精确 `:app:assembleDebug`+`:app:assembleDebugAndroidTest` 任务对，
  Release/bundle/publish/install 一律 GradleException（G06，`gate-check-results.json` 5/5）。
- 运行期授权令牌 `round13-home-visual.authorization`（noBackupFilesDir）缺令牌在数据库打开前失败关闭（G04）；
  授权链与单写者租约 `LegacyHandoffRuntime.writers.exclusive` 全程复用（G05 沿用 R12 既有方法验证）。
- 视觉系统：单一 token 权威（`core/design/Color.kt`/`Motion.kt`/`TvMetrics.kt`/`core/focus/TvFocus.kt`），
  未新增并列常量；10 枚原创图标（24 单位网格、fill=none、stroke 宽 2、圆头）以
  `design/icons/tv-*.svg` 可编辑母版与 `TvOriginalIcons.kt`（`TvIconPaths` 同一份数据）双载体呈现，
  JVM `TvOriginalIconsTest` 四项结构核对全部通过（无位图/外部 href/外部样式/多路径）。
- 可复用组件 `TvNavTile`/`TvPrimaryButton`/`TvPosterTile`/`TvStatusMessage`/`TvLoadingTile`（`core/ui/`）：
  选中与焦点视觉独立（金色下划线 vs tvFocus 描边），禁用不可聚焦，竖图进横容器一律 Fit 收纳，
  缺图占位保留真实标题，图片完成回调不请求焦点。
- 首页投影 `HomeProjection`（纯函数）+ `VisualHomeRuntime`（只读，共享浏览运行时的个人库实例与目录
  fixture，无第二写入者）；持久化仅写 `home:<queryId>` 上下文键（browse_snapshots 表既有结构，
  无 schema 变更），浏览活动指针仍由浏览独占；焦点/恢复复用 R11 令牌管线独立实例。
- R12-M1 关闭：`PersonalLibraryTestHooks.lostReceiptAcknowledgements` 一次性注入“仓储已提交、UI 回执丢弃”，
  并修复由此暴露的缺陷 —— 回执丢失后待定意图被清除会导致重试读到已提交状态而**反向切换**；
  现在待定意图延续，重试按幂等目标值收敛（`ack_lost_after_commit_ui_retry_has_true_state`）。

| schema | 与起点比对 |
| --- | --- |
| `1.json`–`6.json` | 全部逐字节不变（SHA-256 见 `.local/evidence/round-13/` 核对输出；R01 通过） |

## 执行环境、命令与结果

- 唯一最终设备：`emulator-5586`，API30、纯 ARM64（abilist32 为空）、ranchu，自有一次性 AVD
  `cinema-home-20261025-8a412171`，host PID 以 `.local/evidence/round-13/native-target.json` 为准，
  独立 ADB 服务端口 5041；共享 ADB 5037 未触碰，未访问物理设备。
- 本地 SDK：`<main-checkout>/.local/toolchain/android-sdk`；Java：mise `21.0.2`（JAVA_HOME 显式导出）。
- 依赖、AGP/KSP/Room/Compose/Media3 版本未升级；无新增运行时依赖。

| 层次 | 实际命令／证据（忽略目录 `.local/evidence/round-13/`） | 结果 |
| --- | --- | --- |
| R13 原生 UI | `am instrument …-e cinema.home round13 -e class VisualHomeUiNativeTest`（五授权全开配对） | 24/24 |
| R13 真实截图 | 同上，`VisualHomeScreenshotTest`（1280x720/1920x1080/大字/未配置/减动效） | 5/5；PNG 存 `shots/` |
| R12 回归（浏览配对） | `run-native-phases.py r12-browse`（storage+playback+browse 三探针配对，逐类隔离） | BrowseNavigation 29/29；BrowsePersistence 30/30 |
| R12 回归（个人库配对） | `run-native-phases.py r12-personal`（四探针配对，逐类隔离 10 类） | 121/121 |
| R12 回归（播放/交接配对） | `run-native-phases.py r12-playback`（storage+playback 配对，逐类隔离 5 类） | 34/34 |
| 多进程编排 | `run-process-phases.py`（复刻 R12 序列：逐方法 + force-stop + phase_c 保数据换 OFF 配对） | 7/7（replay-a/b、handoff-interrupted/resume/a/b/c） |
| 普通路径原生 | `run-native-phases.py ordinary-off`（仅 storageTestArm64，逐类隔离 4 类） | 27/27（含 G01 的 VisualHomeOff 与既有 BrowseProbeOff/LegacyPlayback/WorkIdentity） |
| 原生总对账 | 上述全部唯一方法合计 | **277 启动／277 通过／0 失败／0 跳过**（R12 基线 247 + R13 新增 24 + 截图 5 + OFF 1；stdout 标记码与跳过已区分核对） |
| 普通 OFF 全回归 | `ordinary-regression.py`（`--rerun-tasks --no-daemon --no-configuration-cache`；`ordinary-regression.json`） | JVM **370/370**（R12 基线 351 + 新增 19）；Debug/unsigned Release/DebugAndroidTest 构建通过；lint Debug 22 条、Release 23 条消息与 R12 基线**逐条零增减**，0 Error/Fatal |
| 门禁核验 | `gate-check.py`（探针专属拒绝消息逐案例核对） | 5/5 拒绝 |
| Python | `python3 -m unittest discover -s scripts/tests -v` | 62/62 |
| 暂存区安全 | `python3 scripts/check_repository_security.py`（全部 38 项变更暂存后） | Checked 379 tracked files; 0 policy violations |
| schema | `git show HEAD:<n>.json` 逐字节比对 | 1–6 不变 |

### 配对说明（G06 的运行时侧面）

R12 既有类按其授权前提分组执行：R11 浏览类需三探针配对（storage+playback+browse，其夹具不写
R12 令牌，四探针配对下运行期授权会按规则失败关闭）；个人库类需四探针配对；LegacyHandoff*/
PlaybackRuntime 类需 storage+playback 配对（浏览探针开启时旧记录写入被既有规则禁止，且交接类会
写入旧 DataStore 记录，须逐类整包重装隔离）；多进程编排类按 R12 宿主序列逐方法执行并在
phase_c 以保数据 `-r` 安装切换到探针 OFF 配对；普通路径类仅开 storageTestArm64。全部类在同一
配对或同一会话混跑会产生跨类状态污染（先后出现 48 与 45 项假阴性），已按 R12 惯例改为分组隔离；
`r12-classes-on-r13-build-incompatible.log`、`stale-*` 与各相位早期失败日志保留了误配对的真实记录。

## 独立审查、RED 与失败分类

- RED 证据：新 UI 类首跑 24 中 10 失败（`visual-home-ui-red-*.log`），迭代 2–7 轮共暴露并修复
  真实缺陷：① 首页在子层（详情/播放器）打开时吞按键导致浏览确认键去重集卡死与 Activity 被销毁；
  ② 浏览根返回被“位置保存成功”门槛困死（保存失败即无法返回首页）；③ 激活使用陈旧待落焦意图
  导致目的地错选；④ 首行宽图在 1280 逻辑宽下裁切动作按钮（L01 红线，截图复查发现）；
  ⑤ R12-M1 重试反向切换（上节）。全部先失败后修复为 GREEN（`visual-home-ui-green.log`、29/29 复跑）。
- 独立复审（只读代理，逐条核对十条硬性要求并给出 file:line）确认：普通路径不变、门控链安全、
  无新增数据写入者、真话状态优先级正确、回执注入默认零行为、SVG 与 Kotlin 路径数据逐字节一致、
  全中文语义与合成素材仅测试域。复审另发现并已全部修复：
  - **Important（已修）**：让位浏览的确认键 DOWN 曾进入首页 held 去重集而 UP 让位不清除，
    会吞掉浏览根每隔一次的 OK —— 现让位键完全不进入 held 集；
  - **Important（已修）**：完整空目录时 `contentRows` 会生成空“片库”焦点区，DOWN 计算越界崩溃
    —— 现目录行仅在有真实卡片时出现，空态由“完整空”文案表达；
  - **Minor（已修）**：浏览根有弹层时返回应由浏览先关弹层 —— 现弹层打开时返回让位给浏览；
  - **Minor（已修）**：网络回调在 ConnectivityThread 写状态 —— 现回调绑定主线程 Handler；
  - **Nit（已修）**：状态条重复 testTag —— 现按调用点传唯一 tag。其余 Nit（progress 文案解析、
    HomeImageShape 分层、@After 还原固定值）以降级安全或仅限一次性模拟器为由保留并记录。
- 双 lint 另拦截两处真实问题并已修复（构建失败即证据）：`NewApi` —— 带 Handler 的
  `registerDefaultNetworkCallback` 需 API26 而 minSdk 24，改为版本分支 + 回调内跳主线程；
  `RememberInComposition` —— 设置页在组合内直接构造 FocusRequester，改为 remember 包装。
- 失败分类：配对/顺序错误的假阴性（混跑 48 项、遗留旧记录 45 项）均已按 R12 惯例改为
  按授权配对逐类隔离重装执行，不作为通过依据，原始失败日志保留。

## 矩阵逐项对账

N=五授权配对真实原生方法；P=storage+playback 配对；X=仅 storage 配对（普通路径）；H=主机实际检查；
SHOT=真实 AVD 截图+复查。全部 54 行均实际执行；无预填。

| ID | 方法／检查 | 证据 | 状态 |
| --- | --- | --- | --- |
| G01 | `VisualHomeOffNativeTest.ordinary_debug_uses_legacy_home_and_never_surfaces_candidate_home` | X | PASS |
| G02 | Release 无探针 BuildConfig + 门禁拒绝 + `assembleRelease` 无符号产物 | H/BUILD | PASS |
| G03 | `authorized_profile_opens_home_root_with_five_destinations` | N | PASS |
| G04 | `home_probe_requires_owned_profile_token` | N | PASS |
| G05 | 既有 `personal_probe_rejects_legacy_conflict`（R12 方法，本轮回归重跑） | N | PASS |
| G06 | `gate-check.py` 5 案例 + 配对说明 | H | PASS |
| V01–V10 | `TvOriginalIconsTest`×4 + 各图标在导航/按钮/状态条真实渲染（`VisualHomeUiNativeTest` 各用例的图标语义） | JVM/NATIVE/ART | PASS |
| V11 | `svg_masters_have_no_raster_external_href_or_style_import` + `ten_concepts_have_editable_svg_masters_matching_native_paths` | JVM/SEC | PASS |
| V12 | `selected_and_focused_nav_are_distinct` + 组件 token 单一权威 | N/JVM | PASS |
| H01 | `continue_first_when_real_committed_record_exists` | N | PASS |
| H02 | `no_continue_hides_resume_hero` | N | PASS |
| H03 | `unconfigured_catalog_shows_truthful_state_without_fake_posters` | N+SHOT | PASS |
| H04 | `unconfigured_catalog_retains_personal_records` | N | PASS |
| H05 | `offline_home_retains_saved_personal_rows`（UiAutomation 真实断网） | N | PASS |
| H06 | `home_catalog_row_truthful_when_partial` + JVM `partial_coverage_does_not_claim_global_total` | N/JVM | PASS |
| H07 | JVM `empty_local_page_does_not_claim_query_complete` | JVM | PASS |
| H08 | JVM `complete_empty_shows_truthful_empty_state` + 首页“完整空”文案分支 | JVM/N | PASS |
| H09 | JVM `unknown_title_or_poster_does_not_fabricate_metadata` | JVM | PASS |
| H10 | `missing_artwork_shows_focusable_placeholder_with_real_title` | N | PASS |
| H11 | JVM `work_id_survives_multi_source_change`（运行时换源不换稳定 ID） | JVM | PASS |
| H12 | `home_resume_uses_committed_checkpoint_and_stays_paused` | N | PASS |
| H13 | `browsing_never_creates_watch_history` | N | PASS |
| N01 | `all_five_top_destinations_reachable_by_dpad` | N | PASS |
| N02 | `home_detail_back_restores_work_and_anchor` | N | PASS |
| N03 | `home_to_library_preserves_strict_query` | N | PASS |
| N04 | `home_favorites_history_contexts_stay_independent` | N | PASS |
| N05 | `home_resume_uses_committed_checkpoint_and_stays_paused`（重开暂停、不自动出声） | N | PASS |
| N06 | `image_completion_does_not_steal_focus` | N | PASS |
| N07 | `rapid_tab_switch_aborts_stale_home_restore` | N | PASS |
| N08 | `one_ok_press_does_not_double_navigate` | N | PASS |
| N09 | `back_closes_top_layer_then_returns_origin`（设置/浏览/详情三段，分见 all_five/home_detail_back/home_resume） | N | PASS |
| N10 | `selected_and_focused_nav_are_distinct` | N | PASS |
| N11 | `ack_lost_after_commit_ui_retry_has_true_state` | N | PASS |
| L01 | `home_1280x720_real_render_with_records` + 布局修复复查 | SHOT/N | PASS |
| L02 | `home_1920x1080_real_render` | SHOT/N | PASS |
| L03 | `home_large_font_long_title` + 设置页大字模式切换（`settings_actions_have_real_effects`） | SHOT/N | PASS |
| L04 | `portrait_artwork_keeps_portrait_containment` + 截图复查 | N/SHOT | PASS |
| L05 | 设置/浏览/加载态均可返回（all_five、settings_actions、unconfigured 系列） | N | PASS |
| L06 | `home_reduced_motion_keeps_focus_feedback` | N/SHOT | PASS |
| L07 | 全中文语义 + 无品牌/水印/评分（各用例语义断言 + 截图复查） | N/SHOT | PASS |
| R01 | schema 1–6 逐字节比对 | H | PASS |
| R02 | `ordinary-regression.json`：Debug/Release 均 `armeabi-v7a`、`com.ichichuang.cinema` 0.9.8/code 18、Release 无签名、`update/sources.json` 未改动 | BUILD | PASS |
| R03 | JVM 370/370 + 277/277 原生 + Python 62/62 + 双 lint 消息零增减（`ordinary-regression.json`、各相位 result） | BUILD | PASS |
| R04 | 合成图带“合成测试”水印且仅由 androidTest 生成于一次性档案；`check_repository_security.py` 对暂存区零违规；无新增运行时依赖 | SEC | PASS |
| R05 | 受测源哈希=提交 blob（`final-tested-source-hashes.json`）；独立审查结论与修复见上节；双工作树干净、仅本地提交 | SEC | PASS |

## 视觉复查（主观设计审查与自动正确性分开）

- 与参考图对照：深炭底、暖金焦点、顶部导航 + 首行续播 + 横向内容行的层级成立；参考图中的
  影片名、评分、海报均为示意，本轮首页只显示真实保存记录与候选目录（无图即占位），未虚构任何事实。
- 1280x720 首查发现首行动作按钮被裁（真实缺陷），已修复为 232dp 紧凑首行 + weight 列并复查通过。
- 大字/长标题：标题两行省略、动作按钮完整可达；竖图 Fit 收纳不裁控制；减动效下焦点反馈（描边）保持。

## 保留的验收边界与停止点

- 未交付：完整图标盘点（T02 全量）、片库严格筛选面板/详情/播放器/设置全量重设计（T10 全量）、
  T11 全局遥控与动效系统、诊断代码删除、生产路由激活、真实 CMS/海报 URL、API24/ARMv7 物理电视、
  真实用户升级、性能与发布/签名门。
- 测试图与媒体全部带“合成测试”水印且仅存在于测试代码与私有 fixture；未进入生产目录或默认来源。
- 本轮停止于 Round 13；未进入 Round 14。仅本地提交，未推送、合并、发布或签名。
