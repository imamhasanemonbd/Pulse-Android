package com.android.pulse.data.local.dao

import androidx.room.*
import com.android.pulse.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :songId")
    suspend fun deleteHistoryBySongId(songId: String)

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}
