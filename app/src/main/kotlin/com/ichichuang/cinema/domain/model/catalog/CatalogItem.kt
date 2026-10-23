package com.ichichuang.cinema.domain.model.catalog

/** 最小作品资料；缺失事实不参与肯定或否定归类。 */
data class CatalogItem(
    val workId: WorkId,
    val title: String,
    val year: FieldEvidence<Int> = FieldEvidence.Unknown(),
    val originalLanguages: FieldEvidence<FactIds> = FieldEvidence.Unknown(),
    val productionCountries: FieldEvidence<FactIds> = FieldEvidence.Unknown(),
) {
    init {
        when (year) {
            is FieldEvidence.Unknown -> Unit
            is FieldEvidence.SourceReported -> require(year.value > 0) { "Known year must be positive" }
            is FieldEvidence.Confirmed -> require(year.value > 0) { "Known year must be positive" }
            is FieldEvidence.Conflict -> require(year.claims.all { it.value > 0 }) { "Known years must be positive" }
        }
    }
}

/** 版本音轨独立于作品语言和出品国家；不代表播放入口已验证。 */
data class MediaVariant(
    val variantId: VariantId,
    val workId: WorkId,
    val sourceId: SourceId,
    val sourceItemId: SourceItemId,
    val audioLanguages: FieldEvidence<FactIds> = FieldEvidence.Unknown(),
)
