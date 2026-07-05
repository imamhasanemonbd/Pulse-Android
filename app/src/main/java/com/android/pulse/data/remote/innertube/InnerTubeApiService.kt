package com.android.pulse.data.remote.innertube

import com.android.pulse.data.remote.innertube.model.*
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface InnerTubeApiService {
    @POST("youtubei/v1/search")
    suspend fun search(
        @Body request: SearchRequest,
        @Header("User-Agent") userAgent: String = InnerTubeClient.USER_AGENT_MWEB
    ): InnerTubeResponse

    @POST("youtubei/v1/player")
    suspend fun player(
        @Body request: PlayerRequest,
        @Header("User-Agent") userAgent: String
    ): InnerTubeResponse

    @POST("youtubei/v1/player")
    suspend fun playerBypass(
        @Body request: BypassPlayerRequest,
        @Header("User-Agent") userAgent: String
    ): InnerTubeResponse

    @POST("youtubei/v1/browse")
    suspend fun browse(
        @Body request: BrowseRequest,
        @Header("User-Agent") userAgent: String = InnerTubeClient.USER_AGENT_MWEB
    ): InnerTubeResponse
}
