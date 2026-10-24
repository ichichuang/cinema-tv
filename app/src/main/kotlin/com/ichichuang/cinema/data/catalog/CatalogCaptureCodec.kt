package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.serialization.json.*

internal object CatalogCaptureCodec : JsonStorageCodec<NormalizedCatalogCapture>("catalog_capture") {
    override fun toJson(value: NormalizedCatalogCapture) = buildJsonObject {
        put("sourceId",value.sourceId.value);put("itemId",stringJson(value.itemId?.value));put("raw",value.raw)
        put("observedAt",value.observedAt);put("ruleVersion",value.ruleVersion)
        put("fieldNames",buildJsonObject { value.fieldNames.forEach { (key,field) -> put(key,field) } })
        put("year",evidenceJson(value.year) { JsonPrimitive(it) })
        put("facts",buildJsonObject { value.facts.forEach { (field,evidence) -> put(field,evidenceJson(evidence) { stringsJson(it.values) }) } })
    }
    override fun fromJson(value: JsonObject) = NormalizedCatalogCapture(SourceId(value.required("sourceId").text()),
        value.required("itemId").nullable { SourceItemId(it.text()) },value.required("raw").jsonObject,
        value.required("observedAt").longNumber(),value.required("ruleVersion").text(),value.required("year").evidence { it.integer() },
        value.required("facts").jsonObject.mapValues { (_,v) -> v.evidence { FactIds.of(it.stringSet()) } },
        value.required("fieldNames").jsonObject.mapValues { (_,field) -> field.text() })
}
internal fun declaredEntriesJson(entries: List<DeclaredCatalogEntry>): String = JsonArray(entries.sortedBy { it.entryId }.map { buildJsonObject {
    put("entryId",it.entryId);put("episodeId",it.episodeId.value);put("variantId",it.variantId.value);put("line",it.lineIndex);put("episode",it.episodeIndex)
} }).storageJson()
