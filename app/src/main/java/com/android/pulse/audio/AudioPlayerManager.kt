package com.android.pulse.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.android.pulse.data.local.PulseDatabase
import com.android.pulse.data.local.entity.PlaylistEntity
import com.android.pulse.data.local.entity.PlaylistSongCrossRef
import com.android.pulse.data.local.entity.toHistoryEntity
import com.android.pulse.data.local.entity.toLikedEntity
import com.android.pulse.data.model.Track
import com.android.pulse.data.remote.innertube.InnerTubeRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.net.Uri
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

@UnstableApi
class AudioPlayerManager(private val context: Context, val database: PulseDatabase) {
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(15000)

    val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
        .build()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    
    private val visualizerManager = VisualizerManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentTime = MutableStateFlow(0L)
    val currentTime: StateFlow<Long> = _currentTime.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    val pulsation: StateFlow<Float> = visualizerManager.pulsation

    val isLiked: StateFlow<Boolean> = _currentTrack.flatMapLatest { track ->
        if (track == null) flowOf(false)
        else database.likedSongDao().isLiked(track.id)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    private val queue = mutableListOf<Track>()

    init {
        // Initialize MediaController to ensure the service stays alive and handles notifications
        val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            android.util.Log.d("AudioPlayer", "MediaController bound to service")
        }, MoreExecutors.directExecutor())

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startService()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                _isLoading.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0)
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                visualizerManager.init(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("AudioPlayer", "Error: ${error.message}")
                val index = player.currentMediaItemIndex
                if (index in queue.indices) {
                    loadTrackUriForIndex(index)
                }
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                if (index in queue.indices) {
                    val track = queue[index]
                    _currentTrack.value = track
                    saveToHistory(track)
                    
                    val currentUri = player.currentMediaItem?.localConfiguration?.uri.toString()
                    if (currentUri == "" || currentUri == "https://dummy.mp3") {
                        loadTrackUriForIndex(index)
                    }
                    
                    if (index + 1 in queue.indices) {
                        loadTrackUriForIndex(index + 1)
                    }
                }
            }
        })

        scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _currentTime.value = player.currentPosition
                }
                delay(1000)
            }
        }
    }

    private fun startService() {
        val intent = Intent(context, MediaPlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun playTrack(track: Track, tracks: List<Track> = emptyList()) {
        scope.launch {
            _isLoading.value = true
            try {
                startService()

                queue.clear()
                if (tracks.isNotEmpty()) {
                    queue.addAll(tracks)
                } else {
                    queue.add(track)
                }
                
                val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                _currentTrack.value = queue[startIndex]

                val mediaItems = queue.map { item ->
                    val metadata = MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setDisplayTitle(item.title)
                        .setSubtitle(item.artist)
                        .setArtworkUri(Uri.parse(item.thumbnail ?: ""))
                        .build()

                    MediaItem.Builder()
                        .setMediaId(item.id)
                        .setUri("https://dummy.mp3")
                        .setMediaMetadata(metadata)
                        .build()
                }

                player.setMediaItems(mediaItems, startIndex, 0)
                player.prepare()
                
                loadTrackUriForIndex(startIndex)
                
                player.play()
                saveToHistory(queue[startIndex])
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayer", "Playback failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadTrackUriForIndex(index: Int) {
        if (index !in queue.indices) return
        val track = queue[index]
        
        scope.launch {
            try {
                val uriString = InnerTubeRepository.getStreamUrl(track.id)

                if (uriString != null) {
                    val currentItem = player.getMediaItemAt(index)
                    val updatedItem = currentItem.buildUpon()
                        .setUri(Uri.parse(uriString))
                        .build()
                    
                    player.replaceMediaItem(index, updatedItem)
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayer", "Failed to load URI for track ${track.id}", e)
            }
        }
    }

    private fun saveToHistory(track: Track) {
        scope.launch(Dispatchers.IO) {
            database.historyDao().insertHistoryEntry(track.toHistoryEntity())
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNext()
        }
    }

    fun skipPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
        }
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    fun toggleShuffle() {
        _shuffleMode.value = !_shuffleMode.value
        player.shuffleModeEnabled = _shuffleMode.value
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        player.repeatMode = nextMode
    }

    fun setVolume(value: Float) {
        _volume.value = value
        player.volume = value
    }

    fun toggleLike() {
        val track = _currentTrack.value ?: return
        scope.launch {
            val liked = isLiked.value
            withContext(Dispatchers.IO) {
                if (liked) {
                    database.likedSongDao().deleteLikedSong(track.toLikedEntity())
                } else {
                    database.likedSongDao().insertLikedSong(track.toLikedEntity())
                }
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track, playlistName: String? = null) {
        scope.launch(Dispatchers.IO) {
            database.likedSongDao().insertLikedSong(track.toLikedEntity())
            database.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(playlistId, track.id))
            withContext(Dispatchers.Main) {
                val message = if (playlistName != null) "Added to $playlistName" else "Added to playlist"
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createPlaylistAndAddTrack(name: String, track: Track) {
        scope.launch(Dispatchers.IO) {
            val playlistId = database.playlistDao().insertPlaylist(PlaylistEntity(name = name))
            addTrackToPlaylist(playlistId, track, name)
        }
    }

    fun release() {
        visualizerManager.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        player.release()
        scope.cancel()
    }
}
