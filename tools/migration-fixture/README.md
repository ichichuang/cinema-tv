# 隔离签名迁移预验证夹具

仅用于测试包 `com.ichichuang.cinema.migrationfixture`，不是生产迁移工具。纯 Java Activity 保存合成偏好 `marker=migration-synthetic-data` 和 `progress=12345`，记录 UID、版本、当前 signer 和历史证书。无生产依赖、订阅、网络权限或生产 APK 修改。

使用维护者现有 mise JDK、Android SDK 37 / Build Tools 36.0.0。`build_fixture.py --sdk "$ANDROID_HOME" --output "$MIGRATION_FIXTURE_DIR"` 要求新建仓库外目录；不会安装工具、读取生产凭据或写入分发仓库。输出目录权限 0700、可丢弃 A/B/C 密钥权限 0600；密码仅在子进程环境中，未持久化。再次实验应重新生成测试材料，不将其用于生产、公开下载或密钥恢复。

SDK 设置匹配生产 compile 37 / min 24 / target 28、Java target 17。夹具无 `.so`、与 emulator CPU 架构无关；生产包仅 armeabi-v7a，因此 emulator 成功也不覆盖电视的 ARM32 原生库或厂商安装验证。

签名选项显式使用 `--rotation-min-sdk-version 28`，旧 A `installed-data=true`，`rollback/shared-uid/permission/auth=false`。没有给 A rollback capability。[官方 apksigner 说明](https://developer.android.com/tools/apksigner) 和实际安装的 `rotate/sign --help` 均已核对；不能依赖默认 API 33 轮换行为。工具验证范围 API 24..30，每包另用 API 30..30 的结果确认当前 artifact signer。

| 场景 | versionCode | API 30 产物当前 signer | 本轮工具结果 | API 30 安装结果 |
| --- | --- | --- | --- | --- |
| 初装 A | 1001 | A | 验证通过 | BLOCKED：无 emulator |
| A→B lineage | 1002 | B | 验证通过 | BLOCKED：UID/数据/current signer 未验证 |
| 后续仅 B | 1003 | B | 验证通过 | BLOCKED：更新及保留历史未验证 |
| 仅 A 竞争更新 | 1004 | A | 有效测试 APK，版本更高 | BLOCKED：没有 PM 拒绝原因 |
| A→C 竞争 lineage | 1005 | C | 有效测试 APK，版本更高 | BLOCKED：没有 PM 拒绝原因 |

[artifact-report.json](artifact-report.json) 记录本次 APK 摘要、公开证书摘要及 SDK/选项。APK 和密钥留在仓库外；这些不是正式发布产物。当前 SDK 没有 emulator、system image 或 AVD，本轮没有安装 emulator 或连接任何电视，不能以 JVM、apksigner 或脚本存在替代平台测试。

## 平台执行与判定

在另行可用、明确指定的全新 API 30 emulator 上，运行 `run_matrix.py --adb "$ADB" --serial "$EMULATOR_SERIAL" --artifacts "$MIGRATION_FIXTURE_DIR"`。脚本只接受 `emulator-数字`，核对 qemu=1/API=30，并拒绝已有该夹具包的目标；所有 adb 命令包含 `-s`，不扫描网络、不重启服务器、不清数据、不卸载、不改变系统验证或权限。平台脚本因本轮无 emulator 而未执行。

先核对本地产物摘要，再依次初装/更新；每一步启动夹具并通过 `run-as` 读取合成观察。A→B 后必须 current=[B]、history={A,B}，同时 UID 和数据不变；后续 B 更新仍须 current=[B]。高版本 A-only 与 A→C 只有实际返回 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 且仍保留 B/1003/原 UID/数据时，才计为 signer 拒绝；版本、ABI、SDK、包损坏或其他错误必须失败，不能冒充签名拒绝。实际 PackageManager 原因与观察写入仓库外 `platform-report.json`，失败时也保留已取得的结果，不自动卸载。

## 支持政策和信任限制

API 24–27 不识别 v3 lineage。为测试兼容而存在于 A→B 包内的旧平台 A 签名，不构成在旧平台摆脱受损 A 的安全方案；工具验证通过也不等于该平台已迁移。生产 minSdk 仍为 24，维护者须明确决定旧平台是停止安全更新、采用经批准的重新安装迁移，还是其他有证据的政策；本轮不默默提高 minSdk、不保留旧钥作为未披露的正式方案。

[AOSP v3 文档](https://source.android.com/docs/security/features/apksigning/v3) 当前还明确不推荐 Android 12/API 31 及更早系统的密钥轮换；这使 API 30 实验与厂商电视验收更必要，而不是允许宣称兼容。lineage 只表达密钥链的技术关系，不证明合法所有权。生产根仍须独立引导，电视固件上的启动/播放/数据/后续更新/旧钥拒绝均待单独授权和验证。

复审修复了 fresh emulator 的缺包探测：使用成功返回空集合的 `pm list packages --user 0`，包存在、查询异常或非 emulator 均拒绝。合成数据只允许初装 versionCode 1001 初始化，后续缺失保持缺失使矩阵失败，不自动重建。`check_fixture_state.py` 使用既有 JDK/SDK 和内存偏好代理对真实 seeding 方法执行 3 项 JVM 逻辑检查；Python 工具边界测试也不连接设备。这些测试不能替代 PackageManager 安装或数据保留的真实平台证据。
