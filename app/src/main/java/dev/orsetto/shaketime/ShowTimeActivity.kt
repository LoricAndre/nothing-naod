package dev.orsetto.shaketime

import android.app.Activity
import android.os.Bundle

/**
 * Invisible entry point that reveals the time on the Glyph Matrix and finishes.
 *
 * It backs the launcher shortcut and can be started by automation apps:
 *   am start -n dev.orsetto.shaketime/.ShowTimeActivity
 * An optional long extra "duration_ms" overrides the configured duration.
 *
 * Uses a windowNoDisplay theme and finishes in onCreate (the standard trampoline
 * pattern). The draw and its clear timer live in the shared [GlyphClock]; the
 * process stays cached for the few seconds needed to clear the matrix.
 */
class ShowTimeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val override = intent?.getLongExtra(Actions.EXTRA_DURATION_MS, -1L) ?: -1L
        val duration = if (override > 0) override else prefs.durationMs

        GlyphClock.getInstance(this).showTime(duration)
        finish()
    }
}
