package com.antitheft.securepower.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground service for continuous location tracking during security alerts
 */
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_NORMAL = 30000L // 30 seconds
        private const val UPDATE_INTERVAL_ALERT = 10000L // 10 seconds during alert
        private const val FASTEST_INTERVAL = 5000L // 5 seconds

        fun start(context: Context, sessionId: String, isAlert: Boolean = false) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                putExtra("session_id", sessionId)
                putExtra("is_alert", isAlert)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var wakeLock: PowerManager.WakeLock
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var sessionId: String? = null
    private var isAlert: Boolean = false
    private var locationUpdateInterval = UPDATE_INTERVAL_NORMAL

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create notification channel
        createNotificationChannel()

        // Acquire wake lock to keep service running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SecurePower:LocationTracking"
        )
        wakeLock.acquire(24 * 60 * 60 * 1000L) // 24 hours max

        Log.d(TAG, "Location tracking service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionId = intent?.getStringExtra("session_id")
        isAlert = intent?.getBooleanExtra("is_alert", false) ?: false

        locationUpdateInterval = if (isAlert) {
            UPDATE_INTERVAL_ALERT
        } else {
            UPDATE_INTERVAL_NORMAL
        }

        // Start foreground with notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Start location updates
        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        // Check permissions
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            stopSelf()
            return
        }

        // Configure location request
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            locationUpdateInterval
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            setMaxUpdateDelayMillis(locationUpdateInterval * 2)
        }.build()

        // Define location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "Location not available")
                }
            }
        }

        // Request location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        Log.d(TAG, "Location updates started (interval: ${locationUpdateInterval}ms)")
    }

    private fun handleLocationUpdate(location: Location) {
        Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")

        // Upload to Firebase
        sessionId?.let { session ->
            uploadLocationToFirebase(session, location)
        }
    }

    private fun uploadLocationToFirebase(sessionId: String, location: Location) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "User not authenticated")
            return
        }

        val database = FirebaseDatabase.getInstance()
        val timestamp = System.currentTimeMillis()

        val locationData = hashMapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "accuracy" to location.accuracy,
            "timestamp" to timestamp,
            "speed" to if (location.hasSpeed()) location.speed else 0f,
            "bearing" to if (location.hasBearing()) location.bearing else 0f,
            "altitude" to if (location.hasAltitude()) location.altitude else 0.0,
            "batteryLevel" to getBatteryLevel(),
            "connectionType" to getConnectionType()
        )

        // Upload to tracking session
        database.reference
            .child("tracking")
            .child(sessionId)
            .child("locations")
            .child(timestamp.toString())
            .setValue(locationData)
            .addOnSuccessListener {
                Log.d(TAG, "Location uploaded to Firebase")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to upload location", e)
                // Store locally for later sync if needed
            }

        // Also update device's last known location
        val deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )

        database.reference
            .child("devices")
            .child(deviceId)
            .child("lastLocation")
            .setValue(
                hashMapOf(
                    "lat" to location.latitude,
                    "lng" to location.longitude,
                    "accuracy" to location.accuracy,
                    "timestamp" to timestamp
                )
            )
    }

    private fun getBatteryLevel(): Int {
        val batteryIntent = registerReceiver(
            null,
            android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level != -1 && scale != -1) {
            (level.toFloat() / scale.toFloat() * 100).toInt()
        } else {
            100
        }
    }

    private fun getConnectionType(): String {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "none"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "none"

        return when {
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }

    private fun createNotification(): Notification {
        val title = if (isAlert) "Security Alert - Location Tracking Active" else "Location Tracking"
        val text = "SecurePower is tracking your device location"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous location tracking for security"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop location updates
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Release wake lock
        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        // Cancel coroutines
        serviceJob.cancel()

        Log.d(TAG, "Location tracking service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
