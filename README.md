# 极简影院 · cinema-tv

给 **FFALCON 65S78A（雷鸟鹏7 2026款 / 灵控系统）** 定制的 Android TV 电影应用。
目标只有一个：**用遥控器舒服地挑电影、看电影**，没有任何多余功能。

---

## 版本路线

| 阶段 | 内容 | 状态 |
|---|---|---|
| **0. 骨架** | 首页界面 + 遥控器焦点 + 云端自动构建 | ✅ 完成 |
| **0.5 地基** | 设计系统 / 焦点系统 / 四层架构 / 导航 / 依赖注入 | ✅ 完成 |
| **1. 数据** | 接在线影视接口（苹果CMS V10 标准格式）+ 真实海报 | ← **你在这里** |
| 2. 播放 | Media3 播放器、线路/剧集解析、换线路、进度记忆 | |
| 3. 打磨 | 搜索、设置落地、动画精修、错误兜底 | |

---

## 架构：四层，依赖单向向下

```
feature/   界面层：首页 / 播放器 / 设置     —— 只认 domain，不认任何实现
domain/    领域层：Movie/MovieDetail 模型 + MovieRepository 接口   —— 纯 Kotlin，零依赖
data/      数据层：苹果CMS 适配器 + 设置存储 + http 层   —— 实现 domain 的接口
core/      地基：设计系统 / 焦点系统 / 通用组件
```

**关键约定（这些是这个工程能长期不乱的原因）**

| 约定 | 为什么 |
|---|---|
| UI 只认 `domain` 的 `Movie` / `MovieRepository` | 换片源格式（苹果CMS → 其它）只动 `data/` + DI 两处，UI 一行不用改（阶段 1 实测：首页组件零改动） |
| 所有动画时长/曲线只从 `core/design/Motion.kt` 取 | 页面手感才会一致；"高级感"来自动效时机，不是库 |
| 所有可点组件用 `Modifier.tvFocus(shape)` | 一行拿到统一焦点动效（放大 + 金边 + 回弹） |
| 所有尺寸用 `rememberTvMetrics().dp()/.sp()` | 不同电视 dp 画布从 960x540 到 1920x1080 都有，按屏宽比例缩放才不翻车 |
| 颜色只在 `core/design/Color.kt` 定义 | 防止每个页面各写各的灰 |

### 三大底层能力

1. **焦点系统** `core/focus/TvFocus.kt`
   `Modifier.tvFocus(shape, onFocusChanged)`：放大 1.06 + 暖金描边 + 弹簧回弹。
   边框用 `drawWithContent` 在内容之后绘制 —— 这点很关键，否则边框会被海报背景盖住（很多自绘焦点组件踩这个坑）。

2. **动效规范** `core/design/Motion.kt`
   140 / 200 / 280ms 三档时长 + Apple 式贝塞尔缓动 + 焦点弹簧参数。
   页面里**不许**出现裸的 `tween(180)`。

3. **骨架屏** `PosterSkeleton`（首页内）
   加载时是"微光占位"，不是白屏、不是转圈 —— 电视上尤其重要。

---

## 片源接入（阶段 1）：怎么用

**应用不内置任何片源地址**（这是红线）。第一次装好后首页会显示一句引导，去「设置」填自己的接口地址即可。

- 地址格式：`http://你的站点/api.php/provide/vod/`（苹果CMS V10 标准接口；不写 `http://` 会自动补）
- 想先看界面长什么样：地址栏里填 `demo`，用内置演示数据（不联网）
- 保存后首页自动重新加载，**不用重启应用**（地址变化是数据流的一部分）

适配器处理掉的现实问题（都写在 `data/remote/`）：

| 问题 | 处理 |
|---|---|
| 同一字段有的站返回数字、有的返回字符串 | 全部按 `JsonElement` 读再自己转（`asText/asInt/asDouble`） |
| 老站点默认返回 XML | 每次请求显式带 `at=json` |
| GBK 编码（还常谎报 utf-8） | 按字节读 → 严格 UTF-8 解不动就退 GB18030 |
| 防盗链校验 UA / Referer | 统一伪装成浏览器 UA + 带站点根 Referer |
| 图片是相对路径 | 按接口域名补成绝对地址 |
| 简介是 HTML | 剥标签后再显示 |
| 播放地址 `线路$$$线路` + `集名$地址#` | `parsePlaySources()` 解析成 `PlayLine/Episode`（阶段 2 直接用） |

---

## 电视适配的关键决定（都是踩过坑的）

| 决定 | 原因 |
|---|---|
| `minSdk 24` / `targetSdk 28` | 电视能装 sdk23 的包 → 系统 API ≥ 23；navigation 2.10.2 硬性要求 minSdk ≥ 24。targetSdk 故意压低，避免高版本触发系统限制 |
| `compileSdk 37` | 2026 年 AndroidX 在 AAR 元数据里强制要求 ≥ 37（只影响可用 API，不影响装机） |
| 只出 `armeabi-v7a`（32 位） | 电视不支持 arm64，64 位包装上会崩 |
| 原生库只剩 2 个极小的（都带 v7a） | Compose 的 `libandroidx.graphics.path.so`（7KB）+ DataStore 的 `libdatastore_shared_counter.so`；`abiFilters` 保证只打 v7a，构建后有脚本自动检查 |
| `usesCleartextTraffic=true` | 在线接口和视频源大量是 `http://`，不开会白屏 |
| Leanback + 普通双入口 | 有些 TV 桌面只认其中一种，双保险 |
| `android:required="false"`（leanback feature） | 万一电视不认这个 feature，required=true 会让图标从桌面消失 |
| 启动主题纯黑 | 避免冷启动闪白屏 |
| 详情常驻右侧（不做独立详情页） | 布局不跳、不用"进详情再退出来"，按 OK 直接播 |
| 依赖注入用 Koin 而不是 Hilt | 没有注解处理器，构建更快、报错更好懂 |

---

## 出包流程

### 方式 A：本地一键构建（推荐，快）

本机已装好**便携工具链**（不用 Android Studio，不占系统）：

| 组件 | 位置 |
|---|---|
| JDK 17 | `C:\Users\cc\Tools\jdk17` |
| Android SDK | `C:\Users\cc\Tools\android-sdk` |
| Gradle 9.8.1 | `C:\Users\cc\Tools\gradle-9.8.1` |

```powershell
cd C:\Users\cc\Documents\默认项目\cinema-tv
.\scripts\build-local.ps1          # 编译 release APK
.\scripts\check-apk.ps1            # 红线检查（ABI / minSdk / Leanback 入口 / 体积）
```

编完 APK 路径会打印在最后一行（`app\build\outputs\apk\release\app-release.apk`），
拷进 U 盘插电视安装即可。

> **网络说明**：这台机器 `github.com:443` 不通（SSH 正常，所以推送没问题）。
> Gradle 官方分发地址会 307 跳到 github.com，所以本机用的是从镜像下好的独立 Gradle 分发，
> 仓库里的 `gradle-wrapper.properties` 保持官方地址不变（云端构建不受影响）。

### 方式 B：云端构建（备份 / 换电脑也能出包）

```
推到 GitHub main → Actions 自动编译+签名 → 下载 artifact → U盘装电视
```

> 首次安装如果被系统安全检测拦下：换个 U 盘安装方式重试，或改用 ADB（`adb install -r` 秒装）。

## 签名说明

- 签名文件：`keystore/release.jks`（PKCS12，别名 `cinema`）
- **已随仓库提交**：这样每次构建都用同一把钥匙，电视上的应用可以直接覆盖升级，不用卸载重装
- ⚠️ 前提是**仓库保持私有**。如果哪天要公开，先把 `keystore/` 删掉并重新生成钥匙

---

## 自动检查（每次云端构建都会跑）

`scripts/check-apk.sh`：
1. APK 里不能有 `arm64-v8a` / `x86` 原生库（有几个就崩几个）
2. 必须开启 `usesCleartextTraffic`
3. 体积超过 30MB 会警告

---

## 目录结构

```
app/src/main/kotlin/com/ichichuang/cinema/
├── MainActivity.kt              # 唯一 Activity：主题 + 导航
├── CinemaApplication.kt         # 依赖注入口
├── core/
│   ├── design/                  # 色板 / 动效规范 / 电视尺寸适配 / 主题
│   ├── focus/TvFocus.kt         # ⭐ 统一焦点系统
│   └── ui/                      # 通用组件：海报卡 / 海报图(Coil) / 详情面板 / 播放按钮
├── domain/
│   ├── model/Movie.kt           # 干净的影片模型（不含任何接口字段）
│   ├── model/MovieDetail.kt     # 详情：简介 + 播放线路/剧集
│   └── repository/MovieRepository.kt   # + CATEGORY_ALL
├── data/
│   ├── remote/MaccmsApi.kt      # 苹果CMS V10 适配器（字段解析全在这）
│   ├── remote/Http.kt           # OkHttp + UA/防盗链/GBK 处理
│   ├── settings/CinemaSettings.kt  # DataStore：接口地址
│   ├── MaccmsMovieRepository.kt # 接口 → 领域模型的翻译官
│   ├── SwitchableMovieRepository.kt # 按设置路由：真实片源 / demo
│   └── demo/                    # 内置演示数据（填 demo 时使用）
├── feature/
│   ├── home/                    # 首页（含 ViewModel：单一状态源）
│   ├── player/                  # 播放页（阶段2 接 Media3）
│   └── settings/                # 设置页（接口地址输入）
├── di/AppModule.kt              # 全应用唯一知道"实现是谁"的地方
└── nav/CinemaNavHost.kt         # 路由 + 转场动效
scripts/build-local.ps1          # 本地一键构建
scripts/check-apk.ps1            # 本地红线检查（Windows）
scripts/check-apk.sh             # 构建后红线检查（CI）
.github/workflows/build.yml      # 云端构建流水线
keystore/release.jks             # 签名钥匙（私有仓库）
```

---

## 踩坑记录（都是真金白银试出来的）

| 现象 | 原因 | 处理 |
|---|---|---|
| 报 `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0` | AGP 9 内置了 Kotlin 支持（自带 KGP），手动声明 KGP 属于非法 | 删掉 `kotlin.android` 插件；`kotlin.plugin.compose` 版本必须等于 AGP 自带 KGP 版本（AGP 9.4.1 → 2.2.10） |
| 报 `Error: Google Play requires that apps target API level 33 or higher. [ExpiredTargetSdkVersion]`（`lintVitalRelease` 失败） | AGP 9 把 lintVital 挂进 `assembleRelease`，而这条规则只管 Google Play 上架 | `android { lint { disable += "ExpiredTargetSdkVersion" } }`：只关这一条，其它 lint 错误照样卡构建 |
| 报 `Unresolved reference 'drawOutline'` | Compose 1.12 移除了 `androidx.compose.ui.graphics.drawscope.drawOutline` 扩展（已拆字节码确认，`DrawScopeKt` 里只剩 inset/transform） | 用 `DrawScope` 的**成员函数** `drawRoundRect` / `drawPath` 自绘描边（`core/focus/TvFocus.kt`） |
| 报 `requires libraries and applications that depend on it to compile against version 37 or later` | 2026 年的 AndroidX（Compose 1.12.1、navigation 2.10.2、core-ktx 1.19.1）在 AAR 元数据里强制要求消费方 compileSdk ≥ 37 | `compileSdk = 37`（只影响可用 API，不影响装机兼容性） |
| 报 `Your project path contains non-ASCII characters` | 项目目录是 `默认项目`（中文路径），AGP 在 Windows 上默认拒绝构建 | `gradle.properties` 里已开 `android.overridePathCheck=true` |
| `gradlew` 卡在 `Downloading https://services.gradle.org/...` | 该地址 307 跳 `github.com`，而本机 `github.com:443` 不通 | 本机用独立 Gradle 分发 `C:\Users\cc\Tools\gradle-9.8.1`；仓库配置保持官方地址（CI 不受影响） |
| 报 `Class was compiled with an incompatible version of Kotlin` / 2026 年的库读不了 | Coil 3.6.3 等库是用 Kotlin 2.4 编译的，而 AGP 9.4.1 内置的 KGP 是 2.2.10（旧编译器读不了新元数据） | 按 AGP 官方通道把 KGP 抬到 2.4.20：根 `build.gradle.kts` 里 `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") } }`，同时 `kotlin.plugin.compose` 版本必须改成同一个 2.4.20 |
| Koin 找不到 `androidContext` / `viewModel{}` | Koin 4.2 起 `koin-androidx-compose` 只有 8KB，不再传递依赖 `koin-android` | 两个依赖都显式写进 `build.gradle.kts` |
| Actions 页面打不开、artifact 下不了 | 同上：本机 `github.com:443` 不通（SSH 推送是通的） | 用本地构建出包，云端只做备份 |
