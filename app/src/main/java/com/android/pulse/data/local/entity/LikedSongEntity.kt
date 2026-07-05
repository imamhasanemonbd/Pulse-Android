package com.android.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.android.pulse.data.model.Track

@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val duration: Int?,
    val addedAt: Long = System.currentTimeMillis()
)

fun LikedSongEntity.toTrack() = Track(
    id = id,
    title = title,
    artist = artist,
    thumbnail = thumbnailUrl,
    duration = duration
)

fun Track.toLikedEntity() = LikedSongEntity(
    id = id,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnail,
    duration = duration
)
