package com.android.pulse.data.remote.innertube

import android.util.Log
import com.android.pulse.data.model.Track
import com.android.pulse.data.remote.innertube.model.*
import com.google.gson.Gson

/**
 * Singleton repository with strict Music-Only filtering logic.
 */
object InnerTubeRepository {
    private const val TAG = "PULSE_TAG"
    private val gson = Gson()

    suspend fun getStreamUrl(videoId: String): String? {
        ensureSession()
        val contexts = listOf(
            InnerTubeClient.createVrContext() to InnerTubeClient.USER_AGENT_VR,
            InnerTubeClient.createTvContext() to InnerTubeClient.USER_AGENT_TV,
            InnerTubeClient.createMWebContext() to InnerTubeClient.USER_AGENT_MWEB
        )
        
        for ((ctx, ua) in contexts) {
            val result = runCatching {
                val response = InnerTubeClient.apiService.player(PlayerRequest(ctx, videoId), ua)
                extractAudioUrl(response)
            }
            result.getOrNull()?.let { return it }
        }
        return null
    }

    private suspend fun ensureSession() {
        if (InnerTubeClient.getVisitorData() != null) return
        runCatching {
            val request = BrowseRequest(context = InnerTubeClient.createWebContext(), browseId = "FEmusic_home")
            val response = InnerTubeClient.apiService.browse(request, InnerTubeClient.USER_AGENT_WEB)
            val visitorData = response.responseContext?.visitorData
            if (visitorData != null) {
                InnerTubeClient.setVisitorData(visitorData)
            }
        }
    }

    private fun extractAudioUrl(response: InnerTubeResponse): String? {
        if (response.playabilityStatus?.status != "OK") return null
        val formats = response.streamingData?.adaptiveFormats ?: response.streamingData?.formats
        return formats
            ?.filter { it.mimeType?.startsWith("audio/") == true && it.url != null }
            ?.maxByOrNull { it.bitrate ?: 0 }
            ?.url
    }

    suspend fun searchMusic(query: String, params: String? = null): List<Track> {
        Log.e(TAG, "MUSIC-FILTER: searching '$query'")
        return try {
            // WEB_REMIX naturally filters for YouTube Music results
            val request = SearchRequest(context = InnerTubeClient.createWebContext(), query = query, params = params)
            val response = InnerTubeClient.apiService.search(request, InnerTubeClient.USER_AGENT_WEB)
            response.responseContext?.visitorData?.let { InnerTubeClient.setVisitorData(it) }
            
            val tracks = mutableListOf<Track>()
            parseGreedy(response, tracks)
            
            Log.e(TAG, "MUSIC-FILTER: found ${tracks.size} tracks")
            tracks.distinctBy { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "Search fatal", e)
            emptyList()
        }
    }

    suspend fun getTrending(): List<Track> {
        return searchMusic("Trending Music")
    }

    suspend fun getHomeData(categoryParams: String? = null, relatedToVideoId: String? = null): HomeData {
        Log.e(TAG, "MUSIC-FILTER: getHomeData(related=$relatedToVideoId, catParams=$categoryParams)")
        return try {
            val allTracks = mutableListOf<Track>()
            val categories = mutableListOf<Category>()
            
            val response = if (relatedToVideoId != null) {
                Log.d(TAG, "MUSIC-FILTER: Fetching NEXT for $relatedToVideoId")
                InnerTubeClient.apiService.next(NextRequest(context = InnerTubeClient.createWebContext(), videoId = relatedToVideoId), InnerTubeClient.USER_AGENT_WEB)
            } else {
                Log.d(TAG, "MUSIC-FILTER: Fetching BROWSE for cat=$categoryParams")
                InnerTubeClient.apiService.browse(BrowseRequest(context = InnerTubeClient.createWebContext(), browseId = "FEmusic_home", params = categoryParams), InnerTubeClient.USER_AGENT_WEB)
            }

            parseGreedy(response, allTracks)
            findCategories(convertToStd(response.contents), categories)
            
            Log.d(TAG, "MUSIC-FILTER: Parsed ${allTracks.size} total music tracks")
            
            if (categories.isEmpty()) {
                categories.addAll(listOf(
                    Category("Energize", "FEmusic_home", "ggMPOg1uX2V4Y2x1c2l2ZV9y"),
                    Category("Relax", "FEmusic_home", "ggMPOg1vX2V4Y2x1c2l2ZV9y"),
                    Category("Feel good", "FEmusic_home", "ggMPOg1wX2V4Y2x1c2l2ZV9y"),
                    Category("Party", "FEmusic_home", "ggMPOg1yX2V4Y2x1c2l2ZV9y")
                ))
            }
            
            if (allTracks.isEmpty()) {
                Log.e(TAG, "MUSIC-FILTER: Home yielded 0 tracks, using trending fallback")
                allTracks.addAll(getTrending())
            }
            
            val distinct = allTracks.filter { it.id != relatedToVideoId }.distinctBy { it.id }
            val speedDial = distinct.take(12)
            val quickPicks = distinct.drop(12).take(30).ifEmpty { distinct.shuffled().take(20) }
            
            HomeData(quickPicks, speedDial, categories.distinctBy { it.title })
        } catch (e: Exception) {
            Log.e(TAG, "Home fatal", e)
            HomeData(getTrending(), getTrending().shuffled(), emptyList())
        }
    }

    /**
     * GREEDY PARSER with music-specific validation.
     */
    private fun parseGreedy(response: InnerTubeResponse, out: MutableList<Track>) {
        val root = convertToStd(response.contents)
        val commands = convertToStd(response.onResponseReceivedCommands)
        
        deepScan(root, out)
        deepScan(commands, out)
    }

    private fun deepScan(node: Any?, out: MutableList<Track>) {
        if (node == null) return
        if (node is List<*>) {
            node.forEach { deepScan(it, out) }
        } else if (node is Map<*, *>) {
            val vid = extractVideoId(node)
            if (vid != null) {
                // Validation: Only add if it looks like a music track
                if (isMusicMedia(node)) {
                    val title = extractTitle(node)
                    if (title != null) {
                        val track = Track(
                            vid, 
                            title, 
                            extractArtist(node) ?: "Unknown Artist", 
                            extractThumbnail(node) ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg", 
                            0
                        )
                        if (out.none { it.id == vid }) {
                            out.add(track)
                            Log.d(TAG, "MUSIC VALIDATED: ${track.title} ($vid)")
                        }
                    }
                }
            }
            node.values.forEach { deepScan(it, out) }
        }
    }

    /**
     * Strict validation to filter out telefilms, vlogs, and general videos.
     */
    private fun isMusicMedia(map: Map<*, *>): Boolean {
        val json = gson.toJson(map)
        
        // 1. Check for specific Music Renderers/Types
        val hasMusicType = json.contains("MUSIC_VIDEO_TYPE_ATV") || 
                           json.contains("MUSIC_VIDEO_TYPE_OMV") || 
                           json.contains("MUSIC_PAGE_TYPE_TRACK") ||
                           json.contains("MUSIC_PAGE_TYPE_ALBUM")
        
        if (hasMusicType) return true

        // 2. Blacklist common non-music terms in title/artist
        val title = (extractTitle(map) ?: "").lowercase()
        val artist = (extractArtist(map) ?: "").lowercase()
        val blacklist = listOf("telefilm", "vlog", "blog", "full movie", "episode", "drama", "documentary", "news")
        
        if (blacklist.any { title.contains(it) || artist.contains(it) }) return false

        // 3. Optional: Duration check (Most songs are under 10 minutes)
        // This is a heuristic; phonks/mixes might be longer, but telefilms are 30min+
        val durationText = json.substringAfter("\"simpleText\":\"", "").substringBefore("\"", "")
        if (durationText.contains(":") && !durationText.contains("::")) {
            val parts = durationText.split(":")
            if (parts.size >= 3) return false // More than 1 hour is likely not a song
            val mins = parts[0].toIntOrNull() ?: 0
            if (mins > 15 && !title.contains("mix") && !title.contains("phonk")) return false
        }

        // If it's in a music-specific list item renderer, it's likely music
        return map.containsKey("musicResponsiveListItemRenderer") || 
               map.containsKey("musicTwoRowItemRenderer")
    }

    private fun extractVideoId(map: Map<*, *>): String? {
        (map["videoId"] as? String)?.let { return it }
        (map["playlistItemData"] as? Map<*, *>)?.get("videoId")?.toString()?.let { return it }
        
        val nav = (map["navigationEndpoint"] as? Map<*, *>) ?: (map["serviceEndpoint"] as? Map<*, *>)
        (nav?.get("watchEndpoint") as? Map<*, *>)?.get("videoId")?.toString()?.let { return it }
        (nav?.get("watchPlaylistEndpoint") as? Map<*, *>)?.get("videoId")?.toString()?.let { return it }
        
        val overlay = (map["overlay"] as? Map<*, *>)?.get("musicItemThumbnailOverlayRenderer") as? Map<*, *>
        val playBtn = overlay?.get("content")?.let { (it as? Map<*, *>)?.get("musicPlayButtonRenderer") as? Map<*, *> }
        (playBtn?.get("playNavigationEndpoint") as? Map<*, *>)?.get("watchEndpoint")?.let { (it as? Map<*, *>)?.get("videoId")?.toString()?.let { vid -> return vid } }

        return null
    }

    private fun extractTitle(map: Map<*, *>): String? {
        (map["flexColumns"] as? List<*>)?.let { flex ->
            flex.firstOrNull()?.let { col ->
                val r = (col as? Map<*, *>)?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
                parseRuns(r?.get("text"))?.let { return it }
            }
        }
        return parseRuns(map["title"]) ?: parseRuns(map["headline"]) ?: parseRuns(map["text"]) ?: (map["title"] as? String)
    }

    private fun extractArtist(map: Map<*, *>): String? {
        (map["flexColumns"] as? List<*>)?.let { flex ->
            if (flex.size > 1) {
                for (i in 1 until flex.size) {
                    val r = (flex[i] as? Map<*, *>)?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
                    val t = parseRuns(r?.get("text"))
                    if (t != null && !t.contains("views", true) && !t.contains("plays", true) && !t.any { it.isDigit() }) return t
                }
            }
        }
        return parseRuns(map["longBylineText"]) ?: parseRuns(map["shortBylineText"]) ?: parseRuns(map["subtitle"]) ?: parseRuns(map["bylineText"])
    }

    private fun extractThumbnail(map: Map<*, *>): String? {
        val node = (map["thumbnail"] as? Map<*, *>) ?: (map["thumbnailRenderer"] as? Map<*, *>)
        val nested = (node?.get("musicThumbnailRenderer") as? Map<*, *>) ?: node
        val list = (nested?.get("thumbnail") as? Map<*, *>)?.get("thumbnails") as? List<*> ?: nested?.get("thumbnails") as? List<*>
        return (list?.lastOrNull() as? Map<*, *>)?.get("url") as? String
    }

    private fun findCategories(node: Any?, categories: MutableList<Category>) {
        if (node == null) return
        if (node is List<*>) {
            node.forEach { findCategories(it, categories) }
        } else if (node is Map<*, *>) {
            val button = node["musicNavigationButtonRenderer"] as? Map<*, *>
            if (button != null) {
                val title = parseRuns(button["buttonText"])
                val id = (button["clickCommand"] as? Map<*, *>)?.let { (it["browseEndpoint"] as? Map<*, *>)?.get("browseId") as? String }
                if (title != null && id != null) categories.add(Category(title, id))
            }
            node.values.forEach { findCategories(it, categories) }
        }
    }

    private fun parseRuns(node: Any?): String? {
        if (node == null) return null
        if (node is String) return node
        val map = node as? Map<*, *> ?: return null
        val runs = map["runs"] as? List<*> ?: map["simpleText"]?.let { listOf(mapOf("text" to it)) }
        return runs?.mapNotNull { (it as? Map<*, *>)?.get("text") as? String }?.joinToString("")
    }

    private fun convertToStd(obj: Any?): Any? {
        if (obj == null) return null
        if (obj is List<*>) return obj.map { convertToStd(it) }
        if (obj is Map<*, *>) {
            val map = mutableMapOf<String, Any?>()
            for (entry in obj) {
                map[entry.key.toString()] = convertToStd(entry.value)
            }
            return map
        }
        return obj
    }

    suspend fun browseCategory(browseId: String, params: String?): List<Track> {
        return try {
            val response = InnerTubeClient.apiService.browse(BrowseRequest(InnerTubeClient.createWebContext(), browseId, params), InnerTubeClient.USER_AGENT_WEB)
            val tracks = mutableListOf<Track>()
            parseGreedy(response, tracks)
            tracks.distinctBy { it.id }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getExploreCategories(): List<Category> {
        return try {
            val request = BrowseRequest(context = InnerTubeClient.createWebContext(), browseId = "FEmusic_explore")
            val response = InnerTubeClient.apiService.browse(request, InnerTubeClient.USER_AGENT_WEB)
            val categories = mutableListOf<Category>()
            findCategories(convertToStd(response.contents), categories)
            if (categories.isEmpty()) {
                 findCategories(convertToStd(InnerTubeClient.apiService.browse(BrowseRequest(context = InnerTubeClient.createWebContext(), browseId = "FEmusic_moods_and_genres"), InnerTubeClient.USER_AGENT_WEB).contents), categories)
            }
            categories.distinctBy { it.title }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getLyrics(trackId: String): String? {
        return null
    }
}
