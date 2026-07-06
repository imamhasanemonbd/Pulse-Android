package com.android.pulse.data.local.dao

import androidx.room.*
import com.android.pulse.data.local.entity.OfflineSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineSongDao {
    @Query("SELECT * FROM offline_songs ORDER BY downloadedAt DESC")
    fun getAllOfflineSongs(): Flow<List<OfflineSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineSong(song: OfflineSongEntity)

    @Query("DELETE FROM offline_songs WHERE id = :songId")
    suspend fun deleteOfflineSong(songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM offline_songs WHERE id = :songId AND isFinished = 1)")
    fun isDownloaded(songId: String): Flow<Boolean>
}
