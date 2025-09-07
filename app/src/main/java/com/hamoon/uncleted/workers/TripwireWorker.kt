package com.hamoon.uncleted.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.RootActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TripwireWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val tag = "TripwireWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.e(tag, "Tripwire timer expired! Device has been offline for too long.")

        // If the root firewall feature is enabled, activate it first.
        if (SecurityPreferences.isFirewallTripwireEnabled(applicationContext)) {
            Log.w(tag, "Firewall Tripwire enabled. Activating firewall before wipe.")
            RootActions.blockAllNetworkTraffic(applicationContext)
        }

        Log.e(tag, "Initiating TRIPWIRE_WIPE panic.")
        PanicActionService.trigger(applicationContext, "TRIPWIRE_WIPE", PanicActionService.Severity.CRITICAL)

        return@withContext Result.success()
    }
}