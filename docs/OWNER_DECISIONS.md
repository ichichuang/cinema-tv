# 维护者待决事项与准备度

2026-10-23 收尾记录：代码与本地平台证据检查点为 `18500366838ee71c8c9cd2ac181ea8504bb22936`，工作分支 `security/remediation`，草稿 PR #1 尚未合并；远端 main 仍为 `8204cc0`。本次只校正 PR/文档并做一次限定合并复核，不扩大实现或重复通用审计。检查点在下述范围内技术验证通过，不代表事件关闭、电视验收、源码公开、正式发布或界面工作完成。

## 已确定的范围与控制

维护者已要求：源仓库保持 private；PR 保持 Draft、未合并；生产认证关闭；正式签名/发布冻结；分发仓库不写入；保留历史、缓存和证据；本次不生成生产钥、不签生产迁移包、不连接电视、不改界面。上述限制继续有效。已有同机加密恢复结果、测试矩阵和历史设备资料均可引用，不要求重复提供；它们不自动构成独立离线恢复、当前目标授权或生产激活批准。

Apache-2.0 仍只是建议，未添加根 LICENSE；公开目的仓库和历史/缓存处置路线未被选定。技术建议、CI success、GitHub mergeable 和未回复都不等于维护者批准。

## 技术证据与本次复核范围

- 已核对的确切提交：`18500366838ee71c8c9cd2ac181ea8504bb22936`；[CI 39883316273](https://github.com/ichichuang/cinema-tv/actions/runs/39883316273) 的 headSha 与之相同，终态 completed/success。GitHub CI 实际运行 Python 38、158 文件索引检查、夹具状态 JVM 3、签名冻结、双构建/双 Lint、应用 JVM 75（0 failure/error/skip）；没有启动 AVD 或重跑 Android 安装/harness。
- 本地平台证据：在 [1850036 的既有夹具结果](https://github.com/ichichuang/cinema-tv/blob/18500366838ee71c8c9cd2ac181ea8504bb22936/tools/migration-fixture/artifact-report.json)中，7 个独立全新迁移 AVD、21 次实际安装（17 成功、4 次 INSTALL_FAILED_UPDATE_INCOMPATIBLE）覆盖合法 A/1001→B/1002→B-only/1003、两迁移状态各自的 A-only/1004 与竞争 A→C/1005 拒绝，以及两个 A 状态可安装正对照。UID/合成数据保持不变。B/1002 历史为 A/B，B-only/1003 历史仅 B；不是通用撤销机制，拒绝由实际 PM 结果证明。
- 另一个隔离 Android harness 复用应用真实候选类，9 项限定运行时检查通过：JCA、PM 当前 signer、真实进程重启 replay、受控 HTTPS 中断与重试/清理、有界 envelope、历史集合误用拒绝和注入存储失败。测试根/适配器不在生产 source sets，注入故障不是设备存储损坏；掉电持久性和生产启动恢复未验收。
- 复用未变更代码的既有本地验证：Python 38、夹具 JVM 3、应用 JVM 75；99 个 Gradle actionable tasks 全执行，debug 与 unsigned release 完成；双 Lint 各 0 error/fatal、20 warning。签名请求被固定冻结拒绝。隔离导出 158 个精确源文件并重新构建/测试；其成功不清理现有远端。

本次源码复核对象是 `ecdef5ce094e05641ca4d1cc96a475eaecdab92a..18500366838ee71c8c9cd2ac181ea8504bb22936` 的 11 个文件差异，并沿下表只追踪现存生产边界。新增可执行内容限于迁移工具、nonce 观察、独立认证 harness 和相应回归；此区间的 app、Gradle 配置及 workflows 字节均未变化。本次不是对 PR 全部历史差异的重新独立审计，没有重审许可、历史 cache payload、Windows、API 24–27 运行时或实体电视。

| 限定复核项 | 固定提交中的依据与结论 |
| --- | --- |
| 生产隔离 | [production()](https://github.com/ichichuang/cinema-tv/blob/18500366838ee71c8c9cd2ac181ea8504bb22936/app/src/main/kotlin/com/ichichuang/cinema/data/update/auth/AuthenticatedUpdateVerifier.kt#L119) 固定 PRODUCTION_ENABLED=false；[DI](https://github.com/ichichuang/cinema-tv/blob/18500366838ee71c8c9cd2ac181ea8504bb22936/app/src/main/kotlin/com/ichichuang/cinema/di/AppModule.kt#L64) 仍装配 GitHubUpdater，无候选生产根或新注册；候选内部不引入 legacy 回退。现有 legacy 缺独立认证的风险仍在。 |
| 签名/发布冻结 | [Gradle 冻结](https://github.com/ichichuang/cinema-tv/blob/18500366838ee71c8c9cd2ac181ea8504bb22936/app/build.gradle.kts#L10) 在读签名配置前拒绝显式申请；默认 release 无 signingConfig。verify 只构建 unsigned、不上传 APK；[release workflow](https://github.com/ichichuang/cinema-tv/blob/18500366838ee71c8c9cd2ac181ea8504bb22936/.github/workflows/build.yml) 为手动拒绝，管理端已核对 disabled_manually。 |
| 测试钥/适配器分离 | Gradle 仅 include(:app)，无指向 tools 的 sourceSet 或自动运行 harness 的构建任务。测试 Java/网络/PM 适配器位于 tools/migration-fixture；测试钥、TLS 证书和 APK 仅由显式工具在新建仓库外目录生成，私钥权限受限。跟踪文件中没有这些私钥/签名二进制。 |
| 生产身份保持 | [app 配置](https://github.com/ichichuang/cinema-tv/blob/18500366838ee71c8c9cd2ac181ea8504bb22936/app/build.gradle.kts#L31) 保持 com.ichichuang.cinema、versionCode 18、minSdk 24、targetSdk 28、armeabi-v7a；debug 独立 .debug，测试包另用 migrationfixture/authruntime。 |
| 新增回归与证据有效性 | runner 拒绝非 emulator、非 API30、已有夹具和摘要不符；负向场景单独从全新 AVD 开始，nonce 排除旧观察，PM 原因与版本/UID/current/data 均须符合。测试 harness 显式核对 AVD 身份，使用受控 loopback TLS 并清理自身 reverse；新增流程没有接入生产调用链。记录的回归、平台数据和 CI 相互一致；本轮不重跑已有效矩阵。 |

**限定结论：未发现新增的合并阻断缺陷，建议维护者批准后将已验证整改代码合入 main，同时保持上述生产控制关闭。** 没有新增阻断项要求继续通用整改。该建议以此明确范围和既有证据为限，不是全量漏洞保证；CI 与 mergeable 不替代维护者决定，本执行器未合并、未转 Ready、未改分支保护。

仍未解决但不应伪装为“缺模拟器”的事项：生产 legacy 缺独立发布者认证、下述生产集成尚未交付、指定厂商电视/用户数据与播放验收、API 24–27 政策、独立恢复/密钥/信任和公开权利。这些阻断对应生产或公开操作，不自动阻断仅合并保持冻结的整改代码。测试 D8 元数据警告、两种 Lint 各 20 warning 和 Windows 未执行边界保留，未通过改规则消除。

## 独立生产实现门（尚未交付）

认证候选测试通过后，仍须另行界定并实现以下工作，不能以本次合并或文档批准视为完成：

1. 生产更新 transport：在缓冲前限制 envelope、逐跳执行更新专用传输策略，认证失败不访问 legacy。
2. 信任根预置：依维护者批准的独立渠道固定元数据公钥及替换 APK 身份；网络响应不能学习或替换根。
3. 依赖注入：审阅并替换实际 updater 调用链及用户流程，不把 harness 的测试构造器/根注册到生产。
4. 启动恢复：从真实 PackageManager 状态恢复安装与 pending 状态，处理安装后确认前退出、私有 replay 存储、时钟和中断。
5. 激活与验收：前述实现和独立首次安装完成后，经明确授权才改变 production gate，并单独完成指定电视的原位/后续更新、旧钥拒绝及启动/播放验收。

## 维护者控制的最小决定清单

下表集中列出仍缺的输入。已提供的事实只需引用或确认本次仍有效，不要求重述；只记录决定、负责人、公开校验值或证据位置，不在聊天中请求私钥、密码、恢复口令或订阅凭据。

| 受阻操作 | 已知状态 / 已有决定 | 最小具体维护者输入 |
| --- | --- | --- |
| 代码合并 | 本轮限定技术复核建议合并，PR 仍 Draft，生产控制必须保留 | 明确批准或拒绝将 PR #1 的指定最终 HEAD 合入 main；若有条件，列出条件。此决定不授权签名、发布或公开。 |
| 原创许可与图像权利 | Apache-2.0 只是建议；6 个原图（5 密度 launcher + 1 横幅）未换，依赖 notice 不授予原创权利 | 对原创/引入代码及贡献授权作确认并明确许可证选择；提供六图可再分发的权利确认或证据位置。只补缺失权利信息，不重复广泛许可调查。 |
| 独立离线恢复 | 同机仓库外加密 archive 已恢复并比对；不等于独立离线备份 | 指定不同故障域的离线存储/责任人及独立恢复资料保管方式，并给出一次实际离线恢复的证据位置；不发送口令。 |
| 生产 APK 与元数据签名钥保管 | 仅有可丢弃测试钥，生产钥未生成 | 分别指定两类钥的责任人、安全存储、备份/恢复与签名审批安排，并明确何时另行授权生成；不提供钥或密码。 |
| 可信首次安装渠道 | 受损旧钥 lineage 不能证明新维护者身份；尚无获批生产根 | 指定独立于旧钥/更新镜像的已验证渠道、根指纹/迁移 APK 摘要确认责任人和首次安装程序；本轮不执行安装。 |
| API 24–27 支持政策 | 不支持 v3 lineage；minSdk 仍 24，无已验证的通用保数据换钥方案 | 明确旧平台的安全更新/迁移政策与向用户披露的边界；如选择重新安装等路线，须另行批准数据影响，不能静默抬 minSdk 或把受损旧钥续签当安全恢复。 |
| 指定电视与允许操作 | 已有历史设备资料；没有本次实体电视操作授权，模拟器不能代表厂商固件 | 确认已有设备身份/连接资料本次是否仍有效，指定唯一目标和允许的原位/后续更新、旧钥拒绝、数据与播放验证范围及时段；现场再独立核对身份。无需重发已有资料或凭据。 |
| 历史引用、证据与缓存处置 | 历史仍保留；83 个 cache 的元数据已有，payload 未读/执行/删除；本次保持保全 | 明确保全范围、保管期/责任人，再选择仅保留、隔离只读检查的具体 ID/获取方式，或经保全后的逐 ID 删除；历史/refs 改写与证据删除分别授权，不能批量推断。 |
| 公开源码目的地 | 只有本地可归属的干净导出；未选目的仓库或可见性切换路线 | 选择所有者/组织及精确目标仓库或 URL，并明确新根导出或经另审清理的既有远端路线、归属与引用保留方式；满足权利/安全门后另行授权创建/发布/公开。 |

本地导出不清理现有远端 branches、PR refs、GitHub 对象、日志/artifacts/caches 或第三方副本；不得直接将现有敏感历史仓库改为 public。源码公开路线、技术建议与维护者批准始终分别记录。

## 未审计 Actions caches 的限定提案

先前调查仅保存元数据快照 [actions-cache-metadata.json](evidence/actions-cache-metadata.json)：逐项 ID、ref、大小、创建/访问时间和 key 摘要，不包含凭据值或 cache payload。普通验证 workflow 已禁用 cache；本次收尾未复用或执行旧缓存，也不在签名环境恢复。

选择检查时，先由维护者批准具体 ID 范围和只读获取路径；在无生产凭据、无网络发布权限的隔离环境取得原始压缩内容并加密保全，校验摘要，限定总解压量，仅读取文件/扫描，不执行脚本/JAR/二进制、不安装依赖、不启动任何缓存程序。当前 GitHub cache REST 列表只给元数据，没有取得 payload 的已验证途径；未准备自动恢复 workflow，获取路径不可用时继续标记未审计，不声称扫描通过。

选择移除时，应先批准证据保全方式、保管期及快照中明确的 ID；只调用 `DELETE /repos/ichichuang/cinema-tv/actions/caches/{cache_id}`，逐 ID 读回确认，不按模糊 key、分支或全仓库通配删除。清除缓存不能撤销已暴露的签名身份，也不证明其他副本不存在。本次收尾没有执行删除。

## 分开的准备度

| 层次 | 当前结论 | 尚缺条件 |
| --- | --- | --- |
| 技术检查点 / 代码合并 | 1850036 在记录范围内验证通过；本次限定复核无新增合并阻断，建议在冻结不变的条件下合并 | 维护者对 PR 指定最终 HEAD 的明确合并决定；PR 仍 Draft，未执行合并 |
| 生产更新保护 | 默认关闭的候选取得限定测试；正式应用尚未接入 | 上述独立生产 transport、根预置、DI、启动恢复与激活实现及验收 |
| 安全事件关闭 | 未关闭 | 独立离线恢复、生产根保管/独立引导、旧身份/历史/缓存处置和真实生产迁移验收 |
| 源码公开准备 | 未批准 | 原创/图像权利、许可和目的地/公开路线明确批准；本地导出不是远端清理 |
| 正式发布与电视 | 冻结 / 未验收 | 生产密钥与可信迁移、指定电视原位/后续更新/旧钥拒绝及启动播放的授权和验收 |
| 界面工作 | 本次未开展 | 不从整改合并推断界面改造授权或验收 |

本次仅修改本文件和 SIGNING_MIGRATION.md，并校正 PR 正文；复用未变化的可执行代码、工具配置和平台证据，不重跑完整构建或 emulator 矩阵。文档验证检查链接、事实一致性、改动范围和敏感内容；若 PR 同步触发新的 CI，其结果须与收尾文档提交单独关联，不把旧 run 改写为新 SHA 的验证。PR 和本次交付使用仓库相对路径或固定提交 GitHub 链接；此前会话中的本机文件链接属于报告可移植性问题，本身不是个人路径进入源码提交或凭据泄露的证据。
