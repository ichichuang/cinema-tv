package com.ichichuang.cinema.domain.model.catalog

import java.util.Collections

data class EvidenceReference(
    val sourceId: SourceId,
    val sourceItemId: SourceItemId?,
    val rawValue: String,
    val ruleVersion: String,
) {
    init { require(ruleVersion.isNotBlank()) { "ruleVersion must not be blank" } }
}

/** T 必须是不可变值；本轮资料字段只使用 Int 和 FactIds。 */
sealed interface FieldEvidence<out T> {
    data class Unknown(val observation: EvidenceReference? = null) : FieldEvidence<Nothing>
    data class SourceReported<T>(val value: T, val reference: EvidenceReference) : FieldEvidence<T>
    data class Confirmed<T>(val value: T, val reference: EvidenceReference) : FieldEvidence<T>

    @ConsistentCopyVisibility
    data class Conflict<T> private constructor(val claims: List<SourceReported<T>>) : FieldEvidence<T> {
        init {
            require(claims.map { it.value }.distinct().size >= 2) { "Conflict requires different reported values" }
        }

        companion object {
            fun <T> of(claims: List<SourceReported<T>>): Conflict<T> =
                Conflict(Collections.unmodifiableList(ArrayList(claims)))
        }
    }
}

/** 规范语言、国家或类型 ID 集合；不实现词表映射。 */
@ConsistentCopyVisibility
data class FactIds private constructor(val values: Set<String>) {
    init { require(values.all { it.isNotBlank() }) { "Fact IDs must not be blank" } }

    companion object {
        fun of(values: Set<String>): FactIds = FactIds(Collections.unmodifiableSet(LinkedHashSet(values)))
    }
}
