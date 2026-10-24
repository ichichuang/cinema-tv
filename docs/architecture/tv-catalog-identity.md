# 候选目录身份与字段溯源

Round 08 执行中。限定离线合成夹具和显式候选 Room，不接入普通启动、DI、导航或播放器。

| 实际契约 | 本轮复用与补足 |
| --- | --- |
| WorkId / SourceId / SourceItemId / EpisodeId / VariantId | 复用既有不透明值类型；条目键由 SourceId 与 SourceItemId 组成；不从标题、年份、URL、列表位置分配永久身份 |
| EvidenceReference / FieldEvidence / FactIds | 复用原值、来源、规则版本、Unknown / SourceReported / Confirmed / Conflict；候选观察记录另保留字段名、字段存在性、原始 JSON、观察时间 |
| CatalogItem / MediaVariant | 保留既有签名和 codec；候选字段记录补足类型与字幕；原作品语言、制作国家、版本音轨和字幕独立 |
| RemoteVod / RemotePlaySource / RemoteEpisode | 复用现有 DTO；旧解析会丢失缺失与空值和原始 ID 依据，候选入口接收原始合成 JSON 与显式稳定身份，不调用旧网络解析器 |
| CinemaDatabase v3 | 原11张表、BLOB UTF-16身份编码和单写交接 authority 保持；新增候选关系使用显式3→4迁移 |
| work_references / favorites / playback_snapshots / browse_snapshots / legacy_import_records | 关联和拆分不得删除或改写既有个人行；不自动重分配workId或解析旧staging |

合成夹具必须使用明确 synthetic 标记、固定测试身份、无真实媒体和来源地址。可信关联与端点别名必须经显式、命名空间限定的本地证明，来源声明和标题加年份只能产生声明或候选。验证与最终接口随本轮实现补齐。

## 实际接口与持久化

`MaccmsCatalogNormalizer.normalize(SourceId, JsonObject, observedAt)` 不做网络访问。`NormalizedCatalogCapture` 保留完整原始JSON（含字段缺失、null、空串及不合法类型）、source/item、观察时间、规则版本、year与五组FactIds，以及每个规范字段对应的原字段名。`CatalogCaptureCodec` 复用原证据编码函数，只把其可见性改为internal，旧payload字节和版本不变；UTF-16 surrogate仍走原storageJson转义。

规则固定为 `maccms-exact-2026-10-24`：按精确有限词表映射。`vod_class` 类型与source-scoped `type_id` 分开，惊悚不等于恐怖，欧美不展开为国家。`vod_lang` 的裸语言标注不证明原作品语言；只有明确配音标签映射版本音轨。`work_original_language` 和 `subtitle_language` 是本轮合成夹具显式声明字段，其语义不冒充真实CMS普遍能力。所有标准值都是SourceReported，不自动成为Confirmed；原字段及非标准字段均保留。已知空集合与Unknown不同，冲突通过CatalogClaims.combine保留全部不同声明，观察历史仍独立存在。

`CatalogProjection.project(capture, declaredEntries)` 复用RemoteVod与播放DTO，只在内存转投影。每个entry、episode、variant身份由调用方显式提供；坐标仅用于指向该次输入，不能生成身份。缺少原始条目ID时返回未解析；原始capture仍可落盘。没有在线导入、默认片源或播放能力认证。

`CandidateCatalogStore(database, proofs, allocateId)` 必须显式构建。SourceId由受控调用方供应，WorkId首次事务内分配为UUID（测试注入固定序列），不从URL、标题、年份或坐标推导。原source item的homeWorkId永不改动；groupWorkId只是候选关联。事务验证capture与标准化结果一致，冻结entry输入；同capture token相同内容幂等、不同内容拒绝。相同条目的新观察追加记录，入口历史按capture保留，稳定entry不得改指另一条目／剧集／版本。

`CatalogIdentityProofs` 只接收显式本地核验的association与alias记录，构造时复制输入。关联证明绑定完整左右source-item键及命名空间和值；源JSON中的外部ID不能构造证明。关联前核对两组所有有效已验ID，各命名空间存在不同值即Conflict，包括传递链中的其他命名空间。无证明时标题／年份最多Candidate。证明、冲突、分组变更和拆分留审计；已撤销proof不得重复使用。

端点以精确字符串的无损BLOB表示。首次registerSource只注册新来源；既有来源换端点必须命中完整source、旧端点、新端点的授权证明。新端点若已有另一来源所有权仍拒绝，不使用相似域名、redirect或URL规范化授权。

显式split保守解散当前候选组，各条目回到各自原homeWorkId，撤销该组证明并保留原行与事件；恢复正确关联需要新的证明。这可能减少原有正确分组，但避免拆出一条目后其余成员仍通过已失效的传递证明误聚合。favorites、browse payload、逐集检查点、session/resume和legacy staging从不重分配。unavailable只设置来源状态，不删除目录或用户数据。

Room v4为原11张表增加8张候选表：catalog_sources、catalog_endpoints、catalog_items、catalog_captures、catalog_entries、catalog_routes、catalog_verified_identifiers、catalog_association_history。Source/item、work、capture和entry通过真实外键RESTRICT联系，条目复合主键、homeWork唯一索引及端点唯一键拒绝碰撞。ID沿用UTF-16 BLOB converter；schema1/2/3保持原字节，3→4只建新表与索引，工厂显式注册1→2→3→4，无destructive fallback。

## 权限与验收边界

普通Application、Koin、MovieRepository、导航、播放器、DataStore与交接writer选择均未接入此store。唯一共享候选变化是显式打开CinemaDatabase时的v4迁移。原生在专用API30 ARM64 AVD以固定serial逐命令门禁运行；不能代替ARMv7 TV、API24、真实来源或用户升级验收。

本轮证明仅覆盖合成元数据及显式测试证明。生产核验authority、真实源字段语义、订阅／查询协调、严格筛选、缓存容量／保留策略、真实用户关联与UI均未实现；候选历史没有自动回收，普通运行不产生该历史。不能标记整个T05、T03/T04、M02或A13/A16/A18完成。
