package com.namai.goodalarm.music

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Apple Music has no public way to start a specific song, so while an alarm is starting this
 * does what you would do by hand: on the song's album page it taps the song, and if Apple Music
 * asks what to do with the existing queue it clears it. It only listens to Apple Music
 * (see res/xml/apple_music_helper.xml) and does nothing outside that short window.
 */
class AppleMusicHelper : AccessibilityService() {
    private var lastTap = 0L
    private var lastScroll = 0L

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = scan()

    /** Looks at Apple Music's screen and takes the next step, if armed. */
    fun scan() {
        val title = target ?: return
        if (System.currentTimeMillis() > armedUntil) return
        val root = rootInActiveWindow ?: return
        if (root.packageName != AppleMusic.PACKAGE) return

        // 1. "After this plays, keep or clear your queue?" – clear it so the alarm song
        //    plays now instead of the old queue carrying on.
        val clear = root.findAccessibilityNodeInfosByText("Clear")
            .firstOrNull { it.text?.toString()?.equals("Clear", ignoreCase = true) == true }
        if (clear != null) {
            tap(clear)
            Log.i(TAG, "Helper: cleared Apple Music's queue")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastTap < 4_000) return

        // 2. Tap the song's row. Its title may also appear in the header (for singles), so
        //    prefer the last match, which is the track list entry.
        val row = root.findAccessibilityNodeInfosByText(title)
            .filter { normalize(it.text?.toString()) == normalize(title) }
            .lastOrNull()
        if (row != null) {
            val r = Rect().also { row.getBoundsInScreen(it) }
            if (r.top < resources.displayMetrics.heightPixels / 5) {
                // Tucked under Apple Music's top bar: scroll it into the open first.
                findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                return
            }
            tap(row)
            lastTap = now
            Log.i(TAG, "Helper: tapped '$title'")
            return
        }

        // 3. Not on screen yet: scroll the track list.
        if (now - lastScroll > 700) {
            findScrollable(root)?.let {
                it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                lastScroll = now
            }
        }
    }

    override fun onInterrupt() {}

    /** A real touch: Apple Music ignores accessibility click actions on its track rows. */
    private fun tap(node: AccessibilityNodeInfo) {
        val r = Rect()
        node.getBoundsInScreen(r)
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(),
            null, null,
        )
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findScrollable(it) }?.let { return it }
        }
        return null
    }

    companion object {
        private const val TAG = "GoodAlarm"

        @Volatile
        private var armedUntil = 0L

        @Volatile
        private var target: String? = null

        @Volatile
        private var instance: AppleMusicHelper? = null

        /** Re-check the screen even if nothing changed (e.g. a tap that didn't register). */
        fun poke() {
            instance?.scan()
        }

        /** Start helping Apple Music play the song titled [title] for the next [ms] milliseconds. */
        fun arm(title: String, ms: Long) {
            target = title
            armedUntil = System.currentTimeMillis() + ms
        }

        fun disarm() {
            target = null
            armedUntil = 0L
        }

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return flat.split(':').any {
                ComponentName.unflattenFromString(it) == ComponentName(context, AppleMusicHelper::class.java)
            }
        }

        private fun normalize(s: String?) = s.orEmpty().lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
    }
}
