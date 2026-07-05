package com.android.pulse.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.android.pulse.data.local.dao.HistoryDao
import com.android.pulse.data.local.dao.LikedSongDao
import com.android.pulse.data.local.dao.PlaylistDao
import com.android.pulse.data.local.entity.HistoryEntity
import com.android.pulse.data.local.entity.LikedSongEntity
import com.android.pulse.data.local.entity.PlaylistEntity
import com.android.pulse.data.local.entity.PlaylistSongCrossRef

@Database(
    entities = [
        LikedSongEntity::class, 
        HistoryEntity::class, 
        PlaylistEntity::class, 
        PlaylistSongCrossRef::class
    ], 
    version = 5,
    exportSchema = false
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun likedSongDao(): LikedSongDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao

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
