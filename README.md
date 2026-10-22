# 极简影院 · cinema-tv

面向电视遥控器操作的 Android 点播应用，围绕「挑电影、选剧集、继续观看」设计。主要适配对象为 FFALCON 65S78A（雷鸟鹏 7 2026 款）；历史实机记录为 TCL MT5879、Android 11 / SDK 30、32 位 ARM，不能据此保证所有 Android 电视兼容。

当前源码和发布清单均为 **0.9.8（versionCode 18）**，最近改动为设备诊断的系统设置入口与遥控焦点修复。构建使用 compileSdk 37，安装最低 API 24，targetSdk 28；当前 APK 含两份 armeabi-v7a 原生库，Media3 使用系统解码器。

## 产品能力

- 海报墙、详情、分类与语言／地区／年份筛选；缺少标注时可降级，「国语」筛选不等于实际音轨保证。
- 最多同时启用 8 个苹果 CMS 来源，保存浏览位置、来源选择和播放进度；可从 TVBox 配置提取兼容 CMS 接口，不执行任意爬虫脚本。
- 左右快进退、上下换集、OK 暂停／继续、菜单或长按 OK 打开播放设置。搜索输入界面已从 0.8.0 起移除。
- 手机扫码配置、推荐来源、图片缓存管理、只读设备诊断及应用更新下载与系统安装确认。

APK 不内置点播内容地址；推荐清单由发布仓库提供，用户选择有权使用的来源。可用性、节目质量和音轨取决于上游。

## 开发与架构

Kotlin / Jetpack Compose / TV Material / Media3 / Koin / OkHttp / Coil / DataStore。当前 Gradle 9.8.1、AGP 9.4.1、Kotlin 与 Compose 插件 2.4.20，以实际构建文件为准。

| 目录（相对 `app/src/main/kotlin/com/ichichuang/cinema/`） | 职责 |
| --- | --- |
| `feature/` | 首页、播放、设置、诊断和更新界面 |
| `domain/` | 业务模型和接口 |
| `data/` | CMS、聚合、导入、设置和更新实现 |
| `core/` | 设计、焦点、尺寸与系统安装桥接 |
| `di/`、`nav/` | 注入与导航 |

内容访问以领域接口为中心；现有设置和播放器也直接引用部分 data 类型，并非严格的层间隔离。复用 `Color.kt`、`Motion.kt`、`TvMetrics.kt` 和 `tvFocus` 维护视觉与遥控行为。

macOS 复用 mise 和已有 SDK，无需 Android Studio。本机现有 JDK 为 Temurin 21.0.11，CI 用 JDK 17，Java 编译目标仍为 17。详细环境与 APK 验证见 [开发指南](docs/DEVELOPMENT.md)。

```sh
# 在仓库根目录，要求本地 SDK 与 Gradle 依赖缓存已就绪
mise exec java@temurin-21.0.11+10.0.LTS -- env \
  ANDROID_HOME="$PWD/.local/toolchain/android-sdk" \
  ANDROID_SDK_ROOT="$PWD/.local/toolchain/android-sdk" \
  ANDROID_USER_HOME="$PWD/.local/toolchain/android-user" \
  ./gradlew :app:assembleRelease --offline --no-daemon --console=plain
```

输出为 `app/build/outputs/apk/release/app-release.apk`。本地／CI 构建不替代实体电视的画面、声音和遥控器验收。

## 源码与分发

[cinema-tv 源仓库](https://github.com/ichichuang/cinema-tv) 维护源码、文档和技能；[cinema-tv-releases](https://github.com/ichichuang/cinema-tv-releases) 维护已发布 APK、固定版本标签、version.json 和 sources.json。源码的 update 目录为发布准备副本。

应用依次读取更新地址，采用第一个解析成功的清单；比较 versionCode、下载并核对包身份和可取得的签名指纹，再交系统安装。CDN 可能缓存旧清单，不保证立即更新或静默安装。实际边界见 [发布指南](docs/RELEASE.md)。

现有 `keystore/release.jks` 已受 Git 跟踪，构建配置含原签名凭据；本次保持签名连续性，未删除、轮换凭据或改写历史。源仓库当前为私有；改变可见性须另行处理。旧 `publish-to-repo.ps1` 含强制改标签与强推，**不要运行**。

## 文档与电视运维

| 文档 | 用途 |
| --- | --- |
| [DEVELOPMENT.md](docs/DEVELOPMENT.md) | 工具、构建、验证与 Windows 脚本边界 |
| [RELEASE.md](docs/RELEASE.md) | 签名、版本、分发协议和旧发布脚本风险 |
| [TV_SETUP.md](docs/TV_SETUP.md) | 授权 ADB、FongMi、TCL 特例与恢复 |
| [RESOURCES.md](docs/RESOURCES.md) | 资源归属、校验摘要、历史证据与迁移索引 |

仓库技能：[.agents/skills/fongmi-tv-setup/SKILL.md](.agents/skills/fongmi-tv-setup/SKILL.md)。在本仓库启动新 Codex 会话后输入：

```text
$fongmi-tv-setup 电视授权 ADB 地址为 <电视IP>:<端口>。请安装或复用 FongMi，整理中文家庭直播，保留现有应用和系统设置，只做少量抽查。
```

FongMi 提供独立直播工作流，Aerial 只是可选的既有桌面；**cinema-tv 不依赖二者**。SDK 和私有证据在被忽略的 `.local/`，不随克隆取得；已发布 APK 从分发仓库获取。
