package com.antitheft.securepower.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.antitheft.securepower.ui.auth.PinVerificationActivity
import com.antitheft.securepower.utils.PreferenceManager

/**
 * Accessibility Service to intercept power button press
 * Detects screen-off events and shows authentication dialog
 */
class PowerButtonService : AccessibilityService() {

    companion object {
        private const val TAG = "PowerButtonService"
        private var isServiceRunning = false

        fun isRunning(): Boolean = isServiceRunning
    }

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var screenOffReceiver: BroadcastReceiver
    private var isPowerButtonPressed = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        preferenceManager = PreferenceManager.getInstance(this)

        Log.d(TAG, "PowerButtonService connected")

        // Register screen off receiver
        registerScreenOffReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This accessibility service primarily uses broadcast receivers
        // but we keep this for future enhancements
    }

    override fun onInterrupt() {
        Log.d(TAG, "PowerButtonService interrupted")
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        // Detect power button press
        if (event.keyCode == KeyEvent.KEYCODE_POWER) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                isPowerButtonPressed = true
                Log.d(TAG, "Power button pressed")
            } else if (event.action == KeyEvent.ACTION_UP && isPowerButtonPressed) {
                isPowerButtonPressed = false
                handlePowerButtonPress()
                return true // Consume the event
            }
        }

        return super.onKeyEvent(event)
    }

    private fun registerScreenOffReceiver() {
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    handleScreenOff()
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)
        Log.d(TAG, "Screen off receiver registered")
    }

    private fun handlePowerButtonPress() {
        // Check if protection is enabled
        if (!preferenceManager.isProtectionEnabled()) {
            Log.d(TAG, "Protection disabled, allowing power off")
            return
        }

        // Check if we're in emergency mode (battery critically low)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryLevel = getBatteryLevel()
        if (batteryLevel <= 5) {
            Log.d(TAG, "Battery critically low, allowing power off")
            return
        }

        // Show authentication dialog
        showAuthenticationDialog()
    }

    private fun handleScreenOff() {
        // This is triggered when screen turns off
        // We can use this as additional detection for power button press
        if (!preferenceManager.isProtectionEnabled()) {
            return
        }

        Log.d(TAG, "Screen turned off, checking if power button caused it")

        // If screen turned off within last second, likely power button
        // Show authentication dialog
        if (shouldInterceptScreenOff()) {
            showAuthenticationDialog()
        }
    }

    private fun shouldInterceptScreenOff(): Boolean {
        // Check if screen off was due to power button vs timeout
        // This is a simplified check - in production, would need more sophisticated detection

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        // If screen just turned off and we're still awake (not timeout), likely power button
        return !powerManager.isInteractive
    }

    private fun showAuthenticationDialog() {
        Log.d(TAG, "Showing authentication dialog")

        val intent = Intent(this, PinVerificationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("source", "power_button")
        }

        startActivity(intent)

        // Wake up screen to show dialog
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SecurePower:AuthDialog"
        )
        wakeLock.acquire(10000) // 10 seconds
    }

    private fun getBatteryLevel(): Int {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level != -1 && scale != -1) {
            (level.toFloat() / scale.toFloat() * 100).toInt()
        } else {
            100 // Default to full battery if unable to determine
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false

        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }

        Log.d(TAG, "PowerButtonService destroyed")
    }
}

/**
 * Screen Off Broadcast Receiver
 * Separate receiver to handle screen off events
 */
class ScreenOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
            Log.d("ScreenOffReceiver", "Screen turned off")

            // Check if protection is enabled
            val preferenceManager = PreferenceManager.getInstance(context!!)
            if (!preferenceManager.isProtectionEnabled()) {
                return
            }

            // Show authentication dialog
            val authIntent = Intent(context, PinVerificationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("source", "screen_off")
            }
            context.startActivity(authIntent)
        }
    }
}
