package com.ichichuang.cinema.data.local.dao

import androidx.room.*
import com.ichichuang.cinema.data.local.codec.Utf16KeyConverter
import com.ichichuang.cinema.data.local.entity.*

@Dao
internal interface PersonalLibraryDao {
    @Upsert suspend fun putState(value:PersonalLibraryStateEntity)
    @Query("SELECT * FROM personal_library_state WHERE workId=:workId") suspend fun state(workId:ByteArray):PersonalLibraryStateEntity?
    suspend fun state(workId:String)=state(Utf16KeyConverter().encode(workId))
    @Query("SELECT COALESCE(MAX(favoriteRevision),0)+1 FROM personal_library_state") suspend fun nextFavoriteRevision():Long
    @Upsert suspend fun putCard(value:PersonalLibraryCardEntity)
    @Query("SELECT * FROM personal_library_cards WHERE workId=:workId") suspend fun card(workId:ByteArray):PersonalLibraryCardEntity?
    suspend fun card(workId:String)=card(Utf16KeyConverter().encode(workId))
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun confirm(value:ConfirmedPlayEntity):Long
    @Query("SELECT * FROM confirmed_plays WHERE sessionGeneration=:generation") suspend fun confirmation(generation:Long):ConfirmedPlayEntity?
    @Query("SELECT * FROM confirmed_plays ORDER BY eventOrder DESC") suspend fun history():List<ConfirmedPlayEntity>
    @Query("DELETE FROM confirmed_plays WHERE workId=:workId AND sessionGeneration<=:floor") suspend fun deleteHistory(workId:ByteArray,floor:Long)
    @Query("SELECT * FROM playback_snapshots WHERE workId=:workId") suspend fun snapshots(workId:ByteArray):List<PlaybackSnapshotEntity>
    @Query("DELETE FROM playback_snapshots WHERE workId=:workId AND episodeKey=:episode AND variantKey=:variant")
    suspend fun deleteSnapshot(workId:ByteArray,episode:ByteArray,variant:ByteArray)
    @Query("DELETE FROM playback_resume WHERE workId=:workId AND generation<=:floor") suspend fun deleteResume(workId:ByteArray,floor:Long)
    @Query("SELECT favorites.* FROM favorites LEFT JOIN personal_library_state s ON favorites.workId=s.workId ORDER BY COALESCE(s.favoriteOrder,0) DESC,favorites.workId ASC")
    suspend fun favorites():List<FavoriteEntity>
    @Query("SELECT * FROM playback_resume ORDER BY generation DESC") suspend fun resumes():List<PlaybackResumeEntity>
    @Query("SELECT * FROM playback_sessions WHERE generation=:generation") suspend fun sessionGeneration(generation:Long):PlaybackSessionEntity?
}
