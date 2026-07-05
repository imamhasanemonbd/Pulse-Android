package com.android.pulse.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Current PC IP: 192.168.0.190
    // If you run on emulator, change to 10.0.2.2
    private const val BASE_URL = "http://192.168.0.190:3000/"

    val pulseApiService: PulseApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PulseApiService::class.java)
    }
    
    fun getStreamUrl(trackId: String): String {
        return "${BASE_URL}api/stream/$trackId"
    }
}
