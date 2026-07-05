package com.android.pulse.data.remote.innertube

import android.util.Log
import com.android.pulse.data.remote.innertube.model.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder

object InnerTubeClient {
    private const val BASE_URL = "https://www.youtube.com/"
    private const val ANDROID_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"

    const val USER_AGENT_MWEB = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    const val USER_AGENT_VR = "com.google.android.apps.youtube.vr.oculus/1.50.21 (Linux; U; Android 12; en_US; Quest 2) Build/SQ3A.220605.009.A1"
    const val USER_AGENT_TV = "Mozilla/5.0 (Chromecast; Google TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    @Volatile
    private var visitorData: String? = null

    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val userAgent = original.header("User-Agent") ?: USER_AGENT_MWEB

        val url = original.url.newBuilder()
            .addQueryParameter("key", ANDROID_KEY)
            .build()

        val requestBuilder = original.newBuilder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent)
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com")

        chain.proceed(requestBuilder.method(original.method, original.body).build())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    val apiService: InnerTubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InnerTubeApiService::class.java)
    }

    fun setVisitorData(data: String) {
        try {
            visitorData = URLDecoder.decode(data, "UTF-8")
            Log.d("InnerTubeClient", "Decoded VisitorData: $visitorData")
        } catch (e: Exception) {
            visitorData = data
        }
    }

    fun getVisitorData(): String? = visitorData

    fun createVrContext() = InnerTubeContext(
        client = InnerTubeClientInfo(
            clientName = "ANDROID_VR",
            clientVersion = "1.50.21",
            platform = "MOBILE",
            visitorData = visitorData
        )
    )

    fun createTvContext() = InnerTubeContext(
        client = InnerTubeClientInfo(
            clientName = "TVHTML5",
            clientVersion = "7.20240301.01.00",
            platform = "TV",
            visitorData = visitorData
        )
    )

    fun createMWebContext() = InnerTubeContext(
        client = InnerTubeClientInfo(
            clientName = "MWEB",
            clientVersion = "2.20240301.01.00",
            platform = "MOBILE",
            visitorData = visitorData
        )
    )

    fun createWebContext() = InnerTubeContext(
        client = InnerTubeClientInfo(
            clientName = "WEB_REMIX",
            clientVersion = "1.20240301.01.00",
            platform = "DESKTOP",
            visitorData = visitorData
        )
    )
}
