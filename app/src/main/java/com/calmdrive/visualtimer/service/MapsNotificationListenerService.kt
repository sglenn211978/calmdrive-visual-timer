package com.calmdrive.visualtimer.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.calmdrive.visualtimer.data.PreferencesRepository

class MapsNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val MAPS_PACKAGE = "com.google.android.apps.maps"
    }

    private lateinit var prefs: PreferencesRepository

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesRepository(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != MAPS_PACKAGE) return
        if (!prefs.mapsSyncEnabled) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val combined = "$title $text $subText"
        val remainingSec = parseRemainingSeconds(combined)
        if (remainingSec > 0) {
            CalmDriveMediaService.instance?.timerEngine?.updateMapsEta(remainingSec)
        }
    }

    private fun parseRemainingSeconds(text: String): Int {
        // Look for patterns like "15 min", "1 hr 12 min", "1 hr", "45 mins"
        var totalSec = 0
        val hrRegex = Regex("""(\d+)\s*(?:hr|hour|hours)""", RegexOption.IGNORE_CASE)
        val minRegex = Regex("""(\d+)\s*(?:min|mins|minute|minutes)""", RegexOption.IGNORE_CASE)

        hrRegex.find(text)?.let { match ->
            val hrs = match.groupValues[1].toIntOrNull() ?: 0
            totalSec += hrs * 3600
        }

        minRegex.find(text)?.let { match ->
            val mins = match.groupValues[1].toIntOrNull() ?: 0
            totalSec += mins * 60
        }

        return totalSec
    }
}
