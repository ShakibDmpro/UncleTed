package com.hamoon.uncleted.util

import android.content.Context
import com.hamoon.uncleted.data.SecurityPreferences

/**
 * --- COMPILATION FIX ---
 * This object is now a simple, safe wrapper around the logging logic
 * which has been moved into SecurityPreferences to fix compilation errors
 * and improve encapsulation.
 */
object EventLogger {

    fun log(context: Context, message: String) {
        // Delegate the logging action to SecurityPreferences.
        SecurityPreferences.logEvent(context, message)
    }

    fun getLogs(context: Context): List<String> {
        // Delegate the log retrieval to SecurityPreferences.
        return SecurityPreferences.getLogs(context)
    }
}