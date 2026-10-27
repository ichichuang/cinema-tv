# Round 17.1 — 浏览初始化竞态收口与最终证据核对 验证报告

> 执行日期：2026-10-27。分支 `main`。起点 `6cd8bae64f24ed6ede85c125c4644a8810fef7e1`（R16）。
> 本报告只陈述本轮实际执行的验证；未执行者标 UNVERIFIED / NOT RUN，绝不写 PASS。
> 配套文件：`docs/superpowers/plans/2026-10-27-round-17-1-browse-readiness.md`、
> `docs/verification/ROUND-17-1-BROWSE-READINESS-MATRIX.md`。

## 一、起点核对

```
$ git log --oneline -1
48ac4a3 R17-ST3.3: align st3b doc with final code
$ git status --short
（空）
```

与编排方给定的 HEAD 一致，工作区干净，未做 reset/amend。

## 二、R12 失败：原因与复现状态

**失败方法**：`app/src/androidTest/kotlin/com/ichichuang/cinema/feature/browse/BrowsePersistenceNativeTest.kt`
的 `old_pending_layout_cannot_focus_after_new_query_is_installed`。

**根因（源码级，可复核）**：

1. `MainActivity` 的 `browseProbe` 由异步初始化协程发布，`launch(false)` 不等焦点、也就不等发布。
2. 测试的 `runtime()` 助手直接 `requireNotNull(it.browseProbe)`。
3. 失败方法在 `launch(false)` 后立刻 `runtime { … }` 轮询；探针未发布时轮询先抛
   `IllegalArgumentException`，表现为测试失败而非「等一会儿就好」。

**这是测试就绪性竞态，不是生产初始化缺陷**：同一个类里已有两种安全写法（`until { scenario.onActivity { present = it.browseProbe != null } }`、`it.browseProbe?.navigation != null`），
说明探针能正常发布，只是这两个用例缺了「等待发布」。

**复现状态：UNVERIFIED。** box 上没有 Android 模拟器/真机，无法执行任何 androidTest，
因此本轮的失败复现与修复后的通过都**没有**运行证据；报告不把历史 FAIL 或本轮编译通过说成复现/验证结果。

## 三、代码改动（唯一一处）

`app/src/androidTest/kotlin/com/ichichuang/cinema/feature/browse/BrowsePersistenceNativeTest.kt`（+14 / −1）：

```kotlin
private fun awaitBrowseProbe() = until {
    var present = false
    scenario!!.onActivity { present = it.browseProbe != null }
    present
}
```

调用点两处（`launch(false)` 之后立刻访问探针的两个用例）：

- `first_nonzero_grid_measure_restores_nonzero_offset_before_focus`
- `old_pending_layout_cannot_focus_after_new_query_is_installed`

同时把 `until()` 超时诊断串补上 `published=${it.browseProbe != null}`，让「探针没发布」与
「布局/焦点不对」在失败信息里可区分。**断言未删、未放宽**；15 秒上限、`fail(...)` 语义不变；
没有新增 `Thread.sleep`（`until()` 里原有的 25 ms 是轮询间隔）。

未改动 `MainActivity`、Room 仓库、导航协调者或生产初始化行为。

## 四、相邻用例审计（R17.1 §4）

| 站点 | `launch(false)` 后第一句 | 同类缺陷 | 处置 |
| --- | --- | --- | --- |
| `first_nonzero_grid_measure…` | 直接 `runtime { … }` | 是 | 加 `awaitBrowseProbe()` |
| `old_pending_layout_cannot_focus…` | 直接 `runtime { … }` | 是 | 加 `awaitBrowseProbe()` |
| `old_initial_query_load_cannot_replace…` | 内联 `until` 等 `browseProbe != null` | 否 | 不改 |
| `pause_resume_does_not_revive_cancelled_poster_restore` | 等 `failedFocusRequests > 0`（不解引用探针） | 否 | 不改 |
| 冷恢复用例（`assertColdDeepRemovalRestores`） | `until { it.browseProbe?.navigation != null }` | 否 | 不改 |
| `BrowseNavigationNativeTest` 11 处 | 先 `focus(...)`/`until` 等可见状态 | 否 | 不改 |

## 五、最终原生测试清单（R17.1 §5）

枚举方式：对 `6cd8bae` 与最终 HEAD 分别 `git show` 每个 `app/src/androidTest/**/*.kt`，
用同一正则抽取 `@Test` 后的方法名，按「声明该方法的类」归属，脚本见 `.local/tools/native_inventory_final.py`
与 `.local/tools/enum_methods_at.py`（均在忽略目录内，不入 Git）。

| 项 | `6cd8bae` | 最终 HEAD |
| --- | --- | --- |
| `@Test` 方法总数 | 397 | **400** |
| 含测试的类数 | 47 | **48** |

**本轮新增 3 个方法（无删除）**：

| 类 | 方法 | 对应目标 |
| --- | --- | --- |
| `PlayerControlsUnknownDurationNativeTest`（新类） | `loopback_live_stream_reports_unknown_duration_and_no_seek` | U05 未知时长 |
| `TvPlayerControlsScreenshotTest` | `video_render_stages_distinct_from_overlay` | 720p 视频渲染 |
| `PlayerControlsEpisodesNativeTest` | `disabled_line_and_unplayable_episode_never_receive_actionable_focus` | 选择器焦点（原生侧） |

**播放器控制子家族**（8 个 `PlayerControls*` 测试类 + `TvPlayerControlsScreenshotTest`，共 9 类）：
`Episodes 8 + Key 9 + Lifecycle 7 + Off 1 + ProcessReplay 2 + Seek 8 + Ui 9 + UnknownDuration 1 + Screenshot 8`
= **53 个方法 / 9 个类**（独立复核方枚举一致）。独立复核方用同一口径（`git show 6cd8bae:<文件>` 逐文件抽取 `@Test`）
对起点复核：`6cd8bae` 的播放器控制子家族为 **50 个方法 / 8 个类**（无 `PlayerControlsUnknownDurationNativeTest`）。
历史记录里的「49/49」对应的源码版本无法确认，本轮不采用；本轮相对起点净增 3 个方法，以本表为准。

**重名检查**：

- 同类内重名：0（脚本输出 `DUP_IN_CLASS {}`）。
- 跨类同名：`user_input_cancels_late_focus_restore` 同时存在于 `WorkDetailNavigationNativeTest`
  与 `BrowseNavigationNativeTest`。不同类各自独立执行，不会被算成同一方法的重复执行。

**运行结果：UNVERIFIED。** box 无设备，全部 400 个方法只有编译证据（`assembleDebugAndroidTest` 通过），
没有 started/passed/failed/ignored 的真实仪器化数字可以报告——本报告不给这些数字编造数值。

## 六、验证与回归（本轮真实输出）

| 命令 | 关键输出 |
| --- | --- |
| `python3 -m unittest discover -s scripts/tests` | `Ran 62 tests in 7.947s` / `OK` |
| `python3 scripts/check_repository_security.py` | `Checked 460 tracked files; 0 policy violations`（staged 状态下运行） |
| `:app:testDebugUnitTest --rerun` | `BUILD SUCCESSFUL`；XML 聚合 `files=50 tests=506 failures=0 errors=0 skipped=0` |
| `:app:assembleDebugAndroidTest` | `BUILD SUCCESSFUL`（57 个任务，全部 up-to-date；另有 `:app:compileDebugAndroidTestKotlin --rerun` → `BUILD SUCCESSFUL`，1 个任务真实执行） |
| `:app:assembleDebug` | `BUILD SUCCESSFUL`；执行方那一次构建 `app-debug.apk` 20 955 289 字节（独立复核方 box 构建为 20 731 187 字节，构建不可复现，该大小仅代表那一次构建） |
| `aapt2 dump badging app-debug.apk` | `com.ichichuang.cinema.debug`、`0.9.8-debug`、`versionCode 18`、minSdk 24、targetSdk 28、`native-code: armeabi-v7a` |
| `git diff 6cd8bae -- app/schemas` | 输出为空（v1–v6 未变） |

androidTest 的运行（含本报告修正的 R12 浏览套件）**未执行**：无模拟器，标 UNVERIFIED。

**R14 的 5 个 assumption skip**：保持 skip，本轮未重跑、不计入 PASS。

## 七、剩余限制（UNVERIFIED 汇总）

1. **R12 浏览回归的修复效果**：代码已改、已编译，未在设备上运行 → UNVERIFIED。原 FAIL 与本轮的
   「修复后通过」都没有可复核的运行日志。
2. **全部 androidTest（400 个方法）**：仅编译。R17.1 §6 要求的「R12 浏览套件零失败、每个可运行方法
   都有交代」无法在 box 上完成 → UNVERIFIED。
3. **R17 报告列出的设备侧限制**（U05/P07/720p、E05/E06 屏障、语义树焦点）同样 UNVERIFIED。
4. **播放器有序切换的残余**（引用 R17 ST3 报告 §八，本轮未消除）：队列内过期检查只做一次；
   `pause()`/`capture()` 在检查之前；探针内拒绝时不调用 `onStale`；探针重建后代次归零；
   `reproject()` 在 try 之外；队列卡死会让后续切换排队。
5. **独立评审**：待编排方独立评审。本报告不称已通过评审。

## 八、历史结果的处理

R17 原提交 `46e40ab` 及其「44 PASS / 1 FAIL / 1 BLOCKED」「P07 PID 4341 → 4430」均为历史记录：
提交不可得、证据不可复核。本报告**不改写**它们为通过，也不把它们当作本轮的基线数字；
本轮所有结论只来自上表的真实命令输出。

## 九、commit

本轮在 `main` 上共三次本地提交：ST4 主提交 `709587d252bf0e71e23badcffab6da0ad4146b1f`（内容为本报告、R17.1 计划与矩阵、R17 的计划/矩阵/报告，
以及在 `docs/superpowers/progress/2026-10-23-tv-rebuild.md` 追加的 R17/R17.1 条目；`README.md` 没有轮次进度段落，未改动）、ST4 更正 `213024f25214f064d7299dfd73bd8469f5d93a35`
（本节交付范围描述），以及按独立复核清单所做的文档修正提交（见 `git log` 末尾，提交无法写入自身 SHA）。
不 `git add -A`，不 push。
