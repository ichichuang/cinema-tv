package com.ichichuang.cinema.data.userdata

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.data.local.entity.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

internal data class PersonalLibraryCard(val workId:String,val title:String,val sourceLabel:String?,
    val snapshot:PlaybackSnapshot?,val completedEpisode:Boolean=false,
    val episodeId:EpisodeId?=snapshot?.episodeId,val variantId:VariantId?=snapshot?.variantId,val sessionId:String?=snapshot?.sessionId)

/** One projection over the existing favorite/checkpoint writers; never contacts a source. */
internal data class PersonalLibraryAction(val workId:String,val throughGeneration:Long) {
    init { WorkId(workId);require(throughGeneration>=0) }
}
internal data class PersonalLibraryUnfavoriteAction(val workId:String,val favoriteOrder:Long,val wasSelected:Boolean)
internal data class PersonalLibraryUnfavoriteResult(val result:CommitResult,val undoRevision:Long?)

internal class RoomPersonalLibraryRepository(private val database:CinemaDatabase,
    private val beforeCommit:suspend ()->Unit={}, private val afterCommit:suspend ()->Unit={}) {
    private val dao get()=database.personalLibrary()
    private val changedTables=arrayOf("favorites","personal_library_state","personal_library_cards",
        "confirmed_plays","playback_snapshots","playback_resume")
    fun observeFavorites():Flow<List<PersonalLibraryCard>> = database.invalidationTracker.createFlow(*changedTables).map { favorites() }
    fun observeRecentHistory():Flow<List<PersonalLibraryCard>> = database.invalidationTracker.createFlow(*changedTables).map { recentHistory() }
    fun observeContinueWatching():Flow<List<PersonalLibraryCard>> = database.invalidationTracker.createFlow(*changedTables).map { continueWatching() }

    suspend fun favorites():List<PersonalLibraryCard> = read {
        dao.favorites().map { card(it.workId, null) }
    }
    suspend fun recentHistory():List<PersonalLibraryCard> = read {
        dao.history().distinctBy { it.workId }.map { evidence ->
            val session=dao.sessionGeneration(evidence.sessionGeneration)
                ?: throw StorageReadException(StorageReadFailure.MALFORMED)
            val snapshot=database.playbackSnapshots().find(session.workId,session.episodeKey,session.variantKey)
                ?.snapshot()?.takeIf { it.sessionId==session.sessionId }
            card(evidence.workId,snapshot,session)
        }
    }
    suspend fun continueWatching():List<PersonalLibraryCard> = read {
        dao.resumes().mapNotNull { resume ->
            val state=dao.state(resume.workId)
            val snapshot=PlaybackSnapshotCodec.decode(resume.payload)
            if (snapshot.workId.value != resume.workId) throw StorageReadException(StorageReadFailure.MALFORMED)
            if (resume.generation <= (state?.hiddenThroughGeneration ?: -1) ||
                resume.generation <= (state?.deletedThroughGeneration ?: -1) ||
                ((state?.hiddenThroughGeneration ?: -1)>=0 && dao.confirmation(resume.generation)==null) ||
                snapshot.watchState!=WatchState.IN_PROGRESS) null
            else card(resume.workId,snapshot)
        }
    }
    private suspend fun card(workId:String,snapshot:PlaybackSnapshot?,session:PlaybackSessionEntity?=null):PersonalLibraryCard {
        val saved=dao.card(workId)
        // Missing metadata stays identifiable by the original opaque reference, never fabricated media facts.
        return PersonalLibraryCard(workId,saved?.title ?: workId,saved?.sourceLabel,snapshot,
            snapshot?.watchState==WatchState.COMPLETED,
            session?.episodeKey?.takeIf { it.isNotEmpty() }?.let(::EpisodeId) ?: snapshot?.episodeId,
            session?.variantKey?.takeIf { it.isNotEmpty() }?.let(::VariantId) ?: snapshot?.variantId,
            session?.sessionId ?: snapshot?.sessionId)
    }
    suspend fun rememberCard(workId:String,title:String,sourceLabel:String?=null):CommitResult = mutate(workId) {
        require(title.isNotBlank()) { "缺少作品标识" }
        database.userRecords().insertReference(WorkReferenceEntity(workId))
        dao.putCard(PersonalLibraryCardEntity(workId,title,sourceLabel));CommitResult.Committed
    }
    suspend fun confirmPlayback(sessionId:String):CommitResult = mutation {
        val session=database.playbackSessions().session(sessionId) ?: return@mutation CommitResult.RejectedStale
        if (session.generation <= (dao.state(session.workId)?.deletedThroughGeneration ?: -1))
            return@mutation CommitResult.RejectedStale
        if (dao.confirmation(session.generation)!=null) return@mutation CommitResult.Committed
        if (database.playbackSessions().latestGeneration(session.workId)!=session.generation)
            return@mutation CommitResult.RejectedStale
        dao.confirm(ConfirmedPlayEntity(sessionGeneration=session.generation,workId=session.workId))
        CommitResult.Committed
    }
    /** String convenience starts a new user request; retries retain the prepared action. */
    suspend fun removeFromContinue(workId:String):CommitResult = removeFromContinue(prepareAction(workId))
    suspend fun deleteWatchHistory(workId:String):CommitResult = deleteWatchHistory(prepareAction(workId))

    suspend fun prepareAction(workId:String):PersonalLibraryAction {
        WorkId(workId)
        return PersonalLibraryAction(workId,database.playbackSessions().latestGeneration(workId) ?: 0)
    }
    suspend fun removeFromContinue(action:PersonalLibraryAction):CommitResult = mutate(action.workId) {
        database.userRecords().insertReference(WorkReferenceEntity(action.workId))
        val state=dao.state(action.workId) ?: PersonalLibraryStateEntity(action.workId)
        if (state.hiddenThroughGeneration>=action.throughGeneration) return@mutate CommitResult.Committed
        dao.putState(state.copy(hiddenThroughGeneration=action.throughGeneration))
        CommitResult.Committed
    }
    /** Work-wide through the request's captured generation. New playback is outside this confirmation. */
    suspend fun deleteWatchHistory(action:PersonalLibraryAction):CommitResult = mutate(action.workId) {
        database.userRecords().insertReference(WorkReferenceEntity(action.workId))
        val state=dao.state(action.workId) ?: PersonalLibraryStateEntity(action.workId)
        if (state.deletedThroughGeneration>=action.throughGeneration) return@mutate CommitResult.Committed
        dao.putState(state.copy(deletedThroughGeneration=action.throughGeneration,
            hiddenThroughGeneration=maxOf(state.hiddenThroughGeneration,action.throughGeneration)))
        val converter=Utf16KeyConverter();val key=converter.encode(action.workId)
        dao.deleteHistory(key,action.throughGeneration);dao.deleteResume(key,action.throughGeneration)
        for(row in dao.snapshots(key)) {
            val session=database.playbackSessions().session(row.snapshot().sessionId)
            if ((session?.generation ?: 0)<=action.throughGeneration)
                dao.deleteSnapshot(key,converter.encode(row.episodeKey),converter.encode(row.variantKey))
        }
        CommitResult.Committed
    }
    suspend fun setFavorite(workId:String,selected:Boolean):CommitResult = acknowledged {
        RoomFavoritesStore(database,beforeCommit).setFavorite(workId,selected)
    }
    suspend fun prepareUnfavorite(workId:String):PersonalLibraryUnfavoriteAction = read {
        WorkId(workId)
        PersonalLibraryUnfavoriteAction(workId,dao.state(workId)?.favoriteOrder ?: 0,database.userRecords().favorite(workId)!=null)
    }
    suspend fun unfavorite(workId:String):PersonalLibraryUnfavoriteResult = unfavorite(prepareUnfavorite(workId))
    suspend fun unfavorite(action:PersonalLibraryUnfavoriteAction):PersonalLibraryUnfavoriteResult {
        var revision:Long?=null
        val result=mutate(action.workId) {
            val state=dao.state(action.workId) ?: PersonalLibraryStateEntity(action.workId)
            val favorite=database.userRecords().favorite(action.workId)
            // favoriteOrder identifies the original selected relation and stays after its removal.
            // Re-selecting changes it, so an old failed request cannot claim a newer cancellation.
            if(state.favoriteOrder!=action.favoriteOrder) return@mutate CommitResult.RejectedStale
            if(!action.wasSelected) return@mutate if(favorite==null) CommitResult.Committed else CommitResult.RejectedStale
            if(favorite==null) {
                if(state.favoriteRevision<=action.favoriteOrder) return@mutate CommitResult.RejectedStale
                revision=state.favoriteRevision
                return@mutate CommitResult.Committed
            }
            val committed=RoomFavoritesStore(database).setFavorite(action.workId,false)
            if(committed==CommitResult.Committed) revision=dao.state(action.workId)?.favoriteRevision
            committed
        }
        return PersonalLibraryUnfavoriteResult(result,revision.takeIf { result==CommitResult.Committed })
    }
    suspend fun undoUnfavorite(workId:String,revision:Long):CommitResult = mutate(workId) {
        val state=dao.state(workId) ?: return@mutate CommitResult.RejectedStale
        val favorite=database.userRecords().favorite(workId)
        if(state.undoneRevision==revision && revision>0 && favorite!=null) return@mutate CommitResult.Committed
        if(revision<=0 || state.favoriteRevision!=revision || favorite!=null) return@mutate CommitResult.RejectedStale
        val result=RoomFavoritesStore(database).setFavorite(workId,true)
        if(result==CommitResult.Committed) dao.putState(dao.state(workId)!!.copy(undoneRevision=revision))
        result
    }

    private suspend fun mutate(workId:String,body:suspend ()->CommitResult):CommitResult {
        currentCoroutineContext().ensureActive()
        try { WorkId(workId) } catch (_:IllegalArgumentException) { return CommitResult.Failed("invalid_work_id") }
        return mutation(body)
    }
    private suspend fun mutation(body:suspend ()->CommitResult):CommitResult {
        currentCoroutineContext().ensureActive()
        return acknowledged {
            database.withTransaction {
                body().also { if (it==CommitResult.Committed) { beforeCommit();currentCoroutineContext().ensureActive() } }
            }
        }
    }
    private suspend fun acknowledged(body:suspend ()->CommitResult):CommitResult = try {
        body().also { if(it==CommitResult.Committed) afterCommit() }
    } catch (_:SQLiteException) { CommitResult.Failed("storage_sql") }
    catch (_:IOException) { CommitResult.Failed("storage_io") }
    catch (_:StorageReadException) { CommitResult.Failed("storage_read") }
    private suspend fun <T> read(body:suspend ()->T):T {
        currentCoroutineContext().ensureActive()
        return try { database.withTransaction { body() } }
        catch (_:SQLiteException) { throw StorageReadException(StorageReadFailure.IO) }
        catch (_:IOException) { throw StorageReadException(StorageReadFailure.IO) }
    }
}
