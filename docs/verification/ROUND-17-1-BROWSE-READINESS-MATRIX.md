# Round 17.1 — 浏览就绪性与最终证据核对 验收矩阵

> 日期：2026-10-27。分支 `main`，起点 `6cd8bae64f24ed6ede85c125c4644a8810fef7e1`（R16），
> 收口前 HEAD `48ac4a3`，本轮文档提交见 §末尾。
>
> **状态口径**：`PASS` 只在 box 上真实执行过并且断言成立时才写（JVM 单测、构建、静态枚举、
> Python、安全检查）。凡是需要 Android 设备/模拟器的行（全部 androidTest）一律 `UNVERIFIED`
> ——**已编译，未运行**。R14 的 5 个 assumption skip 保持 `SKIP`，不计入 PASS。
>
> 证据码：`JVM` 宿主单测，`BUILD` 真实 Gradle 输出，`STATIC` 源码/清单枚举，`SEC` 安全检查，
> `NATIVE` 真实仪器化（本轮**不可用**），`REPORT` 可审计记录。
>
> 证据位置：命令输出见 `docs/verification/2026-10-27-round-17-1-browse-readiness.md` §五、§六；
> 原始日志与枚举脚本在被 `.gitignore` 忽略的 `.local/`（不入 Git）。

## B — R12 浏览初始化竞态（7）

| ID | 可测试的声明 | 对应真实方法 / 证据 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| B01 | 失败方法在 `launch(false)` 后不再对尚未发布的探针解引用 | `BrowsePersistenceNativeTest.old_pending_layout_cannot_focus_after_new_query_is_installed` → `awaitBrowseProbe()` | UNVERIFIED | 代码已改、androidTest 已编译；无设备未运行 |
| B02 | 同一缺陷模式的相邻用例也被修正 | `BrowsePersistenceNativeTest.first_nonzero_grid_measure_restores_nonzero_offset_before_focus` | UNVERIFIED | 同上 |
| B03 | 等待是「探针已发布」的可观察条件，不是固定 sleep | `awaitBrowseProbe()` 内 `scenario.onActivity { it.browseProbe != null }`；`until()` 每 25 ms 轮询 | PASS (STATIC) | 源码可复核；`Thread.sleep(25)` 是轮询间隔，非盲等 |
| B04 | 超时语义保持原状（15 s 后 fail，不降级为 skip） | `until()` 的 `fail("等待原生持久化状态超时…")`，诊断串新增 `published=…` | PASS (STATIC) | 未削弱断言 |
| B05 | 原行为断言逐条保留（旧布局不得在新查询后抢焦点） | 方法体断言：`assertFalse(focused().contains(target.value))` 前后两处、查询等价断言 | PASS (STATIC) | 断言未删、未放宽 |
| B06 | 其他 `launch(false)` 站点无同类缺陷（先 focus/until 或 null 安全） | `BrowseNavigationNativeTest` 11 处、`BrowsePersistenceNativeTest` 另 3 处逐条审计 | PASS (STATIC) | 见计划 §四表 |
| B07 | 修改只限 instrumentation 代码，未改生产初始化 | `git diff 6cd8bae..HEAD` 中 `MainActivity`/Room/导航协调者无改动 | PASS (STATIC) | 差异文件清单见 R17 报告 |

## I — 原生测试清单核对（6）

| ID | 可测试的声明 | 对应真实方法 / 证据 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| I01 | `app/src/androidTest` 下 `@Test` 方法总数与类数可枚举且与源码一致 | 枚举脚本 `.local/tools/native_inventory_final.py`（`STATIC`） | PASS | 400 个方法 / 48 个含测试的类 |
| I02 | 相对 `6cd8bae` 的净增方法数明确 | `git show` 两版本枚举比对 | PASS | 397 → 400，净增 3，无删除 |
| I03 | 新增原生方法可点名 | 见 I02 结果表 | PASS (STATIC) | 3 个新增：U05 `loopback_live_stream_reports_unknown_duration_and_no_seek`、720p `video_render_stages_distinct_from_overlay`、`disabled_line_and_unplayable_episode_never_receive_actionable_focus`（停用线路/不可播剧集焦点）。本轮未新增原生长 Back 方法与「反转换集顺序」原生方法（长 Back 只有 JVM 测试；反转顺序见 R17 矩阵 S05，NOT RUN） |
| I04 | 同一类内无重名 `@Test` 方法（不会重复执行） | 枚举脚本 `DUP_IN_CLASS` 为空 | PASS | — |
| I05 | 跨类同名方法已识别并说明 | `user_input_cancels_late_focus_restore` 同时存在于 `WorkDetailNavigationNativeTest` 与 `BrowseNavigationNativeTest` | PASS | 不同类、各自独立执行，不为重复计数 |
| I06 | 播放器控制子家族的最终方法数（不沿用 49/50 旧说法） | `PlayerControls*` + `TvPlayerControlsScreenshotTest` 逐类统计 | PASS | 53 个方法 / 9 个类（本节统计口径见报告 §四） |

## R — 回归、构建与交付（9）

| ID | 可测试的声明 | 对应真实方法 / 证据 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| R01 | JVM 全量单测真实通过，总数取自 JUnit XML | `:app:testDebugUnitTest --rerun` → XML 聚合 | PASS (JVM) | 详见 R17 报告 §五 |
| R02 | Python 测试真实通过 | `python3 -m unittest discover -s scripts/tests` | PASS | 62 tests OK |
| R03 | 仓库安全检查 0 违规（staged 状态） | `python3 scripts/check_repository_security.py` | PASS (SEC) | 见报告 |
| R04 | androidTest 源码可编译（不代表运行结果） | `:app:assembleDebugAndroidTest`、`:app:compileDebugAndroidTestKotlin --rerun` | PASS (BUILD) | 运行结果 UNVERIFIED |
| R05 | 普通 Debug APK 可构建且身份未变 | `:app:assembleDebug` + `aapt2 dump badging` | PASS (BUILD) | 包名/版本/ABI 见报告 |
| R06 | Room schema v1–v6 相对 `6cd8bae` 无变化 | `git diff 6cd8bae -- app/schemas` 为空 | PASS (STATIC) | 无新增 migration |
| R07 | 未改构建配置与依赖 | `git diff --stat 6cd8bae..HEAD` 不含 `*.gradle.kts`/`gradle/`/`gradle.properties` | PASS (STATIC) | — |
| R08 | R14 的 5 个 assumption skip 仍为 skip，不冒充 PASS | R14 矩阵/报告既有记录 | SKIP | 历史记录，本轮未重跑 |
| R09 | 交付为本地提交、工作区干净、未 push | `git status --short` 为空；`git ls-remote origin refs/heads/main` 仍为 `6cd8bae`，`git log origin/main..HEAD` 可比较，本地 `main` 领先 | PASS (STATIC) | origin 存在可比较；未 push |

## 计数

| 结果 | 数量 |
| --- | --- |
| PASS | 19（B03–B07 5 条、I01–I06 6 条、R01–R07 与 R09 8 条；均来自本轮真实命令或静态枚举） |
| FAIL | 0 |
| UNVERIFIED（已编译，未运行） | B01、B02（2 条；整体设备侧能力见 R17 报告 §七） |
| SKIP（历史 assumption） | 1 行（R08，汇总 R14 遗留的 5 个 assumption skip，不计入 PASS） |
| BLOCKED | 0 |

> 说明：本矩阵刻意不把任何「需要模拟器」的声明写成 PASS。B01/B02 的代码修改是确定的，
> 但「旧布局不抢焦点」这一行为只有在设备上跑过才能算通过，因此状态停在 UNVERIFIED。
