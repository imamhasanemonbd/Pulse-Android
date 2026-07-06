package com.android.pulse.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.android.pulse.data.local.PulseDatabase
import com.android.pulse.data.local.entity.HistoryEntity
import com.android.pulse.data.local.entity.LikedSongEntity
import com.android.pulse.data.model.Track
import com.android.pulse.data.remote.innertube.InnerTubeRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.media3.common.util.UnstableApi
import com.android.pulse.data.local.entity.PlaylistSongCrossRef
import java.io.File

@UnstableApi
class AudioPlayerManager(private val context: Context, val database: PulseDatabase) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context))
        .build()

    private var controllerFuture: ListenableFuture<MediaController>? = null

    val visualizerManager = VisualizerManager()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

    val isLiked: StateFlow<Boolean> = currentTrack.flatMapLatest { track ->
        if (track == null) flowOf(false)
        else database.likedSongDao().isLiked(track.id)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    val currentQueue: List<Track> get() = queue.toList()

    private val queue = mutableListOf<Track>()

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED
                    )) {
                    val index = player.currentMediaItemIndex
                    if (index in queue.indices) {
                        val track = queue[index]
                        if (_currentTrack.value?.id != track.id) {
                            _currentTrack.value = track
                        }
                    }
                    _isPlaying.value = player.isPlaying
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startService()
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
                val index = player.currentMediaItemIndex
                if (index in queue.indices) {
                    scope.launch { loadTrackUriForIndex(index) }
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
                        scope.launch { loadTrackUriForIndex(index) }
                    }
                    
                    if (index + 1 in queue.indices) {
                        scope.launch { loadTrackUriForIndex(index + 1) }
                    }
                }
            }
        })

        scope.launch {
            while (isActive) {
                if (player.isPlaying) _currentTime.value = player.currentPosition
                delay(200)
            }
        }
    }

    private fun startService() {
        val intent = android.content.Intent(context, MediaPlaybackService::class.java)
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
                queue.addAll(tracks.ifEmpty { listOf(track) })
                
                val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                _currentTrack.value = queue[startIndex]

                // Map media items initially with placeholder, loadTrackUriForIndex will update them
                val mediaItems = queue.map { item ->
                    val metadata = MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
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
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayer", "Playback failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadTrackUriForIndex(index: Int) {
        if (index !in queue.indices) return
        val track = queue[index]
        
        // CHECK DATABASE FOR OFFLINE FILE
        val offlineSong = database.offlineSongDao().getAllOfflineSongs().first().find { it.id == track.id && it.isFinished }
        val localFile = offlineSong?.localPath?.let { File(it) }
        
        val uri = if (localFile != null && localFile.exists() && localFile.length() > 0) {
            android.util.Log.d("AudioPlayer", "Verified OFFLINE File: ${localFile.absolutePath} (${localFile.length()} bytes)")
            Uri.fromFile(localFile)
        } else {
            android.util.Log.d("AudioPlayer", "Offline file not found or invalid. Fetching ONLINE URL for: ${track.id}")
            InnerTubeRepository.getStreamUrl(track.id)?.let { Uri.parse(it) }
        }

        if (uri != null) {
            withContext(Dispatchers.Main) {
                val currentItem = player.getMediaItemAt(index)
                val updatedItem = currentItem.buildUpon().setUri(uri).build()
                player.replaceMediaItem(index, updatedItem)
                android.util.Log.d("AudioPlayer", "Successfully assigned URI to player: $uri")
            }
        } else {
            android.util.Log.e("AudioPlayer", "Could not resolve ANY URI (Online or Offline) for ${track.id}")
        }
    }

    private fun saveToHistory(track: Track) {
        scope.launch(Dispatchers.IO) {
            database.historyDao().insertHistoryEntry(
                HistoryEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    thumbnailUrl = track.thumbnail,
                    duration = track.duration
                )
            )
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() { if (player.hasNextMediaItem()) player.seekToNext() }
    fun skipPrevious() { if (player.hasPreviousMediaItem()) player.seekToPrevious() }
    fun seekTo(position: Long) { player.seekTo(position) }
    fun toggleShuffle() { 
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        _shuffleMode.value = player.shuffleModeEnabled
    }
    fun cycleRepeatMode() {
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        _repeatMode.value = nextMode
    }
    fun setVolume(vol: Float) {
        player.volume = vol
        _volume.value = vol
    }

    fun toggleLike() {
        val track = currentTrack.value ?: return
        scope.launch(Dispatchers.IO) {
            val liked = database.likedSongDao().isLiked(track.id).first()
            if (liked) {
                database.likedSongDao().deleteLikedSong(LikedSongEntity(track.id, track.title, track.artist, track.thumbnail, track.duration))
            } else {
                database.likedSongDao().insertLikedSong(LikedSongEntity(track.id, track.title, track.artist, track.thumbnail, track.duration))
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track, shortcut: String?) {
        scope.launch(Dispatchers.IO) {
            database.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(playlistId, track.id))
        }
    }

    fun createPlaylistAndAddTrack(name: String, track: Track) {
        scope.launch(Dispatchers.IO) {
            val id = database.playlistDao().insertPlaylist(com.android.pulse.data.local.entity.PlaylistEntity(name = name))
            database.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(id, track.id))
        }
    }

    fun release() {
        player.release()
        visualizerManager.release()
        scope.cancel()
    }
}
