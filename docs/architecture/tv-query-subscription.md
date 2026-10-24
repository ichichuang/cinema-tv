# 候选来源能力、查询与订阅契约

Round 09 仅在显式隔离候选对象与合成夹具中执行。普通 Application、Koin、HomeViewModel、SettingsViewModel、MovieRepository、播放器、legacy DataStore 和默认空目录保持原路径。实际执行证据见同日 Round 09 验证报告。

## 起始接口映射

| 实际接口 / 类型 | 所有权与本轮用法 |
| --- | --- |
| SourceId / SourceItemId / WorkId / EpisodeId / VariantId | 原 domain.model.catalog 不透明值类型；SourceItemKey(sourceId,itemId) 为 Round 08 复合身份；不按域名、片名、年份生成身份 |
| NormalizedCatalogCapture | sourceId、可空 itemId、完整 raw JSON、observedAt、ruleVersion、year、facts、fieldNames；所有新探测复用原标准化与来源依据 |
| CandidateCatalogStore.registerSource / ingest / workFor / groupFor / captures / entries | 显式 Room 候选；稳定 homeWorkId 与候选 groupWorkId 分开；完整可信本地证明才可关联，保留来源独立入口 |
| CatalogIdentityProofs / VerifiedSourceAlias | 精确来源、旧端点、新端点证明；URL 相似、redirect 不授予别名权限 |
| CatalogRepository.observeQuery(CatalogQuery): Flow<CatalogResult> / suspend loadMore(queryId): LoadOutcome | 原 T02 签名不变；查询来源独立游标、会话代次、部分覆盖与取消保护 |
| CatalogQuery.create | queryId、contentKind、languageScope、countryIds、genreIds、yearStart/end、sort、enabledSourceIds；同 ID 不复用于不同条件；空来源不扩为全部 |
| CatalogResult.create | queryId、items、coverage、failedSourceIds、unknownFieldCount、knownMatchedCount、hasMore、snapshotRevision；knownMatchedCount 是可靠匹配总量，未完成时保留 null，当前已证匹配数单独表达 |
| LoadOutcome | Loaded / Complete / Partial(BUDGET_EXHAUSTED 或 SOURCE_FAILURE) / Cancelled / Failed(内部脱敏码)；协程取消继续抛出 |
| SubscriptionManager.refresh(SubscriptionRefreshRequest): Flow<SubscriptionState> | reason、catalogId、enabledSourceIds、consentRevision；授权须在候选事务提交时再次核对 |
| SubscriptionState | Idle / Checking / CatalogEmpty / CandidatesFound / Saving / Configured / Failed；Configured 只表示候选配置持久化，不证明媒体可播放 |
| SourceCapability | 起点无此 Kotlin 类型或实现；总计划只有概念名，本轮仅新增一组候选能力观察，不复制现有 domain 契约 |
| CinemaDatabase / CinemaDatabaseFactory.open(context,name) | 起点 v4，19 表，完整显式 1→2→3→4 迁移；不在启动自动开库；旧 schema 1–4 字节冻结 |
| metadataCache().evictAll() | 只删除 work_metadata_cache；原始 captures、routes、关联证明与个人记录不是可随缓存清除的资料 |
| SourceCatalog / CinemaSettings / ConfigServer | 原活动推荐目录、设置、手机配对与手动 LAN 路径保持原实现；候选不复用其宽松解析或网络策略作为公开目录信任授权 |

## 约束裁定

只实现候选年份证据核对和有界协调。全 T07 多维严格谓词与年份 UI 不实现；收到本轮不支持的多维条件须显式拒绝，不静默忽略。未知、冲突、缺字段不成为精确匹配。

`CatalogResult.knownMatchedCount` 沿用原 T02 可靠总量语义：部分查询保留 null，独立候选快照提供当前已证数量。完整覆盖只针对显式选择来源与所执行的合成查询，不能冒充真实来源、全库或媒体可用性。

公开目录只允许声明式数据，下载、发布者认证、用户授权、配置提交和媒体验证独立。手动 LAN 配置不是公开目录私网访问例外。生产 trust root、默认媒体目录及运行入口本轮不添加。

## 能力观察与缓存

`SourceCapabilityProbe.observe(context,observedAt,ttlMillis,captures,requestedYear?,requestedCategory?,health,unfilteredControl)` 是纯观察，不发网络请求。context 包含 SourceId、精确 endpoint、adapterRevision、protocolRevision 和 authorizationRevision。`SourceCategoryKey(SourceId,categoryId)` 不将两个来源同号分类视作相同类型。字段供给记录 present/nonNull/sampleSize；年/分类过滤分别为 UNKNOWN、CONTRADICTED、OBSERVED_ON_SAMPLE；健康另为 SUCCESS/TIMEOUT/TRANSPORT_FAILURE/PROTOCOL_FAILURE。

正向过滤证据要求非空样本全部有原始匹配事实，并有样本之外完整 source-item 键的未筛选反例。只观察一个过滤维度，避免对照混杂；正向结论仅针对该样本，不能推广为全来源永久支持。缺失、null、空值、空页或 HTTP 成功均不构成正向证明。反序列化与直接构造也执行同一完整性规则。

`SourceCapabilityCache.open/record/lookup/invalidateForAuthorizedAlias` 保存最新健康与最后成功观察；超时不擦除或延长旧证明。端点/协议/适配/授权修订变更、probe 规则变化、过期、时间倒退及精确授权 alias 均令旧证明不可当作当前能力。时间倒退对比持久化的最新观察（包括较晚超时），防止重开后回到旧成功证明的有效期而复活能力。别名只使能力失效，不代替 Round08 的端点所有权事务。缓存每来源保存最新一次成功探测，单次一个维度，不合成为未经核验的多维支持。

缓存上限256来源、每次200样本与200对照，完整编码上限1,048,576字符。`RoomSourceCapabilityPersistence` 解析前检查完整行长度，再由原 codec 验证语义；写入真实事务且提交前检查取消。显式实例遵守单写 ownership，跨实例并发刷新不在本轮承诺内。

## 独立游标查询

`QueryCoordinator(store,transport,budget,monotonicMillis,initialCursor)` 实现原 `CatalogRepository`；`start(query)` 与 `snapshot(queryId)` 只供候选控制/验证。`CandidateQueryTransport.fetch(source,cursor,query,maxBytes)` 只注入合成响应，未添加真实来源网络入口。支持基本年份证据核对，其他 T07 维度和非默认排序显式拒绝。

`StoredQuerySession` 持有 query、各 SourceCursorState、来源 evidenceKeys、已捕获/缺身份行数、projectionDigest 与 revision。每来源独立 nextCursor、已请求 cursor、页指纹、exhausted、failure 和 stopReason；分页空匹配不自行结束，原始 total 不充当跨源去重总量。只接受明确 exhaustion 或经核对的 Apple CMS page/pagecount，不因一个空列表宣布完成。重复页/循环/预算停止均保留 PARTIAL；失败保留原来源及重试 cursor。

默认一次 loadMore 至多16请求、1,048,576响应字节、5秒；并发抓取1。每来源32页、单页100行、会话500行、16来源、候选库8会话、会话payload262,144字节；构造参数还受硬上限校验。超限不静默放宽年份，不清掉个人记录或源证据；不提供自动全库扫描和后台补全。

`RoomCandidateQueryStore` 复用已注册 SourceId 的 `CandidateCatalogStore.ingest`、captures、home/group 身份与 entries。每个不可变观察分配独立 capture token，包含不匹配、未知和缺条目ID的原文；新观察不覆盖旧记录。查询不从片名/年份建立组，也不从播放数组索引发明 EpisodeId/VariantId；已有 Round08 显式声明入口保持独立，未声明入口的原始播放字段仍在 capture，不声称可播放。

先组合已验证组中本次范围的所有来源事实，再对年份做严格判断；冲突和未知不能靠先删另一来源来通过。可靠总量在 PARTIAL、相关年份未决或有缺身份行时为null；当前已证去重数量是 `CandidateQuerySnapshot.verifiedCurrentMatchCount`。每个来源条目保留 home、group 和 playbackEntryIds，即使展示作品去重也不删除来源入口。

证据写入、游标、投影摘要与修订号共用真实 Room 事务。显式 snapshot/后续加载发现关联或拆分改变投影时更新修订，不后台接运行观察器。旧查询 Run 被替换或观察取消后，延迟响应不得写入；观察清理在 NonCancellable 范围内完成。并发 snapshot 后返回的页仅在同一 Run、预期 cursor 不变时应用到当前 session，保留新的修订；source 与 query 都不共享全局页号。损坏 UTF-8 进入来源失败路径，孤立 UTF-16 键在 JSON/指纹的 UTF-8 边界前用原 storageJson 转义。

## 目录、信任、授权和候选保存

`DirectoryValidator` 只接受有界声明式目录；目录ID、origin、publisher、递增revision、issuedAt/expiresAt、source ID/name/endpoint 由精确规范编码参与 SHA256withRSA 验签。`CatalogTrustRule` 必须由受控调用方明确提供，绑定目录/源站/publisher/RSA公钥；没有默认生产根、不复用 APK 私钥、不从目录自带 key 建立信任。SHA256withRSA 的 Android 支持范围按官方 Signature 表核对；真实provider只在JVM/API30运行验证，API24设备仍未验。

JSON 在通用树构造前执行65,536字节、深度8、token2048、数组128、字符串和重复解码键限制；目录最多64来源，未知执行字段、脚本、坏UTF8、重复ID/endpoint、无效签名/时间均失败。默认空 sources 与其声明式 notice 仅产生 CatalogEmpty，不保存/清空配置或建立 publisher 信任。规范签名/目录格式是新持久化边界，原有 SourceCatalog 宽松格式和手动LAN路径未迁移。

`PublicDirectoryFetcher` 仅支持受限 HTTPS443、无用户信息/查询/fragment；DNS结果全部分类并绑定到新建、无代理/重定向/自动重试的 OkHttp client。每次实际连接复用同一经过分类的地址集合，重绑定不能用第二次DNS回答替代；全部3xx拒绝。响应流在读取时限制字节；caller取消实际 call.cancel。总抓取10秒、DNS2秒；默认exchange连接/读取3秒、call8秒，最多两条DNS工作线程且无无界队列。单个manager inspect整体15秒，默认并发2、最大4，无自动重试或后台循环。

地址规则是保守公共子集，拒绝环回、私网、链路本地、metadata、组播、CGNAT、映射IPv6与特殊IPv6，且额外拒绝整个192/8等范围。这是隔离候选安全策略，不宣称所有公网站点兼容；显式手动LAN设置仍走旧路径。验证只使用注入 resolver/exchange，不连接实际媒体或目录端点，真实公网TLS/socket未验。

`SubscriptionRefreshCoordinator.inspect` 下载/核验后只给候选；受控调用方 `CandidateSubscriptionStore.grantVerifiedConsent` 绑定实际review的目录digest/revision/origin/selected与本地consentRevision。`refresh` 复用原T02状态流，Saving之后才做真实事务；提交再次核对当前授权、停用tombstone、时间高水位、递增revision与会话token。目录发现、授权、保存和媒体可播放相互独立。Configured只证明隔离候选行已经提交。

`RoomSubscriptionPersistence.update` 在同一事务读取旧行、变换、写入、检查取消；旧个人/证据/检查点表不触碰。授权、配置、停用ID和独立时钟高水位编码在同一候选行；撤销授权不擦除防回退依据。失败、取消、旧响应、坏签名、重放和陈旧授权保持上次有效候选行；用户明确停用的来源不因刷新重启用，目录移除来源作为removed保留。最多64持久目录，新行容量在事务内检查，旧行可继续更新。

## 验收边界

所有实现只由合成测试显式构造；没有普通运行 DI、Application、Home/Settings、MovieRepository、播放器、DataStore 或旧writer authority 接线。原始 schema1–4、默认sources、应用ID、0.9.8/code18、minSdk24/target28、普通ARMv7、签名冻结均保持。生产目录接通、生产信任bootstrap、真实来源适配、TV/UI/遥控、真实用户迁移和完整 T06/T07/M03/A14/A15/A17/A18/A21 均未验收；本轮只补指定合成子场景证据并停止于Round09。
