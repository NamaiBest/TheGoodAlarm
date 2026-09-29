package com.namai.goodalarm.music

import com.namai.goodalarm.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Searches the Apple Music catalog through Apple's public iTunes Search API (no login needed). */
object AppleMusicSearch {
    suspend fun search(term: String, storefront: String): List<Song> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(term.trim(), "UTF-8")
        val url = URL("https://itunes.apple.com/search?term=$q&media=music&entity=song&limit=40&country=$storefront")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).getJSONArray("results")
            (0 until results.length()).map { results.getJSONObject(it) }
                .filter { it.optString("kind") == "song" }
                .map {
                    Song(
                        id = it.optLong("trackId"),
                        title = it.optString("trackName"),
                        artist = it.optString("artistName"),
                        album = it.optString("collectionName"),
                        artworkUrl = it.optString("artworkUrl100"),
                        url = it.optString("trackViewUrl").substringBefore("&uo="),
                        previewUrl = it.optString("previewUrl"),
                    )
                }
        } finally {
            conn.disconnect()
        }
    }
}
