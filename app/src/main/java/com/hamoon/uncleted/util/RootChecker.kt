package com.hamoon.uncleted.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootChecker {

    private const val TAG = "RootChecker"

    @Volatile
    private var isRooted: Boolean? = null

    suspend fun isDeviceRooted(): Boolean {
        if (isRooted != null) {
            return isRooted as Boolean
        }

        return withContext(Dispatchers.IO) {
            if (isRooted != null) {
                return@withContext isRooted as Boolean
            }

            Log.d(TAG, "Starting comprehensive root detection...")
            val check = checkRootMethod1() || checkRootMethod2() || checkRootMethod3() || checkRootMethod4()
            isRooted = check
            Log.d(TAG, "Root detection complete. Result: $check")
            check
        }
    }

    private fun checkRootMethod1(): Boolean {
        return try {
            val buildTags = android.os.Build.TAGS
            val hasTestKeys = buildTags != null && buildTags.contains("test-keys")
            Log.d(TAG, "Method 1 - Build tags check: $hasTestKeys")
            hasTestKeys
        } catch (e: Exception) {
            Log.d(TAG, "Method 1 failed: ${e.message}")
            false
        }
    }

    private fun checkRootMethod2(): Boolean {
        return try {
            val paths = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su",
                "/su/bin/su",
                "/system/usr/we-need-root/",
                "/data/app/eu.chainfire.supersu-*.apk",
                // ### THE FIX IS HERE: These two lines are removed as they cause false positives ###
                // "/system/xbin/which",
                // "/system/bin/which",
                "/system/etc/init.d/99SuperSUDaemon",
                "/dev/com.koushikdutta.superuser.daemon/",
                "/system/app/SuperSU",
                "/system/app/SuperSU.apk"
            )

            var found = false
            for (path in paths) {
                if (File(path).exists()) {
                    Log.d(TAG, "Method 2 - Found root file: $path")
                    found = true
                    break
                }
            }
            Log.d(TAG, "Method 2 - Binary/file check: $found")
            found
        } catch (e: Exception) {
            Log.d(TAG, "Method 2 failed: ${e.message}")
            false
        }
    }

    private fun checkRootMethod3(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val result = process.inputStream.bufferedReader().use { reader ->
                val line = reader.readLine()
                line != null && line.trim().isNotEmpty()
            }
            Log.d(TAG, "Method 3 - which su check: $result")
            result
        } catch (e: Exception) {
            Log.d(TAG, "Method 3 failed (expected): ${e.message}")
            false
        } finally {
            process?.destroy()
        }
    }

    private suspend fun checkRootMethod4(): Boolean {
        return try {
            Log.d(TAG, "Method 4 - Attempting su command execution...")
            val result = RootExecutor.run("id")
            val hasRootUid = result.isSuccess && result.output.any { it.contains("uid=0(root)") }
            Log.d(TAG, "Method 4 - su execution check: $hasRootUid")
            hasRootUid
        } catch (e: Exception) {
            Log.d(TAG, "Method 4 failed (expected on non-rooted devices): ${e.message}")
            false
        }
    }

    fun clearCache() {
        isRooted = null
        Log.d(TAG, "Root detection cache cleared")
    }
}