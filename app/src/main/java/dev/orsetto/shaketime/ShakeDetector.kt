package dev.orsetto.shaketime

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects a shake that happens while the phone is lying face-down on a surface.
 *
 * A low-pass filter isolates gravity so we can tell the screen is pointing down
 * (accelerometer Z strongly negative). The high-pass remainder is the "jolt";
 * we require several jolts above [Prefs.shakeThreshold] within a short window so
 * a single bump of the table doesn't trigger, then enforce a cooldown so one
 * shake produces exactly one reveal.
 */
class ShakeDetector(
    private val thresholdProvider: () -> Float,
    private val cooldownMsProvider: () -> Long,
    private val onShakeWhileFaceDown: () -> Unit,
) : SensorEventListener {

    private val gravity = FloatArray(3)
    private var haveGravity = false

    private var joltCount = 0
    private var firstJoltAt = 0L
    private var lastTriggerAt = 0L

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = System.currentTimeMillis()

        // Low-pass filter to estimate gravity.
        if (!haveGravity) {
            gravity[0] = event.values[0]
            gravity[1] = event.values[1]
            gravity[2] = event.values[2]
            haveGravity = true
            return
        }
        for (i in 0..2) {
            gravity[i] = ALPHA * gravity[i] + (1 - ALPHA) * event.values[i]
        }

        // Face-down: gravity vector points into the screen (Z clearly negative)
        // and the phone lies roughly flat (X/Y gravity small).
        val faceDown = gravity[2] < FACE_DOWN_Z &&
            abs(gravity[0]) < FLAT_XY && abs(gravity[1]) < FLAT_XY
        if (!faceDown) {
            joltCount = 0
            return
        }

        // High-pass remainder = motion above gravity.
        val lx = event.values[0] - gravity[0]
        val ly = event.values[1] - gravity[1]
        val lz = event.values[2] - gravity[2]
        val magnitude = sqrt(lx * lx + ly * ly + lz * lz)

        if (magnitude < thresholdProvider()) return

        if (joltCount == 0 || now - firstJoltAt > SHAKE_WINDOW_MS) {
            joltCount = 1
            firstJoltAt = now
            return
        }
        joltCount++

        if (joltCount >= REQUIRED_JOLTS && now - lastTriggerAt > cooldownMsProvider()) {
            lastTriggerAt = now
            joltCount = 0
            onShakeWhileFaceDown()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Reset transient state (e.g. when (re)registering the listener). */
    fun reset() {
        haveGravity = false
        joltCount = 0
    }

    private companion object {
        const val ALPHA = 0.8f

        // ~ -9.8 when perfectly face-down; -6 allows ~52 degrees of tilt.
        const val FACE_DOWN_Z = -6f
        const val FLAT_XY = 7f

        const val REQUIRED_JOLTS = 2
        const val SHAKE_WINDOW_MS = 1200L
    }
}
