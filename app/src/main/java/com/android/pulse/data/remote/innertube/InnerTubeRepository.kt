package com.android.pulse.data.remote.innertube

import android.util.Log
import com.android.pulse.data.model.Track
import com.android.pulse.data.remote.innertube.model.*
import com.google.gson.internal.LinkedTreeMap

/**
 * Singleton repository to manage network requests and parsing logic.
 * Uses consistent InnerTubeClient (Retrofit/OkHttp) to prevent memory leaks.
 */
object InnerTubeRepository {
    private const val TAG = "InnerTubeRepo"

    suspend fun getStreamUrl(videoId: String): String? {
        ensureSession()

        // 1. Try ANDROID_VR (Most resilient to bot detection)
        val vrResult = runCatching {
            val request = PlayerRequest(
                context = InnerTubeClient.createVrContext(),
                videoId = videoId
            )
            val response = InnerTubeClient.apiService.player(
                request, 
                InnerTubeClient.USER_AGENT_VR
            )
            extractAudioUrl(response)
        }
        
        vrResult.getOrNull()?.let { return it }
        Log.w(TAG, "VR resolution failed: ${vrResult.exceptionOrNull()?.message}")

        // 2. Try TVHTML5 (Stable fallback)
        val tvResult = runCatching {
            val request = PlayerRequest(
                context = InnerTubeClient.createTvContext(),
                videoId = videoId
            )
            val response = InnerTubeClient.apiService.player(
                request, 
                InnerTubeClient.USER_AGENT_TV
            )
            extractAudioUrl(response)
        }
        
        tvResult.getOrNull()?.let { return it }
        Log.w(TAG, "TV resolution failed: ${tvResult.exceptionOrNull()?.message}")

        // 3. Fallback to standard Mobile Web
        val fallbackResult = runCatching {
            val fallbackRequest = PlayerRequest(
                context = InnerTubeClient.createMWebContext(),
                videoId = videoId
            )
            val response = InnerTubeClient.apiService.player(
                fallbackRequest,
                InnerTubeClient.USER_AGENT_MWEB
            )
            extractAudioUrl(response)
        }
        
        return fallbackResult.getOrNull()
    }

    private suspend fun ensureSession() {
        if (InnerTubeClient.getVisitorData() != null) return
        
        runCatching {
            val request = BrowseRequest(
                context = InnerTubeClient.createMWebContext(),
                browseId = "FEwhat_to_watch"
            )
            val response = InnerTubeClient.apiService.browse(request)
            val visitorData = response.responseContext?.visitorData
            if (visitorData != null) {
                InnerTubeClient.setVisitorData(visitorData)
            }
        }.onFailure {
            Log.e(TAG, "Failed to establish session: ${it.message}")
        }
    }

    private fun extractAudioUrl(response: InnerTubeResponse): String? {
        if (response.playabilityStatus?.status != "OK") {
            Log.w(TAG, "Playability restriction: ${response.playabilityStatus?.status}")
            return null
        }
        
        val formats = response.streamingData?.adaptiveFormats ?: response.streamingData?.formats
        return formats
            ?.filter { it.mimeType?.startsWith("audio/") == true }
            ?.filter { it.url != null }
            ?.maxByOrNull { it.bitrate ?: 0 }
            ?.url
    }

    suspend fun searchMusic(query: String): List<Track> {
        return try {
            val request = SearchRequest(
                context = InnerTubeClient.createMWebContext(),
                query = query
            )

            val response = InnerTubeClient.apiService.search(request)
            response.responseContext?.visitorData?.let { InnerTubeClient.setVisitorData(it) }

            val tracks = mutableListOf<Track>()
            findTracks(response.contents, tracks)
            findTracks(response.onResponseReceivedCommands, tracks)
            
            Log.d(TAG, "Search Found ${tracks.size} tracks.")
            tracks.distinctBy { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching music: ${e.message}")
            emptyList()
        }
    }

    suspend fun getExploreCategories(): List<Category> {
        return try {
            val request = BrowseRequest(
                context = InnerTubeClient.createWebContext(),
                browseId = "FEmusic_explore"
            )
            val response = InnerTubeClient.apiService.browse(
                request,
                InnerTubeClient.USER_AGENT_WEB
            )
            val categories = mutableListOf<Category>()
            findCategories(response.contents, categories)
            
            if (categories.isEmpty()) {
                val moodsRequest = BrowseRequest(
                    context = InnerTubeClient.createWebContext(),
                    browseId = "FEmusic_moods_and_genres"
                )
                val moodsResponse = InnerTubeClient.apiService.browse(
                    moodsRequest,
                    InnerTubeClient.USER_AGENT_WEB
                )
                findCategories(moodsResponse.contents, categories)
            }

            categories.distinctBy { it.title }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching categories: ${e.message}")
            emptyList()
        }
    }

    suspend fun browseCategory(browseId: String, params: String?): List<Track> {
        return try {
            val request = BrowseRequest(
                context = InnerTubeClient.createWebContext(),
                browseId = browseId,
                params = params
            )
            val response = InnerTubeClient.apiService.browse(
                request,
                InnerTubeClient.USER_AGENT_WEB
            )
            val tracks = mutableListOf<Track>()
            findTracks(response.contents, tracks)
            tracks.distinctBy { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "Error browsing category: ${e.message}")
            emptyList()
        }
    }

    private fun findTracks(node: Any?, tracks: MutableList<Track>) {
        if (node == null) return
        when (node) {
            is List<*> -> {
                node.forEach { findTracks(it, tracks) }
            }
            is LinkedTreeMap<*, *> -> {
                val videoRenderer = (node["videoRenderer"] as? LinkedTreeMap<*, *>)
                    ?: (node["videoWithContextRenderer"] as? LinkedTreeMap<*, *>)
                    ?: (node["playlistVideoRenderer"] as? LinkedTreeMap<*, *>)
                
                if (videoRenderer != null) {
                    val videoId = videoRenderer["videoId"] as? String
                    val title = parseRuns(videoRenderer["title"]) ?: parseRuns(videoRenderer["headline"])
                    val artist = parseRuns(videoRenderer["longBylineText"]) ?: parseRuns(videoRenderer["shortBylineText"]) ?: parseRuns(videoRenderer["ownerText"])
                    val thumbnailObj = videoRenderer["thumbnail"] as? LinkedTreeMap<*, *>
                    val thumbnails = thumbnailObj?.get("thumbnails") as? List<*>
                    val thumbnailUrl = (thumbnails?.lastOrNull() as? LinkedTreeMap<*, *>)?.get("url") as? String

                    if (videoId != null && title != null) {
                        tracks.add(Track(videoId, title, artist ?: "Unknown Artist", thumbnailUrl, 0))
                    }
                } else {
                    val responsiveRenderer = node["musicResponsiveListItemRenderer"] as? LinkedTreeMap<*, *>
                    if (responsiveRenderer != null) {
                        val flexColumns = responsiveRenderer["flexColumns"] as? List<*>
                        val firstColumn = (flexColumns?.getOrNull(0) as? LinkedTreeMap<String, Any>)?.get("musicResponsiveListItemFlexColumnRenderer") as? LinkedTreeMap<String, Any>
                        val secondColumn = (flexColumns?.getOrNull(1) as? LinkedTreeMap<String, Any>)?.get("musicResponsiveListItemFlexColumnRenderer") as? LinkedTreeMap<String, Any>
                        
                        val title = parseRuns(firstColumn?.get("text"))
                        val artist = parseRuns(secondColumn?.get("text"))
                        
                        val thumbnailObj = responsiveRenderer["thumbnail"] as? LinkedTreeMap<String, Any>
                        val musicThumbnailRenderer = thumbnailObj?.get("musicThumbnailRenderer") as? LinkedTreeMap<String, Any>
                        val thumbnails = musicThumbnailRenderer?.get("thumbnail") as? LinkedTreeMap<String, Any>
                        val thumbnailList = thumbnails?.get("thumbnails") as? List<*>
                        val thumbnailUrl = (thumbnailList?.lastOrNull() as? LinkedTreeMap<String, Any>)?.get("url") as? String
                        
                        var navigationEndpoint = responsiveRenderer["navigationEndpoint"] as? LinkedTreeMap<String, Any>
                        if (navigationEndpoint == null) {
                            val overlay = responsiveRenderer["overlay"] as? LinkedTreeMap<String, Any>
                            val musicItemThumbnailOverlayRenderer = overlay?.get("musicItemThumbnailOverlayRenderer") as? LinkedTreeMap<String, Any>
                            val content = musicItemThumbnailOverlayRenderer?.get("content") as? LinkedTreeMap<String, Any>
                            val musicPlayButtonRenderer = content?.get("musicPlayButtonRenderer") as? LinkedTreeMap<String, Any>
                            navigationEndpoint = musicPlayButtonRenderer?.get("playNavigationEndpoint") as? LinkedTreeMap<String, Any>
                        }
                        
                        val watchEndpoint = navigationEndpoint?.get("watchEndpoint") as? LinkedTreeMap<String, Any>
                        val videoId = watchEndpoint?.get("videoId") as? String

                        if (videoId != null && title != null) {
                            tracks.add(Track(videoId, title, artist ?: "Unknown Artist", thumbnailUrl, 0))
                        }
                    } else {
                        val twoRowRenderer = node["musicTwoRowItemRenderer"] as? LinkedTreeMap<String, Any>
                        if (twoRowRenderer != null) {
                            val title = parseRuns(twoRowRenderer["title"])
                            val artist = parseRuns(twoRowRenderer["subtitle"])
                            
                            val thumbnailObj = twoRowRenderer["thumbnailRenderer"] as? LinkedTreeMap<String, Any>
                            val musicThumbnailRenderer = thumbnailObj?.get("musicThumbnailRenderer") as? LinkedTreeMap<String, Any>
                            val thumbnails = musicThumbnailRenderer?.get("thumbnail") as? LinkedTreeMap<String, Any>
                            val thumbnailList = thumbnails?.get("thumbnails") as? List<*>
                            val thumbnailUrl = (thumbnailList?.lastOrNull() as? LinkedTreeMap<String, Any>)?.get("url") as? String
                            
                            val navigationEndpoint = twoRowRenderer["navigationEndpoint"] as? LinkedTreeMap<String, Any>
                            val watchEndpoint = navigationEndpoint?.get("watchEndpoint") as? LinkedTreeMap<String, Any>
                            val videoId = watchEndpoint?.get("videoId") as? String

                            if (videoId != null && title != null) {
                                tracks.add(Track(videoId, title, artist ?: "Unknown Artist", thumbnailUrl, 0))
                            }
                        }
                        // Use explicit casting to avoid potential iterator issues
                        (node as? Map<*, *>)?.values?.forEach { findTracks(it, tracks) }
                    }
                }
            }
        }
    }

    private fun findCategories(node: Any?, categories: MutableList<Category>) {
        if (node == null) return
        when (node) {
            is List<*> -> {
                node.forEach { findCategories(it, categories) }
            }
            is LinkedTreeMap<*, *> -> {
                val buttonRenderer = node["musicNavigationButtonRenderer"] as? LinkedTreeMap<*, *>
                if (buttonRenderer != null) {
                    val title = parseRuns(buttonRenderer["buttonText"])
                    val clickCommand = buttonRenderer["clickCommand"] as? LinkedTreeMap<*, *>
                    val browseEndpoint = clickCommand?.get("browseEndpoint") as? LinkedTreeMap<*, *>
                    val browseId = browseEndpoint?.get("browseId") as? String
                    val params = browseEndpoint?.get("params") as? String
                    
                    val solid = buttonRenderer["solid"] as? LinkedTreeMap<*, *>
                    val color = (solid?.get("leftIconColor") as? Double)?.toLong() 
                        ?: (solid?.get("leftStripeColor") as? Double)?.toLong()
                        ?: 0xFF212121

                    if (title != null && browseId != null) {
                        categories.add(Category(title, browseId, params, color))
                    }
                } else {
                    node.values.forEach { findCategories(it, categories) }
                }
            }
        }
    }

    private fun parseRuns(node: Any?): String? {
        if (node == null) return null
        if (node is String) return node
        val runs = (node as? LinkedTreeMap<*, *>)?.get("runs") as? List<*>
            ?: (node as? LinkedTreeMap<*, *>)?.get("simpleText")?.let { listOf(LinkedTreeMap<String, Any>().apply { put("text", it) }) }
        return runs?.mapNotNull { (it as? LinkedTreeMap<*, *>)?.get("text") as? String }?.joinToString("")
    }
}
