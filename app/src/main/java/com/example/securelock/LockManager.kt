package com.example.securelock

import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton to manage temporary unlock state with support for different timeout modes.
 */
object LockManager {
    // Maps package name to the timestamp (ms) when it was unlocked
    private val unlockedPackages = ConcurrentHashMap<String, Long>()

    // Configuration Modes
    const val MODE_INSTANT = 0
    const val MODE_SCREEN_OFF = 1
    const val MODE_10_MIN = 2

    private const val TEN_MINUTES_MS = 10 * 60 * 1000L

    /**
     * Checks if an app is currently considered "unlocked" based on the selected mode.
     */
    fun isUnlocked(packageName: String, mode: Int): Boolean {
        if (!unlockedPackages.containsKey(packageName)) return false

        return when (mode) {
            MODE_10_MIN -> {
                val unlockTime = unlockedPackages[packageName] ?: 0L
                val elapsed = System.currentTimeMillis() - unlockTime
                if (elapsed < TEN_MINUTES_MS) {
                    true
                } else {
                    // Expired
                    unlockedPackages.remove(packageName)
                    false
                }
            }
            // For Instant and Screen Off, presence in the map implies unlocked
            // (Clearing the map handles the re-locking logic for these modes)
            else -> true
        }
    }

    fun unlock(packageName: String) {
        unlockedPackages[packageName] = System.currentTimeMillis()
    }

    fun clear() {
        unlockedPackages.clear()
    }

    fun clearPackage(packageName: String) {
        unlockedPackages.remove(packageName)
    }
}