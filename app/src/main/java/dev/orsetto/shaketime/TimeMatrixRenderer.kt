package dev.orsetto.shaketime

import android.graphics.Bitmap
import android.graphics.Color

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

    /**
     * @param hour24 hour of day 0..23
     * @param minute minute 0..59
     * @param use24h whether to show 24h time; otherwise 12h (leading zero kept)
     * @param matrixLen side length of the (square) matrix in LEDs
     */
    fun render(hour24: Int, minute: Int, use24h: Boolean, matrixLen: Int): Bitmap {
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
        return bmp
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
