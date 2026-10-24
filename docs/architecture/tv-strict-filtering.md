# 候选严格筛选契约

本文件先于 R10-1 候选代码建立。基线为 `87303304947649a4a464c781b57feae8e01dbb04`，JVM 基线 276 项通过。总计划第七、八章与 T07 是目标语义；本轮只隔离候选，不修改普通应用的宽松筛选契约。R10-2 已在同一候选边界补充多维谓词；R10-3 已补年份选择；R10-4 再接入既有协调器。

## 实际接口映射

| 现有类型或入口 | 候选边界与不变约束 |
| --- | --- |
| `MovieFilter` / `DefaultMovieFilter` | 旧分类、lang、area、字符串年份、types 原样；首次国语默认不改。候选只接收不可变 `CatalogQuery`，无隐式互转 |
| `CategoryTree` / `Options.kt` | 旧来源分类树与到 2000 年的 UI 选项保持；不拿分类名猜内容类别、不把旧年份列表当候选合法范围 |
| `MovieRepository.movies` / `MoviePage.relaxed` | 旧接口明确允许降级；`MaccmsSource.plain/direct` 的宽松返回及 `MaccmsMovieRepository` 聚合保持，不调用新谓词 |
| `SwitchableMovieRepository` / `DemoMovieRepository` | 旧演示或活动来源路由不改；候选评估不获取来源、不调用旧 movies |
| `HomeViewModel` / `FilterSheet` / `HomeScreen` | 旧初始筛选、apply/reset、第一页与后续页 movies 调用及 relaxed 提示保持 |
| `DataStoreFilterStore` / `FilterStore` | `cinema_filters` 的 f_category/f_lang/f_area/f_year/f_types 与旧默认保持；候选不持有 Context、FilterStore 或任何 writer |
| `AppModule` | `MovieRepository` 仍绑定 Switchable，原 DataStoreFilterStore 和 HomeViewModel 构造保持；新 evaluator 无 Koin 接线 |
| `CatalogQuery.create` | 复用 queryId、contentKind、languageScope、countryIds、genreIds、yearStart/end、sort、enabledSourceIds；集合防外部修改，年份正数、闭区间，空来源不扩展 |
| `FieldEvidence<T>` / `EvidenceReference` / `FactIds` | 复用 Unknown、SourceReported、Confirmed、Conflict，以及 source/item/raw/ruleVersion；保留已知空集合和冲突区别，不改成 Boolean 标签 |
| `SourceItemKey` / `WorkId` / `CandidateCatalogStore` | 复用源条目复合键、home/group 身份和可信关联；不按标题加年份合并、不改播放入口 |
| `NormalizedCatalogCapture` / `MaccmsCatalogNormalizer` | 原文、时间、规则版本、year、facts 和 fieldNames 不可变保存；vod_year 是来源标注，work_original_language 与 vod_lang、subtitle_language 分离；旧 captures 不重写 |
| `TaxonomyRuleMapper` / `CatalogClaims.combine` | 复用版本化映射和保守事实组合；惊悚不等于恐怖，欧美不拆成美国；先组合已证同作品资料再评估，不先删除冲突来源 |
| `QueryCatalogEntry` | 当前只有身份、title、year、originalLanguages、countries、playbackEntryIds；缺 genres、contentKind 与语言完整性。R10-4 从已保留 captures 构建临时事实适配，不新造第二套来源身份 |
| `SourceCapabilityProbe` / `SourceCapabilityCache` | 当前能力观察区分字段供给与样本参数核对；不将 HTTP 成功当筛选保证。评估器无网络，也不把能力当逐条事实 |
| `QueryCoordinator` / `StoredQuerySession` / `SourceCursorState` | 原协调器已有严格年份与来源独立 cursor、generation、revision、预算、取消、失败；R10-4 替换其局部谓词，不新增协调器；R10-1 保持其显式拒绝多维条件行为 |
| `QuerySessionCodec` | 目前仅保存 queryId、yearStart/end 与 sources，未保存其他查询维度；R10-4 须对同一 payload 加可选字段与旧值默认，不能假称已有完整序列化 |
| `CandidateQuerySnapshot` / `CatalogResult` | verifiedCurrentMatchCount 是当前已证去重数量；knownMatchedCount 只有覆盖完成且相关事实可判定时才可给出，不能累加上游 total；PARTIAL 排序仅针对观察集合 |
| `RoomCandidateQueryStore` / `CinemaDatabase` v5 | 原事务保留 capture、来源游标、查询 payload 与投影 revision；本轮不增加表、不改 schema1–5、不动个人表或旧写者 |

## Ruling：接口兼容与职责

1. `StrictFilterEvaluator` 放在 `domain/filter`，是无副作用候选边界；输入使用现有 `CatalogQuery`、`FieldEvidence`，返回 `MatchVerdict`。R10-1 建立年份最小能力时曾明确拒绝非年份条件；R10-2 在同一边界扩展多维事实，只有年份事实的兼容调用遇到其他选中条件返回 INDETERMINATE，不再抛出不支持异常；R10-3 复用该年份判断或提取规范年份辅助，不维持两份年份判断。R10-4 接入原 QueryCoordinator。
2. 旧 `MovieRepository` 的软降级与目标严格语义存在明确差异。本轮裁定为隔离候选，不替换其文档、实现、默认、UI 或 DI；因此不宣称普通应用已完成 T07。严格候选只接受 MATCH，未知或冲突为 INDETERMINATE，空结果不改变 CatalogQuery。
3. 当前语言集合只证明观察到的作品语言成员，不能证明完整。后续适配须用独立、显式、有出处和规则版本的完整性依据；缺少声明时默认不完整，不能从 tags、字段存在、非空列表推导完整。华语允许有依据的中文成员正向命中，非华语需要完整、非空、无冲突并排除中文；已知空、未知、冲突均须可解释。R10-2 仅加入内存完整性事实，不增加持久化类型。
4. 现有词表还缺泰国/TH 与犯罪/crime，且没有 contentKind 事实。内容类别、这些新增精确映射、genres 和作用域缺口采用显式版本化候选内存适配解决，既有归一器规则版本和 immutable captures 保持。country 指制作国家，year 指原作发行年或明确来源简单年份；更新、修复、配音、字幕均不能代替作品事实。
5. 不同维度 AND，同维度 OR；未选择中立。显式不符吸收未知，OR 有证符合优先，否则未知优先。R10-2 实现该真值表；R10-1 的年份行为保持。
6. 不增加迁移、依赖、真实来源网络、信任根、默认目录或运行入口。Room v5 与旧 schema 字节保持；候选序列化扩展使用现有 payload，旧资料完整性不得追溯捏造。

## 证据边界

R10-1 测试应先观察新候选路径“未知年份不属于精确 1994”的真实断言失败，再实现修正；编译错误不能作为 RED。隔离测试使用真实 legacy DataStore 文件（JVM Preferences DataStore 工厂）作为外部状态哨兵，候选调用前后读取其旧年份和国语，验证空结果没有触发持久化放宽。它不等于真实 Android Context、TV 遥控或普通应用运行验收。完整 Round10 验证与最终证据另记，不在 R10-1 提交。

## R10-2 已实现 API 与事实裁定

- `StrictWorkFacts` 是不可变候选事实投影：`year`、`countries`、`genres`、`contentKind`、`originalLanguages`、`completeOriginalLanguages`。它复用 `FieldEvidence`、`FactIds` 与来源条目身份，不携带新查询或作品身份系统。
- `StrictFilterEvaluator.evaluate(query: CatalogQuery, facts: StrictWorkFacts): StrictEvaluation` 返回最终 `verdict` 与不可变 `dimensions`（year/countries/genres/contentKind/originalLanguages）。`FacetVerdict` 保存 verdict、reason、原始 evidence，语言负向证明另带 scopeEvidence。UNKNOWN、KNOWN_EMPTY、CONFLICT、INCOMPLETE_SCOPE 分开；未选为 UNSELECTED。同维已知集合任一成员命中即 MATCH；整个字段未知或冲突时，不能从争议成员挑选 MATCH。跨维 MISMATCH 优先于 INDETERMINATE。排序参数不参与谓词，不在此执行排序。
- `evaluate(query, year): MatchVerdict` 仍是同一评估器兼容入口：只提供 year，其他事实未知。原边界测试由“country 抛异常”调整成“country 无事实为 INDETERMINATE”，仍禁止静默忽略选中条件；旧 DataStore 隔离断言保留。
- 华语需要 `originalLanguages` 中可信来源声明的 `zh` 正向成员；包含英语不抹去中文。存在中文即排除非华语。只观察到 en 等非中文语言且没有完整作用域时，对华语和非华语都不能作否定分类。只有非空、无冲突、与当前语言集合一致的 completeOriginalLanguages 才能证明中文缺席；未知、已知空或冲突均不匹配精确分组。
- `data/catalog/StrictCaptureFacts.fromCaptures(List<NormalizedCatalogCapture>): StrictWorkFacts` 是 R10-4 的分组边界。调用者须先按现有可信作品身份组成同一 work 的 captures；适配器不合并身份、不读写 Room、不联网。它直接组合已存 year 与 originalLanguages，保留现有 year 语义；使用 CatalogClaims.combine 保守保留不同来源的冲突。
- 旧 `MaccmsCatalogNormalizer` 与 `maccms-exact-2026-10-24` 均不改。内存适配规则版本为 `strict-explicit-facets-2026-10-24`，仅在原 countries/genres 归一结果 Unknown 且 fieldNames 指向既有显式字段时补精确“泰国→TH”“犯罪→crime”。不推导“欧美→US”或“惊悚→horror”；原有已知事实、空集合和 Conflict 原样保留。
- 内容类别只读已保存 raw 中的显式 `work_content_kind`，精确值为 movie/series/variety/animation/documentary/other；type_name、type_id、标题、播放来源索引均不作为类别依据。没有该字段即 Unknown。
- 完整性只接受同一 capture 中 `work_original_language_scope` 的精确字符串 `complete`，且该 capture 本身有已知 originalLanguages 声明；无语言的裸 marker 不能与另一 capture 的语言拼装完整证明。完整性 evidence rawValue 同时保留语言与 scope 原文、sourceId、sourceItemId、新规则版本。该字段是显式候选扩展契约，旧资料缺省不完整，不由字段存在或非空标签推导；vod_lang、subtitle_language 完全不参与作品语言与制作国家判断。

R10-3 从该边界提取共享 year 判断（见下节）。R10-4 须扩展现有 QueryCatalogEntry/Room 投影与既有 QueryCoordinator 的接入，保存完整查询选择，按现有 work 组提供 captures，保持未知项与 coverage 证据；本节不代表普通 UI 或生产入口启用。


## R10-3 年份选择 API 与裁定

- `YearSelection.Exact(year)`、`Decade(startYear)`、`Range(startInclusive, endInclusive)` 是不可变候选选择意图，暴露闭区间 `startInclusive/endInclusive`。Decade 的 startYear 必须为正的十年起点（如 1980），表示 1980–1989。没有 2000 下限、当前年上限或任何库存数量属性；1895、1962、1987、未来正数年份均可选，不保证当前来源存在条目。
- 无效构造直接抛出 `InvalidYearSelection(reason: YearSelectionFailure)`：NON_POSITIVE、INVERTED_RANGE、INVALID_DECADE、OVERFLOW；不归一化、不截断、不转成 Unknown。Decade 先验证正数、整十起点，再在加 9 前检查 Int 溢出。Exact 和 Range 的 Int 正数上限仍合法。
- `CatalogQuery.withYearSelection(selection: YearSelection?, queryId: String): CatalogQuery` 返回现有不可变 CatalogQuery；null 只清除年份上下界。调用者显式传入 queryId；改变边界时复用原 ID 抛 QUERY_ID_REUSED，避免引导调用者覆盖已存查询的身份。其他字段（内容类别、语言、国家、类型、排序、启用来源）和原查询保持。此函数只构造选择，不执行放宽、不写持久化；R10-4 的显式 Relax 必须另提供审计说明。
- `YearFacetEvaluator.matchesYear(evidence: FieldEvidence<Int>, selection: YearSelection): FacetVerdict` 与 `StrictFilterEvaluator` 的 year 维度调用同一 `assess`；既有 CatalogQuery 单端 null 无界契约保留。未选为 MATCH/UNSELECTED；已选时 SourceReported/Confirmed 按闭区间判断；Unknown/Conflict 为 INDETERMINATE，保留实际 FieldEvidence 对象与原始 reference。现有 normalized capture 仍负责事实年份正数校验，此 API 不把原始自由文本强取四位。
- Unknown 的 observation.rawValue 精确为空串时返回 KNOWN_EMPTY；缺失、null 或无法解析仍 UNKNOWN，通过 observation 区分原始情况；不捏造年份事实。来源 vod_year 保持 SourceReported 与原归一规则 reference；更新、其他剪辑的数字发行日期、修复媒体日期均不能补作品年份。保守同作品组合中的年份冲突仍保留全部声明。
- `MaccmsCatalogNormalizer` 字节、规则版本、旧 Options/DataStore、CatalogQuery 原构造验证契约均未修改；没有年份库存生成器、网络、协调器接线、UI 或持久化变更。
