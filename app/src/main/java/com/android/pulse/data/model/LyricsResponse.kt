package com.android.pulse.data.model

import com.google.gson.annotations.SerializedName

data class LyricsResponse(
    @SerializedName("lyrics") val lyrics: String,
    @SerializedName("source") val source: String?
)
