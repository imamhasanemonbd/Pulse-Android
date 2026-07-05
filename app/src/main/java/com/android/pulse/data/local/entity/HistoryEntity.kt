package com.android.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.android.pulse.data.model.Track

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0,
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val duration: Int?,
    val playedAt: Long = System.currentTimeMillis()
)

fun HistoryEntity.toTrack() = Track(
    id = id,
    title = title,
    artist = artist,
    thumbnail = thumbnailUrl,
    duration = duration
)

fun Track.toHistoryEntity() = HistoryEntity(
    id = id,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnail,
    duration = duration
)
