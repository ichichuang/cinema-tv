# 开发与本地验证

依据 2026-10-22 仓库和本机盘点。包名为 `com.ichichuang.cinema`；版本、依赖和行为以实际源文件为准。

## 环境

| 项目 | 当前配置或本机观察 | 依据 |
| --- | --- | --- |
| Gradle | 9.8.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.4.1，内置 Kotlin 模式 | 根及 app 构建脚本 |
| Kotlin／Compose 插件 | 均为 2.4.20 | `build.gradle.kts` |
| Java | 编译目标 17；CI 使用 JDK 17；本机已有 mise Temurin 21.0.11+10 LTS | 构建脚本、CI、本机 mise 清单 |
| SDK 平台 | compileSdk 37；本机目录 `android-37.0`，revision 2 | SDK `source.properties` |
| Build Tools | 本机 36.0.0，不是新加的 Gradle 固定配置 | SDK 元数据 |
| Command-line Tools／Platform Tools | 本机 22.0 ARM64／37.0.1 | SDK 元数据 |

当前没有项目级 mise 配置，不调整全局版本。README 命令选择已安装的 JDK；CI JDK 17 与本机 JDK 21 是不同环境，不混用验证结论。

新机器从 [Android 官方工具](https://developer.android.com/tools)、[SDK Manager](https://developer.android.com/tools/sdkmanager)、[Adoptium](https://adoptium.net/temurin/releases/) 和 [Gradle 官方分发](https://services.gradle.org/distributions/) 准备对应工具与许可。不要为构建关闭 macOS 安全机制或安装第二套版本管理器。

## 本机路径与构建

以下路径相对仓库根目录：

- `.local/toolchain/android-sdk/`：SDK、工具和原有许可，Git 忽略。
- `.local/toolchain/android-user/`：原 SDK 用户状态，仅本机保留。
- `.local/consolidation/`：清单、校验和本次构建结果，包含本地信息，不提交。
- `keystore/release.jks`：原有受跟踪签名库，原路径和签名行为保持。

`~/.gradle/` 现有分发和依赖缓存保持原位。克隆仓库不会取得 `.local/`；其他机器可使用自己的 SDK 环境变量或被忽略的 `local.properties`，不要提交个人绝对路径。

从仓库根目录执行 [README 的构建命令](../README.md#开发与架构)。`--offline` 要求依赖缓存已就绪；缺项时记录具体依赖，再决定是否允许联网，不循环下载或更换版本。构建输出为 `app/build/outputs/apk/release/app-release.apk`，采用既有 release 签名配置。

## APK 验证

```sh
APK=app/build/outputs/apk/release/app-release.apk
BT="$PWD/.local/toolchain/android-sdk/build-tools/36.0.0"
"$BT/aapt2" dump badging "$APK"
"$BT/aapt2" dump xmltree --file AndroidManifest.xml "$APK"
mise exec java@temurin-21.0.11+10.0.LTS -- "$BT/apksigner" verify --print-certs "$APK"
shasum -a 256 "$APK"
unzip -l "$APK"
```

检查包名、版本、minSdk 24、targetSdk 28、Leanback 入口、横幅、明文流量实际值、安装权限和 FileProvider。实际 0.9.8 发布包和本机构建均有 `lib/armeabi-v7a/libandroidx.graphics.path.so`、`lib/armeabi-v7a/libdatastore_shared_counter.so`；旧文档及源码注释的「无原生库」不能作为 APK 事实。逐项核对目标 ABI，30 MiB 是现有脚本的警告阈值。

`scripts/check-apk.sh` 是 Linux CI 脚本，使用 `stat -c`，不能直接当 macOS 检查入口。它只覆盖部分 ABI、属性存在性和体积，不完成签名或设备验收。检查通过不表示声音、画面、安装与遥控器已实测。

## 现有代码约定

保留 `feature / domain / data / core / di / nav`。内容接口在 domain，实现位于 data；部分界面直接引用设置实现，不能宣称依赖已严格单向。颜色、动效、尺寸分别复用 `Color.kt`、`Motion.kt`、`TvMetrics.kt`，焦点使用现有 `tvFocus`。

跨来源影片以 `uniqueKey` 区分，不只认数字 ID；返回时保留焦点与滚动。分类来自接口，缺字段与服务器忽略筛选不同；语言元数据不能替代听音。播放器由 ViewModel 持有，后台／退出保存进度，换线路延续进度。

## Windows 与 CI

`build-local.ps1`、`check-apk.ps1` 仍保留历史 Windows 便携路径；不作为当前 Mac 环境说明，也未在本次改写。保留 PowerShell 原有 UTF-8 BOM。

推送 main 会运行 `Build TV APK`，构建 release、执行 Linux 检查并上传短期 Actions artifact；它不自动发布分发仓库或新标签。本次迁移只进行一次离线构建验证 SDK 新位置，实际结果见 `.local/consolidation/build-result.json`；没有连接电视。
