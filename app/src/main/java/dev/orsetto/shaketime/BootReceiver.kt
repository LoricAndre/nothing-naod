package dev.orsetto.shaketime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts background monitoring after a reboot if the user had it enabled. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        if (Prefs(context).monitoringEnabled) {
            ShakeMonitorService.startMonitoring(context)
        }
    }
}
