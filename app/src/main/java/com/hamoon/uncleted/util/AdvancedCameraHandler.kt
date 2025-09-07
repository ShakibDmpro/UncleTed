// ================================================================================
// ### FILE: app/src/main/java/com/hamoon/uncleted/util/AdvancedCameraHandler.kt
// ================================================================================
package com.hamoon.uncleted.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.lifecycle.LifecycleOwner
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File

object AdvancedCameraHandler {

    private const val TAG = "AdvancedCameraHandler"

    data class CameraCapture(
        val frontPhoto: File?,
        val backPhoto: File?,
        val frontVideo: File?,
        val backVideo: File?,
        val location: Location?,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun performFullCapture(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        videoDurationSeconds: Int = 10
    ): CameraCapture = withContext(Dispatchers.IO) {

        if (SecurityPreferences.isStealthMediaCaptureEnabled(context)) {
            RootActions.suppressPrivacyIndicators(true)
        }

        try {
            val location = getCurrentLocation(context)

            // ### START: PRECISE FIX FOR CAMERA RACE CONDITION ###
            // All camera operations are now performed sequentially to prevent race conditions
            // where one operation unbinds the camera while another is trying to use it.
            // This ensures stability and reliable media capture.

            val frontPhoto = CameraHandler.takePhoto(
                context, lifecycleOwner, CameraSelector.LENS_FACING_FRONT
            )

            val backPhoto = if (hasBackCamera(context)) {
                CameraHandler.takePhoto(context, lifecycleOwner, CameraSelector.LENS_FACING_BACK)
            } else null

            val frontVideo = CameraHandler.recordVideo(
                context, lifecycleOwner, videoDurationSeconds, CameraSelector.LENS_FACING_FRONT
            )

            val backVideo = if (hasBackCamera(context)) {
                CameraHandler.recordVideo(
                    context, lifecycleOwner, videoDurationSeconds, CameraSelector.LENS_FACING_BACK
                )
            } else null

            // ### END: PRECISE FIX FOR CAMERA RACE CONDITION ###

            return@withContext CameraCapture(
                frontPhoto = frontPhoto,
                backPhoto = backPhoto,
                frontVideo = frontVideo,
                backVideo = backVideo,
                location = location
            )
        } finally {
            if (SecurityPreferences.isStealthMediaCaptureEnabled(context)) {
                // The system auto-restarts the camera server, so no explicit restore action is needed.
                // This log confirms the block's execution.
                Log.d(TAG, "Stealth media capture finished, privacy indicator state will be restored by system.")
            }
        }
    }

    private fun hasBackCamera(context: Context): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.any { cameraId ->
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for back camera", e)
            false
        }
    }

    private suspend fun getCurrentLocation(context: Context): Location? {
        return try {
            LocationTracker.getCurrentLocationDetailed(context)?.location
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }
}