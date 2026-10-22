# 安全整改证据与未通过门槛

日期：2026-10-23；历史起点 `8204cc0709a03743422a065eaf2457771889f6f3`，工作分支 `security/remediation`。本文记录本轮实际证据；最终提交号及对应 CI 终态以交付报告和 GitHub run 为准。

## 已实施

- GitHub 源仓库核对为 private。旧发布工作流 `387692952` 在管理端禁用并读回 `disabled_manually`；普通验证工作流保持启用。仓库 secrets 列表及 environments 均为空；普通 CI 只有 read 权限、action 使用固定提交、没有 APK 上传/发布步骤。
- 在更新本地 checkout 之前，将旧签名库、含凭据的历史配置与经验证的 Git bundle 放入仓库外的 AES-256 加密恢复归档；从维护者 macOS 钥匙串取回恢复口令，实际解密、逐项比对并打开旧签名库。没有打印私钥或口令。该归档仍在本机，不能等同离线备份。
- 更新校验拆出可执行 JVM 测试的策略、解析与文件生命周期单元：拒绝空包、错包名、错清单版本、同版本重放、降级、缺失/畸形/不完整 signer 集；缓存同样重验大小与可选摘要；校验/查包失败删除文件，下载失败/取消/重命名失败清理 `.part`，同一 updater 串行写文件。旧清单及别名仍兼容。
- 更新专用客户端禁止 HTTPS 跨协议重定向，媒体客户端和用户 HTTP 片源保持原行为。既有 HTTP 更新地址仍接受；可选 `sha256` 只是完整性检查，未认证清单及受损旧密钥仍是未关闭风险。
- 根据本轮实际 Lint 输出修复 9 个错误：API 24/25 避免访问 HARDWARE 位图常量；两处 FocusRequester 缓存使用 remember；播放器明确 Media3 unstable API 使用，并通过 LocalActivity 获取 Activity。没有界面改版、SDK/依赖降版、Lint baseline 或规则放宽。
- 普通 CI 增加 debug 完整组装及 release Lint，读取实际 JUnit XML 数量；推荐源发布脚本也拒绝执行。贡献构建脚本改用已有环境配置，保留自动 APK 检查并传播失败；去除个人路径。依赖共享许可资源改为合并保留。

## 泄露面审计

| 表面 | 实际范围与结果 | 限制 |
| --- | --- | --- |
| 当前 index | 起点 108 个受跟踪文件，原 guard 0 发现；补充规则定位 4 个文件内的个人路径，已修复并增加回归 | index guard 是定向回归检查，不覆盖所有秘密类别 |
| 源 Git 历史 | 起点 32 个提交、812 个 reachable objects；Gitleaks 8.26.0 默认规则扫描 all refs 报 0。补充规则确认 1 个签名库 blob、20 个含签名凭据的配置 blob、30 个含个人路径的历史 blob | 历史仍保留；默认扫描器漏报了已知签名材料，0 不能作为安全关闭 |
| 远端 refs | 起点仅 main，0 tags、0 PR；`refs/pull/*` 无可见引用，0 个可访问 fork | 外部克隆、不可见 fork、GitHub 内部缓存/历史页面无法完全枚举 |
| Actions 日志 | 26 次历史运行日志均成功读取（初次失败的一份经重试恢复）；默认扫描器及补充规则未确认服务凭据 | masked/未记录的值不能由日志证明不存在 |
| Actions artifacts | 20 个历史 APK artifact 均读取并解包检查；初次失败的一份经重试恢复，另做二进制内容扫描 | 默认扫描器的通用 token 命中需与依赖内容核对，不能直接认定为可用服务凭据；见最终交付结果 |
| Actions caches | 83 个缓存的 ID、ref 与大小已列出；未删除 | 未取得缓存 payload，内容审计仍受限 |
| Releases 与分发 | 两仓库 Release API 均无附件；分发 checkout 有 14 个 APK、14 个既有版本标签和两份 JSON。远端 main 仍为 `5e3d56021ed1bc38988f4d711e6fe93dc4f46cb5` | 分发仓库只读；未重写、覆盖或删除其历史和产物 |
| 管理设置 | main 保护及 private vulnerability reporting 查询未取得配置；protected environments 不存在 | 不能声明保护/私密报告入口已部署，公开前需维护者落实 |

没有确认可在发行者处撤销的独立服务 token，因此未修改共享服务。旧 APK 签名身份及其凭据已确认泄露，仍按受损身份处理；更改口令或删除当前文件不恢复信任。不能保证已流出副本可回收。没有强推、删除远端引用或清除日志、产物、缓存等证据。

## 实际验证

现有 mise Temurin 21.0.11+10 LTS、项目 Gradle 9.8.1、已有 Android 平台 37 与 Build Tools 36.0.0；CI 使用其声明的 JDK 17。没有改全局运行时、SDK/依赖版本或设备设置。

| 命令/证据 | 本轮结果 |
| --- | --- |
| `python3 -m unittest discover -s scripts/tests -v` | 22 项通过；含导出、index 与 URL userinfo 规则测试 |
| 更新回归 RED | 16 项 artifact 测试中 7 项先失败；5 项解析测试中 3 项先失败。修复后全套通过 |
| `:app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest` | 本地完整命令成功；44 项 JVM 测试，0 failure/error/skip；Lint 0 errors、每种 variant 19 warnings，hint 仍报告 |
| `help -Pcinema.enableReleaseSigning=true` | 非零退出并出现明确 freeze 原因，符合预期拒绝 |
| aapt2 与 apksigner | debug 包为 `com.ichichuang.cinema.debug`；release 保留 `com.ichichuang.cinema`，unsigned 产物未通过签名验证且原因为无签名；版本均保持 18 |
| APK 内容 | 两种产物均只含 armeabi-v7a 的 Graphics Path/DataStore 原生库；没有上传或安装 |
| 独立代码复核 | 发现 Windows wrapper 移除自动 APK 检查的回归，已恢复检查调用和失败传播；未发现其他 Important/Critical 新回归。Windows 无可用 PowerShell，执行验收仍待该平台 |
| 原位升级/播放/遥控/性能 | 本轮无设备结果；没有扫描网络、连接电视或改变电视设置 |

离线基线先因既有 JUnit 缓存缺项失败，之后联网解析原有版本再验证。当前 AGP 未注册 `testReleaseUnitTest`，没有用任务名冒充测试执行。完整 Lint 已跑时，Gradle 的 lintVital 汇总可能显示 SKIPPED；它不代表完整 Lint 或 JUnit 被跳过。

## 分开验收

| 门槛 | 状态 | 尚需证据/决定 |
| --- | --- | --- |
| 安全事件关闭 | 未通过 | 离线恢复备份、独立信任引导、新签名保管、真实迁移与旧钥匙拒绝、受限泄露面处理 |
| 源码公开 | 未通过 | 历史/引用方案批准、原创及图像权属、许可证批准、最终第三方 notices 与私密报告/保护设置 |
| 正式发布 | 冻结 | 上述安全门、指定电视原位与后续更新验证、发布独立审批 |
| 原生界面改进 | 未开始 | 按顺序等待安全门；没有把 Lint 修复表述为界面完成或性能提升 |

[迁移评审材料](SIGNING_MIGRATION.md)与[第三方材料审计](THIRD_PARTY_NOTICES.md)给出可审阅的具体方案。`prepare_public_source.py` 只制作本地 review snapshot；不会改变现有远端。它的实际候选导出与无私有凭据构建应在提交后另行留证，不能由脚本存在推断通过。
