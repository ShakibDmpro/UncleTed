// ================================================================================
// ### FILE: app/src/main/java/com/hamoon/uncleted/workers/WatchdogWorker.kt
// ================================================================================
package com.hamoon.uncleted.workers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.telephony.SmsManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.EmailSender
import com.hamoon.uncleted.util.PermissionUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WatchdogWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val TAG = "WatchdogWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "WatchdogWorker started.")
        val context = applicationContext
        val emergencyContact = SecurityPreferences.getEmergencyContact(context)

        if (emergencyContact.isNullOrEmpty()) {
            Log.e(TAG, "No emergency contact set. Aborting watchdog work.")
            return Result.failure()
        }

        val batteryLevel = getBatteryLevel(context)
        val location = getCurrentLocation(context)

        val locationString = if (location != null) {
            "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
        } else {
            "Location not available."
        }

        val batteryString = batteryLevel?.toString() ?: "N/A"

        val subject = context.getString(R.string.watchdog_status_message)
        val body = context.getString(R.string.watchdog_status_body, batteryString, locationString)

        if (emergencyContact.contains("@")) {
            val success = EmailSender.sendEmail(context, emergencyContact, subject, body)
            return if (success) Result.success() else Result.retry()
        } else {
            return try {
                val smsManager = context.getSystemService(SmsManager::class.java)
                smsManager.sendTextMessage(emergencyContact, null, body, null, null)
                Log.i(TAG, "Watchdog SMS sent to $emergencyContact")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send watchdog SMS.", e)
                Result.retry()
            }
        }
    }

    private suspend fun getCurrentLocation(context: Context): Location? = suspendCancellableCoroutine { continuation ->
        if (!PermissionUtils.hasLocationPermissions(context)) {
            Log.e(TAG, "Location permissions not granted.")
            if (continuation.isActive) continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationTokenSource = CancellationTokenSource()

        continuation.invokeOnCancellation { cancellationTokenSource.cancel() }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                Log.d(TAG, "Location retrieved: ${location?.latitude}, ${location?.longitude}")
                if (continuation.isActive) continuation.resume(location)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location", e)
                if (continuation.isActive) continuation.resume(null)
            }
    }

    private fun getBatteryLevel(context: Context): Int? {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, iFilter)
        return batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            (level.toFloat() / scale.toFloat() * 100).toInt()
        }
    }
}