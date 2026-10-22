# 安全整改执行计划

日期：2026-10-23。起点：远端 `8204cc0`；专用分支 `security/remediation`。历史测试不作为本轮证据。

目标：先完成可独立执行的安全修复与完整构建，再准备公开源码；安全门未关闭前不进入界面改造。依据本轮维护者完整整改要求和 `SECURITY.md`，直接在当前会话执行。

## 约束与验收门

- 源仓库保持私有；正式签名、发布、APK 上传冻结；分发仓库只读。禁止强推、删除远端引用或改写生产包身份、设置和进度。
- 旧签名身份视为泄露。备份必须在仓库外加密并验证恢复；本机备份不能冒充离线副本。
- 没有维护者批准的独立信任引导、新密钥安全保管和具体电视授权，不做迁移签名或设备安装。旧密钥派生的 lineage 不证明替换身份合法。
- 保留现有更新字段兼容；清单内摘要只提供完整性检查。发布者认证与签名迁移需一起审阅，不静默启用未经批准的新信任根。
- 使用已有 mise JDK、SDK、Gradle 和依赖；不通过压低检查、修改版本或增加基线绕过失败。
- 最终分别报告安全关闭、源码公开、正式发布和界面完成状态；不得用静态检查替代设备验收。

## 执行任务

- [x] 保护原签名材料及历史：仓库外加密恢复包含 Git bundle、旧签名库和凭据配置；从系统钥匙串取回备份口令并实际解密比对，验证签名库可打开。离线副本待维护者完成。
- [x] GitHub 管理端禁用旧发布工作流 `387692952` 并读回；保留普通验证工作流。核对私有可见性、历史 runs 和当前失败步骤。
- [x] 安全回归：`data/update/GitHubUpdater.kt`、`UpdateTransportPolicy.kt`、相关纯 Kotlin 校验/文件生命周期单元及 `app/src/test/.../update/`。先复现版本、包名、签名、篡改、缓存与失败清理缺口，再修复并跑 JUnit；不放宽签名历史信任。
- [x] 构建修复：从实际 Lint 报告确定 `app/src/main/` 的兼容性错误；只修复阻断项。验证 debug 和 unsigned release、两种 variant 的 Lint 与单元测试、显式签名请求拒绝及 APK 身份。
- [x] 扩展审计：`scripts/check_repository_security.py` 仍仅是 index 回归检查；另用扫描工具/审计脚本检查 reachable blobs、branches/tags、可访问 PR refs、日志、artifacts、caches、release assets 和文档。只输出位置及类别，记录权限和覆盖边界；不得删除证据。
- [x] 历史发布准备：新增可离线验证的 clean-history 导出程序及测试；验证文件内容与归属说明保留、无旧 refs、无私有产物；不推送导出树。
- [x] 开源准备：核对原创权属、依赖 POM、图像/字体/资源与 notices，更新 `README.md`、`docs/DEVELOPMENT.md`、`docs/RELEASE.md`、`docs/OPEN_SOURCE_READINESS.md`、`SECURITY.md`、贡献说明和模板。提出许可证建议，未经维护者决定不新增 LICENSE。
- [x] 迁移评审：对照 Android 官方文档及本机 `apksigner` 帮助，记录 Android 11/旧系统差异、已安装 updater 的证书限制、独立信任引导与逐设备原位升级验收；没有实际授权设备时标明阻断。
- [ ] Git 收尾：明确路径分批暂存、检查候选、中文提交并推送专用分支；核对新 SHA 的 CI 终态。公开、正式发布与界面工作保持门控。

## 验证入口

`python3 -m unittest discover -s scripts/tests -v`；`python3 scripts/check_repository_security.py`；既有 Gitleaks（输出脱敏）；mise 既有 JDK 下执行 `:app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest`，以及 `help -Pcinema.enableReleaseSigning=true` 的预期失败。读取测试 XML 的实际数量与 skip/failure，不以 Gradle 任务名称推断测试已执行。当前 AGP 未注册 `testReleaseUnitTest`，不新增重复任务。离线基线因既有 JUnit 缓存缺项失败，后续联网解析原版本。

重点复核：同版本/降级与过期缓存；缺失/畸形及完整 signer 集；失败后 `.part` 与最终文件；更新传输限制不影响用户 HTTP 媒体；未认证清单与受损旧密钥的剩余风险。

## 检查点 885cb0b 后的限定执行

依据维护者继续执行指令：本轮只做迁移预验证、生产关闭的认证更新候选和开源材料；不合并 PR、不改 main、不公开、不生成生产密钥、不连接电视。实现直接在本会话完成，按下列独立边界提交。

- [ ] 安全复审：复核缓存验证、取消/清理、下载互斥、逐跳重定向、完整当前 signer、发布冻结、源码导出；发现问题先加入失败回归。只对实际覆盖范围作结论。
- [ ] 迁移夹具：`tools/migration-fixture/` 保存独立包名的纯 Java 合成数据应用和可重复实验脚本；密钥、lineage、APK 仅生成在仓库外。使用已有 SDK/apksigner，显式 rotation-min-sdk=28、旧钥 rollback=false。API 30 emulator 可用才安装并验证 UID/数据/current signer、B 后续更新、A-only 和 A→C 拒绝；不可用即阻断。API 24–27 不宣称恢复安全。
- [ ] 更新认证：`data/update/auth/` 为无网络、无 Android 适配依赖的隔离模块；固定 ASCII 签名载荷和 RSA/SHA-256，信任根只能由独立引导提供。明确长度/字段限制、未知根拒绝、时钟失败、持久化 sequence 与同载荷重试；生产常量关闭且无现有 updater 调用。不改旧清单或 HTTP 媒体。真实 JCA 签名、重放、文件摘要/签名身份和重试测试先红后绿。
- [ ] 开源材料：从实际 resolved artifact 与 APK 检查生成可复查组件清单、哈希、资源/许可文本映射，区分 runtime/build/test；保留 Apache-2.0 建议及权属待决。集中记录离线备份、密钥保管、bootstrap、电视授权、历史/caches 与公开路线；仅提出缓存检查/删除方案，不执行缓存。
- [ ] 最终验证与交付：Python、index guard、全部 JVM、两种构建/Lint、显式 freeze；对最终 SHA 的隔离源码导出检查归属/内容/旧对象并验证构建。分批中文提交、推送、等确切 SHA 的 CI 终态；分别报告代码/合并、安全事件、公开和发布准备度。

设计边界：认证失败不能降级到 legacy；最高 sequence 在授权下载前持久化，下载中断仅允许完全相同载荷重试，安装确认后拒绝重放。损坏/丢失的持久化记录与回退的时钟均拒绝，不从候选包学习信任。实验 lineage 只证明测试平台兼容性，不能证明替换密钥的合法所有权。
