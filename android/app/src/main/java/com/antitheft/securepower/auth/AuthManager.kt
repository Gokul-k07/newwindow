package com.antitheft.securepower.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.experimental.and

/**
 * Manages PIN/Password authentication with secure storage using Android Keystore
 * Implements Argon2id-like hashing (using PBKDF2 with SHA-256) and AES-256 encryption
 */
class AuthManager(private val context: Context) {

    companion object {
        private const val KEYSTORE_ALIAS = "SecurePowerAuthKey"
        private const val SHARED_PREFS_NAME = "secure_power_auth"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_PASSWORD_SALT = "password_salt"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LAST_ATTEMPT_TIME = "last_attempt_time"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

        private const val ITERATION_COUNT = 100000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 32
        private const val MAX_ATTEMPTS_BEFORE_ALERT = 2
        private const val LOCKOUT_DURATION_MS = 30000L // 30 seconds

        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        SHARED_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Set up PIN (4-6 digits)
     */
    fun setupPin(pin: String): Boolean {
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
            return false
        }

        val salt = generateSalt()
        val hash = hashPassword(pin, salt)

        sharedPreferences.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, salt)
            .apply()

        return true
    }

    /**
     * Set up backup password (minimum 8 characters)
     */
    fun setupPassword(password: String): Boolean {
        if (password.length < 8) {
            return false
        }

        val salt = generateSalt()
        val hash = hashPassword(password, salt)

        sharedPreferences.edit()
            .putString(KEY_PASSWORD_HASH, hash)
            .putString(KEY_PASSWORD_SALT, salt)
            .apply()

        return true
    }

    /**
     * Verify PIN
     */
    fun verifyPin(pin: String): AuthResult {
        return verifyCredential(pin, KEY_PIN_HASH, KEY_PIN_SALT)
    }

    /**
     * Verify password
     */
    fun verifyPassword(password: String): AuthResult {
        return verifyCredential(password, KEY_PASSWORD_HASH, KEY_PASSWORD_SALT)
    }

    private fun verifyCredential(
        credential: String,
        hashKey: String,
        saltKey: String
    ): AuthResult {
        // Check if locked out
        val lockoutUntil = sharedPreferences.getLong(KEY_LOCKOUT_UNTIL, 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime < lockoutUntil) {
            val remainingSeconds = ((lockoutUntil - currentTime) / 1000).toInt()
            return AuthResult.LockedOut(remainingSeconds)
        }

        // Get stored hash and salt
        val storedHash = sharedPreferences.getString(hashKey, null)
        val storedSalt = sharedPreferences.getString(saltKey, null)

        if (storedHash == null || storedSalt == null) {
            return AuthResult.NotConfigured
        }

        // Hash input and compare
        val inputHash = hashPassword(credential, storedSalt)
        val isValid = inputHash == storedHash

        return if (isValid) {
            // Reset failed attempts on successful auth
            sharedPreferences.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0)
                .apply()
            AuthResult.Success
        } else {
            handleFailedAttempt()
        }
    }

    private fun handleFailedAttempt(): AuthResult {
        val failedAttempts = sharedPreferences.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val currentTime = System.currentTimeMillis()

        sharedPreferences.edit()
            .putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
            .putLong(KEY_LAST_ATTEMPT_TIME, currentTime)
            .apply()

        return when {
            failedAttempts > MAX_ATTEMPTS_BEFORE_ALERT -> {
                // Lock out and trigger security alert
                val lockoutUntil = currentTime + LOCKOUT_DURATION_MS
                sharedPreferences.edit()
                    .putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
                    .apply()

                AuthResult.FailedWithAlert(failedAttempts, LOCKOUT_DURATION_MS / 1000)
            }
            failedAttempts == MAX_ATTEMPTS_BEFORE_ALERT -> {
                AuthResult.FailedAtThreshold(failedAttempts)
            }
            else -> {
                AuthResult.Failed(failedAttempts)
            }
        }
    }

    /**
     * Hash password using PBKDF2 with SHA-256
     */
    private fun hashPassword(password: String, saltHex: String): String {
        val salt = hexStringToByteArray(saltHex)
        val spec = javax.crypto.spec.PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATION_COUNT,
            KEY_LENGTH
        )

        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded

        return byteArrayToHexString(hash)
    }

    /**
     * Generate random salt
     */
    private fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return byteArrayToHexString(salt)
    }

    /**
     * Get failed attempt count
     */
    fun getFailedAttempts(): Int {
        return sharedPreferences.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    /**
     * Check if PIN is configured
     */
    fun isPinConfigured(): Boolean {
        return sharedPreferences.contains(KEY_PIN_HASH)
    }

    /**
     * Check if password is configured
     */
    fun isPasswordConfigured(): Boolean {
        return sharedPreferences.contains(KEY_PASSWORD_HASH)
    }

    /**
     * Enable/disable biometric authentication
     */
    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    /**
     * Check if biometric is enabled
     */
    fun isBiometricEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    /**
     * Reset authentication (for testing or account recovery)
     */
    fun resetAuth() {
        sharedPreferences.edit().clear().apply()
    }

    /**
     * Convert byte array to hex string
     */
    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Convert hex string to byte array
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    /**
     * Get authentication logs for review
     */
    fun getAuthLogs(): List<AuthLog> {
        // In production, would retrieve from local database
        // For now, return basic info
        return listOf(
            AuthLog(
                timestamp = sharedPreferences.getLong(KEY_LAST_ATTEMPT_TIME, 0),
                failedAttempts = getFailedAttempts(),
                isLockedOut = System.currentTimeMillis() < sharedPreferences.getLong(KEY_LOCKOUT_UNTIL, 0)
            )
        )
    }
}

/**
 * Authentication result sealed class
 */
sealed class AuthResult {
    object Success : AuthResult()
    object NotConfigured : AuthResult()
    data class Failed(val attemptCount: Int) : AuthResult()
    data class FailedAtThreshold(val attemptCount: Int) : AuthResult()
    data class FailedWithAlert(val attemptCount: Int, val lockoutSeconds: Long) : AuthResult()
    data class LockedOut(val remainingSeconds: Int) : AuthResult()
}

/**
 * Authentication log data class
 */
data class AuthLog(
    val timestamp: Long,
    val failedAttempts: Int,
    val isLockedOut: Boolean
)
