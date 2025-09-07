package com.hamoon.uncleted.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.workers.TripwireWorker
import java.util.concurrent.TimeUnit

object TripwireManager {

    private const val WORK_TAG = "uncle_ted_tripwire_work"
    private const val TAG = "TripwireManager"

    fun scheduleOrCancelTripwire(context: Context) {
        if (SecurityPreferences.isTripwireEnabled(context)) {
            // If the feature is enabled, record the current time as a check-in and arm the worker.
            SecurityPreferences.setLastTripwireCheckIn(context, System.currentTimeMillis()) // REVISED: Typo fix
            armTripwire(context)
        } else {
            // If disabled, cancel any pending worker and clear the check-in time.
            cancelTripwire(context)
            SecurityPreferences.setLastTripwireCheckIn(context, 0L) // REVISED: Typo fix
        }
    }

    private fun armTripwire(context: Context) {
        if (!SecurityPreferences.isTripwireEnabled(context)) {
            Log.d(TAG, "Tripwire is disabled, not arming.")
            return
        }

        val workManager = WorkManager.getInstance(context)
        val durationHours = SecurityPreferences.getTripwireDuration(context).toLong()

        Log.d(TAG, "Arming tripwire. Device will be wiped in $durationHours hours if no check-in occurs.")

        val tripwireWorkRequest = OneTimeWorkRequestBuilder<TripwireWorker>()
            .setInitialDelay(durationHours, TimeUnit.HOURS)
            .build()

        // Use REPLACE to ensure only one tripwire is active.
        workManager.enqueueUniqueWork(
            WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            tripwireWorkRequest
        )
    }

    private fun cancelTripwire(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_TAG)
        Log.i(TAG, "Tripwire worker cancelled.")
    }

    fun checkIn(context: Context) {
        if (!SecurityPreferences.isTripwireEnabled(context)) {
            return
        }
        Log.i(TAG, "Network connection detected. Checking in and resetting tripwire timer.")
        // Cancel the current worker and schedule a new one from this point.
        cancelTripwire(context)
        armTripwire(context)
        SecurityPreferences.setLastTripwireCheckIn(context, System.currentTimeMillis()) // REVISED: Typo fix
    }

    fun scheduleFromLastCheckIn(context: Context) { // REVISED: Typo fix
        if (!SecurityPreferences.isTripwireEnabled(context)) {
            return
        }

        val lastCheckIn = SecurityPreferences.getLastTripwireCheckIn(context) // REVISED: Typo fix
        if (lastCheckIn == 0L) {
            // No previous check-in, arm with full duration
            armTripwire(context)
            return
        }

        val durationMillis = TimeUnit.HOURS.toMillis(SecurityPreferences.getTripwireDuration(context).toLong())
        val deadline = lastCheckIn + durationMillis
        val remainingMillis = deadline - System.currentTimeMillis()

        if (remainingMillis <= 0) {
            // Deadline has passed while the device was off. Trigger wipe immediately.
            Log.e(TAG, "Tripwire deadline passed during device offline period. Triggering immediate wipe.")
            val intent = Intent(context, PanicActionService::class.java).apply {
                putExtra("REASON", "TRIPWIRE_WIPE")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startForegroundService(intent)
            return
        }

        Log.d(TAG, "Re-arming tripwire from last check-in. Remaining time: ${remainingMillis / 1000 / 60} minutes.")

        val workManager = WorkManager.getInstance(context)
        val tripwireWorkRequest = OneTimeWorkRequestBuilder<TripwireWorker>()
            .setInitialDelay(remainingMillis, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            tripwireWorkRequest
        )
    }
}