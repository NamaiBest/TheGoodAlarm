package com.namai.goodalarm.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.namai.goodalarm.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "GoodAlarm"

object AppleMusic {
    const val PACKAGE = "com.apple.android.music"

    fun isInstalled(context: Context) = try {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    } catch (_: Exception) {
        false
    }

    /** Whether the user granted notification access, which lets us find Apple Music's media session. */
    fun hasSessionAccess(context: Context): Boolean {
        val flat = AndroidSettings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(ComponentName(context, MediaListenerService::class.java).flattenToString())
    }
}

/**
 * Drives the Apple Music app through its media session: we ask it to play a specific track,
 * then pause it again when the alarm is dismissed. Apple Music keeps its own login and DRM,
 * so nothing is streamed through this app.
 */
class AppleMusicController(private val context: Context) {
    private var browser: MediaBrowserCompat? = null
    private var controller: MediaControllerCompat? = null
    private var previousRepeatMode: Int? = null
    private val prefs = context.getSharedPreferences("apple_music", Context.MODE_PRIVATE)

    /** Asks Apple Music to play [song]. Returns true once Apple Music reports it is playing. */
    suspend fun play(song: Song): Boolean = withContext(Dispatchers.Main) {
        val c = connect()
        if (c == null) {
            Log.w(TAG, "No Apple Music media session available")
            return@withContext false
        }
        val tc = c.transportControls
        val query = "${song.title} ${song.artist}"
        val extras = Bundle().apply {
            putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            putString(MediaStore.EXTRA_MEDIA_TITLE, song.title)
            putString(MediaStore.EXTRA_MEDIA_ARTIST, song.artist)
            putString(MediaStore.EXTRA_MEDIA_ALBUM, song.album)
        }
        val strategies = linkedMapOf<String, () -> Unit>(
            "uri" to { tc.playFromUri(Uri.parse(song.url), Bundle()) },
            "search" to { tc.playFromSearch(query, extras) },
            "mediaId" to { tc.playFromMediaId(song.id.toString(), Bundle()) },
        )
        // Try whatever worked last time first.
        val preferred = prefs.getString("strategy", null)
        val order = strategies.keys.sortedByDescending { it == preferred }

        for (name in order) {
            Log.i(TAG, "Trying Apple Music strategy '$name' for $query")
            strategies.getValue(name)()
            if (waitFor(8_000) { isPlaying(c) && matches(c, song) }) {
                Log.i(TAG, "Apple Music playing via '$name'")
                prefs.edit().putString("strategy", name).apply()
                loopCurrentTrack(c)
                return@withContext true
            }
        }
        // Something is playing even if the title didn't match exactly (e.g. a localized title).
        if (isPlaying(c)) {
            loopCurrentTrack(c)
            return@withContext true
        }
        false
    }

    suspend fun isPlaying(): Boolean = withContext(Dispatchers.Main) { controller?.let(::isPlaying) == true }

    fun nowPlayingTitle(): String? = controller?.metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)

    /** Pauses playback and puts Apple Music's repeat setting back the way it was. */
    suspend fun stop() = withContext(Dispatchers.Main) {
        controller?.let { c ->
            c.transportControls.pause()
            previousRepeatMode?.let { c.transportControls.setRepeatMode(it) }
        }
        previousRepeatMode = null
    }

    fun release() {
        browser?.disconnect()
        browser = null
        controller = null
    }

    private fun loopCurrentTrack(c: MediaControllerCompat) {
        if (previousRepeatMode == null) previousRepeatMode = c.repeatMode
        c.transportControls.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)
    }

    private suspend fun connect(): MediaControllerCompat? {
        controller?.let { return it }
        // Apple Music usually refuses browser clients other than Android Auto, but the bind
        // still starts its playback service, which publishes the session we then look up.
        val c = findSession(wait = false) ?: viaBrowser() ?: findSession(wait = true)
        controller = c
        return c
    }

    /** Binds to Apple Music's MediaBrowserService (the interface Android Auto uses). */
    private suspend fun viaBrowser(): MediaControllerCompat? {
        val services = context.packageManager.queryIntentServices(
            Intent("android.media.browse.MediaBrowserService").setPackage(AppleMusic.PACKAGE), 0,
        )
        for (svc in services) {
            val cn = ComponentName(svc.serviceInfo.packageName, svc.serviceInfo.name)
            val c = withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    var b: MediaBrowserCompat? = null
                    b = MediaBrowserCompat(context, cn, object : MediaBrowserCompat.ConnectionCallback() {
                        override fun onConnected() {
                            if (cont.isActive) cont.resume(MediaControllerCompat(context, b!!.sessionToken))
                        }

                        override fun onConnectionFailed() {
                            if (cont.isActive) cont.resume(null)
                        }
                    }, null)
                    browser = b
                    cont.invokeOnCancellation { b.disconnect() }
                    b.connect()
                }
            }
            Log.i(TAG, "MediaBrowser ${cn.className}: ${if (c != null) "connected" else "failed"}")
            if (c != null) return c
            browser?.disconnect()
            browser = null
        }
        return null
    }

    /** Finds Apple Music's active session; needs notification access. */
    private suspend fun findSession(wait: Boolean): MediaControllerCompat? {
        if (!AppleMusic.hasSessionAccess(context)) return null
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(context, MediaListenerService::class.java)
        fun find() = try {
            msm.getActiveSessions(listener).firstOrNull { it.packageName == AppleMusic.PACKAGE }
        } catch (e: SecurityException) {
            null
        }
        val session = if (wait) waitForValue(8_000) { find() } else find()
        Log.i(TAG, "Session lookup (wait=$wait): ${if (session != null) "found" else "not found"}")
        return session?.let { MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(it.sessionToken)) }
    }

    private fun isPlaying(c: MediaControllerCompat) = c.playbackState?.state == PlaybackStateCompat.STATE_PLAYING

    private fun matches(c: MediaControllerCompat, song: Song): Boolean {
        val title = c.metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: return false
        val a = normalize(title)
        val b = normalize(song.title)
        return a.isNotEmpty() && (a.contains(b) || b.contains(a))
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")

    private suspend fun waitFor(timeoutMs: Long, check: () -> Boolean): Boolean =
        waitForValue(timeoutMs) { check().takeIf { it } } == true

    private suspend fun <T> waitForValue(timeoutMs: Long, get: () -> T?): T? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            get()?.let { return it }
            delay(250)
        }
        return null
    }
}
