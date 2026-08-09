package dev.orsetto.shaketime

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Tracks how many notifications are currently in the shade and stores the count
 * (clamped to [Prefs.MAX_NOTIF_COUNT]) for the Glyph indicator to read.
 *
 * Requires the user to grant "Notification access" in system settings. Ongoing
 * notifications (including this app's own foreground-service notification) and
 * group summaries are excluded so the count reflects real, dismissable items.
 */
class NotificationCountService : NotificationListenerService() {

    private val prefs by lazy { Prefs(this) }

    override fun onListenerConnected() = updateCount()

    override fun onNotificationPosted(sbn: StatusBarNotification?) = updateCount()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = updateCount()

    private fun updateCount() {
        val active = try {
            activeNotifications
        } catch (t: Throwable) {
            Log.w(TAG, "activeNotifications unavailable", t)
            return
        } ?: return

        var count = 0
        for (sbn in active) {
            val flags = sbn.notification.flags
            if (flags and Notification.FLAG_ONGOING_EVENT != 0) continue
            if (flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            count++
        }
        prefs.notificationCount = count
    }

    private companion object {
        const val TAG = "NotifCountService"
    }
}
