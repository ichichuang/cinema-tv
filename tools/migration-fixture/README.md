# 隔离签名迁移预验证夹具

仅用于测试包 `com.ichichuang.cinema.migrationfixture`，不是生产迁移工具。纯 Java Activity 保存合成偏好 `marker=migration-synthetic-data` 和 `progress=12345`，记录 UID、版本、当前 signer 和历史证书。无生产依赖、订阅、网络权限或生产 APK 修改。

使用维护者现有 mise JDK、Android SDK 37 / Build Tools 36.0.0。`build_fixture.py --sdk "$ANDROID_HOME" --output "$MIGRATION_FIXTURE_DIR"` 要求新建仓库外目录。输出目录权限 0700、可丢弃 A/B/C 密钥权限 0600；密码仅在子进程环境中，不持久化。再次实验应重新生成测试材料，不用于生产、公开下载或密钥恢复。

SDK 设置匹配生产 compile 37 / min 24 / target 28、Java target 17。夹具无 `.so`，与 emulator CPU 架构无关；生产包仅 armeabi-v7a，因此 emulator 成功不覆盖电视的 ARM32 原生库或厂商安装验证。

签名显式使用 `--rotation-min-sdk-version 28`，旧 A `installed-data=true`，`rollback/shared-uid/permission/auth=false`。未给 A rollback capability。[官方 apksigner 说明](https://developer.android.com/tools/apksigner) 和本机 `rotate/sign --help` 已核对；不依赖默认 API 33 轮换。五包均通过 API 24..30 工具验证，另以 API 30..30 确认当前 artifact signer。

## 2026-10-23 实际 API 30 结果

主机 arm64 / macOS 27.0，预检空闲 358 GiB、`kern.hv_support=1`。使用现有 SDK Manager，仅在仓库外安装官方稳定 emulator 37.2.12（build 16428233）和 `system-images;android-30;default;arm64-v8a` revision 2；`-accel-check` 返回 0 / Hypervisor.Framework。原有 `android-sdk-license` 记录原样复用，安装 stdin 为 DEVNULL，没有接受新协议、升级应用工具链或改全局配置。镜像 package.xml revision 为 2，原始 source.properties 写 1，此差异原样记录，未改镜像。官方工具参考：[SDK Manager](https://developer.android.com/tools/sdkmanager)、[加速检查](https://developer.android.com/studio/run/emulator-acceleration)。

指纹：`Android/sdk_phone_arm64/emulator_arm64:11/RSR1.210722.013.A4/10160917:userdebug/test-keys`。使用现有 platform-tools 37.0.1，独立 ADB server 5047，所有设备命令显式指定验证过的 `emulator-5580`。没有列举、连接或修改实体电视，原有 5037 服务未重启。

| 状态 / 操作 | 实际 PackageManager 结果 | 安装后独立读回 | 判定 |
| --- | --- | --- | --- |
| A/1001 → A→B/1002 → B-only/1003 | 三次 Success | UID 10130、合成 marker/progress 不变；当前 A→B→B；历史 A→A/B→B | PASS |
| 刚迁移至 B/1002：A-only/1004 | INSTALL_FAILED_UPDATE_INCOMPATIBLE | B/1002、原 UID、合成数据与 A/B 历史不变 | PASS |
| 刚迁移至 B/1002：竞争 A→C/1005 | INSTALL_FAILED_UPDATE_INCOMPATIBLE | B/1002、原 UID、合成数据与 A/B 历史不变 | PASS |
| 后续更新至 B/1003：A-only/1004 | INSTALL_FAILED_UPDATE_INCOMPATIBLE | B/1003、原 UID、合成数据与 B 历史不变 | PASS |
| 后续更新至 B/1003：竞争 A→C/1005 | INSTALL_FAILED_UPDATE_INCOMPATIBLE | B/1003、原 UID、合成数据与 B 历史不变 | PASS |
| 正对照 A/1001 → A-only/1004 | Success | A/1004、UID 与合成数据不变 | PASS |
| 正对照 A/1001 → A→C/1005 | Success | C/1005、UID 与合成数据不变，历史 A/C | PASS |

共 7 个独立全新 AVD、21 次实际安装尝试：17 次成功、4 次签名拒绝。每个负向候选都从自己的全新 AVD 独立经过对应合法更新，没有共享可污染的负向测试状态。四次完整拒绝原因为：`INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package com.ichichuang.cinema.migrationfixture signatures do not match previously installed version; ignoring!`。没有将版本降级、ABI、SDK、坏包或其他安装错误算作签名拒绝。

[artifact-report.json](artifact-report.json) 是既有结果文件，现包含本次新生成的五包 SHA-256、公开测试证书指纹、lineage 能力、镜像/工具身份，以及每次 PM 结果与 UID/version/current/history/合成数据读回。APK、私钥、初始诊断和原始日志仅保存在仓库外。测试材料不是正式发布产物。

## 复现和判定边界

为七个场景各创建一个唯一命名的全新 AVD，使用上述官方镜像、独立 `ANDROID_USER_HOME` / `ANDROID_AVD_HOME` 和显式 emulator 端口。通过 `getprop` 与 `emu avd name` 核对目标后，每个 AVD 仅运行一个场景：

```sh
python3 tools/migration-fixture/run_matrix.py --adb "$ADB" --serial "$EMULATOR_SERIAL" \
  --artifacts "$MIGRATION_FIXTURE_DIR" --scenario migration
```

其余场景为 `rotation-old-a`、`rotation-competing`、`followup-old-a`、`followup-competing`、`control-old-a`、`control-competing`；每次均须换全新 AVD。脚本拒绝已有夹具包、非 emulator 或非 API 30 目标，核对本地产物摘要；不扫描网络、不卸载、不清数据、不改变系统验证或权限。每场景结果写入仓库外 `platform-report-场景.json`。出现意外接受或其他错误时保留 PM 原因和已取得的观察，不自动修复状态。

本次初始诊断发现 Activity 在 onCreate 立即结束时 `am start -W` 会挂起。该部分运行和 AVD 已保留，未计入最终矩阵。执行器现在使用无 `-W` 的启动和随机 nonce，有限等待本次观察；不删除旧观察或重建偏好，避免把旧文件当成成功。命令设置超时。Python 回归覆盖独立状态/候选、意外旧钥接受、错误拒绝原因、数据变化、正对照与过期观察；`check_fixture_state.py` 继续执行真实 seeding 方法的 3 项 JVM 检查。这些脚本测试与实际平台结果分别记录。

## 认证候选 Android 运行时检查

迁移矩阵完成后，在另一个全新 API 30 AVD 上运行测试包 `com.ichichuang.cinema.authruntime`。`check_authenticated_runtime.py` 直接打包 `:app:compileDebugKotlin` 产出的真实候选 class、下载文件工具和现有 Kotlin stdlib；记录来源和 class 摘要，不复制或简化候选实现。Java Activity、TLS/PM 适配器都在本工具目录，测试 TLS 证书、元数据根、私钥和 APK 在仓库外或测试应用私有目录，不进入生产 source sets。

先用既有 Gradle wrapper 编译候选，再在已核实且无夹具包/测试包的独立 AVD 上执行：

```sh
python3 tools/migration-fixture/check_authenticated_runtime.py --sdk "$ANDROID_HOME" \
  --output "$AUTH_RUNTIME_DIR" --artifacts "$MIGRATION_FIXTURE_DIR" \
  --adb "$ADB" --serial "$EMULATOR_SERIAL" --avd-name "$OWNED_AVD_NAME"
```

`AUTH_RUNTIME_DIR` 必须是仓库外尚不存在的目录。脚本复用 mise 的 JAVA_HOME、现有 SDK/stdlib 和 OpenSSL；不安装依赖。仅绑定主机 127.0.0.1:9443 的受控 HTTPS 端点，通过所选 emulator 的 adb reverse 访问，保持 TLS 主机名验证；结束后移除自己创建的 reverse 映射并关闭端点。若端口已占用则失败，不关闭其他服务。安装的夹具和 harness 均为测试身份。

最终 9 项 Android 检查通过：AndroidOpenSSL 的真实 RSA 验签；认证失败在 permit/下载前终止及 production() 拒绝；16,384 字节的声明大小与流式读取上限；真实 HTTPS 半途断开后的 partial 清理；实际进程重启后的重放 floor 和相同 pending 重试；重试的真实 APK 验证；有效签名的低序号/不同同序号载荷拒绝；PM 当前 signer B 与历史 A/B 的区别及错用历史集合的拒绝/文件清理；注入路径冲突导致存储写入失败并拒绝授权。受控端点没有 legacy 请求。进程 PID、端点计数和细项见既有 JSON。

Build Tools 36.0.0 的 D8 对 Kotlin 2.4 元数据重写有警告；DEX、安装和全部非反射运行时断言成功，未升级工具或忽略失败。存储测试是注入故障，不是设备存储损坏或掉电持久性证明。候选测试不证明生产保护：生产网络集成、信任根预置、DI、启动恢复适配和激活仍未接入，生产常量保持关闭。

## 支持政策和信任限制

API 24–27 不识别 v3 lineage。测试包内旧平台的 A 签名不构成在旧平台摆脱受损 A 的安全方案；生产 minSdk 仍为 24。旧设备支持政策待维护者明确，不能静默提高 minSdk 或继续把受损 A 当作安全恢复。

[AOSP v3 文档](https://source.android.com/docs/security/features/apksigning/v3) 仍不推荐 API 31 及更早系统的密钥轮换。本次 emulator 结果限于上述官方镜像。lineage 不证明合法所有权；独立信任引导、生产密钥保管、离线恢复和厂商电视上的安装/数据/后续更新/旧钥拒绝/启动播放均待维护者分别批准与验证。PR 保持 Draft、未合并，正式签名和发布冻结；本轮不关闭安全事件。
