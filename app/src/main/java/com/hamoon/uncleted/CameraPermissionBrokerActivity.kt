package com.hamoon.uncleted

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.hamoon.uncleted.services.PanicActionService

/**
 * A short-lived, transparent activity used to satisfy Android's background launch restrictions
 * for sensitive permissions like the camera.
 *
 * How it works:
 * 1. PanicActionService (running in the background) needs to use the camera.
 * 2. It starts this activity with the original panic reason ("INTRUDER_SELFIE", etc.).
 * 3. This activity's launch briefly brings the app to the foreground.
 * 4. In onCreate(), it immediately starts the PanicActionService again, but with a flag
 *    indicating that the foreground brokerage is complete.
 * 5. The service can now access the camera.
 * 6. This activity has done its job; finish immediately.
 */
class CameraPermissionBrokerActivity : AppCompatActivity() {

    private val TAG = "CameraBrokerActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Broker activity started.")

        // Immediately forward the original intent to the service, but add a flag
        // to prevent an infinite loop.
        val originalReason = intent.getStringExtra("REASON")
        // ### FIX: Retrieve the severity from the intent as well. ###
        val originalSeverity = intent.getStringExtra("SEVERITY")

        if (originalReason != null && originalSeverity != null) {
            val serviceIntent = Intent(this, PanicActionService::class.java).apply {
                putExtra("REASON", originalReason)
                // ### FIX: Pass the original severity along to the new service intent. ###
                putExtra("SEVERITY", originalSeverity)
                // This flag tells the service it has been brokered and can proceed.
                putExtra("IS_BROKERED", true)
            }
            startForegroundService(serviceIntent)
            Log.d(TAG, "Re-launching PanicActionService with reason: $originalReason, severity: $originalSeverity and IS_BROKERED flag.")
        } else {
            Log.e(TAG, "Broker activity started without a REASON or SEVERITY extra.")
        }

        // This activity has done its job; finish immediately.
        finish()
    }
}