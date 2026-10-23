# 极简影院 · cinema-tv

面向电视遥控器操作的 Android 点播应用，围绕挑电影、选剧集与继续观看设计。历史实机记录为 Android 11 / SDK 30、32 位 ARM；这不代表所有 Android 电视均已兼容。

> **仅源码开源准备中，签名安全事件尚未关闭。** 原创部分已获维护者批准采用 [Apache License 2.0](LICENSE)，代码及六张图片的再分发权已由维护者确认。当前保持私有；独立离线恢复、干净历史和受控 Public 操作尚待完成。源码公开与生产 APK 迁移分别验收，签名与发布继续冻结，本轮不提供生产 APK 下载。验收条件见 [SECURITY.md](SECURITY.md)。

源码版本保持 **0.9.8 / versionCode 18**；本次整改不发布新 APK，不改包名，不卸载现有电视应用。

## 产品能力

海报墙、详情、分类与语言／地区／年份筛选；多来源苹果 CMS 接口与兼容配置导入；继续观看和播放进度；遥控快进退、换集、暂停与播放设置；扫码配置、图片缓存、只读设备诊断和经系统确认的更新安装。语言筛选不等于实际音轨保证，内容可用性取决于用户有权访问的上游。

## 开发

Kotlin / Jetpack Compose / TV Material / Media3 / Koin / OkHttp / Coil / DataStore。当前配置为 Gradle 9.8.1、AGP 9.4.1、Java 编译目标 17、compileSdk 37、minSdk 24、targetSdk 28，以构建文件为准。

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_repository_security.py
bash ./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest --no-daemon --no-configuration-cache
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
| [OPEN_SOURCE_READINESS.md](docs/OPEN_SOURCE_READINESS.md) | 干净源码、加密恢复、同名重建及公开验收 |
| [TV_SETUP.md](docs/TV_SETUP.md) | 既有电视运维流程，不等于本次设备验证 |
| [RESOURCES.md](docs/RESOURCES.md) | 源码、工具、产物与私有证据的边界 |
| [整改计划](docs/REMEDIATION_PLAN.md) / [审计结果](docs/SECURITY_REMEDIATION_REPORT.md) | 本轮范围、已执行验证与尚未通过的门槛 |
| [签名迁移评审](docs/SIGNING_MIGRATION.md) | Android 11、独立信任引导及逐设备原位升级要求 |
| [第三方材料](docs/THIRD_PARTY_NOTICES.md) / [贡献说明](CONTRIBUTING.md) | 许可、第三方署名与开发约束 |
| [认证更新候选](docs/AUTHENTICATED_UPDATES.md) | 固定签名字节、重放/时钟/重试规则；生产关闭 |
| [维护者待决事项](docs/OWNER_DECISIONS.md) | 许可、权属、离线保管、迁移与公开路线的集中决策 |

最终分发采用**唯一 `ichichuang/cinema-tv` 仓库**：`main` 保存源码、`update/version.json` 与 `update/sources.json`，未来经过审批的正式 APK 上传到本仓库的 **GitHub Releases**，使用不可移动版本标签和独立的 SHA-256 校验。独立的 `cinema-tv-releases` 仅作为**旧版 0.9.8 电视应用的临时兼容入口**：已安装版本仍使用其硬编码更新/推荐源地址，直接删除会中断这些入口。仓库合并、历史清理、正式开源与生产签名迁移分别验收；此次只迁移未来源码默认地址，**不发布 APK、不删除旧仓库、不解除签名冻结**。`.local/` 为被忽略的本地工具与私有证据，不能随源码发布。仓库内 FongMi 技能属于独立运维工作流，cinema-tv 不依赖 FongMi 或第三方桌面。

本仓库目前没有 GitHub Release APK。`update/version.json` 明确标记冻结并不提供下载地址，避免指向旧仓库或虚构尚未发布的文件；恢复发布时须经认证的候选和设备验收。`update/sources.json` 是空目录，不内置真实服务推荐；用户可在设置中配置自己有权访问的服务，已有设备设置和来源不会被修改。第三方许可与通知见 [THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md)。
