package com.ichichuang.cinema.data.userdata

import androidx.room.withTransaction
import com.ichichuang.cinema.data.local.CinemaDatabase
import com.ichichuang.cinema.data.local.entity.FavoriteEntity
import com.ichichuang.cinema.data.local.entity.WorkReferenceEntity
import com.ichichuang.cinema.domain.model.catalog.WorkId
import com.ichichuang.cinema.domain.model.userdata.CommitResult
import com.ichichuang.cinema.domain.repository.FavoritesStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class RoomFavoritesStore(private val database: CinemaDatabase, private val beforeCommit:suspend ()->Unit={}) : FavoritesStore {
    override suspend fun setFavorite(workId: String, selected: Boolean): CommitResult {
        currentCoroutineContext().ensureActive()
        val id = try { WorkId(workId) } catch (_: IllegalArgumentException) { return CommitResult.Failed("invalid_work_id") }
        return storageCommit {
            database.withTransaction {
                val dao = database.userRecords()
                if (selected) {
                    dao.insertReference(WorkReferenceEntity(id.value))
                    if (dao.favorite(id.value) == null) {
                        val library = database.personalLibrary()
                        val state = library.state(id.value) ?: com.ichichuang.cinema.data.local.entity.PersonalLibraryStateEntity(id.value)
                        val revision=library.nextFavoriteRevision()
                        library.putState(state.copy(favoriteOrder=revision,favoriteRevision=revision,undoneRevision=0))
                        dao.insertFavorite(FavoriteEntity(id.value))
                    }
                } else if (dao.favorite(id.value) != null) {
                    val library = database.personalLibrary()
                    val state = library.state(id.value) ?: com.ichichuang.cinema.data.local.entity.PersonalLibraryStateEntity(id.value)
                    library.putState(state.copy(favoriteRevision=library.nextFavoriteRevision(),undoneRevision=0))
                    dao.removeFavorite(id.value)
                }
                beforeCommit()
                currentCoroutineContext().ensureActive()
            }
        }
    }
}
