package dev.orsetto.shaketime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Settings screen: toggle background monitoring, tune duration / sensitivity /
 * brightness, test the reveal, pin a home-screen shortcut, and read the
 * automation recipe.
 */
class MainActivity : android.app.Activity() {

    private lateinit var prefs: Prefs

    private lateinit var monitorSwitch: Switch
    private lateinit var durationLabel: TextView
    private lateinit var sensitivityLabel: TextView
    private lateinit var brightnessLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        monitorSwitch = findViewById(R.id.switch_monitor)
        durationLabel = findViewById(R.id.label_duration)
        sensitivityLabel = findViewById(R.id.label_sensitivity)
        brightnessLabel = findViewById(R.id.label_brightness)

        setupMonitorSwitch()
        setupShowNow()
        setupDuration()
        setupSensitivity()
        setupBrightness()
        setupPinShortcut()
    }

    override fun onResume() {
        super.onResume()
        monitorSwitch.isChecked = prefs.monitoringEnabled
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

    private fun setupShowNow() {
        findViewById<Button>(R.id.btn_show_now).setOnClickListener {
            // In-app and foreground, so we can drive the Glyph directly.
            GlyphClock.getInstance(this).showTime(prefs.durationMs, prefs.brightness)
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

    private fun setupBrightness() {
        val seek = findViewById<SeekBar>(R.id.seek_brightness)
        seek.progress = prefs.brightness
        updateBrightnessLabel()
        seek.setOnSeekBarChangeListener(object : SimpleSeekListener() {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                prefs.brightness = p.coerceAtLeast(1)
                updateBrightnessLabel()
            }
        })
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

    private fun updateBrightnessLabel() {
        brightnessLabel.text = getString(R.string.brightness_fmt, prefs.brightness)
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
