# 构建、签名与分发

这是现有协议的维护说明。APK 发布、标签和分发仓库变更须属于当前明确授权范围。

## 产物与责任

| 对象 | 权威位置 | 含义 |
| --- | --- | --- |
| 源码提交 | `ichichuang/cinema-tv` | 源码、构建配置、文档和技能 |
| 本地 APK | `app/build/outputs/apk/release/`，Git 忽略 | 本机产物，不自动等于已发布包 |
| 签名 APK | 经过实际包身份和签名核验的文件 | 仍需设备验收 |
| 版本标签 | 分发仓库 `v版本号` | 固定 APK 的分发引用，创建后不移动或覆盖 |
| `version.json` | 分发仓库 main | 更新清单；源码 `update/` 为准备副本 |
| `sources.json` | 分发仓库 main | 独立于 APK 更新的可选来源目录 |
| Actions artifact | 源仓库 CI | 临时构建产物，不等于永久发布 |

整理前基线：源提交 `e7d0c1c3bb6b2eb0cb7dccaf73d450c877d20444`；分发 main 为 `5e3d56021ed1bc38988f4d711e6fe93dc4f46cb5`。两份当前版本清单一致，均为 **0.9.8 / 18**。文档提交会推进源 HEAD，不代表新增应用版本。

## 发布核对顺序

1. 确认源分支、干净工作树、上游和候选验证对应同一提交；记录源 SHA。
2. APK 的 versionName／versionCode 与构建配置一致，versionCode 单调增加。本次不修改版本。
3. 按 [开发指南](DEVELOPMENT.md) 构建并检查包名、SDK、电视入口、ABI、签名、大小与 SHA-256。
4. 保持签名连续性。已发布 0.9.6、0.9.8 的证书 SHA-256 均为 `5c3ae14663ca57023e33e9775255aa2906c712e849fa0c542f799bd4edfa6abd`。不同则停止，不能靠卸载用户应用或换钥匙解决。
5. 在独立待审查文件中准备 APK 和清单，核对 sizeBytes；同名目标已存在时先比对，不能直接覆盖。
6. 核实远程与目标标签状态后，仅在发布已获授权时执行普通提交、新标签推送。已存在标签只读验证；从标签读回 APK 对比摘要，再核实 main 清单与实际下载路径。

当前 v0.9.8 是附注标签，解引用为 APK 提交 `8c468395a0b08a6a839df4739ec262dd85026648`；随后 main 提交更新清单。标签处不必含最新 version.json，不能为此重写旧标签。

原 `keystore/release.jks` 和构建配置中的签名凭据已在私有源仓库中。本次不删除受跟踪密钥、不改口令、不轮换证书、不改写历史；不要把凭据复制进新文档或命令。改变源仓库可见性须单独处理凭据和升级连续性。

## 应用实际协议

依据 `CinemaSettings.kt`、`GitHubUpdater.kt`、`UpdateInstaller.kt`：

- 默认依次读取 `https://gh-proxy.com/https://raw.githubusercontent.com/ichichuang/cinema-tv-releases/main/version.json`、`https://cdn.jsdelivr.net/gh/ichichuang/cinema-tv-releases@main/version.json`。自定义地址非空时使用自定义列表，清空后回到默认。
- 使用**第一个解析成功的清单**，没有并行选择最高版本；同进程普通检查间隔 10 分钟，强制检查例外。CDN 可能缓存旧清单，不保证立即发现更新。
- 当前字段为 versionCode、versionName、apkUrl、sizeBytes、notes、mirrorUrl；解析器还兼容若干旧别名，不更改此契约。
- APK 主链为固定版本标签的 jsDelivr 地址，镜像为相同标签的 raw 代理地址。新发布使用新标签，禁止覆盖旧标签。
- 下载先写 `.part`；有 sizeBytes 时检查实际大小。网络失败依次尝试主链和镜像，包身份校验失败不继续镜像重试。
- 校验包名和清单版本；双方证书指纹都可取得时才阻断不一致。**任一指纹取不到时当前实现不阻断**。清单和下载器未实现 APK SHA-256 字段校验，维护者须另外留证，不能声称应用完成摘要认证。
- `REQUEST_INSTALL_PACKAGES`、FileProvider 和系统确认完成安装，不是静默升级。TCL 还可能施加固件策略，见 [电视运维](TV_SETUP.md)。

SourceCatalog 单独拉取和缓存 sources.json，读取 `sources[]` 的 name／url／info。目录更新不自动改变 APK；用户在本地选择启用来源。

## 历史脚本风险

| 脚本 | 处理边界 |
| --- | --- |
| `publish-to-repo.ps1` | 含 `git add -A`、`git tag -f`、强推和目标覆盖；**不安全，不要运行**；NoPush 仍会改本地清单 |
| `publish-update.ps1` | GitHub Release 附件发布路线，会改清单和远程对象，不等于当前分发仓库协议 |
| `publish-sources.ps1` | 会改目录并推送，仅在明确授权且审阅后使用 |
| `build-local.ps1` | 旧 Windows 构建入口，不做远程发布 |

本次均未执行或改写上述脚本；不更改应用更新契约、不操作发布标签和分发仓库。
