package com.antitheft.securepower.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages app preferences and settings
 */
class PreferenceManager private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "securepower_prefs"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_ALARM_ENABLED = "alarm_enabled"
        private const val KEY_AUTO_ENABLE_INTERNET = "auto_enable_internet"
        private const val KEY_AUTO_ENABLE_LOCATION = "auto_enable_location"
        private const val KEY_FAKE_POWEROFF_ENABLED = "fake_poweroff_enabled"
        private const val KEY_SIM_ALERT_ENABLED = "sim_alert_enabled"
        private const val KEY_ORIGINAL_SIM_ICCID = "original_sim_iccid"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_USER_ID = "user_id"

        @Volatile
        private var instance: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return instance ?: synchronized(this) {
                instance ?: PreferenceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Protection settings
    fun isProtectionEnabled(): Boolean =
        sharedPreferences.getBoolean(KEY_PROTECTION_ENABLED, false)

    fun setProtectionEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
    }

    // Alarm settings
    fun isAlarmEnabled(): Boolean =
        sharedPreferences.getBoolean(KEY_ALARM_ENABLED, true)

    fun setAlarmEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ALARM_ENABLED, enabled).apply()
    }

    // Auto-enable internet
    fun isAutoEnableInternetEnabled(): Boolean =
        sharedPreferences.getBoolean(KEY_AUTO_ENABLE_INTERNET, true)

    fun setAutoEnableInternet(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_ENABLE_INTERNET, enabled).apply()
    }

    // Auto-enable location
    fun isAutoEnableLocationEnabled(): Boolean =
        sharedPreferences.getBoolean(KEY_AUTO_ENABLE_LOCATION, true)

    fun setAutoEnableLocation(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_ENABLE_LOCATION, enabled).apply()
    }

    // Fake power-off screen
    fun isFakePoweroffEnabled(): Boolean =
        sharedPreferences.getBoolean(KEY_FAKE_POWEROFF_ENABLED, true)

    fun setFakePoweroffEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_FAKE_POWEROFF_ENABLED, enabled).apply()
    }

    // SIM change alert
    fun isSimAlertEnabled(): Boolean =
        sharedPreferences.getBoolean(KEY_SIM_ALERT_ENABLED, true)

    fun setSimAlertEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SIM_ALERT_ENABLED, enabled).apply()
    }

    // Original SIM ICCID
    fun getOriginalSimIccid(): String? =
        sharedPreferences.getString(KEY_ORIGINAL_SIM_ICCID, null)

    fun setOriginalSimIccid(iccid: String) {
        sharedPreferences.edit().putString(KEY_ORIGINAL_SIM_ICCID, iccid).apply()
    }

    // Device ID
    fun getDeviceId(): String? =
        sharedPreferences.getString(KEY_DEVICE_ID, null)

    fun setDeviceId(deviceId: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    // User ID
    fun getUserId(): String? =
        sharedPreferences.getString(KEY_USER_ID, null)

    fun setUserId(userId: String) {
        sharedPreferences.edit().putString(KEY_USER_ID, userId).apply()
    }

    // Clear all preferences
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}
