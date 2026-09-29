package com.namai.goodalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.namai.goodalarm.MainActivity
import com.namai.goodalarm.data.Alarm
import com.namai.goodalarm.data.AlarmRepository
import java.util.Calendar

object AlarmScheduler {
    private const val SNOOZE_OFFSET = 1_000_000

    fun nextTrigger(alarm: Alarm, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (i in 0..7) {
            if (cal.timeInMillis > now && (alarm.days.isEmpty() || cal.get(Calendar.DAY_OF_WEEK) in alarm.days)) {
                return cal.timeInMillis
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun schedule(context: Context, alarm: Alarm) {
        cancel(context, alarm.id)
        if (alarm.enabled) setAlarmClock(context, nextTrigger(alarm), alarm.id, snooze = false)
    }

    fun scheduleSnooze(context: Context, alarm: Alarm): Long {
        val at = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        setAlarmClock(context, at, alarm.id, snooze = true)
        SnoozeStore.set(context, alarm.id, at)
        return at
    }

    fun cancelSnooze(context: Context, id: Int) {
        am(context).cancel(firePendingIntent(context, id, snooze = true))
        SnoozeStore.clear(context, id)
    }

    fun cancel(context: Context, id: Int) {
        am(context).cancel(firePendingIntent(context, id, snooze = false))
        cancelSnooze(context, id)
    }

    fun rescheduleAll(context: Context) {
        AlarmRepository.load(context).forEach { schedule(context, it) }
        // Restore pending snoozes that survived a reboot.
        SnoozeStore.all(context).forEach { (id, at) ->
            if (at > System.currentTimeMillis()) setAlarmClock(context, at, id, snooze = true)
            else SnoozeStore.clear(context, id)
        }
    }

    private fun setAlarmClock(context: Context, at: Long, id: Int, snooze: Boolean) {
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am(context).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), firePendingIntent(context, id, snooze))
    }

    private fun firePendingIntent(context: Context, id: Int, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ID, id)
            putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze)
        }
        return PendingIntent.getBroadcast(
            context, if (snooze) id + SNOOZE_OFFSET else id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun am(context: Context) = context.getSystemService(AlarmManager::class.java)
}

/** Persists snooze deadlines so they survive reboots and can be shown in the UI. */
object SnoozeStore {
    private fun prefs(c: Context) = c.getSharedPreferences("snoozes", Context.MODE_PRIVATE)
    fun set(c: Context, id: Int, at: Long) = prefs(c).edit().putLong(id.toString(), at).apply()
    fun clear(c: Context, id: Int) = prefs(c).edit().remove(id.toString()).apply()
    fun get(c: Context, id: Int): Long? = prefs(c).getLong(id.toString(), 0L).takeIf { it > System.currentTimeMillis() }
    fun all(c: Context): Map<Int, Long> = prefs(c).all.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to (v as Long) } }.toMap()
}
