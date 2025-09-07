package com.hamoon.uncleted.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A singleton object to track the application's lifecycle state (foreground/background).
 * This is crucial for enabling/disabling heavy background tasks like AI analysis
 * only when the app's UI is visible.
 */
object AppLifecycleManager {

    private var startedActivities = 0
    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    fun onActivityStarted() {
        if (startedActivities == 0) {
            _isAppInForeground.value = true
        }
        startedActivities++
    }

    fun onActivityStopped() {
        startedActivities--
        if (startedActivities == 0) {
            _isAppInForeground.value = false
        }
    }
}