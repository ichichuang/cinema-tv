package com.ichichuang.cinema.data.catalog

import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.data.local.entity.*
import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.util.UUID

/** Explicit offline candidate. It never chooses a runtime writer or updates personal records. */
internal class CandidateCatalogStore(private val database: CinemaDatabase, private val proofs: CatalogIdentityProofs = CatalogIdentityProofs(),
    private val allocateId: () -> String = { UUID.randomUUID().toString() }) {
    private val dao = database.catalogIdentity()
    private val converter = Utf16KeyConverter()
    private fun bytes(value: String) = converter.encode(value)
    private suspend fun item(key: SourceItemKey) = dao.item(bytes(key.sourceId.value),bytes(key.itemId.value))
    suspend fun registerSource(sourceId: SourceId, endpoint: String) = database.withTransaction {
        require(endpoint.isNotBlank())
        val existing=dao.endpoint(bytes(endpoint))
        if (dao.source(bytes(sourceId.value)) != null) {
            require(existing?.sourceId == sourceId.value) { "Existing source needs a verified endpoint alias" }
        } else {
            require(existing == null) { "Endpoint already belongs to another source" }
            dao.source(CatalogSourceEntity(sourceId.value,true))
            dao.endpoint(CatalogEndpointEntity(endpoint,sourceId.value,null))
        }
    }
    suspend fun ingest(captureId: String, capture: NormalizedCatalogCapture, entries: List<DeclaredCatalogEntry> = emptyList()): WorkId? {
        require(captureId.isNotBlank())
        val declared=entries.toList()
        require(capture == MaccmsCatalogNormalizer.normalize(capture.sourceId,capture.raw,capture.observedAt)) { "Unrecognized normalization evidence" }
        val projection=CatalogProjection.project(capture,declared)
        require(projection != null || declared.isEmpty())
        val payload=CatalogCaptureCodec.encode(capture);val entriesPayload=declaredEntriesJson(declared)
        return database.withTransaction {
            require(dao.source(bytes(capture.sourceId.value)) != null) { "Unknown source" }
            val existing=dao.capture(bytes(captureId))
            if(existing != null) {
                require(existing.payload == payload && existing.entriesPayload == entriesPayload) { "Capture token content changed" }
                return@withTransaction capture.itemId?.let { workFor(SourceItemKey(capture.sourceId,it)) }
            }
            val itemId=capture.itemId
            val stored=if(itemId == null) null else {
                item(SourceItemKey(capture.sourceId,itemId)) ?: run {
                    val work=WorkId(allocateId())
                    // A collision is a failed transaction, never a reused personal work identity.
                    require(database.userRecords().reference(bytes(work.value)) == null) { "Allocated identity collision" }
                    database.userRecords().insertReference(WorkReferenceEntity(work.value))
                    CatalogItemEntity(capture.sourceId.value,itemId.value,work.value,work.value).also { dao.item(it) }
                }
            }
            dao.capture(CatalogCaptureEntity(captureId,capture.sourceId.value,itemId?.value,payload,entriesPayload))
            if(stored != null && projection != null) for(entry in declared) {
                val value=CatalogEntryEntity(entry.entryId,stored.sourceId,stored.itemId,entry.episodeId.value,entry.variantId.value)
                val previous=dao.entry(bytes(entry.entryId))
                require(previous == null || previous == value) { "Playback identity reassignment refused" }
                if(previous == null) dao.entry(value)
                val source=projection.remote.playSources[entry.lineIndex]
                val episode=source.episodes[entry.episodeIndex]
                val route=buildJsonObject { put("sourceLabel",source.name);put("episodeLabel",episode.name);put("url",episode.url) }.storageJson()
                dao.route(CatalogRouteEntity(captureId,entry.entryId,route))
            }
            currentCoroutineContext().ensureActive()
            stored?.homeWorkId?.let(::WorkId)
        }
    }
    suspend fun workFor(key: SourceItemKey): WorkId? = item(key)?.homeWorkId?.let(::WorkId)
    suspend fun groupFor(key: SourceItemKey): WorkId? = item(key)?.groupWorkId?.let(::WorkId)
    suspend fun captures(key: SourceItemKey): List<NormalizedCatalogCapture> = dao.captures(bytes(key.sourceId.value),bytes(key.itemId.value)).map { CatalogCaptureCodec.decode(it.payload) }
    suspend fun pending(sourceId: SourceId): List<NormalizedCatalogCapture> = dao.pending(bytes(sourceId.value)).map { CatalogCaptureCodec.decode(it.payload) }
    suspend fun entries(key: SourceItemKey): List<String> = dao.entries(bytes(key.sourceId.value),bytes(key.itemId.value)).map { it.entryId }
    private suspend fun identifiers(group: List<CatalogItemEntity>) = group.flatMap { dao.identifiers(bytes(it.sourceId),bytes(it.itemId)) }
    private suspend fun titleYearMatch(left: SourceItemKey,right: SourceItemKey): Boolean {
        val a=captures(left);val b=captures(right)
        val ay=CatalogClaims.combine(a.map { it.year });val by=CatalogClaims.combine(b.map { it.year })
        val an=a.mapNotNull { it.raw["vod_name"].scalar()?.takeIf(String::isNotBlank) }.distinct()
        val bn=b.mapNotNull { it.raw["vod_name"].scalar()?.takeIf(String::isNotBlank) }.distinct()
        return an.size == 1 && an == bn && ay is FieldEvidence.SourceReported && by is FieldEvidence.SourceReported && ay.value == by.value
    }
    private suspend fun event(row: CatalogItemEntity,to: String,action: String,proof: String,at: Long,detail: String = "") {
        dao.history(CatalogAssociationHistoryEntity(sourceId=row.sourceId,itemId=row.itemId,fromWorkId=row.groupWorkId,toWorkId=to,
            action=action,proofId=proof,occurredAt=at,detail=detail))
    }
    suspend fun associate(left: SourceItemKey, right: SourceItemKey, proofId: String, at: Long): AssociationDecision = database.withTransaction {
        require(left != right && proofId.isNotBlank())
        val a=requireNotNull(item(left));val b=requireNotNull(item(right))
        val leftGroup=dao.group(bytes(a.groupWorkId));val rightGroup=dao.group(bytes(b.groupWorkId))
        val verified=proofs.association(proofId,left,right)
        val decision=if(dao.proof(bytes(proofId)).any { it.revoked }) AssociationDecision.CONFLICT else WorkIdentity.decide(verified,
            identifiers(leftGroup).map { CatalogIdentifier(it.namespace,it.identifier) }.toSet(),
            identifiers(rightGroup).map { CatalogIdentifier(it.namespace,it.identifier) }.toSet(),titleYearMatch(left,right))
        if(decision != AssociationDecision.LINKED) {
            event(a,a.groupWorkId,decision.name.lowercase(),proofId,at,buildJsonObject {
                put("rightSource",right.sourceId.value);put("rightItem",right.itemId.value)
                put("namespace",stringJson(verified?.namespace));put("identifier",stringJson(verified?.value))
            }.storageJson())
            return@withTransaction decision
        }
        check(verified != null)
        for(key in listOf(left,right)) dao.identifier(CatalogVerifiedIdentifierEntity(key.sourceId.value,key.itemId.value,verified.namespace,verified.value,proofId,false))
        if(a.groupWorkId != b.groupWorkId) for(member in rightGroup) {
            event(member,a.groupWorkId,"linked",proofId,at,buildJsonObject { put("namespace",verified.namespace);put("identifier",verified.value) }.storageJson())
            dao.regroup(bytes(member.sourceId),bytes(member.itemId),bytes(a.groupWorkId))
        }
        currentCoroutineContext().ensureActive()
        decision
    }
    /** Dissolve the candidate group conservatively; all personal keys and historical proof rows stay. */
    suspend fun split(key: SourceItemKey, reason: String, at: Long) = database.withTransaction {
        require(reason.isNotBlank())
        val row=requireNotNull(item(key));val group=dao.group(bytes(row.groupWorkId))
        if(group.size > 1) {
            identifiers(group).map { it.proofId }.distinct().forEach { dao.revoke(bytes(it)) }
            for(member in group) {
                event(member,member.homeWorkId,"split",reason,at)
                dao.regroup(bytes(member.sourceId),bytes(member.itemId),bytes(member.homeWorkId))
            }
        }
    }
    suspend fun alias(sourceId: SourceId, previous: String, next: String, proofId: String): Boolean = database.withTransaction {
        if(!proofs.alias(proofId,sourceId,previous,next) || dao.endpoint(bytes(previous))?.sourceId != sourceId.value) return@withTransaction false
        val existing=dao.endpoint(bytes(next))
        if(existing != null) return@withTransaction existing.sourceId == sourceId.value
        dao.endpoint(CatalogEndpointEntity(next,sourceId.value,proofId));true
    }
    suspend fun unavailable(sourceId: SourceId) = database.withTransaction { dao.unavailable(bytes(sourceId.value)) }
}
