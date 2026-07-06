package com.android.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.android.pulse.data.model.Track

@Entity(tableName = "offline_songs")
data class OfflineSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val duration: Int?,
    val localPath: String,
    val downloadedAt: Long = System.currentTimeMillis()
)

fun OfflineSongEntity.toTrack() = Track(
    id = id,
    title = title,
    artist = artist,
    thumbnail = thumbnailUrl,
    duration = duration
)
