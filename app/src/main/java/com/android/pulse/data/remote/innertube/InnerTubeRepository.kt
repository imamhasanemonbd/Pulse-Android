package com.android.pulse.data.remote.innertube

import android.util.Log
import com.android.pulse.data.model.Track
import com.android.pulse.data.remote.innertube.model.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

/**
 * Singleton repository with strict Music-Only filtering logic and robust discovery.
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
            val request = SearchRequest(context = InnerTubeClient.createWebContext(), query = query, params = params)
            val response = InnerTubeClient.apiService.search(request, InnerTubeClient.USER_AGENT_WEB)
            response.responseContext?.visitorData?.let { InnerTubeClient.setVisitorData(it) }
            
            val tracks = mutableListOf<Track>()
            parseGreedy(response, tracks)
            
            // --- METADATA SELF-HEALING ---
            for (i in tracks.indices) {
                if (tracks[i].artist == "Unknown Artist") {
                    val cleanTitle = tracks[i].title.lowercase()
                        .substringBefore(" (")
                        .substringBefore(" |")
                        .trim()
                    
                    val correctArtist = tracks.find { 
                        val otherTitle = it.title.lowercase()
                        otherTitle.contains(cleanTitle) && it.artist != "Unknown Artist" 
                    }?.artist
                    
                    if (correctArtist != null) {
                        tracks[i] = tracks[i].copy(artist = correctArtist)
                        Log.d(TAG, "HEALED: Set artist '$correctArtist' for '${tracks[i].title}'")
                    }
                }
            }
            
            Log.e(TAG, "MUSIC-FILTER: found ${tracks.size} tracks")
            tracks.distinctBy { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "Search fatal", e)
            emptyList()
        }
    }

    suspend fun getTrending(): List<Track> {
        val results = mutableListOf<Track>()
        results.addAll(searchMusic("Trending Music"))
        if (results.size < 20) results.addAll(searchMusic("Top Hits 2025"))
        if (results.size < 20) results.addAll(searchMusic("New Music"))
        return results.distinctBy { it.id }
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
            
            if (categoryParams != null && allTracks.size < 15) {
                val catName = categories.find { it.params == categoryParams }?.title 
                            ?: categoryParams.substringAfter("ggMPOg").take(5)
                Log.d(TAG, "MUSIC-FILTER: Low category results, supplementing with search for '$catName'")
                allTracks.addAll(searchMusic("$catName Music"))
            }

            if (allTracks.size < 10) {
                Log.w(TAG, "MUSIC-FILTER: Results still low (${allTracks.size}), merging with trending")
                allTracks.addAll(getTrending())
            }
            
            if (categories.isEmpty()) {
                categories.addAll(listOf(
                    Category("Energize", "FEmusic_home", "ggMPOg1uX2V4Y2x1c2l2ZV9y"),
                    Category("Relax", "FEmusic_home", "ggMPOg1vX2V4Y2x1c2l2ZV9y"),
                    Category("Feel good", "FEmusic_home", "ggMPOg1wX2V4Y2x1c2l2ZV9y"),
                    Category("Party", "FEmusic_home", "ggMPOg1yX2V4Y2x1c2l2ZV9y"),
                    Category("Romance", "FEmusic_home", "ggMPOg16X2V4Y2x1c2l2ZV9y"),
                    Category("Workout", "FEmusic_home", "ggMPOg1xX2V4Y2x1c2l2ZV9y")
                ))
            }
            
            val distinct = allTracks.filter { it.id != relatedToVideoId }.distinctBy { it.id }
            val speedDial = distinct.take(12)
            val quickPicks = distinct.drop(12).ifEmpty { distinct.shuffled() }
            
            HomeData(quickPicks, speedDial, categories.distinctBy { it.title })
        } catch (e: Exception) {
            Log.e(TAG, "Home fatal", e)
            val trending = getTrending()
            HomeData(trending, trending.shuffled(), emptyList())
        }
    }

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
                        }
                    }
                }
            }
            node.values.forEach { deepScan(it, out) }
        }
    }

    private fun isMusicMedia(node: Map<*, *>): Boolean {
        if (node.containsKey("musicResponsiveListItemRenderer")) return true
        if (node.containsKey("musicTwoRowItemRenderer")) return true
        if (node.containsKey("musicCardShelfRenderer")) return true
        
        val json = gson.toJson(node)
        val hasMusicType = json.contains("MUSIC_VIDEO_TYPE_ATV") || 
                           json.contains("MUSIC_VIDEO_TYPE_OMV") || 
                           json.contains("MUSIC_PAGE_TYPE_TRACK") ||
                           json.contains("MUSIC_PAGE_TYPE_ALBUM")
        
        if (hasMusicType) return true

        val title = (extractTitle(node) ?: "").lowercase()
        val artist = (extractArtist(node) ?: "").lowercase()
        val blacklist = listOf("telefilm", "vlog", "blog", "full movie", "episode", "drama", "documentary", "news")
        
        if (blacklist.any { title.contains(it) || artist.contains(it) }) return false

        val durationText = json.substringAfter("\"simpleText\":\"", "").substringBefore("\"", "")
        if (durationText.contains(":") && !durationText.contains("::")) {
            val parts = durationText.split(":")
            if (parts.size >= 3) return false 
            val mins = parts[0].toIntOrNull() ?: 0
            if (mins > 15 && !title.contains("mix") && !title.contains("phonk")) return false
        }

        return false
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
        val artistsList = mutableListOf<String>()

        fun addFromRuns(node: Any?) {
            val runs = when (node) {
                is Map<*, *> -> node["runs"] as? List<*>
                is List<*> -> node
                else -> null
            }
            runs?.forEach { run ->
                if (run is Map<*, *>) {
                    val text = run["text"]?.toString()
                    val nav = (run["navigationEndpoint"] as? Map<*, *>) ?: (run["serviceEndpoint"] as? Map<*, *>)
                    val browseId = (nav?.get("browseEndpoint") as? Map<*, *>)?.get("browseId")?.toString()
                    
                    if (text != null && browseId != null && (browseId.startsWith("UC") || browseId.contains("artist"))) {
                        if (!artistsList.contains(text)) artistsList.add(text)
                    }
                }
            }
        }

        // Path 1: Card Shelf Header (Best Match)
        (map["header"] as? Map<*, *>)?.let { h ->
            val cardHeader = h["musicCardShelfHeaderBasicRenderer"] as? Map<*, *>
            addFromRuns(cardHeader?.get("title"))
            addFromRuns(cardHeader?.get("subtitle"))
        }
        
        // Path 1.1: Web Port - Check specific byline objects
        addFromRuns(map["primaryByline"])
        addFromRuns(map["secondaryByline"])

        // Path 2: Flex Columns
        (map["flexColumns"] as? List<*>)?.let { flex ->
            if (flex.size > 1) {
                for (i in 1 until flex.size) {
                    val col = (flex[i] as? Map<*, *>)?.get("musicResponsiveListItemFlexColumnRenderer") as? Map<*, *>
                    addFromRuns(col?.get("text"))
                    if (artistsList.isNotEmpty()) return artistsList.joinToString(", ")
                    
                    val text = parseRuns(col?.get("text"))
                    if (text != null) {
                        val lower = text.lowercase()
                        if (lower.contains("views") || lower.contains("plays") || lower == "song" || lower == "video") continue
                        return text.substringBefore(" • ").trim()
                    }
                }
            }
        }

        addFromRuns(map["longBylineText"])
        addFromRuns(map["shortBylineText"])
        addFromRuns(map["subtitle"])
        addFromRuns(map["bylineText"])

        if (artistsList.isNotEmpty()) return artistsList.joinToString(", ")
        return (map["subtitle"] as? String)?.substringBefore(" • ")?.trim()
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

    suspend fun getLyrics(trackId: String, title: String? = null, artist: String? = null, durationMs: Long = 0): String? {
        // 1. HIGH PRIORITY: Try LRCLIB for Synced Lyrics (Musixmatch data)
        if (title != null && artist != null) {
            val durationSecs = (durationMs / 1000).toInt()
            val lrc = fetchFromLrcLib(title, artist, durationSecs)
            if (lrc != null) return lrc
        }

        // 2. FALLBACK: InnerTube (Official YouTube Music Source: Musixmatch/LyricFind)
        return try {
            val nextReq = NextRequest(context = InnerTubeClient.createWebContext(), videoId = trackId)
            val nextResp = InnerTubeClient.apiService.next(nextReq, InnerTubeClient.USER_AGENT_WEB)
            val root = convertToStd(nextResp.contents) as? Map<*, *>
            
            var lyricsBrowseId: String? = null
            
            // Comprehensive recursive scan for lyrics browseId or tab
            fun findLyricsId(node: Any?) {
                if (lyricsBrowseId != null || node == null) return
                if (node is List<*>) node.forEach { findLyricsId(it) }
                else if (node is Map<*, *>) {
                    // Method A: Check for explicit "Lyrics" tab label
                    val title = parseRuns(node["title"])?.lowercase()
                    if (title == "lyrics") {
                        val browseEndpoint = node["endpoint"] as? Map<*, *> ?: node["navigationEndpoint"] as? Map<*, *>
                        val id = (browseEndpoint?.get("browseEndpoint") as? Map<*, *>)?.get("browseId")?.toString()
                        if (id != null) {
                            lyricsBrowseId = id
                            return
                        }
                    }
                    
                    // Method B: Direct browseId check
                    val browseId = (node["browseEndpoint"] as? Map<*, *>)?.get("browseId")?.toString()
                    if (browseId?.startsWith("FEmusic_lyrics") == true) {
                        lyricsBrowseId = browseId
                        return
                    }
                    
                    node.values.forEach { findLyricsId(it) }
                }
            }
            findLyricsId(root)

            if (lyricsBrowseId != null) {
                Log.d(TAG, "InnerTube: Resolved lyricsBrowseId: $lyricsBrowseId")
                val lyricsReq = LyricsRequest(context = InnerTubeClient.createWebContext(), browseId = lyricsBrowseId!!)
                val lyricsResp = InnerTubeClient.apiService.getLyrics(lyricsReq, InnerTubeClient.USER_AGENT_WEB)
                val contents = convertToStd(lyricsResp.contents) as? Map<*, *>
                
                var lyricsText: String? = null
                fun extractText(node: Any?) {
                    if (lyricsText != null || node == null) return
                    if (node is List<*>) node.forEach { extractText(it) }
                    else if (node is Map<*, *>) {
                        // Official YT Music Lyrics Container
                        if (node.containsKey("musicDescriptionShelfRenderer")) {
                            val r = node["musicDescriptionShelfRenderer"] as? Map<*, *>
                            lyricsText = parseRuns(r?.get("description"))
                            val source = parseRuns(r?.get("footer")) ?: "YouTube Music"
                            Log.d(TAG, "InnerTube: Extracted lyrics from source: $source")
                            return
                        }
                        node.values.forEach { extractText(it) }
                    }
                }
                extractText(contents)
                lyricsText
            } else {
                Log.w(TAG, "InnerTube: Could not find lyrics tab/ID for $trackId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lyrics fetch failed", e)
            null
        }
    }

    private suspend fun fetchFromLrcLib(title: String, artist: String, durationSecs: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Pre-sanitize for LRCLIB
                val cleanTitle = title.substringBefore(" (").substringBefore(" |").trim()
                val cleanArtist = artist.substringBefore(",").trim()
                
                val url = "https://lrclib.net/api/get?artist_name=${URLEncoder.encode(cleanArtist, "UTF-8")}&track_name=${URLEncoder.encode(cleanTitle, "UTF-8")}&duration=$durationSecs"
                val request = Request.Builder().url(url).build()
                
                val response = InnerTubeClient.okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val map = gson.fromJson(body, Map::class.java)
                    
                    val synced = map["syncedLyrics"] as? String
                    if (!synced.isNullOrBlank()) {
                        Log.d(TAG, "LRCLIB: Found SYNCED lyrics for '$cleanTitle'")
                        return@withContext synced
                    }
                    
                    val plain = map["plainLyrics"] as? String
                    if (!plain.isNullOrBlank()) {
                        Log.d(TAG, "LRCLIB: Found PLAIN lyrics for '$cleanTitle'")
                        return@withContext plain
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "LRCLIB direct fetch failed, trying search...")
                // Fallback: Try LRCLIB search if direct get fails
                try {
                     val searchUrl = "https://lrclib.net/api/search?q=${URLEncoder.encode("$artist $title", "UTF-8")}"
                     val searchReq = Request.Builder().url(searchUrl).build()
                     val searchResp = InnerTubeClient.okHttpClient.newCall(searchReq).execute()
                     if (searchResp.isSuccessful) {
                         val results = gson.fromJson(searchResp.body?.string(), List::class.java)
                         if (results != null && results.isNotEmpty()) {
                             val first = results[0] as Map<*, *>
                             return@withContext (first["syncedLyrics"] ?: first["plainLyrics"]) as? String
                         }
                     }
                } catch (se: Exception) { /* search failed too */ }
            }
            null
        }
    }
}
