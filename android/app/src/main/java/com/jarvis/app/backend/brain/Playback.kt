package com.jarvis.app.backend.brain

import org.json.JSONObject
import java.net.URLEncoder

/** §11 zero-API media router: pure routing logic. Tested. */

const val MEDIA_PLATFORM_YOUTUBE = "youtube"
const val MEDIA_PLATFORM_MUSIC = "music"
const val MEDIA_PREF_SYSTEM = "system"

const val PKG_SPOTIFY = "com.spotify.music"
const val PKG_YOUTUBE = "com.google.android.youtube"
const val PKG_YTMUSIC = "com.google.android.apps.youtube.music"

val MUSIC_CANDIDATES = listOf(PKG_SPOTIFY, PKG_YTMUSIC)
val YOUTUBE_CANDIDATES = listOf(PKG_YOUTUBE)

/** Parsed play_media command: normalized platform + search query. */
data class PlayMedia(val platform: String, val query: String)

/** Pending app choice: blank query + manage = Settings repick (saves only). */
data class MediaPick(val query: String, val platform: String, val manage: Boolean = false)

/**
 * Parse an LLM reply for a play_media command. Tolerates prose around the
 * JSON. Unknown/missing platform falls back to music. Null = plain chat.
 */
fun parsePlayMedia(reply: String): PlayMedia? {
    return try {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = JSONObject(reply.substring(start, end + 1))
        if (o.optString("action") != "play_media") return null
        val raw = o.optString("platform").lowercase()
        val platform = if ("tube" in raw || raw == "video") MEDIA_PLATFORM_YOUTUBE else MEDIA_PLATFORM_MUSIC
        val query = o.optString("query").trim()
        if (query.isEmpty()) return null
        PlayMedia(platform, query)
    } catch (_: Exception) {
        null
    }
}

/** Spoken handoff line per platform. */
fun mediaAck(platform: String): String =
    if (platform == MEDIA_PLATFORM_YOUTUBE) "Routing to YouTube." else "Loading track, sir."

/** Human label for a package (or the system default). */
fun mediaAppLabel(pkg: String): String = when (pkg) {
    PKG_SPOTIFY -> "Spotify"
    PKG_YOUTUBE -> "YouTube"
    PKG_YTMUSIC -> "YouTube Music"
    MEDIA_PREF_SYSTEM -> "System default"
    else -> "Ask every time"
}

/**
 * Resolve the playback target: remembered pref when still installed,
 * "system" when chosen, null when the user must be asked.
 */
fun pickMediaPackage(platform: String, pref: String, installed: Set<String>): String? {
    if (pref == MEDIA_PREF_SYSTEM) return MEDIA_PREF_SYSTEM
    if (pref.isNotBlank()) {
        if (pref in installed) return pref
        return null // remembered app gone — ask again
    }
    val cands = if (platform == MEDIA_PLATFORM_YOUTUBE) YOUTUBE_CANDIDATES else MUSIC_CANDIDATES
    val have = cands.filter { it in installed }
    // Blank pref: honor "ask the user" unless exactly one candidate exists
    // and the platform is YouTube (single sensible target).
    if (platform == MEDIA_PLATFORM_YOUTUBE && have.size == 1) return have[0]
    return null
}

/** Installed candidates worth offering for [platform]. */
fun mediaChoices(platform: String, installed: Set<String>): List<String> {
    val cands = if (platform == MEDIA_PLATFORM_YOUTUBE) YOUTUBE_CANDIDATES else MUSIC_CANDIDATES
    return cands.filter { it in installed }
}

/** No-key YouTube fallback: web search URL. */
fun youtubeSearchUrl(query: String): String =
    "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
