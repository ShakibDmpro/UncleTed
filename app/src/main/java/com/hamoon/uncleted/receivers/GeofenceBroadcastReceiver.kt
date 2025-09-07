package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.hamoon.uncleted.services.PanicActionService

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            Log.e("GeofenceReceiver", "Geofence event error: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Log.w("GeofenceReceiver", "Device has EXITED the safe zone geofence!")
            // THE FIX IS HERE: Use the full path PanicActionService.Severity
            PanicActionService.trigger(
                context,
                "GEOFENCE_EXIT",
                PanicActionService.Severity.LOW
            )
        }
    }
}