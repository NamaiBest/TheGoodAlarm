package com.namai.goodalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.namai.goodalarm.R
import com.namai.goodalarm.RingActivity
import com.namai.goodalarm.data.Alarm
import com.namai.goodalarm.data.AlarmRepository
import com.namai.goodalarm.data.Settings
import com.namai.goodalarm.music.AppleMusic
import com.namai.goodalarm.music.AppleMusicController
import com.namai.goodalarm.music.TonePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

enum class SoundSource { Connecting, AppleMusic, Tone }

data class Ringing(val alarm: Alarm, val source: SoundSource, val isTest: Boolean)

object RingingState {
    internal val _current = MutableStateFlow<Ringing?>(null)
    val current: StateFlow<Ringing?> = _current.asStateFlow()
}

class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apple by lazy { AppleMusicController(this) }
    private val tone by lazy { TonePlayer(this) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val settings by lazy { Settings(this) }
    private var ringJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var savedMusicVolume: Int? = null
    private var ringing: Ringing? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val alarm = intent.getStringExtra(EXTRA_ALARM_JSON)?.let { Alarm.fromJson(JSONObject(it)) }
                    ?: AlarmRepository.get(this, intent.getIntExtra(EXTRA_ID, -1))
                if (alarm == null) {
                    stopSelf()
                } else {
                    start(alarm, isTest = intent.hasExtra(EXTRA_ALARM_JSON))
                }
            }
            ACTION_SNOOZE, ACTION_DISMISS -> {
                if (ringing == null) {
                    // Started just to deliver the action (e.g. after a restart): must still go
                    // foreground briefly before stopping, or Android kills the app.
                    ensureChannels(this)
                    ServiceCompat.startForeground(
                        this, NOTIFICATION_ID,
                        NotificationCompat.Builder(this, CHANNEL_INFO).setSmallIcon(R.drawable.ic_stat_alarm).build(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                    )
                }
                finish(snooze = intent.action == ACTION_SNOOZE)
            }
            else -> if (ringing == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(alarm: Alarm, isTest: Boolean) {
        val state = Ringing(alarm, SoundSource.Connecting, isTest)
        ringing = state
        RingingState._current.value = state
        ensureChannels(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, ringNotification(alarm),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GoodAlarm:ring")
            .apply { acquire(settings.ringMinutes * 60_000L + 60_000L) }
        try {
            // Works when the screen is off or the app is in front; otherwise the full-screen
            // notification takes over.
            startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
        if (alarm.vibrate) startVibration()

        ringJob?.cancel()
        ringJob = scope.launch {
            stopSound()
            val song = alarm.song
            var viaApple = false
            if (song != null && AppleMusic.isInstalled(this@AlarmService)) {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                savedMusicVolume = savedMusicVolume ?: audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                setMusicVolume(if (settings.fadeIn) 0.15f else 1f, max)
                viaApple = apple.play(song)
            }
            if (!viaApple) {
                Log.i("GoodAlarm", "Using fallback tone")
                tone.playAlarm(song?.previewUrl)
            }
            update(if (viaApple) SoundSource.AppleMusic else SoundSource.Tone)

            if (viaApple) {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (settings.fadeIn) {
                    for (step in 1..20) {
                        delay(1_500)
                        setMusicVolume(0.15f + 0.85f * step / 20f, max)
                    }
                }
                // Watchdog: if Apple Music stops (e.g. paused from a headset), keep the alarm going.
                var misses = 0
                while (true) {
                    delay(3_000)
                    misses = if (apple.isPlaying()) 0 else misses + 1
                    if (misses == 2) {
                        Log.w("GoodAlarm", "Apple Music stopped; switching to fallback tone")
                        tone.playAlarm(song?.previewUrl)
                        update(SoundSource.Tone)
                        break
                    }
                }
            }
        }
        scope.launch {
            delay(settings.ringMinutes * 60_000L)
            if (ringing === state || ringing?.alarm?.id == alarm.id) finish(snooze = alarm.snoozeEnabled)
        }
    }

    private fun update(source: SoundSource) {
        ringing = ringing?.copy(source = source)
        RingingState._current.value = ringing
    }

    private fun setMusicVolume(fraction: Float, max: Int) {
        val target = (max * settings.volume * fraction).toInt().coerceIn(1, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    private fun finish(snooze: Boolean) {
        val r = ringing
        Log.i("GoodAlarm", "Finishing alarm (snooze=$snooze, ringing=${r != null})")
        ringing = null
        RingingState._current.value = null
        ringJob?.cancel()
        ringJob = null
        // Silence our own sound immediately; Apple Music is paused below.
        tone.stop()
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        if (r != null && snooze && r.alarm.snoozeEnabled && !r.isTest) {
            val at = AlarmScheduler.scheduleSnooze(this, r.alarm)
            postSnoozeNotification(r.alarm, at)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        scope.launch {
            try {
                withContext(NonCancellable) { stopSound() }
            } catch (e: Exception) {
                Log.w("GoodAlarm", "Error while stopping sound", e)
            } finally {
                apple.release()
                wakeLock?.takeIf { it.isHeld }?.release()
                stopSelf()
            }
        }
    }

    private suspend fun stopSound() {
        tone.stop()
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        try {
            apple.stop()
        } catch (e: Exception) {
            Log.w("GoodAlarm", "Couldn't pause Apple Music", e)
        }
        savedMusicVolume?.let {
            // Give Apple Music a moment to pause before restoring the user's volume.
            delay(400)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
        }
        savedMusicVolume = null
    }

    private fun startVibration() {
        val vib = getSystemService(VibratorManager::class.java).defaultVibrator
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 1400), 0)
        @Suppress("DEPRECATION")
        vib.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }

    private fun ringNotification(alarm: Alarm): Notification {
        val full = PendingIntent.getActivity(
            this, 1, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val time = formatTime(this, alarm.hour, alarm.minute)
        val b = NotificationCompat.Builder(this, CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(alarm.label.ifBlank { "Alarm" } + " · " + time)
            .setContentText(alarm.song?.let { "${it.title} — ${it.artist}" } ?: "Wake up")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (alarm.snoozeEnabled) {
            b.addAction(0, "Snooze", servicePendingIntent(this, ACTION_SNOOZE, 2))
        }
        b.addAction(0, "Stop", servicePendingIntent(this, ACTION_DISMISS, 3))
        return b.build()
    }

    private fun postSnoozeNotification(alarm: Alarm, at: Long) {
        val cancel = PendingIntent.getBroadcast(
            this, alarm.id,
            Intent(this, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_CANCEL_SNOOZE)
                .putExtra(AlarmReceiver.EXTRA_ID, alarm.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Snoozed until " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at)))
            .setContentText(alarm.label.ifBlank { "Alarm" })
            .setOngoing(true)
            .setWhen(at).setUsesChronometer(true).setChronometerCountDown(true)
            .addAction(0, "Dismiss", cancel)
            .build()
        getSystemService(NotificationManager::class.java).notify(snoozeNotificationId(alarm.id), n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.namai.goodalarm.START"
        const val ACTION_SNOOZE = "com.namai.goodalarm.SNOOZE"
        const val ACTION_DISMISS = "com.namai.goodalarm.DISMISS"
        private const val EXTRA_ID = "id"
        private const val EXTRA_ALARM_JSON = "alarm_json"
        private const val NOTIFICATION_ID = 42
        const val CHANNEL_RING = "ringing"
        const val CHANNEL_INFO = "status"

        fun snoozeNotificationId(alarmId: Int) = 10_000 + alarmId

        fun startIntent(context: Context, id: Int) =
            Intent(context, AlarmService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, id)

        /** Rings right now with an unsaved alarm, for trying out a song. */
        fun testIntent(context: Context, alarm: Alarm) =
            Intent(context, AlarmService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_ALARM_JSON, alarm.toJson().toString())

        fun action(context: Context, action: String) = Intent(context, AlarmService::class.java).setAction(action)

        private fun servicePendingIntent(context: Context, action: String, code: Int) = PendingIntent.getService(
            context, code, action(context, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_INFO, "Snoozed alarms", NotificationManager.IMPORTANCE_LOW),
            )
        }

        fun formatTime(context: Context, hour: Int, minute: Int): String {
            val is24 = android.text.format.DateFormat.is24HourFormat(context)
            return if (is24) "%d:%02d".format(hour, minute)
            else "%d:%02d %s".format(if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "AM" else "PM")
        }
    }
}
