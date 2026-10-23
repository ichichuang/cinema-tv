# 候选存储与旧记录保留契约

本轮限定于 Round 02：T02 的编码切片和 T03 的候选存储／合成旧记录保留切片。沿用 `tv-data-contracts.md` 的真实类型；旧 DataStore 仍是应用唯一活动路径。新工厂、DAO、store、reader 和 importer 均不接入 Koin、启动、导航或播放器。

## 字段映射

| 实际类型 | 完整存储字段与边界 |
| --- | --- |
| CatalogQuery.create | queryId、contentKind、languageScope、countryIds、genreIds、yearStart、yearEnd、sort、enabledSourceIds；保留 null、空来源集合、正数老年份和包含端点的区间 |
| BrowseSnapshot | contextKey 主键；route、parentRoute、query、resultRevision、focusedWorkId、anchorWorkId、relativeOffset、savedAt 全部编码；允许有限负偏移和负时间 |
| PlaybackSnapshot | workId、episodeId、variantId、sessionId、operationSequence、positionMs、durationMs、watchState、capturedAt 全部编码；可空身份与时长、超过陈旧时长的位置原样保留 |
| CatalogItem / MediaVariant | supplied WorkId／VariantId／SourceId／SourceItemId；title、year、originalLanguages、productionCountries／audioLanguages；年份、语言、国家和音轨的依据独立保存 |
| FieldEvidence<Int / FactIds> | Unknown（含可空 observation）、SourceReported、Confirmed、Conflict（保留 claims 顺序和全部 reference）；FactIds 已知空集合不同于 Unknown |
| EvidenceReference | sourceId、sourceItemId、rawValue、ruleVersion；原始空串保留，错误信息不包含内容 |
| CatalogResult、LoadOutcome、订阅类型 | 本轮不作为数据库记录持久化，不复制或改动公共契约；knownMatchedCount 的既有约束保持 |
| FavoritesStore | 按现有 String 边界验证 WorkId，selected 为幂等目标状态；缺失收藏的取消不创建 work reference |

所有实际集合值对象继续通过原工厂构造，使用原有结构相等语义。仅无序集合的编码排序；有序 claims 不排序。身份不 trim、不按标题／URL／下标推导。

## 旧文件格式与缺失信息

| DataStore 文件 | 实际键／类型／值 | 保留策略 |
| --- | --- | --- |
| cinema_progress.preferences_pb | String `wp_` + 原 movieId；`lineIndex,episodeIndex,positionMs,durationMs` 四段数字字符串 | 保留完整原键和原串；索引只是旧坐标。bare ID、来源限定 ID、碰撞及不明分隔符均不关联 canonical work；非法段隔离保留，不沿用旧 reader 的 duration=0 兜底 |
| cinema_browse.preferences_pb | String last_movie_id；Int last_index | 原样保留 ID、深列表索引；不伪造 context、route、anchor 或恢复现场 |
| cinema_filters.preferences_pb | String f_category、f_lang、f_area、f_year、f_types；types 用换行分隔 | 保存精确类型与原串，空串与未保存是不同状态；不套默认国语、不推导规范分类 |

旧格式未保存 canonical work／episode／variant 身份、逐集历史、session／sequence、watch state、观看时间或收藏。reader 只接受显式传入且独占拥有的 fixture DataStore，不解析生产 Context 默认属性，不扫描 settings、订阅或诊断数据。

## 编码、schema 与事务

`data/local/codec/` 提供内部 `StorageCodec<T>`，JSON envelope 固定 `kind`、`version=1` 和 `value`。枚举用明确字符串标签，不用 ordinal。query、browse、playback、catalog item、media variant 和 legacy snapshot 均可独立往返。缺字段、错误类型、非法值、未来版本或错误 kind 抛出无原始负载／cause 的 `StorageReadException`，不会改写原串。所有 surrogate 码元在 JSON 输出中转义，避免 UTF-8 边界替换不同 Kotlin String。

Room database version 1 包含七张表，实际生成文件为 `app/schemas/com.ichichuang.cinema.data.local.CinemaDatabase/1.json`。debug/release 共用该导出路径；两种实际生成的 database implementation 逐字节一致。没有旧 Room 版本、虚构的升级 migration 或 destructive fallback。

| 表 | 键与保留关系 |
| --- | --- |
| work_references | 调用方提供的 workId，无标题／网址推导 |
| work_metadata_cache | workId 主键，版本化 CatalogItem payload；外键 RESTRICT；evictAll 只删此表 |
| favorites | workId 主键，外键 RESTRICT；选中时同事务确保 reference，取消缺失收藏不创建 reference |
| browse_snapshots | contextKey 主键，完整版本化 payload，事务替换 |
| playback_snapshots | workId + episodeKey + variantKey 复合主键，外键 RESTRICT；仅内部低层 DAO，空键只表示 domain 不允许为空串的可空身份 |
| legacy_import_batches | importVersion + contentDigest 复合主键及捕获时间／三类计数 |
| legacy_import_records | batch 键 + storeKind + entryKey 唯一；指向 batch 的延迟外键 RESTRICT，四项旧坐标可空，rawValue／类型／状态／原因均保留 |

上述不透明 work/context/episode/variant/legacy-entry 键在 Kotlin 中仍是原 String，在 SQL 中为 BLOB。`Utf16KeyConverter` 按固定大端 UTF-16 码元编码，查询显式绑定相同 ByteArray；不经过会替换非法代理项的字符集 encoder。它是可逆存储表示，不分配新身份，不 trim 或猜匹配。奇数字节的损坏键显式失败。真实生成 DAO 的 bindBlob/getBlob 已核对；原生关闭重开仍待执行。

Browse save 和 favorite set 在 `withTransaction` 返回后才返回既有 `Committed`。预期 SQLite／I/O 写入错误映射为脱敏 `Failed`；读错误显式抛出 `StorageReadException`，load 只有缺行才返回 null。取消保持向上传播，不捕获 fatal 异常。直接 save 以数据库事务接受的调用顺序为准，savedAt 仅为数据，不负责跨导航或跨会话裁决。

## 旧记录保留流程

reader 对显式 handles 逐 store 捕获 typed entries，不承诺多个独立 DataStore 同时刻的快照；调用方必须独占其 ownership，并停止 fixture 并发写入。所有条目经防御性复制，按 store kind／精确 key 排序；版本化 canonical JSON 中包含 exact key、value type、value，保留集合语义及 Float/Double 原始位。SHA-256 输入排除捕获时间、文件路径及 mtime，且转义 surrogate 后才编码 UTF-8。

importer 在真实 Room 事务内保存全部 entries，再保存 batch accounting 和幂等标记；插行与 marker 的延迟外键在同一事务提交时满足。提交前比较实际行与预期行，核对总数与 recognized／unresolved／malformed 之和。已有同版本、同摘要的批次也核对完整 typed rows，不只核计数。相同内容不重复，新内容产生独立 staging 批次；任何批次均不写入 canonical 收藏、进度或作品身份。

格式可识别的旧筛选为 recognized；仍需关联的 ID／索引和未知键为 unresolved；非法坐标／类型为 malformed。状态不意味着已经迁移为可播放内容；原始 typed value 均保留。source read 失败不调用 importer 或写 marker。事务内 I/O、storage-full 或取消导致回滚；提交后 caller 可能失去确认，此时返回失败／取消不代表没写入，使用同一快照幂等重试核实。测试 hook 仅提供真实事务内外的可控注入点，不对运行时注册。

## 依赖与证据

固定 `androidx.room` runtime/compiler/plugin 2.8.5、KSP 2.3.12，AndroidX Test core/runner 1.7.0、ext junit 1.3.0。沿用 AGP 9.4.1、内置 Kotlin／Compose 2.4.20、Gradle 9.8.1、现有 mise Temurin 21（Java 17 target）、SDK 37、既有 SDK/ABI/版本与签名冻结。未添加 KMP、Robolectric、Bundled SQLite 或 serializer plugin。release 仍未签名。

版本依据：[Room 发布与 schema 插件](https://developer.android.com/jetpack/androidx/releases/room)、[KSP 2.3.12](https://github.com/google/ksp/releases/tag/2.3.12)、[内置 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)、[AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test)。实际解析、代码生成、两种构建和原生测试编译均执行；不是仅按文档假定兼容。

完整主机单测 124/124、Python 62/62；34 项原生测试源码已编译，执行为 0，缺少经核实的一次性 emulator。真实 Room 持久性、synthetic DataStore → Room 导入、事务注入、SQL 约束及 unsupported schema 保留均未作原生通过声明。原生测试只允许显式 pinned disposable emulator；无实体电视、个人设备或真实旧数据访问。详见 `docs/verification/2026-10-23-round-02-storage.md`。
