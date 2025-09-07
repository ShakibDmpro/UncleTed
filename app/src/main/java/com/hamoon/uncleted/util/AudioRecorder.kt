package com.hamoon.uncleted.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object AudioRecorder {
    private const val TAG = "AudioRecorder"
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

    suspend fun recordAudio(context: Context, durationSeconds: Int): File? {
        if (!PermissionUtils.hasRecordAudioPermission(context)) {
            Log.e(TAG, "RECORD_AUDIO permission not granted.")
            return null
        }

        val audioFile = File(
            context.filesDir,
            "AUD_${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())}.mp3"
        )

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            Log.i(TAG, "Ambient audio recording started. Duration: ${durationSeconds}s")
            delay(durationSeconds * 1000L)
            recorder.stop()
            Log.i(TAG, "Ambient audio recording finished. File: ${audioFile.absolutePath}")
            audioFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record audio", e)
            null
        } finally {
            recorder.release()
        }
    }
}