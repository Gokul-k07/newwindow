package com.antitheft.securepower.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antitheft.securepower.utils.PreferenceManager

/**
 * Restarts security services after device boot
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed")

            val preferenceManager = PreferenceManager.getInstance(context)

            // Check if protection was enabled before shutdown
            if (preferenceManager.isProtectionEnabled()) {
                Log.d(TAG, "Restarting security services")

                // Restart PowerButtonService (accessibility service)
                // Note: Accessibility services auto-start if enabled

                // Check if device was in security mode before shutdown
                // This could indicate theft (forced shutdown)
                checkAbnormalShutdown(context)
            }
        }
    }

    private fun checkAbnormalShutdown(context: Context) {
        // TODO: Implement logic to detect if device was shut down abnormally
        // Check for active security session that wasn't closed properly
        // If found, resume security response
    }
}
