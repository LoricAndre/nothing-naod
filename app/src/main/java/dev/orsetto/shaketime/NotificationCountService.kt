package dev.orsetto.shaketime

import android.app.Notification
import android.app.NotificationManager
import android.graphics.drawable.Drawable
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Watches the notification shade for two features:
 *
 *  - keeps the count of alerting notifications for the Glyph binary indicator;
 *  - flashes an arriving notification's icon on the Glyph Matrix when the phone
 *    is lying face-down.
 *
 * Requires the user to grant "Notification access" in system settings. Ongoing
 * notifications (including this app's own foreground-service notification),
 * group summaries, and silent notifications (importance below
 * [NotificationManager.IMPORTANCE_DEFAULT]) are ignored by both features, so
 * only real, alerting items count.
 */
class NotificationCountService : NotificationListenerService() {

    private val prefs by lazy { Prefs(this) }
    private val glyphClock by lazy { GlyphClock.getInstance(this) }
    private val sampler by lazy { FaceDownSampler(this) }
    private val powerManager by lazy { getSystemService(POWER_SERVICE) as PowerManager }

    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShakeTime:notification")
            .apply { setReferenceCounted(false) }
    }

    private var lastRevealKey: String? = null
    private var lastRevealAt = 0L

    override fun onListenerConnected() = updateCount()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateCount()
        if (sbn != null) maybeRevealIcon(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = updateCount()

    // --- icon reveal --------------------------------------------------------

    /**
     * Flashes [sbn]'s icon if the feature is on, the notification is a real
     * alerting one, and the phone is currently face-down.
     */
    private fun maybeRevealIcon(sbn: StatusBarNotification) {
        if (!prefs.notificationRevealEnabled) return
        if (!isAlerting(sbn)) return

        // Ignore repeats of the same notification (updates, progress ticks) and
        // rapid bursts, so the matrix isn't monopolised.
        val now = System.currentTimeMillis()
        val duration = prefs.notificationRevealMs
        if (sbn.key == lastRevealKey && now - lastRevealAt < REPEAT_SUPPRESS_MS) return
        if (now - lastRevealAt < duration) return
        lastRevealKey = sbn.key
        lastRevealAt = now

        val icon = loadIcon(sbn) ?: return

        // Keep the CPU awake to sample the sensor and drive the matrix; the
        // screen is off whenever the phone is actually face-down.
        wakeLock.acquire(duration + WAKELOCK_MARGIN_MS)
        sampler.sample { faceDown ->
            if (!faceDown) {
                if (wakeLock.isHeld) wakeLock.release()
                return@sample
            }
            val bitmap = try {
                NotificationIconRenderer.render(icon, glyphClock.matrixSize())
            } catch (t: Throwable) {
                Log.w(TAG, "icon render failed", t)
                if (wakeLock.isHeld) wakeLock.release()
                return@sample
            }
            glyphClock.showBitmap(bitmap, duration) {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    /**
     * The icon to draw for [sbn].
     *
     * By default this is the notification's small icon — apps set that to their
     * own monochrome badge, so it stays legible on the matrix. When the user
     * prefers the app icon (e.g. because an app puts per-conversation artwork in
     * its notifications), the launcher icon is used instead, preferring its
     * monochrome layer.
     */
    private fun loadIcon(sbn: StatusBarNotification): Drawable? = try {
        if (prefs.useAppIconForNotifications) {
            appIcon(sbn.packageName) ?: sbn.notification.smallIcon?.loadDrawable(this)
        } else {
            sbn.notification.smallIcon?.loadDrawable(this) ?: appIcon(sbn.packageName)
        }
    } catch (t: Throwable) {
        Log.w(TAG, "icon load failed for ${sbn.packageName}", t)
        null
    }

    private fun appIcon(packageName: String): Drawable? = try {
        NotificationIconRenderer.preferMonochrome(packageManager.getApplicationIcon(packageName))
    } catch (t: Throwable) {
        Log.w(TAG, "app icon unavailable for $packageName", t)
        null
    }

    // --- counting -----------------------------------------------------------

    private fun updateCount() {
        val active = try {
            activeNotifications
        } catch (t: Throwable) {
            Log.w(TAG, "activeNotifications unavailable", t)
            return
        } ?: return

        var count = 0
        for (sbn in active) {
            if (isAlerting(sbn)) count++
        }
        prefs.notificationCount = count
    }

    /** True for dismissable, non-summary, non-silent notifications. */
    private fun isAlerting(sbn: StatusBarNotification): Boolean {
        val flags = sbn.notification.flags
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val rankingMap = currentRanking ?: return true
        val ranking = Ranking()
        if (!rankingMap.getRanking(sbn.key, ranking)) return true
        return ranking.importance >= NotificationManager.IMPORTANCE_DEFAULT
    }

    private companion object {
        const val TAG = "NotifCountService"
        const val WAKELOCK_MARGIN_MS = 2500L
        const val REPEAT_SUPPRESS_MS = 30_000L
    }
}
