package com.namai.goodalarm.data

import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val url: String,
    val previewUrl: String,
) {
    /** Artwork at a larger size than the 100px the search API returns. */
    fun artwork(size: Int = 600): String = artworkUrl.replace(Regex("\\d+x\\d+bb"), "${size}x${size}bb")

    fun toJson() = JSONObject()
        .put("id", id).put("title", title).put("artist", artist).put("album", album)
        .put("artworkUrl", artworkUrl).put("url", url).put("previewUrl", previewUrl)

    companion object {
        fun fromJson(o: JSONObject) = Song(
            id = o.optLong("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            album = o.optString("album"),
            artworkUrl = o.optString("artworkUrl"),
            url = o.optString("url"),
            previewUrl = o.optString("previewUrl"),
        )
    }
}

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "",
    /** java.util.Calendar DAY_OF_WEEK values (1 = Sunday … 7 = Saturday). Empty = one-time. */
    val days: Set<Int> = emptySet(),
    val song: Song? = null,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 9,
    val vibrate: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("hour", hour).put("minute", minute).put("enabled", enabled)
        .put("label", label).put("days", JSONArray(days.sorted()))
        .put("song", song?.toJson() ?: JSONObject.NULL)
        .put("snoozeEnabled", snoozeEnabled).put("snoozeMinutes", snoozeMinutes)
        .put("vibrate", vibrate)

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val d = o.optJSONArray("days") ?: JSONArray()
            return Alarm(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                enabled = o.optBoolean("enabled", true),
                label = o.optString("label"),
                days = (0 until d.length()).map { d.getInt(it) }.toSet(),
                song = o.optJSONObject("song")?.let(Song::fromJson),
                snoozeEnabled = o.optBoolean("snoozeEnabled", true),
                snoozeMinutes = o.optInt("snoozeMinutes", 9),
                vibrate = o.optBoolean("vibrate", true),
            )
        }
    }
}
