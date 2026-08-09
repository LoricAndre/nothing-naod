package dev.orsetto.shaketime

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Renders the current time into a square [Bitmap] sized to the Glyph Matrix.
 *
 * The Phone (4a) Pro matrix is only 13x13 LEDs, which is too small to show
 * "HH:MM" on one line legibly, so the hours are drawn on the top row and the
 * minutes on the bottom row using a compact 3x5 pixel font. Lit LEDs are white,
 * everything else black; the SDK converts the bitmap to per-LED brightness.
 */
object TimeMatrixRenderer {

    // 3 wide x 5 tall glyphs for digits 0-9. Each string is a 5-row bitmap,
    // '1' = lit pixel.
    private val FONT: Array<Array<String>> = arrayOf(
        arrayOf("111", "101", "101", "101", "111"), // 0
        arrayOf("010", "110", "010", "010", "111"), // 1
        arrayOf("111", "001", "111", "100", "111"), // 2
        arrayOf("111", "001", "111", "001", "111"), // 3
        arrayOf("101", "101", "111", "001", "001"), // 4
        arrayOf("111", "100", "111", "001", "111"), // 5
        arrayOf("111", "100", "111", "101", "111"), // 6
        arrayOf("111", "001", "010", "100", "100"), // 7
        arrayOf("111", "101", "111", "101", "111"), // 8
        arrayOf("111", "101", "111", "001", "111"), // 9
    )

    private const val GLYPH_W = 3
    private const val GLYPH_H = 5

    // Dim grey for unlit notification bits so all four positions stay visible.
    private val DIM_BIT = Color.rgb(28, 28, 28)

    /**
     * @param hour24 hour of day 0..23
     * @param minute minute 0..59
     * @param use24h whether to show 24h time; otherwise 12h (leading zero kept)
     * @param matrixLen side length of the (square) matrix in LEDs
     * @param notifications notification count to show as a 4-bit indicator in the
     *   rightmost column (top pixel = 8, bottom = 1); negative to hide it
     */
    fun render(
        hour24: Int,
        minute: Int,
        use24h: Boolean,
        matrixLen: Int,
        notifications: Int = -1,
    ): Bitmap {
        val len = matrixLen.coerceAtLeast(GLYPH_H)
        val bmp = Bitmap.createBitmap(len, len, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLACK)

        val displayHour = if (use24h) hour24 else ((hour24 + 11) % 12 + 1)
        val hh = "%02d".format(displayHour)
        val mm = "%02d".format(minute)

        // Scale the font up on larger matrices (e.g. Phone (3) is 25x25).
        val scale = if (len >= 20) 2 else 1
        val gap = scale
        val digitW = GLYPH_W * scale
        val digitH = GLYPH_H * scale

        val rowW = digitW * 2 + gap
        val totalH = digitH * 2 + gap
        val startX = (len - rowW) / 2
        val topY = (len - totalH) / 2

        drawPair(bmp, hh, startX, topY, scale, gap, digitW)
        drawPair(bmp, mm, startX, topY + digitH + gap, scale, gap, digitW)

        if (notifications >= 0) drawNotificationBits(bmp, len, notifications)
        return bmp
    }

    /**
     * Draws a 4-bit binary counter as four vertically-spaced dots on the right,
     * top = most significant bit (value 8), bottom = least significant (value 1).
     * Set bits are full brightness; unset bits are drawn dim so all four
     * positions stay visible.
     *
     * The Glyph Matrix is a disc, so the extreme corners/edges of the square
     * grid have no physical LED. The dots are placed at the right-most column
     * whose four positions still fall inside the lit disc (same distance from
     * centre as the clock digits' corners), one column clear of the digits.
     */
    private fun drawNotificationBits(bmp: Bitmap, len: Int, count: Int) {
        val value = count.coerceIn(0, 15)
        val center = (len - 1) / 2
        val half = (len / 12).coerceAtLeast(1) // half the gap between dots
        val rowOffsets = intArrayOf(-3, -1, 1, 3) // symmetric around centre
        val extreme = 3 * half // furthest dot from centre, vertically

        // Largest horizontal offset that keeps the top/bottom dots inside the disc.
        val radius = len / 2.0
        val maxDx = floor(sqrt(radius * radius - (extreme * extreme).toDouble()) - 0.5)
            .toInt()
            .coerceIn(1, center)
        val x = (center + maxDx).coerceIn(0, len - 1)

        for (i in 0 until 4) {
            val bit = 3 - i // top dot is the most significant bit
            val on = (value shr bit) and 1 == 1
            val y = center + rowOffsets[i] * half
            if (y in 0 until len) bmp.setPixel(x, y, if (on) Color.WHITE else DIM_BIT)
        }
    }

    private fun drawPair(
        bmp: Bitmap,
        two: String,
        startX: Int,
        y: Int,
        scale: Int,
        gap: Int,
        digitW: Int,
    ) {
        drawDigit(bmp, two[0] - '0', startX, y, scale)
        drawDigit(bmp, two[1] - '0', startX + digitW + gap, y, scale)
    }

    private fun drawDigit(bmp: Bitmap, digit: Int, x0: Int, y0: Int, scale: Int) {
        if (digit !in 0..9) return
        val rows = FONT[digit]
        for (ry in 0 until GLYPH_H) {
            val row = rows[ry]
            for (rx in 0 until GLYPH_W) {
                if (row[rx] != '1') continue
                for (sy in 0 until scale) {
                    for (sx in 0 until scale) {
                        val px = x0 + rx * scale + sx
                        val py = y0 + ry * scale + sy
                        if (px in 0 until bmp.width && py in 0 until bmp.height) {
                            bmp.setPixel(px, py, Color.WHITE)
                        }
                    }
                }
            }
        }
    }
}
