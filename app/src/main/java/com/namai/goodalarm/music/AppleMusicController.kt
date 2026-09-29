package com.namai.goodalarm.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Bundle
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

    /** Set when we brought Apple Music to the front, so the caller can come back. */
    var openedAppleMusic = false
        private set

    enum class Result { ChosenSong, OtherMusic, Nothing }

    /**
     * Makes Apple Music play [song] at alarm time.
     *
     * Apple Music only lets you start a specific track from its own UI, which can't be used while
     * the phone is locked. So the song is normally loaded ahead of time by [prepare], and here we
     * just resume it. If it isn't loaded and the phone is unlocked and online, we load it now.
     * Otherwise we play whatever Apple Music has (downloads, when offline).
     */
    suspend fun play(song: Song, storefront: String): Result = withContext(Dispatchers.Main) {
        val c = connect()
        if (c == null) {
            Log.w(TAG, "No Apple Music media session available")
            return@withContext Result.Nothing
        }
        if (!matches(c, song) && !isLocked() && isOnline()) startSong(c, song, storefront)
        if (matches(c, song)) {
            if (!isPlaying(c)) c.transportControls.play()
            if (waitFor(8_000) { isPlaying(c) }) {
                Log.i(TAG, "Playing the alarm song")
                loopCurrentTrack(c)
                return@withContext Result.ChosenSong
            }
        }
        Log.i(TAG, "Alarm song not available (now: ${nowPlayingTitle()}); playing what Apple Music has")
        if (playAnything(c)) Result.OtherMusic else Result.Nothing
    }

    /**
     * Loads [song] as Apple Music's current track (queue cleared, paused) so a locked-phone alarm
     * can simply resume it. Muted while it briefly starts. Skipped if you're listening to
     * something. Returns true if the song is loaded.
     */
    suspend fun prepare(song: Song, storefront: String): Boolean = withContext(Dispatchers.Main) {
        val c = connect() ?: return@withContext false
        try {
            if (matches(c, song)) return@withContext true
            if (isPlaying(c)) {
                Log.i(TAG, "Not preparing: Apple Music is playing '${nowPlayingTitle()}'")
                return@withContext false
            }
            if (!isOnline() || !AppleMusicHelper.isEnabled(context)) return@withContext false
            val audio = context.getSystemService(android.media.AudioManager::class.java)
            val volume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
            try {
                val ok = startSong(c, song, storefront)
                c.transportControls.pause()
                waitFor(3_000) { !isPlaying(c) }
                delay(300)
                Log.i(TAG, "Prepare '${song.title}': $ok")
                ok
            } finally {
                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, volume, 0)
            }
        } finally {
            release()
        }
    }

    /** Whether Apple Music's current track is [song]. */
    suspend fun isPrepared(song: Song): Boolean = withContext(Dispatchers.Main) {
        val c = connect() ?: return@withContext false
        matches(c, song).also { release() }
    }

    /** Opens the song page in Apple Music and lets [AppleMusicHelper] tap it. */
    private suspend fun startSong(c: MediaControllerCompat, song: Song, storefront: String): Boolean {
        AppleMusicHelper.arm(song.title, 30_000)
        try {
            openSongLink(song, storefront)
            openedAppleMusic = true
            var polls = 0
            val ok = waitFor(25_000) {
                if (++polls % 12 == 0) AppleMusicHelper.poke()
                matches(c, song) && isPlaying(c)
            }
            Log.i(TAG, "Start '${song.title}': ${if (ok) "playing" else "failed"} (now: ${nowPlayingTitle()})")
            return ok
        } finally {
            AppleMusicHelper.disarm()
        }
    }

    private fun isLocked() = context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked

    /**
     * Resume Apple Music's queue; offline, non-downloaded tracks can't play, so skip ahead until
     * one that's downloaded starts.
     */
    private suspend fun playAnything(c: MediaControllerCompat): Boolean {
        c.transportControls.play()
        if (waitFor(5_000) { isPlaying(c) }) return true
        repeat(10) {
            c.transportControls.skipToNext()
            c.transportControls.play()
            if (waitFor(3_000) { isPlaying(c) }) {
                Log.i(TAG, "Playing '${nowPlayingTitle()}' from the queue")
                return true
            }
        }
        return false
    }

    private fun openSongLink(song: Song, storefront: String) {
        val uri = Uri.parse("https://music.apple.com/$storefront/song/${song.id}")
        Log.i(TAG, "Opening $uri in Apple Music")
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).setPackage(AppleMusic.PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
        if (keepingAlive) {
            runCatching { context.applicationContext.unbindService(keepAlive) }
            keepingAlive = false
        }
        browser?.disconnect()
        browser = null
        controller = null
    }

    private fun loopCurrentTrack(c: MediaControllerCompat) {
        if (previousRepeatMode == null) previousRepeatMode = c.repeatMode
        c.transportControls.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)
    }

    /**
     * One UI freezes cached background apps, and a frozen Apple Music ignores play commands.
     * Staying bound to its playback service from our foreground service keeps it awake.
     */
    private val keepAlive = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }
    private var keepingAlive = false

    private fun holdAppleMusicAwake() {
        if (keepingAlive) return
        val svc = context.packageManager.queryIntentServices(
            Intent("android.media.browse.MediaBrowserService").setPackage(AppleMusic.PACKAGE), 0,
        ).firstOrNull() ?: return
        val intent = Intent("android.media.browse.MediaBrowserService")
            .setComponent(ComponentName(svc.serviceInfo.packageName, svc.serviceInfo.name))
        keepingAlive = try {
            context.applicationContext.bindService(intent, keepAlive, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        } catch (e: Exception) {
            Log.w(TAG, "Keep-alive bind failed", e)
            false
        }
        Log.i(TAG, "Keep-alive bind: $keepingAlive")
    }

    private suspend fun connect(): MediaControllerCompat? {
        holdAppleMusicAwake()
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
