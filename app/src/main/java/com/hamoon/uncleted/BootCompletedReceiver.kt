package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.MonitoringService
import com.hamoon.uncleted.util.TripwireManager
import com.hamoon.uncleted.util.WatchdogManager

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootCompletedReceiver", "Device booted. Checking if services should start.")

            // REVISED: Check the master protection switch to start the main monitoring service.
            if (SecurityPreferences.isProtectionEnabled(context)) {
                val serviceIntent = Intent(context, MonitoringService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
                Log.i("BootCompletedReceiver", "Started MonitoringService on boot.")
            }

            // Check and reschedule WatchdogWorker
            if (SecurityPreferences.isWatchdogModeEnabled(context)) {
                WatchdogManager.scheduleOrCancelWatchdog(context)
                Log.i("BootCompletedReceiver", "Rescheduled WatchdogWorker on boot.")
            }

            // Check and reschedule TripwireWorker from its last check-in time
            if (SecurityPreferences.isTripwireEnabled(context)) {
                TripwireManager.scheduleFromLastCheckIn(context)
                Log.i("BootCompletedReceiver", "Rescheduled TripwireWorker on boot.")
            }
        }
    }
}