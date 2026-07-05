package com.android.pulse.data.remote.innertube.model

data class InnerTubeContext(
    val client: InnerTubeClientInfo,
    val user: InnerTubeTubeUserInfo = InnerTubeTubeUserInfo()
)

data class InnerTubeTubeUserInfo(
    val lockedSafetyMode: Boolean = false
)

data class InnerTubeClientInfo(
    val clientName: String,
    val clientVersion: String,
    val hl: String = "en",
    val gl: String = "US",
    val userAgent: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val platform: String? = null,
    val androidSdkVersion: Int? = null,
    val visitorData: String? = null
)

data class SearchRequest(
    val context: InnerTubeContext,
    val query: String,
    val params: String? = null
)

data class BrowseRequest(
    val context: InnerTubeContext,
    val browseId: String,
    val params: String? = null
)

data class Category(
    val title: String,
    val browseId: String,
    val params: String? = null,
    val color: Long = 0xFF212121 // Default dark color
)

/**
 * Specialized request for bypass resolution.
 */
data class PlayerRequest(
    val context: InnerTubeContext,
    val videoId: String,
    val playbackContext: PlaybackContext = PlaybackContext(ContentPlaybackContext(19850))
)

/**
 * Specialized request for bypass resolution.
 */
data class BypassPlayerRequest(
    val videoId: String,
    val client: InnerTubeClientInfo,
    val playbackContext: PlaybackContext = PlaybackContext(ContentPlaybackContext(19800))
)

data class PlaybackContext(
    val contentPlaybackContext: ContentPlaybackContext
)

data class ContentPlaybackContext(
    val signatureTimestamp: Int
)

data class InnerTubeResponse(
    val responseContext: ResponseContext? = null,
    val contents: Any? = null,
    val onResponseReceivedCommands: List<Any>? = null,
    val streamingData: StreamingData? = null,
    val playabilityStatus: PlayabilityStatus? = null
)

data class ResponseContext(
    val visitorData: String? = null
)

data class PlayabilityStatus(
    val status: String,
    val reason: String? = null,
    val messages: List<String>? = null
)

data class StreamingData(
    val adaptiveFormats: List<AdaptiveFormat>? = null,
    val formats: List<AdaptiveFormat>? = null
)

data class AdaptiveFormat(
    val url: String? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val contentLength: String? = null,
    val audioQuality: String? = null,
    val audioSampleRate: String? = null
)
