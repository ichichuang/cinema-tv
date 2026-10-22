# 资源、证据与迁移索引

盘点日期：2026-10-22。仓库内路径相对 cinema-tv；本地原始证据可能包含私有信息，不随文档提交。

## 归属与位置

| 资源 | 权威位置／来源 | Git 策略 |
| --- | --- | --- |
| 源码、构建和文档 | 本仓库 | 跟踪 |
| FongMi 技能 | `.agents/skills/fongmi-tv-setup/` | 跟踪，仅仓库发现，无全局副本 |
| 已发布 APK、标签与分发清单 | [cinema-tv-releases](https://github.com/ichichuang/cinema-tv-releases) | 只在分发仓库；不提交源仓库 |
| SDK、许可和用户状态 | `.local/toolchain/`，上游 Google SDK | 本地保留，忽略 |
| 发布下载、CI、响应头与标签证据 | `.local/evidence/release-artifacts/` | 原始小型证据，忽略 |
| 旧验证源码差异及原始报告 | `.local/evidence/verification-baseline/` | 逐字保留，可能含旧凭据，忽略 |
| 每个原文件的类型、大小、模式、SHA-256、重复关系、去向 | `.local/consolidation/inventory.json` | 私有清单，忽略 |
| 两仓库原 HEAD、refs、状态与文件摘要 | `.local/consolidation/repository-baseline.json` | 原始基线，忽略 |
| 构建、验证、清理和推送记录 | `.local/consolidation/` | 运行证据，忽略 |
| 签名库 | `keystore/release.jks` | 原有受跟踪文件原位保留 |
| Gradle 缓存 | `~/.gradle/` 及原有忽略目录 | 不迁移、不提交 |

## 三目录处置

原 `/Users/cc/MyPorject/TV/` 三个额外目录共 **12,399 个文件、752,728,656 字节**，均非符号链接。完整清单同时记录所有目录项；不是按名称推测后删除。

| 原目录 | 文件／字节 | 处置依据 |
| --- | --- | --- |
| release-artifacts | 10 / 42,190,458 | 7 份原始小型资料保留；3 份 APK 与 v0.9.6 标签文件逐字一致，去重 |
| toolchain | 11,757 / 702,796,983 | 11,756 文件复制到 `.local/toolchain/` 并逐一校验；ZIP 的 141 文件与安装组件全部一致，去掉重复归档 |
| verification-baseline | 632 / 7,741,215 | 保留 14 个差异文件及报告；84 个与原源码提交逐字相同，534 个为可再生构建输出和缓存 |

保留 **11,777 个文件、547,400,553 字节**，不含新生成的清单。按清单逐文件删除重复／再生资源，仅以 rmdir 删除空目录；实际结束状态见 `.local/consolidation/cleanup-result.json`，不以目标结构代替文件检查。

旧验证树主要对应 0.9.5；源码差异与报告保留为历史证据，不当作当前源码。重复源码可从基线提交 `e7d0c1c3bb6b2eb0cb7dccaf73d450c877d20444` 读取。原路径可存在于历史证据和迁移清单中，活动构建命令使用新 `.local/toolchain/`。

## 摘要与来源

| 资源 | 字节 | 校验与权威位置 |
| --- | --- | --- |
| cinema-tv 0.9.6 / code 16 | 14,060,687 | SHA-256 `044884ba63d0a671881ede43f15378e6e8fd40e2be3d0164daeaa9a09b7f7d6a`；[v0.9.6 文件](https://github.com/ichichuang/cinema-tv-releases/blob/v0.9.6/cinema-tv-0.9.6.apk) |
| cinema-tv 0.9.8 / code 18 | 14,093,455 | SHA-256 `81ae48b2f805415720e8663cfeb7bfdfa0343f6b5e2175a0d0677822e18801ee`；[v0.9.8 文件](https://github.com/ichichuang/cinema-tv-releases/blob/v0.9.8/cinema-tv-0.9.8.apk) |
| Command-line Tools 22.0 ARM64 ZIP | 156,083,281 | 官方索引 SHA-1 `c4e0dbc53f1a4ce04fc2b11d4e2c4675001a95af` 匹配；SHA-256 和提取物比较在本地清单 |

CLI 归档名为 `commandlinetools-mac_arm64-15859902_latest.zip`；官方索引保存在 `.local/toolchain/downloads/`，安装组件有 source.properties／package.xml。标准获取入口为 [Android 工具](https://developer.android.com/tools)。APK 证书一致性指纹见 [发布说明](RELEASE.md)。

## FongMi 上游与历史材料

官方来源：[FongMi/TV](https://github.com/FongMi/TV)、[FongMi/Release](https://github.com/FongMi/Release)、[配置文档](https://fongmi.github.io/TV/config/)。TCL 安装参考为 [Aerial v1.22 设备说明](https://github.com/pengchujin/aerial-tv/blob/v1.22/docs/devices/tcl-mt5879-cn.md)，只使用审阅后的安装机制。

中文直播候选：[CCSH/IPTV](https://github.com/CCSH/IPTV)、[suxuang/myIPTV](https://github.com/suxuang/myIPTV)、[vbskycn/iptv](https://github.com/vbskycn/iptv)、[iptv-org/iptv](https://github.com/iptv-org/iptv)。未来运行重新核验，不能据此保证维护状态和可播。

`/Users/cc/tv-optimization/` 保持原位，没有移动、删除或批量复制。已阅读的证据包括：

- `adb-baseline-20261021-234843-ra2yqsck`：连接与设备只读基线。
- `aerial-trial-20261022-000437-gxgv7g7b`：普通 ADB 拒绝及多屏安装机制。
- `video-install-20261022-003602-utxajk8q`：官方 APK、签名、已装摘要和 Owner 确认。
- `public-media-config-20261022-012829-xhgenzj6`、`mandarin-family-20261022-mwJZl6`：本地订阅、M3U、原生备份和实际有限播放。
- `live-family-20261022-_qcjnrg0`：候选收集，以及用户要求停止过长换台测试的记录。
- `family-home-20261022-2218`：最终 JSON 家庭列表、保护核验和精确恢复材料。

最终报告记录 96 频道、127 线路、六组菜单；不表示全表已实播。早期 13 台、五台 M3U、临时逐台核验 JSON 和最终家庭 JSON 是不同阶段，新任务以实际订阅为准。

历史 FongMi 5.7.8 ARM32 APK SHA-256 `27fdfebe5b847f95048e58e6d885b662f33e2651d3c07a5985bbeb8406182fdb`，安装证据确认与电视 APK 一致；只供历史核对，不固定未来版本。私人台标、订阅、原生备份和内容留在原工作区，仓库保存流程知识，不冻结播放源。
