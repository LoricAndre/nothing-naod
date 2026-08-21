package dev.orsetto.shaketime

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

/**
 * Shared definition of "the phone is lying face-down on a surface": gravity
 * points into the screen (Z strongly negative) and the device is roughly flat.
 */
object FaceDown {

    // ~ -9.8 when perfectly face-down; -6 allows ~52 degrees of tilt.
    const val FACE_DOWN_Z = -6f
    const val FLAT_XY = 7f

    fun isFaceDown(x: Float, y: Float, z: Float): Boolean =
        z < FACE_DOWN_Z && abs(x) < FLAT_XY && abs(y) < FLAT_XY
}

/**
 * Takes a short one-shot accelerometer reading to decide whether the phone is
 * currently face-down.
 *
 * Used by [NotificationCountService], which has no long-lived sensor listener of
 * its own. The caller is responsible for holding a wake lock if the screen may
 * be off, otherwise the sensor will not deliver samples.
 */
class FaceDownSampler(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val main = Handler(Looper.getMainLooper())

    /**
     * Samples the accelerometer briefly and reports whether the phone is
     * face-down. [callback] is always invoked exactly once, on the main thread;
     * it reports false if no reading arrives within [timeoutMs].
     */
    fun sample(timeoutMs: Long = DEFAULT_TIMEOUT_MS, callback: (Boolean) -> Unit) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            main.post { callback(false) }
            return
        }

        var finished = false
        var count = 0
        var sumX = 0f
        var sumY = 0f
        var sumZ = 0f

        lateinit var listener: SensorEventListener
        lateinit var timeout: Runnable

        fun finish(result: Boolean) {
            if (finished) return
            finished = true
            main.removeCallbacks(timeout)
            try {
                sensorManager.unregisterListener(listener)
            } catch (_: Throwable) {
            }
            callback(result)
        }

        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                sumX += event.values[0]
                sumY += event.values[1]
                sumZ += event.values[2]
                count++
                if (count >= SAMPLES) {
                    val n = count.toFloat()
                    finish(FaceDown.isFaceDown(sumX / n, sumY / n, sumZ / n))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        timeout = Runnable {
            // Use whatever we managed to collect; otherwise assume not face-down.
            if (count > 0) {
                val n = count.toFloat()
                finish(FaceDown.isFaceDown(sumX / n, sumY / n, sumZ / n))
            } else {
                finish(false)
            }
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, 0)
        main.postDelayed(timeout, timeoutMs)
    }

    private companion object {
        const val SAMPLES = 5
        const val DEFAULT_TIMEOUT_MS = 700L
    }
}
