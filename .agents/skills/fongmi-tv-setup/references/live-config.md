# 中文频道整理与实际配置格式

## 来源与验证状态

维护现有订阅优先。需要发现来源时，用中文／英文检索频道、官方直播和维护中的 GitHub 项目；候选可从 CCSH/IPTV、suxuang/myIPTV、vbskycn/iptv、iptv-org/iptv 开始，链接见仓库 [资源索引](../../../../docs/RESOURCES.md)。重新核对文件、维护和出处，不冻结路径、数量或可信度。

优先可归因的公开免费直播；不得猜 URL、绕过订阅、付费、认证、DRM 或地区限制。候选列表不是上游授权或可用性证明。不要将全球清单直接导入家庭菜单。流内电视广告本身不是假频道证据，也不能承诺持续免费无广告。

分别记录收集数、语法有效数、有限网络可达数、电视实际抽查项和 Owner 接受项。Mac 可达不表示电视可播，Mac 失败也不能单独否定电视上已工作的流。默认不全表 HTTP 探测；确有诊断需要时只检查少量代表项，合理超时、并发上限通常为 3，不反复请求分片。

明显占位、捐赠视频、冒充直播的固定 MP4、无关外语、格式错误及已证实错台的流移出家庭表，原始输入和排除原因保留。合理但未重测的候选保留为未验证，不因短会话没有测过就删除。身份不清的条目进恢复来源池，不能靠名称猜测或贴 CCTV 标识。

## 格式与字段

[官方配置字典](https://fongmi.github.io/TV/config/) 会变化，应与安装版本的 LiveParser、Channel、LiveConfig 及实际文件交叉核对。历史 5.7.8 对应源码检查点为 `4de3f2d53554d51fd4afca71c21d5397b072f579`；同 versionName 未必等于完全相同源码。

| 文件或字段 | 结构／用途 |
| --- | --- |
| live-config.json | 顶层对象含 lives 数组；每项 name 与 url，可引用同目录文件 |
| 外置 channels-family.json | **顶层直接为 Group 数组**，不是再次包 lives；每组 name、channel 数组 |
| 内嵌频道 | 放在 lives[].groups；不要把外置和内嵌层级混用 |
| Channel | name 与 urls 字符串数组；可用 number、logo、tvgId、tvgName、epg、ua、header、referer、origin、parse、format 等实际支持字段 |
| 多线路 | 一个频道的 urls 内保存；非 DRM 情况可按对应版本支持的 `$线路标签` 命名，不能误改 URL 中的特殊字符 |
| M3U | EXTM3U 与 EXTINF；tvg-id/name/logo/chno、group-title，支持的 UA／Referer 等指令保留 |
| M3U → JSON | tvg-id→tvgId、tvg-name→tvgName、tvg-logo→logo、tvg-chno→number；保留 EPG、请求头、追看等，无法无损转换就不转换 |

M3U 是频道清单，不是 HLS 媒体分片清单；不得误将带 EXT-X-TARGETDURATION 等标记的媒体 m3u8 当频道表。JSON 和 M3U 不通过改扩展名互换。不要复制其他 TVBox 分支的 clan URI 或未证实字段。

历史主配置使用 `file://TV/Family/live-config.json`，引用 `./channels-family.json`；boot=true 是原有用户选择，不给新设备自动启用。timeZone 可按可靠 EPG 时区设定，不能捏造节目。导入方法与 URI／台标路径区别见 [TV_SETUP](../../../../docs/TV_SETUP.md)。

## 身份、分组、备用线路

先根据来源、可靠 tvg ID 和台标／节目证据建立播出机构身份，再规范名称。如 CCTV-1 综合、CCTV-5 体育、CCTV-5+ 体育赛事、湖南卫视、钱江都市；这些仅是命名例子，不提供任何播放地址。

按照常看、央视、卫视、地方台、少儿教育、其他排列，省略空组；地方台较多时按实际省份细分。央视数字排序保留 5 与 5+ 的差异，优先熟悉频道。常看不必复制频道到另一组，避免重复计台。改名／重编号可能影响版本相关的收藏匹配，维护已有表时先保全并谨慎调整。

完全相同播出机构、播放 URL 和请求参数才可去掉重复。独立备用 URL 留在同一频道；同 URL 但 header／Referer／parse 不同不能简单合并。不要删除／排序签名查询参数、片段或路径，不按 URL 外观认定两个 CDN 真正独立。新整理通常选少量独立备用；既有更多线路保留，不静默截断。

台标优先复用已验证的本地文件或可靠上游；核对台名、比例和透明边界，不用错台图。EPG 的 tvg ID／名称仅在身份和实际支持明确时添加；无可靠数据就省略，不生成节目单。候选／补测／测试／demo／线路1 不作为顶层频道名称。

## 本地 JSON 工具

`scripts/normalize_playlist.py` 只处理**已经整理好身份和分组的外置 JSON Group 数组**。它不联网、不开 ADB、不猜台名、不分配 EPG、不改请求字段，也不把 M3U 作有损转换。原文件保持，输出必须是新的显式路径：

```sh
mise exec -- python3 .agents/skills/fongmi-tv-setup/scripts/normalize_playlist.py \
  --input /绝对路径/原频道.json --dry-run
mise exec -- python3 .agents/skills/fongmi-tv-setup/scripts/normalize_playlist.py \
  --input /绝对路径/原频道.json --output /绝对路径/整理后频道.json --organize
```

默认只去掉同一频道内逐字相同 URL，并合并同组、同名、其他元数据完全相同的条目；元数据冲突保留并报告。`--organize` 排家庭组并将组内 CCTV 按数字排，其他顺序保持；`--renumber` 才重写编号，应在已授权重编号时使用。未知字段保留，不静默舍弃备用线路，空组省略。工具输出是条目／线路统计，不自动证明唯一播出机构数或可播。

处理 M3U 时在本地保留原文、全局头和逐频道指令，使用有限解析过程逐项核对后再决定输出格式；不能只按逗号切所有文本、忽略请求头或剥离 token。最终部署前验证 UTF-8、结构、相对依赖和 URL 多重集合，所有移出项在来源池可恢复。
