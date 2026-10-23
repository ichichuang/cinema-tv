package com.ichichuang.cinema.domain.model.catalog

/** 不透明内部身份；不得由标题、地址或列表位置推导。来源条目需与 SourceId 一起使用。 */
@JvmInline value class WorkId(val value: String) {
    init { require(value.isNotBlank()) { "WorkId must not be blank" } }
}
@JvmInline value class EpisodeId(val value: String) {
    init { require(value.isNotBlank()) { "EpisodeId must not be blank" } }
}
@JvmInline value class VariantId(val value: String) {
    init { require(value.isNotBlank()) { "VariantId must not be blank" } }
}
@JvmInline value class SourceId(val value: String) {
    init { require(value.isNotBlank()) { "SourceId must not be blank" } }
}
@JvmInline value class SourceItemId(val value: String) {
    init { require(value.isNotBlank()) { "SourceItemId must not be blank" } }
}
