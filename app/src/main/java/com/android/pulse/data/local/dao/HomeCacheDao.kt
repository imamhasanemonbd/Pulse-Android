package com.android.pulse.data.local.dao

import androidx.room.*
import com.android.pulse.data.local.entity.HomeCacheEntity

@Dao
interface HomeCacheDao {
    @Query("SELECT * FROM home_cache WHERE id = 'main_home'")
    suspend fun getHomeCache(): HomeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeCache(cache: HomeCacheEntity)

    @Query("DELETE FROM home_cache")
    suspend fun clearHomeCache()
}
