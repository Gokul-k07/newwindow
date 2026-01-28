package com.antitheft.securepower.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.antitheft.securepower.utils.PreferenceManager

/**
 * Detects SIM card changes and triggers security alert
 */
class SimChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SimChangeReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            Intent.ACTION_SIM_STATE_CHANGED -> {
                handleSimStateChange(context, intent)
            }
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                // Additional SIM change detection
                checkSimChange(context)
            }
        }
    }

    private fun handleSimStateChange(context: Context, intent: Intent) {
        val simState = intent.getStringExtra("ss")
        Log.d(TAG, "SIM state changed: $simState")

        when (simState) {
            "ABSENT" -> {
                Log.d(TAG, "SIM card removed")
                // Wait a grace period before triggering alert
                scheduleSimCheckWithDelay(context, 5 * 60 * 1000L) // 5 minutes
            }
            "LOADED", "READY" -> {
                Log.d(TAG, "SIM card loaded/ready")
                checkSimChange(context)
            }
        }
    }

    private fun checkSimChange(context: Context) {
        val preferenceManager = PreferenceManager.getInstance(context)

        if (!preferenceManager.isSimAlertEnabled()) {
            Log.d(TAG, "SIM alert disabled")
            return
        }

        val currentSimIccid = getCurrentSimIccid(context)
        val originalSimIccid = preferenceManager.getOriginalSimIccid()

        if (originalSimIccid == null) {
            // First time, store current SIM
            if (currentSimIccid != null) {
                preferenceManager.setOriginalSimIccid(currentSimIccid)
                Log.d(TAG, "Original SIM stored: $currentSimIccid")
            }
            return
        }

        if (currentSimIccid != null && currentSimIccid != originalSimIccid) {
            Log.w(TAG, "SIM card changed! Old: $originalSimIccid, New: $currentSimIccid")
            triggerSimChangeAlert(context, originalSimIccid, currentSimIccid)
        }
    }

    private fun getCurrentSimIccid(context: Context): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE)
                    as TelephonyManager
            telephonyManager.simSerialNumber
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for reading SIM", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SIM", e)
            null
        }
    }

    private fun triggerSimChangeAlert(context: Context, oldIccid: String, newIccid: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "User not authenticated")
            return
        }

        val preferenceManager = PreferenceManager.getInstance(context)
        val deviceId = preferenceManager.getDeviceId()
            ?: android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )

        // Create alert in Firebase
        val database = FirebaseDatabase.getInstance()
        val alertRef = database.reference.child("alerts").push()

        val alertData = hashMapOf<String, Any>(
            "userId" to userId,
            "deviceId" to deviceId,
            "type" to "SIM_CHANGED",
            "timestamp" to System.currentTimeMillis(),
            "details" to hashMapOf(
                "simChange" to hashMapOf(
                    "oldICCID" to oldIccid,
                    "newICCID" to newIccid
                )
            )
        )

        alertRef.setValue(alertData)
            .addOnSuccessListener {
                Log.d(TAG, "SIM change alert created")

                // Start security response
                startSecurityResponse(context, alertRef.key)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to create alert", e)
            }
    }

    private fun startSecurityResponse(context: Context, sessionId: String?) {
        // Start location tracking
        if (sessionId != null) {
            LocationTrackingService.start(context, sessionId, isAlert = true)
        }

        // Start alarm if enabled
        val preferenceManager = PreferenceManager.getInstance(context)
        if (preferenceManager.isAlarmEnabled()) {
            // TODO: Start AlarmService
        }

        // Show fake power-off screen if enabled
        if (preferenceManager.isFakePoweroffEnabled()) {
            // TODO: Start FakePowerOffActivity
        }
    }

    private fun scheduleSimCheckWithDelay(context: Context, delayMs: Long) {
        // TODO: Use WorkManager to schedule delayed check
        // This prevents false alarms from temporary SIM removal for battery swap
    }
}
