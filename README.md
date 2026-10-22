# 极简影院 · cinema-tv

面向电视遥控器操作的 Android 点播应用，围绕挑电影、选剧集与继续观看设计。历史实机记录为 Android 11 / SDK 30、32 位 ARM；这不代表所有 Android 电视均已兼容。

> **开源准备中，签名安全事件尚未关闭。** 项目计划正式开源；当前保持私有并冻结签名与发布。源码清理不等于旧签名密钥已经失效。公开仓库与恢复发布的验收条件见 [SECURITY.md](SECURITY.md)。尚无正式许可证，不把当前状态表述为已完成开源。

源码版本保持 **0.9.8 / versionCode 18**；本次整改不发布新 APK，不改包名，不卸载现有电视应用。

## 产品能力

海报墙、详情、分类与语言／地区／年份筛选；多来源苹果 CMS 接口与兼容配置导入；继续观看和播放进度；遥控快进退、换集、暂停与播放设置；扫码配置、图片缓存、只读设备诊断和经系统确认的更新安装。语言筛选不等于实际音轨保证，内容可用性取决于用户有权访问的上游。

## 开发

Kotlin / Jetpack Compose / TV Material / Media3 / Koin / OkHttp / Coil / DataStore。当前配置为 Gradle 9.8.1、AGP 9.4.1、Java 编译目标 17、compileSdk 37、minSdk 24、targetSdk 28，以构建文件为准。

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_repository_security.py
bash ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --no-configuration-cache
```

先配置本机 Android SDK。没有缓存时不要使用 `--offline`。开发包使用 `com.ichichuang.cinema.debug`，不能覆盖正式安装。`assembleRelease` 默认只生成未签名验证产物，不能安装或发布为正式更新。普通开发与 CI 均不需要正式签名凭据。

签名恢复前，`cinema.enableReleaseSigning=true` 会明确失败。不要在命令行、源码、日志或聊天中填写签名密码。详见 [开发指南](docs/DEVELOPMENT.md) 和 [发布指南](docs/RELEASE.md)。

## 架构与文档

`feature/` 维护界面，`domain/` 维护模型和接口，`data/` 维护来源、设置和更新实现，`core/` 维护设计、焦点和系统桥接，`di/` 与 `nav/` 维护注入和导航。部分界面仍直接依赖 data 类型，不能宣称分层重构已经完成。

| 文档 | 用途 |
| --- | --- |
| [SECURITY.md](SECURITY.md) | 安全事件、发布冻结和重新公开的门槛 |
| [DEVELOPMENT.md](docs/DEVELOPMENT.md) | 无正式凭据开发、构建和验证 |
| [RELEASE.md](docs/RELEASE.md) | 签名连续性、迁移验收和分发限制 |
| [OPEN_SOURCE_READINESS.md](docs/OPEN_SOURCE_READINESS.md) | 许可证、依赖审计与界面重构验收 |
| [TV_SETUP.md](docs/TV_SETUP.md) | 既有电视运维流程，不等于本次设备验证 |
| [RESOURCES.md](docs/RESOURCES.md) | 源码、工具、产物与私有证据的边界 |

`cinema-tv-releases` 是既有分发仓库；此次整改不更改其 APK、标签或清单。`.local/` 为被忽略的本地工具与私有证据，不能随源码发布。仓库内 FongMi 技能属于独立运维工作流，cinema-tv 不依赖 FongMi 或第三方桌面。
