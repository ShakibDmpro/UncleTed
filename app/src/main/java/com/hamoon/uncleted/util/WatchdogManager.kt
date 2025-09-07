// ================================================================================
// ### FILE: app/src/main/java/com/hamoon/uncleted/util/WatchdogManager.kt
// ================================================================================
package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.workers.WatchdogWorker
import java.util.concurrent.TimeUnit

object WatchdogManager {

    private const val WORK_TAG = "uncle_ted_watchdog_work"
    private const val TAG = "WatchdogManager"

    fun scheduleOrCancelWatchdog(context: Context) {
        val workManager = WorkManager.getInstance(context)

        if (SecurityPreferences.isWatchdogModeEnabled(context)) {
            val interval = SecurityPreferences.getWatchdogInterval(context).toLong()
            Log.d(TAG, "Watchdog mode is enabled. Scheduling periodic work with interval: $interval minutes.")

            val watchdogWorkRequest = PeriodicWorkRequestBuilder<WatchdogWorker>(
                interval, TimeUnit.MINUTES
            ).build()

            workManager.enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.REPLACE, // Use REPLACE to update the interval if it changes
                watchdogWorkRequest
            )
            Log.i(TAG, "Watchdog worker enqueued successfully.")
        } else {
            Log.d(TAG, "Watchdog mode is disabled. Cancelling any existing work.")
            workManager.cancelUniqueWork(WORK_TAG)
            Log.i(TAG, "Watchdog worker cancelled.")
        }
    }
}