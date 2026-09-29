package com.namai.goodalarm.data

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/** App-wide preferences. */
class Settings(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Target media volume while an alarm rings, 0..1. */
    var volume: Float
        get() = prefs.getFloat("volume", 0.6f)
        set(v) = prefs.edit().putFloat("volume", v).apply()

    /** Ramp the volume up over ~30 seconds instead of starting at full volume. */
    var fadeIn: Boolean
        get() = prefs.getBoolean("fadeIn", true)
        set(v) = prefs.edit().putBoolean("fadeIn", v).apply()

    /** Apple Music storefront used for search, e.g. "us" or "in". */
    var storefront: String
        get() = prefs.getString("storefront", null) ?: defaultStorefront()
        set(v) = prefs.edit().putString("storefront", v.lowercase()).apply()

    /** The SIM's country is the best guess for the Apple Music account's region. */
    private fun defaultStorefront(): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return listOf(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { !it.isNullOrBlank() }?.lowercase() ?: "us"
    }

    /** Minutes an alarm rings before it snoozes itself (or stops, if snooze is off). */
    var ringMinutes: Int
        get() = prefs.getInt("ringMinutes", 10)
        set(v) = prefs.edit().putInt("ringMinutes", v).apply()
}
