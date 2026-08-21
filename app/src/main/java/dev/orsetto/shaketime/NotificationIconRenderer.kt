package dev.orsetto.shaketime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Converts a notification's icon into a bitmap sized for the Glyph Matrix.
 *
 * Notification small icons are monochrome silhouettes with meaningful alpha, so
 * the icon is rendered large, then box-downsampled using **coverage** (alpha) as
 * the LED intensity. That keeps a recognisable shape at 13x13, where a naive
 * scale would turn detail into mush. The result is normalised so the strongest
 * pixel is fully lit, and very faint pixels are dropped.
 */
object NotificationIconRenderer {

    /** Supersampling factor used before downsampling to the matrix size. */
    private const val SS = 8

    /** Coverage below this fraction of the peak is treated as unlit. */
    private const val NOISE_FLOOR = 0.28f

    /**
     * @param drawable the notification's small icon (or the app icon)
     * @param matrixLen side length of the (square) matrix in LEDs
     * @param inset LEDs of padding around the icon, so it isn't clipped by the
     *   circular matrix
     */
    fun render(drawable: Drawable, matrixLen: Int, inset: Int = 1): Bitmap {
        val len = matrixLen.coerceAtLeast(1)
        val out = Bitmap.createBitmap(len, len, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.BLACK)

        val target = (len - 2 * inset).coerceAtLeast(1)
        val big = Bitmap.createBitmap(target * SS, target * SS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(big)
        // Draw white so colour never skews the intensity; only shape matters.
        val d = drawable.mutate()
        d.setBounds(0, 0, big.width, big.height)
        d.setTint(Color.WHITE)
        d.draw(canvas)

        val pixels = IntArray(big.width * big.height)
        big.getPixels(pixels, 0, big.width, 0, 0, big.width, big.height)
        big.recycle()

        // Box-downsample: average coverage (alpha x luminance) per output cell.
        val coverage = FloatArray(target * target)
        var peak = 0f
        for (cy in 0 until target) {
            for (cx in 0 until target) {
                var sum = 0f
                for (sy in 0 until SS) {
                    val py = cy * SS + sy
                    val rowOffset = py * (target * SS)
                    for (sx in 0 until SS) {
                        val p = pixels[rowOffset + cx * SS + sx]
                        val a = (p ushr 24) and 0xFF
                        if (a == 0) continue
                        val lum = (
                            0.299f * ((p shr 16) and 0xFF) +
                                0.587f * ((p shr 8) and 0xFF) +
                                0.114f * (p and 0xFF)
                            ) / 255f
                        sum += (a / 255f) * lum
                    }
                }
                val v = sum / (SS * SS)
                coverage[cy * target + cx] = v
                if (v > peak) peak = v
            }
        }

        if (peak <= 0f) return out // Nothing drawable; leave the matrix blank.

        for (cy in 0 until target) {
            for (cx in 0 until target) {
                val norm = coverage[cy * target + cx] / peak
                if (norm < NOISE_FLOOR) continue
                val level = min(255, (norm * 255f).roundToInt())
                out.setPixel(cx + inset, cy + inset, Color.rgb(level, level, level))
            }
        }
        return out
    }
}
