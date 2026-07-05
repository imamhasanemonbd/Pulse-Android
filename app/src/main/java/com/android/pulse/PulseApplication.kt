package com.android.pulse

import android.app.Application
import coil.ImageLoader
import android.util.Log
import coil.ImageLoaderFactory
import coil.util.DebugLogger
import com.android.pulse.audio.AudioPlayerManager
import com.android.pulse.data.local.PulseDatabase
import androidx.media3.common.util.UnstableApi

@UnstableApi
class PulseApplication : Application(), ImageLoaderFactory {
    val database by lazy { PulseDatabase.getDatabase(this) }
    val audioPlayerManager by lazy { AudioPlayerManager(this, database) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .logger(DebugLogger()) // Enable detailed Coil logging in logcat
            .build()
    }
}
