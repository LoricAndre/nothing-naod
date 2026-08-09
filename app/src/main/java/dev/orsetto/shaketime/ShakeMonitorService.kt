package dev.orsetto.shaketime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Foreground service that owns the accelerometer listener for background shake
 * detection. It is always started from an allowed context (the app in the
 * foreground, or [BootReceiver] on boot), so it never trips Android 12+ limits
 * on starting foreground services from the background.
 *
 * One-shot reveals (shortcut / automation / test button) do NOT go through this
 * service; they use the shared [GlyphClock] directly so they work even when the
 * monitor is off.
 */
class ShakeMonitorService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var glyphClock: GlyphClock
    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private var accelerometer: Sensor? = null
    private var listenerRegistered = false

    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShakeTime:reveal")
            .apply { setReferenceCounted(false) }
    }

    private val detector by lazy {
        ShakeDetector(
            thresholdProvider = { prefs.shakeThreshold },
            cooldownMsProvider = { prefs.durationMs + 1000L },
            onShakeWhileFaceDown = { reveal() },
        )
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        glyphClock = GlyphClock.getInstance(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        // Prefer a wake-up accelerometer so shakes are detected while the screen
        // is off (the phone is face-down). Fall back to the standard sensor,
        // which only delivers events while the device is awake.
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote promptly: startForegroundService() requires startForeground().
        promoteToForeground()

        when (intent?.action ?: defaultAction()) {
            ACTION_STOP_MONITORING -> {
                prefs.monitoringEnabled = false
                stopMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> { // ACTION_START_MONITORING or a sticky restart
                prefs.monitoringEnabled = true
                startMonitoring()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    /**
     * Shows the time in response to a shake. Holds a short wake lock so the CPU
     * stays awake to draw and then clear the matrix while the screen is off.
     */
    private fun reveal() {
        val duration = prefs.durationMs
        wakeLock.acquire(duration + WAKELOCK_MARGIN_MS)
        glyphClock.showTime(duration, prefs.brightness) {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun startMonitoring() {
        // Bind the Glyph connection now for zero-latency reveals; it stays open
        // for the process lifetime.
        glyphClock.connect()
        if (!listenerRegistered) {
            val sensor = accelerometer
            if (sensor == null) {
                Log.w(TAG, "No accelerometer; cannot monitor")
                return
            }
            detector.reset()
            // maxReportLatency = 0: no batching, so wake-up events arrive
            // immediately instead of being buffered until the next wake.
            sensorManager.registerListener(
                detector,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
                0,
            )
            listenerRegistered = true
        }
    }

    private fun stopMonitoring() {
        if (listenerRegistered) {
            sensorManager.unregisterListener(detector)
            listenerRegistered = false
        }
    }

    private fun defaultAction(): String =
        if (prefs.monitoringEnabled) ACTION_START_MONITORING else ACTION_STOP_MONITORING

    // --- notification -------------------------------------------------------

    private fun promoteToForeground() {
        startForeground(
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ShakeMonitorService::class.java).setAction(ACTION_STOP_MONITORING),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text_on))
            .setSmallIcon(R.drawable.ic_shake_time)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.action_stop), stop).build(),
            )
            .build()
    }

    companion object {
        const val ACTION_START_MONITORING = "dev.orsetto.shaketime.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "dev.orsetto.shaketime.action.STOP_MONITORING"

        private const val TAG = "ShakeMonitorService"
        private const val CHANNEL_ID = "shake_monitor"
        private const val NOTIF_ID = 1
        private const val WAKELOCK_MARGIN_MS = 2000L

        fun startMonitoring(context: Context) =
            context.startForegroundService(intent(context, ACTION_START_MONITORING))

        fun stopMonitoring(context: Context) =
            context.startForegroundService(intent(context, ACTION_STOP_MONITORING))

        private fun intent(context: Context, action: String) =
            Intent(context, ShakeMonitorService::class.java).setAction(action)
    }
}
