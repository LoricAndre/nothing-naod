package dev.orsetto.shaketime

import android.content.Context

/**
 * Thin wrapper over [android.content.SharedPreferences] holding user settings.
 *
 * Sensitivity and duration are stored as 0..100 slider values and mapped to
 * concrete units on read so the UI and the detector never disagree.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("shake_time", Context.MODE_PRIVATE)

    /** Whether the background shake monitor should be running. */
    var monitoringEnabled: Boolean
        get() = sp.getBoolean(KEY_MONITORING, false)
        set(value) = sp.edit().putBoolean(KEY_MONITORING, value).apply()

    /** Detection power mode: one of [MODE_RELIABLE], [MODE_BALANCED], [MODE_SCREEN_ON]. */
    var monitorMode: Int
        get() = sp.getInt(KEY_MODE, MODE_RELIABLE).coerceIn(MODE_RELIABLE, MODE_SCREEN_ON)
        set(value) = sp.edit().putInt(KEY_MODE, value.coerceIn(MODE_RELIABLE, MODE_SCREEN_ON)).apply()

    /** Slider value 0..100; higher = more sensitive (lower acceleration needed). */
    var sensitivity: Int
        get() = sp.getInt(KEY_SENSITIVITY, DEFAULT_SENSITIVITY).coerceIn(0, 100)
        set(value) = sp.edit().putInt(KEY_SENSITIVITY, value.coerceIn(0, 100)).apply()

    /** Slider value 0..100 mapped to [MIN_DURATION_MS]..[MAX_DURATION_MS]. */
    var durationSlider: Int
        get() = sp.getInt(KEY_DURATION, DEFAULT_DURATION).coerceIn(0, 100)
        set(value) = sp.edit().putInt(KEY_DURATION, value.coerceIn(0, 100)).apply()

    /**
     * Whether to flash a notification's icon on the Glyph when it arrives while
     * the phone is face-down.
     */
    var notificationRevealEnabled: Boolean
        get() = sp.getBoolean(KEY_NOTIF_REVEAL, false)
        set(value) = sp.edit().putBoolean(KEY_NOTIF_REVEAL, value).apply()

    /** Slider value 0..100 mapped to [MIN_REVEAL_MS]..[MAX_REVEAL_MS]. */
    var notificationRevealSlider: Int
        get() = sp.getInt(KEY_NOTIF_REVEAL_MS, DEFAULT_NOTIF_REVEAL).coerceIn(0, 100)
        set(value) = sp.edit().putInt(KEY_NOTIF_REVEAL_MS, value.coerceIn(0, 100)).apply()

    /** How long to show a notification icon, in milliseconds. */
    val notificationRevealMs: Long
        get() = (MIN_REVEAL_MS + (MAX_REVEAL_MS - MIN_REVEAL_MS) *
            notificationRevealSlider / 100).toLong()

    /**
     * When true, always draw the notifying app's launcher icon (preferring its
     * monochrome layer) instead of the icon carried by the notification.
     */
    var useAppIconForNotifications: Boolean
        get() = sp.getBoolean(KEY_NOTIF_APP_ICON, false)
        set(value) = sp.edit().putBoolean(KEY_NOTIF_APP_ICON, value).apply()

    /** Whether to overlay the notification count as a binary indicator. */
    var notificationIndicatorEnabled: Boolean
        get() = sp.getBoolean(KEY_NOTIF_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_NOTIF_ENABLED, value).apply()

    /** Last known notification count, 0..[MAX_NOTIF_COUNT]. */
    var notificationCount: Int
        get() = sp.getInt(KEY_NOTIF_COUNT, 0).coerceIn(0, MAX_NOTIF_COUNT)
        set(value) = sp.edit().putInt(KEY_NOTIF_COUNT, value.coerceIn(0, MAX_NOTIF_COUNT)).apply()

    /** How long to keep the time on the matrix, in milliseconds. */
    val durationMs: Long
        get() = (MIN_DURATION_MS + (MAX_DURATION_MS - MIN_DURATION_MS) *
            durationSlider / 100).toLong()

    /**
     * Peak linear-acceleration (m/s^2 above gravity) required to count as a
     * shake jolt. High sensitivity -> low threshold.
     */
    val shakeThreshold: Float
        get() = MAX_THRESHOLD - (MAX_THRESHOLD - MIN_THRESHOLD) * sensitivity / 100f

    companion object {
        private const val KEY_MONITORING = "monitoring"
        private const val KEY_MODE = "monitor_mode"
        private const val KEY_SENSITIVITY = "sensitivity"

        /** Continuous wake lock; works on any device, most battery. */
        const val MODE_RELIABLE = 0

        /** Wake-up sensor, no continuous wake lock; lighter on battery. */
        const val MODE_BALANCED = 1

        /** No wake lock; only detects while the screen is on; least battery. */
        const val MODE_SCREEN_ON = 2
        private const val KEY_DURATION = "duration"
        private const val KEY_NOTIF_ENABLED = "notif_enabled"
        private const val KEY_NOTIF_REVEAL = "notif_reveal"
        private const val KEY_NOTIF_REVEAL_MS = "notif_reveal_ms"
        private const val KEY_NOTIF_APP_ICON = "notif_app_icon"

        /** ~3 s within the range below: a glance, not a takeover. */
        const val DEFAULT_NOTIF_REVEAL = 29

        const val MIN_REVEAL_MS = 1000
        const val MAX_REVEAL_MS = 8000
        private const val KEY_NOTIF_COUNT = "notif_count"

        /** 4-bit indicator maxes out at 15. */
        const val MAX_NOTIF_COUNT = 15

        const val DEFAULT_SENSITIVITY = 65
        const val DEFAULT_DURATION = 40 // ~4.6s within the range below

        const val MIN_DURATION_MS = 1500
        const val MAX_DURATION_MS = 10_000

        // Acceleration thresholds (m/s^2 above the ~9.8 gravity baseline).
        const val MIN_THRESHOLD = 3f
        const val MAX_THRESHOLD = 16f
    }
}
