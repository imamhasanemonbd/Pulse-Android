package com.android.pulse.data.remote

import com.android.pulse.data.model.LyricsResponse
import com.android.pulse.data.model.Track
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PulseApiService {
    @GET("api/search")
    suspend fun searchMusic(@Query("q") query: String): List<Track>

    @GET("api/lyrics/{id}")
    suspend fun getLyrics(@Path("id") id: String): LyricsResponse

    // Stream URL can be constructed as API_BASE + "api/stream/" + id
}
