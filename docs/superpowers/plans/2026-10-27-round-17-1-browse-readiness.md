# Round 17.1 — 浏览初始化竞态收口与最终证据核对 执行计划

> 执行日期：2026-10-27。分支 `main`，起点 `6cd8bae64f24ed6ede85c125c4644a8810fef7e1`（R16）。
> 本计划只覆盖 R17 留下的两个验收缺口：R12 浏览回归的初始化竞态，以及最终原生测试清单的核对。
> 实际结果见 `docs/verification/2026-10-27-round-17-1-browse-readiness.md` 与
> `docs/verification/ROUND-17-1-BROWSE-READINESS-MATRIX.md`。

## 一、缺口来源

R17 的执行记录报告了两项未收口的事实（历史记录，本轮不继承其结论）：

1. **R12 浏览回归 1 FAIL**：`BrowsePersistenceNativeTest.old_pending_layout_cannot_focus_after_new_query_is_installed`
   在 `launch(false)` 之后立即轮询，轮询条件里 `runtime()` 直接执行 `requireNotNull(it.browseProbe)`；
   `MainActivity` 的 `browseProbe` 由异步初始化协程发布，未发布完成时轮询先抛 `IllegalArgumentException`，
   而不是等待初始化成功。
2. **最终播放器原生测试总数未经核对**：记录里先有 49/49，随后又新增 720p 截图测试并单独 8/8 通过，
   最终完整套件是否包含全部新增方法未被证明。

## 二、判定：这是测试就绪性竞态，不是生产初始化缺陷

判断依据（代码层面）：

- `MainActivity` 的探针发布是**异步**的，测试在其发布前读取属于「过早访问」。
- 同一个测试类里已有安全写法：`old_initial_query_load_cannot_replace_new_explicit_query_and_layout`
  用 `until { scenario.onActivity { present = it.browseProbe != null } }` 等待发布，
  另一个冷恢复测试用 `it.browseProbe?.navigation != null` 做 null 安全轮询。
- 因此不是「探针永远发布不出来」，而是「这两个用例少了等待发布这一步」。

结论：把修正限制在受影响的 instrumentation 代码内，不改 `MainActivity`、不改进程初始化行为。

## 三、修正方案（最小、可观察、有时限）

在测试类中新增一个只读就绪助手：

```
private fun awaitBrowseProbe() = until {
    var present = false
    scenario!!.onActivity { present = it.browseProbe != null }
    present
}
```

- **有界**：复用既有 `until()` 的 15 秒上限，超时仍抛 `fail(...)`，并打印 `published=false` 等诊断；
  初始化真失败时不会变成 assumption skip。
- **可观察**：等待的是「探针已发布」这一真实状态，不是固定 `sleep`。
- **不改断言**：原本的行为断言（旧布局不得在新查询之后抢焦点）逐条保留。
- **只在确有缺陷处使用**：仅 `first_nonzero_grid_measure_restores_nonzero_offset_before_focus`
  与 `old_pending_layout_cannot_focus_after_new_query_is_installed` 两处 `launch(false)` 后**立即**
  `runtime { … }` 的用例加调用。

## 四、相邻用例审计（R17.1 §4）

审计对象：所有 `launch(false)` 之后立刻访问探针的用例。

| 位置 | `launch(false)` 之后的第一句 | 是否同一缺陷 | 处置 |
| --- | --- | --- | --- |
| `BrowsePersistenceNativeTest.first_nonzero_grid_measure_restores_nonzero_offset_before_focus` | 直接 `runtime { … }` | 是 | 加 `awaitBrowseProbe()` |
| `BrowsePersistenceNativeTest.old_pending_layout_cannot_focus_after_new_query_is_installed` | 直接 `runtime { … }` | 是 | 加 `awaitBrowseProbe()` |
| `BrowsePersistenceNativeTest.old_initial_query_load_cannot_replace_new_explicit_query_and_layout` | 内联 `until` 等 `browseProbe != null` | 否 | 不改 |
| `BrowsePersistenceNativeTest.pause_resume_does_not_revive_cancelled_poster_restore` | 等 `failedFocusRequests > 0`（不解引用探针） | 否 | 不改 |
| `BrowsePersistenceNativeTest` 冷恢复用例 | `until { it.browseProbe?.navigation != null }`（null 安全） | 否 | 不改 |
| `BrowseNavigationNativeTest` 各 `launch(false)` 站点 | 先 `focus(...)` / `until` 等待可见状态 | 否 | 不改 |

原则：只修确有缺陷处，不做「全部助手统一无条件等待」的扩散式改动。

## 五、最终原生测试清单核对（R17.1 §5）

1. 用脚本按 `git show` 枚举 `6cd8bae` 与最终 HEAD 两个版本的 `app/src/androidTest` 下全部 `@Test` 方法，
   统计类数、方法数、增量方法、同方法名跨类重名情况。
2. 结果**从枚举脚本输出取真实数字**，不假设 49 或 50。
3. 区分「播放器控制子家族」（`PlayerControls*` + `TvPlayerControlsScreenshotTest`）与全量 androidTest。
4. box 无模拟器：全部标「已编译，未运行（UNVERIFIED）」，不写运行通过。

## 六、回归门

- JVM：`./gradlew :app:testDebugUnitTest`，总数从 JUnit XML 聚合。
- Python：`python3 -m unittest discover -s scripts/tests`。
- 安全检查：`python3 scripts/check_repository_security.py`，staged 状态下必须 0 违规。
- 原生：`assembleDebugAndroidTest` 仅编译；R12 浏览套件本身需设备，标 UNVERIFIED。
- R14 的 5 个 assumption skip 保持为 skip。

## 七、交付物

```
docs/superpowers/plans/2026-10-27-round-17-1-browse-readiness.md   （本文件）
docs/verification/ROUND-17-1-BROWSE-READINESS-MATRIX.md
docs/verification/2026-10-27-round-17-1-browse-readiness.md
```

R17 的历史结果（44 PASS / 1 FAIL / 1 BLOCKED、P07 PID 4341 → 4430）保持为不可复核的历史记录，
本轮新结论只以 2026-10-27 附录形式记录，不改写为通过。
