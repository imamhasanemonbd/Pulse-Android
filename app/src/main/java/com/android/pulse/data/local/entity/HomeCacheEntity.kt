package com.android.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_cache")
data class HomeCacheEntity(
    @PrimaryKey val id: String = "main_home",
    val quickPicksJson: String,
    val speedDialJson: String,
    val categoriesJson: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
