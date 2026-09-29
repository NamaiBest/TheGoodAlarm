package com.namai.goodalarm.music

import android.service.notification.NotificationListenerService

/**
 * Exists only so the user can grant notification access, which lets the app find
 * Apple Music's media session when a direct connection isn't allowed.
 */
class MediaListenerService : NotificationListenerService()
