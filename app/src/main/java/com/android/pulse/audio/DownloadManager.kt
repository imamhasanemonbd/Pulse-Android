package com.android.pulse.audio

import android.content.Context
import com.android.pulse.data.local.PulseDatabase
import com.android.pulse.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DownloadManager(private val context: Context, private val database: PulseDatabase) {
    // Feature removed as requested.
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    fun downloadTrack(track: Track) {
        // No-op
    }
}
