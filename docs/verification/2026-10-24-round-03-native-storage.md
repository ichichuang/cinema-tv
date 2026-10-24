# Round 03 原生存储验证记录

## 结论与范围

**NATIVE_TARGET_BLOCKED：34 项原生测试仍为 0 执行。** 本轮实际 debug APK 只有 `armeabi-v7a` 原生库；本轮唯一安装并检查的 Android 11 / API 30 镜像只支持 `arm64-v8a`，`ro.product.cpu.abilist32` 为空。两者 ABI 无交集，未创建 AVD、未启动设备、未安装 APK、未调用 instrumentation。没有将编译、主机测试或环境探针计作原生通过。

本轮交付是阻塞证据与主机回归记录。R03-2 原生执行未开展，R03-3 没有实际原生失败可供修复；T03 candidate native-storage verification slice 尚未验证，整个 T03、M02、A01–A24 均保持未验收。未进入 T04。

## 输入、工作树与基线

- 用户明确选择“按附件方案执行 Round 03”后开始执行。
- 沿用既有 `cinema-contract-foundation` 工作树、`codex/contract-foundation` 分支；实查原始 HEAD 为 `f588ed11d6f998b36de16feaa7a141fda1cc2bef`，原先没有暂存、未暂存或未跟踪改动。
- 原 main 为 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`，原 checkout 干净；未重建工作树、重置或丢弃不同状态。
- 独立 instruction / plan 与 ZIP 内对应内容逐字一致，ZIP 三项校验均匹配。instruction SHA-256：`b554ee3993cfed8988a135b5c6db8370c85d7ac170f79e90134008a9a6f12e71`；唯一执行计划 `2026-10-24-round-03-native-storage-verification.md` SHA-256：`4f0b86c7a3efe4a4349e6fd2a97a4a93b4fd829b87c7205e15b41cb479cc7f17`。
- 原计划含个人绝对路径，按 index 安全规则保存在忽略的 `.local/evidence/round-03/`，没有改写计划、提交个人路径或覆盖旧计划。主规格、Round 02 计划、契约、报告与实际测试均已核对。
- 开始执行时对原有 210 个跟踪文件记录 SHA-256；完整重跑构建后，210 个文件的内容仍全部匹配。随后仅修改本进度记录及新增本报告，应用源码、测试、Gradle 配置与 schema 没有变更。

## 实际工具链与目标检查

| 项目 | 实查结果 |
| --- | --- |
| 主机 | macOS 27.0，`arm64`，48 GiB RAM；开始时约 346 GiB 可用磁盘 |
| 虚拟化 | `kern.hv_support=1`；实际 `emulator -accel-check` 返回 0，报告 Hypervisor.Framework OS X Version 27.0 |
| 沿用运行时 | mise Temurin `21.0.11+10.0.LTS`、Python `3.13.3`；未安装或切换全局运行时 |
| 原有 SDK | cmdline-tools `22.0`、platform-tools `37.0.1`、build-tools `36.0.0`、platform `android-37.0` revision 2 |
| 既有设备 / AVD | 复用原有 ADB server 做只读 `devices -l`，列表为空；`avdmanager list avd` 为空；未重启 ADB server |
| 最小新增 SDK 包 | `emulator` `37.2.12` / build `16428233`；`system-images;android-30;default;arm64-v8a` revision 2；没有安装第二个镜像或替换原有 SDK 包 |
| 安装与许可 | `sdkmanager --install` 退出 0，用关闭的 stdin 执行，未输入许可确认；现有 `android-sdk-license` 条目在前后均为 `24333f8a63b6825ea9c5514f83c2829b004d1fee` |
| 镜像内容 | Android 11 / API 30；实际 `build.prop` 的 `abilist=arm64-v8a`、`abilist32=`、`abilist64=arm64-v8a` |
| APK 原生 ABI | debug / unsigned release 均只有 `lib/armeabi-v7a/libandroidx.graphics.path.so` 与 `lib/armeabi-v7a/libdatastore_shared_counter.so`；androidTest APK 无 `.so` |
| AVD / serial / boot | 未创建、未分配 serial、未启动，因此没有虚构 AVD 名称、boot-completed 或设备 ABI 实测 |

镜像 `build.prop` SHA-256 为 `09feda2d8af0c2e293c978aeb1658a9925faf9759a8e7188c45b0ae6fec8fe21`。SDK package revision 为 2，其归档内 `source.properties` 的 `Pkg.Revision` 仍为 1；报告区分了两处实际元数据，没有自行修改。

ABI 结论来自实际 APK ZIP / `aapt` 及镜像属性，不是凭包名推断。官方[加速要求](https://developer.android.com/studio/run/emulator-acceleration)规定 ARM64 主机的加速镜像架构为 arm64-v8a；[ABI 文档](https://developer.android.com/ndk/guides/abis)解释了设备主 / 次 ABI 与 APK native 库匹配。这里只裁定当前已核实路径受阻，不声称所有其他主机或镜像均不可能运行。

另做了只读低层 `qemu-system-armel -machine help` / `-accel help` 能力探针，均在 dyld 加载阶段失败，末命令退出 134，没有启动 AVD；该结果不是原生测试失败，也不单独证明其他 ARM32 路径不可行。未绕过 Android 启动器、拼装未经验证的 QEMU 环境或下载额外镜像；未用生产 ABI 修改换取测试便利。

## APK 与 runner

首次按计划运行 `:app:assembleDebug :app:assembleDebugAndroidTest` 成功，70 个任务均 up-to-date。随后完整回归使用 `--rerun-tasks`，134 个任务实际执行，包含两个 APK 的重新编译。下列为最终产物，不混用首次构建的 SHA-256。

| 产物 | 从实际 manifest / metadata 读取的身份 | 最终 SHA-256 |
| --- | --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | `com.ichichuang.cinema.debug`，18 / `0.9.8-debug`，min 24 / target 28 | `a8c1f0453ea1660756c16207b70603a38f70395e483ddd2a5a963709904a1885` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `com.ichichuang.cinema.debug.test`；target `com.ichichuang.cinema.debug` | `a70dce5423f2fd428550b249c4408f3dab49dfb1e3a50bbe0b48d1deaacc1353` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | `com.ichichuang.cinema`，18 / `0.9.8`，未签名验证产物 | `af286f0bb0a67524a1d36c02381ab9e1ea1dfb5e79fa337b7ad87ca07ca370e1` |

实际 runner 为 `androidx.test.runner.AndroidJUnitRunner`，组件为 `com.ichichuang.cinema.debug.test/androidx.test.runner.AndroidJUnitRunner`。由于没有兼容目标，未执行任何 `adb -s SERIAL install` 或 `am instrument`，也没有使用 `connectedDebugAndroidTest` / `ANDROID_SERIAL` 冒充目标隔离。

## 34 项原生测试逐项清点

两类均使用 `AndroidJUnit4`；没有 `@Ignore`。共同 fixture 的数据库及 DataStore 文件使用独占的 synthetic UUID 名称；`reopen()` 关闭原实例后调用实际 file-backed `CinemaDatabaseFactory.open()` 构造新实例。以下每项本轮证据等级均为 **COMPILED_ONLY**，前置条件为兼容的独立 disposable emulator 与 debug 包上下文。

`app/src/androidTest/kotlin/com/ichichuang/cinema/data/local/CinemaDatabaseTest.kt`：

| 方法 | 源码断言涉及的行为 | 本轮结果 |
| --- | --- | --- |
| `file_backed_values_survive_close_and_reopen` | 文件存在、新实例重开、browse / favorite / playback / metadata 值 | 未执行 |
| `browse_contexts_do_not_overwrite_each_other` | 独立 context 与直接串行写入顺序 | 未执行 |
| `favorite_setting_is_idempotent` | 收藏 / 取消幂等与非法 ID | 未执行 |
| `cache_eviction_preserves_user_records` | 清 metadata cache 后用户表保留 | 未执行 |
| `actual_sql_constraints_reject_dangling_duplicate_and_null_rows` | 外键、唯一键、非空、RESTRICT | 未执行 |
| `actual_schema_has_required_columns_and_deferred_import_fk` | 实际 SQL 列与 BLOB、延迟外键 | 未执行 |
| `playback_null_keys_and_opaque_ids_are_distinct` | 空 episode / variant 与不透明 ID | 未执行 |
| `corrupt_existing_payload_is_explicit_and_is_not_deleted` | 损坏负载显式失败且保留 | 未执行 |
| `unsupported_format_is_retained` | 不支持的编码版本保留 | 未执行 |
| `payload_cannot_claim_a_different_context` | 行键与负载 context 一致 | 未执行 |
| `failed_transaction_never_reports_committed_or_leaves_reference` | 触发器注入失败、回滚与无假成功 | 未执行 |
| `failed_save_preserves_previous_snapshot` | 保存失败后保留旧快照 | 未执行 |
| `cancelled_calls_propagate` | 已取消调用传播且不写入 | 未执行 |
| `unsupported_database_version_does_not_reset_records` | 不支持 schema 时不重置已有行 | 未执行 |
| `opaque_utf16_keys_and_payloads_survive_close_and_reopen` | UTF-16 键 / 负载重开后无损 | 未执行 |

`app/src/androidTest/kotlin/com/ichichuang/cinema/data/local/LegacyUserDataImportTest.kt`：

| 方法 | 源码断言涉及的行为 | 本轮结果 |
| --- | --- | --- |
| `import_is_idempotent` | 重开后的 batch 幂等 | 未执行 |
| `changed_snapshot_is_not_silently_skipped` | 内容变更产生不同 batch | 未执行 |
| `same_content_new_capture_time_is_not_new_data` | 相同内容不同 capture time 摘要稳定 | 未执行 |
| `unresolved_legacy_id_is_preserved` | unresolved 记录保留、不伪造用户关联 | 未执行 |
| `malformed_entry_is_quarantined` | 错误条目与原值保留 | 未执行 |
| `legacy_files_are_unchanged` | typed DataStore capture、源文件字节不变 | 未执行 |
| `failure_before_marker_rolls_back_rows` | marker 前 I/O 注入回滚 | 未执行 |
| `failure_after_marker_before_commit_rolls_back_all` | marker 后 commit 前完整回滚 | 未执行 |
| `cancellation_before_commit_rolls_back_and_propagates` | 实际协程取消、重开后无残留 | 未执行 |
| `retry_after_commit_acknowledgement_loss_is_idempotent` | commit 确认丢失与重试对账 | 未执行 |
| `cancellation_after_commit_propagates_and_retry_reconciles` | commit 后取消与重试对账 | 未执行 |
| `injected_storage_full_has_no_false_success_or_partial_batch` | SQLiteFullException 合成注入 | 未执行 |
| `source_read_failure_cannot_create_a_completed_batch` | 错误源文件不生成完成 batch | 未执行 |
| `every_entry_is_accounted_for_without_overwriting_user_records` | 每条 typed 原值、未知项与用户记录隔离 | 未执行 |
| `empty_snapshot_is_an_idempotent_preserved_batch` | 空快照可保留且幂等 | 未执行 |
| `staging_rows_without_a_batch_cannot_commit` | staging 到 batch 的真实外键 | 未执行 |
| `unicode_key_order_cannot_drop_or_reject_entries` | Unicode 顺序不丢条目 | 未执行 |
| `changed_utf16_content_is_preserved_as_distinct_batches` | 不同 UTF-16 内容分别保留 | 未执行 |
| `existing_marker_with_different_raw_rows_is_not_acknowledged` | marker / raw rows 不一致显式失败 | 未执行 |

实际 instrumentation 统计：executed 0、passed 0、failed 0、skipped 0、not run 34。0 failed 不代表通过；没有 runner 结果可解析。未新增、忽略、改名或弱化任何测试。

## 主机回归、警告与存储隔离

命令在隔离工作树执行；`CINEMA_ANDROID_SDK` 代表已有本地 SDK 路径。SDK 安装命令的 stdin 关闭，没有 `yes` 或 `--licenses`。

```sh
ANDROID_HOME="$CINEMA_ANDROID_SDK" ANDROID_SDK_ROOT="$CINEMA_ANDROID_SDK" \
  mise exec java@temurin-21.0.11+10.0.LTS -- \
  "$CINEMA_ANDROID_SDK/cmdline-tools/22.0/bin/sdkmanager" --install \
  emulator 'system-images;android-30;default;arm64-v8a' --channel=0 \
  --sdk_root="$CINEMA_ANDROID_SDK" </dev/null
"$CINEMA_ANDROID_SDK/emulator/emulator" -accel-check
"$CINEMA_ANDROID_SDK/build-tools/36.0.0/aapt" dump badging \
  app/build/outputs/apk/debug/app-debug.apk
"$CINEMA_ANDROID_SDK/build-tools/36.0.0/aapt" dump xmltree \
  app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk AndroidManifest.xml
mise exec -- python3 -m unittest discover -s scripts/tests -v
ANDROID_HOME="$CINEMA_ANDROID_SDK" ANDROID_SDK_ROOT="$CINEMA_ANDROID_SDK" \
  mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
  :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease \
  --rerun-tasks --no-daemon --no-configuration-cache --console=plain
```

| 检查 | 本轮实际结果与等级 |
| --- | --- |
| Python | 62/62，退出 0，EXECUTED_HOST |
| 应用单测 XML | 12 suites、124 tests，failure / error / skipped 均 0，EXECUTED_HOST |
| debug / unsigned release / androidTest assembly | 成功；与双 Lint 合计 134 tasks、134 executed，退出 0 |
| debug Lint | 20 Warning + 1 Hint；相对 R02 baseline 的 id / severity / message 多重集无变化 |
| release Lint | 21 Warning + 1 Hint；新增 1 条 GradleDependency：DataStore 1.2.1 可更新至 1.3.0；没有消失的消息 |
| native SQLite / DataStore / 事务行为 | COMPILED_ONLY，0 执行 |
| 真实旧用户数据、运行时切换、process death、断电、磁盘耗尽、实机性能、升级 | NOT_TESTED |

对告警逐项核查后，debug 的四处 location 差异、release 的对应四处 location 差异来自与 R02 实施前 baseline 比较时 build 文件行号移动；没有把 location 差异误称为新增代码问题。release 另有上述版本提示，官方 [DataStore 发布资料](https://developer.android.com/jetpack/androidx/releases/datastore)亦列出 1.3.0；本轮保持固定 1.2.1，没有依赖升级或 lint 压制。一次预设“告警完全一致”的本地审计断言因此失败，已据真实 XML 记录差异；Gradle / Lint 任务本身均退出 0。既有 Kotlin、overridePathCheck 实验选项及 Gradle 10 弃用提示保留。

Room schema 仍为 version 1、七表，identityHash `ecf80cb44c0d3908fb222ce47999b3bf`，SHA-256 `14ff4e2507925df8ef1cd34085868de900520f162dca1dd9cb3d57385f048959`；真实 compiler 重跑后与原 HEAD 一致。debug / release 的实际 `CinemaDatabase_Impl.kt` SHA-256 均为 `276b375de44118a5ebe60f36fcdae24c6c1978e54e3274ba6f6810f4a3fe1e11`，与 R02 一致。检查了生成表的主键、BLOB 身份键、RESTRICT 及 staging 的延迟外键定义；这属于生成物 / 静态证据，未替代实际 SQL 执行。

`CinemaApplication` 仍只启动既有 Koin 模块；`AppModule` 仍绑定 `PlaybackProgress`、`DataStoreBrowseStore`、`DataStoreFilterStore`。实际候选工厂 / store / reader / importer 的 main 引用仅在定义及候选内部；没有 DI、启动、`CinemaNavHost` 或 player 接入。生产 DataStore、包名、18 / 0.9.8、SDK / ABI、签名冻结和 release / update 元数据均与原 HEAD 一致。debug / release 的 assets / raw 未发现新增 fixture。没有原生失败或红绿证据，故没有修改代码、测试、schema 或契约。

## 独立审阅与本地交付

完成一次独立只读审阅，Critical / Important / Minor 均为 0。审阅者独立读取两份文档、原计划、实际 APK / 镜像、schema / 生成实现，并重新解析 34 项源码清单、124 项测试 XML、62 项 Python 日志及 Lint 差异；没有重跑完整测试、SDK 安装或原生执行。裁定仅为当前文档可进入本地证据交付，不能解释为原生通过、可合并或可发布。

对审阅者 declined-to-judge 项逐项裁定：34 项存储行为、安装 / boot / serial 门禁及其他模拟路径均保持未验证；真实用户迁移、运行时切换、断电 / process death / 磁盘耗尽、实机 / UI / 升级保持范围外；当前状态与日志不证明历史上从未接触其他服务；不以本轮替代 R02 全面代码质量或整个 T03 / M02 / A01–A24 验收。暂存安全检查和提交终态由作者在审阅后完成并另行记录。没有审阅修正或第二次独立复核；审阅后仅补充命令、已有哈希和交付终态元数据。

提交准备阶段已核对分支、状态、upstream 与远端 refspec，随后 `git fetch --no-tags origin` 退出 0。`origin/main`、本地 main 和原 checkout HEAD 均保持 `7f3e06318ddec8385e7d8be698a922a9cd85c5dd`，原 checkout 干净；提交前 `origin/main...HEAD` 为 0/6，专用本地分支没有 upstream，没有需要同步的变动。明确暂存本报告及账本后运行 `mise exec -- python3 scripts/check_repository_security.py`：211 个跟踪文件、0 policy violations、退出 0；未扫描历史。已检查暂存 diff、blob 与工作区字节一致及无剩余未暂存差异；补记终态后再次检查最终 index。

本轮只提交本报告与既有进度账本。最终提交包含本报告，因此其自身 HEAD 由本轮终端交接及 `git log -1 --format=fuller` 记录；不会在提交内容中虚构自引用哈希。原始日志、APK manifests、34 项清单、原始计划、基线 / 回归 JSON、镜像属性与最终交付证据保存在忽略的 `.local/evidence/round-03/`。

执行决定：

- Ruling: 沿用既有账本对应 R03-1–R03-4，按原计划顺序记录，不为了技能数字任务脚本改写原计划。代价：状态需人工对应。
- Ruling: 唯一已安装镜像与 APK ABI 不匹配后停止目标准备，不额外下载镜像、改变 ABI 或用低层模拟器拼装环境；按计划阻塞分支仅完成可用主机验证与报告。代价：34 项原生验证仍未解决，不能裁定候选存储通过。
- Ruling: 原始执行计划保留在忽略证据目录，提交中仅引用文件名和 SHA-256，以同时满足按原计划执行与禁止个人绝对路径的 index 规则。代价：复核计划原字节需本地附件 / 忽略证据，Git 内没有新计划副本。
- Ruling: 本轮是明确允许的 evidence-only 本地提交；专用分支没有 upstream，按当前 scoped plan 不推送、不合并。代价：证据没有远端副本。
- Ruling: 接受独立审阅对未执行行为及范围外关口的保留裁定，只对当前证据文档作本地交付判断；暂存 / 提交终态仍由作者实查。代价：独立审阅未补足任何原生、实机或生产验收，也不构成对随后交付元数据的第二次复核。

实机、真实数据、用户侧视觉验收、生产升级与发布均未执行；未进行 T04、资源扫描、界面或运行时改造。已安装的两项 SDK 包保留在原有忽略工具目录，未改动个人 AVD、网络 / 代理 / 系统服务或其他仓库。
