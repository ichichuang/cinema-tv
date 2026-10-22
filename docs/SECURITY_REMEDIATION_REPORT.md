# 安全整改证据与未通过门槛

日期：2026-10-23；历史起点 `8204cc0709a03743422a065eaf2457771889f6f3`，工作分支 `security/remediation`。本文记录本轮实际证据；最终提交号及对应 CI 终态以交付报告和 GitHub run 为准。

## 已实施

- GitHub 源仓库核对为 private。旧发布工作流 `387692952` 在管理端禁用并读回 `disabled_manually`；普通验证工作流保持启用。仓库 secrets 列表及 environments 均为空；普通 CI 只有 read 权限、action 使用固定提交、没有 APK 上传/发布步骤。
- 在更新本地 checkout 之前，将旧签名库、含凭据的历史配置与经验证的 Git bundle 放入仓库外的 AES-256 加密恢复归档；从维护者 macOS 钥匙串取回恢复口令，实际解密、逐项比对，并在内存中取回旧私钥验证恢复能力；没有执行签名。没有打印私钥或口令。该归档仍在本机，不能等同离线备份。
- 更新校验拆出可执行 JVM 测试的策略、解析与文件生命周期单元：拒绝空包、错包名、错清单版本、同版本重放、降级、缺失/畸形/不完整 signer 集；缓存同样重验大小与可选摘要；校验/查包失败删除文件，下载失败/取消/重命名失败清理 `.part`，同一 updater 串行写文件。旧清单及别名仍兼容。
- 更新专用客户端在每一跳仅拒绝 HTTPS → HTTP 降级并关闭响应，仍允许 HTTP → HTTPS 升级和同协议跳转；媒体客户端和用户 HTTP 片源保持原行为。既有 HTTP 更新地址仍接受；可选 `sha256` 只是完整性检查，未认证清单及受损旧密钥仍是未关闭风险。
- 根据本轮实际 Lint 输出修复 9 个错误：API 24/25 避免访问 HARDWARE 位图常量；两处 FocusRequester 缓存使用 remember；播放器明确 Media3 unstable API 使用，并通过 LocalActivity 获取 Activity。没有界面改版、SDK/依赖降版、Lint baseline 或规则放宽。
- 普通 CI 增加 debug 完整组装及 release Lint，读取实际 JUnit XML 数量；推荐源发布脚本也拒绝执行。贡献构建脚本改用已有环境配置，保留自动 APK 检查并传播失败；去除个人路径。依赖共享许可资源改为配置合并；实际 APK 含 8 份 AndroidX LICENSE 文本，AL2.0/LGPL2.1 同名资源未出现，完整 notices 仍待核对。

## 泄露面审计

| 表面 | 实际范围与结果 | 限制 |
| --- | --- | --- |
| 当前 index | 起点 108 个受跟踪文件，原 guard 0 发现；补充规则定位 4 个文件内的个人路径，已修复并增加回归 | index guard 是定向回归检查，不覆盖所有秘密类别 |
| 源 Git 历史 | 起点 32 个提交、812 个 reachable objects；Gitleaks 8.26.0 默认规则扫描 all refs 报 0。补充规则确认 1 个签名库 blob、20 个含签名凭据的配置 blob、30 个含个人路径的历史 blob | 历史仍保留；默认扫描器漏报了已知签名材料，0 不能作为安全关闭 |
| 远端 refs | 起点仅 main，0 tags、0 PR；`refs/pull/*` 无可见引用，0 个可访问 fork | 外部克隆、不可见 fork、GitHub 内部缓存/历史页面无法完全枚举 |
| Actions 日志 | 26 次历史运行日志均成功读取（初次失败的一份经重试恢复）；默认扫描器及补充规则未确认服务凭据 | masked/未记录的值不能由日志证明不存在 |
| Actions artifacts | 20 个历史 APK artifact 均读取、解包并完成默认规则扫描；通用 token 命中已逐包核对，与公开 kotlinx-serialization-core-jvm 1.11.0 的同名验证资源逐字节一致 | 命中归类为公开依赖的验证标记；没有将该值复制到报告或向服务发行者试用。二进制扫描不构成运行时/设备验收 |
| Actions caches | 83 个缓存的 ID、ref 与大小已列出；未删除 | 未取得缓存 payload，内容审计仍受限 |
| Releases 与分发 | 两仓库 Release API 均无附件；分发 checkout 有 14 个 APK、14 个既有版本标签和两份 JSON。远端 main 仍为 `5e3d56021ed1bc38988f4d711e6fe93dc4f46cb5` | 分发仓库只读；未重写、覆盖或删除其历史和产物 |
| 管理设置 | main 保护及 private vulnerability reporting 查询未取得配置；protected environments 不存在 | 不能声明保护/私密报告入口已部署，公开前需维护者落实 |

没有确认可在发行者处撤销的独立服务 token，因此未修改共享服务。旧 APK 签名身份及其凭据已确认泄露，仍按受损身份处理；更改口令或删除当前文件不恢复信任。不能保证已流出副本可回收。没有强推、删除远端引用或清除日志、产物、缓存等证据。

## 实际验证

现有 mise Temurin 21.0.11+10 LTS、项目 Gradle 9.8.1、已有 Android 平台 37 与 Build Tools 36.0.0；CI 使用其声明的 JDK 17。没有改全局运行时、SDK/依赖版本或设备设置。

| 命令/证据 | 本轮结果 |
| --- | --- |
| `python3 -m unittest discover -s scripts/tests -v` | 22 项通过；含导出、index 与 URL userinfo 规则测试 |
| 更新回归 RED | 16 项 artifact 测试中 7 项先失败；5 项解析测试中 3 项先失败。修复后全套通过；另以本机回环复现了 HTTP → HTTPS 被误阻断，补充 3 项传输策略测试 |
| `:app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest` | 本地完整命令成功；47 项 JVM 测试，0 failure/error/skip；Lint 0 errors、每种 variant 19 warnings，hint 仍报告 |
| `help -Pcinema.enableReleaseSigning=true` | 非零退出并出现明确 freeze 原因，符合预期拒绝 |
| aapt2 与 apksigner | debug 包为 `com.ichichuang.cinema.debug`；release 保留 `com.ichichuang.cinema`，unsigned 产物未通过签名验证且原因为无签名；版本均保持 18 |
| APK 内容 | 两种产物均只含 armeabi-v7a 的 Graphics Path/DataStore 原生库；没有上传或安装 |
| 独立代码复核 | 发现 Windows wrapper 移除自动 APK 检查的回归，已恢复检查调用和失败传播；当时候选未发现其他 Important/Critical 新回归；此后传输兼容收尾由回环 RED/GREEN 与单元测试覆盖。Windows 无可用 PowerShell，执行验收仍待该平台 |
| 原位升级/播放/遥控/性能 | 本轮无设备结果；没有扫描网络、连接电视或改变电视设置 |

离线基线先因既有 JUnit 缓存缺项失败，之后联网解析原有版本再验证。当前 AGP 未注册 `testReleaseUnitTest`，没有用任务名冒充测试执行。完整 Lint 已跑时，Gradle 的 lintVital 汇总可能显示 SKIPPED；它不代表完整 Lint 或 JUnit 被跳过。

## 分开验收

| 门槛 | 状态 | 尚需证据/决定 |
| --- | --- | --- |
| 安全事件关闭 | 未通过 | 离线恢复备份、独立信任引导、新签名保管、真实迁移与旧钥匙拒绝、受限泄露面处理 |
| 源码公开 | 未通过 | 历史/引用方案批准、原创及图像权属、许可证批准、最终第三方 notices 与私密报告/保护设置 |
| 正式发布 | 冻结 | 上述安全门、指定电视原位与后续更新验证、发布独立审批 |
| 原生界面改进 | 未开始 | 按顺序等待安全门；没有把 Lint 修复表述为界面完成或性能提升 |

[迁移评审材料](SIGNING_MIGRATION.md)与[第三方材料审计](THIRD_PARTY_NOTICES.md)给出可审阅的具体方案。`prepare_public_source.py` 只制作本地 review snapshot，不改变现有远端。已经针对 `7c3c260a3edf69ddc8286f2067c2c65924e49d13` 实际导出 121 个源文件，另生成归属说明；无私有目录/签名配置、无远端，独立 Gitleaks 与 index guard 通过。隔离目录初始化后仅 1 个本地根提交，旧源提交对象不存在。复用现有公共工具/依赖缓存，移除签名环境输入并禁用 Gradle 构建缓存后，完整 debug、unsigned release、两种 Lint 及 JVM 测试共 99 个 actionable tasks 实际执行成功；JUnit XML 为 44 项、0 failure/error/skip。该本地实验不是公开、历史删除或正式发布。
# 检查点后安全复审补记

2026-10-23，重新复核 `885cb0b59e7b17ba67f0134d072d2314edb523c0` 的缓存校验、取消与清理、Koin 单例与互斥、逐跳更新传输、完整 current-signer 比较、Gradle/Windows/Actions 冻结和隔离源码导出。确认两项 P2 导出缺陷：大小写不敏感卷上的 `source_history.md` 会被归属文件覆盖；`--root` 为子目录时可向真实仓库内导出。两例均先用合成仓库回归复现，再通过真实 Git 顶层规范化、保留名称大小写折叠检查和归属文件排他创建修复。Python 全套由 22 增为 24 项，全部通过。

本轮复审未发现其余所审路径的新增确定性缺陷；这不是全面安全认证。阻塞网络调用期间的 Android 取消、杀进程、存储故障和 Windows PowerShell 执行未验证。缓存/新下载共用 verifier、实际装配的实例级 Mutex 和完整 signer 集的源码审阅，不代替这些运行时证据。既有发布工作流在管理端仍为 `disabled_manually`，PR #1 保持草稿，private、正式签名/发布冻结与分发只读继续有效。

## 限定续轮的交付证据

从 885cb0b 继续，只读复核后修复两项导出器 P2；最终候选复核再修复夹具 PM 缺包探测、升级重建合成数据的假阳性，以及认证模块跨实例/旧 permit 清理别的成功文件，共五项 P2。每项先有失败回归；限定复核确认原问题闭环，未再发现阻断。它没有执行 Android 网络取消/杀进程/存储故障或电视验收。

- [迁移夹具与五包结果](../tools/migration-fixture/README.md)：独立包名、仓库外测试钥、实际 manifest 和 lineage 能力读回，API24–30签名工具验证；API30当前 artifact signer为A/B/B/A/C。没有 emulator，所以真实安装、UID/合成数据、后续 B 更新和高版本 A-only/A→C 的 PM 拒绝原因全部 BLOCKED。夹具 JVM 3项和Python命令边界回归仅验证逻辑。
- [认证更新协议](AUTHENTICATED_UPDATES.md)：28项候选测试+原有47项JVM，总75，0 failure/error/skip。签名伪造及文件竞争明确先失败再修复。生产常量关闭，无生产根、DI入口、legacy回退或清单变更；不是生产迁移。
- 本地最终完整 Gradle 命令两种构建/Lint/JVM通过，99个actionable tasks全部执行。Lint每种20 warnings和1 hint，0 error；包括在线依赖/Gradle版本可用性提示，未升级/抑制。另有既有 Kotlin 弃用、Gradle10兼容提示及两份原生库无法strip而原样打包提示。显式正式签名请求按固定错误拒绝。
- Python 全套32项通过。index guard仍仅检查选定规则；[独立增量扫描分类](evidence/scanner-classification.json)原始exit1/88命中均为83个实际缓存key摘要和5个读回的测试证书指纹，0未复核；没有关闭规则/添加allowlist。相对旧快照的APK变更条目（含DEX字符串和12份新通知）单独扫描16项，exit0/0命中。旧历史暴露面保留，未把这些检查当作全面审计通过。
- [实际组件/出货证据](evidence/component-inventory.json)和[许可材料](THIRD_PARTY_NOTICES.md)覆盖244份artifact、135份runtime source JAR版权核对、12份随包原文/署名；具体未知项仍保留。源代码/六图权属未批准，无根LICENSE。
- [缓存元数据](evidence/actions-cache-metadata.json)保留83项、4,684,050,658字节的ID/ref/时间/key摘要；payload未读取、执行或删除。[维护者待决事项](OWNER_DECISIONS.md)集中列明保管、引导、精确电视授权、历史/缓存处置和公开路线。

后续仅对最终已提交树做隔离源码导出与完整验证、最终SHA CI、APK身份及Git收尾，确切提交/导出数量/CI终态在PR #1记录。准备文档不等于已经启用的操作控制；本地干净历史不清理现有远端。代码审阅、合并批准、安全事件关闭、源码公开和正式发布继续分别报告。
