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
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust background download manager with job cancellation and progress tracking.
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

    // Map to track active download jobs for cancellation
    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun downloadTrack(context: Context, database: PulseDatabase, track: Track) {
        if (activeJobs.containsKey(track.id)) {
            Log.d(TAG, "Download already in progress for: ${track.id}")
            return
        }

        val job = scope.launch {
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
                if (streamUrl == null) throw Exception("Stream URL resolved to null")

                // 3. Execute Download
                val request = Request.Builder().url(streamUrl).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) throw Exception("HTTP error: ${response.code}")
                
                val body = response.body ?: throw Exception("Response body is null")
                val contentLength = body.contentLength()

                val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                val file = File(downloadsDir, "${track.id}.mp3")
                if (file.exists()) file.delete() 

                var totalRead: Long = 0
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            // Check for cancellation during loop
                            if (!isActive) throw CancellationException("Download cancelled by user")
                            
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

                if (totalRead == 0L || !file.exists()) throw Exception("Zero bytes written")

                // 4. Commit to Database
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
                delay(1000)
                removeDownload(track.id)

            } catch (e: CancellationException) {
                Log.d(TAG, "Download job cancelled for ${track.id}")
                cleanupFile(context, track.id)
                removeDownload(track.id)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for ${track.id}: ${e.message}")
                cleanupFile(context, track.id)
                database.offlineSongDao().deleteOfflineSong(track.id)
                removeDownload(track.id)
            }
        }
        
        activeJobs[track.id] = job
    }

    fun removeTrack(context: Context, database: PulseDatabase, trackId: String) {
        // 1. Cancel active job if any
        activeJobs[trackId]?.cancel("Removed by user")
        
        scope.launch {
            try {
                // 2. Remove from database
                database.offlineSongDao().deleteOfflineSong(trackId)
                
                // 3. Remove physical file
                cleanupFile(context, trackId)
                
                // 4. Remove from progress map
                removeDownload(trackId)
            } catch (e: Exception) {
                Log.e(TAG, "Error during track removal", e)
            }
        }
    }

    private fun cleanupFile(context: Context, trackId: String) {
        try {
            val file = File(context.getExternalFilesDir(null), "downloads/$trackId.mp3")
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Cleaned up file for $trackId")
            }
        } catch (e: Exception) { /* Silent */ }
    }

    private fun updateProgress(id: String, progress: Float) {
        val current = _downloadProgress.value.toMutableMap()
        current[id] = progress
        _downloadProgress.value = current
    }

    private fun removeDownload(id: String) {
        activeJobs.remove(id)
        val current = _downloadProgress.value.toMutableMap()
        current.remove(id)
        _downloadProgress.value = current
    }
}
