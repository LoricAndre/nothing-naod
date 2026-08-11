package dev.orsetto.shaketime

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Settings screen: toggle background monitoring, tune duration / sensitivity,
 * test the reveal, pin a home-screen shortcut, and read the automation recipe.
 * Glyph brightness follows the system Glyph Interface setting.
 */
class MainActivity : android.app.Activity() {

    private lateinit var prefs: Prefs

    private lateinit var monitorSwitch: Switch
    private lateinit var notifSwitch: Switch
    private lateinit var durationLabel: TextView
    private lateinit var sensitivityLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        monitorSwitch = findViewById(R.id.switch_monitor)
        notifSwitch = findViewById(R.id.switch_notif)
        durationLabel = findViewById(R.id.label_duration)
        sensitivityLabel = findViewById(R.id.label_sensitivity)

        setupMonitorSwitch()
        setupModeSelector()
        setupShowNow()
        setupDuration()
        setupSensitivity()
        setupNotificationIndicator()
        setupPinShortcut()
    }

    override fun onResume() {
        super.onResume()
        monitorSwitch.isChecked = prefs.monitoringEnabled
        // Access may have been granted/revoked in system settings while away.
        notifSwitch.isChecked = prefs.notificationIndicatorEnabled && isNotificationAccessGranted()
    }

    private fun setupMonitorSwitch() {
        monitorSwitch.isChecked = prefs.monitoringEnabled
        monitorSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                ensureNotificationPermission()
                ShakeMonitorService.startMonitoring(this)
            } else {
                ShakeMonitorService.stopMonitoring(this)
            }
        }
    }

    private fun setupModeSelector() {
        val group = findViewById<RadioGroup>(R.id.rg_mode)
        group.check(
            when (prefs.monitorMode) {
                Prefs.MODE_BALANCED -> R.id.rb_balanced
                Prefs.MODE_SCREEN_ON -> R.id.rb_screen_on
                else -> R.id.rb_reliable
            },
        )
        group.setOnCheckedChangeListener { _, checkedId ->
            prefs.monitorMode = when (checkedId) {
                R.id.rb_balanced -> Prefs.MODE_BALANCED
                R.id.rb_screen_on -> Prefs.MODE_SCREEN_ON
                else -> Prefs.MODE_RELIABLE
            }
            // Re-apply immediately if the monitor is running.
            if (prefs.monitoringEnabled) ShakeMonitorService.startMonitoring(this)
        }
    }

    private fun setupShowNow() {
        findViewById<Button>(R.id.btn_show_now).setOnClickListener {
            // In-app and foreground, so we can drive the Glyph directly.
            GlyphClock.getInstance(this).showTime(prefs.durationMs)
            Toast.makeText(this, R.string.shown_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDuration() {
        val seek = findViewById<SeekBar>(R.id.seek_duration)
        seek.progress = prefs.durationSlider
        updateDurationLabel()
        seek.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                prefs.durationSlider = p
                updateDurationLabel()
            }
        })
    }

    private fun setupSensitivity() {
        val seek = findViewById<SeekBar>(R.id.seek_sensitivity)
        seek.progress = prefs.sensitivity
        updateSensitivityLabel()
        seek.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                prefs.sensitivity = p
                updateSensitivityLabel()
            }
        })
    }

    private fun setupNotificationIndicator() {
        notifSwitch.isChecked = prefs.notificationIndicatorEnabled && isNotificationAccessGranted()
        notifSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && !isNotificationAccessGranted()) {
                Toast.makeText(this, R.string.notif_access_needed, Toast.LENGTH_LONG).show()
                openNotificationAccessSettings()
                notifSwitch.isChecked = false
                prefs.notificationIndicatorEnabled = false
                return@setOnCheckedChangeListener
            }
            prefs.notificationIndicatorEnabled = checked
        }
        findViewById<Button>(R.id.btn_notif_access).setOnClickListener {
            openNotificationAccessSettings()
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val nm = getSystemService(NotificationManager::class.java) ?: return false
        return nm.isNotificationListenerAccessGranted(
            ComponentName(this, NotificationCountService::class.java),
        )
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun setupPinShortcut() {
        findViewById<Button>(R.id.btn_pin_shortcut).setOnClickListener {
            val sm = getSystemService(ShortcutManager::class.java)
            if (sm == null || !sm.isRequestPinShortcutSupported) {
                Toast.makeText(this, R.string.pin_unsupported, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(this, ShowTimeActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
            val shortcut = ShortcutInfo.Builder(this, "show_time")
                .setShortLabel(getString(R.string.shortcut_short))
                .setLongLabel(getString(R.string.shortcut_long))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shake_time))
                .setIntent(intent)
                .build()
            sm.requestPinShortcut(shortcut, null)
        }
    }

    private fun updateDurationLabel() {
        durationLabel.text = getString(R.string.duration_fmt, prefs.durationMs / 1000f)
    }

    private fun updateSensitivityLabel() {
        sensitivityLabel.text = getString(R.string.sensitivity_fmt, prefs.sensitivity)
    }

    private fun ensureNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    private companion object {
        const val REQ_NOTIF = 100
    }

    /** SeekBar listener that only cares about progress changes. */
    private abstract class SimpleSeekListener : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }
}
