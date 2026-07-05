package com.android.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.android.pulse.data.model.Track

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val coverUrl: String? = null
)

@Entity(tableName = "playlist_song_cross_ref", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: String
)
