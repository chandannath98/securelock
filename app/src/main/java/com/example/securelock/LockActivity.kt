package com.example.securelock

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity

class LockActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Identify which app we are protecting
        val lockedPackage = intent.getStringExtra("LOCKED_PACKAGE")

        // Unlock Action: Tell LockManager this package is safe, then close overlay
        fun performUnlock() {
            if (lockedPackage != null) {
                LockManager.unlock(lockedPackage)
            }
            finish()
        }

        // Check Preference for Biometric
        val prefs = getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE)
        val isBiometricEnabled = prefs.getBoolean("BIOMETRIC_ENABLED", true)

        // Trigger Biometric only if enabled
        if (isBiometricEnabled && BiometricHelper.isBiometricAvailable(this)) {
            BiometricHelper.showBiometricPrompt(
                activity = this,
                onSuccess = { performUnlock() },
                onError = { /* Fallback to PIN */ }
            )
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2196F3),
                    onPrimary = Color.White,
                    background = Color.White
                )
            ) {
                LockOverlayScreen(
                    onUnlock = { performUnlock() }
                )
            }
        }
    }

    override fun onBackPressed() {
        // Go Home instead of back to the locked app
        moveTaskToBack(true)
    }
}

@Composable
fun LockOverlayScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE)
    val savedPin = prefs.getString("USER_PIN", "1234")

    var pin by remember { mutableStateOf("") }

    LaunchedEffect(pin) {
        if (pin == savedPin) {
            onUnlock()
        } else if (pin.length == 4) {
            pin = "" // Reset on wrong PIN
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = "Locked",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("App Locked", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text("Enter PIN to open", color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        // PIN Dots
        Row(horizontalArrangement = Arrangement.Center) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                        .background(
                            if (index < pin.length) MaterialTheme.colorScheme.primary else Color.LightGray,
                            CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        PinKeypad(
            onKeyClick = { key ->
                if (pin.length < 4) pin += key
            },
            onDelete = {
                if (pin.isNotEmpty()) pin = pin.dropLast(1)
            }
        )
    }
}

@Composable
fun PinKeypad(onKeyClick: (String) -> Unit, onDelete: () -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "Del")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.chunked(3).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowKeys.forEach { key ->
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(80.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (key == "Del") onDelete()
                                else if (key.isNotEmpty()) onKeyClick(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "Del") {
                            Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = Color.Black)
                        } else if (key.isNotEmpty()) {
                            Text(key, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}