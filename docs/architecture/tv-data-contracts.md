# TV 数据契约

本文件对应总计划第十一章及 Round 01 的数据契约切片。所有新类型位于 `domain/model/catalog`、`domain/model/userdata`、`domain/model/subscription`，接口位于 `domain/repository`。旧 `Movie`、`MovieFilter`、`MovieRepository`、DataStore、DI 和页面仍独立运行。

## 类型与身份

`WorkId`、`EpisodeId`、`VariantId`、`SourceId`、`SourceItemId` 是不同的 `@JvmInline value class`，包装非空白 `String`，不分配或推导 ID。作品身份与来源身份独立；来源条目由 `(SourceId, SourceItemId)` 限定。标题、年份、列表索引、域名及临时播放 URL 均不能作为内部永久身份的生成规则。合法字符串也不等于身份关联已经核验；本轮不实现关联算法。

`EvidenceReference(sourceId: SourceId, sourceItemId: SourceItemId?, rawValue: String, ruleVersion: String)` 保留原始来源字段及非空白规则版本；原值允许空串，以记录来源实际缺失。不得将整个响应、播放 URL 或凭据放入该字段。

`FieldEvidence<out T>` 分为：

- `Unknown(observation: EvidenceReference? = null)`：无标准事实；可保留一条不足以分类的观察。
- `SourceReported<T>(value: T, reference: EvidenceReference)`：来源标注，尚未确认。
- `Confirmed<T>(value: T, reference: EvidenceReference)`：确认事实及其可追溯依据；多数来源一致不自动成为确认。
- `Conflict<T>.of(claims: List<SourceReported<T>>)`：保留至少两个不同标准值及全部传入依据，不自动选赢家。

`T` 应为不可变值；本轮实际资料字段只使用 `Int` 和 `FactIds`。`FactIds.of(values: Set<String>)` 保存防御性复制后的规范 ID 集合，每个 ID 非空白；已知空集合与 `Unknown` 是不同事实。规范 ID 的映射规则、外部词表和冲突消解不在本轮实现。

`CatalogItem(workId: WorkId, title: String, year: FieldEvidence<Int>, originalLanguages: FieldEvidence<FactIds>, productionCountries: FieldEvidence<FactIds>)` 是结果中最小作品资料；后三项默认 `Unknown()`，已知年份须大于零。`MediaVariant(variantId: VariantId, workId: WorkId, sourceId: SourceId, sourceItemId: SourceItemId, audioLanguages: FieldEvidence<FactIds>)` 是独立版本资料，音轨默认未知。它不包含已验证可播放的结论或播放入口实现。

原作语言、出品国家/地区和版本音轨分别存储。缺少原作语言不能归入非华语；一小段双语对白可以作为 `Unknown.observation` 保留，不能单独证明华语作品；中文配音也不能反推原作语言。契约测试只验证表达能力，真实归类和证据充分性由后续规则及样本验证。

## 查询

通过 `CatalogQuery.create` 构造：

| 字段 | Kotlin 类型 | 语义 |
| --- | --- | --- |
| queryId | String | 非空白查询会话身份；相同 ID 不复用于不同条件 |
| contentKind | ContentKind? | null 不限制；MOVIE / SERIES / VARIETY / ANIMATION / DOCUMENTARY / OTHER |
| languageScope | LanguageScope | ALL / CHINESE / NON_CHINESE；默认 ALL，不负责给作品分类 |
| countryIds | Set<String> | 规范国家/地区 ID，空集合不限制 |
| genreIds | Set<String> | 规范类型 ID，空集合不限制 |
| yearStart / yearEnd | Int? | 包含端点的公历年份界限；null 无界，非 null 大于零；起点不得大于终点 |
| sort | CatalogSort | DEFAULT / YEAR_ASCENDING / YEAR_DESCENDING；未知年排序规则待查询实现规定 |
| enabledSourceIds | Set<SourceId> | 本会话选择的来源；空集合表示没有启用来源，不自动启用全部 |

同一维度集合表示任一所选值，各维度交集；此处定义输入语义，不实现筛选。1994、1990–1999 及单端无界范围有效；不得用 0、自动交换或扩大范围替代非法输入。

通过 `CatalogResult.create` 构造：

| 字段 | Kotlin 类型 | 语义 |
| --- | --- | --- |
| queryId | String | 对应查询会话 |
| items | List<CatalogItem> | 当前累计结果快照，顺序属于本次结果 |
| coverage | QueryCoverage | PARTIAL / COMPLETE，独立于列表是否为空 |
| failedSourceIds | Set<SourceId> | 失败来源，默认空，不表示这些来源没有内容 |
| unknownFieldCount | Int | 本快照涉及的未知资料字段数量，非负；不是未匹配数量 |
| knownMatchedCount | Long? | 可靠的匹配总量，未知为 null，不从 items 大小推导 |
| hasMore | Boolean | 当前有可继续的加载工作；false 单独不能证明覆盖完成 |
| snapshotRevision | Long | 同一 queryId 内非负修订号；不跨会话比较 |

`COMPLETE` 要求没有失败来源且 `hasMore=false`。空 `PARTIAL` 可以 `hasMore=true`，也可以因为失败/预算暂停而为 false；都不能表述为已确认无匹配。计数及修订号不得为负。

`LoadOutcome` 为 `Loaded`、`Complete`、`Partial(reason: PartialReason)`、`Cancelled`、`Failed(reason: String)`；部分原因是 `BUDGET_EXHAUSTED` 或 `SOURCE_FAILURE`。`Loaded` 只表示有加载进展，当前覆盖状态以观察流为准。失败 reason 是非空白内部错误代码，不能直接作为界面文案或承载原始异常、URL、凭据。

集合值对象通过私有构造函数及工厂防御性复制，暴露不可修改的集合视图；其 data-class copy 可见性与构造函数一致。`CatalogItem` 和标量快照的普通 copy 仍运行构造校验。本轮不提供编码器或解码器，不承诺序列化往返。

## 用户记录

`PlaybackSnapshot` 字段：`workId: WorkId`、`episodeId: EpisodeId?`、`variantId: VariantId?`、`sessionId: String`、`operationSequence: Long`、`positionMs: Long`、`durationMs: Long?`、`watchState: WatchState`、`capturedAt: Long`。

`WatchState` 是 `NOT_STARTED / IN_PROGRESS / COMPLETED`。位置、已知时长和序号非负，未知时长用 null，位置 0 是有效值。剧集/版本尚未确定时允许 null，不伪造关联。位置允许超过陈旧的已知时长；快退也不因位置变小被视为旧事件。sessionId 非空白，序号只在一个会话内排序；后续存储必须在事务边界建立跨会话顺序，不能按 ID 字典序或墙上时钟判断新旧。capturedAt 为 Unix 毫秒时间，用于记录而非排序授权。

`BrowseSnapshot` 字段：`contextKey: String`、`route: String`、`parentRoute: String?`、`query: CatalogQuery?`、`resultRevision: Long?`、`focusedWorkId: WorkId?`、`anchorWorkId: WorkId?`、`relativeOffset: Float`、`savedAt: Long`。

contextKey 和 route 非空白；parentRoute 非 null 时非空白。query / resultRevision 允许无查询或未知修订，已知修订非负。relativeOffset 是锚点起点相对可用视口起点的滚动主轴偏移除以视口长度；允许负数以表达部分遮挡，必须有限，savedAt 为 Unix 毫秒。焦点/锚点缺失时，未来恢复器优先使用仍存在的稳定焦点、再锚点、再当前结果的第一个可聚焦项；无内容落到对应空/错误状态的合法操作。不实现恢复器或 UI。

`CommitResult` 是 `Committed`、`RejectedStale`、`Failed(reason: String)`。只有目标存储实际完成提交才能返回 Committed；RejectedStale 不覆盖新记录；Failed 不谎称已保存。调用取消应向上传播，不能伪装成功或普通失败。值对象及测试不能证明持久性。

## 订阅

通过 `SubscriptionRefreshRequest.create` 构造：`reason: RefreshReason`（USER_REQUEST / AUTOMATIC_REFRESH / RETRY）、`catalogId: String`（非空白内部身份）、`enabledSourceIds: Set<SourceId>`、`consentRevision: Long?`（未知授权为 null，已知修订非负）。下载目录不产生 consentRevision；未来管理器必须核对本地真实授权及其修订。存在数字修订也不能独自证明授权有效。

`SubscriptionState`：`Idle`、`Checking`、`CatalogEmpty`、`CandidatesFound(count: Int)`（大于零）、`Saving`、`Configured(configurationRevision: Long)`（非负）、`Failed(reason: String)`。

Configured 仅表明配置提交到指定修订。它不证明目录内容完整、入口可读或媒体可播放；播放可用性需要后续按入口、时间和设备记录真实验证。目录空、找到候选、保存中及失败均不等于配置提交，失败须保留最后有效配置。本轮只有状态值，没有刷新状态机、目录检查或资源 URL。

## 接口与迁移交接

严格保持总计划的方法签名：

```kotlin
interface CatalogRepository {
    fun observeQuery(query: CatalogQuery): Flow<CatalogResult>
    suspend fun loadMore(queryId: String): LoadOutcome
}
interface PlaybackProgressStore {
    suspend fun commit(snapshot: PlaybackSnapshot): CommitResult
}
interface BrowseStateStore {
    suspend fun save(snapshot: BrowseSnapshot): CommitResult
    suspend fun load(contextKey: String): BrowseSnapshot?
}
interface FavoritesStore {
    suspend fun setFavorite(workId: String, selected: Boolean): CommitResult
}
interface SubscriptionManager {
    fun refresh(request: SubscriptionRefreshRequest): Flow<SubscriptionState>
}
```

- CatalogRepository 管理独立查询会话与来源游标；observeQuery 必须保留覆盖范围和失败来源，loadMore 只继续指定查询，不共享页码。订阅次数不自动代表新用户授权；会话资源生命周期、并发策略留给真实实现及测试。
- Flow / suspend 调用传播协程取消及 Flow 异常透明性，不将 `CancellationException` 捕获后包装为 Failed。`LoadOutcome.Cancelled` 仅用于调用者仍存活时观察到查询会话已经被显式取消；协程自身被取消仍抛出取消信号。参考 [Kotlin Flow 契约](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/)。
- PlaybackProgressStore 原子校验目标会话与序号并提交；BrowseStateStore 按 contextKey 隔离保存/加载。load 返回 null 只表示没有已提交记录，I/O 失败不得伪装缺失；查询/授权等未知 ID 的具体失败码在实现时补齐。
- FavoritesStore 使用显式 selected 的幂等设置，重复请求不得盲目翻转。按总计划保留 String 边界，传入值必须是 WorkId.value；接口实现仍须验证及解析身份。
- SubscriptionManager 依据真实检查/配置提交事件发状态，不能在无持久化写入时发 Configured；不因失败、停用来源或临时网络问题删除个人记录。
- 现在仅新增接口，无实现、假仓库、DI 注册、双写或迁移。旧读取和写入全部继续。
- 未来迁移先取得授权且脱敏的旧样本，建立幂等导入及同事务完成标记。切换写入方必须在独立任务中明确完成；不得旧新存储同时写。导入中断保留旧记录及可重试状态，不通过删库恢复。
- 缺来源、旧编号不确定或无法关联的记录原样保留为待解决记录，不按片名/序号猜作品，不跨错误关联迁移进度。旧存储没保存的逐集历史、观看时间和收藏不得凭空补造。

## 边界决定与待验收项

第十一章字段和方法名均保留。其未规定的 Kotlin 类型、可空条件、枚举值和偏移单位按上文显式定义；本轮没有下游消费者。分轮计划明确允许暂不实现编码，因此序列化、数据格式兼容、事务/取消/并发运行行为、严格查询和完整订阅流程均留待后续独立任务验证。本轮单测不等于 T02 全部、M01 或 A01–A24 验收。
