package com.namai.goodalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.namai.goodalarm.data.AlarmRepository

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val id = intent.getIntExtra(EXTRA_ID, -1)
                val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
                val alarm = AlarmRepository.get(context, id) ?: return
                if (snooze) {
                    SnoozeStore.clear(context, id)
                    context.getSystemService(android.app.NotificationManager::class.java)
                        .cancel(AlarmService.snoozeNotificationId(id))
                } else if (alarm.days.isEmpty()) {
                    AlarmRepository.upsert(context, alarm.copy(enabled = false))
                } else {
                    AlarmScheduler.schedule(context, alarm)
                }
                ContextCompat.startForegroundService(context, AlarmService.startIntent(context, id))
            }
            ACTION_CANCEL_SNOOZE -> {
                val id = intent.getIntExtra(EXTRA_ID, -1)
                AlarmScheduler.cancelSnooze(context, id)
                context.getSystemService(android.app.NotificationManager::class.java)
                    .cancel(AlarmService.snoozeNotificationId(id))
            }
            ACTION_DEBUG_STOP -> if (isDebuggable(context)) {
                context.startService(AlarmService.action(context, AlarmService.ACTION_DISMISS))
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> AlarmScheduler.rescheduleAll(context)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.namai.goodalarm.FIRE"
        const val ACTION_CANCEL_SNOOZE = "com.namai.goodalarm.CANCEL_SNOOZE"
        private const val ACTION_DEBUG_STOP = "com.namai.goodalarm.DEBUG_STOP"

        private fun isDebuggable(context: Context) =
            context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        const val EXTRA_ID = "id"
        const val EXTRA_SNOOZE = "snooze"
    }
}
