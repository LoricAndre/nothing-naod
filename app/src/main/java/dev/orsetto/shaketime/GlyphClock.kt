package dev.orsetto.shaketime

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import java.util.Calendar

/**
 * Process-wide owner of the [GlyphMatrixManager] connection that paints the
 * current time onto the Glyph Matrix for a bounded amount of time.
 *
 * A single shared instance is used by every trigger (background shake monitor,
 * launcher shortcut, automation broadcast, in-app test button) so they never
 * fight over the connection. Binding to the system Glyph service is
 * asynchronous, so [showTime] records the request and draws once connected.
 *
 * The connection is kept open while [keepConnected] is set (the shake monitor
 * does this for zero-latency reveals). Otherwise it is released shortly after a
 * reveal finishes so the process can be reclaimed.
 *
 * All work runs on the main thread.
 */
class GlyphClock private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var manager: GlyphMatrixManager? = null
    private var connected = false

    /** When true the connection is kept open between reveals. */
    var keepConnected: Boolean = false

    private var pendingBrightness = Prefs.DEFAULT_BRIGHTNESS
    private var pendingDurationMs = Prefs.MAX_DURATION_MS.toLong()
    private var hasPending = false
    private var onCleared: (() -> Unit)? = null

    private val clearRunnable = Runnable { onClearTimeout() }
    private val releaseRunnable = Runnable { releaseIfIdle() }

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            val gm = manager ?: return
            try {
                gm.register(deviceTarget())
                connected = true
                Log.d(TAG, "Glyph service connected")
                if (hasPending) draw(pendingDurationMs, pendingBrightness)
            } catch (t: Throwable) {
                Log.e(TAG, "register failed", t)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connected = false
        }
    }

    /** Ensures the manager is bound. Safe to call repeatedly. */
    fun connect() {
        if (manager != null) return
        try {
            val gm = GlyphMatrixManager.getInstance(appContext)
            manager = gm
            gm.init(callback)
        } catch (t: Throwable) {
            Log.e(TAG, "init failed", t)
            manager = null
        }
    }

    /**
     * Show the current time for [durationMs], then clear the matrix.
     *
     * @param onCleared invoked on the main thread once the matrix is cleared
     *   (used by short-lived callers to release their keep-alive).
     */
    fun showTime(durationMs: Long, brightness: Int, onCleared: (() -> Unit)? = null) {
        main.post {
            main.removeCallbacks(releaseRunnable)
            pendingDurationMs = durationMs
            pendingBrightness = brightness
            this.onCleared = onCleared
            hasPending = true
            if (connected) draw(durationMs, brightness) else connect()
        }
    }

    private fun draw(durationMs: Long, brightness: Int) {
        val gm = manager ?: return
        hasPending = false
        try {
            val len = matrixLength()
            val now = Calendar.getInstance()
            val bmp = TimeMatrixRenderer.render(
                hour24 = now.get(Calendar.HOUR_OF_DAY),
                minute = now.get(Calendar.MINUTE),
                use24h = DateFormat.is24HourFormat(appContext),
                matrixLen = len,
            )
            val obj = GlyphMatrixObject.Builder()
                .setImageSource(bmp)
                .setScale(100)
                .setOrientation(0)
                .setPosition(0, 0)
                .setBrightness(brightness.coerceIn(1, 255))
                .setReverse(false)
                .build()
            val frame = GlyphMatrixFrame.Builder()
                .addTop(obj)
                .build(appContext)
            val data = frame.render()

            // Prefer app-based control (Nothing OS 20250801+); fall back to the
            // direct matrix API on older builds.
            try {
                gm.setAppMatrixFrame(data)
            } catch (t: Throwable) {
                Log.w(TAG, "setAppMatrixFrame unavailable, using setMatrixFrame", t)
                gm.setMatrixFrame(data)
            }

            main.removeCallbacks(clearRunnable)
            main.postDelayed(clearRunnable, durationMs)
        } catch (t: Throwable) {
            Log.e(TAG, "draw failed", t)
            // Don't strand a caller waiting on completion.
            onClearTimeout()
        }
    }

    private fun onClearTimeout() {
        clear()
        val cb = onCleared
        onCleared = null
        cb?.invoke()
        if (!keepConnected) {
            // Give rapid successive triggers a moment to reuse the connection.
            main.postDelayed(releaseRunnable, RELEASE_GRACE_MS)
        }
    }

    private fun clear() {
        val gm = manager ?: return
        try {
            gm.closeAppMatrix()
        } catch (t: Throwable) {
            try {
                gm.turnOff()
            } catch (t2: Throwable) {
                Log.w(TAG, "clear failed", t2)
            }
        }
    }

    /** Releases the connection if it is idle and not pinned by [keepConnected]. */
    fun releaseIfIdle() {
        main.post {
            if (keepConnected || hasPending) return@post
            release()
        }
    }

    private fun release() {
        main.removeCallbacks(clearRunnable)
        main.removeCallbacks(releaseRunnable)
        clear()
        try {
            manager?.turnOff()
        } catch (_: Throwable) {
        }
        try {
            manager?.unInit()
        } catch (_: Throwable) {
        }
        manager = null
        connected = false
        hasPending = false
    }

    private fun deviceTarget(): String = try {
        when {
            Common.is25111p() -> Glyph.DEVICE_25111p
            Common.is23112() -> Glyph.DEVICE_23112
            else -> Glyph.DEVICE_25111p
        }
    } catch (t: Throwable) {
        Glyph.DEVICE_25111p
    }

    private fun matrixLength(): Int = try {
        val l = Common.getDeviceMatrixLength()
        if (l > 0) l else fallbackLength()
    } catch (t: Throwable) {
        fallbackLength()
    }

    private fun fallbackLength(): Int = try {
        if (Common.is23112()) 25 else 13
    } catch (t: Throwable) {
        13
    }

    companion object {
        private const val TAG = "GlyphClock"
        private const val RELEASE_GRACE_MS = 1500L

        @Volatile
        private var instance: GlyphClock? = null

        /** Returns the shared, process-wide instance. */
        fun getInstance(context: Context): GlyphClock =
            instance ?: synchronized(this) {
                instance ?: GlyphClock(context).also { instance = it }
            }
    }
}
