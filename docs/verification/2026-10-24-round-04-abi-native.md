# Round 04 ABI 与原生存储验证记录

**NATIVE_STORAGE_TESTS_EXECUTED：34 项实际执行，34 通过，0 失败，0 跳过，0 未运行。** 证据限定为新建 API 30 ARM64 AVD 上的候选存储与合成记录保留测试，不代表 ARMv7 实机、生产迁移、整个 T03 / M02 或 A01–A24 验收。

## 范围与起点

用户明确选择按附件执行 Round 04 后开始执行。沿用既有 `cinema-contract-foundation` 工作树及 `codex/contract-foundation`，起始 HEAD 为 `541b7ce5370693c5f96c848bb0cd3e8ec8cbc7b5`，暂存、未暂存及未跟踪改动均为空。原 main 保持 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd` 且干净。

ZIP 三项 SHA-256 均匹配，独立 instruction / plan 与包内字节一致。计划 SHA-256 为 `67c5a1c73022423ed574ec3ebe9f57d27b151e16790c14dfd0dbc6bffff096cc`，instruction 为 `5078e8863b44b670e800d6062383d1a9e58ae839f5bec43c383f649f0df3dde9`。原计划含个人路径，原字节保存在忽略的 `.local/evidence/round-04/2026-10-24-round-04-abi-native-verification.md`，不提交违反既有 index 安全规则的副本。

执行前已记录全部 211 个跟踪文件的内容摘要。schema version 1 摘要为 `14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959`。源码清点为 `CinemaDatabaseTest` 15 项、`LegacyUserDataImportTest` 19 项，无 Ignore；最终 runner 的全部方法名与这 34 项一一对应。

## 执行状态

R04-1–R04-3 的 ABI 路线、产物隔离和原生执行已验证；R04-4 无存储缺陷可修复，未修改业务实现或测试。R04-5 主机回归及独立审阅已完成；最终 index 安全检查 / 本地提交另在交付段记录。

## ABI 根因、依赖与 opt-in 契约

主机为 macOS 27.0 / arm64，约 342 GiB 可用磁盘；`kern.hv_support=1`，emulator 37.2.12 / 16428233 的 `-accel-check` 退出 0，确认 Hypervisor.Framework。已有 `system-images;android-30;default;arm64-v8a` 的实际 build.prop 为 API 30、`abilist=arm64-v8a`、`abilist32=`。AVD 列表及共享 ADB server 的设备清单均为空。未下载额外镜像或升级工具链。

普通 Debug / androidTest 的 property-off 重建成功，70 个任务实际执行。实际普通 Debug 和既有 unsigned Release 的 JNI 均只有 `armeabi-v7a/libandroidx.graphics.path.so`、`armeabi-v7a/libdatastore_shared_counter.so`；测试 APK 无 JNI。三个实际 manifest 的应用身份分别为 `com.ichichuang.cinema.debug`、`com.ichichuang.cinema.debug.test`、`com.ichichuang.cinema`；runner 为 `androidx.test.runner.AndroidJUnitRunner`，target 为 `.debug`。

实际 Gradle 缓存 AAR：`androidx.graphics:graphics-path:1.0.1` 和 `androidx.datastore:datastore-core-android:1.2.1` 均包含各自同名真实 `arm64-v8a` 库；ELF class 2、machine 183（AArch64），ARMv7 对照为 class 1、machine 40。未重新标记或生成伪造库。每个 AAR 与 ELF 摘要保存在 `jni-dependencies.json`。

本机仅有的已验证镜像与普通 APK ABI 无交集；官方[加速矩阵](https://developer.android.com/studio/run/emulator-acceleration)规定 ARM64 主机使用 arm64-v8a 加速镜像。本轮不额外下载 ARMv7 / x86 镜像，不把未验证路线说成普遍不可能。选择现有 ARM64 镜像和真实依赖的 Route B。

已核对 [AGP 9.4 Ndk](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/Ndk) 及 [ApplicationBuildType](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/ApplicationBuildType) 文档，并反汇编本地 9.4.1 `MergedNdkConfig.append`：ABI 使用 `Set.addAll` 合并，不能假设 debug 设置会替换 defaultConfig。最小改动将原 ABI 约束明确放到已有两个 buildType：Release 永远 ARMv7，Debug 默认 ARMv7，仅 opt-in 时 ARM64，不增加 flavor 或第二份存储实现。

opt-in 契约为 `-Pcinema.storageTestArm64=true`，默认关闭。仅允许精确请求 `:app:assembleDebug`、`:app:assembleDebugAndroidTest`；其他根任务（包括 Release、bundle、publish、connected / install 和缩写）全部拒绝，另检查实际 task graph 不含 release / publish 或 `bundleDebug` 分发任务，允许原有编译用 classes bundle。使用现有 `.debug` 身份：原始 fixture 明确断言该包名，保留未修改的测试源码优先于增加新包名；仅安装到新建且身份核实的一次性 AVD。

修改前使用 opt-in 参数构建仍得到 ARMv7，打包断言实际失败（expected arm64-v8a / actual armeabi-v7a）。这是打包前置条件 RED，不是 Room 行为失败。修改前 Release `--dry-run` 尚未拒绝参数，作为门禁 RED，不生成或安装 Release。

初次 opt-in 构建因作者的 task graph 检查将正常编译任务 `bundleDebugClassesToCompileJar` 误判为分发 bundle 而在任务执行前失败。根据实际任务名将检查限定为 `bundleDebug` 分发任务，保留所有 Release / publish 检查及精确根任务白名单；这是本轮构建门禁错误，非既有问题或原生存储失败。


## 产物隔离与门禁实测

opt-in 构建成功，70 tasks（2 executed，68 up-to-date）；实际测试 APK 与普通测试 APK 字节相同。ARM64 app 的全部 482 个非 JNI ZIP 条目与新鲜普通 Debug 字节一致，包括 manifest、DEX 和资源。两份打包 ARM64 `.so` 与原始依赖 AAR 条目逐字一致，不仅核对目录名。

关闭 property 后独立重建普通 Debug、unsigned Release 和 androidTest，118 tasks（7 executed，111 up-to-date）；最终完整重跑后又重新检查同一批普通产物。实际三个 manifest、JNI 条目和每份 ELF 内容均与本轮修改前基线一致，最终整个 APK 摘要也一致。正常 SDK、包名、版本及正式签名冻结均保持。

| 实际产物 | JNI / 身份 | 最终 SHA-256 |
| --- | --- | --- |
| 普通 Debug | 仅 armeabi-v7a；`com.ichichuang.cinema.debug`，18 / 0.9.8-debug | `7603b07a1f6c4e794fc482bc58505323c8c2197bc936a3cf34614be4410a6c22` |
| 普通 androidTest / ARM64 配对 test | 无 JNI；`com.ichichuang.cinema.debug.test`；target `.debug` | `bc579fbc69b6462abb7260a6db7f3970c8e0687933ded565403b0a19f5f8a971` |
| 正常 unsigned Release | 仅 armeabi-v7a；`com.ichichuang.cinema`，18 / 0.9.8 | `af286f0bb0a67524a1d36c02381ab9e1ea1dfb5e79fa337b7ad87ca07ca370e1` |
| opt-in Debug | 仅 arm64-v8a；`.debug`，18 / 0.9.8-debug | `980dff5923e9b21ac4ce34ef98214ffd3c19129760772d4358422054dcb7b48d` |

SDK tooling 从实际 metadata 导出 APK 路径，使用 aapt 读取 manifest / runner / min 24 / target 28；ELF class 与 machine 另做二进制核验。apksigner 对普通 Debug / test 均退出 0，验证 v2 开发签名；Release 退出 1，报告未签名，不作为安装产物。`releaseSigningFrozen=true` 及凭据控制未改。

三项负向检查均确认预期中文门禁、非零退出且没有任务执行；原 Release 文件摘要保持：

- opt-in + `:app:assembleRelease`：配置阶段拒绝。
- opt-in + `:app:bundleDebug :app:publish`：根任务白名单拒绝。
- 仅请求允许的 `:app:assembleDebug`，通过忽略目录的 init script 将其依赖指向 `:app:assembleRelease`：真实 task graph 拒绝。最初探针脚本误用 Gradle `project()` 接收者导致自身初始化错误，未计为门禁通过；改用 `rootProject.project()` 后才取得上述有效结果。

opt-in APK 的独立副本保存在 `.local/evidence/round-04/optin/`，安装前再次核对其实际 SHA-256。之后的 property-off 构建没有覆盖实际受测副本。

## 唯一 AVD、实际执行与数据边界

新建 AVD：`cinema-storage-20261024-db502d6c`；owner 为本轮候选存储验证。使用既有 `system-images;android-30;default;arm64-v8a`，独占忽略目录下的新 AVD home / data path，无 force 覆盖。主机端口 5562 / 5563 创建前检查空闲；serial 为 `emulator-5562`。以 pixel_2 硬件 profile、软件图形、无窗口 / 音频 / 快照启动，实测 21.20 秒完成启动。这是存储测试目标，不是 Android TV 兼容性目标。

每次两项安装、instrumentation 和最后关闭前均核验 `emu avd name`、`ro.kernel.qemu=1`、API 30、`abilist=arm64-v8a`、空 `abilist32` 和 `sys.boot_completed=1`。命令固定 `adb -H 127.0.0.1 -P 5037 -s emulator-5562`，未使用 connected task、未将 ANDROID_SERIAL 当作隔离证明。两次 `install` 均返回 Success，之后实际执行：

```sh
adb -H 127.0.0.1 -P 5037 -s emulator-5562 shell am instrument -w -r \
  com.ichichuang.cinema.debug.test/androidx.test.runner.AndroidJUnitRunner
```

完整输出的 shell exit 为 0，runner 为 `INSTRUMENTATION_CODE: -1`，`OK (34 tests)`，测试耗时 1.335 秒。逐项解析 34 个 start 和对应 terminal result，34 个 code 0；无重复、未知或遗漏方法。executed 34、passed 34、failed 0、skipped 0、not run 0。不是仅凭进程退出或末行文字推断数量。

测试继承原 `NativeStorageFixture`，只使用目标 `.debug` app-private storage 中 synthetic UUID 文件。`reopen()` 真正关闭数据库并通过原 `CinemaDatabaseFactory.open` 创建新实例；未改为内存数据库、mock 或第二份 DAO。测试后用已核验 serial 正常关闭此 AVD，emulator 退出 0；共享 ADB server 保持运行，结束时设备清单为空，私有 AVD 文件保留供复核。

| 实际断言覆盖 | 方法依据与边界 |
| --- | --- |
| 文件持久化、重开与键编码 | `file_backed_values_survive_close_and_reopen`、`opaque_utf16_keys_and_payloads_survive_close_and_reopen` 核对文件、新实例与读回值；不等于进程被杀或断电 |
| SQL 约束与 schema 保留 | 两项 `actual_*`、`staging_rows_without_a_batch_cannot_commit`、`unsupported_database_version_does_not_reset_records` 在真实 SQLite 上核对列、外键 / 唯一 / 非空及保留旧行 |
| 用户记录隔离 | 独立 browse context、收藏幂等、cache eviction、损坏 / 不支持负载保留及空 / 不透明 playback 键读回；没有实现播放 commit 仲裁 |
| 合成 typed DataStore | `legacy_files_are_unchanged` 对真实合成文件逐字比较，`every_entry_is_accounted_for_without_overwriting_user_records` 核对每条类型 / 原值及既有收藏；fixture 包含 String / Int / Long，不扩张为真实用户迁移 |
| 批次幂等与摘要 | 相同内容、新捕获时间、变更内容、Unicode 顺序、不同 UTF-16 内容、marker 与原行不一致均有真实数据库断言 |
| 回滚、取消与确认丢失 | marker 前 / 后注入 IOException、提交前实际取消均重开核对无残留；提交后 IOException / 取消保留已提交行并通过幂等重试对账 |
| 受控 I/O / 存储失败 | 损坏源文件读取失败、触发器拒绝写入、SQLiteFullException 注入；没有填满磁盘或制造真实断电 |

本轮 34 项均通过，没有实际存储缺陷可进入 R04-4，不新增 / 修改测试或业务代码，不伪造行为 RED。构建门禁修正及测试探针错误如上分别记录。

## 普通构建与主机回归

沿用 mise Temurin `21.0.11+10.0.LTS`（Java target 17）、Python `3.13.3` 与既有 SDK。下列符号变量代表实际已有 SDK，不提交个人路径；完整命令及退出信息在忽略证据内。

```sh
ANDROID_HOME="$CINEMA_ANDROID_SDK" ANDROID_SDK_ROOT="$CINEMA_ANDROID_SDK" \
  mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  -Pcinema.storageTestArm64=true --no-daemon --no-configuration-cache --console=plain
mise exec -- python3 -m unittest discover -s scripts/tests -v
ANDROID_HOME="$CINEMA_ANDROID_SDK" ANDROID_SDK_ROOT="$CINEMA_ANDROID_SDK" \
  mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
  :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease \
  :app:dependencies --configuration debugRuntimeClasspath \
  --rerun-tasks --no-daemon --no-configuration-cache --console=plain
```

| 检查 | 实际结果 |
| --- | --- |
| Python | 62/62，退出 0 |
| 应用单测 XML | 12 suites、124 tests；0 failure / error / skipped |
| property-off 最终完整 Gradle | 135 tasks，135 executed，退出 0；包含实际依赖图，54.15 秒 |
| Debug Lint | 0 Error、21 Warning、1 Hint |
| Release Lint | 0 Error、22 Warning、1 Hint |
| manifest / ABI / ELF / schema / 源码隔离 | 实际比较通过，非仅对照整个 APK 摘要 |
| index 安全检查 | 审阅后实际暂存 index：212 files，0 policy violations，退出 0；未扫描历史 |

相对 R03 Lint 的 id / severity / message 多重集，两种构建均只增加一条既有 `ChromeOsAbiSupport` 的重复告警：原 defaultConfig 一处 ARMv7 声明改为 Debug / Release 两处声明，两处均被检查为缺少 ChromeOS x86_64。实际普通 APK ABI 与基线逐字相同，未新增产品兼容性变化；保留此告警，不压制规则或为本轮加入 x86 ABI。其余既有 Kotlin、DataStore 版本提示、overridePathCheck 和 Gradle 10 弃用提示保持。

最终 211 个原跟踪文件中 209 个内容摘要保持，仅本轮 Gradle 与既有进度账本改变；另新增本报告。所有 Kotlin 源码、原生 / 主机测试、Koin / Application / navigation / player / 旧 DataStore、schema、生产 manifest、update 元数据均未修改。schema version 1 摘要仍为 `14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959`。候选 Room / importer 未绑定运行时，不访问真实用户记录，不实现 `PlaybackProgressStore.commit`、T04 或 UI。

## 独立审阅与本地交付

已完成一次整轮独立只读审阅，Critical / Important / Minor 均为 0，没有需要修复的审阅发现或二次复核。审阅者直接重读保存的 APK / AAR / ELF、实际 manifest、原始 runner 文本、源码断言及主机 XML / 日志；没有重跑构建、测试、ADB、AVD 或安全检查，也未修改仓库。完整审阅范围记录在忽略的 `independent-review.md`。

审阅保留项逐项裁定如下，均不扩大当前通过声明：

| 保留项 | 本轮裁定及其证据限制 |
| --- | --- |
| ARMv7 实机 / TV / 遥控 | 保持未验证；ARM64 Pixel 2 AVD 不能替代该证据 |
| 强杀 / 系统回收 / 断电 | 保持未验证；实际测试仅关闭文件数据库后重开 |
| 实际磁盘耗尽 | 保持未验证；只证明受控 SQLiteFullException 下的事务行为 |
| 真实旧数据 / cutover / 双写 / T04 | 保持范围外；候选没有生产绑定，不能裁定迁移完成 |
| 读失败后的完整迁移编排 | 当前测试直接证明 reader 抛错与候选 DB 为空，没有包含 reader / importer 的生产入口，因此不声称完整编排已验证 |
| 所有 DataStore 类型原生往返 | 只裁定本 fixture 的 String / Int / Long；其他类型的主机编码结果不能替代原生证据 |
| 设备端 APK 回提摘要 | 未执行；受测副本摘要 + pinned 安装成功 + 对应 runner 足以支持本轮执行归属，但不声称已核对设备回提字节 |
| 最终 index / commit / clean 状态 | 审阅不代替这些操作；由执行者在审阅后另行实查并记录 |
| merge / 发布 / 生产签名 / 整体验收 | 不执行、不作通过裁定；T03 / M02 / A01–A24 继续未验收 |

提交准备已确认专用分支与起始 HEAD、三个预期路径、原 main checkout 干净、upstream 缺省符合明确的 local-only 约定。`git fetch --no-tags origin` 退出 0，远端 main 仍为 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`，提交前 `origin/main...HEAD` 为 0/7，无需同步或合并。本轮只允许三个明确路径的本地提交，不推送、合并、签名发布或前进 T04。Git 自身最终 HEAD 将在终端交接记录，不在提交内容中虚构自引用摘要。

已明确暂存 `app/build.gradle.kts`、本报告与既有进度账本，实际执行 `mise exec -- python3 scripts/check_repository_security.py`：212 个文件，0 policy violations，退出 0，范围仅为当前 index。补记交付信息后的最终 index 再做同一安全检查，并核对 staged blob / 工作区内容及没有额外改动；原始输出与交付摘要保存在忽略证据目录。仅保留本地分支 / 工作树；没有推送、合并、标签、生产签名、发布或自动进入 T04。

## 34 项逐方法实际结果

以下直接对应原源码清单及 runner terminal events；全数为实际 Android 执行证据。

| 测试类 | 方法 | 实际结果 |
| --- | --- | --- |
| `CinemaDatabaseTest` | `file_backed_values_survive_close_and_reopen` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `browse_contexts_do_not_overwrite_each_other` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `favorite_setting_is_idempotent` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `cache_eviction_preserves_user_records` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `actual_sql_constraints_reject_dangling_duplicate_and_null_rows` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `actual_schema_has_required_columns_and_deferred_import_fk` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `playback_null_keys_and_opaque_ids_are_distinct` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `corrupt_existing_payload_is_explicit_and_is_not_deleted` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `unsupported_format_is_retained` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `payload_cannot_claim_a_different_context` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `failed_transaction_never_reports_committed_or_leaves_reference` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `failed_save_preserves_previous_snapshot` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `cancelled_calls_propagate` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `unsupported_database_version_does_not_reset_records` | 已执行通过（code 0） |
| `CinemaDatabaseTest` | `opaque_utf16_keys_and_payloads_survive_close_and_reopen` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `import_is_idempotent` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `changed_snapshot_is_not_silently_skipped` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `same_content_new_capture_time_is_not_new_data` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `unresolved_legacy_id_is_preserved` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `malformed_entry_is_quarantined` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `legacy_files_are_unchanged` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `failure_before_marker_rolls_back_rows` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `failure_after_marker_before_commit_rolls_back_all` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `cancellation_before_commit_rolls_back_and_propagates` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `retry_after_commit_acknowledgement_loss_is_idempotent` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `cancellation_after_commit_propagates_and_retry_reconciles` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `injected_storage_full_has_no_false_success_or_partial_batch` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `source_read_failure_cannot_create_a_completed_batch` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `every_entry_is_accounted_for_without_overwriting_user_records` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `empty_snapshot_is_an_idempotent_preserved_batch` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `staging_rows_without_a_batch_cannot_commit` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `unicode_key_order_cannot_drop_or_reject_entries` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `changed_utf16_content_is_preserved_as_distinct_batches` | 已执行通过（code 0） |
| `LegacyUserDataImportTest` | `existing_marker_with_different_raw_rows_is_not_acknowledged` | 已执行通过（code 0） |
