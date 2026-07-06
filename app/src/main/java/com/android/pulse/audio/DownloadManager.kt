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
 * Robust background download manager with progress tracking and validation.
 */
object DownloadManager {
    private const val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val activeDownloads = mutableSetOf<String>()

    fun downloadTrack(context: Context, database: PulseDatabase, track: Track) {
        if (activeDownloads.contains(track.id)) {
            Log.d(TAG, "Download already in progress for: ${track.id}")
            return
        }
        activeDownloads.add(track.id)

        scope.launch {
            try {
                Log.d(TAG, "Starting download for: ${track.title} (${track.id})")
                
                // 1. Initial Placeholder
                database.offlineSongDao().insertOfflineSong(
                    OfflineSongEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        thumbnailUrl = track.thumbnail,
                        duration = track.duration,
                        localPath = null,
                        isFinished = false
                    )
                )
                
                updateProgress(track.id, 0.001f)

                // 2. Fetch Stream URL
                val streamUrl = InnerTubeRepository.getStreamUrl(track.id)
                if (streamUrl == null) {
                    throw Exception("Could not resolve stream URL for ${track.id}")
                }
                Log.d(TAG, "Resolved stream URL for ${track.id}")

                // 3. Execute Download
                val request = Request.Builder().url(streamUrl).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("Network request failed: ${response.code} ${response.message}")
                }
                
                val body = response.body ?: throw Exception("Response body is null")
                val contentLength = body.contentLength()
                Log.d(TAG, "Download body size: $contentLength bytes")

                val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                val file = File(downloadsDir, "${track.id}.mp3")
                if (file.exists()) file.delete() // Start fresh

                var totalRead: Long = 0
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                val progress = totalRead.toFloat() / contentLength
                                updateProgress(track.id, progress.coerceIn(0.01f, 0.99f))
                            }
                        }
                        output.flush()
                    }
                }

                // 4. Final Validation
                if (totalRead == 0L || !file.exists()) {
                    throw Exception("File was not written correctly (size 0)")
                }

                Log.d(TAG, "Download finished. Saved ${totalRead} bytes to ${file.absolutePath}")

                // 5. Commit to Database
                database.offlineSongDao().insertOfflineSong(
                    OfflineSongEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        thumbnailUrl = track.thumbnail,
                        duration = track.duration,
                        localPath = file.absolutePath,
                        isFinished = true
                    )
                )

                updateProgress(track.id, 1.0f)
                delay(1500)
                removeDownload(track.id)

            } catch (e: Exception) {
                Log.e(TAG, "FATAL DOWNLOAD ERROR for ${track.id}: ${e.message}", e)
                // Cleanup partial file if it exists
                try {
                    val file = File(context.getExternalFilesDir(null), "downloads/${track.id}.mp3")
                    if (file.exists()) file.delete()
                    database.offlineSongDao().deleteOfflineSong(track.id)
                } catch (ce: Exception) { /* Ignore cleanup errors */ }

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
