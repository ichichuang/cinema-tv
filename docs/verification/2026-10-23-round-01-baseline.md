# Round 01：基线与验证记录

执行范围为本仓库 `docs/superpowers/plans/2026-10-23-round-01-contract-foundation.md`。总计划只提供规格依据，不授权继续 T03–T16。

## 输入与隔离

- 基线：`7f3e06318ddec8385e7d8be698a922a9cd85c5dd`，原始 `main` 的工作区、暂存区均干净，与已存在的 `origin/main` 跟踪引用一致。
- 本轮分支：`codex/contract-foundation`；工作区位置：相对原仓库 `../.worktrees/cinema-contract-foundation`。分支无 upstream，仅用于本地提交。
- 桌面工作区工具未识别聊天目录下的子仓库，返回 `Not a git repository`；随后从真实仓库用 `git worktree add -b` 创建隔离工作区。未重置、清理、暂存或改写原始工作区。
- 输入包 SHA-256：`2df220d4f1b632856080a55bcd163a59cf9ff0037042f6dba299df1ec6b9f44e`。五个文件均匹配随包 `SHA256SUMS`；解压前检查相对路径与符号链接。
- 两份计划原样导入。主计划 SHA-256：`439c9be55bf8e889168711b650aa09dec02f05c38d04ac0e3011d24a86bbe1af`；分轮计划 SHA-256：`393c50390733799af5534cc439d3b3913d0ad381e68301e8baa96649458a507a`。
- 视觉参考只保留在被忽略的 `.local/design-references/no-brand-visual-direction.png`，未作为应用素材或图标提交。

## 环境与命令

沿用已安装的 mise Temurin JDK `21.0.11+10.0.LTS`（编译目标仍为 Java 17）、mise Python `3.13.3` 和原仓库本地 Android SDK。SDK 含 Android 37.0 平台及 Build Tools 36.0.0；Wrapper 为 Gradle 9.8.1，AGP 9.4.1。未新增依赖、安装工具链或变更全局配置。

命令在隔离工作区运行；下列记录用 `$CINEMA_ANDROID_SDK` 代替实际传入的已有 SDK 绝对路径，避免提交个人路径。完整原始命令输出和报告仅保存在忽略目录 `.local/evidence/contract-foundation/`。

```sh
mise exec -- python3 -m unittest discover -s scripts/tests -v
mise exec -- python3 scripts/check_repository_security.py
ANDROID_HOME="$CINEMA_ANDROID_SDK" ANDROID_SDK_ROOT="$CINEMA_ANDROID_SDK" \
  mise exec java@temurin-21.0.11+10.0.LTS -- bash ./gradlew \
  :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease \
  :app:testDebugUnitTest --no-daemon --no-configuration-cache
```

## 修改应用代码前的基线

| 检查 | 退出码 | 实际结果 |
| --- | --- | --- |
| Python 脚本测试 | 0 | 62 个测试，全部通过 |
| Git index 安全检查 | 0 | 162 个已跟踪文件，0 违规；不扫描历史及未暂存的新文件 |
| Debug/Release 构建、Lint、应用单测 | 0 | Gradle 构建成功，99 个任务；75 个单测，0 失败/错误/跳过 |
| Debug Lint XML | 同上 | 0 Error，19 Warning，1 Hint |
| Release Lint XML | 同上 | 0 Error，20 Warning，1 Hint |

已有 Gradle 弃用提示（后续 Gradle 10 兼容性）及 Lint 提示保留；未通过关闭规则消除。Release 是未签名验证产物，不是可发布升级包。未传入生产签名属性。

## 本轮验证与交付

定向命令使用相同 JDK / SDK，Gradle 任务为：

```sh
bash ./gradlew :app:testDebugUnitTest --tests 'com.ichichuang.cinema.domain.*' \
  --no-daemon --no-configuration-cache
```

| 阶段 | 退出码 | 实际结果 |
| --- | --- | --- |
| 测试先行、类型尚不存在 | 1 | 新 catalog 等 API 无法解析；只证明缺类型，不计作行为红灯 |
| 最小类型结构、尚未加入校验 | 1 | 23 个新测试实际执行，9 个预期断言失败，均为预期抛 IllegalArgumentException 而未抛出 |
| 加入校验后的定向测试 | 0 | 23/23，通过；0 失败/错误/跳过 |
| 独立审阅前完整应用单测与构建、Lint 命令 | 0 | 98/98（既有 75 + 新增 23）；99 个 Gradle 任务，构建成功 |
| Python 脚本回归 | 0 | 62/62，通过 |
| Debug / Release Lint | 0 | 均 0 Error、20 Warning、1 Hint |
| 仅暂存导入计划的安全检查 | 0 | 164 个文件，0 违规 |
| 全部本轮文件暂存后的安全检查 | 0 | 186 个文件，0 违规；不扫描历史 |

9 个行为红灯为：`inverted_year_range_is_rejected`、`unknown_year_is_not_zero`、`source_failure_is_not_confirmed_no_match`、`invalid_identity_counts_and_conflict_are_rejected`、`negative_position_is_rejected`、`unknown_duration_remains_null`、`negative_operation_sequence_is_rejected`、`invalid_snapshot_context_is_rejected`、`invalid_subscription_metadata_is_rejected`。JUnit XML 和日志分别保留在 `red-reports/`、`targeted-green-reports/`、`final-reports/` 等忽略目录内。

审阅前新测试分布：CatalogContractTest 12、UserDataContractTest 7、SubscriptionContractTest 4。除计划要求的 15 项断言外，补充了输入集合防御性复制、会话序号边界、非有限偏移及授权未知状态等验证。年份包含端点、未知语言与配置不等于可播放等用例仅验证契约表达，不是筛选引擎、真实来源分类或播放器验收。

Release Lint 的问题 ID、严重度、消息及位置与基线完全一致。Debug 新出现的唯一提示是原 `app/build.gradle.kts:97` 的 DataStore 可用版本提示；同一提示已存在于 Release 基线。该依赖声明完全未改，属版本检查结果差异；未为消除提示修改依赖或规则。

应用与测试文件哈希保存在忽略的 `validated-source-sha256.txt`；审阅修复后的最终候选另存 `review-fix-source-sha256.txt`，提交时核对相同字节。基线已有文件无修改，新类型只互相引用及被新接口使用，没有运行路径注册。新增文件限于三个模型子目录、五个 repository 接口、三个测试类和五份文档（含原样计划）。

提交前已 `git fetch origin`；`main...origin/main` 为 0/0，基线无漂移。fetch 自动取得远端既有 `v0.9.8` 的本地标签引用，未创建或修改远端标签、Release 或资产。审阅修复提交前再次使用 `git fetch --no-tags origin`，确认 main 仍为 0/0。本轮分支无 upstream，符合仅本地保留要求，未推送。

## 独立审阅与修复后的最终验证

独立只读审阅覆盖 `7f3e063..6a56515` 的完整分支，核对源代码、计划、测试 XML 和候选哈希；没有重新运行构建或修改工作区。审阅发现：

1. Important：已知总量可小于已返回的不同作品数。先新增 `known_total_cannot_be_smaller_than_distinct_returned_works`，实际执行 25 个契约测试，仅此断言失败（退出 1）；随后补充构造校验。配套用例确认按稳定 workId 计数、不合并条目、不推导 null 总量。
2. 文档焦点回退规则与主规格 4.2 冲突。审阅者标为 Minor；执行者按数据契约交接的一致性问题纳入同一修复，改为引用主规格回退顺序。无恢复器实现，不为纯文档修正伪造 UI/JUnit 证据。

| 修复后检查 | 退出码 | 结果 |
| --- | --- | --- |
| 定向契约单测 | 0 | 25/25；Catalog 14、UserData 7、Subscription 4 |
| 完整应用单测、两种构建与 Lint | 0 | 100/100（既有 75 + 新增 25），0 失败/错误/跳过；99 个 Gradle 任务成功 |
| Python 脚本测试 | 0 | 62/62 |
| Debug / Release Lint | 0 | 均 0 Error、20 Warning、1 Hint |
| 修复文件暂存后的安全扫描 | 0 | 186 个文件，0 违规 |
| 暂存与未暂存差异检查 | 0 | 无格式错误，无范围外改动 |

这些命令与前述环境和完整回归命令一致。最终证据位于忽略目录的 `review-fix-red.log`、`review-fix-green.log`、`review-fix-full-gradle.log`、`review-fix-full-python.log`、`review-fix-security.log` 及对应 XML 报告目录。两项发现均已处理；修复由实际回归验证，没有进行第二次独立复审。没有延后处理的 Minor 发现。

## 本地提交与文件清单

| 提交 | 内容 |
| --- | --- |
| `f0afef72efd24e3897d0c2e82afc364b18807061` | 原样导入两份权威计划 |
| `6a5651587d2a9c74afbc6ae6024afe48c21f695b` | 最小数据契约、五个接口、测试与执行记录 |
| `41bb6885f5945159d825b53eef2cecb59d623865` | 修复总量校验与焦点回退文档冲突 |

以上第三笔为最终代码候选；随后只提交本报告及进度账本的结项记录。最终交付 HEAD 在用户答复中给出，避免报告自引用自己的提交哈希。分支和隔离工作区保留；不合并、不推送、不发布。

相对基线共 24 个新增文件，基线文件无修改：

| 目录 | 文件 |
| --- | --- |
| `app/src/main/kotlin/com/ichichuang/cinema/domain/model/catalog/` | CatalogIds.kt、CatalogItem.kt、CatalogQuery.kt、CatalogResult.kt、FieldEvidence.kt、LoadOutcome.kt |
| `app/src/main/kotlin/com/ichichuang/cinema/domain/model/userdata/` | PlaybackSnapshot.kt、BrowseSnapshot.kt、CommitResult.kt |
| `app/src/main/kotlin/com/ichichuang/cinema/domain/model/subscription/` | SubscriptionRefreshRequest.kt、SubscriptionState.kt |
| `app/src/main/kotlin/com/ichichuang/cinema/domain/repository/` | CatalogRepository.kt、PlaybackProgressStore.kt、BrowseStateStore.kt、FavoritesStore.kt、SubscriptionManager.kt |
| `app/src/test/kotlin/com/ichichuang/cinema/domain/` | catalog/CatalogContractTest.kt、userdata/UserDataContractTest.kt、subscription/SubscriptionContractTest.kt |
| `docs/superpowers/plans/` | 2026-10-23-tv-experience-rebuild.md、2026-10-23-round-01-contract-foundation.md |
| `docs/architecture/` | tv-data-contracts.md |
| `docs/verification/` | 2026-10-23-round-01-baseline.md |
| `docs/superpowers/progress/` | 2026-10-23-tv-rebuild.md |

## 未验证前置条件

没有提供已授权、脱敏的旧 DataStore 样本及两类参考设备证据。本轮未提取正式设备数据，未执行迁移、UI/遥控器/播放/断电恢复验证、真实性能测试或升级安装。基线构建不证明这些验收场景通过。
