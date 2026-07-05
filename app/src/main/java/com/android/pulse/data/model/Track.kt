package com.android.pulse.data.model

import com.google.gson.annotations.SerializedName

data class Track(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("thumbnail") val thumbnail: String?,
    @SerializedName("duration") val duration: Int?
)
