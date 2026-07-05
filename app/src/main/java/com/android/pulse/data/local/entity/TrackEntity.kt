package com.android.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.android.pulse.data.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val duration: Int?
)

fun TrackEntity.toDomain() = Track(
    id = id,
    title = title,
    artist = artist,
    thumbnail = thumbnailUrl,
    duration = duration
)

fun Track.toEntity() = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnail,
    duration = duration
)
