package com.example.securelock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent

class AppLockService : AccessibilityService() {

    private var currentPackage: String = ""
    private var screenOffReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Register receiver to detect Screen Off (Phone Lock)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    // Mode 1 & 2: Re-lock everything when phone is locked
                    // (You could optionally persist Mode 2, but usually screen off = lock)
                    LockManager.clear()
                }
            }
        }
        registerReceiver(screenOffReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // 1. Ignore self
            if (packageName == this.packageName) return

            // 2. Get User Preference for Timeout Mode
            val prefs = getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE)
            val mode = prefs.getInt("LOCK_TIMEOUT_MODE", LockManager.MODE_INSTANT)

            // 3. Handle App Switching Logic based on Mode
            if (currentPackage != packageName) {
                // If Mode is "Instant", clear unlocks immediately upon leaving an app
                if (mode == LockManager.MODE_INSTANT) {
                    LockManager.clear()
                }
                currentPackage = packageName
            }

            // 4. Check if App is Protected
            if (isAppLocked(packageName)) {

                // 5. Check if currently unlocked (respecting time/mode)
                if (LockManager.isUnlocked(packageName, mode)) {
                    return // Allow access
                }

                // 6. Launch Lock Screen
                val intent = Intent(this, LockActivity::class.java)
                intent.putExtra("LOCKED_PACKAGE", packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Clean up receiver
        if (screenOffReceiver != null) {
            unregisterReceiver(screenOffReceiver)
            screenOffReceiver = null
        }
        return super.onUnbind(intent)
    }

    private fun isAppLocked(packageName: String): Boolean {
        val prefs = getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE)
        val lockedApps = prefs.getStringSet("LOCKED_APPS", emptySet()) ?: emptySet()
        return lockedApps.contains(packageName)
    }

    override fun onInterrupt() {}
}