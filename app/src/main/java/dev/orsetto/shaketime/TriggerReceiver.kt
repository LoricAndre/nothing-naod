package dev.orsetto.shaketime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Broadcast entry point for automation apps (Tasker, MacroDroid, adb, ...).
 *
 *   am broadcast -a dev.orsetto.shaketime.action.SHOW_TIME \
 *     -n dev.orsetto.shaketime/.TriggerReceiver
 *
 * Optional long extra "duration_ms" overrides the configured display duration.
 *
 * The reveal is done via the shared [GlyphClock] (no foreground service, which
 * a background broadcast may not start). [goAsync] keeps the process alive for
 * the display duration and is finished once the matrix clears.
 */
class TriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Actions.ACTION_SHOW_TIME) return

        val prefs = Prefs(context)
        val override = intent.getLongExtra(Actions.EXTRA_DURATION_MS, -1L)
        val duration = if (override > 0) override else prefs.durationMs

        val pending = goAsync()
        GlyphClock.getInstance(context)
            .showTime(duration, prefs.brightness, onCleared = { pending.finish() })
    }
}
