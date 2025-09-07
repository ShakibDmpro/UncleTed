package com.hamoon.uncleted.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

object CameraHandler {
    private const val TAG = "CameraHandler"
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

    private suspend fun getCameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
        ProcessCameraProvider.getInstance(context).addListener({
            try {
                if (continuation.isActive) {
                    continuation.resume(ProcessCameraProvider.getInstance(context).get())
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.cancel(e)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    suspend fun takePhoto(context: Context, lifecycleOwner: LifecycleOwner, lensFacing: Int): File? = withContext(Dispatchers.Main) {
        return@withContext try {
            val cameraProvider = getCameraProvider(context)
            val imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

            val photoFile = File(
                context.filesDir,
                "IMG_${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())}.jpg"
            )

            suspendCancellableCoroutine { continuation ->
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            Log.i(TAG, "Photo capture succeeded: ${outputFileResults.savedUri}")
                            if (continuation.isActive) continuation.resume(photoFile)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not take photo", e)
            null
        } finally {
            getCameraProvider(context).unbindAll()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun recordVideo(context: Context, lifecycleOwner: LifecycleOwner, durationSeconds: Int, lensFacing: Int): File? = withContext(Dispatchers.Main) {
        return@withContext try {
            val cameraProvider = getCameraProvider(context)

            val qualitySelector = QualitySelector.from(Quality.HIGHEST, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, videoCapture)

            val videoFile = File(
                context.filesDir,
                "VID_${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())}.mp4"
            )

            suspendCancellableCoroutine { continuation ->
                val recording = videoCapture.output.prepareRecording(context, FileOutputOptions.Builder(videoFile).build())
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                        when (recordEvent) {
                            is VideoRecordEvent.Start -> {
                                Log.i(TAG, "Video recording started.")
                            }
                            is VideoRecordEvent.Finalize -> {
                                if (recordEvent.hasError()) {
                                    Log.e(TAG, "Video capture error: ${recordEvent.error}", recordEvent.cause)
                                    if (continuation.isActive) continuation.resume(null)
                                } else {
                                    Log.i(TAG, "Video capture succeeded: ${recordEvent.outputResults.outputUri}")
                                    if (continuation.isActive) continuation.resume(videoFile)
                                }
                            }
                        }
                    }

                // Stop recording after the specified duration
                launch {
                    delay(durationSeconds * 1000L)
                    recording.stop()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not record video", e)
            null
        } finally {
            getCameraProvider(context).unbindAll()
        }
    }
}