package com.hamoon.uncleted.util

import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocationTracker {

    private const val TAG = "LocationTracker"

    data class LocationInfo(
        val location: Location,
        val address: String? = null,
        val accuracy: Float,
        val timestamp: Long
    )

    suspend fun getCurrentLocationDetailed(context: Context): LocationInfo? {
        if (!PermissionUtils.hasLocationPermissions(context)) {
            Log.e(TAG, "Location permissions not granted")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val cancellationTokenSource = CancellationTokenSource()

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }

            try {
                // Try to get last known location first
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                    if (lastLocation != null && isLocationRecent(lastLocation)) {
                        val locationInfo = LocationInfo(
                            location = lastLocation,
                            accuracy = lastLocation.accuracy,
                            timestamp = lastLocation.time
                        )
                        if (continuation.isActive) {
                            continuation.resume(locationInfo)
                            return@addOnSuccessListener
                        }
                    }

                    // If no recent location, request fresh one
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                        .setWaitForAccurateLocation(false)
                        .setMaxUpdateDelayMillis(10000)
                        .setMaxUpdates(1)
                        .build()

                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            val location = locationResult.lastLocation
                            if (location != null && continuation.isActive) {
                                val locationInfo = LocationInfo(
                                    location = location,
                                    accuracy = location.accuracy,
                                    timestamp = location.time
                                )
                                continuation.resume(locationInfo)
                            }
                        }
                    }

                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    )

                    // Set timeout
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }, 15000) // 15 second timeout

                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get location", e)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }

            } catch (e: SecurityException) {
                Log.e(TAG, "Location permission denied", e)
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    private fun isLocationRecent(location: Location): Boolean {
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        return location.time > fiveMinutesAgo
    }

    fun formatLocationForSms(locationInfo: LocationInfo): String {
        return "Location: ${locationInfo.location.latitude},${locationInfo.location.longitude} " +
                "(±${locationInfo.accuracy.toInt()}m) " +
                "Maps: https://maps.google.com/maps?q=${locationInfo.location.latitude},${locationInfo.location.longitude}"
    }

    fun formatLocationForEmail(locationInfo: LocationInfo): String {
        return """
            GPS Coordinates: ${locationInfo.location.latitude}, ${locationInfo.location.longitude}
            Accuracy: ±${locationInfo.accuracy.toInt()} meters
            Altitude: ${if (locationInfo.location.hasAltitude()) "${locationInfo.location.altitude}m" else "Unknown"}
            Speed: ${if (locationInfo.location.hasSpeed()) "${locationInfo.location.speed} m/s" else "Unknown"}
            Bearing: ${if (locationInfo.location.hasBearing()) "${locationInfo.location.bearing}°" else "Unknown"}
            Timestamp: ${java.util.Date(locationInfo.timestamp)}
            
            Google Maps Link: https://www.google.com/maps/search/?api=1&query=${locationInfo.location.latitude},${locationInfo.location.longitude}
        """.trimIndent()
    }
}