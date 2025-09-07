package com.hamoon.uncleted.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.hamoon.uncleted.receivers.GeofenceBroadcastReceiver

object GeofenceHelper {
    private const val TAG = "GeofenceHelper"
    private const val GEOFENCE_ID = "UNCLE_TED_SAFE_ZONE"
    private const val GEOFENCE_RADIUS_METERS = 100f // 100 meters

    private val geofencingClient by lazy { LocationServices.getGeofencingClient(context) }
    private lateinit var context: Context

    fun initialize(appContext: Context) {
        context = appContext
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    fun addGeofence(lat: Double, lon: Double) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot add geofence, location permission missing.")
            return
        }
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(lat, lon, GEOFENCE_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)?.run {
            addOnSuccessListener { Log.i(TAG, "Geofence added successfully.") }
            addOnFailureListener { Log.e(TAG, "Failed to add geofence.", it) }
        }
    }

    fun removeGeofence() {
        geofencingClient.removeGeofences(geofencePendingIntent)?.run {
            addOnSuccessListener { Log.i(TAG, "Geofence removed successfully.") }
            addOnFailureListener { Log.e(TAG, "Failed to remove geofence.", it) }
        }
    }
}