package com.namai.goodalarm.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

/**
 * Plays audio directly: the fallback alarm sound (song preview or system alarm tone) when
 * Apple Music can't be reached, and 30-second previews in the song picker.
 */
class TonePlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun playAlarm(previewUrl: String?) {
        val uri = previewUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        start(uri ?: defaultAlarmUri(), AudioAttributes.USAGE_ALARM, loop = true) {
            // Preview couldn't load (offline?) – use the built-in alarm sound.
            if (uri != null) start(defaultAlarmUri(), AudioAttributes.USAGE_ALARM, loop = true, onError = null)
        }
    }

    fun playPreview(url: String, onDone: () -> Unit) {
        start(Uri.parse(url), AudioAttributes.USAGE_MEDIA, loop = false, onError = { onDone() }, onComplete = onDone)
    }

    fun stop() {
        val p = player ?: return
        player = null
        p.setOnPreparedListener(null)
        p.setOnErrorListener(null)
        p.setOnCompletionListener(null)
        runCatching { p.stop() }
        p.release()
    }

    private fun start(
        uri: Uri,
        usage: Int,
        loop: Boolean,
        onComplete: (() -> Unit)? = null,
        onError: (() -> Unit)?,
    ) {
        stop()
        val attrs = AudioAttributes.Builder().setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val mp = MediaPlayer()
        player = mp
        mp.apply {
            setAudioAttributes(attrs)
            isLooping = loop
            // Ignore callbacks from a player that has since been stopped/replaced.
            setOnPreparedListener { if (player === mp) it.start() }
            setOnCompletionListener { if (player === mp) onComplete?.invoke() }
            setOnErrorListener { _, _, _ -> if (player === mp) onError?.invoke(); true }
            try {
                setDataSource(context, uri)
                prepareAsync()
            } catch (e: Exception) {
                onError?.invoke()
            }
        }
    }

    private fun defaultAlarmUri(): Uri =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
}
