package com.android.pulse.data.local.dao

import androidx.room.*
import com.android.pulse.data.local.entity.LikedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedSongDao {
    @Query("SELECT * FROM liked_songs ORDER BY addedAt DESC")
    fun getAllLikedSongs(): Flow<List<LikedSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedSong(song: LikedSongEntity)

    @Delete
    suspend fun deleteLikedSong(song: LikedSongEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE id = :songId)")
    fun isLiked(songId: String): Flow<Boolean>

    @Query("DELETE FROM liked_songs")
    suspend fun clearLikedSongs()
}
