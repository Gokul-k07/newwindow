package com.antitheft.securepower

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
import com.antitheft.securepower.utils.PreferenceManager

/**
 * Main Activity - Entry point of the app
 */
class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = AuthManager.getInstance(this)
        preferenceManager = PreferenceManager.getInstance(this)

        setContent {
            MaterialTheme {
                MainScreen(
                    authManager = authManager,
                    preferenceManager = preferenceManager
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    authManager: AuthManager,
    preferenceManager: PreferenceManager
) {
    var isProtectionEnabled by remember {
        mutableStateOf(preferenceManager.isProtectionEnabled())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SecurePower",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Check if PIN is configured
            if (!authManager.isPinConfigured()) {
                Text("Please set up PIN in settings")
                // TODO: Navigate to setup screen
            } else {
                // Protection toggle
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Power-Off Protection",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Switch(
                                checked = isProtectionEnabled,
                                onCheckedChange = {
                                    isProtectionEnabled = it
                                    preferenceManager.setProtectionEnabled(it)
                                }
                            )
                        }

                        Text(
                            text = if (isProtectionEnabled) "Protection is active" else "Protection is disabled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isProtectionEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // TODO: Add more UI components
                // - Last location
                // - Alert history
                // - Quick actions
                // - Settings button
            }
        }
    }
}
