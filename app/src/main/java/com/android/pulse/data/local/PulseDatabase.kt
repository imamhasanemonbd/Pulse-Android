package com.android.pulse.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.android.pulse.data.local.dao.HistoryDao
import com.android.pulse.data.local.dao.HomeCacheDao
import com.android.pulse.data.local.dao.LikedSongDao
import com.android.pulse.data.local.dao.OfflineSongDao
import com.android.pulse.data.local.dao.PlaylistDao
import com.android.pulse.data.local.entity.*

@Database(
    entities = [
        LikedSongEntity::class, 
        HistoryEntity::class, 
        PlaylistEntity::class, 
        PlaylistSongCrossRef::class,
        OfflineSongEntity::class,
        HomeCacheEntity::class
    ], 
    version = 9,
    exportSchema = false
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun likedSongDao(): LikedSongDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun offlineSongDao(): OfflineSongDao
    abstract fun homeCacheDao(): HomeCacheDao

    companion object {
        @Volatile
        private var INSTANCE: PulseDatabase? = null

        fun getDatabase(context: Context): PulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PulseDatabase::class.java,
                    "pulse_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
