# 认证更新候选协议与启用边界

本模块位于 `data/update/auth/`，是待集成的隔离候选：无网络请求、无生产信任根、无 DI 注册，`production()` 由固定 `PRODUCTION_ENABLED=false` 拒绝。现有 GitHubUpdater 和旧 release manifests 未改动，现有 HTTP 媒体不受影响。当前生产 legacy 更新仍缺少独立发布者认证；增加候选代码不关闭该风险。本轮没有发布/迁移签名或生产密钥生成。

## 独立信任与初次安装

新信任根必须在维护者以独立渠道确认的一次安装中预置，例如对预先核实的发布者指纹和包摘要进行线下交叉确认后，通过正常系统安装流程引导。受损旧钥签出的 lineage 或 APK 本身不能证明替换身份属于合法维护者；候选 APK、HTTP 清单及其 key 字段都不能学习/替换根。根轮换和恢复程序须另行评审，本候选不实现远程根更新。

维护者尚未批准生产根、保管方式及具体引导流程，所以没有生产激活入口。生产集成还需要实际 PackageManager current signer 适配、真实设备存储/时钟/中断测试、更新专用传输限制、用户流程审阅及明确批准。调用方不能在认证异常后访问旧清单、换到未认证镜像或继续安装。

## 精确字节和边界

网络层必须在读入内存前限制 envelope 大小为 16,384 字节。模块再次检查长度；它不代替网络读取限制。二进制 envelope 顺序如下：8 个 ASCII 字节 `AUTHUPD1`，4 字节无符号含义的大端 payload 长度（接受范围 1..8192），该长度的原始 payload，2 字节大端签名长度（384..512），签名本身；不得有尾随字节。负数或越界长度拒绝，不能分配攻击者声称长度的缓冲。

签名算法固定为 JCA `SHA256withRSA`（PKCS#1 v1.5 + SHA-256），不接受网络算法选择；本地根只接受 RSA 3072..4096 位、指数 65537。测试在内存生成可丢弃密钥，不嵌入生产材料。使用平台密码 API，不自行实现 RSA。

签名覆盖**原始 payload 的全部字节**，包含协议名、key ID 和末尾 LF。只允许可打印 ASCII 32..126 与 LF，无 BOM、CR、空白归一化、JSON 或可忽略扩展。字段次序严格固定，每行 `name=value`，最后必须恰有一个 LF：

```text
cinema-update-auth/1
key=test-publisher
package=sample.app
sequence=1
version=19
size=4
sha256=3a6eb0790f39ac87c94f3856b2dd2c5d110e6811602261a9a923d3bb23adc8b7
signers=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
min-sdk=24
max-sdk=35
abis=armeabi-v7a
not-before=1799999900
expires=1800001000
url=https://updates.example.invalid/fixture.apk
```

这是测试示例，域名不可用，也不授予该 key 任何生产信任。

- `key` 为 1..64 个 ASCII 字母、数字、下划线或连字符，只在独立预置 map 内查找；未知 key 拒绝，不联网寻找根。
- `package` 为至多 255 字节的点分包名；须精确等于当前包身份。`version` 为 1..2^31−1 的 Android versionCode，必须高于实际已安装版本；未修改生产 versionCode/minSdk。
- `sequence` 为 1..2^63−1 的发布序号，独立于版本号，所有 publisher key 共用同一个包的持久化 floor。数字不允许正负号、前导零、指数或溢出。
- `size` 为 1..536,870,912 字节；`sha256` 为 64 个小写 hex。下载后精确比较大小和流式 SHA-256，再读取包名、版本与**完整当前** signer 集；历史证书不作为当前 signer。
- `signers` 是唯一获准的完整当前 signer 集，1..8 个排序、无重复的 SHA-256 指纹，用逗号分隔；不按“包含某一个旧证书”接受。
- `min-sdk`、`max-sdk` 为 24..100 且前者不大于后者；调用时实际 API 必须在范围内。`abis` 为排序、无重复的 Android ABI 名集合，实际执行 ABI 必须匹配。候选设置不决定 API 24–27 的迁移支持政策。
- `not-before` / `expires` 为 UTC Unix 秒，前者不早于 2024-01-01，有效期必须正数且不超过 31 天。当前时间必须在闭区间内。`url` 最长 2048 字节，必须为可解析 HTTPS URL，禁止 userinfo 与 fragment；URL 也被签名。后续集成仍须逐跳拒绝 HTTPS 降级，不能改动媒体 client。

任何重复、未知、缺少、乱序、非规范字段和未认证字节均拒绝。解析只为选择已预置根和有界验证，认证与所有适用性检查完成之前，不更改持久化记录，也不返回 download permit。

## 重放、重试与时钟

`FileUpdateReplayStore` 必须放在应用私有、不参与备份回滚的目录。独立安装引导显式初始化一次；平常缺失/损坏/不可读时拒绝，不能自动重置。调用方必须从真实安装状态提供 package/version/API/ABI；不使用远端声明充当本机身份。

文件记录最高 sequence、原始 payload SHA-256、对应 version、是否确认安装以及最近接受的墙钟秒。进程锁和系统文件锁覆盖读改写；同目录临时文件先 `fd.sync()` 再 rename，写失败不产生 permit。不能把它描述为防 root、防系统备份回滚的硬件单调计数器；文件系统断电持久性和实际 Android 存储故障尚未验收。

在返回 permit、允许下载之前持久化新 sequence。低 sequence 拒绝；同 sequence 只允许尚未完成且 payload 摘要完全相同的重试；更高 sequence 的 version 不得低于已授权候选的 version。更高 sequence 使旧 permit 失效。不同 verifier 实例的 permit 不能交叉使用，重启后须重新验签获取 permit。

网络/取消/大小/摘要/包身份错误清理本操作的 `.part` 和失败最终文件，不消耗同载荷的 pending 重试。下载成功也不声称安装成功；仅由实际系统安装观察确认精确 package/version/current signer 后调用 `confirmInstalled` 标记完成，之后同序号拒绝。进程在安装后、确认前退出时，启动适配须读取真实已安装版本，禁止重复降级；该生产适配本轮未接入。

未设置时钟、时钟读取异常、低于持久化最近接受时间、未到生效时间或已过期均拒绝。下载前后及安装确认均重新检查有效期；下载不会延长元数据寿命。网络中断期间若元数据过期，必须取得新的有效认证发布声明。时钟前跳可能造成拒绝服务，需要受信时间/人工恢复流程；不以忽略 expiry 或回退 legacy 绕过。防离线 freeze/replay 的保证受墙钟和私有状态未被回滚的前提限制。

## 本轮证据

同一规范目标的下载由进程级共享锁和目标文件锁保护，锁覆盖写入、验证和失败清理；初始 permit 拒绝不取得文件所有权，只有本次成功替换了目标时才清理自己的失败文件，避免跨实例失败操作删除其他已验证文件。锁文件保留在私有缓存目录，不在释放后删除；进程退出由系统释放锁。

真实 JCA 签名测试覆盖伪造、逐字段篡改、错误/未知根、有效期、解析限制、适用性、降级/重放、持久化重启、摘要/包身份、取消和中断重试。移除签名检查的实现使三项安全断言明确失败；加入 JCA 验证后候选测试通过；复审又通过双实例交错和过期 permit 回归复现并修复文件所有权问题。最后共 28 项候选测试及原有 47 项测试通过。JVM 结果不代替 Android API 24/30 密码提供者、PackageManager 或真实下载网络的验证。

依据：[Android Signature API](https://developer.android.com/reference/java/security/Signature)、[Java Signature API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html)。生产启用仍被门控。
