// ================================================================================
// ### FILE: app/src/main/java/com/hamoon/uncleted/services/PanicActionService.kt
// ================================================================================
package com.hamoon.uncleted.services

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.hamoon.uncleted.CameraPermissionBrokerActivity
import com.hamoon.uncleted.LockScreenActivity
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.*
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class PanicActionService : LifecycleService() {

    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var sirenMediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private lateinit var cameraLifecycleOwner: FakeLifecycleOwner

    companion object {
        private const val TAG = "PanicActionService"
        private val lastTriggerTimestamps = ConcurrentHashMap<String, Long>()
        private const val TRIGGER_COOLDOWN_MS = 10000
        private const val NOTIFICATION_ID = 1001

        fun trigger(context: Context, reason: String, severity: Severity) {
            val now = System.currentTimeMillis()
            val lastTrigger = lastTriggerTimestamps[reason] ?: 0L
            if (now - lastTrigger < TRIGGER_COOLDOWN_MS) {
                Log.w(TAG, "Panic trigger for '$reason' throttled. Cooldown active.")
                return
            }
            lastTriggerTimestamps[reason] = now
            EventLogger.log(context, "Panic Triggered: $reason (Severity: ${severity.name})")

            val intent = Intent(context, PanicActionService::class.java).apply {
                putExtra("REASON", reason)
                putExtra("SEVERITY", severity.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        cameraLifecycleOwner = FakeLifecycleOwner()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "uncleted::PanicWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val reason = intent?.getStringExtra("REASON") ?: "UNKNOWN"
        val severity = try {
            Severity.valueOf(intent?.getStringExtra("SEVERITY") ?: "MEDIUM")
        } catch (e: Exception) {
            Severity.MEDIUM
        }
        val isBrokered = intent?.getBooleanExtra("IS_BROKERED", false) ?: false

        try {
            startForeground(NOTIFICATION_ID, NotificationHelper.createPanicNotification(this))
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start foreground service with full permissions", e)
            try {
                startForeground(NOTIFICATION_ID, NotificationHelper.createBasicNotification(this))
            } catch (e2: SecurityException) {
                Log.e(TAG, "Failed to start any foreground service, stopping", e2)
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        val needsCamera = severity in listOf(Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL)

        if (needsCamera && !isBrokered && !PermissionUtils.hasCameraPermission(this)) {
            Log.d(TAG, "Camera needed for reason '$reason' but no permission. Launching broker activity.")

            val brokerIntent = Intent(this, CameraPermissionBrokerActivity::class.java).apply {
                putExtra("REASON", reason)
                putExtra("SEVERITY", severity.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(brokerIntent)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Log.w(TAG, "Service started with reason: $reason, severity: $severity (Brokered: $isBrokered)")

        lifecycleScope.launch(serviceScope.coroutineContext) {
            try {
                withContext(Dispatchers.Main) {
                    cameraLifecycleOwner.start()
                }

                when (severity) {
                    Severity.LOW -> handleLowSeverityIncident(reason)
                    Severity.MEDIUM -> handleMediumSeverityIncident(reason)
                    Severity.HIGH -> handleHighSeverityIncident(reason)
                    Severity.CRITICAL -> handleCriticalIncident(reason)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing protocol for reason: $reason", e)
                EventLogger.log(this@PanicActionService, "ERROR: Protocol failed for $reason. Details: ${e.message}")
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun handleLowSeverityIncident(reason: String) {
        Log.i(TAG, "Handling LOW severity: $reason")
        val template = when (reason) {
            "GEOFENCE_EXIT" -> AdvancedEmailSender.getEmailTemplates()["DEVICE_MOVED"]!!
            else -> AdvancedEmailSender.EmailTemplate("Uncle Ted Alert: $reason", "Low severity event detected: $reason")
        }
        sendAlert(template)
    }

    private suspend fun handleMediumSeverityIncident(reason: String) {
        Log.w(TAG, "Handling MEDIUM severity: $reason")
        val capture = if (PermissionUtils.hasCameraPermission(this)) {
            AdvancedCameraHandler.performFullCapture(this, cameraLifecycleOwner, videoDurationSeconds = 5)
        } else {
            null
        }

        val template = when (reason) {
            "INTRUDER_SELFIE" -> AdvancedEmailSender.getEmailTemplates()["INTRUDER"]!!
            "SIM_CHANGED" -> AdvancedEmailSender.getEmailTemplates()["SIM_CHANGE"]!!
            "FAKE_SHUTDOWN" -> AdvancedEmailSender.getEmailTemplates()["SYSTEM_TAMPER"]!!
            else -> AdvancedEmailSender.getEmailTemplates()["GENERIC_MEDIUM"]!!
        }
        sendAlert(template, capture)

        if (reason == "INTRUDER_SELFIE" && SecurityPreferences.isSaveSelfieToStorageEnabled(this)) {
            capture?.frontPhoto?.let {
                val uri = FileUtils.saveImageToPictures(this, it)
                if (uri != null) NotificationHelper.showSelfieSavedNotification(this, uri)
            }
        }
    }

    private suspend fun handleHighSeverityIncident(reason: String) {
        Log.e(TAG, "Handling HIGH severity: $reason")
        val capture = if (PermissionUtils.hasCameraPermission(this)) {
            AdvancedCameraHandler.performFullCapture(this, cameraLifecycleOwner, videoDurationSeconds = 15)
        } else {
            null
        }

        val audioFile = if (SecurityPreferences.isAmbientAudioEnabled(this) && PermissionUtils.hasRecordAudioPermission(this)) {
            AudioRecorder.recordAudio(this, 30)
        } else {
            null
        }

        if (reason == "DURESS_PIN" && SecurityPreferences.isGpsSpoofingEnabled(this)) {
            RootActions.enableGpsSpoofing(this)
        }

        // ### START: PRECISE FIX FOR AI LOCKDOWN ACTION ###
        // Define the email template and perform the specific action for each high-severity reason.
        val template: AdvancedEmailSender.EmailTemplate
        when (reason) {
            "DURESS_PIN", "SHAKE_TRIGGERED" -> {
                template = AdvancedEmailSender.getEmailTemplates()["DURESS"]!!
                sendAlert(template, capture, audioFile)
                startSiren(30)
            }
            "UNINSTALL_ATTEMPT" -> {
                template = AdvancedEmailSender.getEmailTemplates()["SYSTEM_BREACH"]!!
                sendAlert(template, capture, audioFile)
            }
            "REMOTE_SIREN" -> {
                template = AdvancedEmailSender.getEmailTemplates()["REMOTE_ACTION"]!!
                sendAlert(template, capture, audioFile)
                startSiren(30)
            }
            "AI_INITIATED_LOCKDOWN" -> {
                template = AdvancedEmailSender.getEmailTemplates()["SYSTEM_BREACH"]!!.copy(subject = "Security Alert: AI-Initiated Lockdown")
                sendAlert(template, capture, audioFile)
                // Perform the lockdown action
                withContext(Dispatchers.Main) {
                    Log.i(TAG, "ACTION: Locking device due to AI decision.")
                    val lockIntent = Intent(this@PanicActionService, LockScreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra("REASON", "AI_LOCKDOWN")
                    }
                    startActivity(lockIntent)
                }
            }
            else -> {
                template = AdvancedEmailSender.getEmailTemplates()["GENERIC_HIGH"]!!
                sendAlert(template, capture, audioFile)
            }
        }
        // ### END: PRECISE FIX FOR AI LOCKDOWN ACTION ###
    }

    private suspend fun handleCriticalIncident(reason: String) {
        Log.e(TAG, "Handling CRITICAL severity: $reason")
        sendAlert(AdvancedEmailSender.getEmailTemplates()["URGENT_PREAMBLE"]!!.copy(
            body = "CRITICAL INCIDENT: $reason. Stand by for evidence package."
        ))

        val capture = if (PermissionUtils.hasCameraPermission(this)) {
            AdvancedCameraHandler.performFullCapture(this, cameraLifecycleOwner, videoDurationSeconds = 30)
        } else {
            null
        }

        val audioFile = if (SecurityPreferences.isAmbientAudioEnabled(this) && PermissionUtils.hasRecordAudioPermission(this)) {
            AudioRecorder.recordAudio(this, 60)
        } else {
            null
        }

        val template = AdvancedEmailSender.getEmailTemplates()["SYSTEM_BREACH"]!!.copy(
            body = "CRITICAL INCIDENT: $reason. Full device diagnostics and evidence package attached."
        )

        sendAlert(template, capture, audioFile, includeDeviceInfo = true)

        if ((reason == "REMOTE_WIPE" || reason == "TRIPWIRE_WIPE" || reason == "WIPE_PIN") && SecurityPreferences.isWipeDeviceEnabled(this)) {
            if (SecurityPreferences.isSecureWipeEnabled(this)) {
                RootActions.performSecureWipePlus(this)
            } else {
                performStandardWipe()
            }
        }
    }

    private suspend fun sendAlert(
        template: AdvancedEmailSender.EmailTemplate,
        capture: AdvancedCameraHandler.CameraCapture? = null,
        audioFile: File? = null,
        includeDeviceInfo: Boolean = false
    ) {
        val contact = SecurityPreferences.getEmergencyContact(this)
        if (contact.isNullOrEmpty()) {
            Log.e(TAG, "Emergency contact not set. Cannot send alert.")
            EventLogger.log(this, "Alert failed: Emergency contact not set.")
            return
        }

        val deviceInfo = if (includeDeviceInfo) DeviceInfoCollector.collectDeviceInfo(this) else null

        var screenshotFile: File? = null
        if (SecurityPreferences.isStealthScreenshotEnabled(this) && RootChecker.isDeviceRooted()) {
            screenshotFile = RootActions.takeStealthScreenshot(this)
        }

        if (contact.contains("@")) {
            AdvancedEmailSender.sendAdvancedAlert(this, template, capture, audioFile, deviceInfo, screenshotFile)
        } else {
            val location = capture?.location ?: getCurrentLocation()
            val locationString = if (location != null) {
                "Location: https://maps.google.com?q=${location.latitude},${location.longitude}"
            } else {
                "Location unavailable."
            }
            val smsBody = "Uncle Ted Alert: ${template.subject}. $locationString"
            sendSms(contact, smsBody)
        }
    }

    private suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        if (!PermissionUtils.hasLocationPermissions(this)) {
            Log.e(TAG, "Location permissions not granted.")
            if (continuation.isActive) continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val cancellationTokenSource = CancellationTokenSource()

        continuation.invokeOnCancellation { cancellationTokenSource.cancel() }

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    Log.d(TAG, "Location retrieved: ${location?.latitude}, ${location?.longitude}")
                    if (continuation.isActive) continuation.resume(location)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get location", e)
                    if (continuation.isActive) continuation.resume(null)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied", e)
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private fun sendSms(phoneNumber: String, message: String) {
        if (!PermissionUtils.hasSmsPermissions(this)) {
            Log.e(TAG, "SMS permissions not granted. Cannot send SMS.")
            return
        }
        try {
            val smsManager = getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.i(TAG, "SMS sent to $phoneNumber")
            EventLogger.log(this, "SMS alert sent to $phoneNumber.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
            EventLogger.log(this, "ERROR: Failed to send SMS.")
        }
    }

    private suspend fun startSiren(durationSeconds: Int) {
        Log.d(TAG, "ACTION: Executing Remote Siren for ${durationSeconds}s")
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var originalAlarmVolume: Int? = null

        try {
            originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0)

            val alarmUri = Settings.System.DEFAULT_ALARM_ALERT_URI
            if (alarmUri != null) {
                sirenMediaPlayer = MediaPlayer().apply {
                    setDataSource(this@PanicActionService, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } else {
                Log.e(TAG, "Default alarm URI is null. Cannot play siren sound.")
            }

            vibrator?.let {
                val pattern = longArrayOf(0, 1000, 500, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(pattern, 0)
                }
            }

            delay(durationSeconds * 1000L)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start or maintain siren", e)
        } finally {
            stopSiren()
            originalAlarmVolume?.let {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, it, 0)
                Log.d(TAG, "Restored original alarm volume.")
            }
        }
    }

    private fun stopSiren() {
        try {
            if (sirenMediaPlayer?.isPlaying == true) {
                sirenMediaPlayer?.stop()
            }
            sirenMediaPlayer?.release()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "MediaPlayer was in an invalid state during stop/release.", e)
        } finally {
            sirenMediaPlayer = null
        }

        vibrator?.cancel()
        Log.d(TAG, "Siren and vibration stopped.")
    }

    private fun performStandardWipe() {
        EventLogger.log(this, "WIPE INITIATED (Standard).")
        if (PermissionUtils.isDeviceAdminActive(this)) {
            Log.w(TAG, "ACTION: INITIATING DEVICE WIPE!")
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.wipeData(0)
        } else {
            Log.e(TAG, "Attempted to wipe data but Device Admin is not active.")
            EventLogger.log(this, "WIPE FAILED: Device Admin not active.")
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        wakeLock?.release()
        stopSiren()
        cameraLifecycleOwner.destroy()
        serviceJob.cancel()
        super.onDestroy()
        Log.d(TAG, "PanicActionService destroyed.")
    }
}