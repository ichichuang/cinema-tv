package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.serialization.json.*
import com.ichichuang.cinema.data.local.codec.*
import java.security.MessageDigest

/** Bounded candidate state only. Facts remain in the Round 08 evidence tables. */
internal object QuerySessionCodec {
    fun encode(session: StoredQuerySession): String = buildJsonObject {
        put("queryId",session.query.queryId)
        session.query.yearStart?.let { put("yearStart",it) };session.query.yearEnd?.let { put("yearEnd",it) }
        put("sources",JsonArray(session.query.enabledSourceIds.map { JsonPrimitive(it.value) }))
        put("projectionDigest",session.projectionDigest);put("unidentifiedRecords",session.unidentifiedRecords)
        put("revision",session.revision);put("capturedRecords",session.capturedRecords)
        put("cursors",JsonArray(session.cursors.map { state -> buildJsonObject {
            put("source",state.sourceId.value);put("next",state.nextCursor);put("exhausted",state.exhausted)
            state.failure?.let {put("failure",it)};state.stopReason?.let {put("stop",it)}
            put("visited",JsonArray(state.requestedCursors.map(::JsonPrimitive)))
            put("pages",JsonArray(state.pageFingerprints.map(::JsonPrimitive)))
        } }))
        put("keys",JsonArray(session.evidenceKeys.map { buildJsonObject { put("source",it.sourceId.value);put("item",it.itemId.value) } }))
    }.storageJson()
    fun decode(payload: String): StoredQuerySession {
        require(payload.toByteArray().size <= 1_048_576)
        val raw=Json.parseToJsonElement(payload).jsonObject
        fun JsonObject.string(key: String)=getValue(key).jsonPrimitive.content.also {require(it.isNotBlank() && it.length<=256)}
        val sources=raw.getValue("sources").jsonArray.map { SourceId(it.jsonPrimitive.content.also { v -> require(v.isNotBlank() && v.length<=256) }) }
        require(sources.size<=32 && sources.distinct().size==sources.size)
        val query=CatalogQuery.create(raw.string("queryId"),yearStart=raw["yearStart"]?.jsonPrimitive?.int,
            yearEnd=raw["yearEnd"]?.jsonPrimitive?.int,enabledSourceIds=sources.toSet())
        val cursors=raw.getValue("cursors").jsonArray.map { element ->
            val row=element.jsonObject
            val visited=row.getValue("visited").jsonArray.map {it.jsonPrimitive.content.also { v -> require(v.isNotBlank() && v.length<=256) }}
            val pages=row.getValue("pages").jsonArray.map {it.jsonPrimitive.content.also { v -> require(v.matches(Regex("[0-9a-f]{64}"))) }}
            require(visited.size<=128 && pages.size==visited.size)
            SourceCursorState(SourceId(row.string("source")),row.string("next"),row.getValue("exhausted").jsonPrimitive.boolean,
                row["failure"]?.jsonPrimitive?.content?.also {require(it.length<=256)},row["stop"]?.jsonPrimitive?.content?.also {require(it.length<=256)},visited,pages)
        }
        require(cursors.map {it.sourceId}.toSet()==sources.toSet() && cursors.size==sources.size)
        val keys=raw.getValue("keys").jsonArray.map {val row=it.jsonObject;SourceItemKey(SourceId(row.string("source")),SourceItemId(row.string("item")))}
        require(keys.size<=2000 && keys.distinct().size==keys.size && keys.all {it.sourceId in sources})
        val revision=raw.getValue("revision").jsonPrimitive.long;val count=raw.getValue("capturedRecords").jsonPrimitive.int
        require(revision>=0 && count in 0..2000 && keys.size<=count)
        val digest=raw.getValue("projectionDigest").jsonPrimitive.content
        require(digest.isEmpty() || digest.matches(Regex("[0-9a-f]{64}")))
        val unidentified=raw.getValue("unidentifiedRecords").jsonPrimitive.int
        require(unidentified in 0..count)
        return StoredQuerySession(query,cursors,keys,revision,count,digest,unidentified)
    }
}

/** Includes every retained entry, home/group identity and evidence fact; never hashes title into identity. */
internal fun queryProjectionDigest(entries: List<QueryCatalogEntry>): String {
    val json=JsonArray(entries.map { entry -> buildJsonObject {
        put("source",entry.key.sourceId.value);put("item",entry.key.itemId.value);put("home",entry.homeWorkId.value)
        put("group",entry.groupWorkId.value);put("title",entry.title)
        put("year",evidenceJson(entry.year) {JsonPrimitive(it)})
        put("languages",evidenceJson(entry.originalLanguages) {stringsJson(it.values)})
        put("countries",evidenceJson(entry.countries) {stringsJson(it.values)})
        put("entries",JsonArray(entry.playbackEntryIds.sorted().map(::JsonPrimitive)))
    } }).storageJson()
    return MessageDigest.getInstance("SHA-256").digest(json.toByteArray()).joinToString("") { "%02x".format(it) }
}
internal fun reconcileQueryProjection(session: StoredQuerySession, previous: StoredQuerySession?, entries: List<QueryCatalogEntry>): StoredQuerySession {
    val digest=queryProjectionDigest(entries)
    val changed=previous != null && session.revision==previous.revision && previous.projectionDigest!=digest
    return session.copy(projectionDigest=digest,revision=session.revision+if(changed) 1 else 0)
}
