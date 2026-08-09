package dev.orsetto.shaketime

/** Public intent contract used by shortcuts and automation apps. */
object Actions {
    /** Broadcast/activity action that triggers a one-shot time reveal. */
    const val ACTION_SHOW_TIME = "dev.orsetto.shaketime.action.SHOW_TIME"

    /** Optional long extra: display duration in milliseconds. */
    const val EXTRA_DURATION_MS = "duration_ms"
}
