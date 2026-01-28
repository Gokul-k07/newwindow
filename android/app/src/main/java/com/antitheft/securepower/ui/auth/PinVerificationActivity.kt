package com.antitheft.securepower.ui.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antitheft.securepower.auth.AuthManager
import com.antitheft.securepower.auth.AuthResult

/**
 * PIN Verification Dialog Activity
 * Shown when power button is pressed to authenticate user
 */
class PinVerificationActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = AuthManager.getInstance(this)

        setContent {
            MaterialTheme {
                PinVerificationScreen(
                    onPinEntered = { pin -> handlePinVerification(pin) },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun handlePinVerification(pin: String) {
        when (val result = authManager.verifyPin(pin)) {
            is AuthResult.Success -> {
                // Authentication successful, allow power off
                setResult(RESULT_OK)
                finish()
            }
            is AuthResult.Failed -> {
                // Show error, allow retry
                // TODO: Show error message
            }
            is AuthResult.FailedAtThreshold -> {
                // Show warning about triggering alert
                // TODO: Show warning
            }
            is AuthResult.FailedWithAlert -> {
                // Trigger security response
                triggerSecurityResponse()
                finish()
            }
            is AuthResult.LockedOut -> {
                // Show lockout message
                // TODO: Show lockout duration
            }
            is AuthResult.NotConfigured -> {
                // PIN not set up, shouldn't happen
                finish()
            }
        }
    }

    private fun triggerSecurityResponse() {
        // TODO: Implement full security response
        // 1. Start LocationTrackingService
        // 2. Start AlarmService
        // 3. Show FakePowerOffActivity
        // 4. Create alert in Firebase
    }
}

@Composable
fun PinVerificationScreen(
    onPinEntered: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Enter PIN to Power Off",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // TODO: Implement PIN input UI with number pad

            Button(
                onClick = { onPinEntered(pin) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verify")
            }

            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}
