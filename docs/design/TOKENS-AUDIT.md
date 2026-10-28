# R18-ST1 设计令牌审计、补齐与减弱动效

状态：已实现（ST1 范围）。日期：2026-10-28。基线：`main@f63a833`。

本文只记录 ST1 做过的事：审计 → 补令牌 → 替换字面量 → 减弱动效 → 脚本守卫。
设置页 UI、首次使用引导、状态矩阵属于 ST2/ST3/ST4，本文只在「后续」一节列出接线缺口。

## 1. 结论

| 项 | 数值 |
| --- | --- |
| 令牌唯一权威 | `app/src/main/kotlin/com/ichichuang/cinema/core/design/` |
| 审计基线（`f63a833`）非令牌目录字面量 | 50 处 / 33 行 / 6 个文件 |
| 本次替换为令牌 | 50 处（其中 `dp` 49 处、动画 spec 1 处） |
| 旧 UI 白名单保留 | 84 处 / 7 个文件（`LEGACY-OLD-UI`，R21 删除旧 UI 时清零） |
| `// token-ok:` 豁免 | 0 处 |
| 脚本当前输出 | `total=155 token-dir=71 legacy-whitelist=84 exempted=0 violations=0` |

数字自洽关系（脚本每次运行都会重新计算，文档与之一致）：

- 基线：`151 = 17(令牌目录) + 84(白名单) + 50(违规)`
- 现状：`155 = 71(令牌目录) + 84(白名单) + 0(违规)`
- 令牌目录从 17 → 71，增量 54 全部来自本次新增/扩展的令牌定义文件；被替换的 50 处不再计入 `violations`。

颜色值全部未改：`Ink / Panel / PanelHi / Gold / TextMain / TextSub / TextBody` 的十六进制与基线一致，新增语义色只有 `Success / Warning / Danger / Border` 四个（`Danger` 取的是旧片库筛选面板里已经在用的 `0xFFE57373`，因此那处颜色本来就没变）。

## 2. 新增与扩展的令牌

### 2.1 新增文件

| 文件 | 内容 | 说明 |
| --- | --- | --- |
| `core/design/Spacing.kt` | `Spacing` | 2dp 栅格；数字后缀即 dp 值（`n4`…`n48`），另有 4dp 基数语义别名 `xs 4 / sm 8 / md 12 / lg 16 / xl 24 / xxl 32 / page 48` |
| `core/design/Radii.kt` | `Radii` | `xs 6 / sm 8 / md 10 / lg 12 / xl 14 / full 999` |
| `core/design/Sizes.kt` | `Sizes`、`IconSize`、`TileSize` | 指示条 `3dp`、选中点 `8/10dp`、导航下划线宽 `24dp`；图标 `18/20/26dp`；瓦片 `208x117 / 232x130 / 120x180` |
| `core/design/Layers.kt` | `Layers`（z）、`Elevation` | `content 0 / nav 1 / overlay 10 / dialog 20 / toast 30`；elevation 同名五级 |
| `core/design/TypeScale.kt` | `TypeScale` | TV 10-foot 字号阶梯：`caption 16 / label 18 / body 20 / bodyLarge 24 / title 28 / titleLarge 34 / display 44`，附行高 |
| `core/design/ReducedMotion.kt` | `shouldReduceMotion()`、`AnimatorScaleSource` | 纯函数 + 可注入接口，JVM 可测 |
| `core/design/SystemAnimatorScaleSource.kt` | 系统动效缩放读取 | 读 `Settings.Global.ANIMATOR_DURATION_SCALE`，读不到/异常按 `1f` |
| `core/design/MotionTransitions.kt` | `LocalReducedMotion`、`ProvideReducedMotion`、`rememberMotionEnter/Exit` | 入场/出场过渡，只用 alpha/scale/translation |
| `data/settings/ReduceMotionPreference.kt` | `ReduceMotionPreference` | DataStore 键 `reduce_motion`，默认 `false` |

### 2.2 扩展的既有文件

- `core/design/Color.kt`：新增 `Success / Warning / Danger / Border` 与语义层 `Palette`（`background / surface / surfaceRaised / textPrimary / textSecondary / textBody / accent / success / warning / danger / border / focusRing / scrim`）。原顶层 `Ink/Panel/...` 保留，旧代码不需要改就能编译，像素不变。
- `core/design/Motion.kt`：新增 `DUR_EXIT 210`（= `DUR_ENTER` 的 75%）、`DUR_REDUCED 40`、`DUR_PULSE 900`、`EaseIn`、`STAGGER_MS 40`、`STAGGER_TOTAL_MS 300`、`ENTER_OFFSET_DP 8f`、`ENTER_SCALE_FROM 0.96f`、`enterSpec()/exitSpec()`、`focusScale()/pressScale()`、`staggerDelayMs()`、`exit()`、`pulse()`；`focus/quick/base/enter/press/swap` 增加 `reduced: Boolean = false` 参数（默认值不变，旧调用点行为完全一致）。

## 3. 审计基线（`f63a833`）

用与本仓库同一个脚本、同一套规则复算 HEAD 源码树：

```
total=151 token-dir=17 legacy-whitelist=84 exempted=0 violations=50
```

违规分布（50 处，按文件）：

| 文件 | 处数 | 规则 |
| --- | --- | --- |
| `TvHomeScreen.kt`（`feature/home` 的 `visual` 子包） | 16 | dp |
| `core/ui/TvControls.kt` | 11 | dp |
| `core/ui/TvPosterTile.kt` | 11 | dp |
| `core/ui/TvNavTile.kt` | 6 | dp |
| `CandidateSettingsScreen.kt`（同上，新 UI 设置页） | 5 | dp |
| `core/ui/PosterSkeleton.kt` | 1 | 动画 spec（`tween(900, …)`） |

按规则合计：`dp-literal 49 + animation-spec 1 = 50`。

## 4. 替换对照（50 处）

替换原则：**数值必须完全相同**（同一 dp 映射到同值令牌），不为凑令牌取近似值。没有合适令牌的，先补令牌再替换（如 `Sizes.navUnderline 24dp`、`TileSize.*`）。

### `core/ui/TvControls.kt`（11）

| 行 | 原值 | 令牌 |
| --- | --- | --- |
| 44 | `spacedBy(8.dp)` | `Spacing.sm` |
| 47 | `tvFocus(cornerRadius = 999.dp)` | `Radii.full` |
| 50 | `RoundedCornerShape(999.dp)` | `Radii.full` |
| 56 | `padding(horizontal = 20.dp)` | `Spacing.n20` |
| 56 | `padding(vertical = 10.dp)` | `Spacing.n10` |
| 61 | `size(18.dp)` | `IconSize.inline` |
| 86 | `spacedBy(12.dp)` | `Spacing.md` |
| 88 | `size(18.dp)` | `IconSize.inline` |
| 102 | `RoundedCornerShape(10.dp)` | `Radii.md` |
| 103 | `size(10.dp)` | `Sizes.dotLarge` |
| 103 | `RoundedCornerShape(5.dp)` | `Radii.full`（10dp 方块上的 5dp 圆角与胶囊等价，观感不变） |

### `core/ui/TvPosterTile.kt`（11）

| 行 | 原值 | 令牌 |
| --- | --- | --- |
| 58 | `RoundedCornerShape(10.dp)` | `Radii.md` |
| 63 | `232.dp` | `TileSize.wideWidth` |
| 63 | `208.dp` | `TileSize.landscapeWidth` |
| 63 | `130.dp` | `TileSize.wideHeight` |
| 63 | `117.dp` | `TileSize.landscapeHeight` |
| 64 | `120.dp` | `TileSize.portraitWidth` |
| 64 | `180.dp` | `TileSize.portraitHeight` |
| 71 | `tvFocus(cornerRadius = 12.dp)` | `Radii.lg` |
| 88 | `size(26.dp)` | `IconSize.placeholder` |
| 97 | `height(3.dp)` | `Sizes.indicator` |
| 100 | `height(6.dp)` | `Spacing.n6` |

### `core/ui/TvNavTile.kt`（6）

| 行 | 原值 | 令牌 |
| --- | --- | --- |
| 48 | `tvFocus(cornerRadius = 10.dp)` | `Radii.md` |
| 54 | `spacedBy(8.dp)` | `Spacing.sm` |
| 57 | `size(20.dp)` | `IconSize.nav` |
| 62 | `padding(top = 4.dp)` | `Spacing.xs` |
| 62 | `width = 24.dp` | `Sizes.navUnderline` |
| 62 | `height = 3.dp` | `Sizes.indicator` |

### `core/ui/PosterSkeleton.kt`（1）

| 行 | 原值 | 令牌 |
| --- | --- | --- |
| 37 | `tween(900, easing = Motion.EaseInOut)` | `Motion.pulse()` |

### `TvHomeScreen.kt`（`feature/home` 的 `visual` 子包，16 处）

| 行 | 原值 | 令牌 |
| --- | --- | --- |
| 115 | `start/end = 32.dp`（2 处） | `Spacing.xxl` |
| 115 | `top = 20.dp` | `Spacing.n20` |
| 115 | `spacedBy(20.dp)` | `Spacing.n20` |
| 160 | `start = 32.dp` | `Spacing.xxl` |
| 160 | `top = 10.dp` | `Spacing.n10` |
| 162 | `start = 32.dp` | `Spacing.xxl` |
| 162 | `top = 12.dp` | `Spacing.md` |
| 163 | `spacedBy(18.dp)` | `Spacing.n18` |
| 166 | `spacedBy(20.dp)` | `Spacing.n20` |
| 172 | `spacedBy(10.dp)` | `Spacing.n10` |
| 199 | `spacedBy(8.dp)` | `Spacing.sm` |
| 201 | `spacedBy(14.dp)` | `Spacing.n14` |
| 218 | `spacedBy(14.dp)` | `Spacing.n14` |
| 219 | `208.dp` | `TileSize.landscapeWidth` |
| 219 | `117.dp` | `TileSize.landscapeHeight` |

### `CandidateSettingsScreen.kt`（同上，5 处）

| 行 | 原值 | 令牌 |
| --- | --- | --- |
| 38 | `start/end = 48.dp`（2 处） | `Spacing.page` |
| 38 | `top/bottom = 32.dp`（2 处） | `Spacing.xxl` |
| 39 | `spacedBy(14.dp)` | `Spacing.n14` |

## 5. 白名单与豁免

### 5.1 `LEGACY-OLD-UI` 白名单（84 处 / 7 文件）

这些是 R18 之前的老界面，R21 会随旧 UI 一起删除。现在把它们逐个改写成令牌，改动量大、且会随删除一起丢掉；先按原值保留，由脚本显式列出（不是"悄悄跳过"）。

| 文件 | 处数 |
| --- | --- |
| `feature/detail/TvWorkDetailScreen.kt` | 23 |
| `feature/library/TvLibraryFilterPanel.kt` | 17（含 1 处 `Color(0xFFE57373)`，与 `Danger` 同值，未改） |
| `feature/browse/BrowseProbeScreen.kt` | 15 |
| `feature/library/TvLibraryScreen.kt` | 13 |
| `feature/player/TvPlayerControlsScreen.kt` | 7 |
| `feature/personal/PersonalLibraryScreen.kt` | 6 |
| `HomeScreen.kt`（旧首页，`feature/home`） | 3 |
| `feature/settings/SettingsScreen.kt` | 0（已在白名单：它全部走 `TvMetrics.dp()`，当前无字面量；保留条目避免它回填字面量时漏检） |

白名单条目在脚本里以 `feature/` 下的相对路径书写、导入时拼接（避免把形如家目录的路径片段写进源文件，被仓库安全检查误判为个人路径）；不在清单里的文件一律按违规处理。

规则：**新 UI 不允许落进白名单文件**。白名单条目必须真实存在（`scripts/tests/test_check_design_tokens.py` 会逐个断言存在，且必须位于 `feature/` 下），R21 删除旧 UI 时同步删除。新增"旧 UI"文件需要同时改脚本和白名单表，属于显式动作。

### 5.2 豁免（0 处）

`// token-ok: <原因>` 允许精确豁免**单行**，且原因不能为空（空原因仍报违规）。本次替换后没有需要豁免的行，因此为 0。机制本身由测试覆盖（豁免只对本行生效、空原因不放行）。

## 6. 令牌脚本 `scripts/check_design_tokens.py`

- 扫描范围：`app/src/main/kotlin` 下所有 `.kt`，跳过 `core/design/`。
- 规则：`Color(0x`、`tween(`/`spring(`、`keyframes`、`durationMillis = <数字>`、`<数字>.dp`（`0.dp`/`1.dp` 发丝线除外）、`<数字>.sp`。
- 输出：每条违规 `文件:行: 规则: 行内容`，末尾一行汇总 `total / token-dir / legacy-whitelist / exempted / violations`。
- 退出码：`0` 干净；`1` 有违规；`2` 找不到源码目录。
- 反向验证（单元测试内真实执行脚本，非替身）：临时树里写 `Color(0xFF…)`、`tween(`、`12.dp`、`16.sp`、`durationMillis = 300` 均 `exit 1` 且行号正确；`0.dp/1.dp` 放行；`token-ok` 只放行本行；白名单只覆盖列出的文件。
- 当前结果：

```
Scanned 197 Kotlin files under app/src/main/kotlin.
literal occurrences: total=155 token-dir=71 legacy-whitelist=84 exempted=0 violations=0
OK: every design literal outside core/design is tokenised, exempted or legacy-whitelisted.
```

## 7. 对比度（WCAG 2.1 相对亮度）

实测值（`core/design` 真实色值，写入 `ContrastTest` 并由 JVM 断言 ≥ 4.5:1，焦点环按非文本 ≥ 3:1）：

| 前景 / 背景 | 比值 | 门槛 | 结果 |
| --- | --- | --- | --- |
| `TextMain / Ink` | 18.17 | 4.5 | PASS |
| `TextMain / Panel` | 16.57 | 4.5 | PASS |
| `TextMain / PanelHi` | 14.50 | 4.5 | PASS |
| `TextSub / Ink` | 6.07 | 4.5 | PASS |
| `TextSub / Panel` | 5.53 | 4.5 | PASS |
| `TextSub / PanelHi` | 4.84 | 4.5 | PASS |
| `TextBody / Ink` | 11.75 | 4.5 | PASS |
| `TextBody / Panel` | 10.71 | 4.5 | PASS |
| `Gold / Ink` | 8.44 | 4.5 | PASS |
| `Gold / Panel` | 7.70 | 4.5 | PASS |
| `Success / Ink` | 10.84 | 4.5 | PASS |
| `Success / Panel` | 9.89 | 4.5 | PASS |
| `Warning / Ink` | 10.68 | 4.5 | PASS |
| `Warning / Panel` | 9.75 | 4.5 | PASS |
| `Danger / Ink` | 6.62 | 4.5 | PASS |
| `Danger / Panel` | 6.04 | 4.5 | PASS |
| `Ink / Gold`（金底按钮文字） | 8.44 | 4.5 | PASS |
| `Gold / Ink`（焦点环） | 8.44 | 3.0 | PASS |
| `Gold / PanelHi`（焦点环） | 6.74 | 3.0 | PASS |

如实记录一处**不达标但判定为装饰性**的项：

| 项 | 比值 | 说明 |
| --- | --- | --- |
| `Border` 压平到 `Ink` | 2.54 | 未达非文本 3:1 |
| `Border` 压平到 `Panel` | 2.55 | 同上 |

`Border` 是未聚焦描边（`TextSub @ 55%`），它界定的表面本身还有 `Panel` 与 `Ink` 的明度差；WCAG 1.4.11 要求的"标识控件的视觉信息"由焦点环承担（金色，8.44 / 6.74，达标）。色值属于冻结的视觉基线，本次**不为了凑比值改颜色**，只把它写进测试报告的输出里（`decorative` 行），并在本节如实标注。

## 8. 减弱动效

### 8.1 判定

```kotlin
fun shouldReduceMotion(userSetting: Boolean, animatorDurationScale: Float): Boolean =
    userSetting || animatorDurationScale == 0f
```

真值表（JVM 覆盖四种组合）：

| 用户设置 | 系统缩放 | 结果 |
| --- | --- | --- |
| false | 1.0 | 正常 |
| false | 0.5 | 正常 |
| true | 1.0 | 减弱 |
| false | 0.0 | 减弱 |
| true | 0.0 | 减弱 |

系统值通过 `AnimatorScaleSource` 接口读取（`SystemAnimatorScaleSource` 读 `Settings.Global.ANIMATOR_DURATION_SCALE`，异常/缺失返回 `1f`），纯函数不碰 Android，JVM 测试直接调生产代码。

### 8.2 减弱后的取值

| 项 | 正常 | 减弱 |
| --- | --- | --- |
| `focus()` | 弹簧（damping 0.72） | `tween(40ms, Linear)` |
| `quick/base/enter/exit/swap/press` | 140/200/280/210/260 + 弹簧 | `tween(40ms, Linear)` |
| 焦点缩放 `focusScale()` | 1.06 | 1.0（不缩放） |
| 按下缩放 `pressScale()` | 0.97 | 1.0（不缩放） |
| 入场 `enterSpec()` | 280ms、+8dp、0.96→1、EaseOut | 40ms、0dp、1→1、线性淡入 |
| 出场 `exitSpec()` | 210ms、-8dp、1→0.96、EaseIn | 40ms、0dp、1→1、线性淡出 |
| 列表 stagger | 每项 +40ms，总计 ≤300ms | 0ms |
| `linear(ms)`（进度条等连续量） | 不变 | **不变**（它表达的是用户正在读的数值，快照会变成谎报位置） |

已接线处：`core/focus/TvFocus.kt`（焦点/按下的 spec 与缩放）、`core/ui/PosterSkeleton.kt`（减弱时骨架保持恒定 0.5 alpha，不再呼吸）。其余动画的 `reduced` 传参属于 ST2/ST3 的接线工作。

### 8.3 过渡构造

`rememberMotionEnter/Exit` 用 `fadeIn/scaleIn/slideInVertically`（以及出场对应物）搭出过渡，只改 alpha / scale / translation（全部走 graphics layer），不改布局；`reduced = true` 时去掉位移与缩放，只保留 40ms 淡入淡出，`offsetDp = 0`。数值全部来自 `Motion.enterSpec()/exitSpec()`，测试直接断言这三个量。

## 9. 存储

- 键：`reduce_motion`（`ReduceMotionPreference.KEY_NAME`），布尔，默认 `false`。
- 不改任何既有键名、不改 Room schema、不改迁移路径；缺键读作默认值。
- `CinemaSettings.reduceMotion: Flow<Boolean>` 与 `setReduceMotion(Boolean)` 走既有 `dataStore.editLegacy`。
- JVM 测试覆盖：缺键默认、写入 true/false 往返、写入不动其它键、键名不变。

## 10. 已知缺口（后续子任务）

1. `TypeScale` 已建立但**未接入** `CinemaTheme`：ST1 要求视觉不变，字号切换会改变所有界面的排版，因此留给 ST2/ST4 一次性接线。
2. `LocalReducedMotion` 尚未在应用根部 provide（需要在设置页读用户开关 + 读系统缩放后 provide），属于 ST2。
3. `rememberMotionEnter/Exit`、`staggerDelayMs()` 已就绪但还没有调用方，ST2/ST3 的转场与列表入场使用。
4. 白名单 84 处随 R21 删除旧 UI 一并清零；届时脚本白名单表也必须清空，否则 `test_every_whitelist_entry_exists_and_is_old_ui` 会失败（这是有意的守卫）。
