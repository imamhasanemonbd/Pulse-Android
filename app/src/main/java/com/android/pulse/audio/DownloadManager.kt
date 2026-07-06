package com.android.pulse.audio

import android.content.Context
import android.util.Log
import com.android.pulse.data.local.PulseDatabase
import com.android.pulse.data.local.entity.OfflineSongEntity
import com.android.pulse.data.model.Track
import com.android.pulse.data.remote.innertube.InnerTubeRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Robust background download manager with progress tracking.
 */
object DownloadManager {
    private const val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val activeDownloads = mutableSetOf<String>()

    fun downloadTrack(context: Context, database: PulseDatabase, track: Track) {
        if (activeDownloads.contains(track.id)) return
        activeDownloads.add(track.id)

        scope.launch {
            try {
                Log.d(TAG, "Starting download for: ${track.title}")
                updateProgress(track.id, 0.01f)

                val streamUrl = InnerTubeRepository.getStreamUrl(track.id)
                if (streamUrl == null) {
                    Log.e(TAG, "Could not fetch stream URL for ${track.id}")
                    removeDownload(track.id)
                    return@launch
                }

                val request = Request.Builder().url(streamUrl).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful || response.body == null) {
                    Log.e(TAG, "Download request failed for ${track.id}")
                    removeDownload(track.id)
                    return@launch
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                val file = File(context.getExternalFilesDir(null), "downloads/${track.id}.mp3")
                file.parentFile?.mkdirs()

                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        var totalRead: Long = 0
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                updateProgress(track.id, totalRead.toFloat() / contentLength)
                            }
                        }
                    }
                }

                // Save to database
                database.offlineSongDao().insertOfflineSong(
                    OfflineSongEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        thumbnailUrl = track.thumbnail,
                        duration = track.duration,
                        localPath = file.absolutePath
                    )
                )

                Log.d(TAG, "Download complete: ${track.title}")
                updateProgress(track.id, 1.0f)
                delay(2000) // Keep 100% for a bit
                removeDownload(track.id)

            } catch (e: Exception) {
                Log.e(TAG, "Download failed for ${track.id}", e)
                removeDownload(track.id)
            }
        }
    }

    private fun updateProgress(id: String, progress: Float) {
        val current = _downloadProgress.value.toMutableMap()
        current[id] = progress
        _downloadProgress.value = current
    }

    private fun removeDownload(id: String) {
        activeDownloads.remove(id)
        val current = _downloadProgress.value.toMutableMap()
        current.remove(id)
        _downloadProgress.value = current
    }
}
